/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/g1/g1BarrierSetAssembler.hpp"
#include "gc/g1/g1BarrierSetRuntime.hpp"
#include "gc/g1/g1CardTable.hpp"
#include "gc/g1/g1DirtyCardQueue.hpp"
#include "gc/g1/g1SATBMarkQueueSet.hpp"
#include "gc/g1/g1ThreadLocalData.hpp"
#include "gc/g1/heapRegion.hpp"
#include "interpreter/interp_masm.hpp"
#include "runtime/sharedRuntime.hpp"
#include "utilities/macros.hpp"
#ifdef COMPILER1
#include "c1/c1_LIRAssembler.hpp"
#include "c1/c1_MacroAssembler.hpp"
#include "gc/g1/c1/g1BarrierSetC1.hpp"
#endif

#define __ masm->

void G1BarrierSetAssembler::gen_write_ref_array_pre_barrier(MacroAssembler* masm, DecoratorSet decorators,
                                                            Register addr, Register count) {
  bool dest_uninitialized = (decorators & IS_DEST_UNINITIALIZED) != 0;
  // With G1, don't generate the call if we statically know that the target in uninitialized
  if (!dest_uninitialized) {
    Register tmp = O5;
    assert_different_registers(addr, count, tmp);
    Label filtered;
    // Is marking active?
    if (in_bytes(SATBMarkQueue::byte_width_of_active()) == 4) {
      __ ld(G2, in_bytes(G1ThreadLocalData::satb_mark_queue_active_offset()), tmp);
    } else {
      guarantee(in_bytes(SATBMarkQueue::byte_width_of_active()) == 1, "Assumption");
      __ ldsb(G2, in_bytes(G1ThreadLocalData::satb_mark_queue_active_offset()), tmp);
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
    address slowpath = UseCompressedOops ? CAST_FROM_FN_PTR(address, G1BarrierSetRuntime::write_ref_array_pre_narrow_oop_entry)
                                         : CAST_FROM_FN_PTR(address, G1BarrierSetRuntime::write_ref_array_pre_oop_entry);
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
  __ call(CAST_FROM_FN_PTR(address, G1BarrierSetRuntime::write_ref_array_post_entry));
  __ delayed()->mov(count->after_save(), O1);
  __ restore();
}

#undef __

static address satb_log_enqueue_with_frame = NULL;
static u_char* satb_log_enqueue_with_frame_end = NULL;

static address satb_log_enqueue_frameless = NULL;
static u_char* satb_log_enqueue_frameless_end = NULL;

static int EnqueueCodeSize = 128 DEBUG_ONLY( + 256); // Instructions?

static void generate_satb_log_enqueue(bool with_frame) {
  BufferBlob* bb = BufferBlob::create("enqueue_with_frame", EnqueueCodeSize);
  CodeBuffer buf(bb);
  MacroAssembler masm(&buf);

#define __ masm.

  address start = __ pc();
  Register pre_val;

  Label refill, restart;
  if (with_frame) {
    __ save_frame(0);
    pre_val = I0;  // Was O0 before the save.
  } else {
    pre_val = O0;
  }

  int satb_q_index_byte_offset = in_bytes(G1ThreadLocalData::satb_mark_queue_index_offset());
  int satb_q_buf_byte_offset = in_bytes(G1ThreadLocalData::satb_mark_queue_buffer_offset());

  assert(in_bytes(SATBMarkQueue::byte_width_of_index()) == sizeof(intptr_t) &&
         in_bytes(SATBMarkQueue::byte_width_of_buf()) == sizeof(intptr_t),
         "check sizes in assembly below");

  __ bind(restart);

  // Load the index into the SATB buffer. SATBMarkQueue::_index is a size_t
  // so ld_ptr is appropriate.
  __ ld_ptr(G2_thread, satb_q_index_byte_offset, L0);

  // index == 0?
  __ cmp_and_brx_short(L0, G0, Assembler::equal, Assembler::pn, refill);

  __ ld_ptr(G2_thread, satb_q_buf_byte_offset, L1);
  __ sub(L0, oopSize, L0);

  __ st_ptr(pre_val, L1, L0);  // [_buf + index] := I0
  if (!with_frame) {
    // Use return-from-leaf
    __ retl();
    __ delayed()->st_ptr(L0, G2_thread, satb_q_index_byte_offset);
  } else {
    // Not delayed.
    __ st_ptr(L0, G2_thread, satb_q_index_byte_offset);
  }
  if (with_frame) {
    __ ret();
    __ delayed()->restore();
  }
  __ bind(refill);

  address handle_zero =
    CAST_FROM_FN_PTR(address,
                     &G1SATBMarkQueueSet::handle_zero_index_for_thread);
  // This should be rare enough that we can afford to save all the
  // scratch registers that the calling context might be using.
  __ mov(G1_scratch, L0);
  __ mov(G3_scratch, L1);
  __ mov(G4, L2);
  // We need the value of O0 above (for the write into the buffer), so we
  // save and restore it.
  __ mov(O0, L3);
  // Since the call will overwrite O7, we save and restore that, as well.
  __ mov(O7, L4);
  __ call_VM_leaf(L5, handle_zero, G2_thread);
  __ mov(L0, G1_scratch);
  __ mov(L1, G3_scratch);
  __ mov(L2, G4);
  __ mov(L3, O0);
  __ br(Assembler::always, /*annul*/false, Assembler::pt, restart);
  __ delayed()->mov(L4, O7);

  if (with_frame) {
    satb_log_enqueue_with_frame = start;
    satb_log_enqueue_with_frame_end = __ pc();
  } else {
    satb_log_enqueue_frameless = start;
    satb_log_enqueue_frameless_end = __ pc();
  }

#undef __
}

#define __ masm->

void G1BarrierSetAssembler::g1_write_barrier_pre(MacroAssembler* masm,
                                                 Register obj,
                                                 Register index,
                                                 int offset,
                                                 Register pre_val,
                                                 Register tmp,
                                                 bool preserve_o_regs) {
  Label filtered;

  if (obj == noreg) {
    // We are not loading the previous value so make
    // sure that we don't trash the value in pre_val
    // with the code below.
    assert_different_registers(pre_val, tmp);
  } else {
    // We will be loading the previous value
    // in this code so...
    assert(offset == 0 || index == noreg, "choose one");
    assert(pre_val == noreg, "check this code");
  }

  // Is marking active?
  if (in_bytes(SATBMarkQueue::byte_width_of_active()) == 4) {
    __ ld(G2, in_bytes(G1ThreadLocalData::satb_mark_queue_active_offset()), tmp);
  } else {
    guarantee(in_bytes(SATBMarkQueue::byte_width_of_active()) == 1, "Assumption");
    __ ldsb(G2, in_bytes(G1ThreadLocalData::satb_mark_queue_active_offset()), tmp);
  }

  // Is marking active?
  __ cmp_and_br_short(tmp, G0, Assembler::equal, Assembler::pt, filtered);

  // Do we need to load the previous value?
  if (obj != noreg) {
    // Load the previous value...
    if (index == noreg) {
      if (Assembler::is_simm13(offset)) {
        __ load_heap_oop(obj, offset, tmp);
      } else {
        __ set(offset, tmp);
        __ load_heap_oop(obj, tmp, tmp);
      }
    } else {
      __ load_heap_oop(obj, index, tmp);
    }
    // Previous value has been loaded into tmp
    pre_val = tmp;
  }

  assert(pre_val != noreg, "must have a real register");

  // Is the previous value null?
  __ cmp_and_brx_short(pre_val, G0, Assembler::equal, Assembler::pt, filtered);

  // OK, it's not filtered, so we'll need to call enqueue.  In the normal
  // case, pre_val will be a scratch G-reg, but there are some cases in
  // which it's an O-reg.  In the first case, do a normal call.  In the
  // latter, do a save here and call the frameless version.

  guarantee(pre_val->is_global() || pre_val->is_out(),
            "Or we need to think harder.");

  if (pre_val->is_global() && !preserve_o_regs) {
    __ call(satb_log_enqueue_with_frame);
    __ delayed()->mov(pre_val, O0);
  } else {
    __ save_frame(0);
    __ call(satb_log_enqueue_frameless);
    __ delayed()->mov(pre_val->after_save(), O0);
    __ restore();
  }

  __ bind(filtered);
}

#undef __

static address dirty_card_log_enqueue = 0;
static u_char* dirty_card_log_enqueue_end = 0;

// This gets to assume that o0 contains the object address.
static void generate_dirty_card_log_enqueue(CardTable::CardValue* byte_map_base) {
  BufferBlob* bb = BufferBlob::create("dirty_card_enqueue", EnqueueCodeSize*2);
  CodeBuffer buf(bb);
  MacroAssembler masm(&buf);
#define __ masm.
  address start = __ pc();

  Label not_already_dirty, restart, refill, young_card;

  __ srlx(O0, CardTable::card_shift, O0);
  AddressLiteral addrlit(byte_map_base);
  __ set(addrlit, O1); // O1 := <card table base>
  __ ldub(O0, O1, O2); // O2 := [O0 + O1]

  __ cmp_and_br_short(O2, G1CardTable::g1_young_card_val(), Assembler::equal, Assembler::pt, young_card);

  __ membar(Assembler::Membar_mask_bits(Assembler::StoreLoad));
  __ ldub(O0, O1, O2); // O2 := [O0 + O1]

  assert(G1CardTable::dirty_card_val() == 0, "otherwise check this code");
  __ cmp_and_br_short(O2, G0, Assembler::notEqual, Assembler::pt, not_already_dirty);

  __ bind(young_card);
  // We didn't take the branch, so we're already dirty: return.
  // Use return-from-leaf
  __ retl();
  __ delayed()->nop();

  // Not dirty.
  __ bind(not_already_dirty);

  // Get O0 + O1 into a reg by itself
  __ add(O0, O1, O3);

  // First, dirty it.
  __ stb(G0, O3, G0);  // [cardPtr] := 0  (i.e., dirty).

  int dirty_card_q_index_byte_offset = in_bytes(G1ThreadLocalData::dirty_card_queue_index_offset());
  int dirty_card_q_buf_byte_offset = in_bytes(G1ThreadLocalData::dirty_card_queue_buffer_offset());
  __ bind(restart);

  // Load the index into the update buffer. G1DirtyCardQueue::_index is
  // a size_t so ld_ptr is appropriate here.
  __ ld_ptr(G2_thread, dirty_card_q_index_byte_offset, L0);

  // index == 0?
  __ cmp_and_brx_short(L0, G0, Assembler::equal, Assembler::pn, refill);

  __ ld_ptr(G2_thread, dirty_card_q_buf_byte_offset, L1);
  __ sub(L0, oopSize, L0);

  __ st_ptr(O3, L1, L0);  // [_buf + index] := I0
  // Use return-from-leaf
  __ retl();
  __ delayed()->st_ptr(L0, G2_thread, dirty_card_q_index_byte_offset);

  __ bind(refill);
  address handle_zero =
    CAST_FROM_FN_PTR(address,
                     &G1DirtyCardQueueSet::handle_zero_index_for_thread);
  // This should be rare enough that we can afford to save all the
  // scratch registers that the calling context might be using.
  __ mov(G1_scratch, L3);
  __ mov(G3_scratch, L5);
  // We need the value of O3 above (for the write into the buffer), so we
  // save and restore it.
  __ mov(O3, L6);
  // Since the call will overwrite O7, we save and restore that, as well.
  __ mov(O7, L4);

  __ call_VM_leaf(L7_thread_cache, handle_zero, G2_thread);
  __ mov(L3, G1_scratch);
  __ mov(L5, G3_scratch);
  __ mov(L6, O3);
  __ br(Assembler::always, /*annul*/false, Assembler::pt, restart);
  __ delayed()->mov(L4, O7);

  dirty_card_log_enqueue = start;
  dirty_card_log_enqueue_end = __ pc();
  // XXX Should have a guarantee here about not going off the end!
  // Does it already do so?  Do an experiment...

#undef __

}

#define __ masm->

void G1BarrierSetAssembler::g1_write_barrier_post(MacroAssembler* masm, Register store_addr, Register new_val, Register tmp) {
  Label filtered;
  MacroAssembler* post_filter_masm = masm;

  if (new_val == G0) return;

  G1BarrierSet* bs = barrier_set_cast<G1BarrierSet>(BarrierSet::barrier_set());

  __ xor3(store_addr, new_val, tmp);
  __ srlx(tmp, HeapRegion::LogOfHRGrainBytes, tmp);

  __ cmp_and_brx_short(tmp, G0, Assembler::equal, Assembler::pt, filtered);

  // If the "store_addr" register is an "in" or "local" register, move it to
  // a scratch reg so we can pass it as an argument.
  bool use_scr = !(store_addr->is_global() || store_addr->is_out());
  // Pick a scratch register different from "tmp".
  Register scr = (tmp == G1_scratch ? G3_scratch : G1_scratch);
  // Make sure we use up the delay slot!
  if (use_scr) {
    post_filter_masm->mov(store_addr, scr);
  } else {
    post_filter_masm->nop();
  }
  __ save_frame(0);
  __ call(dirty_card_log_enqueue);
  if (use_scr) {
    __ delayed()->mov(scr, O0);
  } else {
    __ delayed()->mov(store_addr->after_save(), O0);
  }
  __ restore();

  __ bind(filtered);
}

void G1BarrierSetAssembler::oop_store_at(MacroAssembler* masm, DecoratorSet decorators, BasicType type,
                                         Register val, Address dst, Register tmp) {
  bool in_heap = (decorators & IN_HEAP) != 0;
  bool as_normal = (decorators & AS_NORMAL) != 0;
  assert((decorators & IS_DEST_UNINITIALIZED) == 0, "unsupported");

  bool needs_pre_barrier = as_normal;
  // No need for post barrier if storing NULL
  bool needs_post_barrier = val != G0 && in_heap;

  bool is_array = (decorators & IS_ARRAY) != 0;
  bool on_anonymous = (decorators & ON_UNKNOWN_OOP_REF) != 0;
  bool precise = is_array || on_anonymous;

  Register index = dst.has_index() ? dst.index() : noreg;
  int disp = dst.has_disp() ? dst.disp() : 0;

  if (needs_pre_barrier) {
    // Load and record the previous value.
    g1_write_barrier_pre(masm, dst.base(), index, disp,
                         noreg /* pre_val */,
                         tmp, true /*preserve_o_regs*/);
  }

  Register new_val = val;
  if (needs_post_barrier) {
    // G1 barrier needs uncompressed oop for region cross check.
    if (UseCompressedOops && val != G0) {
      new_val = tmp;
      __ mov(val, new_val);
    }
  }

  BarrierSetAssembler::store_at(masm, decorators, type, val, dst, tmp);

  if (needs_post_barrier) {
    Register base = dst.base();
    if (precise) {
      if (!dst.has_index()) {
        __ add(base, disp, base);
      } else {
        assert(!dst.has_disp(), "not supported yet");
        __ add(base, index, base);
      }
    }
    g1_write_barrier_post(masm, base, new_val, tmp);
  }
}

void G1BarrierSetAssembler::load_at(MacroAssembler* masm, DecoratorSet decorators, BasicType type,
                                    Address src, Register dst, Register tmp) {
  bool on_oop = type == T_OBJECT || type == T_ARRAY;
  bool on_weak = (decorators & ON_WEAK_OOP_REF) != 0;
  bool on_phantom = (decorators & ON_PHANTOM_OOP_REF) != 0;
  bool on_reference = on_weak || on_phantom;
  // Load the value of the referent field.
  ModRefBarrierSetAssembler::load_at(masm, decorators, type, src, dst, tmp);
  if (on_oop && on_reference) {
    // Generate the G1 pre-barrier code to log the value of
    // the referent field in an SATB buffer. Note with
    // these parameters the pre-barrier does not generate
    // the load of the previous value

    Register pre_val = dst;
    bool saved = false;
    if (pre_val->is_in()) {
      // The g1_write_barrier_pre method assumes that the pre_val
      // is not in an input register.
      __ save_frame_and_mov(0, pre_val, O0);
      pre_val = O0;
      saved = true;
    }

    g1_write_barrier_pre(masm, noreg /* obj */, noreg /* index */, 0 /* offset */,
                         pre_val /* pre_val */,
                         tmp /* tmp */,
                         true /* preserve_o_regs */);

    if (saved) {
      __ restore();
    }
  }
}

void G1BarrierSetAssembler::barrier_stubs_init() {
  if (dirty_card_log_enqueue == 0) {
    G1BarrierSet* bs = barrier_set_cast<G1BarrierSet>(BarrierSet::barrier_set());
    CardTable *ct = bs->card_table();
    generate_dirty_card_log_enqueue(ct->byte_map_base());
    assert(dirty_card_log_enqueue != 0, "postcondition.");
  }
  if (satb_log_enqueue_with_frame == 0) {
    generate_satb_log_enqueue(true);
    assert(satb_log_enqueue_with_frame != 0, "postcondition.");
  }
  if (satb_log_enqueue_frameless == 0) {
    generate_satb_log_enqueue(false);
    assert(satb_log_enqueue_frameless != 0, "postcondition.");
  }
}

#ifdef COMPILER1

#undef __
#define __ ce->masm()->

void G1BarrierSetAssembler::gen_pre_barrier_stub(LIR_Assembler* ce, G1PreBarrierStub* stub) {
  G1BarrierSetC1* bs = (G1BarrierSetC1*)BarrierSet::barrier_set()->barrier_set_c1();
  // At this point we know that marking is in progress.
  // If do_load() is true then we have to emit the
  // load of the previous value; otherwise it has already
  // been loaded into _pre_val.

  __ bind(*stub->entry());

  assert(stub->pre_val()->is_register(), "Precondition.");
  Register pre_val_reg = stub->pre_val()->as_register();

  if (stub->do_load()) {
    ce->mem2reg(stub->addr(), stub->pre_val(), T_OBJECT, stub->patch_code(), stub->info(), false /*wide*/, false /*unaligned*/);
  }

  if (__ is_in_wdisp16_range(*stub->continuation())) {
    __ br_null(pre_val_reg, /*annul*/false, Assembler::pt, *stub->continuation());
  } else {
    __ cmp(pre_val_reg, G0);
    __ brx(Assembler::equal, false, Assembler::pn, *stub->continuation());
  }
  __ delayed()->nop();

  __ call(bs->pre_barrier_c1_runtime_code_blob()->code_begin());
  __ delayed()->mov(pre_val_reg, G4);
  __ br(Assembler::always, false, Assembler::pt, *stub->continuation());
  __ delayed()->nop();
}

void G1BarrierSetAssembler::gen_post_barrier_stub(LIR_Assembler* ce, G1PostBarrierStub* stub) {
  G1BarrierSetC1* bs = (G1BarrierSetC1*)BarrierSet::barrier_set()->barrier_set_c1();
  __ bind(*stub->entry());

  assert(stub->addr()->is_register(), "Precondition.");
  assert(stub->new_val()->is_register(), "Precondition.");
  Register addr_reg = stub->addr()->as_pointer_register();
  Register new_val_reg = stub->new_val()->as_register();

  if (__ is_in_wdisp16_range(*stub->continuation())) {
    __ br_null(new_val_reg, /*annul*/false, Assembler::pt, *stub->continuation());
  } else {
    __ cmp(new_val_reg, G0);
    __ brx(Assembler::equal, false, Assembler::pn, *stub->continuation());
  }
  __ delayed()->nop();

  __ call(bs->post_barrier_c1_runtime_code_blob()->code_begin());
  __ delayed()->mov(addr_reg, G4);
  __ br(Assembler::always, false, Assembler::pt, *stub->continuation());
  __ delayed()->nop();
}

#undef __
#define __ sasm->

void G1BarrierSetAssembler::generate_c1_pre_barrier_runtime_stub(StubAssembler* sasm) {
  __ prologue("g1_pre_barrier", false);

  // G4: previous value of memory

  Register pre_val = G4;
  Register tmp  = G1_scratch;
  Register tmp2 = G3_scratch;

  Label refill, restart;
  int satb_q_active_byte_offset = in_bytes(G1ThreadLocalData::satb_mark_queue_active_offset());
  int satb_q_index_byte_offset = in_bytes(G1ThreadLocalData::satb_mark_queue_index_offset());
  int satb_q_buf_byte_offset = in_bytes(G1ThreadLocalData::satb_mark_queue_buffer_offset());

  // Is marking still active?
  if (in_bytes(SATBMarkQueue::byte_width_of_active()) == 4) {
    __ ld(G2_thread, satb_q_active_byte_offset, tmp);
  } else {
    assert(in_bytes(SATBMarkQueue::byte_width_of_active()) == 1, "Assumption");
    __ ldsb(G2_thread, satb_q_active_byte_offset, tmp);
  }
  __ cmp_and_br_short(tmp, G0, Assembler::notEqual, Assembler::pt, restart);
  __ retl();
  __ delayed()->nop();

  __ bind(restart);
  // Load the index into the SATB buffer. SATBMarkQueue::_index is a
  // size_t so ld_ptr is appropriate
  __ ld_ptr(G2_thread, satb_q_index_byte_offset, tmp);

  // index == 0?
  __ cmp_and_brx_short(tmp, G0, Assembler::equal, Assembler::pn, refill);

  __ ld_ptr(G2_thread, satb_q_buf_byte_offset, tmp2);
  __ sub(tmp, oopSize, tmp);

  __ st_ptr(pre_val, tmp2, tmp);  // [_buf + index] := <address_of_card>
  // Use return-from-leaf
  __ retl();
  __ delayed()->st_ptr(tmp, G2_thread, satb_q_index_byte_offset);

  __ bind(refill);

  __ save_live_registers_no_oop_map(true);

  __ call_VM_leaf(L7_thread_cache,
                  CAST_FROM_FN_PTR(address,
                                   G1SATBMarkQueueSet::handle_zero_index_for_thread),
                  G2_thread);

  __ restore_live_registers(true);

  __ br(Assembler::always, /*annul*/false, Assembler::pt, restart);
  __ epilogue();
}

void G1BarrierSetAssembler::generate_c1_post_barrier_runtime_stub(StubAssembler* sasm) {
  __ prologue("g1_post_barrier", false);

  G1BarrierSet* bs = barrier_set_cast<G1BarrierSet>(BarrierSet::barrier_set());

  Register addr = G4;
  Register cardtable = G5;
  Register tmp  = G1_scratch;
  Register tmp2 = G3_scratch;
  CardTable::CardValue* byte_map_base = bs->card_table()->byte_map_base();

  Label not_already_dirty, restart, refill, young_card;

#ifdef _LP64
  __ srlx(addr, CardTable::card_shift, addr);
#else
  __ srl(addr, CardTable::card_shift, addr);
#endif

  AddressLiteral rs((address)byte_map_base);
  __ set(rs, cardtable);         // cardtable := <card table base>
  __ ldub(addr, cardtable, tmp); // tmp := [addr + cardtable]

  __ cmp_and_br_short(tmp, G1CardTable::g1_young_card_val(), Assembler::equal, Assembler::pt, young_card);

  __ membar(Assembler::Membar_mask_bits(Assembler::StoreLoad));
  __ ldub(addr, cardtable, tmp); // tmp := [addr + cardtable]

  assert(G1CardTable::dirty_card_val() == 0, "otherwise check this code");
  __ cmp_and_br_short(tmp, G0, Assembler::notEqual, Assembler::pt, not_already_dirty);

  __ bind(young_card);
  // We didn't take the branch, so we're already dirty: return.
  // Use return-from-leaf
  __ retl();
  __ delayed()->nop();

  // Not dirty.
  __ bind(not_already_dirty);

  // Get cardtable + tmp into a reg by itself
  __ add(addr, cardtable, tmp2);

  // First, dirty it.
  __ stb(G0, tmp2, 0);  // [cardPtr] := 0  (i.e., dirty).

  Register tmp3 = cardtable;
  Register tmp4 = tmp;

  // these registers are now dead
  addr = cardtable = tmp = noreg;

  int dirty_card_q_index_byte_offset = in_bytes(G1ThreadLocalData::dirty_card_queue_index_offset());
  int dirty_card_q_buf_byte_offset = in_bytes(G1ThreadLocalData::dirty_card_queue_buffer_offset());

  __ bind(restart);

  // Get the index into the update buffer. G1DirtyCardQueue::_index is
  // a size_t so ld_ptr is appropriate here.
  __ ld_ptr(G2_thread, dirty_card_q_index_byte_offset, tmp3);

  // index == 0?
  __ cmp_and_brx_short(tmp3, G0, Assembler::equal,  Assembler::pn, refill);

  __ ld_ptr(G2_thread, dirty_card_q_buf_byte_offset, tmp4);
  __ sub(tmp3, oopSize, tmp3);

  __ st_ptr(tmp2, tmp4, tmp3);  // [_buf + index] := <address_of_card>
  // Use return-from-leaf
  __ retl();
  __ delayed()->st_ptr(tmp3, G2_thread, dirty_card_q_index_byte_offset);

  __ bind(refill);

  __ save_live_registers_no_oop_map(true);

  __ call_VM_leaf(L7_thread_cache,
                  CAST_FROM_FN_PTR(address,
                                   G1DirtyCardQueueSet::handle_zero_index_for_thread),
                  G2_thread);

  __ restore_live_registers(true);

  __ br(Assembler::always, /*annul*/false, Assembler::pt, restart);
  __ epilogue();
}

#undef __

#endif // COMPILER1
