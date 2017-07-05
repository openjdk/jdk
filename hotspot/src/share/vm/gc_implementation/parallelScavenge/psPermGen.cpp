/*
 * Copyright (c) 2001, 2008, Oracle and/or its affiliates. All rights reserved.
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
# include "incls/_psPermGen.cpp.incl"

PSPermGen::PSPermGen(ReservedSpace rs, size_t alignment,
                     size_t initial_size, size_t min_size, size_t max_size,
                     const char* gen_name, int level) :
  PSOldGen(rs, alignment, initial_size, min_size, max_size, gen_name, level),
  _last_used(0)
{
  assert(object_mark_sweep() != NULL, "Sanity");

  object_mark_sweep()->set_allowed_dead_ratio(PermMarkSweepDeadRatio);
  _avg_size = new AdaptivePaddedAverage(AdaptivePermSizeWeight,
                                        PermGenPadding);
}

HeapWord* PSPermGen::allocate_permanent(size_t size) {
  assert_locked_or_safepoint(Heap_lock);
  HeapWord* obj = allocate_noexpand(size, false);

  if (obj == NULL) {
    obj = expand_and_allocate(size, false);
  }

  return obj;
}

void PSPermGen::compute_new_size(size_t used_before_collection) {
  // Update our padded average of objects allocated in perm
  // gen between collections.
  assert(used_before_collection >= _last_used,
                                "negative allocation amount since last GC?");

  const size_t alloc_since_last_gc = used_before_collection - _last_used;
  _avg_size->sample(alloc_since_last_gc);

  const size_t current_live = used_in_bytes();
  // Stash away the current amount live for the next call to this method.
  _last_used = current_live;

  // We have different alignment constraints than the rest of the heap.
  const size_t alignment = MAX2(MinPermHeapExpansion,
                                virtual_space()->alignment());

  // Compute the desired size:
  //  The free space is the newly computed padded average,
  //  so the desired size is what's live + the free space.
  size_t desired_size = current_live + (size_t)_avg_size->padded_average();
  desired_size = align_size_up(desired_size, alignment);

  // ...and no larger or smaller than our max and min allowed.
  desired_size = MAX2(MIN2(desired_size, _max_gen_size), _min_gen_size);
  assert(desired_size <= _max_gen_size, "just checking");

  const size_t size_before = _virtual_space->committed_size();

  if (desired_size == size_before) {
    // no change, we're done
    return;
  }

  {
    // We'll be growing or shrinking the heap:  in either case,
    // we need to hold a lock.
    MutexLocker x(ExpandHeap_lock);
    if (desired_size > size_before) {
      const size_t change_bytes = desired_size - size_before;
      const size_t aligned_change_bytes =
        align_size_up(change_bytes, alignment);
      expand_by(aligned_change_bytes);
    } else {
      // Shrinking
      const size_t change_bytes =
        size_before - desired_size;
      const size_t aligned_change_bytes = align_size_down(change_bytes, alignment);
      shrink(aligned_change_bytes);
    }
  }

  // While this code isn't controlled by AdaptiveSizePolicy, it's
  // convenient to see all resizing decsions under the same flag.
  if (PrintAdaptiveSizePolicy) {
    ParallelScavengeHeap* heap = (ParallelScavengeHeap*)Universe::heap();
    assert(heap->kind() == CollectedHeap::ParallelScavengeHeap, "Sanity");

    gclog_or_tty->print_cr("AdaptiveSizePolicy::perm generation size: "
                           "collection: %d "
                           "(" SIZE_FORMAT ") -> (" SIZE_FORMAT ") ",
                           heap->total_collections(),
                           size_before, _virtual_space->committed_size());
  }
}



void PSPermGen::move_and_update(ParCompactionManager* cm) {
  PSParallelCompact::move_and_update(cm, PSParallelCompact::perm_space_id);
}

void PSPermGen::precompact() {
  // Reset start array first.
  _start_array.reset();
  object_mark_sweep()->precompact();
}
