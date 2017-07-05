/*
 * Copyright (c) 1998, 2008, Oracle and/or its affiliates. All rights reserved.
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

private:

  void pd_initialize() {
    _anchor.clear();
    _base_of_stack_pointer        = NULL;
  }

  frame pd_last_frame() {
    assert(has_last_Java_frame(), "must have last_Java_sp() when suspended");
    assert(_anchor.walkable(), "thread has not dumped its register windows yet");

    assert(_anchor.last_Java_pc() != NULL, "Ack no pc!");
    return frame(last_Java_sp(), frame::unpatchable, _anchor.last_Java_pc());
  }

  // Sometimes the trap handler needs to record both PC and NPC.
  // This is a SPARC-specific companion to Thread::set_saved_exception_pc.
  address _saved_exception_npc;

  // In polling_page_safepoint_handler_blob(s) we have to tail call other
  // blobs without blowing any registers.  A tail call requires some
  // register to jump with and we can't blow any registers, so it must
  // be restored in the delay slot.  'restore' cannot be used as it
  // will chop the heads off of 64-bit %o registers in the 32-bit
  // build.  Instead we reload the registers using G2_thread and this
  // location.  Must be 64bits in the 32-bit LION build.
  jdouble _o_reg_temps[6];

  // a stack pointer older than any java frame stack pointer.  It is
  // used to validate stack pointers in frame::next_younger_sp (it
  // provides the upper bound in the range check).  This is necessary
  // on Solaris/SPARC since the ucontext passed to a signal handler is
  // sometimes corrupt and we need a way to check the extracted sp.
  intptr_t* _base_of_stack_pointer;

public:

  static int o_reg_temps_offset_in_bytes() { return offset_of(JavaThread, _o_reg_temps); }

#ifndef _LP64
  address o_reg_temps(int i) { return (address)&_o_reg_temps[i]; }
#endif

  static int saved_exception_npc_offset_in_bytes() { return offset_of(JavaThread,_saved_exception_npc); }

  address  saved_exception_npc()             { return _saved_exception_npc; }
  void set_saved_exception_npc(address a)    { _saved_exception_npc = a; }


public:

  intptr_t* base_of_stack_pointer() { return _base_of_stack_pointer; }

  void set_base_of_stack_pointer(intptr_t* base_sp) {
    _base_of_stack_pointer = base_sp;
  }

  void record_base_of_stack_pointer() {
    intptr_t *sp = (intptr_t *)(((intptr_t)StubRoutines::Sparc::flush_callers_register_windows_func()()));
    intptr_t *ysp;
    while((ysp = (intptr_t*)sp[FP->sp_offset_in_saved_window()]) != NULL) {
      sp = (intptr_t *)((intptr_t)ysp + STACK_BIAS);
    }
    _base_of_stack_pointer = sp;
  }

  bool pd_get_top_frame_for_signal_handler(frame* fr_addr, void* ucontext,
    bool isInJava);

  // These routines are only used on cpu architectures that
  // have separate register stacks (Itanium).
  static bool register_stack_overflow() { return false; }
  static void enable_register_stack_guard() {}
  static void disable_register_stack_guard() {}
