/*
 * Copyright (c) 2002, 2018, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "gc/parallel/mutableSpace.hpp"
#include "gc/parallel/parallelScavengeHeap.hpp"
#include "gc/parallel/psPromotionLAB.hpp"
#include "memory/universe.hpp"
#include "oops/oop.inline.hpp"

// This is the shared initialization code. It sets up the basic pointers,
// and allows enough extra space for a filler object. We call a virtual
// method, "lab_is_valid()" to handle the different asserts the old/young
// labs require.
void PSPromotionLAB::initialize(MemRegion lab) {
  assert(lab_is_valid(lab), "Sanity");

  HeapWord* bottom = lab.start();
  HeapWord* end    = lab.end();

  set_bottom(bottom);
  set_end(end);
  set_top(bottom);

  // We can be initialized to a zero size!
  if (free() > 0) {
    if (ZapUnusedHeapArea) {
      debug_only(Copy::fill_to_words(top(), free()/HeapWordSize, badHeapWord));
    }

    // NOTE! We need to allow space for a filler object.
    assert(lab.word_size() >= CollectedHeap::min_dummy_object_size(), "lab is too small");
    end = end - CollectedHeap::min_dummy_object_size();
    set_end(end);

    _state = needs_flush;
  } else {
    _state = zero_size;
  }

  assert(this->top() <= this->end(), "pointers out of order");
}

// Fill all remaining lab space with an unreachable object.
// The goal is to leave a contiguous parseable span of objects.
void PSPromotionLAB::flush() {
  assert(_state != flushed, "Attempt to flush PLAB twice");
  assert(top() <= end(), "pointers out of order");

  // If we were initialized to a zero sized lab, there is
  // nothing to flush
  if (_state == zero_size)
    return;

  // PLAB's never allocate the last aligned_header_size
  // so they can always fill with an array.
  HeapWord* tlab_end = end() + CollectedHeap::min_dummy_object_size();
  CollectedHeap::fill_with_object(top(), tlab_end, trueInDebug);

  set_bottom(NULL);
  set_end(NULL);
  set_top(NULL);

  _state = flushed;
}

void PSPromotionLAB::unallocate_object(HeapWord* obj, size_t obj_size) {
  assert(ParallelScavengeHeap::heap()->is_in(obj), "Object outside heap");

  // If the object is inside this LAB, we just bump-down the `top` pointer.
  // Otherwise, we overwrite it with a filler object.
  if (contains(obj)) {
    HeapWord* object_end = obj + obj_size;
    assert(object_end == top(), "Not matching last allocation");

    set_top(obj);
  } else {
    CollectedHeap::fill_with_object(obj, obj_size);
  }
}

// Fill all remaining lab space with an unreachable object.
// The goal is to leave a contiguous parseable span of objects.
void PSOldPromotionLAB::flush() {
  assert(_state != flushed, "Attempt to flush PLAB twice");
  assert(top() <= end(), "pointers out of order");

  if (_state == zero_size)
    return;

  HeapWord* obj = top();

  PSPromotionLAB::flush();

  assert(_start_array != NULL, "Sanity");

  _start_array->allocate_block(obj);
}

#ifdef ASSERT

bool PSYoungPromotionLAB::lab_is_valid(MemRegion lab) {
  ParallelScavengeHeap* heap = ParallelScavengeHeap::heap();
  MutableSpace* to_space = heap->young_gen()->to_space();
  MemRegion used = to_space->used_region();
  if (used.contains(lab)) {
    return true;
  }

  return false;
}

bool PSOldPromotionLAB::lab_is_valid(MemRegion lab) {
  assert(_start_array->covered_region().contains(lab), "Sanity");

  ParallelScavengeHeap* heap = ParallelScavengeHeap::heap();
  PSOldGen* old_gen = heap->old_gen();
  MemRegion used = old_gen->object_space()->used_region();

  if (used.contains(lab)) {
    return true;
  }

  return false;
}

#endif /* ASSERT */
