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
#include "gc/g1/g1HeterogeneousCollectorPolicy.hpp"
#include "gc/g1/g1HeterogeneousHeapYoungGenSizer.hpp"
#include "gc/g1/heapRegion.hpp"

G1HeterogeneousHeapYoungGenSizer::G1HeterogeneousHeapYoungGenSizer() : G1YoungGenSizer() {
  // will be used later when min and max young size is calculated.
  _max_young_length = (uint)(G1HeterogeneousCollectorPolicy::reasonable_max_memory_for_young() / HeapRegion::GrainBytes);
}

// Since heap is sized potentially to larger value accounting for dram + nvdimm, we need to limit
// max young gen size to the available dram.
// Call parent class method first and then adjust sizes based on available dram
void G1HeterogeneousHeapYoungGenSizer::adjust_max_new_size(uint number_of_heap_regions) {
  G1YoungGenSizer::adjust_max_new_size(number_of_heap_regions);
  adjust_lengths_based_on_dram_memory();
}

void G1HeterogeneousHeapYoungGenSizer::heap_size_changed(uint new_number_of_heap_regions) {
  G1YoungGenSizer::heap_size_changed(new_number_of_heap_regions);
  adjust_lengths_based_on_dram_memory();
}

void G1HeterogeneousHeapYoungGenSizer::adjust_lengths_based_on_dram_memory() {
  _min_desired_young_length = MIN2(_min_desired_young_length, _max_young_length);
  _max_desired_young_length = MIN2(_max_desired_young_length, _max_young_length);
}
