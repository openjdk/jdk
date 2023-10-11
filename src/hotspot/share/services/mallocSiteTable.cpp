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
#include "precompiled.hpp"


#include "memory/allocation.inline.hpp"
#include "runtime/atomic.hpp"
#include "services/mallocSiteTable.hpp"

// Malloc site hashtable buckets
MallocSiteHashtableEntry**  MallocSiteTable::_table = nullptr;
const NativeCallStack* MallocSiteTable::_hash_entry_allocation_stack = nullptr;
const MallocSiteHashtableEntry* MallocSiteTable::_hash_entry_allocation_site = nullptr;

/*
 * Initialize malloc site table.
 * Hashtable entry is malloc'd, so it can cause infinite recursion.
 * To avoid above problem, we pre-initialize a hash entry for
 * this allocation site.
 * The method is called during C runtime static variable initialization
 * time, it is in single-threaded mode from JVM perspective.
 */
bool MallocSiteTable::initialize() {

  ALLOW_C_FUNCTION(::calloc,
                   _table = (MallocSiteHashtableEntry**)::calloc(table_size, sizeof(MallocSiteHashtableEntry*));)
  if (_table == nullptr) {
    return false;
  }

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

  static const NativeCallStack stack(pc, MIN2(((int)(sizeof(pc) / sizeof(address))), ((int)NMT_TrackingStackDepth)));
  static const MallocSiteHashtableEntry entry(stack, mtNMT);

  assert(_hash_entry_allocation_stack == nullptr &&
         _hash_entry_allocation_site == nullptr,
         "Already initialized");

  _hash_entry_allocation_stack = &stack;
  _hash_entry_allocation_site = &entry;

  // Add the allocation site to hashtable.
  int index = hash_to_index(entry.hash());
  _table[index] = const_cast<MallocSiteHashtableEntry*>(&entry);

  return true;
}

// Walks entries in the hashtable.
// It stops walk if the walker returns false.
bool MallocSiteTable::walk(MallocSiteWalker* walker) {
  MallocSiteHashtableEntry* head;
  for (int index = 0; index < table_size; index ++) {
    head = _table[index];
    while (head != nullptr) {
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
 *  This method should not return nullptr under normal circumstance.
 *  If nullptr is returned, it indicates:
 *    1. Out of memory, it cannot allocate new hash entry.
 *    2. Overflow hash bucket.
 *  Under any of above circumstances, caller should handle the situation.
 */
MallocSite* MallocSiteTable::lookup_or_add(const NativeCallStack& key, uint32_t* marker, MEMFLAGS flags) {
  assert(flags != mtNone, "Should have a real memory type");
  const unsigned int hash = key.calculate_hash();
  const unsigned int index = hash_to_index(hash);
  *marker = 0;

  // First entry for this hash bucket
  if (_table[index] == nullptr) {
    MallocSiteHashtableEntry* entry = new_entry(key, flags);
    // OOM check
    if (entry == nullptr) return nullptr;

    // swap in the head
    if (Atomic::replace_if_null(&_table[index], entry)) {
      *marker = build_marker(index, 0);
      return entry->data();
    }

    delete entry;
  }

  unsigned pos_idx = 0;
  MallocSiteHashtableEntry* head = _table[index];
  while (head != nullptr && pos_idx < MAX_BUCKET_LENGTH) {
    if (head->hash() == hash) {
      MallocSite* site = head->data();
      if (site->flag() == flags && site->equals(key)) {
        *marker = build_marker(index, pos_idx);
        return head->data();
      }
    }

    if (head->next() == nullptr && pos_idx < (MAX_BUCKET_LENGTH - 1)) {
      MallocSiteHashtableEntry* entry = new_entry(key, flags);
      // OOM check
      if (entry == nullptr) return nullptr;
      if (head->atomic_insert(entry)) {
        pos_idx ++;
        *marker = build_marker(index, pos_idx);
        return entry->data();
      }
      // contended, other thread won
      delete entry;
    }
    head = (MallocSiteHashtableEntry*)head->next();
    pos_idx ++;
  }
  return nullptr;
}

// Access malloc site
MallocSite* MallocSiteTable::malloc_site(uint32_t marker) {
  uint16_t bucket_idx = bucket_idx_from_marker(marker);
  assert(bucket_idx < table_size, "Invalid bucket index");
  const uint16_t pos_idx = pos_idx_from_marker(marker);
  MallocSiteHashtableEntry* head = _table[bucket_idx];
  for (size_t index = 0;
       index < pos_idx && head != nullptr;
       index++, head = (MallocSiteHashtableEntry*)head->next()) {}
  assert(head != nullptr, "Invalid position index");
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

bool MallocSiteTable::walk_malloc_site(MallocSiteWalker* walker) {
  assert(walker != nullptr, "NuLL walker");
  return walk(walker);
}

static int qsort_helper(const void* a, const void* b) {
  return *((uint16_t*)a) - *((uint16_t*)b);
}

void MallocSiteTable::print_tuning_statistics(outputStream* st) {
  // Total number of allocation sites, include empty sites
  int total_entries = 0;
  // Number of allocation sites that have all memory freed
  int empty_entries = 0;
  // Number of captured call stack distribution
  int stack_depth_distribution[NMT_TrackingStackDepth + 1] = { 0 };
  // Chain lengths
  uint16_t lengths[table_size] = { 0 };
  // Unused buckets
  int unused_buckets = 0;

  for (int i = 0; i < table_size; i ++) {
    int this_chain_length = 0;
    const MallocSiteHashtableEntry* head = _table[i];
    if (head == nullptr) {
      unused_buckets ++;
    }
    while (head != nullptr) {
      total_entries ++;
      this_chain_length ++;
      if (head->size() == 0) {
        empty_entries ++;
      }
      const int callstack_depth = head->peek()->call_stack()->frames();
      assert(callstack_depth >= 0 && callstack_depth <= NMT_TrackingStackDepth,
             "Sanity (%d)", callstack_depth);
      stack_depth_distribution[callstack_depth] ++;
      head = head->next();
    }
    lengths[i] = (uint16_t)MIN2(this_chain_length, USHRT_MAX);
  }

  st->print_cr("Malloc allocation site table:");
  st->print_cr("\tTotal entries: %d", total_entries);
  st->print_cr("\tEmpty entries (no outstanding mallocs): %d (%2.2f%%)",
                  empty_entries, ((float)empty_entries * 100) / (float)total_entries);
  st->cr();

  qsort(lengths, table_size, sizeof(uint16_t), qsort_helper);

  st->print_cr("Bucket chain length distribution:");
  st->print_cr("unused:  %d", unused_buckets);
  st->print_cr("longest: %d", lengths[table_size - 1]);
  st->print_cr("median:  %d", lengths[table_size / 2]);
  st->cr();

  st->print_cr("Call stack depth distribution:");
  for (int i = 0; i <= NMT_TrackingStackDepth; i ++) {
    st->print_cr("\t%d: %d", i, stack_depth_distribution[i]);
  }
  st->cr();
}

bool MallocSiteHashtableEntry::atomic_insert(MallocSiteHashtableEntry* entry) {
  return Atomic::replace_if_null(&_next, entry);
}
