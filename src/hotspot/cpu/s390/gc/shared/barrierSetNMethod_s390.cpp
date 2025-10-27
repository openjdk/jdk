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
#include "gc/shared/barrierSetAssembler.hpp"
#include "gc/shared/barrierSetNMethod.hpp"
#include "utilities/debug.hpp"

class NativeMethodBarrier: public NativeInstruction {
  private:

    address get_barrier_start_address() const {
      return NativeInstruction::addr_at(0);
    }

    address get_patchable_data_address() const {
      address start_address = get_barrier_start_address();
#ifdef ASSERT
      address inst_addr = start_address + BarrierSetAssembler::OFFSET_TO_PATCHABLE_DATA_INSTRUCTION;

      unsigned long instr = 0;
      Assembler::get_instruction(inst_addr, &instr);
      assert(Assembler::is_z_cfi(instr), "sanity check");
#endif // ASSERT

      return start_address + BarrierSetAssembler::OFFSET_TO_PATCHABLE_DATA;
    }

  public:
    static const int BARRIER_TOTAL_LENGTH = BarrierSetAssembler::BARRIER_TOTAL_LENGTH;

    int get_guard_value() const {
      address data_addr = get_patchable_data_address();
      // Return guard instruction value
      return *((int32_t*)data_addr);
    }

    void set_guard_value(int value, int bit_mask) {
      if (bit_mask == ~0) {
        int32_t* data_addr = (int32_t*)get_patchable_data_address();

        // Set guard instruction value
        *data_addr = value;
        return;
      }
      assert((value & ~bit_mask) == 0, "trying to set bits outside the mask");
      value &= bit_mask;
      int32_t* data_addr = (int32_t*)get_patchable_data_address();
      int old_value = AtomicAccess::load(data_addr);
      while (true) {
        // Only bits in the mask are changed
        int new_value = value | (old_value & ~bit_mask);
        if (new_value == old_value) break;
        int v = AtomicAccess::cmpxchg(data_addr, old_value, new_value, memory_order_release);
        if (v == old_value) break;
        old_value = v;
      }
    }

    #ifdef ASSERT
      void verify() const {
        unsigned long instr = 0;
        int offset = 0; // bytes
        const address start = get_barrier_start_address();

        assert(MacroAssembler::is_load_const(/* address */ start + offset), "sanity check"); // two instructions
        offset += Assembler::instr_len(&start[offset]);
        offset += Assembler::instr_len(&start[offset]);

        Assembler::get_instruction(start + offset, &instr);
        assert(Assembler::is_z_lg(instr), "sanity check");
        offset += Assembler::instr_len(&start[offset]);

        // it will be assignment operation, So it doesn't matter what value is already present in instr
        // hence, no need to 0 it out.
        Assembler::get_instruction(start + offset, &instr);
        assert(Assembler::is_z_cfi(instr), "sanity check");
        offset += Assembler::instr_len(&start[offset]);

        Assembler::get_instruction(start + offset, &instr);
        assert(Assembler::is_z_larl(instr), "sanity check");
        offset += Assembler::instr_len(&start[offset]);

        Assembler::get_instruction(start + offset, &instr);
        assert(Assembler::is_z_bcr(instr), "sanity check");
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

void BarrierSetNMethod::set_guard_value(nmethod* nm, int value, int bit_mask) {
  if (!supports_entry_barrier(nm)) {
    return;
  }

  NativeMethodBarrier* barrier = get_nmethod_barrier(nm);
  barrier->set_guard_value(value, bit_mask);
}

int BarrierSetNMethod::guard_value(nmethod* nm) {
  if (!supports_entry_barrier(nm)) {
    return disarmed_guard_value();
  }

  NativeMethodBarrier* barrier = get_nmethod_barrier(nm);
  return barrier->get_guard_value();
}
