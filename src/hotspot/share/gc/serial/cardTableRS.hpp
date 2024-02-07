/*
 * Copyright (c) 2001, 2021, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_SERIAL_CARDTABLERS_HPP
#define SHARE_GC_SERIAL_CARDTABLERS_HPP

#include "gc/shared/cardTable.hpp"
#include "memory/memRegion.hpp"
#include "oops/oop.hpp"

class Space;
class TenuredGeneration;
class TenuredSpace;

// This RemSet uses a card table both as shared data structure
// for a mod ref barrier set and for the rem set information.

class CardTableRS : public CardTable {
  friend class VMStructs;

  static bool is_dirty(const CardValue* const v) {
    return !is_clean(v);
  }

  static bool is_clean(const CardValue* const v) {
    return *v == clean_card_val();
  }

  static void clear_cards(CardValue* start, CardValue* end);

  static CardValue* find_first_dirty_card(CardValue* start_card,
                                          CardValue* end_card);

  template<typename Func>
  CardValue* find_first_clean_card(CardValue* start_card,
                                   CardValue* end_card,
                                   CardTableRS* ct,
                                   Func& object_start);

public:
  CardTableRS(MemRegion whole_heap);

  void younger_refs_in_space_iterate(TenuredSpace* sp, OopIterateClosure* cl);

  virtual void verify_used_region_at_save_marks(Space* sp) const NOT_DEBUG_RETURN;

  void inline_write_ref_field_gc(void* field) {
    CardValue* byte = byte_for(field);
    *byte = dirty_card_val();
  }

  bool is_dirty_for_addr(const void* p) const {
    CardValue* card = byte_for(p);
    return is_dirty(card);
  }

  void verify();

  // Update old gen cards to maintain old-to-young-pointer invariant: Clear
  // the old generation card table completely if the young generation had been
  // completely evacuated, otherwise dirties the whole old generation to
  // conservatively not loose any old-to-young pointer.
  void maintain_old_to_young_invariant(TenuredGeneration* old_gen, bool is_young_gen_empty);

  // Iterate over the portion of the card-table which covers the given
  // region mr in the given space and apply cl to any dirty sub-regions
  // of mr. Clears the dirty cards as they are processed.
  void non_clean_card_iterate(TenuredSpace* sp,
                              MemRegion mr,
                              OopIterateClosure* cl,
                              CardTableRS* ct);

  bool is_in_young(const void* p) const override;
};

#endif // SHARE_GC_SERIAL_CARDTABLERS_HPP
