/*
 * Copyright (c) 2005, 2017, Oracle and/or its affiliates. All rights reserved.
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

#ifndef CPU_SPARC_VM_C1_LINEARSCAN_SPARC_HPP
#define CPU_SPARC_VM_C1_LINEARSCAN_SPARC_HPP

inline bool LinearScan::is_processed_reg_num(int reg_num) {
  return reg_num < 26 || reg_num > 31;
}

inline int LinearScan::num_physical_regs(BasicType type) {
  // Sparc requires two cpu registers for long
  // and two cpu registers for double
  if (type == T_DOUBLE) {
    return 2;
  }
  return 1;
}


inline bool LinearScan::requires_adjacent_regs(BasicType type) {
  return type == T_DOUBLE;
}

inline bool LinearScan::is_caller_save(int assigned_reg) {
  return assigned_reg > pd_last_callee_saved_reg && assigned_reg <= pd_last_fpu_reg;
}


inline void LinearScan::pd_add_temps(LIR_Op* op) {
  // No special case behaviours yet
}


inline bool LinearScanWalker::pd_init_regs_for_alloc(Interval* cur) {
  if (allocator()->gen()->is_vreg_flag_set(cur->reg_num(), LIRGenerator::callee_saved)) {
    assert(cur->type() != T_FLOAT && cur->type() != T_DOUBLE, "cpu regs only");
    _first_reg = pd_first_callee_saved_reg;
    _last_reg = pd_last_callee_saved_reg;
    return true;
  } else if (cur->type() == T_INT || cur->type() == T_LONG || cur->type() == T_OBJECT || cur->type() == T_ADDRESS || cur->type() == T_METADATA) {
    _first_reg = pd_first_cpu_reg;
    _last_reg = pd_last_allocatable_cpu_reg;
    return true;
  }
  return false;
}

#endif // CPU_SPARC_VM_C1_LINEARSCAN_SPARC_HPP
