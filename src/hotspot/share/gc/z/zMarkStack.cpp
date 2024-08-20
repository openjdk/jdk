/*
 * Copyright (c) 2016, 2024, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "gc/z/zMarkStack.inline.hpp"
#include "gc/z/zMarkStackAllocator.hpp"
#include "gc/z/zMarkTerminate.inline.hpp"
#include "logging/log.hpp"
#include "runtime/atomic.hpp"
#include "utilities/debug.hpp"
#include "utilities/powerOfTwo.hpp"

ZMarkStripe::ZMarkStripe(uintptr_t base)
  : _published(base),
    _overflowed(base) {}

ZMarkStripeSet::ZMarkStripeSet(uintptr_t base)
  : _nstripes_mask(0),
    _stripes() {

  // Re-construct array elements with the correct base
  for (size_t i = 0; i < ARRAY_SIZE(_stripes); i++) {
    _stripes[i] = ZMarkStripe(base);
  }
}

void ZMarkStripeSet::set_nstripes(size_t nstripes) {
  assert(is_power_of_2(nstripes), "Must be a power of two");
  assert(is_power_of_2(ZMarkStripesMax), "Must be a power of two");
  assert(nstripes >= 1, "Invalid number of stripes");
  assert(nstripes <= ZMarkStripesMax, "Invalid number of stripes");

  // Mutators may read these values concurrently. It doesn't matter
  // if they see the old or new values.
  Atomic::store(&_nstripes_mask, nstripes - 1);

  log_debug(gc, marking)("Using " SIZE_FORMAT " mark stripes", nstripes);
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

ZMarkThreadLocalStacks::ZMarkThreadLocalStacks()
  : _magazine(nullptr) {
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

ZMarkStack* ZMarkThreadLocalStacks::allocate_stack(ZMarkStackAllocator* allocator) {
  if (_magazine == nullptr) {
    // Allocate new magazine
    _magazine = allocator->alloc_magazine();
    if (_magazine == nullptr) {
      return nullptr;
    }
  }

  ZMarkStack* stack = nullptr;

  if (!_magazine->pop(stack)) {
    // Magazine is empty, convert magazine into a new stack
    _magazine->~ZMarkStackMagazine();
    stack = new ((void*)_magazine) ZMarkStack();
    _magazine = nullptr;
  }

  return stack;
}

void ZMarkThreadLocalStacks::free_stack(ZMarkStackAllocator* allocator, ZMarkStack* stack) {
  for (;;) {
    if (_magazine == nullptr) {
      // Convert stack into a new magazine
      stack->~ZMarkStack();
      _magazine = new ((void*)stack) ZMarkStackMagazine();
      return;
    }

    if (_magazine->push(stack)) {
      // Success
      return;
    }

    // Free and uninstall full magazine
    allocator->free_magazine(_magazine);
    _magazine = nullptr;
  }
}

bool ZMarkThreadLocalStacks::push_slow(ZMarkStackAllocator* allocator,
                                       ZMarkStripe* stripe,
                                       ZMarkStack** stackp,
                                       ZMarkTerminate* terminate,
                                       ZMarkStackEntry entry,
                                       bool publish) {
  ZMarkStack* stack = *stackp;

  for (;;) {
    if (stack == nullptr) {
      // Allocate and install new stack
      *stackp = stack = allocate_stack(allocator);
      if (stack == nullptr) {
        // Out of mark stack memory
        return false;
      }
    }

    if (stack->push(entry)) {
      // Success
      return true;
    }

    // Publish/Overflow and uninstall stack
    stripe->publish_stack(stack, terminate, publish);
    *stackp = stack = nullptr;
  }
}

bool ZMarkThreadLocalStacks::pop_slow(ZMarkStackAllocator* allocator,
                                      ZMarkStripe* stripe,
                                      ZMarkStack** stackp,
                                      ZMarkStackEntry& entry) {
  ZMarkStack* stack = *stackp;

  for (;;) {
    if (stack == nullptr) {
      // Try steal and install stack
      *stackp = stack = stripe->steal_stack();
      if (stack == nullptr) {
        // Nothing to steal
        return false;
      }
    }

    if (stack->pop(entry)) {
      // Success
      return true;
    }

    // Free and uninstall stack
    free_stack(allocator, stack);
    *stackp = stack = nullptr;
  }
}

bool ZMarkThreadLocalStacks::flush(ZMarkStackAllocator* allocator, ZMarkStripeSet* stripes, ZMarkTerminate* terminate) {
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
    if (stack->is_empty()) {
      free_stack(allocator, stack);
    } else {
      stripe->publish_stack(stack, terminate, true /* publish */);
      flushed = true;
    }
    *stackp = nullptr;
  }

  return flushed;
}

void ZMarkThreadLocalStacks::free(ZMarkStackAllocator* allocator) {
  // Free and uninstall magazine
  if (_magazine != nullptr) {
    allocator->free_magazine(_magazine);
    _magazine = nullptr;
  }
}
