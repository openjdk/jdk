/*
 * Copyright (c) 1999, 2015, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/shared/genCollectedHeap.hpp"
#include "gc/shared/threadLocalAllocBuffer.inline.hpp"
#include "logging/log.hpp"
#include "memory/resourceArea.hpp"
#include "memory/universe.inline.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/thread.inline.hpp"
#include "utilities/copy.hpp"

// Thread-Local Edens support

// static member initialization
size_t           ThreadLocalAllocBuffer::_max_size       = 0;
int              ThreadLocalAllocBuffer::_reserve_for_allocation_prefetch = 0;
unsigned         ThreadLocalAllocBuffer::_target_refills = 0;
GlobalTLABStats* ThreadLocalAllocBuffer::_global_stats   = NULL;

void ThreadLocalAllocBuffer::clear_before_allocation() {
  _slow_refill_waste += (unsigned)remaining();
  make_parsable(true);   // also retire the TLAB
}

void ThreadLocalAllocBuffer::accumulate_statistics_before_gc() {
  global_stats()->initialize();

  for (JavaThread *thread = Threads::first(); thread != NULL; thread = thread->next()) {
    thread->tlab().accumulate_statistics();
    thread->tlab().initialize_statistics();
  }

  // Publish new stats if some allocation occurred.
  if (global_stats()->allocation() != 0) {
    global_stats()->publish();
    global_stats()->print();
  }
}

void ThreadLocalAllocBuffer::accumulate_statistics() {
  Thread* thread = myThread();
  size_t capacity = Universe::heap()->tlab_capacity(thread);
  size_t used     = Universe::heap()->tlab_used(thread);

  _gc_waste += (unsigned)remaining();
  size_t total_allocated = thread->allocated_bytes();
  size_t allocated_since_last_gc = total_allocated - _allocated_before_last_gc;
  _allocated_before_last_gc = total_allocated;

  print_stats("gc");

  if (_number_of_refills > 0) {
    // Update allocation history if a reasonable amount of eden was allocated.
    bool update_allocation_history = used > 0.5 * capacity;

    if (update_allocation_history) {
      // Average the fraction of eden allocated in a tlab by this
      // thread for use in the next resize operation.
      // _gc_waste is not subtracted because it's included in
      // "used".
      // The result can be larger than 1.0 due to direct to old allocations.
      // These allocations should ideally not be counted but since it is not possible
      // to filter them out here we just cap the fraction to be at most 1.0.
      double alloc_frac = MIN2(1.0, (double) allocated_since_last_gc / used);
      _allocation_fraction.sample(alloc_frac);
    }
    global_stats()->update_allocating_threads();
    global_stats()->update_number_of_refills(_number_of_refills);
    global_stats()->update_allocation(_number_of_refills * desired_size());
    global_stats()->update_gc_waste(_gc_waste);
    global_stats()->update_slow_refill_waste(_slow_refill_waste);
    global_stats()->update_fast_refill_waste(_fast_refill_waste);

  } else {
    assert(_number_of_refills == 0 && _fast_refill_waste == 0 &&
           _slow_refill_waste == 0 && _gc_waste          == 0,
           "tlab stats == 0");
  }
  global_stats()->update_slow_allocations(_slow_allocations);
}

// Fills the current tlab with a dummy filler array to create
// an illusion of a contiguous Eden and optionally retires the tlab.
// Waste accounting should be done in caller as appropriate; see,
// for example, clear_before_allocation().
void ThreadLocalAllocBuffer::make_parsable(bool retire, bool zap) {
  if (end() != NULL) {
    invariants();

    if (retire) {
      myThread()->incr_allocated_bytes(used_bytes());
    }

    CollectedHeap::fill_with_object(top(), hard_end(), retire && zap);

    if (retire || ZeroTLAB) {  // "Reset" the TLAB
      set_start(NULL);
      set_top(NULL);
      set_pf_top(NULL);
      set_end(NULL);
    }
  }
  assert(!(retire || ZeroTLAB)  ||
         (start() == NULL && end() == NULL && top() == NULL),
         "TLAB must be reset");
}

void ThreadLocalAllocBuffer::resize_all_tlabs() {
  if (ResizeTLAB) {
    for (JavaThread *thread = Threads::first(); thread != NULL; thread = thread->next()) {
      thread->tlab().resize();
    }
  }
}

void ThreadLocalAllocBuffer::resize() {
  // Compute the next tlab size using expected allocation amount
  assert(ResizeTLAB, "Should not call this otherwise");
  size_t alloc = (size_t)(_allocation_fraction.average() *
                          (Universe::heap()->tlab_capacity(myThread()) / HeapWordSize));
  size_t new_size = alloc / _target_refills;

  new_size = MIN2(MAX2(new_size, min_size()), max_size());

  size_t aligned_new_size = align_object_size(new_size);

  log_trace(gc, tlab)("TLAB new size: thread: " INTPTR_FORMAT " [id: %2d]"
                      " refills %d  alloc: %8.6f desired_size: " SIZE_FORMAT " -> " SIZE_FORMAT,
                      p2i(myThread()), myThread()->osthread()->thread_id(),
                      _target_refills, _allocation_fraction.average(), desired_size(), aligned_new_size);

  set_desired_size(aligned_new_size);
  set_refill_waste_limit(initial_refill_waste_limit());
}

void ThreadLocalAllocBuffer::initialize_statistics() {
    _number_of_refills = 0;
    _fast_refill_waste = 0;
    _slow_refill_waste = 0;
    _gc_waste          = 0;
    _slow_allocations  = 0;
}

void ThreadLocalAllocBuffer::fill(HeapWord* start,
                                  HeapWord* top,
                                  size_t    new_size) {
  _number_of_refills++;
  print_stats("fill");
  assert(top <= start + new_size - alignment_reserve(), "size too small");
  initialize(start, top, start + new_size - alignment_reserve());

  // Reset amount of internal fragmentation
  set_refill_waste_limit(initial_refill_waste_limit());
}

void ThreadLocalAllocBuffer::initialize(HeapWord* start,
                                        HeapWord* top,
                                        HeapWord* end) {
  set_start(start);
  set_top(top);
  set_pf_top(top);
  set_end(end);
  invariants();
}

void ThreadLocalAllocBuffer::initialize() {
  initialize(NULL,                    // start
             NULL,                    // top
             NULL);                   // end

  set_desired_size(initial_desired_size());

  // Following check is needed because at startup the main (primordial)
  // thread is initialized before the heap is.  The initialization for
  // this thread is redone in startup_initialization below.
  if (Universe::heap() != NULL) {
    size_t capacity   = Universe::heap()->tlab_capacity(myThread()) / HeapWordSize;
    double alloc_frac = desired_size() * target_refills() / (double) capacity;
    _allocation_fraction.sample(alloc_frac);
  }

  set_refill_waste_limit(initial_refill_waste_limit());

  initialize_statistics();
}

void ThreadLocalAllocBuffer::startup_initialization() {

  // Assuming each thread's active tlab is, on average,
  // 1/2 full at a GC
  _target_refills = 100 / (2 * TLABWasteTargetPercent);
  _target_refills = MAX2(_target_refills, (unsigned)1U);

  _global_stats = new GlobalTLABStats();

#ifdef COMPILER2
  // If the C2 compiler is present, extra space is needed at the end of
  // TLABs, otherwise prefetching instructions generated by the C2
  // compiler will fault (due to accessing memory outside of heap).
  // The amount of space is the max of the number of lines to
  // prefetch for array and for instance allocations. (Extra space must be
  // reserved to accommodate both types of allocations.)
  //
  // Only SPARC-specific BIS instructions are known to fault. (Those
  // instructions are generated if AllocatePrefetchStyle==3 and
  // AllocatePrefetchInstr==1). To be on the safe side, however,
  // extra space is reserved for all combinations of
  // AllocatePrefetchStyle and AllocatePrefetchInstr.
  //
  // If the C2 compiler is not present, no space is reserved.

  // +1 for rounding up to next cache line, +1 to be safe
  if (is_server_compilation_mode_vm()) {
    int lines =  MAX2(AllocatePrefetchLines, AllocateInstancePrefetchLines) + 2;
    _reserve_for_allocation_prefetch = (AllocatePrefetchDistance + AllocatePrefetchStepSize * lines) /
                                       (int)HeapWordSize;
  }
#endif

  // During jvm startup, the main (primordial) thread is initialized
  // before the heap is initialized.  So reinitialize it now.
  guarantee(Thread::current()->is_Java_thread(), "tlab initialization thread not Java thread");
  Thread::current()->tlab().initialize();

  log_develop_trace(gc, tlab)("TLAB min: " SIZE_FORMAT " initial: " SIZE_FORMAT " max: " SIZE_FORMAT,
                               min_size(), Thread::current()->tlab().initial_desired_size(), max_size());
}

size_t ThreadLocalAllocBuffer::initial_desired_size() {
  size_t init_sz = 0;

  if (TLABSize > 0) {
    init_sz = TLABSize / HeapWordSize;
  } else if (global_stats() != NULL) {
    // Initial size is a function of the average number of allocating threads.
    unsigned nof_threads = global_stats()->allocating_threads_avg();

    init_sz  = (Universe::heap()->tlab_capacity(myThread()) / HeapWordSize) /
                      (nof_threads * target_refills());
    init_sz = align_object_size(init_sz);
  }
  init_sz = MIN2(MAX2(init_sz, min_size()), max_size());
  return init_sz;
}

void ThreadLocalAllocBuffer::print_stats(const char* tag) {
  Log(gc, tlab) log;
  if (!log.is_trace()) {
    return;
  }

  Thread* thrd = myThread();
  size_t waste = _gc_waste + _slow_refill_waste + _fast_refill_waste;
  size_t alloc = _number_of_refills * _desired_size;
  double waste_percent = alloc == 0 ? 0.0 :
                      100.0 * waste / alloc;
  size_t tlab_used  = Universe::heap()->tlab_used(thrd);
  log.trace("TLAB: %s thread: " INTPTR_FORMAT " [id: %2d]"
            " desired_size: " SIZE_FORMAT "KB"
            " slow allocs: %d  refill waste: " SIZE_FORMAT "B"
            " alloc:%8.5f %8.0fKB refills: %d waste %4.1f%% gc: %dB"
            " slow: %dB fast: %dB",
            tag, p2i(thrd), thrd->osthread()->thread_id(),
            _desired_size / (K / HeapWordSize),
            _slow_allocations, _refill_waste_limit * HeapWordSize,
            _allocation_fraction.average(),
            _allocation_fraction.average() * tlab_used / K,
            _number_of_refills, waste_percent,
            _gc_waste * HeapWordSize,
            _slow_refill_waste * HeapWordSize,
            _fast_refill_waste * HeapWordSize);
}

void ThreadLocalAllocBuffer::verify() {
  HeapWord* p = start();
  HeapWord* t = top();
  HeapWord* prev_p = NULL;
  while (p < t) {
    oop(p)->verify();
    prev_p = p;
    p += oop(p)->size();
  }
  guarantee(p == top(), "end of last object must match end of space");
}

Thread* ThreadLocalAllocBuffer::myThread() {
  return (Thread*)(((char *)this) +
                   in_bytes(start_offset()) -
                   in_bytes(Thread::tlab_start_offset()));
}


GlobalTLABStats::GlobalTLABStats() :
  _allocating_threads_avg(TLABAllocationWeight) {

  initialize();

  _allocating_threads_avg.sample(1); // One allocating thread at startup

  if (UsePerfData) {

    EXCEPTION_MARK;
    ResourceMark rm;

    char* cname = PerfDataManager::counter_name("tlab", "allocThreads");
    _perf_allocating_threads =
      PerfDataManager::create_variable(SUN_GC, cname, PerfData::U_None, CHECK);

    cname = PerfDataManager::counter_name("tlab", "fills");
    _perf_total_refills =
      PerfDataManager::create_variable(SUN_GC, cname, PerfData::U_None, CHECK);

    cname = PerfDataManager::counter_name("tlab", "maxFills");
    _perf_max_refills =
      PerfDataManager::create_variable(SUN_GC, cname, PerfData::U_None, CHECK);

    cname = PerfDataManager::counter_name("tlab", "alloc");
    _perf_allocation =
      PerfDataManager::create_variable(SUN_GC, cname, PerfData::U_Bytes, CHECK);

    cname = PerfDataManager::counter_name("tlab", "gcWaste");
    _perf_gc_waste =
      PerfDataManager::create_variable(SUN_GC, cname, PerfData::U_Bytes, CHECK);

    cname = PerfDataManager::counter_name("tlab", "maxGcWaste");
    _perf_max_gc_waste =
      PerfDataManager::create_variable(SUN_GC, cname, PerfData::U_Bytes, CHECK);

    cname = PerfDataManager::counter_name("tlab", "slowWaste");
    _perf_slow_refill_waste =
      PerfDataManager::create_variable(SUN_GC, cname, PerfData::U_Bytes, CHECK);

    cname = PerfDataManager::counter_name("tlab", "maxSlowWaste");
    _perf_max_slow_refill_waste =
      PerfDataManager::create_variable(SUN_GC, cname, PerfData::U_Bytes, CHECK);

    cname = PerfDataManager::counter_name("tlab", "fastWaste");
    _perf_fast_refill_waste =
      PerfDataManager::create_variable(SUN_GC, cname, PerfData::U_Bytes, CHECK);

    cname = PerfDataManager::counter_name("tlab", "maxFastWaste");
    _perf_max_fast_refill_waste =
      PerfDataManager::create_variable(SUN_GC, cname, PerfData::U_Bytes, CHECK);

    cname = PerfDataManager::counter_name("tlab", "slowAlloc");
    _perf_slow_allocations =
      PerfDataManager::create_variable(SUN_GC, cname, PerfData::U_None, CHECK);

    cname = PerfDataManager::counter_name("tlab", "maxSlowAlloc");
    _perf_max_slow_allocations =
      PerfDataManager::create_variable(SUN_GC, cname, PerfData::U_None, CHECK);
  }
}

void GlobalTLABStats::initialize() {
  // Clear counters summarizing info from all threads
  _allocating_threads      = 0;
  _total_refills           = 0;
  _max_refills             = 0;
  _total_allocation        = 0;
  _total_gc_waste          = 0;
  _max_gc_waste            = 0;
  _total_slow_refill_waste = 0;
  _max_slow_refill_waste   = 0;
  _total_fast_refill_waste = 0;
  _max_fast_refill_waste   = 0;
  _total_slow_allocations  = 0;
  _max_slow_allocations    = 0;
}

void GlobalTLABStats::publish() {
  _allocating_threads_avg.sample(_allocating_threads);
  if (UsePerfData) {
    _perf_allocating_threads   ->set_value(_allocating_threads);
    _perf_total_refills        ->set_value(_total_refills);
    _perf_max_refills          ->set_value(_max_refills);
    _perf_allocation           ->set_value(_total_allocation);
    _perf_gc_waste             ->set_value(_total_gc_waste);
    _perf_max_gc_waste         ->set_value(_max_gc_waste);
    _perf_slow_refill_waste    ->set_value(_total_slow_refill_waste);
    _perf_max_slow_refill_waste->set_value(_max_slow_refill_waste);
    _perf_fast_refill_waste    ->set_value(_total_fast_refill_waste);
    _perf_max_fast_refill_waste->set_value(_max_fast_refill_waste);
    _perf_slow_allocations     ->set_value(_total_slow_allocations);
    _perf_max_slow_allocations ->set_value(_max_slow_allocations);
  }
}

void GlobalTLABStats::print() {
  Log(gc, tlab) log;
  if (!log.is_debug()) {
    return;
  }

  size_t waste = _total_gc_waste + _total_slow_refill_waste + _total_fast_refill_waste;
  double waste_percent = _total_allocation == 0 ? 0.0 :
                         100.0 * waste / _total_allocation;
  log.debug("TLAB totals: thrds: %d  refills: %d max: %d"
            " slow allocs: %d max %d waste: %4.1f%%"
            " gc: " SIZE_FORMAT "B max: " SIZE_FORMAT "B"
            " slow: " SIZE_FORMAT "B max: " SIZE_FORMAT "B"
            " fast: " SIZE_FORMAT "B max: " SIZE_FORMAT "B",
            _allocating_threads,
            _total_refills, _max_refills,
            _total_slow_allocations, _max_slow_allocations,
            waste_percent,
            _total_gc_waste * HeapWordSize,
            _max_gc_waste * HeapWordSize,
            _total_slow_refill_waste * HeapWordSize,
            _max_slow_refill_waste * HeapWordSize,
            _total_fast_refill_waste * HeapWordSize,
            _max_fast_refill_waste * HeapWordSize);
}
