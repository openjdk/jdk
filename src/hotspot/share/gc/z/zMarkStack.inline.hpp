/*
 * Copyright (c) 2016, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/z/zAttachedArray.inline.hpp"
#include "gc/z/zMarkTerminate.inline.hpp"
#include "utilities/debug.hpp"

inline bool ZMarkStack::is_empty() const {
  return _top == 0;
}

inline bool ZMarkStack::is_full() const {
  return _top == _entries.length();
}

inline ZMarkStackEntry* ZMarkStack::slots() {
  return _entries(this);
}

inline void ZMarkStack::push(ZMarkStackEntry value) {
  assert(!is_full(), "can't push to full stack");
  slots()[_top++] = value;
}

inline ZMarkStackEntry ZMarkStack::pop() {
  assert(!is_empty(), "can't pop from empty stack");
  return slots()[--_top];
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
  assert(!stack->is_empty(), "we never publish empty stacks");

  if (publish) {
    _published.push(stack);
  } else {
    _overflowed.push(stack);
  }

  terminate->wake_up();
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

inline ZMarkStripe* ZMarkStripeSet::stripe_for_addr(uintptr_t addr) {
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

inline void ZMarkThreadLocalStacks::push(ZMarkStripeSet* stripes,
                                         ZMarkStripe* stripe,
                                         ZMarkTerminate* terminate,
                                         ZMarkStackEntry entry,
                                         bool publish) {
  const size_t stripe_id = stripes->stripe_id(stripe);
  ZMarkStack** const stackp = &_stacks[stripe_id];
  ZMarkStack* const prev_stack = *stackp;

  if (prev_stack != nullptr) {
    if (!prev_stack->is_full()) {
      // There's a stack and it isn't full: just push
      prev_stack->push(entry);
      return;
    }

    // Publish full stacks
    stripe->publish_stack(prev_stack, terminate, publish);
    *stackp = nullptr;
  }

  // If no stack was available, allocate one and push to it
  const bool first_stack = prev_stack == nullptr;
  ZMarkStack* const new_stack = ZMarkStack::create(first_stack);
  *stackp = new_stack;

  new_stack->push(entry);
}

inline bool ZMarkThreadLocalStacks::pop(ZMarkingSMR* marking_smr,
                                        ZMarkStripeSet* stripes,
                                        ZMarkStripe* stripe,
                                        ZMarkStackEntry* entry) {
  ZMarkStack** const stackp = &_stacks[stripes->stripe_id(stripe)];
  ZMarkStack* stack = *stackp;

  // First make sure there is a stack to pop from
  if (stack == nullptr) {
    // If we have no stack, try to steal one
    stack = stripe->steal_stack(marking_smr);
    *stackp = stack;

    if (stack == nullptr) {
      // Out of stacks to pop from
      return false;
    }
  }

  *entry = stack->pop();

  if (stack->is_empty()) {
    // Eagerly free empty stacks while on a worker thread
    ZMarkStack::destroy(stack);
    *stackp = nullptr;
  }

  return true;
}

#endif // SHARE_GC_Z_ZMARKSTACK_INLINE_HPP
