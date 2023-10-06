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
#include "utilities/spinYield.hpp"
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
    if (is_dirty(i_card)) {
      return i_card;
    }
  }
  return end_card;
}

// postcondition: ret is a clean card or end_card
// Note: if a part of an object is on a dirty card, all cards this object
// resides on are considered dirty.
template <typename T>
CardTable::CardValue* PSCardTable::find_first_clean_card(T start_cache,
                                                         CardValue* const start_card,
                                                         CardValue* const end_card) {
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
    HeapWord* obj_addr = start_cache.object_start(addr_for(i_card)-1);
    if (cast_to_oop(obj_addr)->is_objArray()) {
      // Object arrays are precisely marked.
      return i_card;
    }
    HeapWord* obj_end_addr = obj_addr + cast_to_oop(obj_addr)->size();
    CardValue* final_card_by_obj = byte_for(obj_end_addr - 1);
    if (final_card_by_obj <= i_card || final_card_by_obj >= end_card) {
      return i_card;
    }
    // This final obj extends beyond i_card but not beyond end_card.
    // Check if this new card is dirty.
    if (*final_card_by_obj == PSCardTable::clean_card_val()) {
      return final_card_by_obj;
    }
    // This new card is dirty, continuing the search...
    i_card = final_card_by_obj + 1;
  }
  return end_card;
}

void PSCardTable::scan_obj(PSPromotionManager* pm,
                           oop obj) {
  prefetch_write(obj);
  pm->push_contents(obj);
}

void PSCardTable::scan_obj_with_limit(PSPromotionManager* pm,
                                      oop obj,
                                      HeapWord* start,
                                      HeapWord* end) {
  prefetch_write(start);
  pm->push_contents_bounded(obj, start, end);
}

// ObjectStartArray queries can get expensive if the start is far.  The
// information can be cached if iterating monotonically from lower to higher
// addresses. This is vital with parallel processing of large objects.
class ObjStartCache : public StackObj {
    HeapWord* _obj_start;
    HeapWord* _obj_end;
    ObjectStartArray* _start_array;
    DEBUG_ONLY(HeapWord* _prev_query);
  public:
    ObjStartCache(ObjectStartArray* start_array) : _obj_start(nullptr), _obj_end(nullptr),
                                                   _start_array(start_array)
                                                   DEBUG_ONLY(COMMA _prev_query(nullptr)) {}
    HeapWord* object_start(HeapWord* addr) {
      assert(_prev_query <= addr, "precondition");
      DEBUG_ONLY(_prev_query = addr);
      if (addr >= _obj_end) {
        _obj_start = _start_array->object_start(addr);
        _obj_end = _obj_start + cast_to_oop(_obj_start)->size();
      }
      assert(_obj_start != nullptr, "postcondition");
      return _obj_start;
    }
};

void PSCardTable::pre_scavenge(HeapWord* old_gen_bottom, uint active_workers) {
  _pre_scavenge_active_workers = active_workers;
  _pre_scavenge_current_goal_active_workers = active_workers;
  _pre_scavenge_current_goal = old_gen_bottom + _pre_scavenge_sync_interval;
  _pre_scavenge_completed_top = nullptr;
}

void PSCardTable::clear_cards(CardValue* const start, CardValue* const end) {
  for (CardValue* i_card = start; i_card < end; ++i_card) {
    *i_card = clean_card_val();
  }
}

// Find cards within [start, end) marked dirty, clear corresponding parts of the
// card table and scan objects on dirty cards.
// Scanning of objects is limited to [start, end) of a stripe. This way the
// scanning of objects crossing stripe boundaries is distributed. For this the
// dirty marks of imprecisely marked non-array objects are propagated
// pre-scavenge to the stripes they extend to.
// Except for the limitation to the [start, end) stripe non-array objects are scanned completely.
// Object arrays are marked precisely. Therefore the scanning is limited to dirty cards.
template <typename T>
void PSCardTable::process_range(T& start_cache,
                                PSPromotionManager* pm,
                                HeapWord* const start,
                                HeapWord* const end) {
  assert(start < end, "precondition");
  assert(is_card_aligned(start), "precondition");

  // end might not be card-aligned
  CardValue* itr_limit_r = byte_for(end - 1) + 1;
  CardValue* clr_limit_r = byte_for(end);

  CardValue* dirty_l;
  CardValue* dirty_r;

  // Helper struct to keep the following code compact.
  struct Obj {
    HeapWord* addr;
    oop obj;
    bool is_obj_array;
    HeapWord* end_addr;
    Obj(HeapWord* o_addr) : addr(o_addr),
                            obj(cast_to_oop(o_addr)),
                            is_obj_array(obj->is_objArray()),
                            end_addr(addr + obj->size()) {}
    void next() {
      addr = end_addr;
      obj = cast_to_oop(addr);
      is_obj_array = obj->is_objArray();
      end_addr = addr + obj->size();
    }
  };

  for (CardValue* cur_card = byte_for(start); cur_card < itr_limit_r; cur_card = dirty_r + 1) {
    dirty_l = find_first_dirty_card(cur_card, itr_limit_r);
    dirty_r = find_first_clean_card(start_cache, dirty_l, itr_limit_r);

    assert(dirty_l <= dirty_r, "inv");

    if (dirty_l == dirty_r) {
      assert(dirty_r == itr_limit_r, "inv");
      break;
    }

    // Located a non-empty dirty chunk [dirty_l, dirty_r)
    HeapWord* addr_l = addr_for(dirty_l);
    HeapWord* addr_r = MIN2(addr_for(dirty_r), end);

    // Clear the cards before scanning.
    clear_cards(dirty_l, MIN2(dirty_r, clr_limit_r));

    // Scan objects overlapping [addr_l, addr_r) limited to [start, end)
    Obj obj(start_cache.object_start(addr_l));

    // Scan non-objArray reaching into stripe and into [addr_l, addr_r).
    if (!obj.is_obj_array && obj.addr < start) {
      scan_obj_with_limit(pm, obj.obj, start, MIN2(obj.end_addr, end));
      if (obj.end_addr >= addr_r) {
        // Last object in [addr_l, addr_r)
        continue;
      }
      // move to next obj inside this dirty chunk
      obj.next();
    }

    // Scan objects overlapping [addr_l, addr_r).
    // Non-objArrays are known to start within the stripe. They are scanned completely.
    // Scanning of objArrays is limited to the dirty chunk [addr_l, addr_r).
    while (obj.end_addr < addr_r) {
      if (obj.is_obj_array) {
        // precisely marked
        scan_obj_with_limit(pm, obj.obj, addr_l, addr_r);
      } else {
        assert(obj.addr >= start, "handled before");
        // scan whole obj
        scan_obj(pm, obj.obj);
      }

      // move to next obj
      obj.next();
    }

    // Scan object that extends beyond [addr_l, addr_r) and maybe even beyond the stripe.
    assert(obj.addr < addr_r, "inv");
    if (obj.is_obj_array) {
      // precise-marked
      scan_obj_with_limit(pm, obj.obj, addr_l, addr_r);
    } else {
      assert(obj.addr >= start, "handled before");
      scan_obj_with_limit(pm, obj.obj, obj.addr, MIN2(obj.end_addr, end));
    }

    // Finished a dirty chunk
    pm->drain_stacks_cond_depth();
  }
}

// Propagate imprecise card marks from object start to the stripes an object extends to.
// Pre-scavenging and scavenging can overlap.
void PSCardTable::pre_scavenge_parallel(ObjectStartArray* start_array,
                                                 HeapWord* old_gen_bottom,
                                                 HeapWord* old_gen_top,
                                                 uint stripe_index,
                                                 uint n_stripes) {
  const uint active_workers = n_stripes;
  const size_t num_cards_in_slice = num_cards_in_stripe * n_stripes;
  CardValue* cur_card = byte_for(old_gen_bottom) + stripe_index * num_cards_in_stripe;
  CardValue* const end_card = byte_for(old_gen_top - 1) + 1;
  HeapWord* signaled_goal = nullptr;
  ObjStartCache start_cache(start_array);

  for ( /* empty */ ; cur_card < end_card; cur_card += num_cards_in_slice) {
    HeapWord* stripe_addr = addr_for(cur_card);
    if (!is_dirty(cur_card)) {
      HeapWord* first_obj_addr = start_cache.object_start(stripe_addr);
      if (first_obj_addr < stripe_addr) {
        oop first_obj = cast_to_oop(first_obj_addr);
        if (!first_obj->is_array() && is_dirty(byte_for(first_obj_addr))) {
          // Potentially imprecisely marked dirty.
          // Mark first card of stripe dirty too.
          *cur_card = dirty_card_val();
        }
      }
    }
    // Synchronization with already scavenging threads.
    if (signaled_goal < _pre_scavenge_current_goal && _pre_scavenge_current_goal <= stripe_addr) {
      signaled_goal = (HeapWord*) _pre_scavenge_current_goal;
      Atomic::dec(&_pre_scavenge_current_goal_active_workers);
      if (_pre_scavenge_current_goal_active_workers == 0) {
        // We're the last one to reach the current goal.
        // Set completed top.
        _pre_scavenge_completed_top = _pre_scavenge_current_goal;
        // Set next goal.
        _pre_scavenge_current_goal_active_workers = n_stripes;
        Atomic::add(&_pre_scavenge_current_goal, _pre_scavenge_sync_interval);
      }
    }
  }
  Atomic::dec(&_pre_scavenge_active_workers);
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

void PSCardTable::scavenge_contents_parallel(ObjectStartArray* start_array,
                                             HeapWord* old_gen_bottom,
                                             HeapWord* old_gen_top,
                                             PSPromotionManager* pm,
                                             uint stripe_index,
                                             uint n_stripes) {
  const size_t stripe_size_in_words = num_cards_in_stripe * _card_size_in_words;
  const size_t slice_size_in_words = stripe_size_in_words * n_stripes;

  // Prepare scavenge
  pre_scavenge_parallel(start_array, old_gen_bottom, old_gen_top, stripe_index, n_stripes);

  // Scavenge
  HeapWord* cur_stripe_addr = old_gen_bottom + stripe_index * stripe_size_in_words;
  ObjStartCache start_cache(start_array);
  for (/* empty */; cur_stripe_addr < old_gen_top; cur_stripe_addr += slice_size_in_words) {
    HeapWord* const stripe_l = cur_stripe_addr;
    HeapWord* const stripe_r = MIN2(cur_stripe_addr + stripe_size_in_words,
                                    old_gen_top);

    // Sync with concurrent pre-scavenge.
    SpinYield spin;
    while (_pre_scavenge_active_workers != 0 && cur_stripe_addr > _pre_scavenge_completed_top) {
      spin.wait();
    }

    process_range(start_cache, pm, stripe_l, stripe_r);
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
