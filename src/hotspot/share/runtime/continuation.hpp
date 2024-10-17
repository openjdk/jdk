/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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

#include "jni.h"
#include "memory/allStatic.hpp"
#include "oops/oopsHierarchy.hpp"

class ContinuationEntry;
class frame;
class FrameValues;
class Handle;
class outputStream;
class RegisterMap;

class Continuations : public AllStatic {
public:
  static void init();
  static bool enabled();
};

void continuations_init();

class javaVFrame;
class JavaThread;

// should match Continuation.toPreemptStatus() in Continuation.java
enum freeze_result {
  freeze_ok = 0,
  freeze_ok_bottom = 1,
  freeze_pinned_cs = 2,
  freeze_pinned_native = 3,
  freeze_pinned_monitor = 4,
  freeze_exception = 5,
  freeze_not_mounted = 6,
  freeze_unsupported = 7
};

class Continuation : AllStatic {
public:

  enum preempt_kind {
    freeze_on_monitorenter = 1,
    freeze_on_wait         = 2
  };

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
  static address freeze_preempt_entry();
  static int prepare_thaw(JavaThread* thread, bool return_barrier);
  static address thaw_entry();

  static int try_preempt(JavaThread* target, oop continuation) NOT_LOOM_MONITOR_SUPPORT({ return freeze_unsupported; });

  static ContinuationEntry* get_continuation_entry_for_continuation(JavaThread* thread, oop continuation);
  static ContinuationEntry* get_continuation_entry_for_sp(JavaThread* thread, intptr_t* const sp);
  static ContinuationEntry* get_continuation_entry_for_entry_frame(JavaThread* thread, const frame& f);

  static bool is_continuation_mounted(JavaThread* thread, oop continuation);

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
