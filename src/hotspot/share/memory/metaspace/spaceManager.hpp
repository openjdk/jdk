/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_MEMORY_METASPACE_SPACEMANAGER_HPP
#define SHARE_MEMORY_METASPACE_SPACEMANAGER_HPP

#include "memory/allocation.hpp"
#include "memory/metaspace.hpp"
#include "memory/metaspace/blockFreelist.hpp"
#include "memory/metaspace/metaspaceCommon.hpp"
#include "memory/metaspace/metachunk.hpp"
#include "memory/metaspace/metaspaceStatistics.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"

class outputStream;
class Mutex;

namespace metaspace {

//  SpaceManager - used by Metaspace to handle allocations
class SpaceManager : public CHeapObj<mtClass> {
  friend class ::ClassLoaderMetaspace;
  friend class Metadebug;

 private:

  // protects allocations
  Mutex* const _lock;

  // Type of metadata allocated.
  const Metaspace::MetadataType   _mdtype;

  // Type of metaspace
  const Metaspace::MetaspaceType  _space_type;

  // List of chunks in use by this SpaceManager.  Allocations
  // are done from the current chunk.  The list is used for deallocating
  // chunks when the SpaceManager is freed.
  Metachunk* _chunk_list;
  Metachunk* _current_chunk;

  enum {

    // Maximum number of small chunks to allocate to a SpaceManager
    small_chunk_limit = 4,

    // Maximum number of specialize chunks to allocate for anonymous and delegating
    // metadata space to a SpaceManager
    anon_and_delegating_metadata_specialize_chunk_limit = 4,

    allocation_from_dictionary_limit = 4 * K

  };

  // Some running counters, but lets keep their number small to not add to much to
  // the per-classloader footprint.
  // Note: capacity = used + free + waste + overhead. We do not keep running counters for
  // free and waste. Their sum can be deduced from the three other values.
  size_t _overhead_words;
  size_t _capacity_words;
  size_t _used_words;
  uintx _num_chunks_by_type[NumberOfInUseLists];

  // Free lists of blocks are per SpaceManager since they
  // are assumed to be in chunks in use by the SpaceManager
  // and all chunks in use by a SpaceManager are freed when
  // the class loader using the SpaceManager is collected.
  BlockFreelist* _block_freelists;

 private:
  // Accessors
  Metachunk* chunk_list() const { return _chunk_list; }

  BlockFreelist* block_freelists() const { return _block_freelists; }

  Metaspace::MetadataType mdtype() { return _mdtype; }

  VirtualSpaceList* vs_list()   const { return Metaspace::get_space_list(_mdtype); }
  ChunkManager* chunk_manager() const { return Metaspace::get_chunk_manager(_mdtype); }

  Metachunk* current_chunk() const { return _current_chunk; }
  void set_current_chunk(Metachunk* v) {
    _current_chunk = v;
  }

  Metachunk* find_current_chunk(size_t word_size);

  // Add chunk to the list of chunks in use
  void add_chunk(Metachunk* v, bool make_current);
  void retire_current_chunk();

  Mutex* lock() const { return _lock; }

  // Adds to the given statistic object. Expects to be locked with lock().
  void add_to_statistics_locked(SpaceManagerStatistics* out) const;

  // Verify internal counters against the current state. Expects to be locked with lock().
  DEBUG_ONLY(void verify_metrics_locked() const;)

 public:
  SpaceManager(Metaspace::MetadataType mdtype,
               Metaspace::MetaspaceType space_type,
               Mutex* lock);
  ~SpaceManager();

  enum ChunkMultiples {
    MediumChunkMultiple = 4
  };

  static size_t specialized_chunk_size(bool is_class) { return is_class ? ClassSpecializedChunk : SpecializedChunk; }
  static size_t small_chunk_size(bool is_class)       { return is_class ? ClassSmallChunk : SmallChunk; }
  static size_t medium_chunk_size(bool is_class)      { return is_class ? ClassMediumChunk : MediumChunk; }

  static size_t smallest_chunk_size(bool is_class)    { return specialized_chunk_size(is_class); }

  // Accessors
  bool is_class() const { return _mdtype == Metaspace::ClassType; }

  size_t specialized_chunk_size() const { return specialized_chunk_size(is_class()); }
  size_t small_chunk_size()       const { return small_chunk_size(is_class()); }
  size_t medium_chunk_size()      const { return medium_chunk_size(is_class()); }

  size_t smallest_chunk_size()    const { return smallest_chunk_size(is_class()); }

  size_t medium_chunk_bunch()     const { return medium_chunk_size() * MediumChunkMultiple; }

  bool is_humongous(size_t word_size) { return word_size > medium_chunk_size(); }

  size_t capacity_words() const     { return _capacity_words; }
  size_t used_words() const         { return _used_words; }
  size_t overhead_words() const     { return _overhead_words; }

  // Adjust local, global counters after a new chunk has been added.
  void account_for_new_chunk(const Metachunk* new_chunk);

  // Adjust local, global counters after space has been allocated from the current chunk.
  void account_for_allocation(size_t words);

  // Adjust global counters just before the SpaceManager dies, after all its chunks
  // have been returned to the freelist.
  void account_for_spacemanager_death();

  // Adjust the initial chunk size to match one of the fixed chunk list sizes,
  // or return the unadjusted size if the requested size is humongous.
  static size_t adjust_initial_chunk_size(size_t requested, bool is_class_space);
  size_t adjust_initial_chunk_size(size_t requested) const;

  // Get the initial chunks size for this metaspace type.
  size_t get_initial_chunk_size(Metaspace::MetaspaceType type) const;

  // Todo: remove this once we have counters by chunk type.
  uintx num_chunks_by_type(ChunkIndex chunk_type) const       { return _num_chunks_by_type[chunk_type]; }

  Metachunk* get_new_chunk(size_t chunk_word_size);

  // Block allocation and deallocation.
  // Allocates a block from the current chunk
  MetaWord* allocate(size_t word_size);

  // Helper for allocations
  MetaWord* allocate_work(size_t word_size);

  // Returns a block to the per manager freelist
  void deallocate(MetaWord* p, size_t word_size);

  // Based on the allocation size and a minimum chunk size,
  // returned chunk size (for expanding space for chunk allocation).
  size_t calc_chunk_size(size_t allocation_word_size);

  // Called when an allocation from the current chunk fails.
  // Gets a new chunk (may require getting a new virtual space),
  // and allocates from that chunk.
  MetaWord* grow_and_allocate(size_t word_size);

  // Notify memory usage to MemoryService.
  void track_metaspace_memory_usage();

  // debugging support.

  void print_on(outputStream* st) const;
  void locked_print_chunks_in_use_on(outputStream* st) const;

  void verify();
  void verify_chunk_size(Metachunk* chunk);

  // This adjusts the size given to be greater than the minimum allocation size in
  // words for data in metaspace.  Esentially the minimum size is currently 3 words.
  size_t get_allocation_word_size(size_t word_size) {
    size_t byte_size = word_size * BytesPerWord;

    size_t raw_bytes_size = MAX2(byte_size, sizeof(Metablock));
    raw_bytes_size = align_up(raw_bytes_size, Metachunk::object_alignment());

    size_t raw_word_size = raw_bytes_size / BytesPerWord;
    assert(raw_word_size * BytesPerWord == raw_bytes_size, "Size problem");

    return raw_word_size;
  }

  // Adds to the given statistic object.
  void add_to_statistics(SpaceManagerStatistics* out) const;

  // Verify internal counters against the current state.
  DEBUG_ONLY(void verify_metrics() const;)

};


} // namespace metaspace

#endif /* SHARE_MEMORY_METASPACE_SPACEMANAGER_HPP */

