/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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
#include "precompiled.hpp"


#include "memory/allocation.inline.hpp"
#include "runtime/atomic.hpp"
#include "services/mallocSiteTable.hpp"

/*
 * Early os::malloc() calls come from initializations of static variables, long before entering any
 * VM code. Upon the arrival of the first os::malloc() call, malloc site hashtable has to be
 * initialized, along with the allocation site for the hashtable entries.
 * To ensure that malloc site hashtable can be initialized without triggering any additional os::malloc()
 * call, the hashtable bucket array and hashtable entry allocation site have to be static.
 * It is not a problem for hashtable bucket, since it is an array of pointer type, C runtime just
 * allocates a block memory and zero the memory for it.
 * But for hashtable entry allocation site object, things get tricky. C runtime not only allocates
 * memory for it, but also calls its constructor at some later time. If we initialize the allocation site
 * at the first os::malloc() call, the object will be reinitialized when its constructor is called
 * by C runtime.
 * To workaround above issue, we declare a static size_t array with the size of the CallsiteHashtableEntry,
 * the memory is used to instantiate CallsiteHashtableEntry for the hashtable entry allocation site.
 * Given it is a primitive type array, C runtime will do nothing other than assign the memory block for the variable,
 * which is exactly what we want.
 * The same trick is also applied to create NativeCallStack object for CallsiteHashtableEntry memory allocation.
 *
 * Note: C++ object usually aligns to particular alignment, depends on compiler implementation, we declare
 * the memory as size_t arrays, to ensure the memory is aligned to native machine word alignment.
 */

// Reserve enough memory for NativeCallStack and MallocSiteHashtableEntry objects
size_t MallocSiteTable::_hash_entry_allocation_stack[CALC_OBJ_SIZE_IN_TYPE(NativeCallStack, size_t)];
size_t MallocSiteTable::_hash_entry_allocation_site[CALC_OBJ_SIZE_IN_TYPE(MallocSiteHashtableEntry, size_t)];

// Malloc site hashtable buckets
MallocSiteHashtableEntry*  MallocSiteTable::_table[MallocSiteTable::table_size];

// concurrent access counter
volatile int MallocSiteTable::_access_count = 0;

// Tracking hashtable contention
NOT_PRODUCT(int MallocSiteTable::_peak_count = 0;)


/*
 * Initialize malloc site table.
 * Hashtable entry is malloc'd, so it can cause infinite recursion.
 * To avoid above problem, we pre-initialize a hash entry for
 * this allocation site.
 * The method is called during C runtime static variable initialization
 * time, it is in single-threaded mode from JVM perspective.
 */
bool MallocSiteTable::initialize() {
  assert(sizeof(_hash_entry_allocation_stack) >= sizeof(NativeCallStack), "Sanity Check");
  assert(sizeof(_hash_entry_allocation_site) >= sizeof(MallocSiteHashtableEntry),
    "Sanity Check");
  assert((size_t)table_size <= MAX_MALLOCSITE_TABLE_SIZE, "Hashtable overflow");

  // Fake the call stack for hashtable entry allocation
  assert(NMT_TrackingStackDepth > 1, "At least one tracking stack");

  // Create pseudo call stack for hashtable entry allocation
  address pc[3];
  if (NMT_TrackingStackDepth >= 3) {
    uintx *fp = (uintx*)MallocSiteTable::allocation_at;
    // On ppc64, 'fp' is a pointer to a function descriptor which is a struct  of
    // three native pointers where the first pointer is the real function address.
    // See: http://refspecs.linuxfoundation.org/ELF/ppc64/PPC-elf64abi-1.9.html#FUNC-DES
    pc[2] = (address)(fp PPC64_ONLY(BIG_ENDIAN_ONLY([0])));
  }
  if (NMT_TrackingStackDepth >= 2) {
    uintx *fp = (uintx*)MallocSiteTable::lookup_or_add;
    pc[1] = (address)(fp PPC64_ONLY(BIG_ENDIAN_ONLY([0])));
  }
  uintx *fp = (uintx*)MallocSiteTable::new_entry;
  pc[0] = (address)(fp PPC64_ONLY(BIG_ENDIAN_ONLY([0])));

  // Instantiate NativeCallStack object, have to use placement new operator. (see comments above)
  NativeCallStack* stack = ::new ((void*)_hash_entry_allocation_stack)
    NativeCallStack(pc, MIN2(((int)(sizeof(pc) / sizeof(address))), ((int)NMT_TrackingStackDepth)));

  // Instantiate hash entry for hashtable entry allocation callsite
  MallocSiteHashtableEntry* entry = ::new ((void*)_hash_entry_allocation_site)
    MallocSiteHashtableEntry(*stack, mtNMT);

  // Add the allocation site to hashtable.
  int index = hash_to_index(stack->hash());
  _table[index] = entry;

  return true;
}

// Walks entries in the hashtable.
// It stops walk if the walker returns false.
bool MallocSiteTable::walk(MallocSiteWalker* walker) {
  MallocSiteHashtableEntry* head;
  for (int index = 0; index < table_size; index ++) {
    head = _table[index];
    while (head != NULL) {
      if (!walker->do_malloc_site(head->peek())) {
        return false;
      }
      head = (MallocSiteHashtableEntry*)head->next();
    }
  }
  return true;
}

/*
 *  The hashtable does not have deletion policy on individual entry,
 *  and each linked list node is inserted via compare-and-swap,
 *  so each linked list is stable, the contention only happens
 *  at the end of linked list.
 *  This method should not return NULL under normal circumstance.
 *  If NULL is returned, it indicates:
 *    1. Out of memory, it cannot allocate new hash entry.
 *    2. Overflow hash bucket.
 *  Under any of above circumstances, caller should handle the situation.
 */
MallocSite* MallocSiteTable::lookup_or_add(const NativeCallStack& key, size_t* bucket_idx,
  size_t* pos_idx, MEMFLAGS flags) {
  assert(flags != mtNone, "Should have a real memory type");
  unsigned int index = hash_to_index(key.hash());
  *bucket_idx = (size_t)index;
  *pos_idx = 0;

  // First entry for this hash bucket
  if (_table[index] == NULL) {
    MallocSiteHashtableEntry* entry = new_entry(key, flags);
    // OOM check
    if (entry == NULL) return NULL;

    // swap in the head
    if (Atomic::replace_if_null(entry, &_table[index])) {
      return entry->data();
    }

    delete entry;
  }

  MallocSiteHashtableEntry* head = _table[index];
  while (head != NULL && (*pos_idx) <= MAX_BUCKET_LENGTH) {
    MallocSite* site = head->data();
    if (site->flags() == flags && site->equals(key)) {
      return head->data();
    }

    if (head->next() == NULL && (*pos_idx) < MAX_BUCKET_LENGTH) {
      MallocSiteHashtableEntry* entry = new_entry(key, flags);
      // OOM check
      if (entry == NULL) return NULL;
      if (head->atomic_insert(entry)) {
        (*pos_idx) ++;
        return entry->data();
      }
      // contended, other thread won
      delete entry;
    }
    head = (MallocSiteHashtableEntry*)head->next();
    (*pos_idx) ++;
  }
  return NULL;
}

// Access malloc site
MallocSite* MallocSiteTable::malloc_site(size_t bucket_idx, size_t pos_idx) {
  assert(bucket_idx < table_size, "Invalid bucket index");
  MallocSiteHashtableEntry* head = _table[bucket_idx];
  for (size_t index = 0;
       index < pos_idx && head != NULL;
       index++, head = (MallocSiteHashtableEntry*)head->next()) {}
  assert(head != NULL, "Invalid position index");
  return head->data();
}

// Allocates MallocSiteHashtableEntry object. Special call stack
// (pre-installed allocation site) has to be used to avoid infinite
// recursion.
MallocSiteHashtableEntry* MallocSiteTable::new_entry(const NativeCallStack& key, MEMFLAGS flags) {
  void* p = AllocateHeap(sizeof(MallocSiteHashtableEntry), mtNMT,
    *hash_entry_allocation_stack(), AllocFailStrategy::RETURN_NULL);
  return ::new (p) MallocSiteHashtableEntry(key, flags);
}

void MallocSiteTable::reset() {
  for (int index = 0; index < table_size; index ++) {
    MallocSiteHashtableEntry* head = _table[index];
    _table[index] = NULL;
    delete_linked_list(head);
  }
}

void MallocSiteTable::delete_linked_list(MallocSiteHashtableEntry* head) {
  MallocSiteHashtableEntry* p;
  while (head != NULL) {
    p = head;
    head = (MallocSiteHashtableEntry*)head->next();
    if (p != (MallocSiteHashtableEntry*)_hash_entry_allocation_site) {
      delete p;
    }
  }
}

void MallocSiteTable::shutdown() {
  AccessLock locker(&_access_count);
  locker.exclusiveLock();
  reset();
}

bool MallocSiteTable::walk_malloc_site(MallocSiteWalker* walker) {
  assert(walker != NULL, "NuLL walker");
  AccessLock locker(&_access_count);
  if (locker.sharedLock()) {
    NOT_PRODUCT(_peak_count = MAX2(_peak_count, _access_count);)
    return walk(walker);
  }
  return false;
}


void MallocSiteTable::AccessLock::exclusiveLock() {
  int target;
  int val;

  assert(_lock_state != ExclusiveLock, "Can only call once");
  assert(*_lock >= 0, "Can not content exclusive lock");

  // make counter negative to block out shared locks
  do {
    val = *_lock;
    target = _MAGIC_ + *_lock;
  } while (Atomic::cmpxchg(target, _lock, val) != val);

  // wait for all readers to exit
  while (*_lock != _MAGIC_) {
#ifdef _WINDOWS
    os::naked_short_sleep(1);
#else
    os::naked_yield();
#endif
  }
  _lock_state = ExclusiveLock;
}

bool MallocSiteHashtableEntry::atomic_insert(MallocSiteHashtableEntry* entry) {
  return Atomic::replace_if_null(entry, &_next);
}
