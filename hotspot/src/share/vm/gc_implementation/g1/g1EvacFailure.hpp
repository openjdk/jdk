/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_IMPLEMENTATION_G1_G1EVACFAILURE_HPP
#define SHARE_VM_GC_IMPLEMENTATION_G1_G1EVACFAILURE_HPP

#include "gc_implementation/g1/concurrentMark.inline.hpp"
#include "gc_implementation/g1/dirtyCardQueue.hpp"
#include "gc_implementation/g1/g1CollectedHeap.inline.hpp"
#include "gc_implementation/g1/g1_globals.hpp"
#include "gc_implementation/g1/g1OopClosures.inline.hpp"
#include "gc_implementation/g1/heapRegion.hpp"
#include "gc_implementation/g1/heapRegionRemSet.hpp"
#include "utilities/workgroup.hpp"

// Closures and tasks associated with any self-forwarding pointers
// installed as a result of an evacuation failure.

class UpdateRSetDeferred : public OopsInHeapRegionClosure {
private:
  G1CollectedHeap* _g1;
  DirtyCardQueue *_dcq;
  CardTableModRefBS* _ct_bs;

public:
  UpdateRSetDeferred(G1CollectedHeap* g1, DirtyCardQueue* dcq) :
    _g1(g1), _ct_bs((CardTableModRefBS*)_g1->barrier_set()), _dcq(dcq) {}

  virtual void do_oop(narrowOop* p) { do_oop_work(p); }
  virtual void do_oop(      oop* p) { do_oop_work(p); }
  template <class T> void do_oop_work(T* p) {
    assert(_from->is_in_reserved(p), "paranoia");
    if (!_from->is_in_reserved(oopDesc::load_decode_heap_oop(p)) &&
        !_from->is_survivor()) {
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
  ConcurrentMark* _cm;
  HeapRegion* _hr;
  size_t _prev_marked_bytes;
  size_t _next_marked_bytes;
  OopsInHeapRegionClosure *_update_rset_cl;
public:
  RemoveSelfForwardPtrObjClosure(G1CollectedHeap* g1, ConcurrentMark* cm,
                                 HeapRegion* hr,
                                 OopsInHeapRegionClosure* update_rset_cl) :
    _g1(g1), _cm(cm), _hr(hr),
    _update_rset_cl(update_rset_cl),
    _prev_marked_bytes(0), _next_marked_bytes(0) {}

  size_t prev_marked_bytes() { return _prev_marked_bytes; }
  size_t next_marked_bytes() { return _next_marked_bytes; }

  // <original comment>
  // The original idea here was to coalesce evacuated and dead objects.
  // However that caused complications with the block offset table (BOT).
  // In particular if there were two TLABs, one of them partially refined.
  // |----- TLAB_1--------|----TLAB_2-~~~(partially refined part)~~~|
  // The BOT entries of the unrefined part of TLAB_2 point to the start
  // of TLAB_2. If the last object of the TLAB_1 and the first object
  // of TLAB_2 are coalesced, then the cards of the unrefined part
  // would point into middle of the filler object.
  // The current approach is to not coalesce and leave the BOT contents intact.
  // </original comment>
  //
  // We now reset the BOT when we start the object iteration over the
  // region and refine its entries for every object we come across. So
  // the above comment is not really relevant and we should be able
  // to coalesce dead objects if we want to.
  void do_object(oop obj) {
    HeapWord* obj_addr = (HeapWord*) obj;
    assert(_hr->is_in(obj_addr), "sanity");
    size_t obj_size = obj->size();

    _hr->update_bot_for_object(obj_addr, obj_size);

    if (obj->is_forwarded() && obj->forwardee() == obj) {
      // The object failed to move.
      assert(!_g1->is_obj_dead(obj), "We should not be preserving dead objs.");
      _cm->markPrev(obj);
      assert(_cm->isPrevMarked(obj), "Should be marked!");
      _prev_marked_bytes += (obj_size * HeapWordSize);
      if (_g1->mark_in_progress() && !_g1->is_obj_ill(obj)) {
        _cm->markAndGrayObjectIfNecessary(obj);
      }
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
      assert(_cm->isPrevMarked(obj), "Should be marked!");
    } else {
      // The object has been either evacuated or is dead. Fill it with a
      // dummy object.
      MemRegion mr((HeapWord*)obj, obj_size);
      CollectedHeap::fill_with_object(mr);
      _cm->clearRangeBothMaps(mr);
    }
  }
};

class RemoveSelfForwardPtrHRClosure: public HeapRegionClosure {
  G1CollectedHeap* _g1h;
  ConcurrentMark* _cm;
  OopsInHeapRegionClosure *_update_rset_cl;

public:
  RemoveSelfForwardPtrHRClosure(G1CollectedHeap* g1h,
                                OopsInHeapRegionClosure* update_rset_cl) :
    _g1h(g1h), _update_rset_cl(update_rset_cl),
    _cm(_g1h->concurrent_mark()) { }

  bool doHeapRegion(HeapRegion *hr) {
    assert(!hr->isHumongous(), "sanity");
    assert(hr->in_collection_set(), "bad CS");

    if (hr->claimHeapRegion(HeapRegion::ParEvacFailureClaimValue)) {
      if (hr->evacuation_failed()) {
        RemoveSelfForwardPtrObjClosure rspc(_g1h, _cm, hr, _update_rset_cl);

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
        _update_rset_cl->set_region(hr);
        hr->object_iterate(&rspc);

        // A number of manipulations to make the TAMS for this region
        // be the current top, and the marked bytes be the ones observed
        // in the iteration.
        if (_cm->at_least_one_mark_complete()) {
          // The comments below are the postconditions achieved by the
          // calls.  Note especially the last such condition, which says that
          // the count of marked bytes has been properly restored.
          hr->note_start_of_marking(false);
          // _next_top_at_mark_start == top, _next_marked_bytes == 0
          hr->add_to_marked_bytes(rspc.prev_marked_bytes());
          // _next_marked_bytes == prev_marked_bytes.
          hr->note_end_of_marking();
          // _prev_top_at_mark_start == top(),
          // _prev_marked_bytes == prev_marked_bytes
        }
        // If there is no mark in progress, we modified the _next variables
        // above needlessly, but harmlessly.
        if (_g1h->mark_in_progress()) {
          hr->note_start_of_marking(false);
          // _next_top_at_mark_start == top, _next_marked_bytes == 0
          // _next_marked_bytes == next_marked_bytes.
        }
      }
    }
    return false;
  }
};

class G1ParRemoveSelfForwardPtrsTask: public AbstractGangTask {
protected:
  G1CollectedHeap* _g1h;

public:
  G1ParRemoveSelfForwardPtrsTask(G1CollectedHeap* g1h) :
    AbstractGangTask("G1 Remove Self-forwarding Pointers"),
    _g1h(g1h) { }

  void work(uint worker_id) {
    UpdateRSetImmediate immediate_update(_g1h->g1_rem_set());
    DirtyCardQueue dcq(&_g1h->dirty_card_queue_set());
    UpdateRSetDeferred deferred_update(_g1h, &dcq);

    OopsInHeapRegionClosure *update_rset_cl = &deferred_update;
    if (!G1DeferredRSUpdate) {
      update_rset_cl = &immediate_update;
    }

    RemoveSelfForwardPtrHRClosure rsfp_cl(_g1h, update_rset_cl);

    HeapRegion* hr = _g1h->start_cset_region_for_worker(worker_id);
    _g1h->collection_set_iterate_from(hr, &rsfp_cl);
  }
};

#endif // SHARE_VM_GC_IMPLEMENTATION_G1_G1EVACFAILURE_HPP
