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
  return 20;
}

void C2SafepointPollStub::emit(C2_MacroAssembler& masm) {
  assert(SharedRuntime::polling_page_return_handler_blob() != nullptr,
         "polling page return stub not created yet");
  address stub = SharedRuntime::polling_page_return_handler_blob()->entry_point();

  RuntimeAddress callback_addr(stub);

  __ bind(entry());
  InternalAddress safepoint_pc(masm.pc() - masm.offset() + _safepoint_offset);
  __ adr(rscratch1, safepoint_pc);
  __ str(rscratch1, Address(rthread, JavaThread::saved_exception_pc_offset()));
  __ far_jump(callback_addr);
}

int C2EntryBarrierStub::max_size() const {
  return 24;
}

void C2EntryBarrierStub::emit(C2_MacroAssembler& masm) {
  __ bind(entry());
  __ lea(rscratch1, RuntimeAddress(StubRoutines::method_entry_barrier()));
  __ blr(rscratch1);
  __ b(continuation());

  __ bind(guard());
  __ relocate(entry_guard_Relocation::spec());
  __ emit_int32(0);   // nmethod guard value
}

int C2HandleAnonOMOwnerStub::max_size() const {
  // Max size of stub has been determined by testing with 0, in which case
  // C2CodeStubList::emit() will throw an assertion and report the actual size that
  // is needed.
  return 24;
}

void C2HandleAnonOMOwnerStub::emit(C2_MacroAssembler& masm) {
  __ bind(entry());
  Register mon = monitor();
  Register t = tmp();
  assert(t != noreg, "need tmp register");

  // Fix owner to be the current thread.
  __ str(rthread, Address(mon, ObjectMonitor::owner_offset()));

  // Pop owner object from lock-stack.
  __ ldrw(t, Address(rthread, JavaThread::lock_stack_top_offset()));
  __ subw(t, t, oopSize);
#ifdef ASSERT
  __ str(zr, Address(rthread, t));
#endif
  __ strw(t, Address(rthread, JavaThread::lock_stack_top_offset()));

  __ b(continuation());
}

int C2FastUnlockLightweightStub::max_size() const {
  return 256;
}

void C2FastUnlockLightweightStub::emit(C2_MacroAssembler& masm) {

  const Register monitor = _mark_or_monitor;
  const Register contentions_addr = _t;

  Label fix_flags_and_slow_path;
  Label restore_contentions_slow_path;
  {
    __ bind (restore_contentions_slow_path);
    __ atomic_addw(noreg, -1, contentions_addr);
    // fallthrough fix_flags_and_slow_path
  }
  {
    __ bind (fix_flags_and_slow_path);
    __ cmp(monitor, checked_cast<unsigned char>(0));
    __ b(slow_path_continuation());
  }
  Label fix_flags_and_fast_path;
  Label restore_contentions_fast_path;
  {
    __ bind (restore_contentions_fast_path);
    __ atomic_addw(noreg, -1, contentions_addr);
    // fallthrough fix_flags_and_fast_path
  }
  {
    __ bind (fix_flags_and_fast_path);
    __ cmp(monitor, monitor);
    __ b(unlocked_continuation());
  }

  // The cancellation requires that we first increment and then decrement the contentions.
  // Instead we can simply go to the slow path without changing contentions.
  Label& canceled_deflation_slow_path = fix_flags_and_slow_path;

  { // Handle monitor medium path.

    __ bind(_inflated_medium_path);

    __ cmpxchg(_owner_addr, zr, rthread, Assembler::xword, /*acquire*/ true,
            /*release*/ false, /*weak*/ false, _t);
    __ br(Assembler::EQ, fix_flags_and_slow_path);

    __ cmp(_t, checked_cast<unsigned char>(reinterpret_cast<intptr_t>(DEFLATER_MARKER)));
    __ br(Assembler::NE, fix_flags_and_fast_path);

    __ lea(contentions_addr, Address(monitor, ObjectMonitor::contentions_offset()));

    // FIXME: Need an extra register. This works because of the atomic_XXX implementation.
    const Register usable_scratch = UseLSE ? rscratch1 : rscratch2;

    __ atomic_addw(usable_scratch, 1, contentions_addr);

    __ cmp(usable_scratch, checked_cast<unsigned char>(0));
    __ br(Assembler::LS, restore_contentions_fast_path);

    __ mov(rscratch2, reinterpret_cast<intptr_t>(DEFLATER_MARKER));
    __ cmpxchg(_owner_addr, rscratch2, rthread, Assembler::xword, /*acquire*/ true,
            /*release*/ false, /*weak*/ false, zr);
    __ br(Assembler::EQ, canceled_deflation_slow_path);

    __ cmpxchg(_owner_addr, zr, rthread, Assembler::xword, /*acquire*/ true,
            /*release*/ false, /*weak*/ false, zr);
    __ br(Assembler::EQ, restore_contentions_slow_path);

    __ atomic_addw(zr, -1, contentions_addr);

    __ b(fix_flags_and_fast_path);
  }
}

#undef __
