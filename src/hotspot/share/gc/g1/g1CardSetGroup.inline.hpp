/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_G1_G1CARDSETGROUP_INLINE_HPP
#define SHARE_GC_G1_G1CARDSETGROUP_INLINE_HPP

#include "gc/g1/g1CardSetGroup.hpp"

#include "gc/g1/g1CardSet.inline.hpp"

template <typename Closure>
class G1ContainerCardsOrRanges {
  Closure& _cl;
  uint _region_idx;
  uint _offset;

public:
  G1ContainerCardsOrRanges(Closure& cl, uint region_idx, uint offset) : _cl(cl), _region_idx(region_idx), _offset(offset) { }

  bool start_iterate(uint tag) {
    return _cl.start_iterate(tag, _region_idx);
  }

  void operator()(uint card_idx) {
    _cl.do_card(card_idx + _offset);
  }

  void operator()(uint card_idx, uint length) {
    _cl.do_card_range(card_idx + _offset, length);
  }
};

template <typename Closure>
class G1CardSetGroupMergeCardClosure : public G1CardSet::ContainerPtrClosure {
  G1CardSet* _card_set;
  Closure& _cl;
  uint _log_card_regions_per_region;
  uint _card_regions_per_region_mask;
  uint _log_card_region_size;

public:
  G1CardSetGroupMergeCardClosure(G1CardSet* card_set,
                                 Closure& cl,
                                 uint log_card_regions_per_region,
                                 uint log_card_region_size) :
    _card_set(card_set),
    _cl(cl),
    _log_card_regions_per_region(log_card_regions_per_region),
    _card_regions_per_region_mask((1 << log_card_regions_per_region) - 1),
    _log_card_region_size(log_card_region_size) {
  }

  void do_containerptr(uint card_region_idx, size_t num_occupied, G1CardSet::ContainerPtr container) override {
    G1ContainerCardsOrRanges<Closure> cl(_cl,
                                         card_region_idx >> _log_card_regions_per_region,
                                         (card_region_idx & _card_regions_per_region_mask) << _log_card_region_size);
    _card_set->iterate_cards_or_ranges_in_container(container, cl);
  }
};

template <class CardOrRangeVisitor>
inline void G1CardSetGroup::iterate_for_merge(CardOrRangeVisitor& cl) {
  G1CardSetGroupMergeCardClosure<CardOrRangeVisitor> cl2(card_set(),
                                                         cl,
                                                         card_set()->config()->log2_card_regions_per_heap_region(),
                                                         card_set()->config()->log2_cards_per_card_region());
  card_set()->iterate_containers(&cl2, true /* at_safepoint */);
}

template<typename Func>
void G1CardSetGroupList::iterate(Func&& f) const {
  for (G1CardSetGroup* group : _groups) {
    for (G1CardSetGroupItem ci : *group) {
      G1HeapRegion* r = ci._r;
      f(r);
    }
  }
}

#endif /* SHARE_GC_G1_G1CARDSETGROUP_INLINE_HPP */
