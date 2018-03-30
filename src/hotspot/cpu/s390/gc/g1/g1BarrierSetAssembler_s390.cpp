/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2018, SAP SE. All rights reserved.
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
#include "asm/macroAssembler.inline.hpp"
#include "registerSaver_s390.hpp"
#include "gc/g1/g1CardTable.hpp"
#include "gc/g1/g1BarrierSet.hpp"
#include "gc/g1/g1BarrierSetAssembler.hpp"
#include "gc/g1/heapRegion.hpp"
#include "gc/shared/collectedHeap.hpp"
#include "runtime/thread.hpp"
#include "interpreter/interp_masm.hpp"

#define __ masm->

#define BLOCK_COMMENT(str) if (PrintAssembly) __ block_comment(str)

void G1BarrierSetAssembler::gen_write_ref_array_pre_barrier(MacroAssembler* masm, DecoratorSet decorators,
                                                            Register addr, Register count) {
  bool dest_uninitialized = (decorators & AS_DEST_NOT_INITIALIZED) != 0;

  // With G1, don't generate the call if we statically know that the target is uninitialized.
  if (!dest_uninitialized) {
    // Is marking active?
    Label filtered;
    assert_different_registers(addr,  Z_R0_scratch);  // would be destroyed by push_frame()
    assert_different_registers(count, Z_R0_scratch);  // would be destroyed by push_frame()
    Register Rtmp1 = Z_R0_scratch;
    const int active_offset = in_bytes(JavaThread::satb_mark_queue_offset() +
                                       SATBMarkQueue::byte_offset_of_active());
    if (in_bytes(SATBMarkQueue::byte_width_of_active()) == 4) {
      __ load_and_test_int(Rtmp1, Address(Z_thread, active_offset));
    } else {
      guarantee(in_bytes(SATBMarkQueue::byte_width_of_active()) == 1, "Assumption");
      __ load_and_test_byte(Rtmp1, Address(Z_thread, active_offset));
    }
    __ z_bre(filtered); // Activity indicator is zero, so there is no marking going on currently.

    RegisterSaver::save_live_registers(masm, RegisterSaver::arg_registers); // Creates frame.

    if (UseCompressedOops) {
      __ call_VM_leaf(CAST_FROM_FN_PTR(address, G1BarrierSet::write_ref_array_pre_narrow_oop_entry), addr, count);
    } else {
      __ call_VM_leaf(CAST_FROM_FN_PTR(address, G1BarrierSet::write_ref_array_pre_oop_entry), addr, count);
    }

    RegisterSaver::restore_live_registers(masm, RegisterSaver::arg_registers);

    __ bind(filtered);
  }
}

void G1BarrierSetAssembler::gen_write_ref_array_post_barrier(MacroAssembler* masm, DecoratorSet decorators,
                                                             Register addr, Register count, bool do_return) {
  address entry_point = CAST_FROM_FN_PTR(address, G1BarrierSet::write_ref_array_post_entry);
  if (!do_return) {
    assert_different_registers(addr,  Z_R0_scratch);  // would be destroyed by push_frame()
    assert_different_registers(count, Z_R0_scratch);  // would be destroyed by push_frame()
    RegisterSaver::save_live_registers(masm, RegisterSaver::arg_registers); // Creates frame.
    __ call_VM_leaf(entry_point, addr, count);
    RegisterSaver::restore_live_registers(masm, RegisterSaver::arg_registers);
  } else {
    // Tail call: call c and return to stub caller.
    __ lgr_if_needed(Z_ARG1, addr);
    __ lgr_if_needed(Z_ARG2, count);
    __ load_const(Z_R1, entry_point);
    __ z_br(Z_R1); // Branch without linking, callee will return to stub caller.
  }
}
