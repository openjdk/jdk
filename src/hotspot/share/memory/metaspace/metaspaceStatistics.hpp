/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2018 SAP SE. All rights reserved.
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

#ifndef SHARE_MEMORY_METASPACE_METASPACESTATISTICS_HPP
#define SHARE_MEMORY_METASPACE_METASPACESTATISTICS_HPP

#include "utilities/globalDefinitions.hpp"
#include "memory/metaspace.hpp" // for MetadataType enum
#include "memory/metaspace/metachunk.hpp" // for ChunkIndex enum

class outputStream;

namespace metaspace {

// Contains statistics for a number of free chunks.
class FreeChunksStatistics {
  uintx _num;         // Number of chunks
  size_t _cap;        // Total capacity, in words

public:
  FreeChunksStatistics();

  void reset();

  uintx num() const { return _num; }
  size_t cap() const { return _cap; }

  void add(uintx n, size_t s);
  void add(const FreeChunksStatistics& other);
  void print_on(outputStream* st, size_t scale) const;

}; // end: FreeChunksStatistics


// Contains statistics for a ChunkManager.
class ChunkManagerStatistics {

  FreeChunksStatistics _chunk_stats[NumberOfInUseLists];

public:

  // Free chunk statistics, by chunk index.
  const FreeChunksStatistics& chunk_stats(ChunkIndex index) const   { return _chunk_stats[index]; }
  FreeChunksStatistics& chunk_stats(ChunkIndex index)               { return _chunk_stats[index]; }

  void reset();
  size_t total_capacity() const;

  void print_on(outputStream* st, size_t scale) const;

}; // ChunkManagerStatistics

// Contains statistics for a number of chunks in use.
// Each chunk has a used and free portion; however, there are current chunks (serving
// potential future metaspace allocations) and non-current chunks. Unused portion of the
// former is counted as free, unused portion of the latter counts as waste.
class UsedChunksStatistics {
  uintx _num;     // Number of chunks
  size_t _cap;    // Total capacity in words.
  size_t _used;   // Total used area, in words
  size_t _free;   // Total free area (unused portions of current chunks), in words
  size_t _waste;  // Total waste area (unused portions of non-current chunks), in words
  size_t _overhead; // Total sum of chunk overheads, in words.

public:

  UsedChunksStatistics();

  void reset();

  uintx num() const { return _num; }

  // Total capacity, in words
  size_t cap() const { return _cap; }

  // Total used area, in words
  size_t used() const { return _used; }

  // Total free area (unused portions of current chunks), in words
  size_t free() const { return _free; }

  // Total waste area (unused portions of non-current chunks), in words
  size_t waste() const { return _waste; }

  // Total area spent in overhead (chunk headers), in words
  size_t overhead() const { return _overhead; }

  void add_num(uintx n) { _num += n; }
  void add_cap(size_t s) { _cap += s; }
  void add_used(size_t s) { _used += s; }
  void add_free(size_t s) { _free += s; }
  void add_waste(size_t s) { _waste += s; }
  void add_overhead(size_t s) { _overhead += s; }

  void add(const UsedChunksStatistics& other);

  void print_on(outputStream* st, size_t scale) const;

#ifdef ASSERT
  void check_sanity() const;
#endif

}; // UsedChunksStatistics

// Class containing statistics for one or more space managers.
class SpaceManagerStatistics {

  UsedChunksStatistics _chunk_stats[NumberOfInUseLists];
  uintx _free_blocks_num;
  size_t _free_blocks_cap_words;

public:

  SpaceManagerStatistics();

  // Chunk statistics by chunk index
  const UsedChunksStatistics& chunk_stats(ChunkIndex index) const   { return _chunk_stats[index]; }
  UsedChunksStatistics& chunk_stats(ChunkIndex index)               { return _chunk_stats[index]; }

  uintx free_blocks_num () const                                    { return _free_blocks_num; }
  size_t free_blocks_cap_words () const                             { return _free_blocks_cap_words; }

  void reset();

  void add_free_blocks_info(uintx num, size_t cap);

  // Returns total chunk statistics over all chunk types.
  UsedChunksStatistics totals() const;

  void add(const SpaceManagerStatistics& other);

  void print_on(outputStream* st, size_t scale,  bool detailed) const;

}; // SpaceManagerStatistics

class ClassLoaderMetaspaceStatistics {

  SpaceManagerStatistics _sm_stats[Metaspace::MetadataTypeCount];

public:

  ClassLoaderMetaspaceStatistics();

  const SpaceManagerStatistics& sm_stats(Metaspace::MetadataType mdType) const { return _sm_stats[mdType]; }
  SpaceManagerStatistics& sm_stats(Metaspace::MetadataType mdType)             { return _sm_stats[mdType]; }

  const SpaceManagerStatistics& nonclass_sm_stats() const { return sm_stats(Metaspace::NonClassType); }
  SpaceManagerStatistics& nonclass_sm_stats()             { return sm_stats(Metaspace::NonClassType); }
  const SpaceManagerStatistics& class_sm_stats() const    { return sm_stats(Metaspace::ClassType); }
  SpaceManagerStatistics& class_sm_stats()                { return sm_stats(Metaspace::ClassType); }

  void reset();

  void add(const ClassLoaderMetaspaceStatistics& other);

  // Returns total space manager statistics for both class and non-class metaspace
  SpaceManagerStatistics totals() const;

  void print_on(outputStream* st, size_t scale, bool detailed) const;

}; // ClassLoaderMetaspaceStatistics

} // namespace metaspace

#endif /* SHARE_MEMORY_METASPACE_METASPACESTATISTICS_HPP */

