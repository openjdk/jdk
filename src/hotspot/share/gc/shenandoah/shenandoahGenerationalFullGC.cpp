/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/shared/fullGCForwarding.inline.hpp"
#include "gc/shared/preservedMarks.inline.hpp"
#include "gc/shenandoah/shenandoahGeneration.hpp"
#include "gc/shenandoah/shenandoahGenerationalFullGC.hpp"
#include "gc/shenandoah/shenandoahGenerationalHeap.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahHeapRegion.hpp"
#include "gc/shenandoah/shenandoahOldGeneration.hpp"
#include "gc/shenandoah/shenandoahUtils.hpp"
#include "gc/shenandoah/shenandoahYoungGeneration.hpp"

#ifdef ASSERT
void assert_regions_used_not_more_than_capacity(ShenandoahGeneration* generation) {
  assert(generation->used_regions_size() <= generation->max_capacity(),
         "%s generation affiliated regions must be less than capacity", generation->name());
}

void assert_usage_not_more_than_regions_used(ShenandoahGeneration* generation) {
  assert(generation->used() <= generation->used_regions_size(),
         "%s consumed can be no larger than span of affiliated regions", generation->name());
}
#else
void assert_regions_used_not_more_than_capacity(ShenandoahGeneration* generation) {}
void assert_usage_not_more_than_regions_used(ShenandoahGeneration* generation) {}
#endif


void ShenandoahGenerationalFullGC::prepare() {
  auto heap = ShenandoahGenerationalHeap::heap();
  // Since we may arrive here from degenerated GC failure of either young or old, establish generation as GLOBAL.
  heap->set_active_generation(heap->global_generation());

  // Full GC supersedes any marking or coalescing in old generation.
  heap->old_generation()->cancel_gc();
}

void ShenandoahGenerationalFullGC::handle_completion(ShenandoahHeap* heap) {
  // Full GC should reset time since last gc for young and old heuristics
  ShenandoahGenerationalHeap* gen_heap = ShenandoahGenerationalHeap::cast(heap);
  ShenandoahYoungGeneration* young = gen_heap->young_generation();
  ShenandoahOldGeneration* old = gen_heap->old_generation();
  young->heuristics()->record_cycle_end();
  old->heuristics()->record_cycle_end();

  gen_heap->mmu_tracker()->record_full(GCId::current());
  gen_heap->log_heap_status("At end of Full GC");

  assert(old->is_idle(), "After full GC, old generation should be idle.");

  // Since we allow temporary violation of these constraints during Full GC, we want to enforce that the assertions are
  // made valid by the time Full GC completes.
  assert_regions_used_not_more_than_capacity(old);
  assert_regions_used_not_more_than_capacity(young);
  assert_usage_not_more_than_regions_used(old);
  assert_usage_not_more_than_regions_used(young);

  // Establish baseline for next old-has-grown trigger.
  old->set_live_bytes_at_last_mark(old->used());
}

void ShenandoahGenerationalFullGC::rebuild_remembered_set(ShenandoahHeap* heap) {
  ShenandoahGCPhase phase(ShenandoahPhaseTimings::full_gc_reconstruct_remembered_set);

  ShenandoahScanRemembered* scanner = heap->old_generation()->card_scan();
  scanner->mark_read_table_as_clean();
  scanner->swap_card_tables();

  ShenandoahRegionIterator regions;
  ShenandoahReconstructRememberedSetTask task(&regions);
  heap->workers()->run_task(&task);

  // Rebuilding the remembered set recomputes all the card offsets for objects.
  // The adjust pointers phase coalesces and fills all necessary regions. In case
  // we came to the full GC from an incomplete global cycle, we need to indicate
  // that the old regions are parsable.
  heap->old_generation()->set_parsable(true);
}

void ShenandoahGenerationalFullGC::log_live_in_old(ShenandoahHeap* heap) {
  LogTarget(Debug, gc) lt;
  if (lt.is_enabled()) {
    size_t live_bytes_in_old = 0;
    for (size_t i = 0; i < heap->num_regions(); i++) {
      ShenandoahHeapRegion* r = heap->get_region(i);
      if (r->is_old()) {
        live_bytes_in_old += r->get_live_data_bytes();
      }
    }
    log_debug(gc)("Live bytes in old after STW mark: " PROPERFMT, PROPERFMTARGS(live_bytes_in_old));
  }
}

void ShenandoahGenerationalFullGC::restore_top_before_promote(ShenandoahHeap* heap) {
  for (size_t i = 0; i < heap->num_regions(); i++) {
    ShenandoahHeapRegion* r = heap->get_region(i);
    if (r->get_top_before_promote() != nullptr) {
      r->restore_top_before_promote();
    }
  }
}

void ShenandoahGenerationalFullGC::account_for_region(ShenandoahHeapRegion* r, size_t &region_count, size_t &region_usage, size_t &humongous_waste) {
  region_count++;
  region_usage += r->used();
  if (r->is_humongous_start()) {
    // For each humongous object, we take this path once regardless of how many regions it spans.
    HeapWord* obj_addr = r->bottom();
    oop obj = cast_to_oop(obj_addr);
    size_t word_size = obj->size();
    size_t region_size_words = ShenandoahHeapRegion::region_size_words();
    size_t overreach = word_size % region_size_words;
    if (overreach != 0) {
      humongous_waste += (region_size_words - overreach) * HeapWordSize;
    }
    // else, this humongous object aligns exactly on region size, so no waste.
  }
}

void ShenandoahGenerationalFullGC::maybe_coalesce_and_fill_region(ShenandoahHeapRegion* r) {
  if (r->is_pinned() && r->is_old() && r->is_active() && !r->is_humongous()) {
    r->begin_preemptible_coalesce_and_fill();
    r->oop_coalesce_and_fill(false);
  }
}

void ShenandoahGenerationalFullGC::compute_balances() {
  auto heap = ShenandoahGenerationalHeap::heap();

  // In case this Full GC resulted from degeneration, clear the tally on anticipated promotion.
  heap->old_generation()->set_promotion_potential(0);

  // Invoke this in case we are able to transfer memory from OLD to YOUNG
  size_t allocation_runway =
    heap->young_generation()->heuristics()->bytes_of_allocation_runway_before_gc_trigger(0L);
  heap->compute_old_generation_balance(allocation_runway, 0, 0);
}

ShenandoahPrepareForGenerationalCompactionObjectClosure::ShenandoahPrepareForGenerationalCompactionObjectClosure(PreservedMarks* preserved_marks,
                                                          GrowableArray<ShenandoahHeapRegion*>& empty_regions,
                                                          ShenandoahHeapRegion* from_region, uint worker_id) :
        _preserved_marks(preserved_marks),
        _heap(ShenandoahGenerationalHeap::heap()),
        _empty_regions(empty_regions),
        _empty_regions_pos(0),
        _old_to_region(nullptr),
        _young_to_region(nullptr),
        _from_region(nullptr),
        _from_affiliation(ShenandoahAffiliation::FREE),
        _old_compact_point(nullptr),
        _young_compact_point(nullptr),
        _worker_id(worker_id) {
  assert(from_region != nullptr, "Worker needs from_region");
  // assert from_region has live?
  if (from_region->is_old()) {
    _old_to_region = from_region;
    _old_compact_point = from_region->bottom();
  } else if (from_region->is_young()) {
    _young_to_region = from_region;
    _young_compact_point = from_region->bottom();
  }
}

void ShenandoahPrepareForGenerationalCompactionObjectClosure::set_from_region(ShenandoahHeapRegion* from_region) {
  log_debug(gc)("Worker %u compacting %s Region %zu which had used %zu and %s live",
                _worker_id, from_region->affiliation_name(),
                from_region->index(), from_region->used(), from_region->has_live()? "has": "does not have");

  _from_region = from_region;
  _from_affiliation = from_region->affiliation();
  if (_from_region->has_live()) {
    if (_from_affiliation == ShenandoahAffiliation::OLD_GENERATION) {
      if (_old_to_region == nullptr) {
        _old_to_region = from_region;
        _old_compact_point = from_region->bottom();
      }
    } else {
      assert(_from_affiliation == ShenandoahAffiliation::YOUNG_GENERATION, "from_region must be OLD or YOUNG");
      if (_young_to_region == nullptr) {
        _young_to_region = from_region;
        _young_compact_point = from_region->bottom();
      }
    }
  } // else, we won't iterate over this _from_region so we don't need to set up to region to hold copies
}

void ShenandoahPrepareForGenerationalCompactionObjectClosure::finish() {
  finish_old_region();
  finish_young_region();
}

void ShenandoahPrepareForGenerationalCompactionObjectClosure::finish_old_region() {
  if (_old_to_region != nullptr) {
    log_debug(gc)("Planned compaction into Old Region %zu, used: %zu tabulated by worker %u",
            _old_to_region->index(), _old_compact_point - _old_to_region->bottom(), _worker_id);
    _old_to_region->set_new_top(_old_compact_point);
    _old_to_region = nullptr;
  }
}

void ShenandoahPrepareForGenerationalCompactionObjectClosure::finish_young_region() {
  if (_young_to_region != nullptr) {
    log_debug(gc)("Worker %u planned compaction into Young Region %zu, used: %zu",
            _worker_id, _young_to_region->index(), _young_compact_point - _young_to_region->bottom());
    _young_to_region->set_new_top(_young_compact_point);
    _young_to_region = nullptr;
  }
}

bool ShenandoahPrepareForGenerationalCompactionObjectClosure::is_compact_same_region() {
  return (_from_region == _old_to_region) || (_from_region == _young_to_region);
}

void ShenandoahPrepareForGenerationalCompactionObjectClosure::do_object(oop p) {
  assert(_from_region != nullptr, "must set before work");
  assert((_from_region->bottom() <= cast_from_oop<HeapWord*>(p)) && (cast_from_oop<HeapWord*>(p) < _from_region->top()),
         "Object must reside in _from_region");
  assert(_heap->global_generation()->complete_marking_context()->is_marked(p), "must be marked");
  assert(!_heap->global_generation()->complete_marking_context()->allocated_after_mark_start(p), "must be truly marked");

  size_t obj_size = p->size();
  uint from_region_age = _from_region->age();
  uint object_age = p->age();

  bool promote_object = false;
  if ((_from_affiliation == ShenandoahAffiliation::YOUNG_GENERATION) &&
      _heap->age_census()->is_tenurable(from_region_age + object_age)) {
    if ((_old_to_region != nullptr) && (_old_compact_point + obj_size > _old_to_region->end())) {
      finish_old_region();
      _old_to_region = nullptr;
    }
    if (_old_to_region == nullptr) {
      if (_empty_regions_pos < _empty_regions.length()) {
        ShenandoahHeapRegion* new_to_region = _empty_regions.at(_empty_regions_pos);
        _empty_regions_pos++;
        new_to_region->set_affiliation(OLD_GENERATION);
        _old_to_region = new_to_region;
        _old_compact_point = _old_to_region->bottom();
        promote_object = true;
      }
      // Else this worker thread does not yet have any empty regions into which this aged object can be promoted so
      // we leave promote_object as false, deferring the promotion.
    } else {
      promote_object = true;
    }
  }

  if (promote_object || (_from_affiliation == ShenandoahAffiliation::OLD_GENERATION)) {
    assert(_old_to_region != nullptr, "_old_to_region should not be nullptr when evacuating to OLD region");
    if (_old_compact_point + obj_size > _old_to_region->end()) {
      ShenandoahHeapRegion* new_to_region;

      log_debug(gc)("Worker %u finishing old region %zu, compact_point: " PTR_FORMAT ", obj_size: %zu"
      ", &compact_point[obj_size]: " PTR_FORMAT ", region end: " PTR_FORMAT,  _worker_id, _old_to_region->index(),
              p2i(_old_compact_point), obj_size, p2i(_old_compact_point + obj_size), p2i(_old_to_region->end()));

      // Object does not fit.  Get a new _old_to_region.
      finish_old_region();
      if (_empty_regions_pos < _empty_regions.length()) {
        new_to_region = _empty_regions.at(_empty_regions_pos);
        _empty_regions_pos++;
        new_to_region->set_affiliation(OLD_GENERATION);
      } else {
        // If we've exhausted the previously selected _old_to_region, we know that the _old_to_region is distinct
        // from _from_region.  That's because there is always room for _from_region to be compacted into itself.
        // Since we're out of empty regions, let's use _from_region to hold the results of its own compaction.
        new_to_region = _from_region;
      }

      assert(new_to_region != _old_to_region, "must not reuse same OLD to-region");
      assert(new_to_region != nullptr, "must not be nullptr");
      _old_to_region = new_to_region;
      _old_compact_point = _old_to_region->bottom();
    }

    // Object fits into current region, record new location, if object does not move:
    assert(_old_compact_point + obj_size <= _old_to_region->end(), "must fit");
    shenandoah_assert_not_forwarded(nullptr, p);
    if (_old_compact_point != cast_from_oop<HeapWord*>(p)) {
      _preserved_marks->push_if_necessary(p, p->mark());
      FullGCForwarding::forward_to(p, cast_to_oop(_old_compact_point));
    }
    _old_compact_point += obj_size;
  } else {
    assert(_from_affiliation == ShenandoahAffiliation::YOUNG_GENERATION,
           "_from_region must be OLD_GENERATION or YOUNG_GENERATION");
    assert(_young_to_region != nullptr, "_young_to_region should not be nullptr when compacting YOUNG _from_region");

    // After full gc compaction, all regions have age 0.  Embed the region's age into the object's age in order to preserve
    // tenuring progress.
    if (_heap->is_aging_cycle()) {
      ShenandoahHeap::increase_object_age(p, from_region_age + 1);
    } else {
      ShenandoahHeap::increase_object_age(p, from_region_age);
    }

    if (_young_compact_point + obj_size > _young_to_region->end()) {
      ShenandoahHeapRegion* new_to_region;

      log_debug(gc)("Worker %u finishing young region %zu, compact_point: " PTR_FORMAT ", obj_size: %zu"
      ", &compact_point[obj_size]: " PTR_FORMAT ", region end: " PTR_FORMAT,  _worker_id, _young_to_region->index(),
              p2i(_young_compact_point), obj_size, p2i(_young_compact_point + obj_size), p2i(_young_to_region->end()));

      // Object does not fit.  Get a new _young_to_region.
      finish_young_region();
      if (_empty_regions_pos < _empty_regions.length()) {
        new_to_region = _empty_regions.at(_empty_regions_pos);
        _empty_regions_pos++;
        new_to_region->set_affiliation(YOUNG_GENERATION);
      } else {
        // If we've exhausted the previously selected _young_to_region, we know that the _young_to_region is distinct
        // from _from_region.  That's because there is always room for _from_region to be compacted into itself.
        // Since we're out of empty regions, let's use _from_region to hold the results of its own compaction.
        new_to_region = _from_region;
      }

      assert(new_to_region != _young_to_region, "must not reuse same OLD to-region");
      assert(new_to_region != nullptr, "must not be nullptr");
      _young_to_region = new_to_region;
      _young_compact_point = _young_to_region->bottom();
    }

    // Object fits into current region, record new location, if object does not move:
    assert(_young_compact_point + obj_size <= _young_to_region->end(), "must fit");
    shenandoah_assert_not_forwarded(nullptr, p);

    if (_young_compact_point != cast_from_oop<HeapWord*>(p)) {
      _preserved_marks->push_if_necessary(p, p->mark());
      FullGCForwarding::forward_to(p, cast_to_oop(_young_compact_point));
    }
    _young_compact_point += obj_size;
  }
}
