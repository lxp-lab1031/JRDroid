//! Streaming bridge: unsigned interleaved RTL IQ -> upstream JR pipeline -> PCM16.
use receiver_core::{
    data::Format,
    pipeline::{ChannelConfig, Pipeline},
    Complex32,
};

pub const FREQUENCY: f64 = 352_625_000.0;
pub const RATE: f64 = 240_000.0;
pub const MIN_FREQUENCY: u32 = 352_537_500;
pub const MAX_FREQUENCY: u32 = 352_668_750;
pub const CHANNEL_STEP: u32 = 6_250;
// User-supplied Windows settings, not verified private-radio protocol support.
pub const PRIVATE_FREQUENCIES: [u32; 16] = [
    142493750, 142481250, 142506250, 142518750, 142500000, 142487500, 142512500, 142525000,
    142550000, 142537500, 142562500, 142575000, 142818750, 142806250, 142831250, 142843750,
];

pub struct Receiver {
    pipeline: Pipeline,
    pending_i: Option<u8>,
    last_audio: Option<(u64, u32)>,
    iq_buffer: Vec<Complex32>,
    #[cfg(any(target_os = "android", feature = "host-jni"))]
    input_buffer: Vec<i8>,
}
impl Receiver {
    pub fn new() -> Result<Self, String> {
        Self::at_frequency(FREQUENCY as u32)
    }
    pub fn at_frequency(hz: u32) -> Result<Self, String> {
        let jr = (MIN_FREQUENCY..=MAX_FREQUENCY).contains(&hz)
            && (hz - MIN_FREQUENCY).is_multiple_of(CHANNEL_STEP);
        if !jr && !PRIVATE_FREQUENCIES.contains(&hz) {
            return Err(
                "Select a JR D-type channel or one of the 16 experimental 142 MHz presets".into(),
            );
        }
        let frequency = f64::from(hz);
        Ok(Self {
            pipeline: Pipeline::new(
                ChannelConfig {
                    id: format!("jr-{hz}"),
                    label: if hz == FREQUENCY as u32 {
                        "旅客一斉".into()
                    } else {
                        format!("D {hz} Hz")
                    },
                    frequency_hz: frequency,
                    format: Format::for_frequency(frequency),
                },
                RATE,
                frequency,
            )?,
            pending_i: None,
            last_audio: None,
            iq_buffer: Vec::new(),
            #[cfg(any(target_os = "android", feature = "host-jni"))]
            input_buffer: Vec::new(),
        })
    }
    pub fn process(&mut self, bytes: &[u8]) -> Result<Vec<i16>, String> {
        if bytes.len() > 131_072 {
            return Err("IQ block too large".into());
        }
        self.iq_buffer.clear();
        self.iq_buffer.reserve(bytes.len() / 2 + 1);
        let sample = |i: u8, q: u8| Complex32 {
            real: (i as f32 - 127.5) / 128.0,
            imaginary: (q as f32 - 127.5) / 128.0,
        };
        let mut at = 0;
        if let Some(i) = self.pending_i.take() {
            if let Some(&q) = bytes.first() {
                self.iq_buffer.push(sample(i, q));
                at = 1;
            } else {
                self.pending_i = Some(i);
            }
        }
        let mut pairs = bytes[at..].chunks_exact(2);
        self.iq_buffer
            .extend(pairs.by_ref().map(|p| sample(p[0], p[1])));
        if let Some(&i) = pairs.remainder().first() {
            self.pending_i = Some(i);
        }
        let audio = self.pipeline.process(&self.iq_buffer)?;
        Ok(self.render(audio))
    }
    fn render(&mut self, audio: Vec<receiver_core::pipeline::Audio>) -> Vec<i16> {
        let mut pcm = Vec::new();
        for a in audio {
            if let Some((epoch, last)) = self.last_audio {
                if epoch == a.epoch && a.index > last {
                    // Preserve short missing slots; never allocate an unbounded gap.
                    let gap = (a.index - last - 1).min(25) as usize;
                    pcm.resize(pcm.len() + gap * 320, 0);
                }
            }
            pcm.extend(a.pcm);
            self.last_audio = Some((a.epoch, a.index));
        }
        pcm
    }
    pub fn flush(&mut self) -> Result<Vec<i16>, String> {
        let audio = self.pipeline.flush()?;
        Ok(self.render(audio))
    }
    pub fn status(&mut self) -> String {
        self.status_with_diagnostics(true)
    }
    pub fn status_with_diagnostics(&mut self, diagnostics: bool) -> String {
        // Constellation/EVM and cloned message history are only needed on screen.
        if diagnostics {
            self.pipeline.snapshot();
        }
        let s = &self.pipeline.status;
        serde_json::json!({
            "experimental_private": PRIVATE_FREQUENCIES.contains(&(self.pipeline.config.frequency_hz as u32)),
            "frequency_hz": self.pipeline.config.frequency_hz as u32,
            "format": self.pipeline.config.format,
            "iq_samples": s.iq_samples, "frames": s.frames,
            "synchronized_frames": s.synchronized_frames,
            "voice_frames": s.voice_frames, "rejected_voice": s.rejected_voice,
            "audio_frames": s.audio_frames, "frame_locked": s.frame_locked,
            "carrier_locked": s.carrier_locked, "frequency_error_hz": s.frequency_error_hz,
            "diagnostics_current": diagnostics,
            "evm_percent": if diagnostics { Some(s.evm_percent) } else { None }, "input_dbfs": s.input_dbfs,
        })
        .to_string()
    }
}

#[cfg(any(target_os = "android", feature = "host-jni"))]
mod android {
    use super::Receiver;
    use jni::{
        objects::{JByteArray, JClass},
        sys::{jlong, jshortArray, jstring},
        JNIEnv,
    };
    use std::panic::{catch_unwind, AssertUnwindSafe};

    fn guarded<T: Default>(
        env: &mut JNIEnv,
        f: impl FnOnce(&mut JNIEnv) -> Result<T, String>,
    ) -> T {
        match catch_unwind(AssertUnwindSafe(|| f(env))) {
            Ok(Ok(value)) => value,
            Ok(Err(e)) => {
                let _ = env.throw_new("java/lang/IllegalStateException", e);
                T::default()
            }
            Err(_) => {
                let _ = env.throw_new(
                    "java/lang/IllegalStateException",
                    "Rust decoder panic; restart reception",
                );
                T::default()
            }
        }
    }
    // Handles are private to NativeReceiver; only its owning DSP worker uses them.
    unsafe fn receiver<'a>(handle: jlong) -> Result<&'a mut Receiver, String> {
        (handle as *mut Receiver)
            .as_mut()
            .ok_or_else(|| "Receiver is closed".into())
    }
    #[no_mangle]
    pub extern "system" fn Java_jp_jrdroid_NativeReceiver_create(
        mut env: JNIEnv,
        _: JClass,
        frequency_hz: i32,
    ) -> jlong {
        guarded(&mut env, |_| {
            let hz = u32::try_from(frequency_hz).map_err(|_| "Invalid receive frequency")?;
            Ok(Box::into_raw(Box::new(Receiver::at_frequency(hz)?)) as jlong)
        })
    }
    #[no_mangle]
    pub extern "system" fn Java_jp_jrdroid_NativeReceiver_process(
        mut env: JNIEnv,
        _: JClass,
        h: jlong,
        input: JByteArray,
        count: i32,
    ) -> jshortArray {
        guarded(&mut env, |env| {
            let length = env.get_array_length(&input).map_err(|e| e.to_string())?;
            if count < 0 || count > length || count > 131_072 {
                return Err("Invalid IQ length".into());
            }
            let r = unsafe { receiver(h)? };
            let mut bytes = std::mem::take(&mut r.input_buffer);
            bytes.resize(count as usize, 0);
            let result = (|| {
                env.get_byte_array_region(&input, 0, &mut bytes)
                    .map_err(|e| e.to_string())?;
                // i8 and u8 have identical layout; this read-only view preserves all bits.
                // The allocation is owned here, outside Receiver, during process().
                let unsigned =
                    unsafe { std::slice::from_raw_parts(bytes.as_ptr().cast::<u8>(), bytes.len()) };
                r.process(unsigned)
            })();
            r.input_buffer = bytes;
            let pcm = result?;
            if pcm.is_empty() {
                return Ok(std::ptr::null_mut());
            }
            let out = env
                .new_short_array(pcm.len() as i32)
                .map_err(|e| e.to_string())?;
            env.set_short_array_region(&out, 0, &pcm)
                .map_err(|e| e.to_string())?;
            Ok(out.into_raw())
        })
    }
    #[no_mangle]
    pub extern "system" fn Java_jp_jrdroid_NativeReceiver_status(
        mut env: JNIEnv,
        _: JClass,
        h: jlong,
        diagnostics: jni::sys::jboolean,
    ) -> jstring {
        guarded(&mut env, |env| {
            let s = unsafe { receiver(h)? }.status_with_diagnostics(diagnostics != 0);
            Ok(env.new_string(s).map_err(|e| e.to_string())?.into_raw())
        })
    }
    #[no_mangle]
    pub extern "system" fn Java_jp_jrdroid_NativeReceiver_destroy(_: JNIEnv, _: JClass, h: jlong) {
        if h != 0 {
            unsafe {
                drop(Box::from_raw(h as *mut Receiver));
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use receiver_core::{frame::Synchronizer, synthesis};
    #[test]
    fn frequencies_are_validated_and_data_formats_follow_upstream() {
        for hz in [
            0,
            MIN_FREQUENCY - 1,
            MIN_FREQUENCY + 1,
            MAX_FREQUENCY + CHANNEL_STEP,
            u32::MAX,
        ] {
            assert!(Receiver::at_frequency(hz).is_err(), "{hz}");
        }
        for hz in (MIN_FREQUENCY..=MAX_FREQUENCY).step_by(CHANNEL_STEP as usize) {
            let r = Receiver::at_frequency(hz).unwrap();
            assert_eq!(r.pipeline.config.frequency_hz, f64::from(hz));
            assert_eq!(
                r.pipeline.config.format,
                if hz == FREQUENCY as u32 {
                    Format::Common
                } else {
                    Format::Slotted
                }
            );
        }
    }
    #[test]
    fn experimental_presets_are_explicit_and_not_reported_as_verified_protocols() {
        for hz in PRIVATE_FREQUENCIES {
            let mut r = Receiver::at_frequency(hz).unwrap();
            let status: serde_json::Value =
                serde_json::from_str(&r.status_with_diagnostics(false)).unwrap();
            assert_eq!(status["frequency_hz"], hz);
            assert_eq!(status["experimental_private"], true);
            assert_eq!(r.pipeline.config.format, Format::Slotted);
        }
        for hz in [142000000, 142493751, 142600000, 142843751] {
            assert!(Receiver::at_frequency(hz).is_err());
        }
    }
    #[test]
    fn iq_buffers_are_reused_and_light_status_preserves_counters() {
        let mut r = Receiver::new().unwrap();
        let input = vec![127; 32768];
        r.process(&input).unwrap();
        let capacity = r.iq_buffer.capacity();
        let ptr = r.iq_buffer.as_ptr();
        for _ in 0..4 {
            assert!(r.process(&input).unwrap().is_empty());
        }
        assert_eq!(r.iq_buffer.capacity(), capacity);
        assert_eq!(r.iq_buffer.as_ptr(), ptr);
        let light: serde_json::Value =
            serde_json::from_str(&r.status_with_diagnostics(false)).unwrap();
        let full: serde_json::Value = serde_json::from_str(&r.status()).unwrap();
        assert_eq!(light["iq_samples"], 81920);
        assert_eq!(light["iq_samples"], full["iq_samples"]);
        assert!(light["evm_percent"].is_null());
        assert_eq!(light["diagnostics_current"], false);
        assert_eq!(full["diagnostics_current"], true);
    }
    #[test]
    fn larger_iq_batches_produce_identical_pcm() {
        let envelope = synthesis::voice(&[0x15; 20], false);
        let dibits: Vec<_> = (0..30)
            .flat_map(|_| jr_demod_eval::envelope_dibits(&envelope))
            .collect();
        let iq = jr_demod_eval::modulate(&dibits, 50);
        let bytes: Vec<u8> = iq
            .iter()
            .flat_map(|z| {
                [z.real, z.imaginary].map(|v| (v * 45.0 + 127.5).clamp(0., 255.).round() as u8)
            })
            .collect();
        let decode = |size| {
            let mut r = Receiver::new().unwrap();
            let mut pcm = Vec::new();
            for chunk in bytes.chunks(size) {
                pcm.extend(r.process(chunk).unwrap());
            }
            pcm.extend(r.flush().unwrap());
            pcm
        };
        let short = decode(8191);
        let batched = decode(32768);
        assert!(short.len() >= 6400);
        assert_eq!(short, batched);
    }
    #[test]
    fn odd_usb_chunks_preserve_iq_pairs_and_noise_does_not_make_audio() {
        let mut a = Receiver::new().unwrap();
        let mut b = Receiver::new().unwrap();
        let input = vec![127u8; 24001];
        assert!(a.process(&input).unwrap().is_empty());
        for chunk in input.chunks(137) {
            assert!(b.process(chunk).unwrap().is_empty());
        }
        let av: serde_json::Value = serde_json::from_str(&a.status()).unwrap();
        let bv: serde_json::Value = serde_json::from_str(&b.status()).unwrap();
        assert_eq!(av["iq_samples"], 12000);
        assert_eq!(av["iq_samples"], bv["iq_samples"]);
        assert_eq!(bv["voice_frames"], 0);
        assert!(b.process(&[127]).unwrap().is_empty());
        assert_eq!(
            serde_json::from_str::<serde_json::Value>(&b.status()).unwrap()["iq_samples"],
            12001
        );
    }
    #[test]
    fn valid_envelopes_synchronize_and_produce_pcm_with_flush() {
        let mut r = Receiver::new().unwrap();
        let mut sync = Synchronizer::default();
        let payload = [0x15; 20];
        let envelope = synthesis::voice(&payload, false);
        let mut pcm = Vec::new();
        for _ in 0..14 {
            for frame in sync.packed(&envelope) {
                let audio = r.pipeline.frame(&frame).unwrap();
                pcm.extend(r.render(audio));
            }
        }
        pcm.extend(r.flush().unwrap());
        assert_eq!(pcm.len(), 14 * 320);
        assert!(pcm.iter().any(|&s| s != 0));
        assert!(r.flush().unwrap().is_empty());
        assert_eq!(r.pipeline.status.voice_frames, 14);
    }
    #[test]
    fn synthetic_rf_unsigned_iq_to_audio() {
        let payload = [0x15; 20];
        let envelope = synthesis::voice(&payload, false);
        let dibits: Vec<_> = (0..30)
            .flat_map(|_| jr_demod_eval::envelope_dibits(&envelope))
            .collect();
        let iq = jr_demod_eval::modulate(&dibits, 50); // 240 kS/s / 4800 symbols/s
        let bytes: Vec<u8> = iq
            .iter()
            .flat_map(|z| {
                [z.real, z.imaginary].map(|v| (v * 45.0 + 127.5).clamp(0.0, 255.0).round() as u8)
            })
            .collect();
        for hz in (MIN_FREQUENCY..=MAX_FREQUENCY).step_by(CHANNEL_STEP as usize) {
            let mut r = Receiver::at_frequency(hz).unwrap();
            let mut pcm = Vec::new();
            for chunk in bytes.chunks(8191) {
                pcm.extend(r.process(chunk).unwrap());
            }
            pcm.extend(r.flush().unwrap());
            assert!(
                r.pipeline.status.synchronized_frames >= 20,
                "{hz}: {}",
                r.status()
            );
            assert!(r.pipeline.status.voice_frames >= 20, "{hz}: {}", r.status());
            assert!(pcm.len() >= 20 * 320);
            assert!(pcm.iter().any(|&s| s != 0));
        }
    }
}
