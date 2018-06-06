/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
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
#include "jfr/recorder/jfrRecorder.hpp"
#include "jfr/recorder/repository/jfrChunkWriter.hpp"
#include "jfr/recorder/service/jfrOptionSet.hpp"
#include "jfr/recorder/service/jfrPostBox.hpp"
#include "jfr/recorder/storage/jfrMemorySpace.inline.hpp"
#include "jfr/recorder/storage/jfrStorage.hpp"
#include "jfr/recorder/storage/jfrStorageControl.hpp"
#include "jfr/recorder/storage/jfrStorageUtils.inline.hpp"
#include "jfr/utilities/jfrIterator.hpp"
#include "jfr/utilities/jfrTime.hpp"
#include "jfr/writers/jfrNativeEventWriter.hpp"
#include "logging/log.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/orderAccess.hpp"
#include "runtime/os.inline.hpp"
#include "runtime/safepoint.hpp"
#include "runtime/thread.hpp"

typedef JfrStorage::Buffer* BufferPtr;

static JfrStorage* _instance = NULL;
static JfrStorageControl* _control;

JfrStorage& JfrStorage::instance() {
  return *_instance;
}

JfrStorage* JfrStorage::create(JfrChunkWriter& chunkwriter, JfrPostBox& post_box) {
  assert(_instance == NULL, "invariant");
  _instance = new JfrStorage(chunkwriter, post_box);
  return _instance;
}

void JfrStorage::destroy() {
  if (_instance != NULL) {
    delete _instance;
    _instance = NULL;
  }
}

JfrStorage::JfrStorage(JfrChunkWriter& chunkwriter, JfrPostBox& post_box) :
  _control(NULL),
  _global_mspace(NULL),
  _thread_local_mspace(NULL),
  _transient_mspace(NULL),
  _age_mspace(NULL),
  _chunkwriter(chunkwriter),
  _post_box(post_box) {}

JfrStorage::~JfrStorage() {
  if (_control != NULL) {
    delete _control;
  }
  if (_global_mspace != NULL) {
    delete _global_mspace;
  }
  if (_thread_local_mspace != NULL) {
    delete _thread_local_mspace;
  }
  if (_transient_mspace != NULL) {
    delete _transient_mspace;
  }
  if (_age_mspace != NULL) {
    delete _age_mspace;
  }
  _instance = NULL;
}

static const size_t in_memory_discard_threshold_delta = 2; // start to discard data when the only this number of free buffers are left
static const size_t unlimited_mspace_size = 0;
static const size_t thread_local_cache_count = 8;
static const size_t thread_local_scavenge_threshold = thread_local_cache_count / 2;
static const size_t transient_buffer_size_multiplier = 8; // against thread local buffer size

template <typename Mspace>
static Mspace* create_mspace(size_t buffer_size, size_t limit, size_t cache_count, JfrStorage* storage_instance) {
  Mspace* mspace = new Mspace(buffer_size, limit, cache_count, storage_instance);
  if (mspace != NULL) {
    mspace->initialize();
  }
  return mspace;
}

bool JfrStorage::initialize() {
  assert(_control == NULL, "invariant");
  assert(_global_mspace == NULL, "invariant");
  assert(_thread_local_mspace == NULL, "invariant");
  assert(_transient_mspace == NULL, "invariant");
  assert(_age_mspace == NULL, "invariant");

  const size_t num_global_buffers = (size_t)JfrOptionSet::num_global_buffers();
  assert(num_global_buffers >= in_memory_discard_threshold_delta, "invariant");
  const size_t memory_size = (size_t)JfrOptionSet::memory_size();
  const size_t global_buffer_size = (size_t)JfrOptionSet::global_buffer_size();
  const size_t thread_buffer_size = (size_t)JfrOptionSet::thread_buffer_size();

  _control = new JfrStorageControl(num_global_buffers, num_global_buffers - in_memory_discard_threshold_delta);
  if (_control == NULL) {
    return false;
  }
  _global_mspace = create_mspace<JfrStorageMspace>(global_buffer_size, memory_size, num_global_buffers, this);
  if (_global_mspace == NULL) {
    return false;
  }
  _thread_local_mspace = create_mspace<JfrThreadLocalMspace>(thread_buffer_size, unlimited_mspace_size, thread_local_cache_count, this);
  if (_thread_local_mspace == NULL) {
    return false;
  }
  _transient_mspace = create_mspace<JfrStorageMspace>(thread_buffer_size * transient_buffer_size_multiplier, unlimited_mspace_size, 0, this);
  if (_transient_mspace == NULL) {
    return false;
  }
  _age_mspace = create_mspace<JfrStorageAgeMspace>(0 /* no extra size except header */, unlimited_mspace_size, num_global_buffers, this);
  if (_age_mspace == NULL) {
    return false;
  }
  control().set_scavenge_threshold(thread_local_scavenge_threshold);
  return true;
}

JfrStorageControl& JfrStorage::control() {
  return *instance()._control;
}

static void log_allocation_failure(const char* msg, size_t size) {
  log_warning(jfr)("Unable to allocate " SIZE_FORMAT " bytes of %s.", size, msg);
}

BufferPtr JfrStorage::acquire_thread_local(Thread* thread, size_t size /* 0 */) {
  BufferPtr buffer = mspace_get_to_full(size, instance()._thread_local_mspace, thread);
  if (buffer == NULL) {
    log_allocation_failure("thread local_memory", size);
    return NULL;
  }
  assert(buffer->acquired_by_self(), "invariant");
  return buffer;
}

BufferPtr JfrStorage::acquire_transient(size_t size, Thread* thread) {
  BufferPtr buffer = mspace_allocate_transient_lease_to_full(size, instance()._transient_mspace, thread);
  if (buffer == NULL) {
    log_allocation_failure("transient memory", size);
    return NULL;
  }
  assert(buffer->acquired_by_self(), "invariant");
  assert(buffer->transient(), "invariant");
  assert(buffer->lease(), "invariant");
  return buffer;
}

static BufferPtr get_lease(size_t size, JfrStorageMspace* mspace, JfrStorage& storage_instance, size_t retry_count, Thread* thread) {
  assert(size <= mspace->min_elem_size(), "invariant");
  while (true) {
    BufferPtr t = mspace_get_free_lease_with_retry(size, mspace, retry_count, thread);
    if (t == NULL && storage_instance.control().should_discard()) {
      storage_instance.discard_oldest(thread);
      continue;
    }
    return t;
  }
}

static BufferPtr get_promotion_buffer(size_t size, JfrStorageMspace* mspace, JfrStorage& storage_instance, size_t retry_count, Thread* thread) {
  assert(size <= mspace->min_elem_size(), "invariant");
  while (true) {
    BufferPtr t = mspace_get_free_with_retry(size, mspace, retry_count, thread);
    if (t == NULL && storage_instance.control().should_discard()) {
      storage_instance.discard_oldest(thread);
      continue;
    }
    return t;
  }
}

static const size_t lease_retry = 10;

BufferPtr JfrStorage::acquire_large(size_t size, Thread* thread) {
  JfrStorage& storage_instance = instance();
  const size_t max_elem_size = storage_instance._global_mspace->min_elem_size(); // min is also max
  // if not too large and capacity is still available, ask for a lease from the global system
  if (size < max_elem_size && storage_instance.control().is_global_lease_allowed()) {
    BufferPtr const buffer = get_lease(size, storage_instance._global_mspace, storage_instance, lease_retry, thread);
    if (buffer != NULL) {
      assert(buffer->acquired_by_self(), "invariant");
      assert(!buffer->transient(), "invariant");
      assert(buffer->lease(), "invariant");
      storage_instance.control().increment_leased();
      return buffer;
    }
  }
  return acquire_transient(size, thread);
}

static void write_data_loss_event(JfrBuffer* buffer, u8 unflushed_size, Thread* thread) {
  assert(buffer != NULL, "invariant");
  assert(buffer->empty(), "invariant");
  const u8 total_data_loss = thread->jfr_thread_local()->add_data_lost(unflushed_size);
  if (EventDataLoss::is_enabled()) {
    JfrNativeEventWriter writer(buffer, thread);
    writer.write<u8>(EventDataLoss::eventId);
    writer.write(JfrTicks::now());
    writer.write(unflushed_size);
    writer.write(total_data_loss);
  }
}

static void write_data_loss(BufferPtr buffer, Thread* thread) {
  assert(buffer != NULL, "invariant");
  const size_t unflushed_size = buffer->unflushed_size();
  buffer->concurrent_reinitialization();
  if (unflushed_size == 0) {
    return;
  }
  write_data_loss_event(buffer, unflushed_size, thread);
}

static const size_t promotion_retry = 100;

bool JfrStorage::flush_regular_buffer(BufferPtr buffer, Thread* thread) {
  assert(buffer != NULL, "invariant");
  assert(!buffer->lease(), "invariant");
  assert(!buffer->transient(), "invariant");
  const size_t unflushed_size = buffer->unflushed_size();
  if (unflushed_size == 0) {
    buffer->concurrent_reinitialization();
    assert(buffer->empty(), "invariant");
    return true;
  }
  BufferPtr const promotion_buffer = get_promotion_buffer(unflushed_size, _global_mspace, *this, promotion_retry, thread);
  if (promotion_buffer == NULL) {
    write_data_loss(buffer, thread);
    return false;
  }
  assert(promotion_buffer->acquired_by_self(), "invariant");
  assert(promotion_buffer->free_size() >= unflushed_size, "invariant");
  buffer->concurrent_move_and_reinitialize(promotion_buffer, unflushed_size);
  assert(buffer->empty(), "invariant");
  return true;
}

/*
* 1. If the buffer was a "lease" from the global system, release back.
* 2. If the buffer is transient (temporal dynamically allocated), retire and register full.
*
* The buffer is effectively invalidated for the thread post-return,
* and the caller should take means to ensure that it is not referenced any longer.
*/
void JfrStorage::release_large(BufferPtr buffer, Thread* thread) {
  assert(buffer != NULL, "invariant");
  assert(buffer->lease(), "invariant");
  assert(buffer->acquired_by_self(), "invariant");
  buffer->clear_lease();
  if (buffer->transient()) {
    buffer->set_retired();
    register_full(buffer, thread);
  } else {
    buffer->release();
    control().decrement_leased();
  }
}

static JfrAgeNode* new_age_node(BufferPtr buffer, JfrStorageAgeMspace* age_mspace, Thread* thread) {
  assert(buffer != NULL, "invariant");
  assert(age_mspace != NULL, "invariant");
  return mspace_allocate_transient(0, age_mspace, thread);
}

static void log_registration_failure(size_t unflushed_size) {
  log_warning(jfr)("Unable to register a full buffer of " SIZE_FORMAT " bytes.", unflushed_size);
  log_debug(jfr, system)("Cleared 1 full buffer of " SIZE_FORMAT " bytes.", unflushed_size);
}

static void handle_registration_failure(BufferPtr buffer) {
  assert(buffer != NULL, "invariant");
  assert(buffer->retired(), "invariant");
  const size_t unflushed_size = buffer->unflushed_size();
  buffer->reinitialize();
  log_registration_failure(unflushed_size);
}

static JfrAgeNode* get_free_age_node(JfrStorageAgeMspace* age_mspace, Thread* thread) {
  assert(JfrBuffer_lock->owned_by_self(), "invariant");
  return mspace_get_free_with_detach(0, age_mspace, thread);
}

static bool insert_full_age_node(JfrAgeNode* age_node, JfrStorageAgeMspace* age_mspace, Thread* thread) {
  assert(JfrBuffer_lock->owned_by_self(), "invariant");
  assert(age_node->retired_buffer()->retired(), "invariant");
  age_mspace->insert_full_head(age_node);
  return true;
}

static bool full_buffer_registration(BufferPtr buffer, JfrStorageAgeMspace* age_mspace, JfrStorageControl& control, Thread* thread) {
  assert(buffer != NULL, "invariant");
  assert(buffer->retired(), "invariant");
  assert(age_mspace != NULL, "invariant");
  MutexLockerEx lock(JfrBuffer_lock, Mutex::_no_safepoint_check_flag);
  JfrAgeNode* age_node = get_free_age_node(age_mspace, thread);
  if (age_node == NULL) {
    age_node = new_age_node(buffer, age_mspace, thread);
    if (age_node == NULL) {
      return false;
    }
  }
  assert(age_node->acquired_by_self(), "invariant");
  assert(age_node != NULL, "invariant");
  age_node->set_retired_buffer(buffer);
  control.increment_full();
  return insert_full_age_node(age_node, age_mspace, thread);
}

void JfrStorage::register_full(BufferPtr buffer, Thread* thread) {
  assert(buffer != NULL, "invariant");
  assert(buffer->retired(), "invariant");
  if (!full_buffer_registration(buffer, _age_mspace, control(), thread)) {
    handle_registration_failure(buffer);
    buffer->release();
  }
  if (control().should_post_buffer_full_message()) {
    _post_box.post(MSG_FULLBUFFER);
  }
}

void JfrStorage::lock() {
  assert(!JfrBuffer_lock->owned_by_self(), "invariant");
  JfrBuffer_lock->lock_without_safepoint_check();
}

void JfrStorage::unlock() {
  assert(JfrBuffer_lock->owned_by_self(), "invariant");
  JfrBuffer_lock->unlock();
}

#ifdef ASSERT
bool JfrStorage::is_locked() const {
  return JfrBuffer_lock->owned_by_self();
}
#endif

// don't use buffer on return, it is gone
void JfrStorage::release(BufferPtr buffer, Thread* thread) {
  assert(buffer != NULL, "invariant");
  assert(!buffer->lease(), "invariant");
  assert(!buffer->transient(), "invariant");
  assert(!buffer->retired(), "invariant");
  if (!buffer->empty()) {
    if (!flush_regular_buffer(buffer, thread)) {
      buffer->concurrent_reinitialization();
    }
  }
  assert(buffer->empty(), "invariant");
  control().increment_dead();
  buffer->release();
  buffer->set_retired();
}

void JfrStorage::release_thread_local(BufferPtr buffer, Thread* thread) {
  assert(buffer != NULL, "invariant");
  JfrStorage& storage_instance = instance();
  storage_instance.release(buffer, thread);
  if (storage_instance.control().should_scavenge()) {
    storage_instance._post_box.post(MSG_DEADBUFFER);
  }
}

static void log_discard(size_t count, size_t amount, size_t current) {
  if (log_is_enabled(Debug, jfr, system)) {
    assert(count > 0, "invariant");
    log_debug(jfr, system)("Cleared " SIZE_FORMAT " full buffer(s) of " SIZE_FORMAT" bytes.", count, amount);
    log_debug(jfr, system)("Current number of full buffers " SIZE_FORMAT "", current);
  }
}

void JfrStorage::discard_oldest(Thread* thread) {
  if (JfrBuffer_lock->try_lock()) {
    if (!control().should_discard()) {
      // another thread handled it
      return;
    }
    const size_t num_full_pre_discard = control().full_count();
    size_t num_full_post_discard = 0;
    size_t discarded_size = 0;
    while (true) {
      JfrAgeNode* const oldest_age_node = _age_mspace->full_tail();
      if (oldest_age_node == NULL) {
        break;
      }
      BufferPtr const buffer = oldest_age_node->retired_buffer();
      assert(buffer->retired(), "invariant");
      discarded_size += buffer->unflushed_size();
      num_full_post_discard = control().decrement_full();
      if (buffer->transient()) {
        mspace_release_full(buffer, _transient_mspace);
        mspace_release_full(oldest_age_node, _age_mspace);
        continue;
      } else {
        mspace_release_full(oldest_age_node, _age_mspace);
        buffer->reinitialize();
        buffer->release(); // pusb
        break;
      }
    }
    JfrBuffer_lock->unlock();
    const size_t number_of_discards = num_full_pre_discard - num_full_post_discard;
    if (number_of_discards > 0) {
      log_discard(number_of_discards, discarded_size, num_full_post_discard);
    }
  }
}

#ifdef ASSERT
typedef const BufferPtr ConstBufferPtr;

static void assert_flush_precondition(ConstBufferPtr cur, size_t used, bool native, const Thread* t) {
  assert(t != NULL, "invariant");
  assert(cur != NULL, "invariant");
  assert(cur->pos() + used <= cur->end(), "invariant");
  assert(native ? t->jfr_thread_local()->native_buffer() == cur : t->jfr_thread_local()->java_buffer() == cur, "invariant");
}

static void assert_flush_regular_precondition(ConstBufferPtr cur, const u1* const cur_pos, size_t used, size_t req, const Thread* t) {
  assert(t != NULL, "invariant");
  assert(t->jfr_thread_local()->shelved_buffer() == NULL, "invariant");
  assert(cur != NULL, "invariant");
  assert(!cur->lease(), "invariant");
  assert(cur_pos != NULL, "invariant");
  assert(req >= used, "invariant");
}

static void assert_provision_large_precondition(ConstBufferPtr cur, size_t used, size_t req, const Thread* t) {
  assert(cur != NULL, "invariant");
  assert(t != NULL, "invariant");
  assert(t->jfr_thread_local()->shelved_buffer() != NULL, "invariant");
  assert(req >= used, "invariant");
}

static void assert_flush_large_precondition(ConstBufferPtr cur, const u1* const cur_pos, size_t used, size_t req, bool native, Thread* t) {
  assert(t != NULL, "invariant");
  assert(cur != NULL, "invariant");
  assert(cur->lease(), "invariant");
  assert(cur_pos != NULL, "invariant");
  assert(native ? t->jfr_thread_local()->native_buffer() == cur : t->jfr_thread_local()->java_buffer() == cur, "invariant");
  assert(t->jfr_thread_local()->shelved_buffer() != NULL, "invariant");
  assert(req >= used, "invariant");
  assert(cur != t->jfr_thread_local()->shelved_buffer(), "invariant");
}
#endif // ASSERT

BufferPtr JfrStorage::flush(BufferPtr cur, size_t used, size_t req, bool native, Thread* t) {
  debug_only(assert_flush_precondition(cur, used, native, t);)
  const u1* const cur_pos = cur->pos();
  req += used;
  // requested size now encompass the outstanding used size
  return cur->lease() ? instance().flush_large(cur, cur_pos, used, req, native, t) :
                          instance().flush_regular(cur, cur_pos, used, req, native, t);
}

BufferPtr JfrStorage::flush_regular(BufferPtr cur, const u1* const cur_pos, size_t used, size_t req, bool native, Thread* t) {
  debug_only(assert_flush_regular_precondition(cur, cur_pos, used, req, t);)
  // A flush is needed before memcpy since a non-large buffer is thread stable
  // (thread local). The flush will not modify memory in addresses above pos()
  // which is where the "used / uncommitted" data resides. It is therefore both
  // possible and valid to migrate data after the flush. This is however only
  // the case for stable thread local buffers; it is not the case for large buffers.
  if (!cur->empty()) {
    flush_regular_buffer(cur, t);
  }
  assert(t->jfr_thread_local()->shelved_buffer() == NULL, "invariant");
  if (cur->free_size() >= req) {
    // simplest case, no switching of buffers
    if (used > 0) {
      memcpy(cur->pos(), (void*)cur_pos, used);
    }
    assert(native ? t->jfr_thread_local()->native_buffer() == cur : t->jfr_thread_local()->java_buffer() == cur, "invariant");
    return cur;
  }
  // Going for a "larger-than-regular" buffer.
  // Shelve the current buffer to make room for a temporary lease.
  t->jfr_thread_local()->shelve_buffer(cur);
  return provision_large(cur, cur_pos, used, req, native, t);
}

static BufferPtr store_buffer_to_thread_local(BufferPtr buffer, JfrThreadLocal* jfr_thread_local, bool native) {
  assert(buffer != NULL, "invariant");
  if (native) {
    jfr_thread_local->set_native_buffer(buffer);
  } else {
    jfr_thread_local->set_java_buffer(buffer);
  }
  return buffer;
}

static BufferPtr restore_shelved_buffer(bool native, Thread* t) {
  JfrThreadLocal* const tl = t->jfr_thread_local();
  BufferPtr shelved = tl->shelved_buffer();
  assert(shelved != NULL, "invariant");
  tl->shelve_buffer(NULL);
  // restore shelved buffer back as primary
  return store_buffer_to_thread_local(shelved, tl, native);
}

BufferPtr JfrStorage::flush_large(BufferPtr cur, const u1* const cur_pos, size_t used, size_t req, bool native, Thread* t) {
  debug_only(assert_flush_large_precondition(cur, cur_pos, used, req, native, t);)
  // Can the "regular" buffer (now shelved) accommodate the requested size?
  BufferPtr shelved = t->jfr_thread_local()->shelved_buffer();
  assert(shelved != NULL, "invariant");
  if (shelved->free_size() >= req) {
    if (req > 0) {
      memcpy(shelved->pos(), (void*)cur_pos, (size_t)used);
    }
    // release and invalidate
    release_large(cur, t);
    return restore_shelved_buffer(native, t);
  }
  // regular too small
  return provision_large(cur, cur_pos,  used, req, native, t);
}

static BufferPtr large_fail(BufferPtr cur, bool native, JfrStorage& storage_instance, Thread* t) {
  assert(cur != NULL, "invariant");
  assert(t != NULL, "invariant");
  if (cur->lease()) {
    storage_instance.release_large(cur, t);
  }
  return restore_shelved_buffer(native, t);
}

// Always returns a non-null buffer.
// If accommodating the large request fails, the shelved buffer is returned
// even though it might be smaller than the requested size.
// Caller needs to ensure if the size was successfully accommodated.
BufferPtr JfrStorage::provision_large(BufferPtr cur, const u1* const cur_pos, size_t used, size_t req, bool native, Thread* t) {
  debug_only(assert_provision_large_precondition(cur, used, req, t);)
  assert(t->jfr_thread_local()->shelved_buffer() != NULL, "invariant");
  BufferPtr const buffer = acquire_large(req, t);
  if (buffer == NULL) {
    // unable to allocate and serve the request
    return large_fail(cur, native, *this, t);
  }
  // ok managed to acquire a "large" buffer for the requested size
  assert(buffer->free_size() >= req, "invariant");
  assert(buffer->lease(), "invariant");
  // transfer outstanding data
  memcpy(buffer->pos(), (void*)cur_pos, used);
  if (cur->lease()) {
    release_large(cur, t);
    // don't use current anymore, it is gone
  }
  return store_buffer_to_thread_local(buffer, t->jfr_thread_local(), native);
}

typedef UnBufferedWriteToChunk<JfrBuffer> WriteOperation;
typedef MutexedWriteOp<WriteOperation> MutexedWriteOperation;
typedef ConcurrentWriteOp<WriteOperation> ConcurrentWriteOperation;
typedef ConcurrentWriteOpExcludeRetired<WriteOperation> ThreadLocalConcurrentWriteOperation;

size_t JfrStorage::write() {
  const size_t full_size_processed = write_full();
  WriteOperation wo(_chunkwriter);
  ThreadLocalConcurrentWriteOperation tlwo(wo);
  process_full_list(tlwo, _thread_local_mspace);
  ConcurrentWriteOperation cwo(wo);
  process_free_list(cwo, _global_mspace);
  return full_size_processed + wo.processed();
}

size_t JfrStorage::write_at_safepoint() {
  assert(SafepointSynchronize::is_at_safepoint(), "invariant");
  WriteOperation wo(_chunkwriter);
  MutexedWriteOperation writer(wo); // mutexed write mode
  process_full_list(writer, _thread_local_mspace);
  assert(_transient_mspace->is_free_empty(), "invariant");
  process_full_list(writer, _transient_mspace);
  assert(_global_mspace->is_full_empty(), "invariant");
  process_free_list(writer, _global_mspace);
  return wo.processed();
}

typedef DiscardOp<DefaultDiscarder<JfrStorage::Buffer> > DiscardOperation;
typedef ReleaseOp<JfrStorageMspace> ReleaseOperation;
typedef CompositeOperation<MutexedWriteOperation, ReleaseOperation> FullOperation;

size_t JfrStorage::clear() {
  const size_t full_size_processed = clear_full();
  DiscardOperation discarder(concurrent); // concurrent discard mode
  process_full_list(discarder, _thread_local_mspace);
  assert(_transient_mspace->is_free_empty(), "invariant");
  process_full_list(discarder, _transient_mspace);
  assert(_global_mspace->is_full_empty(), "invariant");
  process_free_list(discarder, _global_mspace);
  return full_size_processed + discarder.processed();
}

static void insert_free_age_nodes(JfrStorageAgeMspace* age_mspace, JfrAgeNode* head, JfrAgeNode* tail, size_t count) {
  if (tail != NULL) {
    assert(tail->next() == NULL, "invariant");
    assert(head != NULL, "invariant");
    assert(head->prev() == NULL, "invariant");
    MutexLockerEx buffer_lock(JfrBuffer_lock, Mutex::_no_safepoint_check_flag);
    age_mspace->insert_free_tail(head, tail, count);
  }
}

template <typename Processor>
static void process_age_list(Processor& processor, JfrStorageAgeMspace* age_mspace, JfrAgeNode* head, size_t count) {
  assert(age_mspace != NULL, "invariant");
  assert(head != NULL, "invariant");
  assert(count > 0, "invariant");
  JfrAgeNode* node = head;
  JfrAgeNode* last = NULL;
  while (node != NULL) {
    last = node;
    BufferPtr const buffer = node->retired_buffer();
    assert(buffer != NULL, "invariant");
    assert(buffer->retired(), "invariant");
    processor.process(buffer);
    // at this point, buffer is already live or destroyed
    node->clear_identity();
    JfrAgeNode* const next = (JfrAgeNode*)node->next();
    if (node->transient()) {
      // detach
      last = (JfrAgeNode*)last->prev();
      if (last != NULL) {
        last->set_next(next);
      } else {
        head = next;
      }
      if (next != NULL) {
        next->set_prev(last);
      }
      --count;
      age_mspace->deallocate(node);
    }
    node = next;
  }
  insert_free_age_nodes(age_mspace, head, last, count);
}

template <typename Processor>
static size_t process_full(Processor& processor, JfrStorageControl& control, JfrStorageAgeMspace* age_mspace) {
  assert(age_mspace != NULL, "invariant");
  if (age_mspace->is_full_empty()) {
    // nothing to do
    return 0;
  }
  size_t count;
  JfrAgeNode* head;
  {
    // fetch age list
    MutexLockerEx buffer_lock(JfrBuffer_lock, Mutex::_no_safepoint_check_flag);
    count = age_mspace->full_count();
    head = age_mspace->clear_full();
    control.reset_full();
  }
  assert(head != NULL, "invariant");
  assert(count > 0, "invariant");
  process_age_list(processor, age_mspace, head, count);
  return count;
}

static void log(size_t count, size_t amount, bool clear = false) {
  if (log_is_enabled(Debug, jfr, system)) {
    if (count > 0) {
      log_debug(jfr, system)("%s " SIZE_FORMAT " full buffer(s) of " SIZE_FORMAT" B of data%s",
        clear ? "Discarded" : "Wrote", count, amount, clear ? "." : " to chunk.");
    }
  }
}

// full writer
// Assumption is retired only; exclusive access
// MutexedWriter -> ReleaseOp
//
size_t JfrStorage::write_full() {
  assert(_chunkwriter.is_valid(), "invariant");
  Thread* const thread = Thread::current();
  WriteOperation wo(_chunkwriter);
  MutexedWriteOperation writer(wo); // a retired buffer implies mutexed access
  ReleaseOperation ro(_transient_mspace, thread);
  FullOperation cmd(&writer, &ro);
  const size_t count = process_full(cmd, control(), _age_mspace);
  log(count, writer.processed());
  return writer.processed();
}

size_t JfrStorage::clear_full() {
  DiscardOperation discarder(mutexed); // a retired buffer implies mutexed access
  const size_t count = process_full(discarder, control(), _age_mspace);
  log(count, discarder.processed(), true);
  return discarder.processed();
}

static void scavenge_log(size_t count, size_t amount, size_t current) {
  if (count > 0) {
    if (log_is_enabled(Debug, jfr, system)) {
      log_debug(jfr, system)("Released " SIZE_FORMAT " dead buffer(s) of " SIZE_FORMAT" B of data.", count, amount);
      log_debug(jfr, system)("Current number of dead buffers " SIZE_FORMAT "", current);
    }
  }
}

template <typename Mspace>
class Scavenger {
private:
  JfrStorageControl& _control;
  Mspace* _mspace;
  size_t _count;
  size_t _amount;
public:
  typedef typename Mspace::Type Type;
  Scavenger(JfrStorageControl& control, Mspace* mspace) : _control(control), _mspace(mspace), _count(0), _amount(0) {}
  bool process(Type* t) {
    if (t->retired()) {
      assert(!t->transient(), "invariant");
      assert(!t->lease(), "invariant");
      assert(t->empty(), "invariant");
      assert(t->identity() == NULL, "invariant");
      ++_count;
      _amount += t->total_size();
      t->clear_retired();
      _control.decrement_dead();
      mspace_release_full_critical(t, _mspace);
    }
    return true;
  }
  size_t processed() const { return _count; }
  size_t amount() const { return _amount; }
};

size_t JfrStorage::scavenge() {
  JfrStorageControl& ctrl = control();
  if (ctrl.dead_count() == 0) {
    return 0;
  }
  Scavenger<JfrThreadLocalMspace> scavenger(ctrl, _thread_local_mspace);
  process_full_list(scavenger, _thread_local_mspace);
  scavenge_log(scavenger.processed(), scavenger.amount(), ctrl.dead_count());
  return scavenger.processed();
}
