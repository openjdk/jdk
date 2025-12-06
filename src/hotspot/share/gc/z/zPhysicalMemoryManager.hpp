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

#ifndef SHARE_GC_Z_ZPHYSICALMEMORYMANAGER_HPP
#define SHARE_GC_Z_ZPHYSICALMEMORYMANAGER_HPP

#include "gc/z/zAddress.hpp"
#include "gc/z/zArray.hpp"
#include "gc/z/zGranuleMap.hpp"
#include "gc/z/zRange.hpp"
#include "gc/z/zRangeRegistry.hpp"
#include "gc/z/zValue.hpp"
#include "memory/allocation.hpp"
#include OS_HEADER(gc/z/zPhysicalMemoryBacking)

class ZVirtualMemory;

using ZBackingIndexRange = ZRange<zbacking_index, zbacking_index_end>;

class ZPhysicalMemoryManager {
private:
  using ZBackingIndexRegistry = ZRangeRegistry<ZBackingIndexRange>;

  ZPhysicalMemoryBacking          _backing;
  ZPerNUMA<ZBackingIndexRegistry> _partition_registries;
  ZGranuleMap<zbacking_index>     _physical_mappings;

  void copy_to_stash(ZArraySlice<zbacking_index> stash, const ZVirtualMemory& vmem) const;
  void copy_from_stash(const ZArraySlice<const zbacking_index> stash, const ZVirtualMemory& vmem);

public:
  ZPhysicalMemoryManager(size_t max_capacity);

  bool is_initialized() const;

  void warn_commit_limits(size_t max_capacity) const;
  void try_enable_uncommit(size_t min_capacity, size_t max_capacity);

  void alloc(const ZVirtualMemory& vmem, uint32_t numa_id);
  void free(const ZVirtualMemory& vmem, uint32_t numa_id);

  size_t commit(const ZVirtualMemory& vmem, uint32_t numa_id);
  size_t uncommit(const ZVirtualMemory& vmem);

  void map(const ZVirtualMemory& vmem, uint32_t numa_id) const;
  void unmap(const ZVirtualMemory& vmem) const;

  void copy_physical_segments(const ZVirtualMemory& to, const ZVirtualMemory& from);

  void sort_segments_physical(const ZVirtualMemory& vmem);

  void stash_segments(const ZVirtualMemory& vmem, ZArray<zbacking_index>* stash_out) const;
  void restore_segments(const ZVirtualMemory& vmem, const ZArray<zbacking_index>& stash);

  void stash_segments(const ZArraySlice<const ZVirtualMemory>& vmems, ZArray<zbacking_index>* stash_out) const;
  void restore_segments(const ZArraySlice<const ZVirtualMemory>& vmems, const ZArray<zbacking_index>& stash);
};

#endif // SHARE_GC_Z_ZPHYSICALMEMORYMANAGER_HPP
