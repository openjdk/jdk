/*
 * Copyright (c) 2017, 2021, Red Hat, Inc. All rights reserved.
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
#include "gc/shared/tlab_globals.hpp"
#include "gc/shenandoah/shenandoahAsserts.hpp"
#include "gc/shenandoah/shenandoahForwarding.inline.hpp"
#include "gc/shenandoah/shenandoahPhaseTimings.hpp"
#include "gc/shenandoah/shenandoahGeneration.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahHeapRegion.inline.hpp"
#include "gc/shenandoah/shenandoahOldGeneration.hpp"
#include "gc/shenandoah/shenandoahRootProcessor.hpp"
#include "gc/shenandoah/shenandoahTaskqueue.inline.hpp"
#include "gc/shenandoah/shenandoahUtils.hpp"
#include "gc/shenandoah/shenandoahVerifier.hpp"
#include "gc/shenandoah/shenandoahYoungGeneration.hpp"
#include "memory/allocation.hpp"
#include "memory/iterator.inline.hpp"
#include "memory/resourceArea.hpp"
#include "oops/compressedOops.inline.hpp"
#include "runtime/atomic.hpp"
#include "runtime/orderAccess.hpp"
#include "runtime/threads.hpp"
#include "utilities/align.hpp"

// Avoid name collision on verify_oop (defined in macroAssembler_arm.hpp)
#ifdef verify_oop
#undef verify_oop
#endif

static bool is_instance_ref_klass(Klass* k) {
  return k->is_instance_klass() && InstanceKlass::cast(k)->reference_type() != REF_NONE;
}

class ShenandoahIgnoreReferenceDiscoverer : public ReferenceDiscoverer {
public:
  virtual bool discover_reference(oop obj, ReferenceType type) {
    return true;
  }
};

class ShenandoahVerifyOopClosure : public BasicOopIterateClosure {
private:
  const char* _phase;
  ShenandoahVerifier::VerifyOptions _options;
  ShenandoahVerifierStack* _stack;
  ShenandoahHeap* _heap;
  MarkBitMap* _map;
  ShenandoahLivenessData* _ld;
  void* _interior_loc;
  oop _loc;
  ShenandoahGeneration* _generation;

public:
  ShenandoahVerifyOopClosure(ShenandoahVerifierStack* stack, MarkBitMap* map, ShenandoahLivenessData* ld,
                             const char* phase, ShenandoahVerifier::VerifyOptions options) :
    _phase(phase),
    _options(options),
    _stack(stack),
    _heap(ShenandoahHeap::heap()),
    _map(map),
    _ld(ld),
    _interior_loc(nullptr),
    _loc(nullptr),
    _generation(nullptr) {
    if (options._verify_marked == ShenandoahVerifier::_verify_marked_complete_except_references ||
        options._verify_marked == ShenandoahVerifier::_verify_marked_complete_satb_empty ||
        options._verify_marked == ShenandoahVerifier::_verify_marked_disable) {
      set_ref_discoverer_internal(new ShenandoahIgnoreReferenceDiscoverer());
    }

    if (_heap->mode()->is_generational()) {
      _generation = _heap->active_generation();
      assert(_generation != nullptr, "Expected active generation in this mode");
    }
  }

private:
  void check(ShenandoahAsserts::SafeLevel level, oop obj, bool test, const char* label) {
    if (!test) {
      ShenandoahAsserts::print_failure(level, obj, _interior_loc, _loc, _phase, label, __FILE__, __LINE__);
    }
  }

  template <class T>
  void do_oop_work(T* p) {
    T o = RawAccess<>::oop_load(p);
    if (!CompressedOops::is_null(o)) {
      oop obj = CompressedOops::decode_not_null(o);
      if (is_instance_ref_klass(obj->klass())) {
        obj = ShenandoahForwarding::get_forwardee(obj);
      }
      // Single threaded verification can use faster non-atomic stack and bitmap
      // methods.
      //
      // For performance reasons, only fully verify non-marked field values.
      // We are here when the host object for *p is already marked.

      // TODO: We should consider specializing this closure by generation ==/!= null,
      // to avoid in_generation check on fast path here for non-generational mode.
      if (in_generation(obj) && _map->par_mark(obj)) {
        verify_oop_at(p, obj);
        _stack->push(ShenandoahVerifierTask(obj));
      }
    }
  }

  bool in_generation(oop obj) {
    if (_generation == nullptr) {
      return true;
    }

    ShenandoahHeapRegion* region = _heap->heap_region_containing(obj);
    return _generation->contains(region);
  }

  void verify_oop(oop obj) {
    // Perform consistency checks with gradually decreasing safety level. This guarantees
    // that failure report would not try to touch something that was not yet verified to be
    // safe to process.

    check(ShenandoahAsserts::_safe_unknown, obj, _heap->is_in(obj),
              "oop must be in heap");
    check(ShenandoahAsserts::_safe_unknown, obj, is_object_aligned(obj),
              "oop must be aligned");

    ShenandoahHeapRegion *obj_reg = _heap->heap_region_containing(obj);
    Klass* obj_klass = obj->klass_or_null();

    // Verify that obj is not in dead space:
    {
      // Do this before touching obj->size()
      check(ShenandoahAsserts::_safe_unknown, obj, obj_klass != nullptr,
             "Object klass pointer should not be null");
      check(ShenandoahAsserts::_safe_unknown, obj, Metaspace::contains(obj_klass),
             "Object klass pointer must go to metaspace");

      HeapWord *obj_addr = cast_from_oop<HeapWord*>(obj);
      check(ShenandoahAsserts::_safe_unknown, obj, obj_addr < obj_reg->top(),
             "Object start should be within the region");

      if (!obj_reg->is_humongous()) {
        check(ShenandoahAsserts::_safe_unknown, obj, (obj_addr + obj->size()) <= obj_reg->top(),
               "Object end should be within the region");
      } else {
        size_t humongous_start = obj_reg->index();
        size_t humongous_end = humongous_start + (obj->size() >> ShenandoahHeapRegion::region_size_words_shift());
        for (size_t idx = humongous_start + 1; idx < humongous_end; idx++) {
          check(ShenandoahAsserts::_safe_unknown, obj, _heap->get_region(idx)->is_humongous_continuation(),
                 "Humongous object is in continuation that fits it");
        }
      }

      // ------------ obj is safe at this point --------------

      check(ShenandoahAsserts::_safe_oop, obj, obj_reg->is_active(),
            "Object should be in active region");

      switch (_options._verify_liveness) {
        case ShenandoahVerifier::_verify_liveness_disable:
          // skip
          break;
        case ShenandoahVerifier::_verify_liveness_complete:
          Atomic::add(&_ld[obj_reg->index()], (uint) obj->size(), memory_order_relaxed);
          // fallthrough for fast failure for un-live regions:
        case ShenandoahVerifier::_verify_liveness_conservative:
          check(ShenandoahAsserts::_safe_oop, obj, obj_reg->has_live() ||
                (obj_reg->is_old() && ShenandoahHeap::heap()->is_gc_generation_young()),
                   "Object must belong to region with live data");
          break;
        default:
          assert(false, "Unhandled liveness verification");
      }
    }

    oop fwd = ShenandoahForwarding::get_forwardee_raw_unchecked(obj);

    ShenandoahHeapRegion* fwd_reg = nullptr;

    if (obj != fwd) {
      check(ShenandoahAsserts::_safe_oop, obj, _heap->is_in(fwd),
             "Forwardee must be in heap");
      check(ShenandoahAsserts::_safe_oop, obj, !CompressedOops::is_null(fwd),
             "Forwardee is set");
      check(ShenandoahAsserts::_safe_oop, obj, is_object_aligned(fwd),
             "Forwardee must be aligned");

      // Do this before touching fwd->size()
      Klass* fwd_klass = fwd->klass_or_null();
      check(ShenandoahAsserts::_safe_oop, obj, fwd_klass != nullptr,
             "Forwardee klass pointer should not be null");
      check(ShenandoahAsserts::_safe_oop, obj, Metaspace::contains(fwd_klass),
             "Forwardee klass pointer must go to metaspace");
      check(ShenandoahAsserts::_safe_oop, obj, obj_klass == fwd_klass,
             "Forwardee klass pointer must go to metaspace");

      fwd_reg = _heap->heap_region_containing(fwd);

      // Verify that forwardee is not in the dead space:
      check(ShenandoahAsserts::_safe_oop, obj, !fwd_reg->is_humongous(),
             "Should have no humongous forwardees");

      HeapWord *fwd_addr = cast_from_oop<HeapWord *>(fwd);
      check(ShenandoahAsserts::_safe_oop, obj, fwd_addr < fwd_reg->top(),
             "Forwardee start should be within the region");
      check(ShenandoahAsserts::_safe_oop, obj, (fwd_addr + fwd->size()) <= fwd_reg->top(),
             "Forwardee end should be within the region");

      oop fwd2 = ShenandoahForwarding::get_forwardee_raw_unchecked(fwd);
      check(ShenandoahAsserts::_safe_oop, obj, (fwd == fwd2),
             "Double forwarding");
    } else {
      fwd_reg = obj_reg;
    }

    // ------------ obj and fwd are safe at this point --------------
    // We allow for marked or old here for two reasons:
    //  1. If this is a young collect, old objects wouldn't be marked. We've
    //     recently change the verifier traversal to only follow young objects
    //     during a young collect so this _shouldn't_ be necessary.
    //  2. At present, we do not clear dead objects from the remembered set.
    //     Everything in the remembered set is old (ipso facto), so allowing for
    //     'marked_or_old' covers the case of stale objects in rset.
    // TODO: Just use 'is_marked' here.
    switch (_options._verify_marked) {
      case ShenandoahVerifier::_verify_marked_disable:
        // skip
        break;
      case ShenandoahVerifier::_verify_marked_incomplete:
        check(ShenandoahAsserts::_safe_all, obj, _heap->marking_context()->is_marked_or_old(obj),
               "Must be marked in incomplete bitmap");
        break;
      case ShenandoahVerifier::_verify_marked_complete:
        check(ShenandoahAsserts::_safe_all, obj, _heap->complete_marking_context()->is_marked_or_old(obj),
               "Must be marked in complete bitmap");
        break;
      case ShenandoahVerifier::_verify_marked_complete_except_references:
      case ShenandoahVerifier::_verify_marked_complete_satb_empty:
        check(ShenandoahAsserts::_safe_all, obj, _heap->complete_marking_context()->is_marked_or_old(obj),
              "Must be marked in complete bitmap, except j.l.r.Reference referents");
        break;
      default:
        assert(false, "Unhandled mark verification");
    }

    switch (_options._verify_forwarded) {
      case ShenandoahVerifier::_verify_forwarded_disable:
        // skip
        break;
      case ShenandoahVerifier::_verify_forwarded_none: {
        check(ShenandoahAsserts::_safe_all, obj, (obj == fwd),
               "Should not be forwarded");
        break;
      }
      case ShenandoahVerifier::_verify_forwarded_allow: {
        if (obj != fwd) {
          check(ShenandoahAsserts::_safe_all, obj, obj_reg != fwd_reg,
                 "Forwardee should be in another region");
        }
        break;
      }
      default:
        assert(false, "Unhandled forwarding verification");
    }

    switch (_options._verify_cset) {
      case ShenandoahVerifier::_verify_cset_disable:
        // skip
        break;
      case ShenandoahVerifier::_verify_cset_none:
        check(ShenandoahAsserts::_safe_all, obj, !_heap->in_collection_set(obj),
               "Should not have references to collection set");
        break;
      case ShenandoahVerifier::_verify_cset_forwarded:
        if (_heap->in_collection_set(obj)) {
          check(ShenandoahAsserts::_safe_all, obj, (obj != fwd),
                 "Object in collection set, should have forwardee");
        }
        break;
      default:
        assert(false, "Unhandled cset verification");
    }

  }

public:
  /**
   * Verify object with known interior reference.
   * @param p interior reference where the object is referenced from; can be off-heap
   * @param obj verified object
   */
  template <class T>
  void verify_oop_at(T* p, oop obj) {
    _interior_loc = p;
    verify_oop(obj);
    _interior_loc = nullptr;
  }

  /**
   * Verify object without known interior reference.
   * Useful when picking up the object at known offset in heap,
   * but without knowing what objects reference it.
   * @param obj verified object
   */
  void verify_oop_standalone(oop obj) {
    _interior_loc = nullptr;
    verify_oop(obj);
    _interior_loc = nullptr;
  }

  /**
   * Verify oop fields from this object.
   * @param obj host object for verified fields
   */
  void verify_oops_from(oop obj) {
    _loc = obj;
    obj->oop_iterate(this);
    _loc = nullptr;
  }

  virtual void do_oop(oop* p) { do_oop_work(p); }
  virtual void do_oop(narrowOop* p) { do_oop_work(p); }
};

// This closure computes the amounts of used, committed, and garbage memory and the number of regions contained within
// a subset (e.g. the young generation or old generation) of the total heap.
class ShenandoahCalculateRegionStatsClosure : public ShenandoahHeapRegionClosure {
private:
  size_t _used, _committed, _garbage, _regions, _humongous_waste;
public:
  ShenandoahCalculateRegionStatsClosure() : _used(0), _committed(0), _garbage(0), _regions(0), _humongous_waste(0) {};

  void heap_region_do(ShenandoahHeapRegion* r) {
    _used += r->used();
    _garbage += r->garbage();
    _committed += r->is_committed() ? ShenandoahHeapRegion::region_size_bytes() : 0;
    if (r->is_humongous()) {
      _humongous_waste += r->free();
    }
    _regions++;
    log_debug(gc)("ShenandoahCalculateRegionStatsClosure: adding " SIZE_FORMAT " for %s Region " SIZE_FORMAT ", yielding: " SIZE_FORMAT,
            r->used(), (r->is_humongous() ? "humongous" : "regular"), r->index(), _used);
  }

  size_t used() { return _used; }
  size_t committed() { return _committed; }
  size_t garbage() { return _garbage; }
  size_t regions() { return _regions; }
  size_t waste() { return _humongous_waste; }

  // span is the total memory affiliated with these stats (some of which is in use and other is available)
  size_t span() { return _regions * ShenandoahHeapRegion::region_size_bytes(); }
};

class ShenandoahGenerationStatsClosure : public ShenandoahHeapRegionClosure {
 public:
  ShenandoahCalculateRegionStatsClosure old;
  ShenandoahCalculateRegionStatsClosure young;
  ShenandoahCalculateRegionStatsClosure global;

  void heap_region_do(ShenandoahHeapRegion* r) override {
    switch (r->affiliation()) {
      case FREE:
        return;
      case YOUNG_GENERATION:
        young.heap_region_do(r);
        global.heap_region_do(r);
        break;
      case OLD_GENERATION:
        old.heap_region_do(r);
        global.heap_region_do(r);
        break;
      default:
        ShouldNotReachHere();
    }
  }

  static void log_usage(ShenandoahGeneration* generation, ShenandoahCalculateRegionStatsClosure& stats) {
    log_debug(gc)("Safepoint verification: %s verified usage: " SIZE_FORMAT "%s, recorded usage: " SIZE_FORMAT "%s",
                  generation->name(),
                  byte_size_in_proper_unit(generation->used()), proper_unit_for_byte_size(generation->used()),
                  byte_size_in_proper_unit(stats.used()),       proper_unit_for_byte_size(stats.used()));
  }

  static void validate_usage(const bool adjust_for_padding,
                             const char* label, ShenandoahGeneration* generation, ShenandoahCalculateRegionStatsClosure& stats) {
    ShenandoahHeap* heap = ShenandoahHeap::heap();
    size_t generation_used = generation->used();
    size_t generation_used_regions = generation->used_regions();
    if (adjust_for_padding && (generation->is_young() || generation->is_global())) {
      size_t pad = ShenandoahHeap::heap()->get_pad_for_promote_in_place();
      generation_used += pad;
    }

    guarantee(stats.used() == generation_used,
              "%s: generation (%s) used size must be consistent: generation-used: " SIZE_FORMAT "%s, regions-used: " SIZE_FORMAT "%s",
              label, generation->name(),
              byte_size_in_proper_unit(generation_used), proper_unit_for_byte_size(generation_used),
              byte_size_in_proper_unit(stats.used()),    proper_unit_for_byte_size(stats.used()));

    guarantee(stats.regions() == generation_used_regions,
              "%s: generation (%s) used regions (" SIZE_FORMAT ") must equal regions that are in use (" SIZE_FORMAT ")",
              label, generation->name(), generation->used_regions(), stats.regions());

    size_t generation_capacity = generation->max_capacity();
    size_t humongous_regions_promoted = 0;
    guarantee(stats.span() <= generation_capacity,
              "%s: generation (%s) size spanned by regions (" SIZE_FORMAT ") must not exceed current capacity (" SIZE_FORMAT "%s)",
              label, generation->name(), stats.regions(),
              byte_size_in_proper_unit(generation_capacity), proper_unit_for_byte_size(generation_capacity));

    size_t humongous_waste = generation->get_humongous_waste();
    guarantee(stats.waste() == humongous_waste,
              "%s: generation (%s) humongous waste must be consistent: generation: " SIZE_FORMAT "%s, regions: " SIZE_FORMAT "%s",
              label, generation->name(),
              byte_size_in_proper_unit(humongous_waste), proper_unit_for_byte_size(humongous_waste),
              byte_size_in_proper_unit(stats.waste()),   proper_unit_for_byte_size(stats.waste()));
  }
};

class ShenandoahVerifyHeapRegionClosure : public ShenandoahHeapRegionClosure {
private:
  ShenandoahHeap* _heap;
  const char* _phase;
  ShenandoahVerifier::VerifyRegions _regions;
public:
  ShenandoahVerifyHeapRegionClosure(const char* phase, ShenandoahVerifier::VerifyRegions regions) :
    _heap(ShenandoahHeap::heap()),
    _phase(phase),
    _regions(regions) {};

  void print_failure(ShenandoahHeapRegion* r, const char* label) {
    ResourceMark rm;

    ShenandoahMessageBuffer msg("Shenandoah verification failed; %s: %s\n\n", _phase, label);

    stringStream ss;
    r->print_on(&ss);
    msg.append("%s", ss.as_string());

    report_vm_error(__FILE__, __LINE__, msg.buffer());
  }

  void verify(ShenandoahHeapRegion* r, bool test, const char* msg) {
    if (!test) {
      print_failure(r, msg);
    }
  }

  void heap_region_do(ShenandoahHeapRegion* r) {
    switch (_regions) {
      case ShenandoahVerifier::_verify_regions_disable:
        break;
      case ShenandoahVerifier::_verify_regions_notrash:
        verify(r, !r->is_trash(),
               "Should not have trash regions");
        break;
      case ShenandoahVerifier::_verify_regions_nocset:
        verify(r, !r->is_cset(),
               "Should not have cset regions");
        break;
      case ShenandoahVerifier::_verify_regions_notrash_nocset:
        verify(r, !r->is_trash(),
               "Should not have trash regions");
        verify(r, !r->is_cset(),
               "Should not have cset regions");
        break;
      default:
        ShouldNotReachHere();
    }

    verify(r, r->capacity() == ShenandoahHeapRegion::region_size_bytes(),
           "Capacity should match region size");

    verify(r, r->bottom() <= r->top(),
           "Region top should not be less than bottom");

    verify(r, r->bottom() <= _heap->marking_context()->top_at_mark_start(r),
           "Region TAMS should not be less than bottom");

    verify(r, _heap->marking_context()->top_at_mark_start(r) <= r->top(),
           "Complete TAMS should not be larger than top");

    verify(r, r->get_live_data_bytes() <= r->capacity(),
           "Live data cannot be larger than capacity");

    verify(r, r->garbage() <= r->capacity(),
           "Garbage cannot be larger than capacity");

    verify(r, r->used() <= r->capacity(),
           "Used cannot be larger than capacity");

    verify(r, r->get_shared_allocs() <= r->capacity(),
           "Shared alloc count should not be larger than capacity");

    verify(r, r->get_tlab_allocs() <= r->capacity(),
           "TLAB alloc count should not be larger than capacity");

    verify(r, r->get_gclab_allocs() <= r->capacity(),
           "GCLAB alloc count should not be larger than capacity");

    verify(r, r->get_plab_allocs() <= r->capacity(),
           "PLAB alloc count should not be larger than capacity");

    verify(r, r->get_shared_allocs() + r->get_tlab_allocs() + r->get_gclab_allocs() + r->get_plab_allocs() == r->used(),
           "Accurate accounting: shared + TLAB + GCLAB + PLAB = used");

    verify(r, !r->is_empty() || !r->has_live(),
           "Empty regions should not have live data");

    verify(r, r->is_cset() == _heap->collection_set()->is_in(r),
           "Transitional: region flags and collection set agree");
  }
};

class ShenandoahVerifierReachableTask : public WorkerTask {
private:
  const char* _label;
  ShenandoahVerifier::VerifyOptions _options;
  ShenandoahHeap* _heap;
  ShenandoahLivenessData* _ld;
  MarkBitMap* _bitmap;
  volatile size_t _processed;

public:
  ShenandoahVerifierReachableTask(MarkBitMap* bitmap,
                                  ShenandoahLivenessData* ld,
                                  const char* label,
                                  ShenandoahVerifier::VerifyOptions options) :
    WorkerTask("Shenandoah Verifier Reachable Objects"),
    _label(label),
    _options(options),
    _heap(ShenandoahHeap::heap()),
    _ld(ld),
    _bitmap(bitmap),
    _processed(0) {};

  size_t processed() {
    return _processed;
  }

  virtual void work(uint worker_id) {
    ResourceMark rm;
    ShenandoahVerifierStack stack;

    // On level 2, we need to only check the roots once.
    // On level 3, we want to check the roots, and seed the local stack.
    // It is a lesser evil to accept multiple root scans at level 3, because
    // extended parallelism would buy us out.
    if (((ShenandoahVerifyLevel == 2) && (worker_id == 0))
        || (ShenandoahVerifyLevel >= 3)) {
        ShenandoahVerifyOopClosure cl(&stack, _bitmap, _ld,
                                      ShenandoahMessageBuffer("%s, Roots", _label),
                                      _options);
        if (_heap->unload_classes()) {
          ShenandoahRootVerifier::strong_roots_do(&cl);
        } else {
          ShenandoahRootVerifier::roots_do(&cl);
        }
    }

    size_t processed = 0;

    if (ShenandoahVerifyLevel >= 3) {
      ShenandoahVerifyOopClosure cl(&stack, _bitmap, _ld,
                                    ShenandoahMessageBuffer("%s, Reachable", _label),
                                    _options);
      while (!stack.is_empty()) {
        processed++;
        ShenandoahVerifierTask task = stack.pop();
        cl.verify_oops_from(task.obj());
      }
    }

    Atomic::add(&_processed, processed, memory_order_relaxed);
  }
};

class ShenandoahVerifyNoIncompleteSatbBuffers : public ThreadClosure {
public:
  virtual void do_thread(Thread* thread) {
    SATBMarkQueue& queue = ShenandoahThreadLocalData::satb_mark_queue(thread);
    if (!queue.is_empty()) {
      fatal("All SATB buffers should have been flushed during mark");
    }
  }
};

class ShenandoahVerifierMarkedRegionTask : public WorkerTask {
private:
  const char* _label;
  ShenandoahVerifier::VerifyOptions _options;
  ShenandoahHeap *_heap;
  MarkBitMap* _bitmap;
  ShenandoahLivenessData* _ld;
  volatile size_t _claimed;
  volatile size_t _processed;
  ShenandoahGeneration* _generation;

public:
  ShenandoahVerifierMarkedRegionTask(MarkBitMap* bitmap,
                                     ShenandoahLivenessData* ld,
                                     const char* label,
                                     ShenandoahVerifier::VerifyOptions options) :
          WorkerTask("Shenandoah Verifier Marked Objects"),
          _label(label),
          _options(options),
          _heap(ShenandoahHeap::heap()),
          _bitmap(bitmap),
          _ld(ld),
          _claimed(0),
          _processed(0),
          _generation(nullptr) {
    if (_options._verify_marked == ShenandoahVerifier::_verify_marked_complete_satb_empty) {
      Threads::change_thread_claim_token();
    }

    if (_heap->mode()->is_generational()) {
      _generation = _heap->active_generation();
      assert(_generation != nullptr, "Expected active generation in this mode.");
    }
  };

  size_t processed() {
    return Atomic::load(&_processed);
  }

  virtual void work(uint worker_id) {
    if (_options._verify_marked == ShenandoahVerifier::_verify_marked_complete_satb_empty) {
      ShenandoahVerifyNoIncompleteSatbBuffers verify_satb;
      Threads::possibly_parallel_threads_do(true, &verify_satb);
    }

    ShenandoahVerifierStack stack;
    ShenandoahVerifyOopClosure cl(&stack, _bitmap, _ld,
                                  ShenandoahMessageBuffer("%s, Marked", _label),
                                  _options);

    while (true) {
      size_t v = Atomic::fetch_then_add(&_claimed, 1u, memory_order_relaxed);
      if (v < _heap->num_regions()) {
        ShenandoahHeapRegion* r = _heap->get_region(v);
        if (!in_generation(r)) {
          continue;
        }

        if (!r->is_humongous() && !r->is_trash()) {
          work_regular(r, stack, cl);
        } else if (r->is_humongous_start()) {
          work_humongous(r, stack, cl);
        }
      } else {
        break;
      }
    }
  }

  bool in_generation(ShenandoahHeapRegion* r) {
    return _generation == nullptr || _generation->contains(r);
  }

  virtual void work_humongous(ShenandoahHeapRegion *r, ShenandoahVerifierStack& stack, ShenandoahVerifyOopClosure& cl) {
    size_t processed = 0;
    HeapWord* obj = r->bottom();
    if (_heap->complete_marking_context()->is_marked(cast_to_oop(obj))) {
      verify_and_follow(obj, stack, cl, &processed);
    }
    Atomic::add(&_processed, processed, memory_order_relaxed);
  }

  virtual void work_regular(ShenandoahHeapRegion *r, ShenandoahVerifierStack &stack, ShenandoahVerifyOopClosure &cl) {
    size_t processed = 0;
    ShenandoahMarkingContext* ctx = _heap->complete_marking_context();
    HeapWord* tams = ctx->top_at_mark_start(r);

    // Bitmaps, before TAMS
    if (tams > r->bottom()) {
      HeapWord* start = r->bottom();
      HeapWord* addr = ctx->get_next_marked_addr(start, tams);

      while (addr < tams) {
        verify_and_follow(addr, stack, cl, &processed);
        addr += 1;
        if (addr < tams) {
          addr = ctx->get_next_marked_addr(addr, tams);
        }
      }
    }

    // Size-based, after TAMS
    {
      HeapWord* limit = r->top();
      HeapWord* addr = tams;

      while (addr < limit) {
        verify_and_follow(addr, stack, cl, &processed);
        addr += cast_to_oop(addr)->size();
      }
    }

    Atomic::add(&_processed, processed, memory_order_relaxed);
  }

  void verify_and_follow(HeapWord *addr, ShenandoahVerifierStack &stack, ShenandoahVerifyOopClosure &cl, size_t *processed) {
    if (!_bitmap->par_mark(addr)) return;

    // Verify the object itself:
    oop obj = cast_to_oop(addr);
    cl.verify_oop_standalone(obj);

    // Verify everything reachable from that object too, hopefully realizing
    // everything was already marked, and never touching further:
    if (!is_instance_ref_klass(obj->klass())) {
      cl.verify_oops_from(obj);
      (*processed)++;
    }
    while (!stack.is_empty()) {
      ShenandoahVerifierTask task = stack.pop();
      cl.verify_oops_from(task.obj());
      (*processed)++;
    }
  }
};

class VerifyThreadGCState : public ThreadClosure {
private:
  const char* const _label;
         char const _expected;

public:
  VerifyThreadGCState(const char* label, char expected) : _label(label), _expected(expected) {}
  void do_thread(Thread* t) {
    char actual = ShenandoahThreadLocalData::gc_state(t);
    if (!verify_gc_state(actual, _expected)) {
      fatal("%s: Thread %s: expected gc-state %d, actual %d", _label, t->name(), _expected, actual);
    }
  }

  static bool verify_gc_state(char actual, char expected) {
    // Old generation marking is allowed in all states.
    if (ShenandoahHeap::heap()->mode()->is_generational()) {
      return ((actual & ~(ShenandoahHeap::OLD_MARKING | ShenandoahHeap::MARKING)) == expected);
    } else {
      assert((actual & ShenandoahHeap::OLD_MARKING) == 0, "Should not mark old in non-generational mode");
      return (actual == expected);
    }
  }
};

void ShenandoahVerifier::verify_at_safepoint(const char* label,
                                             VerifyRememberedSet remembered,
                                             VerifyForwarded forwarded, VerifyMarked marked,
                                             VerifyCollectionSet cset,
                                             VerifyLiveness liveness, VerifyRegions regions,
                                             VerifySize sizeness,
                                             VerifyGCState gcstate) {
  guarantee(ShenandoahSafepoint::is_at_shenandoah_safepoint(), "only when nothing else happens");
  guarantee(ShenandoahVerify, "only when enabled, and bitmap is initialized in ShenandoahHeap::initialize");

  // Avoid side-effect of changing workers' active thread count, but bypass concurrent/parallel protocol check
  ShenandoahPushWorkerScope verify_worker_scope(_heap->workers(), _heap->max_workers(), false /*bypass check*/);

  log_info(gc,start)("Verify %s, Level " INTX_FORMAT, label, ShenandoahVerifyLevel);

  // GC state checks
  {
    char expected = -1;
    bool enabled;
    switch (gcstate) {
      case _verify_gcstate_disable:
        enabled = false;
        break;
      case _verify_gcstate_forwarded:
        enabled = true;
        expected = ShenandoahHeap::HAS_FORWARDED;
        break;
      case _verify_gcstate_evacuation:
        enabled = true;
        expected = ShenandoahHeap::EVACUATION;
        if (!_heap->is_stw_gc_in_progress()) {
          // Only concurrent GC sets this.
          expected |= ShenandoahHeap::WEAK_ROOTS;
        }
        break;
      case _verify_gcstate_updating:
        enabled = true;
        expected = ShenandoahHeap::HAS_FORWARDED | ShenandoahHeap::UPDATEREFS;
        break;
      case _verify_gcstate_stable:
        enabled = true;
        expected = ShenandoahHeap::STABLE;
        break;
      case _verify_gcstate_stable_weakroots:
        enabled = true;
        expected = ShenandoahHeap::STABLE;
        if (!_heap->is_stw_gc_in_progress()) {
          // Only concurrent GC sets this.
          expected |= ShenandoahHeap::WEAK_ROOTS;
        }
        break;
      default:
        enabled = false;
        assert(false, "Unhandled gc-state verification");
    }

    if (enabled) {
      char actual = _heap->gc_state();

      bool is_marking = (actual & ShenandoahHeap::MARKING)? 1: 0;
      bool is_marking_young_or_old = (actual & (ShenandoahHeap::YOUNG_MARKING | ShenandoahHeap::OLD_MARKING))? 1: 0;
      assert(is_marking == is_marking_young_or_old, "MARKING iff (YOUNG_MARKING or OLD_MARKING), gc_state is: %x", actual);

      // Old generation marking is allowed in all states.
      if (!VerifyThreadGCState::verify_gc_state(actual, expected)) {
        fatal("%s: Global gc-state: expected %d, actual %d", label, expected, actual);
      }

      VerifyThreadGCState vtgcs(label, expected);
      Threads::java_threads_do(&vtgcs);
    }
  }

  // Deactivate barriers temporarily: Verifier wants plain heap accesses
  ShenandoahGCStateResetter resetter;

  // Heap size checks
  {
    ShenandoahHeapLocker lock(_heap->lock());

    ShenandoahCalculateRegionStatsClosure cl;
    _heap->heap_region_iterate(&cl);
    size_t heap_used;
    if (_heap->mode()->is_generational() && (sizeness == _verify_size_adjusted_for_padding)) {
      // Prior to evacuation, regular regions that are to be evacuated in place are padded to prevent further allocations
      heap_used = _heap->used() + _heap->get_pad_for_promote_in_place();
    } else if (sizeness != _verify_size_disable) {
      heap_used = _heap->used();
    }
    if (sizeness != _verify_size_disable) {
      guarantee(cl.used() == heap_used,
                "%s: heap used size must be consistent: heap-used = " SIZE_FORMAT "%s, regions-used = " SIZE_FORMAT "%s",
                label,
                byte_size_in_proper_unit(heap_used), proper_unit_for_byte_size(heap_used),
                byte_size_in_proper_unit(cl.used()), proper_unit_for_byte_size(cl.used()));
    }
    size_t heap_committed = _heap->committed();
    guarantee(cl.committed() == heap_committed,
              "%s: heap committed size must be consistent: heap-committed = " SIZE_FORMAT "%s, regions-committed = " SIZE_FORMAT "%s",
              label,
              byte_size_in_proper_unit(heap_committed), proper_unit_for_byte_size(heap_committed),
              byte_size_in_proper_unit(cl.committed()), proper_unit_for_byte_size(cl.committed()));
  }

  log_debug(gc)("Safepoint verification finished heap usage verification");

  ShenandoahGeneration* generation;
  if (_heap->mode()->is_generational()) {
    generation = _heap->active_generation();
    guarantee(generation != nullptr, "Need to know which generation to verify.");
  } else {
    generation = nullptr;
  }

  if (generation != nullptr) {
    ShenandoahHeapLocker lock(_heap->lock());

    switch (remembered) {
      case _verify_remembered_disable:
        break;
      case _verify_remembered_before_marking:
        log_debug(gc)("Safepoint verification of remembered set at mark");
        verify_rem_set_before_mark();
        break;
      case _verify_remembered_before_updating_references:
        log_debug(gc)("Safepoint verification of remembered set at update ref");
        verify_rem_set_before_update_ref();
        break;
      case _verify_remembered_after_full_gc:
        log_debug(gc)("Safepoint verification of remembered set after full gc");
        verify_rem_set_after_full_gc();
        break;
      default:
        fatal("Unhandled remembered set verification mode");
    }

    ShenandoahGenerationStatsClosure cl;
    _heap->heap_region_iterate(&cl);

    if (LogTarget(Debug, gc)::is_enabled()) {
      ShenandoahGenerationStatsClosure::log_usage(_heap->old_generation(),    cl.old);
      ShenandoahGenerationStatsClosure::log_usage(_heap->young_generation(),  cl.young);
      ShenandoahGenerationStatsClosure::log_usage(_heap->global_generation(), cl.global);
    }
    if (sizeness == _verify_size_adjusted_for_padding) {
      ShenandoahGenerationStatsClosure::validate_usage(false, label, _heap->old_generation(), cl.old);
      ShenandoahGenerationStatsClosure::validate_usage(true, label, _heap->young_generation(), cl.young);
      ShenandoahGenerationStatsClosure::validate_usage(true, label, _heap->global_generation(), cl.global);
    } else if (sizeness == _verify_size_exact) {
      ShenandoahGenerationStatsClosure::validate_usage(false, label, _heap->old_generation(), cl.old);
      ShenandoahGenerationStatsClosure::validate_usage(false, label, _heap->young_generation(), cl.young);
      ShenandoahGenerationStatsClosure::validate_usage(false, label, _heap->global_generation(), cl.global);
    }
    // else: sizeness must equal _verify_size_disable
  }

  log_debug(gc)("Safepoint verification finished remembered set verification");

  // Internal heap region checks
  if (ShenandoahVerifyLevel >= 1) {
    ShenandoahVerifyHeapRegionClosure cl(label, regions);
    if (generation != nullptr) {
      generation->heap_region_iterate(&cl);
    } else {
      _heap->heap_region_iterate(&cl);
    }
  }

  log_debug(gc)("Safepoint verification finished heap region closure verification");

  OrderAccess::fence();

  if (UseTLAB) {
    _heap->labs_make_parsable();
  }

  // Allocate temporary bitmap for storing marking wavefront:
  _verification_bit_map->clear();

  // Allocate temporary array for storing liveness data
  ShenandoahLivenessData* ld = NEW_C_HEAP_ARRAY(ShenandoahLivenessData, _heap->num_regions(), mtGC);
  Copy::fill_to_bytes((void*)ld, _heap->num_regions()*sizeof(ShenandoahLivenessData), 0);

  const VerifyOptions& options = ShenandoahVerifier::VerifyOptions(forwarded, marked, cset, liveness, regions, gcstate);

  // Steps 1-2. Scan root set to get initial reachable set. Finish walking the reachable heap.
  // This verifies what application can see, since it only cares about reachable objects.
  size_t count_reachable = 0;
  if (ShenandoahVerifyLevel >= 2) {
    ShenandoahVerifierReachableTask task(_verification_bit_map, ld, label, options);
    _heap->workers()->run_task(&task);
    count_reachable = task.processed();
  }

  log_debug(gc)("Safepoint verification finished getting initial reachable set");

  // Step 3. Walk marked objects. Marked objects might be unreachable. This verifies what collector,
  // not the application, can see during the region scans. There is no reason to process the objects
  // that were already verified, e.g. those marked in verification bitmap. There is interaction with TAMS:
  // before TAMS, we verify the bitmaps, if available; after TAMS, we walk until the top(). It mimics
  // what marked_object_iterate is doing, without calling into that optimized (and possibly incorrect)
  // version

  size_t count_marked = 0;
  if (ShenandoahVerifyLevel >= 4 &&
        (marked == _verify_marked_complete ||
         marked == _verify_marked_complete_except_references ||
         marked == _verify_marked_complete_satb_empty)) {
    guarantee(_heap->marking_context()->is_complete(), "Marking context should be complete");
    ShenandoahVerifierMarkedRegionTask task(_verification_bit_map, ld, label, options);
    _heap->workers()->run_task(&task);
    count_marked = task.processed();
  } else {
    guarantee(ShenandoahVerifyLevel < 4 || marked == _verify_marked_incomplete || marked == _verify_marked_disable, "Should be");
  }

  log_debug(gc)("Safepoint verification finished walking marked objects");

  // Step 4. Verify accumulated liveness data, if needed. Only reliable if verification level includes
  // marked objects.

  if (ShenandoahVerifyLevel >= 4 && marked == _verify_marked_complete && liveness == _verify_liveness_complete) {
    for (size_t i = 0; i < _heap->num_regions(); i++) {
      ShenandoahHeapRegion* r = _heap->get_region(i);
      if (generation != nullptr && !generation->contains(r)) {
        continue;
      }

      juint verf_live = 0;
      if (r->is_humongous()) {
        // For humongous objects, test if start region is marked live, and if so,
        // all humongous regions in that chain have live data equal to their "used".
        juint start_live = Atomic::load(&ld[r->humongous_start_region()->index()]);
        if (start_live > 0) {
          verf_live = (juint)(r->used() / HeapWordSize);
        }
      } else {
        verf_live = Atomic::load(&ld[r->index()]);
      }

      size_t reg_live = r->get_live_data_words();
      if (reg_live != verf_live) {
        stringStream ss;
        r->print_on(&ss);
        fatal("%s: Live data should match: region-live = " SIZE_FORMAT ", verifier-live = " UINT32_FORMAT "\n%s",
              label, reg_live, verf_live, ss.freeze());
      }
    }
  }

  log_debug(gc)("Safepoint verification finished accumulation of liveness data");


  log_info(gc)("Verify %s, Level " INTX_FORMAT " (" SIZE_FORMAT " reachable, " SIZE_FORMAT " marked)",
               label, ShenandoahVerifyLevel, count_reachable, count_marked);

  FREE_C_HEAP_ARRAY(ShenandoahLivenessData, ld);
}

void ShenandoahVerifier::verify_generic(VerifyOption vo) {
  verify_at_safepoint(
          "Generic Verification",
          _verify_remembered_disable,  // do not verify remembered set
          _verify_forwarded_allow,     // conservatively allow forwarded
          _verify_marked_disable,      // do not verify marked: lots ot time wasted checking dead allocations
          _verify_cset_disable,        // cset may be inconsistent
          _verify_liveness_disable,    // no reliable liveness data
          _verify_regions_disable,     // no reliable region data
          _verify_size_exact,          // expect generation and heap sizes to match exactly
          _verify_gcstate_disable      // no data about gcstate
  );
}

void ShenandoahVerifier::verify_before_concmark() {
    verify_at_safepoint(
          "Before Mark",
          _verify_remembered_before_marking,
                                       // verify read-only remembered set from bottom() to top()
          _verify_forwarded_none,      // UR should have fixed up
          _verify_marked_disable,      // do not verify marked: lots ot time wasted checking dead allocations
          _verify_cset_none,           // UR should have fixed this
          _verify_liveness_disable,    // no reliable liveness data
          _verify_regions_notrash,     // no trash regions
          _verify_size_exact,          // expect generation and heap sizes to match exactly
          _verify_gcstate_stable       // there are no forwarded objects
  );
}

void ShenandoahVerifier::verify_after_concmark() {
  verify_at_safepoint(
          "After Mark",
          _verify_remembered_disable,  // do not verify remembered set
          _verify_forwarded_none,      // no forwarded references
          _verify_marked_complete_satb_empty,
                                       // bitmaps as precise as we can get, except dangling j.l.r.Refs
          _verify_cset_none,           // no references to cset anymore
          _verify_liveness_complete,   // liveness data must be complete here
          _verify_regions_disable,     // trash regions not yet recycled
          _verify_size_exact,          // expect generation and heap sizes to match exactly
          _verify_gcstate_stable_weakroots  // heap is still stable, weakroots are in progress
  );
}

void ShenandoahVerifier::verify_before_evacuation() {
  verify_at_safepoint(
          "Before Evacuation",
          _verify_remembered_disable,                // do not verify remembered set
          _verify_forwarded_none,                    // no forwarded references
          _verify_marked_complete_except_references, // walk over marked objects too
          _verify_cset_disable,                      // non-forwarded references to cset expected
          _verify_liveness_complete,                 // liveness data must be complete here
          _verify_regions_disable,                   // trash regions not yet recycled
          _verify_size_adjusted_for_padding,         // expect generation and heap sizes to match after adjustments
                                                     //  for promote in place padding
          _verify_gcstate_stable_weakroots           // heap is still stable, weakroots are in progress
  );
}

void ShenandoahVerifier::verify_during_evacuation() {
  verify_at_safepoint(
          "During Evacuation",
          _verify_remembered_disable, // do not verify remembered set
          _verify_forwarded_allow,    // some forwarded references are allowed
          _verify_marked_disable,     // walk only roots
          _verify_cset_disable,       // some cset references are not forwarded yet
          _verify_liveness_disable,   // liveness data might be already stale after pre-evacs
          _verify_regions_disable,    // trash regions not yet recycled
          _verify_size_disable,       // we don't know how much of promote-in-place work has been completed
          _verify_gcstate_evacuation  // evacuation is in progress
  );
}

void ShenandoahVerifier::verify_after_evacuation() {
  verify_at_safepoint(
          "After Evacuation",
          _verify_remembered_disable,  // do not verify remembered set
          _verify_forwarded_allow,     // objects are still forwarded
          _verify_marked_complete,     // bitmaps might be stale, but alloc-after-mark should be well
          _verify_cset_forwarded,      // all cset refs are fully forwarded
          _verify_liveness_disable,    // no reliable liveness data anymore
          _verify_regions_notrash,     // trash regions have been recycled already
          _verify_size_exact,          // expect generation and heap sizes to match exactly
          _verify_gcstate_forwarded    // evacuation produced some forwarded objects
  );
}

void ShenandoahVerifier::verify_before_updaterefs() {
  verify_at_safepoint(
          "Before Updating References",
          _verify_remembered_before_updating_references,  // verify read-write remembered set
          _verify_forwarded_allow,     // forwarded references allowed
          _verify_marked_complete,     // bitmaps might be stale, but alloc-after-mark should be well
          _verify_cset_forwarded,      // all cset refs are fully forwarded
          _verify_liveness_disable,    // no reliable liveness data anymore
          _verify_regions_notrash,     // trash regions have been recycled already
          _verify_size_exact,          // expect generation and heap sizes to match exactly
          _verify_gcstate_updating     // evacuation should have produced some forwarded objects
  );
}

// We have not yet cleanup (reclaimed) the collection set
void ShenandoahVerifier::verify_after_updaterefs() {
  verify_at_safepoint(
          "After Updating References",
          _verify_remembered_disable,  // do not verify remembered set
          _verify_forwarded_none,      // no forwarded references
          _verify_marked_complete,     // bitmaps might be stale, but alloc-after-mark should be well
          _verify_cset_none,           // no cset references, all updated
          _verify_liveness_disable,    // no reliable liveness data anymore
          _verify_regions_nocset,      // no cset regions, trash regions have appeared
          _verify_size_exact,          // expect generation and heap sizes to match exactly
          _verify_gcstate_stable       // update refs had cleaned up forwarded objects
  );
}

void ShenandoahVerifier::verify_after_degenerated() {
  verify_at_safepoint(
          "After Degenerated GC",
          _verify_remembered_disable,  // do not verify remembered set
          _verify_forwarded_none,      // all objects are non-forwarded
          _verify_marked_complete,     // all objects are marked in complete bitmap
          _verify_cset_none,           // no cset references
          _verify_liveness_disable,    // no reliable liveness data anymore
          _verify_regions_notrash_nocset, // no trash, no cset
          _verify_size_exact,          // expect generation and heap sizes to match exactly
          _verify_gcstate_stable       // degenerated refs had cleaned up forwarded objects
  );
}

void ShenandoahVerifier::verify_before_fullgc() {
  verify_at_safepoint(
          "Before Full GC",
          _verify_remembered_disable,  // do not verify remembered set
          _verify_forwarded_allow,     // can have forwarded objects
          _verify_marked_disable,      // do not verify marked: lots ot time wasted checking dead allocations
          _verify_cset_disable,        // cset might be foobared
          _verify_liveness_disable,    // no reliable liveness data anymore
          _verify_regions_disable,     // no reliable region data here
          _verify_size_disable,        // if we degenerate during evacuation, usage not valid: padding and deferred accounting
          _verify_gcstate_disable      // no reliable gcstate data
  );
}

void ShenandoahVerifier::verify_after_fullgc() {
  verify_at_safepoint(
          "After Full GC",
          _verify_remembered_after_full_gc,  // verify read-write remembered set
          _verify_forwarded_none,      // all objects are non-forwarded
          _verify_marked_complete,     // all objects are marked in complete bitmap
          _verify_cset_none,           // no cset references
          _verify_liveness_disable,    // no reliable liveness data anymore
          _verify_regions_notrash_nocset, // no trash, no cset
          _verify_size_exact,           // expect generation and heap sizes to match exactly
          _verify_gcstate_stable        // full gc cleaned up everything
  );
}

// TODO: Why this closure does not visit metadata?
class ShenandoahVerifyNoForwared : public BasicOopIterateClosure {
private:
  template <class T>
  void do_oop_work(T* p) {
    T o = RawAccess<>::oop_load(p);
    if (!CompressedOops::is_null(o)) {
      oop obj = CompressedOops::decode_not_null(o);
      oop fwd = ShenandoahForwarding::get_forwardee_raw_unchecked(obj);
      if (obj != fwd) {
        ShenandoahAsserts::print_failure(ShenandoahAsserts::_safe_all, obj, p, nullptr,
                                         "Verify Roots", "Should not be forwarded", __FILE__, __LINE__);
      }
    }
  }

public:
  void do_oop(narrowOop* p) { do_oop_work(p); }
  void do_oop(oop* p)       { do_oop_work(p); }
};

// TODO: Why this closure does not visit metadata?
class ShenandoahVerifyInToSpaceClosure : public BasicOopIterateClosure {
private:
  template <class T>
  void do_oop_work(T* p) {
    T o = RawAccess<>::oop_load(p);
    if (!CompressedOops::is_null(o)) {
      oop obj = CompressedOops::decode_not_null(o);
      ShenandoahHeap* heap = ShenandoahHeap::heap();

      if (!heap->marking_context()->is_marked_or_old(obj)) {
        ShenandoahAsserts::print_failure(ShenandoahAsserts::_safe_all, obj, p, nullptr,
                "Verify Roots In To-Space", "Should be marked", __FILE__, __LINE__);
      }

      if (heap->in_collection_set(obj)) {
        ShenandoahAsserts::print_failure(ShenandoahAsserts::_safe_all, obj, p, nullptr,
                "Verify Roots In To-Space", "Should not be in collection set", __FILE__, __LINE__);
      }

      oop fwd = ShenandoahForwarding::get_forwardee_raw_unchecked(obj);
      if (obj != fwd) {
        ShenandoahAsserts::print_failure(ShenandoahAsserts::_safe_all, obj, p, nullptr,
                "Verify Roots In To-Space", "Should not be forwarded", __FILE__, __LINE__);
      }
    }
  }

public:
  void do_oop(narrowOop* p) { do_oop_work(p); }
  void do_oop(oop* p)       { do_oop_work(p); }
};

void ShenandoahVerifier::verify_roots_in_to_space() {
  ShenandoahVerifyInToSpaceClosure cl;
  ShenandoahRootVerifier::roots_do(&cl);
}

void ShenandoahVerifier::verify_roots_no_forwarded() {
  ShenandoahVerifyNoForwared cl;
  ShenandoahRootVerifier::roots_do(&cl);
}

class ShenandoahVerifyRemSetClosure : public BasicOopIterateClosure {
protected:
  bool               const _init_mark;
  ShenandoahHeap*    const _heap;
  RememberedScanner* const _scanner;

public:
  // Argument distinguishes between initial mark or start of update refs verification.
  ShenandoahVerifyRemSetClosure(bool init_mark) :
            _init_mark(init_mark),
            _heap(ShenandoahHeap::heap()),
            _scanner(_heap->card_scan()) {}

  template<class T>
  inline void work(T* p) {
    T o = RawAccess<>::oop_load(p);
    if (!CompressedOops::is_null(o)) {
      oop obj = CompressedOops::decode_not_null(o);
      if (_heap->is_in_young(obj)) {
        size_t card_index = _scanner->card_index_for_addr((HeapWord*) p);
        if (_init_mark && !_scanner->is_card_dirty(card_index)) {
          ShenandoahAsserts::print_failure(ShenandoahAsserts::_safe_all, obj, p, nullptr,
                                           "Verify init-mark remembered set violation", "clean card should be dirty", __FILE__, __LINE__);
        } else if (!_init_mark && !_scanner->is_write_card_dirty(card_index)) {
          ShenandoahAsserts::print_failure(ShenandoahAsserts::_safe_all, obj, p, nullptr,
                                           "Verify init-update-refs remembered set violation", "clean card should be dirty", __FILE__, __LINE__);
        }
      }
    }
  }

  virtual void do_oop(narrowOop* p) { work(p); }
  virtual void do_oop(oop* p)       { work(p); }
};

void ShenandoahVerifier::help_verify_region_rem_set(ShenandoahHeapRegion* r, ShenandoahMarkingContext* ctx, HeapWord* from,
                                                    HeapWord* top, HeapWord* registration_watermark, const char* message) {
  RememberedScanner* scanner = _heap->card_scan();
  ShenandoahVerifyRemSetClosure check_interesting_pointers(false);

  HeapWord* obj_addr = from;
  if (r->is_humongous_start()) {
    oop obj = cast_to_oop(obj_addr);
    if ((ctx == nullptr) || ctx->is_marked(obj)) {
      size_t card_index = scanner->card_index_for_addr(obj_addr);
      // For humongous objects, the typical object is an array, so the following checks may be overkill
      // For regular objects (not object arrays), if the card holding the start of the object is dirty,
      // we do not need to verify that cards spanning interesting pointers within this object are dirty.
      if (!scanner->is_write_card_dirty(card_index) || obj->is_objArray()) {
        obj->oop_iterate(&check_interesting_pointers);
      }
      // else, object's start is marked dirty and obj is not an objArray, so any interesting pointers are covered
    }
    // else, this humongous object is not live so no need to verify its internal pointers

    if ((obj_addr < registration_watermark) && !scanner->verify_registration(obj_addr, ctx)) {
      ShenandoahAsserts::print_failure(ShenandoahAsserts::_safe_all, obj, obj_addr, nullptr, message,
                                       "object not properly registered", __FILE__, __LINE__);
    }
  } else if (!r->is_humongous()) {
    while (obj_addr < top) {
      oop obj = cast_to_oop(obj_addr);
      // ctx->is_marked() returns true if mark bit set or if obj above TAMS.
      if ((ctx == nullptr) || ctx->is_marked(obj)) {
        size_t card_index = scanner->card_index_for_addr(obj_addr);
        // For regular objects (not object arrays), if the card holding the start of the object is dirty,
        // we do not need to verify that cards spanning interesting pointers within this object are dirty.
        if (!scanner->is_write_card_dirty(card_index) || obj->is_objArray()) {
          obj->oop_iterate(&check_interesting_pointers);
        }
        // else, object's start is marked dirty and obj is not an objArray, so any interesting pointers are covered

        if ((obj_addr < registration_watermark) && !scanner->verify_registration(obj_addr, ctx)) {
          ShenandoahAsserts::print_failure(ShenandoahAsserts::_safe_all, obj, obj_addr, nullptr, message,
                                           "object not properly registered", __FILE__, __LINE__);
        }
        obj_addr += obj->size();
      } else {
        // This object is not live so we don't verify dirty cards contained therein
        HeapWord* tams = ctx->top_at_mark_start(r);
        obj_addr = ctx->get_next_marked_addr(obj_addr, tams);
      }
    }
  }
}

// Assure that the remember set has a dirty card everywhere there is an interesting pointer.
// This examines the read_card_table between bottom() and top() since all PLABS are retired
// before the safepoint for init_mark.  Actually, we retire them before update-references and don't
// restore them until the start of evacuation.
void ShenandoahVerifier::verify_rem_set_before_mark() {
  shenandoah_assert_safepoint();
  assert(_heap->mode()->is_generational(), "Only verify remembered set for generational operational modes");

  ShenandoahRegionIterator iterator;
  RememberedScanner* scanner = _heap->card_scan();
  ShenandoahVerifyRemSetClosure check_interesting_pointers(true);
  ShenandoahMarkingContext* ctx;

  log_debug(gc)("Verifying remembered set at %s mark", _heap->doing_mixed_evacuations()? "mixed": "young");

  if (_heap->is_old_bitmap_stable() || _heap->active_generation()->is_global()) {
    ctx = _heap->complete_marking_context();
  } else {
    ctx = nullptr;
  }

  while (iterator.has_next()) {
    ShenandoahHeapRegion* r = iterator.next();
    if (r == nullptr) {
      // TODO: Can this really happen?
      break;
    }

    HeapWord* tams = (ctx != nullptr) ? ctx->top_at_mark_start(r) : nullptr;

    // TODO: Is this replaceable with call to help_verify_region_rem_set?

    if (r->is_old() && r->is_active()) {
      HeapWord* obj_addr = r->bottom();
      if (r->is_humongous_start()) {
        oop obj = cast_to_oop(obj_addr);
        if ((ctx == nullptr) || ctx->is_marked(obj)) {
          // For humongous objects, the typical object is an array, so the following checks may be overkill
          // For regular objects (not object arrays), if the card holding the start of the object is dirty,
          // we do not need to verify that cards spanning interesting pointers within this object are dirty.
          if (!scanner->is_card_dirty(obj_addr) || obj->is_objArray()) {
            obj->oop_iterate(&check_interesting_pointers);
          }
          // else, object's start is marked dirty and obj is not an objArray, so any interesting pointers are covered
        }
        // else, this humongous object is not marked so no need to verify its internal pointers
        if (!scanner->verify_registration(obj_addr, ctx)) {
          ShenandoahAsserts::print_failure(ShenandoahAsserts::_safe_all, obj, nullptr, nullptr,
                                           "Verify init-mark remembered set violation", "object not properly registered", __FILE__, __LINE__);
        }
      } else if (!r->is_humongous()) {
        HeapWord* top = r->top();
        while (obj_addr < top) {
          oop obj = cast_to_oop(obj_addr);
          // ctx->is_marked() returns true if mark bit set (TAMS not relevant during init mark)
          if ((ctx == nullptr) || ctx->is_marked(obj)) {
            // For regular objects (not object arrays), if the card holding the start of the object is dirty,
            // we do not need to verify that cards spanning interesting pointers within this object are dirty.
            if (!scanner->is_card_dirty(obj_addr) || obj->is_objArray()) {
              obj->oop_iterate(&check_interesting_pointers);
            }
            // else, object's start is marked dirty and obj is not an objArray, so any interesting pointers are covered
            if (!scanner->verify_registration(obj_addr, ctx)) {
              ShenandoahAsserts::print_failure(ShenandoahAsserts::_safe_all, obj, nullptr, nullptr,
                                               "Verify init-mark remembered set violation", "object not properly registered", __FILE__, __LINE__);
            }
            obj_addr += obj->size();
          } else {
            // This object is not live so we don't verify dirty cards contained therein
            assert(tams != nullptr, "If object is not live, ctx and tams should be non-null");
            obj_addr = ctx->get_next_marked_addr(obj_addr, tams);
          }
        }
      } // else, we ignore humongous continuation region
    } // else, this is not an OLD region so we ignore it
  } // all regions have been processed
}

void ShenandoahVerifier::verify_rem_set_after_full_gc() {
  shenandoah_assert_safepoint();
  assert(_heap->mode()->is_generational(), "Only verify remembered set for generational operational modes");

  ShenandoahRegionIterator iterator;

  while (iterator.has_next()) {
    ShenandoahHeapRegion* r = iterator.next();
    if (r == nullptr) {
      // TODO: Can this really happen?
      break;
    }
    if (r->is_old() && !r->is_cset()) {
      help_verify_region_rem_set(r, nullptr, r->bottom(), r->top(), r->top(), "Remembered set violation at end of Full GC");
    }
  }
}

// Assure that the remember set has a dirty card everywhere there is an interesting pointer.  Even though
// the update-references scan of remembered set only examines cards up to update_watermark, the remembered
// set should be valid through top.  This examines the write_card_table between bottom() and top() because
// all PLABS are retired immediately before the start of update refs.
void ShenandoahVerifier::verify_rem_set_before_update_ref() {
  shenandoah_assert_safepoint();
  assert(_heap->mode()->is_generational(), "Only verify remembered set for generational operational modes");

  ShenandoahRegionIterator iterator;
  ShenandoahMarkingContext* ctx;

  if (_heap->is_old_bitmap_stable() || _heap->active_generation()->is_global()) {
    ctx = _heap->complete_marking_context();
  } else {
    ctx = nullptr;
  }

  while (iterator.has_next()) {
    ShenandoahHeapRegion* r = iterator.next();
    if (r == nullptr) {
      // TODO: Can this really happen?
      break;
    }
    if (r->is_old() && !r->is_cset()) {
      help_verify_region_rem_set(r, ctx, r->bottom(), r->top(), r->get_update_watermark(),
                                 "Remembered set violation at init-update-references");
    }
  }
}
