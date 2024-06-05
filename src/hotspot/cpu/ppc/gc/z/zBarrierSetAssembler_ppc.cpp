/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021, 2023 SAP SE. All rights reserved.
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
 */

#include "precompiled.hpp"
#include "asm/macroAssembler.inline.hpp"
#include "asm/register.hpp"
#include "code/codeBlob.hpp"
#include "code/vmreg.inline.hpp"
#include "gc/z/zAddress.hpp"
#include "gc/z/zBarrier.inline.hpp"
#include "gc/z/zBarrierSet.hpp"
#include "gc/z/zBarrierSetAssembler.hpp"
#include "gc/z/zBarrierSetRuntime.hpp"
#include "gc/z/zThreadLocalData.hpp"
#include "memory/resourceArea.hpp"
#include "register_ppc.hpp"
#include "runtime/jniHandles.hpp"
#include "runtime/sharedRuntime.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"
#ifdef COMPILER1
#include "c1/c1_LIRAssembler.hpp"
#include "c1/c1_MacroAssembler.hpp"
#include "gc/z/c1/zBarrierSetC1.hpp"
#endif // COMPILER1
#ifdef COMPILER2
#include "gc/z/c2/zBarrierSetC2.hpp"
#include "opto/output.hpp"
#endif // COMPILER2

#undef __
#define __ masm->

// Helper for saving and restoring registers across a runtime call that does
// not have any live vector registers.
class ZRuntimeCallSpill {
  MacroAssembler* _masm;
  Register _result;
  bool _needs_frame, _preserve_gp_registers, _preserve_fp_registers;
  int _nbytes_save;

  void save() {
    MacroAssembler* masm = _masm;

    if (_needs_frame) {
      if (_preserve_gp_registers) {
        bool preserve_R3 = _result != R3_ARG1;
        _nbytes_save = (MacroAssembler::num_volatile_gp_regs
                        + (_preserve_fp_registers ? MacroAssembler::num_volatile_fp_regs : 0)
                        - (preserve_R3 ? 0 : 1)
                       ) * BytesPerWord;
        __ save_volatile_gprs(R1_SP, -_nbytes_save, _preserve_fp_registers, preserve_R3);
      }

      __ save_LR_CR(R0);
      __ push_frame_reg_args(_nbytes_save, R0);
    }
  }

  void restore() {
    MacroAssembler* masm = _masm;

    Register result = R3_RET;
    if (_needs_frame) {
      __ pop_frame();
      __ restore_LR_CR(R0);

      if (_preserve_gp_registers) {
        bool restore_R3 = _result != R3_ARG1;
        if (restore_R3 && _result != noreg) {
          __ mr(R0, R3_RET);
          result = R0;
        }
        __ restore_volatile_gprs(R1_SP, -_nbytes_save, _preserve_fp_registers, restore_R3);
      }
    }
    if (_result != noreg) {
      __ mr_if_needed(_result, result);
    }
  }

public:
  ZRuntimeCallSpill(MacroAssembler* masm, Register result, MacroAssembler::PreservationLevel preservation_level)
    : _masm(masm),
      _result(result),
      _needs_frame(preservation_level >= MacroAssembler::PRESERVATION_FRAME_LR),
      _preserve_gp_registers(preservation_level >= MacroAssembler::PRESERVATION_FRAME_LR_GP_REGS),
      _preserve_fp_registers(preservation_level >= MacroAssembler::PRESERVATION_FRAME_LR_GP_FP_REGS),
      _nbytes_save(0) {
    save();
  }
  ~ZRuntimeCallSpill() {
    restore();
  }
};


void ZBarrierSetAssembler::load_at(MacroAssembler* masm, DecoratorSet decorators, BasicType type,
                                   Register base, RegisterOrConstant ind_or_offs, Register dst,
                                   Register tmp1, Register tmp2,
                                   MacroAssembler::PreservationLevel preservation_level, Label *L_handle_null) {
  __ block_comment("load_at (zgc) {");

  // Check whether a special gc barrier is required for this particular load
  // (e.g. whether it's a reference load or not)
  if (!ZBarrierSet::barrier_needed(decorators, type)) {
    BarrierSetAssembler::load_at(masm, decorators, type, base, ind_or_offs, dst,
                                 tmp1, tmp2, preservation_level, L_handle_null);
    return;
  }

  if (ind_or_offs.is_register()) {
    assert_different_registers(base, ind_or_offs.as_register(), tmp1, tmp2, R0, noreg);
    assert_different_registers(dst, ind_or_offs.as_register(), tmp1, tmp2, R0, noreg);
  } else {
    assert_different_registers(base, tmp1, tmp2, R0, noreg);
    assert_different_registers(dst, tmp1, tmp2, R0, noreg);
  }

  /* ==== Load the pointer using the standard implementation for the actual heap access
          and the decompression of compressed pointers ==== */
  // Result of 'load_at' (standard implementation) will be written back to 'dst'.
  // As 'base' is required for the C-call, it must be reserved in case of a register clash.
  Register saved_base = base;
  if (base == dst) {
    __ mr(tmp2, base);
    saved_base = tmp2;
  }

  __ ld(dst, ind_or_offs, base);

  /* ==== Check whether pointer is dirty ==== */
  Label done, uncolor;

  const bool on_non_strong =
    (decorators & ON_WEAK_OOP_REF) != 0 ||
    (decorators & ON_PHANTOM_OOP_REF) != 0;

  // Load bad mask into scratch register.
  if (on_non_strong) {
    __ ld(tmp1, in_bytes(ZThreadLocalData::mark_bad_mask_offset()), R16_thread);
  } else {
    __ ld(tmp1, in_bytes(ZThreadLocalData::load_bad_mask_offset()), R16_thread);
  }

  // The color bits of the to-be-tested pointer do not have to be equivalent to the 'bad_mask' testing bits.
  // A pointer is classified as dirty if any of the color bits that also match the bad mask is set.
  // Conversely, it follows that the logical AND of the bad mask and the pointer must be zero
  // if the pointer is not dirty.
  // Only dirty pointers must be processed by this barrier, so we can skip it in case the latter condition holds true.
  __ and_(tmp1, tmp1, dst);
  __ beq(CCR0, uncolor);

  /* ==== Invoke barrier ==== */
  {
    ZRuntimeCallSpill rcs(masm, dst, preservation_level);

    // Setup arguments
    if (saved_base != R3_ARG1 && ind_or_offs.register_or_noreg() != R3_ARG1) {
      __ mr_if_needed(R3_ARG1, dst);
      __ add(R4_ARG2, ind_or_offs, saved_base);
    } else if (dst != R4_ARG2) {
      __ add(R4_ARG2, ind_or_offs, saved_base);
      __ mr(R3_ARG1, dst);
    } else {
      __ add(R0, ind_or_offs, saved_base);
      __ mr(R3_ARG1, dst);
      __ mr(R4_ARG2, R0);
    }

    __ call_VM_leaf(ZBarrierSetRuntime::load_barrier_on_oop_field_preloaded_addr(decorators));
  }

  // Slow-path has already uncolored
  if (L_handle_null != nullptr) {
    __ cmpdi(CCR0, dst, 0);
    __ beq(CCR0, *L_handle_null);
  }
  __ b(done);

  __ bind(uncolor);
  if (L_handle_null == nullptr) {
    __ srdi(dst, dst, ZPointerLoadShift);
  } else {
    __ srdi_(dst, dst, ZPointerLoadShift);
    __ beq(CCR0, *L_handle_null);
  }

  __ bind(done);
  __ block_comment("} load_at (zgc)");
}

static void load_least_significant_16_oop_bits(MacroAssembler* masm, Register dst, RegisterOrConstant ind_or_offs, Register base) {
  assert_different_registers(dst, base);
#ifndef VM_LITTLE_ENDIAN
  const int BE_offset = 6;
  if (ind_or_offs.is_register()) {
    __ addi(dst, ind_or_offs.as_register(), BE_offset);
    __ lhzx(dst, base, dst);
  } else {
    __ lhz(dst, ind_or_offs.as_constant() + BE_offset, base);
  }
#else
  __ lhz(dst, ind_or_offs, base);
#endif
}

static void emit_store_fast_path_check(MacroAssembler* masm, Register base, RegisterOrConstant ind_or_offs, bool is_atomic, Label& medium_path) {
  if (is_atomic) {
    assert(ZPointerLoadShift + LogMinObjAlignmentInBytes >= 16, "or replace following code");
    load_least_significant_16_oop_bits(masm, R0, ind_or_offs, base);
    // Atomic operations must ensure that the contents of memory are store-good before
    // an atomic operation can execute.
    // A not relocatable object could have spurious raw null pointers in its fields after
    // getting promoted to the old generation.
    __ relocate(barrier_Relocation::spec(), ZBarrierRelocationFormatStoreGoodBits);
    __ cmplwi(CCR0, R0, barrier_Relocation::unpatched);
  } else {
    __ ld(R0, ind_or_offs, base);
    // Stores on relocatable objects never need to deal with raw null pointers in fields.
    // Raw null pointers may only exist in the young generation, as they get pruned when
    // the object is relocated to old. And no pre-write barrier needs to perform any action
    // in the young generation.
    __ relocate(barrier_Relocation::spec(), ZBarrierRelocationFormatStoreBadMask);
    __ andi_(R0, R0, barrier_Relocation::unpatched);
  }
  __ bc_far_optimized(Assembler::bcondCRbiIs0, __ bi0(CCR0, Assembler::equal), medium_path);
}

void ZBarrierSetAssembler::store_barrier_fast(MacroAssembler* masm,
                                              Register ref_base,
                                              RegisterOrConstant ind_or_offset,
                                              Register rnew_zaddress,
                                              Register rnew_zpointer,
                                              bool in_nmethod,
                                              bool is_atomic,
                                              Label& medium_path,
                                              Label& medium_path_continuation) const {
  assert_different_registers(ref_base, rnew_zpointer);
  assert_different_registers(ind_or_offset.register_or_noreg(), rnew_zpointer);
  assert_different_registers(rnew_zaddress, rnew_zpointer);

  if (in_nmethod) {
    emit_store_fast_path_check(masm, ref_base, ind_or_offset, is_atomic, medium_path);
    __ bind(medium_path_continuation);
    __ relocate(barrier_Relocation::spec(), ZBarrierRelocationFormatStoreGoodBits);
    __ li(rnew_zpointer, barrier_Relocation::unpatched); // Load color bits.
    if (rnew_zaddress == noreg) { // noreg encodes null.
      if (ZPointerLoadShift >= 16) {
        __ rldicl(rnew_zpointer, rnew_zpointer, 0, 64 - ZPointerLoadShift); // Clear sign extension from li.
      }
    }
  } else {
    __ ld(R0, ind_or_offset, ref_base);
    __ ld(rnew_zpointer, in_bytes(ZThreadLocalData::store_bad_mask_offset()), R16_thread);
    __ and_(R0, R0, rnew_zpointer);
    __ bne(CCR0, medium_path);
    __ bind(medium_path_continuation);
    __ ld(rnew_zpointer, in_bytes(ZThreadLocalData::store_good_mask_offset()), R16_thread);
  }
  if (rnew_zaddress != noreg) { // noreg encodes null.
    __ rldimi(rnew_zpointer, rnew_zaddress, ZPointerLoadShift, 0); // Insert shifted pointer.
  }
}

static void store_barrier_buffer_add(MacroAssembler* masm,
                                     Register ref_base,
                                     RegisterOrConstant ind_or_offs,
                                     Register tmp1,
                                     Label& slow_path) {
  __ ld(tmp1, in_bytes(ZThreadLocalData::store_barrier_buffer_offset()), R16_thread);

  // Combined pointer bump and check if the buffer is disabled or full
  __ ld(R0, in_bytes(ZStoreBarrierBuffer::current_offset()), tmp1);
  __ addic_(R0, R0, -(int)sizeof(ZStoreBarrierEntry));
  __ blt(CCR0, slow_path);
  __ std(R0, in_bytes(ZStoreBarrierBuffer::current_offset()), tmp1);

  // Entry is at ZStoreBarrierBuffer (tmp1) + buffer_offset + scaled index (R0)
  __ add(tmp1, tmp1, R0);

  // Compute and log the store address
  Register store_addr = ref_base;
  if (!ind_or_offs.is_constant() || ind_or_offs.as_constant() != 0) {
    __ add(R0, ind_or_offs, ref_base);
    store_addr = R0;
  }
  __ std(store_addr, in_bytes(ZStoreBarrierBuffer::buffer_offset()) + in_bytes(ZStoreBarrierEntry::p_offset()), tmp1);

  // Load and log the prev value
  __ ld(R0, ind_or_offs, ref_base);
  __ std(R0, in_bytes(ZStoreBarrierBuffer::buffer_offset()) + in_bytes(ZStoreBarrierEntry::prev_offset()), tmp1);
}

void ZBarrierSetAssembler::store_barrier_medium(MacroAssembler* masm,
                                                Register ref_base,
                                                RegisterOrConstant ind_or_offs,
                                                Register tmp,
                                                bool is_atomic,
                                                Label& medium_path_continuation,
                                                Label& slow_path) const {
  assert_different_registers(ref_base, tmp, R0);

  // The reason to end up in the medium path is that the pre-value was not 'good'.

  if (is_atomic) {
    // Atomic accesses can get to the medium fast path because the value was a
    // raw null value. If it was not null, then there is no doubt we need to take a slow path.
    __ ld(tmp, ind_or_offs, ref_base);
    __ cmpdi(CCR0, tmp, 0);
    __ bne(CCR0, slow_path);

    // If we get this far, we know there is a young raw null value in the field.
    // Try to self-heal null values for atomic accesses
    bool need_restore = false;
    if (!ind_or_offs.is_constant() || ind_or_offs.as_constant() != 0) {
      __ add(ref_base, ind_or_offs, ref_base);
      need_restore = true;
    }
    __ ld(R0, in_bytes(ZThreadLocalData::store_good_mask_offset()), R16_thread);
    __ cmpxchgd(CCR0, tmp, (intptr_t)0, R0, ref_base,
                MacroAssembler::MemBarNone, MacroAssembler::cmpxchgx_hint_atomic_update(),
                noreg, need_restore ? nullptr : &slow_path);
    if (need_restore) {
      __ subf(ref_base, ind_or_offs, ref_base);
      __ bne(CCR0, slow_path);
    }
  } else {
    // A non-atomic relocatable object won't get to the medium fast path due to a
    // raw null in the young generation. We only get here because the field is bad.
    // In this path we don't need any self healing, so we can avoid a runtime call
    // most of the time by buffering the store barrier to be applied lazily.
    store_barrier_buffer_add(masm,
                             ref_base,
                             ind_or_offs,
                             tmp,
                             slow_path);
  }
  __ b(medium_path_continuation);
}

// The Z store barrier only verifies the pointers it is operating on and is thus a sole debugging measure.
void ZBarrierSetAssembler::store_at(MacroAssembler* masm, DecoratorSet decorators, BasicType type,
                                    Register base, RegisterOrConstant ind_or_offs, Register val,
                                    Register tmp1, Register tmp2, Register tmp3,
                                    MacroAssembler::PreservationLevel preservation_level) {
  __ block_comment("store_at (zgc) {");

  bool dest_uninitialized = (decorators & IS_DEST_UNINITIALIZED) != 0;

  if (is_reference_type(type)) {
    assert_different_registers(base, val, tmp1, tmp2, tmp3);

    if (dest_uninitialized) {
      // tmp1 = (val << ZPointerLoadShift) | store_good_mask
      __ ld(tmp1, in_bytes(ZThreadLocalData::store_good_mask_offset()), R16_thread);
      if (val != noreg) { // noreg encodes null.
        __ rldimi(tmp1, val, ZPointerLoadShift, 0);
      }
    } else {
      Label done;
      Label medium;
      Label medium_continuation; // bound in store_barrier_fast
      Label slow;

      store_barrier_fast(masm, base, ind_or_offs, val, tmp1, false, false, medium, medium_continuation);
      __ b(done);
      __ bind(medium);
      store_barrier_medium(masm, base, ind_or_offs, tmp1, false, medium_continuation, slow);
      __ bind(slow);
      {
        ZRuntimeCallSpill rcs(masm, noreg, preservation_level);
        __ add(R3_ARG1, ind_or_offs, base);
        __ call_VM_leaf(ZBarrierSetRuntime::store_barrier_on_oop_field_without_healing_addr(), R3_ARG1);
      }
      __ b(medium_continuation);

      __ bind(done);
    }
    BarrierSetAssembler::store_at(masm, decorators, type, base, ind_or_offs, tmp1, tmp2, tmp3, noreg, preservation_level);
  } else {
    BarrierSetAssembler::store_at(masm, decorators, type, base, ind_or_offs, val, tmp1, tmp2, tmp3, preservation_level);
  }

  __ block_comment("} store_at (zgc)");
}

/* arraycopy */
const Register _load_bad_mask = R6, _store_bad_mask = R7, _store_good_mask = R8;

void ZBarrierSetAssembler::arraycopy_prologue(MacroAssembler *masm, DecoratorSet decorators, BasicType type,
                                              Register src, Register dst, Register count,
                                              Register preserve1, Register preserve2) {
  bool is_checkcast_copy  = (decorators & ARRAYCOPY_CHECKCAST)   != 0,
       dest_uninitialized = (decorators & IS_DEST_UNINITIALIZED) != 0;

  if (!ZBarrierSet::barrier_needed(decorators, type) || is_checkcast_copy) {
    // Barrier not needed
    return;
  }

  __ block_comment("arraycopy_prologue (zgc) {");

  load_copy_masks(masm, _load_bad_mask, _store_bad_mask, _store_good_mask, dest_uninitialized);

  __ block_comment("} arraycopy_prologue (zgc)");
}

void ZBarrierSetAssembler::load_copy_masks(MacroAssembler* masm,
                                           Register load_bad_mask,
                                           Register store_bad_mask,
                                           Register store_good_mask,
                                           bool dest_uninitialized) const {
  __ ld(load_bad_mask, in_bytes(ZThreadLocalData::load_bad_mask_offset()), R16_thread);
  __ ld(store_good_mask, in_bytes(ZThreadLocalData::store_good_mask_offset()), R16_thread);
  if (dest_uninitialized) {
    DEBUG_ONLY( __ li(store_bad_mask, -1); )
  } else {
    __ ld(store_bad_mask, in_bytes(ZThreadLocalData::store_bad_mask_offset()), R16_thread);
  }
}
void ZBarrierSetAssembler::copy_load_at_fast(MacroAssembler* masm,
                                             Register zpointer,
                                             Register addr,
                                             Register load_bad_mask,
                                             Label& slow_path,
                                             Label& continuation) const {
  __ ldx(zpointer, addr);
  __ and_(R0, zpointer, load_bad_mask);
  __ bne(CCR0, slow_path);
  __ bind(continuation);
}
void ZBarrierSetAssembler::copy_load_at_slow(MacroAssembler* masm,
                                             Register zpointer,
                                             Register addr,
                                             Register tmp,
                                             Label& slow_path,
                                             Label& continuation) const {
  __ align(32);
  __ bind(slow_path);
  __ mfctr(tmp); // preserve loop counter
  {
    ZRuntimeCallSpill rcs(masm, R0, MacroAssembler::PRESERVATION_FRAME_LR_GP_REGS);
    assert(zpointer != R4_ARG2, "or change argument setup");
    __ mr_if_needed(R4_ARG2, addr);
    __ call_VM_leaf(ZBarrierSetRuntime::load_barrier_on_oop_field_preloaded_addr(), zpointer, R4_ARG2);
  }
  __ sldi(zpointer, R0, ZPointerLoadShift); // Slow-path has uncolored; revert
  __ mtctr(tmp); // restore loop counter
  __ b(continuation);
}
void ZBarrierSetAssembler::copy_store_at_fast(MacroAssembler* masm,
                                              Register zpointer,
                                              Register addr,
                                              Register store_bad_mask,
                                              Register store_good_mask,
                                              Label& medium_path,
                                              Label& continuation,
                                              bool dest_uninitialized) const {
  if (!dest_uninitialized) {
    __ ldx(R0, addr);
    __ and_(R0, R0, store_bad_mask);
    __ bne(CCR0, medium_path);
    __ bind(continuation);
  }
  __ rldimi(zpointer, store_good_mask, 0, 64 - ZPointerLoadShift); // Replace color bits.
  __ stdx(zpointer, addr);
}
void ZBarrierSetAssembler::copy_store_at_slow(MacroAssembler* masm,
                                              Register addr,
                                              Register tmp,
                                              Label& medium_path,
                                              Label& continuation,
                                              bool dest_uninitialized) const {
  if (!dest_uninitialized) {
    Label slow_path;
    __ align(32);
    __ bind(medium_path);
    store_barrier_medium(masm, addr, (intptr_t)0, tmp, false, continuation, slow_path);
    __ bind(slow_path);
    __ mfctr(tmp); // preserve loop counter
    {
      ZRuntimeCallSpill rcs(masm, noreg, MacroAssembler::PRESERVATION_FRAME_LR_GP_REGS);
      __ call_VM_leaf(ZBarrierSetRuntime::store_barrier_on_oop_field_without_healing_addr(), addr);
    }
    __ mtctr(tmp); // restore loop counter
    __ b(continuation);
  }
}

// Arguments for generated stub:
//      from:  R3_ARG1
//      to:    R4_ARG2
//      count: R5_ARG3 (int >= 0)
void ZBarrierSetAssembler::generate_disjoint_oop_copy(MacroAssembler* masm, bool dest_uninitialized) {
  const Register zpointer = R2, tmp = R9;
  Label done, loop, load_bad, load_good, store_bad, store_good;
  __ cmpdi(CCR0, R5_ARG3, 0);
  __ beq(CCR0, done);
  __ mtctr(R5_ARG3);

  __ align(32);
  __ bind(loop);
  copy_load_at_fast(masm, zpointer, R3_ARG1, _load_bad_mask, load_bad, load_good);
  copy_store_at_fast(masm, zpointer, R4_ARG2, _store_bad_mask, _store_good_mask, store_bad, store_good, dest_uninitialized);
  __ addi(R3_ARG1, R3_ARG1, 8);
  __ addi(R4_ARG2, R4_ARG2, 8);
  __ bdnz(loop);

  __ bind(done);
  __ li(R3_RET, 0);
  __ blr();

  copy_load_at_slow(masm, zpointer, R3_ARG1, tmp, load_bad, load_good);
  copy_store_at_slow(masm, R4_ARG2, tmp, store_bad, store_good, dest_uninitialized);
}

void ZBarrierSetAssembler::generate_conjoint_oop_copy(MacroAssembler* masm, bool dest_uninitialized) {
  const Register zpointer = R2, tmp = R9;
  Label done, loop, load_bad, load_good, store_bad, store_good;
  __ sldi_(R0, R5_ARG3, 3);
  __ beq(CCR0, done);
  __ mtctr(R5_ARG3);
  // Point behind last elements and copy backwards.
  __ add(R3_ARG1, R3_ARG1, R0);
  __ add(R4_ARG2, R4_ARG2, R0);

  __ align(32);
  __ bind(loop);
  __ addi(R3_ARG1, R3_ARG1, -8);
  __ addi(R4_ARG2, R4_ARG2, -8);
  copy_load_at_fast(masm, zpointer, R3_ARG1, _load_bad_mask, load_bad, load_good);
  copy_store_at_fast(masm, zpointer, R4_ARG2, _store_bad_mask, _store_good_mask, store_bad, store_good, dest_uninitialized);
  __ bdnz(loop);

  __ bind(done);
  __ li(R3_RET, 0);
  __ blr();

  copy_load_at_slow(masm, zpointer, R3_ARG1, tmp, load_bad, load_good);
  copy_store_at_slow(masm, R4_ARG2, tmp, store_bad, store_good, dest_uninitialized);
}


// Verify a colored pointer.
void ZBarrierSetAssembler::check_oop(MacroAssembler *masm, Register obj, const char* msg) {
  if (!VerifyOops) {
    return;
  }
  Label done, skip_uncolor;
  // Skip (colored) null.
  __ srdi_(R0, obj, ZPointerLoadShift);
  __ beq(CCR0, done);

  // Check if ZAddressHeapBase << ZPointerLoadShift is set. If so, we need to uncolor.
  __ rldicl_(R0, obj, 64 - ZAddressHeapBaseShift - ZPointerLoadShift, 63);
  __ mr(R0, obj);
  __ beq(CCR0, skip_uncolor);
  __ srdi(R0, obj, ZPointerLoadShift);
  __ bind(skip_uncolor);

  __ verify_oop(R0, msg);
  __ bind(done);
}


void ZBarrierSetAssembler::try_resolve_jobject_in_native(MacroAssembler* masm, Register dst, Register jni_env,
                                                         Register obj, Register tmp, Label& slowpath) {
  __ block_comment("try_resolve_jobject_in_native (zgc) {");

  Label done, tagged, weak_tagged, check_color;
  Address load_bad_mask = load_bad_mask_from_jni_env(jni_env),
          mark_bad_mask = mark_bad_mask_from_jni_env(jni_env);

  // Test for tag
  __ andi_(tmp, obj, JNIHandles::tag_mask);
  __ bne(CCR0, tagged);

  // Resolve local handle
  __ ld(dst, 0, obj);
  __ b(done);

  __ bind(tagged);

  // Test for weak tag
  __ andi_(tmp, obj, JNIHandles::TypeTag::weak_global);
  __ clrrdi(dst, obj, JNIHandles::tag_size); // Untag.
  __ bne(CCR0, weak_tagged);

  // Resolve global handle
  __ ld(dst, 0, dst);
  __ ld(tmp, load_bad_mask.disp(), load_bad_mask.base());
  __ b(check_color);

  __ bind(weak_tagged);

  // Resolve weak handle
  __ ld(dst, 0, dst);
  __ ld(tmp, mark_bad_mask.disp(), mark_bad_mask.base());

  __ bind(check_color);
  __ and_(tmp, tmp, dst);
  __ bne(CCR0, slowpath);

  // Uncolor
  __ srdi(dst, dst, ZPointerLoadShift);

  __ bind(done);

  __ block_comment("} try_resolve_jobject_in_native (zgc)");
}

#undef __

#ifdef COMPILER1
#define __ ce->masm()->

static void z_uncolor(LIR_Assembler* ce, LIR_Opr ref) {
  Register r = ref->as_register();
  __ srdi(r, r, ZPointerLoadShift);
}

static void check_color(LIR_Assembler* ce, LIR_Opr ref, bool on_non_strong) {
  int relocFormat = on_non_strong ? ZBarrierRelocationFormatMarkBadMask
                                  : ZBarrierRelocationFormatLoadBadMask;
  __ relocate(barrier_Relocation::spec(), relocFormat);
  __ andi_(R0, ref->as_register(), barrier_Relocation::unpatched);
}

static void z_color(LIR_Assembler* ce, LIR_Opr ref) {
  __ sldi(ref->as_register(), ref->as_register(), ZPointerLoadShift);
  __ relocate(barrier_Relocation::spec(), ZBarrierRelocationFormatStoreGoodBits);
  __ ori(ref->as_register(), ref->as_register(), barrier_Relocation::unpatched);
}

void ZBarrierSetAssembler::generate_c1_uncolor(LIR_Assembler* ce, LIR_Opr ref) const {
  z_uncolor(ce, ref);
}

void ZBarrierSetAssembler::generate_c1_color(LIR_Assembler* ce, LIR_Opr ref) const {
  z_color(ce, ref);
}

void ZBarrierSetAssembler::generate_c1_load_barrier(LIR_Assembler* ce,
                                                    LIR_Opr ref,
                                                    ZLoadBarrierStubC1* stub,
                                                    bool on_non_strong) const {
  check_color(ce, ref, on_non_strong);
  __ bc_far_optimized(Assembler::bcondCRbiIs0, __ bi0(CCR0, Assembler::equal), *stub->entry());
  z_uncolor(ce, ref);
  __ bind(*stub->continuation());
}

// Code emitted by code stub "ZLoadBarrierStubC1" which in turn is emitted by ZBarrierSetC1::load_barrier.
// Invokes the runtime stub which is defined just below.
void ZBarrierSetAssembler::generate_c1_load_barrier_stub(LIR_Assembler* ce,
                                                         ZLoadBarrierStubC1* stub) const {
  __ block_comment("c1_load_barrier_stub (zgc) {");

  __ bind(*stub->entry());

  /* ==== Determine relevant data registers and ensure register sanity ==== */
  Register ref = stub->ref()->as_register();
  Register ref_addr = noreg;

  // Determine reference address
  if (stub->tmp()->is_valid()) {
    // 'tmp' register is given, so address might have an index or a displacement.
    ce->leal(stub->ref_addr(), stub->tmp());
    ref_addr = stub->tmp()->as_pointer_register();
  } else {
    // 'tmp' register is not given, so address must have neither an index nor a displacement.
    // The address' base register is thus usable as-is.
    assert(stub->ref_addr()->as_address_ptr()->disp() == 0, "illegal displacement");
    assert(!stub->ref_addr()->as_address_ptr()->index()->is_valid(), "illegal index");

    ref_addr = stub->ref_addr()->as_address_ptr()->base()->as_pointer_register();
  }

  assert_different_registers(ref, ref_addr, R0, noreg);

  /* ==== Invoke stub ==== */
  // Pass arguments via stack. The stack pointer will be bumped by the stub.
  __ std(ref, -1 * BytesPerWord, R1_SP);
  __ std(ref_addr, -2 * BytesPerWord, R1_SP);

  __ load_const_optimized(R0, stub->runtime_stub(), /* temp */ ref);
  __ call_stub(R0);

  // The runtime stub passes the result via the R0 register, overriding the previously-loaded stub address.
  __ mr(ref, R0);
  __ b(*stub->continuation());

  __ block_comment("} c1_load_barrier_stub (zgc)");
}

void ZBarrierSetAssembler::generate_c1_store_barrier(LIR_Assembler* ce,
                                                     LIR_Address* addr,
                                                     LIR_Opr new_zaddress,
                                                     LIR_Opr new_zpointer,
                                                     ZStoreBarrierStubC1* stub) const {
  Register rnew_zaddress = new_zaddress->as_register();
  Register rnew_zpointer = new_zpointer->as_register();

  Register rbase = addr->base()->as_pointer_register();
  RegisterOrConstant ind_or_offs = (addr->index()->is_illegal())
                                 ? (RegisterOrConstant)addr->disp()
                                 : (RegisterOrConstant)addr->index()->as_pointer_register();

  store_barrier_fast(ce->masm(),
                     rbase,
                     ind_or_offs,
                     rnew_zaddress,
                     rnew_zpointer,
                     true,
                     stub->is_atomic(),
                     *stub->entry(),
                     *stub->continuation());
}

void ZBarrierSetAssembler::generate_c1_store_barrier_stub(LIR_Assembler* ce,
                                                          ZStoreBarrierStubC1* stub) const {
  // Stub entry
  __ bind(*stub->entry());

  Label slow;

  LIR_Address* addr = stub->ref_addr()->as_address_ptr();
  assert(addr->index()->is_illegal() || addr->disp() == 0, "can't have both");
  Register rbase = addr->base()->as_pointer_register();
  RegisterOrConstant ind_or_offs = (addr->index()->is_illegal())
                                 ? (RegisterOrConstant)addr->disp()
                                 : (RegisterOrConstant)addr->index()->as_pointer_register();
  Register new_zpointer = stub->new_zpointer()->as_register();

  store_barrier_medium(ce->masm(),
                       rbase,
                       ind_or_offs,
                       new_zpointer, // temp
                       stub->is_atomic(),
                       *stub->continuation(),
                       slow);

  __ bind(slow);

  __ load_const_optimized(/*stub address*/ new_zpointer, stub->runtime_stub(), R0);
  __ add(R0, ind_or_offs, rbase); // pass store address in R0
  __ mtctr(new_zpointer);
  __ bctrl();

  // Stub exit
  __ b(*stub->continuation());
}

#undef __
#define __ sasm->

// Code emitted by runtime code stub which in turn is emitted by ZBarrierSetC1::generate_c1_runtime_stubs.
void ZBarrierSetAssembler::generate_c1_load_barrier_runtime_stub(StubAssembler* sasm,
                                                                 DecoratorSet decorators) const {
  __ block_comment("c1_load_barrier_runtime_stub (zgc) {");

  const int stack_parameters = 2;
  const int nbytes_save = (MacroAssembler::num_volatile_regs + stack_parameters) * BytesPerWord;

  __ save_volatile_gprs(R1_SP, -nbytes_save);
  __ save_LR_CR(R0);

  // Load arguments back again from the stack.
  __ ld(R3_ARG1, -1 * BytesPerWord, R1_SP); // ref
  __ ld(R4_ARG2, -2 * BytesPerWord, R1_SP); // ref_addr

  __ push_frame_reg_args(nbytes_save, R0);

  __ call_VM_leaf(ZBarrierSetRuntime::load_barrier_on_oop_field_preloaded_addr(decorators));

  __ verify_oop(R3_RET, "Bad pointer after barrier invocation");
  __ mr(R0, R3_RET);

  __ pop_frame();
  __ restore_LR_CR(R3_RET);
  __ restore_volatile_gprs(R1_SP, -nbytes_save);

  __ blr();

  __ block_comment("} c1_load_barrier_runtime_stub (zgc)");
}

void ZBarrierSetAssembler::generate_c1_store_barrier_runtime_stub(StubAssembler* sasm,
                                                                  bool self_healing) const {
  __ block_comment("c1_store_barrier_runtime_stub (zgc) {");

  const int nbytes_save = MacroAssembler::num_volatile_regs * BytesPerWord;
  __ save_volatile_gprs(R1_SP, -nbytes_save);
  __ mr(R3_ARG1, R0); // store address

  __ save_LR_CR(R0);
  __ push_frame_reg_args(nbytes_save, R0);

  if (self_healing) {
    __ call_VM_leaf(ZBarrierSetRuntime::store_barrier_on_oop_field_with_healing_addr());
  } else {
    __ call_VM_leaf(ZBarrierSetRuntime::store_barrier_on_oop_field_without_healing_addr());
  }

  __ pop_frame();
  __ restore_LR_CR(R3_RET);
  __ restore_volatile_gprs(R1_SP, -nbytes_save);

  __ blr();

  __ block_comment("} c1_store_barrier_runtime_stub (zgc)");
}

#undef __
#endif // COMPILER1

#ifdef COMPILER2

#undef __
#define __ _masm->

class ZSetupArguments {
  MacroAssembler* const _masm;
  const Register        _ref;
  const Address         _ref_addr;

 public:
  ZSetupArguments(MacroAssembler* masm, ZLoadBarrierStubC2* stub)
    : _masm(masm),
      _ref(stub->ref()),
      _ref_addr(stub->ref_addr()) {

    // Desired register/argument configuration:
    // _ref: R3_ARG1
    // _ref_addr: R4_ARG2

    // '_ref_addr' can be unspecified. In that case, the barrier will not heal the reference.
    if (_ref_addr.base() == noreg) {
      assert_different_registers(_ref, R0, noreg);

      __ mr_if_needed(R3_ARG1, _ref);
      __ li(R4_ARG2, 0);
    } else {
      assert_different_registers(_ref, _ref_addr.base(), R0, noreg);
      assert(!_ref_addr.index()->is_valid(), "reference addresses must not contain an index component");

      if (_ref != R4_ARG2) {
        // Calculate address first as the address' base register might clash with R4_ARG2
        __ addi(R4_ARG2, _ref_addr.base(), _ref_addr.disp());
        __ mr_if_needed(R3_ARG1, _ref);
      } else if (_ref_addr.base() != R3_ARG1) {
        __ mr(R3_ARG1, _ref);
        __ addi(R4_ARG2, _ref_addr.base(), _ref_addr.disp()); // Clobbering _ref
      } else {
        // Arguments are provided in inverse order (i.e. _ref == R4_ARG2, _ref_addr == R3_ARG1)
        __ mr(R0, _ref);
        __ addi(R4_ARG2, _ref_addr.base(), _ref_addr.disp());
        __ mr(R3_ARG1, R0);
      }
    }
  }
};

#undef __
#define __ masm->

void ZBarrierSetAssembler::generate_c2_load_barrier_stub(MacroAssembler* masm, ZLoadBarrierStubC2* stub) const {
  Assembler::InlineSkippedInstructionsCounter skipped_counter(masm);
  __ block_comment("generate_c2_load_barrier_stub (zgc) {");

  __ bind(*stub->entry());

  Register ref = stub->ref();
  Address ref_addr = stub->ref_addr();

  assert_different_registers(ref, ref_addr.base());

  {
    SaveLiveRegisters save_live_registers(masm, stub);
    ZSetupArguments setup_arguments(masm, stub);

    __ call_VM_leaf(stub->slow_path());
    __ mr_if_needed(ref, R3_RET);
  }

  __ b(*stub->continuation());

  __ block_comment("} generate_c2_load_barrier_stub (zgc)");
}

void ZBarrierSetAssembler::generate_c2_store_barrier_stub(MacroAssembler* masm, ZStoreBarrierStubC2* stub) const {
  Assembler::InlineSkippedInstructionsCounter skipped_counter(masm);
  __ block_comment("ZStoreBarrierStubC2");

  // Stub entry
  __ bind(*stub->entry());

  Label slow;

  Address addr = stub->ref_addr();
  Register rbase = addr.base();
  RegisterOrConstant ind_or_offs = (addr.index() == noreg)
                                 ? (RegisterOrConstant)addr.disp()
                                 : (RegisterOrConstant)addr.index();

  if (!stub->is_native()) {
    store_barrier_medium(masm,
                         rbase,
                         ind_or_offs,
                         stub->new_zpointer(),
                         stub->is_atomic(),
                         *stub->continuation(),
                         slow);
  }

  __ bind(slow);
  {
    SaveLiveRegisters save_live_registers(masm, stub);
    __ add(R3_ARG1, ind_or_offs, rbase);
    if (stub->is_native()) {
      __ call_VM_leaf(ZBarrierSetRuntime::store_barrier_on_native_oop_field_without_healing_addr(), R3_ARG1);
    } else if (stub->is_atomic()) {
      __ call_VM_leaf(ZBarrierSetRuntime::store_barrier_on_oop_field_with_healing_addr(), R3_ARG1);
    } else {
      __ call_VM_leaf(ZBarrierSetRuntime::store_barrier_on_oop_field_without_healing_addr(), R3_ARG1);
    }
  }

  // Stub exit
  __ b(*stub->continuation());
}

#undef __
#endif // COMPILER2

static uint16_t patch_barrier_relocation_value(int format) {
  switch (format) {
  case ZBarrierRelocationFormatLoadBadMask:
    return (uint16_t)ZPointerLoadBadMask;
  case ZBarrierRelocationFormatMarkBadMask:
    return (uint16_t)ZPointerMarkBadMask;
  case ZBarrierRelocationFormatStoreGoodBits:
    return (uint16_t)ZPointerStoreGoodMask;
  case ZBarrierRelocationFormatStoreBadMask:
    return (uint16_t)ZPointerStoreBadMask;
  default:
    ShouldNotReachHere();
    return 0;
  }
}

void ZBarrierSetAssembler::patch_barrier_relocation(address addr, int format) {
#ifdef ASSERT
  int inst = *(int*)addr;
  if (format == ZBarrierRelocationFormatStoreGoodBits) {
    assert(Assembler::is_li(inst) || Assembler::is_ori(inst) || Assembler::is_cmpli(inst),
           "unexpected instruction 0x%04x", inst);
    // Note: li uses sign extend, but these bits will get cleared by rldimi.
  } else {
    assert(Assembler::is_andi(inst), "unexpected instruction 0x%04x", inst);
  }
#endif
  // Patch the signed/unsigned 16 bit immediate field of the instruction.
  *(uint16_t*)(addr BIG_ENDIAN_ONLY(+2)) = patch_barrier_relocation_value(format);
  ICache::ppc64_flush_icache_bytes(addr, BytesPerInstWord);
}
