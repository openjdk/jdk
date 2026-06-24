/*
 * Copyright (c) 2018, 2019, Red Hat, Inc. All rights reserved.
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
#ifndef SHARE_GC_SHENANDOAH_VMSTRUCTS_SHENANDOAH_HPP
#define SHARE_GC_SHENANDOAH_VMSTRUCTS_SHENANDOAH_HPP

#include "gc/shenandoah/shenandoahAllocator.hpp"
#include "gc/shenandoah/shenandoahGeneration.hpp"
#include "gc/shenandoah/shenandoahGenerationalHeap.hpp"
#include "gc/shenandoah/shenandoahHeap.hpp"
#include "gc/shenandoah/shenandoahHeapRegion.hpp"
#include "gc/shenandoah/shenandoahMonitoringSupport.hpp"
#include "gc/shenandoah/shenandoahPartitionAllocator.hpp"
#include "runtime/atomic.hpp"

// Template instantiations of the per-partition allocator, named so vmStructs/SA can refer to the
// concrete embedded sub-objects (ShenandoahAllocator holds one of each by value). Mirrors the
// typedef approach vmStructs_z.hpp uses for ZGC's templated partition types.
typedef ShenandoahPartitionAllocator<ShenandoahFreeSetPartitionId::Mutator>      ShenandoahMutatorPartitionAllocator;
typedef ShenandoahPartitionAllocator<ShenandoahFreeSetPartitionId::Collector>    ShenandoahCollectorPartitionAllocator;
typedef ShenandoahPartitionAllocator<ShenandoahFreeSetPartitionId::OldCollector> ShenandoahOldCollectorPartitionAllocator;

#define VM_STRUCTS_SHENANDOAH(nonstatic_field, volatile_nonstatic_field, static_field)                            \
  nonstatic_field(ShenandoahHeap, _num_regions,                        size_t)                                    \
  nonstatic_field(ShenandoahHeap, _regions,                            ShenandoahHeapRegion**)                    \
  nonstatic_field(ShenandoahHeap, _log_min_obj_alignment_in_bytes,     int)                                       \
  nonstatic_field(ShenandoahHeap, _free_set,                           ShenandoahFreeSet*)                        \
  nonstatic_field(ShenandoahHeap, _allocator,                          ShenandoahAllocator*)                      \
  nonstatic_field(ShenandoahAllocator, _mutator_alloc,                 ShenandoahMutatorPartitionAllocator)       \
  nonstatic_field(ShenandoahAllocator, _collector_alloc,               ShenandoahCollectorPartitionAllocator)     \
  nonstatic_field(ShenandoahAllocator, _old_collector_alloc,           ShenandoahOldCollectorPartitionAllocator)  \
  nonstatic_field(ShenandoahMutatorPartitionAllocator,      _alloc_region_count, const uint)                      \
  nonstatic_field(ShenandoahMutatorPartitionAllocator,      _alloc_regions[0],   Atomic<ShenandoahHeapRegion*>)   \
  nonstatic_field(ShenandoahCollectorPartitionAllocator,    _alloc_region_count, const uint)                      \
  nonstatic_field(ShenandoahCollectorPartitionAllocator,    _alloc_regions[0],   Atomic<ShenandoahHeapRegion*>)   \
  nonstatic_field(ShenandoahOldCollectorPartitionAllocator, _alloc_region_count, const uint)                      \
  nonstatic_field(ShenandoahOldCollectorPartitionAllocator, _alloc_regions[0],   Atomic<ShenandoahHeapRegion*>)   \
  volatile_nonstatic_field(ShenandoahHeap, _committed,                 Atomic<size_t>)                            \
  static_field(ShenandoahHeapRegion, RegionSizeBytes,                  size_t)                                    \
  static_field(ShenandoahHeapRegion, RegionSizeBytesShift,             size_t)                                    \
  volatile_nonstatic_field(ShenandoahHeapRegion, _state,               Atomic<ShenandoahHeapRegion::RegionState>) \
  nonstatic_field(ShenandoahHeapRegion, _index,                        size_t const)                              \
  nonstatic_field(ShenandoahHeapRegion, _bottom,                       HeapWord* const)                           \
  volatile_nonstatic_field(ShenandoahHeapRegion, _top,                 HeapWord*)                                 \
  nonstatic_field(ShenandoahHeapRegion, _atomic_top,                   Atomic<HeapWord*>)                         \
  nonstatic_field(ShenandoahHeapRegion, _end,                          HeapWord* const)                           \
  nonstatic_field(ShenandoahFreeSet, _total_global_used,               size_t)                                    \

#define VM_INT_CONSTANTS_SHENANDOAH(declare_constant, declare_constant_with_value) \
  declare_constant(ShenandoahHeapRegion::_empty_uncommitted)                       \
  declare_constant(ShenandoahHeapRegion::_empty_committed)                         \
  declare_constant(ShenandoahHeapRegion::_regular)                                 \
  declare_constant(ShenandoahHeapRegion::_humongous_start)                         \
  declare_constant(ShenandoahHeapRegion::_humongous_cont)                          \
  declare_constant(ShenandoahHeapRegion::_pinned_humongous_start)                  \
  declare_constant(ShenandoahHeapRegion::_cset)                                    \
  declare_constant(ShenandoahHeapRegion::_pinned)                                  \
  declare_constant(ShenandoahHeapRegion::_pinned_cset)                             \
  declare_constant(ShenandoahHeapRegion::_trash)                                   \

#define VM_TYPES_SHENANDOAH(declare_type,                                     \
                            declare_toplevel_type,                            \
                            declare_integer_type)                             \
  declare_type(ShenandoahHeap, CollectedHeap)                                 \
  declare_type(ShenandoahGenerationalHeap, ShenandoahHeap)                    \
  declare_toplevel_type(ShenandoahHeapRegion)                                 \
  declare_toplevel_type(ShenandoahHeap*)                                      \
  declare_toplevel_type(ShenandoahHeapRegion*)                                \
  declare_toplevel_type(Atomic<ShenandoahHeapRegion::RegionState>)            \
  declare_toplevel_type(Atomic<ShenandoahHeapRegion*>)                        \
  declare_toplevel_type(ShenandoahFreeSet)                                    \
  declare_toplevel_type(ShenandoahFreeSet*)                                   \
  declare_toplevel_type(ShenandoahAllocator)                                  \
  declare_toplevel_type(ShenandoahAllocator*)                                 \
  declare_toplevel_type(ShenandoahMutatorPartitionAllocator)                  \
  declare_toplevel_type(ShenandoahCollectorPartitionAllocator)                \
  declare_toplevel_type(ShenandoahOldCollectorPartitionAllocator)             \

#endif // SHARE_GC_SHENANDOAH_VMSTRUCTS_SHENANDOAH_HPP
