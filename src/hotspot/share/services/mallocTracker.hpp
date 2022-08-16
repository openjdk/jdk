/*
 * Copyright (c) 2014, 2022, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_SERVICES_MALLOCTRACKER_HPP
#define SHARE_SERVICES_MALLOCTRACKER_HPP

#include "memory/allocation.hpp"
#include "runtime/atomic.hpp"
#include "runtime/threadCritical.hpp"
#include "services/mallocHeader.hpp"
#include "services/nmtCommon.hpp"
#include "utilities/nativeCallStack.hpp"

class outputStream;

/*
 * This counter class counts memory allocation and deallocation,
 * records total memory allocation size and number of allocations.
 * The counters are updated atomically.
 */
class MemoryCounter {
 private:
  volatile size_t   _count;
  volatile size_t   _size;

  DEBUG_ONLY(volatile size_t   _peak_count;)
  DEBUG_ONLY(volatile size_t   _peak_size; )

 public:
  MemoryCounter() : _count(0), _size(0) {
    DEBUG_ONLY(_peak_count = 0;)
    DEBUG_ONLY(_peak_size  = 0;)
  }

  inline void allocate(size_t sz) {
    size_t cnt = Atomic::add(&_count, size_t(1), memory_order_relaxed);
    if (sz > 0) {
      size_t sum = Atomic::add(&_size, sz, memory_order_relaxed);
      DEBUG_ONLY(update_peak_size(sum);)
    }
    DEBUG_ONLY(update_peak_count(cnt);)
  }

  inline void deallocate(size_t sz) {
    assert(count() > 0, "Nothing allocated yet");
    assert(size() >= sz, "deallocation > allocated");
    Atomic::dec(&_count, memory_order_relaxed);
    if (sz > 0) {
      Atomic::sub(&_size, sz, memory_order_relaxed);
    }
  }

  inline void resize(ssize_t sz) {
    if (sz != 0) {
      assert(sz >= 0 || size() >= size_t(-sz), "Must be");
      size_t sum = Atomic::add(&_size, size_t(sz), memory_order_relaxed);
      DEBUG_ONLY(update_peak_size(sum);)
    }
  }

  inline size_t count() const { return Atomic::load(&_count); }
  inline size_t size()  const { return Atomic::load(&_size);  }

#ifdef ASSERT
  void update_peak_count(size_t cnt);
  void update_peak_size(size_t sz);
  size_t peak_count() const;
  size_t peak_size()  const;
#endif // ASSERT
};

/*
 * Malloc memory used by a particular subsystem.
 * It includes the memory acquired through os::malloc()
 * call and arena's backing memory.
 */
class MallocMemory {
 private:
  MemoryCounter _malloc;
  MemoryCounter _arena;

 public:
  MallocMemory() { }

  inline void record_malloc(size_t sz) {
    _malloc.allocate(sz);
  }

  inline void record_free(size_t sz) {
    _malloc.deallocate(sz);
  }

  inline void record_new_arena() {
    _arena.allocate(0);
  }

  inline void record_arena_free() {
    _arena.deallocate(0);
  }

  inline void record_arena_size_change(ssize_t sz) {
    _arena.resize(sz);
  }

  inline size_t malloc_size()  const { return _malloc.size(); }
  inline size_t malloc_count() const { return _malloc.count();}
  inline size_t arena_size()   const { return _arena.size();  }
  inline size_t arena_count()  const { return _arena.count(); }

  DEBUG_ONLY(inline const MemoryCounter& malloc_counter() const { return _malloc; })
  DEBUG_ONLY(inline const MemoryCounter& arena_counter()  const { return _arena;  })
};

class MallocMemorySummary;

// A snapshot of malloc'd memory, includes malloc memory
// usage by types and memory used by tracking itself.
class MallocMemorySnapshot : public ResourceObj {
  friend class MallocMemorySummary;

 private:
  MallocMemory      _malloc[mt_number_of_types];
  MemoryCounter     _all_mallocs;


 public:
  inline MallocMemory*  by_type(MEMFLAGS flags) {
    int index = NMTUtil::flag_to_index(flags);
    return &_malloc[index];
  }

  inline size_t malloc_overhead() const {
    return _all_mallocs.count() * sizeof(MallocHeader);
  }

  // Total malloc invocation count
  size_t total_count() const {
    return _all_mallocs.count();
  }

  // Total malloc'd memory amount
  size_t total() const {
    return _all_mallocs.size() + malloc_overhead() + total_arena();
  }

  // Total malloc'd memory used by arenas
  size_t total_arena() const;

  inline size_t thread_count() const {
    MallocMemorySnapshot* s = const_cast<MallocMemorySnapshot*>(this);
    return s->by_type(mtThreadStack)->malloc_count();
  }

  void copy_to(MallocMemorySnapshot* s) {
    // Need to make sure that mtChunks don't get deallocated while the
    // copy is going on, because their size is adjusted using this
    // buffer in make_adjustment().
    ThreadCritical tc;
    s->_all_mallocs = _all_mallocs;
    for (int index = 0; index < mt_number_of_types; index ++) {
      s->_malloc[index] = _malloc[index];
    }
  }

  // Make adjustment by subtracting chunks used by arenas
  // from total chunks to get total free chunk size
  void make_adjustment();
};

/*
 * This class is for collecting malloc statistics at summary level
 */
class MallocMemorySummary : AllStatic {
 private:
  // Reserve memory for placement of MallocMemorySnapshot object
  static size_t _snapshot[CALC_OBJ_SIZE_IN_TYPE(MallocMemorySnapshot, size_t)];

 public:
   static void initialize();

   static inline void record_malloc(size_t size, MEMFLAGS flag) {
     as_snapshot()->by_type(flag)->record_malloc(size);
     as_snapshot()->_all_mallocs.allocate(size);
   }

   static inline void record_free(size_t size, MEMFLAGS flag) {
     as_snapshot()->by_type(flag)->record_free(size);
     as_snapshot()->_all_mallocs.deallocate(size);
   }

   static inline void record_new_arena(MEMFLAGS flag) {
     as_snapshot()->by_type(flag)->record_new_arena();
   }

   static inline void record_arena_free(MEMFLAGS flag) {
     as_snapshot()->by_type(flag)->record_arena_free();
   }

   static inline void record_arena_size_change(ssize_t size, MEMFLAGS flag) {
     as_snapshot()->by_type(flag)->record_arena_size_change(size);
   }

   static void snapshot(MallocMemorySnapshot* s) {
     as_snapshot()->copy_to(s);
     s->make_adjustment();
   }

   // The memory used by malloc tracking headers
   static inline size_t tracking_overhead() {
     return as_snapshot()->malloc_overhead();
   }

  static MallocMemorySnapshot* as_snapshot() {
    return (MallocMemorySnapshot*)_snapshot;
  }
};

// Main class called from MemTracker to track malloc activities
class MallocTracker : AllStatic {
 public:
  // Initialize malloc tracker for specific tracking level
  static bool initialize(NMT_TrackingLevel level);

  // The overhead that is incurred by switching on NMT (we need, per malloc allocation,
  // space for header and 16-bit footer)
  static const size_t overhead_per_malloc = sizeof(MallocHeader) + sizeof(uint16_t);

  // Parameter name convention:
  // memblock :   the beginning address for user data
  // malloc_base: the beginning address that includes malloc tracking header
  //
  // The relationship:
  // memblock = (char*)malloc_base + sizeof(nmt header)
  //

  // Record  malloc on specified memory block
  static void* record_malloc(void* malloc_base, size_t size, MEMFLAGS flags,
    const NativeCallStack& stack);

  // Record free on specified memory block
  static void* record_free(void* memblock);

  static inline void record_new_arena(MEMFLAGS flags) {
    MallocMemorySummary::record_new_arena(flags);
  }

  static inline void record_arena_free(MEMFLAGS flags) {
    MallocMemorySummary::record_arena_free(flags);
  }

  static inline void record_arena_size_change(ssize_t size, MEMFLAGS flags) {
    MallocMemorySummary::record_arena_size_change(size, flags);
  }

  // Given a pointer, if it seems to point to the start of a valid malloced block,
  // print the block. Note that since there is very low risk of memory looking
  // accidentally like a valid malloc block header (canaries and all) this is not
  // totally failproof. Only use this during debugging or when you can afford
  // signals popping up, e.g. when writing an hs_err file.
  static bool print_pointer_information(const void* p, outputStream* st);

 private:
  static inline MallocHeader* malloc_header(void *memblock) {
    assert(memblock != NULL, "NULL pointer");
    return (MallocHeader*)((char*)memblock - sizeof(MallocHeader));
  }
  static inline const MallocHeader* malloc_header(const void *memblock) {
    assert(memblock != NULL, "NULL pointer");
    return (const MallocHeader*)((const char*)memblock - sizeof(MallocHeader));
  }
};

#endif // SHARE_SERVICES_MALLOCTRACKER_HPP
