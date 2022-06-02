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

  enum thaw_kind {
    thaw_top = 0,
    thaw_return_barrier = 1,
    thaw_return_barrier_exception = 2,
  };

  static bool is_thaw_return_barrier(thaw_kind kind) {
    return kind != thaw_top;
  }

  static bool is_thaw_return_barrier_exception(thaw_kind kind) {
    bool r = (kind == thaw_return_barrier_exception);
    assert(!r || is_thaw_return_barrier(kind), "must be");
    return r;
  }

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

  static frame continuation_bottom_sender(JavaThread* thread, const frame& callee, intptr_t* sender_sp);
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

void CONT_RegisterNativeMethods(JNIEnv *env, jclass cls);

#endif // SHARE_VM_RUNTIME_CONTINUATION_HPP
