/*
 * Copyright (c) 2011, 2018, Oracle and/or its affiliates. All rights reserved.
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
#include "precompiled.hpp"
#include "aot/aotLoader.hpp"
#include "gc/shared/collectedHeap.hpp"
#include "gc/shared/collectorPolicy.hpp"
#include "logging/log.hpp"
#include "logging/logStream.hpp"
#include "memory/allocation.hpp"
#include "memory/binaryTreeDictionary.inline.hpp"
#include "memory/filemap.hpp"
#include "memory/freeList.inline.hpp"
#include "memory/metachunk.hpp"
#include "memory/metaspace.hpp"
#include "memory/metaspaceGCThresholdUpdater.hpp"
#include "memory/metaspaceShared.hpp"
#include "memory/metaspaceTracer.hpp"
#include "memory/resourceArea.hpp"
#include "memory/universe.hpp"
#include "runtime/atomic.hpp"
#include "runtime/globals.hpp"
#include "runtime/init.hpp"
#include "runtime/java.hpp"
#include "runtime/mutex.hpp"
#include "runtime/orderAccess.inline.hpp"
#include "services/memTracker.hpp"
#include "services/memoryService.hpp"
#include "utilities/align.hpp"
#include "utilities/copy.hpp"
#include "utilities/debug.hpp"
#include "utilities/macros.hpp"

typedef BinaryTreeDictionary<Metablock, FreeList<Metablock> > BlockTreeDictionary;
typedef BinaryTreeDictionary<Metachunk, FreeList<Metachunk> > ChunkTreeDictionary;

// Helper function that does a bunch of checks for a chunk.
DEBUG_ONLY(static void do_verify_chunk(Metachunk* chunk);)

// Given a Metachunk, update its in-use information (both in the
// chunk and the occupancy map).
static void do_update_in_use_info_for_chunk(Metachunk* chunk, bool inuse);

size_t const allocation_from_dictionary_limit = 4 * K;

MetaWord* last_allocated = 0;

size_t Metaspace::_compressed_class_space_size;
const MetaspaceTracer* Metaspace::_tracer = NULL;

DEBUG_ONLY(bool Metaspace::_frozen = false;)

enum ChunkSizes {    // in words.
  ClassSpecializedChunk = 128,
  SpecializedChunk = 128,
  ClassSmallChunk = 256,
  SmallChunk = 512,
  ClassMediumChunk = 4 * K,
  MediumChunk = 8 * K
};

// Returns size of this chunk type.
size_t get_size_for_nonhumongous_chunktype(ChunkIndex chunktype, bool is_class) {
  assert(is_valid_nonhumongous_chunktype(chunktype), "invalid chunk type.");
  size_t size = 0;
  if (is_class) {
    switch(chunktype) {
      case SpecializedIndex: size = ClassSpecializedChunk; break;
      case SmallIndex: size = ClassSmallChunk; break;
      case MediumIndex: size = ClassMediumChunk; break;
      default:
        ShouldNotReachHere();
    }
  } else {
    switch(chunktype) {
      case SpecializedIndex: size = SpecializedChunk; break;
      case SmallIndex: size = SmallChunk; break;
      case MediumIndex: size = MediumChunk; break;
      default:
        ShouldNotReachHere();
    }
  }
  return size;
}

ChunkIndex get_chunk_type_by_size(size_t size, bool is_class) {
  if (is_class) {
    if (size == ClassSpecializedChunk) {
      return SpecializedIndex;
    } else if (size == ClassSmallChunk) {
      return SmallIndex;
    } else if (size == ClassMediumChunk) {
      return MediumIndex;
    } else if (size > ClassMediumChunk) {
      // A valid humongous chunk size is a multiple of the smallest chunk size.
      assert(is_aligned(size, ClassSpecializedChunk), "Invalid chunk size");
      return HumongousIndex;
    }
  } else {
    if (size == SpecializedChunk) {
      return SpecializedIndex;
    } else if (size == SmallChunk) {
      return SmallIndex;
    } else if (size == MediumChunk) {
      return MediumIndex;
    } else if (size > MediumChunk) {
      // A valid humongous chunk size is a multiple of the smallest chunk size.
      assert(is_aligned(size, SpecializedChunk), "Invalid chunk size");
      return HumongousIndex;
    }
  }
  ShouldNotReachHere();
  return (ChunkIndex)-1;
}


static ChunkIndex next_chunk_index(ChunkIndex i) {
  assert(i < NumberOfInUseLists, "Out of bound");
  return (ChunkIndex) (i+1);
}

static ChunkIndex prev_chunk_index(ChunkIndex i) {
  assert(i > ZeroIndex, "Out of bound");
  return (ChunkIndex) (i-1);
}

static const char* scale_unit(size_t scale) {
  switch(scale) {
    case 1: return "BYTES";
    case K: return "KB";
    case M: return "MB";
    case G: return "GB";
    default:
      ShouldNotReachHere();
      return NULL;
  }
}

volatile intptr_t MetaspaceGC::_capacity_until_GC = 0;
uint MetaspaceGC::_shrink_factor = 0;
bool MetaspaceGC::_should_concurrent_collect = false;

typedef class FreeList<Metachunk> ChunkList;

// Manages the global free lists of chunks.
class ChunkManager : public CHeapObj<mtInternal> {
  friend class TestVirtualSpaceNodeTest;

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
  ChunkTreeDictionary* humongous_dictionary() {
    return &_humongous_dictionary;
  }

  // Size, in metaspace words, of all chunks managed by this ChunkManager
  size_t _free_chunks_total;
  // Number of chunks in this ChunkManager
  size_t _free_chunks_count;

  // Update counters after a chunk was added or removed removed.
  void account_for_added_chunk(const Metachunk* c);
  void account_for_removed_chunk(const Metachunk* c);

  // Debug support

  size_t sum_free_chunks();
  size_t sum_free_chunks_count();

  void locked_verify_free_chunks_total();
  void slow_locked_verify_free_chunks_total() {
    if (VerifyMetaspace) {
      locked_verify_free_chunks_total();
    }
  }
  void locked_verify_free_chunks_count();
  void slow_locked_verify_free_chunks_count() {
    if (VerifyMetaspace) {
      locked_verify_free_chunks_count();
    }
  }
  void verify_free_chunks_count();

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

  struct ChunkManagerStatistics {
    size_t num_by_type[NumberOfFreeLists];
    size_t single_size_by_type[NumberOfFreeLists];
    size_t total_size_by_type[NumberOfFreeLists];
    size_t num_humongous_chunks;
    size_t total_size_humongous_chunks;
  };

  void locked_get_statistics(ChunkManagerStatistics* stat) const;
  void get_statistics(ChunkManagerStatistics* stat) const;
  static void print_statistics(const ChunkManagerStatistics* stat, outputStream* out, size_t scale);


  ChunkManager(bool is_class)
      : _is_class(is_class), _free_chunks_total(0), _free_chunks_count(0) {
    _free_chunks[SpecializedIndex].set_size(get_size_for_nonhumongous_chunktype(SpecializedIndex, is_class));
    _free_chunks[SmallIndex].set_size(get_size_for_nonhumongous_chunktype(SmallIndex, is_class));
    _free_chunks[MediumIndex].set_size(get_size_for_nonhumongous_chunktype(MediumIndex, is_class));
  }

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
  void return_single_chunk(ChunkIndex index, Metachunk* chunk);

  // Add the simple linked list of chunks to the freelist of chunks
  // of type index.
  void return_chunk_list(ChunkIndex index, Metachunk* chunk);

  // Total of the space in the free chunks list
  size_t free_chunks_total_words();
  size_t free_chunks_total_bytes();

  // Number of chunks in the free chunks list
  size_t free_chunks_count();

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

  // Debug support
  void verify();
  void slow_verify() {
    if (VerifyMetaspace) {
      verify();
    }
  }
  void locked_verify();
  void slow_locked_verify() {
    if (VerifyMetaspace) {
      locked_verify();
    }
  }
  void verify_free_chunks_total();

  void locked_print_free_chunks(outputStream* st);
  void locked_print_sum_free_chunks(outputStream* st);

  void print_on(outputStream* st) const;

  // Prints composition for both non-class and (if available)
  // class chunk manager.
  static void print_all_chunkmanagers(outputStream* out, size_t scale = 1);
};

class SmallBlocks : public CHeapObj<mtClass> {
  const static uint _small_block_max_size = sizeof(TreeChunk<Metablock,  FreeList<Metablock> >)/HeapWordSize;
  const static uint _small_block_min_size = sizeof(Metablock)/HeapWordSize;

 private:
  FreeList<Metablock> _small_lists[_small_block_max_size - _small_block_min_size];

  FreeList<Metablock>& list_at(size_t word_size) {
    assert(word_size >= _small_block_min_size, "There are no metaspace objects less than %u words", _small_block_min_size);
    return _small_lists[word_size - _small_block_min_size];
  }

 public:
  SmallBlocks() {
    for (uint i = _small_block_min_size; i < _small_block_max_size; i++) {
      uint k = i - _small_block_min_size;
      _small_lists[k].set_size(i);
    }
  }

  size_t total_size() const {
    size_t result = 0;
    for (uint i = _small_block_min_size; i < _small_block_max_size; i++) {
      uint k = i - _small_block_min_size;
      result = result + _small_lists[k].count() * _small_lists[k].size();
    }
    return result;
  }

  static uint small_block_max_size() { return _small_block_max_size; }
  static uint small_block_min_size() { return _small_block_min_size; }

  MetaWord* get_block(size_t word_size) {
    if (list_at(word_size).count() > 0) {
      MetaWord* new_block = (MetaWord*) list_at(word_size).get_chunk_at_head();
      return new_block;
    } else {
      return NULL;
    }
  }
  void return_block(Metablock* free_chunk, size_t word_size) {
    list_at(word_size).return_chunk_at_head(free_chunk, false);
    assert(list_at(word_size).count() > 0, "Should have a chunk");
  }

  void print_on(outputStream* st) const {
    st->print_cr("SmallBlocks:");
    for (uint i = _small_block_min_size; i < _small_block_max_size; i++) {
      uint k = i - _small_block_min_size;
      st->print_cr("small_lists size " SIZE_FORMAT " count " SIZE_FORMAT, _small_lists[k].size(), _small_lists[k].count());
    }
  }
};

// Used to manage the free list of Metablocks (a block corresponds
// to the allocation of a quantum of metadata).
class BlockFreelist : public CHeapObj<mtClass> {
  BlockTreeDictionary* const _dictionary;
  SmallBlocks* _small_blocks;

  // Only allocate and split from freelist if the size of the allocation
  // is at least 1/4th the size of the available block.
  const static int WasteMultiplier = 4;

  // Accessors
  BlockTreeDictionary* dictionary() const { return _dictionary; }
  SmallBlocks* small_blocks() {
    if (_small_blocks == NULL) {
      _small_blocks = new SmallBlocks();
    }
    return _small_blocks;
  }

 public:
  BlockFreelist();
  ~BlockFreelist();

  // Get and return a block to the free list
  MetaWord* get_block(size_t word_size);
  void return_block(MetaWord* p, size_t word_size);

  size_t total_size() const  {
    size_t result = dictionary()->total_size();
    if (_small_blocks != NULL) {
      result = result + _small_blocks->total_size();
    }
    return result;
  }

  static size_t min_dictionary_size()   { return TreeChunk<Metablock, FreeList<Metablock> >::min_size(); }
  void print_on(outputStream* st) const;
};

// Helper for Occupancy Bitmap. A type trait to give an all-bits-are-one-unsigned constant.
template <typename T> struct all_ones  { static const T value; };
template <> struct all_ones <uint64_t> { static const uint64_t value = 0xFFFFFFFFFFFFFFFFULL; };
template <> struct all_ones <uint32_t> { static const uint32_t value = 0xFFFFFFFF; };

// The OccupancyMap is a bitmap which, for a given VirtualSpaceNode,
// keeps information about
// - where a chunk starts
// - whether a chunk is in-use or free
// A bit in this bitmap represents one range of memory in the smallest
// chunk size (SpecializedChunk or ClassSpecializedChunk).
class OccupancyMap : public CHeapObj<mtInternal> {

  // The address range this map covers.
  const MetaWord* const _reference_address;
  const size_t _word_size;

  // The word size of a specialized chunk, aka the number of words one
  // bit in this map represents.
  const size_t _smallest_chunk_word_size;

  // map data
  // Data are organized in two bit layers:
  // The first layer is the chunk-start-map. Here, a bit is set to mark
  // the corresponding region as the head of a chunk.
  // The second layer is the in-use-map. Here, a set bit indicates that
  // the corresponding belongs to a chunk which is in use.
  uint8_t* _map[2];

  enum { layer_chunk_start_map = 0, layer_in_use_map = 1 };

  // length, in bytes, of bitmap data
  size_t _map_size;

  // Returns true if bit at position pos at bit-layer layer is set.
  bool get_bit_at_position(unsigned pos, unsigned layer) const {
    assert(layer == 0 || layer == 1, "Invalid layer %d", layer);
    const unsigned byteoffset = pos / 8;
    assert(byteoffset < _map_size,
           "invalid byte offset (%u), map size is " SIZE_FORMAT ".", byteoffset, _map_size);
    const unsigned mask = 1 << (pos % 8);
    return (_map[layer][byteoffset] & mask) > 0;
  }

  // Changes bit at position pos at bit-layer layer to value v.
  void set_bit_at_position(unsigned pos, unsigned layer, bool v) {
    assert(layer == 0 || layer == 1, "Invalid layer %d", layer);
    const unsigned byteoffset = pos / 8;
    assert(byteoffset < _map_size,
           "invalid byte offset (%u), map size is " SIZE_FORMAT ".", byteoffset, _map_size);
    const unsigned mask = 1 << (pos % 8);
    if (v) {
      _map[layer][byteoffset] |= mask;
    } else {
      _map[layer][byteoffset] &= ~mask;
    }
  }

  // Optimized case of is_any_bit_set_in_region for 32/64bit aligned access:
  // pos is 32/64 aligned and num_bits is 32/64.
  // This is the typical case when coalescing to medium chunks, whose size is
  // 32 or 64 times the specialized chunk size (depending on class or non class
  // case), so they occupy 64 bits which should be 64bit aligned, because
  // chunks are chunk-size aligned.
  template <typename T>
  bool is_any_bit_set_in_region_3264(unsigned pos, unsigned num_bits, unsigned layer) const {
    assert(_map_size > 0, "not initialized");
    assert(layer == 0 || layer == 1, "Invalid layer %d.", layer);
    assert(pos % (sizeof(T) * 8) == 0, "Bit position must be aligned (%u).", pos);
    assert(num_bits == (sizeof(T) * 8), "Number of bits incorrect (%u).", num_bits);
    const size_t byteoffset = pos / 8;
    assert(byteoffset <= (_map_size - sizeof(T)),
           "Invalid byte offset (" SIZE_FORMAT "), map size is " SIZE_FORMAT ".", byteoffset, _map_size);
    const T w = *(T*)(_map[layer] + byteoffset);
    return w > 0 ? true : false;
  }

  // Returns true if any bit in region [pos1, pos1 + num_bits) is set in bit-layer layer.
  bool is_any_bit_set_in_region(unsigned pos, unsigned num_bits, unsigned layer) const {
    if (pos % 32 == 0 && num_bits == 32) {
      return is_any_bit_set_in_region_3264<uint32_t>(pos, num_bits, layer);
    } else if (pos % 64 == 0 && num_bits == 64) {
      return is_any_bit_set_in_region_3264<uint64_t>(pos, num_bits, layer);
    } else {
      for (unsigned n = 0; n < num_bits; n ++) {
        if (get_bit_at_position(pos + n, layer)) {
          return true;
        }
      }
    }
    return false;
  }

  // Returns true if any bit in region [p, p+word_size) is set in bit-layer layer.
  bool is_any_bit_set_in_region(MetaWord* p, size_t word_size, unsigned layer) const {
    assert(word_size % _smallest_chunk_word_size == 0,
        "Region size " SIZE_FORMAT " not a multiple of smallest chunk size.", word_size);
    const unsigned pos = get_bitpos_for_address(p);
    const unsigned num_bits = (unsigned) (word_size / _smallest_chunk_word_size);
    return is_any_bit_set_in_region(pos, num_bits, layer);
  }

  // Optimized case of set_bits_of_region for 32/64bit aligned access:
  // pos is 32/64 aligned and num_bits is 32/64.
  // This is the typical case when coalescing to medium chunks, whose size
  // is 32 or 64 times the specialized chunk size (depending on class or non
  // class case), so they occupy 64 bits which should be 64bit aligned,
  // because chunks are chunk-size aligned.
  template <typename T>
  void set_bits_of_region_T(unsigned pos, unsigned num_bits, unsigned layer, bool v) {
    assert(pos % (sizeof(T) * 8) == 0, "Bit position must be aligned to %u (%u).",
           (unsigned)(sizeof(T) * 8), pos);
    assert(num_bits == (sizeof(T) * 8), "Number of bits incorrect (%u), expected %u.",
           num_bits, (unsigned)(sizeof(T) * 8));
    const size_t byteoffset = pos / 8;
    assert(byteoffset <= (_map_size - sizeof(T)),
           "invalid byte offset (" SIZE_FORMAT "), map size is " SIZE_FORMAT ".", byteoffset, _map_size);
    T* const pw = (T*)(_map[layer] + byteoffset);
    *pw = v ? all_ones<T>::value : (T) 0;
  }

  // Set all bits in a region starting at pos to a value.
  void set_bits_of_region(unsigned pos, unsigned num_bits, unsigned layer, bool v) {
    assert(_map_size > 0, "not initialized");
    assert(layer == 0 || layer == 1, "Invalid layer %d.", layer);
    if (pos % 32 == 0 && num_bits == 32) {
      set_bits_of_region_T<uint32_t>(pos, num_bits, layer, v);
    } else if (pos % 64 == 0 && num_bits == 64) {
      set_bits_of_region_T<uint64_t>(pos, num_bits, layer, v);
    } else {
      for (unsigned n = 0; n < num_bits; n ++) {
        set_bit_at_position(pos + n, layer, v);
      }
    }
  }

  // Helper: sets all bits in a region [p, p+word_size).
  void set_bits_of_region(MetaWord* p, size_t word_size, unsigned layer, bool v) {
    assert(word_size % _smallest_chunk_word_size == 0,
        "Region size " SIZE_FORMAT " not a multiple of smallest chunk size.", word_size);
    const unsigned pos = get_bitpos_for_address(p);
    const unsigned num_bits = (unsigned) (word_size / _smallest_chunk_word_size);
    set_bits_of_region(pos, num_bits, layer, v);
  }

  // Helper: given an address, return the bit position representing that address.
  unsigned get_bitpos_for_address(const MetaWord* p) const {
    assert(_reference_address != NULL, "not initialized");
    assert(p >= _reference_address && p < _reference_address + _word_size,
           "Address %p out of range for occupancy map [%p..%p).",
            p, _reference_address, _reference_address + _word_size);
    assert(is_aligned(p, _smallest_chunk_word_size * sizeof(MetaWord)),
           "Address not aligned (%p).", p);
    const ptrdiff_t d = (p - _reference_address) / _smallest_chunk_word_size;
    assert(d >= 0 && (size_t)d < _map_size * 8, "Sanity.");
    return (unsigned) d;
  }

 public:

  OccupancyMap(const MetaWord* reference_address, size_t word_size, size_t smallest_chunk_word_size) :
    _reference_address(reference_address), _word_size(word_size),
    _smallest_chunk_word_size(smallest_chunk_word_size) {
    assert(reference_address != NULL, "invalid reference address");
    assert(is_aligned(reference_address, smallest_chunk_word_size),
           "Reference address not aligned to smallest chunk size.");
    assert(is_aligned(word_size, smallest_chunk_word_size),
           "Word_size shall be a multiple of the smallest chunk size.");
    // Calculate bitmap size: one bit per smallest_chunk_word_size'd area.
    size_t num_bits = word_size / smallest_chunk_word_size;
    _map_size = (num_bits + 7) / 8;
    assert(_map_size * 8 >= num_bits, "sanity");
    _map[0] = (uint8_t*) os::malloc(_map_size, mtInternal);
    _map[1] = (uint8_t*) os::malloc(_map_size, mtInternal);
    assert(_map[0] != NULL && _map[1] != NULL, "Occupancy Map: allocation failed.");
    memset(_map[1], 0, _map_size);
    memset(_map[0], 0, _map_size);
    // Sanity test: the first respectively last possible chunk start address in
    // the covered range shall map to the first and last bit in the bitmap.
    assert(get_bitpos_for_address(reference_address) == 0,
      "First chunk address in range must map to fist bit in bitmap.");
    assert(get_bitpos_for_address(reference_address + word_size - smallest_chunk_word_size) == num_bits - 1,
      "Last chunk address in range must map to last bit in bitmap.");
  }

  ~OccupancyMap() {
    os::free(_map[0]);
    os::free(_map[1]);
  }

  // Returns true if at address x a chunk is starting.
  bool chunk_starts_at_address(MetaWord* p) const {
    const unsigned pos = get_bitpos_for_address(p);
    return get_bit_at_position(pos, layer_chunk_start_map);
  }

  void set_chunk_starts_at_address(MetaWord* p, bool v) {
    const unsigned pos = get_bitpos_for_address(p);
    set_bit_at_position(pos, layer_chunk_start_map, v);
  }

  // Removes all chunk-start-bits inside a region, typically as a
  // result of a chunk merge.
  void wipe_chunk_start_bits_in_region(MetaWord* p, size_t word_size) {
    set_bits_of_region(p, word_size, layer_chunk_start_map, false);
  }

  // Returns true if there are life (in use) chunks in the region limited
  // by [p, p+word_size).
  bool is_region_in_use(MetaWord* p, size_t word_size) const {
    return is_any_bit_set_in_region(p, word_size, layer_in_use_map);
  }

  // Marks the region starting at p with the size word_size as in use
  // or free, depending on v.
  void set_region_in_use(MetaWord* p, size_t word_size, bool v) {
    set_bits_of_region(p, word_size, layer_in_use_map, v);
  }

#ifdef ASSERT
  // Verify occupancy map for the address range [from, to).
  // We need to tell it the address range, because the memory the
  // occupancy map is covering may not be fully comitted yet.
  void verify(MetaWord* from, MetaWord* to) {
    Metachunk* chunk = NULL;
    int nth_bit_for_chunk = 0;
    MetaWord* chunk_end = NULL;
    for (MetaWord* p = from; p < to; p += _smallest_chunk_word_size) {
      const unsigned pos = get_bitpos_for_address(p);
      // Check the chunk-starts-info:
      if (get_bit_at_position(pos, layer_chunk_start_map)) {
        // Chunk start marked in bitmap.
        chunk = (Metachunk*) p;
        if (chunk_end != NULL) {
          assert(chunk_end == p, "Unexpected chunk start found at %p (expected "
                 "the next chunk to start at %p).", p, chunk_end);
        }
        assert(chunk->is_valid_sentinel(), "Invalid chunk at address %p.", p);
        if (chunk->get_chunk_type() != HumongousIndex) {
          guarantee(is_aligned(p, chunk->word_size()), "Chunk %p not aligned.", p);
        }
        chunk_end = p + chunk->word_size();
        nth_bit_for_chunk = 0;
        assert(chunk_end <= to, "Chunk end overlaps test address range.");
      } else {
        // No chunk start marked in bitmap.
        assert(chunk != NULL, "Chunk should start at start of address range.");
        assert(p < chunk_end, "Did not find expected chunk start at %p.", p);
        nth_bit_for_chunk ++;
      }
      // Check the in-use-info:
      const bool in_use_bit = get_bit_at_position(pos, layer_in_use_map);
      if (in_use_bit) {
        assert(!chunk->is_tagged_free(), "Chunk %p: marked in-use in map but is free (bit %u).",
               chunk, nth_bit_for_chunk);
      } else {
        assert(chunk->is_tagged_free(), "Chunk %p: marked free in map but is in use (bit %u).",
               chunk, nth_bit_for_chunk);
      }
    }
  }

  // Verify that a given chunk is correctly accounted for in the bitmap.
  void verify_for_chunk(Metachunk* chunk) {
    assert(chunk_starts_at_address((MetaWord*) chunk),
           "No chunk start marked in map for chunk %p.", chunk);
    // For chunks larger than the minimal chunk size, no other chunk
    // must start in its area.
    if (chunk->word_size() > _smallest_chunk_word_size) {
      assert(!is_any_bit_set_in_region(((MetaWord*) chunk) + _smallest_chunk_word_size,
                                       chunk->word_size() - _smallest_chunk_word_size, layer_chunk_start_map),
             "No chunk must start within another chunk.");
    }
    if (!chunk->is_tagged_free()) {
      assert(is_region_in_use((MetaWord*)chunk, chunk->word_size()),
             "Chunk %p is in use but marked as free in map (%d %d).",
             chunk, chunk->get_chunk_type(), chunk->get_origin());
    } else {
      assert(!is_region_in_use((MetaWord*)chunk, chunk->word_size()),
             "Chunk %p is free but marked as in-use in map (%d %d).",
             chunk, chunk->get_chunk_type(), chunk->get_origin());
    }
  }

#endif // ASSERT

};

// A VirtualSpaceList node.
class VirtualSpaceNode : public CHeapObj<mtClass> {
  friend class VirtualSpaceList;

  // Link to next VirtualSpaceNode
  VirtualSpaceNode* _next;

  // Whether this node is contained in class or metaspace.
  const bool _is_class;

  // total in the VirtualSpace
  MemRegion _reserved;
  ReservedSpace _rs;
  VirtualSpace _virtual_space;
  MetaWord* _top;
  // count of chunks contained in this VirtualSpace
  uintx _container_count;

  OccupancyMap* _occupancy_map;

  // Convenience functions to access the _virtual_space
  char* low()  const { return virtual_space()->low(); }
  char* high() const { return virtual_space()->high(); }

  // The first Metachunk will be allocated at the bottom of the
  // VirtualSpace
  Metachunk* first_chunk() { return (Metachunk*) bottom(); }

  // Committed but unused space in the virtual space
  size_t free_words_in_vs() const;

  // True if this node belongs to class metaspace.
  bool is_class() const { return _is_class; }

  // Helper function for take_from_committed: allocate padding chunks
  // until top is at the given address.
  void allocate_padding_chunks_until_top_is_at(MetaWord* target_top);

 public:

  VirtualSpaceNode(bool is_class, size_t byte_size);
  VirtualSpaceNode(bool is_class, ReservedSpace rs) :
    _is_class(is_class), _top(NULL), _next(NULL), _rs(rs), _container_count(0), _occupancy_map(NULL) {}
  ~VirtualSpaceNode();

  // Convenience functions for logical bottom and end
  MetaWord* bottom() const { return (MetaWord*) _virtual_space.low(); }
  MetaWord* end() const { return (MetaWord*) _virtual_space.high(); }

  const OccupancyMap* occupancy_map() const { return _occupancy_map; }
  OccupancyMap* occupancy_map() { return _occupancy_map; }

  bool contains(const void* ptr) { return ptr >= low() && ptr < high(); }

  size_t reserved_words() const  { return _virtual_space.reserved_size() / BytesPerWord; }
  size_t committed_words() const { return _virtual_space.actual_committed_size() / BytesPerWord; }

  bool is_pre_committed() const { return _virtual_space.special(); }

  // address of next available space in _virtual_space;
  // Accessors
  VirtualSpaceNode* next() { return _next; }
  void set_next(VirtualSpaceNode* v) { _next = v; }

  void set_reserved(MemRegion const v) { _reserved = v; }
  void set_top(MetaWord* v) { _top = v; }

  // Accessors
  MemRegion* reserved() { return &_reserved; }
  VirtualSpace* virtual_space() const { return (VirtualSpace*) &_virtual_space; }

  // Returns true if "word_size" is available in the VirtualSpace
  bool is_available(size_t word_size) { return word_size <= pointer_delta(end(), _top, sizeof(MetaWord)); }

  MetaWord* top() const { return _top; }
  void inc_top(size_t word_size) { _top += word_size; }

  uintx container_count() { return _container_count; }
  void inc_container_count();
  void dec_container_count();
#ifdef ASSERT
  uintx container_count_slow();
  void verify_container_count();
#endif

  // used and capacity in this single entry in the list
  size_t used_words_in_vs() const;
  size_t capacity_words_in_vs() const;

  bool initialize();

  // get space from the virtual space
  Metachunk* take_from_committed(size_t chunk_word_size);

  // Allocate a chunk from the virtual space and return it.
  Metachunk* get_chunk_vs(size_t chunk_word_size);

  // Expands/shrinks the committed space in a virtual space.  Delegates
  // to Virtualspace
  bool expand_by(size_t min_words, size_t preferred_words);

  // In preparation for deleting this node, remove all the chunks
  // in the node from any freelist.
  void purge(ChunkManager* chunk_manager);

  // If an allocation doesn't fit in the current node a new node is created.
  // Allocate chunks out of the remaining committed space in this node
  // to avoid wasting that memory.
  // This always adds up because all the chunk sizes are multiples of
  // the smallest chunk size.
  void retire(ChunkManager* chunk_manager);


  void print_on(outputStream* st) const;
  void print_map(outputStream* st, bool is_class) const;

  // Debug support
  DEBUG_ONLY(void mangle();)
  // Verify counters, all chunks in this list node and the occupancy map.
  DEBUG_ONLY(void verify();)
  // Verify that all free chunks in this node are ideally merged
  // (there not should be multiple small chunks where a large chunk could exist.)
  DEBUG_ONLY(void verify_free_chunks_are_ideally_merged();)

};

#define assert_is_aligned(value, alignment)                  \
  assert(is_aligned((value), (alignment)),                   \
         SIZE_FORMAT_HEX " is not aligned to "               \
         SIZE_FORMAT, (size_t)(uintptr_t)value, (alignment))

// Decide if large pages should be committed when the memory is reserved.
static bool should_commit_large_pages_when_reserving(size_t bytes) {
  if (UseLargePages && UseLargePagesInMetaspace && !os::can_commit_large_page_memory()) {
    size_t words = bytes / BytesPerWord;
    bool is_class = false; // We never reserve large pages for the class space.
    if (MetaspaceGC::can_expand(words, is_class) &&
        MetaspaceGC::allowed_expansion() >= words) {
      return true;
    }
  }

  return false;
}

  // byte_size is the size of the associated virtualspace.
VirtualSpaceNode::VirtualSpaceNode(bool is_class, size_t bytes) :
  _is_class(is_class), _top(NULL), _next(NULL), _rs(), _container_count(0), _occupancy_map(NULL) {
  assert_is_aligned(bytes, Metaspace::reserve_alignment());
  bool large_pages = should_commit_large_pages_when_reserving(bytes);
  _rs = ReservedSpace(bytes, Metaspace::reserve_alignment(), large_pages);

  if (_rs.is_reserved()) {
    assert(_rs.base() != NULL, "Catch if we get a NULL address");
    assert(_rs.size() != 0, "Catch if we get a 0 size");
    assert_is_aligned(_rs.base(), Metaspace::reserve_alignment());
    assert_is_aligned(_rs.size(), Metaspace::reserve_alignment());

    MemTracker::record_virtual_memory_type((address)_rs.base(), mtClass);
  }
}

void VirtualSpaceNode::purge(ChunkManager* chunk_manager) {
  DEBUG_ONLY(this->verify();)
  Metachunk* chunk = first_chunk();
  Metachunk* invalid_chunk = (Metachunk*) top();
  while (chunk < invalid_chunk ) {
    assert(chunk->is_tagged_free(), "Should be tagged free");
    MetaWord* next = ((MetaWord*)chunk) + chunk->word_size();
    chunk_manager->remove_chunk(chunk);
    chunk->remove_sentinel();
    assert(chunk->next() == NULL &&
           chunk->prev() == NULL,
           "Was not removed from its list");
    chunk = (Metachunk*) next;
  }
}

void VirtualSpaceNode::print_map(outputStream* st, bool is_class) const {

  if (bottom() == top()) {
    return;
  }

  const size_t spec_chunk_size = is_class ? ClassSpecializedChunk : SpecializedChunk;
  const size_t small_chunk_size = is_class ? ClassSmallChunk : SmallChunk;
  const size_t med_chunk_size = is_class ? ClassMediumChunk : MediumChunk;

  int line_len = 100;
  const size_t section_len = align_up(spec_chunk_size * line_len, med_chunk_size);
  line_len = (int)(section_len / spec_chunk_size);

  static const int NUM_LINES = 4;

  char* lines[NUM_LINES];
  for (int i = 0; i < NUM_LINES; i ++) {
    lines[i] = (char*)os::malloc(line_len, mtInternal);
  }
  int pos = 0;
  const MetaWord* p = bottom();
  const Metachunk* chunk = (const Metachunk*)p;
  const MetaWord* chunk_end = p + chunk->word_size();
  while (p < top()) {
    if (pos == line_len) {
      pos = 0;
      for (int i = 0; i < NUM_LINES; i ++) {
        st->fill_to(22);
        st->print_raw(lines[i], line_len);
        st->cr();
      }
    }
    if (pos == 0) {
      st->print(PTR_FORMAT ":", p2i(p));
    }
    if (p == chunk_end) {
      chunk = (Metachunk*)p;
      chunk_end = p + chunk->word_size();
    }
    // line 1: chunk starting points (a dot if that area is a chunk start).
    lines[0][pos] = p == (const MetaWord*)chunk ? '.' : ' ';

    // Line 2: chunk type (x=spec, s=small, m=medium, h=humongous), uppercase if
    // chunk is in use.
    const bool chunk_is_free = ((Metachunk*)chunk)->is_tagged_free();
    if (chunk->word_size() == spec_chunk_size) {
      lines[1][pos] = chunk_is_free ? 'x' : 'X';
    } else if (chunk->word_size() == small_chunk_size) {
      lines[1][pos] = chunk_is_free ? 's' : 'S';
    } else if (chunk->word_size() == med_chunk_size) {
      lines[1][pos] = chunk_is_free ? 'm' : 'M';
    } else if (chunk->word_size() > med_chunk_size) {
      lines[1][pos] = chunk_is_free ? 'h' : 'H';
    } else {
      ShouldNotReachHere();
    }

    // Line 3: chunk origin
    const ChunkOrigin origin = chunk->get_origin();
    lines[2][pos] = origin == origin_normal ? ' ' : '0' + (int) origin;

    // Line 4: Virgin chunk? Virgin chunks are chunks created as a byproduct of padding or splitting,
    //         but were never used.
    lines[3][pos] = chunk->get_use_count() > 0 ? ' ' : 'v';

    p += spec_chunk_size;
    pos ++;
  }
  if (pos > 0) {
    for (int i = 0; i < NUM_LINES; i ++) {
      st->fill_to(22);
      st->print_raw(lines[i], line_len);
      st->cr();
    }
  }
  for (int i = 0; i < NUM_LINES; i ++) {
    os::free(lines[i]);
  }
}


#ifdef ASSERT
uintx VirtualSpaceNode::container_count_slow() {
  uintx count = 0;
  Metachunk* chunk = first_chunk();
  Metachunk* invalid_chunk = (Metachunk*) top();
  while (chunk < invalid_chunk ) {
    MetaWord* next = ((MetaWord*)chunk) + chunk->word_size();
    do_verify_chunk(chunk);
    // Don't count the chunks on the free lists.  Those are
    // still part of the VirtualSpaceNode but not currently
    // counted.
    if (!chunk->is_tagged_free()) {
      count++;
    }
    chunk = (Metachunk*) next;
  }
  return count;
}
#endif

#ifdef ASSERT
// Verify counters, all chunks in this list node and the occupancy map.
void VirtualSpaceNode::verify() {
  uintx num_in_use_chunks = 0;
  Metachunk* chunk = first_chunk();
  Metachunk* invalid_chunk = (Metachunk*) top();

  // Iterate the chunks in this node and verify each chunk.
  while (chunk < invalid_chunk ) {
    DEBUG_ONLY(do_verify_chunk(chunk);)
    if (!chunk->is_tagged_free()) {
      num_in_use_chunks ++;
    }
    MetaWord* next = ((MetaWord*)chunk) + chunk->word_size();
    chunk = (Metachunk*) next;
  }
  assert(_container_count == num_in_use_chunks, "Container count mismatch (real: " UINTX_FORMAT
         ", counter: " UINTX_FORMAT ".", num_in_use_chunks, _container_count);
  // Also verify the occupancy map.
  occupancy_map()->verify(this->bottom(), this->top());
}
#endif // ASSERT

#ifdef ASSERT
// Verify that all free chunks in this node are ideally merged
// (there not should be multiple small chunks where a large chunk could exist.)
void VirtualSpaceNode::verify_free_chunks_are_ideally_merged() {
  Metachunk* chunk = first_chunk();
  Metachunk* invalid_chunk = (Metachunk*) top();
  // Shorthands.
  const size_t size_med = (is_class() ? ClassMediumChunk : MediumChunk) * BytesPerWord;
  const size_t size_small = (is_class() ? ClassSmallChunk : SmallChunk) * BytesPerWord;
  int num_free_chunks_since_last_med_boundary = -1;
  int num_free_chunks_since_last_small_boundary = -1;
  while (chunk < invalid_chunk ) {
    // Test for missed chunk merge opportunities: count number of free chunks since last chunk boundary.
    // Reset the counter when encountering a non-free chunk.
    if (chunk->get_chunk_type() != HumongousIndex) {
      if (chunk->is_tagged_free()) {
        // Count successive free, non-humongous chunks.
        if (is_aligned(chunk, size_small)) {
          assert(num_free_chunks_since_last_small_boundary <= 1,
                 "Missed chunk merge opportunity at " PTR_FORMAT " for chunk size " SIZE_FORMAT_HEX ".", p2i(chunk) - size_small, size_small);
          num_free_chunks_since_last_small_boundary = 0;
        } else if (num_free_chunks_since_last_small_boundary != -1) {
          num_free_chunks_since_last_small_boundary ++;
        }
        if (is_aligned(chunk, size_med)) {
          assert(num_free_chunks_since_last_med_boundary <= 1,
                 "Missed chunk merge opportunity at " PTR_FORMAT " for chunk size " SIZE_FORMAT_HEX ".", p2i(chunk) - size_med, size_med);
          num_free_chunks_since_last_med_boundary = 0;
        } else if (num_free_chunks_since_last_med_boundary != -1) {
          num_free_chunks_since_last_med_boundary ++;
        }
      } else {
        // Encountering a non-free chunk, reset counters.
        num_free_chunks_since_last_med_boundary = -1;
        num_free_chunks_since_last_small_boundary = -1;
      }
    } else {
      // One cannot merge areas with a humongous chunk in the middle. Reset counters.
      num_free_chunks_since_last_med_boundary = -1;
      num_free_chunks_since_last_small_boundary = -1;
    }

    MetaWord* next = ((MetaWord*)chunk) + chunk->word_size();
    chunk = (Metachunk*) next;
  }
}
#endif // ASSERT

// List of VirtualSpaces for metadata allocation.
class VirtualSpaceList : public CHeapObj<mtClass> {
  friend class VirtualSpaceNode;

  enum VirtualSpaceSizes {
    VirtualSpaceSize = 256 * K
  };

  // Head of the list
  VirtualSpaceNode* _virtual_space_list;
  // virtual space currently being used for allocations
  VirtualSpaceNode* _current_virtual_space;

  // Is this VirtualSpaceList used for the compressed class space
  bool _is_class;

  // Sum of reserved and committed memory in the virtual spaces
  size_t _reserved_words;
  size_t _committed_words;

  // Number of virtual spaces
  size_t _virtual_space_count;

  ~VirtualSpaceList();

  VirtualSpaceNode* virtual_space_list() const { return _virtual_space_list; }

  void set_virtual_space_list(VirtualSpaceNode* v) {
    _virtual_space_list = v;
  }
  void set_current_virtual_space(VirtualSpaceNode* v) {
    _current_virtual_space = v;
  }

  void link_vs(VirtualSpaceNode* new_entry);

  // Get another virtual space and add it to the list.  This
  // is typically prompted by a failed attempt to allocate a chunk
  // and is typically followed by the allocation of a chunk.
  bool create_new_virtual_space(size_t vs_word_size);

  // Chunk up the unused committed space in the current
  // virtual space and add the chunks to the free list.
  void retire_current_virtual_space();

 public:
  VirtualSpaceList(size_t word_size);
  VirtualSpaceList(ReservedSpace rs);

  size_t free_bytes();

  Metachunk* get_new_chunk(size_t chunk_word_size,
                           size_t suggested_commit_granularity);

  bool expand_node_by(VirtualSpaceNode* node,
                      size_t min_words,
                      size_t preferred_words);

  bool expand_by(size_t min_words,
                 size_t preferred_words);

  VirtualSpaceNode* current_virtual_space() {
    return _current_virtual_space;
  }

  bool is_class() const { return _is_class; }

  bool initialization_succeeded() { return _virtual_space_list != NULL; }

  size_t reserved_words()  { return _reserved_words; }
  size_t reserved_bytes()  { return reserved_words() * BytesPerWord; }
  size_t committed_words() { return _committed_words; }
  size_t committed_bytes() { return committed_words() * BytesPerWord; }

  void inc_reserved_words(size_t v);
  void dec_reserved_words(size_t v);
  void inc_committed_words(size_t v);
  void dec_committed_words(size_t v);
  void inc_virtual_space_count();
  void dec_virtual_space_count();

  bool contains(const void* ptr);

  // Unlink empty VirtualSpaceNodes and free it.
  void purge(ChunkManager* chunk_manager);

  void print_on(outputStream* st) const;
  void print_map(outputStream* st) const;

  class VirtualSpaceListIterator : public StackObj {
    VirtualSpaceNode* _virtual_spaces;
   public:
    VirtualSpaceListIterator(VirtualSpaceNode* virtual_spaces) :
      _virtual_spaces(virtual_spaces) {}

    bool repeat() {
      return _virtual_spaces != NULL;
    }

    VirtualSpaceNode* get_next() {
      VirtualSpaceNode* result = _virtual_spaces;
      if (_virtual_spaces != NULL) {
        _virtual_spaces = _virtual_spaces->next();
      }
      return result;
    }
  };
};

class Metadebug : AllStatic {
  // Debugging support for Metaspaces
  static int _allocation_fail_alot_count;

 public:

  static void init_allocation_fail_alot_count();
#ifdef ASSERT
  static bool test_metadata_failure();
#endif
};

int Metadebug::_allocation_fail_alot_count = 0;

//  SpaceManager - used by Metaspace to handle allocations
class SpaceManager : public CHeapObj<mtClass> {
  friend class ClassLoaderMetaspace;
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
  Metachunk* _chunks_in_use[NumberOfInUseLists];
  Metachunk* _current_chunk;

  // Maximum number of small chunks to allocate to a SpaceManager
  static uint const _small_chunk_limit;

  // Maximum number of specialize chunks to allocate for anonymous and delegating
  // metadata space to a SpaceManager
  static uint const _anon_and_delegating_metadata_specialize_chunk_limit;

  // Sum of all space in allocated chunks
  size_t _allocated_blocks_words;

  // Sum of all allocated chunks
  size_t _allocated_chunks_words;
  size_t _allocated_chunks_count;

  // Free lists of blocks are per SpaceManager since they
  // are assumed to be in chunks in use by the SpaceManager
  // and all chunks in use by a SpaceManager are freed when
  // the class loader using the SpaceManager is collected.
  BlockFreelist* _block_freelists;

 private:
  // Accessors
  Metachunk* chunks_in_use(ChunkIndex index) const { return _chunks_in_use[index]; }
  void set_chunks_in_use(ChunkIndex index, Metachunk* v) {
    _chunks_in_use[index] = v;
  }

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

 protected:
  void initialize();

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

  size_t allocated_blocks_words() const { return _allocated_blocks_words; }
  size_t allocated_blocks_bytes() const { return _allocated_blocks_words * BytesPerWord; }
  size_t allocated_chunks_words() const { return _allocated_chunks_words; }
  size_t allocated_chunks_bytes() const { return _allocated_chunks_words * BytesPerWord; }
  size_t allocated_chunks_count() const { return _allocated_chunks_count; }

  bool is_humongous(size_t word_size) { return word_size > medium_chunk_size(); }

  // Increment the per Metaspace and global running sums for Metachunks
  // by the given size.  This is used when a Metachunk to added to
  // the in-use list.
  void inc_size_metrics(size_t words);
  // Increment the per Metaspace and global running sums Metablocks by the given
  // size.  This is used when a Metablock is allocated.
  void inc_used_metrics(size_t words);
  // Delete the portion of the running sums for this SpaceManager. That is,
  // the globals running sums for the Metachunks and Metablocks are
  // decremented for all the Metachunks in-use by this SpaceManager.
  void dec_total_from_size_metrics();

  // Adjust the initial chunk size to match one of the fixed chunk list sizes,
  // or return the unadjusted size if the requested size is humongous.
  static size_t adjust_initial_chunk_size(size_t requested, bool is_class_space);
  size_t adjust_initial_chunk_size(size_t requested) const;

  // Get the initial chunks size for this metaspace type.
  size_t get_initial_chunk_size(Metaspace::MetaspaceType type) const;

  size_t sum_capacity_in_chunks_in_use() const;
  size_t sum_used_in_chunks_in_use() const;
  size_t sum_free_in_chunks_in_use() const;
  size_t sum_waste_in_chunks_in_use() const;
  size_t sum_waste_in_chunks_in_use(ChunkIndex index ) const;

  size_t sum_count_in_chunks_in_use();
  size_t sum_count_in_chunks_in_use(ChunkIndex i);

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

  void dump(outputStream* const out) const;
  void print_on(outputStream* st) const;
  void locked_print_chunks_in_use_on(outputStream* st) const;

  void verify();
  void verify_chunk_size(Metachunk* chunk);
#ifdef ASSERT
  void verify_allocated_blocks_words();
#endif

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
};

uint const SpaceManager::_small_chunk_limit = 4;
uint const SpaceManager::_anon_and_delegating_metadata_specialize_chunk_limit = 4;

void VirtualSpaceNode::inc_container_count() {
  assert_lock_strong(MetaspaceExpand_lock);
  _container_count++;
}

void VirtualSpaceNode::dec_container_count() {
  assert_lock_strong(MetaspaceExpand_lock);
  _container_count--;
}

#ifdef ASSERT
void VirtualSpaceNode::verify_container_count() {
  assert(_container_count == container_count_slow(),
         "Inconsistency in container_count _container_count " UINTX_FORMAT
         " container_count_slow() " UINTX_FORMAT, _container_count, container_count_slow());
}
#endif

// BlockFreelist methods

BlockFreelist::BlockFreelist() : _dictionary(new BlockTreeDictionary()), _small_blocks(NULL) {}

BlockFreelist::~BlockFreelist() {
  delete _dictionary;
  if (_small_blocks != NULL) {
    delete _small_blocks;
  }
}

void BlockFreelist::return_block(MetaWord* p, size_t word_size) {
  assert(word_size >= SmallBlocks::small_block_min_size(), "never return dark matter");

  Metablock* free_chunk = ::new (p) Metablock(word_size);
  if (word_size < SmallBlocks::small_block_max_size()) {
    small_blocks()->return_block(free_chunk, word_size);
  } else {
  dictionary()->return_chunk(free_chunk);
}
  log_trace(gc, metaspace, freelist, blocks)("returning block at " INTPTR_FORMAT " size = "
            SIZE_FORMAT, p2i(free_chunk), word_size);
}

MetaWord* BlockFreelist::get_block(size_t word_size) {
  assert(word_size >= SmallBlocks::small_block_min_size(), "never get dark matter");

  // Try small_blocks first.
  if (word_size < SmallBlocks::small_block_max_size()) {
    // Don't create small_blocks() until needed.  small_blocks() allocates the small block list for
    // this space manager.
    MetaWord* new_block = (MetaWord*) small_blocks()->get_block(word_size);
    if (new_block != NULL) {
      log_trace(gc, metaspace, freelist, blocks)("getting block at " INTPTR_FORMAT " size = " SIZE_FORMAT,
              p2i(new_block), word_size);
      return new_block;
    }
  }

  if (word_size < BlockFreelist::min_dictionary_size()) {
    // If allocation in small blocks fails, this is Dark Matter.  Too small for dictionary.
    return NULL;
  }

  Metablock* free_block = dictionary()->get_chunk(word_size);
  if (free_block == NULL) {
    return NULL;
  }

  const size_t block_size = free_block->size();
  if (block_size > WasteMultiplier * word_size) {
    return_block((MetaWord*)free_block, block_size);
    return NULL;
  }

  MetaWord* new_block = (MetaWord*)free_block;
  assert(block_size >= word_size, "Incorrect size of block from freelist");
  const size_t unused = block_size - word_size;
  if (unused >= SmallBlocks::small_block_min_size()) {
    return_block(new_block + word_size, unused);
  }

  log_trace(gc, metaspace, freelist, blocks)("getting block at " INTPTR_FORMAT " size = " SIZE_FORMAT,
            p2i(new_block), word_size);
  return new_block;
}

void BlockFreelist::print_on(outputStream* st) const {
  dictionary()->print_free_lists(st);
  if (_small_blocks != NULL) {
    _small_blocks->print_on(st);
  }
}

// VirtualSpaceNode methods

VirtualSpaceNode::~VirtualSpaceNode() {
  _rs.release();
  if (_occupancy_map != NULL) {
    delete _occupancy_map;
  }
#ifdef ASSERT
  size_t word_size = sizeof(*this) / BytesPerWord;
  Copy::fill_to_words((HeapWord*) this, word_size, 0xf1f1f1f1);
#endif
}

size_t VirtualSpaceNode::used_words_in_vs() const {
  return pointer_delta(top(), bottom(), sizeof(MetaWord));
}

// Space committed in the VirtualSpace
size_t VirtualSpaceNode::capacity_words_in_vs() const {
  return pointer_delta(end(), bottom(), sizeof(MetaWord));
}

size_t VirtualSpaceNode::free_words_in_vs() const {
  return pointer_delta(end(), top(), sizeof(MetaWord));
}

// Given an address larger than top(), allocate padding chunks until top is at the given address.
void VirtualSpaceNode::allocate_padding_chunks_until_top_is_at(MetaWord* target_top) {

  assert(target_top > top(), "Sanity");

  // Padding chunks are added to the freelist.
  ChunkManager* const chunk_manager = Metaspace::get_chunk_manager(this->is_class());

  // shorthands
  const size_t spec_word_size = chunk_manager->specialized_chunk_word_size();
  const size_t small_word_size = chunk_manager->small_chunk_word_size();
  const size_t med_word_size = chunk_manager->medium_chunk_word_size();

  while (top() < target_top) {

    // We could make this coding more generic, but right now we only deal with two possible chunk sizes
    // for padding chunks, so it is not worth it.
    size_t padding_chunk_word_size = small_word_size;
    if (is_aligned(top(), small_word_size * sizeof(MetaWord)) == false) {
      assert_is_aligned(top(), spec_word_size * sizeof(MetaWord)); // Should always hold true.
      padding_chunk_word_size = spec_word_size;
    }
    MetaWord* here = top();
    assert_is_aligned(here, padding_chunk_word_size * sizeof(MetaWord));
    inc_top(padding_chunk_word_size);

    // Create new padding chunk.
    ChunkIndex padding_chunk_type = get_chunk_type_by_size(padding_chunk_word_size, is_class());
    assert(padding_chunk_type == SpecializedIndex || padding_chunk_type == SmallIndex, "sanity");

    Metachunk* const padding_chunk =
      ::new (here) Metachunk(padding_chunk_type, is_class(), padding_chunk_word_size, this);
    assert(padding_chunk == (Metachunk*)here, "Sanity");
    DEBUG_ONLY(padding_chunk->set_origin(origin_pad);)
    log_trace(gc, metaspace, freelist)("Created padding chunk in %s at "
                                       PTR_FORMAT ", size " SIZE_FORMAT_HEX ".",
                                       (is_class() ? "class space " : "metaspace"),
                                       p2i(padding_chunk), padding_chunk->word_size() * sizeof(MetaWord));

    // Mark chunk start in occupancy map.
    occupancy_map()->set_chunk_starts_at_address((MetaWord*)padding_chunk, true);

    // Chunks are born as in-use (see MetaChunk ctor). So, before returning
    // the padding chunk to its chunk manager, mark it as in use (ChunkManager
    // will assert that).
    do_update_in_use_info_for_chunk(padding_chunk, true);

    // Return Chunk to freelist.
    inc_container_count();
    chunk_manager->return_single_chunk(padding_chunk_type, padding_chunk);
    // Please note: at this point, ChunkManager::return_single_chunk()
    // may already have merged the padding chunk with neighboring chunks, so
    // it may have vanished at this point. Do not reference the padding
    // chunk beyond this point.
  }

  assert(top() == target_top, "Sanity");

} // allocate_padding_chunks_until_top_is_at()

// Allocates the chunk from the virtual space only.
// This interface is also used internally for debugging.  Not all
// chunks removed here are necessarily used for allocation.
Metachunk* VirtualSpaceNode::take_from_committed(size_t chunk_word_size) {
  // Non-humongous chunks are to be allocated aligned to their chunk
  // size. So, start addresses of medium chunks are aligned to medium
  // chunk size, those of small chunks to small chunk size and so
  // forth. This facilitates merging of free chunks and reduces
  // fragmentation. Chunk sizes are spec < small < medium, with each
  // larger chunk size being a multiple of the next smaller chunk
  // size.
  // Because of this alignment, me may need to create a number of padding
  // chunks. These chunks are created and added to the freelist.

  // The chunk manager to which we will give our padding chunks.
  ChunkManager* const chunk_manager = Metaspace::get_chunk_manager(this->is_class());

  // shorthands
  const size_t spec_word_size = chunk_manager->specialized_chunk_word_size();
  const size_t small_word_size = chunk_manager->small_chunk_word_size();
  const size_t med_word_size = chunk_manager->medium_chunk_word_size();

  assert(chunk_word_size == spec_word_size || chunk_word_size == small_word_size ||
         chunk_word_size >= med_word_size, "Invalid chunk size requested.");

  // Chunk alignment (in bytes) == chunk size unless humongous.
  // Humongous chunks are aligned to the smallest chunk size (spec).
  const size_t required_chunk_alignment = (chunk_word_size > med_word_size ?
                                           spec_word_size : chunk_word_size) * sizeof(MetaWord);

  // Do we have enough space to create the requested chunk plus
  // any padding chunks needed?
  MetaWord* const next_aligned =
    static_cast<MetaWord*>(align_up(top(), required_chunk_alignment));
  if (!is_available((next_aligned - top()) + chunk_word_size)) {
    return NULL;
  }

  // Before allocating the requested chunk, allocate padding chunks if necessary.
  // We only need to do this for small or medium chunks: specialized chunks are the
  // smallest size, hence always aligned. Homungous chunks are allocated unaligned
  // (implicitly, also aligned to smallest chunk size).
  if ((chunk_word_size == med_word_size || chunk_word_size == small_word_size) && next_aligned > top())  {
    log_trace(gc, metaspace, freelist)("Creating padding chunks in %s between %p and %p...",
        (is_class() ? "class space " : "metaspace"),
        top(), next_aligned);
    allocate_padding_chunks_until_top_is_at(next_aligned);
    // Now, top should be aligned correctly.
    assert_is_aligned(top(), required_chunk_alignment);
  }

  // Now, top should be aligned correctly.
  assert_is_aligned(top(), required_chunk_alignment);

  // Bottom of the new chunk
  MetaWord* chunk_limit = top();
  assert(chunk_limit != NULL, "Not safe to call this method");

  // The virtual spaces are always expanded by the
  // commit granularity to enforce the following condition.
  // Without this the is_available check will not work correctly.
  assert(_virtual_space.committed_size() == _virtual_space.actual_committed_size(),
      "The committed memory doesn't match the expanded memory.");

  if (!is_available(chunk_word_size)) {
    LogTarget(Debug, gc, metaspace, freelist) lt;
    if (lt.is_enabled()) {
      LogStream ls(lt);
      ls.print("VirtualSpaceNode::take_from_committed() not available " SIZE_FORMAT " words ", chunk_word_size);
      // Dump some information about the virtual space that is nearly full
      print_on(&ls);
    }
    return NULL;
  }

  // Take the space  (bump top on the current virtual space).
  inc_top(chunk_word_size);

  // Initialize the chunk
  ChunkIndex chunk_type = get_chunk_type_by_size(chunk_word_size, is_class());
  Metachunk* result = ::new (chunk_limit) Metachunk(chunk_type, is_class(), chunk_word_size, this);
  assert(result == (Metachunk*)chunk_limit, "Sanity");
  occupancy_map()->set_chunk_starts_at_address((MetaWord*)result, true);
  do_update_in_use_info_for_chunk(result, true);

  inc_container_count();

  if (VerifyMetaspace) {
    DEBUG_ONLY(chunk_manager->locked_verify());
    DEBUG_ONLY(this->verify());
  }

  DEBUG_ONLY(do_verify_chunk(result));

  result->inc_use_count();

  return result;
}


// Expand the virtual space (commit more of the reserved space)
bool VirtualSpaceNode::expand_by(size_t min_words, size_t preferred_words) {
  size_t min_bytes = min_words * BytesPerWord;
  size_t preferred_bytes = preferred_words * BytesPerWord;

  size_t uncommitted = virtual_space()->reserved_size() - virtual_space()->actual_committed_size();

  if (uncommitted < min_bytes) {
    return false;
  }

  size_t commit = MIN2(preferred_bytes, uncommitted);
  bool result = virtual_space()->expand_by(commit, false);

  if (result) {
    log_trace(gc, metaspace, freelist)("Expanded %s virtual space list node by " SIZE_FORMAT " words.",
              (is_class() ? "class" : "non-class"), commit);
  } else {
    log_trace(gc, metaspace, freelist)("Failed to expand %s virtual space list node by " SIZE_FORMAT " words.",
              (is_class() ? "class" : "non-class"), commit);
  }

  assert(result, "Failed to commit memory");

  return result;
}

Metachunk* VirtualSpaceNode::get_chunk_vs(size_t chunk_word_size) {
  assert_lock_strong(MetaspaceExpand_lock);
  Metachunk* result = take_from_committed(chunk_word_size);
  return result;
}

bool VirtualSpaceNode::initialize() {

  if (!_rs.is_reserved()) {
    return false;
  }

  // These are necessary restriction to make sure that the virtual space always
  // grows in steps of Metaspace::commit_alignment(). If both base and size are
  // aligned only the middle alignment of the VirtualSpace is used.
  assert_is_aligned(_rs.base(), Metaspace::commit_alignment());
  assert_is_aligned(_rs.size(), Metaspace::commit_alignment());

  // ReservedSpaces marked as special will have the entire memory
  // pre-committed. Setting a committed size will make sure that
  // committed_size and actual_committed_size agrees.
  size_t pre_committed_size = _rs.special() ? _rs.size() : 0;

  bool result = virtual_space()->initialize_with_granularity(_rs, pre_committed_size,
                                            Metaspace::commit_alignment());
  if (result) {
    assert(virtual_space()->committed_size() == virtual_space()->actual_committed_size(),
        "Checking that the pre-committed memory was registered by the VirtualSpace");

    set_top((MetaWord*)virtual_space()->low());
    set_reserved(MemRegion((HeapWord*)_rs.base(),
                 (HeapWord*)(_rs.base() + _rs.size())));

    assert(reserved()->start() == (HeapWord*) _rs.base(),
           "Reserved start was not set properly " PTR_FORMAT
           " != " PTR_FORMAT, p2i(reserved()->start()), p2i(_rs.base()));
    assert(reserved()->word_size() == _rs.size() / BytesPerWord,
           "Reserved size was not set properly " SIZE_FORMAT
           " != " SIZE_FORMAT, reserved()->word_size(),
           _rs.size() / BytesPerWord);
  }

  // Initialize Occupancy Map.
  const size_t smallest_chunk_size = is_class() ? ClassSpecializedChunk : SpecializedChunk;
  _occupancy_map = new OccupancyMap(bottom(), reserved_words(), smallest_chunk_size);

  return result;
}

void VirtualSpaceNode::print_on(outputStream* st) const {
  size_t used = used_words_in_vs();
  size_t capacity = capacity_words_in_vs();
  VirtualSpace* vs = virtual_space();
  st->print_cr("   space @ " PTR_FORMAT " " SIZE_FORMAT "K, " SIZE_FORMAT_W(3) "%% used "
           "[" PTR_FORMAT ", " PTR_FORMAT ", "
           PTR_FORMAT ", " PTR_FORMAT ")",
           p2i(vs), capacity / K,
           capacity == 0 ? 0 : used * 100 / capacity,
           p2i(bottom()), p2i(top()), p2i(end()),
           p2i(vs->high_boundary()));
}

#ifdef ASSERT
void VirtualSpaceNode::mangle() {
  size_t word_size = capacity_words_in_vs();
  Copy::fill_to_words((HeapWord*) low(), word_size, 0xf1f1f1f1);
}
#endif // ASSERT

// VirtualSpaceList methods
// Space allocated from the VirtualSpace

VirtualSpaceList::~VirtualSpaceList() {
  VirtualSpaceListIterator iter(virtual_space_list());
  while (iter.repeat()) {
    VirtualSpaceNode* vsl = iter.get_next();
    delete vsl;
  }
}

void VirtualSpaceList::inc_reserved_words(size_t v) {
  assert_lock_strong(MetaspaceExpand_lock);
  _reserved_words = _reserved_words + v;
}
void VirtualSpaceList::dec_reserved_words(size_t v) {
  assert_lock_strong(MetaspaceExpand_lock);
  _reserved_words = _reserved_words - v;
}

#define assert_committed_below_limit()                        \
  assert(MetaspaceUtils::committed_bytes() <= MaxMetaspaceSize, \
         "Too much committed memory. Committed: " SIZE_FORMAT \
         " limit (MaxMetaspaceSize): " SIZE_FORMAT,           \
          MetaspaceUtils::committed_bytes(), MaxMetaspaceSize);

void VirtualSpaceList::inc_committed_words(size_t v) {
  assert_lock_strong(MetaspaceExpand_lock);
  _committed_words = _committed_words + v;

  assert_committed_below_limit();
}
void VirtualSpaceList::dec_committed_words(size_t v) {
  assert_lock_strong(MetaspaceExpand_lock);
  _committed_words = _committed_words - v;

  assert_committed_below_limit();
}

void VirtualSpaceList::inc_virtual_space_count() {
  assert_lock_strong(MetaspaceExpand_lock);
  _virtual_space_count++;
}
void VirtualSpaceList::dec_virtual_space_count() {
  assert_lock_strong(MetaspaceExpand_lock);
  _virtual_space_count--;
}

void ChunkManager::remove_chunk(Metachunk* chunk) {
  size_t word_size = chunk->word_size();
  ChunkIndex index = list_index(word_size);
  if (index != HumongousIndex) {
    free_chunks(index)->remove_chunk(chunk);
  } else {
    humongous_dictionary()->remove_chunk(chunk);
  }

  // Chunk has been removed from the chunks free list, update counters.
  account_for_removed_chunk(chunk);
}

bool ChunkManager::attempt_to_coalesce_around_chunk(Metachunk* chunk, ChunkIndex target_chunk_type) {
  assert_lock_strong(MetaspaceExpand_lock);
  assert(chunk != NULL, "invalid chunk pointer");
  // Check for valid merge combinations.
  assert((chunk->get_chunk_type() == SpecializedIndex &&
          (target_chunk_type == SmallIndex || target_chunk_type == MediumIndex)) ||
         (chunk->get_chunk_type() == SmallIndex && target_chunk_type == MediumIndex),
        "Invalid chunk merge combination.");

  const size_t target_chunk_word_size =
    get_size_for_nonhumongous_chunktype(target_chunk_type, this->is_class());

  // [ prospective merge region )
  MetaWord* const p_merge_region_start =
    (MetaWord*) align_down(chunk, target_chunk_word_size * sizeof(MetaWord));
  MetaWord* const p_merge_region_end =
    p_merge_region_start + target_chunk_word_size;

  // We need the VirtualSpaceNode containing this chunk and its occupancy map.
  VirtualSpaceNode* const vsn = chunk->container();
  OccupancyMap* const ocmap = vsn->occupancy_map();

  // The prospective chunk merge range must be completely contained by the
  // committed range of the virtual space node.
  if (p_merge_region_start < vsn->bottom() || p_merge_region_end > vsn->top()) {
    return false;
  }

  // Only attempt to merge this range if at its start a chunk starts and at its end
  // a chunk ends. If a chunk (can only be humongous) straddles either start or end
  // of that range, we cannot merge.
  if (!ocmap->chunk_starts_at_address(p_merge_region_start)) {
    return false;
  }
  if (p_merge_region_end < vsn->top() &&
      !ocmap->chunk_starts_at_address(p_merge_region_end)) {
    return false;
  }

  // Now check if the prospective merge area contains live chunks. If it does we cannot merge.
  if (ocmap->is_region_in_use(p_merge_region_start, target_chunk_word_size)) {
    return false;
  }

  // Success! Remove all chunks in this region...
  log_trace(gc, metaspace, freelist)("%s: coalescing chunks in area [%p-%p)...",
    (is_class() ? "class space" : "metaspace"),
    p_merge_region_start, p_merge_region_end);

  const int num_chunks_removed =
    remove_chunks_in_area(p_merge_region_start, target_chunk_word_size);

  // ... and create a single new bigger chunk.
  Metachunk* const p_new_chunk =
      ::new (p_merge_region_start) Metachunk(target_chunk_type, is_class(), target_chunk_word_size, vsn);
  assert(p_new_chunk == (Metachunk*)p_merge_region_start, "Sanity");
  p_new_chunk->set_origin(origin_merge);

  log_trace(gc, metaspace, freelist)("%s: created coalesced chunk at %p, size " SIZE_FORMAT_HEX ".",
    (is_class() ? "class space" : "metaspace"),
    p_new_chunk, p_new_chunk->word_size() * sizeof(MetaWord));

  // Fix occupancy map: remove old start bits of the small chunks and set new start bit.
  ocmap->wipe_chunk_start_bits_in_region(p_merge_region_start, target_chunk_word_size);
  ocmap->set_chunk_starts_at_address(p_merge_region_start, true);

  // Mark chunk as free. Note: it is not necessary to update the occupancy
  // map in-use map, because the old chunks were also free, so nothing
  // should have changed.
  p_new_chunk->set_is_tagged_free(true);

  // Add new chunk to its freelist.
  ChunkList* const list = free_chunks(target_chunk_type);
  list->return_chunk_at_head(p_new_chunk);

  // And adjust ChunkManager:: _free_chunks_count (_free_chunks_total
  // should not have changed, because the size of the space should be the same)
  _free_chunks_count -= num_chunks_removed;
  _free_chunks_count ++;

  // VirtualSpaceNode::container_count does not have to be modified:
  // it means "number of active (non-free) chunks", so merging free chunks
  // should not affect that count.

  // At the end of a chunk merge, run verification tests.
  if (VerifyMetaspace) {
    DEBUG_ONLY(this->locked_verify());
    DEBUG_ONLY(vsn->verify());
  }

  return true;
}

// Remove all chunks in the given area - the chunks are supposed to be free -
// from their corresponding freelists. Mark them as invalid.
// - This does not correct the occupancy map.
// - This does not adjust the counters in ChunkManager.
// - Does not adjust container count counter in containing VirtualSpaceNode
// Returns number of chunks removed.
int ChunkManager::remove_chunks_in_area(MetaWord* p, size_t word_size) {
  assert(p != NULL && word_size > 0, "Invalid range.");
  const size_t smallest_chunk_size = get_size_for_nonhumongous_chunktype(SpecializedIndex, is_class());
  assert_is_aligned(word_size, smallest_chunk_size);

  Metachunk* const start = (Metachunk*) p;
  const Metachunk* const end = (Metachunk*)(p + word_size);
  Metachunk* cur = start;
  int num_removed = 0;
  while (cur < end) {
    Metachunk* next = (Metachunk*)(((MetaWord*)cur) + cur->word_size());
    DEBUG_ONLY(do_verify_chunk(cur));
    assert(cur->get_chunk_type() != HumongousIndex, "Unexpected humongous chunk found at %p.", cur);
    assert(cur->is_tagged_free(), "Chunk expected to be free (%p)", cur);
    log_trace(gc, metaspace, freelist)("%s: removing chunk %p, size " SIZE_FORMAT_HEX ".",
      (is_class() ? "class space" : "metaspace"),
      cur, cur->word_size() * sizeof(MetaWord));
    cur->remove_sentinel();
    // Note: cannot call ChunkManager::remove_chunk, because that
    // modifies the counters in ChunkManager, which we do not want. So
    // we call remove_chunk on the freelist directly (see also the
    // splitting function which does the same).
    ChunkList* const list = free_chunks(list_index(cur->word_size()));
    list->remove_chunk(cur);
    num_removed ++;
    cur = next;
  }
  return num_removed;
}

// Walk the list of VirtualSpaceNodes and delete
// nodes with a 0 container_count.  Remove Metachunks in
// the node from their respective freelists.
void VirtualSpaceList::purge(ChunkManager* chunk_manager) {
  assert(SafepointSynchronize::is_at_safepoint(), "must be called at safepoint for contains to work");
  assert_lock_strong(MetaspaceExpand_lock);
  // Don't use a VirtualSpaceListIterator because this
  // list is being changed and a straightforward use of an iterator is not safe.
  VirtualSpaceNode* purged_vsl = NULL;
  VirtualSpaceNode* prev_vsl = virtual_space_list();
  VirtualSpaceNode* next_vsl = prev_vsl;
  while (next_vsl != NULL) {
    VirtualSpaceNode* vsl = next_vsl;
    DEBUG_ONLY(vsl->verify_container_count();)
    next_vsl = vsl->next();
    // Don't free the current virtual space since it will likely
    // be needed soon.
    if (vsl->container_count() == 0 && vsl != current_virtual_space()) {
      log_trace(gc, metaspace, freelist)("Purging VirtualSpaceNode " PTR_FORMAT " (capacity: " SIZE_FORMAT
                                         ", used: " SIZE_FORMAT ").", p2i(vsl), vsl->capacity_words_in_vs(), vsl->used_words_in_vs());
      // Unlink it from the list
      if (prev_vsl == vsl) {
        // This is the case of the current node being the first node.
        assert(vsl == virtual_space_list(), "Expected to be the first node");
        set_virtual_space_list(vsl->next());
      } else {
        prev_vsl->set_next(vsl->next());
      }

      vsl->purge(chunk_manager);
      dec_reserved_words(vsl->reserved_words());
      dec_committed_words(vsl->committed_words());
      dec_virtual_space_count();
      purged_vsl = vsl;
      delete vsl;
    } else {
      prev_vsl = vsl;
    }
  }
#ifdef ASSERT
  if (purged_vsl != NULL) {
    // List should be stable enough to use an iterator here.
    VirtualSpaceListIterator iter(virtual_space_list());
    while (iter.repeat()) {
      VirtualSpaceNode* vsl = iter.get_next();
      assert(vsl != purged_vsl, "Purge of vsl failed");
    }
  }
#endif
}


// This function looks at the mmap regions in the metaspace without locking.
// The chunks are added with store ordering and not deleted except for at
// unloading time during a safepoint.
bool VirtualSpaceList::contains(const void* ptr) {
  // List should be stable enough to use an iterator here because removing virtual
  // space nodes is only allowed at a safepoint.
  VirtualSpaceListIterator iter(virtual_space_list());
  while (iter.repeat()) {
    VirtualSpaceNode* vsn = iter.get_next();
    if (vsn->contains(ptr)) {
      return true;
    }
  }
  return false;
}

void VirtualSpaceList::retire_current_virtual_space() {
  assert_lock_strong(MetaspaceExpand_lock);

  VirtualSpaceNode* vsn = current_virtual_space();

  ChunkManager* cm = is_class() ? Metaspace::chunk_manager_class() :
                                  Metaspace::chunk_manager_metadata();

  vsn->retire(cm);
}

void VirtualSpaceNode::retire(ChunkManager* chunk_manager) {
  DEBUG_ONLY(verify_container_count();)
  assert(this->is_class() == chunk_manager->is_class(), "Wrong ChunkManager?");
  for (int i = (int)MediumIndex; i >= (int)ZeroIndex; --i) {
    ChunkIndex index = (ChunkIndex)i;
    size_t chunk_size = chunk_manager->size_by_index(index);

    while (free_words_in_vs() >= chunk_size) {
      Metachunk* chunk = get_chunk_vs(chunk_size);
      // Chunk will be allocated aligned, so allocation may require
      // additional padding chunks. That may cause above allocation to
      // fail. Just ignore the failed allocation and continue with the
      // next smaller chunk size. As the VirtualSpaceNode comitted
      // size should be a multiple of the smallest chunk size, we
      // should always be able to fill the VirtualSpace completely.
      if (chunk == NULL) {
        break;
      }
      chunk_manager->return_single_chunk(index, chunk);
    }
    DEBUG_ONLY(verify_container_count();)
  }
  assert(free_words_in_vs() == 0, "should be empty now");
}

VirtualSpaceList::VirtualSpaceList(size_t word_size) :
                                   _is_class(false),
                                   _virtual_space_list(NULL),
                                   _current_virtual_space(NULL),
                                   _reserved_words(0),
                                   _committed_words(0),
                                   _virtual_space_count(0) {
  MutexLockerEx cl(MetaspaceExpand_lock,
                   Mutex::_no_safepoint_check_flag);
  create_new_virtual_space(word_size);
}

VirtualSpaceList::VirtualSpaceList(ReservedSpace rs) :
                                   _is_class(true),
                                   _virtual_space_list(NULL),
                                   _current_virtual_space(NULL),
                                   _reserved_words(0),
                                   _committed_words(0),
                                   _virtual_space_count(0) {
  MutexLockerEx cl(MetaspaceExpand_lock,
                   Mutex::_no_safepoint_check_flag);
  VirtualSpaceNode* class_entry = new VirtualSpaceNode(is_class(), rs);
  bool succeeded = class_entry->initialize();
  if (succeeded) {
    link_vs(class_entry);
  }
}

size_t VirtualSpaceList::free_bytes() {
  return current_virtual_space()->free_words_in_vs() * BytesPerWord;
}

// Allocate another meta virtual space and add it to the list.
bool VirtualSpaceList::create_new_virtual_space(size_t vs_word_size) {
  assert_lock_strong(MetaspaceExpand_lock);

  if (is_class()) {
    assert(false, "We currently don't support more than one VirtualSpace for"
                  " the compressed class space. The initialization of the"
                  " CCS uses another code path and should not hit this path.");
    return false;
  }

  if (vs_word_size == 0) {
    assert(false, "vs_word_size should always be at least _reserve_alignment large.");
    return false;
  }

  // Reserve the space
  size_t vs_byte_size = vs_word_size * BytesPerWord;
  assert_is_aligned(vs_byte_size, Metaspace::reserve_alignment());

  // Allocate the meta virtual space and initialize it.
  VirtualSpaceNode* new_entry = new VirtualSpaceNode(is_class(), vs_byte_size);
  if (!new_entry->initialize()) {
    delete new_entry;
    return false;
  } else {
    assert(new_entry->reserved_words() == vs_word_size,
        "Reserved memory size differs from requested memory size");
    // ensure lock-free iteration sees fully initialized node
    OrderAccess::storestore();
    link_vs(new_entry);
    return true;
  }
}

void VirtualSpaceList::link_vs(VirtualSpaceNode* new_entry) {
  if (virtual_space_list() == NULL) {
      set_virtual_space_list(new_entry);
  } else {
    current_virtual_space()->set_next(new_entry);
  }
  set_current_virtual_space(new_entry);
  inc_reserved_words(new_entry->reserved_words());
  inc_committed_words(new_entry->committed_words());
  inc_virtual_space_count();
#ifdef ASSERT
  new_entry->mangle();
#endif
  LogTarget(Trace, gc, metaspace) lt;
  if (lt.is_enabled()) {
    LogStream ls(lt);
    VirtualSpaceNode* vsl = current_virtual_space();
    ResourceMark rm;
    vsl->print_on(&ls);
  }
}

bool VirtualSpaceList::expand_node_by(VirtualSpaceNode* node,
                                      size_t min_words,
                                      size_t preferred_words) {
  size_t before = node->committed_words();

  bool result = node->expand_by(min_words, preferred_words);

  size_t after = node->committed_words();

  // after and before can be the same if the memory was pre-committed.
  assert(after >= before, "Inconsistency");
  inc_committed_words(after - before);

  return result;
}

bool VirtualSpaceList::expand_by(size_t min_words, size_t preferred_words) {
  assert_is_aligned(min_words,       Metaspace::commit_alignment_words());
  assert_is_aligned(preferred_words, Metaspace::commit_alignment_words());
  assert(min_words <= preferred_words, "Invalid arguments");

  const char* const class_or_not = (is_class() ? "class" : "non-class");

  if (!MetaspaceGC::can_expand(min_words, this->is_class())) {
    log_trace(gc, metaspace, freelist)("Cannot expand %s virtual space list.",
              class_or_not);
    return  false;
  }

  size_t allowed_expansion_words = MetaspaceGC::allowed_expansion();
  if (allowed_expansion_words < min_words) {
    log_trace(gc, metaspace, freelist)("Cannot expand %s virtual space list (must try gc first).",
              class_or_not);
    return false;
  }

  size_t max_expansion_words = MIN2(preferred_words, allowed_expansion_words);

  // Commit more memory from the the current virtual space.
  bool vs_expanded = expand_node_by(current_virtual_space(),
                                    min_words,
                                    max_expansion_words);
  if (vs_expanded) {
     log_trace(gc, metaspace, freelist)("Expanded %s virtual space list.",
               class_or_not);
     return true;
  }
  log_trace(gc, metaspace, freelist)("%s virtual space list: retire current node.",
            class_or_not);
  retire_current_virtual_space();

  // Get another virtual space.
  size_t grow_vs_words = MAX2((size_t)VirtualSpaceSize, preferred_words);
  grow_vs_words = align_up(grow_vs_words, Metaspace::reserve_alignment_words());

  if (create_new_virtual_space(grow_vs_words)) {
    if (current_virtual_space()->is_pre_committed()) {
      // The memory was pre-committed, so we are done here.
      assert(min_words <= current_virtual_space()->committed_words(),
          "The new VirtualSpace was pre-committed, so it"
          "should be large enough to fit the alloc request.");
      return true;
    }

    return expand_node_by(current_virtual_space(),
                          min_words,
                          max_expansion_words);
  }

  return false;
}

// Given a chunk, calculate the largest possible padding space which
// could be required when allocating it.
static size_t largest_possible_padding_size_for_chunk(size_t chunk_word_size, bool is_class) {
  const ChunkIndex chunk_type = get_chunk_type_by_size(chunk_word_size, is_class);
  if (chunk_type != HumongousIndex) {
    // Normal, non-humongous chunks are allocated at chunk size
    // boundaries, so the largest padding space required would be that
    // minus the smallest chunk size.
    const size_t smallest_chunk_size = is_class ? ClassSpecializedChunk : SpecializedChunk;
    return chunk_word_size - smallest_chunk_size;
  } else {
    // Humongous chunks are allocated at smallest-chunksize
    // boundaries, so there is no padding required.
    return 0;
  }
}


Metachunk* VirtualSpaceList::get_new_chunk(size_t chunk_word_size, size_t suggested_commit_granularity) {

  // Allocate a chunk out of the current virtual space.
  Metachunk* next = current_virtual_space()->get_chunk_vs(chunk_word_size);

  if (next != NULL) {
    return next;
  }

  // The expand amount is currently only determined by the requested sizes
  // and not how much committed memory is left in the current virtual space.

  // We must have enough space for the requested size and any
  // additional reqired padding chunks.
  const size_t size_for_padding = largest_possible_padding_size_for_chunk(chunk_word_size, this->is_class());

  size_t min_word_size       = align_up(chunk_word_size + size_for_padding, Metaspace::commit_alignment_words());
  size_t preferred_word_size = align_up(suggested_commit_granularity, Metaspace::commit_alignment_words());
  if (min_word_size >= preferred_word_size) {
    // Can happen when humongous chunks are allocated.
    preferred_word_size = min_word_size;
  }

  bool expanded = expand_by(min_word_size, preferred_word_size);
  if (expanded) {
    next = current_virtual_space()->get_chunk_vs(chunk_word_size);
    assert(next != NULL, "The allocation was expected to succeed after the expansion");
  }

   return next;
}

void VirtualSpaceList::print_on(outputStream* st) const {
  VirtualSpaceListIterator iter(virtual_space_list());
  while (iter.repeat()) {
    VirtualSpaceNode* node = iter.get_next();
    node->print_on(st);
  }
}

void VirtualSpaceList::print_map(outputStream* st) const {
  VirtualSpaceNode* list = virtual_space_list();
  VirtualSpaceListIterator iter(list);
  unsigned i = 0;
  while (iter.repeat()) {
    st->print_cr("Node %u:", i);
    VirtualSpaceNode* node = iter.get_next();
    node->print_map(st, this->is_class());
    i ++;
  }
}

// MetaspaceGC methods

// VM_CollectForMetadataAllocation is the vm operation used to GC.
// Within the VM operation after the GC the attempt to allocate the metadata
// should succeed.  If the GC did not free enough space for the metaspace
// allocation, the HWM is increased so that another virtualspace will be
// allocated for the metadata.  With perm gen the increase in the perm
// gen had bounds, MinMetaspaceExpansion and MaxMetaspaceExpansion.  The
// metaspace policy uses those as the small and large steps for the HWM.
//
// After the GC the compute_new_size() for MetaspaceGC is called to
// resize the capacity of the metaspaces.  The current implementation
// is based on the flags MinMetaspaceFreeRatio and MaxMetaspaceFreeRatio used
// to resize the Java heap by some GC's.  New flags can be implemented
// if really needed.  MinMetaspaceFreeRatio is used to calculate how much
// free space is desirable in the metaspace capacity to decide how much
// to increase the HWM.  MaxMetaspaceFreeRatio is used to decide how much
// free space is desirable in the metaspace capacity before decreasing
// the HWM.

// Calculate the amount to increase the high water mark (HWM).
// Increase by a minimum amount (MinMetaspaceExpansion) so that
// another expansion is not requested too soon.  If that is not
// enough to satisfy the allocation, increase by MaxMetaspaceExpansion.
// If that is still not enough, expand by the size of the allocation
// plus some.
size_t MetaspaceGC::delta_capacity_until_GC(size_t bytes) {
  size_t min_delta = MinMetaspaceExpansion;
  size_t max_delta = MaxMetaspaceExpansion;
  size_t delta = align_up(bytes, Metaspace::commit_alignment());

  if (delta <= min_delta) {
    delta = min_delta;
  } else if (delta <= max_delta) {
    // Don't want to hit the high water mark on the next
    // allocation so make the delta greater than just enough
    // for this allocation.
    delta = max_delta;
  } else {
    // This allocation is large but the next ones are probably not
    // so increase by the minimum.
    delta = delta + min_delta;
  }

  assert_is_aligned(delta, Metaspace::commit_alignment());

  return delta;
}

size_t MetaspaceGC::capacity_until_GC() {
  size_t value = OrderAccess::load_acquire(&_capacity_until_GC);
  assert(value >= MetaspaceSize, "Not initialized properly?");
  return value;
}

bool MetaspaceGC::inc_capacity_until_GC(size_t v, size_t* new_cap_until_GC, size_t* old_cap_until_GC) {
  assert_is_aligned(v, Metaspace::commit_alignment());

  intptr_t capacity_until_GC = _capacity_until_GC;
  intptr_t new_value = capacity_until_GC + v;

  if (new_value < capacity_until_GC) {
    // The addition wrapped around, set new_value to aligned max value.
    new_value = align_down(max_uintx, Metaspace::commit_alignment());
  }

  intptr_t expected = _capacity_until_GC;
  intptr_t actual = Atomic::cmpxchg(new_value, &_capacity_until_GC, expected);

  if (expected != actual) {
    return false;
  }

  if (new_cap_until_GC != NULL) {
    *new_cap_until_GC = new_value;
  }
  if (old_cap_until_GC != NULL) {
    *old_cap_until_GC = capacity_until_GC;
  }
  return true;
}

size_t MetaspaceGC::dec_capacity_until_GC(size_t v) {
  assert_is_aligned(v, Metaspace::commit_alignment());

  return (size_t)Atomic::sub((intptr_t)v, &_capacity_until_GC);
}

void MetaspaceGC::initialize() {
  // Set the high-water mark to MaxMetapaceSize during VM initializaton since
  // we can't do a GC during initialization.
  _capacity_until_GC = MaxMetaspaceSize;
}

void MetaspaceGC::post_initialize() {
  // Reset the high-water mark once the VM initialization is done.
  _capacity_until_GC = MAX2(MetaspaceUtils::committed_bytes(), MetaspaceSize);
}

bool MetaspaceGC::can_expand(size_t word_size, bool is_class) {
  // Check if the compressed class space is full.
  if (is_class && Metaspace::using_class_space()) {
    size_t class_committed = MetaspaceUtils::committed_bytes(Metaspace::ClassType);
    if (class_committed + word_size * BytesPerWord > CompressedClassSpaceSize) {
      log_trace(gc, metaspace, freelist)("Cannot expand %s metaspace by " SIZE_FORMAT " words (CompressedClassSpaceSize = " SIZE_FORMAT " words)",
                (is_class ? "class" : "non-class"), word_size, CompressedClassSpaceSize / sizeof(MetaWord));
      return false;
    }
  }

  // Check if the user has imposed a limit on the metaspace memory.
  size_t committed_bytes = MetaspaceUtils::committed_bytes();
  if (committed_bytes + word_size * BytesPerWord > MaxMetaspaceSize) {
    log_trace(gc, metaspace, freelist)("Cannot expand %s metaspace by " SIZE_FORMAT " words (MaxMetaspaceSize = " SIZE_FORMAT " words)",
              (is_class ? "class" : "non-class"), word_size, MaxMetaspaceSize / sizeof(MetaWord));
    return false;
  }

  return true;
}

size_t MetaspaceGC::allowed_expansion() {
  size_t committed_bytes = MetaspaceUtils::committed_bytes();
  size_t capacity_until_gc = capacity_until_GC();

  assert(capacity_until_gc >= committed_bytes,
         "capacity_until_gc: " SIZE_FORMAT " < committed_bytes: " SIZE_FORMAT,
         capacity_until_gc, committed_bytes);

  size_t left_until_max  = MaxMetaspaceSize - committed_bytes;
  size_t left_until_GC = capacity_until_gc - committed_bytes;
  size_t left_to_commit = MIN2(left_until_GC, left_until_max);
  log_trace(gc, metaspace, freelist)("allowed expansion words: " SIZE_FORMAT
            " (left_until_max: " SIZE_FORMAT ", left_until_GC: " SIZE_FORMAT ".",
            left_to_commit / BytesPerWord, left_until_max / BytesPerWord, left_until_GC / BytesPerWord);

  return left_to_commit / BytesPerWord;
}

void MetaspaceGC::compute_new_size() {
  assert(_shrink_factor <= 100, "invalid shrink factor");
  uint current_shrink_factor = _shrink_factor;
  _shrink_factor = 0;

  // Using committed_bytes() for used_after_gc is an overestimation, since the
  // chunk free lists are included in committed_bytes() and the memory in an
  // un-fragmented chunk free list is available for future allocations.
  // However, if the chunk free lists becomes fragmented, then the memory may
  // not be available for future allocations and the memory is therefore "in use".
  // Including the chunk free lists in the definition of "in use" is therefore
  // necessary. Not including the chunk free lists can cause capacity_until_GC to
  // shrink below committed_bytes() and this has caused serious bugs in the past.
  const size_t used_after_gc = MetaspaceUtils::committed_bytes();
  const size_t capacity_until_GC = MetaspaceGC::capacity_until_GC();

  const double minimum_free_percentage = MinMetaspaceFreeRatio / 100.0;
  const double maximum_used_percentage = 1.0 - minimum_free_percentage;

  const double min_tmp = used_after_gc / maximum_used_percentage;
  size_t minimum_desired_capacity =
    (size_t)MIN2(min_tmp, double(max_uintx));
  // Don't shrink less than the initial generation size
  minimum_desired_capacity = MAX2(minimum_desired_capacity,
                                  MetaspaceSize);

  log_trace(gc, metaspace)("MetaspaceGC::compute_new_size: ");
  log_trace(gc, metaspace)("    minimum_free_percentage: %6.2f  maximum_used_percentage: %6.2f",
                           minimum_free_percentage, maximum_used_percentage);
  log_trace(gc, metaspace)("     used_after_gc       : %6.1fKB", used_after_gc / (double) K);


  size_t shrink_bytes = 0;
  if (capacity_until_GC < minimum_desired_capacity) {
    // If we have less capacity below the metaspace HWM, then
    // increment the HWM.
    size_t expand_bytes = minimum_desired_capacity - capacity_until_GC;
    expand_bytes = align_up(expand_bytes, Metaspace::commit_alignment());
    // Don't expand unless it's significant
    if (expand_bytes >= MinMetaspaceExpansion) {
      size_t new_capacity_until_GC = 0;
      bool succeeded = MetaspaceGC::inc_capacity_until_GC(expand_bytes, &new_capacity_until_GC);
      assert(succeeded, "Should always succesfully increment HWM when at safepoint");

      Metaspace::tracer()->report_gc_threshold(capacity_until_GC,
                                               new_capacity_until_GC,
                                               MetaspaceGCThresholdUpdater::ComputeNewSize);
      log_trace(gc, metaspace)("    expanding:  minimum_desired_capacity: %6.1fKB  expand_bytes: %6.1fKB  MinMetaspaceExpansion: %6.1fKB  new metaspace HWM:  %6.1fKB",
                               minimum_desired_capacity / (double) K,
                               expand_bytes / (double) K,
                               MinMetaspaceExpansion / (double) K,
                               new_capacity_until_GC / (double) K);
    }
    return;
  }

  // No expansion, now see if we want to shrink
  // We would never want to shrink more than this
  assert(capacity_until_GC >= minimum_desired_capacity,
         SIZE_FORMAT " >= " SIZE_FORMAT,
         capacity_until_GC, minimum_desired_capacity);
  size_t max_shrink_bytes = capacity_until_GC - minimum_desired_capacity;

  // Should shrinking be considered?
  if (MaxMetaspaceFreeRatio < 100) {
    const double maximum_free_percentage = MaxMetaspaceFreeRatio / 100.0;
    const double minimum_used_percentage = 1.0 - maximum_free_percentage;
    const double max_tmp = used_after_gc / minimum_used_percentage;
    size_t maximum_desired_capacity = (size_t)MIN2(max_tmp, double(max_uintx));
    maximum_desired_capacity = MAX2(maximum_desired_capacity,
                                    MetaspaceSize);
    log_trace(gc, metaspace)("    maximum_free_percentage: %6.2f  minimum_used_percentage: %6.2f",
                             maximum_free_percentage, minimum_used_percentage);
    log_trace(gc, metaspace)("    minimum_desired_capacity: %6.1fKB  maximum_desired_capacity: %6.1fKB",
                             minimum_desired_capacity / (double) K, maximum_desired_capacity / (double) K);

    assert(minimum_desired_capacity <= maximum_desired_capacity,
           "sanity check");

    if (capacity_until_GC > maximum_desired_capacity) {
      // Capacity too large, compute shrinking size
      shrink_bytes = capacity_until_GC - maximum_desired_capacity;
      // We don't want shrink all the way back to initSize if people call
      // System.gc(), because some programs do that between "phases" and then
      // we'd just have to grow the heap up again for the next phase.  So we
      // damp the shrinking: 0% on the first call, 10% on the second call, 40%
      // on the third call, and 100% by the fourth call.  But if we recompute
      // size without shrinking, it goes back to 0%.
      shrink_bytes = shrink_bytes / 100 * current_shrink_factor;

      shrink_bytes = align_down(shrink_bytes, Metaspace::commit_alignment());

      assert(shrink_bytes <= max_shrink_bytes,
             "invalid shrink size " SIZE_FORMAT " not <= " SIZE_FORMAT,
             shrink_bytes, max_shrink_bytes);
      if (current_shrink_factor == 0) {
        _shrink_factor = 10;
      } else {
        _shrink_factor = MIN2(current_shrink_factor * 4, (uint) 100);
      }
      log_trace(gc, metaspace)("    shrinking:  initThreshold: %.1fK  maximum_desired_capacity: %.1fK",
                               MetaspaceSize / (double) K, maximum_desired_capacity / (double) K);
      log_trace(gc, metaspace)("    shrink_bytes: %.1fK  current_shrink_factor: %d  new shrink factor: %d  MinMetaspaceExpansion: %.1fK",
                               shrink_bytes / (double) K, current_shrink_factor, _shrink_factor, MinMetaspaceExpansion / (double) K);
    }
  }

  // Don't shrink unless it's significant
  if (shrink_bytes >= MinMetaspaceExpansion &&
      ((capacity_until_GC - shrink_bytes) >= MetaspaceSize)) {
    size_t new_capacity_until_GC = MetaspaceGC::dec_capacity_until_GC(shrink_bytes);
    Metaspace::tracer()->report_gc_threshold(capacity_until_GC,
                                             new_capacity_until_GC,
                                             MetaspaceGCThresholdUpdater::ComputeNewSize);
  }
}

// Metadebug methods

void Metadebug::init_allocation_fail_alot_count() {
  if (MetadataAllocationFailALot) {
    _allocation_fail_alot_count =
      1+(long)((double)MetadataAllocationFailALotInterval*os::random()/(max_jint+1.0));
  }
}

#ifdef ASSERT
bool Metadebug::test_metadata_failure() {
  if (MetadataAllocationFailALot &&
      Threads::is_vm_complete()) {
    if (_allocation_fail_alot_count > 0) {
      _allocation_fail_alot_count--;
    } else {
      log_trace(gc, metaspace, freelist)("Metadata allocation failing for MetadataAllocationFailALot");
      init_allocation_fail_alot_count();
      return true;
    }
  }
  return false;
}
#endif

// ChunkManager methods
size_t ChunkManager::free_chunks_total_words() {
  return _free_chunks_total;
}

size_t ChunkManager::free_chunks_total_bytes() {
  return free_chunks_total_words() * BytesPerWord;
}

// Update internal accounting after a chunk was added
void ChunkManager::account_for_added_chunk(const Metachunk* c) {
  assert_lock_strong(MetaspaceExpand_lock);
  _free_chunks_count ++;
  _free_chunks_total += c->word_size();
}

// Update internal accounting after a chunk was removed
void ChunkManager::account_for_removed_chunk(const Metachunk* c) {
  assert_lock_strong(MetaspaceExpand_lock);
  assert(_free_chunks_count >= 1,
    "ChunkManager::_free_chunks_count: about to go negative (" SIZE_FORMAT ").", _free_chunks_count);
  assert(_free_chunks_total >= c->word_size(),
    "ChunkManager::_free_chunks_total: about to go negative"
     "(now: " SIZE_FORMAT ", decrement value: " SIZE_FORMAT ").", _free_chunks_total, c->word_size());
  _free_chunks_count --;
  _free_chunks_total -= c->word_size();
}

size_t ChunkManager::free_chunks_count() {
#ifdef ASSERT
  if (!UseConcMarkSweepGC && !MetaspaceExpand_lock->is_locked()) {
    MutexLockerEx cl(MetaspaceExpand_lock,
                     Mutex::_no_safepoint_check_flag);
    // This lock is only needed in debug because the verification
    // of the _free_chunks_totals walks the list of free chunks
    slow_locked_verify_free_chunks_count();
  }
#endif
  return _free_chunks_count;
}

ChunkIndex ChunkManager::list_index(size_t size) {
  return get_chunk_type_by_size(size, is_class());
}

size_t ChunkManager::size_by_index(ChunkIndex index) const {
  index_bounds_check(index);
  assert(index != HumongousIndex, "Do not call for humongous chunks.");
  return get_size_for_nonhumongous_chunktype(index, is_class());
}

void ChunkManager::locked_verify_free_chunks_total() {
  assert_lock_strong(MetaspaceExpand_lock);
  assert(sum_free_chunks() == _free_chunks_total,
         "_free_chunks_total " SIZE_FORMAT " is not the"
         " same as sum " SIZE_FORMAT, _free_chunks_total,
         sum_free_chunks());
}

void ChunkManager::verify_free_chunks_total() {
  MutexLockerEx cl(MetaspaceExpand_lock,
                     Mutex::_no_safepoint_check_flag);
  locked_verify_free_chunks_total();
}

void ChunkManager::locked_verify_free_chunks_count() {
  assert_lock_strong(MetaspaceExpand_lock);
  assert(sum_free_chunks_count() == _free_chunks_count,
         "_free_chunks_count " SIZE_FORMAT " is not the"
         " same as sum " SIZE_FORMAT, _free_chunks_count,
         sum_free_chunks_count());
}

void ChunkManager::verify_free_chunks_count() {
#ifdef ASSERT
  MutexLockerEx cl(MetaspaceExpand_lock,
                     Mutex::_no_safepoint_check_flag);
  locked_verify_free_chunks_count();
#endif
}

void ChunkManager::verify() {
  MutexLockerEx cl(MetaspaceExpand_lock,
                     Mutex::_no_safepoint_check_flag);
  locked_verify();
}

void ChunkManager::locked_verify() {
  locked_verify_free_chunks_count();
  locked_verify_free_chunks_total();
  for (ChunkIndex i = ZeroIndex; i < NumberOfFreeLists; i = next_chunk_index(i)) {
    ChunkList* list = free_chunks(i);
    if (list != NULL) {
      Metachunk* chunk = list->head();
      while (chunk) {
        DEBUG_ONLY(do_verify_chunk(chunk);)
        assert(chunk->is_tagged_free(), "Chunk should be tagged as free.");
        chunk = chunk->next();
      }
    }
  }
}

void ChunkManager::locked_print_free_chunks(outputStream* st) {
  assert_lock_strong(MetaspaceExpand_lock);
  st->print_cr("Free chunk total " SIZE_FORMAT "  count " SIZE_FORMAT,
                _free_chunks_total, _free_chunks_count);
}

void ChunkManager::locked_print_sum_free_chunks(outputStream* st) {
  assert_lock_strong(MetaspaceExpand_lock);
  st->print_cr("Sum free chunk total " SIZE_FORMAT "  count " SIZE_FORMAT,
                sum_free_chunks(), sum_free_chunks_count());
}

ChunkList* ChunkManager::free_chunks(ChunkIndex index) {
  assert(index == SpecializedIndex || index == SmallIndex || index == MediumIndex,
         "Bad index: %d", (int)index);

  return &_free_chunks[index];
}

// These methods that sum the free chunk lists are used in printing
// methods that are used in product builds.
size_t ChunkManager::sum_free_chunks() {
  assert_lock_strong(MetaspaceExpand_lock);
  size_t result = 0;
  for (ChunkIndex i = ZeroIndex; i < NumberOfFreeLists; i = next_chunk_index(i)) {
    ChunkList* list = free_chunks(i);

    if (list == NULL) {
      continue;
    }

    result = result + list->count() * list->size();
  }
  result = result + humongous_dictionary()->total_size();
  return result;
}

size_t ChunkManager::sum_free_chunks_count() {
  assert_lock_strong(MetaspaceExpand_lock);
  size_t count = 0;
  for (ChunkIndex i = ZeroIndex; i < NumberOfFreeLists; i = next_chunk_index(i)) {
    ChunkList* list = free_chunks(i);
    if (list == NULL) {
      continue;
    }
    count = count + list->count();
  }
  count = count + humongous_dictionary()->total_free_blocks();
  return count;
}

ChunkList* ChunkManager::find_free_chunks_list(size_t word_size) {
  ChunkIndex index = list_index(word_size);
  assert(index < HumongousIndex, "No humongous list");
  return free_chunks(index);
}

// Helper for chunk splitting: given a target chunk size and a larger free chunk,
// split up the larger chunk into n smaller chunks, at least one of which should be
// the target chunk of target chunk size. The smaller chunks, including the target
// chunk, are returned to the freelist. The pointer to the target chunk is returned.
// Note that this chunk is supposed to be removed from the freelist right away.
Metachunk* ChunkManager::split_chunk(size_t target_chunk_word_size, Metachunk* larger_chunk) {
  assert(larger_chunk->word_size() > target_chunk_word_size, "Sanity");

  const ChunkIndex larger_chunk_index = larger_chunk->get_chunk_type();
  const ChunkIndex target_chunk_index = get_chunk_type_by_size(target_chunk_word_size, is_class());

  MetaWord* const region_start = (MetaWord*)larger_chunk;
  const size_t region_word_len = larger_chunk->word_size();
  MetaWord* const region_end = region_start + region_word_len;
  VirtualSpaceNode* const vsn = larger_chunk->container();
  OccupancyMap* const ocmap = vsn->occupancy_map();

  // Any larger non-humongous chunk size is a multiple of any smaller chunk size.
  // Since non-humongous chunks are aligned to their chunk size, the larger chunk should start
  // at an address suitable to place the smaller target chunk.
  assert_is_aligned(region_start, target_chunk_word_size);

  // Remove old chunk.
  free_chunks(larger_chunk_index)->remove_chunk(larger_chunk);
  larger_chunk->remove_sentinel();

  // Prevent access to the old chunk from here on.
  larger_chunk = NULL;
  // ... and wipe it.
  DEBUG_ONLY(memset(region_start, 0xfe, region_word_len * BytesPerWord));

  // In its place create first the target chunk...
  MetaWord* p = region_start;
  Metachunk* target_chunk = ::new (p) Metachunk(target_chunk_index, is_class(), target_chunk_word_size, vsn);
  assert(target_chunk == (Metachunk*)p, "Sanity");
  target_chunk->set_origin(origin_split);

  // Note: we do not need to mark its start in the occupancy map
  // because it coincides with the old chunk start.

  // Mark chunk as free and return to the freelist.
  do_update_in_use_info_for_chunk(target_chunk, false);
  free_chunks(target_chunk_index)->return_chunk_at_head(target_chunk);

  // This chunk should now be valid and can be verified.
  DEBUG_ONLY(do_verify_chunk(target_chunk));

  // In the remaining space create the remainder chunks.
  p += target_chunk->word_size();
  assert(p < region_end, "Sanity");

  while (p < region_end) {

    // Find the largest chunk size which fits the alignment requirements at address p.
    ChunkIndex this_chunk_index = prev_chunk_index(larger_chunk_index);
    size_t this_chunk_word_size = 0;
    for(;;) {
      this_chunk_word_size = get_size_for_nonhumongous_chunktype(this_chunk_index, is_class());
      if (is_aligned(p, this_chunk_word_size * BytesPerWord)) {
        break;
      } else {
        this_chunk_index = prev_chunk_index(this_chunk_index);
        assert(this_chunk_index >= target_chunk_index, "Sanity");
      }
    }

    assert(this_chunk_word_size >= target_chunk_word_size, "Sanity");
    assert(is_aligned(p, this_chunk_word_size * BytesPerWord), "Sanity");
    assert(p + this_chunk_word_size <= region_end, "Sanity");

    // Create splitting chunk.
    Metachunk* this_chunk = ::new (p) Metachunk(this_chunk_index, is_class(), this_chunk_word_size, vsn);
    assert(this_chunk == (Metachunk*)p, "Sanity");
    this_chunk->set_origin(origin_split);
    ocmap->set_chunk_starts_at_address(p, true);
    do_update_in_use_info_for_chunk(this_chunk, false);

    // This chunk should be valid and can be verified.
    DEBUG_ONLY(do_verify_chunk(this_chunk));

    // Return this chunk to freelist and correct counter.
    free_chunks(this_chunk_index)->return_chunk_at_head(this_chunk);
    _free_chunks_count ++;

    log_trace(gc, metaspace, freelist)("Created chunk at " PTR_FORMAT ", word size "
      SIZE_FORMAT_HEX " (%s), in split region [" PTR_FORMAT "..." PTR_FORMAT ").",
      p2i(this_chunk), this_chunk->word_size(), chunk_size_name(this_chunk_index),
      p2i(region_start), p2i(region_end));

    p += this_chunk_word_size;

  }

  return target_chunk;
}

Metachunk* ChunkManager::free_chunks_get(size_t word_size) {
  assert_lock_strong(MetaspaceExpand_lock);

  slow_locked_verify();

  Metachunk* chunk = NULL;
  bool we_did_split_a_chunk = false;

  if (list_index(word_size) != HumongousIndex) {

    ChunkList* free_list = find_free_chunks_list(word_size);
    assert(free_list != NULL, "Sanity check");

    chunk = free_list->head();

    if (chunk == NULL) {
      // Split large chunks into smaller chunks if there are no smaller chunks, just large chunks.
      // This is the counterpart of the coalescing-upon-chunk-return.

      ChunkIndex target_chunk_index = get_chunk_type_by_size(word_size, is_class());

      // Is there a larger chunk we could split?
      Metachunk* larger_chunk = NULL;
      ChunkIndex larger_chunk_index = next_chunk_index(target_chunk_index);
      while (larger_chunk == NULL && larger_chunk_index < NumberOfFreeLists) {
        larger_chunk = free_chunks(larger_chunk_index)->head();
        if (larger_chunk == NULL) {
          larger_chunk_index = next_chunk_index(larger_chunk_index);
        }
      }

      if (larger_chunk != NULL) {
        assert(larger_chunk->word_size() > word_size, "Sanity");
        assert(larger_chunk->get_chunk_type() == larger_chunk_index, "Sanity");

        // We found a larger chunk. Lets split it up:
        // - remove old chunk
        // - in its place, create new smaller chunks, with at least one chunk
        //   being of target size, the others sized as large as possible. This
        //   is to make sure the resulting chunks are "as coalesced as possible"
        //   (similar to VirtualSpaceNode::retire()).
        // Note: during this operation both ChunkManager and VirtualSpaceNode
        //  are temporarily invalid, so be careful with asserts.

        log_trace(gc, metaspace, freelist)("%s: splitting chunk " PTR_FORMAT
           ", word size " SIZE_FORMAT_HEX " (%s), to get a chunk of word size " SIZE_FORMAT_HEX " (%s)...",
          (is_class() ? "class space" : "metaspace"), p2i(larger_chunk), larger_chunk->word_size(),
          chunk_size_name(larger_chunk_index), word_size, chunk_size_name(target_chunk_index));

        chunk = split_chunk(word_size, larger_chunk);

        // This should have worked.
        assert(chunk != NULL, "Sanity");
        assert(chunk->word_size() == word_size, "Sanity");
        assert(chunk->is_tagged_free(), "Sanity");

        we_did_split_a_chunk = true;

      }
    }

    if (chunk == NULL) {
      return NULL;
    }

    // Remove the chunk as the head of the list.
    free_list->remove_chunk(chunk);

    log_trace(gc, metaspace, freelist)("ChunkManager::free_chunks_get: free_list: " PTR_FORMAT " chunks left: " SSIZE_FORMAT ".",
                                       p2i(free_list), free_list->count());

  } else {
    chunk = humongous_dictionary()->get_chunk(word_size);

    if (chunk == NULL) {
      return NULL;
    }

    log_debug(gc, metaspace, alloc)("Free list allocate humongous chunk size " SIZE_FORMAT " for requested size " SIZE_FORMAT " waste " SIZE_FORMAT,
                                    chunk->word_size(), word_size, chunk->word_size() - word_size);
  }

  // Chunk has been removed from the chunk manager; update counters.
  account_for_removed_chunk(chunk);
  do_update_in_use_info_for_chunk(chunk, true);
  chunk->container()->inc_container_count();
  chunk->inc_use_count();

  // Remove it from the links to this freelist
  chunk->set_next(NULL);
  chunk->set_prev(NULL);

  // Run some verifications (some more if we did a chunk split)
#ifdef ASSERT
  if (VerifyMetaspace) {
    locked_verify();
    VirtualSpaceNode* const vsn = chunk->container();
    vsn->verify();
    if (we_did_split_a_chunk) {
      vsn->verify_free_chunks_are_ideally_merged();
    }
  }
#endif

  return chunk;
}

Metachunk* ChunkManager::chunk_freelist_allocate(size_t word_size) {
  assert_lock_strong(MetaspaceExpand_lock);
  slow_locked_verify();

  // Take from the beginning of the list
  Metachunk* chunk = free_chunks_get(word_size);
  if (chunk == NULL) {
    return NULL;
  }

  assert((word_size <= chunk->word_size()) ||
         (list_index(chunk->word_size()) == HumongousIndex),
         "Non-humongous variable sized chunk");
  LogTarget(Debug, gc, metaspace, freelist) lt;
  if (lt.is_enabled()) {
    size_t list_count;
    if (list_index(word_size) < HumongousIndex) {
      ChunkList* list = find_free_chunks_list(word_size);
      list_count = list->count();
    } else {
      list_count = humongous_dictionary()->total_count();
    }
    LogStream ls(lt);
    ls.print("ChunkManager::chunk_freelist_allocate: " PTR_FORMAT " chunk " PTR_FORMAT "  size " SIZE_FORMAT " count " SIZE_FORMAT " ",
             p2i(this), p2i(chunk), chunk->word_size(), list_count);
    ResourceMark rm;
    locked_print_free_chunks(&ls);
  }

  return chunk;
}

void ChunkManager::return_single_chunk(ChunkIndex index, Metachunk* chunk) {
  assert_lock_strong(MetaspaceExpand_lock);
  DEBUG_ONLY(do_verify_chunk(chunk);)
  assert(chunk->get_chunk_type() == index, "Chunk does not match expected index.");
  assert(chunk != NULL, "Expected chunk.");
  assert(chunk->container() != NULL, "Container should have been set.");
  assert(chunk->is_tagged_free() == false, "Chunk should be in use.");
  index_bounds_check(index);

  // Note: mangle *before* returning the chunk to the freelist or dictionary. It does not
  // matter for the freelist (non-humongous chunks), but the humongous chunk dictionary
  // keeps tree node pointers in the chunk payload area which mangle will overwrite.
  DEBUG_ONLY(chunk->mangle(badMetaWordVal);)

  if (index != HumongousIndex) {
    // Return non-humongous chunk to freelist.
    ChunkList* list = free_chunks(index);
    assert(list->size() == chunk->word_size(), "Wrong chunk type.");
    list->return_chunk_at_head(chunk);
    log_trace(gc, metaspace, freelist)("returned one %s chunk at " PTR_FORMAT " to freelist.",
        chunk_size_name(index), p2i(chunk));
  } else {
    // Return humongous chunk to dictionary.
    assert(chunk->word_size() > free_chunks(MediumIndex)->size(), "Wrong chunk type.");
    assert(chunk->word_size() % free_chunks(SpecializedIndex)->size() == 0,
           "Humongous chunk has wrong alignment.");
    _humongous_dictionary.return_chunk(chunk);
    log_trace(gc, metaspace, freelist)("returned one %s chunk at " PTR_FORMAT " (word size " SIZE_FORMAT ") to freelist.",
        chunk_size_name(index), p2i(chunk), chunk->word_size());
  }
  chunk->container()->dec_container_count();
  do_update_in_use_info_for_chunk(chunk, false);

  // Chunk has been added; update counters.
  account_for_added_chunk(chunk);

  // Attempt coalesce returned chunks with its neighboring chunks:
  // if this chunk is small or special, attempt to coalesce to a medium chunk.
  if (index == SmallIndex || index == SpecializedIndex) {
    if (!attempt_to_coalesce_around_chunk(chunk, MediumIndex)) {
      // This did not work. But if this chunk is special, we still may form a small chunk?
      if (index == SpecializedIndex) {
        if (!attempt_to_coalesce_around_chunk(chunk, SmallIndex)) {
          // give up.
        }
      }
    }
  }

}

void ChunkManager::return_chunk_list(ChunkIndex index, Metachunk* chunks) {
  index_bounds_check(index);
  if (chunks == NULL) {
    return;
  }
  LogTarget(Trace, gc, metaspace, freelist) log;
  if (log.is_enabled()) { // tracing
    log.print("returning list of %s chunks...", chunk_size_name(index));
  }
  unsigned num_chunks_returned = 0;
  size_t size_chunks_returned = 0;
  Metachunk* cur = chunks;
  while (cur != NULL) {
    // Capture the next link before it is changed
    // by the call to return_chunk_at_head();
    Metachunk* next = cur->next();
    if (log.is_enabled()) { // tracing
      num_chunks_returned ++;
      size_chunks_returned += cur->word_size();
    }
    return_single_chunk(index, cur);
    cur = next;
  }
  if (log.is_enabled()) { // tracing
    log.print("returned %u %s chunks to freelist, total word size " SIZE_FORMAT ".",
        num_chunks_returned, chunk_size_name(index), size_chunks_returned);
    if (index != HumongousIndex) {
      log.print("updated freelist count: " SIZE_FORMAT ".", free_chunks(index)->size());
    } else {
      log.print("updated dictionary count " SIZE_FORMAT ".", _humongous_dictionary.total_count());
    }
  }
}

void ChunkManager::print_on(outputStream* out) const {
  _humongous_dictionary.report_statistics(out);
}

void ChunkManager::locked_get_statistics(ChunkManagerStatistics* stat) const {
  assert_lock_strong(MetaspaceExpand_lock);
  for (ChunkIndex i = ZeroIndex; i < NumberOfFreeLists; i = next_chunk_index(i)) {
    stat->num_by_type[i] = num_free_chunks(i);
    stat->single_size_by_type[i] = size_by_index(i);
    stat->total_size_by_type[i] = size_free_chunks_in_bytes(i);
  }
  stat->num_humongous_chunks = num_free_chunks(HumongousIndex);
  stat->total_size_humongous_chunks = size_free_chunks_in_bytes(HumongousIndex);
}

void ChunkManager::get_statistics(ChunkManagerStatistics* stat) const {
  MutexLockerEx cl(MetaspaceExpand_lock,
                   Mutex::_no_safepoint_check_flag);
  locked_get_statistics(stat);
}

void ChunkManager::print_statistics(const ChunkManagerStatistics* stat, outputStream* out, size_t scale) {
  size_t total = 0;
  assert(scale == 1 || scale == K || scale == M || scale == G, "Invalid scale");

  const char* unit = scale_unit(scale);
  for (ChunkIndex i = ZeroIndex; i < NumberOfFreeLists; i = next_chunk_index(i)) {
    out->print("  " SIZE_FORMAT " %s (" SIZE_FORMAT " bytes) chunks, total ",
                   stat->num_by_type[i], chunk_size_name(i),
                   stat->single_size_by_type[i]);
    if (scale == 1) {
      out->print_cr(SIZE_FORMAT " bytes", stat->total_size_by_type[i]);
    } else {
      out->print_cr("%.2f%s", (float)stat->total_size_by_type[i] / scale, unit);
    }

    total += stat->total_size_by_type[i];
  }


  total += stat->total_size_humongous_chunks;

  if (scale == 1) {
    out->print_cr("  " SIZE_FORMAT " humongous chunks, total " SIZE_FORMAT " bytes",
    stat->num_humongous_chunks, stat->total_size_humongous_chunks);

    out->print_cr("  total size: " SIZE_FORMAT " bytes.", total);
  } else {
    out->print_cr("  " SIZE_FORMAT " humongous chunks, total %.2f%s",
    stat->num_humongous_chunks,
    (float)stat->total_size_humongous_chunks / scale, unit);

    out->print_cr("  total size: %.2f%s.", (float)total / scale, unit);
  }

}

void ChunkManager::print_all_chunkmanagers(outputStream* out, size_t scale) {
  assert(scale == 1 || scale == K || scale == M || scale == G, "Invalid scale");

  // Note: keep lock protection only to retrieving statistics; keep printing
  // out of lock protection
  ChunkManagerStatistics stat;
  out->print_cr("Chunkmanager (non-class):");
  const ChunkManager* const non_class_cm = Metaspace::chunk_manager_metadata();
  if (non_class_cm != NULL) {
    non_class_cm->get_statistics(&stat);
    ChunkManager::print_statistics(&stat, out, scale);
  } else {
    out->print_cr("unavailable.");
  }
  out->print_cr("Chunkmanager (class):");
  const ChunkManager* const class_cm = Metaspace::chunk_manager_class();
  if (class_cm != NULL) {
    class_cm->get_statistics(&stat);
    ChunkManager::print_statistics(&stat, out, scale);
  } else {
    out->print_cr("unavailable.");
  }
}

// SpaceManager methods

size_t SpaceManager::adjust_initial_chunk_size(size_t requested, bool is_class_space) {
  size_t chunk_sizes[] = {
      specialized_chunk_size(is_class_space),
      small_chunk_size(is_class_space),
      medium_chunk_size(is_class_space)
  };

  // Adjust up to one of the fixed chunk sizes ...
  for (size_t i = 0; i < ARRAY_SIZE(chunk_sizes); i++) {
    if (requested <= chunk_sizes[i]) {
      return chunk_sizes[i];
    }
  }

  // ... or return the size as a humongous chunk.
  return requested;
}

size_t SpaceManager::adjust_initial_chunk_size(size_t requested) const {
  return adjust_initial_chunk_size(requested, is_class());
}

size_t SpaceManager::get_initial_chunk_size(Metaspace::MetaspaceType type) const {
  size_t requested;

  if (is_class()) {
    switch (type) {
    case Metaspace::BootMetaspaceType:       requested = Metaspace::first_class_chunk_word_size(); break;
    case Metaspace::AnonymousMetaspaceType:  requested = ClassSpecializedChunk; break;
    case Metaspace::ReflectionMetaspaceType: requested = ClassSpecializedChunk; break;
    default:                                 requested = ClassSmallChunk; break;
    }
  } else {
    switch (type) {
    case Metaspace::BootMetaspaceType:       requested = Metaspace::first_chunk_word_size(); break;
    case Metaspace::AnonymousMetaspaceType:  requested = SpecializedChunk; break;
    case Metaspace::ReflectionMetaspaceType: requested = SpecializedChunk; break;
    default:                                 requested = SmallChunk; break;
    }
  }

  // Adjust to one of the fixed chunk sizes (unless humongous)
  const size_t adjusted = adjust_initial_chunk_size(requested);

  assert(adjusted != 0, "Incorrect initial chunk size. Requested: "
         SIZE_FORMAT " adjusted: " SIZE_FORMAT, requested, adjusted);

  return adjusted;
}

size_t SpaceManager::sum_free_in_chunks_in_use() const {
  MutexLockerEx cl(lock(), Mutex::_no_safepoint_check_flag);
  size_t free = 0;
  for (ChunkIndex i = ZeroIndex; i < NumberOfInUseLists; i = next_chunk_index(i)) {
    Metachunk* chunk = chunks_in_use(i);
    while (chunk != NULL) {
      free += chunk->free_word_size();
      chunk = chunk->next();
    }
  }
  return free;
}

size_t SpaceManager::sum_waste_in_chunks_in_use() const {
  MutexLockerEx cl(lock(), Mutex::_no_safepoint_check_flag);
  size_t result = 0;
  for (ChunkIndex i = ZeroIndex; i < NumberOfInUseLists; i = next_chunk_index(i)) {
   result += sum_waste_in_chunks_in_use(i);
  }

  return result;
}

size_t SpaceManager::sum_waste_in_chunks_in_use(ChunkIndex index) const {
  size_t result = 0;
  Metachunk* chunk = chunks_in_use(index);
  // Count the free space in all the chunk but not the
  // current chunk from which allocations are still being done.
  while (chunk != NULL) {
    if (chunk != current_chunk()) {
      result += chunk->free_word_size();
    }
    chunk = chunk->next();
  }
  return result;
}

size_t SpaceManager::sum_capacity_in_chunks_in_use() const {
  // For CMS use "allocated_chunks_words()" which does not need the
  // Metaspace lock.  For the other collectors sum over the
  // lists.  Use both methods as a check that "allocated_chunks_words()"
  // is correct.  That is, sum_capacity_in_chunks() is too expensive
  // to use in the product and allocated_chunks_words() should be used
  // but allow for  checking that allocated_chunks_words() returns the same
  // value as sum_capacity_in_chunks_in_use() which is the definitive
  // answer.
  if (UseConcMarkSweepGC) {
    return allocated_chunks_words();
  } else {
    MutexLockerEx cl(lock(), Mutex::_no_safepoint_check_flag);
    size_t sum = 0;
    for (ChunkIndex i = ZeroIndex; i < NumberOfInUseLists; i = next_chunk_index(i)) {
      Metachunk* chunk = chunks_in_use(i);
      while (chunk != NULL) {
        sum += chunk->word_size();
        chunk = chunk->next();
      }
    }
  return sum;
  }
}

size_t SpaceManager::sum_count_in_chunks_in_use() {
  size_t count = 0;
  for (ChunkIndex i = ZeroIndex; i < NumberOfInUseLists; i = next_chunk_index(i)) {
    count = count + sum_count_in_chunks_in_use(i);
  }

  return count;
}

size_t SpaceManager::sum_count_in_chunks_in_use(ChunkIndex i) {
  size_t count = 0;
  Metachunk* chunk = chunks_in_use(i);
  while (chunk != NULL) {
    count++;
    chunk = chunk->next();
  }
  return count;
}


size_t SpaceManager::sum_used_in_chunks_in_use() const {
  MutexLockerEx cl(lock(), Mutex::_no_safepoint_check_flag);
  size_t used = 0;
  for (ChunkIndex i = ZeroIndex; i < NumberOfInUseLists; i = next_chunk_index(i)) {
    Metachunk* chunk = chunks_in_use(i);
    while (chunk != NULL) {
      used += chunk->used_word_size();
      chunk = chunk->next();
    }
  }
  return used;
}

void SpaceManager::locked_print_chunks_in_use_on(outputStream* st) const {

  for (ChunkIndex i = ZeroIndex; i < NumberOfInUseLists; i = next_chunk_index(i)) {
    Metachunk* chunk = chunks_in_use(i);
    st->print("SpaceManager: %s " PTR_FORMAT,
                 chunk_size_name(i), p2i(chunk));
    if (chunk != NULL) {
      st->print_cr(" free " SIZE_FORMAT,
                   chunk->free_word_size());
    } else {
      st->cr();
    }
  }

  chunk_manager()->locked_print_free_chunks(st);
  chunk_manager()->locked_print_sum_free_chunks(st);
}

size_t SpaceManager::calc_chunk_size(size_t word_size) {

  // Decide between a small chunk and a medium chunk.  Up to
  // _small_chunk_limit small chunks can be allocated.
  // After that a medium chunk is preferred.
  size_t chunk_word_size;

  // Special case for anonymous metadata space.
  // Anonymous metadata space is usually small, with majority within 1K - 2K range and
  // rarely about 4K (64-bits JVM).
  // Instead of jumping to SmallChunk after initial chunk exhausted, keeping allocation
  // from SpecializeChunk up to _anon_or_delegating_metadata_specialize_chunk_limit (4)
  // reduces space waste from 60+% to around 30%.
  if ((_space_type == Metaspace::AnonymousMetaspaceType || _space_type == Metaspace::ReflectionMetaspaceType) &&
      _mdtype == Metaspace::NonClassType &&
      sum_count_in_chunks_in_use(SpecializedIndex) < _anon_and_delegating_metadata_specialize_chunk_limit &&
      word_size + Metachunk::overhead() <= SpecializedChunk) {
    return SpecializedChunk;
  }

  if (chunks_in_use(MediumIndex) == NULL &&
      sum_count_in_chunks_in_use(SmallIndex) < _small_chunk_limit) {
    chunk_word_size = (size_t) small_chunk_size();
    if (word_size + Metachunk::overhead() > small_chunk_size()) {
      chunk_word_size = medium_chunk_size();
    }
  } else {
    chunk_word_size = medium_chunk_size();
  }

  // Might still need a humongous chunk.  Enforce
  // humongous allocations sizes to be aligned up to
  // the smallest chunk size.
  size_t if_humongous_sized_chunk =
    align_up(word_size + Metachunk::overhead(),
                  smallest_chunk_size());
  chunk_word_size =
    MAX2((size_t) chunk_word_size, if_humongous_sized_chunk);

  assert(!SpaceManager::is_humongous(word_size) ||
         chunk_word_size == if_humongous_sized_chunk,
         "Size calculation is wrong, word_size " SIZE_FORMAT
         " chunk_word_size " SIZE_FORMAT,
         word_size, chunk_word_size);
  Log(gc, metaspace, alloc) log;
  if (log.is_debug() && SpaceManager::is_humongous(word_size)) {
    log.debug("Metadata humongous allocation:");
    log.debug("  word_size " PTR_FORMAT, word_size);
    log.debug("  chunk_word_size " PTR_FORMAT, chunk_word_size);
    log.debug("    chunk overhead " PTR_FORMAT, Metachunk::overhead());
  }
  return chunk_word_size;
}

void SpaceManager::track_metaspace_memory_usage() {
  if (is_init_completed()) {
    if (is_class()) {
      MemoryService::track_compressed_class_memory_usage();
    }
    MemoryService::track_metaspace_memory_usage();
  }
}

MetaWord* SpaceManager::grow_and_allocate(size_t word_size) {
  assert_lock_strong(_lock);
  assert(vs_list()->current_virtual_space() != NULL,
         "Should have been set");
  assert(current_chunk() == NULL ||
         current_chunk()->allocate(word_size) == NULL,
         "Don't need to expand");
  MutexLockerEx cl(MetaspaceExpand_lock, Mutex::_no_safepoint_check_flag);

  if (log_is_enabled(Trace, gc, metaspace, freelist)) {
    size_t words_left = 0;
    size_t words_used = 0;
    if (current_chunk() != NULL) {
      words_left = current_chunk()->free_word_size();
      words_used = current_chunk()->used_word_size();
    }
    log_trace(gc, metaspace, freelist)("SpaceManager::grow_and_allocate for " SIZE_FORMAT " words " SIZE_FORMAT " words used " SIZE_FORMAT " words left",
                                       word_size, words_used, words_left);
  }

  // Get another chunk
  size_t chunk_word_size = calc_chunk_size(word_size);
  Metachunk* next = get_new_chunk(chunk_word_size);

  MetaWord* mem = NULL;

  // If a chunk was available, add it to the in-use chunk list
  // and do an allocation from it.
  if (next != NULL) {
    // Add to this manager's list of chunks in use.
    add_chunk(next, false);
    mem = next->allocate(word_size);
  }

  // Track metaspace memory usage statistic.
  track_metaspace_memory_usage();

  return mem;
}

void SpaceManager::print_on(outputStream* st) const {

  for (ChunkIndex i = ZeroIndex;
       i < NumberOfInUseLists ;
       i = next_chunk_index(i) ) {
    st->print_cr("  chunks_in_use " PTR_FORMAT " chunk size " SIZE_FORMAT,
                 p2i(chunks_in_use(i)),
                 chunks_in_use(i) == NULL ? 0 : chunks_in_use(i)->word_size());
  }
  st->print_cr("    waste:  Small " SIZE_FORMAT " Medium " SIZE_FORMAT
               " Humongous " SIZE_FORMAT,
               sum_waste_in_chunks_in_use(SmallIndex),
               sum_waste_in_chunks_in_use(MediumIndex),
               sum_waste_in_chunks_in_use(HumongousIndex));
  // block free lists
  if (block_freelists() != NULL) {
    st->print_cr("total in block free lists " SIZE_FORMAT,
      block_freelists()->total_size());
  }
}

SpaceManager::SpaceManager(Metaspace::MetadataType mdtype,
                           Metaspace::MetaspaceType space_type,
                           Mutex* lock) :
  _mdtype(mdtype),
  _space_type(space_type),
  _allocated_blocks_words(0),
  _allocated_chunks_words(0),
  _allocated_chunks_count(0),
  _block_freelists(NULL),
  _lock(lock)
{
  initialize();
}

void SpaceManager::inc_size_metrics(size_t words) {
  assert_lock_strong(MetaspaceExpand_lock);
  // Total of allocated Metachunks and allocated Metachunks count
  // for each SpaceManager
  _allocated_chunks_words = _allocated_chunks_words + words;
  _allocated_chunks_count++;
  // Global total of capacity in allocated Metachunks
  MetaspaceUtils::inc_capacity(mdtype(), words);
  // Global total of allocated Metablocks.
  // used_words_slow() includes the overhead in each
  // Metachunk so include it in the used when the
  // Metachunk is first added (so only added once per
  // Metachunk).
  MetaspaceUtils::inc_used(mdtype(), Metachunk::overhead());
}

void SpaceManager::inc_used_metrics(size_t words) {
  // Add to the per SpaceManager total
  Atomic::add(words, &_allocated_blocks_words);
  // Add to the global total
  MetaspaceUtils::inc_used(mdtype(), words);
}

void SpaceManager::dec_total_from_size_metrics() {
  MetaspaceUtils::dec_capacity(mdtype(), allocated_chunks_words());
  MetaspaceUtils::dec_used(mdtype(), allocated_blocks_words());
  // Also deduct the overhead per Metachunk
  MetaspaceUtils::dec_used(mdtype(), allocated_chunks_count() * Metachunk::overhead());
}

void SpaceManager::initialize() {
  Metadebug::init_allocation_fail_alot_count();
  for (ChunkIndex i = ZeroIndex; i < NumberOfInUseLists; i = next_chunk_index(i)) {
    _chunks_in_use[i] = NULL;
  }
  _current_chunk = NULL;
  log_trace(gc, metaspace, freelist)("SpaceManager(): " PTR_FORMAT, p2i(this));
}

SpaceManager::~SpaceManager() {
  // This call this->_lock which can't be done while holding MetaspaceExpand_lock
  assert(sum_capacity_in_chunks_in_use() == allocated_chunks_words(),
         "sum_capacity_in_chunks_in_use() " SIZE_FORMAT
         " allocated_chunks_words() " SIZE_FORMAT,
         sum_capacity_in_chunks_in_use(), allocated_chunks_words());

  MutexLockerEx fcl(MetaspaceExpand_lock,
                    Mutex::_no_safepoint_check_flag);

  assert(sum_count_in_chunks_in_use() == allocated_chunks_count(),
         "sum_count_in_chunks_in_use() " SIZE_FORMAT
         " allocated_chunks_count() " SIZE_FORMAT,
         sum_count_in_chunks_in_use(), allocated_chunks_count());

  chunk_manager()->slow_locked_verify();

  dec_total_from_size_metrics();

  Log(gc, metaspace, freelist) log;
  if (log.is_trace()) {
    log.trace("~SpaceManager(): " PTR_FORMAT, p2i(this));
    ResourceMark rm;
    LogStream ls(log.trace());
    locked_print_chunks_in_use_on(&ls);
    if (block_freelists() != NULL) {
      block_freelists()->print_on(&ls);
    }
  }

  // Add all the chunks in use by this space manager
  // to the global list of free chunks.

  // Follow each list of chunks-in-use and add them to the
  // free lists.  Each list is NULL terminated.

  for (ChunkIndex i = ZeroIndex; i <= HumongousIndex; i = next_chunk_index(i)) {
    Metachunk* chunks = chunks_in_use(i);
    chunk_manager()->return_chunk_list(i, chunks);
    set_chunks_in_use(i, NULL);
  }

  chunk_manager()->slow_locked_verify();

  if (_block_freelists != NULL) {
    delete _block_freelists;
  }
}

void SpaceManager::deallocate(MetaWord* p, size_t word_size) {
  assert_lock_strong(_lock);
  // Allocations and deallocations are in raw_word_size
  size_t raw_word_size = get_allocation_word_size(word_size);
  // Lazily create a block_freelist
  if (block_freelists() == NULL) {
    _block_freelists = new BlockFreelist();
  }
  block_freelists()->return_block(p, raw_word_size);
}

// Adds a chunk to the list of chunks in use.
void SpaceManager::add_chunk(Metachunk* new_chunk, bool make_current) {

  assert_lock_strong(_lock);
  assert(new_chunk != NULL, "Should not be NULL");
  assert(new_chunk->next() == NULL, "Should not be on a list");

  new_chunk->reset_empty();

  // Find the correct list and and set the current
  // chunk for that list.
  ChunkIndex index = chunk_manager()->list_index(new_chunk->word_size());

  if (index != HumongousIndex) {
    retire_current_chunk();
    set_current_chunk(new_chunk);
    new_chunk->set_next(chunks_in_use(index));
    set_chunks_in_use(index, new_chunk);
  } else {
    // For null class loader data and DumpSharedSpaces, the first chunk isn't
    // small, so small will be null.  Link this first chunk as the current
    // chunk.
    if (make_current) {
      // Set as the current chunk but otherwise treat as a humongous chunk.
      set_current_chunk(new_chunk);
    }
    // Link at head.  The _current_chunk only points to a humongous chunk for
    // the null class loader metaspace (class and data virtual space managers)
    // any humongous chunks so will not point to the tail
    // of the humongous chunks list.
    new_chunk->set_next(chunks_in_use(HumongousIndex));
    set_chunks_in_use(HumongousIndex, new_chunk);

    assert(new_chunk->word_size() > medium_chunk_size(), "List inconsistency");
  }

  // Add to the running sum of capacity
  inc_size_metrics(new_chunk->word_size());

  assert(new_chunk->is_empty(), "Not ready for reuse");
  Log(gc, metaspace, freelist) log;
  if (log.is_trace()) {
    log.trace("SpaceManager::add_chunk: " SIZE_FORMAT ") ", sum_count_in_chunks_in_use());
    ResourceMark rm;
    LogStream ls(log.trace());
    new_chunk->print_on(&ls);
    chunk_manager()->locked_print_free_chunks(&ls);
  }
}

void SpaceManager::retire_current_chunk() {
  if (current_chunk() != NULL) {
    size_t remaining_words = current_chunk()->free_word_size();
    if (remaining_words >= BlockFreelist::min_dictionary_size()) {
      MetaWord* ptr = current_chunk()->allocate(remaining_words);
      deallocate(ptr, remaining_words);
      inc_used_metrics(remaining_words);
    }
  }
}

Metachunk* SpaceManager::get_new_chunk(size_t chunk_word_size) {
  // Get a chunk from the chunk freelist
  Metachunk* next = chunk_manager()->chunk_freelist_allocate(chunk_word_size);

  if (next == NULL) {
    next = vs_list()->get_new_chunk(chunk_word_size,
                                    medium_chunk_bunch());
  }

  Log(gc, metaspace, alloc) log;
  if (log.is_debug() && next != NULL &&
      SpaceManager::is_humongous(next->word_size())) {
    log.debug("  new humongous chunk word size " PTR_FORMAT, next->word_size());
  }

  return next;
}

MetaWord* SpaceManager::allocate(size_t word_size) {
  MutexLockerEx cl(lock(), Mutex::_no_safepoint_check_flag);
  size_t raw_word_size = get_allocation_word_size(word_size);
  BlockFreelist* fl =  block_freelists();
  MetaWord* p = NULL;
  // Allocation from the dictionary is expensive in the sense that
  // the dictionary has to be searched for a size.  Don't allocate
  // from the dictionary until it starts to get fat.  Is this
  // a reasonable policy?  Maybe an skinny dictionary is fast enough
  // for allocations.  Do some profiling.  JJJ
  if (fl != NULL && fl->total_size() > allocation_from_dictionary_limit) {
    p = fl->get_block(raw_word_size);
  }
  if (p == NULL) {
    p = allocate_work(raw_word_size);
  }

  return p;
}

// Returns the address of spaced allocated for "word_size".
// This methods does not know about blocks (Metablocks)
MetaWord* SpaceManager::allocate_work(size_t word_size) {
  assert_lock_strong(_lock);
#ifdef ASSERT
  if (Metadebug::test_metadata_failure()) {
    return NULL;
  }
#endif
  // Is there space in the current chunk?
  MetaWord* result = NULL;

  if (current_chunk() != NULL) {
    result = current_chunk()->allocate(word_size);
  }

  if (result == NULL) {
    result = grow_and_allocate(word_size);
  }

  if (result != NULL) {
    inc_used_metrics(word_size);
    assert(result != (MetaWord*) chunks_in_use(MediumIndex),
           "Head of the list is being allocated");
  }

  return result;
}

void SpaceManager::verify() {
  for (ChunkIndex i = ZeroIndex; i < NumberOfInUseLists; i = next_chunk_index(i)) {
    Metachunk* curr = chunks_in_use(i);
    while (curr != NULL) {
      DEBUG_ONLY(do_verify_chunk(curr);)
      assert(curr->is_tagged_free() == false, "Chunk should be tagged as in use.");
      curr = curr->next();
    }
  }
}

void SpaceManager::verify_chunk_size(Metachunk* chunk) {
  assert(is_humongous(chunk->word_size()) ||
         chunk->word_size() == medium_chunk_size() ||
         chunk->word_size() == small_chunk_size() ||
         chunk->word_size() == specialized_chunk_size(),
         "Chunk size is wrong");
  return;
}

#ifdef ASSERT
void SpaceManager::verify_allocated_blocks_words() {
  // Verification is only guaranteed at a safepoint.
  assert(SafepointSynchronize::is_at_safepoint() || !Universe::is_fully_initialized(),
    "Verification can fail if the applications is running");
  assert(allocated_blocks_words() == sum_used_in_chunks_in_use(),
         "allocation total is not consistent " SIZE_FORMAT
         " vs " SIZE_FORMAT,
         allocated_blocks_words(), sum_used_in_chunks_in_use());
}

#endif

void SpaceManager::dump(outputStream* const out) const {
  size_t curr_total = 0;
  size_t waste = 0;
  uint i = 0;
  size_t used = 0;
  size_t capacity = 0;

  // Add up statistics for all chunks in this SpaceManager.
  for (ChunkIndex index = ZeroIndex;
       index < NumberOfInUseLists;
       index = next_chunk_index(index)) {
    for (Metachunk* curr = chunks_in_use(index);
         curr != NULL;
         curr = curr->next()) {
      out->print("%d) ", i++);
      curr->print_on(out);
      curr_total += curr->word_size();
      used += curr->used_word_size();
      capacity += curr->word_size();
      waste += curr->free_word_size() + curr->overhead();;
    }
  }

  if (log_is_enabled(Trace, gc, metaspace, freelist)) {
    if (block_freelists() != NULL) block_freelists()->print_on(out);
  }

  size_t free = current_chunk() == NULL ? 0 : current_chunk()->free_word_size();
  // Free space isn't wasted.
  waste -= free;

  out->print_cr("total of all chunks "  SIZE_FORMAT " used " SIZE_FORMAT
                " free " SIZE_FORMAT " capacity " SIZE_FORMAT
                " waste " SIZE_FORMAT, curr_total, used, free, capacity, waste);
}

// MetaspaceUtils


size_t MetaspaceUtils::_capacity_words[] = {0, 0};
volatile size_t MetaspaceUtils::_used_words[] = {0, 0};

size_t MetaspaceUtils::free_bytes(Metaspace::MetadataType mdtype) {
  VirtualSpaceList* list = Metaspace::get_space_list(mdtype);
  return list == NULL ? 0 : list->free_bytes();
}

size_t MetaspaceUtils::free_bytes() {
  return free_bytes(Metaspace::ClassType) + free_bytes(Metaspace::NonClassType);
}

void MetaspaceUtils::dec_capacity(Metaspace::MetadataType mdtype, size_t words) {
  assert_lock_strong(MetaspaceExpand_lock);
  assert(words <= capacity_words(mdtype),
         "About to decrement below 0: words " SIZE_FORMAT
         " is greater than _capacity_words[%u] " SIZE_FORMAT,
         words, mdtype, capacity_words(mdtype));
  _capacity_words[mdtype] -= words;
}

void MetaspaceUtils::inc_capacity(Metaspace::MetadataType mdtype, size_t words) {
  assert_lock_strong(MetaspaceExpand_lock);
  // Needs to be atomic
  _capacity_words[mdtype] += words;
}

void MetaspaceUtils::dec_used(Metaspace::MetadataType mdtype, size_t words) {
  assert(words <= used_words(mdtype),
         "About to decrement below 0: words " SIZE_FORMAT
         " is greater than _used_words[%u] " SIZE_FORMAT,
         words, mdtype, used_words(mdtype));
  // For CMS deallocation of the Metaspaces occurs during the
  // sweep which is a concurrent phase.  Protection by the MetaspaceExpand_lock
  // is not enough since allocation is on a per Metaspace basis
  // and protected by the Metaspace lock.
  Atomic::sub(words, &_used_words[mdtype]);
}

void MetaspaceUtils::inc_used(Metaspace::MetadataType mdtype, size_t words) {
  // _used_words tracks allocations for
  // each piece of metadata.  Those allocations are
  // generally done concurrently by different application
  // threads so must be done atomically.
  Atomic::add(words, &_used_words[mdtype]);
}

size_t MetaspaceUtils::used_bytes_slow(Metaspace::MetadataType mdtype) {
  size_t used = 0;
  ClassLoaderDataGraphMetaspaceIterator iter;
  while (iter.repeat()) {
    ClassLoaderMetaspace* msp = iter.get_next();
    // Sum allocated_blocks_words for each metaspace
    if (msp != NULL) {
      used += msp->used_words_slow(mdtype);
    }
  }
  return used * BytesPerWord;
}

size_t MetaspaceUtils::free_bytes_slow(Metaspace::MetadataType mdtype) {
  size_t free = 0;
  ClassLoaderDataGraphMetaspaceIterator iter;
  while (iter.repeat()) {
    ClassLoaderMetaspace* msp = iter.get_next();
    if (msp != NULL) {
      free += msp->free_words_slow(mdtype);
    }
  }
  return free * BytesPerWord;
}

size_t MetaspaceUtils::capacity_bytes_slow(Metaspace::MetadataType mdtype) {
  if ((mdtype == Metaspace::ClassType) && !Metaspace::using_class_space()) {
    return 0;
  }
  // Don't count the space in the freelists.  That space will be
  // added to the capacity calculation as needed.
  size_t capacity = 0;
  ClassLoaderDataGraphMetaspaceIterator iter;
  while (iter.repeat()) {
    ClassLoaderMetaspace* msp = iter.get_next();
    if (msp != NULL) {
      capacity += msp->capacity_words_slow(mdtype);
    }
  }
  return capacity * BytesPerWord;
}

size_t MetaspaceUtils::capacity_bytes_slow() {
#ifdef PRODUCT
  // Use capacity_bytes() in PRODUCT instead of this function.
  guarantee(false, "Should not call capacity_bytes_slow() in the PRODUCT");
#endif
  size_t class_capacity = capacity_bytes_slow(Metaspace::ClassType);
  size_t non_class_capacity = capacity_bytes_slow(Metaspace::NonClassType);
  assert(capacity_bytes() == class_capacity + non_class_capacity,
         "bad accounting: capacity_bytes() " SIZE_FORMAT
         " class_capacity + non_class_capacity " SIZE_FORMAT
         " class_capacity " SIZE_FORMAT " non_class_capacity " SIZE_FORMAT,
         capacity_bytes(), class_capacity + non_class_capacity,
         class_capacity, non_class_capacity);

  return class_capacity + non_class_capacity;
}

size_t MetaspaceUtils::reserved_bytes(Metaspace::MetadataType mdtype) {
  VirtualSpaceList* list = Metaspace::get_space_list(mdtype);
  return list == NULL ? 0 : list->reserved_bytes();
}

size_t MetaspaceUtils::committed_bytes(Metaspace::MetadataType mdtype) {
  VirtualSpaceList* list = Metaspace::get_space_list(mdtype);
  return list == NULL ? 0 : list->committed_bytes();
}

size_t MetaspaceUtils::min_chunk_size_words() { return Metaspace::first_chunk_word_size(); }

size_t MetaspaceUtils::free_chunks_total_words(Metaspace::MetadataType mdtype) {
  ChunkManager* chunk_manager = Metaspace::get_chunk_manager(mdtype);
  if (chunk_manager == NULL) {
    return 0;
  }
  chunk_manager->slow_verify();
  return chunk_manager->free_chunks_total_words();
}

size_t MetaspaceUtils::free_chunks_total_bytes(Metaspace::MetadataType mdtype) {
  return free_chunks_total_words(mdtype) * BytesPerWord;
}

size_t MetaspaceUtils::free_chunks_total_words() {
  return free_chunks_total_words(Metaspace::ClassType) +
         free_chunks_total_words(Metaspace::NonClassType);
}

size_t MetaspaceUtils::free_chunks_total_bytes() {
  return free_chunks_total_words() * BytesPerWord;
}

bool MetaspaceUtils::has_chunk_free_list(Metaspace::MetadataType mdtype) {
  return Metaspace::get_chunk_manager(mdtype) != NULL;
}

MetaspaceChunkFreeListSummary MetaspaceUtils::chunk_free_list_summary(Metaspace::MetadataType mdtype) {
  if (!has_chunk_free_list(mdtype)) {
    return MetaspaceChunkFreeListSummary();
  }

  const ChunkManager* cm = Metaspace::get_chunk_manager(mdtype);
  return cm->chunk_free_list_summary();
}

void MetaspaceUtils::print_metaspace_change(size_t prev_metadata_used) {
  log_info(gc, metaspace)("Metaspace: "  SIZE_FORMAT "K->" SIZE_FORMAT "K("  SIZE_FORMAT "K)",
                          prev_metadata_used/K, used_bytes()/K, reserved_bytes()/K);
}

void MetaspaceUtils::print_on(outputStream* out) {
  Metaspace::MetadataType nct = Metaspace::NonClassType;

  out->print_cr(" Metaspace       "
                "used "      SIZE_FORMAT "K, "
                "capacity "  SIZE_FORMAT "K, "
                "committed " SIZE_FORMAT "K, "
                "reserved "  SIZE_FORMAT "K",
                used_bytes()/K,
                capacity_bytes()/K,
                committed_bytes()/K,
                reserved_bytes()/K);

  if (Metaspace::using_class_space()) {
    Metaspace::MetadataType ct = Metaspace::ClassType;
    out->print_cr("  class space    "
                  "used "      SIZE_FORMAT "K, "
                  "capacity "  SIZE_FORMAT "K, "
                  "committed " SIZE_FORMAT "K, "
                  "reserved "  SIZE_FORMAT "K",
                  used_bytes(ct)/K,
                  capacity_bytes(ct)/K,
                  committed_bytes(ct)/K,
                  reserved_bytes(ct)/K);
  }
}

// Print information for class space and data space separately.
// This is almost the same as above.
void MetaspaceUtils::print_on(outputStream* out, Metaspace::MetadataType mdtype) {
  size_t free_chunks_capacity_bytes = free_chunks_total_bytes(mdtype);
  size_t capacity_bytes = capacity_bytes_slow(mdtype);
  size_t used_bytes = used_bytes_slow(mdtype);
  size_t free_bytes = free_bytes_slow(mdtype);
  size_t used_and_free = used_bytes + free_bytes +
                           free_chunks_capacity_bytes;
  out->print_cr("  Chunk accounting: (used in chunks " SIZE_FORMAT
             "K + unused in chunks " SIZE_FORMAT "K  + "
             " capacity in free chunks " SIZE_FORMAT "K) = " SIZE_FORMAT
             "K  capacity in allocated chunks " SIZE_FORMAT "K",
             used_bytes / K,
             free_bytes / K,
             free_chunks_capacity_bytes / K,
             used_and_free / K,
             capacity_bytes / K);
  // Accounting can only be correct if we got the values during a safepoint
  assert(!SafepointSynchronize::is_at_safepoint() || used_and_free == capacity_bytes, "Accounting is wrong");
}

// Print total fragmentation for class metaspaces
void MetaspaceUtils::print_class_waste(outputStream* out) {
  assert(Metaspace::using_class_space(), "class metaspace not used");
  size_t cls_specialized_waste = 0, cls_small_waste = 0, cls_medium_waste = 0;
  size_t cls_specialized_count = 0, cls_small_count = 0, cls_medium_count = 0, cls_humongous_count = 0;
  ClassLoaderDataGraphMetaspaceIterator iter;
  while (iter.repeat()) {
    ClassLoaderMetaspace* msp = iter.get_next();
    if (msp != NULL) {
      cls_specialized_waste += msp->class_vsm()->sum_waste_in_chunks_in_use(SpecializedIndex);
      cls_specialized_count += msp->class_vsm()->sum_count_in_chunks_in_use(SpecializedIndex);
      cls_small_waste += msp->class_vsm()->sum_waste_in_chunks_in_use(SmallIndex);
      cls_small_count += msp->class_vsm()->sum_count_in_chunks_in_use(SmallIndex);
      cls_medium_waste += msp->class_vsm()->sum_waste_in_chunks_in_use(MediumIndex);
      cls_medium_count += msp->class_vsm()->sum_count_in_chunks_in_use(MediumIndex);
      cls_humongous_count += msp->class_vsm()->sum_count_in_chunks_in_use(HumongousIndex);
    }
  }
  out->print_cr(" class: " SIZE_FORMAT " specialized(s) " SIZE_FORMAT ", "
                SIZE_FORMAT " small(s) " SIZE_FORMAT ", "
                SIZE_FORMAT " medium(s) " SIZE_FORMAT ", "
                "large count " SIZE_FORMAT,
                cls_specialized_count, cls_specialized_waste,
                cls_small_count, cls_small_waste,
                cls_medium_count, cls_medium_waste, cls_humongous_count);
}

// Print total fragmentation for data and class metaspaces separately
void MetaspaceUtils::print_waste(outputStream* out) {
  size_t specialized_waste = 0, small_waste = 0, medium_waste = 0;
  size_t specialized_count = 0, small_count = 0, medium_count = 0, humongous_count = 0;

  ClassLoaderDataGraphMetaspaceIterator iter;
  while (iter.repeat()) {
    ClassLoaderMetaspace* msp = iter.get_next();
    if (msp != NULL) {
      specialized_waste += msp->vsm()->sum_waste_in_chunks_in_use(SpecializedIndex);
      specialized_count += msp->vsm()->sum_count_in_chunks_in_use(SpecializedIndex);
      small_waste += msp->vsm()->sum_waste_in_chunks_in_use(SmallIndex);
      small_count += msp->vsm()->sum_count_in_chunks_in_use(SmallIndex);
      medium_waste += msp->vsm()->sum_waste_in_chunks_in_use(MediumIndex);
      medium_count += msp->vsm()->sum_count_in_chunks_in_use(MediumIndex);
      humongous_count += msp->vsm()->sum_count_in_chunks_in_use(HumongousIndex);
    }
  }
  out->print_cr("Total fragmentation waste (words) doesn't count free space");
  out->print_cr("  data: " SIZE_FORMAT " specialized(s) " SIZE_FORMAT ", "
                        SIZE_FORMAT " small(s) " SIZE_FORMAT ", "
                        SIZE_FORMAT " medium(s) " SIZE_FORMAT ", "
                        "large count " SIZE_FORMAT,
             specialized_count, specialized_waste, small_count,
             small_waste, medium_count, medium_waste, humongous_count);
  if (Metaspace::using_class_space()) {
    print_class_waste(out);
  }
}

class MetadataStats {
private:
  size_t _capacity;
  size_t _used;
  size_t _free;
  size_t _waste;

public:
  MetadataStats() : _capacity(0), _used(0), _free(0), _waste(0) { }
  MetadataStats(size_t capacity, size_t used, size_t free, size_t waste)
  : _capacity(capacity), _used(used), _free(free), _waste(waste) { }

  void add(const MetadataStats& stats) {
    _capacity += stats.capacity();
    _used += stats.used();
    _free += stats.free();
    _waste += stats.waste();
  }

  size_t capacity() const { return _capacity; }
  size_t used() const     { return _used; }
  size_t free() const     { return _free; }
  size_t waste() const    { return _waste; }

  void print_on(outputStream* out, size_t scale) const;
};


void MetadataStats::print_on(outputStream* out, size_t scale) const {
  const char* unit = scale_unit(scale);
  out->print_cr("capacity=%10.2f%s used=%10.2f%s free=%10.2f%s waste=%10.2f%s",
    (float)capacity() / scale, unit,
    (float)used() / scale, unit,
    (float)free() / scale, unit,
    (float)waste() / scale, unit);
}

class PrintCLDMetaspaceInfoClosure : public CLDClosure {
private:
  outputStream*  _out;
  size_t         _scale;

  size_t         _total_count;
  MetadataStats  _total_metadata;
  MetadataStats  _total_class;

  size_t         _total_anon_count;
  MetadataStats  _total_anon_metadata;
  MetadataStats  _total_anon_class;

public:
  PrintCLDMetaspaceInfoClosure(outputStream* out, size_t scale = K)
  : _out(out), _scale(scale), _total_count(0), _total_anon_count(0) { }

  ~PrintCLDMetaspaceInfoClosure() {
    print_summary();
  }

  void do_cld(ClassLoaderData* cld) {
    assert(SafepointSynchronize::is_at_safepoint(), "Must be at a safepoint");

    if (cld->is_unloading()) return;
    ClassLoaderMetaspace* msp = cld->metaspace_or_null();
    if (msp == NULL) {
      return;
    }

    bool anonymous = false;
    if (cld->is_anonymous()) {
      _out->print_cr("ClassLoader: for anonymous class");
      anonymous = true;
    } else {
      ResourceMark rm;
      _out->print_cr("ClassLoader: %s", cld->loader_name());
    }

    print_metaspace(msp, anonymous);
    _out->cr();
  }

private:
  void print_metaspace(ClassLoaderMetaspace* msp, bool anonymous);
  void print_summary() const;
};

void PrintCLDMetaspaceInfoClosure::print_metaspace(ClassLoaderMetaspace* msp, bool anonymous){
  assert(msp != NULL, "Sanity");
  SpaceManager* vsm = msp->vsm();
  const char* unit = scale_unit(_scale);

  size_t capacity = vsm->sum_capacity_in_chunks_in_use() * BytesPerWord;
  size_t used = vsm->sum_used_in_chunks_in_use() * BytesPerWord;
  size_t free = vsm->sum_free_in_chunks_in_use() * BytesPerWord;
  size_t waste = vsm->sum_waste_in_chunks_in_use() * BytesPerWord;

  _total_count ++;
  MetadataStats metadata_stats(capacity, used, free, waste);
  _total_metadata.add(metadata_stats);

  if (anonymous) {
    _total_anon_count ++;
    _total_anon_metadata.add(metadata_stats);
  }

  _out->print("  Metadata   ");
  metadata_stats.print_on(_out, _scale);

  if (Metaspace::using_class_space()) {
    vsm = msp->class_vsm();

    capacity = vsm->sum_capacity_in_chunks_in_use() * BytesPerWord;
    used = vsm->sum_used_in_chunks_in_use() * BytesPerWord;
    free = vsm->sum_free_in_chunks_in_use() * BytesPerWord;
    waste = vsm->sum_waste_in_chunks_in_use() * BytesPerWord;

    MetadataStats class_stats(capacity, used, free, waste);
    _total_class.add(class_stats);

    if (anonymous) {
      _total_anon_class.add(class_stats);
    }

    _out->print("  Class data ");
    class_stats.print_on(_out, _scale);
  }
}

void PrintCLDMetaspaceInfoClosure::print_summary() const {
  const char* unit = scale_unit(_scale);
  _out->cr();
  _out->print_cr("Summary:");

  MetadataStats total;
  total.add(_total_metadata);
  total.add(_total_class);

  _out->print("  Total class loaders=" SIZE_FORMAT_W(6) " ", _total_count);
  total.print_on(_out, _scale);

  _out->print("                    Metadata ");
  _total_metadata.print_on(_out, _scale);

  if (Metaspace::using_class_space()) {
    _out->print("                  Class data ");
    _total_class.print_on(_out, _scale);
  }
  _out->cr();

  MetadataStats total_anon;
  total_anon.add(_total_anon_metadata);
  total_anon.add(_total_anon_class);

  _out->print("For anonymous classes=" SIZE_FORMAT_W(6) " ", _total_anon_count);
  total_anon.print_on(_out, _scale);

  _out->print("                    Metadata ");
  _total_anon_metadata.print_on(_out, _scale);

  if (Metaspace::using_class_space()) {
    _out->print("                  Class data ");
    _total_anon_class.print_on(_out, _scale);
  }
}

void MetaspaceUtils::print_metadata_for_nmt(outputStream* out, size_t scale) {
  const char* unit = scale_unit(scale);
  out->print_cr("Metaspaces:");
  out->print_cr("  Metadata space: reserved=" SIZE_FORMAT_W(10) "%s committed=" SIZE_FORMAT_W(10) "%s",
    reserved_bytes(Metaspace::NonClassType) / scale, unit,
    committed_bytes(Metaspace::NonClassType) / scale, unit);
  if (Metaspace::using_class_space()) {
    out->print_cr("  Class    space: reserved=" SIZE_FORMAT_W(10) "%s committed=" SIZE_FORMAT_W(10) "%s",
    reserved_bytes(Metaspace::ClassType) / scale, unit,
    committed_bytes(Metaspace::ClassType) / scale, unit);
  }

  out->cr();
  ChunkManager::print_all_chunkmanagers(out, scale);

  out->cr();
  out->print_cr("Per-classloader metadata:");
  out->cr();

  PrintCLDMetaspaceInfoClosure cl(out, scale);
  ClassLoaderDataGraph::cld_do(&cl);
}


// Dump global metaspace things from the end of ClassLoaderDataGraph
void MetaspaceUtils::dump(outputStream* out) {
  out->print_cr("All Metaspace:");
  out->print("data space: "); print_on(out, Metaspace::NonClassType);
  out->print("class space: "); print_on(out, Metaspace::ClassType);
  print_waste(out);
}

// Prints an ASCII representation of the given space.
void MetaspaceUtils::print_metaspace_map(outputStream* out, Metaspace::MetadataType mdtype) {
  MutexLockerEx cl(MetaspaceExpand_lock, Mutex::_no_safepoint_check_flag);
  const bool for_class = mdtype == Metaspace::ClassType ? true : false;
  VirtualSpaceList* const vsl = for_class ? Metaspace::class_space_list() : Metaspace::space_list();
  if (vsl != NULL) {
    if (for_class) {
      if (!Metaspace::using_class_space()) {
        out->print_cr("No Class Space.");
        return;
      }
      out->print_raw("---- Metaspace Map (Class Space) ----");
    } else {
      out->print_raw("---- Metaspace Map (Non-Class Space) ----");
    }
    // Print legend:
    out->cr();
    out->print_cr("Chunk Types (uppercase chunks are in use): x-specialized, s-small, m-medium, h-humongous.");
    out->cr();
    VirtualSpaceList* const vsl = for_class ? Metaspace::class_space_list() : Metaspace::space_list();
    vsl->print_map(out);
    out->cr();
  }
}

void MetaspaceUtils::verify_free_chunks() {
  Metaspace::chunk_manager_metadata()->verify();
  if (Metaspace::using_class_space()) {
    Metaspace::chunk_manager_class()->verify();
  }
}

void MetaspaceUtils::verify_capacity() {
#ifdef ASSERT
  size_t running_sum_capacity_bytes = capacity_bytes();
  // For purposes of the running sum of capacity, verify against capacity
  size_t capacity_in_use_bytes = capacity_bytes_slow();
  assert(running_sum_capacity_bytes == capacity_in_use_bytes,
         "capacity_words() * BytesPerWord " SIZE_FORMAT
         " capacity_bytes_slow()" SIZE_FORMAT,
         running_sum_capacity_bytes, capacity_in_use_bytes);
  for (Metaspace::MetadataType i = Metaspace::ClassType;
       i < Metaspace:: MetadataTypeCount;
       i = (Metaspace::MetadataType)(i + 1)) {
    size_t capacity_in_use_bytes = capacity_bytes_slow(i);
    assert(capacity_bytes(i) == capacity_in_use_bytes,
           "capacity_bytes(%u) " SIZE_FORMAT
           " capacity_bytes_slow(%u)" SIZE_FORMAT,
           i, capacity_bytes(i), i, capacity_in_use_bytes);
  }
#endif
}

void MetaspaceUtils::verify_used() {
#ifdef ASSERT
  size_t running_sum_used_bytes = used_bytes();
  // For purposes of the running sum of used, verify against used
  size_t used_in_use_bytes = used_bytes_slow();
  assert(used_bytes() == used_in_use_bytes,
         "used_bytes() " SIZE_FORMAT
         " used_bytes_slow()" SIZE_FORMAT,
         used_bytes(), used_in_use_bytes);
  for (Metaspace::MetadataType i = Metaspace::ClassType;
       i < Metaspace:: MetadataTypeCount;
       i = (Metaspace::MetadataType)(i + 1)) {
    size_t used_in_use_bytes = used_bytes_slow(i);
    assert(used_bytes(i) == used_in_use_bytes,
           "used_bytes(%u) " SIZE_FORMAT
           " used_bytes_slow(%u)" SIZE_FORMAT,
           i, used_bytes(i), i, used_in_use_bytes);
  }
#endif
}

void MetaspaceUtils::verify_metrics() {
  verify_capacity();
  verify_used();
}


// Metaspace methods

size_t Metaspace::_first_chunk_word_size = 0;
size_t Metaspace::_first_class_chunk_word_size = 0;

size_t Metaspace::_commit_alignment = 0;
size_t Metaspace::_reserve_alignment = 0;

VirtualSpaceList* Metaspace::_space_list = NULL;
VirtualSpaceList* Metaspace::_class_space_list = NULL;

ChunkManager* Metaspace::_chunk_manager_metadata = NULL;
ChunkManager* Metaspace::_chunk_manager_class = NULL;

#define VIRTUALSPACEMULTIPLIER 2

#ifdef _LP64
static const uint64_t UnscaledClassSpaceMax = (uint64_t(max_juint) + 1);

void Metaspace::set_narrow_klass_base_and_shift(address metaspace_base, address cds_base) {
  assert(!DumpSharedSpaces, "narrow_klass is set by MetaspaceShared class.");
  // Figure out the narrow_klass_base and the narrow_klass_shift.  The
  // narrow_klass_base is the lower of the metaspace base and the cds base
  // (if cds is enabled).  The narrow_klass_shift depends on the distance
  // between the lower base and higher address.
  address lower_base;
  address higher_address;
#if INCLUDE_CDS
  if (UseSharedSpaces) {
    higher_address = MAX2((address)(cds_base + MetaspaceShared::core_spaces_size()),
                          (address)(metaspace_base + compressed_class_space_size()));
    lower_base = MIN2(metaspace_base, cds_base);
  } else
#endif
  {
    higher_address = metaspace_base + compressed_class_space_size();
    lower_base = metaspace_base;

    uint64_t klass_encoding_max = UnscaledClassSpaceMax << LogKlassAlignmentInBytes;
    // If compressed class space fits in lower 32G, we don't need a base.
    if (higher_address <= (address)klass_encoding_max) {
      lower_base = 0; // Effectively lower base is zero.
    }
  }

  Universe::set_narrow_klass_base(lower_base);

  // CDS uses LogKlassAlignmentInBytes for narrow_klass_shift. See
  // MetaspaceShared::initialize_dumptime_shared_and_meta_spaces() for
  // how dump time narrow_klass_shift is set. Although, CDS can work
  // with zero-shift mode also, to be consistent with AOT it uses
  // LogKlassAlignmentInBytes for klass shift so archived java heap objects
  // can be used at same time as AOT code.
  if (!UseSharedSpaces
      && (uint64_t)(higher_address - lower_base) <= UnscaledClassSpaceMax) {
    Universe::set_narrow_klass_shift(0);
  } else {
    Universe::set_narrow_klass_shift(LogKlassAlignmentInBytes);
  }
  AOTLoader::set_narrow_klass_shift();
}

#if INCLUDE_CDS
// Return TRUE if the specified metaspace_base and cds_base are close enough
// to work with compressed klass pointers.
bool Metaspace::can_use_cds_with_metaspace_addr(char* metaspace_base, address cds_base) {
  assert(cds_base != 0 && UseSharedSpaces, "Only use with CDS");
  assert(UseCompressedClassPointers, "Only use with CompressedKlassPtrs");
  address lower_base = MIN2((address)metaspace_base, cds_base);
  address higher_address = MAX2((address)(cds_base + MetaspaceShared::core_spaces_size()),
                                (address)(metaspace_base + compressed_class_space_size()));
  return ((uint64_t)(higher_address - lower_base) <= UnscaledClassSpaceMax);
}
#endif

// Try to allocate the metaspace at the requested addr.
void Metaspace::allocate_metaspace_compressed_klass_ptrs(char* requested_addr, address cds_base) {
  assert(!DumpSharedSpaces, "compress klass space is allocated by MetaspaceShared class.");
  assert(using_class_space(), "called improperly");
  assert(UseCompressedClassPointers, "Only use with CompressedKlassPtrs");
  assert(compressed_class_space_size() < KlassEncodingMetaspaceMax,
         "Metaspace size is too big");
  assert_is_aligned(requested_addr, _reserve_alignment);
  assert_is_aligned(cds_base, _reserve_alignment);
  assert_is_aligned(compressed_class_space_size(), _reserve_alignment);

  // Don't use large pages for the class space.
  bool large_pages = false;

#if !(defined(AARCH64) || defined(AIX))
  ReservedSpace metaspace_rs = ReservedSpace(compressed_class_space_size(),
                                             _reserve_alignment,
                                             large_pages,
                                             requested_addr);
#else // AARCH64
  ReservedSpace metaspace_rs;

  // Our compressed klass pointers may fit nicely into the lower 32
  // bits.
  if ((uint64_t)requested_addr + compressed_class_space_size() < 4*G) {
    metaspace_rs = ReservedSpace(compressed_class_space_size(),
                                 _reserve_alignment,
                                 large_pages,
                                 requested_addr);
  }

  if (! metaspace_rs.is_reserved()) {
    // Aarch64: Try to align metaspace so that we can decode a compressed
    // klass with a single MOVK instruction.  We can do this iff the
    // compressed class base is a multiple of 4G.
    // Aix: Search for a place where we can find memory. If we need to load
    // the base, 4G alignment is helpful, too.
    size_t increment = AARCH64_ONLY(4*)G;
    for (char *a = align_up(requested_addr, increment);
         a < (char*)(1024*G);
         a += increment) {
      if (a == (char *)(32*G)) {
        // Go faster from here on. Zero-based is no longer possible.
        increment = 4*G;
      }

#if INCLUDE_CDS
      if (UseSharedSpaces
          && ! can_use_cds_with_metaspace_addr(a, cds_base)) {
        // We failed to find an aligned base that will reach.  Fall
        // back to using our requested addr.
        metaspace_rs = ReservedSpace(compressed_class_space_size(),
                                     _reserve_alignment,
                                     large_pages,
                                     requested_addr);
        break;
      }
#endif

      metaspace_rs = ReservedSpace(compressed_class_space_size(),
                                   _reserve_alignment,
                                   large_pages,
                                   a);
      if (metaspace_rs.is_reserved())
        break;
    }
  }

#endif // AARCH64

  if (!metaspace_rs.is_reserved()) {
#if INCLUDE_CDS
    if (UseSharedSpaces) {
      size_t increment = align_up(1*G, _reserve_alignment);

      // Keep trying to allocate the metaspace, increasing the requested_addr
      // by 1GB each time, until we reach an address that will no longer allow
      // use of CDS with compressed klass pointers.
      char *addr = requested_addr;
      while (!metaspace_rs.is_reserved() && (addr + increment > addr) &&
             can_use_cds_with_metaspace_addr(addr + increment, cds_base)) {
        addr = addr + increment;
        metaspace_rs = ReservedSpace(compressed_class_space_size(),
                                     _reserve_alignment, large_pages, addr);
      }
    }
#endif
    // If no successful allocation then try to allocate the space anywhere.  If
    // that fails then OOM doom.  At this point we cannot try allocating the
    // metaspace as if UseCompressedClassPointers is off because too much
    // initialization has happened that depends on UseCompressedClassPointers.
    // So, UseCompressedClassPointers cannot be turned off at this point.
    if (!metaspace_rs.is_reserved()) {
      metaspace_rs = ReservedSpace(compressed_class_space_size(),
                                   _reserve_alignment, large_pages);
      if (!metaspace_rs.is_reserved()) {
        vm_exit_during_initialization(err_msg("Could not allocate metaspace: " SIZE_FORMAT " bytes",
                                              compressed_class_space_size()));
      }
    }
  }

  // If we got here then the metaspace got allocated.
  MemTracker::record_virtual_memory_type((address)metaspace_rs.base(), mtClass);

#if INCLUDE_CDS
  // Verify that we can use shared spaces.  Otherwise, turn off CDS.
  if (UseSharedSpaces && !can_use_cds_with_metaspace_addr(metaspace_rs.base(), cds_base)) {
    FileMapInfo::stop_sharing_and_unmap(
        "Could not allocate metaspace at a compatible address");
  }
#endif
  set_narrow_klass_base_and_shift((address)metaspace_rs.base(),
                                  UseSharedSpaces ? (address)cds_base : 0);

  initialize_class_space(metaspace_rs);

  LogTarget(Trace, gc, metaspace) lt;
  if (lt.is_enabled()) {
    ResourceMark rm;
    LogStream ls(lt);
    print_compressed_class_space(&ls, requested_addr);
  }
}

void Metaspace::print_compressed_class_space(outputStream* st, const char* requested_addr) {
  st->print_cr("Narrow klass base: " PTR_FORMAT ", Narrow klass shift: %d",
               p2i(Universe::narrow_klass_base()), Universe::narrow_klass_shift());
  if (_class_space_list != NULL) {
    address base = (address)_class_space_list->current_virtual_space()->bottom();
    st->print("Compressed class space size: " SIZE_FORMAT " Address: " PTR_FORMAT,
                 compressed_class_space_size(), p2i(base));
    if (requested_addr != 0) {
      st->print(" Req Addr: " PTR_FORMAT, p2i(requested_addr));
    }
    st->cr();
  }
}

// For UseCompressedClassPointers the class space is reserved above the top of
// the Java heap.  The argument passed in is at the base of the compressed space.
void Metaspace::initialize_class_space(ReservedSpace rs) {
  // The reserved space size may be bigger because of alignment, esp with UseLargePages
  assert(rs.size() >= CompressedClassSpaceSize,
         SIZE_FORMAT " != " SIZE_FORMAT, rs.size(), CompressedClassSpaceSize);
  assert(using_class_space(), "Must be using class space");
  _class_space_list = new VirtualSpaceList(rs);
  _chunk_manager_class = new ChunkManager(true/*is_class*/);

  if (!_class_space_list->initialization_succeeded()) {
    vm_exit_during_initialization("Failed to setup compressed class space virtual space list.");
  }
}

#endif

void Metaspace::ergo_initialize() {
  if (DumpSharedSpaces) {
    // Using large pages when dumping the shared archive is currently not implemented.
    FLAG_SET_ERGO(bool, UseLargePagesInMetaspace, false);
  }

  size_t page_size = os::vm_page_size();
  if (UseLargePages && UseLargePagesInMetaspace) {
    page_size = os::large_page_size();
  }

  _commit_alignment  = page_size;
  _reserve_alignment = MAX2(page_size, (size_t)os::vm_allocation_granularity());

  // Do not use FLAG_SET_ERGO to update MaxMetaspaceSize, since this will
  // override if MaxMetaspaceSize was set on the command line or not.
  // This information is needed later to conform to the specification of the
  // java.lang.management.MemoryUsage API.
  //
  // Ideally, we would be able to set the default value of MaxMetaspaceSize in
  // globals.hpp to the aligned value, but this is not possible, since the
  // alignment depends on other flags being parsed.
  MaxMetaspaceSize = align_down_bounded(MaxMetaspaceSize, _reserve_alignment);

  if (MetaspaceSize > MaxMetaspaceSize) {
    MetaspaceSize = MaxMetaspaceSize;
  }

  MetaspaceSize = align_down_bounded(MetaspaceSize, _commit_alignment);

  assert(MetaspaceSize <= MaxMetaspaceSize, "MetaspaceSize should be limited by MaxMetaspaceSize");

  MinMetaspaceExpansion = align_down_bounded(MinMetaspaceExpansion, _commit_alignment);
  MaxMetaspaceExpansion = align_down_bounded(MaxMetaspaceExpansion, _commit_alignment);

  CompressedClassSpaceSize = align_down_bounded(CompressedClassSpaceSize, _reserve_alignment);

  // Initial virtual space size will be calculated at global_initialize()
  size_t min_metaspace_sz =
      VIRTUALSPACEMULTIPLIER * InitialBootClassLoaderMetaspaceSize;
  if (UseCompressedClassPointers) {
    if ((min_metaspace_sz + CompressedClassSpaceSize) >  MaxMetaspaceSize) {
      if (min_metaspace_sz >= MaxMetaspaceSize) {
        vm_exit_during_initialization("MaxMetaspaceSize is too small.");
      } else {
        FLAG_SET_ERGO(size_t, CompressedClassSpaceSize,
                      MaxMetaspaceSize - min_metaspace_sz);
      }
    }
  } else if (min_metaspace_sz >= MaxMetaspaceSize) {
    FLAG_SET_ERGO(size_t, InitialBootClassLoaderMetaspaceSize,
                  min_metaspace_sz);
  }

  set_compressed_class_space_size(CompressedClassSpaceSize);
}

void Metaspace::global_initialize() {
  MetaspaceGC::initialize();

#if INCLUDE_CDS
  if (DumpSharedSpaces) {
    MetaspaceShared::initialize_dumptime_shared_and_meta_spaces();
  } else if (UseSharedSpaces) {
    // If any of the archived space fails to map, UseSharedSpaces
    // is reset to false. Fall through to the
    // (!DumpSharedSpaces && !UseSharedSpaces) case to set up class
    // metaspace.
    MetaspaceShared::initialize_runtime_shared_and_meta_spaces();
  }

  if (!DumpSharedSpaces && !UseSharedSpaces)
#endif // INCLUDE_CDS
  {
#ifdef _LP64
    if (using_class_space()) {
      char* base = (char*)align_up(Universe::heap()->reserved_region().end(), _reserve_alignment);
      allocate_metaspace_compressed_klass_ptrs(base, 0);
    }
#endif // _LP64
  }

  // Initialize these before initializing the VirtualSpaceList
  _first_chunk_word_size = InitialBootClassLoaderMetaspaceSize / BytesPerWord;
  _first_chunk_word_size = align_word_size_up(_first_chunk_word_size);
  // Make the first class chunk bigger than a medium chunk so it's not put
  // on the medium chunk list.   The next chunk will be small and progress
  // from there.  This size calculated by -version.
  _first_class_chunk_word_size = MIN2((size_t)MediumChunk*6,
                                     (CompressedClassSpaceSize/BytesPerWord)*2);
  _first_class_chunk_word_size = align_word_size_up(_first_class_chunk_word_size);
  // Arbitrarily set the initial virtual space to a multiple
  // of the boot class loader size.
  size_t word_size = VIRTUALSPACEMULTIPLIER * _first_chunk_word_size;
  word_size = align_up(word_size, Metaspace::reserve_alignment_words());

  // Initialize the list of virtual spaces.
  _space_list = new VirtualSpaceList(word_size);
  _chunk_manager_metadata = new ChunkManager(false/*metaspace*/);

  if (!_space_list->initialization_succeeded()) {
    vm_exit_during_initialization("Unable to setup metadata virtual space list.", NULL);
  }

  _tracer = new MetaspaceTracer();
}

void Metaspace::post_initialize() {
  MetaspaceGC::post_initialize();
}

void Metaspace::verify_global_initialization() {
  assert(space_list() != NULL, "Metadata VirtualSpaceList has not been initialized");
  assert(chunk_manager_metadata() != NULL, "Metadata ChunkManager has not been initialized");

  if (using_class_space()) {
    assert(class_space_list() != NULL, "Class VirtualSpaceList has not been initialized");
    assert(chunk_manager_class() != NULL, "Class ChunkManager has not been initialized");
  }
}

size_t Metaspace::align_word_size_up(size_t word_size) {
  size_t byte_size = word_size * wordSize;
  return ReservedSpace::allocation_align_size_up(byte_size) / wordSize;
}

MetaWord* Metaspace::allocate(ClassLoaderData* loader_data, size_t word_size,
                              MetaspaceObj::Type type, TRAPS) {
  assert(!_frozen, "sanity");
  if (HAS_PENDING_EXCEPTION) {
    assert(false, "Should not allocate with exception pending");
    return NULL;  // caller does a CHECK_NULL too
  }

  assert(loader_data != NULL, "Should never pass around a NULL loader_data. "
        "ClassLoaderData::the_null_class_loader_data() should have been used.");

  MetadataType mdtype = (type == MetaspaceObj::ClassType) ? ClassType : NonClassType;

  // Try to allocate metadata.
  MetaWord* result = loader_data->metaspace_non_null()->allocate(word_size, mdtype);

  if (result == NULL) {
    tracer()->report_metaspace_allocation_failure(loader_data, word_size, type, mdtype);

    // Allocation failed.
    if (is_init_completed() && !(DumpSharedSpaces && THREAD->is_VM_thread())) {
      // Only start a GC if the bootstrapping has completed.
      // Also, we cannot GC if we are at the end of the CDS dumping stage which runs inside
      // the VM thread.

      // Try to clean out some memory and retry.
      result = Universe::heap()->satisfy_failed_metadata_allocation(loader_data, word_size, mdtype);
    }
  }

  if (result == NULL) {
    if (DumpSharedSpaces) {
      // CDS dumping keeps loading classes, so if we hit an OOM we probably will keep hitting OOM.
      // We should abort to avoid generating a potentially bad archive.
      tty->print_cr("Failed allocating metaspace object type %s of size " SIZE_FORMAT ". CDS dump aborted.",
          MetaspaceObj::type_name(type), word_size * BytesPerWord);
      tty->print_cr("Please increase MaxMetaspaceSize (currently " SIZE_FORMAT " bytes).", MaxMetaspaceSize);
      vm_exit(1);
    }
    report_metadata_oome(loader_data, word_size, type, mdtype, CHECK_NULL);
  }

  // Zero initialize.
  Copy::fill_to_words((HeapWord*)result, word_size, 0);

  return result;
}

void Metaspace::report_metadata_oome(ClassLoaderData* loader_data, size_t word_size, MetaspaceObj::Type type, MetadataType mdtype, TRAPS) {
  tracer()->report_metadata_oom(loader_data, word_size, type, mdtype);

  // If result is still null, we are out of memory.
  Log(gc, metaspace, freelist) log;
  if (log.is_info()) {
    log.info("Metaspace (%s) allocation failed for size " SIZE_FORMAT,
             is_class_space_allocation(mdtype) ? "class" : "data", word_size);
    ResourceMark rm;
    if (log.is_debug()) {
      if (loader_data->metaspace_or_null() != NULL) {
        LogStream ls(log.debug());
        loader_data->print_value_on(&ls);
      }
    }
    LogStream ls(log.info());
    MetaspaceUtils::dump(&ls);
    MetaspaceUtils::print_metaspace_map(&ls, mdtype);
    ChunkManager::print_all_chunkmanagers(&ls);
  }

  bool out_of_compressed_class_space = false;
  if (is_class_space_allocation(mdtype)) {
    ClassLoaderMetaspace* metaspace = loader_data->metaspace_non_null();
    out_of_compressed_class_space =
      MetaspaceUtils::committed_bytes(Metaspace::ClassType) +
      (metaspace->class_chunk_size(word_size) * BytesPerWord) >
      CompressedClassSpaceSize;
  }

  // -XX:+HeapDumpOnOutOfMemoryError and -XX:OnOutOfMemoryError support
  const char* space_string = out_of_compressed_class_space ?
    "Compressed class space" : "Metaspace";

  report_java_out_of_memory(space_string);

  if (JvmtiExport::should_post_resource_exhausted()) {
    JvmtiExport::post_resource_exhausted(
        JVMTI_RESOURCE_EXHAUSTED_OOM_ERROR,
        space_string);
  }

  if (!is_init_completed()) {
    vm_exit_during_initialization("OutOfMemoryError", space_string);
  }

  if (out_of_compressed_class_space) {
    THROW_OOP(Universe::out_of_memory_error_class_metaspace());
  } else {
    THROW_OOP(Universe::out_of_memory_error_metaspace());
  }
}

const char* Metaspace::metadata_type_name(Metaspace::MetadataType mdtype) {
  switch (mdtype) {
    case Metaspace::ClassType: return "Class";
    case Metaspace::NonClassType: return "Metadata";
    default:
      assert(false, "Got bad mdtype: %d", (int) mdtype);
      return NULL;
  }
}

void Metaspace::purge(MetadataType mdtype) {
  get_space_list(mdtype)->purge(get_chunk_manager(mdtype));
}

void Metaspace::purge() {
  MutexLockerEx cl(MetaspaceExpand_lock,
                   Mutex::_no_safepoint_check_flag);
  purge(NonClassType);
  if (using_class_space()) {
    purge(ClassType);
  }
}

bool Metaspace::contains(const void* ptr) {
  if (MetaspaceShared::is_in_shared_metaspace(ptr)) {
    return true;
  }
  return contains_non_shared(ptr);
}

bool Metaspace::contains_non_shared(const void* ptr) {
  if (using_class_space() && get_space_list(ClassType)->contains(ptr)) {
     return true;
  }

  return get_space_list(NonClassType)->contains(ptr);
}

// ClassLoaderMetaspace

ClassLoaderMetaspace::ClassLoaderMetaspace(Mutex* lock, Metaspace::MetaspaceType type) {
  initialize(lock, type);
}

ClassLoaderMetaspace::~ClassLoaderMetaspace() {
  delete _vsm;
  if (Metaspace::using_class_space()) {
    delete _class_vsm;
  }
}
void ClassLoaderMetaspace::initialize_first_chunk(Metaspace::MetaspaceType type, Metaspace::MetadataType mdtype) {
  Metachunk* chunk = get_initialization_chunk(type, mdtype);
  if (chunk != NULL) {
    // Add to this manager's list of chunks in use and current_chunk().
    get_space_manager(mdtype)->add_chunk(chunk, true);
  }
}

Metachunk* ClassLoaderMetaspace::get_initialization_chunk(Metaspace::MetaspaceType type, Metaspace::MetadataType mdtype) {
  size_t chunk_word_size = get_space_manager(mdtype)->get_initial_chunk_size(type);

  // Get a chunk from the chunk freelist
  Metachunk* chunk = Metaspace::get_chunk_manager(mdtype)->chunk_freelist_allocate(chunk_word_size);

  if (chunk == NULL) {
    chunk = Metaspace::get_space_list(mdtype)->get_new_chunk(chunk_word_size,
                                                  get_space_manager(mdtype)->medium_chunk_bunch());
  }

  return chunk;
}

void ClassLoaderMetaspace::initialize(Mutex* lock, Metaspace::MetaspaceType type) {
  Metaspace::verify_global_initialization();

  // Allocate SpaceManager for metadata objects.
  _vsm = new SpaceManager(Metaspace::NonClassType, type, lock);

  if (Metaspace::using_class_space()) {
    // Allocate SpaceManager for classes.
    _class_vsm = new SpaceManager(Metaspace::ClassType, type, lock);
  }

  MutexLockerEx cl(MetaspaceExpand_lock, Mutex::_no_safepoint_check_flag);

  // Allocate chunk for metadata objects
  initialize_first_chunk(type, Metaspace::NonClassType);

  // Allocate chunk for class metadata objects
  if (Metaspace::using_class_space()) {
    initialize_first_chunk(type, Metaspace::ClassType);
  }
}

MetaWord* ClassLoaderMetaspace::allocate(size_t word_size, Metaspace::MetadataType mdtype) {
  Metaspace::assert_not_frozen();
  // Don't use class_vsm() unless UseCompressedClassPointers is true.
  if (Metaspace::is_class_space_allocation(mdtype)) {
    return  class_vsm()->allocate(word_size);
  } else {
    return  vsm()->allocate(word_size);
  }
}

MetaWord* ClassLoaderMetaspace::expand_and_allocate(size_t word_size, Metaspace::MetadataType mdtype) {
  Metaspace::assert_not_frozen();
  size_t delta_bytes = MetaspaceGC::delta_capacity_until_GC(word_size * BytesPerWord);
  assert(delta_bytes > 0, "Must be");

  size_t before = 0;
  size_t after = 0;
  MetaWord* res;
  bool incremented;

  // Each thread increments the HWM at most once. Even if the thread fails to increment
  // the HWM, an allocation is still attempted. This is because another thread must then
  // have incremented the HWM and therefore the allocation might still succeed.
  do {
    incremented = MetaspaceGC::inc_capacity_until_GC(delta_bytes, &after, &before);
    res = allocate(word_size, mdtype);
  } while (!incremented && res == NULL);

  if (incremented) {
    Metaspace::tracer()->report_gc_threshold(before, after,
                                  MetaspaceGCThresholdUpdater::ExpandAndAllocate);
    log_trace(gc, metaspace)("Increase capacity to GC from " SIZE_FORMAT " to " SIZE_FORMAT, before, after);
  }

  return res;
}

size_t ClassLoaderMetaspace::used_words_slow(Metaspace::MetadataType mdtype) const {
  if (mdtype == Metaspace::ClassType) {
    return Metaspace::using_class_space() ? class_vsm()->sum_used_in_chunks_in_use() : 0;
  } else {
    return vsm()->sum_used_in_chunks_in_use();  // includes overhead!
  }
}

size_t ClassLoaderMetaspace::free_words_slow(Metaspace::MetadataType mdtype) const {
  Metaspace::assert_not_frozen();
  if (mdtype == Metaspace::ClassType) {
    return Metaspace::using_class_space() ? class_vsm()->sum_free_in_chunks_in_use() : 0;
  } else {
    return vsm()->sum_free_in_chunks_in_use();
  }
}

// Space capacity in the Metaspace.  It includes
// space in the list of chunks from which allocations
// have been made. Don't include space in the global freelist and
// in the space available in the dictionary which
// is already counted in some chunk.
size_t ClassLoaderMetaspace::capacity_words_slow(Metaspace::MetadataType mdtype) const {
  if (mdtype == Metaspace::ClassType) {
    return Metaspace::using_class_space() ? class_vsm()->sum_capacity_in_chunks_in_use() : 0;
  } else {
    return vsm()->sum_capacity_in_chunks_in_use();
  }
}

size_t ClassLoaderMetaspace::used_bytes_slow(Metaspace::MetadataType mdtype) const {
  return used_words_slow(mdtype) * BytesPerWord;
}

size_t ClassLoaderMetaspace::capacity_bytes_slow(Metaspace::MetadataType mdtype) const {
  return capacity_words_slow(mdtype) * BytesPerWord;
}

size_t ClassLoaderMetaspace::allocated_blocks_bytes() const {
  return vsm()->allocated_blocks_bytes() +
      (Metaspace::using_class_space() ? class_vsm()->allocated_blocks_bytes() : 0);
}

size_t ClassLoaderMetaspace::allocated_chunks_bytes() const {
  return vsm()->allocated_chunks_bytes() +
      (Metaspace::using_class_space() ? class_vsm()->allocated_chunks_bytes() : 0);
}

void ClassLoaderMetaspace::deallocate(MetaWord* ptr, size_t word_size, bool is_class) {
  Metaspace::assert_not_frozen();
  assert(!SafepointSynchronize::is_at_safepoint()
         || Thread::current()->is_VM_thread(), "should be the VM thread");

  MutexLockerEx ml(vsm()->lock(), Mutex::_no_safepoint_check_flag);

  if (is_class && Metaspace::using_class_space()) {
    class_vsm()->deallocate(ptr, word_size);
  } else {
    vsm()->deallocate(ptr, word_size);
  }
}

size_t ClassLoaderMetaspace::class_chunk_size(size_t word_size) {
  assert(Metaspace::using_class_space(), "Has to use class space");
  return class_vsm()->calc_chunk_size(word_size);
}

void ClassLoaderMetaspace::print_on(outputStream* out) const {
  // Print both class virtual space counts and metaspace.
  if (Verbose) {
    vsm()->print_on(out);
    if (Metaspace::using_class_space()) {
      class_vsm()->print_on(out);
    }
  }
}

void ClassLoaderMetaspace::verify() {
  vsm()->verify();
  if (Metaspace::using_class_space()) {
    class_vsm()->verify();
  }
}

void ClassLoaderMetaspace::dump(outputStream* const out) const {
  out->print_cr("\nVirtual space manager: " INTPTR_FORMAT, p2i(vsm()));
  vsm()->dump(out);
  if (Metaspace::using_class_space()) {
    out->print_cr("\nClass space manager: " INTPTR_FORMAT, p2i(class_vsm()));
    class_vsm()->dump(out);
  }
}



#ifdef ASSERT
static void do_verify_chunk(Metachunk* chunk) {
  guarantee(chunk != NULL, "Sanity");
  // Verify chunk itself; then verify that it is consistent with the
  // occupany map of its containing node.
  chunk->verify();
  VirtualSpaceNode* const vsn = chunk->container();
  OccupancyMap* const ocmap = vsn->occupancy_map();
  ocmap->verify_for_chunk(chunk);
}
#endif

static void do_update_in_use_info_for_chunk(Metachunk* chunk, bool inuse) {
  chunk->set_is_tagged_free(!inuse);
  OccupancyMap* const ocmap = chunk->container()->occupancy_map();
  ocmap->set_region_in_use((MetaWord*)chunk, chunk->word_size(), inuse);
}

/////////////// Unit tests ///////////////

#ifndef PRODUCT

class TestMetaspaceUtilsTest : AllStatic {
 public:
  static void test_reserved() {
    size_t reserved = MetaspaceUtils::reserved_bytes();

    assert(reserved > 0, "assert");

    size_t committed  = MetaspaceUtils::committed_bytes();
    assert(committed <= reserved, "assert");

    size_t reserved_metadata = MetaspaceUtils::reserved_bytes(Metaspace::NonClassType);
    assert(reserved_metadata > 0, "assert");
    assert(reserved_metadata <= reserved, "assert");

    if (UseCompressedClassPointers) {
      size_t reserved_class    = MetaspaceUtils::reserved_bytes(Metaspace::ClassType);
      assert(reserved_class > 0, "assert");
      assert(reserved_class < reserved, "assert");
    }
  }

  static void test_committed() {
    size_t committed = MetaspaceUtils::committed_bytes();

    assert(committed > 0, "assert");

    size_t reserved  = MetaspaceUtils::reserved_bytes();
    assert(committed <= reserved, "assert");

    size_t committed_metadata = MetaspaceUtils::committed_bytes(Metaspace::NonClassType);
    assert(committed_metadata > 0, "assert");
    assert(committed_metadata <= committed, "assert");

    if (UseCompressedClassPointers) {
      size_t committed_class    = MetaspaceUtils::committed_bytes(Metaspace::ClassType);
      assert(committed_class > 0, "assert");
      assert(committed_class < committed, "assert");
    }
  }

  static void test_virtual_space_list_large_chunk() {
    VirtualSpaceList* vs_list = new VirtualSpaceList(os::vm_allocation_granularity());
    MutexLockerEx cl(MetaspaceExpand_lock, Mutex::_no_safepoint_check_flag);
    // A size larger than VirtualSpaceSize (256k) and add one page to make it _not_ be
    // vm_allocation_granularity aligned on Windows.
    size_t large_size = (size_t)(2*256*K + (os::vm_page_size()/BytesPerWord));
    large_size += (os::vm_page_size()/BytesPerWord);
    vs_list->get_new_chunk(large_size, 0);
  }

  static void test() {
    test_reserved();
    test_committed();
    test_virtual_space_list_large_chunk();
  }
};

void TestMetaspaceUtils_test() {
  TestMetaspaceUtilsTest::test();
}

class TestVirtualSpaceNodeTest {
  static void chunk_up(size_t words_left, size_t& num_medium_chunks,
                                          size_t& num_small_chunks,
                                          size_t& num_specialized_chunks) {
    num_medium_chunks = words_left / MediumChunk;
    words_left = words_left % MediumChunk;

    num_small_chunks = words_left / SmallChunk;
    words_left = words_left % SmallChunk;
    // how many specialized chunks can we get?
    num_specialized_chunks = words_left / SpecializedChunk;
    assert(words_left % SpecializedChunk == 0, "should be nothing left");
  }

 public:
  static void test() {
    MutexLockerEx ml(MetaspaceExpand_lock, Mutex::_no_safepoint_check_flag);
    const size_t vsn_test_size_words = MediumChunk  * 4;
    const size_t vsn_test_size_bytes = vsn_test_size_words * BytesPerWord;

    // The chunk sizes must be multiples of eachother, or this will fail
    STATIC_ASSERT(MediumChunk % SmallChunk == 0);
    STATIC_ASSERT(SmallChunk % SpecializedChunk == 0);

    { // No committed memory in VSN
      ChunkManager cm(false);
      VirtualSpaceNode vsn(false, vsn_test_size_bytes);
      vsn.initialize();
      vsn.retire(&cm);
      assert(cm.sum_free_chunks_count() == 0, "did not commit any memory in the VSN");
    }

    { // All of VSN is committed, half is used by chunks
      ChunkManager cm(false);
      VirtualSpaceNode vsn(false, vsn_test_size_bytes);
      vsn.initialize();
      vsn.expand_by(vsn_test_size_words, vsn_test_size_words);
      vsn.get_chunk_vs(MediumChunk);
      vsn.get_chunk_vs(MediumChunk);
      vsn.retire(&cm);
      assert(cm.sum_free_chunks_count() == 2, "should have been memory left for 2 medium chunks");
      assert(cm.sum_free_chunks() == 2*MediumChunk, "sizes should add up");
    }

    const size_t page_chunks = 4 * (size_t)os::vm_page_size() / BytesPerWord;
    // This doesn't work for systems with vm_page_size >= 16K.
    if (page_chunks < MediumChunk) {
      // 4 pages of VSN is committed, some is used by chunks
      ChunkManager cm(false);
      VirtualSpaceNode vsn(false, vsn_test_size_bytes);

      vsn.initialize();
      vsn.expand_by(page_chunks, page_chunks);
      vsn.get_chunk_vs(SmallChunk);
      vsn.get_chunk_vs(SpecializedChunk);
      vsn.retire(&cm);

      // committed - used = words left to retire
      const size_t words_left = page_chunks - SmallChunk - SpecializedChunk;

      size_t num_medium_chunks, num_small_chunks, num_spec_chunks;
      chunk_up(words_left, num_medium_chunks, num_small_chunks, num_spec_chunks);

      assert(num_medium_chunks == 0, "should not get any medium chunks");
      assert(cm.sum_free_chunks_count() == (num_small_chunks + num_spec_chunks), "should be space for 3 chunks");
      assert(cm.sum_free_chunks() == words_left, "sizes should add up");
    }

    { // Half of VSN is committed, a humongous chunk is used
      ChunkManager cm(false);
      VirtualSpaceNode vsn(false, vsn_test_size_bytes);
      vsn.initialize();
      vsn.expand_by(MediumChunk * 2, MediumChunk * 2);
      vsn.get_chunk_vs(MediumChunk + SpecializedChunk); // Humongous chunks will be aligned up to MediumChunk + SpecializedChunk
      vsn.retire(&cm);

      const size_t words_left = MediumChunk * 2 - (MediumChunk + SpecializedChunk);
      size_t num_medium_chunks, num_small_chunks, num_spec_chunks;
      chunk_up(words_left, num_medium_chunks, num_small_chunks, num_spec_chunks);

      assert(num_medium_chunks == 0, "should not get any medium chunks");
      assert(cm.sum_free_chunks_count() == (num_small_chunks + num_spec_chunks), "should be space for 3 chunks");
      assert(cm.sum_free_chunks() == words_left, "sizes should add up");
    }

  }

#define assert_is_available_positive(word_size) \
  assert(vsn.is_available(word_size), \
         #word_size ": " PTR_FORMAT " bytes were not available in " \
         "VirtualSpaceNode [" PTR_FORMAT ", " PTR_FORMAT ")", \
         (uintptr_t)(word_size * BytesPerWord), p2i(vsn.bottom()), p2i(vsn.end()));

#define assert_is_available_negative(word_size) \
  assert(!vsn.is_available(word_size), \
         #word_size ": " PTR_FORMAT " bytes should not be available in " \
         "VirtualSpaceNode [" PTR_FORMAT ", " PTR_FORMAT ")", \
         (uintptr_t)(word_size * BytesPerWord), p2i(vsn.bottom()), p2i(vsn.end()));

  static void test_is_available_positive() {
    // Reserve some memory.
    VirtualSpaceNode vsn(false, os::vm_allocation_granularity());
    assert(vsn.initialize(), "Failed to setup VirtualSpaceNode");

    // Commit some memory.
    size_t commit_word_size = os::vm_allocation_granularity() / BytesPerWord;
    bool expanded = vsn.expand_by(commit_word_size, commit_word_size);
    assert(expanded, "Failed to commit");

    // Check that is_available accepts the committed size.
    assert_is_available_positive(commit_word_size);

    // Check that is_available accepts half the committed size.
    size_t expand_word_size = commit_word_size / 2;
    assert_is_available_positive(expand_word_size);
  }

  static void test_is_available_negative() {
    // Reserve some memory.
    VirtualSpaceNode vsn(false, os::vm_allocation_granularity());
    assert(vsn.initialize(), "Failed to setup VirtualSpaceNode");

    // Commit some memory.
    size_t commit_word_size = os::vm_allocation_granularity() / BytesPerWord;
    bool expanded = vsn.expand_by(commit_word_size, commit_word_size);
    assert(expanded, "Failed to commit");

    // Check that is_available doesn't accept a too large size.
    size_t two_times_commit_word_size = commit_word_size * 2;
    assert_is_available_negative(two_times_commit_word_size);
  }

  static void test_is_available_overflow() {
    // Reserve some memory.
    VirtualSpaceNode vsn(false, os::vm_allocation_granularity());
    assert(vsn.initialize(), "Failed to setup VirtualSpaceNode");

    // Commit some memory.
    size_t commit_word_size = os::vm_allocation_granularity() / BytesPerWord;
    bool expanded = vsn.expand_by(commit_word_size, commit_word_size);
    assert(expanded, "Failed to commit");

    // Calculate a size that will overflow the virtual space size.
    void* virtual_space_max = (void*)(uintptr_t)-1;
    size_t bottom_to_max = pointer_delta(virtual_space_max, vsn.bottom(), 1);
    size_t overflow_size = bottom_to_max + BytesPerWord;
    size_t overflow_word_size = overflow_size / BytesPerWord;

    // Check that is_available can handle the overflow.
    assert_is_available_negative(overflow_word_size);
  }

  static void test_is_available() {
    TestVirtualSpaceNodeTest::test_is_available_positive();
    TestVirtualSpaceNodeTest::test_is_available_negative();
    TestVirtualSpaceNodeTest::test_is_available_overflow();
  }
};

// The following test is placed here instead of a gtest / unittest file
// because the ChunkManager class is only available in this file.
void ChunkManager_test_list_index() {
  {
    // Test previous bug where a query for a humongous class metachunk,
    // incorrectly matched the non-class medium metachunk size.
    {
      ChunkManager manager(true);

      assert(MediumChunk > ClassMediumChunk, "Precondition for test");

      ChunkIndex index = manager.list_index(MediumChunk);

      assert(index == HumongousIndex,
          "Requested size is larger than ClassMediumChunk,"
          " so should return HumongousIndex. Got index: %d", (int)index);
    }

    // Check the specified sizes as well.
    {
      ChunkManager manager(true);
      assert(manager.list_index(ClassSpecializedChunk) == SpecializedIndex, "sanity");
      assert(manager.list_index(ClassSmallChunk) == SmallIndex, "sanity");
      assert(manager.list_index(ClassMediumChunk) == MediumIndex, "sanity");
      assert(manager.list_index(ClassMediumChunk + ClassSpecializedChunk) == HumongousIndex, "sanity");
    }
    {
      ChunkManager manager(false);
      assert(manager.list_index(SpecializedChunk) == SpecializedIndex, "sanity");
      assert(manager.list_index(SmallChunk) == SmallIndex, "sanity");
      assert(manager.list_index(MediumChunk) == MediumIndex, "sanity");
      assert(manager.list_index(MediumChunk + SpecializedChunk) == HumongousIndex, "sanity");
    }

  }

}

#endif // !PRODUCT

#ifdef ASSERT

// The following test is placed here instead of a gtest / unittest file
// because the ChunkManager class is only available in this file.
class SpaceManagerTest : AllStatic {
  friend void SpaceManager_test_adjust_initial_chunk_size();

  static void test_adjust_initial_chunk_size(bool is_class) {
    const size_t smallest = SpaceManager::smallest_chunk_size(is_class);
    const size_t normal   = SpaceManager::small_chunk_size(is_class);
    const size_t medium   = SpaceManager::medium_chunk_size(is_class);

#define test_adjust_initial_chunk_size(value, expected, is_class_value)          \
    do {                                                                         \
      size_t v = value;                                                          \
      size_t e = expected;                                                       \
      assert(SpaceManager::adjust_initial_chunk_size(v, (is_class_value)) == e,  \
             "Expected: " SIZE_FORMAT " got: " SIZE_FORMAT, e, v);               \
    } while (0)

    // Smallest (specialized)
    test_adjust_initial_chunk_size(1,            smallest, is_class);
    test_adjust_initial_chunk_size(smallest - 1, smallest, is_class);
    test_adjust_initial_chunk_size(smallest,     smallest, is_class);

    // Small
    test_adjust_initial_chunk_size(smallest + 1, normal, is_class);
    test_adjust_initial_chunk_size(normal - 1,   normal, is_class);
    test_adjust_initial_chunk_size(normal,       normal, is_class);

    // Medium
    test_adjust_initial_chunk_size(normal + 1, medium, is_class);
    test_adjust_initial_chunk_size(medium - 1, medium, is_class);
    test_adjust_initial_chunk_size(medium,     medium, is_class);

    // Humongous
    test_adjust_initial_chunk_size(medium + 1, medium + 1, is_class);

#undef test_adjust_initial_chunk_size
  }

  static void test_adjust_initial_chunk_size() {
    test_adjust_initial_chunk_size(false);
    test_adjust_initial_chunk_size(true);
  }
};

void SpaceManager_test_adjust_initial_chunk_size() {
  SpaceManagerTest::test_adjust_initial_chunk_size();
}

#endif // ASSERT

struct chunkmanager_statistics_t {
  int num_specialized_chunks;
  int num_small_chunks;
  int num_medium_chunks;
  int num_humongous_chunks;
};

extern void test_metaspace_retrieve_chunkmanager_statistics(Metaspace::MetadataType mdType, chunkmanager_statistics_t* out) {
  ChunkManager* const chunk_manager = Metaspace::get_chunk_manager(mdType);
  ChunkManager::ChunkManagerStatistics stat;
  chunk_manager->get_statistics(&stat);
  out->num_specialized_chunks = (int)stat.num_by_type[SpecializedIndex];
  out->num_small_chunks = (int)stat.num_by_type[SmallIndex];
  out->num_medium_chunks = (int)stat.num_by_type[MediumIndex];
  out->num_humongous_chunks = (int)stat.num_humongous_chunks;
}

struct chunk_geometry_t {
  size_t specialized_chunk_word_size;
  size_t small_chunk_word_size;
  size_t medium_chunk_word_size;
};

extern void test_metaspace_retrieve_chunk_geometry(Metaspace::MetadataType mdType, chunk_geometry_t* out) {
  if (mdType == Metaspace::NonClassType) {
    out->specialized_chunk_word_size = SpecializedChunk;
    out->small_chunk_word_size = SmallChunk;
    out->medium_chunk_word_size = MediumChunk;
  } else {
    out->specialized_chunk_word_size = ClassSpecializedChunk;
    out->small_chunk_word_size = ClassSmallChunk;
    out->medium_chunk_word_size = ClassMediumChunk;
  }
}
