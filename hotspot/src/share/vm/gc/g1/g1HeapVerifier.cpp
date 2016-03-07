/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
#include "logging/log.hpp"
#include "gc/g1/concurrentMarkThread.hpp"
#include "gc/g1/g1CollectedHeap.hpp"
#include "gc/g1/g1CollectedHeap.inline.hpp"
#include "gc/g1/g1HeapVerifier.hpp"
#include "gc/g1/g1MarkSweep.hpp"
#include "gc/g1/g1RemSet.hpp"
#include "gc/g1/g1RootProcessor.hpp"
#include "gc/g1/heapRegion.hpp"
#include "gc/g1/heapRegion.inline.hpp"
#include "gc/g1/heapRegionRemSet.hpp"
#include "gc/g1/g1StringDedup.hpp"
#include "gc/g1/youngList.hpp"
#include "memory/resourceArea.hpp"
#include "oops/oop.inline.hpp"

class VerifyRootsClosure: public OopClosure {
private:
  G1CollectedHeap* _g1h;
  VerifyOption     _vo;
  bool             _failures;
public:
  // _vo == UsePrevMarking -> use "prev" marking information,
  // _vo == UseNextMarking -> use "next" marking information,
  // _vo == UseMarkWord    -> use mark word from object header.
  VerifyRootsClosure(VerifyOption vo) :
    _g1h(G1CollectedHeap::heap()),
    _vo(vo),
    _failures(false) { }

  bool failures() { return _failures; }

  template <class T> void do_oop_nv(T* p) {
    T heap_oop = oopDesc::load_heap_oop(p);
    if (!oopDesc::is_null(heap_oop)) {
      oop obj = oopDesc::decode_heap_oop_not_null(heap_oop);
      if (_g1h->is_obj_dead_cond(obj, _vo)) {
        LogHandle(gc, verify) log;
        log.info("Root location " PTR_FORMAT " points to dead obj " PTR_FORMAT, p2i(p), p2i(obj));
        if (_vo == VerifyOption_G1UseMarkWord) {
          log.error("  Mark word: " PTR_FORMAT, p2i(obj->mark()));
        }
        ResourceMark rm;
        obj->print_on(log.error_stream());
        _failures = true;
      }
    }
  }

  void do_oop(oop* p)       { do_oop_nv(p); }
  void do_oop(narrowOop* p) { do_oop_nv(p); }
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
    if (_vo == VerifyOption_G1UseMarkWord) {
      return;
    }

    // Now verify that the current nmethod (which contains p) is
    // in the code root list of the heap region containing the
    // object referenced by p.

    T heap_oop = oopDesc::load_heap_oop(p);
    if (!oopDesc::is_null(heap_oop)) {
      oop obj = oopDesc::decode_heap_oop_not_null(heap_oop);

      // Now fetch the region containing the object
      HeapRegion* hr = _g1h->heap_region_containing(obj);
      HeapRegionRemSet* hrrs = hr->rem_set();
      // Verify that the strong code root list for this region
      // contains the nmethod
      if (!hrrs->strong_code_roots_list_contains(_nm)) {
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
    _g1h(g1h), _root_cl(root_cl), _vo(vo), _nm(NULL), _failures(false) {}

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
    if (nm != NULL) {
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

class VerifyKlassClosure: public KlassClosure {
  YoungRefCounterClosure _young_ref_counter_closure;
  OopClosure *_oop_closure;
 public:
  VerifyKlassClosure(G1CollectedHeap* g1h, OopClosure* cl) : _young_ref_counter_closure(g1h), _oop_closure(cl) {}
  void do_klass(Klass* k) {
    k->oops_do(_oop_closure);

    _young_ref_counter_closure.reset_count();
    k->oops_do(&_young_ref_counter_closure);
    if (_young_ref_counter_closure.count() > 0) {
      guarantee(k->has_modified_oops(), "Klass " PTR_FORMAT ", has young refs but is not dirty.", p2i(k));
    }
  }
};

class VerifyLivenessOopClosure: public OopClosure {
  G1CollectedHeap* _g1h;
  VerifyOption _vo;
public:
  VerifyLivenessOopClosure(G1CollectedHeap* g1h, VerifyOption vo):
    _g1h(g1h), _vo(vo)
  { }
  void do_oop(narrowOop *p) { do_oop_work(p); }
  void do_oop(      oop *p) { do_oop_work(p); }

  template <class T> void do_oop_work(T *p) {
    oop obj = oopDesc::load_decode_heap_oop(p);
    guarantee(obj == NULL || !_g1h->is_obj_dead_cond(obj, _vo),
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
  // _vo == UsePrevMarking -> use "prev" marking information,
  // _vo == UseNextMarking -> use "next" marking information,
  // _vo == UseMarkWord    -> use mark word from object header.
  VerifyObjsInRegionClosure(HeapRegion *hr, VerifyOption vo)
    : _live_bytes(0), _hr(hr), _vo(vo) {
    _g1h = G1CollectedHeap::heap();
  }
  void do_object(oop o) {
    VerifyLivenessOopClosure isLive(_g1h, _vo);
    assert(o != NULL, "Huh?");
    if (!_g1h->is_obj_dead_cond(o, _vo)) {
      // If the object is alive according to the mark word,
      // then verify that the marking information agrees.
      // Note we can't verify the contra-positive of the
      // above: if the object is dead (according to the mark
      // word), it may not be marked, or may have been marked
      // but has since became dead, or may have been allocated
      // since the last marking.
      if (_vo == VerifyOption_G1UseMarkWord) {
        guarantee(!_g1h->is_obj_dead(o), "mark word and concurrent mark mismatch");
      }

      o->oop_iterate_no_header(&isLive);
      if (!_hr->obj_allocated_since_prev_marking(o)) {
        size_t obj_size = o->size();    // Make sure we don't overflow
        _live_bytes += (obj_size * HeapWordSize);
      }
    }
  }
  size_t live_bytes() { return _live_bytes; }
};

class VerifyArchiveOopClosure: public OopClosure {
public:
  VerifyArchiveOopClosure(HeapRegion *hr) { }
  void do_oop(narrowOop *p) { do_oop_work(p); }
  void do_oop(      oop *p) { do_oop_work(p); }

  template <class T> void do_oop_work(T *p) {
    oop obj = oopDesc::load_decode_heap_oop(p);
    guarantee(obj == NULL || G1MarkSweep::in_archive_range(obj),
              "Archive object at " PTR_FORMAT " references a non-archive object at " PTR_FORMAT,
              p2i(p), p2i(obj));
  }
};

class VerifyArchiveRegionClosure: public ObjectClosure {
public:
  VerifyArchiveRegionClosure(HeapRegion *hr) { }
  // Verify that all object pointers are to archive regions.
  void do_object(oop o) {
    VerifyArchiveOopClosure checkOop(NULL);
    assert(o != NULL, "Should not be here for NULL oops");
    o->oop_iterate_no_header(&checkOop);
  }
};

class VerifyRegionClosure: public HeapRegionClosure {
private:
  bool             _par;
  VerifyOption     _vo;
  bool             _failures;
public:
  // _vo == UsePrevMarking -> use "prev" marking information,
  // _vo == UseNextMarking -> use "next" marking information,
  // _vo == UseMarkWord    -> use mark word from object header.
  VerifyRegionClosure(bool par, VerifyOption vo)
    : _par(par),
      _vo(vo),
      _failures(false) {}

  bool failures() {
    return _failures;
  }

  bool doHeapRegion(HeapRegion* r) {
    // For archive regions, verify there are no heap pointers to
    // non-pinned regions. For all others, verify liveness info.
    if (r->is_archive()) {
      VerifyArchiveRegionClosure verify_oop_pointers(r);
      r->object_iterate(&verify_oop_pointers);
      return true;
    }
    if (!r->is_continues_humongous()) {
      bool failures = false;
      r->verify(_vo, &failures);
      if (failures) {
        _failures = true;
      } else if (!r->is_starts_humongous()) {
        VerifyObjsInRegionClosure not_dead_yet_cl(r, _vo);
        r->object_iterate(&not_dead_yet_cl);
        if (_vo != VerifyOption_G1UseNextMarking) {
          if (r->max_live_bytes() < not_dead_yet_cl.live_bytes()) {
            log_error(gc, verify)("[" PTR_FORMAT "," PTR_FORMAT "] max_live_bytes " SIZE_FORMAT " < calculated " SIZE_FORMAT,
                                  p2i(r->bottom()), p2i(r->end()), r->max_live_bytes(), not_dead_yet_cl.live_bytes());
            _failures = true;
          }
        } else {
          // When vo == UseNextMarking we cannot currently do a sanity
          // check on the live bytes as the calculation has not been
          // finalized yet.
        }
      }
    }
    return false; // stop the region iteration if we hit a failure
  }
};

// This is the task used for parallel verification of the heap regions

class G1ParVerifyTask: public AbstractGangTask {
private:
  G1CollectedHeap*  _g1h;
  VerifyOption      _vo;
  bool              _failures;
  HeapRegionClaimer _hrclaimer;

public:
  // _vo == UsePrevMarking -> use "prev" marking information,
  // _vo == UseNextMarking -> use "next" marking information,
  // _vo == UseMarkWord    -> use mark word from object header.
  G1ParVerifyTask(G1CollectedHeap* g1h, VerifyOption vo) :
      AbstractGangTask("Parallel verify task"),
      _g1h(g1h),
      _vo(vo),
      _failures(false),
      _hrclaimer(g1h->workers()->active_workers()) {}

  bool failures() {
    return _failures;
  }

  void work(uint worker_id) {
    HandleMark hm;
    VerifyRegionClosure blk(true, _vo);
    _g1h->heap_region_par_iterate(&blk, worker_id, &_hrclaimer);
    if (blk.failures()) {
      _failures = true;
    }
  }
};


void G1HeapVerifier::verify(VerifyOption vo) {
  if (!SafepointSynchronize::is_at_safepoint()) {
    log_info(gc, verify)("Skipping verification. Not at safepoint.");
  }

  assert(Thread::current()->is_VM_thread(),
         "Expected to be executed serially by the VM thread at this point");

  log_debug(gc, verify)("Roots");
  VerifyRootsClosure rootsCl(vo);
  VerifyKlassClosure klassCl(_g1h, &rootsCl);
  CLDToKlassAndOopClosure cldCl(&klassCl, &rootsCl, false);

  // We apply the relevant closures to all the oops in the
  // system dictionary, class loader data graph, the string table
  // and the nmethods in the code cache.
  G1VerifyCodeRootOopClosure codeRootsCl(_g1h, &rootsCl, vo);
  G1VerifyCodeRootBlobClosure blobsCl(&codeRootsCl);

  {
    G1RootProcessor root_processor(_g1h, 1);
    root_processor.process_all_roots(&rootsCl,
                                     &cldCl,
                                     &blobsCl);
  }

  bool failures = rootsCl.failures() || codeRootsCl.failures();

  if (vo != VerifyOption_G1UseMarkWord) {
    // If we're verifying during a full GC then the region sets
    // will have been torn down at the start of the GC. Therefore
    // verifying the region sets will fail. So we only verify
    // the region sets when not in a full GC.
    log_debug(gc, verify)("HeapRegionSets");
    verify_region_sets();
  }

  log_debug(gc, verify)("HeapRegions");
  if (GCParallelVerificationEnabled && ParallelGCThreads > 1) {

    G1ParVerifyTask task(_g1h, vo);
    _g1h->workers()->run_task(&task);
    if (task.failures()) {
      failures = true;
    }

  } else {
    VerifyRegionClosure blk(false, vo);
    _g1h->heap_region_iterate(&blk);
    if (blk.failures()) {
      failures = true;
    }
  }

  if (G1StringDedup::is_enabled()) {
    log_debug(gc, verify)("StrDedup");
    G1StringDedup::verify();
  }

  if (failures) {
    log_error(gc, verify)("Heap after failed verification:");
    // It helps to have the per-region information in the output to
    // help us track down what went wrong. This is why we call
    // print_extended_on() instead of print_on().
    LogHandle(gc, verify) log;
    ResourceMark rm;
    _g1h->print_extended_on(log.error_stream());
  }
  guarantee(!failures, "there should not have been any failures");
}

// Heap region set verification

class VerifyRegionListsClosure : public HeapRegionClosure {
private:
  HeapRegionSet*   _old_set;
  HeapRegionSet*   _humongous_set;
  HeapRegionManager*   _hrm;

public:
  uint _old_count;
  uint _humongous_count;
  uint _free_count;

  VerifyRegionListsClosure(HeapRegionSet* old_set,
                           HeapRegionSet* humongous_set,
                           HeapRegionManager* hrm) :
    _old_set(old_set), _humongous_set(humongous_set), _hrm(hrm),
    _old_count(), _humongous_count(), _free_count(){ }

  bool doHeapRegion(HeapRegion* hr) {
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
      // There are no other valid region types. Check for one invalid
      // one we can identify: pinned without old or humongous set.
      assert(!hr->is_pinned(), "Heap region %u is pinned but not old (archive) or humongous.", hr->hrm_index());
      ShouldNotReachHere();
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
  {
    // Given that a concurrent operation might be adding regions to
    // the secondary free list we have to take the lock before
    // verifying it.
    MutexLockerEx x(SecondaryFreeList_lock, Mutex::_no_safepoint_check_flag);
    _g1h->_secondary_free_list.verify_list();
  }

  // If a concurrent region freeing operation is in progress it will
  // be difficult to correctly attributed any free regions we come
  // across to the correct free list given that they might belong to
  // one of several (free_list, secondary_free_list, any local lists,
  // etc.). So, if that's the case we will skip the rest of the
  // verification operation. Alternatively, waiting for the concurrent
  // operation to complete will have a non-trivial effect on the GC's
  // operation (no concurrent operation will last longer than the
  // interval between two calls to verification) and it might hide
  // any issues that we would like to catch during testing.
  if (_g1h->free_regions_coming()) {
    return;
  }

  // Make sure we append the secondary_free_list on the free_list so
  // that all free regions we will come across can be safely
  // attributed to the free_list.
  _g1h->append_secondary_free_list_if_not_empty_with_lock();

  // Finally, make sure that the region accounting in the lists is
  // consistent with what we see in the heap.

  VerifyRegionListsClosure cl(&_g1h->_old_set, &_g1h->_humongous_set, &_g1h->_hrm);
  _g1h->heap_region_iterate(&cl);
  cl.verify_counts(&_g1h->_old_set, &_g1h->_humongous_set, &_g1h->_hrm);
}

void G1HeapVerifier::prepare_for_verify() {
  if (SafepointSynchronize::is_at_safepoint() || ! UseTLAB) {
    _g1h->ensure_parsability(false);
  }
  _g1h->g1_rem_set()->prepare_for_verify();
}

double G1HeapVerifier::verify(bool guard, const char* msg) {
  double verify_time_ms = 0.0;

  if (guard && _g1h->total_collections() >= VerifyGCStartAt) {
    double verify_start = os::elapsedTime();
    HandleMark hm;  // Discard invalid handles created during verification
    prepare_for_verify();
    Universe::verify(VerifyOption_G1UsePrevMarking, msg);
    verify_time_ms = (os::elapsedTime() - verify_start) * 1000;
  }

  return verify_time_ms;
}

void G1HeapVerifier::verify_before_gc() {
  double verify_time_ms = verify(VerifyBeforeGC, "Before GC");
  _g1h->g1_policy()->phase_times()->record_verify_before_time_ms(verify_time_ms);
}

void G1HeapVerifier::verify_after_gc() {
  double verify_time_ms = verify(VerifyAfterGC, "After GC");
  _g1h->g1_policy()->phase_times()->record_verify_after_time_ms(verify_time_ms);
}


#ifndef PRODUCT
class G1VerifyCardTableCleanup: public HeapRegionClosure {
  G1HeapVerifier* _verifier;
  G1SATBCardTableModRefBS* _ct_bs;
public:
  G1VerifyCardTableCleanup(G1HeapVerifier* verifier, G1SATBCardTableModRefBS* ct_bs)
    : _verifier(verifier), _ct_bs(ct_bs) { }
  virtual bool doHeapRegion(HeapRegion* r) {
    if (r->is_survivor()) {
      _verifier->verify_dirty_region(r);
    } else {
      _verifier->verify_not_dirty_region(r);
    }
    return false;
  }
};

void G1HeapVerifier::verify_card_table_cleanup() {
  if (G1VerifyCTCleanup || VerifyAfterGC) {
    G1VerifyCardTableCleanup cleanup_verifier(this, _g1h->g1_barrier_set());
    _g1h->heap_region_iterate(&cleanup_verifier);
  }
}

void G1HeapVerifier::verify_not_dirty_region(HeapRegion* hr) {
  // All of the region should be clean.
  G1SATBCardTableModRefBS* ct_bs = _g1h->g1_barrier_set();
  MemRegion mr(hr->bottom(), hr->end());
  ct_bs->verify_not_dirty_region(mr);
}

void G1HeapVerifier::verify_dirty_region(HeapRegion* hr) {
  // We cannot guarantee that [bottom(),end()] is dirty.  Threads
  // dirty allocated blocks as they allocate them. The thread that
  // retires each region and replaces it with a new one will do a
  // maximal allocation to fill in [pre_dummy_top(),end()] but will
  // not dirty that area (one less thing to have to do while holding
  // a lock). So we can only verify that [bottom(),pre_dummy_top()]
  // is dirty.
  G1SATBCardTableModRefBS* ct_bs = _g1h->g1_barrier_set();
  MemRegion mr(hr->bottom(), hr->pre_dummy_top());
  if (hr->is_young()) {
    ct_bs->verify_g1_young_region(mr);
  } else {
    ct_bs->verify_dirty_region(mr);
  }
}

void G1HeapVerifier::verify_dirty_young_list(HeapRegion* head) {
  G1SATBCardTableModRefBS* ct_bs = _g1h->g1_barrier_set();
  for (HeapRegion* hr = head; hr != NULL; hr = hr->get_next_young_region()) {
    verify_dirty_region(hr);
  }
}

void G1HeapVerifier::verify_dirty_young_regions() {
  verify_dirty_young_list(_g1h->young_list()->first_region());
}

bool G1HeapVerifier::verify_no_bits_over_tams(const char* bitmap_name, G1CMBitMapRO* bitmap,
                                               HeapWord* tams, HeapWord* end) {
  guarantee(tams <= end,
            "tams: " PTR_FORMAT " end: " PTR_FORMAT, p2i(tams), p2i(end));
  HeapWord* result = bitmap->getNextMarkedWordAddress(tams, end);
  if (result < end) {
    log_error(gc, verify)("## wrong marked address on %s bitmap: " PTR_FORMAT, bitmap_name, p2i(result));
    log_error(gc, verify)("## %s tams: " PTR_FORMAT " end: " PTR_FORMAT, bitmap_name, p2i(tams), p2i(end));
    return false;
  }
  return true;
}

bool G1HeapVerifier::verify_bitmaps(const char* caller, HeapRegion* hr) {
  G1CMBitMapRO* prev_bitmap = _g1h->concurrent_mark()->prevMarkBitMap();
  G1CMBitMapRO* next_bitmap = (G1CMBitMapRO*) _g1h->concurrent_mark()->nextMarkBitMap();

  HeapWord* bottom = hr->bottom();
  HeapWord* ptams  = hr->prev_top_at_mark_start();
  HeapWord* ntams  = hr->next_top_at_mark_start();
  HeapWord* end    = hr->end();

  bool res_p = verify_no_bits_over_tams("prev", prev_bitmap, ptams, end);

  bool res_n = true;
  // We reset mark_in_progress() before we reset _cmThread->in_progress() and in this window
  // we do the clearing of the next bitmap concurrently. Thus, we can not verify the bitmap
  // if we happen to be in that state.
  if (_g1h->collector_state()->mark_in_progress() || !_g1h->_cmThread->in_progress()) {
    res_n = verify_no_bits_over_tams("next", next_bitmap, ntams, end);
  }
  if (!res_p || !res_n) {
    log_error(gc, verify)("#### Bitmap verification failed for " HR_FORMAT, HR_FORMAT_PARAMS(hr));
    log_error(gc, verify)("#### Caller: %s", caller);
    return false;
  }
  return true;
}

void G1HeapVerifier::check_bitmaps(const char* caller, HeapRegion* hr) {
  if (!G1VerifyBitmaps) return;

  guarantee(verify_bitmaps(caller, hr), "bitmap verification");
}

class G1VerifyBitmapClosure : public HeapRegionClosure {
private:
  const char* _caller;
  G1HeapVerifier* _verifier;
  bool _failures;

public:
  G1VerifyBitmapClosure(const char* caller, G1HeapVerifier* verifier) :
    _caller(caller), _verifier(verifier), _failures(false) { }

  bool failures() { return _failures; }

  virtual bool doHeapRegion(HeapRegion* hr) {
    bool result = _verifier->verify_bitmaps(_caller, hr);
    if (!result) {
      _failures = true;
    }
    return false;
  }
};

void G1HeapVerifier::check_bitmaps(const char* caller) {
  if (!G1VerifyBitmaps) return;

  G1VerifyBitmapClosure cl(caller, this);
  _g1h->heap_region_iterate(&cl);
  guarantee(!cl.failures(), "bitmap verification");
}

class G1CheckCSetFastTableClosure : public HeapRegionClosure {
 private:
  bool _failures;
 public:
  G1CheckCSetFastTableClosure() : HeapRegionClosure(), _failures(false) { }

  virtual bool doHeapRegion(HeapRegion* hr) {
    uint i = hr->hrm_index();
    InCSetState cset_state = (InCSetState) G1CollectedHeap::heap()->_in_cset_fast_test.get_by_index(i);
    if (hr->is_humongous()) {
      if (hr->in_collection_set()) {
        log_error(gc, verify)("## humongous region %u in CSet", i);
        _failures = true;
        return true;
      }
      if (cset_state.is_in_cset()) {
        log_error(gc, verify)("## inconsistent cset state " CSETSTATE_FORMAT " for humongous region %u", cset_state.value(), i);
        _failures = true;
        return true;
      }
      if (hr->is_continues_humongous() && cset_state.is_humongous()) {
        log_error(gc, verify)("## inconsistent cset state " CSETSTATE_FORMAT " for continues humongous region %u", cset_state.value(), i);
        _failures = true;
        return true;
      }
    } else {
      if (cset_state.is_humongous()) {
        log_error(gc, verify)("## inconsistent cset state " CSETSTATE_FORMAT " for non-humongous region %u", cset_state.value(), i);
        _failures = true;
        return true;
      }
      if (hr->in_collection_set() != cset_state.is_in_cset()) {
        log_error(gc, verify)("## in CSet %d / cset state " CSETSTATE_FORMAT " inconsistency for region %u",
                             hr->in_collection_set(), cset_state.value(), i);
        _failures = true;
        return true;
      }
      if (cset_state.is_in_cset()) {
        if (hr->is_young() != (cset_state.is_young())) {
          log_error(gc, verify)("## is_young %d / cset state " CSETSTATE_FORMAT " inconsistency for region %u",
                               hr->is_young(), cset_state.value(), i);
          _failures = true;
          return true;
        }
        if (hr->is_old() != (cset_state.is_old())) {
          log_error(gc, verify)("## is_old %d / cset state " CSETSTATE_FORMAT " inconsistency for region %u",
                               hr->is_old(), cset_state.value(), i);
          _failures = true;
          return true;
        }
      }
    }
    return false;
  }

  bool failures() const { return _failures; }
};

bool G1HeapVerifier::check_cset_fast_test() {
  G1CheckCSetFastTableClosure cl;
  _g1h->_hrm.iterate(&cl);
  return !cl.failures();
}
#endif // PRODUCT
