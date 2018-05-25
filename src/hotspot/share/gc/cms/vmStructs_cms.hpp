/*
 * Copyright (c) 2007, 2017, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_CMS_VMSTRUCTS_CMS_HPP
#define SHARE_VM_GC_CMS_VMSTRUCTS_CMS_HPP

#include "gc/cms/cmsHeap.hpp"
#include "gc/cms/compactibleFreeListSpace.hpp"
#include "gc/cms/concurrentMarkSweepGeneration.hpp"
#include "gc/cms/concurrentMarkSweepThread.hpp"
#include "gc/cms/parNewGeneration.hpp"

#define VM_STRUCTS_CMSGC(nonstatic_field,                                                                                            \
                         volatile_nonstatic_field,                                                                                   \
                         static_field)                                                                                               \
  nonstatic_field(CompactibleFreeListSpace,    _collector,                                    CMSCollector*)                         \
  nonstatic_field(CompactibleFreeListSpace,    _bt,                                           BlockOffsetArrayNonContigSpace)        \
     static_field(CompactibleFreeListSpace,    _min_chunk_size_in_bytes,                      size_t)                                \
  nonstatic_field(CMSBitMap,                   _bmStartWord,                                  HeapWord*)                             \
  nonstatic_field(CMSBitMap,                   _bmWordSize,                                   size_t)                                \
  nonstatic_field(CMSBitMap,                   _shifter,                                      const int)                             \
  nonstatic_field(CMSBitMap,                   _bm,                                           BitMapView)                            \
  nonstatic_field(CMSBitMap,                   _virtual_space,                                VirtualSpace)                          \
  nonstatic_field(CMSCollector,                _markBitMap,                                   CMSBitMap)                             \
  nonstatic_field(ConcurrentMarkSweepGeneration, _cmsSpace,                                   CompactibleFreeListSpace*)             \
     static_field(ConcurrentMarkSweepThread,   _collector,                                    CMSCollector*)                         \
  nonstatic_field(LinearAllocBlock,            _word_size,                                    size_t)                                \
  nonstatic_field(AFLBinaryTreeDictionary,     _total_size,                                   size_t)                                \
  nonstatic_field(CompactibleFreeListSpace,    _dictionary,                                   AFLBinaryTreeDictionary*)              \
  nonstatic_field(CompactibleFreeListSpace,    _indexedFreeList[0],                           AdaptiveFreeList<FreeChunk>)           \
  nonstatic_field(CompactibleFreeListSpace,    _smallLinearAllocBlock,                        LinearAllocBlock)                      \
  volatile_nonstatic_field(FreeChunk,          _size,                                         size_t)                                \
  nonstatic_field(FreeChunk,                   _next,                                         FreeChunk*)                            \
  nonstatic_field(FreeChunk,                   _prev,                                         FreeChunk*)                            \
  nonstatic_field(AdaptiveFreeList<FreeChunk>, _size,                                         size_t)                                \
  nonstatic_field(AdaptiveFreeList<FreeChunk>, _count,                                        ssize_t)



#define VM_TYPES_CMSGC(declare_type,                                      \
                       declare_toplevel_type,                             \
                       declare_integer_type)                              \
                                                                          \
           declare_type(CMSHeap,                      GenCollectedHeap)   \
           declare_type(ConcurrentMarkSweepGeneration,CardGeneration)     \
           declare_type(ParNewGeneration,             DefNewGeneration)   \
           declare_type(CompactibleFreeListSpace,     CompactibleSpace)   \
           declare_type(ConcurrentMarkSweepThread,    NamedThread)        \
  declare_toplevel_type(CMSCollector)                                     \
  declare_toplevel_type(CMSBitMap)                                        \
  declare_toplevel_type(FreeChunk)                                        \
  declare_toplevel_type(metaspace::Metablock)                             \
  declare_toplevel_type(ConcurrentMarkSweepThread*)                       \
  declare_toplevel_type(ConcurrentMarkSweepGeneration*)                   \
  declare_toplevel_type(CompactibleFreeListSpace*)                        \
  declare_toplevel_type(CMSCollector*)                                    \
  declare_toplevel_type(AFLBinaryTreeDictionary)                          \
  declare_toplevel_type(LinearAllocBlock)                                 \
  declare_toplevel_type(FreeChunk*)                                       \
  declare_toplevel_type(AdaptiveFreeList<FreeChunk>*)                     \
  declare_toplevel_type(AdaptiveFreeList<FreeChunk>)


#define VM_INT_CONSTANTS_CMSGC(declare_constant,                          \
                               declare_constant_with_value)               \
  declare_constant(CompactibleFreeListSpace::IndexSetSize)                \
  declare_constant(Generation::ConcurrentMarkSweep)                       \
  declare_constant(Generation::ParNew)

#endif // SHARE_VM_GC_CMS_VMSTRUCTS_CMS_HPP
