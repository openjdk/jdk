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

#include "gc/z/zMarkingSMR.hpp"
#include "gc/z/zMarkStack.inline.hpp"
#include "gc/z/zMarkTerminate.inline.hpp"
#include "logging/log.hpp"
#include "runtime/atomic.hpp"
#include "runtime/orderAccess.hpp"
#include "utilities/debug.hpp"
#include "utilities/powerOfTwo.hpp"

ZMarkStack* ZMarkStack::create(bool first_stack) {
  // When allocating the first stack on a stripe, we try to use a
  // smaller mark stack to promote sharing of stacks with other
  // threads instead. Once more than one stack is needed, we revert
  // to a larger stack size instead, which reduces synchronization
  // overhead of churning around stacks on a stripe.
  const size_t capacity = first_stack ? 128 : 512;

  void* const memory = AttachedArray::alloc(capacity);
  return ::new (memory) ZMarkStack(capacity);
}

void ZMarkStack::destroy(ZMarkStack* stack) {
  stack->~ZMarkStack();
  AttachedArray::free(stack);
}

ZMarkStack::ZMarkStack(size_t capacity)
  : _top(0),
    _entries(capacity) {}

ZMarkStackListNode::ZMarkStackListNode(ZMarkStack* stack)
  : _stack(stack),
    _next() {}

ZMarkStack* ZMarkStackListNode::stack() const {
  return _stack;
}

ZMarkStackListNode* ZMarkStackListNode::next() const {
  return _next;
}

void ZMarkStackListNode::set_next(ZMarkStackListNode* next) {
  _next = next;
}

ZMarkStackList::ZMarkStackList()
  : _head(),
    _length() {}

bool ZMarkStackList::is_empty() const {
  return Atomic::load(&_head) == nullptr;
}

void ZMarkStackList::push(ZMarkStack* stack) {
  ZMarkStackListNode* const node = new ZMarkStackListNode(stack);
  ZMarkStackListNode* head = Atomic::load(&_head);
  for (;;) {
    node->set_next(head);
    // Between reading the head and the linearizing CAS that pushes
    // the node onto the list, there could be an ABA problem. Except,
    // on the pushing side, that is benign. The node is never
    // dereferenced while pushing and if we were to detect the ABA
    // situation and run this loop one more time, we would end up
    // having the same side effects: set the next pointer to the same
    // head again, and CAS the head link.
    ZMarkStackListNode* prev = Atomic::cmpxchg(&_head, head, node, memory_order_release);

    if (prev == head) {
      // Success

      // Bookkeep the population count
      Atomic::inc(&_length, memory_order_relaxed);
      return;
    }

    // Retry
    head = prev;
  }
}

ZMarkStack* ZMarkStackList::pop(ZMarkingSMR* marking_smr) {
  ZMarkStackListNode* volatile* const hazard_ptr = marking_smr->hazard_ptr();

  ZMarkStackListNode* head = Atomic::load(&_head);
  for (;;) {
    if (head == nullptr) {
      // Stack is empty
      return nullptr;
    }

    // Establish what the head is and publish a hazard pointer denoting
    // that the head is not safe to concurrently free while we are in the
    // middle of popping it and finding out that we lost the race.
    Atomic::store(hazard_ptr, head);

    // A full fence is needed to ensure the store and subsequent load do
    // not reorder. If they did reorder, the second head load could happen
    // before other threads scanning hazard pointers can observe it, meaning
    // it could get concurrently freed.
    OrderAccess::fence();

    // The acquire fence when loading the head is necessary to make sure
    // the next pointer load below observes the next pointer published
    // with the releasing CAS for the push operation that published the
    // marking stack.
    ZMarkStackListNode* const head_after_publish = Atomic::load_acquire(&_head);
    if (head_after_publish != head) {
      // Race during hazard pointer publishing
      head = head_after_publish;
      continue;
    }

    // With the hazard pointer published, we can read the next pointer,
    // knowing that it is indeed the next pointer of the intended logical
    // head node that we established above.
    ZMarkStackListNode* const next = head->next();

    // Popping entries from the list does not require any particular memory
    // ordering.
    ZMarkStackListNode* const prev = Atomic::cmpxchg(&_head, head, next, memory_order_relaxed);

    if (prev == head) {
      // Success

      // The ABA hazard is gone after the CAS. We use release_store to ensure
      // that the relinquishing of the hazard pointer becomes observable after
      // the unlinking CAS.
      Atomic::release_store(hazard_ptr, (ZMarkStackListNode*)nullptr);

      // Perform bookkeeping of the population count.
      Atomic::dec(&_length, memory_order_relaxed);

      ZMarkStack* result = head->stack();

      marking_smr->free_node(head);

      return result;
    }

    // Retry
    head = prev;
  }
}

size_t ZMarkStackList::length() const {
  const ssize_t result = Atomic::load(&_length);

  if (result < 0) {
    return 0;
  }

  return (size_t)result;
}

ZMarkStripe::ZMarkStripe()
  : _published(),
    _overflowed() {}

ZMarkStack* ZMarkStripe::steal_stack(ZMarkingSMR* marking_smr) {
  // Steal overflowed stacks first, then published stacks
  ZMarkStack* const stack = _overflowed.pop(marking_smr);
  if (stack != nullptr) {
    return stack;
  }

  return _published.pop(marking_smr);
}

size_t ZMarkStripe::population() const {
  return _overflowed.length() + _published.length();
}

ZMarkStripeSet::ZMarkStripeSet()
  : _nstripes_mask(0),
    _stripes() {}

void ZMarkStripeSet::set_nstripes(size_t nstripes) {
  assert(is_power_of_2(nstripes), "Must be a power of two");
  assert(is_power_of_2(ZMarkStripesMax), "Must be a power of two");
  assert(nstripes >= 1, "Invalid number of stripes");
  assert(nstripes <= ZMarkStripesMax, "Invalid number of stripes");

  const size_t new_nstripes_mask = nstripes - 1;
  _nstripes_mask = new_nstripes_mask;

  log_debug(gc, marking)("Using %zu mark stripes", nstripes);
}

bool ZMarkStripeSet::try_set_nstripes(size_t old_nstripes, size_t new_nstripes) {
  assert(is_power_of_2(new_nstripes), "Must be a power of two");
  assert(is_power_of_2(ZMarkStripesMax), "Must be a power of two");
  assert(new_nstripes >= 1, "Invalid number of stripes");
  assert(new_nstripes <= ZMarkStripesMax, "Invalid number of stripes");

  const size_t old_nstripes_mask = old_nstripes - 1;
  const size_t new_nstripes_mask = new_nstripes - 1;

  // Mutators may read these values concurrently. It doesn't matter
  // if they see the old or new values.
  if (Atomic::cmpxchg(&_nstripes_mask, old_nstripes_mask, new_nstripes_mask) == old_nstripes_mask) {
    log_debug(gc, marking)("Using %zu mark stripes", new_nstripes);
    return true;
  }

  return false;
}

size_t ZMarkStripeSet::nstripes() const {
  return Atomic::load(&_nstripes_mask) + 1;
}

bool ZMarkStripeSet::is_empty() const {
  for (size_t i = 0; i < ZMarkStripesMax; i++) {
    if (!_stripes[i].is_empty()) {
      return false;
    }
  }

  return true;
}

bool ZMarkStripeSet::is_crowded() const {
  size_t population = 0;
  const size_t crowded_threshold = nstripes() << 4;

  for (size_t i = 0; i < ZMarkStripesMax; i++) {
    population += _stripes[i].population();
    if (population > crowded_threshold) {
      return true;
    }
  }

  return false;
}

ZMarkStripe* ZMarkStripeSet::stripe_for_worker(uint nworkers, uint worker_id) {
  const size_t mask = Atomic::load(&_nstripes_mask);
  const size_t nstripes = mask + 1;

  const size_t spillover_limit = (nworkers / nstripes) * nstripes;
  size_t index;

  if (worker_id < spillover_limit) {
    // Not a spillover worker, use natural stripe
    index = worker_id & mask;
  } else {
    // Distribute spillover workers evenly across stripes
    const size_t spillover_nworkers = nworkers - spillover_limit;
    const size_t spillover_worker_id = worker_id - spillover_limit;
    const double spillover_chunk = (double)nstripes / (double)spillover_nworkers;
    index = (size_t)(spillover_worker_id * spillover_chunk);
  }

  assert(index < nstripes, "Invalid index");
  return &_stripes[index];
}

ZMarkThreadLocalStacks::ZMarkThreadLocalStacks() {
  for (size_t i = 0; i < ZMarkStripesMax; i++) {
    _stacks[i] = nullptr;
  }
}

bool ZMarkThreadLocalStacks::is_empty(const ZMarkStripeSet* stripes) const {
  for (size_t i = 0; i < ZMarkStripesMax; i++) {
    ZMarkStack* const stack = _stacks[i];
    if (stack != nullptr) {
      return false;
    }
  }

  return true;
}

bool ZMarkThreadLocalStacks::flush(ZMarkStripeSet* stripes,
                                   ZMarkTerminate* terminate) {
  bool flushed = false;

  // Flush all stacks
  for (size_t i = 0; i < ZMarkStripesMax; i++) {
    ZMarkStripe* const stripe = stripes->stripe_at(i);
    ZMarkStack** const stackp = &_stacks[i];
    ZMarkStack* const stack = *stackp;
    if (stack == nullptr) {
      continue;
    }

    // Free/Publish and uninstall stack
    stripe->publish_stack(stack, terminate, true /* publish */);
    flushed = true;
    *stackp = nullptr;
  }

  return flushed;
}
