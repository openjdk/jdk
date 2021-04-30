/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_Z_ZMARKSTACK_INLINE_HPP
#define SHARE_GC_Z_ZMARKSTACK_INLINE_HPP

#include "gc/z/zMarkStack.hpp"

#include "gc/z/zMarkTerminate.inline.hpp"
#include "runtime/atomic.hpp"
#include "utilities/debug.hpp"

template <typename T, size_t S>
inline ZStack<T, S>::ZStack() :
    _top(0),
    _next(nullptr) {}

template <typename T, size_t S>
inline bool ZStack<T, S>::is_empty() const {
  return _top == 0;
}

template <typename T, size_t S>
inline bool ZStack<T, S>::is_full() const {
  return _top == S;
}

template <typename T, size_t S>
inline bool ZStack<T, S>::push(T value) {
  if (is_full()) {
    return false;
  }

  _slots[_top++] = value;
  return true;
}

template <typename T, size_t S>
inline bool ZStack<T, S>::pop(T& value) {
  if (is_empty()) {
    return false;
  }

  value = _slots[--_top];
  return true;
}

template <typename T, size_t S>
inline ZStack<T, S>* ZStack<T, S>::next() const {
  return _next;
}

template <typename T, size_t S>
inline ZStack<T, S>** ZStack<T, S>::next_addr() {
  return &_next;
}

template <typename T>
inline ZStackList<T>::ZStackList(uintptr_t base) :
    _base(base),
    _head(encode_versioned_pointer(nullptr, 0)) {}

template <typename T>
inline T* ZStackList<T>::encode_versioned_pointer(const T* stack, uint32_t version) const {
  uint64_t addr;

  if (stack == nullptr) {
    addr = (uint32_t)-1;
  } else {
    addr = ((uint64_t)stack - _base) >> ZMarkStackSizeShift;
  }

  return (T*)((addr << 32) | (uint64_t)version);
}

template <typename T>
inline void ZStackList<T>::decode_versioned_pointer(const T* vstack, T** stack, uint32_t* version) const {
  const uint64_t addr = (uint64_t)vstack >> 32;

  if (addr == (uint32_t)-1) {
    *stack = nullptr;
  } else {
    *stack = (T*)((addr << ZMarkStackSizeShift) + _base);
  }

  *version = (uint32_t)(uint64_t)vstack;
}

template <typename T>
inline bool ZStackList<T>::is_empty() const {
  const T* vstack = _head;
  T* stack = nullptr;
  uint32_t version = 0;

  decode_versioned_pointer(vstack, &stack, &version);
  return stack == nullptr;
}

template <typename T>
inline void ZStackList<T>::push(T* stack) {
  T* vstack = _head;
  uint32_t version = 0;

  for (;;) {
    decode_versioned_pointer(vstack, stack->next_addr(), &version);
    T* const new_vstack = encode_versioned_pointer(stack, version + 1);
    T* const prev_vstack = Atomic::cmpxchg(&_head, vstack, new_vstack);
    if (prev_vstack == vstack) {
      // Success
      break;
    }

    // Retry
    vstack = prev_vstack;
  }
}

template <typename T>
inline T* ZStackList<T>::pop() {
  T* vstack = _head;
  T* stack = nullptr;
  uint32_t version = 0;

  for (;;) {
    decode_versioned_pointer(vstack, &stack, &version);
    if (stack == nullptr) {
      return nullptr;
    }

    T* const new_vstack = encode_versioned_pointer(stack->next(), version + 1);
    T* const prev_vstack = Atomic::cmpxchg(&_head, vstack, new_vstack);
    if (prev_vstack == vstack) {
      // Success
      return stack;
    }

    // Retry
    vstack = prev_vstack;
  }
}

template <typename T>
inline void ZStackList<T>::clear() {
  _head = encode_versioned_pointer(nullptr, 0);
}

inline bool ZMarkStripe::is_empty() const {
  return _published.is_empty() && _overflowed.is_empty();
}

inline void ZMarkStripe::publish_stack(ZMarkStack* stack, ZMarkTerminate* terminate, bool publish) {
  // A stack is published either on the published list or the overflowed
  // list. The published list is used by mutators publishing stacks for GC
  // workers to work on, while the overflowed list is used by GC workers
  // to publish stacks that overflowed. The intention here is to avoid
  // contention between mutators and GC workers as much as possible, while
  // still allowing GC workers to help out and steal work from each other.
  if (publish) {
    _published.push(stack);
  } else {
    _overflowed.push(stack);
  }
  terminate->wake_up();
}

inline ZMarkStack* ZMarkStripe::steal_stack() {
  // Steal overflowed stacks first, then published stacks
  ZMarkStack* const stack = _overflowed.pop();
  if (stack != nullptr) {
    return stack;
  }

  return _published.pop();
}

inline size_t ZMarkStripeSet::stripe_id(const ZMarkStripe* stripe) const {
  const size_t index = ((uintptr_t)stripe - (uintptr_t)_stripes) / sizeof(ZMarkStripe);
  assert(index < ZMarkStripesMax, "Invalid index");
  return index;
}

inline ZMarkStripe* ZMarkStripeSet::stripe_at(size_t index) {
  assert(index < ZMarkStripesMax, "Invalid index");
  return &_stripes[index];
}

inline ZMarkStripe* ZMarkStripeSet::stripe_next(ZMarkStripe* stripe) {
  const size_t index = (stripe_id(stripe) + 1) & (ZMarkStripesMax - 1);
  assert(index < ZMarkStripesMax, "Invalid index");
  return &_stripes[index];
}

inline ZMarkStripe* ZMarkStripeSet::stripe_for_addr_worker(uintptr_t addr) {
  const size_t index = (addr >> ZMarkStripeShift) & _nstripes_mask;
  assert(index < ZMarkStripesMax, "Invalid index");
  return &_stripes[index];
}

inline ZMarkStripe* ZMarkStripeSet::stripe_for_addr_barrier(uintptr_t addr) {
  const size_t index = (addr >> ZMarkStripeShift) & Atomic::load(&_nstripes_mask);
  assert(index < ZMarkStripesMax, "Invalid index");
  return &_stripes[index];
}

inline void ZMarkThreadLocalStacks::install(ZMarkStripeSet* stripes,
                                            ZMarkStripe* stripe,
                                            ZMarkStack* stack) {
  ZMarkStack** const stackp = &_stacks[stripes->stripe_id(stripe)];
  assert(*stackp == nullptr, "Should be empty");
  *stackp = stack;
}

inline ZMarkStack* ZMarkThreadLocalStacks::steal(ZMarkStripeSet* stripes,
                                                 ZMarkStripe* stripe) {
  ZMarkStack** const stackp = &_stacks[stripes->stripe_id(stripe)];
  ZMarkStack* const stack = *stackp;
  if (stack != nullptr) {
    *stackp = nullptr;
  }

  return stack;
}

inline bool ZMarkThreadLocalStacks::push(ZMarkStackAllocator* allocator,
                                         ZMarkStripeSet* stripes,
                                         ZMarkStripe* stripe,
                                         ZMarkTerminate* terminate,
                                         ZMarkStackEntry entry,
                                         bool publish) {
  ZMarkStack** const stackp = &_stacks[stripes->stripe_id(stripe)];
  ZMarkStack* const stack = *stackp;
  if (stack != nullptr && stack->push(entry)) {
    return true;
  }

  return push_slow(allocator, stripe, stackp, terminate, entry, publish);
}

inline bool ZMarkThreadLocalStacks::pop(ZMarkStackAllocator* allocator,
                                        ZMarkStripeSet* stripes,
                                        ZMarkStripe* stripe,
                                        ZMarkStackEntry& entry) {
  ZMarkStack** const stackp = &_stacks[stripes->stripe_id(stripe)];
  ZMarkStack* const stack = *stackp;
  if (stack != nullptr && stack->pop(entry)) {
    return true;
  }

  return pop_slow(allocator, stripe, stackp, entry);
}

#endif // SHARE_GC_Z_ZMARKSTACK_INLINE_HPP
