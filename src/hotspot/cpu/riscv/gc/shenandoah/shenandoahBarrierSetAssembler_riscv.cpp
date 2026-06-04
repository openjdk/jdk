/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2018, 2020, Red Hat, Inc. All rights reserved.
 * Copyright (c) 2020, 2021, Huawei Technologies Co., Ltd. All rights reserved.
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

#include "gc/shenandoah/heuristics/shenandoahHeuristics.hpp"
#include "gc/shenandoah/mode/shenandoahMode.hpp"
#include "gc/shenandoah/shenandoahBarrierSet.hpp"
#include "gc/shenandoah/shenandoahBarrierSetAssembler.hpp"
#include "gc/shenandoah/shenandoahForwarding.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahHeapRegion.hpp"
#include "gc/shenandoah/shenandoahRuntime.hpp"
#include "gc/shenandoah/shenandoahThreadLocalData.hpp"
#include "interpreter/interp_masm.hpp"
#include "interpreter/interpreter.hpp"
#include "runtime/javaThread.hpp"
#include "runtime/sharedRuntime.hpp"
#ifdef COMPILER1
#include "c1/c1_LIRAssembler.hpp"
#include "c1/c1_MacroAssembler.hpp"
#include "gc/shenandoah/c1/shenandoahBarrierSetC1.hpp"
#endif
#ifdef COMPILER2
#include "gc/shenandoah/c2/shenandoahBarrierSetC2.hpp"
#include "opto/output.hpp"
#endif

#define __ masm->

void ShenandoahBarrierSetAssembler::arraycopy_prologue(MacroAssembler* masm, DecoratorSet decorators, bool is_oop,
                                                       Register src, Register dst, Register count, RegSet saved_regs) {
  if (is_oop) {
    bool dest_uninitialized = (decorators & IS_DEST_UNINITIALIZED) != 0;
    if ((ShenandoahSATBBarrier && !dest_uninitialized) || ShenandoahLoadRefBarrier) {

      Label done;

      // Avoid calling runtime if count == 0
      __ beqz(count, done);

      // Is GC active?
      Address gc_state(xthread, in_bytes(ShenandoahThreadLocalData::gc_state_offset()));
      assert_different_registers(src, dst, count, t0);

      assert(!saved_regs.contains(t0), "Sanity: about to clobber t0");

      __ lbu(t0, gc_state);
      if (ShenandoahSATBBarrier && dest_uninitialized) {
        __ test_bit(t0, t0, ShenandoahHeap::HAS_FORWARDED_BITPOS);
        __ beqz(t0, done);
      } else {
        __ andi(t0, t0, ShenandoahHeap::HAS_FORWARDED | ShenandoahHeap::MARKING);
        __ beqz(t0, done);
      }

      __ push_call_clobbered_registers();
      // If arguments are not in proper places, shuffle them.
      // Doing this via the stack is the most straight-forward way to avoid
      // accidentally smashing any register.
      if (c_rarg0 != src || c_rarg1 != dst || c_rarg2 != count) {
        __ push_reg(RegSet::of(src), sp);
        __ push_reg(RegSet::of(dst), sp);
        __ push_reg(RegSet::of(count), sp);
        __ pop_reg(RegSet::of(c_rarg2), sp);
        __ pop_reg(RegSet::of(c_rarg1), sp);
        __ pop_reg(RegSet::of(c_rarg0), sp);
      }
      address target = nullptr;
      if (UseCompressedOops) {
        target = CAST_FROM_FN_PTR(address, ShenandoahRuntime::arraycopy_barrier_narrow_oop);
      } else {
        target = CAST_FROM_FN_PTR(address, ShenandoahRuntime::arraycopy_barrier_oop);
      }
      __ call_VM_leaf(target, 3);
      __ pop_call_clobbered_registers();
      __ bind(done);
    }
  }
}

void ShenandoahBarrierSetAssembler::arraycopy_epilogue(MacroAssembler* masm, DecoratorSet decorators, bool is_oop,
                                                       Register start, Register count, Register tmp) {
  if (ShenandoahCardBarrier && is_oop) {
    gen_write_ref_array_post_barrier(masm, decorators, start, count, tmp);
  }
}

void ShenandoahBarrierSetAssembler::satb_barrier(MacroAssembler* masm,
                                                 Register obj,
                                                 Register pre_val,
                                                 Register thread,
                                                 Register tmp1,
                                                 Register tmp2) {
  assert(ShenandoahSATBBarrier, "Should be checked by caller");
  assert(thread == xthread, "must be");

  Label done;
  Label runtime;

  assert_different_registers(obj, pre_val, tmp1, tmp2);
  assert(pre_val != noreg && tmp1 != noreg && tmp2 != noreg, "expecting a register");

  Address index(thread, in_bytes(ShenandoahThreadLocalData::satb_mark_queue_index_offset()));
  Address buffer(thread, in_bytes(ShenandoahThreadLocalData::satb_mark_queue_buffer_offset()));

  // Is marking active?
  Address gc_state(xthread, in_bytes(ShenandoahThreadLocalData::gc_state_offset()));
  __ lbu(t1, gc_state);
  __ test_bit(t1, t1, ShenandoahHeap::MARKING_BITPOS);
  __ beqz(t1, done);

  // Do we need to load the previous value?
  if (obj != noreg) {
    if (UseCompressedOops) {
      __ lwu(pre_val, Address(obj, 0));
      __ decode_heap_oop(pre_val);
    } else {
      __ ld(pre_val, Address(obj, 0));
    }
  }

  // Is the previous value null?
  __ beqz(pre_val, done);

  // Can we store original value in the thread's buffer?
  // Is index == 0?
  // (The index field is typed as size_t.)
  __ ld(tmp1, index);                  // tmp := *index_adr
  __ beqz(tmp1, runtime);              // tmp == 0? If yes, goto runtime

  __ subi(tmp1, tmp1, wordSize);       // tmp := tmp - wordSize
  __ sd(tmp1, index);                  // *index_adr := tmp
  __ ld(tmp2, buffer);
  __ add(tmp1, tmp1, tmp2);            // tmp := tmp + *buffer_adr

  // Record the previous value
  __ sd(pre_val, Address(tmp1, 0));
  __ j(done);

  // Slow-path call.
  __ bind(runtime);
  __ enter();
  __ push_call_clobbered_registers();
  if (c_rarg0 != pre_val) {
    __ mv(c_rarg0, pre_val);
  }
  // Calling with super_call_VM_leaf with c_rarg0 bypasses interpreter checks and avoids any moves.
  __ super_call_VM_leaf(CAST_FROM_FN_PTR(address, ShenandoahRuntime::write_barrier_pre), c_rarg0);
  __ pop_call_clobbered_registers();
  __ leave();

  __ bind(done);
}

void ShenandoahBarrierSetAssembler::resolve_forward_pointer(MacroAssembler* masm, Register dst, Register tmp) {
  assert(ShenandoahLoadRefBarrier || ShenandoahCASBarrier, "Should be enabled");

  Label is_null;
  __ beqz(dst, is_null);
  resolve_forward_pointer_not_null(masm, dst, tmp);
  __ bind(is_null);
}

// IMPORTANT: This must preserve all registers, even t0 and t1, except those explicitly
// passed in.
void ShenandoahBarrierSetAssembler::resolve_forward_pointer_not_null(MacroAssembler* masm, Register dst, Register tmp) {
  assert(ShenandoahLoadRefBarrier || ShenandoahCASBarrier, "Should be enabled");
  // The below loads the mark word, checks if the lowest two bits are
  // set, and if so, clear the lowest two bits and copy the result
  // to dst. Otherwise it leaves dst alone.
  // Implementing this is surprisingly awkward. I do it here by:
  // - Inverting the mark word
  // - Test lowest two bits == 0
  // - If so, set the lowest two bits
  // - Invert the result back, and copy to dst
  RegSet saved_regs = RegSet::of(t2);
  bool borrow_reg = (tmp == noreg);
  if (borrow_reg) {
    // No free registers available. Make one useful.
    tmp = t0;
    if (tmp == dst) {
      tmp = t1;
    }
    saved_regs += RegSet::of(tmp);
  }

  assert_different_registers(tmp, dst, t2);
  __ push_reg(saved_regs, sp);

  Label done;
  __ ld(tmp, Address(dst, oopDesc::mark_offset_in_bytes()));
  __ xori(tmp, tmp, -1); // eon with 0 is equivalent to XOR with -1
  __ andi(t2, tmp, markWord::lock_mask_in_place);
  __ bnez(t2, done);
  __ ori(tmp, tmp, markWord::marked_value);
  __ xori(dst, tmp, -1); // eon with 0 is equivalent to XOR with -1
  __ bind(done);

  __ pop_reg(saved_regs, sp);
}

void ShenandoahBarrierSetAssembler::load_reference_barrier(MacroAssembler* masm,
                                                           Register dst,
                                                           Address load_addr,
                                                           DecoratorSet decorators) {
  assert(ShenandoahLoadRefBarrier, "Should be enabled");
  assert(dst != t1 && load_addr.base() != t1, "need t1");
  assert_different_registers(load_addr.base(), t0, t1);

  bool is_strong  = ShenandoahBarrierSet::is_strong_access(decorators);
  bool is_weak    = ShenandoahBarrierSet::is_weak_access(decorators);
  bool is_phantom = ShenandoahBarrierSet::is_phantom_access(decorators);
  bool is_native  = ShenandoahBarrierSet::is_native_access(decorators);
  bool is_narrow  = UseCompressedOops && !is_native;

  Label heap_stable, not_cset;
  Address gc_state(xthread, in_bytes(ShenandoahThreadLocalData::gc_state_offset()));
  __ lbu(t1, gc_state);

  // Check for heap stability
  if (is_strong) {
    __ test_bit(t1, t1, ShenandoahHeap::HAS_FORWARDED_BITPOS);
    __ beqz(t1, heap_stable);
  } else {
    Label lrb;
    __ test_bit(t0, t1, ShenandoahHeap::WEAK_ROOTS_BITPOS);
    __ bnez(t0, lrb);
    __ test_bit(t0, t1, ShenandoahHeap::HAS_FORWARDED_BITPOS);
    __ beqz(t0, heap_stable);
    __ bind(lrb);
  }

  // use x11 for load address
  Register result_dst = dst;
  if (dst == x11) {
    __ mv(t1, dst);
    dst = t1;
  }

  // Save x10 and x11, unless it is an output register
  RegSet saved_regs = RegSet::of(x10, x11) - result_dst;
  __ push_reg(saved_regs, sp);
  __ la(x11, load_addr);
  __ mv(x10, dst);

  // Test for in-cset
  if (is_strong) {
    __ mv(t1, ShenandoahHeap::in_cset_fast_test_addr());
    __ srli(t0, x10, ShenandoahHeapRegion::region_size_bytes_shift_jint());
    __ add(t1, t1, t0);
    __ lbu(t1, Address(t1));
    __ test_bit(t0, t1, 0);
    __ beqz(t0, not_cset);
  }

  // Slow-path call
  __ enter();
  __ push_call_clobbered_registers();
  address target = nullptr;
  if (is_strong) {
    if (is_narrow) {
      target = CAST_FROM_FN_PTR(address, ShenandoahRuntime::load_reference_barrier_strong_narrow);
    } else {
      target = CAST_FROM_FN_PTR(address, ShenandoahRuntime::load_reference_barrier_strong);
    }
  } else if (is_weak) {
    if (is_narrow) {
      target = CAST_FROM_FN_PTR(address, ShenandoahRuntime::load_reference_barrier_weak_narrow);
    } else {
      target = CAST_FROM_FN_PTR(address, ShenandoahRuntime::load_reference_barrier_weak);
    }
  } else {
    assert(is_phantom, "only remaining strength");
    assert(!is_narrow, "phantom access cannot be narrow");
    target = CAST_FROM_FN_PTR(address, ShenandoahRuntime::load_reference_barrier_phantom);
  }
  // Calling with super_call_VM_leaf with c_rarg0/1 bypasses interpreter checks and avoids any moves.
  __ super_call_VM_leaf(target, c_rarg0, c_rarg1);
  __ mv(t0, x10);
  __ pop_call_clobbered_registers();
  __ mv(x10, t0);
  __ leave();

  __ bind(not_cset);
  __ mv(result_dst, x10);
  __ pop_reg(saved_regs, sp);

  __ bind(heap_stable);
}

//
// Arguments:
//
// Inputs:
//   src:        oop location to load from, might be clobbered
//
// Output:
//   dst:        oop loaded from src location
//
// Kill:
//   x30 (tmp reg)
//
// Alias:
//   dst: x30 (might use x30 as temporary output register to avoid clobbering src)
//
void ShenandoahBarrierSetAssembler::load_at(MacroAssembler* masm,
                                            DecoratorSet decorators,
                                            BasicType type,
                                            Register dst,
                                            Address src,
                                            Register tmp1,
                                            Register tmp2) {
  // 1: non-reference load, no additional barrier is needed
  if (!is_reference_type(type)) {
    BarrierSetAssembler::load_at(masm, decorators, type, dst, src, tmp1, tmp2);
    return;
  }

  // 2: load a reference from src location and apply LRB if needed
  if (ShenandoahBarrierSet::need_load_reference_barrier(decorators, type)) {
    Register result_dst = dst;

    // Preserve src location for LRB
    RegSet saved_regs;
    if (dst == src.base()) {
      dst = (src.base() == x28) ? x29 : x28;
      saved_regs = RegSet::of(dst);
      __ push_reg(saved_regs, sp);
    }
    assert_different_registers(dst, src.base());

    BarrierSetAssembler::load_at(masm, decorators, type, dst, src, tmp1, tmp2);

    load_reference_barrier(masm, dst, src, decorators);

    if (dst != result_dst) {
      __ mv(result_dst, dst);
      dst = result_dst;
    }

    if (saved_regs.bits() != 0) {
      __ pop_reg(saved_regs, sp);
    }
  } else {
    BarrierSetAssembler::load_at(masm, decorators, type, dst, src, tmp1, tmp2);
  }

  // 3: apply keep-alive barrier if needed
  if (ShenandoahBarrierSet::need_keep_alive_barrier(decorators, type)) {
    satb_barrier(masm /* masm */,
                 noreg /* obj */,
                 dst /* pre_val */,
                 xthread /* thread */,
                 tmp1 /* tmp1 */,
                 tmp2 /* tmp2 */);
  }
}

void ShenandoahBarrierSetAssembler::card_barrier(MacroAssembler* masm, Register obj) {
  assert(ShenandoahCardBarrier, "Should have been checked by caller");

  __ srli(obj, obj, CardTable::card_shift());

  assert(CardTable::dirty_card_val() == 0, "must be");

  Address curr_ct_holder_addr(xthread, in_bytes(ShenandoahThreadLocalData::card_table_offset()));
  __ ld(t1, curr_ct_holder_addr);
  __ add(t1, obj, t1);

  if (UseCondCardMark) {
    Label L_already_dirty;
    __ lbu(t0, Address(t1));
    __ beqz(t0, L_already_dirty);
    __ sb(zr, Address(t1));
    __ bind(L_already_dirty);
  } else {
    __ sb(zr, Address(t1));
  }
}

void ShenandoahBarrierSetAssembler::store_at(MacroAssembler* masm, DecoratorSet decorators, BasicType type,
                                             Address dst, Register val, Register tmp1, Register tmp2, Register tmp3) {
  // 1: non-reference types require no barriers
  if (!is_reference_type(type)) {
    BarrierSetAssembler::store_at(masm, decorators, type, dst, val, tmp1, tmp2, tmp3);
    return;
  }

  // Flatten object address right away for simplicity: likely needed by barriers
  if (dst.offset() == 0) {
    if (dst.base() != tmp3) {
      __ mv(tmp3, dst.base());
    }
  } else {
    __ la(tmp3, dst);
  }

  // 2: pre-barrier: SATB needs the previous value
  if (ShenandoahBarrierSet::need_satb_barrier(decorators, type)) {
    satb_barrier(masm,
                 tmp3 /* obj */,
                 tmp2 /* pre_val */,
                 xthread /* thread */,
                 tmp1 /* tmp */,
                 t0 /* tmp2 */);
  }

  // Store!
  BarrierSetAssembler::store_at(masm, decorators, type, Address(tmp3, 0), val, noreg, noreg, noreg);

  // 3: post-barrier: card barrier needs store address
  bool storing_non_null = (val != noreg);
  if (ShenandoahBarrierSet::need_card_barrier(decorators, type) && storing_non_null) {
    card_barrier(masm, tmp3);
  }
}

void ShenandoahBarrierSetAssembler::try_resolve_jobject_in_native(MacroAssembler* masm, Register jni_env,
                                                                  Register obj, Register tmp, Label& slowpath) {
  Label done;
  // Resolve jobject
  BarrierSetAssembler::try_resolve_jobject_in_native(masm, jni_env, obj, tmp, slowpath);

  // Check for null.
  __ beqz(obj, done);

  assert(obj != t1, "need t1");
  Address gc_state(jni_env, ShenandoahThreadLocalData::gc_state_offset() - JavaThread::jni_environment_offset());
  __ lbu(t1, gc_state);

  // Check for heap in evacuation phase
  __ test_bit(t0, t1, ShenandoahHeap::EVACUATION_BITPOS);
  __ bnez(t0, slowpath);

  __ bind(done);
}

void ShenandoahBarrierSetAssembler::try_peek_weak_handle_in_nmethod(MacroAssembler *masm, Register weak_handle,
                                                                    Register obj, Register tmp, Label& slow_path) {
  assert_different_registers(weak_handle, tmp, noreg);
  assert_different_registers(obj, tmp, noreg);


  Label done;

  // Peek weak handle using the standard implementation.
  BarrierSetAssembler::try_peek_weak_handle_in_nmethod(masm, weak_handle, obj, tmp, slow_path);

  // Check if the reference is null, and if it is, take the fast path.
  __ beqz(obj, done);

  Address gc_state(xthread, ShenandoahThreadLocalData::gc_state_offset());
  __ lbu(tmp, gc_state);

  // Check if the heap is under weak-reference/roots processing, in
  // which case we need to take the slow path.
  __ test_bit(tmp, tmp, ShenandoahHeap::WEAK_ROOTS_BITPOS);
  __ bnez(tmp, slow_path);
  __ bind(done);
}

// Special Shenandoah CAS implementation that handles false negatives due
// to concurrent evacuation.  The service is more complex than a
// traditional CAS operation because the CAS operation is intended to
// succeed if the reference at addr exactly matches expected or if the
// reference at addr holds a pointer to a from-space object that has
// been relocated to the location named by expected.  There are two
// races that must be addressed:
//  a) A parallel thread may mutate the contents of addr so that it points
//     to a different object.  In this case, the CAS operation should fail.
//  b) A parallel thread may heal the contents of addr, replacing a
//     from-space pointer held in addr with the to-space pointer
//     representing the new location of the object.
// Upon entry to cmpxchg_oop, it is assured that new_val equals null
// or it refers to an object that is not being evacuated out of
// from-space, or it refers to the to-space version of an object that
// is being evacuated out of from-space.
//
// By default the value held in the result register following execution
// of the generated code sequence is 0 to indicate failure of CAS,
// non-zero to indicate success. If is_cae, the result is the value most
// recently fetched from addr rather than a boolean success indicator.
//
// Clobbers t0, t1
void ShenandoahBarrierSetAssembler::cmpxchg_oop(MacroAssembler* masm,
                                                Register addr,
                                                Register expected,
                                                Register new_val,
                                                Assembler::Aqrl acquire,
                                                Assembler::Aqrl release,
                                                bool is_cae,
                                                Register result) {
  bool is_narrow = UseCompressedOops;
  Assembler::operand_size size = is_narrow ? Assembler::uint32 : Assembler::int64;

  assert_different_registers(addr, expected, t0, t1);
  assert_different_registers(addr, new_val, t0, t1);

  Label retry, success, fail, done;

  __ bind(retry);

  // Step1: Try to CAS.
  __ cmpxchg(addr, expected, new_val, size, acquire, release, /* result */ t1);

  // If success, then we are done.
  __ beq(expected, t1, success);

  // Step2: CAS failed, check the forwarded pointer.
  __ mv(t0, t1);

  if (is_narrow) {
    __ decode_heap_oop(t0, t0);
  }
  resolve_forward_pointer(masm, t0);

  __ encode_heap_oop(t0, t0);

  // Report failure when the forwarded oop was not expected.
  __ bne(t0, expected, fail);

  // Step 3: CAS again using the forwarded oop.
  __ cmpxchg(addr, t1, new_val, size, acquire, release, /* result */ t0);

  // Retry when failed.
  __ bne(t0, t1, retry);

  __ bind(success);
  if (is_cae) {
    __ mv(result, expected);
  } else {
    __ mv(result, 1);
  }
  __ j(done);

  __ bind(fail);
  if (is_cae) {
    __ mv(result, t0);
  } else {
    __ mv(result, zr);
  }

  __ bind(done);
}

void ShenandoahBarrierSetAssembler::gen_write_ref_array_post_barrier(MacroAssembler* masm, DecoratorSet decorators,
                                                                     Register start, Register count, Register tmp) {
  assert(ShenandoahCardBarrier, "Did you mean to enable ShenandoahCardBarrier?");

  Label L_loop, L_done;
  const Register end = count;

  // Zero count? Nothing to do.
  __ beqz(count, L_done);

  // end = start + count << LogBytesPerHeapOop
  // last element address to make inclusive
  __ shadd(end, count, start, tmp, LogBytesPerHeapOop);
  __ subi(end, end, BytesPerHeapOop);
  __ srli(start, start, CardTable::card_shift());
  __ srli(end, end, CardTable::card_shift());

  // number of bytes to copy
  __ sub(count, end, start);

  Address curr_ct_holder_addr(xthread, in_bytes(ShenandoahThreadLocalData::card_table_offset()));
  __ ld(tmp, curr_ct_holder_addr);
  __ add(start, start, tmp);

  __ bind(L_loop);
  __ add(tmp, start, count);
  __ sb(zr, Address(tmp));
  __ subi(count, count, 1);
  __ bgez(count, L_loop);
  __ bind(L_done);
}

#undef __

#ifdef COMPILER1

#define __ ce->masm()->

void ShenandoahBarrierSetAssembler::gen_pre_barrier_stub(LIR_Assembler* ce, ShenandoahPreBarrierStub* stub) {
  ShenandoahBarrierSetC1* bs = (ShenandoahBarrierSetC1*)BarrierSet::barrier_set()->barrier_set_c1();
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

void ShenandoahBarrierSetAssembler::gen_load_reference_barrier_stub(LIR_Assembler* ce,
                                                                    ShenandoahLoadReferenceBarrierStub* stub) {
  ShenandoahBarrierSetC1* bs = (ShenandoahBarrierSetC1*)BarrierSet::barrier_set()->barrier_set_c1();
  __ bind(*stub->entry());

  DecoratorSet decorators = stub->decorators();
  bool is_strong  = ShenandoahBarrierSet::is_strong_access(decorators);
  bool is_weak    = ShenandoahBarrierSet::is_weak_access(decorators);
  bool is_phantom = ShenandoahBarrierSet::is_phantom_access(decorators);
  bool is_native  = ShenandoahBarrierSet::is_native_access(decorators);

  Register obj = stub->obj()->as_register();
  Register res = stub->result()->as_register();
  Register addr = stub->addr()->as_pointer_register();
  Register tmp1 = stub->tmp1()->as_register();
  Register tmp2 = stub->tmp2()->as_register();

  assert(res == x10, "result must arrive in x10");
  assert_different_registers(tmp1, tmp2, t0);

  if (res != obj) {
    __ mv(res, obj);
  }

  if (is_strong) {
    // Check for object in cset.
    __ mv(tmp2, ShenandoahHeap::in_cset_fast_test_addr());
    __ srli(tmp1, res, ShenandoahHeapRegion::region_size_bytes_shift_jint());
    __ add(tmp2, tmp2, tmp1);
    __ lbu(tmp2, Address(tmp2));
    __ beqz(tmp2, *stub->continuation(), true /* is_far */);
  }

  ce->store_parameter(res, 0);
  ce->store_parameter(addr, 1);

  if (is_strong) {
    if (is_native) {
      __ far_call(RuntimeAddress(bs->load_reference_barrier_strong_native_rt_code_blob()->code_begin()));
    } else {
      __ far_call(RuntimeAddress(bs->load_reference_barrier_strong_rt_code_blob()->code_begin()));
    }
  } else if (is_weak) {
    __ far_call(RuntimeAddress(bs->load_reference_barrier_weak_rt_code_blob()->code_begin()));
  } else {
    assert(is_phantom, "only remaining strength");
    __ far_call(RuntimeAddress(bs->load_reference_barrier_phantom_rt_code_blob()->code_begin()));
  }

  __ j(*stub->continuation());
}

#undef __

#define __ sasm->

void ShenandoahBarrierSetAssembler::generate_c1_pre_barrier_runtime_stub(StubAssembler* sasm) {
  __ prologue("shenandoah_pre_barrier", false);

  // arg0 : previous value of memory

  BarrierSet* bs = BarrierSet::barrier_set();

  const Register pre_val = x10;
  const Register thread = xthread;
  const Register tmp = t0;

  Address queue_index(thread, in_bytes(ShenandoahThreadLocalData::satb_mark_queue_index_offset()));
  Address buffer(thread, in_bytes(ShenandoahThreadLocalData::satb_mark_queue_buffer_offset()));

  Label done;
  Label runtime;

  // Is marking still active?
  Address gc_state(thread, in_bytes(ShenandoahThreadLocalData::gc_state_offset()));
  __ lb(tmp, gc_state);
  __ test_bit(tmp, tmp, ShenandoahHeap::MARKING_BITPOS);
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
  __ call_VM_leaf(CAST_FROM_FN_PTR(address, ShenandoahRuntime::write_barrier_pre), pre_val);
  __ pop_call_clobbered_registers();
  __ bind(done);

  __ epilogue();
}

void ShenandoahBarrierSetAssembler::generate_c1_load_reference_barrier_runtime_stub(StubAssembler* sasm,
                                                                                    DecoratorSet decorators) {
  __ prologue("shenandoah_load_reference_barrier", false);
  // arg0 : object to be resolved

  __ push_call_clobbered_registers();
  __ load_parameter(0, x10);
  __ load_parameter(1, x11);

  bool is_strong  = ShenandoahBarrierSet::is_strong_access(decorators);
  bool is_weak    = ShenandoahBarrierSet::is_weak_access(decorators);
  bool is_phantom = ShenandoahBarrierSet::is_phantom_access(decorators);
  bool is_native  = ShenandoahBarrierSet::is_native_access(decorators);
  address target  = nullptr;
  if (is_strong) {
    if (is_native) {
      target = CAST_FROM_FN_PTR(address, ShenandoahRuntime::load_reference_barrier_strong);
    } else {
      if (UseCompressedOops) {
        target = CAST_FROM_FN_PTR(address, ShenandoahRuntime::load_reference_barrier_strong_narrow);
      } else {
        target = CAST_FROM_FN_PTR(address, ShenandoahRuntime::load_reference_barrier_strong);
      }
    }
  } else if (is_weak) {
    assert(!is_native, "weak must not be called off-heap");
    if (UseCompressedOops) {
      target = CAST_FROM_FN_PTR(address, ShenandoahRuntime::load_reference_barrier_weak_narrow);
    } else {
      target = CAST_FROM_FN_PTR(address, ShenandoahRuntime::load_reference_barrier_weak);
    }
  } else {
    assert(is_phantom, "only remaining strength");
    assert(is_native, "phantom must only be called off-heap");
    target = CAST_FROM_FN_PTR(address, ShenandoahRuntime::load_reference_barrier_phantom);
  }
  __ rt_call(target);
  __ mv(t0, x10);
  __ pop_call_clobbered_registers();
  __ mv(x10, t0);

  __ epilogue();
}

#undef __

#endif // COMPILER1

#ifdef COMPILER2

#undef __
#define __ masm->

void ShenandoahBarrierSetAssembler::load_c2(const MachNode* node, MacroAssembler* masm, Register dst, Address src, Register tmp1, Register tmp2, bool is_narrow) {
  // Do the actual load. This load is the candidate for implicit null check, and MUST come first.
  if (is_narrow) {
    __ lwu(dst, src);
  } else {
    __ ld(dst, src);
  }

  ShenandoahBarrierStubC2::load_post(masm, node, dst, src, tmp1, tmp2, is_narrow);
}

void ShenandoahBarrierSetAssembler::store_c2(const MachNode* node, MacroAssembler* masm, Address dst, bool dst_narrow,
    Register src, bool src_narrow, Register tmp1, Register tmp2, Register tmp3) {

  ShenandoahBarrierStubC2::store_pre(masm, node, dst, tmp1, tmp2, tmp3, dst_narrow);

  // Do the actual store
  if (dst_narrow) {
    if (!src_narrow) {
      // Need to encode into tmp, because we cannot clobber src.
      assert(tmp1 != noreg, "need temp register");
      if ((node->barrier_data() & ShenandoahBitNotNull) == 0) {
        __ encode_heap_oop(tmp1, src);
      } else {
        __ encode_heap_oop_not_null(tmp1, src);
      }
      src = tmp1;
    }
    __ sw(src, dst);
  } else {
    __ sd(src, dst);
  }

  ShenandoahBarrierStubC2::store_post(masm, node, dst, tmp2, tmp3);
}

void ShenandoahBarrierSetAssembler::compare_and_set_c2(const MachNode* node, MacroAssembler* masm, Register res, Register addr,
    Register oldval, Register newval, Register tmp1, Register tmp2, Register tmp3, bool exchange, bool narrow, bool is_acquire) {
  const Assembler::Aqrl acquire = is_acquire ? Assembler::aq : Assembler::relaxed;
  const Assembler::Aqrl release = Assembler::rl;
  const Assembler::operand_size size = narrow ? Assembler::uint32 : Assembler::int64;

  ShenandoahBarrierStubC2::load_store_pre(masm, node, Address(addr), tmp1, tmp2, tmp3, narrow);

  // CAS!
  __ cmpxchg(addr, oldval, newval, size, acquire, release, /* result */ res, !exchange /* result_as_bool */);

  ShenandoahBarrierStubC2::load_store_post(masm, node, Address(addr, 0), tmp2, tmp3);
}

void ShenandoahBarrierSetAssembler::get_and_set_c2(const MachNode* node, MacroAssembler* masm, Register preval,
    Register newval, Register addr, Register tmp1, Register tmp2, Register tmp3, bool is_acquire) {
  const bool is_narrow = node->bottom_type()->isa_narrowoop();

  ShenandoahBarrierStubC2::load_store_pre(masm, node, Address(addr, 0), tmp1, tmp2, tmp3, is_narrow);

  if (is_narrow) {
    if (is_acquire) {
      __ atomic_xchgalwu(preval, newval, addr);
    } else {
      __ atomic_xchgwu(preval, newval, addr);
    }
  } else {
    if (is_acquire) {
      __ atomic_xchgal(preval, newval, addr);
    } else {
      __ atomic_xchg(preval, newval, addr);
    }
  }

  ShenandoahBarrierStubC2::load_store_post(masm, node, Address(addr, 0), tmp2, tmp3);
}

#undef __
#define __ masm.

void ShenandoahBarrierStubC2::cardtable(MacroAssembler& masm, Address address, Register tmp1, Register tmp2) {
  assert(CardTable::dirty_card_val() == 0, "must be");
  Assembler::InlineSkippedInstructionsCounter skip_counter(&masm);

  // tmp1 = card table base (holder)
  Address curr_ct_holder_addr(xthread, in_bytes(ShenandoahThreadLocalData::card_table_offset()));
  __ ld(tmp1, curr_ct_holder_addr);

  // tmp1 = effective address
  __ la(tmp2, address);

  // tmp2 = &card_table[ addr >> CardTable::card_shift() ] ; card index
  __ srli(tmp2, tmp2, CardTable::card_shift());
  __ add(tmp2, tmp2, tmp1);

  if (UseCondCardMark) {
    Label L_already_dirty;
    __ lbu(tmp1, Address(tmp2));
    __ beqz(tmp1, L_already_dirty);
    __ sb(zr, Address(tmp2));
    __ bind(L_already_dirty);
  } else {
    __ sb(zr, Address(tmp2));
  }
}

void ShenandoahBarrierStubC2::enter_if_gc_state(MacroAssembler& masm, const char test_state, Register tmp) {
  Assembler::InlineSkippedInstructionsCounter skip_counter(&masm);

  Address gc_state_fast(xthread, in_bytes(ShenandoahThreadLocalData::gc_state_fast_array_offset(test_state)));
  __ lbu(tmp, gc_state_fast);
  __ beqz(tmp, *continuation());
  __ j(*entry());

  // This is were the slowpath stub will return to or the code above will
  // jump to if the checks are false
  __ bind(*continuation());
}

void ShenandoahBarrierStubC2::emit_code(MacroAssembler& masm) {
  Assembler::InlineSkippedInstructionsCounter skip_counter(&masm);
  assert(_needs_keep_alive_barrier || _needs_load_ref_barrier, "Why are you here?");

  __ bind(*entry());

  // If we need to load ourselves, do it here.
  if (_do_load) {
    if (_narrow) {
      __ lwu(_obj, _addr);
    } else {
      __ ld(_obj, _addr);
    }
  }

  // If the object is null, there is no point in applying barriers.
  maybe_far_jump_if_zero(masm, _obj);

  // We need to make sure that loads done by callers survive across slow-path calls.
  // For self-loads, we need to care about the case when both KA and LRB are enabled (rare).
  bool needs_both_barriers = _needs_keep_alive_barrier && _needs_load_ref_barrier;
  if (!_do_load || needs_both_barriers) {
    preserve(_obj);
  }

  // Go for barriers. Barriers can return straight to continuation, as long
  // as another barrier is not needed and we can reach the fastpath.
  if (needs_both_barriers) {
    keepalive(masm, nullptr);
    lrb(masm);
  } else if (_needs_keep_alive_barrier) {
    keepalive(masm, continuation());
  } else if (_needs_load_ref_barrier) {
    lrb(masm);
  } else {
    ShouldNotReachHere();
  }
}

void ShenandoahBarrierStubC2::maybe_far_jump_if_zero(MacroAssembler& masm, Register reg) {
  Label L_short_jump;
  __ bnez(reg, L_short_jump);
  __ j(*continuation());
  __ bind(L_short_jump);
}

void ShenandoahBarrierStubC2::keepalive(MacroAssembler& masm, Label* L_done) {
  Address index(xthread, in_bytes(ShenandoahThreadLocalData::satb_mark_queue_index_offset()));
  Address buffer(xthread, in_bytes(ShenandoahThreadLocalData::satb_mark_queue_buffer_offset()));
  Label L_through, L_slowpath;

  // If another barrier is enabled as well, do a runtime check for a specific barrier.
  if (_needs_load_ref_barrier) {
    assert(L_done == nullptr, "L_done is always null when _needs_load_ref_barrier is true");
    Address gc_state_fast(xthread, in_bytes(ShenandoahThreadLocalData::gc_state_fast_array_offset(ShenandoahHeap::MARKING)));
    __ lbu(_tmp1, gc_state_fast);
    __ beqz(_tmp1, L_through);
  }

  // Fast-path: put object into buffer.
  // If buffer is already full, go slow.
  __ ld(_tmp1, index);
  __ beqz(_tmp1, L_slowpath);
  __ subi(_tmp1, _tmp1, wordSize);
  __ sd(_tmp1, index);
  __ ld(_tmp2, buffer);

  // Store the object in queue.
  // If object is narrow, we need to decode it before inserting.
  __ add(_tmp1, _tmp1, _tmp2);
  if (_narrow) {
    __ decode_heap_oop_not_null(_tmp2, _obj);
    __ sd(_tmp2, Address(_tmp1));
  } else {
    __ sd(_obj, Address(_tmp1));
  }

  // Fast-path exits here.
  if (L_done != nullptr) {
    __ j(*L_done);
  } else {
    __ j(L_through);
  }

  // Slow-path: call runtime to handle.
  __ bind(L_slowpath);

  {
    SaveLiveRegisters slr(&masm, this);

    // Go to runtime and handle the rest there.
    __ mv(c_rarg0, _obj);
    __ la(ra, RuntimeAddress(keepalive_runtime_entry_addr()));
    __ jalr(ra);
  }
  if (L_done != nullptr) {
    __ j(*L_done);
  } else {
    __ bind(L_through);
  }
}

void ShenandoahBarrierStubC2::lrb(MacroAssembler& masm) {
  Label L_slow;

  // If another barrier is enabled as well, do a runtime check for a specific barrier.
  if (_needs_keep_alive_barrier) {
    char state_to_check = ShenandoahHeap::HAS_FORWARDED | (_needs_load_ref_weak_barrier ? ShenandoahHeap::WEAK_ROOTS : 0);
    Address gc_state_fast(xthread, in_bytes(ShenandoahThreadLocalData::gc_state_fast_array_offset(state_to_check)));
    __ lbu(_tmp1, gc_state_fast);
    maybe_far_jump_if_zero(masm, _tmp1);
  }

  // If weak references are being processed, weak/phantom loads need to go slow,
  // regardless of their cset status.
  if (_needs_load_ref_weak_barrier) {
    Address gc_state_fast(xthread, in_bytes(ShenandoahThreadLocalData::gc_state_fast_array_offset(ShenandoahHeap::WEAK_ROOTS)));
    __ lbu(_tmp1, gc_state_fast);
    __ bnez(_tmp1, L_slow);
  }

  // Cset-check. Fall-through to slow if in collection set.
  if (_narrow) {
    __ decode_heap_oop_not_null(_tmp2, _obj);
  } else {
    __ mv(_tmp2, _obj);
  }

  __ mv(_tmp1, ShenandoahHeap::in_cset_fast_test_addr());
  __ srli(_tmp2, _tmp2, ShenandoahHeapRegion::region_size_bytes_shift_jint());
  __ add(_tmp1, _tmp1, _tmp2);
  __ lbu(_tmp1, Address(_tmp1, 0));
  maybe_far_jump_if_zero(masm, _tmp1);

  // Slow path
  __ bind(L_slow);

  // Obj is the result, need to temporarily stop preserving it.
  bool is_obj_preserved = is_preserved(_obj);
  if (is_obj_preserved) {
    dont_preserve(_obj);
  }
  {
    SaveLiveRegisters slr(&masm, this);

    // Shuffle in the arguments. The end result should be:
    //   c_rarg0 <- obj
    //   c_rarg1 <- lea(addr)
    if (c_rarg0 == _obj) {
      __ la(c_rarg1, _addr);
    } else if (c_rarg1 == _obj) {
      // Set up arguments in reverse, and then flip them
      __ la(c_rarg0, _addr);
      // flip them
      __ mv(_tmp1, c_rarg0);
      __ mv(c_rarg0, c_rarg1);
      __ mv(c_rarg1, _tmp1);
    } else {
      assert_different_registers(c_rarg1, _obj);
      __ la(c_rarg1, _addr);
      __ mv(c_rarg0, _obj);
    }

    // Go to runtime and handle the rest there.
    __ la(ra, RuntimeAddress(lrb_runtime_entry_addr()));
    __ jalr(ra);

    // Save the result where needed. Narrow entries return narrowOop (32 bits)
    // we need to zero the upper 32 bits of x10.
    if (_narrow) {
      __ zext_w(_obj, x10);
    } else {
      __ mv(_obj, x10);
    }
  }
  if (is_obj_preserved) {
    preserve(_obj);
  }

  __ j(*continuation());
}

int ShenandoahBarrierStubC2::available_gp_registers() {
  Unimplemented(); // Not used
  return 0;
}

bool ShenandoahBarrierStubC2::is_special_register(Register r) {
  Unimplemented(); // Not used
  return true;
}

void ShenandoahBarrierStubC2::post_init() {
  // Do nothing.
}

#endif // COMPILER2
