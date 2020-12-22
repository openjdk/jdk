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
#include "jfr/recorder/checkpoint/types/traceid/jfrTraceIdEpoch.hpp"
#include "jfr/recorder/service/jfrOptionSet.hpp"
#include "jfr/recorder/storage/jfrMemorySpace.inline.hpp"
#include "jfr/recorder/repository/jfrChunkWriter.hpp"
#include "jfr/recorder/storage/jfrStorageUtils.inline.hpp"
#include "jfr/recorder/stringpool/jfrStringPool.hpp"
#include "jfr/recorder/stringpool/jfrStringPoolWriter.hpp"
#include "jfr/utilities/jfrLinkedList.inline.hpp"
#include "jfr/utilities/jfrSignal.hpp"
#include "jfr/utilities/jfrTypes.hpp"
#include "logging/log.hpp"
#include "runtime/atomic.hpp"
#include "runtime/safepoint.hpp"
#include "runtime/thread.inline.hpp"

typedef JfrStringPool::BufferPtr BufferPtr;

static JfrSignal _new_string;

bool JfrStringPool::is_modified() {
  return _new_string.is_signaled();
}

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

JfrStringPool::JfrStringPool(JfrChunkWriter& cw) : _mspace(NULL) {}

JfrStringPool::~JfrStringPool() {
  delete _mspace;
}

static const size_t string_pool_cache_count = 2;
static const size_t string_pool_buffer_size = 512 * K;

bool JfrStringPool::initialize() {
  assert(_mspace == NULL, "invariant");
  _mspace = create_mspace<JfrStringPoolMspace, JfrStringPool>(string_pool_buffer_size,
                                                              string_pool_cache_count, // cache limit
                                                              0, // cache preallocate count
                                                              false, // post-pone preallocation
                                                              this);
  if (_mspace == NULL) {
    return false;
  }
  // preallocate buffer count to each of the epoch live lists
  for (size_t i = 0; i < string_pool_cache_count * 2; ++i) {
    Buffer* const buffer = mspace_allocate(string_pool_buffer_size, _mspace);
    _mspace->add_to_live_list(buffer, i % 2 == 0);
  }
  assert(_mspace->free_list_is_empty(), "invariant");
  return true;
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

static void assert_retired(const BufferPtr buffer, Thread* thread) {
  assert(buffer != NULL, "invariant");
  assert(buffer->acquired_by(thread), "invariant");
  assert(buffer->retired(), "invariant");
}
#endif // ASSERT

/*
* If the buffer was a "lease" from the global system, release back.
*
* The buffer is effectively invalidated for the thread post-return,
* and the caller should take means to ensure that it is not referenced any longer.
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

BufferPtr JfrStringPool::flush(BufferPtr old, size_t used, size_t requested, Thread* thread) {
  assert(old != NULL, "invariant");
  assert(old->lease(), "invariant");
  if (0 == requested) {
    // indicates a lease is being returned
    release(old, thread);
    return NULL;
  }
  // migration of in-flight information
  BufferPtr const new_buffer = lease(thread, used + requested);
  if (new_buffer != NULL) {
    migrate_outstanding_writes(old, new_buffer, used, requested);
  }
  release(old, thread);
  return new_buffer; // might be NULL
}

BufferPtr JfrStringPool::lease(Thread* thread, size_t size /* 0 */) {
  JfrStringPoolMspace* const mspace = instance()._mspace;
  assert(mspace != NULL, "invariant");
  static const size_t max_elem_size = mspace->min_element_size(); // min is max
  BufferPtr buffer;
  if (size <= max_elem_size) {
    buffer = mspace_acquire_live(size, mspace, thread);
    if (buffer != NULL) {
      buffer->set_lease();
      DEBUG_ONLY(assert_lease(buffer);)
      return buffer;
    }
  }
  buffer = mspace_allocate_transient_lease_to_live_list(size, mspace, thread);
  DEBUG_ONLY(assert_lease(buffer);)
  return buffer;
}

jboolean JfrStringPool::add(jlong id, jstring string, JavaThread* jt) {
  assert(jt != NULL, "invariant");
  {
    JfrStringPoolWriter writer(jt);
    writer.write(id);
    writer.write(string);
    writer.inc_nof_strings();
  }
  _new_string.signal();
  return JNI_TRUE;
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

template <typename T>
class StringPoolDiscarderStub {
 public:
  typedef T Type;
  bool write(Type* buffer, const u1* data, size_t size) {
    // stub only, discard happens at higher level
    return true;
  }
};

typedef StringPoolOp<UnBufferedWriteToChunk> WriteOperation;
typedef StringPoolOp<StringPoolDiscarderStub> DiscardOperation;
typedef ExclusiveOp<WriteOperation> ExclusiveWriteOperation;
typedef ExclusiveOp<DiscardOperation> ExclusiveDiscardOperation;
typedef ReleaseWithExcisionOp<JfrStringPoolMspace, JfrStringPoolMspace::LiveList> ReleaseOperation;
typedef CompositeOperation<ExclusiveWriteOperation, ReleaseOperation> WriteReleaseOperation;
typedef CompositeOperation<ExclusiveDiscardOperation, ReleaseOperation> DiscardReleaseOperation;

size_t JfrStringPool::write(JfrChunkWriter& cw, bool previous_epoch /* false */) {
  Thread* const thread = Thread::current();
  WriteOperation wo(cw, thread);
  ExclusiveWriteOperation ewo(wo);
  assert(_mspace->free_list_is_empty(), "invariant");
  ReleaseOperation ro(_mspace, _mspace->live_list());
  WriteReleaseOperation wro(&ewo, &ro);
  assert(_mspace->live_list_is_nonempty(previous_epoch), "invariant");
  process_live_list(wro, _mspace, previous_epoch);
  return wo.processed();
}

size_t JfrStringPool::clear() {
  DiscardOperation discard_operation;
  ExclusiveDiscardOperation edo(discard_operation);
  assert(_mspace->free_list_is_empty(), "invariant");
  ReleaseOperation ro(_mspace, _mspace->live_list());
  DiscardReleaseOperation discard_op(&edo, &ro);
  assert(_mspace->live_list_is_nonempty(true), "invariant");
  process_live_list(discard_op, _mspace, true); // previous epoch list
  return discard_operation.processed();
}

void JfrStringPool::register_full(BufferPtr buffer, Thread* thread) {
  DEBUG_ONLY(assert_retired(buffer, thread);)
  // nothing here at the moment
}
