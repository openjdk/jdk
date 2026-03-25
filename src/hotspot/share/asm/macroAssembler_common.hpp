/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_ASM_MACROASSEMBLER_COMMON_HPP
#define SHARE_ASM_MACROASSEMBLER_COMMON_HPP

// These are part of the MacroAssembler class that are common for all CPUs

// class MacroAssembler ... {

  enum RegState {
    reg_readonly,
    reg_writable,
    reg_written
  };

  void skip_unpacked_fields(const GrowableArray<SigEntry>* sig, int& sig_index, VMRegPair* regs_from,
                            int regs_from_count, int& from_index);
  bool is_reg_in_unpacked_fields(const GrowableArray<SigEntry>* sig, int sig_index, VMReg to, VMRegPair* regs_from,
                                 int regs_from_count, int from_index);
  void mark_reg_writable(const VMRegPair* regs, int num_regs, int reg_index, RegState* reg_state);
  RegState* init_reg_state(VMRegPair* regs, int num_regs, int sp_inc, int max_stack);
  int unpack_inline_args(Compile* C, bool receiver_only);
  void shuffle_inline_args(bool is_packing, bool receiver_only,
                           const GrowableArray<SigEntry>* sig,
                           int args_passed, int args_on_stack, VMRegPair* regs,
                           int args_passed_to, int args_on_stack_to, VMRegPair* regs_to,
                           int sp_inc, Register val_array);
  bool shuffle_inline_args_spill(bool is_packing, const GrowableArray<SigEntry>* sig, int sig_index,
                                 VMRegPair* regs_from, int from_index, int regs_from_count, RegState* reg_state);
// };

#endif // SHARE_ASM_MACROASSEMBLER_COMMON_HPP
