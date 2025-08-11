/*
 * Copyright (c) 2016, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "classfile/javaClasses.inline.hpp"
#include "classfile/symbolTable.hpp"
#include "classfile/systemDictionary.hpp"
#include "classfile/vmSymbols.hpp"
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
#include "runtime/javaThread.hpp"
#include "runtime/safepoint.hpp"

static int generation_offset = invalid_offset;
static jobject string_pool = nullptr;

static bool setup_string_pool_offsets(TRAPS) {
  const char class_name[] = "jdk/jfr/internal/StringPool";
  Symbol* const k_sym = SymbolTable::new_symbol(class_name);
  assert(k_sym != nullptr, "invariant");
  Klass* klass = SystemDictionary::resolve_or_fail(k_sym, true, CHECK_false);
  assert(klass != nullptr, "invariant");
  klass->initialize(CHECK_false);
  assert(!klass->should_be_initialized(), "invariant");
  assert(string_pool == nullptr, "invariant");
  jobject pool = JfrJavaSupport::global_jni_handle(klass->java_mirror(), THREAD);
  if (pool == nullptr) {
    return false;
  }
  const char generation_name[] = "generation";
  Symbol* const generation_sym = SymbolTable::new_symbol(generation_name);
  assert(generation_sym != nullptr, "invariant");
  assert(invalid_offset == generation_offset, "invariant");
  if (!JfrJavaSupport::compute_field_offset(generation_offset, klass, generation_sym, vmSymbols::short_signature(), true)) {
    JfrJavaSupport::destroy_global_jni_handle(pool);
    return false;
  }
  assert(generation_offset != invalid_offset, "invariant");
  string_pool = pool;
  return true;
}

static bool initialize_java_string_pool() {
  static bool initialized = false;
  if (!initialized) {
    initialized = setup_string_pool_offsets(JavaThread::current());
  }
  return initialized;
}

typedef JfrStringPool::BufferPtr BufferPtr;

static JfrSignal _new_string;

bool JfrStringPool::is_modified() {
  return _new_string.is_signaled_with_reset();
}

static JfrStringPool* _instance = nullptr;

JfrStringPool& JfrStringPool::instance() {
  return *_instance;
}

JfrStringPool* JfrStringPool::create(JfrChunkWriter& cw) {
  assert(_instance == nullptr, "invariant");
  _instance = new JfrStringPool(cw);
  return _instance;
}

void JfrStringPool::destroy() {
  assert(_instance != nullptr, "invariant");
  delete _instance;
  _instance = nullptr;
}

JfrStringPool::JfrStringPool(JfrChunkWriter& cw) : _mspace(nullptr), _chunkwriter(cw) {}

JfrStringPool::~JfrStringPool() {
  delete _mspace;
}

static const size_t string_pool_cache_count = 2;
static const size_t string_pool_buffer_size = 512 * K;

bool JfrStringPool::initialize() {
  if (!initialize_java_string_pool()) {
    return false;
  }

  assert(_mspace == nullptr, "invariant");
  _mspace = create_mspace<JfrStringPoolMspace>(string_pool_buffer_size,
                                               0,
                                               0, // cache preallocate count
                                               false,
                                               this);

  // preallocate buffer count to each of the epoch live lists
  for (size_t i = 0; i < string_pool_cache_count * 2; ++i) {
    Buffer* const buffer = mspace_allocate(string_pool_buffer_size, _mspace);
    if (buffer == nullptr) {
      return false;
    }
    _mspace->add_to_live_list(buffer, i % 2 == 0);
  }
  assert(_mspace->free_list_is_empty(), "invariant");
  return _mspace != nullptr;
}

/*
* If the buffer was a "lease" from the global system, release back.
*
* The buffer is effectively invalidated for the thread post-return,
* and the caller should take means to ensure that it is not referenced any longer.
*/
static void release(BufferPtr buffer, Thread* thread) {
  assert(buffer != nullptr, "invariant");
  assert(buffer->lease(), "invariant");
  assert(buffer->acquired_by_self(), "invariant");
  buffer->clear_lease();
  buffer->release();
}

BufferPtr JfrStringPool::flush(BufferPtr old, size_t used, size_t requested, Thread* thread) {
  assert(old != nullptr, "invariant");
  assert(old->lease(), "invariant");
  if (0 == requested) {
    // indicates a lease is being returned
    release(old, thread);
    return nullptr;
  }
  // migration of in-flight information
  BufferPtr const new_buffer = lease(thread, used + requested);
  if (new_buffer != nullptr) {
    migrate_outstanding_writes(old, new_buffer, used, requested);
  }
  release(old, thread);
  return new_buffer; // might be null
}

static const size_t lease_retry = 10;

BufferPtr JfrStringPool::lease(Thread* thread, size_t size /* 0 */) {
  BufferPtr buffer = mspace_acquire_lease_with_retry(size, instance()._mspace, lease_retry, thread);
  if (buffer == nullptr) {
    buffer = mspace_allocate_transient_lease_to_live_list(size,  instance()._mspace, thread);
  }
  assert(buffer->acquired_by_self(), "invariant");
  assert(buffer->lease(), "invariant");
  return buffer;
}

jboolean JfrStringPool::add(jlong id, jstring string, JavaThread* jt) {
  assert(jt != nullptr, "invariant");
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
typedef ReinitializationOp<JfrStringPoolBuffer> ReinitializationOperation;
typedef ReleaseWithExcisionOp<JfrStringPoolMspace, JfrStringPoolMspace::LiveList> ReleaseOperation;
typedef CompositeOperation<ExclusiveWriteOperation, ReleaseOperation> WriteReleaseOperation;
typedef CompositeOperation<ExclusiveWriteOperation, ReinitializationOperation> WriteReinitializeOperation;
typedef CompositeOperation<ExclusiveDiscardOperation, ReleaseOperation> DiscardReleaseOperation;

size_t JfrStringPool::write() {
  Thread* const thread = Thread::current();
  WriteOperation wo(_chunkwriter, thread);
  ExclusiveWriteOperation ewo(wo);
  assert(_mspace->free_list_is_empty(), "invariant");
  ReleaseOperation ro(_mspace, _mspace->live_list(true)); // previous epoch list
  WriteReleaseOperation wro(&ewo, &ro);
  assert(_mspace->live_list_is_nonempty(), "invariant");
  process_live_list(wro, _mspace, true); // previous epoch list
  return wo.processed();
}

size_t JfrStringPool::flush() {
  Thread* const thread = Thread::current();
  WriteOperation wo(_chunkwriter, thread);
  ExclusiveWriteOperation ewo(wo);
  ReinitializationOperation rio;
  WriteReinitializeOperation wro(&ewo, &rio);
  assert(_mspace->free_list_is_empty(), "invariant");
  assert(_mspace->live_list_is_nonempty(), "invariant");
  process_live_list(wro, _mspace); // current epoch list
  return wo.processed();
}

size_t JfrStringPool::clear() {
  DiscardOperation discard_operation;
  ExclusiveDiscardOperation edo(discard_operation);
  assert(_mspace->free_list_is_empty(), "invariant");
  ReleaseOperation ro(_mspace, _mspace->live_list(true)); // previous epoch list
  DiscardReleaseOperation discard_op(&edo, &ro);
  assert(_mspace->live_list_is_nonempty(), "invariant");
  process_live_list(discard_op, _mspace, true); // previous epoch list
  return discard_operation.processed();
}

void JfrStringPool::register_full(BufferPtr buffer, Thread* thread) {
  // nothing here at the moment
  assert(buffer != nullptr, "invariant");
  assert(buffer->acquired_by(thread), "invariant");
  assert(buffer->retired(), "invariant");
}

void JfrStringPool::on_epoch_shift() {
  assert(SafepointSynchronize::is_at_safepoint(), "invariant");
  assert(string_pool != nullptr, "invariant");
  oop mirror = JfrJavaSupport::resolve_non_null(string_pool);
  assert(mirror != nullptr, "invariant");
  mirror->short_field_put(generation_offset, JfrTraceIdEpoch::epoch_generation());
}
