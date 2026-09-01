use bubbel_libdf::{
    bubbel_libdf_close, bubbel_libdf_open, bubbel_libdf_process, bubbel_libdf_reopen,
    BubbelLibDfSession, BUBBEL_LIBDF_STATUS_INVALID_ARGUMENT, BUBBEL_LIBDF_STATUS_OK,
    BUBBEL_LIBDF_STATUS_PANIC, BUBBEL_LIBDF_HOP_SIZE,
};

const MODEL: &[u8] = include_bytes!("../../assets/models/deepfilternet3/DeepFilterNet3_onnx.tar.gz");

#[test]
fn ffi_rejects_invalid_pointers_and_lengths_without_unwinding() {
    let mut session: *mut BubbelLibDfSession = std::ptr::null_mut();
    let input = [0.0f32; BUBBEL_LIBDF_HOP_SIZE];
    let mut output = [0.0f32; BUBBEL_LIBDF_HOP_SIZE];

    unsafe {
        assert_eq!(
            bubbel_libdf_open(std::ptr::null(), MODEL.len(), &mut session),
            BUBBEL_LIBDF_STATUS_INVALID_ARGUMENT
        );
        assert_eq!(
            bubbel_libdf_open(MODEL.as_ptr(), MODEL.len(), &mut session),
            BUBBEL_LIBDF_STATUS_OK
        );
        assert_eq!(
            bubbel_libdf_process(session, input.as_ptr(), BUBBEL_LIBDF_HOP_SIZE - 1, output.as_mut_ptr(), BUBBEL_LIBDF_HOP_SIZE),
            BUBBEL_LIBDF_STATUS_INVALID_ARGUMENT
        );
        let misaligned = (input.as_ptr() as *const u8).add(1) as *const f32;
        assert_eq!(
            bubbel_libdf_process(session, misaligned, BUBBEL_LIBDF_HOP_SIZE, output.as_mut_ptr(), BUBBEL_LIBDF_HOP_SIZE),
            BUBBEL_LIBDF_STATUS_INVALID_ARGUMENT
        );
        assert_ne!(bubbel_libdf_reopen(std::ptr::null_mut()), BUBBEL_LIBDF_STATUS_PANIC);
        assert_eq!(bubbel_libdf_close(session), BUBBEL_LIBDF_STATUS_OK);
    }
}

#[test]
fn independently_opened_sessions_have_identical_first_hop_output() {
    let mut first: *mut BubbelLibDfSession = std::ptr::null_mut();
    let mut second: *mut BubbelLibDfSession = std::ptr::null_mut();
    let mut input = [0.0f32; BUBBEL_LIBDF_HOP_SIZE];
    let mut first_output = [0.0f32; BUBBEL_LIBDF_HOP_SIZE];
    let mut second_output = [0.0f32; BUBBEL_LIBDF_HOP_SIZE];
    for (index, sample) in input.iter_mut().enumerate() {
        *sample = (index as f32 * 0.013).sin() * 0.2;
    }

    unsafe {
        assert_eq!(bubbel_libdf_open(MODEL.as_ptr(), MODEL.len(), &mut first), BUBBEL_LIBDF_STATUS_OK);
        assert_eq!(bubbel_libdf_open(MODEL.as_ptr(), MODEL.len(), &mut second), BUBBEL_LIBDF_STATUS_OK);
        assert_eq!(bubbel_libdf_process(first, input.as_ptr(), input.len(), first_output.as_mut_ptr(), first_output.len()), BUBBEL_LIBDF_STATUS_OK);
        assert_eq!(bubbel_libdf_process(second, input.as_ptr(), input.len(), second_output.as_mut_ptr(), second_output.len()), BUBBEL_LIBDF_STATUS_OK);
        assert_eq!(first_output, second_output);
        assert_eq!(bubbel_libdf_close(first), BUBBEL_LIBDF_STATUS_OK);
        assert_eq!(bubbel_libdf_close(second), BUBBEL_LIBDF_STATUS_OK);
    }
}
