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

#ifndef SHARE_VM_JFR_RECORDER_STORAGE_JFRMEMORYSPACE_INLINE_HPP
#define SHARE_VM_JFR_RECORDER_STORAGE_JFRMEMORYSPACE_INLINE_HPP

#include "jfr/recorder/storage/jfrMemorySpace.hpp"

template <typename T, template <typename> class RetrievalType, typename Callback>
JfrMemorySpace<T, RetrievalType, Callback>::
JfrMemorySpace(size_t min_elem_size, size_t limit_size, size_t cache_count, Callback* callback) :
  _free(),
  _full(),
  _min_elem_size(min_elem_size),
  _limit_size(limit_size),
  _cache_count(cache_count),
  _callback(callback) {}

template <typename T, template <typename> class RetrievalType, typename Callback>
JfrMemorySpace<T, RetrievalType, Callback>::~JfrMemorySpace() {
  Iterator full_iter(_full);
  while (full_iter.has_next()) {
    Type* t = full_iter.next();
    _full.remove(t);
    deallocate(t);
  }
  Iterator free_iter(_free);
  while (free_iter.has_next()) {
    Type* t = free_iter.next();
    _free.remove(t);
    deallocate(t);
  }
}

template <typename T, template <typename> class RetrievalType, typename Callback>
bool JfrMemorySpace<T, RetrievalType, Callback>::initialize() {
  assert(_min_elem_size % os::vm_page_size() == 0, "invariant");
  assert(_limit_size % os::vm_page_size() == 0, "invariant");
  // pre-allocate cache elements
  for (size_t i = 0; i < _cache_count; ++i) {
    Type* const t = allocate(_min_elem_size);
    if (t == NULL) {
      return false;
    }
    insert_free_head(t);
  }
  assert(_free.count() == _cache_count, "invariant");
  return true;
}

template <typename T, template <typename> class RetrievalType, typename Callback>
inline void JfrMemorySpace<T, RetrievalType, Callback>::release_full(T* t) {
  assert(is_locked(), "invariant");
  assert(t != NULL, "invariant");
  assert(_full.in_list(t), "invariant");
  remove_full(t);
  assert(!_full.in_list(t), "invariant");
  if (t->transient()) {
    deallocate(t);
    return;
  }
  assert(t->empty(), "invariant");
  assert(!t->retired(), "invariant");
  assert(t->identity() == NULL, "invariant");
  if (should_populate_cache()) {
    assert(!_free.in_list(t), "invariant");
    insert_free_head(t);
  } else {
    deallocate(t);
  }
}

template <typename T, template <typename> class RetrievalType, typename Callback>
inline void JfrMemorySpace<T, RetrievalType, Callback>::release_free(T* t) {
  assert(is_locked(), "invariant");
  assert(t != NULL, "invariant");
  assert(_free.in_list(t), "invariant");
  if (t->transient()) {
    remove_free(t);
    assert(!_free.in_list(t), "invariant");
    deallocate(t);
    return;
  }
  assert(t->empty(), "invariant");
  assert(!t->retired(), "invariant");
  assert(t->identity() == NULL, "invariant");
  if (!should_populate_cache()) {
    remove_free(t);
    assert(!_free.in_list(t), "invariant");
    deallocate(t);
  }
}

template <typename T, template <typename> class RetrievalType, typename Callback>
template <typename IteratorCallback, typename IteratorType>
inline void JfrMemorySpace<T, RetrievalType, Callback>
::iterate(IteratorCallback& callback, bool full, jfr_iter_direction direction) {
  IteratorType iterator(full ? _full : _free, direction);
  while (iterator.has_next()) {
    callback.process(iterator.next());
  }
}

template <typename Mspace>
inline size_t size_adjustment(size_t size, Mspace* mspace) {
  assert(mspace != NULL, "invariant");
  static const size_t min_elem_size = mspace->min_elem_size();
  if (size < min_elem_size) {
    size = min_elem_size;
  }
  return size;
}

template <typename Mspace>
inline typename Mspace::Type* mspace_allocate(size_t size, Mspace* mspace) {
  return mspace->allocate(size_adjustment(size, mspace));
}

template <typename Mspace>
inline typename Mspace::Type* mspace_allocate_acquired(size_t size, Mspace* mspace, Thread* thread) {
  typename Mspace::Type* const t = mspace_allocate(size, mspace);
  if (t == NULL) return NULL;
  t->acquire(thread);
  return t;
}

template <typename Mspace>
inline typename Mspace::Type* mspace_allocate_transient(size_t size, Mspace* mspace, Thread* thread) {
  typename Mspace::Type* const t = mspace_allocate_acquired(size, mspace, thread);
  if (t == NULL) return NULL;
  assert(t->acquired_by_self(), "invariant");
  t->set_transient();
  return t;
}

template <typename Mspace>
inline typename Mspace::Type* mspace_allocate_transient_lease(size_t size, Mspace* mspace, Thread* thread) {
  typename Mspace::Type* const t = mspace_allocate_transient(size, mspace, thread);
  if (t == NULL) return NULL;
  assert(t->acquired_by_self(), "invariant");
  assert(t->transient(), "invaiant");
  t->set_lease();
  return t;
}

template <typename Mspace>
inline typename Mspace::Type* mspace_allocate_to_full(size_t size, Mspace* mspace, Thread* thread) {
  assert(mspace->is_locked(), "invariant");
  typename Mspace::Type* const t = mspace_allocate_acquired(size, mspace, thread);
  if (t == NULL) return NULL;
  mspace->insert_full_head(t);
  return t;
}

template <typename Mspace>
inline typename Mspace::Type* mspace_allocate_transient_to_full(size_t size, Mspace* mspace, Thread* thread) {
  typename Mspace::Type* const t = mspace_allocate_transient(size, mspace, thread);
  if (t == NULL) return NULL;
  MspaceLock<Mspace> lock(mspace);
  mspace->insert_full_head(t);
  return t;
}

template <typename Mspace>
inline typename Mspace::Type* mspace_allocate_transient_lease_to_full(size_t size, Mspace* mspace, Thread* thread) {
  typename Mspace::Type* const t = mspace_allocate_transient_lease(size, mspace, thread);
  if (t == NULL) return NULL;
  assert(t->acquired_by_self(), "invariant");
  assert(t->transient(), "invaiant");
  assert(t->lease(), "invariant");
  MspaceLock<Mspace> lock(mspace);
  mspace->insert_full_head(t);
  return t;
}

template <typename Mspace>
inline typename Mspace::Type* mspace_allocate_transient_lease_to_free(size_t size, Mspace* mspace, Thread* thread) {
  typename Mspace::Type* const t = mspace_allocate_transient_lease(size, mspace, thread);
  if (t == NULL) return NULL;
  assert(t->acquired_by_self(), "invariant");
  assert(t->transient(), "invaiant");
  assert(t->lease(), "invariant");
  MspaceLock<Mspace> lock(mspace);
  mspace->insert_free_head(t);
  return t;
}

template <typename Mspace>
inline typename Mspace::Type* mspace_get_free(size_t size, Mspace* mspace, Thread* thread) {
  return mspace->get(size, thread);
}

template <typename Mspace>
inline typename Mspace::Type* mspace_get_free_with_retry(size_t size, Mspace* mspace, size_t retry_count, Thread* thread) {
  assert(size <= mspace->min_elem_size(), "invariant");
  for (size_t i = 0; i < retry_count; ++i) {
    typename Mspace::Type* const t = mspace_get_free(size, mspace, thread);
    if (t != NULL) {
      return t;
    }
  }
  return NULL;
}

template <typename Mspace>
inline typename Mspace::Type* mspace_get_free_with_detach(size_t size, Mspace* mspace, Thread* thread) {
  typename Mspace::Type* t = mspace_get_free(size, mspace, thread);
  if (t != NULL) {
    mspace->remove_free(t);
  }
  return t;
}

template <typename Mspace>
inline typename Mspace::Type* mspace_get_free_to_full(size_t size, Mspace* mspace, Thread* thread) {
  assert(size <= mspace->min_elem_size(), "invariant");
  assert(mspace->is_locked(), "invariant");
  typename Mspace::Type* t = mspace_get_free(size, mspace, thread);
  if (t == NULL) {
    return NULL;
  }
  assert(t->acquired_by_self(), "invariant");
  move_to_head(t, mspace->free(), mspace->full());
  return t;
}

template <typename Mspace>
inline typename Mspace::Type* mspace_get_to_full(size_t size, Mspace* mspace, Thread* thread) {
  size = size_adjustment(size, mspace);
  MspaceLock<Mspace> lock(mspace);
  if (size <= mspace->min_elem_size()) {
    typename Mspace::Type* const t = mspace_get_free_to_full(size, mspace, thread);
    if (t != NULL) {
      return t;
    }
  }
  return mspace_allocate_to_full(size, mspace, thread);
}

template <typename Mspace>
inline typename Mspace::Type* mspace_get_free_lease_with_retry(size_t size, Mspace* mspace, size_t retry_count, Thread* thread) {
  typename Mspace::Type* t = mspace_get_free_with_retry(size, mspace, retry_count, thread);
  if (t != NULL) {
    t->set_lease();
  }
  return t;
}

template <typename Mspace>
inline typename Mspace::Type* mspace_get_lease(size_t size, Mspace* mspace, Thread* thread) {
  typename Mspace::Type* t;
  t = mspace_get_free_lease(size, mspace, thread);
  if (t != NULL) {
    assert(t->acquired_by_self(), "invariant");
    assert(t->lease(), "invariant");
    return t;
  }
  t = mspace_allocate_transient_to_full(size, mspace, thread);
  if (t != NULL) {
    t->set_lease();
  }
  return t;
}

template <typename Mspace>
inline void mspace_release_full(typename Mspace::Type* t, Mspace* mspace) {
  assert(t != NULL, "invariant");
  assert(t->unflushed_size() == 0, "invariant");
  assert(mspace != NULL, "invariant");
  assert(mspace->is_locked(), "invariant");
  mspace->release_full(t);
}

template <typename Mspace>
inline void mspace_release_free(typename Mspace::Type* t, Mspace* mspace) {
  assert(t != NULL, "invariant");
  assert(t->unflushed_size() == 0, "invariant");
  assert(mspace != NULL, "invariant");
  assert(mspace->is_locked(), "invariant");
  mspace->release_free(t);
}

template <typename Mspace>
inline void mspace_release_full_critical(typename Mspace::Type* t, Mspace* mspace) {
  MspaceLock<Mspace> lock(mspace);
  mspace_release_full(t, mspace);
}

template <typename Mspace>
inline void mspace_release_free_critical(typename Mspace::Type* t, Mspace* mspace) {
  MspaceLock<Mspace> lock(mspace);
  mspace_release_free(t, mspace);
}

template <typename List>
inline void move_to_head(typename List::Node* t, List& from, List& to) {
  assert(from.in_list(t), "invariant");
  to.prepend(from.remove(t));
}

template <typename Processor, typename Mspace, typename Iterator>
inline void process_free_list_iterator_control(Processor& processor, Mspace* mspace, jfr_iter_direction direction = forward) {
  mspace->template iterate<Processor, Iterator>(processor, false, direction);
}

template <typename Processor, typename Mspace, typename Iterator>
inline void process_full_list_iterator_control(Processor& processor, Mspace* mspace, jfr_iter_direction direction = forward) {
  mspace->template iterate<Processor, Iterator>(processor, true, direction);
}

template <typename Processor, typename Mspace>
inline void process_full_list(Processor& processor, Mspace* mspace, jfr_iter_direction direction = forward) {
  assert(mspace != NULL, "invariant");
  if (mspace->is_full_empty()) return;
  process_full_list_iterator_control<Processor, Mspace, typename Mspace::Iterator>(processor, mspace, direction);
}

template <typename Processor, typename Mspace>
inline void process_free_list(Processor& processor, Mspace* mspace, jfr_iter_direction direction = forward) {
  assert(mspace != NULL, "invariant");
  assert(mspace->has_free(), "invariant");
  process_free_list_iterator_control<Processor, Mspace, typename Mspace::Iterator>(processor, mspace, direction);
}

template <typename Mspace>
inline bool ReleaseOp<Mspace>::process(typename Mspace::Type* t) {
  assert(t != NULL, "invariant");
  if (t->retired() || t->try_acquire(_thread)) {
    if (t->transient()) {
      if (_release_full) {
        mspace_release_full_critical(t, _mspace);
      } else {
        mspace_release_free_critical(t, _mspace);
      }
      return true;
    }
    t->reinitialize();
    assert(t->empty(), "invariant");
    t->release(); // publish
  }
  return true;
}

#ifdef ASSERT
template <typename T>
inline void assert_migration_state(const T* old, const T* new_buffer, size_t used, size_t requested) {
  assert(old != NULL, "invariant");
  assert(new_buffer != NULL, "invariant");
  assert(old->pos() >= old->start(), "invariant");
  assert(old->pos() + used <= old->end(), "invariant");
  assert(new_buffer->free_size() >= (used + requested), "invariant");
}
#endif // ASSERT

template <typename T>
inline void migrate_outstanding_writes(const T* old, T* new_buffer, size_t used, size_t requested) {
  DEBUG_ONLY(assert_migration_state(old, new_buffer, used, requested);)
  if (used > 0) {
    memcpy(new_buffer->pos(), old->pos(), used);
  }
}

#endif // SHARE_VM_JFR_RECORDER_STORAGE_JFRMEMORYSPACE_INLINE_HPP

