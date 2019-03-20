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

#ifndef SHARE_MEMORY_METASPACE_METASPACECOMMON_HPP
#define SHARE_MEMORY_METASPACE_METASPACECOMMON_HPP

#include "utilities/align.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"

class outputStream;

namespace metaspace {

enum ChunkSizes {    // in words.
  ClassSpecializedChunk = 128,
  SpecializedChunk = 128,
  ClassSmallChunk = 256,
  SmallChunk = 512,
  ClassMediumChunk = 4 * K,
  MediumChunk = 8 * K
};

// Print a size, in words, scaled.
void print_scaled_words(outputStream* st, size_t word_size, size_t scale = 0, int width = -1);

// Convenience helper: prints a size value and a percentage.
void print_scaled_words_and_percentage(outputStream* st, size_t word_size, size_t compare_word_size, size_t scale = 0, int width = -1);

// Print a human readable size.
// byte_size: size, in bytes, to be printed.
// scale: one of 1 (byte-wise printing), sizeof(word) (word-size printing), K, M, G (scaled by KB, MB, GB respectively,
//         or 0, which means the best scale is choosen dynamically.
// width: printing width.
void print_human_readable_size(outputStream* st, size_t byte_size, size_t scale = 0, int width = -1);

// Prints a percentage value. Values smaller than 1% but not 0 are displayed as "<1%", values
// larger than 99% but not 100% are displayed as ">100%".
void print_percentage(outputStream* st, size_t total, size_t part);


#define assert_is_aligned(value, alignment)                  \
  assert(is_aligned((value), (alignment)),                   \
         SIZE_FORMAT_HEX " is not aligned to "               \
         SIZE_FORMAT, (size_t)(uintptr_t)value, (alignment))

// Internal statistics.
#ifdef ASSERT
struct  internal_statistics_t {
  // Number of allocations.
  uintx num_allocs;
  // Number of times a ClassLoaderMetaspace was born...
  uintx num_metaspace_births;
  // ... and died.
  uintx num_metaspace_deaths;
  // Number of times VirtualSpaceListNodes were created...
  uintx num_vsnodes_created;
  // ... and purged.
  uintx num_vsnodes_purged;
  // Number of times we expanded the committed section of the space.
  uintx num_committed_space_expanded;
  // Number of deallocations
  uintx num_deallocs;
  // Number of deallocations triggered from outside ("real" deallocations).
  uintx num_external_deallocs;
  // Number of times an allocation was satisfied from deallocated blocks.
  uintx num_allocs_from_deallocated_blocks;
  // Number of times a chunk was added to the freelist
  uintx num_chunks_added_to_freelist;
  // Number of times a chunk was removed from the freelist
  uintx num_chunks_removed_from_freelist;
  // Number of chunk merges
  uintx num_chunk_merges;
  // Number of chunk splits
  uintx num_chunk_splits;
};
extern internal_statistics_t g_internal_statistics;
#endif

// ChunkIndex defines the type of chunk.
// Chunk types differ by size: specialized < small < medium, chunks
// larger than medium are humongous chunks of varying size.
enum ChunkIndex {
  ZeroIndex = 0,
  SpecializedIndex = ZeroIndex,
  SmallIndex = SpecializedIndex + 1,
  MediumIndex = SmallIndex + 1,
  HumongousIndex = MediumIndex + 1,
  NumberOfFreeLists = 3,
  NumberOfInUseLists = 4
};

// Utility functions.
size_t get_size_for_nonhumongous_chunktype(ChunkIndex chunk_type, bool is_class);
ChunkIndex get_chunk_type_by_size(size_t size, bool is_class);

ChunkIndex next_chunk_index(ChunkIndex i);
ChunkIndex prev_chunk_index(ChunkIndex i);
// Returns a descriptive name for a chunk type.
const char* chunk_size_name(ChunkIndex index);

// Verify chunk sizes.
inline bool is_valid_chunksize(bool is_class, size_t size) {
  const size_t reasonable_maximum_humongous_chunk_size = 1 * G;
  return is_aligned(size, sizeof(MetaWord)) &&
         size < reasonable_maximum_humongous_chunk_size &&
         is_class ?
             (size == ClassSpecializedChunk || size == ClassSmallChunk || size >= ClassMediumChunk) :
             (size == SpecializedChunk || size == SmallChunk || size >= MediumChunk);
}

// Verify chunk type.
inline bool is_valid_chunktype(ChunkIndex index) {
  return index == SpecializedIndex || index == SmallIndex ||
         index == MediumIndex || index == HumongousIndex;
}

inline bool is_valid_nonhumongous_chunktype(ChunkIndex index) {
  return is_valid_chunktype(index) && index != HumongousIndex;
}

} // namespace metaspace

#endif // SHARE_MEMORY_METASPACE_METASPACECOMMON_HPP
