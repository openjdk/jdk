/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_SERVICES_MALLOC_SITE_TABLE_HPP
#define SHARE_VM_SERVICES_MALLOC_SITE_TABLE_HPP

#if INCLUDE_NMT

#include "memory/allocation.hpp"
#include "runtime/atomic.hpp"
#include "services/allocationSite.hpp"
#include "services/mallocTracker.hpp"
#include "services/nmtCommon.hpp"

// MallocSite represents a code path that eventually calls
// os::malloc() to allocate memory
class MallocSite : public AllocationSite<MemoryCounter> {
 public:
  MallocSite() :
    AllocationSite<MemoryCounter>(emptyStack) { }

  MallocSite(const NativeCallStack& stack) :
    AllocationSite<MemoryCounter>(stack) { }

  void allocate(size_t size)      { data()->allocate(size);   }
  void deallocate(size_t size)    { data()->deallocate(size); }

  // Memory allocated from this code path
  size_t size()  const { return peek()->size(); }
  // The number of calls were made
  size_t count() const { return peek()->count(); }
};

// Malloc site hashtable entry
class MallocSiteHashtableEntry : public CHeapObj<mtNMT> {
 private:
  MallocSite                _malloc_site;
  MallocSiteHashtableEntry* _next;

 public:
  MallocSiteHashtableEntry() : _next(NULL) { }

  MallocSiteHashtableEntry(NativeCallStack stack):
    _malloc_site(stack), _next(NULL) { }

  inline const MallocSiteHashtableEntry* next() const {
    return _next;
  }

  // Insert an entry atomically.
  // Return true if the entry is inserted successfully.
  // The operation can be failed due to contention from other thread.
  bool atomic_insert(const MallocSiteHashtableEntry* entry) {
    return (Atomic::cmpxchg_ptr((void*)entry, (volatile void*)&_next,
      NULL) == NULL);
  }

  void set_callsite(const MallocSite& site) {
    _malloc_site = site;
  }

  inline const MallocSite* peek() const { return &_malloc_site; }
  inline MallocSite* data()             { return &_malloc_site; }

  inline long hash() const { return _malloc_site.hash(); }
  inline bool equals(const NativeCallStack& stack) const {
    return _malloc_site.equals(stack);
  }
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

  // Currently, (number of buckets / number of entires) ratio is
  // about 1 / 6
  enum {
    table_base_size = 128,   // The base size is calculated from statistics to give
                             // table ratio around 1:6
    table_size = (table_base_size * NMT_TrackingStackDepth - 1)
  };


  // This is a very special lock, that allows multiple shared accesses (sharedLock), but
  // once exclusive access (exclusiveLock) is requested, all shared accesses are
  // rejected forever.
  class AccessLock : public StackObj {
    enum LockState {
      NoLock,
      SharedLock,
      ExclusiveLock
    };

   private:
    // A very large negative number. The only possibility to "overflow"
    // this number is when there are more than -min_jint threads in
    // this process, which is not going to happen in foreseeable future.
    const static int _MAGIC_ = min_jint;

    LockState      _lock_state;
    volatile int*  _lock;
   public:
    AccessLock(volatile int* lock) :
      _lock(lock), _lock_state(NoLock) {
    }

    ~AccessLock() {
      if (_lock_state == SharedLock) {
        Atomic::dec((volatile jint*)_lock);
      }
    }
    // Acquire shared lock.
    // Return true if shared access is granted.
    inline bool sharedLock() {
      jint res = Atomic::add(1, _lock);
      if (res < 0) {
        Atomic::add(-1, _lock);
        return false;
      }
      _lock_state = SharedLock;
      return true;
    }
    // Acquire exclusive lock
    void exclusiveLock();
 };

 public:
  static bool initialize();
  static void shutdown();

  NOT_PRODUCT(static int access_peak_count() { return _peak_count; })

  // Number of hash buckets
  static inline int hash_buckets()      { return (int)table_size; }

  // Access and copy a call stack from this table. Shared lock should be
  // acquired before access the entry.
  static inline bool access_stack(NativeCallStack& stack, size_t bucket_idx,
    size_t pos_idx) {
    AccessLock locker(&_access_count);
    if (locker.sharedLock()) {
      NOT_PRODUCT(_peak_count = MAX2(_peak_count, _access_count);)
      MallocSite* site = malloc_site(bucket_idx, pos_idx);
      if (site != NULL) {
        stack = *site->call_stack();
        return true;
      }
    }
    return false;
  }

  // Record a new allocation from specified call path.
  // Return true if the allocation is recorded successfully, bucket_idx
  // and pos_idx are also updated to indicate the entry where the allocation
  // information was recorded.
  // Return false only occurs under rare scenarios:
  //  1. out of memory
  //  2. overflow hash bucket
  static inline bool allocation_at(const NativeCallStack& stack, size_t size,
    size_t* bucket_idx, size_t* pos_idx) {
    AccessLock locker(&_access_count);
    if (locker.sharedLock()) {
      NOT_PRODUCT(_peak_count = MAX2(_peak_count, _access_count);)
      MallocSite* site = lookup_or_add(stack, bucket_idx, pos_idx);
      if (site != NULL) site->allocate(size);
      return site != NULL;
    }
    return false;
  }

  // Record memory deallocation. bucket_idx and pos_idx indicate where the allocation
  // information was recorded.
  static inline bool deallocation_at(size_t size, size_t bucket_idx, size_t pos_idx) {
    AccessLock locker(&_access_count);
    if (locker.sharedLock()) {
      NOT_PRODUCT(_peak_count = MAX2(_peak_count, _access_count);)
      MallocSite* site = malloc_site(bucket_idx, pos_idx);
      if (site != NULL) {
        site->deallocate(size);
        return true;
      }
    }
    return false;
  }

  // Walk this table.
  static bool walk_malloc_site(MallocSiteWalker* walker);

 private:
  static MallocSiteHashtableEntry* new_entry(const NativeCallStack& key);
  static void reset();

  // Delete a bucket linked list
  static void delete_linked_list(MallocSiteHashtableEntry* head);

  static MallocSite* lookup_or_add(const NativeCallStack& key, size_t* bucket_idx, size_t* pos_idx);
  static MallocSite* malloc_site(size_t bucket_idx, size_t pos_idx);
  static bool walk(MallocSiteWalker* walker);

  static inline int hash_to_index(int  hash) {
    hash = (hash > 0) ? hash : (-hash);
    return (hash % table_size);
  }

  static inline const NativeCallStack* hash_entry_allocation_stack() {
    return (NativeCallStack*)_hash_entry_allocation_stack;
  }

 private:
  // Counter for counting concurrent access
  static volatile int                _access_count;

  // The callsite hashtable. It has to be a static table,
  // since malloc call can come from C runtime linker.
  static MallocSiteHashtableEntry*   _table[table_size];


  // Reserve enough memory for placing the objects

  // The memory for hashtable entry allocation stack object
  static size_t _hash_entry_allocation_stack[CALC_OBJ_SIZE_IN_TYPE(NativeCallStack, size_t)];
  // The memory for hashtable entry allocation callsite object
  static size_t _hash_entry_allocation_site[CALC_OBJ_SIZE_IN_TYPE(MallocSiteHashtableEntry, size_t)];
  NOT_PRODUCT(static int     _peak_count;)
};

#endif // INCLUDE_NMT
#endif // SHARE_VM_SERVICES_MALLOC_SITE_TABLE_HPP
