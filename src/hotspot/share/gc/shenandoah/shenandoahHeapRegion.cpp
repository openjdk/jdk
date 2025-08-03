/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2013, 2020, Red Hat, Inc. All rights reserved.
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

#include "gc/shared/cardTable.hpp"
#include "gc/shared/space.hpp"
#include "gc/shared/tlab_globals.hpp"
#include "gc/shenandoah/shenandoahCardTable.hpp"
#include "gc/shenandoah/shenandoahFreeSet.hpp"
#include "gc/shenandoah/shenandoahGeneration.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahHeapRegion.hpp"
#include "gc/shenandoah/shenandoahHeapRegionSet.inline.hpp"
#include "gc/shenandoah/shenandoahMarkingContext.inline.hpp"
#include "gc/shenandoah/shenandoahOldGeneration.hpp"
#include "gc/shenandoah/shenandoahScanRemembered.inline.hpp"
#include "gc/shenandoah/shenandoahYoungGeneration.hpp"
#include "jfr/jfrEvents.hpp"
#include "memory/allocation.hpp"
#include "memory/iterator.inline.hpp"
#include "memory/resourceArea.hpp"
#include "memory/universe.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/atomic.hpp"
#include "runtime/globals_extension.hpp"
#include "runtime/java.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/os.hpp"
#include "runtime/safepoint.hpp"
#include "utilities/powerOfTwo.hpp"

size_t ShenandoahHeapRegion::RegionCount = 0;
size_t ShenandoahHeapRegion::RegionSizeBytes = 0;
size_t ShenandoahHeapRegion::RegionSizeWords = 0;
size_t ShenandoahHeapRegion::RegionSizeBytesShift = 0;
size_t ShenandoahHeapRegion::RegionSizeWordsShift = 0;
size_t ShenandoahHeapRegion::RegionSizeBytesMask = 0;
size_t ShenandoahHeapRegion::RegionSizeWordsMask = 0;
size_t ShenandoahHeapRegion::MaxTLABSizeBytes = 0;
size_t ShenandoahHeapRegion::MaxTLABSizeWords = 0;

ShenandoahHeapRegion::ShenandoahHeapRegion(HeapWord* start, size_t index, bool committed) :
  _index(index),
  _bottom(start),
  _end(start + RegionSizeWords),
  _new_top(nullptr),
  _empty_time(os::elapsedTime()),
  _top_before_promoted(nullptr),
  _state(committed ? _empty_committed : _empty_uncommitted),
  _top(start),
  _tlab_allocs(0),
  _gclab_allocs(0),
  _plab_allocs(0),
  _live_data(0),
  _critical_pins(0),
  _update_watermark(start),
  _age(0),
#ifdef SHENANDOAH_CENSUS_NOISE
  _youth(0),
#endif // SHENANDOAH_CENSUS_NOISE
  _needs_bitmap_reset(false)
  {

  assert(Universe::on_page_boundary(_bottom) && Universe::on_page_boundary(_end),
         "invalid space boundaries");
  if (ZapUnusedHeapArea && committed) {
    SpaceMangler::mangle_region(MemRegion(_bottom, _end));
  }
  _recycling.unset();
}

void ShenandoahHeapRegion::report_illegal_transition(const char *method) {
  stringStream ss;
  ss.print("Illegal region state transition from \"%s\", at %s\n  ", region_state_to_string(state()), method);
  print_on(&ss);
  fatal("%s", ss.freeze());
}

void ShenandoahHeapRegion::make_regular_allocation(ShenandoahAffiliation affiliation) {
  shenandoah_assert_heaplocked();
  reset_age();
  switch (state()) {
    case _empty_uncommitted:
      do_commit();
    case _empty_committed:
      assert(this->affiliation() == affiliation, "Region affiliation should already be established");
      set_state(_regular);
    case _regular:
    case _pinned:
      return;
    default:
      report_illegal_transition("regular allocation");
  }
}

// Change affiliation to YOUNG_GENERATION if _state is not _pinned_cset, _regular, or _pinned.  This implements
// behavior previously performed as a side effect of make_regular_bypass().  This is used by Full GC in non-generational
// modes to transition regions from FREE. Note that all non-free regions in single-generational modes are young.
void ShenandoahHeapRegion::make_affiliated_maybe() {
  shenandoah_assert_heaplocked();
  assert(!ShenandoahHeap::heap()->mode()->is_generational(), "Only call if non-generational");
  switch (state()) {
   case _empty_uncommitted:
   case _empty_committed:
   case _cset:
   case _humongous_start:
   case _humongous_cont:
     if (affiliation() != YOUNG_GENERATION) {
       set_affiliation(YOUNG_GENERATION);
     }
     return;
   case _pinned_cset:
   case _regular:
   case _pinned:
     return;
   default:
     assert(false, "Unexpected _state in make_affiliated_maybe");
  }
}

void ShenandoahHeapRegion::make_regular_bypass() {
  shenandoah_assert_heaplocked();
  assert (!Universe::is_fully_initialized() ||
          ShenandoahHeap::heap()->is_full_gc_in_progress() ||
          ShenandoahHeap::heap()->is_degenerated_gc_in_progress(),
          "Only for STW GC or when Universe is initializing (CDS)");
  reset_age();
  auto cur_state = state();
  switch (cur_state) {
    case _empty_uncommitted:
      do_commit();
    case _empty_committed:
    case _cset:
    case _humongous_start:
    case _humongous_cont:
      if (cur_state == _humongous_start || cur_state == _humongous_cont) {
        // CDS allocates chunks of the heap to fill with regular objects. The allocator
        // will dutifully track any waste in the unused portion of the last region. Once
        // CDS has finished initializing the objects, it will convert these regions to
        // regular regions. The 'waste' in the last region is no longer wasted at this point,
        // so we must stop treating it as such.
        decrement_humongous_waste();
      }
      set_state(_regular);
      return;
    case _pinned_cset:
      set_state(_pinned);
      return;
    case _regular:
    case _pinned:
      return;
    default:
      report_illegal_transition("regular bypass");
  }
}

void ShenandoahHeapRegion::make_humongous_start() {
  shenandoah_assert_heaplocked();
  reset_age();
  switch (state()) {
    case _empty_uncommitted:
      do_commit();
    case _empty_committed:
      set_state(_humongous_start);
      return;
    default:
      report_illegal_transition("humongous start allocation");
  }
}

void ShenandoahHeapRegion::make_humongous_start_bypass(ShenandoahAffiliation affiliation) {
  shenandoah_assert_heaplocked();
  assert (ShenandoahHeap::heap()->is_full_gc_in_progress(), "only for full GC");
  // Don't bother to account for affiliated regions during Full GC.  We recompute totals at end.
  set_affiliation(affiliation);
  reset_age();
  switch (state()) {
    case _empty_committed:
    case _regular:
    case _humongous_start:
    case _humongous_cont:
      set_state(_humongous_start);
      return;
    default:
      report_illegal_transition("humongous start bypass");
  }
}

void ShenandoahHeapRegion::make_humongous_cont() {
  shenandoah_assert_heaplocked();
  reset_age();
  switch (state()) {
    case _empty_uncommitted:
      do_commit();
    case _empty_committed:
     set_state(_humongous_cont);
      return;
    default:
      report_illegal_transition("humongous continuation allocation");
  }
}

void ShenandoahHeapRegion::make_humongous_cont_bypass(ShenandoahAffiliation affiliation) {
  shenandoah_assert_heaplocked();
  assert (ShenandoahHeap::heap()->is_full_gc_in_progress(), "only for full GC");
  set_affiliation(affiliation);
  // Don't bother to account for affiliated regions during Full GC.  We recompute totals at end.
  reset_age();
  switch (state()) {
    case _empty_committed:
    case _regular:
    case _humongous_start:
    case _humongous_cont:
      set_state(_humongous_cont);
      return;
    default:
      report_illegal_transition("humongous continuation bypass");
  }
}

void ShenandoahHeapRegion::make_pinned() {
  shenandoah_assert_heaplocked();
  assert(pin_count() > 0, "Should have pins: %zu", pin_count());

  switch (state()) {
    case _regular:
      set_state(_pinned);
    case _pinned_cset:
    case _pinned:
      return;
    case _humongous_start:
      set_state(_pinned_humongous_start);
    case _pinned_humongous_start:
      return;
    case _cset:
      set_state(_pinned_cset);
      return;
    default:
      report_illegal_transition("pinning");
  }
}

void ShenandoahHeapRegion::make_unpinned() {
  shenandoah_assert_heaplocked();
  assert(pin_count() == 0, "Should not have pins: %zu", pin_count());

  switch (state()) {
    case _pinned:
      assert(is_affiliated(), "Pinned region should be affiliated");
      set_state(_regular);
      return;
    case _regular:
    case _humongous_start:
      return;
    case _pinned_cset:
      set_state(_cset);
      return;
    case _pinned_humongous_start:
      set_state(_humongous_start);
      return;
    default:
      report_illegal_transition("unpinning");
  }
}

void ShenandoahHeapRegion::make_cset() {
  shenandoah_assert_heaplocked();
  // Leave age untouched.  We need to consult the age when we are deciding whether to promote evacuated objects.
  switch (state()) {
    case _regular:
      set_state(_cset);
    case _cset:
      return;
    default:
      report_illegal_transition("cset");
  }
}

void ShenandoahHeapRegion::make_trash() {
  shenandoah_assert_heaplocked();
  reset_age();
  switch (state()) {
    case _humongous_start:
    case _humongous_cont:
    {
      // Reclaiming humongous regions and reclaim humongous waste.  When this region is eventually recycled, we'll reclaim
      // its used memory.  At recycle time, we no longer recognize this as a humongous region.
      decrement_humongous_waste();
    }
    case _cset:
      // Reclaiming cset regions
    case _regular:
      // Immediate region reclaim
      set_state(_trash);
      return;
    default:
      report_illegal_transition("trashing");
  }
}

void ShenandoahHeapRegion::make_trash_immediate() {
  make_trash();

  // On this path, we know there are no marked objects in the region,
  // tell marking context about it to bypass bitmap resets.
  assert(ShenandoahHeap::heap()->gc_generation()->is_mark_complete(), "Marking should be complete here.");
  shenandoah_assert_generations_reconciled();
  ShenandoahHeap::heap()->marking_context()->reset_top_bitmap(this);
}

void ShenandoahHeapRegion::make_empty() {
  reset_age();
  CENSUS_NOISE(clear_youth();)
  switch (state()) {
    case _trash:
      set_state(_empty_committed);
      _empty_time = os::elapsedTime();
      return;
    default:
      report_illegal_transition("emptying");
  }
}

void ShenandoahHeapRegion::make_uncommitted() {
  shenandoah_assert_heaplocked();
  switch (state()) {
    case _empty_committed:
      do_uncommit();
      set_state(_empty_uncommitted);
      return;
    default:
      report_illegal_transition("uncommiting");
  }
}

void ShenandoahHeapRegion::make_committed_bypass() {
  shenandoah_assert_heaplocked();
  assert (ShenandoahHeap::heap()->is_full_gc_in_progress(), "only for full GC");

  switch (state()) {
    case _empty_uncommitted:
      do_commit();
      set_state(_empty_committed);
      return;
    default:
      report_illegal_transition("commit bypass");
  }
}

void ShenandoahHeapRegion::reset_alloc_metadata() {
  _tlab_allocs = 0;
  _gclab_allocs = 0;
  _plab_allocs = 0;
}

size_t ShenandoahHeapRegion::get_shared_allocs() const {
  return used() - (_tlab_allocs + _gclab_allocs + _plab_allocs) * HeapWordSize;
}

size_t ShenandoahHeapRegion::get_tlab_allocs() const {
  return _tlab_allocs * HeapWordSize;
}

size_t ShenandoahHeapRegion::get_gclab_allocs() const {
  return _gclab_allocs * HeapWordSize;
}

size_t ShenandoahHeapRegion::get_plab_allocs() const {
  return _plab_allocs * HeapWordSize;
}

void ShenandoahHeapRegion::set_live_data(size_t s) {
  assert(Thread::current()->is_VM_thread(), "by VM thread");
  _live_data = (s >> LogHeapWordSize);
}

void ShenandoahHeapRegion::print_on(outputStream* st) const {
  st->print("|");
  st->print("%5zu", this->_index);

  switch (state()) {
    case _empty_uncommitted:
      st->print("|EU ");
      break;
    case _empty_committed:
      st->print("|EC ");
      break;
    case _regular:
      st->print("|R  ");
      break;
    case _humongous_start:
      st->print("|H  ");
      break;
    case _pinned_humongous_start:
      st->print("|HP ");
      break;
    case _humongous_cont:
      st->print("|HC ");
      break;
    case _cset:
      st->print("|CS ");
      break;
    case _trash:
      st->print("|TR ");
      break;
    case _pinned:
      st->print("|P  ");
      break;
    case _pinned_cset:
      st->print("|CSP");
      break;
    default:
      ShouldNotReachHere();
  }

  st->print("|%s", shenandoah_affiliation_code(affiliation()));

#define SHR_PTR_FORMAT "%12" PRIxPTR

  st->print("|BTE " SHR_PTR_FORMAT  ", " SHR_PTR_FORMAT ", " SHR_PTR_FORMAT,
            p2i(bottom()), p2i(top()), p2i(end()));
  st->print("|TAMS " SHR_PTR_FORMAT,
            p2i(ShenandoahHeap::heap()->marking_context()->top_at_mark_start(const_cast<ShenandoahHeapRegion*>(this))));
  st->print("|UWM " SHR_PTR_FORMAT,
            p2i(_update_watermark));
  st->print("|U %5zu%1s", byte_size_in_proper_unit(used()),                proper_unit_for_byte_size(used()));
  st->print("|T %5zu%1s", byte_size_in_proper_unit(get_tlab_allocs()),     proper_unit_for_byte_size(get_tlab_allocs()));
  st->print("|G %5zu%1s", byte_size_in_proper_unit(get_gclab_allocs()),    proper_unit_for_byte_size(get_gclab_allocs()));
  if (ShenandoahHeap::heap()->mode()->is_generational()) {
    st->print("|P %5zu%1s", byte_size_in_proper_unit(get_plab_allocs()),   proper_unit_for_byte_size(get_plab_allocs()));
  }
  st->print("|S %5zu%1s", byte_size_in_proper_unit(get_shared_allocs()),   proper_unit_for_byte_size(get_shared_allocs()));
  st->print("|L %5zu%1s", byte_size_in_proper_unit(get_live_data_bytes()), proper_unit_for_byte_size(get_live_data_bytes()));
  st->print("|CP %3zu", pin_count());
  st->cr();

#undef SHR_PTR_FORMAT
}

// oop_iterate without closure, return true if completed without cancellation
bool ShenandoahHeapRegion::oop_coalesce_and_fill(bool cancellable) {

  assert(!is_humongous(), "No need to fill or coalesce humongous regions");
  if (!is_active()) {
    end_preemptible_coalesce_and_fill();
    return true;
  }

  ShenandoahGenerationalHeap* heap = ShenandoahGenerationalHeap::heap();
  ShenandoahMarkingContext* marking_context = heap->marking_context();

  // Expect marking to be completed before these threads invoke this service.
  assert(heap->gc_generation()->is_mark_complete(), "sanity");
  shenandoah_assert_generations_reconciled();

  // All objects above TAMS are considered live even though their mark bits will not be set.  Note that young-
  // gen evacuations that interrupt a long-running old-gen concurrent mark may promote objects into old-gen
  // while the old-gen concurrent marking is ongoing.  These newly promoted objects will reside above TAMS
  // and will be treated as live during the current old-gen marking pass, even though they will not be
  // explicitly marked.
  HeapWord* t = marking_context->top_at_mark_start(this);

  // Resume coalesce and fill from this address
  HeapWord* obj_addr = resume_coalesce_and_fill();

  while (obj_addr < t) {
    oop obj = cast_to_oop(obj_addr);
    if (marking_context->is_marked(obj)) {
      assert(obj->klass() != nullptr, "klass should not be nullptr");
      obj_addr += obj->size();
    } else {
      // Object is not marked.  Coalesce and fill dead object with dead neighbors.
      HeapWord* next_marked_obj = marking_context->get_next_marked_addr(obj_addr, t);
      assert(next_marked_obj <= t, "next marked object cannot exceed top");
      size_t fill_size = next_marked_obj - obj_addr;
      assert(fill_size >= ShenandoahHeap::min_fill_size(), "previously allocated object known to be larger than min_size");
      ShenandoahHeap::fill_with_object(obj_addr, fill_size);
      heap->old_generation()->card_scan()->coalesce_objects(obj_addr, fill_size);
      obj_addr = next_marked_obj;
    }
    if (cancellable && heap->cancelled_gc()) {
      suspend_coalesce_and_fill(obj_addr);
      return false;
    }
  }
  // Mark that this region has been coalesced and filled
  end_preemptible_coalesce_and_fill();
  return true;
}

size_t get_card_count(size_t words) {
  assert(words % CardTable::card_size_in_words() == 0, "Humongous iteration must span whole number of cards");
  assert(CardTable::card_size_in_words() * (words / CardTable::card_size_in_words()) == words,
         "slice must be integral number of cards");
  return words / CardTable::card_size_in_words();
}

void ShenandoahHeapRegion::oop_iterate_humongous_slice_dirty(OopIterateClosure* blk,
                                                             HeapWord* start, size_t words, bool write_table) const {
  assert(is_humongous(), "only humongous region here");

  ShenandoahHeapRegion* r = humongous_start_region();
  oop obj = cast_to_oop(r->bottom());
  size_t num_cards = get_card_count(words);

  ShenandoahGenerationalHeap* heap = ShenandoahGenerationalHeap::heap();
  ShenandoahScanRemembered* scanner = heap->old_generation()->card_scan();
  size_t card_index = scanner->card_index_for_addr(start);
  if (write_table) {
    while (num_cards-- > 0) {
      if (scanner->is_write_card_dirty(card_index++)) {
        obj->oop_iterate(blk, MemRegion(start, start + CardTable::card_size_in_words()));
      }
      start += CardTable::card_size_in_words();
    }
  } else {
    while (num_cards-- > 0) {
      if (scanner->is_card_dirty(card_index++)) {
        obj->oop_iterate(blk, MemRegion(start, start + CardTable::card_size_in_words()));
      }
      start += CardTable::card_size_in_words();
    }
  }
}

void ShenandoahHeapRegion::oop_iterate_humongous_slice_all(OopIterateClosure* cl, HeapWord* start, size_t words) const {
  assert(is_humongous(), "only humongous region here");

  ShenandoahHeapRegion* r = humongous_start_region();
  oop obj = cast_to_oop(r->bottom());

  // Scan all data, regardless of whether cards are dirty
  obj->oop_iterate(cl, MemRegion(start, start + words));
}

ShenandoahHeapRegion* ShenandoahHeapRegion::humongous_start_region() const {
  ShenandoahHeap* heap = ShenandoahHeap::heap();
  assert(is_humongous(), "Must be a part of the humongous region");
  size_t i = index();
  ShenandoahHeapRegion* r = const_cast<ShenandoahHeapRegion*>(this);
  while (!r->is_humongous_start()) {
    assert(i > 0, "Sanity");
    i--;
    r = heap->get_region(i);
    assert(r->is_humongous(), "Must be a part of the humongous region");
  }
  assert(r->is_humongous_start(), "Must be");
  return r;
}


void ShenandoahHeapRegion::recycle_internal() {
  assert(_recycling.is_set() && is_trash(), "Wrong state");
  ShenandoahHeap* heap = ShenandoahHeap::heap();

  set_top(bottom());
  clear_live_data();
  reset_alloc_metadata();
  heap->marking_context()->reset_top_at_mark_start(this);
  set_update_watermark(bottom());
  if (ZapUnusedHeapArea) {
    SpaceMangler::mangle_region(MemRegion(bottom(), end()));
  }

  make_empty();
  set_affiliation(FREE);
}

void ShenandoahHeapRegion::try_recycle_under_lock() {
  shenandoah_assert_heaplocked();
  if (is_trash() && _recycling.try_set()) {
    if (is_trash()) {
      ShenandoahHeap* heap = ShenandoahHeap::heap();
      ShenandoahGeneration* generation = heap->generation_for(affiliation());

      heap->decrease_used(generation, used());
      generation->decrement_affiliated_region_count();

      recycle_internal();
    }
    _recycling.unset();
  } else {
    // Ensure recycling is unset before returning to mutator to continue memory allocation.
    while (_recycling.is_set()) {
      if (os::is_MP()) {
        SpinPause();
      } else {
        os::naked_yield();
      }
    }
  }
}

void ShenandoahHeapRegion::try_recycle() {
  shenandoah_assert_not_heaplocked();
  if (is_trash() && _recycling.try_set()) {
    // Double check region state after win the race to set recycling flag
    if (is_trash()) {
      ShenandoahHeap* heap = ShenandoahHeap::heap();
      ShenandoahGeneration* generation = heap->generation_for(affiliation());
      heap->decrease_used(generation, used());
      generation->decrement_affiliated_region_count_without_lock();

      recycle_internal();
    }
    _recycling.unset();
  }
}

HeapWord* ShenandoahHeapRegion::block_start(const void* p) const {
  assert(MemRegion(bottom(), end()).contains(p),
         "p (" PTR_FORMAT ") not in space [" PTR_FORMAT ", " PTR_FORMAT ")",
         p2i(p), p2i(bottom()), p2i(end()));
  if (p >= top()) {
    return top();
  } else {
    HeapWord* last = bottom();
    HeapWord* cur = last;
    while (cur <= p) {
      last = cur;
      cur += cast_to_oop(cur)->size();
    }
    shenandoah_assert_correct(nullptr, cast_to_oop(last));
    return last;
  }
}

size_t ShenandoahHeapRegion::block_size(const HeapWord* p) const {
  assert(MemRegion(bottom(), end()).contains(p),
         "p (" PTR_FORMAT ") not in space [" PTR_FORMAT ", " PTR_FORMAT ")",
         p2i(p), p2i(bottom()), p2i(end()));
  if (p < top()) {
    return cast_to_oop(p)->size();
  } else {
    assert(p == top(), "just checking");
    return pointer_delta(end(), (HeapWord*) p);
  }
}

size_t ShenandoahHeapRegion::setup_sizes(size_t max_heap_size) {
  // Absolute minimums we should not ever break.
  static const size_t MIN_REGION_SIZE = 256*K;

  if (FLAG_IS_DEFAULT(ShenandoahMinRegionSize)) {
    FLAG_SET_DEFAULT(ShenandoahMinRegionSize, MIN_REGION_SIZE);
  }

  // Generational Shenandoah needs this alignment for card tables.
  if (strcmp(ShenandoahGCMode, "generational") == 0) {
    max_heap_size = align_up(max_heap_size , CardTable::ct_max_alignment_constraint());
  }

  size_t region_size;
  if (FLAG_IS_DEFAULT(ShenandoahRegionSize)) {
    if (ShenandoahMinRegionSize > max_heap_size / MIN_NUM_REGIONS) {
      err_msg message("Max heap size (%zu%s) is too low to afford the minimum number "
                      "of regions (%zu) of minimum region size (%zu%s).",
                      byte_size_in_proper_unit(max_heap_size), proper_unit_for_byte_size(max_heap_size),
                      MIN_NUM_REGIONS,
                      byte_size_in_proper_unit(ShenandoahMinRegionSize), proper_unit_for_byte_size(ShenandoahMinRegionSize));
      vm_exit_during_initialization("Invalid -XX:ShenandoahMinRegionSize option", message);
    }
    if (ShenandoahMinRegionSize < MIN_REGION_SIZE) {
      err_msg message("%zu%s should not be lower than minimum region size (%zu%s).",
                      byte_size_in_proper_unit(ShenandoahMinRegionSize), proper_unit_for_byte_size(ShenandoahMinRegionSize),
                      byte_size_in_proper_unit(MIN_REGION_SIZE),         proper_unit_for_byte_size(MIN_REGION_SIZE));
      vm_exit_during_initialization("Invalid -XX:ShenandoahMinRegionSize option", message);
    }
    if (ShenandoahMinRegionSize < MinTLABSize) {
      err_msg message("%zu%s should not be lower than TLAB size size (%zu%s).",
                      byte_size_in_proper_unit(ShenandoahMinRegionSize), proper_unit_for_byte_size(ShenandoahMinRegionSize),
                      byte_size_in_proper_unit(MinTLABSize),             proper_unit_for_byte_size(MinTLABSize));
      vm_exit_during_initialization("Invalid -XX:ShenandoahMinRegionSize option", message);
    }
    if (ShenandoahMaxRegionSize < MIN_REGION_SIZE) {
      err_msg message("%zu%s should not be lower than min region size (%zu%s).",
                      byte_size_in_proper_unit(ShenandoahMaxRegionSize), proper_unit_for_byte_size(ShenandoahMaxRegionSize),
                      byte_size_in_proper_unit(MIN_REGION_SIZE),         proper_unit_for_byte_size(MIN_REGION_SIZE));
      vm_exit_during_initialization("Invalid -XX:ShenandoahMaxRegionSize option", message);
    }
    if (ShenandoahMinRegionSize > ShenandoahMaxRegionSize) {
      err_msg message("Minimum (%zu%s) should be larger than maximum (%zu%s).",
                      byte_size_in_proper_unit(ShenandoahMinRegionSize), proper_unit_for_byte_size(ShenandoahMinRegionSize),
                      byte_size_in_proper_unit(ShenandoahMaxRegionSize), proper_unit_for_byte_size(ShenandoahMaxRegionSize));
      vm_exit_during_initialization("Invalid -XX:ShenandoahMinRegionSize or -XX:ShenandoahMaxRegionSize", message);
    }

    // We rapidly expand to max_heap_size in most scenarios, so that is the measure
    // for usual heap sizes. Do not depend on initial_heap_size here.
    region_size = max_heap_size / ShenandoahTargetNumRegions;

    // Now make sure that we don't go over or under our limits.
    region_size = MAX2(ShenandoahMinRegionSize, region_size);
    region_size = MIN2(ShenandoahMaxRegionSize, region_size);

  } else {
    if (ShenandoahRegionSize > max_heap_size / MIN_NUM_REGIONS) {
      err_msg message("Max heap size (%zu%s) is too low to afford the minimum number "
                              "of regions (%zu) of requested size (%zu%s).",
                      byte_size_in_proper_unit(max_heap_size), proper_unit_for_byte_size(max_heap_size),
                      MIN_NUM_REGIONS,
                      byte_size_in_proper_unit(ShenandoahRegionSize), proper_unit_for_byte_size(ShenandoahRegionSize));
      vm_exit_during_initialization("Invalid -XX:ShenandoahRegionSize option", message);
    }
    if (ShenandoahRegionSize < ShenandoahMinRegionSize) {
      err_msg message("Heap region size (%zu%s) should be larger than min region size (%zu%s).",
                      byte_size_in_proper_unit(ShenandoahRegionSize), proper_unit_for_byte_size(ShenandoahRegionSize),
                      byte_size_in_proper_unit(ShenandoahMinRegionSize),  proper_unit_for_byte_size(ShenandoahMinRegionSize));
      vm_exit_during_initialization("Invalid -XX:ShenandoahRegionSize option", message);
    }
    if (ShenandoahRegionSize > ShenandoahMaxRegionSize) {
      err_msg message("Heap region size (%zu%s) should be lower than max region size (%zu%s).",
                      byte_size_in_proper_unit(ShenandoahRegionSize), proper_unit_for_byte_size(ShenandoahRegionSize),
                      byte_size_in_proper_unit(ShenandoahMaxRegionSize),  proper_unit_for_byte_size(ShenandoahMaxRegionSize));
      vm_exit_during_initialization("Invalid -XX:ShenandoahRegionSize option", message);
    }
    region_size = ShenandoahRegionSize;
  }

  // Make sure region size and heap size are page aligned.
  // If large pages are used, we ensure that region size is aligned to large page size if
  // heap size is large enough to accommodate minimal number of regions. Otherwise, we align
  // region size to regular page size.

  // Figure out page size to use, and aligns up heap to page size
  size_t page_size = os::vm_page_size();
  if (UseLargePages) {
    size_t large_page_size = os::large_page_size();
    max_heap_size = align_up(max_heap_size, large_page_size);
    if ((max_heap_size / align_up(region_size, large_page_size)) >= MIN_NUM_REGIONS) {
      page_size = large_page_size;
    } else {
      // Should have been checked during argument initialization
      assert(!ShenandoahUncommit, "Uncommit requires region size aligns to large page size");
    }
  } else {
    max_heap_size = align_up(max_heap_size, page_size);
  }

  // Align region size to page size
  region_size = align_up(region_size, page_size);

  int region_size_log = log2i(region_size);
  // Recalculate the region size to make sure it's a power of
  // 2. This means that region_size is the largest power of 2 that's
  // <= what we've calculated so far.
  region_size = size_t(1) << region_size_log;

  // Now, set up the globals.
  guarantee(RegionSizeBytesShift == 0, "we should only set it once");
  RegionSizeBytesShift = (size_t)region_size_log;

  guarantee(RegionSizeWordsShift == 0, "we should only set it once");
  RegionSizeWordsShift = RegionSizeBytesShift - LogHeapWordSize;

  guarantee(RegionSizeBytes == 0, "we should only set it once");
  RegionSizeBytes = region_size;
  RegionSizeWords = RegionSizeBytes >> LogHeapWordSize;
  assert (RegionSizeWords*HeapWordSize == RegionSizeBytes, "sanity");

  guarantee(RegionSizeWordsMask == 0, "we should only set it once");
  RegionSizeWordsMask = RegionSizeWords - 1;

  guarantee(RegionSizeBytesMask == 0, "we should only set it once");
  RegionSizeBytesMask = RegionSizeBytes - 1;

  guarantee(RegionCount == 0, "we should only set it once");
  RegionCount = align_up(max_heap_size, RegionSizeBytes) / RegionSizeBytes;
  guarantee(RegionCount >= MIN_NUM_REGIONS, "Should have at least minimum regions");

  // Limit TLAB size for better startup behavior and more equitable distribution of memory between contending mutator threads.
  guarantee(MaxTLABSizeWords == 0, "we should only set it once");
  MaxTLABSizeWords = align_down(MIN2(RegionSizeWords, MAX2(RegionSizeWords / 32, (size_t) (256 * 1024) / HeapWordSize)),
                                MinObjAlignment);

  guarantee(MaxTLABSizeBytes == 0, "we should only set it once");
  MaxTLABSizeBytes = MaxTLABSizeWords * HeapWordSize;
  assert (MaxTLABSizeBytes > MinTLABSize, "should be larger");

  return max_heap_size;
}

void ShenandoahHeapRegion::do_commit() {
  ShenandoahHeap* heap = ShenandoahHeap::heap();
  if (!heap->is_heap_region_special()) {
    os::commit_memory_or_exit((char*) bottom(), RegionSizeBytes, false, "Unable to commit region");
  }
  if (!heap->is_bitmap_region_special()) {
    heap->commit_bitmap_slice(this);
  }
  if (AlwaysPreTouch) {
    os::pretouch_memory(bottom(), end(), heap->pretouch_heap_page_size());
  }
  if (ZapUnusedHeapArea) {
    SpaceMangler::mangle_region(MemRegion(bottom(), end()));
  }
  heap->increase_committed(ShenandoahHeapRegion::region_size_bytes());
}

void ShenandoahHeapRegion::do_uncommit() {
  ShenandoahHeap* heap = ShenandoahHeap::heap();
  if (!heap->is_heap_region_special()) {
    bool success = os::uncommit_memory((char *) bottom(), RegionSizeBytes);
    if (!success) {
      log_warning(gc)("Region uncommit failed: " PTR_FORMAT " (%zu bytes)", p2i(bottom()), RegionSizeBytes);
      assert(false, "Region uncommit should always succeed");
    }
  }
  if (!heap->is_bitmap_region_special()) {
    heap->uncommit_bitmap_slice(this);
  }
  heap->decrease_committed(ShenandoahHeapRegion::region_size_bytes());
}

void ShenandoahHeapRegion::set_state(RegionState to) {
  EventShenandoahHeapRegionStateChange evt;
  if (evt.should_commit()){
    evt.set_index((unsigned) index());
    evt.set_start((uintptr_t)bottom());
    evt.set_used(used());
    evt.set_from(state());
    evt.set_to(to);
    evt.commit();
  }
  Atomic::store(&_state, to);
}

void ShenandoahHeapRegion::record_pin() {
  Atomic::add(&_critical_pins, (size_t)1);
}

void ShenandoahHeapRegion::record_unpin() {
  assert(pin_count() > 0, "Region %zu should have non-zero pins", index());
  Atomic::sub(&_critical_pins, (size_t)1);
}

size_t ShenandoahHeapRegion::pin_count() const {
  return Atomic::load(&_critical_pins);
}

void ShenandoahHeapRegion::set_affiliation(ShenandoahAffiliation new_affiliation) {
  ShenandoahHeap* heap = ShenandoahHeap::heap();

  ShenandoahAffiliation region_affiliation = heap->region_affiliation(this);
  ShenandoahMarkingContext* const ctx = heap->marking_context();
  {
    log_debug(gc)("Setting affiliation of Region %zu from %s to %s, top: " PTR_FORMAT ", TAMS: " PTR_FORMAT
                  ", watermark: " PTR_FORMAT ", top_bitmap: " PTR_FORMAT,
                  index(), shenandoah_affiliation_name(region_affiliation), shenandoah_affiliation_name(new_affiliation),
                  p2i(top()), p2i(ctx->top_at_mark_start(this)), p2i(_update_watermark), p2i(ctx->top_bitmap(this)));
  }

#ifdef ASSERT
  {
    size_t idx = this->index();
    HeapWord* top_bitmap = ctx->top_bitmap(this);

    assert(ctx->is_bitmap_range_within_region_clear(top_bitmap, _end),
           "Region %zu, bitmap should be clear between top_bitmap: " PTR_FORMAT " and end: " PTR_FORMAT, idx,
           p2i(top_bitmap), p2i(_end));
  }
#endif

  if (region_affiliation == new_affiliation) {
    return;
  }

  if (!heap->mode()->is_generational()) {
    log_trace(gc)("Changing affiliation of region %zu from %s to %s",
                  index(), affiliation_name(), shenandoah_affiliation_name(new_affiliation));
    heap->set_affiliation(this, new_affiliation);
    return;
  }

  switch (new_affiliation) {
    case FREE:
      assert(!has_live(), "Free region should not have live data");
      break;
    case YOUNG_GENERATION:
      reset_age();
      break;
    case OLD_GENERATION:
      break;
    default:
      ShouldNotReachHere();
      return;
  }
  heap->set_affiliation(this, new_affiliation);
}

void ShenandoahHeapRegion::decrement_humongous_waste() const {
  assert(is_humongous(), "Should only use this for humongous regions");
  size_t waste_bytes = free();
  if (waste_bytes > 0) {
    ShenandoahHeap* heap = ShenandoahHeap::heap();
    ShenandoahGeneration* generation = heap->generation_for(affiliation());
    heap->decrease_humongous_waste(generation, waste_bytes);
  }
}
