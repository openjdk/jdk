/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_SERVICES_MEM_TRACKER_HPP
#define SHARE_VM_SERVICES_MEM_TRACKER_HPP

#include "services/nmtCommon.hpp"
#include "utilities/nativeCallStack.hpp"


#if !INCLUDE_NMT

#define CURRENT_PC   NativeCallStack::empty_stack()
#define CALLER_PC    NativeCallStack::empty_stack()

class Tracker : public StackObj {
 public:
  enum TrackerType {
     uncommit,
     release
  };
  Tracker(enum TrackerType type) : _type(type) { }
  void record(address addr, size_t size) { }
 private:
  enum TrackerType  _type;
};

class MemTracker : AllStatic {
 public:
  static inline NMT_TrackingLevel tracking_level() { return NMT_off; }
  static inline void shutdown() { }
  static inline void init() { }
  static bool check_launcher_nmt_support(const char* value) { return true; }
  static bool verify_nmt_option() { return true; }

  static inline void* record_malloc(void* mem_base, size_t size, MEMFLAGS flag,
    const NativeCallStack& stack, NMT_TrackingLevel level) { return mem_base; }
  static inline size_t malloc_header_size(NMT_TrackingLevel level) { return 0; }
  static inline size_t malloc_header_size(void* memblock) { return 0; }
  static inline void* malloc_base(void* memblock) { return memblock; }
  static inline void* record_free(void* memblock) { return memblock; }

  static inline void record_new_arena(MEMFLAGS flag) { }
  static inline void record_arena_free(MEMFLAGS flag) { }
  static inline void record_arena_size_change(int diff, MEMFLAGS flag) { }
  static inline void record_virtual_memory_reserve(void* addr, size_t size, const NativeCallStack& stack,
                       MEMFLAGS flag = mtNone) { }
  static inline void record_virtual_memory_reserve_and_commit(void* addr, size_t size,
    const NativeCallStack& stack, MEMFLAGS flag = mtNone) { }
  static inline void record_virtual_memory_commit(void* addr, size_t size, const NativeCallStack& stack) { }
  static inline void record_virtual_memory_type(void* addr, MEMFLAGS flag) { }
  static inline void record_thread_stack(void* addr, size_t size) { }
  static inline void release_thread_stack(void* addr, size_t size) { }

  static void final_report(outputStream*) { }
  static void error_report(outputStream*) { }
};

#else

#include "runtime/mutexLocker.hpp"
#include "runtime/threadCritical.hpp"
#include "services/mallocTracker.hpp"
#include "services/virtualMemoryTracker.hpp"

extern volatile bool NMT_stack_walkable;

#define CURRENT_PC ((MemTracker::tracking_level() == NMT_detail && NMT_stack_walkable) ? \
                    NativeCallStack(0, true) : NativeCallStack::empty_stack())
#define CALLER_PC  ((MemTracker::tracking_level() == NMT_detail && NMT_stack_walkable) ?  \
                    NativeCallStack(1, true) : NativeCallStack::empty_stack())

class MemBaseline;

// Tracker is used for guarding 'release' semantics of virtual memory operation, to avoid
// the other thread obtains and records the same region that is just 'released' by current
// thread but before it can record the operation.
class Tracker : public StackObj {
 public:
  enum TrackerType {
     uncommit,
     release
  };

 public:
  Tracker(enum TrackerType type) : _type(type) { }
  void record(address addr, size_t size);
 private:
  enum TrackerType  _type;
  // Virtual memory tracking data structures are protected by ThreadCritical lock.
  ThreadCritical    _tc;
};

class MemTracker : AllStatic {
  friend class VirtualMemoryTrackerTest;

 public:
  static inline NMT_TrackingLevel tracking_level() {
    if (_tracking_level == NMT_unknown) {
      // No fencing is needed here, since JVM is in single-threaded
      // mode.
      _tracking_level = init_tracking_level();
      _cmdline_tracking_level = _tracking_level;
    }
    return _tracking_level;
  }

  // A late initialization, for the stuff(s) can not be
  // done in init_tracking_level(), which can NOT malloc
  // any memory.
  static void init();

  // Shutdown native memory tracking
  static void shutdown();

  // Verify native memory tracking command line option.
  // This check allows JVM to detect if compatible launcher
  // is used.
  // If an incompatible launcher is used, NMT may not be
  // able to start, even it is enabled by command line option.
  // A warning message should be given if it is encountered.
  static bool check_launcher_nmt_support(const char* value);

  // This method checks native memory tracking environment
  // variable value passed by launcher.
  // Launcher only obligated to pass native memory tracking
  // option value, but not obligated to validate the value,
  // and launcher has option to discard native memory tracking
  // option from the command line once it sets up the environment
  // variable, so NMT has to catch the bad value here.
  static bool verify_nmt_option();

  // Transition the tracking level to specified level
  static bool transition_to(NMT_TrackingLevel level);

  static inline void* record_malloc(void* mem_base, size_t size, MEMFLAGS flag,
    const NativeCallStack& stack, NMT_TrackingLevel level) {
    return MallocTracker::record_malloc(mem_base, size, flag, stack, level);
  }

  static inline size_t malloc_header_size(NMT_TrackingLevel level) {
    return MallocTracker::malloc_header_size(level);
  }

  static size_t malloc_header_size(void* memblock) {
    if (tracking_level() != NMT_off) {
      return MallocTracker::get_header_size(memblock);
    }
    return 0;
  }

  // To malloc base address, which is the starting address
  // of malloc tracking header if tracking is enabled.
  // Otherwise, it returns the same address.
  static void* malloc_base(void* memblock);

  // Record malloc free and return malloc base address
  static inline void* record_free(void* memblock) {
    return MallocTracker::record_free(memblock);
  }


  // Record creation of an arena
  static inline void record_new_arena(MEMFLAGS flag) {
    if (tracking_level() < NMT_summary) return;
    MallocTracker::record_new_arena(flag);
  }

  // Record destruction of an arena
  static inline void record_arena_free(MEMFLAGS flag) {
    if (tracking_level() < NMT_summary) return;
    MallocTracker::record_arena_free(flag);
  }

  // Record arena size change. Arena size is the size of all arena
  // chuncks that backing up the arena.
  static inline void record_arena_size_change(int diff, MEMFLAGS flag) {
    if (tracking_level() < NMT_summary) return;
    MallocTracker::record_arena_size_change(diff, flag);
  }

  static inline void record_virtual_memory_reserve(void* addr, size_t size, const NativeCallStack& stack,
    MEMFLAGS flag = mtNone) {
    if (tracking_level() < NMT_summary) return;
    if (addr != NULL) {
      ThreadCritical tc;
      // Recheck to avoid potential racing during NMT shutdown
      if (tracking_level() < NMT_summary) return;
      VirtualMemoryTracker::add_reserved_region((address)addr, size, stack, flag);
    }
  }

  static inline void record_virtual_memory_reserve_and_commit(void* addr, size_t size,
    const NativeCallStack& stack, MEMFLAGS flag = mtNone) {
    if (tracking_level() < NMT_summary) return;
    if (addr != NULL) {
      ThreadCritical tc;
      if (tracking_level() < NMT_summary) return;
      VirtualMemoryTracker::add_reserved_region((address)addr, size, stack, flag);
      VirtualMemoryTracker::add_committed_region((address)addr, size, stack);
    }
  }

  static inline void record_virtual_memory_commit(void* addr, size_t size,
    const NativeCallStack& stack) {
    if (tracking_level() < NMT_summary) return;
    if (addr != NULL) {
      ThreadCritical tc;
      if (tracking_level() < NMT_summary) return;
      VirtualMemoryTracker::add_committed_region((address)addr, size, stack);
    }
  }

  static inline void record_virtual_memory_type(void* addr, MEMFLAGS flag) {
    if (tracking_level() < NMT_summary) return;
    if (addr != NULL) {
      ThreadCritical tc;
      if (tracking_level() < NMT_summary) return;
      VirtualMemoryTracker::set_reserved_region_type((address)addr, flag);
    }
  }

#ifdef _AIX
  // See JDK-8202772 - temporarily disable thread stack tracking on AIX.
  static inline void record_thread_stack(void* addr, size_t size) {}
  static inline void release_thread_stack(void* addr, size_t size) {}
#else
  static inline void record_thread_stack(void* addr, size_t size) {
    if (tracking_level() < NMT_summary) return;
    if (addr != NULL) {
      // uses thread stack malloc slot for book keeping number of threads
      MallocMemorySummary::record_malloc(0, mtThreadStack);
      record_virtual_memory_reserve(addr, size, CALLER_PC, mtThreadStack);
    }
  }

  static inline void release_thread_stack(void* addr, size_t size) {
    if (tracking_level() < NMT_summary) return;
    if (addr != NULL) {
      // uses thread stack malloc slot for book keeping number of threads
      MallocMemorySummary::record_free(0, mtThreadStack);
      ThreadCritical tc;
      if (tracking_level() < NMT_summary) return;
      VirtualMemoryTracker::remove_released_region((address)addr, size);
    }
  }
#endif

  // Query lock is used to synchronize the access to tracking data.
  // So far, it is only used by JCmd query, but it may be used by
  // other tools.
  static inline Mutex* query_lock() {
    assert(NMTQuery_lock != NULL, "not initialized!");
    return NMTQuery_lock;
  }

  // Make a final report or report for hs_err file.
  static void error_report(outputStream* output) {
    if (tracking_level() >= NMT_summary) {
      report(true, output);  // just print summary for error case.
    }
   }

  static void final_report(outputStream* output) {
    NMT_TrackingLevel level = tracking_level();
    if (level >= NMT_summary) {
      report(level == NMT_summary, output);
    }
  }


  // Stored baseline
  static inline MemBaseline& get_baseline() {
    return _baseline;
  }

  static NMT_TrackingLevel cmdline_tracking_level() {
    return _cmdline_tracking_level;
  }

  static void tuning_statistics(outputStream* out);

 private:
  static NMT_TrackingLevel init_tracking_level();
  static void report(bool summary_only, outputStream* output);

 private:
  // Tracking level
  static volatile NMT_TrackingLevel   _tracking_level;
  // If NMT option value passed by launcher through environment
  // variable is valid
  static bool                         _is_nmt_env_valid;
  // command line tracking level
  static NMT_TrackingLevel            _cmdline_tracking_level;
  // Stored baseline
  static MemBaseline      _baseline;
  // Query lock
  static Mutex*           _query_lock;
};

#endif // INCLUDE_NMT

#endif // SHARE_VM_SERVICES_MEM_TRACKER_HPP
