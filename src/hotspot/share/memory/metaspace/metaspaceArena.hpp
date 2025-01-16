/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020 SAP SE. All rights reserved.
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

#ifndef SHARE_MEMORY_METASPACE_METASPACEARENA_HPP
#define SHARE_MEMORY_METASPACE_METASPACEARENA_HPP

#include "memory/allocation.hpp"
#include "memory/metaspace.hpp"
#include "memory/metaspace/counters.hpp"
#include "memory/metaspace/metablock.hpp"
#include "memory/metaspace/metachunkList.hpp"

class outputStream;
class Mutex;

namespace metaspace {

class ArenaGrowthPolicy;
struct ArenaStats;
class ChunkManager;
class FreeBlocks;
class Metachunk;
class MetaspaceContext;


// The MetaspaceArena is a growable metaspace memory pool belonging to a CLD;
//  internally it consists of a list of metaspace chunks, of which the head chunk
//  is the current chunk from which we allocate via pointer bump.
//
//  +---------------+
//  |     Arena     |
//  +---------------+
//            |
//            | _chunks                                               commit top
//            |                                                       v
//        +----------+      +----------+      +----------+      +----------+
//        | retired  | ---> | retired  | ---> | retired  | ---> | current  |
//        | chunk    |      | chunk    |      | chunk    |      | chunk    |
//        +----------+      +----------+      +----------+      +----------+
//                                                                  ^
//                                                                  used top
//
//        +------------+
//        | FreeBlocks | --> O -> O -> O -> O
//        +------------+
//
//

// When the current chunk is used up, MetaspaceArena requests a new chunk from
//  the associated ChunkManager.
//
// MetaspaceArena also keeps a FreeBlocks structure to manage memory blocks which
//  had been deallocated prematurely.
//

class MetaspaceArena : public CHeapObj<mtClass> {
  friend class MetaspaceArenaTestFriend;

  // Please note that access to a metaspace arena may be shared
  // between threads and needs to be synchronized in CLMS.

  // Allocation alignment specific to this arena
  const size_t _allocation_alignment_words;

  // Reference to the chunk manager to allocate chunks from.
  ChunkManager* const _chunk_manager;

  // Reference to the growth policy to use.
  const ArenaGrowthPolicy* const _growth_policy;

  // List of chunks. Head of the list is the current chunk.
  MetachunkList _chunks;

  // Structure to take care of leftover/deallocated space in used chunks.
  // Owned by the Arena. Gets allocated on demand only.
  FreeBlocks* _fbl;

  Metachunk* current_chunk()              { return _chunks.first(); }
  const Metachunk* current_chunk() const  { return _chunks.first(); }

  // Reference to an outside counter to keep track of used space.
  SizeAtomicCounter* const _total_used_words_counter;

  // A name for purely debugging/logging purposes.
  const char* const _name;

  ChunkManager* chunk_manager() const           { return _chunk_manager; }

  // free block list
  FreeBlocks* fbl() const                       { return _fbl; }
  void add_allocation_to_fbl(MetaBlock bl);

  // Given a chunk, return the committed remainder of this chunk.
  MetaBlock salvage_chunk(Metachunk* c);

  // Allocate a new chunk from the underlying chunk manager able to hold at least
  // requested word size.
  Metachunk* allocate_new_chunk(size_t requested_word_size);

  // Returns the level of the next chunk to be added, acc to growth policy.
  chunklevel_t next_chunk_level() const;

  // Attempt to enlarge the current chunk to make it large enough to hold at least
  //  requested_word_size additional words.
  //
  // On success, true is returned, false otherwise.
  bool attempt_enlarge_current_chunk(size_t requested_word_size);

  // Allocate from the arena proper, once dictionary allocations and fencing are sorted out.
  MetaBlock allocate_inner(size_t word_size, MetaBlock& wastage);

public:

  MetaspaceArena(MetaspaceContext* context,
                 const ArenaGrowthPolicy* growth_policy,
                 size_t allocation_alignment_words,
                 const char* name);

  ~MetaspaceArena();

  size_t allocation_alignment_words() const { return _allocation_alignment_words; }
  size_t allocation_alignment_bytes() const { return allocation_alignment_words() * BytesPerWord; }

  // Allocate memory from Metaspace.
  // On success, returns non-empty block of the specified word size, and
  // possibly a wastage block that is the result of alignment operations.
  // On failure, returns an empty block. Failure may happen if we hit a
  // commit limit.
  MetaBlock allocate(size_t word_size, MetaBlock& wastage);

  // Prematurely returns a metaspace allocation to the _block_freelists because it is not
  // needed anymore.
  void deallocate(MetaBlock bl);

  // Update statistics. This walks all in-use chunks.
  void add_to_statistics(ArenaStats* out) const;

  // Convenience method to get the most important usage statistics.
  // For deeper analysis use add_to_statistics().
  void usage_numbers(size_t* p_used_words, size_t* p_committed_words, size_t* p_capacity_words) const;

  DEBUG_ONLY(void verify() const;)
  DEBUG_ONLY(void verify_allocation_guards() const;)

  void print_on(outputStream* st) const;

  // Returns true if the given block is contained in this arena
  DEBUG_ONLY(bool contains(MetaBlock bl) const;)
};

} // namespace metaspace

#endif // SHARE_MEMORY_METASPACE_METASPACEARENA_HPP

