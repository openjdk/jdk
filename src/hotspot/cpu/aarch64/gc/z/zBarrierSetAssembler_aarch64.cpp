/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "code/codeBlob.hpp"
#include "code/vmreg.inline.hpp"
#include "gc/z/zAddress.hpp"
#include "gc/z/zBarrier.inline.hpp"
#include "gc/z/zBarrierSet.hpp"
#include "gc/z/zBarrierSetAssembler.hpp"
#include "gc/z/zBarrierSetRuntime.hpp"
#include "gc/z/zThreadLocalData.hpp"
#include "memory/resourceArea.hpp"
#include "nativeInst_aarch64.hpp"
#include "runtime/icache.hpp"
#include "runtime/jniHandles.hpp"
#include "runtime/sharedRuntime.hpp"
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

#ifdef PRODUCT
#define BLOCK_COMMENT(str) /* nothing */
#else
#define BLOCK_COMMENT(str) __ block_comment(str)
#endif

#undef __
#define __ masm->

// Helper for saving and restoring registers across a runtime call that does
// not have any live vector registers.
class ZRuntimeCallSpill {
private:
  MacroAssembler* _masm;
  Register _result;

  void save() {
    MacroAssembler* masm = _masm;

    __ enter(true /* strip_ret_addr */);
    if (_result != noreg) {
      __ push_call_clobbered_registers_except(RegSet::of(_result));
    } else {
      __ push_call_clobbered_registers();
    }
  }

  void restore() {
    MacroAssembler* masm = _masm;

    if (_result != noreg) {
      // Make sure _result has the return value.
      if (_result != r0) {
        __ mov(_result, r0);
      }

      __ pop_call_clobbered_registers_except(RegSet::of(_result));
    } else {
      __ pop_call_clobbered_registers();
    }
    __ leave();
  }

public:
  ZRuntimeCallSpill(MacroAssembler* masm, Register result)
    : _masm(masm),
      _result(result) {
    save();
  }

  ~ZRuntimeCallSpill() {
    restore();
  }
};

void ZBarrierSetAssembler::load_at(MacroAssembler* masm,
                                   DecoratorSet decorators,
                                   BasicType type,
                                   Register dst,
                                   Address src,
                                   Register tmp1,
                                   Register tmp2) {
  if (!ZBarrierSet::barrier_needed(decorators, type)) {
    // Barrier not needed
    BarrierSetAssembler::load_at(masm, decorators, type, dst, src, tmp1, tmp2);
    return;
  }

  assert_different_registers(tmp1, tmp2, src.base(), noreg);
  assert_different_registers(tmp1, tmp2, src.index());
  assert_different_registers(tmp1, tmp2, dst, noreg);
  assert_different_registers(tmp2, rscratch1);

  Label done;
  Label uncolor;

  // Load bad mask into scratch register.
  const bool on_non_strong =
    (decorators & ON_WEAK_OOP_REF) != 0 ||
    (decorators & ON_PHANTOM_OOP_REF) != 0;

  if (on_non_strong) {
    __ ldr(tmp1, mark_bad_mask_from_thread(rthread));
  } else {
    __ ldr(tmp1, load_bad_mask_from_thread(rthread));
  }

  __ lea(tmp2, src);
  __ ldr(dst, tmp2);

  // Test reference against bad mask. If mask bad, then we need to fix it up.
  __ tst(dst, tmp1);
  __ br(Assembler::EQ, uncolor);

  {
    // Call VM
    ZRuntimeCallSpill rcs(masm, dst);

    if (c_rarg0 != dst) {
      __ mov(c_rarg0, dst);
    }
    __ mov(c_rarg1, tmp2);

    __ call_VM_leaf(ZBarrierSetRuntime::load_barrier_on_oop_field_preloaded_addr(decorators), 2);
  }

  // Slow-path has already uncolored
  __ b(done);

  __ bind(uncolor);

  // Remove the color bits
  __ lsr(dst, dst, ZPointerLoadShift);

  __ bind(done);
}

void ZBarrierSetAssembler::store_barrier_fast(MacroAssembler* masm,
                                              Address ref_addr,
                                              Register rnew_zaddress,
                                              Register rnew_zpointer,
                                              Register rtmp,
                                              bool in_nmethod,
                                              bool is_atomic,
                                              Label& medium_path,
                                              Label& medium_path_continuation) const {
  assert_different_registers(ref_addr.base(), rnew_zpointer, rtmp);
  assert_different_registers(ref_addr.index(), rnew_zpointer, rtmp);
  assert_different_registers(rnew_zaddress, rnew_zpointer, rtmp);

  if (in_nmethod) {
    if (is_atomic) {
      __ ldrh(rtmp, ref_addr);
      // Atomic operations must ensure that the contents of memory are store-good before
      // an atomic operation can execute.
      // A not relocatable object could have spurious raw null pointers in its fields after
      // getting promoted to the old generation.
      __ relocate(barrier_Relocation::spec(), ZBarrierRelocationFormatStoreGoodBeforeMov);
      __ movzw(rnew_zpointer, barrier_Relocation::unpatched);
      __ cmpw(rtmp, rnew_zpointer);
    } else {
      __ ldr(rtmp, ref_addr);
      // Stores on relocatable objects never need to deal with raw null pointers in fields.
      // Raw null pointers may only exist in the young generation, as they get pruned when
      // the object is relocated to old. And no pre-write barrier needs to perform any action
      // in the young generation.
      __ relocate(barrier_Relocation::spec(), ZBarrierRelocationFormatStoreBadBeforeMov);
      __ movzw(rnew_zpointer, barrier_Relocation::unpatched);
      __ tst(rtmp, rnew_zpointer);
    }
    __ br(Assembler::NE, medium_path);
    __ bind(medium_path_continuation);
    assert_different_registers(rnew_zaddress, rnew_zpointer);
    __ relocate(barrier_Relocation::spec(), ZBarrierRelocationFormatStoreGoodBeforeMov);
    __ movzw(rnew_zpointer, barrier_Relocation::unpatched);
    __ orr(rnew_zpointer, rnew_zpointer, rnew_zaddress, Assembler::LSL, ZPointerLoadShift);
  } else {
    assert(!is_atomic, "atomics outside of nmethods not supported");
    __ lea(rtmp, ref_addr);
    __ ldr(rtmp, rtmp);
    __ ldr(rnew_zpointer, Address(rthread, ZThreadLocalData::store_bad_mask_offset()));
    __ tst(rtmp, rnew_zpointer);
    __ br(Assembler::NE, medium_path);
    __ bind(medium_path_continuation);
    if (rnew_zaddress == noreg) {
      __ eor(rnew_zpointer, rnew_zpointer, rnew_zpointer);
    } else {
      __ mov(rnew_zpointer, rnew_zaddress);
    }

    // Load the current good shift, and add the color bits
    __ lsl(rnew_zpointer, rnew_zpointer, ZPointerLoadShift);
    __ ldr(rtmp, Address(rthread, ZThreadLocalData::store_good_mask_offset()));
    __ orr(rnew_zpointer, rnew_zpointer, rtmp);
  }
}

static void store_barrier_buffer_add(MacroAssembler* masm,
                                     Address ref_addr,
                                     Register tmp1,
                                     Register tmp2,
                                     Label& slow_path) {
  Address buffer(rthread, ZThreadLocalData::store_barrier_buffer_offset());
  assert_different_registers(ref_addr.base(), ref_addr.index(), tmp1, tmp2);

  __ ldr(tmp1, buffer);

  // Combined pointer bump and check if the buffer is disabled or full
  __ ldr(tmp2, Address(tmp1, ZStoreBarrierBuffer::current_offset()));
  __ cmp(tmp2, (uint8_t)0);
  __ br(Assembler::EQ, slow_path);

  // Bump the pointer
  __ sub(tmp2, tmp2, sizeof(ZStoreBarrierEntry));
  __ str(tmp2, Address(tmp1, ZStoreBarrierBuffer::current_offset()));

  // Compute the buffer entry address
  __ lea(tmp2, Address(tmp2, ZStoreBarrierBuffer::buffer_offset()));
  __ add(tmp2, tmp2, tmp1);

  // Compute and log the store address
  __ lea(tmp1, ref_addr);
  __ str(tmp1, Address(tmp2, in_bytes(ZStoreBarrierEntry::p_offset())));

  // Load and log the prev value
  __ ldr(tmp1, tmp1);
  __ str(tmp1, Address(tmp2, in_bytes(ZStoreBarrierEntry::prev_offset())));
}

void ZBarrierSetAssembler::store_barrier_medium(MacroAssembler* masm,
                                                Address ref_addr,
                                                Register rtmp1,
                                                Register rtmp2,
                                                Register rtmp3,
                                                bool is_native,
                                                bool is_atomic,
                                                Label& medium_path_continuation,
                                                Label& slow_path,
                                                Label& slow_path_continuation) const {
  assert_different_registers(ref_addr.base(), ref_addr.index(), rtmp1, rtmp2);

  // The reason to end up in the medium path is that the pre-value was not 'good'.

  if (is_native) {
    __ b(slow_path);
    __ bind(slow_path_continuation);
    __ b(medium_path_continuation);
  } else if (is_atomic) {
    // Atomic accesses can get to the medium fast path because the value was a
    // raw null value. If it was not null, then there is no doubt we need to take a slow path.
    __ lea(rtmp2, ref_addr);
    __ ldr(rtmp1, rtmp2);
    __ cbnz(rtmp1, slow_path);

    // If we get this far, we know there is a young raw null value in the field.
    __ relocate(barrier_Relocation::spec(), ZBarrierRelocationFormatStoreGoodBeforeMov);
    __ movzw(rtmp1, barrier_Relocation::unpatched);
    __ cmpxchg(rtmp2, zr, rtmp1,
               Assembler::xword,
               false /* acquire */, false /* release */, true /* weak */,
               rtmp3);
    __ br(Assembler::NE, slow_path);

    __ bind(slow_path_continuation);
    __ b(medium_path_continuation);
  } else {
    // A non-atomic relocatable object won't get to the medium fast path due to a
    // raw null in the young generation. We only get here because the field is bad.
    // In this path we don't need any self healing, so we can avoid a runtime call
    // most of the time by buffering the store barrier to be applied lazily.
    store_barrier_buffer_add(masm,
                             ref_addr,
                             rtmp1,
                             rtmp2,
                             slow_path);
    __ bind(slow_path_continuation);
    __ b(medium_path_continuation);
  }
}

void ZBarrierSetAssembler::store_at(MacroAssembler* masm,
                                    DecoratorSet decorators,
                                    BasicType type,
                                    Address dst,
                                    Register val,
                                    Register tmp1,
                                    Register tmp2,
                                    Register tmp3) {
  if (!ZBarrierSet::barrier_needed(decorators, type)) {
    BarrierSetAssembler::store_at(masm, decorators, type, dst, val, tmp1, tmp2, tmp3);
    return;
  }

  bool dest_uninitialized = (decorators & IS_DEST_UNINITIALIZED) != 0;

  assert_different_registers(val, tmp1, dst.base(), dst.index());

  if (dest_uninitialized) {
    if (val == noreg) {
      __ eor(tmp1, tmp1, tmp1);
    } else {
      __ mov(tmp1, val);
    }
    // Add the color bits
    __ lsl(tmp1, tmp1, ZPointerLoadShift);
    __ ldr(tmp2, Address(rthread, ZThreadLocalData::store_good_mask_offset()));
    __ orr(tmp1, tmp2, tmp1);
  } else {
    Label done;
    Label medium;
    Label medium_continuation;
    Label slow;
    Label slow_continuation;
    store_barrier_fast(masm, dst, val, tmp1, tmp2, false, false, medium, medium_continuation);
    __ b(done);
    __ bind(medium);
    store_barrier_medium(masm,
                         dst,
                         tmp1,
                         tmp2,
                         noreg /* tmp3 */,
                         false /* is_native */,
                         false /* is_atomic */,
                         medium_continuation,
                         slow,
                         slow_continuation);

    __ bind(slow);
    {
      // Call VM
      ZRuntimeCallSpill rcs(masm, noreg);
      __ lea(c_rarg0, dst);
      __ MacroAssembler::call_VM_leaf(ZBarrierSetRuntime::store_barrier_on_oop_field_without_healing_addr(), 1);
    }

    __ b(slow_continuation);
    __ bind(done);
  }

  // Store value
  BarrierSetAssembler::store_at(masm, decorators, type, dst, tmp1, tmp2, tmp3, noreg);
}

static FloatRegister z_copy_load_bad_vreg = v17;
static FloatRegister z_copy_store_good_vreg = v18;
static FloatRegister z_copy_store_bad_vreg = v19;

static void load_wide_arraycopy_masks(MacroAssembler* masm) {
  __ lea(rscratch1, ExternalAddress((address)&ZPointerVectorLoadBadMask));
  __ ldrq(z_copy_load_bad_vreg, Address(rscratch1, 0));
  __ lea(rscratch1, ExternalAddress((address)&ZPointerVectorStoreBadMask));
  __ ldrq(z_copy_store_bad_vreg, Address(rscratch1, 0));
  __ lea(rscratch1, ExternalAddress((address)&ZPointerVectorStoreGoodMask));
  __ ldrq(z_copy_store_good_vreg, Address(rscratch1, 0));
}

class ZCopyRuntimeCallSpill {
private:
  MacroAssembler* _masm;
  Register _result;

  void save() {
    MacroAssembler* masm = _masm;

    __ enter(true /* strip_ret_addr */);
    if (_result != noreg) {
      __ push(__ call_clobbered_gp_registers() - RegSet::of(_result), sp);
    } else {
      __ push(__ call_clobbered_gp_registers(), sp);
    }
    int neonSize = wordSize * 2;
    __ sub(sp, sp, 4 * neonSize);
    __ st1(v0, v1, v2, v3, Assembler::T16B, Address(sp, 0));
    __ sub(sp, sp, 4 * neonSize);
    __ st1(v4, v5, v6, v7, Assembler::T16B, Address(sp, 0));
    __ sub(sp, sp, 4 * neonSize);
    __ st1(v16, v17, v18, v19, Assembler::T16B, Address(sp, 0));
  }

  void restore() {
    MacroAssembler* masm = _masm;

    int neonSize = wordSize * 2;
    __ ld1(v16, v17, v18, v19, Assembler::T16B, Address(sp, 0));
    __ add(sp, sp, 4 * neonSize);
    __ ld1(v4, v5, v6, v7, Assembler::T16B, Address(sp, 0));
    __ add(sp, sp, 4 * neonSize);
    __ ld1(v0, v1, v2, v3, Assembler::T16B, Address(sp, 0));
    __ add(sp, sp, 4 * neonSize);
    if (_result != noreg) {
      if (_result != r0) {
        __ mov(_result, r0);
      }
      __ pop(__ call_clobbered_gp_registers() - RegSet::of(_result), sp);
    } else {
      __ pop(__ call_clobbered_gp_registers(), sp);
    }
    __ leave();
  }

public:
  ZCopyRuntimeCallSpill(MacroAssembler* masm, Register result)
    : _masm(masm),
      _result(result) {
    save();
  }

  ~ZCopyRuntimeCallSpill() {
    restore();
  }
};

void ZBarrierSetAssembler::arraycopy_prologue(MacroAssembler* masm,
                                              DecoratorSet decorators,
                                              bool is_oop,
                                              Register src,
                                              Register dst,
                                              Register count,
                                              RegSet saved_regs) {
  if (!is_oop) {
    // Barrier not needed
    return;
  }

  BLOCK_COMMENT("ZBarrierSetAssembler::arraycopy_prologue {");

  load_wide_arraycopy_masks(masm);

  BLOCK_COMMENT("} ZBarrierSetAssembler::arraycopy_prologue");
}

static void copy_load_barrier(MacroAssembler* masm,
                              Register ref,
                              Address src,
                              Register tmp) {
  Label done;

  __ ldr(tmp, Address(rthread, ZThreadLocalData::load_bad_mask_offset()));

  // Test reference against bad mask. If mask bad, then we need to fix it up.
  __ tst(ref, tmp);
  __ br(Assembler::EQ, done);

  {
    // Call VM
    ZCopyRuntimeCallSpill rcs(masm, ref);

    __ lea(c_rarg1, src);

    if (c_rarg0 != ref) {
      __ mov(c_rarg0, ref);
    }

    __ call_VM_leaf(ZBarrierSetRuntime::load_barrier_on_oop_field_preloaded_addr(IN_HEAP | ON_STRONG_OOP_REF), 2);
  }

  // Slow-path has uncolored; revert
  __ lsl(ref, ref, ZPointerLoadShift);

  __ bind(done);
}

static void copy_load_barrier(MacroAssembler* masm,
                              FloatRegister ref,
                              Address src,
                              Register tmp1,
                              Register tmp2,
                              FloatRegister vec_tmp) {
  Label done;

  // Test reference against bad mask. If mask bad, then we need to fix it up.
  __ andr(vec_tmp, Assembler::T16B, ref, z_copy_load_bad_vreg);
  __ umaxv(vec_tmp, Assembler::T16B, vec_tmp);
  __ fcmpd(vec_tmp, 0.0);
  __ br(Assembler::EQ, done);

  __ umov(tmp2, ref, Assembler::D, 0);
  copy_load_barrier(masm, tmp2, Address(src.base(), src.offset() + 0), tmp1);
  __ mov(ref, __ D, 0, tmp2);

  __ umov(tmp2, ref, Assembler::D, 1);
  copy_load_barrier(masm, tmp2, Address(src.base(), src.offset() + 8), tmp1);
  __ mov(ref, __ D, 1, tmp2);

  __ bind(done);
}

static void copy_store_barrier(MacroAssembler* masm,
                               Register pre_ref,
                               Register new_ref,
                               Address src,
                               Register tmp1,
                               Register tmp2) {
  Label done;
  Label slow;

  // Test reference against bad mask. If mask bad, then we need to fix it up.
  __ ldr(tmp1, Address(rthread, ZThreadLocalData::store_bad_mask_offset()));
  __ tst(pre_ref, tmp1);
  __ br(Assembler::EQ, done);

  store_barrier_buffer_add(masm, src, tmp1, tmp2, slow);
  __ b(done);

  __ bind(slow);
  {
    // Call VM
    ZCopyRuntimeCallSpill rcs(masm, noreg);

    __ lea(c_rarg0, src);

    __ call_VM_leaf(ZBarrierSetRuntime::store_barrier_on_oop_field_without_healing_addr(), 1);
  }

  __ bind(done);

  if (new_ref != noreg) {
    // Set store-good color, replacing whatever color was there before
    __ ldr(tmp1, Address(rthread, ZThreadLocalData::store_good_mask_offset()));
    __ bfi(new_ref, tmp1, 0, 16);
  }
}

static void copy_store_barrier(MacroAssembler* masm,
                               FloatRegister pre_ref,
                               FloatRegister new_ref,
                               Address src,
                               Register tmp1,
                               Register tmp2,
                               Register tmp3,
                               FloatRegister vec_tmp) {
  Label done;

  // Test reference against bad mask. If mask bad, then we need to fix it up.
  __ andr(vec_tmp, Assembler::T16B, pre_ref, z_copy_store_bad_vreg);
  __ umaxv(vec_tmp, Assembler::T16B, vec_tmp);
  __ fcmpd(vec_tmp, 0.0);
  __ br(Assembler::EQ, done);

  // Extract the 2 oops from the pre_ref vector register
  __ umov(tmp2, pre_ref, Assembler::D, 0);
  copy_store_barrier(masm, tmp2, noreg, Address(src.base(), src.offset() + 0), tmp1, tmp3);

  __ umov(tmp2, pre_ref, Assembler::D, 1);
  copy_store_barrier(masm, tmp2, noreg, Address(src.base(), src.offset() + 8), tmp1, tmp3);

  __ bind(done);

  // Remove any bad colors
  __ bic(new_ref, Assembler::T16B, new_ref, z_copy_store_bad_vreg);
  // Add good colors
  __ orr(new_ref, Assembler::T16B, new_ref, z_copy_store_good_vreg);
}

class ZAdjustAddress {
private:
  MacroAssembler* _masm;
  Address _addr;
  int _pre_adjustment;
  int _post_adjustment;

  void pre() {
    if (_pre_adjustment != 0) {
      _masm->add(_addr.base(), _addr.base(), _addr.offset());
    }
  }

  void post() {
    if (_post_adjustment != 0) {
      _masm->add(_addr.base(), _addr.base(), _addr.offset());
    }
  }

public:
  ZAdjustAddress(MacroAssembler* masm, Address addr)
    : _masm(masm),
      _addr(addr),
      _pre_adjustment(addr.getMode() == Address::pre ? addr.offset() : 0),
      _post_adjustment(addr.getMode() == Address::post ? addr.offset() : 0) {
    pre();
  }

  ~ZAdjustAddress() {
    post();
  }

  Address address() {
    if (_pre_adjustment != 0 || _post_adjustment != 0) {
      return Address(_addr.base(), 0);
    } else {
      return Address(_addr.base(), _addr.offset());
    }
  }
};

void ZBarrierSetAssembler::copy_load_at(MacroAssembler* masm,
                                        DecoratorSet decorators,
                                        BasicType type,
                                        size_t bytes,
                                        Register dst1,
                                        Register dst2,
                                        Address src,
                                        Register tmp) {
  if (!is_reference_type(type)) {
    BarrierSetAssembler::copy_load_at(masm, decorators, type, bytes, dst1, dst2, src, noreg);
    return;
  }

  ZAdjustAddress adjust(masm, src);
  src = adjust.address();

  BarrierSetAssembler::copy_load_at(masm, decorators, type, bytes, dst1, dst2, src, noreg);

  if (bytes == 8) {
    copy_load_barrier(masm, dst1, src, tmp);
  } else if (bytes == 16) {
    copy_load_barrier(masm, dst1, Address(src.base(), src.offset() + 0), tmp);
    copy_load_barrier(masm, dst2, Address(src.base(), src.offset() + 8), tmp);
  } else {
    ShouldNotReachHere();
  }
  if ((decorators & ARRAYCOPY_CHECKCAST) != 0) {
    __ lsr(dst1, dst1, ZPointerLoadShift);
  }
}

void ZBarrierSetAssembler::copy_store_at(MacroAssembler* masm,
                                         DecoratorSet decorators,
                                         BasicType type,
                                         size_t bytes,
                                         Address dst,
                                         Register src1,
                                         Register src2,
                                         Register tmp1,
                                         Register tmp2,
                                         Register tmp3) {
  if (!is_reference_type(type)) {
    BarrierSetAssembler::copy_store_at(masm, decorators, type, bytes, dst, src1, src2, noreg, noreg, noreg);
    return;
  }

  ZAdjustAddress adjust(masm, dst);
  dst = adjust.address();

  if ((decorators & ARRAYCOPY_CHECKCAST) != 0) {
    __ lsl(src1, src1, ZPointerLoadShift);
  }

  bool is_dest_uninitialized = (decorators & IS_DEST_UNINITIALIZED) != 0;

  if (is_dest_uninitialized) {
    __ ldr(tmp1, Address(rthread, ZThreadLocalData::store_good_mask_offset()));
    if (bytes == 8) {
      __ bfi(src1, tmp1, 0, 16);
    } else if (bytes == 16) {
      __ bfi(src1, tmp1, 0, 16);
      __ bfi(src2, tmp1, 0, 16);
    } else {
      ShouldNotReachHere();
    }
  } else {
    // Store barrier pre values and color new values
    if (bytes == 8) {
      __ ldr(tmp1, dst);
      copy_store_barrier(masm, tmp1, src1, dst, tmp2, tmp3);
    } else if (bytes == 16) {
      Address dst1(dst.base(), dst.offset() + 0);
      Address dst2(dst.base(), dst.offset() + 8);

      __ ldr(tmp1, dst1);
      copy_store_barrier(masm, tmp1, src1, dst1, tmp2, tmp3);

      __ ldr(tmp1, dst2);
      copy_store_barrier(masm, tmp1, src2, dst2, tmp2, tmp3);
    } else {
      ShouldNotReachHere();
    }
  }

  // Store new values
  BarrierSetAssembler::copy_store_at(masm, decorators, type, bytes, dst, src1, src2, noreg, noreg, noreg);
}

void ZBarrierSetAssembler::copy_load_at(MacroAssembler* masm,
                                        DecoratorSet decorators,
                                        BasicType type,
                                        size_t bytes,
                                        FloatRegister dst1,
                                        FloatRegister dst2,
                                        Address src,
                                        Register tmp1,
                                        Register tmp2,
                                        FloatRegister vec_tmp) {
  if (!is_reference_type(type)) {
    BarrierSetAssembler::copy_load_at(masm, decorators, type, bytes, dst1, dst2, src, noreg, noreg, fnoreg);
    return;
  }

  ZAdjustAddress adjust(masm, src);
  src = adjust.address();

  BarrierSetAssembler::copy_load_at(masm, decorators, type, bytes, dst1, dst2, src, noreg, noreg, fnoreg);

  if (bytes == 32) {
    copy_load_barrier(masm, dst1, Address(src.base(), src.offset() + 0), tmp1, tmp2, vec_tmp);
    copy_load_barrier(masm, dst2, Address(src.base(), src.offset() + 16), tmp1, tmp2, vec_tmp);
  } else {
    ShouldNotReachHere();
  }
}

void ZBarrierSetAssembler::copy_store_at(MacroAssembler* masm,
                                         DecoratorSet decorators,
                                         BasicType type,
                                         size_t bytes,
                                         Address dst,
                                         FloatRegister src1,
                                         FloatRegister src2,
                                         Register tmp1,
                                         Register tmp2,
                                         Register tmp3,
                                         FloatRegister vec_tmp1,
                                         FloatRegister vec_tmp2,
                                         FloatRegister vec_tmp3) {
  if (!is_reference_type(type)) {
    BarrierSetAssembler::copy_store_at(masm, decorators, type, bytes, dst, src1, src2, noreg, noreg, noreg, fnoreg, fnoreg, fnoreg);
    return;
  }

  bool is_dest_uninitialized = (decorators & IS_DEST_UNINITIALIZED) != 0;

  ZAdjustAddress adjust(masm, dst);
  dst = adjust.address();

  if (is_dest_uninitialized) {
    if (bytes == 32) {
      __ bic(src1, Assembler::T16B, src1, z_copy_store_bad_vreg);
      __ orr(src1, Assembler::T16B, src1, z_copy_store_good_vreg);
      __ bic(src2, Assembler::T16B, src2, z_copy_store_bad_vreg);
      __ orr(src2, Assembler::T16B, src2, z_copy_store_good_vreg);
    } else {
      ShouldNotReachHere();
    }
  } else {
    // Load pre values
    BarrierSetAssembler::copy_load_at(masm, decorators, type, bytes, vec_tmp1, vec_tmp2, dst, noreg, noreg, fnoreg);

    // Store barrier pre values and color new values
    if (bytes == 32) {
      copy_store_barrier(masm, vec_tmp1, src1, Address(dst.base(), dst.offset() + 0), tmp1, tmp2, tmp3, vec_tmp3);
      copy_store_barrier(masm, vec_tmp2, src2, Address(dst.base(), dst.offset() + 16), tmp1, tmp2, tmp3, vec_tmp3);
    } else {
      ShouldNotReachHere();
    }
  }

  // Store new values
  BarrierSetAssembler::copy_store_at(masm, decorators, type, bytes, dst, src1, src2, noreg, noreg, noreg, fnoreg, fnoreg, fnoreg);
}

void ZBarrierSetAssembler::try_resolve_jobject_in_native(MacroAssembler* masm,
                                                         Register jni_env,
                                                         Register robj,
                                                         Register tmp,
                                                         Label& slowpath) {
  BLOCK_COMMENT("ZBarrierSetAssembler::try_resolve_jobject_in_native {");

  Label done, tagged, weak_tagged, uncolor;

  // Test for tag
  __ tst(robj, JNIHandles::tag_mask);
  __ br(Assembler::NE, tagged);

  // Resolve local handle
  __ ldr(robj, robj);
  __ b(done);

  __ bind(tagged);

  // Test for weak tag
  __ tst(robj, JNIHandles::TypeTag::weak_global);
  __ br(Assembler::NE, weak_tagged);

  // Resolve global handle
  __ ldr(robj, Address(robj, -JNIHandles::TypeTag::global));
  __ lea(tmp, load_bad_mask_from_jni_env(jni_env));
  __ ldr(tmp, tmp);
  __ tst(robj, tmp);
  __ br(Assembler::NE, slowpath);
  __ b(uncolor);

  __ bind(weak_tagged);

  // Resolve weak handle
  __ ldr(robj, Address(robj, -JNIHandles::TypeTag::weak_global));
  __ lea(tmp, mark_bad_mask_from_jni_env(jni_env));
  __ ldr(tmp, tmp);
  __ tst(robj, tmp);
  __ br(Assembler::NE, slowpath);

  __ bind(uncolor);

  // Uncolor
  __ lsr(robj, robj, ZPointerLoadShift);

  __ bind(done);

  BLOCK_COMMENT("} ZBarrierSetAssembler::try_resolve_jobject_in_native");
}

static uint16_t patch_barrier_relocation_value(int format) {
  switch (format) {
  case ZBarrierRelocationFormatLoadGoodBeforeTbX:
    return (uint16_t)exact_log2(ZPointerRemapped);

  case ZBarrierRelocationFormatMarkBadBeforeMov:
    return (uint16_t)ZPointerMarkBadMask;

  case ZBarrierRelocationFormatStoreGoodBeforeMov:
    return (uint16_t)ZPointerStoreGoodMask;

  case ZBarrierRelocationFormatStoreBadBeforeMov:
    return (uint16_t)ZPointerStoreBadMask;

  default:
    ShouldNotReachHere();
    return 0;
  }
}

static void change_immediate(uint32_t& instr, uint32_t imm, uint32_t start, uint32_t end) {
  uint32_t imm_mask = ((1u << start) - 1u) ^ ((1u << (end + 1)) - 1u);
  instr &= ~imm_mask;
  instr |= imm << start;
}

void ZBarrierSetAssembler::patch_barrier_relocation(address addr, int format) {
  const uint16_t value = patch_barrier_relocation_value(format);
  uint32_t* const patch_addr = (uint32_t*)addr;

  switch (format) {
  case ZBarrierRelocationFormatLoadGoodBeforeTbX:
    change_immediate(*patch_addr, value, 19, 23);
    break;
  case ZBarrierRelocationFormatStoreGoodBeforeMov:
  case ZBarrierRelocationFormatMarkBadBeforeMov:
  case ZBarrierRelocationFormatStoreBadBeforeMov:
    change_immediate(*patch_addr, value, 5, 20);
    break;
  default:
    ShouldNotReachHere();
  }

  OrderAccess::fence();
  ICache::invalidate_word((address)patch_addr);
}

#ifdef COMPILER1

#undef __
#define __ ce->masm()->

static void z_uncolor(LIR_Assembler* ce, LIR_Opr ref) {
  __ lsr(ref->as_register(), ref->as_register(), ZPointerLoadShift);
}

static void z_color(LIR_Assembler* ce, LIR_Opr ref) {
  __ relocate(barrier_Relocation::spec(), ZBarrierRelocationFormatStoreGoodBeforeMov);
  __ movzw(rscratch2, barrier_Relocation::unpatched);
  __ orr(ref->as_register(), rscratch2, ref->as_register(), Assembler::LSL, ZPointerLoadShift);
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

  if (on_non_strong) {
    // Test against MarkBad mask
    assert_different_registers(rscratch1, rthread, ref->as_register());
    __ relocate(barrier_Relocation::spec(), ZBarrierRelocationFormatMarkBadBeforeMov);
    __ movzw(rscratch1, barrier_Relocation::unpatched);
    __ tst(ref->as_register(), rscratch1);
    __ br(Assembler::NE, *stub->entry());
    z_uncolor(ce, ref);
  } else {
    Label good;
    __ relocate(barrier_Relocation::spec(), ZBarrierRelocationFormatLoadGoodBeforeTbX);
    __ tbz(ref->as_register(), barrier_Relocation::unpatched, good);
    __ b(*stub->entry());
    __ bind(good);
    z_uncolor(ce, ref);
  }
  __ bind(*stub->continuation());
}

void ZBarrierSetAssembler::generate_c1_load_barrier_stub(LIR_Assembler* ce,
                                                         ZLoadBarrierStubC1* stub) const {
  // Stub entry
  __ bind(*stub->entry());

  Register ref = stub->ref()->as_register();
  Register ref_addr = noreg;
  Register tmp = noreg;

  if (stub->tmp()->is_valid()) {
    // Load address into tmp register
    ce->leal(stub->ref_addr(), stub->tmp());
    ref_addr = tmp = stub->tmp()->as_pointer_register();
  } else {
    // Address already in register
    ref_addr = stub->ref_addr()->as_address_ptr()->base()->as_pointer_register();
  }

  assert_different_registers(ref, ref_addr, noreg);

  // Save r0 unless it is the result or tmp register
  // Set up SP to accommodate parameters and maybe r0..
  if (ref != r0 && tmp != r0) {
    __ sub(sp, sp, 32);
    __ str(r0, Address(sp, 16));
  } else {
    __ sub(sp, sp, 16);
  }

  // Setup arguments and call runtime stub
  ce->store_parameter(ref_addr, 1);
  ce->store_parameter(ref, 0);

  __ far_call(stub->runtime_stub());

  // Verify result
  __ verify_oop(r0);

  // Move result into place
  if (ref != r0) {
    __ mov(ref, r0);
  }

  // Restore r0 unless it is the result or tmp register
  if (ref != r0 && tmp != r0) {
    __ ldr(r0, Address(sp, 16));
    __ add(sp, sp, 32);
  } else {
    __ add(sp, sp, 16);
  }

  // Stub exit
  __ b(*stub->continuation());
}

void ZBarrierSetAssembler::generate_c1_store_barrier(LIR_Assembler* ce,
                                                     LIR_Address* addr,
                                                     LIR_Opr new_zaddress,
                                                     LIR_Opr new_zpointer,
                                                     ZStoreBarrierStubC1* stub) const {
  Register rnew_zaddress = new_zaddress->as_register();
  Register rnew_zpointer = new_zpointer->as_register();

  store_barrier_fast(ce->masm(),
                     ce->as_Address(addr),
                     rnew_zaddress,
                     rnew_zpointer,
                     rscratch2,
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
  Label slow_continuation;
  store_barrier_medium(ce->masm(),
                       ce->as_Address(stub->ref_addr()->as_address_ptr()),
                       rscratch2,
                       stub->new_zpointer()->as_register(),
                       rscratch1,
                       false /* is_native */,
                       stub->is_atomic(),
                       *stub->continuation(),
                       slow,
                       slow_continuation);

  __ bind(slow);

  __ lea(stub->new_zpointer()->as_register(), ce->as_Address(stub->ref_addr()->as_address_ptr()));

  __ sub(sp, sp, 16);
  // Setup arguments and call runtime stub
  assert(stub->new_zpointer()->is_valid(), "invariant");
  ce->store_parameter(stub->new_zpointer()->as_register(), 0);
  __ far_call(stub->runtime_stub());
  __ add(sp, sp, 16);

  // Stub exit
  __ b(slow_continuation);
}

#undef __
#define __ sasm->

void ZBarrierSetAssembler::generate_c1_load_barrier_runtime_stub(StubAssembler* sasm,
                                                                 DecoratorSet decorators) const {
  __ prologue("zgc_load_barrier stub", false);

  __ push_call_clobbered_registers_except(RegSet::of(r0));

  // Setup arguments
  __ load_parameter(0, c_rarg0);
  __ load_parameter(1, c_rarg1);

  __ call_VM_leaf(ZBarrierSetRuntime::load_barrier_on_oop_field_preloaded_addr(decorators), 2);

  __ pop_call_clobbered_registers_except(RegSet::of(r0));

  __ epilogue();
}

void ZBarrierSetAssembler::generate_c1_store_barrier_runtime_stub(StubAssembler* sasm,
                                                                  bool self_healing) const {
  __ prologue("zgc_store_barrier stub", false);

  __ push_call_clobbered_registers();

  // Setup arguments
  __ load_parameter(0, c_rarg0);

  if (self_healing) {
    __ call_VM_leaf(ZBarrierSetRuntime::store_barrier_on_oop_field_with_healing_addr(), 1);
  } else {
    __ call_VM_leaf(ZBarrierSetRuntime::store_barrier_on_oop_field_without_healing_addr(), 1);
  }

  __ pop_call_clobbered_registers();

  __ epilogue();
}

#endif // COMPILER1

#ifdef COMPILER2

OptoReg::Name ZBarrierSetAssembler::refine_register(const Node* node, OptoReg::Name opto_reg) {
  if (!OptoReg::is_reg(opto_reg)) {
    return OptoReg::Bad;
  }

  const VMReg vm_reg = OptoReg::as_VMReg(opto_reg);
  if (vm_reg->is_FloatRegister()) {
    return opto_reg & ~1;
  }

  return opto_reg;
}

#undef __
#define __ _masm->

class ZSaveLiveRegisters {
private:
  MacroAssembler* const _masm;
  RegSet                _gp_regs;
  FloatRegSet           _fp_regs;
  PRegSet               _p_regs;

public:
  void initialize(ZBarrierStubC2* stub) {
    // Record registers that needs to be saved/restored
    RegMaskIterator rmi(stub->live());
    while (rmi.has_next()) {
      const OptoReg::Name opto_reg = rmi.next();
      if (OptoReg::is_reg(opto_reg)) {
        const VMReg vm_reg = OptoReg::as_VMReg(opto_reg);
        if (vm_reg->is_Register()) {
          _gp_regs += RegSet::of(vm_reg->as_Register());
        } else if (vm_reg->is_FloatRegister()) {
          _fp_regs += FloatRegSet::of(vm_reg->as_FloatRegister());
        } else if (vm_reg->is_PRegister()) {
          _p_regs += PRegSet::of(vm_reg->as_PRegister());
        } else {
          fatal("Unknown register type");
        }
      }
    }

    // Remove C-ABI SOE registers, scratch regs and _ref register that will be updated
    if (stub->result() != noreg) {
      _gp_regs -= RegSet::range(r19, r30) + RegSet::of(r8, r9, stub->result());
    } else {
      _gp_regs -= RegSet::range(r19, r30) + RegSet::of(r8, r9);
    }
  }

  ZSaveLiveRegisters(MacroAssembler* masm, ZBarrierStubC2* stub)
    : _masm(masm),
      _gp_regs(),
      _fp_regs(),
      _p_regs() {

    // Figure out what registers to save/restore
    initialize(stub);

    // Save registers
    __ push(_gp_regs, sp);
    __ push_fp(_fp_regs, sp);
    __ push_p(_p_regs, sp);
  }

  ~ZSaveLiveRegisters() {
    // Restore registers
    __ pop_p(_p_regs, sp);
    __ pop_fp(_fp_regs, sp);

    // External runtime call may clobber ptrue reg
    __ reinitialize_ptrue();

    __ pop(_gp_regs, sp);
  }
};

#undef __
#define __ _masm->

class ZSetupArguments {
private:
  MacroAssembler* const _masm;
  const Register        _ref;
  const Address         _ref_addr;

public:
  ZSetupArguments(MacroAssembler* masm, ZLoadBarrierStubC2* stub)
    : _masm(masm),
      _ref(stub->ref()),
      _ref_addr(stub->ref_addr()) {

    // Setup arguments
    if (_ref_addr.base() == noreg) {
      // No self healing
      if (_ref != c_rarg0) {
        __ mov(c_rarg0, _ref);
      }
      __ mov(c_rarg1, 0);
    } else {
      // Self healing
      if (_ref == c_rarg0) {
        // _ref is already at correct place
        __ lea(c_rarg1, _ref_addr);
      } else if (_ref != c_rarg1) {
        // _ref is in wrong place, but not in c_rarg1, so fix it first
        __ lea(c_rarg1, _ref_addr);
        __ mov(c_rarg0, _ref);
      } else if (_ref_addr.base() != c_rarg0 && _ref_addr.index() != c_rarg0) {
        assert(_ref == c_rarg1, "Mov ref first, vacating c_rarg0");
        __ mov(c_rarg0, _ref);
        __ lea(c_rarg1, _ref_addr);
      } else {
        assert(_ref == c_rarg1, "Need to vacate c_rarg1 and _ref_addr is using c_rarg0");
        if (_ref_addr.base() == c_rarg0 || _ref_addr.index() == c_rarg0) {
          __ mov(rscratch2, c_rarg1);
          __ lea(c_rarg1, _ref_addr);
          __ mov(c_rarg0, rscratch2);
        } else {
          ShouldNotReachHere();
        }
      }
    }
  }

  ~ZSetupArguments() {
    // Transfer result
    if (_ref != r0) {
      __ mov(_ref, r0);
    }
  }
};

#undef __
#define __ masm->

void ZBarrierSetAssembler::generate_c2_load_barrier_stub(MacroAssembler* masm, ZLoadBarrierStubC2* stub) const {
  Assembler::InlineSkippedInstructionsCounter skipped_counter(masm);
  BLOCK_COMMENT("ZLoadBarrierStubC2");

  // Stub entry
  if (!Compile::current()->output()->in_scratch_emit_size()) {
    __ bind(*stub->entry());
  }

  {
    ZSaveLiveRegisters save_live_registers(masm, stub);
    ZSetupArguments setup_arguments(masm, stub);
    __ mov(rscratch1, stub->slow_path());
    __ blr(rscratch1);
  }
  // Stub exit
  __ b(*stub->continuation());
}

void ZBarrierSetAssembler::generate_c2_store_barrier_stub(MacroAssembler* masm, ZStoreBarrierStubC2* stub) const {
  Assembler::InlineSkippedInstructionsCounter skipped_counter(masm);
  BLOCK_COMMENT("ZStoreBarrierStubC2");

  // Stub entry
  __ bind(*stub->entry());

  Label slow;
  Label slow_continuation;
  store_barrier_medium(masm,
                       stub->ref_addr(),
                       stub->new_zpointer(),
                       rscratch1,
                       rscratch2,
                       stub->is_native(),
                       stub->is_atomic(),
                       *stub->continuation(),
                       slow,
                       slow_continuation);

  __ bind(slow);

  {
    ZSaveLiveRegisters save_live_registers(masm, stub);
    __ lea(c_rarg0, stub->ref_addr());

    if (stub->is_native()) {
      __ lea(rscratch1, RuntimeAddress(ZBarrierSetRuntime::store_barrier_on_native_oop_field_without_healing_addr()));
    } else if (stub->is_atomic()) {
      __ lea(rscratch1, RuntimeAddress(ZBarrierSetRuntime::store_barrier_on_oop_field_with_healing_addr()));
    } else {
      __ lea(rscratch1, RuntimeAddress(ZBarrierSetRuntime::store_barrier_on_oop_field_without_healing_addr()));
    }
    __ blr(rscratch1);
  }

  // Stub exit
  __ b(slow_continuation);
}

// Only handles forward branch jumps, target_offset >= branch_offset
static bool aarch64_test_and_branch_reachable(int branch_offset, int target_offset) {
  assert(branch_offset >= 0, "branch to stub offsets must be positive");
  assert(target_offset >= 0, "offset in stubs section must be positive");
  assert(target_offset >= branch_offset, "forward branches only, branch_offset -> target_offset");

  const int test_and_branch_delta_limit = 32 * K;

  const int test_and_branch_to_trampoline_delta = target_offset - branch_offset;

  return test_and_branch_to_trampoline_delta < test_and_branch_delta_limit;
}

ZLoadBarrierStubC2Aarch64::ZLoadBarrierStubC2Aarch64(const MachNode* node, Address ref_addr, Register ref)
  : ZLoadBarrierStubC2(node, ref_addr, ref), _test_and_branch_reachable_entry(), _offset(), _deferred_emit(false), _test_and_branch_reachable(false) {}

ZLoadBarrierStubC2Aarch64::ZLoadBarrierStubC2Aarch64(const MachNode* node, Address ref_addr, Register ref, int offset)
  : ZLoadBarrierStubC2(node, ref_addr, ref), _test_and_branch_reachable_entry(), _offset(offset), _deferred_emit(false), _test_and_branch_reachable(false) {
  PhaseOutput* const output = Compile::current()->output();
  if (output->in_scratch_emit_size()) {
    return;
  }
  const int code_size = output->buffer_sizing_data()->_code;
  const int offset_code = _offset;
  // Assumption that the stub can always be reached from a branch immediate. (128 M Product, 2 M Debug)
  // Same assumption is made in z_aarch64.ad
  const int trampoline_offset = trampoline_stubs_count() * NativeInstruction::instruction_size;
  _test_and_branch_reachable = aarch64_test_and_branch_reachable(offset_code, code_size + trampoline_offset);
  if (_test_and_branch_reachable) {
    inc_trampoline_stubs_count();
  }
}

int ZLoadBarrierStubC2Aarch64::get_stub_size() {
  PhaseOutput* const output = Compile::current()->output();
  assert(!output->in_scratch_emit_size(), "only used when emitting stubs");
  BufferBlob* const blob = output->scratch_buffer_blob();
  CodeBuffer cb(blob->content_begin(), (address)output->scratch_locs_memory() - blob->content_begin());
  MacroAssembler masm(&cb);
  output->set_in_scratch_emit_size(true);
  ZLoadBarrierStubC2::emit_code(masm);
  output->set_in_scratch_emit_size(false);
  return cb.insts_size();
}

ZLoadBarrierStubC2Aarch64* ZLoadBarrierStubC2Aarch64::create(const MachNode* node, Address ref_addr, Register ref) {
  ZLoadBarrierStubC2Aarch64* const stub = new (Compile::current()->comp_arena()) ZLoadBarrierStubC2Aarch64(node, ref_addr, ref);
  register_stub(stub);
  return stub;
}

ZLoadBarrierStubC2Aarch64* ZLoadBarrierStubC2Aarch64::create(const MachNode* node, Address ref_addr, Register ref, int offset) {
  ZLoadBarrierStubC2Aarch64* const stub = new (Compile::current()->comp_arena()) ZLoadBarrierStubC2Aarch64(node, ref_addr, ref, offset);
  register_stub(stub);
  return stub;
}

#undef __
#define __ masm.

void ZLoadBarrierStubC2Aarch64::emit_code(MacroAssembler& masm) {
  PhaseOutput* const output = Compile::current()->output();
  const int branch_offset = _offset;
  const int target_offset = __ offset();

  // Deferred emission, emit actual stub
  if (_deferred_emit) {
    ZLoadBarrierStubC2::emit_code(masm);
    return;
  }
  _deferred_emit = true;

  // No trampoline used, defer emission to after trampolines
  if (!_test_and_branch_reachable) {
    register_stub(this);
    return;
  }

  // Current assumption is that the barrier stubs are the first stubs emitted after the actual code
  assert(stubs_start_offset() <= output->buffer_sizing_data()->_code, "stubs are assumed to be emitted directly after code and code_size is a hard limit on where it can start");

  __ bind(_test_and_branch_reachable_entry);

  // Next branch's offset is unknown, but is > branch_offset
  const int next_branch_offset = branch_offset + NativeInstruction::instruction_size;
  // If emitting the stub directly does not interfere with emission of the next trampoline then do it to avoid a double jump.
  if (aarch64_test_and_branch_reachable(next_branch_offset, target_offset + get_stub_size())) {
    // The next potential trampoline will still be reachable even if we emit the whole stub
    ZLoadBarrierStubC2::emit_code(masm);
  } else {
    // Emit trampoline and defer actual stub to the end
    assert(aarch64_test_and_branch_reachable(branch_offset, target_offset), "trampoline should be reachable");
    __ b(*ZLoadBarrierStubC2::entry());
    register_stub(this);
  }
}

bool ZLoadBarrierStubC2Aarch64::is_test_and_branch_reachable() {
  return _test_and_branch_reachable;
}

Label* ZLoadBarrierStubC2Aarch64::entry() {
  if (_test_and_branch_reachable) {
    return &_test_and_branch_reachable_entry;
  }
  return ZBarrierStubC2::entry();
}

ZStoreBarrierStubC2Aarch64::ZStoreBarrierStubC2Aarch64(const MachNode* node, Address ref_addr, Register new_zaddress, Register new_zpointer, bool is_native, bool is_atomic)
  : ZStoreBarrierStubC2(node, ref_addr, new_zaddress, new_zpointer, is_native, is_atomic), _deferred_emit(false) {}

ZStoreBarrierStubC2Aarch64* ZStoreBarrierStubC2Aarch64::create(const MachNode* node, Address ref_addr, Register new_zaddress, Register new_zpointer, bool is_native, bool is_atomic) {
  ZStoreBarrierStubC2Aarch64* const stub = new (Compile::current()->comp_arena()) ZStoreBarrierStubC2Aarch64(node, ref_addr, new_zaddress, new_zpointer, is_native, is_atomic);
  register_stub(stub);
  return stub;
}

void ZStoreBarrierStubC2Aarch64::emit_code(MacroAssembler& masm) {
  if (_deferred_emit) {
    ZStoreBarrierStubC2::emit_code(masm);
    return;
  }
  // Defer emission of store barriers so that trampolines are emitted first
  _deferred_emit = true;
  register_stub(this);
}

#undef __

#endif // COMPILER2

#undef __
#define __ masm->

void ZBarrierSetAssembler::check_oop(MacroAssembler* masm, Register obj, Register tmp1, Register tmp2, Label& error) {
  // C1 calls verfy_oop in the middle of barriers, before they have been uncolored
  // and after being colored. Therefore, we must deal with colored oops as well.
  Label done;
  Label check_oop;
  Label check_zaddress;
  int color_bits = ZPointerRemappedShift + ZPointerRemappedBits;

  uintptr_t shifted_base_start_mask = (UCONST64(1) << (ZAddressHeapBaseShift + color_bits + 1)) - 1;
  uintptr_t shifted_base_end_mask = (UCONST64(1) << (ZAddressHeapBaseShift + 1)) - 1;
  uintptr_t shifted_base_mask = shifted_base_start_mask ^ shifted_base_end_mask;

  uintptr_t shifted_address_end_mask = (UCONST64(1) << (color_bits + 1)) - 1;
  uintptr_t shifted_address_mask = shifted_address_end_mask ^ (uintptr_t)CONST64(-1);

  __ get_nzcv(tmp2);

  // Check colored null
  __ mov(tmp1, shifted_address_mask);
  __ tst(tmp1, obj);
  __ br(Assembler::EQ, done);

  // Check for zpointer
  __ mov(tmp1, shifted_base_mask);
  __ tst(tmp1, obj);
  __ br(Assembler::EQ, check_oop);

  // Uncolor presumed zpointer
  __ lsr(obj, obj, ZPointerLoadShift);

  __ b(check_zaddress);

  __ bind(check_oop);

  // make sure klass is 'reasonable', which is not zero.
  __ load_klass(tmp1, obj);  // get klass
  __ tst(tmp1, tmp1);
  __ br(Assembler::EQ, error); // if klass is null it is broken

  __ bind(check_zaddress);
  // Check if the oop is in the right area of memory
  __ mov(tmp1, (intptr_t) Universe::verify_oop_mask());
  __ andr(tmp1, tmp1, obj);
  __ mov(obj, (intptr_t) Universe::verify_oop_bits());
  __ cmp(tmp1, obj);
  __ br(Assembler::NE, error);

  __ bind(done);

  __ set_nzcv(tmp2);
}

#undef __
