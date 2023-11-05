/*
 * Copyright (c) 2000, 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_SHARED_SPACE_INLINE_HPP
#define SHARE_GC_SHARED_SPACE_INLINE_HPP

#include "gc/shared/space.hpp"

#include "gc/serial/generation.hpp"
#include "gc/shared/collectedHeap.hpp"
#include "gc/shared/spaceDecorator.hpp"
#include "oops/oop.inline.hpp"
#include "oops/oopsHierarchy.hpp"
#include "runtime/prefetch.inline.hpp"
#include "runtime/safepoint.hpp"
#if INCLUDE_SERIALGC
#include "gc/serial/serialBlockOffsetTable.inline.hpp"
#include "gc/serial/markSweep.inline.hpp"
#endif

inline HeapWord* Space::block_start(const void* p) {
  return block_start_const(p);
}

#if INCLUDE_SERIALGC
inline HeapWord* TenuredSpace::allocate(size_t size) {
  HeapWord* res = ContiguousSpace::allocate(size);
  if (res != nullptr) {
    _offsets.update_for_block(res, res + size);
  }
  return res;
}

inline HeapWord* TenuredSpace::par_allocate(size_t size) {
  HeapWord* res = ContiguousSpace::par_allocate(size);
  if (res != nullptr) {
    _offsets.update_for_block(res, res + size);
  }
  return res;
}

class DeadSpacer : StackObj {
  size_t _allowed_deadspace_words;
  bool _active;
  ContiguousSpace* _space;

public:
  DeadSpacer(ContiguousSpace* space) : _allowed_deadspace_words(0), _space(space) {
    size_t ratio = _space->allowed_dead_ratio();
    _active = ratio > 0;

    if (_active) {
      assert(!UseG1GC, "G1 should not be using dead space");

      // We allow some amount of garbage towards the bottom of the space, so
      // we don't start compacting before there is a significant gain to be made.
      // Occasionally, we want to ensure a full compaction, which is determined
      // by the MarkSweepAlwaysCompactCount parameter.
      if ((MarkSweep::total_invocations() % MarkSweepAlwaysCompactCount) != 0) {
        _allowed_deadspace_words = (space->capacity() * ratio / 100) / HeapWordSize;
      } else {
        _active = false;
      }
    }
  }

  bool insert_deadspace(HeapWord* dead_start, HeapWord* dead_end) {
    if (!_active) {
      return false;
    }

    size_t dead_length = pointer_delta(dead_end, dead_start);
    if (_allowed_deadspace_words >= dead_length) {
      _allowed_deadspace_words -= dead_length;
      CollectedHeap::fill_with_object(dead_start, dead_length);
      oop obj = cast_to_oop(dead_start);
      obj->set_mark(obj->mark().set_marked());

      assert(dead_length == obj->size(), "bad filler object size");
      log_develop_trace(gc, compaction)("Inserting object to dead space: " PTR_FORMAT ", " PTR_FORMAT ", " SIZE_FORMAT "b",
          p2i(dead_start), p2i(dead_end), dead_length * HeapWordSize);

      return true;
    } else {
      _active = false;
      return false;
    }
  }
};

#ifdef ASSERT
inline void ContiguousSpace::verify_up_to_first_dead(ContiguousSpace* space) {
  HeapWord* cur_obj = space->bottom();

  if (cur_obj < space->_end_of_live && space->_first_dead > cur_obj && !cast_to_oop(cur_obj)->is_gc_marked()) {
     // we have a chunk of the space which hasn't moved and we've reinitialized
     // the mark word during the previous pass, so we can't use is_gc_marked for
     // the traversal.
     HeapWord* prev_obj = nullptr;

     while (cur_obj < space->_first_dead) {
       size_t size = cast_to_oop(cur_obj)->size();
       assert(!cast_to_oop(cur_obj)->is_gc_marked(), "should be unmarked (special dense prefix handling)");
       prev_obj = cur_obj;
       cur_obj += size;
     }
  }
}
#endif

inline void ContiguousSpace::clear_empty_region(ContiguousSpace* space) {
  // Let's remember if we were empty before we did the compaction.
  bool was_empty = space->used_region().is_empty();
  // Reset space after compaction is complete
  space->reset_after_compaction();
  // We do this clear, below, since it has overloaded meanings for some
  // space subtypes.  For example, TenuredSpace's that were
  // compacted into will have had their offset table thresholds updated
  // continuously, but those that weren't need to have their thresholds
  // re-initialized.  Also mangles unused area for debugging.
  if (space->used_region().is_empty()) {
    if (!was_empty) space->clear(SpaceDecorator::Mangle);
  } else {
    if (ZapUnusedHeapArea) space->mangle_unused_area();
  }
}
#endif // INCLUDE_SERIALGC

template <typename OopClosureType>
void ContiguousSpace::oop_since_save_marks_iterate(OopClosureType* blk) {
  HeapWord* t;
  HeapWord* p = saved_mark_word();
  assert(p != nullptr, "expected saved mark");

  const intx interval = PrefetchScanIntervalInBytes;
  do {
    t = top();
    while (p < t) {
      Prefetch::write(p, interval);
      debug_only(HeapWord* prev = p);
      oop m = cast_to_oop(p);
      p += m->oop_iterate_size(blk);
    }
  } while (t < top());

  set_saved_mark_word(p);
}

#endif // SHARE_GC_SHARED_SPACE_INLINE_HPP
