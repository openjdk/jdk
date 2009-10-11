/*
 * Copyright 2005-2008 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

# include "incls/_precompiled.incl"
# include "incls/_asParNewGeneration.cpp.incl"

ASParNewGeneration::ASParNewGeneration(ReservedSpace rs,
                                       size_t initial_byte_size,
                                       size_t min_byte_size,
                                       int level) :
  ParNewGeneration(rs, initial_byte_size, level),
  _min_gen_size(min_byte_size) {}

const char* ASParNewGeneration::name() const {
  return "adaptive size par new generation";
}

void ASParNewGeneration::adjust_desired_tenuring_threshold() {
  assert(UseAdaptiveSizePolicy,
    "Should only be used with UseAdaptiveSizePolicy");
}

void ASParNewGeneration::resize(size_t eden_size, size_t survivor_size) {
  // Resize the generation if needed. If the generation resize
  // reports false, do not attempt to resize the spaces.
  if (resize_generation(eden_size, survivor_size)) {
    // Then we lay out the spaces inside the generation
    resize_spaces(eden_size, survivor_size);

    space_invariants();

    if (PrintAdaptiveSizePolicy && Verbose) {
      gclog_or_tty->print_cr("Young generation size: "
        "desired eden: " SIZE_FORMAT " survivor: " SIZE_FORMAT
        " used: " SIZE_FORMAT " capacity: " SIZE_FORMAT
        " gen limits: " SIZE_FORMAT " / " SIZE_FORMAT,
        eden_size, survivor_size, used(), capacity(),
        max_gen_size(), min_gen_size());
    }
  }
}

size_t ASParNewGeneration::available_to_min_gen() {
  assert(virtual_space()->committed_size() >= min_gen_size(), "Invariant");
  return virtual_space()->committed_size() - min_gen_size();
}

// This method assumes that from-space has live data and that
// any shrinkage of the young gen is limited by location of
// from-space.
size_t ASParNewGeneration::available_to_live() const {
#undef SHRINKS_AT_END_OF_EDEN
#ifdef SHRINKS_AT_END_OF_EDEN
  size_t delta_in_survivor = 0;
  ParallelScavengeHeap* heap = (ParallelScavengeHeap*)Universe::heap();
  const size_t space_alignment = heap->intra_heap_alignment();
  const size_t gen_alignment = heap->object_heap_alignment();

  MutableSpace* space_shrinking = NULL;
  if (from_space()->end() > to_space()->end()) {
    space_shrinking = from_space();
  } else {
    space_shrinking = to_space();
  }

  // Include any space that is committed but not included in
  // the survivor spaces.
  assert(((HeapWord*)virtual_space()->high()) >= space_shrinking->end(),
    "Survivor space beyond high end");
  size_t unused_committed = pointer_delta(virtual_space()->high(),
    space_shrinking->end(), sizeof(char));

  if (space_shrinking->is_empty()) {
    // Don't let the space shrink to 0
    assert(space_shrinking->capacity_in_bytes() >= space_alignment,
      "Space is too small");
    delta_in_survivor = space_shrinking->capacity_in_bytes() - space_alignment;
  } else {
    delta_in_survivor = pointer_delta(space_shrinking->end(),
                                      space_shrinking->top(),
                                      sizeof(char));
  }

  size_t delta_in_bytes = unused_committed + delta_in_survivor;
  delta_in_bytes = align_size_down(delta_in_bytes, gen_alignment);
  return delta_in_bytes;
#else
  // The only space available for shrinking is in to-space if it
  // is above from-space.
  if (to()->bottom() > from()->bottom()) {
    const size_t alignment = os::vm_page_size();
    if (to()->capacity() < alignment) {
      return 0;
    } else {
      return to()->capacity() - alignment;
    }
  } else {
    return 0;
  }
#endif
}

// Return the number of bytes available for resizing down the young
// generation.  This is the minimum of
//      input "bytes"
//      bytes to the minimum young gen size
//      bytes to the size currently being used + some small extra
size_t ASParNewGeneration::limit_gen_shrink (size_t bytes) {
  // Allow shrinkage into the current eden but keep eden large enough
  // to maintain the minimum young gen size
  bytes = MIN3(bytes, available_to_min_gen(), available_to_live());
  return align_size_down(bytes, os::vm_page_size());
}

// Note that the the alignment used is the OS page size as
// opposed to an alignment associated with the virtual space
// (as is done in the ASPSYoungGen/ASPSOldGen)
bool ASParNewGeneration::resize_generation(size_t eden_size,
                                           size_t survivor_size) {
  const size_t alignment = os::vm_page_size();
  size_t orig_size = virtual_space()->committed_size();
  bool size_changed = false;

  // There used to be this guarantee there.
  // guarantee ((eden_size + 2*survivor_size)  <= _max_gen_size, "incorrect input arguments");
  // Code below forces this requirement.  In addition the desired eden
  // size and disired survivor sizes are desired goals and may
  // exceed the total generation size.

  assert(min_gen_size() <= orig_size && orig_size <= max_gen_size(),
    "just checking");

  // Adjust new generation size
  const size_t eden_plus_survivors =
          align_size_up(eden_size + 2 * survivor_size, alignment);
  size_t desired_size = MAX2(MIN2(eden_plus_survivors, max_gen_size()),
                             min_gen_size());
  assert(desired_size <= max_gen_size(), "just checking");

  if (desired_size > orig_size) {
    // Grow the generation
    size_t change = desired_size - orig_size;
    assert(change % alignment == 0, "just checking");
    if (expand(change)) {
      return false; // Error if we fail to resize!
    }
    size_changed = true;
  } else if (desired_size < orig_size) {
    size_t desired_change = orig_size - desired_size;
    assert(desired_change % alignment == 0, "just checking");

    desired_change = limit_gen_shrink(desired_change);

    if (desired_change > 0) {
      virtual_space()->shrink_by(desired_change);
      reset_survivors_after_shrink();

      size_changed = true;
    }
  } else {
    if (Verbose && PrintGC) {
      if (orig_size == max_gen_size()) {
        gclog_or_tty->print_cr("ASParNew generation size at maximum: "
          SIZE_FORMAT "K", orig_size/K);
      } else if (orig_size == min_gen_size()) {
        gclog_or_tty->print_cr("ASParNew generation size at minium: "
          SIZE_FORMAT "K", orig_size/K);
      }
    }
  }

  if (size_changed) {
    MemRegion cmr((HeapWord*)virtual_space()->low(),
                  (HeapWord*)virtual_space()->high());
    GenCollectedHeap::heap()->barrier_set()->resize_covered_region(cmr);

    if (Verbose && PrintGC) {
      size_t current_size  = virtual_space()->committed_size();
      gclog_or_tty->print_cr("ASParNew generation size changed: "
                             SIZE_FORMAT "K->" SIZE_FORMAT "K",
                             orig_size/K, current_size/K);
    }
  }

  guarantee(eden_plus_survivors <= virtual_space()->committed_size() ||
            virtual_space()->committed_size() == max_gen_size(), "Sanity");

  return true;
}

void ASParNewGeneration::reset_survivors_after_shrink() {

  GenCollectedHeap* gch = GenCollectedHeap::heap();
  HeapWord* new_end = (HeapWord*)virtual_space()->high();

  if (from()->end() > to()->end()) {
    assert(new_end >= from()->end(), "Shrinking past from-space");
  } else {
    assert(new_end >= to()->bottom(), "Shrink was too large");
    // Was there a shrink of the survivor space?
    if (new_end < to()->end()) {
      MemRegion mr(to()->bottom(), new_end);
      to()->initialize(mr,
                       SpaceDecorator::DontClear,
                       SpaceDecorator::DontMangle);
    }
  }
}
void ASParNewGeneration::resize_spaces(size_t requested_eden_size,
                                       size_t requested_survivor_size) {
  assert(UseAdaptiveSizePolicy, "sanity check");
  assert(requested_eden_size > 0  && requested_survivor_size > 0,
         "just checking");
  CollectedHeap* heap = Universe::heap();
  assert(heap->kind() == CollectedHeap::GenCollectedHeap, "Sanity");


  // We require eden and to space to be empty
  if ((!eden()->is_empty()) || (!to()->is_empty())) {
    return;
  }

  size_t cur_eden_size = eden()->capacity();

  if (PrintAdaptiveSizePolicy && Verbose) {
    gclog_or_tty->print_cr("ASParNew::resize_spaces(requested_eden_size: "
                  SIZE_FORMAT
                  ", requested_survivor_size: " SIZE_FORMAT ")",
                  requested_eden_size, requested_survivor_size);
    gclog_or_tty->print_cr("    eden: [" PTR_FORMAT ".." PTR_FORMAT ") "
                  SIZE_FORMAT,
                  eden()->bottom(),
                  eden()->end(),
                  pointer_delta(eden()->end(),
                                eden()->bottom(),
                                sizeof(char)));
    gclog_or_tty->print_cr("    from: [" PTR_FORMAT ".." PTR_FORMAT ") "
                  SIZE_FORMAT,
                  from()->bottom(),
                  from()->end(),
                  pointer_delta(from()->end(),
                                from()->bottom(),
                                sizeof(char)));
    gclog_or_tty->print_cr("      to: [" PTR_FORMAT ".." PTR_FORMAT ") "
                  SIZE_FORMAT,
                  to()->bottom(),
                  to()->end(),
                  pointer_delta(  to()->end(),
                                  to()->bottom(),
                                  sizeof(char)));
  }

  // There's nothing to do if the new sizes are the same as the current
  if (requested_survivor_size == to()->capacity() &&
      requested_survivor_size == from()->capacity() &&
      requested_eden_size == eden()->capacity()) {
    if (PrintAdaptiveSizePolicy && Verbose) {
      gclog_or_tty->print_cr("    capacities are the right sizes, returning");
    }
    return;
  }

  char* eden_start = (char*)eden()->bottom();
  char* eden_end   = (char*)eden()->end();
  char* from_start = (char*)from()->bottom();
  char* from_end   = (char*)from()->end();
  char* to_start   = (char*)to()->bottom();
  char* to_end     = (char*)to()->end();

  const size_t alignment = os::vm_page_size();
  const bool maintain_minimum =
    (requested_eden_size + 2 * requested_survivor_size) <= min_gen_size();

  // Check whether from space is below to space
  if (from_start < to_start) {
    // Eden, from, to
    if (PrintAdaptiveSizePolicy && Verbose) {
      gclog_or_tty->print_cr("  Eden, from, to:");
    }

    // Set eden
    // "requested_eden_size" is a goal for the size of eden
    // and may not be attainable.  "eden_size" below is
    // calculated based on the location of from-space and
    // the goal for the size of eden.  from-space is
    // fixed in place because it contains live data.
    // The calculation is done this way to avoid 32bit
    // overflow (i.e., eden_start + requested_eden_size
    // may too large for representation in 32bits).
    size_t eden_size;
    if (maintain_minimum) {
      // Only make eden larger than the requested size if
      // the minimum size of the generation has to be maintained.
      // This could be done in general but policy at a higher
      // level is determining a requested size for eden and that
      // should be honored unless there is a fundamental reason.
      eden_size = pointer_delta(from_start,
                                eden_start,
                                sizeof(char));
    } else {
      eden_size = MIN2(requested_eden_size,
                       pointer_delta(from_start, eden_start, sizeof(char)));
    }

    eden_size = align_size_down(eden_size, alignment);
    eden_end = eden_start + eden_size;
    assert(eden_end >= eden_start, "addition overflowed");

    // To may resize into from space as long as it is clear of live data.
    // From space must remain page aligned, though, so we need to do some
    // extra calculations.

    // First calculate an optimal to-space
    to_end   = (char*)virtual_space()->high();
    to_start = (char*)pointer_delta(to_end, (char*)requested_survivor_size,
                                    sizeof(char));

    // Does the optimal to-space overlap from-space?
    if (to_start < (char*)from()->end()) {
      // Calculate the minimum offset possible for from_end
      size_t from_size = pointer_delta(from()->top(), from_start, sizeof(char));

      // Should we be in this method if from_space is empty? Why not the set_space method? FIX ME!
      if (from_size == 0) {
        from_size = alignment;
      } else {
        from_size = align_size_up(from_size, alignment);
      }

      from_end = from_start + from_size;
      assert(from_end > from_start, "addition overflow or from_size problem");

      guarantee(from_end <= (char*)from()->end(), "from_end moved to the right");

      // Now update to_start with the new from_end
      to_start = MAX2(from_end, to_start);
    } else {
      // If shrinking, move to-space down to abut the end of from-space
      // so that shrinking will move to-space down.  If not shrinking
      // to-space is moving up to allow for growth on the next expansion.
      if (requested_eden_size <= cur_eden_size) {
        to_start = from_end;
        if (to_start + requested_survivor_size > to_start) {
          to_end = to_start + requested_survivor_size;
        }
      }
      // else leave to_end pointing to the high end of the virtual space.
    }

    guarantee(to_start != to_end, "to space is zero sized");

    if (PrintAdaptiveSizePolicy && Verbose) {
      gclog_or_tty->print_cr("    [eden_start .. eden_end): "
                    "[" PTR_FORMAT " .. " PTR_FORMAT ") " SIZE_FORMAT,
                    eden_start,
                    eden_end,
                    pointer_delta(eden_end, eden_start, sizeof(char)));
      gclog_or_tty->print_cr("    [from_start .. from_end): "
                    "[" PTR_FORMAT " .. " PTR_FORMAT ") " SIZE_FORMAT,
                    from_start,
                    from_end,
                    pointer_delta(from_end, from_start, sizeof(char)));
      gclog_or_tty->print_cr("    [  to_start ..   to_end): "
                    "[" PTR_FORMAT " .. " PTR_FORMAT ") " SIZE_FORMAT,
                    to_start,
                    to_end,
                    pointer_delta(  to_end,   to_start, sizeof(char)));
    }
  } else {
    // Eden, to, from
    if (PrintAdaptiveSizePolicy && Verbose) {
      gclog_or_tty->print_cr("  Eden, to, from:");
    }

    // Calculate the to-space boundaries based on
    // the start of from-space.
    to_end = from_start;
    to_start = (char*)pointer_delta(from_start,
                                    (char*)requested_survivor_size,
                                    sizeof(char));
    // Calculate the ideal eden boundaries.
    // eden_end is already at the bottom of the generation
    assert(eden_start == virtual_space()->low(),
      "Eden is not starting at the low end of the virtual space");
    if (eden_start + requested_eden_size >= eden_start) {
      eden_end = eden_start + requested_eden_size;
    } else {
      eden_end = to_start;
    }

    // Does eden intrude into to-space?  to-space
    // gets priority but eden is not allowed to shrink
    // to 0.
    if (eden_end > to_start) {
      eden_end = to_start;
    }

    // Don't let eden shrink down to 0 or less.
    eden_end = MAX2(eden_end, eden_start + alignment);
    assert(eden_start + alignment >= eden_start, "Overflow");

    size_t eden_size;
    if (maintain_minimum) {
      // Use all the space available.
      eden_end = MAX2(eden_end, to_start);
      eden_size = pointer_delta(eden_end, eden_start, sizeof(char));
      eden_size = MIN2(eden_size, cur_eden_size);
    } else {
      eden_size = pointer_delta(eden_end, eden_start, sizeof(char));
    }
    eden_size = align_size_down(eden_size, alignment);
    assert(maintain_minimum || eden_size <= requested_eden_size,
      "Eden size is too large");
    assert(eden_size >= alignment, "Eden size is too small");
    eden_end = eden_start + eden_size;

    // Move to-space down to eden.
    if (requested_eden_size < cur_eden_size) {
      to_start = eden_end;
      if (to_start + requested_survivor_size > to_start) {
        to_end = MIN2(from_start, to_start + requested_survivor_size);
      } else {
        to_end = from_start;
      }
    }

    // eden_end may have moved so again make sure
    // the to-space and eden don't overlap.
    to_start = MAX2(eden_end, to_start);

    // from-space
    size_t from_used = from()->used();
    if (requested_survivor_size > from_used) {
      if (from_start + requested_survivor_size >= from_start) {
        from_end = from_start + requested_survivor_size;
      }
      if (from_end > virtual_space()->high()) {
        from_end = virtual_space()->high();
      }
    }

    assert(to_start >= eden_end, "to-space should be above eden");
    if (PrintAdaptiveSizePolicy && Verbose) {
      gclog_or_tty->print_cr("    [eden_start .. eden_end): "
                    "[" PTR_FORMAT " .. " PTR_FORMAT ") " SIZE_FORMAT,
                    eden_start,
                    eden_end,
                    pointer_delta(eden_end, eden_start, sizeof(char)));
      gclog_or_tty->print_cr("    [  to_start ..   to_end): "
                    "[" PTR_FORMAT " .. " PTR_FORMAT ") " SIZE_FORMAT,
                    to_start,
                    to_end,
                    pointer_delta(  to_end,   to_start, sizeof(char)));
      gclog_or_tty->print_cr("    [from_start .. from_end): "
                    "[" PTR_FORMAT " .. " PTR_FORMAT ") " SIZE_FORMAT,
                    from_start,
                    from_end,
                    pointer_delta(from_end, from_start, sizeof(char)));
    }
  }


  guarantee((HeapWord*)from_start <= from()->bottom(),
            "from start moved to the right");
  guarantee((HeapWord*)from_end >= from()->top(),
            "from end moved into live data");
  assert(is_object_aligned((intptr_t)eden_start), "checking alignment");
  assert(is_object_aligned((intptr_t)from_start), "checking alignment");
  assert(is_object_aligned((intptr_t)to_start), "checking alignment");

  MemRegion edenMR((HeapWord*)eden_start, (HeapWord*)eden_end);
  MemRegion toMR  ((HeapWord*)to_start,   (HeapWord*)to_end);
  MemRegion fromMR((HeapWord*)from_start, (HeapWord*)from_end);

  // Let's make sure the call to initialize doesn't reset "top"!
  HeapWord* old_from_top = from()->top();

  // For PrintAdaptiveSizePolicy block  below
  size_t old_from = from()->capacity();
  size_t old_to   = to()->capacity();

  // If not clearing the spaces, do some checking to verify that
  // the spaces are already mangled.

  // Must check mangling before the spaces are reshaped.  Otherwise,
  // the bottom or end of one space may have moved into another
  // a failure of the check may not correctly indicate which space
  // is not properly mangled.
  if (ZapUnusedHeapArea) {
    HeapWord* limit = (HeapWord*) virtual_space()->high();
    eden()->check_mangled_unused_area(limit);
    from()->check_mangled_unused_area(limit);
      to()->check_mangled_unused_area(limit);
  }

  // The call to initialize NULL's the next compaction space
  eden()->initialize(edenMR,
                     SpaceDecorator::Clear,
                     SpaceDecorator::DontMangle);
  eden()->set_next_compaction_space(from());
    to()->initialize(toMR  ,
                     SpaceDecorator::Clear,
                     SpaceDecorator::DontMangle);
  from()->initialize(fromMR,
                     SpaceDecorator::DontClear,
                     SpaceDecorator::DontMangle);

  assert(from()->top() == old_from_top, "from top changed!");

  if (PrintAdaptiveSizePolicy) {
    GenCollectedHeap* gch = GenCollectedHeap::heap();
    assert(gch->kind() == CollectedHeap::GenCollectedHeap, "Sanity");

    gclog_or_tty->print("AdaptiveSizePolicy::survivor space sizes: "
                  "collection: %d "
                  "(" SIZE_FORMAT ", " SIZE_FORMAT ") -> "
                  "(" SIZE_FORMAT ", " SIZE_FORMAT ") ",
                  gch->total_collections(),
                  old_from, old_to,
                  from()->capacity(),
                  to()->capacity());
    gclog_or_tty->cr();
  }
}

void ASParNewGeneration::compute_new_size() {
  GenCollectedHeap* gch = GenCollectedHeap::heap();
  assert(gch->kind() == CollectedHeap::GenCollectedHeap,
    "not a CMS generational heap");


  CMSAdaptiveSizePolicy* size_policy =
    (CMSAdaptiveSizePolicy*)gch->gen_policy()->size_policy();
  assert(size_policy->is_gc_cms_adaptive_size_policy(),
    "Wrong type of size policy");

  size_t survived = from()->used();
  if (!survivor_overflow()) {
    // Keep running averages on how much survived
    size_policy->avg_survived()->sample(survived);
  } else {
    size_t promoted =
      (size_t) next_gen()->gc_stats()->avg_promoted()->last_sample();
    assert(promoted < gch->capacity(), "Conversion problem?");
    size_t survived_guess = survived + promoted;
    size_policy->avg_survived()->sample(survived_guess);
  }

  size_t survivor_limit = max_survivor_size();
  _tenuring_threshold =
    size_policy->compute_survivor_space_size_and_threshold(
                                                     _survivor_overflow,
                                                     _tenuring_threshold,
                                                     survivor_limit);
  size_policy->avg_young_live()->sample(used());
  size_policy->avg_eden_live()->sample(eden()->used());

  size_policy->compute_young_generation_free_space(eden()->capacity(),
                                                   max_gen_size());

  resize(size_policy->calculated_eden_size_in_bytes(),
         size_policy->calculated_survivor_size_in_bytes());

  if (UsePerfData) {
    CMSGCAdaptivePolicyCounters* counters =
      (CMSGCAdaptivePolicyCounters*) gch->collector_policy()->counters();
    assert(counters->kind() ==
           GCPolicyCounters::CMSGCAdaptivePolicyCountersKind,
      "Wrong kind of counters");
    counters->update_tenuring_threshold(_tenuring_threshold);
    counters->update_survivor_overflowed(_survivor_overflow);
    counters->update_young_capacity(capacity());
  }
}


#ifndef PRODUCT
// Changes from PSYoungGen version
//      value of "alignment"
void ASParNewGeneration::space_invariants() {
  const size_t alignment = os::vm_page_size();

  // Currently, our eden size cannot shrink to zero
  guarantee(eden()->capacity() >= alignment, "eden too small");
  guarantee(from()->capacity() >= alignment, "from too small");
  guarantee(to()->capacity() >= alignment, "to too small");

  // Relationship of spaces to each other
  char* eden_start = (char*)eden()->bottom();
  char* eden_end   = (char*)eden()->end();
  char* from_start = (char*)from()->bottom();
  char* from_end   = (char*)from()->end();
  char* to_start   = (char*)to()->bottom();
  char* to_end     = (char*)to()->end();

  guarantee(eden_start >= virtual_space()->low(), "eden bottom");
  guarantee(eden_start < eden_end, "eden space consistency");
  guarantee(from_start < from_end, "from space consistency");
  guarantee(to_start < to_end, "to space consistency");

  // Check whether from space is below to space
  if (from_start < to_start) {
    // Eden, from, to
    guarantee(eden_end <= from_start, "eden/from boundary");
    guarantee(from_end <= to_start,   "from/to boundary");
    guarantee(to_end <= virtual_space()->high(), "to end");
  } else {
    // Eden, to, from
    guarantee(eden_end <= to_start, "eden/to boundary");
    guarantee(to_end <= from_start, "to/from boundary");
    guarantee(from_end <= virtual_space()->high(), "from end");
  }

  // More checks that the virtual space is consistent with the spaces
  assert(virtual_space()->committed_size() >=
    (eden()->capacity() +
     to()->capacity() +
     from()->capacity()), "Committed size is inconsistent");
  assert(virtual_space()->committed_size() <= virtual_space()->reserved_size(),
    "Space invariant");
  char* eden_top = (char*)eden()->top();
  char* from_top = (char*)from()->top();
  char* to_top = (char*)to()->top();
  assert(eden_top <= virtual_space()->high(), "eden top");
  assert(from_top <= virtual_space()->high(), "from top");
  assert(to_top <= virtual_space()->high(), "to top");
}
#endif
