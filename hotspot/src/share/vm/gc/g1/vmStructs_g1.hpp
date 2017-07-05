/*
 * Copyright (c) 2011, 2015, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_G1_VMSTRUCTS_G1_HPP
#define SHARE_VM_GC_G1_VMSTRUCTS_G1_HPP

#include "gc/g1/g1CollectedHeap.hpp"
#include "gc/g1/heapRegion.hpp"
#include "gc/g1/heapRegionManager.hpp"
#include "utilities/macros.hpp"

#define VM_STRUCTS_G1(nonstatic_field, static_field)                          \
                                                                              \
  static_field(HeapRegion, GrainBytes,        size_t)                         \
  static_field(HeapRegion, LogOfHRGrainBytes, int)                            \
                                                                              \
  nonstatic_field(G1OffsetTableContigSpace, _top,       HeapWord* volatile)   \
                                                                              \
  nonstatic_field(G1HeapRegionTable, _base,             address)              \
  nonstatic_field(G1HeapRegionTable, _length,           size_t)               \
  nonstatic_field(G1HeapRegionTable, _biased_base,      address)              \
  nonstatic_field(G1HeapRegionTable, _bias,             size_t)               \
  nonstatic_field(G1HeapRegionTable, _shift_by,         uint)                 \
                                                                              \
  nonstatic_field(HeapRegionManager, _regions,          G1HeapRegionTable)    \
  nonstatic_field(HeapRegionManager, _num_committed,    uint)                 \
                                                                              \
  nonstatic_field(G1CollectedHeap, _summary_bytes_used, size_t)               \
  nonstatic_field(G1CollectedHeap, _hrm,                HeapRegionManager)    \
  nonstatic_field(G1CollectedHeap, _g1mm,               G1MonitoringSupport*) \
  nonstatic_field(G1CollectedHeap, _old_set,            HeapRegionSetBase)    \
  nonstatic_field(G1CollectedHeap, _humongous_set,      HeapRegionSetBase)    \
                                                                              \
  nonstatic_field(G1MonitoringSupport, _eden_committed,     size_t)           \
  nonstatic_field(G1MonitoringSupport, _eden_used,          size_t)           \
  nonstatic_field(G1MonitoringSupport, _survivor_committed, size_t)           \
  nonstatic_field(G1MonitoringSupport, _survivor_used,      size_t)           \
  nonstatic_field(G1MonitoringSupport, _old_committed,      size_t)           \
  nonstatic_field(G1MonitoringSupport, _old_used,           size_t)           \
                                                                              \
  nonstatic_field(HeapRegionSetBase,   _count,          HeapRegionSetCount)   \
                                                                              \
  nonstatic_field(HeapRegionSetCount,  _length,         uint)                 \
  nonstatic_field(HeapRegionSetCount,  _capacity,       size_t)               \
                                                                              \
  nonstatic_field(PtrQueue,            _active,         bool)                 \
  nonstatic_field(PtrQueue,            _buf,            void**)               \
  nonstatic_field(PtrQueue,            _index,          size_t)               \


#define VM_INT_CONSTANTS_G1(declare_constant, declare_constant_with_value)    \
                                                                              \
  JVMCI_ONLY(                                                                 \
    declare_constant_with_value(                                              \
      "dirtyCardQueueBufferOffset",                                           \
      in_bytes(DirtyCardQueue::byte_offset_of_buf()))                         \
    declare_constant_with_value(                                              \
      "dirtyCardQueueIndexOffset",                                            \
      in_bytes(DirtyCardQueue::byte_offset_of_index()))                       \
    ) /* JVMCI_ONLY */                                                        \
                                                                              \
  JVMCI_ONLY(                                                                 \
    declare_constant_with_value(                                              \
      "satbMarkQueueBufferOffset",                                            \
      in_bytes(SATBMarkQueue::byte_offset_of_buf()))                          \
    declare_constant_with_value(                                              \
      "satbMarkQueueIndexOffset",                                             \
      in_bytes(SATBMarkQueue::byte_offset_of_index()))                        \
    declare_constant_with_value(                                              \
      "satbMarkQueueActiveOffset",                                            \
      in_bytes(SATBMarkQueue::byte_offset_of_active()))                       \
    ) /* JVMCI_ONLY */                                                        \


#define VM_TYPES_G1(declare_type, declare_toplevel_type)                      \
                                                                              \
  declare_toplevel_type(G1HeapRegionTable)                                    \
                                                                              \
  declare_type(G1CollectedHeap, CollectedHeap)                                \
                                                                              \
  declare_type(G1OffsetTableContigSpace, CompactibleSpace)                    \
  declare_type(HeapRegion, G1OffsetTableContigSpace)                          \
  declare_toplevel_type(HeapRegionManager)                                    \
  declare_toplevel_type(HeapRegionSetBase)                                    \
  declare_toplevel_type(HeapRegionSetCount)                                   \
  declare_toplevel_type(G1MonitoringSupport)                                  \
  declare_toplevel_type(PtrQueue)                                             \
                                                                              \
  declare_toplevel_type(G1CollectedHeap*)                                     \
  declare_toplevel_type(HeapRegion*)                                          \
  declare_toplevel_type(G1MonitoringSupport*)                                 \

#endif // SHARE_VM_GC_G1_VMSTRUCTS_G1_HPP
