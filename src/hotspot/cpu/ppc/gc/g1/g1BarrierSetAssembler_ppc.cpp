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
#include "gc/g1/g1BarrierSet.hpp"
#include "gc/g1/g1CardTable.hpp"
#include "gc/g1/g1BarrierSetAssembler.hpp"
#include "gc/g1/heapRegion.hpp"
#include "gc/shared/collectedHeap.hpp"
#include "runtime/thread.hpp"
#include "interpreter/interp_masm.hpp"

#define __ masm->

void G1BarrierSetAssembler::gen_write_ref_array_pre_barrier(MacroAssembler* masm, DecoratorSet decorators,
                                                            Register from, Register to, Register count,
                                                            Register preserve1, Register preserve2) {
  bool dest_uninitialized = (decorators & AS_DEST_NOT_INITIALIZED) != 0;
  // With G1, don't generate the call if we statically know that the target in uninitialized
  if (!dest_uninitialized) {
    int spill_slots = 3;
    if (preserve1 != noreg) { spill_slots++; }
    if (preserve2 != noreg) { spill_slots++; }
    const int frame_size = align_up(frame::abi_reg_args_size + spill_slots * BytesPerWord, frame::alignment_in_bytes);
    Label filtered;

    // Is marking active?
    if (in_bytes(SATBMarkQueue::byte_width_of_active()) == 4) {
      __ lwz(R0, in_bytes(JavaThread::satb_mark_queue_offset() + SATBMarkQueue::byte_offset_of_active()), R16_thread);
    } else {
      guarantee(in_bytes(SATBMarkQueue::byte_width_of_active()) == 1, "Assumption");
      __ lbz(R0, in_bytes(JavaThread::satb_mark_queue_offset() + SATBMarkQueue::byte_offset_of_active()), R16_thread);
    }
    __ cmpdi(CCR0, R0, 0);
    __ beq(CCR0, filtered);

    __ save_LR_CR(R0);
    __ push_frame(frame_size, R0);
    int slot_nr = 0;
    __ std(from,  frame_size - (++slot_nr) * wordSize, R1_SP);
    __ std(to,    frame_size - (++slot_nr) * wordSize, R1_SP);
    __ std(count, frame_size - (++slot_nr) * wordSize, R1_SP);
    if (preserve1 != noreg) { __ std(preserve1, frame_size - (++slot_nr) * wordSize, R1_SP); }
    if (preserve2 != noreg) { __ std(preserve2, frame_size - (++slot_nr) * wordSize, R1_SP); }

    if (UseCompressedOops) {
      __ call_VM_leaf(CAST_FROM_FN_PTR(address, G1BarrierSet::write_ref_array_pre_narrow_oop_entry), to, count);
    } else {
      __ call_VM_leaf(CAST_FROM_FN_PTR(address, G1BarrierSet::write_ref_array_pre_oop_entry), to, count);
    }

    slot_nr = 0;
    __ ld(from,  frame_size - (++slot_nr) * wordSize, R1_SP);
    __ ld(to,    frame_size - (++slot_nr) * wordSize, R1_SP);
    __ ld(count, frame_size - (++slot_nr) * wordSize, R1_SP);
    if (preserve1 != noreg) { __ ld(preserve1, frame_size - (++slot_nr) * wordSize, R1_SP); }
    if (preserve2 != noreg) { __ ld(preserve2, frame_size - (++slot_nr) * wordSize, R1_SP); }
    __ addi(R1_SP, R1_SP, frame_size); // pop_frame()
    __ restore_LR_CR(R0);

    __ bind(filtered);
  }
}

void G1BarrierSetAssembler::gen_write_ref_array_post_barrier(MacroAssembler* masm, DecoratorSet decorators,
                                                             Register addr, Register count, Register preserve) {
  int spill_slots = (preserve != noreg) ? 1 : 0;
  const int frame_size = align_up(frame::abi_reg_args_size + spill_slots * BytesPerWord, frame::alignment_in_bytes);

  __ save_LR_CR(R0);
  __ push_frame(frame_size, R0);
  if (preserve != noreg) { __ std(preserve, frame_size - 1 * wordSize, R1_SP); }
  __ call_VM_leaf(CAST_FROM_FN_PTR(address, G1BarrierSet::write_ref_array_post_entry), addr, count);
  if (preserve != noreg) { __ ld(preserve, frame_size - 1 * wordSize, R1_SP); }
  __ addi(R1_SP, R1_SP, frame_size); // pop_frame();
  __ restore_LR_CR(R0);
}
