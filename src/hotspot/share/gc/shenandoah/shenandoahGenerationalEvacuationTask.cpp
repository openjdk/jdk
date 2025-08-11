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

#include "gc/shenandoah/shenandoahAsserts.hpp"
#include "gc/shenandoah/shenandoahFreeSet.hpp"
#include "gc/shenandoah/shenandoahGenerationalEvacuationTask.hpp"
#include "gc/shenandoah/shenandoahGenerationalHeap.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahOldGeneration.hpp"
#include "gc/shenandoah/shenandoahScanRemembered.inline.hpp"
#include "gc/shenandoah/shenandoahUtils.hpp"
#include "gc/shenandoah/shenandoahYoungGeneration.hpp"

class ShenandoahConcurrentEvacuator : public ObjectClosure {
private:
  ShenandoahGenerationalHeap* const _heap;
  Thread* const _thread;
public:
  explicit ShenandoahConcurrentEvacuator(ShenandoahGenerationalHeap* heap) :
          _heap(heap), _thread(Thread::current()) {}

  void do_object(oop p) override {
    shenandoah_assert_marked(nullptr, p);
    if (!p->is_forwarded()) {
      _heap->evacuate_object(p, _thread);
    }
  }
};

ShenandoahGenerationalEvacuationTask::ShenandoahGenerationalEvacuationTask(ShenandoahGenerationalHeap* heap,
                                                                           ShenandoahRegionIterator* iterator,
                                                                           bool concurrent, bool only_promote_regions) :
  WorkerTask("Shenandoah Evacuation"),
  _heap(heap),
  _regions(iterator),
  _concurrent(concurrent),
  _only_promote_regions(only_promote_regions),
  _tenuring_threshold(0)
{
  shenandoah_assert_generational();
  _tenuring_threshold = _heap->age_census()->tenuring_threshold();
}

void ShenandoahGenerationalEvacuationTask::work(uint worker_id) {
  if (_concurrent) {
    ShenandoahConcurrentWorkerSession worker_session(worker_id);
    ShenandoahSuspendibleThreadSetJoiner stsj;
    do_work();
  } else {
    ShenandoahParallelWorkerSession worker_session(worker_id);
    do_work();
  }
}

void ShenandoahGenerationalEvacuationTask::do_work() {
  if (_only_promote_regions) {
    // No allocations will be made, do not enter oom-during-evac protocol.
    assert(ShenandoahHeap::heap()->collection_set()->is_empty(), "Should not have a collection set here");
    promote_regions();
  } else {
    assert(!ShenandoahHeap::heap()->collection_set()->is_empty(), "Should have a collection set here");
    ShenandoahEvacOOMScope oom_evac_scope;
    evacuate_and_promote_regions();
  }
}

void log_region(const ShenandoahHeapRegion* r, LogStream* ls) {
  ls->print_cr("GenerationalEvacuationTask, looking at %s region %zu, (age: %d) [%s, %s, %s]",
              r->is_old()? "old": r->is_young()? "young": "free", r->index(), r->age(),
              r->is_active()? "active": "inactive",
              r->is_humongous()? (r->is_humongous_start()? "humongous_start": "humongous_continuation"): "regular",
              r->is_cset()? "cset": "not-cset");
}

void ShenandoahGenerationalEvacuationTask::promote_regions() {
  ShenandoahHeapRegion* r;
  LogTarget(Debug, gc) lt;

  while ((r = _regions->next()) != nullptr) {
    if (lt.is_enabled()) {
      LogStream ls(lt);
      log_region(r, &ls);
    }

    maybe_promote_region(r);

    if (_heap->check_cancelled_gc_and_yield(_concurrent)) {
      break;
    }
  }
}

void ShenandoahGenerationalEvacuationTask::evacuate_and_promote_regions() {
  LogTarget(Debug, gc) lt;
  ShenandoahConcurrentEvacuator cl(_heap);
  ShenandoahHeapRegion* r;

  while ((r = _regions->next()) != nullptr) {
    if (lt.is_enabled()) {
      LogStream ls(lt);
      log_region(r, &ls);
    }

    if (r->is_cset()) {
      assert(r->has_live(), "Region %zu should have been reclaimed early", r->index());
      _heap->marked_object_iterate(r, &cl);
    } else {
      maybe_promote_region(r);
    }

    if (_heap->check_cancelled_gc_and_yield(_concurrent)) {
      break;
    }
  }
}


void ShenandoahGenerationalEvacuationTask::maybe_promote_region(ShenandoahHeapRegion* r) {
  if (r->is_young() && r->is_active() && (r->age() >= _tenuring_threshold)) {
    if (r->is_humongous_start()) {
      // We promote humongous_start regions along with their affiliated continuations during evacuation rather than
      // doing this work during a safepoint.  We cannot put humongous regions into the collection set because that
      // triggers the load-reference barrier (LRB) to copy on reference fetch.
      //
      // Aged humongous continuation regions are handled with their start region.  If an aged regular region has
      // more garbage than ShenandoahOldGarbageThreshold, we'll promote by evacuation.  If there is room for evacuation
      // in this cycle, the region will be in the collection set.  If there is not room, the region will be promoted
      // by evacuation in some future GC cycle.
      promote_humongous(r);
    } else if (r->is_regular() && (r->get_top_before_promote() != nullptr)) {
      // Likewise, we cannot put promote-in-place regions into the collection set because that would also trigger
      // the LRB to copy on reference fetch.
      //
      // If an aged regular region has received allocations during the current cycle, we do not promote because the
      // newly allocated objects do not have appropriate age; this region's age will be reset to zero at end of cycle.
      promote_in_place(r);
    }
  }
}

// When we promote a region in place, we can continue to use the established marking context to guide subsequent remembered
// set scans of this region's content.  The region will be coalesced and filled prior to the next old-gen marking effort.
// We identify the entirety of the region as DIRTY to force the next remembered set scan to identify the "interesting pointers"
// contained herein.
void ShenandoahGenerationalEvacuationTask::promote_in_place(ShenandoahHeapRegion* region) {
  assert(!_heap->gc_generation()->is_old(), "Sanity check");
  ShenandoahMarkingContext* const marking_context = _heap->young_generation()->complete_marking_context();
  HeapWord* const tams = marking_context->top_at_mark_start(region);

  {
    const size_t old_garbage_threshold = (ShenandoahHeapRegion::region_size_bytes() * ShenandoahOldGarbageThreshold) / 100;
    shenandoah_assert_generations_reconciled();
    assert(!_heap->is_concurrent_old_mark_in_progress(), "Cannot promote in place during old marking");
    assert(region->garbage_before_padded_for_promote() < old_garbage_threshold, "Region %zu has too much garbage for promotion", region->index());
    assert(region->is_young(), "Only young regions can be promoted");
    assert(region->is_regular(), "Use different service to promote humongous regions");
    assert(region->age() >= _tenuring_threshold, "Only promote regions that are sufficiently aged");
    assert(region->get_top_before_promote() == tams, "Region %zu has been used for allocations before promotion", region->index());
  }

  ShenandoahOldGeneration* const old_gen = _heap->old_generation();
  ShenandoahYoungGeneration* const young_gen = _heap->young_generation();

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

    HeapWord* update_watermark = region->get_update_watermark();

    // Now that this region is affiliated with old, we can allow it to receive allocations, though it may not be in the
    // is_collector_free range.
    region->restore_top_before_promote();

    size_t region_used = region->used();

    // The update_watermark was likely established while we had the artificially high value of top.  Make it sane now.
    assert(update_watermark >= region->top(), "original top cannot exceed preserved update_watermark");
    region->set_update_watermark(region->top());

    // Unconditionally transfer one region from young to old. This represents the newly promoted region.
    // This expands old and shrinks new by the size of one region.  Strictly, we do not "need" to expand old
    // if there are already enough unaffiliated regions in old to account for this newly promoted region.
    // However, if we do not transfer the capacities, we end up reducing the amount of memory that would have
    // otherwise been available to hold old evacuations, because old available is max_capacity - used and now
    // we would be trading a fully empty region for a partially used region.
    young_gen->decrease_used(region_used);
    young_gen->decrement_affiliated_region_count();

    // transfer_to_old() increases capacity of old and decreases capacity of young
    _heap->generation_sizer()->force_transfer_to_old(1);
    region->set_affiliation(OLD_GENERATION);

    old_gen->increment_affiliated_region_count();
    old_gen->increase_used(region_used);

    // add_old_collector_free_region() increases promoted_reserve() if available space exceeds plab_min_size()
    _heap->free_set()->add_promoted_in_place_region_to_old_collector(region);
  }
}

void ShenandoahGenerationalEvacuationTask::promote_humongous(ShenandoahHeapRegion* region) {
  ShenandoahMarkingContext* marking_context = _heap->marking_context();
  oop obj = cast_to_oop(region->bottom());
  assert(_heap->gc_generation()->is_mark_complete(), "sanity");
  shenandoah_assert_generations_reconciled();
  assert(region->is_young(), "Only young regions can be promoted");
  assert(region->is_humongous_start(), "Should not promote humongous continuation in isolation");
  assert(region->age() >= _tenuring_threshold, "Only promote regions that are sufficiently aged");
  assert(marking_context->is_marked(obj), "promoted humongous object should be alive");

  const size_t used_bytes = obj->size() * HeapWordSize;
  const size_t spanned_regions = ShenandoahHeapRegion::required_regions(used_bytes);
  const size_t humongous_waste = spanned_regions * ShenandoahHeapRegion::region_size_bytes() - obj->size() * HeapWordSize;
  const size_t index_limit = region->index() + spanned_regions;

  ShenandoahOldGeneration* const old_gen = _heap->old_generation();
  ShenandoahGeneration* const young_gen = _heap->young_generation();
  {
    // We need to grab the heap lock in order to avoid a race when changing the affiliations of spanned_regions from
    // young to old.
    ShenandoahHeapLocker locker(_heap->lock());

    // We promote humongous objects unconditionally, without checking for availability.  We adjust
    // usage totals, including humongous waste, after evacuation is done.
    log_debug(gc)("promoting humongous region %zu, spanning %zu", region->index(), spanned_regions);

    young_gen->decrease_used(used_bytes);
    young_gen->decrease_humongous_waste(humongous_waste);
    young_gen->decrease_affiliated_region_count(spanned_regions);

    // transfer_to_old() increases capacity of old and decreases capacity of young
    _heap->generation_sizer()->force_transfer_to_old(spanned_regions);

    // For this region and each humongous continuation region spanned by this humongous object, change
    // affiliation to OLD_GENERATION and adjust the generation-use tallies.  The remnant of memory
    // in the last humongous region that is not spanned by obj is currently not used.
    for (size_t i = region->index(); i < index_limit; i++) {
      ShenandoahHeapRegion* r = _heap->get_region(i);
      log_debug(gc)("promoting humongous region %zu, from " PTR_FORMAT " to " PTR_FORMAT,
              r->index(), p2i(r->bottom()), p2i(r->top()));
      // We mark the entire humongous object's range as dirty after loop terminates, so no need to dirty the range here
      r->set_affiliation(OLD_GENERATION);
    }

    old_gen->increase_affiliated_region_count(spanned_regions);
    old_gen->increase_used(used_bytes);
    old_gen->increase_humongous_waste(humongous_waste);
  }

  // Since this region may have served previously as OLD, it may hold obsolete object range info.
  HeapWord* const humongous_bottom = region->bottom();
  ShenandoahScanRemembered* const scanner = old_gen->card_scan();
  scanner->reset_object_range(humongous_bottom, humongous_bottom + spanned_regions * ShenandoahHeapRegion::region_size_words());
  // Since the humongous region holds only one object, no lock is necessary for this register_object() invocation.
  scanner->register_object_without_lock(humongous_bottom);

  if (obj->is_typeArray()) {
    // Primitive arrays don't need to be scanned.
    log_debug(gc)("Clean cards for promoted humongous object (Region %zu) from " PTR_FORMAT " to " PTR_FORMAT,
            region->index(), p2i(humongous_bottom), p2i(humongous_bottom + obj->size()));
    scanner->mark_range_as_clean(humongous_bottom, obj->size());
  } else {
    log_debug(gc)("Dirty cards for promoted humongous object (Region %zu) from " PTR_FORMAT " to " PTR_FORMAT,
            region->index(), p2i(humongous_bottom), p2i(humongous_bottom + obj->size()));
    scanner->mark_range_as_dirty(humongous_bottom, obj->size());
  }
}
