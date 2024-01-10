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
 *
 */

#include "precompiled.hpp"
#include "classfile/classLoaderData.hpp"
#include "gc/shared/barrierSet.hpp"
#include "gc/shared/barrierSetAssembler.hpp"
#include "gc/shared/barrierSetNMethod.hpp"
#include "gc/shared/collectedHeap.hpp"
#include "interpreter/interp_masm.hpp"
#include "memory/universe.hpp"
#include "runtime/javaThread.hpp"
#include "runtime/jniHandles.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/stubRoutines.hpp"


#define __ masm->

void BarrierSetAssembler::load_at(MacroAssembler* masm, DecoratorSet decorators, BasicType type,
                                  Register dst, Address src, Register tmp1, Register tmp2) {

  // LR is live.  It must be saved around calls.

  bool in_heap = (decorators & IN_HEAP) != 0;
  bool in_native = (decorators & IN_NATIVE) != 0;
  bool is_not_null = (decorators & IS_NOT_NULL) != 0;
  switch (type) {
  case T_OBJECT:
  case T_ARRAY: {
    if (in_heap) {
      if (UseCompressedOops) {
        __ ldrw(dst, src);
        if (is_not_null) {
          __ decode_heap_oop_not_null(dst);
        } else {
          __ decode_heap_oop(dst);
        }
      } else {
        __ ldr(dst, src);
      }
    } else {
      assert(in_native, "why else?");
      __ ldr(dst, src);
    }
    break;
  }
  case T_BOOLEAN: __ load_unsigned_byte (dst, src); break;
  case T_BYTE:    __ load_signed_byte   (dst, src); break;
  case T_CHAR:    __ load_unsigned_short(dst, src); break;
  case T_SHORT:   __ load_signed_short  (dst, src); break;
  case T_INT:     __ ldrw               (dst, src); break;
  case T_LONG:    __ ldr                (dst, src); break;
  case T_ADDRESS: __ ldr                (dst, src); break;
  case T_FLOAT:   __ ldrs               (v0, src);  break;
  case T_DOUBLE:  __ ldrd               (v0, src);  break;
  default: Unimplemented();
  }
}

void BarrierSetAssembler::store_at(MacroAssembler* masm, DecoratorSet decorators, BasicType type,
                                   Address dst, Register val, Register tmp1, Register tmp2, Register tmp3) {
  bool in_heap = (decorators & IN_HEAP) != 0;
  bool in_native = (decorators & IN_NATIVE) != 0;
  switch (type) {
  case T_OBJECT:
  case T_ARRAY: {
    val = val == noreg ? zr : val;
    if (in_heap) {
      if (UseCompressedOops) {
        assert(!dst.uses(val), "not enough registers");
        if (val != zr) {
          __ encode_heap_oop(val);
        }
        __ strw(val, dst);
      } else {
        __ str(val, dst);
      }
    } else {
      assert(in_native, "why else?");
      __ str(val, dst);
    }
    break;
  }
  case T_BOOLEAN:
    __ andw(val, val, 0x1);  // boolean is true if LSB is 1
    __ strb(val, dst);
    break;
  case T_BYTE:    __ strb(val, dst); break;
  case T_CHAR:    __ strh(val, dst); break;
  case T_SHORT:   __ strh(val, dst); break;
  case T_INT:     __ strw(val, dst); break;
  case T_LONG:    __ str (val, dst); break;
  case T_ADDRESS: __ str (val, dst); break;
  case T_FLOAT:   __ strs(v0,  dst); break;
  case T_DOUBLE:  __ strd(v0,  dst); break;
  default: Unimplemented();
  }
}

void BarrierSetAssembler::copy_load_at(MacroAssembler* masm,
                                       DecoratorSet decorators,
                                       BasicType type,
                                       size_t bytes,
                                       Register dst1,
                                       Register dst2,
                                       Address src,
                                       Register tmp) {
  if (bytes == 1) {
    assert(dst2 == noreg, "invariant");
    __ ldrb(dst1, src);
  } else if (bytes == 2) {
    assert(dst2 == noreg, "invariant");
    __ ldrh(dst1, src);
  } else if (bytes == 4) {
    assert(dst2 == noreg, "invariant");
    __ ldrw(dst1, src);
  } else if (bytes == 8) {
    assert(dst2 == noreg, "invariant");
    __ ldr(dst1, src);
  } else if (bytes == 16) {
    assert(dst2 != noreg, "invariant");
    assert(dst2 != dst1, "invariant");
    __ ldp(dst1, dst2, src);
  } else {
    // Not the right size
    ShouldNotReachHere();
  }
  if ((decorators & ARRAYCOPY_CHECKCAST) != 0 && UseCompressedOops) {
    __ decode_heap_oop(dst1);
  }
}

void BarrierSetAssembler::copy_store_at(MacroAssembler* masm,
                                        DecoratorSet decorators,
                                        BasicType type,
                                        size_t bytes,
                                        Address dst,
                                        Register src1,
                                        Register src2,
                                        Register tmp1,
                                        Register tmp2,
                                        Register tmp3) {
  if ((decorators & ARRAYCOPY_CHECKCAST) != 0 && UseCompressedOops) {
    __ encode_heap_oop(src1);
  }
  if (bytes == 1) {
    assert(src2 == noreg, "invariant");
    __ strb(src1, dst);
  } else if (bytes == 2) {
    assert(src2 == noreg, "invariant");
    __ strh(src1, dst);
  } else if (bytes == 4) {
    assert(src2 == noreg, "invariant");
    __ strw(src1, dst);
  } else if (bytes == 8) {
    assert(src2 == noreg, "invariant");
    __ str(src1, dst);
  } else if (bytes == 16) {
    assert(src2 != noreg, "invariant");
    assert(src2 != src1, "invariant");
    __ stp(src1, src2, dst);
  } else {
    // Not the right size
    ShouldNotReachHere();
  }
}

void BarrierSetAssembler::copy_load_at(MacroAssembler* masm,
                                       DecoratorSet decorators,
                                       BasicType type,
                                       size_t bytes,
                                       FloatRegister dst1,
                                       FloatRegister dst2,
                                       Address src,
                                       Register tmp1,
                                       Register tmp2,
                                       FloatRegister vec_tmp) {
  if (bytes == 32) {
    __ ldpq(dst1, dst2, src);
  } else {
    ShouldNotReachHere();
  }
}

void BarrierSetAssembler::copy_store_at(MacroAssembler* masm,
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
  if (bytes == 32) {
    __ stpq(src1, src2, dst);
  } else {
    ShouldNotReachHere();
  }
}

void BarrierSetAssembler::try_resolve_jobject_in_native(MacroAssembler* masm, Register jni_env,
                                                        Register obj, Register tmp, Label& slowpath) {
  // If mask changes we need to ensure that the inverse is still encodable as an immediate
  STATIC_ASSERT(JNIHandles::tag_mask == 0b11);
  __ andr(obj, obj, ~JNIHandles::tag_mask);
  __ ldr(obj, Address(obj, 0));             // *obj
}

// Defines obj, preserves var_size_in_bytes, okay for t2 == var_size_in_bytes.
void BarrierSetAssembler::tlab_allocate(MacroAssembler* masm, Register obj,
                                        Register var_size_in_bytes,
                                        int con_size_in_bytes,
                                        Register t1,
                                        Register t2,
                                        Label& slow_case) {
  assert_different_registers(obj, t2);
  assert_different_registers(obj, var_size_in_bytes);
  Register end = t2;

  // verify_tlab();

  __ ldr(obj, Address(rthread, JavaThread::tlab_top_offset()));
  if (var_size_in_bytes == noreg) {
    __ lea(end, Address(obj, con_size_in_bytes));
  } else {
    __ lea(end, Address(obj, var_size_in_bytes));
  }
  __ ldr(rscratch1, Address(rthread, JavaThread::tlab_end_offset()));
  __ cmp(end, rscratch1);
  __ br(Assembler::HI, slow_case);

  // update the tlab top pointer
  __ str(end, Address(rthread, JavaThread::tlab_top_offset()));

  // recover var_size_in_bytes if necessary
  if (var_size_in_bytes == end) {
    __ sub(var_size_in_bytes, var_size_in_bytes, obj);
  }
  // verify_tlab();
}

void BarrierSetAssembler::incr_allocated_bytes(MacroAssembler* masm,
                                               Register var_size_in_bytes,
                                               int con_size_in_bytes,
                                               Register t1) {
  assert(t1->is_valid(), "need temp reg");

  __ ldr(t1, Address(rthread, in_bytes(JavaThread::allocated_bytes_offset())));
  if (var_size_in_bytes->is_valid()) {
    __ add(t1, t1, var_size_in_bytes);
  } else {
    __ add(t1, t1, con_size_in_bytes);
  }
  __ str(t1, Address(rthread, in_bytes(JavaThread::allocated_bytes_offset())));
}

static volatile uint32_t _patching_epoch = 0;

address BarrierSetAssembler::patching_epoch_addr() {
  return (address)&_patching_epoch;
}

void BarrierSetAssembler::increment_patching_epoch() {
  Atomic::inc(&_patching_epoch);
}

void BarrierSetAssembler::clear_patching_epoch() {
  _patching_epoch = 0;
}

void BarrierSetAssembler::nmethod_entry_barrier(MacroAssembler* masm, Label* slow_path, Label* continuation, Label* guard) {
  BarrierSetNMethod* bs_nm = BarrierSet::barrier_set()->barrier_set_nmethod();

  if (bs_nm == nullptr) {
    return;
  }

  Label local_guard;
  Label skip_barrier;
  NMethodPatchingType patching_type = nmethod_patching_type();

  if (slow_path == nullptr) {
    guard = &local_guard;
  }

  // If the slow path is out of line in a stub, we flip the condition
  Assembler::Condition condition = slow_path == nullptr ? Assembler::EQ : Assembler::NE;
  Label& barrier_target = slow_path == nullptr ? skip_barrier : *slow_path;

  __ ldrw(rscratch1, *guard);

  if (patching_type == NMethodPatchingType::stw_instruction_and_data_patch) {
    // With STW patching, no data or instructions are updated concurrently,
    // which means there isn't really any need for any fencing for neither
    // data nor instruction modifications happening concurrently. The
    // instruction patching is handled with isb fences on the way back
    // from the safepoint to Java. So here we can do a plain conditional
    // branch with no fencing.
    Address thread_disarmed_addr(rthread, in_bytes(bs_nm->thread_disarmed_guard_value_offset()));
    __ ldrw(rscratch2, thread_disarmed_addr);
    __ cmp(rscratch1, rscratch2);
  } else if (patching_type == NMethodPatchingType::conc_instruction_and_data_patch) {
    // If we patch code we need both a code patching and a loadload
    // fence. It's not super cheap, so we use a global epoch mechanism
    // to hide them in a slow path.
    // The high level idea of the global epoch mechanism is to detect
    // when any thread has performed the required fencing, after the
    // last nmethod was disarmed. This implies that the required
    // fencing has been performed for all preceding nmethod disarms
    // as well. Therefore, we do not need any further fencing.
    __ lea(rscratch2, ExternalAddress((address)&_patching_epoch));
    // Embed an artificial data dependency to order the guard load
    // before the epoch load.
    __ orr(rscratch2, rscratch2, rscratch1, Assembler::LSR, 32);
    // Read the global epoch value.
    __ ldrw(rscratch2, rscratch2);
    // Combine the guard value (low order) with the epoch value (high order).
    __ orr(rscratch1, rscratch1, rscratch2, Assembler::LSL, 32);
    // Compare the global values with the thread-local values.
    Address thread_disarmed_and_epoch_addr(rthread, in_bytes(bs_nm->thread_disarmed_guard_value_offset()));
    __ ldr(rscratch2, thread_disarmed_and_epoch_addr);
    __ cmp(rscratch1, rscratch2);
  } else {
    assert(patching_type == NMethodPatchingType::conc_data_patch, "must be");
    // Subsequent loads of oops must occur after load of guard value.
    // BarrierSetNMethod::disarm sets guard with release semantics.
    __ membar(__ LoadLoad);
    Address thread_disarmed_addr(rthread, in_bytes(bs_nm->thread_disarmed_guard_value_offset()));
    __ ldrw(rscratch2, thread_disarmed_addr);
    __ cmpw(rscratch1, rscratch2);
  }
  __ br(condition, barrier_target);

  if (slow_path == nullptr) {
    __ lea(rscratch1, RuntimeAddress(StubRoutines::method_entry_barrier()));
    __ blr(rscratch1);
    __ b(skip_barrier);

    __ bind(local_guard);

    __ emit_int32(0);   // nmethod guard value. Skipped over in common case.
  } else {
    __ bind(*continuation);
  }

  __ bind(skip_barrier);
}

void BarrierSetAssembler::c2i_entry_barrier(MacroAssembler* masm) {
  BarrierSetNMethod* bs = BarrierSet::barrier_set()->barrier_set_nmethod();
  if (bs == nullptr) {
    return;
  }

  Label bad_call;
  __ cbz(rmethod, bad_call);

  // Pointer chase to the method holder to find out if the method is concurrently unloading.
  Label method_live;
  __ load_method_holder_cld(rscratch1, rmethod);

  // Is it a strong CLD?
  __ ldrw(rscratch2, Address(rscratch1, ClassLoaderData::keep_alive_offset()));
  __ cbnz(rscratch2, method_live);

  // Is it a weak but alive CLD?
  __ push(RegSet::of(r10), sp);
  __ ldr(r10, Address(rscratch1, ClassLoaderData::holder_offset()));

  __ resolve_weak_handle(r10, rscratch1, rscratch2);
  __ mov(rscratch1, r10);
  __ pop(RegSet::of(r10), sp);
  __ cbnz(rscratch1, method_live);

  __ bind(bad_call);

  __ far_jump(RuntimeAddress(SharedRuntime::get_handle_wrong_method_stub()));
  __ bind(method_live);
}

void BarrierSetAssembler::check_oop(MacroAssembler* masm, Register obj, Register tmp1, Register tmp2, Label& error) {
  // Check if the oop is in the right area of memory
  __ mov(tmp2, (intptr_t) Universe::verify_oop_mask());
  __ andr(tmp1, obj, tmp2);
  __ mov(tmp2, (intptr_t) Universe::verify_oop_bits());

  // Compare tmp1 and tmp2.  We don't use a compare
  // instruction here because the flags register is live.
  __ eor(tmp1, tmp1, tmp2);
  __ cbnz(tmp1, error);

  // make sure klass is 'reasonable', which is not zero.
  __ load_klass(obj, obj); // get klass
  __ cbz(obj, error);      // if klass is null it is broken
}
