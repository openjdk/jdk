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

#include "precompiled.hpp"

#include "gc/shenandoah/heuristics/shenandoahYoungHeuristics.hpp"
#include "gc/shenandoah/shenandoahFreeSet.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahMonitoringSupport.hpp"
#include "gc/shenandoah/shenandoahOldGC.hpp"
#include "gc/shenandoah/shenandoahOopClosures.inline.hpp"
#include "gc/shenandoah/shenandoahGeneration.hpp"
#include "gc/shenandoah/shenandoahOldGeneration.hpp"
#include "gc/shenandoah/shenandoahYoungGeneration.hpp"
#include "prims/jvmtiTagMap.hpp"
#include "utilities/events.hpp"




ShenandoahOldGC::ShenandoahOldGC(ShenandoahGeneration* generation, ShenandoahSharedFlag& allow_preemption) :
    ShenandoahConcurrentGC(generation, false), _allow_preemption(allow_preemption) {
}

// Final mark for old-gen is different than for young or old, so we
// override the implementation.
void ShenandoahOldGC::op_final_mark() {

  ShenandoahHeap* const heap = ShenandoahHeap::heap();
  assert(ShenandoahSafepoint::is_at_shenandoah_safepoint(), "Should be at safepoint");
  assert(!heap->has_forwarded_objects(), "No forwarded objects on this path");

  if (ShenandoahVerify) {
    heap->verifier()->verify_roots_no_forwarded();
  }

  if (!heap->cancelled_gc()) {
    assert(_mark.generation()->is_old(), "Generation of Old-Gen GC should be OLD");
    _mark.finish_mark();
    assert(!heap->cancelled_gc(), "STW mark cannot OOM");

    // Old collection is complete, the young generation no longer needs this
    // reference to the old concurrent mark so clean it up.
    heap->young_generation()->set_old_gen_task_queues(nullptr);

    // We need to do this because weak root cleaning reports the number of dead handles
    JvmtiTagMap::set_needs_cleaning();

    _generation->prepare_regions_and_collection_set(true);

    heap->set_unload_classes(false);
    heap->prepare_concurrent_roots();

    // Believe verification following old-gen concurrent mark needs to be different than verification following
    // young-gen concurrent mark, so am commenting this out for now:
    //   if (ShenandoahVerify) {
    //     heap->verifier()->verify_after_concmark();
    //   }

    if (VerifyAfterGC) {
      Universe::verify();
    }
  }
}

bool ShenandoahOldGC::collect(GCCause::Cause cause) {
  ShenandoahHeap* heap = ShenandoahHeap::heap();
  assert(!heap->doing_mixed_evacuations(), "Should not start an old gc with pending mixed evacuations");
  assert(!heap->is_prepare_for_old_mark_in_progress(), "Old regions need to be parseable during concurrent mark.");

  // Enable preemption of old generation mark.
  _allow_preemption.set();

  // Continue concurrent mark, do not reset regions, do not mark roots, do not collect $200.
  entry_mark();

  // If we failed to unset the preemption flag, it means another thread has already unset it.
  if (!_allow_preemption.try_unset()) {
    // The regulator thread has unset the preemption guard. That thread will shortly cancel
    // the gc, but the control thread is now racing it. Wait until this thread sees the
    // cancellation.
    while (!heap->cancelled_gc()) {
      SpinPause();
    }
  }

  if (heap->cancelled_gc()) {
    return false;
  }

  // Complete marking under STW
  vmop_entry_final_mark();

  // We aren't dealing with old generation evacuation yet. Our heuristic
  // should not have built a cset in final mark.
  assert(!heap->is_evacuation_in_progress(), "Old gen evacuations are not supported");

  // Process weak roots that might still point to regions that would be broken by cleanup
  if (heap->is_concurrent_weak_root_in_progress()) {
    entry_weak_refs();
    entry_weak_roots();
  }

  // Final mark might have reclaimed some immediate garbage, kick cleanup to reclaim
  // the space. This would be the last action if there is nothing to evacuate.
  entry_cleanup_early();

  {
    ShenandoahHeapLocker locker(heap->lock());
    heap->free_set()->log_status();
  }


  // TODO: Old marking doesn't support class unloading yet
  // Perform concurrent class unloading
  // if (heap->unload_classes() &&
  //     heap->is_concurrent_weak_root_in_progress()) {
  //   entry_class_unloading();
  // }


  assert(!heap->is_concurrent_strong_root_in_progress(), "No evacuations during old gc.");

  // We must execute this vm operation if we completed final mark. We cannot
  // return from here with weak roots in progress. This is not a valid gc state
  // for any young collections (or allocation failures) that interrupt the old
  // collection.
  vmop_entry_final_roots();

  // We do not rebuild_free following increments of old marking because memory has not been reclaimed..  However, we may
  // need to transfer memory to OLD in order to efficiently support the mixed evacuations that might immediately follow.
  size_t allocation_runway = heap->young_heuristics()->bytes_of_allocation_runway_before_gc_trigger(0);
  heap->adjust_generation_sizes_for_next_cycle(allocation_runway, 0, 0);

  bool success;
  size_t region_xfer;
  const char* region_destination;
  ShenandoahYoungGeneration* young_gen = heap->young_generation();
  ShenandoahGeneration* old_gen = heap->old_generation();
  {
    ShenandoahHeapLocker locker(heap->lock());

    size_t old_region_surplus = heap->get_old_region_surplus();
    size_t old_region_deficit = heap->get_old_region_deficit();
    if (old_region_surplus) {
      success = heap->generation_sizer()->transfer_to_young(old_region_surplus);
      region_destination = "young";
      region_xfer = old_region_surplus;
    } else if (old_region_deficit) {
      success = heap->generation_sizer()->transfer_to_old(old_region_deficit);
      region_destination = "old";
      region_xfer = old_region_deficit;
      if (!success) {
        ((ShenandoahOldHeuristics *) old_gen->heuristics())->trigger_cannot_expand();
      }
    } else {
      region_destination = "none";
      region_xfer = 0;
      success = true;
    }
    heap->set_old_region_surplus(0);
    heap->set_old_region_deficit(0);
  }

  // Report outside the heap lock
  size_t young_available = young_gen->available();
  size_t old_available = old_gen->available();
  log_info(gc, ergo)("After old marking finished, %s " SIZE_FORMAT " regions to %s to prepare for next gc, old available: "
                     SIZE_FORMAT "%s, young_available: " SIZE_FORMAT "%s",
                     success? "successfully transferred": "failed to transfer", region_xfer, region_destination,
                     byte_size_in_proper_unit(old_available), proper_unit_for_byte_size(old_available),
                     byte_size_in_proper_unit(young_available), proper_unit_for_byte_size(young_available));
  return true;
}
