/*
 * Copyright (c) 2001, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "classfile/classLoaderDataGraph.hpp"
#include "gc/serial/cardTableRS.hpp"
#include "gc/serial/serialGcRefProcProxyTask.hpp"
#include "gc/serial/serialHeap.inline.hpp"
#include "gc/serial/serialStringDedup.inline.hpp"
#include "gc/serial/tenuredGeneration.hpp"
#include "gc/shared/adaptiveSizePolicy.hpp"
#include "gc/shared/ageTable.inline.hpp"
#include "gc/shared/collectorCounters.hpp"
#include "gc/shared/continuationGCSupport.inline.hpp"
#include "gc/shared/gcArguments.hpp"
#include "gc/shared/gcHeapSummary.hpp"
#include "gc/shared/gcLocker.hpp"
#include "gc/shared/gcPolicyCounters.hpp"
#include "gc/shared/gcTimer.hpp"
#include "gc/shared/gcTrace.hpp"
#include "gc/shared/gcTraceTime.inline.hpp"
#include "gc/shared/oopStorageSet.inline.hpp"
#include "gc/shared/referencePolicy.hpp"
#include "gc/shared/referenceProcessorPhaseTimes.hpp"
#include "gc/shared/scavengableNMethods.hpp"
#include "gc/shared/space.hpp"
#include "gc/shared/spaceDecorator.hpp"
#include "gc/shared/weakProcessor.hpp"
#include "logging/log.hpp"
#include "memory/iterator.inline.hpp"
#include "memory/reservedSpace.hpp"
#include "memory/resourceArea.hpp"
#include "oops/instanceRefKlass.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/java.hpp"
#include "runtime/javaThread.hpp"
#include "runtime/prefetch.inline.hpp"
#include "runtime/threads.hpp"
#include "utilities/align.hpp"
#include "utilities/copy.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/stack.inline.hpp"

class PromoteFailureClosure : public InHeapScanClosure {
  template <typename T>
  void do_oop_work(T* p) {
    assert(is_in_young_gen(p), "promote-fail objs must be in young-gen");
    assert(!SerialHeap::heap()->young_gen()->to()->is_in_reserved(p), "must not be in to-space");

    try_scavenge(p, [] (auto) {});
  }
public:
  PromoteFailureClosure(DefNewGeneration* g) : InHeapScanClosure(g) {}

  void do_oop(oop* p)       { do_oop_work(p); }
  void do_oop(narrowOop* p) { do_oop_work(p); }
};

class RootScanClosure : public OffHeapScanClosure {
  template <typename T>
  void do_oop_work(T* p) {
    assert(!SerialHeap::heap()->is_in_reserved(p), "outside the heap");

    try_scavenge(p,  [] (auto) {});
  }
public:
  RootScanClosure(DefNewGeneration* g) : OffHeapScanClosure(g) {}

  void do_oop(oop* p)       { do_oop_work(p); }
  void do_oop(narrowOop* p) { do_oop_work(p); }
};

class CLDScanClosure: public CLDClosure {

  class CLDOopClosure : public OffHeapScanClosure {
  public:
    // Records whether this CLD contains oops pointing into young-gen after scavenging.
    bool _has_oops_into_young_gen;

    CLDOopClosure(DefNewGeneration* g) : OffHeapScanClosure(g),
      _has_oops_into_young_gen(false) {}

    void do_oop(oop* p) {
      assert(!SerialHeap::heap()->is_in_reserved(p), "outside the heap");

      try_scavenge(p, [&] (oop new_obj) {
        if (!_has_oops_into_young_gen && is_in_young_gen(new_obj)) {
          _has_oops_into_young_gen = true;
        }
      });
    }

    void do_oop(narrowOop* p) { ShouldNotReachHere(); }
  };

  DefNewGeneration* _g;
 public:
  CLDScanClosure(DefNewGeneration* g) : _g(g) {}

  void do_cld(ClassLoaderData* cld) {
    // If the cld has not been dirtied we know that there's
    // no references into  the young gen and we can skip it.
    if (!cld->has_modified_oops()) {
      return;
    }

    CLDOopClosure oop_closure{_g};

    // Clean the cld since we're going to scavenge all the metadata.
    cld->oops_do(&oop_closure, ClassLoaderData::_claim_none, /*clear_modified_oops*/true);

    if (oop_closure._has_oops_into_young_gen) {
      cld->record_modified_oops();
    }
  }
};

class IsAliveClosure: public BoolObjectClosure {
  HeapWord*         _young_gen_end;
public:
  IsAliveClosure(DefNewGeneration* g): _young_gen_end(g->reserved().end()) {}

  bool do_object_b(oop p) {
    return cast_from_oop<HeapWord*>(p) >= _young_gen_end || p->is_forwarded();
  }
};

class AdjustWeakRootClosure: public OffHeapScanClosure {
  template <class T>
  void do_oop_work(T* p) {
    DEBUG_ONLY(SerialHeap* heap = SerialHeap::heap();)
    assert(!heap->is_in_reserved(p), "outside the heap");

    oop obj = RawAccess<IS_NOT_NULL>::oop_load(p);
    if (is_in_young_gen(obj)) {
      assert(!heap->young_gen()->to()->is_in_reserved(obj), "inv");
      assert(obj->is_forwarded(), "forwarded before weak-root-processing");
      oop new_obj = obj->forwardee();
      RawAccess<IS_NOT_NULL>::oop_store(p, new_obj);
    }
  }
 public:
  AdjustWeakRootClosure(DefNewGeneration* g): OffHeapScanClosure(g) {}

  void do_oop(oop* p)       { do_oop_work(p); }
  void do_oop(narrowOop* p) { ShouldNotReachHere(); }
};

class KeepAliveClosure: public OopClosure {
  DefNewGeneration* _young_gen;
  HeapWord*         _young_gen_end;
  CardTableRS* _rs;

  bool is_in_young_gen(void* p) const {
    return p < _young_gen_end;
  }

  template <class T>
  void do_oop_work(T* p) {
    oop obj = RawAccess<IS_NOT_NULL>::oop_load(p);

    if (is_in_young_gen(obj)) {
      oop new_obj = obj->is_forwarded() ? obj->forwardee()
                                        : _young_gen->copy_to_survivor_space(obj);
      RawAccess<IS_NOT_NULL>::oop_store(p, new_obj);

      if (is_in_young_gen(new_obj) && !is_in_young_gen(p)) {
        _rs->inline_write_ref_field_gc(p);
      }
    }
  }
public:
  KeepAliveClosure(DefNewGeneration* g) :
    _young_gen(g),
    _young_gen_end(g->reserved().end()),
    _rs(SerialHeap::heap()->rem_set()) {}

  void do_oop(oop* p)       { do_oop_work(p); }
  void do_oop(narrowOop* p) { do_oop_work(p); }
};

class FastEvacuateFollowersClosure: public VoidClosure {
  SerialHeap* _heap;
  YoungGenScanClosure* _young_cl;
  OldGenScanClosure* _old_cl;
public:
  FastEvacuateFollowersClosure(SerialHeap* heap,
                               YoungGenScanClosure* young_cl,
                               OldGenScanClosure* old_cl) :
    _heap(heap), _young_cl(young_cl), _old_cl(old_cl)
  {}

  void do_void() {
    _heap->scan_evacuated_objs(_young_cl, _old_cl);
  }
};

DefNewGeneration::DefNewGeneration(ReservedSpace rs,
                                   size_t initial_size,
                                   size_t min_size,
                                   size_t max_size,
                                   const char* policy)
  : Generation(rs, initial_size),
    _promotion_failed(false),
    _promo_failure_drain_in_progress(false),
    _string_dedup_requests()
{
  _eden_space = new ContiguousSpace();
  _from_space = new ContiguousSpace();
  _to_space   = new ContiguousSpace();

  init_spaces();

  // Compute the maximum eden and survivor space sizes. These sizes
  // are computed assuming the entire reserved space is committed.
  // These values are exported as performance counters.
  uintx size = _virtual_space.reserved_size();
  _max_survivor_size = compute_survivor_size(size, SpaceAlignment);

  // Eden might grow to be almost as large as the entire young generation.
  // We approximate this as the entire virtual space.
  _max_eden_size = size;

  // allocate the performance counters

  // Generation counters -- generation 0, 3 subspaces
  _gen_counters = new GenerationCounters("new", 0, 3,
      min_size, max_size, _virtual_space.committed_size());
  _gc_counters = new CollectorCounters(policy, 0);

  _eden_counters = new CSpaceCounters("eden", 0, _max_eden_size, _eden_space,
                                      _gen_counters);
  _from_counters = new CSpaceCounters("s0", 1, _max_survivor_size, _from_space,
                                      _gen_counters);
  _to_counters = new CSpaceCounters("s1", 2, _max_survivor_size, _to_space,
                                    _gen_counters);

  update_counters();
  _old_gen = nullptr;
  _tenuring_threshold = MaxTenuringThreshold;

  _ref_processor = nullptr;

  _gc_timer = new STWGCTimer();

  _gc_tracer = new DefNewTracer();
}

void DefNewGeneration::init_spaces() {
  // Using layout: from, to, eden, so only from can be non-empty.
  assert(eden()->is_empty(), "precondition");
  assert(to()->is_empty(), "precondition");

  if (!from()->is_empty()) {
    assert((char*) from()->bottom() == _virtual_space.low(), "inv");
  }

  // Compute sizes
  size_t size = _virtual_space.committed_size();
  size_t survivor_size = compute_survivor_size(size, SpaceAlignment);
  assert(survivor_size >= from()->used(), "inv");
  assert(size > 2 * survivor_size, "inv");
  size_t eden_size = size - (2 * survivor_size);
  assert(eden_size > 0 && survivor_size <= eden_size, "just checking");

  // layout: from, to, eden
  char* from_start = _virtual_space.low();
  char* to_start = from_start + survivor_size;
  char* eden_start = to_start + survivor_size;
  char* eden_end = eden_start + eden_size;

  assert(eden_end == _virtual_space.high(), "just checking");
  assert(is_aligned(from_start, SpaceAlignment), "checking alignment");
  assert(is_aligned(to_start, SpaceAlignment),   "checking alignment");
  assert(is_aligned(eden_start, SpaceAlignment), "checking alignment");
  assert(is_aligned(eden_end, SpaceAlignment), "checking alignment");

  MemRegion fromMR((HeapWord*)from_start, (HeapWord*)to_start);
  MemRegion toMR  ((HeapWord*)to_start, (HeapWord*)eden_start);
  MemRegion edenMR((HeapWord*)eden_start, (HeapWord*)eden_end);

  // Reset the spaces for their new regions.
  from()->initialize(fromMR, from()->is_empty());
  to()->initialize(toMR, true);
  eden()->initialize(edenMR, true);

  post_resize();
}

void DefNewGeneration::post_resize() {
  MemRegion cmr((HeapWord*)_virtual_space.low(),
                (HeapWord*)_virtual_space.high());
  SerialHeap::heap()->rem_set()->resize_covered_region(cmr);
}

void DefNewGeneration::swap_spaces() {
  ContiguousSpace* s = from();
  _from_space        = to();
  _to_space          = s;

  if (UsePerfData) {
    CSpaceCounters* c = _from_counters;
    _from_counters = _to_counters;
    _to_counters = c;
  }
}

bool DefNewGeneration::expand(size_t bytes) {
  assert(bytes != 0, "precondition");
  assert(is_aligned(bytes, SpaceAlignment), "precondition");

  bool success = _virtual_space.expand_by(bytes);
  if (!success) {
    log_info(gc)("Failed to expand young-gen by %zu bytes", bytes);
  }

  return success;
}

void DefNewGeneration::expand_eden_by(size_t delta_bytes) {
  if (!expand(delta_bytes)) {
    return;
  }

  MemRegion eden_mr{eden()->bottom(), (HeapWord*)_virtual_space.high()};
  eden()->initialize(eden_mr, eden()->is_empty());

  post_resize();
}

size_t DefNewGeneration::calculate_thread_increase_size(int threads_count) const {
    size_t thread_increase_size = 0;
    // Check an overflow at 'threads_count * NewSizeThreadIncrease'.
    if (threads_count > 0 && NewSizeThreadIncrease <= max_uintx / threads_count) {
      thread_increase_size = threads_count * NewSizeThreadIncrease;
    }
    return thread_increase_size;
}

size_t DefNewGeneration::adjust_for_thread_increase(size_t new_size_candidate,
                                                    size_t new_size_before,
                                                    size_t alignment,
                                                    size_t thread_increase_size) const {
  size_t desired_new_size = new_size_before;

  if (NewSizeThreadIncrease > 0 && thread_increase_size > 0) {

    // 1. Check an overflow at 'new_size_candidate + thread_increase_size'.
    if (new_size_candidate <= max_uintx - thread_increase_size) {
      new_size_candidate += thread_increase_size;

      // 2. Check an overflow at 'align_up'.
      size_t aligned_max = ((max_uintx - alignment) & ~(alignment-1));
      if (new_size_candidate <= aligned_max) {
        desired_new_size = align_up(new_size_candidate, alignment);
      }
    }
  }

  return desired_new_size;
}

size_t DefNewGeneration::calculate_desired_young_gen_bytes() const {
  size_t old_size = SerialHeap::heap()->old_gen()->capacity();
  size_t new_size_before = _virtual_space.committed_size();
  size_t min_new_size = NewSize;
  size_t max_new_size = reserved().byte_size();
  assert(min_new_size <= new_size_before &&
         new_size_before <= max_new_size,
         "just checking");
  // All space sizes must be multiples of Generation::GenGrain.
  size_t alignment = Generation::GenGrain;

  int threads_count = Threads::number_of_non_daemon_threads();
  size_t thread_increase_size = calculate_thread_increase_size(threads_count);

  size_t new_size_candidate = old_size / NewRatio;
  // Compute desired new generation size based on NewRatio and NewSizeThreadIncrease
  // and reverts to previous value if any overflow happens
  size_t desired_new_size = adjust_for_thread_increase(new_size_candidate, new_size_before,
                                                       alignment, thread_increase_size);

  // Adjust new generation size
  desired_new_size = clamp(desired_new_size, min_new_size, max_new_size);
  if (!from()->is_empty()) {
    // Mininum constraint to hold all live objs inside from-space.
    size_t min_survivor_size = align_up(from()->used(), alignment);

    // SurvivorRatio := eden_size / survivor_size
    // young-gen-size = eden_size                     + 2 * survivor_size
    //                = SurvivorRatio * survivor_size + 2 * survivor_size
    //                = (SurvivorRatio + 2) * survivor_size
    size_t min_young_gen_size = min_survivor_size * (SurvivorRatio + 2);

    desired_new_size = MAX2(min_young_gen_size, desired_new_size);
  }
  assert(is_aligned(desired_new_size, alignment), "postcondition");

  return desired_new_size;
}

void DefNewGeneration::resize_inner() {
  assert(eden()->is_empty(), "precondition");
  assert(to()->is_empty(), "precondition");

  size_t current_young_gen_size_bytes = _virtual_space.committed_size();
  size_t desired_young_gen_size_bytes = calculate_desired_young_gen_bytes();
  if (current_young_gen_size_bytes == desired_young_gen_size_bytes) {
    return;
  }

  // Commit/uncommit
  if (desired_young_gen_size_bytes > current_young_gen_size_bytes) {
    size_t delta_bytes = desired_young_gen_size_bytes - current_young_gen_size_bytes;
    if (!expand(delta_bytes)) {
      return;
    }
  } else {
    size_t delta_bytes = current_young_gen_size_bytes - desired_young_gen_size_bytes;
    _virtual_space.shrink_by(delta_bytes);
  }

  assert(desired_young_gen_size_bytes == _virtual_space.committed_size(), "inv");

  init_spaces();

  log_debug(gc, ergo, heap)("New generation size %zuK->%zuK [eden=%zuK,survivor=%zuK]",
    current_young_gen_size_bytes/K, _virtual_space.committed_size()/K,
    eden()->capacity()/K, from()->capacity()/K);
}

void DefNewGeneration::resize_after_young_gc() {
  // Called only after successful young-gc.
  assert(eden()->is_empty(), "precondition");
  assert(to()->is_empty(), "precondition");

  if ((char*)to()->bottom() == _virtual_space.low()) {
    // layout: to, from, eden; can't resize.
    return;
  }

  assert((char*)from()->bottom() == _virtual_space.low(), "inv");
  resize_inner();
}

void DefNewGeneration::resize_after_full_gc() {
  if (eden()->is_empty() && from()->is_empty() && to()->is_empty()) {
    resize_inner();
    return;
  }

  // Usually the young-gen is empty after full-gc.
  // This is the extreme case; expand young-gen to its max size.
  if (_virtual_space.uncommitted_size() == 0) {
    // Already at its max size.
    return;
  }

  // Keep from/to and expand eden.
  expand_eden_by(_virtual_space.uncommitted_size());
}

void DefNewGeneration::ref_processor_init() {
  assert(_ref_processor == nullptr, "a reference processor already exists");
  assert(!_reserved.is_empty(), "empty generation?");
  _span_based_discoverer.set_span(_reserved);
  _ref_processor = new ReferenceProcessor(&_span_based_discoverer);    // a vanilla reference processor
}

size_t DefNewGeneration::capacity() const {
  return eden()->capacity()
       + from()->capacity();  // to() is only used during scavenge
}

size_t DefNewGeneration::used() const {
  return eden()->used()
       + from()->used();      // to() is only used during scavenge
}

size_t DefNewGeneration::free() const {
  return eden()->free()
       + from()->free();      // to() is only used during scavenge
}

size_t DefNewGeneration::max_capacity() const {
  const size_t reserved_bytes = reserved().byte_size();
  const size_t min_survivor_bytes = SpaceAlignment;
  return reserved_bytes - min_survivor_bytes;
}

bool DefNewGeneration::is_in(const void* p) const {
  return eden()->is_in(p)
      || from()->is_in(p)
      || to()  ->is_in(p);
}

size_t DefNewGeneration::unsafe_max_alloc_nogc() const {
  return eden()->free();
}

size_t DefNewGeneration::capacity_before_gc() const {
  return eden()->capacity();
}

void DefNewGeneration::object_iterate(ObjectClosure* blk) {
  eden()->object_iterate(blk);
  from()->object_iterate(blk);
}

// If "p" is in the space, returns the address of the start of the
// "block" that contains "p".  We say "block" instead of "object" since
// some heaps may not pack objects densely; a chunk may either be an
// object or a non-object.  If "p" is not in the space, return null.
// Very general, slow implementation.
static HeapWord* block_start_const(const ContiguousSpace* cs, const void* p) {
  assert(MemRegion(cs->bottom(), cs->end()).contains(p),
         "p (" PTR_FORMAT ") not in space [" PTR_FORMAT ", " PTR_FORMAT ")",
         p2i(p), p2i(cs->bottom()), p2i(cs->end()));
  if (p >= cs->top()) {
    return cs->top();
  } else {
    HeapWord* last = cs->bottom();
    HeapWord* cur = last;
    while (cur <= p) {
      last = cur;
      cur += cast_to_oop(cur)->size();
    }
    assert(oopDesc::is_oop(cast_to_oop(last)), PTR_FORMAT " should be an object start", p2i(last));
    return last;
  }
}

HeapWord* DefNewGeneration::block_start(const void* p) const {
  if (eden()->is_in_reserved(p)) {
    return block_start_const(eden(), p);
  }
  if (from()->is_in_reserved(p)) {
    return block_start_const(from(), p);
  }
  assert(to()->is_in_reserved(p), "inv");
  return block_start_const(to(), p);
}

void DefNewGeneration::adjust_desired_tenuring_threshold() {
  // Set the desired survivor size to half the real survivor space
  size_t const survivor_capacity = to()->capacity() / HeapWordSize;
  size_t const desired_survivor_size = (size_t)((((double)survivor_capacity) * TargetSurvivorRatio) / 100);

  _tenuring_threshold = age_table()->compute_tenuring_threshold(desired_survivor_size);

  if (UsePerfData) {
    GCPolicyCounters* gc_counters = SerialHeap::heap()->counters();
    gc_counters->tenuring_threshold()->set_value(_tenuring_threshold);
    gc_counters->desired_survivor_size()->set_value(desired_survivor_size * oopSize);
  }

  age_table()->print_age_table();
}

bool DefNewGeneration::collect(bool clear_all_soft_refs) {
  SerialHeap* heap = SerialHeap::heap();

  assert(to()->is_empty(), "Else not collection_attempt_is_safe");
  _gc_timer->register_gc_start();
  _gc_tracer->report_gc_start(heap->gc_cause(), _gc_timer->gc_start());
  _ref_processor->start_discovery(clear_all_soft_refs);

  _old_gen = heap->old_gen();

  init_assuming_no_promotion_failure();

  GCTraceTime(Trace, gc, phases) tm("DefNew", nullptr, heap->gc_cause());

  heap->trace_heap_before_gc(_gc_tracer);

  // These can be shared for all code paths
  IsAliveClosure is_alive(this);

  age_table()->clear();

  YoungGenScanClosure young_gen_cl(this);
  OldGenScanClosure   old_gen_cl(this);

  FastEvacuateFollowersClosure evacuate_followers(heap,
                                                  &young_gen_cl,
                                                  &old_gen_cl);

  {
    RootScanClosure oop_closure{this};
    CLDScanClosure cld_closure{this};

    NMethodToOopClosure nmethod_closure(&oop_closure,
                                        NMethodToOopClosure::FixRelocations);

    // Starting tracing from roots, there are 4 kinds of roots in young-gc.
    //
    // 1. old-to-young pointers; processing them before relocating other kinds
    // of roots.
    _old_gen->scan_old_to_young_refs();

    // 2. CLD; visit all (strong+weak) clds with the same closure, because we
    // don't perform class unloading during young-gc.
    ClassLoaderDataGraph::cld_do(&cld_closure);

    // 3. Threads stack frames and nmethods.
    // Only nmethods that contain pointers into-young need to be processed
    // during young-gc, and they are tracked in ScavengableNMethods
    Threads::oops_do(&oop_closure, nullptr);
    ScavengableNMethods::nmethods_do(&nmethod_closure);

    // 4. VM internal roots.
    OopStorageSet::strong_oops_do(&oop_closure);
  }

  // "evacuate followers".
  evacuate_followers.do_void();

  {
    // Reference processing
    KeepAliveClosure keep_alive(this);
    ReferenceProcessor* rp = ref_processor();
    ReferenceProcessorPhaseTimes pt(_gc_timer, rp->max_num_queues());
    SerialGCRefProcProxyTask task(is_alive, keep_alive, evacuate_followers);
    const ReferenceProcessorStats& stats = rp->process_discovered_references(task, nullptr, pt);
    _gc_tracer->report_gc_reference_stats(stats);
    _gc_tracer->report_tenuring_threshold(tenuring_threshold());
    pt.print_all_references();
  }

  {
    AdjustWeakRootClosure cl{this};
    WeakProcessor::weak_oops_do(&is_alive, &cl);
  }

  _string_dedup_requests.flush();

  if (!_promotion_failed) {
    // Swap the survivor spaces.
    eden()->clear(SpaceDecorator::Mangle);
    from()->clear(SpaceDecorator::Mangle);
    swap_spaces();

    assert(to()->is_empty(), "to space should be empty now");

    adjust_desired_tenuring_threshold();
  } else {
    assert(_promo_failure_scan_stack.is_empty(), "post condition");
    _promo_failure_scan_stack.clear(true); // Clear cached segments.

    remove_forwarding_pointers();
    log_info(gc, promotion)("Promotion failed");

    _gc_tracer->report_promotion_failed(_promotion_failed_info);

    // Reset the PromotionFailureALot counters.
    NOT_PRODUCT(heap->reset_promotion_should_fail();)
  }

  heap->trace_heap_after_gc(_gc_tracer);

  _gc_timer->register_gc_end();

  _gc_tracer->report_gc_end(_gc_timer->gc_end(), _gc_timer->time_partitions());

  return !_promotion_failed;
}

void DefNewGeneration::init_assuming_no_promotion_failure() {
  _promotion_failed = false;
  _promotion_failed_info.reset();
}

void DefNewGeneration::remove_forwarding_pointers() {
  assert(_promotion_failed, "precondition");

  // Will enter Full GC soon due to failed promotion. Must reset the mark word
  // of objs in young-gen so that no objs are marked (forwarded) when Full GC
  // starts. (The mark word is overloaded: `is_marked()` == `is_forwarded()`.)
  struct ResetForwardedMarkWord : ObjectClosure {
    void do_object(oop obj) override {
      if (obj->is_self_forwarded()) {
        obj->unset_self_forwarded();
      } else if (obj->is_forwarded()) {
        // To restore the klass-bits in the header.
        // Needed for object iteration to work properly.
        obj->set_mark(obj->forwardee()->prototype_mark());
      }
    }
  } cl;
  eden()->object_iterate(&cl);
  from()->object_iterate(&cl);
}

void DefNewGeneration::handle_promotion_failure(oop old) {
  log_debug(gc, promotion)("Promotion failure size = %zu) ", old->size());

  _promotion_failed = true;
  _promotion_failed_info.register_copy_failure(old->size());

  ContinuationGCSupport::transform_stack_chunk(old);

  // forward to self
  old->forward_to_self();

  _promo_failure_scan_stack.push(old);

  if (!_promo_failure_drain_in_progress) {
    // prevent recursion in copy_to_survivor_space()
    _promo_failure_drain_in_progress = true;
    drain_promo_failure_scan_stack();
    _promo_failure_drain_in_progress = false;
  }
}

oop DefNewGeneration::copy_to_survivor_space(oop old) {
  assert(is_in_reserved(old) && !old->is_forwarded(),
         "shouldn't be scavenging this oop");
  size_t s = old->size();
  oop obj = nullptr;

  // Try allocating obj in to-space (unless too old)
  if (old->age() < tenuring_threshold()) {
    obj = cast_to_oop(to()->allocate(s));
  }

  bool new_obj_is_tenured = false;
  // Otherwise try allocating obj tenured
  if (obj == nullptr) {
    obj = _old_gen->allocate_for_promotion(old, s);
    if (obj == nullptr) {
      handle_promotion_failure(old);
      return old;
    }

    new_obj_is_tenured = true;
  }

  // Prefetch beyond obj
  const intx interval = PrefetchCopyIntervalInBytes;
  Prefetch::write(obj, interval);

  // Copy obj
  Copy::aligned_disjoint_words(cast_from_oop<HeapWord*>(old), cast_from_oop<HeapWord*>(obj), s);

  ContinuationGCSupport::transform_stack_chunk(obj);

  if (!new_obj_is_tenured) {
    // Increment age if obj still in new generation
    obj->incr_age();
    age_table()->add(obj, s);
  }

  // Done, insert forward pointer to obj in this header
  old->forward_to(obj);

  if (SerialStringDedup::is_candidate_from_evacuation(obj, new_obj_is_tenured)) {
    // Record old; request adds a new weak reference, which reference
    // processing expects to refer to a from-space object.
    _string_dedup_requests.add(old);
  }
  return obj;
}

void DefNewGeneration::drain_promo_failure_scan_stack() {
  PromoteFailureClosure cl{this};
  while (!_promo_failure_scan_stack.is_empty()) {
     oop obj = _promo_failure_scan_stack.pop();
     obj->oop_iterate(&cl);
  }
}

void DefNewGeneration::contribute_scratch(void*& scratch, size_t& num_words) {
  if (_promotion_failed) {
    return;
  }

  const size_t MinFreeScratchWords = 100;

  ContiguousSpace* to_space = to();
  const size_t free_words = pointer_delta(to_space->end(), to_space->top());
  if (free_words >= MinFreeScratchWords) {
    scratch = to_space->top();
    num_words = free_words;
  }
}

void DefNewGeneration::reset_scratch() {
  // If contributing scratch in to_space, mangle all of
  // to_space if ZapUnusedHeapArea.  This is needed because
  // top is not maintained while using to-space as scratch.
  if (ZapUnusedHeapArea) {
    to()->mangle_unused_area();
  }
}

void DefNewGeneration::gc_epilogue() {
  assert(!GCLocker::is_active(), "We should not be executing here");
  // update the generation and space performance counters
  update_counters();
}

void DefNewGeneration::update_counters() {
  if (UsePerfData) {
    _eden_counters->update_all();
    _from_counters->update_all();
    _to_counters->update_all();
    _gen_counters->update_capacity(_virtual_space.committed_size());
  }
}

void DefNewGeneration::verify() {
  eden()->verify();
  from()->verify();
    to()->verify();
}

void DefNewGeneration::print_on(outputStream* st) const {
  st->print("%-10s", name());

  st->print(" total %zuK, used %zuK ", capacity() / K, used() / K);
  _virtual_space.print_space_boundaries_on(st);

  StreamIndentor si(st, 1);
  eden()->print_on(st, "eden ");
  from()->print_on(st, "from ");
  to()->print_on(st, "to   ");
}

HeapWord* DefNewGeneration::expand_and_allocate(size_t word_size) {
  assert(Heap_lock->is_locked(), "precondition");

  size_t eden_free_bytes = eden()->free();
  size_t requested_bytes = word_size * HeapWordSize;
  if (eden_free_bytes < requested_bytes) {
    size_t expand_bytes = requested_bytes - eden_free_bytes;
    expand_eden_by(align_up(expand_bytes, SpaceAlignment));
  }

  HeapWord* result = eden()->allocate(word_size);
  return result;
}

HeapWord* DefNewGeneration::par_allocate(size_t word_size) {
  return eden()->par_allocate(word_size);
}

size_t DefNewGeneration::tlab_capacity() const {
  return eden()->capacity();
}

size_t DefNewGeneration::tlab_used() const {
  return eden()->used();
}

size_t DefNewGeneration::unsafe_max_tlab_alloc() const {
  return unsafe_max_alloc_nogc();
}
