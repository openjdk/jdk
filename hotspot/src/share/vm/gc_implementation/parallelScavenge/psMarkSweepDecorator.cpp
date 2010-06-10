/*
 * Copyright (c) 2001, 2009, Oracle and/or its affiliates. All rights reserved.
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

# include "incls/_precompiled.incl"
# include "incls/_psMarkSweepDecorator.cpp.incl"

PSMarkSweepDecorator* PSMarkSweepDecorator::_destination_decorator = NULL;


void PSMarkSweepDecorator::set_destination_decorator_tenured() {
  ParallelScavengeHeap* heap = (ParallelScavengeHeap*)Universe::heap();
  assert(heap->kind() == CollectedHeap::ParallelScavengeHeap, "Sanity");

  _destination_decorator = heap->old_gen()->object_mark_sweep();
}

void PSMarkSweepDecorator::set_destination_decorator_perm_gen() {
  ParallelScavengeHeap* heap = (ParallelScavengeHeap*)Universe::heap();
  assert(heap->kind() == CollectedHeap::ParallelScavengeHeap, "Sanity");

  _destination_decorator = heap->perm_gen()->object_mark_sweep();
}

void PSMarkSweepDecorator::advance_destination_decorator() {
  ParallelScavengeHeap* heap = (ParallelScavengeHeap*)Universe::heap();
  assert(heap->kind() == CollectedHeap::ParallelScavengeHeap, "Sanity");

  assert(_destination_decorator != NULL, "Sanity");
  guarantee(_destination_decorator != heap->perm_gen()->object_mark_sweep(), "Cannot advance perm gen decorator");

  PSMarkSweepDecorator* first = heap->old_gen()->object_mark_sweep();
  PSMarkSweepDecorator* second = heap->young_gen()->eden_mark_sweep();
  PSMarkSweepDecorator* third = heap->young_gen()->from_mark_sweep();
  PSMarkSweepDecorator* fourth = heap->young_gen()->to_mark_sweep();

  if ( _destination_decorator == first ) {
    _destination_decorator = second;
  } else if ( _destination_decorator == second ) {
    _destination_decorator = third;
  } else if ( _destination_decorator == third ) {
    _destination_decorator = fourth;
  } else {
    fatal("PSMarkSweep attempting to advance past last compaction area");
  }
}

PSMarkSweepDecorator* PSMarkSweepDecorator::destination_decorator() {
  assert(_destination_decorator != NULL, "Sanity");

  return _destination_decorator;
}

// FIX ME FIX ME FIX ME FIX ME!!!!!!!!!
// The object forwarding code is duplicated. Factor this out!!!!!
//
// This method "precompacts" objects inside its space to dest. It places forwarding
// pointers into markOops for use by adjust_pointers. If "dest" should overflow, we
// finish by compacting into our own space.

void PSMarkSweepDecorator::precompact() {
  // Reset our own compact top.
  set_compaction_top(space()->bottom());

  /* We allow some amount of garbage towards the bottom of the space, so
   * we don't start compacting before there is a significant gain to be made.
   * Occasionally, we want to ensure a full compaction, which is determined
   * by the MarkSweepAlwaysCompactCount parameter. This is a significant
   * performance improvement!
   */
  bool skip_dead = ((PSMarkSweep::total_invocations() % MarkSweepAlwaysCompactCount) != 0);

  size_t allowed_deadspace = 0;
  if (skip_dead) {
    const size_t ratio = allowed_dead_ratio();
    allowed_deadspace = space()->capacity_in_words() * ratio / 100;
  }

  // Fetch the current destination decorator
  PSMarkSweepDecorator* dest = destination_decorator();
  ObjectStartArray* start_array = dest->start_array();

  HeapWord* compact_top = dest->compaction_top();
  HeapWord* compact_end = dest->space()->end();

  HeapWord* q = space()->bottom();
  HeapWord* t = space()->top();

  HeapWord*  end_of_live= q;    /* One byte beyond the last byte of the last
                                   live object. */
  HeapWord*  first_dead = space()->end(); /* The first dead object. */
  LiveRange* liveRange  = NULL; /* The current live range, recorded in the
                                   first header of preceding free area. */
  _first_dead = first_dead;

  const intx interval = PrefetchScanIntervalInBytes;

  while (q < t) {
    assert(oop(q)->mark()->is_marked() || oop(q)->mark()->is_unlocked() ||
           oop(q)->mark()->has_bias_pattern(),
           "these are the only valid states during a mark sweep");
    if (oop(q)->is_gc_marked()) {
      /* prefetch beyond q */
      Prefetch::write(q, interval);
      size_t size = oop(q)->size();

      size_t compaction_max_size = pointer_delta(compact_end, compact_top);

      // This should only happen if a space in the young gen overflows the
      // old gen. If that should happen, we null out the start_array, because
      // the young spaces are not covered by one.
      while(size > compaction_max_size) {
        // First record the last compact_top
        dest->set_compaction_top(compact_top);

        // Advance to the next compaction decorator
        advance_destination_decorator();
        dest = destination_decorator();

        // Update compaction info
        start_array = dest->start_array();
        compact_top = dest->compaction_top();
        compact_end = dest->space()->end();
        assert(compact_top == dest->space()->bottom(), "Advanced to space already in use");
        assert(compact_end > compact_top, "Must always be space remaining");
        compaction_max_size =
          pointer_delta(compact_end, compact_top);
      }

      // store the forwarding pointer into the mark word
      if (q != compact_top) {
        oop(q)->forward_to(oop(compact_top));
        assert(oop(q)->is_gc_marked(), "encoding the pointer should preserve the mark");
      } else {
        // if the object isn't moving we can just set the mark to the default
        // mark and handle it specially later on.
        oop(q)->init_mark();
        assert(oop(q)->forwardee() == NULL, "should be forwarded to NULL");
      }

      // Update object start array
      if (start_array) {
        start_array->allocate_block(compact_top);
      }

      VALIDATE_MARK_SWEEP_ONLY(MarkSweep::register_live_oop(oop(q), size));
      compact_top += size;
      assert(compact_top <= dest->space()->end(),
        "Exceeding space in destination");

      q += size;
      end_of_live = q;
    } else {
      /* run over all the contiguous dead objects */
      HeapWord* end = q;
      do {
        /* prefetch beyond end */
        Prefetch::write(end, interval);
        end += oop(end)->size();
      } while (end < t && (!oop(end)->is_gc_marked()));

      /* see if we might want to pretend this object is alive so that
       * we don't have to compact quite as often.
       */
      if (allowed_deadspace > 0 && q == compact_top) {
        size_t sz = pointer_delta(end, q);
        if (insert_deadspace(allowed_deadspace, q, sz)) {
          size_t compaction_max_size = pointer_delta(compact_end, compact_top);

          // This should only happen if a space in the young gen overflows the
          // old gen. If that should happen, we null out the start_array, because
          // the young spaces are not covered by one.
          while (sz > compaction_max_size) {
            // First record the last compact_top
            dest->set_compaction_top(compact_top);

            // Advance to the next compaction decorator
            advance_destination_decorator();
            dest = destination_decorator();

            // Update compaction info
            start_array = dest->start_array();
            compact_top = dest->compaction_top();
            compact_end = dest->space()->end();
            assert(compact_top == dest->space()->bottom(), "Advanced to space already in use");
            assert(compact_end > compact_top, "Must always be space remaining");
            compaction_max_size =
              pointer_delta(compact_end, compact_top);
          }

          // store the forwarding pointer into the mark word
          if (q != compact_top) {
            oop(q)->forward_to(oop(compact_top));
            assert(oop(q)->is_gc_marked(), "encoding the pointer should preserve the mark");
          } else {
            // if the object isn't moving we can just set the mark to the default
            // mark and handle it specially later on.
            oop(q)->init_mark();
            assert(oop(q)->forwardee() == NULL, "should be forwarded to NULL");
          }

          // Update object start array
          if (start_array) {
            start_array->allocate_block(compact_top);
          }

          VALIDATE_MARK_SWEEP_ONLY(MarkSweep::register_live_oop(oop(q), sz));
          compact_top += sz;
          assert(compact_top <= dest->space()->end(),
            "Exceeding space in destination");

          q = end;
          end_of_live = end;
          continue;
        }
      }

      /* for the previous LiveRange, record the end of the live objects. */
      if (liveRange) {
        liveRange->set_end(q);
      }

      /* record the current LiveRange object.
       * liveRange->start() is overlaid on the mark word.
       */
      liveRange = (LiveRange*)q;
      liveRange->set_start(end);
      liveRange->set_end(end);

      /* see if this is the first dead region. */
      if (q < first_dead) {
        first_dead = q;
      }

      /* move on to the next object */
      q = end;
    }
  }

  assert(q == t, "just checking");
  if (liveRange != NULL) {
    liveRange->set_end(q);
  }
  _end_of_live = end_of_live;
  if (end_of_live < first_dead) {
    first_dead = end_of_live;
  }
  _first_dead = first_dead;

  // Update compaction top
  dest->set_compaction_top(compact_top);
}

bool PSMarkSweepDecorator::insert_deadspace(size_t& allowed_deadspace_words,
                                            HeapWord* q, size_t deadlength) {
  if (allowed_deadspace_words >= deadlength) {
    allowed_deadspace_words -= deadlength;
    CollectedHeap::fill_with_object(q, deadlength);
    oop(q)->set_mark(oop(q)->mark()->set_marked());
    assert((int) deadlength == oop(q)->size(), "bad filler object size");
    // Recall that we required "q == compaction_top".
    return true;
  } else {
    allowed_deadspace_words = 0;
    return false;
  }
}

void PSMarkSweepDecorator::adjust_pointers() {
  // adjust all the interior pointers to point at the new locations of objects
  // Used by MarkSweep::mark_sweep_phase3()

  HeapWord* q = space()->bottom();
  HeapWord* t = _end_of_live;  // Established by "prepare_for_compaction".

  assert(_first_dead <= _end_of_live, "Stands to reason, no?");

  if (q < t && _first_dead > q &&
      !oop(q)->is_gc_marked()) {
    // we have a chunk of the space which hasn't moved and we've
    // reinitialized the mark word during the previous pass, so we can't
    // use is_gc_marked for the traversal.
    HeapWord* end = _first_dead;

    while (q < end) {
      VALIDATE_MARK_SWEEP_ONLY(MarkSweep::track_interior_pointers(oop(q)));
      // point all the oops to the new location
      size_t size = oop(q)->adjust_pointers();
      VALIDATE_MARK_SWEEP_ONLY(MarkSweep::check_interior_pointers());
      VALIDATE_MARK_SWEEP_ONLY(MarkSweep::validate_live_oop(oop(q), size));
      q += size;
    }

    if (_first_dead == t) {
      q = t;
    } else {
      // $$$ This is funky.  Using this to read the previously written
      // LiveRange.  See also use below.
      q = (HeapWord*)oop(_first_dead)->mark()->decode_pointer();
    }
  }
  const intx interval = PrefetchScanIntervalInBytes;

  debug_only(HeapWord* prev_q = NULL);
  while (q < t) {
    // prefetch beyond q
    Prefetch::write(q, interval);
    if (oop(q)->is_gc_marked()) {
      // q is alive
      VALIDATE_MARK_SWEEP_ONLY(MarkSweep::track_interior_pointers(oop(q)));
      // point all the oops to the new location
      size_t size = oop(q)->adjust_pointers();
      VALIDATE_MARK_SWEEP_ONLY(MarkSweep::check_interior_pointers());
      VALIDATE_MARK_SWEEP_ONLY(MarkSweep::validate_live_oop(oop(q), size));
      debug_only(prev_q = q);
      q += size;
    } else {
      // q is not a live object, so its mark should point at the next
      // live object
      debug_only(prev_q = q);
      q = (HeapWord*) oop(q)->mark()->decode_pointer();
      assert(q > prev_q, "we should be moving forward through memory");
    }
  }

  assert(q == t, "just checking");
}

void PSMarkSweepDecorator::compact(bool mangle_free_space ) {
  // Copy all live objects to their new location
  // Used by MarkSweep::mark_sweep_phase4()

  HeapWord*       q = space()->bottom();
  HeapWord* const t = _end_of_live;
  debug_only(HeapWord* prev_q = NULL);

  if (q < t && _first_dead > q &&
      !oop(q)->is_gc_marked()) {
#ifdef ASSERT
    // we have a chunk of the space which hasn't moved and we've reinitialized the
    // mark word during the previous pass, so we can't use is_gc_marked for the
    // traversal.
    HeapWord* const end = _first_dead;

    while (q < end) {
      size_t size = oop(q)->size();
      assert(!oop(q)->is_gc_marked(), "should be unmarked (special dense prefix handling)");
      VALIDATE_MARK_SWEEP_ONLY(MarkSweep::live_oop_moved_to(q, size, q));
      debug_only(prev_q = q);
      q += size;
    }
#endif

    if (_first_dead == t) {
      q = t;
    } else {
      // $$$ Funky
      q = (HeapWord*) oop(_first_dead)->mark()->decode_pointer();
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
      size_t size = oop(q)->size();
      HeapWord* compaction_top = (HeapWord*)oop(q)->forwardee();

      // prefetch beyond compaction_top
      Prefetch::write(compaction_top, copy_interval);

      // copy object and reinit its mark
      VALIDATE_MARK_SWEEP_ONLY(MarkSweep::live_oop_moved_to(q, size, compaction_top));
      assert(q != compaction_top, "everything in this pass should be moving");
      Copy::aligned_conjoint_words(q, compaction_top, size);
      oop(compaction_top)->init_mark();
      assert(oop(compaction_top)->klass() != NULL, "should have a class");

      debug_only(prev_q = q);
      q += size;
    }
  }

  assert(compaction_top() >= space()->bottom() && compaction_top() <= space()->end(),
         "should point inside space");
  space()->set_top(compaction_top());

  if (mangle_free_space) {
    space()->mangle_unused_area();
  }
}
