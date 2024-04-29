/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "compiler/compileTask.hpp"
#include "gc/z/zAddress.hpp"
#include "gc/z/zBarrier.inline.hpp"
#include "gc/z/zBarrierSet.hpp"
#include "gc/z/zBarrierSetAssembler.hpp"
#include "gc/z/zBarrierSetRuntime.hpp"
#include "gc/z/zThreadLocalData.hpp"
#include "memory/resourceArea.hpp"
#include "runtime/jniHandles.hpp"
#include "runtime/sharedRuntime.hpp"
#include "utilities/macros.hpp"
#ifdef COMPILER1
#include "c1/c1_LIRAssembler.hpp"
#include "c1/c1_MacroAssembler.hpp"
#include "gc/z/c1/zBarrierSetC1.hpp"
#endif // COMPILER1
#ifdef COMPILER2
#include "c2_intelJccErratum_x86.hpp"
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

ZBarrierSetAssembler::ZBarrierSetAssembler()
  : _load_bad_relocations(),
    _store_bad_relocations(),
    _store_good_relocations() {}

enum class ZXMMSpillMode {
  none,
  avx128,
  avx256
};

// Helper for saving and restoring registers across a runtime call that does
// not have any live vector registers.
class ZRuntimeCallSpill {
private:
  const ZXMMSpillMode _xmm_spill_mode;
  const int _xmm_size;
  const int _xmm_spill_size;
  MacroAssembler* _masm;
  Register _result;

  void save() {
    MacroAssembler* masm = _masm;
    __ push(rax);
    __ push(rcx);
    __ push(rdx);
    __ push(rdi);
    __ push(rsi);
    __ push(r8);
    __ push(r9);
    __ push(r10);
    __ push(r11);

    if (_xmm_spill_size != 0) {
      __ subptr(rsp, _xmm_spill_size);
      if (_xmm_spill_mode == ZXMMSpillMode::avx128) {
        __ movdqu(Address(rsp, _xmm_size * 7), xmm7);
        __ movdqu(Address(rsp, _xmm_size * 6), xmm6);
        __ movdqu(Address(rsp, _xmm_size * 5), xmm5);
        __ movdqu(Address(rsp, _xmm_size * 4), xmm4);
        __ movdqu(Address(rsp, _xmm_size * 3), xmm3);
        __ movdqu(Address(rsp, _xmm_size * 2), xmm2);
        __ movdqu(Address(rsp, _xmm_size * 1), xmm1);
        __ movdqu(Address(rsp, _xmm_size * 0), xmm0);
      } else {
        assert(_xmm_spill_mode == ZXMMSpillMode::avx256, "AVX support ends at avx256");
        __ vmovdqu(Address(rsp, _xmm_size * 7), xmm7);
        __ vmovdqu(Address(rsp, _xmm_size * 6), xmm6);
        __ vmovdqu(Address(rsp, _xmm_size * 5), xmm5);
        __ vmovdqu(Address(rsp, _xmm_size * 4), xmm4);
        __ vmovdqu(Address(rsp, _xmm_size * 3), xmm3);
        __ vmovdqu(Address(rsp, _xmm_size * 2), xmm2);
        __ vmovdqu(Address(rsp, _xmm_size * 1), xmm1);
        __ vmovdqu(Address(rsp, _xmm_size * 0), xmm0);
      }
    }
  }

  void restore() {
    MacroAssembler* masm = _masm;
    if (_xmm_spill_size != 0) {
      if (_xmm_spill_mode == ZXMMSpillMode::avx128) {
        __ movdqu(xmm0, Address(rsp, _xmm_size * 0));
        __ movdqu(xmm1, Address(rsp, _xmm_size * 1));
        __ movdqu(xmm2, Address(rsp, _xmm_size * 2));
        __ movdqu(xmm3, Address(rsp, _xmm_size * 3));
        __ movdqu(xmm4, Address(rsp, _xmm_size * 4));
        __ movdqu(xmm5, Address(rsp, _xmm_size * 5));
        __ movdqu(xmm6, Address(rsp, _xmm_size * 6));
        __ movdqu(xmm7, Address(rsp, _xmm_size * 7));
      } else {
        assert(_xmm_spill_mode == ZXMMSpillMode::avx256, "AVX support ends at avx256");
        __ vmovdqu(xmm0, Address(rsp, _xmm_size * 0));
        __ vmovdqu(xmm1, Address(rsp, _xmm_size * 1));
        __ vmovdqu(xmm2, Address(rsp, _xmm_size * 2));
        __ vmovdqu(xmm3, Address(rsp, _xmm_size * 3));
        __ vmovdqu(xmm4, Address(rsp, _xmm_size * 4));
        __ vmovdqu(xmm5, Address(rsp, _xmm_size * 5));
        __ vmovdqu(xmm6, Address(rsp, _xmm_size * 6));
        __ vmovdqu(xmm7, Address(rsp, _xmm_size * 7));
      }
      __ addptr(rsp, _xmm_spill_size);
    }

    __ pop(r11);
    __ pop(r10);
    __ pop(r9);
    __ pop(r8);
    __ pop(rsi);
    __ pop(rdi);
    __ pop(rdx);
    __ pop(rcx);
    if (_result == noreg) {
      __ pop(rax);
    } else if (_result == rax) {
      __ addptr(rsp, wordSize);
    } else {
      __ movptr(_result, rax);
      __ pop(rax);
    }
  }

  static int compute_xmm_size(ZXMMSpillMode spill_mode) {
    switch (spill_mode) {
      case ZXMMSpillMode::none:
        return 0;
      case ZXMMSpillMode::avx128:
        return wordSize * 2;
      case ZXMMSpillMode::avx256:
        return wordSize * 4;
      default:
        ShouldNotReachHere();
        return 0;
    }
  }

public:
  ZRuntimeCallSpill(MacroAssembler* masm, Register result, ZXMMSpillMode spill_mode)
    : _xmm_spill_mode(spill_mode),
      _xmm_size(compute_xmm_size(spill_mode)),
      _xmm_spill_size(_xmm_size * Argument::n_float_register_parameters_j),
      _masm(masm),
      _result(result) {
    // We may end up here from generate_native_wrapper, then the method may have
    // floats as arguments, and we must spill them before calling the VM runtime
    // leaf. From the interpreter all floats are passed on the stack.
    assert(Argument::n_float_register_parameters_j == 8, "Assumption");
    save();
  }

  ~ZRuntimeCallSpill() {
    restore();
  }
};

static void call_vm(MacroAssembler* masm,
                    address entry_point,
                    Register arg0,
                    Register arg1) {
  // Setup arguments
  if (arg1 == c_rarg0) {
    if (arg0 == c_rarg1) {
      __ xchgptr(c_rarg1, c_rarg0);
    } else {
      __ movptr(c_rarg1, arg1);
      __ movptr(c_rarg0, arg0);
    }
  } else {
    if (arg0 != c_rarg0) {
      __ movptr(c_rarg0, arg0);
    }
    if (arg1 != c_rarg1) {
      __ movptr(c_rarg1, arg1);
    }
  }

  // Call VM
  __ MacroAssembler::call_VM_leaf_base(entry_point, 2);
}

void ZBarrierSetAssembler::load_at(MacroAssembler* masm,
                                   DecoratorSet decorators,
                                   BasicType type,
                                   Register dst,
                                   Address src,
                                   Register tmp1,
                                   Register tmp_thread) {
  if (!ZBarrierSet::barrier_needed(decorators, type)) {
    // Barrier not needed
    BarrierSetAssembler::load_at(masm, decorators, type, dst, src, tmp1, tmp_thread);
    return;
  }

  BLOCK_COMMENT("ZBarrierSetAssembler::load_at {");

  // Allocate scratch register
  Register scratch = tmp1;
  if (tmp1 == noreg) {
    scratch = r12;
    __ push(scratch);
  }

  assert_different_registers(dst, scratch);

  Label done;
  Label uncolor;

  //
  // Fast Path
  //

  // Load address
  __ lea(scratch, src);

  // Load oop at address
  __ movptr(dst, Address(scratch, 0));

  const bool on_non_strong =
      (decorators & ON_WEAK_OOP_REF) != 0 ||
      (decorators & ON_PHANTOM_OOP_REF) != 0;

  // Test address bad mask
  if (on_non_strong) {
    __ testptr(dst, mark_bad_mask_from_thread(r15_thread));
  } else {
    __ testptr(dst, load_bad_mask_from_thread(r15_thread));
  }

  __ jcc(Assembler::zero, uncolor);

  //
  // Slow path
  //

  {
    // Call VM
    ZRuntimeCallSpill rcs(masm, dst, ZXMMSpillMode::avx128);
    call_vm(masm, ZBarrierSetRuntime::load_barrier_on_oop_field_preloaded_addr(decorators), dst, scratch);
  }

  // Slow-path has already uncolored
  __ jmp(done);

  __ bind(uncolor);

  __ movptr(scratch, rcx); // Save rcx because shrq needs shift in rcx
  __ movptr(rcx, ExternalAddress((address)&ZPointerLoadShift));
  if (dst == rcx) {
    // Dst was rcx which is saved in scratch because shrq needs rcx for shift
    __ shrq(scratch);
  } else {
    __ shrq(dst);
  }
  __ movptr(rcx, scratch); // restore rcx

  __ bind(done);

  // Restore scratch register
  if (tmp1 == noreg) {
    __ pop(scratch);
  }

  BLOCK_COMMENT("} ZBarrierSetAssembler::load_at");
}

static void emit_store_fast_path_check(MacroAssembler* masm, Address ref_addr, bool is_atomic, Label& medium_path) {
  if (is_atomic) {
    // Atomic operations must ensure that the contents of memory are store-good before
    // an atomic operation can execute.
    // A not relocatable object could have spurious raw null pointers in its fields after
    // getting promoted to the old generation.
    __ cmpw(ref_addr, barrier_Relocation::unpatched);
    __ relocate(barrier_Relocation::spec(), ZBarrierRelocationFormatStoreGoodAfterCmp);
  } else {
    // Stores on relocatable objects never need to deal with raw null pointers in fields.
    // Raw null pointers may only exist in the young generation, as they get pruned when
    // the object is relocated to old. And no pre-write barrier needs to perform any action
    // in the young generation.
    __ Assembler::testl(ref_addr, barrier_Relocation::unpatched);
    __ relocate(barrier_Relocation::spec(), ZBarrierRelocationFormatStoreBadAfterTest);
  }
  __ jcc(Assembler::notEqual, medium_path);
}

#ifdef COMPILER2
static int store_fast_path_check_size(MacroAssembler* masm, Address ref_addr, bool is_atomic, Label& medium_path) {
  if (!VM_Version::has_intel_jcc_erratum()) {
    return 0;
  }
  int size = 0;
  bool in_scratch_emit_size = masm->code_section()->scratch_emit();
  if (!in_scratch_emit_size) {
    // Temporarily register as scratch buffer so that relocations don't register
    masm->code_section()->set_scratch_emit();
  }
  // First emit the code, to measure its size
  address insts_end = masm->code_section()->end();
  // The dummy medium path label is bound after the code emission. This ensures
  // full size of the generated jcc, which is what the real barrier will have
  // as well, as it also binds after the emission of the barrier.
  Label dummy_medium_path;
  emit_store_fast_path_check(masm, ref_addr, is_atomic, dummy_medium_path);
  address emitted_end = masm->code_section()->end();
  size = (int)(intptr_t)(emitted_end - insts_end);
  __ bind(dummy_medium_path);
  if (!in_scratch_emit_size) {
    // Potentially restore scratchyness
    masm->code_section()->clear_scratch_emit();
  }
  // Roll back code, now that we know the size
  masm->code_section()->set_end(insts_end);
  return size;
}
#endif

static void emit_store_fast_path_check_c2(MacroAssembler* masm, Address ref_addr, bool is_atomic, Label& medium_path) {
#ifdef COMPILER2
  // This is a JCC erratum mitigation wrapper for calling the inner check
  int size = store_fast_path_check_size(masm, ref_addr, is_atomic, medium_path);
  // Emit JCC erratum mitigation nops with the right size
  IntelJccErratumAlignment intel_alignment(masm, size);
  // Emit the JCC erratum mitigation guarded code
  emit_store_fast_path_check(masm, ref_addr, is_atomic, medium_path);
#endif
}

static bool is_c2_compilation() {
  CompileTask* task = ciEnv::current()->task();
  return task != nullptr && is_c2_compile(task->comp_level());
}

void ZBarrierSetAssembler::store_barrier_fast(MacroAssembler* masm,
                                              Address ref_addr,
                                              Register rnew_zaddress,
                                              Register rnew_zpointer,
                                              bool in_nmethod,
                                              bool is_atomic,
                                              Label& medium_path,
                                              Label& medium_path_continuation) const {
  assert_different_registers(ref_addr.base(), rnew_zpointer);
  assert_different_registers(ref_addr.index(), rnew_zpointer);
  assert_different_registers(rnew_zaddress, rnew_zpointer);

  if (in_nmethod) {
    if (is_c2_compilation()) {
      emit_store_fast_path_check_c2(masm, ref_addr, is_atomic, medium_path);
    } else {
      emit_store_fast_path_check(masm, ref_addr, is_atomic, medium_path);
    }
    __ bind(medium_path_continuation);
    if (rnew_zaddress != noreg) {
      // noreg means null; no need to color
      __ movptr(rnew_zpointer, rnew_zaddress);
      __ relocate(barrier_Relocation::spec(), ZBarrierRelocationFormatLoadGoodBeforeShl);
      __ shlq(rnew_zpointer, barrier_Relocation::unpatched);
      __ orq_imm32(rnew_zpointer, barrier_Relocation::unpatched);
      __ relocate(barrier_Relocation::spec(), ZBarrierRelocationFormatStoreGoodAfterOr);
    }
  } else {
    __ movzwq(rnew_zpointer, ref_addr);
    __ testq(rnew_zpointer, Address(r15_thread, ZThreadLocalData::store_bad_mask_offset()));
    __ jcc(Assembler::notEqual, medium_path);
    __ bind(medium_path_continuation);
    if (rnew_zaddress == noreg) {
      __ xorptr(rnew_zpointer, rnew_zpointer);
    } else {
      __ movptr(rnew_zpointer, rnew_zaddress);
    }
    assert_different_registers(rcx, rnew_zpointer);
    __ push(rcx);
    __ movptr(rcx, ExternalAddress((address)&ZPointerLoadShift));
    __ shlq(rnew_zpointer);
    __ pop(rcx);
    __ orq(rnew_zpointer, Address(r15_thread, ZThreadLocalData::store_good_mask_offset()));
  }
}

static void store_barrier_buffer_add(MacroAssembler* masm,
                                     Address ref_addr,
                                     Register tmp1,
                                     Label& slow_path) {
  Address buffer(r15_thread, ZThreadLocalData::store_barrier_buffer_offset());

  __ movptr(tmp1, buffer);

  // Combined pointer bump and check if the buffer is disabled or full
  __ cmpptr(Address(tmp1, ZStoreBarrierBuffer::current_offset()), 0);
  __ jcc(Assembler::equal, slow_path);

  Register tmp2 = r15_thread;
  __ push(tmp2);

  // Bump the pointer
  __ movq(tmp2, Address(tmp1, ZStoreBarrierBuffer::current_offset()));
  __ subq(tmp2, sizeof(ZStoreBarrierEntry));
  __ movq(Address(tmp1, ZStoreBarrierBuffer::current_offset()), tmp2);

  // Compute the buffer entry address
  __ lea(tmp2, Address(tmp1, tmp2, Address::times_1, ZStoreBarrierBuffer::buffer_offset()));

  // Compute and log the store address
  __ lea(tmp1, ref_addr);
  __ movptr(Address(tmp2, in_bytes(ZStoreBarrierEntry::p_offset())), tmp1);

  // Load and log the prev value
  __ movptr(tmp1, Address(tmp1, 0));
  __ movptr(Address(tmp2, in_bytes(ZStoreBarrierEntry::prev_offset())), tmp1);

  __ pop(tmp2);
}

void ZBarrierSetAssembler::store_barrier_medium(MacroAssembler* masm,
                                                Address ref_addr,
                                                Register tmp,
                                                bool is_native,
                                                bool is_atomic,
                                                Label& medium_path_continuation,
                                                Label& slow_path,
                                                Label& slow_path_continuation) const {
  assert_different_registers(ref_addr.base(), tmp);

  // The reason to end up in the medium path is that the pre-value was not 'good'.

  if (is_native) {
    __ jmp(slow_path);
    __ bind(slow_path_continuation);
    __ jmp(medium_path_continuation);
  } else if (is_atomic) {
    // Atomic accesses can get to the medium fast path because the value was a
    // raw null value. If it was not null, then there is no doubt we need to take a slow path.
    __ cmpptr(ref_addr, 0);
    __ jcc(Assembler::notEqual, slow_path);

    // If we get this far, we know there is a young raw null value in the field.
    // Try to self-heal null values for atomic accesses
    __ push(rax);
    __ push(rbx);
    __ push(rcx);

    __ lea(rcx, ref_addr);
    __ xorq(rax, rax);
    __ movptr(rbx, Address(r15, ZThreadLocalData::store_good_mask_offset()));

    __ lock();
    __ cmpxchgq(rbx, Address(rcx, 0));

    __ pop(rcx);
    __ pop(rbx);
    __ pop(rax);

    __ jcc(Assembler::notEqual, slow_path);

    __ bind(slow_path_continuation);
    __ jmp(medium_path_continuation);
  } else {
    // A non-atomic relocatable object won't get to the medium fast path due to a
    // raw null in the young generation. We only get here because the field is bad.
    // In this path we don't need any self healing, so we can avoid a runtime call
    // most of the time by buffering the store barrier to be applied lazily.
    store_barrier_buffer_add(masm,
                             ref_addr,
                             tmp,
                             slow_path);
    __ bind(slow_path_continuation);
    __ jmp(medium_path_continuation);
  }
}

void ZBarrierSetAssembler::store_at(MacroAssembler* masm,
                                    DecoratorSet decorators,
                                    BasicType type,
                                    Address dst,
                                    Register src,
                                    Register tmp1,
                                    Register tmp2,
                                    Register tmp3) {
  BLOCK_COMMENT("ZBarrierSetAssembler::store_at {");

  bool dest_uninitialized = (decorators & IS_DEST_UNINITIALIZED) != 0;

  if (is_reference_type(type)) {
    assert_different_registers(src, tmp1, dst.base(), dst.index());

    if (dest_uninitialized) {
      assert_different_registers(rcx, tmp1);
      if (src == noreg) {
        __ xorq(tmp1, tmp1);
      } else {
        __ movptr(tmp1, src);
      }
      __ push(rcx);
      __ movptr(rcx, ExternalAddress((address)&ZPointerLoadShift));
      __ shlq(tmp1);
      __ pop(rcx);
      __ orq(tmp1, Address(r15_thread, ZThreadLocalData::store_good_mask_offset()));
    } else {
      Label done;
      Label medium;
      Label medium_continuation;
      Label slow;
      Label slow_continuation;
      store_barrier_fast(masm, dst, src, tmp1, false, false, medium, medium_continuation);
      __ jmp(done);
      __ bind(medium);
      store_barrier_medium(masm,
                           dst,
                           tmp1,
                           false /* is_native */,
                           false /* is_atomic */,
                           medium_continuation,
                           slow,
                           slow_continuation);

      __ bind(slow);
      {
        // Call VM
        ZRuntimeCallSpill rcs(masm, noreg, ZXMMSpillMode::avx128);
        __ leaq(c_rarg0, dst);
        __ MacroAssembler::call_VM_leaf(ZBarrierSetRuntime::store_barrier_on_oop_field_without_healing_addr(), c_rarg0);
      }

      __ jmp(slow_continuation);
      __ bind(done);
    }

    // Store value
    BarrierSetAssembler::store_at(masm, decorators, type, dst, tmp1, noreg, noreg, noreg);
  } else {
    BarrierSetAssembler::store_at(masm, decorators, type, dst, src, noreg, noreg, noreg);
  }

  BLOCK_COMMENT("} ZBarrierSetAssembler::store_at");
}

bool ZBarrierSetAssembler::supports_avx3_masked_arraycopy() {
  return false;
}

static void load_arraycopy_masks(MacroAssembler* masm) {
  // xmm2: load_bad_mask
  // xmm3: store_bad_mask
  // xmm4: store_good_mask
  if (UseAVX >= 2) {
    __ lea(r10, ExternalAddress((address)&ZPointerVectorLoadBadMask));
    __ vmovdqu(xmm2, Address(r10, 0));
    __ lea(r10, ExternalAddress((address)&ZPointerVectorStoreBadMask));
    __ vmovdqu(xmm3, Address(r10, 0));
    __ lea(r10, ExternalAddress((address)&ZPointerVectorStoreGoodMask));
    __ vmovdqu(xmm4, Address(r10, 0));
  } else {
    __ lea(r10, ExternalAddress((address)&ZPointerVectorLoadBadMask));
    __ movdqu(xmm2, Address(r10, 0));
    __ lea(r10, ExternalAddress((address)&ZPointerVectorStoreBadMask));
    __ movdqu(xmm3, Address(r10, 0));
    __ lea(r10, ExternalAddress((address)&ZPointerVectorStoreGoodMask));
    __ movdqu(xmm4, Address(r10, 0));
  }
}

static ZXMMSpillMode compute_arraycopy_spill_mode() {
  if (UseAVX >= 2) {
    return ZXMMSpillMode::avx256;
  } else {
    return ZXMMSpillMode::avx128;
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
    BarrierSetAssembler::copy_load_at(masm, decorators, type, bytes, dst, src, tmp);
    return;
  }

  Label load_done;

  // Load oop at address
  __ movptr(dst, src);

  // Test address bad mask
  __ Assembler::testl(dst, (int32_t)(uint32_t)ZPointerLoadBadMask);
  _load_bad_relocations.append(__ code_section()->end());
  __ jcc(Assembler::zero, load_done);

  {
    // Call VM
    ZRuntimeCallSpill rcs(masm, dst, compute_arraycopy_spill_mode());
    __ leaq(c_rarg1, src);
    call_vm(masm, ZBarrierSetRuntime::load_barrier_on_oop_field_preloaded_store_good_addr(), dst, c_rarg1);
  }

  __ bind(load_done);

  // Remove metadata bits so that the store side (vectorized or non-vectorized) can
  // inject the store-good color with an or instruction.
  __ andq(dst, _zpointer_address_mask);

  if ((decorators & ARRAYCOPY_CHECKCAST) != 0) {
    // The checkcast arraycopy needs to be able to dereference the oops in order to perform a typechecks.
    assert(tmp != rcx, "Surprising choice of temp register");
    __ movptr(tmp, rcx);
    __ movptr(rcx, ExternalAddress((address)&ZPointerLoadShift));
    __ shrq(dst);
    __ movptr(rcx, tmp);
  }
}

void ZBarrierSetAssembler::copy_store_at(MacroAssembler* masm,
                                         DecoratorSet decorators,
                                         BasicType type,
                                         size_t bytes,
                                         Address dst,
                                         Register src,
                                         Register tmp) {
  if (!is_reference_type(type)) {
    BarrierSetAssembler::copy_store_at(masm, decorators, type, bytes, dst, src, tmp);
    return;
  }

  bool dest_uninitialized = (decorators & IS_DEST_UNINITIALIZED) != 0;

  if (!dest_uninitialized) {
    Label store;
    Label store_bad;
    __ Assembler::testl(dst, (int32_t)(uint32_t)ZPointerStoreBadMask);
    _store_bad_relocations.append(__ code_section()->end());
    __ jcc(Assembler::zero, store);

    store_barrier_buffer_add(masm, dst, tmp, store_bad);
    __ jmp(store);

    __ bind(store_bad);
    {
      // Call VM
      ZRuntimeCallSpill rcs(masm, noreg, compute_arraycopy_spill_mode());
      __ leaq(c_rarg0, dst);
      __ MacroAssembler::call_VM_leaf(ZBarrierSetRuntime::store_barrier_on_oop_field_without_healing_addr(), c_rarg0);
    }

    __ bind(store);
  }

  if ((decorators & ARRAYCOPY_CHECKCAST) != 0) {
    assert(tmp != rcx, "Surprising choice of temp register");
    __ movptr(tmp, rcx);
    __ movptr(rcx, ExternalAddress((address)&ZPointerLoadShift));
    __ shlq(src);
    __ movptr(rcx, tmp);
  }

  // Color
  __ orq_imm32(src, (int32_t)(uint32_t)ZPointerStoreGoodMask);
  _store_good_relocations.append(__ code_section()->end());

  // Store value
  __ movptr(dst, src);
}

void ZBarrierSetAssembler::copy_load_at(MacroAssembler* masm,
                                        DecoratorSet decorators,
                                        BasicType type,
                                        size_t bytes,
                                        XMMRegister dst,
                                        Address src,
                                        Register tmp,
                                        XMMRegister xmm_tmp) {
  if (!is_reference_type(type)) {
    BarrierSetAssembler::copy_load_at(masm, decorators, type, bytes, dst, src, tmp, xmm_tmp);
    return;
  }
  Address src0(src.base(), src.index(), src.scale(), src.disp() + 0);
  Address src1(src.base(), src.index(), src.scale(), src.disp() + 8);
  Address src2(src.base(), src.index(), src.scale(), src.disp() + 16);
  Address src3(src.base(), src.index(), src.scale(), src.disp() + 24);

  // Registers set up in the prologue:
  // xmm2: load_bad_mask
  // xmm3: store_bad_mask
  // xmm4: store_good_mask

  if (bytes == 16) {
    Label done;
    Label fallback;

    if (UseAVX >= 1) {
      // Load source vector
      __ movdqu(dst, src);
      // Check source load-good
      __ movdqu(xmm_tmp, dst);
      __ ptest(xmm_tmp, xmm2);
      __ jcc(Assembler::notZero, fallback);

      // Remove bad metadata bits
      __ vpandn(dst, xmm3, dst, Assembler::AVX_128bit);
      __ jmp(done);
    }

    __ bind(fallback);

    __ subptr(rsp, wordSize * 2);

    ZBarrierSetAssembler::copy_load_at(masm, decorators, type, 8, tmp, src0, noreg);
    __ movq(Address(rsp, 0), tmp);
    ZBarrierSetAssembler::copy_load_at(masm, decorators, type, 8, tmp, src1, noreg);
    __ movq(Address(rsp, 8), tmp);

    __ movdqu(dst, Address(rsp, 0));
    __ addptr(rsp, wordSize * 2);

    __ bind(done);
  } else if (bytes == 32) {
    Label done;
    Label fallback;
    assert(UseAVX >= 2, "Assume that UseAVX >= 2");

    // Load source vector
    __ vmovdqu(dst, src);
    // Check source load-good
    __ vmovdqu(xmm_tmp, dst);
    __ vptest(xmm_tmp, xmm2, Assembler::AVX_256bit);
    __ jcc(Assembler::notZero, fallback);

    // Remove bad metadata bits so that the store can colour the pointers with an or instruction.
    // This makes the fast path and slow path formats look the same, in the sense that they don't
    // have any of the store bad bits.
    __ vpandn(dst, xmm3, dst, Assembler::AVX_256bit);
    __ jmp(done);

    __ bind(fallback);

    __ subptr(rsp, wordSize * 4);

    ZBarrierSetAssembler::copy_load_at(masm, decorators, type, 8, tmp, src0, noreg);
    __ movq(Address(rsp, 0), tmp);
    ZBarrierSetAssembler::copy_load_at(masm, decorators, type, 8, tmp, src1, noreg);
    __ movq(Address(rsp, 8), tmp);
    ZBarrierSetAssembler::copy_load_at(masm, decorators, type, 8, tmp, src2, noreg);
    __ movq(Address(rsp, 16), tmp);
    ZBarrierSetAssembler::copy_load_at(masm, decorators, type, 8, tmp, src3, noreg);
    __ movq(Address(rsp, 24), tmp);

    __ vmovdqu(dst, Address(rsp, 0));
    __ addptr(rsp, wordSize * 4);

    __ bind(done);
  }
}

void ZBarrierSetAssembler::copy_store_at(MacroAssembler* masm,
                                         DecoratorSet decorators,
                                         BasicType type,
                                         size_t bytes,
                                         Address dst,
                                         XMMRegister src,
                                         Register tmp1,
                                         Register tmp2,
                                         XMMRegister xmm_tmp) {
  if (!is_reference_type(type)) {
    BarrierSetAssembler::copy_store_at(masm, decorators, type, bytes, dst, src, tmp1, tmp2, xmm_tmp);
    return;
  }
  Address dst0(dst.base(), dst.index(), dst.scale(), dst.disp() + 0);
  Address dst1(dst.base(), dst.index(), dst.scale(), dst.disp() + 8);
  Address dst2(dst.base(), dst.index(), dst.scale(), dst.disp() + 16);
  Address dst3(dst.base(), dst.index(), dst.scale(), dst.disp() + 24);

  bool dest_uninitialized = (decorators & IS_DEST_UNINITIALIZED) != 0;

  // Registers set up in the prologue:
  // xmm2: load_bad_mask
  // xmm3: store_bad_mask
  // xmm4: store_good_mask

  if (bytes == 16) {
    Label done;
    Label fallback;

    if (UseAVX >= 1) {
      if (!dest_uninitialized) {
        // Load destination vector
        __ movdqu(xmm_tmp, dst);
        // Check destination store-good
        __ ptest(xmm_tmp, xmm3);
        __ jcc(Assembler::notZero, fallback);
      }

      // Color source
      __ por(src, xmm4);
      // Store source in destination
      __ movdqu(dst, src);
      __ jmp(done);
    }

    __ bind(fallback);

    __ subptr(rsp, wordSize * 2);
    __ movdqu(Address(rsp, 0), src);

    __ movq(tmp1, Address(rsp, 0));
    ZBarrierSetAssembler::copy_store_at(masm, decorators, type, 8, dst0, tmp1, tmp2);
    __ movq(tmp1, Address(rsp, 8));
    ZBarrierSetAssembler::copy_store_at(masm, decorators, type, 8, dst1, tmp1, tmp2);

    __ addptr(rsp, wordSize * 2);

    __ bind(done);
  } else if (bytes == 32) {
    Label done;
    Label fallback;
    assert(UseAVX >= 2, "Assume UseAVX >= 2");

    if (!dest_uninitialized) {
      // Load destination vector
      __ vmovdqu(xmm_tmp, dst);
      // Check destination store-good
      __ vptest(xmm_tmp, xmm3, Assembler::AVX_256bit);
      __ jcc(Assembler::notZero, fallback);
    }

    // Color source
    __ vpor(src, src, xmm4, Assembler::AVX_256bit);

    // Store colored source in destination
    __ vmovdqu(dst, src);
    __ jmp(done);

    __ bind(fallback);

    __ subptr(rsp, wordSize * 4);
    __ vmovdqu(Address(rsp, 0), src);

    __ movq(tmp1, Address(rsp, 0));
    ZBarrierSetAssembler::copy_store_at(masm, decorators, type, 8, dst0, tmp1, tmp2);
    __ movq(tmp1, Address(rsp, 8));
    ZBarrierSetAssembler::copy_store_at(masm, decorators, type, 8, dst1, tmp1, tmp2);
    __ movq(tmp1, Address(rsp, 16));
    ZBarrierSetAssembler::copy_store_at(masm, decorators, type, 8, dst2, tmp1, tmp2);
    __ movq(tmp1, Address(rsp, 24));
    ZBarrierSetAssembler::copy_store_at(masm, decorators, type, 8, dst3, tmp1, tmp2);

    __ addptr(rsp, wordSize * 4);

    __ bind(done);
  }
}

void ZBarrierSetAssembler::arraycopy_prologue(MacroAssembler* masm,
                                              DecoratorSet decorators,
                                              BasicType type,
                                              Register src,
                                              Register dst,
                                              Register count) {
  if (!ZBarrierSet::barrier_needed(decorators, type)) {
    // Barrier not needed
    return;
  }

  BLOCK_COMMENT("ZBarrierSetAssembler::arraycopy_prologue {");

  load_arraycopy_masks(masm);

  BLOCK_COMMENT("} ZBarrierSetAssembler::arraycopy_prologue");
}

void ZBarrierSetAssembler::try_resolve_jobject_in_native(MacroAssembler* masm,
                                                         Register jni_env,
                                                         Register obj,
                                                         Register tmp,
                                                         Label& slowpath) {
  BLOCK_COMMENT("ZBarrierSetAssembler::try_resolve_jobject_in_native {");

  Label done, tagged, weak_tagged, uncolor;

  // Test for tag
  __ testptr(obj, JNIHandles::tag_mask);
  __ jcc(Assembler::notZero, tagged);

  // Resolve local handle
  __ movptr(obj, Address(obj, 0));
  __ jmp(done);

  __ bind(tagged);

  // Test for weak tag
  __ testptr(obj, JNIHandles::TypeTag::weak_global);
  __ jcc(Assembler::notZero, weak_tagged);

  // Resolve global handle
  __ movptr(obj, Address(obj, -JNIHandles::TypeTag::global));
  __ testptr(obj, load_bad_mask_from_jni_env(jni_env));
  __ jcc(Assembler::notZero, slowpath);
  __ jmp(uncolor);

  __ bind(weak_tagged);

  // Resolve weak handle
  __ movptr(obj, Address(obj, -JNIHandles::TypeTag::weak_global));
  __ testptr(obj, mark_bad_mask_from_jni_env(jni_env));
  __ jcc(Assembler::notZero, slowpath);

  __ bind(uncolor);

  // Uncolor
  if (obj == rcx) {
    __ movptr(tmp, obj);
    __ movptr(rcx, ExternalAddress((address)&ZPointerLoadShift));
    __ shrq(tmp);
    __ movptr(obj, tmp);
  } else {
    __ push(rcx);
    __ movptr(rcx, ExternalAddress((address)&ZPointerLoadShift));
    __ shrq(obj);
    __ pop(rcx);
  }

  __ bind(done);

  BLOCK_COMMENT("} ZBarrierSetAssembler::try_resolve_jobject_in_native");
}

#ifdef COMPILER1

#undef __
#define __ ce->masm()->

static void z_uncolor(LIR_Assembler* ce, LIR_Opr ref) {
  __ relocate(barrier_Relocation::spec(), ZBarrierRelocationFormatLoadGoodBeforeShl);
  __ shrq(ref->as_register(), barrier_Relocation::unpatched);
}

static void z_color(LIR_Assembler* ce, LIR_Opr ref) {
  __ relocate(barrier_Relocation::spec(), ZBarrierRelocationFormatLoadGoodBeforeShl);
  __ shlq(ref->as_register(), barrier_Relocation::unpatched);
  __ orq_imm32(ref->as_register(), barrier_Relocation::unpatched);
  __ relocate(barrier_Relocation::spec(), ZBarrierRelocationFormatStoreGoodAfterOr);
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
    __ Assembler::testl(ref->as_register(), barrier_Relocation::unpatched);
    __ relocate(barrier_Relocation::spec(), ZBarrierRelocationFormatMarkBadAfterTest);

    // Slow path if not zero
    __ jcc(Assembler::notZero, *stub->entry());
    // Fast path: convert to colorless
    z_uncolor(ce, ref);
  } else {
    // Convert to colorless and fast path test
    z_uncolor(ce, ref);
    __ jcc(Assembler::above, *stub->entry());
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

  // The fast-path shift destroyed the oop - need to re-read it
  __ movptr(ref, ce->as_Address(stub->ref_addr()->as_address_ptr()));

  if (stub->tmp()->is_valid()) {
    // Load address into tmp register
    ce->leal(stub->ref_addr(), stub->tmp());
    ref_addr = tmp = stub->tmp()->as_pointer_register();
  } else {
    // Address already in register
    ref_addr = stub->ref_addr()->as_address_ptr()->base()->as_pointer_register();
  }

  assert_different_registers(ref, ref_addr, noreg);

  // Save rax unless it is the result or tmp register
  if (ref != rax && tmp != rax) {
    __ push(rax);
  }

  // Setup arguments and call runtime stub
  __ subptr(rsp, 2 * BytesPerWord);
  ce->store_parameter(ref_addr, 1);
  ce->store_parameter(ref, 0);
  __ call(RuntimeAddress(stub->runtime_stub()));
  __ addptr(rsp, 2 * BytesPerWord);

  // Verify result
  __ verify_oop(rax);

  // Move result into place
  if (ref != rax) {
    __ movptr(ref, rax);
  }

  // Restore rax unless it is the result or tmp register
  if (ref != rax && tmp != rax) {
    __ pop(rax);
  }

  // Stub exit
  __ jmp(*stub->continuation());
}

void ZBarrierSetAssembler::generate_c1_store_barrier(LIR_Assembler* ce,
                                                     LIR_Address* addr,
                                                     LIR_Opr new_zaddress,
                                                     LIR_Opr new_zpointer,
                                                     ZStoreBarrierStubC1* stub) const {
  Register rnew_zaddress = new_zaddress->as_register();
  Register rnew_zpointer = new_zpointer->as_register();

  Register rbase = addr->base()->as_pointer_register();
  store_barrier_fast(ce->masm(),
                     ce->as_Address(addr),
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
  Label slow_continuation;
  store_barrier_medium(ce->masm(),
                       ce->as_Address(stub->ref_addr()->as_address_ptr()),
                       rscratch1,
                       false /* is_native */,
                       stub->is_atomic(),
                       *stub->continuation(),
                       slow,
                       slow_continuation);

  __ bind(slow);

  ce->leal(stub->ref_addr(), stub->new_zpointer());

  // Setup arguments and call runtime stub
  __ subptr(rsp, 2 * BytesPerWord);
  ce->store_parameter(stub->new_zpointer()->as_pointer_register(), 0);
  __ call(RuntimeAddress(stub->runtime_stub()));
  __ addptr(rsp, 2 * BytesPerWord);

  // Stub exit
  __ jmp(slow_continuation);
}

#undef __
#define __ sasm->

void ZBarrierSetAssembler::generate_c1_load_barrier_runtime_stub(StubAssembler* sasm,
                                                                 DecoratorSet decorators) const {
  // Enter and save registers
  __ enter();
  __ save_live_registers_no_oop_map(true /* save_fpu_registers */);

  // Setup arguments
  __ load_parameter(1, c_rarg1);
  __ load_parameter(0, c_rarg0);

  // Call VM
  __ call_VM_leaf(ZBarrierSetRuntime::load_barrier_on_oop_field_preloaded_addr(decorators), c_rarg0, c_rarg1);

  // Restore registers and return
  __ restore_live_registers_except_rax(true /* restore_fpu_registers */);
  __ leave();
  __ ret(0);
}

void ZBarrierSetAssembler::generate_c1_store_barrier_runtime_stub(StubAssembler* sasm,
                                                                  bool self_healing) const {
  // Enter and save registers
  __ enter();
  __ save_live_registers_no_oop_map(true /* save_fpu_registers */);

  // Setup arguments
  __ load_parameter(0, c_rarg0);

  // Call VM
  if (self_healing) {
    __ call_VM_leaf(ZBarrierSetRuntime::store_barrier_on_oop_field_with_healing_addr(), c_rarg0);
  } else {
    __ call_VM_leaf(ZBarrierSetRuntime::store_barrier_on_oop_field_without_healing_addr(), c_rarg0);
  }

  // Restore registers and return
  __ restore_live_registers(true /* restore_fpu_registers */);
  __ leave();
  __ ret(0);
}

#endif // COMPILER1

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
        __ movq(c_rarg0, _ref);
      }
      __ xorq(c_rarg1, c_rarg1);
    } else {
      // Self healing
      if (_ref == c_rarg0) {
        __ lea(c_rarg1, _ref_addr);
      } else if (_ref != c_rarg1) {
        __ lea(c_rarg1, _ref_addr);
        __ movq(c_rarg0, _ref);
      } else if (_ref_addr.base() != c_rarg0 && _ref_addr.index() != c_rarg0) {
        __ movq(c_rarg0, _ref);
        __ lea(c_rarg1, _ref_addr);
      } else {
        __ xchgq(c_rarg0, c_rarg1);
        if (_ref_addr.base() == c_rarg0) {
          __ lea(c_rarg1, Address(c_rarg1, _ref_addr.index(), _ref_addr.scale(), _ref_addr.disp()));
        } else if (_ref_addr.index() == c_rarg0) {
          __ lea(c_rarg1, Address(_ref_addr.base(), c_rarg1, _ref_addr.scale(), _ref_addr.disp()));
        } else {
          ShouldNotReachHere();
        }
      }
    }
  }

  ~ZSetupArguments() {
    // Transfer result
    if (_ref != rax) {
      __ movq(_ref, rax);
    }
  }
};

#undef __
#define __ masm->

void ZBarrierSetAssembler::generate_c2_load_barrier_stub(MacroAssembler* masm, ZLoadBarrierStubC2* stub) const {
  Assembler::InlineSkippedInstructionsCounter skipped_counter(masm);
  BLOCK_COMMENT("ZLoadBarrierStubC2");

  // Stub entry
  __ bind(*stub->entry());

  // The fast-path shift destroyed the oop - need to re-read it
  __ movptr(stub->ref(), stub->ref_addr());

  {
    SaveLiveRegisters save_live_registers(masm, stub);
    ZSetupArguments setup_arguments(masm, stub);
    __ call(RuntimeAddress(stub->slow_path()));
  }

  // Stub exit
  __ jmp(*stub->continuation());
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
                       stub->is_native(),
                       stub->is_atomic(),
                       *stub->continuation(),
                       slow,
                       slow_continuation);

  __ bind(slow);

  {
    SaveLiveRegisters save_live_registers(masm, stub);
    __ lea(c_rarg0, stub->ref_addr());

    if (stub->is_native()) {
      __ call(RuntimeAddress(ZBarrierSetRuntime::store_barrier_on_native_oop_field_without_healing_addr()));
    } else if (stub->is_atomic()) {
      __ call(RuntimeAddress(ZBarrierSetRuntime::store_barrier_on_oop_field_with_healing_addr()));
    } else {
      __ call(RuntimeAddress(ZBarrierSetRuntime::store_barrier_on_oop_field_without_healing_addr()));
    }
  }

  // Stub exit
  __ jmp(slow_continuation);
}

#undef __
#endif // COMPILER2

static int patch_barrier_relocation_offset(int format) {
  switch (format) {
  case ZBarrierRelocationFormatLoadGoodBeforeShl:
    return 3;

  case ZBarrierRelocationFormatStoreGoodAfterCmp:
    return -2;

  case ZBarrierRelocationFormatLoadBadAfterTest:
  case ZBarrierRelocationFormatMarkBadAfterTest:
  case ZBarrierRelocationFormatStoreBadAfterTest:
  case ZBarrierRelocationFormatStoreGoodAfterOr:
    return -4;
  case ZBarrierRelocationFormatStoreGoodAfterMov:
    return -3;

  default:
    ShouldNotReachHere();
    return 0;
  }
}

static uint16_t patch_barrier_relocation_value(int format) {
  switch (format) {
  case ZBarrierRelocationFormatLoadGoodBeforeShl:
    return (uint16_t)ZPointerLoadShift;

  case ZBarrierRelocationFormatMarkBadAfterTest:
    return (uint16_t)ZPointerMarkBadMask;

  case ZBarrierRelocationFormatLoadBadAfterTest:
    return (uint16_t)ZPointerLoadBadMask;

  case ZBarrierRelocationFormatStoreGoodAfterCmp:
  case ZBarrierRelocationFormatStoreGoodAfterOr:
  case ZBarrierRelocationFormatStoreGoodAfterMov:
    return (uint16_t)ZPointerStoreGoodMask;

  case ZBarrierRelocationFormatStoreBadAfterTest:
    return (uint16_t)ZPointerStoreBadMask;

  default:
    ShouldNotReachHere();
    return 0;
  }
}

void ZBarrierSetAssembler::patch_barrier_relocation(address addr, int format) {
  const int offset = patch_barrier_relocation_offset(format);
  const uint16_t value = patch_barrier_relocation_value(format);
  uint8_t* const patch_addr = (uint8_t*)addr + offset;
  if (format == ZBarrierRelocationFormatLoadGoodBeforeShl) {
    *patch_addr = (uint8_t)value;
  } else {
    *(uint16_t*)patch_addr = value;
  }
}

void ZBarrierSetAssembler::patch_barriers() {
  for (int i = 0; i < _load_bad_relocations.length(); ++i) {
    address addr = _load_bad_relocations.at(i);
    patch_barrier_relocation(addr, ZBarrierRelocationFormatLoadBadAfterTest);
  }
  for (int i = 0; i < _store_bad_relocations.length(); ++i) {
    address addr = _store_bad_relocations.at(i);
    patch_barrier_relocation(addr, ZBarrierRelocationFormatStoreBadAfterTest);
  }
  for (int i = 0; i < _store_good_relocations.length(); ++i) {
    address addr = _store_good_relocations.at(i);
    patch_barrier_relocation(addr, ZBarrierRelocationFormatStoreGoodAfterOr);
  }
}


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

  // Check colored null
  __ mov64(tmp1, shifted_address_mask);
  __ testptr(tmp1, obj);
  __ jcc(Assembler::zero, done);

  // Check for zpointer
  __ mov64(tmp1, shifted_base_mask);
  __ testptr(tmp1, obj);
  __ jcc(Assembler::zero, check_oop);

  // Lookup shift
  __ movq(tmp1, obj);
  __ mov64(tmp2, shifted_address_end_mask);
  __ andq(tmp1, tmp2);
  __ shrq(tmp1, ZPointerRemappedShift);
  __ andq(tmp1, (1 << ZPointerRemappedBits) - 1);
  __ lea(tmp2, ExternalAddress((address)&ZPointerLoadShiftTable));

  // Uncolor presumed zpointer
  assert(obj != rcx, "bad choice of register");
  if (rcx != tmp1 && rcx != tmp2) {
    __ push(rcx);
  }
  __ movl(rcx, Address(tmp2, tmp1, Address::times_4, 0));
  __ shrq(obj);
  if (rcx != tmp1 && rcx != tmp2) {
    __ pop(rcx);
  }

  __ jmp(check_zaddress);

  __ bind(check_oop);

  // make sure klass is 'reasonable', which is not zero.
  __ load_klass(tmp1, obj, tmp2);  // get klass
  __ testptr(tmp1, tmp1);
  __ jcc(Assembler::zero, error); // if klass is null it is broken

  __ bind(check_zaddress);
  // Check if the oop is in the right area of memory
  __ movptr(tmp1, obj);
  __ movptr(tmp2, (intptr_t) Universe::verify_oop_mask());
  __ andptr(tmp1, tmp2);
  __ movptr(tmp2, (intptr_t) Universe::verify_oop_bits());
  __ cmpptr(tmp1, tmp2);
  __ jcc(Assembler::notZero, error);

  __ bind(done);
}

#undef __
