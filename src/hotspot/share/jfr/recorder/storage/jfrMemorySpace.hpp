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
#ifndef SHARE_JFR_RECORDER_STORAGE_JFRMEMORYSPACE_HPP
#define SHARE_JFR_RECORDER_STORAGE_JFRMEMORYSPACE_HPP

#include "jfr/utilities/jfrAllocation.hpp"
#include "jfr/utilities/jfrDoublyLinkedList.hpp"
#include "jfr/utilities/jfrIterator.hpp"

template <typename T, template <typename> class RetrievalType, typename Callback>
class JfrMemorySpace : public JfrCHeapObj {
 public:
  typedef T Type;
  typedef RetrievalType<JfrMemorySpace<T, RetrievalType, Callback> > Retrieval;
  typedef JfrDoublyLinkedList<Type> List;
  typedef StopOnNullIterator<List> Iterator;
 private:
  List _free;
  List _full;
  size_t _min_elem_size;
  size_t _limit_size;
  size_t _cache_count;
  Callback* _callback;

  bool should_populate_cache() const { return _free.count() < _cache_count; }

 public:
  JfrMemorySpace(size_t min_elem_size, size_t limit_size, size_t cache_count, Callback* callback);
  ~JfrMemorySpace();
  bool initialize();

  size_t min_elem_size() const { return _min_elem_size; }
  size_t limit_size() const { return _limit_size; }

  bool has_full() const { return _full.head() != NULL; }
  bool has_free() const { return _free.head() != NULL; }
  bool is_full_empty() const { return !has_full(); }
  bool is_free_empty() const { return !has_free(); }

  size_t full_count() const { return _full.count(); }
  size_t free_count() const { return _free.count(); }

  List& full() { return _full; }
  const List& full() const { return _full; }
  List& free() { return _free; }
  const List& free() const { return _free; }

  Type* full_head() { return _full.head(); }
  Type* full_tail() { return _full.tail(); }
  Type* free_head() { return _free.head(); }
  Type* free_tail() { return _free.tail(); }

  void insert_free_head(Type* t) { _free.prepend(t); }
  void insert_free_tail(Type* t) { _free.append(t); }
  void insert_free_tail(Type* t, Type* tail, size_t count) { _free.append_list(t, tail, count); }
  void insert_full_head(Type* t) { _full.prepend(t); }
  void insert_full_tail(Type* t) { _full.append(t); }
  void insert_full_tail(Type* t, Type* tail, size_t count) { _full.append_list(t, tail, count); }

  Type* remove_free(Type* t) { return _free.remove(t); }
  Type* remove_full(Type* t) { return _full.remove(t); }
  Type* remove_free_tail() { _free.remove(_free.tail()); }
  Type* remove_full_tail() { return _full.remove(_full.tail()); }
  Type* clear_full(bool return_tail = false) { return _full.clear(return_tail); }
  Type* clear_free(bool return_tail = false) { return _free.clear(return_tail); }
  void release_full(Type* t);
  void release_free(Type* t);

  void register_full(Type* t, Thread* thread) { _callback->register_full(t, thread); }
  void lock() { _callback->lock(); }
  void unlock() { _callback->unlock(); }
  DEBUG_ONLY(bool is_locked() const { return _callback->is_locked(); })

  Type* allocate(size_t size);
  void deallocate(Type* t);
  Type* get(size_t size, Thread* thread) { return Retrieval::get(size, this, thread); }

  template <typename IteratorCallback, typename IteratorType>
  void iterate(IteratorCallback& callback, bool full = true, jfr_iter_direction direction = forward);

  bool in_full_list(const Type* t) const { return _full.in_list(t); }
  bool in_free_list(const Type* t) const { return _free.in_list(t); }
};

#endif // SHARE_JFR_RECORDER_STORAGE_JFRMEMORYSPACE_HPP
