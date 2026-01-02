/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/shared/gc_globals.hpp"
#include "gc/shared/gcLogPrecious.hpp"
#include "gc/z/zAddress.inline.hpp"
#include "gc/z/zAddressSpaceLimit.hpp"
#include "gc/z/zArray.hpp"
#include "gc/z/zGlobals.hpp"
#include "gc/z/zInitialize.hpp"
#include "gc/z/zNMT.hpp"
#include "gc/z/zNUMA.inline.hpp"
#include "gc/z/zOnError.hpp"
#include "gc/z/zValue.inline.hpp"
#include "gc/z/zVirtualMemory.inline.hpp"
#include "gc/z/zVirtualMemoryManager.inline.hpp"
#include "utilities/align.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"

bool ZVirtualMemoryReserver::reserve(uintptr_t addr, size_t size) {
  log_debug(gc, init)("ZGC reserve:   [" PTR_FORMAT " - " PTR_FORMAT ")", addr, addr + size);

  // Reserve address space
  if (!pd_reserve(addr, size)) {
    return false;
  }

  // Register address views with native memory tracker
  ZNMT::reserve(addr, size);

  return true;
}

void ZVirtualMemoryReserver::split_reserved(uintptr_t addr, size_t split_size, size_t size) {
  pd_split_reserved(addr, split_size, size);
}

void ZVirtualMemoryReserver::unreserve(uintptr_t addr, size_t size) {
  log_debug(gc, init)("ZGC unreserve: [" PTR_FORMAT " - " PTR_FORMAT ")", addr, addr + size);

  // Unregister the reserved memory from NMT
  ZNMT::unreserve(addr, size);

  // Unreserve address space
  pd_unreserve(addr, size);
}

ZVirtualMemoryWithHeapBaseReserver::ZVirtualMemoryWithHeapBaseReserver(size_t heap_base)
  : _heap_base(heap_base),
    _reserved_ranges() {}

ZVirtualMemoryWithHeapBaseReserver::~ZVirtualMemoryWithHeapBaseReserver() {
  unreserve_all();
}

uintptr_t ZVirtualMemoryWithHeapBaseReserver::heap_base() const {
  return _heap_base;
}

size_t ZVirtualMemoryWithHeapBaseReserver::offset_max() const {
  // We currently have a restriction that the offsets don't overflow the heap base bit,
  // this limits the offset bits to be equal to the heap base.
  return (size_t)_heap_base;
}

size_t ZVirtualMemoryWithHeapBaseReserver::reserve(size_t size) {
  if (offset_max() < size) {
    // Only attempt to reserve if the current heap base can accommodate the desired size
    return 0;
  }

#ifdef ASSERT
  if (ZForceDiscontiguousHeapReservations > 0) {
    return force_reserve_discontiguous(size);
  }
#endif

  // Prefer a contiguous address space
  if (reserve_contiguous(size)) {
    return size;
  }

  // Fall back to a discontiguous address space
  return reserve_discontiguous(size);
}


void ZVirtualMemoryWithHeapBaseReserver::transfer_reserved_ranges_to(ZArray<ZVirtualMemoryUntyped>* to) {
  to->appendAll(&_reserved_ranges);
  _reserved_ranges.clear();
}

size_t ZVirtualMemoryWithHeapBaseReserver::unreserve_all() {
  size_t unreserved = 0;

  for (ZVirtualMemoryUntyped range : _reserved_ranges) {
    ZVirtualMemoryReserver::unreserve(range._start, range._size);
    unreserved += range._size;
  }

  _reserved_ranges.clear();

  return unreserved;
}

#ifdef ASSERT
size_t ZVirtualMemoryWithHeapBaseReserver::force_reserve_discontiguous(size_t size) {
  const size_t min_range = calculate_min_range(size);
  const size_t max_range = MAX2(align_down(size / ZForceDiscontiguousHeapReservations, ZGranuleSize), min_range);
  size_t reserved = 0;

  // Try to reserve ZForceDiscontiguousHeapReservations number of virtual memory
  // ranges. Starting with higher addresses.
  size_t end = offset_max();
  while (reserved < size && end >= max_range) {
    const size_t remaining = size - reserved;
    const size_t reserve_size = MIN2(max_range, remaining);
    const size_t reserve_start = end - reserve_size;
    const uintptr_t addr = _heap_base + reserve_start;

    if (reserve_contiguous(addr, reserve_size)) {
      reserved += reserve_size;
      end -= reserve_size;
    }

    end -= MIN2(end, reserve_size);
  }

  // If (reserved < size) attempt to reserve the rest via normal divide and conquer
  uintptr_t start = 0;
  while (reserved < size && start < offset_max()) {
    const size_t remaining = MIN2(size - reserved, offset_max() - start);
    const uintptr_t addr = _heap_base + start;
    reserved += reserve_discontiguous(addr, remaining, min_range);
    start += remaining;
  }

  return reserved;
}
#endif

size_t ZVirtualMemoryWithHeapBaseReserver::reserve_discontiguous(uintptr_t addr, size_t size, size_t min_range) {
  if (size < min_range) {
    // Too small
    return 0;
  }

  assert(is_aligned(size, ZGranuleSize), "Misaligned");

  if (reserve_contiguous(addr, size)) {
    return size;
  }

  const size_t half = size / 2;
  if (half < min_range) {
    // Too small
    return 0;
  }

  // Divide and conquer
  const size_t first_part = align_down(half, ZGranuleSize);
  const size_t second_part = size - first_part;
  const size_t first_size = reserve_discontiguous(addr, first_part, min_range);
  const size_t second_size = reserve_discontiguous(addr + first_part, second_part, min_range);
  return first_size + second_size;
}

size_t ZVirtualMemoryWithHeapBaseReserver::calculate_min_range(size_t size) {
  // Don't try to reserve address ranges smaller than 1% of the requested size.
  // This avoids an explosion of reservation attempts in case large parts of the
  // address space is already occupied.
  return align_up(size / ZMaxVirtualReservations, ZGranuleSize);
}

size_t ZVirtualMemoryWithHeapBaseReserver::reserve_discontiguous(size_t size) {
  const size_t min_range = calculate_min_range(size);
  uintptr_t start = 0;
  size_t reserved = 0;

  // Reserve size somewhere between [0, offset_max())
  while (reserved < size && start < offset_max()) {
    const size_t remaining = MIN2(size - reserved, offset_max() - start);
    const uintptr_t addr = _heap_base + start;
    reserved += reserve_discontiguous(addr, remaining, min_range);
    start += remaining;
  }

  return reserved;
}

bool ZVirtualMemoryWithHeapBaseReserver::reserve_contiguous(uintptr_t addr, size_t size) {
  assert(is_aligned(size, ZGranuleSize), "Must be granule aligned 0x%zx", size);
  assert(addr >= _heap_base && addr < _heap_base + offset_max(),
         PTR_FORMAT " not within [" PTR_FORMAT ", " PTR_FORMAT ")",
         addr, _heap_base, _heap_base + offset_max());

  if (!ZVirtualMemoryReserver::reserve(addr, size)) {
    return false;
  }

  // Register the memory reservation
  _reserved_ranges.append({addr, size});

  return true;
}

bool ZVirtualMemoryWithHeapBaseReserver::reserve_contiguous(size_t size) {
  // Allow at most 8192 attempts spread evenly across [0, offset_max)
  const size_t unused = offset_max() - size;
  const size_t increment = MAX2(align_up(unused / 8192, ZGranuleSize), ZGranuleSize);

  for (uintptr_t start = 0; start + size <= offset_max(); start += increment) {
    const uintptr_t addr = _heap_base + start;
    if (reserve_contiguous(addr, size)) {
      // Success
      return true;
    }
  }

  // Failed
  return false;
}

class ZHeapBaseIterator {
private:
  const size_t _initial;
  size_t       _current;

public:
  ZHeapBaseIterator(size_t initial_heap_base_shift = ZGlobalsPointers::initial_heap_base_shift())
    : _initial(initial_heap_base_shift),
      _current(initial_heap_base_shift) {}

  bool next(uintptr_t* out_heap_base) {
    size_t next = ZGlobalsPointers::next_heap_base_shift(_current);
    if (next == _initial) {
      // Iterator has completed
      return false;
    }

    _current = next;

    const uintptr_t heap_base = uintptr_t(1) << _current;

    log_trace(gc, init)("Attempting Heap Base: " PTR_FORMAT, heap_base);

    *out_heap_base = heap_base;

    return true;
  }
};

ZVirtualMemoryAdaptiveReserver::ZVirtualMemoryAdaptiveReserver()
  : _heap_base(),
    _reserved_ranges() {}

static int compare_ZVirtualMemoryUntyped(ZVirtualMemoryUntyped* vmem0, ZVirtualMemoryUntyped* vmem1) {
  if (vmem0->_start == vmem1->_start) {
    return 0;
  } else if (vmem0->_start < vmem1->_start) {
    return -1;
  } else {
    return 1;
  }
};

void ZVirtualMemoryAdaptiveReserver::accept(ZVirtualMemoryWithHeapBaseReserver* reserver) {
  _heap_base = reserver->heap_base();
  reserver->transfer_reserved_ranges_to(&_reserved_ranges);
  _reserved_ranges.sort(&compare_ZVirtualMemoryUntyped);
}

size_t ZVirtualMemoryAdaptiveReserver::reserve(size_t required_size, size_t desired_size) {
  assert(required_size <= desired_size, "0x%zx <= 0x%zx", required_size, desired_size);

  size_t heap_base;

  // First attempt to get the desired size
  for (ZHeapBaseIterator iter{}; iter.next(&heap_base);) {
    ZVirtualMemoryWithHeapBaseReserver reserver(heap_base);

    const size_t reserved = reserver.reserve(desired_size);

    if (reserved >= desired_size) {
      // Succeeded
      accept(&reserver);
      return reserved;
    }
  }

  // Second attempt to get at least the required size
  for (ZHeapBaseIterator iter{}; iter.next(&heap_base);) {
    ZVirtualMemoryWithHeapBaseReserver reserver(heap_base);

    const size_t max_reserve_size = reserver.offset_max();
    assert(max_reserve_size >= required_size, "Should not have attempted this heap base: "
          PTR_FORMAT " for required size: 0x%zx", heap_base, required_size);

    // Still attempt to get up to desired_size
    const size_t to_reserve = MIN2<size_t>(max_reserve_size, desired_size);

    const size_t reserved = reserver.reserve(to_reserve);

    if (reserved >= required_size) {
      // Succeeded
      accept(&reserver);
      return reserved;
    }
  }

  // Failed to reserve
  return 0;
}

size_t ZVirtualMemoryAdaptiveReserver::unreserve_after(size_t keep_size) {
  precond(keep_size > 0);
  precond(keep_size <= reserved());

  const size_t before = reserved();

  struct UnreservePoint {
    int    _index;
    size_t _offset;
  };

  auto find_unreserve_point = [&]() -> UnreservePoint {
    size_t accumulated = 0;

    for (int i = 0; i < _reserved_ranges.length(); i++) {
      ZVirtualMemoryUntyped vmem = _reserved_ranges.at(i);

      accumulated += vmem._size;

      if (accumulated < keep_size) {
        // Keep on accumulating
        continue;
      }

      // We have found the unreserve point

      if (accumulated > keep_size) {
        // The unreserve point splits a vmem
        size_t vmem_over_size = (accumulated - keep_size);
        size_t vmem_split_size = vmem._size - vmem_over_size;

        return {i, vmem_split_size};
      }

      // The unreserve point doesn't split a vmem
      return {i + 1, 0};
    }

    // Nothing to split
    return {_reserved_ranges.length(), 0};
  };

  // Search for the point where we should unreserve from
  const UnreservePoint split = find_unreserve_point();

  int index = split._index;
  const size_t offset = split._offset;

  size_t unreserved = 0;

  auto do_unreserve = [&](uintptr_t addr, size_t size) {
    ZVirtualMemoryReserver::unreserve(addr, size);
    unreserved += size;
  };

  // Split a vmem if the unreserve point falls inside a vmem
  if (offset > 0) {
    const ZVirtualMemoryUntyped& vmem = _reserved_ranges.at(index);

    // Mainly a call to Windows that the memory reservation is split
    ZVirtualMemoryReserver::split_reserved(vmem._start, offset, vmem._size);

    // Unreserve the surplus
    do_unreserve(vmem._start + offset, vmem._size - offset);

    // Re-register the area that was shrunk
    _reserved_ranges.at(index) = ZVirtualMemoryUntyped{vmem._start, offset};

    // Unreserve the rest
    index++;
  }

  // Unreserve the reset of the vmems
  for (int i = index; i < _reserved_ranges.length(); i++) {
    const ZVirtualMemoryUntyped& vmem = _reserved_ranges.at(i);

    do_unreserve(vmem._start, vmem._size);
  }

  _reserved_ranges.trunc_to(index);

  z_on_error_capture_64_6(keep_size, unreserved, before, index, offset, _reserved_ranges.length());

  postcond(keep_size + unreserved == before);
  postcond(reserved() == keep_size);

  return unreserved;
}

void ZVirtualMemoryAdaptiveReserver::unreserve_all() {
  for (ZVirtualMemoryUntyped vmem : _reserved_ranges) {
    ZVirtualMemoryReserver::unreserve(vmem._start, vmem._size);
  }

  _reserved_ranges.clear();
}

uintptr_t ZVirtualMemoryAdaptiveReserver::heap_base() const {
  return _heap_base;
}

ZArray<ZVirtualMemoryUntyped>* ZVirtualMemoryAdaptiveReserver::reserved_ranges() {
  return &_reserved_ranges;
}

uintptr_t ZVirtualMemoryAdaptiveReserver::bottom() const {
  uintptr_t min_start = SIZE_MAX;

  for (auto range : _reserved_ranges) {
    const uintptr_t start = range._start;

    if (start < min_start) {
      min_start = start;
    }

  }

  postcond(min_start != SIZE_MAX);

  return min_start;
}

uintptr_t ZVirtualMemoryAdaptiveReserver::end() const {
  uintptr_t max_end = 0;

  OnVMError on_error([&](outputStream* st) {
    for (auto vmem : _reserved_ranges) {
      st->print_cr(" " PTR_FORMAT " " PTR_FORMAT " %zuM", vmem._start, vmem._start + vmem._size, vmem._size / M);
    }
  });

  for (auto range : _reserved_ranges) {
    const uintptr_t end = range._start + range._size;

    assert(end > max_end,
           "Unordered reserved memory end: " PTR_FORMAT " max_end: " PTR_FORMAT,
           end, max_end);

    if (end > max_end) {
      max_end = end;
    }

  }

  return max_end;
}

size_t ZVirtualMemoryAdaptiveReserver::reserved() const {
  size_t reserved = 0;

  for (auto range : _reserved_ranges) {
    reserved += range._size;
  }

  return reserved;
}

ZVirtualMemoryReservation::ZVirtualMemoryReservation(ZArray<ZVirtualMemoryUntyped>* reserved_ranges)
  : _registry() {

  // Register Windows callbacks
  pd_register_callbacks(&_registry);

  // Register the reserved regions with the registry
  transfer_reserved_ranges(reserved_ranges);
}

void ZVirtualMemoryReservation::transfer_reserved_ranges(ZArray<ZVirtualMemoryUntyped>* reserved_ranges) {
  for (ZVirtualMemoryUntyped range : *reserved_ranges) {
    const zaddress_unsafe addr = to_zaddress_unsafe(range._start);
    const zoffset start = ZAddress::offset(addr);
    const size_t size = range._size;

    // Register the memory reservation
    _registry.register_range({start, size});
  }

  // Clear the accepted input array
  reserved_ranges->clear();
}

void ZVirtualMemoryReservation::initialize_partition_registry(ZVirtualMemoryRegistry* partition_registry, size_t size) {
  assert(partition_registry->is_empty(), "Should be empty when initializing");

  // Registers the Windows callbacks
  pd_register_callbacks(partition_registry);

  _registry.transfer_from_low(partition_registry, size);

  // Set the limits according to the virtual memory given to this partition
  partition_registry->anchor_limits();
}

void ZVirtualMemoryReservation::unreserve(const ZVirtualMemory& vmem) {
  const zaddress_unsafe addr = ZOffset::address_unsafe(vmem.start());

  ZVirtualMemoryReserver::unreserve(untype(addr), vmem.size());
}

size_t ZVirtualMemoryReservation::unreserve_all() {
  size_t unreserved = 0;

  for (ZVirtualMemory vmem; _registry.unregister_first(&vmem);) {
    unreserve(vmem);
    unreserved += vmem.size();
  }

  return unreserved;
}

bool ZVirtualMemoryReservation::is_empty() const {
  return _registry.is_empty();
}

bool ZVirtualMemoryReservation::is_contiguous() const {
  return _registry.is_contiguous();
}

size_t ZVirtualMemoryReservation::reserved() const {
  size_t reserved = 0;

  _registry.visit_all([&](const ZVirtualMemory* vmem) {
    reserved += vmem->size();
  });

  return reserved;
}

zoffset_end ZVirtualMemoryReservation::highest_available_address_end() const {
  return _registry.peak_high_address_end();
}

ZVirtualMemoryManager::ZVirtualMemoryManager(size_t max_capacity)
  : _partition_registries(),
    _multi_partition_registry(),
    _is_multi_partition_enabled(false),
    _initialized(false) {

  ZAddressSpaceLimit::print_limits();

  const size_t limit = ZAddressSpaceLimit::heap();

  if (max_capacity > limit) {
    // Cannot fit the heap within the limit
    ZInitialize::error_d("Java heap exceeds address space limits (" EXACTFMT ")", EXACTFMTARGS(limit));
    return;
  }

  const size_t desired_for_partitions = max_capacity * ZVirtualToPhysicalRatio;
  const size_t desired_for_multi_partition = ZNUMA::count() > 1 ? desired_for_partitions : 0;

  const size_t desired = desired_for_partitions + desired_for_multi_partition;
  const size_t requested = desired <= limit
      ? desired
      : MIN2(desired_for_partitions, limit);
  const size_t required = max_capacity;

  log_debug_p(gc, init)("Reserved Space: limit " EXACTFMT ", required " EXACTFMT ", desired " EXACTFMT ", requested " EXACTFMT,
                        EXACTFMTARGS(limit), EXACTFMTARGS(required), EXACTFMTARGS(desired), EXACTFMTARGS(requested));

  ZVirtualMemoryAdaptiveReserver reserver;

  // Reserve virtual memory for the heap
  const size_t reserved = reserver.reserve(required, requested);

  if (reserved < max_capacity) {
    ZInitialize::error_d("Failed to reserve " EXACTFMT " address space for Java heap", EXACTFMTARGS(max_capacity));
    return;
  }

  const size_t size_for_partitions = MIN2(reserved, desired_for_partitions);

  size_t unreserved;
  if (desired_for_multi_partition > 0 && reserved == desired) {
    // Can have multi-partitions
    _is_multi_partition_enabled = true;
    unreserved = 0;
  } else {
    // Failed to reserve enough memory for multi-partition, unreserve unused memory
    unreserved = reserver.unreserve_after(size_for_partitions);
  }

  // Now lock down the heap limits to the reserved spaces selected by the reserver
  ZGlobalsPointers::set_heap_limits(reserver.heap_base(), reserver.end());

  // Transfer the reserved ranges to the type-safe system
  ZVirtualMemoryReservation reservation(reserver.reserved_ranges());

  // Divide size_for_partitions virtual memory over the NUMA nodes
  initialize_partitions(&reservation, size_for_partitions);

  // Set up multi-partition
  if (_is_multi_partition_enabled) {
    // Enough left to setup the multi-partition memory reservation
    reservation.initialize_partition_registry(&_multi_partition_registry, desired_for_multi_partition);
  }

  assert(reservation.is_empty(), "Must have handled all reserved memory");

  const double heap_ratio = static_cast<double>(reserved) / static_cast<double>(max_capacity);
  const uintptr_t lowest_offset = untype(lowest_available_address(0));
  const bool is_contiguous = reservation.is_contiguous();

  log_info_p(gc, init)("Reserved Space Type: %s/%s/%s",
                       (is_contiguous ? "Contiguous" : "Discontiguous"),
                       (requested == desired ? "Unrestricted" : "Restricted"),
                       (reserved == desired ? "Complete" : ((reserved < desired_for_partitions) ? "Degraded"  : "NUMA-Degraded")));
  log_info_p(gc, init)("Reserved Space Size: " EXACTFMT " (x%.2f Heap Ratio)", EXACTFMTARGS(reserved - unreserved), heap_ratio);
  log_debug_p(gc, init)("Reserved Space Span: " RANGE2EXACTFMT, ZAddressHeapBase + lowest_offset, ZAddressHeapBase + ZAddressOffsetUpperLimit,
                        EXACTFMTARGS(ZAddressOffsetUpperLimit - lowest_offset));

  // Successfully initialized
  _initialized = true;
}

void ZVirtualMemoryManager::initialize_partitions(ZVirtualMemoryReservation* reservation, size_t size_for_partitions) {
  precond(is_aligned(size_for_partitions, ZGranuleSize));

  // If the capacity consist of less granules than the number of partitions
  // some partitions will be empty. Distribute these shares on the none empty
  // partitions.
  const uint32_t first_empty_numa_id = MIN2(static_cast<uint32_t>(size_for_partitions >> ZGranuleSizeShift), ZNUMA::count());
  const uint32_t ignore_count = ZNUMA::count() - first_empty_numa_id;

  // Install reserved memory into registry(s)
  uint32_t numa_id;
  ZPerNUMAIterator<ZVirtualMemoryRegistry> iter(&_partition_registries);
  for (ZVirtualMemoryRegistry* registry; iter.next(&registry, &numa_id);) {
    if (numa_id == first_empty_numa_id) {
      break;
    }

    // Calculate how much reserved memory this partition gets
    const size_t reserved_for_partition = ZNUMA::calculate_share(numa_id, size_for_partitions, ZGranuleSize, ignore_count);

    // Transfer reserved memory
    reservation->initialize_partition_registry(registry, reserved_for_partition);
  }
}

bool ZVirtualMemoryManager::is_initialized() const {
  return _initialized;
}

ZVirtualMemoryRegistry& ZVirtualMemoryManager::registry(uint32_t partition_id) {
  return _partition_registries.get(partition_id);
}

const ZVirtualMemoryRegistry& ZVirtualMemoryManager::registry(uint32_t partition_id) const {
  return _partition_registries.get(partition_id);
}

zoffset ZVirtualMemoryManager::lowest_available_address(uint32_t partition_id) const {
  return registry(partition_id).peek_low_address();
}

void ZVirtualMemoryManager::insert(const ZVirtualMemory& vmem, uint32_t partition_id) {
  assert(partition_id == lookup_partition_id(vmem), "wrong partition_id for vmem");
  registry(partition_id).insert(vmem);
}

void ZVirtualMemoryManager::insert_multi_partition(const ZVirtualMemory& vmem) {
  _multi_partition_registry.insert(vmem);
}

size_t ZVirtualMemoryManager::remove_from_low_many_at_most(size_t size, uint32_t partition_id, ZArray<ZVirtualMemory>* vmems_out) {
  return registry(partition_id).remove_from_low_many_at_most(size, vmems_out);
}

ZVirtualMemory ZVirtualMemoryManager::remove_from_low(size_t size, uint32_t partition_id) {
  return registry(partition_id).remove_from_low(size);
}

ZVirtualMemory ZVirtualMemoryManager::remove_from_low_multi_partition(size_t size) {
  return _multi_partition_registry.remove_from_low(size);
}

void ZVirtualMemoryManager::insert_and_remove_from_low_many(const ZVirtualMemory& vmem, uint32_t partition_id, ZArray<ZVirtualMemory>* vmems_out) {
  registry(partition_id).insert_and_remove_from_low_many(vmem, vmems_out);
}

ZVirtualMemory ZVirtualMemoryManager::insert_and_remove_from_low_exact_or_many(size_t size, uint32_t partition_id, ZArray<ZVirtualMemory>* vmems_in_out) {
  return registry(partition_id).insert_and_remove_from_low_exact_or_many(size, vmems_in_out);
}
