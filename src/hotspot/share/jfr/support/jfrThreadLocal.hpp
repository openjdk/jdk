/*
 * Copyright (c) 2012, 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2024, Datadog, Inc. All rights reserved.
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

#ifndef SHARE_JFR_SUPPORT_JFRTHREADLOCAL_HPP
#define SHARE_JFR_SUPPORT_JFRTHREADLOCAL_HPP

#include "jfr/support/jfrThreadContext.hpp"
#include "jfr/utilities/jfrBlob.hpp"
#include "jfr/utilities/jfrTypes.hpp"

class Arena;
class JavaThread;
class JfrBuffer;
class JfrStackFrame;
class Thread;

class JfrThreadLocal {
  friend class Jfr;
  friend class JfrIntrinsicSupport;
  friend class JfrJavaSupport;
  friend class JfrRecorder;
  friend class JVMCIVMStructs;
 private:
  jobject _java_event_writer;
  mutable JfrBuffer* _java_buffer;
  mutable JfrBuffer* _native_buffer;
  JfrBuffer* _shelved_buffer;
  JfrBuffer* _load_barrier_buffer_epoch_0;
  JfrBuffer* _load_barrier_buffer_epoch_1;
  JfrBuffer* _checkpoint_buffer_epoch_0;
  JfrBuffer* _checkpoint_buffer_epoch_1;
  mutable JfrStackFrame* _stackframes;
  Arena* _dcmd_arena;
  JfrBlobHandle _thread;
  mutable traceid _vthread_id;
  mutable traceid _jvm_thread_id;
  mutable traceid _thread_id_alias;
  u8 _data_lost;
  traceid _stack_trace_id;
  traceid _stack_trace_hash;
  traceid _parent_trace_id;
  int64_t _last_allocated_bytes;
  jlong _user_time;
  jlong _cpu_time;
  jlong _wallclock_time;
  mutable u4 _stackdepth;
  volatile jint _entering_suspend_flag;
  mutable volatile int _critical_section;
  u2 _vthread_epoch;
  bool _vthread_excluded;
  bool _jvm_thread_excluded;
  bool _vthread;
  bool _notified;
  bool _dead;
  mutable JfrThreadContext* _context;

  JfrBuffer* install_native_buffer() const;
  JfrBuffer* install_java_buffer() const;
  JfrStackFrame* install_stackframes() const;
  void release(Thread* t);
  static void release(JfrThreadLocal* tl, Thread* t);

  static void set(bool* excluded_field, bool state);
  static traceid assign_thread_id(const Thread* t, JfrThreadLocal* tl);
  static traceid vthread_id(const Thread* t);
  static void set_vthread_epoch(const JavaThread* jt, traceid id, u2 epoch);
  bool is_vthread_excluded() const;
  static void exclude_vthread(const JavaThread* jt);
  static void include_vthread(const JavaThread* jt);
  static bool is_jvm_thread_excluded(const Thread* t);
  static void exclude_jvm_thread(const Thread* t);
  static void include_jvm_thread(const Thread* t);

 public:
  JfrThreadLocal();

  JfrBuffer* native_buffer() const {
    return _native_buffer != nullptr ? _native_buffer : install_native_buffer();
  }

  bool has_native_buffer() const {
    return _native_buffer != nullptr;
  }

  void set_native_buffer(JfrBuffer* buffer) {
    _native_buffer = buffer;
  }

  JfrBuffer* java_buffer() const {
    return _java_buffer != nullptr ? _java_buffer : install_java_buffer();
  }

  bool has_java_buffer() const {
    return _java_buffer != nullptr;
  }

  void set_java_buffer(JfrBuffer* buffer) {
    _java_buffer = buffer;
  }

  JfrBuffer* shelved_buffer() const {
    return _shelved_buffer;
  }

  void shelve_buffer(JfrBuffer* buffer) {
    _shelved_buffer = buffer;
  }

  bool has_java_event_writer() const {
    return _java_event_writer != nullptr;
  }

  jobject java_event_writer() {
    return _java_event_writer;
  }

  void set_java_event_writer(jobject java_event_writer) {
    _java_event_writer = java_event_writer;
  }

  JfrStackFrame* stackframes() const {
    return _stackframes != nullptr ? _stackframes : install_stackframes();
  }

  void set_stackframes(JfrStackFrame* frames) {
    _stackframes = frames;
  }

  u4 stackdepth() const;

  void set_stackdepth(u4 depth) {
    _stackdepth = depth;
  }

  int64_t last_allocated_bytes() const {
    return _last_allocated_bytes;
  }

  void set_last_allocated_bytes(int64_t allocated_bytes) {
    _last_allocated_bytes = allocated_bytes;
  }

  void clear_last_allocated_bytes() {
    set_last_allocated_bytes(0);
  }

  // Contextually defined thread id that is volatile,
  // a function of Java carrier thread mounts / unmounts.
  static traceid thread_id(const Thread* t);
  static bool is_vthread(const JavaThread* jt);
  static u2 vthread_epoch(const JavaThread* jt);

  // Exposed to external code that use a thread id unconditionally.
  // Jfr might not even be running.
  static traceid external_thread_id(const Thread* t);

  // Non-volatile thread id, for Java carrier threads and non-java threads.
  static traceid jvm_thread_id(const Thread* t);
  static traceid jvm_thread_id(const Thread* t, JfrThreadLocal* tl);

  // To impersonate is to temporarily masquerade as another thread.
  // For example, when writing an event that should be attributed to some other thread.
  static void impersonate(const Thread* t, traceid other_thread_id);
  static void stop_impersonating(const Thread* t);
  static bool is_impersonating(const Thread* t);

  traceid parent_thread_id() const {
    return _parent_trace_id;
  }

  void set_cached_stack_trace_id(traceid id, traceid hash = 0) {
    _stack_trace_id = id;
    _stack_trace_hash = hash;
  }

  bool has_cached_stack_trace() const {
    return _stack_trace_id != max_julong;
  }

  void clear_cached_stack_trace() {
    _stack_trace_id = max_julong;
    _stack_trace_hash = 0;
  }

  traceid cached_stack_trace_id() const {
    return _stack_trace_id;
  }

  traceid cached_stack_trace_hash() const {
    return _stack_trace_hash;
  }

  void set_trace_block() {
    _entering_suspend_flag = 1;
  }

  void clear_trace_block() {
    _entering_suspend_flag = 0;
  }

  bool is_trace_block() const {
    return _entering_suspend_flag != 0;
  }

  u8 data_lost() const {
    return _data_lost;
  }

  u8 add_data_lost(u8 value);

  jlong get_user_time() const {
    return _user_time;
  }

  void set_user_time(jlong user_time) {
    _user_time = user_time;
  }

  jlong get_cpu_time() const {
    return _cpu_time;
  }

  void set_cpu_time(jlong cpu_time) {
    _cpu_time = cpu_time;
  }

  jlong get_wallclock_time() const {
    return _wallclock_time;
  }

  void set_wallclock_time(jlong wallclock_time) {
    _wallclock_time = wallclock_time;
  }

  bool is_notified() {
    return _notified;
  }

  void notify() {
    _notified = true;
  }

  void clear_notification() {
    _notified = false;
  }

  bool is_dead() const {
    return _dead;
  }

  JfrThreadContext* get_context() {
    if (_context == nullptr) {
      _context = new JfrThreadContext();
    }
    return _context;
  }

  bool has_context() {
    return _context != nullptr;
  }

  bool is_excluded() const;
  bool is_included() const;
  static bool is_excluded(const Thread* thread);
  static bool is_included(const Thread* thread);

  static Arena* dcmd_arena(JavaThread* jt);

  bool has_thread_blob() const;
  void set_thread_blob(const JfrBlobHandle& handle);
  const JfrBlobHandle& thread_blob() const;

  // Hooks
  static void on_start(Thread* t);
  static void on_exit(Thread* t);
  static void on_set_current_thread(JavaThread* jt, oop thread);
  static void on_java_thread_start(JavaThread* starter, JavaThread* startee);

  // Code generation
  static ByteSize java_event_writer_offset();
  static ByteSize java_buffer_offset();
  static ByteSize vthread_id_offset();
  static ByteSize vthread_offset();
  static ByteSize vthread_epoch_offset();
  static ByteSize vthread_excluded_offset();
  static ByteSize notified_offset();

  friend class JfrJavaThread;
  friend class JfrCheckpointManager;
  template <typename>
  friend class JfrEpochQueueKlassPolicy;
};

#endif // SHARE_JFR_SUPPORT_JFRTHREADLOCAL_HPP
