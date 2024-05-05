/*
 * Copyright (c) 2014, 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_NMT_MALLOCSITETABLE_HPP
#define SHARE_NMT_MALLOCSITETABLE_HPP

#include "memory/allocation.hpp"
#include "nmt/allocationSite.hpp"
#include "nmt/mallocTracker.hpp"
#include "nmt/nmtCommon.hpp"
#include "runtime/atomic.hpp"
#include "utilities/macros.hpp"
#include "utilities/nativeCallStack.hpp"

// MallocSite represents a code path that eventually calls
// os::malloc() to allocate memory
class MallocSite : public AllocationSite {
  MemoryCounter _c;
 public:
  MallocSite(const NativeCallStack& stack, MEMFLAGS flags) :
    AllocationSite(stack, flags) {}

  void allocate(size_t size)      { _c.allocate(size);   }
  void deallocate(size_t size)    { _c.deallocate(size); }

  // Memory allocated from this code path
  size_t size()  const { return _c.size(); }
  // Peak memory ever allocated from this code path
  size_t peak_size()  const { return _c.peak_size(); }
  // The number of calls were made
  size_t count() const { return _c.count(); }

  const MemoryCounter* counter() const { return &_c; }
};

// Malloc site hashtable entry
class MallocSiteHashtableEntry : public CHeapObj<mtNMT> {
 private:
  MallocSite                         _malloc_site;
  const unsigned int                 _hash;
  MallocSiteHashtableEntry* volatile _next;

 public:

  MallocSiteHashtableEntry(NativeCallStack stack, MEMFLAGS flags):
    _malloc_site(stack, flags), _hash(stack.calculate_hash()), _next(nullptr) {
    assert(flags != mtNone, "Expect a real memory type");
  }

  inline const MallocSiteHashtableEntry* next() const {
    return _next;
  }

  // Insert an entry atomically.
  // Return true if the entry is inserted successfully.
  // The operation can be failed due to contention from other thread.
  bool atomic_insert(MallocSiteHashtableEntry* entry);

  unsigned int hash() const { return _hash; }

  inline const MallocSite* peek() const { return &_malloc_site; }
  inline MallocSite* data()             { return &_malloc_site; }

  // Allocation/deallocation on this allocation site
  inline void allocate(size_t size)   { _malloc_site.allocate(size);   }
  inline void deallocate(size_t size) { _malloc_site.deallocate(size); }
  // Memory counters
  inline size_t size() const  { return _malloc_site.size();  }
  inline size_t count() const { return _malloc_site.count(); }
};

// The walker walks every entry on MallocSiteTable
class MallocSiteWalker : public StackObj {
 public:
   virtual bool do_malloc_site(const MallocSite* e) { return false; }
};

/*
 * Native memory tracking call site table.
 * The table is only needed when detail tracking is enabled.
 */
class MallocSiteTable : AllStatic {
 private:
  // The number of hash bucket in this hashtable. The number should
  // be tuned if malloc activities changed significantly.
  // The statistics data can be obtained via Jcmd
  // jcmd <pid> VM.native_memory statistics.
  static const int table_size = 4099;

  // Table cannot be wider than a 16bit bucket idx can hold
#define MAX_MALLOCSITE_TABLE_SIZE (USHRT_MAX - 1)
  // Each bucket chain cannot be longer than what a 16 bit pos idx can hold (hopefully way shorter)
#define MAX_BUCKET_LENGTH         (USHRT_MAX - 1)

  STATIC_ASSERT(table_size <= MAX_MALLOCSITE_TABLE_SIZE);

  static uint32_t build_marker(unsigned bucket_idx, unsigned pos_idx) {
    assert(bucket_idx <= MAX_MALLOCSITE_TABLE_SIZE && pos_idx < MAX_BUCKET_LENGTH, "overflow");
    return (uint32_t)bucket_idx << 16 | pos_idx;
  }
  static uint16_t bucket_idx_from_marker(uint32_t marker) { return (uint16_t)(marker >> 16); }
  static uint16_t pos_idx_from_marker(uint32_t marker) { return marker & 0xFFFF; }

 public:

  static bool initialize();

  // Number of hash buckets
  static inline int hash_buckets()      { return (int)table_size; }

  // Access and copy a call stack from this table. Shared lock should be
  // acquired before access the entry.
  static inline bool access_stack(NativeCallStack& stack, uint32_t marker) {
    MallocSite* site = malloc_site(marker);
    if (site != nullptr) {
      stack = *site->call_stack();
      return true;
    }
    return false;
  }

  // Record a new allocation from specified call path.
  // Return true if the allocation is recorded successfully and updates marker
  // to indicate the entry where the allocation information was recorded.
  // Return false only occurs under rare scenarios:
  //  1. out of memory
  //  2. overflow hash bucket
  static inline bool allocation_at(const NativeCallStack& stack, size_t size,
      uint32_t* marker, MEMFLAGS flags) {
    MallocSite* site = lookup_or_add(stack, marker, flags);
    if (site != nullptr) site->allocate(size);
    return site != nullptr;
  }

  // Record memory deallocation. marker indicates where the allocation
  // information was recorded.
  static inline bool deallocation_at(size_t size, uint32_t marker) {
    MallocSite* site = malloc_site(marker);
    if (site != nullptr) {
      site->deallocate(size);
      return true;
    }
    return false;
  }

  // Walk this table.
  static bool walk_malloc_site(MallocSiteWalker* walker);

  static void print_tuning_statistics(outputStream* st);

 private:
  static MallocSiteHashtableEntry* new_entry(const NativeCallStack& key, MEMFLAGS flags);

  static MallocSite* lookup_or_add(const NativeCallStack& key, uint32_t* marker, MEMFLAGS flags);
  static MallocSite* malloc_site(uint32_t marker);
  static bool walk(MallocSiteWalker* walker);

  static inline unsigned int hash_to_index(unsigned int hash) {
    return (hash % table_size);
  }

  static inline const NativeCallStack* hash_entry_allocation_stack() {
    assert(_hash_entry_allocation_stack != nullptr, "Must be set");
    return _hash_entry_allocation_stack;
  }

  static inline const MallocSiteHashtableEntry* hash_entry_allocation_site() {
    assert(_hash_entry_allocation_site != nullptr, "Must be set");
    return _hash_entry_allocation_site;
  }

 private:
  // The callsite hashtable. It has to be a static table,
  // since malloc call can come from C runtime linker.
  static MallocSiteHashtableEntry**       _table;
  static const NativeCallStack*           _hash_entry_allocation_stack;
  static const MallocSiteHashtableEntry*  _hash_entry_allocation_site;
};

#endif // SHARE_NMT_MALLOCSITETABLE_HPP
