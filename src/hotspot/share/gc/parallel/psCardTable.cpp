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

void PSCardTable::pre_scavenge(HeapWord* old_gen_bottom, uint active_workers) {
  _pre_scavenge_active_workers = active_workers;
  _pre_scavenge_current_goal_active_workers = active_workers;
  _pre_scavenge_current_goal = old_gen_bottom + _pre_scavenge_sync_interval;
  _pre_scavenge_completed_top = nullptr;
}

// Scavenge objects on dirty cards of the given stripe [start, end). Accesses to
// the card table and scavenging is strictly limited to the stripe. The work on
// objects covering multiple stripes is shared among the worker threads owning the
// stripes.  To support this the card table is preprocessed before
// scavenge. Imprecise dirty marks of non-objArrays are copied from start stripes
// to all stripes (if any) they extend to.
// A copy of card table entries corresponding to the stripe called "shadow" table
// is used to separate card reading, clearing and redirtying.
template <typename Func>
void PSCardTable::process_range(Func&& object_start,
                                PSPromotionManager* pm,
                                HeapWord* const start,
                                HeapWord* const end) {
  assert(start < end, "precondition");
  assert(is_card_aligned(start), "precondition");

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

  StripeShadowTable sct(this, MemRegion(start, end));

  // end might not be card-aligned
  const CardValue* end_card = sct.card_for(end - 1) + 1;

  for (HeapWord* i_addr = start; i_addr < end; /* empty */) {
    const CardValue* dirty_l = sct.find_first_dirty_card(sct.card_for(i_addr), end_card);
    const CardValue* dirty_r = sct.find_first_clean_card(dirty_l, end_card);

    assert(dirty_l <= dirty_r, "inv");

    if (dirty_l == dirty_r) {
      assert(dirty_r == end_card, "inv");
      break;
    }

    // Located a non-empty dirty chunk [dirty_l, dirty_r)
    HeapWord* addr_l = sct.addr_for(dirty_l);
    HeapWord* addr_r = MIN2(sct.addr_for(dirty_r), end);

    // Scan objects overlapping [addr_l, addr_r) limited to [start, end)
    Obj obj(object_start(addr_l));

    while (true) {
      assert(obj.addr < addr_r, "inv");

      if (obj.is_obj_array) {
        // precise-marked
        scan_obj_with_limit(pm, obj.obj, addr_l, addr_r);
      } else {
        if (obj.addr < addr_l) {
          if (sct.is_any_dirty(sct.card_for(MAX2(obj.addr, start)), dirty_l)) {
            // already scanned
          } else {
            // treat it as semi-precise-marked, [addr_l, obj-end)
            scan_obj_with_limit(pm, obj.obj, addr_l, MIN2(obj.end_addr, end));
          }
        } else {
          // obj-start is dirty
          if (obj.end_addr <= end) {
            // scan whole obj if it does not extend beyond the stripe end
            scan_obj(pm, obj.obj);
          } else {
            // otherwise scan limit the scan
            scan_obj_with_limit(pm, obj.obj, addr_l, end);
          }
        }
      }

      if (obj.end_addr >= addr_r) {
        i_addr = obj.is_obj_array ? addr_r : obj.end_addr;
        break;
      }

      // move to next obj inside this dirty chunk
      obj.next();
    }

    // Finished a dirty chunk
    pm->drain_stacks_cond_depth();
  }
}

// Propagate imprecise card marks from object start to the stripes an object extends to.
// Pre-scavenging and scavenging can overlap.
template <typename Func>
void PSCardTable::pre_scavenge_parallel(Func&& object_start,
                                        HeapWord* old_gen_bottom,
                                        HeapWord* old_gen_top,
                                        uint stripe_index,
                                        uint n_stripes) {
  const uint active_workers = n_stripes;
  const size_t num_cards_in_slice = num_cards_in_stripe * n_stripes;
  CardValue* cur_card = byte_for(old_gen_bottom) + stripe_index * num_cards_in_stripe;
  CardValue* const end_card = byte_for(old_gen_top - 1) + 1;
  HeapWord* signaled_goal = nullptr;

  for ( /* empty */ ; cur_card < end_card; cur_card += num_cards_in_slice) {
    HeapWord* stripe_addr = addr_for(cur_card);
    if (!is_dirty(cur_card)) {
      HeapWord* first_obj_addr = object_start(stripe_addr);
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
        _pre_scavenge_current_goal_active_workers = active_workers;
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
  struct {
    HeapWord* start_addr;
    HeapWord* end_addr;
    DEBUG_ONLY(HeapWord* _prev_query);
  } cached_obj {nullptr, old_gen_bottom DEBUG_ONLY(COMMA nullptr)};

  auto object_start = [&] (HeapWord* addr) {
    assert(cached_obj._prev_query <= addr, "precondition");
    DEBUG_ONLY(cached_obj._prev_query = addr);
    if (addr < cached_obj.end_addr) {
      assert(cached_obj.start_addr != nullptr, "inv");
      return cached_obj.start_addr;
    }
    HeapWord* result = start_array->object_start(addr);

    cached_obj.start_addr = result;
    cached_obj.end_addr = result + cast_to_oop(result)->size();

    return result;
  };

  const size_t stripe_size_in_words = num_cards_in_stripe * _card_size_in_words;
  const size_t slice_size_in_words = stripe_size_in_words * n_stripes;

  // Prepare scavenge
  pre_scavenge_parallel(object_start, old_gen_bottom, old_gen_top, stripe_index, n_stripes);

  // Reset cached object
  cached_obj = {nullptr, old_gen_bottom DEBUG_ONLY(COMMA nullptr)};

  // Scavenge
  HeapWord* cur_stripe_addr = old_gen_bottom + stripe_index * stripe_size_in_words;
  bool pre_scavenge_complete = false;
  for (/* empty */; cur_stripe_addr < old_gen_top; cur_stripe_addr += slice_size_in_words) {
    HeapWord* const stripe_l = cur_stripe_addr;
    HeapWord* const stripe_r = MIN2(cur_stripe_addr + stripe_size_in_words,
                                    old_gen_top);

    // Sync with concurrent pre-scavenge.
    if (!pre_scavenge_complete) {
      SpinYield spin;
      while (Atomic::load_acquire(&_pre_scavenge_active_workers) != 0 &&
             cur_stripe_addr > Atomic::load_acquire(&_pre_scavenge_completed_top)) {
        spin.wait();
      }
      pre_scavenge_complete = Atomic::load_acquire(&_pre_scavenge_active_workers) == 0;
    }

    process_range(object_start, pm, stripe_l, stripe_r);
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
