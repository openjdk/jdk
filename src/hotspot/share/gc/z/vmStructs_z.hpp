/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_Z_VMSTRUCTS_Z_HPP
#define SHARE_GC_Z_VMSTRUCTS_Z_HPP

#include "gc/z/zAttachedArray.hpp"
#include "gc/z/zCollectedHeap.hpp"
#include "gc/z/zForwarding.hpp"
#include "gc/z/zGranuleMap.hpp"
#include "gc/z/zHeap.hpp"
#include "gc/z/zNUMA.hpp"
#include "gc/z/zPageAllocator.hpp"
#include "gc/z/zPageType.hpp"
#include "gc/z/zValue.hpp"
#include "gc/z/zVirtualMemory.hpp"
#include "utilities/macros.hpp"

// Expose some ZGC globals to the SA agent.
class ZGlobalsForVMStructs {
  static ZGlobalsForVMStructs _instance;

public:
  static ZGlobalsForVMStructs* _instance_p;

  ZGlobalsForVMStructs();

  uintptr_t* _ZAddressOffsetMask;

  uintptr_t* _ZPointerLoadGoodMask;
  uintptr_t* _ZPointerLoadBadMask;
  size_t*    _ZPointerLoadShift;

  uintptr_t* _ZPointerMarkGoodMask;
  uintptr_t* _ZPointerMarkBadMask;

  uintptr_t* _ZPointerStoreGoodMask;
  uintptr_t* _ZPointerStoreBadMask;

  const int* _ZObjectAlignmentSmallShift;
  const int* _ZObjectAlignmentSmall;
};

typedef ZGranuleMap<ZPage*> ZGranuleMapForPageTable;
typedef ZGranuleMap<ZForwarding*> ZGranuleMapForForwarding;
typedef ZAttachedArray<ZForwarding, ZForwardingEntry> ZAttachedArrayForForwarding;
typedef ZValue<ZPerNUMAStorage, ZPartition> ZPerNUMAZPartition;

#define VM_STRUCTS_Z(nonstatic_field, volatile_nonstatic_field, static_field)                        \
  static_field(ZGlobalsForVMStructs,            _instance_p,          ZGlobalsForVMStructs*)         \
                                                                                                     \
  nonstatic_field(ZGlobalsForVMStructs,         _ZAddressOffsetMask,         uintptr_t*)             \
  nonstatic_field(ZGlobalsForVMStructs,         _ZPointerLoadGoodMask,       uintptr_t*)             \
  nonstatic_field(ZGlobalsForVMStructs,         _ZPointerLoadBadMask,        uintptr_t*)             \
  nonstatic_field(ZGlobalsForVMStructs,         _ZPointerLoadShift,          size_t*)                \
  nonstatic_field(ZGlobalsForVMStructs,         _ZPointerMarkGoodMask,       uintptr_t*)             \
  nonstatic_field(ZGlobalsForVMStructs,         _ZPointerMarkBadMask,        uintptr_t*)             \
  nonstatic_field(ZGlobalsForVMStructs,         _ZPointerStoreGoodMask,      uintptr_t*)             \
  nonstatic_field(ZGlobalsForVMStructs,         _ZPointerStoreBadMask,       uintptr_t*)             \
  nonstatic_field(ZGlobalsForVMStructs,         _ZObjectAlignmentSmallShift, const int*)             \
  nonstatic_field(ZGlobalsForVMStructs,         _ZObjectAlignmentSmall,      const int*)             \
                                                                                                     \
  nonstatic_field(ZCollectedHeap,               _heap,                ZHeap)                         \
                                                                                                     \
  nonstatic_field(ZHeap,                        _page_allocator,      ZPageAllocator)                \
  nonstatic_field(ZHeap,                        _page_table,          ZPageTable)                    \
                                                                                                     \
  nonstatic_field(ZPage,                        _type,                const ZPageType)               \
  volatile_nonstatic_field(ZPage,               _seqnum,              uint32_t)                      \
  nonstatic_field(ZPage,                        _virtual,             const ZVirtualMemory)          \
  volatile_nonstatic_field(ZPage,               _top,                 zoffset_end)                   \
                                                                                                     \
  nonstatic_field(ZPageAllocator,               _max_capacity,        const size_t)                  \
  nonstatic_field(ZPageAllocator,               _partitions,          ZPerNUMAZPartition)            \
                                                                                                     \
  static_field(ZNUMA,                           _count,               uint32_t)                      \
  nonstatic_field(ZPerNUMAZPartition,           _addr,                const uintptr_t)               \
                                                                                                     \
  volatile_nonstatic_field(ZPartition,          _capacity,            size_t)                        \
  nonstatic_field(ZPartition,                   _used,                size_t)                        \
                                                                                                     \
  nonstatic_field(ZPageTable,                   _map,                 ZGranuleMapForPageTable)       \
                                                                                                     \
  nonstatic_field(ZGranuleMapForPageTable,      _map,                 ZPage** const)                 \
  nonstatic_field(ZGranuleMapForForwarding,     _map,                 ZForwarding** const)           \
                                                                                                     \
  nonstatic_field(ZForwardingTable,             _map,                 ZGranuleMapForForwarding)      \
                                                                                                     \
  nonstatic_field(ZVirtualMemory,               _start,               const zoffset_end)             \
  nonstatic_field(ZVirtualMemory,               _size,                const size_t)                  \
                                                                                                     \
  nonstatic_field(ZForwarding,                  _virtual,             const ZVirtualMemory)          \
  nonstatic_field(ZForwarding,                  _object_alignment_shift, const size_t)               \
  volatile_nonstatic_field(ZForwarding,         _ref_count,           int)                           \
  nonstatic_field(ZForwarding,                  _entries,             const ZAttachedArrayForForwarding) \
  nonstatic_field(ZForwardingEntry,             _entry,               uint64_t)                      \
  nonstatic_field(ZAttachedArrayForForwarding,  _length,              const size_t)

#define VM_INT_CONSTANTS_Z(declare_constant, declare_constant_with_value)                            \
  declare_constant(ZPageType::small)                                                                 \
  declare_constant(ZPageType::medium)                                                                \
  declare_constant(ZPageType::large)                                                                 \
  declare_constant(ZPageSizeSmallShift)                                                              \
  declare_constant(ZPageSizeMediumMaxShift)                                                          \
  declare_constant(ZObjectAlignmentMediumShift)                                                      \
  declare_constant(ZObjectAlignmentLargeShift)

#define VM_LONG_CONSTANTS_Z(declare_constant)                                                        \
  declare_constant(ZGranuleSizeShift)                                                                \
  declare_constant(ZAddressOffsetShift)                                                              \
  declare_constant(ZAddressOffsetBits)                                                               \
  declare_constant(ZAddressOffsetMask)                                                               \
  declare_constant(ZAddressOffsetMax)

#define VM_TYPES_Z(declare_type, declare_toplevel_type, declare_integer_type)                        \
  declare_toplevel_type(zoffset)                                                                     \
  declare_toplevel_type(zoffset_end)                                                                 \
  declare_toplevel_type(ZGlobalsForVMStructs)                                                        \
  declare_type(ZCollectedHeap, CollectedHeap)                                                        \
  declare_toplevel_type(ZHeap)                                                                       \
  declare_toplevel_type(ZRelocate)                                                                   \
  declare_toplevel_type(ZPage)                                                                       \
  declare_toplevel_type(ZPageType)                                                                   \
  declare_toplevel_type(ZPageAllocator)                                                              \
  declare_toplevel_type(ZPageTable)                                                                  \
  declare_toplevel_type(ZPartition)                                                                  \
  declare_toplevel_type(ZNUMA)                                                                       \
  declare_toplevel_type(ZPerNUMAZPartition)                                                          \
  declare_toplevel_type(ZAttachedArrayForForwarding)                                                 \
  declare_toplevel_type(ZGranuleMapForPageTable)                                                     \
  declare_toplevel_type(ZGranuleMapForForwarding)                                                    \
  declare_toplevel_type(ZVirtualMemory)                                                              \
  declare_toplevel_type(ZForwardingTable)                                                            \
  declare_toplevel_type(ZForwarding)                                                                 \
  declare_toplevel_type(ZForwardingEntry)                                                            \
  declare_toplevel_type(ZPhysicalMemoryManager)

#endif // SHARE_GC_Z_VMSTRUCTS_Z_HPP
