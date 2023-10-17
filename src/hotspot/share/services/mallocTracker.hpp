/*
 * Copyright (c) 2014, 2021, Oracle and/or its affiliates. All rights reserved.
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

#if INCLUDE_NMT

#include "memory/allocation.hpp"
#include "runtime/atomic.hpp"
#include "runtime/threadCritical.hpp"
#include "services/nmtCommon.hpp"
#include "utilities/nativeCallStack.hpp"

/*
 * This counter class counts memory allocation and deallocation,
 * records total memory allocation size and number of allocations.
 * The counters are updated atomically.
 */
class MemoryCounter {
 private:
  volatile size_t   _count;
  volatile size_t   _size;

  // Peak size and count. Note: Peak count is the count at the point
  // peak size was reached, not the absolute highest peak count.
  volatile size_t _peak_count;
  volatile size_t _peak_size;
  void update_peak(size_t size, size_t cnt);

 public:
  MemoryCounter() : _count(0), _size(0), _peak_count(0), _peak_size(0) {}

  inline void allocate(size_t sz) {
    size_t cnt = Atomic::add(&_count, size_t(1), memory_order_relaxed);
    if (sz > 0) {
      size_t sum = Atomic::add(&_size, sz, memory_order_relaxed);
      update_peak(sum, cnt);
    }
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
      update_peak(sum, _count);
    }
  }

  inline size_t count() const { return Atomic::load(&_count); }
  inline size_t size()  const { return Atomic::load(&_size);  }

  inline size_t peak_count() const {
    return Atomic::load(&_peak_count);
  }

  inline size_t peak_size() const {
    return Atomic::load(&_peak_size);
  }
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
  inline size_t malloc_peak_size()  const { return _malloc.peak_size(); }
  inline size_t malloc_count() const { return _malloc.count();}
  inline size_t arena_size()   const { return _arena.size();  }
  inline size_t arena_peak_size()  const { return _arena.peak_size(); }
  inline size_t arena_count()  const { return _arena.count(); }

  const MemoryCounter* malloc_counter() const { return &_malloc; }
  const MemoryCounter* arena_counter()  const { return &_arena;  }
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

  inline size_t malloc_overhead() const;

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


/*
 * Malloc tracking header.
 *
 * If NMT is active (state >= minimal), we need to track allocations. A simple and cheap way to
 * do this is by using malloc headers.
 *
 * The user allocation is preceded by a header and is immediately followed by a (possibly unaligned)
 *  footer canary:
 *
 * +--------------+-------------  ....  ------------------+-----+
 * |    header    |               user                    | can |
 * |              |             allocation                | ary |
 * +--------------+-------------  ....  ------------------+-----+
 *     16 bytes              user size                      2 byte
 *
 * Alignment:
 *
 * The start of the user allocation needs to adhere to malloc alignment. We assume 128 bits
 * on both 64-bit/32-bit to be enough for that. So the malloc header is 16 bytes long on both
 * 32-bit and 64-bit.
 *
 * Layout on 64-bit:
 *
 *     0        1        2        3        4        5        6        7
 * +--------+--------+--------+--------+--------+--------+--------+--------+
 * |                            64-bit size                                |  ...
 * +--------+--------+--------+--------+--------+--------+--------+--------+
 *
 *           8        9        10       11       12       13       14       15          16 ++
 *       +--------+--------+--------+--------+--------+--------+--------+--------+  ------------------------
 *  ...  |   malloc site table marker        | flags  | unused |     canary      |  ... User payload ....
 *       +--------+--------+--------+--------+--------+--------+--------+--------+  ------------------------
 *
 * Layout on 32-bit:
 *
 *     0        1        2        3        4        5        6        7
 * +--------+--------+--------+--------+--------+--------+--------+--------+
 * |            alt. canary            |           32-bit size             |  ...
 * +--------+--------+--------+--------+--------+--------+--------+--------+
 *
 *           8        9        10       11       12       13       14       15          16 ++
 *       +--------+--------+--------+--------+--------+--------+--------+--------+  ------------------------
 *  ...  |   malloc site table marker        | flags  | unused |     canary      |  ... User payload ....
 *       +--------+--------+--------+--------+--------+--------+--------+--------+  ------------------------
 *
 * Notes:
 * - We have a canary in the two bytes directly preceding the user payload. That allows us to
 *   catch negative buffer overflows.
 * - On 32-bit, due to the smaller size_t, we have some bits to spare. So we also have a second
 *   canary at the very start of the malloc header (generously sized 32 bits).
 * - The footer canary consists of two bytes. Since the footer location may be unaligned to 16 bits,
 *   the bytes are stored individually.
 */

class MallocHeader {

  NOT_LP64(uint32_t _alt_canary);
  const size_t _size;
  const uint32_t _mst_marker;
  const uint8_t _flags;
  const uint8_t _unused;
  uint16_t _canary;

  static const uint16_t _header_canary_life_mark = 0xE99E;
  static const uint16_t _header_canary_dead_mark = 0xD99D;
  static const uint16_t _footer_canary_life_mark = 0xE88E;
  static const uint16_t _footer_canary_dead_mark = 0xD88D;
  NOT_LP64(static const uint32_t _header_alt_canary_life_mark = 0xE99EE99E;)
  NOT_LP64(static const uint32_t _header_alt_canary_dead_mark = 0xD88DD88D;)

  // We discount sizes larger than these
  static const size_t max_reasonable_malloc_size = LP64_ONLY(256 * G) NOT_LP64(3500 * M);

  void print_block_on_error(outputStream* st, address bad_address) const;

  static uint16_t build_footer(uint8_t b1, uint8_t b2) { return ((uint16_t)b1 << 8) | (uint16_t)b2; }

  uint8_t* footer_address() const   { return ((address)this) + sizeof(MallocHeader) + _size; }
  uint16_t get_footer() const       { return build_footer(footer_address()[0], footer_address()[1]); }
  void set_footer(uint16_t v)       { footer_address()[0] = v >> 8; footer_address()[1] = (uint8_t)v; }

 public:

  MallocHeader(size_t size, MEMFLAGS flags, const NativeCallStack& stack, uint32_t mst_marker)
    : _size(size), _mst_marker(mst_marker), _flags(NMTUtil::flag_to_index(flags)),
      _unused(0), _canary(_header_canary_life_mark)
  {
    assert(size < max_reasonable_malloc_size, "Too large allocation size?");
    // On 32-bit we have some bits more, use them for a second canary
    // guarding the start of the header.
    NOT_LP64(_alt_canary = _header_alt_canary_life_mark;)
    set_footer(_footer_canary_life_mark); // set after initializing _size
  }

  inline size_t   size()  const { return _size; }
  inline MEMFLAGS flags() const { return (MEMFLAGS)_flags; }
  inline uint32_t mst_marker() const { return _mst_marker; }
  bool get_stack(NativeCallStack& stack) const;

  void mark_block_as_dead();

  // Check block integrity. If block is broken, print out a report
  // to tty (optionally with hex dump surrounding the broken block),
  // then trigger a fatal error.
  void check_block_integrity() const;
};

size_t MallocMemorySnapshot::malloc_overhead() const {
  return _all_mallocs.count() * sizeof(MallocHeader);
}

// This needs to be true on both 64-bit and 32-bit platforms
STATIC_ASSERT(sizeof(MallocHeader) == (sizeof(uint64_t) * 2));


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

#endif // INCLUDE_NMT


#endif // SHARE_SERVICES_MALLOCTRACKER_HPP
