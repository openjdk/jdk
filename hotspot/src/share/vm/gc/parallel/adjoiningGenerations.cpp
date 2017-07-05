/*
 * Copyright (c) 2003, 2015, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/parallel/adjoiningGenerations.hpp"
#include "gc/parallel/adjoiningVirtualSpaces.hpp"
#include "gc/parallel/generationSizer.hpp"
#include "gc/parallel/parallelScavengeHeap.hpp"
#include "logging/log.hpp"
#include "memory/resourceArea.hpp"
#include "utilities/ostream.hpp"

// If boundary moving is being used, create the young gen and old
// gen with ASPSYoungGen and ASPSOldGen, respectively.  Revert to
// the old behavior otherwise (with PSYoungGen and PSOldGen).

AdjoiningGenerations::AdjoiningGenerations(ReservedSpace old_young_rs,
                                           GenerationSizer* policy,
                                           size_t alignment) :
  _virtual_spaces(old_young_rs, policy->min_old_size(),
                  policy->min_young_size(), alignment) {
  size_t init_low_byte_size = policy->initial_old_size();
  size_t min_low_byte_size = policy->min_old_size();
  size_t max_low_byte_size = policy->max_old_size();
  size_t init_high_byte_size = policy->initial_young_size();
  size_t min_high_byte_size = policy->min_young_size();
  size_t max_high_byte_size = policy->max_young_size();

  assert(min_low_byte_size <= init_low_byte_size &&
         init_low_byte_size <= max_low_byte_size, "Parameter check");
  assert(min_high_byte_size <= init_high_byte_size &&
         init_high_byte_size <= max_high_byte_size, "Parameter check");
  // Create the generations differently based on the option to
  // move the boundary.
  if (UseAdaptiveGCBoundary) {
    // Initialize the adjoining virtual spaces.  Then pass the
    // a virtual to each generation for initialization of the
    // generation.

    // Does the actual creation of the virtual spaces
    _virtual_spaces.initialize(max_low_byte_size,
                               init_low_byte_size,
                               init_high_byte_size);

    // Place the young gen at the high end.  Passes in the virtual space.
    _young_gen = new ASPSYoungGen(_virtual_spaces.high(),
                                  _virtual_spaces.high()->committed_size(),
                                  min_high_byte_size,
                                  _virtual_spaces.high_byte_size_limit());

    // Place the old gen at the low end. Passes in the virtual space.
    _old_gen = new ASPSOldGen(_virtual_spaces.low(),
                              _virtual_spaces.low()->committed_size(),
                              min_low_byte_size,
                              _virtual_spaces.low_byte_size_limit(),
                              "old", 1);

    young_gen()->initialize_work();
    assert(young_gen()->reserved().byte_size() <= young_gen()->gen_size_limit(),
     "Consistency check");
    assert(old_young_rs.size() >= young_gen()->gen_size_limit(),
     "Consistency check");

    old_gen()->initialize_work("old", 1);
    assert(old_gen()->reserved().byte_size() <= old_gen()->gen_size_limit(),
     "Consistency check");
    assert(old_young_rs.size() >= old_gen()->gen_size_limit(),
     "Consistency check");
  } else {

    // Layout the reserved space for the generations.
    ReservedSpace old_rs   =
      virtual_spaces()->reserved_space().first_part(max_low_byte_size);
    ReservedSpace heap_rs  =
      virtual_spaces()->reserved_space().last_part(max_low_byte_size);
    ReservedSpace young_rs = heap_rs.first_part(max_high_byte_size);
    assert(young_rs.size() == heap_rs.size(), "Didn't reserve all of the heap");

    // Create the generations.  Virtual spaces are not passed in.
    _young_gen = new PSYoungGen(init_high_byte_size,
                                min_high_byte_size,
                                max_high_byte_size);
    _old_gen = new PSOldGen(init_low_byte_size,
                            min_low_byte_size,
                            max_low_byte_size,
                            "old", 1);

    // The virtual spaces are created by the initialization of the gens.
    _young_gen->initialize(young_rs, alignment);
    assert(young_gen()->gen_size_limit() == young_rs.size(),
      "Consistency check");
    _old_gen->initialize(old_rs, alignment, "old", 1);
    assert(old_gen()->gen_size_limit() == old_rs.size(), "Consistency check");
  }
}

size_t AdjoiningGenerations::reserved_byte_size() {
  return virtual_spaces()->reserved_space().size();
}

void log_before_expansion(bool old, size_t expand_in_bytes, size_t change_in_bytes, size_t max_size) {
  Log(heap, ergo) log;
  if (!log.is_debug()) {
   return;
  }
  log.debug("Before expansion of %s gen with boundary move", old ? "old" : "young");
  log.debug("  Requested change: " SIZE_FORMAT_HEX "  Attempted change: " SIZE_FORMAT_HEX,
                        expand_in_bytes, change_in_bytes);
  ResourceMark rm;
  ParallelScavengeHeap::heap()->print_on(log.debug_stream());
  log.debug("  PS%sGen max size: " SIZE_FORMAT "K", old ? "Old" : "Young", max_size/K);
}

void log_after_expansion(bool old, size_t max_size) {
  Log(heap, ergo) log;
  if (!log.is_debug()) {
   return;
  }
  log.debug("After expansion of %s gen with boundary move", old ? "old" : "young");
  ResourceMark rm;
  ParallelScavengeHeap::heap()->print_on(log.debug_stream());
  log.debug("  PS%sGen max size: " SIZE_FORMAT "K", old ? "Old" : "Young", max_size/K);
}

// Make checks on the current sizes of the generations and
// the constraints on the sizes of the generations.  Push
// up the boundary within the constraints.  A partial
// push can occur.
void AdjoiningGenerations::request_old_gen_expansion(size_t expand_in_bytes) {
  assert(UseAdaptiveSizePolicy && UseAdaptiveGCBoundary, "runtime check");

  assert_lock_strong(ExpandHeap_lock);
  assert_locked_or_safepoint(Heap_lock);

  // These sizes limit the amount the boundaries can move.  Effectively,
  // the generation says how much it is willing to yield to the other
  // generation.
  const size_t young_gen_available = young_gen()->available_for_contraction();
  const size_t old_gen_available = old_gen()->available_for_expansion();
  const size_t alignment = virtual_spaces()->alignment();
  size_t change_in_bytes = MIN3(young_gen_available,
                                old_gen_available,
                                align_size_up_(expand_in_bytes, alignment));

  if (change_in_bytes == 0) {
    return;
  }

  log_before_expansion(true, expand_in_bytes, change_in_bytes, old_gen()->max_gen_size());

  // Move the boundary between the generations up (smaller young gen).
  if (virtual_spaces()->adjust_boundary_up(change_in_bytes)) {
    young_gen()->reset_after_change();
    old_gen()->reset_after_change();
  }

  // The total reserved for the generations should match the sum
  // of the two even if the boundary is moving.
  assert(reserved_byte_size() ==
         old_gen()->max_gen_size() + young_gen()->max_size(),
         "Space is missing");
  young_gen()->space_invariants();
  old_gen()->space_invariants();

  log_after_expansion(true, old_gen()->max_gen_size());
}

// See comments on request_old_gen_expansion()
bool AdjoiningGenerations::request_young_gen_expansion(size_t expand_in_bytes) {
  assert(UseAdaptiveSizePolicy && UseAdaptiveGCBoundary, "runtime check");

  // If eden is not empty, the boundary can be moved but no advantage
  // can be made of the move since eden cannot be moved.
  if (!young_gen()->eden_space()->is_empty()) {
    return false;
  }


  bool result = false;
  const size_t young_gen_available = young_gen()->available_for_expansion();
  const size_t old_gen_available = old_gen()->available_for_contraction();
  const size_t alignment = virtual_spaces()->alignment();
  size_t change_in_bytes = MIN3(young_gen_available,
                                old_gen_available,
                                align_size_up_(expand_in_bytes, alignment));

  if (change_in_bytes == 0) {
    return false;
  }

  log_before_expansion(false, expand_in_bytes, change_in_bytes, young_gen()->max_size());

  // Move the boundary between the generations down (smaller old gen).
  MutexLocker x(ExpandHeap_lock);
  if (virtual_spaces()->adjust_boundary_down(change_in_bytes)) {
    young_gen()->reset_after_change();
    old_gen()->reset_after_change();
    result = true;
  }

  // The total reserved for the generations should match the sum
  // of the two even if the boundary is moving.
  assert(reserved_byte_size() ==
         old_gen()->max_gen_size() + young_gen()->max_size(),
         "Space is missing");
  young_gen()->space_invariants();
  old_gen()->space_invariants();

  log_after_expansion(false, young_gen()->max_size());

  return result;
}

// Additional space is needed in the old generation.  Try to move the boundary
// up to meet the need.  Moves boundary up only
void AdjoiningGenerations::adjust_boundary_for_old_gen_needs(
  size_t desired_free_space) {
  assert(UseAdaptiveSizePolicy && UseAdaptiveGCBoundary, "runtime check");

  // Stress testing.
  if (PSAdaptiveSizePolicyResizeVirtualSpaceAlot == 1) {
    MutexLocker x(ExpandHeap_lock);
    request_old_gen_expansion(virtual_spaces()->alignment() * 3 / 2);
  }

  // Expand only if the entire generation is already committed.
  if (old_gen()->virtual_space()->uncommitted_size() == 0) {
    if (old_gen()->free_in_bytes() < desired_free_space) {
      MutexLocker x(ExpandHeap_lock);
      request_old_gen_expansion(desired_free_space);
    }
  }
}

// See comment on adjust_boundary_for_old_gen_needss().
// Adjust boundary down only.
void AdjoiningGenerations::adjust_boundary_for_young_gen_needs(size_t eden_size,
    size_t survivor_size) {

  assert(UseAdaptiveSizePolicy && UseAdaptiveGCBoundary, "runtime check");

  // Stress testing.
  if (PSAdaptiveSizePolicyResizeVirtualSpaceAlot == 0) {
    request_young_gen_expansion(virtual_spaces()->alignment() * 3 / 2);
    eden_size = young_gen()->eden_space()->capacity_in_bytes();
  }

  // Expand only if the entire generation is already committed.
  if (young_gen()->virtual_space()->uncommitted_size() == 0) {
    size_t desired_size = eden_size + 2 * survivor_size;
    const size_t committed = young_gen()->virtual_space()->committed_size();
    if (desired_size > committed) {
      request_young_gen_expansion(desired_size - committed);
    }
  }
}
