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
#include "utilities/globalDefinitions.hpp"
#include "utilities/pair.hpp"

// Return status of a LockFreeQueue::try_pop() call.
// See description for try_pop() below.
enum class LockFreeQueuePopStatus {
  success,
  lost_race,
  operation_in_progress
};

// The LockFreeQueue template provides a lock-free FIFO. Its structure
// and usage is similar to LockFreeStack. It provides a try_pop() function
// for the client to implement pop() according to its need (e.g., whether
// or not to retry or prevent ABA problem). It has inner padding of one
// cache line between its two internal pointer fields.
//
// \tparam T is the class of the elements in the queue.
//
// \tparam next_ptr is a function pointer.  Applying this function to
// an object of type T must return a pointer to the list entry member
// of the object associated with the LockFreeQueue type.
template<typename T, T* volatile* (*next_ptr)(T&)>
class LockFreeQueue {
  T* volatile _head;
  // Padding of one cache line to avoid false sharing.
  DEFINE_PAD_MINUS_SIZE(1, DEFAULT_CACHE_LINE_SIZE, sizeof(T*));
  T* volatile _tail;

  NONCOPYABLE(LockFreeQueue);

  // Return the entry following node in the list used by the
  // specialized LockFreeQueue class.
  static inline T* next(const T& node);

  // Set the entry following node to new_next in the list used by the
  // specialized LockFreeQueue class. Not thread-safe, as it cannot
  // concurrently run with push or try_pop operations that modify this
  // node.
  static inline void set_next(T& node, T* new_next);

public:
  inline LockFreeQueue();
  DEBUG_ONLY(~LockFreeQueue();)

  // Return the first object in the queue.
  // Thread-safe, but the result may change immediately.
  inline T* top() const;

  // Return true if the queue is empty.
  inline bool empty() const { return top() == NULL; }

  // Return the number of objects in the queue.
  // Not thread-safe. There must be no concurrent modification
  // while the length is being determined.
  inline size_t length() const;

  // Thread-safe add the object to the end of the queue.
  inline void push(T& node) { append(node, node); }

  // Thread-safe add the objects from first to last to the end of the queue.
  inline void append(T& first, T& last);

  // Thread-safe attempt to remove and return the first object in the queue.
  // Returns a <LockFreeQueuePopStatus, T*> pair for the caller to determine
  // further operation. 3 possible cases depending on pair.first:
  // - success:
  //   The operation succeeded. If pair.second is NULL, the queue is empty;
  //   otherwise caller can assume ownership of the object pointed by
  //   pair.second. Note that this case is still subject to ABA behavior;
  //   callers must ensure usage is safe.
  // - lost_race:
  //   An atomic operation failed. pair.second is NULL.
  //   The caller can typically retry in this case.
  // - operation_in_progress:
  //   An in-progress concurrent operation interfered with taking what had been
  //   the only remaining element in the queue. pair.second is NULL.
  //   A concurrent try_pop may have already claimed it, but not completely
  //   updated the queue. Alternatively, a concurrent push/append may have not
  //   yet linked the new entry(s) to the former sole entry. Retrying the try_pop
  //   will continue to fail in this way until that other thread has updated the
  //   queue's internal structure.
  inline Pair<LockFreeQueuePopStatus, T*> try_pop();

  // Take all the objects from the queue, leaving the queue empty.
  // Not thread-safe. It should only be used when there is no concurrent
  // push/append/try_pop operation.
  // Returns a pair of <head, tail> pointers to the current queue.
  inline Pair<T*, T*> take_all();
};

#endif // SHARE_UTILITIES_LOCKFREEQUEUE_HPP
