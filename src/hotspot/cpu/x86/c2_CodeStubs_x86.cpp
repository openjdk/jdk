/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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

  { // Restore held monitor and slow path.

    __ bind(restore_held_monitor_count_and_slow_path);
    // Restore held monitor count.
    __ increment(Address(_thread, JavaThread::held_monitor_count_offset()));
    // increment will always result in ZF = 0 (no overflows).
    // continuation is the slow_path.
    __ jmp(continuation());
  }

  { // Handle monitor medium path.

    __ bind(_check_successor);

    Label fix_zf_and_unlocked;
    const Register monitor = _mark;

#ifndef _LP64
    // The owner may be anonymous, see comment in x86_64 section.
    __ movptr(Address(monitor, OM_OFFSET_NO_MONITOR_VALUE_TAG(owner)), _thread);
    __ jmpb(restore_held_monitor_count_and_slow_path);
#else // _LP64
    // The owner may be anonymous and we removed the last obj entry in
    // the lock-stack. This loses the information about the owner.
    // Write the thread to the owner field so the runtime knows the owner.
    __ movptr(Address(monitor, OM_OFFSET_NO_MONITOR_VALUE_TAG(owner)), _thread);

    // successor null check.
    __ cmpptr(Address(monitor, OM_OFFSET_NO_MONITOR_VALUE_TAG(succ)), NULL_WORD);
    __ jccb(Assembler::equal, restore_held_monitor_count_and_slow_path);

    // Release lock.
    __ movptr(Address(monitor, OM_OFFSET_NO_MONITOR_VALUE_TAG(owner)), NULL_WORD);

    // Fence.
    __ lock(); __ addl(Address(rsp, 0), 0);

    // Recheck successor.
    __ cmpptr(Address(monitor, OM_OFFSET_NO_MONITOR_VALUE_TAG(succ)), NULL_WORD);
    // Seen a successor after the release -> fence we have handed of the monitor
    __ jccb(Assembler::notEqual, fix_zf_and_unlocked);

    // Try to relock, if it fail the monitor has been handed over
    // TODO: Caveat, this may fail due to deflation, which does
    //       not handle the monitor handoff. Currently only works
    //       due to the responsible thread.
    __ xorptr(rax, rax);
    __ lock(); __ cmpxchgptr(_thread, Address(monitor, OM_OFFSET_NO_MONITOR_VALUE_TAG(owner)));
    __ jccb  (Assembler::equal, restore_held_monitor_count_and_slow_path);
#endif

    __ bind(fix_zf_and_unlocked);
    __ xorl(rax, rax);
    __ jmp(unlocked());
  }
}

#ifdef _LP64
int C2HandleAnonOMOwnerStub::max_size() const {
  // Max size of stub has been determined by testing with 0, in which case
  // C2CodeStubList::emit() will throw an assertion and report the actual size that
  // is needed.
  return DEBUG_ONLY(36) NOT_DEBUG(21);
}

void C2HandleAnonOMOwnerStub::emit(C2_MacroAssembler& masm) {
  __ bind(entry());
  Register mon = monitor();
  Register t = tmp();
  __ movptr(Address(mon, OM_OFFSET_NO_MONITOR_VALUE_TAG(owner)), r15_thread);
  __ subl(Address(r15_thread, JavaThread::lock_stack_top_offset()), oopSize);
#ifdef ASSERT
  __ movl(t, Address(r15_thread, JavaThread::lock_stack_top_offset()));
  __ movptr(Address(r15_thread, t), 0);
#endif
  __ jmp(continuation());
}
#endif

#undef __
