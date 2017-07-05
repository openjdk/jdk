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

#ifndef SHARE_VM_MEMORY_THREADLOCALALLOCBUFFER_HPP
#define SHARE_VM_MEMORY_THREADLOCALALLOCBUFFER_HPP

#include "gc_implementation/shared/gcUtil.hpp"
#include "oops/typeArrayOop.hpp"
#include "runtime/perfData.hpp"

class GlobalTLABStats;

// ThreadLocalAllocBuffer: a descriptor for thread-local storage used by
// the threads for allocation.
//            It is thread-private at any time, but maybe multiplexed over
//            time across multiple threads. The park()/unpark() pair is
//            used to make it avaiable for such multiplexing.
class ThreadLocalAllocBuffer: public CHeapObj {
  friend class VMStructs;
private:
  HeapWord* _start;                              // address of TLAB
  HeapWord* _top;                                // address after last allocation
  HeapWord* _pf_top;                             // allocation prefetch watermark
  HeapWord* _end;                                // allocation end (excluding alignment_reserve)
  size_t    _desired_size;                       // desired size   (including alignment_reserve)
  size_t    _refill_waste_limit;                 // hold onto tlab if free() is larger than this

  static unsigned _target_refills;               // expected number of refills between GCs

  unsigned  _number_of_refills;
  unsigned  _fast_refill_waste;
  unsigned  _slow_refill_waste;
  unsigned  _gc_waste;
  unsigned  _slow_allocations;

  AdaptiveWeightedAverage _allocation_fraction;  // fraction of eden allocated in tlabs

  void accumulate_statistics();
  void initialize_statistics();

  void set_start(HeapWord* start)                { _start = start; }
  void set_end(HeapWord* end)                    { _end = end; }
  void set_top(HeapWord* top)                    { _top = top; }
  void set_pf_top(HeapWord* pf_top)              { _pf_top = pf_top; }
  void set_desired_size(size_t desired_size)     { _desired_size = desired_size; }
  void set_refill_waste_limit(size_t waste)      { _refill_waste_limit = waste;  }

  size_t initial_refill_waste_limit()            { return desired_size() / TLABRefillWasteFraction; }

  static int    target_refills()                 { return _target_refills; }
  size_t initial_desired_size();

  size_t remaining() const                       { return end() == NULL ? 0 : pointer_delta(hard_end(), top()); }

  // Make parsable and release it.
  void reset();

  // Resize based on amount of allocation, etc.
  void resize();

  void invariants() const { assert(top() >= start() && top() <= end(), "invalid tlab"); }

  void initialize(HeapWord* start, HeapWord* top, HeapWord* end);

  void print_stats(const char* tag);

  Thread* myThread();

  // statistics

  int number_of_refills() const { return _number_of_refills; }
  int fast_refill_waste() const { return _fast_refill_waste; }
  int slow_refill_waste() const { return _slow_refill_waste; }
  int gc_waste() const          { return _gc_waste; }
  int slow_allocations() const  { return _slow_allocations; }

  static GlobalTLABStats* _global_stats;
  static GlobalTLABStats* global_stats() { return _global_stats; }

public:
  ThreadLocalAllocBuffer() : _allocation_fraction(TLABAllocationWeight) {
    // do nothing.  tlabs must be inited by initialize() calls
  }

  static const size_t min_size()                 { return align_object_size(MinTLABSize / HeapWordSize); }
  static const size_t max_size();

  HeapWord* start() const                        { return _start; }
  HeapWord* end() const                          { return _end; }
  HeapWord* hard_end() const                     { return _end + alignment_reserve(); }
  HeapWord* top() const                          { return _top; }
  HeapWord* pf_top() const                       { return _pf_top; }
  size_t desired_size() const                    { return _desired_size; }
  size_t free() const                            { return pointer_delta(end(), top()); }
  // Don't discard tlab if remaining space is larger than this.
  size_t refill_waste_limit() const              { return _refill_waste_limit; }

  // Allocate size HeapWords. The memory is NOT initialized to zero.
  inline HeapWord* allocate(size_t size);

  // Reserve space at the end of TLAB
  static size_t end_reserve() {
    int reserve_size = typeArrayOopDesc::header_size(T_INT);
    if (AllocatePrefetchStyle == 3) {
      // BIS is used to prefetch - we need a space for it.
      // +1 for rounding up to next cache line +1 to be safe
      int lines = AllocatePrefetchLines + 2;
      int step_size = AllocatePrefetchStepSize;
      int distance = AllocatePrefetchDistance;
      int prefetch_end = (distance + step_size*lines)/(int)HeapWordSize;
      reserve_size = MAX2(reserve_size, prefetch_end);
    }
    return reserve_size;
  }
  static size_t alignment_reserve()              { return align_object_size(end_reserve()); }
  static size_t alignment_reserve_in_bytes()     { return alignment_reserve() * HeapWordSize; }

  // Return tlab size or remaining space in eden such that the
  // space is large enough to hold obj_size and necessary fill space.
  // Otherwise return 0;
  inline size_t compute_size(size_t obj_size);

  // Record slow allocation
  inline void record_slow_allocation(size_t obj_size);

  // Initialization at startup
  static void startup_initialization();

  // Make an in-use tlab parsable, optionally also retiring it.
  void make_parsable(bool retire);

  // Retire in-use tlab before allocation of a new tlab
  void clear_before_allocation();

  // Accumulate statistics across all tlabs before gc
  static void accumulate_statistics_before_gc();

  // Resize tlabs for all threads
  static void resize_all_tlabs();

  void fill(HeapWord* start, HeapWord* top, size_t new_size);
  void initialize();

  static size_t refill_waste_limit_increment()   { return TLABWasteIncrement; }

  // Code generation support
  static ByteSize start_offset()                 { return byte_offset_of(ThreadLocalAllocBuffer, _start); }
  static ByteSize end_offset()                   { return byte_offset_of(ThreadLocalAllocBuffer, _end  ); }
  static ByteSize top_offset()                   { return byte_offset_of(ThreadLocalAllocBuffer, _top  ); }
  static ByteSize pf_top_offset()                { return byte_offset_of(ThreadLocalAllocBuffer, _pf_top  ); }
  static ByteSize size_offset()                  { return byte_offset_of(ThreadLocalAllocBuffer, _desired_size ); }
  static ByteSize refill_waste_limit_offset()    { return byte_offset_of(ThreadLocalAllocBuffer, _refill_waste_limit ); }

  static ByteSize number_of_refills_offset()     { return byte_offset_of(ThreadLocalAllocBuffer, _number_of_refills ); }
  static ByteSize fast_refill_waste_offset()     { return byte_offset_of(ThreadLocalAllocBuffer, _fast_refill_waste ); }
  static ByteSize slow_allocations_offset()      { return byte_offset_of(ThreadLocalAllocBuffer, _slow_allocations ); }

  void verify();
};

class GlobalTLABStats: public CHeapObj {
private:

  // Accumulate perfdata in private variables because
  // PerfData should be write-only for security reasons
  // (see perfData.hpp)
  unsigned _allocating_threads;
  unsigned _total_refills;
  unsigned _max_refills;
  size_t   _total_allocation;
  size_t   _total_gc_waste;
  size_t   _max_gc_waste;
  size_t   _total_slow_refill_waste;
  size_t   _max_slow_refill_waste;
  size_t   _total_fast_refill_waste;
  size_t   _max_fast_refill_waste;
  unsigned _total_slow_allocations;
  unsigned _max_slow_allocations;

  PerfVariable* _perf_allocating_threads;
  PerfVariable* _perf_total_refills;
  PerfVariable* _perf_max_refills;
  PerfVariable* _perf_allocation;
  PerfVariable* _perf_gc_waste;
  PerfVariable* _perf_max_gc_waste;
  PerfVariable* _perf_slow_refill_waste;
  PerfVariable* _perf_max_slow_refill_waste;
  PerfVariable* _perf_fast_refill_waste;
  PerfVariable* _perf_max_fast_refill_waste;
  PerfVariable* _perf_slow_allocations;
  PerfVariable* _perf_max_slow_allocations;

  AdaptiveWeightedAverage _allocating_threads_avg;

public:
  GlobalTLABStats();

  // Initialize all counters
  void initialize();

  // Write all perf counters to the perf_counters
  void publish();

  void print();

  // Accessors
  unsigned allocating_threads_avg() {
    return MAX2((unsigned)(_allocating_threads_avg.average() + 0.5), 1U);
  }

  size_t allocation() {
    return _total_allocation;
  }

  // Update methods

  void update_allocating_threads() {
    _allocating_threads++;
  }
  void update_number_of_refills(unsigned value) {
    _total_refills += value;
    _max_refills    = MAX2(_max_refills, value);
  }
  void update_allocation(size_t value) {
    _total_allocation += value;
  }
  void update_gc_waste(size_t value) {
    _total_gc_waste += value;
    _max_gc_waste    = MAX2(_max_gc_waste, value);
  }
  void update_fast_refill_waste(size_t value) {
    _total_fast_refill_waste += value;
    _max_fast_refill_waste    = MAX2(_max_fast_refill_waste, value);
  }
  void update_slow_refill_waste(size_t value) {
    _total_slow_refill_waste += value;
    _max_slow_refill_waste    = MAX2(_max_slow_refill_waste, value);
  }
  void update_slow_allocations(unsigned value) {
    _total_slow_allocations += value;
    _max_slow_allocations    = MAX2(_max_slow_allocations, value);
  }
};

#endif // SHARE_VM_MEMORY_THREADLOCALALLOCBUFFER_HPP
