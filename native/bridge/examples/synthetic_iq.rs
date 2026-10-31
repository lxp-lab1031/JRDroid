//! Generated test data only: no railway recordings, no RF transmission.
fn main() {
    let path = std::env::args().nth(1).expect("output path");
    let envelope = receiver_core::synthesis::voice(&[0x15; 20], false);
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
    std::fs::write(path, bytes).unwrap();
}
