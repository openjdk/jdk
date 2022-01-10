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
      if (_unmarked_addr == NULL) {
        _unmarked_addr = (HeapWord*)p;
      }
    }
  }

 public:
  CheckForUnmarkedOops(PSYoungGen* young_gen, PSCardTable* card_table) :
    _young_gen(young_gen), _card_table(card_table), _unmarked_addr(NULL) { }

  virtual void do_oop(oop* p)       { CheckForUnmarkedOops::do_oop_work(p); }
  virtual void do_oop(narrowOop* p) { CheckForUnmarkedOops::do_oop_work(p); }

  bool has_unmarked_oop() {
    return _unmarked_addr != NULL;
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

// We get passed the space_top value to prevent us from traversing into
// the old_gen promotion labs, which cannot be safely parsed.

// Do not call this method if the space is empty.
// It is a waste to start tasks and get here only to
// do no work.  If this method needs to be called
// when the space is empty, fix the calculation of
// end_card to allow sp_top == sp->bottom().

// The generation (old gen) is divided into slices, which are further
// subdivided into stripes, with one stripe per GC thread. The size of
// a stripe is a constant, ssize.
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
// the next slice is calculated based on the number of stripes.  The next
// stripe is at ssize * number_of_stripes (= slice_stride)..  So after
// finishing stripe 0 in slice 0, the thread finds the stripe 0 in slice1 by
// adding slice_stride to the start of stripe 0 in slice 0 to get to the start
// of stride 0 in slice 1.

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
CardTable::CardValue* PSCardTable::find_first_clean_card(ObjectStartArray* const start_array,
                                                         CardValue* const start_card,
                                                         CardValue* const end_card) {
  assert(start_card == end_card ||
         *start_card != PSCardTable::clean_card_val(), "precondition");
  // skip the first dirty card
  CardValue* i_card = start_card + 1;
  while (i_card < end_card) {
    if (*i_card != PSCardTable::clean_card_val()) {
      i_card++;
      continue;
    }
    assert(i_card - 1 >= start_card, "inv");
    // prev card must be dirty
    assert(*(i_card - 1) != PSCardTable::clean_card_val(), "inv");
    // find the final obj on the prev dirty card
    HeapWord* obj_addr = start_array->object_start(addr_for(i_card)-1);
    HeapWord* obj_end_addr = obj_addr + cast_to_oop(obj_addr)->size();
    CardValue* final_card_by_obj = byte_for(obj_end_addr - 1);
    assert(final_card_by_obj < end_card, "inv");
    if (final_card_by_obj <= i_card) {
      return i_card;
    }
    if (*final_card_by_obj == PSCardTable::clean_card_val()) {
      return final_card_by_obj;
    }
    i_card = final_card_by_obj + 1;
  }
  return end_card;
}

void PSCardTable::scavenge_contents_parallel(ObjectStartArray* start_array,
                                             MutableSpace* sp,
                                             HeapWord* space_top,
                                             PSPromotionManager* pm,
                                             uint stripe_index,
                                             uint n_stripes) {
  const size_t num_cards_in_stripe = 128;
  const size_t stripe_size_in_words = num_cards_in_stripe * _card_size_in_words;
  const size_t slice_size_in_words = stripe_size_in_words * n_stripes;

  HeapWord* cur_stripe = sp->bottom() + stripe_index * stripe_size_in_words;

  for (/* empty */; cur_stripe < space_top; cur_stripe += slice_size_in_words) {
    // exclusive
    HeapWord* const cur_stripe_end = MIN2(cur_stripe + stripe_size_in_words,
                                          space_top);

    // process a stripe iff it contains any obj-start
    if (!start_array->object_starts_in_range(cur_stripe, cur_stripe_end)) {
      continue;
    }

    // Constraints:
    // 1. range of cards checked for being dirty or clean: [iter_limit_l, iter_limit_r)
    // 2. range of cards can be cleared: [clear_limit_l, clear_limit_r)
    // 3. range of objs (obj-start) can be scanned: [first_obj_addr, cur_stripe_end)

    CardValue* iter_limit_l;
    CardValue* iter_limit_r;
    CardValue* clear_limit_l;
    CardValue* clear_limit_r;

    HeapWord* first_obj_addr = start_array->object_start(cur_stripe);
    if (first_obj_addr < cur_stripe) {
      // this obj belongs to previous stripe; can't clear any cards it occupies
      first_obj_addr += cast_to_oop(first_obj_addr)->size();
      clear_limit_l = byte_for(first_obj_addr - 1) + 1;
      iter_limit_l = byte_for(first_obj_addr);
    } else {
      assert(first_obj_addr == cur_stripe, "inv");
      iter_limit_l = clear_limit_l = byte_for(cur_stripe);
    }

    assert(cur_stripe <= first_obj_addr, "inside this stripe");
    assert(first_obj_addr <= cur_stripe_end, "can be empty");

    {
      HeapWord* obj_addr = start_array->object_start(cur_stripe_end - 1);
      HeapWord* obj_end_addr = obj_addr + cast_to_oop(obj_addr)->size();
      assert(obj_end_addr >= cur_stripe_end, "inv");
      clear_limit_r = byte_for(obj_end_addr);
      iter_limit_r = byte_for(obj_end_addr - 1) + 1;
    }

    assert(iter_limit_l <= clear_limit_l, "inv");
    assert(clear_limit_r <= iter_limit_r, "inv");

    // iterate [iter_limit_l, iter_limit_r) to find consecutive dirty cards
    CardValue* cur_card = iter_limit_l;

    while (cur_card < iter_limit_r) {
      CardValue* dirty_l = find_first_dirty_card(cur_card, iter_limit_r);
      CardValue* dirty_r = find_first_clean_card(start_array, dirty_l, iter_limit_r);
      assert(dirty_l <= dirty_r, "inv");

      cur_card = dirty_r + 1;

      // empty
      if (dirty_l == dirty_r) {
        assert(dirty_r == iter_limit_r, "no more dirty cards in this stripe");
        break;
      }

      assert(*dirty_l != clean_card, "inv");
      assert(*dirty_r == clean_card || dirty_r == iter_limit_r, "inv");

      {
        // clear card in [dirty_l, dirty_r) subject to [clear_limit_l, clear_limit_r) constraint
        CardValue* l = MAX2(dirty_l, clear_limit_l);
        CardValue* r = MIN2(dirty_r, clear_limit_r);
        for (CardValue* i_card = l; i_card < r; ++i_card) {
          *i_card = clean_card;
        }
      }

      {
        // scan objs in [dirty_l, dirty_r) subject to [first_obj_addr, cur_stripe_end) constraint
        HeapWord* obj_l = MAX2(start_array->object_start(addr_for(dirty_l)),
                               first_obj_addr);

        HeapWord* obj_r = MIN2(addr_for(dirty_r),
                               cur_stripe_end);

        // push objs with start-addr in [obj_l, obj_r)
        HeapWord* obj_addr = obj_l;
        while (obj_addr < obj_r) {
          oop obj = cast_to_oop(obj_addr);
          assert(oopDesc::is_oop(obj), "inv");
          prefetch_write(obj_addr);
          pm->push_contents(obj);
          obj_addr += obj->size();
        }
        pm->drain_stacks_cond_depth();
      }
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

// Assumes that only the base or the end changes.  This allows indentification
// of the region that is being resized.  The
// CardTable::resize_covered_region() is used for the normal case
// where the covered regions are growing or shrinking at the high end.
// The method resize_covered_region_by_end() is analogous to
// CardTable::resize_covered_region() but
// for regions that grow or shrink at the low end.
void PSCardTable::resize_covered_region(MemRegion new_region) {
  for (int i = 0; i < _cur_covered_regions; i++) {
    if (_covered[i].start() == new_region.start()) {
      // Found a covered region with the same start as the
      // new region.  The region is growing or shrinking
      // from the start of the region.
      resize_covered_region_by_start(new_region);
      return;
    }
    if (_covered[i].start() > new_region.start()) {
      break;
    }
  }

  int changed_region = -1;
  for (int j = 0; j < _cur_covered_regions; j++) {
    if (_covered[j].end() == new_region.end()) {
      changed_region = j;
      // This is a case where the covered region is growing or shrinking
      // at the start of the region.
      assert(changed_region != -1, "Don't expect to add a covered region");
      assert(_covered[changed_region].byte_size() != new_region.byte_size(),
        "The sizes should be different here");
      resize_covered_region_by_end(changed_region, new_region);
      return;
    }
  }
  // This should only be a new covered region (where no existing
  // covered region matches at the start or the end).
  assert(_cur_covered_regions < _max_covered_regions,
    "An existing region should have been found");
  resize_covered_region_by_start(new_region);
}

void PSCardTable::resize_covered_region_by_start(MemRegion new_region) {
  CardTable::resize_covered_region(new_region);
  debug_only(verify_guard();)
}

void PSCardTable::resize_covered_region_by_end(int changed_region,
                                               MemRegion new_region) {
  assert(SafepointSynchronize::is_at_safepoint(),
    "Only expect an expansion at the low end at a GC");
  debug_only(verify_guard();)
#ifdef ASSERT
  for (int k = 0; k < _cur_covered_regions; k++) {
    if (_covered[k].end() == new_region.end()) {
      assert(changed_region == k, "Changed region is incorrect");
      break;
    }
  }
#endif

  // Commit new or uncommit old pages, if necessary.
  if (resize_commit_uncommit(changed_region, new_region)) {
    // Set the new start of the committed region
    resize_update_committed_table(changed_region, new_region);
  }

  // Update card table entries
  resize_update_card_table_entries(changed_region, new_region);

  // Update the covered region
  resize_update_covered_table(changed_region, new_region);

  int ind = changed_region;
  log_trace(gc, barrier)("CardTable::resize_covered_region: ");
  log_trace(gc, barrier)("    _covered[%d].start(): " INTPTR_FORMAT "  _covered[%d].last(): " INTPTR_FORMAT,
                ind, p2i(_covered[ind].start()), ind, p2i(_covered[ind].last()));
  log_trace(gc, barrier)("    _committed[%d].start(): " INTPTR_FORMAT "  _committed[%d].last(): " INTPTR_FORMAT,
                ind, p2i(_committed[ind].start()), ind, p2i(_committed[ind].last()));
  log_trace(gc, barrier)("    byte_for(start): " INTPTR_FORMAT "  byte_for(last): " INTPTR_FORMAT,
                p2i(byte_for(_covered[ind].start())),  p2i(byte_for(_covered[ind].last())));
  log_trace(gc, barrier)("    addr_for(start): " INTPTR_FORMAT "  addr_for(last): " INTPTR_FORMAT,
                p2i(addr_for((CardValue*) _committed[ind].start())), p2i(addr_for((CardValue*) _committed[ind].last())));

  debug_only(verify_guard();)
}

bool PSCardTable::resize_commit_uncommit(int changed_region,
                                         MemRegion new_region) {
  bool result = false;
  // Commit new or uncommit old pages, if necessary.
  MemRegion cur_committed = _committed[changed_region];
  assert(_covered[changed_region].end() == new_region.end(),
    "The ends of the regions are expected to match");
  // Extend the start of this _committed region to
  // to cover the start of any previous _committed region.
  // This forms overlapping regions, but never interior regions.
  HeapWord* min_prev_start = lowest_prev_committed_start(changed_region);
  if (min_prev_start < cur_committed.start()) {
    // Only really need to set start of "cur_committed" to
    // the new start (min_prev_start) but assertion checking code
    // below use cur_committed.end() so make it correct.
    MemRegion new_committed =
        MemRegion(min_prev_start, cur_committed.end());
    cur_committed = new_committed;
  }
#ifdef ASSERT
  ParallelScavengeHeap* heap = ParallelScavengeHeap::heap();
  assert(cur_committed.start() == align_up(cur_committed.start(), os::vm_page_size()),
         "Starts should have proper alignment");
#endif

  CardValue* new_start = byte_for(new_region.start());
  // Round down because this is for the start address
  HeapWord* new_start_aligned = align_down((HeapWord*)new_start, os::vm_page_size());
  // The guard page is always committed and should not be committed over.
  // This method is used in cases where the generation is growing toward
  // lower addresses but the guard region is still at the end of the
  // card table.  That still makes sense when looking for writes
  // off the end of the card table.
  if (new_start_aligned < cur_committed.start()) {
    // Expand the committed region
    //
    // Case A
    //                                          |+ guard +|
    //                          |+ cur committed +++++++++|
    //                  |+ new committed +++++++++++++++++|
    //
    // Case B
    //                                          |+ guard +|
    //                        |+ cur committed +|
    //                  |+ new committed +++++++|
    //
    // These are not expected because the calculation of the
    // cur committed region and the new committed region
    // share the same end for the covered region.
    // Case C
    //                                          |+ guard +|
    //                        |+ cur committed +|
    //                  |+ new committed +++++++++++++++++|
    // Case D
    //                                          |+ guard +|
    //                        |+ cur committed +++++++++++|
    //                  |+ new committed +++++++|

    HeapWord* new_end_for_commit =
      MIN2(cur_committed.end(), _guard_region.start());
    if(new_start_aligned < new_end_for_commit) {
      MemRegion new_committed =
        MemRegion(new_start_aligned, new_end_for_commit);
      os::commit_memory_or_exit((char*)new_committed.start(),
                                new_committed.byte_size(), !ExecMem,
                                "card table expansion");
    }
    result = true;
  } else if (new_start_aligned > cur_committed.start()) {
    // Shrink the committed region
#if 0 // uncommitting space is currently unsafe because of the interactions
      // of growing and shrinking regions.  One region A can uncommit space
      // that it owns but which is being used by another region B (maybe).
      // Region B has not committed the space because it was already
      // committed by region A.
    MemRegion uncommit_region = committed_unique_to_self(changed_region,
      MemRegion(cur_committed.start(), new_start_aligned));
    if (!uncommit_region.is_empty()) {
      if (!os::uncommit_memory((char*)uncommit_region.start(),
                               uncommit_region.byte_size())) {
        // If the uncommit fails, ignore it.  Let the
        // committed table resizing go even though the committed
        // table will over state the committed space.
      }
    }
#else
    assert(!result, "Should be false with current workaround");
#endif
  }
  assert(_committed[changed_region].end() == cur_committed.end(),
    "end should not change");
  return result;
}

void PSCardTable::resize_update_committed_table(int changed_region,
                                                MemRegion new_region) {

  CardValue* new_start = byte_for(new_region.start());
  // Set the new start of the committed region
  HeapWord* new_start_aligned = align_down((HeapWord*)new_start, os::vm_page_size());
  MemRegion new_committed = MemRegion(new_start_aligned,
                                      _committed[changed_region].end());
  _committed[changed_region] = new_committed;
  _committed[changed_region].set_start(new_start_aligned);
}

void PSCardTable::resize_update_card_table_entries(int changed_region,
                                                   MemRegion new_region) {
  debug_only(verify_guard();)
  MemRegion original_covered = _covered[changed_region];
  // Initialize the card entries.  Only consider the
  // region covered by the card table (_whole_heap)
  CardValue* entry;
  if (new_region.start() < _whole_heap.start()) {
    entry = byte_for(_whole_heap.start());
  } else {
    entry = byte_for(new_region.start());
  }
  CardValue* end = byte_for(original_covered.start());
  // If _whole_heap starts at the original covered regions start,
  // this loop will not execute.
  while (entry < end) { *entry++ = clean_card; }
}

void PSCardTable::resize_update_covered_table(int changed_region,
                                              MemRegion new_region) {
  // Update the covered region
  _covered[changed_region].set_start(new_region.start());
  _covered[changed_region].set_word_size(new_region.word_size());

  // reorder regions.  There should only be at most 1 out
  // of order.
  for (int i = _cur_covered_regions-1 ; i > 0; i--) {
    if (_covered[i].start() < _covered[i-1].start()) {
        MemRegion covered_mr = _covered[i-1];
        _covered[i-1] = _covered[i];
        _covered[i] = covered_mr;
        MemRegion committed_mr = _committed[i-1];
      _committed[i-1] = _committed[i];
      _committed[i] = committed_mr;
      break;
    }
  }
#ifdef ASSERT
  for (int m = 0; m < _cur_covered_regions-1; m++) {
    assert(_covered[m].start() <= _covered[m+1].start(),
      "Covered regions out of order");
    assert(_committed[m].start() <= _committed[m+1].start(),
      "Committed regions out of order");
  }
#endif
}

// Returns the start of any committed region that is lower than
// the target committed region (index ind) and that intersects the
// target region.  If none, return start of target region.
//
//      -------------
//      |           |
//      -------------
//              ------------
//              | target   |
//              ------------
//                               -------------
//                               |           |
//                               -------------
//      ^ returns this
//
//      -------------
//      |           |
//      -------------
//                      ------------
//                      | target   |
//                      ------------
//                               -------------
//                               |           |
//                               -------------
//                      ^ returns this

HeapWord* PSCardTable::lowest_prev_committed_start(int ind) const {
  assert(_cur_covered_regions >= 0, "Expecting at least on region");
  HeapWord* min_start = _committed[ind].start();
  for (int j = 0; j < ind; j++) {
    HeapWord* this_start = _committed[j].start();
    if ((this_start < min_start) &&
        !(_committed[j].intersection(_committed[ind])).is_empty()) {
       min_start = this_start;
    }
  }
  return min_start;
}

bool PSCardTable::is_in_young(oop obj) const {
  return ParallelScavengeHeap::heap()->is_in_young(obj);
}
