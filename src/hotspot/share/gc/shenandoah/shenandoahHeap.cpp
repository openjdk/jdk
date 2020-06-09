/*
 * Copyright (c) 2013, 2020, Red Hat, Inc. All rights reserved.
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
#include "memory/allocation.hpp"
#include "memory/universe.hpp"

#include "gc/shared/gcArguments.hpp"
#include "gc/shared/gcTimer.hpp"
#include "gc/shared/gcTraceTime.inline.hpp"
#include "gc/shared/locationPrinter.inline.hpp"
#include "gc/shared/memAllocator.hpp"
#include "gc/shared/oopStorageSet.hpp"
#include "gc/shared/plab.hpp"

#include "gc/shenandoah/shenandoahBarrierSet.hpp"
#include "gc/shenandoah/shenandoahClosures.inline.hpp"
#include "gc/shenandoah/shenandoahCollectionSet.hpp"
#include "gc/shenandoah/shenandoahCollectorPolicy.hpp"
#include "gc/shenandoah/shenandoahConcurrentMark.inline.hpp"
#include "gc/shenandoah/shenandoahConcurrentRoots.hpp"
#include "gc/shenandoah/shenandoahControlThread.hpp"
#include "gc/shenandoah/shenandoahFreeSet.hpp"
#include "gc/shenandoah/shenandoahPhaseTimings.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahHeapRegion.inline.hpp"
#include "gc/shenandoah/shenandoahHeapRegionSet.hpp"
#include "gc/shenandoah/shenandoahInitLogger.hpp"
#include "gc/shenandoah/shenandoahMarkCompact.hpp"
#include "gc/shenandoah/shenandoahMarkingContext.inline.hpp"
#include "gc/shenandoah/shenandoahMemoryPool.hpp"
#include "gc/shenandoah/shenandoahMetrics.hpp"
#include "gc/shenandoah/shenandoahMonitoringSupport.hpp"
#include "gc/shenandoah/shenandoahOopClosures.inline.hpp"
#include "gc/shenandoah/shenandoahPacer.inline.hpp"
#include "gc/shenandoah/shenandoahPadding.hpp"
#include "gc/shenandoah/shenandoahParallelCleaning.inline.hpp"
#include "gc/shenandoah/shenandoahRootProcessor.inline.hpp"
#include "gc/shenandoah/shenandoahStringDedup.hpp"
#include "gc/shenandoah/shenandoahTaskqueue.hpp"
#include "gc/shenandoah/shenandoahUtils.hpp"
#include "gc/shenandoah/shenandoahVerifier.hpp"
#include "gc/shenandoah/shenandoahCodeRoots.hpp"
#include "gc/shenandoah/shenandoahVMOperations.hpp"
#include "gc/shenandoah/shenandoahWorkGroup.hpp"
#include "gc/shenandoah/shenandoahWorkerPolicy.hpp"
#include "gc/shenandoah/mode/shenandoahIUMode.hpp"
#include "gc/shenandoah/mode/shenandoahPassiveMode.hpp"
#include "gc/shenandoah/mode/shenandoahSATBMode.hpp"
#if INCLUDE_JFR
#include "gc/shenandoah/shenandoahJfrSupport.hpp"
#endif

#include "memory/metaspace.hpp"
#include "oops/compressedOops.inline.hpp"
#include "runtime/atomic.hpp"
#include "runtime/globals.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/orderAccess.hpp"
#include "runtime/safepointMechanism.hpp"
#include "runtime/vmThread.hpp"
#include "services/mallocTracker.hpp"
#include "utilities/powerOfTwo.hpp"

ShenandoahHeap* ShenandoahHeap::_heap = NULL;

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
      if (r->is_committed()) {
        os::pretouch_memory(r->bottom(), r->end(), _page_size);
      }
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
      size_t start = r->index()       * ShenandoahHeapRegion::region_size_bytes() / MarkBitMap::heap_map_factor();
      size_t end   = (r->index() + 1) * ShenandoahHeapRegion::region_size_bytes() / MarkBitMap::heap_map_factor();
      assert (end <= _bitmap_size, "end is sane: " SIZE_FORMAT " < " SIZE_FORMAT, end, _bitmap_size);

      if (r->is_committed()) {
        os::pretouch_memory(_bitmap_base + start, _bitmap_base + end, _page_size);
      }

      r = _regions.next();
    }
  }
};

jint ShenandoahHeap::initialize() {
  //
  // Figure out heap sizing
  //

  size_t init_byte_size = InitialHeapSize;
  size_t min_byte_size  = MinHeapSize;
  size_t max_byte_size  = MaxHeapSize;
  size_t heap_alignment = HeapAlignment;

  size_t reg_size_bytes = ShenandoahHeapRegion::region_size_bytes();

  Universe::check_alignment(max_byte_size,  reg_size_bytes, "Shenandoah heap");
  Universe::check_alignment(init_byte_size, reg_size_bytes, "Shenandoah heap");

  _num_regions = ShenandoahHeapRegion::region_count();

  // Now we know the number of regions, initialize the heuristics.
  initialize_heuristics();

  size_t num_committed_regions = init_byte_size / reg_size_bytes;
  num_committed_regions = MIN2(num_committed_regions, _num_regions);
  assert(num_committed_regions <= _num_regions, "sanity");
  _initial_size = num_committed_regions * reg_size_bytes;

  size_t num_min_regions = min_byte_size / reg_size_bytes;
  num_min_regions = MIN2(num_min_regions, _num_regions);
  assert(num_min_regions <= _num_regions, "sanity");
  _minimum_size = num_min_regions * reg_size_bytes;

  _committed = _initial_size;

  size_t heap_page_size   = UseLargePages ? (size_t)os::large_page_size() : (size_t)os::vm_page_size();
  size_t bitmap_page_size = UseLargePages ? (size_t)os::large_page_size() : (size_t)os::vm_page_size();
  size_t region_page_size = UseLargePages ? (size_t)os::large_page_size() : (size_t)os::vm_page_size();

  //
  // Reserve and commit memory for heap
  //

  ReservedHeapSpace heap_rs = Universe::reserve_heap(max_byte_size, heap_alignment);
  initialize_reserved_region(heap_rs);
  _heap_region = MemRegion((HeapWord*)heap_rs.base(), heap_rs.size() / HeapWordSize);
  _heap_region_special = heap_rs.special();

  assert((((size_t) base()) & ShenandoahHeapRegion::region_size_bytes_mask()) == 0,
         "Misaligned heap: " PTR_FORMAT, p2i(base()));

#if SHENANDOAH_OPTIMIZED_OBJTASK
  // The optimized ObjArrayChunkedTask takes some bits away from the full object bits.
  // Fail if we ever attempt to address more than we can.
  if ((uintptr_t)heap_rs.end() >= ObjArrayChunkedTask::max_addressable()) {
    FormatBuffer<512> buf("Shenandoah reserved [" PTR_FORMAT ", " PTR_FORMAT") for the heap, \n"
                          "but max object address is " PTR_FORMAT ". Try to reduce heap size, or try other \n"
                          "VM options that allocate heap at lower addresses (HeapBaseMinAddress, AllocateHeapAt, etc).",
                p2i(heap_rs.base()), p2i(heap_rs.end()), ObjArrayChunkedTask::max_addressable());
    vm_exit_during_initialization("Fatal Error", buf);
  }
#endif

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
  size_t region_align = align_up(sizeof(ShenandoahHeapRegion), SHENANDOAH_CACHE_LINE_SIZE);
  size_t region_storage_size = align_up(region_align * _num_regions, region_page_size);
  region_storage_size = align_up(region_storage_size, os::vm_allocation_granularity());

  ReservedSpace region_storage(region_storage_size, region_page_size);
  MemTracker::record_virtual_memory_type(region_storage.base(), mtGC);
  if (!region_storage.special()) {
    os::commit_memory_or_exit(region_storage.base(), region_storage_size, region_page_size, false,
                              "Cannot commit region memory");
  }

  // Try to fit the collection set bitmap at lower addresses. This optimizes code generation for cset checks.
  // Go up until a sensible limit (subject to encoding constraints) and try to reserve the space there.
  // If not successful, bite a bullet and allocate at whatever address.
  {
    size_t cset_align = MAX2<size_t>(os::vm_page_size(), os::vm_allocation_granularity());
    size_t cset_size = align_up(((size_t) sh_rs.base() + sh_rs.size()) >> ShenandoahHeapRegion::region_size_bytes_shift(), cset_align);

    uintptr_t min = round_up_power_of_2(cset_align);
    uintptr_t max = (1u << 30u);

    for (uintptr_t addr = min; addr <= max; addr <<= 1u) {
      char* req_addr = (char*)addr;
      assert(is_aligned(req_addr, cset_align), "Should be aligned");
      ReservedSpace cset_rs(cset_size, cset_align, false, req_addr);
      if (cset_rs.is_reserved()) {
        assert(cset_rs.base() == req_addr, "Allocated where requested: " PTR_FORMAT ", " PTR_FORMAT, p2i(cset_rs.base()), addr);
        _collection_set = new ShenandoahCollectionSet(this, cset_rs, sh_rs.base());
        break;
      }
    }

    if (_collection_set == NULL) {
      ReservedSpace cset_rs(cset_size, cset_align, false);
      _collection_set = new ShenandoahCollectionSet(this, cset_rs, sh_rs.base());
    }
  }

  _regions = NEW_C_HEAP_ARRAY(ShenandoahHeapRegion*, _num_regions, mtGC);
  _free_set = new ShenandoahFreeSet(this, _num_regions);

  {
    ShenandoahHeapLocker locker(lock());

    for (size_t i = 0; i < _num_regions; i++) {
      HeapWord* start = (HeapWord*)sh_rs.base() + ShenandoahHeapRegion::region_size_words() * i;
      bool is_committed = i < num_committed_regions;
      void* loc = region_storage.base() + i * region_align;

      ShenandoahHeapRegion* r = new (loc) ShenandoahHeapRegion(start, i, is_committed);
      assert(is_aligned(r, SHENANDOAH_CACHE_LINE_SIZE), "Sanity");

      _marking_context->initialize_top_at_mark_start(r);
      _regions[i] = r;
      assert(!collection_set()->is_in(i), "New region should not be in collection set");
    }

    // Initialize to complete
    _marking_context->mark_complete();

    _free_set->rebuild();
  }

  if (AlwaysPreTouch) {
    // For NUMA, it is important to pre-touch the storage under bitmaps with worker threads,
    // before initialize() below zeroes it with initializing thread. For any given region,
    // we touch the region and the corresponding bitmaps from the same thread.
    ShenandoahPushWorkerScope scope(workers(), _max_workers, false);

    _pretouch_heap_page_size = heap_page_size;
    _pretouch_bitmap_page_size = bitmap_page_size;

#ifdef LINUX
    // UseTransparentHugePages would madvise that backing memory can be coalesced into huge
    // pages. But, the kernel needs to know that every small page is used, in order to coalesce
    // them into huge one. Therefore, we need to pretouch with smaller pages.
    if (UseTransparentHugePages) {
      _pretouch_heap_page_size = (size_t)os::vm_page_size();
      _pretouch_bitmap_page_size = (size_t)os::vm_page_size();
    }
#endif

    // OS memory managers may want to coalesce back-to-back pages. Make their jobs
    // simpler by pre-touching continuous spaces (heap and bitmap) separately.

    ShenandoahPretouchBitmapTask bcl(bitmap.base(), _bitmap_size, _pretouch_bitmap_page_size);
    _workers->run_task(&bcl);

    ShenandoahPretouchHeapTask hcl(_pretouch_heap_page_size);
    _workers->run_task(&hcl);
  }

  //
  // Initialize the rest of GC subsystems
  //

  _liveness_cache = NEW_C_HEAP_ARRAY(ShenandoahLiveData*, _max_workers, mtGC);
  for (uint worker = 0; worker < _max_workers; worker++) {
    _liveness_cache[worker] = NEW_C_HEAP_ARRAY(ShenandoahLiveData, _num_regions, mtGC);
    Copy::fill_to_bytes(_liveness_cache[worker], _num_regions * sizeof(ShenandoahLiveData));
  }

  // There should probably be Shenandoah-specific options for these,
  // just as there are G1-specific options.
  {
    ShenandoahSATBMarkQueueSet& satbqs = ShenandoahBarrierSet::satb_mark_queue_set();
    satbqs.set_process_completed_buffers_threshold(20); // G1SATBProcessCompletedThreshold
    satbqs.set_buffer_enqueue_threshold_percentage(60); // G1SATBBufferEnqueueingThresholdPercent
  }

  _monitoring_support = new ShenandoahMonitoringSupport(this);
  _phase_timings = new ShenandoahPhaseTimings(max_workers());
  ShenandoahStringDedup::initialize();
  ShenandoahCodeRoots::initialize();

  if (ShenandoahPacing) {
    _pacer = new ShenandoahPacer(this);
    _pacer->setup_for_idle();
  } else {
    _pacer = NULL;
  }

  _control_thread = new ShenandoahControlThread();

  _ref_proc_mt_processing = ParallelRefProcEnabled && (ParallelGCThreads > 1);
  _ref_proc_mt_discovery = _max_workers > 1;

  ShenandoahInitLogger::print();

  return JNI_OK;
}

void ShenandoahHeap::initialize_heuristics() {
  if (ShenandoahGCMode != NULL) {
    if (strcmp(ShenandoahGCMode, "satb") == 0) {
      _gc_mode = new ShenandoahSATBMode();
    } else if (strcmp(ShenandoahGCMode, "iu") == 0) {
      _gc_mode = new ShenandoahIUMode();
    } else if (strcmp(ShenandoahGCMode, "passive") == 0) {
      _gc_mode = new ShenandoahPassiveMode();
    } else {
      vm_exit_during_initialization("Unknown -XX:ShenandoahGCMode option");
    }
  } else {
    ShouldNotReachHere();
  }
  _gc_mode->initialize_flags();
  if (_gc_mode->is_diagnostic() && !UnlockDiagnosticVMOptions) {
    vm_exit_during_initialization(
            err_msg("GC mode \"%s\" is diagnostic, and must be enabled via -XX:+UnlockDiagnosticVMOptions.",
                    _gc_mode->name()));
  }
  if (_gc_mode->is_experimental() && !UnlockExperimentalVMOptions) {
    vm_exit_during_initialization(
            err_msg("GC mode \"%s\" is experimental, and must be enabled via -XX:+UnlockExperimentalVMOptions.",
                    _gc_mode->name()));
  }

  _heuristics = _gc_mode->initialize_heuristics();

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
  _full_gc(new ShenandoahMarkCompact()),
  _pacer(NULL),
  _verifier(NULL),
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
  _heap = this;

  BarrierSet::set_barrier_set(new ShenandoahBarrierSet(this));

  _max_workers = MAX2(_max_workers, 1U);
  _workers = new ShenandoahWorkGang("Shenandoah GC Threads", _max_workers,
                            /* are_GC_task_threads */ true,
                            /* are_ConcurrentGC_threads */ true);
  if (_workers == NULL) {
    vm_exit_during_initialization("Failed necessary allocation.");
  } else {
    _workers->initialize_workers();
  }

  if (ParallelGCThreads > 1) {
    _safepoint_workers = new ShenandoahWorkGang("Safepoint Cleanup Thread",
                                                ParallelGCThreads,
                      /* are_GC_task_threads */ false,
                 /* are_ConcurrentGC_threads */ false);
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
  st->print_cr(" " SIZE_FORMAT "%s total, " SIZE_FORMAT "%s committed, " SIZE_FORMAT "%s used",
               byte_size_in_proper_unit(max_capacity()), proper_unit_for_byte_size(max_capacity()),
               byte_size_in_proper_unit(committed()),    proper_unit_for_byte_size(committed()),
               byte_size_in_proper_unit(used()),         proper_unit_for_byte_size(used()));
  st->print_cr(" " SIZE_FORMAT " x " SIZE_FORMAT"%s regions",
               num_regions(),
               byte_size_in_proper_unit(ShenandoahHeapRegion::region_size_bytes()),
               proper_unit_for_byte_size(ShenandoahHeapRegion::region_size_bytes()));

  st->print("Status: ");
  if (has_forwarded_objects())                 st->print("has forwarded objects, ");
  if (is_concurrent_mark_in_progress())        st->print("marking, ");
  if (is_evacuation_in_progress())             st->print("evacuating, ");
  if (is_update_refs_in_progress())            st->print("updating refs, ");
  if (is_degenerated_gc_in_progress())         st->print("degenerated gc, ");
  if (is_full_gc_in_progress())                st->print("full gc, ");
  if (is_full_gc_move_in_progress())           st->print("full gc move, ");
  if (is_concurrent_weak_root_in_progress())   st->print("concurrent weak roots, ");
  if (is_concurrent_strong_root_in_progress() &&
      !is_concurrent_weak_root_in_progress())  st->print("concurrent strong roots, ");

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

  ShenandoahCollectionSet* cset = collection_set();
  st->print_cr("Collection set:");
  if (cset != NULL) {
    st->print_cr(" - map (vanilla): " PTR_FORMAT, p2i(cset->map_address()));
    st->print_cr(" - map (biased):  " PTR_FORMAT, p2i(cset->biased_map_address()));
  } else {
    st->print_cr(" (NULL)");
  }

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

  JFR_ONLY(ShenandoahJFRSupport::register_jfr_type_serializers());
}

size_t ShenandoahHeap::used() const {
  return Atomic::load_acquire(&_used);
}

size_t ShenandoahHeap::committed() const {
  OrderAccess::acquire();
  return _committed;
}

void ShenandoahHeap::increase_committed(size_t bytes) {
  shenandoah_assert_heaplocked_or_safepoint();
  _committed += bytes;
}

void ShenandoahHeap::decrease_committed(size_t bytes) {
  shenandoah_assert_heaplocked_or_safepoint();
  _committed -= bytes;
}

void ShenandoahHeap::increase_used(size_t bytes) {
  Atomic::add(&_used, bytes);
}

void ShenandoahHeap::set_used(size_t bytes) {
  Atomic::release_store_fence(&_used, bytes);
}

void ShenandoahHeap::decrease_used(size_t bytes) {
  assert(used() >= bytes, "never decrease heap size by more than we've left");
  Atomic::sub(&_used, bytes);
}

void ShenandoahHeap::increase_allocated(size_t bytes) {
  Atomic::add(&_bytes_allocated_since_gc_start, bytes);
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

size_t ShenandoahHeap::min_capacity() const {
  return _minimum_size;
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

  // Application allocates from the beginning of the heap, and GC allocates at
  // the end of it. It is more efficient to uncommit from the end, so that applications
  // could enjoy the near committed regions. GC allocations are much less frequent,
  // and therefore can accept the committing costs.

  size_t count = 0;
  for (size_t i = num_regions(); i > 0; i--) { // care about size_t underflow
    ShenandoahHeapRegion* r = get_region(i - 1);
    if (r->is_empty_committed() && (r->empty_time() < shrink_before)) {
      ShenandoahHeapLocker locker(lock());
      if (r->is_empty_committed()) {
        // Do not uncommit below minimal capacity
        if (committed() < min_capacity() + ShenandoahHeapRegion::region_size_bytes()) {
          break;
        }

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

HeapWord* ShenandoahHeap::allocate_memory(ShenandoahAllocRequest& req) {
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
      control_thread()->handle_alloc_failure(req);
      result = allocate_memory_under_lock(req, in_new_region);
    }

    while (result == NULL && tries <= ShenandoahFullGCThreshold) {
      tries++;
      control_thread()->handle_alloc_failure(req);
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

class ShenandoahConcurrentEvacuateRegionObjectClosure : public ObjectClosure {
private:
  ShenandoahHeap* const _heap;
  Thread* const _thread;
public:
  ShenandoahConcurrentEvacuateRegionObjectClosure(ShenandoahHeap* heap) :
    _heap(heap), _thread(Thread::current()) {}

  void do_object(oop p) {
    shenandoah_assert_marked(NULL, p);
    if (!p->is_forwarded()) {
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
      assert(r->has_live(), "Region " SIZE_FORMAT " should have been reclaimed early", r->index());
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
  st->print_cr("R=root, CP=critical pins, TAMS=top-at-mark-start, UWM=update watermark");
  st->print_cr("SN=alloc sequence number");

  for (size_t i = 0; i < num_regions(); i++) {
    get_region(i)->print_on(st);
  }
}

void ShenandoahHeap::trash_humongous_region_at(ShenandoahHeapRegion* start) {
  assert(start->is_humongous_start(), "reclaim regions starting with the first one");

  oop humongous_obj = oop(start->bottom());
  size_t size = humongous_obj->size();
  size_t required_regions = ShenandoahHeapRegion::required_regions(size * HeapWordSize);
  size_t index = start->index() + required_regions - 1;

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
    ShenandoahEvacuateUpdateRootsClosure<> cl;
    MarkingCodeBlobClosure blobsCl(&cl, CodeBlobToOopClosure::FixRelocations);
    _rp->roots_do(worker_id, &cl);
  }
};

void ShenandoahHeap::evacuate_and_update_roots() {
#if COMPILER2_OR_JVMCI
  DerivedPointerTable::clear();
#endif
  assert(ShenandoahSafepoint::is_at_shenandoah_safepoint(), "Only iterate roots while world is stopped");
  {
    // Include concurrent roots if current cycle can not process those roots concurrently
    ShenandoahRootEvacuator rp(workers()->active_workers(),
                               ShenandoahPhaseTimings::init_evac,
                               !ShenandoahConcurrentRoots::should_do_concurrent_roots(),
                               !ShenandoahConcurrentRoots::should_do_concurrent_class_unloading());
    ShenandoahEvacuateUpdateRootsTask roots_task(&rp);
    workers()->run_task(&roots_task);
  }

#if COMPILER2_OR_JVMCI
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

HeapWord* ShenandoahHeap::block_start(const void* addr) const {
  ShenandoahHeapRegion* r = heap_region_containing(addr);
  if (r != NULL) {
    return r->block_start(addr);
  }
  return NULL;
}

bool ShenandoahHeap::block_is_obj(const HeapWord* addr) const {
  ShenandoahHeapRegion* r = heap_region_containing(addr);
  return r->block_is_obj(addr);
}

bool ShenandoahHeap::print_location(outputStream* st, void* addr) const {
  return BlockLocationPrinter<ShenandoahHeap>::print_location(st, addr);
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

    phase_timings()->print_global_on(&ls);

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
  ShenandoahHeap* const _heap;
  ShenandoahMarkingContext* const _marking_context;

  template <class T>
  void do_oop_work(T* p) {
    T o = RawAccess<>::oop_load(p);
    if (!CompressedOops::is_null(o)) {
      oop obj = CompressedOops::decode_not_null(o);
      if (_heap->is_concurrent_weak_root_in_progress() && !_marking_context->is_marked(obj)) {
        // There may be dead oops in weak roots in concurrent root phase, do not touch them.
        return;
      }
      obj = ShenandoahBarrierSet::resolve_forwarded_not_null(obj);

      assert(oopDesc::is_oop(obj), "must be a valid oop");
      if (!_bitmap->is_marked(obj)) {
        _bitmap->mark(obj);
        _oop_stack->push(obj);
      }
    }
  }
public:
  ObjectIterateScanRootClosure(MarkBitMap* bitmap, Stack<oop,mtGC>* oop_stack) :
    _bitmap(bitmap), _oop_stack(oop_stack), _heap(ShenandoahHeap::heap()),
    _marking_context(_heap->marking_context()) {}
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

  // First, we process GC roots according to current GC cycle. This populates the work stack with initial objects.
  ShenandoahHeapIterationRootScanner rp;
  ObjectIterateScanRootClosure oops(&_aux_bit_map, &oop_stack);

  rp.roots_do(&oops);

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

// Keep alive an object that was loaded with AS_NO_KEEPALIVE.
void ShenandoahHeap::keep_alive(oop obj) {
  if (is_concurrent_mark_in_progress()) {
    ShenandoahBarrierSet::barrier_set()->enqueue(obj);
  }
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

  shenandoah_padding(0);
  volatile size_t _index;
  shenandoah_padding(1);

public:
  ShenandoahParallelHeapRegionTask(ShenandoahHeapRegionClosure* blk) :
          AbstractGangTask("Parallel Region Task"),
          _heap(ShenandoahHeap::heap()), _blk(blk), _index(0) {}

  void work(uint worker_id) {
    ShenandoahParallelWorkerSession worker_session(worker_id);
    size_t stride = ShenandoahParallelRegionStride;

    size_t max = _heap->num_regions();
    while (_index < max) {
      size_t cur = Atomic::fetch_and_add(&_index, stride);
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

class ShenandoahInitMarkUpdateRegionStateClosure : public ShenandoahHeapRegionClosure {
private:
  ShenandoahMarkingContext* const _ctx;
public:
  ShenandoahInitMarkUpdateRegionStateClosure() : _ctx(ShenandoahHeap::heap()->marking_context()) {}

  void heap_region_do(ShenandoahHeapRegion* r) {
    assert(!r->has_live(), "Region " SIZE_FORMAT " should have no live data", r->index());
    if (r->is_active()) {
      // Check if region needs updating its TAMS. We have updated it already during concurrent
      // reset, so it is very likely we don't need to do another write here.
      if (_ctx->top_at_mark_start(r) != r->top()) {
        _ctx->capture_top_at_mark_start(r);
      }
    } else {
      assert(_ctx->top_at_mark_start(r) == r->top(),
             "Region " SIZE_FORMAT " should already have correct TAMS", r->index());
    }
  }

  bool is_thread_safe() { return true; }
};

void ShenandoahHeap::op_init_mark() {
  assert(ShenandoahSafepoint::is_at_shenandoah_safepoint(), "Should be at safepoint");
  assert(Thread::current()->is_VM_thread(), "can only do this in VMThread");

  assert(marking_context()->is_bitmap_clear(), "need clear marking bitmap");
  assert(!marking_context()->is_complete(), "should not be complete");
  assert(!has_forwarded_objects(), "No forwarded objects on this path");

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
    ShenandoahGCPhase phase(ShenandoahPhaseTimings::init_update_region_states);
    ShenandoahInitMarkUpdateRegionStateClosure cl;
    parallel_heap_region_iterate(&cl);
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

  // Arm nmethods for concurrent marking. When a nmethod is about to be executed,
  // we need to make sure that all its metadata are marked. alternative is to remark
  // thread roots at final mark pause, but it can be potential latency killer.
  if (ShenandoahConcurrentRoots::should_do_concurrent_class_unloading()) {
    ShenandoahCodeRoots::arm_nmethods();
  }
}

void ShenandoahHeap::op_mark() {
  concurrent_mark()->mark_from_roots();
}

class ShenandoahFinalMarkUpdateRegionStateClosure : public ShenandoahHeapRegionClosure {
private:
  ShenandoahMarkingContext* const _ctx;
  ShenandoahHeapLock* const _lock;

public:
  ShenandoahFinalMarkUpdateRegionStateClosure() :
    _ctx(ShenandoahHeap::heap()->complete_marking_context()), _lock(ShenandoahHeap::heap()->lock()) {}

  void heap_region_do(ShenandoahHeapRegion* r) {
    if (r->is_active()) {
      // All allocations past TAMS are implicitly live, adjust the region data.
      // Bitmaps/TAMS are swapped at this point, so we need to poll complete bitmap.
      HeapWord *tams = _ctx->top_at_mark_start(r);
      HeapWord *top = r->top();
      if (top > tams) {
        r->increase_live_data_alloc_words(pointer_delta(top, tams));
      }

      // We are about to select the collection set, make sure it knows about
      // current pinning status. Also, this allows trashing more regions that
      // now have their pinning status dropped.
      if (r->is_pinned()) {
        if (r->pin_count() == 0) {
          ShenandoahHeapLocker locker(_lock);
          r->make_unpinned();
        }
      } else {
        if (r->pin_count() > 0) {
          ShenandoahHeapLocker locker(_lock);
          r->make_pinned();
        }
      }

      // Remember limit for updating refs. It's guaranteed that we get no
      // from-space-refs written from here on.
      r->set_update_watermark_at_safepoint(r->top());
    } else {
      assert(!r->has_live(), "Region " SIZE_FORMAT " should have no live data", r->index());
      assert(_ctx->top_at_mark_start(r) == r->top(),
             "Region " SIZE_FORMAT " should have correct TAMS", r->index());
    }
  }

  bool is_thread_safe() { return true; }
};

void ShenandoahHeap::op_final_mark() {
  assert(ShenandoahSafepoint::is_at_shenandoah_safepoint(), "Should be at safepoint");
  assert(!has_forwarded_objects(), "No forwarded objects on this path");

  // It is critical that we
  // evacuate roots right after finishing marking, so that we don't
  // get unmarked objects in the roots.

  if (!cancelled_gc()) {
    concurrent_mark()->finish_mark_from_roots(/* full_gc = */ false);

    // Marking is completed, deactivate SATB barrier
    set_concurrent_mark_in_progress(false);
    mark_complete_marking_context();

    parallel_cleaning(false /* full gc*/);

    if (ShenandoahVerify) {
      verifier()->verify_roots_no_forwarded();
    }

    {
      ShenandoahGCPhase phase(ShenandoahPhaseTimings::final_update_region_states);
      ShenandoahFinalMarkUpdateRegionStateClosure cl;
      parallel_heap_region_iterate(&cl);

      assert_pinned_region_status();
    }

    // Retire the TLABs, which will force threads to reacquire their TLABs after the pause.
    // This is needed for two reasons. Strong one: new allocations would be with new freeset,
    // which would be outside the collection set, so no cset writes would happen there.
    // Weaker one: new allocations would happen past update watermark, and so less work would
    // be needed for reference updates (would update the large filler instead).
    {
      ShenandoahGCPhase phase(ShenandoahPhaseTimings::retire_tlabs);
      make_parsable(true);
    }

    {
      ShenandoahGCPhase phase(ShenandoahPhaseTimings::choose_cset);
      ShenandoahHeapLocker locker(lock());
      _collection_set->clear();
      heuristics()->choose_collection_set(_collection_set);
    }

    {
      ShenandoahGCPhase phase(ShenandoahPhaseTimings::final_rebuild_freeset);
      ShenandoahHeapLocker locker(lock());
      _free_set->rebuild();
    }

    if (!is_degenerated_gc_in_progress()) {
      prepare_concurrent_roots();
      prepare_concurrent_unloading();
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

      if (!is_degenerated_gc_in_progress()) {
        if (ShenandoahConcurrentRoots::should_do_concurrent_class_unloading()) {
          ShenandoahCodeRoots::arm_nmethods();
        }
        evacuate_and_update_roots();
      }

      if (ShenandoahPacing) {
        pacer()->setup_for_evac();
      }

      if (ShenandoahVerify) {
        // If OOM while evacuating/updating of roots, there is no guarantee of their consistencies
        if (!cancelled_gc()) {
          ShenandoahRootVerifier::RootTypes types = ShenandoahRootVerifier::None;
          if (ShenandoahConcurrentRoots::should_do_concurrent_roots()) {
            types = ShenandoahRootVerifier::combine(ShenandoahRootVerifier::JNIHandleRoots, ShenandoahRootVerifier::WeakRoots);
            types = ShenandoahRootVerifier::combine(types, ShenandoahRootVerifier::CLDGRoots);
            types = ShenandoahRootVerifier::combine(types, ShenandoahRootVerifier::StringDedupRoots);
          }

          if (ShenandoahConcurrentRoots::should_do_concurrent_class_unloading()) {
            types = ShenandoahRootVerifier::combine(types, ShenandoahRootVerifier::CodeRoots);
          }
          verifier()->verify_roots_no_forwarded_except(types);
        }
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
    // If this cycle was updating references, we need to keep the has_forwarded_objects
    // flag on, for subsequent phases to deal with it.
    concurrent_mark()->cancel();
    set_concurrent_mark_in_progress(false);

    if (process_references()) {
      // Abandon reference processing right away: pre-cleaning must have failed.
      ReferenceProcessor *rp = ref_processor();
      rp->disable_discovery();
      rp->abandon_partial_discovery();
      rp->verify_no_references_recorded();
    }
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

void ShenandoahHeap::op_cleanup_early() {
  free_set()->recycle_trash();
}

void ShenandoahHeap::op_cleanup_complete() {
  free_set()->recycle_trash();
}

class ShenandoahConcurrentRootsEvacUpdateTask : public AbstractGangTask {
private:
  ShenandoahVMRoots<true /*concurrent*/>        _vm_roots;
  ShenandoahClassLoaderDataRoots<true /*concurrent*/, false /*single threaded*/> _cld_roots;

public:
  ShenandoahConcurrentRootsEvacUpdateTask(ShenandoahPhaseTimings::Phase phase) :
    AbstractGangTask("Shenandoah Evacuate/Update Concurrent Strong Roots Task"),
    _vm_roots(phase),
    _cld_roots(phase, ShenandoahHeap::heap()->workers()->active_workers()) {}

  void work(uint worker_id) {
    ShenandoahConcurrentWorkerSession worker_session(worker_id);
    ShenandoahEvacOOMScope oom;
    {
      // vm_roots and weak_roots are OopStorage backed roots, concurrent iteration
      // may race against OopStorage::release() calls.
      ShenandoahEvacUpdateOopStorageRootsClosure cl;
      _vm_roots.oops_do<ShenandoahEvacUpdateOopStorageRootsClosure>(&cl, worker_id);
    }

    {
      ShenandoahEvacuateUpdateRootsClosure<> cl;
      CLDToOopClosure clds(&cl, ClassLoaderData::_claim_strong);
      _cld_roots.cld_do(&clds, worker_id);
    }
  }
};

class ShenandoahEvacUpdateCleanupOopStorageRootsClosure : public BasicOopIterateClosure {
private:
  ShenandoahHeap* const _heap;
  ShenandoahMarkingContext* const _mark_context;
  bool  _evac_in_progress;
  Thread* const _thread;
  size_t  _dead_counter;

public:
  ShenandoahEvacUpdateCleanupOopStorageRootsClosure();
  void do_oop(oop* p);
  void do_oop(narrowOop* p);

  size_t dead_counter() const;
  void reset_dead_counter();
};

ShenandoahEvacUpdateCleanupOopStorageRootsClosure::ShenandoahEvacUpdateCleanupOopStorageRootsClosure() :
  _heap(ShenandoahHeap::heap()),
  _mark_context(ShenandoahHeap::heap()->marking_context()),
  _evac_in_progress(ShenandoahHeap::heap()->is_evacuation_in_progress()),
  _thread(Thread::current()),
  _dead_counter(0) {
}

void ShenandoahEvacUpdateCleanupOopStorageRootsClosure::do_oop(oop* p) {
  const oop obj = RawAccess<>::oop_load(p);
  if (!CompressedOops::is_null(obj)) {
    if (!_mark_context->is_marked(obj)) {
      shenandoah_assert_correct(p, obj);
      oop old = Atomic::cmpxchg(p, obj, oop(NULL));
      if (obj == old) {
        _dead_counter ++;
      }
    } else if (_evac_in_progress && _heap->in_collection_set(obj)) {
      oop resolved = ShenandoahBarrierSet::resolve_forwarded_not_null(obj);
      if (resolved == obj) {
        resolved = _heap->evacuate_object(obj, _thread);
      }
      Atomic::cmpxchg(p, obj, resolved);
      assert(_heap->cancelled_gc() ||
             _mark_context->is_marked(resolved) && !_heap->in_collection_set(resolved),
             "Sanity");
    }
  }
}

void ShenandoahEvacUpdateCleanupOopStorageRootsClosure::do_oop(narrowOop* p) {
  ShouldNotReachHere();
}

size_t ShenandoahEvacUpdateCleanupOopStorageRootsClosure::dead_counter() const {
  return _dead_counter;
}

void ShenandoahEvacUpdateCleanupOopStorageRootsClosure::reset_dead_counter() {
  _dead_counter = 0;
}

class ShenandoahIsCLDAliveClosure : public CLDClosure {
public:
  void do_cld(ClassLoaderData* cld) {
    cld->is_alive();
  }
};

class ShenandoahIsNMethodAliveClosure: public NMethodClosure {
public:
  void do_nmethod(nmethod* n) {
    n->is_unloading();
  }
};

// This task not only evacuates/updates marked weak roots, but also "NULL"
// dead weak roots.
class ShenandoahConcurrentWeakRootsEvacUpdateTask : public AbstractGangTask {
private:
  ShenandoahWeakRoot<true /*concurrent*/>  _jni_roots;
  ShenandoahWeakRoot<true /*concurrent*/>  _string_table_roots;
  ShenandoahWeakRoot<true /*concurrent*/>  _resolved_method_table_roots;
  ShenandoahWeakRoot<true /*concurrent*/>  _vm_roots;

  // Roots related to concurrent class unloading
  ShenandoahClassLoaderDataRoots<true /* concurrent */, false /* single thread*/>
                                           _cld_roots;
  ShenandoahConcurrentNMethodIterator      _nmethod_itr;
  ShenandoahConcurrentStringDedupRoots     _dedup_roots;
  bool                                     _concurrent_class_unloading;

public:
  ShenandoahConcurrentWeakRootsEvacUpdateTask(ShenandoahPhaseTimings::Phase phase) :
    AbstractGangTask("Shenandoah Concurrent Weak Root Task"),
    _jni_roots(OopStorageSet::jni_weak(), phase, ShenandoahPhaseTimings::JNIWeakRoots),
    _string_table_roots(OopStorageSet::string_table_weak(), phase, ShenandoahPhaseTimings::StringTableRoots),
    _resolved_method_table_roots(OopStorageSet::resolved_method_table_weak(), phase, ShenandoahPhaseTimings::ResolvedMethodTableRoots),
    _vm_roots(OopStorageSet::vm_weak(), phase, ShenandoahPhaseTimings::VMWeakRoots),
    _cld_roots(phase, ShenandoahHeap::heap()->workers()->active_workers()),
    _nmethod_itr(ShenandoahCodeRoots::table()),
    _dedup_roots(phase),
    _concurrent_class_unloading(ShenandoahConcurrentRoots::should_do_concurrent_class_unloading()) {
    StringTable::reset_dead_counter();
    ResolvedMethodTable::reset_dead_counter();
    if (_concurrent_class_unloading) {
      MutexLocker mu(CodeCache_lock, Mutex::_no_safepoint_check_flag);
      _nmethod_itr.nmethods_do_begin();
    }
  }

  ~ShenandoahConcurrentWeakRootsEvacUpdateTask() {
    StringTable::finish_dead_counter();
    ResolvedMethodTable::finish_dead_counter();
    if (_concurrent_class_unloading) {
      MutexLocker mu(CodeCache_lock, Mutex::_no_safepoint_check_flag);
      _nmethod_itr.nmethods_do_end();
    }
  }

  void work(uint worker_id) {
    ShenandoahConcurrentWorkerSession worker_session(worker_id);
    {
      ShenandoahEvacOOMScope oom;
      // jni_roots and weak_roots are OopStorage backed roots, concurrent iteration
      // may race against OopStorage::release() calls.
      ShenandoahEvacUpdateCleanupOopStorageRootsClosure cl;
      _jni_roots.oops_do(&cl, worker_id);
      _vm_roots.oops_do(&cl, worker_id);

      cl.reset_dead_counter();
      _string_table_roots.oops_do(&cl, worker_id);
      StringTable::inc_dead_counter(cl.dead_counter());

      cl.reset_dead_counter();
      _resolved_method_table_roots.oops_do(&cl, worker_id);
      ResolvedMethodTable::inc_dead_counter(cl.dead_counter());

      // String dedup weak roots
      ShenandoahForwardedIsAliveClosure is_alive;
      ShenandoahEvacuateUpdateRootsClosure<MO_RELEASE> keep_alive;
      _dedup_roots.oops_do(&is_alive, &keep_alive, worker_id);
    }

    // If we are going to perform concurrent class unloading later on, we need to
    // cleanup the weak oops in CLD and determinate nmethod's unloading state, so that we
    // can cleanup immediate garbage sooner.
    if (_concurrent_class_unloading) {
      // Applies ShenandoahIsCLDAlive closure to CLDs, native barrier will either NULL the
      // CLD's holder or evacuate it.
      ShenandoahIsCLDAliveClosure is_cld_alive;
      _cld_roots.cld_do(&is_cld_alive, worker_id);

      // Applies ShenandoahIsNMethodAliveClosure to registered nmethods.
      // The closure calls nmethod->is_unloading(). The is_unloading
      // state is cached, therefore, during concurrent class unloading phase,
      // we will not touch the metadata of unloading nmethods
      ShenandoahIsNMethodAliveClosure is_nmethod_alive;
      _nmethod_itr.nmethods_do(&is_nmethod_alive);
    }
  }
};

void ShenandoahHeap::op_weak_roots() {
  if (is_concurrent_weak_root_in_progress()) {
    // Concurrent weak root processing
    {
      ShenandoahTimingsTracker t(ShenandoahPhaseTimings::conc_weak_roots_work);
      ShenandoahGCWorkerPhase worker_phase(ShenandoahPhaseTimings::conc_weak_roots_work);
      ShenandoahConcurrentWeakRootsEvacUpdateTask task(ShenandoahPhaseTimings::conc_weak_roots_work);
      workers()->run_task(&task);
      if (!ShenandoahConcurrentRoots::should_do_concurrent_class_unloading()) {
        set_concurrent_weak_root_in_progress(false);
      }
    }

    // Perform handshake to flush out dead oops
    {
      ShenandoahTimingsTracker t(ShenandoahPhaseTimings::conc_weak_roots_rendezvous);
      ShenandoahRendezvousClosure cl;
      Handshake::execute(&cl);
    }
  }
}

void ShenandoahHeap::op_class_unloading() {
  assert (is_concurrent_weak_root_in_progress() &&
          ShenandoahConcurrentRoots::should_do_concurrent_class_unloading(),
          "Checked by caller");
  _unloader.unload();
  set_concurrent_weak_root_in_progress(false);
}

void ShenandoahHeap::op_strong_roots() {
  assert(is_concurrent_strong_root_in_progress(), "Checked by caller");
  ShenandoahConcurrentRootsEvacUpdateTask task(ShenandoahPhaseTimings::conc_strong_roots);
  workers()->run_task(&task);
  set_concurrent_strong_root_in_progress(false);
}

class ShenandoahResetUpdateRegionStateClosure : public ShenandoahHeapRegionClosure {
private:
  ShenandoahMarkingContext* const _ctx;
public:
  ShenandoahResetUpdateRegionStateClosure() : _ctx(ShenandoahHeap::heap()->marking_context()) {}

  void heap_region_do(ShenandoahHeapRegion* r) {
    if (r->is_active()) {
      // Reset live data and set TAMS optimistically. We would recheck these under the pause
      // anyway to capture any updates that happened since now.
      r->clear_live_data();
      _ctx->capture_top_at_mark_start(r);
    }
  }

  bool is_thread_safe() { return true; }
};

void ShenandoahHeap::op_reset() {
  if (ShenandoahPacing) {
    pacer()->setup_for_reset();
  }
  reset_mark_bitmap();

  ShenandoahResetUpdateRegionStateClosure cl;
  parallel_heap_region_iterate(&cl);
}

void ShenandoahHeap::op_preclean() {
  if (ShenandoahPacing) {
    pacer()->setup_for_preclean();
  }
  concurrent_mark()->preclean_weak_refs();
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

  if (metrics.is_good_progress()) {
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

      if (!has_forwarded_objects() && ShenandoahConcurrentRoots::can_do_concurrent_class_unloading()) {
        // Disarm nmethods that armed for concurrent mark. On normal cycle, it would
        // be disarmed while conc-roots phase is running.
        // TODO: Call op_conc_roots() here instead
        ShenandoahCodeRoots::disarm_nmethods();
      }

      op_cleanup_early();

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
        //
        // Before doing that, we need to make sure we never had any cset-pinned
        // regions. This may happen if allocation failure happened when evacuating
        // the about-to-be-pinned object, oom-evac protocol left the object in
        // the collection set, and then the pin reached the cset region. If we continue
        // the cycle here, we would trash the cset and alive objects in it. To avoid
        // it, we fail degeneration right away and slide into Full GC to recover.

        {
          sync_pinned_region_status();
          collection_set()->clear_current_index();

          ShenandoahHeapRegion* r;
          while ((r = collection_set()->next()) != NULL) {
            if (r->is_pinned()) {
              cancel_gc(GCCause::_shenandoah_upgrade_to_full_gc);
              op_degenerated_fail();
              return;
            }
          }

          collection_set()->clear_current_index();
        }

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

      op_cleanup_complete();
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

  // Check for futility and fail. There is no reason to do several back-to-back Degenerated cycles,
  // because that probably means the heap is overloaded and/or fragmented.
  if (!metrics.is_good_progress()) {
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

void ShenandoahHeap::force_satb_flush_all_threads() {
  if (!is_concurrent_mark_in_progress()) {
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

void ShenandoahHeap::set_evacuation_in_progress(bool in_progress) {
  assert(ShenandoahSafepoint::is_at_shenandoah_safepoint(), "Only call this at safepoint");
  set_gc_state_mask(EVACUATION, in_progress);
}

void ShenandoahHeap::set_concurrent_strong_root_in_progress(bool in_progress) {
  assert(ShenandoahConcurrentRoots::can_do_concurrent_roots(), "Why set the flag?");
  if (in_progress) {
    _concurrent_strong_root_in_progress.set();
  } else {
    _concurrent_strong_root_in_progress.unset();
  }
}

void ShenandoahHeap::set_concurrent_weak_root_in_progress(bool in_progress) {
  assert(ShenandoahConcurrentRoots::can_do_concurrent_roots(), "Why set the flag?");
  if (in_progress) {
    _concurrent_weak_root_in_progress.set();
  } else {
    _concurrent_weak_root_in_progress.unset();
  }
}

void ShenandoahHeap::ref_processing_init() {
  assert(_max_workers > 0, "Sanity");

  _ref_processor =
    new ReferenceProcessor(&_subject_to_discovery,  // is_subject_to_discovery
                           _ref_proc_mt_processing, // MT processing
                           _max_workers,            // Degree of MT processing
                           _ref_proc_mt_discovery,  // MT discovery
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
    if (Thread::current()->is_Java_thread()) {
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

void ShenandoahHeap::stw_unload_classes(bool full_gc) {
  if (!unload_classes()) return;

  // Unload classes and purge SystemDictionary.
  {
    ShenandoahGCPhase phase(full_gc ?
                            ShenandoahPhaseTimings::full_gc_purge_class_unload :
                            ShenandoahPhaseTimings::purge_class_unload);
    bool purged_class = SystemDictionary::do_unloading(gc_timer());

    ShenandoahIsAliveSelector is_alive;
    uint num_workers = _workers->active_workers();
    ShenandoahClassUnloadingTask unlink_task(is_alive.is_alive_closure(), num_workers, purged_class);
    _workers->run_task(&unlink_task);
  }

  {
    ShenandoahGCPhase phase(full_gc ?
                            ShenandoahPhaseTimings::full_gc_purge_cldg :
                            ShenandoahPhaseTimings::purge_cldg);
    ClassLoaderDataGraph::purge();
  }
  // Resize and verify metaspace
  MetaspaceGC::compute_new_size();
  MetaspaceUtils::verify_metrics();
}

// Weak roots are either pre-evacuated (final mark) or updated (final updaterefs),
// so they should not have forwarded oops.
// However, we do need to "null" dead oops in the roots, if can not be done
// in concurrent cycles.
void ShenandoahHeap::stw_process_weak_roots(bool full_gc) {
  ShenandoahGCPhase root_phase(full_gc ?
                               ShenandoahPhaseTimings::full_gc_purge :
                               ShenandoahPhaseTimings::purge);
  uint num_workers = _workers->active_workers();
  ShenandoahPhaseTimings::Phase timing_phase = full_gc ?
                                               ShenandoahPhaseTimings::full_gc_purge_weak_par :
                                               ShenandoahPhaseTimings::purge_weak_par;
  ShenandoahGCPhase phase(timing_phase);
  ShenandoahGCWorkerPhase worker_phase(timing_phase);

  // Cleanup weak roots
  if (has_forwarded_objects()) {
    ShenandoahForwardedIsAliveClosure is_alive;
    ShenandoahUpdateRefsClosure keep_alive;
    ShenandoahParallelWeakRootsCleaningTask<ShenandoahForwardedIsAliveClosure, ShenandoahUpdateRefsClosure>
      cleaning_task(timing_phase, &is_alive, &keep_alive, num_workers, !ShenandoahConcurrentRoots::should_do_concurrent_class_unloading());
    _workers->run_task(&cleaning_task);
  } else {
    ShenandoahIsAliveClosure is_alive;
#ifdef ASSERT
    ShenandoahAssertNotForwardedClosure verify_cl;
    ShenandoahParallelWeakRootsCleaningTask<ShenandoahIsAliveClosure, ShenandoahAssertNotForwardedClosure>
      cleaning_task(timing_phase, &is_alive, &verify_cl, num_workers, !ShenandoahConcurrentRoots::should_do_concurrent_class_unloading());
#else
    ShenandoahParallelWeakRootsCleaningTask<ShenandoahIsAliveClosure, DoNothingClosure>
      cleaning_task(timing_phase, &is_alive, &do_nothing_cl, num_workers, !ShenandoahConcurrentRoots::should_do_concurrent_class_unloading());
#endif
    _workers->run_task(&cleaning_task);
  }
}

void ShenandoahHeap::parallel_cleaning(bool full_gc) {
  assert(SafepointSynchronize::is_at_safepoint(), "Must be at a safepoint");
  stw_process_weak_roots(full_gc);
  if (!ShenandoahConcurrentRoots::should_do_concurrent_class_unloading()) {
    stw_unload_classes(full_gc);
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
  return Atomic::load_acquire(&_bytes_allocated_since_gc_start);
}

void ShenandoahHeap::reset_bytes_allocated_since_gc_start() {
  Atomic::release_store_fence(&_bytes_allocated_since_gc_start, (size_t)0);
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
  ShenandoahCodeRoots::register_nmethod(nm);
}

void ShenandoahHeap::unregister_nmethod(nmethod* nm) {
  ShenandoahCodeRoots::unregister_nmethod(nm);
}

void ShenandoahHeap::flush_nmethod(nmethod* nm) {
  ShenandoahCodeRoots::flush_nmethod(nm);
}

oop ShenandoahHeap::pin_object(JavaThread* thr, oop o) {
  heap_region_containing(o)->record_pin();
  return o;
}

void ShenandoahHeap::unpin_object(JavaThread* thr, oop o) {
  heap_region_containing(o)->record_unpin();
}

void ShenandoahHeap::sync_pinned_region_status() {
  ShenandoahHeapLocker locker(lock());

  for (size_t i = 0; i < num_regions(); i++) {
    ShenandoahHeapRegion *r = get_region(i);
    if (r->is_active()) {
      if (r->is_pinned()) {
        if (r->pin_count() == 0) {
          r->make_unpinned();
        }
      } else {
        if (r->pin_count() > 0) {
          r->make_pinned();
        }
      }
    }
  }

  assert_pinned_region_status();
}

#ifdef ASSERT
void ShenandoahHeap::assert_pinned_region_status() {
  for (size_t i = 0; i < num_regions(); i++) {
    ShenandoahHeapRegion* r = get_region(i);
    assert((r->is_pinned() && r->pin_count() > 0) || (!r->is_pinned() && r->pin_count() == 0),
           "Region " SIZE_FORMAT " pinning status is inconsistent", i);
  }
}
#endif

ConcurrentGCTimer* ShenandoahHeap::gc_timer() const {
  return _gc_timer;
}

void ShenandoahHeap::prepare_concurrent_roots() {
  assert(SafepointSynchronize::is_at_safepoint(), "Must be at a safepoint");
  if (ShenandoahConcurrentRoots::should_do_concurrent_roots()) {
    set_concurrent_strong_root_in_progress(!collection_set()->is_empty());
    set_concurrent_weak_root_in_progress(true);
  }
}

void ShenandoahHeap::prepare_concurrent_unloading() {
  assert(SafepointSynchronize::is_at_safepoint(), "Must be at a safepoint");
  if (ShenandoahConcurrentRoots::should_do_concurrent_class_unloading()) {
    _unloader.prepare();
  }
}

void ShenandoahHeap::finish_concurrent_unloading() {
  assert(SafepointSynchronize::is_at_safepoint(), "Must be at a safepoint");
  if (ShenandoahConcurrentRoots::should_do_concurrent_class_unloading()) {
    _unloader.finish();
  }
}

#ifdef ASSERT
void ShenandoahHeap::assert_gc_workers(uint nworkers) {
  assert(nworkers > 0 && nworkers <= max_workers(), "Sanity");

  if (ShenandoahSafepoint::is_at_shenandoah_safepoint()) {
    if (UseDynamicNumberOfGCThreads) {
      assert(nworkers <= ParallelGCThreads, "Cannot use more than it has");
    } else {
      // Use ParallelGCThreads inside safepoints
      assert(nworkers == ParallelGCThreads, "Use ParallelGCThreads within safepoints");
    }
  } else {
    if (UseDynamicNumberOfGCThreads) {
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
      HeapWord* update_watermark = r->get_update_watermark();
      assert (update_watermark >= r->bottom(), "sanity");
      if (r->is_active() && !r->is_cset()) {
        _heap->marked_object_oop_iterate(r, &cl, update_watermark);
      }
      if (ShenandoahPacing) {
        _heap->pacer()->report_updaterefs(pointer_delta(update_watermark, r->bottom()));
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

  {
    ShenandoahGCPhase phase(ShenandoahPhaseTimings::init_update_refs_retire_gclabs);
    retire_and_reset_gclabs();
  }

  if (ShenandoahVerify) {
    if (!is_degenerated_gc_in_progress()) {
      verifier()->verify_roots_in_to_space_except(ShenandoahRootVerifier::ThreadRoots);
    }
    verifier()->verify_before_updaterefs();
  }

  set_update_refs_in_progress(true);

  _update_refs_iterator.reset();

  if (ShenandoahPacing) {
    pacer()->setup_for_updaterefs();
  }
}

class ShenandoahFinalUpdateRefsUpdateRegionStateClosure : public ShenandoahHeapRegionClosure {
private:
  ShenandoahHeapLock* const _lock;

public:
  ShenandoahFinalUpdateRefsUpdateRegionStateClosure() : _lock(ShenandoahHeap::heap()->lock()) {}

  void heap_region_do(ShenandoahHeapRegion* r) {
    // Drop unnecessary "pinned" state from regions that does not have CP marks
    // anymore, as this would allow trashing them.

    if (r->is_active()) {
      if (r->is_pinned()) {
        if (r->pin_count() == 0) {
          ShenandoahHeapLocker locker(_lock);
          r->make_unpinned();
        }
      } else {
        if (r->pin_count() > 0) {
          ShenandoahHeapLocker locker(_lock);
          r->make_pinned();
        }
      }
    }
  }

  bool is_thread_safe() { return true; }
};

void ShenandoahHeap::op_final_updaterefs() {
  assert(ShenandoahSafepoint::is_at_shenandoah_safepoint(), "must be at safepoint");

  finish_concurrent_unloading();

  // Check if there is left-over work, and finish it
  if (_update_refs_iterator.has_next()) {
    ShenandoahGCPhase phase(ShenandoahPhaseTimings::final_update_refs_finish_work);

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

  if (ShenandoahVerify && !is_degenerated_gc_in_progress()) {
    verifier()->verify_roots_in_to_space_except(ShenandoahRootVerifier::ThreadRoots);
  }

  if (is_degenerated_gc_in_progress()) {
    concurrent_mark()->update_roots(ShenandoahPhaseTimings::degen_gc_update_roots);
  } else {
    concurrent_mark()->update_thread_roots(ShenandoahPhaseTimings::final_update_refs_roots);
  }

  // Has to be done before cset is clear
  if (ShenandoahVerify) {
    verifier()->verify_roots_in_to_space();
  }

  {
    ShenandoahGCPhase phase(ShenandoahPhaseTimings::final_update_refs_update_region_states);
    ShenandoahFinalUpdateRefsUpdateRegionStateClosure cl;
    parallel_heap_region_iterate(&cl);

    assert_pinned_region_status();
  }

  {
    ShenandoahGCPhase phase(ShenandoahPhaseTimings::final_update_refs_trash_cset);
    trash_cset_regions();
  }

  set_has_forwarded_objects(false);
  set_update_refs_in_progress(false);

  if (ShenandoahVerify) {
    verifier()->verify_after_updaterefs();
  }

  if (VerifyAfterGC) {
    Universe::verify();
  }

  {
    ShenandoahGCPhase phase(ShenandoahPhaseTimings::final_update_refs_rebuild_freeset);
    ShenandoahHeapLocker locker(lock());
    _free_set->rebuild();
  }
}

void ShenandoahHeap::print_extended_on(outputStream *st) const {
  print_on(st);
  print_heap_regions_on(st);
}

bool ShenandoahHeap::is_bitmap_slice_committed(ShenandoahHeapRegion* r, bool skip_self) {
  size_t slice = r->index() / _bitmap_regions_per_slice;

  size_t regions_from = _bitmap_regions_per_slice * slice;
  size_t regions_to   = MIN2(num_regions(), _bitmap_regions_per_slice * (slice + 1));
  for (size_t g = regions_from; g < regions_to; g++) {
    assert (g / _bitmap_regions_per_slice == slice, "same slice");
    if (skip_self && g == r->index()) continue;
    if (get_region(g)->is_committed()) {
      return true;
    }
  }
  return false;
}

bool ShenandoahHeap::commit_bitmap_slice(ShenandoahHeapRegion* r) {
  shenandoah_assert_heaplocked();

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
  size_t slice = r->index() / _bitmap_regions_per_slice;
  size_t off = _bitmap_bytes_per_slice * slice;
  size_t len = _bitmap_bytes_per_slice;
  char* start = (char*) _bitmap_region.start() + off;

  if (!os::commit_memory(start, len, false)) {
    return false;
  }

  if (AlwaysPreTouch) {
    os::pretouch_memory(start, start + len, _pretouch_bitmap_page_size);
  }

  return true;
}

bool ShenandoahHeap::uncommit_bitmap_slice(ShenandoahHeapRegion *r) {
  shenandoah_assert_heaplocked();

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
  size_t slice = r->index() / _bitmap_regions_per_slice;
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
  ShenandoahTimingsTracker timing(ShenandoahPhaseTimings::init_mark_gross);

  try_inject_alloc_failure();
  VM_ShenandoahInitMark op;
  VMThread::execute(&op); // jump to entry_init_mark() under safepoint
}

void ShenandoahHeap::vmop_entry_final_mark() {
  TraceCollectorStats tcs(monitoring_support()->stw_collection_counters());
  ShenandoahTimingsTracker timing(ShenandoahPhaseTimings::final_mark_gross);

  try_inject_alloc_failure();
  VM_ShenandoahFinalMarkStartEvac op;
  VMThread::execute(&op); // jump to entry_final_mark under safepoint
}

void ShenandoahHeap::vmop_entry_init_updaterefs() {
  TraceCollectorStats tcs(monitoring_support()->stw_collection_counters());
  ShenandoahTimingsTracker timing(ShenandoahPhaseTimings::init_update_refs_gross);

  try_inject_alloc_failure();
  VM_ShenandoahInitUpdateRefs op;
  VMThread::execute(&op);
}

void ShenandoahHeap::vmop_entry_final_updaterefs() {
  TraceCollectorStats tcs(monitoring_support()->stw_collection_counters());
  ShenandoahTimingsTracker timing(ShenandoahPhaseTimings::final_update_refs_gross);

  try_inject_alloc_failure();
  VM_ShenandoahFinalUpdateRefs op;
  VMThread::execute(&op);
}

void ShenandoahHeap::vmop_entry_full(GCCause::Cause cause) {
  TraceCollectorStats tcs(monitoring_support()->full_stw_collection_counters());
  ShenandoahTimingsTracker timing(ShenandoahPhaseTimings::full_gc_gross);

  try_inject_alloc_failure();
  VM_ShenandoahFullGC op(cause);
  VMThread::execute(&op);
}

void ShenandoahHeap::vmop_degenerated(ShenandoahDegenPoint point) {
  TraceCollectorStats tcs(monitoring_support()->full_stw_collection_counters());
  ShenandoahTimingsTracker timing(ShenandoahPhaseTimings::degen_gc_gross);

  VM_ShenandoahDegeneratedGC degenerated_gc((int)point);
  VMThread::execute(&degenerated_gc);
}

void ShenandoahHeap::entry_init_mark() {
  const char* msg = init_mark_event_message();
  ShenandoahPausePhase gc_phase(msg, ShenandoahPhaseTimings::init_mark);
  EventMark em("%s", msg);

  ShenandoahWorkerScope scope(workers(),
                              ShenandoahWorkerPolicy::calc_workers_for_init_marking(),
                              "init marking");

  op_init_mark();
}

void ShenandoahHeap::entry_final_mark() {
  const char* msg = final_mark_event_message();
  ShenandoahPausePhase gc_phase(msg, ShenandoahPhaseTimings::final_mark);
  EventMark em("%s", msg);

  ShenandoahWorkerScope scope(workers(),
                              ShenandoahWorkerPolicy::calc_workers_for_final_marking(),
                              "final marking");

  op_final_mark();
}

void ShenandoahHeap::entry_init_updaterefs() {
  static const char* msg = "Pause Init Update Refs";
  ShenandoahPausePhase gc_phase(msg, ShenandoahPhaseTimings::init_update_refs);
  EventMark em("%s", msg);

  // No workers used in this phase, no setup required

  op_init_updaterefs();
}

void ShenandoahHeap::entry_final_updaterefs() {
  static const char* msg = "Pause Final Update Refs";
  ShenandoahPausePhase gc_phase(msg, ShenandoahPhaseTimings::final_update_refs);
  EventMark em("%s", msg);

  ShenandoahWorkerScope scope(workers(),
                              ShenandoahWorkerPolicy::calc_workers_for_final_update_ref(),
                              "final reference update");

  op_final_updaterefs();
}

void ShenandoahHeap::entry_full(GCCause::Cause cause) {
  static const char* msg = "Pause Full";
  ShenandoahPausePhase gc_phase(msg, ShenandoahPhaseTimings::full_gc, true /* log_heap_usage */);
  EventMark em("%s", msg);

  ShenandoahWorkerScope scope(workers(),
                              ShenandoahWorkerPolicy::calc_workers_for_fullgc(),
                              "full gc");

  op_full(cause);
}

void ShenandoahHeap::entry_degenerated(int point) {
  ShenandoahDegenPoint dpoint = (ShenandoahDegenPoint)point;
  const char* msg = degen_event_message(dpoint);
  ShenandoahPausePhase gc_phase(msg, ShenandoahPhaseTimings::degen_gc, true /* log_heap_usage */);
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
  ShenandoahConcurrentPhase gc_phase(msg, ShenandoahPhaseTimings::conc_mark);
  EventMark em("%s", msg);

  ShenandoahWorkerScope scope(workers(),
                              ShenandoahWorkerPolicy::calc_workers_for_conc_marking(),
                              "concurrent marking");

  try_inject_alloc_failure();
  op_mark();
}

void ShenandoahHeap::entry_evac() {
  TraceCollectorStats tcs(monitoring_support()->concurrent_collection_counters());

  static const char* msg = "Concurrent evacuation";
  ShenandoahConcurrentPhase gc_phase(msg, ShenandoahPhaseTimings::conc_evac);
  EventMark em("%s", msg);

  ShenandoahWorkerScope scope(workers(),
                              ShenandoahWorkerPolicy::calc_workers_for_conc_evac(),
                              "concurrent evacuation");

  try_inject_alloc_failure();
  op_conc_evac();
}

void ShenandoahHeap::entry_updaterefs() {
  static const char* msg = "Concurrent update references";
  ShenandoahConcurrentPhase gc_phase(msg, ShenandoahPhaseTimings::conc_update_refs);
  EventMark em("%s", msg);

  ShenandoahWorkerScope scope(workers(),
                              ShenandoahWorkerPolicy::calc_workers_for_conc_update_ref(),
                              "concurrent reference update");

  try_inject_alloc_failure();
  op_updaterefs();
}

void ShenandoahHeap::entry_weak_roots() {
  static const char* msg = "Concurrent weak roots";
  ShenandoahConcurrentPhase gc_phase(msg, ShenandoahPhaseTimings::conc_weak_roots);
  EventMark em("%s", msg);

  ShenandoahWorkerScope scope(workers(),
                              ShenandoahWorkerPolicy::calc_workers_for_conc_root_processing(),
                              "concurrent weak root");

  try_inject_alloc_failure();
  op_weak_roots();
}

void ShenandoahHeap::entry_class_unloading() {
  static const char* msg = "Concurrent class unloading";
  ShenandoahConcurrentPhase gc_phase(msg, ShenandoahPhaseTimings::conc_class_unload);
  EventMark em("%s", msg);

  ShenandoahWorkerScope scope(workers(),
                              ShenandoahWorkerPolicy::calc_workers_for_conc_root_processing(),
                              "concurrent class unloading");

  try_inject_alloc_failure();
  op_class_unloading();
}

void ShenandoahHeap::entry_strong_roots() {
  static const char* msg = "Concurrent strong roots";
  ShenandoahConcurrentPhase gc_phase(msg, ShenandoahPhaseTimings::conc_strong_roots);
  EventMark em("%s", msg);

  ShenandoahGCWorkerPhase worker_phase(ShenandoahPhaseTimings::conc_strong_roots);

  ShenandoahWorkerScope scope(workers(),
                              ShenandoahWorkerPolicy::calc_workers_for_conc_root_processing(),
                              "concurrent strong root");

  try_inject_alloc_failure();
  op_strong_roots();
}

void ShenandoahHeap::entry_cleanup_early() {
  static const char* msg = "Concurrent cleanup";
  ShenandoahConcurrentPhase gc_phase(msg, ShenandoahPhaseTimings::conc_cleanup_early, true /* log_heap_usage */);
  EventMark em("%s", msg);

  // This phase does not use workers, no need for setup

  try_inject_alloc_failure();
  op_cleanup_early();
}

void ShenandoahHeap::entry_cleanup_complete() {
  static const char* msg = "Concurrent cleanup";
  ShenandoahConcurrentPhase gc_phase(msg, ShenandoahPhaseTimings::conc_cleanup_complete, true /* log_heap_usage */);
  EventMark em("%s", msg);

  // This phase does not use workers, no need for setup

  try_inject_alloc_failure();
  op_cleanup_complete();
}

void ShenandoahHeap::entry_reset() {
  static const char* msg = "Concurrent reset";
  ShenandoahConcurrentPhase gc_phase(msg, ShenandoahPhaseTimings::conc_reset);
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
    ShenandoahConcurrentPhase gc_phase(msg, ShenandoahPhaseTimings::conc_preclean);
    EventMark em("%s", msg);

    ShenandoahWorkerScope scope(workers(),
                                ShenandoahWorkerPolicy::calc_workers_for_conc_preclean(),
                                "concurrent preclean",
                                /* check_workers = */ false);

    try_inject_alloc_failure();
    op_preclean();
  }
}

void ShenandoahHeap::entry_uncommit(double shrink_before) {
  static const char *msg = "Concurrent uncommit";
  ShenandoahConcurrentPhase gc_phase(msg, ShenandoahPhaseTimings::conc_uncommit, true /* log_heap_usage */);
  EventMark em("%s", msg);

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
  assert(!has_forwarded_objects(), "Should not have forwarded objects here");

  bool proc_refs = process_references();
  bool unload_cls = unload_classes();

  if (proc_refs && unload_cls) {
    return "Pause Init Mark (process weakrefs) (unload classes)";
  } else if (proc_refs) {
    return "Pause Init Mark (process weakrefs)";
  } else if (unload_cls) {
    return "Pause Init Mark (unload classes)";
  } else {
    return "Pause Init Mark";
  }
}

const char* ShenandoahHeap::final_mark_event_message() const {
  assert(!has_forwarded_objects(), "Should not have forwarded objects here");

  bool proc_refs = process_references();
  bool unload_cls = unload_classes();

  if (proc_refs && unload_cls) {
    return "Pause Final Mark (process weakrefs) (unload classes)";
  } else if (proc_refs) {
    return "Pause Final Mark (process weakrefs)";
  } else if (unload_cls) {
    return "Pause Final Mark (unload classes)";
  } else {
    return "Pause Final Mark";
  }
}

const char* ShenandoahHeap::conc_mark_event_message() const {
  assert(!has_forwarded_objects(), "Should not have forwarded objects here");

  bool proc_refs = process_references();
  bool unload_cls = unload_classes();

  if (proc_refs && unload_cls) {
    return "Concurrent marking (process weakrefs) (unload classes)";
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

ShenandoahLiveData* ShenandoahHeap::get_liveness_cache(uint worker_id) {
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
  ShenandoahLiveData* ld = _liveness_cache[worker_id];
  for (uint i = 0; i < num_regions(); i++) {
    ShenandoahLiveData live = ld[i];
    if (live > 0) {
      ShenandoahHeapRegion* r = get_region(i);
      r->increase_live_data_gc_words(live);
      ld[i] = 0;
    }
  }
}
