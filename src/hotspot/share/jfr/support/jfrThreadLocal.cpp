/*
 * Copyright (c) 2012, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "jfr/jfrEvents.hpp"
#include "jfr/jni/jfrJavaSupport.hpp"
#include "jfr/leakprofiler/checkpoint/objectSampleCheckpoint.hpp"
#include "jfr/periodic/jfrThreadCPULoadEvent.hpp"
#include "jfr/recorder/checkpoint/jfrCheckpointManager.hpp"
#include "jfr/recorder/checkpoint/types/traceid/jfrOopTraceId.inline.hpp"
#include "jfr/recorder/jfrRecorder.hpp"
#include "jfr/recorder/service/jfrOptionSet.hpp"
#include "jfr/recorder/stacktrace/jfrStackTraceRepository.hpp"
#include "jfr/recorder/storage/jfrStorage.hpp"
#include "jfr/support/jfrThreadId.inline.hpp"
#include "jfr/support/jfrThreadLocal.hpp"
#include "jfr/utilities/jfrSpinlockHelper.hpp"
#include "jfr/writers/jfrJavaEventWriter.hpp"
#include "logging/log.hpp"
#include "memory/allocation.inline.hpp"
#include "memory/arena.hpp"
#include "runtime/atomic.hpp"
#include "runtime/javaThread.hpp"
#include "runtime/os.hpp"
#include "runtime/threadIdentifier.hpp"
#include "utilities/sizes.hpp"

JfrThreadLocal::JfrThreadLocal() :
  _java_event_writer(nullptr),
  _java_buffer(nullptr),
  _native_buffer(nullptr),
  _shelved_buffer(nullptr),
  _load_barrier_buffer_epoch_0(nullptr),
  _load_barrier_buffer_epoch_1(nullptr),
  _checkpoint_buffer_epoch_0(nullptr),
  _checkpoint_buffer_epoch_1(nullptr),
  _stackframes(nullptr),
  _dcmd_arena(nullptr),
  _thread(),
  _vthread_id(0),
  _jvm_thread_id(0),
  _thread_id_alias(max_julong),
  _data_lost(0),
  _stack_trace_id(max_julong),
  _stack_trace_hash(0),
  _parent_trace_id(0),
  _last_allocated_bytes(0),
  _user_time(0),
  _cpu_time(0),
  _wallclock_time(os::javaTimeNanos()),
  _stackdepth(0),
  _entering_suspend_flag(0),
  _critical_section(0),
  _vthread_epoch(0),
  _vthread_excluded(false),
  _jvm_thread_excluded(false),
  _vthread(false),
  _notified(false),
  _dead(false) {
  Thread* thread = Thread::current_or_null();
  _parent_trace_id = thread != nullptr ? jvm_thread_id(thread) : (traceid)0;
}

u8 JfrThreadLocal::add_data_lost(u8 value) {
  _data_lost += value;
  return _data_lost;
}

bool JfrThreadLocal::has_thread_blob() const {
  return _thread.valid();
}

void JfrThreadLocal::set_thread_blob(const JfrBlobHandle& ref) {
  assert(!_thread.valid(), "invariant");
  _thread = ref;
}

const JfrBlobHandle& JfrThreadLocal::thread_blob() const {
  return _thread;
}

static void send_java_thread_start_event(JavaThread* jt) {
  assert(jt != nullptr, "invariant");
  assert(Thread::current() == jt, "invariant");
  if (!JfrJavaSupport::on_thread_start(jt)) {
    // thread is excluded
    return;
  }
  EventThreadStart event;
  traceid thread_id = JfrThreadLocal::jvm_thread_id(jt);
  assert(thread_id != 0, "invariant");
  event.set_thread(thread_id);
  event.set_parentThread(jt->jfr_thread_local()->parent_thread_id());
  event.commit();
}

void JfrThreadLocal::on_start(Thread* t) {
  assign_thread_id(t, t->jfr_thread_local());
  if (JfrRecorder::is_recording()) {
    JfrCheckpointManager::write_checkpoint(t);
    if (t->is_Java_thread()) {
      send_java_thread_start_event(JavaThread::cast(t));
    }
  }
  if (t->jfr_thread_local()->has_cached_stack_trace()) {
    t->jfr_thread_local()->clear_cached_stack_trace();
  }
}

// The starter thread ensures that the startee has a valid _vm_thread_id and _contextual_id.
// This is to avoid recursion in thread assignment since accessing the java threadObj can lead
// to events being fired, a situation the starter thread can handle but not the startee.
void JfrThreadLocal::on_java_thread_start(JavaThread* starter, JavaThread* startee) {
  assert(starter != nullptr, "invariant");
  assert(startee != nullptr, "invariant");
  JfrThreadLocal* const tl = startee->jfr_thread_local();
  assign_thread_id(startee, tl);
  assert(vthread_id(startee) != 0, "invariant");
  assert(jvm_thread_id(startee) == vthread_id(startee), "invariant");
  if (JfrRecorder::is_recording() && EventThreadStart::is_enabled() && EventThreadStart::is_stacktrace_enabled()) {
    // skip level 2 to skip frames Thread.start() and Thread.start0()
    startee->jfr_thread_local()->set_cached_stack_trace_id(JfrStackTraceRepository::record(starter, 2));
  }
}

void JfrThreadLocal::release(Thread* t) {
  if (has_java_event_writer()) {
    assert(t->is_Java_thread(), "invariant");
    JfrJavaSupport::destroy_global_jni_handle(java_event_writer());
    _java_event_writer = nullptr;
  }
  if (has_native_buffer()) {
    JfrStorage::release_thread_local(native_buffer(), t);
    _native_buffer = nullptr;
  }
  if (has_java_buffer()) {
    JfrStorage::release_thread_local(java_buffer(), t);
    _java_buffer = nullptr;
  }
  if (_stackframes != nullptr) {
    FREE_C_HEAP_ARRAY(JfrStackFrame, _stackframes);
    _stackframes = nullptr;
  }
  if (_load_barrier_buffer_epoch_0 != nullptr) {
    _load_barrier_buffer_epoch_0->set_retired();
    _load_barrier_buffer_epoch_0 = nullptr;
  }
  if (_load_barrier_buffer_epoch_1 != nullptr) {
    _load_barrier_buffer_epoch_1->set_retired();
    _load_barrier_buffer_epoch_1 = nullptr;
  }
  if (_checkpoint_buffer_epoch_0 != nullptr) {
    _checkpoint_buffer_epoch_0->set_retired();
    _checkpoint_buffer_epoch_0 = nullptr;
  }
  if (_checkpoint_buffer_epoch_1 != nullptr) {
    _checkpoint_buffer_epoch_1->set_retired();
    _checkpoint_buffer_epoch_1 = nullptr;
  }
  if (_dcmd_arena != nullptr) {
    delete _dcmd_arena;
    _dcmd_arena = nullptr;
  }
}

void JfrThreadLocal::release(JfrThreadLocal* tl, Thread* t) {
  assert(tl != nullptr, "invariant");
  assert(t != nullptr, "invariant");
  assert(Thread::current() == t, "invariant");
  assert(!tl->is_dead(), "invariant");
  assert(tl->shelved_buffer() == nullptr, "invariant");
  tl->_dead = true;
  tl->release(t);
}

static void send_java_thread_end_event(JavaThread* jt, traceid tid) {
  assert(jt != nullptr, "invariant");
  assert(Thread::current() == jt, "invariant");
  assert(tid != 0, "invariant");
  if (JfrRecorder::is_recording()) {
    EventThreadEnd event;
    event.set_thread(tid);
    event.commit();
    ObjectSampleCheckpoint::on_thread_exit(tid);
  }
}

void JfrThreadLocal::on_exit(Thread* t) {
  assert(t != nullptr, "invariant");
  JfrThreadLocal * const tl = t->jfr_thread_local();
  assert(!tl->is_dead(), "invariant");
  if (JfrRecorder::is_recording()) {
    JfrCheckpointManager::write_checkpoint(t);
  }
  if (t->is_Java_thread()) {
    JavaThread* const jt = JavaThread::cast(t);
    send_java_thread_end_event(jt, JfrThreadLocal::jvm_thread_id(jt));
    JfrThreadCPULoadEvent::send_event_for_thread(jt);
  }
  release(tl, Thread::current()); // because it could be that Thread::current() != t
}

static JfrBuffer* acquire_buffer() {
  return JfrStorage::acquire_thread_local(Thread::current());
}

JfrBuffer* JfrThreadLocal::install_native_buffer() const {
  assert(!has_native_buffer(), "invariant");
  _native_buffer = acquire_buffer();
  return _native_buffer;
}

JfrBuffer* JfrThreadLocal::install_java_buffer() const {
  assert(!has_java_buffer(), "invariant");
  assert(!has_java_event_writer(), "invariant");
  _java_buffer = acquire_buffer();
  return _java_buffer;
}

JfrStackFrame* JfrThreadLocal::install_stackframes() const {
  assert(_stackframes == nullptr, "invariant");
  _stackframes = NEW_C_HEAP_ARRAY(JfrStackFrame, stackdepth(), mtTracing);
  return _stackframes;
}

ByteSize JfrThreadLocal::java_event_writer_offset() {
  return byte_offset_of(JfrThreadLocal, _java_event_writer);
}

ByteSize JfrThreadLocal::java_buffer_offset() {
  return byte_offset_of(JfrThreadLocal, _java_buffer);
}

ByteSize JfrThreadLocal::vthread_id_offset() {
  return byte_offset_of(JfrThreadLocal, _vthread_id);
}

ByteSize JfrThreadLocal::vthread_offset() {
  return byte_offset_of(JfrThreadLocal, _vthread);
}

ByteSize JfrThreadLocal::vthread_epoch_offset() {
  return byte_offset_of(JfrThreadLocal, _vthread_epoch);
}

ByteSize JfrThreadLocal::vthread_excluded_offset() {
  return byte_offset_of(JfrThreadLocal, _vthread_excluded);
}

ByteSize JfrThreadLocal::notified_offset() {
  return byte_offset_of(JfrThreadLocal, _notified);
}

void JfrThreadLocal::set(bool* exclusion_field, bool state) {
  assert(exclusion_field != nullptr, "invariant");
  *exclusion_field = state;
}

bool JfrThreadLocal::is_vthread_excluded() const {
  return Atomic::load(&_vthread_excluded);
}

bool JfrThreadLocal::is_jvm_thread_excluded(const Thread* t) {
  assert(t != nullptr, "invariant");
  return t->jfr_thread_local()->_jvm_thread_excluded;
}

void JfrThreadLocal::exclude_vthread(const JavaThread* jt) {
  set(&jt->jfr_thread_local()->_vthread_excluded, true);
  JfrJavaEventWriter::exclude(vthread_id(jt), jt);
}

void JfrThreadLocal::include_vthread(const JavaThread* jt) {
  set(&jt->jfr_thread_local()->_vthread_excluded, false);
  JfrJavaEventWriter::include(vthread_id(jt), jt);
}

void JfrThreadLocal::exclude_jvm_thread(const Thread* t) {
  set(&t->jfr_thread_local()->_jvm_thread_excluded, true);
  if (t->is_Java_thread()) {
    JfrJavaEventWriter::exclude(t->jfr_thread_local()->_jvm_thread_id, JavaThread::cast(t));
  }
}

void JfrThreadLocal::include_jvm_thread(const Thread* t) {
  set(&t->jfr_thread_local()->_jvm_thread_excluded, false);
  if (t->is_Java_thread()) {
    JfrJavaEventWriter::include(t->jfr_thread_local()->_jvm_thread_id, JavaThread::cast(t));
  }
}

bool JfrThreadLocal::is_excluded() const {
  return Atomic::load_acquire(&_vthread) ? is_vthread_excluded(): _jvm_thread_excluded;
}

bool JfrThreadLocal::is_included() const {
  return !is_excluded();
}

bool JfrThreadLocal::is_excluded(const Thread* t) {
  assert(t != nullptr, "invariant");
  return t->jfr_thread_local()->is_excluded();
}

bool JfrThreadLocal::is_included(const Thread* t) {
  assert(t != nullptr, "invariant");
  return t->jfr_thread_local()->is_included();
}

u4 JfrThreadLocal::stackdepth() const {
  return _stackdepth != 0 ? _stackdepth : (u4)JfrOptionSet::stackdepth();
}

bool JfrThreadLocal::is_impersonating(const Thread* t) {
  return t->jfr_thread_local()->_thread_id_alias != max_julong;
}

void JfrThreadLocal::impersonate(const Thread* t, traceid other_thread_id) {
  assert(t != nullptr, "invariant");
  assert(other_thread_id != 0, "invariant");
  JfrThreadLocal* const tl = t->jfr_thread_local();
  tl->_thread_id_alias = other_thread_id;
}

void JfrThreadLocal::stop_impersonating(const Thread* t) {
  assert(t != nullptr, "invariant");
  JfrThreadLocal* const tl = t->jfr_thread_local();
  if (is_impersonating(t)) {
    tl->_thread_id_alias = max_julong;
  }
  assert(!is_impersonating(t), "invariant");
}

typedef JfrOopTraceId<ThreadIdAccess> AccessThreadTraceId;

void JfrThreadLocal::set_vthread_epoch(const JavaThread* jt, traceid tid, u2 epoch) {
  assert(jt != nullptr, "invariant");
  assert(is_vthread(jt), "invariant");
  // To support event recursion, we update the native side first,
  // this provides the terminating case.
  Atomic::store(&jt->jfr_thread_local()->_vthread_epoch, epoch);
  /*
  * The java side, i.e. the vthread object, can now be updated.
  * Accessing the vthread object itself is a recursive case,
  * because it can trigger additional events, e.g.
  * loading the oop through load barriers.
  * Note there is a potential problem with this solution:
  * The recursive write hitting the terminating case will
  * use the thread id _before_ the checkpoint is committed.
  * Hence, the periodic thread can possibly flush that event
  * to a segment that does not include an associated checkpoint.
  * Considered rare and quite benign for now. The worst case is
  * that thread information for that event is not resolvable, i.e. null.
  */
  oop vthread = jt->vthread();
  assert(vthread != nullptr, "invariant");
  AccessThreadTraceId::set_epoch(vthread, epoch);
  JfrCheckpointManager::write_checkpoint(const_cast<JavaThread*>(jt), tid, vthread);
}

traceid JfrThreadLocal::vthread_id(const Thread* t) {
  assert(t != nullptr, "invariant");
  return Atomic::load(&t->jfr_thread_local()->_vthread_id);
}

u2 JfrThreadLocal::vthread_epoch(const JavaThread* jt) {
  assert(jt != nullptr, "invariant");
  return Atomic::load(&jt->jfr_thread_local()->_vthread_epoch);
}

traceid JfrThreadLocal::thread_id(const Thread* t) {
  assert(t != nullptr, "invariant");
  if (is_impersonating(t)) {
    return t->jfr_thread_local()->_thread_id_alias;
  }
  JfrThreadLocal* const tl = t->jfr_thread_local();
  if (!t->is_Java_thread()) {
    return jvm_thread_id(t, tl);
  }
  const JavaThread* jt = JavaThread::cast(t);
  if (!is_vthread(jt)) {
    return jvm_thread_id(t, tl);
  }
  // virtual thread
  const traceid tid = vthread_id(jt);
  assert(tid != 0, "invariant");
  if (!tl->is_vthread_excluded()) {
    const u2 current_epoch = AccessThreadTraceId::current_epoch();
    if (vthread_epoch(jt) != current_epoch) {
      set_vthread_epoch(jt, tid, current_epoch);
    }
  }
  return tid;
}

// When not recording, there is no checkpoint system
// in place for writing vthread information.
traceid JfrThreadLocal::external_thread_id(const Thread* t) {
  assert(t != nullptr, "invariant");
  return JfrRecorder::is_recording() ? thread_id(t) : jvm_thread_id(t);
}

inline traceid load_java_thread_id(const Thread* t) {
  assert(t != nullptr, "invariant");
  assert(t->is_Java_thread(), "invariant");
  oop threadObj = JavaThread::cast(t)->threadObj();
  return threadObj != nullptr ? AccessThreadTraceId::id(threadObj) : 0;
}

traceid JfrThreadLocal::assign_thread_id(const Thread* t, JfrThreadLocal* tl) {
  assert(t != nullptr, "invariant");
  assert(tl != nullptr, "invariant");
  JfrSpinlockHelper spinlock(&tl->_critical_section);
  traceid tid = tl->_jvm_thread_id;
  if (tid == 0) {
    if (t->is_Java_thread()) {
      tid = load_java_thread_id(t);
      tl->_jvm_thread_id = tid;
      Atomic::store(&tl->_vthread_id, tid);
      return tid;
    }
    tid = static_cast<traceid>(ThreadIdentifier::next());
    tl->_jvm_thread_id = tid;
  }
  return tid;
}

traceid JfrThreadLocal::jvm_thread_id(const Thread* t, JfrThreadLocal* tl) {
  assert(t != nullptr, "invariant");
  assert(tl != nullptr, "invariant");
  return tl->_jvm_thread_id != 0 ? tl->_jvm_thread_id : JfrThreadLocal::assign_thread_id(t, tl);
}

traceid JfrThreadLocal::jvm_thread_id(const Thread* t) {
  assert(t != nullptr, "invariant");
  return jvm_thread_id(t, t->jfr_thread_local());
}

bool JfrThreadLocal::is_vthread(const JavaThread* jt) {
  assert(jt != nullptr, "invariant");
  return Atomic::load_acquire(&jt->jfr_thread_local()->_vthread) && jt->last_continuation() != nullptr;
}

inline bool is_virtual(const JavaThread* jt, oop thread) {
  assert(jt != nullptr, "invariant");
  return thread != jt->threadObj();
}

void JfrThreadLocal::on_set_current_thread(JavaThread* jt, oop thread) {
  assert(jt != nullptr, "invariant");
  assert(thread != nullptr, "invariant");
  JfrThreadLocal* const tl = jt->jfr_thread_local();
  if (!is_virtual(jt, thread)) {
    Atomic::release_store(&tl->_vthread, false);
    return;
  }
  Atomic::store(&tl->_vthread_id, AccessThreadTraceId::id(thread));
  const u2 epoch_raw = AccessThreadTraceId::epoch(thread);
  const bool excluded = epoch_raw & excluded_bit;
  Atomic::store(&tl->_vthread_excluded, excluded);
  if (!excluded) {
    Atomic::store(&tl->_vthread_epoch, static_cast<u2>(epoch_raw & epoch_mask));
  }
  Atomic::release_store(&tl->_vthread, true);
}

Arena* JfrThreadLocal::dcmd_arena(JavaThread* jt) {
  assert(jt != nullptr, "invariant");
  JfrThreadLocal* tl = jt->jfr_thread_local();
  Arena* arena = tl->_dcmd_arena;
  if (arena != nullptr) {
    return arena;
  }
  arena = new (mtTracing) Arena(mtTracing);
  tl->_dcmd_arena = arena;
  return arena;
}
