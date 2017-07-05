/*
 * Copyright (c) 2012, 2016, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/g1/dirtyCardQueue.hpp"
#include "gc/g1/g1CollectedHeap.inline.hpp"
#include "gc/g1/g1CollectorState.hpp"
#include "gc/g1/g1ConcurrentMark.inline.hpp"
#include "gc/g1/g1EvacFailure.hpp"
#include "gc/g1/g1HeapVerifier.hpp"
#include "gc/g1/g1OopClosures.inline.hpp"
#include "gc/g1/g1_globals.hpp"
#include "gc/g1/heapRegion.hpp"
#include "gc/g1/heapRegionRemSet.hpp"

class UpdateRSetDeferred : public OopsInHeapRegionClosure {
private:
  G1CollectedHeap* _g1;
  DirtyCardQueue *_dcq;
  G1SATBCardTableModRefBS* _ct_bs;

public:
  UpdateRSetDeferred(DirtyCardQueue* dcq) :
    _g1(G1CollectedHeap::heap()), _ct_bs(_g1->g1_barrier_set()), _dcq(dcq) {}

  virtual void do_oop(narrowOop* p) { do_oop_work(p); }
  virtual void do_oop(      oop* p) { do_oop_work(p); }
  template <class T> void do_oop_work(T* p) {
    assert(_from->is_in_reserved(p), "paranoia");
    assert(!_from->is_survivor(), "Unexpected evac failure in survivor region");

    if (!_from->is_in_reserved(oopDesc::load_decode_heap_oop(p))) {
      size_t card_index = _ct_bs->index_for(p);
      if (_ct_bs->mark_card_deferred(card_index)) {
        _dcq->enqueue((jbyte*)_ct_bs->byte_for_index(card_index));
      }
    }
  }
};

class RemoveSelfForwardPtrObjClosure: public ObjectClosure {
private:
  G1CollectedHeap* _g1;
  G1ConcurrentMark* _cm;
  HeapRegion* _hr;
  size_t _marked_bytes;
  OopsInHeapRegionClosure *_update_rset_cl;
  bool _during_initial_mark;
  uint _worker_id;
  HeapWord* _last_forwarded_object_end;

public:
  RemoveSelfForwardPtrObjClosure(HeapRegion* hr,
                                 OopsInHeapRegionClosure* update_rset_cl,
                                 bool during_initial_mark,
                                 uint worker_id) :
    _g1(G1CollectedHeap::heap()),
    _cm(_g1->concurrent_mark()),
    _hr(hr),
    _marked_bytes(0),
    _update_rset_cl(update_rset_cl),
    _during_initial_mark(during_initial_mark),
    _worker_id(worker_id),
    _last_forwarded_object_end(hr->bottom()) { }

  size_t marked_bytes() { return _marked_bytes; }

  // Iterate over the live objects in the region to find self-forwarded objects
  // that need to be kept live. We need to update the remembered sets of these
  // objects. Further update the BOT and marks.
  // We can coalesce and overwrite the remaining heap contents with dummy objects
  // as they have either been dead or evacuated (which are unreferenced now, i.e.
  // dead too) already.
  void do_object(oop obj) {
    HeapWord* obj_addr = (HeapWord*) obj;
    assert(_hr->is_in(obj_addr), "sanity");
    size_t obj_size = obj->size();
    HeapWord* obj_end = obj_addr + obj_size;

    if (obj->is_forwarded() && obj->forwardee() == obj) {
      // The object failed to move.

      zap_dead_objects(_last_forwarded_object_end, obj_addr);
      // We consider all objects that we find self-forwarded to be
      // live. What we'll do is that we'll update the prev marking
      // info so that they are all under PTAMS and explicitly marked.
      if (!_cm->isPrevMarked(obj)) {
        _cm->markPrev(obj);
      }
      if (_during_initial_mark) {
        // For the next marking info we'll only mark the
        // self-forwarded objects explicitly if we are during
        // initial-mark (since, normally, we only mark objects pointed
        // to by roots if we succeed in copying them). By marking all
        // self-forwarded objects we ensure that we mark any that are
        // still pointed to be roots. During concurrent marking, and
        // after initial-mark, we don't need to mark any objects
        // explicitly and all objects in the CSet are considered
        // (implicitly) live. So, we won't mark them explicitly and
        // we'll leave them over NTAMS.
        _cm->grayRoot(obj, obj_size, _worker_id, _hr);
      }
      _marked_bytes += (obj_size * HeapWordSize);
      obj->set_mark(markOopDesc::prototype());

      // While we were processing RSet buffers during the collection,
      // we actually didn't scan any cards on the collection set,
      // since we didn't want to update remembered sets with entries
      // that point into the collection set, given that live objects
      // from the collection set are about to move and such entries
      // will be stale very soon.
      // This change also dealt with a reliability issue which
      // involved scanning a card in the collection set and coming
      // across an array that was being chunked and looking malformed.
      // The problem is that, if evacuation fails, we might have
      // remembered set entries missing given that we skipped cards on
      // the collection set. So, we'll recreate such entries now.
      obj->oop_iterate(_update_rset_cl);

      _last_forwarded_object_end = obj_end;
      _hr->cross_threshold(obj_addr, obj_end);
    }
  }

  // Fill the memory area from start to end with filler objects, and update the BOT
  // and the mark bitmap accordingly.
  void zap_dead_objects(HeapWord* start, HeapWord* end) {
    if (start == end) {
      return;
    }

    size_t gap_size = pointer_delta(end, start);
    MemRegion mr(start, gap_size);
    if (gap_size >= CollectedHeap::min_fill_size()) {
      CollectedHeap::fill_with_objects(start, gap_size);

      HeapWord* end_first_obj = start + ((oop)start)->size();
      _hr->cross_threshold(start, end_first_obj);
      // Fill_with_objects() may have created multiple (i.e. two)
      // objects, as the max_fill_size() is half a region.
      // After updating the BOT for the first object, also update the
      // BOT for the second object to make the BOT complete.
      if (end_first_obj != end) {
        _hr->cross_threshold(end_first_obj, end);
#ifdef ASSERT
        size_t size_second_obj = ((oop)end_first_obj)->size();
        HeapWord* end_of_second_obj = end_first_obj + size_second_obj;
        assert(end == end_of_second_obj,
               "More than two objects were used to fill the area from " PTR_FORMAT " to " PTR_FORMAT ", "
               "second objects size " SIZE_FORMAT " ends at " PTR_FORMAT,
               p2i(start), p2i(end), size_second_obj, p2i(end_of_second_obj));
#endif
      }
    }
    _cm->clearRangePrevBitmap(mr);
  }

  void zap_remainder() {
    zap_dead_objects(_last_forwarded_object_end, _hr->top());
  }
};

class RemoveSelfForwardPtrHRClosure: public HeapRegionClosure {
  G1CollectedHeap* _g1h;
  uint _worker_id;
  HeapRegionClaimer* _hrclaimer;

  DirtyCardQueue _dcq;
  UpdateRSetDeferred _update_rset_cl;

public:
  RemoveSelfForwardPtrHRClosure(uint worker_id,
                                HeapRegionClaimer* hrclaimer) :
    _g1h(G1CollectedHeap::heap()),
    _dcq(&_g1h->dirty_card_queue_set()),
    _update_rset_cl(&_dcq),
    _worker_id(worker_id),
    _hrclaimer(hrclaimer) {
  }

  size_t remove_self_forward_ptr_by_walking_hr(HeapRegion* hr,
                                               bool during_initial_mark) {
    RemoveSelfForwardPtrObjClosure rspc(hr,
                                        &_update_rset_cl,
                                        during_initial_mark,
                                        _worker_id);
    _update_rset_cl.set_region(hr);
    hr->object_iterate(&rspc);
    // Need to zap the remainder area of the processed region.
    rspc.zap_remainder();

    return rspc.marked_bytes();
  }

  bool doHeapRegion(HeapRegion *hr) {
    bool during_initial_mark = _g1h->collector_state()->during_initial_mark_pause();
    bool during_conc_mark = _g1h->collector_state()->mark_in_progress();

    assert(!hr->is_pinned(), "Unexpected pinned region at index %u", hr->hrm_index());
    assert(hr->in_collection_set(), "bad CS");

    if (_hrclaimer->claim_region(hr->hrm_index())) {
      if (hr->evacuation_failed()) {
        hr->note_self_forwarding_removal_start(during_initial_mark,
                                               during_conc_mark);
        _g1h->verifier()->check_bitmaps("Self-Forwarding Ptr Removal", hr);

        // In the common case (i.e. when there is no evacuation
        // failure) we make sure that the following is done when
        // the region is freed so that it is "ready-to-go" when it's
        // re-allocated. However, when evacuation failure happens, a
        // region will remain in the heap and might ultimately be added
        // to a CSet in the future. So we have to be careful here and
        // make sure the region's RSet is ready for parallel iteration
        // whenever this might be required in the future.
        hr->rem_set()->reset_for_par_iteration();
        hr->reset_bot();

        size_t live_bytes = remove_self_forward_ptr_by_walking_hr(hr, during_initial_mark);

        hr->rem_set()->clean_strong_code_roots(hr);

        hr->note_self_forwarding_removal_end(during_initial_mark,
                                             during_conc_mark,
                                             live_bytes);
      }
    }
    return false;
  }
};

G1ParRemoveSelfForwardPtrsTask::G1ParRemoveSelfForwardPtrsTask() :
  AbstractGangTask("G1 Remove Self-forwarding Pointers"),
  _g1h(G1CollectedHeap::heap()),
  _hrclaimer(_g1h->workers()->active_workers()) { }

void G1ParRemoveSelfForwardPtrsTask::work(uint worker_id) {
  RemoveSelfForwardPtrHRClosure rsfp_cl(worker_id, &_hrclaimer);

  HeapRegion* hr = _g1h->start_cset_region_for_worker(worker_id);
  _g1h->collection_set_iterate_from(hr, &rsfp_cl);
}

G1RestorePreservedMarksTask::G1RestorePreservedMarksTask(OopAndMarkOopStack* preserved_objs) :
  AbstractGangTask("G1 Restore Preserved Marks"),
  _preserved_objs(preserved_objs) {}

void G1RestorePreservedMarksTask::work(uint worker_id) {
  OopAndMarkOopStack& cur = _preserved_objs[worker_id];
  while (!cur.is_empty()) {
    OopAndMarkOop elem = cur.pop();
    elem.set_mark();
  }
  cur.clear(true);
}
