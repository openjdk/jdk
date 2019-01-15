/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/parallel/adjoiningGenerationsForHeteroHeap.hpp"
#include "gc/parallel/adjoiningVirtualSpaces.hpp"
#include "gc/parallel/generationSizer.hpp"
#include "gc/parallel/parallelScavengeHeap.hpp"
#include "gc/parallel/psFileBackedVirtualspace.hpp"
#include "logging/log.hpp"
#include "logging/logStream.hpp"
#include "memory/resourceArea.hpp"
#include "utilities/align.hpp"
#include "utilities/ostream.hpp"

// Create two virtual spaces (HeteroVirtualSpaces), low() on nv-dimm memory, high() on dram.
// create ASPSOldGen and ASPSYoungGen the same way as in base class

AdjoiningGenerationsForHeteroHeap::AdjoiningGenerationsForHeteroHeap(ReservedSpace old_young_rs, GenerationSizer* policy, size_t alignment) :
  _total_size_limit(policy->max_heap_byte_size()) {
  size_t init_old_byte_size = policy->initial_old_size();
  size_t min_old_byte_size = policy->min_old_size();
  size_t max_old_byte_size = policy->max_old_size();
  size_t init_young_byte_size = policy->initial_young_size();
  size_t min_young_byte_size = policy->min_young_size();
  size_t max_young_byte_size = policy->max_young_size();
  // create HeteroVirtualSpaces which is composed of non-overlapping virtual spaces.
  HeteroVirtualSpaces* hetero_virtual_spaces = new HeteroVirtualSpaces(old_young_rs, min_old_byte_size,
                                                                       min_young_byte_size, _total_size_limit, alignment);

  assert(min_old_byte_size <= init_old_byte_size &&
         init_old_byte_size <= max_old_byte_size, "Parameter check");
  assert(min_young_byte_size <= init_young_byte_size &&
         init_young_byte_size <= max_young_byte_size, "Parameter check");

  assert(UseAdaptiveGCBoundary, "Should be used only when UseAdaptiveGCBoundary is true");

  // Initialize the virtual spaces. Then pass a virtual space to each generation
  // for initialization of the generation.

  // Does the actual creation of the virtual spaces
  hetero_virtual_spaces->initialize(max_old_byte_size, init_old_byte_size, init_young_byte_size);

  _young_gen = new ASPSYoungGen(hetero_virtual_spaces->high(),
                                hetero_virtual_spaces->high()->committed_size() /* intial_size */,
                                min_young_byte_size,
                                hetero_virtual_spaces->max_young_size());

  _old_gen = new ASPSOldGen(hetero_virtual_spaces->low(),
                            hetero_virtual_spaces->low()->committed_size() /* intial_size */,
                            min_old_byte_size,
                            hetero_virtual_spaces->max_old_size(), "old", 1);

  young_gen()->initialize_work();
  assert(young_gen()->reserved().byte_size() <= young_gen()->gen_size_limit(), "Consistency check");
  assert(old_young_rs.size() >= young_gen()->gen_size_limit(), "Consistency check");

  old_gen()->initialize_work("old", 1);
  assert(old_gen()->reserved().byte_size() <= old_gen()->gen_size_limit(), "Consistency check");
  assert(old_young_rs.size() >= old_gen()->gen_size_limit(), "Consistency check");

  _virtual_spaces = hetero_virtual_spaces;
}

size_t AdjoiningGenerationsForHeteroHeap::required_reserved_memory(GenerationSizer* policy) {
  // This is the size that young gen can grow to, when AdaptiveGCBoundary is true.
  size_t max_yg_size = policy->max_heap_byte_size() - policy->min_old_size();
  // This is the size that old gen can grow to, when AdaptiveGCBoundary is true.
  size_t max_old_size = policy->max_heap_byte_size() - policy->min_young_size();

  return max_yg_size + max_old_size;
}

// We override this function since size of reservedspace here is more than heap size and
// callers expect this function to return heap size.
size_t AdjoiningGenerationsForHeteroHeap::reserved_byte_size() {
  return total_size_limit();
}

AdjoiningGenerationsForHeteroHeap::HeteroVirtualSpaces::HeteroVirtualSpaces(ReservedSpace rs, size_t min_old_byte_size, size_t min_yg_byte_size, size_t max_total_size, size_t alignment) :
                                                                            AdjoiningVirtualSpaces(rs, min_old_byte_size, min_yg_byte_size, alignment),
                                                                            _max_total_size(max_total_size),
                                                                            _min_old_byte_size(min_old_byte_size), _min_young_byte_size(min_yg_byte_size),
                                                                            _max_old_byte_size(_max_total_size - _min_young_byte_size),
                                                                            _max_young_byte_size(_max_total_size - _min_old_byte_size) {
}

void AdjoiningGenerationsForHeteroHeap::HeteroVirtualSpaces::initialize(size_t initial_old_reserved_size, size_t init_old_byte_size,
                                                                        size_t init_young_byte_size) {

  // This is the reserved space exclusively for old generation.
  ReservedSpace low_rs = _reserved_space.first_part(_max_old_byte_size, true);
  // Intially we only assign 'initial_old_reserved_size' of the reserved space to old virtual space.
  low_rs = low_rs.first_part(initial_old_reserved_size);

  // This is the reserved space exclusively for young generation.
  ReservedSpace high_rs = _reserved_space.last_part(_max_old_byte_size).first_part(_max_young_byte_size);

  // Carve out 'initial_young_reserved_size' of reserved space.
  size_t initial_young_reserved_size = _max_total_size - initial_old_reserved_size;
  high_rs = high_rs.last_part(_max_young_byte_size - initial_young_reserved_size);

  _low = new PSFileBackedVirtualSpace(low_rs, alignment(), AllocateOldGenAt);
  if (!static_cast <PSFileBackedVirtualSpace*>(_low)->initialize()) {
    vm_exit_during_initialization("Could not map space for old generation at given AllocateOldGenAt path");
  }

  if (!_low->expand_by(init_old_byte_size)) {
    vm_exit_during_initialization("Could not reserve enough space for object heap");
  }

  _high = new PSVirtualSpaceHighToLow(high_rs, alignment());
  if (!_high->expand_by(init_young_byte_size)) {
    vm_exit_during_initialization("Could not reserve enough space for object heap");
  }
}

// Since the virtual spaces are non-overlapping, there is no boundary as such.
// We replicate the same behavior and maintain the same invariants as base class 'AdjoiningVirtualSpaces' by
// increasing old generation size and decreasing young generation size by same amount.
bool AdjoiningGenerationsForHeteroHeap::HeteroVirtualSpaces::adjust_boundary_up(size_t change_in_bytes) {
  assert(UseAdaptiveSizePolicy && UseAdaptiveGCBoundary, "runtime check");
  DEBUG_ONLY(size_t total_size_before = young_vs()->reserved_size() + old_vs()->reserved_size());

  size_t bytes_needed = change_in_bytes;
  size_t uncommitted_in_old = MIN2(old_vs()->uncommitted_size(), bytes_needed);
  bool old_expanded = false;

  // 1. Try to expand old within its reserved space.
  if (uncommitted_in_old != 0) {
    if (!old_vs()->expand_by(uncommitted_in_old)) {
      return false;
    }
    old_expanded = true;
    bytes_needed -= uncommitted_in_old;
    if (bytes_needed == 0) {
      return true;
    }
  }

  size_t bytes_to_add_in_old = 0;

  // 2. Get uncommitted memory from Young virtualspace.
  size_t young_uncommitted = MIN2(young_vs()->uncommitted_size(), bytes_needed);
  if (young_uncommitted > 0) {
    young_vs()->set_reserved(young_vs()->reserved_low_addr() + young_uncommitted,
                             young_vs()->reserved_high_addr(),
                             young_vs()->special());
    bytes_needed -= young_uncommitted;
    bytes_to_add_in_old = young_uncommitted;
  }

  // 3. Get committed memory from Young virtualspace
  if (bytes_needed > 0) {
    size_t shrink_size = align_down(bytes_needed, young_vs()->alignment());
    bool ret = young_vs()->shrink_by(shrink_size);
    assert(ret, "We should be able to shrink young space");
    young_vs()->set_reserved(young_vs()->reserved_low_addr() + shrink_size,
                             young_vs()->reserved_high_addr(),
                             young_vs()->special());

    bytes_to_add_in_old += shrink_size;
  }

  // 4. Increase size of old space
  old_vs()->set_reserved(old_vs()->reserved_low_addr(),
                         old_vs()->reserved_high_addr() + bytes_to_add_in_old,
                         old_vs()->special());
  if (!old_vs()->expand_by(bytes_to_add_in_old) && !old_expanded) {
    return false;
  }

  DEBUG_ONLY(size_t total_size_after = young_vs()->reserved_size() + old_vs()->reserved_size());
  assert(total_size_after == total_size_before, "should be equal");

  return true;
}

// Read comment for adjust_boundary_up()
// Increase young generation size and decrease old generation size by same amount.
bool AdjoiningGenerationsForHeteroHeap::HeteroVirtualSpaces::adjust_boundary_down(size_t change_in_bytes) {
  assert(UseAdaptiveSizePolicy && UseAdaptiveGCBoundary, "runtime check");
  DEBUG_ONLY(size_t total_size_before = young_vs()->reserved_size() + old_vs()->reserved_size());

  size_t bytes_needed = change_in_bytes;
  size_t uncommitted_in_young = MIN2(young_vs()->uncommitted_size(), bytes_needed);
  bool young_expanded = false;

  // 1. Try to expand old within its reserved space.
  if (uncommitted_in_young > 0) {
    if (!young_vs()->expand_by(uncommitted_in_young)) {
      return false;
    }
    young_expanded = true;
    bytes_needed -= uncommitted_in_young;
    if (bytes_needed == 0) {
      return true;
    }
  }

  size_t bytes_to_add_in_young = 0;

  // 2. Get uncommitted memory from Old virtualspace.
  size_t old_uncommitted = MIN2(old_vs()->uncommitted_size(), bytes_needed);
  if (old_uncommitted > 0) {
    old_vs()->set_reserved(old_vs()->reserved_low_addr(),
                           old_vs()->reserved_high_addr() - old_uncommitted,
                           old_vs()->special());
    bytes_needed -= old_uncommitted;
    bytes_to_add_in_young = old_uncommitted;
  }

  // 3. Get committed memory from Old virtualspace
  if (bytes_needed > 0) {
    size_t shrink_size = align_down(bytes_needed, old_vs()->alignment());
    bool ret = old_vs()->shrink_by(shrink_size);
    assert(ret, "We should be able to shrink young space");
           old_vs()->set_reserved(old_vs()->reserved_low_addr(),
           old_vs()->reserved_high_addr() - shrink_size,
           old_vs()->special());

    bytes_to_add_in_young += shrink_size;
  }

  assert(bytes_to_add_in_young <= change_in_bytes, "should not be more than requested size");
  // 4. Increase size of young space
  young_vs()->set_reserved(young_vs()->reserved_low_addr() - bytes_to_add_in_young,
                           young_vs()->reserved_high_addr(),
                           young_vs()->special());
  if (!young_vs()->expand_by(bytes_to_add_in_young) && !young_expanded) {
    return false;
  }

  DEBUG_ONLY(size_t total_size_after = young_vs()->reserved_size() + old_vs()->reserved_size());
  assert(total_size_after == total_size_before, "should be equal");

  return true;
}

