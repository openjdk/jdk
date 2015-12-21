/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_G1_G1FROMCARDCACHE_HPP
#define SHARE_VM_GC_G1_G1FROMCARDCACHE_HPP

#include "memory/allocation.hpp"
#include "utilities/ostream.hpp"

// The FromCardCache remembers the most recently processed card on the heap on
// a per-region and per-thread basis.
class FromCardCache : public AllStatic {
 private:
  // Array of card indices. Indexed by thread X and heap region to minimize
  // thread contention.
  static int** _cache;
  static uint _max_regions;
  static size_t _static_mem_size;

 public:
  enum {
    InvalidCard = -1 // Card value of an invalid card, i.e. a card index not otherwise used.
  };

  static void clear(uint region_idx);

  // Returns true if the given card is in the cache at the given location, or
  // replaces the card at that location and returns false.
  static bool contains_or_replace(uint worker_id, uint region_idx, int card) {
    int card_in_cache = at(worker_id, region_idx);
    if (card_in_cache == card) {
      return true;
    } else {
      set(worker_id, region_idx, card);
      return false;
    }
  }

  static int at(uint worker_id, uint region_idx) {
    return _cache[worker_id][region_idx];
  }

  static void set(uint worker_id, uint region_idx, int val) {
    _cache[worker_id][region_idx] = val;
  }

  static void initialize(uint n_par_rs, uint max_num_regions);

  static void invalidate(uint start_idx, size_t num_regions);

  static void print(outputStream* out = tty) PRODUCT_RETURN;

  static size_t static_mem_size() {
    return _static_mem_size;
  }
};

#endif // SHARE_VM_GC_G1_G1FROMCARDCACHE_HPP
