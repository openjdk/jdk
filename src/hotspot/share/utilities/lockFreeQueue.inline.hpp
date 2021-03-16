/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_UTILITIES_LOCKFREEQUEUE_INLINE_HPP
#define SHARE_UTILITIES_LOCKFREEQUEUE_INLINE_HPP

#include "runtime/atomic.hpp"
#include "runtime/thread.inline.hpp"
#include "utilities/globalCounter.inline.hpp"
#include "utilities/lockFreeQueue.hpp"
#include "logging/log.hpp"

template<typename T, T* volatile* (*next_ptr)(T&)>
T* LockFreeQueue<T, next_ptr>::next(const T& node) {
  return Atomic::load(next_ptr(const_cast<T&>(node)));
}

template<typename T, T* volatile* (*next_ptr)(T&)>
void LockFreeQueue<T, next_ptr>::set_next(T& node, T* new_next) {
    Atomic::store(next_ptr(node), new_next);
}

template<typename T, T* volatile* (*next_ptr)(T&)>
LockFreeQueue<T, next_ptr>::LockFreeQueue() : _head(NULL), _tail(NULL) {}

#ifdef ASSERT
template<typename T, T* volatile* (*next_ptr)(T&)>
LockFreeQueue<T, next_ptr>::~LockFreeQueue() {
  assert(_head == NULL, "precondition");
  assert(_tail == NULL, "precondition");
}
#endif

template<typename T, T* volatile* (*next_ptr)(T&)>
T* LockFreeQueue<T, next_ptr>::top() const {
  return Atomic::load(&_head);
}

template<typename T, T* volatile* (*next_ptr)(T&)>
size_t LockFreeQueue<T, next_ptr>::length() const {
  size_t result = 0;
  for (const T* current = top(); current != NULL; current = next(*current)) {
    ++result;
  }
  return result;
}

// An append operation atomically exchanges the new tail with the queue tail.
// It then sets the "next" value of the old tail to the head of the list being
// appended; it is an invariant that the old tail's "next" value is NULL.
// But if the old tail is NULL then the queue was empty.  In this case the
// head of the list being appended is instead stored in the queue head; it is
// an invariant that the queue head is NULL in this case.
//
// This means there is a period between the exchange and the old tail update
// where the queue sequence is split into two parts, the list from the queue
// head to the old tail, and the list being appended.  If there are concurrent
// push/append operations, each may introduce another such segment.  But they
// all eventually get resolved by their respective updates of their old tail's
// "next" value.  This also means that pop operations must handle an object
// with a NULL "next" value specially.
//
// A push operation is just a degenerate append, where the object being pushed
// is both the head and the tail of the list being appended.
template<typename T, T* volatile* (*next_ptr)(T&)>
void LockFreeQueue<T, next_ptr>::append(T& first, T& last) {
  assert(next(last) == NULL, "precondition");
  T* old_tail = Atomic::xchg(&_tail, &last);
  if (old_tail == NULL) {       // Was empty.
    Atomic::store(&_head, &first);
  } else {
    assert(next(*old_tail) == NULL, "invariant");
    set_next(*old_tail, &first);
  }
}

template<typename T, T* volatile* (*next_ptr)(T&)>
template<bool use_rcu>
T* LockFreeQueue<T, next_ptr>::pop() {
  while (true) {
    // Use a critical section per iteration, rather than over the whole
    // operation. We're not guaranteed to make progress. Lingering in one
    // CS could defer the write-side operation of RCU synchronization
    // too long, leading to unwanted effect. E.g., if the write-side
    // returns released objects to a free list for reuse, it could cause
    // excessive allocations.
    GlobalCounter::ConditionalCriticalSection<use_rcu> cs(use_rcu ?
                                                          Thread::current():
                                                          NULL);

    // We only need memory_order_consume. Upgrade it to "load_acquire"
    // as the memory_order_consume API is not ready for use yet.
    T* result = Atomic::load_acquire(&_head);
    if (result == NULL) return NULL; // Queue is empty.

    // This relaxed load is always followed by a cmpxchg(), thus it
    // is OK as the reader-side of the release-acquire ordering.
    T* next_node = Atomic::load(next_ptr(*result));
    if (next_node != NULL) {
      // The "usual" lock-free pop from the head of a singly linked list.
      if (result == Atomic::cmpxchg(&_head, result, next_node)) {
        // Former head successfully taken; it is not the last.
        assert(Atomic::load(&_tail) != result, "invariant");
        assert(next(*result) != NULL, "invariant");
        set_next(*result, NULL);
        return result;
      }
      // Lost the race; try again.
      continue;
    }

    // next is NULL.  This case is handled differently from the "usual"
    // lock-free pop from the head of a singly linked list.

    // If _tail == result then result is the only element in the list. We can
    // remove it from the list by first setting _tail to NULL and then setting
    // _head to NULL, the order being important.  We set _tail with cmpxchg in
    // case of a concurrent push/append/pop also changing _tail.  If we win
    // then we've claimed result.
    if (Atomic::cmpxchg(&_tail, result, (T*)NULL) == result) {
      assert(next(*result) == NULL, "invariant");
      // Now that we've claimed result, also set _head to NULL.  But we must
      // be careful of a concurrent push/append after we NULLed _tail, since
      // it may have already performed its list-was-empty update of _head,
      // which we must not overwrite.
      Atomic::cmpxchg(&_head, result, (T*)NULL);
      return result;
    }

    // If _head != result then we lost the race to take result; try again.
    if (result != Atomic::load_acquire(&_head)) {
      continue;
    }

    // An in-progress concurrent operation interfered with taking the head
    // element when it was the only element.  A concurrent pop may have won
    // the race to clear the tail but not yet cleared the head. Alternatively,
    // a concurrent push/append may have changed the tail but not yet linked
    // result->next().  We cannot take result in either case.  We don't just
    // try again, because we could spin for a long time waiting for that
    // concurrent operation to finish.  In the first case, returning NULL is
    // fine; we lost the race for the only element to another thread.  We
    // also return NULL for the second case, and let the caller cope.
    return NULL;
  }
}

template<typename T, T* volatile* (*next_ptr)(T&)>
Pair<T*, T*> LockFreeQueue<T, next_ptr>::take_all() {
  Pair<T*, T*> result(Atomic::load(&_head), Atomic::load(&_tail));
  Atomic::store(&_head, (T*)NULL);
  Atomic::store(&_tail, (T*)NULL);
  return result;
}

#endif // SHARE_UTILITIES_LOCKFREEQUEUE_INLINE_HPP
