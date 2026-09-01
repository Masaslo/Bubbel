use std::fs;

use df::tract::{DfParams, DfTract, ReduceMask, RuntimeParams};
use ndarray::{ArrayView2, ArrayViewMut2};

const HOP_SIZE: usize = 480;

fn main() -> Result<(), Box<dyn std::error::Error>> {
    let args: Vec<String> = std::env::args().collect();
    if args.len() != 4 {
        return Err("expected model archive, input output and golden output paths".into());
    }

    let params = DfParams::from_bytes(&fs::read(&args[1])?)?;
    let runtime = RuntimeParams::default_with_ch(1)
        .with_atten_lim(100.0)
        .with_thresholds(-15.0, 35.0, 35.0)
        .with_mask_reduce(ReduceMask::MAX);
    let mut tract = DfTract::new(params, &runtime)?;
    assert_eq!((tract.sr, tract.fft_size, tract.hop_size), (48_000, 960, HOP_SIZE));

    let mut input = [0.0f32; HOP_SIZE];
    let mut output = [0.0f32; HOP_SIZE];
    for (index, sample) in input.iter_mut().enumerate() {
        *sample = (index as f32 * 0.013).sin() * 0.2;
    }
    tract.process(
        ArrayView2::from_shape((1, HOP_SIZE), &input)?,
        ArrayViewMut2::from_shape((1, HOP_SIZE), &mut output)?,
    )?;

    fs::write(&args[2], input.iter().flat_map(|value| value.to_le_bytes()).collect::<Vec<_>>())?;
    fs::write(&args[3], output.iter().flat_map(|value| value.to_le_bytes()).collect::<Vec<_>>())?;
    Ok(())
}
