/*
 * Copyright (c) 2003, 2010, Oracle and/or its affiliates. All rights reserved.
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
#include "gc_implementation/parallelScavenge/asPSYoungGen.hpp"
#include "gc_implementation/parallelScavenge/parallelScavengeHeap.hpp"
#include "gc_implementation/parallelScavenge/psMarkSweepDecorator.hpp"
#include "gc_implementation/parallelScavenge/psScavenge.hpp"
#include "gc_implementation/parallelScavenge/psYoungGen.hpp"
#include "gc_implementation/shared/gcUtil.hpp"
#include "gc_implementation/shared/spaceDecorator.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/java.hpp"

ASPSYoungGen::ASPSYoungGen(size_t init_byte_size,
                           size_t minimum_byte_size,
                           size_t byte_size_limit) :
  PSYoungGen(init_byte_size, minimum_byte_size, byte_size_limit),
  _gen_size_limit(byte_size_limit) {
}


ASPSYoungGen::ASPSYoungGen(PSVirtualSpace* vs,
                           size_t init_byte_size,
                           size_t minimum_byte_size,
                           size_t byte_size_limit) :
  //PSYoungGen(init_byte_size, minimum_byte_size, byte_size_limit),
  PSYoungGen(vs->committed_size(), minimum_byte_size, byte_size_limit),
  _gen_size_limit(byte_size_limit) {

  assert(vs->committed_size() == init_byte_size, "Cannot replace with");

  _virtual_space = vs;
}

void ASPSYoungGen::initialize_virtual_space(ReservedSpace rs,
                                            size_t alignment) {
  assert(_init_gen_size != 0, "Should have a finite size");
  _virtual_space = new PSVirtualSpaceHighToLow(rs, alignment);
  if (!_virtual_space->expand_by(_init_gen_size)) {
    vm_exit_during_initialization("Could not reserve enough space for "
                                  "object heap");
  }
}

void ASPSYoungGen::initialize(ReservedSpace rs, size_t alignment) {
  initialize_virtual_space(rs, alignment);
  initialize_work();
}

size_t ASPSYoungGen::available_for_expansion() {
  size_t current_committed_size = virtual_space()->committed_size();
  assert((gen_size_limit() >= current_committed_size),
    "generation size limit is wrong");
  ParallelScavengeHeap* heap = (ParallelScavengeHeap*)Universe::heap();
  size_t result =  gen_size_limit() - current_committed_size;
  size_t result_aligned = align_size_down(result, heap->generation_alignment());
  return result_aligned;
}

// Return the number of bytes the young gen is willing give up.
//
// Future implementations could check the survivors and if to_space is in the
// right place (below from_space), take a chunk from to_space.
size_t ASPSYoungGen::available_for_contraction() {
  size_t uncommitted_bytes = virtual_space()->uncommitted_size();
  if (uncommitted_bytes != 0) {
    return uncommitted_bytes;
  }

  if (eden_space()->is_empty()) {
    // Respect the minimum size for eden and for the young gen as a whole.
    ParallelScavengeHeap* heap = (ParallelScavengeHeap*)Universe::heap();
    const size_t eden_alignment = heap->space_alignment();
    const size_t gen_alignment = heap->generation_alignment();

    assert(eden_space()->capacity_in_bytes() >= eden_alignment,
      "Alignment is wrong");
    size_t eden_avail = eden_space()->capacity_in_bytes() - eden_alignment;
    eden_avail = align_size_down(eden_avail, gen_alignment);

    assert(virtual_space()->committed_size() >= min_gen_size(),
      "minimum gen size is wrong");
    size_t gen_avail = virtual_space()->committed_size() - min_gen_size();
    assert(virtual_space()->is_aligned(gen_avail), "not aligned");

    const size_t max_contraction = MIN2(eden_avail, gen_avail);
    // See comment for ASPSOldGen::available_for_contraction()
    // for reasons the "increment" fraction is used.
    PSAdaptiveSizePolicy* policy = heap->size_policy();
    size_t result = policy->eden_increment_aligned_down(max_contraction);
    size_t result_aligned = align_size_down(result, gen_alignment);
    if (PrintAdaptiveSizePolicy && Verbose) {
      gclog_or_tty->print_cr("ASPSYoungGen::available_for_contraction: %d K",
        result_aligned/K);
      gclog_or_tty->print_cr("  max_contraction %d K", max_contraction/K);
      gclog_or_tty->print_cr("  eden_avail %d K", eden_avail/K);
      gclog_or_tty->print_cr("  gen_avail %d K", gen_avail/K);
    }
    return result_aligned;
  }

  return 0;
}

// The current implementation only considers to the end of eden.
// If to_space is below from_space, to_space is not considered.
// to_space can be.
size_t ASPSYoungGen::available_to_live() {
  ParallelScavengeHeap* heap = (ParallelScavengeHeap*)Universe::heap();
  const size_t alignment = heap->space_alignment();

  // Include any space that is committed but is not in eden.
  size_t available = pointer_delta(eden_space()->bottom(),
                                   virtual_space()->low(),
                                   sizeof(char));

  const size_t eden_capacity = eden_space()->capacity_in_bytes();
  if (eden_space()->is_empty() && eden_capacity > alignment) {
    available += eden_capacity - alignment;
  }
  return available;
}

// Similar to PSYoungGen::resize_generation() but
//  allows sum of eden_size and 2 * survivor_size to exceed _max_gen_size
//  expands at the low end of the virtual space
//  moves the boundary between the generations in order to expand
//  some additional diagnostics
// If no additional changes are required, this can be deleted
// and the changes factored back into PSYoungGen::resize_generation().
bool ASPSYoungGen::resize_generation(size_t eden_size, size_t survivor_size) {
  const size_t alignment = virtual_space()->alignment();
  size_t orig_size = virtual_space()->committed_size();
  bool size_changed = false;

  // There used to be a guarantee here that
  //   (eden_size + 2*survivor_size)  <= _max_gen_size
  // This requirement is enforced by the calculation of desired_size
  // below.  It may not be true on entry since the size of the
  // eden_size is no bounded by the generation size.

  assert(max_size() == reserved().byte_size(), "max gen size problem?");
  assert(min_gen_size() <= orig_size && orig_size <= max_size(),
         "just checking");

  // Adjust new generation size
  const size_t eden_plus_survivors =
    align_size_up(eden_size + 2 * survivor_size, alignment);
  size_t desired_size = MAX2(MIN2(eden_plus_survivors, gen_size_limit()),
                             min_gen_size());
  assert(desired_size <= gen_size_limit(), "just checking");

  if (desired_size > orig_size) {
    // Grow the generation
    size_t change = desired_size - orig_size;
    HeapWord* prev_low = (HeapWord*) virtual_space()->low();
    if (!virtual_space()->expand_by(change)) {
      return false;
    }
    if (ZapUnusedHeapArea) {
      // Mangle newly committed space immediately because it
      // can be done here more simply that after the new
      // spaces have been computed.
      HeapWord* new_low = (HeapWord*) virtual_space()->low();
      assert(new_low < prev_low, "Did not grow");

      MemRegion mangle_region(new_low, prev_low);
      SpaceMangler::mangle_region(mangle_region);
    }
    size_changed = true;
  } else if (desired_size < orig_size) {
    size_t desired_change = orig_size - desired_size;

    // How much is available for shrinking.
    size_t available_bytes = limit_gen_shrink(desired_change);
    size_t change = MIN2(desired_change, available_bytes);
    virtual_space()->shrink_by(change);
    size_changed = true;
  } else {
    if (Verbose && PrintGC) {
      if (orig_size == gen_size_limit()) {
        gclog_or_tty->print_cr("ASPSYoung generation size at maximum: "
          SIZE_FORMAT "K", orig_size/K);
      } else if (orig_size == min_gen_size()) {
        gclog_or_tty->print_cr("ASPSYoung generation size at minium: "
          SIZE_FORMAT "K", orig_size/K);
      }
    }
  }

  if (size_changed) {
    reset_after_change();
    if (Verbose && PrintGC) {
      size_t current_size  = virtual_space()->committed_size();
      gclog_or_tty->print_cr("ASPSYoung generation size changed: "
        SIZE_FORMAT "K->" SIZE_FORMAT "K",
        orig_size/K, current_size/K);
    }
  }

  guarantee(eden_plus_survivors <= virtual_space()->committed_size() ||
            virtual_space()->committed_size() == max_size(), "Sanity");

  return true;
}

// Similar to PSYoungGen::resize_spaces() but
//  eden always starts at the low end of the committed virtual space
//  current implementation does not allow holes between the spaces
//  _young_generation_boundary has to be reset because it changes.
//  so additional verification

void ASPSYoungGen::resize_spaces(size_t requested_eden_size,
                                 size_t requested_survivor_size) {
  assert(UseAdaptiveSizePolicy, "sanity check");
  assert(requested_eden_size > 0 && requested_survivor_size > 0,
         "just checking");

  space_invariants();

  // We require eden and to space to be empty
  if ((!eden_space()->is_empty()) || (!to_space()->is_empty())) {
    return;
  }

  if (PrintAdaptiveSizePolicy && Verbose) {
    gclog_or_tty->print_cr("PSYoungGen::resize_spaces(requested_eden_size: "
                  SIZE_FORMAT
                  ", requested_survivor_size: " SIZE_FORMAT ")",
                  requested_eden_size, requested_survivor_size);
    gclog_or_tty->print_cr("    eden: [" PTR_FORMAT ".." PTR_FORMAT ") "
                  SIZE_FORMAT,
                  eden_space()->bottom(),
                  eden_space()->end(),
                  pointer_delta(eden_space()->end(),
                                eden_space()->bottom(),
                                sizeof(char)));
    gclog_or_tty->print_cr("    from: [" PTR_FORMAT ".." PTR_FORMAT ") "
                  SIZE_FORMAT,
                  from_space()->bottom(),
                  from_space()->end(),
                  pointer_delta(from_space()->end(),
                                from_space()->bottom(),
                                sizeof(char)));
    gclog_or_tty->print_cr("      to: [" PTR_FORMAT ".." PTR_FORMAT ") "
                  SIZE_FORMAT,
                  to_space()->bottom(),
                  to_space()->end(),
                  pointer_delta(  to_space()->end(),
                                  to_space()->bottom(),
                                  sizeof(char)));
  }

  // There's nothing to do if the new sizes are the same as the current
  if (requested_survivor_size == to_space()->capacity_in_bytes() &&
      requested_survivor_size == from_space()->capacity_in_bytes() &&
      requested_eden_size == eden_space()->capacity_in_bytes()) {
    if (PrintAdaptiveSizePolicy && Verbose) {
      gclog_or_tty->print_cr("    capacities are the right sizes, returning");
    }
    return;
  }

  char* eden_start = (char*)virtual_space()->low();
  char* eden_end   = (char*)eden_space()->end();
  char* from_start = (char*)from_space()->bottom();
  char* from_end   = (char*)from_space()->end();
  char* to_start   = (char*)to_space()->bottom();
  char* to_end     = (char*)to_space()->end();

  assert(eden_start < from_start, "Cannot push into from_space");

  ParallelScavengeHeap* heap = (ParallelScavengeHeap*)Universe::heap();
  const size_t alignment = heap->space_alignment();
  const bool maintain_minimum =
    (requested_eden_size + 2 * requested_survivor_size) <= min_gen_size();

  bool eden_from_to_order = from_start < to_start;
  // Check whether from space is below to space
  if (eden_from_to_order) {
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

    eden_end = eden_start + eden_size;
    assert(eden_end >= eden_start, "addition overflowed");

    // To may resize into from space as long as it is clear of live data.
    // From space must remain page aligned, though, so we need to do some
    // extra calculations.

    // First calculate an optimal to-space
    to_end   = (char*)virtual_space()->high();
    to_start = (char*)pointer_delta(to_end,
                                    (char*)requested_survivor_size,
                                    sizeof(char));

    // Does the optimal to-space overlap from-space?
    if (to_start < (char*)from_space()->end()) {
      assert(heap->kind() == CollectedHeap::ParallelScavengeHeap, "Sanity");

      // Calculate the minimum offset possible for from_end
      size_t from_size =
        pointer_delta(from_space()->top(), from_start, sizeof(char));

      // Should we be in this method if from_space is empty? Why not the set_space method? FIX ME!
      if (from_size == 0) {
        from_size = alignment;
      } else {
        from_size = align_size_up(from_size, alignment);
      }

      from_end = from_start + from_size;
      assert(from_end > from_start, "addition overflow or from_size problem");

      guarantee(from_end <= (char*)from_space()->end(),
        "from_end moved to the right");

      // Now update to_start with the new from_end
      to_start = MAX2(from_end, to_start);
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

    // To space gets priority over eden resizing. Note that we position
    // to space as if we were able to resize from space, even though from
    // space is not modified.
    // Giving eden priority was tried and gave poorer performance.
    to_end   = (char*)pointer_delta(virtual_space()->high(),
                                    (char*)requested_survivor_size,
                                    sizeof(char));
    to_end   = MIN2(to_end, from_start);
    to_start = (char*)pointer_delta(to_end, (char*)requested_survivor_size,
                                    sizeof(char));
    // if the space sizes are to be increased by several times then
    // 'to_start' will point beyond the young generation. In this case
    // 'to_start' should be adjusted.
    to_start = MAX2(to_start, eden_start + alignment);

    // Compute how big eden can be, then adjust end.
    // See  comments above on calculating eden_end.
    size_t eden_size;
    if (maintain_minimum) {
      eden_size = pointer_delta(to_start, eden_start, sizeof(char));
    } else {
      eden_size = MIN2(requested_eden_size,
                       pointer_delta(to_start, eden_start, sizeof(char)));
    }
    eden_end = eden_start + eden_size;
    assert(eden_end >= eden_start, "addition overflowed");

    // Don't let eden shrink down to 0 or less.
    eden_end = MAX2(eden_end, eden_start + alignment);
    to_start = MAX2(to_start, eden_end);

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


  guarantee((HeapWord*)from_start <= from_space()->bottom(),
            "from start moved to the right");
  guarantee((HeapWord*)from_end >= from_space()->top(),
            "from end moved into live data");
  assert(is_object_aligned((intptr_t)eden_start), "checking alignment");
  assert(is_object_aligned((intptr_t)from_start), "checking alignment");
  assert(is_object_aligned((intptr_t)to_start), "checking alignment");

  MemRegion edenMR((HeapWord*)eden_start, (HeapWord*)eden_end);
  MemRegion toMR  ((HeapWord*)to_start,   (HeapWord*)to_end);
  MemRegion fromMR((HeapWord*)from_start, (HeapWord*)from_end);

  // Let's make sure the call to initialize doesn't reset "top"!
  DEBUG_ONLY(HeapWord* old_from_top = from_space()->top();)

  // For PrintAdaptiveSizePolicy block  below
  size_t old_from = from_space()->capacity_in_bytes();
  size_t old_to   = to_space()->capacity_in_bytes();

  if (ZapUnusedHeapArea) {
    // NUMA is a special case because a numa space is not mangled
    // in order to not prematurely bind its address to memory to
    // the wrong memory (i.e., don't want the GC thread to first
    // touch the memory).  The survivor spaces are not numa
    // spaces and are mangled.
    if (UseNUMA) {
      if (eden_from_to_order) {
        mangle_survivors(from_space(), fromMR, to_space(), toMR);
      } else {
        mangle_survivors(to_space(), toMR, from_space(), fromMR);
      }
    }

    // If not mangling the spaces, do some checking to verify that
    // the spaces are already mangled.
    // The spaces should be correctly mangled at this point so
    // do some checking here. Note that they are not being mangled
    // in the calls to initialize().
    // Must check mangling before the spaces are reshaped.  Otherwise,
    // the bottom or end of one space may have moved into an area
    // covered by another space and a failure of the check may
    // not correctly indicate which space is not properly mangled.

    HeapWord* limit = (HeapWord*) virtual_space()->high();
    eden_space()->check_mangled_unused_area(limit);
    from_space()->check_mangled_unused_area(limit);
      to_space()->check_mangled_unused_area(limit);
  }
  // When an existing space is being initialized, it is not
  // mangled because the space has been previously mangled.
  eden_space()->initialize(edenMR,
                           SpaceDecorator::Clear,
                           SpaceDecorator::DontMangle);
    to_space()->initialize(toMR,
                           SpaceDecorator::Clear,
                           SpaceDecorator::DontMangle);
  from_space()->initialize(fromMR,
                           SpaceDecorator::DontClear,
                           SpaceDecorator::DontMangle);

  PSScavenge::set_young_generation_boundary(eden_space()->bottom());

  assert(from_space()->top() == old_from_top, "from top changed!");

  if (PrintAdaptiveSizePolicy) {
    ParallelScavengeHeap* heap = (ParallelScavengeHeap*)Universe::heap();
    assert(heap->kind() == CollectedHeap::ParallelScavengeHeap, "Sanity");

    gclog_or_tty->print("AdaptiveSizePolicy::survivor space sizes: "
                  "collection: %d "
                  "(" SIZE_FORMAT ", " SIZE_FORMAT ") -> "
                  "(" SIZE_FORMAT ", " SIZE_FORMAT ") ",
                  heap->total_collections(),
                  old_from, old_to,
                  from_space()->capacity_in_bytes(),
                  to_space()->capacity_in_bytes());
    gclog_or_tty->cr();
  }
  space_invariants();
}
void ASPSYoungGen::reset_after_change() {
  assert_locked_or_safepoint(Heap_lock);

  _reserved = MemRegion((HeapWord*)virtual_space()->low_boundary(),
                        (HeapWord*)virtual_space()->high_boundary());
  PSScavenge::reference_processor()->set_span(_reserved);

  HeapWord* new_eden_bottom = (HeapWord*)virtual_space()->low();
  HeapWord* eden_bottom = eden_space()->bottom();
  if (new_eden_bottom != eden_bottom) {
    MemRegion eden_mr(new_eden_bottom, eden_space()->end());
    eden_space()->initialize(eden_mr,
                             SpaceDecorator::Clear,
                             SpaceDecorator::Mangle);
    PSScavenge::set_young_generation_boundary(eden_space()->bottom());
  }
  MemRegion cmr((HeapWord*)virtual_space()->low(),
                (HeapWord*)virtual_space()->high());
  Universe::heap()->barrier_set()->resize_covered_region(cmr);

  space_invariants();
}
