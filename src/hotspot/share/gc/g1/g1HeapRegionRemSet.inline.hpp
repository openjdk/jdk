/*
 * Copyright (c) 1997, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_G1_G1HEAPREGIONREMSET_INLINE_HPP
#define SHARE_VM_GC_G1_G1HEAPREGIONREMSET_INLINE_HPP

#include "gc/g1/g1HeapRegionRemSet.hpp"

#include "gc/g1/g1CardSet.inline.hpp"
#include "gc/g1/g1CollectedHeap.inline.hpp"
#include "gc/g1/g1HeapRegion.inline.hpp"
#include "runtime/atomic.hpp"
#include "utilities/bitMap.inline.hpp"

void G1HeapRegionRemSet::set_state_untracked() {
  guarantee(SafepointSynchronize::is_at_safepoint() || !is_tracked(),
            "Should only set to Untracked during safepoint but is %s.", get_state_str());
  if (_state == Untracked) {
    return;
  }
  clear_fcc();
  _state = Untracked;
}

void G1HeapRegionRemSet::set_state_updating() {
  guarantee(SafepointSynchronize::is_at_safepoint() && !is_tracked(),
            "Should only set to Updating from Untracked during safepoint but is %s", get_state_str());
  clear_fcc();
  _state = Updating;
}

void G1HeapRegionRemSet::set_state_complete() {
  clear_fcc();
  _state = Complete;
}

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

template <typename Closure, template <typename> class CardOrRanges>
class G1HeapRegionRemSetMergeCardClosure : public G1CardSet::ContainerPtrClosure {
  G1CardSet* _card_set;
  Closure& _cl;
  uint _log_card_regions_per_region;
  uint _card_regions_per_region_mask;
  uint _log_card_region_size;

public:

  G1HeapRegionRemSetMergeCardClosure(G1CardSet* card_set,
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
    CardOrRanges<Closure> cl(_cl,
                             card_region_idx >> _log_card_regions_per_region,
                             (card_region_idx & _card_regions_per_region_mask) << _log_card_region_size);
    _card_set->iterate_cards_or_ranges_in_container(container, cl);
  }
};

template <class CardOrRangeVisitor>
inline void G1HeapRegionRemSet::iterate_for_merge(CardOrRangeVisitor& cl) {
  G1HeapRegionRemSetMergeCardClosure<CardOrRangeVisitor, G1ContainerCardsOrRanges> cl2(&_card_set,
                                                                                       cl,
                                                                                       _card_set.config()->log2_card_regions_per_heap_region(),
                                                                                       _card_set.config()->log2_cards_per_card_region());
  _card_set.iterate_containers(&cl2, true /* at_safepoint */);
}


uintptr_t G1HeapRegionRemSet::to_card(OopOrNarrowOopStar from) const {
  return pointer_delta(from, _heap_base_address, 1) >> CardTable::card_shift();
}

void G1HeapRegionRemSet::add_reference(OopOrNarrowOopStar from, uint tid) {
  assert(_state != Untracked, "must be");

  uint cur_idx = _hr->hrm_index();
  uintptr_t from_card = uintptr_t(from) >> CardTable::card_shift();

  if (G1FromCardCache::contains_or_replace(tid, cur_idx, from_card)) {
    // We can't check whether the card is in the remembered set - the card container
    // may be coarsened just now.
    //assert(contains_reference(from), "We just found " PTR_FORMAT " in the FromCardCache", p2i(from));
   return;
  }

  _card_set.add_card(to_card(from));
}

bool G1HeapRegionRemSet::contains_reference(OopOrNarrowOopStar from) {
  return _card_set.contains_card(to_card(from));
}

void G1HeapRegionRemSet::print_info(outputStream* st, OopOrNarrowOopStar from) {
  _card_set.print_info(st, to_card(from));
}

#endif // SHARE_VM_GC_G1_G1HEAPREGIONREMSET_INLINE_HPP
