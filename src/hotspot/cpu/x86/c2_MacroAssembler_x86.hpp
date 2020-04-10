/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
  // special instructions for EVEX
  void setvectmask(Register dst, Register src);
  void restorevectmask();

  // Code used by cmpFastLock and cmpFastUnlock mach instructions in .ad file.
  // See full desription in macroAssembler_x86.cpp.
  void fast_lock(Register obj, Register box, Register tmp,
                 Register scr, Register cx1, Register cx2,
                 BiasedLockingCounters* counters,
                 RTMLockingCounters* rtm_counters,
                 RTMLockingCounters* stack_rtm_counters,
                 Metadata* method_data,
                 bool use_rtm, bool profile_rtm);
  void fast_unlock(Register obj, Register box, Register tmp, bool use_rtm);

#if INCLUDE_RTM_OPT
  void rtm_counters_update(Register abort_status, Register rtm_counters);
  void branch_on_random_using_rdtsc(Register tmp, Register scr, int count, Label& brLabel);
  void rtm_abort_ratio_calculation(Register tmp, Register rtm_counters_reg,
                                   RTMLockingCounters* rtm_counters,
                                   Metadata* method_data);
  void rtm_profiling(Register abort_status_Reg, Register rtm_counters_Reg,
                     RTMLockingCounters* rtm_counters, Metadata* method_data, bool profile_rtm);
  void rtm_retry_lock_on_abort(Register retry_count, Register abort_status, Label& retryLabel);
  void rtm_retry_lock_on_busy(Register retry_count, Register box, Register tmp, Register scr, Label& retryLabel);
  void rtm_stack_locking(Register obj, Register tmp, Register scr,
                         Register retry_on_abort_count,
                         RTMLockingCounters* stack_rtm_counters,
                         Metadata* method_data, bool profile_rtm,
                         Label& DONE_LABEL, Label& IsInflated);
  void rtm_inflated_locking(Register obj, Register box, Register tmp,
                            Register scr, Register retry_on_busy_count,
                            Register retry_on_abort_count,
                            RTMLockingCounters* rtm_counters,
                            Metadata* method_data, bool profile_rtm,
                            Label& DONE_LABEL);
#endif

  // Generic instructions support for use in .ad files C2 code generation
  void vabsnegd(int opcode, XMMRegister dst, XMMRegister src, Register scr);
  void vabsnegd(int opcode, XMMRegister dst, XMMRegister src, int vector_len, Register scr);
  void vabsnegf(int opcode, XMMRegister dst, XMMRegister src, Register scr);
  void vabsnegf(int opcode, XMMRegister dst, XMMRegister src, int vector_len, Register scr);
  void vextendbw(bool sign, XMMRegister dst, XMMRegister src, int vector_len);
  void vextendbw(bool sign, XMMRegister dst, XMMRegister src);
  void vshiftd(int opcode, XMMRegister dst, XMMRegister src);
  void vshiftd(int opcode, XMMRegister dst, XMMRegister nds, XMMRegister src, int vector_len);
  void vshiftw(int opcode, XMMRegister dst, XMMRegister src);
  void vshiftw(int opcode, XMMRegister dst, XMMRegister nds, XMMRegister src, int vector_len);
  void vshiftq(int opcode, XMMRegister dst, XMMRegister src);
  void vshiftq(int opcode, XMMRegister dst, XMMRegister nds, XMMRegister src, int vector_len);

  // Reductions for vectors of ints, longs, floats, and doubles.

  // dst = src1 + reduce(op, src2) using vtmp as temps
  void reduceI(int opcode, int vlen, Register dst, Register src1, XMMRegister src2, XMMRegister vtmp1, XMMRegister vtmp2);
#ifdef _LP64
  void reduceL(int opcode, int vlen, Register dst, Register src1, XMMRegister src2, XMMRegister vtmp1, XMMRegister vtmp2);
#endif // _LP64

  // dst = reduce(op, src2) using vtmp as temps
  void reduce_fp(int opcode, int vlen,
                 XMMRegister dst, XMMRegister src,
                 XMMRegister vtmp1, XMMRegister vtmp2 = xnoreg);
 private:
  void reduceF(int opcode, int vlen, XMMRegister dst, XMMRegister src, XMMRegister vtmp1, XMMRegister vtmp2);
  void reduceD(int opcode, int vlen, XMMRegister dst, XMMRegister src, XMMRegister vtmp1, XMMRegister vtmp2);

  void reduce2I (int opcode, Register dst, Register src1, XMMRegister src2, XMMRegister vtmp1, XMMRegister vtmp2);
  void reduce4I (int opcode, Register dst, Register src1, XMMRegister src2, XMMRegister vtmp1, XMMRegister vtmp2);
  void reduce8I (int opcode, Register dst, Register src1, XMMRegister src2, XMMRegister vtmp1, XMMRegister vtmp2);
  void reduce16I(int opcode, Register dst, Register src1, XMMRegister src2, XMMRegister vtmp1, XMMRegister vtmp2);

#ifdef _LP64
  void reduce2L(int opcode, Register dst, Register src1, XMMRegister src2, XMMRegister vtmp1, XMMRegister vtmp2);
  void reduce4L(int opcode, Register dst, Register src1, XMMRegister src2, XMMRegister vtmp1, XMMRegister vtmp2);
  void reduce8L(int opcode, Register dst, Register src1, XMMRegister src2, XMMRegister vtmp1, XMMRegister vtmp2);
#endif // _LP64

  void reduce2F (int opcode, XMMRegister dst, XMMRegister src, XMMRegister vtmp);
  void reduce4F (int opcode, XMMRegister dst, XMMRegister src, XMMRegister vtmp);
  void reduce8F (int opcode, XMMRegister dst, XMMRegister src, XMMRegister vtmp1, XMMRegister vtmp2);
  void reduce16F(int opcode, XMMRegister dst, XMMRegister src, XMMRegister vtmp1, XMMRegister vtmp2);

  void reduce2D(int opcode, XMMRegister dst, XMMRegister src, XMMRegister vtmp);
  void reduce4D(int opcode, XMMRegister dst, XMMRegister src, XMMRegister vtmp1, XMMRegister vtmp2);
  void reduce8D(int opcode, XMMRegister dst, XMMRegister src, XMMRegister vtmp1, XMMRegister vtmp2);

  void reduce_operation_128(int opcode, XMMRegister dst, XMMRegister src);
  void reduce_operation_256(int opcode, XMMRegister dst, XMMRegister src1, XMMRegister src2);

 public:

  void string_indexof_char(Register str1, Register cnt1, Register ch, Register result,
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
                      XMMRegister vec1, int ae);

  // Search for Non-ASCII character (Negative byte value) in a byte array,
  // return true if it has any and false otherwise.
  void has_negatives(Register ary1, Register len,
                     Register result, Register tmp1,
                     XMMRegister vec1, XMMRegister vec2);

  // Compare char[] or byte[] arrays.
  void arrays_equals(bool is_array_equ, Register ary1, Register ary2,
                     Register limit, Register result, Register chr,
                     XMMRegister vec1, XMMRegister vec2, bool is_char);

#endif // CPU_X86_C2_MACROASSEMBLER_X86_HPP
