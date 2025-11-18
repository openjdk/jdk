/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_UTILITIES_LOCKFREESTACK_HPP
#define SHARE_UTILITIES_LOCKFREESTACK_HPP

#include "runtime/atomic.hpp"
#include "runtime/atomicAccess.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"

// The LockFreeStack class template provides a lock-free LIFO. The objects
// in the sequence are intrusively linked via a member in the objects.  As
// a result, there is no allocation involved in adding objects to the stack
// or removing them from the stack.
//
// To be used in a LockFreeStack of objects of type T, an object of type T
// must have a list entry member. A list entry member is a data member whose
// type is either (1) Atomic<T*>, or (2) T* volatile. There must be a
// non-member or static member function returning a pointer to that member,
// which is used to provide access to it by a LockFreeStack.  A LockFreeStack
// is associated with the class of its elements and an entry member from that
// class by being specialized on the element class and a pointer to the
// function for accessing that entry member.
//
// An object can be in multiple stacks at the same time, so long as
// each stack uses a different entry member. That is, the class of the
// object must have multiple LockFreeStack entry members, one for each
// stack in which the object may simultaneously be an element.
//
// LockFreeStacks support polymorphic elements.  Because the objects
// in a stack are externally managed, rather than being embedded
// values in the stack, the actual type of such objects may be more
// specific than the stack's element type.
//
// \tparam T is the class of the elements in the stack.
//
// \tparam next_accessor is a function pointer.  Applying this function to
// an object of type T must return a pointer to the list entry member
// of the object associated with the LockFreeStack type.
template<typename T, auto next_accessor>
class LockFreeStack {
  Atomic<T*> _top;

  void prepend_impl(T* first, T* last) {
    T* cur = top();
    T* old;
    do {
      old = cur;
      set_next(*last, cur);
      cur = _top.compare_exchange(cur, first);
    } while (old != cur);
  }

  NONCOPYABLE(LockFreeStack);

  template<typename NextAccessor>
  static constexpr void use_atomic_access_impl(NextAccessor) {
    static_assert(DependentAlwaysFalse<NextAccessor>, "Invalid next accessor");
  }
  static constexpr bool use_atomic_access_impl(T* volatile* (*)(T&)) { return true; }
  static constexpr bool use_atomic_access_impl(Atomic<T*>* (*)(T&)) { return false; }

  static constexpr bool use_atomic_access = use_atomic_access_impl(next_accessor);

public:
  LockFreeStack() : _top(nullptr) {}
  ~LockFreeStack() { assert(empty(), "stack not empty"); }

  // Atomically removes the top object from this stack and returns a
  // pointer to that object, or null if this stack is empty. Acts as a
  // full memory barrier. Subject to ABA behavior; callers must ensure
  // usage is safe.
  T* pop() {
    T* result = top();
    T* old;
    do {
      old = result;
      T* new_top = nullptr;
      if (result != nullptr) {
        new_top = next(*result);
      }
      // CAS even on empty pop, for consistent membar behavior.
      result = _top.compare_exchange(result, new_top);
    } while (result != old);
    if (result != nullptr) {
      set_next(*result, nullptr);
    }
    return result;
  }

  // Atomically exchange the list of elements with null, returning the old
  // list of elements.  Acts as a full memory barrier.
  // postcondition: empty()
  T* pop_all() {
    return _top.exchange(nullptr);
  }

  // Atomically adds value to the top of this stack.  Acts as a full
  // memory barrier.
  void push(T& value) {
    assert(next(value) == nullptr, "precondition");
    prepend_impl(&value, &value);
  }

  // Atomically adds the list of objects (designated by first and
  // last) before the objects already in this stack, in the same order
  // as in the list. Acts as a full memory barrier.
  // precondition: next(last) == nullptr.
  // postcondition: top() == &first, next(last) == old top().
  void prepend(T& first, T& last) {
    assert(next(last) == nullptr, "precondition");
#ifdef ASSERT
    for (T* p = &first; p != &last; p = next(*p)) {
      assert(p != nullptr, "invalid prepend list");
    }
#endif
    prepend_impl(&first, &last);
  }

  // Atomically adds the list of objects headed by first before the
  // objects already in this stack, in the same order as in the list.
  // Acts as a full memory barrier.
  // postcondition: top() == &first.
  void prepend(T& first) {
    T* last = &first;
    while (true) {
      T* step_to = next(*last);
      if (step_to == nullptr) break;
      last = step_to;
    }
    prepend_impl(&first, last);
  }

  // Return true if the stack is empty.
  bool empty() const { return top() == nullptr; }

  // Return the most recently pushed element, or null if the stack is empty.
  // The returned element is not removed from the stack.
  T* top() const { return _top.load_relaxed(); }

  // Return the number of objects in the stack.  There must be no concurrent
  // pops while the length is being determined.
  size_t length() const {
    size_t result = 0;
    for (const T* current = top(); current != nullptr; current = next(*current)) {
      ++result;
    }
    return result;
  }

  // Return the entry following value in the list used by the
  // specialized LockFreeStack class.
  static T* next(const T& value) {
    if constexpr (use_atomic_access) {
      return AtomicAccess::load(next_accessor(const_cast<T&>(value)));
    } else {
      return next_accessor(const_cast<T&>(value))->load_relaxed();
    }
  }

  // Set the entry following value to new_next in the list used by the
  // specialized LockFreeStack class.  Not thread-safe; in particular,
  // if value is in an instance of this specialization of LockFreeStack,
  // there must be no concurrent push or pop operations on that stack.
  static void set_next(T& value, T* new_next) {
    if constexpr (use_atomic_access) {
      AtomicAccess::store(next_accessor(value), new_next);
    } else {
      next_accessor(value)->store_relaxed(new_next);
    }
  }
};

#endif // SHARE_UTILITIES_LOCKFREESTACK_HPP
