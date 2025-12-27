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

#ifndef SHARE_GC_Z_ZVIRTUALMEMORYMANAGER_HPP
#define SHARE_GC_Z_ZVIRTUALMEMORYMANAGER_HPP

#include "gc/z/zAddress.hpp"
#include "gc/z/zArray.hpp"
#include "gc/z/zRange.hpp"
#include "gc/z/zRangeRegistry.hpp"
#include "gc/z/zValue.hpp"
#include "gc/z/zVirtualMemory.hpp"
#include "utilities/globalDefinitions.hpp"

class ZVirtualMemoryReserver : AllStatic {
private:
  // Platform specific implementation
  static bool pd_reserve(uintptr_t addr, size_t size);
  static void pd_split_reserved(uintptr_t addr, size_t split_size, size_t size);
  static void pd_unreserve(uintptr_t addr, size_t size);

public:
  static bool reserve(uintptr_t addr, size_t size);
  static void split_reserved(uintptr_t addr, size_t split_size, size_t size);
  static void unreserve(uintptr_t addr, size_t size);
};

class ZVirtualMemoryWithHeapBaseReserver {
  friend class ZVirtualMemoryReservationTest;

private:
  // The heap base to reserve against
  const uintptr_t               _heap_base;
  ZArray<ZVirtualMemoryUntyped> _reserved_ranges;

  static size_t calculate_min_range(size_t size);

  bool reserve_contiguous(uintptr_t addr, size_t size);
  bool reserve_contiguous(size_t size);
  size_t reserve_discontiguous(uintptr_t start, size_t size, size_t min_range);
  size_t reserve_discontiguous(size_t size);

  DEBUG_ONLY(size_t force_reserve_discontiguous(size_t size);)

  size_t unreserve_all();

public:
  ZVirtualMemoryWithHeapBaseReserver(size_t heap_base);
  ~ZVirtualMemoryWithHeapBaseReserver();

  uintptr_t heap_base() const;
  size_t offset_max() const;

  size_t reserve(size_t size);

  void transfer_reserved_ranges_to(ZArray<ZVirtualMemoryUntyped>* to);
};

class ZVirtualMemoryAdaptiveReserver {
  friend class ZTest;
  friend class ZMapperTest;
  friend class ZVirtualMemoryRegistryTest;
  friend class ZVirtualMemoryReservationTest;

private:
  // Accepted heap base
  uintptr_t                     _heap_base;
  // Accepted reserved ranges
  ZArray<ZVirtualMemoryUntyped> _reserved_ranges;

  void accept(ZVirtualMemoryWithHeapBaseReserver* reserver);

public:
  ZVirtualMemoryAdaptiveReserver();

  size_t reserve(size_t required_size, size_t desired_size);
  size_t unreserve_after(size_t keep_size);
  void unreserve_all();

  uintptr_t heap_base() const;
  ZArray<ZVirtualMemoryUntyped>* reserved_ranges();

  uintptr_t bottom() const;
  uintptr_t end() const;
  size_t reserved() const;
};

using ZVirtualMemoryRegistry = ZRangeRegistry<ZVirtualMemory>;

class ZVirtualMemoryReservation {
  friend class ZMapperTest;
  friend class ZTestAddressReserver;
  friend class ZVirtualMemoryReservationTest;

private:
  ZVirtualMemoryRegistry _registry;

  // Platform specific implementation
  void pd_register_callbacks(ZVirtualMemoryRegistry* registry);

  void unreserve(const ZVirtualMemory& vmem);

  void transfer_reserved_ranges(ZArray<ZVirtualMemoryUntyped>* reserved_ranges);

public:
  ZVirtualMemoryReservation(ZArray<ZVirtualMemoryUntyped>* reserved_ranges);

  void initialize_partition_registry(ZVirtualMemoryRegistry* partition_registry, size_t size);

  size_t unreserve_all();

  bool is_empty() const;
  bool is_contiguous() const;

  size_t reserved() const;

  zoffset_end highest_available_address_end() const;
};

class ZVirtualMemoryManager {
private:
  ZPerNUMA<ZVirtualMemoryRegistry> _partition_registries;
  ZVirtualMemoryRegistry           _multi_partition_registry;
  bool                             _is_multi_partition_enabled;
  bool                             _initialized;

  ZVirtualMemoryRegistry& registry(uint32_t partition_id);
  const ZVirtualMemoryRegistry& registry(uint32_t partition_id) const;

public:
  ZVirtualMemoryManager(size_t max_capacity);

  void initialize_partitions(ZVirtualMemoryReservation* reservation, size_t size_for_partitions);

  bool is_initialized() const;
  bool is_multi_partition_enabled() const;
  bool is_in_multi_partition(const ZVirtualMemory& vmem) const;

  uint32_t lookup_partition_id(const ZVirtualMemory& vmem) const;
  zoffset lowest_available_address(uint32_t partition_id) const;

  void insert(const ZVirtualMemory& vmem, uint32_t partition_id);
  void insert_multi_partition(const ZVirtualMemory& vmem);

  size_t remove_from_low_many_at_most(size_t size, uint32_t partition_id, ZArray<ZVirtualMemory>* vmems_out);
  ZVirtualMemory remove_from_low(size_t size, uint32_t partition_id);
  ZVirtualMemory remove_from_low_multi_partition(size_t size);

  void insert_and_remove_from_low_many(const ZVirtualMemory& vmem, uint32_t partition_id, ZArray<ZVirtualMemory>* vmems_out);
  ZVirtualMemory insert_and_remove_from_low_exact_or_many(size_t size, uint32_t partition_id, ZArray<ZVirtualMemory>* vmems_in_out);
};

#endif // SHARE_GC_Z_ZVIRTUALMEMORYMANAGER_HPP
