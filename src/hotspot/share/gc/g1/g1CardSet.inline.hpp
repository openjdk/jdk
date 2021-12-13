/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_G1_G1CARDSET_INLINE_HPP
#define SHARE_GC_G1_G1CARDSET_INLINE_HPP

#include "gc/g1/g1CardSet.hpp"
#include "gc/g1/g1CardSetContainers.inline.hpp"
#include "gc/g1/g1GCPhaseTimes.hpp"
#include "runtime/atomic.hpp"
#include "logging/log.hpp"

template <class T>
inline T* G1CardSet::card_set_ptr(CardSetPtr ptr) {
  return (T*)strip_card_set_type(ptr);
}

inline G1CardSet::CardSetPtr G1CardSet::make_card_set_ptr(void* value, uintptr_t type) {
  assert(card_set_type(value) == 0, "Given ptr " PTR_FORMAT " already has type bits set", p2i(value));
  return (CardSetPtr)((uintptr_t)value | type);
}

template <class CardOrRangeVisitor>
inline void G1CardSet::iterate_cards_or_ranges_in_container(CardSetPtr const card_set, CardOrRangeVisitor& cl) {
  switch (card_set_type(card_set)) {
    case CardSetInlinePtr: {
      if (cl.start_iterate(G1GCPhaseTimes::MergeRSMergedInline)) {
        G1CardSetInlinePtr ptr(card_set);
        ptr.iterate(cl, _config->inline_ptr_bits_per_card());
      }
      return;
    }
    case CardSetArrayOfCards : {
      if (cl.start_iterate(G1GCPhaseTimes::MergeRSMergedArrayOfCards)) {
        card_set_ptr<G1CardSetArray>(card_set)->iterate(cl);
      }
      return;
    }
    case CardSetBitMap: {
      // There is no first-level bitmap spanning the whole area.
      ShouldNotReachHere();
      return;
    }
    case CardSetHowl: {
      assert(card_set_type(FullCardSet) == CardSetHowl, "Must be");
      if (card_set == FullCardSet) {
        if (cl.start_iterate(G1GCPhaseTimes::MergeRSMergedFull)) {
          cl(0, _config->max_cards_in_region());
        }
        return;
      }
      if (cl.start_iterate(G1GCPhaseTimes::MergeRSMergedHowl)) {
        card_set_ptr<G1CardSetHowl>(card_set)->iterate(cl, _config);
      }
      return;
    }
  }
  log_error(gc)("Unkown card set type %u", card_set_type(card_set));
  ShouldNotReachHere();
}

#endif // SHARE_GC_G1_G1CARDSET_INLINE_HPP
