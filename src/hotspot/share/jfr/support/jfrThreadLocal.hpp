/*
 * Copyright (c) 2012, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "jfr/periodic/sampling/jfrSampleRequest.hpp"
#include "jfr/utilities/jfrAllocation.hpp"
#include "jfr/utilities/jfrBlob.hpp"
#include "jfr/utilities/jfrTime.hpp"
#include "jfr/utilities/jfrTypes.hpp"
#include "runtime/atomic.hpp"
#include "runtime/mutexLocker.hpp"

#ifdef LINUX
#include "jfr/periodic/sampling/jfrCPUTimeThreadSampler.hpp"
#endif

class Arena;
class JavaThread;
class JfrBuffer;
class Thread;

class JfrThreadLocal {
  friend class Jfr;
  friend class JfrIntrinsicSupport;
  friend class JfrJavaSupport;
  friend class JVMCIVMStructs;
 private:
  mutable JfrSampleRequest _sample_request;
  JfrSampleRequestQueue _sample_request_queue;
  Monitor _sample_monitor;
  jobject _java_event_writer;
  mutable JfrBuffer* _java_buffer;
  mutable JfrBuffer* _native_buffer;
  JfrBuffer* _shelved_buffer;
  JfrBuffer* _load_barrier_buffer_epoch_0;
  JfrBuffer* _load_barrier_buffer_epoch_1;
  JfrBuffer* _checkpoint_buffer_epoch_0;
  JfrBuffer* _checkpoint_buffer_epoch_1;
  volatile int _sample_state;
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
  int32_t _non_reentrant_nesting;
  u2 _vthread_epoch;
  bool _vthread_excluded;
  bool _jvm_thread_excluded;
  volatile bool _enqueued_requests;
  bool _vthread;
  bool _notified;
  bool _dead;
  bool _sampling_critical_section;

#ifdef LINUX
  timer_t* _cpu_timer;

  enum CPUTimeLockState {
    UNLOCKED,
    // locked for enqueuing
    ENQUEUE,
    // locked for dequeuing
    DEQUEUE
  };
  volatile CPUTimeLockState _cpu_time_jfr_locked;
  volatile bool _has_cpu_time_jfr_requests;
  JfrCPUTimeTraceQueue _cpu_time_jfr_queue;
  volatile bool _do_async_processing_of_cpu_time_jfr_requests;
#endif

  JfrBuffer* install_native_buffer() const;
  JfrBuffer* install_java_buffer() const;
  void release(Thread* t);
  static void release(JfrThreadLocal* tl, Thread* t);
  static void initialize_main_thread(JavaThread* jt);

  static void set(bool* excluded_field, bool state);
  static traceid assign_thread_id(const Thread* t, JfrThreadLocal* tl);
  static traceid vthread_id(const Thread* t);
  static void set_vthread_epoch(const JavaThread* jt, traceid tid, u2 epoch);
  static void set_vthread_epoch_checked(const JavaThread* jt, traceid tid, u2 epoch);
  static traceid jvm_thread_id(const JfrThreadLocal* tl);
  bool is_vthread_excluded() const;
  static void exclude_vthread(const JavaThread* jt);
  static void include_vthread(const JavaThread* jt);
  static bool is_jvm_thread_excluded(const Thread* t);
  static void exclude_jvm_thread(const Thread* t);
  static void include_jvm_thread(const Thread* t);
  static bool is_non_reentrant();

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


  int sample_state() const {
    return Atomic::load_acquire(&_sample_state);
  }

  void set_sample_state(int state) {
    Atomic::release_store(&_sample_state, state);
  }

  Monitor* sample_monitor() {
    return &_sample_monitor;
  }

  JfrSampleRequestQueue* sample_requests() {
    return &_sample_request_queue;
  }

  JfrSampleRequest sample_request() const {
    return _sample_request;
  }

  void set_sample_request(JfrSampleRequest request) {
    _sample_request = request;
  }

  void set_sample_ticks() {
    _sample_request._sample_ticks = JfrTicks::now();
  }

  void set_sample_ticks(const JfrTicks& ticks) {
    _sample_request._sample_ticks = ticks;
  }

  bool has_sample_ticks() const {
    return _sample_request._sample_ticks.value() != 0;
  }

  const JfrTicks& sample_ticks() const {
    return _sample_request._sample_ticks;
  }

  bool has_enqueued_requests() const {
    return Atomic::load_acquire(&_enqueued_requests);
  }

  void enqueue_request() {
    assert_lock_strong(sample_monitor());
    assert(sample_state() == JAVA_SAMPLE, "invariant");
    if (_sample_request_queue.append(_sample_request) == 0) {
      Atomic::release_store(&_enqueued_requests, true);
    }
    set_sample_state(NO_SAMPLE);
  }

  void clear_enqueued_requests() {
    assert_lock_strong(sample_monitor());
    assert(has_enqueued_requests(), "invariant");
    assert(_sample_request_queue.is_nonempty(), "invariant");
    _sample_request_queue.clear();
    Atomic::release_store(&_enqueued_requests, false);
  }

  bool has_native_sample_request() const {
    return sample_state() == NATIVE_SAMPLE;
  }

  bool has_java_sample_request() const {
    return sample_state() == JAVA_SAMPLE || has_enqueued_requests();
  }

  bool has_sample_request() const {
    return sample_state() != NO_SAMPLE || has_enqueued_requests();
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
  traceid vthread_id_with_epoch_update(const JavaThread* jt) const;

  // Exposed to external code that use a thread id unconditionally.
  // Jfr might not even be running.
  static traceid external_thread_id(const Thread* t);

  // Non-volatile thread id, for Java carrier threads and non-java threads.
  static traceid jvm_thread_id(const Thread* t);

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

  bool in_sampling_critical_section() const {
    return _sampling_critical_section;
  }

  static int32_t make_non_reentrant(Thread* thread);
  static void make_reentrant(Thread* thread, int32_t previous_nesting);

  bool is_excluded() const;
  bool is_included() const;
  static bool is_excluded(const Thread* thread);
  static bool is_included(const Thread* thread);

  static Arena* dcmd_arena(JavaThread* jt);

  bool has_thread_blob() const;
  void set_thread_blob(const JfrBlobHandle& handle);
  const JfrBlobHandle& thread_blob() const;

  // CPU time sampling
#ifdef LINUX
  void set_cpu_timer(timer_t* timer);
  void unset_cpu_timer();
  timer_t* cpu_timer() const;

  // The CPU time JFR lock has three different states:
  // - ENQUEUE: lock for enqueuing CPU time requests
  // - DEQUEUE: lock for dequeuing CPU time requests
  // - UNLOCKED: no lock held
  // This ensures that we can safely enqueue and dequeue CPU time requests,
  // without interleaving

  bool is_cpu_time_jfr_enqueue_locked();
  bool is_cpu_time_jfr_dequeue_locked();

  bool try_acquire_cpu_time_jfr_enqueue_lock();
  bool try_acquire_cpu_time_jfr_dequeue_lock();
  void acquire_cpu_time_jfr_dequeue_lock();
  void release_cpu_time_jfr_queue_lock();

  void set_has_cpu_time_jfr_requests(bool has_events);
  bool has_cpu_time_jfr_requests();

  JfrCPUTimeTraceQueue& cpu_time_jfr_queue();
  void deallocate_cpu_time_jfr_queue();

  void set_do_async_processing_of_cpu_time_jfr_requests(bool wants);
  bool wants_async_processing_of_cpu_time_jfr_requests();
#else
  bool has_cpu_time_jfr_requests() { return false; }
#endif

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
  static ByteSize sample_state_offset();
  static ByteSize sampling_critical_section_offset();

  friend class JfrJavaThread;
  friend class JfrCheckpointManager;
  template <typename>
  friend class JfrEpochQueueKlassPolicy;
};

#endif // SHARE_JFR_SUPPORT_JFRTHREADLOCAL_HPP
