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
  MemoryCounter     _tracking_header;


 public:
  inline MallocMemory*  by_type(MEMFLAGS flags) {
    int index = NMTUtil::flag_to_index(flags);
    return &_malloc[index];
  }

  inline MemoryCounter* malloc_overhead() {
    return &_tracking_header;
  }

  // Total malloc invocation count
  size_t total_count() const;
  // Total malloc'd memory amount
  size_t total() const;
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
    s->_tracking_header = _tracking_header;
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
   }

   static inline void record_free(size_t size, MEMFLAGS flag) {
     as_snapshot()->by_type(flag)->record_free(size);
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

   // Record memory used by malloc tracking header
   static inline void record_new_malloc_header(size_t sz) {
     as_snapshot()->malloc_overhead()->allocate(sz);
   }

   static inline void record_free_malloc_header(size_t sz) {
     as_snapshot()->malloc_overhead()->deallocate(sz);
   }

   // The memory used by malloc tracking headers
   static inline size_t tracking_overhead() {
     return as_snapshot()->malloc_overhead()->size();
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
 *  ...  |   bucket idx    |     pos idx     | flags  | unused |     canary      |  ... User payload ....
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
 *  ...  |   bucket idx    |     pos idx     | flags  | unused |     canary      |  ... User payload ....
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
  size_t _size;
  uint16_t _bucket_idx;
  uint16_t _pos_idx;
  uint8_t _flags;
  uint8_t _unused;
  uint16_t _canary;

#define MAX_MALLOCSITE_TABLE_SIZE (USHRT_MAX - 1)
#define MAX_BUCKET_LENGTH         (USHRT_MAX - 1)

  static const uint16_t _header_canary_life_mark = 0xE99E;
  static const uint16_t _header_canary_dead_mark = 0xD99D;
  static const uint16_t _footer_canary_life_mark = 0xE88E;
  static const uint16_t _footer_canary_dead_mark = 0xD88D;
  NOT_LP64(static const uint32_t _header_alt_canary_life_mark = 0xE99EE99E;)
  NOT_LP64(static const uint32_t _header_alt_canary_dead_mark = 0xD88DD88D;)

  // We discount sizes larger than these
  static const size_t max_reasonable_malloc_size = LP64_ONLY(256 * G) NOT_LP64(3500 * M);

  // Check block integrity. If block is broken, print out a report
  // to tty (optionally with hex dump surrounding the broken block),
  // then trigger a fatal error.
  void check_block_integrity() const;
  void print_block_on_error(outputStream* st, address bad_address) const;
  void mark_block_as_dead();

  static uint16_t build_footer(uint8_t b1, uint8_t b2) { return ((uint16_t)b1 << 8) | (uint16_t)b2; }

  uint8_t* footer_address() const   { return ((address)this) + sizeof(MallocHeader) + _size; }
  uint16_t get_footer() const       { return build_footer(footer_address()[0], footer_address()[1]); }
  void set_footer(uint16_t v)       { footer_address()[0] = v >> 8; footer_address()[1] = (uint8_t)v; }

 public:

  MallocHeader(size_t size, MEMFLAGS flags, const NativeCallStack& stack, NMT_TrackingLevel level) {
    assert(size < max_reasonable_malloc_size, "Too large allocation size?");

    _flags = NMTUtil::flag_to_index(flags);
    set_size(size);
    if (level == NMT_detail) {
      size_t bucket_idx;
      size_t pos_idx;
      if (record_malloc_site(stack, size, &bucket_idx, &pos_idx, flags)) {
        assert(bucket_idx <= MAX_MALLOCSITE_TABLE_SIZE, "Overflow bucket index");
        assert(pos_idx <= MAX_BUCKET_LENGTH, "Overflow bucket position index");
        _bucket_idx = (uint16_t)bucket_idx;
        _pos_idx = (uint16_t)pos_idx;
      }
    }

    _unused = 0;
    _canary = _header_canary_life_mark;
    // On 32-bit we have some bits more, use them for a second canary
    // guarding the start of the header.
    NOT_LP64(_alt_canary = _header_alt_canary_life_mark;)
    set_footer(_footer_canary_life_mark); // set after initializing _size

    MallocMemorySummary::record_malloc(size, flags);
    MallocMemorySummary::record_new_malloc_header(sizeof(MallocHeader));
  }

  inline size_t   size()  const { return _size; }
  inline MEMFLAGS flags() const { return (MEMFLAGS)_flags; }
  bool get_stack(NativeCallStack& stack) const;

  // Cleanup tracking information and mark block as dead before the memory is released.
  void release();

 private:
  inline void set_size(size_t size) {
    _size = size;
  }
  bool record_malloc_site(const NativeCallStack& stack, size_t size,
    size_t* bucket_idx, size_t* pos_idx, MEMFLAGS flags) const;
};

// This needs to be true on both 64-bit and 32-bit platforms
STATIC_ASSERT(sizeof(MallocHeader) == (sizeof(uint64_t) * 2));


// Main class called from MemTracker to track malloc activities
class MallocTracker : AllStatic {
 public:
  // Initialize malloc tracker for specific tracking level
  static bool initialize(NMT_TrackingLevel level);

  // malloc tracking header size for specific tracking level
  static inline size_t malloc_header_size(NMT_TrackingLevel level) {
    return (level == NMT_off) ? 0 : sizeof(MallocHeader);
  }

  // malloc tracking footer size for specific tracking level
  static inline size_t malloc_footer_size(NMT_TrackingLevel level) {
    return (level == NMT_off) ? 0 : sizeof(uint16_t);
  }

  // Parameter name convention:
  // memblock :   the beginning address for user data
  // malloc_base: the beginning address that includes malloc tracking header
  //
  // The relationship:
  // memblock = (char*)malloc_base + sizeof(nmt header)
  //

  // Record  malloc on specified memory block
  static void* record_malloc(void* malloc_base, size_t size, MEMFLAGS flags,
    const NativeCallStack& stack, NMT_TrackingLevel level);

  // Record free on specified memory block
  static void* record_free(void* memblock);

  // Offset memory address to header address
  static inline void* get_base(void* memblock);
  static inline void* get_base(void* memblock, NMT_TrackingLevel level) {
    if (memblock == NULL || level == NMT_off) return memblock;
    return (char*)memblock - malloc_header_size(level);
  }

  // Get memory size
  static inline size_t get_size(void* memblock) {
    MallocHeader* header = malloc_header(memblock);
    return header->size();
  }

  // Get memory type
  static inline MEMFLAGS get_flags(void* memblock) {
    MallocHeader* header = malloc_header(memblock);
    return header->flags();
  }

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
    MallocHeader* header = (MallocHeader*)((char*)memblock - sizeof(MallocHeader));
    return header;
  }
};

#endif // INCLUDE_NMT


#endif // SHARE_SERVICES_MALLOCTRACKER_HPP
