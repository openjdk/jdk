/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2018, 2025, Red Hat, Inc. All rights reserved.
 * Copyright (c) 2012, 2026 SAP SE. All rights reserved.
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
#include "gc/shared/gc_globals.hpp"
#include "gc/shared/gcArguments.hpp"
#include "gc/shenandoah/heuristics/shenandoahHeuristics.hpp"
#include "gc/shenandoah/mode/shenandoahMode.hpp"
#include "gc/shenandoah/shenandoahBarrierSet.hpp"
#include "gc/shenandoah/shenandoahBarrierSetAssembler.hpp"
#include "gc/shenandoah/shenandoahHeap.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahHeapRegion.hpp"
#include "gc/shenandoah/shenandoahRuntime.hpp"
#include "gc/shenandoah/shenandoahThreadLocalData.hpp"
#include "interpreter/interpreter.hpp"
#include "macroAssembler_ppc.hpp"
#include "runtime/javaThread.hpp"
#include "runtime/sharedRuntime.hpp"
#include "utilities/globalDefinitions.hpp"
#include "vm_version_ppc.hpp"
#ifdef COMPILER1
#include "c1/c1_LIRAssembler.hpp"
#include "c1/c1_MacroAssembler.hpp"
#include "gc/shenandoah/c1/shenandoahBarrierSetC1.hpp"
#endif
#ifdef COMPILER2
#include "gc/shenandoah/c2/shenandoahBarrierSetC2.hpp"
#endif

#define __ masm->

void ShenandoahBarrierSetAssembler::satb_barrier(MacroAssembler *masm,
                                                 Register base, RegisterOrConstant ind_or_offs,
                                                 Register tmp1, Register tmp2, Register tmp3,
                                                 MacroAssembler::PreservationLevel preservation_level,
                                                 int extra_stack_space) {
  if (ShenandoahSATBBarrier) {
    __ block_comment("satb_barrier (shenandoahgc) {");
    satb_barrier_impl(masm, 0, base, ind_or_offs, tmp1, tmp2, tmp3, preservation_level, extra_stack_space);
    __ block_comment("} satb_barrier (shenandoahgc)");
  }
}

void ShenandoahBarrierSetAssembler::load_reference_barrier(MacroAssembler *masm, DecoratorSet decorators,
                                                           Register base, RegisterOrConstant ind_or_offs,
                                                           Register dst,
                                                           Register tmp1, Register tmp2,
                                                           MacroAssembler::PreservationLevel preservation_level,
                                                           int extra_stack_space) {
  if (ShenandoahLoadRefBarrier) {
    __ block_comment("load_reference_barrier (shenandoahgc) {");
    load_reference_barrier_impl(masm, decorators, base, ind_or_offs, dst, tmp1, tmp2, preservation_level, extra_stack_space);
    __ block_comment("} load_reference_barrier (shenandoahgc)");
  }
}

void ShenandoahBarrierSetAssembler::arraycopy_prologue(MacroAssembler *masm, DecoratorSet decorators, BasicType type,
                                                       Register src, Register dst, Register count,
                                                       Register preserve1, Register preserve2) {
  Register R11_tmp = R11_scratch1;

  assert_different_registers(src, dst, count, R11_tmp, noreg);
  if (preserve1 != noreg) {
    // Technically not required, but likely to indicate an error.
    assert_different_registers(preserve1, preserve2);
  }

  /* ==== Check whether barrier is required (optimizations) ==== */
  // Fast path: Component type of array is not a reference type.
  if (!is_reference_type(type)) {
    return;
  }

  bool dest_uninitialized = (decorators & IS_DEST_UNINITIALIZED) != 0;

  // Fast path: No barrier required if for every barrier type, it is either disabled or would not store
  // any useful information.
  if ((!ShenandoahSATBBarrier || dest_uninitialized) && !ShenandoahLoadRefBarrier) {
    return;
  }

  __ block_comment("arraycopy_prologue (shenandoahgc) {");
  Label skip_prologue;

  // Fast path: Array is of length zero.
  __ cmpdi(CR0, count, 0);
  __ beq(CR0, skip_prologue);

  /* ==== Check whether barrier is required (gc state) ==== */
  __ lbz(R11_tmp, in_bytes(ShenandoahThreadLocalData::gc_state_offset()),
         R16_thread);

  // The set of garbage collection states requiring barriers depends on the available barrier types and the
  // type of the reference in question.
  // For instance, satb barriers may be skipped if it is certain that the overridden values are not relevant
  // for the garbage collector.
  const int required_states = ShenandoahSATBBarrier && dest_uninitialized
                              ? ShenandoahHeap::HAS_FORWARDED
                              : ShenandoahHeap::HAS_FORWARDED | ShenandoahHeap::MARKING;

  __ andi_(R11_tmp, R11_tmp, required_states);
  __ beq(CR0, skip_prologue);

  /* ==== Invoke runtime ==== */
  // Save to-be-preserved registers.
  int highest_preserve_register_index = 0;
  {
    if (preserve1 != noreg && preserve1->is_volatile()) {
      __ std(preserve1, -BytesPerWord * ++highest_preserve_register_index, R1_SP);
    }
    if (preserve2 != noreg && preserve2 != preserve1 && preserve2->is_volatile()) {
      __ std(preserve2, -BytesPerWord * ++highest_preserve_register_index, R1_SP);
    }

    __ std(src, -BytesPerWord * ++highest_preserve_register_index, R1_SP);
    __ std(dst, -BytesPerWord * ++highest_preserve_register_index, R1_SP);
    __ std(count, -BytesPerWord * ++highest_preserve_register_index, R1_SP);

    __ save_LR(R11_tmp);
    __ push_frame_reg_args(-BytesPerWord * highest_preserve_register_index,
                           R11_tmp);
  }

  // Invoke runtime.
  address jrt_address = nullptr;
  if (UseCompressedOops) {
    jrt_address = CAST_FROM_FN_PTR(address, ShenandoahRuntime::arraycopy_barrier_narrow_oop);
  } else {
    jrt_address = CAST_FROM_FN_PTR(address, ShenandoahRuntime::arraycopy_barrier_oop);
  }
  assert(jrt_address != nullptr, "jrt routine cannot be found");

  __ call_VM_leaf(jrt_address, src, dst, count);

  // Restore to-be-preserved registers.
  {
    __ pop_frame();
    __ restore_LR(R11_tmp);

    __ ld(count, -BytesPerWord * highest_preserve_register_index--, R1_SP);
    __ ld(dst, -BytesPerWord * highest_preserve_register_index--, R1_SP);
    __ ld(src, -BytesPerWord * highest_preserve_register_index--, R1_SP);

    if (preserve2 != noreg && preserve2 != preserve1 && preserve2->is_volatile()) {
      __ ld(preserve2, -BytesPerWord * highest_preserve_register_index--, R1_SP);
    }
    if (preserve1 != noreg && preserve1->is_volatile()) {
      __ ld(preserve1, -BytesPerWord * highest_preserve_register_index--, R1_SP);
    }
  }

  __ bind(skip_prologue);
  __ block_comment("} arraycopy_prologue (shenandoahgc)");
}

void ShenandoahBarrierSetAssembler::arraycopy_epilogue(MacroAssembler* masm, DecoratorSet decorators, BasicType type,
                                                       Register dst, Register count,
                                                       Register preserve) {
  if (ShenandoahCardBarrier && is_reference_type(type)) {
    __ block_comment("arraycopy_epilogue (shenandoahgc) {");
    gen_write_ref_array_post_barrier(masm, decorators, dst, count, preserve);
    __ block_comment("} arraycopy_epilogue (shenandoahgc)");
  }
}

// The to-be-enqueued value can either be determined
// - dynamically by passing the reference's address information (load mode) or
// - statically by passing a register the value is stored in (preloaded mode)
//   - for performance optimizations in cases where the previous value is known (currently not implemented) and
//   - for incremental-update barriers.
//
// decorators:  The previous value's decorator set.
//              In "load mode", the value must equal '0'.
// base:        Base register of the reference's address (load mode).
//              In "preloaded mode", the register must equal 'noreg'.
// ind_or_offs: Index or offset of the reference's address (load mode).
//              If 'base' equals 'noreg' (preloaded mode), the passed value is ignored.
// pre_val:     Register holding the to-be-stored value (preloaded mode).
//              In "load mode", this register acts as a temporary register and must
//              thus not be 'noreg'.  In "preloaded mode", its content will be sustained.
// tmp1/tmp2:   Temporary registers, one of which must be non-volatile in "preloaded mode".
void ShenandoahBarrierSetAssembler::satb_barrier_impl(MacroAssembler *masm, DecoratorSet decorators,
                                                      Register base, RegisterOrConstant ind_or_offs,
                                                      Register pre_val,
                                                      Register tmp1, Register tmp2,
                                                      MacroAssembler::PreservationLevel preservation_level,
                                                      int extra_stack_space) {
  assert(ShenandoahSATBBarrier, "Should be checked by caller");
  assert_different_registers(tmp1, tmp2, pre_val, noreg);

  Label skip_barrier;

  /* ==== Determine necessary runtime invocation preservation measures ==== */
  const bool needs_frame           = preservation_level >= MacroAssembler::PRESERVATION_FRAME_LR;
  const bool preserve_gp_registers = preservation_level >= MacroAssembler::PRESERVATION_FRAME_LR_GP_REGS;
  const bool preserve_fp_registers = preservation_level >= MacroAssembler::PRESERVATION_FRAME_LR_GP_FP_REGS;

  // Check whether marking is active.
  __ lbz(tmp1, in_bytes(ShenandoahThreadLocalData::gc_state_offset()), R16_thread);

  __ andi_(tmp1, tmp1, ShenandoahHeap::MARKING);
  __ beq(CR0, skip_barrier);

  /* ==== Determine the reference's previous value ==== */
  bool preloaded_mode = base == noreg;
  Register pre_val_save = noreg;

  if (preloaded_mode) {
    // Previous value has been passed to the method, so it must not be determined manually.
    // In case 'pre_val' is a volatile register, it must be saved across the C-call
    // as callers may depend on its value.
    // Unless the general purposes registers are saved anyway, one of the temporary registers
    // (i.e., 'tmp1' and 'tmp2') is used to the preserve 'pre_val'.
    if (!preserve_gp_registers && pre_val->is_volatile()) {
      pre_val_save = !tmp1->is_volatile() ? tmp1 : tmp2;
      assert(!pre_val_save->is_volatile(), "at least one of the temporary registers must be non-volatile");
    }

    if ((decorators & IS_NOT_NULL) != 0) {
#ifdef ASSERT
      __ cmpdi(CR0, pre_val, 0);
      __ asm_assert_ne("null oop is not allowed");
#endif // ASSERT
    } else {
      __ cmpdi(CR0, pre_val, 0);
      __ beq(CR0, skip_barrier);
    }
  } else {
    // Load from the reference address to determine the reference's current value (before the store is being performed).
    // Contrary to the given value in "preloaded mode", it is not necessary to preserve it.
    assert(decorators == 0, "decorator set must be empty");
    assert(base != noreg, "base must be a register");
    assert(!ind_or_offs.is_register() || ind_or_offs.as_register() != noreg, "ind_or_offs must be a register");
    if (UseCompressedOops) {
      __ lwz(pre_val, ind_or_offs, base);
    } else {
      __ ld(pre_val, ind_or_offs, base);
    }

    __ cmpdi(CR0, pre_val, 0);
    __ beq(CR0, skip_barrier);

    if (UseCompressedOops) {
      __ decode_heap_oop_not_null(pre_val);
    }
  }

  /* ==== Try to enqueue the to-be-stored value directly into thread's local SATB mark queue ==== */
  {
    Label runtime;
    Register Rbuffer = tmp1, Rindex = tmp2;

    // Check whether the queue has enough capacity to store another oop.
    // If not, jump to the runtime to commit the buffer and to allocate a new one.
    // (The buffer's index corresponds to the amount of remaining free space.)
    __ ld(Rindex, in_bytes(ShenandoahThreadLocalData::satb_mark_queue_index_offset()), R16_thread);
    __ cmpdi(CR0, Rindex, 0);
    __ beq(CR0, runtime); // If index == 0 (buffer is full), goto runtime.

    // Capacity suffices.  Decrement the queue's size by the size of one oop.
    // (The buffer is filled contrary to the heap's growing direction, i.e., it is filled downwards.)
    __ addi(Rindex, Rindex, -wordSize);
    __ std(Rindex, in_bytes(ShenandoahThreadLocalData::satb_mark_queue_index_offset()), R16_thread);

    // Enqueue the previous value and skip the invocation of the runtime.
    __ ld(Rbuffer, in_bytes(ShenandoahThreadLocalData::satb_mark_queue_buffer_offset()), R16_thread);
    __ stdx(pre_val, Rbuffer, Rindex);
    __ b(skip_barrier);

    __ bind(runtime);
  }

  /* ==== Invoke runtime to commit SATB mark queue to gc and allocate a new buffer ==== */
  // Save to-be-preserved registers.
  int nbytes_save = 0;

  if (needs_frame) {
    if (preserve_gp_registers) {
      nbytes_save = (preserve_fp_registers
                     ? MacroAssembler::num_volatile_gp_regs + MacroAssembler::num_volatile_fp_regs
                     : MacroAssembler::num_volatile_gp_regs) * BytesPerWord + extra_stack_space;
      __ save_volatile_gprs(R1_SP, -nbytes_save, preserve_fp_registers);
    }

    __ save_LR(tmp1);
    __ push_frame_reg_args(nbytes_save, tmp2);
  }

  if (!preserve_gp_registers && preloaded_mode && pre_val->is_volatile()) {
    assert(pre_val_save != noreg, "nv_save must not be noreg");

    // 'pre_val' register must be saved manually unless general-purpose are preserved in general.
    __ mr(pre_val_save, pre_val);
  }

  // Invoke runtime.
  __ call_VM_leaf(CAST_FROM_FN_PTR(address, ShenandoahRuntime::write_barrier_pre), pre_val);

  // Restore to-be-preserved registers.
  if (!preserve_gp_registers && preloaded_mode && pre_val->is_volatile()) {
    __ mr(pre_val, pre_val_save);
  }

  if (needs_frame) {
    __ pop_frame();
    __ restore_LR(tmp1);

    if (preserve_gp_registers) {
      __ restore_volatile_gprs(R1_SP, -nbytes_save, preserve_fp_registers);
    }
  }

  __ bind(skip_barrier);
}

// base:        Base register of the reference's address.
// ind_or_offs: Index or offset of the reference's address (load mode).
// dst:         Reference's address.  In case the object has been evacuated, this is the to-space version
//              of that object.
void ShenandoahBarrierSetAssembler::load_reference_barrier_impl(
    MacroAssembler *masm, DecoratorSet decorators,
    Register base, RegisterOrConstant ind_or_offs,
    Register dst,
    Register tmp1, Register tmp2,
    MacroAssembler::PreservationLevel preservation_level,
    int extra_stack_space) {
  if (ind_or_offs.is_register()) {
    assert_different_registers(tmp1, tmp2, base, ind_or_offs.as_register(), dst, noreg);
  } else {
    assert_different_registers(tmp1, tmp2, base, dst, noreg);
  }

  Label skip_barrier;

  bool is_strong  = ShenandoahBarrierSet::is_strong_access(decorators);
  bool is_weak    = ShenandoahBarrierSet::is_weak_access(decorators);
  bool is_phantom = ShenandoahBarrierSet::is_phantom_access(decorators);
  bool is_native  = ShenandoahBarrierSet::is_native_access(decorators);
  bool is_narrow  = UseCompressedOops && !is_native;

  /* ==== Check whether heap is stable ==== */
  __ lbz(tmp2, in_bytes(ShenandoahThreadLocalData::gc_state_offset()), R16_thread);

  if (is_strong) {
    // For strong references, the heap is considered stable if "has forwarded" is not active.
    __ andi_(tmp1, tmp2, ShenandoahHeap::HAS_FORWARDED | ShenandoahHeap::EVACUATION);
    __ beq(CR0, skip_barrier);
#ifdef ASSERT
    // "evacuation" -> (implies) "has forwarded".  If we reach this code, "has forwarded" must thus be set.
    __ andi_(tmp1, tmp1, ShenandoahHeap::HAS_FORWARDED);
    __ asm_assert_ne("'has forwarded' is missing");
#endif // ASSERT
  } else {
    // For all non-strong references, the heap is considered stable if not any of "has forwarded",
    // "root set processing", and "weak reference processing" is active.
    // The additional phase conditions are in place to avoid the resurrection of weak references (see JDK-8266440).
    Label skip_fastpath;
    __ andi_(tmp1, tmp2, ShenandoahHeap::WEAK_ROOTS);
    __ bne(CR0, skip_fastpath);

    __ andi_(tmp1, tmp2, ShenandoahHeap::HAS_FORWARDED | ShenandoahHeap::EVACUATION);
    __ beq(CR0, skip_barrier);
#ifdef ASSERT
    // "evacuation" -> (implies) "has forwarded".  If we reach this code, "has forwarded" must thus be set.
    __ andi_(tmp1, tmp1, ShenandoahHeap::HAS_FORWARDED);
    __ asm_assert_ne("'has forwarded' is missing");
#endif // ASSERT

    __ bind(skip_fastpath);
  }

  /* ==== Check whether region is in collection set ==== */
  if (is_strong) {
    // Shenandoah stores metadata on regions in a continuous area of memory in which a single byte corresponds to
    // an entire region of the shenandoah heap.  At present, only the least significant bit is of significance
    // and indicates whether the region is part of the collection set.
    //
    // All regions are of the same size and are always aligned by a power of two.
    // Any address can thus be shifted by a fixed number of bits to retrieve the address prefix shared by
    // all objects within that region (region identification bits).
    //
    //  | unused bits | region identification bits | object identification bits |
    //  (Region size depends on a couple of criteria, such as page size, user-provided arguments and the max heap size.
    //   The number of object identification bits can thus not be determined at compile time.)
    //
    // -------------------------------------------------------  <--- cs (collection set) base address
    // | lost space due to heap space base address                   -> 'ShenandoahHeap::in_cset_fast_test_addr()'
    // | (region identification bits contain heap base offset)
    // |------------------------------------------------------  <--- cs base address + (heap_base >> region size shift)
    // | collection set in the proper                                -> shift: 'region_size_bytes_shift_jint()'
    // |
    // |------------------------------------------------------  <--- cs base address + (heap_base >> region size shift)
    //                                                                               + number of regions
    __ load_const_optimized(tmp2, ShenandoahHeap::in_cset_fast_test_addr(), tmp1);
    __ srdi(tmp1, dst, ShenandoahHeapRegion::region_size_bytes_shift_jint());
    __ lbzx(tmp2, tmp1, tmp2);
    __ andi_(tmp2, tmp2, 1);
    __ beq(CR0, skip_barrier);
  }

  /* ==== Invoke runtime ==== */
  // Save to-be-preserved registers.
  int nbytes_save = 0;

  const bool needs_frame           = preservation_level >= MacroAssembler::PRESERVATION_FRAME_LR;
  const bool preserve_gp_registers = preservation_level >= MacroAssembler::PRESERVATION_FRAME_LR_GP_REGS;
  const bool preserve_fp_registers = preservation_level >= MacroAssembler::PRESERVATION_FRAME_LR_GP_FP_REGS;

  if (needs_frame) {
    if (preserve_gp_registers) {
      nbytes_save = (preserve_fp_registers
                     ? MacroAssembler::num_volatile_gp_regs + MacroAssembler::num_volatile_fp_regs
                     : MacroAssembler::num_volatile_gp_regs) * BytesPerWord + extra_stack_space;
      __ save_volatile_gprs(R1_SP, -nbytes_save, preserve_fp_registers);
    }

    __ save_LR(tmp1);
    __ push_frame_reg_args(nbytes_save, tmp1);
  }

  // Calculate the reference's absolute address.
  __ add(R4_ARG2, ind_or_offs, base);

  // Invoke runtime.
  address jrt_address = nullptr;

  if (is_strong) {
    if (is_narrow) {
      jrt_address = CAST_FROM_FN_PTR(address, ShenandoahRuntime::load_reference_barrier_strong_narrow);
    } else {
      jrt_address = CAST_FROM_FN_PTR(address, ShenandoahRuntime::load_reference_barrier_strong);
    }
  } else if (is_weak) {
    if (is_narrow) {
      jrt_address = CAST_FROM_FN_PTR(address, ShenandoahRuntime::load_reference_barrier_weak_narrow);
    } else {
      jrt_address = CAST_FROM_FN_PTR(address, ShenandoahRuntime::load_reference_barrier_weak);
    }
  } else {
    assert(is_phantom, "only remaining strength");
    assert(!is_narrow, "phantom access cannot be narrow");
    jrt_address = CAST_FROM_FN_PTR(address, ShenandoahRuntime::load_reference_barrier_phantom);
  }
  assert(jrt_address != nullptr, "jrt routine cannot be found");

  __ call_VM_leaf(jrt_address, dst /* reference */, R4_ARG2 /* reference address */);

  // Restore to-be-preserved registers.
  if (preserve_gp_registers) {
    __ mr(R0, R3_RET);
  } else {
    __ mr_if_needed(dst, R3_RET);
  }

  if (needs_frame) {
    __ pop_frame();
    __ restore_LR(tmp1);

    if (preserve_gp_registers) {
      __ restore_volatile_gprs(R1_SP, -nbytes_save, preserve_fp_registers);
      __ mr(dst, R0);
    }
  }

  __ bind(skip_barrier);
}

// base:           Base register of the reference's address.
// ind_or_offs:    Index or offset of the reference's address.
// L_handle_null:  An optional label that will be jumped to if the reference is null.
void ShenandoahBarrierSetAssembler::load_at(
    MacroAssembler *masm, DecoratorSet decorators, BasicType type,
    Register base, RegisterOrConstant ind_or_offs, Register dst,
    Register tmp1, Register tmp2,
    MacroAssembler::PreservationLevel preservation_level, Label *L_handle_null) {
  // Register must not clash, except 'base' and 'dst'.
  if (ind_or_offs.is_register()) {
    if (base != noreg) {
      assert_different_registers(tmp1, tmp2, base, ind_or_offs.register_or_noreg(), R0, noreg);
    }
    assert_different_registers(tmp1, tmp2, dst, ind_or_offs.register_or_noreg(), R0, noreg);
  } else {
    if (base == noreg) {
      assert_different_registers(tmp1, tmp2, base, R0, noreg);
    }
    assert_different_registers(tmp1, tmp2, dst, R0, noreg);
  }

  /* ==== Apply load barrier, if required ==== */
  if (ShenandoahBarrierSet::need_load_reference_barrier(decorators, type)) {
    assert(is_reference_type(type), "need_load_reference_barrier must check whether type is a reference type");

    // If 'dst' clashes with either 'base' or 'ind_or_offs', use an intermediate result register
    // to keep the values of those alive until the load reference barrier is applied.
    Register intermediate_dst = (dst == base || (ind_or_offs.is_register() && dst == ind_or_offs.as_register()))
                                ? tmp2
                                : dst;

    BarrierSetAssembler::load_at(masm, decorators, type,
                                 base, ind_or_offs,
                                 intermediate_dst,
                                 tmp1, noreg,
                                 preservation_level, L_handle_null);

    load_reference_barrier(masm, decorators,
                           base, ind_or_offs,
                           intermediate_dst,
                           tmp1, R0,
                           preservation_level);

    __ mr_if_needed(dst, intermediate_dst);
  } else {
    BarrierSetAssembler::load_at(masm, decorators, type,
                                 base, ind_or_offs,
                                 dst,
                                 tmp1, tmp2,
                                 preservation_level, L_handle_null);
  }

  /* ==== Apply keep-alive barrier, if required (e.g., to inhibit weak reference resurrection) ==== */
  if (ShenandoahBarrierSet::need_keep_alive_barrier(decorators, type)) {
    if (ShenandoahSATBBarrier) {
      __ block_comment("keep_alive_barrier (shenandoahgc) {");
      satb_barrier_impl(masm, 0, noreg, noreg, dst, tmp1, tmp2, preservation_level);
      __ block_comment("} keep_alive_barrier (shenandoahgc)");
    }
  }
}

void ShenandoahBarrierSetAssembler::card_barrier(MacroAssembler* masm, Register base, RegisterOrConstant ind_or_offs, Register tmp) {
  assert(ShenandoahCardBarrier, "Should have been checked by caller");
  assert_different_registers(base, tmp, R0);

  if (ind_or_offs.is_constant()) {
    __ add_const_optimized(base, base, ind_or_offs.as_constant(), tmp);
  } else {
    __ add(base, ind_or_offs.as_register(), base);
  }

  __ ld(tmp, in_bytes(ShenandoahThreadLocalData::card_table_offset()), R16_thread); /* tmp = *[R16_thread + card_table_offset] */
  __ srdi(base, base, CardTable::card_shift());
  __ li(R0, CardTable::dirty_card_val());
  __ stbx(R0, tmp, base);
}

// base:        Base register of the reference's address.
// ind_or_offs: Index or offset of the reference's address.
// val:         To-be-stored value/reference's new value.
void ShenandoahBarrierSetAssembler::store_at(MacroAssembler *masm, DecoratorSet decorators, BasicType type,
                                             Register base, RegisterOrConstant ind_or_offs, Register val,
                                             Register tmp1, Register tmp2, Register tmp3,
                                             MacroAssembler::PreservationLevel preservation_level) {
  // 1: non-reference types require no barriers
  if (!is_reference_type(type)) {
    BarrierSetAssembler::store_at(masm, decorators, type,
                                  base, ind_or_offs,
                                  val,
                                  tmp1, tmp2, tmp3,
                                  preservation_level);
    return;
  }

  bool storing_non_null = (val != noreg);

  // 2: pre-barrier: SATB needs the previous value
  if (ShenandoahBarrierSet::need_satb_barrier(decorators, type)) {
    satb_barrier(masm, base, ind_or_offs, tmp1, tmp2, tmp3, preservation_level);
  }

  // Store!
  BarrierSetAssembler::store_at(masm, decorators, type,
                                base, ind_or_offs,
                                val,
                                tmp1, tmp2, tmp3,
                                preservation_level);

  // 3: post-barrier: card barrier needs store address
  if (ShenandoahBarrierSet::need_card_barrier(decorators, type) && storing_non_null) {
    card_barrier(masm, base, ind_or_offs, tmp1);
  }
}

void ShenandoahBarrierSetAssembler::try_resolve_jobject_in_native(MacroAssembler *masm,
                                                                  Register dst, Register jni_env, Register obj,
                                                                  Register tmp, Label &slowpath) {
  __ block_comment("try_resolve_jobject_in_native (shenandoahgc) {");

  assert_different_registers(jni_env, obj, tmp);

  Label done;

  // Fast path: Reference is null (JNI tags are zero for null pointers).
  __ cmpdi(CR0, obj, 0);
  __ beq(CR0, done);

  // Resolve jobject using standard implementation.
  BarrierSetAssembler::try_resolve_jobject_in_native(masm, dst, jni_env, obj, tmp, slowpath);

  // Check whether heap is stable.
  __ lbz(tmp,
         in_bytes(ShenandoahThreadLocalData::gc_state_offset() - JavaThread::jni_environment_offset()),
         jni_env);

  __ andi_(tmp, tmp, ShenandoahHeap::EVACUATION | ShenandoahHeap::HAS_FORWARDED);
  __ bne(CR0, slowpath);

  __ bind(done);
  __ block_comment("} try_resolve_jobject_in_native (shenandoahgc)");
}

void ShenandoahBarrierSetAssembler::try_peek_weak_handle_in_nmethod(MacroAssembler *masm, Register weak_handle,
                                                                    Register obj, Register tmp, Label &slow_path) {
  __ block_comment("try_peek_weak_handle_in_nmethod (shenandoahgc) {");

  assert_different_registers(weak_handle, tmp, noreg);
  assert_different_registers(obj, tmp, noreg);


  Label done;

  // Peek weak handle using the standard implementation.
  BarrierSetAssembler::try_peek_weak_handle_in_nmethod(masm, weak_handle, obj, tmp, slow_path);

  // Check if the reference is null, and if it is, take the fast path.
  __ cmpdi(CR0, obj, 0);
  __ beq(CR0, done);

  // Check if the heap is under weak-reference/roots processing, in
  // which case we need to take the slow path.
  __ lbz(tmp, in_bytes(ShenandoahThreadLocalData::gc_state_offset()), R16_thread);
  __ andi_(tmp, tmp, ShenandoahHeap::WEAK_ROOTS);
  __ bne(CR0, slow_path);
  __ bind(done);

  __ block_comment("} try_peek_weak_handle_in_nmethod (shenandoahgc)");
}

void ShenandoahBarrierSetAssembler::gen_write_ref_array_post_barrier(MacroAssembler* masm, DecoratorSet decorators,
                                                                     Register addr, Register count, Register preserve) {
  assert(ShenandoahCardBarrier, "Should have been checked by caller");
  assert_different_registers(addr, count, R0);

  Label L_skip_loop, L_store_loop;

  __ sldi_(count, count, LogBytesPerHeapOop);

  // Zero length? Skip.
  __ beq(CR0, L_skip_loop);

  __ addi(count, count, -BytesPerHeapOop);
  __ add(count, addr, count);
  // Use two shifts to clear out those low order two bits! (Cannot opt. into 1.)
  __ srdi(addr, addr, CardTable::card_shift());
  __ srdi(count, count, CardTable::card_shift());
  __ subf(count, addr, count);
  __ ld(R0, in_bytes(ShenandoahThreadLocalData::card_table_offset()), R16_thread);
  __ add(addr, addr, R0);
  __ addi(count, count, 1);
  __ li(R0, 0);
  __ mtctr(count);

  // Byte store loop
  __ bind(L_store_loop);
  __ stb(R0, 0, addr);
  __ addi(addr, addr, 1);
  __ bdnz(L_store_loop);
  __ bind(L_skip_loop);
}

#undef __

#ifdef COMPILER1

#define __ ce->masm()->

void ShenandoahBarrierSetAssembler::keepalive_barrier_c1_stub(LIR_Assembler* ce, ShenandoahKeepaliveBarrierStub* stub) {
  __ block_comment("keepalive_barrier_stub (shenandoahgc) {");
  __ bind(*stub->entry());

  ShenandoahBarrierSetC1* bs = (ShenandoahBarrierSetC1*) BarrierSet::barrier_set()->barrier_set_c1();

  Register obj = stub->obj()->as_register();

  // If 'do_load()' returns false, the to-be-stored value is already available in 'obj'
  if (stub->do_load()) {
    ce->mem2reg(stub->addr(), stub->obj(), T_OBJECT, lir_patch_none, nullptr, false);
  }

  // Fast path: reference is null.
  __ cmpdi(CR0, obj, 0);
  __ bc_far_optimized(Assembler::bcondCRbiIs1_bhintNoHint, __ bi0(CR0, Assembler::equal), *stub->continuation());

  // Argument passing via the stack.
  __ std(obj, -8, R1_SP);

  address blob_addr = bs->keepalive_barrier_stub();
  __ load_const_optimized(R0, blob_addr);
  __ call_stub(R0);

  __ b(*stub->continuation());
  __ block_comment("} keepalive_barrier_stub (shenandoahgc)");
}

void ShenandoahBarrierSetAssembler::load_reference_barrier_c1_stub(LIR_Assembler* ce, ShenandoahLoadReferenceBarrierStub* stub) {
  __ block_comment("load_reference_barrier_stub (shenandoahgc) {");

  __ bind(*stub->entry());

  ShenandoahBarrierSetC1* bs = (ShenandoahBarrierSetC1*) BarrierSet::barrier_set()->barrier_set_c1();

  Register obj  = stub->obj()->as_register();
  Register addr = stub->addr()->as_pointer_register();
  Register slow_result = stub->slow_result()->as_register();
  assert_different_registers(obj, addr, slow_result);
  assert(slow_result == R3_RET, "C1 must know about our slow call result register");

  // Argument passing via the stack.
  __ std(obj,   -8, R1_SP);
  __ std(addr, -16, R1_SP);

  address blob_addr = bs->load_reference_barrier_stub(stub->decorators());
  __ load_const_optimized(R0, blob_addr);
  __ call_stub(R0);
  if (obj != slow_result) {
    __ mr(obj, slow_result);
  }

  __ b(*stub->continuation());
  __ block_comment("} load_reference_barrier_stub (shenandoahgc)");
}

#undef __

#define __ sasm->

void ShenandoahBarrierSetAssembler::keepalive_barrier_c1_runtime_stub(StubAssembler* sasm) {
  __ block_comment("keepalive_barrier_runtime_stub (shenandoahgc) {");

  Register obj  = R3_ARG1;
  Register tmp1 = R11_scratch1;
  Register tmp2 = R12_scratch2;

  // Save registers we are about to clobber
  __ std(obj,  -16, R1_SP);
  __ std(tmp1, -24, R1_SP);
  __ std(tmp2, -32, R1_SP);

  // Pull the arguments from stack
  __ ld(obj, -8, R1_SP);

  satb_barrier(sasm, noreg, noreg, obj, tmp1, tmp2, MacroAssembler::PRESERVATION_FRAME_LR_GP_FP_REGS, 4 * BytesPerWord);

  // Restore registers
  __ ld(tmp2, -32, R1_SP);
  __ ld(tmp1, -24, R1_SP);
  __ ld(obj,  -16, R1_SP);

  __ blr();
  __ block_comment("} keepalive_barrier_runtime_stub (shenandoahgc)");
}

void ShenandoahBarrierSetAssembler::load_reference_barrier_c1_runtime_stub(StubAssembler* sasm, DecoratorSet decorators) {
  __ block_comment("load_reference_barrier_runtime_stub (shenandoahgc) {");

  Register obj  = R3_ARG1;
  Register addr = R4_ARG2;
  Register tmp1 = R11_scratch1;
  Register tmp2 = R12_scratch2;

  // Save registers we are about to clobber
  __ std(addr, -24, R1_SP);
  __ std(tmp1, -32, R1_SP);
  __ std(tmp2, -40, R1_SP);

  // Pull the arguments from the stack
  __ ld(obj,    -8, R1_SP);
  __ ld(addr,  -16, R1_SP);

  load_reference_barrier(sasm, decorators, addr, noreg, obj, tmp1, tmp2,
                         MacroAssembler::PRESERVATION_FRAME_LR_GP_FP_REGS, 5 * BytesPerWord);

  // Restore registers
  __ ld(tmp2, -40, R1_SP);
  __ ld(tmp1, -32, R1_SP);
  __ ld(addr, -24, R1_SP);

  __ blr();
  __ block_comment("} load_reference_barrier_runtime_stub (shenandoahgc)");
}

#undef __

#endif // COMPILER1

#ifdef COMPILER2

#undef __
#define __ masm->

void ShenandoahBarrierSetAssembler::load_c2(const MachNode* node, MacroAssembler* masm, Register dst, Register addr, int disp, Register tmp1, Register tmp2, bool is_narrow, bool is_acquire) {
  if (is_narrow) {
    __ lwz(dst, disp, addr);
  } else {
    __ ld(dst, disp, addr);
  }
  if (is_acquire) {
    __ twi_0(dst);
    __ isync();
  }

  ShenandoahBarrierStubC2::load_post(masm, node, dst, Address(addr, disp), tmp1, tmp2, is_narrow);
}

void ShenandoahBarrierSetAssembler::store_c2(const MachNode* node, MacroAssembler* masm,
    Register dst, int disp, bool dst_narrow, Register src, bool src_narrow, Register tmp1, Register tmp2, Register tmp3) {

  ShenandoahBarrierStubC2::store_pre(masm, node, Address(dst, disp), tmp1, tmp2, tmp3, dst_narrow);

  if (dst_narrow && !src_narrow) {
    // Need to encode into tmp, because we cannot clobber src.
    if ((node->barrier_data() & ShenandoahBitNotNull) == 0) {
      src = __ encode_heap_oop(tmp1, src);
    } else {
      src = __ encode_heap_oop_not_null(tmp1, src);
    }
  }
  if (dst_narrow) {
    __ stw(src, disp, dst);
  } else {
    __ std(src, disp, dst);
  }

  ShenandoahBarrierStubC2::store_post(masm, node, Address(dst, disp), tmp1, tmp2);
}

void ShenandoahBarrierSetAssembler::compare_and_set_c2(const MachNode* node, MacroAssembler* masm, Register res, Register addr,
      Register oldval, Register newval, Register tmp1, Register tmp2, bool exchange, bool narrow, bool weak, bool acquire) {

  ShenandoahBarrierStubC2::load_store_pre(masm, node, addr, res, tmp1, tmp2, narrow);

  Register dest_current = exchange ? res : R0;
  Label no_update;
  int semantics = MacroAssembler::MemBarNone;

  if (acquire) {
    semantics = support_IRIW_for_not_multiple_copy_atomic_cpu ?
                  MacroAssembler::MemBarAcq : MacroAssembler::MemBarFenceAfter;
  }

  if (!exchange) { __ li(res, 0); }
  if (narrow) {
    __ cmpxchgw(CR0, dest_current, oldval, newval, addr,
                semantics, MacroAssembler::cmpxchgx_hint_atomic_update(),
                noreg, &no_update, true, weak);
  } else {
    __ cmpxchgd(CR0, dest_current, oldval, newval, addr,
                semantics, MacroAssembler::cmpxchgx_hint_atomic_update(),
                noreg, &no_update, true, weak);
  }
  if (!exchange) { __ li(res, 1); }

  ShenandoahBarrierStubC2::load_store_post(masm, node, Address(addr, 0), tmp1, tmp2);

  __ bind(no_update);
}

void ShenandoahBarrierSetAssembler::get_and_set_c2(const MachNode* node, MacroAssembler* masm, Register preval, Register newval, Register addr, Register tmp1, Register tmp2) {
  bool is_narrow = node->bottom_type()->isa_narrowoop();

  ShenandoahBarrierStubC2::load_store_pre(masm, node, addr, preval, tmp1, tmp2, is_narrow);

  if (is_narrow) {
    __ getandsetw(preval, newval, addr, MacroAssembler::cmpxchgx_hint_atomic_update());
  } else {
    __ getandsetd(preval, newval, addr, MacroAssembler::cmpxchgx_hint_atomic_update());
  }

  if (support_IRIW_for_not_multiple_copy_atomic_cpu) {
    __ isync();
  } else {
    __ sync();
  }

  ShenandoahBarrierStubC2::load_store_post(masm, node, Address(addr, 0), tmp1, tmp2);
}

#undef __
#define __ masm.

void ShenandoahBarrierStubC2::cardtable(MacroAssembler& masm, Address address, Register tmp1, Register tmp2) {
  Assembler::InlineSkippedInstructionsCounter skip_counter(&masm);
  assert_different_registers(tmp1, tmp2, address.index(), address.base());

  __ ld(tmp1, in_bytes(ShenandoahThreadLocalData::card_table_offset()), R16_thread);
  if (address.index() == noreg) {
    __ add_const_optimized(tmp2, address.base(), address.disp(), R0);
  } else {
    __ add(tmp2, address.index(), address.base());
    if (address.disp() != 0) {
      __ addi(tmp2, tmp2, address.disp());
    }
  }
  __ srdi(tmp2, tmp2, CardTable::card_shift());
  __ li(R0, CardTable::dirty_card_val());
  __ stbx(R0, tmp2, tmp1);
}

void ShenandoahBarrierStubC2::enter_if_gc_state(MacroAssembler& masm, const char test_state, Register tmp) {
  Assembler::InlineSkippedInstructionsCounter skip_counter(&masm);

  __ lbz(tmp, in_bytes(ShenandoahThreadLocalData::gc_state_fast_array_offset(test_state)), R16_thread);
  __ cmpdi(CR0, tmp, 0);
  // Branch to entry if not equal
  __ bc_far_optimized(Assembler::bcondCRbiIs0, __ bi0(CR0, Assembler::equal), *entry());
  // This is were the slowpath stub will return to
  __ bind(*continuation());
}

void ShenandoahBarrierStubC2::emit_code(MacroAssembler& masm) {
  Assembler::InlineSkippedInstructionsCounter skip_counter(&masm);
  assert(_needs_keep_alive_barrier || _needs_load_ref_barrier, "Why are you here?");

  __ bind(*entry());

  // If we need to load ourselves, do it here.
  if (_do_load) {
    if (_narrow) {
      __ lwz(_obj, _addr.disp(), _addr.base());
    } else {
      __ ld(_obj, _addr.disp(), _addr.base());
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
  __ cmpdi(CR0, reg, 0);
  // Branch to continuation if equal
  __ bc_far_optimized(Assembler::bcondCRbiIs1, __ bi0(CR0, Assembler::equal), *continuation());
}

void ShenandoahBarrierStubC2::keepalive(MacroAssembler& masm, Label* L_done) {
  const int gcstate_offset = in_bytes(ShenandoahThreadLocalData::gc_state_fast_array_offset(ShenandoahHeap::MARKING));
  const int index_offset = in_bytes(ShenandoahThreadLocalData::satb_mark_queue_index_offset());
  const int buffer_offset = in_bytes(ShenandoahThreadLocalData::satb_mark_queue_buffer_offset());
  Label L_through, L_slowpath;

  // If another barrier is enabled as well, do a runtime check for a specific barrier.
  if (_needs_load_ref_barrier) {
    assert(L_done == nullptr, "L_done is always null when _needs_load_ref_barrier is true");
    __ lbz(_tmp1, gcstate_offset, R16_thread);
    __ cmpdi(CR0, _tmp1, 0);
    __ beq(CR0, L_through);
  }

  // Fast-path: put object into buffer.
  // If buffer is already full, go slow.
  __ ld(_tmp1, index_offset, R16_thread);
  __ cmpdi(CR0, _tmp1, 0);
  __ beq(CR0, L_slowpath);
  __ addi(_tmp1, _tmp1, -wordSize);
  __ std(_tmp1, index_offset, R16_thread);
  __ ld(_tmp2, buffer_offset, R16_thread);

  // Store the object in queue.
  // If object is narrow, we need to decode it before inserting.
  if (_narrow) {
    __ add(_tmp2, _tmp2, _tmp1);
    Register decoded = __ decode_heap_oop_not_null(_tmp1, _obj);
    __ stdx(decoded, _tmp2);
  } else {
    __ stdx(_obj, _tmp2, _tmp1);
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
    __ call_VM_leaf(keepalive_runtime_entry_addr(), _obj);
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
    __ lbz(_tmp1, in_bytes(ShenandoahThreadLocalData::gc_state_fast_array_offset(state_to_check)), R16_thread);
    maybe_far_jump_if_zero(masm, _tmp1);
  }

  // If weak references are being processed, weak/phantom loads need to go slow,
  // regardless of their cset status.
  if (_needs_load_ref_weak_barrier) {
    __ lbz(_tmp1, in_bytes(ShenandoahThreadLocalData::gc_state_fast_array_offset(ShenandoahHeap::WEAK_ROOTS)), R16_thread);
    __ cmpdi(CR0, _tmp1, 0);
    __ bne(CR0, L_slow);
  }

  // Cset-check. Fall-through to slow if in collection set.
  __ load_const_optimized(_tmp1, ShenandoahHeap::in_cset_fast_test_addr(), _tmp2);
  if (_narrow) {
    Register decoded = __ decode_heap_oop_not_null(_tmp2, _obj);
    __ srdi(_tmp2, decoded, ShenandoahHeapRegion::region_size_bytes_shift_jint());
  } else {
    __ srdi(_tmp2, _obj, ShenandoahHeapRegion::region_size_bytes_shift_jint());
  }
  __ lbzx(_tmp2, _tmp2, _tmp1);
  maybe_far_jump_if_zero(masm, _tmp2);

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
    Register c_rarg0 = R3_ARG1;
    Register c_rarg1 = R4_ARG2;
    if (c_rarg0 == _obj) {
      __ addi(c_rarg1, _addr.base(), _addr.disp());
    } else if (c_rarg1 == _obj) {
      __ mr(_tmp1, c_rarg1);
      __ addi(c_rarg1, _addr.base(), _addr.disp());
      __ mr(c_rarg0, _tmp1);
    } else {
      assert_different_registers(c_rarg1, _obj);
      __ addi(c_rarg1, _addr.base(), _addr.disp());
      __ mr(c_rarg0, _obj);
    }

    // Go to runtime and handle the rest there.
    __ call_VM_leaf(lrb_runtime_entry_addr(), c_rarg0, c_rarg1);

    // Save the result where needed.
    if (_obj != R3_RET) {
      __ mr(_obj, R3_RET);
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

void ShenandoahBarrierStubC2::post_init() {
  // Do nothing.
}

#endif // COMPILER2
