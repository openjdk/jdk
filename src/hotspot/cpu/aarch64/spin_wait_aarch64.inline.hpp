/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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
 */

#ifndef CPU_AARCH64_SPIN_WAIT_AARCH64_INLINE_HPP
#define CPU_AARCH64_SPIN_WAIT_AARCH64_INLINE_HPP

#include "asm/macroAssembler.hpp"
#include "runtime/vm_version.hpp"
#include "spin_wait_aarch64.hpp"
#include "utilities/powerOfTwo.hpp"

inline void exec_spin_wait_inst(SpinWait::Inst inst_id) {
  assert(SpinWait::NONE  == 0, "SpinWait::Inst value 0 reserved to indicate no implementation");
  assert(SpinWait::YIELD == 1, "SpinWait::Inst value 1 reserved for 'yield' instruction");
  assert(SpinWait::ISB   == 2, "SpinWait::Inst value 2 reserved for 'isb' instruction");
  assert(SpinWait::SB    == 4, "SpinWait::Inst value 4 reserved for 'sb' instruction");
  assert(SpinWait::NOP   == 8, "SpinWait::Inst value 8 reserved for 'nop' instruction");
  assert(inst_id == 0 || is_power_of_2((uint64_t)inst_id), "Values of SpinWait::Inst must be 0 or use only one bit");
  assert(inst_id <= SpinWait::NOP, "Unsupported type of SpinWait::Inst: %d", inst_id);
  assert(inst_id != SpinWait::SB || VM_Version::supports_sb(), "current CPU does not support SB instruction");

  if (inst_id < SpinWait::NONE || inst_id > SpinWait::NOP) {
    ShouldNotReachHere();
  }

  // The assembly code below is equivalent to the following:
  //
  // if (inst_id == 1) {
  //   exec_yield_inst();
  // } else if (inst_id == 2) {
  //   exec_isb_inst();
  // } else if (inst_id == 4) {
  //   exec_sb_inst();
  // } else if (inst_id == 8) {
  //   exec_nop_inst();
  // }
  asm volatile(
      "  tbz %[id], 0, 0f      \n" // The default instruction for SpinWait is YIELD.
                                   // We check it first before going to switch.
      "  yield                 \n"
      "  b    4f               \n"
      "0:                      \n"
      "  tbnz %[id], 1, 1f     \n"
      "  tbnz %[id], 2, 2f     \n"
      "  tbnz %[id], 3, 3f     \n"
      "  b    4f               \n"
      "1:                      \n"
      "  isb                   \n"
      "  b    4f               \n"
      "2:                      \n"
      "  .inst 0xd50330ff      \n" // SB instruction, explicitly encoded not to rely on a compiler
      "  b    4f               \n"
      "3:                      \n"
      "  nop                   \n"
      "4:                      \n"
      :
      : [id]"r"((uint64_t)inst_id)
      : "memory");
}

inline void generate_spin_wait(MacroAssembler *masm, const SpinWait &spin_wait_desc) {
  for (int i = 0; i < spin_wait_desc.inst_count(); ++i) {
    switch (spin_wait_desc.inst()) {
      case SpinWait::NOP:
        masm->nop();
        break;
      case SpinWait::ISB:
        masm->isb();
        break;
      case SpinWait::YIELD:
        masm->yield();
        break;
      case SpinWait::SB:
        assert(VM_Version::supports_sb(), "current CPU does not support SB instruction");
        masm->sb();
        break;
      default:
        ShouldNotReachHere();
    }
  }
}

#endif // CPU_AARCH64_SPIN_WAIT_AARCH64_INLINE_HPP
