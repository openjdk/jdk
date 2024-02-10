/*
 * Copyright (c) 1997, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_SERIAL_GENERATION_HPP
#define SHARE_GC_SERIAL_GENERATION_HPP

#include "gc/shared/collectorCounters.hpp"
#include "gc/shared/referenceProcessor.hpp"
#include "logging/log.hpp"
#include "memory/allocation.hpp"
#include "memory/memRegion.hpp"
#include "memory/virtualspace.hpp"
#include "runtime/mutex.hpp"
#include "runtime/perfData.hpp"

// A Generation models a heap area for similarly-aged objects.
// It will contain one ore more spaces holding the actual objects.
//
// The Generation class hierarchy:
//
// Generation                      - abstract base class
// - DefNewGeneration              - allocation area (copy collected)
// - TenuredGeneration             - tenured (old object) space (markSweepCompact)
//
// The system configuration currently allowed is:
//
//   DefNewGeneration + TenuredGeneration
//

class DefNewGeneration;
class GCMemoryManager;
class ContiguousSpace;

class OopClosure;
class GCStats;

class Generation: public CHeapObj<mtGC> {
  friend class VMStructs;
 private:
  GCMemoryManager* _gc_manager;

 protected:
  // Minimum and maximum addresses for memory reserved (not necessarily
  // committed) for generation.
  // Used by card marking code. Must not overlap with address ranges of
  // other generations.
  MemRegion _reserved;

  // Memory area reserved for generation
  VirtualSpace _virtual_space;

  // Performance Counters
  CollectorCounters* _gc_counters;

  // Statistics for garbage collection
  GCStats* _gc_stats;

  // Initialize the generation.
  Generation(ReservedSpace rs, size_t initial_byte_size);

 public:
  enum SomePublicConstants {
    // Generations are GenGrain-aligned and have size that are multiples of
    // GenGrain.
    // Note: on ARM we add 1 bit for card_table_base to be properly aligned
    // (we expect its low byte to be zero - see implementation of post_barrier)
    LogOfGenGrain = 16 ARM32_ONLY(+1),
    GenGrain = 1 << LogOfGenGrain
  };

  virtual size_t capacity() const = 0;  // The maximum number of object bytes the
                                        // generation can currently hold.
  virtual size_t used() const = 0;      // The number of used bytes in the gen.
  virtual size_t free() const = 0;      // The number of free bytes in the gen.

  // Support for java.lang.Runtime.maxMemory(); see CollectedHeap.
  // Returns the total number of bytes  available in a generation
  // for the allocation of objects.
  virtual size_t max_capacity() const;

  // The largest number of contiguous free bytes in the generation,
  // including expansion  (Assumes called at a safepoint.)
  virtual size_t contiguous_available() const = 0;
  // The largest number of contiguous free bytes in this or any higher generation.
  virtual size_t max_contiguous_available() const;

  // Returns true if this generation cannot be expanded further
  // without a GC. Override as appropriate.
  virtual bool is_maximal_no_gc() const {
    return _virtual_space.uncommitted_size() == 0;
  }

  MemRegion reserved() const { return _reserved; }

  /* Returns "TRUE" iff "p" points into the reserved area of the generation. */
  bool is_in_reserved(const void* p) const {
    return _reserved.contains(p);
  }

  // Returns "true" iff this generation should be used to allocate an
  // object of the given size.  Young generations might
  // wish to exclude very large objects, for example, since, if allocated
  // often, they would greatly increase the frequency of young-gen
  // collection.
  virtual bool should_allocate(size_t word_size, bool is_tlab) {
    bool result = false;
    size_t overflow_limit = (size_t)1 << (BitsPerSize_t - LogHeapWordSize);
    if (!is_tlab || supports_tlab_allocation()) {
      result = (word_size > 0) && (word_size < overflow_limit);
    }
    return result;
  }

  // Allocate and returns a block of the requested size, or returns "null".
  // Assumes the caller has done any necessary locking.
  virtual HeapWord* allocate(size_t word_size, bool is_tlab) = 0;

  // Like "allocate", but performs any necessary locking internally.
  virtual HeapWord* par_allocate(size_t word_size, bool is_tlab) = 0;

  // Thread-local allocation buffers
  virtual bool supports_tlab_allocation() const { return false; }

  // "obj" is the address of an object in a younger generation.  Allocate space
  // for "obj" in the current (or some higher) generation, and copy "obj" into
  // the newly allocated space, if possible, returning the result (or null if
  // the allocation failed).
  //
  // The "obj_size" argument is just obj->size(), passed along so the caller can
  // avoid repeating the virtual call to retrieve it.
  virtual oop promote(oop obj, size_t obj_size);

  // Returns "true" iff collect() should subsequently be called on this
  // this generation. See comment below.
  // This is a generic implementation which can be overridden.
  //
  // Note: in the current (1.4) implementation, when serialHeap's
  // incremental_collection_will_fail flag is set, all allocations are
  // slow path (the only fast-path place to allocate is DefNew, which
  // will be full if the flag is set).
  // Thus, older generations which collect younger generations should
  // test this flag and collect if it is set.
  virtual bool should_collect(bool   full,
                              size_t word_size,
                              bool   is_tlab) {
    return (full || should_allocate(word_size, is_tlab));
  }

  // Perform a garbage collection.
  // If full is true attempt a full garbage collection of this generation.
  // Otherwise, attempting to (at least) free enough space to support an
  // allocation of the given "word_size".
  virtual void collect(bool   full,
                       bool   clear_all_soft_refs,
                       size_t word_size,
                       bool   is_tlab) = 0;

  // Perform a heap collection, attempting to create (at least) enough
  // space to support an allocation of the given "word_size".  If
  // successful, perform the allocation and return the resulting
  // "oop" (initializing the allocated block). If the allocation is
  // still unsuccessful, return "null".
  virtual HeapWord* expand_and_allocate(size_t word_size, bool is_tlab) = 0;

  // Save the high water marks for the used space in a generation.
  virtual void record_spaces_top() {}

  // Generations may keep statistics about collection. This method
  // updates those statistics. current_generation is the generation
  // that was most recently collected. This allows the generation to
  // decide what statistics are valid to collect. For example, the
  // generation can decide to gather the amount of promoted data if
  // the collection of the young generation has completed.
  GCStats* gc_stats() const { return _gc_stats; }
  virtual void update_gc_stats(Generation* current_generation, bool full) {}

  // Printing
  virtual const char* name() const = 0;
  virtual const char* short_name() const = 0;

  virtual void print() const;
  virtual void print_on(outputStream* st) const;

  virtual void verify() = 0;

  struct StatRecord {
    int invocations;
    elapsedTimer accumulated_time;
    StatRecord() :
      invocations(0),
      accumulated_time(elapsedTimer()) {}
  };
private:
  StatRecord _stat_record;
public:
  StatRecord* stat_record() { return &_stat_record; }

  virtual void print_summary_info_on(outputStream* st);

  // Performance Counter support
  virtual void update_counters() = 0;
  virtual CollectorCounters* counters() { return _gc_counters; }

  GCMemoryManager* gc_manager() const {
    assert(_gc_manager != nullptr, "not initialized yet");
    return _gc_manager;
  }

  void set_gc_manager(GCMemoryManager* gc_manager) {
    _gc_manager = gc_manager;
  }

};

#endif // SHARE_GC_SERIAL_GENERATION_HPP
