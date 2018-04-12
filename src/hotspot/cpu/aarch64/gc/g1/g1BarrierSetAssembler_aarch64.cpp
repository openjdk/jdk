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
#include "gc/g1/g1ThreadLocalData.hpp"
#include "gc/g1/heapRegion.hpp"
#include "gc/shared/collectedHeap.hpp"
#include "interpreter/interp_masm.hpp"

#define __ masm->

void G1BarrierSetAssembler::gen_write_ref_array_pre_barrier(MacroAssembler* masm, DecoratorSet decorators,
                                                            Register addr, Register count, RegSet saved_regs) {
  bool dest_uninitialized = (decorators & AS_DEST_NOT_INITIALIZED) != 0;
  if (!dest_uninitialized) {
    __ push(saved_regs, sp);
    if (count == c_rarg0) {
      if (addr == c_rarg1) {
        // exactly backwards!!
        __ mov(rscratch1, c_rarg0);
        __ mov(c_rarg0, c_rarg1);
        __ mov(c_rarg1, rscratch1);
      } else {
        __ mov(c_rarg1, count);
        __ mov(c_rarg0, addr);
      }
    } else {
      __ mov(c_rarg0, addr);
      __ mov(c_rarg1, count);
    }
    if (UseCompressedOops) {
      __ call_VM_leaf(CAST_FROM_FN_PTR(address, G1BarrierSet::write_ref_array_pre_narrow_oop_entry), 2);
    } else {
      __ call_VM_leaf(CAST_FROM_FN_PTR(address, G1BarrierSet::write_ref_array_pre_oop_entry), 2);
    }
    __ pop(saved_regs, sp);
  }
}

void G1BarrierSetAssembler::gen_write_ref_array_post_barrier(MacroAssembler* masm, DecoratorSet decorators,
                                                             Register start, Register end, Register scratch, RegSet saved_regs) {
  __ push(saved_regs, sp);
  // must compute element count unless barrier set interface is changed (other platforms supply count)
  assert_different_registers(start, end, scratch);
  __ lea(scratch, Address(end, BytesPerHeapOop));
  __ sub(scratch, scratch, start);               // subtract start to get #bytes
  __ lsr(scratch, scratch, LogBytesPerHeapOop);  // convert to element count
  __ mov(c_rarg0, start);
  __ mov(c_rarg1, scratch);
  __ call_VM_leaf(CAST_FROM_FN_PTR(address, G1BarrierSet::write_ref_array_post_entry), 2);
  __ pop(saved_regs, sp);
}

void G1BarrierSetAssembler::g1_write_barrier_pre(MacroAssembler* masm,
                                                 Register obj,
                                                 Register pre_val,
                                                 Register thread,
                                                 Register tmp,
                                                 bool tosca_live,
                                                 bool expand_call) {
  // If expand_call is true then we expand the call_VM_leaf macro
  // directly to skip generating the check by
  // InterpreterMacroAssembler::call_VM_leaf_base that checks _last_sp.

  assert(thread == rthread, "must be");

  Label done;
  Label runtime;

  assert_different_registers(obj, pre_val, tmp, rscratch1);
  assert(pre_val != noreg &&  tmp != noreg, "expecting a register");

  Address in_progress(thread, in_bytes(G1ThreadLocalData::satb_mark_queue_active_offset()));
  Address index(thread, in_bytes(G1ThreadLocalData::satb_mark_queue_index_offset()));
  Address buffer(thread, in_bytes(G1ThreadLocalData::satb_mark_queue_buffer_offset()));

  // Is marking active?
  if (in_bytes(SATBMarkQueue::byte_width_of_active()) == 4) {
    __ ldrw(tmp, in_progress);
  } else {
    assert(in_bytes(SATBMarkQueue::byte_width_of_active()) == 1, "Assumption");
    __ ldrb(tmp, in_progress);
  }
  __ cbzw(tmp, done);

  // Do we need to load the previous value?
  if (obj != noreg) {
    __ load_heap_oop(pre_val, Address(obj, 0));
  }

  // Is the previous value null?
  __ cbz(pre_val, done);

  // Can we store original value in the thread's buffer?
  // Is index == 0?
  // (The index field is typed as size_t.)

  __ ldr(tmp, index);                      // tmp := *index_adr
  __ cbz(tmp, runtime);                    // tmp == 0?
                                        // If yes, goto runtime

  __ sub(tmp, tmp, wordSize);              // tmp := tmp - wordSize
  __ str(tmp, index);                      // *index_adr := tmp
  __ ldr(rscratch1, buffer);
  __ add(tmp, tmp, rscratch1);             // tmp := tmp + *buffer_adr

  // Record the previous value
  __ str(pre_val, Address(tmp, 0));
  __ b(done);

  __ bind(runtime);
  // save the live input values
  RegSet saved = RegSet::of(pre_val);
  if (tosca_live) saved += RegSet::of(r0);
  if (obj != noreg) saved += RegSet::of(obj);

  __ push(saved, sp);

  // Calling the runtime using the regular call_VM_leaf mechanism generates
  // code (generated by InterpreterMacroAssember::call_VM_leaf_base)
  // that checks that the *(rfp+frame::interpreter_frame_last_sp) == NULL.
  //
  // If we care generating the pre-barrier without a frame (e.g. in the
  // intrinsified Reference.get() routine) then ebp might be pointing to
  // the caller frame and so this check will most likely fail at runtime.
  //
  // Expanding the call directly bypasses the generation of the check.
  // So when we do not have have a full interpreter frame on the stack
  // expand_call should be passed true.

  if (expand_call) {
    assert(pre_val != c_rarg1, "smashed arg");
    __ super_call_VM_leaf(CAST_FROM_FN_PTR(address, SharedRuntime::g1_wb_pre), pre_val, thread);
  } else {
    __ call_VM_leaf(CAST_FROM_FN_PTR(address, SharedRuntime::g1_wb_pre), pre_val, thread);
  }

  __ pop(saved, sp);

  __ bind(done);

}

void G1BarrierSetAssembler::g1_write_barrier_post(MacroAssembler* masm,
                                                  Register store_addr,
                                                  Register new_val,
                                                  Register thread,
                                                  Register tmp,
                                                  Register tmp2) {
  assert(thread == rthread, "must be");
  assert_different_registers(store_addr, new_val, thread, tmp, tmp2,
                             rscratch1);
  assert(store_addr != noreg && new_val != noreg && tmp != noreg
         && tmp2 != noreg, "expecting a register");

  Address queue_index(thread, in_bytes(G1ThreadLocalData::dirty_card_queue_index_offset()));
  Address buffer(thread, in_bytes(G1ThreadLocalData::dirty_card_queue_buffer_offset()));

  BarrierSet* bs = Universe::heap()->barrier_set();
  CardTableBarrierSet* ctbs = barrier_set_cast<CardTableBarrierSet>(bs);
  CardTable* ct = ctbs->card_table();
  assert(sizeof(*ct->byte_map_base()) == sizeof(jbyte), "adjust this code");

  Label done;
  Label runtime;

  // Does store cross heap regions?

  __ eor(tmp, store_addr, new_val);
  __ lsr(tmp, tmp, HeapRegion::LogOfHRGrainBytes);
  __ cbz(tmp, done);

  // crosses regions, storing NULL?

  __ cbz(new_val, done);

  // storing region crossing non-NULL, is card already dirty?

  ExternalAddress cardtable((address) ct->byte_map_base());
  assert(sizeof(*ct->byte_map_base()) == sizeof(jbyte), "adjust this code");
  const Register card_addr = tmp;

  __ lsr(card_addr, store_addr, CardTable::card_shift);

  // get the address of the card
  __ load_byte_map_base(tmp2);
  __ add(card_addr, card_addr, tmp2);
  __ ldrb(tmp2, Address(card_addr));
  __ cmpw(tmp2, (int)G1CardTable::g1_young_card_val());
  __ br(Assembler::EQ, done);

  assert((int)CardTable::dirty_card_val() == 0, "must be 0");

  __ membar(Assembler::StoreLoad);

  __ ldrb(tmp2, Address(card_addr));
  __ cbzw(tmp2, done);

  // storing a region crossing, non-NULL oop, card is clean.
  // dirty card and log.

  __ strb(zr, Address(card_addr));

  __ ldr(rscratch1, queue_index);
  __ cbz(rscratch1, runtime);
  __ sub(rscratch1, rscratch1, wordSize);
  __ str(rscratch1, queue_index);

  __ ldr(tmp2, buffer);
  __ str(card_addr, Address(tmp2, rscratch1));
  __ b(done);

  __ bind(runtime);
  // save the live input values
  RegSet saved = RegSet::of(store_addr, new_val);
  __ push(saved, sp);
  __ call_VM_leaf(CAST_FROM_FN_PTR(address, SharedRuntime::g1_wb_post), card_addr, thread);
  __ pop(saved, sp);

  __ bind(done);
}

void G1BarrierSetAssembler::load_at(MacroAssembler* masm, DecoratorSet decorators, BasicType type,
                                    Register dst, Address src, Register tmp1, Register tmp_thread) {
  bool on_oop = type == T_OBJECT || type == T_ARRAY;
  bool on_weak = (decorators & ON_WEAK_OOP_REF) != 0;
  bool on_phantom = (decorators & ON_PHANTOM_OOP_REF) != 0;
  bool on_reference = on_weak || on_phantom;
  ModRefBarrierSetAssembler::load_at(masm, decorators, type, dst, src, tmp1, tmp_thread);
  if (on_oop && on_reference) {
    // Generate the G1 pre-barrier code to log the value of
    // the referent field in an SATB buffer.
    g1_write_barrier_pre(masm /* masm */,
                         noreg /* obj */,
                         dst /* pre_val */,
                         rthread /* thread */,
                         tmp1 /* tmp */,
                         true /* tosca_live */,
                         true /* expand_call */);
  }
}

void G1BarrierSetAssembler::oop_store_at(MacroAssembler* masm, DecoratorSet decorators, BasicType type,
                                         Address dst, Register val, Register tmp1, Register tmp2) {
  // flatten object address if needed
  if (dst.index() == noreg && dst.offset() == 0) {
    if (dst.base() != r3) {
      __ mov(r3, dst.base());
    }
  } else {
    __ lea(r3, dst);
  }

  g1_write_barrier_pre(masm,
                       r3 /* obj */,
                       tmp2 /* pre_val */,
                       rthread /* thread */,
                       tmp1  /* tmp */,
                       val != noreg /* tosca_live */,
                       false /* expand_call */);

  if (val == noreg) {
    __ store_heap_oop_null(Address(r3, 0));
  } else {
    // G1 barrier needs uncompressed oop for region cross check.
    Register new_val = val;
    if (UseCompressedOops) {
      new_val = rscratch2;
      __ mov(new_val, val);
    }
    __ store_heap_oop(Address(r3, 0), val);
    g1_write_barrier_post(masm,
                          r3 /* store_adr */,
                          new_val /* new_val */,
                          rthread /* thread */,
                          tmp1 /* tmp */,
                          tmp2 /* tmp2 */);
  }

}

#undef __
