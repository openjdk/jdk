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

#ifndef SHARE_GC_G1_G1CARDSETGROUP_HPP
#define SHARE_GC_G1_G1CARDSETGROUP_HPP

#include "gc/g1/g1CardSetMemory.hpp"
#include "gc/shared/gc_globals.hpp"
#include "memory/allocation.hpp"
#include "runtime/atomic.hpp"
#include "utilities/growableArray.hpp"

class G1HeapRegion;

struct G1CardSetGroupItem {
  G1HeapRegion* _r;
  uint _num_unreclaimed;          // Number of GCs this region has been found unreclaimable.

  G1CardSetGroupItem() : G1CardSetGroupItem(nullptr) { }
  G1CardSetGroupItem(G1HeapRegion* r) : _r(r), _num_unreclaimed(0) { }

  bool update_num_unreclaimed() {
    ++_num_unreclaimed;
    return _num_unreclaimed < G1NumCollectionsKeepPinned;
  }
};

using G1CardSetGroupIterator = GrowableArrayIterator<G1CardSetGroupItem>;

// G1CardSetGroup groups regions that share a single G1CardSet.
//
// Applications of this grouping are
// * all young gen regions
// * candidate regions determined by marking
// * regions retained due to evacuation failure
// * regions covered by humongous start regions
//
// The shared card set records remembered set entries for all regions in the group
// as a whole. No references between these regions are recorded. This saves memory,
// but requires reclamation of multi-region card set groups together as a single unit.
class G1CardSetGroup : public CHeapObj<mtGCCardSet>{
  GrowableArray<G1CardSetGroupItem> _items;

  G1CardSetMemoryManager _card_set_mm;

  // The set of cards in the Java heap for this card set group.
  G1CardSet _card_set;

  size_t _reclaimable_bytes;
  double _gc_efficiency;
  // The _group_id identifies the card set group for logging and for use in the
  // FromCardCache. A group id must be unique among all currently used card set groups.
  uint _group_id;

public:
  static constexpr uint NoGroupId = 0;
  static constexpr uint YoungId = NoGroupId + 1;
  static constexpr uint FirstNonYoungId = YoungId + 1;
  static constexpr uint InvalidId = UINT_MAX;

  G1CardSetGroup();
  G1CardSetGroup(G1CardSetConfiguration* config, G1MonotonicArenaFreePool* card_set_freelist_pool, uint group_id);
  ~G1CardSetGroup() {
    assert(num_regions() == 0, "post condition!");
  }

  void add(G1HeapRegion* hr);

  uint num_regions() const { return (uint)_items.length(); }

  G1CardSet* card_set() { return &_card_set; }
  const G1CardSet* card_set() const { return &_card_set; }

  void calculate_efficiency();

  double liveness_percent() const;
  // Comparison function to order card set groups in decreasing GC efficiency order. This
  // will cause card set groups with a lot of live objects and large card sets to end
  // up at the end of the list.
  static int compare_gc_efficiency(G1CardSetGroup** gr1, G1CardSetGroup** gr2);

  double gc_efficiency() const { return _gc_efficiency; }

  G1HeapRegion* region_at(uint i) const { return _items.at(i)._r; }

  G1CardSetGroupItem* at(uint i) { return &_items.at(i); }

  double predict_group_total_time_ms() const;

  G1MonotonicArenaMemoryStats card_set_memory_stats() const {
    return _card_set_mm.memory_stats();
  }

  size_t cards_occupied() const {
    return _card_set.occupied();
  }

  // Clear the group-owned card set.
  void clear_card_set();

  void clear(bool uninstall_card_set_group = false);

  G1CardSetGroupIterator begin() const {
    return _items.begin();
  }

  G1CardSetGroupIterator end() const {
    return _items.end();
  }

  uint group_id() const {
    assert(_group_id != InvalidId, "group must have an assigned id");
    return _group_id;
  }
};

using G1CardSetGroupListIterator = GrowableArrayIterator<G1CardSetGroup*>;

class G1CardSetGroupList {
  GrowableArray<G1CardSetGroup*> _groups;
  Atomic<uint> _num_regions;

public:
  G1CardSetGroupList();
  void append(G1CardSetGroup* group);

  // Delete all groups from the list. The card set group uninstall for regions within
  // the groups could have been done elsewhere (e.g. when adding groups to the
  // collection set or to the retained card set group list). The uninstall_card_set_group
  // parameter should be set to true if the card set groups must be uninstalled from
  // the regions, and their state set to Untracked.
  void clear(bool uninstall_card_set_group = false);

  G1CardSetGroup* at(uint index);

  uint length() const { return (uint)_groups.length(); }

  uint num_regions() const { return _num_regions.load_relaxed(); }

  void remove_selected(uint count, uint num_regions);

  // Removes any card set groups stored in this and in the other list. The other
  // list may only contain card set groups in this list, sorted by gc efficiency. The
  // other list need not be a prefix of this list.
  // E.g. if this list is "A B G H", the other list may be "A G H", but not "F" (not in
  // this list) or "A H G" (wrong order).
  void remove(G1CardSetGroupList* other);

  void prepare_for_scan();

  void sort_by_efficiency();

  GrowableArray<G1CardSetGroup*>*  groups() {
    return &_groups;
  }

  void verify() const PRODUCT_RETURN;

  G1CardSetGroupListIterator begin() const {
    return _groups.begin();
  }

  G1CardSetGroupListIterator end() const {
    return _groups.end();
  }

  template<typename Func>
  void iterate(Func&& f) const;
};

#endif /* SHARE_GC_G1_G1CARDSETGROUP_HPP */
