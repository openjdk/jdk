/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_MEMORY_METASPACE_CHUNKMANAGER_HPP
#define SHARE_MEMORY_METASPACE_CHUNKMANAGER_HPP

#include "memory/allocation.hpp"
#include "memory/binaryTreeDictionary.hpp"
#include "memory/freeList.hpp"
#include "memory/metaspace/metachunk.hpp"
#include "memory/metaspace/metaspaceStatistics.hpp"
#include "memory/metaspaceChunkFreeListSummary.hpp"
#include "utilities/globalDefinitions.hpp"

class ChunkManagerTestAccessor;

namespace metaspace {

typedef class FreeList<Metachunk> ChunkList;
typedef BinaryTreeDictionary<Metachunk, FreeList<Metachunk> > ChunkTreeDictionary;

// Manages the global free lists of chunks.
class ChunkManager : public CHeapObj<mtInternal> {
  friend class ::ChunkManagerTestAccessor;

  // Free list of chunks of different sizes.
  //   SpecializedChunk
  //   SmallChunk
  //   MediumChunk
  ChunkList _free_chunks[NumberOfFreeLists];

  // Whether or not this is the class chunkmanager.
  const bool _is_class;

  // Return non-humongous chunk list by its index.
  ChunkList* free_chunks(ChunkIndex index);

  // Returns non-humongous chunk list for the given chunk word size.
  ChunkList* find_free_chunks_list(size_t word_size);

  //   HumongousChunk
  ChunkTreeDictionary _humongous_dictionary;

  // Returns the humongous chunk dictionary.
  ChunkTreeDictionary* humongous_dictionary() { return &_humongous_dictionary; }
  const ChunkTreeDictionary* humongous_dictionary() const { return &_humongous_dictionary; }

  // Size, in metaspace words, of all chunks managed by this ChunkManager
  size_t _free_chunks_total;
  // Number of chunks in this ChunkManager
  size_t _free_chunks_count;

  // Update counters after a chunk was added or removed removed.
  void account_for_added_chunk(const Metachunk* c);
  void account_for_removed_chunk(const Metachunk* c);

  // Given a pointer to a chunk, attempts to merge it with neighboring
  // free chunks to form a bigger chunk. Returns true if successful.
  bool attempt_to_coalesce_around_chunk(Metachunk* chunk, ChunkIndex target_chunk_type);

  // Helper for chunk merging:
  //  Given an address range with 1-n chunks which are all supposed to be
  //  free and hence currently managed by this ChunkManager, remove them
  //  from this ChunkManager and mark them as invalid.
  // - This does not correct the occupancy map.
  // - This does not adjust the counters in ChunkManager.
  // - Does not adjust container count counter in containing VirtualSpaceNode.
  // Returns number of chunks removed.
  int remove_chunks_in_area(MetaWord* p, size_t word_size);

  // Helper for chunk splitting: given a target chunk size and a larger free chunk,
  // split up the larger chunk into n smaller chunks, at least one of which should be
  // the target chunk of target chunk size. The smaller chunks, including the target
  // chunk, are returned to the freelist. The pointer to the target chunk is returned.
  // Note that this chunk is supposed to be removed from the freelist right away.
  Metachunk* split_chunk(size_t target_chunk_word_size, Metachunk* chunk);

 public:

  ChunkManager(bool is_class);

  // Add or delete (return) a chunk to the global freelist.
  Metachunk* chunk_freelist_allocate(size_t word_size);

  // Map a size to a list index assuming that there are lists
  // for special, small, medium, and humongous chunks.
  ChunkIndex list_index(size_t size);

  // Map a given index to the chunk size.
  size_t size_by_index(ChunkIndex index) const;

  bool is_class() const { return _is_class; }

  // Convenience accessors.
  size_t medium_chunk_word_size() const { return size_by_index(MediumIndex); }
  size_t small_chunk_word_size() const { return size_by_index(SmallIndex); }
  size_t specialized_chunk_word_size() const { return size_by_index(SpecializedIndex); }

  // Take a chunk from the ChunkManager. The chunk is expected to be in
  // the chunk manager (the freelist if non-humongous, the dictionary if
  // humongous).
  void remove_chunk(Metachunk* chunk);

  // Return a single chunk of type index to the ChunkManager.
  void return_single_chunk(Metachunk* chunk);

  // Add the simple linked list of chunks to the freelist of chunks
  // of type index.
  void return_chunk_list(Metachunk* chunk);

  // Total of the space in the free chunks list
  size_t free_chunks_total_words() const { return _free_chunks_total; }
  size_t free_chunks_total_bytes() const { return free_chunks_total_words() * BytesPerWord; }

  // Number of chunks in the free chunks list
  size_t free_chunks_count() const { return _free_chunks_count; }

  // Remove from a list by size.  Selects list based on size of chunk.
  Metachunk* free_chunks_get(size_t chunk_word_size);

#define index_bounds_check(index)                                         \
  assert(is_valid_chunktype(index), "Bad index: %d", (int) index)

  size_t num_free_chunks(ChunkIndex index) const {
    index_bounds_check(index);

    if (index == HumongousIndex) {
      return _humongous_dictionary.total_free_blocks();
    }

    ssize_t count = _free_chunks[index].count();
    return count == -1 ? 0 : (size_t) count;
  }

  size_t size_free_chunks_in_bytes(ChunkIndex index) const {
    index_bounds_check(index);

    size_t word_size = 0;
    if (index == HumongousIndex) {
      word_size = _humongous_dictionary.total_size();
    } else {
      const size_t size_per_chunk_in_words = _free_chunks[index].size();
      word_size = size_per_chunk_in_words * num_free_chunks(index);
    }

    return word_size * BytesPerWord;
  }

  MetaspaceChunkFreeListSummary chunk_free_list_summary() const {
    return MetaspaceChunkFreeListSummary(num_free_chunks(SpecializedIndex),
                                         num_free_chunks(SmallIndex),
                                         num_free_chunks(MediumIndex),
                                         num_free_chunks(HumongousIndex),
                                         size_free_chunks_in_bytes(SpecializedIndex),
                                         size_free_chunks_in_bytes(SmallIndex),
                                         size_free_chunks_in_bytes(MediumIndex),
                                         size_free_chunks_in_bytes(HumongousIndex));
  }

#ifdef ASSERT
  // Debug support
  // Verify free list integrity. slow=true: verify chunk-internal integrity too.
  void verify(bool slow) const;
  void locked_verify(bool slow) const;
#endif

  void locked_print_free_chunks(outputStream* st);

  // Fill in current statistic values to the given statistics object.
  void collect_statistics(ChunkManagerStatistics* out) const;

};

} // namespace metaspace


#endif // SHARE_MEMORY_METASPACE_CHUNKMANAGER_HPP
