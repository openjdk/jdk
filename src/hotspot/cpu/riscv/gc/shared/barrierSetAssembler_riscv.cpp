/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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
  // RA is live. It must be saved around calls.

  bool in_heap = (decorators & IN_HEAP) != 0;
  bool in_native = (decorators & IN_NATIVE) != 0;
  bool is_not_null = (decorators & IS_NOT_NULL) != 0;
  switch (type) {
    case T_OBJECT:  // fall through
    case T_ARRAY: {
      if (in_heap) {
        if (UseCompressedOops) {
          __ lwu(dst, src);
          if (is_not_null) {
            __ decode_heap_oop_not_null(dst);
          } else {
            __ decode_heap_oop(dst);
          }
        } else {
          __ ld(dst, src);
        }
      } else {
        assert(in_native, "why else?");
        __ ld(dst, src);
      }
      break;
    }
    case T_BOOLEAN: __ load_unsigned_byte (dst, src); break;
    case T_BYTE:    __ load_signed_byte   (dst, src); break;
    case T_CHAR:    __ load_unsigned_short(dst, src); break;
    case T_SHORT:   __ load_signed_short  (dst, src); break;
    case T_INT:     __ lw                 (dst, src); break;
    case T_LONG:    __ ld                 (dst, src); break;
    case T_ADDRESS: __ ld                 (dst, src); break;
    case T_FLOAT:   __ flw                (f10, src); break;
    case T_DOUBLE:  __ fld                (f10, src); break;
    default: Unimplemented();
  }
}

void BarrierSetAssembler::store_at(MacroAssembler* masm, DecoratorSet decorators, BasicType type,
                                   Address dst, Register val, Register tmp1, Register tmp2, Register tmp3) {
  bool in_heap = (decorators & IN_HEAP) != 0;
  bool in_native = (decorators & IN_NATIVE) != 0;
  switch (type) {
    case T_OBJECT: // fall through
    case T_ARRAY: {
      val = val == noreg ? zr : val;
      if (in_heap) {
        if (UseCompressedOops) {
          assert(!dst.uses(val), "not enough registers");
          if (val != zr) {
            __ encode_heap_oop(val);
          }
          __ sw(val, dst);
        } else {
          __ sd(val, dst);
        }
      } else {
        assert(in_native, "why else?");
        __ sd(val, dst);
      }
      break;
    }
    case T_BOOLEAN:
      __ andi(val, val, 0x1);  // boolean is true if LSB is 1
      __ sb(val, dst);
      break;
    case T_BYTE:    __ sb(val, dst); break;
    case T_CHAR:    __ sh(val, dst); break;
    case T_SHORT:   __ sh(val, dst); break;
    case T_INT:     __ sw(val, dst); break;
    case T_LONG:    __ sd(val, dst); break;
    case T_ADDRESS: __ sd(val, dst); break;
    case T_FLOAT:   __ fsw(f10,  dst); break;
    case T_DOUBLE:  __ fsd(f10,  dst); break;
    default: Unimplemented();
  }

}

void BarrierSetAssembler::copy_load_at(MacroAssembler* masm,
                                       DecoratorSet decorators,
                                       BasicType type,
                                       size_t bytes,
                                       Register dst,
                                       Address src,
                                       Register tmp) {
  if (bytes == 1) {
    __ lbu(dst, src);
  } else if (bytes == 2) {
    __ lhu(dst, src);
  } else if (bytes == 4) {
    __ lwu(dst, src);
  } else if (bytes == 8) {
    __ ld(dst, src);
  } else {
    // Not the right size
    ShouldNotReachHere();
  }
  if ((decorators & ARRAYCOPY_CHECKCAST) != 0 && UseCompressedOops) {
    __ decode_heap_oop(dst);
  }
}

void BarrierSetAssembler::copy_store_at(MacroAssembler* masm,
                                        DecoratorSet decorators,
                                        BasicType type,
                                        size_t bytes,
                                        Address dst,
                                        Register src,
                                        Register tmp1,
                                        Register tmp2,
                                        Register tmp3) {
  if ((decorators & ARRAYCOPY_CHECKCAST) != 0 && UseCompressedOops) {
    __ encode_heap_oop(src);
  }

  if (bytes == 1) {
    __ sb(src, dst);
  } else if (bytes == 2) {
    __ sh(src, dst);
  } else if (bytes == 4) {
    __ sw(src, dst);
  } else if (bytes == 8) {
    __ sd(src, dst);
  } else {
    // Not the right size
    ShouldNotReachHere();
  }
}

void BarrierSetAssembler::try_resolve_jobject_in_native(MacroAssembler* masm, Register jni_env,
                                                        Register obj, Register tmp, Label& slowpath) {
  // If mask changes we need to ensure that the inverse is still encodable as an immediate
  STATIC_ASSERT(JNIHandles::tag_mask == 3);
  __ andi(obj, obj, ~JNIHandles::tag_mask);
  __ ld(obj, Address(obj, 0));             // *obj
}

// Defines obj, preserves var_size_in_bytes, okay for tmp2 == var_size_in_bytes.
void BarrierSetAssembler::tlab_allocate(MacroAssembler* masm, Register obj,
                                        Register var_size_in_bytes,
                                        int con_size_in_bytes,
                                        Register tmp1,
                                        Register tmp2,
                                        Label& slow_case,
                                        bool is_far) {
  assert_different_registers(obj, tmp2);
  assert_different_registers(obj, var_size_in_bytes);
  Register end = tmp2;

  __ ld(obj, Address(xthread, JavaThread::tlab_top_offset()));
  if (var_size_in_bytes == noreg) {
    __ la(end, Address(obj, con_size_in_bytes));
  } else {
    __ add(end, obj, var_size_in_bytes);
  }
  __ ld(t0, Address(xthread, JavaThread::tlab_end_offset()));
  __ bgtu(end, t0, slow_case, is_far);

  // update the tlab top pointer
  __ sd(end, Address(xthread, JavaThread::tlab_top_offset()));

  // recover var_size_in_bytes if necessary
  if (var_size_in_bytes == end) {
    __ sub(var_size_in_bytes, var_size_in_bytes, obj);
  }
}

void BarrierSetAssembler::incr_allocated_bytes(MacroAssembler* masm,
                                               Register var_size_in_bytes,
                                               int con_size_in_bytes,
                                               Register tmp1) {
  assert(tmp1->is_valid(), "need temp reg");

  __ ld(tmp1, Address(xthread, in_bytes(JavaThread::allocated_bytes_offset())));
  if (var_size_in_bytes->is_valid()) {
    __ add(tmp1, tmp1, var_size_in_bytes);
  } else {
    __ add(tmp1, tmp1, con_size_in_bytes);
  }
  __ sd(tmp1, Address(xthread, in_bytes(JavaThread::allocated_bytes_offset())));
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

  Assembler::IncompressibleRegion ir(masm);  // Fixed length: see entry_barrier_offset()

  Label local_guard;
  NMethodPatchingType patching_type = nmethod_patching_type();

  if (slow_path == nullptr) {
    guard = &local_guard;

    // RISCV atomic operations require that the memory address be naturally aligned.
    __ align(4);
  }

  __ lwu(t0, *guard);

  switch (patching_type) {
    case NMethodPatchingType::conc_data_patch:
      // Subsequent loads of oops must occur after load of guard value.
      // BarrierSetNMethod::disarm sets guard with release semantics.
      __ membar(MacroAssembler::LoadLoad); // fall through to stw_instruction_and_data_patch
    case NMethodPatchingType::stw_instruction_and_data_patch:
      {
        // With STW patching, no data or instructions are updated concurrently,
        // which means there isn't really any need for any fencing for neither
        // data nor instruction modification happening concurrently. The
        // instruction patching is synchronized with global icache_flush() by
        // the write hart on riscv. So here we can do a plain conditional
        // branch with no fencing.
        Address thread_disarmed_addr(xthread, in_bytes(bs_nm->thread_disarmed_guard_value_offset()));
        __ lwu(t1, thread_disarmed_addr);
        break;
      }
    case NMethodPatchingType::conc_instruction_and_data_patch:
      {
        // If we patch code we need both a code patching and a loadload
        // fence. It's not super cheap, so we use a global epoch mechanism
        // to hide them in a slow path.
        // The high level idea of the global epoch mechanism is to detect
        // when any thread has performed the required fencing, after the
        // last nmethod was disarmed. This implies that the required
        // fencing has been performed for all preceding nmethod disarms
        // as well. Therefore, we do not need any further fencing.
        __ la(t1, ExternalAddress((address)&_patching_epoch));
        // Embed an artificial data dependency to order the guard load
        // before the epoch load.
        __ srli(ra, t0, 32);
        __ orr(t1, t1, ra);
        // Read the global epoch value.
        __ lwu(t1, t1);
        // Combine the guard value (low order) with the epoch value (high order).
        __ slli(t1, t1, 32);
        __ orr(t0, t0, t1);
        // Compare the global values with the thread-local values
        Address thread_disarmed_and_epoch_addr(xthread, in_bytes(bs_nm->thread_disarmed_guard_value_offset()));
        __ ld(t1, thread_disarmed_and_epoch_addr);
        break;
      }
    default:
      ShouldNotReachHere();
  }

  if (slow_path == nullptr) {
    Label skip_barrier;
    __ beq(t0, t1, skip_barrier);

    RuntimeAddress target(StubRoutines::method_entry_barrier());
    __ relocate(target.rspec(), [&] {
      int32_t offset;
      __ la_patchable(t0, target, offset);
      __ jalr(ra, t0, offset);
    });

    __ j(skip_barrier);

    __ bind(local_guard);

    MacroAssembler::assert_alignment(__ pc());
    __ emit_int32(0); // nmethod guard value. Skipped over in common case.
    __ bind(skip_barrier);
  } else {
    __ beq(t0, t1, *continuation);
    __ j(*slow_path);
    __ bind(*continuation);
  }
}

void BarrierSetAssembler::c2i_entry_barrier(MacroAssembler* masm) {
  BarrierSetNMethod* bs = BarrierSet::barrier_set()->barrier_set_nmethod();
  if (bs == nullptr) {
    return;
  }

  Label bad_call;
  __ beqz(xmethod, bad_call);

  // Pointer chase to the method holder to find out if the method is concurrently unloading.
  Label method_live;
  __ load_method_holder_cld(t0, xmethod);

  // Is it a strong CLD?
  __ lwu(t1, Address(t0, ClassLoaderData::keep_alive_offset()));
  __ bnez(t1, method_live);

  // Is it a weak but alive CLD?
  __ push_reg(RegSet::of(x28), sp);

  __ ld(x28, Address(t0, ClassLoaderData::holder_offset()));

  __ resolve_weak_handle(x28, t0, t1);
  __ mv(t0, x28);

  __ pop_reg(RegSet::of(x28), sp);

  __ bnez(t0, method_live);

  __ bind(bad_call);

  __ far_jump(RuntimeAddress(SharedRuntime::get_handle_wrong_method_stub()));
  __ bind(method_live);
}

void BarrierSetAssembler::check_oop(MacroAssembler* masm, Register obj, Register tmp1, Register tmp2, Label& error) {
  // Check if the oop is in the right area of memory
  __ mv(tmp2, (intptr_t) Universe::verify_oop_mask());
  __ andr(tmp1, obj, tmp2);
  __ mv(tmp2, (intptr_t) Universe::verify_oop_bits());

  // Compare tmp1 and tmp2.
  __ bne(tmp1, tmp2, error);

  // Make sure klass is 'reasonable', which is not zero.
  __ load_klass(obj, obj, tmp1); // get klass
  __ beqz(obj, error);           // if klass is null it is broken
}
