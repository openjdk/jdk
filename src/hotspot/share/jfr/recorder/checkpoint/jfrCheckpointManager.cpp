/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/javaClasses.inline.hpp"
#include "jfr/leakprofiler/checkpoint/objectSampleCheckpoint.hpp"
#include "jfr/leakprofiler/leakProfiler.hpp"
#include "jfr/recorder/checkpoint/jfrCheckpointManager.hpp"
#include "jfr/recorder/checkpoint/jfrCheckpointWriter.hpp"
#include "jfr/recorder/checkpoint/types/jfrTypeManager.hpp"
#include "jfr/recorder/checkpoint/types/jfrTypeSet.hpp"
#include "jfr/recorder/checkpoint/types/traceid/jfrTraceIdEpoch.hpp"
#include "jfr/recorder/jfrRecorder.hpp"
#include "jfr/recorder/repository/jfrChunkWriter.hpp"
#include "jfr/recorder/service/jfrOptionSet.hpp"
#include "jfr/recorder/storage/jfrMemorySpace.inline.hpp"
#include "jfr/recorder/storage/jfrStorageUtils.inline.hpp"
#include "jfr/support/jfrKlassUnloading.hpp"
#include "jfr/utilities/jfrBigEndian.hpp"
#include "jfr/utilities/jfrIterator.hpp"
#include "jfr/utilities/jfrLinkedList.inline.hpp"
#include "jfr/utilities/jfrThreadIterator.hpp"
#include "jfr/utilities/jfrTypes.hpp"
#include "jfr/writers/jfrJavaEventWriter.hpp"
#include "logging/log.hpp"
#include "memory/iterator.hpp"
#include "memory/resourceArea.hpp"
#include "runtime/atomic.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/mutex.hpp"
#include "runtime/os.inline.hpp"
#include "runtime/safepoint.hpp"

typedef JfrCheckpointManager::BufferPtr BufferPtr;

static volatile bool constant_pending = false;

static bool is_constant_pending() {
  if (Atomic::load_acquire(&constant_pending)) {
    Atomic::release_store(&constant_pending, false); // reset
    return true;
  }
  return false;
}

static void set_constant_pending() {
  if (!Atomic::load_acquire(&constant_pending)) {
    Atomic::release_store(&constant_pending, true);
  }
}

static JfrCheckpointManager* _instance = NULL;

JfrCheckpointManager& JfrCheckpointManager::instance() {
  return *_instance;
}

JfrCheckpointManager* JfrCheckpointManager::create(JfrChunkWriter& cw) {
  assert(_instance == NULL, "invariant");
  _instance = new JfrCheckpointManager(cw);
  return _instance;
}

void JfrCheckpointManager::destroy() {
  assert(_instance != NULL, "invariant");
  delete _instance;
  _instance = NULL;
}

JfrCheckpointManager::JfrCheckpointManager(JfrChunkWriter& cw) :
  _mspace(NULL),
  _chunkwriter(cw) {}

JfrCheckpointManager::~JfrCheckpointManager() {
  JfrTraceIdLoadBarrier::destroy();
  JfrTypeManager::destroy();
  delete _mspace;
}

static const size_t buffer_count = 2;
static const size_t buffer_size = 512 * K;

static JfrCheckpointMspace* allocate_mspace(size_t min_elem_size,
                                            size_t free_list_cache_count_limit,
                                            size_t cache_prealloc_count,
                                            bool prealloc_to_free_list,
                                            JfrCheckpointManager* mgr) {
  return create_mspace<JfrCheckpointMspace, JfrCheckpointManager>(min_elem_size,
                                                                  free_list_cache_count_limit,
                                                                  cache_prealloc_count,
                                                                  prealloc_to_free_list,
                                                                  mgr);
}

bool JfrCheckpointManager::initialize() {
  assert(_mspace == NULL, "invariant");
  _mspace = allocate_mspace(buffer_size, 0, 0, false, this); // post-pone preallocation
  if (_mspace == NULL) {
    return false;
  }
  // preallocate buffer count to each of the epoch live lists
  for (size_t i = 0; i < buffer_count * 2; ++i) {
    Buffer* const buffer = mspace_allocate(buffer_size, _mspace);
    _mspace->add_to_live_list(buffer, i % 2 == 0);
  }
  assert(_mspace->free_list_is_empty(), "invariant");
  return JfrTypeManager::initialize() && JfrTraceIdLoadBarrier::initialize();
}

void JfrCheckpointManager::register_full(BufferPtr buffer, Thread* thread) {
  // nothing here at the moment
  assert(buffer != NULL, "invariant");
  assert(buffer->acquired_by(thread), "invariant");
  assert(buffer->retired(), "invariant");
}

#ifdef ASSERT
static void assert_lease(const BufferPtr buffer) {
  assert(buffer != NULL, "invariant");
  assert(buffer->acquired_by_self(), "invariant");
  assert(buffer->lease(), "invariant");
}

static void assert_release(const BufferPtr buffer) {
  assert(buffer != NULL, "invariant");
  assert(buffer->lease(), "invariant");
  assert(buffer->acquired_by_self(), "invariant");
}
#endif // ASSERT

static BufferPtr lease(size_t size, JfrCheckpointMspace* mspace, size_t retry_count, Thread* thread, bool previous_epoch) {
  assert(mspace != NULL, "invariant");
  static const size_t max_elem_size = mspace->min_element_size(); // min is max
  BufferPtr buffer;
  if (size <= max_elem_size) {
    buffer = mspace_acquire_lease_with_retry(size, mspace, retry_count, thread, previous_epoch);
    if (buffer != NULL) {
      DEBUG_ONLY(assert_lease(buffer);)
      return buffer;
    }
  }
  buffer = mspace_allocate_transient_lease_to_live_list(size, mspace, thread, previous_epoch);
  DEBUG_ONLY(assert_lease(buffer);)
  return buffer;
}

static const size_t lease_retry = 100;

BufferPtr JfrCheckpointManager::lease(Thread* thread, bool previous_epoch /* false */, size_t size /* 0 */) {
  return ::lease(size, instance()._mspace, lease_retry, thread, previous_epoch);
}

bool JfrCheckpointManager::lookup(BufferPtr old) const {
  assert(old != NULL, "invariant");
  return !_mspace->in_current_epoch_list(old);
}

BufferPtr JfrCheckpointManager::lease(BufferPtr old, Thread* thread, size_t size /* 0 */) {
  assert(old != NULL, "invariant");
  return ::lease(size, instance()._mspace, lease_retry, thread, instance().lookup(old));
}

/*
 * If the buffer was a lease, release back.
 *
 * The buffer is effectively invalidated for the thread post-return,
 * and the caller should take means to ensure that it is not referenced.
 */
static void release(BufferPtr buffer, Thread* thread) {
  DEBUG_ONLY(assert_release(buffer);)
  buffer->clear_lease();
  if (buffer->transient()) {
    buffer->set_retired();
  } else {
    buffer->release();
  }
}

BufferPtr JfrCheckpointManager::flush(BufferPtr old, size_t used, size_t requested, Thread* thread) {
  assert(old != NULL, "invariant");
  assert(old->lease(), "invariant");
  if (0 == requested) {
    // indicates a lease is being returned
    release(old, thread);
    set_constant_pending();
    return NULL;
  }
  // migration of in-flight information
  BufferPtr const new_buffer = lease(old, thread, used + requested);
  if (new_buffer != NULL) {
    migrate_outstanding_writes(old, new_buffer, used, requested);
  }
  release(old, thread);
  return new_buffer; // might be NULL
}

// offsets into the JfrCheckpointEntry
static const juint starttime_offset = sizeof(jlong);
static const juint duration_offset = starttime_offset + sizeof(jlong);
static const juint checkpoint_type_offset = duration_offset + sizeof(jlong);
static const juint types_offset = checkpoint_type_offset + sizeof(juint);
static const juint payload_offset = types_offset + sizeof(juint);

template <typename Return>
static Return read_data(const u1* data) {
  return JfrBigEndian::read<Return>(data);
}

static jlong total_size(const u1* data) {
  return read_data<jlong>(data);
}

static jlong starttime(const u1* data) {
  return read_data<jlong>(data + starttime_offset);
}

static jlong duration(const u1* data) {
  return read_data<jlong>(data + duration_offset);
}

static u1 checkpoint_type(const u1* data) {
  return read_data<u1>(data + checkpoint_type_offset);
}

static juint number_of_types(const u1* data) {
  return read_data<juint>(data + types_offset);
}

static void write_checkpoint_header(JfrChunkWriter& cw, int64_t delta_to_last_checkpoint, const u1* data) {
  cw.reserve(sizeof(u4));
  cw.write<u8>(EVENT_CHECKPOINT);
  cw.write(starttime(data));
  cw.write(duration(data));
  cw.write(delta_to_last_checkpoint);
  cw.write(checkpoint_type(data));
  cw.write(number_of_types(data));
}

static void write_checkpoint_content(JfrChunkWriter& cw, const u1* data, size_t size) {
  assert(data != NULL, "invariant");
  cw.write_unbuffered(data + payload_offset, size - sizeof(JfrCheckpointEntry));
}

static size_t write_checkpoint_event(JfrChunkWriter& cw, const u1* data) {
  assert(data != NULL, "invariant");
  const int64_t event_begin = cw.current_offset();
  const int64_t last_checkpoint_event = cw.last_checkpoint_offset();
  const int64_t delta_to_last_checkpoint = last_checkpoint_event == 0 ? 0 : last_checkpoint_event - event_begin;
  const int64_t checkpoint_size = total_size(data);
  write_checkpoint_header(cw, delta_to_last_checkpoint, data);
  write_checkpoint_content(cw, data, checkpoint_size);
  const int64_t event_size = cw.current_offset() - event_begin;
  cw.write_padded_at_offset<u4>(event_size, event_begin);
  cw.set_last_checkpoint_offset(event_begin);
  return (size_t)checkpoint_size;
}

static size_t write_checkpoints(JfrChunkWriter& cw, const u1* data, size_t size) {
  assert(cw.is_valid(), "invariant");
  assert(data != NULL, "invariant");
  assert(size > 0, "invariant");
  const u1* const limit = data + size;
  const u1* next = data;
  size_t processed = 0;
  while (next < limit) {
    const size_t checkpoint_size = write_checkpoint_event(cw, next);
    processed += checkpoint_size;
    next += checkpoint_size;
  }
  assert(next == limit, "invariant");
  return processed;
}

template <typename T>
class CheckpointWriteOp {
 private:
  JfrChunkWriter& _writer;
  size_t _processed;
 public:
  typedef T Type;
  CheckpointWriteOp(JfrChunkWriter& writer) : _writer(writer), _processed(0) {}
  bool write(Type* t, const u1* data, size_t size) {
    _processed += write_checkpoints(_writer, data, size);
    return true;
  }
  size_t processed() const { return _processed; }
};

typedef CheckpointWriteOp<JfrCheckpointManager::Buffer> WriteOperation;
typedef MutexedWriteOp<WriteOperation> MutexedWriteOperation;
typedef ReleaseOpWithExcision<JfrCheckpointMspace, JfrCheckpointMspace::LiveList> ReleaseOperation;
typedef CompositeOperation<MutexedWriteOperation, ReleaseOperation> WriteReleaseOperation;

void JfrCheckpointManager::begin_epoch_shift() {
  assert(SafepointSynchronize::is_at_safepoint(), "invariant");
  JfrTraceIdEpoch::begin_epoch_shift();
}

void JfrCheckpointManager::end_epoch_shift() {
  assert(SafepointSynchronize::is_at_safepoint(), "invariant");
  debug_only(const u1 current_epoch = JfrTraceIdEpoch::current();)
  JfrTraceIdEpoch::end_epoch_shift();
  assert(current_epoch != JfrTraceIdEpoch::current(), "invariant");
}

size_t JfrCheckpointManager::write() {
  assert(_mspace->free_list_is_empty(), "invariant");
  WriteOperation wo(_chunkwriter);
  MutexedWriteOperation mwo(wo);
  ReleaseOperation ro(_mspace, _mspace->live_list(true));
  WriteReleaseOperation wro(&mwo, &ro);
  process_live_list(wro, _mspace, true);
  return wo.processed();
}

typedef DiscardOp<DefaultDiscarder<JfrCheckpointManager::Buffer> > DiscardOperation;
typedef CompositeOperation<DiscardOperation, ReleaseOperation> DiscardReleaseOperation;

size_t JfrCheckpointManager::clear() {
  JfrTraceIdLoadBarrier::clear();
  clear_type_set();
  DiscardOperation discard_operation(mutexed); // mutexed discard mode
  ReleaseOperation ro(_mspace, _mspace->live_list(true));
  DiscardReleaseOperation discard_op(&discard_operation, &ro);
  assert(_mspace->free_list_is_empty(), "invariant");
  process_live_list(discard_op, _mspace, true); // previous epoch list
  return discard_operation.elements();
}

size_t JfrCheckpointManager::write_static_type_set(Thread* thread) {
  assert(thread != NULL, "invariant");
  JfrCheckpointWriter writer(true, thread, STATICS);
  JfrTypeManager::write_static_types(writer);
  return writer.used_size();
}

size_t JfrCheckpointManager::write_threads(Thread* thread) {
  assert(thread != NULL, "invariant");
  JfrCheckpointWriter writer(true, thread, THREADS);
  JfrTypeManager::write_threads(writer);
  return writer.used_size();
}

size_t JfrCheckpointManager::write_static_type_set_and_threads() {
  Thread* const thread = Thread::current();
  ResourceMark rm(thread);
  HandleMark hm(thread);
  write_static_type_set(thread);
  write_threads(thread);
  return write();
}

void JfrCheckpointManager::on_rotation() {
  assert(SafepointSynchronize::is_at_safepoint(), "invariant");
  JfrTypeManager::on_rotation();
  notify_threads();
}

void JfrCheckpointManager::clear_type_set() {
  assert(!SafepointSynchronize::is_at_safepoint(), "invariant");
  assert(!JfrRecorder::is_recording(), "invariant");
  // can safepoint here
  MutexLocker cld_lock(ClassLoaderDataGraph_lock);
  MutexLocker module_lock(Module_lock);
  JfrTypeSet::clear();
}

void JfrCheckpointManager::write_type_set() {
  assert(!SafepointSynchronize::is_at_safepoint(), "invariant");
  Thread* const thread = Thread::current();
  if (LeakProfiler::is_running()) {
    // can safepoint here
    MutexLocker cld_lock(thread, ClassLoaderDataGraph_lock);
    MutexLocker module_lock(thread, Module_lock);
    JfrCheckpointWriter leakp_writer(true, thread);
    JfrCheckpointWriter writer(true, thread);
    JfrTypeSet::serialize(&writer, &leakp_writer, false, false);
    ObjectSampleCheckpoint::on_type_set(leakp_writer);
  } else {
    // can safepoint here
    MutexLocker cld_lock(ClassLoaderDataGraph_lock);
    MutexLocker module_lock(Module_lock);
    JfrCheckpointWriter writer(true, thread);
    JfrTypeSet::serialize(&writer, NULL, false, false);
  }
  write();
}

void JfrCheckpointManager::on_unloading_classes() {
  assert_locked_or_safepoint(ClassLoaderDataGraph_lock);
  JfrCheckpointWriter writer(Thread::current());
  JfrTypeSet::on_unloading_classes(&writer);
  if (LeakProfiler::is_running()) {
    ObjectSampleCheckpoint::on_type_set_unload(writer);
  }
}

size_t JfrCheckpointManager::flush_type_set() {
  size_t elements = 0;
  if (JfrTraceIdEpoch::has_changed_tag_state()) {
    JfrCheckpointWriter writer(Thread::current());
    // can safepoint here
    MutexLocker cld_lock(ClassLoaderDataGraph_lock);
    MutexLocker module_lock(Module_lock);
    elements = JfrTypeSet::serialize(&writer, NULL, false, true);
  }
  if (is_constant_pending()) {
    WriteOperation wo(_chunkwriter);
    MutexedWriteOperation mwo(wo);
    assert(_mspace->live_list_is_nonempty(), "invariant");
    process_live_list(mwo, _mspace);
  }
  return elements;
}

void JfrCheckpointManager::create_thread_blob(Thread* thread) {
  JfrTypeManager::create_thread_blob(thread);
}

void JfrCheckpointManager::write_thread_checkpoint(Thread* thread) {
  JfrTypeManager::write_thread_checkpoint(thread);
}

class JfrNotifyClosure : public ThreadClosure {
 public:
  void do_thread(Thread* thread) {
    assert(thread != NULL, "invariant");
    assert(thread->is_Java_thread(), "invariant");
    assert_locked_or_safepoint(Threads_lock);
    JfrJavaEventWriter::notify((JavaThread*)thread);
  }
};

void JfrCheckpointManager::notify_threads() {
  assert(SafepointSynchronize::is_at_safepoint(), "invariant");
  JfrNotifyClosure tc;
  JfrJavaThreadIterator iter;
  while (iter.has_next()) {
    tc.do_thread(iter.next());
  }
}
