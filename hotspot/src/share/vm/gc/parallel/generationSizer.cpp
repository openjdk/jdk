/*
 * Copyright (c) 2001, 2015, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/parallel/generationSizer.hpp"
#include "gc/shared/collectorPolicy.hpp"
#include "runtime/globals_extension.hpp"
#include "utilities/align.hpp"
#include "utilities/globalDefinitions.hpp"

void GenerationSizer::initialize_alignments() {
  _space_alignment = _gen_alignment = default_gen_alignment();
  _heap_alignment = compute_heap_alignment();
}

void GenerationSizer::initialize_flags() {
  // Do basic sizing work
  GenCollectorPolicy::initialize_flags();

  // The survivor ratio's are calculated "raw", unlike the
  // default gc, which adds 2 to the ratio value. We need to
  // make sure the values are valid before using them.
  if (MinSurvivorRatio < 3) {
    FLAG_SET_ERGO(uintx, MinSurvivorRatio, 3);
  }

  if (InitialSurvivorRatio < 3) {
    FLAG_SET_ERGO(uintx, InitialSurvivorRatio, 3);
  }
}

void GenerationSizer::initialize_size_info() {
  const size_t max_page_sz = os::page_size_for_region_aligned(_max_heap_byte_size, 8);
  const size_t min_pages = 4; // 1 for eden + 1 for each survivor + 1 for old
  const size_t min_page_sz = os::page_size_for_region_aligned(_min_heap_byte_size, min_pages);
  const size_t page_sz = MIN2(max_page_sz, min_page_sz);

  // Can a page size be something else than a power of two?
  assert(is_power_of_2((intptr_t)page_sz), "must be a power of 2");
  size_t new_alignment = align_up(page_sz, _gen_alignment);
  if (new_alignment != _gen_alignment) {
    _gen_alignment = new_alignment;
    _space_alignment = new_alignment;
    // Redo everything from the start
    initialize_flags();
  }
  GenCollectorPolicy::initialize_size_info();
}
