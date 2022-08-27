/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
#include "runtime/continuation.hpp"
#include "runtime/continuationEntry.hpp"
#include "runtime/javaThread.hpp"
#include "macroAssembler_x86.hpp"

//---------------------------- continuation_enter_setup ---------------------------
//
// Arguments:
//   None.
//
// Results:
//   rsp: pointer to blank ContinuationEntry
//
// Kills:
//   rax
//
OopMap* MacroAssembler::continuation_enter_setup(int& stack_slots) {
  assert(ContinuationEntry::size() % VMRegImpl::stack_slot_size == 0, "");
  assert(in_bytes(ContinuationEntry::cont_offset())  % VMRegImpl::stack_slot_size == 0, "");
  assert(in_bytes(ContinuationEntry::chunk_offset()) % VMRegImpl::stack_slot_size == 0, "");

  stack_slots += checked_cast<int>(ContinuationEntry::size()) / wordSize;
  subptr(rsp, checked_cast<int32_t>(ContinuationEntry::size()));

  int frame_size = (checked_cast<int>(ContinuationEntry::size()) + wordSize) / VMRegImpl::stack_slot_size;
  OopMap* map = new OopMap(frame_size, 0);
  ContinuationEntry::setup_oopmap(map);

  movptr(rax, Address(r15_thread, JavaThread::cont_entry_offset()));
  movptr(Address(rsp, ContinuationEntry::parent_offset()), rax);
  movptr(Address(r15_thread, JavaThread::cont_entry_offset()), rsp);

  return map;
}

//---------------------------- fill_continuation_entry ---------------------------
//
// Arguments:
//   rsp: pointer to blank Continuation entry
//   reg_cont_obj: pointer to the continuation
//   reg_flags: flags
//
// Results:
//   rsp: pointer to filled out ContinuationEntry
//
// Kills:
//   rax
//
void MacroAssembler::fill_continuation_entry(Register reg_cont_obj, Register reg_flags) {
  assert_different_registers(rax, reg_cont_obj, reg_flags);
#ifdef ASSERT
  movl(Address(rsp, ContinuationEntry::cookie_offset()), ContinuationEntry::cookie_value());
#endif
  movptr(Address(rsp, ContinuationEntry::cont_offset()), reg_cont_obj);
  movl  (Address(rsp, ContinuationEntry::flags_offset()), reg_flags);
  movptr(Address(rsp, ContinuationEntry::chunk_offset()), 0);
  movl(Address(rsp, ContinuationEntry::argsize_offset()), 0);
  movl(Address(rsp, ContinuationEntry::pin_count_offset()), 0);

  movptr(rax, Address(r15_thread, JavaThread::cont_fastpath_offset()));
  movptr(Address(rsp, ContinuationEntry::parent_cont_fastpath_offset()), rax);
  movq(rax, Address(r15_thread, JavaThread::held_monitor_count_offset()));
  movq(Address(rsp, ContinuationEntry::parent_held_monitor_count_offset()), rax);

  movptr(Address(r15_thread, JavaThread::cont_fastpath_offset()), 0);
  movq(Address(r15_thread, JavaThread::held_monitor_count_offset()), 0);
}

//---------------------------- continuation_enter_cleanup ---------------------------
//
// Arguments:
//   rsp: pointer to the ContinuationEntry
//
// Results:
//   rsp: pointer to the spilled rbp in the entry frame
//
// Kills:
//   rbx
//
void MacroAssembler::continuation_enter_cleanup() {
#ifdef ASSERT
  Label L_good_sp;
  cmpptr(rsp, Address(r15_thread, JavaThread::cont_entry_offset()));
  jcc(Assembler::equal, L_good_sp);
  stop("Incorrect rsp at continuation_enter_cleanup");
  bind(L_good_sp);
#endif

  movptr(rbx, Address(rsp, ContinuationEntry::parent_cont_fastpath_offset()));
  movptr(Address(r15_thread, JavaThread::cont_fastpath_offset()), rbx);
  movq(rbx, Address(rsp, ContinuationEntry::parent_held_monitor_count_offset()));
  movq(Address(r15_thread, JavaThread::held_monitor_count_offset()), rbx);

  movptr(rbx, Address(rsp, ContinuationEntry::parent_offset()));
  movptr(Address(r15_thread, JavaThread::cont_entry_offset()), rbx);
  addptr(rsp, checked_cast<int32_t>(ContinuationEntry::size()));
}
