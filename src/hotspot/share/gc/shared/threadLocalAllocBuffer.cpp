/*
 * Copyright (c) 1999, 2026, Oracle and/or its affiliates. All rights reserved.
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

#include "compiler/compilerDefinitions.inline.hpp"
#include "gc/shared/collectedHeap.hpp"
#include "gc/shared/threadLocalAllocBuffer.inline.hpp"
#include "gc/shared/tlab_globals.hpp"
#include "logging/log.hpp"
#include "memory/resourceArea.hpp"
#include "memory/universe.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/atomicAccess.hpp"
#include "runtime/javaThread.hpp"
#include "runtime/perfData.hpp"
#include "runtime/threadSMR.hpp"
#include "utilities/copy.hpp"
#include "utilities/integerCast.hpp"

size_t       ThreadLocalAllocBuffer::_max_size = 0;
unsigned int ThreadLocalAllocBuffer::_target_num_refills = 0;

ThreadLocalAllocBuffer::ThreadLocalAllocBuffer() :
  _start(nullptr),
  _top(nullptr),
  _pf_top(nullptr),
  _end(nullptr),
  _allocation_end(nullptr),
  _desired_size(0),
  _refill_waste_limit(0),
  _allocated_before_last_gc(0),
  _num_refills(0),
  _refill_waste(0),
  _gc_waste(0),
  _num_slow_allocations(0),
  _allocated_size(0),
  _allocation_fraction(TLABAllocationWeight) {

  // do nothing. TLABs must be inited by initialize() calls
}

size_t ThreadLocalAllocBuffer::initial_refill_waste_limit() {
  assert(TLABRefillWasteFraction != 0, "inv");
  return desired_size() / TLABRefillWasteFraction;
}

size_t ThreadLocalAllocBuffer::min_size()                       { return align_object_size(MinTLABSize / HeapWordSize) + alignment_reserve(); }
size_t ThreadLocalAllocBuffer::refill_waste_limit_increment()   { return TLABWasteIncrement; }

size_t ThreadLocalAllocBuffer::remaining() {
  if (end() == nullptr) {
    return 0;
  }

  return pointer_delta(hard_end(), top());
}

void ThreadLocalAllocBuffer::accumulate_and_reset_statistics(ThreadLocalAllocStats* stats) {
  _gc_waste += (unsigned)remaining();
  const uint64_t allocated_bytes = thread()->allocated_bytes();

  const size_t allocated_since_last_gc = integer_cast_permit_tautology<size_t>(allocated_bytes - _allocated_before_last_gc);
  _allocated_before_last_gc = allocated_bytes;

  if (allocated_since_last_gc > 0) {
    const size_t tlab_capacity = Universe::heap()->tlab_capacity();
    const size_t tlab_used = Universe::heap()->tlab_used();
    if (tlab_used > 0.5 * tlab_capacity) {
      // To avoid divide-by-zero
      const size_t effective_tlab_capacity = MAX2(tlab_capacity, size_t(1));
      const float alloc_frac = (float)allocated_since_last_gc / effective_tlab_capacity;
      _allocation_fraction.sample(MIN2(alloc_frac, 1.0f));
    }
    stats->update_current_thread_stats(_num_refills,
                                       allocated_since_last_gc,
                                       _allocated_size,
                                       _gc_waste,
                                       _refill_waste,
                                       _num_slow_allocations);
  } else {
    assert(_num_refills == 0 && _refill_waste == 0
           && _gc_waste == 0 && _num_slow_allocations == 0,
           "tlab stats == 0");
  }

  {
    Log(gc, tlab) log;
    if (log.is_trace()) {
      Thread* thrd = thread();
      size_t waste = _gc_waste + _refill_waste;
      double waste_percent = percent_of(waste, _allocated_size);
      log.trace("TLAB GC: thread: " PTR_FORMAT " [id: %2d]"
                " desired: %zuK"
                " allocated: %zuK"
                " slow allocs: %d  refill waste: %zuB"
                " refills: %d waste %4.1f%% gc: %dB"
                " slow: %dB",
                p2i(thrd), thrd->osthread()->thread_id(),
                _desired_size*HeapWordSize/K,
                allocated_since_last_gc/K,
                _num_slow_allocations, _refill_waste_limit * HeapWordSize,
                _num_refills, waste_percent,
                _gc_waste * HeapWordSize,
                _refill_waste * HeapWordSize);
    }
  }

  reset_statistics();
}

void ThreadLocalAllocBuffer::insert_filler() {
  assert(end() != nullptr, "Must not be retired");
  if (top() < hard_end()) {
    Universe::heap()->fill_with_dummy_object(top(), hard_end(), true);
  }
}

void ThreadLocalAllocBuffer::make_parsable() {
  if (end() != nullptr) {
    invariants();
    insert_filler();
  }
}

void ThreadLocalAllocBuffer::retire(ThreadLocalAllocStats* stats) {
  if (stats != nullptr) {
    accumulate_and_reset_statistics(stats);
  }

  if (end() != nullptr) {
    invariants();
    insert_filler();
    initialize(nullptr, nullptr, nullptr);
  }
}

void ThreadLocalAllocBuffer::record_refill_waste() {
  _refill_waste += (unsigned int)remaining();
}

void ThreadLocalAllocBuffer::resize() {
  assert(ResizeTLAB, "Should not call this otherwise");
  size_t capacity_in_words = Universe::heap()->tlab_capacity() / HeapWordSize;
  float alloc_fraction = _allocation_fraction.average();
  if (alloc_fraction == 0.0) {
    // No samples, use global alloc fraction as an approximation.
    const float total_frac = ThreadLocalAllocStats::total_requested_size_fraction_avg();
    const uint num_threads = ThreadLocalAllocStats::num_allocating_threads_avg();
    alloc_fraction = total_frac / num_threads;
  }
  size_t alloc = (size_t)(alloc_fraction * capacity_in_words);
  size_t new_size = alloc / _target_num_refills;

  new_size = clamp(new_size, min_size(), max_size());

  size_t aligned_new_size = align_object_size(new_size);

  log_trace(gc, tlab)("TLAB resize: thread: " PTR_FORMAT " [id: %2d]"
                      " alloc-fraction: %.3f desired_size: %zuK -> %zuK",
                      p2i(thread()), thread()->osthread()->thread_id(),
                      alloc_fraction,
                      desired_size() * HeapWordSize/K, aligned_new_size * HeapWordSize/K);

  set_desired_size(aligned_new_size);
  set_refill_waste_limit(initial_refill_waste_limit());
}

void ThreadLocalAllocBuffer::reset_statistics() {
  _num_refills          = 0;
  _refill_waste         = 0;
  _gc_waste             = 0;
  _num_slow_allocations = 0;
  _allocated_size       = 0;
}

void ThreadLocalAllocBuffer::fill(HeapWord* start,
                                  HeapWord* top,
                                  size_t    new_size) {
  _num_refills++;
  _allocated_size += new_size;

  assert(top <= start + new_size - alignment_reserve(), "size too small");

  initialize(start, top, start + new_size - alignment_reserve());
  {
    Log(gc, tlab) log;
    if (log.is_trace()) {
      Thread* thrd = thread();
      log.trace("TLAB fill: thread: " PTR_FORMAT " [id: %2d]"
                " capacity: %zuK"
                " slow allocs: %d "
                " refills: %d",
                p2i(thrd), thrd->osthread()->thread_id(),
                pointer_delta(_end, _start, sizeof(char)) / K,
                _num_slow_allocations,
                _num_refills);
    }
  }
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
  set_allocation_end(end);
  invariants();
}

void ThreadLocalAllocBuffer::initialize() {
  initialize(nullptr,                    // start
             nullptr,                    // top
             nullptr);                   // end

  set_desired_size(initial_desired_size());

  set_refill_waste_limit(initial_refill_waste_limit());

  reset_statistics();
}

void ThreadLocalAllocBuffer::startup_initialization() {
  ThreadLocalAllocStats::initialize();

  // Assuming each thread's active tlab is, on average,
  // 1/2 full at a GC
  _target_num_refills = 100 / (2 * TLABWasteTargetPercent);
  // We need to set the initial target number of refills to 2 to avoid a GC which causes VM
  // abort during VM initialization.
  _target_num_refills = MAX2(_target_num_refills, 2U);

  // During jvm startup, the main thread is initialized
  // before the heap is initialized.  So reinitialize it now.
  guarantee(Thread::current()->is_Java_thread(), "tlab initialization thread not Java thread");
  Thread::current()->tlab().initialize();

  log_develop_trace(gc, tlab)("TLAB min: %zu initial: %zu max: %zu",
                               min_size(), Thread::current()->tlab().initial_desired_size(), max_size());
}

size_t ThreadLocalAllocBuffer::initial_desired_size() {
  size_t init_sz = 0;

  if (TLABSize > 0) {
    init_sz = TLABSize / HeapWordSize;
  } else {
    const size_t predicted_total_requested_size = (size_t)(ThreadLocalAllocStats::total_requested_size_fraction_avg() * Universe::heap()->tlab_capacity());
    const uint num_threads = ThreadLocalAllocStats::num_allocating_threads_avg();
    const size_t per_thread_requested_size = predicted_total_requested_size / num_threads;
    const size_t tlab_size = per_thread_requested_size / _target_num_refills;
    init_sz = tlab_size / HeapWordSize;
    init_sz = align_object_size(init_sz);
  }
  // We can't use clamp() between min_size() and max_size() here because some
  // options based on them may still be inconsistent and so it may assert;
  // inconsistencies between those will be caught by following AfterMemoryInit
  // constraint checking.
  init_sz = MIN2(MAX2(init_sz, min_size()), max_size());
  return init_sz;
}

Thread* ThreadLocalAllocBuffer::thread() const {
  return (Thread*)(((char*)this) + in_bytes(start_offset()) - in_bytes(Thread::tlab_start_offset()));
}

void ThreadLocalAllocBuffer::set_back_allocation_end() {
  _end = _allocation_end;
}

void ThreadLocalAllocBuffer::set_sampling_point(HeapWord* sampling_point) {
  precond(sampling_point >= _top);
  precond(sampling_point <= _allocation_end);

  // This will trigger a slow-path, which in turn might take a sample.
  _end = sampling_point;
}

HeapWord* ThreadLocalAllocBuffer::hard_end() {
  return _allocation_end + alignment_reserve();
}

PerfVariable* ThreadLocalAllocStats::_perf_num_allocating_threads;
PerfVariable* ThreadLocalAllocStats::_perf_total_num_refills;
PerfVariable* ThreadLocalAllocStats::_perf_max_num_refills;
PerfVariable* ThreadLocalAllocStats::_perf_total_allocated_size;
PerfVariable* ThreadLocalAllocStats::_perf_total_gc_waste;
PerfVariable* ThreadLocalAllocStats::_perf_max_gc_waste;
PerfVariable* ThreadLocalAllocStats::_perf_total_refill_waste;
PerfVariable* ThreadLocalAllocStats::_perf_max_refill_waste;
PerfVariable* ThreadLocalAllocStats::_perf_total_num_slow_allocations;
PerfVariable* ThreadLocalAllocStats::_perf_max_num_slow_allocations;
AdaptiveWeightedAverage ThreadLocalAllocStats::_num_allocating_threads_avg(0);
AdaptiveWeightedAverage ThreadLocalAllocStats::_total_requested_size_fraction(0);

static PerfVariable* create_perf_variable(const char* name, PerfData::Units unit, TRAPS) {
  ResourceMark rm;
  return PerfDataManager::create_variable(SUN_GC, PerfDataManager::counter_name("tlab", name), unit, THREAD);
}

void ThreadLocalAllocStats::initialize() {
  _num_allocating_threads_avg = AdaptiveWeightedAverage(TLABAllocationWeight);
  _num_allocating_threads_avg.sample(1); // One allocating thread at startup

  _total_requested_size_fraction = AdaptiveWeightedAverage(TLABAllocationWeight);
  _total_requested_size_fraction.sample(0.10f); // 10%

  if (UsePerfData) {
    EXCEPTION_MARK;
    _perf_num_allocating_threads     = create_perf_variable("allocThreads",   PerfData::U_None,  CHECK);
    _perf_total_num_refills          = create_perf_variable("fills",          PerfData::U_None,  CHECK);
    _perf_max_num_refills            = create_perf_variable("maxFills",       PerfData::U_None,  CHECK);
    _perf_total_allocated_size       = create_perf_variable("alloc",          PerfData::U_Bytes, CHECK);
    _perf_total_gc_waste             = create_perf_variable("gcWaste",        PerfData::U_Bytes, CHECK);
    _perf_max_gc_waste               = create_perf_variable("maxGcWaste",     PerfData::U_Bytes, CHECK);
    _perf_total_refill_waste         = create_perf_variable("refillWaste",    PerfData::U_Bytes, CHECK);
    _perf_max_refill_waste           = create_perf_variable("maxRefillWaste", PerfData::U_Bytes, CHECK);
    _perf_total_num_slow_allocations = create_perf_variable("slowAlloc",      PerfData::U_None,  CHECK);
    _perf_max_num_slow_allocations   = create_perf_variable("maxSlowAlloc",   PerfData::U_None,  CHECK);
  }
}

ThreadLocalAllocStats::ThreadLocalAllocStats() :
    _num_allocating_threads(0),
    _total_num_refills(0),
    _max_num_refills(0),
    _total_allocated_size(0),
    _total_requested_bytes(0),
    _total_gc_waste(0),
    _max_gc_waste(0),
    _total_refill_waste(0),
    _max_refill_waste(0),
    _total_num_slow_allocations(0),
    _max_num_slow_allocations(0) {}

unsigned int ThreadLocalAllocStats::num_allocating_threads_avg() {
  return MAX2((unsigned int)(_num_allocating_threads_avg.average() + 0.5), 1U);
}

float ThreadLocalAllocStats::total_requested_size_fraction_avg() {
  return _total_requested_size_fraction.average();
}

void ThreadLocalAllocStats::update_current_thread_stats(unsigned int num_refills,
                                                        size_t requested_bytes,
                                                        size_t alloc_size_for_tlab,
                                                        size_t gc_waste,
                                                        size_t refill_waste,
                                                        unsigned int num_slow_allocations) {
  _num_allocating_threads     += 1;
  _total_num_refills          += num_refills;
  _max_num_refills             = MAX2(_max_num_refills, num_refills);
  _total_allocated_size       += alloc_size_for_tlab;
  _total_requested_bytes      += requested_bytes;
  _total_gc_waste             += gc_waste;
  _max_gc_waste                = MAX2(_max_gc_waste, gc_waste);
  _total_refill_waste         += refill_waste;
  _max_refill_waste            = MAX2(_max_refill_waste, refill_waste);
  _total_num_slow_allocations += num_slow_allocations;
  _max_num_slow_allocations    = MAX2(_max_num_slow_allocations, num_slow_allocations);
}

void ThreadLocalAllocStats::update(const ThreadLocalAllocStats& other) {
  _num_allocating_threads     += other._num_allocating_threads;
  _total_num_refills          += other._total_num_refills;
  _max_num_refills             = MAX2(_max_num_refills, other._max_num_refills);
  _total_allocated_size       += other._total_allocated_size;
  _total_requested_bytes      += other._total_requested_bytes;
  _total_gc_waste             += other._total_gc_waste;
  _max_gc_waste                = MAX2(_max_gc_waste, other._max_gc_waste);
  _total_refill_waste         += other._total_refill_waste;
  _max_refill_waste            = MAX2(_max_refill_waste, other._max_refill_waste);
  _total_num_slow_allocations += other._total_num_slow_allocations;
  _max_num_slow_allocations    = MAX2(_max_num_slow_allocations, other._max_num_slow_allocations);
}

void ThreadLocalAllocStats::reset() {
  _num_allocating_threads     = 0;
  _total_num_refills          = 0;
  _max_num_refills            = 0;
  _total_allocated_size       = 0;
  _total_requested_bytes      = 0;
  _total_gc_waste             = 0;
  _max_gc_waste               = 0;
  _total_refill_waste         = 0;
  _max_refill_waste           = 0;
  _total_num_slow_allocations = 0;
  _max_num_slow_allocations   = 0;
}

void ThreadLocalAllocStats::publish() {
  if (_total_requested_bytes == 0) {
    return;
  }

  _num_allocating_threads_avg.sample(_num_allocating_threads);

  {
    const size_t tlab_capacity = Universe::heap()->tlab_capacity();
    const size_t tlab_used = Universe::heap()->tlab_used();
    if (tlab_used > 0.5 * tlab_capacity) {
      // To avoid divide-by-zero
      const size_t effective_tlab_capacity = MAX2(tlab_capacity, size_t(1));
      const float requested_size_fraction = (float)_total_requested_bytes / effective_tlab_capacity;
      _total_requested_size_fraction.sample(MIN2(requested_size_fraction, 1.0f));
    }
  }

  const size_t waste = _total_gc_waste + _total_refill_waste;
  const double waste_percent = percent_of(waste, _total_allocated_size);

  const double gc_waste_pct = percent_of(_total_gc_waste, _total_allocated_size);
  const double refill_waste_pct = percent_of(_total_refill_waste, _total_allocated_size);

  log_debug(gc, tlab)("TLAB totals: thrds: %d alloc-frac: %.1f%% refills: %d max: %d"
                      " slow allocs: %d max %d waste: %.1f%%"
                      " gc: %zuB(%.1f%%) max: %zuB"
                      " refill: %zuB(%.1f%%) max: %zuB",
                      _num_allocating_threads, _total_requested_size_fraction.average() * 100, _total_num_refills, _max_num_refills,
                      _total_num_slow_allocations, _max_num_slow_allocations, waste_percent,
                      _total_gc_waste * HeapWordSize, gc_waste_pct, _max_gc_waste * HeapWordSize,
                      _total_refill_waste * HeapWordSize, refill_waste_pct, _max_refill_waste * HeapWordSize);

  if (UsePerfData) {
    _perf_num_allocating_threads      ->set_value(_num_allocating_threads);
    _perf_total_num_refills           ->set_value(_total_num_refills);
    _perf_max_num_refills             ->set_value(_max_num_refills);
    _perf_total_allocated_size        ->set_value(_total_allocated_size);
    _perf_total_gc_waste              ->set_value(_total_gc_waste);
    _perf_max_gc_waste                ->set_value(_max_gc_waste);
    _perf_total_refill_waste          ->set_value(_total_refill_waste);
    _perf_max_refill_waste            ->set_value(_max_refill_waste);
    _perf_total_num_slow_allocations  ->set_value(_total_num_slow_allocations);
    _perf_max_num_slow_allocations    ->set_value(_max_num_slow_allocations);
  }
}

size_t ThreadLocalAllocBuffer::end_reserve() {
  return CollectedHeap::lab_alignment_reserve();
}

size_t ThreadLocalAllocBuffer::estimated_used_bytes() const {
  // Data races due to unsynchronized access like the following reads to _start
  // and _top are undefined behavior. Atomic<T> would not provide any additional
  // guarantees, so use AtomicAccess directly.
  HeapWord* start = AtomicAccess::load(&_start);
  HeapWord* top = AtomicAccess::load(&_top);
  // If there has been a race when retrieving _top and _start, return 0.
  if (top < start) {
    return 0;
  }
  size_t used_bytes = pointer_delta(top, start, 1);
  // Comparing diff with the maximum allowed size will ensure that we don't add
  // the used bytes from a semi-initialized TLAB ending up with implausible values.
  // In this case also just return 0.
  if (used_bytes > ThreadLocalAllocBuffer::max_size_in_bytes()) {
    return 0;
  }
  return used_bytes;
}
