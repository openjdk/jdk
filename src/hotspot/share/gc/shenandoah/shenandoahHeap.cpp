/*
 * Copyright (c) 2013, 2019, Red Hat, Inc. All rights reserved.
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
#include "memory/allocation.hpp"

#include "gc/shared/gcTimer.hpp"
#include "gc/shared/gcTraceTime.inline.hpp"
#include "gc/shared/memAllocator.hpp"
#include "gc/shared/parallelCleaning.hpp"
#include "gc/shared/plab.hpp"

#include "gc/shenandoah/shenandoahAllocTracker.hpp"
#include "gc/shenandoah/shenandoahBarrierSet.hpp"
#include "gc/shenandoah/shenandoahBrooksPointer.hpp"
#include "gc/shenandoah/shenandoahCollectionSet.hpp"
#include "gc/shenandoah/shenandoahCollectorPolicy.hpp"
#include "gc/shenandoah/shenandoahConcurrentMark.inline.hpp"
#include "gc/shenandoah/shenandoahControlThread.hpp"
#include "gc/shenandoah/shenandoahFreeSet.hpp"
#include "gc/shenandoah/shenandoahPhaseTimings.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahHeapRegion.hpp"
#include "gc/shenandoah/shenandoahHeapRegionSet.hpp"
#include "gc/shenandoah/shenandoahMarkCompact.hpp"
#include "gc/shenandoah/shenandoahMarkingContext.inline.hpp"
#include "gc/shenandoah/shenandoahMemoryPool.hpp"
#include "gc/shenandoah/shenandoahMetrics.hpp"
#include "gc/shenandoah/shenandoahMonitoringSupport.hpp"
#include "gc/shenandoah/shenandoahOopClosures.inline.hpp"
#include "gc/shenandoah/shenandoahPacer.inline.hpp"
#include "gc/shenandoah/shenandoahRootProcessor.hpp"
#include "gc/shenandoah/shenandoahStringDedup.hpp"
#include "gc/shenandoah/shenandoahUtils.hpp"
#include "gc/shenandoah/shenandoahVerifier.hpp"
#include "gc/shenandoah/shenandoahCodeRoots.hpp"
#include "gc/shenandoah/shenandoahVMOperations.hpp"
#include "gc/shenandoah/shenandoahWorkGroup.hpp"
#include "gc/shenandoah/shenandoahWorkerPolicy.hpp"
#include "gc/shenandoah/heuristics/shenandoahAdaptiveHeuristics.hpp"
#include "gc/shenandoah/heuristics/shenandoahAggressiveHeuristics.hpp"
#include "gc/shenandoah/heuristics/shenandoahCompactHeuristics.hpp"
#include "gc/shenandoah/heuristics/shenandoahPassiveHeuristics.hpp"
#include "gc/shenandoah/heuristics/shenandoahStaticHeuristics.hpp"
#include "gc/shenandoah/heuristics/shenandoahTraversalHeuristics.hpp"

#include "memory/metaspace.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/safepointMechanism.hpp"
#include "runtime/vmThread.hpp"
#include "services/mallocTracker.hpp"

ShenandoahUpdateRefsClosure::ShenandoahUpdateRefsClosure() : _heap(ShenandoahHeap::heap()) {}

#ifdef ASSERT
template <class T>
void ShenandoahAssertToSpaceClosure::do_oop_work(T* p) {
  T o = RawAccess<>::oop_load(p);
  if (! CompressedOops::is_null(o)) {
    oop obj = CompressedOops::decode_not_null(o);
    shenandoah_assert_not_forwarded(p, obj);
  }
}

void ShenandoahAssertToSpaceClosure::do_oop(narrowOop* p) { do_oop_work(p); }
void ShenandoahAssertToSpaceClosure::do_oop(oop* p)       { do_oop_work(p); }
#endif

class ShenandoahPretouchHeapTask : public AbstractGangTask {
private:
  ShenandoahRegionIterator _regions;
  const size_t _page_size;
public:
  ShenandoahPretouchHeapTask(size_t page_size) :
    AbstractGangTask("Shenandoah Pretouch Heap"),
    _page_size(page_size) {}

  virtual void work(uint worker_id) {
    ShenandoahHeapRegion* r = _regions.next();
    while (r != NULL) {
      os::pretouch_memory(r->bottom(), r->end(), _page_size);
      r = _regions.next();
    }
  }
};

class ShenandoahPretouchBitmapTask : public AbstractGangTask {
private:
  ShenandoahRegionIterator _regions;
  char* _bitmap_base;
  const size_t _bitmap_size;
  const size_t _page_size;
public:
  ShenandoahPretouchBitmapTask(char* bitmap_base, size_t bitmap_size, size_t page_size) :
    AbstractGangTask("Shenandoah Pretouch Bitmap"),
    _bitmap_base(bitmap_base),
    _bitmap_size(bitmap_size),
    _page_size(page_size) {}

  virtual void work(uint worker_id) {
    ShenandoahHeapRegion* r = _regions.next();
    while (r != NULL) {
      size_t start = r->region_number()       * ShenandoahHeapRegion::region_size_bytes() / MarkBitMap::heap_map_factor();
      size_t end   = (r->region_number() + 1) * ShenandoahHeapRegion::region_size_bytes() / MarkBitMap::heap_map_factor();
      assert (end <= _bitmap_size, "end is sane: " SIZE_FORMAT " < " SIZE_FORMAT, end, _bitmap_size);

      os::pretouch_memory(_bitmap_base + start, _bitmap_base + end, _page_size);

      r = _regions.next();
    }
  }
};

jint ShenandoahHeap::initialize() {
  ShenandoahBrooksPointer::initial_checks();

  initialize_heuristics();

  //
  // Figure out heap sizing
  //

  size_t init_byte_size = collector_policy()->initial_heap_byte_size();
  size_t max_byte_size  = collector_policy()->max_heap_byte_size();
  size_t heap_alignment = collector_policy()->heap_alignment();

  size_t reg_size_bytes = ShenandoahHeapRegion::region_size_bytes();

  if (ShenandoahAlwaysPreTouch) {
    // Enabled pre-touch means the entire heap is committed right away.
    init_byte_size = max_byte_size;
  }

  Universe::check_alignment(max_byte_size,  reg_size_bytes, "Shenandoah heap");
  Universe::check_alignment(init_byte_size, reg_size_bytes, "Shenandoah heap");

  _num_regions = ShenandoahHeapRegion::region_count();

  size_t num_committed_regions = init_byte_size / reg_size_bytes;
  num_committed_regions = MIN2(num_committed_regions, _num_regions);
  assert(num_committed_regions <= _num_regions, "sanity");

  _initial_size = num_committed_regions * reg_size_bytes;
  _committed = _initial_size;

  size_t heap_page_size   = UseLargePages ? (size_t)os::large_page_size() : (size_t)os::vm_page_size();
  size_t bitmap_page_size = UseLargePages ? (size_t)os::large_page_size() : (size_t)os::vm_page_size();

  //
  // Reserve and commit memory for heap
  //

  ReservedSpace heap_rs = Universe::reserve_heap(max_byte_size, heap_alignment);
  initialize_reserved_region((HeapWord*)heap_rs.base(), (HeapWord*) (heap_rs.base() + heap_rs.size()));
  _heap_region = MemRegion((HeapWord*)heap_rs.base(), heap_rs.size() / HeapWordSize);
  _heap_region_special = heap_rs.special();

  assert((((size_t) base()) & ShenandoahHeapRegion::region_size_bytes_mask()) == 0,
         "Misaligned heap: " PTR_FORMAT, p2i(base()));

  ReservedSpace sh_rs = heap_rs.first_part(max_byte_size);
  if (!_heap_region_special) {
    os::commit_memory_or_exit(sh_rs.base(), _initial_size, heap_alignment, false,
                              "Cannot commit heap memory");
  }

  //
  // Reserve and commit memory for bitmap(s)
  //

  _bitmap_size = MarkBitMap::compute_size(heap_rs.size());
  _bitmap_size = align_up(_bitmap_size, bitmap_page_size);

  size_t bitmap_bytes_per_region = reg_size_bytes / MarkBitMap::heap_map_factor();

  guarantee(bitmap_bytes_per_region != 0,
            "Bitmap bytes per region should not be zero");
  guarantee(is_power_of_2(bitmap_bytes_per_region),
            "Bitmap bytes per region should be power of two: " SIZE_FORMAT, bitmap_bytes_per_region);

  if (bitmap_page_size > bitmap_bytes_per_region) {
    _bitmap_regions_per_slice = bitmap_page_size / bitmap_bytes_per_region;
    _bitmap_bytes_per_slice = bitmap_page_size;
  } else {
    _bitmap_regions_per_slice = 1;
    _bitmap_bytes_per_slice = bitmap_bytes_per_region;
  }

  guarantee(_bitmap_regions_per_slice >= 1,
            "Should have at least one region per slice: " SIZE_FORMAT,
            _bitmap_regions_per_slice);

  guarantee(((_bitmap_bytes_per_slice) % bitmap_page_size) == 0,
            "Bitmap slices should be page-granular: bps = " SIZE_FORMAT ", page size = " SIZE_FORMAT,
            _bitmap_bytes_per_slice, bitmap_page_size);

  ReservedSpace bitmap(_bitmap_size, bitmap_page_size);
  MemTracker::record_virtual_memory_type(bitmap.base(), mtGC);
  _bitmap_region = MemRegion((HeapWord*) bitmap.base(), bitmap.size() / HeapWordSize);
  _bitmap_region_special = bitmap.special();

  size_t bitmap_init_commit = _bitmap_bytes_per_slice *
                              align_up(num_committed_regions, _bitmap_regions_per_slice) / _bitmap_regions_per_slice;
  bitmap_init_commit = MIN2(_bitmap_size, bitmap_init_commit);
  if (!_bitmap_region_special) {
    os::commit_memory_or_exit((char *) _bitmap_region.start(), bitmap_init_commit, bitmap_page_size, false,
                              "Cannot commit bitmap memory");
  }

  _marking_context = new ShenandoahMarkingContext(_heap_region, _bitmap_region, _num_regions);

  if (ShenandoahVerify) {
    ReservedSpace verify_bitmap(_bitmap_size, bitmap_page_size);
    if (!verify_bitmap.special()) {
      os::commit_memory_or_exit(verify_bitmap.base(), verify_bitmap.size(), bitmap_page_size, false,
                                "Cannot commit verification bitmap memory");
    }
    MemTracker::record_virtual_memory_type(verify_bitmap.base(), mtGC);
    MemRegion verify_bitmap_region = MemRegion((HeapWord *) verify_bitmap.base(), verify_bitmap.size() / HeapWordSize);
    _verification_bit_map.initialize(_heap_region, verify_bitmap_region);
    _verifier = new ShenandoahVerifier(this, &_verification_bit_map);
  }

  // Reserve aux bitmap for use in object_iterate(). We don't commit it here.
  ReservedSpace aux_bitmap(_bitmap_size, bitmap_page_size);
  MemTracker::record_virtual_memory_type(aux_bitmap.base(), mtGC);
  _aux_bitmap_region = MemRegion((HeapWord*) aux_bitmap.base(), aux_bitmap.size() / HeapWordSize);
  _aux_bitmap_region_special = aux_bitmap.special();
  _aux_bit_map.initialize(_heap_region, _aux_bitmap_region);

  //
  // Create regions and region sets
  //

  _regions = NEW_C_HEAP_ARRAY(ShenandoahHeapRegion*, _num_regions, mtGC);
  _free_set = new ShenandoahFreeSet(this, _num_regions);
  _collection_set = new ShenandoahCollectionSet(this, (HeapWord*)sh_rs.base());

  {
    ShenandoahHeapLocker locker(lock());

    size_t size_words = ShenandoahHeapRegion::region_size_words();

    for (size_t i = 0; i < _num_regions; i++) {
      HeapWord* start = (HeapWord*)sh_rs.base() + size_words * i;
      bool is_committed = i < num_committed_regions;
      ShenandoahHeapRegion* r = new ShenandoahHeapRegion(this, start, size_words, i, is_committed);

      _marking_context->initialize_top_at_mark_start(r);
      _regions[i] = r;
      assert(!collection_set()->is_in(i), "New region should not be in collection set");
    }

    // Initialize to complete
    _marking_context->mark_complete();

    _free_set->rebuild();
  }

  if (ShenandoahAlwaysPreTouch) {
    assert(!AlwaysPreTouch, "Should have been overridden");

    // For NUMA, it is important to pre-touch the storage under bitmaps with worker threads,
    // before initialize() below zeroes it with initializing thread. For any given region,
    // we touch the region and the corresponding bitmaps from the same thread.
    ShenandoahPushWorkerScope scope(workers(), _max_workers, false);

    size_t pretouch_heap_page_size = heap_page_size;
    size_t pretouch_bitmap_page_size = bitmap_page_size;

#ifdef LINUX
    // UseTransparentHugePages would madvise that backing memory can be coalesced into huge
    // pages. But, the kernel needs to know that every small page is used, in order to coalesce
    // them into huge one. Therefore, we need to pretouch with smaller pages.
    if (UseTransparentHugePages) {
      pretouch_heap_page_size = (size_t)os::vm_page_size();
      pretouch_bitmap_page_size = (size_t)os::vm_page_size();
    }
#endif

    // OS memory managers may want to coalesce back-to-back pages. Make their jobs
    // simpler by pre-touching continuous spaces (heap and bitmap) separately.

    log_info(gc, init)("Pretouch bitmap: " SIZE_FORMAT " regions, " SIZE_FORMAT " bytes page",
                       _num_regions, pretouch_bitmap_page_size);
    ShenandoahPretouchBitmapTask bcl(bitmap.base(), _bitmap_size, pretouch_bitmap_page_size);
    _workers->run_task(&bcl);

    log_info(gc, init)("Pretouch heap: " SIZE_FORMAT " regions, " SIZE_FORMAT " bytes page",
                       _num_regions, pretouch_heap_page_size);
    ShenandoahPretouchHeapTask hcl(pretouch_heap_page_size);
    _workers->run_task(&hcl);
  }

  //
  // Initialize the rest of GC subsystems
  //

  _liveness_cache = NEW_C_HEAP_ARRAY(jushort*, _max_workers, mtGC);
  for (uint worker = 0; worker < _max_workers; worker++) {
    _liveness_cache[worker] = NEW_C_HEAP_ARRAY(jushort, _num_regions, mtGC);
    Copy::fill_to_bytes(_liveness_cache[worker], _num_regions * sizeof(jushort));
  }

  // The call below uses stuff (the SATB* things) that are in G1, but probably
  // belong into a shared location.
  ShenandoahBarrierSet::satb_mark_queue_set().initialize(this,
                                                         SATB_Q_CBL_mon,
                                                         20 /* G1SATBProcessCompletedThreshold */,
                                                         60 /* G1SATBBufferEnqueueingThresholdPercent */);

  _monitoring_support = new ShenandoahMonitoringSupport(this);
  _phase_timings = new ShenandoahPhaseTimings();
  ShenandoahStringDedup::initialize();
  ShenandoahCodeRoots::initialize();

  if (ShenandoahAllocationTrace) {
    _alloc_tracker = new ShenandoahAllocTracker();
  }

  if (ShenandoahPacing) {
    _pacer = new ShenandoahPacer(this);
    _pacer->setup_for_idle();
  } else {
    _pacer = NULL;
  }

  _traversal_gc = heuristics()->can_do_traversal_gc() ?
                  new ShenandoahTraversalGC(this, _num_regions) :
                  NULL;

  _control_thread = new ShenandoahControlThread();

  log_info(gc, init)("Initialize Shenandoah heap with initial size " SIZE_FORMAT "%s",
                     byte_size_in_proper_unit(_initial_size), proper_unit_for_byte_size(_initial_size));

  log_info(gc, init)("Safepointing mechanism: %s",
                     SafepointMechanism::uses_thread_local_poll() ? "thread-local poll" :
                     (SafepointMechanism::uses_global_page_poll() ? "global-page poll" : "unknown"));

  return JNI_OK;
}

void ShenandoahHeap::initialize_heuristics() {
  if (ShenandoahGCHeuristics != NULL) {
    if (strcmp(ShenandoahGCHeuristics, "aggressive") == 0) {
      _heuristics = new ShenandoahAggressiveHeuristics();
    } else if (strcmp(ShenandoahGCHeuristics, "static") == 0) {
      _heuristics = new ShenandoahStaticHeuristics();
    } else if (strcmp(ShenandoahGCHeuristics, "adaptive") == 0) {
      _heuristics = new ShenandoahAdaptiveHeuristics();
    } else if (strcmp(ShenandoahGCHeuristics, "passive") == 0) {
      _heuristics = new ShenandoahPassiveHeuristics();
    } else if (strcmp(ShenandoahGCHeuristics, "compact") == 0) {
      _heuristics = new ShenandoahCompactHeuristics();
    } else if (strcmp(ShenandoahGCHeuristics, "traversal") == 0) {
      _heuristics = new ShenandoahTraversalHeuristics();
    } else {
      vm_exit_during_initialization("Unknown -XX:ShenandoahGCHeuristics option");
    }

    if (_heuristics->is_diagnostic() && !UnlockDiagnosticVMOptions) {
      vm_exit_during_initialization(
              err_msg("Heuristics \"%s\" is diagnostic, and must be enabled via -XX:+UnlockDiagnosticVMOptions.",
                      _heuristics->name()));
    }
    if (_heuristics->is_experimental() && !UnlockExperimentalVMOptions) {
      vm_exit_during_initialization(
              err_msg("Heuristics \"%s\" is experimental, and must be enabled via -XX:+UnlockExperimentalVMOptions.",
                      _heuristics->name()));
    }
    log_info(gc, init)("Shenandoah heuristics: %s",
                       _heuristics->name());
  } else {
      ShouldNotReachHere();
  }

}

#ifdef _MSC_VER
#pragma warning( push )
#pragma warning( disable:4355 ) // 'this' : used in base member initializer list
#endif

ShenandoahHeap::ShenandoahHeap(ShenandoahCollectorPolicy* policy) :
  CollectedHeap(),
  _initial_size(0),
  _used(0),
  _committed(0),
  _bytes_allocated_since_gc_start(0),
  _max_workers(MAX2(ConcGCThreads, ParallelGCThreads)),
  _workers(NULL),
  _safepoint_workers(NULL),
  _heap_region_special(false),
  _num_regions(0),
  _regions(NULL),
  _update_refs_iterator(this),
  _control_thread(NULL),
  _shenandoah_policy(policy),
  _heuristics(NULL),
  _free_set(NULL),
  _scm(new ShenandoahConcurrentMark()),
  _traversal_gc(NULL),
  _full_gc(new ShenandoahMarkCompact()),
  _pacer(NULL),
  _verifier(NULL),
  _alloc_tracker(NULL),
  _phase_timings(NULL),
  _monitoring_support(NULL),
  _memory_pool(NULL),
  _stw_memory_manager("Shenandoah Pauses", "end of GC pause"),
  _cycle_memory_manager("Shenandoah Cycles", "end of GC cycle"),
  _gc_timer(new (ResourceObj::C_HEAP, mtGC) ConcurrentGCTimer()),
  _soft_ref_policy(),
  _log_min_obj_alignment_in_bytes(LogMinObjAlignmentInBytes),
  _ref_processor(NULL),
  _marking_context(NULL),
  _bitmap_size(0),
  _bitmap_regions_per_slice(0),
  _bitmap_bytes_per_slice(0),
  _bitmap_region_special(false),
  _aux_bitmap_region_special(false),
  _liveness_cache(NULL),
  _collection_set(NULL)
{
  log_info(gc, init)("GC threads: " UINT32_FORMAT " parallel, " UINT32_FORMAT " concurrent", ParallelGCThreads, ConcGCThreads);
  log_info(gc, init)("Reference processing: %s", ParallelRefProcEnabled ? "parallel" : "serial");

  BarrierSet::set_barrier_set(new ShenandoahBarrierSet(this));

  _max_workers = MAX2(_max_workers, 1U);
  _workers = new ShenandoahWorkGang("Shenandoah GC Threads", _max_workers,
                            /* are_GC_task_threads */true,
                            /* are_ConcurrentGC_threads */false);
  if (_workers == NULL) {
    vm_exit_during_initialization("Failed necessary allocation.");
  } else {
    _workers->initialize_workers();
  }

  if (ShenandoahParallelSafepointThreads > 1) {
    _safepoint_workers = new ShenandoahWorkGang("Safepoint Cleanup Thread",
                                                ShenandoahParallelSafepointThreads,
                                                false, false);
    _safepoint_workers->initialize_workers();
  }
}

#ifdef _MSC_VER
#pragma warning( pop )
#endif

class ShenandoahResetBitmapTask : public AbstractGangTask {
private:
  ShenandoahRegionIterator _regions;

public:
  ShenandoahResetBitmapTask() :
    AbstractGangTask("Parallel Reset Bitmap Task") {}

  void work(uint worker_id) {
    ShenandoahHeapRegion* region = _regions.next();
    ShenandoahHeap* heap = ShenandoahHeap::heap();
    ShenandoahMarkingContext* const ctx = heap->marking_context();
    while (region != NULL) {
      if (heap->is_bitmap_slice_committed(region)) {
        ctx->clear_bitmap(region);
      }
      region = _regions.next();
    }
  }
};

void ShenandoahHeap::reset_mark_bitmap() {
  assert_gc_workers(_workers->active_workers());
  mark_incomplete_marking_context();

  ShenandoahResetBitmapTask task;
  _workers->run_task(&task);
}

void ShenandoahHeap::print_on(outputStream* st) const {
  st->print_cr("Shenandoah Heap");
  st->print_cr(" " SIZE_FORMAT "K total, " SIZE_FORMAT "K committed, " SIZE_FORMAT "K used",
               max_capacity() / K, committed() / K, used() / K);
  st->print_cr(" " SIZE_FORMAT " x " SIZE_FORMAT"K regions",
               num_regions(), ShenandoahHeapRegion::region_size_bytes() / K);

  st->print("Status: ");
  if (has_forwarded_objects())               st->print("has forwarded objects, ");
  if (is_concurrent_mark_in_progress())      st->print("marking, ");
  if (is_evacuation_in_progress())           st->print("evacuating, ");
  if (is_update_refs_in_progress())          st->print("updating refs, ");
  if (is_concurrent_traversal_in_progress()) st->print("traversal, ");
  if (is_degenerated_gc_in_progress())       st->print("degenerated gc, ");
  if (is_full_gc_in_progress())              st->print("full gc, ");
  if (is_full_gc_move_in_progress())         st->print("full gc move, ");

  if (cancelled_gc()) {
    st->print("cancelled");
  } else {
    st->print("not cancelled");
  }
  st->cr();

  st->print_cr("Reserved region:");
  st->print_cr(" - [" PTR_FORMAT ", " PTR_FORMAT ") ",
               p2i(reserved_region().start()),
               p2i(reserved_region().end()));

  st->cr();
  MetaspaceUtils::print_on(st);

  if (Verbose) {
    print_heap_regions_on(st);
  }
}

class ShenandoahInitWorkerGCLABClosure : public ThreadClosure {
public:
  void do_thread(Thread* thread) {
    assert(thread != NULL, "Sanity");
    assert(thread->is_Worker_thread(), "Only worker thread expected");
    ShenandoahThreadLocalData::initialize_gclab(thread);
  }
};

void ShenandoahHeap::post_initialize() {
  CollectedHeap::post_initialize();
  MutexLocker ml(Threads_lock);

  ShenandoahInitWorkerGCLABClosure init_gclabs;
  _workers->threads_do(&init_gclabs);

  // gclab can not be initialized early during VM startup, as it can not determinate its max_size.
  // Now, we will let WorkGang to initialize gclab when new worker is created.
  _workers->set_initialize_gclab();

  _scm->initialize(_max_workers);
  _full_gc->initialize(_gc_timer);

  ref_processing_init();

  _heuristics->initialize();
}

size_t ShenandoahHeap::used() const {
  return OrderAccess::load_acquire(&_used);
}

size_t ShenandoahHeap::committed() const {
  OrderAccess::acquire();
  return _committed;
}

void ShenandoahHeap::increase_committed(size_t bytes) {
  assert_heaplock_or_safepoint();
  _committed += bytes;
}

void ShenandoahHeap::decrease_committed(size_t bytes) {
  assert_heaplock_or_safepoint();
  _committed -= bytes;
}

void ShenandoahHeap::increase_used(size_t bytes) {
  Atomic::add(bytes, &_used);
}

void ShenandoahHeap::set_used(size_t bytes) {
  OrderAccess::release_store_fence(&_used, bytes);
}

void ShenandoahHeap::decrease_used(size_t bytes) {
  assert(used() >= bytes, "never decrease heap size by more than we've left");
  Atomic::sub(bytes, &_used);
}

void ShenandoahHeap::increase_allocated(size_t bytes) {
  Atomic::add(bytes, &_bytes_allocated_since_gc_start);
}

void ShenandoahHeap::notify_mutator_alloc_words(size_t words, bool waste) {
  size_t bytes = words * HeapWordSize;
  if (!waste) {
    increase_used(bytes);
  }
  increase_allocated(bytes);
  if (ShenandoahPacing) {
    control_thread()->pacing_notify_alloc(words);
    if (waste) {
      pacer()->claim_for_alloc(words, true);
    }
  }
}

size_t ShenandoahHeap::capacity() const {
  return committed();
}

size_t ShenandoahHeap::max_capacity() const {
  return _num_regions * ShenandoahHeapRegion::region_size_bytes();
}

size_t ShenandoahHeap::initial_capacity() const {
  return _initial_size;
}

bool ShenandoahHeap::is_in(const void* p) const {
  HeapWord* heap_base = (HeapWord*) base();
  HeapWord* last_region_end = heap_base + ShenandoahHeapRegion::region_size_words() * num_regions();
  return p >= heap_base && p < last_region_end;
}

void ShenandoahHeap::op_uncommit(double shrink_before) {
  assert (ShenandoahUncommit, "should be enabled");

  size_t count = 0;
  for (size_t i = 0; i < num_regions(); i++) {
    ShenandoahHeapRegion* r = get_region(i);
    if (r->is_empty_committed() && (r->empty_time() < shrink_before)) {
      ShenandoahHeapLocker locker(lock());
      if (r->is_empty_committed()) {
        r->make_uncommitted();
        count++;
      }
    }
    SpinPause(); // allow allocators to take the lock
  }

  if (count > 0) {
    control_thread()->notify_heap_changed();
  }
}

HeapWord* ShenandoahHeap::allocate_from_gclab_slow(Thread* thread, size_t size) {
  // New object should fit the GCLAB size
  size_t min_size = MAX2(size, PLAB::min_size());

  // Figure out size of new GCLAB, looking back at heuristics. Expand aggressively.
  size_t new_size = ShenandoahThreadLocalData::gclab_size(thread) * 2;
  new_size = MIN2(new_size, PLAB::max_size());
  new_size = MAX2(new_size, PLAB::min_size());

  // Record new heuristic value even if we take any shortcut. This captures
  // the case when moderately-sized objects always take a shortcut. At some point,
  // heuristics should catch up with them.
  ShenandoahThreadLocalData::set_gclab_size(thread, new_size);

  if (new_size < size) {
    // New size still does not fit the object. Fall back to shared allocation.
    // This avoids retiring perfectly good GCLABs, when we encounter a large object.
    return NULL;
  }

  // Retire current GCLAB, and allocate a new one.
  PLAB* gclab = ShenandoahThreadLocalData::gclab(thread);
  gclab->retire();

  size_t actual_size = 0;
  HeapWord* gclab_buf = allocate_new_gclab(min_size, new_size, &actual_size);
  if (gclab_buf == NULL) {
    return NULL;
  }

  assert (size <= actual_size, "allocation should fit");

  if (ZeroTLAB) {
    // ..and clear it.
    Copy::zero_to_words(gclab_buf, actual_size);
  } else {
    // ...and zap just allocated object.
#ifdef ASSERT
    // Skip mangling the space corresponding to the object header to
    // ensure that the returned space is not considered parsable by
    // any concurrent GC thread.
    size_t hdr_size = oopDesc::header_size();
    Copy::fill_to_words(gclab_buf + hdr_size, actual_size - hdr_size, badHeapWordVal);
#endif // ASSERT
  }
  gclab->set_buf(gclab_buf, actual_size);
  return gclab->allocate(size);
}

HeapWord* ShenandoahHeap::allocate_new_tlab(size_t min_size,
                                            size_t requested_size,
                                            size_t* actual_size) {
  ShenandoahAllocRequest req = ShenandoahAllocRequest::for_tlab(min_size, requested_size);
  HeapWord* res = allocate_memory(req);
  if (res != NULL) {
    *actual_size = req.actual_size();
  } else {
    *actual_size = 0;
  }
  return res;
}

HeapWord* ShenandoahHeap::allocate_new_gclab(size_t min_size,
                                             size_t word_size,
                                             size_t* actual_size) {
  ShenandoahAllocRequest req = ShenandoahAllocRequest::for_gclab(min_size, word_size);
  HeapWord* res = allocate_memory(req);
  if (res != NULL) {
    *actual_size = req.actual_size();
  } else {
    *actual_size = 0;
  }
  return res;
}

ShenandoahHeap* ShenandoahHeap::heap() {
  CollectedHeap* heap = Universe::heap();
  assert(heap != NULL, "Unitialized access to ShenandoahHeap::heap()");
  assert(heap->kind() == CollectedHeap::Shenandoah, "not a shenandoah heap");
  return (ShenandoahHeap*) heap;
}

ShenandoahHeap* ShenandoahHeap::heap_no_check() {
  CollectedHeap* heap = Universe::heap();
  return (ShenandoahHeap*) heap;
}

HeapWord* ShenandoahHeap::allocate_memory(ShenandoahAllocRequest& req) {
  ShenandoahAllocTrace trace_alloc(req.size(), req.type());

  intptr_t pacer_epoch = 0;
  bool in_new_region = false;
  HeapWord* result = NULL;

  if (req.is_mutator_alloc()) {
    if (ShenandoahPacing) {
      pacer()->pace_for_alloc(req.size());
      pacer_epoch = pacer()->epoch();
    }

    if (!ShenandoahAllocFailureALot || !should_inject_alloc_failure()) {
      result = allocate_memory_under_lock(req, in_new_region);
    }

    // Allocation failed, block until control thread reacted, then retry allocation.
    //
    // It might happen that one of the threads requesting allocation would unblock
    // way later after GC happened, only to fail the second allocation, because
    // other threads have already depleted the free storage. In this case, a better
    // strategy is to try again, as long as GC makes progress.
    //
    // Then, we need to make sure the allocation was retried after at least one
    // Full GC, which means we want to try more than ShenandoahFullGCThreshold times.

    size_t tries = 0;

    while (result == NULL && _progress_last_gc.is_set()) {
      tries++;
      control_thread()->handle_alloc_failure(req.size());
      result = allocate_memory_under_lock(req, in_new_region);
    }

    while (result == NULL && tries <= ShenandoahFullGCThreshold) {
      tries++;
      control_thread()->handle_alloc_failure(req.size());
      result = allocate_memory_under_lock(req, in_new_region);
    }

  } else {
    assert(req.is_gc_alloc(), "Can only accept GC allocs here");
    result = allocate_memory_under_lock(req, in_new_region);
    // Do not call handle_alloc_failure() here, because we cannot block.
    // The allocation failure would be handled by the LRB slowpath with handle_alloc_failure_evac().
  }

  if (in_new_region) {
    control_thread()->notify_heap_changed();
  }

  if (result != NULL) {
    size_t requested = req.size();
    size_t actual = req.actual_size();

    assert (req.is_lab_alloc() || (requested == actual),
            "Only LAB allocations are elastic: %s, requested = " SIZE_FORMAT ", actual = " SIZE_FORMAT,
            ShenandoahAllocRequest::alloc_type_to_string(req.type()), requested, actual);

    if (req.is_mutator_alloc()) {
      notify_mutator_alloc_words(actual, false);

      // If we requested more than we were granted, give the rest back to pacer.
      // This only matters if we are in the same pacing epoch: do not try to unpace
      // over the budget for the other phase.
      if (ShenandoahPacing && (pacer_epoch > 0) && (requested > actual)) {
        pacer()->unpace_for_alloc(pacer_epoch, requested - actual);
      }
    } else {
      increase_used(actual*HeapWordSize);
    }
  }

  return result;
}

HeapWord* ShenandoahHeap::allocate_memory_under_lock(ShenandoahAllocRequest& req, bool& in_new_region) {
  ShenandoahHeapLocker locker(lock());
  return _free_set->allocate(req, in_new_region);
}

class ShenandoahMemAllocator : public MemAllocator {
private:
  MemAllocator& _initializer;
public:
  ShenandoahMemAllocator(MemAllocator& initializer, Klass* klass, size_t word_size, Thread* thread) :
  MemAllocator(klass, word_size + ShenandoahBrooksPointer::word_size(), thread),
    _initializer(initializer) {}

protected:
  virtual HeapWord* mem_allocate(Allocation& allocation) const {
    HeapWord* result = MemAllocator::mem_allocate(allocation);
    // Initialize brooks-pointer
    if (result != NULL) {
      result += ShenandoahBrooksPointer::word_size();
      ShenandoahBrooksPointer::initialize(oop(result));
      assert(! ShenandoahHeap::heap()->in_collection_set(result), "never allocate in targetted region");
    }
    return result;
  }

  virtual oop initialize(HeapWord* mem) const {
     return _initializer.initialize(mem);
  }
};

oop ShenandoahHeap::obj_allocate(Klass* klass, int size, TRAPS) {
  ObjAllocator initializer(klass, size, THREAD);
  ShenandoahMemAllocator allocator(initializer, klass, size, THREAD);
  return allocator.allocate();
}

oop ShenandoahHeap::array_allocate(Klass* klass, int size, int length, bool do_zero, TRAPS) {
  ObjArrayAllocator initializer(klass, size, length, do_zero, THREAD);
  ShenandoahMemAllocator allocator(initializer, klass, size, THREAD);
  return allocator.allocate();
}

oop ShenandoahHeap::class_allocate(Klass* klass, int size, TRAPS) {
  ClassAllocator initializer(klass, size, THREAD);
  ShenandoahMemAllocator allocator(initializer, klass, size, THREAD);
  return allocator.allocate();
}

HeapWord* ShenandoahHeap::mem_allocate(size_t size,
                                        bool*  gc_overhead_limit_was_exceeded) {
  ShenandoahAllocRequest req = ShenandoahAllocRequest::for_shared(size);
  return allocate_memory(req);
}

MetaWord* ShenandoahHeap::satisfy_failed_metadata_allocation(ClassLoaderData* loader_data,
                                                             size_t size,
                                                             Metaspace::MetadataType mdtype) {
  MetaWord* result;

  // Inform metaspace OOM to GC heuristics if class unloading is possible.
  if (heuristics()->can_unload_classes()) {
    ShenandoahHeuristics* h = heuristics();
    h->record_metaspace_oom();
  }

  // Expand and retry allocation
  result = loader_data->metaspace_non_null()->expand_and_allocate(size, mdtype);
  if (result != NULL) {
    return result;
  }

  // Start full GC
  collect(GCCause::_metadata_GC_clear_soft_refs);

  // Retry allocation
  result = loader_data->metaspace_non_null()->allocate(size, mdtype);
  if (result != NULL) {
    return result;
  }

  // Expand and retry allocation
  result = loader_data->metaspace_non_null()->expand_and_allocate(size, mdtype);
  if (result != NULL) {
    return result;
  }

  // Out of memory
  return NULL;
}

void ShenandoahHeap::fill_with_dummy_object(HeapWord* start, HeapWord* end, bool zap) {
  HeapWord* obj = tlab_post_allocation_setup(start);
  CollectedHeap::fill_with_object(obj, end);
}

size_t ShenandoahHeap::min_dummy_object_size() const {
  return CollectedHeap::min_dummy_object_size() + ShenandoahBrooksPointer::word_size();
}

class ShenandoahEvacuateUpdateRootsClosure: public BasicOopIterateClosure {
private:
  ShenandoahHeap* _heap;
  Thread* _thread;
public:
  ShenandoahEvacuateUpdateRootsClosure() :
    _heap(ShenandoahHeap::heap()), _thread(Thread::current()) {
  }

private:
  template <class T>
  void do_oop_work(T* p) {
    assert(_heap->is_evacuation_in_progress(), "Only do this when evacuation is in progress");

    T o = RawAccess<>::oop_load(p);
    if (! CompressedOops::is_null(o)) {
      oop obj = CompressedOops::decode_not_null(o);
      if (_heap->in_collection_set(obj)) {
        shenandoah_assert_marked(p, obj);
        oop resolved = ShenandoahBarrierSet::resolve_forwarded_not_null(obj);
        if (oopDesc::equals_raw(resolved, obj)) {
          resolved = _heap->evacuate_object(obj, _thread);
        }
        RawAccess<IS_NOT_NULL>::oop_store(p, resolved);
      }
    }
  }

public:
  void do_oop(oop* p) {
    do_oop_work(p);
  }
  void do_oop(narrowOop* p) {
    do_oop_work(p);
  }
};

class ShenandoahConcurrentEvacuateRegionObjectClosure : public ObjectClosure {
private:
  ShenandoahHeap* const _heap;
  Thread* const _thread;
public:
  ShenandoahConcurrentEvacuateRegionObjectClosure(ShenandoahHeap* heap) :
    _heap(heap), _thread(Thread::current()) {}

  void do_object(oop p) {
    shenandoah_assert_marked(NULL, p);
    if (oopDesc::equals_raw(p, ShenandoahBarrierSet::resolve_forwarded_not_null(p))) {
      _heap->evacuate_object(p, _thread);
    }
  }
};

class ShenandoahEvacuationTask : public AbstractGangTask {
private:
  ShenandoahHeap* const _sh;
  ShenandoahCollectionSet* const _cs;
  bool _concurrent;
public:
  ShenandoahEvacuationTask(ShenandoahHeap* sh,
                           ShenandoahCollectionSet* cs,
                           bool concurrent) :
    AbstractGangTask("Parallel Evacuation Task"),
    _sh(sh),
    _cs(cs),
    _concurrent(concurrent)
  {}

  void work(uint worker_id) {
    if (_concurrent) {
      ShenandoahConcurrentWorkerSession worker_session(worker_id);
      ShenandoahSuspendibleThreadSetJoiner stsj(ShenandoahSuspendibleWorkers);
      ShenandoahEvacOOMScope oom_evac_scope;
      do_work();
    } else {
      ShenandoahParallelWorkerSession worker_session(worker_id);
      ShenandoahEvacOOMScope oom_evac_scope;
      do_work();
    }
  }

private:
  void do_work() {
    ShenandoahConcurrentEvacuateRegionObjectClosure cl(_sh);
    ShenandoahHeapRegion* r;
    while ((r =_cs->claim_next()) != NULL) {
      assert(r->has_live(), "all-garbage regions are reclaimed early");
      _sh->marked_object_iterate(r, &cl);

      if (ShenandoahPacing) {
        _sh->pacer()->report_evac(r->used() >> LogHeapWordSize);
      }

      if (_sh->check_cancelled_gc_and_yield(_concurrent)) {
        break;
      }
    }
  }
};

void ShenandoahHeap::trash_cset_regions() {
  ShenandoahHeapLocker locker(lock());

  ShenandoahCollectionSet* set = collection_set();
  ShenandoahHeapRegion* r;
  set->clear_current_index();
  while ((r = set->next()) != NULL) {
    r->make_trash();
  }
  collection_set()->clear();
}

void ShenandoahHeap::print_heap_regions_on(outputStream* st) const {
  st->print_cr("Heap Regions:");
  st->print_cr("EU=empty-uncommitted, EC=empty-committed, R=regular, H=humongous start, HC=humongous continuation, CS=collection set, T=trash, P=pinned");
  st->print_cr("BTE=bottom/top/end, U=used, T=TLAB allocs, G=GCLAB allocs, S=shared allocs, L=live data");
  st->print_cr("R=root, CP=critical pins, TAMS=top-at-mark-start (previous, next)");
  st->print_cr("SN=alloc sequence numbers (first mutator, last mutator, first gc, last gc)");

  for (size_t i = 0; i < num_regions(); i++) {
    get_region(i)->print_on(st);
  }
}

void ShenandoahHeap::trash_humongous_region_at(ShenandoahHeapRegion* start) {
  assert(start->is_humongous_start(), "reclaim regions starting with the first one");

  oop humongous_obj = oop(start->bottom() + ShenandoahBrooksPointer::word_size());
  size_t size = humongous_obj->size() + ShenandoahBrooksPointer::word_size();
  size_t required_regions = ShenandoahHeapRegion::required_regions(size * HeapWordSize);
  size_t index = start->region_number() + required_regions - 1;

  assert(!start->has_live(), "liveness must be zero");

  for(size_t i = 0; i < required_regions; i++) {
    // Reclaim from tail. Otherwise, assertion fails when printing region to trace log,
    // as it expects that every region belongs to a humongous region starting with a humongous start region.
    ShenandoahHeapRegion* region = get_region(index --);

    assert(region->is_humongous(), "expect correct humongous start or continuation");
    assert(!region->is_cset(), "Humongous region should not be in collection set");

    region->make_trash_immediate();
  }
}

class ShenandoahRetireGCLABClosure : public ThreadClosure {
public:
  void do_thread(Thread* thread) {
    PLAB* gclab = ShenandoahThreadLocalData::gclab(thread);
    assert(gclab != NULL, "GCLAB should be initialized for %s", thread->name());
    gclab->retire();
  }
};

void ShenandoahHeap::make_parsable(bool retire_tlabs) {
  if (UseTLAB) {
    CollectedHeap::ensure_parsability(retire_tlabs);
  }
  ShenandoahRetireGCLABClosure cl;
  for (JavaThreadIteratorWithHandle jtiwh; JavaThread *t = jtiwh.next(); ) {
    cl.do_thread(t);
  }
  workers()->threads_do(&cl);
}

void ShenandoahHeap::resize_tlabs() {
  CollectedHeap::resize_all_tlabs();
}

class ShenandoahEvacuateUpdateRootsTask : public AbstractGangTask {
private:
  ShenandoahRootEvacuator* _rp;

public:
  ShenandoahEvacuateUpdateRootsTask(ShenandoahRootEvacuator* rp) :
    AbstractGangTask("Shenandoah evacuate and update roots"),
    _rp(rp) {}

  void work(uint worker_id) {
    ShenandoahParallelWorkerSession worker_session(worker_id);
    ShenandoahEvacOOMScope oom_evac_scope;
    ShenandoahEvacuateUpdateRootsClosure cl;
    MarkingCodeBlobClosure blobsCl(&cl, CodeBlobToOopClosure::FixRelocations);
    _rp->process_evacuate_roots(&cl, &blobsCl, worker_id);
  }
};

void ShenandoahHeap::evacuate_and_update_roots() {
#if defined(COMPILER2) || INCLUDE_JVMCI
  DerivedPointerTable::clear();
#endif
  assert(ShenandoahSafepoint::is_at_shenandoah_safepoint(), "Only iterate roots while world is stopped");

  {
    ShenandoahRootEvacuator rp(this, workers()->active_workers(), ShenandoahPhaseTimings::init_evac);
    ShenandoahEvacuateUpdateRootsTask roots_task(&rp);
    workers()->run_task(&roots_task);
  }

#if defined(COMPILER2) || INCLUDE_JVMCI
  DerivedPointerTable::update_pointers();
#endif
}

// Returns size in bytes
size_t ShenandoahHeap::unsafe_max_tlab_alloc(Thread *thread) const {
  if (ShenandoahElasticTLAB) {
    // With Elastic TLABs, return the max allowed size, and let the allocation path
    // figure out the safe size for current allocation.
    return ShenandoahHeapRegion::max_tlab_size_bytes();
  } else {
    return MIN2(_free_set->unsafe_peek_free(), ShenandoahHeapRegion::max_tlab_size_bytes());
  }
}

size_t ShenandoahHeap::max_tlab_size() const {
  // Returns size in words
  return ShenandoahHeapRegion::max_tlab_size_words();
}

class ShenandoahRetireAndResetGCLABClosure : public ThreadClosure {
public:
  void do_thread(Thread* thread) {
    PLAB* gclab = ShenandoahThreadLocalData::gclab(thread);
    gclab->retire();
    if (ShenandoahThreadLocalData::gclab_size(thread) > 0) {
      ShenandoahThreadLocalData::set_gclab_size(thread, 0);
    }
  }
};

void ShenandoahHeap::retire_and_reset_gclabs() {
  ShenandoahRetireAndResetGCLABClosure cl;
  for (JavaThreadIteratorWithHandle jtiwh; JavaThread *t = jtiwh.next(); ) {
    cl.do_thread(t);
  }
  workers()->threads_do(&cl);
}

void ShenandoahHeap::collect(GCCause::Cause cause) {
  control_thread()->request_gc(cause);
}

void ShenandoahHeap::do_full_collection(bool clear_all_soft_refs) {
  //assert(false, "Shouldn't need to do full collections");
}

CollectorPolicy* ShenandoahHeap::collector_policy() const {
  return _shenandoah_policy;
}

HeapWord* ShenandoahHeap::block_start(const void* addr) const {
  Space* sp = heap_region_containing(addr);
  if (sp != NULL) {
    return sp->block_start(addr);
  }
  return NULL;
}

bool ShenandoahHeap::block_is_obj(const HeapWord* addr) const {
  Space* sp = heap_region_containing(addr);
  return sp->block_is_obj(addr);
}

jlong ShenandoahHeap::millis_since_last_gc() {
  double v = heuristics()->time_since_last_gc() * 1000;
  assert(0 <= v && v <= max_jlong, "value should fit: %f", v);
  return (jlong)v;
}

void ShenandoahHeap::prepare_for_verify() {
  if (SafepointSynchronize::is_at_safepoint() || ! UseTLAB) {
    make_parsable(false);
  }
}

void ShenandoahHeap::print_gc_threads_on(outputStream* st) const {
  workers()->print_worker_threads_on(st);
  if (ShenandoahStringDedup::is_enabled()) {
    ShenandoahStringDedup::print_worker_threads_on(st);
  }
}

void ShenandoahHeap::gc_threads_do(ThreadClosure* tcl) const {
  workers()->threads_do(tcl);
  if (_safepoint_workers != NULL) {
    _safepoint_workers->threads_do(tcl);
  }
  if (ShenandoahStringDedup::is_enabled()) {
    ShenandoahStringDedup::threads_do(tcl);
  }
}

void ShenandoahHeap::print_tracing_info() const {
  LogTarget(Info, gc, stats) lt;
  if (lt.is_enabled()) {
    ResourceMark rm;
    LogStream ls(lt);

    phase_timings()->print_on(&ls);

    ls.cr();
    ls.cr();

    shenandoah_policy()->print_gc_stats(&ls);

    ls.cr();
    ls.cr();

    if (ShenandoahPacing) {
      pacer()->print_on(&ls);
    }

    ls.cr();
    ls.cr();

    if (ShenandoahAllocationTrace) {
      assert(alloc_tracker() != NULL, "Must be");
      alloc_tracker()->print_on(&ls);
    } else {
      ls.print_cr("  Allocation tracing is disabled, use -XX:+ShenandoahAllocationTrace to enable.");
    }
  }
}

void ShenandoahHeap::verify(VerifyOption vo) {
  if (ShenandoahSafepoint::is_at_shenandoah_safepoint()) {
    if (ShenandoahVerify) {
      verifier()->verify_generic(vo);
    } else {
      // TODO: Consider allocating verification bitmaps on demand,
      // and turn this on unconditionally.
    }
  }
}
size_t ShenandoahHeap::tlab_capacity(Thread *thr) const {
  return _free_set->capacity();
}

class ObjectIterateScanRootClosure : public BasicOopIterateClosure {
private:
  MarkBitMap* _bitmap;
  Stack<oop,mtGC>* _oop_stack;

  template <class T>
  void do_oop_work(T* p) {
    T o = RawAccess<>::oop_load(p);
    if (!CompressedOops::is_null(o)) {
      oop obj = CompressedOops::decode_not_null(o);
      obj = ShenandoahBarrierSet::resolve_forwarded_not_null(obj);
      assert(oopDesc::is_oop(obj), "must be a valid oop");
      if (!_bitmap->is_marked((HeapWord*) obj)) {
        _bitmap->mark((HeapWord*) obj);
        _oop_stack->push(obj);
      }
    }
  }
public:
  ObjectIterateScanRootClosure(MarkBitMap* bitmap, Stack<oop,mtGC>* oop_stack) :
    _bitmap(bitmap), _oop_stack(oop_stack) {}
  void do_oop(oop* p)       { do_oop_work(p); }
  void do_oop(narrowOop* p) { do_oop_work(p); }
};

/*
 * This is public API, used in preparation of object_iterate().
 * Since we don't do linear scan of heap in object_iterate() (see comment below), we don't
 * need to make the heap parsable. For Shenandoah-internal linear heap scans that we can
 * control, we call SH::make_tlabs_parsable().
 */
void ShenandoahHeap::ensure_parsability(bool retire_tlabs) {
  // No-op.
}

/*
 * Iterates objects in the heap. This is public API, used for, e.g., heap dumping.
 *
 * We cannot safely iterate objects by doing a linear scan at random points in time. Linear
 * scanning needs to deal with dead objects, which may have dead Klass* pointers (e.g.
 * calling oopDesc::size() would crash) or dangling reference fields (crashes) etc. Linear
 * scanning therefore depends on having a valid marking bitmap to support it. However, we only
 * have a valid marking bitmap after successful marking. In particular, we *don't* have a valid
 * marking bitmap during marking, after aborted marking or during/after cleanup (when we just
 * wiped the bitmap in preparation for next marking).
 *
 * For all those reasons, we implement object iteration as a single marking traversal, reporting
 * objects as we mark+traverse through the heap, starting from GC roots. JVMTI IterateThroughHeap
 * is allowed to report dead objects, but is not required to do so.
 */
void ShenandoahHeap::object_iterate(ObjectClosure* cl) {
  assert(SafepointSynchronize::is_at_safepoint(), "safe iteration is only available during safepoints");
  if (!_aux_bitmap_region_special && !os::commit_memory((char*)_aux_bitmap_region.start(), _aux_bitmap_region.byte_size(), false)) {
    log_warning(gc)("Could not commit native memory for auxiliary marking bitmap for heap iteration");
    return;
  }

  // Reset bitmap
  _aux_bit_map.clear();

  Stack<oop,mtGC> oop_stack;

  // First, we process all GC roots. This populates the work stack with initial objects.
  ShenandoahRootProcessor rp(this, 1, ShenandoahPhaseTimings::_num_phases);
  ObjectIterateScanRootClosure oops(&_aux_bit_map, &oop_stack);
  CLDToOopClosure clds(&oops, ClassLoaderData::_claim_none);
  CodeBlobToOopClosure blobs(&oops, false);
  rp.process_all_roots(&oops, &clds, &blobs, NULL, 0);

  // Work through the oop stack to traverse heap.
  while (! oop_stack.is_empty()) {
    oop obj = oop_stack.pop();
    assert(oopDesc::is_oop(obj), "must be a valid oop");
    cl->do_object(obj);
    obj->oop_iterate(&oops);
  }

  assert(oop_stack.is_empty(), "should be empty");

  if (!_aux_bitmap_region_special && !os::uncommit_memory((char*)_aux_bitmap_region.start(), _aux_bitmap_region.byte_size())) {
    log_warning(gc)("Could not uncommit native memory for auxiliary marking bitmap for heap iteration");
  }
}

void ShenandoahHeap::safe_object_iterate(ObjectClosure* cl) {
  assert(SafepointSynchronize::is_at_safepoint(), "safe iteration is only available during safepoints");
  object_iterate(cl);
}

void ShenandoahHeap::heap_region_iterate(ShenandoahHeapRegionClosure* blk) const {
  for (size_t i = 0; i < num_regions(); i++) {
    ShenandoahHeapRegion* current = get_region(i);
    blk->heap_region_do(current);
  }
}

class ShenandoahParallelHeapRegionTask : public AbstractGangTask {
private:
  ShenandoahHeap* const _heap;
  ShenandoahHeapRegionClosure* const _blk;

  DEFINE_PAD_MINUS_SIZE(0, DEFAULT_CACHE_LINE_SIZE, sizeof(volatile size_t));
  volatile size_t _index;
  DEFINE_PAD_MINUS_SIZE(1, DEFAULT_CACHE_LINE_SIZE, 0);

public:
  ShenandoahParallelHeapRegionTask(ShenandoahHeapRegionClosure* blk) :
          AbstractGangTask("Parallel Region Task"),
          _heap(ShenandoahHeap::heap()), _blk(blk), _index(0) {}

  void work(uint worker_id) {
    size_t stride = ShenandoahParallelRegionStride;

    size_t max = _heap->num_regions();
    while (_index < max) {
      size_t cur = Atomic::add(stride, &_index) - stride;
      size_t start = cur;
      size_t end = MIN2(cur + stride, max);
      if (start >= max) break;

      for (size_t i = cur; i < end; i++) {
        ShenandoahHeapRegion* current = _heap->get_region(i);
        _blk->heap_region_do(current);
      }
    }
  }
};

void ShenandoahHeap::parallel_heap_region_iterate(ShenandoahHeapRegionClosure* blk) const {
  assert(blk->is_thread_safe(), "Only thread-safe closures here");
  if (num_regions() > ShenandoahParallelRegionStride) {
    ShenandoahParallelHeapRegionTask task(blk);
    workers()->run_task(&task);
  } else {
    heap_region_iterate(blk);
  }
}

class ShenandoahClearLivenessClosure : public ShenandoahHeapRegionClosure {
private:
  ShenandoahMarkingContext* const _ctx;
public:
  ShenandoahClearLivenessClosure() : _ctx(ShenandoahHeap::heap()->marking_context()) {}

  void heap_region_do(ShenandoahHeapRegion* r) {
    if (r->is_active()) {
      r->clear_live_data();
      _ctx->capture_top_at_mark_start(r);
    } else {
      assert(!r->has_live(), "Region " SIZE_FORMAT " should have no live data", r->region_number());
      assert(_ctx->top_at_mark_start(r) == r->top(),
             "Region " SIZE_FORMAT " should already have correct TAMS", r->region_number());
    }
  }

  bool is_thread_safe() { return true; }
};

void ShenandoahHeap::op_init_mark() {
  assert(ShenandoahSafepoint::is_at_shenandoah_safepoint(), "Should be at safepoint");
  assert(Thread::current()->is_VM_thread(), "can only do this in VMThread");

  assert(marking_context()->is_bitmap_clear(), "need clear marking bitmap");
  assert(!marking_context()->is_complete(), "should not be complete");

  if (ShenandoahVerify) {
    verifier()->verify_before_concmark();
  }

  if (VerifyBeforeGC) {
    Universe::verify();
  }

  set_concurrent_mark_in_progress(true);
  // We need to reset all TLABs because we'd lose marks on all objects allocated in them.
  {
    ShenandoahGCPhase phase(ShenandoahPhaseTimings::make_parsable);
    make_parsable(true);
  }

  {
    ShenandoahGCPhase phase(ShenandoahPhaseTimings::clear_liveness);
    ShenandoahClearLivenessClosure clc;
    parallel_heap_region_iterate(&clc);
  }

  // Make above changes visible to worker threads
  OrderAccess::fence();

  concurrent_mark()->mark_roots(ShenandoahPhaseTimings::scan_roots);

  if (UseTLAB) {
    ShenandoahGCPhase phase(ShenandoahPhaseTimings::resize_tlabs);
    resize_tlabs();
  }

  if (ShenandoahPacing) {
    pacer()->setup_for_mark();
  }
}

void ShenandoahHeap::op_mark() {
  concurrent_mark()->mark_from_roots();
}

class ShenandoahCompleteLivenessClosure : public ShenandoahHeapRegionClosure {
private:
  ShenandoahMarkingContext* const _ctx;
public:
  ShenandoahCompleteLivenessClosure() : _ctx(ShenandoahHeap::heap()->complete_marking_context()) {}

  void heap_region_do(ShenandoahHeapRegion* r) {
    if (r->is_active()) {
      HeapWord *tams = _ctx->top_at_mark_start(r);
      HeapWord *top = r->top();
      if (top > tams) {
        r->increase_live_data_alloc_words(pointer_delta(top, tams));
      }
    } else {
      assert(!r->has_live(), "Region " SIZE_FORMAT " should have no live data", r->region_number());
      assert(_ctx->top_at_mark_start(r) == r->top(),
             "Region " SIZE_FORMAT " should have correct TAMS", r->region_number());
    }
  }

  bool is_thread_safe() { return true; }
};

void ShenandoahHeap::op_final_mark() {
  assert(ShenandoahSafepoint::is_at_shenandoah_safepoint(), "Should be at safepoint");

  // It is critical that we
  // evacuate roots right after finishing marking, so that we don't
  // get unmarked objects in the roots.

  if (!cancelled_gc()) {
    concurrent_mark()->finish_mark_from_roots(/* full_gc = */ false);

    if (has_forwarded_objects()) {
      concurrent_mark()->update_roots(ShenandoahPhaseTimings::update_roots);
    }

    stop_concurrent_marking();

    {
      ShenandoahGCPhase phase(ShenandoahPhaseTimings::complete_liveness);

      // All allocations past TAMS are implicitly live, adjust the region data.
      // Bitmaps/TAMS are swapped at this point, so we need to poll complete bitmap.
      ShenandoahCompleteLivenessClosure cl;
      parallel_heap_region_iterate(&cl);
    }

    {
      ShenandoahGCPhase prepare_evac(ShenandoahPhaseTimings::prepare_evac);

      make_parsable(true);

      trash_cset_regions();

      {
        ShenandoahHeapLocker locker(lock());
        _collection_set->clear();
        _free_set->clear();

        heuristics()->choose_collection_set(_collection_set);

        _free_set->rebuild();
      }
    }

    // If collection set has candidates, start evacuation.
    // Otherwise, bypass the rest of the cycle.
    if (!collection_set()->is_empty()) {
      ShenandoahGCPhase init_evac(ShenandoahPhaseTimings::init_evac);

      if (ShenandoahVerify) {
        verifier()->verify_before_evacuation();
      }

      set_evacuation_in_progress(true);
      // From here on, we need to update references.
      set_has_forwarded_objects(true);

      evacuate_and_update_roots();

      if (ShenandoahPacing) {
        pacer()->setup_for_evac();
      }

      if (ShenandoahVerify) {
        verifier()->verify_during_evacuation();
      }
    } else {
      if (ShenandoahVerify) {
        verifier()->verify_after_concmark();
      }

      if (VerifyAfterGC) {
        Universe::verify();
      }
    }

  } else {
    concurrent_mark()->cancel();
    stop_concurrent_marking();

    if (process_references()) {
      // Abandon reference processing right away: pre-cleaning must have failed.
      ReferenceProcessor *rp = ref_processor();
      rp->disable_discovery();
      rp->abandon_partial_discovery();
      rp->verify_no_references_recorded();
    }
  }
}

void ShenandoahHeap::op_final_evac() {
  assert(ShenandoahSafepoint::is_at_shenandoah_safepoint(), "Should be at safepoint");

  set_evacuation_in_progress(false);

  retire_and_reset_gclabs();

  if (ShenandoahVerify) {
    verifier()->verify_after_evacuation();
  }

  if (VerifyAfterGC) {
    Universe::verify();
  }
}

void ShenandoahHeap::op_conc_evac() {
  ShenandoahEvacuationTask task(this, _collection_set, true);
  workers()->run_task(&task);
}

void ShenandoahHeap::op_stw_evac() {
  ShenandoahEvacuationTask task(this, _collection_set, false);
  workers()->run_task(&task);
}

void ShenandoahHeap::op_updaterefs() {
  update_heap_references(true);
}

void ShenandoahHeap::op_cleanup() {
  free_set()->recycle_trash();
}

void ShenandoahHeap::op_reset() {
  reset_mark_bitmap();
}

void ShenandoahHeap::op_preclean() {
  concurrent_mark()->preclean_weak_refs();
}

void ShenandoahHeap::op_init_traversal() {
  traversal_gc()->init_traversal_collection();
}

void ShenandoahHeap::op_traversal() {
  traversal_gc()->concurrent_traversal_collection();
}

void ShenandoahHeap::op_final_traversal() {
  traversal_gc()->final_traversal_collection();
}

void ShenandoahHeap::op_full(GCCause::Cause cause) {
  ShenandoahMetricsSnapshot metrics;
  metrics.snap_before();

  full_gc()->do_it(cause);
  if (UseTLAB) {
    ShenandoahGCPhase phase(ShenandoahPhaseTimings::full_gc_resize_tlabs);
    resize_all_tlabs();
  }

  metrics.snap_after();
  metrics.print();

  if (metrics.is_good_progress("Full GC")) {
    _progress_last_gc.set();
  } else {
    // Nothing to do. Tell the allocation path that we have failed to make
    // progress, and it can finally fail.
    _progress_last_gc.unset();
  }
}

void ShenandoahHeap::op_degenerated(ShenandoahDegenPoint point) {
  // Degenerated GC is STW, but it can also fail. Current mechanics communicates
  // GC failure via cancelled_concgc() flag. So, if we detect the failure after
  // some phase, we have to upgrade the Degenerate GC to Full GC.

  clear_cancelled_gc();

  ShenandoahMetricsSnapshot metrics;
  metrics.snap_before();

  switch (point) {
    case _degenerated_traversal:
      {
        // Drop the collection set. Note: this leaves some already forwarded objects
        // behind, which may be problematic, see comments for ShenandoahEvacAssist
        // workarounds in ShenandoahTraversalHeuristics.

        ShenandoahHeapLocker locker(lock());
        collection_set()->clear_current_index();
        for (size_t i = 0; i < collection_set()->count(); i++) {
          ShenandoahHeapRegion* r = collection_set()->next();
          r->make_regular_bypass();
        }
        collection_set()->clear();
      }
      op_final_traversal();
      op_cleanup();
      return;

    // The cases below form the Duff's-like device: it describes the actual GC cycle,
    // but enters it at different points, depending on which concurrent phase had
    // degenerated.

    case _degenerated_outside_cycle:
      // We have degenerated from outside the cycle, which means something is bad with
      // the heap, most probably heavy humongous fragmentation, or we are very low on free
      // space. It makes little sense to wait for Full GC to reclaim as much as it can, when
      // we can do the most aggressive degen cycle, which includes processing references and
      // class unloading, unless those features are explicitly disabled.
      //
      // Note that we can only do this for "outside-cycle" degens, otherwise we would risk
      // changing the cycle parameters mid-cycle during concurrent -> degenerated handover.
      set_process_references(heuristics()->can_process_references());
      set_unload_classes(heuristics()->can_unload_classes());

      if (heuristics()->can_do_traversal_gc()) {
        // Not possible to degenerate from here, upgrade to Full GC right away.
        cancel_gc(GCCause::_shenandoah_upgrade_to_full_gc);
        op_degenerated_fail();
        return;
      }

      op_reset();

      op_init_mark();
      if (cancelled_gc()) {
        op_degenerated_fail();
        return;
      }

    case _degenerated_mark:
      op_final_mark();
      if (cancelled_gc()) {
        op_degenerated_fail();
        return;
      }

      op_cleanup();

    case _degenerated_evac:
      // If heuristics thinks we should do the cycle, this flag would be set,
      // and we can do evacuation. Otherwise, it would be the shortcut cycle.
      if (is_evacuation_in_progress()) {

        // Degeneration under oom-evac protocol might have left some objects in
        // collection set un-evacuated. Restart evacuation from the beginning to
        // capture all objects. For all the objects that are already evacuated,
        // it would be a simple check, which is supposed to be fast. This is also
        // safe to do even without degeneration, as CSet iterator is at beginning
        // in preparation for evacuation anyway.
        collection_set()->clear_current_index();

        op_stw_evac();
        if (cancelled_gc()) {
          op_degenerated_fail();
          return;
        }
      }

      // If heuristics thinks we should do the cycle, this flag would be set,
      // and we need to do update-refs. Otherwise, it would be the shortcut cycle.
      if (has_forwarded_objects()) {
        op_init_updaterefs();
        if (cancelled_gc()) {
          op_degenerated_fail();
          return;
        }
      }

    case _degenerated_updaterefs:
      if (has_forwarded_objects()) {
        op_final_updaterefs();
        if (cancelled_gc()) {
          op_degenerated_fail();
          return;
        }
      }

      op_cleanup();
      break;

    default:
      ShouldNotReachHere();
  }

  if (ShenandoahVerify) {
    verifier()->verify_after_degenerated();
  }

  if (VerifyAfterGC) {
    Universe::verify();
  }

  metrics.snap_after();
  metrics.print();

  // Check for futility and fail. There is no reason to do several back-to-back Degenerated cycles,
  // because that probably means the heap is overloaded and/or fragmented.
  if (!metrics.is_good_progress("Degenerated GC")) {
    _progress_last_gc.unset();
    cancel_gc(GCCause::_shenandoah_upgrade_to_full_gc);
    op_degenerated_futile();
  } else {
    _progress_last_gc.set();
  }
}

void ShenandoahHeap::op_degenerated_fail() {
  log_info(gc)("Cannot finish degeneration, upgrading to Full GC");
  shenandoah_policy()->record_degenerated_upgrade_to_full();
  op_full(GCCause::_shenandoah_upgrade_to_full_gc);
}

void ShenandoahHeap::op_degenerated_futile() {
  shenandoah_policy()->record_degenerated_upgrade_to_full();
  op_full(GCCause::_shenandoah_upgrade_to_full_gc);
}

void ShenandoahHeap::stop_concurrent_marking() {
  assert(is_concurrent_mark_in_progress(), "How else could we get here?");
  set_concurrent_mark_in_progress(false);
  if (!cancelled_gc()) {
    // If we needed to update refs, and concurrent marking has been cancelled,
    // we need to finish updating references.
    set_has_forwarded_objects(false);
    mark_complete_marking_context();
  }
}

void ShenandoahHeap::force_satb_flush_all_threads() {
  if (!is_concurrent_mark_in_progress() && !is_concurrent_traversal_in_progress()) {
    // No need to flush SATBs
    return;
  }

  for (JavaThreadIteratorWithHandle jtiwh; JavaThread *t = jtiwh.next(); ) {
    ShenandoahThreadLocalData::set_force_satb_flush(t, true);
  }
  // The threads are not "acquiring" their thread-local data, but it does not
  // hurt to "release" the updates here anyway.
  OrderAccess::fence();
}

void ShenandoahHeap::set_gc_state_all_threads(char state) {
  for (JavaThreadIteratorWithHandle jtiwh; JavaThread *t = jtiwh.next(); ) {
    ShenandoahThreadLocalData::set_gc_state(t, state);
  }
}

void ShenandoahHeap::set_gc_state_mask(uint mask, bool value) {
  assert(ShenandoahSafepoint::is_at_shenandoah_safepoint(), "Should really be Shenandoah safepoint");
  _gc_state.set_cond(mask, value);
  set_gc_state_all_threads(_gc_state.raw_value());
}

void ShenandoahHeap::set_concurrent_mark_in_progress(bool in_progress) {
  if (has_forwarded_objects()) {
    set_gc_state_mask(MARKING | UPDATEREFS, in_progress);
  } else {
    set_gc_state_mask(MARKING, in_progress);
  }
  ShenandoahBarrierSet::satb_mark_queue_set().set_active_all_threads(in_progress, !in_progress);
}

void ShenandoahHeap::set_concurrent_traversal_in_progress(bool in_progress) {
   set_gc_state_mask(TRAVERSAL | HAS_FORWARDED | UPDATEREFS, in_progress);
   ShenandoahBarrierSet::satb_mark_queue_set().set_active_all_threads(in_progress, !in_progress);
}

void ShenandoahHeap::set_evacuation_in_progress(bool in_progress) {
  assert(ShenandoahSafepoint::is_at_shenandoah_safepoint(), "Only call this at safepoint");
  set_gc_state_mask(EVACUATION, in_progress);
}

HeapWord* ShenandoahHeap::tlab_post_allocation_setup(HeapWord* obj) {
  // Initialize Brooks pointer for the next object
  HeapWord* result = obj + ShenandoahBrooksPointer::word_size();
  ShenandoahBrooksPointer::initialize(oop(result));
  return result;
}

ShenandoahForwardedIsAliveClosure::ShenandoahForwardedIsAliveClosure() :
  _mark_context(ShenandoahHeap::heap()->marking_context()) {
}

ShenandoahIsAliveClosure::ShenandoahIsAliveClosure() :
  _mark_context(ShenandoahHeap::heap()->marking_context()) {
}

bool ShenandoahForwardedIsAliveClosure::do_object_b(oop obj) {
  if (CompressedOops::is_null(obj)) {
    return false;
  }
  obj = ShenandoahBarrierSet::resolve_forwarded_not_null(obj);
  shenandoah_assert_not_forwarded_if(NULL, obj, ShenandoahHeap::heap()->is_concurrent_mark_in_progress() || ShenandoahHeap::heap()->is_concurrent_traversal_in_progress());
  return _mark_context->is_marked(obj);
}

bool ShenandoahIsAliveClosure::do_object_b(oop obj) {
  if (CompressedOops::is_null(obj)) {
    return false;
  }
  shenandoah_assert_not_forwarded(NULL, obj);
  return _mark_context->is_marked(obj);
}

void ShenandoahHeap::ref_processing_init() {
  assert(_max_workers > 0, "Sanity");

  _ref_processor =
    new ReferenceProcessor(&_subject_to_discovery,  // is_subject_to_discovery
                           ParallelRefProcEnabled,  // MT processing
                           _max_workers,            // Degree of MT processing
                           true,                    // MT discovery
                           _max_workers,            // Degree of MT discovery
                           false,                   // Reference discovery is not atomic
                           NULL,                    // No closure, should be installed before use
                           true);                   // Scale worker threads

  shenandoah_assert_rp_isalive_not_installed();
}

GCTracer* ShenandoahHeap::tracer() {
  return shenandoah_policy()->tracer();
}

size_t ShenandoahHeap::tlab_used(Thread* thread) const {
  return _free_set->used();
}

bool ShenandoahHeap::try_cancel_gc() {
  while (true) {
    jbyte prev = _cancelled_gc.cmpxchg(CANCELLED, CANCELLABLE);
    if (prev == CANCELLABLE) return true;
    else if (prev == CANCELLED) return false;
    assert(ShenandoahSuspendibleWorkers, "should not get here when not using suspendible workers");
    assert(prev == NOT_CANCELLED, "must be NOT_CANCELLED");
    {
      // We need to provide a safepoint here, otherwise we might
      // spin forever if a SP is pending.
      ThreadBlockInVM sp(JavaThread::current());
      SpinPause();
    }
  }
}

void ShenandoahHeap::cancel_gc(GCCause::Cause cause) {
  if (try_cancel_gc()) {
    FormatBuffer<> msg("Cancelling GC: %s", GCCause::to_string(cause));
    log_info(gc)("%s", msg.buffer());
    Events::log(Thread::current(), "%s", msg.buffer());
  }
}

uint ShenandoahHeap::max_workers() {
  return _max_workers;
}

void ShenandoahHeap::stop() {
  // The shutdown sequence should be able to terminate when GC is running.

  // Step 0. Notify policy to disable event recording.
  _shenandoah_policy->record_shutdown();

  // Step 1. Notify control thread that we are in shutdown.
  // Note that we cannot do that with stop(), because stop() is blocking and waits for the actual shutdown.
  // Doing stop() here would wait for the normal GC cycle to complete, never falling through to cancel below.
  control_thread()->prepare_for_graceful_shutdown();

  // Step 2. Notify GC workers that we are cancelling GC.
  cancel_gc(GCCause::_shenandoah_stop_vm);

  // Step 3. Wait until GC worker exits normally.
  control_thread()->stop();

  // Step 4. Stop String Dedup thread if it is active
  if (ShenandoahStringDedup::is_enabled()) {
    ShenandoahStringDedup::stop();
  }
}

void ShenandoahHeap::unload_classes_and_cleanup_tables(bool full_gc) {
  assert(heuristics()->can_unload_classes(), "Class unloading should be enabled");

  ShenandoahGCPhase root_phase(full_gc ?
                               ShenandoahPhaseTimings::full_gc_purge :
                               ShenandoahPhaseTimings::purge);

  ShenandoahIsAliveSelector alive;
  BoolObjectClosure* is_alive = alive.is_alive_closure();

  bool purged_class;

  // Unload classes and purge SystemDictionary.
  {
    ShenandoahGCPhase phase(full_gc ?
                            ShenandoahPhaseTimings::full_gc_purge_class_unload :
                            ShenandoahPhaseTimings::purge_class_unload);
    purged_class = SystemDictionary::do_unloading(gc_timer());
  }

  {
    ShenandoahGCPhase phase(full_gc ?
                            ShenandoahPhaseTimings::full_gc_purge_par :
                            ShenandoahPhaseTimings::purge_par);
    uint active = _workers->active_workers();
    ParallelCleaningTask unlink_task(is_alive, active, purged_class, true);
    _workers->run_task(&unlink_task);
  }

  {
    ShenandoahGCPhase phase(full_gc ?
                      ShenandoahPhaseTimings::full_gc_purge_cldg :
                      ShenandoahPhaseTimings::purge_cldg);
    ClassLoaderDataGraph::purge();
  }
}

void ShenandoahHeap::set_has_forwarded_objects(bool cond) {
  set_gc_state_mask(HAS_FORWARDED, cond);
}

void ShenandoahHeap::set_process_references(bool pr) {
  _process_references.set_cond(pr);
}

void ShenandoahHeap::set_unload_classes(bool uc) {
  _unload_classes.set_cond(uc);
}

bool ShenandoahHeap::process_references() const {
  return _process_references.is_set();
}

bool ShenandoahHeap::unload_classes() const {
  return _unload_classes.is_set();
}

address ShenandoahHeap::in_cset_fast_test_addr() {
  ShenandoahHeap* heap = ShenandoahHeap::heap();
  assert(heap->collection_set() != NULL, "Sanity");
  return (address) heap->collection_set()->biased_map_address();
}

address ShenandoahHeap::cancelled_gc_addr() {
  return (address) ShenandoahHeap::heap()->_cancelled_gc.addr_of();
}

address ShenandoahHeap::gc_state_addr() {
  return (address) ShenandoahHeap::heap()->_gc_state.addr_of();
}

size_t ShenandoahHeap::bytes_allocated_since_gc_start() {
  return OrderAccess::load_acquire(&_bytes_allocated_since_gc_start);
}

void ShenandoahHeap::reset_bytes_allocated_since_gc_start() {
  OrderAccess::release_store_fence(&_bytes_allocated_since_gc_start, (size_t)0);
}

void ShenandoahHeap::set_degenerated_gc_in_progress(bool in_progress) {
  _degenerated_gc_in_progress.set_cond(in_progress);
}

void ShenandoahHeap::set_full_gc_in_progress(bool in_progress) {
  _full_gc_in_progress.set_cond(in_progress);
}

void ShenandoahHeap::set_full_gc_move_in_progress(bool in_progress) {
  assert (is_full_gc_in_progress(), "should be");
  _full_gc_move_in_progress.set_cond(in_progress);
}

void ShenandoahHeap::set_update_refs_in_progress(bool in_progress) {
  set_gc_state_mask(UPDATEREFS, in_progress);
}

void ShenandoahHeap::register_nmethod(nmethod* nm) {
  ShenandoahCodeRoots::add_nmethod(nm);
}

void ShenandoahHeap::unregister_nmethod(nmethod* nm) {
  ShenandoahCodeRoots::remove_nmethod(nm);
}

oop ShenandoahHeap::pin_object(JavaThread* thr, oop o) {
  ShenandoahHeapLocker locker(lock());
  heap_region_containing(o)->make_pinned();
  return o;
}

void ShenandoahHeap::unpin_object(JavaThread* thr, oop o) {
  ShenandoahHeapLocker locker(lock());
  heap_region_containing(o)->make_unpinned();
}

GCTimer* ShenandoahHeap::gc_timer() const {
  return _gc_timer;
}

#ifdef ASSERT
void ShenandoahHeap::assert_gc_workers(uint nworkers) {
  assert(nworkers > 0 && nworkers <= max_workers(), "Sanity");

  if (ShenandoahSafepoint::is_at_shenandoah_safepoint()) {
    if (UseDynamicNumberOfGCThreads ||
        (FLAG_IS_DEFAULT(ParallelGCThreads) && ForceDynamicNumberOfGCThreads)) {
      assert(nworkers <= ParallelGCThreads, "Cannot use more than it has");
    } else {
      // Use ParallelGCThreads inside safepoints
      assert(nworkers == ParallelGCThreads, "Use ParalleGCThreads within safepoints");
    }
  } else {
    if (UseDynamicNumberOfGCThreads ||
        (FLAG_IS_DEFAULT(ConcGCThreads) && ForceDynamicNumberOfGCThreads)) {
      assert(nworkers <= ConcGCThreads, "Cannot use more than it has");
    } else {
      // Use ConcGCThreads outside safepoints
      assert(nworkers == ConcGCThreads, "Use ConcGCThreads outside safepoints");
    }
  }
}
#endif

ShenandoahVerifier* ShenandoahHeap::verifier() {
  guarantee(ShenandoahVerify, "Should be enabled");
  assert (_verifier != NULL, "sanity");
  return _verifier;
}

template<class T>
class ShenandoahUpdateHeapRefsTask : public AbstractGangTask {
private:
  T cl;
  ShenandoahHeap* _heap;
  ShenandoahRegionIterator* _regions;
  bool _concurrent;
public:
  ShenandoahUpdateHeapRefsTask(ShenandoahRegionIterator* regions, bool concurrent) :
    AbstractGangTask("Concurrent Update References Task"),
    cl(T()),
    _heap(ShenandoahHeap::heap()),
    _regions(regions),
    _concurrent(concurrent) {
  }

  void work(uint worker_id) {
    if (_concurrent) {
      ShenandoahConcurrentWorkerSession worker_session(worker_id);
      ShenandoahSuspendibleThreadSetJoiner stsj(ShenandoahSuspendibleWorkers);
      do_work();
    } else {
      ShenandoahParallelWorkerSession worker_session(worker_id);
      do_work();
    }
  }

private:
  void do_work() {
    ShenandoahHeapRegion* r = _regions->next();
    ShenandoahMarkingContext* const ctx = _heap->complete_marking_context();
    while (r != NULL) {
      HeapWord* top_at_start_ur = r->concurrent_iteration_safe_limit();
      assert (top_at_start_ur >= r->bottom(), "sanity");
      if (r->is_active() && !r->is_cset()) {
        _heap->marked_object_oop_iterate(r, &cl, top_at_start_ur);
      }
      if (ShenandoahPacing) {
        _heap->pacer()->report_updaterefs(pointer_delta(top_at_start_ur, r->bottom()));
      }
      if (_heap->check_cancelled_gc_and_yield(_concurrent)) {
        return;
      }
      r = _regions->next();
    }
  }
};

void ShenandoahHeap::update_heap_references(bool concurrent) {
  ShenandoahUpdateHeapRefsTask<ShenandoahUpdateHeapRefsClosure> task(&_update_refs_iterator, concurrent);
  workers()->run_task(&task);
}

void ShenandoahHeap::op_init_updaterefs() {
  assert(ShenandoahSafepoint::is_at_shenandoah_safepoint(), "must be at safepoint");

  set_evacuation_in_progress(false);

  retire_and_reset_gclabs();

  if (ShenandoahVerify) {
    verifier()->verify_before_updaterefs();
  }

  set_update_refs_in_progress(true);
  make_parsable(true);
  for (uint i = 0; i < num_regions(); i++) {
    ShenandoahHeapRegion* r = get_region(i);
    r->set_concurrent_iteration_safe_limit(r->top());
  }

  // Reset iterator.
  _update_refs_iterator.reset();

  if (ShenandoahPacing) {
    pacer()->setup_for_updaterefs();
  }
}

void ShenandoahHeap::op_final_updaterefs() {
  assert(ShenandoahSafepoint::is_at_shenandoah_safepoint(), "must be at safepoint");

  // Check if there is left-over work, and finish it
  if (_update_refs_iterator.has_next()) {
    ShenandoahGCPhase final_work(ShenandoahPhaseTimings::final_update_refs_finish_work);

    // Finish updating references where we left off.
    clear_cancelled_gc();
    update_heap_references(false);
  }

  // Clear cancelled GC, if set. On cancellation path, the block before would handle
  // everything. On degenerated paths, cancelled gc would not be set anyway.
  if (cancelled_gc()) {
    clear_cancelled_gc();
  }
  assert(!cancelled_gc(), "Should have been done right before");

  concurrent_mark()->update_roots(is_degenerated_gc_in_progress() ?
                                 ShenandoahPhaseTimings::degen_gc_update_roots:
                                 ShenandoahPhaseTimings::final_update_refs_roots);

  ShenandoahGCPhase final_update_refs(ShenandoahPhaseTimings::final_update_refs_recycle);

  trash_cset_regions();
  set_has_forwarded_objects(false);
  set_update_refs_in_progress(false);

  if (ShenandoahVerify) {
    verifier()->verify_after_updaterefs();
  }

  if (VerifyAfterGC) {
    Universe::verify();
  }

  {
    ShenandoahHeapLocker locker(lock());
    _free_set->rebuild();
  }
}

#ifdef ASSERT
void ShenandoahHeap::assert_heaplock_owned_by_current_thread() {
  _lock.assert_owned_by_current_thread();
}

void ShenandoahHeap::assert_heaplock_not_owned_by_current_thread() {
  _lock.assert_not_owned_by_current_thread();
}

void ShenandoahHeap::assert_heaplock_or_safepoint() {
  _lock.assert_owned_by_current_thread_or_safepoint();
}
#endif

void ShenandoahHeap::print_extended_on(outputStream *st) const {
  print_on(st);
  print_heap_regions_on(st);
}

bool ShenandoahHeap::is_bitmap_slice_committed(ShenandoahHeapRegion* r, bool skip_self) {
  size_t slice = r->region_number() / _bitmap_regions_per_slice;

  size_t regions_from = _bitmap_regions_per_slice * slice;
  size_t regions_to   = MIN2(num_regions(), _bitmap_regions_per_slice * (slice + 1));
  for (size_t g = regions_from; g < regions_to; g++) {
    assert (g / _bitmap_regions_per_slice == slice, "same slice");
    if (skip_self && g == r->region_number()) continue;
    if (get_region(g)->is_committed()) {
      return true;
    }
  }
  return false;
}

bool ShenandoahHeap::commit_bitmap_slice(ShenandoahHeapRegion* r) {
  assert_heaplock_owned_by_current_thread();

  // Bitmaps in special regions do not need commits
  if (_bitmap_region_special) {
    return true;
  }

  if (is_bitmap_slice_committed(r, true)) {
    // Some other region from the group is already committed, meaning the bitmap
    // slice is already committed, we exit right away.
    return true;
  }

  // Commit the bitmap slice:
  size_t slice = r->region_number() / _bitmap_regions_per_slice;
  size_t off = _bitmap_bytes_per_slice * slice;
  size_t len = _bitmap_bytes_per_slice;
  if (!os::commit_memory((char*)_bitmap_region.start() + off, len, false)) {
    return false;
  }
  return true;
}

bool ShenandoahHeap::uncommit_bitmap_slice(ShenandoahHeapRegion *r) {
  assert_heaplock_owned_by_current_thread();

  // Bitmaps in special regions do not need uncommits
  if (_bitmap_region_special) {
    return true;
  }

  if (is_bitmap_slice_committed(r, true)) {
    // Some other region from the group is still committed, meaning the bitmap
    // slice is should stay committed, exit right away.
    return true;
  }

  // Uncommit the bitmap slice:
  size_t slice = r->region_number() / _bitmap_regions_per_slice;
  size_t off = _bitmap_bytes_per_slice * slice;
  size_t len = _bitmap_bytes_per_slice;
  if (!os::uncommit_memory((char*)_bitmap_region.start() + off, len)) {
    return false;
  }
  return true;
}

void ShenandoahHeap::safepoint_synchronize_begin() {
  if (ShenandoahSuspendibleWorkers || UseStringDeduplication) {
    SuspendibleThreadSet::synchronize();
  }
}

void ShenandoahHeap::safepoint_synchronize_end() {
  if (ShenandoahSuspendibleWorkers || UseStringDeduplication) {
    SuspendibleThreadSet::desynchronize();
  }
}

void ShenandoahHeap::vmop_entry_init_mark() {
  TraceCollectorStats tcs(monitoring_support()->stw_collection_counters());
  ShenandoahGCPhase total(ShenandoahPhaseTimings::total_pause_gross);
  ShenandoahGCPhase phase(ShenandoahPhaseTimings::init_mark_gross);

  try_inject_alloc_failure();
  VM_ShenandoahInitMark op;
  VMThread::execute(&op); // jump to entry_init_mark() under safepoint
}

void ShenandoahHeap::vmop_entry_final_mark() {
  TraceCollectorStats tcs(monitoring_support()->stw_collection_counters());
  ShenandoahGCPhase total(ShenandoahPhaseTimings::total_pause_gross);
  ShenandoahGCPhase phase(ShenandoahPhaseTimings::final_mark_gross);

  try_inject_alloc_failure();
  VM_ShenandoahFinalMarkStartEvac op;
  VMThread::execute(&op); // jump to entry_final_mark under safepoint
}

void ShenandoahHeap::vmop_entry_final_evac() {
  TraceCollectorStats tcs(monitoring_support()->stw_collection_counters());
  ShenandoahGCPhase total(ShenandoahPhaseTimings::total_pause_gross);
  ShenandoahGCPhase phase(ShenandoahPhaseTimings::final_evac_gross);

  VM_ShenandoahFinalEvac op;
  VMThread::execute(&op); // jump to entry_final_evac under safepoint
}

void ShenandoahHeap::vmop_entry_init_updaterefs() {
  TraceCollectorStats tcs(monitoring_support()->stw_collection_counters());
  ShenandoahGCPhase total(ShenandoahPhaseTimings::total_pause_gross);
  ShenandoahGCPhase phase(ShenandoahPhaseTimings::init_update_refs_gross);

  try_inject_alloc_failure();
  VM_ShenandoahInitUpdateRefs op;
  VMThread::execute(&op);
}

void ShenandoahHeap::vmop_entry_final_updaterefs() {
  TraceCollectorStats tcs(monitoring_support()->stw_collection_counters());
  ShenandoahGCPhase total(ShenandoahPhaseTimings::total_pause_gross);
  ShenandoahGCPhase phase(ShenandoahPhaseTimings::final_update_refs_gross);

  try_inject_alloc_failure();
  VM_ShenandoahFinalUpdateRefs op;
  VMThread::execute(&op);
}

void ShenandoahHeap::vmop_entry_init_traversal() {
  TraceCollectorStats tcs(monitoring_support()->stw_collection_counters());
  ShenandoahGCPhase total(ShenandoahPhaseTimings::total_pause_gross);
  ShenandoahGCPhase phase(ShenandoahPhaseTimings::init_traversal_gc_gross);

  try_inject_alloc_failure();
  VM_ShenandoahInitTraversalGC op;
  VMThread::execute(&op);
}

void ShenandoahHeap::vmop_entry_final_traversal() {
  TraceCollectorStats tcs(monitoring_support()->stw_collection_counters());
  ShenandoahGCPhase total(ShenandoahPhaseTimings::total_pause_gross);
  ShenandoahGCPhase phase(ShenandoahPhaseTimings::final_traversal_gc_gross);

  try_inject_alloc_failure();
  VM_ShenandoahFinalTraversalGC op;
  VMThread::execute(&op);
}

void ShenandoahHeap::vmop_entry_full(GCCause::Cause cause) {
  TraceCollectorStats tcs(monitoring_support()->full_stw_collection_counters());
  ShenandoahGCPhase total(ShenandoahPhaseTimings::total_pause_gross);
  ShenandoahGCPhase phase(ShenandoahPhaseTimings::full_gc_gross);

  try_inject_alloc_failure();
  VM_ShenandoahFullGC op(cause);
  VMThread::execute(&op);
}

void ShenandoahHeap::vmop_degenerated(ShenandoahDegenPoint point) {
  TraceCollectorStats tcs(monitoring_support()->full_stw_collection_counters());
  ShenandoahGCPhase total(ShenandoahPhaseTimings::total_pause_gross);
  ShenandoahGCPhase phase(ShenandoahPhaseTimings::degen_gc_gross);

  VM_ShenandoahDegeneratedGC degenerated_gc((int)point);
  VMThread::execute(&degenerated_gc);
}

void ShenandoahHeap::entry_init_mark() {
  ShenandoahGCPhase total_phase(ShenandoahPhaseTimings::total_pause);
  ShenandoahGCPhase phase(ShenandoahPhaseTimings::init_mark);
  const char* msg = init_mark_event_message();
  GCTraceTime(Info, gc) time(msg, gc_timer());
  EventMark em("%s", msg);

  ShenandoahWorkerScope scope(workers(),
                              ShenandoahWorkerPolicy::calc_workers_for_init_marking(),
                              "init marking");

  op_init_mark();
}

void ShenandoahHeap::entry_final_mark() {
  ShenandoahGCPhase total_phase(ShenandoahPhaseTimings::total_pause);
  ShenandoahGCPhase phase(ShenandoahPhaseTimings::final_mark);
  const char* msg = final_mark_event_message();
  GCTraceTime(Info, gc) time(msg, gc_timer());
  EventMark em("%s", msg);

  ShenandoahWorkerScope scope(workers(),
                              ShenandoahWorkerPolicy::calc_workers_for_final_marking(),
                              "final marking");

  op_final_mark();
}

void ShenandoahHeap::entry_final_evac() {
  ShenandoahGCPhase total_phase(ShenandoahPhaseTimings::total_pause);
  ShenandoahGCPhase phase(ShenandoahPhaseTimings::final_evac);
  static const char* msg = "Pause Final Evac";
  GCTraceTime(Info, gc) time(msg, gc_timer());
  EventMark em("%s", msg);

  op_final_evac();
}

void ShenandoahHeap::entry_init_updaterefs() {
  ShenandoahGCPhase total_phase(ShenandoahPhaseTimings::total_pause);
  ShenandoahGCPhase phase(ShenandoahPhaseTimings::init_update_refs);

  static const char* msg = "Pause Init Update Refs";
  GCTraceTime(Info, gc) time(msg, gc_timer());
  EventMark em("%s", msg);

  // No workers used in this phase, no setup required

  op_init_updaterefs();
}

void ShenandoahHeap::entry_final_updaterefs() {
  ShenandoahGCPhase total_phase(ShenandoahPhaseTimings::total_pause);
  ShenandoahGCPhase phase(ShenandoahPhaseTimings::final_update_refs);

  static const char* msg = "Pause Final Update Refs";
  GCTraceTime(Info, gc) time(msg, gc_timer());
  EventMark em("%s", msg);

  ShenandoahWorkerScope scope(workers(),
                              ShenandoahWorkerPolicy::calc_workers_for_final_update_ref(),
                              "final reference update");

  op_final_updaterefs();
}

void ShenandoahHeap::entry_init_traversal() {
  ShenandoahGCPhase total_phase(ShenandoahPhaseTimings::total_pause);
  ShenandoahGCPhase phase(ShenandoahPhaseTimings::init_traversal_gc);

  static const char* msg = "Pause Init Traversal";
  GCTraceTime(Info, gc) time(msg, gc_timer());
  EventMark em("%s", msg);

  ShenandoahWorkerScope scope(workers(),
                              ShenandoahWorkerPolicy::calc_workers_for_stw_traversal(),
                              "init traversal");

  op_init_traversal();
}

void ShenandoahHeap::entry_final_traversal() {
  ShenandoahGCPhase total_phase(ShenandoahPhaseTimings::total_pause);
  ShenandoahGCPhase phase(ShenandoahPhaseTimings::final_traversal_gc);

  static const char* msg = "Pause Final Traversal";
  GCTraceTime(Info, gc) time(msg, gc_timer());
  EventMark em("%s", msg);

  ShenandoahWorkerScope scope(workers(),
                              ShenandoahWorkerPolicy::calc_workers_for_stw_traversal(),
                              "final traversal");

  op_final_traversal();
}

void ShenandoahHeap::entry_full(GCCause::Cause cause) {
  ShenandoahGCPhase total_phase(ShenandoahPhaseTimings::total_pause);
  ShenandoahGCPhase phase(ShenandoahPhaseTimings::full_gc);

  static const char* msg = "Pause Full";
  GCTraceTime(Info, gc) time(msg, gc_timer(), cause, true);
  EventMark em("%s", msg);

  ShenandoahWorkerScope scope(workers(),
                              ShenandoahWorkerPolicy::calc_workers_for_fullgc(),
                              "full gc");

  op_full(cause);
}

void ShenandoahHeap::entry_degenerated(int point) {
  ShenandoahGCPhase total_phase(ShenandoahPhaseTimings::total_pause);
  ShenandoahGCPhase phase(ShenandoahPhaseTimings::degen_gc);

  ShenandoahDegenPoint dpoint = (ShenandoahDegenPoint)point;
  const char* msg = degen_event_message(dpoint);
  GCTraceTime(Info, gc) time(msg, NULL, GCCause::_no_gc, true);
  EventMark em("%s", msg);

  ShenandoahWorkerScope scope(workers(),
                              ShenandoahWorkerPolicy::calc_workers_for_stw_degenerated(),
                              "stw degenerated gc");

  set_degenerated_gc_in_progress(true);
  op_degenerated(dpoint);
  set_degenerated_gc_in_progress(false);
}

void ShenandoahHeap::entry_mark() {
  TraceCollectorStats tcs(monitoring_support()->concurrent_collection_counters());

  const char* msg = conc_mark_event_message();
  GCTraceTime(Info, gc) time(msg, NULL, GCCause::_no_gc, true);
  EventMark em("%s", msg);

  ShenandoahWorkerScope scope(workers(),
                              ShenandoahWorkerPolicy::calc_workers_for_conc_marking(),
                              "concurrent marking");

  try_inject_alloc_failure();
  op_mark();
}

void ShenandoahHeap::entry_evac() {
  ShenandoahGCPhase conc_evac_phase(ShenandoahPhaseTimings::conc_evac);
  TraceCollectorStats tcs(monitoring_support()->concurrent_collection_counters());

  static const char* msg = "Concurrent evacuation";
  GCTraceTime(Info, gc) time(msg, NULL, GCCause::_no_gc, true);
  EventMark em("%s", msg);

  ShenandoahWorkerScope scope(workers(),
                              ShenandoahWorkerPolicy::calc_workers_for_conc_evac(),
                              "concurrent evacuation");

  try_inject_alloc_failure();
  op_conc_evac();
}

void ShenandoahHeap::entry_updaterefs() {
  ShenandoahGCPhase phase(ShenandoahPhaseTimings::conc_update_refs);

  static const char* msg = "Concurrent update references";
  GCTraceTime(Info, gc) time(msg, NULL, GCCause::_no_gc, true);
  EventMark em("%s", msg);

  ShenandoahWorkerScope scope(workers(),
                              ShenandoahWorkerPolicy::calc_workers_for_conc_update_ref(),
                              "concurrent reference update");

  try_inject_alloc_failure();
  op_updaterefs();
}
void ShenandoahHeap::entry_cleanup() {
  ShenandoahGCPhase phase(ShenandoahPhaseTimings::conc_cleanup);

  static const char* msg = "Concurrent cleanup";
  GCTraceTime(Info, gc) time(msg, NULL, GCCause::_no_gc, true);
  EventMark em("%s", msg);

  // This phase does not use workers, no need for setup

  try_inject_alloc_failure();
  op_cleanup();
}

void ShenandoahHeap::entry_reset() {
  ShenandoahGCPhase phase(ShenandoahPhaseTimings::conc_reset);

  static const char* msg = "Concurrent reset";
  GCTraceTime(Info, gc) time(msg, NULL, GCCause::_no_gc, true);
  EventMark em("%s", msg);

  ShenandoahWorkerScope scope(workers(),
                              ShenandoahWorkerPolicy::calc_workers_for_conc_reset(),
                              "concurrent reset");

  try_inject_alloc_failure();
  op_reset();
}

void ShenandoahHeap::entry_preclean() {
  if (ShenandoahPreclean && process_references()) {
    static const char* msg = "Concurrent precleaning";
    GCTraceTime(Info, gc) time(msg, NULL, GCCause::_no_gc, true);
    EventMark em("%s", msg);

    ShenandoahGCPhase conc_preclean(ShenandoahPhaseTimings::conc_preclean);

    ShenandoahWorkerScope scope(workers(),
                                ShenandoahWorkerPolicy::calc_workers_for_conc_preclean(),
                                "concurrent preclean",
                                /* check_workers = */ false);

    try_inject_alloc_failure();
    op_preclean();
  }
}

void ShenandoahHeap::entry_traversal() {
  static const char* msg = "Concurrent traversal";
  GCTraceTime(Info, gc) time(msg, NULL, GCCause::_no_gc, true);
  EventMark em("%s", msg);

  TraceCollectorStats tcs(monitoring_support()->concurrent_collection_counters());

  ShenandoahWorkerScope scope(workers(),
                              ShenandoahWorkerPolicy::calc_workers_for_conc_traversal(),
                              "concurrent traversal");

  try_inject_alloc_failure();
  op_traversal();
}

void ShenandoahHeap::entry_uncommit(double shrink_before) {
  static const char *msg = "Concurrent uncommit";
  GCTraceTime(Info, gc) time(msg, NULL, GCCause::_no_gc, true);
  EventMark em("%s", msg);

  ShenandoahGCPhase phase(ShenandoahPhaseTimings::conc_uncommit);

  op_uncommit(shrink_before);
}

void ShenandoahHeap::try_inject_alloc_failure() {
  if (ShenandoahAllocFailureALot && !cancelled_gc() && ((os::random() % 1000) > 950)) {
    _inject_alloc_failure.set();
    os::naked_short_sleep(1);
    if (cancelled_gc()) {
      log_info(gc)("Allocation failure was successfully injected");
    }
  }
}

bool ShenandoahHeap::should_inject_alloc_failure() {
  return _inject_alloc_failure.is_set() && _inject_alloc_failure.try_unset();
}

void ShenandoahHeap::initialize_serviceability() {
  _memory_pool = new ShenandoahMemoryPool(this);
  _cycle_memory_manager.add_pool(_memory_pool);
  _stw_memory_manager.add_pool(_memory_pool);
}

GrowableArray<GCMemoryManager*> ShenandoahHeap::memory_managers() {
  GrowableArray<GCMemoryManager*> memory_managers(2);
  memory_managers.append(&_cycle_memory_manager);
  memory_managers.append(&_stw_memory_manager);
  return memory_managers;
}

GrowableArray<MemoryPool*> ShenandoahHeap::memory_pools() {
  GrowableArray<MemoryPool*> memory_pools(1);
  memory_pools.append(_memory_pool);
  return memory_pools;
}

MemoryUsage ShenandoahHeap::memory_usage() {
  return _memory_pool->get_memory_usage();
}

void ShenandoahHeap::enter_evacuation() {
  _oom_evac_handler.enter_evacuation();
}

void ShenandoahHeap::leave_evacuation() {
  _oom_evac_handler.leave_evacuation();
}

ShenandoahRegionIterator::ShenandoahRegionIterator() :
  _heap(ShenandoahHeap::heap()),
  _index(0) {}

ShenandoahRegionIterator::ShenandoahRegionIterator(ShenandoahHeap* heap) :
  _heap(heap),
  _index(0) {}

void ShenandoahRegionIterator::reset() {
  _index = 0;
}

bool ShenandoahRegionIterator::has_next() const {
  return _index < _heap->num_regions();
}

char ShenandoahHeap::gc_state() const {
  return _gc_state.raw_value();
}

void ShenandoahHeap::deduplicate_string(oop str) {
  assert(java_lang_String::is_instance(str), "invariant");

  if (ShenandoahStringDedup::is_enabled()) {
    ShenandoahStringDedup::deduplicate(str);
  }
}

const char* ShenandoahHeap::init_mark_event_message() const {
  bool update_refs = has_forwarded_objects();
  bool proc_refs = process_references();
  bool unload_cls = unload_classes();

  if (update_refs && proc_refs && unload_cls) {
    return "Pause Init Mark (update refs) (process weakrefs) (unload classes)";
  } else if (update_refs && proc_refs) {
    return "Pause Init Mark (update refs) (process weakrefs)";
  } else if (update_refs && unload_cls) {
    return "Pause Init Mark (update refs) (unload classes)";
  } else if (proc_refs && unload_cls) {
    return "Pause Init Mark (process weakrefs) (unload classes)";
  } else if (update_refs) {
    return "Pause Init Mark (update refs)";
  } else if (proc_refs) {
    return "Pause Init Mark (process weakrefs)";
  } else if (unload_cls) {
    return "Pause Init Mark (unload classes)";
  } else {
    return "Pause Init Mark";
  }
}

const char* ShenandoahHeap::final_mark_event_message() const {
  bool update_refs = has_forwarded_objects();
  bool proc_refs = process_references();
  bool unload_cls = unload_classes();

  if (update_refs && proc_refs && unload_cls) {
    return "Pause Final Mark (update refs) (process weakrefs) (unload classes)";
  } else if (update_refs && proc_refs) {
    return "Pause Final Mark (update refs) (process weakrefs)";
  } else if (update_refs && unload_cls) {
    return "Pause Final Mark (update refs) (unload classes)";
  } else if (proc_refs && unload_cls) {
    return "Pause Final Mark (process weakrefs) (unload classes)";
  } else if (update_refs) {
    return "Pause Final Mark (update refs)";
  } else if (proc_refs) {
    return "Pause Final Mark (process weakrefs)";
  } else if (unload_cls) {
    return "Pause Final Mark (unload classes)";
  } else {
    return "Pause Final Mark";
  }
}

const char* ShenandoahHeap::conc_mark_event_message() const {
  bool update_refs = has_forwarded_objects();
  bool proc_refs = process_references();
  bool unload_cls = unload_classes();

  if (update_refs && proc_refs && unload_cls) {
    return "Concurrent marking (update refs) (process weakrefs) (unload classes)";
  } else if (update_refs && proc_refs) {
    return "Concurrent marking (update refs) (process weakrefs)";
  } else if (update_refs && unload_cls) {
    return "Concurrent marking (update refs) (unload classes)";
  } else if (proc_refs && unload_cls) {
    return "Concurrent marking (process weakrefs) (unload classes)";
  } else if (update_refs) {
    return "Concurrent marking (update refs)";
  } else if (proc_refs) {
    return "Concurrent marking (process weakrefs)";
  } else if (unload_cls) {
    return "Concurrent marking (unload classes)";
  } else {
    return "Concurrent marking";
  }
}

const char* ShenandoahHeap::degen_event_message(ShenandoahDegenPoint point) const {
  switch (point) {
    case _degenerated_unset:
      return "Pause Degenerated GC (<UNSET>)";
    case _degenerated_traversal:
      return "Pause Degenerated GC (Traversal)";
    case _degenerated_outside_cycle:
      return "Pause Degenerated GC (Outside of Cycle)";
    case _degenerated_mark:
      return "Pause Degenerated GC (Mark)";
    case _degenerated_evac:
      return "Pause Degenerated GC (Evacuation)";
    case _degenerated_updaterefs:
      return "Pause Degenerated GC (Update Refs)";
    default:
      ShouldNotReachHere();
      return "ERROR";
  }
}

jushort* ShenandoahHeap::get_liveness_cache(uint worker_id) {
#ifdef ASSERT
  assert(_liveness_cache != NULL, "sanity");
  assert(worker_id < _max_workers, "sanity");
  for (uint i = 0; i < num_regions(); i++) {
    assert(_liveness_cache[worker_id][i] == 0, "liveness cache should be empty");
  }
#endif
  return _liveness_cache[worker_id];
}

void ShenandoahHeap::flush_liveness_cache(uint worker_id) {
  assert(worker_id < _max_workers, "sanity");
  assert(_liveness_cache != NULL, "sanity");
  jushort* ld = _liveness_cache[worker_id];
  for (uint i = 0; i < num_regions(); i++) {
    ShenandoahHeapRegion* r = get_region(i);
    jushort live = ld[i];
    if (live > 0) {
      r->increase_live_data_gc_words(live);
      ld[i] = 0;
    }
  }
}

size_t ShenandoahHeap::obj_size(oop obj) const {
  return CollectedHeap::obj_size(obj) + ShenandoahBrooksPointer::word_size();
}

ptrdiff_t ShenandoahHeap::cell_header_size() const {
  return ShenandoahBrooksPointer::byte_size();
}

BoolObjectClosure* ShenandoahIsAliveSelector::is_alive_closure() {
  return ShenandoahHeap::heap()->has_forwarded_objects() ? reinterpret_cast<BoolObjectClosure*>(&_fwd_alive_cl)
                                                         : reinterpret_cast<BoolObjectClosure*>(&_alive_cl);
}
