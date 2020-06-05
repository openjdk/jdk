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
#ifndef SHARE_JFR_RECORDER_STORAGE_JFRMEMORYSPACE_HPP
#define SHARE_JFR_RECORDER_STORAGE_JFRMEMORYSPACE_HPP

#include "jfr/utilities/jfrAllocation.hpp"

template <typename Callback, template <typename> class RetrievalPolicy, typename FreeListType, typename FullListType = FreeListType>
class JfrMemorySpace : public JfrCHeapObj {
 public:
  typedef FreeListType FreeList;
  typedef FullListType FullList;
  typedef typename FreeListType::Node Node;
  typedef typename FreeListType::NodePtr NodePtr;
 private:
  FreeList _free_list;
  FullList _full_list;
  const size_t _min_elem_size;
  const size_t _limit_size;
  const size_t _free_list_cache_count;
  size_t _free_list_count;
  Callback* _callback;

  bool should_populate_free_list() const;

 public:
  JfrMemorySpace(size_t min_elem_size, size_t limit_size, size_t free_list_cache_count, Callback* callback);
  ~JfrMemorySpace();
  bool initialize();

  size_t min_elem_size() const;
  size_t limit_size() const;

  NodePtr allocate(size_t size);
  void deallocate(NodePtr node);

  NodePtr acquire(Thread* thread, size_t size = 0);
  void release(NodePtr node);

  FreeList& free_list();
  const FreeList& free_list() const;

  FullList& full_list();
  const FullList& full_list() const;

  bool free_list_is_empty() const;
  bool full_list_is_empty() const;
  bool free_list_is_nonempty() const;
  bool full_list_is_nonempty() const;
  bool in_free_list(const Node* node) const;
  bool in_full_list(const Node* node) const;
  bool in_mspace(const Node* node) const;

  void add_to_free_list(NodePtr node);
  void add_to_full_list(NodePtr node);

  NodePtr remove_from_free_list();
  NodePtr remove_from_full_list();

  NodePtr clear_free_list();
  NodePtr clear_full_list();

  template <typename Processor>
  void iterate(Processor& processor, bool full_list = true);

  void decrement_free_list_count();

  void register_full(NodePtr node, Thread* thread);
};

#endif // SHARE_JFR_RECORDER_STORAGE_JFRMEMORYSPACE_HPP
