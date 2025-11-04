/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2024, Huawei Technologies Co., Ltd. All rights reserved.
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
#include "gc/shared/collectedHeap.hpp"
#include "interpreter/interp_masm.hpp"
#include "runtime/javaThread.hpp"
#include "runtime/sharedRuntime.hpp"
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
                                                            Register addr, Register count, RegSet saved_regs) {
  bool dest_uninitialized = (decorators & IS_DEST_UNINITIALIZED) != 0;
  if (!dest_uninitialized) {
    Label done;
    Address in_progress(xthread, in_bytes(G1ThreadLocalData::satb_mark_queue_active_offset()));

    // Is marking active?
    if (in_bytes(SATBMarkQueue::byte_width_of_active()) == 4) {
      __ lwu(t0, in_progress);
    } else {
      assert(in_bytes(SATBMarkQueue::byte_width_of_active()) == 1, "Assumption");
      __ lbu(t0, in_progress);
    }
    __ beqz(t0, done);

    __ push_reg(saved_regs, sp);
    if (count == c_rarg0) {
      if (addr == c_rarg1) {
        // exactly backwards!!
        __ mv(t0, c_rarg0);
        __ mv(c_rarg0, c_rarg1);
        __ mv(c_rarg1, t0);
      } else {
        __ mv(c_rarg1, count);
        __ mv(c_rarg0, addr);
      }
    } else {
      __ mv(c_rarg0, addr);
      __ mv(c_rarg1, count);
    }
    if (UseCompressedOops) {
      __ call_VM_leaf(CAST_FROM_FN_PTR(address, G1BarrierSetRuntime::write_ref_array_pre_narrow_oop_entry), 2);
    } else {
      __ call_VM_leaf(CAST_FROM_FN_PTR(address, G1BarrierSetRuntime::write_ref_array_pre_oop_entry), 2);
    }
    __ pop_reg(saved_regs, sp);

    __ bind(done);
  }
}

void G1BarrierSetAssembler::gen_write_ref_array_post_barrier(MacroAssembler* masm,
                                                             DecoratorSet decorators,
                                                             Register start,
                                                             Register count,
                                                             Register tmp,
                                                             RegSet saved_regs) {
  assert_different_registers(start, count, tmp);

  Label loop, next, done;

  // Zero count? Nothing to do.
  __ beqz(count, done);

  // Calculate the number of card marks to set. Since the object might start and
  // end within a card, we need to calculate this via the card table indexes of
  // the actual start and last addresses covered by the object.
  // Temporarily use the count register for the last element address.
  __ shadd(count, count, start, tmp, LogBytesPerHeapOop); // end = start + count << LogBytesPerHeapOop
  __ subi(count, count, BytesPerHeapOop);                 // Use last element address for end.

  __ srli(start, start, CardTable::card_shift());
  __ srli(count, count, CardTable::card_shift());
  __ sub(count, count, start);                            // Number of bytes to mark - 1.

  // Add card table base offset to start.
  Address card_table_address(xthread, G1ThreadLocalData::card_table_base_offset());
  __ ld(tmp, card_table_address);
  __ add(start, start, tmp);

  __ bind(loop);
  if (UseCondCardMark) {
    __ add(tmp, start, count);
    __ lbu(tmp, Address(tmp, 0));
    static_assert((uint)G1CardTable::clean_card_val() == 0xff, "must be");
    __ subi(tmp, tmp, G1CardTable::clean_card_val()); // Convert to clean_card_value() to a comparison
                                                      // against zero to avoid use of an extra temp.
    __ bnez(tmp, next);
  }

  __ add(tmp, start, count);
  static_assert(G1CardTable::dirty_card_val() == 0, "must be to use zr");
  __ sb(zr, Address(tmp, 0));

  __ bind(next);
  __ subi(count, count, 1);
  __ bgez(count, loop);

  __ bind(done);
}

static void generate_queue_test_and_insertion(MacroAssembler* masm, ByteSize index_offset, ByteSize buffer_offset, Label& runtime,
                                              const Register thread, const Register value, const Register tmp1, const Register tmp2) {
  // Can we store a value in the given thread's buffer?
  // (The index field is typed as size_t.)
  __ ld(tmp1, Address(thread, in_bytes(index_offset)));   // tmp1 := *(index address)
  __ beqz(tmp1, runtime);                                 // jump to runtime if index == 0 (full buffer)
  // The buffer is not full, store value into it.
  __ subi(tmp1, tmp1, wordSize);                           // tmp1 := next index
  __ sd(tmp1, Address(thread, in_bytes(index_offset)));   // *(index address) := next index
  __ ld(tmp2, Address(thread, in_bytes(buffer_offset)));  // tmp2 := buffer address
  __ add(tmp2, tmp2, tmp1);
  __ sd(value, Address(tmp2));                            // *(buffer address + next index) := value
}

static void generate_pre_barrier_fast_path(MacroAssembler* masm,
                                           const Register thread,
                                           const Register tmp1) {
  Address in_progress(thread, in_bytes(G1ThreadLocalData::satb_mark_queue_active_offset()));
  // Is marking active?
  if (in_bytes(SATBMarkQueue::byte_width_of_active()) == 4) {
    __ lwu(tmp1, in_progress);
  } else {
    assert(in_bytes(SATBMarkQueue::byte_width_of_active()) == 1, "Assumption");
    __ lbu(tmp1, in_progress);
  }
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
    __ load_heap_oop(pre_val, Address(obj, 0), noreg, noreg, AS_RAW);
  }
  // Is the previous value null?
  __ beqz(pre_val, done, true);
  generate_queue_test_and_insertion(masm,
                                    G1ThreadLocalData::satb_mark_queue_index_offset(),
                                    G1ThreadLocalData::satb_mark_queue_buffer_offset(),
                                    runtime,
                                    thread, pre_val, tmp1, tmp2);
  __ j(done);
}

void G1BarrierSetAssembler::g1_write_barrier_pre(MacroAssembler* masm,
                                                 Register obj,
                                                 Register pre_val,
                                                 Register thread,
                                                 Register tmp1,
                                                 Register tmp2,
                                                 bool tosca_live,
                                                 bool expand_call) {
  // If expand_call is true then we expand the call_VM_leaf macro
  // directly to skip generating the check by
  // InterpreterMacroAssembler::call_VM_leaf_base that checks _last_sp.

  assert(thread == xthread, "must be");

  Label done;
  Label runtime;

  assert_different_registers(obj, pre_val, tmp1, tmp2);
  assert(pre_val != noreg && tmp1 != noreg && tmp2 != noreg, "expecting a register");

  generate_pre_barrier_fast_path(masm, thread, tmp1);
  // If marking is not active (*(mark queue active address) == 0), jump to done
  __ beqz(tmp1, done);
  generate_pre_barrier_slow_path(masm, obj, pre_val, thread, tmp1, tmp2, done, runtime);

  __ bind(runtime);

  __ push_call_clobbered_registers();

  if (expand_call) {
    assert(pre_val != c_rarg1, "smashed arg");
    __ super_call_VM_leaf(CAST_FROM_FN_PTR(address, G1BarrierSetRuntime::write_ref_field_pre_entry), pre_val, thread);
  } else {
    __ call_VM_leaf(CAST_FROM_FN_PTR(address, G1BarrierSetRuntime::write_ref_field_pre_entry), pre_val, thread);
  }

  __ pop_call_clobbered_registers();

  __ bind(done);

}

static void generate_post_barrier(MacroAssembler* masm,
                                  const Register store_addr,
                                  const Register new_val,
                                  const Register thread,
                                  const Register tmp1,
                                  const Register tmp2,
                                  Label& done,
                                  bool new_val_may_be_null) {
  assert(thread == xthread, "must be");
  assert_different_registers(store_addr, new_val, thread, tmp1, tmp2, noreg);
  // Does store cross heap regions?
  __ xorr(tmp1, store_addr, new_val);                    // tmp1 := store address ^ new value
  __ srli(tmp1, tmp1, G1HeapRegion::LogOfHRGrainBytes);  // tmp1 := ((store address ^ new value) >> LogOfHRGrainBytes)
  __ beqz(tmp1, done);

  // Crosses regions, storing null?
  if (new_val_may_be_null) {
    __ beqz(new_val, done);
  }
  // Storing region crossing non-null, is card clean?
  __ srli(tmp1, store_addr, CardTable::card_shift());    // tmp1 := card address relative to card table base

  Address card_table_address(xthread, G1ThreadLocalData::card_table_base_offset());
  __ ld(tmp2, card_table_address);                       // tmp2 := card table base address
  __ add(tmp1, tmp1, tmp2);                              // tmp1 := card address
  if (UseCondCardMark) {
    static_assert((uint)G1CardTable::clean_card_val() == 0xff, "must be");
    __ lbu(tmp2, Address(tmp1, 0));                      // tmp2 := card
    __ subi(tmp2, tmp2, G1CardTable::clean_card_val());  // Convert to clean_card_value() to a comparison
                                                         // against zero to avoid use of an extra temp.
    __ bnez(tmp2, done);
  }
  static_assert((uint)G1CardTable::dirty_card_val() == 0, "must be to use zr");
  __ sb(zr, Address(tmp1, 0));
}

void G1BarrierSetAssembler::g1_write_barrier_post(MacroAssembler* masm,
                                                  Register store_addr,
                                                  Register new_val,
                                                  Register thread,
                                                  Register tmp1,
                                                  Register tmp2) {
  Label done;
  generate_post_barrier(masm, store_addr, new_val, thread, tmp1, tmp2, done, true /* new_val_may_be_null */);
  __ bind(done);
}

#if defined(COMPILER2)

static void generate_c2_barrier_runtime_call(MacroAssembler* masm, G1BarrierStubC2* stub, const Register arg, const address runtime_path) {
  SaveLiveRegisters save_registers(masm, stub);
  if (c_rarg0 != arg) {
    __ mv(c_rarg0, arg);
  }
  __ mv(c_rarg1, xthread);
  __ mv(t1, runtime_path);
  __ jalr(t1);
}

void G1BarrierSetAssembler::g1_write_barrier_pre_c2(MacroAssembler* masm,
                                                    Register obj,
                                                    Register pre_val,
                                                    Register thread,
                                                    Register tmp1,
                                                    Register tmp2,
                                                    G1PreBarrierStubC2* stub) {
  assert(thread == xthread, "must be");
  assert_different_registers(obj, pre_val, tmp1, tmp2);
  assert(pre_val != noreg && tmp1 != noreg && tmp2 != noreg, "expecting a register");

  stub->initialize_registers(obj, pre_val, thread, tmp1, tmp2);

  generate_pre_barrier_fast_path(masm, thread, tmp1);
  // If marking is active (*(mark queue active address) != 0), jump to stub (slow path)
  __ bnez(tmp1, *stub->entry(), true);

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
  generate_c2_barrier_runtime_call(masm, stub, pre_val, CAST_FROM_FN_PTR(address, G1BarrierSetRuntime::write_ref_field_pre_entry));
  __ j(*stub->continuation());
}

void G1BarrierSetAssembler::g1_write_barrier_post_c2(MacroAssembler* masm,
                                                     Register store_addr,
                                                     Register new_val,
                                                     Register thread,
                                                     Register tmp1,
                                                     Register tmp2,
                                                     bool new_val_may_be_null) {
  Label done;
  generate_post_barrier(masm, store_addr, new_val, thread, tmp1, tmp2, done, new_val_may_be_null);
  __ bind(done);
}

#endif // COMPILER2

void G1BarrierSetAssembler::load_at(MacroAssembler* masm, DecoratorSet decorators, BasicType type,
                                    Register dst, Address src, Register tmp1, Register tmp2) {
  bool on_oop = is_reference_type(type);
  bool on_weak = (decorators & ON_WEAK_OOP_REF) != 0;
  bool on_phantom = (decorators & ON_PHANTOM_OOP_REF) != 0;
  bool on_reference = on_weak || on_phantom;
  CardTableBarrierSetAssembler::load_at(masm, decorators, type, dst, src, tmp1, tmp2);
  if (on_oop && on_reference) {
    // RA is live.  It must be saved around calls.
    __ enter(); // barrier may call runtime
    // Generate the G1 pre-barrier code to log the value of
    // the referent field in an SATB buffer.
    g1_write_barrier_pre(masm /* masm */,
                         noreg /* obj */,
                         dst /* pre_val */,
                         xthread /* thread */,
                         tmp1 /* tmp1 */,
                         tmp2 /* tmp2 */,
                         true /* tosca_live */,
                         true /* expand_call */);
    __ leave();
  }
}

void G1BarrierSetAssembler::oop_store_at(MacroAssembler* masm, DecoratorSet decorators, BasicType type,
                                         Address dst, Register val, Register tmp1, Register tmp2, Register tmp3) {
  // flatten object address if needed
  if (dst.offset() == 0) {
    if (dst.base() != tmp3) {
      __ mv(tmp3, dst.base());
    }
  } else {
    __ la(tmp3, dst);
  }

  g1_write_barrier_pre(masm,
                       tmp3 /* obj */,
                       tmp2 /* pre_val */,
                       xthread /* thread */,
                       tmp1 /* tmp1 */,
                       t1 /* tmp2 */,
                       val != noreg /* tosca_live */,
                       false /* expand_call */);

  if (val == noreg) {
    BarrierSetAssembler::store_at(masm, decorators, type, Address(tmp3, 0), noreg, noreg, noreg, noreg);
  } else {
    // G1 barrier needs uncompressed oop for region cross check.
    Register new_val = val;
    if (UseCompressedOops) {
      new_val = t1;
      __ mv(new_val, val);
    }
    BarrierSetAssembler::store_at(masm, decorators, type, Address(tmp3, 0), val, noreg, noreg, noreg);
    g1_write_barrier_post(masm,
                          tmp3 /* store_adr */,
                          new_val /* new_val */,
                          xthread /* thread */,
                          tmp1 /* tmp1 */,
                          tmp2 /* tmp2 */);
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
    ce->mem2reg(stub->addr(), stub->pre_val(), T_OBJECT, stub->patch_code(), stub->info(), false /* wide */);
  }
  __ beqz(pre_val_reg, *stub->continuation(), /* is_far */ true);
  ce->store_parameter(stub->pre_val()->as_register(), 0);
  __ far_call(RuntimeAddress(bs->pre_barrier_c1_runtime_code_blob()->code_begin()));
  __ j(*stub->continuation());
}

#undef __

void G1BarrierSetAssembler::g1_write_barrier_post_c1(MacroAssembler* masm,
                                                     Register store_addr,
                                                     Register new_val,
                                                     Register thread,
                                                     Register tmp1,
                                                     Register tmp2) {
  Label done;
  generate_post_barrier(masm, store_addr, new_val, thread, tmp1, tmp2, done, true /* new_val_may_be_null */);
  masm->bind(done);
}

#define __ sasm->

void G1BarrierSetAssembler::generate_c1_pre_barrier_runtime_stub(StubAssembler* sasm) {
  __ prologue("g1_pre_barrier", false);

  BarrierSet* bs = BarrierSet::barrier_set();

  // arg0 : previous value of memory
  const Register pre_val = x10;
  const Register thread = xthread;
  const Register tmp = t0;

  Address in_progress(thread, in_bytes(G1ThreadLocalData::satb_mark_queue_active_offset()));
  Address queue_index(thread, in_bytes(G1ThreadLocalData::satb_mark_queue_index_offset()));
  Address buffer(thread, in_bytes(G1ThreadLocalData::satb_mark_queue_buffer_offset()));

  Label done;
  Label runtime;

  // Is marking still active?
  if (in_bytes(SATBMarkQueue::byte_width_of_active()) == 4) {  // 4-byte width
    __ lwu(tmp, in_progress);
  } else {
    assert(in_bytes(SATBMarkQueue::byte_width_of_active()) == 1, "Assumption");
    __ lbu(tmp, in_progress);
  }
  __ beqz(tmp, done);

  // Can we store original value in the thread's buffer?
  __ ld(tmp, queue_index);
  __ beqz(tmp, runtime);

  __ subi(tmp, tmp, wordSize);
  __ sd(tmp, queue_index);
  __ ld(t1, buffer);
  __ add(tmp, tmp, t1);
  __ load_parameter(0, t1);
  __ sd(t1, Address(tmp, 0));
  __ j(done);

  __ bind(runtime);
  __ push_call_clobbered_registers();
  __ load_parameter(0, pre_val);
  __ call_VM_leaf(CAST_FROM_FN_PTR(address, G1BarrierSetRuntime::write_ref_field_pre_entry), pre_val, thread);
  __ pop_call_clobbered_registers();
  __ bind(done);

  __ epilogue();
}

#undef __

#endif // COMPILER1
