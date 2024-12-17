/*
 * Copyright (c) 2001, 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/shared/referencePolicy.hpp"
#include "gc/shared/referenceProcessorPhaseTimes.hpp"
#include "gc/shared/space.hpp"
#include "gc/shared/spaceDecorator.hpp"
#include "gc/shared/strongRootsScope.hpp"
#include "gc/shared/weakProcessor.hpp"
#include "logging/log.hpp"
#include "memory/iterator.inline.hpp"
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
    ClassLoaderData* _scanned_cld;

    template <typename T>
    void do_oop_work(T* p) {
      assert(!SerialHeap::heap()->is_in_reserved(p), "outside the heap");

      try_scavenge(p, [&] (oop new_obj) {
        assert(_scanned_cld != nullptr, "inv");
        if (is_in_young_gen(new_obj) && !_scanned_cld->has_modified_oops()) {
          _scanned_cld->record_modified_oops();
        }
      });
    }

  public:
    CLDOopClosure(DefNewGeneration* g) : OffHeapScanClosure(g),
      _scanned_cld(nullptr) {}

    void set_scanned_cld(ClassLoaderData* cld) {
      assert(cld == nullptr || _scanned_cld == nullptr, "Must be");
      _scanned_cld = cld;
    }

    void do_oop(oop* p)       { do_oop_work(p); }
    void do_oop(narrowOop* p) { ShouldNotReachHere(); }
  };

  CLDOopClosure _oop_closure;
 public:
  CLDScanClosure(DefNewGeneration* g) : _oop_closure(g) {}

  void do_cld(ClassLoaderData* cld) {
    // If the cld has not been dirtied we know that there's
    // no references into  the young gen and we can skip it.
    if (cld->has_modified_oops()) {

      // Tell the closure which CLD is being scanned so that it can be dirtied
      // if oops are left pointing into the young gen.
      _oop_closure.set_scanned_cld(cld);

      // Clean the cld since we're going to scavenge all the metadata.
      cld->oops_do(&_oop_closure, ClassLoaderData::_claim_none, /*clear_modified_oops*/true);

      _oop_closure.set_scanned_cld(nullptr);
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
  MemRegion cmr((HeapWord*)_virtual_space.low(),
                (HeapWord*)_virtual_space.high());
  SerialHeap* gch = SerialHeap::heap();

  gch->rem_set()->resize_covered_region(cmr);

  _eden_space = new ContiguousSpace();
  _from_space = new ContiguousSpace();
  _to_space   = new ContiguousSpace();

  // Compute the maximum eden and survivor space sizes. These sizes
  // are computed assuming the entire reserved space is committed.
  // These values are exported as performance counters.
  uintx size = _virtual_space.reserved_size();
  _max_survivor_size = compute_survivor_size(size, SpaceAlignment);
  _max_eden_size = size - (2*_max_survivor_size);

  // allocate the performance counters

  // Generation counters -- generation 0, 3 subspaces
  _gen_counters = new GenerationCounters("new", 0, 3,
      min_size, max_size, &_virtual_space);
  _gc_counters = new CollectorCounters(policy, 0);

  _eden_counters = new CSpaceCounters("eden", 0, _max_eden_size, _eden_space,
                                      _gen_counters);
  _from_counters = new CSpaceCounters("s0", 1, _max_survivor_size, _from_space,
                                      _gen_counters);
  _to_counters = new CSpaceCounters("s1", 2, _max_survivor_size, _to_space,
                                    _gen_counters);

  compute_space_boundaries(0, SpaceDecorator::Clear, SpaceDecorator::Mangle);
  update_counters();
  _old_gen = nullptr;
  _tenuring_threshold = MaxTenuringThreshold;
  _pretenure_size_threshold_words = PretenureSizeThreshold >> LogHeapWordSize;

  _ref_processor = nullptr;

  _gc_timer = new STWGCTimer();

  _gc_tracer = new DefNewTracer();
}

void DefNewGeneration::compute_space_boundaries(uintx minimum_eden_size,
                                                bool clear_space,
                                                bool mangle_space) {
  // If the spaces are being cleared (only done at heap initialization
  // currently), the survivor spaces need not be empty.
  // Otherwise, no care is taken for used areas in the survivor spaces
  // so check.
  assert(clear_space || (to()->is_empty() && from()->is_empty()),
    "Initialization of the survivor spaces assumes these are empty");

  // Compute sizes
  uintx size = _virtual_space.committed_size();
  uintx survivor_size = compute_survivor_size(size, SpaceAlignment);
  uintx eden_size = size - (2*survivor_size);
  if (eden_size > max_eden_size()) {
    // Need to reduce eden_size to satisfy the max constraint. The delta needs
    // to be 2*SpaceAlignment aligned so that both survivors are properly
    // aligned.
    uintx eden_delta = align_up(eden_size - max_eden_size(), 2*SpaceAlignment);
    eden_size     -= eden_delta;
    survivor_size += eden_delta/2;
  }
  assert(eden_size > 0 && survivor_size <= eden_size, "just checking");

  if (eden_size < minimum_eden_size) {
    // May happen due to 64Kb rounding, if so adjust eden size back up
    minimum_eden_size = align_up(minimum_eden_size, SpaceAlignment);
    uintx maximum_survivor_size = (size - minimum_eden_size) / 2;
    uintx unaligned_survivor_size =
      align_down(maximum_survivor_size, SpaceAlignment);
    survivor_size = MAX2(unaligned_survivor_size, SpaceAlignment);
    eden_size = size - (2*survivor_size);
    assert(eden_size > 0 && survivor_size <= eden_size, "just checking");
    assert(eden_size >= minimum_eden_size, "just checking");
  }

  char *eden_start = _virtual_space.low();
  char *from_start = eden_start + eden_size;
  char *to_start   = from_start + survivor_size;
  char *to_end     = to_start   + survivor_size;

  assert(to_end == _virtual_space.high(), "just checking");
  assert(is_aligned(eden_start, SpaceAlignment), "checking alignment");
  assert(is_aligned(from_start, SpaceAlignment), "checking alignment");
  assert(is_aligned(to_start, SpaceAlignment),   "checking alignment");

  MemRegion edenMR((HeapWord*)eden_start, (HeapWord*)from_start);
  MemRegion fromMR((HeapWord*)from_start, (HeapWord*)to_start);
  MemRegion toMR  ((HeapWord*)to_start, (HeapWord*)to_end);

  // A minimum eden size implies that there is a part of eden that
  // is being used and that affects the initialization of any
  // newly formed eden.
  bool live_in_eden = minimum_eden_size > 0;

  // Reset the spaces for their new regions.
  eden()->initialize(edenMR,
                     clear_space && !live_in_eden,
                     SpaceDecorator::Mangle);
  // If clear_space and live_in_eden, we will not have cleared any
  // portion of eden above its top. This can cause newly
  // expanded space not to be mangled if using ZapUnusedHeapArea.
  // We explicitly do such mangling here.
  if (ZapUnusedHeapArea && clear_space && live_in_eden && mangle_space) {
    eden()->mangle_unused_area();
  }
  from()->initialize(fromMR, clear_space, mangle_space);
  to()->initialize(toMR, clear_space, mangle_space);
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
  HeapWord* prev_high = (HeapWord*) _virtual_space.high();
  bool success = _virtual_space.expand_by(bytes);
  if (success && ZapUnusedHeapArea) {
    // Mangle newly committed space immediately because it
    // can be done here more simply that after the new
    // spaces have been computed.
    HeapWord* new_high = (HeapWord*) _virtual_space.high();
    MemRegion mangle_region(prev_high, new_high);
    SpaceMangler::mangle_region(mangle_region);
  }

  // Do not attempt an expand-to-the reserve size.  The
  // request should properly observe the maximum size of
  // the generation so an expand-to-reserve should be
  // unnecessary.  Also a second call to expand-to-reserve
  // value potentially can cause an undue expansion.
  // For example if the first expand fail for unknown reasons,
  // but the second succeeds and expands the heap to its maximum
  // value.
  if (GCLocker::is_active()) {
    log_debug(gc)("Garbage collection disabled, expanded heap instead");
  }

  return success;
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

void DefNewGeneration::compute_new_size() {
  // This is called after a GC that includes the old generation, so from-space
  // will normally be empty.
  // Note that we check both spaces, since if scavenge failed they revert roles.
  // If not we bail out (otherwise we would have to relocate the objects).
  if (!from()->is_empty() || !to()->is_empty()) {
    return;
  }

  SerialHeap* gch = SerialHeap::heap();

  size_t old_size = gch->old_gen()->capacity();
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
  assert(desired_new_size <= max_new_size, "just checking");

  bool changed = false;
  if (desired_new_size > new_size_before) {
    size_t change = desired_new_size - new_size_before;
    assert(change % alignment == 0, "just checking");
    if (expand(change)) {
       changed = true;
    }
    // If the heap failed to expand to the desired size,
    // "changed" will be false.  If the expansion failed
    // (and at this point it was expected to succeed),
    // ignore the failure (leaving "changed" as false).
  }
  if (desired_new_size < new_size_before && eden()->is_empty()) {
    // bail out of shrinking if objects in eden
    size_t change = new_size_before - desired_new_size;
    assert(change % alignment == 0, "just checking");
    _virtual_space.shrink_by(change);
    changed = true;
  }
  if (changed) {
    // The spaces have already been mangled at this point but
    // may not have been cleared (set top = bottom) and should be.
    // Mangling was done when the heap was being expanded.
    compute_space_boundaries(eden()->used(),
                             SpaceDecorator::Clear,
                             SpaceDecorator::DontMangle);
    MemRegion cmr((HeapWord*)_virtual_space.low(),
                  (HeapWord*)_virtual_space.high());
    gch->rem_set()->resize_covered_region(cmr);

    log_debug(gc, ergo, heap)(
        "New generation size " SIZE_FORMAT "K->" SIZE_FORMAT "K [eden=" SIZE_FORMAT "K,survivor=" SIZE_FORMAT "K]",
        new_size_before/K, _virtual_space.committed_size()/K,
        eden()->capacity()/K, from()->capacity()/K);
    log_trace(gc, ergo, heap)(
        "  [allowed " SIZE_FORMAT "K extra for %d threads]",
          thread_increase_size/K, threads_count);
      }
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
  return reserved_bytes - compute_survivor_size(reserved_bytes, SpaceAlignment);
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
  to()->clear(SpaceDecorator::Mangle);

  YoungGenScanClosure young_gen_cl(this);
  OldGenScanClosure   old_gen_cl(this);

  FastEvacuateFollowersClosure evacuate_followers(heap,
                                                  &young_gen_cl,
                                                  &old_gen_cl);

  {
    StrongRootsScope srs(0);
    RootScanClosure root_cl{this};
    CLDScanClosure cld_cl{this};

    MarkingNMethodClosure code_cl(&root_cl,
                                  NMethodToOopClosure::FixRelocations,
                                  false /* keepalive_nmethods */);

    HeapWord* saved_top_in_old_gen = _old_gen->space()->top();
    heap->process_roots(SerialHeap::SO_ScavengeCodeCache,
                        &root_cl,
                        &cld_cl,
                        &cld_cl,
                        &code_cl);

    _old_gen->scan_old_to_young_refs(saved_top_in_old_gen);
  }

  // "evacuate followers".
  evacuate_followers.do_void();

  {
    // Reference processing
    KeepAliveClosure keep_alive(this);
    ReferenceProcessor* rp = ref_processor();
    ReferenceProcessorPhaseTimes pt(_gc_timer, rp->max_num_queues());
    SerialGCRefProcProxyTask task(is_alive, keep_alive, evacuate_followers);
    const ReferenceProcessorStats& stats = rp->process_discovered_references(task, pt);
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
  log_debug(gc, promotion)("Promotion failure size = " SIZE_FORMAT ") ", old->size());

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

void DefNewGeneration::gc_epilogue(bool full) {
  assert(!GCLocker::is_active(), "We should not be executing here");
  // update the generation and space performance counters
  update_counters();
}

void DefNewGeneration::update_counters() {
  if (UsePerfData) {
    _eden_counters->update_all();
    _from_counters->update_all();
    _to_counters->update_all();
    _gen_counters->update_all();
  }
}

void DefNewGeneration::verify() {
  eden()->verify();
  from()->verify();
    to()->verify();
}

void DefNewGeneration::print_on(outputStream* st) const {
  st->print(" %-10s", name());

  st->print(" total " SIZE_FORMAT "K, used " SIZE_FORMAT "K",
            capacity()/K, used()/K);
  st->print_cr(" [" PTR_FORMAT ", " PTR_FORMAT ", " PTR_FORMAT ")",
               p2i(_virtual_space.low_boundary()),
               p2i(_virtual_space.high()),
               p2i(_virtual_space.high_boundary()));

  st->print("  eden");
  eden()->print_on(st);
  st->print("  from");
  from()->print_on(st);
  st->print("  to  ");
  to()->print_on(st);
}

HeapWord* DefNewGeneration::allocate(size_t word_size) {
  // This is the slow-path allocation for the DefNewGeneration.
  // Most allocations are fast-path in compiled code.
  // We try to allocate from the eden.  If that works, we are happy.
  // Note that since DefNewGeneration supports lock-free allocation, we
  // have to use it here, as well.
  HeapWord* result = eden()->par_allocate(word_size);
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
