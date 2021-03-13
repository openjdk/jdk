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

template<bool enable> class LockFreeQueueCriticalSection: public StackObj {
  Thread* _thread;
  GlobalCounter::CSContext _context;
public:
  inline LockFreeQueueCriticalSection(Thread* thread) {
    if (enable) {
      _thread = thread;
      _context = GlobalCounter::critical_section_begin(thread);
    }
  }
  inline ~LockFreeQueueCriticalSection() {
    if (enable) {
      GlobalCounter::critical_section_end(_thread, _context);
    }
  }
};

template<typename T, T* volatile* (*next_ptr)(T&), bool rcu_pop>
T* LockFreeQueue<T, next_ptr, rcu_pop>::pop() {
  Thread* current_thread = Thread::current();
  while (true) {
    // Use a critical section per iteration, rather than over the whole
    // operation.  We're not guaranteed to make progress.  Lingering in one
    // CS could lead to excessive allocation of objects, because the CS
    // may block return of released objects to a free list for reuse.
    LockFreeQueueCriticalSection<rcu_pop> cs(current_thread);

    T* result = Atomic::load_acquire(&_head);
    if (result == NULL) return NULL; // Queue is empty.

    T* next = Atomic::load_acquire(next_ptr(*result));
    if (next != NULL) {
      // The "usual" lock-free pop from the head of a singly linked list.
      if (result == Atomic::cmpxchg(&_head, result, next)) {
        // Former head successfully taken; it is not the last.
        assert(Atomic::load(&_tail) != result, "invariant");
        assert(get_next(*result) != NULL, "invariant");
        *next_ptr(*result) = NULL;
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
      assert(get_next(*result) == NULL, "invariant");
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

#endif // SHARE_UTILITIES_LOCKFREEQUEUE_INLINE_HPP
