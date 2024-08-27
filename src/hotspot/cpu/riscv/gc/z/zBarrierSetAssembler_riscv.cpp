/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2023, Huawei Technologies Co., Ltd. All rights reserved.
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
#include "code/codeBlob.hpp"
#include "code/vmreg.inline.hpp"
#include "gc/z/zAddress.hpp"
#include "gc/z/zBarrier.inline.hpp"
#include "gc/z/zBarrierSet.hpp"
#include "gc/z/zBarrierSetAssembler.hpp"
#include "gc/z/zBarrierSetRuntime.hpp"
#include "gc/z/zThreadLocalData.hpp"
#include "memory/resourceArea.hpp"
#include "runtime/jniHandles.hpp"
#include "runtime/sharedRuntime.hpp"
#include "utilities/debug.hpp"
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

    __ enter();
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
      if (_result != x10) {
        __ mv(_result, x10);
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
  assert_different_registers(tmp1, tmp2, dst, noreg);
  assert_different_registers(tmp2, t0);

  Label done;
  Label uncolor;

  // Load bad mask into scratch register.
  const bool on_non_strong =
    (decorators & ON_WEAK_OOP_REF) != 0 ||
    (decorators & ON_PHANTOM_OOP_REF) != 0;

  if (on_non_strong) {
    __ ld(tmp1, mark_bad_mask_from_thread(xthread));
  } else {
    __ ld(tmp1, load_bad_mask_from_thread(xthread));
  }

  __ la(tmp2, src);
  __ ld(dst, tmp2);

  // Test reference against bad mask. If mask bad, then we need to fix it up.
  __ andr(tmp1, dst, tmp1);
  __ beqz(tmp1, uncolor);

  {
    // Call VM
    ZRuntimeCallSpill rsc(masm, dst);

    if (c_rarg0 != dst) {
      __ mv(c_rarg0, dst);
    }
    __ mv(c_rarg1, tmp2);

    __ call_VM_leaf(ZBarrierSetRuntime::load_barrier_on_oop_field_preloaded_addr(decorators), 2);
  }

  // Slow-path has already uncolored
  __ j(done);

  __ bind(uncolor);

  // Remove the color bits
  __ srli(dst, dst, ZPointerLoadShift);

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
  assert_different_registers(rnew_zaddress, rnew_zpointer, rtmp);

  if (in_nmethod) {
    if (is_atomic) {
      __ lhu(rtmp, ref_addr);
      // Atomic operations must ensure that the contents of memory are store-good before
      // an atomic opertion can execute.
      // A non-relocatable object could have spurious raw null pointers in its fields after
      // getting promoted to the old generation.
      __ relocate(barrier_Relocation::spec(), [&] {
        __ li16u(rnew_zpointer, barrier_Relocation::unpatched);
      }, ZBarrierRelocationFormatStoreGoodBits);
      __ bne(rtmp, rnew_zpointer, medium_path, true /* is_far */);
    } else {
      __ ld(rtmp, ref_addr);
      // Stores on relocatable objects never need to deal with raw null pointers in fields.
      // Raw null pointers may only exists in the young generation, as they get pruned when
      // the object is relocated to old. And no pre-write barrier needs to perform any action
      // in the young generation.
      __ relocate(barrier_Relocation::spec(), [&] {
        __ li16u(rnew_zpointer, barrier_Relocation::unpatched);
      }, ZBarrierRelocationFormatStoreBadMask);
      __ andr(rtmp, rtmp, rnew_zpointer);
      __ bnez(rtmp, medium_path, true /* is_far */);
    }
    __ bind(medium_path_continuation);
    __ relocate(barrier_Relocation::spec(), [&] {
      __ li16u(rtmp, barrier_Relocation::unpatched);
    }, ZBarrierRelocationFormatStoreGoodBits);
    __ slli(rnew_zpointer, rnew_zaddress, ZPointerLoadShift);
    __ orr(rnew_zpointer, rnew_zpointer, rtmp);
  } else {
    assert(!is_atomic, "atomic outside of nmethods not supported");
    __ la(rtmp, ref_addr);
    __ ld(rtmp, rtmp);
    __ ld(rnew_zpointer, Address(xthread, ZThreadLocalData::store_bad_mask_offset()));
    __ andr(rtmp, rtmp, rnew_zpointer);
    __ bnez(rtmp, medium_path, true /* is_far */);
    __ bind(medium_path_continuation);
    if (rnew_zaddress == noreg) {
      __ mv(rnew_zpointer, zr);
    } else {
      __ mv(rnew_zpointer, rnew_zaddress);
    }

    // Load the current good shift, and add the color bits
    __ slli(rnew_zpointer, rnew_zpointer, ZPointerLoadShift);
    __ ld(rtmp, Address(xthread, ZThreadLocalData::store_good_mask_offset()));
    __ orr(rnew_zpointer, rnew_zpointer, rtmp);
  }
}

static void store_barrier_buffer_add(MacroAssembler* masm,
                                     Address ref_addr,
                                     Register tmp1,
                                     Register tmp2,
                                     Label& slow_path) {
  Address buffer(xthread, ZThreadLocalData::store_barrier_buffer_offset());
  assert_different_registers(ref_addr.base(), tmp1, tmp2);

  __ ld(tmp1, buffer);

  // Combined pointer bump and check if the buffer is disabled or full
  __ ld(tmp2, Address(tmp1, ZStoreBarrierBuffer::current_offset()));
  __ beqz(tmp2, slow_path);

  // Bump the pointer
  __ sub(tmp2, tmp2, sizeof(ZStoreBarrierEntry));
  __ sd(tmp2, Address(tmp1, ZStoreBarrierBuffer::current_offset()));

  // Compute the buffer entry address
  __ la(tmp2, Address(tmp2, ZStoreBarrierBuffer::buffer_offset()));
  __ add(tmp2, tmp2, tmp1);

  // Compute and log the store address
  __ la(tmp1, ref_addr);
  __ sd(tmp1, Address(tmp2, in_bytes(ZStoreBarrierEntry::p_offset())));

  // Load and log the prev value
  __ ld(tmp1, tmp1);
  __ sd(tmp1, Address(tmp2, in_bytes(ZStoreBarrierEntry::prev_offset())));
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
  assert_different_registers(ref_addr.base(), rtmp1, rtmp2, rtmp3);

  // The reason to end up in the medium path is that the pre-value was not 'good'.
  if (is_native) {
    __ j(slow_path);
    __ bind(slow_path_continuation);
    __ j(medium_path_continuation);
  } else if (is_atomic) {
    // Atomic accesses can get to the medium fast path because the value was a
    // raw null value. If it was not null, then there is no doubt we need to take a slow path.

    __ la(rtmp2, ref_addr);
    __ ld(rtmp1, rtmp2);
    __ bnez(rtmp1, slow_path);

    // If we get this far, we know there is a young raw null value in the field.
    __ relocate(barrier_Relocation::spec(), [&] {
      __ li16u(rtmp1, barrier_Relocation::unpatched);
    }, ZBarrierRelocationFormatStoreGoodBits);
    __ cmpxchg_weak(rtmp2, zr, rtmp1,
                    Assembler::int64,
                    Assembler::relaxed /* acquire */, Assembler::relaxed /* release */,
                    rtmp3);
    __ beqz(rtmp3, slow_path);
    __ bind(slow_path_continuation);
    __ j(medium_path_continuation);
  } else {
    // A non-atomic relocatable object wont't get to the medium fast path due to a
    // raw null in the young generation. We only get here because the field is bad.
    // In this path we don't need any self healing, so we can avoid a runtime call
    // most of the time by buffering the store barrier to be applied lazily.
    store_barrier_buffer_add(masm,
                             ref_addr,
                             rtmp1,
                             rtmp2,
                             slow_path);
    __ bind(slow_path_continuation);
    __ j(medium_path_continuation);
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

  assert_different_registers(val, tmp1, dst.base());

  if (dest_uninitialized) {
    if (val == noreg) {
      __ mv(tmp1, zr);
    } else {
      __ mv(tmp1, val);
    }
    // Add the color bits
    __ slli(tmp1, tmp1, ZPointerLoadShift);
    __ ld(tmp2, Address(xthread, ZThreadLocalData::store_good_mask_offset()));
    __ orr(tmp1, tmp2, tmp1);
  } else {
    Label done;
    Label medium;
    Label medium_continuation;
    Label slow;
    Label slow_continuation;
    store_barrier_fast(masm, dst, val, tmp1, tmp2, false, false, medium, medium_continuation);

    __ j(done);
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
      __ la(c_rarg0, dst);
      __ MacroAssembler::call_VM_leaf(ZBarrierSetRuntime::store_barrier_on_oop_field_without_healing_addr(), 1);
    }

    __ j(slow_continuation);
    __ bind(done);
  }

  // Store value
  BarrierSetAssembler::store_at(masm, decorators, type, dst, tmp1, tmp2, tmp3, noreg);
}

class ZCopyRuntimeCallSpill {
private:
  MacroAssembler* _masm;
  Register _result;

  void save() {
    MacroAssembler* masm = _masm;

    __ enter();
    if (_result != noreg) {
      __ push_call_clobbered_registers_except(RegSet::of(_result));
    } else {
      __ push_call_clobbered_registers();
    }
  }

  void restore() {
    MacroAssembler* masm = _masm;

    if (_result != noreg) {
      if (_result != x10) {
        __ mv(_result, x10);
      }
      __ pop_call_clobbered_registers_except(RegSet::of(_result));
    } else {
      __ pop_call_clobbered_registers();
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
                                              RegSet saved_regs) {}

static void copy_load_barrier(MacroAssembler* masm,
                              Register ref,
                              Address src,
                              Register tmp) {
  Label done;

  __ ld(tmp, Address(xthread, ZThreadLocalData::load_bad_mask_offset()));

  // Test reference against bad mask. If mask bad, then we need to fix it up
  __ andr(tmp, ref, tmp);
  __ beqz(tmp, done);

  {
    // Call VM
    ZCopyRuntimeCallSpill rsc(masm, ref);

    __ la(c_rarg1, src);

    if (c_rarg0 != ref) {
      __ mv(c_rarg0, ref);
    }

    __ call_VM_leaf(ZBarrierSetRuntime::load_barrier_on_oop_field_preloaded_addr(IN_HEAP | ON_STRONG_OOP_REF), 2);
  }

  // Slow-path has uncolored; revert
  __ slli(ref, ref, ZPointerLoadShift);

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
  __ ld(tmp1, Address(xthread, ZThreadLocalData::store_bad_mask_offset()));
  __ andr(tmp1, pre_ref, tmp1);
  __ beqz(tmp1, done);

  store_barrier_buffer_add(masm, src, tmp1, tmp2, slow);
  __ j(done);

  __ bind(slow);
  {
    // Call VM
    ZCopyRuntimeCallSpill rcs(masm, noreg);

    __ la(c_rarg0, src);

    __ call_VM_leaf(ZBarrierSetRuntime::store_barrier_on_oop_field_without_healing_addr(), 1);
  }

  __ bind(done);

  if (new_ref != noreg) {
    // Set store-good color, replacing whatever color was there before
    __ ld(tmp1, Address(xthread, ZThreadLocalData::store_good_mask_offset()));
    __ srli(new_ref, new_ref, 16);
    __ slli(new_ref, new_ref, 16);
    __ orr(new_ref, new_ref, tmp1);
  }
}

void ZBarrierSetAssembler::copy_load_at(MacroAssembler* masm,
                                        DecoratorSet decorators,
                                        BasicType type,
                                        size_t bytes,
                                        Register dst,
                                        Address src,
                                        Register tmp) {
  if (!is_reference_type(type)) {
    BarrierSetAssembler::copy_load_at(masm, decorators, type, bytes, dst, src, noreg);
    return;
  }

  BarrierSetAssembler::copy_load_at(masm, decorators, type, bytes, dst, src, noreg);

  assert(bytes == 8, "unsupported copy step");
  copy_load_barrier(masm, dst, src, tmp);

  if ((decorators & ARRAYCOPY_CHECKCAST) != 0) {
    __ srli(dst, dst, ZPointerLoadShift);
  }
}

void ZBarrierSetAssembler::copy_store_at(MacroAssembler* masm,
                                         DecoratorSet decorators,
                                         BasicType type,
                                         size_t bytes,
                                         Address dst,
                                         Register src,
                                         Register tmp1,
                                         Register tmp2,
                                         Register tmp3) {
  if (!is_reference_type(type)) {
    BarrierSetAssembler::copy_store_at(masm, decorators, type, bytes, dst, src, noreg, noreg, noreg);
    return;
  }

  if ((decorators & ARRAYCOPY_CHECKCAST) != 0) {
    __ slli(src, src, ZPointerLoadShift);
  }

  bool is_dest_uninitialized = (decorators & IS_DEST_UNINITIALIZED) != 0;

  assert(bytes == 8, "unsupported copy step");
  if (is_dest_uninitialized) {
    __ ld(tmp1, Address(xthread, ZThreadLocalData::store_good_mask_offset()));
    __ srli(src, src, 16);
    __ slli(src, src, 16);
    __ orr(src, src, tmp1);
  } else {
    // Store barrier pre values and color new values
    __ ld(tmp1, dst);
    copy_store_barrier(masm, tmp1, src, dst, tmp2, tmp3);
  }

  // Store new values
  BarrierSetAssembler::copy_store_at(masm, decorators, type, bytes, dst, src, noreg, noreg, noreg);
}

bool ZBarrierSetAssembler::supports_rvv_arraycopy() {
  return false;
}

void ZBarrierSetAssembler::try_resolve_jobject_in_native(MacroAssembler* masm,
                                                         Register jni_env,
                                                         Register robj,
                                                         Register tmp,
                                                         Label& slowpath) {
  BLOCK_COMMENT("ZBarrierSetAssembler::try_resolve_jobject_in_native {");

  Label done, tagged, weak_tagged, uncolor;

  // Test for tag
  __ andi(tmp, robj, JNIHandles::tag_mask);
  __ bnez(tmp, tagged);

  // Resolve local handle
  __ ld(robj, robj);
  __ j(done);

  __ bind(tagged);

  // Test for weak tag
  __ andi(tmp, robj, JNIHandles::TypeTag::weak_global);
  __ bnez(tmp, weak_tagged);

  // Resolve global handle
  __ ld(robj, Address(robj, -JNIHandles::TypeTag::global));
  __ la(tmp, load_bad_mask_from_jni_env(jni_env));
  __ ld(tmp, tmp);
  __ andr(tmp, robj, tmp);
  __ bnez(tmp, slowpath);
  __ j(uncolor);

  __ bind(weak_tagged);

  // Resolve weak handle
  __ ld(robj, Address(robj, -JNIHandles::TypeTag::weak_global));
  __ la(tmp, mark_bad_mask_from_jni_env(jni_env));
  __ ld(tmp, tmp);
  __ andr(tmp, robj, tmp);
  __ bnez(tmp, slowpath);

  __ bind(uncolor);

  // Uncolor
  __ srli(robj, robj, ZPointerLoadShift);

  __ bind(done);

  BLOCK_COMMENT("} ZBarrierSetAssembler::try_resolve_jobject_in_native");
}

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
  const uint16_t value = patch_barrier_relocation_value(format);

  int bytes;
  switch (format) {
    case ZBarrierRelocationFormatLoadBadMask:
    case ZBarrierRelocationFormatMarkBadMask:
    case ZBarrierRelocationFormatStoreGoodBits:
    case ZBarrierRelocationFormatStoreBadMask:
      assert(MacroAssembler::is_li16u_at(addr), "invalide zgc barrier");
      bytes = MacroAssembler::pd_patch_instruction_size(addr, (address)(uintptr_t)value);
      break;
    default:
      ShouldNotReachHere();
  }

  // A full fence is generated before icache_flush by default in invalidate_word
  ICache::invalidate_range(addr, bytes);
}

#ifdef COMPILER2

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
        __ mv(c_rarg0, _ref);
      }
      __ mv(c_rarg1, zr);
    } else {
      // Self healing
      if (_ref == c_rarg0) {
        // _ref is already at correct place
        __ la(c_rarg1, _ref_addr);
      } else if (_ref != c_rarg1) {
        // _ref is in wrong place, but not in c_rarg1, so fix it first
        __ la(c_rarg1, _ref_addr);
        __ mv(c_rarg0, _ref);
      } else if (_ref_addr.base() != c_rarg0) {
        assert(_ref == c_rarg1, "Mov ref first, vacating c_rarg0");
        __ mv(c_rarg0, _ref);
        __ la(c_rarg1, _ref_addr);
      } else {
        assert(_ref == c_rarg1, "Need to vacate c_rarg1 and _ref_addr is using c_rarg0");
        if (_ref_addr.base() == c_rarg0) {
          __ mv(t1, c_rarg1);
          __ la(c_rarg1, _ref_addr);
          __ mv(c_rarg0, t1);
        } else {
          ShouldNotReachHere();
        }
      }
    }
  }

  ~ZSetupArguments() {
    // Transfer result
    if (_ref != x10) {
      __ mv(_ref, x10);
    }
  }
};

#undef __
#define __ masm->

void ZBarrierSetAssembler::generate_c2_load_barrier_stub(MacroAssembler* masm, ZLoadBarrierStubC2* stub) const {
  BLOCK_COMMENT("ZLoadBarrierStubC2");

  // Stub entry
  if (!Compile::current()->output()->in_scratch_emit_size()) {
    __ bind(*stub->entry());
  }

  {
    SaveLiveRegisters save_live_registers(masm, stub);
    ZSetupArguments setup_arguments(masm, stub);
    __ mv(t0, stub->slow_path());
    __ jalr(t0);
  }

  // Stub exit
  __ j(*stub->continuation());
}

void ZBarrierSetAssembler::generate_c2_store_barrier_stub(MacroAssembler* masm, ZStoreBarrierStubC2* stub) const {
  BLOCK_COMMENT("ZStoreBarrierStubC2");

  // Stub entry
  __ bind(*stub->entry());

  Label slow;
  Label slow_continuation;
  store_barrier_medium(masm,
                       stub->ref_addr(),
                       stub->new_zpointer(),
                       t1,
                       t0,
                       stub->is_native(),
                       stub->is_atomic(),
                       *stub->continuation(),
                       slow,
                       slow_continuation);

  __ bind(slow);

  {
    SaveLiveRegisters save_live_registers(masm, stub);
    __ la(c_rarg0, stub->ref_addr());

    if (stub->is_native()) {
      __ la(t0, RuntimeAddress(ZBarrierSetRuntime::store_barrier_on_native_oop_field_without_healing_addr()));
    } else if (stub->is_atomic()) {
      __ la(t0, RuntimeAddress(ZBarrierSetRuntime::store_barrier_on_oop_field_with_healing_addr()));
    } else {
      __ la(t0, RuntimeAddress(ZBarrierSetRuntime::store_barrier_on_oop_field_without_healing_addr()));
    }
    __ jalr(t0);
  }

  // Stub exit
  __ j(slow_continuation);
}

#undef __

#endif // COMPILER2

#ifdef COMPILER1
#undef __
#define __ ce->masm()->

static void z_color(LIR_Assembler* ce, LIR_Opr ref) {
  __ relocate(barrier_Relocation::spec(), [&] {
    __ li16u(t1, barrier_Relocation::unpatched);
  }, ZBarrierRelocationFormatStoreGoodBits);
  __ slli(ref->as_register(), ref->as_register(), ZPointerLoadShift);
  __ orr(ref->as_register(), ref->as_register(), t1);
}

static void z_uncolor(LIR_Assembler* ce, LIR_Opr ref) {
  __ srli(ref->as_register(), ref->as_register(), ZPointerLoadShift);
}

static void check_color(LIR_Assembler* ce, LIR_Opr ref, bool on_non_strong) {
  assert_different_registers(t0, xthread, ref->as_register());
  int format = on_non_strong ? ZBarrierRelocationFormatMarkBadMask
                             : ZBarrierRelocationFormatLoadBadMask;
  Label good;
  __ relocate(barrier_Relocation::spec(), [&] {
    __ li16u(t0, barrier_Relocation::unpatched);
  }, format);
  __ andr(t0, ref->as_register(), t0);
}

void ZBarrierSetAssembler::generate_c1_color(LIR_Assembler* ce, LIR_Opr ref) const {
  z_color(ce, ref);
}

void ZBarrierSetAssembler::generate_c1_uncolor(LIR_Assembler* ce, LIR_Opr ref) const {
  z_uncolor(ce, ref);
}

void ZBarrierSetAssembler::generate_c1_load_barrier(LIR_Assembler* ce,
                                                    LIR_Opr ref,
                                                    ZLoadBarrierStubC1* stub,
                                                    bool on_non_strong) const {
  Label good;
  check_color(ce, ref, on_non_strong);
  __ beqz(t0, good);
  __ j(*stub->entry());

  __ bind(good);
  z_uncolor(ce, ref);
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

   // Save x10 unless it is the result or tmp register
   // Set up SP to accommdate parameters and maybe x10.
   if (ref != x10 && tmp != x10) {
     __ sub(sp, sp, 32);
     __ sd(x10, Address(sp, 16));
   } else {
     __ sub(sp, sp, 16);
   }

   // Setup arguments and call runtime stub
   ce->store_parameter(ref_addr, 1);
   ce->store_parameter(ref, 0);

   __ far_call(stub->runtime_stub());

   // Verify result
   __ verify_oop(x10);

   // Move result into place
   if (ref != x10) {
     __ mv(ref, x10);
   }

   // Restore x10 unless it is the result or tmp register
   if (ref != x10 && tmp != x10) {
     __ ld(x10, Address(sp, 16));
     __ addi(sp, sp, 32);
   } else {
     __ addi(sp, sp, 16);
   }

   // Stub exit
   __ j(*stub->continuation());
}

#undef __
#define __ sasm->

void ZBarrierSetAssembler::generate_c1_load_barrier_runtime_stub(StubAssembler* sasm,
                                                                 DecoratorSet decorators) const {
  __ prologue("zgc_load_barrier stub", false);

  __ push_call_clobbered_registers_except(RegSet::of(x10));

  // Setup arguments
  __ load_parameter(0, c_rarg0);
  __ load_parameter(1, c_rarg1);

  __ call_VM_leaf(ZBarrierSetRuntime::load_barrier_on_oop_field_preloaded_addr(decorators), 2);

  __ pop_call_clobbered_registers_except(RegSet::of(x10));

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

#undef __
#define __ ce->masm()->

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
                     t1,
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
                       t1,
                       stub->new_zpointer()->as_register(),
                       stub->tmp()->as_pointer_register(),
                       false /* is_native */,
                       stub->is_atomic(),
                       *stub->continuation(),
                       slow,
                       slow_continuation);

  __ bind(slow);

  __ la(stub->new_zpointer()->as_register(), ce->as_Address(stub->ref_addr()->as_address_ptr()));

  __ sub(sp, sp, 16);
  //Setup arguments and call runtime stub
  assert(stub->new_zpointer()->is_valid(), "invariant");
  ce->store_parameter(stub->new_zpointer()->as_register(), 0);
  __ far_call(stub->runtime_stub());
  __ addi(sp, sp, 16);

  // Stub exit
  __ j(slow_continuation);
}

#undef __

#endif // COMPILER1

#undef __
#define __ masm->

void ZBarrierSetAssembler::check_oop(MacroAssembler* masm, Register obj, Register tmp1, Register tmp2, Label& error) {
  // C1 calls verify_oop in the middle of barriers, before they have been uncolored
  // and after being colored. Therefore, we must deal with colored oops as well.
  Label done;
  Label check_oop;
  Label check_zaddress;
  int color_bits = ZPointerRemappedShift + ZPointerRemappedBits;

  uintptr_t shifted_base_start_mask = (UCONST64(1) << (ZAddressHeapBaseShift + color_bits + 1)) - 1;
  uintptr_t shifted_base_end_mask = (UCONST64(1) << (ZAddressHeapBaseShift + 1)) - 1;
  uintptr_t shifted_base_mask = shifted_base_start_mask ^ shifted_base_end_mask;

  uintptr_t shifted_address_end_mask = (UCONST64(1) << (color_bits + 1)) - 1;
  uintptr_t shifted_address_mask = shifted_base_end_mask ^ (uintptr_t)CONST64(-1);

  // Check colored null
  __ mv(tmp1, shifted_address_mask);
  __ andr(tmp1, tmp1, obj);
  __ beqz(tmp1, done);

  // Check for zpointer
  __ mv(tmp1, shifted_base_mask);
  __ andr(tmp1, tmp1, obj);
  __ beqz(tmp1, check_oop);

  // Uncolor presumed zpointer
  __ srli(obj, obj, ZPointerLoadShift);

  __ j(check_zaddress);

  __ bind(check_oop);

  // Make sure klass is 'reasonable', which is not zero
  __ load_klass(tmp1, obj, tmp2);
  __ beqz(tmp1, error);

  __ bind(check_zaddress);
  // Check if the oop is the right area of memory
  __ mv(tmp1, (intptr_t) Universe::verify_oop_mask());
  __ andr(tmp1, tmp1, obj);
  __ mv(obj, (intptr_t) Universe::verify_oop_bits());
  __ bne(tmp1, obj, error);

  __ bind(done);
}

#undef __
