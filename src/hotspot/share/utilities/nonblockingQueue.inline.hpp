/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_UTILITIES_NONBLOCKINGQUEUE_INLINE_HPP
#define SHARE_UTILITIES_NONBLOCKINGQUEUE_INLINE_HPP

#include "utilities/nonblockingQueue.hpp"

#include "runtime/atomic.hpp"
#include "runtime/atomicAccess.hpp"
#include "utilities/atomicNextAccess.hpp"

template<typename T, auto next_access>
T* NonblockingQueue<T, next_access>::next(const T& node) {
  return NextAccess::next(node);
}

template<typename T, auto next_access>
void NonblockingQueue<T, next_access>::set_next(T& node, T* new_next) {
  NextAccess::set_next(node, new_next);
}

template<typename T, auto next_access>
NonblockingQueue<T, next_access>::NonblockingQueue()
  : _head(nullptr), _tail(nullptr) {}

#ifdef ASSERT
template<typename T, auto next_access>
NonblockingQueue<T, next_access>::~NonblockingQueue() {
  assert(_head.load_relaxed() == nullptr, "precondition");
  assert(_tail.load_relaxed() == nullptr, "precondition");
}
#endif

// The end_marker must be uniquely associated with the specific queue, in
// case queue elements can make their way through multiple queues.  A
// pointer to the queue itself (after casting) satisfies that requirement.
template<typename T, auto next_access>
T* NonblockingQueue<T, next_access>::end_marker() const {
  return const_cast<T*>(reinterpret_cast<const T*>(this));
}

template<typename T, auto next_access>
T* NonblockingQueue<T, next_access>::first() const {
  T* head = _head.load_relaxed();
  return head == nullptr ? end_marker() : head;
}

template<typename T, auto next_access>
bool NonblockingQueue<T, next_access>::is_end(const T* entry) const {
  return entry == end_marker();
}

template<typename T, auto next_access>
bool NonblockingQueue<T, next_access>::empty() const {
  return _head.load_relaxed() == nullptr;
}

template<typename T, auto next_access>
size_t NonblockingQueue<T, next_access>::length() const {
  size_t result = 0;
  for (T* cur = first(); !is_end(cur); cur = next(*cur)) {
    ++result;
  }
  return result;
}

// An append operation atomically exchanges the new tail with the queue tail.
// It then sets the "next" value of the old tail to the head of the list being
// appended. If the old tail is null then the queue was empty, then the
// head of the list being appended is instead stored in the queue head.
//
// This means there is a period between the exchange and the old tail update
// where the queue sequence is split into two parts, the list from the queue
// head to the old tail, and the list being appended.  If there are concurrent
// push/append operations, each may introduce another such segment.  But they
// all eventually get resolved by their respective updates of their old tail's
// "next" value.  This also means that try_pop operation must handle an object
// differently depending on its "next" value.
//
// A push operation is just a degenerate append, where the object being pushed
// is both the head and the tail of the list being appended.
template<typename T, auto next_access>
void NonblockingQueue<T, next_access>::append(T& first, T& last) {
  assert(next(last) == nullptr, "precondition");
  // Make last the new end of the queue.  Any further push/appends will
  // extend after last.  We will try to extend from the previous end of
  // queue.
  set_next(last, end_marker());
  T* old_tail = _tail.fetch_then_set(&last);
  if (old_tail == nullptr) {
    // If old_tail is null then the queue was empty, and _head must also be
    // null. The correctness of this assertion depends on try_pop clearing
    // first _head then _tail when taking the last entry.
    assert(_head.load_relaxed() == nullptr, "invariant");
    // Fall through to common update of _head.
  } else if (is_end(NextAccess::cmpxchg(*old_tail, end_marker(), &first))) {
    // Successfully extended the queue list from old_tail to first.  No
    // other push/append could have competed with us, because we claimed
    // old_tail for extension.  We won any races with try_pop by changing
    // away from end-marker.  So we're done.
    //
    // Note that ABA is possible here.  A concurrent try_pop could take
    // old_tail before our update of old_tail's next_ptr, old_tail gets
    // recycled and re-added to the end of this queue, and then we
    // successfully cmpxchg, making the list in _tail circular.  Callers
    // must ensure this can't happen.
    return;
  } else {
    // A concurrent try_pop has claimed old_tail, so it is no longer in the
    // list. The queue was logically empty.  _head is either null or
    // old_tail, depending on how far try_pop operations have progressed.
    DEBUG_ONLY(T* old_head = _head.load_relaxed();)
    assert((old_head == nullptr) || (old_head == old_tail), "invariant");
    // Fall through to common update of _head.
  }
  // The queue was empty, and first should become the new _head.  The queue
  // will appear to be empty to any further try_pops until done.
  _head.store_relaxed(&first);
}

template<typename T, auto next_access>
bool NonblockingQueue<T, next_access>::try_pop(T** node_ptr) {
  // We only need memory_order_consume. Upgrade it to "load_acquire"
  // as the memory_order_consume API is not ready for use yet.
  T* old_head = _head.load_acquire();
  if (old_head == nullptr) {
    *node_ptr = nullptr;
    return true;                // Queue is empty.
  }

  T* next_node = NextAccess::next_acquire(*old_head);
  if (!is_end(next_node)) {
    // [Clause 1]
    // There are several cases for next_node.
    // (1) next_node is the extension of the queue's list.
    // (2) next_node is null, because a competing try_pop took old_head.
    // (3) next_node is the extension of some unrelated list, because a
    // competing try_pop took old_head and put it in some other list.
    //
    // Attempt to advance the list, replacing old_head with next_node in
    // _head.  The success or failure of that attempt, along with the value
    // of next_node, are used to partially determine which case we're in and
    // how to proceed.  In particular, advancement will fail for case (3).
    if (old_head != _head.cmpxchg(old_head, next_node)) {
      // [Clause 1a]
      // The cmpxchg to advance the list failed; a concurrent try_pop won
      // the race and claimed old_head.  This can happen for any of the
      // next_node cases.
      return false;
    } else if (next_node == nullptr) {
      // [Clause 1b]
      // The cmpxchg to advance the list succeeded, but a concurrent try_pop
      // has already claimed old_head (see [Clause 2] - old_head was the last
      // entry in the list) by nulling old_head's next field.  The advance set
      // _head to null, "helping" the competing try_pop.  _head will remain
      // nullptr until a subsequent push/append.  This is a lost race, and we
      // report it as such for consistency, though we could report the queue
      // was empty.  We don't attempt to further help [Clause 2] by also
      // trying to set _tail to nullptr, as that would just ensure that one or
      // the other cmpxchg is a wasted failure.
      return false;
    } else {
      // [Clause 1c]
      // Successfully advanced the list and claimed old_head.  next_node was
      // in the extension of the queue's list.  Return old_head after
      // unlinking it from next_node.
      set_next(*old_head, nullptr);
      *node_ptr = old_head;
      return true;
    }

  } else if (is_end(NextAccess::cmpxchg(*old_head, next_node, nullptr))) {
    // [Clause 2]
    // Old_head was the last entry and we've claimed it by setting its next
    // value to null.  However, this leaves the queue in disarray.  Fix up
    // the queue, possibly in conjunction with other concurrent operations.
    // Any further try_pops will consider the queue empty until a
    // push/append completes by installing a new head.

    // The order of the two cmpxchgs doesn't matter algorithmically, but
    // dealing with _head first gives a stronger invariant in append, and is
    // also consistent with [Clause 1b].

    // Attempt to change the queue head from old_head to null.  Failure of
    // the cmpxchg indicates a concurrent operation updated _head first.  That
    // could be either a push/append or a try_pop in [Clause 1b].
    _head.cmpxchg(old_head, nullptr);

    // Attempt to change the queue tail from old_head to null.  Failure of
    // the cmpxchg indicates that a concurrent push/append updated _tail first.
    // That operation will eventually recognize the old tail (our old_head) is
    // no longer in the list and update _head from the list being appended.
    _tail.cmpxchg(old_head, nullptr);

    // The queue has been restored to order, and we can return old_head.
    *node_ptr = old_head;
    return true;

  } else {
    // [Clause 3]
    // Old_head was the last entry in the list, but either a concurrent
    // try_pop claimed it first or a concurrent push/append extended the
    // list from it.  Either way, we lost the race to claim it.
    return false;
  }
}

template<typename T, auto next_access>
T* NonblockingQueue<T, next_access>::pop() {
  T* result = nullptr;
  // Typically try_pop() will succeed without retrying many times, thus we
  // omit SpinPause in the loop body.  SpinPause or yield may be worthwhile
  // in rare, highly contended cases, and client code could implement such
  // with try_pop().
  while (!try_pop(&result)) {}
  return result;
}

template<typename T, auto next_access>
Pair<T*, T*> NonblockingQueue<T, next_access>::take_all() {
  T* tail = _tail.load_relaxed();
  if (tail != nullptr) set_next(*tail, nullptr); // Clear end marker.
  Pair<T*, T*> result(_head.load_relaxed(), tail);
  _head.store_relaxed(nullptr);
  _tail.store_relaxed(nullptr);
  return result;
}

#endif // SHARE_UTILITIES_NONBLOCKINGQUEUE_INLINE_HPP
