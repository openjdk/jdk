/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
 * Copyright 2011 Red Hat, Inc.
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


// Adapters
enum /* platform_dependent_constants */ {
  adapter_code_size = 0
};

#define TARGET_ARCH_NYI_6939861 1
// ..#ifdef TARGET_ARCH_NYI_6939861
// ..  // Here are some backward compatible declarations until the 6939861 ports are updated.
// ..  #define _adapter_flyby    (_EK_LIMIT + 10)
// ..  #define _adapter_ricochet (_EK_LIMIT + 11)
// ..  #define _adapter_opt_spread_1    _adapter_opt_spread_1_ref
// ..  #define _adapter_opt_spread_more _adapter_opt_spread_ref
// ..  enum {
// ..    _INSERT_NO_MASK   = -1,
// ..    _INSERT_REF_MASK  = 0,
// ..    _INSERT_INT_MASK  = 1,
// ..    _INSERT_LONG_MASK = 3
// ..  };
// ..  static void get_ek_bound_mh_info(EntryKind ek, BasicType& arg_type, int& arg_mask, int& arg_slots) {
// ..    arg_type = ek_bound_mh_arg_type(ek);
// ..    arg_mask = 0;
// ..    arg_slots = type2size[arg_type];;
// ..  }
// ..  static void get_ek_adapter_opt_swap_rot_info(EntryKind ek, int& swap_bytes, int& rotate) {
// ..    int swap_slots = ek_adapter_opt_swap_slots(ek);
// ..    rotate = ek_adapter_opt_swap_mode(ek);
// ..    swap_bytes = swap_slots * Interpreter::stackElementSize;
// ..  }
// ..  static int get_ek_adapter_opt_spread_info(EntryKind ek) {
// ..    return ek_adapter_opt_spread_count(ek);
// ..  }
// ..
// ..  static void insert_arg_slots(MacroAssembler* _masm,
// ..                               RegisterOrConstant arg_slots,
// ..                               int arg_mask,
// ..                               Register argslot_reg,
// ..                               Register temp_reg, Register temp2_reg, Register temp3_reg = noreg);
// ..
// ..  static void remove_arg_slots(MacroAssembler* _masm,
// ..                               RegisterOrConstant arg_slots,
// ..                               Register argslot_reg,
// ..                               Register temp_reg, Register temp2_reg, Register temp3_reg = noreg);
// ..
// ..  static void trace_method_handle(MacroAssembler* _masm, const char* adaptername) PRODUCT_RETURN;
// ..#endif //TARGET_ARCH_NYI_6939861
