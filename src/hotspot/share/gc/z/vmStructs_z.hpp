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

#ifndef SHARE_VM_GC_Z_VMSTRUCTS_Z_HPP
#define SHARE_VM_GC_Z_VMSTRUCTS_Z_HPP

#include "gc/z/zAddressRangeMap.hpp"
#include "gc/z/zCollectedHeap.hpp"
#include "gc/z/zHeap.hpp"
#include "gc/z/zPageAllocator.hpp"
#include "gc/z/zPhysicalMemory.hpp"
#include "utilities/macros.hpp"

// Expose some ZGC globals to the SA agent.
class ZGlobalsForVMStructs {
  static ZGlobalsForVMStructs _instance;

public:
  static ZGlobalsForVMStructs* _instance_p;

  ZGlobalsForVMStructs();

  uint32_t* _ZGlobalPhase;

  uintptr_t* _ZAddressGoodMask;
  uintptr_t* _ZAddressBadMask;
  uintptr_t* _ZAddressWeakBadMask;

  const int* _ZObjectAlignmentSmallShift;
  const int* _ZObjectAlignmentSmall;
};

typedef ZAddressRangeMap<ZPageTableEntry, ZPageSizeMinShift> ZAddressRangeMapForPageTable;

#define VM_STRUCTS_ZGC(nonstatic_field, volatile_nonstatic_field, static_field)                      \
  static_field(ZGlobalsForVMStructs,            _instance_p,          ZGlobalsForVMStructs*)         \
  nonstatic_field(ZGlobalsForVMStructs,         _ZGlobalPhase,        uint32_t*)                     \
  nonstatic_field(ZGlobalsForVMStructs,         _ZAddressGoodMask,    uintptr_t*)                    \
  nonstatic_field(ZGlobalsForVMStructs,         _ZAddressBadMask,     uintptr_t*)                    \
  nonstatic_field(ZGlobalsForVMStructs,         _ZAddressWeakBadMask, uintptr_t*)                    \
  nonstatic_field(ZGlobalsForVMStructs,         _ZObjectAlignmentSmallShift, const int*)             \
  nonstatic_field(ZGlobalsForVMStructs,         _ZObjectAlignmentSmall, const int*)                  \
                                                                                                     \
  nonstatic_field(ZCollectedHeap,               _heap,                ZHeap)                         \
                                                                                                     \
  nonstatic_field(ZHeap,                        _page_allocator,      ZPageAllocator)                \
  nonstatic_field(ZHeap,                        _pagetable,           ZPageTable)                    \
                                                                                                     \
  nonstatic_field(ZPage,                        _type,                const uint8_t)                 \
  nonstatic_field(ZPage,                        _virtual,             const ZVirtualMemory)          \
  nonstatic_field(ZPage,                        _forwarding,          ZForwardingTable)              \
                                                                                                     \
  nonstatic_field(ZPageAllocator,               _physical,            ZPhysicalMemoryManager)        \
  nonstatic_field(ZPageAllocator,               _used,                size_t)                        \
                                                                                                     \
  nonstatic_field(ZPageTable,                   _map,                 ZAddressRangeMapForPageTable)  \
                                                                                                     \
  nonstatic_field(ZAddressRangeMapForPageTable, _map,                 ZPageTableEntry* const)        \
                                                                                                     \
  nonstatic_field(ZVirtualMemory,                _start,              uintptr_t)                     \
  nonstatic_field(ZVirtualMemory,                _end,                uintptr_t)                     \
                                                                                                     \
  nonstatic_field(ZForwardingTable,              _table,              ZForwardingTableEntry*)        \
  nonstatic_field(ZForwardingTable,              _size,               size_t)                        \
                                                                                                     \
  nonstatic_field(ZPhysicalMemoryManager,        _max_capacity,       const size_t)                  \
  nonstatic_field(ZPhysicalMemoryManager,        _capacity,           size_t)

#define VM_INT_CONSTANTS_ZGC(declare_constant, declare_constant_with_value)                          \
  declare_constant(ZPhaseRelocate)                                                                   \
  declare_constant(ZPageTypeSmall)                                                                   \
  declare_constant(ZPageTypeMedium)                                                                  \
  declare_constant(ZPageTypeLarge)                                                                   \
  declare_constant(ZObjectAlignmentMediumShift)                                                      \
  declare_constant(ZObjectAlignmentLargeShift)

#define VM_LONG_CONSTANTS_ZGC(declare_constant)                                                      \
  declare_constant(ZPageSizeSmallShift)                                                              \
  declare_constant(ZPageSizeMediumShift)                                                             \
  declare_constant(ZPageSizeMinShift)                                                                \
  declare_constant(ZAddressOffsetShift)                                                              \
  declare_constant(ZAddressOffsetBits)                                                               \
  declare_constant(ZAddressOffsetMask)                                                               \
  declare_constant(ZAddressSpaceStart)

#define VM_TYPES_ZGC(declare_type, declare_toplevel_type, declare_integer_type)                      \
  declare_toplevel_type(ZGlobalsForVMStructs)                                                        \
  declare_type(ZCollectedHeap, CollectedHeap)                                                        \
  declare_toplevel_type(ZHeap)                                                                       \
  declare_toplevel_type(ZPage)                                                                       \
  declare_toplevel_type(ZPageAllocator)                                                              \
  declare_toplevel_type(ZPageTable)                                                                  \
  declare_toplevel_type(ZPageTableEntry)                                                             \
  declare_toplevel_type(ZAddressRangeMapForPageTable)                                                \
  declare_toplevel_type(ZVirtualMemory)                                                              \
  declare_toplevel_type(ZForwardingTable)                                                            \
  declare_toplevel_type(ZForwardingTableEntry)                                                       \
  declare_toplevel_type(ZPhysicalMemoryManager)

#endif // SHARE_VM_GC_Z_VMSTRUCTS_Z_HPP
