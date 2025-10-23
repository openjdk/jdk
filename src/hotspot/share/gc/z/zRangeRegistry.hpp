/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
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
 */

#ifndef SHARE_GC_Z_ZRANGEREGISTRY_HPP
#define SHARE_GC_Z_ZRANGEREGISTRY_HPP

#include "gc/z/zAddress.hpp"
#include "gc/z/zList.hpp"
#include "gc/z/zLock.hpp"
#include "memory/allocation.hpp"
#include "utilities/globalDefinitions.hpp"

template <typename T>
class ZArray;

template <typename Range>
class ZRangeRegistry {
  friend class ZVirtualMemoryManagerTest;

private:
  // The node type for the list of Ranges
  class Node;

public:
  using offset     = typename Range::offset;
  using offset_end = typename Range::offset_end;

  typedef void (*CallbackPrepare)(const Range& range);
  typedef void (*CallbackResize)(const Range& from, const Range& to);

  struct Callbacks {
    CallbackPrepare _prepare_for_hand_out;
    CallbackPrepare _prepare_for_hand_back;
    CallbackResize  _grow;
    CallbackResize  _shrink;

    Callbacks();
  };

private:
  mutable ZLock _lock;
  ZList<Node>   _list;
  Callbacks     _callbacks;
  Range         _limits;

  void move_into(const Range& range);

  void insert_inner(const Range& range);
  void register_inner(const Range& range);

  void grow_from_front(Range* range, size_t size);
  void grow_from_back(Range* range, size_t size);

  Range shrink_from_front(Range* range, size_t size);
  Range shrink_from_back(Range* range, size_t size);

  Range remove_from_low_inner(size_t size);
  Range remove_from_low_at_most_inner(size_t size);

  size_t remove_from_low_many_at_most_inner(size_t size, ZArray<Range>* out);

  bool check_limits(const Range& range) const;

public:
  ZRangeRegistry();

  void register_callbacks(const Callbacks& callbacks);

  void register_range(const Range& range);
  bool unregister_first(Range* out);

  bool is_empty() const;
  bool is_contiguous() const;

  void anchor_limits();
  bool limits_contain(const Range& range) const;

  offset peek_low_address() const;
  offset_end peak_high_address_end() const;

  void insert(const Range& range);

  void insert_and_remove_from_low_many(const Range& range, ZArray<Range>* out);
  Range insert_and_remove_from_low_exact_or_many(size_t size, ZArray<Range>* in_out);

  Range remove_from_low(size_t size);
  Range remove_from_low_at_most(size_t size);
  size_t remove_from_low_many_at_most(size_t size, ZArray<Range>* out);
  Range remove_from_high(size_t size);

  void transfer_from_low(ZRangeRegistry* other, size_t size);
};

template <typename Range>
class ZRangeRegistry<Range>::Node : public CHeapObj<mtGC> {
  friend class ZList<Node>;

private:
  using offset     = typename Range::offset;
  using offset_end = typename Range::offset_end;

  Range           _range;
  ZListNode<Node> _node;

public:
  Node(offset start, size_t size)
    : _range(start, size),
      _node() {}

  Node(const Range& other)
    : Node(other.start(), other.size()) {}

  Range* range() {
    return &_range;
  }

  offset start() const {
    return _range.start();
  }

  offset_end end() const {
    return _range.end();
  }

  size_t size() const {
    return _range.size();
  }
};

#endif // SHARE_GC_Z_ZRANGEREGISTRY_HPP
