/*
 * Copyright (c) 2003, 2026, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2014, 2020, Red Hat Inc. All rights reserved.
 * Copyright (c) 2020, 2023, Huawei Technologies Co., Ltd. All rights reserved.
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

#ifndef CPU_RISCV_REGISTER_SAVER_RISCV_HPP
#define CPU_RISCV_REGISTER_SAVER_RISCV_HPP

#include "asm/macroAssembler.hpp"
#include "compiler/oopMap.hpp"

class RegisterSaver {
  const bool _save_vectors;
 public:
  RegisterSaver(bool save_vectors) : _save_vectors(UseRVV && save_vectors) {}
  ~RegisterSaver() {}
  OopMap* save_live_registers(MacroAssembler* masm, int additional_frame_words, int* total_frame_words);
  void restore_live_registers(MacroAssembler* masm);

  // Offsets into the register save area
  // Used by deoptimization when it is managing result register
  // values on its own
  // gregs:28, float_register:32; except: x1(ra) & x2(sp) & gp(x3) & tp(x4)
  // |---v0---|<---SP
  // |---v1---|optional vector save area
  // |-- .. --|
  // |---v31--|-----
  // |---f0---|
  // |---f1---|
  // |   ..   |
  // |---f31--|
  // |---reserved slot for stack alignment---|
  // |---x5---|
  // |   x6   |
  // |---.. --|
  // |---x31--|
  // |---fp---|
  // |---ra---|
  int v0_offset_in_bytes(void) { return 0; }
  int f0_offset_in_bytes(void);
  int reserved_slot_offset_in_bytes(void) {
    return f0_offset_in_bytes() +
           FloatRegister::max_slots_per_register *
           FloatRegister::number_of_registers *
           BytesPerInt;
  }

  int reg_offset_in_bytes(Register r) {
    assert (r->encoding() > 4, "ra, sp, gp and tp not saved");
    return reserved_slot_offset_in_bytes() + (r->encoding() - 4 /* x1, x2, x3, x4 */) * wordSize;
  }

  int freg_offset_in_bytes(FloatRegister f) {
    return f0_offset_in_bytes() + f->encoding() * wordSize;
  }

  int ra_offset_in_bytes(void) {
    return reserved_slot_offset_in_bytes() +
           (Register::number_of_registers - 3) *
           Register::max_slots_per_register *
           BytesPerInt;
  }
};

#endif // CPU_RISCV_REGISTER_SAVER_RISCV_HPP
