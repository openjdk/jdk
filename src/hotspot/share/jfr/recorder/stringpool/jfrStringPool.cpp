/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates. All rights reserved.
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
#include "jfr/recorder/checkpoint/types/traceid/jfrTraceIdEpoch.hpp"
#include "jfr/recorder/service/jfrOptionSet.hpp"
#include "jfr/recorder/storage/jfrMemorySpace.inline.hpp"
#include "jfr/recorder/repository/jfrChunkWriter.hpp"
#include "jfr/recorder/storage/jfrStorageUtils.inline.hpp"
#include "jfr/recorder/stringpool/jfrStringPool.hpp"
#include "jfr/recorder/stringpool/jfrStringPoolWriter.hpp"
#include "jfr/utilities/jfrTypes.hpp"
#include "logging/log.hpp"
#include "runtime/atomic.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/orderAccess.hpp"
#include "runtime/safepoint.hpp"
#include "runtime/thread.inline.hpp"

typedef JfrStringPool::Buffer* BufferPtr;

static JfrStringPool* _instance = NULL;

JfrStringPool& JfrStringPool::instance() {
  return *_instance;
}

JfrStringPool* JfrStringPool::create(JfrChunkWriter& cw) {
  assert(_instance == NULL, "invariant");
  _instance = new JfrStringPool(cw);
  return _instance;
}

void JfrStringPool::destroy() {
  assert(_instance != NULL, "invariant");
  delete _instance;
  _instance = NULL;
}

JfrStringPool::JfrStringPool(JfrChunkWriter& cw) : _free_list_mspace(NULL), _lock(NULL), _chunkwriter(cw) {}

JfrStringPool::~JfrStringPool() {
  if (_free_list_mspace != NULL) {
    delete _free_list_mspace;
  }
  if (_lock != NULL) {
    delete _lock;
  }
}

static const size_t unlimited_mspace_size = 0;
static const size_t string_pool_cache_count = 2;
static const size_t string_pool_buffer_size = 512 * K;

bool JfrStringPool::initialize() {
  assert(_free_list_mspace == NULL, "invariant");
  _free_list_mspace = new JfrStringPoolMspace(string_pool_buffer_size, unlimited_mspace_size, string_pool_cache_count, this);
  if (_free_list_mspace == NULL || !_free_list_mspace->initialize()) {
    return false;
  }
  assert(_lock == NULL, "invariant");
  _lock = new Mutex(Monitor::leaf - 1, "Checkpoint mutex", Mutex::_allow_vm_block_flag, Monitor::_safepoint_check_never);
  return _lock != NULL;
}

/*
* If the buffer was a "lease" from the global system, release back.
*
* The buffer is effectively invalidated for the thread post-return,
* and the caller should take means to ensure that it is not referenced any longer.
*/
static void release(BufferPtr buffer, Thread* thread) {
  assert(buffer != NULL, "invariant");
  assert(buffer->lease(), "invariant");
  assert(buffer->acquired_by_self(), "invariant");
  buffer->clear_lease();
  buffer->release();
}

BufferPtr JfrStringPool::flush(BufferPtr old, size_t used, size_t requested, Thread* thread) {
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

static const size_t lease_retry = 10;

BufferPtr JfrStringPool::lease_buffer(Thread* thread, size_t size /* 0 */) {
  BufferPtr buffer = mspace_get_free_lease_with_retry(size, instance()._free_list_mspace, lease_retry, thread);
  if (buffer == NULL) {
    buffer = mspace_allocate_transient_lease_to_free(size,  instance()._free_list_mspace, thread);
  }
  assert(buffer->acquired_by_self(), "invariant");
  assert(buffer->lease(), "invariant");
  return buffer;
}

bool JfrStringPool::add(bool epoch, jlong id, jstring string, JavaThread* jt) {
  assert(jt != NULL, "invariant");
  const bool current_epoch = JfrTraceIdEpoch::epoch();
  if (current_epoch == epoch) {
    JfrStringPoolWriter writer(jt);
    writer.write(id);
    writer.write(string);
    writer.inc_nof_strings();
  }
  return current_epoch;
}

template <template <typename> class Operation>
class StringPoolOp {
 public:
  typedef JfrStringPoolBuffer Type;
 private:
  Operation<Type> _op;
  Thread* _thread;
  size_t _strings_processed;
 public:
  StringPoolOp() : _op(), _thread(Thread::current()), _strings_processed(0) {}
  StringPoolOp(JfrChunkWriter& writer, Thread* thread) : _op(writer), _thread(thread), _strings_processed(0) {}
  bool write(Type* buffer, const u1* data, size_t size) {
    assert(buffer->acquired_by(_thread) || buffer->retired(), "invariant");
    const uint64_t nof_strings_used = buffer->string_count();
    assert(nof_strings_used > 0, "invariant");
    buffer->set_string_top(buffer->string_top() + nof_strings_used);
    // "size processed" for string pool buffers is the number of processed string elements
    _strings_processed += nof_strings_used;
    return _op.write(buffer, data, size);
  }
  size_t processed() { return _strings_processed; }
};

template <typename Type>
class StringPoolDiscarderStub {
 public:
  bool write(Type* buffer, const u1* data, size_t size) {
    // stub only, discard happens at higher level
    return true;
  }
};

typedef StringPoolOp<UnBufferedWriteToChunk> WriteOperation;
typedef StringPoolOp<StringPoolDiscarderStub> DiscardOperation;
typedef ExclusiveOp<WriteOperation> ExclusiveWriteOperation;
typedef ExclusiveOp<DiscardOperation> ExclusiveDiscardOperation;
typedef ReleaseOp<JfrStringPoolMspace> StringPoolReleaseOperation;
typedef CompositeOperation<ExclusiveWriteOperation, StringPoolReleaseOperation> StringPoolWriteOperation;
typedef CompositeOperation<ExclusiveDiscardOperation, StringPoolReleaseOperation> StringPoolDiscardOperation;

size_t JfrStringPool::write() {
  Thread* const thread = Thread::current();
  WriteOperation wo(_chunkwriter, thread);
  ExclusiveWriteOperation ewo(wo);
  StringPoolReleaseOperation spro(_free_list_mspace, thread, false);
  StringPoolWriteOperation spwo(&ewo, &spro);
  assert(_free_list_mspace->is_full_empty(), "invariant");
  process_free_list(spwo, _free_list_mspace);
  return wo.processed();
}

size_t JfrStringPool::write_at_safepoint() {
  assert(SafepointSynchronize::is_at_safepoint(), "invariant");
  return write();
}

size_t JfrStringPool::clear() {
  DiscardOperation discard_operation;
  ExclusiveDiscardOperation edo(discard_operation);
  StringPoolReleaseOperation spro(_free_list_mspace, Thread::current(), false);
  StringPoolDiscardOperation spdo(&edo, &spro);
  assert(_free_list_mspace->is_full_empty(), "invariant");
  process_free_list(spdo, _free_list_mspace);
  return discard_operation.processed();
}

void JfrStringPool::register_full(BufferPtr t, Thread* thread) {
  // nothing here at the moment
  assert(t != NULL, "invariant");
  assert(t->acquired_by(thread), "invariant");
  assert(t->retired(), "invariant");
}

void JfrStringPool::lock() {
  assert(!_lock->owned_by_self(), "invariant");
  _lock->lock_without_safepoint_check();
}

void JfrStringPool::unlock() {
  _lock->unlock();
}

#ifdef ASSERT
bool JfrStringPool::is_locked() const {
  return _lock->owned_by_self();
}
#endif
