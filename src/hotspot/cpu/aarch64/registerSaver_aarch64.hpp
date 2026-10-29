/*
 * Copyright (c) 2003, 2026, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2014, 2021, Red Hat Inc. All rights reserved.
 * Copyright (c) 2021, Azul Systems, Inc. All rights reserved.
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

#ifndef CPU_AARCH64_REGISTER_SAVER_AARCH64_HPP
#define CPU_AARCH64_REGISTER_SAVER_AARCH64_HPP

#include "asm/macroAssembler.hpp"
#include "compiler/oopMap.hpp"

class RegisterSaver {
  const bool _save_vectors;
 public:
  RegisterSaver(bool save_vectors) : _save_vectors(save_vectors) {}

  OopMap* save_live_registers(MacroAssembler* masm, int additional_frame_words, int* total_frame_words);
  void restore_live_registers(MacroAssembler* masm);

  // Offsets into the register save area
  // Used by deoptimization when it is managing result register
  // values on its own

  int reg_offset_in_bytes(Register r);
  int r0_offset_in_bytes()    { return reg_offset_in_bytes(r0); }
  int rscratch1_offset_in_bytes()    { return reg_offset_in_bytes(rscratch1); }
  int v0_offset_in_bytes();

  // Total stack size in bytes for saving SVE predicate registers.
  int total_sve_predicate_in_bytes();

  // Capture info about frame layout
  // Note this is only correct when not saving full vectors.
  enum layout {
                fpu_state_off = 0,
                fpu_state_end = fpu_state_off + FPUStateSizeInWords - 1,
                // The frame sender code expects that rfp will be in
                // the "natural" place and will override any oopMap
                // setting for it. We must therefore force the layout
                // so that it agrees with the frame sender code.
                r0_off = fpu_state_off + FPUStateSizeInWords,
                rfp_off = r0_off + (Register::number_of_registers - 2) * Register::max_slots_per_register,
                return_off = rfp_off + Register::max_slots_per_register,      // slot for return address
                reg_save_size = return_off + Register::max_slots_per_register};

};

#endif // CPU_AARCH64_REGISTER_SAVER_AARCH64_HPP
