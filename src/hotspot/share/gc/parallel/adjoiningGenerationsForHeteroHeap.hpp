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

#ifndef SHARE_VM_GC_PARALLEL_ADJOININGGENERATIONSFORHETEROHEAP_HPP
#define SHARE_VM_GC_PARALLEL_ADJOININGGENERATIONSFORHETEROHEAP_HPP

#include "gc/parallel/adjoiningGenerations.hpp"

class AdjoiningGenerationsForHeteroHeap : public AdjoiningGenerations {
  friend class VMStructs;
private:
  // Maximum total size of the generations. This is equal to the heap size specified by user.
  // When adjusting young and old generation sizes, we need ensure that sum of the generation sizes does not exceed this.
  size_t _total_size_limit;

  size_t total_size_limit() const {
    return _total_size_limit;
  }

  // HeteroVirtualSpaces creates non-overlapping virtual spaces. Here _low and _high do not share a reserved space, i.e. there is no boundary
  // separating the two virtual spaces.
  class HeteroVirtualSpaces : public AdjoiningVirtualSpaces {
    size_t _max_total_size;
    size_t _min_old_byte_size;
    size_t _min_young_byte_size;
    size_t _max_old_byte_size;
    size_t _max_young_byte_size;

    // Internally we access the virtual spaces using these methods. It increases readability, since we were not really
    // dealing with adjoining virtual spaces separated by a boundary as is the case in base class.
    // Externally they are accessed using low() and high() methods of base class.
    PSVirtualSpace* young_vs() { return high(); }
    PSVirtualSpace* old_vs() { return low(); }

  public:
    HeteroVirtualSpaces(ReservedSpace rs,
                        size_t min_old_byte_size,
                        size_t min_young_byte_size, size_t max_total_size,
                        size_t alignment);

    // Increase old generation size and decrease young generation size by same amount
    bool adjust_boundary_up(size_t size_in_bytes);
    // Increase young generation size and decrease old generation size by same amount
    bool adjust_boundary_down(size_t size_in_bytes);

    size_t max_young_size() const { return _max_young_byte_size; }
    size_t max_old_size() const { return _max_old_byte_size; }

    void initialize(size_t initial_old_reserved_size, size_t init_low_byte_size,
                    size_t init_high_byte_size);
  };

public:
  AdjoiningGenerationsForHeteroHeap(ReservedSpace rs, GenerationSizer* policy, size_t alignment);

  // Given the size policy, calculate the total amount of memory that needs to be reserved.
  // We need to reserve more memory than Xmx, since we use non-overlapping virtual spaces for the young and old generations.
  static size_t required_reserved_memory(GenerationSizer* policy);

  // Return the total byte size of the reserved space
  size_t reserved_byte_size();
};
#endif // SHARE_VM_GC_PARALLEL_ADJOININGGENERATIONSFORHETEROHEAP_HPP

