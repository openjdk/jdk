/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "gc/shared/cardTable.hpp"
#include "gc/shared/space.inline.hpp"
#include "gc/shared/tlab_globals.hpp"
#include "gc/shenandoah/shenandoahCardTable.hpp"
#include "gc/shenandoah/shenandoahFreeSet.hpp"
#include "gc/shenandoah/shenandoahHeapRegionSet.inline.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahHeapRegion.hpp"
#include "gc/shenandoah/shenandoahMarkingContext.inline.hpp"
#include "gc/shenandoah/shenandoahOldGeneration.hpp"
#include "gc/shenandoah/shenandoahGeneration.hpp"
#include "gc/shenandoah/shenandoahYoungGeneration.hpp"
#include "gc/shenandoah/shenandoahScanRemembered.inline.hpp"
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
size_t ShenandoahHeapRegion::HumongousThresholdBytes = 0;
size_t ShenandoahHeapRegion::HumongousThresholdWords = 0;
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
  _age(0)
#ifdef SHENANDOAH_CENSUS_NOISE
  , _youth(0)
#endif // SHENANDOAH_CENSUS_NOISE
  {

  assert(Universe::on_page_boundary(_bottom) && Universe::on_page_boundary(_end),
         "invalid space boundaries");
  if (ZapUnusedHeapArea && committed) {
    SpaceMangler::mangle_region(MemRegion(_bottom, _end));
  }
}

void ShenandoahHeapRegion::report_illegal_transition(const char *method) {
  stringStream ss;
  ss.print("Illegal region state transition from \"%s\", at %s\n  ", region_state_to_string(_state), method);
  print_on(&ss);
  fatal("%s", ss.freeze());
}

void ShenandoahHeapRegion::make_regular_allocation(ShenandoahAffiliation affiliation) {
  shenandoah_assert_heaplocked();
  reset_age();
  switch (_state) {
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
// behavior previously performed as a side effect of make_regular_bypass().
void ShenandoahHeapRegion::make_young_maybe() {
  shenandoah_assert_heaplocked();
  switch (_state) {
   case _empty_uncommitted:
   case _empty_committed:
   case _cset:
   case _humongous_start:
   case _humongous_cont:
     if (affiliation() != YOUNG_GENERATION) {
       if (is_old()) {
         ShenandoahHeap::heap()->old_generation()->decrement_affiliated_region_count();
       }
       set_affiliation(YOUNG_GENERATION);
       ShenandoahHeap::heap()->young_generation()->increment_affiliated_region_count();
     }
     return;
   case _pinned_cset:
   case _regular:
   case _pinned:
     return;
   default:
     assert(false, "Unexpected _state in make_young_maybe");
  }
}

void ShenandoahHeapRegion::make_regular_bypass() {
  shenandoah_assert_heaplocked();
  assert (ShenandoahHeap::heap()->is_full_gc_in_progress() || ShenandoahHeap::heap()->is_degenerated_gc_in_progress(),
          "only for full or degen GC");
  reset_age();
  switch (_state) {
    case _empty_uncommitted:
      do_commit();
    case _empty_committed:
    case _cset:
    case _humongous_start:
    case _humongous_cont:
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
  switch (_state) {
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
  switch (_state) {
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
  switch (_state) {
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
  switch (_state) {
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
  assert(pin_count() > 0, "Should have pins: " SIZE_FORMAT, pin_count());

  switch (_state) {
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
      _state = _pinned_cset;
      return;
    default:
      report_illegal_transition("pinning");
  }
}

void ShenandoahHeapRegion::make_unpinned() {
  shenandoah_assert_heaplocked();
  assert(pin_count() == 0, "Should not have pins: " SIZE_FORMAT, pin_count());

  switch (_state) {
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
  switch (_state) {
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
  switch (_state) {
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
  assert(ShenandoahHeap::heap()->active_generation()->is_mark_complete(), "Marking should be complete here.");
  ShenandoahHeap::heap()->marking_context()->reset_top_bitmap(this);
}

void ShenandoahHeapRegion::make_empty() {
  shenandoah_assert_heaplocked();
  reset_age();
  CENSUS_NOISE(clear_youth();)
  switch (_state) {
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
  switch (_state) {
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

  switch (_state) {
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
  st->print(SIZE_FORMAT_W(5), this->_index);

  switch (_state) {
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
  st->print("|U " SIZE_FORMAT_W(5) "%1s", byte_size_in_proper_unit(used()),                proper_unit_for_byte_size(used()));
  st->print("|T " SIZE_FORMAT_W(5) "%1s", byte_size_in_proper_unit(get_tlab_allocs()),     proper_unit_for_byte_size(get_tlab_allocs()));
  st->print("|G " SIZE_FORMAT_W(5) "%1s", byte_size_in_proper_unit(get_gclab_allocs()),    proper_unit_for_byte_size(get_gclab_allocs()));
  if (ShenandoahHeap::heap()->mode()->is_generational()) {
    st->print("|P " SIZE_FORMAT_W(5) "%1s", byte_size_in_proper_unit(get_plab_allocs()),   proper_unit_for_byte_size(get_plab_allocs()));
  }
  st->print("|S " SIZE_FORMAT_W(5) "%1s", byte_size_in_proper_unit(get_shared_allocs()),   proper_unit_for_byte_size(get_shared_allocs()));
  st->print("|L " SIZE_FORMAT_W(5) "%1s", byte_size_in_proper_unit(get_live_data_bytes()), proper_unit_for_byte_size(get_live_data_bytes()));
  st->print("|CP " SIZE_FORMAT_W(3), pin_count());
  st->cr();

#undef SHR_PTR_FORMAT
}

// oop_iterate without closure and without cancellation.  always return true.
bool ShenandoahHeapRegion::oop_fill_and_coalesce_without_cancel() {
  HeapWord* obj_addr = resume_coalesce_and_fill();

  assert(!is_humongous(), "No need to fill or coalesce humongous regions");
  if (!is_active()) {
    end_preemptible_coalesce_and_fill();
    return true;
  }

  ShenandoahHeap* heap = ShenandoahHeap::heap();
  ShenandoahMarkingContext* marking_context = heap->marking_context();
  // All objects above TAMS are considered live even though their mark bits will not be set.  Note that young-
  // gen evacuations that interrupt a long-running old-gen concurrent mark may promote objects into old-gen
  // while the old-gen concurrent marking is ongoing.  These newly promoted objects will reside above TAMS
  // and will be treated as live during the current old-gen marking pass, even though they will not be
  // explicitly marked.
  HeapWord* t = marking_context->top_at_mark_start(this);

  // Expect marking to be completed before these threads invoke this service.
  assert(heap->active_generation()->is_mark_complete(), "sanity");
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
      assert(fill_size >= ShenandoahHeap::min_fill_size(), "previously allocated objects known to be larger than min_size");
      ShenandoahHeap::fill_with_object(obj_addr, fill_size);
      heap->card_scan()->coalesce_objects(obj_addr, fill_size);
      obj_addr = next_marked_obj;
    }
  }
  // Mark that this region has been coalesced and filled
  end_preemptible_coalesce_and_fill();
  return true;
}

// oop_iterate without closure, return true if completed without cancellation
bool ShenandoahHeapRegion::oop_fill_and_coalesce() {
  HeapWord* obj_addr = resume_coalesce_and_fill();
  // Consider yielding to cancel/preemption request after this many coalesce operations (skip marked, or coalesce free).
  const size_t preemption_stride = 128;

  assert(!is_humongous(), "No need to fill or coalesce humongous regions");
  if (!is_active()) {
    end_preemptible_coalesce_and_fill();
    return true;
  }

  ShenandoahHeap* heap = ShenandoahHeap::heap();
  ShenandoahMarkingContext* marking_context = heap->marking_context();
  // All objects above TAMS are considered live even though their mark bits will not be set.  Note that young-
  // gen evacuations that interrupt a long-running old-gen concurrent mark may promote objects into old-gen
  // while the old-gen concurrent marking is ongoing.  These newly promoted objects will reside above TAMS
  // and will be treated as live during the current old-gen marking pass, even though they will not be
  // explicitly marked.
  HeapWord* t = marking_context->top_at_mark_start(this);

  // Expect marking to be completed before these threads invoke this service.
  assert(heap->active_generation()->is_mark_complete(), "sanity");

  size_t ops_before_preempt_check = preemption_stride;
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
      heap->card_scan()->coalesce_objects(obj_addr, fill_size);
      obj_addr = next_marked_obj;
    }
    if (ops_before_preempt_check-- == 0) {
      if (heap->cancelled_gc()) {
        suspend_coalesce_and_fill(obj_addr);
        return false;
      }
      ops_before_preempt_check = preemption_stride;
    }
  }
  // Mark that this region has been coalesced and filled
  end_preemptible_coalesce_and_fill();
  return true;
}

void ShenandoahHeapRegion::global_oop_iterate_and_fill_dead(OopIterateClosure* blk) {
  if (!is_active()) return;
  if (is_humongous()) {
    // No need to fill dead within humongous regions.  Either the entire region is dead, or the entire region is
    // unchanged.  A humongous region holds no more than one humongous object.
    oop_iterate_humongous(blk);
  } else {
    global_oop_iterate_objects_and_fill_dead(blk);
  }
}

void ShenandoahHeapRegion::global_oop_iterate_objects_and_fill_dead(OopIterateClosure* blk) {
  assert(!is_humongous(), "no humongous region here");
  HeapWord* obj_addr = bottom();

  ShenandoahHeap* heap = ShenandoahHeap::heap();
  ShenandoahMarkingContext* marking_context = heap->marking_context();
  RememberedScanner* rem_set_scanner = heap->card_scan();
  // Objects allocated above TAMS are not marked, but are considered live for purposes of current GC efforts.
  HeapWord* t = marking_context->top_at_mark_start(this);

  assert(heap->active_generation()->is_mark_complete(), "sanity");

  while (obj_addr < t) {
    oop obj = cast_to_oop(obj_addr);
    if (marking_context->is_marked(obj)) {
      assert(obj->klass() != nullptr, "klass should not be nullptr");
      // when promoting an entire region, we have to register the marked objects as well
      obj_addr += obj->oop_iterate_size(blk);
    } else {
      // Object is not marked.  Coalesce and fill dead object with dead neighbors.
      HeapWord* next_marked_obj = marking_context->get_next_marked_addr(obj_addr, t);
      assert(next_marked_obj <= t, "next marked object cannot exceed top");
      size_t fill_size = next_marked_obj - obj_addr;
      assert(fill_size >= ShenandoahHeap::min_fill_size(), "previously allocated objects known to be larger than min_size");
      ShenandoahHeap::fill_with_object(obj_addr, fill_size);
      // coalesce_objects() unregisters all but first object subsumed within coalesced range.
      rem_set_scanner->coalesce_objects(obj_addr, fill_size);
      obj_addr = next_marked_obj;
    }
  }

  // Any object above TAMS and below top() is considered live.
  t = top();
  while (obj_addr < t) {
    oop obj = cast_to_oop(obj_addr);
    obj_addr += obj->oop_iterate_size(blk);
  }
}

// DO NOT CANCEL.  If this worker thread has accepted responsibility for scanning a particular range of addresses, it
// must finish the work before it can be cancelled.
void ShenandoahHeapRegion::oop_iterate_humongous_slice(OopIterateClosure* blk, bool dirty_only,
                                                       HeapWord* start, size_t words, bool write_table) {
  assert(words % CardTable::card_size_in_words() == 0, "Humongous iteration must span whole number of cards");
  assert(is_humongous(), "only humongous region here");
  ShenandoahHeap* heap = ShenandoahHeap::heap();

  // Find head.
  ShenandoahHeapRegion* r = humongous_start_region();
  assert(r->is_humongous_start(), "need humongous head here");
  assert(CardTable::card_size_in_words() * (words / CardTable::card_size_in_words()) == words,
         "slice must be integral number of cards");

  oop obj = cast_to_oop(r->bottom());
  RememberedScanner* scanner = ShenandoahHeap::heap()->card_scan();
  size_t card_index = scanner->card_index_for_addr(start);
  size_t num_cards = words / CardTable::card_size_in_words();

  if (dirty_only) {
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
  } else {
    // Scan all data, regardless of whether cards are dirty
    obj->oop_iterate(blk, MemRegion(start, start + num_cards * CardTable::card_size_in_words()));
  }
}

void ShenandoahHeapRegion::oop_iterate_humongous(OopIterateClosure* blk, HeapWord* start, size_t words) {
  assert(is_humongous(), "only humongous region here");
  // Find head.
  ShenandoahHeapRegion* r = humongous_start_region();
  assert(r->is_humongous_start(), "need humongous head here");
  oop obj = cast_to_oop(r->bottom());
  obj->oop_iterate(blk, MemRegion(start, start + words));
}

void ShenandoahHeapRegion::oop_iterate_humongous(OopIterateClosure* blk) {
  assert(is_humongous(), "only humongous region here");
  // Find head.
  ShenandoahHeapRegion* r = humongous_start_region();
  assert(r->is_humongous_start(), "need humongous head here");
  oop obj = cast_to_oop(r->bottom());
  obj->oop_iterate(blk, MemRegion(bottom(), top()));
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

void ShenandoahHeapRegion::recycle() {
  shenandoah_assert_heaplocked();
  ShenandoahHeap* heap = ShenandoahHeap::heap();
  ShenandoahGeneration* generation = heap->generation_for(affiliation());
  heap->decrease_used(generation, used());

  set_top(bottom());
  clear_live_data();

  reset_alloc_metadata();

  heap->marking_context()->reset_top_at_mark_start(this);
  set_update_watermark(bottom());

  make_empty();
  ShenandoahHeap::heap()->generation_for(affiliation())->decrement_affiliated_region_count();
  set_affiliation(FREE);
  if (ZapUnusedHeapArea) {
    SpaceMangler::mangle_region(MemRegion(bottom(), end()));
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
      err_msg message("Max heap size (" SIZE_FORMAT "%s) is too low to afford the minimum number "
                      "of regions (" SIZE_FORMAT ") of minimum region size (" SIZE_FORMAT "%s).",
                      byte_size_in_proper_unit(max_heap_size), proper_unit_for_byte_size(max_heap_size),
                      MIN_NUM_REGIONS,
                      byte_size_in_proper_unit(ShenandoahMinRegionSize), proper_unit_for_byte_size(ShenandoahMinRegionSize));
      vm_exit_during_initialization("Invalid -XX:ShenandoahMinRegionSize option", message);
    }
    if (ShenandoahMinRegionSize < MIN_REGION_SIZE) {
      err_msg message("" SIZE_FORMAT "%s should not be lower than minimum region size (" SIZE_FORMAT "%s).",
                      byte_size_in_proper_unit(ShenandoahMinRegionSize), proper_unit_for_byte_size(ShenandoahMinRegionSize),
                      byte_size_in_proper_unit(MIN_REGION_SIZE),         proper_unit_for_byte_size(MIN_REGION_SIZE));
      vm_exit_during_initialization("Invalid -XX:ShenandoahMinRegionSize option", message);
    }
    if (ShenandoahMinRegionSize < MinTLABSize) {
      err_msg message("" SIZE_FORMAT "%s should not be lower than TLAB size size (" SIZE_FORMAT "%s).",
                      byte_size_in_proper_unit(ShenandoahMinRegionSize), proper_unit_for_byte_size(ShenandoahMinRegionSize),
                      byte_size_in_proper_unit(MinTLABSize),             proper_unit_for_byte_size(MinTLABSize));
      vm_exit_during_initialization("Invalid -XX:ShenandoahMinRegionSize option", message);
    }
    if (ShenandoahMaxRegionSize < MIN_REGION_SIZE) {
      err_msg message("" SIZE_FORMAT "%s should not be lower than min region size (" SIZE_FORMAT "%s).",
                      byte_size_in_proper_unit(ShenandoahMaxRegionSize), proper_unit_for_byte_size(ShenandoahMaxRegionSize),
                      byte_size_in_proper_unit(MIN_REGION_SIZE),         proper_unit_for_byte_size(MIN_REGION_SIZE));
      vm_exit_during_initialization("Invalid -XX:ShenandoahMaxRegionSize option", message);
    }
    if (ShenandoahMinRegionSize > ShenandoahMaxRegionSize) {
      err_msg message("Minimum (" SIZE_FORMAT "%s) should be larger than maximum (" SIZE_FORMAT "%s).",
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
      err_msg message("Max heap size (" SIZE_FORMAT "%s) is too low to afford the minimum number "
                              "of regions (" SIZE_FORMAT ") of requested size (" SIZE_FORMAT "%s).",
                      byte_size_in_proper_unit(max_heap_size), proper_unit_for_byte_size(max_heap_size),
                      MIN_NUM_REGIONS,
                      byte_size_in_proper_unit(ShenandoahRegionSize), proper_unit_for_byte_size(ShenandoahRegionSize));
      vm_exit_during_initialization("Invalid -XX:ShenandoahRegionSize option", message);
    }
    if (ShenandoahRegionSize < ShenandoahMinRegionSize) {
      err_msg message("Heap region size (" SIZE_FORMAT "%s) should be larger than min region size (" SIZE_FORMAT "%s).",
                      byte_size_in_proper_unit(ShenandoahRegionSize), proper_unit_for_byte_size(ShenandoahRegionSize),
                      byte_size_in_proper_unit(ShenandoahMinRegionSize),  proper_unit_for_byte_size(ShenandoahMinRegionSize));
      vm_exit_during_initialization("Invalid -XX:ShenandoahRegionSize option", message);
    }
    if (ShenandoahRegionSize > ShenandoahMaxRegionSize) {
      err_msg message("Heap region size (" SIZE_FORMAT "%s) should be lower than max region size (" SIZE_FORMAT "%s).",
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

  guarantee(HumongousThresholdWords == 0, "we should only set it once");
  HumongousThresholdWords = RegionSizeWords * ShenandoahHumongousThreshold / 100;
  HumongousThresholdWords = align_down(HumongousThresholdWords, MinObjAlignment);
  assert (HumongousThresholdWords <= RegionSizeWords, "sanity");

  guarantee(HumongousThresholdBytes == 0, "we should only set it once");
  HumongousThresholdBytes = HumongousThresholdWords * HeapWordSize;
  assert (HumongousThresholdBytes <= RegionSizeBytes, "sanity");

  // The rationale for trimming the TLAB sizes has to do with the raciness in
  // TLAB allocation machinery. It may happen that TLAB sizing policy polls Shenandoah
  // about next free size, gets the answer for region #N, goes away for a while, then
  // tries to allocate in region #N, and fail because some other thread have claimed part
  // of the region #N, and then the freeset allocation code has to retire the region #N,
  // before moving the allocation to region #N+1.
  //
  // The worst case realizes when "answer" is "region size", which means it could
  // prematurely retire an entire region. Having smaller TLABs does not fix that
  // completely, but reduces the probability of too wasteful region retirement.
  // With current divisor, we will waste no more than 1/8 of region size in the worst
  // case. This also has a secondary effect on collection set selection: even under
  // the race, the regions would be at least 7/8 used, which allows relying on
  // "used" - "live" for cset selection. Otherwise, we can get the fragmented region
  // below the garbage threshold that would never be considered for collection.
  //
  // The whole thing is mitigated if Elastic TLABs are enabled.
  //
  guarantee(MaxTLABSizeWords == 0, "we should only set it once");
  MaxTLABSizeWords = MIN2(ShenandoahElasticTLAB ? RegionSizeWords : (RegionSizeWords / 8), HumongousThresholdWords);
  MaxTLABSizeWords = align_down(MaxTLABSizeWords, MinObjAlignment);

  guarantee(MaxTLABSizeBytes == 0, "we should only set it once");
  MaxTLABSizeBytes = MaxTLABSizeWords * HeapWordSize;
  assert (MaxTLABSizeBytes > MinTLABSize, "should be larger");

  return max_heap_size;
}

void ShenandoahHeapRegion::do_commit() {
  ShenandoahHeap* heap = ShenandoahHeap::heap();
  if (!heap->is_heap_region_special() && !os::commit_memory((char *) bottom(), RegionSizeBytes, false)) {
    report_java_out_of_memory("Unable to commit region");
  }
  if (!heap->commit_bitmap_slice(this)) {
    report_java_out_of_memory("Unable to commit bitmaps for region");
  }
  if (AlwaysPreTouch) {
    os::pretouch_memory(bottom(), end(), heap->pretouch_heap_page_size());
  }
  heap->increase_committed(ShenandoahHeapRegion::region_size_bytes());
}

void ShenandoahHeapRegion::do_uncommit() {
  ShenandoahHeap* heap = ShenandoahHeap::heap();
  if (!heap->is_heap_region_special() && !os::uncommit_memory((char *) bottom(), RegionSizeBytes)) {
    report_java_out_of_memory("Unable to uncommit region");
  }
  if (!heap->uncommit_bitmap_slice(this)) {
    report_java_out_of_memory("Unable to uncommit bitmaps for region");
  }
  heap->decrease_committed(ShenandoahHeapRegion::region_size_bytes());
}

void ShenandoahHeapRegion::set_state(RegionState to) {
  EventShenandoahHeapRegionStateChange evt;
  if (evt.should_commit()){
    evt.set_index((unsigned) index());
    evt.set_start((uintptr_t)bottom());
    evt.set_used(used());
    evt.set_from(_state);
    evt.set_to(to);
    evt.commit();
  }
  _state = to;
}

void ShenandoahHeapRegion::record_pin() {
  Atomic::add(&_critical_pins, (size_t)1);
}

void ShenandoahHeapRegion::record_unpin() {
  assert(pin_count() > 0, "Region " SIZE_FORMAT " should have non-zero pins", index());
  Atomic::sub(&_critical_pins, (size_t)1);
}

size_t ShenandoahHeapRegion::pin_count() const {
  return Atomic::load(&_critical_pins);
}

void ShenandoahHeapRegion::set_affiliation(ShenandoahAffiliation new_affiliation) {
  ShenandoahHeap* heap = ShenandoahHeap::heap();

  ShenandoahAffiliation region_affiliation = heap->region_affiliation(this);
  {
    ShenandoahMarkingContext* const ctx = heap->complete_marking_context();
    log_debug(gc)("Setting affiliation of Region " SIZE_FORMAT " from %s to %s, top: " PTR_FORMAT ", TAMS: " PTR_FORMAT
                  ", watermark: " PTR_FORMAT ", top_bitmap: " PTR_FORMAT,
                  index(), shenandoah_affiliation_name(region_affiliation), shenandoah_affiliation_name(new_affiliation),
                  p2i(top()), p2i(ctx->top_at_mark_start(this)), p2i(_update_watermark), p2i(ctx->top_bitmap(this)));
  }

#ifdef ASSERT
  {
    // During full gc, heap->complete_marking_context() is not valid, may equal nullptr.
    ShenandoahMarkingContext* const ctx = heap->complete_marking_context();
    size_t idx = this->index();
    HeapWord* top_bitmap = ctx->top_bitmap(this);

    assert(ctx->is_bitmap_clear_range(top_bitmap, _end),
           "Region " SIZE_FORMAT ", bitmap should be clear between top_bitmap: " PTR_FORMAT " and end: " PTR_FORMAT, idx,
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
      // TODO: should we reset_age() for OLD as well?  Examine invocations of set_affiliation(). Some contexts redundantly
      //       invoke reset_age().
      break;
    default:
      ShouldNotReachHere();
      return;
  }
  heap->set_affiliation(this, new_affiliation);
}

// When we promote a region in place, we can continue to use the established marking context to guide subsequent remembered
// set scans of this region's content.  The region will be coalesced and filled prior to the next old-gen marking effort.
// We identify the entirety of the region as DIRTY to force the next remembered set scan to identify the "interesting poitners"
// contained herein.
void ShenandoahHeapRegion::promote_in_place() {
  ShenandoahHeap* heap = ShenandoahHeap::heap();
  ShenandoahMarkingContext* marking_context = heap->marking_context();
  HeapWord* tams = marking_context->top_at_mark_start(this);
  assert(heap->active_generation()->is_mark_complete(), "sanity");
  assert(!heap->is_concurrent_old_mark_in_progress(), "Cannot promote in place during old marking");
  assert(is_young(), "Only young regions can be promoted");
  assert(is_regular(), "Use different service to promote humongous regions");
  assert(age() >= heap->age_census()->tenuring_threshold(), "Only promote regions that are sufficiently aged");

  ShenandoahOldGeneration* old_gen = heap->old_generation();
  ShenandoahYoungGeneration* young_gen = heap->young_generation();
  size_t region_size_bytes = ShenandoahHeapRegion::region_size_bytes();

  assert(get_top_before_promote() == tams, "Cannot promote regions in place if top has advanced beyond TAMS");

  // Rebuild the remembered set information and mark the entire range as DIRTY.  We do NOT scan the content of this
  // range to determine which cards need to be DIRTY.  That would force us to scan the region twice, once now, and
  // once during the subsequent remembered set scan.  Instead, we blindly (conservatively) mark everything as DIRTY
  // now and then sort out the CLEAN pages during the next remembered set scan.
  //
  // Rebuilding the remembered set consists of clearing all object registrations (reset_object_range()) here,
  // then registering every live object and every coalesced range of free objects in the loop that follows.
  heap->card_scan()->reset_object_range(bottom(), end());
  heap->card_scan()->mark_range_as_dirty(bottom(), get_top_before_promote() - bottom());

  // TODO: use an existing coalesce-and-fill function rather than replicating the code here.
  HeapWord* obj_addr = bottom();
  while (obj_addr < tams) {
    oop obj = cast_to_oop(obj_addr);
    if (marking_context->is_marked(obj)) {
      assert(obj->klass() != nullptr, "klass should not be NULL");
      // This thread is responsible for registering all objects in this region.  No need for lock.
      heap->card_scan()->register_object_without_lock(obj_addr);
      obj_addr += obj->size();
    } else {
      HeapWord* next_marked_obj = marking_context->get_next_marked_addr(obj_addr, tams);
      assert(next_marked_obj <= tams, "next marked object cannot exceed tams");
      size_t fill_size = next_marked_obj - obj_addr;
      assert(fill_size >= ShenandoahHeap::min_fill_size(), "previously allocated objects known to be larger than min_size");
      ShenandoahHeap::fill_with_object(obj_addr, fill_size);
      heap->card_scan()->register_object_without_lock(obj_addr);
      obj_addr = next_marked_obj;
    }
  }
  // We do not need to scan above TAMS because restored top equals tams
  assert(obj_addr == tams, "Expect loop to terminate when obj_addr equals tams");

  {
    ShenandoahHeapLocker locker(heap->lock());

    HeapWord* update_watermark = get_update_watermark();

    // Now that this region is affiliated with old, we can allow it to receive allocations, though it may not be in the
    // is_collector_free range.
    restore_top_before_promote();

    size_t region_capacity = free();
    size_t region_used = used();

    // The update_watermark was likely established while we had the artificially high value of top.  Make it sane now.
    assert(update_watermark >= top(), "original top cannot exceed preserved update_watermark");
    set_update_watermark(top());

    // Unconditionally transfer one region from young to old to represent the newly promoted region.
    // This expands old and shrinks new by the size of one region.  Strictly, we do not "need" to expand old
    // if there are already enough unaffiliated regions in old to account for this newly promoted region.
    // However, if we do not transfer the capacities, we end up reducing the amount of memory that would have
    // otherwise been available to hold old evacuations, because old available is max_capacity - used and now
    // we would be trading a fully empty region for a partially used region.

    young_gen->decrease_used(region_used);
    young_gen->decrement_affiliated_region_count();

    // transfer_to_old() increases capacity of old and decreases capacity of young
    heap->generation_sizer()->force_transfer_to_old(1);
    set_affiliation(OLD_GENERATION);

    old_gen->increment_affiliated_region_count();
    old_gen->increase_used(region_used);

    // add_old_collector_free_region() increases promoted_reserve() if available space exceeds PLAB::min_size()
    heap->free_set()->add_old_collector_free_region(this);
  }
}

void ShenandoahHeapRegion::promote_humongous() {
  ShenandoahHeap* heap = ShenandoahHeap::heap();
  ShenandoahMarkingContext* marking_context = heap->marking_context();
  assert(heap->active_generation()->is_mark_complete(), "sanity");
  assert(is_young(), "Only young regions can be promoted");
  assert(is_humongous_start(), "Should not promote humongous continuation in isolation");
  assert(age() >= heap->age_census()->tenuring_threshold(), "Only promote regions that are sufficiently aged");

  ShenandoahGeneration* old_generation = heap->old_generation();
  ShenandoahGeneration* young_generation = heap->young_generation();

  oop obj = cast_to_oop(bottom());
  assert(marking_context->is_marked(obj), "promoted humongous object should be alive");

  // TODO: Consider not promoting humongous objects that represent primitive arrays.  Leaving a primitive array
  // (obj->is_typeArray()) in young-gen is harmless because these objects are never relocated and they are not
  // scanned.  Leaving primitive arrays in young-gen memory allows their memory to be reclaimed more quickly when
  // it becomes garbage.  Better to not make this change until sizes of young-gen and old-gen are completely
  // adaptive, as leaving primitive arrays in young-gen might be perceived as an "astonishing result" by someone
  // has carefully analyzed the required sizes of an application's young-gen and old-gen.
  size_t used_bytes = obj->size() * HeapWordSize;
  size_t spanned_regions = ShenandoahHeapRegion::required_regions(used_bytes);
  size_t humongous_waste = spanned_regions * ShenandoahHeapRegion::region_size_bytes() - obj->size() * HeapWordSize;
  size_t index_limit = index() + spanned_regions;
  {
    // We need to grab the heap lock in order to avoid a race when changing the affiliations of spanned_regions from
    // young to old.
    ShenandoahHeapLocker locker(heap->lock());

    // We promote humongous objects unconditionally, without checking for availability.  We adjust
    // usage totals, including humongous waste, after evacuation is done.
    log_debug(gc)("promoting humongous region " SIZE_FORMAT ", spanning " SIZE_FORMAT, index(), spanned_regions);

    young_generation->decrease_used(used_bytes);
    young_generation->decrease_humongous_waste(humongous_waste);
    young_generation->decrease_affiliated_region_count(spanned_regions);

    // transfer_to_old() increases capacity of old and decreases capacity of young
    heap->generation_sizer()->force_transfer_to_old(spanned_regions);

    // For this region and each humongous continuation region spanned by this humongous object, change
    // affiliation to OLD_GENERATION and adjust the generation-use tallies.  The remnant of memory
    // in the last humongous region that is not spanned by obj is currently not used.
    for (size_t i = index(); i < index_limit; i++) {
      ShenandoahHeapRegion* r = heap->get_region(i);
      log_debug(gc)("promoting humongous region " SIZE_FORMAT ", from " PTR_FORMAT " to " PTR_FORMAT,
                    r->index(), p2i(r->bottom()), p2i(r->top()));
      // We mark the entire humongous object's range as dirty after loop terminates, so no need to dirty the range here
      r->set_affiliation(OLD_GENERATION);
    }

    old_generation->increase_affiliated_region_count(spanned_regions);
    old_generation->increase_used(used_bytes);
    old_generation->increase_humongous_waste(humongous_waste);
  }

  // Since this region may have served previously as OLD, it may hold obsolete object range info.
  heap->card_scan()->reset_object_range(bottom(), bottom() + spanned_regions * ShenandoahHeapRegion::region_size_words());
  // Since the humongous region holds only one object, no lock is necessary for this register_object() invocation.
  heap->card_scan()->register_object_without_lock(bottom());

  if (obj->is_typeArray()) {
    // Primitive arrays don't need to be scanned.
    log_debug(gc)("Clean cards for promoted humongous object (Region " SIZE_FORMAT ") from " PTR_FORMAT " to " PTR_FORMAT,
                  index(), p2i(bottom()), p2i(bottom() + obj->size()));
    heap->card_scan()->mark_range_as_clean(bottom(), obj->size());
  } else {
    log_debug(gc)("Dirty cards for promoted humongous object (Region " SIZE_FORMAT ") from " PTR_FORMAT " to " PTR_FORMAT,
                  index(), p2i(bottom()), p2i(bottom() + obj->size()));
    heap->card_scan()->mark_range_as_dirty(bottom(), obj->size());
  }
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
