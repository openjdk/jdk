/*
 * Copyright (c) 2016, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "code/nmethod.hpp"
#include "gc/g1/g1Allocator.inline.hpp"
#include "gc/g1/g1CollectedHeap.inline.hpp"
#include "gc/g1/g1ConcurrentMarkThread.hpp"
#include "gc/g1/g1HeapRegion.inline.hpp"
#include "gc/g1/g1HeapRegionRemSet.hpp"
#include "gc/g1/g1HeapVerifier.hpp"
#include "gc/g1/g1Policy.hpp"
#include "gc/g1/g1RemSet.hpp"
#include "gc/g1/g1RootProcessor.hpp"
#include "gc/shared/tlab_globals.hpp"
#include "logging/log.hpp"
#include "logging/logStream.hpp"
#include "memory/iterator.inline.hpp"
#include "memory/resourceArea.hpp"
#include "memory/universe.hpp"
#include "oops/access.inline.hpp"
#include "oops/compressedOops.inline.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/handles.inline.hpp"

int G1HeapVerifier::_enabled_verification_types = G1HeapVerifier::G1VerifyAll;

class VerifyRootsClosure: public OopClosure {
private:
  G1CollectedHeap* _g1h;
  VerifyOption     _vo;
  bool             _failures;

public:
  VerifyRootsClosure(VerifyOption vo) :
    _g1h(G1CollectedHeap::heap()),
    _vo(vo),
    _failures(false) { }

  bool failures() { return _failures; }

  template <class T> void do_oop_work(T* p) {
    T heap_oop = RawAccess<>::oop_load(p);
    if (!CompressedOops::is_null(heap_oop)) {
      oop obj = CompressedOops::decode_not_null(heap_oop);
      if (_g1h->is_obj_dead_cond(obj, _vo)) {
        Log(gc, verify) log;
        log.error("Root location " PTR_FORMAT " points to dead obj " PTR_FORMAT " in region " HR_FORMAT,
                  p2i(p), p2i(obj), HR_FORMAT_PARAMS(_g1h->heap_region_containing(obj)));
        ResourceMark rm;
        LogStream ls(log.error());
        obj->print_on(&ls);
        _failures = true;
      }
    }
  }

  void do_oop(oop* p)       { do_oop_work(p); }
  void do_oop(narrowOop* p) { do_oop_work(p); }
};

class G1VerifyCodeRootOopClosure: public OopClosure {
  G1CollectedHeap* _g1h;
  OopClosure* _root_cl;
  nmethod* _nm;
  VerifyOption _vo;
  bool _failures;

  template <class T> void do_oop_work(T* p) {
    // First verify that this root is live
    _root_cl->do_oop(p);

    if (!G1VerifyHeapRegionCodeRoots) {
      // We're not verifying the code roots attached to heap region.
      return;
    }

    // Don't check the code roots during marking verification in a full GC
    if (_vo == VerifyOption::G1UseFullMarking) {
      return;
    }

    // Now verify that the current nmethod (which contains p) is
    // in the code root list of the heap region containing the
    // object referenced by p.

    T heap_oop = RawAccess<>::oop_load(p);
    if (!CompressedOops::is_null(heap_oop)) {
      oop obj = CompressedOops::decode_not_null(heap_oop);

      // Now fetch the region containing the object
      HeapRegion* hr = _g1h->heap_region_containing(obj);
      HeapRegionRemSet* hrrs = hr->rem_set();
      // Verify that the code root list for this region
      // contains the nmethod
      if (!hrrs->code_roots_list_contains(_nm)) {
        log_error(gc, verify)("Code root location " PTR_FORMAT " "
                              "from nmethod " PTR_FORMAT " not in strong "
                              "code roots for region [" PTR_FORMAT "," PTR_FORMAT ")",
                              p2i(p), p2i(_nm), p2i(hr->bottom()), p2i(hr->end()));
        _failures = true;
      }
    }
  }

public:
  G1VerifyCodeRootOopClosure(G1CollectedHeap* g1h, OopClosure* root_cl, VerifyOption vo):
    _g1h(g1h), _root_cl(root_cl), _nm(nullptr), _vo(vo), _failures(false) {}

  void do_oop(oop* p) { do_oop_work(p); }
  void do_oop(narrowOop* p) { do_oop_work(p); }

  void set_nmethod(nmethod* nm) { _nm = nm; }
  bool failures() { return _failures; }
};

class G1VerifyCodeRootBlobClosure: public CodeBlobClosure {
  G1VerifyCodeRootOopClosure* _oop_cl;

public:
  G1VerifyCodeRootBlobClosure(G1VerifyCodeRootOopClosure* oop_cl):
    _oop_cl(oop_cl) {}

  void do_code_blob(CodeBlob* cb) {
    nmethod* nm = cb->as_nmethod_or_null();
    if (nm != nullptr) {
      _oop_cl->set_nmethod(nm);
      nm->oops_do(_oop_cl);
    }
  }
};

class YoungRefCounterClosure : public OopClosure {
  G1CollectedHeap* _g1h;
  int              _count;
 public:
  YoungRefCounterClosure(G1CollectedHeap* g1h) : _g1h(g1h), _count(0) {}
  void do_oop(oop* p)       { if (_g1h->is_in_young(*p)) { _count++; } }
  void do_oop(narrowOop* p) { ShouldNotReachHere(); }

  int count() { return _count; }
  void reset_count() { _count = 0; };
};

class VerifyCLDClosure: public CLDClosure {
  YoungRefCounterClosure _young_ref_counter_closure;
  OopClosure *_oop_closure;
 public:
  VerifyCLDClosure(G1CollectedHeap* g1h, OopClosure* cl) : _young_ref_counter_closure(g1h), _oop_closure(cl) {}
  void do_cld(ClassLoaderData* cld) {
    cld->oops_do(_oop_closure, ClassLoaderData::_claim_none);

    _young_ref_counter_closure.reset_count();
    cld->oops_do(&_young_ref_counter_closure, ClassLoaderData::_claim_none);
    if (_young_ref_counter_closure.count() > 0) {
      guarantee(cld->has_modified_oops(), "CLD " PTR_FORMAT ", has young %d refs but is not dirty.", p2i(cld), _young_ref_counter_closure.count());
    }
  }
};

class VerifyLivenessOopClosure: public BasicOopIterateClosure {
  G1CollectedHeap* _g1h;
  VerifyOption _vo;
public:
  VerifyLivenessOopClosure(G1CollectedHeap* g1h, VerifyOption vo):
    _g1h(g1h), _vo(vo)
  { }
  void do_oop(narrowOop *p) { do_oop_work(p); }
  void do_oop(      oop *p) { do_oop_work(p); }

  template <class T> void do_oop_work(T *p) {
    oop obj = RawAccess<>::oop_load(p);
    guarantee(obj == nullptr || !_g1h->is_obj_dead_cond(obj, _vo),
              "Dead object referenced by a not dead object");
  }
};

class VerifyObjsInRegionClosure: public ObjectClosure {
private:
  G1CollectedHeap* _g1h;
  size_t _live_bytes;
  HeapRegion *_hr;
  VerifyOption _vo;

public:
  VerifyObjsInRegionClosure(HeapRegion *hr, VerifyOption vo)
    : _live_bytes(0), _hr(hr), _vo(vo) {
    _g1h = G1CollectedHeap::heap();
  }
  void do_object(oop o) {
    VerifyLivenessOopClosure isLive(_g1h, _vo);
    assert(o != nullptr, "Huh?");
    if (!_g1h->is_obj_dead_cond(o, _vo)) {
      // If the object is alive according to the full gc mark,
      // then verify that the marking information agrees.
      // Note we can't verify the contra-positive of the
      // above: if the object is dead (according to the mark
      // word), it may not be marked, or may have been marked
      // but has since became dead, or may have been allocated
      // since the last marking.
      if (_vo == VerifyOption::G1UseFullMarking) {
        guarantee(!_g1h->is_obj_dead(o), "Full GC marking and concurrent mark mismatch");
      }

      o->oop_iterate(&isLive);
      if (!_hr->is_in_parsable_area(o)) {
        size_t obj_size = o->size();
        _live_bytes += (obj_size * HeapWordSize);
      }
    }
  }
  size_t live_bytes() { return _live_bytes; }
};

class VerifyRegionClosure: public HeapRegionClosure {
private:
  VerifyOption     _vo;
  bool             _failures;

public:
  VerifyRegionClosure(VerifyOption vo)
    : _vo(vo),
      _failures(false) {}

  bool failures() {
    return _failures;
  }

  bool do_heap_region(HeapRegion* r) {
    guarantee(!r->has_index_in_opt_cset(), "Region %u still has opt collection set index %u", r->hrm_index(), r->index_in_opt_cset());
    guarantee(!r->is_young() || r->rem_set()->is_complete(), "Remembered set for Young region %u must be complete, is %s", r->hrm_index(), r->rem_set()->get_state_str());
    // Humongous and old regions regions might be of any state, so can't check here.
    guarantee(!r->is_free() || !r->rem_set()->is_tracked(), "Remembered set for free region %u must be untracked, is %s", r->hrm_index(), r->rem_set()->get_state_str());

    if (r->is_continues_humongous()) {
      // Verify that the continues humongous regions' remembered set state
      // matches the one from the starts humongous region.
      if (r->rem_set()->get_state_str() != r->humongous_start_region()->rem_set()->get_state_str()) {
         log_error(gc, verify)("Remset states differ: Region %u (%s) remset %s with starts region %u (%s) remset %s",
                               r->hrm_index(),
                               r->get_short_type_str(),
                               r->rem_set()->get_state_str(),
                               r->humongous_start_region()->hrm_index(),
                               r->humongous_start_region()->get_short_type_str(),
                               r->humongous_start_region()->rem_set()->get_state_str());
         _failures = true;
      }
    } else {
      if (r->verify(_vo)) {
        _failures = true;
      } else if (!r->is_starts_humongous()) {
        VerifyObjsInRegionClosure not_dead_yet_cl(r, _vo);
        r->object_iterate(&not_dead_yet_cl);
        if (r->live_bytes() < not_dead_yet_cl.live_bytes()) {
          log_error(gc, verify)(HR_FORMAT " max_live_bytes %zu < calculated %zu",
                                HR_FORMAT_PARAMS(r), r->live_bytes(), not_dead_yet_cl.live_bytes());
          _failures = true;
        }
      }
    }

    // stop the region iteration if we hit a failure
    return _failures;
  }
};

// This is the task used for verification of the heap regions

class G1VerifyTask: public WorkerTask {
private:
  G1CollectedHeap*  _g1h;
  VerifyOption      _vo;
  bool              _failures;
  HeapRegionClaimer _hrclaimer;

public:
  G1VerifyTask(G1CollectedHeap* g1h, VerifyOption vo) :
      WorkerTask("Verify task"),
      _g1h(g1h),
      _vo(vo),
      _failures(false),
      _hrclaimer(g1h->workers()->active_workers()) {}

  bool failures() {
    return _failures;
  }

  void work(uint worker_id) {
    VerifyRegionClosure blk(_vo);
    _g1h->heap_region_par_iterate_from_worker_offset(&blk, &_hrclaimer, worker_id);
    if (blk.failures()) {
      _failures = true;
    }
  }
};

void G1HeapVerifier::enable_verification_type(G1VerifyType type) {
  // First enable will clear _enabled_verification_types.
  if (_enabled_verification_types == G1VerifyAll) {
    _enabled_verification_types = type;
  } else {
    _enabled_verification_types |= type;
  }
}

bool G1HeapVerifier::should_verify(G1VerifyType type) {
  return (_enabled_verification_types & type) != 0;
}

void G1HeapVerifier::verify(VerifyOption vo) {
  assert_at_safepoint_on_vm_thread();
  assert(Heap_lock->is_locked(), "heap must be locked");

  log_debug(gc, verify)("Roots");
  VerifyRootsClosure rootsCl(vo);
  VerifyCLDClosure cldCl(_g1h, &rootsCl);

  // We apply the relevant closures to all the oops in the
  // system dictionary, class loader data graph, the string table
  // and the nmethods in the code cache.
  G1VerifyCodeRootOopClosure codeRootsCl(_g1h, &rootsCl, vo);
  G1VerifyCodeRootBlobClosure blobsCl(&codeRootsCl);

  {
    G1RootProcessor root_processor(_g1h, 1);
    root_processor.process_all_roots(&rootsCl, &cldCl, &blobsCl);
  }

  bool failures = rootsCl.failures() || codeRootsCl.failures();

  if (!_g1h->policy()->collector_state()->in_full_gc()) {
    // If we're verifying during a full GC then the region sets
    // will have been torn down at the start of the GC. Therefore
    // verifying the region sets will fail. So we only verify
    // the region sets when not in a full GC.
    log_debug(gc, verify)("HeapRegionSets");
    verify_region_sets();
  }

  log_debug(gc, verify)("HeapRegions");

  G1VerifyTask task(_g1h, vo);
  _g1h->workers()->run_task(&task);
  if (failures || task.failures()) {
    log_error(gc, verify)("Heap after failed verification (kind %u):",
                          static_cast<std::underlying_type_t<VerifyOption>>(vo));
    // It helps to have the per-region information in the output to
    // help us track down what went wrong. This is why we call
    // print_extended_on() instead of print_on().
    Log(gc, verify) log;
    ResourceMark rm;
    LogStream ls(log.error());
    _g1h->print_extended_on(&ls);

    fatal("there should not have been any failures");
  }
}

// Heap region set verification

class VerifyRegionListsClosure : public HeapRegionClosure {
private:
  HeapRegionSet*   _old_set;
  HeapRegionSet*   _humongous_set;
  HeapRegionManager* _hrm;

public:
  uint _old_count;
  uint _humongous_count;
  uint _free_count;

  VerifyRegionListsClosure(HeapRegionSet* old_set,
                           HeapRegionSet* humongous_set,
                           HeapRegionManager* hrm) :
    _old_set(old_set), _humongous_set(humongous_set), _hrm(hrm),
    _old_count(), _humongous_count(), _free_count(){ }

  bool do_heap_region(HeapRegion* hr) {
    if (hr->is_young()) {
      // TODO
    } else if (hr->is_humongous()) {
      assert(hr->containing_set() == _humongous_set, "Heap region %u is humongous but not in humongous set.", hr->hrm_index());
      _humongous_count++;
    } else if (hr->is_empty()) {
      assert(_hrm->is_free(hr), "Heap region %u is empty but not on the free list.", hr->hrm_index());
      _free_count++;
    } else if (hr->is_old()) {
      assert(hr->containing_set() == _old_set, "Heap region %u is old but not in the old set.", hr->hrm_index());
      _old_count++;
    } else {
      fatal("Invalid region type for region %u (%s)", hr->hrm_index(), hr->get_short_type_str());
    }
    return false;
  }

  void verify_counts(HeapRegionSet* old_set, HeapRegionSet* humongous_set, HeapRegionManager* free_list) {
    guarantee(old_set->length() == _old_count, "Old set count mismatch. Expected %u, actual %u.", old_set->length(), _old_count);
    guarantee(humongous_set->length() == _humongous_count, "Hum set count mismatch. Expected %u, actual %u.", humongous_set->length(), _humongous_count);
    guarantee(free_list->num_free_regions() == _free_count, "Free list count mismatch. Expected %u, actual %u.", free_list->num_free_regions(), _free_count);
  }
};

void G1HeapVerifier::verify_region_sets() {
  assert_heap_locked_or_at_safepoint(true /* should_be_vm_thread */);

  // First, check the explicit lists.
  _g1h->_hrm.verify();

  // Finally, make sure that the region accounting in the lists is
  // consistent with what we see in the heap.

  VerifyRegionListsClosure cl(&_g1h->_old_set, &_g1h->_humongous_set, &_g1h->_hrm);
  _g1h->heap_region_iterate(&cl);
  cl.verify_counts(&_g1h->_old_set, &_g1h->_humongous_set, &_g1h->_hrm);

  _g1h->collection_set()->candidates()->verify();
}

class G1VerifyRegionMarkingStateClosure : public HeapRegionClosure {
  class MarkedBytesClosure {
    size_t _marked_words;

  public:
    MarkedBytesClosure() : _marked_words(0) { }

    inline size_t apply(oop obj) {
      size_t result = obj->size();
      _marked_words += result;
      return result;
    }

    size_t marked_bytes() const { return _marked_words * HeapWordSize; }
  };

public:
  virtual bool do_heap_region(HeapRegion* r) {
    if (r->is_free()) {
      return false;
    }

    G1ConcurrentMark* cm = G1CollectedHeap::heap()->concurrent_mark();

    bool part_of_marking = r->is_old_or_humongous() && !r->is_collection_set_candidate();

    if (part_of_marking) {
      guarantee(r->bottom() != r->top_at_mark_start(), "region %u (%s) does not have TAMS set",
                                                       r->hrm_index(), r->get_short_type_str());
      size_t marked_bytes = cm->live_bytes(r->hrm_index());

      MarkedBytesClosure cl;
      r->apply_to_marked_objects(cm->mark_bitmap(), &cl);

      guarantee(cl.marked_bytes() == marked_bytes,
                "region %u (%s) live bytes actual %zu and cache %zu differ",
                r->hrm_index(), r->get_short_type_str(), cl.marked_bytes(), marked_bytes);
    } else {
      guarantee(r->bottom() == r->top_at_mark_start(),
                "region %u (%s) has TAMS set " PTR_FORMAT " " PTR_FORMAT,
                r->hrm_index(), r->get_short_type_str(), p2i(r->bottom()), p2i(r->top_at_mark_start()));
      guarantee(cm->live_bytes(r->hrm_index()) == 0,
                "region %u (%s) has %zu live bytes recorded",
                r->hrm_index(), r->get_short_type_str(), cm->live_bytes(r->hrm_index()));
      guarantee(cm->mark_bitmap()->get_next_marked_addr(r->bottom(), r->end()) == r->end(),
                "region %u (%s) has mark",
                r->hrm_index(), r->get_short_type_str());
      guarantee(cm->is_root_region(r),
                "region %u (%s) should be root region",
                r->hrm_index(), r->get_short_type_str());
    }
    return false;
  }
};

void G1HeapVerifier::verify_marking_state() {
  assert(G1CollectedHeap::heap()->collector_state()->in_concurrent_start_gc(), "must be");

  // Verify TAMSes, bitmaps and liveness statistics.
  //
  // - if part of marking: TAMS != bottom, liveness == 0, bitmap clear
  // - if evacuation failed + part of marking: TAMS != bottom, liveness != 0, bitmap has at least on object set (corresponding to liveness)
  // - if not part of marking: TAMS == bottom, liveness == 0, bitmap clear; must be in root region

  // To compare liveness recorded in G1ConcurrentMark and actual we need to flush the
  // cache.
  G1CollectedHeap::heap()->concurrent_mark()->flush_all_task_caches();

  G1VerifyRegionMarkingStateClosure cl;
  _g1h->heap_region_iterate(&cl);
}

void G1HeapVerifier::prepare_for_verify() {
  if (SafepointSynchronize::is_at_safepoint() || ! UseTLAB) {
    _g1h->ensure_parsability(false);
  }
}

void G1HeapVerifier::verify(VerifyOption vo, const char* msg) {
  if (_g1h->total_collections() >= VerifyGCStartAt) {
    prepare_for_verify();
    Universe::verify(vo, msg);
  }
}

void G1HeapVerifier::verify_before_gc() {
  verify(VerifyOption::G1UseConcMarking, "Before GC");
}

void G1HeapVerifier::verify_after_gc() {
  verify(VerifyOption::G1UseConcMarking, "After GC");
}

void G1HeapVerifier::verify_bitmap_clear(bool from_tams) {
  if (!G1VerifyBitmaps) {
    return;
  }

  class G1VerifyBitmapClear : public HeapRegionClosure {
    bool _from_tams;

  public:
    G1VerifyBitmapClear(bool from_tams) : _from_tams(from_tams) { }

    virtual bool do_heap_region(HeapRegion* r) {
      G1CMBitMap* bitmap = G1CollectedHeap::heap()->concurrent_mark()->mark_bitmap();

      HeapWord* start = _from_tams ? r->top_at_mark_start() : r->bottom();

      HeapWord* mark = bitmap->get_next_marked_addr(start, r->end());
      guarantee(mark == r->end(), "Found mark at " PTR_FORMAT " in region %u from start " PTR_FORMAT, p2i(mark), r->hrm_index(), p2i(start));
      return false;
    }
  } cl(from_tams);

  G1CollectedHeap::heap()->heap_region_iterate(&cl);
}

#ifndef PRODUCT
class G1VerifyCardTableCleanup: public HeapRegionClosure {
  G1HeapVerifier* _verifier;
public:
  G1VerifyCardTableCleanup(G1HeapVerifier* verifier)
    : _verifier(verifier) { }
  virtual bool do_heap_region(HeapRegion* r) {
    if (r->is_survivor()) {
      _verifier->verify_dirty_region(r);
    } else {
      _verifier->verify_not_dirty_region(r);
    }
    return false;
  }
};

void G1HeapVerifier::verify_card_table_cleanup() {
  if (VerifyAfterGC) {
    G1VerifyCardTableCleanup cleanup_verifier(this);
    _g1h->heap_region_iterate(&cleanup_verifier);
  }
}

void G1HeapVerifier::verify_not_dirty_region(HeapRegion* hr) {
  // All of the region should be clean.
  G1CardTable* ct = _g1h->card_table();
  MemRegion mr(hr->bottom(), hr->end());
  ct->verify_not_dirty_region(mr);
}

void G1HeapVerifier::verify_dirty_region(HeapRegion* hr) {
  // We cannot guarantee that [bottom(),end()] is dirty.  Threads
  // dirty allocated blocks as they allocate them. The thread that
  // retires each region and replaces it with a new one will do a
  // maximal allocation to fill in [pre_dummy_top(),end()] but will
  // not dirty that area (one less thing to have to do while holding
  // a lock). So we can only verify that [bottom(),pre_dummy_top()]
  // is dirty.
  G1CardTable* ct = _g1h->card_table();
  MemRegion mr(hr->bottom(), hr->pre_dummy_top());
  if (hr->is_young()) {
    ct->verify_g1_young_region(mr);
  } else {
    ct->verify_dirty_region(mr);
  }
}

class G1VerifyDirtyYoungListClosure : public HeapRegionClosure {
private:
  G1HeapVerifier* _verifier;
public:
  G1VerifyDirtyYoungListClosure(G1HeapVerifier* verifier) : HeapRegionClosure(), _verifier(verifier) { }
  virtual bool do_heap_region(HeapRegion* r) {
    _verifier->verify_dirty_region(r);
    return false;
  }
};

void G1HeapVerifier::verify_dirty_young_regions() {
  G1VerifyDirtyYoungListClosure cl(this);
  _g1h->collection_set()->iterate(&cl);
}

class G1CheckRegionAttrTableClosure : public HeapRegionClosure {
private:
  bool _failures;

public:
  G1CheckRegionAttrTableClosure() : HeapRegionClosure(), _failures(false) { }

  virtual bool do_heap_region(HeapRegion* hr) {
    uint i = hr->hrm_index();
    G1HeapRegionAttr region_attr = (G1HeapRegionAttr) G1CollectedHeap::heap()->_region_attr.get_by_index(i);
    if (hr->is_humongous()) {
      if (hr->in_collection_set()) {
        log_error(gc, verify)("## humongous region %u in CSet", i);
        _failures = true;
        return true;
      }
      if (region_attr.is_in_cset()) {
        log_error(gc, verify)("## inconsistent region attr type %s for humongous region %u", region_attr.get_type_str(), i);
        _failures = true;
        return true;
      }
      if (hr->is_continues_humongous() && region_attr.is_humongous_candidate()) {
        log_error(gc, verify)("## inconsistent region attr type %s for continues humongous region %u", region_attr.get_type_str(), i);
        _failures = true;
        return true;
      }
    } else {
      if (region_attr.is_humongous_candidate()) {
        log_error(gc, verify)("## inconsistent region attr type %s for non-humongous region %u", region_attr.get_type_str(), i);
        _failures = true;
        return true;
      }
      if (hr->in_collection_set() != region_attr.is_in_cset()) {
        log_error(gc, verify)("## in CSet %d / region attr type %s inconsistency for region %u",
                             hr->in_collection_set(), region_attr.get_type_str(), i);
        _failures = true;
        return true;
      }
      if (region_attr.is_in_cset()) {
        if (hr->is_young() != (region_attr.is_young())) {
          log_error(gc, verify)("## is_young %d / region attr type %s inconsistency for region %u",
                               hr->is_young(), region_attr.get_type_str(), i);
          _failures = true;
          return true;
        }
        if (hr->is_old() != (region_attr.is_old())) {
          log_error(gc, verify)("## is_old %d / region attr type %s inconsistency for region %u",
                               hr->is_old(), region_attr.get_type_str(), i);
          _failures = true;
          return true;
        }
      }
    }
    return false;
  }

  bool failures() const { return _failures; }
};

bool G1HeapVerifier::check_region_attr_table() {
  G1CheckRegionAttrTableClosure cl;
  _g1h->_hrm.iterate(&cl);
  return !cl.failures();
}
#endif // PRODUCT
