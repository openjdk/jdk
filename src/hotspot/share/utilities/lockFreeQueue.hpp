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

#ifndef SHARE_UTILITIES_LOCKFREEQUEUE_HPP
#define SHARE_UTILITIES_LOCKFREEQUEUE_HPP

#include "memory/padded.hpp"
#include "runtime/atomic.hpp"
#include "utilities/globalDefinitions.hpp"

// The LockFreeQueue template provides a lock-free FIFO. Its structure
// and usage is similar to LockFreeStack. It has inner paddings, and
// optionally use GlobalCounter critical section in pop() to address
// the ABA problem. This class has a restriction that pop() may return
// NULL when there are objects in the queue if there is a concurrent
// push/append operation.
//
// \tparam T is the class of the elements in the queue.
//
// \tparam next_ptr is a function pointer.  Applying this function to
// an object of type T must return a pointer to the list entry member
// of the object associated with the LockFreeQueue type.
//
// \tparam rcu_pop true if use GlobalCounter critical section in pop().
template<typename T, T* volatile* (*next_ptr)(T&), bool rcu_pop>
class LockFreeQueue {
  NONCOPYABLE(LockFreeQueue);

protected:
  T* volatile _head;
  DEFINE_PAD_MINUS_SIZE(1, DEFAULT_CACHE_LINE_SIZE, sizeof(T*));
  T* volatile _tail;
  DEFINE_PAD_MINUS_SIZE(2, DEFAULT_CACHE_LINE_SIZE, sizeof(T*));

public:
  LockFreeQueue() : _head(NULL), _tail(NULL) {}
#ifdef ASSERT
  ~LockFreeQueue() {
    assert(_head == NULL, "precondition");
    assert(_tail == NULL, "precondition");
  }
#endif // ASSERT

  // Return the first object in the queue.
  // Thread-safe, but the result may change immediately.
  T* top() const {
    return Atomic::load(&_head);
  }

  // Thread-safe add the object to the end of the queue.
  void push(T& node) { append(node, node); }

  // Thread-safe add the objects from first to last to the end of the queue.
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
  void append(T& first, T& last) {
    assert(get_next(last) == NULL, "precondition");
    T* old_tail = Atomic::xchg(&_tail, &last);
    if (old_tail == NULL) {       // Was empty.
      Atomic::store(&_head, &first);
    } else {
      assert(get_next(*old_tail) == NULL, "invariant");
      Atomic::store(next_ptr(*old_tail), &first);
    }
  }

  // Thread-safe attempt to remove and return the first object in the queue.
  // Returns NULL if the queue is empty, or if a concurrent push/append
  // interferes.
  // If rcu_pop is true, it applies GlobalCounter critical sections to
  // address the ABA problem. This requires the object's
  // allocator use GlobalCounter synchronization to defer reusing object.
  T* pop();

  // Return the entry following value in the list used by the
  // specialized LockFreeQueue class.
  static T* get_next(const T& value) {
    return Atomic::load(next_ptr(const_cast<T&>(value)));
  }
};

#endif // SHARE_UTILITIES_LOCKFREEQUEUE_HPP
