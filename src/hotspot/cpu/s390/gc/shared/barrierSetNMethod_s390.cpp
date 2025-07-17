/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "asm/assembler.inline.hpp"
#include "code/codeBlob.hpp"
#include "code/nativeInst.hpp"
#include "code/nmethod.hpp"
#include "gc/shared/barrierSetNMethod.hpp"
#include "utilities/debug.hpp"

class NativeMethodBarrier: public NativeInstruction {
  private:
    static const int PATCHABLE_INSTRUCTION_OFFSET = 3*6; // bytes

    address get_barrier_start_address() const {
      return NativeInstruction::addr_at(0);
    }

    address get_patchable_data_address() const {
      address inst_addr = get_barrier_start_address() + PATCHABLE_INSTRUCTION_OFFSET;

      DEBUG_ONLY(Assembler::is_z_cfi(*((long*)inst_addr)));
      return inst_addr + 2;
    }

  public:
    static const int BARRIER_TOTAL_LENGTH = PATCHABLE_INSTRUCTION_OFFSET + 2*6 + 2; // bytes

    int get_guard_value() const {
      address data_addr = get_patchable_data_address();
      // Return guard instruction value
      return *((int32_t*)data_addr);
    }

    void set_guard_value(int value) {
      int32_t* data_addr = (int32_t*)get_patchable_data_address();

      // Set guard instruction value
      *data_addr = value;
    }

    #ifdef ASSERT
      void verify() const {
        int offset = 0; // bytes
        const address start = get_barrier_start_address();

        MacroAssembler::is_load_const(/* address */ start + offset); // two instructions
        offset += Assembler::instr_len(&start[offset]);
        offset += Assembler::instr_len(&start[offset]);

        Assembler::is_z_lg(*((long*)(start + offset)));
        offset += Assembler::instr_len(&start[offset]);

        Assembler::is_z_cfi(*((long*)(start + offset)));
        offset += Assembler::instr_len(&start[offset]);

        Assembler::is_z_larl(*((long*)(start + offset)));
        offset += Assembler::instr_len(&start[offset]);

        Assembler::is_z_bcr(*((long*)(start + offset)));
        offset += Assembler::instr_len(&start[offset]);

        assert(offset == BARRIER_TOTAL_LENGTH, "check offset == barrier length constant");
      }
    #endif

};

static NativeMethodBarrier* get_nmethod_barrier(nmethod* nm) {
  address barrier_address = nm->code_begin() + nm->frame_complete_offset() - NativeMethodBarrier::BARRIER_TOTAL_LENGTH;
  auto barrier = reinterpret_cast<NativeMethodBarrier*>(barrier_address);

  DEBUG_ONLY(barrier->verify());
  return barrier;
}

void BarrierSetNMethod::deoptimize(nmethod* nm, address* return_address_ptr) {
  // Not required on s390 as a valid backchain is present
  return;
}

void BarrierSetNMethod::set_guard_value(nmethod* nm, int value) {
  if (!supports_entry_barrier(nm)) {
    return;
  }

  NativeMethodBarrier* barrier = get_nmethod_barrier(nm);
  barrier->set_guard_value(value);
}

int BarrierSetNMethod::guard_value(nmethod* nm) {
  if (!supports_entry_barrier(nm)) {
    return disarmed_guard_value();
  }

  NativeMethodBarrier* barrier = get_nmethod_barrier(nm);
  return barrier->get_guard_value();
}
