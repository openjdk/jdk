/*
 * Copyright (c) 2003, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 *
 */

#ifndef CPU_X86_STUBGENERATOR_X86_64_HPP
#define CPU_X86_STUBGENERATOR_X86_64_HPP

#include "code/codeBlob.hpp"
#include "runtime/continuation.hpp"
#include "runtime/stubCodeGenerator.hpp"

// Stub Code definitions

class StubGenerator: public StubCodeGenerator {
 private:

  // Call stubs are used to call Java from C.
  address generate_call_stub(address& return_address);

  // Return point for a Java call if there's an exception thrown in
  // Java code.  The exception is caught and transformed into a
  // pending exception stored in JavaThread that can be tested from
  // within the VM.
  //
  // Note: Usually the parameters are removed by the callee. In case
  // of an exception crossing an activation frame boundary, that is
  // not the case if the callee is compiled code => need to setup the
  // rsp.
  //
  // rax: exception oop

  address generate_catch_exception();

  // Continuation point for runtime calls returning with a pending
  // exception.  The pending exception check happened in the runtime
  // or native call stub.  The pending exception in Thread is
  // converted into a Java-level exception.
  //
  // Contract with Java-level exception handlers:
  // rax: exception
  // rdx: throwing pc
  //
  // NOTE: At entry of this stub, exception-pc must be on stack !!

  address generate_forward_exception();

  // Support for intptr_t OrderAccess::fence()
  address generate_orderaccess_fence();

  // Support for intptr_t get_previous_sp()
  //
  // This routine is used to find the previous stack pointer for the
  // caller.
  address generate_get_previous_sp();

  //----------------------------------------------------------------------------------------------------
  // Support for void verify_mxcsr()
  //
  // This routine is used with -Xcheck:jni to verify that native
  // JNI code does not return to Java code without restoring the
  // MXCSR register to our expected state.

  address generate_verify_mxcsr();

  address generate_f2i_fixup();
  address generate_f2l_fixup();
  address generate_d2i_fixup();
  address generate_d2l_fixup();

  address generate_count_leading_zeros_lut(const char *stub_name);
  address generate_popcount_avx_lut(const char *stub_name);
  address generate_iota_indices(const char *stub_name);
  address generate_vector_reverse_bit_lut(const char *stub_name);

  address generate_vector_reverse_byte_perm_mask_long(const char *stub_name);
  address generate_vector_reverse_byte_perm_mask_int(const char *stub_name);
  address generate_vector_reverse_byte_perm_mask_short(const char *stub_name);
  address generate_vector_byte_shuffle_mask(const char *stub_name);

  address generate_fp_mask(const char *stub_name, int64_t mask);

  address generate_compress_perm_table(const char *stub_name, int32_t esize);

  address generate_expand_perm_table(const char *stub_name, int32_t esize);

  address generate_vector_mask(const char *stub_name, int64_t mask);

  address generate_vector_byte_perm_mask(const char *stub_name);

  address generate_vector_fp_mask(const char *stub_name, int64_t mask);

  address generate_vector_custom_i32(const char *stub_name, Assembler::AvxVectorLen len,
                                     int32_t val0, int32_t val1, int32_t val2, int32_t val3,
                                     int32_t val4 = 0, int32_t val5 = 0, int32_t val6 = 0, int32_t val7 = 0,
                                     int32_t val8 = 0, int32_t val9 = 0, int32_t val10 = 0, int32_t val11 = 0,
                                     int32_t val12 = 0, int32_t val13 = 0, int32_t val14 = 0, int32_t val15 = 0);

  // Non-destructive plausibility checks for oops
  address generate_verify_oop();

  // Verify that a register contains clean 32-bits positive value
  // (high 32-bits are 0) so it could be used in 64-bits shifts.
  void assert_clean_int(Register Rint, Register Rtmp);

  //  Generate overlap test for array copy stubs
  void array_overlap_test(address no_overlap_target, Label* NOLp, Address::ScaleFactor sf);

  void array_overlap_test(address no_overlap_target, Address::ScaleFactor sf) {
    assert(no_overlap_target != nullptr, "must be generated");
    array_overlap_test(no_overlap_target, nullptr, sf);
  }
  void array_overlap_test(Label& L_no_overlap, Address::ScaleFactor sf) {
    array_overlap_test(nullptr, &L_no_overlap, sf);
  }


  // Shuffle first three arg regs on Windows into Linux/Solaris locations.
  void setup_arg_regs(int nargs = 3);
  void restore_arg_regs();

#ifdef ASSERT
  bool _regs_in_thread;
#endif

  // This is used in places where r10 is a scratch register, and can
  // be adapted if r9 is needed also.
  void setup_arg_regs_using_thread(int nargs = 3);

  void restore_arg_regs_using_thread();

  // Copy big chunks forward
  void copy_bytes_forward(Register end_from, Register end_to,
                          Register qword_count, Register tmp1,
                          Register tmp2, Label& L_copy_bytes,
                          Label& L_copy_8_bytes, DecoratorSet decorators,
                          BasicType type);

  // Copy big chunks backward
  void copy_bytes_backward(Register from, Register dest,
                           Register qword_count, Register tmp1,
                           Register tmp2, Label& L_copy_bytes,
                           Label& L_copy_8_bytes, DecoratorSet decorators,
                           BasicType type);

  void setup_argument_regs(BasicType type);

  void restore_argument_regs(BasicType type);

#if COMPILER2_OR_JVMCI
  // Following rules apply to AVX3 optimized arraycopy stubs:
  // - If target supports AVX3 features (BW+VL+F) then implementation uses 32 byte vectors (YMMs)
  //   for both special cases (various small block sizes) and aligned copy loop. This is the
  //   default configuration.
  // - If copy length is above AVX3Threshold, then implementation use 64 byte vectors (ZMMs)
  //   for main copy loop (and subsequent tail) since bulk of the cycles will be consumed in it.
  // - If user forces MaxVectorSize=32 then above 4096 bytes its seen that REP MOVs shows a
  //   better performance for disjoint copies. For conjoint/backward copy vector based
  //   copy performs better.
  // - If user sets AVX3Threshold=0, then special cases for small blocks sizes operate over
  //   64 byte vector registers (ZMMs).

  address generate_disjoint_copy_avx3_masked(address* entry, const char *name, int shift,
                                             bool aligned, bool is_oop, bool dest_uninitialized);

  address generate_conjoint_copy_avx3_masked(address* entry, const char *name, int shift,
                                             address nooverlap_target, bool aligned, bool is_oop,
                                             bool dest_uninitialized);

  void arraycopy_avx3_special_cases(XMMRegister xmm, KRegister mask, Register from,
                                    Register to, Register count, int shift,
                                    Register index, Register temp,
                                    bool use64byteVector, Label& L_entry, Label& L_exit);

  void arraycopy_avx3_special_cases_256(XMMRegister xmm, KRegister mask, Register from,
                                    Register to, Register count, int shift,
                                    Register index, Register temp, Label& L_exit);

  void arraycopy_avx3_special_cases_conjoint(XMMRegister xmm, KRegister mask, Register from,
                                             Register to, Register start_index, Register end_index,
                                             Register count, int shift, Register temp,
                                             bool use64byteVector, Label& L_entry, Label& L_exit);

  void arraycopy_avx3_large(Register to, Register from, Register temp1, Register temp2,
                            Register temp3, Register temp4, Register count,
                            XMMRegister xmm1, XMMRegister xmm2, XMMRegister xmm3,
                            XMMRegister xmm4, int shift);

  void copy32_avx(Register dst, Register src, Register index, XMMRegister xmm,
                  int shift = Address::times_1, int offset = 0);

  void copy64_avx(Register dst, Register src, Register index, XMMRegister xmm,
                  bool conjoint, int shift = Address::times_1, int offset = 0,
                  bool use64byteVector = false);

  void copy256_avx3(Register dst, Register src, Register index, XMMRegister xmm1, XMMRegister xmm2,
                                XMMRegister xmm3, XMMRegister xmm4, int shift, int offset = 0);

  void copy64_masked_avx(Register dst, Register src, XMMRegister xmm,
                         KRegister mask, Register length, Register index,
                         Register temp, int shift = Address::times_1, int offset = 0,
                         bool use64byteVector = false);

  void copy32_masked_avx(Register dst, Register src, XMMRegister xmm,
                         KRegister mask, Register length, Register index,
                         Register temp, int shift = Address::times_1, int offset = 0);
#endif // COMPILER2_OR_JVMCI

  address generate_disjoint_byte_copy(bool aligned, address* entry, const char *name);

  address generate_conjoint_byte_copy(bool aligned, address nooverlap_target,
                                      address* entry, const char *name);

  address generate_disjoint_short_copy(bool aligned, address *entry, const char *name);

  address generate_fill(BasicType t, bool aligned, const char *name);

  address generate_conjoint_short_copy(bool aligned, address nooverlap_target,
                                       address *entry, const char *name);
  address generate_disjoint_int_oop_copy(bool aligned, bool is_oop, address* entry,
                                         const char *name, bool dest_uninitialized = false);
  address generate_conjoint_int_oop_copy(bool aligned, bool is_oop, address nooverlap_target,
                                         address *entry, const char *name,
                                         bool dest_uninitialized = false);
  address generate_disjoint_long_oop_copy(bool aligned, bool is_oop, address *entry,
                                          const char *name, bool dest_uninitialized = false);
  address generate_conjoint_long_oop_copy(bool aligned, bool is_oop,
                                          address nooverlap_target, address *entry,
                                          const char *name, bool dest_uninitialized = false);

  // Helper for generating a dynamic type check.
  // Smashes no registers.
  void generate_type_check(Register sub_klass,
                           Register super_check_offset,
                           Register super_klass,
                           Label& L_success);

  // Generate checkcasting array copy stub
  address generate_checkcast_copy(const char *name, address *entry,
                                  bool dest_uninitialized = false);

  // Generate 'unsafe' array copy stub
  // Though just as safe as the other stubs, it takes an unscaled
  // size_t argument instead of an element count.
  //
  // Examines the alignment of the operands and dispatches
  // to a long, int, short, or byte copy loop.
  address generate_unsafe_copy(const char *name,
                               address byte_copy_entry, address short_copy_entry,
                               address int_copy_entry, address long_copy_entry);

  // Perform range checks on the proposed arraycopy.
  // Kills temp, but nothing else.
  // Also, clean the sign bits of src_pos and dst_pos.
  void arraycopy_range_checks(Register src,     // source array oop (c_rarg0)
                              Register src_pos, // source position (c_rarg1)
                              Register dst,     // destination array oo (c_rarg2)
                              Register dst_pos, // destination position (c_rarg3)
                              Register length,
                              Register temp,
                              Label& L_failed);

  // Generate generic array copy stubs
  address generate_generic_copy(const char *name,
                                address byte_copy_entry, address short_copy_entry,
                                address int_copy_entry, address oop_copy_entry,
                                address long_copy_entry, address checkcast_copy_entry);

  address generate_data_cache_writeback();

  address generate_data_cache_writeback_sync();

  void generate_arraycopy_stubs();


  // MD5 stubs

  // ofs and limit are use for multi-block byte array.
  // int com.sun.security.provider.MD5.implCompress(byte[] b, int ofs)
  address generate_md5_implCompress(bool multi_block, const char *name);


  // SHA stubs

  // ofs and limit are use for multi-block byte array.
  // int com.sun.security.provider.DigestBase.implCompressMultiBlock(byte[] b, int ofs, int limit)
  address generate_sha1_implCompress(bool multi_block, const char *name);

  // ofs and limit are use for multi-block byte array.
  // int com.sun.security.provider.DigestBase.implCompressMultiBlock(byte[] b, int ofs, int limit)
  address generate_sha256_implCompress(bool multi_block, const char *name);
  address generate_sha512_implCompress(bool multi_block, const char *name);

  // Mask for byte-swapping a couple of qwords in an XMM register using (v)pshufb.
  address generate_pshuffle_byte_flip_mask_sha512();

  address generate_upper_word_mask();
  address generate_shuffle_byte_flip_mask();
  address generate_pshuffle_byte_flip_mask();


  // AES intrinsic stubs

  address generate_aescrypt_encryptBlock();

  address generate_aescrypt_decryptBlock();

  address generate_cipherBlockChaining_encryptAESCrypt();

  // A version of CBC/AES Decrypt which does 4 blocks in a loop at a time
  // to hide instruction latency
  address generate_cipherBlockChaining_decryptAESCrypt_Parallel();

  address generate_electronicCodeBook_encryptAESCrypt();

  void aesecb_encrypt(Register source_addr, Register dest_addr, Register key, Register len);

  address generate_electronicCodeBook_decryptAESCrypt();

  void aesecb_decrypt(Register source_addr, Register dest_addr, Register key, Register len);

  // Vector AES Galois Counter Mode implementation
  address generate_galoisCounterMode_AESCrypt();
  void aesgcm_encrypt(Register in, Register len, Register ct, Register out, Register key,
                      Register state, Register subkeyHtbl, Register avx512_subkeyHtbl, Register counter);

  // AVX2 AES Galois Counter Mode implementation
  address generate_avx2_galoisCounterMode_AESCrypt();
  void aesgcm_avx2(Register in, Register len, Register ct, Register out, Register key,
                   Register state, Register subkeyHtbl, Register counter);

 // Vector AES Counter implementation
  address generate_counterMode_VectorAESCrypt();
  void aesctr_encrypt(Register src_addr, Register dest_addr, Register key, Register counter,
                      Register len_reg, Register used, Register used_addr, Register saved_encCounter_start);

  // This is a version of CTR/AES crypt which does 6 blocks in a loop at a time
  // to hide instruction latency
  address generate_counterMode_AESCrypt_Parallel();

  address generate_cipherBlockChaining_decryptVectorAESCrypt();

  address generate_key_shuffle_mask();

  void roundDec(XMMRegister xmm_reg);
  void roundDeclast(XMMRegister xmm_reg);
  void roundEnc(XMMRegister key, int rnum);
  void lastroundEnc(XMMRegister key, int rnum);
  void roundDec(XMMRegister key, int rnum);
  void lastroundDec(XMMRegister key, int rnum);
  void gfmul_avx512(XMMRegister ghash, XMMRegister hkey);
  void generateHtbl_48_block_zmm(Register htbl, Register avx512_subkeyHtbl, Register rscratch);
  void ghash16_encrypt16_parallel(Register key, Register subkeyHtbl, XMMRegister ctr_blockx,
                                  XMMRegister aad_hashx, Register in, Register out, Register data, Register pos, bool reduction,
                                  XMMRegister addmask, bool no_ghash_input, Register rounds, Register ghash_pos,
                                  bool final_reduction, int index, XMMRegister counter_inc_mask);
  // AVX2 AES-GCM related functions
  void initial_blocks_avx2(XMMRegister ctr, Register rounds, Register key, Register len,
                           Register in, Register out, Register ct, XMMRegister aad_hashx, Register pos);
  void gfmul_avx2(XMMRegister GH, XMMRegister HK);
  void generateHtbl_8_block_avx2(Register htbl);
  void ghash8_encrypt8_parallel_avx2(Register key, Register subkeyHtbl, XMMRegister ctr_blockx, Register in,
                                     Register out, Register ct, Register pos, bool out_order, Register rounds,
                                     XMMRegister xmm1, XMMRegister xmm2, XMMRegister xmm3, XMMRegister xmm4,
                                     XMMRegister xmm5, XMMRegister xmm6, XMMRegister xmm7, XMMRegister xmm8);
  void ghash_last_8_avx2(Register subkeyHtbl);

  // Load key and shuffle operation
  void ev_load_key(XMMRegister xmmdst, Register key, int offset, XMMRegister xmm_shuf_mask);
  void ev_load_key(XMMRegister xmmdst, Register key, int offset, Register rscratch);

  // Utility routine for loading a 128-bit key word in little endian format
  // can optionally specify that the shuffle mask is already in an xmmregister
  void load_key(XMMRegister xmmdst, Register key, int offset, XMMRegister xmm_shuf_mask);
  void load_key(XMMRegister xmmdst, Register key, int offset, Register rscratch);

  // Utility routine for increase 128bit counter (iv in CTR mode)
  void inc_counter(Register reg, XMMRegister xmmdst, int inc_delta, Label& next_block);
  void ev_add128(XMMRegister xmmdst, XMMRegister xmmsrc1, XMMRegister xmmsrc2,
                 int vector_len, KRegister ktmp, XMMRegister ones);
  void generate_aes_stubs();


  // GHASH stubs

  void generate_ghash_stubs();

  void schoolbookAAD(int i, Register subkeyH, XMMRegister data, XMMRegister tmp0,
                     XMMRegister tmp1, XMMRegister tmp2, XMMRegister tmp3);
  void gfmul(XMMRegister tmp0, XMMRegister t);
  void generateHtbl_one_block(Register htbl, Register rscratch);
  void generateHtbl_eight_blocks(Register htbl);
  void avx_ghash(Register state, Register htbl, Register data, Register blocks);

  // Used by GHASH and AES stubs.
  address ghash_polynomial_addr();
  address ghash_shufflemask_addr();
  address ghash_long_swap_mask_addr(); // byte swap x86 long
  address ghash_byte_swap_mask_addr(); // byte swap x86 byte array

  // Single and multi-block ghash operations
  address generate_ghash_processBlocks();

  // Ghash single and multi block operations using AVX instructions
  address generate_avx_ghash_processBlocks();

  // ChaCha20 stubs and helper functions
  void generate_chacha_stubs();
  address generate_chacha20Block_avx();
  address generate_chacha20Block_avx512();
  void cc20_quarter_round_avx(XMMRegister aVec, XMMRegister bVec,
    XMMRegister cVec, XMMRegister dVec, XMMRegister scratch,
    XMMRegister lrot8, XMMRegister lrot16, int vector_len);
  void cc20_shift_lane_org(XMMRegister bVec, XMMRegister cVec,
    XMMRegister dVec, int vector_len, bool colToDiag);
  void cc20_keystream_collate_avx512(XMMRegister aVec, XMMRegister bVec,
    XMMRegister cVec, XMMRegister dVec, Register baseAddr, int baseOffset);

  // Poly1305 multiblock using IFMA instructions
  address generate_poly1305_processBlocks();
  void poly1305_process_blocks_avx512(const Register input, const Register length,
                                      const Register A0, const Register A1, const Register A2,
                                      const Register R0, const Register R1, const Register C1);
  void poly1305_multiply_scalar(const Register a0, const Register a1, const Register a2,
                                const Register r0, const Register r1, const Register c1, bool only128,
                                const Register t0, const Register t1, const Register t2,
                                const Register mulql, const Register mulqh);
  void poly1305_multiply8_avx512(const XMMRegister A0, const XMMRegister A1, const XMMRegister A2,
                                 const XMMRegister R0, const XMMRegister R1, const XMMRegister R2, const XMMRegister R1P, const XMMRegister R2P,
                                 const XMMRegister P0L, const XMMRegister P0H, const XMMRegister P1L, const XMMRegister P1H, const XMMRegister P2L, const XMMRegister P2H,
                                 const XMMRegister TMP, const Register rscratch);
  void poly1305_limbs(const Register limbs, const Register a0, const Register a1, const Register a2, const Register t0, const Register t1);
  void poly1305_limbs_out(const Register a0, const Register a1, const Register a2, const Register limbs, const Register t0, const Register t1);
  void poly1305_limbs_avx512(const XMMRegister D0, const XMMRegister D1,
                             const XMMRegister L0, const XMMRegister L1, const XMMRegister L2, bool padMSG,
                             const XMMRegister TMP, const Register rscratch);

  // BASE64 stubs

  address base64_shuffle_addr();
  address base64_avx2_shuffle_addr();
  address base64_avx2_input_mask_addr();
  address base64_avx2_lut_addr();
  address base64_encoding_table_addr();

  // Code for generating Base64 encoding.
  // Intrinsic function prototype in Base64.java:
  // private void encodeBlock(byte[] src, int sp, int sl, byte[] dst, int dp, boolean isURL)
  address generate_base64_encodeBlock();

  // base64 AVX512vbmi tables
  address base64_vbmi_lookup_lo_addr();
  address base64_vbmi_lookup_hi_addr();
  address base64_vbmi_lookup_lo_url_addr();
  address base64_vbmi_lookup_hi_url_addr();
  address base64_vbmi_pack_vec_addr();
  address base64_vbmi_join_0_1_addr();
  address base64_vbmi_join_1_2_addr();
  address base64_vbmi_join_2_3_addr();
  address base64_decoding_table_addr();
  address base64_AVX2_decode_tables_addr();
  address base64_AVX2_decode_LUT_tables_addr();

  // Code for generating Base64 decoding.
  //
  // Based on the article (and associated code) from https://arxiv.org/abs/1910.05109.
  //
  // Intrinsic function prototype in Base64.java:
  // private void decodeBlock(byte[] src, int sp, int sl, byte[] dst, int dp, boolean isURL, isMIME);
  address generate_base64_decodeBlock();

  address generate_updateBytesCRC32();
  address generate_updateBytesCRC32C(bool is_pclmulqdq_supported);

  address generate_updateBytesAdler32();

  address generate_multiplyToLen();

  address generate_vectorizedMismatch();

  address generate_squareToLen();

  address generate_method_entry_barrier();

  address generate_mulAdd();

  address generate_bigIntegerRightShift();
  address generate_bigIntegerLeftShift();

  address generate_float16ToFloat();
  address generate_floatToFloat16();

  // Libm trigonometric stubs

  address generate_libmSin();
  address generate_libmCos();
  address generate_libmTan();
  address generate_libmExp();
  address generate_libmPow();
  address generate_libmLog();
  address generate_libmLog10();
  address generate_libmFmod();

  // Shared constants
  static address ZERO;
  static address NEG_ZERO;
  static address ONE;
  static address ONEHALF;
  static address SIGN_MASK;
  static address TWO_POW_55;
  static address TWO_POW_M55;
  static address SHIFTER;
  static address PI32INV;
  static address PI_INV_TABLE;
  static address Ctable;
  static address SC_1;
  static address SC_2;
  static address SC_3;
  static address SC_4;
  static address PI_4;
  static address P_1;
  static address P_3;
  static address P_2;

  void generate_libm_stubs();


  address generate_cont_thaw(const char* label, Continuation::thaw_kind kind);
  address generate_cont_thaw();

  // TODO: will probably need multiple return barriers depending on return type
  address generate_cont_returnBarrier();
  address generate_cont_returnBarrier_exception();

#if INCLUDE_JFR
  void generate_jfr_stubs();
  // For c2: c_rarg0 is junk, call to runtime to write a checkpoint.
  // It returns a jobject handle to the event writer.
  // The handle is dereferenced and the return value is the event writer oop.
  RuntimeStub* generate_jfr_write_checkpoint();
  // For c2: call to runtime to return a buffer lease.
  RuntimeStub* generate_jfr_return_lease();
#endif // INCLUDE_JFR

  // Continuation point for throwing of implicit exceptions that are
  // not handled in the current activation. Fabricates an exception
  // oop and initiates normal exception dispatching in this
  // frame. Since we need to preserve callee-saved values (currently
  // only for C2, but done for C1 as well) we need a callee-saved oop
  // map and therefore have to make these stubs into RuntimeStubs
  // rather than BufferBlobs.  If the compiler needs all registers to
  // be preserved between the fault point and the exception handler
  // then it must assume responsibility for that in
  // AbstractCompiler::continuation_for_implicit_null_exception or
  // continuation_for_implicit_division_by_zero_exception. All other
  // implicit exceptions (e.g., NullPointerException or
  // AbstractMethodError on entry) are either at call sites or
  // otherwise assume that stack unwinding will be initiated, so
  // caller saved registers were assumed volatile in the compiler.
  address generate_throw_exception(const char* name,
                                   address runtime_entry,
                                   Register arg1 = noreg,
                                   Register arg2 = noreg);

  // shared exception handler for FFM upcall stubs
  address generate_upcall_stub_exception_handler();

  void create_control_words();

  // Initialization
  void generate_initial_stubs();
  void generate_continuation_stubs();
  void generate_compiler_stubs();
  void generate_final_stubs();

 public:
  StubGenerator(CodeBuffer* code, StubsKind kind);
};

#endif // CPU_X86_STUBGENERATOR_X86_64_HPP
