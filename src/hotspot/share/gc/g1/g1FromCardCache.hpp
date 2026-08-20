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

#include "oops/oopsHierarchy.hpp"
#include "utilities/globalDefinitions.hpp"

// G1FromCardCache remembers which destination cardsets have been
// encountered while a worker scans the current source card.
class G1FromCardCache {
  // GCCardSizeInBytes is constrained to NOT_LP64(512) LP64_ONLY(1024).
  static constexpr uint MaxCardSizeInBytes = NOT_LP64(512) LP64_ONLY(1024);
  static constexpr uint MaxNumCardsets = MaxCardSizeInBytes / sizeof(narrowOop);

  uintptr_t _source_card;
  uint _num_cardsets;
  uint _cardset_ids[MaxNumCardsets];

  NONCOPYABLE(G1FromCardCache);

public:
  G1FromCardCache()
    : _source_card(0),
      _num_cardsets(0) {}

  // Discard the state associated with the _source_card. This must be called before
  // a worker begins a new refinement or rebuild scan and after a rebuild yield.
  void reset() {
    _num_cardsets = 0;
  }

  // Returns true if cardset_fcc_id has already been encountered while
  // scanning source_card. Otherwise, records the id and returns false.
  inline bool contains_or_add(uintptr_t source_card, uint cardset_id);
};

#endif // SHARE_GC_G1_G1FROMCARDCACHE_HPP
