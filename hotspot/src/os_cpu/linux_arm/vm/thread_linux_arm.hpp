/*
 * Copyright (c) 2008, 2016, Oracle and/or its affiliates. All rights reserved.
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

#ifndef OS_CPU_LINUX_ARM_VM_THREAD_LINUX_ARM_HPP
#define OS_CPU_LINUX_ARM_VM_THREAD_LINUX_ARM_HPP

 private:
  // The following thread-local variables replicate corresponding global variables.
  // They are used for a quick access from compiled code via Rthread register.
  address _heap_top_addr;
  address _heap_lock_addr;
  address _card_table_base;

  void pd_initialize() {
    _anchor.clear();
    _in_top_frame_unsafe_section = NULL;
  }

  frame pd_last_frame() {
    assert(has_last_Java_frame(), "must have last_Java_sp() when suspended");
#ifdef AARCH64
    assert (_anchor.last_Java_pc() != NULL, "pc should be stored");
    return frame(_anchor.last_Java_sp(), _anchor.last_Java_fp(), _anchor.last_Java_pc());
#else
    if (_anchor.last_Java_pc() != NULL) {
      return frame(_anchor.last_Java_sp(), _anchor.last_Java_fp(), _anchor.last_Java_pc());
    } else {
      // This will pick up pc from sp
      return frame(_anchor.last_Java_sp(), _anchor.last_Java_fp());
    }
#endif // AARCH64
  }

 public:
  intptr_t* last_Java_fp()                       { return _anchor.last_Java_fp(); }
  void  set_last_Java_fp(intptr_t* fp)           { _anchor.set_last_Java_fp(fp);  }
  void  set_last_Java_pc(address pc)             { _anchor.set_last_Java_pc(pc);  }

  static ByteSize last_Java_fp_offset()          {
    return byte_offset_of(JavaThread, _anchor) + JavaFrameAnchor::last_Java_fp_offset();
  }

  void set_base_of_stack_pointer(intptr_t* base_sp) {
    // Nothing to do
  }

  intptr_t* base_of_stack_pointer() {
    return NULL;
  }

  void record_base_of_stack_pointer() {
    // Nothing to do
  }

  static ByteSize heap_top_addr_offset()         { return byte_offset_of(JavaThread, _heap_top_addr); }
  static ByteSize card_table_base_offset()       { return byte_offset_of(JavaThread, _card_table_base); }

private:
  // Set to "this" if pd_get_top_frame should ignore this thread for now.
  JavaThread *_in_top_frame_unsafe_section;

public:
  static ByteSize in_top_frame_unsafe_section_offset() { return byte_offset_of(JavaThread, _in_top_frame_unsafe_section); }
  bool in_top_frame_unsafe_section() { return _in_top_frame_unsafe_section == this; }

  bool pd_get_top_frame_for_signal_handler(frame* fr_addr, void* ucontext, bool isInJava);

  bool pd_get_top_frame_for_profiling(frame* fr_addr, void* ucontext, bool isInJava);
private:
  bool pd_get_top_frame(frame* fr_addr, void* ucontext, bool isInJava);
public:

  // These routines are only used on cpu architectures that
  // have separate register stacks (Itanium).
  static bool register_stack_overflow() { return false; }
  static void enable_register_stack_guard() {}
  static void disable_register_stack_guard() {}

#endif // OS_CPU_LINUX_ARM_VM_THREAD_LINUX_ARM_HPP
