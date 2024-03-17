/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "opto/c2_MacroAssembler.hpp"
#include "opto/c2_CodeStubs.hpp"
#include "runtime/objectMonitor.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/stubRoutines.hpp"

#define __ masm.

int C2SafepointPollStub::max_size() const {
  return 33;
}

void C2SafepointPollStub::emit(C2_MacroAssembler& masm) {
  assert(SharedRuntime::polling_page_return_handler_blob() != nullptr,
         "polling page return stub not created yet");
  address stub = SharedRuntime::polling_page_return_handler_blob()->entry_point();

  RuntimeAddress callback_addr(stub);

  __ bind(entry());
  InternalAddress safepoint_pc(masm.pc() - masm.offset() + _safepoint_offset);
#ifdef _LP64
  __ lea(rscratch1, safepoint_pc);
  __ movptr(Address(r15_thread, JavaThread::saved_exception_pc_offset()), rscratch1);
#else
  const Register tmp1 = rcx;
  const Register tmp2 = rdx;
  __ push(tmp1);
  __ push(tmp2);

  __ lea(tmp1, safepoint_pc);
  __ get_thread(tmp2);
  __ movptr(Address(tmp2, JavaThread::saved_exception_pc_offset()), tmp1);

  __ pop(tmp2);
  __ pop(tmp1);
#endif
  __ jump(callback_addr);
}

int C2EntryBarrierStub::max_size() const {
  return 10;
}

void C2EntryBarrierStub::emit(C2_MacroAssembler& masm) {
  __ bind(entry());
  __ call(RuntimeAddress(StubRoutines::method_entry_barrier()));
  __ jmp(continuation(), false /* maybe_short */);
}

int C2FastUnlockLightweightStub::max_size() const {
  return 128;
}

void C2FastUnlockLightweightStub::emit(C2_MacroAssembler& masm) {
  assert(_t == rax, "must be");

  Label restore_held_monitor_count_and_slow_path;

  { // Restore lock-stack and handle the unlock in runtime.

    __ bind(_push_and_slow_path);
#ifdef ASSERT
    // The obj was only cleared in debug.
    __ movl(_t, Address(_thread, JavaThread::lock_stack_top_offset()));
    __ movptr(Address(_thread, _t), _obj);
#endif
    __ addl(Address(_thread, JavaThread::lock_stack_top_offset()), oopSize);
  }

  { // Restore held monitor count and slow path.

    __ bind(restore_held_monitor_count_and_slow_path);
    // Restore held monitor count.
    __ increment(Address(_thread, JavaThread::held_monitor_count_offset()));
    // increment will always result in ZF = 0 (no overflows).
    __ jmp(slow_path_continuation());
  }

  const Register monitor = _mark;

  Label restore_contentions_slow_path;
  {
    __ bind (restore_contentions_slow_path);
    __ lock(); __ decrementl(Address(monitor, OM_OFFSET_NO_MONITOR_VALUE_TAG(contentions)));
    __ jmpb(restore_held_monitor_count_and_slow_path);
  }
  Label fix_zf_and_unlock;
  Label restore_contentions_fast_path;
  {
    __ bind (restore_contentions_fast_path);
    __ lock(); __ decrementl(Address(monitor, OM_OFFSET_NO_MONITOR_VALUE_TAG(contentions)));
    __ jmpb(fix_zf_and_unlock);
  }

  // The cancellation requires that we first increment and then decrement the contentions.
  // Instead we can simply go to the slow path without changing contentions.
  Label& canceled_deflation_slow_path = restore_held_monitor_count_and_slow_path;

  { // Handle monitor medium path.

    __ bind(_inflated_medium_path);


    __ xorptr(rax, rax);
    __ lock(); __ cmpxchgptr(_thread, Address(monitor, OM_OFFSET_NO_MONITOR_VALUE_TAG(owner)));
    __ jccb  (Assembler::equal, restore_held_monitor_count_and_slow_path);

    __ cmpptr(rax, reinterpret_cast<intptr_t>(DEFLATER_MARKER));
    __ jccb(Assembler::notEqual, fix_zf_and_unlock);

    __ lock(); __ incrementl(Address(monitor, OM_OFFSET_NO_MONITOR_VALUE_TAG(contentions)));

    __ cmpl(Address(monitor, OM_OFFSET_NO_MONITOR_VALUE_TAG(contentions)), 0);
    __ jccb(Assembler::less, restore_contentions_fast_path);

    // rax contains DEFLATER_MARKER here
    __ lock(); __ cmpxchgptr(_thread, Address(monitor, OM_OFFSET_NO_MONITOR_VALUE_TAG(owner)));
    __ jccb  (Assembler::equal, canceled_deflation_slow_path);

    __ xorptr(rax, rax);
    __ lock(); __ cmpxchgptr(_thread, Address(monitor, OM_OFFSET_NO_MONITOR_VALUE_TAG(owner)));
    __ jccb  (Assembler::equal, restore_contentions_slow_path);

    __ lock(); __ decrementl(Address(monitor, OM_OFFSET_NO_MONITOR_VALUE_TAG(contentions)));

    __ bind(fix_zf_and_unlock);
    __ xorl(rax, rax);
    __ jmp(unlocked_continuation());
  }
}

#undef __
