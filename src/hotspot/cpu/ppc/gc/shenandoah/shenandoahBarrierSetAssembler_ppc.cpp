/*
 * Copyright (c) 2018, 2025, Red Hat, Inc. All rights reserved.
 * Copyright (c) 2012, 2025 SAP SE. All rights reserved.
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
#include "gc/shenandoah/shenandoahForwarding.hpp"
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

#define __ masm->

void ShenandoahBarrierSetAssembler::satb_write_barrier(MacroAssembler *masm,
                                                       Register base, RegisterOrConstant ind_or_offs,
                                                       Register tmp1, Register tmp2, Register tmp3,
                                                       MacroAssembler::PreservationLevel preservation_level) {
  if (ShenandoahSATBBarrier) {
    __ block_comment("satb_write_barrier (shenandoahgc) {");
    satb_write_barrier_impl(masm, 0, base, ind_or_offs, tmp1, tmp2, tmp3, preservation_level);
    __ block_comment("} satb_write_barrier (shenandoahgc)");
  }
}

void ShenandoahBarrierSetAssembler::load_reference_barrier(MacroAssembler *masm, DecoratorSet decorators,
                                                           Register base, RegisterOrConstant ind_or_offs,
                                                           Register dst,
                                                           Register tmp1, Register tmp2,
                                                           MacroAssembler::PreservationLevel preservation_level) {
  if (ShenandoahLoadRefBarrier) {
    __ block_comment("load_reference_barrier (shenandoahgc) {");
    load_reference_barrier_impl(masm, decorators, base, ind_or_offs, dst, tmp1, tmp2, preservation_level);
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
void ShenandoahBarrierSetAssembler::satb_write_barrier_impl(MacroAssembler *masm, DecoratorSet decorators,
                                                            Register base, RegisterOrConstant ind_or_offs,
                                                            Register pre_val,
                                                            Register tmp1, Register tmp2,
                                                            MacroAssembler::PreservationLevel preservation_level) {
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
                     : MacroAssembler::num_volatile_gp_regs) * BytesPerWord;
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

void ShenandoahBarrierSetAssembler::resolve_forward_pointer_not_null(MacroAssembler *masm, Register dst, Register tmp) {
  __ block_comment("resolve_forward_pointer_not_null (shenandoahgc) {");

  Register tmp1 = tmp,
           R0_tmp2 = R0;
  assert_different_registers(dst, tmp1, R0_tmp2, noreg);

  // If the object has been evacuated, the mark word layout is as follows:
  // | forwarding pointer (62-bit) | '11' (2-bit) |

  // The invariant that stack/thread pointers have the lowest two bits cleared permits retrieving
  // the forwarding pointer solely by inversing the lowest two bits.
  // This invariant follows inevitably from hotspot's minimal alignment.
  assert(markWord::marked_value <= (unsigned long) MinObjAlignmentInBytes,
         "marked value must not be higher than hotspot's minimal alignment");

  Label done;

  // Load the object's mark word.
  __ ld(tmp1, oopDesc::mark_offset_in_bytes(), dst);

  // Load the bit mask for the lock bits.
  __ li(R0_tmp2, markWord::lock_mask_in_place);

  // Check whether all bits matching the bit mask are set.
  // If that is the case, the object has been evacuated and the most significant bits form the forward pointer.
  __ andc_(R0_tmp2, R0_tmp2, tmp1);

  assert(markWord::lock_mask_in_place == markWord::marked_value,
         "marked value must equal the value obtained when all lock bits are being set");
  __ xori(tmp1, tmp1, markWord::lock_mask_in_place);
  __ isel(dst, CR0, Assembler::equal, false, tmp1);

  __ bind(done);
  __ block_comment("} resolve_forward_pointer_not_null (shenandoahgc)");
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
    MacroAssembler::PreservationLevel preservation_level) {
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
                     : MacroAssembler::num_volatile_gp_regs) * BytesPerWord;
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
      satb_write_barrier_impl(masm, 0, noreg, noreg, dst, tmp1, tmp2, preservation_level);
      __ block_comment("} keep_alive_barrier (shenandoahgc)");
    }
  }
}

void ShenandoahBarrierSetAssembler::store_check(MacroAssembler* masm, Register base, RegisterOrConstant ind_or_offs, Register tmp) {
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
  if (is_reference_type(type)) {
    if (ShenandoahSATBBarrier) {
      satb_write_barrier(masm, base, ind_or_offs, tmp1, tmp2, tmp3, preservation_level);
    }
  }

  BarrierSetAssembler::store_at(masm, decorators, type,
                                base, ind_or_offs,
                                val,
                                tmp1, tmp2, tmp3,
                                preservation_level);

  // No need for post barrier if storing null
  if (ShenandoahCardBarrier && is_reference_type(type) && val != noreg) {
    store_check(masm, base, ind_or_offs, tmp1);
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

// Special shenandoah CAS implementation that handles false negatives due
// to concurrent evacuation.  That is, the CAS operation is intended to succeed in
// the following scenarios (success criteria):
//  s1) The reference pointer ('base_addr') equals the expected ('expected') pointer.
//  s2) The reference pointer refers to the from-space version of an already-evacuated
//      object, whereas the expected pointer refers to the to-space version of the same object.
// Situations in which the reference pointer refers to the to-space version of an object
// and the expected pointer refers to the from-space version of the same object can not occur due to
// shenandoah's strong to-space invariant.  This also implies that the reference stored in 'new_val'
// can not refer to the from-space version of an already-evacuated object.
//
// To guarantee correct behavior in concurrent environments, two races must be addressed:
//  r1) A concurrent thread may heal the reference pointer (i.e., it is no longer referring to the
//      from-space version but to the to-space version of the object in question).
//      In this case, the CAS operation should succeed.
//  r2) A concurrent thread may mutate the reference (i.e., the reference pointer refers to an entirely different object).
//      In this case, the CAS operation should fail.
//
// By default, the value held in the 'result' register is zero to indicate failure of CAS,
// non-zero to indicate success.  If 'is_cae' is set, the result is the most recently fetched
// value from 'base_addr' rather than a boolean success indicator.
void ShenandoahBarrierSetAssembler::cmpxchg_oop(MacroAssembler *masm, Register base_addr,
                                                Register expected, Register new_val, Register tmp1, Register tmp2,
                                                bool is_cae, Register result) {
  __ block_comment("cmpxchg_oop (shenandoahgc) {");

  assert_different_registers(base_addr, new_val, tmp1, tmp2, result, R0);
  assert_different_registers(base_addr, expected, tmp1, tmp2, result, R0);

  // Potential clash of 'success_flag' and 'tmp' is being accounted for.
  Register success_flag  = is_cae ? noreg  : result,
           current_value = is_cae ? result : tmp1,
           tmp           = is_cae ? tmp1   : result,
           initial_value = tmp2;

  Label done, step_four;

  __ bind(step_four);

  /* ==== Step 1 ("Standard" CAS) ==== */
  // Fast path: The values stored in 'expected' and 'base_addr' are equal.
  // Given that 'expected' must refer to the to-space object of an evacuated object (strong to-space invariant),
  // no special processing is required.
  if (UseCompressedOops) {
    __ cmpxchgw(CR0, current_value, expected, new_val, base_addr, MacroAssembler::MemBarNone,
                false, success_flag, nullptr, true);
  } else {
    __ cmpxchgd(CR0, current_value, expected, new_val, base_addr, MacroAssembler::MemBarNone,
                false, success_flag, nullptr, true);
  }

  // Skip the rest of the barrier if the CAS operation succeeds immediately.
  // If it does not, the value stored at the address is either the from-space pointer of the
  // referenced object (success criteria s2)) or simply another object.
  __ beq(CR0, done);

  /* ==== Step 2 (Null check) ==== */
  // The success criteria s2) cannot be matched with a null pointer
  // (null pointers cannot be subject to concurrent evacuation).  The failure of the CAS operation is thus legitimate.
  __ cmpdi(CR0, current_value, 0);
  __ beq(CR0, done);

  /* ==== Step 3 (reference pointer refers to from-space version; success criteria s2)) ==== */
  // To check whether the reference pointer refers to the from-space version, the forward
  // pointer of the object referred to by the reference is resolved and compared against the expected pointer.
  // If this check succeed, another CAS operation is issued with the from-space pointer being the expected pointer.
  //
  // Save the potential from-space pointer.
  __ mr(initial_value, current_value);

  // Resolve forward pointer.
  if (UseCompressedOops) { __ decode_heap_oop_not_null(current_value); }
  resolve_forward_pointer_not_null(masm, current_value, tmp);
  if (UseCompressedOops) { __ encode_heap_oop_not_null(current_value); }

  if (!is_cae) {
    // 'success_flag' was overwritten by call to 'resovle_forward_pointer_not_null'.
    // Load zero into register for the potential failure case.
    __ li(success_flag, 0);
  }
  __ cmpd(CR0, current_value, expected);
  __ bne(CR0, done);

  // Discard fetched value as it might be a reference to the from-space version of an object.
  if (UseCompressedOops) {
    __ cmpxchgw(CR0, R0, initial_value, new_val, base_addr, MacroAssembler::MemBarNone,
                false, success_flag);
  } else {
    __ cmpxchgd(CR0, R0, initial_value, new_val, base_addr, MacroAssembler::MemBarNone,
                false, success_flag);
  }

  /* ==== Step 4 (Retry CAS with to-space pointer (success criteria s2) under race r1)) ==== */
  // The reference pointer could have been healed whilst the previous CAS operation was being performed.
  // Another CAS operation must thus be issued with the to-space pointer being the expected pointer.
  // If that CAS operation fails as well, race r2) must have occurred, indicating that
  // the operation failure is legitimate.
  //
  // To keep the code's size small and thus improving cache (icache) performance, this highly
  // unlikely case should be handled by the smallest possible code.  Instead of emitting a third,
  // explicit CAS operation, the code jumps back and reuses the first CAS operation (step 1)
  // (passed arguments are identical).
  //
  // A failure of the CAS operation in step 1 would imply that the overall CAS operation is supposed
  // to fail.  Jumping back to step 1 requires, however, that step 2 and step 3 are re-executed as well.
  // It is thus important to ensure that a re-execution of those steps does not put program correctness
  // at risk:
  // - Step 2: Either terminates in failure (desired result) or falls through to step 3.
  // - Step 3: Terminates if the comparison between the forwarded, fetched pointer and the expected value
  //           fails.  Unless the reference has been updated in the meanwhile once again, this is
  //           guaranteed to be the case.
  //           In case of a concurrent update, the CAS would be retried again. This is legitimate
  //           in terms of program correctness (even though it is not desired).
  __ bne(CR0, step_four);

  __ bind(done);
  __ block_comment("} cmpxchg_oop (shenandoahgc)");
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

void ShenandoahBarrierSetAssembler::gen_pre_barrier_stub(LIR_Assembler *ce, ShenandoahPreBarrierStub *stub) {
  __ block_comment("gen_pre_barrier_stub (shenandoahgc) {");

  ShenandoahBarrierSetC1 *bs = (ShenandoahBarrierSetC1*) BarrierSet::barrier_set()->barrier_set_c1();
  __ bind(*stub->entry());

  // GC status has already been verified by 'ShenandoahBarrierSetC1::pre_barrier'.
  // This stub is the slowpath of that function.

  assert(stub->pre_val()->is_register(), "pre_val must be a register");
  Register pre_val = stub->pre_val()->as_register();

  // If 'do_load()' returns false, the to-be-stored value is already available in 'stub->pre_val()'
  // ("preloaded mode" of the store barrier).
  if (stub->do_load()) {
    ce->mem2reg(stub->addr(), stub->pre_val(), T_OBJECT, stub->patch_code(), stub->info(), false);
  }

  // Fast path: Reference is null.
  __ cmpdi(CR0, pre_val, 0);
  __ bc_far_optimized(Assembler::bcondCRbiIs1_bhintNoHint, __ bi0(CR0, Assembler::equal), *stub->continuation());

  // Argument passing via the stack.
  __ std(pre_val, -8, R1_SP);

  __ load_const_optimized(R0, bs->pre_barrier_c1_runtime_code_blob()->code_begin());
  __ call_stub(R0);

  __ b(*stub->continuation());
  __ block_comment("} gen_pre_barrier_stub (shenandoahgc)");
}

void ShenandoahBarrierSetAssembler::gen_load_reference_barrier_stub(LIR_Assembler *ce,
                                                                    ShenandoahLoadReferenceBarrierStub *stub) {
  __ block_comment("gen_load_reference_barrier_stub (shenandoahgc) {");

  ShenandoahBarrierSetC1 *bs = (ShenandoahBarrierSetC1*) BarrierSet::barrier_set()->barrier_set_c1();
  __ bind(*stub->entry());

  Register obj  = stub->obj()->as_register();
  Register res  = stub->result()->as_register();
  Register addr = stub->addr()->as_pointer_register();
  Register tmp1 = stub->tmp1()->as_register();
  Register tmp2 = stub->tmp2()->as_register();
  assert_different_registers(addr, res, tmp1, tmp2);

#ifdef ASSERT
  // Ensure that 'res' is 'R3_ARG1' and contains the same value as 'obj' to reduce the number of required
  // copy instructions.
  assert(R3_RET == res, "res must be r3");
  __ cmpd(CR0, res, obj);
  __ asm_assert_eq("result register must contain the reference stored in obj");
#endif

  DecoratorSet decorators = stub->decorators();

  /* ==== Check whether region is in collection set ==== */
  // GC status (unstable) has already been verified by 'ShenandoahBarrierSetC1::load_reference_barrier_impl'.
  // This stub is the slowpath of that function.

  bool is_strong  = ShenandoahBarrierSet::is_strong_access(decorators);
  bool is_weak    = ShenandoahBarrierSet::is_weak_access(decorators);
  bool is_phantom = ShenandoahBarrierSet::is_phantom_access(decorators);
  bool is_native  = ShenandoahBarrierSet::is_native_access(decorators);

  if (is_strong) {
    // Check whether object is in collection set.
    __ load_const_optimized(tmp2, ShenandoahHeap::in_cset_fast_test_addr(), tmp1);
    __ srdi(tmp1, obj, ShenandoahHeapRegion::region_size_bytes_shift_jint());
    __ lbzx(tmp2, tmp1, tmp2);

    __ andi_(tmp2, tmp2, 1);
    __ bc_far_optimized(Assembler::bcondCRbiIs1_bhintNoHint, __ bi0(CR0, Assembler::equal), *stub->continuation());
  }

  address blob_addr = nullptr;

  if (is_strong) {
    if (is_native) {
      blob_addr = bs->load_reference_barrier_strong_native_rt_code_blob()->code_begin();
    } else {
      blob_addr = bs->load_reference_barrier_strong_rt_code_blob()->code_begin();
    }
  } else if (is_weak) {
    blob_addr = bs->load_reference_barrier_weak_rt_code_blob()->code_begin();
  } else {
    assert(is_phantom, "only remaining strength");
    blob_addr = bs->load_reference_barrier_phantom_rt_code_blob()->code_begin();
  }

  assert(blob_addr != nullptr, "code blob cannot be found");

  // Argument passing via the stack.  'obj' is passed implicitly (as asserted above).
  __ std(addr, -8, R1_SP);

  __ load_const_optimized(tmp1, blob_addr, tmp2);
  __ call_stub(tmp1);

  // 'res' is 'R3_RET'.  The result is thus already in the correct register.

  __ b(*stub->continuation());
  __ block_comment("} gen_load_reference_barrier_stub (shenandoahgc)");
}

#undef __

#define __ sasm->

void ShenandoahBarrierSetAssembler::generate_c1_pre_barrier_runtime_stub(StubAssembler *sasm) {
  __ block_comment("generate_c1_pre_barrier_runtime_stub (shenandoahgc) {");

  Label runtime, skip_barrier;
  BarrierSet *bs = BarrierSet::barrier_set();

  // Argument passing via the stack.
  const int caller_stack_slots = 3;

  Register R0_pre_val = R0;
  __ ld(R0, -8, R1_SP);
  Register R11_tmp1 = R11_scratch1;
  __ std(R11_tmp1, -16, R1_SP);
  Register R12_tmp2 = R12_scratch2;
  __ std(R12_tmp2, -24, R1_SP);

  /* ==== Check whether marking is active ==== */
  // Even though gc status was checked in 'ShenandoahBarrierSetAssembler::gen_pre_barrier_stub',
  // another check is required as a safepoint might have been reached in the meantime (JDK-8140588).
  __ lbz(R12_tmp2, in_bytes(ShenandoahThreadLocalData::gc_state_offset()), R16_thread);

  __ andi_(R12_tmp2, R12_tmp2, ShenandoahHeap::MARKING);
  __ beq(CR0, skip_barrier);

  /* ==== Add previous value directly to thread-local SATB mark queue ==== */
  // Check queue's capacity.  Jump to runtime if no free slot is available.
  __ ld(R12_tmp2, in_bytes(ShenandoahThreadLocalData::satb_mark_queue_index_offset()), R16_thread);
  __ cmpdi(CR0, R12_tmp2, 0);
  __ beq(CR0, runtime);

  // Capacity suffices.  Decrement the queue's size by one slot (size of one oop).
  __ addi(R12_tmp2, R12_tmp2, -wordSize);
  __ std(R12_tmp2, in_bytes(ShenandoahThreadLocalData::satb_mark_queue_index_offset()), R16_thread);

  // Enqueue the previous value and skip the runtime invocation.
  __ ld(R11_tmp1, in_bytes(ShenandoahThreadLocalData::satb_mark_queue_buffer_offset()), R16_thread);
  __ stdx(R0_pre_val, R11_tmp1, R12_tmp2);
  __ b(skip_barrier);

  __ bind(runtime);

  /* ==== Invoke runtime to commit SATB mark queue to gc and allocate a new buffer ==== */
  // Save to-be-preserved registers.
  const int nbytes_save = (MacroAssembler::num_volatile_regs + caller_stack_slots) * BytesPerWord;
  __ save_volatile_gprs(R1_SP, -nbytes_save);
  __ save_LR(R11_tmp1);
  __ push_frame_reg_args(nbytes_save, R11_tmp1);

  // Invoke runtime.
  __ call_VM_leaf(CAST_FROM_FN_PTR(address, ShenandoahRuntime::write_barrier_pre), R0_pre_val);

  // Restore to-be-preserved registers.
  __ pop_frame();
  __ restore_LR(R11_tmp1);
  __ restore_volatile_gprs(R1_SP, -nbytes_save);

  __ bind(skip_barrier);

  // Restore spilled registers.
  __ ld(R11_tmp1, -16, R1_SP);
  __ ld(R12_tmp2, -24, R1_SP);

  __ blr();
  __ block_comment("} generate_c1_pre_barrier_runtime_stub (shenandoahgc)");
}

void ShenandoahBarrierSetAssembler::generate_c1_load_reference_barrier_runtime_stub(StubAssembler *sasm,
                                                                                    DecoratorSet decorators) {
  __ block_comment("generate_c1_load_reference_barrier_runtime_stub (shenandoahgc) {");

  // Argument passing via the stack.
  const int caller_stack_slots = 1;

  // Save to-be-preserved registers.
  const int nbytes_save = (MacroAssembler::num_volatile_regs - 1 // 'R3_ARG1' is skipped
                           + caller_stack_slots) * BytesPerWord;
  __ save_volatile_gprs(R1_SP, -nbytes_save, true, false);

  // Load arguments from stack.
  // No load required, as assured by assertions in 'ShenandoahBarrierSetAssembler::gen_load_reference_barrier_stub'.
  Register R3_obj = R3_ARG1;
  Register R4_load_addr = R4_ARG2;
  __ ld(R4_load_addr, -8, R1_SP);

  Register R11_tmp = R11_scratch1;

  /* ==== Invoke runtime ==== */
  bool is_strong  = ShenandoahBarrierSet::is_strong_access(decorators);
  bool is_weak    = ShenandoahBarrierSet::is_weak_access(decorators);
  bool is_phantom = ShenandoahBarrierSet::is_phantom_access(decorators);
  bool is_native  = ShenandoahBarrierSet::is_native_access(decorators);

  address jrt_address = nullptr;

  if (is_strong) {
    if (is_native) {
      jrt_address = CAST_FROM_FN_PTR(address, ShenandoahRuntime::load_reference_barrier_strong);
    } else {
      if (UseCompressedOops) {
        jrt_address = CAST_FROM_FN_PTR(address, ShenandoahRuntime::load_reference_barrier_strong_narrow);
      } else {
        jrt_address = CAST_FROM_FN_PTR(address, ShenandoahRuntime::load_reference_barrier_strong);
      }
    }
  } else if (is_weak) {
    assert(!is_native, "weak load reference barrier must not be called off-heap");
    if (UseCompressedOops) {
      jrt_address = CAST_FROM_FN_PTR(address, ShenandoahRuntime::load_reference_barrier_weak_narrow);
    } else {
      jrt_address = CAST_FROM_FN_PTR(address, ShenandoahRuntime::load_reference_barrier_weak);
    }
  } else {
    assert(is_phantom, "reference type must be phantom");
    assert(is_native, "phantom load reference barrier must be called off-heap");
    jrt_address = CAST_FROM_FN_PTR(address, ShenandoahRuntime::load_reference_barrier_phantom);
  }
  assert(jrt_address != nullptr, "load reference barrier runtime routine cannot be found");

  __ save_LR(R11_tmp);
  __ push_frame_reg_args(nbytes_save, R11_tmp);

  // Invoke runtime.  Arguments are already stored in the corresponding registers.
  __ call_VM_leaf(jrt_address, R3_obj, R4_load_addr);

  // Restore to-be-preserved registers.
  __ pop_frame();
  __ restore_LR(R11_tmp);
  __ restore_volatile_gprs(R1_SP, -nbytes_save, true, false); // Skip 'R3_RET' register.

  __ blr();
  __ block_comment("} generate_c1_load_reference_barrier_runtime_stub (shenandoahgc)");
}

#undef __

#endif // COMPILER1
