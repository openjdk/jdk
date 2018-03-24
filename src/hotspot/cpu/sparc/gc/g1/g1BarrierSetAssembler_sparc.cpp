/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/g1/g1BarrierSet.hpp"
#include "gc/g1/g1CardTable.hpp"
#include "gc/g1/g1BarrierSetAssembler.hpp"
#include "gc/g1/heapRegion.hpp"
#include "gc/shared/collectedHeap.hpp"
#include "interpreter/interp_masm.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/thread.hpp"
#include "utilities/macros.hpp"

#define __ masm->

void G1BarrierSetAssembler::gen_write_ref_array_pre_barrier(MacroAssembler* masm, DecoratorSet decorators,
                                                            Register addr, Register count) {
  bool dest_uninitialized = (decorators & AS_DEST_NOT_INITIALIZED) != 0;
  // With G1, don't generate the call if we statically know that the target in uninitialized
  if (!dest_uninitialized) {
    Register tmp = O5;
    assert_different_registers(addr, count, tmp);
    Label filtered;
    // Is marking active?
    if (in_bytes(SATBMarkQueue::byte_width_of_active()) == 4) {
      __ ld(G2, in_bytes(JavaThread::satb_mark_queue_offset() + SATBMarkQueue::byte_offset_of_active()), tmp);
    } else {
      guarantee(in_bytes(SATBMarkQueue::byte_width_of_active()) == 1,
                "Assumption");
      __ ldsb(G2, in_bytes(JavaThread::satb_mark_queue_offset() + SATBMarkQueue::byte_offset_of_active()), tmp);
    }
    // Is marking active?
    __ cmp_and_br_short(tmp, G0, Assembler::equal, Assembler::pt, filtered);

    __ save_frame(0);
    // Save the necessary global regs... will be used after.
    if (addr->is_global()) {
      __ mov(addr, L0);
    }
    if (count->is_global()) {
      __ mov(count, L1);
    }
    __ mov(addr->after_save(), O0);
    // Get the count into O1
    address slowpath = UseCompressedOops ? CAST_FROM_FN_PTR(address, G1BarrierSet::write_ref_array_pre_narrow_oop_entry)
                                         : CAST_FROM_FN_PTR(address, G1BarrierSet::write_ref_array_pre_oop_entry);
    __ call(slowpath);
    __ delayed()->mov(count->after_save(), O1);
    if (addr->is_global()) {
      __ mov(L0, addr);
    }
    if (count->is_global()) {
      __ mov(L1, count);
    }
    __ restore();

    __ bind(filtered);
    DEBUG_ONLY(__ set(0xDEADC0DE, tmp);) // we have killed tmp
  }
}

void G1BarrierSetAssembler::gen_write_ref_array_post_barrier(MacroAssembler* masm, DecoratorSet decorators,
                                                             Register addr, Register count, Register tmp) {
  // Get some new fresh output registers.
  __ save_frame(0);
  __ mov(addr->after_save(), O0);
  __ call(CAST_FROM_FN_PTR(address, G1BarrierSet::write_ref_array_post_entry));
  __ delayed()->mov(count->after_save(), O1);
  __ restore();
}
