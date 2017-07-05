/*
 * Copyright (c) 1999, 2010, Oracle and/or its affiliates. All rights reserved.
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
#include "memory/genCollectedHeap.hpp"
#include "memory/resourceArea.hpp"
#include "memory/threadLocalAllocBuffer.inline.hpp"
#include "memory/universe.inline.hpp"
#include "oops/oop.inline.hpp"
#include "utilities/copy.hpp"
#ifdef TARGET_OS_FAMILY_linux
# include "thread_linux.inline.hpp"
#endif
#ifdef TARGET_OS_FAMILY_solaris
# include "thread_solaris.inline.hpp"
#endif
#ifdef TARGET_OS_FAMILY_windows
# include "thread_windows.inline.hpp"
#endif

// Thread-Local Edens support

// static member initialization
unsigned         ThreadLocalAllocBuffer::_target_refills = 0;
GlobalTLABStats* ThreadLocalAllocBuffer::_global_stats   = NULL;

void ThreadLocalAllocBuffer::clear_before_allocation() {
  _slow_refill_waste += (unsigned)remaining();
  make_parsable(true);   // also retire the TLAB
}

void ThreadLocalAllocBuffer::accumulate_statistics_before_gc() {
  global_stats()->initialize();

  for(JavaThread *thread = Threads::first(); thread; thread = thread->next()) {
    thread->tlab().accumulate_statistics();
    thread->tlab().initialize_statistics();
  }

  // Publish new stats if some allocation occurred.
  if (global_stats()->allocation() != 0) {
    global_stats()->publish();
    if (PrintTLAB) {
      global_stats()->print();
    }
  }
}

void ThreadLocalAllocBuffer::accumulate_statistics() {
  size_t capacity = Universe::heap()->tlab_capacity(myThread()) / HeapWordSize;
  size_t unused   = Universe::heap()->unsafe_max_tlab_alloc(myThread()) / HeapWordSize;
  size_t used     = capacity - unused;

  // Update allocation history if a reasonable amount of eden was allocated.
  bool update_allocation_history = used > 0.5 * capacity;

  _gc_waste += (unsigned)remaining();

  if (PrintTLAB && (_number_of_refills > 0 || Verbose)) {
    print_stats("gc");
  }

  if (_number_of_refills > 0) {

    if (update_allocation_history) {
      // Average the fraction of eden allocated in a tlab by this
      // thread for use in the next resize operation.
      // _gc_waste is not subtracted because it's included in
      // "used".
      size_t allocation = _number_of_refills * desired_size();
      double alloc_frac = allocation / (double) used;
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
void ThreadLocalAllocBuffer::make_parsable(bool retire) {
  if (end() != NULL) {
    invariants();
    CollectedHeap::fill_with_object(top(), hard_end(), retire);

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
  for(JavaThread *thread = Threads::first(); thread; thread = thread->next()) {
    thread->tlab().resize();
  }
}

void ThreadLocalAllocBuffer::resize() {

  if (ResizeTLAB) {
    // Compute the next tlab size using expected allocation amount
    size_t alloc = (size_t)(_allocation_fraction.average() *
                            (Universe::heap()->tlab_capacity(myThread()) / HeapWordSize));
    size_t new_size = alloc / _target_refills;

    new_size = MIN2(MAX2(new_size, min_size()), max_size());

    size_t aligned_new_size = align_object_size(new_size);

    if (PrintTLAB && Verbose) {
      gclog_or_tty->print("TLAB new size: thread: " INTPTR_FORMAT " [id: %2d]"
                          " refills %d  alloc: %8.6f desired_size: " SIZE_FORMAT " -> " SIZE_FORMAT "\n",
                          myThread(), myThread()->osthread()->thread_id(),
                          _target_refills, _allocation_fraction.average(), desired_size(), aligned_new_size);
    }
    set_desired_size(aligned_new_size);

    set_refill_waste_limit(initial_refill_waste_limit());
  }
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
  if (PrintTLAB && Verbose) {
    print_stats("fill");
  }
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

  // During jvm startup, the main (primordial) thread is initialized
  // before the heap is initialized.  So reinitialize it now.
  guarantee(Thread::current()->is_Java_thread(), "tlab initialization thread not Java thread");
  Thread::current()->tlab().initialize();

  if (PrintTLAB && Verbose) {
    gclog_or_tty->print("TLAB min: " SIZE_FORMAT " initial: " SIZE_FORMAT " max: " SIZE_FORMAT "\n",
                        min_size(), Thread::current()->tlab().initial_desired_size(), max_size());
  }
}

size_t ThreadLocalAllocBuffer::initial_desired_size() {
  size_t init_sz;

  if (TLABSize > 0) {
    init_sz = MIN2(TLABSize / HeapWordSize, max_size());
  } else if (global_stats() == NULL) {
    // Startup issue - main thread initialized before heap initialized.
    init_sz = min_size();
  } else {
    // Initial size is a function of the average number of allocating threads.
    unsigned nof_threads = global_stats()->allocating_threads_avg();

    init_sz  = (Universe::heap()->tlab_capacity(myThread()) / HeapWordSize) /
                      (nof_threads * target_refills());
    init_sz = align_object_size(init_sz);
    init_sz = MIN2(MAX2(init_sz, min_size()), max_size());
  }
  return init_sz;
}

const size_t ThreadLocalAllocBuffer::max_size() {

  // TLABs can't be bigger than we can fill with a int[Integer.MAX_VALUE].
  // This restriction could be removed by enabling filling with multiple arrays.
  // If we compute that the reasonable way as
  //    header_size + ((sizeof(jint) * max_jint) / HeapWordSize)
  // we'll overflow on the multiply, so we do the divide first.
  // We actually lose a little by dividing first,
  // but that just makes the TLAB  somewhat smaller than the biggest array,
  // which is fine, since we'll be able to fill that.

  size_t unaligned_max_size = typeArrayOopDesc::header_size(T_INT) +
                              sizeof(jint) *
                              ((juint) max_jint / (size_t) HeapWordSize);
  return align_size_down(unaligned_max_size, MinObjAlignment);
}

void ThreadLocalAllocBuffer::print_stats(const char* tag) {
  Thread* thrd = myThread();
  size_t waste = _gc_waste + _slow_refill_waste + _fast_refill_waste;
  size_t alloc = _number_of_refills * _desired_size;
  double waste_percent = alloc == 0 ? 0.0 :
                      100.0 * waste / alloc;
  size_t tlab_used  = Universe::heap()->tlab_capacity(thrd) -
                      Universe::heap()->unsafe_max_tlab_alloc(thrd);
  gclog_or_tty->print("TLAB: %s thread: " INTPTR_FORMAT " [id: %2d]"
                      " desired_size: " SIZE_FORMAT "KB"
                      " slow allocs: %d  refill waste: " SIZE_FORMAT "B"
                      " alloc:%8.5f %8.0fKB refills: %d waste %4.1f%% gc: %dB"
                      " slow: %dB fast: %dB\n",
                      tag, thrd, thrd->osthread()->thread_id(),
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
  size_t waste = _total_gc_waste + _total_slow_refill_waste + _total_fast_refill_waste;
  double waste_percent = _total_allocation == 0 ? 0.0 :
                         100.0 * waste / _total_allocation;
  gclog_or_tty->print("TLAB totals: thrds: %d  refills: %d max: %d"
                      " slow allocs: %d max %d waste: %4.1f%%"
                      " gc: " SIZE_FORMAT "B max: " SIZE_FORMAT "B"
                      " slow: " SIZE_FORMAT "B max: " SIZE_FORMAT "B"
                      " fast: " SIZE_FORMAT "B max: " SIZE_FORMAT "B\n",
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
