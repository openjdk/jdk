/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2018, 2024 SAP SE. All rights reserved.
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

#include "asm/macroAssembler.inline.hpp"
#include "gc/g1/g1BarrierSet.hpp"
#include "gc/g1/g1BarrierSetAssembler.hpp"
#include "gc/g1/g1BarrierSetRuntime.hpp"
#include "gc/g1/g1CardTable.hpp"
#include "gc/g1/g1DirtyCardQueue.hpp"
#include "gc/g1/g1HeapRegion.hpp"
#include "gc/g1/g1SATBMarkQueueSet.hpp"
#include "gc/g1/g1ThreadLocalData.hpp"
#include "interpreter/interp_masm.hpp"
#include "registerSaver_s390.hpp"
#include "runtime/jniHandles.hpp"
#include "runtime/sharedRuntime.hpp"
#include "utilities/macros.hpp"
#ifdef COMPILER1
#include "c1/c1_LIRAssembler.hpp"
#include "c1/c1_MacroAssembler.hpp"
#include "gc/g1/c1/g1BarrierSetC1.hpp"
#endif // COMPILER1
#ifdef COMPILER2
#include "gc/g1/c2/g1BarrierSetC2.hpp"
#endif // COMPILER2

#define __ masm->

#define BLOCK_COMMENT(str) __ block_comment(str)

static void generate_pre_barrier_fast_path(MacroAssembler* masm,
                                           const Register thread,
                                           const Register tmp1) {
  Address in_progress(thread, in_bytes(G1ThreadLocalData::satb_mark_queue_active_offset()));
  // Is marking active?
  if (in_bytes(SATBMarkQueue::byte_width_of_active()) == 4) {
    __ load_and_test_int(tmp1, in_progress);
  } else {
    assert(in_bytes(SATBMarkQueue::byte_width_of_active()) == 1, "Assumption");
    __ load_and_test_byte(tmp1, in_progress);
  }
}

static void generate_queue_test_and_insertion(MacroAssembler* masm, ByteSize index_offset, ByteSize buffer_offset, Label& runtime,
                                              const Register Z_thread, const Register value, const Register temp) {
  BLOCK_COMMENT("generate_queue_test_and_insertion {");

  assert_different_registers(temp, value);
  // Can we store a value in the given thread's buffer?
  // (The index field is typed as size_t.)

  __ load_and_test_long(temp, Address(Z_thread, in_bytes(index_offset))); // temp := *(index address)
  __ branch_optimized(Assembler::bcondEqual, runtime);                    // jump to runtime if index == 0 (full buffer)

  // The buffer is not full, store value into it.
  __ add2reg(temp, -wordSize);                                            // temp := next index
  __ z_stg(temp, in_bytes(index_offset), Z_thread);                       // *(index address) := next index

  __ z_ag(temp, Address(Z_thread, in_bytes(buffer_offset)));              // temp := buffer address + next index
  __ z_stg(value, 0, temp);                                               // *(buffer address + next index) := value
  BLOCK_COMMENT("} generate_queue_test_and_insertion");
}

void G1BarrierSetAssembler::gen_write_ref_array_pre_barrier(MacroAssembler* masm, DecoratorSet decorators,
                                                            Register addr, Register count) {
  bool dest_uninitialized = (decorators & IS_DEST_UNINITIALIZED) != 0;

  // With G1, don't generate the call if we statically know that the target is uninitialized.
  if (!dest_uninitialized) {
    // Is marking active?
    Label filtered;
    assert_different_registers(addr,  Z_R0_scratch);  // would be destroyed by push_frame()
    assert_different_registers(count, Z_R0_scratch);  // would be destroyed by push_frame()
    Register Rtmp1 = Z_R0_scratch;

    generate_pre_barrier_fast_path(masm, Z_thread, Rtmp1);
    __ z_bre(filtered); // Activity indicator is zero, so there is no marking going on currently.

    RegisterSaver::save_live_registers(masm, RegisterSaver::arg_registers); // Creates frame.

    if (UseCompressedOops) {
      __ call_VM_leaf(CAST_FROM_FN_PTR(address, G1BarrierSetRuntime::write_ref_array_pre_narrow_oop_entry), addr, count);
    } else {
      __ call_VM_leaf(CAST_FROM_FN_PTR(address, G1BarrierSetRuntime::write_ref_array_pre_oop_entry), addr, count);
    }

    RegisterSaver::restore_live_registers(masm, RegisterSaver::arg_registers);

    __ bind(filtered);
  }
}

void G1BarrierSetAssembler::gen_write_ref_array_post_barrier(MacroAssembler* masm, DecoratorSet decorators,
                                                             Register addr, Register count, bool do_return) {
  address entry_point = CAST_FROM_FN_PTR(address, G1BarrierSetRuntime::write_ref_array_post_entry);
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

#if defined(COMPILER2)

#undef __
#define __ masm->

static void generate_c2_barrier_runtime_call(MacroAssembler* masm, G1BarrierStubC2* stub, const Register pre_val, const address runtime_path) {
  BLOCK_COMMENT("generate_c2_barrier_runtime_call {");
  SaveLiveRegisters save_registers(masm, stub);
  __ call_VM_leaf(runtime_path, pre_val, Z_thread);
  BLOCK_COMMENT("} generate_c2_barrier_runtime_call");
}

void G1BarrierSetAssembler::g1_write_barrier_pre_c2(MacroAssembler* masm,
                                                    Register obj,
                                                    Register pre_val,
                                                    Register thread,
                                                    Register tmp1,
                                                    G1PreBarrierStubC2* stub) {

  BLOCK_COMMENT("g1_write_barrier_pre_c2 {");

  assert(thread == Z_thread, "must be");
  assert_different_registers(obj, pre_val, tmp1);
  assert(pre_val != noreg && tmp1 != noreg, "expecting a register");

  stub->initialize_registers(obj, pre_val, thread, tmp1, noreg);

  generate_pre_barrier_fast_path(masm, thread, tmp1);
  __ branch_optimized(Assembler::bcondNotEqual, *stub->entry()); // Activity indicator is zero, so there is no marking going on currently.

  __ bind(*stub->continuation());

  BLOCK_COMMENT("} g1_write_barrier_pre_c2");
}

void G1BarrierSetAssembler::generate_c2_pre_barrier_stub(MacroAssembler* masm,
                                                         G1PreBarrierStubC2* stub) const {

  BLOCK_COMMENT("generate_c2_pre_barrier_stub {");

  Assembler::InlineSkippedInstructionsCounter skip_counter(masm);

  Label runtime;
  Register obj     = stub->obj();
  Register pre_val = stub->pre_val();
  Register thread  = stub->thread();
  Register tmp1    = stub->tmp1();

  __ bind(*stub->entry());

  BLOCK_COMMENT("generate_pre_val_not_null_test {");
  if (obj != noreg) {
    __ load_heap_oop(pre_val, Address(obj), noreg, noreg, AS_RAW);
  }
  __ z_ltgr(pre_val, pre_val);
  __ branch_optimized(Assembler::bcondEqual, *stub->continuation());
  BLOCK_COMMENT("} generate_pre_val_not_null_test");

  generate_queue_test_and_insertion(masm,
                                    G1ThreadLocalData::satb_mark_queue_index_offset(),
                                    G1ThreadLocalData::satb_mark_queue_buffer_offset(),
                                    runtime,
                                    Z_thread, pre_val, tmp1);

  __ branch_optimized(Assembler::bcondAlways, *stub->continuation());

  __ bind(runtime);

  generate_c2_barrier_runtime_call(masm, stub, pre_val, CAST_FROM_FN_PTR(address, G1BarrierSetRuntime::write_ref_field_pre_entry));

  __ branch_optimized(Assembler::bcondAlways, *stub->continuation());

  BLOCK_COMMENT("} generate_c2_pre_barrier_stub");
}

void G1BarrierSetAssembler::g1_write_barrier_post_c2(MacroAssembler* masm,
                                                     Register store_addr,
                                                     Register new_val,
                                                     Register thread,
                                                     Register tmp1,
                                                     Register tmp2,
                                                     G1PostBarrierStubC2* stub) {
  BLOCK_COMMENT("g1_write_barrier_post_c2 {");

  assert(thread == Z_thread, "must be");
  assert_different_registers(store_addr, new_val, thread, tmp1, tmp2, Z_R1_scratch);

  assert(store_addr != noreg && new_val != noreg && tmp1 != noreg && tmp2 != noreg, "expecting a register");

  stub->initialize_registers(thread, tmp1, tmp2);

  BLOCK_COMMENT("generate_region_crossing_test {");
  if (VM_Version::has_DistinctOpnds()) {
    __ z_xgrk(tmp1, store_addr, new_val);
  } else {
    __ z_lgr(tmp1, store_addr);
    __ z_xgr(tmp1, new_val);
  }
  __ z_srag(tmp1, tmp1, G1HeapRegion::LogOfHRGrainBytes);
  __ branch_optimized(Assembler::bcondEqual, *stub->continuation());
  BLOCK_COMMENT("} generate_region_crossing_test");

  // crosses regions, storing null?
  if ((stub->barrier_data() & G1C2BarrierPostNotNull) == 0) {
    __ z_ltgr(new_val, new_val);
    __ branch_optimized(Assembler::bcondEqual, *stub->continuation());
  }

  BLOCK_COMMENT("generate_card_young_test {");
  CardTableBarrierSet* ct = barrier_set_cast<CardTableBarrierSet>(BarrierSet::barrier_set());
  // calculate address of card
  __ load_const_optimized(tmp2, (address)ct->card_table()->byte_map_base());      // Card table base.
  __ z_srlg(tmp1, store_addr, CardTable::card_shift());         // Index into card table.
  __ z_algr(tmp1, tmp2);                                      // Explicit calculation needed for cli.

  // Filter young.
  __ z_cli(0, tmp1, G1CardTable::g1_young_card_val());

  BLOCK_COMMENT("} generate_card_young_test");

  // From here on, tmp1 holds the card address.
  __ branch_optimized(Assembler::bcondNotEqual, *stub->entry());

  __ bind(*stub->continuation());

  BLOCK_COMMENT("} g1_write_barrier_post_c2");
}

void G1BarrierSetAssembler::generate_c2_post_barrier_stub(MacroAssembler* masm,
                                                          G1PostBarrierStubC2* stub) const {

  BLOCK_COMMENT("generate_c2_post_barrier_stub {");

  Assembler::InlineSkippedInstructionsCounter skip_counter(masm);
  Label runtime;

  Register thread     = stub->thread();
  Register tmp1       = stub->tmp1(); // tmp1 holds the card address.
  Register tmp2       = stub->tmp2();
  Register Rcard_addr = tmp1;

  __ bind(*stub->entry());

  BLOCK_COMMENT("generate_card_clean_test {");
  __ z_sync(); // Required to support concurrent cleaning.
  __ z_cli(0, Rcard_addr, 0); // Reload after membar.
  __ branch_optimized(Assembler::bcondEqual, *stub->continuation());
  BLOCK_COMMENT("} generate_card_clean_test");

  BLOCK_COMMENT("generate_dirty_card {");
  // Storing a region crossing, non-null oop, card is clean.
  // Dirty card and log.
  STATIC_ASSERT(CardTable::dirty_card_val() == 0);
  __ z_mvi(0, Rcard_addr, CardTable::dirty_card_val());
  BLOCK_COMMENT("} generate_dirty_card");

  generate_queue_test_and_insertion(masm,
                                    G1ThreadLocalData::dirty_card_queue_index_offset(),
                                    G1ThreadLocalData::dirty_card_queue_buffer_offset(),
                                    runtime,
                                    Z_thread, tmp1, tmp2);

  __ branch_optimized(Assembler::bcondAlways, *stub->continuation());

  __ bind(runtime);

  generate_c2_barrier_runtime_call(masm, stub, tmp1, CAST_FROM_FN_PTR(address, G1BarrierSetRuntime::write_ref_field_post_entry));

  __ branch_optimized(Assembler::bcondAlways, *stub->continuation());

  BLOCK_COMMENT("} generate_c2_post_barrier_stub");
}

#endif //COMPILER2

void G1BarrierSetAssembler::load_at(MacroAssembler* masm, DecoratorSet decorators, BasicType type,
                                    const Address& src, Register dst, Register tmp1, Register tmp2, Label *L_handle_null) {
  bool on_oop = is_reference_type(type);
  bool on_weak = (decorators & ON_WEAK_OOP_REF) != 0;
  bool on_phantom = (decorators & ON_PHANTOM_OOP_REF) != 0;
  bool on_reference = on_weak || on_phantom;
  Label done;
  if (on_oop && on_reference && L_handle_null == nullptr) { L_handle_null = &done; }
  ModRefBarrierSetAssembler::load_at(masm, decorators, type, src, dst, tmp1, tmp2, L_handle_null);
  if (on_oop && on_reference) {
    // Generate the G1 pre-barrier code to log the value of
    // the referent field in an SATB buffer.
    g1_write_barrier_pre(masm, decorators | IS_NOT_NULL,
                         nullptr /* obj */,
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

  bool not_null  = (decorators & IS_NOT_NULL) != 0,
       preloaded = obj == nullptr;

  const Register Robj = obj ? obj->base() : noreg,
                 Roff = obj ? obj->index() : noreg;
  assert_different_registers(Rtmp1, Rtmp2, Z_R0_scratch); // None of the Rtmp<i> must be Z_R0!!
  assert_different_registers(Robj, Z_R0_scratch);         // Used for addressing. Furthermore, push_frame destroys Z_R0!!
  assert_different_registers(Rval, Z_R0_scratch);         // push_frame destroys Z_R0!!

  Label callRuntime, filtered;

  BLOCK_COMMENT("g1_write_barrier_pre {");

  generate_pre_barrier_fast_path(masm, Z_thread, Rtmp1);
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

  // Is the previous value null?
  // If so, we don't need to record it and we're done.
  // Note: pre_val is loaded, decompressed and stored (directly or via runtime call).
  //       Register contents is preserved across runtime call if caller requests to do so.
  if (preloaded && not_null) {
#ifdef ASSERT
    __ z_ltgr(Rpre_val, Rpre_val);
    __ asm_assert(Assembler::bcondNotZero, "null oop not allowed (G1 pre)", 0x321); // Checked by caller.
#endif
  } else {
    __ z_ltgr(Rpre_val, Rpre_val);
    __ z_bre(filtered); // previous value is null, so we don't need to record it.
  }

  // Decode the oop now. We know it's not null.
  if (Robj != noreg && UseCompressedOops) {
    __ oop_decoder(Rpre_val, Rpre_val, /*maybenullptr=*/false);
  }

  // OK, it's not filtered, so we'll need to call enqueue.

  // We can store the original value in the thread's buffer
  // only if index > 0. Otherwise, we need runtime to handle.
  // (The index field is typed as size_t.)

  generate_queue_test_and_insertion(masm,
                                    G1ThreadLocalData::satb_mark_queue_index_offset(),
                                    G1ThreadLocalData::satb_mark_queue_buffer_offset(),
                                    callRuntime,
                                    Z_thread, Rpre_val, Rtmp2);
  __ z_bru(filtered);  // We are done.

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
  __ call_VM_leaf(CAST_FROM_FN_PTR(address, G1BarrierSetRuntime::write_ref_field_pre_entry), Rpre_save, Z_thread);

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
  bool not_null = (decorators & IS_NOT_NULL) != 0;

  assert_different_registers(Rstore_addr, Rnew_val, Rtmp1, Rtmp2); // Most probably, Rnew_val == Rtmp3.

  Label callRuntime, filtered;

  CardTableBarrierSet* ct = barrier_set_cast<CardTableBarrierSet>(BarrierSet::barrier_set());

  BLOCK_COMMENT("g1_write_barrier_post {");

  // Does store cross heap regions?
  // It does if the two addresses specify different grain addresses.
  if (VM_Version::has_DistinctOpnds()) {
    __ z_xgrk(Rtmp1, Rstore_addr, Rnew_val);
  } else {
    __ z_lgr(Rtmp1, Rstore_addr);
    __ z_xgr(Rtmp1, Rnew_val);
  }
  __ z_srag(Rtmp1, Rtmp1, G1HeapRegion::LogOfHRGrainBytes);
  __ z_bre(filtered);

  // Crosses regions, storing null?
  if (not_null) {
#ifdef ASSERT
    __ z_ltgr(Rnew_val, Rnew_val);
    __ asm_assert(Assembler::bcondNotZero, "null oop not allowed (G1 post)", 0x322); // Checked by caller.
#endif
  } else {
    __ z_ltgr(Rnew_val, Rnew_val);
    __ z_bre(filtered);
  }

  Rnew_val = noreg; // end of lifetime

  // Storing region crossing non-null, is card already dirty?
  assert_different_registers(Rtmp1, Rtmp2, Rtmp3);
  // Make sure not to use Z_R0 for any of these registers.
  Register Rcard_addr = (Rtmp1 != Z_R0_scratch) ? Rtmp1 : Rtmp3;
  Register Rbase      = (Rtmp2 != Z_R0_scratch) ? Rtmp2 : Rtmp3;

  // calculate address of card
  __ load_const_optimized(Rbase, (address)ct->card_table()->byte_map_base());      // Card table base.
  __ z_srlg(Rcard_addr, Rstore_addr, CardTable::card_shift());         // Index into card table.
  __ z_algr(Rcard_addr, Rbase);                                      // Explicit calculation needed for cli.
  Rbase = noreg; // end of lifetime

  // Filter young.
  __ z_cli(0, Rcard_addr, G1CardTable::g1_young_card_val());
  __ z_bre(filtered);

  // Check the card value. If dirty, we're done.
  // This also avoids false sharing of the (already dirty) card.
  __ z_sync(); // Required to support concurrent cleaning.
  __ z_cli(0, Rcard_addr, G1CardTable::dirty_card_val()); // Reload after membar.
  __ z_bre(filtered);

  // Storing a region crossing, non-null oop, card is clean.
  // Dirty card and log.
  __ z_mvi(0, Rcard_addr, G1CardTable::dirty_card_val());

  Register Rcard_addr_x = Rcard_addr;
  Register Rqueue_index = (Rtmp2 != Z_R0_scratch) ? Rtmp2 : Rtmp1;
  if (Rcard_addr == Rqueue_index) {
    Rcard_addr_x = Z_R0_scratch;  // Register shortage. We have to use Z_R0.
  }
  __ lgr_if_needed(Rcard_addr_x, Rcard_addr);

  generate_queue_test_and_insertion(masm,
                                    G1ThreadLocalData::dirty_card_queue_index_offset(),
                                    G1ThreadLocalData::dirty_card_queue_buffer_offset(),
                                    callRuntime,
                                    Z_thread, Rcard_addr_x, Rqueue_index);
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
  __ call_VM_leaf(CAST_FROM_FN_PTR(address, G1BarrierSetRuntime::write_ref_field_post_entry), Rcard_addr, Z_thread);

  if (needs_frame) {
    __ pop_frame();
    __ restore_return_pc();
  }

  __ bind(filtered);

  BLOCK_COMMENT("} g1_write_barrier_post");
}

void G1BarrierSetAssembler::oop_store_at(MacroAssembler* masm, DecoratorSet decorators, BasicType type,
                                         const Address& dst, Register val, Register tmp1, Register tmp2, Register tmp3) {
  bool is_array = (decorators & IS_ARRAY) != 0;
  bool on_anonymous = (decorators & ON_UNKNOWN_OOP_REF) != 0;
  bool precise = is_array || on_anonymous;
  // Load and record the previous value.
  g1_write_barrier_pre(masm, decorators, &dst, tmp3, val, tmp1, tmp2, false);

  BarrierSetAssembler::store_at(masm, decorators, type, dst, val, tmp1, tmp2, tmp3);

  // No need for post barrier if storing null
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
  __ z_bre(Ldone);          // Use null result as-is.

  __ z_nill(value, ~JNIHandles::tag_mask);
  __ z_lg(value, 0, value); // Resolve (untagged) jobject.

  __ z_tmll(tmp1, JNIHandles::TypeTag::weak_global); // Test for jweak tag.
  __ z_braz(Lnot_weak);
  __ verify_oop(value, FILE_AND_LINE);
  DecoratorSet decorators = IN_NATIVE | ON_PHANTOM_OOP_REF;
  g1_write_barrier_pre(masm, decorators, (const Address*)nullptr, value, noreg, tmp1, tmp2, true);
  __ bind(Lnot_weak);
  __ verify_oop(value, FILE_AND_LINE);
  __ bind(Ldone);
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
  ce->check_reserved_argument_area(16); // RT stub needs 2 spill slots.
  assert(stub->pre_val()->is_register(), "Precondition.");

  Register pre_val_reg = stub->pre_val()->as_register();

  if (stub->do_load()) {
    ce->mem2reg(stub->addr(), stub->pre_val(), T_OBJECT, stub->patch_code(), stub->info(), false /*wide*/);
  }

  __ z_ltgr(Z_R1_scratch, pre_val_reg); // Pass oop in Z_R1_scratch to Runtime1::g1_pre_barrier_slow_id.
  __ branch_optimized(Assembler::bcondZero, *stub->continuation());
  ce->emit_call_c(bs->pre_barrier_c1_runtime_code_blob()->code_begin());
  __ branch_optimized(Assembler::bcondAlways, *stub->continuation());
}

void G1BarrierSetAssembler::gen_post_barrier_stub(LIR_Assembler* ce, G1PostBarrierStub* stub) {
  G1BarrierSetC1* bs = (G1BarrierSetC1*)BarrierSet::barrier_set()->barrier_set_c1();
  __ bind(*stub->entry());
  ce->check_reserved_argument_area(16); // RT stub needs 2 spill slots.
  assert(stub->addr()->is_register(), "Precondition.");
  assert(stub->new_val()->is_register(), "Precondition.");
  Register new_val_reg = stub->new_val()->as_register();
  __ z_ltgr(new_val_reg, new_val_reg);
  __ branch_optimized(Assembler::bcondZero, *stub->continuation());
  __ z_lgr(Z_R1_scratch, stub->addr()->as_pointer_register());
  ce->emit_call_c(bs->post_barrier_c1_runtime_code_blob()->code_begin());
  __ branch_optimized(Assembler::bcondAlways, *stub->continuation());
}

#undef __

#define __ sasm->

static OopMap* save_volatile_registers(StubAssembler* sasm, Register return_pc = Z_R14) {
  __ block_comment("save_volatile_registers");
  RegisterSaver::RegisterSet reg_set = RegisterSaver::all_volatile_registers;
  int frame_size_in_slots = RegisterSaver::live_reg_frame_size(reg_set) / VMRegImpl::stack_slot_size;
  sasm->set_frame_size(frame_size_in_slots / VMRegImpl::slots_per_word);
  return RegisterSaver::save_live_registers(sasm, reg_set, return_pc);
}

static void restore_volatile_registers(StubAssembler* sasm) {
  __ block_comment("restore_volatile_registers");
  RegisterSaver::RegisterSet reg_set = RegisterSaver::all_volatile_registers;
  RegisterSaver::restore_live_registers(sasm, reg_set);
}

void G1BarrierSetAssembler::generate_c1_pre_barrier_runtime_stub(StubAssembler* sasm) {
  // Z_R1_scratch: previous value of memory

  BarrierSet* bs = BarrierSet::barrier_set();
  __ set_info("g1_pre_barrier_slow_id", false);

  Register pre_val = Z_R1_scratch;
  Register tmp  = Z_R6; // Must be non-volatile because it is used to save pre_val.
  Register tmp2 = Z_R7;

  Label refill, restart, marking_not_active;
  int satb_q_active_byte_offset = in_bytes(G1ThreadLocalData::satb_mark_queue_active_offset());
  int satb_q_index_byte_offset = in_bytes(G1ThreadLocalData::satb_mark_queue_index_offset());
  int satb_q_buf_byte_offset = in_bytes(G1ThreadLocalData::satb_mark_queue_buffer_offset());

  // Save tmp registers (see assertion in G1PreBarrierStub::emit_code()).
  __ z_stg(tmp,  0*BytesPerWord + FrameMap::first_available_sp_in_frame, Z_SP);
  __ z_stg(tmp2, 1*BytesPerWord + FrameMap::first_available_sp_in_frame, Z_SP);

  // Is marking still active?
  if (in_bytes(SATBMarkQueue::byte_width_of_active()) == 4) {
    __ load_and_test_int(tmp, Address(Z_thread, satb_q_active_byte_offset));
  } else {
    assert(in_bytes(SATBMarkQueue::byte_width_of_active()) == 1, "Assumption");
    __ load_and_test_byte(tmp, Address(Z_thread, satb_q_active_byte_offset));
  }
  __ z_bre(marking_not_active); // Activity indicator is zero, so there is no marking going on currently.

  __ bind(restart);
  // Load the index into the SATB buffer. SATBMarkQueue::_index is a
  // size_t so ld_ptr is appropriate.
  __ z_ltg(tmp, satb_q_index_byte_offset, Z_R0, Z_thread);

  // index == 0?
  __ z_brz(refill);

  __ z_lg(tmp2, satb_q_buf_byte_offset, Z_thread);
  __ add2reg(tmp, -oopSize);

  __ z_stg(pre_val, 0, tmp, tmp2); // [_buf + index] := <address_of_card>
  __ z_stg(tmp, satb_q_index_byte_offset, Z_thread);

  __ bind(marking_not_active);
  // Restore tmp registers (see assertion in G1PreBarrierStub::emit_code()).
  __ z_lg(tmp,  0*BytesPerWord + FrameMap::first_available_sp_in_frame, Z_SP);
  __ z_lg(tmp2, 1*BytesPerWord + FrameMap::first_available_sp_in_frame, Z_SP);
  __ z_br(Z_R14);

  __ bind(refill);
  save_volatile_registers(sasm);
  __ z_lgr(tmp, pre_val); // save pre_val
  __ call_VM_leaf(CAST_FROM_FN_PTR(address, G1SATBMarkQueueSet::handle_zero_index_for_thread),
                  Z_thread);
  __ z_lgr(pre_val, tmp); // restore pre_val
  restore_volatile_registers(sasm);
  __ z_bru(restart);
}

void G1BarrierSetAssembler::generate_c1_post_barrier_runtime_stub(StubAssembler* sasm) {
  // Z_R1_scratch: oop address, address of updated memory slot

  BarrierSet* bs = BarrierSet::barrier_set();
  __ set_info("g1_post_barrier_slow_id", false);

  Register addr_oop  = Z_R1_scratch;
  Register addr_card = Z_R1_scratch;
  Register r1        = Z_R6; // Must be saved/restored.
  Register r2        = Z_R7; // Must be saved/restored.
  Register cardtable = r1;   // Must be non-volatile, because it is used to save addr_card.
  CardTableBarrierSet* ctbs = barrier_set_cast<CardTableBarrierSet>(bs);
  CardTable* ct = ctbs->card_table();
  CardTable::CardValue* byte_map_base = ct->byte_map_base();

  // Save registers used below (see assertion in G1PreBarrierStub::emit_code()).
  __ z_stg(r1, 0*BytesPerWord + FrameMap::first_available_sp_in_frame, Z_SP);

  Label not_already_dirty, restart, refill, young_card;

  // Calculate address of card corresponding to the updated oop slot.
  AddressLiteral rs(byte_map_base);
  __ z_srlg(addr_card, addr_oop, CardTable::card_shift());
  addr_oop = noreg; // dead now
  __ load_const_optimized(cardtable, rs); // cardtable := <card table base>
  __ z_agr(addr_card, cardtable); // addr_card := addr_oop>>card_shift + cardtable

  __ z_cli(0, addr_card, (int)G1CardTable::g1_young_card_val());
  __ z_bre(young_card);

  __ z_sync(); // Required to support concurrent cleaning.

  __ z_cli(0, addr_card, (int)CardTable::dirty_card_val());
  __ z_brne(not_already_dirty);

  __ bind(young_card);
  // We didn't take the branch, so we're already dirty: restore
  // used registers and return.
  __ z_lg(r1, 0*BytesPerWord + FrameMap::first_available_sp_in_frame, Z_SP);
  __ z_br(Z_R14);

  // Not dirty.
  __ bind(not_already_dirty);

  // First, dirty it: [addr_card] := 0
  __ z_mvi(0, addr_card, CardTable::dirty_card_val());

  Register idx = cardtable; // Must be non-volatile, because it is used to save addr_card.
  Register buf = r2;
  cardtable = noreg; // now dead

  // Save registers used below (see assertion in G1PreBarrierStub::emit_code()).
  __ z_stg(r2, 1*BytesPerWord + FrameMap::first_available_sp_in_frame, Z_SP);

  ByteSize dirty_card_q_index_byte_offset = G1ThreadLocalData::dirty_card_queue_index_offset();
  ByteSize dirty_card_q_buf_byte_offset = G1ThreadLocalData::dirty_card_queue_buffer_offset();

  __ bind(restart);

  // Get the index into the update buffer. G1DirtyCardQueue::_index is
  // a size_t so z_ltg is appropriate here.
  __ z_ltg(idx, Address(Z_thread, dirty_card_q_index_byte_offset));

  // index == 0?
  __ z_brz(refill);

  __ z_lg(buf, Address(Z_thread, dirty_card_q_buf_byte_offset));
  __ add2reg(idx, -oopSize);

  __ z_stg(addr_card, 0, idx, buf); // [_buf + index] := <address_of_card>
  __ z_stg(idx, Address(Z_thread, dirty_card_q_index_byte_offset));
  // Restore killed registers and return.
  __ z_lg(r1, 0*BytesPerWord + FrameMap::first_available_sp_in_frame, Z_SP);
  __ z_lg(r2, 1*BytesPerWord + FrameMap::first_available_sp_in_frame, Z_SP);
  __ z_br(Z_R14);

  __ bind(refill);
  save_volatile_registers(sasm);
  __ z_lgr(idx, addr_card); // Save addr_card, tmp3 must be non-volatile.
  __ call_VM_leaf(CAST_FROM_FN_PTR(address, G1DirtyCardQueueSet::handle_zero_index_for_thread),
                                   Z_thread);
  __ z_lgr(addr_card, idx);
  restore_volatile_registers(sasm); // Restore addr_card.
  __ z_bru(restart);
}

#undef __

#endif // COMPILER1
