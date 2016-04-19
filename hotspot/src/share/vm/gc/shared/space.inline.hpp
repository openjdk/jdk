/*
 * Copyright (c) 2000, 2016, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_SHARED_SPACE_INLINE_HPP
#define SHARE_VM_GC_SHARED_SPACE_INLINE_HPP

#include "gc/serial/markSweep.inline.hpp"
#include "gc/shared/collectedHeap.hpp"
#include "gc/shared/generation.hpp"
#include "gc/shared/space.hpp"
#include "gc/shared/spaceDecorator.hpp"
#include "memory/universe.hpp"
#include "runtime/prefetch.inline.hpp"
#include "runtime/safepoint.hpp"

inline HeapWord* Space::block_start(const void* p) {
  return block_start_const(p);
}

inline HeapWord* OffsetTableContigSpace::allocate(size_t size) {
  HeapWord* res = ContiguousSpace::allocate(size);
  if (res != NULL) {
    _offsets.alloc_block(res, size);
  }
  return res;
}

// Because of the requirement of keeping "_offsets" up to date with the
// allocations, we sequentialize these with a lock.  Therefore, best if
// this is used for larger LAB allocations only.
inline HeapWord* OffsetTableContigSpace::par_allocate(size_t size) {
  MutexLocker x(&_par_alloc_lock);
  // This ought to be just "allocate", because of the lock above, but that
  // ContiguousSpace::allocate asserts that either the allocating thread
  // holds the heap lock or it is the VM thread and we're at a safepoint.
  // The best I (dld) could figure was to put a field in ContiguousSpace
  // meaning "locking at safepoint taken care of", and set/reset that
  // here.  But this will do for now, especially in light of the comment
  // above.  Perhaps in the future some lock-free manner of keeping the
  // coordination.
  HeapWord* res = ContiguousSpace::par_allocate(size);
  if (res != NULL) {
    _offsets.alloc_block(res, size);
  }
  return res;
}

inline HeapWord*
OffsetTableContigSpace::block_start_const(const void* p) const {
  return _offsets.block_start(p);
}

size_t CompactibleSpace::obj_size(const HeapWord* addr) const {
  return oop(addr)->size();
}

template <class SpaceType>
inline void CompactibleSpace::scan_and_forward(SpaceType* space, CompactPoint* cp) {
  // Compute the new addresses for the live objects and store it in the mark
  // Used by universe::mark_sweep_phase2()
  HeapWord* compact_top; // This is where we are currently compacting to.

  // We're sure to be here before any objects are compacted into this
  // space, so this is a good time to initialize this:
  space->set_compaction_top(space->bottom());

  if (cp->space == NULL) {
    assert(cp->gen != NULL, "need a generation");
    assert(cp->threshold == NULL, "just checking");
    assert(cp->gen->first_compaction_space() == space, "just checking");
    cp->space = cp->gen->first_compaction_space();
    compact_top = cp->space->bottom();
    cp->space->set_compaction_top(compact_top);
    cp->threshold = cp->space->initialize_threshold();
  } else {
    compact_top = cp->space->compaction_top();
  }

  // We allow some amount of garbage towards the bottom of the space, so
  // we don't start compacting before there is a significant gain to be made.
  // Occasionally, we want to ensure a full compaction, which is determined
  // by the MarkSweepAlwaysCompactCount parameter.
  uint invocations = MarkSweep::total_invocations();
  bool skip_dead = ((invocations % MarkSweepAlwaysCompactCount) != 0);

  size_t allowed_deadspace = 0;
  if (skip_dead) {
    const size_t ratio = space->allowed_dead_ratio();
    allowed_deadspace = (space->capacity() * ratio / 100) / HeapWordSize;
  }

  HeapWord* q = space->bottom();
  HeapWord* t = space->scan_limit();

  HeapWord*  end_of_live= q;            // One byte beyond the last byte of the last
                                        // live object.
  HeapWord*  first_dead = space->end(); // The first dead object.

  const intx interval = PrefetchScanIntervalInBytes;

  while (q < t) {
    assert(!space->scanned_block_is_obj(q) ||
           oop(q)->mark()->is_marked() || oop(q)->mark()->is_unlocked() ||
           oop(q)->mark()->has_bias_pattern(),
           "these are the only valid states during a mark sweep");
    if (space->scanned_block_is_obj(q) && oop(q)->is_gc_marked()) {
      // prefetch beyond q
      Prefetch::write(q, interval);
      size_t size = space->scanned_block_size(q);
      compact_top = cp->space->forward(oop(q), size, cp, compact_top);
      q += size;
      end_of_live = q;
    } else {
      // run over all the contiguous dead objects
      HeapWord* end = q;
      do {
        // prefetch beyond end
        Prefetch::write(end, interval);
        end += space->scanned_block_size(end);
      } while (end < t && (!space->scanned_block_is_obj(end) || !oop(end)->is_gc_marked()));

      // see if we might want to pretend this object is alive so that
      // we don't have to compact quite as often.
      if (allowed_deadspace > 0 && q == compact_top) {
        size_t sz = pointer_delta(end, q);
        if (space->insert_deadspace(allowed_deadspace, q, sz)) {
          compact_top = cp->space->forward(oop(q), sz, cp, compact_top);
          q = end;
          end_of_live = end;
          continue;
        }
      }

      // otherwise, it really is a free region.

      // q is a pointer to a dead object. Use this dead memory to store a pointer to the next live object.
      (*(HeapWord**)q) = end;

      // see if this is the first dead region.
      if (q < first_dead) {
        first_dead = q;
      }

      // move on to the next object
      q = end;
    }
  }

  assert(q == t, "just checking");
  space->_end_of_live = end_of_live;
  if (end_of_live < first_dead) {
    first_dead = end_of_live;
  }
  space->_first_dead = first_dead;

  // save the compaction_top of the compaction space.
  cp->space->set_compaction_top(compact_top);
}

template <class SpaceType>
inline void CompactibleSpace::scan_and_adjust_pointers(SpaceType* space) {
  // adjust all the interior pointers to point at the new locations of objects
  // Used by MarkSweep::mark_sweep_phase3()

  HeapWord* q = space->bottom();
  HeapWord* t = space->_end_of_live;  // Established by "prepare_for_compaction".

  assert(space->_first_dead <= space->_end_of_live, "Stands to reason, no?");

  if (q < t && space->_first_dead > q && !oop(q)->is_gc_marked()) {
    // we have a chunk of the space which hasn't moved and we've
    // reinitialized the mark word during the previous pass, so we can't
    // use is_gc_marked for the traversal.
    HeapWord* end = space->_first_dead;

    while (q < end) {
      // I originally tried to conjoin "block_start(q) == q" to the
      // assertion below, but that doesn't work, because you can't
      // accurately traverse previous objects to get to the current one
      // after their pointers have been
      // updated, until the actual compaction is done.  dld, 4/00
      assert(space->block_is_obj(q), "should be at block boundaries, and should be looking at objs");

      // point all the oops to the new location
      size_t size = MarkSweep::adjust_pointers(oop(q));
      size = space->adjust_obj_size(size);

      q += size;
    }

    if (space->_first_dead == t) {
      q = t;
    } else {
      // The first dead object is no longer an object. At that memory address,
      // there is a pointer to the first live object that the previous phase found.
      q = *((HeapWord**)(space->_first_dead));
    }
  }

  const intx interval = PrefetchScanIntervalInBytes;

  debug_only(HeapWord* prev_q = NULL);
  while (q < t) {
    // prefetch beyond q
    Prefetch::write(q, interval);
    if (oop(q)->is_gc_marked()) {
      // q is alive
      // point all the oops to the new location
      size_t size = MarkSweep::adjust_pointers(oop(q));
      size = space->adjust_obj_size(size);
      debug_only(prev_q = q);
      q += size;
    } else {
      debug_only(prev_q = q);
      // q is not a live object, instead it points at the next live object
      q = *(HeapWord**)q;
      assert(q > prev_q, "we should be moving forward through memory, q: " PTR_FORMAT ", prev_q: " PTR_FORMAT, p2i(q), p2i(prev_q));
    }
  }

  assert(q == t, "just checking");
}

template <class SpaceType>
inline void CompactibleSpace::scan_and_compact(SpaceType* space) {
  // Copy all live objects to their new location
  // Used by MarkSweep::mark_sweep_phase4()

  HeapWord*       q = space->bottom();
  HeapWord* const t = space->_end_of_live;
  debug_only(HeapWord* prev_q = NULL);

  if (q < t && space->_first_dead > q && !oop(q)->is_gc_marked()) {
    #ifdef ASSERT // Debug only
      // we have a chunk of the space which hasn't moved and we've reinitialized
      // the mark word during the previous pass, so we can't use is_gc_marked for
      // the traversal.
      HeapWord* const end = space->_first_dead;

      while (q < end) {
        size_t size = space->obj_size(q);
        assert(!oop(q)->is_gc_marked(), "should be unmarked (special dense prefix handling)");
        prev_q = q;
        q += size;
      }
    #endif

    if (space->_first_dead == t) {
      q = t;
    } else {
      // $$$ Funky
      q = (HeapWord*) oop(space->_first_dead)->mark()->decode_pointer();
    }
  }

  const intx scan_interval = PrefetchScanIntervalInBytes;
  const intx copy_interval = PrefetchCopyIntervalInBytes;
  while (q < t) {
    if (!oop(q)->is_gc_marked()) {
      // mark is pointer to next marked oop
      debug_only(prev_q = q);
      q = (HeapWord*) oop(q)->mark()->decode_pointer();
      assert(q > prev_q, "we should be moving forward through memory");
    } else {
      // prefetch beyond q
      Prefetch::read(q, scan_interval);

      // size and destination
      size_t size = space->obj_size(q);
      HeapWord* compaction_top = (HeapWord*)oop(q)->forwardee();

      // prefetch beyond compaction_top
      Prefetch::write(compaction_top, copy_interval);

      // copy object and reinit its mark
      assert(q != compaction_top, "everything in this pass should be moving");
      Copy::aligned_conjoint_words(q, compaction_top, size);
      oop(compaction_top)->init_mark();
      assert(oop(compaction_top)->klass() != NULL, "should have a class");

      debug_only(prev_q = q);
      q += size;
    }
  }

  // Let's remember if we were empty before we did the compaction.
  bool was_empty = space->used_region().is_empty();
  // Reset space after compaction is complete
  space->reset_after_compaction();
  // We do this clear, below, since it has overloaded meanings for some
  // space subtypes.  For example, OffsetTableContigSpace's that were
  // compacted into will have had their offset table thresholds updated
  // continuously, but those that weren't need to have their thresholds
  // re-initialized.  Also mangles unused area for debugging.
  if (space->used_region().is_empty()) {
    if (!was_empty) space->clear(SpaceDecorator::Mangle);
  } else {
    if (ZapUnusedHeapArea) space->mangle_unused_area();
  }
}

size_t ContiguousSpace::scanned_block_size(const HeapWord* addr) const {
  return oop(addr)->size();
}

#endif // SHARE_VM_GC_SHARED_SPACE_INLINE_HPP
