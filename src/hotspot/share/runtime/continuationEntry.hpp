/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_RUNTIME_CONTINUATIONENTRY_HPP
#define SHARE_VM_RUNTIME_CONTINUATIONENTRY_HPP

#include "oops/oop.hpp"
#include "oops/oopsHierarchy.hpp"
#include "runtime/continuation.hpp"
#include "utilities/sizes.hpp"

#include CPU_HEADER(continuationEntry)

class JavaThread;
class nmethod;
class OopMap;
class RegisterMap;

// Metadata stored in the continuation entry frame
class ContinuationEntry {
  ContinuationEntryPD _pd;
#ifdef ASSERT
private:
  static const int COOKIE_VALUE = 0x1234;
  int cookie;

public:
  static int cookie_value() { return COOKIE_VALUE; }
  static ByteSize cookie_offset() { return byte_offset_of(ContinuationEntry, cookie); }

  void verify_cookie() {
    assert(cookie == COOKIE_VALUE, "Bad cookie: %#x, expected: %#x", cookie, COOKIE_VALUE);
  }
#endif

public:
  static int _return_pc_offset; // friend gen_continuation_enter
  static void set_enter_code(nmethod* nm, int interpreted_entry_offset);
  static bool is_interpreted_call(address call_address);

private:
  static address _return_pc;
  static nmethod* _enter_special;
  static int _interpreted_entry_offset;

private:
  ContinuationEntry* _parent;
  oopDesc* _cont;
  oopDesc* _chunk;
  int _flags;
  // Size in words of the stack arguments of the bottom frame on stack if compiled 0 otherwise.
  // The caller (if there is one) is the still frozen top frame in the StackChunk.
  int _argsize;
  intptr_t* _parent_cont_fastpath;
#ifdef _LP64
  int64_t   _parent_held_monitor_count;
#else
  int32_t   _parent_held_monitor_count;
#endif
  uint _pin_count;

public:
  static ByteSize parent_offset()   { return byte_offset_of(ContinuationEntry, _parent); }
  static ByteSize cont_offset()     { return byte_offset_of(ContinuationEntry, _cont); }
  static ByteSize chunk_offset()    { return byte_offset_of(ContinuationEntry, _chunk); }
  static ByteSize flags_offset()    { return byte_offset_of(ContinuationEntry, _flags); }
  static ByteSize argsize_offset()  { return byte_offset_of(ContinuationEntry, _argsize); }
  static ByteSize pin_count_offset(){ return byte_offset_of(ContinuationEntry, _pin_count); }
  static ByteSize parent_cont_fastpath_offset()      { return byte_offset_of(ContinuationEntry, _parent_cont_fastpath); }
  static ByteSize parent_held_monitor_count_offset() { return byte_offset_of(ContinuationEntry, _parent_held_monitor_count); }

public:
  static size_t size() { return align_up((int)sizeof(ContinuationEntry), 2*wordSize); }

  ContinuationEntry* parent() const { return _parent; }
  int64_t parent_held_monitor_count() const { return (int64_t)_parent_held_monitor_count; }

  static address entry_pc() { return _return_pc; }
  intptr_t* entry_sp() const { return (intptr_t*)this; }
  intptr_t* entry_fp() const;

  static address compiled_entry();
  static address interpreted_entry();

  int argsize() const { return _argsize; }
  void set_argsize(int value) { _argsize = value; }

  bool is_pinned() { return _pin_count > 0; }
  bool pin() {
    if (_pin_count == UINT_MAX) return false;
    _pin_count++;
    return true;
  }
  bool unpin() {
    if (_pin_count == 0) return false;
    _pin_count--;
    return true;
  }

  intptr_t* parent_cont_fastpath() const { return _parent_cont_fastpath; }
  void set_parent_cont_fastpath(intptr_t* x) { _parent_cont_fastpath = x; }

  static ContinuationEntry* from_frame(const frame& f);
  frame to_frame() const;
  void update_register_map(RegisterMap* map) const;
  void flush_stack_processing(JavaThread* thread) const;

  inline intptr_t* bottom_sender_sp() const;
  inline oop cont_oop(const JavaThread* thread) const;
  inline oop scope(const JavaThread* thread) const;
  inline static oop cont_oop_or_null(const ContinuationEntry* ce, const JavaThread* thread);

  oop* cont_addr() { return (oop*)&_cont; }
  oop* chunk_addr() { return (oop*)&_chunk; }

  bool is_virtual_thread() const { return _flags != 0; }

#ifndef PRODUCT
  void describe(FrameValues& values, int frame_no) const;
#endif

#ifdef ASSERT
  static bool assert_entry_frame_laid_out(JavaThread* thread);
#endif
};

#endif // SHARE_VM_RUNTIME_CONTINUATIONENTRY_HPP
