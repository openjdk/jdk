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
#include "runtime/sharedRuntime.hpp"
#include "utilities/debug.hpp"
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

void G1BarrierSetAssembler::gen_write_ref_array_pre_barrier(MacroAssembler* masm, DecoratorSet decorators,
                                                            Register addr, Register count) {
  bool dest_uninitialized = (decorators & IS_DEST_UNINITIALIZED) != 0;

  if (!dest_uninitialized) {
    Register thread = r15_thread;

    Label filtered;
    Address in_progress(thread, in_bytes(G1ThreadLocalData::satb_mark_queue_active_offset()));
    // Is marking active?
    if (in_bytes(SATBMarkQueue::byte_width_of_active()) == 4) {
      __ cmpl(in_progress, 0);
    } else {
      assert(in_bytes(SATBMarkQueue::byte_width_of_active()) == 1, "Assumption");
      __ cmpb(in_progress, 0);
    }

    __ jcc(Assembler::equal, filtered);

    __ push_call_clobbered_registers(false /* save_fpu */);
    if (count == c_rarg0) {
      if (addr == c_rarg1) {
        // exactly backwards!!
        __ xchgptr(c_rarg1, c_rarg0);
      } else {
        __ movptr(c_rarg1, count);
        __ movptr(c_rarg0, addr);
      }
    } else {
      __ movptr(c_rarg0, addr);
      __ movptr(c_rarg1, count);
    }
    if (UseCompressedOops) {
      __ call_VM_leaf(CAST_FROM_FN_PTR(address, G1BarrierSetRuntime::write_ref_array_pre_narrow_oop_entry), 2);
    } else {
      __ call_VM_leaf(CAST_FROM_FN_PTR(address, G1BarrierSetRuntime::write_ref_array_pre_oop_entry), 2);
    }
    __ pop_call_clobbered_registers(false /* save_fpu */);

    __ bind(filtered);
  }
}

void G1BarrierSetAssembler::gen_write_ref_array_post_barrier(MacroAssembler* masm, DecoratorSet decorators,
                                                             Register addr, Register count, Register tmp) {
  __ push_call_clobbered_registers(false /* save_fpu */);
  if (c_rarg0 == count) { // On win64 c_rarg0 == rcx
    assert_different_registers(c_rarg1, addr);
    __ mov(c_rarg1, count);
    __ mov(c_rarg0, addr);
  } else {
    assert_different_registers(c_rarg0, count);
    __ mov(c_rarg0, addr);
    __ mov(c_rarg1, count);
  }
  __ call_VM_leaf(CAST_FROM_FN_PTR(address, G1BarrierSetRuntime::write_ref_array_post_entry), 2);
  __ pop_call_clobbered_registers(false /* save_fpu */);

}

void G1BarrierSetAssembler::load_at(MacroAssembler* masm, DecoratorSet decorators, BasicType type,
                                    Register dst, Address src, Register tmp1) {
  bool on_oop = is_reference_type(type);
  bool on_weak = (decorators & ON_WEAK_OOP_REF) != 0;
  bool on_phantom = (decorators & ON_PHANTOM_OOP_REF) != 0;
  bool on_reference = on_weak || on_phantom;
  ModRefBarrierSetAssembler::load_at(masm, decorators, type, dst, src, tmp1);
  if (on_oop && on_reference) {
    // Generate the G1 pre-barrier code to log the value of
    // the referent field in an SATB buffer.
    g1_write_barrier_pre(masm /* masm */,
                         noreg /* obj */,
                         dst /* pre_val */,
                         tmp1 /* tmp */,
                         true /* tosca_live */,
                         true /* expand_call */);
  }
}

static void generate_queue_insertion(MacroAssembler* masm, ByteSize index_offset, ByteSize buffer_offset, Label& runtime,
                                     const Register thread, const Register value, const Register temp) {
  // This code assumes that buffer index is pointer sized.
  STATIC_ASSERT(in_bytes(SATBMarkQueue::byte_width_of_index()) == sizeof(intptr_t));
  // Can we store a value in the given thread's buffer?
  // (The index field is typed as size_t.)
  __ movptr(temp, Address(thread, in_bytes(index_offset)));   // temp := *(index address)
  __ testptr(temp, temp);                                     // index == 0?
  __ jcc(Assembler::zero, runtime);                           // jump to runtime if index == 0 (full buffer)
  // The buffer is not full, store value into it.
  __ subptr(temp, wordSize);                                  // temp := next index
  __ movptr(Address(thread, in_bytes(index_offset)), temp);   // *(index address) := next index
  __ addptr(temp, Address(thread, in_bytes(buffer_offset)));  // temp := buffer address + next index
  __ movptr(Address(temp, 0), value);                         // *(buffer address + next index) := value
}

static void generate_pre_barrier_fast_path(MacroAssembler* masm,
                                           const Register thread) {
  Address in_progress(thread, in_bytes(G1ThreadLocalData::satb_mark_queue_active_offset()));
  // Is marking active?
  if (in_bytes(SATBMarkQueue::byte_width_of_active()) == 4) {
    __ cmpl(in_progress, 0);
  } else {
    assert(in_bytes(SATBMarkQueue::byte_width_of_active()) == 1, "Assumption");
    __ cmpb(in_progress, 0);
  }
}

static void generate_pre_barrier_slow_path(MacroAssembler* masm,
                                           const Register obj,
                                           const Register pre_val,
                                           const Register thread,
                                           const Register tmp,
                                           Label& done,
                                           Label& runtime) {
  // Do we need to load the previous value?
  if (obj != noreg) {
    __ load_heap_oop(pre_val, Address(obj, 0), noreg, AS_RAW);
  }
  // Is the previous value null?
  __ cmpptr(pre_val, NULL_WORD);
  __ jcc(Assembler::equal, done);
  generate_queue_insertion(masm,
                           G1ThreadLocalData::satb_mark_queue_index_offset(),
                           G1ThreadLocalData::satb_mark_queue_buffer_offset(),
                           runtime,
                           thread, pre_val, tmp);
  __ jmp(done);
}

void G1BarrierSetAssembler::g1_write_barrier_pre(MacroAssembler* masm,
                                                 Register obj,
                                                 Register pre_val,
                                                 Register tmp,
                                                 bool tosca_live,
                                                 bool expand_call) {
  // If expand_call is true then we expand the call_VM_leaf macro
  // directly to skip generating the check by
  // InterpreterMacroAssembler::call_VM_leaf_base that checks _last_sp.

  const Register thread = r15_thread;

  Label done;
  Label runtime;

  assert(pre_val != noreg, "check this code");

  if (obj != noreg) {
    assert_different_registers(obj, pre_val, tmp);
    assert(pre_val != rax, "check this code");
  }

  generate_pre_barrier_fast_path(masm, thread);
  // If marking is not active (*(mark queue active address) == 0), jump to done
  __ jcc(Assembler::equal, done);
  generate_pre_barrier_slow_path(masm, obj, pre_val, thread, tmp, done, runtime);

  __ bind(runtime);

  // Determine and save the live input values
  __ push_call_clobbered_registers();

  // Calling the runtime using the regular call_VM_leaf mechanism generates
  // code (generated by InterpreterMacroAssember::call_VM_leaf_base)
  // that checks that the *(ebp+frame::interpreter_frame_last_sp) == nullptr.
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
    if (c_rarg1 != thread) {
      __ mov(c_rarg1, thread);
    }
    if (c_rarg0 != pre_val) {
      __ mov(c_rarg0, pre_val);
    }
    __ MacroAssembler::call_VM_leaf_base(CAST_FROM_FN_PTR(address, G1BarrierSetRuntime::write_ref_field_pre_entry), 2);
  } else {
    __ call_VM_leaf(CAST_FROM_FN_PTR(address, G1BarrierSetRuntime::write_ref_field_pre_entry), pre_val, thread);
  }

  __ pop_call_clobbered_registers();

  __ bind(done);
}

static void generate_post_barrier_fast_path(MacroAssembler* masm,
                                            const Register store_addr,
                                            const Register new_val,
                                            const Register tmp,
                                            const Register tmp2,
                                            Label& done,
                                            bool new_val_may_be_null) {
  CardTableBarrierSet* ct = barrier_set_cast<CardTableBarrierSet>(BarrierSet::barrier_set());
  // Does store cross heap regions?
  __ movptr(tmp, store_addr);                                    // tmp := store address
  __ xorptr(tmp, new_val);                                       // tmp := store address ^ new value
  __ shrptr(tmp, G1HeapRegion::LogOfHRGrainBytes);               // ((store address ^ new value) >> LogOfHRGrainBytes) == 0?
  __ jcc(Assembler::equal, done);
  // Crosses regions, storing null?
  if (new_val_may_be_null) {
    __ cmpptr(new_val, NULL_WORD);                               // new value == null?
    __ jcc(Assembler::equal, done);
  }
  // Storing region crossing non-null, is card young?
  __ movptr(tmp, store_addr);                                    // tmp := store address
  __ shrptr(tmp, CardTable::card_shift());                       // tmp := card address relative to card table base
  // Do not use ExternalAddress to load 'byte_map_base', since 'byte_map_base' is NOT
  // a valid address and therefore is not properly handled by the relocation code.
  __ movptr(tmp2, (intptr_t)ct->card_table()->byte_map_base());  // tmp2 := card table base address
  __ addptr(tmp, tmp2);                                          // tmp := card address
  __ cmpb(Address(tmp, 0), G1CardTable::g1_young_card_val());    // *(card address) == young_card_val?
}

static void generate_post_barrier_slow_path(MacroAssembler* masm,
                                            const Register thread,
                                            const Register tmp,
                                            const Register tmp2,
                                            Label& done,
                                            Label& runtime) {
  __ membar(Assembler::Membar_mask_bits(Assembler::StoreLoad));  // StoreLoad membar
  __ cmpb(Address(tmp, 0), G1CardTable::dirty_card_val());       // *(card address) == dirty_card_val?
  __ jcc(Assembler::equal, done);
  // Storing a region crossing, non-null oop, card is clean.
  // Dirty card and log.
  __ movb(Address(tmp, 0), G1CardTable::dirty_card_val());       // *(card address) := dirty_card_val
  generate_queue_insertion(masm,
                           G1ThreadLocalData::dirty_card_queue_index_offset(),
                           G1ThreadLocalData::dirty_card_queue_buffer_offset(),
                           runtime,
                           thread, tmp, tmp2);
  __ jmp(done);
}

void G1BarrierSetAssembler::g1_write_barrier_post(MacroAssembler* masm,
                                                  Register store_addr,
                                                  Register new_val,
                                                  Register tmp,
                                                  Register tmp2) {
  const Register thread = r15_thread;

  Label done;
  Label runtime;

  generate_post_barrier_fast_path(masm, store_addr, new_val, tmp, tmp2, done, true /* new_val_may_be_null */);
  // If card is young, jump to done
  __ jcc(Assembler::equal, done);
  generate_post_barrier_slow_path(masm, thread, tmp, tmp2, done, runtime);

  __ bind(runtime);
  // save the live input values
  RegSet saved = RegSet::of(store_addr);
  __ push_set(saved);
  __ call_VM_leaf(CAST_FROM_FN_PTR(address, G1BarrierSetRuntime::write_ref_field_post_entry), tmp, thread);
  __ pop_set(saved);

  __ bind(done);
}

#if defined(COMPILER2)

static void generate_c2_barrier_runtime_call(MacroAssembler* masm, G1BarrierStubC2* stub, const Register arg, const address runtime_path) {
  SaveLiveRegisters save_registers(masm, stub);
  if (c_rarg0 != arg) {
    __ mov(c_rarg0, arg);
  }
  __ mov(c_rarg1, r15_thread);
  // rax is a caller-saved, non-argument-passing register, so it does not
  // interfere with c_rarg0 or c_rarg1. If it contained any live value before
  // entering this stub, it is saved at this point, and restored after the
  // call. If it did not contain any live value, it is free to be used. In
  // either case, it is safe to use it here as a call scratch register.
  __ call(RuntimeAddress(runtime_path), rax);
}

void G1BarrierSetAssembler::g1_write_barrier_pre_c2(MacroAssembler* masm,
                                                    Register obj,
                                                    Register pre_val,
                                                    Register tmp,
                                                    G1PreBarrierStubC2* stub) {
  const Register thread = r15_thread;

  assert(pre_val != noreg, "check this code");
  if (obj != noreg) {
    assert_different_registers(obj, pre_val, tmp);
  }

  stub->initialize_registers(obj, pre_val, thread, tmp);

  generate_pre_barrier_fast_path(masm, thread);
  // If marking is active (*(mark queue active address) != 0), jump to stub (slow path)
  __ jcc(Assembler::notEqual, *stub->entry());

  __ bind(*stub->continuation());
}

void G1BarrierSetAssembler::generate_c2_pre_barrier_stub(MacroAssembler* masm,
                                                         G1PreBarrierStubC2* stub) const {
  Assembler::InlineSkippedInstructionsCounter skip_counter(masm);
  Label runtime;
  Register obj = stub->obj();
  Register pre_val = stub->pre_val();
  Register thread = stub->thread();
  Register tmp = stub->tmp1();
  assert(stub->tmp2() == noreg, "not needed in this platform");

  __ bind(*stub->entry());
  generate_pre_barrier_slow_path(masm, obj, pre_val, thread, tmp, *stub->continuation(), runtime);

  __ bind(runtime);
  generate_c2_barrier_runtime_call(masm, stub, pre_val, CAST_FROM_FN_PTR(address, G1BarrierSetRuntime::write_ref_field_pre_entry));
  __ jmp(*stub->continuation());
}

void G1BarrierSetAssembler::g1_write_barrier_post_c2(MacroAssembler* masm,
                                                     Register store_addr,
                                                     Register new_val,
                                                     Register tmp,
                                                     Register tmp2,
                                                     G1PostBarrierStubC2* stub) {
  const Register thread = r15_thread;
  stub->initialize_registers(thread, tmp, tmp2);

  bool new_val_may_be_null = (stub->barrier_data() & G1C2BarrierPostNotNull) == 0;
  generate_post_barrier_fast_path(masm, store_addr, new_val, tmp, tmp2, *stub->continuation(), new_val_may_be_null);
  // If card is not young, jump to stub (slow path)
  __ jcc(Assembler::notEqual, *stub->entry());

  __ bind(*stub->continuation());
}

void G1BarrierSetAssembler::generate_c2_post_barrier_stub(MacroAssembler* masm,
                                                          G1PostBarrierStubC2* stub) const {
  Assembler::InlineSkippedInstructionsCounter skip_counter(masm);
  Label runtime;
  Register thread = stub->thread();
  Register tmp = stub->tmp1(); // tmp holds the card address.
  Register tmp2 = stub->tmp2();
  assert(stub->tmp3() == noreg, "not needed in this platform");

  __ bind(*stub->entry());
  generate_post_barrier_slow_path(masm, thread, tmp, tmp2, *stub->continuation(), runtime);

  __ bind(runtime);
  generate_c2_barrier_runtime_call(masm, stub, tmp, CAST_FROM_FN_PTR(address, G1BarrierSetRuntime::write_ref_field_post_entry));
  __ jmp(*stub->continuation());
}

#endif // COMPILER2

void G1BarrierSetAssembler::oop_store_at(MacroAssembler* masm, DecoratorSet decorators, BasicType type,
                                         Address dst, Register val, Register tmp1, Register tmp2, Register tmp3) {
  bool in_heap = (decorators & IN_HEAP) != 0;
  bool as_normal = (decorators & AS_NORMAL) != 0;

  bool needs_pre_barrier = as_normal;
  bool needs_post_barrier = val != noreg && in_heap;

  // flatten object address if needed
  // We do it regardless of precise because we need the registers
  if (dst.index() == noreg && dst.disp() == 0) {
    if (dst.base() != tmp1) {
      __ movptr(tmp1, dst.base());
    }
  } else {
    __ lea(tmp1, dst);
  }

  if (needs_pre_barrier) {
    g1_write_barrier_pre(masm /*masm*/,
                         tmp1 /* obj */,
                         tmp2 /* pre_val */,
                         tmp3  /* tmp */,
                         val != noreg /* tosca_live */,
                         false /* expand_call */);
  }
  if (val == noreg) {
    BarrierSetAssembler::store_at(masm, decorators, type, Address(tmp1, 0), val, noreg, noreg, noreg);
  } else {
    Register new_val = val;
    if (needs_post_barrier) {
      // G1 barrier needs uncompressed oop for region cross check.
      if (UseCompressedOops) {
        new_val = tmp2;
        __ movptr(new_val, val);
      }
    }
    BarrierSetAssembler::store_at(masm, decorators, type, Address(tmp1, 0), val, noreg, noreg, noreg);
    if (needs_post_barrier) {
      g1_write_barrier_post(masm /*masm*/,
                            tmp1 /* store_adr */,
                            new_val /* new_val */,
                            tmp3 /* tmp */,
                            tmp2 /* tmp2 */);
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

  __ cmpptr(pre_val_reg, NULL_WORD);
  __ jcc(Assembler::equal, *stub->continuation());
  ce->store_parameter(stub->pre_val()->as_register(), 0);
  __ call(RuntimeAddress(bs->pre_barrier_c1_runtime_code_blob()->code_begin()));
  __ jmp(*stub->continuation());

}

void G1BarrierSetAssembler::gen_post_barrier_stub(LIR_Assembler* ce, G1PostBarrierStub* stub) {
  G1BarrierSetC1* bs = (G1BarrierSetC1*)BarrierSet::barrier_set()->barrier_set_c1();
  __ bind(*stub->entry());
  assert(stub->addr()->is_register(), "Precondition.");
  assert(stub->new_val()->is_register(), "Precondition.");
  Register new_val_reg = stub->new_val()->as_register();
  __ cmpptr(new_val_reg, NULL_WORD);
  __ jcc(Assembler::equal, *stub->continuation());
  ce->store_parameter(stub->addr()->as_pointer_register(), 0);
  __ call(RuntimeAddress(bs->post_barrier_c1_runtime_code_blob()->code_begin()));
  __ jmp(*stub->continuation());
}

#undef __

#define __ sasm->

void G1BarrierSetAssembler::generate_c1_pre_barrier_runtime_stub(StubAssembler* sasm) {
  // Generated code assumes that buffer index is pointer sized.
  STATIC_ASSERT(in_bytes(SATBMarkQueue::byte_width_of_index()) == sizeof(intptr_t));

  __ prologue("g1_pre_barrier", false);
  // arg0 : previous value of memory

  __ push_ppx(rax);
  __ push_ppx(rdx);

  const Register pre_val = rax;
  const Register thread = r15_thread;
  const Register tmp = rdx;

  Address queue_active(thread, in_bytes(G1ThreadLocalData::satb_mark_queue_active_offset()));
  Address queue_index(thread, in_bytes(G1ThreadLocalData::satb_mark_queue_index_offset()));
  Address buffer(thread, in_bytes(G1ThreadLocalData::satb_mark_queue_buffer_offset()));

  Label done;
  Label runtime;

  // Is marking still active?
  if (in_bytes(SATBMarkQueue::byte_width_of_active()) == 4) {
    __ cmpl(queue_active, 0);
  } else {
    assert(in_bytes(SATBMarkQueue::byte_width_of_active()) == 1, "Assumption");
    __ cmpb(queue_active, 0);
  }
  __ jcc(Assembler::equal, done);

  // Can we store original value in the thread's buffer?

  __ movptr(tmp, queue_index);
  __ testptr(tmp, tmp);
  __ jcc(Assembler::zero, runtime);
  __ subptr(tmp, wordSize);
  __ movptr(queue_index, tmp);
  __ addptr(tmp, buffer);

  // prev_val (rax)
  __ load_parameter(0, pre_val);
  __ movptr(Address(tmp, 0), pre_val);
  __ jmp(done);

  __ bind(runtime);

  __ push_call_clobbered_registers();

  // load the pre-value
  __ load_parameter(0, rcx);
  __ call_VM_leaf(CAST_FROM_FN_PTR(address, G1BarrierSetRuntime::write_ref_field_pre_entry), rcx, thread);

  __ pop_call_clobbered_registers();

  __ bind(done);

  __ pop_ppx(rdx);
  __ pop_ppx(rax);

  __ epilogue();
}

void G1BarrierSetAssembler::generate_c1_post_barrier_runtime_stub(StubAssembler* sasm) {
  __ prologue("g1_post_barrier", false);

  CardTableBarrierSet* ct =
    barrier_set_cast<CardTableBarrierSet>(BarrierSet::barrier_set());

  Label done;
  Label enqueued;
  Label runtime;

  // At this point we know new_value is non-null and the new_value crosses regions.
  // Must check to see if card is already dirty

  const Register thread = r15_thread;

  Address queue_index(thread, in_bytes(G1ThreadLocalData::dirty_card_queue_index_offset()));
  Address buffer(thread, in_bytes(G1ThreadLocalData::dirty_card_queue_buffer_offset()));

  __ push_ppx(rax);
  __ push_ppx(rcx);

  const Register cardtable = rax;
  const Register card_addr = rcx;

  __ load_parameter(0, card_addr);
  __ shrptr(card_addr, CardTable::card_shift());
  // Do not use ExternalAddress to load 'byte_map_base', since 'byte_map_base' is NOT
  // a valid address and therefore is not properly handled by the relocation code.
  __ movptr(cardtable, (intptr_t)ct->card_table()->byte_map_base());
  __ addptr(card_addr, cardtable);

  __ cmpb(Address(card_addr, 0), G1CardTable::g1_young_card_val());
  __ jcc(Assembler::equal, done);

  __ membar(Assembler::Membar_mask_bits(Assembler::StoreLoad));
  __ cmpb(Address(card_addr, 0), CardTable::dirty_card_val());
  __ jcc(Assembler::equal, done);

  // storing region crossing non-null, card is clean.
  // dirty card and log.

  __ movb(Address(card_addr, 0), CardTable::dirty_card_val());

  const Register tmp = rdx;
  __ push_ppx(rdx);

  __ movptr(tmp, queue_index);
  __ testptr(tmp, tmp);
  __ jcc(Assembler::zero, runtime);
  __ subptr(tmp, wordSize);
  __ movptr(queue_index, tmp);
  __ addptr(tmp, buffer);
  __ movptr(Address(tmp, 0), card_addr);
  __ jmp(enqueued);

  __ bind(runtime);
  __ push_call_clobbered_registers();

  __ call_VM_leaf(CAST_FROM_FN_PTR(address, G1BarrierSetRuntime::write_ref_field_post_entry), card_addr, thread);

  __ pop_call_clobbered_registers();

  __ bind(enqueued);
  __ pop_ppx(rdx);

  __ bind(done);
  __ pop_ppx(rcx);
  __ pop_ppx(rax);

  __ epilogue();
}

#undef __

#endif // COMPILER1
