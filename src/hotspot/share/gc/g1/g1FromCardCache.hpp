/*
 * Copyright (c) 2013, 2026, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_G1_G1FROMCARDCACHE_HPP
#define SHARE_GC_G1_G1FROMCARDCACHE_HPP

#include "gc/shared/gc_globals.hpp"
#include "oops/oopsHierarchy.hpp"
#include "utilities/globalDefinitions.hpp"

// G1FromCardCache remembers which destination card set groups have been
// encountered while a worker scans the current from_card.
//
// Refinement and remembered set rebuild scan the heap linearly, visiting
// references from a card consecutively. Therefore, the cache only tracks
// the destination card set groups found while scanning the current card. The
// cache state is discarded when advancing to the next card.
//
// A scan can be suspended at a yield point. A GC may run while it is
// suspended and change the card set group assignments. Therefore, the cache
// must be reset before the scan resumes after every yield.
class G1FromCardCache {
  // Worst case: each reference in a card targets a different card set group.
  static constexpr uint MaxGroupsPerCard = MaxGCCardSizeInBytes / sizeof(narrowOop);

  uintptr_t _from_card;
  uint _num_card_set_groups;
  uint _card_set_group_ids[MaxGroupsPerCard];

  NONCOPYABLE(G1FromCardCache);

public:
  G1FromCardCache()
    : _from_card(0),
      _num_card_set_groups(0) {}

  // Discard the state associated with the _from_card.
  void reset() {
    _num_card_set_groups = 0;
  }

  // Returns true if card_set_group_id has already been encountered while
  // scanning from_card. Otherwise, records the id and returns false.
  inline bool contains_or_add(uintptr_t from_card, uint card_set_group_id);
};

#endif // SHARE_GC_G1_G1FROMCARDCACHE_HPP
