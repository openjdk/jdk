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
  _free_list_mspace(NULL),
  _epoch_transition_mspace(NULL),
  _service_thread(NULL),
  _chunkwriter(cw),
  _checkpoint_epoch_state(JfrTraceIdEpoch::epoch()) {}

JfrCheckpointManager::~JfrCheckpointManager() {
  if (_free_list_mspace != NULL) {
    delete _free_list_mspace;
  }
  if (_epoch_transition_mspace != NULL) {
    delete _epoch_transition_mspace;
  }
  JfrTypeManager::destroy();
}

static const size_t unlimited_mspace_size = 0;
static const size_t checkpoint_buffer_cache_count = 2;
static const size_t checkpoint_buffer_size = 512 * K;

static JfrCheckpointMspace* allocate_mspace(size_t size, size_t limit, size_t cache_count, JfrCheckpointManager* mgr) {
  return create_mspace<JfrCheckpointMspace, JfrCheckpointManager>(size, limit, cache_count, mgr);
}

bool JfrCheckpointManager::initialize() {
  assert(_free_list_mspace == NULL, "invariant");
  _free_list_mspace = allocate_mspace(checkpoint_buffer_size, unlimited_mspace_size, checkpoint_buffer_cache_count, this);
  if (_free_list_mspace == NULL) {
    return false;
  }
  assert(_epoch_transition_mspace == NULL, "invariant");
  _epoch_transition_mspace = allocate_mspace(checkpoint_buffer_size, unlimited_mspace_size, checkpoint_buffer_cache_count, this);
  if (_epoch_transition_mspace == NULL) {
    return false;
  }
  return JfrTypeManager::initialize();
}

void JfrCheckpointManager::register_service_thread(const Thread* thread) {
  _service_thread = thread;
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

bool JfrCheckpointManager::use_epoch_transition_mspace(const Thread* thread) const {
  return _service_thread != thread && Atomic::load_acquire(&_checkpoint_epoch_state) != JfrTraceIdEpoch::epoch();
}

static const size_t lease_retry = 10;

BufferPtr JfrCheckpointManager::lease(JfrCheckpointMspace* mspace, Thread* thread, size_t size /* 0 */) {
  assert(mspace != NULL, "invariant");
  static const size_t max_elem_size = mspace->min_elem_size(); // min is max
  BufferPtr buffer;
  if (size <= max_elem_size) {
    buffer = mspace_get_free_lease_with_retry(size, mspace, lease_retry, thread);
    if (buffer != NULL) {
      DEBUG_ONLY(assert_lease(buffer);)
      return buffer;
    }
  }
  buffer = mspace_allocate_transient_lease_to_full(size, mspace, thread);
  DEBUG_ONLY(assert_lease(buffer);)
  return buffer;
}

BufferPtr JfrCheckpointManager::lease(Thread* thread, size_t size /* 0 */) {
  JfrCheckpointManager& manager = instance();
  JfrCheckpointMspace* const mspace = manager.use_epoch_transition_mspace(thread) ?
                                        manager._epoch_transition_mspace :
                                          manager._free_list_mspace;
  return lease(mspace, thread, size);
}

JfrCheckpointMspace* JfrCheckpointManager::lookup(BufferPtr old) const {
  assert(old != NULL, "invariant");
  return _free_list_mspace->in_mspace(old) ? _free_list_mspace : _epoch_transition_mspace;
}

BufferPtr JfrCheckpointManager::lease(BufferPtr old, Thread* thread, size_t size /* 0 */) {
  assert(old != NULL, "invariant");
  JfrCheckpointMspace* mspace = instance().lookup(old);
  assert(mspace != NULL, "invariant");
  return lease(mspace, thread, size);
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
typedef ReleaseOp<JfrCheckpointMspace> CheckpointReleaseFreeOperation;
typedef ScavengingReleaseOp<JfrCheckpointMspace> CheckpointReleaseFullOperation;

template <template <typename> class WriterHost>
static size_t write_mspace(JfrCheckpointMspace* mspace, JfrChunkWriter& chunkwriter) {
  assert(mspace != NULL, "invariant");
  WriteOperation wo(chunkwriter);
  WriterHost<WriteOperation> wh(wo);
  CheckpointReleaseFreeOperation free_release_op(mspace);
  CompositeOperation<WriterHost<WriteOperation>, CheckpointReleaseFreeOperation> free_op(&wh, &free_release_op);
  process_free_list(free_op, mspace);
  CheckpointReleaseFullOperation full_release_op(mspace);
  MutexedWriteOp<WriteOperation> full_write_op(wo);
  CompositeOperation<MutexedWriteOp<WriteOperation>, CheckpointReleaseFullOperation> full_op(&full_write_op, &full_release_op);
  process_full_list(full_op, mspace);
  return wo.processed();
}

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

void JfrCheckpointManager::synchronize_checkpoint_manager_with_current_epoch() {
  assert(_checkpoint_epoch_state != JfrTraceIdEpoch::epoch(), "invariant");
  OrderAccess::storestore();
  _checkpoint_epoch_state = JfrTraceIdEpoch::epoch();
}

size_t JfrCheckpointManager::write() {
  const size_t processed = write_mspace<MutexedWriteOp>(_free_list_mspace, _chunkwriter);
  synchronize_checkpoint_manager_with_current_epoch();
  return processed;
}

size_t JfrCheckpointManager::write_epoch_transition_mspace() {
  return write_mspace<ExclusiveOp>(_epoch_transition_mspace, _chunkwriter);
}

typedef DiscardOp<DefaultDiscarder<JfrCheckpointManager::Buffer> > DiscardOperation;
typedef ExclusiveDiscardOp<DefaultDiscarder<JfrCheckpointManager::Buffer> > DiscardOperationEpochTransitionMspace;
typedef CompositeOperation<DiscardOperation, CheckpointReleaseFreeOperation> DiscardFreeOperation;
typedef CompositeOperation<DiscardOperation, CheckpointReleaseFullOperation> DiscardFullOperation;
typedef CompositeOperation<DiscardOperationEpochTransitionMspace, CheckpointReleaseFreeOperation> DiscardEpochTransMspaceFreeOperation;
typedef CompositeOperation<DiscardOperationEpochTransitionMspace, CheckpointReleaseFullOperation> DiscardEpochTransMspaceFullOperation;

size_t JfrCheckpointManager::clear() {
  clear_type_set();
  DiscardOperation mutex_discarder(mutexed);
  CheckpointReleaseFreeOperation free_release_op(_free_list_mspace);
  DiscardFreeOperation free_op(&mutex_discarder, &free_release_op);
  process_free_list(free_op, _free_list_mspace);
  CheckpointReleaseFullOperation full_release_op(_free_list_mspace);
  DiscardFullOperation full_op(&mutex_discarder, &full_release_op);
  process_full_list(full_op, _free_list_mspace);
  DiscardOperationEpochTransitionMspace epoch_transition_discarder(mutexed);
  CheckpointReleaseFreeOperation epoch_free_release_op(_epoch_transition_mspace);
  DiscardEpochTransMspaceFreeOperation epoch_free_op(&epoch_transition_discarder, &epoch_free_release_op);
  process_free_list(epoch_free_op, _epoch_transition_mspace);
  CheckpointReleaseFullOperation epoch_full_release_op(_epoch_transition_mspace);
  DiscardEpochTransMspaceFullOperation epoch_full_op(&epoch_transition_discarder, &epoch_full_release_op);
  process_full_list(epoch_full_op, _epoch_transition_mspace);
  synchronize_checkpoint_manager_with_current_epoch();
  return mutex_discarder.elements() + epoch_transition_discarder.elements();
}

// Optimization for write_static_type_set() and write_threads() is to write
// directly into the epoch transition mspace because we will immediately
// serialize and reset this mspace post-write.
BufferPtr JfrCheckpointManager::epoch_transition_buffer(Thread* thread) {
  assert(_epoch_transition_mspace->free_list_is_nonempty(), "invariant");
  BufferPtr const buffer = lease(_epoch_transition_mspace, thread, _epoch_transition_mspace->min_elem_size());
  DEBUG_ONLY(assert_lease(buffer);)
  return buffer;
}

size_t JfrCheckpointManager::write_static_type_set() {
  Thread* const thread = Thread::current();
  ResourceMark rm(thread);
  HandleMark hm(thread);
  JfrCheckpointWriter writer(thread, epoch_transition_buffer(thread), STATICS);
  JfrTypeManager::write_static_types(writer);
  return writer.used_size();
}

size_t JfrCheckpointManager::write_threads() {
  Thread* const thread = Thread::current();
  ResourceMark rm(thread);
  HandleMark hm(thread);
  JfrCheckpointWriter writer(thread, epoch_transition_buffer(thread), THREADS);
  JfrTypeManager::write_threads(writer);
  return writer.used_size();
}

size_t JfrCheckpointManager::write_static_type_set_and_threads() {
  write_static_type_set();
  write_threads();
  return write_epoch_transition_mspace();
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
    JfrCheckpointWriter leakp_writer(thread);
    JfrCheckpointWriter writer(thread);
    JfrTypeSet::serialize(&writer, &leakp_writer, false, false);
    ObjectSampleCheckpoint::on_type_set(leakp_writer);
  } else {
    // can safepoint here
    MutexLocker cld_lock(ClassLoaderDataGraph_lock);
    MutexLocker module_lock(Module_lock);
    JfrCheckpointWriter writer(thread);
    JfrTypeSet::serialize(&writer, NULL, false, false);
  }
  write();
}

void JfrCheckpointManager::write_type_set_for_unloaded_classes() {
  assert_locked_or_safepoint(ClassLoaderDataGraph_lock);
  JfrCheckpointWriter writer(Thread::current());
  const JfrCheckpointContext ctx = writer.context();
  JfrTypeSet::serialize(&writer, NULL, true, false);
  if (LeakProfiler::is_running()) {
    ObjectSampleCheckpoint::on_type_set_unload(writer);
  }
  if (!JfrRecorder::is_recording()) {
    // discard by rewind
    writer.set_context(ctx);
  }
}

typedef MutexedWriteOp<WriteOperation> FlushOperation;

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
    FlushOperation fo(wo);
    process_free_list(fo, _free_list_mspace);
    process_full_list(fo, _free_list_mspace);
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
