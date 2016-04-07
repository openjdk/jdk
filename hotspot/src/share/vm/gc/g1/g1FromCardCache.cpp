/*
 * Copyright (c) 2001, 2016, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/g1/g1FromCardCache.hpp"
#include "gc/g1/g1RemSet.hpp"
#include "memory/padded.inline.hpp"
#include "utilities/debug.hpp"

int**  G1FromCardCache::_cache = NULL;
uint   G1FromCardCache::_max_regions = 0;
size_t G1FromCardCache::_static_mem_size = 0;

void G1FromCardCache::initialize(uint num_par_rem_sets, uint max_num_regions) {
  guarantee(max_num_regions > 0, "Heap size must be valid");
  guarantee(_cache == NULL, "Should not call this multiple times");

  _max_regions = max_num_regions;
  _cache = Padded2DArray<int, mtGC>::create_unfreeable(_max_regions,
                                                       num_par_rem_sets,
                                                       &_static_mem_size);

  invalidate(0, _max_regions);
}

void G1FromCardCache::invalidate(uint start_idx, size_t new_num_regions) {
  guarantee((size_t)start_idx + new_num_regions <= max_uintx,
            "Trying to invalidate beyond maximum region, from %u size " SIZE_FORMAT,
            start_idx, new_num_regions);
  uint end_idx = (start_idx + (uint)new_num_regions);
  assert(end_idx <= _max_regions, "Must be within max.");

  for (uint i = 0; i < G1RemSet::num_par_rem_sets(); i++) {
    for (uint j = start_idx; j < end_idx; j++) {
      set(i, j, InvalidCard);
    }
  }
}

#ifndef PRODUCT
void G1FromCardCache::print(outputStream* out) {
  for (uint i = 0; i < G1RemSet::num_par_rem_sets(); i++) {
    for (uint j = 0; j < _max_regions; j++) {
      out->print_cr("_from_card_cache[%u][%u] = %d.",
                    i, j, at(i, j));
    }
  }
}
#endif

void G1FromCardCache::clear(uint region_idx) {
  uint num_par_remsets = G1RemSet::num_par_rem_sets();
  for (uint i = 0; i < num_par_remsets; i++) {
    set(i, region_idx, InvalidCard);
  }
}
