/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2013, 2022, Red Hat, Inc. All rights reserved.
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
#include "memory/allocation.hpp"
#include "memory/universe.hpp"

#include "gc/shared/gcArguments.hpp"
#include "gc/shared/gcTimer.hpp"
#include "gc/shared/gcTraceTime.inline.hpp"
#include "gc/shared/locationPrinter.inline.hpp"
#include "gc/shared/memAllocator.hpp"
#include "gc/shared/plab.hpp"
#include "gc/shared/tlab_globals.hpp"

#include "gc/shenandoah/shenandoahAgeCensus.hpp"
#include "gc/shenandoah/heuristics/shenandoahOldHeuristics.hpp"
#include "gc/shenandoah/heuristics/shenandoahYoungHeuristics.hpp"
#include "gc/shenandoah/shenandoahAllocRequest.hpp"
#include "gc/shenandoah/shenandoahBarrierSet.hpp"
#include "gc/shenandoah/shenandoahCardTable.hpp"
#include "gc/shenandoah/shenandoahClosures.inline.hpp"
#include "gc/shenandoah/shenandoahCollectionSet.hpp"
#include "gc/shenandoah/shenandoahCollectorPolicy.hpp"
#include "gc/shenandoah/shenandoahConcurrentMark.hpp"
#include "gc/shenandoah/shenandoahMarkingContext.inline.hpp"
#include "gc/shenandoah/shenandoahControlThread.hpp"
#include "gc/shenandoah/shenandoahRegulatorThread.hpp"
#include "gc/shenandoah/shenandoahFreeSet.hpp"
#include "gc/shenandoah/shenandoahGlobalGeneration.hpp"
#include "gc/shenandoah/shenandoahPhaseTimings.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahHeapRegion.inline.hpp"
#include "gc/shenandoah/shenandoahHeapRegionSet.hpp"
#include "gc/shenandoah/shenandoahInitLogger.hpp"
#include "gc/shenandoah/shenandoahMarkingContext.inline.hpp"
#include "gc/shenandoah/shenandoahMemoryPool.hpp"
#include "gc/shenandoah/shenandoahMetrics.hpp"
#include "gc/shenandoah/shenandoahMonitoringSupport.hpp"
#include "gc/shenandoah/shenandoahOldGeneration.hpp"
#include "gc/shenandoah/shenandoahOopClosures.inline.hpp"
#include "gc/shenandoah/shenandoahPacer.inline.hpp"
#include "gc/shenandoah/shenandoahPadding.hpp"
#include "gc/shenandoah/shenandoahParallelCleaning.inline.hpp"
#include "gc/shenandoah/shenandoahReferenceProcessor.hpp"
#include "gc/shenandoah/shenandoahRootProcessor.inline.hpp"
#include "gc/shenandoah/shenandoahScanRemembered.inline.hpp"
#include "gc/shenandoah/shenandoahSTWMark.hpp"
#include "gc/shenandoah/shenandoahUtils.hpp"
#include "gc/shenandoah/shenandoahVerifier.hpp"
#include "gc/shenandoah/shenandoahCodeRoots.hpp"
#include "gc/shenandoah/shenandoahVMOperations.hpp"
#include "gc/shenandoah/shenandoahWorkGroup.hpp"
#include "gc/shenandoah/shenandoahWorkerPolicy.hpp"
#include "gc/shenandoah/shenandoahYoungGeneration.hpp"
#include "gc/shenandoah/mode/shenandoahGenerationalMode.hpp"
#include "gc/shenandoah/mode/shenandoahIUMode.hpp"
#include "gc/shenandoah/mode/shenandoahPassiveMode.hpp"
#include "gc/shenandoah/mode/shenandoahSATBMode.hpp"
#include "utilities/globalDefinitions.hpp"

#if INCLUDE_JFR
#include "gc/shenandoah/shenandoahJfrSupport.hpp"
#endif

#include "classfile/systemDictionary.hpp"
#include "code/codeCache.hpp"
#include "memory/classLoaderMetaspace.hpp"
#include "memory/metaspaceUtils.hpp"
#include "oops/compressedOops.inline.hpp"
#include "prims/jvmtiTagMap.hpp"
#include "runtime/atomic.hpp"
#include "runtime/globals.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/java.hpp"
#include "runtime/orderAccess.hpp"
#include "runtime/safepointMechanism.hpp"
#include "runtime/vmThread.hpp"
#include "services/mallocTracker.hpp"
#include "services/memTracker.hpp"
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

  // Now we know the number of regions and heap sizes, initialize the heuristics.
  initialize_heuristics_generations();

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

  BarrierSet::set_barrier_set(new ShenandoahBarrierSet(this, _heap_region));

  //
  // After reserving the Java heap, create the card table, barriers, and workers, in dependency order
  //
  if (mode()->is_generational()) {
    ShenandoahDirectCardMarkRememberedSet *rs;
    ShenandoahCardTable* card_table = ShenandoahBarrierSet::barrier_set()->card_table();
    size_t card_count = card_table->cards_required(heap_rs.size() / HeapWordSize);
    rs = new ShenandoahDirectCardMarkRememberedSet(ShenandoahBarrierSet::barrier_set()->card_table(), card_count);
    _card_scan = new ShenandoahScanRemembered<ShenandoahDirectCardMarkRememberedSet>(rs);

    // Age census structure
    _age_census = new ShenandoahAgeCensus();
  }

  _workers = new ShenandoahWorkerThreads("Shenandoah GC Threads", _max_workers);
  if (_workers == nullptr) {
    vm_exit_during_initialization("Failed necessary allocation.");
  } else {
    _workers->initialize_workers();
  }

  if (ParallelGCThreads > 1) {
    _safepoint_workers = new ShenandoahWorkerThreads("Safepoint Cleanup Thread", ParallelGCThreads);
    _safepoint_workers->initialize_workers();
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

  _marking_context = new ShenandoahMarkingContext(_heap_region, _bitmap_region, _num_regions);

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
  _affiliations = NEW_C_HEAP_ARRAY(uint8_t, _num_regions, mtGC);
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

      _affiliations[i] = ShenandoahAffiliation::FREE;
    }

    // Initialize to complete
    _marking_context->mark_complete();
    size_t young_cset_regions, old_cset_regions;

    // We are initializing free set.  We ignore cset region tallies.
    _free_set->prepare_to_rebuild(young_cset_regions, old_cset_regions);
    _free_set->rebuild(young_cset_regions, old_cset_regions);
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
  } else {
    _pacer = nullptr;
  }

  _control_thread = new ShenandoahControlThread();
  _regulator_thread = new ShenandoahRegulatorThread(_control_thread);

  print_init_logger();

  return JNI_OK;
}

void ShenandoahHeap::print_init_logger() const {
  ShenandoahInitLogger::print();
}

size_t ShenandoahHeap::max_size_for(ShenandoahGeneration* generation) const {
  switch (generation->type()) {
    case YOUNG:
      return _generation_sizer.max_young_size();
    case OLD:
      return max_capacity() - _generation_sizer.min_young_size();
    case GLOBAL_GEN:
    case GLOBAL_NON_GEN:
      return max_capacity();
    default:
      ShouldNotReachHere();
      return 0;
  }
}

size_t ShenandoahHeap::min_size_for(ShenandoahGeneration* generation) const {
  switch (generation->type()) {
    case YOUNG:
      return _generation_sizer.min_young_size();
    case OLD:
      return max_capacity() - _generation_sizer.max_young_size();
    case GLOBAL_GEN:
    case GLOBAL_NON_GEN:
      return min_capacity();
    default:
      ShouldNotReachHere();
      return 0;
  }
}

void ShenandoahHeap::initialize_heuristics_generations() {
  if (ShenandoahGCMode != nullptr) {
    if (strcmp(ShenandoahGCMode, "satb") == 0) {
      _gc_mode = new ShenandoahSATBMode();
    } else if (strcmp(ShenandoahGCMode, "iu") == 0) {
      _gc_mode = new ShenandoahIUMode();
    } else if (strcmp(ShenandoahGCMode, "passive") == 0) {
      _gc_mode = new ShenandoahPassiveMode();
    } else if (strcmp(ShenandoahGCMode, "generational") == 0) {
      _gc_mode = new ShenandoahGenerationalMode();
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

  // Max capacity is the maximum _allowed_ capacity. That is, the maximum allowed capacity
  // for old would be total heap - minimum capacity of young. This means the sum of the maximum
  // allowed for old and young could exceed the total heap size. It remains the case that the
  // _actual_ capacity of young + old = total.
  _generation_sizer.heap_size_changed(max_capacity());
  size_t initial_capacity_young = _generation_sizer.max_young_size();
  size_t max_capacity_young = _generation_sizer.max_young_size();
  size_t initial_capacity_old = max_capacity() - max_capacity_young;
  size_t max_capacity_old = max_capacity() - initial_capacity_young;

  _young_generation = new ShenandoahYoungGeneration(_max_workers, max_capacity_young, initial_capacity_young);
  _old_generation = new ShenandoahOldGeneration(_max_workers, max_capacity_old, initial_capacity_old);
  _global_generation = new ShenandoahGlobalGeneration(_gc_mode->is_generational(), _max_workers, max_capacity(), max_capacity());
  _global_generation->initialize_heuristics(_gc_mode);
  if (mode()->is_generational()) {
    _young_generation->initialize_heuristics(_gc_mode);
    _old_generation->initialize_heuristics(_gc_mode);
  }
  _evac_tracker = new ShenandoahEvacuationTracker(mode()->is_generational());
}

#ifdef _MSC_VER
#pragma warning( push )
#pragma warning( disable:4355 ) // 'this' : used in base member initializer list
#endif

ShenandoahHeap::ShenandoahHeap(ShenandoahCollectorPolicy* policy) :
  CollectedHeap(),
  _gc_generation(nullptr),
  _prepare_for_old_mark(false),
  _initial_size(0),
  _promotion_potential(0),
  _committed(0),
  _max_workers(MAX3(ConcGCThreads, ParallelGCThreads, 1U)),
  _workers(nullptr),
  _safepoint_workers(nullptr),
  _heap_region_special(false),
  _num_regions(0),
  _regions(nullptr),
  _affiliations(nullptr),
  _update_refs_iterator(this),
  _promoted_reserve(0),
  _old_evac_reserve(0),
  _young_evac_reserve(0),
  _upgraded_to_full(false),
  _age_census(nullptr),
  _has_evacuation_reserve_quantities(false),
  _cancel_requested_time(0),
  _young_generation(nullptr),
  _global_generation(nullptr),
  _old_generation(nullptr),
  _control_thread(nullptr),
  _regulator_thread(nullptr),
  _shenandoah_policy(policy),
  _free_set(nullptr),
  _pacer(nullptr),
  _verifier(nullptr),
  _phase_timings(nullptr),
  _evac_tracker(nullptr),
  _mmu_tracker(),
  _generation_sizer(),
  _monitoring_support(nullptr),
  _memory_pool(nullptr),
  _young_gen_memory_pool(nullptr),
  _old_gen_memory_pool(nullptr),
  _stw_memory_manager("Shenandoah Pauses"),
  _cycle_memory_manager("Shenandoah Cycles"),
  _gc_timer(new ConcurrentGCTimer()),
  _soft_ref_policy(),
  _log_min_obj_alignment_in_bytes(LogMinObjAlignmentInBytes),
  _old_regions_surplus(0),
  _old_regions_deficit(0),
  _marking_context(nullptr),
  _bitmap_size(0),
  _bitmap_regions_per_slice(0),
  _bitmap_bytes_per_slice(0),
  _bitmap_region_special(false),
  _aux_bitmap_region_special(false),
  _liveness_cache(nullptr),
  _collection_set(nullptr),
  _card_scan(nullptr)
{
}

#ifdef _MSC_VER
#pragma warning( pop )
#endif

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
  if (is_concurrent_old_mark_in_progress())    st->print("old marking, ");
  if (is_concurrent_young_mark_in_progress())  st->print("young marking, ");
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
  _mmu_tracker.initialize();

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

  JFR_ONLY(ShenandoahJFRSupport::register_jfr_type_serializers());
}

ShenandoahHeuristics* ShenandoahHeap::heuristics() {
  return _global_generation->heuristics();
}

ShenandoahOldHeuristics* ShenandoahHeap::old_heuristics() {
  return (ShenandoahOldHeuristics*) _old_generation->heuristics();
}

ShenandoahYoungHeuristics* ShenandoahHeap::young_heuristics() {
  return (ShenandoahYoungHeuristics*) _young_generation->heuristics();
}

bool ShenandoahHeap::doing_mixed_evacuations() {
  return _old_generation->state() == ShenandoahOldGeneration::WAITING_FOR_EVAC;
}

bool ShenandoahHeap::is_old_bitmap_stable() const {
  return _old_generation->is_mark_complete();
}

bool ShenandoahHeap::is_gc_generation_young() const {
  return _gc_generation != nullptr && _gc_generation->is_young();
}

size_t ShenandoahHeap::used() const {
  return global_generation()->used();
}

size_t ShenandoahHeap::committed() const {
  return Atomic::load(&_committed);
}

void ShenandoahHeap::increase_committed(size_t bytes) {
  shenandoah_assert_heaplocked_or_safepoint();
  _committed += bytes;
}

void ShenandoahHeap::decrease_committed(size_t bytes) {
  shenandoah_assert_heaplocked_or_safepoint();
  _committed -= bytes;
}

// For tracking usage based on allocations, it should be the case that:
// * The sum of regions::used == heap::used
// * The sum of a generation's regions::used == generation::used
// * The sum of a generation's humongous regions::free == generation::humongous_waste
// These invariants are checked by the verifier on GC safepoints.
//
// Additional notes:
// * When a mutator's allocation request causes a region to be retired, the
//   free memory left in that region is considered waste. It does not contribute
//   to the usage, but it _does_ contribute to allocation rate.
// * The bottom of a PLAB must be aligned on card size. In some cases this will
//   require padding in front of the PLAB (a filler object). Because this padding
//   is included in the region's used memory we include the padding in the usage
//   accounting as waste.
// * Mutator allocations are used to compute an allocation rate. They are also
//   sent to the Pacer for those purposes.
// * There are three sources of waste:
//  1. The padding used to align a PLAB on card size
//  2. Region's free is less than minimum TLAB size and is retired
//  3. The unused portion of memory in the last region of a humongous object
void ShenandoahHeap::increase_used(const ShenandoahAllocRequest& req) {
  size_t actual_bytes = req.actual_size() * HeapWordSize;
  size_t wasted_bytes = req.waste() * HeapWordSize;
  ShenandoahGeneration* generation = generation_for(req.affiliation());

  if (req.is_gc_alloc()) {
    assert(wasted_bytes == 0 || req.type() == ShenandoahAllocRequest::_alloc_plab, "Only PLABs have waste");
    increase_used(generation, actual_bytes + wasted_bytes);
  } else {
    assert(req.is_mutator_alloc(), "Expected mutator alloc here");
    // padding and actual size both count towards allocation counter
    generation->increase_allocated(actual_bytes + wasted_bytes);

    // only actual size counts toward usage for mutator allocations
    increase_used(generation, actual_bytes);

    // notify pacer of both actual size and waste
    notify_mutator_alloc_words(req.actual_size(), req.waste());

    if (wasted_bytes > 0 && req.actual_size() > ShenandoahHeapRegion::humongous_threshold_words()) {
      increase_humongous_waste(generation,wasted_bytes);
    }
  }
}

void ShenandoahHeap::increase_humongous_waste(ShenandoahGeneration* generation, size_t bytes) {
  generation->increase_humongous_waste(bytes);
  if (!generation->is_global()) {
    global_generation()->increase_humongous_waste(bytes);
  }
}

void ShenandoahHeap::decrease_humongous_waste(ShenandoahGeneration* generation, size_t bytes) {
  generation->decrease_humongous_waste(bytes);
  if (!generation->is_global()) {
    global_generation()->decrease_humongous_waste(bytes);
  }
}

void ShenandoahHeap::increase_used(ShenandoahGeneration* generation, size_t bytes) {
  generation->increase_used(bytes);
  if (!generation->is_global()) {
    global_generation()->increase_used(bytes);
  }
}

void ShenandoahHeap::decrease_used(ShenandoahGeneration* generation, size_t bytes) {
  generation->decrease_used(bytes);
  if (!generation->is_global()) {
    global_generation()->decrease_used(bytes);
  }
}

void ShenandoahHeap::notify_mutator_alloc_words(size_t words, size_t waste) {
  if (ShenandoahPacing) {
    control_thread()->pacing_notify_alloc(words);
    if (waste > 0) {
      pacer()->claim_for_alloc(waste, true);
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
    control_thread()->notify_heap_changed();
    regulator_thread()->notify_heap_changed();
  }
}

void ShenandoahHeap::handle_old_evacuation(HeapWord* obj, size_t words, bool promotion) {
  // Only register the copy of the object that won the evacuation race.
  card_scan()->register_object_without_lock(obj);

  // Mark the entire range of the evacuated object as dirty.  At next remembered set scan,
  // we will clear dirty bits that do not hold interesting pointers.  It's more efficient to
  // do this in batch, in a background GC thread than to try to carefully dirty only cards
  // that hold interesting pointers right now.
  card_scan()->mark_range_as_dirty(obj, words);

  if (promotion) {
    // This evacuation was a promotion, track this as allocation against old gen
    old_generation()->increase_allocated(words * HeapWordSize);
  }
}

void ShenandoahHeap::handle_old_evacuation_failure() {
  if (_old_gen_oom_evac.try_set()) {
    log_info(gc)("Old gen evac failure.");
  }
}

void ShenandoahHeap::report_promotion_failure(Thread* thread, size_t size) {
  // We squelch excessive reports to reduce noise in logs.
  const size_t MaxReportsPerEpoch = 4;
  static size_t last_report_epoch = 0;
  static size_t epoch_report_count = 0;

  size_t promotion_reserve;
  size_t promotion_expended;

  size_t gc_id = control_thread()->get_gc_id();

  if ((gc_id != last_report_epoch) || (epoch_report_count++ < MaxReportsPerEpoch)) {
    {
      // Promotion failures should be very rare.  Invest in providing useful diagnostic info.
      ShenandoahHeapLocker locker(lock());
      promotion_reserve = get_promoted_reserve();
      promotion_expended = get_promoted_expended();
    }
    PLAB* plab = ShenandoahThreadLocalData::plab(thread);
    size_t words_remaining = (plab == nullptr)? 0: plab->words_remaining();
    const char* promote_enabled = ShenandoahThreadLocalData::allow_plab_promotions(thread)? "enabled": "disabled";
    ShenandoahGeneration* old_gen = old_generation();
    size_t old_capacity = old_gen->max_capacity();
    size_t old_usage = old_gen->used();
    size_t old_free_regions = old_gen->free_unaffiliated_regions();

    log_info(gc, ergo)("Promotion failed, size " SIZE_FORMAT ", has plab? %s, PLAB remaining: " SIZE_FORMAT
                       ", plab promotions %s, promotion reserve: " SIZE_FORMAT ", promotion expended: " SIZE_FORMAT
                       ", old capacity: " SIZE_FORMAT ", old_used: " SIZE_FORMAT ", old unaffiliated regions: " SIZE_FORMAT,
                       size * HeapWordSize, plab == nullptr? "no": "yes",
                       words_remaining * HeapWordSize, promote_enabled, promotion_reserve, promotion_expended,
                       old_capacity, old_usage, old_free_regions);

    if ((gc_id == last_report_epoch) && (epoch_report_count >= MaxReportsPerEpoch)) {
      log_info(gc, ergo)("Squelching additional promotion failure reports for current epoch");
    } else if (gc_id != last_report_epoch) {
      last_report_epoch = gc_id;
      epoch_report_count = 1;
    }
  }
}

HeapWord* ShenandoahHeap::allocate_from_gclab_slow(Thread* thread, size_t size) {
  // New object should fit the GCLAB size
  size_t min_size = MAX2(size, PLAB::min_size());

  // Figure out size of new GCLAB, looking back at heuristics. Expand aggressively.
  size_t new_size = ShenandoahThreadLocalData::gclab_size(thread) * 2;

  // Limit growth of GCLABs to ShenandoahMaxEvacLABRatio * the minimum size.  This enables more equitable distribution of
  // available evacuation buidget between the many threads that are coordinating in the evacuation effort.
  if (ShenandoahMaxEvacLABRatio > 0) {
    log_debug(gc, free)("Allocate new gclab: " SIZE_FORMAT ", " SIZE_FORMAT, new_size, PLAB::min_size() * ShenandoahMaxEvacLABRatio);
    new_size = MIN2(new_size, PLAB::min_size() * ShenandoahMaxEvacLABRatio);
  }

  new_size = MIN2(new_size, PLAB::max_size());
  new_size = MAX2(new_size, PLAB::min_size());

  // Record new heuristic value even if we take any shortcut. This captures
  // the case when moderately-sized objects always take a shortcut. At some point,
  // heuristics should catch up with them.
  ShenandoahThreadLocalData::set_gclab_size(thread, new_size);

  if (new_size < size) {
    // New size still does not fit the object. Fall back to shared allocation.
    // This avoids retiring perfectly good GCLABs, when we encounter a large object.
    log_debug(gc, free)("New gclab size (" SIZE_FORMAT ") is too small for " SIZE_FORMAT, new_size, size);
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

// Establish a new PLAB and allocate size HeapWords within it.
HeapWord* ShenandoahHeap::allocate_from_plab_slow(Thread* thread, size_t size, bool is_promotion) {
  // New object should fit the PLAB size
  size_t min_size = MAX2(size, PLAB::min_size());

  // Figure out size of new PLAB, looking back at heuristics. Expand aggressively.
  size_t cur_size = ShenandoahThreadLocalData::plab_size(thread);
  if (cur_size == 0) {
    cur_size = PLAB::min_size();
  }
  size_t future_size = cur_size * 2;
  // Limit growth of PLABs to ShenandoahMaxEvacLABRatio * the minimum size.  This enables more equitable distribution of
  // available evacuation buidget between the many threads that are coordinating in the evacuation effort.
  if (ShenandoahMaxEvacLABRatio > 0) {
    future_size = MIN2(future_size, PLAB::min_size() * ShenandoahMaxEvacLABRatio);
  }
  future_size = MIN2(future_size, PLAB::max_size());
  future_size = MAX2(future_size, PLAB::min_size());

  size_t unalignment = future_size % CardTable::card_size_in_words();
  if (unalignment != 0) {
    future_size = future_size - unalignment + CardTable::card_size_in_words();
  }

  // Record new heuristic value even if we take any shortcut. This captures
  // the case when moderately-sized objects always take a shortcut. At some point,
  // heuristics should catch up with them.  Note that the requested cur_size may
  // not be honored, but we remember that this is the preferred size.
  ShenandoahThreadLocalData::set_plab_size(thread, future_size);
  if (cur_size < size) {
    // The PLAB to be allocated is still not large enough to hold the object. Fall back to shared allocation.
    // This avoids retiring perfectly good PLABs in order to represent a single large object allocation.
    return nullptr;
  }

  // Retire current PLAB, and allocate a new one.
  PLAB* plab = ShenandoahThreadLocalData::plab(thread);
  if (plab->words_remaining() < PLAB::min_size()) {
    // Retire current PLAB, and allocate a new one.
    // CAUTION: retire_plab may register the remnant filler object with the remembered set scanner without a lock.  This
    // is safe iff it is assured that each PLAB is a whole-number multiple of card-mark memory size and each PLAB is
    // aligned with the start of a card's memory range.
    retire_plab(plab, thread);

    size_t actual_size = 0;
    // allocate_new_plab resets plab_evacuated and plab_promoted and disables promotions if old-gen available is
    // less than the remaining evacuation need.  It also adjusts plab_preallocated and expend_promoted if appropriate.
    HeapWord* plab_buf = allocate_new_plab(min_size, cur_size, &actual_size);
    if (plab_buf == nullptr) {
      if (min_size == PLAB::min_size()) {
        // Disable plab promotions for this thread because we cannot even allocate a plab of minimal size.  This allows us
        // to fail faster on subsequent promotion attempts.
        ShenandoahThreadLocalData::disable_plab_promotions(thread);
      }
      return NULL;
    } else {
      ShenandoahThreadLocalData::enable_plab_retries(thread);
    }
    assert (size <= actual_size, "allocation should fit");
    if (ZeroTLAB) {
      // ..and clear it.
      Copy::zero_to_words(plab_buf, actual_size);
    } else {
      // ...and zap just allocated object.
#ifdef ASSERT
      // Skip mangling the space corresponding to the object header to
      // ensure that the returned space is not considered parsable by
      // any concurrent GC thread.
      size_t hdr_size = oopDesc::header_size();
      Copy::fill_to_words(plab_buf + hdr_size, actual_size - hdr_size, badHeapWordVal);
#endif // ASSERT
    }
    plab->set_buf(plab_buf, actual_size);
    if (is_promotion && !ShenandoahThreadLocalData::allow_plab_promotions(thread)) {
      return nullptr;
    }
    return plab->allocate(size);
  } else {
    // If there's still at least min_size() words available within the current plab, don't retire it.  Let's gnaw
    // away on this plab as long as we can.  Meanwhile, return nullptr to force this particular allocation request
    // to be satisfied with a shared allocation.  By packing more promotions into the previously allocated PLAB, we
    // reduce the likelihood of evacuation failures, and we we reduce the need for downsizing our PLABs.
    return nullptr;
  }
}

// TODO: It is probably most efficient to register all objects (both promotions and evacuations) that were allocated within
// this plab at the time we retire the plab.  A tight registration loop will run within both code and data caches.  This change
// would allow smaller and faster in-line implementation of alloc_from_plab().  Since plabs are aligned on card-table boundaries,
// this object registration loop can be performed without acquiring a lock.
void ShenandoahHeap::retire_plab(PLAB* plab, Thread* thread) {
  // We don't enforce limits on plab_evacuated.  We let it consume all available old-gen memory in order to reduce
  // probability of an evacuation failure.  We do enforce limits on promotion, to make sure that excessive promotion
  // does not result in an old-gen evacuation failure.  Note that a failed promotion is relatively harmless.  Any
  // object that fails to promote in the current cycle will be eligible for promotion in a subsequent cycle.

  // When the plab was instantiated, its entirety was treated as if the entire buffer was going to be dedicated to
  // promotions.  Now that we are retiring the buffer, we adjust for the reality that the plab is not entirely promotions.
  //  1. Some of the plab may have been dedicated to evacuations.
  //  2. Some of the plab may have been abandoned due to waste (at the end of the plab).
  size_t not_promoted =
    ShenandoahThreadLocalData::get_plab_preallocated_promoted(thread) - ShenandoahThreadLocalData::get_plab_promoted(thread);
  ShenandoahThreadLocalData::reset_plab_promoted(thread);
  ShenandoahThreadLocalData::reset_plab_evacuated(thread);
  ShenandoahThreadLocalData::set_plab_preallocated_promoted(thread, 0);
  if (not_promoted > 0) {
    unexpend_promoted(not_promoted);
  }
  size_t waste = plab->waste();
  HeapWord* top = plab->top();
  plab->retire();
  if (top != nullptr && plab->waste() > waste && is_in_old(top)) {
    // If retiring the plab created a filler object, then we
    // need to register it with our card scanner so it can
    // safely walk the region backing the plab.
    log_debug(gc)("retire_plab() is registering remnant of size " SIZE_FORMAT " at " PTR_FORMAT,
                  plab->waste() - waste, p2i(top));
    card_scan()->register_object_without_lock(top);
  }
}

void ShenandoahHeap::retire_plab(PLAB* plab) {
  Thread* thread = Thread::current();
  retire_plab(plab, thread);
}

void ShenandoahHeap::cancel_old_gc() {
  shenandoah_assert_safepoint();
  assert(_old_generation != nullptr, "Should only have mixed collections in generation mode.");
  if (_old_generation->state() == ShenandoahOldGeneration::IDLE) {
    assert(!old_generation()->is_concurrent_mark_in_progress(), "Cannot be marking in IDLE");
    assert(!old_heuristics()->has_coalesce_and_fill_candidates(), "Cannot have coalesce and fill candidates in IDLE");
    assert(!old_heuristics()->unprocessed_old_collection_candidates(), "Cannot have mixed collection candidates in IDLE");
    assert(!young_generation()->is_bootstrap_cycle(), "Cannot have old mark queues if IDLE");
  } else {
    log_info(gc)("Terminating old gc cycle.");
    // Stop marking
    old_generation()->cancel_marking();
    // Stop coalescing undead objects
    set_prepare_for_old_mark_in_progress(false);
    // Stop tracking old regions
    old_heuristics()->abandon_collection_candidates();
    // Remove old generation access to young generation mark queues
    young_generation()->set_old_gen_task_queues(nullptr);
    // Transition to IDLE now.
    _old_generation->transition_to(ShenandoahOldGeneration::IDLE);
  }
}

// xfer_limit is the maximum we're able to transfer from young to old
void ShenandoahHeap::adjust_generation_sizes_for_next_cycle(
  size_t xfer_limit, size_t young_cset_regions, size_t old_cset_regions) {

  // Make sure old-generation is large enough, but no larger, than is necessary to hold mixed evacuations
  // and promotions if we anticipate either.
  size_t region_size_bytes = ShenandoahHeapRegion::region_size_bytes();
  size_t promo_load = get_promotion_potential();
  // The free set will reserve this amount of memory to hold young evacuations
  size_t young_reserve = (young_generation()->max_capacity() * ShenandoahEvacReserve) / 100;
  size_t old_reserve = 0;
  size_t mixed_candidates = old_heuristics()->unprocessed_old_collection_candidates();
  bool doing_mixed = (mixed_candidates > 0);
  bool doing_promotions = promo_load > 0;

  // round down
  size_t max_old_region_xfer = xfer_limit / region_size_bytes;

  // We can limit the reserve to the size of anticipated promotions
  size_t max_old_reserve = young_reserve * ShenandoahOldEvacRatioPercent / (100 - ShenandoahOldEvacRatioPercent);
  // Here's the algebra:
  //  TotalEvacuation = OldEvacuation + YoungEvacuation
  //  OldEvacuation = TotalEvacuation*(ShenandoahOldEvacRatioPercent/100)
  //  OldEvacuation = YoungEvacuation * (ShenandoahOldEvacRatioPercent/100)/(1 - ShenandoahOldEvacRatioPercent/100)
  //  OldEvacuation = YoungEvacuation * ShenandoahOldEvacRatioPercent/(100 - ShenandoahOldEvacRatioPercent)

  size_t reserve_for_mixed, reserve_for_promo;
  if (doing_mixed) {
    assert(old_generation()->available() >= old_generation()->free_unaffiliated_regions() * region_size_bytes,
           "Unaffiliated available must be less than total available");

    // We want this much memory to be unfragmented in order to reliably evacuate old.  This is conservative because we
    // may not evacuate the entirety of unprocessed candidates in a single mixed evacuation.
    size_t max_evac_need = (size_t)
      (old_heuristics()->unprocessed_old_collection_candidates_live_memory() * ShenandoahOldEvacWaste);
    size_t old_fragmented_available =
      old_generation()->available() - old_generation()->free_unaffiliated_regions() * region_size_bytes;
    reserve_for_mixed = max_evac_need + old_fragmented_available;
    if (reserve_for_mixed > max_old_reserve) {
      reserve_for_mixed = max_old_reserve;
    }
  } else {
    reserve_for_mixed = 0;
  }

  size_t available_for_promotions = max_old_reserve - reserve_for_mixed;
  if (doing_promotions) {
    // We're only promoting and we have a maximum bound on the amount to be promoted
    reserve_for_promo = (size_t) (promo_load * ShenandoahPromoEvacWaste);
    if (reserve_for_promo > available_for_promotions) {
      reserve_for_promo = available_for_promotions;
    }
  } else {
    reserve_for_promo = 0;
  }
  old_reserve = reserve_for_mixed + reserve_for_promo;
  assert(old_reserve <= max_old_reserve, "cannot reserve more than max for old evacuations");
  size_t old_available = old_generation()->available() + old_cset_regions * region_size_bytes;
  size_t young_available = young_generation()->available() + young_cset_regions * region_size_bytes;
  size_t old_region_deficit = 0;
  size_t old_region_surplus = 0;
  if (old_available >= old_reserve) {
    size_t old_excess = old_available - old_reserve;
    size_t excess_regions = old_excess / region_size_bytes;
    size_t unaffiliated_old_regions = old_generation()->free_unaffiliated_regions() + old_cset_regions;
    size_t unaffiliated_old = unaffiliated_old_regions * region_size_bytes;
    if (unaffiliated_old_regions < excess_regions) {
      // We'll give only unaffiliated old to young, which is known to be less than the excess.
      old_region_surplus = unaffiliated_old_regions;
    } else {
      // unaffiliated_old_regions > excess_regions, so we only give away the excess.
      old_region_surplus = excess_regions;
    }
  } else {
    // We need to request transfer from YOUNG.  Ignore that this will directly impact young_generation()->max_capacity(),
    // indirectly impacting young_reserve and old_reserve.  These computations are conservative.
    size_t old_need = old_reserve - old_available;
    // Round up the number of regions needed from YOUNG
    old_region_deficit = (old_need + region_size_bytes - 1) / region_size_bytes;
  }
  if (old_region_deficit > max_old_region_xfer) {
    // If we're running short on young-gen memory, limit the xfer.  Old-gen collection activities will be curtailed
    // if the budget is smaller than desired.
    old_region_deficit = max_old_region_xfer;
  }
  set_old_region_surplus(old_region_surplus);
  set_old_region_deficit(old_region_deficit);
}

// Called from stubs in JIT code or interpreter
HeapWord* ShenandoahHeap::allocate_new_tlab(size_t min_size,
                                            size_t requested_size,
                                            size_t* actual_size) {
  ShenandoahAllocRequest req = ShenandoahAllocRequest::for_tlab(min_size, requested_size);
  HeapWord* res = allocate_memory(req, false);
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
  HeapWord* res = allocate_memory(req, false);
  if (res != nullptr) {
    *actual_size = req.actual_size();
  } else {
    *actual_size = 0;
  }
  return res;
}

HeapWord* ShenandoahHeap::allocate_new_plab(size_t min_size,
                                            size_t word_size,
                                            size_t* actual_size) {
  // Align requested sizes to card sized multiples
  size_t words_in_card = CardTable::card_size_in_words();
  size_t align_mask = ~(words_in_card - 1);
  min_size = (min_size + words_in_card - 1) & align_mask;
  word_size = (word_size + words_in_card - 1) & align_mask;
  ShenandoahAllocRequest req = ShenandoahAllocRequest::for_plab(min_size, word_size);
  // Note that allocate_memory() sets a thread-local flag to prohibit further promotions by this thread
  // if we are at risk of infringing on the old-gen evacuation budget.
  HeapWord* res = allocate_memory(req, false);
  if (res != nullptr) {
    *actual_size = req.actual_size();
  } else {
    *actual_size = 0;
  }
  return res;
}

// is_promotion is true iff this allocation is known for sure to hold the result of young-gen evacuation
// to old-gen.  plab allocates are not known as such, since they may hold old-gen evacuations.
HeapWord* ShenandoahHeap::allocate_memory(ShenandoahAllocRequest& req, bool is_promotion) {
  intptr_t pacer_epoch = 0;
  bool in_new_region = false;
  HeapWord* result = nullptr;

  if (req.is_mutator_alloc()) {
    if (ShenandoahPacing) {
      pacer()->pace_for_alloc(req.size());
      pacer_epoch = pacer()->epoch();
    }

    if (!ShenandoahAllocFailureALot || !should_inject_alloc_failure()) {
      result = allocate_memory_under_lock(req, in_new_region, is_promotion);
    }

    // Allocation failed, block until control thread reacted, then retry allocation.
    //
    // It might happen that one of the threads requesting allocation would unblock
    // way later after GC happened, only to fail the second allocation, because
    // other threads have already depleted the free storage. In this case, a better
    // strategy is to try again, as long as GC makes progress (or until at least
    // one full GC has completed).
    size_t original_count = shenandoah_policy()->full_gc_count();
    while (result == nullptr
        && (_progress_last_gc.is_set() || original_count == shenandoah_policy()->full_gc_count())) {
      control_thread()->handle_alloc_failure(req);
      result = allocate_memory_under_lock(req, in_new_region, is_promotion);
    }

  } else {
    assert(req.is_gc_alloc(), "Can only accept GC allocs here");
    result = allocate_memory_under_lock(req, in_new_region, is_promotion);
    // Do not call handle_alloc_failure() here, because we cannot block.
    // The allocation failure would be handled by the LRB slowpath with handle_alloc_failure_evac().
  }

  if (in_new_region) {
    control_thread()->notify_heap_changed();
    regulator_thread()->notify_heap_changed();
  }

  if (result == nullptr) {
    req.set_actual_size(0);
  }

  // This is called regardless of the outcome of the allocation to account
  // for any waste created by retiring regions with this request.
  increase_used(req);

  if (result != nullptr) {
    size_t requested = req.size();
    size_t actual = req.actual_size();

    assert (req.is_lab_alloc() || (requested == actual),
            "Only LAB allocations are elastic: %s, requested = " SIZE_FORMAT ", actual = " SIZE_FORMAT,
            ShenandoahAllocRequest::alloc_type_to_string(req.type()), requested, actual);

    if (req.is_mutator_alloc()) {
      // If we requested more than we were granted, give the rest back to pacer.
      // This only matters if we are in the same pacing epoch: do not try to unpace
      // over the budget for the other phase.
      if (ShenandoahPacing && (pacer_epoch > 0) && (requested > actual)) {
        pacer()->unpace_for_alloc(pacer_epoch, requested - actual);
      }
    }
  }

  return result;
}

HeapWord* ShenandoahHeap::allocate_memory_under_lock(ShenandoahAllocRequest& req, bool& in_new_region, bool is_promotion) {
  bool try_smaller_lab_size = false;
  size_t smaller_lab_size;
  {
    // promotion_eligible pertains only to PLAB allocations, denoting that the PLAB is allowed to allocate for promotions.
    bool promotion_eligible = false;
    bool allow_allocation = true;
    bool plab_alloc = false;
    size_t requested_bytes = req.size() * HeapWordSize;
    HeapWord* result = nullptr;
    ShenandoahHeapLocker locker(lock());
    Thread* thread = Thread::current();

    if (mode()->is_generational()) {
      if (req.affiliation() == YOUNG_GENERATION) {
        if (req.is_mutator_alloc()) {
          size_t young_words_available = young_generation()->available() / HeapWordSize;
          if (ShenandoahElasticTLAB && req.is_lab_alloc() && (req.min_size() < young_words_available)) {
            // Allow ourselves to try a smaller lab size even if requested_bytes <= young_available.  We may need a smaller
            // lab size because young memory has become too fragmented.
            try_smaller_lab_size = true;
            smaller_lab_size = (young_words_available < req.size())? young_words_available: req.size();
          } else if (req.size() > young_words_available) {
            // Can't allocate because even min_size() is larger than remaining young_available
            log_info(gc, ergo)("Unable to shrink %s alloc request of minimum size: " SIZE_FORMAT
                               ", young words available: " SIZE_FORMAT, req.type_string(),
                               HeapWordSize * (req.is_lab_alloc()? req.min_size(): req.size()), young_words_available);
            return nullptr;
          }
        }
      } else {                    // reg.affiliation() == OLD_GENERATION
        assert(req.type() != ShenandoahAllocRequest::_alloc_gclab, "GCLAB pertains only to young-gen memory");
        if (req.type() ==  ShenandoahAllocRequest::_alloc_plab) {
          plab_alloc = true;
          size_t promotion_avail = get_promoted_reserve();
          size_t promotion_expended = get_promoted_expended();
          if (promotion_expended + requested_bytes > promotion_avail) {
            promotion_avail = 0;
            if (get_old_evac_reserve() == 0) {
              // There are no old-gen evacuations in this pass.  There's no value in creating a plab that cannot
              // be used for promotions.
              allow_allocation = false;
            }
          } else {
            promotion_avail = promotion_avail - (promotion_expended + requested_bytes);
            promotion_eligible = true;
          }
        } else if (is_promotion) {
          // This is a shared alloc for promotion
          size_t promotion_avail = get_promoted_reserve();
          size_t promotion_expended = get_promoted_expended();
          if (promotion_expended + requested_bytes > promotion_avail) {
            promotion_avail = 0;
          } else {
            promotion_avail = promotion_avail - (promotion_expended + requested_bytes);
          }
          if (promotion_avail == 0) {
            // We need to reserve the remaining memory for evacuation.  Reject this allocation.  The object will be
            // evacuated to young-gen memory and promoted during a future GC pass.
            return nullptr;
          }
          // Else, we'll allow the allocation to proceed.  (Since we hold heap lock, the tested condition remains true.)
        } else {
          // This is a shared allocation for evacuation.  Memory has already been reserved for this purpose.
        }
      }
    } // This ends the is_generational() block

    // First try the original request.  If TLAB request size is greater than available, allocate() will attempt to downsize
    // request to fit within available memory.
    result = (allow_allocation)? _free_set->allocate(req, in_new_region): nullptr;
    if (result != nullptr) {
      if (req.is_old()) {
        ShenandoahThreadLocalData::reset_plab_promoted(thread);
        if (req.is_gc_alloc()) {
          bool disable_plab_promotions = false;
          if (req.type() ==  ShenandoahAllocRequest::_alloc_plab) {
            if (promotion_eligible) {
              size_t actual_size = req.actual_size() * HeapWordSize;
              // The actual size of the allocation may be larger than the requested bytes (due to alignment on card boundaries).
              // If this puts us over our promotion budget, we need to disable future PLAB promotions for this thread.
              if (get_promoted_expended() + actual_size <= get_promoted_reserve()) {
                // Assume the entirety of this PLAB will be used for promotion.  This prevents promotion from overreach.
                // When we retire this plab, we'll unexpend what we don't really use.
                ShenandoahThreadLocalData::enable_plab_promotions(thread);
                expend_promoted(actual_size);
                assert(get_promoted_expended() <= get_promoted_reserve(), "Do not expend more promotion than budgeted");
                ShenandoahThreadLocalData::set_plab_preallocated_promoted(thread, actual_size);
              } else {
                disable_plab_promotions = true;
              }
            } else {
              disable_plab_promotions = true;
            }
            if (disable_plab_promotions) {
              // Disable promotions in this thread because entirety of this PLAB must be available to hold old-gen evacuations.
              ShenandoahThreadLocalData::disable_plab_promotions(thread);
              ShenandoahThreadLocalData::set_plab_preallocated_promoted(thread, 0);
            }
          } else if (is_promotion) {
            // Shared promotion.  Assume size is requested_bytes.
            expend_promoted(requested_bytes);
            assert(get_promoted_expended() <= get_promoted_reserve(), "Do not expend more promotion than budgeted");
          }
        }

        // Register the newly allocated object while we're holding the global lock since there's no synchronization
        // built in to the implementation of register_object().  There are potential races when multiple independent
        // threads are allocating objects, some of which might span the same card region.  For example, consider
        // a card table's memory region within which three objects are being allocated by three different threads:
        //
        // objects being "concurrently" allocated:
        //    [-----a------][-----b-----][--------------c------------------]
        //            [---- card table memory range --------------]
        //
        // Before any objects are allocated, this card's memory range holds no objects.  Note that allocation of object a
        //   wants to set the starts-object, first-start, and last-start attributes of the preceding card region.
        //   allocation of object b wants to set the starts-object, first-start, and last-start attributes of this card region.
        //   allocation of object c also wants to set the starts-object, first-start, and last-start attributes of this
        //   card region.
        //
        // The thread allocating b and the thread allocating c can "race" in various ways, resulting in confusion, such as
        // last-start representing object b while first-start represents object c.  This is why we need to require all
        // register_object() invocations to be "mutually exclusive" with respect to each card's memory range.
        ShenandoahHeap::heap()->card_scan()->register_object(result);
      }
    } else {
      // The allocation failed.  If this was a plab allocation, We've already retired it and no longer have a plab.
      if (req.is_old() && req.is_gc_alloc() && (req.type() == ShenandoahAllocRequest::_alloc_plab)) {
        // We don't need to disable PLAB promotions because there is no PLAB.  We leave promotions enabled because
        // this allows the surrounding infrastructure to retry alloc_plab_slow() with a smaller PLAB size.
        ShenandoahThreadLocalData::set_plab_preallocated_promoted(thread, 0);
      }
    }
    if ((result != nullptr) || !try_smaller_lab_size) {
      return result;
    }
    // else, fall through to try_smaller_lab_size
  } // This closes the block that holds the heap lock, releasing the lock.

  // We failed to allocate the originally requested lab size.  Let's see if we can allocate a smaller lab size.
  if (req.size() == smaller_lab_size) {
    // If we were already trying to allocate min size, no value in attempting to repeat the same.  End the recursion.
    return nullptr;
  }

  // We arrive here if the tlab allocation request can be resized to fit within young_available
  assert((req.affiliation() == YOUNG_GENERATION) && req.is_lab_alloc() && req.is_mutator_alloc() &&
         (smaller_lab_size < req.size()), "Only shrink allocation request size for TLAB allocations");

  // By convention, ShenandoahAllocationRequest is primarily read-only.  The only mutable instance data is represented by
  // actual_size(), which is overwritten with the size of the allocaion when the allocation request is satisfied.  We use a
  // recursive call here rather than introducing new methods to mutate the existing ShenandoahAllocationRequest argument.
  // Mutation of the existing object might result in astonishing results if calling contexts assume the content of immutable
  // fields remain constant.  The original TLAB allocation request was for memory that exceeded the current capacity.  We'll
  // attempt to allocate a smaller TLAB.  If this is successful, we'll update actual_size() of our incoming
  // ShenandoahAllocRequest.  If the recursive request fails, we'll simply return nullptr.

  // Note that we've relinquished the HeapLock and some other thread may perform additional allocation before our recursive
  // call reacquires the lock.  If that happens, we will need another recursive call to further reduce the size of our request
  // for each time another thread allocates young memory during the brief intervals that the heap lock is available to
  // interfering threads.  We expect this interference to be rare.  The recursion bottoms out when young_available is
  // smaller than req.min_size().  The inner-nested call to allocate_memory_under_lock() uses the same min_size() value
  // as this call, but it uses a preferred size() that is smaller than our preferred size, and is no larger than what we most
  // recently saw as the memory currently available within the young generation.

  // TODO: At the expense of code clarity, we could rewrite this recursive solution to use iteration.  We need at most one
  // extra instance of the ShenandoahAllocRequest, which we can re-initialize multiple times inside a loop, with one iteration
  // of the loop required for each time the existing solution would recurse.  An iterative solution would be more efficient
  // in CPU time and stack memory utilization.  The expectation is that it is very rare that we would recurse more than once
  // so making this change is not currently seen as a high priority.

  ShenandoahAllocRequest smaller_req = ShenandoahAllocRequest::for_tlab(req.min_size(), smaller_lab_size);

  // Note that shrinking the preferred size gets us past the gatekeeper that checks whether there's available memory to
  // satisfy the allocation request.  The reality is the actual TLAB size is likely to be even smaller, because it will
  // depend on how much memory is available within mutator regions that are not yet fully used.
  HeapWord* result = allocate_memory_under_lock(smaller_req, in_new_region, is_promotion);
  if (result != nullptr) {
    req.set_actual_size(smaller_req.actual_size());
  }
  return result;
}

HeapWord* ShenandoahHeap::mem_allocate(size_t size,
                                        bool*  gc_overhead_limit_was_exceeded) {
  ShenandoahAllocRequest req = ShenandoahAllocRequest::for_shared(size);
  return allocate_memory(req, false);
}

MetaWord* ShenandoahHeap::satisfy_failed_metadata_allocation(ClassLoaderData* loader_data,
                                                             size_t size,
                                                             Metaspace::MetadataType mdtype) {
  MetaWord* result;

  // Inform metaspace OOM to GC heuristics if class unloading is possible.
  ShenandoahHeuristics* h = global_generation()->heuristics();
  if (h->can_unload_classes()) {
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

// Unlike ShenandoahEvacuationTask, this iterates over all regions rather than just the collection set.
// This is needed in order to promote humongous start regions if age() >= tenure threshold.
class ShenandoahGenerationalEvacuationTask : public WorkerTask {
private:
  ShenandoahHeap* const _sh;
  ShenandoahRegionIterator *_regions;
  bool _concurrent;
  uint _tenuring_threshold;

public:
  ShenandoahGenerationalEvacuationTask(ShenandoahHeap* sh,
                                       ShenandoahRegionIterator* iterator,
                                       bool concurrent) :
    WorkerTask("Shenandoah Evacuation"),
    _sh(sh),
    _regions(iterator),
    _concurrent(concurrent),
    _tenuring_threshold(0)
  {
    if (_sh->mode()->is_generational()) {
      _tenuring_threshold = _sh->age_census()->tenuring_threshold();
    }
  }

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
    ShenandoahMarkingContext* const ctx = ShenandoahHeap::heap()->marking_context();
    size_t region_size_bytes = ShenandoahHeapRegion::region_size_bytes();
    size_t old_garbage_threshold = (region_size_bytes * ShenandoahOldGarbageThreshold) / 100;
    while ((r = _regions->next()) != nullptr) {
      log_debug(gc)("GenerationalEvacuationTask do_work(), looking at %s region " SIZE_FORMAT ", (age: %d) [%s, %s, %s]",
                    r->is_old()? "old": r->is_young()? "young": "free", r->index(), r->age(),
                    r->is_active()? "active": "inactive",
                    r->is_humongous()? (r->is_humongous_start()? "humongous_start": "humongous_continuation"): "regular",
                    r->is_cset()? "cset": "not-cset");

      if (r->is_cset()) {
        assert(r->has_live(), "Region " SIZE_FORMAT " should have been reclaimed early", r->index());
        _sh->marked_object_iterate(r, &cl);
        if (ShenandoahPacing) {
          _sh->pacer()->report_evac(r->used() >> LogHeapWordSize);
        }
      } else if (r->is_young() && r->is_active() && (r->age() >= _tenuring_threshold)) {
        HeapWord* tams = ctx->top_at_mark_start(r);
        if (r->is_humongous_start()) {
          // We promote humongous_start regions along with their affiliated continuations during evacuation rather than
          // doing this work during a safepoint.  We cannot put humongous regions into the collection set because that
          // triggers the load-reference barrier (LRB) to copy on reference fetch.
          r->promote_humongous();
        } else if (r->is_regular() && (r->get_top_before_promote() != nullptr)) {
          assert(r->garbage_before_padded_for_promote() < old_garbage_threshold,
                 "Region " SIZE_FORMAT " has too much garbage for promotion", r->index());
          assert(r->get_top_before_promote() == tams,
                 "Region " SIZE_FORMAT " has been used for allocations before promotion", r->index());
          // Likewise, we cannot put promote-in-place regions into the collection set because that would also trigger
          // the LRB to copy on reference fetch.
          r->promote_in_place();
        }
        // Aged humongous continuation regions are handled with their start region.  If an aged regular region has
        // more garbage than ShenandoahOldGarbageTrheshold, we'll promote by evacuation.  If there is room for evacuation
        // in this cycle, the region will be in the collection set.  If there is not room, the region will be promoted
        // by evacuation in some future GC cycle.

        // If an aged regular region has received allocations during the current cycle, we do not promote because the
        // newly allocated objects do not have appropriate age; this region's age will be reset to zero at end of cycle.
      }
      // else, region is free, or OLD, or not in collection set, or humongous_continuation,
      // or is young humongous_start that is too young to be promoted

      if (_sh->check_cancelled_gc_and_yield(_concurrent)) {
        break;
      }
    }
  }
};

void ShenandoahHeap::evacuate_collection_set(bool concurrent) {
  if (ShenandoahHeap::heap()->mode()->is_generational()) {
    ShenandoahRegionIterator regions;
    ShenandoahGenerationalEvacuationTask task(this, &regions, concurrent);
    workers()->run_task(&task);
  } else {
    ShenandoahEvacuationTask task(this, _collection_set, concurrent);
    workers()->run_task(&task);
  }
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

size_t ShenandoahHeap::trash_humongous_region_at(ShenandoahHeapRegion* start) {
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
  return required_regions;
}

class ShenandoahCheckCleanGCLABClosure : public ThreadClosure {
public:
  ShenandoahCheckCleanGCLABClosure() {}
  void do_thread(Thread* thread) {
    PLAB* gclab = ShenandoahThreadLocalData::gclab(thread);
    assert(gclab != nullptr, "GCLAB should be initialized for %s", thread->name());
    assert(gclab->words_remaining() == 0, "GCLAB should not need retirement");

    PLAB* plab = ShenandoahThreadLocalData::plab(thread);
    assert(plab != nullptr, "PLAB should be initialized for %s", thread->name());
    assert(plab->words_remaining() == 0, "PLAB should not need retirement");
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

    PLAB* plab = ShenandoahThreadLocalData::plab(thread);
    assert(plab != nullptr, "PLAB should be initialized for %s", thread->name());

    // There are two reasons to retire all plabs between old-gen evacuation passes.
    //  1. We need to make the plab memory parseable by remembered-set scanning.
    //  2. We need to establish a trustworthy UpdateWaterMark value within each old-gen heap region
    ShenandoahHeap::heap()->retire_plab(plab, thread);
    if (_resize && ShenandoahThreadLocalData::plab_size(thread) > 0) {
      ShenandoahThreadLocalData::set_plab_size(thread, 0);
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
  if (ShenandoahElasticTLAB) {
    if (mode()->is_generational()) {
      return MIN2(ShenandoahHeapRegion::max_tlab_size_bytes(), young_generation()->available());
    } else {
      // With Elastic TLABs, return the max allowed size, and let the allocation path
      // figure out the safe size for current allocation.
      return ShenandoahHeapRegion::max_tlab_size_bytes();
    }
  } else {
    return MIN2(_free_set->unsafe_peek_free(), ShenandoahHeapRegion::max_tlab_size_bytes());
  }
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
  if (_shenandoah_policy->is_at_shutdown()) {
    return;
  }

  tcl->do_thread(_control_thread);
  tcl->do_thread(_regulator_thread);
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

    evac_tracker()->print_global_on(&ls);

    ls.cr();
    ls.cr();
  }
}

void ShenandoahHeap::on_cycle_start(GCCause::Cause cause, ShenandoahGeneration* generation) {
  set_gc_cause(cause);
  set_gc_generation(generation);

  shenandoah_policy()->record_cycle_start();
  generation->heuristics()->record_cycle_start();
}

void ShenandoahHeap::on_cycle_end(ShenandoahGeneration* generation) {
  generation->heuristics()->record_cycle_end();
  if (mode()->is_generational() && (generation->is_global() || upgraded_to_full())) {
    // If we just completed a GLOBAL GC, claim credit for completion of young-gen and old-gen GC as well
    young_generation()->heuristics()->record_cycle_end();
    old_generation()->heuristics()->record_cycle_end();
  }
  set_gc_cause(GCCause::_no_gc);
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

void ShenandoahHeap::do_class_unloading() {
  _unloader.unload();
}

void ShenandoahHeap::stw_weak_refs(bool full_gc) {
  // Weak refs processing
  ShenandoahPhaseTimings::Phase phase = full_gc ? ShenandoahPhaseTimings::full_gc_weakrefs
                                                : ShenandoahPhaseTimings::degen_gc_weakrefs;
  ShenandoahTimingsTracker t(phase);
  ShenandoahGCWorkerPhase worker_phase(phase);
  active_generation()->ref_processor()->process_references(phase, workers(), false /* concurrent */);
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

void ShenandoahHeap::set_evacuation_reserve_quantities(bool is_valid) {
  _has_evacuation_reserve_quantities = is_valid;
}

void ShenandoahHeap::set_concurrent_young_mark_in_progress(bool in_progress) {
  uint mask;
  assert(!has_forwarded_objects(), "Young marking is not concurrent with evacuation");
  if (!in_progress && is_concurrent_old_mark_in_progress()) {
    assert(mode()->is_generational(), "Only generational GC has old marking");
    assert(_gc_state.is_set(MARKING), "concurrent_old_marking_in_progress implies MARKING");
    // If old-marking is in progress when we turn off YOUNG_MARKING, leave MARKING (and OLD_MARKING) on
    mask = YOUNG_MARKING;
  } else {
    mask = MARKING | YOUNG_MARKING;
  }
  set_gc_state_mask(mask, in_progress);
  manage_satb_barrier(in_progress);
}

void ShenandoahHeap::set_concurrent_old_mark_in_progress(bool in_progress) {
#ifdef ASSERT
  // has_forwarded_objects() iff UPDATEREFS or EVACUATION
  bool has_forwarded = has_forwarded_objects();
  bool updating_or_evacuating = _gc_state.is_set(UPDATEREFS | EVACUATION);
  bool evacuating = _gc_state.is_set(EVACUATION);
  assert ((has_forwarded == updating_or_evacuating) || (evacuating && !has_forwarded && collection_set()->is_empty()),
          "Updating or evacuating iff has forwarded object, or evacuation phase is promoting in place without forwarding");
#endif
  if (!in_progress && is_concurrent_young_mark_in_progress()) {
    // If young-marking is in progress when we turn off OLD_MARKING, leave MARKING (and YOUNG_MARKING) on
    assert(_gc_state.is_set(MARKING), "concurrent_young_marking_in_progress implies MARKING");
    set_gc_state_mask(OLD_MARKING, in_progress);
  } else {
    set_gc_state_mask(MARKING | OLD_MARKING, in_progress);
  }
  manage_satb_barrier(in_progress);
}

void ShenandoahHeap::set_prepare_for_old_mark_in_progress(bool in_progress) {
  // Unlike other set-gc-state functions, this may happen outside safepoint.
  // Is only set and queried by control thread, so no coherence issues.
  _prepare_for_old_mark = in_progress;
}

void ShenandoahHeap::set_aging_cycle(bool in_progress) {
  _is_aging_cycle.set_cond(in_progress);
}

void ShenandoahHeap::manage_satb_barrier(bool active) {
  if (is_concurrent_mark_in_progress()) {
    // Ignore request to deactivate barrier while concurrent mark is in progress.
    // Do not attempt to re-activate the barrier if it is already active.
    if (active && !ShenandoahBarrierSet::satb_mark_queue_set().is_active()) {
      ShenandoahBarrierSet::satb_mark_queue_set().set_active_all_threads(active, !active);
    }
  } else {
    // No concurrent marking is in progress so honor request to deactivate,
    // but only if the barrier is already active.
    if (!active && ShenandoahBarrierSet::satb_mark_queue_set().is_active()) {
      ShenandoahBarrierSet::satb_mark_queue_set().set_active_all_threads(active, !active);
    }
  }
}

void ShenandoahHeap::set_evacuation_in_progress(bool in_progress) {
  assert(ShenandoahSafepoint::is_at_shenandoah_safepoint(), "Only call this at safepoint");
  set_gc_state_mask(EVACUATION, in_progress);
}

void ShenandoahHeap::set_concurrent_strong_root_in_progress(bool in_progress) {
  if (in_progress) {
    _concurrent_strong_root_in_progress.set();
  } else {
    _concurrent_strong_root_in_progress.unset();
  }
}

void ShenandoahHeap::set_concurrent_weak_root_in_progress(bool cond) {
  set_gc_state_mask(WEAK_ROOTS, cond);
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

void ShenandoahHeap::cancel_concurrent_mark() {
  _young_generation->cancel_marking();
  _old_generation->cancel_marking();
  _global_generation->cancel_marking();

  ShenandoahBarrierSet::satb_mark_queue_set().abandon_partial_marking();
}

void ShenandoahHeap::cancel_gc(GCCause::Cause cause) {
  if (try_cancel_gc()) {
    FormatBuffer<> msg("Cancelling GC: %s", GCCause::to_string(cause));
    log_info(gc)("%s", msg.buffer());
    Events::log(Thread::current(), "%s", msg.buffer());
    _cancel_requested_time = os::elapsedTime();
    if (cause == GCCause::_shenandoah_upgrade_to_full_gc) {
      _upgraded_to_full = true;
    }
  }
}

uint ShenandoahHeap::max_workers() {
  return _max_workers;
}

void ShenandoahHeap::stop() {
  // The shutdown sequence should be able to terminate when GC is running.

  // Step 1. Notify policy to disable event recording and prevent visiting gc threads during shutdown
  _shenandoah_policy->record_shutdown();

  // Step 2. Stop requesting collections.
  regulator_thread()->stop();

  // Step 3. Notify control thread that we are in shutdown.
  // Note that we cannot do that with stop(), because stop() is blocking and waits for the actual shutdown.
  // Doing stop() here would wait for the normal GC cycle to complete, never falling through to cancel below.
  control_thread()->prepare_for_graceful_shutdown();

  // Step 4. Notify GC workers that we are cancelling GC.
  cancel_gc(GCCause::_shenandoah_stop_vm);

  // Step 5. Wait until GC worker exits normally.
  control_thread()->stop();
}

void ShenandoahHeap::stw_unload_classes(bool full_gc) {
  if (!unload_classes()) return;
  // Unload classes and purge SystemDictionary.
  {
    ShenandoahPhaseTimings::Phase phase = full_gc ?
                                          ShenandoahPhaseTimings::full_gc_purge_class_unload :
                                          ShenandoahPhaseTimings::degen_gc_purge_class_unload;
    ShenandoahIsAliveSelector is_alive;
    CodeCache::UnloadingScope scope(is_alive.is_alive_closure());
    ShenandoahGCPhase gc_phase(phase);
    ShenandoahGCWorkerPhase worker_phase(phase);
    bool purged_class = SystemDictionary::do_unloading(gc_timer());

    uint num_workers = _workers->active_workers();
    ShenandoahClassUnloadingTask unlink_task(phase, num_workers, purged_class);
    _workers->run_task(&unlink_task);
  }

  {
    ShenandoahGCPhase phase(full_gc ?
                            ShenandoahPhaseTimings::full_gc_purge_cldg :
                            ShenandoahPhaseTimings::degen_gc_purge_cldg);
    ClassLoaderDataGraph::purge(/*at_safepoint*/true);
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
  set_gc_state_mask(HAS_FORWARDED, cond);
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

address ShenandoahHeap::gc_state_addr() {
  return (address) ShenandoahHeap::heap()->_gc_state.addr_of();
}

void ShenandoahHeap::reset_bytes_allocated_since_gc_start() {
  if (mode()->is_generational()) {
    young_generation()->reset_bytes_allocated_since_gc_start();
    old_generation()->reset_bytes_allocated_since_gc_start();
  }

  global_generation()->reset_bytes_allocated_since_gc_start();
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
    if (active_generation()->contains(r)) {
      assert((r->is_pinned() && r->pin_count() > 0) || (!r->is_pinned() && r->pin_count() == 0),
             "Region " SIZE_FORMAT " pinning status is inconsistent", i);
    }
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
  assert (_verifier != nullptr, "sanity");
  return _verifier;
}

template<bool CONCURRENT>
class ShenandoahUpdateHeapRefsTask : public WorkerTask {
private:
  ShenandoahHeap* _heap;
  ShenandoahRegionIterator* _regions;
  ShenandoahRegionChunkIterator* _work_chunks;

public:
  explicit ShenandoahUpdateHeapRefsTask(ShenandoahRegionIterator* regions,
                                        ShenandoahRegionChunkIterator* work_chunks) :
    WorkerTask("Shenandoah Update References"),
    _heap(ShenandoahHeap::heap()),
    _regions(regions),
    _work_chunks(work_chunks)
  {
  }

  void work(uint worker_id) {
    if (CONCURRENT) {
      ShenandoahConcurrentWorkerSession worker_session(worker_id);
      ShenandoahSuspendibleThreadSetJoiner stsj(ShenandoahSuspendibleWorkers);
      do_work<ShenandoahConcUpdateRefsClosure>(worker_id);
    } else {
      ShenandoahParallelWorkerSession worker_session(worker_id);
      do_work<ShenandoahSTWUpdateRefsClosure>(worker_id);
    }
  }

private:
  template<class T>
  void do_work(uint worker_id) {
    T cl;
    if (CONCURRENT && (worker_id == 0)) {
      // We ask the first worker to replenish the Mutator free set by moving regions previously reserved to hold the
      // results of evacuation.  These reserves are no longer necessary because evacuation has completed.
      size_t cset_regions = _heap->collection_set()->count();
      // We cannot transfer any more regions than will be reclaimed when the existing collection set is recycled, because
      // we need the reclaimed collection set regions to replenish the collector reserves
      _heap->free_set()->move_collector_sets_to_mutator(cset_regions);
    }
    // If !CONCURRENT, there's no value in expanding Mutator free set

    ShenandoahHeapRegion* r = _regions->next();
    // We update references for global, old, and young collections.
    assert(_heap->active_generation()->is_mark_complete(), "Expected complete marking");
    ShenandoahMarkingContext* const ctx = _heap->marking_context();
    bool is_mixed = _heap->collection_set()->has_old_regions();
    while (r != nullptr) {
      HeapWord* update_watermark = r->get_update_watermark();
      assert (update_watermark >= r->bottom(), "sanity");

      log_debug(gc)("ShenandoahUpdateHeapRefsTask::do_work(%u) looking at region " SIZE_FORMAT, worker_id, r->index());
      bool region_progress = false;
      if (r->is_active() && !r->is_cset()) {
        if (!_heap->mode()->is_generational() || r->is_young()) {
          _heap->marked_object_oop_iterate(r, &cl, update_watermark);
          region_progress = true;
        } else if (r->is_old()) {
          if (_heap->active_generation()->is_global()) {
            // Note that GLOBAL collection is not as effectively balanced as young and mixed cycles.  This is because
            // concurrent GC threads are parceled out entire heap regions of work at a time and there
            // is no "catchup phase" consisting of remembered set scanning, during which parcels of work are smaller
            // and more easily distributed more fairly across threads.

            // TODO: Consider an improvement to load balance GLOBAL GC.
            _heap->marked_object_oop_iterate(r, &cl, update_watermark);
            region_progress = true;
          }
          // Otherwise, this is an old region in a young or mixed cycle.  Process it during a second phase, below.
          // Don't bother to report pacing progress in this case.
        } else {
          // Because updating of references runs concurrently, it is possible that a FREE inactive region transitions
          // to a non-free active region while this loop is executing.  Whenever this happens, the changing of a region's
          // active status may propagate at a different speed than the changing of the region's affiliation.

          // When we reach this control point, it is because a race has allowed a region's is_active() status to be seen
          // by this thread before the region's affiliation() is seen by this thread.

          // It's ok for this race to occur because the newly transformed region does not have any references to be
          // updated.

          assert(r->get_update_watermark() == r->bottom(),
                 "%s Region " SIZE_FORMAT " is_active but not recognized as YOUNG or OLD so must be newly transitioned from FREE",
                 r->affiliation_name(), r->index());
        }
      }
      if (region_progress && ShenandoahPacing) {
        _heap->pacer()->report_updaterefs(pointer_delta(update_watermark, r->bottom()));
      }
      if (_heap->check_cancelled_gc_and_yield(CONCURRENT)) {
        return;
      }
      r = _regions->next();
    }

    if (_heap->mode()->is_generational() && !_heap->active_generation()->is_global()) {
      // Since this is generational and not GLOBAL, we have to process the remembered set.  There's no remembered
      // set processing if not in generational mode or if GLOBAL mode.

      // After this thread has exhausted its traditional update-refs work, it continues with updating refs within remembered set.
      // The remembered set workload is better balanced between threads, so threads that are "behind" can catch up with other
      // threads during this phase, allowing all threads to work more effectively in parallel.
      struct ShenandoahRegionChunk assignment;
      RememberedScanner* scanner = _heap->card_scan();

      while (!_heap->check_cancelled_gc_and_yield(CONCURRENT) && _work_chunks->next(&assignment)) {
        // Keep grabbing next work chunk to process until finished, or asked to yield
        ShenandoahHeapRegion* r = assignment._r;
        if (r->is_active() && !r->is_cset() && r->is_old()) {
          HeapWord* start_of_range = r->bottom() + assignment._chunk_offset;
          HeapWord* end_of_range = r->get_update_watermark();
          if (end_of_range > start_of_range + assignment._chunk_size) {
            end_of_range = start_of_range + assignment._chunk_size;
          }

          // Old region in a young cycle or mixed cycle.
          if (is_mixed) {
            // TODO: For mixed evac, consider building an old-gen remembered set that allows restricted updating
            // within old-gen HeapRegions.  This remembered set can be constructed by old-gen concurrent marking
            // and augmented by card marking.  For example, old-gen concurrent marking can remember for each old-gen
            // card which other old-gen regions it refers to: none, one-other specifically, multiple-other non-specific.
            // Update-references when _mixed_evac processess each old-gen memory range that has a traditional DIRTY
            // card or if the "old-gen remembered set" indicates that this card holds pointers specifically to an
            // old-gen region in the most recent collection set, or if this card holds pointers to other non-specific
            // old-gen heap regions.

            if (r->is_humongous()) {
              if (start_of_range < end_of_range) {
                // Need to examine both dirty and clean cards during mixed evac.
                r->oop_iterate_humongous_slice(&cl, false, start_of_range, assignment._chunk_size, true);
              }
            } else {
              // Since this is mixed evacuation, old regions that are candidates for collection have not been coalesced
              // and filled.  Use mark bits to find objects that need to be updated.
              //
              // Future TODO: establish a second remembered set to identify which old-gen regions point to other old-gen
              // regions which are in the collection set for a particular mixed evacuation.
              if (start_of_range < end_of_range) {
                HeapWord* p = nullptr;
                size_t card_index = scanner->card_index_for_addr(start_of_range);
                // In case last object in my range spans boundary of my chunk, I may need to scan all the way to top()
                ShenandoahObjectToOopBoundedClosure<T> objs(&cl, start_of_range, r->top());

                // Any object that begins in a previous range is part of a different scanning assignment.  Any object that
                // starts after end_of_range is also not my responsibility.  (Either allocated during evacuation, so does
                // not hold pointers to from-space, or is beyond the range of my assigned work chunk.)

                // Find the first object that begins in my range, if there is one.
                p = start_of_range;
                oop obj = cast_to_oop(p);
                HeapWord* tams = ctx->top_at_mark_start(r);
                if (p >= tams) {
                  // We cannot use ctx->is_marked(obj) to test whether an object begins at this address.  Instead,
                  // we need to use the remembered set crossing map to advance p to the first object that starts
                  // within the enclosing card.

                  while (true) {
                    HeapWord* first_object = scanner->first_object_in_card(card_index);
                    if (first_object != nullptr) {
                      p = first_object;
                      break;
                    } else if (scanner->addr_for_card_index(card_index + 1) < end_of_range) {
                      card_index++;
                    } else {
                      // Force the loop that follows to immediately terminate.
                      p = end_of_range;
                      break;
                    }
                  }
                  obj = cast_to_oop(p);
                  // Note: p may be >= end_of_range
                } else if (!ctx->is_marked(obj)) {
                  p = ctx->get_next_marked_addr(p, tams);
                  obj = cast_to_oop(p);
                  // If there are no more marked objects before tams, this returns tams.
                  // Note that tams is either >= end_of_range, or tams is the start of an object that is marked.
                }
                while (p < end_of_range) {
                  // p is known to point to the beginning of marked object obj
                  objs.do_object(obj);
                  HeapWord* prev_p = p;
                  p += obj->size();
                  if (p < tams) {
                    p = ctx->get_next_marked_addr(p, tams);
                    // If there are no more marked objects before tams, this returns tams.  Note that tams is
                    // either >= end_of_range, or tams is the start of an object that is marked.
                  }
                  assert(p != prev_p, "Lack of forward progress");
                  obj = cast_to_oop(p);
                }
              }
            }
          } else {
            // This is a young evac..
            if (start_of_range < end_of_range) {
              size_t cluster_size =
                CardTable::card_size_in_words() * ShenandoahCardCluster<ShenandoahDirectCardMarkRememberedSet>::CardsPerCluster;
              size_t clusters = assignment._chunk_size / cluster_size;
              assert(clusters * cluster_size == assignment._chunk_size, "Chunk assignment must align on cluster boundaries");
              scanner->process_region_slice(r, assignment._chunk_offset, clusters, end_of_range, &cl, true, worker_id);
            }
          }
          if (ShenandoahPacing && (start_of_range < end_of_range)) {
            _heap->pacer()->report_updaterefs(pointer_delta(end_of_range, start_of_range));
          }
        }
      }
    }
  }
};

void ShenandoahHeap::update_heap_references(bool concurrent) {
  assert(!is_full_gc_in_progress(), "Only for concurrent and degenerated GC");
  uint nworkers = workers()->active_workers();
  ShenandoahRegionChunkIterator work_list(nworkers);

  if (concurrent) {
    ShenandoahUpdateHeapRefsTask<true> task(&_update_refs_iterator, &work_list);
    workers()->run_task(&task);
  } else {
    ShenandoahUpdateHeapRefsTask<false> task(&_update_refs_iterator, &work_list);
    workers()->run_task(&task);
  }
  if (ShenandoahEnableCardStats && card_scan()!=nullptr) { // generational check proxy
    card_scan()->log_card_stats(nworkers, CARD_STAT_UPDATE_REFS);
  }
}

class ShenandoahFinalUpdateRefsUpdateRegionStateClosure : public ShenandoahHeapRegionClosure {
private:
  ShenandoahMarkingContext* _ctx;
  ShenandoahHeapLock* const _lock;
  bool _is_generational;

public:
  ShenandoahFinalUpdateRefsUpdateRegionStateClosure(
    ShenandoahMarkingContext* ctx) : _ctx(ctx), _lock(ShenandoahHeap::heap()->lock()),
                                     _is_generational(ShenandoahHeap::heap()->mode()->is_generational()) { }

  void heap_region_do(ShenandoahHeapRegion* r) {

    // Maintenance of region age must follow evacuation in order to account for evacuation allocations within survivor
    // regions.  We consult region age during the subsequent evacuation to determine whether certain objects need to
    // be promoted.
    if (_is_generational && r->is_young() && r->is_active()) {
      HeapWord *tams = _ctx->top_at_mark_start(r);
      HeapWord *top = r->top();

      // Allocations move the watermark when top moves.  However compacting
      // objects will sometimes lower top beneath the watermark, after which,
      // attempts to read the watermark will assert out (watermark should not be
      // higher than top).
      if (top > tams) {
        // There have been allocations in this region since the start of the cycle.
        // Any objects new to this region must not assimilate elevated age.
        r->reset_age();
      } else if (ShenandoahHeap::heap()->is_aging_cycle()) {
        r->increment_age();
      }
    }

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
    ShenandoahFinalUpdateRefsUpdateRegionStateClosure cl (active_generation()->complete_marking_context());
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
  ShenandoahGCPhase phase(concurrent ?
                          ShenandoahPhaseTimings::final_update_refs_rebuild_freeset :
                          ShenandoahPhaseTimings::degen_gc_final_update_refs_rebuild_freeset);
  size_t region_size_bytes = ShenandoahHeapRegion::region_size_bytes();
  ShenandoahHeapLocker locker(lock());
  size_t young_cset_regions, old_cset_regions;
  _free_set->prepare_to_rebuild(young_cset_regions, old_cset_regions);

  if (mode()->is_generational()) {
    assert(verify_generation_usage(true, old_generation()->used_regions(),
                                   old_generation()->used(), old_generation()->get_humongous_waste(),
                                   true, young_generation()->used_regions(),
                                   young_generation()->used(), young_generation()->get_humongous_waste()),
           "Generation accounts are inaccurate");

    // The computation of bytes_of_allocation_runway_before_gc_trigger is quite conservative so consider all of this
    // available for transfer to old. Note that transfer of humongous regions does not impact available.
    size_t allocation_runway = young_heuristics()->bytes_of_allocation_runway_before_gc_trigger(young_cset_regions);
    adjust_generation_sizes_for_next_cycle(allocation_runway, young_cset_regions, old_cset_regions);

    // Total old_available may have been expanded to hold anticipated promotions.  We trigger if the fragmented available
    // memory represents more than 16 regions worth of data.  Note that fragmentation may increase when we promote regular
    // regions in place when many of these regular regions have an abundant amount of available memory within them.  Fragmentation
    // will decrease as promote-by-copy consumes the available memory within these partially consumed regions.
    //
    // We consider old-gen to have excessive fragmentation if more than 12.5% of old-gen is free memory that resides
    // within partially consumed regions of memory.
  }
  // Rebuild free set based on adjusted generation sizes.
  _free_set->rebuild(young_cset_regions, old_cset_regions);

  if (mode()->is_generational()) {
    size_t old_available = old_generation()->available();
    size_t old_unaffiliated_available = old_generation()->free_unaffiliated_regions() * region_size_bytes;
    size_t old_fragmented_available;
    assert(old_available >= old_unaffiliated_available, "unaffiliated available is a subset of total available");
    old_fragmented_available = old_available - old_unaffiliated_available;

    size_t old_capacity = old_generation()->max_capacity();
    size_t heap_capacity = capacity();
    if ((old_capacity > heap_capacity / 8) && (old_fragmented_available > old_capacity / 8)) {
      old_heuristics()->trigger_old_is_fragmented();
    }

    size_t old_used = old_generation()->used() + old_generation()->get_humongous_waste();
    size_t trigger_threshold = old_generation()->usage_trigger_threshold();
    // Detects unsigned arithmetic underflow
    assert(old_used <= capacity(),
           "Old used (" SIZE_FORMAT ", " SIZE_FORMAT") must not be more than heap capacity (" SIZE_FORMAT ")",
           old_generation()->used(), old_generation()->get_humongous_waste(), capacity());

    if (old_used > trigger_threshold) {
      old_heuristics()->trigger_old_has_grown();
    }
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
  if (ShenandoahSuspendibleWorkers) {
    SuspendibleThreadSet::synchronize();
  }
}

void ShenandoahHeap::safepoint_synchronize_end() {
  if (ShenandoahSuspendibleWorkers) {
    SuspendibleThreadSet::desynchronize();
  }
}

void ShenandoahHeap::entry_uncommit(double shrink_before, size_t shrink_until) {
  static const char *msg = "Concurrent uncommit";
  ShenandoahConcurrentPhase gc_phase(msg, ShenandoahPhaseTimings::conc_uncommit, true /* log_heap_usage */);
  EventMark em("%s", msg);

  op_uncommit(shrink_before, shrink_until);
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
  if (mode()->is_generational()) {
    _young_gen_memory_pool = new ShenandoahYoungGenMemoryPool(this);
    _old_gen_memory_pool = new ShenandoahOldGenMemoryPool(this);
    _cycle_memory_manager.add_pool(_young_gen_memory_pool);
    _cycle_memory_manager.add_pool(_old_gen_memory_pool);
    _stw_memory_manager.add_pool(_young_gen_memory_pool);
    _stw_memory_manager.add_pool(_old_gen_memory_pool);
  } else {
    _memory_pool = new ShenandoahMemoryPool(this);
    _cycle_memory_manager.add_pool(_memory_pool);
    _stw_memory_manager.add_pool(_memory_pool);
  }
}

GrowableArray<GCMemoryManager*> ShenandoahHeap::memory_managers() {
  GrowableArray<GCMemoryManager*> memory_managers(2);
  memory_managers.append(&_cycle_memory_manager);
  memory_managers.append(&_stw_memory_manager);
  return memory_managers;
}

GrowableArray<MemoryPool*> ShenandoahHeap::memory_pools() {
  GrowableArray<MemoryPool*> memory_pools(1);
  if (mode()->is_generational()) {
    memory_pools.append(_young_gen_memory_pool);
    memory_pools.append(_old_gen_memory_pool);
  } else {
    memory_pools.append(_memory_pool);
  }
  return memory_pools;
}

MemoryUsage ShenandoahHeap::memory_usage() {
  return MemoryUsage(_initial_size, used(), committed(), max_capacity());
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

void ShenandoahHeap::transfer_old_pointers_from_satb() {
  _old_generation->transfer_pointers_from_satb();
}

template<>
void ShenandoahGenerationRegionClosure<YOUNG>::heap_region_do(ShenandoahHeapRegion* region) {
  // Visit young and free regions
  if (!region->is_old()) {
    _cl->heap_region_do(region);
  }
}

template<>
void ShenandoahGenerationRegionClosure<OLD>::heap_region_do(ShenandoahHeapRegion* region) {
  // Visit old and free regions
  if (!region->is_young()) {
    _cl->heap_region_do(region);
  }
}

template<>
void ShenandoahGenerationRegionClosure<GLOBAL_GEN>::heap_region_do(ShenandoahHeapRegion* region) {
  _cl->heap_region_do(region);
}

template<>
void ShenandoahGenerationRegionClosure<GLOBAL_NON_GEN>::heap_region_do(ShenandoahHeapRegion* region) {
  _cl->heap_region_do(region);
}

bool ShenandoahHeap::verify_generation_usage(bool verify_old, size_t old_regions, size_t old_bytes, size_t old_waste,
                                             bool verify_young, size_t young_regions, size_t young_bytes, size_t young_waste) {
  size_t tally_old_regions = 0;
  size_t tally_old_bytes = 0;
  size_t tally_old_waste = 0;
  size_t tally_young_regions = 0;
  size_t tally_young_bytes = 0;
  size_t tally_young_waste = 0;

  shenandoah_assert_heaplocked_or_safepoint();
  for (size_t i = 0; i < num_regions(); i++) {
    ShenandoahHeapRegion* r = get_region(i);
    if (r->is_old()) {
      tally_old_regions++;
      tally_old_bytes += r->used();
      if (r->is_humongous()) {
        ShenandoahHeapRegion* start = r->humongous_start_region();
        HeapWord* obj_addr = start->bottom();
        oop obj = cast_to_oop(obj_addr);
        size_t word_size = obj->size();
        HeapWord* end_addr = obj_addr + word_size;
        if (end_addr <= r->end()) {
          tally_old_waste += (r->end() - end_addr) * HeapWordSize;
        }
      }
    } else if (r->is_young()) {
      tally_young_regions++;
      tally_young_bytes += r->used();
      if (r->is_humongous()) {
        ShenandoahHeapRegion* start = r->humongous_start_region();
        HeapWord* obj_addr = start->bottom();
        oop obj = cast_to_oop(obj_addr);
        size_t word_size = obj->size();
        HeapWord* end_addr = obj_addr + word_size;
        if (end_addr <= r->end()) {
          tally_young_waste += (r->end() - end_addr) * HeapWordSize;
        }
      }
    }
  }
  if (verify_young &&
      ((young_regions != tally_young_regions) || (young_bytes != tally_young_bytes) || (young_waste != tally_young_waste))) {
    return false;
  } else if (verify_old &&
             ((old_regions != tally_old_regions) || (old_bytes != tally_old_bytes) || (old_waste != tally_old_waste))) {
    return false;
  } else {
    return true;
  }
}

ShenandoahGeneration* ShenandoahHeap::generation_for(ShenandoahAffiliation affiliation) const {
  if (!mode()->is_generational()) {
    return global_generation();
  } else if (affiliation == YOUNG_GENERATION) {
    return young_generation();
  } else if (affiliation == OLD_GENERATION) {
    return old_generation();
  }

  ShouldNotReachHere();
  return nullptr;
}

void ShenandoahHeap::log_heap_status(const char* msg) const {
  if (mode()->is_generational()) {
    young_generation()->log_status(msg);
    old_generation()->log_status(msg);
  } else {
    global_generation()->log_status(msg);
  }
}
