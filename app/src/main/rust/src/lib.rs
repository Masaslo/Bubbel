//! App-owned, status-code C ABI around DeepFilterNet's public Tract API.
//!
//! `bubbel_libdf_reopen` intentionally replaces the complete `DfTract` object.
//! Call it from a worker/control thread only; inference itself never belongs in
//! an Oboe callback.
//!
//! The exported functions reject null/misaligned pointers and invalid lengths.
//! As with any raw-pointer C ABI, callers must still provide live, accessible
//! buffers and may only use a session returned by `bubbel_libdf_open` until its
//! first `bubbel_libdf_close`; forged, stale and double-closed handles are
//! caller undefined behavior and cannot be made safe by `catch_unwind`.

use std::panic::{catch_unwind, AssertUnwindSafe};

use df::tract::{DfParams, DfTract, ReduceMask, RuntimeParams};
use ndarray::{ArrayView2, ArrayViewMut2};

pub const BUBBEL_LIBDF_HOP_SIZE: usize = 480;
pub const BUBBEL_LIBDF_STATUS_OK: i32 = 0;
pub const BUBBEL_LIBDF_STATUS_INVALID_ARGUMENT: i32 = 1;
pub const BUBBEL_LIBDF_STATUS_MODEL_ERROR: i32 = 2;
pub const BUBBEL_LIBDF_STATUS_PROCESSING_ERROR: i32 = 3;
pub const BUBBEL_LIBDF_STATUS_PANIC: i32 = 4;

pub struct BubbelLibDfSession {
    params: DfParams,
    tract: DfTract,
}

fn runtime_params() -> RuntimeParams {
    RuntimeParams::default_with_ch(1)
        .with_atten_lim(100.0)
        .with_thresholds(-15.0, 35.0, 35.0)
        .with_mask_reduce(ReduceMask::MAX)
}

fn new_tract(params: DfParams) -> Result<DfTract, i32> {
    let tract = DfTract::new(params, &runtime_params()).map_err(|_| BUBBEL_LIBDF_STATUS_MODEL_ERROR)?;
    if tract.hop_size != BUBBEL_LIBDF_HOP_SIZE || tract.sr != 48_000 || tract.fft_size != 960 {
        return Err(BUBBEL_LIBDF_STATUS_MODEL_ERROR);
    }
    Ok(tract)
}

fn guarded(operation: impl FnOnce() -> i32) -> i32 {
    catch_unwind(AssertUnwindSafe(operation)).unwrap_or(BUBBEL_LIBDF_STATUS_PANIC)
}

fn buffers_overlap(input: *const f32, input_len: usize, output: *mut f32, output_len: usize) -> bool {
    let input_start = input as usize;
    let output_start = output as usize;
    let Some(input_end) = input_start.checked_add(input_len.saturating_mul(std::mem::size_of::<f32>())) else {
        return true;
    };
    let Some(output_end) = output_start.checked_add(output_len.saturating_mul(std::mem::size_of::<f32>())) else {
        return true;
    };
    input_start < output_end && output_start < input_end
}

fn is_aligned<T>(pointer: *const T) -> bool {
    (pointer as usize) % std::mem::align_of::<T>() == 0
}

/// Parses a complete, immutable DeepFilterNet3 tar archive and creates an
/// opaque session. Both `model_bytes` and `out_session` must reference live,
/// accessible storage for their declared sizes.
#[no_mangle]
pub unsafe extern "C" fn bubbel_libdf_open(
    model_bytes: *const u8,
    model_len: usize,
    out_session: *mut *mut BubbelLibDfSession,
) -> i32 {
    guarded(|| {
        if model_bytes.is_null()
            || model_len == 0
            || out_session.is_null()
            || !is_aligned(out_session)
        {
            return BUBBEL_LIBDF_STATUS_INVALID_ARGUMENT;
        }
        let bytes = unsafe { std::slice::from_raw_parts(model_bytes, model_len) };
        let Ok(params) = DfParams::from_bytes(bytes) else {
            return BUBBEL_LIBDF_STATUS_MODEL_ERROR;
        };
        let Ok(tract) = new_tract(params.clone()) else {
            return BUBBEL_LIBDF_STATUS_MODEL_ERROR;
        };
        unsafe {
            *out_session = Box::into_raw(Box::new(BubbelLibDfSession { params, tract }));
        }
        BUBBEL_LIBDF_STATUS_OK
    })
}

/// Processes exactly one mono, 48 kHz DeepFilterNet3 hop. Input and output
/// must be separate live 480-sample buffers. `session` must be a live handle
/// returned by `bubbel_libdf_open` and not yet closed.
#[no_mangle]
pub unsafe extern "C" fn bubbel_libdf_process(
    session: *mut BubbelLibDfSession,
    input: *const f32,
    input_len: usize,
    output: *mut f32,
    output_len: usize,
) -> i32 {
    guarded(|| {
        if session.is_null()
            || input.is_null()
            || output.is_null()
            || !is_aligned(session)
            || !is_aligned(input)
            || !is_aligned(output)
            || input_len != BUBBEL_LIBDF_HOP_SIZE
            || output_len != BUBBEL_LIBDF_HOP_SIZE
            || buffers_overlap(input, input_len, output, output_len)
        {
            return BUBBEL_LIBDF_STATUS_INVALID_ARGUMENT;
        }
        let session = unsafe { &mut *session };
        let input = unsafe { std::slice::from_raw_parts(input, input_len) };
        let output = unsafe { std::slice::from_raw_parts_mut(output, output_len) };
        let Ok(input) = ArrayView2::from_shape((1, BUBBEL_LIBDF_HOP_SIZE), input) else {
            return BUBBEL_LIBDF_STATUS_INVALID_ARGUMENT;
        };
        let Ok(output) = ArrayViewMut2::from_shape((1, BUBBEL_LIBDF_HOP_SIZE), output) else {
            return BUBBEL_LIBDF_STATUS_INVALID_ARGUMENT;
        };
        match session.tract.process(input, output) {
            Ok(_) => BUBBEL_LIBDF_STATUS_OK,
            Err(_) => BUBBEL_LIBDF_STATUS_PROCESSING_ERROR,
        }
    })
}

/// Fully resets model, STFT/iSTFT and temporal state by replacing `DfTract`.
/// It is intentionally not safe for use from a realtime audio callback. The
/// session handle must be live and owned by the caller.
#[no_mangle]
pub unsafe extern "C" fn bubbel_libdf_reopen(session: *mut BubbelLibDfSession) -> i32 {
    guarded(|| {
        if session.is_null() || !is_aligned(session) {
            return BUBBEL_LIBDF_STATUS_INVALID_ARGUMENT;
        }
        let session = unsafe { &mut *session };
        let Ok(tract) = new_tract(session.params.clone()) else {
            return BUBBEL_LIBDF_STATUS_MODEL_ERROR;
        };
        session.tract = tract;
        BUBBEL_LIBDF_STATUS_OK
    })
}

/// Drops an opaque session. A null session is rejected rather than ignored so
/// callers can surface lifecycle mistakes. A valid handle may be closed once;
/// stale, forged or already-closed handles are caller undefined behavior.
#[no_mangle]
pub unsafe extern "C" fn bubbel_libdf_close(session: *mut BubbelLibDfSession) -> i32 {
    guarded(|| {
        if session.is_null() || !is_aligned(session) {
            return BUBBEL_LIBDF_STATUS_INVALID_ARGUMENT;
        }
        unsafe {
            drop(Box::from_raw(session));
        }
        BUBBEL_LIBDF_STATUS_OK
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn panic_is_translated_to_status_code() {
        assert_eq!(guarded(|| panic!("test panic")), BUBBEL_LIBDF_STATUS_PANIC);
    }
}
