/*
 * Copyright (c) 2001, 2019, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_PARALLEL_PSCARDTABLE_HPP
#define SHARE_GC_PARALLEL_PSCARDTABLE_HPP

#include "gc/shared/cardTable.hpp"
#include "oops/oop.hpp"

class MutableSpace;
class ObjectStartArray;
class PSPromotionManager;

class PSCardTable: public CardTable {
 private:
  static constexpr size_t num_cards_in_stripe = 128;
  static_assert(num_cards_in_stripe >= 1, "progress");

  class StripeShadowTable {
    CardValue _table[num_cards_in_stripe];
    const CardValue* _table_base;
#ifdef ASSERT
    const CardValue* _table_end;
#endif

   public:
    StripeShadowTable(PSCardTable* pst, MemRegion stripe) :
      _table_base(_table - (uintptr_t(stripe.start()) >> _card_shift))
#ifdef ASSERT
      , _table_end((const CardValue*)(uintptr_t(_table) + (align_up(stripe.byte_size(), _card_size) >> _card_shift)))
#endif
    {
      // Old gen top is not card aligned.
      size_t copy_length = align_up(stripe.byte_size(), _card_size) >> _card_shift;
      size_t clear_length = align_down(stripe.byte_size(), _card_size) >> _card_shift;
      memcpy(_table, pst->byte_for(stripe.start()), copy_length);
      memset(pst->byte_for(stripe.start()), clean_card_val(), clear_length);
    }

    HeapWord* addr_for(const CardValue* const card) {
      assert(card >= _table && card <= _table_end, "out of bounds");
      return (HeapWord*) ((card - _table_base) << _card_shift);
    }

    const CardValue* card_for(HeapWord* addr) {
      return &_table_base[uintptr_t(addr) >> _card_shift];
    }

    bool is_dirty(const CardValue* const card) {
      return !is_clean(card);
    }

    bool is_any_dirty(const CardValue* const start, const CardValue* const end) {
      return find_first_dirty_card(start, end) != end;
    }

    bool is_clean(const CardValue* const card) {
      assert(card >= _table && card < _table_end, "out of bounds");
      return *card == PSCardTable::clean_card_val();
    }

    const CardValue* find_first_dirty_card(const CardValue* const start,
                                           const CardValue* const end) {
      for (const CardValue* i = start; i < end; ++i) {
        if (!is_clean(i)) {
          return i;
        }
      }
      return end;
    }

    const CardValue* find_first_clean_card(const CardValue* const start,
                                           const CardValue* const end) {
      for (const CardValue* i = start; i < end; ++i) {
        if (is_clean(i)) {
          return i;
        }
      }
      return end;
    }
  };

  // Pre-scavenge support.
  // The pre-scavenge phase can overlap with scavenging.
  volatile int _pre_scavenge_active_workers;

  bool is_dirty(CardValue* card) {
    return !is_clean(card);
  }

  bool is_clean(CardValue* card) {
    return *card == clean_card_val();
  }

  template <typename Func>
  void process_range(Func&& object_start,
                     PSPromotionManager* pm,
                     HeapWord* const start,
                     HeapWord* const end);

  void verify_all_young_refs_precise_helper(MemRegion mr);

  enum ExtendedCardValue {
    youngergen_card   = CT_MR_BS_last_reserved + 1,
    verify_card       = CT_MR_BS_last_reserved + 5
  };

  void scan_obj(PSPromotionManager* pm,
                oop obj);

  void scan_obj_with_limit(PSPromotionManager* pm,
                           oop obj,
                           HeapWord* start,
                           HeapWord* end);

 public:
  PSCardTable(MemRegion whole_heap) : CardTable(whole_heap),
                                      _pre_scavenge_active_workers(0) {}

  static CardValue youngergen_card_val() { return youngergen_card; }
  static CardValue verify_card_val()     { return verify_card; }

  void pre_scavenge(HeapWord* old_gen_bottom, uint active_workers);

  // Scavenge support

  // Propagate imprecise card marks from object start to the stripes an object extends to.
  template <typename Func>
  void preprocess_card_table_parallel(Func&& object_start,
                                      HeapWord* old_gen_bottom,
                                      HeapWord* old_gen_top,
                                      uint stripe_index,
                                      uint n_stripes);

  void scavenge_contents_parallel(ObjectStartArray* start_array,
                                  HeapWord* old_gen_bottom,
                                  HeapWord* old_gen_top,
                                  PSPromotionManager* pm,
                                  uint stripe_index,
                                  uint n_stripes);

  bool addr_is_marked_imprecise(void *addr);
  bool addr_is_marked_precise(void *addr);

  void set_card_newgen(void* addr)   { CardValue* p = byte_for(addr); *p = verify_card; }

  // Testers for entries
  static bool card_is_dirty(int value)      { return value == dirty_card; }
  static bool card_is_newgen(int value)     { return value == youngergen_card; }
  static bool card_is_clean(int value)      { return value == clean_card; }
  static bool card_is_verify(int value)     { return value == verify_card; }

  // Card marking
  void inline_write_ref_field_gc(void* field) {
    CardValue* byte = byte_for(field);
    *byte = youngergen_card;
  }

  // ReduceInitialCardMarks support
  bool is_in_young(const void* p) const override;

#ifdef ASSERT
  bool is_valid_card_address(CardValue* addr) {
    return (addr >= _byte_map) && (addr < _byte_map + _byte_map_size);
  }
#endif // ASSERT

  // Verification
  void verify_all_young_refs_imprecise();
  void verify_all_young_refs_precise();
};

#endif // SHARE_GC_PARALLEL_PSCARDTABLE_HPP
