/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/g1/g1HeapRegion.hpp"
#include "gc/g1/g1ThreadLocalData.hpp"
#include "interpreter/interp_masm.hpp"
#include "runtime/javaThread.hpp"
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

#ifdef PRODUCT
#define BLOCK_COMMENT(str) /* nothing */
#else
#define BLOCK_COMMENT(str) __ block_comment(str)
#endif

#define BIND(label) bind(label); BLOCK_COMMENT(#label ":")

void G1BarrierSetAssembler::gen_write_ref_array_pre_barrier(MacroAssembler* masm, DecoratorSet decorators,
                                                            Register addr, Register count, int callee_saved_regs) {
  bool dest_uninitialized = (decorators & IS_DEST_UNINITIALIZED) != 0;
  if (!dest_uninitialized) {
    assert( addr->encoding() < callee_saved_regs, "addr must be saved");
    assert(count->encoding() < callee_saved_regs, "count must be saved");

    BLOCK_COMMENT("PreBarrier");

    RegisterSet saved_regs = RegisterSet(R0, as_Register(callee_saved_regs-1));
    __ push(saved_regs | R9ifScratched);

    if (addr != R0) {
      assert_different_registers(count, R0);
      __ mov(R0, addr);
    }
    if (count != R1) {
      __ mov(R1, count);
    }

    if (UseCompressedOops) {
      __ call(CAST_FROM_FN_PTR(address, G1BarrierSetRuntime::write_ref_array_pre_narrow_oop_entry));
    } else {
      __ call(CAST_FROM_FN_PTR(address, G1BarrierSetRuntime::write_ref_array_pre_oop_entry));
    }

    __ pop(saved_regs | R9ifScratched);
  }
}

void G1BarrierSetAssembler::gen_write_ref_array_post_barrier(MacroAssembler* masm, DecoratorSet decorators,
                                                             Register addr, Register count, Register tmp) {

  BLOCK_COMMENT("G1PostBarrier");
  if (addr != R0) {
    assert_different_registers(count, R0);
    __ mov(R0, addr);
  }
  if (count != R1) {
    __ mov(R1, count);
  }
#if R9_IS_SCRATCHED
  // Safer to save R9 here since callers may have been written
  // assuming R9 survives. This is suboptimal but is not in
  // general worth optimizing for the few platforms where R9
  // is scratched. Note that the optimization might not be to
  // difficult for this particular call site.
  __ push(R9);
#endif // !R9_IS_SCRATCHED
  __ call(CAST_FROM_FN_PTR(address, G1BarrierSetRuntime::write_ref_array_post_entry));
#if R9_IS_SCRATCHED
  __ pop(R9);
#endif // !R9_IS_SCRATCHED
}

static void generate_queue_test_and_insertion(MacroAssembler* masm, ByteSize index_offset, ByteSize buffer_offset, Label& runtime,
                                              const Register thread, const Register value, const Register temp1, const Register temp2) {
  assert_different_registers(value, temp1, temp2);
  // Can we store original value in the thread's buffer?
  // (The index field is typed as size_t.)
  __ ldr(temp1, Address(thread, in_bytes(index_offset)));  // temp1 := *(index address)
  __ cbz(temp1, runtime);                                  // jump to runtime if index == 0 (full buffer)
  // The buffer is not full, store value into it.
  __ sub(temp1, temp1, wordSize);                          // temp1 := next index
  __ str(temp1, Address(thread, in_bytes(index_offset)));  // *(index address) := next index
  __ ldr(temp2, Address(thread, in_bytes(buffer_offset))); // temp2 := buffer address
  // Record the previous value
  __ str(value, Address(temp2, temp1));                    // *(buffer address + next index) := value
 }

static void generate_pre_barrier_fast_path(MacroAssembler* masm,
                                           const Register thread,
                                           const Register tmp1) {
  Address in_progress(thread, in_bytes(G1ThreadLocalData::satb_mark_queue_active_offset()));
  // Is marking active?
  assert(in_bytes(SATBMarkQueue::byte_width_of_active()) == 1, "adjust this code");
  __ ldrb(tmp1, in_progress);
}

static void generate_pre_barrier_slow_path(MacroAssembler* masm,
                                           const Register obj,
                                           const Register pre_val,
                                           const Register thread,
                                           const Register tmp1,
                                           const Register tmp2,
                                           Label& done,
                                           Label& runtime) {
  // Do we need to load the previous value?
  if (obj != noreg) {
    __ load_heap_oop(pre_val, Address(obj, 0));
  }

  // Is the previous value null?
  __ cbz(pre_val, done);

  generate_queue_test_and_insertion(masm,
                                    G1ThreadLocalData::satb_mark_queue_index_offset(),
                                    G1ThreadLocalData::satb_mark_queue_buffer_offset(),
                                    runtime,
                                    thread, pre_val, tmp1, tmp2);
  __ b(done);
}

// G1 pre-barrier.
// Blows all volatile registers R0-R3, LR).
// If obj != noreg, then previous value is loaded from [obj];
// in such case obj and pre_val registers is preserved;
// otherwise pre_val register is preserved.
void G1BarrierSetAssembler::g1_write_barrier_pre(MacroAssembler* masm,
                                          Register obj,
                                          Register pre_val,
                                          Register tmp1,
                                          Register tmp2) {
  Label done;
  Label runtime;

  assert_different_registers(obj, pre_val, tmp1, tmp2, noreg);

  generate_pre_barrier_fast_path(masm, Rthread, tmp1);
  // If marking is not active (*(mark queue active address) == 0), jump to done
  __ cbz(tmp1, done);

   generate_pre_barrier_slow_path(masm, obj, pre_val, Rthread, tmp1, tmp2, done, runtime);

  __ bind(runtime);

  // save the live input values
  RegisterSet set = RegisterSet(pre_val) | RegisterSet(R0, R3) | RegisterSet(R12);
  // save the live input values
  if (obj != noreg) {
    // avoid raw_push to support any ordering of store_addr and pre_val
    set = set | RegisterSet(obj);
  }

  __ push(set);

  if (pre_val != R0) {
    __ mov(R0, pre_val);
  }
  __ mov(R1, Rthread);

  __ call_VM_leaf(CAST_FROM_FN_PTR(address, G1BarrierSetRuntime::write_ref_field_pre_entry), R0, R1);

  __ pop(set);
  __ bind(done);
}

static void generate_post_barrier_fast_path(MacroAssembler* masm,
                                            const Register store_addr,
                                            const Register new_val,
                                            const Register tmp1,
                                            const Register tmp2,
                                            Label& done,
                                            bool new_val_may_be_null) {
  // Does store cross heap regions?

  __ eor(tmp1, store_addr, new_val);
  __ movs(tmp1, AsmOperand(tmp1, lsr, G1HeapRegion::LogOfHRGrainBytes));
  __ b(done, eq);

  // crosses regions, storing null?
  if (new_val_may_be_null) {
    __ cbz(new_val, done);
  }
  // storing region crossing non-null, is card already dirty?
  const Register card_addr = tmp1;

  CardTableBarrierSet* ct = barrier_set_cast<CardTableBarrierSet>(BarrierSet::barrier_set());
  __ mov_address(tmp2, (address)ct->card_table()->byte_map_base());
  __ add(card_addr, tmp2, AsmOperand(store_addr, lsr, CardTable::card_shift()));

  __ ldrb(tmp2, Address(card_addr));
  __ cmp(tmp2, (int)G1CardTable::g1_young_card_val());
}

static void generate_post_barrier_slow_path(MacroAssembler* masm,
                                            const Register thread,
                                            const Register tmp1,
                                            const Register tmp2,
                                            const Register tmp3,
                                            Label& done,
                                            Label& runtime) {
  __ membar(MacroAssembler::Membar_mask_bits(MacroAssembler::StoreLoad), tmp2);
  assert(CardTable::dirty_card_val() == 0, "adjust this code");
  // card_addr is loaded by generate_post_barrier_fast_path
  const Register card_addr = tmp1;
  __ ldrb(tmp2, Address(card_addr));
  __ cbz(tmp2, done);

  // storing a region crossing, non-null oop, card is clean.
  // dirty card and log.

  __ strb(__ zero_register(tmp2), Address(card_addr));
  generate_queue_test_and_insertion(masm,
                                    G1ThreadLocalData::dirty_card_queue_index_offset(),
                                    G1ThreadLocalData::dirty_card_queue_buffer_offset(),
                                    runtime,
                                    thread, card_addr, tmp2, tmp3);
  __ b(done);
}


// G1 post-barrier.
// Blows all volatile registers R0-R3,  LR).
void G1BarrierSetAssembler::g1_write_barrier_post(MacroAssembler* masm,
                                           Register store_addr,
                                           Register new_val,
                                           Register tmp1,
                                           Register tmp2,
                                           Register tmp3) {
  Label done;
  Label runtime;

  generate_post_barrier_fast_path(masm, store_addr, new_val, tmp1, tmp2, done, true /* new_val_may_be_null */);
  // If card is young, jump to done
  // card_addr and card are loaded by generate_post_barrier_fast_path
  const Register card      = tmp2;
  const Register card_addr = tmp1;
   __ b(done, eq);
  generate_post_barrier_slow_path(masm, Rthread, card_addr, tmp2, tmp3, done, runtime);

  __ bind(runtime);

  RegisterSet set = RegisterSet(store_addr) | RegisterSet(R0, R3) | RegisterSet(R12);
  __ push(set);

  if (card_addr != R0) {
    __ mov(R0, card_addr);
  }
  __ mov(R1, Rthread);
  __ call_VM_leaf(CAST_FROM_FN_PTR(address, G1BarrierSetRuntime::write_ref_field_post_entry), R0, R1);

  __ pop(set);

  __ bind(done);
}

#if defined(COMPILER2)

static void generate_c2_barrier_runtime_call(MacroAssembler* masm, G1BarrierStubC2* stub, const Register arg, const address runtime_path, Register tmp1) {
  SaveLiveRegisters save_registers(masm, stub);
  if (c_rarg0 != arg) {
    __ mov(c_rarg0, arg);
  }
  __ mov(c_rarg1, Rthread);
  __ call_VM_leaf(runtime_path, R0, R1);
}

void G1BarrierSetAssembler::g1_write_barrier_pre_c2(MacroAssembler* masm,
                                                    Register obj,
                                                    Register pre_val,
                                                    Register thread,
                                                    Register tmp1,
                                                    Register tmp2,
                                                    G1PreBarrierStubC2* stub) {
  assert(thread == Rthread, "must be");
  assert_different_registers(obj, pre_val, tmp1, tmp2);
  assert(pre_val != noreg && tmp1 != noreg && tmp2 != noreg, "expecting a register");

  stub->initialize_registers(obj, pre_val, thread, tmp1, tmp2);

  generate_pre_barrier_fast_path(masm, thread, tmp1);
  // If marking is active (*(mark queue active address) != 0), jump to stub (slow path)
  __ cbnz(tmp1, *stub->entry());

  __ bind(*stub->continuation());
}

void G1BarrierSetAssembler::generate_c2_pre_barrier_stub(MacroAssembler* masm,
                                                         G1PreBarrierStubC2* stub) const {
  Assembler::InlineSkippedInstructionsCounter skip_counter(masm);
  Label runtime;
  Register obj = stub->obj();
  Register pre_val = stub->pre_val();
  Register thread = stub->thread();
  Register tmp1 = stub->tmp1();
  Register tmp2 = stub->tmp2();

  __ bind(*stub->entry());
  generate_pre_barrier_slow_path(masm, obj, pre_val, thread, tmp1, tmp2, *stub->continuation(), runtime);

  __ bind(runtime);
  generate_c2_barrier_runtime_call(masm, stub, pre_val, CAST_FROM_FN_PTR(address, G1BarrierSetRuntime::write_ref_field_pre_entry), tmp1);
  __ b(*stub->continuation());
}

void G1BarrierSetAssembler::g1_write_barrier_post_c2(MacroAssembler* masm,
                                                     Register store_addr,
                                                     Register new_val,
                                                     Register thread,
                                                     Register tmp1,
                                                     Register tmp2,
                                                     Register tmp3,
                                                     G1PostBarrierStubC2* stub) {
  assert(thread == Rthread, "must be");
  assert_different_registers(store_addr, new_val, thread, tmp1, tmp2, noreg);

  stub->initialize_registers(thread, tmp1, tmp2, tmp3);

  bool new_val_may_be_null = (stub->barrier_data() & G1C2BarrierPostNotNull) == 0;
  generate_post_barrier_fast_path(masm, store_addr, new_val, tmp1, tmp2, *stub->continuation(), new_val_may_be_null);
  // If card is not young, jump to stub (slow path)
  __ b(*stub->entry(), ne);

  __ bind(*stub->continuation());
}

void G1BarrierSetAssembler::generate_c2_post_barrier_stub(MacroAssembler* masm,
                                                          G1PostBarrierStubC2* stub) const {
  Assembler::InlineSkippedInstructionsCounter skip_counter(masm);
  Label runtime;
  Register thread = stub->thread();
  Register tmp1 = stub->tmp1(); // tmp1 holds the card address.
  Register tmp2 = stub->tmp2();
  Register tmp3 = stub->tmp3();

  __ bind(*stub->entry());
  generate_post_barrier_slow_path(masm, thread, tmp1, tmp2, tmp3,  *stub->continuation(), runtime);

  __ bind(runtime);
  generate_c2_barrier_runtime_call(masm, stub, tmp1, CAST_FROM_FN_PTR(address, G1BarrierSetRuntime::write_ref_field_post_entry), tmp2);
  __ b(*stub->continuation());
}

#endif // COMPILER2

void G1BarrierSetAssembler::load_at(MacroAssembler* masm, DecoratorSet decorators, BasicType type,
                                    Register dst, Address src, Register tmp1, Register tmp2, Register tmp3) {
  bool on_oop = type == T_OBJECT || type == T_ARRAY;
  bool on_weak = (decorators & ON_WEAK_OOP_REF) != 0;
  bool on_phantom = (decorators & ON_PHANTOM_OOP_REF) != 0;
  bool on_reference = on_weak || on_phantom;

  ModRefBarrierSetAssembler::load_at(masm, decorators, type, dst, src, tmp1, tmp2, tmp3);
  if (on_oop && on_reference) {
    // Generate the G1 pre-barrier code to log the value of
    // the referent field in an SATB buffer.
    g1_write_barrier_pre(masm, noreg, dst, tmp1, tmp2);
  }
}


void G1BarrierSetAssembler::oop_store_at(MacroAssembler* masm, DecoratorSet decorators, BasicType type,
                                         Address obj, Register new_val, Register tmp1, Register tmp2, Register tmp3, bool is_null) {
  bool in_heap = (decorators & IN_HEAP) != 0;
  bool as_normal = (decorators & AS_NORMAL) != 0;
  assert((decorators & IS_DEST_UNINITIALIZED) == 0, "unsupported");

  bool needs_pre_barrier = as_normal;
  bool needs_post_barrier = (new_val != noreg) && in_heap;

  // flatten object address if needed
  assert (obj.mode() == basic_offset, "pre- or post-indexing is not supported here");

  const Register store_addr = obj.base();
  if (obj.index() != noreg) {
    assert (obj.disp() == 0, "index or displacement, not both");
    assert(obj.offset_op() == add_offset, "addition is expected");
    __ add(store_addr, obj.base(), AsmOperand(obj.index(), obj.shift(), obj.shift_imm()));
  } else if (obj.disp() != 0) {
    __ add(store_addr, obj.base(), obj.disp());
  }

  if (needs_pre_barrier) {
    g1_write_barrier_pre(masm, store_addr, tmp3 /*pre_val*/, tmp1, tmp2);
  }

  if (is_null) {
    BarrierSetAssembler::store_at(masm, decorators, type, Address(store_addr), new_val, tmp1, tmp2, tmp3, true);
  } else {
    // G1 barrier needs uncompressed oop for region cross check.
    Register val_to_store = new_val;
    if (UseCompressedOops) {
      val_to_store = tmp1;
      __ mov(val_to_store, new_val);
    }
    BarrierSetAssembler::store_at(masm, decorators, type, Address(store_addr), val_to_store, tmp1, tmp2, tmp3, false);
    if (needs_post_barrier) {
      g1_write_barrier_post(masm, store_addr, new_val, tmp1, tmp2, tmp3);
    }
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
    ce->mem2reg(stub->addr(), stub->pre_val(), T_OBJECT, stub->patch_code(), stub->info(), false /*wide*/);
  }

  __ cbz(pre_val_reg, *stub->continuation());
  ce->verify_reserved_argument_area_size(1);
  __ str(pre_val_reg, Address(SP));
  __ call(bs->pre_barrier_c1_runtime_code_blob()->code_begin(), relocInfo::runtime_call_type);

  __ b(*stub->continuation());
}

void G1BarrierSetAssembler::gen_post_barrier_stub(LIR_Assembler* ce, G1PostBarrierStub* stub) {
  G1BarrierSetC1* bs = (G1BarrierSetC1*)BarrierSet::barrier_set()->barrier_set_c1();
  __ bind(*stub->entry());
  assert(stub->addr()->is_register(), "Precondition.");
  assert(stub->new_val()->is_register(), "Precondition.");
  Register new_val_reg = stub->new_val()->as_register();
  __ cbz(new_val_reg, *stub->continuation());
  ce->verify_reserved_argument_area_size(1);
  __ str(stub->addr()->as_pointer_register(), Address(SP));
  __ call(bs->post_barrier_c1_runtime_code_blob()->code_begin(), relocInfo::runtime_call_type);
  __ b(*stub->continuation());
}

#undef __
#define __ sasm->

void G1BarrierSetAssembler::generate_c1_pre_barrier_runtime_stub(StubAssembler* sasm) {
  // Input:
  // - pre_val pushed on the stack

  __ set_info("g1_pre_barrier_slow_id", false);

  // save at least the registers that need saving if the runtime is called
  const RegisterSet saved_regs = RegisterSet(R0,R3) | RegisterSet(R12) | RegisterSet(LR);
  const int nb_saved_regs = 6;
  assert(nb_saved_regs == saved_regs.size(), "fix nb_saved_regs");
  __ push(saved_regs);

  const Register r_pre_val_0  = R0; // must be R0, to be ready for the runtime call
  const Register r_index_1    = R1;
  const Register r_buffer_2   = R2;

  Address queue_active(Rthread, in_bytes(G1ThreadLocalData::satb_mark_queue_active_offset()));
  Address queue_index(Rthread, in_bytes(G1ThreadLocalData::satb_mark_queue_index_offset()));
  Address buffer(Rthread, in_bytes(G1ThreadLocalData::satb_mark_queue_buffer_offset()));

  Label done;
  Label runtime;

  // Is marking still active?
  assert(in_bytes(SATBMarkQueue::byte_width_of_active()) == 1, "Assumption");
  __ ldrb(R1, queue_active);
  __ cbz(R1, done);

  __ ldr(r_index_1, queue_index);
  __ ldr(r_pre_val_0, Address(SP, nb_saved_regs*wordSize));
  __ ldr(r_buffer_2, buffer);

  __ subs(r_index_1, r_index_1, wordSize);
  __ b(runtime, lt);

  __ str(r_index_1, queue_index);
  __ str(r_pre_val_0, Address(r_buffer_2, r_index_1));

  __ bind(done);

  __ pop(saved_regs);

  __ ret();

  __ bind(runtime);

  __ save_live_registers();

  assert(r_pre_val_0 == c_rarg0, "pre_val should be in R0");
  __ mov(c_rarg1, Rthread);
  __ call_VM_leaf(CAST_FROM_FN_PTR(address, G1BarrierSetRuntime::write_ref_field_pre_entry), c_rarg0, c_rarg1);

  __ restore_live_registers_without_return();

  __ b(done);
}

void G1BarrierSetAssembler::generate_c1_post_barrier_runtime_stub(StubAssembler* sasm) {
  // Input:
  // - store_addr, pushed on the stack

  __ set_info("g1_post_barrier_slow_id", false);

  Label done;
  Label recheck;
  Label runtime;

  Address queue_index(Rthread, in_bytes(G1ThreadLocalData::dirty_card_queue_index_offset()));
  Address buffer(Rthread, in_bytes(G1ThreadLocalData::dirty_card_queue_buffer_offset()));

  AddressLiteral cardtable(ci_card_table_address_as<address>(), relocInfo::none);

  // save at least the registers that need saving if the runtime is called
  const RegisterSet saved_regs = RegisterSet(R0,R3) | RegisterSet(R12) | RegisterSet(LR);
  const int nb_saved_regs = 6;
  assert(nb_saved_regs == saved_regs.size(), "fix nb_saved_regs");
  __ push(saved_regs);

  const Register r_card_addr_0 = R0; // must be R0 for the slow case
  const Register r_obj_0 = R0;
  const Register r_card_base_1 = R1;
  const Register r_tmp2 = R2;
  const Register r_index_2 = R2;
  const Register r_buffer_3 = R3;
  const Register tmp1 = Rtemp;

  __ ldr(r_obj_0, Address(SP, nb_saved_regs*wordSize));
  // Note: there is a comment in x86 code about not using
  // ExternalAddress / lea, due to relocation not working
  // properly for that address. Should be OK for arm, where we
  // explicitly specify that 'cardtable' has a relocInfo::none
  // type.
  __ lea(r_card_base_1, cardtable);
  __ add(r_card_addr_0, r_card_base_1, AsmOperand(r_obj_0, lsr, CardTable::card_shift()));

  // first quick check without barrier
  __ ldrb(r_tmp2, Address(r_card_addr_0));

  __ cmp(r_tmp2, (int)G1CardTable::g1_young_card_val());
  __ b(recheck, ne);

  __ bind(done);

  __ pop(saved_regs);

  __ ret();

  __ bind(recheck);

  __ membar(MacroAssembler::Membar_mask_bits(MacroAssembler::StoreLoad), tmp1);

  // reload card state after the barrier that ensures the stored oop was visible
  __ ldrb(r_tmp2, Address(r_card_addr_0));

  assert(CardTable::dirty_card_val() == 0, "adjust this code");
  __ cbz(r_tmp2, done);

  // storing region crossing non-null, card is clean.
  // dirty card and log.

  assert(0 == (int)CardTable::dirty_card_val(), "adjust this code");
  if ((ci_card_table_address_as<intptr_t>() & 0xff) == 0) {
    // Card table is aligned so the lowest byte of the table address base is zero.
    __ strb(r_card_base_1, Address(r_card_addr_0));
  } else {
    __ strb(__ zero_register(r_tmp2), Address(r_card_addr_0));
  }

  __ ldr(r_index_2, queue_index);
  __ ldr(r_buffer_3, buffer);

  __ subs(r_index_2, r_index_2, wordSize);
  __ b(runtime, lt); // go to runtime if now negative

  __ str(r_index_2, queue_index);

  __ str(r_card_addr_0, Address(r_buffer_3, r_index_2));

  __ b(done);

  __ bind(runtime);

  __ save_live_registers();

  assert(r_card_addr_0 == c_rarg0, "card_addr should be in R0");
  __ mov(c_rarg1, Rthread);
  __ call_VM_leaf(CAST_FROM_FN_PTR(address, G1BarrierSetRuntime::write_ref_field_post_entry), c_rarg0, c_rarg1);

  __ restore_live_registers_without_return();

  __ b(done);
}

#undef __

#endif // COMPILER1
