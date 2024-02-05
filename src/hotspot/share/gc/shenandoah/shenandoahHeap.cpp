/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2013, 2022, Red Hat, Inc. All rights reserved.
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

#include "gc/shared/classUnloadingContext.hpp"
#include "gc/shared/gcArguments.hpp"
#include "gc/shared/gcTimer.hpp"
#include "gc/shared/gcTraceTime.inline.hpp"
#include "gc/shared/locationPrinter.inline.hpp"
#include "gc/shared/memAllocator.hpp"
#include "gc/shared/plab.hpp"
#include "gc/shared/tlab_globals.hpp"

#include "gc/shenandoah/shenandoahBarrierSet.hpp"
#include "gc/shenandoah/shenandoahClosures.inline.hpp"
#include "gc/shenandoah/shenandoahCollectionSet.hpp"
#include "gc/shenandoah/shenandoahCollectorPolicy.hpp"
#include "gc/shenandoah/shenandoahConcurrentMark.hpp"
#include "gc/shenandoah/shenandoahMarkingContext.inline.hpp"
#include "gc/shenandoah/shenandoahControlThread.hpp"
#include "gc/shenandoah/shenandoahFreeSet.hpp"
#include "gc/shenandoah/shenandoahPhaseTimings.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahHeapRegion.inline.hpp"
#include "gc/shenandoah/shenandoahHeapRegionSet.hpp"
#include "gc/shenandoah/shenandoahInitLogger.hpp"
#include "gc/shenandoah/shenandoahMarkingContext.inline.hpp"
#include "gc/shenandoah/shenandoahMemoryPool.hpp"
#include "gc/shenandoah/shenandoahMetrics.hpp"
#include "gc/shenandoah/shenandoahMonitoringSupport.hpp"
#include "gc/shenandoah/shenandoahOopClosures.inline.hpp"
#include "gc/shenandoah/shenandoahPacer.inline.hpp"
#include "gc/shenandoah/shenandoahPadding.hpp"
#include "gc/shenandoah/shenandoahParallelCleaning.inline.hpp"
#include "gc/shenandoah/shenandoahReferenceProcessor.hpp"
#include "gc/shenandoah/shenandoahRootProcessor.inline.hpp"
#include "gc/shenandoah/shenandoahSTWMark.hpp"
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

#include "classfile/systemDictionary.hpp"
#include "code/codeCache.hpp"
#include "memory/classLoaderMetaspace.hpp"
#include "memory/metaspaceUtils.hpp"
#include "nmt/mallocTracker.hpp"
#include "nmt/memTracker.hpp"
#include "oops/compressedOops.inline.hpp"
#include "prims/jvmtiTagMap.hpp"
#include "runtime/atomic.hpp"
#include "runtime/globals.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/java.hpp"
#include "runtime/orderAccess.hpp"
#include "runtime/safepointMechanism.hpp"
#include "runtime/vmThread.hpp"
#include "utilities/events.hpp"
#include "utilities/powerOfTwo.hpp"

class ShenandoahPretouchHeapTask : public WorkerTask {
private:
  ShenandoahRegionIterator _regions;
  const size_t _page_size;
public:
  ShenandoahPretouchHeapTask(size_t page_size) :
    WorkerTask("Shenandoah Pretouch Heap"),
    _page_size(page_size) {}

  virtual void work(uint worker_id) {
    ShenandoahHeapRegion* r = _regions.next();
    while (r != nullptr) {
      if (r->is_committed()) {
        os::pretouch_memory(r->bottom(), r->end(), _page_size);
      }
      r = _regions.next();
    }
  }
};

class ShenandoahPretouchBitmapTask : public WorkerTask {
private:
  ShenandoahRegionIterator _regions;
  char* _bitmap_base;
  const size_t _bitmap_size;
  const size_t _page_size;
public:
  ShenandoahPretouchBitmapTask(char* bitmap_base, size_t bitmap_size, size_t page_size) :
    WorkerTask("Shenandoah Pretouch Bitmap"),
    _bitmap_base(bitmap_base),
    _bitmap_size(bitmap_size),
    _page_size(page_size) {}

  virtual void work(uint worker_id) {
    ShenandoahHeapRegion* r = _regions.next();
    while (r != nullptr) {
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
  assert(_num_regions == (max_byte_size / reg_size_bytes),
         "Regions should cover entire heap exactly: " SIZE_FORMAT " != " SIZE_FORMAT "/" SIZE_FORMAT,
         _num_regions, max_byte_size, reg_size_bytes);

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

  // Default to max heap size.
  _soft_max_size = _num_regions * reg_size_bytes;

  _committed = _initial_size;

  size_t heap_page_size   = UseLargePages ? os::large_page_size() : os::vm_page_size();
  size_t bitmap_page_size = UseLargePages ? os::large_page_size() : os::vm_page_size();
  size_t region_page_size = UseLargePages ? os::large_page_size() : os::vm_page_size();

  //
  // Reserve and commit memory for heap
  //

  ReservedHeapSpace heap_rs = Universe::reserve_heap(max_byte_size, heap_alignment);
  initialize_reserved_region(heap_rs);
  _heap_region = MemRegion((HeapWord*)heap_rs.base(), heap_rs.size() / HeapWordSize);
  _heap_region_special = heap_rs.special();

  assert((((size_t) base()) & ShenandoahHeapRegion::region_size_bytes_mask()) == 0,
         "Misaligned heap: " PTR_FORMAT, p2i(base()));
  os::trace_page_sizes_for_requested_size("Heap",
                                          max_byte_size, heap_alignment,
                                          heap_rs.base(),
                                          heap_rs.size(), heap_rs.page_size());

#if SHENANDOAH_OPTIMIZED_MARKTASK
  // The optimized ShenandoahMarkTask takes some bits away from the full object bits.
  // Fail if we ever attempt to address more than we can.
  if ((uintptr_t)heap_rs.end() >= ShenandoahMarkTask::max_addressable()) {
    FormatBuffer<512> buf("Shenandoah reserved [" PTR_FORMAT ", " PTR_FORMAT") for the heap, \n"
                          "but max object address is " PTR_FORMAT ". Try to reduce heap size, or try other \n"
                          "VM options that allocate heap at lower addresses (HeapBaseMinAddress, AllocateHeapAt, etc).",
                p2i(heap_rs.base()), p2i(heap_rs.end()), ShenandoahMarkTask::max_addressable());
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

  size_t bitmap_size_orig = ShenandoahMarkBitMap::compute_size(heap_rs.size());
  _bitmap_size = align_up(bitmap_size_orig, bitmap_page_size);

  size_t bitmap_bytes_per_region = reg_size_bytes / ShenandoahMarkBitMap::heap_map_factor();

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
  os::trace_page_sizes_for_requested_size("Mark Bitmap",
                                          bitmap_size_orig, bitmap_page_size,
                                          bitmap.base(),
                                          bitmap.size(), bitmap.page_size());
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

  _marking_context = new ShenandoahMarkingContext(_heap_region, _bitmap_region, _num_regions, _max_workers);

  if (ShenandoahVerify) {
    ReservedSpace verify_bitmap(_bitmap_size, bitmap_page_size);
    os::trace_page_sizes_for_requested_size("Verify Bitmap",
                                            bitmap_size_orig, bitmap_page_size,
                                            verify_bitmap.base(),
                                            verify_bitmap.size(), verify_bitmap.page_size());
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
  size_t aux_bitmap_page_size = bitmap_page_size;
#ifdef LINUX
  // In THP "advise" mode, we refrain from advising the system to use large pages
  // since we know these commits will be short lived, and there is no reason to trash
  // the THP area with this bitmap.
  if (UseTransparentHugePages) {
    aux_bitmap_page_size = os::vm_page_size();
  }
#endif
  ReservedSpace aux_bitmap(_bitmap_size, aux_bitmap_page_size);
  os::trace_page_sizes_for_requested_size("Aux Bitmap",
                                          bitmap_size_orig, aux_bitmap_page_size,
                                          aux_bitmap.base(),
                                          aux_bitmap.size(), aux_bitmap.page_size());
  MemTracker::record_virtual_memory_type(aux_bitmap.base(), mtGC);
  _aux_bitmap_region = MemRegion((HeapWord*) aux_bitmap.base(), aux_bitmap.size() / HeapWordSize);
  _aux_bitmap_region_special = aux_bitmap.special();
  _aux_bit_map.initialize(_heap_region, _aux_bitmap_region);

  //
  // Create regions and region sets
  //
  size_t region_align = align_up(sizeof(ShenandoahHeapRegion), SHENANDOAH_CACHE_LINE_SIZE);
  size_t region_storage_size_orig = region_align * _num_regions;
  size_t region_storage_size = align_up(region_storage_size_orig,
                                        MAX2(region_page_size, os::vm_allocation_granularity()));

  ReservedSpace region_storage(region_storage_size, region_page_size);
  os::trace_page_sizes_for_requested_size("Region Storage",
                                          region_storage_size_orig, region_page_size,
                                          region_storage.base(),
                                          region_storage.size(), region_storage.page_size());
  MemTracker::record_virtual_memory_type(region_storage.base(), mtGC);
  if (!region_storage.special()) {
    os::commit_memory_or_exit(region_storage.base(), region_storage_size, region_page_size, false,
                              "Cannot commit region memory");
  }

  // Try to fit the collection set bitmap at lower addresses. This optimizes code generation for cset checks.
  // Go up until a sensible limit (subject to encoding constraints) and try to reserve the space there.
  // If not successful, bite a bullet and allocate at whatever address.
  {
    const size_t cset_align = MAX2<size_t>(os::vm_page_size(), os::vm_allocation_granularity());
    const size_t cset_size = align_up(((size_t) sh_rs.base() + sh_rs.size()) >> ShenandoahHeapRegion::region_size_bytes_shift(), cset_align);
    const size_t cset_page_size = os::vm_page_size();

    uintptr_t min = round_up_power_of_2(cset_align);
    uintptr_t max = (1u << 30u);
    ReservedSpace cset_rs;

    for (uintptr_t addr = min; addr <= max; addr <<= 1u) {
      char* req_addr = (char*)addr;
      assert(is_aligned(req_addr, cset_align), "Should be aligned");
      cset_rs = ReservedSpace(cset_size, cset_align, cset_page_size, req_addr);
      if (cset_rs.is_reserved()) {
        assert(cset_rs.base() == req_addr, "Allocated where requested: " PTR_FORMAT ", " PTR_FORMAT, p2i(cset_rs.base()), addr);
        _collection_set = new ShenandoahCollectionSet(this, cset_rs, sh_rs.base());
        break;
      }
    }

    if (_collection_set == nullptr) {
      cset_rs = ReservedSpace(cset_size, cset_align, os::vm_page_size());
      _collection_set = new ShenandoahCollectionSet(this, cset_rs, sh_rs.base());
    }
    os::trace_page_sizes_for_requested_size("Collection Set",
                                            cset_size, cset_page_size,
                                            cset_rs.base(),
                                            cset_rs.size(), cset_rs.page_size());
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
  ShenandoahCodeRoots::initialize();

  if (ShenandoahPacing) {
    _pacer = new ShenandoahPacer(this);
    _pacer->setup_for_idle();
  }

  _control_thread = new ShenandoahControlThread();

  ShenandoahInitLogger::print();

  return JNI_OK;
}

void ShenandoahHeap::initialize_mode() {
  if (ShenandoahGCMode != nullptr) {
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
    vm_exit_during_initialization("Unknown -XX:ShenandoahGCMode option (null)");
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
}

void ShenandoahHeap::initialize_heuristics() {
  assert(_gc_mode != nullptr, "Must be initialized");
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
  _workers(nullptr),
  _safepoint_workers(nullptr),
  _heap_region_special(false),
  _num_regions(0),
  _regions(nullptr),
  _update_refs_iterator(this),
  _gc_state_changed(false),
  _gc_no_progress_count(0),
  _control_thread(nullptr),
  _shenandoah_policy(policy),
  _gc_mode(nullptr),
  _heuristics(nullptr),
  _free_set(nullptr),
  _pacer(nullptr),
  _verifier(nullptr),
  _phase_timings(nullptr),
  _monitoring_support(nullptr),
  _memory_pool(nullptr),
  _stw_memory_manager("Shenandoah Pauses"),
  _cycle_memory_manager("Shenandoah Cycles"),
  _gc_timer(new ConcurrentGCTimer()),
  _soft_ref_policy(),
  _log_min_obj_alignment_in_bytes(LogMinObjAlignmentInBytes),
  _ref_processor(new ShenandoahReferenceProcessor(MAX2(_max_workers, 1U))),
  _marking_context(nullptr),
  _bitmap_size(0),
  _bitmap_regions_per_slice(0),
  _bitmap_bytes_per_slice(0),
  _bitmap_region_special(false),
  _aux_bitmap_region_special(false),
  _liveness_cache(nullptr),
  _collection_set(nullptr)
{
  // Initialize GC mode early, so we can adjust barrier support
  initialize_mode();
  BarrierSet::set_barrier_set(new ShenandoahBarrierSet(this));

  _max_workers = MAX2(_max_workers, 1U);
  _workers = new ShenandoahWorkerThreads("Shenandoah GC Threads", _max_workers);
  if (_workers == nullptr) {
    vm_exit_during_initialization("Failed necessary allocation.");
  } else {
    _workers->initialize_workers();
  }

  if (ParallelGCThreads > 1) {
    _safepoint_workers = new ShenandoahWorkerThreads("Safepoint Cleanup Thread",
                                                ParallelGCThreads);
    _safepoint_workers->initialize_workers();
  }
}

#ifdef _MSC_VER
#pragma warning( pop )
#endif

class ShenandoahResetBitmapTask : public WorkerTask {
private:
  ShenandoahRegionIterator _regions;

public:
  ShenandoahResetBitmapTask() :
    WorkerTask("Shenandoah Reset Bitmap") {}

  void work(uint worker_id) {
    ShenandoahHeapRegion* region = _regions.next();
    ShenandoahHeap* heap = ShenandoahHeap::heap();
    ShenandoahMarkingContext* const ctx = heap->marking_context();
    while (region != nullptr) {
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
  st->print_cr(" " SIZE_FORMAT "%s max, " SIZE_FORMAT "%s soft max, " SIZE_FORMAT "%s committed, " SIZE_FORMAT "%s used",
               byte_size_in_proper_unit(max_capacity()), proper_unit_for_byte_size(max_capacity()),
               byte_size_in_proper_unit(soft_max_capacity()), proper_unit_for_byte_size(soft_max_capacity()),
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
  if (cset != nullptr) {
    st->print_cr(" - map (vanilla): " PTR_FORMAT, p2i(cset->map_address()));
    st->print_cr(" - map (biased):  " PTR_FORMAT, p2i(cset->biased_map_address()));
  } else {
    st->print_cr(" (null)");
  }

  st->cr();
  MetaspaceUtils::print_on(st);

  if (Verbose) {
    st->cr();
    print_heap_regions_on(st);
  }
}

class ShenandoahInitWorkerGCLABClosure : public ThreadClosure {
public:
  void do_thread(Thread* thread) {
    assert(thread != nullptr, "Sanity");
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
  // Now, we will let WorkerThreads to initialize gclab when new worker is created.
  _workers->set_initialize_gclab();
  if (_safepoint_workers != nullptr) {
    _safepoint_workers->threads_do(&init_gclabs);
    _safepoint_workers->set_initialize_gclab();
  }

  _heuristics->initialize();

  JFR_ONLY(ShenandoahJFRSupport::register_jfr_type_serializers());
}

size_t ShenandoahHeap::used() const {
  return Atomic::load(&_used);
}

size_t ShenandoahHeap::committed() const {
  return Atomic::load(&_committed);
}

size_t ShenandoahHeap::available() const {
  return free_set()->available();
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
  Atomic::add(&_used, bytes, memory_order_relaxed);
}

void ShenandoahHeap::set_used(size_t bytes) {
  Atomic::store(&_used, bytes);
}

void ShenandoahHeap::decrease_used(size_t bytes) {
  assert(used() >= bytes, "never decrease heap size by more than we've left");
  Atomic::sub(&_used, bytes, memory_order_relaxed);
}

void ShenandoahHeap::increase_allocated(size_t bytes) {
  Atomic::add(&_bytes_allocated_since_gc_start, bytes, memory_order_relaxed);
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

size_t ShenandoahHeap::soft_max_capacity() const {
  size_t v = Atomic::load(&_soft_max_size);
  assert(min_capacity() <= v && v <= max_capacity(),
         "Should be in bounds: " SIZE_FORMAT " <= " SIZE_FORMAT " <= " SIZE_FORMAT,
         min_capacity(), v, max_capacity());
  return v;
}

void ShenandoahHeap::set_soft_max_capacity(size_t v) {
  assert(min_capacity() <= v && v <= max_capacity(),
         "Should be in bounds: " SIZE_FORMAT " <= " SIZE_FORMAT " <= " SIZE_FORMAT,
         min_capacity(), v, max_capacity());
  Atomic::store(&_soft_max_size, v);
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

void ShenandoahHeap::maybe_uncommit(double shrink_before, size_t shrink_until) {
  assert (ShenandoahUncommit, "should be enabled");

  // Determine if there is work to do. This avoids taking heap lock if there is
  // no work available, avoids spamming logs with superfluous logging messages,
  // and minimises the amount of work while locks are taken.

  if (committed() <= shrink_until) return;

  bool has_work = false;
  for (size_t i = 0; i < num_regions(); i++) {
    ShenandoahHeapRegion* r = get_region(i);
    if (r->is_empty_committed() && (r->empty_time() < shrink_before)) {
      has_work = true;
      break;
    }
  }

  if (has_work) {
    static const char* msg = "Concurrent uncommit";
    ShenandoahConcurrentPhase gcPhase(msg, ShenandoahPhaseTimings::conc_uncommit, true /* log_heap_usage */);
    EventMark em("%s", msg);

    op_uncommit(shrink_before, shrink_until);
  }
}

void ShenandoahHeap::op_uncommit(double shrink_before, size_t shrink_until) {
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
        if (committed() < shrink_until + ShenandoahHeapRegion::region_size_bytes()) {
          break;
        }

        r->make_uncommitted();
        count++;
      }
    }
    SpinPause(); // allow allocators to take the lock
  }

  if (count > 0) {
    notify_heap_changed();
  }
}

bool ShenandoahHeap::check_soft_max_changed() {
  size_t new_soft_max = Atomic::load(&SoftMaxHeapSize);
  size_t old_soft_max = soft_max_capacity();
  if (new_soft_max != old_soft_max) {
    new_soft_max = MAX2(min_capacity(), new_soft_max);
    new_soft_max = MIN2(max_capacity(), new_soft_max);
    if (new_soft_max != old_soft_max) {
      log_info(gc)("Soft Max Heap Size: " SIZE_FORMAT "%s -> " SIZE_FORMAT "%s",
                   byte_size_in_proper_unit(old_soft_max), proper_unit_for_byte_size(old_soft_max),
                   byte_size_in_proper_unit(new_soft_max), proper_unit_for_byte_size(new_soft_max)
      );
      set_soft_max_capacity(new_soft_max);
      return true;
    }
  }
  return false;
}

void ShenandoahHeap::notify_heap_changed() {
  // Update monitoring counters when we took a new region. This amortizes the
  // update costs on slow path.
  monitoring_support()->notify_heap_changed();
  control_thread()->notify_heap_changed();
}

void ShenandoahHeap::set_forced_counters_update(bool value) {
  monitoring_support()->set_forced_counters_update(value);
}

void ShenandoahHeap::handle_force_counters_update() {
  monitoring_support()->handle_force_counters_update();
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
    return nullptr;
  }

  // Retire current GCLAB, and allocate a new one.
  PLAB* gclab = ShenandoahThreadLocalData::gclab(thread);
  gclab->retire();

  size_t actual_size = 0;
  HeapWord* gclab_buf = allocate_new_gclab(min_size, new_size, &actual_size);
  if (gclab_buf == nullptr) {
    return nullptr;
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
  if (res != nullptr) {
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
  if (res != nullptr) {
    *actual_size = req.actual_size();
  } else {
    *actual_size = 0;
  }
  return res;
}

HeapWord* ShenandoahHeap::allocate_memory(ShenandoahAllocRequest& req) {
  intptr_t pacer_epoch = 0;
  bool in_new_region = false;
  HeapWord* result = nullptr;

  if (req.is_mutator_alloc()) {
    if (ShenandoahPacing) {
      pacer()->pace_for_alloc(req.size());
      pacer_epoch = pacer()->epoch();
    }

    if (!ShenandoahAllocFailureALot || !should_inject_alloc_failure()) {
      result = allocate_memory_under_lock(req, in_new_region);
    }

    // Check that gc overhead is not exceeded.
    //
    // Shenandoah will grind along for quite a while allocating one
    // object at a time using shared (non-tlab) allocations. This check
    // is testing that the GC overhead limit has not been exceeded.
    // This will notify the collector to start a cycle, but will raise
    // an OOME to the mutator if the last Full GCs have not made progress.
    if (result == nullptr && !req.is_lab_alloc() && get_gc_no_progress_count() > ShenandoahNoProgressThreshold) {
      control_thread()->handle_alloc_failure(req, false);
      return nullptr;
    }

    // Block until control thread reacted, then retry allocation.
    //
    // It might happen that one of the threads requesting allocation would unblock
    // way later after GC happened, only to fail the second allocation, because
    // other threads have already depleted the free storage. In this case, a better
    // strategy is to try again, as long as GC makes progress (or until at least
    // one full GC has completed).
    size_t original_count = shenandoah_policy()->full_gc_count();
    while (result == nullptr
        && (get_gc_no_progress_count() == 0 || original_count == shenandoah_policy()->full_gc_count())) {
      control_thread()->handle_alloc_failure(req);
      result = allocate_memory_under_lock(req, in_new_region);
    }

    if (log_is_enabled(Debug, gc, alloc)) {
      ResourceMark rm;
      log_debug(gc, alloc)("Thread: %s, Result: " PTR_FORMAT ", Request: %s, Size: " SIZE_FORMAT ", Original: " SIZE_FORMAT ", Latest: " SIZE_FORMAT,
                           Thread::current()->name(), p2i(result), req.type_string(), req.size(), original_count, get_gc_no_progress_count());
    }
  } else {
    assert(req.is_gc_alloc(), "Can only accept GC allocs here");
    result = allocate_memory_under_lock(req, in_new_region);
    // Do not call handle_alloc_failure() here, because we cannot block.
    // The allocation failure would be handled by the LRB slowpath with handle_alloc_failure_evac().
  }

  if (in_new_region) {
    notify_heap_changed();
  }

  if (result != nullptr) {
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
  if (result != nullptr) {
    return result;
  }

  // Start full GC
  collect(GCCause::_metadata_GC_clear_soft_refs);

  // Retry allocation
  result = loader_data->metaspace_non_null()->allocate(size, mdtype);
  if (result != nullptr) {
    return result;
  }

  // Expand and retry allocation
  result = loader_data->metaspace_non_null()->expand_and_allocate(size, mdtype);
  if (result != nullptr) {
    return result;
  }

  // Out of memory
  return nullptr;
}

class ShenandoahConcurrentEvacuateRegionObjectClosure : public ObjectClosure {
private:
  ShenandoahHeap* const _heap;
  Thread* const _thread;
public:
  ShenandoahConcurrentEvacuateRegionObjectClosure(ShenandoahHeap* heap) :
    _heap(heap), _thread(Thread::current()) {}

  void do_object(oop p) {
    shenandoah_assert_marked(nullptr, p);
    if (!p->is_forwarded()) {
      _heap->evacuate_object(p, _thread);
    }
  }
};

class ShenandoahEvacuationTask : public WorkerTask {
private:
  ShenandoahHeap* const _sh;
  ShenandoahCollectionSet* const _cs;
  bool _concurrent;
public:
  ShenandoahEvacuationTask(ShenandoahHeap* sh,
                           ShenandoahCollectionSet* cs,
                           bool concurrent) :
    WorkerTask("Shenandoah Evacuation"),
    _sh(sh),
    _cs(cs),
    _concurrent(concurrent)
  {}

  void work(uint worker_id) {
    if (_concurrent) {
      ShenandoahConcurrentWorkerSession worker_session(worker_id);
      ShenandoahSuspendibleThreadSetJoiner stsj;
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
    while ((r =_cs->claim_next()) != nullptr) {
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

void ShenandoahHeap::evacuate_collection_set(bool concurrent) {
  ShenandoahEvacuationTask task(this, _collection_set, concurrent);
  workers()->run_task(&task);
}

void ShenandoahHeap::trash_cset_regions() {
  ShenandoahHeapLocker locker(lock());

  ShenandoahCollectionSet* set = collection_set();
  ShenandoahHeapRegion* r;
  set->clear_current_index();
  while ((r = set->next()) != nullptr) {
    r->make_trash();
  }
  collection_set()->clear();
}

void ShenandoahHeap::print_heap_regions_on(outputStream* st) const {
  st->print_cr("Heap Regions:");
  st->print_cr("Region state: EU=empty-uncommitted, EC=empty-committed, R=regular, H=humongous start, HP=pinned humongous start");
  st->print_cr("              HC=humongous continuation, CS=collection set, TR=trash, P=pinned, CSP=pinned collection set");
  st->print_cr("BTE=bottom/top/end, TAMS=top-at-mark-start");
  st->print_cr("UWM=update watermark, U=used");
  st->print_cr("T=TLAB allocs, G=GCLAB allocs");
  st->print_cr("S=shared allocs, L=live data");
  st->print_cr("CP=critical pins");

  for (size_t i = 0; i < num_regions(); i++) {
    get_region(i)->print_on(st);
  }
}

void ShenandoahHeap::trash_humongous_region_at(ShenandoahHeapRegion* start) {
  assert(start->is_humongous_start(), "reclaim regions starting with the first one");

  oop humongous_obj = cast_to_oop(start->bottom());
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

class ShenandoahCheckCleanGCLABClosure : public ThreadClosure {
public:
  ShenandoahCheckCleanGCLABClosure() {}
  void do_thread(Thread* thread) {
    PLAB* gclab = ShenandoahThreadLocalData::gclab(thread);
    assert(gclab != nullptr, "GCLAB should be initialized for %s", thread->name());
    assert(gclab->words_remaining() == 0, "GCLAB should not need retirement");
  }
};

class ShenandoahRetireGCLABClosure : public ThreadClosure {
private:
  bool const _resize;
public:
  ShenandoahRetireGCLABClosure(bool resize) : _resize(resize) {}
  void do_thread(Thread* thread) {
    PLAB* gclab = ShenandoahThreadLocalData::gclab(thread);
    assert(gclab != nullptr, "GCLAB should be initialized for %s", thread->name());
    gclab->retire();
    if (_resize && ShenandoahThreadLocalData::gclab_size(thread) > 0) {
      ShenandoahThreadLocalData::set_gclab_size(thread, 0);
    }
  }
};

void ShenandoahHeap::labs_make_parsable() {
  assert(UseTLAB, "Only call with UseTLAB");

  ShenandoahRetireGCLABClosure cl(false);

  for (JavaThreadIteratorWithHandle jtiwh; JavaThread *t = jtiwh.next(); ) {
    ThreadLocalAllocBuffer& tlab = t->tlab();
    tlab.make_parsable();
    cl.do_thread(t);
  }

  workers()->threads_do(&cl);
}

void ShenandoahHeap::tlabs_retire(bool resize) {
  assert(UseTLAB, "Only call with UseTLAB");
  assert(!resize || ResizeTLAB, "Only call for resize when ResizeTLAB is enabled");

  ThreadLocalAllocStats stats;

  for (JavaThreadIteratorWithHandle jtiwh; JavaThread *t = jtiwh.next(); ) {
    ThreadLocalAllocBuffer& tlab = t->tlab();
    tlab.retire(&stats);
    if (resize) {
      tlab.resize();
    }
  }

  stats.publish();

#ifdef ASSERT
  ShenandoahCheckCleanGCLABClosure cl;
  for (JavaThreadIteratorWithHandle jtiwh; JavaThread *t = jtiwh.next(); ) {
    cl.do_thread(t);
  }
  workers()->threads_do(&cl);
#endif
}

void ShenandoahHeap::gclabs_retire(bool resize) {
  assert(UseTLAB, "Only call with UseTLAB");
  assert(!resize || ResizeTLAB, "Only call for resize when ResizeTLAB is enabled");

  ShenandoahRetireGCLABClosure cl(resize);
  for (JavaThreadIteratorWithHandle jtiwh; JavaThread *t = jtiwh.next(); ) {
    cl.do_thread(t);
  }
  workers()->threads_do(&cl);

  if (safepoint_workers() != nullptr) {
    safepoint_workers()->threads_do(&cl);
  }
}

// Returns size in bytes
size_t ShenandoahHeap::unsafe_max_tlab_alloc(Thread *thread) const {
  // Return the max allowed size, and let the allocation path
  // figure out the safe size for current allocation.
  return ShenandoahHeapRegion::max_tlab_size_bytes();
}

size_t ShenandoahHeap::max_tlab_size() const {
  // Returns size in words
  return ShenandoahHeapRegion::max_tlab_size_words();
}

void ShenandoahHeap::collect(GCCause::Cause cause) {
  control_thread()->request_gc(cause);
}

void ShenandoahHeap::do_full_collection(bool clear_all_soft_refs) {
  //assert(false, "Shouldn't need to do full collections");
}

HeapWord* ShenandoahHeap::block_start(const void* addr) const {
  ShenandoahHeapRegion* r = heap_region_containing(addr);
  if (r != nullptr) {
    return r->block_start(addr);
  }
  return nullptr;
}

bool ShenandoahHeap::block_is_obj(const HeapWord* addr) const {
  ShenandoahHeapRegion* r = heap_region_containing(addr);
  return r->block_is_obj(addr);
}

bool ShenandoahHeap::print_location(outputStream* st, void* addr) const {
  return BlockLocationPrinter<ShenandoahHeap>::print_location(st, addr);
}

void ShenandoahHeap::prepare_for_verify() {
  if (SafepointSynchronize::is_at_safepoint() && UseTLAB) {
    labs_make_parsable();
  }
}

void ShenandoahHeap::gc_threads_do(ThreadClosure* tcl) const {
  tcl->do_thread(_control_thread);
  workers()->threads_do(tcl);
  if (_safepoint_workers != nullptr) {
    _safepoint_workers->threads_do(tcl);
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
  ShenandoahScanObjectStack* _oop_stack;
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
      obj = ShenandoahBarrierSet::barrier_set()->load_reference_barrier(obj);

      assert(oopDesc::is_oop(obj), "must be a valid oop");
      if (!_bitmap->is_marked(obj)) {
        _bitmap->mark(obj);
        _oop_stack->push(obj);
      }
    }
  }
public:
  ObjectIterateScanRootClosure(MarkBitMap* bitmap, ShenandoahScanObjectStack* oop_stack) :
    _bitmap(bitmap), _oop_stack(oop_stack), _heap(ShenandoahHeap::heap()),
    _marking_context(_heap->marking_context()) {}
  void do_oop(oop* p)       { do_oop_work(p); }
  void do_oop(narrowOop* p) { do_oop_work(p); }
};

/*
 * This is public API, used in preparation of object_iterate().
 * Since we don't do linear scan of heap in object_iterate() (see comment below), we don't
 * need to make the heap parsable. For Shenandoah-internal linear heap scans that we can
 * control, we call SH::tlabs_retire, SH::gclabs_retire.
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
  // Reset bitmap
  if (!prepare_aux_bitmap_for_iteration())
    return;

  ShenandoahScanObjectStack oop_stack;
  ObjectIterateScanRootClosure oops(&_aux_bit_map, &oop_stack);
  // Seed the stack with root scan
  scan_roots_for_iteration(&oop_stack, &oops);

  // Work through the oop stack to traverse heap
  while (! oop_stack.is_empty()) {
    oop obj = oop_stack.pop();
    assert(oopDesc::is_oop(obj), "must be a valid oop");
    cl->do_object(obj);
    obj->oop_iterate(&oops);
  }

  assert(oop_stack.is_empty(), "should be empty");
  // Reclaim bitmap
  reclaim_aux_bitmap_for_iteration();
}

bool ShenandoahHeap::prepare_aux_bitmap_for_iteration() {
  assert(SafepointSynchronize::is_at_safepoint(), "safe iteration is only available during safepoints");

  if (!_aux_bitmap_region_special && !os::commit_memory((char*)_aux_bitmap_region.start(), _aux_bitmap_region.byte_size(), false)) {
    log_warning(gc)("Could not commit native memory for auxiliary marking bitmap for heap iteration");
    return false;
  }
  // Reset bitmap
  _aux_bit_map.clear();
  return true;
}

void ShenandoahHeap::scan_roots_for_iteration(ShenandoahScanObjectStack* oop_stack, ObjectIterateScanRootClosure* oops) {
  // Process GC roots according to current GC cycle
  // This populates the work stack with initial objects
  // It is important to relinquish the associated locks before diving
  // into heap dumper
  uint n_workers = safepoint_workers() != nullptr ? safepoint_workers()->active_workers() : 1;
  ShenandoahHeapIterationRootScanner rp(n_workers);
  rp.roots_do(oops);
}

void ShenandoahHeap::reclaim_aux_bitmap_for_iteration() {
  if (!_aux_bitmap_region_special && !os::uncommit_memory((char*)_aux_bitmap_region.start(), _aux_bitmap_region.byte_size())) {
    log_warning(gc)("Could not uncommit native memory for auxiliary marking bitmap for heap iteration");
  }
}

// Closure for parallelly iterate objects
class ShenandoahObjectIterateParScanClosure : public BasicOopIterateClosure {
private:
  MarkBitMap* _bitmap;
  ShenandoahObjToScanQueue* _queue;
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
      obj = ShenandoahBarrierSet::barrier_set()->load_reference_barrier(obj);

      assert(oopDesc::is_oop(obj), "Must be a valid oop");
      if (_bitmap->par_mark(obj)) {
        _queue->push(ShenandoahMarkTask(obj));
      }
    }
  }
public:
  ShenandoahObjectIterateParScanClosure(MarkBitMap* bitmap, ShenandoahObjToScanQueue* q) :
    _bitmap(bitmap), _queue(q), _heap(ShenandoahHeap::heap()),
    _marking_context(_heap->marking_context()) {}
  void do_oop(oop* p)       { do_oop_work(p); }
  void do_oop(narrowOop* p) { do_oop_work(p); }
};

// Object iterator for parallel heap iteraion.
// The root scanning phase happenes in construction as a preparation of
// parallel marking queues.
// Every worker processes it's own marking queue. work-stealing is used
// to balance workload.
class ShenandoahParallelObjectIterator : public ParallelObjectIteratorImpl {
private:
  uint                         _num_workers;
  bool                         _init_ready;
  MarkBitMap*                  _aux_bit_map;
  ShenandoahHeap*              _heap;
  ShenandoahScanObjectStack    _roots_stack; // global roots stack
  ShenandoahObjToScanQueueSet* _task_queues;
public:
  ShenandoahParallelObjectIterator(uint num_workers, MarkBitMap* bitmap) :
        _num_workers(num_workers),
        _init_ready(false),
        _aux_bit_map(bitmap),
        _heap(ShenandoahHeap::heap()) {
    // Initialize bitmap
    _init_ready = _heap->prepare_aux_bitmap_for_iteration();
    if (!_init_ready) {
      return;
    }

    ObjectIterateScanRootClosure oops(_aux_bit_map, &_roots_stack);
    _heap->scan_roots_for_iteration(&_roots_stack, &oops);

    _init_ready = prepare_worker_queues();
  }

  ~ShenandoahParallelObjectIterator() {
    // Reclaim bitmap
    _heap->reclaim_aux_bitmap_for_iteration();
    // Reclaim queue for workers
    if (_task_queues!= nullptr) {
      for (uint i = 0; i < _num_workers; ++i) {
        ShenandoahObjToScanQueue* q = _task_queues->queue(i);
        if (q != nullptr) {
          delete q;
          _task_queues->register_queue(i, nullptr);
        }
      }
      delete _task_queues;
      _task_queues = nullptr;
    }
  }

  virtual void object_iterate(ObjectClosure* cl, uint worker_id) {
    if (_init_ready) {
      object_iterate_parallel(cl, worker_id, _task_queues);
    }
  }

private:
  // Divide global root_stack into worker queues
  bool prepare_worker_queues() {
    _task_queues = new ShenandoahObjToScanQueueSet((int) _num_workers);
    // Initialize queues for every workers
    for (uint i = 0; i < _num_workers; ++i) {
      ShenandoahObjToScanQueue* task_queue = new ShenandoahObjToScanQueue();
      _task_queues->register_queue(i, task_queue);
    }
    // Divide roots among the workers. Assume that object referencing distribution
    // is related with root kind, use round-robin to make every worker have same chance
    // to process every kind of roots
    size_t roots_num = _roots_stack.size();
    if (roots_num == 0) {
      // No work to do
      return false;
    }

    for (uint j = 0; j < roots_num; j++) {
      uint stack_id = j % _num_workers;
      oop obj = _roots_stack.pop();
      _task_queues->queue(stack_id)->push(ShenandoahMarkTask(obj));
    }
    return true;
  }

  void object_iterate_parallel(ObjectClosure* cl,
                               uint worker_id,
                               ShenandoahObjToScanQueueSet* queue_set) {
    assert(SafepointSynchronize::is_at_safepoint(), "safe iteration is only available during safepoints");
    assert(queue_set != nullptr, "task queue must not be null");

    ShenandoahObjToScanQueue* q = queue_set->queue(worker_id);
    assert(q != nullptr, "object iterate queue must not be null");

    ShenandoahMarkTask t;
    ShenandoahObjectIterateParScanClosure oops(_aux_bit_map, q);

    // Work through the queue to traverse heap.
    // Steal when there is no task in queue.
    while (q->pop(t) || queue_set->steal(worker_id, t)) {
      oop obj = t.obj();
      assert(oopDesc::is_oop(obj), "must be a valid oop");
      cl->do_object(obj);
      obj->oop_iterate(&oops);
    }
    assert(q->is_empty(), "should be empty");
  }
};

ParallelObjectIteratorImpl* ShenandoahHeap::parallel_object_iterator(uint workers) {
  return new ShenandoahParallelObjectIterator(workers, &_aux_bit_map);
}

// Keep alive an object that was loaded with AS_NO_KEEPALIVE.
void ShenandoahHeap::keep_alive(oop obj) {
  if (is_concurrent_mark_in_progress() && (obj != nullptr)) {
    ShenandoahBarrierSet::barrier_set()->enqueue(obj);
  }
}

void ShenandoahHeap::heap_region_iterate(ShenandoahHeapRegionClosure* blk) const {
  for (size_t i = 0; i < num_regions(); i++) {
    ShenandoahHeapRegion* current = get_region(i);
    blk->heap_region_do(current);
  }
}

class ShenandoahParallelHeapRegionTask : public WorkerTask {
private:
  ShenandoahHeap* const _heap;
  ShenandoahHeapRegionClosure* const _blk;

  shenandoah_padding(0);
  volatile size_t _index;
  shenandoah_padding(1);

public:
  ShenandoahParallelHeapRegionTask(ShenandoahHeapRegionClosure* blk) :
          WorkerTask("Shenandoah Parallel Region Operation"),
          _heap(ShenandoahHeap::heap()), _blk(blk), _index(0) {}

  void work(uint worker_id) {
    ShenandoahParallelWorkerSession worker_session(worker_id);
    size_t stride = ShenandoahParallelRegionStride;

    size_t max = _heap->num_regions();
    while (Atomic::load(&_index) < max) {
      size_t cur = Atomic::fetch_then_add(&_index, stride, memory_order_relaxed);
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

class ShenandoahRendezvousClosure : public HandshakeClosure {
public:
  inline ShenandoahRendezvousClosure() : HandshakeClosure("ShenandoahRendezvous") {}
  inline void do_thread(Thread* thread) {}
};

void ShenandoahHeap::rendezvous_threads() {
  ShenandoahRendezvousClosure cl;
  Handshake::execute(&cl);
}

void ShenandoahHeap::recycle_trash() {
  free_set()->recycle_trash();
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

void ShenandoahHeap::prepare_gc() {
  reset_mark_bitmap();

  ShenandoahResetUpdateRegionStateClosure cl;
  parallel_heap_region_iterate(&cl);
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

void ShenandoahHeap::prepare_regions_and_collection_set(bool concurrent) {
  assert(!is_full_gc_in_progress(), "Only for concurrent and degenerated GC");
  {
    ShenandoahGCPhase phase(concurrent ? ShenandoahPhaseTimings::final_update_region_states :
                                         ShenandoahPhaseTimings::degen_gc_final_update_region_states);
    ShenandoahFinalMarkUpdateRegionStateClosure cl;
    parallel_heap_region_iterate(&cl);

    assert_pinned_region_status();
  }

  {
    ShenandoahGCPhase phase(concurrent ? ShenandoahPhaseTimings::choose_cset :
                                         ShenandoahPhaseTimings::degen_gc_choose_cset);
    ShenandoahHeapLocker locker(lock());
    _collection_set->clear();
    heuristics()->choose_collection_set(_collection_set);
  }

  {
    ShenandoahGCPhase phase(concurrent ? ShenandoahPhaseTimings::final_rebuild_freeset :
                                         ShenandoahPhaseTimings::degen_gc_final_rebuild_freeset);
    ShenandoahHeapLocker locker(lock());
    _free_set->rebuild();
  }
}

void ShenandoahHeap::do_class_unloading() {
  _unloader.unload();
}

void ShenandoahHeap::stw_weak_refs(bool full_gc) {
  // Weak refs processing
  ShenandoahPhaseTimings::Phase phase = full_gc ? ShenandoahPhaseTimings::full_gc_weakrefs
                                                : ShenandoahPhaseTimings::degen_gc_weakrefs;
  ShenandoahTimingsTracker t(phase);
  ShenandoahGCWorkerPhase worker_phase(phase);
  ref_processor()->process_references(phase, workers(), false /* concurrent */);
}

void ShenandoahHeap::prepare_update_heap_references(bool concurrent) {
  assert(ShenandoahSafepoint::is_at_shenandoah_safepoint(), "must be at safepoint");

  // Evacuation is over, no GCLABs are needed anymore. GCLABs are under URWM, so we need to
  // make them parsable for update code to work correctly. Plus, we can compute new sizes
  // for future GCLABs here.
  if (UseTLAB) {
    ShenandoahGCPhase phase(concurrent ?
                            ShenandoahPhaseTimings::init_update_refs_manage_gclabs :
                            ShenandoahPhaseTimings::degen_gc_init_update_refs_manage_gclabs);
    gclabs_retire(ResizeTLAB);
  }

  _update_refs_iterator.reset();
}

void ShenandoahHeap::propagate_gc_state_to_java_threads() {
  assert(ShenandoahSafepoint::is_at_shenandoah_safepoint(), "Must be at Shenandoah safepoint");
  if (_gc_state_changed) {
    _gc_state_changed = false;
    char state = gc_state();
    for (JavaThreadIteratorWithHandle jtiwh; JavaThread *t = jtiwh.next(); ) {
      ShenandoahThreadLocalData::set_gc_state(t, state);
    }
  }
}

void ShenandoahHeap::set_gc_state(uint mask, bool value) {
  assert(ShenandoahSafepoint::is_at_shenandoah_safepoint(), "Must be at Shenandoah safepoint");
  _gc_state.set_cond(mask, value);
  _gc_state_changed = true;
}

void ShenandoahHeap::set_concurrent_mark_in_progress(bool in_progress) {
  assert(!has_forwarded_objects(), "Not expected before/after mark phase");
  set_gc_state(MARKING, in_progress);
  ShenandoahBarrierSet::satb_mark_queue_set().set_active_all_threads(in_progress, !in_progress);
}

void ShenandoahHeap::set_evacuation_in_progress(bool in_progress) {
  assert(ShenandoahSafepoint::is_at_shenandoah_safepoint(), "Only call this at safepoint");
  set_gc_state(EVACUATION, in_progress);
}

void ShenandoahHeap::set_concurrent_strong_root_in_progress(bool in_progress) {
  if (in_progress) {
    _concurrent_strong_root_in_progress.set();
  } else {
    _concurrent_strong_root_in_progress.unset();
  }
}

void ShenandoahHeap::set_concurrent_weak_root_in_progress(bool cond) {
  set_gc_state(WEAK_ROOTS, cond);
}

GCTracer* ShenandoahHeap::tracer() {
  return shenandoah_policy()->tracer();
}

size_t ShenandoahHeap::tlab_used(Thread* thread) const {
  return _free_set->used();
}

bool ShenandoahHeap::try_cancel_gc() {
  jbyte prev = _cancelled_gc.cmpxchg(CANCELLED, CANCELLABLE);
  return prev == CANCELLABLE;
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
}

void ShenandoahHeap::stw_unload_classes(bool full_gc) {
  if (!unload_classes()) return;
  ClassUnloadingContext ctx(_workers->active_workers(),
                            true /* unregister_nmethods_during_purge */,
                            false /* lock_codeblob_free_separately */);

  // Unload classes and purge SystemDictionary.
  {
    ShenandoahPhaseTimings::Phase phase = full_gc ?
                                          ShenandoahPhaseTimings::full_gc_purge_class_unload :
                                          ShenandoahPhaseTimings::degen_gc_purge_class_unload;
    ShenandoahIsAliveSelector is_alive;
    {
      CodeCache::UnlinkingScope scope(is_alive.is_alive_closure());
      ShenandoahGCPhase gc_phase(phase);
      ShenandoahGCWorkerPhase worker_phase(phase);
      bool unloading_occurred = SystemDictionary::do_unloading(gc_timer());

      uint num_workers = _workers->active_workers();
      ShenandoahClassUnloadingTask unlink_task(phase, num_workers, unloading_occurred);
      _workers->run_task(&unlink_task);
    }
    // Release unloaded nmethods's memory.
    ClassUnloadingContext::context()->purge_and_free_nmethods();
  }

  {
    ShenandoahGCPhase phase(full_gc ?
                            ShenandoahPhaseTimings::full_gc_purge_cldg :
                            ShenandoahPhaseTimings::degen_gc_purge_cldg);
    ClassLoaderDataGraph::purge(true /* at_safepoint */);
  }
  // Resize and verify metaspace
  MetaspaceGC::compute_new_size();
  DEBUG_ONLY(MetaspaceUtils::verify();)
}

// Weak roots are either pre-evacuated (final mark) or updated (final updaterefs),
// so they should not have forwarded oops.
// However, we do need to "null" dead oops in the roots, if can not be done
// in concurrent cycles.
void ShenandoahHeap::stw_process_weak_roots(bool full_gc) {
  uint num_workers = _workers->active_workers();
  ShenandoahPhaseTimings::Phase timing_phase = full_gc ?
                                               ShenandoahPhaseTimings::full_gc_purge_weak_par :
                                               ShenandoahPhaseTimings::degen_gc_purge_weak_par;
  ShenandoahGCPhase phase(timing_phase);
  ShenandoahGCWorkerPhase worker_phase(timing_phase);
  // Cleanup weak roots
  if (has_forwarded_objects()) {
    ShenandoahForwardedIsAliveClosure is_alive;
    ShenandoahUpdateRefsClosure keep_alive;
    ShenandoahParallelWeakRootsCleaningTask<ShenandoahForwardedIsAliveClosure, ShenandoahUpdateRefsClosure>
      cleaning_task(timing_phase, &is_alive, &keep_alive, num_workers);
    _workers->run_task(&cleaning_task);
  } else {
    ShenandoahIsAliveClosure is_alive;
#ifdef ASSERT
    ShenandoahAssertNotForwardedClosure verify_cl;
    ShenandoahParallelWeakRootsCleaningTask<ShenandoahIsAliveClosure, ShenandoahAssertNotForwardedClosure>
      cleaning_task(timing_phase, &is_alive, &verify_cl, num_workers);
#else
    ShenandoahParallelWeakRootsCleaningTask<ShenandoahIsAliveClosure, DoNothingClosure>
      cleaning_task(timing_phase, &is_alive, &do_nothing_cl, num_workers);
#endif
    _workers->run_task(&cleaning_task);
  }
}

void ShenandoahHeap::parallel_cleaning(bool full_gc) {
  assert(SafepointSynchronize::is_at_safepoint(), "Must be at a safepoint");
  assert(is_stw_gc_in_progress(), "Only for Degenerated and Full GC");
  ShenandoahGCPhase phase(full_gc ?
                          ShenandoahPhaseTimings::full_gc_purge :
                          ShenandoahPhaseTimings::degen_gc_purge);
  stw_weak_refs(full_gc);
  stw_process_weak_roots(full_gc);
  stw_unload_classes(full_gc);
}

void ShenandoahHeap::set_has_forwarded_objects(bool cond) {
  set_gc_state(HAS_FORWARDED, cond);
}

void ShenandoahHeap::set_unload_classes(bool uc) {
  _unload_classes.set_cond(uc);
}

bool ShenandoahHeap::unload_classes() const {
  return _unload_classes.is_set();
}

address ShenandoahHeap::in_cset_fast_test_addr() {
  ShenandoahHeap* heap = ShenandoahHeap::heap();
  assert(heap->collection_set() != nullptr, "Sanity");
  return (address) heap->collection_set()->biased_map_address();
}

size_t ShenandoahHeap::bytes_allocated_since_gc_start() const {
  return Atomic::load(&_bytes_allocated_since_gc_start);
}

void ShenandoahHeap::reset_bytes_allocated_since_gc_start() {
  Atomic::store(&_bytes_allocated_since_gc_start, (size_t)0);
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
  set_gc_state(UPDATEREFS, in_progress);
}

void ShenandoahHeap::register_nmethod(nmethod* nm) {
  ShenandoahCodeRoots::register_nmethod(nm);
}

void ShenandoahHeap::unregister_nmethod(nmethod* nm) {
  ShenandoahCodeRoots::unregister_nmethod(nm);
}

void ShenandoahHeap::pin_object(JavaThread* thr, oop o) {
  heap_region_containing(o)->record_pin();
}

void ShenandoahHeap::unpin_object(JavaThread* thr, oop o) {
  ShenandoahHeapRegion* r = heap_region_containing(o);
  assert(r != nullptr, "Sanity");
  assert(r->pin_count() > 0, "Region " SIZE_FORMAT " should have non-zero pins", r->index());
  r->record_unpin();
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
  assert(!is_stw_gc_in_progress(), "Only concurrent GC");
  set_concurrent_strong_root_in_progress(!collection_set()->is_empty());
  set_concurrent_weak_root_in_progress(true);
  if (unload_classes()) {
    _unloader.prepare();
  }
}

void ShenandoahHeap::finish_concurrent_roots() {
  assert(SafepointSynchronize::is_at_safepoint(), "Must be at a safepoint");
  assert(!is_stw_gc_in_progress(), "Only concurrent GC");
  if (unload_classes()) {
    _unloader.finish();
  }
}

#ifdef ASSERT
void ShenandoahHeap::assert_gc_workers(uint nworkers) {
  assert(nworkers > 0 && nworkers <= max_workers(), "Sanity");

  if (ShenandoahSafepoint::is_at_shenandoah_safepoint()) {
    // Use ParallelGCThreads inside safepoints
    assert(nworkers == ParallelGCThreads, "Use ParallelGCThreads (%u) within safepoint, not %u",
           ParallelGCThreads, nworkers);
  } else {
    // Use ConcGCThreads outside safepoints
    assert(nworkers == ConcGCThreads, "Use ConcGCThreads (%u) outside safepoints, %u",
           ConcGCThreads, nworkers);
  }
}
#endif

ShenandoahVerifier* ShenandoahHeap::verifier() {
  guarantee(ShenandoahVerify, "Should be enabled");
  assert (_verifier != nullptr, "sanity");
  return _verifier;
}

template<bool CONCURRENT>
class ShenandoahUpdateHeapRefsTask : public WorkerTask {
private:
  ShenandoahHeap* _heap;
  ShenandoahRegionIterator* _regions;
public:
  ShenandoahUpdateHeapRefsTask(ShenandoahRegionIterator* regions) :
    WorkerTask("Shenandoah Update References"),
    _heap(ShenandoahHeap::heap()),
    _regions(regions) {
  }

  void work(uint worker_id) {
    if (CONCURRENT) {
      ShenandoahConcurrentWorkerSession worker_session(worker_id);
      ShenandoahSuspendibleThreadSetJoiner stsj;
      do_work<ShenandoahConcUpdateRefsClosure>();
    } else {
      ShenandoahParallelWorkerSession worker_session(worker_id);
      do_work<ShenandoahSTWUpdateRefsClosure>();
    }
  }

private:
  template<class T>
  void do_work() {
    T cl;
    ShenandoahHeapRegion* r = _regions->next();
    ShenandoahMarkingContext* const ctx = _heap->complete_marking_context();
    while (r != nullptr) {
      HeapWord* update_watermark = r->get_update_watermark();
      assert (update_watermark >= r->bottom(), "sanity");
      if (r->is_active() && !r->is_cset()) {
        _heap->marked_object_oop_iterate(r, &cl, update_watermark);
      }
      if (ShenandoahPacing) {
        _heap->pacer()->report_updaterefs(pointer_delta(update_watermark, r->bottom()));
      }
      if (_heap->check_cancelled_gc_and_yield(CONCURRENT)) {
        return;
      }
      r = _regions->next();
    }
  }
};

void ShenandoahHeap::update_heap_references(bool concurrent) {
  assert(!is_full_gc_in_progress(), "Only for concurrent and degenerated GC");

  if (concurrent) {
    ShenandoahUpdateHeapRefsTask<true> task(&_update_refs_iterator);
    workers()->run_task(&task);
  } else {
    ShenandoahUpdateHeapRefsTask<false> task(&_update_refs_iterator);
    workers()->run_task(&task);
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

void ShenandoahHeap::update_heap_region_states(bool concurrent) {
  assert(SafepointSynchronize::is_at_safepoint(), "Must be at a safepoint");
  assert(!is_full_gc_in_progress(), "Only for concurrent and degenerated GC");

  {
    ShenandoahGCPhase phase(concurrent ?
                            ShenandoahPhaseTimings::final_update_refs_update_region_states :
                            ShenandoahPhaseTimings::degen_gc_final_update_refs_update_region_states);
    ShenandoahFinalUpdateRefsUpdateRegionStateClosure cl;
    parallel_heap_region_iterate(&cl);

    assert_pinned_region_status();
  }

  {
    ShenandoahGCPhase phase(concurrent ?
                            ShenandoahPhaseTimings::final_update_refs_trash_cset :
                            ShenandoahPhaseTimings::degen_gc_final_update_refs_trash_cset);
    trash_cset_regions();
  }
}

void ShenandoahHeap::rebuild_free_set(bool concurrent) {
  {
    ShenandoahGCPhase phase(concurrent ?
                            ShenandoahPhaseTimings::final_update_refs_rebuild_freeset :
                            ShenandoahPhaseTimings::degen_gc_final_update_refs_rebuild_freeset);
    ShenandoahHeapLocker locker(lock());
    _free_set->rebuild();
  }
}

void ShenandoahHeap::print_extended_on(outputStream *st) const {
  print_on(st);
  st->cr();
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
  SuspendibleThreadSet::synchronize();
}

void ShenandoahHeap::safepoint_synchronize_end() {
  SuspendibleThreadSet::desynchronize();
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

ShenandoahLiveData* ShenandoahHeap::get_liveness_cache(uint worker_id) {
#ifdef ASSERT
  assert(_liveness_cache != nullptr, "sanity");
  assert(worker_id < _max_workers, "sanity");
  for (uint i = 0; i < num_regions(); i++) {
    assert(_liveness_cache[worker_id][i] == 0, "liveness cache should be empty");
  }
#endif
  return _liveness_cache[worker_id];
}

void ShenandoahHeap::flush_liveness_cache(uint worker_id) {
  assert(worker_id < _max_workers, "sanity");
  assert(_liveness_cache != nullptr, "sanity");
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

bool ShenandoahHeap::requires_barriers(stackChunkOop obj) const {
  if (is_idle()) return false;

  // Objects allocated after marking start are implicitly alive, don't need any barriers during
  // marking phase.
  if (is_concurrent_mark_in_progress() &&
     !marking_context()->allocated_after_mark_start(obj)) {
    return true;
  }

  // Can not guarantee obj is deeply good.
  if (has_forwarded_objects()) {
    return true;
  }

  return false;
}
