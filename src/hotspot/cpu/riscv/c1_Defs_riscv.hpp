/*
 * Copyright (c) 2000, 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef CPU_RISCV_C1_DEFS_RISCV_HPP
#define CPU_RISCV_C1_DEFS_RISCV_HPP

// native word offsets from memory address (little endian)
enum {
  pd_lo_word_offset_in_bytes = 0,
  pd_hi_word_offset_in_bytes = BytesPerWord
};

// registers
enum {
  pd_nof_cpu_regs_frame_map = Register::number_of_registers,       // number of registers used during code emission
  pd_nof_fpu_regs_frame_map = FloatRegister::number_of_registers,  // number of float registers used during code emission

  // caller saved
  pd_nof_caller_save_cpu_regs_frame_map = 13, // number of registers killed by calls
  pd_nof_caller_save_fpu_regs_frame_map = 32, // number of float registers killed by calls

  pd_first_callee_saved_reg = pd_nof_caller_save_cpu_regs_frame_map,
  pd_last_callee_saved_reg = 21,

  pd_last_allocatable_cpu_reg = pd_nof_caller_save_cpu_regs_frame_map - 1,

  pd_nof_cpu_regs_reg_alloc
    = pd_nof_caller_save_cpu_regs_frame_map,  // number of registers that are visible to register allocator
  pd_nof_fpu_regs_reg_alloc = 32,  // number of float registers that are visible to register allocator

  pd_nof_cpu_regs_linearscan = 32, // number of registers visible to linear scan
  pd_nof_fpu_regs_linearscan = pd_nof_fpu_regs_frame_map, // number of float registers visible to linear scan
  pd_nof_xmm_regs_linearscan = 0, // don't have vector registers

  pd_first_cpu_reg  = 0,
  pd_last_cpu_reg   = pd_nof_cpu_regs_reg_alloc - 1,
  pd_first_byte_reg = 0,
  pd_last_byte_reg  = pd_nof_cpu_regs_reg_alloc - 1,

  pd_first_fpu_reg  = pd_nof_cpu_regs_frame_map,
  pd_last_fpu_reg   = pd_first_fpu_reg + 31,

  pd_first_callee_saved_fpu_reg_1 = 8 + pd_first_fpu_reg,
  pd_last_callee_saved_fpu_reg_1  = 9 + pd_first_fpu_reg,
  pd_first_callee_saved_fpu_reg_2 = 18 + pd_first_fpu_reg,
  pd_last_callee_saved_fpu_reg_2  = 27 + pd_first_fpu_reg
};


// Encoding of float value in debug info.  This is true on x86 where
// floats are extended to doubles when stored in the stack, false for
// RISCV where floats and doubles are stored in their native form.
enum {
  pd_float_saved_as_double = false
};

enum {
  pd_two_operand_lir_form = false
};

// the number of stack required by ArrayCopyStub
enum {
  pd_arraycopystub_reserved_argument_area_size = 2
};

#endif // CPU_RISCV_C1_DEFS_RISCV_HPP
