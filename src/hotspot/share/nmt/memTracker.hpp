/*
 * Copyright (c) 2013, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_NMT_MEMTRACKER_HPP
#define SHARE_NMT_MEMTRACKER_HPP

#include "nmt/mallocTracker.hpp"
#include "nmt/nmtCommon.hpp"
#include "nmt/memoryFileTracker.hpp"
#include "nmt/threadStackTracker.hpp"
#include "nmt/virtualMemoryTracker.hpp"
#include "runtime/mutexLocker.hpp"
#include "utilities/debug.hpp"
#include "utilities/nativeCallStack.hpp"

#define CURRENT_PC ((MemTracker::tracking_level() == NMT_detail) ? \
                    NativeCallStack(0) : FAKE_CALLSTACK)
#define CALLER_PC  ((MemTracker::tracking_level() == NMT_detail) ?  \
                    NativeCallStack(1) : FAKE_CALLSTACK)

class MemBaseline;

class MemTracker : AllStatic {
  friend class VirtualMemoryTrackerTest;

  // Helper; asserts that we are in post-NMT-init phase
  static void assert_post_init() {
    assert(is_initialized(), "NMT not yet initialized.");
  }

 public:

  // Initializes NMT to whatever -XX:NativeMemoryTracking says.
  //  - Can only be called once.
  //  - NativeMemoryTracking must be validated beforehand.
  static void initialize();

  // Returns true if NMT had been initialized.
  static bool is_initialized()  {
    return _tracking_level != NMT_unknown;
  }

  static inline NMT_TrackingLevel tracking_level() {
    return _tracking_level;
  }

  static inline bool enabled() {
    return _tracking_level > NMT_off;
  }

  // Per-malloc overhead incurred by NMT, depending on the current NMT level
  static size_t overhead_per_malloc() {
    return enabled() ? MallocTracker::overhead_per_malloc() : 0;
  }

  static inline void* record_malloc(void* mem_base, size_t size, MemTag mem_tag,
    const NativeCallStack& stack) {
    assert(mem_base != nullptr, "caller should handle null");
    if (enabled()) {
      return MallocTracker::record_malloc(mem_base, size, mem_tag, stack);
    }
    return mem_base;
  }

  // Record malloc free and return malloc base address
  static inline void* record_free(void* memblock) {
    // Never turned on
    assert(memblock != nullptr, "caller should handle null");
    if (!enabled()) {
      return memblock;
    }
    return MallocTracker::record_free_block(memblock);
  }
  static inline void deaccount(MallocHeader::FreeInfo free_info) {
    assert(enabled(), "NMT must be enabled");
    MallocTracker::deaccount(free_info);
  }

  // Record creation of an arena
  static inline void record_new_arena(MemTag mem_tag) {
    if (!enabled()) return;
    MallocTracker::record_new_arena(mem_tag);
  }

  // Record destruction of an arena
  static inline void record_arena_free(MemTag mem_tag) {
    if (!enabled()) return;
    MallocTracker::record_arena_free(mem_tag);
  }

  // Record arena size change. Arena size is the size of all arena
  // chunks that are backing up the arena.
  static inline void record_arena_size_change(ssize_t diff, MemTag mem_tag) {
    if (!enabled()) return;
    MallocTracker::record_arena_size_change(diff, mem_tag);
  }

  // Note: virtual memory operations should only ever be called after NMT initialization
  //  (we do not do any reservations before that).

  static inline void record_virtual_memory_reserve(void* addr, size_t size, const NativeCallStack& stack,
    MemTag mem_tag = mtNone) {
    assert_post_init();
    if (!enabled()) return;
    if (addr != nullptr) {
      NmtVirtualMemoryLocker ml;
      VirtualMemoryTracker::add_reserved_region((address)addr, size, stack, mem_tag);
    }
  }

  static inline void record_virtual_memory_release(address addr, size_t size) {
    assert_post_init();
    if (!enabled()) return;
    if (addr != nullptr) {
      VirtualMemoryTracker::remove_released_region((address)addr, size);
    }
  }

  static inline void record_virtual_memory_uncommit(address addr, size_t size) {
    assert_post_init();
    if (!enabled()) return;
    if (addr != nullptr) {
      VirtualMemoryTracker::remove_uncommitted_region((address)addr, size);
    }
  }

  static inline void record_virtual_memory_reserve_and_commit(void* addr, size_t size,
    const NativeCallStack& stack, MemTag mem_tag = mtNone) {
    assert_post_init();
    if (!enabled()) return;
    if (addr != nullptr) {
      NmtVirtualMemoryLocker ml;
      VirtualMemoryTracker::add_reserved_region((address)addr, size, stack, mem_tag);
      VirtualMemoryTracker::add_committed_region((address)addr, size, stack);
    }
  }

  static inline void record_virtual_memory_commit(void* addr, size_t size,
    const NativeCallStack& stack) {
    assert_post_init();
    if (!enabled()) return;
    if (addr != nullptr) {
      NmtVirtualMemoryLocker ml;
      VirtualMemoryTracker::add_committed_region((address)addr, size, stack);
    }
  }

  static inline MemoryFileTracker::MemoryFile* register_file(const char* descriptive_name) {
    assert_post_init();
    if (!enabled()) return nullptr;
    NmtVirtualMemoryLocker ml;
    return MemoryFileTracker::Instance::make_file(descriptive_name);
  }

  static inline void remove_file(MemoryFileTracker::MemoryFile* file) {
    assert_post_init();
    if (!enabled()) return;
    assert(file != nullptr, "must be");
    NmtVirtualMemoryLocker ml;
    MemoryFileTracker::Instance::free_file(file);
  }

  static inline void allocate_memory_in(MemoryFileTracker::MemoryFile* file, size_t offset, size_t size,
                                       const NativeCallStack& stack, MemTag mem_tag) {
    assert_post_init();
    if (!enabled()) return;
    assert(file != nullptr, "must be");
    NmtVirtualMemoryLocker ml;
    MemoryFileTracker::Instance::allocate_memory(file, offset, size, stack, mem_tag);
  }

  static inline void free_memory_in(MemoryFileTracker::MemoryFile* file,
                                        size_t offset, size_t size) {
    assert_post_init();
    if (!enabled()) return;
    assert(file != nullptr, "must be");
    NmtVirtualMemoryLocker ml;
    MemoryFileTracker::Instance::free_memory(file, offset, size);
  }

  // Given an existing memory mapping registered with NMT and a splitting
  //  address, split the mapping in two. The memory region is supposed to
  //  be fully uncommitted.
  //
  // The two new memory regions will be both registered under stack and
  //  memory flags of the original region.
  static inline void record_virtual_memory_split_reserved(void* addr, size_t size, size_t split, MemTag mem_tag, MemTag split_tag) {
    assert_post_init();
    if (!enabled()) return;
    if (addr != nullptr) {
      NmtVirtualMemoryLocker ml;
      VirtualMemoryTracker::split_reserved_region((address)addr, size, split, mem_tag, split_tag);
    }
  }

  static inline void record_virtual_memory_tag(void* addr, MemTag mem_tag) {
    assert_post_init();
    if (!enabled()) return;
    if (addr != nullptr) {
      NmtVirtualMemoryLocker ml;
      VirtualMemoryTracker::set_reserved_region_type((address)addr, mem_tag);
    }
  }

  static void record_thread_stack(void* addr, size_t size) {
    assert_post_init();
    if (!enabled()) return;
    if (addr != nullptr) {
      ThreadStackTracker::new_thread_stack((address)addr, size, CALLER_PC);
    }
  }

  static inline void release_thread_stack(void* addr, size_t size) {
    assert_post_init();
    if (!enabled()) return;
    if (addr != nullptr) {
      ThreadStackTracker::delete_thread_stack((address)addr, size);
    }
  }

  // Query lock is used to synchronize the access to tracking data.
  // So far, it is only used by JCmd query, but it may be used by
  // other tools.
  static inline Mutex* query_lock() {
    assert(NMTQuery_lock != nullptr, "not initialized!");
    return NMTQuery_lock;
  }

  // Report during error reporting.
  static void error_report(outputStream* output);

  // Report when handling PrintNMTStatistics before VM shutdown.
  static void final_report(outputStream* output);

  // Stored baseline
  static inline MemBaseline& get_baseline() {
    return _baseline;
  }

  static void tuning_statistics(outputStream* out);

  // MallocLimt: Given an allocation size s, check if mallocing this much
  // for MemTag would hit either the global limit or the limit for MemTag.
  static inline bool check_exceeds_limit(size_t s, MemTag mem_tag);

  // Given an unknown pointer, check if it points into a known region; print region if found
  // and return true; false if not found.
  static bool print_containing_region(const void* p, outputStream* out);

 private:
  static void report(bool summary_only, outputStream* output, size_t scale);

 private:
  // Tracking level
  static NMT_TrackingLevel   _tracking_level;
  // Stored baseline
  static MemBaseline      _baseline;
  // Query lock
  static Mutex*           _query_lock;
};

#endif // SHARE_NMT_MEMTRACKER_HPP
