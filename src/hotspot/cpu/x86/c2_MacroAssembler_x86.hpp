/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef CPU_X86_C2_MACROASSEMBLER_X86_HPP
#define CPU_X86_C2_MACROASSEMBLER_X86_HPP

// C2_MacroAssembler contains high-level macros for C2

public:
  // C2 compiled method's prolog code.
  void verified_entry(int framesize, int stack_bang_size, bool fp_mode_24b, bool is_stub);

  Assembler::AvxVectorLen vector_length_encoding(int vlen_in_bytes);

  // Code used by cmpFastLock and cmpFastUnlock mach instructions in .ad file.
  // See full description in macroAssembler_x86.cpp.
  void fast_lock(Register obj, Register box, Register tmp,
                 Register scr, Register cx1, Register cx2, Register thread,
                 Metadata* method_data);
  void fast_unlock(Register obj, Register box, Register tmp);

  void fast_lock_lightweight(Register obj, Register box, Register rax_reg,
                             Register t, Register thread);
  void fast_unlock_lightweight(Register obj, Register reg_rax, Register t, Register thread);

  // Generic instructions support for use in .ad files C2 code generation
  void vabsnegd(int opcode, XMMRegister dst, XMMRegister src);
  void vabsnegd(int opcode, XMMRegister dst, XMMRegister src, int vector_len);
  void vabsnegf(int opcode, XMMRegister dst, XMMRegister src);
  void vabsnegf(int opcode, XMMRegister dst, XMMRegister src, int vector_len);

  void pminmax(int opcode, BasicType elem_bt, XMMRegister dst, XMMRegister src,
               XMMRegister tmp = xnoreg);
  void vpminmax(int opcode, BasicType elem_bt,
                XMMRegister dst, XMMRegister src1, XMMRegister src2,
                int vlen_enc);

  void vminmax_fp(int opcode, BasicType elem_bt,
                  XMMRegister dst, XMMRegister a, XMMRegister b,
                  XMMRegister tmp, XMMRegister atmp, XMMRegister btmp,
                  int vlen_enc);
  void evminmax_fp(int opcode, BasicType elem_bt,
                   XMMRegister dst, XMMRegister a, XMMRegister b,
                   KRegister ktmp, XMMRegister atmp, XMMRegister btmp,
                   int vlen_enc);

  void signum_fp(int opcode, XMMRegister dst, XMMRegister zero, XMMRegister one);

  void vector_compress_expand(int opcode, XMMRegister dst, XMMRegister src, KRegister mask,
                              bool merge, BasicType bt, int vec_enc);

  void vector_mask_compress(KRegister dst, KRegister src, Register rtmp1, Register rtmp2, int mask_len);

  void vextendbw(bool sign, XMMRegister dst, XMMRegister src, int vector_len);
  void vextendbw(bool sign, XMMRegister dst, XMMRegister src);
  void vextendbd(bool sign, XMMRegister dst, XMMRegister src, int vector_len);
  void vextendwd(bool sign, XMMRegister dst, XMMRegister src, int vector_len);

  void vshiftd(int opcode, XMMRegister dst, XMMRegister shift);
  void vshiftd_imm(int opcode, XMMRegister dst, int shift);
  void vshiftd(int opcode, XMMRegister dst, XMMRegister src, XMMRegister shift, int vlen_enc);
  void vshiftd_imm(int opcode, XMMRegister dst, XMMRegister nds, int shift, int vector_len);
  void vshiftw(int opcode, XMMRegister dst, XMMRegister shift);
  void vshiftw(int opcode, XMMRegister dst, XMMRegister src, XMMRegister shift, int vlen_enc);
  void vshiftq(int opcode, XMMRegister dst, XMMRegister shift);
  void vshiftq_imm(int opcode, XMMRegister dst, int shift);
  void vshiftq(int opcode, XMMRegister dst, XMMRegister src, XMMRegister shift, int vlen_enc);
  void vshiftq_imm(int opcode, XMMRegister dst, XMMRegister nds, int shift, int vector_len);

  void vprotate_imm(int opcode, BasicType etype, XMMRegister dst, XMMRegister src, int shift, int vector_len);
  void vprotate_var(int opcode, BasicType etype, XMMRegister dst, XMMRegister src, XMMRegister shift, int vector_len);

  void varshiftd(int opcode, XMMRegister dst, XMMRegister src, XMMRegister shift, int vlen_enc);
  void varshiftw(int opcode, XMMRegister dst, XMMRegister src, XMMRegister shift, int vlen_enc);
  void varshiftq(int opcode, XMMRegister dst, XMMRegister src, XMMRegister shift, int vlen_enc, XMMRegister vtmp = xnoreg);
  void varshiftbw(int opcode, XMMRegister dst, XMMRegister src, XMMRegister shift, int vector_len, XMMRegister vtmp);
  void evarshiftb(int opcode, XMMRegister dst, XMMRegister src, XMMRegister shift, int vector_len, XMMRegister vtmp);

  void insert(BasicType typ, XMMRegister dst, Register val, int idx);
  void vinsert(BasicType typ, XMMRegister dst, XMMRegister src, Register val, int idx);
  void vgather(BasicType typ, XMMRegister dst, Register base, XMMRegister idx, XMMRegister mask, int vector_len);
  void evgather(BasicType typ, XMMRegister dst, KRegister mask, Register base, XMMRegister idx, int vector_len);
  void evscatter(BasicType typ, Register base, XMMRegister idx, KRegister mask, XMMRegister src, int vector_len);

  void evmovdqu(BasicType type, KRegister kmask, XMMRegister dst, Address src, bool merge, int vector_len);
  void evmovdqu(BasicType type, KRegister kmask, Address dst, XMMRegister src, bool merge, int vector_len);

  // extract
  void extract(BasicType typ, Register dst, XMMRegister src, int idx);
  XMMRegister get_lane(BasicType typ, XMMRegister dst, XMMRegister src, int elemindex);
  void get_elem(BasicType typ, Register dst, XMMRegister src, int elemindex);
  void get_elem(BasicType typ, XMMRegister dst, XMMRegister src, int elemindex, XMMRegister vtmp = xnoreg);
  void movsxl(BasicType typ, Register dst);

  // vector test
  void vectortest(BasicType bt, XMMRegister src1, XMMRegister src2, XMMRegister vtmp, int vlen_in_bytes);

 // Covert B2X
 void vconvert_b2x(BasicType to_elem_bt, XMMRegister dst, XMMRegister src, int vlen_enc);
#ifdef _LP64
 void vpbroadcast(BasicType elem_bt, XMMRegister dst, Register src, int vlen_enc);
#endif

  // blend
  void evpcmp(BasicType typ, KRegister kdmask, KRegister ksmask, XMMRegister src1, XMMRegister    src2, int comparison, int vector_len);
  void evpcmp(BasicType typ, KRegister kdmask, KRegister ksmask, XMMRegister src1, AddressLiteral src2, int comparison, int vector_len, Register rscratch = noreg);
  void evpblend(BasicType typ, XMMRegister dst, KRegister kmask, XMMRegister src1, XMMRegister src2, bool merge, int vector_len);

  void load_vector(XMMRegister dst, Address        src, int vlen_in_bytes);
  void load_vector(XMMRegister dst, AddressLiteral src, int vlen_in_bytes, Register rscratch = noreg);

  void load_vector_mask(XMMRegister dst, XMMRegister src, int vlen_in_bytes, BasicType elem_bt, bool is_legacy);
  void load_vector_mask(KRegister   dst, XMMRegister src, XMMRegister xtmp, bool novlbwdq, int vlen_enc);

  void load_constant_vector(BasicType bt, XMMRegister dst, InternalAddress src, int vlen);
  void load_iota_indices(XMMRegister dst, int vlen_in_bytes, BasicType bt);

  // Reductions for vectors of bytes, shorts, ints, longs, floats, and doubles.

  // dst = src1  reduce(op, src2) using vtmp as temps
  void reduceI(int opcode, int vlen, Register dst, Register src1, XMMRegister src2, XMMRegister vtmp1, XMMRegister vtmp2);
#ifdef _LP64
  void reduceL(int opcode, int vlen, Register dst, Register src1, XMMRegister src2, XMMRegister vtmp1, XMMRegister vtmp2);
  void genmask(KRegister dst, Register len, Register temp);
#endif // _LP64

  // dst = reduce(op, src2) using vtmp as temps
  void reduce_fp(int opcode, int vlen,
                 XMMRegister dst, XMMRegister src,
                 XMMRegister vtmp1, XMMRegister vtmp2 = xnoreg);
  void reduceB(int opcode, int vlen, Register dst, Register src1, XMMRegister src2, XMMRegister vtmp1, XMMRegister vtmp2);
  void mulreduceB(int opcode, int vlen, Register dst, Register src1, XMMRegister src2, XMMRegister vtmp1, XMMRegister vtmp2);
  void reduceS(int opcode, int vlen, Register dst, Register src1, XMMRegister src2, XMMRegister vtmp1, XMMRegister vtmp2);
  void reduceFloatMinMax(int opcode, int vlen, bool is_dst_valid,
                         XMMRegister dst, XMMRegister src,
                         XMMRegister tmp, XMMRegister atmp, XMMRegister btmp, XMMRegister xmm_0, XMMRegister xmm_1 = xnoreg);
  void reduceDoubleMinMax(int opcode, int vlen, bool is_dst_valid,
                          XMMRegister dst, XMMRegister src,
                          XMMRegister tmp, XMMRegister atmp, XMMRegister btmp, XMMRegister xmm_0, XMMRegister xmm_1 = xnoreg);
 private:
  void reduceF(int opcode, int vlen, XMMRegister dst, XMMRegister src, XMMRegister vtmp1, XMMRegister vtmp2);
  void reduceD(int opcode, int vlen, XMMRegister dst, XMMRegister src, XMMRegister vtmp1, XMMRegister vtmp2);

  // Int Reduction
  void reduce2I (int opcode, Register dst, Register src1, XMMRegister src2, XMMRegister vtmp1, XMMRegister vtmp2);
  void reduce4I (int opcode, Register dst, Register src1, XMMRegister src2, XMMRegister vtmp1, XMMRegister vtmp2);
  void reduce8I (int opcode, Register dst, Register src1, XMMRegister src2, XMMRegister vtmp1, XMMRegister vtmp2);
  void reduce16I(int opcode, Register dst, Register src1, XMMRegister src2, XMMRegister vtmp1, XMMRegister vtmp2);

  // Byte Reduction
  void reduce8B (int opcode, Register dst, Register src1, XMMRegister src2, XMMRegister vtmp1, XMMRegister vtmp2);
  void reduce16B(int opcode, Register dst, Register src1, XMMRegister src2, XMMRegister vtmp1, XMMRegister vtmp2);
  void reduce32B(int opcode, Register dst, Register src1, XMMRegister src2, XMMRegister vtmp1, XMMRegister vtmp2);
  void reduce64B(int opcode, Register dst, Register src1, XMMRegister src2, XMMRegister vtmp1, XMMRegister vtmp2);
  void mulreduce8B (int opcode, Register dst, Register src1, XMMRegister src2, XMMRegister vtmp1, XMMRegister vtmp2);
  void mulreduce16B(int opcode, Register dst, Register src1, XMMRegister src2, XMMRegister vtmp1, XMMRegister vtmp2);
  void mulreduce32B(int opcode, Register dst, Register src1, XMMRegister src2, XMMRegister vtmp1, XMMRegister vtmp2);
  void mulreduce64B(int opcode, Register dst, Register src1, XMMRegister src2, XMMRegister vtmp1, XMMRegister vtmp2);

  // Short Reduction
  void reduce4S (int opcode, Register dst, Register src1, XMMRegister src2, XMMRegister vtmp1, XMMRegister vtmp2);
  void reduce8S (int opcode, Register dst, Register src1, XMMRegister src2, XMMRegister vtmp1, XMMRegister vtmp2);
  void reduce16S(int opcode, Register dst, Register src1, XMMRegister src2, XMMRegister vtmp1, XMMRegister vtmp2);
  void reduce32S(int opcode, Register dst, Register src1, XMMRegister src2, XMMRegister vtmp1, XMMRegister vtmp2);

  // Long Reduction
#ifdef _LP64
  void reduce2L(int opcode, Register dst, Register src1, XMMRegister src2, XMMRegister vtmp1, XMMRegister vtmp2);
  void reduce4L(int opcode, Register dst, Register src1, XMMRegister src2, XMMRegister vtmp1, XMMRegister vtmp2);
  void reduce8L(int opcode, Register dst, Register src1, XMMRegister src2, XMMRegister vtmp1, XMMRegister vtmp2);
#endif // _LP64

  // Float Reduction
  void reduce2F (int opcode, XMMRegister dst, XMMRegister src, XMMRegister vtmp);
  void reduce4F (int opcode, XMMRegister dst, XMMRegister src, XMMRegister vtmp);
  void reduce8F (int opcode, XMMRegister dst, XMMRegister src, XMMRegister vtmp1, XMMRegister vtmp2);
  void reduce16F(int opcode, XMMRegister dst, XMMRegister src, XMMRegister vtmp1, XMMRegister vtmp2);

  // Double Reduction
  void reduce2D(int opcode, XMMRegister dst, XMMRegister src, XMMRegister vtmp);
  void reduce4D(int opcode, XMMRegister dst, XMMRegister src, XMMRegister vtmp1, XMMRegister vtmp2);
  void reduce8D(int opcode, XMMRegister dst, XMMRegister src, XMMRegister vtmp1, XMMRegister vtmp2);

  // Base reduction instruction
  void reduce_operation_128(BasicType typ, int opcode, XMMRegister dst, XMMRegister src);
  void reduce_operation_256(BasicType typ, int opcode, XMMRegister dst, XMMRegister src1, XMMRegister src2);

 public:
#ifdef _LP64
  void vector_mask_operation_helper(int opc, Register dst, Register tmp, int masklen);

  void vector_mask_operation(int opc, Register dst, KRegister mask, Register tmp, int masklen, int masksize, int vec_enc);

  void vector_mask_operation(int opc, Register dst, XMMRegister mask, XMMRegister xtmp,
                             Register tmp, int masklen, BasicType bt, int vec_enc);
  void vector_long_to_maskvec(XMMRegister dst, Register src, Register rtmp1,
                              Register rtmp2, XMMRegister xtmp, int mask_len, int vec_enc);
#endif

  void vector_maskall_operation(KRegister dst, Register src, int mask_len);

#ifndef _LP64
  void vector_maskall_operation32(KRegister dst, Register src, KRegister ktmp, int mask_len);
#endif

  void string_indexof_char(Register str1, Register cnt1, Register ch, Register result,
                           XMMRegister vec1, XMMRegister vec2, XMMRegister vec3, Register tmp);

  void stringL_indexof_char(Register str1, Register cnt1, Register ch, Register result,
                           XMMRegister vec1, XMMRegister vec2, XMMRegister vec3, Register tmp);

  // IndexOf strings.
  // Small strings are loaded through stack if they cross page boundary.
  void string_indexof(Register str1, Register str2,
                      Register cnt1, Register cnt2,
                      int int_cnt2,  Register result,
                      XMMRegister vec, Register tmp,
                      int ae);

  // IndexOf for constant substrings with size >= 8 elements
  // which don't need to be loaded through stack.
  void string_indexofC8(Register str1, Register str2,
                      Register cnt1, Register cnt2,
                      int int_cnt2,  Register result,
                      XMMRegister vec, Register tmp,
                      int ae);

    // Smallest code: we don't need to load through stack,
    // check string tail.

  // helper function for string_compare
  void load_next_elements(Register elem1, Register elem2, Register str1, Register str2,
                          Address::ScaleFactor scale, Address::ScaleFactor scale1,
                          Address::ScaleFactor scale2, Register index, int ae);
  // Compare strings.
  void string_compare(Register str1, Register str2,
                      Register cnt1, Register cnt2, Register result,
                      XMMRegister vec1, int ae, KRegister mask = knoreg);

  // Search for Non-ASCII character (Negative byte value) in a byte array,
  // return index of the first such character, otherwise len.
  void count_positives(Register ary1, Register len,
                       Register result, Register tmp1,
                       XMMRegister vec1, XMMRegister vec2, KRegister mask1 = knoreg, KRegister mask2 = knoreg);

  // Compare char[] or byte[] arrays.
  void arrays_equals(bool is_array_equ, Register ary1, Register ary2, Register limit,
                     Register result, Register chr, XMMRegister vec1, XMMRegister vec2,
                     bool is_char, KRegister mask = knoreg, bool expand_ary2 = false);

  void arrays_hashcode(Register str1, Register cnt1, Register result,
                       Register tmp1, Register tmp2, Register tmp3, XMMRegister vnext,
                       XMMRegister vcoef0, XMMRegister vcoef1, XMMRegister vcoef2, XMMRegister vcoef3,
                       XMMRegister vresult0, XMMRegister vresult1, XMMRegister vresult2, XMMRegister vresult3,
                       XMMRegister vtmp0, XMMRegister vtmp1, XMMRegister vtmp2, XMMRegister vtmp3,
                       BasicType eltype);

  // helper functions for arrays_hashcode
  int arrays_hashcode_elsize(BasicType eltype);
  void arrays_hashcode_elload(Register dst, Address src, BasicType eltype);
  void arrays_hashcode_elvload(XMMRegister dst, Address src, BasicType eltype);
  void arrays_hashcode_elvload(XMMRegister dst, AddressLiteral src, BasicType eltype);
  void arrays_hashcode_elvcast(XMMRegister dst, BasicType eltype);

#ifdef _LP64
  void convertF2I(BasicType dst_bt, BasicType src_bt, Register dst, XMMRegister src);
#endif

  void evmasked_op(int ideal_opc, BasicType eType, KRegister mask,
                   XMMRegister dst, XMMRegister src1, XMMRegister src2,
                   bool merge, int vlen_enc, bool is_varshift = false);

  void evmasked_op(int ideal_opc, BasicType eType, KRegister mask,
                   XMMRegister dst, XMMRegister src1, Address src2,
                   bool merge, int vlen_enc);

  void evmasked_op(int ideal_opc, BasicType eType, KRegister mask, XMMRegister dst,
                   XMMRegister src1, int imm8, bool merge, int vlen_enc);

  void masked_op(int ideal_opc, int mask_len, KRegister dst,
                 KRegister src1, KRegister src2);

  void vector_unsigned_cast(XMMRegister dst, XMMRegister src, int vlen_enc,
                            BasicType from_elem_bt, BasicType to_elem_bt);

  void vector_signed_cast(XMMRegister dst, XMMRegister src, int vlen_enc,
                          BasicType from_elem_bt, BasicType to_elem_bt);

  void vector_cast_int_to_subword(BasicType to_elem_bt, XMMRegister dst, XMMRegister zero,
                                  XMMRegister xtmp, Register rscratch, int vec_enc);

  void vector_castF2X_avx(BasicType to_elem_bt, XMMRegister dst, XMMRegister src, XMMRegister xtmp1,
                          XMMRegister xtmp2, XMMRegister xtmp3, XMMRegister xtmp4,
                          AddressLiteral float_sign_flip, Register rscratch, int vec_enc);

  void vector_castF2X_evex(BasicType to_elem_bt, XMMRegister dst, XMMRegister src, XMMRegister xtmp1,
                           XMMRegister xtmp2, KRegister ktmp1, KRegister ktmp2, AddressLiteral float_sign_flip,
                           Register rscratch, int vec_enc);

  void vector_castF2L_evex(XMMRegister dst, XMMRegister src, XMMRegister xtmp1, XMMRegister xtmp2,
                           KRegister ktmp1, KRegister ktmp2, AddressLiteral double_sign_flip,
                           Register rscratch, int vec_enc);

  void vector_castD2X_evex(BasicType to_elem_bt, XMMRegister dst, XMMRegister src, XMMRegister xtmp1,
                           XMMRegister xtmp2, KRegister ktmp1, KRegister ktmp2, AddressLiteral sign_flip,
                           Register rscratch, int vec_enc);

  void vector_castD2X_avx(BasicType to_elem_bt, XMMRegister dst, XMMRegister src, XMMRegister xtmp1,
                          XMMRegister xtmp2, XMMRegister xtmp3, XMMRegister xtmp4, XMMRegister xtmp5,
                          AddressLiteral float_sign_flip, Register rscratch, int vec_enc);


  void vector_cast_double_to_int_special_cases_avx(XMMRegister dst, XMMRegister src, XMMRegister xtmp1, XMMRegister xtmp2,
                                                   XMMRegister xtmp3, XMMRegister xtmp4, XMMRegister xtmp5, Register rscratch,
                                                   AddressLiteral float_sign_flip, int vec_enc);

  void vector_cast_double_to_int_special_cases_evex(XMMRegister dst, XMMRegister src, XMMRegister xtmp1, XMMRegister xtmp2,
                                                    KRegister ktmp1, KRegister ktmp2, Register rscratch, AddressLiteral float_sign_flip,
                                                    int vec_enc);

  void vector_cast_double_to_long_special_cases_evex(XMMRegister dst, XMMRegister src, XMMRegister xtmp1, XMMRegister xtmp2,
                                                     KRegister ktmp1, KRegister ktmp2, Register rscratch, AddressLiteral double_sign_flip,
                                                     int vec_enc);

  void vector_cast_float_to_int_special_cases_evex(XMMRegister dst, XMMRegister src, XMMRegister xtmp1, XMMRegister xtmp2,
                                                   KRegister ktmp1, KRegister ktmp2, Register rscratch, AddressLiteral float_sign_flip,
                                                   int vec_enc);

  void vector_cast_float_to_long_special_cases_evex(XMMRegister dst, XMMRegister src, XMMRegister xtmp1, XMMRegister xtmp2,
                                                    KRegister ktmp1, KRegister ktmp2, Register rscratch, AddressLiteral double_sign_flip,
                                                    int vec_enc);

  void vector_cast_float_to_int_special_cases_avx(XMMRegister dst, XMMRegister src, XMMRegister xtmp1, XMMRegister xtmp2, XMMRegister xtmp3,
                                                  XMMRegister xtmp4, Register rscratch, AddressLiteral float_sign_flip,
                                                  int vec_enc);

  void vector_crosslane_doubleword_pack_avx(XMMRegister dst, XMMRegister src, XMMRegister zero,
                                            XMMRegister xtmp, int index, int vec_enc);

  void vector_mask_cast(XMMRegister dst, XMMRegister src, BasicType dst_bt, BasicType src_bt, int vlen);

#ifdef _LP64
  void vector_round_double_evex(XMMRegister dst, XMMRegister src, AddressLiteral double_sign_flip, AddressLiteral new_mxcsr, int vec_enc,
                                Register tmp, XMMRegister xtmp1, XMMRegister xtmp2, KRegister ktmp1, KRegister ktmp2);

  void vector_round_float_evex(XMMRegister dst, XMMRegister src, AddressLiteral double_sign_flip, AddressLiteral new_mxcsr, int vec_enc,
                               Register tmp, XMMRegister xtmp1, XMMRegister xtmp2, KRegister ktmp1, KRegister ktmp2);

  void vector_round_float_avx(XMMRegister dst, XMMRegister src, AddressLiteral float_sign_flip, AddressLiteral new_mxcsr, int vec_enc,
                              Register tmp, XMMRegister xtmp1, XMMRegister xtmp2, XMMRegister xtmp3, XMMRegister xtmp4);

  void vector_compress_expand_avx2(int opcode, XMMRegister dst, XMMRegister src, XMMRegister mask,
                                   Register rtmp, Register rscratch, XMMRegister permv, XMMRegister xtmp,
                                   BasicType bt, int vec_enc);
#endif // _LP64

  void udivI(Register rax, Register divisor, Register rdx);
  void umodI(Register rax, Register divisor, Register rdx);
  void udivmodI(Register rax, Register divisor, Register rdx, Register tmp);

#ifdef _LP64
  void reverseI(Register dst, Register src, XMMRegister xtmp1,
                XMMRegister xtmp2, Register rtmp);
  void reverseL(Register dst, Register src, XMMRegister xtmp1,
                XMMRegister xtmp2, Register rtmp1, Register rtmp2);
  void udivL(Register rax, Register divisor, Register rdx);
  void umodL(Register rax, Register divisor, Register rdx);
  void udivmodL(Register rax, Register divisor, Register rdx, Register tmp);
#endif

  void evpternlog(XMMRegister dst, int func, KRegister mask, XMMRegister src2, XMMRegister src3,
                  bool merge, BasicType bt, int vlen_enc);

  void evpternlog(XMMRegister dst, int func, KRegister mask, XMMRegister src2, Address src3,
                  bool merge, BasicType bt, int vlen_enc);

  void vector_reverse_bit(BasicType bt, XMMRegister dst, XMMRegister src, XMMRegister xtmp1,
                          XMMRegister xtmp2, Register rtmp, int vec_enc);

  void vector_reverse_bit_gfni(BasicType bt, XMMRegister dst, XMMRegister src, AddressLiteral mask, int vec_enc,
                               XMMRegister xtmp, Register rscratch = noreg);

  void vector_reverse_byte(BasicType bt, XMMRegister dst, XMMRegister src, int vec_enc);

  void vector_popcount_int(XMMRegister dst, XMMRegister src, XMMRegister xtmp1,
                           XMMRegister xtmp2, Register rtmp, int vec_enc);

  void vector_popcount_long(XMMRegister dst, XMMRegister src, XMMRegister xtmp1,
                            XMMRegister xtmp2, Register rtmp, int vec_enc);

  void vector_popcount_short(XMMRegister dst, XMMRegister src, XMMRegister xtmp1,
                             XMMRegister xtmp2, Register rtmp, int vec_enc);

  void vector_popcount_byte(XMMRegister dst, XMMRegister src, XMMRegister xtmp1,
                            XMMRegister xtmp2, Register rtmp, int vec_enc);

  void vector_popcount_integral(BasicType bt, XMMRegister dst, XMMRegister src, XMMRegister xtmp1,
                                XMMRegister xtmp2, Register rtmp, int vec_enc);

  void vector_popcount_integral_evex(BasicType bt, XMMRegister dst, XMMRegister src,
                                     KRegister mask, bool merge, int vec_enc);

  void vbroadcast(BasicType bt, XMMRegister dst, int imm32, Register rtmp, int vec_enc);

  void vector_reverse_byte64(BasicType bt, XMMRegister dst, XMMRegister src, XMMRegister xtmp1,
                             XMMRegister xtmp2, Register rtmp, int vec_enc);

  void vector_count_leading_zeros_evex(BasicType bt, XMMRegister dst, XMMRegister src,
                                       XMMRegister xtmp1, XMMRegister xtmp2, XMMRegister xtmp3,
                                       KRegister ktmp, Register rtmp, bool merge, int vec_enc);

  void vector_count_leading_zeros_byte_avx(XMMRegister dst, XMMRegister src, XMMRegister xtmp1,
                                           XMMRegister xtmp2, XMMRegister xtmp3, Register rtmp, int vec_enc);

  void vector_count_leading_zeros_short_avx(XMMRegister dst, XMMRegister src, XMMRegister xtmp1,
                                            XMMRegister xtmp2, XMMRegister xtmp3, Register rtmp, int vec_enc);

  void vector_count_leading_zeros_int_avx(XMMRegister dst, XMMRegister src, XMMRegister xtmp1,
                                          XMMRegister xtmp2, XMMRegister xtmp3, int vec_enc);

  void vector_count_leading_zeros_long_avx(XMMRegister dst, XMMRegister src, XMMRegister xtmp1,
                                           XMMRegister xtmp2, XMMRegister xtmp3, Register rtmp, int vec_enc);

  void vector_count_leading_zeros_avx(BasicType bt, XMMRegister dst, XMMRegister src, XMMRegister xtmp1,
                                      XMMRegister xtmp2, XMMRegister xtmp3, Register rtmp, int vec_enc);

  void vpadd(BasicType bt, XMMRegister dst, XMMRegister src1, XMMRegister src2, int vec_enc);

  void vpsub(BasicType bt, XMMRegister dst, XMMRegister src1, XMMRegister src2, int vec_enc);

  void vector_count_trailing_zeros_evex(BasicType bt, XMMRegister dst, XMMRegister src, XMMRegister xtmp1,
                                        XMMRegister xtmp2, XMMRegister xtmp3, XMMRegister xtmp4, KRegister ktmp,
                                        Register rtmp, int vec_enc);

  void vector_swap_nbits(int nbits, int bitmask, XMMRegister dst, XMMRegister src,
                         XMMRegister xtmp1, Register rtmp, int vec_enc);

  void vector_count_trailing_zeros_avx(BasicType bt, XMMRegister dst, XMMRegister src, XMMRegister xtmp1,
                                       XMMRegister xtmp2, XMMRegister xtmp3, Register rtmp, int vec_enc);

  void vector_signum_avx(int opcode, XMMRegister dst, XMMRegister src, XMMRegister zero, XMMRegister one,
                         XMMRegister xtmp1, int vec_enc);

  void vector_signum_evex(int opcode, XMMRegister dst, XMMRegister src, XMMRegister zero, XMMRegister one,
                          KRegister ktmp1, int vec_enc);

  void vmovmask(BasicType elem_bt, XMMRegister dst, Address src, XMMRegister mask, int vec_enc);

  void vmovmask(BasicType elem_bt, Address dst, XMMRegister src, XMMRegister mask, int vec_enc);

  void rearrange_bytes(XMMRegister dst, XMMRegister shuffle, XMMRegister src, XMMRegister xtmp1,
                       XMMRegister xtmp2, XMMRegister xtmp3, Register rtmp, KRegister ktmp, int vlen_enc);

  void vector_rearrange_int_float(BasicType bt, XMMRegister dst, XMMRegister shuffle,
                                  XMMRegister src, int vlen_enc);


  void vgather_subword(BasicType elem_ty, XMMRegister dst,  Register base, Register idx_base, Register offset,
                       Register mask, XMMRegister xtmp1, XMMRegister xtmp2, XMMRegister xtmp3, Register rtmp,
                       Register midx, Register length, int vector_len, int vlen_enc);

#ifdef _LP64
  void vgather8b_masked_offset(BasicType elem_bt, XMMRegister dst, Register base, Register idx_base,
                               Register offset, Register mask, Register midx, Register rtmp, int vlen_enc);
#endif
  void vgather8b_offset(BasicType elem_bt, XMMRegister dst, Register base, Register idx_base,
                              Register offset, Register rtmp, int vlen_enc);

#endif // CPU_X86_C2_MACROASSEMBLER_X86_HPP
