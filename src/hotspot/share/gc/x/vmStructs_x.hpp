/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_X_VMSTRUCTS_X_HPP
#define SHARE_GC_X_VMSTRUCTS_X_HPP

#include "gc/x/xAttachedArray.hpp"
#include "gc/x/xCollectedHeap.hpp"
#include "gc/x/xForwarding.hpp"
#include "gc/x/xGranuleMap.hpp"
#include "gc/x/xHeap.hpp"
#include "gc/x/xPageAllocator.hpp"
#include "utilities/macros.hpp"

// Expose some ZGC globals to the SA agent.
class XGlobalsForVMStructs {
  static XGlobalsForVMStructs _instance;

public:
  static XGlobalsForVMStructs* _instance_p;

  XGlobalsForVMStructs();

  uint32_t* _XGlobalPhase;

  uint32_t* _XGlobalSeqNum;

  uintptr_t* _XAddressOffsetMask;
  uintptr_t* _XAddressMetadataMask;
  uintptr_t* _XAddressMetadataFinalizable;
  uintptr_t* _XAddressGoodMask;
  uintptr_t* _XAddressBadMask;
  uintptr_t* _XAddressWeakBadMask;

  const int* _XObjectAlignmentSmallShift;
  const int* _XObjectAlignmentSmall;
};

typedef XGranuleMap<XPage*> XGranuleMapForPageTable;
typedef XGranuleMap<XForwarding*> XGranuleMapForForwarding;
typedef XAttachedArray<XForwarding, XForwardingEntry> XAttachedArrayForForwarding;

#define VM_STRUCTS_X(nonstatic_field, volatile_nonstatic_field, static_field)                            \
  static_field(XGlobalsForVMStructs,            _instance_p,          XGlobalsForVMStructs*)             \
  nonstatic_field(XGlobalsForVMStructs,         _XGlobalPhase,        uint32_t*)                         \
  nonstatic_field(XGlobalsForVMStructs,         _XGlobalSeqNum,       uint32_t*)                         \
  nonstatic_field(XGlobalsForVMStructs,         _XAddressOffsetMask,  uintptr_t*)                        \
  nonstatic_field(XGlobalsForVMStructs,         _XAddressMetadataMask, uintptr_t*)                       \
  nonstatic_field(XGlobalsForVMStructs,         _XAddressMetadataFinalizable, uintptr_t*)                \
  nonstatic_field(XGlobalsForVMStructs,         _XAddressGoodMask,    uintptr_t*)                        \
  nonstatic_field(XGlobalsForVMStructs,         _XAddressBadMask,     uintptr_t*)                        \
  nonstatic_field(XGlobalsForVMStructs,         _XAddressWeakBadMask, uintptr_t*)                        \
  nonstatic_field(XGlobalsForVMStructs,         _XObjectAlignmentSmallShift, const int*)                 \
  nonstatic_field(XGlobalsForVMStructs,         _XObjectAlignmentSmall, const int*)                      \
                                                                                                         \
  nonstatic_field(XCollectedHeap,               _heap,                XHeap)                             \
                                                                                                         \
  nonstatic_field(XHeap,                        _page_allocator,      XPageAllocator)                    \
  nonstatic_field(XHeap,                        _page_table,          XPageTable)                        \
  nonstatic_field(XHeap,                        _forwarding_table,    XForwardingTable)                  \
  nonstatic_field(XHeap,                        _relocate,            XRelocate)                         \
                                                                                                         \
  nonstatic_field(XPage,                        _type,                const uint8_t)                     \
  nonstatic_field(XPage,                        _seqnum,              uint32_t)                          \
  nonstatic_field(XPage,                        _virtual,             const XVirtualMemory)              \
  volatile_nonstatic_field(XPage,               _top,                 uintptr_t)                         \
                                                                                                         \
  nonstatic_field(XPageAllocator,               _max_capacity,        const size_t)                      \
  volatile_nonstatic_field(XPageAllocator,      _capacity,            size_t)                            \
  volatile_nonstatic_field(XPageAllocator,      _used,                size_t)                            \
                                                                                                         \
  nonstatic_field(XPageTable,                   _map,                 XGranuleMapForPageTable)           \
                                                                                                         \
  nonstatic_field(XGranuleMapForPageTable,      _map,                 XPage** const)                     \
  nonstatic_field(XGranuleMapForForwarding,     _map,                 XForwarding** const)               \
                                                                                                         \
  nonstatic_field(XForwardingTable,             _map,                 XGranuleMapForForwarding)          \
                                                                                                         \
  nonstatic_field(XVirtualMemory,               _start,               const uintptr_t)                   \
  nonstatic_field(XVirtualMemory,               _end,                 const uintptr_t)                   \
                                                                                                         \
  nonstatic_field(XForwarding,                  _virtual,             const XVirtualMemory)              \
  nonstatic_field(XForwarding,                  _object_alignment_shift, const size_t)                   \
  volatile_nonstatic_field(XForwarding,         _ref_count,           int)                               \
  nonstatic_field(XForwarding,                  _entries,             const XAttachedArrayForForwarding) \
  nonstatic_field(XForwardingEntry,             _entry,               uint64_t)                          \
  nonstatic_field(XAttachedArrayForForwarding,  _length,              const size_t)

#define VM_INT_CONSTANTS_X(declare_constant, declare_constant_with_value)                                \
  declare_constant(XPhaseRelocate)                                                                       \
  declare_constant(XPageTypeSmall)                                                                       \
  declare_constant(XPageTypeMedium)                                                                      \
  declare_constant(XPageTypeLarge)                                                                       \
  declare_constant(XObjectAlignmentMediumShift)                                                          \
  declare_constant(XObjectAlignmentLargeShift)

#define VM_LONG_CONSTANTS_X(declare_constant)                                                            \
  declare_constant(XGranuleSizeShift)                                                                    \
  declare_constant(XPageSizeSmallShift)                                                                  \
  declare_constant(XPageSizeMediumShift)                                                                 \
  declare_constant(XAddressOffsetShift)                                                                  \
  declare_constant(XAddressOffsetBits)                                                                   \
  declare_constant(XAddressOffsetMask)                                                                   \
  declare_constant(XAddressOffsetMax)

#define VM_TYPES_X(declare_type, declare_toplevel_type, declare_integer_type)                            \
  declare_toplevel_type(XGlobalsForVMStructs)                                                            \
  declare_type(XCollectedHeap, CollectedHeap)                                                            \
  declare_toplevel_type(XHeap)                                                                           \
  declare_toplevel_type(XRelocate)                                                                       \
  declare_toplevel_type(XPage)                                                                           \
  declare_toplevel_type(XPageAllocator)                                                                  \
  declare_toplevel_type(XPageTable)                                                                      \
  declare_toplevel_type(XAttachedArrayForForwarding)                                                     \
  declare_toplevel_type(XGranuleMapForPageTable)                                                         \
  declare_toplevel_type(XGranuleMapForForwarding)                                                        \
  declare_toplevel_type(XVirtualMemory)                                                                  \
  declare_toplevel_type(XForwardingTable)                                                                \
  declare_toplevel_type(XForwarding)                                                                     \
  declare_toplevel_type(XForwardingEntry)                                                                \
  declare_toplevel_type(XPhysicalMemoryManager)

#endif // SHARE_GC_X_VMSTRUCTS_X_HPP
