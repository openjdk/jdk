/*
 * Copyright (c) 2001, 2010, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_IMPLEMENTATION_PARALLELSCAVENGE_CARDTABLEEXTENSION_HPP
#define SHARE_VM_GC_IMPLEMENTATION_PARALLELSCAVENGE_CARDTABLEEXTENSION_HPP

#include "memory/cardTableModRefBS.hpp"

class MutableSpace;
class ObjectStartArray;
class PSPromotionManager;
class GCTaskQueue;

class CardTableExtension : public CardTableModRefBS {
 private:
  // Support methods for resizing the card table.
  // resize_commit_uncommit() returns true if the pages were committed or
  // uncommitted
  bool resize_commit_uncommit(int changed_region, MemRegion new_region);
  void resize_update_card_table_entries(int changed_region,
                                        MemRegion new_region);
  void resize_update_committed_table(int changed_region, MemRegion new_region);
  void resize_update_covered_table(int changed_region, MemRegion new_region);

 protected:

  static void verify_all_young_refs_precise_helper(MemRegion mr);

 public:
  enum ExtendedCardValue {
    youngergen_card   = CardTableModRefBS::CT_MR_BS_last_reserved + 1,
    verify_card       = CardTableModRefBS::CT_MR_BS_last_reserved + 5
  };

  CardTableExtension(MemRegion whole_heap, int max_covered_regions) :
    CardTableModRefBS(whole_heap, max_covered_regions) { }

  // Too risky for the 4/10/02 putback
  // BarrierSet::Name kind() { return BarrierSet::CardTableExtension; }

  // Scavenge support
  void scavenge_contents_parallel(ObjectStartArray* start_array,
                                  MutableSpace* sp,
                                  HeapWord* space_top,
                                  PSPromotionManager* pm,
                                  uint stripe_number,
                                  uint stripe_total);

  // Verification
  static void verify_all_young_refs_imprecise();
  static void verify_all_young_refs_precise();

  bool addr_is_marked_imprecise(void *addr);
  bool addr_is_marked_precise(void *addr);

  void set_card_newgen(void* addr)   { jbyte* p = byte_for(addr); *p = verify_card; }

  // Testers for entries
  static bool card_is_dirty(int value)      { return value == dirty_card; }
  static bool card_is_newgen(int value)     { return value == youngergen_card; }
  static bool card_is_clean(int value)      { return value == clean_card; }
  static bool card_is_verify(int value)     { return value == verify_card; }

  // Card marking
  void inline_write_ref_field_gc(void* field, oop new_val) {
    jbyte* byte = byte_for(field);
    *byte = youngergen_card;
  }

  // Adaptive size policy support
  // Allows adjustment of the base and size of the covered regions
  void resize_covered_region(MemRegion new_region);
  // Finds the covered region to resize based on the start address
  // of the covered regions.
  void resize_covered_region_by_start(MemRegion new_region);
  // Finds the covered region to resize based on the end address
  // of the covered regions.
  void resize_covered_region_by_end(int changed_region, MemRegion new_region);
  // Finds the lowest start address of a covered region that is
  // previous (i.e., lower index) to the covered region with index "ind".
  HeapWord* lowest_prev_committed_start(int ind) const;

#ifdef ASSERT

  bool is_valid_card_address(jbyte* addr) {
    return (addr >= _byte_map) && (addr < _byte_map + _byte_map_size);
  }

#endif // ASSERT
};

#endif // SHARE_VM_GC_IMPLEMENTATION_PARALLELSCAVENGE_CARDTABLEEXTENSION_HPP
