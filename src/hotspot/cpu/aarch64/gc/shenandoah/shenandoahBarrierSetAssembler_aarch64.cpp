/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2018, 2022, Red Hat, Inc. All rights reserved.
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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
      __ cbz(count, done);

      // Is GC active?
      assert(!saved_regs.contains(rscratch1), "Sanity: about to clobber rscratch1");
      assert(!saved_regs.contains(rscratch2), "Sanity: about to clobber rscratch2");
      Address gc_state(rthread, in_bytes(ShenandoahThreadLocalData::gc_state_offset()));
      __ ldrb(rscratch1, gc_state);
      if (ShenandoahSATBBarrier && dest_uninitialized) {
        __ tbz(rscratch1, ShenandoahHeap::HAS_FORWARDED_BITPOS, done);
      } else {
        __ mov(rscratch2, ShenandoahHeap::HAS_FORWARDED | ShenandoahHeap::MARKING);
        __ tst(rscratch1, rscratch2);
        __ br(Assembler::EQ, done);
      }

      __ push_call_clobbered_registers();
      // If arguments are not in proper places, shuffle them.
      // Doing this via the stack is the most straight-forward way to avoid
      // accidentally smashing any register.
      if (c_rarg0 != src || c_rarg1 != dst || c_rarg2 != count) {
        __ push(RegSet::of(src), sp);
        __ push(RegSet::of(dst), sp);
        __ push(RegSet::of(count), sp);
        __ pop(RegSet::of(c_rarg2), sp);
        __ pop(RegSet::of(c_rarg1), sp);
        __ pop(RegSet::of(c_rarg0), sp);
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
  assert(thread == rthread, "must be");

  Label done;
  Label runtime;

  assert_different_registers(obj, pre_val, tmp1, tmp2);
  assert(pre_val != noreg && tmp1 != noreg && tmp2 != noreg, "expecting a register");

  Address index(thread, in_bytes(ShenandoahThreadLocalData::satb_mark_queue_index_offset()));
  Address buffer(thread, in_bytes(ShenandoahThreadLocalData::satb_mark_queue_buffer_offset()));

  // Is marking active?
  Address gc_state(thread, in_bytes(ShenandoahThreadLocalData::gc_state_offset()));
  __ ldrb(tmp1, gc_state);
  __ tbz(tmp1, ShenandoahHeap::MARKING_BITPOS, done);

  // Do we need to load the previous value?
  if (obj != noreg) {
    if (UseCompressedOops) {
      __ ldrw(pre_val, Address(obj, 0));
      __ decode_heap_oop(pre_val);
    } else {
      __ ldr(pre_val, Address(obj, 0));
    }
  }

  // Is the previous value null?
  __ cbz(pre_val, done);

  // Can we store original value in the thread's buffer?
  // Is index == 0?
  // (The index field is typed as size_t.)

  __ ldr(tmp1, index);                      // tmp := *index_adr
  __ cbz(tmp1, runtime);                    // tmp == 0?
                                        // If yes, goto runtime

  __ sub(tmp1, tmp1, wordSize);             // tmp := tmp - wordSize
  __ str(tmp1, index);                      // *index_adr := tmp
  __ ldr(tmp2, buffer);
  __ add(tmp1, tmp1, tmp2);                 // tmp := tmp + *buffer_adr

  // Record the previous value
  __ str(pre_val, Address(tmp1, 0));
  __ b(done);

  __ bind(runtime);

  // Slow-path call
  __ enter(/* strip_ret_addr = */ true);
  __ push_call_clobbered_registers();
  if (c_rarg0 != pre_val) {
    __ mov(c_rarg0, pre_val);
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
  __ cbz(dst, is_null);
  resolve_forward_pointer_not_null(masm, dst, tmp);
  __ bind(is_null);
}

// IMPORTANT: This must preserve all registers, even rscratch1 and rscratch2, except those explicitly
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

  bool borrow_reg = (tmp == noreg);
  if (borrow_reg) {
    // No free registers available. Make one useful.
    tmp = rscratch1;
    if (tmp == dst) {
      tmp = rscratch2;
    }
    __ push(RegSet::of(tmp), sp);
  }

  assert_different_registers(tmp, dst);

  Label done;
  __ ldr(tmp, Address(dst, oopDesc::mark_offset_in_bytes()));
  __ eon(tmp, tmp, zr);
  __ ands(zr, tmp, markWord::lock_mask_in_place);
  __ br(Assembler::NE, done);
  __ orr(tmp, tmp, markWord::marked_value);
  __ eon(dst, tmp, zr);
  __ bind(done);

  if (borrow_reg) {
    __ pop(RegSet::of(tmp), sp);
  }
}

void ShenandoahBarrierSetAssembler::load_reference_barrier(MacroAssembler* masm, Register dst, Address load_addr, DecoratorSet decorators) {
  assert(ShenandoahLoadRefBarrier, "Should be enabled");
  assert(dst != rscratch2, "need rscratch2");
  assert_different_registers(load_addr.base(), load_addr.index(), rscratch1, rscratch2);

  bool is_strong  = ShenandoahBarrierSet::is_strong_access(decorators);
  bool is_weak    = ShenandoahBarrierSet::is_weak_access(decorators);
  bool is_phantom = ShenandoahBarrierSet::is_phantom_access(decorators);
  bool is_native  = ShenandoahBarrierSet::is_native_access(decorators);
  bool is_narrow  = UseCompressedOops && !is_native;

  Label heap_stable, not_cset;
  Address gc_state(rthread, in_bytes(ShenandoahThreadLocalData::gc_state_offset()));
  __ ldrb(rscratch2, gc_state);

  // Check for heap stability
  if (is_strong) {
    __ tbz(rscratch2, ShenandoahHeap::HAS_FORWARDED_BITPOS, heap_stable);
  } else {
    Label lrb;
    __ tbnz(rscratch2, ShenandoahHeap::WEAK_ROOTS_BITPOS, lrb);
    __ tbz(rscratch2, ShenandoahHeap::HAS_FORWARDED_BITPOS, heap_stable);
    __ bind(lrb);
  }

  // use r1 for load address
  Register result_dst = dst;
  if (dst == r1) {
    __ mov(rscratch1, dst);
    dst = rscratch1;
  }

  // Save r0 and r1, unless it is an output register
  RegSet to_save = RegSet::of(r0, r1) - result_dst;
  __ push(to_save, sp);
  __ lea(r1, load_addr);
  __ mov(r0, dst);

  // Test for in-cset
  if (is_strong) {
    if (AOTCodeCache::is_on_for_dump()) {
      __ lea(rscratch2, ExternalAddress(AOTRuntimeConstants::cset_base_address()));
      __ ldr(rscratch2, Address(rscratch2));
      __ lea(rscratch1, ExternalAddress(AOTRuntimeConstants::grain_shift_address()));
      __ ldrw(rscratch1, Address(rscratch1));
      __ lsrv(rscratch1, r0, rscratch1);
    } else {
      __ mov(rscratch2, ShenandoahHeap::in_cset_fast_test_addr());
      __ lsr(rscratch1, r0, ShenandoahHeapRegion::region_size_bytes_shift_jint());
    }
    __ ldrb(rscratch2, Address(rscratch2, rscratch1));
    __ tbz(rscratch2, 0, not_cset);
  }

  // Slow-path call
  __ enter(/* strip_ret_addr = */ true);
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
  __ mov(rscratch1, r0);
  __ pop_call_clobbered_registers();
  __ mov(r0, rscratch1);
  __ leave();

  __ bind(not_cset);

  __ mov(result_dst, r0);
  __ pop(to_save, sp);

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
//   rscratch1 (scratch reg)
//
// Alias:
//   dst: rscratch1 (might use rscratch1 as temporary output register to avoid clobbering src)
//
void ShenandoahBarrierSetAssembler::load_at(MacroAssembler* masm, DecoratorSet decorators, BasicType type,
                                            Register dst, Address src, Register tmp1, Register tmp2) {
  // 1: non-reference load, no additional barrier is needed
  if (!is_reference_type(type)) {
    BarrierSetAssembler::load_at(masm, decorators, type, dst, src, tmp1, tmp2);
    return;
  }

  // 2: load a reference from src location and apply LRB if needed
  if (ShenandoahBarrierSet::need_load_reference_barrier(decorators, type)) {
    Register result_dst = dst;

    // Preserve src location for LRB
    if (dst == src.base() || dst == src.index()) {
      dst = rscratch1;
    }
    assert_different_registers(dst, src.base(), src.index());

    BarrierSetAssembler::load_at(masm, decorators, type, dst, src, tmp1, tmp2);

    load_reference_barrier(masm, dst, src, decorators);

    if (dst != result_dst) {
      __ mov(result_dst, dst);
      dst = result_dst;
    }
  } else {
    BarrierSetAssembler::load_at(masm, decorators, type, dst, src, tmp1, tmp2);
  }

  // 3: apply keep-alive barrier if needed
  if (ShenandoahBarrierSet::need_keep_alive_barrier(decorators, type)) {
    satb_barrier(masm /* masm */,
                 noreg /* obj */,
                 dst /* pre_val */,
                 rthread /* thread */,
                 tmp1 /* tmp1 */,
                 tmp2 /* tmp2 */);
  }
}

void ShenandoahBarrierSetAssembler::card_barrier(MacroAssembler* masm, Register obj) {
  assert(ShenandoahCardBarrier, "Should have been checked by caller");

  __ lsr(obj, obj, CardTable::card_shift());

  assert(CardTable::dirty_card_val() == 0, "must be");

  Address curr_ct_holder_addr(rthread, in_bytes(ShenandoahThreadLocalData::card_table_offset()));
  __ ldr(rscratch1, curr_ct_holder_addr);

  if (UseCondCardMark) {
    Label L_already_dirty;
    __ ldrb(rscratch2, Address(obj, rscratch1));
    __ cbz(rscratch2, L_already_dirty);
    __ strb(zr, Address(obj, rscratch1));
    __ bind(L_already_dirty);
  } else {
    __ strb(zr, Address(obj, rscratch1));
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
  if (dst.index() == noreg && dst.offset() == 0) {
    if (dst.base() != tmp3) {
      __ mov(tmp3, dst.base());
    }
  } else {
    __ lea(tmp3, dst);
  }

  // 2: pre-barrier: SATB needs the previous value
  if (ShenandoahBarrierSet::need_satb_barrier(decorators, type)) {
    satb_barrier(masm,
                 tmp3 /* obj */,
                 tmp2 /* pre_val */,
                 rthread /* thread */,
                 tmp1 /* tmp */,
                 rscratch1 /* tmp2 */);
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
  __ cbz(obj, done);

  assert(obj != rscratch2, "need rscratch2");
  Address gc_state(jni_env, ShenandoahThreadLocalData::gc_state_offset() - JavaThread::jni_environment_offset());
  __ lea(rscratch2, gc_state);
  __ ldrb(rscratch2, Address(rscratch2));

  // Check for heap in evacuation phase
  __ tbnz(rscratch2, ShenandoahHeap::EVACUATION_BITPOS, slowpath);

  __ bind(done);
}

void ShenandoahBarrierSetAssembler::try_peek_weak_handle_in_nmethod(MacroAssembler* masm, Register weak_handle, Register obj,
                                                                    Register tmp, Label& slow_path) {
  assert_different_registers(weak_handle, tmp, noreg);
  assert_different_registers(obj, tmp, noreg);

  Label done;

  // Peek weak handle using the standard implementation.
  BarrierSetAssembler::try_peek_weak_handle_in_nmethod(masm, weak_handle, obj, tmp, slow_path);

  // Check if the reference is null, and if it is, take the fast path.
  __ cbz(obj, done);

  Address gc_state(rthread, ShenandoahThreadLocalData::gc_state_offset());
  __ lea(tmp, gc_state);
  __ ldrb(tmp, __ legitimize_address(gc_state, 1, tmp));

  // Check if the heap is under weak-reference/roots processing, in
  // which case we need to take the slow path.
  __ tbnz(tmp, ShenandoahHeap::WEAK_ROOTS_BITPOS, slow_path);
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
// Clobbers rscratch1, rscratch2
void ShenandoahBarrierSetAssembler::cmpxchg_oop(MacroAssembler* masm,
                                                Register addr,
                                                Register expected,
                                                Register new_val,
                                                bool acquire, bool release,
                                                bool is_cae,
                                                Register result) {
  Register tmp1 = rscratch1;
  Register tmp2 = rscratch2;
  bool is_narrow = UseCompressedOops;
  Assembler::operand_size size = is_narrow ? Assembler::word : Assembler::xword;

  assert_different_registers(addr, expected, tmp1, tmp2);
  assert_different_registers(addr, new_val,  tmp1, tmp2);

  Label step4, done;

  // There are two ways to reach this label.  Initial entry into the
  // cmpxchg_oop code expansion starts at step1 (which is equivalent
  // to label step4).  Additionally, in the rare case that four steps
  // are required to perform the requested operation, the fourth step
  // is the same as the first.  On a second pass through step 1,
  // control may flow through step 2 on its way to failure.  It will
  // not flow from step 2 to step 3 since we are assured that the
  // memory at addr no longer holds a from-space pointer.
  //
  // The comments that immediately follow the step4 label apply only
  // to the case in which control reaches this label by branch from
  // step 3.

  __ bind (step4);

  // Step 4. CAS has failed because the value most recently fetched
  // from addr is no longer the from-space pointer held in tmp2.  If a
  // different thread replaced the in-memory value with its equivalent
  // to-space pointer, then CAS may still be able to succeed.  The
  // value held in the expected register has not changed.
  //
  // It is extremely rare we reach this point.  For this reason, the
  // implementation opts for smaller rather than potentially faster
  // code.  Ultimately, smaller code for this rare case most likely
  // delivers higher overall throughput by enabling improved icache
  // performance.

  // Step 1. Fast-path.
  //
  // Try to CAS with given arguments.  If successful, then we are done.
  //
  // No label required for step 1.

  __ cmpxchg(addr, expected, new_val, size, acquire, release, false, tmp2);
  // EQ flag set iff success.  tmp2 holds value fetched.

  // If expected equals null but tmp2 does not equal null, the
  // following branches to done to report failure of CAS.  If both
  // expected and tmp2 equal null, the following branches to done to
  // report success of CAS.  There's no need for a special test of
  // expected equal to null.

  __ br(Assembler::EQ, done);
  // if CAS failed, fall through to step 2

  // Step 2. CAS has failed because the value held at addr does not
  // match expected.  This may be a false negative because the value fetched
  // from addr (now held in tmp2) may be a from-space pointer to the
  // original copy of same object referenced by to-space pointer expected.
  //
  // To resolve this, it suffices to find the forward pointer associated
  // with fetched value.  If this matches expected, retry CAS with new
  // parameters.  If this mismatches, then we have a legitimate
  // failure, and we're done.
  //
  // No need for step2 label.

  // overwrite tmp1 with from-space pointer fetched from memory
  __ mov(tmp1, tmp2);

  if (is_narrow) {
    // Decode tmp1 in order to resolve its forward pointer
    __ decode_heap_oop(tmp1, tmp1);
  }
  resolve_forward_pointer(masm, tmp1);
  // Encode tmp1 to compare against expected.
  __ encode_heap_oop(tmp1, tmp1);

  // Does forwarded value of fetched from-space pointer match original
  // value of expected?  If tmp1 holds null, this comparison will fail
  // because we know from step1 that expected is not null.  There is
  // no need for a separate test for tmp1 (the value originally held
  // in memory) equal to null.
  __ cmp(tmp1, expected);

  // If not, then the failure was legitimate and we're done.
  // Branching to done with NE condition denotes failure.
  __ br(Assembler::NE, done);

  // Fall through to step 3.  No need for step3 label.

  // Step 3.  We've confirmed that the value originally held in memory
  // (now held in tmp2) pointed to from-space version of original
  // expected value.  Try the CAS again with the from-space expected
  // value.  If it now succeeds, we're good.
  //
  // Note: tmp2 holds encoded from-space pointer that matches to-space
  // object residing at expected.  tmp2 is the new "expected".

  // Note that macro implementation of __cmpxchg cannot use same register
  // tmp2 for result and expected since it overwrites result before it
  // compares result with expected.
  __ cmpxchg(addr, tmp2, new_val, size, acquire, release, false, noreg);
  // EQ flag set iff success.  tmp2 holds value fetched, tmp1 (rscratch1) clobbered.

  // If fetched value did not equal the new expected, this could
  // still be a false negative because some other thread may have
  // newly overwritten the memory value with its to-space equivalent.
  __ br(Assembler::NE, step4);

  if (is_cae) {
    // We're falling through to done to indicate success.  Success
    // with is_cae is denoted by returning the value of expected as
    // result.
    __ mov(tmp2, expected);
  }

  __ bind(done);
  // At entry to done, the Z (EQ) flag is on iff if the CAS
  // operation was successful.  Additionally, if is_cae, tmp2 holds
  // the value most recently fetched from addr. In this case, success
  // is denoted by tmp2 matching expected.

  if (is_cae) {
    __ mov(result, tmp2);
  } else {
    __ cset(result, Assembler::EQ);
  }
}

void ShenandoahBarrierSetAssembler::gen_write_ref_array_post_barrier(MacroAssembler* masm, DecoratorSet decorators,
                                                                     Register start, Register count, Register scratch) {
  assert(ShenandoahCardBarrier, "Should have been checked by caller");

  Label L_loop, L_done;
  const Register end = count;

  // Zero count? Nothing to do.
  __ cbz(count, L_done);

  // end = start + count << LogBytesPerHeapOop
  // last element address to make inclusive
  __ lea(end, Address(start, count, Address::lsl(LogBytesPerHeapOop)));
  __ sub(end, end, BytesPerHeapOop);
  __ lsr(start, start, CardTable::card_shift());
  __ lsr(end, end, CardTable::card_shift());

  // number of bytes to copy
  __ sub(count, end, start);

  Address curr_ct_holder_addr(rthread, in_bytes(ShenandoahThreadLocalData::card_table_offset()));
  __ ldr(scratch, curr_ct_holder_addr);
  __ add(start, start, scratch);
  __ bind(L_loop);
  __ strb(zr, Address(start, count));
  __ subs(count, count, 1);
  __ br(Assembler::GE, L_loop);
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
    ce->mem2reg(stub->addr(), stub->pre_val(), T_OBJECT, stub->patch_code(), stub->info(), false /*wide*/);
  }
  __ cbz(pre_val_reg, *stub->continuation());
  ce->store_parameter(stub->pre_val()->as_register(), 0);
  __ far_call(RuntimeAddress(bs->pre_barrier_c1_runtime_code_blob()->code_begin()));
  __ b(*stub->continuation());
}

void ShenandoahBarrierSetAssembler::gen_load_reference_barrier_stub(LIR_Assembler* ce, ShenandoahLoadReferenceBarrierStub* stub) {
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

  assert(res == r0, "result must arrive in r0");

  if (res != obj) {
    __ mov(res, obj);
  }

  if (is_strong) {
    // Check for object in cset.
    if (AOTCodeCache::is_on_for_dump()) {
      __ lea(tmp2, ExternalAddress(AOTRuntimeConstants::cset_base_address()));
      __ ldr(tmp2, Address(tmp2));
      __ lea(tmp1, ExternalAddress(AOTRuntimeConstants::grain_shift_address()));
      __ ldrw(tmp1, Address(tmp1));
      __ lsrv(tmp1, res, tmp1);
    } else {
      __ mov(tmp2, ShenandoahHeap::in_cset_fast_test_addr());
      __ lsr(tmp1, res, ShenandoahHeapRegion::region_size_bytes_shift_jint());
    }
    __ ldrb(tmp2, Address(tmp2, tmp1));
    __ cbz(tmp2, *stub->continuation());
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

  __ b(*stub->continuation());
}

#undef __

#define __ sasm->

void ShenandoahBarrierSetAssembler::generate_c1_pre_barrier_runtime_stub(StubAssembler* sasm) {
  __ prologue("shenandoah_pre_barrier", false);

  // arg0 : previous value of memory

  BarrierSet* bs = BarrierSet::barrier_set();

  const Register pre_val = r0;
  const Register thread = rthread;
  const Register tmp = rscratch1;

  Address queue_index(thread, in_bytes(ShenandoahThreadLocalData::satb_mark_queue_index_offset()));
  Address buffer(thread, in_bytes(ShenandoahThreadLocalData::satb_mark_queue_buffer_offset()));

  Label done;
  Label runtime;

  // Is marking still active?
  Address gc_state(thread, in_bytes(ShenandoahThreadLocalData::gc_state_offset()));
  __ ldrb(tmp, gc_state);
  __ tbz(tmp, ShenandoahHeap::MARKING_BITPOS, done);

  // Can we store original value in the thread's buffer?
  __ ldr(tmp, queue_index);
  __ cbz(tmp, runtime);

  __ sub(tmp, tmp, wordSize);
  __ str(tmp, queue_index);
  __ ldr(rscratch2, buffer);
  __ add(tmp, tmp, rscratch2);
  __ load_parameter(0, rscratch2);
  __ str(rscratch2, Address(tmp, 0));
  __ b(done);

  __ bind(runtime);
  __ push_call_clobbered_registers();
  __ load_parameter(0, pre_val);
  __ call_VM_leaf(CAST_FROM_FN_PTR(address, ShenandoahRuntime::write_barrier_pre), pre_val);
  __ pop_call_clobbered_registers();
  __ bind(done);

  __ epilogue();
}

void ShenandoahBarrierSetAssembler::generate_c1_load_reference_barrier_runtime_stub(StubAssembler* sasm, DecoratorSet decorators) {
  __ prologue("shenandoah_load_reference_barrier", false);
  // arg0 : object to be resolved

  __ push_call_clobbered_registers();
  __ load_parameter(0, r0);
  __ load_parameter(1, r1);

  bool is_strong  = ShenandoahBarrierSet::is_strong_access(decorators);
  bool is_weak    = ShenandoahBarrierSet::is_weak_access(decorators);
  bool is_phantom = ShenandoahBarrierSet::is_phantom_access(decorators);
  bool is_native  = ShenandoahBarrierSet::is_native_access(decorators);
  if (is_strong) {
    if (is_native) {
      __ lea(lr, RuntimeAddress(CAST_FROM_FN_PTR(address, ShenandoahRuntime::load_reference_barrier_strong)));
    } else {
      if (UseCompressedOops) {
        __ lea(lr, RuntimeAddress(CAST_FROM_FN_PTR(address, ShenandoahRuntime::load_reference_barrier_strong_narrow)));
      } else {
        __ lea(lr, RuntimeAddress(CAST_FROM_FN_PTR(address, ShenandoahRuntime::load_reference_barrier_strong)));
      }
    }
  } else if (is_weak) {
    assert(!is_native, "weak must not be called off-heap");
    if (UseCompressedOops) {
      __ lea(lr, RuntimeAddress(CAST_FROM_FN_PTR(address, ShenandoahRuntime::load_reference_barrier_weak_narrow)));
    } else {
      __ lea(lr, RuntimeAddress(CAST_FROM_FN_PTR(address, ShenandoahRuntime::load_reference_barrier_weak)));
    }
  } else {
    assert(is_phantom, "only remaining strength");
    assert(is_native, "phantom must only be called off-heap");
    __ lea(lr, RuntimeAddress(CAST_FROM_FN_PTR(address, ShenandoahRuntime::load_reference_barrier_phantom)));
  }
  __ blr(lr);
  __ mov(rscratch1, r0);
  __ pop_call_clobbered_registers();
  __ mov(r0, rscratch1);

  __ epilogue();
}

#undef __

#endif // COMPILER1

#ifdef COMPILER2

#undef __
#define __ masm->


void ShenandoahBarrierSetAssembler::load_c2(const MachNode* node, MacroAssembler* masm, Register dst, Address src, Register tmp1, Register tmp2, bool is_narrow, bool is_acquire) {
  // Do the actual load. This load is the candidate for implicit null check, and MUST come first.
  if (is_narrow) {
    if (is_acquire) {
      assert(src.getMode() == Address::base_plus_offset && src.offset() == 0,
          "is_acquire path requires address to be base-only");
      __ ldarw(dst, src.base());
    } else {
      __ ldrw(dst, src);
    }
  } else {
    if (is_acquire) {
      assert(src.getMode() == Address::base_plus_offset && src.offset() == 0,
          "is_acquire path requires address to be base-only");
      __ ldar(dst, src.base());
    } else {
      __ ldr(dst, src);
    }
  }

  ShenandoahBarrierStubC2::load_post(masm, node, dst, src, tmp1, tmp2, is_narrow);
}

void ShenandoahBarrierSetAssembler::store_c2(const MachNode* node, MacroAssembler* masm, Address dst, bool dst_narrow,
    Register src, bool src_narrow, Register tmp1, Register tmp2, Register tmp3, bool is_volatile) {

  ShenandoahBarrierStubC2::store_pre(masm, node, dst, tmp1, tmp2, tmp3, dst_narrow);

  // Do the actual store
  if (dst_narrow) {
    if (!src_narrow) {
      // Need to encode into rscratch, because we cannot clobber src.
      if ((node->barrier_data() & ShenandoahBitNotNull) == 0) {
        __ encode_heap_oop(tmp2, src);
      } else {
        __ encode_heap_oop_not_null(tmp2, src);
      }
      src = tmp2;
    }

    if (is_volatile) {
      assert(dst.getMode() == Address::base_plus_offset && dst.offset() == 0,
          "is_acquire path requires address to be base-only");
      __ stlrw(src, dst.base());
    } else {
      __ strw(src, dst);
    }
  } else {
    if (is_volatile) {
      assert(dst.getMode() == Address::base_plus_offset && dst.offset() == 0,
          "is_acquire path requires address to be base-only");
      __ stlr(src, dst.base());
    } else {
      __ str(src, dst);
    }
  }

  ShenandoahBarrierStubC2::store_post(masm, node, dst, tmp2, tmp3);
}

void ShenandoahBarrierSetAssembler::compare_and_set_c2(const MachNode* node, MacroAssembler* masm, Register res, Register addr,
    Register oldval, Register newval, Register tmp1, Register tmp2, Register tmp3, bool exchange, bool narrow, bool weak, bool acquire) {
  Assembler::operand_size op_size = narrow ? Assembler::word : Assembler::xword;

  ShenandoahBarrierStubC2::load_store_pre(masm, node, addr, tmp1, tmp2, tmp3, narrow);

  // CAS!
  __ cmpxchg(addr, oldval, newval, op_size, acquire, /* release */ true, weak, exchange ? res : noreg);

  // If we need a boolean result out of CAS, set the flag appropriately and promote the result.
  if (!exchange) {
    assert(res != noreg, "need result register");
    __ cset(res, Assembler::EQ);
  }

  ShenandoahBarrierStubC2::load_store_post(masm, node, Address(addr, 0), tmp2, tmp3);
}

void ShenandoahBarrierSetAssembler::get_and_set_c2(const MachNode* node, MacroAssembler* masm, Register preval,
    Register newval, Register addr, Register tmp1, Register tmp2, Register tmp3, bool is_acquire) {
  bool is_narrow = node->bottom_type()->isa_narrowoop();

  ShenandoahBarrierStubC2::load_store_pre(masm, node, addr, tmp1, tmp2, tmp3, is_narrow);

  if (is_narrow) {
    if (is_acquire) {
      __ atomic_xchgalw(preval, newval, addr);
    } else {
      __ atomic_xchgw(preval, newval, addr);
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
  Address curr_ct_holder_addr(rthread, in_bytes(ShenandoahThreadLocalData::card_table_offset()));
  __ ldr(tmp1, curr_ct_holder_addr);

  // tmp2 = effective address
  __ lea(tmp2, address);

  // tmp2 = &card_table[ addr >> CardTable::card_shift() ] ; card index
  __ add(tmp2, tmp1, tmp2, Assembler::LSR, CardTable::card_shift());

  if (UseCondCardMark) {
    Label L_already_dirty;
    __ ldrb(tmp1, Address(tmp2));
    __ cbz(tmp1, L_already_dirty);
    __ strb(zr, Address(tmp2));
    __ bind(L_already_dirty);
  } else {
    __ strb(zr, Address(tmp2));
  }
}

void ShenandoahBarrierStubC2::enter_if_gc_state(MacroAssembler& masm, const char test_state, Register tmp) {
  Assembler::InlineSkippedInstructionsCounter skip_counter(&masm);
  PhaseOutput* const output = Compile::current()->output();
  Address gc_state_fast(rthread, in_bytes(ShenandoahThreadLocalData::gc_state_fast_array_offset(test_state)));

  // We piggyback on scratch_emit_size mode to compute the slowpath stub size.
  // We'll use that information to decide whether we need a far jump to the
  // stub entry point or not. In scratch_emit_size mode we don't bind entry()
  // because otherwise it will be rebound when we later emit the instructions
  // for real.
  if (_needs_far_jump) {
    __ ldrb(tmp, gc_state_fast);
    __ cbz(tmp, *continuation());
    __ b(output->in_scratch_emit_size() ? *continuation() : *entry());
  } else {
    __ ldrb(tmp, gc_state_fast);
    __ cbnz(tmp, output->in_scratch_emit_size() ? *continuation() : *entry());
  }

  // This is were the slowpath stub will return to or the code above will
  // jump to if the checks are false
  __ bind(*continuation());
}

void ShenandoahBarrierStubC2::emit_code(MacroAssembler& masm) {
  Assembler::InlineSkippedInstructionsCounter skip_counter(&masm);
  assert(_needs_keep_alive_barrier || _needs_load_ref_barrier, "Why are you here?");
  PhaseOutput* const output = Compile::current()->output();

  // We piggyback on scratch_emit_size mode to compute the slowpath stub size.
  // We'll use that information to decide whether we need a far jump to the
  // stub entry point or not. In scratch_emit_size mode we don't bind entry()
  // because otherwise it will be rebound when we later emit the instructions
  // for real.
  if (!output->in_scratch_emit_size()) {
    __ bind(*entry());
  }

  // If we need to load ourselves, do it here.
  if (_do_load) {
    if (_narrow) {
      __ ldrw(_obj, _addr);
    } else {
      __ ldr(_obj, _addr);
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
    // The Load match rule in the .ad file may have legitimized the load
    // address using a TEMP register and in that case we need to explicitly
    // preserve them here, because the RA does not consider TEMP as live-in,
    // and the KA runtime call may clobber them and cause a crash on the
    // subsequent LRB stub.
    if (_addr.base() != noreg) {
      preserve(_addr.base());
    }
    if (_addr.index() != noreg) {
      preserve(_addr.index());
    }
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
  if (_needs_far_jump) {
    Label L_short_jump;
    __ cbnz(reg, L_short_jump);
    __ b(*continuation());
    __ bind(L_short_jump);
  } else {
    __ cbz(reg, *continuation());
  }
}

void ShenandoahBarrierStubC2::keepalive(MacroAssembler& masm, Label* L_done) {
  Address gcstate(rthread, in_bytes(ShenandoahThreadLocalData::gc_state_fast_array_offset(ShenandoahHeap::MARKING)));
  Address index(rthread, in_bytes(ShenandoahThreadLocalData::satb_mark_queue_index_offset()));
  Address buffer(rthread, in_bytes(ShenandoahThreadLocalData::satb_mark_queue_buffer_offset()));
  Label L_through, L_slowpath;

  // If another barrier is enabled as well, do a runtime check for a specific barrier.
  if (_needs_load_ref_barrier) {
    assert(L_done == nullptr, "L_done is always null when _needs_load_ref_barrier is true");
    __ ldrb(_tmp1, gcstate);
    __ cbz(_tmp1, L_through);
  }

  // Fast-path: put object into buffer.
  // If buffer is already full, go slow.
  __ ldr(_tmp1, index);
  __ cbz(_tmp1, L_slowpath);
  __ sub(_tmp1, _tmp1, wordSize);
  __ str(_tmp1, index);
  __ ldr(_tmp2, buffer);

  // Store the object in queue.
  // If object is narrow, we need to decode it before inserting.
  if (_narrow) {
    __ add(_tmp2, _tmp2, _tmp1);
    __ decode_heap_oop_not_null(_tmp1, _obj);
    __ str(_tmp1, Address(_tmp2));
  } else {
    // Buffer is 64-bit address, must be in base register.
    __ str(_obj, Address(_tmp2, _tmp1));
  }

  // Fast-path exits here.
  if (L_done != nullptr) {
    __ b(*L_done);
  } else {
    __ b(L_through);
  }

  // Slow-path: call runtime to handle.
  __ bind(L_slowpath);

  {
    SaveLiveRegisters slr(&masm, this);

    // Go to runtime and handle the rest there.
    __ mov(c_rarg0, _obj);
    __ lea(lr, RuntimeAddress(keepalive_runtime_entry_addr()));
    __ blr(lr);
  }
  if (L_done != nullptr) {
    __ b(*L_done);
  } else {
    __ bind(L_through);
  }
}

void ShenandoahBarrierStubC2::lrb(MacroAssembler& masm) {
  Label L_slow;

  // If another barrier is enabled as well, do a runtime check for a specific barrier.
  if (_needs_keep_alive_barrier) {
    char state_to_check = ShenandoahHeap::HAS_FORWARDED | (_needs_load_ref_weak_barrier ? ShenandoahHeap::WEAK_ROOTS : 0);
    Address gc_state_fast(rthread, in_bytes(ShenandoahThreadLocalData::gc_state_fast_array_offset(state_to_check)));
    __ ldrb(_tmp1, gc_state_fast);
    maybe_far_jump_if_zero(masm, _tmp1);
  }

  // If weak references are being processed, weak/phantom loads need to go slow,
  // regardless of their cset status.
  if (_needs_load_ref_weak_barrier) {
    Address gc_state_fast(rthread, in_bytes(ShenandoahThreadLocalData::gc_state_fast_array_offset(ShenandoahHeap::WEAK_ROOTS)));
    __ ldrb(_tmp1, gc_state_fast);
    __ cbnz(_tmp1, L_slow);
  }

  // Cset-check. Fall-through to slow if in collection set.
  bool is_aot = AOTCodeCache::is_on_for_dump();
  if (!is_aot) {
    __ mov(_tmp1, ShenandoahHeap::in_cset_fast_test_addr());
    if (_narrow) {
      __ decode_heap_oop_not_null(_tmp2, _obj);
      __ add(_tmp1, _tmp1, _tmp2, Assembler::LSR, ShenandoahHeapRegion::region_size_bytes_shift_jint());
    } else {
      __ add(_tmp1, _tmp1, _obj, Assembler::LSR, ShenandoahHeapRegion::region_size_bytes_shift_jint());
    }
  } else {
    // Generating AOT code, pull the cset bitmap and region shift from AOT table.
    if (_narrow) {
      __ decode_heap_oop_not_null(_tmp1, _obj);
    } else {
      __ mov(_tmp1, _obj);
    }
    __ lea(_tmp2, ExternalAddress(AOTRuntimeConstants::grain_shift_address()));
    __ ldrw(_tmp2, Address(_tmp2));
    __ lsrv(_tmp2, _tmp1, _tmp2);
    __ lea(_tmp1, ExternalAddress(AOTRuntimeConstants::cset_base_address()));
    __ ldr(_tmp1, Address(_tmp1));
    __ add(_tmp1, _tmp1, _tmp2);
  }
  __ ldrb(_tmp1, Address(_tmp1, 0));
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
    //   c_rarg0 <-- obj
    //   c_rarg1 <-- lea(addr)
    if (c_rarg0 == _obj) {
      __ lea(c_rarg1, _addr);
    } else if (c_rarg1 == _obj) {
      // Set up arguments in reverse, and then flip them
      __ lea(c_rarg0, _addr);
      // flip them
      __ mov(_tmp1, c_rarg0);
      __ mov(c_rarg0, c_rarg1);
      __ mov(c_rarg1, _tmp1);
    } else {
      assert_different_registers(c_rarg1, _obj);
      __ lea(c_rarg1, _addr);
      __ mov(c_rarg0, _obj);
    }

    // Go to runtime and handle the rest there.
    __ lea(lr, RuntimeAddress(lrb_runtime_entry_addr()));
    __ blr(lr);

    // Save the result where needed. Narrow entries return narrowOop (32 bits)
    // and AAPCS does not guarantee the upper 32 bits of x0 are zero.
    if (_narrow) {
      __ movw(_obj, r0);
    } else if (_obj != r0) {
      __ mov(_obj, r0);
    }
  }
  if (is_obj_preserved) {
    preserve(_obj);
  }

  __ b(*continuation());
}

int ShenandoahBarrierStubC2::available_gp_registers() {
  Unimplemented(); // Not used
  return 0;
}

bool ShenandoahBarrierStubC2::is_special_register(Register r) {
  Unimplemented(); // Not used
  return true;
}

static ShenandoahBarrierSetC2State* barrier_set_state() {
  return reinterpret_cast<ShenandoahBarrierSetC2State*>(Compile::current()->barrier_set_state());
}

static int get_stub_size(ShenandoahBarrierStubC2* stub) {
  PhaseOutput* const output = Compile::current()->output();
  assert(output->in_scratch_emit_size(), "only used when in scratch_emit_size.");
  BufferBlob* const blob = output->scratch_buffer_blob();
  CodeBuffer cb(blob->content_begin(), (address)output->scratch_locs_memory() - blob->content_begin());
  MacroAssembler masm(&cb);
  stub->emit_code(masm);
  return cb.insts_size();
}

void ShenandoahBarrierStubC2::post_init() {
  // If we are in scratch emit mode we assume worst case, and force the use of
  // far branches.
  PhaseOutput* const output = Compile::current()->output();
  ShenandoahBarrierSetC2State* state = barrier_set_state();
  if (output->in_scratch_emit_size()) {
    state->inc_stubs_current_total_size(get_stub_size(this));
    _needs_far_jump = true;
    return;
  }

  // The logic implemented in this stub only uses short jumps (cbz, cbnz) if
  // the aggregation of all relevant code sections of a method is less than 1MB
  // - 2KB. We could be more aggressive and try and compute the distance
  // between the fastpath branch and the stub entry but in practice not many
  // methods reach the 1MB size.
  const BufferSizingData* sizing = output->buffer_sizing_data();
  const int code_size = sizing->_code + state->stubs_current_total_size();

  // Maximum backward range is 1M. Maximum forward reach is 1M - 4bytes.
  // Subtract 2K to be ultra conservative.
  const int cond_branch_max_reach = (int)(1*M - 2*K);
  _needs_far_jump = code_size >= cond_branch_max_reach;
}

#endif // COMPILER2
