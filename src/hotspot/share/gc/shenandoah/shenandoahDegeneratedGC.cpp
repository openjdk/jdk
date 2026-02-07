/*
 * Copyright (c) 2021, Red Hat, Inc. All rights reserved.
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


#include "gc/shared/collectorCounters.hpp"
#include "gc/shenandoah/shenandoahCollectorPolicy.hpp"
#include "gc/shenandoah/shenandoahConcurrentMark.hpp"
#include "gc/shenandoah/shenandoahDegeneratedGC.hpp"
#include "gc/shenandoah/shenandoahFullGC.hpp"
#include "gc/shenandoah/shenandoahGeneration.hpp"
#include "gc/shenandoah/shenandoahGenerationalHeap.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahMetrics.hpp"
#include "gc/shenandoah/shenandoahMonitoringSupport.hpp"
#include "gc/shenandoah/shenandoahOldGeneration.hpp"
#include "gc/shenandoah/shenandoahRootProcessor.inline.hpp"
#include "gc/shenandoah/shenandoahSTWMark.hpp"
#include "gc/shenandoah/shenandoahUtils.hpp"
#include "gc/shenandoah/shenandoahVerifier.hpp"
#include "gc/shenandoah/shenandoahVMOperations.hpp"
#include "gc/shenandoah/shenandoahWorkerPolicy.hpp"
#include "gc/shenandoah/shenandoahYoungGeneration.hpp"
#include "runtime/vmThread.hpp"
#include "utilities/events.hpp"

ShenandoahDegenGC::ShenandoahDegenGC(ShenandoahDegenPoint degen_point, ShenandoahGeneration* generation) :
  ShenandoahGC(generation),
  _degen_point(degen_point),
  _abbreviated(false) {
}

bool ShenandoahDegenGC::collect(GCCause::Cause cause) {
  vmop_degenerated();
  ShenandoahHeap* heap = ShenandoahHeap::heap();
  if (heap->mode()->is_generational()) {
    bool is_bootstrap_gc = heap->old_generation()->is_bootstrapping();
    heap->mmu_tracker()->record_degenerated(GCId::current(), is_bootstrap_gc);
    const char* msg = is_bootstrap_gc? "At end of Degenerated Bootstrap Old GC": "At end of Degenerated Young GC";
    heap->log_heap_status(msg);
  }
  return true;
}

void ShenandoahDegenGC::vmop_degenerated() {
  TraceCollectorStats tcs(ShenandoahHeap::heap()->monitoring_support()->full_stw_collection_counters());
  ShenandoahTimingsTracker timing(ShenandoahPhaseTimings::degen_gc_gross);
  VM_ShenandoahDegeneratedGC degenerated_gc(this);
  VMThread::execute(&degenerated_gc);
}

void ShenandoahDegenGC::entry_degenerated() {
  const char* msg = degen_event_message(_degen_point);
  ShenandoahPausePhase gc_phase(msg, ShenandoahPhaseTimings::degen_gc, true /* log_heap_usage */);
  EventMark em("%s", msg);
  ShenandoahHeap* const heap = ShenandoahHeap::heap();
  ShenandoahWorkerScope scope(heap->workers(),
                              ShenandoahWorkerPolicy::calc_workers_for_stw_degenerated(),
                              "stw degenerated gc");

  heap->set_degenerated_gc_in_progress(true);
  op_degenerated();
  heap->set_degenerated_gc_in_progress(false);
  {
    ShenandoahTimingsTracker timing(ShenandoahPhaseTimings::degen_gc_propagate_gc_state);
    heap->propagate_gc_state_to_all_threads();
  }
}

void ShenandoahDegenGC::op_degenerated() {
  ShenandoahHeap* const heap = ShenandoahHeap::heap();
  // Degenerated GC is STW, but it can also fail. Current mechanics communicates
  // GC failure via cancelled_concgc() flag. So, if we detect the failure after
  // some phase, we have to upgrade the Degenerate GC to Full GC.
  heap->clear_cancelled_gc();

  // If it's passive mode with ShenandoahCardBarrier turned on: clean the write table
  // without swapping the tables since no scan happens in passive mode anyway
  if (ShenandoahCardBarrier && !heap->mode()->is_generational()) {
    heap->old_generation()->card_scan()->mark_write_table_as_clean();
  }

  if (heap->mode()->is_generational()) {
    const ShenandoahOldGeneration* old_generation = heap->old_generation();
    if (!heap->is_concurrent_old_mark_in_progress()) {
      // If we are not marking the old generation, there should be nothing in the old mark queues
      assert(old_generation->task_queues()->is_empty(), "Old gen task queues should be empty");
    } else {
      // This is still necessary for degenerated cycles because the degeneration point may occur
      // after final mark of the young generation. See ShenandoahConcurrentGC::op_final_update_refs for
      // a more detailed explanation.
      old_generation->transfer_pointers_from_satb();
    }

    if (_generation->is_global()) {
      // If we are in a global cycle, the old generation should not be marking. It is, however,
      // allowed to be holding regions for evacuation or coalescing.
      assert(old_generation->is_idle()
             || old_generation->is_doing_mixed_evacuations()
             || old_generation->is_preparing_for_mark(),
             "Old generation cannot be in state: %s", old_generation->state_name());
    }
  }

  ShenandoahMetricsSnapshot metrics(heap->free_set());

  switch (_degen_point) {
    // The cases below form the Duff's-like device: it describes the actual GC cycle,
    // but enters it at different points, depending on which concurrent phase had
    // degenerated.

    case _degenerated_outside_cycle:
      // We have degenerated from outside the cycle, which means something is bad with
      // the heap, most probably heavy humongous fragmentation, or we are very low on free
      // space. It makes little sense to wait for Full GC to reclaim as much as it can, when
      // we can do the most aggressive degen cycle, which includes processing references and
      // class unloading, unless those features are explicitly disabled.

      // Note that we can only do this for "outside-cycle" degens, otherwise we would risk
      // changing the cycle parameters mid-cycle during concurrent -> degenerated handover.
      heap->set_unload_classes(_generation->heuristics()->can_unload_classes() &&
                                (!heap->mode()->is_generational() || _generation->is_global()));

      if (heap->mode()->is_generational()) {
        // Clean the read table before swapping it. The end goal here is to have a clean
        // write table, and to have the read table updated with the previous write table.
        heap->old_generation()->card_scan()->mark_read_table_as_clean();

        if (_generation->is_young()) {
          // Swap remembered sets for young
          _generation->swap_card_tables();
        }
      }

    case _degenerated_roots:
      // Degenerated from concurrent root mark, reset the flag for STW mark
      if (!heap->mode()->is_generational()) {
        if (heap->is_concurrent_mark_in_progress()) {
          heap->cancel_concurrent_mark();
        }
      } else {
        if (_generation->is_concurrent_mark_in_progress()) {
          // We want to allow old generation marking to be punctuated by young collections
          // (even if they have degenerated). If this is a global cycle, we'd have cancelled
          // the entire old gc before coming into this switch. Note that cancel_marking on
          // the generation does NOT abandon incomplete SATB buffers as cancel_concurrent_mark does.
          // We need to separate out the old pointers which is done below.
          _generation->cancel_marking();
        }

        if (_degen_point == ShenandoahDegenPoint::_degenerated_roots) {
          // We only need this if the concurrent cycle has already swapped the card tables.
          // Marking will use the 'read' table, but interesting pointers may have been
          // recorded in the 'write' table in the time between the cancelled concurrent cycle
          // and this degenerated cycle. These pointers need to be included in the 'read' table
          // used to scan the remembered set during the STW mark which follows here.
          _generation->merge_write_table();
        }
      }

      op_reset();

      // STW mark
      op_mark();

    case _degenerated_mark:
      // No fallthrough. Continue mark, handed over from concurrent mark if
      // concurrent mark has yet completed
      if (_degen_point == ShenandoahDegenPoint::_degenerated_mark && heap->is_concurrent_mark_in_progress()) {
        assert(!ShenandoahBarrierSet::satb_mark_queue_set().get_filter_out_young(),
               "Should not be filtering out young pointers when concurrent mark degenerates");
        op_finish_mark();
      }
      assert(!heap->cancelled_gc(), "STW mark can not OOM");

      /* Degen select Collection Set. etc. */
      op_prepare_evacuation();

      op_cleanup_early();

    case _degenerated_evac:
      // If heuristics thinks we should do the cycle, this flag would be set,
      // and we can do evacuation. Otherwise, it would be the shortcut cycle.
      if (heap->is_evacuation_in_progress()) {

        if (_degen_point == _degenerated_evac) {
          // Degeneration under oom-evac protocol allows the mutator LRB to expose
          // references to from-space objects. This is okay, in theory, because we
          // will come to the safepoint here to complete the evacuations and update
          // the references. However, if the from-space reference is written to a
          // region that was EC during final mark or was recycled after final mark
          // it will not have TAMS or UWM updated. Such a region is effectively
          // skipped during update references which can lead to crashes and corruption
          // if the from-space reference is accessed.
          if (UseTLAB) {
            heap->labs_make_parsable();
          }

          for (size_t i = 0; i < heap->num_regions(); i++) {
            ShenandoahHeapRegion* r = heap->get_region(i);
            if (r->is_active() && r->top() > r->get_update_watermark()) {
              r->set_update_watermark_at_safepoint(r->top());
            }
          }
        }

        // Degeneration under oom-evac protocol might have left some objects in
        // collection set un-evacuated. Restart evacuation from the beginning to
        // capture all objects. For all the objects that are already evacuated,
        // it would be a simple check, which is supposed to be fast. This is also
        // safe to do even without degeneration, as CSet iterator is at beginning
        // in preparation for evacuation anyway.
        //
        // Before doing that, we need to make sure we never had any cset-pinned
        // regions. This may happen if allocation failure happened when evacuating
        // the about-to-be-pinned object, oom-evac protocol left the object in
        // the collection set, and then the pin reached the cset region. If we continue
        // the cycle here, we would trash the cset and alive objects in it. To avoid
        // it, we fail degeneration right away and slide into Full GC to recover.

        {
          heap->sync_pinned_region_status();
          heap->collection_set()->clear_current_index();
          ShenandoahHeapRegion* r;
          while ((r = heap->collection_set()->next()) != nullptr) {
            if (r->is_pinned()) {
              op_degenerated_fail();
              return;
            }
          }

          heap->collection_set()->clear_current_index();
        }
        op_evacuate();
        if (heap->cancelled_gc()) {
          op_degenerated_fail();
          return;
        }
      } else if (has_in_place_promotions(heap)) {
        // We have nothing to evacuate, but there are still regions to promote in place.
        ShenandoahGCPhase phase(ShenandoahPhaseTimings::degen_gc_promote_regions);
        ShenandoahGenerationalHeap::heap()->promote_regions_in_place(_generation, false /* concurrent*/);
      }

      // Update collector state regardless of whether there are forwarded objects
      heap->set_evacuation_in_progress(false);
      heap->set_concurrent_weak_root_in_progress(false);
      heap->set_concurrent_strong_root_in_progress(false);

      // If heuristics thinks we should do the cycle, this flag would be set,
      // and we need to do update-refs. Otherwise, it would be the shortcut cycle.
      if (heap->has_forwarded_objects()) {
        op_init_update_refs();
        assert(!heap->cancelled_gc(), "STW reference update can not OOM");
      } else {
        _abbreviated = true;
      }

    case _degenerated_update_refs:
      if (heap->has_forwarded_objects()) {
        op_update_refs();
        op_update_roots();
        assert(!heap->cancelled_gc(), "STW reference update can not OOM");
      }

      // Disarm nmethods that armed in concurrent cycle.
      // In above case, update roots should disarm them
      ShenandoahCodeRoots::disarm_nmethods();

      op_cleanup_complete();

      if (heap->mode()->is_generational()) {
        ShenandoahGenerationalHeap::heap()->complete_degenerated_cycle();
      }

      break;
    default:
      ShouldNotReachHere();
  }

  if (ShenandoahVerify) {
    heap->verifier()->verify_after_degenerated(_generation);
  }

  if (VerifyAfterGC) {
    Universe::verify();
  }

  // Decide if this cycle made good progress, and, if not, should it upgrade to a full GC.
  const bool progress = metrics.is_good_progress();
  ShenandoahCollectorPolicy* policy = heap->shenandoah_policy();
  policy->record_degenerated(_generation->is_young(), _abbreviated, progress);
  if (progress) {
    heap->notify_gc_progress();
    _generation->heuristics()->record_degenerated();
  } else if (policy->should_upgrade_degenerated_gc()) {
    // Upgrade to full GC, register full-GC impact on heuristics.
    op_degenerated_futile();
  } else {
    _generation->heuristics()->record_degenerated();
  }
}

void ShenandoahDegenGC::op_reset() {
  _generation->prepare_gc();
}

void ShenandoahDegenGC::op_mark() {
  assert(!_generation->is_concurrent_mark_in_progress(), "Should be reset");
  ShenandoahGCPhase phase(ShenandoahPhaseTimings::degen_gc_stw_mark);
  ShenandoahSTWMark mark(_generation, false /*full gc*/);
  mark.mark();
}

void ShenandoahDegenGC::op_finish_mark() {
  ShenandoahConcurrentMark mark(_generation);
  mark.finish_mark();
}

void ShenandoahDegenGC::op_prepare_evacuation() {
  ShenandoahHeap* const heap = ShenandoahHeap::heap();
  if (ShenandoahVerify) {
    heap->verifier()->verify_roots_no_forwarded(_generation);
  }

  // STW cleanup weak roots and unload classes
  heap->parallel_cleaning(_generation, false /*full gc*/);

  // Prepare regions and collection set
  _generation->prepare_regions_and_collection_set(false /*concurrent*/);

  // Retire the TLABs, which will force threads to reacquire their TLABs after the pause.
  // This is needed for two reasons. Strong one: new allocations would be with new freeset,
  // which would be outside the collection set, so no cset writes would happen there.
  // Weaker one: new allocations would happen past update watermark, and so less work would
  // be needed for reference updates (would update the large filler instead).
  if (UseTLAB) {
    ShenandoahGCPhase phase(ShenandoahPhaseTimings::degen_gc_final_manage_labs);
    heap->tlabs_retire(false);
  }

  if (!heap->collection_set()->is_empty()) {
    if (ShenandoahVerify) {
      heap->verifier()->verify_before_evacuation(_generation);
    }

    heap->set_evacuation_in_progress(true);
    heap->set_has_forwarded_objects(true);
  } else {
    if (ShenandoahVerify) {
      if (has_in_place_promotions(heap)) {
        heap->verifier()->verify_after_concmark_with_promotions(_generation);
      } else {
        heap->verifier()->verify_after_concmark(_generation);
      }
    }

    if (VerifyAfterGC) {
      Universe::verify();
    }
  }
}

bool ShenandoahDegenGC::has_in_place_promotions(const ShenandoahHeap* heap) const {
  return heap->mode()->is_generational() && heap->old_generation()->has_in_place_promotions();
}

void ShenandoahDegenGC::op_cleanup_early() {
  ShenandoahHeap::heap()->recycle_trash();
}

void ShenandoahDegenGC::op_evacuate() {
  ShenandoahGCPhase phase(ShenandoahPhaseTimings::degen_gc_stw_evac);
  ShenandoahHeap::heap()->evacuate_collection_set(_generation, false /* concurrent*/);
}

void ShenandoahDegenGC::op_init_update_refs() {
  // Evacuation has completed
  ShenandoahHeap* const heap = ShenandoahHeap::heap();
  heap->prepare_update_heap_references();
  heap->set_update_refs_in_progress(true);
}

void ShenandoahDegenGC::op_update_refs() {
  ShenandoahHeap* const heap = ShenandoahHeap::heap();
  ShenandoahGCPhase phase(ShenandoahPhaseTimings::degen_gc_update_refs);
  // Handed over from concurrent update references phase
  heap->update_heap_references(_generation, false /*concurrent*/);

  heap->set_update_refs_in_progress(false);
  heap->set_has_forwarded_objects(false);
}

void ShenandoahDegenGC::op_update_roots() {
  ShenandoahHeap* const heap = ShenandoahHeap::heap();

  update_roots(false /*full_gc*/);

  heap->update_heap_region_states(false /*concurrent*/);

  if (ShenandoahVerify) {
    heap->verifier()->verify_after_update_refs(_generation);
  }

  if (VerifyAfterGC) {
    Universe::verify();
  }

  heap->rebuild_free_set(false /*concurrent*/);
}

void ShenandoahDegenGC::op_cleanup_complete() {
  ShenandoahGCPhase phase(ShenandoahPhaseTimings::degen_gc_cleanup_complete);
  ShenandoahHeap::heap()->recycle_trash();
}

void ShenandoahDegenGC::op_degenerated_fail() {
  upgrade_to_full();
}

void ShenandoahDegenGC::op_degenerated_futile() {
  upgrade_to_full();
}

const char* ShenandoahDegenGC::degen_event_message(ShenandoahDegenPoint point) const {
  switch (point) {
    case _degenerated_unset:
      SHENANDOAH_RETURN_EVENT_MESSAGE(_generation->type(), "Pause Degenerated GC", " (<UNSET>)");
    case _degenerated_outside_cycle:
      SHENANDOAH_RETURN_EVENT_MESSAGE(_generation->type(), "Pause Degenerated GC", " (Outside of Cycle)");
    case _degenerated_roots:
      SHENANDOAH_RETURN_EVENT_MESSAGE(_generation->type(), "Pause Degenerated GC", " (Roots)");
    case _degenerated_mark:
      SHENANDOAH_RETURN_EVENT_MESSAGE(_generation->type(), "Pause Degenerated GC", " (Mark)");
    case _degenerated_evac:
      SHENANDOAH_RETURN_EVENT_MESSAGE(_generation->type(), "Pause Degenerated GC", " (Evacuation)");
    case _degenerated_update_refs:
      SHENANDOAH_RETURN_EVENT_MESSAGE(_generation->type(), "Pause Degenerated GC", " (Update Refs)");
    default:
      ShouldNotReachHere();
      SHENANDOAH_RETURN_EVENT_MESSAGE(_generation->type(), "Pause Degenerated GC", " (?)");
  }
}

void ShenandoahDegenGC::upgrade_to_full() {
  log_info(gc)("Degenerated GC upgrading to Full GC");
  ShenandoahHeap* heap = ShenandoahHeap::heap();
  heap->cancel_gc(GCCause::_shenandoah_upgrade_to_full_gc);
  heap->increment_total_collections(true);
  heap->shenandoah_policy()->record_degenerated_upgrade_to_full();
  ShenandoahFullGC full_gc;
  full_gc.op_full(GCCause::_shenandoah_upgrade_to_full_gc);
}
