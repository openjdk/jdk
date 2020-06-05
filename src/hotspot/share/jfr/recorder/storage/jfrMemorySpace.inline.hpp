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

#ifndef SHARE_JFR_RECORDER_STORAGE_JFRMEMORYSPACE_INLINE_HPP
#define SHARE_JFR_RECORDER_STORAGE_JFRMEMORYSPACE_INLINE_HPP

#include "jfr/recorder/storage/jfrMemorySpace.hpp"
#include "runtime/os.hpp"

template <typename Callback, template <typename> class RetrievalPolicy, typename FreeListType, typename FullListType>
JfrMemorySpace<Callback, RetrievalPolicy, FreeListType, FullListType>::
JfrMemorySpace(size_t min_elem_size, size_t limit_size, size_t free_list_cache_count, Callback* callback) :
  _free_list(),
  _full_list(),
  _min_elem_size(min_elem_size),
  _limit_size(limit_size),
  _free_list_cache_count(free_list_cache_count),
  _free_list_count(0),
  _callback(callback) {}

template <typename Callback, template <typename> class RetrievalPolicy, typename FreeListType, typename FullListType>
JfrMemorySpace<Callback, RetrievalPolicy, FreeListType, FullListType>::~JfrMemorySpace() {
  while (full_list_is_nonempty()) {
    NodePtr node = remove_from_full_list();
    deallocate(node);
  }
  while (free_list_is_nonempty()) {
    NodePtr node = remove_from_free_list();
    deallocate(node);
  }
}

template <typename Callback, template <typename> class RetrievalPolicy, typename FreeListType, typename FullListType>
bool JfrMemorySpace<Callback, RetrievalPolicy, FreeListType, FullListType>::initialize() {
  if (!(_free_list.initialize() && _full_list.initialize())) {
    return false;
  }
  assert(_min_elem_size % os::vm_page_size() == 0, "invariant");
  assert(_limit_size % os::vm_page_size() == 0, "invariant");
  // pre-allocate free list cache elements
  for (size_t i = 0; i < _free_list_cache_count; ++i) {
    NodePtr const node = allocate(_min_elem_size);
    if (node == NULL) {
      return false;
    }
    add_to_free_list(node);
  }
  return true;
}

template <typename Callback, template <typename> class RetrievalPolicy, typename FreeListType, typename FullListType>
inline bool JfrMemorySpace<Callback, RetrievalPolicy, FreeListType, FullListType>::should_populate_free_list() const {
  return _free_list_count < _free_list_cache_count;
}

template <typename Callback, template <typename> class RetrievalPolicy, typename FreeListType, typename FullListType>
inline size_t JfrMemorySpace<Callback, RetrievalPolicy, FreeListType, FullListType>::min_elem_size() const {
  return _min_elem_size;
}

template <typename Callback, template <typename> class RetrievalPolicy, typename FreeListType, typename FullListType>
inline size_t JfrMemorySpace<Callback, RetrievalPolicy, FreeListType, FullListType>::limit_size() const {
  return _limit_size;
}

template <typename Callback, template <typename> class RetrievalPolicy, typename FreeListType, typename FullListType>
inline FreeListType& JfrMemorySpace<Callback, RetrievalPolicy, FreeListType, FullListType>::free_list() {
  return _free_list;
}

template <typename Callback, template <typename> class RetrievalPolicy, typename FreeListType, typename FullListType>
inline const FreeListType& JfrMemorySpace<Callback, RetrievalPolicy, FreeListType, FullListType>::free_list() const {
  return _free_list;
}

template <typename Callback, template <typename> class RetrievalPolicy, typename FreeListType, typename FullListType>
inline FullListType& JfrMemorySpace<Callback, RetrievalPolicy, FreeListType, FullListType>::full_list() {
  return _full_list;
}

template <typename Callback, template <typename> class RetrievalPolicy, typename FreeListType, typename FullListType>
inline const FullListType& JfrMemorySpace<Callback, RetrievalPolicy, FreeListType, FullListType>::full_list() const {
  return _full_list;
}

template <typename Callback, template <typename> class RetrievalPolicy, typename FreeListType, typename FullListType>
inline bool JfrMemorySpace<Callback, RetrievalPolicy, FreeListType, FullListType>::free_list_is_empty() const {
  return _free_list.is_empty();
}

template <typename Callback, template <typename> class RetrievalPolicy, typename FreeListType, typename FullListType>
inline bool JfrMemorySpace<Callback, RetrievalPolicy, FreeListType, FullListType>::free_list_is_nonempty() const {
  return !free_list_is_empty();
}

template <typename Callback, template <typename> class RetrievalPolicy, typename FreeListType, typename FullListType>
inline bool JfrMemorySpace<Callback, RetrievalPolicy, FreeListType, FullListType>::full_list_is_empty() const {
  return _full_list.is_empty();
}

template <typename Callback, template <typename> class RetrievalPolicy, typename FreeListType, typename FullListType>
inline bool JfrMemorySpace<Callback, RetrievalPolicy, FreeListType, FullListType>::full_list_is_nonempty() const {
  return !full_list_is_empty();
}

template <typename Callback, template <typename> class RetrievalPolicy, typename FreeListType, typename FullListType>
bool JfrMemorySpace<Callback, RetrievalPolicy, FreeListType, FullListType>::in_free_list(const typename FreeListType::Node* node) const {
  return _free_list.in_list(node);
}

template <typename Callback, template <typename> class RetrievalPolicy, typename FreeListType, typename FullListType>
bool JfrMemorySpace<Callback, RetrievalPolicy, FreeListType, FullListType>::in_full_list(const typename FreeListType::Node* node) const {
  return _full_list.in_list(node);
}

template <typename Callback, template <typename> class RetrievalPolicy, typename FreeListType, typename FullListType>
bool JfrMemorySpace<Callback, RetrievalPolicy, FreeListType, FullListType>::in_mspace(const typename FreeListType::Node* node) const {
  return in_full_list(node) || in_free_list(node);
}

// allocations are even multiples of the mspace min size
static inline size_t align_allocation_size(size_t requested_size, size_t min_elem_size) {
  assert((int)min_elem_size % os::vm_page_size() == 0, "invariant");
  u8 alloc_size_bytes = min_elem_size;
  while (requested_size > alloc_size_bytes) {
    alloc_size_bytes <<= 1;
  }
  assert((int)alloc_size_bytes % os::vm_page_size() == 0, "invariant");
  return (size_t)alloc_size_bytes;
}

template <typename Callback, template <typename> class RetrievalPolicy, typename FreeListType, typename FullListType>
inline typename FreeListType::NodePtr JfrMemorySpace<Callback, RetrievalPolicy, FreeListType, FullListType>::allocate(size_t size) {
  const size_t aligned_size_bytes = align_allocation_size(size, _min_elem_size);
  void* const allocation = JfrCHeapObj::new_array<u1>(aligned_size_bytes + sizeof(Node));
  if (allocation == NULL) {
    return NULL;
  }
  NodePtr node = new (allocation) Node();
  assert(node != NULL, "invariant");
  if (!node->initialize(sizeof(Node), aligned_size_bytes)) {
    JfrCHeapObj::free(node, aligned_size_bytes + sizeof(Node));
    return NULL;
  }
  return node;
}

template <typename Callback, template <typename> class RetrievalPolicy, typename FreeListType, typename FullListType>
inline void JfrMemorySpace<Callback, RetrievalPolicy, FreeListType, FullListType>::deallocate(typename FreeListType::NodePtr node) {
  assert(node != NULL, "invariant");
  assert(!in_free_list(node), "invariant");
  assert(!in_full_list(node), "invariant");
  assert(node != NULL, "invariant");
  JfrCHeapObj::free(node, node->total_size());
}

template <typename Callback, template <typename> class RetrievalPolicy, typename FreeListType, typename FullListType>
inline typename FreeListType::NodePtr JfrMemorySpace<Callback, RetrievalPolicy, FreeListType, FullListType>::acquire(Thread* thread, size_t size /* 0 */) {
  return RetrievalPolicy<JfrMemorySpace<Callback, RetrievalPolicy, FreeListType, FullListType> >::acquire(this, thread, size);
}

template <typename Callback, template <typename> class RetrievalPolicy, typename FreeListType, typename FullListType>
inline void JfrMemorySpace<Callback, RetrievalPolicy, FreeListType, FullListType>::release(typename FreeListType::NodePtr node) {
  assert(node != NULL, "invariant");
  if (node->transient()) {
    deallocate(node);
    return;
  }
  assert(node->empty(), "invariant");
  assert(!node->retired(), "invariant");
  assert(node->identity() == NULL, "invariant");
  if (should_populate_free_list()) {
    add_to_free_list(node);
  } else {
    deallocate(node);
  }
}

template <typename Callback, template <typename> class RetrievalPolicy, typename FreeListType, typename FullListType>
inline void JfrMemorySpace<Callback, RetrievalPolicy, FreeListType, FullListType>::add_to_free_list(typename FreeListType::NodePtr node) {
  assert(node != NULL, "invariant");
  assert(!in_free_list(node), "invariant");
  _free_list.add(node);
  Atomic::inc(&_free_list_count);
}

template <typename Callback, template <typename> class RetrievalPolicy, typename FreeListType, typename FullListType>
inline void JfrMemorySpace<Callback, RetrievalPolicy, FreeListType, FullListType>::add_to_full_list(typename FreeListType::NodePtr node) {
  assert(node != NULL, "invariant");
  _full_list.add(node);
}

template <typename Callback, template <typename> class RetrievalPolicy, typename FreeListType, typename FullListType>
inline typename FreeListType::NodePtr JfrMemorySpace<Callback, RetrievalPolicy, FreeListType, FullListType>::remove_from_free_list() {
  NodePtr node = _free_list.remove();
  if (node != NULL) {
    decrement_free_list_count();
  }
  return node;
}

template <typename Callback, template <typename> class RetrievalPolicy, typename FreeListType, typename FullListType>
inline typename FreeListType::NodePtr JfrMemorySpace<Callback, RetrievalPolicy, FreeListType, FullListType>::remove_from_full_list() {
  return _full_list.remove();
}

template <typename Callback, template <typename> class RetrievalPolicy, typename FreeListType, typename FullListType>
inline void JfrMemorySpace<Callback, RetrievalPolicy, FreeListType, FullListType>::decrement_free_list_count() {
  Atomic::dec(&_free_list_count);
}

template <typename Callback, template <typename> class RetrievalPolicy, typename FreeListType, typename FullListType>
inline typename FreeListType::NodePtr JfrMemorySpace<Callback, RetrievalPolicy, FreeListType, FullListType>::clear_free_list() {
  NodePtr node = _free_list.clear();
  NodePtr temp = node;
  while (temp != NULL) {
    decrement_free_list_count();
    temp = temp->next();
  }
  return node;
}

template <typename Callback, template <typename> class RetrievalPolicy, typename FreeListType, typename FullListType>
inline typename FreeListType::NodePtr JfrMemorySpace<Callback, RetrievalPolicy, FreeListType, FullListType>::clear_full_list() {
  return _full_list.clear();
}

template <typename Callback, template <typename> class RetrievalPolicy, typename FreeListType, typename FullListType>
template <typename Processor>
inline void JfrMemorySpace<Callback, RetrievalPolicy, FreeListType, FullListType>::iterate(Processor& processor, bool full_list /* true */) {
  if (full_list) {
    _full_list.iterate(processor);
  } else {
    _free_list.iterate(processor);
  }
}

template <typename Callback, template <typename> class RetrievalPolicy, typename FreeListType, typename FullListType>
inline void JfrMemorySpace<Callback, RetrievalPolicy, FreeListType, FullListType>::register_full(typename FreeListType::NodePtr node, Thread* thread) {
  _callback->register_full(node, thread);
}

template <typename Mspace, typename Callback>
static inline Mspace* create_mspace(size_t min_elem_size, size_t limit, size_t free_list_cache_count, Callback* cb) {
  Mspace* const mspace = new Mspace(min_elem_size, limit, free_list_cache_count, cb);
  if (mspace != NULL) {
    mspace->initialize();
  }
  return mspace;
}

template <typename Mspace>
inline typename Mspace::NodePtr mspace_allocate(size_t size, Mspace* mspace) {
  return mspace->allocate(size);
}

template <typename Mspace>
inline typename Mspace::NodePtr mspace_allocate_acquired(size_t size, Mspace* mspace, Thread* thread) {
  typename Mspace::NodePtr node = mspace_allocate(size, mspace);
  if (node == NULL) return NULL;
  node->set_identity(thread);
  return node;
}

template <typename Mspace>
inline typename Mspace::NodePtr mspace_allocate_transient(size_t size, Mspace* mspace, Thread* thread) {
  typename Mspace::NodePtr node = mspace_allocate_acquired(size, mspace, thread);
  if (node == NULL) return NULL;
  assert(node->acquired_by_self(), "invariant");
  node->set_transient();
  return node;
}

template <typename Mspace>
inline typename Mspace::NodePtr mspace_allocate_transient_lease(size_t size, Mspace* mspace, Thread* thread) {
  typename Mspace::NodePtr node = mspace_allocate_transient(size, mspace, thread);
  if (node == NULL) return NULL;
  assert(node->transient(), "invariant");
  node->set_lease();
  return node;
}

template <typename Mspace>
inline typename Mspace::NodePtr mspace_allocate_to_full(size_t size, Mspace* mspace, Thread* thread) {
  typename Mspace::NodePtr node = mspace_allocate_acquired(size, mspace, thread);
  if (node == NULL) return NULL;
  assert(node->acquired_by_self(), "invariant");
  mspace->add_to_full_list(node);
  return node;
}

template <typename Mspace>
inline typename Mspace::NodePtr mspace_allocate_transient_to_full(size_t size, Mspace* mspace, Thread* thread) {
  typename Mspace::NodePtr node = mspace_allocate_transient(size, mspace, thread);
  if (node == NULL) return NULL;
  assert(node->transient(), "invariant");
  mspace->add_to_full_list(node);
  return node;
}

template <typename Mspace>
inline typename Mspace::NodePtr mspace_allocate_transient_lease_to_full(size_t size, Mspace* mspace, Thread* thread) {
  typename Mspace::NodePtr node = mspace_allocate_transient_lease(size, mspace, thread);
  if (node == NULL) return NULL;
  assert(node->lease(), "invariant");
  mspace->add_to_full_list(node);
  return node;
}

template <typename Mspace>
inline typename Mspace::NodePtr mspace_allocate_transient_lease_to_free(size_t size, Mspace* mspace, Thread* thread) {
  typename Mspace::NodePtr node = mspace_allocate_transient_lease(size, mspace, thread);
  if (node == NULL) return NULL;
  assert(node->lease(), "invariant");
  mspace->add_to_free_list(node);
  return node;
}

template <typename Mspace>
inline typename Mspace::NodePtr mspace_get_free(size_t size, Mspace* mspace, Thread* thread) {
  return mspace->acquire(thread, size);
}

template <typename Mspace>
inline typename Mspace::NodePtr mspace_get_free_with_retry(size_t size, Mspace* mspace, size_t retry_count, Thread* thread) {
  assert(size <= mspace->min_elem_size(), "invariant");
  for (size_t i = 0; i < retry_count; ++i) {
    typename Mspace::NodePtr node = mspace_get_free(size, mspace, thread);
    if (node != NULL) {
      return node;
    }
  }
  return NULL;
}

template <typename Mspace>
inline typename Mspace::NodePtr mspace_get_free_lease_with_retry(size_t size, Mspace* mspace, size_t retry_count, Thread* thread) {
  typename Mspace::NodePtr node = mspace_get_free_with_retry(size, mspace, retry_count, thread);
  if (node != NULL) {
    node->set_lease();
  }
  return node;
}

template <typename Mspace>
inline typename Mspace::NodePtr mspace_get_free_to_full(size_t size, Mspace* mspace, Thread* thread) {
  assert(size <= mspace->min_elem_size(), "invariant");
  typename Mspace::NodePtr node = mspace_get_free(size, mspace, thread);
  if (node == NULL) {
    return NULL;
  }
  assert(node->acquired_by_self(), "invariant");
  assert(!mspace->in_free_list(node), "invariant");
  mspace->add_to_full_list(node);
  return node;
}

template <typename Mspace>
inline typename Mspace::NodePtr mspace_get_to_full(size_t size, Mspace* mspace, Thread* thread) {
  if (size <= mspace->min_elem_size()) {
    typename Mspace::NodePtr node = mspace_get_free_to_full(size, mspace, thread);
    if (node != NULL) {
      return node;
    }
  }
  return mspace_allocate_to_full(size, mspace, thread);
}

template <typename Mspace>
inline void mspace_release(typename Mspace::NodePtr node, Mspace* mspace) {
  assert(node != NULL, "invariant");
  assert(node->unflushed_size() == 0, "invariant");
  assert(mspace != NULL, "invariant");
  mspace->release(node);
}

template <typename Processor, typename Mspace>
inline void process_full_list(Processor& processor, Mspace* mspace) {
  assert(mspace != NULL, "invariant");
  if (mspace->full_list_is_nonempty()) {
    mspace->iterate(processor);
  }
}

template <typename Processor, typename Mspace>
inline void process_free_list(Processor& processor, Mspace* mspace) {
  assert(mspace != NULL, "invariant");
  assert(mspace->free_list_is_nonempty(), "invariant");
  mspace->iterate(processor, false);
}

template <typename Mspace>
class ReleaseOp : public StackObj {
 private:
  Mspace* _mspace;
 public:
  typedef typename Mspace::Node Node;
  ReleaseOp(Mspace* mspace) : _mspace(mspace) {}
  bool process(typename Mspace::NodePtr node);
  size_t processed() const { return 0; }
};

template <typename Mspace>
inline bool ReleaseOp<Mspace>::process(typename Mspace::NodePtr node) {
  assert(node != NULL, "invariant");
  // assumes some means of exclusive access to the node
  if (node->transient()) {
    // make sure the transient node is already detached
    _mspace->release(node);
    return true;
  }
  node->reinitialize();
  if (node->identity() != NULL) {
    assert(node->empty(), "invariant");
    assert(!node->retired(), "invariant");
    node->release(); // publish
  }
  return true;
}

template <typename Mspace>
class ScavengingReleaseOp : public StackObj {
 private:
  Mspace* _mspace;
  typename Mspace::FullList& _full_list;
  typename Mspace::NodePtr _prev;
  size_t _count;
  size_t _amount;
 public:
  typedef typename Mspace::Node Node;
  ScavengingReleaseOp(Mspace* mspace) :
    _mspace(mspace), _full_list(mspace->full_list()), _prev(NULL), _count(0), _amount(0) {}
  bool process(typename Mspace::NodePtr node);
  size_t processed() const { return _count; }
  size_t amount() const { return _amount; }
};

template <typename Mspace>
inline bool ScavengingReleaseOp<Mspace>::process(typename Mspace::NodePtr node) {
  assert(node != NULL, "invariant");
  if (node->retired()) {
    _prev = _full_list.excise(_prev, node);
    if (node->transient()) {
      _mspace->deallocate(node);
      return true;
    }
    assert(node->identity() != NULL, "invariant");
    assert(node->empty(), "invariant");
    assert(!node->lease(), "invariant");
    assert(!node->excluded(), "invariant");
    ++_count;
    _amount += node->total_size();
    node->clear_retired();
    node->release();
    mspace_release(node, _mspace);
    return true;
  }
  _prev = node;
  return true;
}

#ifdef ASSERT
template <typename Node>
inline void assert_migration_state(const Node* old, const Node* new_node, size_t used, size_t requested) {
  assert(old != NULL, "invariant");
  assert(new_node != NULL, "invariant");
  assert(old->pos() >= old->start(), "invariant");
  assert(old->pos() + used <= old->end(), "invariant");
  assert(new_node->free_size() >= (used + requested), "invariant");
}
#endif // ASSERT

template <typename Node>
inline void migrate_outstanding_writes(const Node* old, Node* new_node, size_t used, size_t requested) {
  DEBUG_ONLY(assert_migration_state(old, new_node, used, requested);)
  if (used > 0) {
    memcpy(new_node->pos(), old->pos(), used);
  }
}

#endif // SHARE_JFR_RECORDER_STORAGE_JFRMEMORYSPACE_INLINE_HPP
