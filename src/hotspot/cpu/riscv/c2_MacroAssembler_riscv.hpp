/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2022, Huawei Technologies Co., Ltd. All rights reserved.
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

#ifndef CPU_RISCV_C2_MACROASSEMBLER_RISCV_HPP
#define CPU_RISCV_C2_MACROASSEMBLER_RISCV_HPP

// C2_MacroAssembler contains high-level macros for C2

 private:
  // Return true if the phase output is in the scratch emit size mode.
  virtual bool in_scratch_emit_size() override;

  void element_compare(Register r1, Register r2,
                       Register result, Register cnt,
                       Register tmp1, Register tmp2,
                       VectorRegister vr1, VectorRegister vr2,
                       VectorRegister vrs,
                       bool is_latin, Label& DONE);

  void compress_bits_v(Register dst, Register src, Register mask, bool is_long);
  void expand_bits_v(Register dst, Register src, Register mask, bool is_long);

 public:
  // Code used by cmpFastLock and cmpFastUnlock mach instructions in .ad file.
  // See full description in macroAssembler_riscv.cpp.
  void fast_lock(Register object, Register box, Register tmp1, Register tmp2, Register tmp3);
  void fast_unlock(Register object, Register box, Register tmp1, Register tmp2);

  void string_compare(Register str1, Register str2,
                      Register cnt1, Register cnt2, Register result,
                      Register tmp1, Register tmp2, Register tmp3,
                      int ae);

  void string_indexof_char_short(Register str1, Register cnt1,
                                 Register ch, Register result,
                                 bool isL);

  void string_indexof_char(Register str1, Register cnt1,
                           Register ch, Register result,
                           Register tmp1, Register tmp2,
                           Register tmp3, Register tmp4,
                           bool isL);

  void string_indexof(Register str1, Register str2,
                      Register cnt1, Register cnt2,
                      Register tmp1, Register tmp2,
                      Register tmp3, Register tmp4,
                      Register tmp5, Register tmp6,
                      Register result, int ae);

  void string_indexof_linearscan(Register haystack, Register needle,
                                 Register haystack_len, Register needle_len,
                                 Register tmp1, Register tmp2,
                                 Register tmp3, Register tmp4,
                                 int needle_con_cnt, Register result, int ae);

  void arrays_equals(Register r1, Register r2,
                     Register tmp3, Register tmp4,
                     Register tmp5, Register tmp6,
                     Register result, Register cnt1,
                     int elem_size);

  void arrays_hashcode(Register ary, Register cnt, Register result,
                       Register tmp1, Register tmp2,
                       Register tmp3, Register tmp4,
                       Register tmp5, Register tmp6,
                       BasicType eltype);
  // helper function for arrays_hashcode
  int arrays_hashcode_elsize(BasicType eltype);
  void arrays_hashcode_elload(Register dst, Address src, BasicType eltype);

  void string_equals(Register r1, Register r2,
                     Register result, Register cnt1,
                     int elem_size);

  // refer to conditional_branches and float_conditional_branches
  static const int bool_test_bits = 3;
  static const int neg_cond_bits = 2;
  static const int unsigned_branch_mask = 1 << bool_test_bits;
  static const int double_branch_mask = 1 << bool_test_bits;

  // cmp
  void cmp_branch(int cmpFlag,
                  Register op1, Register op2,
                  Label& label, bool is_far = false);

  void float_cmp_branch(int cmpFlag,
                        FloatRegister op1, FloatRegister op2,
                        Label& label, bool is_far = false);

  void enc_cmpUEqNeLeGt_imm0_branch(int cmpFlag, Register op,
                                    Label& L, bool is_far = false);

  void enc_cmpEqNe_imm0_branch(int cmpFlag, Register op,
                               Label& L, bool is_far = false);

  void enc_cmove(int cmpFlag,
                 Register op1, Register op2,
                 Register dst, Register src);

  void spill(Register r, bool is64, int offset) {
    is64 ? sd(r, Address(sp, offset))
         : sw(r, Address(sp, offset));
  }

  void spill(FloatRegister f, bool is64, int offset) {
    is64 ? fsd(f, Address(sp, offset))
         : fsw(f, Address(sp, offset));
  }

  void spill(VectorRegister v, int offset) {
    add(t0, sp, offset);
    vs1r_v(v, t0);
  }

  void unspill(Register r, bool is64, int offset) {
    is64 ? ld(r, Address(sp, offset))
         : lw(r, Address(sp, offset));
  }

  void unspillu(Register r, bool is64, int offset) {
    is64 ? ld(r, Address(sp, offset))
         : lwu(r, Address(sp, offset));
  }

  void unspill(FloatRegister f, bool is64, int offset) {
    is64 ? fld(f, Address(sp, offset))
         : flw(f, Address(sp, offset));
  }

  void unspill(VectorRegister v, int offset) {
    add(t0, sp, offset);
    vl1r_v(v, t0);
  }

  void spill_copy_vector_stack_to_stack(int src_offset, int dst_offset, int vector_length_in_bytes) {
    assert(vector_length_in_bytes % 16 == 0, "unexpected vector reg size");
    for (int i = 0; i < vector_length_in_bytes / 8; i++) {
      unspill(t0, true, src_offset + (i * 8));
      spill(t0, true, dst_offset + (i * 8));
    }
  }

  void minmax_fp(FloatRegister dst,
                 FloatRegister src1, FloatRegister src2,
                 bool is_double, bool is_min);

  void round_double_mode(FloatRegister dst, FloatRegister src, int round_mode,
                         Register tmp1, Register tmp2, Register tmp3);

  void signum_fp(FloatRegister dst, FloatRegister one, bool is_double);

  void float16_to_float(FloatRegister dst, Register src, Register tmp);
  void float_to_float16(Register dst, FloatRegister src, FloatRegister ftmp, Register xtmp);

  void signum_fp_v(VectorRegister dst, VectorRegister one, BasicType bt, int vlen);


  // intrinsic methods implemented by rvv instructions

  // compress bits, i.e. j.l.Integer/Long::compress.
  void compress_bits_i_v(Register dst, Register src, Register mask);
  void compress_bits_l_v(Register dst, Register src, Register mask);
  // expand bits, i.e. j.l.Integer/Long::expand.
  void expand_bits_i_v(Register dst, Register src, Register mask);
  void expand_bits_l_v(Register dst, Register src, Register mask);

  void string_equals_v(Register r1, Register r2,
                       Register result, Register cnt1,
                       int elem_size);

  void arrays_equals_v(Register r1, Register r2,
                       Register result, Register cnt1,
                       int elem_size);

  void string_compare_v(Register str1, Register str2,
                        Register cnt1, Register cnt2,
                        Register result,
                        Register tmp1, Register tmp2,
                        int encForm);

  void clear_array_v(Register base, Register cnt);

  void byte_array_inflate_v(Register src, Register dst,
                            Register len, Register tmp);

  void char_array_compress_v(Register src, Register dst,
                            Register len, Register result,
                            Register tmp);

  void encode_iso_array_v(Register src, Register dst,
                          Register len, Register result,
                          Register tmp, bool ascii);

  void count_positives_v(Register ary, Register len,
                        Register result, Register tmp);

  void string_indexof_char_v(Register str1, Register cnt1,
                            Register ch, Register result,
                            Register tmp1, Register tmp2,
                            bool isL);

  void minmax_fp_v(VectorRegister dst,
                  VectorRegister src1, VectorRegister src2,
                  BasicType bt, bool is_min, int vector_length);

  void minmax_fp_masked_v(VectorRegister dst, VectorRegister src1, VectorRegister src2,
                          VectorRegister vmask, VectorRegister tmp1, VectorRegister tmp2,
                          BasicType bt, bool is_min, int vector_length);

  void reduce_minmax_fp_v(FloatRegister dst,
                          FloatRegister src1, VectorRegister src2,
                          VectorRegister tmp1, VectorRegister tmp2,
                          bool is_double, bool is_min, int vector_length,
                          VectorMask vm = Assembler::unmasked);

  void reduce_integral_v(Register dst, Register src1,
                        VectorRegister src2, VectorRegister tmp,
                        int opc, BasicType bt, int vector_length,
                        VectorMask vm = Assembler::unmasked);

  void vsetvli_helper(BasicType bt, int vector_length, LMUL vlmul = Assembler::m1, Register tmp = t0);

  void compare_integral_v(VectorRegister dst, VectorRegister src1, VectorRegister src2, int cond,
                          BasicType bt, int vector_length, VectorMask vm = Assembler::unmasked);

  void compare_fp_v(VectorRegister dst, VectorRegister src1, VectorRegister src2, int cond,
                    BasicType bt, int vector_length, VectorMask vm = Assembler::unmasked);

  // In Matcher::scalable_predicate_reg_slots,
  // we assume each predicate register is one-eighth of the size of
  // scalable vector register, one mask bit per vector byte.
  void spill_vmask(VectorRegister v, int offset){
    vsetvli_helper(T_BYTE, MaxVectorSize >> 3);
    add(t0, sp, offset);
    vse8_v(v, t0);
  }

  void unspill_vmask(VectorRegister v, int offset){
    vsetvli_helper(T_BYTE, MaxVectorSize >> 3);
    add(t0, sp, offset);
    vle8_v(v, t0);
  }

  void spill_copy_vmask_stack_to_stack(int src_offset, int dst_offset, int vector_length_in_bytes) {
    assert(vector_length_in_bytes % 4 == 0, "unexpected vector mask reg size");
    for (int i = 0; i < vector_length_in_bytes / 4; i++) {
      unspill(t0, false, src_offset + (i * 4));
      spill(t0, false, dst_offset + (i * 4));
    }
  }

  void integer_extend_v(VectorRegister dst, BasicType dst_bt, int vector_length,
                        VectorRegister src, BasicType src_bt);

  void integer_narrow_v(VectorRegister dst, BasicType dst_bt, int vector_length,
                        VectorRegister src, BasicType src_bt);

  void vfcvt_rtz_x_f_v_safe(VectorRegister dst, VectorRegister src);

  void extract_v(Register dst, VectorRegister src, BasicType bt, int idx, VectorRegister tmp);
  void extract_fp_v(FloatRegister dst, VectorRegister src, BasicType bt, int idx, VectorRegister tmp);

#endif // CPU_RISCV_C2_MACROASSEMBLER_RISCV_HPP
