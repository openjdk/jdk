/*
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

#define __ masm->

static void save_machine_state(MacroAssembler* masm, bool handle_gpr, bool handle_fp) {
  if (handle_gpr) {
    __ push_IU_state();
  }

  if (handle_fp) {
    // Some paths can be reached from the c2i adapter with live fp arguments in registers.
    assert(Argument::n_float_register_parameters_j == 8, "8 fp registers to save at java call");

    const int xmm_size = wordSize * 2;
    __ subptr(rsp, xmm_size * 8);
    __ movdbl(Address(rsp, xmm_size * 0), xmm0);
    __ movdbl(Address(rsp, xmm_size * 1), xmm1);
    __ movdbl(Address(rsp, xmm_size * 2), xmm2);
    __ movdbl(Address(rsp, xmm_size * 3), xmm3);
    __ movdbl(Address(rsp, xmm_size * 4), xmm4);
    __ movdbl(Address(rsp, xmm_size * 5), xmm5);
    __ movdbl(Address(rsp, xmm_size * 6), xmm6);
    __ movdbl(Address(rsp, xmm_size * 7), xmm7);
  }
}

static void restore_machine_state(MacroAssembler* masm, bool handle_gpr, bool handle_fp) {
  if (handle_fp) {
    const int xmm_size = wordSize * 2;
    __ movdbl(xmm0, Address(rsp, xmm_size * 0));
    __ movdbl(xmm1, Address(rsp, xmm_size * 1));
    __ movdbl(xmm2, Address(rsp, xmm_size * 2));
    __ movdbl(xmm3, Address(rsp, xmm_size * 3));
    __ movdbl(xmm4, Address(rsp, xmm_size * 4));
    __ movdbl(xmm5, Address(rsp, xmm_size * 5));
    __ movdbl(xmm6, Address(rsp, xmm_size * 6));
    __ movdbl(xmm7, Address(rsp, xmm_size * 7));
    __ addptr(rsp, xmm_size * 8);
  }

  if (handle_gpr) {
    __ pop_IU_state();
  }
}

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

      save_machine_state(masm, /* handle_gpr = */ true, /* handle_fp = */ false);

      assert(src == rdi, "expected");
      assert(dst == rsi, "expected");
      assert(count == rdx, "expected");
      if (UseCompressedOops) {
        __ call_VM_leaf(CAST_FROM_FN_PTR(address, ShenandoahRuntime::arraycopy_barrier_narrow_oop),
                        src, dst, count);
      } else {
        __ call_VM_leaf(CAST_FROM_FN_PTR(address, ShenandoahRuntime::arraycopy_barrier_oop),
                        src, dst, count);
      }

      restore_machine_state(masm, /* handle_gpr = */ true, /* handle_fp = */ false);

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

void ShenandoahBarrierSetAssembler::shenandoah_write_barrier_pre(MacroAssembler* masm,
                                                                 Register obj,
                                                                 Register pre_val,
                                                                 Register tmp,
                                                                 bool tosca_live,
                                                                 bool expand_call) {

  if (ShenandoahSATBBarrier) {
    satb_write_barrier_pre(masm, obj, pre_val, tmp, tosca_live, expand_call);
  }
}

void ShenandoahBarrierSetAssembler::satb_write_barrier_pre(MacroAssembler* masm,
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

  Address index(thread, in_bytes(ShenandoahThreadLocalData::satb_mark_queue_index_offset()));
  Address buffer(thread, in_bytes(ShenandoahThreadLocalData::satb_mark_queue_buffer_offset()));

  Address gc_state(thread, in_bytes(ShenandoahThreadLocalData::gc_state_offset()));
  __ testb(gc_state, ShenandoahHeap::MARKING);
  __ jcc(Assembler::zero, done);

  // Do we need to load the previous value?
  if (obj != noreg) {
    __ load_heap_oop(pre_val, Address(obj, 0), noreg, AS_RAW);
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
  // save the live input values
  if(tosca_live) __ push(rax);

  if (obj != noreg && obj != rax)
    __ push(obj);

  if (pre_val != rax)
    __ push(pre_val);

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

  // We move pre_val into c_rarg0 early, in order to avoid smashing it, should
  // pre_val be c_rarg1 (where the call prologue would copy thread argument).
  // Note: this should not accidentally smash thread, because thread is always r15.
  assert(thread != c_rarg0, "smashed arg");
  if (c_rarg0 != pre_val) {
    __ mov(c_rarg0, pre_val);
  }

  if (expand_call) {
    assert(pre_val != c_rarg1, "smashed arg");
    if (c_rarg1 != thread) {
      __ mov(c_rarg1, thread);
    }
    // Already moved pre_val into c_rarg0 above
    __ MacroAssembler::call_VM_leaf_base(CAST_FROM_FN_PTR(address, ShenandoahRuntime::write_ref_field_pre), 2);
  } else {
    __ call_VM_leaf(CAST_FROM_FN_PTR(address, ShenandoahRuntime::write_ref_field_pre), c_rarg0, thread);
  }

  // save the live input values
  if (pre_val != rax)
    __ pop(pre_val);

  if (obj != noreg && obj != rax)
    __ pop(obj);

  if(tosca_live) __ pop(rax);

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
    for (int i = 0; i < 8; i++) {
      Register r = as_Register(i);
      if (r != rsp && r != rbp && r != dst && r != src.base() && r != src.index()) {
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
    __ shrptr(tmp1, ShenandoahHeapRegion::region_size_bytes_shift_jint());
    __ movptr(tmp2, (intptr_t) ShenandoahHeap::in_cset_fast_test_addr());
    __ movbool(tmp1, Address(tmp1, tmp2, Address::times_1));
    __ testbool(tmp1);
    __ jcc(Assembler::zero, not_cset);
  }

  save_machine_state(masm, /* handle_gpr = */ false, /* handle_fp = */ true);

  // The rest is saved with the optimized path

  uint num_saved_regs = 4 + (dst != rax ? 1 : 0) + 4 + (UseAPX ? 16 : 0);
  __ subptr(rsp, num_saved_regs * wordSize);
  uint slot = num_saved_regs;
  if (dst != rax) {
    __ movptr(Address(rsp, (--slot) * wordSize), rax);
  }
  __ movptr(Address(rsp, (--slot) * wordSize), rcx);
  __ movptr(Address(rsp, (--slot) * wordSize), rdx);
  __ movptr(Address(rsp, (--slot) * wordSize), rdi);
  __ movptr(Address(rsp, (--slot) * wordSize), rsi);
  __ movptr(Address(rsp, (--slot) * wordSize), r8);
  __ movptr(Address(rsp, (--slot) * wordSize), r9);
  __ movptr(Address(rsp, (--slot) * wordSize), r10);
  __ movptr(Address(rsp, (--slot) * wordSize), r11);
  // Save APX extended registers r16–r31 if enabled
  if (UseAPX) {
    __ movptr(Address(rsp, (--slot) * wordSize), r16);
    __ movptr(Address(rsp, (--slot) * wordSize), r17);
    __ movptr(Address(rsp, (--slot) * wordSize), r18);
    __ movptr(Address(rsp, (--slot) * wordSize), r19);
    __ movptr(Address(rsp, (--slot) * wordSize), r20);
    __ movptr(Address(rsp, (--slot) * wordSize), r21);
    __ movptr(Address(rsp, (--slot) * wordSize), r22);
    __ movptr(Address(rsp, (--slot) * wordSize), r23);
    __ movptr(Address(rsp, (--slot) * wordSize), r24);
    __ movptr(Address(rsp, (--slot) * wordSize), r25);
    __ movptr(Address(rsp, (--slot) * wordSize), r26);
    __ movptr(Address(rsp, (--slot) * wordSize), r27);
    __ movptr(Address(rsp, (--slot) * wordSize), r28);
    __ movptr(Address(rsp, (--slot) * wordSize), r29);
    __ movptr(Address(rsp, (--slot) * wordSize), r30);
    __ movptr(Address(rsp, (--slot) * wordSize), r31);
  }
  // r12-r15 are callee saved in all calling conventions
  assert(slot == 0, "must use all slots");

  // Shuffle registers such that dst is in c_rarg0 and addr in c_rarg1.
  Register arg0 = c_rarg0, arg1 = c_rarg1;
  if (dst == arg1) {
    __ lea(arg0, src);
    __ xchgptr(arg1, arg0);
  } else {
    __ lea(arg1, src);
    __ movptr(arg0, dst);
  }

  if (is_strong) {
    if (is_narrow) {
      __ super_call_VM_leaf(CAST_FROM_FN_PTR(address, ShenandoahRuntime::load_reference_barrier_strong_narrow), arg0, arg1);
    } else {
      __ super_call_VM_leaf(CAST_FROM_FN_PTR(address, ShenandoahRuntime::load_reference_barrier_strong), arg0, arg1);
    }
  } else if (is_weak) {
    if (is_narrow) {
      __ super_call_VM_leaf(CAST_FROM_FN_PTR(address, ShenandoahRuntime::load_reference_barrier_weak_narrow), arg0, arg1);
    } else {
      __ super_call_VM_leaf(CAST_FROM_FN_PTR(address, ShenandoahRuntime::load_reference_barrier_weak), arg0, arg1);
    }
  } else {
    assert(is_phantom, "only remaining strength");
    assert(!is_narrow, "phantom access cannot be narrow");
    __ super_call_VM_leaf(CAST_FROM_FN_PTR(address, ShenandoahRuntime::load_reference_barrier_phantom), arg0, arg1);
  }

  // Restore APX extended registers r31–r16 if previously saved
  if (UseAPX) {
    __ movptr(r31, Address(rsp, (slot++) * wordSize));
    __ movptr(r30, Address(rsp, (slot++) * wordSize));
    __ movptr(r29, Address(rsp, (slot++) * wordSize));
    __ movptr(r28, Address(rsp, (slot++) * wordSize));
    __ movptr(r27, Address(rsp, (slot++) * wordSize));
    __ movptr(r26, Address(rsp, (slot++) * wordSize));
    __ movptr(r25, Address(rsp, (slot++) * wordSize));
    __ movptr(r24, Address(rsp, (slot++) * wordSize));
    __ movptr(r23, Address(rsp, (slot++) * wordSize));
    __ movptr(r22, Address(rsp, (slot++) * wordSize));
    __ movptr(r21, Address(rsp, (slot++) * wordSize));
    __ movptr(r20, Address(rsp, (slot++) * wordSize));
    __ movptr(r19, Address(rsp, (slot++) * wordSize));
    __ movptr(r18, Address(rsp, (slot++) * wordSize));
    __ movptr(r17, Address(rsp, (slot++) * wordSize));
    __ movptr(r16, Address(rsp, (slot++) * wordSize));
  }
  __ movptr(r11, Address(rsp, (slot++) * wordSize));
  __ movptr(r10, Address(rsp, (slot++) * wordSize));
  __ movptr(r9,  Address(rsp, (slot++) * wordSize));
  __ movptr(r8,  Address(rsp, (slot++) * wordSize));
  __ movptr(rsi, Address(rsp, (slot++) * wordSize));
  __ movptr(rdi, Address(rsp, (slot++) * wordSize));
  __ movptr(rdx, Address(rsp, (slot++) * wordSize));
  __ movptr(rcx, Address(rsp, (slot++) * wordSize));

  if (dst != rax) {
    __ movptr(dst, rax);
    __ movptr(rax, Address(rsp, (slot++) * wordSize));
  }

  assert(slot == num_saved_regs, "must use all slots");
  __ addptr(rsp, num_saved_regs * wordSize);

  restore_machine_state(masm, /* handle_gpr = */ false, /* handle_fp = */ true);

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
    save_machine_state(masm, /* handle_gpr = */ true, /* handle_fp = */ true);

    assert_different_registers(dst, tmp1, r15_thread);
    // Generate the SATB pre-barrier code to log the value of
    // the referent field in an SATB buffer.
    shenandoah_write_barrier_pre(masm /* masm */,
                                 noreg /* obj */,
                                 dst /* pre_val */,
                                 tmp1 /* tmp */,
                                 true /* tosca_live */,
                                 true /* expand_call */);

    restore_machine_state(masm, /* handle_gpr = */ true, /* handle_fp = */ true);
  }
}

void ShenandoahBarrierSetAssembler::store_check(MacroAssembler* masm, Register obj) {
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

  bool on_oop = is_reference_type(type);
  bool in_heap = (decorators & IN_HEAP) != 0;
  bool as_normal = (decorators & AS_NORMAL) != 0;
  if (on_oop && in_heap) {
    bool needs_pre_barrier = as_normal;

    // flatten object address if needed
    // We do it regardless of precise because we need the registers
    if (dst.index() == noreg && dst.disp() == 0) {
      if (dst.base() != tmp1) {
        __ movptr(tmp1, dst.base());
      }
    } else {
      __ lea(tmp1, dst);
    }

    assert_different_registers(val, tmp1, tmp2, tmp3, r15_thread);

    if (needs_pre_barrier) {
      shenandoah_write_barrier_pre(masm /*masm*/,
                                   tmp1 /* obj */,
                                   tmp2 /* pre_val */,
                                   tmp3  /* tmp */,
                                   val != noreg /* tosca_live */,
                                   false /* expand_call */);
    }

    BarrierSetAssembler::store_at(masm, decorators, type, Address(tmp1, 0), val, noreg, noreg, noreg);
    if (val != noreg) {
      if (ShenandoahCardBarrier) {
        store_check(masm, tmp1);
      }
    }
  } else {
    BarrierSetAssembler::store_at(masm, decorators, type, dst, val, tmp1, tmp2, tmp3);
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
    __ shrptr(tmp1, ShenandoahHeapRegion::region_size_bytes_shift_jint());
    __ movptr(tmp2, (intptr_t) ShenandoahHeap::in_cset_fast_test_addr());
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
  __ call_VM_leaf(CAST_FROM_FN_PTR(address, ShenandoahRuntime::write_ref_field_pre), rcx, thread);

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
