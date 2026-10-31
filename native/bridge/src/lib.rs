//! Streaming bridge: unsigned interleaved RTL IQ -> upstream JR pipeline -> PCM16.
use receiver_core::{
    data::Format,
    pipeline::{ChannelConfig, Pipeline},
    Complex32,
};

pub const FREQUENCY: f64 = 352_625_000.0;
pub const RATE: f64 = 240_000.0;

pub struct Receiver {
    pipeline: Pipeline,
    pending_i: Option<u8>,
    last_audio: Option<(u64, u32)>,
}
impl Receiver {
    pub fn new() -> Result<Self, String> {
        Ok(Self {
            pipeline: Pipeline::new(
                ChannelConfig {
                    id: "passenger".into(),
                    label: "旅客一斉".into(),
                    frequency_hz: FREQUENCY,
                    format: Format::Common,
                },
                RATE,
                FREQUENCY,
            )?,
            pending_i: None,
            last_audio: None,
        })
    }
    pub fn process(&mut self, bytes: &[u8]) -> Result<Vec<i16>, String> {
        if bytes.len() > 131_072 {
            return Err("IQ block too large".into());
        }
        let mut iq = Vec::with_capacity(bytes.len() / 2 + 1);
        let sample = |i: u8, q: u8| Complex32 {
            real: (i as f32 - 127.5) / 128.0,
            imaginary: (q as f32 - 127.5) / 128.0,
        };
        let mut at = 0;
        if let Some(i) = self.pending_i.take() {
            if let Some(&q) = bytes.first() {
                iq.push(sample(i, q));
                at = 1;
            } else {
                self.pending_i = Some(i);
            }
        }
        let mut pairs = bytes[at..].chunks_exact(2);
        iq.extend(pairs.by_ref().map(|p| sample(p[0], p[1])));
        if let Some(&i) = pairs.remainder().first() {
            self.pending_i = Some(i);
        }
        let audio = self.pipeline.process(&iq)?;
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
        let s = self.pipeline.snapshot();
        serde_json::json!({
            "iq_samples": s.iq_samples, "frames": s.frames,
            "synchronized_frames": s.synchronized_frames,
            "voice_frames": s.voice_frames, "rejected_voice": s.rejected_voice,
            "audio_frames": s.audio_frames, "frame_locked": s.frame_locked,
            "carrier_locked": s.carrier_locked, "frequency_error_hz": s.frequency_error_hz,
            "evm_percent": s.evm_percent, "input_dbfs": s.input_dbfs,
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
    ) -> jlong {
        guarded(&mut env, |_| {
            Ok(Box::into_raw(Box::new(Receiver::new()?)) as jlong)
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
            let mut bytes = vec![0i8; count as usize];
            env.get_byte_array_region(&input, 0, &mut bytes)
                .map_err(|e| e.to_string())?;
            let bytes: Vec<u8> = bytes.into_iter().map(|b| b as u8).collect();
            let pcm = unsafe { receiver(h)? }.process(&bytes)?;
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
    ) -> jstring {
        guarded(&mut env, |env| {
            let s = unsafe { receiver(h)? }.status();
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
        let mut r = Receiver::new().unwrap();
        let mut pcm = Vec::new();
        for chunk in bytes.chunks(8191) {
            pcm.extend(r.process(chunk).unwrap());
        }
        pcm.extend(r.flush().unwrap());
        assert!(
            r.pipeline.status.synchronized_frames >= 20,
            "{}",
            r.status()
        );
        assert!(r.pipeline.status.voice_frames >= 20, "{}", r.status());
        assert!(pcm.len() >= 20 * 320);
        assert!(pcm.iter().any(|&s| s != 0));
    }
}
