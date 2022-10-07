/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "asm/assembler.inline.hpp"
#include "code/codeBlob.hpp"
#include "code/nativeInst.hpp"
#include "code/nmethod.hpp"
#include "gc/shared/barrierSetNMethod.hpp"
#include "utilities/debug.hpp"

class NativeMethodBarrier: public NativeInstruction {
  private:
    static const int GUARD_INSTRUCTION_OFFSET = 3*6; // bytes

    address get_barrier_start_address() const {
      return NativeInstruction::addr_at(0);
    }

    NativeMovRegMem* get_patchable_instruction_handle() const {
      address guard_addr = get_barrier_start_address() + GUARD_INSTRUCTION_OFFSET;

      return reinterpret_cast<NativeMovRegMem*>(guard_addr);
    }

  public:
    static const int BARRIER_TOTAL_LENGTH = GUARD_INSTRUCTION_OFFSET + 2*6 + 2; // bytes

    int get_guard_value() const {
      NativeMovRegMem* guard_addr = get_patchable_instruction_handle();

      // Return guard instruction and value
      return guard_addr->offset();
    }

    void set_guard_value(int value) {
      NativeMovRegMem* guard_addr = get_patchable_instruction_handle();

      // Set guard instruction and value
      guard_addr->set_offset(value);
    }

    void verify() const {
      const address start = get_barrier_start_address();

      assert(Assembler::is_equal(start[0],  IIHF_ZOPC, RIL_MASK), "check load_const (1/2)");
      assert(Assembler::is_equal(start[6],  IILF_ZOPC, RIL_MASK), "check load_const (2/2)");
      assert(Assembler::is_equal(start[12], LG_ZOPC, RIL_MASK), "check LG");
      assert(Assembler::is_equal(start[18], CFI_ZOPC, RIL_MASK), "check CFI");
      get_patchable_instruction_handle()->verify();
      assert(Assembler::is_equal(start[22], BCR_ZOPC, RIL_MASK), "check BCR");
    }

};

static NativeMethodBarrier* get_nmethod_barrier(nmethod* nm) {
  address barrier_address = nm->code_begin() + nm->frame_complete_offset() - NativeMethodBarrier::BARRIER_TOTAL_LENGTH;
  auto barrier = reinterpret_cast<NativeMethodBarrier*>(barrier_address);

  debug_only(barrier->verify());
  return barrier;
}

void BarrierSetNMethod::deoptimize(nmethod* nm, address* return_address_ptr) {
  // Not required on s390 as a valid backchain is present
  return;
}

void BarrierSetNMethod::arm(nmethod* nm, int arm_value) {
  if (!supports_entry_barrier(nm)) {
    return;
  }

  NativeMethodBarrier* barrier = get_nmethod_barrier(nm);
  barrier->set_guard_value(arm_value);
}

void BarrierSetNMethod::disarm(nmethod* nm) {
  if (!supports_entry_barrier(nm)) {
    return;
  }

  NativeMethodBarrier* barrier = get_nmethod_barrier(nm);
  barrier->set_guard_value(disarmed_value());
}

bool BarrierSetNMethod::is_armed(nmethod* nm) {
  if (!supports_entry_barrier(nm)) {
    return false;
  }

  NativeMethodBarrier* barrier = get_nmethod_barrier(nm);
  return barrier->get_guard_value() != disarmed_value();
}
