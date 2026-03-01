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

#include "gc/shenandoah/heuristics/shenandoahYoungHeuristics.hpp"
#include "gc/shenandoah/shenandoahClosures.inline.hpp"
#include "gc/shenandoah/shenandoahFreeSet.hpp"
#include "gc/shenandoah/shenandoahGeneration.hpp"
#include "gc/shenandoah/shenandoahGenerationalHeap.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahMonitoringSupport.hpp"
#include "gc/shenandoah/shenandoahOldGC.hpp"
#include "gc/shenandoah/shenandoahOldGeneration.hpp"
#include "gc/shenandoah/shenandoahYoungGeneration.hpp"
#include "prims/jvmtiTagMap.hpp"
#include "utilities/events.hpp"


ShenandoahOldGC::ShenandoahOldGC(ShenandoahOldGeneration* generation, ShenandoahSharedFlag& allow_preemption) :
    ShenandoahConcurrentGC(generation, false), _old_generation(generation), _allow_preemption(allow_preemption) {
}

// Final mark for old-gen is different for young than old, so we
// override the implementation.
void ShenandoahOldGC::op_final_mark() {

  ShenandoahGenerationalHeap* const heap = ShenandoahGenerationalHeap::heap();
  assert(ShenandoahSafepoint::is_at_shenandoah_safepoint(), "Should be at safepoint");
  assert(!heap->has_forwarded_objects(), "No forwarded objects on this path");

  if (ShenandoahVerify) {
    heap->verifier()->verify_roots_no_forwarded(_old_generation);
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

    if (VerifyAfterGC) {
      Universe::verify();
    }

    {
      ShenandoahTimingsTracker timing(ShenandoahPhaseTimings::final_mark_propagate_gc_state);
      heap->propagate_gc_state_to_all_threads();
    }
  }
}

bool ShenandoahOldGC::collect(GCCause::Cause cause) {
  auto heap = ShenandoahGenerationalHeap::heap();
  assert(!_old_generation->is_doing_mixed_evacuations(), "Should not start an old gc with pending mixed evacuations");
  assert(!_old_generation->is_preparing_for_mark(), "Old regions need to be parsable during concurrent mark.");

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

  if (_generation->is_concurrent_mark_in_progress()) {
    assert(heap->cancelled_gc(), "Safepoint operation observed gc cancellation");
    // GC may have been cancelled before final mark, but after the preceding cancellation check.
    return false;
  }

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

  assert(!heap->is_concurrent_strong_root_in_progress(), "No evacuations during old gc.");

  // We must execute this vm operation if we completed final mark. We cannot
  // return from here with weak roots in progress. This is not a valid gc state
  // for any young collections (or allocation failures) that interrupt the old
  // collection.
  heap->concurrent_final_roots();

  // After concurrent old marking finishes, we reclaim immediate garbage. Further, we may also want to expand OLD in order
  // to make room for anticipated promotions and/or for mixed evacuations.  Mixed evacuations are especially likely to
  // follow the end of OLD marking.
  heap->rebuild_free_set_within_phase();
  heap->free_set()->log_status_under_lock();
  return true;
}
