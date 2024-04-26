/*
 * Copyright (c) 2001, 2024, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "classfile/classLoaderDataGraph.hpp"
#include "gc/serial/cardTableRS.hpp"
#include "gc/serial/generation.hpp"
#include "gc/serial/serialHeap.inline.hpp"
#include "gc/shared/space.hpp"
#include "memory/iterator.inline.hpp"
#include "utilities/align.hpp"

void CardTableRS::scan_old_to_young_refs(TenuredGeneration* tg, HeapWord* saved_top) {
  const MemRegion ur    = tg->used_region();
  const MemRegion urasm = MemRegion(tg->space()->bottom(), saved_top);

  assert(ur.contains(urasm),
         "[" PTR_FORMAT ", " PTR_FORMAT ") is not contained in "
         "[" PTR_FORMAT ", " PTR_FORMAT ")",
         p2i(urasm.start()), p2i(urasm.end()), p2i(ur.start()), p2i(ur.end()));

  if (!urasm.is_empty()) {
    OldGenScanClosure cl(SerialHeap::heap()->young_gen());
    non_clean_card_iterate(tg, urasm, &cl);
  }
}

void CardTableRS::maintain_old_to_young_invariant(TenuredGeneration* old_gen,
                                                  bool is_young_gen_empty) {
  if (is_young_gen_empty) {
    clear_MemRegion(old_gen->prev_used_region());
  } else {
    MemRegion used_mr = old_gen->used_region();
    MemRegion prev_used_mr = old_gen->prev_used_region();
    if (used_mr.end() < prev_used_mr.end()) {
      // Shrunk; need to clear the previously-used but now-unused parts.
      clear_MemRegion(MemRegion(used_mr.end(), prev_used_mr.end()));
    }
    // No idea which card contains old-to-young pointer, so dirtying cards for
    // the entire used part of old-gen conservatively.
    dirty_MemRegion(used_mr);
  }
}

class SerialCheckForUnmarkedOops : public BasicOopIterateClosure {
  DefNewGeneration* _young_gen;
  CardTableRS* _card_table;
  HeapWord*    _unmarked_addr;

  template <class T> void do_oop_work(T* p) {
    oop obj = RawAccess<>::oop_load(p);
    if (_young_gen->is_in_reserved(obj) &&
        !_card_table->is_dirty_for_addr(p)) {
      // Don't overwrite the first missing card mark
      if (_unmarked_addr == nullptr) {
        _unmarked_addr = (HeapWord*)p;
      }
    }
  }

 public:
  SerialCheckForUnmarkedOops(DefNewGeneration* young_gen, CardTableRS* card_table) :
    _young_gen(young_gen),
    _card_table(card_table),
    _unmarked_addr(nullptr) {}

  void do_oop(oop* p)       override { do_oop_work(p); }
  void do_oop(narrowOop* p) override { do_oop_work(p); }

  bool has_unmarked_oop() {
    return _unmarked_addr != nullptr;
  }
};

void CardTableRS::verify() {
  class CheckForUnmarkedObjects : public ObjectClosure {
    DefNewGeneration* _young_gen;
    CardTableRS* _card_table;

   public:
    CheckForUnmarkedObjects() {
      SerialHeap* heap = SerialHeap::heap();
      _young_gen = heap->young_gen();
      _card_table = heap->rem_set();
    }

    void do_object(oop obj) override {
      SerialCheckForUnmarkedOops object_check(_young_gen, _card_table);
      obj->oop_iterate(&object_check);
      // If this obj is imprecisely-marked, the card for obj-start must be dirty.
      if (object_check.has_unmarked_oop()) {
        guarantee(_card_table->is_dirty_for_addr(obj), "Found unmarked old-to-young pointer");
      }
    }
  } check;

  SerialHeap::heap()->old_gen()->object_iterate(&check);
}

CardTableRS::CardTableRS(MemRegion whole_heap) :
  CardTable(whole_heap) { }

// Implemented word-iteration to skip long consecutive clean cards.
CardTable::CardValue* CardTableRS::find_first_dirty_card(CardValue* const start_card,
                                                         CardValue* const end_card) {
  using Word = uintptr_t;

  CardValue* current_card = start_card;

  while (!is_aligned(current_card, sizeof(Word))) {
    if (current_card >= end_card) {
      return end_card;
    }
    if (is_dirty(current_card)) {
      return current_card;
    }
    ++current_card;
  }

  // Word comparison
  while (current_card + sizeof(Word) <= end_card) {
    Word* current_word = reinterpret_cast<Word*>(current_card);
    if (*current_word != (Word)clean_card_row_val()) {
      // Found a dirty card in this word; fall back to per-CardValue comparison.
      break;
    }
    current_card += sizeof(Word);
  }

  // Per-CardValue comparison.
  for (/* empty */; current_card < end_card; ++current_card) {
    if (is_dirty(current_card)) {
      return current_card;
    }
  }

  return end_card;
}

// Because non-objArray objs can be imprecisely marked (only the obj-start card
// is dirty instead of the part containing old-to-young pointers), if the
// obj-start of a non-objArray is dirty, all cards that the obj resides on,
// except the final one, are unconditionally considered as dirty. This is
// because that obj will be iterated (scanned for old-to-young pointers) as a
// whole.
template<typename Func>
CardTable::CardValue* CardTableRS::find_first_clean_card(CardValue* const start_card,
                                                         CardValue* const end_card,
                                                         Func& object_start) {
  for (CardValue* current_card = start_card; current_card < end_card; /* empty */) {
    if (is_dirty(current_card)) {
      current_card++;
      continue;
    }

    // A potential candidate.
    HeapWord* addr = addr_for(current_card);
    HeapWord* obj_start_addr = object_start(addr);

    if (obj_start_addr == addr) {
      return current_card;
    }

    // Final obj in dirty-chunk crosses card-boundary.
    oop obj = cast_to_oop(obj_start_addr);
    if (obj->is_objArray()) {
      // ObjArrays are always precisely-marked so we are not allowed to jump to
      // the end of the current object.
      return current_card;
    }

    // Final card occupied by obj.
    CardValue* obj_final_card = byte_for(obj_start_addr + obj->size() - 1);
    if (is_clean(obj_final_card)) {
      return obj_final_card;
    }

    // Continue the search after this known-dirty card...
    current_card = obj_final_card + 1;
  }

  return end_card;
}

void CardTableRS::clear_cards(CardValue* start, CardValue* end) {
  size_t num_cards = pointer_delta(end, start, sizeof(CardValue));
  memset(start, clean_card_val(), num_cards);
}

static void prefetch_write(void *p) {
  if (PrefetchScanIntervalInBytes >= 0) {
    Prefetch::write(p, PrefetchScanIntervalInBytes);
  }
}

static void scan_obj_with_limit(oop obj,
                                OldGenScanClosure* cl,
                                HeapWord* start,
                                HeapWord* end) {
  if (!obj->is_typeArray()) {
    prefetch_write(start);
    obj->oop_iterate(cl, MemRegion(start, end));
  }
}

void CardTableRS::non_clean_card_iterate(TenuredGeneration* tg,
                                         MemRegion mr,
                                         OldGenScanClosure* cl) {
  struct {
    HeapWord* start_addr;
    HeapWord* end_addr;
  } cached_obj { nullptr, mr.start() };

  auto object_start = [&] (const HeapWord* const addr) {
    if (addr < cached_obj.end_addr) {
      assert(cached_obj.start_addr != nullptr, "inv");
      return cached_obj.start_addr;
    }
    HeapWord* result = tg->block_start(addr);

    cached_obj.start_addr = result;
    cached_obj.end_addr = result + cast_to_oop(result)->size();

    return result;
  };

  CardValue* const start_card = byte_for(mr.start());
  CardValue* const end_card = byte_for(mr.last()) + 1;

  // if mr.end() is not card-aligned, that final card should not be cleared
  // because it can be annotated dirty due to old-to-young pointers in
  // newly-promoted objs on that card.
  CardValue* const clear_limit_card = is_card_aligned(mr.end()) ? end_card - 1
                                                                : end_card - 2;

  for (CardValue* current_card = start_card; current_card < end_card; /* empty */) {
    CardValue* const dirty_l = find_first_dirty_card(current_card, end_card);
    if (dirty_l == end_card) {
      // No dirty cards to iterate.
      return;
    }

    HeapWord* const addr_l = addr_for(dirty_l);
    HeapWord* obj_addr = object_start(addr_l);

    CardValue* const dirty_r = find_first_clean_card(dirty_l + 1,
                                                     end_card,
                                                     object_start);
    assert(dirty_l < dirty_r, "inv");
    HeapWord* const addr_r = dirty_r == end_card ? mr.end()
                                                 : addr_for(dirty_r);

    clear_cards(MIN2(dirty_l, clear_limit_card),
                MIN2(dirty_r, clear_limit_card));

    while (true) {
      assert(obj_addr < addr_r, "inv");

      oop obj = cast_to_oop(obj_addr);
      const bool is_obj_array = obj->is_objArray();
      HeapWord* const obj_end_addr = obj_addr + obj->size();

      if (is_obj_array) {
        // ObjArrays are always precise-marked.
        scan_obj_with_limit(obj, cl, addr_l, addr_r);
      } else {
        scan_obj_with_limit(obj, cl, addr_l, obj_end_addr);
      }

      if (obj_end_addr >= addr_r) {
        current_card = dirty_r + 1;
        break;
      }

      // Move to next obj inside this dirty chunk.
      obj_addr = obj_end_addr;
    }
  }
}

bool CardTableRS::is_in_young(const void* p) const {
  return SerialHeap::heap()->is_in_young(p);
}
