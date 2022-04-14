/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_RUNTIME_CONTINUATION_HPP
#define SHARE_VM_RUNTIME_CONTINUATION_HPP

#include "oops/access.hpp"
#include "oops/oopsHierarchy.hpp"
#include "memory/iterator.hpp"
#include "runtime/frame.hpp"
#include "runtime/globals.hpp"
#include "jni.h"

class ContinuationEntry;

class Continuations : public AllStatic {
private:
  static uint64_t _gc_epoch;

public:
  static void init();
  static bool enabled(); // TODO: used while virtual threads are in Preview; remove when GA

  // The GC epoch and marking_cycle code below is there to support sweeping
  // nmethods in loom stack chunks.
  static uint64_t gc_epoch();
  static bool is_gc_marking_cycle_active();
  static uint64_t previous_completed_gc_marking_cycle();
  static void on_gc_marking_cycle_start();
  static void on_gc_marking_cycle_finish();
  static void arm_all_nmethods();
};

void continuations_init();

class javaVFrame;
class JavaThread;

class Continuation : AllStatic {
public:
  static void init();

  static address freeze_entry();
  static int prepare_thaw(JavaThread* thread, bool return_barrier);
  static address thaw_entry();

  static const ContinuationEntry* last_continuation(const JavaThread* thread, oop cont_scope);
  static ContinuationEntry* get_continuation_entry_for_continuation(JavaThread* thread, oop continuation);
  static ContinuationEntry* get_continuation_entry_for_sp(JavaThread* thread, intptr_t* const sp);

  static ContinuationEntry* get_continuation_entry_for_entry_frame(JavaThread* thread, const frame& f) {
    assert(is_continuation_enterSpecial(f), "");
    ContinuationEntry* entry = (ContinuationEntry*)f.unextended_sp();
    assert(entry == get_continuation_entry_for_sp(thread, f.sp()-2), "mismatched entry");
    return entry;
  }

  static bool is_continuation_mounted(JavaThread* thread, oop continuation);
  static bool is_continuation_scope_mounted(JavaThread* thread, oop cont_scope);

  static bool is_cont_barrier_frame(const frame& f);
  static bool is_return_barrier_entry(const address pc);
  static bool is_continuation_enterSpecial(const frame& f);
  static bool is_continuation_entry_frame(const frame& f, const RegisterMap *map);

  static bool is_frame_in_continuation(const ContinuationEntry* entry, const frame& f);
  static bool is_frame_in_continuation(JavaThread* thread, const frame& f);

  static bool has_last_Java_frame(oop continuation, frame* frame, RegisterMap* map);
  static frame last_frame(oop continuation, RegisterMap *map);
  static frame top_frame(const frame& callee, RegisterMap* map);
  static javaVFrame* last_java_vframe(Handle continuation, RegisterMap *map);
  static frame continuation_parent_frame(RegisterMap* map);

  static oop continuation_scope(oop continuation);
  static bool is_scope_bottom(oop cont_scope, const frame& fr, const RegisterMap* map);

  static bool is_in_usable_stack(address addr, const RegisterMap* map);

  // pins/unpins the innermost mounted continuation; returns true on success or false if there's no continuation or the operation failed
  static bool pin(JavaThread* current);
  static bool unpin(JavaThread* current);

  static bool fix_continuation_bottom_sender(JavaThread* thread, const frame& callee, address* sender_pc, intptr_t** sender_sp);
  static address get_top_return_pc_post_barrier(JavaThread* thread, address pc);
  static void set_cont_fastpath_thread_state(JavaThread* thread);
  static void notify_deopt(JavaThread* thread, intptr_t* sp);

  // access frame data

#ifndef PRODUCT
  static void describe(FrameValues &values);
#endif

private:
  friend class InstanceStackChunkKlass;

#ifdef ASSERT
public:
  static void debug_verify_continuation(oop continuation);
  static void print(oop continuation);
  static void print_on(outputStream* st, oop continuation);
#endif
};

// Metadata stored in the continuation entry frame
class ContinuationEntry {
public:
#ifdef ASSERT
  int cookie;
  static ByteSize cookie_offset() { return byte_offset_of(ContinuationEntry, cookie); }
  void verify_cookie() { assert(this->cookie == 0x1234, ""); }
#endif

public:
  static int return_pc_offset; // friend gen_continuation_enter
  static void set_enter_nmethod(nmethod* nm); // friend SharedRuntime::generate_native_wrapper

private:
  static nmethod* continuation_enter;
  static address return_pc;

private:
  ContinuationEntry* _parent;
  oopDesc* _cont;
  oopDesc* _chunk;
  int _flags;
  int _argsize;
  intptr_t* _parent_cont_fastpath;
  int _parent_held_monitor_count;
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

  static void setup_oopmap(OopMap* map) {
    map->set_oop(VMRegImpl::stack2reg(in_bytes(cont_offset())  / VMRegImpl::stack_slot_size));
    map->set_oop(VMRegImpl::stack2reg(in_bytes(chunk_offset()) / VMRegImpl::stack_slot_size));
  }

public:
  static size_t size() { return align_up((int)sizeof(ContinuationEntry), 2*wordSize); }

  ContinuationEntry* parent() const { return _parent; }

  static address entry_pc() { return return_pc; }
  intptr_t* entry_sp() const { return (intptr_t*)this; }
  intptr_t* entry_fp() const;

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

  intptr_t* bottom_sender_sp() const {
    intptr_t* sp = entry_sp() - argsize();
#ifdef _LP64
    sp = align_down(sp, frame::frame_alignment);
#endif
    return sp;
  }

  oop cont_oop() const {
    oop snapshot = _cont;
    return NativeAccess<>::oop_load(&snapshot);
  }

  oop scope()     const { return Continuation::continuation_scope(cont_oop()); }

  oop cont_raw()  const { return _cont; }
  oop chunk_raw() const { return _chunk; }

  bool is_virtual_thread() const { return _flags != 0; }

  static oop cont_oop_or_null(const ContinuationEntry* ce) {
    return ce == nullptr ? nullptr : ce->cont_oop();
  }

#ifndef PRODUCT
  void describe(FrameValues& values, int frame_no) const {
    address usp = (address)this;
    values.describe(frame_no, (intptr_t*)(usp + in_bytes(ContinuationEntry::parent_offset())),    "parent");
    values.describe(frame_no, (intptr_t*)(usp + in_bytes(ContinuationEntry::cont_offset())),      "continuation");
    values.describe(frame_no, (intptr_t*)(usp + in_bytes(ContinuationEntry::flags_offset())),     "flags");
    values.describe(frame_no, (intptr_t*)(usp + in_bytes(ContinuationEntry::chunk_offset())),     "chunk");
    values.describe(frame_no, (intptr_t*)(usp + in_bytes(ContinuationEntry::argsize_offset())),   "argsize");
    values.describe(frame_no, (intptr_t*)(usp + in_bytes(ContinuationEntry::pin_count_offset())), "pin_count");
    values.describe(frame_no, (intptr_t*)(usp + in_bytes(ContinuationEntry::parent_cont_fastpath_offset())),      "parent fastpath");
    values.describe(frame_no, (intptr_t*)(usp + in_bytes(ContinuationEntry::parent_held_monitor_count_offset())), "parent held monitor count");
  }
#endif

#ifdef ASSERT
  static bool assert_entry_frame_laid_out(JavaThread* thread);
#endif
};

void CONT_RegisterNativeMethods(JNIEnv *env, jclass cls);

#endif // SHARE_VM_RUNTIME_CONTINUATION_HPP
