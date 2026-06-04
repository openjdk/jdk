/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2018, 2021, Red Hat, Inc. All rights reserved.
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
#include "interpreter/interpreter.hpp"
#include "runtime/javaThread.hpp"
#include "runtime/sharedRuntime.hpp"
#include "utilities/macros.hpp"
#ifdef COMPILER1
#include "c1/c1_LIRAssembler.hpp"
#include "c1/c1_MacroAssembler.hpp"
#include "gc/shenandoah/c1/shenandoahBarrierSetC1.hpp"
#endif
#ifdef COMPILER2
#include "gc/shenandoah/c2/shenandoahBarrierSetC2.hpp"
#endif

#define __ masm->

void ShenandoahBarrierSetAssembler::arraycopy_prologue(MacroAssembler* masm, DecoratorSet decorators, BasicType type,
                                                       Register src, Register dst, Register count) {

  bool dest_uninitialized = (decorators & IS_DEST_UNINITIALIZED) != 0;

  if (is_reference_type(type)) {
    if (ShenandoahCardBarrier) {
      bool checkcast = (decorators & ARRAYCOPY_CHECKCAST) != 0;
      bool disjoint = (decorators & ARRAYCOPY_DISJOINT) != 0;
      bool obj_int = (type == T_OBJECT) && UseCompressedOops;

      // We need to save the original element count because the array copy stub
      // will destroy the value and we need it for the card marking barrier.
      if (!checkcast) {
        if (!obj_int) {
          // Save count for barrier
          __ movptr(r11, count);
        } else if (disjoint) {
          // Save dst in r11 in the disjoint case
          __ movq(r11, dst);
        }
      }
    }

    if ((ShenandoahSATBBarrier && !dest_uninitialized) || ShenandoahLoadRefBarrier) {
      Register thread = r15_thread;
      assert_different_registers(src, dst, count, thread);

      Label L_done;
      // Short-circuit if count == 0.
      __ testptr(count, count);
      __ jcc(Assembler::zero, L_done);

      // Avoid runtime call when not active.
      Address gc_state(thread, in_bytes(ShenandoahThreadLocalData::gc_state_offset()));
      int flags;
      if (ShenandoahSATBBarrier && dest_uninitialized) {
        flags = ShenandoahHeap::HAS_FORWARDED;
      } else {
        flags = ShenandoahHeap::HAS_FORWARDED | ShenandoahHeap::MARKING;
      }
      __ testb(gc_state, flags);
      __ jcc(Assembler::zero, L_done);

      __ push_call_clobbered_registers(/* save_fpu = */ false);
      // If arguments are not in proper places, shuffle them.
      // Doing this via the stack is the most straight-forward way to avoid
      // accidentally smashing any register.
      if (c_rarg0 != src || c_rarg1 != dst || c_rarg2 != count) {
        __ push(src);
        __ push(dst);
        __ push(count);
        __ pop(c_rarg2);
        __ pop(c_rarg1);
        __ pop(c_rarg0);
      }
      address target = nullptr;
      if (UseCompressedOops) {
        target = CAST_FROM_FN_PTR(address, ShenandoahRuntime::arraycopy_barrier_narrow_oop);
      } else {
        target = CAST_FROM_FN_PTR(address, ShenandoahRuntime::arraycopy_barrier_oop);
      }
      __ call_VM_leaf(target, 3);

      __ pop_call_clobbered_registers(/* restore_fpu = */ false);

      __ bind(L_done);
    }
  }

}

void ShenandoahBarrierSetAssembler::arraycopy_epilogue(MacroAssembler* masm, DecoratorSet decorators, BasicType type,
                                                       Register src, Register dst, Register count) {

  if (ShenandoahCardBarrier && is_reference_type(type)) {
    bool checkcast = (decorators & ARRAYCOPY_CHECKCAST) != 0;
    bool disjoint = (decorators & ARRAYCOPY_DISJOINT) != 0;
    bool obj_int = (type == T_OBJECT) && UseCompressedOops;
    Register tmp = rax;

    if (!checkcast) {
      if (!obj_int) {
        // Save count for barrier
        count = r11;
      } else if (disjoint) {
        // Use the saved dst in the disjoint case
        dst = r11;
      }
    } else {
      tmp = rscratch1;
    }
    gen_write_ref_array_post_barrier(masm, decorators, dst, count, tmp);
  }
}

void ShenandoahBarrierSetAssembler::satb_barrier(MacroAssembler* masm,
                                                 Register obj,
                                                 Register pre_val,
                                                 Register tmp) {
  assert(ShenandoahSATBBarrier, "Should be checked by caller");
  const Register thread = r15_thread;

  Label done;
  Label runtime;

  assert(pre_val != noreg, "check this code");

  if (obj != noreg) {
    assert_different_registers(obj, pre_val, tmp);
    assert(pre_val != rax, "check this code");
  }

  Address index(thread, in_bytes(ShenandoahThreadLocalData::satb_mark_queue_index_offset()));
  Address buffer(thread, in_bytes(ShenandoahThreadLocalData::satb_mark_queue_buffer_offset()));

  Address gc_state(thread, in_bytes(ShenandoahThreadLocalData::gc_state_offset()));
  __ testb(gc_state, ShenandoahHeap::MARKING);
  __ jcc(Assembler::zero, done);

  // Do we need to load the previous value?
  if (obj != noreg) {
    if (UseCompressedOops) {
      __ movl(pre_val, Address(obj, 0));
      __ decode_heap_oop(pre_val);
    } else {
      __ movq(pre_val, Address(obj, 0));
    }
  }

  // Is the previous value null?
  __ cmpptr(pre_val, NULL_WORD);
  __ jcc(Assembler::equal, done);

  // Can we store original value in the thread's buffer?
  // Is index == 0?
  // (The index field is typed as size_t.)

  __ movptr(tmp, index);                   // tmp := *index_adr
  __ cmpptr(tmp, 0);                       // tmp == 0?
  __ jcc(Assembler::equal, runtime);       // If yes, goto runtime

  __ subptr(tmp, wordSize);                // tmp := tmp - wordSize
  __ movptr(index, tmp);                   // *index_adr := tmp
  __ addptr(tmp, buffer);                  // tmp := tmp + *buffer_adr

  // Record the previous value
  __ movptr(Address(tmp, 0), pre_val);
  __ jmp(done);

  __ bind(runtime);

  // Slow-path call.
  // Some paths can be reached from the c2i adapter with live fp arguments in registers.
  __ enter();
  __ push_call_clobbered_registers(/* save_fpu = */ true);

  assert(thread != c_rarg0, "smashed arg");
  if (c_rarg0 != pre_val) {
    __ mov(c_rarg0, pre_val);
  }

  // Calling with super_call_VM_leaf with c_rarg0 bypasses interpreter checks and avoids any moves.
  __ super_call_VM_leaf(CAST_FROM_FN_PTR(address, ShenandoahRuntime::write_barrier_pre), c_rarg0);

  __ pop_call_clobbered_registers(/* restore_fpu = */ true);
  __ leave();

  __ bind(done);
}

void ShenandoahBarrierSetAssembler::load_reference_barrier(MacroAssembler* masm, Register dst, Address src, DecoratorSet decorators) {
  assert(ShenandoahLoadRefBarrier, "Should be enabled");

  bool is_strong  = ShenandoahBarrierSet::is_strong_access(decorators);
  bool is_weak    = ShenandoahBarrierSet::is_weak_access(decorators);
  bool is_phantom = ShenandoahBarrierSet::is_phantom_access(decorators);
  bool is_native  = ShenandoahBarrierSet::is_native_access(decorators);
  bool is_narrow  = UseCompressedOops && !is_native;

  Label heap_stable, not_cset;

  __ block_comment("load_reference_barrier { ");

  // Check if GC is active
  Register thread = r15_thread;

  Address gc_state(thread, in_bytes(ShenandoahThreadLocalData::gc_state_offset()));
  int flags = ShenandoahHeap::HAS_FORWARDED;
  if (!is_strong) {
    flags |= ShenandoahHeap::WEAK_ROOTS;
  }
  __ testb(gc_state, flags);
  __ jcc(Assembler::zero, heap_stable);

  Register tmp1 = noreg, tmp2 = noreg;
  if (is_strong) {
    // Test for object in cset
    // Allocate temporary registers
    for (int i = 0; i < Register::available_gp_registers(); i++) {
      Register r = as_Register(i);
      if (r != rsp && r != rbp && r != rcx && r != dst && r != src.base() && r != src.index() ) {
        if (tmp1 == noreg) {
          tmp1 = r;
        } else {
          tmp2 = r;
          break;
        }
      }
    }
    assert(tmp1 != noreg, "tmp1 allocated");
    assert(tmp2 != noreg, "tmp2 allocated");
    assert_different_registers(tmp1, tmp2, src.base(), src.index());
    assert_different_registers(tmp1, tmp2, dst);

    __ push(tmp1);
    __ push(tmp2);

    // Optimized cset-test
    __ movptr(tmp1, dst);
    if (AOTCodeCache::is_on_for_dump()) {
      assert_different_registers(tmp1, tmp2, rcx);
      __ lea(tmp2, ExternalAddress(AOTRuntimeConstants::grain_shift_address()));
      __ push(rcx);
      __ movb(rcx, Address(tmp2));
      __ shrptr(tmp1);
      __ pop(rcx);
      __ lea(tmp2, ExternalAddress(AOTRuntimeConstants::cset_base_address()));
      __ movptr(tmp2, Address(tmp2));
    } else {
      __ shrptr(tmp1, ShenandoahHeapRegion::region_size_bytes_shift_jint());
      __ movptr(tmp2, (intptr_t) ShenandoahHeap::in_cset_fast_test_addr());
    }
    __ movbool(tmp1, Address(tmp1, tmp2, Address::times_1));
    __ testbool(tmp1);
    __ jcc(Assembler::zero, not_cset);
  }

  // Slow-path call.
  // Save registers that can be clobbered by call.
  // Some paths can be reached from the c2i adapter with live fp arguments in registers.
  __ enter();
  if (dst != rax) {
    __ push(rax);
  }
  __ push_call_clobbered_registers_except(rax, /* save_fpu = */ true);

  // Shuffle registers such that dst is in c_rarg0 and addr in c_rarg1.
  if (dst == c_rarg1) {
    __ lea(c_rarg0, src);
    __ xchgptr(c_rarg1, c_rarg0);
  } else {
    __ lea(c_rarg1, src);
    __ movptr(c_rarg0, dst);
  }

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
  __ pop_call_clobbered_registers_except(rax, /* restore_fpu = */ true);
  if (dst != rax) {
    __ movptr(dst, rax);
    __ pop(rax);
  }
  __ leave();

  __ bind(not_cset);

  if  (is_strong) {
    __ pop(tmp2);
    __ pop(tmp1);
  }

  __ bind(heap_stable);

  __ block_comment("} load_reference_barrier");
}

//
// Arguments:
//
// Inputs:
//   src:        oop location, might be clobbered
//   tmp1:       scratch register, might not be valid.
//
// Output:
//   dst:        oop loaded from src location
//
// Kill:
//   tmp1 (if it is valid)
//
void ShenandoahBarrierSetAssembler::load_at(MacroAssembler* masm, DecoratorSet decorators, BasicType type,
             Register dst, Address src, Register tmp1) {
  // 1: non-reference load, no additional barrier is needed
  if (!is_reference_type(type)) {
    BarrierSetAssembler::load_at(masm, decorators, type, dst, src, tmp1);
    return;
  }

  assert((decorators & ON_UNKNOWN_OOP_REF) == 0, "Not expected");

  // 2: load a reference from src location and apply LRB if needed
  if (ShenandoahBarrierSet::need_load_reference_barrier(decorators, type)) {
    Register result_dst = dst;
    bool use_tmp1_for_dst = false;

    // Preserve src location for LRB
    if (dst == src.base() || dst == src.index()) {
    // Use tmp1 for dst if possible, as it is not used in BarrierAssembler::load_at()
      if (tmp1->is_valid() && tmp1 != src.base() && tmp1 != src.index()) {
        dst = tmp1;
        use_tmp1_for_dst = true;
      } else {
        dst = rdi;
        __ push(dst);
      }
      assert_different_registers(dst, src.base(), src.index());
    }

    BarrierSetAssembler::load_at(masm, decorators, type, dst, src, tmp1);

    load_reference_barrier(masm, dst, src, decorators);

    // Move loaded oop to final destination
    if (dst != result_dst) {
      __ movptr(result_dst, dst);

      if (!use_tmp1_for_dst) {
        __ pop(dst);
      }

      dst = result_dst;
    }
  } else {
    BarrierSetAssembler::load_at(masm, decorators, type, dst, src, tmp1);
  }

  // 3: apply keep-alive barrier if needed
  if (ShenandoahBarrierSet::need_keep_alive_barrier(decorators, type)) {
    satb_barrier(masm /* masm */,
                 noreg /* obj */,
                 dst /* pre_val */,
                 tmp1 /* tmp */);
  }
}

void ShenandoahBarrierSetAssembler::card_barrier(MacroAssembler* masm, Register obj) {
  assert(ShenandoahCardBarrier, "Should have been checked by caller");

  // Does a store check for the oop in register obj. The content of
  // register obj is destroyed afterwards.
  __ shrptr(obj, CardTable::card_shift());

  // We'll use this register as the TLS base address and also later on
  // to hold the byte_map_base.
  Register thread = r15_thread;
  Register tmp = rscratch1;

  Address curr_ct_holder_addr(thread, in_bytes(ShenandoahThreadLocalData::card_table_offset()));
  __ movptr(tmp, curr_ct_holder_addr);
  Address card_addr(tmp, obj, Address::times_1);

  int dirty = CardTable::dirty_card_val();
  if (UseCondCardMark) {
    Label L_already_dirty;
    __ cmpb(card_addr, dirty);
    __ jccb(Assembler::equal, L_already_dirty);
    __ movb(card_addr, dirty);
    __ bind(L_already_dirty);
  } else {
    __ movb(card_addr, dirty);
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
  assert_different_registers(val, tmp1, tmp2, tmp3, r15_thread);
  if (dst.index() == noreg && dst.disp() == 0) {
    if (dst.base() != tmp1) {
      __ movptr(tmp1, dst.base());
    }
  } else {
    __ lea(tmp1, dst);
  }

  // 2: pre-barrier: SATB needs the previous value
  if (ShenandoahBarrierSet::need_satb_barrier(decorators, type)) {
    satb_barrier(masm,
                 tmp1 /* obj */,
                 tmp2 /* pre_val */,
                 tmp3 /* tmp */);
  }

  // Store!
  BarrierSetAssembler::store_at(masm, decorators, type, Address(tmp1, 0), val, noreg, noreg, noreg);

  // 3: post-barrier: card barrier needs store address
  bool storing_non_null = (val != noreg);
  if (ShenandoahBarrierSet::need_card_barrier(decorators, type) && storing_non_null) {
    card_barrier(masm, tmp1);
  }
}

void ShenandoahBarrierSetAssembler::try_resolve_jobject_in_native(MacroAssembler* masm, Register jni_env,
                                                                  Register obj, Register tmp, Label& slowpath) {
  Label done;
  // Resolve jobject
  BarrierSetAssembler::try_resolve_jobject_in_native(masm, jni_env, obj, tmp, slowpath);

  // Check for null.
  __ testptr(obj, obj);
  __ jcc(Assembler::zero, done);

  Address gc_state(jni_env, ShenandoahThreadLocalData::gc_state_offset() - JavaThread::jni_environment_offset());
  __ testb(gc_state, ShenandoahHeap::EVACUATION);
  __ jccb(Assembler::notZero, slowpath);
  __ bind(done);
}

void ShenandoahBarrierSetAssembler::try_peek_weak_handle_in_nmethod(MacroAssembler* masm, Register weak_handle, Register obj, Label& slowpath) {
  Label done;

  // Peek weak handle using the standard implementation.
  BarrierSetAssembler::try_peek_weak_handle_in_nmethod(masm, weak_handle, obj, slowpath);

  // Check if the reference is null, and if it is, take the fast path.
  __ testptr(obj, obj);
  __ jcc(Assembler::zero, done);

  Address gc_state(r15_thread, ShenandoahThreadLocalData::gc_state_offset());

  // Check if the heap is under weak-reference/roots processing, in
  // which case we need to take the slow path.
  __ testb(gc_state, ShenandoahHeap::WEAK_ROOTS);
  __ jcc(Assembler::notZero, slowpath);
  __ bind(done);
}

// Special Shenandoah CAS implementation that handles false negatives
// due to concurrent evacuation.
void ShenandoahBarrierSetAssembler::cmpxchg_oop(MacroAssembler* masm,
                                                Register res, Address addr, Register oldval, Register newval,
                                                bool exchange, Register tmp1, Register tmp2) {
  assert(ShenandoahCASBarrier, "Should only be used when CAS barrier is enabled");
  assert(oldval == rax, "must be in rax for implicit use in cmpxchg");
  assert_different_registers(oldval, tmp1, tmp2);
  assert_different_registers(newval, tmp1, tmp2);

  Label L_success, L_failure;

  // Remember oldval for retry logic below
  if (UseCompressedOops) {
    __ movl(tmp1, oldval);
  } else {
    __ movptr(tmp1, oldval);
  }

  // Step 1. Fast-path.
  //
  // Try to CAS with given arguments. If successful, then we are done.

  if (UseCompressedOops) {
    __ lock();
    __ cmpxchgl(newval, addr);
  } else {
    __ lock();
    __ cmpxchgptr(newval, addr);
  }
  __ jcc(Assembler::equal, L_success);

  // Step 2. CAS had failed. This may be a false negative.
  //
  // The trouble comes when we compare the to-space pointer with the from-space
  // pointer to the same object. To resolve this, it will suffice to resolve
  // the value from memory -- this will give both to-space pointers.
  // If they mismatch, then it was a legitimate failure.
  //
  // Before reaching to resolve sequence, see if we can avoid the whole shebang
  // with filters.

  // Filter: when offending in-memory value is null, the failure is definitely legitimate
  __ testptr(oldval, oldval);
  __ jcc(Assembler::zero, L_failure);

  // Filter: when heap is stable, the failure is definitely legitimate
  const Register thread = r15_thread;
  Address gc_state(thread, in_bytes(ShenandoahThreadLocalData::gc_state_offset()));
  __ testb(gc_state, ShenandoahHeap::HAS_FORWARDED);
  __ jcc(Assembler::zero, L_failure);

  if (UseCompressedOops) {
    __ movl(tmp2, oldval);
    __ decode_heap_oop(tmp2);
  } else {
    __ movptr(tmp2, oldval);
  }

  // Decode offending in-memory value.
  // Test if-forwarded
  __ testb(Address(tmp2, oopDesc::mark_offset_in_bytes()), markWord::marked_value);
  __ jcc(Assembler::noParity, L_failure);  // When odd number of bits, then not forwarded
  __ jcc(Assembler::zero, L_failure);      // When it is 00, then also not forwarded

  // Load and mask forwarding pointer
  __ movptr(tmp2, Address(tmp2, oopDesc::mark_offset_in_bytes()));
  __ shrptr(tmp2, 2);
  __ shlptr(tmp2, 2);

  if (UseCompressedOops) {
    __ decode_heap_oop(tmp1); // decode for comparison
  }

  // Now we have the forwarded offender in tmp2.
  // Compare and if they don't match, we have legitimate failure
  __ cmpptr(tmp1, tmp2);
  __ jcc(Assembler::notEqual, L_failure);

  // Step 3. Need to fix the memory ptr before continuing.
  //
  // At this point, we have from-space oldval in the register, and its to-space
  // address is in tmp2. Let's try to update it into memory. We don't care if it
  // succeeds or not. If it does, then the retrying CAS would see it and succeed.
  // If this fixup fails, this means somebody else beat us to it, and necessarily
  // with to-space ptr store. We still have to do the retry, because the GC might
  // have updated the reference for us.

  if (UseCompressedOops) {
    __ encode_heap_oop(tmp2); // previously decoded at step 2.
  }

  if (UseCompressedOops) {
    __ lock();
    __ cmpxchgl(tmp2, addr);
  } else {
    __ lock();
    __ cmpxchgptr(tmp2, addr);
  }

  // Step 4. Try to CAS again.
  //
  // This is guaranteed not to have false negatives, because oldval is definitely
  // to-space, and memory pointer is to-space as well. Nothing is able to store
  // from-space ptr into memory anymore. Make sure oldval is restored, after being
  // garbled during retries.
  //
  if (UseCompressedOops) {
    __ movl(oldval, tmp2);
  } else {
    __ movptr(oldval, tmp2);
  }

  if (UseCompressedOops) {
    __ lock();
    __ cmpxchgl(newval, addr);
  } else {
    __ lock();
    __ cmpxchgptr(newval, addr);
  }
  if (!exchange) {
    __ jccb(Assembler::equal, L_success); // fastpath, peeking into Step 5, no need to jump
  }

  // Step 5. If we need a boolean result out of CAS, set the flag appropriately.
  // and promote the result. Note that we handle the flag from both the 1st and 2nd CAS.
  // Otherwise, failure witness for CAE is in oldval on all paths, and we can return.

  if (exchange) {
    __ bind(L_failure);
    __ bind(L_success);
  } else {
    assert(res != noreg, "need result register");

    Label exit;
    __ bind(L_failure);
    __ xorptr(res, res);
    __ jmpb(exit);

    __ bind(L_success);
    __ movptr(res, 1);
    __ bind(exit);
  }
}

#ifdef PRODUCT
#define BLOCK_COMMENT(str) /* nothing */
#else
#define BLOCK_COMMENT(str) __ block_comment(str)
#endif

#define BIND(label) bind(label); BLOCK_COMMENT(#label ":")

#define TIMES_OOP (UseCompressedOops ? Address::times_4 : Address::times_8)

void ShenandoahBarrierSetAssembler::gen_write_ref_array_post_barrier(MacroAssembler* masm, DecoratorSet decorators,
                                                                     Register addr, Register count,
                                                                     Register tmp) {
  assert(ShenandoahCardBarrier, "Should have been checked by caller");

  Label L_loop, L_done;
  const Register end = count;
  assert_different_registers(addr, end);

  // Zero count? Nothing to do.
  __ testl(count, count);
  __ jccb(Assembler::zero, L_done);

  const Register thread = r15_thread;
  Address curr_ct_holder_addr(thread, in_bytes(ShenandoahThreadLocalData::card_table_offset()));
  __ movptr(tmp, curr_ct_holder_addr);

  __ leaq(end, Address(addr, count, TIMES_OOP, 0));  // end == addr+count*oop_size
  __ subptr(end, BytesPerHeapOop); // end - 1 to make inclusive
  __ shrptr(addr, CardTable::card_shift());
  __ shrptr(end, CardTable::card_shift());
  __ subptr(end, addr); // end --> cards count

  __ addptr(addr, tmp);

  __ BIND(L_loop);
  __ movb(Address(addr, count, Address::times_1), 0);
  __ decrement(count);
  __ jccb(Assembler::greaterEqual, L_loop);

  __ BIND(L_done);
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

  __ cmpptr(pre_val_reg, NULL_WORD);
  __ jcc(Assembler::equal, *stub->continuation());
  ce->store_parameter(stub->pre_val()->as_register(), 0);
  __ call(RuntimeAddress(bs->pre_barrier_c1_runtime_code_blob()->code_begin()));
  __ jmp(*stub->continuation());

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
  assert_different_registers(obj, res, addr, tmp1, tmp2);

  Label slow_path;

  assert(res == rax, "result must arrive in rax");

  if (res != obj) {
    __ mov(res, obj);
  }

  if (is_strong) {
    // Check for object being in the collection set.
    __ mov(tmp1, res);
    if (AOTCodeCache::is_on_for_dump()) {
      __ push(rcx);
      __ lea(rcx, ExternalAddress(AOTRuntimeConstants::grain_shift_address()));
      __ movl(rcx, Address(rcx));
      if (tmp1 != rcx) {
        __ mov(tmp1, res);
        __ shrptr(tmp1);
        __ pop(rcx);
      } else {
        assert_different_registers(tmp2, rcx);
        __ mov(tmp2, res);
        __ shrptr(tmp2);
        __ pop(rcx);
        __ movptr(tmp1, tmp2);
      }
      __ lea(tmp2, ExternalAddress(AOTRuntimeConstants::cset_base_address()));
      __ movptr(tmp2, Address(tmp2));
    } else {
      __ shrptr(tmp1, ShenandoahHeapRegion::region_size_bytes_shift_jint());
      __ movptr(tmp2, (intptr_t) ShenandoahHeap::in_cset_fast_test_addr());
    }
    __ movbool(tmp2, Address(tmp2, tmp1, Address::times_1));
    __ testbool(tmp2);
    __ jcc(Assembler::zero, *stub->continuation());
  }

  __ bind(slow_path);
  ce->store_parameter(res, 0);
  ce->store_parameter(addr, 1);
  if (is_strong) {
    if (is_native) {
      __ call(RuntimeAddress(bs->load_reference_barrier_strong_native_rt_code_blob()->code_begin()));
    } else {
      __ call(RuntimeAddress(bs->load_reference_barrier_strong_rt_code_blob()->code_begin()));
    }
  } else if (is_weak) {
    __ call(RuntimeAddress(bs->load_reference_barrier_weak_rt_code_blob()->code_begin()));
  } else {
    assert(is_phantom, "only remaining strength");
    __ call(RuntimeAddress(bs->load_reference_barrier_phantom_rt_code_blob()->code_begin()));
  }
  __ jmp(*stub->continuation());
}

#undef __

#define __ sasm->

void ShenandoahBarrierSetAssembler::generate_c1_pre_barrier_runtime_stub(StubAssembler* sasm) {
  __ prologue("shenandoah_pre_barrier", false);
  // arg0 : previous value of memory

  __ push(rax);
  __ push(rdx);

  const Register pre_val = rax;
  const Register thread = r15_thread;
  const Register tmp = rdx;

  Address queue_index(thread, in_bytes(ShenandoahThreadLocalData::satb_mark_queue_index_offset()));
  Address buffer(thread, in_bytes(ShenandoahThreadLocalData::satb_mark_queue_buffer_offset()));

  Label done;
  Label runtime;

  // Is SATB still active?
  Address gc_state(thread, in_bytes(ShenandoahThreadLocalData::gc_state_offset()));
  __ testb(gc_state, ShenandoahHeap::MARKING);
  __ jcc(Assembler::zero, done);

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

  __ save_live_registers_no_oop_map(true);

  // load the pre-value
  __ load_parameter(0, rcx);
  __ call_VM_leaf(CAST_FROM_FN_PTR(address, ShenandoahRuntime::write_barrier_pre), rcx);

  __ restore_live_registers(true);

  __ bind(done);

  __ pop(rdx);
  __ pop(rax);

  __ epilogue();
}

void ShenandoahBarrierSetAssembler::generate_c1_load_reference_barrier_runtime_stub(StubAssembler* sasm, DecoratorSet decorators) {
  __ prologue("shenandoah_load_reference_barrier", false);
  // arg0 : object to be resolved

  __ save_live_registers_no_oop_map(true);

  bool is_strong  = ShenandoahBarrierSet::is_strong_access(decorators);
  bool is_weak    = ShenandoahBarrierSet::is_weak_access(decorators);
  bool is_phantom = ShenandoahBarrierSet::is_phantom_access(decorators);
  bool is_native  = ShenandoahBarrierSet::is_native_access(decorators);

  __ load_parameter(0, c_rarg0);
  __ load_parameter(1, c_rarg1);
  if (is_strong) {
    if (is_native) {
      __ call_VM_leaf(CAST_FROM_FN_PTR(address, ShenandoahRuntime::load_reference_barrier_strong), c_rarg0, c_rarg1);
    } else {
      if (UseCompressedOops) {
        __ call_VM_leaf(CAST_FROM_FN_PTR(address, ShenandoahRuntime::load_reference_barrier_strong_narrow), c_rarg0, c_rarg1);
      } else {
        __ call_VM_leaf(CAST_FROM_FN_PTR(address, ShenandoahRuntime::load_reference_barrier_strong), c_rarg0, c_rarg1);
      }
    }
  } else if (is_weak) {
    assert(!is_native, "weak must not be called off-heap");
    if (UseCompressedOops) {
      __ call_VM_leaf(CAST_FROM_FN_PTR(address, ShenandoahRuntime::load_reference_barrier_weak_narrow), c_rarg0, c_rarg1);
    } else {
      __ call_VM_leaf(CAST_FROM_FN_PTR(address, ShenandoahRuntime::load_reference_barrier_weak), c_rarg0, c_rarg1);
    }
  } else {
    assert(is_phantom, "only remaining strength");
    assert(is_native, "phantom must only be called off-heap");
    __ call_VM_leaf(CAST_FROM_FN_PTR(address, ShenandoahRuntime::load_reference_barrier_phantom), c_rarg0, c_rarg1);
  }

  __ restore_live_registers_except_rax(true);

  __ epilogue();
}

#undef __

#endif // COMPILER1

#ifdef COMPILER2

#undef __
#define __ masm->

void ShenandoahBarrierSetAssembler::load_c2(const MachNode* node, MacroAssembler* masm, Register dst, Address src, bool narrow) {
  // Do the actual load. This load is the candidate for implicit null check, and MUST come first.
  if (narrow) {
    __ movl(dst, src);
  } else {
    __ movq(dst, src);
  }

  ShenandoahBarrierStubC2::load_post(masm, node, dst, src, noreg, noreg, narrow);
}

void ShenandoahBarrierSetAssembler::store_c2(const MachNode* node, MacroAssembler* masm,
                                             Address dst, bool dst_narrow,
                                             Register src, bool src_narrow,
                                             Register tmp) {

  ShenandoahBarrierStubC2::store_pre(masm, node, dst, tmp, noreg, noreg, dst_narrow);

  // Need to encode into tmp, because we cannot clobber src.
  if (dst_narrow && !src_narrow) {
    __ movq(tmp, src);
    if ((node->barrier_data() & ShenandoahBitNotNull) == 0) {
      __ encode_heap_oop(tmp);
    } else {
      __ encode_heap_oop_not_null(tmp);
    }
    src = tmp;
  }

  // Do the actual store
  if (dst_narrow) {
    __ movl(dst, src);
  } else {
    __ movq(dst, src);
  }

  ShenandoahBarrierStubC2::store_post(masm, node, dst, tmp, noreg);
}

void ShenandoahBarrierSetAssembler::compare_and_set_c2(const MachNode* node, MacroAssembler* masm,
                                                       Register res, Address addr,
                                                       Register oldval, Register newval, Register tmp,
                                                       bool narrow) {

  assert(oldval == rax, "must be in rax for implicit use in cmpxchg");

  // Oldval and newval can be in the same register, but all other registers should be
  // distinct for extra safety, as we shuffle register values around.
  assert_different_registers(oldval, tmp, addr.base(), addr.index());
  assert_different_registers(newval, tmp, addr.base(), addr.index());

  ShenandoahBarrierStubC2::load_store_pre(masm, node, addr, tmp, noreg, noreg, narrow);

  // CAS!
  __ lock();
  if (narrow) {
    __ cmpxchgl(newval, addr);
  } else {
    __ cmpxchgptr(newval, addr);
  }

  // If we need a boolean result out of CAS, set the flag appropriately and promote the result.
  if (res != noreg) {
    __ setcc(Assembler::equal, res);
  }

  ShenandoahBarrierStubC2::load_store_post(masm, node, addr, tmp, noreg);
}

void ShenandoahBarrierSetAssembler::get_and_set_c2(const MachNode* node, MacroAssembler* masm, Register newval, Address addr, Register tmp, bool narrow) {
  assert_different_registers(newval, tmp, addr.base(), addr.index());

  ShenandoahBarrierStubC2::load_store_pre(masm, node, addr, tmp, noreg, noreg, narrow);

  if (narrow) {
    __ xchgl(newval, addr);
  } else {
    __ xchgq(newval, addr);
  }

  ShenandoahBarrierStubC2::load_store_post(masm, node, addr, tmp, noreg);
}

#undef __
#define __ masm.

void ShenandoahBarrierStubC2::cardtable(MacroAssembler& masm, Address addr, Register tmp1, Register tmp2) {
  Assembler::InlineSkippedInstructionsCounter skip_counter(&masm);

  __ lea(tmp1, addr);
  __ shrptr(tmp1, CardTable::card_shift());
  __ addptr(tmp1, Address(r15_thread, in_bytes(ShenandoahThreadLocalData::card_table_offset())));
  Address card_address(tmp1, 0);

  assert(CardTable::dirty_card_val() == 0, "Encoding assumption");
  Label L_done;
  if (UseCondCardMark) {
    __ cmpb(card_address, 0);
    __ jccb(Assembler::equal, L_done);
  }
  if (UseCompressedOops && CompressedOops::base() == nullptr) {
    __ movb(card_address, r12);
  } else {
    __ movb(card_address, 0);
  }
  __ bind(L_done);
}

void ShenandoahBarrierStubC2::enter_if_gc_state(MacroAssembler& masm, const char test_state, Register tmp) {
  Assembler::InlineSkippedInstructionsCounter skip_counter(&masm);

  Address gc_state_fast(r15_thread, in_bytes(ShenandoahThreadLocalData::gc_state_fast_array_offset(test_state)));
  __ cmpb(gc_state_fast, 0);
  __ jcc(Assembler::notEqual, *entry());
  __ bind(*continuation());
}

void ShenandoahBarrierStubC2::emit_code(MacroAssembler& masm) {
  Assembler::InlineSkippedInstructionsCounter skip_counter(&masm);
  assert(_needs_keep_alive_barrier || _needs_load_ref_barrier, "Why are you here?");

  // On x86, there is a significant penalty with unaligned branch target, for example
  // when the target instruction straggles the fetch line. It makes (performance) sense
  // to spend some code size to align the target better.
  __ align(16);
  __ bind(*entry());

  // If we need to load ourselves, do it here.
  if (_do_load) {
    if (_narrow) {
      __ movl(_obj, _addr);
    } else {
      __ movq(_obj, _addr);
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
  // as another barrier is not needed.
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

void ShenandoahBarrierStubC2::keepalive(MacroAssembler& masm, Label* L_done) {
  Address gc_state_fast(r15_thread, in_bytes(ShenandoahThreadLocalData::gc_state_fast_array_offset(ShenandoahHeap::MARKING)));
  Address index(r15_thread, in_bytes(ShenandoahThreadLocalData::satb_mark_queue_index_offset()));
  Address buffer(r15_thread, in_bytes(ShenandoahThreadLocalData::satb_mark_queue_buffer_offset()));

  Label L_through, L_pop_and_slow;

  // If another barrier is enabled as well, do a runtime check for a specific barrier.
  if (_needs_load_ref_barrier) {
    assert(L_done == nullptr, "L_done is always null when _needs_load_ref_barrier is true");
    __ cmpb(gc_state_fast, 0);
    __ jcc(Assembler::equal, L_through);
  }

  // Need temp to work, allocate one now.
  bool tmp_live;
  Register tmp = select_temp_register(tmp_live);
  if (tmp_live) {
    __ push(tmp);
  }

  // Fast-path: put object into buffer.
  // If buffer is already full, go slow.
  __ movptr(tmp, index);
  __ subptr(tmp, wordSize);
  __ jccb(Assembler::below, L_pop_and_slow);
  __ movptr(index, tmp);
  __ addptr(tmp, buffer);

  // Store the object in queue.
  // If object is narrow, we need to decode it before inserting.
  // We can skip the re-encoding if we know that object is not preserved.
  if (_narrow) {
    __ decode_heap_oop_not_null(_obj);
  }
  __ movptr(Address(tmp, 0), _obj);
  if (_narrow && is_preserved(_obj)) {
    __ encode_heap_oop_not_null(_obj);
  }

  // Fast-path exits here.
  if (tmp_live) {
    __ pop(tmp);
  }

  if (L_done != nullptr) {
    __ jmp(*L_done);
  } else {
    __ jmp(L_through);
  }

  // Slow-path: call runtime to handle.
  // Need to pop tmp immediately for stack to remain aligned.
  __ bind(L_pop_and_slow);
  if (tmp_live) {
    __ pop(tmp);
  }
  {
    SaveLiveRegisters slr(&masm, this);

    // Shuffle in the arguments. The end result should be:
    //   c_rarg0 <-- obj
    if (c_rarg0 != _obj) {
      __ mov(c_rarg0, _obj);
    }

    // Go to runtime and handle the rest there.
    // Use rax as scratch, as it will be saved if live.
    __ call(RuntimeAddress(keepalive_runtime_entry_addr()), rax);
  }
  if (L_done != nullptr) {
    __ jmp(*L_done);
  } else {
    __ bind(L_through);
  }
}

void ShenandoahBarrierStubC2::lrb(MacroAssembler& masm) {
  Label L_pop_and_slow, L_slow;

  // If another barrier is enabled as well, do a runtime check for a specific barrier.
  if (_needs_keep_alive_barrier) {
    char state_to_check = ShenandoahHeap::HAS_FORWARDED | (_needs_load_ref_weak_barrier ? ShenandoahHeap::WEAK_ROOTS : 0);
    Address gc_state_fast(r15_thread, in_bytes(ShenandoahThreadLocalData::gc_state_fast_array_offset(state_to_check)));
    __ cmpb(gc_state_fast, 0);
    __ jcc(Assembler::equal, *continuation());
  }

  // If weak references are being processed, weak/phantom loads need to go slow,
  // regardless of their cset status.
  if (_needs_load_ref_weak_barrier) {
    Address gc_state_fast(r15_thread, in_bytes(ShenandoahThreadLocalData::gc_state_fast_array_offset(ShenandoahHeap::WEAK_ROOTS)));
    __ cmpb(gc_state_fast, 0);
    __ jccb(Assembler::notEqual, L_slow);
  }

  bool is_aot = AOTCodeCache::is_on_for_dump();

  // Need temp to work, allocate one now.
  bool tmp_live;
  Register tmp = select_temp_register(tmp_live, /* skip_reg1 = */ is_aot ? rcx : noreg);
  if (tmp_live) {
    __ push(tmp);
  }

  // Compute the cset bitmap index
  if (_narrow) {
    __ decode_heap_oop_not_null(tmp, _obj);
  } else {
    __ movptr(tmp, _obj);
  }

  Address cset_addr_arg;
  intptr_t cset_addr = reinterpret_cast<intptr_t>(ShenandoahHeap::in_cset_fast_test_addr());
  if (!is_aot && cset_addr < INT32_MAX) {
    // Cset bitmap is at easily encodeable address. Just use it as displacement.
    __ shrptr(tmp, ShenandoahHeapRegion::region_size_bytes_shift_jint());
    cset_addr_arg = Address(tmp, checked_cast<int>(cset_addr));
  } else {
    bool tmp2_live;
    Register tmp2 = select_temp_register(tmp2_live, /* skip_reg1 = */ tmp, /* skip_reg2 = */ is_aot ? rcx : noreg);
    if (tmp2_live) {
      __ push(tmp2);
    }
    if (is_aot) {
      // Generating AOT code, pull the cset bitmap and region shift from AOT table.
      assert_different_registers(tmp, tmp2, rcx);
      __ push(rcx);
      __ lea(rcx, ExternalAddress(AOTRuntimeConstants::grain_shift_address()));
      __ movl(rcx, Address(rcx));
      __ shrptr(tmp);
      __ pop(rcx);
      __ lea(tmp2, ExternalAddress(AOTRuntimeConstants::cset_base_address()));
      __ addptr(tmp, Address(tmp2));
    } else {
      // Cset bitmap is far away. Add its address fully.
      __ shrptr(tmp, ShenandoahHeapRegion::region_size_bytes_shift_jint());
      __ movptr(tmp2, cset_addr);
      __ addptr(tmp, tmp2);
    }
    if (tmp2_live) {
      __ pop(tmp2);
    }
    cset_addr_arg = Address(tmp, 0);
  }

  // Cset-check. Fall-through to slow if in collection set.
  __ cmpb(cset_addr_arg, 0);
  if (tmp_live) {
    __ jccb(Assembler::notEqual, L_pop_and_slow);
    __ pop(tmp);
    __ jmp(*continuation());
  } else {
    // Nothing else to do, jump back
    __ jcc(Assembler::equal, *continuation());
  }

  // Slow path
  __ bind(L_pop_and_slow);
  // Need to pop tmp immediately for stack to remain aligned.
  if (tmp_live) {
    __ pop(tmp);
  }
  __ bind(L_slow);

  // Obj is the result, need to temporarily stop preserving it.
  bool is_obj_preserved = is_preserved(_obj);
  if (is_obj_preserved) {
    dont_preserve(_obj);
  }
  {
    SaveLiveRegisters slr(&masm, this);

    assert_different_registers(rax, c_rarg0, c_rarg1);

    // Shuffle in the arguments. The end result should be:
    //   c_rarg0 <-- obj
    //   c_rarg1 <-- lea(addr)
    if (_obj == c_rarg0) {
      __ lea(c_rarg1, _addr);
    } else if (_obj == c_rarg1) {
      // Set up arguments in reverse, and then flip them
      __ lea(c_rarg0, _addr);
      __ xchgptr(c_rarg0, c_rarg1);
    } else {
      assert_different_registers(_obj, c_rarg0, c_rarg1);
      __ lea(c_rarg1, _addr);
      __ movptr(c_rarg0, _obj);
    }

    // Go to runtime and handle the rest there.
    // Use rax as scratch, as it will be clobbered by result anyway.
    __ call(RuntimeAddress(lrb_runtime_entry_addr()), rax);

    // Save the result where needed.
    if (_narrow) {
      __ movl(_obj, rax);
    } else if (_obj != rax) {
      __ movptr(_obj, rax);
    }
  }
  if (is_obj_preserved) {
    preserve(_obj);
  }

  __ jmp(*continuation());
}

int ShenandoahBarrierStubC2::available_gp_registers() {
  return Register::available_gp_registers();
}

bool ShenandoahBarrierStubC2::is_special_register(Register r) {
  return r == rsp || r == rbp || r == r12_heapbase || r == r15_thread;
}

void ShenandoahBarrierStubC2::post_init() {
  // Do nothing.
}

void ShenandoahBarrierStubC2::maybe_far_jump_if_zero(MacroAssembler& masm, Register reg) {
  if (_narrow) {
    __ testl(reg, reg);
  } else {
    __ testq(reg, reg);
  }
  __ jcc(Assembler::zero, *continuation());
}

#endif // COMPILER2
