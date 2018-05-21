/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
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
#ifndef SHARE_VM_MEMORY_METACHUNK_HPP
#define SHARE_VM_MEMORY_METACHUNK_HPP

#include "memory/metaspace/metabase.hpp"
#include "memory/metaspace/metaspaceCommon.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"

class MetachunkTest;

namespace metaspace {

class VirtualSpaceNode;

//  Metachunk - Quantum of allocation from a Virtualspace
//    Metachunks are reused (when freed are put on a global freelist) and
//    have no permanent association to a SpaceManager.

//            +--------------+ <- end    --+       --+
//            |              |             |         |
//            |              |             | free    |
//            |              |             |         |
//            |              |             |         | size | capacity
//            |              |             |         |
//            |              | <- top   -- +         |
//            |              |             |         |
//            |              |             | used    |
//            |              |             |         |
//            |              |             |         |
//            +--------------+ <- bottom --+       --+

enum ChunkOrigin {
  // Chunk normally born (via take_from_committed)
  origin_normal = 1,
  // Chunk was born as padding chunk
  origin_pad = 2,
  // Chunk was born as leftover chunk in VirtualSpaceNode::retire
  origin_leftover = 3,
  // Chunk was born as result of a merge of smaller chunks
  origin_merge = 4,
  // Chunk was born as result of a split of a larger chunk
  origin_split = 5,

  origin_minimum = origin_normal,
  origin_maximum = origin_split,
  origins_count = origin_maximum + 1
};

inline bool is_valid_chunkorigin(ChunkOrigin origin) {
  return origin == origin_normal ||
    origin == origin_pad ||
    origin == origin_leftover ||
    origin == origin_merge ||
    origin == origin_split;
}

class Metachunk : public Metabase<Metachunk> {

  friend class ::MetachunkTest;

  // The VirtualSpaceNode containing this chunk.
  VirtualSpaceNode* const _container;

  // Current allocation top.
  MetaWord* _top;

  // A 32bit sentinel for debugging purposes.
  enum { CHUNK_SENTINEL = 0x4d4554EF,  // "MET"
         CHUNK_SENTINEL_INVALID = 0xFEEEEEEF
  };

  uint32_t _sentinel;

  const ChunkIndex _chunk_type;
  const bool _is_class;
  // Whether the chunk is free (in freelist) or in use by some class loader.
  bool _is_tagged_free;

  ChunkOrigin _origin;
  int _use_count;

  MetaWord* initial_top() const { return (MetaWord*)this + overhead(); }
  MetaWord* top() const         { return _top; }

 public:
  // Metachunks are allocated out of a MetadataVirtualSpace and
  // and use some of its space to describe itself (plus alignment
  // considerations).  Metadata is allocated in the rest of the chunk.
  // This size is the overhead of maintaining the Metachunk within
  // the space.

  // Alignment of each allocation in the chunks.
  static size_t object_alignment();

  // Size of the Metachunk header, in words, including alignment.
  static size_t overhead();

  Metachunk(ChunkIndex chunktype, bool is_class, size_t word_size, VirtualSpaceNode* container);

  MetaWord* allocate(size_t word_size);

  VirtualSpaceNode* container() const { return _container; }

  MetaWord* bottom() const { return (MetaWord*) this; }

  // Reset top to bottom so chunk can be reused.
  void reset_empty() { _top = initial_top(); clear_next(); clear_prev(); }
  bool is_empty() { return _top == initial_top(); }

  // used (has been allocated)
  // free (available for future allocations)
  size_t word_size() const { return size(); }
  size_t used_word_size() const;
  size_t free_word_size() const;

  bool is_tagged_free() { return _is_tagged_free; }
  void set_is_tagged_free(bool v) { _is_tagged_free = v; }

  bool contains(const void* ptr) { return bottom() <= ptr && ptr < _top; }

  void print_on(outputStream* st) const;

  bool is_valid_sentinel() const        { return _sentinel == CHUNK_SENTINEL; }
  void remove_sentinel()                { _sentinel = CHUNK_SENTINEL_INVALID; }

  int get_use_count() const             { return _use_count; }
  void inc_use_count()                  { _use_count ++; }

  ChunkOrigin get_origin() const        { return _origin; }
  void set_origin(ChunkOrigin orig)     { _origin = orig; }

  ChunkIndex get_chunk_type() const     { return _chunk_type; }
  bool is_class() const                 { return _is_class; }

  DEBUG_ONLY(void mangle(juint word_value);)
  DEBUG_ONLY(void verify() const;)

};


// Helper function that does a bunch of checks for a chunk.
DEBUG_ONLY(void do_verify_chunk(Metachunk* chunk);)

// Given a Metachunk, update its in-use information (both in the
// chunk and the occupancy map).
void do_update_in_use_info_for_chunk(Metachunk* chunk, bool inuse);

} // namespace metaspace

#endif  // SHARE_VM_MEMORY_METACHUNK_HPP
