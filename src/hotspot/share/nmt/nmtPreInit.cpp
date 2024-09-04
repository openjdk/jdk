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

#include "precompiled.hpp"
#include "nmt/nmtPreInit.hpp"
#include "runtime/os.hpp"
#include "utilities/align.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/ostream.hpp"

// Obviously we cannot use os::malloc for any dynamic allocation during pre-NMT-init, so we must use
// raw malloc; to make this very clear, wrap them.
static void* raw_malloc(size_t s)               { ALLOW_C_FUNCTION(::malloc, return ::malloc(s);) }
static void* raw_realloc(void* old, size_t s)   { ALLOW_C_FUNCTION(::realloc, return ::realloc(old, s);) }
static void  raw_free(void* p)                  { ALLOW_C_FUNCTION(::free, ::free(p);) }

// To keep matters simple we just raise a fatal error on OOM. Since preinit allocation
// is just used for pre-VM-initialization mallocs, none of which are optional, we don't
// need a finer grained error handling.

static void* raw_checked_malloc(size_t s) {
  void* p = raw_malloc(s);
  if (p == nullptr) {
    vm_exit_out_of_memory(s, OOM_MALLOC_ERROR, "VM early initialization phase");
  }
  return p;
}

static void* raw_checked_realloc(void* old, size_t s) {
  void* p = raw_realloc(old, s);
  if (p == nullptr) {
    vm_exit_out_of_memory(s, OOM_MALLOC_ERROR, "VM early initialization phase");
  }
  return p;
}

// --------- NMTPreInitAllocation --------------

void* NMTPreInitAllocation::operator new(size_t count) {
  return raw_checked_malloc(count);
}

void NMTPreInitAllocation::operator delete(void* p) {
  raw_free(p);
}

NMTPreInitAllocation* NMTPreInitAllocation::do_alloc(size_t payload_size) {
  void* payload = raw_checked_malloc(payload_size);
  NMTPreInitAllocation* a = new NMTPreInitAllocation(payload_size, payload);
  return a;
}

NMTPreInitAllocation* NMTPreInitAllocation::do_reallocate(NMTPreInitAllocation* a, size_t new_payload_size) {
  assert(a->next == nullptr, "unhang from map first");
  void* new_payload = raw_checked_realloc(a->payload, new_payload_size);
  NMTPreInitAllocation* a2 = new NMTPreInitAllocation(new_payload_size, new_payload);
  delete a;
  return a2;
}

void NMTPreInitAllocation::do_free(NMTPreInitAllocation* a) {
  assert(a->next == nullptr, "unhang from map first");
  raw_free(a->payload);
  delete a;
}

// --------- NMTPreInitAllocationTable --------------

void* NMTPreInitAllocationTable::operator new(size_t count) {
  return raw_checked_malloc(count);
}

void NMTPreInitAllocationTable::operator delete(void* p) {
  return raw_free(p);
}

NMTPreInitAllocationTable::NMTPreInitAllocationTable() {
  ::memset(_entries, 0, sizeof(_entries));
}

NMTPreInitAllocationTable::~NMTPreInitAllocationTable() {
  // clear LU entries, but let payloads live!
  for (int i = 0; i < table_size; i++) {
    NMTPreInitAllocation* a = _entries[i];
    while (a != nullptr) {
      NMTPreInitAllocation* a2 = a->next;
      delete a;
      a = a2;
    }
  }
}

// print a string describing the current state
void NMTPreInitAllocationTable::print_state(outputStream* st) const {
  // Collect some statistics and print them
  int num_entries = 0;
  int num_primary_entries = 0;
  int longest_chain = 0;
  size_t sum_bytes = 0;
  for (int i = 0; i < table_size; i++) {
    int chain_len = 0;
    for (NMTPreInitAllocation* a = _entries[i]; a != nullptr; a = a->next) {
      chain_len++;
      sum_bytes += a->size;
    }
    if (chain_len > 0) {
      num_primary_entries++;
    }
    num_entries += chain_len;
    longest_chain = MAX2(chain_len, longest_chain);
  }
  st->print("entries: %d (primary: %d, empties: %d), sum bytes: " SIZE_FORMAT
            ", longest chain length: %d",
            num_entries, num_primary_entries, table_size - num_primary_entries,
            sum_bytes, longest_chain);
}

#ifdef ASSERT
void NMTPreInitAllocationTable::print_map(outputStream* st) const {
  for (int i = 0; i < table_size; i++) {
    st->print("[%d]: ", i);
    for (NMTPreInitAllocation* a = _entries[i]; a != nullptr; a = a->next) {
      st->print( PTR_FORMAT "(" SIZE_FORMAT ") ", p2i(a->payload), a->size);
    }
    st->cr();
  }
}

void NMTPreInitAllocationTable::verify() const {
  // This verifies the buildup of the lookup table, including the load and the chain lengths.
  // We should see chain lens of 0-1 under normal conditions. Under artificial conditions
  // (20000 VM args) we should see maybe 6-7. From a certain length on we can be sure something
  // is broken.
  const int longest_acceptable_chain_len = 30;
  int num_chains_too_long = 0;
  for (index_t i = 0; i < table_size; i++) {
    int len = 0;
    for (const NMTPreInitAllocation* a = _entries[i]; a != nullptr; a = a->next) {
      index_t i2 = index_for_key(a->payload);
      assert(i2 == i, "wrong hash");
      assert(a->size > 0, "wrong size");
      len++;
      // very paranoid: search for dups
      bool found = false;
      for (const NMTPreInitAllocation* a2 = _entries[i]; a2 != nullptr; a2 = a2->next) {
        if (a == a2) {
          assert(!found, "dup!");
          found = true;
        }
      }
    }
    if (len > longest_acceptable_chain_len) {
      num_chains_too_long++;
    }
  }
  if (num_chains_too_long > 0) {
    assert(false, "NMT preinit lookup table degenerated (%d/%d chains longer than %d)",
                  num_chains_too_long, table_size, longest_acceptable_chain_len);
  }
}
#endif // ASSERT

// --------- NMTPreinit --------------

NMTPreInitAllocationTable* NMTPreInit::_table = nullptr;

// Some statistics
unsigned NMTPreInit::_num_mallocs_pre = 0;
unsigned NMTPreInit::_num_reallocs_pre = 0;
unsigned NMTPreInit::_num_frees_pre = 0;

void NMTPreInit::create_table() {
  assert(_table == nullptr, "just once");
  _table = new NMTPreInitAllocationTable;
}

// Allocate with os::malloc (hidden to prevent having to include os.hpp)
void* NMTPreInit::do_os_malloc(size_t size, MEMFLAGS memflags) {
  return os::malloc(size, memflags);
}

// Switches from NMT pre-init state to NMT post-init state;
//  in post-init, no modifications to the lookup table are possible.
void NMTPreInit::pre_to_post(bool nmt_off) {

  assert(!MemTracker::is_initialized(), "just once");
  DEBUG_ONLY(verify();)
  if (nmt_off) {
    // NMT is disabled.
    // Since neither pre- nor post-init-allocations use headers, from now on any pre-init allocation
    // can be handled directly by os::realloc or os::free.
    // We also can get rid of the lookup table.
    // Note that we deliberately leak the headers (NMTPreInitAllocation) in order to speed up startup.
    // That may leak about 12KB of memory for ~500 surviving pre-init allocations, which is a typical
    // number. This is a compromise to keep the coding simple and startup time short. It could very
    // easily improved by keeping a header pool, similar to metaspace ChunkHeaderPool. But since NMTPreInit
    // had been critizised as "too complicated", I try to keep things short and simple.
    delete _table;
    _table = nullptr;
  }
}

#ifdef ASSERT
void NMTPreInit::verify() {
  if (_table != nullptr) {
    _table->verify();
  }
  assert(_num_reallocs_pre <= _num_mallocs_pre &&
         _num_frees_pre <= _num_mallocs_pre, "stats are off");
}
#endif // ASSERT

void NMTPreInit::print_state(outputStream* st) {
  if (_table != nullptr) {
    _table->print_state(st);
    st->cr();
  }
  st->print_cr("pre-init mallocs: %u, pre-init reallocs: %u, pre-init frees: %u",
               _num_mallocs_pre, _num_reallocs_pre, _num_frees_pre);
}
