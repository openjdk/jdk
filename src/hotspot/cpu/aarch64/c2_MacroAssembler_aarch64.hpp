/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef CPU_AARCH64_C2_MACROASSEMBLER_AARCH64_HPP
#define CPU_AARCH64_C2_MACROASSEMBLER_AARCH64_HPP

// C2_MacroAssembler contains high-level macros for C2

 private:
  // Return true if the phase output is in the scratch emit size mode.
  virtual bool in_scratch_emit_size() override;

  void neon_reduce_logical_helper(int opc, bool sf, Register Rd, Register Rn, Register Rm,
                                  enum shift_kind kind = Assembler::LSL, unsigned shift = 0);

 public:
  // jdk.internal.util.ArraysSupport.vectorizedHashCode
  address arrays_hashcode(Register ary, Register cnt, Register result, FloatRegister vdata0,
                          FloatRegister vdata1, FloatRegister vdata2, FloatRegister vdata3,
                          FloatRegister vmul0, FloatRegister vmul1, FloatRegister vmul2,
                          FloatRegister vmul3, FloatRegister vpow, FloatRegister vpowm,
                          BasicType eltype);

  // Code used by cmpFastLock and cmpFastUnlock mach instructions in .ad file.
  void fast_lock(Register object, Register box, Register tmp, Register tmp2, Register tmp3);
  void fast_unlock(Register object, Register box, Register tmp, Register tmp2);
  // Code used by cmpFastLockLightweight and cmpFastUnlockLightweight mach instructions in .ad file.
  void fast_lock_lightweight(Register object, Register box, Register t1, Register t2, Register t3);
  void fast_unlock_lightweight(Register object, Register box, Register t1, Register t2, Register t3);

  void string_compare(Register str1, Register str2,
                      Register cnt1, Register cnt2, Register result,
                      Register tmp1, Register tmp2, FloatRegister vtmp1,
                      FloatRegister vtmp2, FloatRegister vtmp3,
                      PRegister pgtmp1, PRegister pgtmp2, int ae);

  void string_indexof(Register str1, Register str2,
                      Register cnt1, Register cnt2,
                      Register tmp1, Register tmp2,
                      Register tmp3, Register tmp4,
                      Register tmp5, Register tmp6,
                      int int_cnt1, Register result, int ae);

  void string_indexof_char(Register str1, Register cnt1,
                           Register ch, Register result,
                           Register tmp1, Register tmp2, Register tmp3);

  void stringL_indexof_char(Register str1, Register cnt1,
                            Register ch, Register result,
                            Register tmp1, Register tmp2, Register tmp3);

  void string_indexof_char_sve(Register str1, Register cnt1,
                               Register ch, Register result,
                               FloatRegister ztmp1, FloatRegister ztmp2,
                               PRegister pgtmp, PRegister ptmp, bool isL);

  // Compress the least significant bit of each byte to the rightmost and clear
  // the higher garbage bits.
  void bytemask_compress(Register dst);

  // Pack the lowest-numbered bit of each mask element in src into a long value
  // in dst, at most the first 64 lane elements.
  void sve_vmask_tolong(Register dst, PRegister src, BasicType bt, int lane_cnt,
                        FloatRegister vtmp1, FloatRegister vtmp2);

  // Unpack the mask, a long value in src, into predicate register dst based on the
  // corresponding data type. Note that dst can support at most 64 lanes.
  void sve_vmask_fromlong(PRegister dst, Register src, BasicType bt, int lane_cnt,
                          FloatRegister vtmp1, FloatRegister vtmp2);

  // SIMD&FP comparison
  void neon_compare(FloatRegister dst, BasicType bt, FloatRegister src1,
                    FloatRegister src2, Condition cond, bool isQ);

  void neon_compare_zero(FloatRegister dst, BasicType bt, FloatRegister src,
                         Condition cond, bool isQ);

  void sve_compare(PRegister pd, BasicType bt, PRegister pg,
                   FloatRegister zn, FloatRegister zm, Condition cond);

  void sve_vmask_lasttrue(Register dst, BasicType bt, PRegister src, PRegister ptmp);

  // Vector cast
  void neon_vector_extend(FloatRegister dst, BasicType dst_bt, unsigned dst_vlen_in_bytes,
                          FloatRegister src, BasicType src_bt, bool is_unsigned = false);

  void neon_vector_narrow(FloatRegister dst, BasicType dst_bt,
                          FloatRegister src, BasicType src_bt, unsigned src_vlen_in_bytes);

  void sve_vector_extend(FloatRegister dst, SIMD_RegVariant dst_size,
                         FloatRegister src, SIMD_RegVariant src_size, bool is_unsigned = false);

  void sve_vector_narrow(FloatRegister dst, SIMD_RegVariant dst_size,
                         FloatRegister src, SIMD_RegVariant src_size, FloatRegister tmp);

  void sve_vmaskcast_extend(PRegister dst, PRegister src,
                            uint dst_element_length_in_bytes, uint src_element_lenght_in_bytes);

  void sve_vmaskcast_narrow(PRegister dst, PRegister src, PRegister ptmp,
                            uint dst_element_length_in_bytes, uint src_element_lenght_in_bytes);

  // Vector reduction
  void neon_reduce_add_integral(Register dst, BasicType bt,
                                Register isrc, FloatRegister vsrc,
                                unsigned vector_length_in_bytes, FloatRegister vtmp);

  void neon_reduce_mul_integral(Register dst, BasicType bt,
                                Register isrc, FloatRegister vsrc,
                                unsigned vector_length_in_bytes,
                                FloatRegister vtmp1, FloatRegister vtmp2);

  void neon_reduce_mul_fp(FloatRegister dst, BasicType bt,
                          FloatRegister fsrc, FloatRegister vsrc,
                          unsigned vector_length_in_bytes, FloatRegister vtmp);

  void neon_reduce_logical(int opc, Register dst, BasicType bt, Register isrc,
                           FloatRegister vsrc, unsigned vector_length_in_bytes);

  void neon_reduce_minmax_integral(int opc, Register dst, BasicType bt,
                                   Register isrc, FloatRegister vsrc,
                                   unsigned vector_length_in_bytes, FloatRegister vtmp);

  void sve_reduce_integral(int opc, Register dst, BasicType bt, Register src1,
                           FloatRegister src2, PRegister pg, FloatRegister tmp);

  // Set elements of the dst predicate to true for lanes in the range of
  // [0, lane_cnt), or to false otherwise. The input "lane_cnt" should be
  // smaller than or equal to the supported max vector length of the basic
  // type. Clobbers: rscratch1 and the rFlagsReg.
  void sve_gen_mask_imm(PRegister dst, BasicType bt, uint32_t lane_cnt);

  // Extract a scalar element from an sve vector at position 'idx'.
  // The input elements in src are expected to be of integral type.
  void sve_extract_integral(Register dst, BasicType bt, FloatRegister src,
                            int idx, FloatRegister vtmp);

  // java.lang.Math::round intrinsics
  void vector_round_neon(FloatRegister dst, FloatRegister src, FloatRegister tmp1,
                         FloatRegister tmp2, FloatRegister tmp3,
                         SIMD_Arrangement T);
  void vector_round_sve(FloatRegister dst, FloatRegister src, FloatRegister tmp1,
                        FloatRegister tmp2, PRegister pgtmp,
                        SIMD_RegVariant T);

  // Pack active elements of src, under the control of mask, into the
  // lowest-numbered elements of dst. Any remaining elements of dst will
  // be filled with zero.
  void sve_compress_byte(FloatRegister dst, FloatRegister src, PRegister mask,
                         FloatRegister vtmp1, FloatRegister vtmp2,
                         FloatRegister vtmp3, FloatRegister vtmp4,
                         PRegister ptmp, PRegister pgtmp);

  void sve_compress_short(FloatRegister dst, FloatRegister src, PRegister mask,
                          FloatRegister vtmp1, FloatRegister vtmp2,
                          PRegister pgtmp);

  void neon_reverse_bits(FloatRegister dst, FloatRegister src, BasicType bt, bool isQ);

  void neon_reverse_bytes(FloatRegister dst, FloatRegister src, BasicType bt, bool isQ);

  void neon_rearrange_hsd(FloatRegister dst, FloatRegister src, FloatRegister shuffle,
                          FloatRegister tmp, BasicType bt, bool isQ);
  // java.lang.Math::signum intrinsics
  void vector_signum_neon(FloatRegister dst, FloatRegister src, FloatRegister zero,
                          FloatRegister one, SIMD_Arrangement T);

  void vector_signum_sve(FloatRegister dst, FloatRegister src, FloatRegister zero,
                         FloatRegister one, FloatRegister vtmp, PRegister pgtmp, SIMD_RegVariant T);

  void verify_int_in_range(uint idx, const TypeInt* t, Register val, Register tmp);
  void verify_long_in_range(uint idx, const TypeLong* t, Register val, Register tmp);

  void reconstruct_frame_pointer(Register rtmp);

#endif // CPU_AARCH64_C2_MACROASSEMBLER_AARCH64_HPP
