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
#include "gc/g1/g1BarrierSetAssembler.hpp"
#include "gc/g1/g1ThreadLocalData.hpp"
#include "gc/g1/g1CardTable.hpp"
#include "gc/g1/heapRegion.hpp"
#include "interpreter/interp_masm.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/thread.hpp"
#include "utilities/macros.hpp"
#ifdef COMPILER1
#include "c1/c1_LIRAssembler.hpp"
#include "c1/c1_MacroAssembler.hpp"
#include "gc/g1/c1/g1BarrierSetC1.hpp"
#endif

#define __ masm->

#ifdef PRODUCT
#define BLOCK_COMMENT(str) /* nothing */
#else
#define BLOCK_COMMENT(str) __ block_comment(str)
#endif

#define BIND(label) bind(label); BLOCK_COMMENT(#label ":")

void G1BarrierSetAssembler::gen_write_ref_array_pre_barrier(MacroAssembler* masm, DecoratorSet decorators,
                                                            Register addr, Register count, int callee_saved_regs) {
  bool dest_uninitialized = (decorators & AS_DEST_NOT_INITIALIZED) != 0;
  if (!dest_uninitialized) {
    assert( addr->encoding() < callee_saved_regs, "addr must be saved");
    assert(count->encoding() < callee_saved_regs, "count must be saved");

    BLOCK_COMMENT("PreBarrier");

#ifdef AARCH64
    callee_saved_regs = align_up(callee_saved_regs, 2);
    for (int i = 0; i < callee_saved_regs; i += 2) {
      __ raw_push(as_Register(i), as_Register(i+1));
    }
#else
    RegisterSet saved_regs = RegisterSet(R0, as_Register(callee_saved_regs-1));
    __ push(saved_regs | R9ifScratched);
#endif // AARCH64

    if (addr != R0) {
      assert_different_registers(count, R0);
      __ mov(R0, addr);
    }
#ifdef AARCH64
    __ zero_extend(R1, count, 32); // G1BarrierSet::write_ref_array_pre_*_entry takes size_t
#else
    if (count != R1) {
      __ mov(R1, count);
    }
#endif // AARCH64

    if (UseCompressedOops) {
      __ call(CAST_FROM_FN_PTR(address, G1BarrierSet::write_ref_array_pre_narrow_oop_entry));
    } else {
      __ call(CAST_FROM_FN_PTR(address, G1BarrierSet::write_ref_array_pre_oop_entry));
    }

#ifdef AARCH64
    for (int i = callee_saved_regs - 2; i >= 0; i -= 2) {
      __ raw_pop(as_Register(i), as_Register(i+1));
    }
#else
    __ pop(saved_regs | R9ifScratched);
#endif // AARCH64
  }
}

void G1BarrierSetAssembler::gen_write_ref_array_post_barrier(MacroAssembler* masm, DecoratorSet decorators,
                                                             Register addr, Register count, Register tmp) {

  BLOCK_COMMENT("G1PostBarrier");
  if (addr != R0) {
    assert_different_registers(count, R0);
    __ mov(R0, addr);
  }
#ifdef AARCH64
  __ zero_extend(R1, count, 32); // G1BarrierSet::write_ref_array_post_entry takes size_t
#else
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
#endif // !AARCH64
  __ call(CAST_FROM_FN_PTR(address, G1BarrierSet::write_ref_array_post_entry));
#ifndef AARCH64
#if R9_IS_SCRATCHED
  __ pop(R9);
#endif // !R9_IS_SCRATCHED
#endif // !AARCH64
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
#ifdef AARCH64
  __ raw_push(R0, R1);
  __ raw_push(R2, R3);
  const int nb_saved_regs = 4;
#else // AARCH64
  const RegisterSet saved_regs = RegisterSet(R0,R3) | RegisterSet(R12) | RegisterSet(LR);
  const int nb_saved_regs = 6;
  assert(nb_saved_regs == saved_regs.size(), "fix nb_saved_regs");
  __ push(saved_regs);
#endif // AARCH64

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

#ifdef AARCH64
  __ raw_pop(R2, R3);
  __ raw_pop(R0, R1);
#else // AARCH64
  __ pop(saved_regs);
#endif // AARCH64

  __ ret();

  __ bind(runtime);

  __ save_live_registers();

  assert(r_pre_val_0 == c_rarg0, "pre_val should be in R0");
  __ mov(c_rarg1, Rthread);
  __ call_VM_leaf(CAST_FROM_FN_PTR(address, SharedRuntime::g1_wb_pre), c_rarg0, c_rarg1);

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
#ifdef AARCH64
  __ raw_push(R0, R1);
  __ raw_push(R2, R3);
  const int nb_saved_regs = 4;
#else // AARCH64
  const RegisterSet saved_regs = RegisterSet(R0,R3) | RegisterSet(R12) | RegisterSet(LR);
  const int nb_saved_regs = 6;
  assert(nb_saved_regs == saved_regs.size(), "fix nb_saved_regs");
  __ push(saved_regs);
#endif // AARCH64

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
  __ add(r_card_addr_0, r_card_base_1, AsmOperand(r_obj_0, lsr, CardTable::card_shift));

  // first quick check without barrier
  __ ldrb(r_tmp2, Address(r_card_addr_0));

  __ cmp(r_tmp2, (int)G1CardTable::g1_young_card_val());
  __ b(recheck, ne);

  __ bind(done);

#ifdef AARCH64
  __ raw_pop(R2, R3);
  __ raw_pop(R0, R1);
#else // AARCH64
  __ pop(saved_regs);
#endif // AARCH64

  __ ret();

  __ bind(recheck);

  __ membar(MacroAssembler::Membar_mask_bits(MacroAssembler::StoreLoad), tmp1);

  // reload card state after the barrier that ensures the stored oop was visible
  __ ldrb(r_tmp2, Address(r_card_addr_0));

  assert(CardTable::dirty_card_val() == 0, "adjust this code");
  __ cbz(r_tmp2, done);

  // storing region crossing non-NULL, card is clean.
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
  __ call_VM_leaf(CAST_FROM_FN_PTR(address, SharedRuntime::g1_wb_post), c_rarg0, c_rarg1);

  __ restore_live_registers_without_return();

  __ b(done);
}

#undef __

#endif // COMPILER1
