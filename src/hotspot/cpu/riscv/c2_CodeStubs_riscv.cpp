/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "opto/c2_CodeStubs.hpp"
#include "opto/c2_MacroAssembler.hpp"
#include "runtime/objectMonitor.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/stubRoutines.hpp"

#define __ masm.

int C2SafepointPollStub::max_size() const {
  return 13 * 4;
}

void C2SafepointPollStub::emit(C2_MacroAssembler& masm) {
  assert(SharedRuntime::polling_page_return_handler_blob() != nullptr,
         "polling page return stub not created yet");
  address stub = SharedRuntime::polling_page_return_handler_blob()->entry_point();
  RuntimeAddress callback_addr(stub);

  __ bind(entry());
  InternalAddress safepoint_pc(__ pc() - __ offset() + _safepoint_offset);
  __ relocate(safepoint_pc.rspec(), [&] {
    int32_t offset;
    __ la(t0, safepoint_pc.target(), offset);
    __ addi(t0, t0, offset);
  });
  __ sd(t0, Address(xthread, JavaThread::saved_exception_pc_offset()));
  __ far_jump(callback_addr);
}

int C2EntryBarrierStub::max_size() const {
  // 4 bytes for alignment
  return 8 * 4 + 4;
}

void C2EntryBarrierStub::emit(C2_MacroAssembler& masm) {
  __ bind(entry());
  __ rt_call(StubRoutines::method_entry_barrier());

  __ j(continuation());

  // make guard value 4-byte aligned so that it can be accessed by atomic instructions on RISC-V
  __ align(4);
  __ bind(guard());
  __ relocate(entry_guard_Relocation::spec());
  __ emit_int32(0);  // nmethod guard value
}

int C2HandleAnonOMOwnerStub::max_size() const {
  // Max size of stub has been determined by testing with 0 without using RISC-V compressed
  // instruction-set extension, in which case C2CodeStubList::emit() will throw an assertion
  // and report the actual size that is needed.
  return 20 DEBUG_ONLY(+8);
}

void C2HandleAnonOMOwnerStub::emit(C2_MacroAssembler& masm) {
  __ bind(entry());
  Register mon = monitor();
  Register t = tmp();
  assert(t != noreg, "need tmp register");

  // Fix owner to be the current thread.
  __ sd(xthread, Address(mon, ObjectMonitor::owner_offset()));

  // Pop owner object from lock-stack.
  __ lwu(t, Address(xthread, JavaThread::lock_stack_top_offset()));
  __ subw(t, t, oopSize);
#ifdef ASSERT
  __ add(t0, xthread, t);
  __ sd(zr, Address(t0, 0));
#endif
  __ sw(t, Address(xthread, JavaThread::lock_stack_top_offset()));

  __ j(continuation());
}

int C2FastUnlockLightweightStub::max_size() const {
  // FIXME
  return 256;
}

void C2FastUnlockLightweightStub::emit(C2_MacroAssembler& masm) {

  const Register monitor = _mark_or_monitor;
  const Register contentions_addr = _t;

  Label restore_contentions_slow_path;
  {
    __ bind (restore_contentions_slow_path);
    __ atomic_addw(noreg, -1, contentions_addr);
    __ j(slow_path_continuation());
  }
  Label restore_contentions_fast_path;
  {
    __ bind (restore_contentions_fast_path);
    __ atomic_addw(noreg, -1, contentions_addr);
    __ j(unlocked_continuation());
  }

  // The cancellation requires that we first increment and then decrement the contentions.
  // Instead we can simply go to the slow path without changing contentions.
  Label& canceled_deflation_slow_path = slow_path_continuation();

  { // Handle monitor medium path.

    __ bind(_inflated_medium_path);

    __ cmpxchg(/*addr*/ _owner_addr, /*expected*/ zr, /*new*/ xthread, Assembler::int64,
               /*acquire*/ Assembler::aq, /*release*/ Assembler::relaxed, /*result*/ t2);
    __ beqz(t2, slow_path_continuation());

    __ li(t1, reinterpret_cast<intptr_t>(DEFLATER_MARKER));
    __ bne(t1, t2, slow_path_continuation());

    __ la(contentions_addr, Address(monitor, ObjectMonitor::contentions_offset()));

    __ atomic_addw(t1, 1, contentions_addr);

    __ bltz(t1, restore_contentions_fast_path);

    // t1 contains DEFLATER_MARKER
    __ cmpxchg(/*addr*/ _owner_addr, /*expected*/ t1, /*new*/ xthread, Assembler::int64,
               /*acquire*/ Assembler::aq, /*release*/ Assembler::relaxed, /*result*/ t2);
    __ beq(t1, t2,  canceled_deflation_slow_path);

    __ cmpxchg(/*addr*/ _owner_addr, /*expected*/ zr, /*new*/ xthread, Assembler::int64,
               /*acquire*/ Assembler::aq, /*release*/ Assembler::relaxed, /*result*/ t2);
    __ beqz(t2, restore_contentions_slow_path);

    __ atomic_addw(zr, -1, contentions_addr);

    __ j(unlocked_continuation());
  }
}

#undef __
