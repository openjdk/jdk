/*
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

#include "gc/shared/plab.hpp"
#include "gc/shenandoah/shenandoahFreeSet.hpp"
#include "gc/shenandoah/shenandoahGenerationalHeap.inline.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahHeapRegion.inline.hpp"
#include "gc/shenandoah/shenandoahInPlacePromoter.hpp"
#include "gc/shenandoah/shenandoahMarkingContext.hpp"
#include "gc/shenandoah/shenandoahOldGeneration.hpp"
#include "gc/shenandoah/shenandoahYoungGeneration.hpp"

ShenandoahInPlacePromotionPlanner::RegionPromotions::RegionPromotions(ShenandoahFreeSet* free_set)
  : _low_idx(free_set->max_regions())
  , _high_idx(-1)
  , _regions(0)
  , _bytes(0)
  , _free_set(free_set)
{
}

void ShenandoahInPlacePromotionPlanner::RegionPromotions::increment(idx_t region_index, size_t remnant_bytes) {
  if (region_index < _low_idx) {
    _low_idx = region_index;
  }
  if (region_index > _high_idx) {
    _high_idx = region_index;
  }
  _regions++;
  _bytes += remnant_bytes;
}

void ShenandoahInPlacePromotionPlanner::RegionPromotions::update_free_set(ShenandoahFreeSetPartitionId partition_id) const {
  if (_regions > 0) {
    _free_set->shrink_interval_if_range_modifies_either_boundary(partition_id, _low_idx, _high_idx, _regions);
  }
}

ShenandoahInPlacePromotionPlanner::ShenandoahInPlacePromotionPlanner(const ShenandoahGenerationalHeap* heap)
  : _old_garbage_threshold(ShenandoahHeapRegion::region_size_bytes() * heap->old_generation()->heuristics()->get_old_garbage_threshold() / 100)
  , _pip_used_threshold(ShenandoahHeapRegion::region_size_bytes() * ShenandoahGenerationalMinPIPUsage / 100)
  , _heap(heap)
  , _free_set(_heap->free_set())
  , _marking_context(_heap->marking_context())
  , _mutator_regions(_free_set)
  , _collector_regions(_free_set)
  , _pip_padding_bytes(0)
{
}

bool ShenandoahInPlacePromotionPlanner::is_eligible(const ShenandoahHeapRegion* region) const {
  return region->garbage() < _old_garbage_threshold && region->used() > _pip_used_threshold;
}

void ShenandoahInPlacePromotionPlanner::prepare(ShenandoahHeapRegion* r) {
  HeapWord* tams = _marking_context->top_at_mark_start(r);
  HeapWord* original_top = r->top();

  if (_heap->is_concurrent_mark_in_progress() || tams != original_top) {
    // We do not promote this region (either in place or by copy) because it has received new allocations.
    // During evacuation, we exclude from promotion regions for which age > tenure threshold, garbage < garbage-threshold,
    // used > pip_used_threshold, and get_top_before_promote() != tams.
    //  TODO: Such a region should have had its age reset to zero when it was used for allocation?
    return;
  }

  // No allocations from this region have been made during concurrent mark. It meets all the criteria
  // for in-place-promotion. Though we only need the value of top when we fill the end of the region,
  // we use this field to indicate that this region should be promoted in place during the evacuation
  // phase.
  r->save_top_before_promote();
  size_t remnant_bytes = r->free();
  size_t remnant_words = remnant_bytes / HeapWordSize;
  assert(ShenandoahHeap::min_fill_size() <= PLAB::min_size(), "Implementation makes invalid assumptions");
  if (remnant_words >= ShenandoahHeap::min_fill_size()) {
    ShenandoahHeap::fill_with_object(original_top, remnant_words);
    // Fill the remnant memory within this region to assure no allocations prior to promote in place.  Otherwise,
    // newly allocated objects will not be parsable when promote in place tries to register them.  Furthermore, any
    // new allocations would not necessarily be eligible for promotion.  This addresses both issues.
    r->set_top(r->end());
    // The region r is either in the Mutator or Collector partition if remnant_words > heap()->plab_min_size.
    // Otherwise, the region is in the NotFree partition.
    const idx_t i = r->index();
    ShenandoahFreeSetPartitionId p = _free_set->membership(i);
    if (p == ShenandoahFreeSetPartitionId::Mutator) {
      _mutator_regions.increment(i, remnant_bytes);
    } else if (p == ShenandoahFreeSetPartitionId::Collector) {
      _collector_regions.increment(i, remnant_bytes);
    } else {
      assert((p == ShenandoahFreeSetPartitionId::NotFree) && (remnant_words < _heap->plab_min_size()),
             "Should be NotFree if not in Collector or Mutator partitions");
      // In this case, the memory is already counted as used and the region has already been retired.  There is
      // no need for further adjustments to used.  Further, the remnant memory for this region will not be
      // unallocated or made available to OldCollector after pip.
      remnant_bytes = 0;
    }

    _pip_padding_bytes += remnant_bytes;
    _free_set->prepare_to_promote_in_place(i, remnant_bytes);
  } else {
    // Since the remnant is so small that this region has already been retired, we don't have to worry about any
    // accidental allocations occurring within this region before the region is promoted in place.

    // This region was already not in the Collector or Mutator set, so no need to remove it.
    assert(_free_set->membership(r->index()) == ShenandoahFreeSetPartitionId::NotFree, "sanity");
  }
}

void ShenandoahInPlacePromotionPlanner::update_free_set() const {
  _heap->old_generation()->set_pad_for_promote_in_place(_pip_padding_bytes);

  if (_mutator_regions._regions + _collector_regions._regions > 0) {
    _free_set->account_for_pip_regions(_mutator_regions._regions, _mutator_regions._bytes,
                         _collector_regions._regions, _collector_regions._bytes);
  }

  // Retire any regions that have been selected for promote in place
  _mutator_regions.update_free_set(ShenandoahFreeSetPartitionId::Mutator);
  _collector_regions.update_free_set(ShenandoahFreeSetPartitionId::Collector);
}

void ShenandoahInPlacePromoter::maybe_promote_region(ShenandoahHeapRegion* r) const {
  if (r->is_young() && r->is_active() && _heap->is_tenurable(r)) {
    if (r->is_humongous_start()) {
      // We promote humongous_start regions along with their affiliated continuations during evacuation rather than
      // doing this work during a safepoint.  We cannot put humongous regions into the collection set because that
      // triggers the load-reference barrier (LRB) to copy on reference fetch.
      //
      // Aged humongous continuation regions are handled with their start region.  If an aged regular region has
      // more garbage than ShenandoahOldGarbageThreshold, we'll promote by evacuation.  If there is room for evacuation
      // in this cycle, the region will be in the collection set.  If there is no room, the region will be promoted
      // by evacuation in some future GC cycle.

      // We do not promote primitive arrays because there's no performance penalty keeping them in young.  When/if they
      // become garbage, reclaiming the memory from young is much quicker and more efficient than reclaiming them from old.
      oop obj = cast_to_oop(r->bottom());
      if (!obj->is_typeArray()) {
        promote_humongous(r);
      }
    } else if (r->is_regular() && (r->get_top_before_promote() != nullptr)) {
      // Likewise, we cannot put promote-in-place regions into the collection set because that would also trigger
      // the LRB to copy on reference fetch.
      //
      // If an aged regular region has received allocations during the current cycle, we do not promote because the
      // newly allocated objects do not have appropriate age; this region's age will be reset to zero at end of cycle.
      promote(r);
    }
  }
}

// When we promote a region in place, we can continue to use the established marking context to guide subsequent remembered
// set scans of this region's content.  The region will be coalesced and filled prior to the next old-gen marking effort.
// We identify the entirety of the region as DIRTY to force the next remembered set scan to identify the "interesting pointers"
// contained herein.
void ShenandoahInPlacePromoter::promote(ShenandoahHeapRegion* region) const {

  ShenandoahMarkingContext* const marking_context = _heap->young_generation()->complete_marking_context();
  HeapWord* const tams = marking_context->top_at_mark_start(region);
  size_t region_size_bytes = ShenandoahHeapRegion::region_size_bytes();

  {
    const size_t old_garbage_threshold =
      (region_size_bytes * _heap->old_generation()->heuristics()->get_old_garbage_threshold()) / 100;
    assert(!_heap->is_concurrent_old_mark_in_progress(), "Cannot promote in place during old marking");
    assert(region->garbage_before_padded_for_promote() < old_garbage_threshold,
           "Region %zu has too much garbage for promotion", region->index());
    assert(region->is_young(), "Only young regions can be promoted");
    assert(region->is_regular(), "Use different service to promote humongous regions");
    assert(_heap->is_tenurable(region), "Only promote regions that are sufficiently aged");
    assert(region->get_top_before_promote() == tams, "Region %zu has been used for allocations before promotion", region->index());
  }

  ShenandoahOldGeneration* const old_gen = _heap->old_generation();

  // Rebuild the remembered set information and mark the entire range as DIRTY.  We do NOT scan the content of this
  // range to determine which cards need to be DIRTY.  That would force us to scan the region twice, once now, and
  // once during the subsequent remembered set scan.  Instead, we blindly (conservatively) mark everything as DIRTY
  // now and then sort out the CLEAN pages during the next remembered set scan.
  //
  // Rebuilding the remembered set consists of clearing all object registrations (reset_object_range()) here,
  // then registering every live object and every coalesced range of free objects in the loop that follows.
  ShenandoahScanRemembered* const scanner = old_gen->card_scan();
  scanner->reset_object_range(region->bottom(), region->end());
  scanner->mark_range_as_dirty(region->bottom(), region->get_top_before_promote() - region->bottom());

  HeapWord* obj_addr = region->bottom();
  while (obj_addr < tams) {
    oop obj = cast_to_oop(obj_addr);
    if (marking_context->is_marked(obj)) {
      assert(obj->klass() != nullptr, "klass should not be null");
      // This thread is responsible for registering all objects in this region.  No need for lock.
      scanner->register_object_without_lock(obj_addr);
      obj_addr += obj->size();
    } else {
      HeapWord* next_marked_obj = marking_context->get_next_marked_addr(obj_addr, tams);
      assert(next_marked_obj <= tams, "next marked object cannot exceed tams");
      size_t fill_size = next_marked_obj - obj_addr;
      assert(fill_size >= ShenandoahHeap::min_fill_size(), "previously allocated objects known to be larger than min_size");
      ShenandoahHeap::fill_with_object(obj_addr, fill_size);
      scanner->register_object_without_lock(obj_addr);
      obj_addr = next_marked_obj;
    }
  }
  // We do not need to scan above TAMS because restored top equals tams
  assert(obj_addr == tams, "Expect loop to terminate when obj_addr equals tams");


  {
    ShenandoahHeapLocker locker(_heap->lock());
#ifdef ASSERT
    HeapWord* update_watermark = region->get_update_watermark();
    // pip_unpadded is memory too small to be filled above original top
    size_t pip_unpadded = (region->end() - region->top()) * HeapWordSize;
    assert((region->top() == region->end())
           || (pip_unpadded == (size_t) ((region->end() - region->top()) * HeapWordSize)), "Invariant");
    assert(pip_unpadded < ShenandoahHeap::min_fill_size() * HeapWordSize, "Sanity");
    size_t pip_pad_bytes = (region->top() - region->get_top_before_promote()) * HeapWordSize;
    assert((pip_unpadded == 0) || (pip_pad_bytes == 0), "Only one of pip_unpadded and pip_pad_bytes is non-zero");
#endif

    // Now that this region is affiliated with old, we can allow it to receive allocations, though it may not be in the
    // is_collector_free range.  We'll add it to that range below.
    region->restore_top_before_promote();

    assert(region->used() + pip_pad_bytes + pip_unpadded == region_size_bytes, "invariant");

    // The update_watermark was likely established while we had the artificially high value of top.  Make it sane now.
    assert(update_watermark >= region->top(), "original top cannot exceed preserved update_watermark");
    region->set_update_watermark(region->top());

    // Transfer this region from young to old, increasing promoted_reserve if available space exceeds plab_min_size()
    _heap->free_set()->add_promoted_in_place_region_to_old_collector(region);
    region->set_affiliation(OLD_GENERATION);
    region->set_promoted_in_place();
  }
}

void ShenandoahInPlacePromoter::promote_humongous(ShenandoahHeapRegion* region) const {
  oop obj = cast_to_oop(region->bottom());

  assert(region->is_young(), "Only young regions can be promoted");
  assert(region->is_humongous_start(), "Should not promote humongous continuation in isolation");
  assert(_heap->is_tenurable(region), "Only promote regions that are sufficiently aged");
  assert(_heap->marking_context()->is_marked(obj), "Promoted humongous object should be alive");
  assert(!obj->is_typeArray(), "Don't promote humongous primitives");

  const size_t used_bytes = obj->size() * HeapWordSize;
  const size_t spanned_regions = ShenandoahHeapRegion::required_regions(used_bytes);
  const size_t region_size_bytes = ShenandoahHeapRegion::region_size_bytes();
  const size_t humongous_waste = spanned_regions * region_size_bytes - obj->size() * HeapWordSize;
  const size_t index_limit = region->index() + spanned_regions;

  ShenandoahOldGeneration* const old_gen = _heap->old_generation();
  {
    // We need to grab the heap lock in order to avoid a race when changing the affiliations of spanned_regions from
    // young to old.
    ShenandoahHeapLocker locker(_heap->lock());

    // We promote humongous objects unconditionally, without checking for availability.  We adjust
    // usage totals, including humongous waste, after evacuation is done.
    log_debug(gc)("promoting humongous region %zu, spanning %zu", region->index(), spanned_regions);

    // For this region and each humongous continuation region spanned by this humongous object, change
    // affiliation to OLD_GENERATION and adjust the generation-use tallies.  The remnant of memory
    // in the last humongous region that is not spanned by obj is currently not used.
    for (size_t i = region->index(); i < index_limit; i++) {
      ShenandoahHeapRegion* r = _heap->get_region(i);
      log_debug(gc)("promoting humongous region %zu, from " PTR_FORMAT " to " PTR_FORMAT,
              r->index(), p2i(r->bottom()), p2i(r->top()));
      // We mark the entire humongous object's range as dirty after loop terminates, so no need to dirty the range here
      r->set_affiliation(OLD_GENERATION);
      r->set_promoted_in_place();
    }

    ShenandoahFreeSet* freeset = _heap->free_set();
    freeset->transfer_humongous_regions_from_mutator_to_old_collector(spanned_regions, humongous_waste);
  }

  // Since this region may have served previously as OLD, it may hold obsolete object range info.
  HeapWord* const humongous_bottom = region->bottom();
  ShenandoahScanRemembered* const scanner = old_gen->card_scan();
  scanner->reset_object_range(humongous_bottom, humongous_bottom + spanned_regions * ShenandoahHeapRegion::region_size_words());
  // Since the humongous region holds only one object, no lock is necessary for this register_object() invocation.
  scanner->register_object_without_lock(humongous_bottom);

  log_debug(gc)("Dirty cards for promoted humongous object (Region %zu) from " PTR_FORMAT " to " PTR_FORMAT,
            region->index(), p2i(humongous_bottom), p2i(humongous_bottom + obj->size()));
  scanner->mark_range_as_dirty(humongous_bottom, obj->size());
}
