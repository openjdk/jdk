/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/g1/g1ConcurrentRebuildAndScrub.hpp"

#include "gc/g1/g1ConcurrentMark.inline.hpp"
#include "gc/g1/g1ConcurrentMarkBitMap.inline.hpp"
#include "gc/g1/g1HeapRegion.inline.hpp"
#include "gc/g1/g1HeapRegionManager.inline.hpp"
#include "gc/shared/gc_globals.hpp"
#include "gc/shared/suspendibleThreadSet.hpp"
#include "gc/shared/workerThread.hpp"
#include "logging/log.hpp"
#include "memory/memRegion.hpp"
#include "oops/oopsHierarchy.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"

// Worker task that scans the objects in the old generation to rebuild the remembered
// set and at the same time scrubs dead objects by replacing them with filler objects
// to make them completely parseable.
//
// The remark pause recorded two pointers within the regions:
//
// parsable_bottom (pb): this is the TAMS of the recent marking for that region. Objects
//                       below that may or may not be dead (as per mark bitmap).
//                       This task needs to remove the dead objects, replacing them
//                       with filler objects so that they can be walked through later.
//
// top_at_rebuild_start (tars): at rebuild phase start we record the current top: up to
//                              this address (live) objects need to be scanned for references
//                              that might need to be added to the remembered sets.
//
// Note that bottom <= parsable_bottom <= tars; if there is no tars (i.e. null),
// obviously there can not be a parsable_bottom.
//
// We need to scrub and scan objects to rebuild remembered sets until parsable_bottom;
// we need to scan objects to rebuild remembered sets until tars.
// Regions might have been reclaimed while scrubbing them after having yielded for
// a pause.
class G1RebuildRSAndScrubTask : public WorkerTask {
  G1ConcurrentMark* _cm;
  G1HeapRegionClaimer _hr_claimer;

  const bool _should_rebuild_remset;

  class G1RebuildRSAndScrubRegionClosure : public G1HeapRegionClosure {
    G1ConcurrentMark* _cm;
    const G1CMBitMap* _bitmap;

    G1RebuildRemSetClosure _rebuild_closure;

    const bool _should_rebuild_remset;

    size_t _processed_words;

    const size_t ProcessingYieldLimitInWords = G1RebuildRemSetChunkSize / HeapWordSize;

    void reset_processed_words() {
      _processed_words = 0;
    }

    void add_processed_words(size_t processed) {
      _processed_words += processed;
    }

    // Yield if enough has been processed. Return whether we should stop
    // processing this region because either the concurrent marking cycle has been
    // aborted or the region has been reclaimed.
    bool yield_if_necessary(G1HeapRegion* hr) {
      if (_processed_words >= ProcessingYieldLimitInWords) {
        reset_processed_words();
        // If a yield occurs (potential young-gc pause), must recheck for
        // potential regions reclamation.
        if (_cm->do_yield_check() && !should_rebuild_or_scrub(hr)) {
          return true;
        }
      }
      return _cm->has_aborted() || !should_rebuild_or_scrub(hr);
    }

    // Returns whether the top at rebuild start value for the given region indicates
    // that there is some rebuild or scrubbing work.
    //
    // Based on the results of G1RemSetTrackingPolicy::needs_scan_for_rebuild(),
    // the value may be changed to null during rebuilding if the region has either:
    //  - been allocated after rebuild start, or
    //  - been reclaimed by a collection.
    bool should_rebuild_or_scrub(G1HeapRegion* hr) const {
      return _cm->top_at_rebuild_start(hr) != nullptr;
    }

    // Helper used by both humongous objects and when chunking an object larger than the
    // G1RebuildRemSetChunkSize. The heap region is needed check whether the region has
    // been reclaimed during yielding.
    void scan_large_object(G1HeapRegion* hr, const oop obj, MemRegion scan_range) {
      HeapWord* start = scan_range.start();
      HeapWord* limit = scan_range.end();
      do {
        MemRegion mr(start, MIN2(start + ProcessingYieldLimitInWords, limit));
        obj->oop_iterate(&_rebuild_closure, mr);

        // Update processed words and yield, for humongous objects we will yield
        // after each chunk.
        add_processed_words(mr.word_size());

        if (yield_if_necessary(hr)) {
          return;
        }

        // Step to next chunk of the large object.
        start = mr.end();
      } while (start < limit);
    }

    // Scan for references into regions that need remembered set update for the given
    // live object. Returns the offset to the next object.
    size_t scan_object(G1HeapRegion* hr, HeapWord* current) {
      oop obj = cast_to_oop(current);
      size_t obj_size = obj->size();

      if (!_should_rebuild_remset) {
        // Not rebuilding, just step to next object.
        add_processed_words(obj_size);
      } else if (obj_size > ProcessingYieldLimitInWords) {
        // Large object, needs to be chunked to avoid stalling safepoints.
        MemRegion mr(current, obj_size);
        scan_large_object(hr, obj, mr);
        // No need to add to _processed_words, this is all handled by the above call;
        // we also ignore the marking abort result of scan_large_object - we will check
        // again right afterwards.
      } else {
        // Object smaller than yield limit, process it fully.
        obj->oop_iterate(&_rebuild_closure);
        // Update how much we have processed. Yield check in main loop
        // will handle this case.
        add_processed_words(obj_size);
      }

      return obj_size;
    }

    // Scan or scrub depending on if addr is marked.
    HeapWord* scan_or_scrub(G1HeapRegion* hr, HeapWord* addr, HeapWord* limit) {
      if (_bitmap->is_marked(addr)) {
        //  Live object, need to scan to rebuild remembered sets for this object.
        return addr + scan_object(hr, addr);
      } else {
        // Found dead object (which klass has potentially been unloaded). Scrub to next marked object.
        HeapWord* scrub_end = _bitmap->get_next_marked_addr(addr, limit);
        hr->fill_range_with_dead_objects(addr, scrub_end);
        // Return the next object to handle.
        return scrub_end;
      }
    }

    // Scan and scrub the given region to tars.
    void scan_and_scrub_region(G1HeapRegion* hr, HeapWord* const pb) {
      assert(should_rebuild_or_scrub(hr), "must be");

      log_trace(gc, marking)("Scrub and rebuild region: " HR_FORMAT " pb: " PTR_FORMAT " TARS: " PTR_FORMAT " TAMS: " PTR_FORMAT,
                             HR_FORMAT_PARAMS(hr), p2i(pb), p2i(_cm->top_at_rebuild_start(hr)), p2i(_cm->top_at_mark_start(hr)));

      {
        // Step 1: Scan the given region from bottom to parsable_bottom.
        HeapWord* start = hr->bottom();
        HeapWord* limit = pb;
        while (start < limit) {
          start = scan_or_scrub(hr, start, limit);

          if (yield_if_necessary(hr)) {
            return;
          }
        }
      }

      // Scrubbing completed for this region - notify that we are done with it, resetting
      // pb to bottom.
      hr->note_end_of_scrubbing();

      {
        // Step 2: Rebuild from TAMS (= parsable_bottom) to TARS.
        HeapWord* start = pb;
        HeapWord* limit = _cm->top_at_rebuild_start(hr);
        while (start < limit) {
          start += scan_object(hr, start);

          if (yield_if_necessary(hr)) {
            return;
          }
        }
      }
    }

    // Scan a humongous region for remembered set updates. Scans in chunks to avoid
    // stalling safepoints.
    void scan_humongous_region(G1HeapRegion* hr, HeapWord* const pb) {
      assert(should_rebuild_or_scrub(hr), "must be");

      if (!_should_rebuild_remset) {
        // When not rebuilding there is nothing to do for humongous objects.
        return;
      }

      // At this point we should only have live humongous objects, that
      // means it must either be:
      // - marked
      // - or seen as fully parsable, i.e. allocated after the marking started
      oop humongous = cast_to_oop(hr->humongous_start_region()->bottom());
      assert(_bitmap->is_marked(humongous) || pb == hr->bottom(),
             "Humongous object not live");

      log_trace(gc, marking)("Rebuild for humongous region: " HR_FORMAT " pb: " PTR_FORMAT " TARS: " PTR_FORMAT,
                              HR_FORMAT_PARAMS(hr), p2i(pb), p2i(_cm->top_at_rebuild_start(hr)));

      // Scan the humongous object in chunks from bottom to top to rebuild remembered sets.
      HeapWord* humongous_end = hr->humongous_start_region()->bottom() + humongous->size();
      MemRegion mr(hr->bottom(), MIN2(hr->top(), humongous_end));

      scan_large_object(hr, humongous, mr);
    }

  public:
    G1RebuildRSAndScrubRegionClosure(G1ConcurrentMark* cm, bool should_rebuild_remset, uint worker_id) :
      _cm(cm),
      _bitmap(_cm->mark_bitmap()),
      _rebuild_closure(G1CollectedHeap::heap(), worker_id),
      _should_rebuild_remset(should_rebuild_remset),
      _processed_words(0) { }

    bool do_heap_region(G1HeapRegion* hr) {
      // Avoid stalling safepoints and stop iteration if mark cycle has been aborted.
      _cm->do_yield_check();
      if (_cm->has_aborted()) {
        return true;
      }

      HeapWord* const pb = hr->parsable_bottom_acquire();

      if (!should_rebuild_or_scrub(hr)) {
        // Region has been allocated during this phase, no need to either scrub or
        // scan to rebuild remembered sets.
        log_trace(gc, marking)("Scrub and rebuild region skipped for " HR_FORMAT " pb: " PTR_FORMAT,
                               HR_FORMAT_PARAMS(hr), p2i(pb));
        assert(hr->bottom() == pb, "Region must be fully parsable");
        return false;
      }

      if (hr->needs_scrubbing()) {
        // This is a region with potentially unparsable (dead) objects.
        scan_and_scrub_region(hr, pb);
      } else {
        assert(hr->is_humongous(), "must be, but %u is %s", hr->hrm_index(), hr->get_short_type_str());
        // No need to scrub humongous, but we should scan it to rebuild remsets.
        scan_humongous_region(hr, pb);
      }

      return _cm->has_aborted();
    }
  };

public:
  G1RebuildRSAndScrubTask(G1ConcurrentMark* cm, bool should_rebuild_remset, uint num_workers) :
    WorkerTask("Scrub dead objects"),
    _cm(cm),
    _hr_claimer(num_workers),
    _should_rebuild_remset(should_rebuild_remset) { }

  void work(uint worker_id) {
    SuspendibleThreadSetJoiner sts_join;

    G1CollectedHeap* g1h = G1CollectedHeap::heap();
    G1RebuildRSAndScrubRegionClosure cl(_cm, _should_rebuild_remset, worker_id);
    g1h->heap_region_par_iterate_from_worker_offset(&cl, &_hr_claimer, worker_id);
  }
};

void G1ConcurrentRebuildAndScrub::rebuild_and_scrub(G1ConcurrentMark* cm, bool should_rebuild_remset, WorkerThreads* workers) {
  uint num_workers = workers->active_workers();

  G1RebuildRSAndScrubTask task(cm, should_rebuild_remset, num_workers);
  workers->run_task(&task, num_workers);
}
