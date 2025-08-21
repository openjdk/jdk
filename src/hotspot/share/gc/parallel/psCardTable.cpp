/*
 * Copyright (c) 2001, 2025, Oracle and/or its affiliates. All rights reserved.
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
#include "utilities/spinYield.hpp"

// Checks an individual oop for missing precise marks. Mark
// may be either dirty or newgen.
class PSCheckForUnmarkedOops : public BasicOopIterateClosure {
  PSYoungGen*  _young_gen;
  PSCardTable* _card_table;
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
  PSCheckForUnmarkedOops(PSYoungGen* young_gen, PSCardTable* card_table) :
    _young_gen(young_gen), _card_table(card_table), _unmarked_addr(nullptr) { }

  void do_oop(oop* p)       override { do_oop_work(p); }
  void do_oop(narrowOop* p) override { do_oop_work(p); }

  bool has_unmarked_oop() {
    return _unmarked_addr != nullptr;
  }
};

// Checks all objects for the existence of some type of mark,
// precise or imprecise, dirty or newgen.
class PSCheckForUnmarkedObjects : public ObjectClosure {
 private:
  PSYoungGen*  _young_gen;
  PSCardTable* _card_table;

 public:
  PSCheckForUnmarkedObjects() {
    ParallelScavengeHeap* heap = ParallelScavengeHeap::heap();
    _young_gen = heap->young_gen();
    _card_table = heap->card_table();
  }

  // Card marks are not precise. The current system can leave us with
  // a mismatch of precise marks and beginning of object marks. This means
  // we test for missing precise marks first. If any are found, we don't
  // fail unless the object head is also unmarked.
  virtual void do_object(oop obj) {
    PSCheckForUnmarkedOops object_check(_young_gen, _card_table);
    obj->oop_iterate(&object_check);
    if (object_check.has_unmarked_oop()) {
      guarantee(_card_table->is_dirty_for_addr(obj), "Found unmarked young_gen object");
    }
  }
};

static void prefetch_write(void *p) {
  if (PrefetchScanIntervalInBytes >= 0) {
    Prefetch::write(p, PrefetchScanIntervalInBytes);
  }
}

void PSCardTable::scan_obj_with_limit(PSPromotionManager* pm,
                                      oop obj,
                                      HeapWord* start,
                                      HeapWord* end) {
  if (!obj->is_typeArray()) {
    prefetch_write(start);
    pm->push_contents_bounded(obj, start, end);
  }
}

void PSCardTable::pre_scavenge(uint active_workers) {
  _preprocessing_active_workers = active_workers;
}

// The "shadow" table is a copy of the card table entries of the current stripe.
// It is used to separate card reading, clearing and redirtying which reduces
// complexity significantly.
class PSStripeShadowCardTable {
  typedef CardTable::CardValue CardValue;

  const uint _card_shift;
  const uint _card_size;
  CardValue _table[PSCardTable::num_cards_in_stripe];
  uintptr_t _table_base;

  // Avoid UB pointer operations by using integers internally.

  static_assert(sizeof(uintptr_t) == sizeof(CardValue*), "simplifying assumption");
  static_assert(sizeof(CardValue) == 1, "simplifying assumption");

  static uintptr_t iaddr(const void* p) {
    return reinterpret_cast<uintptr_t>(p);
  }

  uintptr_t compute_table_base(HeapWord* start) const {
    uintptr_t offset = iaddr(start) >> _card_shift;
    return iaddr(_table) - offset;
  }

  void verify_card_inclusive(const CardValue* card) const {
    assert(iaddr(card) >= iaddr(_table), "out of bounds");
    assert(iaddr(card) <= (iaddr(_table) + sizeof(_table)), "out of bounds");
  }

  void verify_card_exclusive(const CardValue* card) const {
    assert(iaddr(card) >= iaddr(_table), "out of bounds");
    assert(iaddr(card) < (iaddr(_table) + sizeof(_table)), "out of bounds");
  }

public:
  PSStripeShadowCardTable(PSCardTable* pst, HeapWord* const start, HeapWord* const end) :
    _card_shift(CardTable::card_shift()),
    _card_size(CardTable::card_size()),
    _table_base(compute_table_base(start))
  {
    size_t stripe_byte_size = pointer_delta(end, start) * HeapWordSize;
    size_t copy_length = align_up(stripe_byte_size, _card_size) >> _card_shift;
    // The end of the last stripe may not be card aligned as it is equal to old
    // gen top at scavenge start. We should not clear the card containing old gen
    // top if not card aligned because there can be promoted objects on that
    // same card. If it was marked dirty because of the promoted objects and we
    // cleared it, we would loose a card mark.
    size_t clear_length = align_down(stripe_byte_size, _card_size) >> _card_shift;
    CardValue* stripe_start_card = pst->byte_for(start);
    memcpy(_table, stripe_start_card, copy_length);
    memset(stripe_start_card, CardTable::clean_card_val(), clear_length);
  }

  HeapWord* addr_for(const CardValue* const card) {
    verify_card_inclusive(card);
    uintptr_t addr = (iaddr(card) - _table_base) << _card_shift;
    return reinterpret_cast<HeapWord*>(addr);
  }

  const CardValue* card_for(HeapWord* addr) {
    uintptr_t icard = _table_base + (iaddr(addr) >> _card_shift);
    const CardValue* card = reinterpret_cast<const CardValue*>(icard);
    verify_card_inclusive(card);
    return card;
  }

  bool is_dirty(const CardValue* const card) {
    return !is_clean(card);
  }

  bool is_clean(const CardValue* const card) {
    verify_card_exclusive(card);
    return *card == PSCardTable::clean_card_val();
  }

  const CardValue* find_first_dirty_card(const CardValue* const start,
                                         const CardValue* const end) {
    for (const CardValue* i = start; i < end; ++i) {
      if (is_dirty(i)) {
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

template <typename Func>
void PSCardTable::process_range(Func&& object_start,
                                PSPromotionManager* pm,
                                HeapWord* const start,
                                HeapWord* const end) {
  assert(start < end, "precondition");
  assert(is_card_aligned(start), "precondition");

  PSStripeShadowCardTable sct(this, start, end);

  // end might not be card-aligned.
  const CardValue* end_card = sct.card_for(end - 1) + 1;

  for (HeapWord* i_addr = start; i_addr < end; /* empty */) {
    const CardValue* dirty_l = sct.find_first_dirty_card(sct.card_for(i_addr), end_card);
    const CardValue* dirty_r = sct.find_first_clean_card(dirty_l, end_card);

    assert(dirty_l <= dirty_r, "inv");

    if (dirty_l == dirty_r) {
      assert(dirty_r == end_card, "inv");
      break;
    }

    // Located a non-empty dirty chunk [dirty_l, dirty_r).
    HeapWord* addr_l = sct.addr_for(dirty_l);
    HeapWord* addr_r = MIN2(sct.addr_for(dirty_r), end);

    // Scan objects overlapping [addr_l, addr_r) limited to [start, end).
    HeapWord* obj_addr = object_start(addr_l);

    while (true) {
      assert(obj_addr < addr_r, "inv");

      oop obj = cast_to_oop(obj_addr);
      const bool is_obj_array = obj->is_objArray();
      HeapWord* const obj_end_addr = obj_addr + obj->size();

      if (is_obj_array) {
        // Always scan obj arrays precisely (they are always marked precisely)
        // to avoid unnecessary work.
        scan_obj_with_limit(pm, obj, addr_l, addr_r);
      } else {
        if (obj_addr < i_addr && i_addr > start) {
          // Already scanned this object. Has been one that spans multiple dirty chunks.
          // The second condition makes sure objects reaching in the stripe are scanned once.
        } else {
          scan_obj_with_limit(pm, obj, addr_l, end);
        }
      }

      if (obj_end_addr >= addr_r) {
        i_addr = is_obj_array ? addr_r : obj_end_addr;
        break;
      }

      // Move to next obj inside this dirty chunk.
      obj_addr = obj_end_addr;
    }

    // Finished a dirty chunk.
    pm->drain_stacks_cond_depth();
  }
}

template <typename Func>
void PSCardTable::preprocess_card_table_parallel(Func&& object_start,
                                                 HeapWord* old_gen_bottom,
                                                 HeapWord* old_gen_top,
                                                 uint stripe_index,
                                                 uint n_stripes) {
  const size_t num_cards_in_slice = num_cards_in_stripe * n_stripes;
  CardValue* cur_card = byte_for(old_gen_bottom) + stripe_index * num_cards_in_stripe;
  CardValue* const end_card = byte_for(old_gen_top - 1) + 1;

  for (/* empty */; cur_card < end_card; cur_card += num_cards_in_slice) {
    HeapWord* stripe_addr = addr_for(cur_card);
    if (is_dirty(cur_card)) {
      // The first card of this stripe is already dirty, no need to see if the
      // reaching-in object is a potentially imprecisely marked non-array
      // object.
      continue;
    }
    HeapWord* first_obj_addr = object_start(stripe_addr);
    if (first_obj_addr == stripe_addr) {
      // No object reaching into this stripe.
      continue;
    }
    oop first_obj = cast_to_oop(first_obj_addr);
    if (!first_obj->is_array() && is_dirty(byte_for(first_obj_addr))) {
      // Found a non-array object reaching into the stripe that has
      // potentially been marked imprecisely. Mark first card of the stripe
      // dirty so it will be processed later.
      *cur_card = dirty_card_val();
    }
  }
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

// Scavenging and accesses to the card table are strictly limited to the stripe.
// In particular scavenging of an object crossing stripe boundaries is shared
// among the threads assigned to the stripes it resides on. This reduces
// complexity and enables shared scanning of large objects.
// It requires preprocessing of the card table though where imprecise card marks of
// objects crossing stripe boundaries are propagated to the first card of
// each stripe covered by the individual object.

void PSCardTable::scavenge_contents_parallel(ObjectStartArray* start_array,
                                             HeapWord* old_gen_bottom,
                                             HeapWord* old_gen_top,
                                             PSPromotionManager* pm,
                                             uint stripe_index,
                                             uint n_stripes) {
  // ObjectStartArray queries can be expensive for large objects. We cache known objects.
  struct {
    HeapWord* start_addr;
    HeapWord* end_addr;
  } cached_obj {nullptr, old_gen_bottom};

  // Queries must be monotonic because we don't check addr >= cached_obj.start_addr.
  auto object_start = [&] (HeapWord* addr) {
    if (addr < cached_obj.end_addr) {
      assert(cached_obj.start_addr != nullptr, "inv");
      return cached_obj.start_addr;
    }
    HeapWord* result = start_array->object_start(addr);

    cached_obj.start_addr = result;
    cached_obj.end_addr = result + cast_to_oop(result)->size();

    return result;
  };

  // Prepare scavenge.
  preprocess_card_table_parallel(object_start, old_gen_bottom, old_gen_top, stripe_index, n_stripes);

  // Sync with other workers.
  Atomic::dec(&_preprocessing_active_workers);
  SpinYield spin_yield;
  while (Atomic::load_acquire(&_preprocessing_active_workers) > 0) {
    spin_yield.wait();
  }

  // Scavenge
  cached_obj = {nullptr, old_gen_bottom};
  const size_t stripe_size_in_words = num_cards_in_stripe * _card_size_in_words;
  const size_t slice_size_in_words = stripe_size_in_words * n_stripes;
  HeapWord* cur_addr = old_gen_bottom + stripe_index * stripe_size_in_words;
  for (/* empty */; cur_addr < old_gen_top; cur_addr += slice_size_in_words) {
    HeapWord* const stripe_l = cur_addr;
    HeapWord* const stripe_r = MIN2(cur_addr + stripe_size_in_words,
                                    old_gen_top);

    process_range(object_start, pm, stripe_l, stripe_r);
  }
}

// This should be called before a scavenge.
void PSCardTable::verify_all_young_refs_imprecise() {
  PSCheckForUnmarkedObjects check;

  ParallelScavengeHeap* heap = ParallelScavengeHeap::heap();
  PSOldGen* old_gen = heap->old_gen();

  old_gen->object_iterate(&check);
}

bool PSCardTable::is_dirty_for_addr(void *addr) {
  CardValue* p = byte_for(addr);
  return is_dirty(p);
}

bool PSCardTable::is_in_young(const void* p) const {
  return ParallelScavengeHeap::heap()->is_in_young(p);
}
