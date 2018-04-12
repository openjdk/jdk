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
#include "gc/g1/g1ThreadLocalData.hpp"
#include "gc/g1/heapRegion.hpp"
#include "gc/shared/collectedHeap.hpp"
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
    const int active_offset = in_bytes(G1ThreadLocalData::satb_mark_queue_active_offset());
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

void G1BarrierSetAssembler::load_at(MacroAssembler* masm, DecoratorSet decorators, BasicType type,
                                    const Address& src, Register dst, Register tmp1, Register tmp2, Label *is_null) {
  bool on_oop = type == T_OBJECT || type == T_ARRAY;
  bool on_weak = (decorators & ON_WEAK_OOP_REF) != 0;
  bool on_phantom = (decorators & ON_PHANTOM_OOP_REF) != 0;
  bool on_reference = on_weak || on_phantom;
  Label done;
  if (on_oop && on_reference && is_null == NULL) { is_null = &done; }
  ModRefBarrierSetAssembler::load_at(masm, decorators, type, src, dst, tmp1, tmp2, is_null);
  if (on_oop && on_reference) {
    // Generate the G1 pre-barrier code to log the value of
    // the referent field in an SATB buffer.
    g1_write_barrier_pre(masm, decorators | OOP_NOT_NULL,
                         NULL /* obj */,
                         dst  /* pre_val */,
                         noreg/* preserve */ ,
                         tmp1, tmp2 /* tmp */,
                         true /* pre_val_needed */);
  }
  __ bind(done);
}

void G1BarrierSetAssembler::g1_write_barrier_pre(MacroAssembler* masm, DecoratorSet decorators,
                                                 const Address*  obj,
                                                 Register        Rpre_val,      // Ideally, this is a non-volatile register.
                                                 Register        Rval,          // Will be preserved.
                                                 Register        Rtmp1,         // If Rpre_val is volatile, either Rtmp1
                                                 Register        Rtmp2,         // or Rtmp2 has to be non-volatile.
                                                 bool            pre_val_needed // Save Rpre_val across runtime call, caller uses it.
                                                 ) {

  bool not_null  = (decorators & OOP_NOT_NULL) != 0,
       preloaded = obj == NULL;

  const Register Robj = obj ? obj->base() : noreg,
                 Roff = obj ? obj->index() : noreg;
  const int active_offset = in_bytes(G1ThreadLocalData::satb_mark_queue_active_offset());
  const int buffer_offset = in_bytes(G1ThreadLocalData::satb_mark_queue_buffer_offset());
  const int index_offset  = in_bytes(G1ThreadLocalData::satb_mark_queue_index_offset());
  assert_different_registers(Rtmp1, Rtmp2, Z_R0_scratch); // None of the Rtmp<i> must be Z_R0!!
  assert_different_registers(Robj, Z_R0_scratch);         // Used for addressing. Furthermore, push_frame destroys Z_R0!!
  assert_different_registers(Rval, Z_R0_scratch);         // push_frame destroys Z_R0!!

  Label callRuntime, filtered;

  BLOCK_COMMENT("g1_write_barrier_pre {");

  // Is marking active?
  // Note: value is loaded for test purposes only. No further use here.
  if (in_bytes(SATBMarkQueue::byte_width_of_active()) == 4) {
    __ load_and_test_int(Rtmp1, Address(Z_thread, active_offset));
  } else {
    guarantee(in_bytes(SATBMarkQueue::byte_width_of_active()) == 1, "Assumption");
    __ load_and_test_byte(Rtmp1, Address(Z_thread, active_offset));
  }
  __ z_bre(filtered); // Activity indicator is zero, so there is no marking going on currently.

  assert(Rpre_val != noreg, "must have a real register");


  // If an object is given, we need to load the previous value into Rpre_val.
  if (obj) {
    // Load the previous value...
    if (UseCompressedOops) {
      __ z_llgf(Rpre_val, *obj);
    } else {
      __ z_lg(Rpre_val, *obj);
    }
  }

  // Is the previous value NULL?
  // If so, we don't need to record it and we're done.
  // Note: pre_val is loaded, decompressed and stored (directly or via runtime call).
  //       Register contents is preserved across runtime call if caller requests to do so.
  if (preloaded && not_null) {
#ifdef ASSERT
    __ z_ltgr(Rpre_val, Rpre_val);
    __ asm_assert_ne("null oop not allowed (G1 pre)", 0x321); // Checked by caller.
#endif
  } else {
    __ z_ltgr(Rpre_val, Rpre_val);
    __ z_bre(filtered); // previous value is NULL, so we don't need to record it.
  }

  // Decode the oop now. We know it's not NULL.
  if (Robj != noreg && UseCompressedOops) {
    __ oop_decoder(Rpre_val, Rpre_val, /*maybeNULL=*/false);
  }

  // OK, it's not filtered, so we'll need to call enqueue.

  // We can store the original value in the thread's buffer
  // only if index > 0. Otherwise, we need runtime to handle.
  // (The index field is typed as size_t.)
  Register Rbuffer = Rtmp1, Rindex = Rtmp2;
  assert_different_registers(Rbuffer, Rindex, Rpre_val);

  __ z_lg(Rbuffer, buffer_offset, Z_thread);

  __ load_and_test_long(Rindex, Address(Z_thread, index_offset));
  __ z_bre(callRuntime); // If index == 0, goto runtime.

  __ add2reg(Rindex, -wordSize); // Decrement index.
  __ z_stg(Rindex, index_offset, Z_thread);

  // Record the previous value.
  __ z_stg(Rpre_val, 0, Rbuffer, Rindex);
  __ z_bru(filtered);  // We are done.

  Rbuffer = noreg;  // end of life
  Rindex  = noreg;  // end of life

  __ bind(callRuntime);

  // Save some registers (inputs and result) over runtime call
  // by spilling them into the top frame.
  if (Robj != noreg && Robj->is_volatile()) {
    __ z_stg(Robj, Robj->encoding()*BytesPerWord, Z_SP);
  }
  if (Roff != noreg && Roff->is_volatile()) {
    __ z_stg(Roff, Roff->encoding()*BytesPerWord, Z_SP);
  }
  if (Rval != noreg && Rval->is_volatile()) {
    __ z_stg(Rval, Rval->encoding()*BytesPerWord, Z_SP);
  }

  // Save Rpre_val (result) over runtime call.
  Register Rpre_save = Rpre_val;
  if ((Rpre_val == Z_R0_scratch) || (pre_val_needed && Rpre_val->is_volatile())) {
    guarantee(!Rtmp1->is_volatile() || !Rtmp2->is_volatile(), "oops!");
    Rpre_save = !Rtmp1->is_volatile() ? Rtmp1 : Rtmp2;
  }
  __ lgr_if_needed(Rpre_save, Rpre_val);

  // Push frame to protect top frame with return pc and spilled register values.
  __ save_return_pc();
  __ push_frame_abi160(0); // Will use Z_R0 as tmp.

  // Rpre_val may be destroyed by push_frame().
  __ call_VM_leaf(CAST_FROM_FN_PTR(address, SharedRuntime::g1_wb_pre), Rpre_save, Z_thread);

  __ pop_frame();
  __ restore_return_pc();

  // Restore spilled values.
  if (Robj != noreg && Robj->is_volatile()) {
    __ z_lg(Robj, Robj->encoding()*BytesPerWord, Z_SP);
  }
  if (Roff != noreg && Roff->is_volatile()) {
    __ z_lg(Roff, Roff->encoding()*BytesPerWord, Z_SP);
  }
  if (Rval != noreg && Rval->is_volatile()) {
    __ z_lg(Rval, Rval->encoding()*BytesPerWord, Z_SP);
  }
  if (pre_val_needed && Rpre_val->is_volatile()) {
    __ lgr_if_needed(Rpre_val, Rpre_save);
  }

  __ bind(filtered);
  BLOCK_COMMENT("} g1_write_barrier_pre");
}

void G1BarrierSetAssembler::g1_write_barrier_post(MacroAssembler* masm, DecoratorSet decorators, Register Rstore_addr, Register Rnew_val,
                                                  Register Rtmp1, Register Rtmp2, Register Rtmp3) {
  bool not_null = (decorators & OOP_NOT_NULL) != 0;

  assert_different_registers(Rstore_addr, Rnew_val, Rtmp1, Rtmp2); // Most probably, Rnew_val == Rtmp3.

  Label callRuntime, filtered;

  CardTableBarrierSet* ct = barrier_set_cast<CardTableBarrierSet>(Universe::heap()->barrier_set());
  assert(sizeof(*ct->card_table()->byte_map_base()) == sizeof(jbyte), "adjust this code");

  BLOCK_COMMENT("g1_write_barrier_post {");

  // Does store cross heap regions?
  // It does if the two addresses specify different grain addresses.
  if (G1RSBarrierRegionFilter) {
    if (VM_Version::has_DistinctOpnds()) {
      __ z_xgrk(Rtmp1, Rstore_addr, Rnew_val);
    } else {
      __ z_lgr(Rtmp1, Rstore_addr);
      __ z_xgr(Rtmp1, Rnew_val);
    }
    __ z_srag(Rtmp1, Rtmp1, HeapRegion::LogOfHRGrainBytes);
    __ z_bre(filtered);
  }

  // Crosses regions, storing NULL?
  if (not_null) {
#ifdef ASSERT
    __ z_ltgr(Rnew_val, Rnew_val);
    __ asm_assert_ne("null oop not allowed (G1 post)", 0x322); // Checked by caller.
#endif
  } else {
    __ z_ltgr(Rnew_val, Rnew_val);
    __ z_bre(filtered);
  }

  Rnew_val = noreg; // end of lifetime

  // Storing region crossing non-NULL, is card already dirty?
  assert(sizeof(*ct->card_table()->byte_map_base()) == sizeof(jbyte), "adjust this code");
  assert_different_registers(Rtmp1, Rtmp2, Rtmp3);
  // Make sure not to use Z_R0 for any of these registers.
  Register Rcard_addr = (Rtmp1 != Z_R0_scratch) ? Rtmp1 : Rtmp3;
  Register Rbase      = (Rtmp2 != Z_R0_scratch) ? Rtmp2 : Rtmp3;

  // calculate address of card
  __ load_const_optimized(Rbase, (address)ct->card_table()->byte_map_base());      // Card table base.
  __ z_srlg(Rcard_addr, Rstore_addr, CardTable::card_shift);         // Index into card table.
  __ z_algr(Rcard_addr, Rbase);                                      // Explicit calculation needed for cli.
  Rbase = noreg; // end of lifetime

  // Filter young.
  assert((unsigned int)G1CardTable::g1_young_card_val() <= 255, "otherwise check this code");
  __ z_cli(0, Rcard_addr, G1CardTable::g1_young_card_val());
  __ z_bre(filtered);

  // Check the card value. If dirty, we're done.
  // This also avoids false sharing of the (already dirty) card.
  __ z_sync(); // Required to support concurrent cleaning.
  assert((unsigned int)G1CardTable::dirty_card_val() <= 255, "otherwise check this code");
  __ z_cli(0, Rcard_addr, G1CardTable::dirty_card_val()); // Reload after membar.
  __ z_bre(filtered);

  // Storing a region crossing, non-NULL oop, card is clean.
  // Dirty card and log.
  __ z_mvi(0, Rcard_addr, G1CardTable::dirty_card_val());

  Register Rcard_addr_x = Rcard_addr;
  Register Rqueue_index = (Rtmp2 != Z_R0_scratch) ? Rtmp2 : Rtmp1;
  Register Rqueue_buf   = (Rtmp3 != Z_R0_scratch) ? Rtmp3 : Rtmp1;
  const int qidx_off    = in_bytes(G1ThreadLocalData::dirty_card_queue_index_offset());
  const int qbuf_off    = in_bytes(G1ThreadLocalData::dirty_card_queue_buffer_offset());
  if ((Rcard_addr == Rqueue_buf) || (Rcard_addr == Rqueue_index)) {
    Rcard_addr_x = Z_R0_scratch;  // Register shortage. We have to use Z_R0.
  }
  __ lgr_if_needed(Rcard_addr_x, Rcard_addr);

  __ load_and_test_long(Rqueue_index, Address(Z_thread, qidx_off));
  __ z_bre(callRuntime); // Index == 0 then jump to runtime.

  __ z_lg(Rqueue_buf, qbuf_off, Z_thread);

  __ add2reg(Rqueue_index, -wordSize); // Decrement index.
  __ z_stg(Rqueue_index, qidx_off, Z_thread);

  __ z_stg(Rcard_addr_x, 0, Rqueue_index, Rqueue_buf); // Store card.
  __ z_bru(filtered);

  __ bind(callRuntime);

  // TODO: do we need a frame? Introduced to be on the safe side.
  bool needs_frame = true;
  __ lgr_if_needed(Rcard_addr, Rcard_addr_x); // copy back asap. push_frame will destroy Z_R0_scratch!

  // VM call need frame to access(write) O register.
  if (needs_frame) {
    __ save_return_pc();
    __ push_frame_abi160(0); // Will use Z_R0 as tmp on old CPUs.
  }

  // Save the live input values.
  __ call_VM_leaf(CAST_FROM_FN_PTR(address, SharedRuntime::g1_wb_post), Rcard_addr, Z_thread);

  if (needs_frame) {
    __ pop_frame();
    __ restore_return_pc();
  }

  __ bind(filtered);

  BLOCK_COMMENT("} g1_write_barrier_post");
}

void G1BarrierSetAssembler::oop_store_at(MacroAssembler* masm, DecoratorSet decorators, BasicType type,
                                         const Address& dst, Register val, Register tmp1, Register tmp2, Register tmp3) {
  bool on_array = (decorators & IN_HEAP_ARRAY) != 0;
  bool on_anonymous = (decorators & ON_UNKNOWN_OOP_REF) != 0;
  bool precise = on_array || on_anonymous;
  // Load and record the previous value.
  g1_write_barrier_pre(masm, decorators, &dst, tmp3, val, tmp1, tmp2, false);

  BarrierSetAssembler::store_at(masm, decorators, type, dst, val, tmp1, tmp2, tmp3);

  // No need for post barrier if storing NULL
  if (val != noreg) {
    const Register base = dst.base(),
                   idx  = dst.index();
    const intptr_t disp = dst.disp();
    if (precise && (disp != 0 || idx != noreg)) {
      __ add2reg_with_index(base, disp, idx, base);
    }
    g1_write_barrier_post(masm, decorators, base, val, tmp1, tmp2, tmp3);
  }
}

void G1BarrierSetAssembler::resolve_jobject(MacroAssembler* masm, Register value, Register tmp1, Register tmp2) {
  NearLabel Ldone, Lnot_weak;
  __ z_ltgr(tmp1, value);
  __ z_bre(Ldone);          // Use NULL result as-is.

  __ z_nill(value, ~JNIHandles::weak_tag_mask);
  __ z_lg(value, 0, value); // Resolve (untagged) jobject.

  __ z_tmll(tmp1, JNIHandles::weak_tag_mask); // Test for jweak tag.
  __ z_braz(Lnot_weak);
  __ verify_oop(value);
  DecoratorSet decorators = IN_ROOT | ON_PHANTOM_OOP_REF;
  g1_write_barrier_pre(masm, decorators, (const Address*)NULL, value, noreg, tmp1, tmp2, true);
  __ bind(Lnot_weak);
  __ verify_oop(value);
  __ bind(Ldone);
}

#undef __
