/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2022, Huawei Technologies Co., Ltd. All rights reserved.
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
#include "runtime/jniHandles.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/stubRoutines.hpp"
#include "runtime/thread.hpp"

#define __ masm->

void BarrierSetAssembler::load_at(MacroAssembler* masm, DecoratorSet decorators, BasicType type,
                                  Register dst, Address src, Register tmp1, Register tmp_thread) {
  assert_cond(masm != NULL);

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
                                   Address dst, Register val, Register tmp1, Register tmp2) {
  assert_cond(masm != NULL);
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

void BarrierSetAssembler::try_resolve_jobject_in_native(MacroAssembler* masm, Register jni_env,
                                                        Register obj, Register tmp, Label& slowpath) {
  assert_cond(masm != NULL);
  // If mask changes we need to ensure that the inverse is still encodable as an immediate
  STATIC_ASSERT(JNIHandles::weak_tag_mask == 1);
  __ andi(obj, obj, ~JNIHandles::weak_tag_mask);
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
  assert_cond(masm != NULL);
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

// Defines obj, preserves var_size_in_bytes
void BarrierSetAssembler::eden_allocate(MacroAssembler* masm, Register obj,
                                        Register var_size_in_bytes,
                                        int con_size_in_bytes,
                                        Register tmp1,
                                        Label& slow_case,
                                        bool is_far) {
  assert_cond(masm != NULL);
  assert_different_registers(obj, var_size_in_bytes, tmp1);
  if (!Universe::heap()->supports_inline_contig_alloc()) {
    __ j(slow_case);
  } else {
    Register end = tmp1;
    Label retry;
    __ bind(retry);

    // Get the current end of the heap
    ExternalAddress address_end((address) Universe::heap()->end_addr());
    {
      int32_t offset;
      __ la_patchable(t1, address_end, offset);
      __ ld(t1, Address(t1, offset));
    }

    // Get the current top of the heap
    ExternalAddress address_top((address) Universe::heap()->top_addr());
    {
      int32_t offset;
      __ la_patchable(t0, address_top, offset);
      __ addi(t0, t0, offset);
      __ lr_d(obj, t0, Assembler::aqrl);
    }

    // Adjust it my the size of our new object
    if (var_size_in_bytes == noreg) {
      __ la(end, Address(obj, con_size_in_bytes));
    } else {
      __ add(end, obj, var_size_in_bytes);
    }

    // if end < obj then we wrapped around high memory
    __ bltu(end, obj, slow_case, is_far);

    __ bgtu(end, t1, slow_case, is_far);

    // If heap_top hasn't been changed by some other thread, update it.
    __ sc_d(t1, end, t0, Assembler::rl);
    __ bnez(t1, retry);

    incr_allocated_bytes(masm, var_size_in_bytes, con_size_in_bytes, tmp1);
  }
}

void BarrierSetAssembler::incr_allocated_bytes(MacroAssembler* masm,
                                               Register var_size_in_bytes,
                                               int con_size_in_bytes,
                                               Register tmp1) {
  assert_cond(masm != NULL);
  assert(tmp1->is_valid(), "need temp reg");

  __ ld(tmp1, Address(xthread, in_bytes(JavaThread::allocated_bytes_offset())));
  if (var_size_in_bytes->is_valid()) {
    __ add(tmp1, tmp1, var_size_in_bytes);
  } else {
    __ add(tmp1, tmp1, con_size_in_bytes);
  }
  __ sd(tmp1, Address(xthread, in_bytes(JavaThread::allocated_bytes_offset())));
}

void BarrierSetAssembler::nmethod_entry_barrier(MacroAssembler* masm) {
  BarrierSetNMethod* bs_nm = BarrierSet::barrier_set()->barrier_set_nmethod();

  if (bs_nm == NULL) {
    return;
  }

  // RISCV atomic operations require that the memory address be naturally aligned.
  __ align(4);

  Label skip, guard;
  Address thread_disarmed_addr(xthread, in_bytes(bs_nm->thread_disarmed_offset()));

  __ lwu(t0, guard);

  // Subsequent loads of oops must occur after load of guard value.
  // BarrierSetNMethod::disarm sets guard with release semantics.
  __ membar(MacroAssembler::LoadLoad);
  __ lwu(t1, thread_disarmed_addr);
  __ beq(t0, t1, skip);

  int32_t offset = 0;
  __ movptr_with_offset(t0, StubRoutines::riscv::method_entry_barrier(), offset);
  __ jalr(ra, t0, offset);
  __ j(skip);

  __ bind(guard);

  assert(__ offset() % 4 == 0, "bad alignment");
  __ emit_int32(0); // nmethod guard value. Skipped over in common case.

  __ bind(skip);
}

void BarrierSetAssembler::c2i_entry_barrier(MacroAssembler* masm) {
  BarrierSetNMethod* bs = BarrierSet::barrier_set()->barrier_set_nmethod();
  if (bs == NULL) {
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
  __ push_reg(RegSet::of(x28, x29), sp);

  __ ld(x28, Address(t0, ClassLoaderData::holder_offset()));

  // Uses x28 & x29, so we must pass new temporaries.
  __ resolve_weak_handle(x28, x29);
  __ mv(t0, x28);

  __ pop_reg(RegSet::of(x28, x29), sp);

  __ bnez(t0, method_live);

  __ bind(bad_call);

  __ far_jump(RuntimeAddress(SharedRuntime::get_handle_wrong_method_stub()));
  __ bind(method_live);
}
