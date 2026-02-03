/*
 * Copyright (c) 2018, 2026, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_SHARED_VMSTRUCTS_GC_HPP
#define SHARE_GC_SHARED_VMSTRUCTS_GC_HPP

#include "gc/shared/ageTable.hpp"
#include "gc/shared/cardTable.hpp"
#include "gc/shared/collectedHeap.hpp"
#include "gc/shared/oopStorage.hpp"
#include "gc/shared/space.hpp"
#if INCLUDE_EPSILONGC
#include "gc/epsilon/vmStructs_epsilon.hpp"
#endif
#if INCLUDE_G1GC
#include "gc/g1/vmStructs_g1.hpp"
#endif
#if INCLUDE_PARALLELGC
#include "gc/parallel/vmStructs_parallelgc.hpp"
#endif
#if INCLUDE_SERIALGC
#include "gc/serial/vmStructs_serial.hpp"
#endif
#if INCLUDE_SHENANDOAHGC
#include "gc/shenandoah/vmStructs_shenandoah.hpp"
#endif
#if INCLUDE_ZGC
#include "gc/z/vmStructs_z.hpp"
#endif
#include "runtime/atomic.hpp"

#define VM_STRUCTS_GC(nonstatic_field,                                                                                               \
                      volatile_static_field,                                                                                         \
                      volatile_nonstatic_field,                                                                                      \
                      static_field,                                                                                                  \
                      unchecked_nonstatic_field)                                                                                     \
  EPSILONGC_ONLY(VM_STRUCTS_EPSILONGC(nonstatic_field,                                                                               \
                                      volatile_nonstatic_field,                                                                      \
                                      static_field))                                                                                 \
  G1GC_ONLY(VM_STRUCTS_G1GC(nonstatic_field,                                                                                         \
                            volatile_nonstatic_field,                                                                                \
                            static_field))                                                                                           \
  PARALLELGC_ONLY(VM_STRUCTS_PARALLELGC(nonstatic_field,                                                                             \
                                        volatile_nonstatic_field,                                                                    \
                                        static_field))                                                                               \
  SERIALGC_ONLY(VM_STRUCTS_SERIALGC(nonstatic_field,                                                                                 \
                                    volatile_nonstatic_field,                                                                        \
                                    static_field))                                                                                   \
  SHENANDOAHGC_ONLY(VM_STRUCTS_SHENANDOAH(nonstatic_field,                                                                           \
                               volatile_nonstatic_field,                                                                             \
                               static_field))                                                                                        \
  ZGC_ONLY(VM_STRUCTS_Z(nonstatic_field,                                                                                             \
                               volatile_nonstatic_field,                                                                             \
                               static_field))                                                                                        \
                                                                                                                                     \
  /**********************************************************************************/                                               \
  /* Generation and Space hierarchies                                               */                                               \
  /**********************************************************************************/                                               \
                                                                                                                                     \
  unchecked_nonstatic_field(AgeTable,          sizes,                                         sizeof(AgeTable::sizes))               \
                                                                                                                                     \
  nonstatic_field(BarrierSet,                  _fake_rtti,                                    BarrierSet::FakeRtti)                  \
                                                                                                                                     \
  nonstatic_field(BarrierSet::FakeRtti,        _concrete_tag,                                 BarrierSet::Name)                      \
                                                                                                                                     \
  nonstatic_field(CardTable,                   _whole_heap,                                   const MemRegion)                       \
  nonstatic_field(CardTable,                   _page_size,                                    const size_t)                          \
  nonstatic_field(CardTable,                   _byte_map_size,                                const size_t)                          \
  nonstatic_field(CardTable,                   _byte_map,                                     CardTable::CardValue*)                 \
  nonstatic_field(CardTable,                   _byte_map_base,                                CardTable::CardValue*)                 \
  nonstatic_field(CardTableBarrierSet,         _card_table,                                   Atomic<CardTable*>)                    \
                                                                                                                                     \
     static_field(CollectedHeap,               _lab_alignment_reserve,                        size_t)                                \
  nonstatic_field(CollectedHeap,               _reserved,                                     MemRegion)                             \
  nonstatic_field(CollectedHeap,               _is_stw_gc_active,                             bool)                                  \
  nonstatic_field(CollectedHeap,               _total_collections,                            unsigned int)                          \
                                                                                                                                     \
  nonstatic_field(ContiguousSpace,             _bottom,                                       HeapWord*)                             \
  nonstatic_field(ContiguousSpace,             _end,                                          HeapWord*)                             \
  nonstatic_field(ContiguousSpace,             _top,                                          Atomic<HeapWord*>)                     \
                                                                                                                                     \
  nonstatic_field(MemRegion,                   _start,                                        HeapWord*)                             \
  nonstatic_field(MemRegion,                   _word_size,                                    size_t)

#define VM_TYPES_GC(declare_type,                                         \
                    declare_toplevel_type,                                \
                    declare_integer_type)                                 \
  EPSILONGC_ONLY(VM_TYPES_EPSILONGC(declare_type,                         \
                                    declare_toplevel_type,                \
                                    declare_integer_type))                \
  G1GC_ONLY(VM_TYPES_G1GC(declare_type,                                   \
                          declare_toplevel_type,                          \
                          declare_integer_type))                          \
  PARALLELGC_ONLY(VM_TYPES_PARALLELGC(declare_type,                       \
                                      declare_toplevel_type,              \
                                      declare_integer_type))              \
  SERIALGC_ONLY(VM_TYPES_SERIALGC(declare_type,                           \
                                  declare_toplevel_type,                  \
                                  declare_integer_type))                  \
  SHENANDOAHGC_ONLY(VM_TYPES_SHENANDOAH(declare_type,                     \
                             declare_toplevel_type,                       \
                             declare_integer_type))                       \
  ZGC_ONLY(VM_TYPES_Z(declare_type,                                       \
                             declare_toplevel_type,                       \
                             declare_integer_type))                       \
                                                                          \
  /******************************************/                            \
  /* Generation and space hierarchies       */                            \
  /* (needed for run-time type information) */                            \
  /******************************************/                            \
                                                                          \
  declare_toplevel_type(CollectedHeap)                                    \
  declare_toplevel_type(ContiguousSpace)                                  \
  declare_toplevel_type(BarrierSet)                                       \
           declare_type(CardTableBarrierSet,             BarrierSet)      \
  declare_toplevel_type(CardTable)                                        \
  declare_toplevel_type(BarrierSet::Name)                                 \
                                                                          \
  /* Miscellaneous other GC types */                                      \
                                                                          \
  declare_toplevel_type(AgeTable)                                         \
  declare_toplevel_type(CardTable::CardValue)                             \
  declare_toplevel_type(HeapWord)                                         \
  declare_toplevel_type(MemRegion)                                        \
  declare_toplevel_type(ThreadLocalAllocBuffer)                           \
  declare_toplevel_type(VirtualSpace)                                     \
                                                                          \
  /* Pointers to Garbage Collection types */                              \
                                                                          \
  declare_toplevel_type(BarrierSet*)                                      \
  declare_toplevel_type(CardTable*)                                       \
  declare_toplevel_type(Atomic<CardTable*>)                               \
  declare_toplevel_type(CardTable*const)                                  \
  declare_toplevel_type(CardTableBarrierSet*)                             \
  declare_toplevel_type(CardTableBarrierSet**)                            \
  declare_toplevel_type(CollectedHeap*)                                   \
  declare_toplevel_type(ContiguousSpace*)                                 \
  declare_toplevel_type(HeapWord*)                                        \
  declare_toplevel_type(HeapWord* volatile)                               \
  declare_toplevel_type(MemRegion*)                                       \
  declare_toplevel_type(ThreadLocalAllocBuffer*)                          \
                                                                          \
  declare_toplevel_type(BarrierSet::FakeRtti)

#define VM_INT_CONSTANTS_GC(declare_constant,                               \
                            declare_constant_with_value)                    \
  EPSILONGC_ONLY(VM_INT_CONSTANTS_EPSILONGC(declare_constant,               \
                                            declare_constant_with_value))   \
  G1GC_ONLY(VM_INT_CONSTANTS_G1GC(declare_constant,                         \
                                  declare_constant_with_value))             \
  PARALLELGC_ONLY(VM_INT_CONSTANTS_PARALLELGC(declare_constant,             \
                                              declare_constant_with_value)) \
  SERIALGC_ONLY(VM_INT_CONSTANTS_SERIALGC(declare_constant,                 \
                                          declare_constant_with_value))     \
  SHENANDOAHGC_ONLY(VM_INT_CONSTANTS_SHENANDOAH(declare_constant,           \
                                     declare_constant_with_value))          \
  ZGC_ONLY(VM_INT_CONSTANTS_Z(declare_constant,                             \
                                     declare_constant_with_value))          \
                                                                            \
  /********************************************/                            \
  /* Generation and Space Hierarchy Constants */                            \
  /********************************************/                            \
                                                                            \
  declare_constant(AgeTable::table_size)                                    \
                                                                            \
  declare_constant(BarrierSet::CardTableBarrierSet)                         \
                                                                            \
  declare_constant(BOTConstants::LogBase)                                   \
  declare_constant(BOTConstants::Base)                                      \
  declare_constant(BOTConstants::N_powers)                                  \
                                                                            \
  declare_constant(CardTable::clean_card)                                   \
  declare_constant(CardTable::dirty_card)                                   \
                                                                            \
  declare_constant(CollectedHeap::Serial)                                   \
  declare_constant(CollectedHeap::Parallel)                                 \
  declare_constant(CollectedHeap::G1)                                       \

#define VM_LONG_CONSTANTS_GC(declare_constant)                              \
  ZGC_ONLY(VM_LONG_CONSTANTS_Z(declare_constant))

#endif // SHARE_GC_SHARED_VMSTRUCTS_GC_HPP
