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

#ifndef SHARE_VM_GC_G1_G1HETEROGENEOUSHEAPYOUNGGENSIZER_HPP
#define SHARE_VM_GC_G1_G1HETEROGENEOUSHEAPYOUNGGENSIZER_HPP

#include "gc/g1/g1YoungGenSizer.hpp"

// This class prevents the size of young generation of G1 heap to exceed dram
// memory available. If set on command line, MaxRAM and MaxRAMFraction/MaxRAMPercentage
// are used to determine the maximum size that young generation can grow.
// Else we set the maximum size to 80% of dram available in the system.

class G1HeterogeneousHeapYoungGenSizer : public G1YoungGenSizer {
private:
  // maximum no of regions that young generation can grow to. Calculated in constructor.
  uint _max_young_length;
  void adjust_lengths_based_on_dram_memory();

public:
  G1HeterogeneousHeapYoungGenSizer();

  // Calculate the maximum length of the young gen given the number of regions
  // depending on the sizing algorithm.
  virtual void adjust_max_new_size(uint number_of_heap_regions);

  virtual void heap_size_changed(uint new_number_of_heap_regions);
};

#endif // SHARE_VM_GC_G1_G1HETEROGENEOUSHEAPYOUNGGENSIZER_HPP
