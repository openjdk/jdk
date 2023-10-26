/*
 * Copyright (c) 2016, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "jfr/jni/jfrJavaSupport.hpp"
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
#include "jfr/recorder/storage/jfrEpochStorage.inline.hpp"
#include "jfr/recorder/storage/jfrMemorySpace.inline.hpp"
#include "jfr/recorder/storage/jfrStorageUtils.inline.hpp"
#include "jfr/recorder/stringpool/jfrStringPool.hpp"
#include "jfr/support/jfrKlassUnloading.hpp"
#include "jfr/support/jfrThreadLocal.hpp"
#include "jfr/utilities/jfrBigEndian.hpp"
#include "jfr/utilities/jfrIterator.hpp"
#include "jfr/utilities/jfrLinkedList.inline.hpp"
#include "jfr/utilities/jfrSignal.hpp"
#include "jfr/utilities/jfrThreadIterator.hpp"
#include "jfr/writers/jfrJavaEventWriter.hpp"
#include "logging/log.hpp"
#include "memory/iterator.hpp"
#include "memory/resourceArea.hpp"
#include "runtime/atomic.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/mutex.hpp"
#include "runtime/safepoint.hpp"

typedef JfrCheckpointManager::BufferPtr BufferPtr;
typedef JfrCheckpointManager::ConstBufferPtr ConstBufferPtr;

static JfrSignal _new_checkpoint;
static JfrCheckpointManager* _instance = nullptr;

JfrCheckpointManager& JfrCheckpointManager::instance() {
  return *_instance;
}

JfrCheckpointManager* JfrCheckpointManager::create(JfrChunkWriter& cw) {
  assert(_instance == nullptr, "invariant");
  _instance = new JfrCheckpointManager(cw);
  return _instance;
}

void JfrCheckpointManager::destroy() {
  assert(_instance != nullptr, "invariant");
  delete _instance;
  _instance = nullptr;
}

JfrCheckpointManager::JfrCheckpointManager(JfrChunkWriter& cw) :
  _global_mspace(nullptr),
  _thread_local_mspace(nullptr),
  _virtual_thread_local_mspace(nullptr),
  _chunkwriter(cw) {}

JfrCheckpointManager::~JfrCheckpointManager() {
  JfrTraceIdLoadBarrier::destroy();
  JfrTypeManager::destroy();
  delete _global_mspace;
  delete _thread_local_mspace;
}

static const size_t global_buffer_prealloc_count = 2;
static const size_t global_buffer_size = 512 * K;

static const size_t thread_local_buffer_prealloc_count = 16;
static const size_t thread_local_buffer_size = 256;

static const size_t virtual_thread_local_buffer_prealloc_count = 0;
static const size_t virtual_thread_local_buffer_size = 4 * K;

bool JfrCheckpointManager::initialize() {
  assert(_global_mspace == nullptr, "invariant");
  _global_mspace =  create_mspace<JfrCheckpointMspace, JfrCheckpointManager>(global_buffer_size, 0, 0, false, this); // post-pone preallocation
  if (_global_mspace == nullptr) {
    return false;
  }
  // preallocate buffer count to each of the epoch live lists
  for (size_t i = 0; i < global_buffer_prealloc_count * 2; ++i) {
    Buffer* const buffer = mspace_allocate(global_buffer_size, _global_mspace);
    if (buffer == nullptr) {
      return false;
    }
    _global_mspace->add_to_live_list(buffer, i % 2 == 0);
  }
  assert(_global_mspace->free_list_is_empty(), "invariant");

  assert(_thread_local_mspace == nullptr, "invariant");
  _thread_local_mspace = new JfrThreadLocalCheckpointMspace();
  if (_thread_local_mspace == nullptr || !_thread_local_mspace->initialize(thread_local_buffer_size,
                                                                        thread_local_buffer_prealloc_count,
                                                                        thread_local_buffer_prealloc_count)) {
    return false;
  }

  assert(_virtual_thread_local_mspace == nullptr, "invariant");
  _virtual_thread_local_mspace = new JfrThreadLocalCheckpointMspace();
  if (_virtual_thread_local_mspace == nullptr || !_virtual_thread_local_mspace->initialize(virtual_thread_local_buffer_size,
                                                                                        JFR_MSPACE_UNLIMITED_CACHE_SIZE,
                                                                                        virtual_thread_local_buffer_prealloc_count)) {
    return false;
  }
  return JfrTypeManager::initialize() && JfrTraceIdLoadBarrier::initialize();
}

#ifdef ASSERT
static void assert_lease(ConstBufferPtr buffer) {
  if (buffer == nullptr) {
    return;
  }
  assert(buffer->acquired_by_self(), "invariant");
  assert(buffer->lease(), "invariant");
}

static void assert_release(ConstBufferPtr buffer) {
  assert(buffer != nullptr, "invariant");
  assert(buffer->lease(), "invariant");
  assert(buffer->acquired_by_self(), "invariant");
}

static void assert_retired(ConstBufferPtr buffer, Thread* thread) {
  assert(buffer != nullptr, "invariant");
  assert(buffer->acquired_by(thread), "invariant");
  assert(buffer->retired(), "invariant");
}
#endif // ASSERT

void JfrCheckpointManager::register_full(BufferPtr buffer, Thread* thread) {
  DEBUG_ONLY(assert_retired(buffer, thread);)
  // nothing here at the moment
}

static inline bool is_global(ConstBufferPtr buffer) {
  assert(buffer != nullptr, "invariant");
  return buffer->context() == JFR_GLOBAL;
}

static inline bool is_thread_local(ConstBufferPtr buffer) {
  assert(buffer != nullptr, "invariant");
  return buffer->context() == JFR_THREADLOCAL;
}

static inline bool is_virtual_thread_local(ConstBufferPtr buffer) {
  assert(buffer != nullptr, "invariant");
  return buffer->context() == JFR_VIRTUAL_THREADLOCAL;
}

BufferPtr JfrCheckpointManager::lease_global(Thread* thread, bool previous_epoch /* false */, size_t size /* 0 */) {
  JfrCheckpointMspace* const mspace = instance()._global_mspace;
  assert(mspace != nullptr, "invariant");
  static const size_t max_elem_size = mspace->min_element_size(); // min is max
  BufferPtr buffer;
  if (size <= max_elem_size) {
    buffer = mspace_acquire_live(size, mspace, thread, previous_epoch);
    if (buffer != nullptr) {
      buffer->set_lease();
      DEBUG_ONLY(assert_lease(buffer);)
      return buffer;
    }
  }
  buffer = mspace_allocate_transient_lease_to_live_list(size, mspace, thread, previous_epoch);
  DEBUG_ONLY(assert_lease(buffer);)
  return buffer;
}

BufferPtr JfrCheckpointManager::lease_thread_local(Thread* thread, size_t size) {
  BufferPtr buffer = instance()._thread_local_mspace->acquire(size, thread);
  assert(buffer != nullptr, "invariant");
  assert(buffer->free_size() >= size, "invariant");
  buffer->set_lease();
  DEBUG_ONLY(assert_lease(buffer);)
  buffer->set_context(JFR_THREADLOCAL);
  assert(is_thread_local(buffer), "invariant");
  return buffer;
}

BufferPtr JfrCheckpointManager::get_virtual_thread_local(Thread* thread) {
  assert(thread != nullptr, "invariant");
  return JfrTraceIdEpoch::epoch() ? thread->jfr_thread_local()->_checkpoint_buffer_epoch_1 :
                                    thread->jfr_thread_local()->_checkpoint_buffer_epoch_0;
}

void JfrCheckpointManager::set_virtual_thread_local(Thread* thread, BufferPtr buffer) {
  assert(thread != nullptr, "invariant");
  if (JfrTraceIdEpoch::epoch()) {
    thread->jfr_thread_local()->_checkpoint_buffer_epoch_1 = buffer;
  } else {
    thread->jfr_thread_local()->_checkpoint_buffer_epoch_0 = buffer;
  }
}

BufferPtr JfrCheckpointManager::new_virtual_thread_local(Thread* thread, size_t size) {
  BufferPtr buffer = instance()._virtual_thread_local_mspace->acquire(size, thread);
  assert(buffer != nullptr, "invariant");
  assert(buffer->free_size() >= size, "invariant");
  buffer->set_context(JFR_VIRTUAL_THREADLOCAL);
  assert(is_virtual_thread_local(buffer), "invariant");
  set_virtual_thread_local(thread, buffer);
  return buffer;
}

BufferPtr JfrCheckpointManager::acquire_virtual_thread_local(Thread* thread, size_t size /* 0 */) {
  BufferPtr buffer = get_virtual_thread_local(thread);
  if (buffer == nullptr || buffer->free_size() < size) {
    buffer = new_virtual_thread_local(thread, size);
  }
  assert(buffer->acquired_by_self(), "invariant");
  assert(buffer->free_size() >= size, "invariant");
  assert(get_virtual_thread_local(thread) == buffer, "invariant");
  assert(is_virtual_thread_local(buffer), "invariant");
  return buffer;
}

BufferPtr JfrCheckpointManager::renew(ConstBufferPtr old, Thread* thread, size_t size, JfrCheckpointBufferKind kind /* JFR_THREADLOCAL */) {
  assert(old != nullptr, "invariant");
  assert(old->acquired_by_self(), "invariant");
  if (kind == JFR_GLOBAL) {
    return lease_global(thread, instance()._global_mspace->in_previous_epoch_list(old), size);
  }
  return kind == JFR_THREADLOCAL ? lease_thread_local(thread, size) : acquire_virtual_thread_local(thread, size);
}

BufferPtr JfrCheckpointManager::acquire(Thread* thread, JfrCheckpointBufferKind kind /* JFR_THREADLOCAL */, bool previous_epoch /* false */, size_t size /* 0 */) {
  if (kind == JFR_GLOBAL) {
    return lease_global(thread, previous_epoch, size);
  }
  if (kind == JFR_THREADLOCAL) {
    return lease_thread_local(thread, size);
  }
  assert(kind == JFR_VIRTUAL_THREADLOCAL, "invariant");
  return acquire_virtual_thread_local(thread, size);
}

static inline void retire(BufferPtr buffer) {
  assert(buffer != nullptr, "invariant");
  assert(buffer->acquired_by_self(), "invariant");
  buffer->set_retired();
}

/*
 * The buffer is effectively invalidated for the thread post-return,
 * and the caller should take means to ensure that it is not referenced.
 */
static inline void release(BufferPtr buffer) {
  DEBUG_ONLY(assert_release(buffer);)
  assert(!is_virtual_thread_local(buffer), "invariant");
  if (is_global(buffer)) {
    buffer->release();
    return;
  }
  assert(is_thread_local(buffer), "invariant");
  retire(buffer);
}

static inline JfrCheckpointBufferKind kind(ConstBufferPtr buffer) {
  assert(buffer != nullptr, "invariant");
  return static_cast<JfrCheckpointBufferKind>(buffer->context());
}

BufferPtr JfrCheckpointManager::flush(BufferPtr old, size_t used, size_t requested, Thread* thread) {
  assert(old != nullptr, "invariant");
  if (0 == requested) {
    // indicates a lease is being returned
    assert(old->lease(), "invariant");
    release(old);
    // signal completion of a new checkpoint
    _new_checkpoint.signal();
    return nullptr;
  }
  BufferPtr new_buffer = renew(old, thread, used + requested, kind(old));
  if (new_buffer != nullptr) {
    migrate_outstanding_writes(old, new_buffer, used, requested);
  }
  retire(old);
  return new_buffer;
}

// offsets into the JfrCheckpointEntry
static const size_t starttime_offset = sizeof(int64_t);
static const size_t duration_offset = starttime_offset + sizeof(int64_t);
static const size_t checkpoint_type_offset = duration_offset + sizeof(int64_t);
static const size_t types_offset = checkpoint_type_offset + sizeof(uint32_t);
static const size_t payload_offset = types_offset + sizeof(uint32_t);

template <typename Return>
static Return read_data(const u1* data) {
  return JfrBigEndian::read<Return, Return>(data);
}

static size_t total_size(const u1* data) {
  const int64_t size = read_data<int64_t>(data);
  assert(size > 0, "invariant");
  return static_cast<size_t>(size);
}

static int64_t starttime(const u1* data) {
  return read_data<int64_t>(data + starttime_offset);
}

static int64_t duration(const u1* data) {
  return read_data<int64_t>(data + duration_offset);
}

static int32_t checkpoint_type(const u1* data) {
  return read_data<int32_t>(data + checkpoint_type_offset);
}

static uint32_t number_of_types(const u1* data) {
  return read_data<uint32_t>(data + types_offset);
}

static size_t payload_size(const u1* data) {
  return total_size(data) - sizeof(JfrCheckpointEntry);
}

static uint64_t calculate_event_size_bytes(JfrChunkWriter& cw, const u1* data, int64_t event_begin, int64_t delta_to_last_checkpoint) {
  assert(data != nullptr, "invariant");
  size_t bytes = cw.size_in_bytes(EVENT_CHECKPOINT);
  bytes += cw.size_in_bytes(starttime(data));
  bytes += cw.size_in_bytes(duration(data));
  bytes += cw.size_in_bytes(delta_to_last_checkpoint);
  bytes += cw.size_in_bytes(checkpoint_type(data));
  bytes += cw.size_in_bytes(number_of_types(data));
  bytes += payload_size(data); // in bytes already.
  return bytes + cw.size_in_bytes(bytes + cw.size_in_bytes(bytes));
}

static size_t write_checkpoint_event(JfrChunkWriter& cw, const u1* data) {
  assert(data != nullptr, "invariant");
  const int64_t event_begin = cw.current_offset();
  const int64_t last_checkpoint_event = cw.last_checkpoint_offset();
  cw.set_last_checkpoint_offset(event_begin);
  const int64_t delta_to_last_checkpoint = last_checkpoint_event == 0 ? 0 : last_checkpoint_event - event_begin;
  const uint64_t event_size = calculate_event_size_bytes(cw, data, event_begin, delta_to_last_checkpoint);
  cw.write(event_size);
  cw.write(EVENT_CHECKPOINT);
  cw.write(starttime(data));
  cw.write(duration(data));
  cw.write(delta_to_last_checkpoint);
  cw.write(checkpoint_type(data));
  cw.write(number_of_types(data));
  cw.write_unbuffered(data + payload_offset, payload_size(data));
  assert(static_cast<uint64_t>(cw.current_offset() - event_begin) == event_size, "invariant");
  return total_size(data);
}

static size_t write_checkpoints(JfrChunkWriter& cw, const u1* data, size_t size) {
  assert(cw.is_valid(), "invariant");
  assert(data != nullptr, "invariant");
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

static size_t write_thread_checkpoint_content(JfrChunkWriter& cw, const u1* data) {
  assert(data != nullptr, "invariant");
  const size_t size = total_size(data);
  assert(size > 0, "invariant");
  assert(checkpoint_type(data) == THREADS, "invariant");
  assert(number_of_types(data) == 1, "invariant");
  // Thread checkpoints are small so write them buffered to cache as much as possible before flush.
  cw.write_buffered(data + payload_offset, payload_size(data));
  return size;
}

static size_t write_thread_checkpoint_payloads(JfrChunkWriter& cw, const u1* data, size_t size, u4& elements) {
  assert(cw.is_valid(), "invariant");
  assert(data != nullptr, "invariant");
  assert(size > 0, "invariant");
  const u1* const limit = data + size;
  const u1* next = data;
  size_t processed_total = 0;
  while (next < limit) {
    const size_t processed = write_thread_checkpoint_content(cw, next);
    next += processed;
    processed_total += processed;
    ++elements;
  }
  assert(next == limit, "invariant");
  return processed_total;
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

// This op will collapse all individual vthread checkpoints into a single checkpoint.
template <typename T>
class VirtualThreadLocalCheckpointWriteOp {
 private:
  JfrChunkWriter& _cw;
  int64_t _begin_offset;
  int64_t _elements_offset;
  size_t _processed;
  uint32_t _elements;
 public:
  typedef T Type;
  VirtualThreadLocalCheckpointWriteOp(JfrChunkWriter& cw) : _cw(cw), _begin_offset(cw.current_offset()), _elements_offset(0), _processed(0), _elements(0) {
    const int64_t last_checkpoint = cw.last_checkpoint_offset();
    const int64_t delta = last_checkpoint == 0 ? 0 : last_checkpoint - _begin_offset;
    cw.reserve(sizeof(uint64_t));
    cw.write(EVENT_CHECKPOINT);
    cw.write(JfrTicks::now().value());
    cw.write(0);
    cw.write(delta);
    cw.write(THREADS); // Thread checkpoint type.
    cw.write(1); // Number of types in this checkpoint, only one, TYPE_THREAD.
    cw.write(TYPE_THREAD); // Constant pool type.
    _elements_offset = cw.current_offset(); // Offset for the number of entries in the TYPE_THREAD constant pool.
    cw.reserve(sizeof(uint32_t));
  }

  ~VirtualThreadLocalCheckpointWriteOp() {
    if (_elements == 0) {
      // Rewind.
      _cw.seek(_begin_offset);
      return;
    }
    const int64_t event_size = _cw.current_offset() - _begin_offset;
    _cw.write_padded_at_offset(_elements, _elements_offset);
    _cw.write_padded_at_offset(event_size, _begin_offset);
    _cw.set_last_checkpoint_offset(_begin_offset);
  }

  bool write(Type* t, const u1* data, size_t size) {
    _processed += write_thread_checkpoint_payloads(_cw, data, size, _elements);
    return true;
  }
  size_t elements() const { return _elements; }
  size_t processed() const { return _processed; }
};

typedef CheckpointWriteOp<JfrCheckpointManager::Buffer> WriteOperation;
typedef MutexedWriteOp<WriteOperation> MutexedWriteOperation;
typedef ReleaseWithExcisionOp<JfrCheckpointMspace, JfrCheckpointMspace::LiveList> ReleaseOperation;
typedef CompositeOperation<MutexedWriteOperation, ReleaseOperation> WriteReleaseOperation;
typedef VirtualThreadLocalCheckpointWriteOp<JfrCheckpointManager::Buffer> VirtualThreadLocalCheckpointOperation;
typedef MutexedWriteOp<VirtualThreadLocalCheckpointOperation> VirtualThreadLocalWriteOperation;

void JfrCheckpointManager::begin_epoch_shift() {
  assert(SafepointSynchronize::is_at_safepoint(), "invariant");
  JfrTraceIdEpoch::begin_epoch_shift();
}

void JfrCheckpointManager::end_epoch_shift() {
  assert(SafepointSynchronize::is_at_safepoint(), "invariant");
  debug_only(const u1 current_epoch = JfrTraceIdEpoch::current();)
  JfrTraceIdEpoch::end_epoch_shift();
  assert(current_epoch != JfrTraceIdEpoch::current(), "invariant");
  JfrStringPool::on_epoch_shift();
}

size_t JfrCheckpointManager::write() {
  DEBUG_ONLY(JfrJavaSupport::check_java_thread_in_native(JavaThread::current()));
  WriteOperation wo(_chunkwriter);
  MutexedWriteOperation mwo(wo);
  _thread_local_mspace->iterate(mwo, true); // previous epoch list
  assert(_global_mspace->free_list_is_empty(), "invariant");
  ReleaseOperation ro(_global_mspace, _global_mspace->live_list(true)); // previous epoch list
  WriteReleaseOperation wro(&mwo, &ro);
  process_live_list(wro, _global_mspace, true); // previous epoch list
  // Do virtual thread local list last. Careful, the vtlco destructor writes to chunk.
  VirtualThreadLocalCheckpointOperation vtlco(_chunkwriter);
  VirtualThreadLocalWriteOperation vtlwo(vtlco);
  _virtual_thread_local_mspace->iterate(vtlwo, true); // previous epoch list
  return wo.processed() + vtlco.processed();
}

typedef DiscardOp<DefaultDiscarder<JfrCheckpointManager::Buffer> > DiscardOperation;
typedef CompositeOperation<DiscardOperation, ReleaseOperation> DiscardReleaseOperation;

size_t JfrCheckpointManager::clear() {
  JfrTraceIdLoadBarrier::clear();
  clear_type_set();
  DiscardOperation dop(mutexed); // mutexed discard mode
  _thread_local_mspace->iterate(dop, true); // previous epoch list
  _virtual_thread_local_mspace->iterate(dop, true); // previous epoch list
  ReleaseOperation ro(_global_mspace, _global_mspace->live_list(true)); // previous epoch list
  DiscardReleaseOperation dro(&dop, &ro);
  assert(_global_mspace->free_list_is_empty(), "invariant");
  process_live_list(dro, _global_mspace, true); // previous epoch list
  return dop.elements();
}

size_t JfrCheckpointManager::write_static_type_set(Thread* thread) {
  assert(thread != nullptr, "invariant");
  JfrCheckpointWriter writer(true, thread, STATICS);
  JfrTypeManager::write_static_types(writer);
  return writer.used_size();
}

size_t JfrCheckpointManager::write_threads(JavaThread* thread) {
  assert(thread != nullptr, "invariant");
  // can safepoint here
  ThreadInVMfromNative transition(thread);
  ResourceMark rm(thread);
  HandleMark hm(thread);
  JfrCheckpointWriter writer(true, thread, THREADS);
  JfrTypeManager::write_threads(writer);
  return writer.used_size();
}

size_t JfrCheckpointManager::write_static_type_set_and_threads() {
  JavaThread* const thread = JavaThread::current();
  DEBUG_ONLY(JfrJavaSupport::check_java_thread_in_native(thread));
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
  assert(!JfrRecorder::is_recording(), "invariant");
  JavaThread* t = JavaThread::current();
  DEBUG_ONLY(JfrJavaSupport::check_java_thread_in_native(t));
  // can safepoint here
  ThreadInVMfromNative transition(t);
  MutexLocker cld_lock(ClassLoaderDataGraph_lock);
  MutexLocker module_lock(Module_lock);
  JfrTypeSet::clear();
}

void JfrCheckpointManager::write_type_set() {
  {
    JavaThread* const thread = JavaThread::current();
    DEBUG_ONLY(JfrJavaSupport::check_java_thread_in_native(thread));
    // can safepoint here
    ThreadInVMfromNative transition(thread);
    MutexLocker cld_lock(thread, ClassLoaderDataGraph_lock);
    MutexLocker module_lock(thread, Module_lock);
    if (LeakProfiler::is_running()) {
      JfrCheckpointWriter leakp_writer(true, thread);
      JfrCheckpointWriter writer(true, thread);
      JfrTypeSet::serialize(&writer, &leakp_writer, false, false);
      ObjectSampleCheckpoint::on_type_set(leakp_writer);
    } else {
      JfrCheckpointWriter writer(true, thread);
      JfrTypeSet::serialize(&writer, nullptr, false, false);
    }
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

static size_t flush_type_set(Thread* thread) {
  assert(thread != nullptr, "invariant");
  JfrCheckpointWriter writer(thread);
  MutexLocker cld_lock(thread, ClassLoaderDataGraph_lock);
  MutexLocker module_lock(thread, Module_lock);
  return JfrTypeSet::serialize(&writer, nullptr, false, true);
}

size_t JfrCheckpointManager::flush_type_set() {
  size_t elements = 0;
  if (JfrTraceIdEpoch::has_changed_tag_state()) {
    Thread* const thread = Thread::current();
    if (thread->is_Java_thread()) {
      // can safepoint here
      ThreadInVMfromNative transition(JavaThread::cast(thread));
      elements = ::flush_type_set(thread);
    } else {
      elements = ::flush_type_set(thread);
    }
  }
  if (_new_checkpoint.is_signaled_with_reset()) {
    WriteOperation wo(_chunkwriter);
    MutexedWriteOperation mwo(wo);
    _thread_local_mspace->iterate(mwo); // current epoch list
    assert(_global_mspace->free_list_is_empty(), "invariant");
    assert(_global_mspace->live_list_is_nonempty(), "invariant");
    process_live_list(mwo, _global_mspace); // current epoch list
    // Do virtual thread local list last. Careful, the vtlco destructor writes to chunk.
    VirtualThreadLocalCheckpointOperation vtlco(_chunkwriter);
    VirtualThreadLocalWriteOperation vtlwo(vtlco);
    _virtual_thread_local_mspace->iterate(vtlwo); // current epoch list
  }
  return elements;
}

JfrBlobHandle JfrCheckpointManager::create_thread_blob(JavaThread* jt, traceid tid /* 0 */, oop vthread /* nullptr */) {
  assert(jt != nullptr, "invariant");
  assert(Thread::current() == jt, "invariant");
  DEBUG_ONLY(JfrJavaSupport::check_java_thread_in_vm(jt));
  return JfrTypeManager::create_thread_blob(jt, tid, vthread);
}

void JfrCheckpointManager::write_checkpoint(Thread* thread, traceid tid /* 0 */, oop vthread /* nullptr */) {
  JfrTypeManager::write_checkpoint(thread, tid, vthread);
}

class JfrNotifyClosure : public ThreadClosure {
 public:
  void do_thread(Thread* thread) {
    assert(thread != nullptr, "invariant");
    assert_locked_or_safepoint(Threads_lock);
    JfrJavaEventWriter::notify(JavaThread::cast(thread));
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
