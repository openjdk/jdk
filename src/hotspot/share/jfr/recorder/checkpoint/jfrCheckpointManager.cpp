/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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
#include "jfr/recorder/jfrRecorder.hpp"
#include "jfr/recorder/checkpoint/jfrCheckpointManager.hpp"
#include "jfr/recorder/checkpoint/jfrCheckpointWriter.hpp"
#include "jfr/recorder/checkpoint/types/jfrTypeManager.hpp"
#include "jfr/recorder/checkpoint/types/traceid/jfrTraceIdEpoch.hpp"
#include "jfr/recorder/service/jfrOptionSet.hpp"
#include "jfr/recorder/storage/jfrMemorySpace.inline.hpp"
#include "jfr/recorder/storage/jfrStorageUtils.inline.hpp"
#include "jfr/recorder/repository/jfrChunkWriter.hpp"
#include "jfr/utilities/jfrBigEndian.hpp"
#include "jfr/utilities/jfrTypes.hpp"
#include "logging/log.hpp"
#include "memory/resourceArea.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/orderAccess.hpp"
#include "runtime/os.inline.hpp"
#include "runtime/safepoint.hpp"

typedef JfrCheckpointManager::Buffer* BufferPtr;

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
  _lock(NULL),
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
  if (_lock != NULL) {
    delete _lock;
  }
  JfrTypeManager::clear();
}

static const size_t unlimited_mspace_size = 0;
static const size_t checkpoint_buffer_cache_count = 2;
static const size_t checkpoint_buffer_size = 512 * K;

static JfrCheckpointMspace* create_mspace(size_t buffer_size, size_t limit, size_t cache_count, JfrCheckpointManager* system) {
  JfrCheckpointMspace* mspace = new JfrCheckpointMspace(buffer_size, limit, cache_count, system);
  if (mspace != NULL) {
    mspace->initialize();
  }
  return mspace;
}

bool JfrCheckpointManager::initialize() {
  assert(_free_list_mspace == NULL, "invariant");
  _free_list_mspace = create_mspace(checkpoint_buffer_size, unlimited_mspace_size, checkpoint_buffer_cache_count, this);
  if (_free_list_mspace == NULL) {
    return false;
  }
  assert(_epoch_transition_mspace == NULL, "invariant");
  _epoch_transition_mspace = create_mspace(checkpoint_buffer_size, unlimited_mspace_size, checkpoint_buffer_cache_count, this);
  if (_epoch_transition_mspace == NULL) {
    return false;
  }
  assert(_lock == NULL, "invariant");
  _lock = new Mutex(Monitor::leaf - 1, "Checkpoint mutex", Mutex::_allow_vm_block_flag, Monitor::_safepoint_check_never);
  if (_lock == NULL) {
    return false;
  }
  return JfrTypeManager::initialize();
}

bool JfrCheckpointManager::use_epoch_transition_mspace(const Thread* thread) const {
  return _service_thread != thread && OrderAccess::load_acquire(&_checkpoint_epoch_state) != JfrTraceIdEpoch::epoch();
}

void JfrCheckpointManager::synchronize_epoch() {
  assert(_checkpoint_epoch_state != JfrTraceIdEpoch::epoch(), "invariant");
  OrderAccess::storestore();
  _checkpoint_epoch_state = JfrTraceIdEpoch::epoch();
}

void JfrCheckpointManager::shift_epoch() {
  debug_only(const u1 current_epoch = JfrTraceIdEpoch::current();)
  JfrTraceIdEpoch::shift_epoch();
  assert(current_epoch != JfrTraceIdEpoch::current(), "invariant");
}

void JfrCheckpointManager::register_service_thread(const Thread* thread) {
  _service_thread = thread;
}

void JfrCheckpointManager::register_full(BufferPtr t, Thread* thread) {
  // nothing here at the moment
  assert(t->retired(), "invariant");
}

void JfrCheckpointManager::lock() {
  assert(!_lock->owned_by_self(), "invariant");
  _lock->lock_without_safepoint_check();
}

void JfrCheckpointManager::unlock() {
  _lock->unlock();
}

#ifdef ASSERT

bool JfrCheckpointManager::is_locked() const {
  return _lock->owned_by_self();
}

static void assert_free_lease(const BufferPtr buffer) {
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

static BufferPtr lease_free(size_t size, JfrCheckpointMspace* mspace, size_t retry_count, Thread* thread) {
  static const size_t max_elem_size = mspace->min_elem_size(); // min is max
  BufferPtr buffer;
  if (size <= max_elem_size) {
    BufferPtr buffer = mspace_get_free_lease_with_retry(size, mspace, retry_count, thread);
    if (buffer != NULL) {
      DEBUG_ONLY(assert_free_lease(buffer);)
      return buffer;
    }
  }
  buffer = mspace_allocate_transient_lease_to_free(size, mspace, thread);
  DEBUG_ONLY(assert_free_lease(buffer);)
  return buffer;
}

static const size_t lease_retry = 10;

BufferPtr JfrCheckpointManager::lease_buffer(Thread* thread, size_t size /* 0 */) {
  JfrCheckpointManager& manager = instance();
  if (manager.use_epoch_transition_mspace(thread)) {
    return lease_free(size, manager._epoch_transition_mspace, lease_retry, thread);
  }
  return lease_free(size, manager._free_list_mspace, lease_retry, thread);
}

/*
* If the buffer was a "lease" from the free list, release back.
*
* The buffer is effectively invalidated for the thread post-return,
* and the caller should take means to ensure that it is not referenced.
*/
static void release(BufferPtr const buffer, Thread* thread) {
  DEBUG_ONLY(assert_release(buffer);)
  buffer->clear_lease();
  buffer->release();
}

BufferPtr JfrCheckpointManager::flush(BufferPtr old, size_t used, size_t requested, Thread* thread) {
  assert(old != NULL, "invariant");
  assert(old->lease(), "invariant");
  if (0 == requested) {
    // indicates a lease is being returned
    release(old, thread);
    return NULL;
  }
  // migration of in-flight information
  BufferPtr const new_buffer = lease_buffer(thread, used + requested);
  if (new_buffer != NULL) {
    migrate_outstanding_writes(old, new_buffer, used, requested);
  }
  release(old, thread);
  return new_buffer; // might be NULL
}

// offsets into the JfrCheckpointEntry
static const juint starttime_offset = sizeof(jlong);
static const juint duration_offset = starttime_offset + sizeof(jlong);
static const juint flushpoint_offset = duration_offset + sizeof(jlong);
static const juint types_offset = flushpoint_offset + sizeof(juint);
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

static bool is_flushpoint(const u1* data) {
  return read_data<juint>(data + flushpoint_offset) == (juint)1;
}

static juint number_of_types(const u1* data) {
  return read_data<juint>(data + types_offset);
}

static void write_checkpoint_header(JfrChunkWriter& cw, intptr_t offset_prev_cp_event, const u1* data) {
  cw.reserve(sizeof(u4));
  cw.write((u8)EVENT_CHECKPOINT);
  cw.write(starttime(data));
  cw.write(duration(data));
  cw.write((jlong)offset_prev_cp_event);
  cw.write(is_flushpoint(data));
  cw.write(number_of_types(data));
}

static void write_checkpoint_content(JfrChunkWriter& cw, const u1* data, size_t size) {
  assert(data != NULL, "invariant");
  cw.write_unbuffered(data + payload_offset, size);
}

static size_t write_checkpoint_event(JfrChunkWriter& cw, const u1* data) {
  assert(data != NULL, "invariant");
  const intptr_t previous_checkpoint_event = cw.previous_checkpoint_offset();
  const intptr_t event_begin = cw.current_offset();
  const intptr_t offset_to_previous_checkpoint_event = 0 == previous_checkpoint_event ? 0 : previous_checkpoint_event - event_begin;
  const jlong total_checkpoint_size = total_size(data);
  write_checkpoint_header(cw, offset_to_previous_checkpoint_event, data);
  write_checkpoint_content(cw, data, total_checkpoint_size - sizeof(JfrCheckpointEntry));
  const jlong checkpoint_event_size = cw.current_offset() - event_begin;
  cw.write_padded_at_offset<u4>(checkpoint_event_size, event_begin);
  cw.set_previous_checkpoint_offset(event_begin);
  return (size_t)total_checkpoint_size;
}

static size_t write_checkpoints(JfrChunkWriter& cw, const u1* data, size_t size) {
  assert(cw.is_valid(), "invariant");
  assert(data != NULL, "invariant");
  assert(size > 0, "invariant");
  const u1* const limit = data + size;
  const u1* next_entry = data;
  size_t processed = 0;
  while (next_entry < limit) {
    const size_t checkpoint_size = write_checkpoint_event(cw, next_entry);
    processed += checkpoint_size;
    next_entry += checkpoint_size;
  }
  assert(next_entry == limit, "invariant");
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

typedef CheckpointWriteOp<JfrCheckpointMspace::Type> WriteOperation;
typedef MutexedWriteOp<WriteOperation> MutexedWriteOperation;
typedef ReleaseOp<JfrCheckpointMspace> CheckpointReleaseOperation;
typedef CompositeOperation<MutexedWriteOperation, CheckpointReleaseOperation> CheckpointWriteOperation;

static size_t write_mspace_exclusive(JfrCheckpointMspace* mspace, JfrChunkWriter& chunkwriter) {
  Thread* const thread = Thread::current();
  WriteOperation wo(chunkwriter);
  MutexedWriteOperation mwo(wo);
  CheckpointReleaseOperation cro(mspace, thread, false);
  CheckpointWriteOperation cpwo(&mwo, &cro);
  assert(mspace->is_full_empty(), "invariant");
  process_free_list(cpwo, mspace);
  return wo.processed();
}

size_t JfrCheckpointManager::write() {
  const size_t processed = write_mspace_exclusive(_free_list_mspace, _chunkwriter);
  synchronize_epoch();
  return processed;
}

size_t JfrCheckpointManager::write_epoch_transition_mspace() {
  return write_mspace_exclusive(_epoch_transition_mspace, _chunkwriter);
}

typedef DiscardOp<DefaultDiscarder<JfrBuffer> > DiscardOperation;
size_t JfrCheckpointManager::clear() {
  DiscardOperation discarder(mutexed); // mutexed discard mode
  process_free_list(discarder, _free_list_mspace);
  process_free_list(discarder, _epoch_transition_mspace);
  synchronize_epoch();
  return discarder.processed();
}

size_t JfrCheckpointManager::write_types() {
  JfrCheckpointWriter writer(false, true, Thread::current());
  JfrTypeManager::write_types(writer);
  return writer.used_size();
}

size_t JfrCheckpointManager::write_safepoint_types() {
  // this is also a "flushpoint"
  JfrCheckpointWriter writer(true, true, Thread::current());
  JfrTypeManager::write_safepoint_types(writer);
  return writer.used_size();
}

void JfrCheckpointManager::write_type_set() {
  JfrTypeManager::write_type_set();
}

void JfrCheckpointManager::write_type_set_for_unloaded_classes() {
  assert_locked_or_safepoint(ClassLoaderDataGraph_lock);
  JfrTypeManager::write_type_set_for_unloaded_classes();
}

void JfrCheckpointManager::create_thread_checkpoint(JavaThread* jt) {
  JfrTypeManager::create_thread_checkpoint(jt);
}

void JfrCheckpointManager::write_thread_checkpoint(JavaThread* jt) {
  JfrTypeManager::write_thread_checkpoint(jt);
}
