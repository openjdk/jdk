/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/x/xMarkStack.inline.hpp"
#include "gc/x/xMarkStackAllocator.hpp"
#include "logging/log.hpp"
#include "utilities/debug.hpp"
#include "utilities/powerOfTwo.hpp"

XMarkStripe::XMarkStripe() :
    _published(),
    _overflowed() {}

XMarkStripeSet::XMarkStripeSet() :
    _nstripes(0),
    _nstripes_mask(0),
    _stripes() {}

void XMarkStripeSet::set_nstripes(size_t nstripes) {
  assert(is_power_of_2(nstripes), "Must be a power of two");
  assert(is_power_of_2(XMarkStripesMax), "Must be a power of two");
  assert(nstripes >= 1, "Invalid number of stripes");
  assert(nstripes <= XMarkStripesMax, "Invalid number of stripes");

  _nstripes = nstripes;
  _nstripes_mask = nstripes - 1;

  log_debug(gc, marking)("Using " SIZE_FORMAT " mark stripes", _nstripes);
}

bool XMarkStripeSet::is_empty() const {
  for (size_t i = 0; i < _nstripes; i++) {
    if (!_stripes[i].is_empty()) {
      return false;
    }
  }

  return true;
}

XMarkStripe* XMarkStripeSet::stripe_for_worker(uint nworkers, uint worker_id) {
  const size_t spillover_limit = (nworkers / _nstripes) * _nstripes;
  size_t index;

  if (worker_id < spillover_limit) {
    // Not a spillover worker, use natural stripe
    index = worker_id & _nstripes_mask;
  } else {
    // Distribute spillover workers evenly across stripes
    const size_t spillover_nworkers = nworkers - spillover_limit;
    const size_t spillover_worker_id = worker_id - spillover_limit;
    const double spillover_chunk = (double)_nstripes / (double)spillover_nworkers;
    index = spillover_worker_id * spillover_chunk;
  }

  assert(index < _nstripes, "Invalid index");
  return &_stripes[index];
}

XMarkThreadLocalStacks::XMarkThreadLocalStacks() :
    _magazine(nullptr) {
  for (size_t i = 0; i < XMarkStripesMax; i++) {
    _stacks[i] = nullptr;
  }
}

bool XMarkThreadLocalStacks::is_empty(const XMarkStripeSet* stripes) const {
  for (size_t i = 0; i < stripes->nstripes(); i++) {
    XMarkStack* const stack = _stacks[i];
    if (stack != nullptr) {
      return false;
    }
  }

  return true;
}

XMarkStack* XMarkThreadLocalStacks::allocate_stack(XMarkStackAllocator* allocator) {
  if (_magazine == nullptr) {
    // Allocate new magazine
    _magazine = allocator->alloc_magazine();
    if (_magazine == nullptr) {
      return nullptr;
    }
  }

  XMarkStack* stack = nullptr;

  if (!_magazine->pop(stack)) {
    // Magazine is empty, convert magazine into a new stack
    _magazine->~XMarkStackMagazine();
    stack = new ((void*)_magazine) XMarkStack();
    _magazine = nullptr;
  }

  return stack;
}

void XMarkThreadLocalStacks::free_stack(XMarkStackAllocator* allocator, XMarkStack* stack) {
  for (;;) {
    if (_magazine == nullptr) {
      // Convert stack into a new magazine
      stack->~XMarkStack();
      _magazine = new ((void*)stack) XMarkStackMagazine();
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

bool XMarkThreadLocalStacks::push_slow(XMarkStackAllocator* allocator,
                                       XMarkStripe* stripe,
                                       XMarkStack** stackp,
                                       XMarkStackEntry entry,
                                       bool publish) {
  XMarkStack* stack = *stackp;

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
    stripe->publish_stack(stack, publish);
    *stackp = stack = nullptr;
  }
}

bool XMarkThreadLocalStacks::pop_slow(XMarkStackAllocator* allocator,
                                      XMarkStripe* stripe,
                                      XMarkStack** stackp,
                                      XMarkStackEntry& entry) {
  XMarkStack* stack = *stackp;

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

bool XMarkThreadLocalStacks::flush(XMarkStackAllocator* allocator, XMarkStripeSet* stripes) {
  bool flushed = false;

  // Flush all stacks
  for (size_t i = 0; i < stripes->nstripes(); i++) {
    XMarkStripe* const stripe = stripes->stripe_at(i);
    XMarkStack** const stackp = &_stacks[i];
    XMarkStack* const stack = *stackp;
    if (stack == nullptr) {
      continue;
    }

    // Free/Publish and uninstall stack
    if (stack->is_empty()) {
      free_stack(allocator, stack);
    } else {
      stripe->publish_stack(stack);
      flushed = true;
    }
    *stackp = nullptr;
  }

  return flushed;
}

void XMarkThreadLocalStacks::free(XMarkStackAllocator* allocator) {
  // Free and uninstall magazine
  if (_magazine != nullptr) {
    allocator->free_magazine(_magazine);
    _magazine = nullptr;
  }
}
