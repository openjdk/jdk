/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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
 */

#include "precompiled.hpp"
#include "code/relocInfo.hpp"
#include "code/nativeInst.hpp"
#include "code/nmethod.hpp"
#include "gc/z/zGlobals.hpp"
#include "gc/z/zHash.inline.hpp"
#include "gc/z/zNMethodTable.hpp"
#include "logging/log.hpp"
#include "memory/allocation.inline.hpp"
#include "memory/resourceArea.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/atomic.hpp"
#include "utilities/debug.hpp"

class ZNMethodWithImmediateOops {
private:
  nmethod* const _nm;
  const size_t   _nimmediate_oops;

  static size_t header_size();

  ZNMethodWithImmediateOops(nmethod* nm, const GrowableArray<oop*>& immediate_oops);

public:
  static ZNMethodWithImmediateOops* create(nmethod* nm, const GrowableArray<oop*>& immediate_oops);
  static void destroy(ZNMethodWithImmediateOops* nmi);

  nmethod* method() const;
  size_t immediate_oops_count() const;
  oop** immediate_oops_begin() const;
  oop** immediate_oops_begin_safe() const;
  oop** immediate_oops_end() const;
};

size_t ZNMethodWithImmediateOops::header_size() {
  const size_t size = sizeof(ZNMethodWithImmediateOops);
  assert(is_aligned(size, sizeof(oop*)), "Header misaligned");
  return size;
}

ZNMethodWithImmediateOops::ZNMethodWithImmediateOops(nmethod* nm, const GrowableArray<oop*>& immediate_oops) :
    _nm(nm),
    _nimmediate_oops(immediate_oops.length()) {
  // Save all immediate oops
  for (size_t i = 0; i < _nimmediate_oops; i++) {
    immediate_oops_begin()[i] = immediate_oops.at(i);
  }
}

ZNMethodWithImmediateOops* ZNMethodWithImmediateOops::create(nmethod* nm, const GrowableArray<oop*>& immediate_oops) {
  // Allocate memory for the ZNMethodWithImmediateOops object
  // plus the immediate oop* array that follows right after.
  const size_t size = header_size() + (sizeof(oop*) * immediate_oops.length());
  void* const method_with_immediate_oops = NEW_C_HEAP_ARRAY(uint8_t, size, mtGC);
  return ::new (method_with_immediate_oops) ZNMethodWithImmediateOops(nm, immediate_oops);
}

void ZNMethodWithImmediateOops::destroy(ZNMethodWithImmediateOops* nmi) {
  FREE_C_HEAP_ARRAY(uint8_t, nmi);
}

nmethod* ZNMethodWithImmediateOops::method() const {
  return _nm;
}

size_t ZNMethodWithImmediateOops::immediate_oops_count() const {
  return _nimmediate_oops;
}

oop** ZNMethodWithImmediateOops::immediate_oops_begin() const {
  // The immediate oop* array starts immediately after this object
  return (oop**)((uintptr_t)this + header_size());
}

oop** ZNMethodWithImmediateOops::immediate_oops_begin_safe() const {
  // Non-entrant nmethods have a jump instruction patched into the beginning
  // of the verified entry point, which could have overwritten an immediate
  // oop. If so, make sure we skip over that oop.
  if (_nm->is_not_entrant()) {
    oop* const first_immediate_oop = *immediate_oops_begin();
    oop* const safe_begin = (oop*)(_nm->verified_entry_point() + NativeJump::instruction_size);
    if (first_immediate_oop < safe_begin) {
      // First immediate oop overwritten, skip it
      return immediate_oops_begin() + 1;
    }
  }

  // First immediate oop not overwritten
  return immediate_oops_begin();
}


oop** ZNMethodWithImmediateOops::immediate_oops_end() const {
  return immediate_oops_begin() + immediate_oops_count();
}

ZNMethodTableEntry* ZNMethodTable::_table = NULL;
size_t ZNMethodTable::_size = 0;
size_t ZNMethodTable::_nregistered = 0;
size_t ZNMethodTable::_nunregistered = 0;
volatile size_t ZNMethodTable::_claimed = 0;

ZNMethodTableEntry ZNMethodTable::create_entry(nmethod* nm) {
  GrowableArray<oop*> immediate_oops;
  bool non_immediate_oops = false;

  // Find all oops relocations
  RelocIterator iter(nm);
  while (iter.next()) {
    if (iter.type() != relocInfo::oop_type) {
      // Not an oop
      continue;
    }

    oop_Relocation* r = iter.oop_reloc();

    if (!r->oop_is_immediate()) {
      // Non-immediate oop found
      non_immediate_oops = true;
      continue;
    }

    if (r->oop_value() != NULL) {
      // Non-NULL immediate oop found. NULL oops can safely be
      // ignored since the method will be re-registered if they
      // are later patched to be non-NULL.
      immediate_oops.push(r->oop_addr());
    }
  }

  // oops_count() returns the number of oops in the oop table plus one
  if (immediate_oops.is_empty() && nm->oops_count() == 1) {
    // No oops found, return empty entry
    return ZNMethodTableEntry();
  }

  if (immediate_oops.is_empty()) {
    // No immediate oops found, return entry without immediate oops
    return ZNMethodTableEntry(nm, non_immediate_oops);
  }

  // Return entry with immediate oops
  return ZNMethodTableEntry(ZNMethodWithImmediateOops::create(nm, immediate_oops), non_immediate_oops);
}

void ZNMethodTable::destroy_entry(ZNMethodTableEntry entry) {
  if (entry.immediate_oops()) {
    ZNMethodWithImmediateOops::destroy(entry.method_with_immediate_oops());
  }
}

nmethod* ZNMethodTable::method(ZNMethodTableEntry entry) {
  return entry.immediate_oops() ? entry.method_with_immediate_oops()->method() : entry.method();
}

size_t ZNMethodTable::first_index(const nmethod* nm, size_t size) {
  assert(is_power_of_2(size), "Invalid size");
  const size_t mask = size - 1;
  const size_t hash = ZHash::address_to_uint32((uintptr_t)nm);
  return hash & mask;
}

size_t ZNMethodTable::next_index(size_t prev_index, size_t size) {
  assert(is_power_of_2(size), "Invalid size");
  const size_t mask = size - 1;
  return (prev_index + 1) & mask;
}

bool ZNMethodTable::register_entry(ZNMethodTableEntry* table, size_t size, ZNMethodTableEntry entry) {
  const nmethod* const nm = method(entry);
  size_t index = first_index(nm, size);

  for (;;) {
    const ZNMethodTableEntry table_entry = table[index];

    if (!table_entry.registered() && !table_entry.unregistered()) {
      // Insert new entry
      table[index] = entry;
      return true;
    }

    if (table_entry.registered() && method(table_entry) == nm) {
      // Replace existing entry
      destroy_entry(table_entry);
      table[index] = entry;
      return false;
    }

    index = next_index(index, size);
  }
}

bool ZNMethodTable::unregister_entry(ZNMethodTableEntry* table, size_t size, const nmethod* nm) {
  if (size == 0) {
    // Table is empty
    return false;
  }

  size_t index = first_index(nm, size);

  for (;;) {
    const ZNMethodTableEntry table_entry = table[index];

    if (!table_entry.registered() && !table_entry.unregistered()) {
      // Entry not found
      return false;
    }

    if (table_entry.registered() && method(table_entry) == nm) {
      // Remove entry
      destroy_entry(table_entry);
      table[index] = ZNMethodTableEntry(true /* unregistered */);
      return true;
    }

    index = next_index(index, size);
  }
}

void ZNMethodTable::rebuild(size_t new_size) {
  assert(is_power_of_2(new_size), "Invalid size");

  log_debug(gc, nmethod)("Rebuilding NMethod Table: "
                         SIZE_FORMAT "->" SIZE_FORMAT " entries, "
                         SIZE_FORMAT "(%.0lf%%->%.0lf%%) registered, "
                         SIZE_FORMAT "(%.0lf%%->%.0lf%%) unregistered",
                         _size, new_size,
                         _nregistered, percent_of(_nregistered, _size), percent_of(_nregistered, new_size),
                         _nunregistered, percent_of(_nunregistered, _size), 0.0);

  // Allocate new table
  ZNMethodTableEntry* const new_table = new ZNMethodTableEntry[new_size];

  // Transfer all registered entries
  for (size_t i = 0; i < _size; i++) {
    const ZNMethodTableEntry entry = _table[i];
    if (entry.registered()) {
      register_entry(new_table, new_size, entry);
    }
  }

  // Delete old table
  delete [] _table;

  // Install new table
  _table = new_table;
  _size = new_size;
  _nunregistered = 0;
}

void ZNMethodTable::rebuild_if_needed() {
  // The hash table uses linear probing. To avoid wasting memory while
  // at the same time maintaining good hash collision behavior we want
  // to keep the table occupancy between 30% and 70%. The table always
  // grows/shrinks by doubling/halving its size. Pruning of unregistered
  // entries is done by rebuilding the table with or without resizing it.
  const size_t min_size = 1024;
  const size_t shrink_threshold = _size * 0.30;
  const size_t prune_threshold = _size * 0.65;
  const size_t grow_threshold = _size * 0.70;

  if (_size == 0) {
    // Initialize table
    rebuild(min_size);
  } else if (_nregistered < shrink_threshold && _size > min_size) {
    // Shrink table
    rebuild(_size / 2);
  } else if (_nregistered + _nunregistered > grow_threshold) {
    // Prune or grow table
    if (_nregistered < prune_threshold) {
      // Prune table
      rebuild(_size);
    } else {
      // Grow table
      rebuild(_size * 2);
    }
  }
}

void ZNMethodTable::log_register(const nmethod* nm, ZNMethodTableEntry entry) {
  LogTarget(Trace, gc, nmethod) log;
  if (!log.is_enabled()) {
    return;
  }

  log.print("Register NMethod: %s.%s (" PTR_FORMAT "), "
            "Compiler: %s, Oops: %d, ImmediateOops: " SIZE_FORMAT ", NonImmediateOops: %s",
            nm->method()->method_holder()->external_name(),
            nm->method()->name()->as_C_string(),
            p2i(nm),
            nm->compiler_name(),
            nm->oops_count() - 1,
            entry.immediate_oops() ? entry.method_with_immediate_oops()->immediate_oops_count() : 0,
            BOOL_TO_STR(entry.non_immediate_oops()));

  LogTarget(Trace, gc, nmethod, oops) log_oops;
  if (!log_oops.is_enabled()) {
    return;
  }

  // Print nmethod oops table
  oop* const begin = nm->oops_begin();
  oop* const end = nm->oops_end();
  for (oop* p = begin; p < end; p++) {
    log_oops.print("           Oop[" SIZE_FORMAT "] " PTR_FORMAT " (%s)",
                   (p - begin), p2i(*p), (*p)->klass()->external_name());
  }

  if (entry.immediate_oops()) {
    // Print nmethod immediate oops
    const ZNMethodWithImmediateOops* const nmi = entry.method_with_immediate_oops();
    oop** const begin = nmi->immediate_oops_begin();
    oop** const end = nmi->immediate_oops_end();
    for (oop** p = begin; p < end; p++) {
      log_oops.print("  ImmediateOop[" SIZE_FORMAT "] " PTR_FORMAT " @ " PTR_FORMAT " (%s)",
                     (p - begin), p2i(**p), p2i(*p), (**p)->klass()->external_name());
    }
  }
}

void ZNMethodTable::log_unregister(const nmethod* nm) {
  LogTarget(Debug, gc, nmethod) log;
  if (!log.is_enabled()) {
    return;
  }

  log.print("Unregister NMethod: %s.%s (" PTR_FORMAT ")",
            nm->method()->method_holder()->external_name(),
            nm->method()->name()->as_C_string(),
            p2i(nm));
}

size_t ZNMethodTable::registered_nmethods() {
  return _nregistered;
}

size_t ZNMethodTable::unregistered_nmethods() {
  return _nunregistered;
}

void ZNMethodTable::register_nmethod(nmethod* nm) {
  ResourceMark rm;

  // Create entry
  const ZNMethodTableEntry entry = create_entry(nm);

  log_register(nm, entry);

  if (!entry.registered()) {
    // Method doesn't have any oops, ignore it
    return;
  }

  // Grow/Shrink/Prune table if needed
  rebuild_if_needed();

  // Insert new entry
  if (register_entry(_table, _size, entry)) {
    // New entry registered. When register_entry() instead returns
    // false the nmethod was already in the table so we do not want
    // to increase number of registered entries in that case.
    _nregistered++;
  }
}

void ZNMethodTable::unregister_nmethod(nmethod* nm) {
  ResourceMark rm;

  log_unregister(nm);

  // Remove entry
  if (unregister_entry(_table, _size, nm)) {
    // Entry was unregistered. When unregister_entry() instead returns
    // false the nmethod was not in the table (because it didn't have
    // any oops) so we do not want to decrease the number of registered
    // entries in that case.
    _nregistered--;
    _nunregistered++;
  }
}

void ZNMethodTable::gc_prologue() {
  _claimed = 0;
}

void ZNMethodTable::gc_epilogue() {
  assert(_claimed >= _size, "Failed to claim all table entries");
}

void ZNMethodTable::entry_oops_do(ZNMethodTableEntry entry, OopClosure* cl) {
  nmethod* const nm = method(entry);
  if (!nm->is_alive()) {
    // No need to visit oops
    return;
  }

  // Process oops table
  oop* const begin = nm->oops_begin();
  oop* const end = nm->oops_end();
  for (oop* p = begin; p < end; p++) {
    if (*p != Universe::non_oop_word()) {
      cl->do_oop(p);
    }
  }

  if (entry.immediate_oops()) {
    // Process immediate oops
    const ZNMethodWithImmediateOops* const nmi = entry.method_with_immediate_oops();
    oop** const begin = nmi->immediate_oops_begin_safe();
    oop** const end = nmi->immediate_oops_end();
    for (oop** p = begin; p < end; p++) {
      cl->do_oop(*p);
    }
  }

  if (entry.non_immediate_oops()) {
    // Process non-immediate oops
    nm->fix_oop_relocations();
  }
}

void ZNMethodTable::oops_do(OopClosure* cl) {
  for (;;) {
    // Claim table partition. Each partition is currently sized to span
    // two cache lines. This number is just a guess, but seems to work well.
    const size_t partition_size = (ZCacheLineSize * 2) / sizeof(ZNMethodTableEntry);
    const size_t partition_start = MIN2(Atomic::add(partition_size, &_claimed) - partition_size, _size);
    const size_t partition_end = MIN2(partition_start + partition_size, _size);
    if (partition_start == partition_end) {
      // End of table
      break;
    }

    // Process table partition
    for (size_t i = partition_start; i < partition_end; i++) {
      const ZNMethodTableEntry entry = _table[i];
      if (entry.registered()) {
        entry_oops_do(entry, cl);
      }
    }
  }
}
