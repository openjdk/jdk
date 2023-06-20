/*
 * Copyright (c) 2001, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/parallel/objectStartArray.inline.hpp"
#include "gc/parallel/parallelScavengeHeap.inline.hpp"
#include "gc/parallel/psCardTable.hpp"
#include "gc/parallel/psPromotionManager.inline.hpp"
#include "gc/parallel/psScavenge.inline.hpp"
#include "gc/parallel/psYoungGen.hpp"
#include "memory/iterator.inline.hpp"
#include "oops/access.inline.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/prefetch.inline.hpp"
#include "utilities/align.hpp"

// Checks an individual oop for missing precise marks. Mark
// may be either dirty or newgen.
class CheckForUnmarkedOops : public BasicOopIterateClosure {
 private:
  PSYoungGen*  _young_gen;
  PSCardTable* _card_table;
  HeapWord*    _unmarked_addr;

 protected:
  template <class T> void do_oop_work(T* p) {
    oop obj = RawAccess<>::oop_load(p);
    if (_young_gen->is_in_reserved(obj) &&
        !_card_table->addr_is_marked_imprecise(p)) {
      // Don't overwrite the first missing card mark
      if (_unmarked_addr == nullptr) {
        _unmarked_addr = (HeapWord*)p;
      }
    }
  }

 public:
  CheckForUnmarkedOops(PSYoungGen* young_gen, PSCardTable* card_table) :
    _young_gen(young_gen), _card_table(card_table), _unmarked_addr(nullptr) { }

  virtual void do_oop(oop* p)       { CheckForUnmarkedOops::do_oop_work(p); }
  virtual void do_oop(narrowOop* p) { CheckForUnmarkedOops::do_oop_work(p); }

  bool has_unmarked_oop() {
    return _unmarked_addr != nullptr;
  }
};

// Checks all objects for the existence of some type of mark,
// precise or imprecise, dirty or newgen.
class CheckForUnmarkedObjects : public ObjectClosure {
 private:
  PSYoungGen*  _young_gen;
  PSCardTable* _card_table;

 public:
  CheckForUnmarkedObjects() {
    ParallelScavengeHeap* heap = ParallelScavengeHeap::heap();
    _young_gen = heap->young_gen();
    _card_table = heap->card_table();
  }

  // Card marks are not precise. The current system can leave us with
  // a mismatch of precise marks and beginning of object marks. This means
  // we test for missing precise marks first. If any are found, we don't
  // fail unless the object head is also unmarked.
  virtual void do_object(oop obj) {
    CheckForUnmarkedOops object_check(_young_gen, _card_table);
    obj->oop_iterate(&object_check);
    if (object_check.has_unmarked_oop()) {
      guarantee(_card_table->addr_is_marked_imprecise(obj), "Found unmarked young_gen object");
    }
  }
};

// Checks for precise marking of oops as newgen.
class CheckForPreciseMarks : public BasicOopIterateClosure {
 private:
  PSYoungGen*  _young_gen;
  PSCardTable* _card_table;

 protected:
  template <class T> void do_oop_work(T* p) {
    oop obj = RawAccess<IS_NOT_NULL>::oop_load(p);
    if (_young_gen->is_in_reserved(obj)) {
      assert(_card_table->addr_is_marked_precise(p), "Found unmarked precise oop");
      _card_table->set_card_newgen(p);
    }
  }

 public:
  CheckForPreciseMarks(PSYoungGen* young_gen, PSCardTable* card_table) :
    _young_gen(young_gen), _card_table(card_table) { }

  virtual void do_oop(oop* p)       { CheckForPreciseMarks::do_oop_work(p); }
  virtual void do_oop(narrowOop* p) { CheckForPreciseMarks::do_oop_work(p); }
};

static void prefetch_write(void *p) {
  if (PrefetchScanIntervalInBytes >= 0) {
    Prefetch::write(p, PrefetchScanIntervalInBytes);
  }
}

// postcondition: ret is a dirty card or end_card
CardTable::CardValue* PSCardTable::find_first_dirty_card(CardValue* const start_card,
                                                         CardValue* const end_card) {
  for (CardValue* i_card = start_card; i_card < end_card; ++i_card) {
    if (*i_card != PSCardTable::clean_card_val()) {
      return i_card;
    }
  }
  return end_card;
}

// postcondition: ret is a clean card or end_card
// Note: if a part of an object is on a dirty card, all cards this object
// resides on are considered dirty except for large object arrays. The card marks
// of object arrays are precise which allows scanning of just the dirty regions.
CardTable::CardValue* PSCardTable::find_first_clean_card(ObjectStartArray* const start_array,
                                                         CardValue* const start_card,
                                                         CardValue* const end_card,
                                                         objArrayOop const large_obj_array) {
  assert(start_card == end_card ||
         *start_card != PSCardTable::clean_card_val(), "precondition");
  // Skip the first dirty card.
  CardValue* i_card = start_card + 1;
  while (i_card < end_card) {
    if (*i_card != PSCardTable::clean_card_val()) {
      i_card++;
      continue;
    }
    assert(i_card - 1 >= start_card, "inv");
    assert(*(i_card - 1) != PSCardTable::clean_card_val(), "prev card must be dirty");
    // Find the final obj on the prev dirty card.
    HeapWord* obj_addr = start_array->object_start(addr_for(i_card)-1);
    if (large_obj_array == cast_to_oop(obj_addr)) {
      // we scan only dirty regions of large arrays
      assert(i_card <= end_card, "inv");
      return i_card;
    }
    HeapWord* obj_end_addr = obj_addr + cast_to_oop(obj_addr)->size();
    CardValue* final_card_by_obj = byte_for(obj_end_addr - 1);
    assert(final_card_by_obj < end_card, "inv");
    if (final_card_by_obj <= i_card) {
      return i_card;
    }
    // This final obj extends beyond i_card, check if this new card is dirty.
    if (*final_card_by_obj == PSCardTable::clean_card_val()) {
      return final_card_by_obj;
    }
    // This new card is dirty, continuing the search...
    i_card = final_card_by_obj + 1;
  }
  return end_card;
}

void PSCardTable::clear_cards(CardValue* const start, CardValue* const end) {
  for (CardValue* i_card = start; i_card < end; ++i_card) {
    *i_card = clean_card;
  }
}

void PSCardTable::scan_objects_in_range(PSPromotionManager* pm,
                                        HeapWord* start,
                                        HeapWord* end) {
  HeapWord* obj_addr = start;
  while (obj_addr < end) {
    oop obj = cast_to_oop(obj_addr);
    assert(oopDesc::is_oop(obj), "inv");
    assert(!obj->is_objArray() || obj->size() < large_obj_arr_min_words(), "inv");
    prefetch_write(obj_addr);
    pm->push_contents(obj);
    obj_addr += obj->size();
  }
  pm->drain_stacks_cond_depth();
}

// We get passed the space_top value to prevent us from traversing into
// the old_gen promotion labs, which cannot be safely parsed.

// Do not call this method if the space is empty.
// It is a waste to start tasks and get here only to
// do no work. This method is just a no-op if space_top == sp->bottom().

// The generation (old gen) is divided into slices, which are further
// subdivided into stripes, with one stripe per GC thread. The size of
// a stripe is a constant, num_cards_in_stripe.
//
//      +===============+        slice 0
//      |  stripe 0     |
//      +---------------+
//      |  stripe 1     |
//      +---------------+
//      |  stripe 2     |
//      +---------------+
//      |  stripe 3     |
//      +===============+        slice 1
//      |  stripe 0     |
//      +---------------+
//      |  stripe 1     |
//      +---------------+
//      |  stripe 2     |
//      +---------------+
//      |  stripe 3     |
//      +===============+        slice 2
//      ...
//
// In this case there are 4 threads, so 4 stripes.  A GC thread first works on
// its stripe within slice 0 and then moves to its stripe in the next slice
// until it has exceeded the top of the generation.  The distance to stripe in
// the next slice is calculated based on the number of stripes. After finishing
// stripe 0 in slice 0, the thread finds the stripe 0 in slice 1 by adding
// slice_size_in_words to the start of stripe 0 in slice 0 to get to the start
// of stripe 0 in slice 1.
//
// Parallel scanning of large arrays is also based on stripes with the exception
// of the last 2 stripes: the chunks defined by them are scanned together.

void PSCardTable::scavenge_contents_parallel(ObjectStartArray* start_array,
                                             MutableSpace* sp,
                                             HeapWord* space_top,
                                             PSPromotionManager* pm,
                                             uint stripe_index,
                                             uint n_stripes) {
  const size_t stripe_size_in_words = num_cards_in_stripe * _card_size_in_words;
  const size_t slice_size_in_words = stripe_size_in_words * n_stripes;

  HeapWord* cur_stripe_addr = sp->bottom() + stripe_index * stripe_size_in_words;

  for (/* empty */; cur_stripe_addr < space_top; cur_stripe_addr += slice_size_in_words) {
    // exclusive
    HeapWord* const cur_stripe_end_addr = MIN2(cur_stripe_addr + stripe_size_in_words,
                                               space_top);

    // Process a stripe iff it contains any obj-start or large array chunk
    if (!start_array->object_starts_in_range(cur_stripe_addr, cur_stripe_end_addr)) {
      scavenge_large_array_stripe(start_array, pm, cur_stripe_addr, cur_stripe_end_addr, space_top);
      continue;
    }

    // Constraints:
    // 1. range of cards checked for being dirty or clean: [iter_limit_l, iter_limit_r)
    // 2. range of cards can be cleared: [clear_limit_l, clear_limit_r)
    // 3. range of objs (obj-start) can be scanned: [first_obj_addr, cur_stripe_end_addr)

    CardValue* iter_limit_l;
    CardValue* iter_limit_r;
    CardValue* clear_limit_l;
    CardValue* clear_limit_r;
    objArrayOop large_arr = nullptr;

    // Identify left ends and the first obj-start inside this stripe.
    HeapWord* first_obj_addr = start_array->object_start(cur_stripe_addr);
    if (first_obj_addr < cur_stripe_addr) {
      // this obj belongs to previous stripe; can't clear any cards it occupies
      first_obj_addr += cast_to_oop(first_obj_addr)->size();
      clear_limit_l = byte_for(first_obj_addr - 1) + 1;
      iter_limit_l = byte_for(first_obj_addr);
    } else {
      assert(first_obj_addr == cur_stripe_addr, "inv");
      iter_limit_l = clear_limit_l = byte_for(cur_stripe_addr);
    }

    assert(cur_stripe_addr <= first_obj_addr, "inside this stripe");
    assert(first_obj_addr <= cur_stripe_end_addr, "can be empty");

    {
      // Identify right ends.
      HeapWord* obj_addr = start_array->object_start(cur_stripe_end_addr - 1);
      size_t obj_sz = cast_to_oop(obj_addr)->size();
      HeapWord* obj_end_addr = obj_addr + obj_sz;
      // large arrays starting in this stripe are scanned here
      if (obj_sz >= large_obj_arr_min_words() && cast_to_oop(obj_addr)->is_objArray() &&
          obj_addr >= cur_stripe_addr) {
        // the last condition is not redundant as we can reach here if an obj starts at space_top
        obj_end_addr = cur_stripe_end_addr;
        large_arr = objArrayOop(cast_to_oop(obj_addr));
      }
      assert(obj_end_addr >= cur_stripe_end_addr, "inv");
      clear_limit_r = byte_for(obj_end_addr);
      iter_limit_r = byte_for(obj_end_addr - 1) + 1;
    }

    assert(iter_limit_l <= clear_limit_l &&
           clear_limit_r <= iter_limit_r, "clear cards only if we iterate over them");

    // Process dirty chunks, i.e. consecutive dirty cards [dirty_l, dirty_r),
    // chunk by chunk inside [iter_limit_l, iter_limit_r).
    CardValue* dirty_l;
    CardValue* dirty_r;

    for (CardValue* cur_card = iter_limit_l; cur_card < iter_limit_r; cur_card = dirty_r + 1) {
      dirty_l = find_first_dirty_card(cur_card, iter_limit_r);
      dirty_r = find_first_clean_card(start_array, dirty_l, iter_limit_r, large_arr);
      assert(dirty_l <= dirty_r, "inv");

      // empty
      if (dirty_l == dirty_r) {
        assert(dirty_r == iter_limit_r, "no more dirty cards in this stripe");
        break;
      }

      assert(*dirty_l != clean_card, "inv");
      assert(*dirty_r == clean_card || dirty_r >= clear_limit_r,
             "clean card or belonging to next stripe");

      // Process this non-empty dirty chunk in three steps:
      {
        // 1. Clear card in [dirty_l, dirty_r) subject to [clear_limit_l, clear_limit_r) constraint
        clear_cards(MAX2(dirty_l, clear_limit_l),
                    MIN2(dirty_r, clear_limit_r));
      }

      {
        // 2. Scan objs in [dirty_l, dirty_r) subject to [first_obj_addr, cur_stripe_end_addr) constraint
        //    Exclude the large array if one begins in the stripe
        HeapWord* obj_l = MAX2(start_array->object_start(addr_for(dirty_l)),
                               first_obj_addr);

        HeapWord* obj_r = MIN2(addr_for(dirty_r),
                               large_arr != nullptr ?
                                   cast_from_oop<HeapWord*>(large_arr) :
                                   cur_stripe_end_addr);

        scan_objects_in_range(pm, obj_l, obj_r);
      }

      if (large_arr != nullptr && addr_for(dirty_r) >= cast_from_oop<HeapWord*>(large_arr)) {
        // 3. Scan the large array elements in [dirty_l, dirty_r) subject to [large_arr, cur_stripe_end_addr)
        HeapWord* arr_l = addr_for(dirty_l);

        HeapWord* arr_r = MIN2(addr_for(dirty_r),
                               cur_stripe_end_addr);

        pm->push_array_region(large_arr, arr_l, arr_r);
      }
    }
  }
}

// Partially scan a large object array in the given stripe.
// Scan to end if it is in the next stripe.
void PSCardTable::scavenge_large_array_stripe(ObjectStartArray* start_array,
                                              PSPromotionManager* pm,
                                              HeapWord* stripe_addr,
                                              HeapWord* stripe_end_addr,
                                              HeapWord* space_top) {
  const size_t stripe_size_in_words = num_cards_in_stripe * _card_size_in_words;

  HeapWord* large_arr_addr = start_array->object_start(stripe_addr);

  size_t arr_sz;
  if (large_arr_addr == nullptr ||
      !cast_to_oop(large_arr_addr)->is_objArray() ||
      (arr_sz = cast_to_oop(large_arr_addr)->size()) < large_obj_arr_min_words())
    return;

  objArrayOop large_arr = objArrayOop(cast_to_oop(large_arr_addr));
  HeapWord* arr_end_addr = large_arr_addr + arr_sz;

  if (arr_end_addr <= stripe_end_addr) {
    // the end chunk is scanned together with the chunk in the previous stripe
    assert(large_obj_arr_min_words() > 2 * stripe_size_in_words, "2nd last chunk must cover stripe");
    return;
  }

  CardValue* iter_limit_l = byte_for(stripe_addr);
  CardValue* iter_limit_r = byte_for(stripe_end_addr - 1) + 1;
  CardValue* clear_limit_l = byte_for(stripe_addr);
  CardValue* clear_limit_r = byte_for(stripe_end_addr);

  HeapWord* scan_limit_r = stripe_end_addr;
  HeapWord* next_stripe = stripe_end_addr;
  HeapWord* next_stripe_end = MIN2(next_stripe + stripe_size_in_words, space_top);

  // scan to end if it is in the following stripe
  if (arr_end_addr > next_stripe && arr_end_addr <= next_stripe_end) {
    clear_limit_r = byte_for(arr_end_addr);
    iter_limit_r = byte_for(arr_end_addr - 1) + 1;
    scan_limit_r = arr_end_addr;
  }

  // Process dirty chunks, i.e. consecutive dirty cards [dirty_l, dirty_r),
  // chunk by chunk inside [iter_limit_l, iter_limit_r).
  CardValue* dirty_l;
  CardValue* dirty_r;

  for (CardValue* cur_card = iter_limit_l; cur_card < iter_limit_r; cur_card = dirty_r + 1) {
    dirty_l = find_first_dirty_card(cur_card, iter_limit_r);
    dirty_r = find_first_clean_card(start_array, dirty_l, iter_limit_r, large_arr);
    assert(dirty_l <= dirty_r, "inv");

    // empty
    if (dirty_l == dirty_r) {
      assert(dirty_r == iter_limit_r, "no more dirty cards in this stripe");
      break;
    }

    assert(*dirty_l != clean_card, "inv");
    assert(*dirty_r == clean_card || dirty_r >= clear_limit_r,
           "clean card or belonging to next stripe");

    // Process this non-empty dirty chunk in two steps:
    {
      // 1. Clear card in [dirty_l, dirty_r) subject to [clear_limit_l, clear_limit_r) constraint
      clear_cards(MAX2(dirty_l, clear_limit_l),
                  MIN2(dirty_r, clear_limit_r));
    }

    {
      // 2. Scan elements in [dirty_l, dirty_r)
      HeapWord* left = addr_for(dirty_l);
      HeapWord* right = MIN2(addr_for(dirty_r), scan_limit_r);
      pm->push_array_region(large_arr, left, right);
    }
  }
}

// This should be called before a scavenge.
void PSCardTable::verify_all_young_refs_imprecise() {
  CheckForUnmarkedObjects check;

  ParallelScavengeHeap* heap = ParallelScavengeHeap::heap();
  PSOldGen* old_gen = heap->old_gen();

  old_gen->object_iterate(&check);
}

// This should be called immediately after a scavenge, before mutators resume.
void PSCardTable::verify_all_young_refs_precise() {
  ParallelScavengeHeap* heap = ParallelScavengeHeap::heap();
  PSOldGen* old_gen = heap->old_gen();

  CheckForPreciseMarks check(heap->young_gen(), this);

  old_gen->oop_iterate(&check);

  verify_all_young_refs_precise_helper(old_gen->object_space()->used_region());
}

void PSCardTable::verify_all_young_refs_precise_helper(MemRegion mr) {
  CardValue* bot = byte_for(mr.start());
  CardValue* top = byte_for(mr.end());
  while (bot <= top) {
    assert(*bot == clean_card || *bot == verify_card, "Found unwanted or unknown card mark");
    if (*bot == verify_card)
      *bot = youngergen_card;
    bot++;
  }
}

bool PSCardTable::addr_is_marked_imprecise(void *addr) {
  CardValue* p = byte_for(addr);
  CardValue val = *p;

  if (card_is_dirty(val))
    return true;

  if (card_is_newgen(val))
    return true;

  if (card_is_clean(val))
    return false;

  assert(false, "Found unhandled card mark type");

  return false;
}

// Also includes verify_card
bool PSCardTable::addr_is_marked_precise(void *addr) {
  CardValue* p = byte_for(addr);
  CardValue val = *p;

  if (card_is_newgen(val))
    return true;

  if (card_is_verify(val))
    return true;

  if (card_is_clean(val))
    return false;

  if (card_is_dirty(val))
    return false;

  assert(false, "Found unhandled card mark type");

  return false;
}

bool PSCardTable::is_in_young(const void* p) const {
  return ParallelScavengeHeap::heap()->is_in_young(p);
}
