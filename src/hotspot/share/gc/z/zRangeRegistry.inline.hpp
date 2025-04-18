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

#ifndef SHARE_GC_Z_ZRANGEREGISTRY_INLINE_HPP
#define SHARE_GC_Z_ZRANGEREGISTRY_INLINE_HPP

#include "gc/z/zRangeRegistry.hpp"

#include "gc/z/zAddress.inline.hpp"
#include "gc/z/zList.inline.hpp"
#include "gc/z/zLock.inline.hpp"

template <typename Range>
void ZRangeRegistry<Range>::move_into(const Range& range) {
  assert(!range.is_null(), "Invalid range");
  assert(check_limits(range), "Range outside limits");

  const offset start = range.start();
  const offset_end end = range.end();
  const size_t size = range.size();

  ZListIterator<Node> iter(&_list);
  for (Node* node; iter.next(&node);) {
    if (node->start() < start) {
      continue;
    }

    Node* const prev = _list.prev(node);
    if (prev != nullptr && start == prev->end()) {
      if (end == node->start()) {
        // Merge with prev and current ranges
        grow_from_back(prev->range(), size);
        grow_from_back(prev->range(), node->size());
        _list.remove(node);
        delete node;
      } else {
        // Merge with prev range
        grow_from_back(prev->range(), size);
      }
    } else if (end == node->start()) {
      // Merge with current range
      grow_from_front(node->range(), size);
    } else {
      // Insert range before current range
      assert(end < node->start(), "Areas must not overlap");
      Node* const new_node = new Node(start, size);
      _list.insert_before(node, new_node);
    }

    // Done
    return;
  }

  // Insert last
  Node* const last = _list.last();
  if (last != nullptr && start == last->end()) {
    // Merge with last range
    grow_from_back(last->range(), size);
  } else {
    // Insert new node last
    Node* const new_node = new Node(start, size);
    _list.insert_last(new_node);
  }
}

template <typename Range>
void ZRangeRegistry<Range>::insert_inner(const Range& range) {
  if (_callbacks._prepare_for_hand_back != nullptr) {
    _callbacks._prepare_for_hand_back(range);
  }
  move_into(range);
}

template <typename Range>
void ZRangeRegistry<Range>::register_inner(const Range& range) {
  move_into(range);
}

template <typename Range>
void ZRangeRegistry<Range>::grow_from_front(Range* range, size_t size) {
  if (_callbacks._grow != nullptr) {
    const Range from = *range;
    const Range to = Range(from.start() - size, from.size() + size);
    _callbacks._grow(from, to);
  }
  range->grow_from_front(size);
}

template <typename Range>
void ZRangeRegistry<Range>::grow_from_back(Range* range, size_t size) {
  if (_callbacks._grow != nullptr) {
    const Range from = *range;
    const Range to = Range(from.start(), from.size() + size);
    _callbacks._grow(from, to);
  }
  range->grow_from_back(size);
}

template <typename Range>
Range ZRangeRegistry<Range>::shrink_from_front(Range* range, size_t size) {
  if (_callbacks._shrink != nullptr) {
    const Range from = *range;
    const Range to = from.last_part(size);
    _callbacks._shrink(from, to);
  }
  return range->shrink_from_front(size);
}

template <typename Range>
Range ZRangeRegistry<Range>::shrink_from_back(Range* range, size_t size) {
  if (_callbacks._shrink != nullptr) {
    const Range from = *range;
    const Range to = from.first_part(from.size() - size);
    _callbacks._shrink(from, to);
  }
  return range->shrink_from_back(size);
}

template <typename Range>
Range ZRangeRegistry<Range>::remove_from_low_inner(size_t size) {
  ZListIterator<Node> iter(&_list);
  for (Node* node; iter.next(&node);) {
    if (node->size() >= size) {
      Range range;

      if (node->size() == size) {
        // Exact match, remove range
        _list.remove(node);
        range = *node->range();
        delete node;
      } else {
        // Larger than requested, shrink range
        range = shrink_from_front(node->range(), size);
      }

      if (_callbacks._prepare_for_hand_out != nullptr) {
        _callbacks._prepare_for_hand_out(range);
      }

      return range;
    }
  }

  // Out of memory
  return Range();
}

template <typename Range>
Range ZRangeRegistry<Range>::remove_from_low_at_most_inner(size_t size) {
  Node* const node = _list.first();
  if (node == nullptr) {
    // List is empty
    return Range();
  }

  Range range;

  if (node->size() <= size) {
    // Smaller than or equal to requested, remove range
    _list.remove(node);
    range = *node->range();
    delete node;
  } else {
    // Larger than requested, shrink range
    range = shrink_from_front(node->range(), size);
  }

  if (_callbacks._prepare_for_hand_out) {
    _callbacks._prepare_for_hand_out(range);
  }

  return range;
}

template <typename Range>
size_t ZRangeRegistry<Range>::remove_from_low_many_at_most_inner(size_t size, ZArray<Range>* out) {
  size_t to_remove = size;

  while (to_remove > 0) {
    const Range range = remove_from_low_at_most_inner(to_remove);

    if (range.is_null()) {
      // The requested amount is not available
      return size - to_remove;
    }

    to_remove -= range.size();
    out->append(range);
  }

  return size;
}

template <typename Range>
ZRangeRegistry<Range>::Callbacks::Callbacks()
  : _prepare_for_hand_out(nullptr),
    _prepare_for_hand_back(nullptr),
    _grow(nullptr),
    _shrink(nullptr) {}

template <typename Range>
ZRangeRegistry<Range>::ZRangeRegistry()
  : _list(),
    _callbacks(),
    _limits() {}

template <typename Range>
void ZRangeRegistry<Range>::register_callbacks(const Callbacks& callbacks) {
  _callbacks = callbacks;
}

template <typename Range>
void ZRangeRegistry<Range>::register_range(const Range& range) {
  ZLocker<ZLock> locker(&_lock);
  register_inner(range);
}

template <typename Range>
bool ZRangeRegistry<Range>::unregister_first(Range* out) {
  // Unregistering a range doesn't call a "prepare_to_hand_out" callback
  // because the range is unregistered and not handed out to be used.

  ZLocker<ZLock> locker(&_lock);

  if (_list.is_empty()) {
    return false;
  }

  // Don't invoke the "prepare_to_hand_out" callback

  Node* const node = _list.remove_first();

  // Return the range
  *out = *node->range();

  delete node;

  return true;
}

template <typename Range>
inline bool ZRangeRegistry<Range>::is_empty() const {
  return _list.is_empty();
}

template <typename Range>
bool ZRangeRegistry<Range>::is_contiguous() const {
  return _list.size() == 1;
}

template <typename Range>
void ZRangeRegistry<Range>::anchor_limits() {
  assert(_limits.is_null(), "Should only anchor limits once");

  if (_list.is_empty()) {
    return;
  }

  const offset start = _list.first()->start();
  const size_t size = _list.last()->end() - start;

  _limits = Range(start, size);
}

template <typename Range>
bool ZRangeRegistry<Range>::limits_contain(const Range& range) const {
  if (_limits.is_null() || range.is_null()) {
    return false;
  }

  return range.start() >= _limits.start() && range.end() <= _limits.end();
}

template <typename Range>
bool ZRangeRegistry<Range>::check_limits(const Range& range) const {
  if (_limits.is_null()) {
    // Limits not anchored
    return true;
  }

  // Otherwise, check that other is within the limits
  return limits_contain(range);
}

template <typename Range>
typename ZRangeRegistry<Range>::offset ZRangeRegistry<Range>::peek_low_address() const {
  ZLocker<ZLock> locker(&_lock);

  const Node* const node = _list.first();
  if (node != nullptr) {
    return node->start();
  }

  // Out of memory
  return offset::invalid;
}

template <typename Range>
typename ZRangeRegistry<Range>::offset_end ZRangeRegistry<Range>::peak_high_address_end() const {
  ZLocker<ZLock> locker(&_lock);

  const Node* const node = _list.last();
  if (node != nullptr) {
    return node->end();
  }

  // Out of memory
  return offset_end::invalid;
}

template <typename Range>
void ZRangeRegistry<Range>::insert(const Range& range) {
  ZLocker<ZLock> locker(&_lock);
  insert_inner(range);
}

template <typename Range>
void ZRangeRegistry<Range>::insert_and_remove_from_low_many(const Range& range, ZArray<Range>* out) {
  ZLocker<ZLock> locker(&_lock);

  const size_t size = range.size();

  // Insert the range
  insert_inner(range);

  // Remove (hopefully) at a lower address
  const size_t removed = remove_from_low_many_at_most_inner(size, out);

  // This should always succeed since we freed the same amount.
  assert(removed == size, "must succeed");
}

template <typename Range>
Range ZRangeRegistry<Range>::insert_and_remove_from_low_exact_or_many(size_t size, ZArray<Range>* in_out) {
  ZLocker<ZLock> locker(&_lock);

  size_t inserted = 0;

  // Insert everything
  ZArrayIterator<Range> iter(in_out);
  for (Range mem; iter.next(&mem);) {
    insert_inner(mem);
    inserted += mem.size();
  }

  // Clear stored memory so that we can populate it below
  in_out->clear();

  // Try to find and remove a contiguous chunk
  Range range = remove_from_low_inner(size);
  if (!range.is_null()) {
    return range;
  }

  // Failed to find a contiguous chunk, split it up into smaller chunks and
  // only remove up to as much that has been inserted.
  size_t removed = remove_from_low_many_at_most_inner(inserted, in_out);
  assert(removed == inserted, "Should be able to get back as much as we previously inserted");
  return Range();
}

template <typename Range>
Range ZRangeRegistry<Range>::remove_from_low(size_t size) {
  ZLocker<ZLock> locker(&_lock);
  Range range = remove_from_low_inner(size);
  return range;
}

template <typename Range>
Range ZRangeRegistry<Range>::remove_from_low_at_most(size_t size) {
  ZLocker<ZLock> lock(&_lock);
  Range range = remove_from_low_at_most_inner(size);
  return range;
}

template <typename Range>
size_t ZRangeRegistry<Range>::remove_from_low_many_at_most(size_t size, ZArray<Range>* out) {
  ZLocker<ZLock> lock(&_lock);
  return remove_from_low_many_at_most_inner(size, out);
}

template <typename Range>
Range ZRangeRegistry<Range>::remove_from_high(size_t size) {
  ZLocker<ZLock> locker(&_lock);

  ZListReverseIterator<Node> iter(&_list);
  for (Node* node; iter.next(&node);) {
    if (node->size() >= size) {
      Range range;

      if (node->size() == size) {
        // Exact match, remove range
        _list.remove(node);
        range = *node->range();
        delete node;
      } else {
        // Larger than requested, shrink range
        range = shrink_from_back(node->range(), size);
      }

      if (_callbacks._prepare_for_hand_out != nullptr) {
        _callbacks._prepare_for_hand_out(range);
      }

      return range;
    }
  }

  // Out of memory
  return Range();
}

template <typename Range>
void ZRangeRegistry<Range>::transfer_from_low(ZRangeRegistry* other, size_t size) {
  assert(other->_list.is_empty(), "Should only be used for initialization");

  ZLocker<ZLock> locker(&_lock);
  size_t to_move = size;

  ZListIterator<Node> iter(&_list);
  for (Node* node; iter.next(&node);) {
    Node* to_transfer;

    if (node->size() <= to_move) {
      // Smaller than or equal to requested, remove range
      _list.remove(node);
      to_transfer = node;
    } else {
      // Larger than requested, shrink range
      const Range range = shrink_from_front(node->range(), to_move);
      to_transfer = new Node(range);
    }

    // Insert into the other list
    //
    // The from list is sorted, the other list starts empty, and the inserts
    // come in sort order, so we can insert_last here.
    other->_list.insert_last(to_transfer);

    to_move -= to_transfer->size();
    if (to_move == 0) {
      break;
    }
  }

  assert(to_move == 0, "Should have transferred requested size");
}

#endif // SHARE_GC_Z_ZRANGEREGISTRY_INLINE_HPP
