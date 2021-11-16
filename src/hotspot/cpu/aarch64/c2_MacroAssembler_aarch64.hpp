/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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

 public:

  void string_compare(Register str1, Register str2,
                      Register cnt1, Register cnt2, Register result,
                      Register tmp1, Register tmp2, FloatRegister vtmp1,
                      FloatRegister vtmp2, FloatRegister vtmp3, int ae);

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

  // SIMD&FP comparison
  void neon_compare(FloatRegister dst, BasicType bt, FloatRegister src1,
                    FloatRegister src2, int cond, bool isQ);

  void sve_compare(PRegister pd, BasicType bt, PRegister pg,
                   FloatRegister zn, FloatRegister zm, int cond);

  void sve_vmask_lasttrue(Register dst, BasicType bt, PRegister src, PRegister ptmp);

  void sve_vector_extend(FloatRegister dst, SIMD_RegVariant dst_size,
                         FloatRegister src, SIMD_RegVariant src_size);

  void sve_vector_narrow(FloatRegister dst, SIMD_RegVariant dst_size,
                         FloatRegister src, SIMD_RegVariant src_size, FloatRegister tmp);

  void sve_vmaskcast_extend(PRegister dst, PRegister src,
                            uint dst_element_length_in_bytes, uint src_element_lenght_in_bytes);

  void sve_vmaskcast_narrow(PRegister dst, PRegister src,
                            uint dst_element_length_in_bytes, uint src_element_lenght_in_bytes);

  void sve_reduce_integral(int opc, Register dst, BasicType bt, Register src1,
                           FloatRegister src2, PRegister pg, FloatRegister tmp);

  // Generate predicate through whilelo, by comparing ZR with an unsigned
  // immediate. rscratch1 will be clobbered.
  inline void sve_whilelo_zr_imm(PRegister pd, SIMD_RegVariant size, uint imm) {
    assert(UseSVE > 0, "not supported");
    mov(rscratch1, imm);
    sve_whilelo(pd, size, zr, rscratch1);
  }

  // Extract a scalar element from an sve vector at position 'idx'.
  // rscratch1 will be clobbered.
  // T could be FloatRegister or Register.
  template<class T>
  inline void sve_extract(T dst, SIMD_RegVariant size, PRegister pg, FloatRegister src, int idx) {
    assert(UseSVE > 0, "not supported");
    assert(pg->is_governing(), "This register has to be a governing predicate register");
    mov(rscratch1, idx);
    sve_whilele(pg, size, zr, rscratch1);
    sve_lastb(dst, size, pg, src);
  }

#endif // CPU_AARCH64_C2_MACROASSEMBLER_AARCH64_HPP
