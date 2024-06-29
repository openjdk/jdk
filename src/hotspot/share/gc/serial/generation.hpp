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
#include "gc/shared/space.hpp"
#include "logging/log.hpp"
#include "memory/allocation.hpp"
#include "memory/memRegion.hpp"
#include "memory/virtualspace.hpp"
#include "runtime/mutex.hpp"
#include "runtime/perfData.hpp"
#include "runtime/prefetch.inline.hpp"

// A Generation models a heap area for similarly-aged objects.
// It will contain one ore more spaces holding the actual objects.
//
// The Generation class hierarchy:
//
// Generation                      - abstract base class
// - DefNewGeneration              - allocation area (copy collected)
// - TenuredGeneration             - tenured (old object) space (mark-compact)
//
// The system configuration currently allowed is:
//
//   DefNewGeneration + TenuredGeneration
//

class DefNewGeneration;
class GCMemoryManager;
class ContiguousSpace;
class OopClosure;

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

  MemRegion reserved() const { return _reserved; }

  /* Returns "TRUE" iff "p" points into the reserved area of the generation. */
  bool is_in_reserved(const void* p) const {
    return _reserved.contains(p);
  }

  // Allocate and returns a block of the requested size, or returns "null".
  // Assumes the caller has done any necessary locking.
  virtual HeapWord* allocate(size_t word_size, bool is_tlab) = 0;

  // Like "allocate", but performs any necessary locking internally.
  virtual HeapWord* par_allocate(size_t word_size, bool is_tlab) = 0;

  // Perform a heap collection, attempting to create (at least) enough
  // space to support an allocation of the given "word_size".  If
  // successful, perform the allocation and return the resulting
  // "oop" (initializing the allocated block). If the allocation is
  // still unsuccessful, return "null".
  virtual HeapWord* expand_and_allocate(size_t word_size, bool is_tlab) = 0;

  // Printing
  virtual const char* name() const = 0;
  virtual const char* short_name() const = 0;

  virtual void print() const;
  virtual void print_on(outputStream* st) const;

  virtual void verify() = 0;

public:
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
