/*
 * Copyright (c) 2022, 2023 SAP SE. All rights reserved.
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_NMT_NMT_PREINIT_HPP
#define SHARE_NMT_NMT_PREINIT_HPP

#include "memory/allStatic.hpp"
#include "nmt/memTracker.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"
#ifdef ASSERT
#include "runtime/atomic.hpp"
#endif

class outputStream;

// NMTPreInit is the solution to a specific problem:
//
// NMT tracks C-heap allocations (os::malloc and friends). Those can happen at all VM life stages,
// including very early during the dynamic C++ initialization of the hotspot, and in CreateJavaVM
// before argument parsing.
//
// However, before the VM parses NMT arguments, we do not know whether NMT is enabled or not. Can we
// just ignore early allocations? If the only problem were statistical correctness, sure: footprint-wise
// they are not really relevant.
//
// But there is one big problem: NMT uses malloc headers to keep meta information of malloced blocks.
// We have to consider those in os::free() when calling free(3).
//
// So:
// 1) NMT off:
//   a) pre-NMT-init allocations have no header
//   b) post-NMT-init allocations have no header
// 2) NMT on:
//   a) pre-NMT-init allocations have no header
//   b) post-NMT-init allocations do have a header
//
// The problem is that inside os::free(p), we only get an opaque void* p; we do not know if p had been
// allocated in (a) or (b) phase. Therefore, we do not know if p is preceded by an NMT header which we
// would need to subtract from the pointer before calling free(3). There is no safe way to "guess" here
// without risking C-heap corruption.
//
// To solve this, we need a way to quickly determine, at os::free(p), whether p was a pre-NMT-init
// allocation. There are several ways to do this, see discussion under JDK-8256844.
//
// One of the easiest and most elegant ways is to store early allocation pointers in a lookup table.
// This is what NMTPreInit does.
//
//////////////////////////////////////////////////////////////////////////
//
// VM initialization wrt NMT:
//
//---------------------------------------------------------------
//-> launcher dlopen's libjvm                           ^
//   -> dynamic C++ initialization                      |
//           of libjvm                                  |
//                                                      |
//-> launcher starts new thread (maybe)          NMT pre-init phase : store allocated pointers in lookup table
//                                                      |
//-> launcher invokes CreateJavaVM                      |
//   -> VM initialization before arg parsing            |
//   -> VM argument parsing                             v
//   -> NMT initialization  -------------------------------------
//                                                      ^
//   ...                                                |
//   -> VM life...                               NMT post-init phase : lookup table is read-only; use it in os::free() and os::realloc().
//   ...                                                |
//                                                      v
//----------------------------------------------------------------
//
//////////////////////////////////////////////////////////////////////////
//
// Notes:
// - The VM will malloc() and realloc() several thousand times before NMT initialization.
//   Starting with a lot of arguments increases this number since argument parsing strdups
//   around a lot.
// - However, *surviving* allocations (allocations not freed immediately) are much rarer:
//   typically only about 300-500. Again, mainly depending on the number of VM args.
// - There are a few cases of pre-to-post-init reallocs where pre-init allocations get
//   reallocated after NMT initialization. Those we need to handle with special care (see
//   NMTPreInit::handle_realloc()). Because of them we need to store allocation size with
//   every pre-init allocation.

// For the lookup table, design considerations are:
//   - lookup speed is paramount since lookup is done for every os::free() call.
//   - insert/delete speed only matters for VM startup - after NMT initialization the lookup
//     table is readonly
//   - memory consumption of the lookup table matters since we always pay for it, NMT on or off.
//   - Obviously, nothing here can use *os::malloc*. Any dynamic allocations - if they cannot
//     be avoided - should use raw malloc(3).
//
// We use a basic open hashmap, dimensioned generously - hash collisions should be very rare.
//   The table is customized for holding malloced pointers. One main point of this map is that we do
//   not allocate memory for the nodes themselves. Instead we piggy-back on the user allocation:
//   the hashmap entry structure precedes, as a header, the malloced block. That way we avoid extra
//   allocations just to hold the map nodes. This keeps runtime/memory overhead as small as possible.

struct NMTPreInitAllocation {
  NMTPreInitAllocation* next;
  const size_t size; // (inner) payload size without header
  void* const payload;

  NMTPreInitAllocation(size_t s, void* p) : next(nullptr), size(s), payload(p) {}

  // These functions do raw-malloc/realloc/free a C-heap block of given payload size,
  //  preceded with a NMTPreInitAllocation header.
  static NMTPreInitAllocation* do_alloc(size_t payload_size);
  static NMTPreInitAllocation* do_reallocate(NMTPreInitAllocation* a, size_t new_payload_size);
  static void do_free(NMTPreInitAllocation* a);

  void* operator new(size_t l);
  void  operator delete(void* p);
};

class NMTPreInitAllocationTable {

  // Table_size: keep table size a prime and the hash function simple; this
  //  seems to give a good distribution for malloced pointers on all our libc variants.
  // 8000ish is really plenty: normal VM runs have ~500 pre-init allocations to hold,
  //  VMs with insanely long command lines maybe ~700-1000. Which gives us an expected
  //  load factor of ~.1. Hash collisions should be very rare.
  // ~8000 entries cost us ~64K for this table (64-bit), which is acceptable.
  // We chose 8191, as this is a Mersenne prime (2^x - 1), which for a random
  //  polynomial modulo p = (2^x - 1) is uniformily distributed in [p], so each
  //  bit has the same distribution.
  static const int table_size = 8191; // i.e. 8191==(2^13 - 1);

  NMTPreInitAllocation* _entries[table_size];

  typedef int index_t;
  const index_t invalid_index = -1;

  static uint64_t calculate_hash(const void* p) {
    // Keep hash function simple, the modulo
    // operation in index function will do the "heavy lifting".
    return (uint64_t)(p);
  }

  static index_t index_for_key(const void* p) {
    const uint64_t hash = calculate_hash(p);
    // "table_size" is a Mersenne prime, so "modulo" is all we need here.
    return checked_cast<index_t>(hash % table_size);
  }

  const NMTPreInitAllocation* const * find_entry(const void* p) const {
    return const_cast<NMTPreInitAllocationTable*>(this)->find_entry(p);
  }

  NMTPreInitAllocation** find_entry(const void* p) {
    const unsigned index = index_for_key(p);
    NMTPreInitAllocation** aa = (&(_entries[index]));
    while ((*aa) != nullptr && (*aa)->payload != p) {
      aa = &((*aa)->next);
    }
    assert((*aa) == nullptr || p == (*aa)->payload,
           "retrieve mismatch " PTR_FORMAT " vs " PTR_FORMAT ".",
           p2i(p), p2i((*aa)->payload));
    return aa;
  }

public:

  NMTPreInitAllocationTable();
  ~NMTPreInitAllocationTable();

  // Adds an entry to the table
  void add(NMTPreInitAllocation* a) {
    void* payload = a->payload;
    const unsigned index = index_for_key(payload);
    assert(a->next == nullptr, "entry already in table?");
    a->next = _entries[index]; // add to front
    _entries[index] = a;        //   of list
    assert(find(payload) == a, "add: reverse lookup error?");
  }

  // Find - but does not remove - an entry in this map.
  // Returns null if not found.
  const NMTPreInitAllocation* find(const void* p) const {
    return *(find_entry(p));
  }

  // Find and removes an entry from the table. Asserts if not found.
  NMTPreInitAllocation* find_and_remove(void* p) {
    NMTPreInitAllocation** aa = find_entry(p);
    assert((*aa) != nullptr, "Entry not found: " PTR_FORMAT, p2i(p));
    NMTPreInitAllocation* a = (*aa);
    (*aa) = (*aa)->next;         // remove from its list
    DEBUG_ONLY(a->next = nullptr;)  // mark as removed
    return a;
  }

  void print_state(outputStream* st) const;
  DEBUG_ONLY(void print_map(outputStream* st) const;)
  DEBUG_ONLY(void verify() const;)

  void* operator new(size_t l);
  void  operator delete(void* p);
};

// NMTPreInit is the outside interface to all of NMT preinit handling.
class NMTPreInit : public AllStatic {

  static NMTPreInitAllocationTable* _table;

  // Some statistics
  static unsigned _num_mallocs_pre;           // Number of pre-init mallocs
  static unsigned _num_reallocs_pre;          // Number of pre-init reallocs
  static unsigned _num_frees_pre;             // Number of pre-init frees

  static void create_table();
  static void delete_table();

  static void add_to_map(NMTPreInitAllocation* a) {
    assert(!MemTracker::is_initialized(), "lookup map cannot be modified after NMT initialization");
    // Only on add, we create the table on demand. Only needed on add, since everything should start
    // with a call to os::malloc().
    if (_table == nullptr) {
      create_table();
    }
    return _table->add(a);
  }

  static const NMTPreInitAllocation* find_in_map(void* p) {
    assert(_table != nullptr, "stray allocation?");
    return _table->find(p);
  }

  static NMTPreInitAllocation* find_and_remove_in_map(void* p) {
    assert(!MemTracker::is_initialized(), "lookup map cannot be modified after NMT initialization");
    assert(_table != nullptr, "stray allocation?");
    return _table->find_and_remove(p);
  }

  // Just a wrapper for os::malloc to avoid including os.hpp here.
  static void* do_os_malloc(size_t size, MEMFLAGS memflags);

public:

  // Switches from NMT pre-init state to NMT post-init state;
  //  in post-init, no modifications to the lookup table are possible.
  static void pre_to_post(bool nmt_off);

  // Called from os::malloc.
  // Returns true if allocation was handled here; in that case,
  // *rc contains the return address.
  static bool handle_malloc(void** rc, size_t size) {
    size = MAX2((size_t)1, size);         // malloc(0)
    if (!MemTracker::is_initialized()) {
      // pre-NMT-init:
      // Allocate entry and add address to lookup table
      NMTPreInitAllocation* a = NMTPreInitAllocation::do_alloc(size);
      add_to_map(a);
      (*rc) = a->payload;
      _num_mallocs_pre++;
      return true;
    }
    return false;
  }

  // Called from os::realloc.
  // Returns true if reallocation was handled here; in that case,
  // *rc contains the return address.
  static bool handle_realloc(void** rc, void* old_p, size_t new_size, MEMFLAGS memflags) {
    if (old_p == nullptr) {                  // realloc(null, n)
      return handle_malloc(rc, new_size);
    }
    new_size = MAX2((size_t)1, new_size); // realloc(.., 0)
    switch (MemTracker::tracking_level()) {
      case NMT_unknown: {
        // pre-NMT-init:
        // - the address must already be in the lookup table
        // - find the old entry, remove from table, reallocate, add to table
        NMTPreInitAllocation* a = find_and_remove_in_map(old_p);
        a = NMTPreInitAllocation::do_reallocate(a, new_size);
        add_to_map(a);
        (*rc) = a->payload;
        _num_reallocs_pre++;
        return true;
      }
      break;
      case NMT_off: {
        // post-NMT-init, NMT *disabled*:
        // Neither pre- nor post-init-allocation use malloc headers, therefore we can just
        // relegate the realloc to os::realloc.
        return false;
      }
      break;
      default: {
        // post-NMT-init, NMT *enabled*:
        // Pre-init allocation does not use malloc header, but from here on we need malloc headers.
        // Therefore, the new block must be allocated with os::malloc.
        // We do this by:
        // - look up (but don't remove! lu table is read-only here.) the old entry
        // - allocate new memory via os::malloc()
        // - manually copy the old content over
        // - return the new memory
        // - The lu table is readonly, so we keep the old address in the table. And we leave
        //   the old block allocated too, to prevent the libc from returning the same address
        //   and confusing us.
        const NMTPreInitAllocation* a = find_in_map(old_p);
        if (a != nullptr) { // this was originally a pre-init allocation
          void* p_new = do_os_malloc(new_size, memflags);
          ::memcpy(p_new, a->payload, MIN2(a->size, new_size));
          (*rc) = p_new;
          return true;
        }
      }
    }
    return false;
  }

  // Called from os::free.
  // Returns true if free was handled here.
  static bool handle_free(void* p) {
    if (p == nullptr) { // free(null)
      return true;
    }
    switch (MemTracker::tracking_level()) {
      case NMT_unknown: {
        // pre-NMT-init:
        // - the allocation must be in the hash map, since all allocations went through
        //   NMTPreInit::handle_malloc()
        // - find the old entry, unhang from map, free it
        NMTPreInitAllocation* a = find_and_remove_in_map(p);
        NMTPreInitAllocation::do_free(a);
        _num_frees_pre++;
        return true;
      }
      break;
      case NMT_off: {
        // post-NMT-init, NMT *disabled*:
        // Neither pre- nor post-init-allocation use malloc headers, therefore we can just
        // relegate the realloc to os::realloc.
        return false;
      }
      break;
      default: {
        // post-NMT-init, NMT *enabled*:
        // - look up (but don't remove! lu table is read-only here.) the entry
        // - if found, we do nothing: the lu table is readonly, so we keep the old address
        //   in the table. We leave the block allocated to prevent the libc from returning
        //   the same address and confusing us.
        // - if not found, we let regular os::free() handle this pointer
        if (find_in_map(p) != nullptr) {
          return true;
        }
      }
    }
    return false;
  }

  static void print_state(outputStream* st);
  static void print_map(outputStream* st);
  DEBUG_ONLY(static void verify();)
};

#endif // SHARE_NMT_NMT_PREINIT_HPP

