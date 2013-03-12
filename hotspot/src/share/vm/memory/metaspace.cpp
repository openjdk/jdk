/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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
#include "gc_interface/collectedHeap.hpp"
#include "memory/binaryTreeDictionary.hpp"
#include "memory/freeList.hpp"
#include "memory/collectorPolicy.hpp"
#include "memory/filemap.hpp"
#include "memory/freeList.hpp"
#include "memory/metablock.hpp"
#include "memory/metachunk.hpp"
#include "memory/metaspace.hpp"
#include "memory/metaspaceShared.hpp"
#include "memory/resourceArea.hpp"
#include "memory/universe.hpp"
#include "runtime/globals.hpp"
#include "runtime/mutex.hpp"
#include "runtime/orderAccess.hpp"
#include "services/memTracker.hpp"
#include "utilities/copy.hpp"
#include "utilities/debug.hpp"

typedef BinaryTreeDictionary<Metablock, FreeList> BlockTreeDictionary;
typedef BinaryTreeDictionary<Metachunk, FreeList> ChunkTreeDictionary;
// Define this macro to enable slow integrity checking of
// the free chunk lists
const bool metaspace_slow_verify = false;


// Parameters for stress mode testing
const uint metadata_deallocate_a_lot_block = 10;
const uint metadata_deallocate_a_lock_chunk = 3;
size_t const allocation_from_dictionary_limit = 64 * K;
const size_t metadata_deallocate = 0xf5f5f5f5;

MetaWord* last_allocated = 0;

// Used in declarations in SpaceManager and ChunkManager
enum ChunkIndex {
  ZeroIndex = 0,
  SpecializedIndex = ZeroIndex,
  SmallIndex = SpecializedIndex + 1,
  MediumIndex = SmallIndex + 1,
  HumongousIndex = MediumIndex + 1,
  NumberOfFreeLists = 3,
  NumberOfInUseLists = 4
};

enum ChunkSizes {    // in words.
  ClassSpecializedChunk = 128,
  SpecializedChunk = 128,
  ClassSmallChunk = 256,
  SmallChunk = 512,
  ClassMediumChunk = 1 * K,
  MediumChunk = 8 * K,
  HumongousChunkGranularity = 8
};

static ChunkIndex next_chunk_index(ChunkIndex i) {
  assert(i < NumberOfInUseLists, "Out of bound");
  return (ChunkIndex) (i+1);
}

// Originally _capacity_until_GC was set to MetaspaceSize here but
// the default MetaspaceSize before argument processing was being
// used which was not the desired value.  See the code
// in should_expand() to see how the initialization is handled
// now.
size_t MetaspaceGC::_capacity_until_GC = 0;
bool MetaspaceGC::_expand_after_GC = false;
uint MetaspaceGC::_shrink_factor = 0;
bool MetaspaceGC::_should_concurrent_collect = false;

// Blocks of space for metadata are allocated out of Metachunks.
//
// Metachunk are allocated out of MetadataVirtualspaces and once
// allocated there is no explicit link between a Metachunk and
// the MetadataVirtualspaces from which it was allocated.
//
// Each SpaceManager maintains a
// list of the chunks it is using and the current chunk.  The current
// chunk is the chunk from which allocations are done.  Space freed in
// a chunk is placed on the free list of blocks (BlockFreelist) and
// reused from there.

// Pointer to list of Metachunks.
class ChunkList VALUE_OBJ_CLASS_SPEC {
  // List of free chunks
  Metachunk* _head;

 public:
  // Constructor
  ChunkList() : _head(NULL) {}

  // Accessors
  Metachunk* head() { return _head; }
  void set_head(Metachunk* v) { _head = v; }

  // Link at head of the list
  void add_at_head(Metachunk* head, Metachunk* tail);
  void add_at_head(Metachunk* head);

  size_t sum_list_size();
  size_t sum_list_count();
  size_t sum_list_capacity();
};

// Manages the global free lists of chunks.
// Has three lists of free chunks, and a total size and
// count that includes all three

class ChunkManager VALUE_OBJ_CLASS_SPEC {

  // Free list of chunks of different sizes.
  //   SmallChunk
  //   MediumChunk
  //   HumongousChunk
  ChunkList _free_chunks[NumberOfFreeLists];


  //   HumongousChunk
  ChunkTreeDictionary _humongous_dictionary;

  // ChunkManager in all lists of this type
  size_t _free_chunks_total;
  size_t _free_chunks_count;

  void dec_free_chunks_total(size_t v) {
    assert(_free_chunks_count > 0 &&
             _free_chunks_total > 0,
             "About to go negative");
    Atomic::add_ptr(-1, &_free_chunks_count);
    jlong minus_v = (jlong) - (jlong) v;
    Atomic::add_ptr(minus_v, &_free_chunks_total);
  }

  // Debug support

  size_t sum_free_chunks();
  size_t sum_free_chunks_count();

  void locked_verify_free_chunks_total();
  void slow_locked_verify_free_chunks_total() {
    if (metaspace_slow_verify) {
      locked_verify_free_chunks_total();
    }
  }
  void locked_verify_free_chunks_count();
  void slow_locked_verify_free_chunks_count() {
    if (metaspace_slow_verify) {
      locked_verify_free_chunks_count();
    }
  }
  void verify_free_chunks_count();

 public:

  ChunkManager() : _free_chunks_total(0), _free_chunks_count(0) {}

  // add or delete (return) a chunk to the global freelist.
  Metachunk* chunk_freelist_allocate(size_t word_size);
  void chunk_freelist_deallocate(Metachunk* chunk);

  // Map a size to a list index assuming that there are lists
  // for special, small, medium, and humongous chunks.
  static ChunkIndex list_index(size_t size);

  // Total of the space in the free chunks list
  size_t free_chunks_total();
  size_t free_chunks_total_in_bytes();

  // Number of chunks in the free chunks list
  size_t free_chunks_count();

  void inc_free_chunks_total(size_t v, size_t count = 1) {
    Atomic::add_ptr(count, &_free_chunks_count);
    Atomic::add_ptr(v, &_free_chunks_total);
  }
  ChunkTreeDictionary* humongous_dictionary() {
    return &_humongous_dictionary;
  }

  ChunkList* free_chunks(ChunkIndex index);

  // Returns the list for the given chunk word size.
  ChunkList* find_free_chunks_list(size_t word_size);

  // Add and remove from a list by size.  Selects
  // list based on size of chunk.
  void free_chunks_put(Metachunk* chuck);
  Metachunk* free_chunks_get(size_t chunk_word_size);

  // Debug support
  void verify();
  void slow_verify() {
    if (metaspace_slow_verify) {
      verify();
    }
  }
  void locked_verify();
  void slow_locked_verify() {
    if (metaspace_slow_verify) {
      locked_verify();
    }
  }
  void verify_free_chunks_total();

  void locked_print_free_chunks(outputStream* st);
  void locked_print_sum_free_chunks(outputStream* st);

  void print_on(outputStream* st);
};


// Used to manage the free list of Metablocks (a block corresponds
// to the allocation of a quantum of metadata).
class BlockFreelist VALUE_OBJ_CLASS_SPEC {
  BlockTreeDictionary* _dictionary;
  static Metablock* initialize_free_chunk(MetaWord* p, size_t word_size);

  // Accessors
  BlockTreeDictionary* dictionary() const { return _dictionary; }

 public:
  BlockFreelist();
  ~BlockFreelist();

  // Get and return a block to the free list
  MetaWord* get_block(size_t word_size);
  void return_block(MetaWord* p, size_t word_size);

  size_t total_size() {
  if (dictionary() == NULL) {
    return 0;
  } else {
    return dictionary()->total_size();
  }
}

  void print_on(outputStream* st) const;
};

class VirtualSpaceNode : public CHeapObj<mtClass> {
  friend class VirtualSpaceList;

  // Link to next VirtualSpaceNode
  VirtualSpaceNode* _next;

  // total in the VirtualSpace
  MemRegion _reserved;
  ReservedSpace _rs;
  VirtualSpace _virtual_space;
  MetaWord* _top;

  // Convenience functions for logical bottom and end
  MetaWord* bottom() const { return (MetaWord*) _virtual_space.low(); }
  MetaWord* end() const { return (MetaWord*) _virtual_space.high(); }

  // Convenience functions to access the _virtual_space
  char* low()  const { return virtual_space()->low(); }
  char* high() const { return virtual_space()->high(); }

 public:

  VirtualSpaceNode(size_t byte_size);
  VirtualSpaceNode(ReservedSpace rs) : _top(NULL), _next(NULL), _rs(rs) {}
  ~VirtualSpaceNode();

  // address of next available space in _virtual_space;
  // Accessors
  VirtualSpaceNode* next() { return _next; }
  void set_next(VirtualSpaceNode* v) { _next = v; }

  void set_reserved(MemRegion const v) { _reserved = v; }
  void set_top(MetaWord* v) { _top = v; }

  // Accessors
  MemRegion* reserved() { return &_reserved; }
  VirtualSpace* virtual_space() const { return (VirtualSpace*) &_virtual_space; }

  // Returns true if "word_size" is available in the virtual space
  bool is_available(size_t word_size) { return _top + word_size <= end(); }

  MetaWord* top() const { return _top; }
  void inc_top(size_t word_size) { _top += word_size; }

  // used and capacity in this single entry in the list
  size_t used_words_in_vs() const;
  size_t capacity_words_in_vs() const;

  bool initialize();

  // get space from the virtual space
  Metachunk* take_from_committed(size_t chunk_word_size);

  // Allocate a chunk from the virtual space and return it.
  Metachunk* get_chunk_vs(size_t chunk_word_size);
  Metachunk* get_chunk_vs_with_expand(size_t chunk_word_size);

  // Expands/shrinks the committed space in a virtual space.  Delegates
  // to Virtualspace
  bool expand_by(size_t words, bool pre_touch = false);
  bool shrink_by(size_t words);

#ifdef ASSERT
  // Debug support
  static void verify_virtual_space_total();
  static void verify_virtual_space_count();
  void mangle();
#endif

  void print_on(outputStream* st) const;
};

  // byte_size is the size of the associated virtualspace.
VirtualSpaceNode::VirtualSpaceNode(size_t byte_size) : _top(NULL), _next(NULL), _rs(0) {
  // This allocates memory with mmap.  For DumpSharedspaces, allocate the
  // space at low memory so that other shared images don't conflict.
  // This is the same address as memory needed for UseCompressedOops but
  // compressed oops don't work with CDS (offsets in metadata are wrong), so
  // borrow the same address.
  if (DumpSharedSpaces) {
    char* shared_base = (char*)HeapBaseMinAddress;
    _rs = ReservedSpace(byte_size, 0, false, shared_base, 0);
    if (_rs.is_reserved()) {
      assert(_rs.base() == shared_base, "should match");
    } else {
      // If we are dumping the heap, then allocate a wasted block of address
      // space in order to push the heap to a lower address.  This extra
      // address range allows for other (or larger) libraries to be loaded
      // without them occupying the space required for the shared spaces.
      uintx reserved = 0;
      uintx block_size = 64*1024*1024;
      while (reserved < SharedDummyBlockSize) {
        char* dummy = os::reserve_memory(block_size);
        reserved += block_size;
      }
      _rs = ReservedSpace(byte_size);
    }
    MetaspaceShared::set_shared_rs(&_rs);
  } else {
    _rs = ReservedSpace(byte_size);
  }

  MemTracker::record_virtual_memory_type((address)_rs.base(), mtClass);
}

// List of VirtualSpaces for metadata allocation.
// It has a  _next link for singly linked list and a MemRegion
// for total space in the VirtualSpace.
class VirtualSpaceList : public CHeapObj<mtClass> {
  friend class VirtualSpaceNode;

  enum VirtualSpaceSizes {
    VirtualSpaceSize = 256 * K
  };

  // Global list of virtual spaces
  // Head of the list
  VirtualSpaceNode* _virtual_space_list;
  // virtual space currently being used for allocations
  VirtualSpaceNode* _current_virtual_space;
  // Free chunk list for all other metadata
  ChunkManager      _chunk_manager;

  // Can this virtual list allocate >1 spaces?  Also, used to determine
  // whether to allocate unlimited small chunks in this virtual space
  bool _is_class;
  bool can_grow() const { return !is_class() || !UseCompressedKlassPointers; }

  // Sum of space in all virtual spaces and number of virtual spaces
  size_t _virtual_space_total;
  size_t _virtual_space_count;

  ~VirtualSpaceList();

  VirtualSpaceNode* virtual_space_list() const { return _virtual_space_list; }

  void set_virtual_space_list(VirtualSpaceNode* v) {
    _virtual_space_list = v;
  }
  void set_current_virtual_space(VirtualSpaceNode* v) {
    _current_virtual_space = v;
  }

  void link_vs(VirtualSpaceNode* new_entry, size_t vs_word_size);

  // Get another virtual space and add it to the list.  This
  // is typically prompted by a failed attempt to allocate a chunk
  // and is typically followed by the allocation of a chunk.
  bool grow_vs(size_t vs_word_size);

 public:
  VirtualSpaceList(size_t word_size);
  VirtualSpaceList(ReservedSpace rs);

  Metachunk* get_new_chunk(size_t word_size,
                           size_t grow_chunks_by_words,
                           size_t medium_chunk_bunch);

  // Get the first chunk for a Metaspace.  Used for
  // special cases such as the boot class loader, reflection
  // class loader and anonymous class loader.
  Metachunk* get_initialization_chunk(size_t word_size, size_t chunk_bunch);

  VirtualSpaceNode* current_virtual_space() {
    return _current_virtual_space;
  }

  ChunkManager* chunk_manager() { return &_chunk_manager; }
  bool is_class() const { return _is_class; }

  // Allocate the first virtualspace.
  void initialize(size_t word_size);

  size_t virtual_space_total() { return _virtual_space_total; }
  void inc_virtual_space_total(size_t v) {
    Atomic::add_ptr(v, &_virtual_space_total);
  }

  size_t virtual_space_count() { return _virtual_space_count; }
  void inc_virtual_space_count() {
    Atomic::inc_ptr(&_virtual_space_count);
  }

  // Used and capacity in the entire list of virtual spaces.
  // These are global values shared by all Metaspaces
  size_t capacity_words_sum();
  size_t capacity_bytes_sum() { return capacity_words_sum() * BytesPerWord; }
  size_t used_words_sum();
  size_t used_bytes_sum() { return used_words_sum() * BytesPerWord; }

  bool contains(const void *ptr);

  void print_on(outputStream* st) const;

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
  static int _deallocate_block_a_lot_count;
  static int _deallocate_chunk_a_lot_count;
  static int _allocation_fail_alot_count;

 public:
  static int deallocate_block_a_lot_count() {
    return _deallocate_block_a_lot_count;
  }
  static void set_deallocate_block_a_lot_count(int v) {
    _deallocate_block_a_lot_count = v;
  }
  static void inc_deallocate_block_a_lot_count() {
    _deallocate_block_a_lot_count++;
  }
  static int deallocate_chunk_a_lot_count() {
    return _deallocate_chunk_a_lot_count;
  }
  static void reset_deallocate_chunk_a_lot_count() {
    _deallocate_chunk_a_lot_count = 1;
  }
  static void inc_deallocate_chunk_a_lot_count() {
    _deallocate_chunk_a_lot_count++;
  }

  static void init_allocation_fail_alot_count();
#ifdef ASSERT
  static bool test_metadata_failure();
#endif

  static void deallocate_chunk_a_lot(SpaceManager* sm,
                                     size_t chunk_word_size);
  static void deallocate_block_a_lot(SpaceManager* sm,
                                     size_t chunk_word_size);

};

int Metadebug::_deallocate_block_a_lot_count = 0;
int Metadebug::_deallocate_chunk_a_lot_count = 0;
int Metadebug::_allocation_fail_alot_count = 0;

//  SpaceManager - used by Metaspace to handle allocations
class SpaceManager : public CHeapObj<mtClass> {
  friend class Metaspace;
  friend class Metadebug;

 private:

  // protects allocations and contains.
  Mutex* const _lock;

  // Chunk related size
  size_t _medium_chunk_bunch;

  // List of chunks in use by this SpaceManager.  Allocations
  // are done from the current chunk.  The list is used for deallocating
  // chunks when the SpaceManager is freed.
  Metachunk* _chunks_in_use[NumberOfInUseLists];
  Metachunk* _current_chunk;

  // Virtual space where allocation comes from.
  VirtualSpaceList* _vs_list;

  // Number of small chunks to allocate to a manager
  // If class space manager, small chunks are unlimited
  static uint const _small_chunk_limit;
  bool has_small_chunk_limit() { return !vs_list()->is_class(); }

  // Sum of all space in allocated chunks
  size_t _allocation_total;

  // Free lists of blocks are per SpaceManager since they
  // are assumed to be in chunks in use by the SpaceManager
  // and all chunks in use by a SpaceManager are freed when
  // the class loader using the SpaceManager is collected.
  BlockFreelist _block_freelists;

  // protects virtualspace and chunk expansions
  static const char*  _expand_lock_name;
  static const int    _expand_lock_rank;
  static Mutex* const _expand_lock;

 private:
  // Accessors
  Metachunk* chunks_in_use(ChunkIndex index) const { return _chunks_in_use[index]; }
  void set_chunks_in_use(ChunkIndex index, Metachunk* v) { _chunks_in_use[index] = v; }

  BlockFreelist* block_freelists() const {
    return (BlockFreelist*) &_block_freelists;
  }

  VirtualSpaceList* vs_list() const    { return _vs_list; }

  Metachunk* current_chunk() const { return _current_chunk; }
  void set_current_chunk(Metachunk* v) {
    _current_chunk = v;
  }

  Metachunk* find_current_chunk(size_t word_size);

  // Add chunk to the list of chunks in use
  void add_chunk(Metachunk* v, bool make_current);

  Mutex* lock() const { return _lock; }

  const char* chunk_size_name(ChunkIndex index) const;

 protected:
  void initialize();

 public:
  SpaceManager(Mutex* lock,
               VirtualSpaceList* vs_list);
  ~SpaceManager();

  enum ChunkMultiples {
    MediumChunkMultiple = 4
  };

  // Accessors
  size_t specialized_chunk_size() { return SpecializedChunk; }
  size_t small_chunk_size() { return (size_t) vs_list()->is_class() ? ClassSmallChunk : SmallChunk; }
  size_t medium_chunk_size() { return (size_t) vs_list()->is_class() ? ClassMediumChunk : MediumChunk; }
  size_t medium_chunk_bunch() { return medium_chunk_size() * MediumChunkMultiple; }

  size_t allocation_total() const { return _allocation_total; }
  void inc_allocation_total(size_t v) { Atomic::add_ptr(v, &_allocation_total); }
  bool is_humongous(size_t word_size) { return word_size > medium_chunk_size(); }

  static Mutex* expand_lock() { return _expand_lock; }

  // Set the sizes for the initial chunks.
  void get_initial_chunk_sizes(Metaspace::MetaspaceType type,
                               size_t* chunk_word_size,
                               size_t* class_chunk_word_size);

  size_t sum_capacity_in_chunks_in_use() const;
  size_t sum_used_in_chunks_in_use() const;
  size_t sum_free_in_chunks_in_use() const;
  size_t sum_waste_in_chunks_in_use() const;
  size_t sum_waste_in_chunks_in_use(ChunkIndex index ) const;

  size_t sum_count_in_chunks_in_use();
  size_t sum_count_in_chunks_in_use(ChunkIndex i);

  Metachunk* get_new_chunk(size_t word_size, size_t grow_chunks_by_words);

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

  // debugging support.

  void dump(outputStream* const out) const;
  void print_on(outputStream* st) const;
  void locked_print_chunks_in_use_on(outputStream* st) const;

  void verify();
  void verify_chunk_size(Metachunk* chunk);
  NOT_PRODUCT(void mangle_freed_chunks();)
#ifdef ASSERT
  void verify_allocation_total();
#endif
};

uint const SpaceManager::_small_chunk_limit = 4;

const char* SpaceManager::_expand_lock_name =
  "SpaceManager chunk allocation lock";
const int SpaceManager::_expand_lock_rank = Monitor::leaf - 1;
Mutex* const SpaceManager::_expand_lock =
  new Mutex(SpaceManager::_expand_lock_rank,
            SpaceManager::_expand_lock_name,
            Mutex::_allow_vm_block_flag);

// BlockFreelist methods

BlockFreelist::BlockFreelist() : _dictionary(NULL) {}

BlockFreelist::~BlockFreelist() {
  if (_dictionary != NULL) {
    if (Verbose && TraceMetadataChunkAllocation) {
      _dictionary->print_free_lists(gclog_or_tty);
    }
    delete _dictionary;
  }
}

Metablock* BlockFreelist::initialize_free_chunk(MetaWord* p, size_t word_size) {
  Metablock* block = (Metablock*) p;
  block->set_word_size(word_size);
  block->set_prev(NULL);
  block->set_next(NULL);

  return block;
}

void BlockFreelist::return_block(MetaWord* p, size_t word_size) {
  Metablock* free_chunk = initialize_free_chunk(p, word_size);
  if (dictionary() == NULL) {
   _dictionary = new BlockTreeDictionary();
  }
  dictionary()->return_chunk(free_chunk);
}

MetaWord* BlockFreelist::get_block(size_t word_size) {
  if (dictionary() == NULL) {
    return NULL;
  }

  if (word_size < TreeChunk<Metablock, FreeList>::min_size()) {
    // Dark matter.  Too small for dictionary.
    return NULL;
  }

  Metablock* free_block =
    dictionary()->get_chunk(word_size, FreeBlockDictionary<Metablock>::exactly);
  if (free_block == NULL) {
    return NULL;
  }

  return (MetaWord*) free_block;
}

void BlockFreelist::print_on(outputStream* st) const {
  if (dictionary() == NULL) {
    return;
  }
  dictionary()->print_free_lists(st);
}

// VirtualSpaceNode methods

VirtualSpaceNode::~VirtualSpaceNode() {
  _rs.release();
}

size_t VirtualSpaceNode::used_words_in_vs() const {
  return pointer_delta(top(), bottom(), sizeof(MetaWord));
}

// Space committed in the VirtualSpace
size_t VirtualSpaceNode::capacity_words_in_vs() const {
  return pointer_delta(end(), bottom(), sizeof(MetaWord));
}


// Allocates the chunk from the virtual space only.
// This interface is also used internally for debugging.  Not all
// chunks removed here are necessarily used for allocation.
Metachunk* VirtualSpaceNode::take_from_committed(size_t chunk_word_size) {
  // Bottom of the new chunk
  MetaWord* chunk_limit = top();
  assert(chunk_limit != NULL, "Not safe to call this method");

  if (!is_available(chunk_word_size)) {
    if (TraceMetadataChunkAllocation) {
      tty->print("VirtualSpaceNode::take_from_committed() not available %d words ", chunk_word_size);
      // Dump some information about the virtual space that is nearly full
      print_on(tty);
    }
    return NULL;
  }

  // Take the space  (bump top on the current virtual space).
  inc_top(chunk_word_size);

  // Point the chunk at the space
  Metachunk* result = Metachunk::initialize(chunk_limit, chunk_word_size);
  return result;
}


// Expand the virtual space (commit more of the reserved space)
bool VirtualSpaceNode::expand_by(size_t words, bool pre_touch) {
  size_t bytes = words * BytesPerWord;
  bool result =  virtual_space()->expand_by(bytes, pre_touch);
  if (TraceMetavirtualspaceAllocation && !result) {
    gclog_or_tty->print_cr("VirtualSpaceNode::expand_by() failed "
                           "for byte size " SIZE_FORMAT, bytes);
    virtual_space()->print();
  }
  return result;
}

// Shrink the virtual space (commit more of the reserved space)
bool VirtualSpaceNode::shrink_by(size_t words) {
  size_t bytes = words * BytesPerWord;
  virtual_space()->shrink_by(bytes);
  return true;
}

// Add another chunk to the chunk list.

Metachunk* VirtualSpaceNode::get_chunk_vs(size_t chunk_word_size) {
  assert_lock_strong(SpaceManager::expand_lock());
  Metachunk* result = NULL;

  return take_from_committed(chunk_word_size);
}

Metachunk* VirtualSpaceNode::get_chunk_vs_with_expand(size_t chunk_word_size) {
  assert_lock_strong(SpaceManager::expand_lock());

  Metachunk* new_chunk = get_chunk_vs(chunk_word_size);

  if (new_chunk == NULL) {
    // Only a small part of the virtualspace is committed when first
    // allocated so committing more here can be expected.
    size_t page_size_words = os::vm_page_size() / BytesPerWord;
    size_t aligned_expand_vs_by_words = align_size_up(chunk_word_size,
                                                    page_size_words);
    expand_by(aligned_expand_vs_by_words, false);
    new_chunk = get_chunk_vs(chunk_word_size);
  }
  return new_chunk;
}

bool VirtualSpaceNode::initialize() {

  if (!_rs.is_reserved()) {
    return false;
  }

  // An allocation out of this Virtualspace that is larger
  // than an initial commit size can waste that initial committed
  // space.
  size_t committed_byte_size = 0;
  bool result = virtual_space()->initialize(_rs, committed_byte_size);
  if (result) {
    set_top((MetaWord*)virtual_space()->low());
    set_reserved(MemRegion((HeapWord*)_rs.base(),
                 (HeapWord*)(_rs.base() + _rs.size())));

    assert(reserved()->start() == (HeapWord*) _rs.base(),
      err_msg("Reserved start was not set properly " PTR_FORMAT
        " != " PTR_FORMAT, reserved()->start(), _rs.base()));
    assert(reserved()->word_size() == _rs.size() / BytesPerWord,
      err_msg("Reserved size was not set properly " SIZE_FORMAT
        " != " SIZE_FORMAT, reserved()->word_size(),
        _rs.size() / BytesPerWord));
  }

  return result;
}

void VirtualSpaceNode::print_on(outputStream* st) const {
  size_t used = used_words_in_vs();
  size_t capacity = capacity_words_in_vs();
  VirtualSpace* vs = virtual_space();
  st->print_cr("   space @ " PTR_FORMAT " " SIZE_FORMAT "K, %3d%% used "
           "[" PTR_FORMAT ", " PTR_FORMAT ", "
           PTR_FORMAT ", " PTR_FORMAT ")",
           vs, capacity / K,
           capacity == 0 ? 0 : used * 100 / capacity,
           bottom(), top(), end(),
           vs->high_boundary());
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

size_t VirtualSpaceList::used_words_sum() {
  size_t allocated_by_vs = 0;
  VirtualSpaceListIterator iter(virtual_space_list());
  while (iter.repeat()) {
    VirtualSpaceNode* vsl = iter.get_next();
    // Sum used region [bottom, top) in each virtualspace
    allocated_by_vs += vsl->used_words_in_vs();
  }
  assert(allocated_by_vs >= chunk_manager()->free_chunks_total(),
    err_msg("Total in free chunks " SIZE_FORMAT
            " greater than total from virtual_spaces " SIZE_FORMAT,
            allocated_by_vs, chunk_manager()->free_chunks_total()));
  size_t used =
    allocated_by_vs - chunk_manager()->free_chunks_total();
  return used;
}

// Space available in all MetadataVirtualspaces allocated
// for metadata.  This is the upper limit on the capacity
// of chunks allocated out of all the MetadataVirtualspaces.
size_t VirtualSpaceList::capacity_words_sum() {
  size_t capacity = 0;
  VirtualSpaceListIterator iter(virtual_space_list());
  while (iter.repeat()) {
    VirtualSpaceNode* vsl = iter.get_next();
    capacity += vsl->capacity_words_in_vs();
  }
  return capacity;
}

VirtualSpaceList::VirtualSpaceList(size_t word_size ) :
                                   _is_class(false),
                                   _virtual_space_list(NULL),
                                   _current_virtual_space(NULL),
                                   _virtual_space_total(0),
                                   _virtual_space_count(0) {
  MutexLockerEx cl(SpaceManager::expand_lock(),
                   Mutex::_no_safepoint_check_flag);
  bool initialization_succeeded = grow_vs(word_size);

  assert(initialization_succeeded,
    " VirtualSpaceList initialization should not fail");
}

VirtualSpaceList::VirtualSpaceList(ReservedSpace rs) :
                                   _is_class(true),
                                   _virtual_space_list(NULL),
                                   _current_virtual_space(NULL),
                                   _virtual_space_total(0),
                                   _virtual_space_count(0) {
  MutexLockerEx cl(SpaceManager::expand_lock(),
                   Mutex::_no_safepoint_check_flag);
  VirtualSpaceNode* class_entry = new VirtualSpaceNode(rs);
  bool succeeded = class_entry->initialize();
  assert(succeeded, " VirtualSpaceList initialization should not fail");
  link_vs(class_entry, rs.size()/BytesPerWord);
}

// Allocate another meta virtual space and add it to the list.
bool VirtualSpaceList::grow_vs(size_t vs_word_size) {
  assert_lock_strong(SpaceManager::expand_lock());
  if (vs_word_size == 0) {
    return false;
  }
  // Reserve the space
  size_t vs_byte_size = vs_word_size * BytesPerWord;
  assert(vs_byte_size % os::vm_page_size() == 0, "Not aligned");

  // Allocate the meta virtual space and initialize it.
  VirtualSpaceNode* new_entry = new VirtualSpaceNode(vs_byte_size);
  if (!new_entry->initialize()) {
    delete new_entry;
    return false;
  } else {
    // ensure lock-free iteration sees fully initialized node
    OrderAccess::storestore();
    link_vs(new_entry, vs_word_size);
    return true;
  }
}

void VirtualSpaceList::link_vs(VirtualSpaceNode* new_entry, size_t vs_word_size) {
  if (virtual_space_list() == NULL) {
      set_virtual_space_list(new_entry);
  } else {
    current_virtual_space()->set_next(new_entry);
  }
  set_current_virtual_space(new_entry);
  inc_virtual_space_total(vs_word_size);
  inc_virtual_space_count();
#ifdef ASSERT
  new_entry->mangle();
#endif
  if (TraceMetavirtualspaceAllocation && Verbose) {
    VirtualSpaceNode* vsl = current_virtual_space();
    vsl->print_on(tty);
  }
}

Metachunk* VirtualSpaceList::get_new_chunk(size_t word_size,
                                           size_t grow_chunks_by_words,
                                           size_t medium_chunk_bunch) {

  // Get a chunk from the chunk freelist
  Metachunk* next = chunk_manager()->chunk_freelist_allocate(grow_chunks_by_words);

  // Allocate a chunk out of the current virtual space.
  if (next == NULL) {
    next = current_virtual_space()->get_chunk_vs(grow_chunks_by_words);
  }

  if (next == NULL) {
    // Not enough room in current virtual space.  Try to commit
    // more space.
    size_t expand_vs_by_words = MAX2(medium_chunk_bunch,
                                     grow_chunks_by_words);
    size_t page_size_words = os::vm_page_size() / BytesPerWord;
    size_t aligned_expand_vs_by_words = align_size_up(expand_vs_by_words,
                                                        page_size_words);
    bool vs_expanded =
      current_virtual_space()->expand_by(aligned_expand_vs_by_words, false);
    if (!vs_expanded) {
      // Should the capacity of the metaspaces be expanded for
      // this allocation?  If it's the virtual space for classes and is
      // being used for CompressedHeaders, don't allocate a new virtualspace.
      if (can_grow() && MetaspaceGC::should_expand(this, word_size)) {
        // Get another virtual space.
          size_t grow_vs_words =
            MAX2((size_t)VirtualSpaceSize, aligned_expand_vs_by_words);
        if (grow_vs(grow_vs_words)) {
          // Got it.  It's on the list now.  Get a chunk from it.
          next = current_virtual_space()->get_chunk_vs_with_expand(grow_chunks_by_words);
        }
      } else {
        // Allocation will fail and induce a GC
        if (TraceMetadataChunkAllocation && Verbose) {
          gclog_or_tty->print_cr("VirtualSpaceList::get_new_chunk():"
            " Fail instead of expand the metaspace");
        }
      }
    } else {
      // The virtual space expanded, get a new chunk
      next = current_virtual_space()->get_chunk_vs(grow_chunks_by_words);
      assert(next != NULL, "Just expanded, should succeed");
    }
  }

  assert(next == NULL || (next->next() == NULL && next->prev() == NULL),
         "New chunk is still on some list");
  return next;
}

Metachunk* VirtualSpaceList::get_initialization_chunk(size_t chunk_word_size,
                                                      size_t chunk_bunch) {
  // Get a chunk from the chunk freelist
  Metachunk* new_chunk = get_new_chunk(chunk_word_size,
                                       chunk_word_size,
                                       chunk_bunch);
  return new_chunk;
}

void VirtualSpaceList::print_on(outputStream* st) const {
  if (TraceMetadataChunkAllocation && Verbose) {
    VirtualSpaceListIterator iter(virtual_space_list());
    while (iter.repeat()) {
      VirtualSpaceNode* node = iter.get_next();
      node->print_on(st);
    }
  }
}

bool VirtualSpaceList::contains(const void *ptr) {
  VirtualSpaceNode* list = virtual_space_list();
  VirtualSpaceListIterator iter(list);
  while (iter.repeat()) {
    VirtualSpaceNode* node = iter.get_next();
    if (node->reserved()->contains(ptr)) {
      return true;
    }
  }
  return false;
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
// is based on the flags MinMetaspaceFreeRatio and MaxHeapFreeRatio used
// to resize the Java heap by some GC's.  New flags can be implemented
// if really needed.  MinHeapFreeRatio is used to calculate how much
// free space is desirable in the metaspace capacity to decide how much
// to increase the HWM.  MaxMetaspaceFreeRatio is used to decide how much
// free space is desirable in the metaspace capacity before decreasing
// the HWM.

// Calculate the amount to increase the high water mark (HWM).
// Increase by a minimum amount (MinMetaspaceExpansion) so that
// another expansion is not requested too soon.  If that is not
// enough to satisfy the allocation (i.e. big enough for a word_size
// allocation), increase by MaxMetaspaceExpansion.  If that is still
// not enough, expand by the size of the allocation (word_size) plus
// some.
size_t MetaspaceGC::delta_capacity_until_GC(size_t word_size) {
  size_t before_inc = MetaspaceGC::capacity_until_GC();
  size_t min_delta_words = MinMetaspaceExpansion / BytesPerWord;
  size_t max_delta_words = MaxMetaspaceExpansion / BytesPerWord;
  size_t page_size_words = os::vm_page_size() / BytesPerWord;
  size_t size_delta_words = align_size_up(word_size, page_size_words);
  size_t delta_words = MAX2(size_delta_words, min_delta_words);
  if (delta_words > min_delta_words) {
    // Don't want to hit the high water mark on the next
    // allocation so make the delta greater than just enough
    // for this allocation.
    delta_words = MAX2(delta_words, max_delta_words);
    if (delta_words > max_delta_words) {
      // This allocation is large but the next ones are probably not
      // so increase by the minimum.
      delta_words = delta_words + min_delta_words;
    }
  }
  return delta_words;
}

bool MetaspaceGC::should_expand(VirtualSpaceList* vsl, size_t word_size) {

  // Class virtual space should always be expanded.  Call GC for the other
  // metadata virtual space.
  if (vsl == Metaspace::class_space_list()) return true;

  // If the user wants a limit, impose one.
  size_t max_metaspace_size_words = MaxMetaspaceSize / BytesPerWord;
  size_t metaspace_size_words = MetaspaceSize / BytesPerWord;
  if (!FLAG_IS_DEFAULT(MaxMetaspaceSize) &&
      vsl->capacity_words_sum() >= max_metaspace_size_words) {
    return false;
  }

  // If this is part of an allocation after a GC, expand
  // unconditionally.
  if(MetaspaceGC::expand_after_GC()) {
    return true;
  }

  // If the capacity is below the minimum capacity, allow the
  // expansion.  Also set the high-water-mark (capacity_until_GC)
  // to that minimum capacity so that a GC will not be induced
  // until that minimum capacity is exceeded.
  if (vsl->capacity_words_sum() < metaspace_size_words ||
      capacity_until_GC() == 0) {
    set_capacity_until_GC(metaspace_size_words);
    return true;
  } else {
    if (vsl->capacity_words_sum() < capacity_until_GC()) {
      return true;
    } else {
      if (TraceMetadataChunkAllocation && Verbose) {
        gclog_or_tty->print_cr("  allocation request size " SIZE_FORMAT
                        "  capacity_until_GC " SIZE_FORMAT
                        "  capacity_words_sum " SIZE_FORMAT
                        "  used_words_sum " SIZE_FORMAT
                        "  free chunks " SIZE_FORMAT
                        "  free chunks count %d",
                        word_size,
                        capacity_until_GC(),
                        vsl->capacity_words_sum(),
                        vsl->used_words_sum(),
                        vsl->chunk_manager()->free_chunks_total(),
                        vsl->chunk_manager()->free_chunks_count());
      }
      return false;
    }
  }
}

// Variables are in bytes

void MetaspaceGC::compute_new_size() {
  assert(_shrink_factor <= 100, "invalid shrink factor");
  uint current_shrink_factor = _shrink_factor;
  _shrink_factor = 0;

  VirtualSpaceList *vsl = Metaspace::space_list();

  size_t capacity_after_gc = vsl->capacity_bytes_sum();
  // Check to see if these two can be calculated without walking the CLDG
  size_t used_after_gc = vsl->used_bytes_sum();
  size_t capacity_until_GC = vsl->capacity_bytes_sum();
  size_t free_after_gc = capacity_until_GC - used_after_gc;

  const double minimum_free_percentage = MinMetaspaceFreeRatio / 100.0;
  const double maximum_used_percentage = 1.0 - minimum_free_percentage;

  const double min_tmp = used_after_gc / maximum_used_percentage;
  size_t minimum_desired_capacity =
    (size_t)MIN2(min_tmp, double(max_uintx));
  // Don't shrink less than the initial generation size
  minimum_desired_capacity = MAX2(minimum_desired_capacity,
                                  MetaspaceSize);

  if (PrintGCDetails && Verbose) {
    const double free_percentage = ((double)free_after_gc) / capacity_until_GC;
    gclog_or_tty->print_cr("\nMetaspaceGC::compute_new_size: ");
    gclog_or_tty->print_cr("  "
                  "  minimum_free_percentage: %6.2f"
                  "  maximum_used_percentage: %6.2f",
                  minimum_free_percentage,
                  maximum_used_percentage);
    double d_free_after_gc = free_after_gc / (double) K;
    gclog_or_tty->print_cr("  "
                  "   free_after_gc       : %6.1fK"
                  "   used_after_gc       : %6.1fK"
                  "   capacity_after_gc   : %6.1fK"
                  "   metaspace HWM     : %6.1fK",
                  free_after_gc / (double) K,
                  used_after_gc / (double) K,
                  capacity_after_gc / (double) K,
                  capacity_until_GC / (double) K);
    gclog_or_tty->print_cr("  "
                  "   free_percentage: %6.2f",
                  free_percentage);
  }


  if (capacity_until_GC < minimum_desired_capacity) {
    // If we have less capacity below the metaspace HWM, then
    // increment the HWM.
    size_t expand_bytes = minimum_desired_capacity - capacity_until_GC;
    // Don't expand unless it's significant
    if (expand_bytes >= MinMetaspaceExpansion) {
      size_t expand_words = expand_bytes / BytesPerWord;
      MetaspaceGC::inc_capacity_until_GC(expand_words);
    }
    if (PrintGCDetails && Verbose) {
      size_t new_capacity_until_GC = MetaspaceGC::capacity_until_GC_in_bytes();
      gclog_or_tty->print_cr("    expanding:"
                    "  minimum_desired_capacity: %6.1fK"
                    "  expand_words: %6.1fK"
                    "  MinMetaspaceExpansion: %6.1fK"
                    "  new metaspace HWM:  %6.1fK",
                    minimum_desired_capacity / (double) K,
                    expand_bytes / (double) K,
                    MinMetaspaceExpansion / (double) K,
                    new_capacity_until_GC / (double) K);
    }
    return;
  }

  // No expansion, now see if we want to shrink
  size_t shrink_words = 0;
  // We would never want to shrink more than this
  size_t max_shrink_words = capacity_until_GC - minimum_desired_capacity;
  assert(max_shrink_words >= 0, err_msg("max_shrink_words " SIZE_FORMAT,
    max_shrink_words));

  // Should shrinking be considered?
  if (MaxMetaspaceFreeRatio < 100) {
    const double maximum_free_percentage = MaxMetaspaceFreeRatio / 100.0;
    const double minimum_used_percentage = 1.0 - maximum_free_percentage;
    const double max_tmp = used_after_gc / minimum_used_percentage;
    size_t maximum_desired_capacity = (size_t)MIN2(max_tmp, double(max_uintx));
    maximum_desired_capacity = MAX2(maximum_desired_capacity,
                                    MetaspaceSize);
    if (PrintGC && Verbose) {
      gclog_or_tty->print_cr("  "
                             "  maximum_free_percentage: %6.2f"
                             "  minimum_used_percentage: %6.2f",
                             maximum_free_percentage,
                             minimum_used_percentage);
      gclog_or_tty->print_cr("  "
                             "  capacity_until_GC: %6.1fK"
                             "  minimum_desired_capacity: %6.1fK"
                             "  maximum_desired_capacity: %6.1fK",
                             capacity_until_GC / (double) K,
                             minimum_desired_capacity / (double) K,
                             maximum_desired_capacity / (double) K);
    }

    assert(minimum_desired_capacity <= maximum_desired_capacity,
           "sanity check");

    if (capacity_until_GC > maximum_desired_capacity) {
      // Capacity too large, compute shrinking size
      shrink_words = capacity_until_GC - maximum_desired_capacity;
      // We don't want shrink all the way back to initSize if people call
      // System.gc(), because some programs do that between "phases" and then
      // we'd just have to grow the heap up again for the next phase.  So we
      // damp the shrinking: 0% on the first call, 10% on the second call, 40%
      // on the third call, and 100% by the fourth call.  But if we recompute
      // size without shrinking, it goes back to 0%.
      shrink_words = shrink_words / 100 * current_shrink_factor;
      assert(shrink_words <= max_shrink_words,
        err_msg("invalid shrink size " SIZE_FORMAT " not <= " SIZE_FORMAT,
          shrink_words, max_shrink_words));
      if (current_shrink_factor == 0) {
        _shrink_factor = 10;
      } else {
        _shrink_factor = MIN2(current_shrink_factor * 4, (uint) 100);
      }
      if (PrintGCDetails && Verbose) {
        gclog_or_tty->print_cr("  "
                      "  shrinking:"
                      "  initSize: %.1fK"
                      "  maximum_desired_capacity: %.1fK",
                      MetaspaceSize / (double) K,
                      maximum_desired_capacity / (double) K);
        gclog_or_tty->print_cr("  "
                      "  shrink_words: %.1fK"
                      "  current_shrink_factor: %d"
                      "  new shrink factor: %d"
                      "  MinMetaspaceExpansion: %.1fK",
                      shrink_words / (double) K,
                      current_shrink_factor,
                      _shrink_factor,
                      MinMetaspaceExpansion / (double) K);
      }
    }
  }


  // Don't shrink unless it's significant
  if (shrink_words >= MinMetaspaceExpansion) {
    VirtualSpaceNode* csp = vsl->current_virtual_space();
    size_t available_to_shrink = csp->capacity_words_in_vs() -
      csp->used_words_in_vs();
    shrink_words = MIN2(shrink_words, available_to_shrink);
    csp->shrink_by(shrink_words);
    MetaspaceGC::dec_capacity_until_GC(shrink_words);
    if (PrintGCDetails && Verbose) {
      size_t new_capacity_until_GC = MetaspaceGC::capacity_until_GC_in_bytes();
      gclog_or_tty->print_cr("  metaspace HWM: %.1fK", new_capacity_until_GC / (double) K);
    }
  }
  assert(vsl->used_bytes_sum() == used_after_gc &&
         used_after_gc <= vsl->capacity_bytes_sum(),
         "sanity check");

}

// Metadebug methods

void Metadebug::deallocate_chunk_a_lot(SpaceManager* sm,
                                       size_t chunk_word_size){
#ifdef ASSERT
  VirtualSpaceList* vsl = sm->vs_list();
  if (MetaDataDeallocateALot &&
      Metadebug::deallocate_chunk_a_lot_count() % MetaDataDeallocateALotInterval == 0 ) {
    Metadebug::reset_deallocate_chunk_a_lot_count();
    for (uint i = 0; i < metadata_deallocate_a_lock_chunk; i++) {
      Metachunk* dummy_chunk = vsl->current_virtual_space()->take_from_committed(chunk_word_size);
      if (dummy_chunk == NULL) {
        break;
      }
      vsl->chunk_manager()->chunk_freelist_deallocate(dummy_chunk);

      if (TraceMetadataChunkAllocation && Verbose) {
        gclog_or_tty->print("Metadebug::deallocate_chunk_a_lot: %d) ",
                               sm->sum_count_in_chunks_in_use());
        dummy_chunk->print_on(gclog_or_tty);
        gclog_or_tty->print_cr("  Free chunks total %d  count %d",
                               vsl->chunk_manager()->free_chunks_total(),
                               vsl->chunk_manager()->free_chunks_count());
      }
    }
  } else {
    Metadebug::inc_deallocate_chunk_a_lot_count();
  }
#endif
}

void Metadebug::deallocate_block_a_lot(SpaceManager* sm,
                                       size_t raw_word_size){
#ifdef ASSERT
  if (MetaDataDeallocateALot &&
        Metadebug::deallocate_block_a_lot_count() % MetaDataDeallocateALotInterval == 0 ) {
    Metadebug::set_deallocate_block_a_lot_count(0);
    for (uint i = 0; i < metadata_deallocate_a_lot_block; i++) {
      MetaWord* dummy_block = sm->allocate_work(raw_word_size);
      if (dummy_block == 0) {
        break;
      }
      sm->deallocate(dummy_block, raw_word_size);
    }
  } else {
    Metadebug::inc_deallocate_block_a_lot_count();
  }
#endif
}

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
      if (TraceMetadataChunkAllocation && Verbose) {
        gclog_or_tty->print_cr("Metadata allocation failing for "
                               "MetadataAllocationFailALot");
      }
      init_allocation_fail_alot_count();
      return true;
    }
  }
  return false;
}
#endif

// ChunkList methods

size_t ChunkList::sum_list_size() {
  size_t result = 0;
  Metachunk* cur = head();
  while (cur != NULL) {
    result += cur->word_size();
    cur = cur->next();
  }
  return result;
}

size_t ChunkList::sum_list_count() {
  size_t result = 0;
  Metachunk* cur = head();
  while (cur != NULL) {
    result++;
    cur = cur->next();
  }
  return result;
}

size_t ChunkList::sum_list_capacity() {
  size_t result = 0;
  Metachunk* cur = head();
  while (cur != NULL) {
    result += cur->capacity_word_size();
    cur = cur->next();
  }
  return result;
}

void ChunkList::add_at_head(Metachunk* head, Metachunk* tail) {
  assert_lock_strong(SpaceManager::expand_lock());
  assert(head == tail || tail->next() == NULL,
         "Not the tail or the head has already been added to a list");

  if (TraceMetadataChunkAllocation && Verbose) {
    gclog_or_tty->print("ChunkList::add_at_head(head, tail): ");
    Metachunk* cur = head;
    while (cur != NULL) {
      gclog_or_tty->print(PTR_FORMAT " (" SIZE_FORMAT ") ", cur, cur->word_size());
      cur = cur->next();
    }
    gclog_or_tty->print_cr("");
  }

  if (tail != NULL) {
    tail->set_next(_head);
  }
  set_head(head);
}

void ChunkList::add_at_head(Metachunk* list) {
  if (list == NULL) {
    // Nothing to add
    return;
  }
  assert_lock_strong(SpaceManager::expand_lock());
  Metachunk* head = list;
  Metachunk* tail = list;
  Metachunk* cur = head->next();
  // Search for the tail since it is not passed.
  while (cur != NULL) {
    tail = cur;
    cur = cur->next();
  }
  add_at_head(head, tail);
}

// ChunkManager methods

// Verification of _free_chunks_total and _free_chunks_count does not
// work with the CMS collector because its use of additional locks
// complicate the mutex deadlock detection but it can still be useful
// for detecting errors in the chunk accounting with other collectors.

size_t ChunkManager::free_chunks_total() {
#ifdef ASSERT
  if (!UseConcMarkSweepGC && !SpaceManager::expand_lock()->is_locked()) {
    MutexLockerEx cl(SpaceManager::expand_lock(),
                     Mutex::_no_safepoint_check_flag);
    slow_locked_verify_free_chunks_total();
  }
#endif
  return _free_chunks_total;
}

size_t ChunkManager::free_chunks_total_in_bytes() {
  return free_chunks_total() * BytesPerWord;
}

size_t ChunkManager::free_chunks_count() {
#ifdef ASSERT
  if (!UseConcMarkSweepGC && !SpaceManager::expand_lock()->is_locked()) {
    MutexLockerEx cl(SpaceManager::expand_lock(),
                     Mutex::_no_safepoint_check_flag);
    // This lock is only needed in debug because the verification
    // of the _free_chunks_totals walks the list of free chunks
    slow_locked_verify_free_chunks_count();
  }
#endif
  return _free_chunks_count;
}

void ChunkManager::locked_verify_free_chunks_total() {
  assert_lock_strong(SpaceManager::expand_lock());
  assert(sum_free_chunks() == _free_chunks_total,
    err_msg("_free_chunks_total " SIZE_FORMAT " is not the"
           " same as sum " SIZE_FORMAT, _free_chunks_total,
           sum_free_chunks()));
}

void ChunkManager::verify_free_chunks_total() {
  MutexLockerEx cl(SpaceManager::expand_lock(),
                     Mutex::_no_safepoint_check_flag);
  locked_verify_free_chunks_total();
}

void ChunkManager::locked_verify_free_chunks_count() {
  assert_lock_strong(SpaceManager::expand_lock());
  assert(sum_free_chunks_count() == _free_chunks_count,
    err_msg("_free_chunks_count " SIZE_FORMAT " is not the"
           " same as sum " SIZE_FORMAT, _free_chunks_count,
           sum_free_chunks_count()));
}

void ChunkManager::verify_free_chunks_count() {
#ifdef ASSERT
  MutexLockerEx cl(SpaceManager::expand_lock(),
                     Mutex::_no_safepoint_check_flag);
  locked_verify_free_chunks_count();
#endif
}

void ChunkManager::verify() {
  MutexLockerEx cl(SpaceManager::expand_lock(),
                     Mutex::_no_safepoint_check_flag);
  locked_verify();
}

void ChunkManager::locked_verify() {
  locked_verify_free_chunks_count();
  locked_verify_free_chunks_total();
}

void ChunkManager::locked_print_free_chunks(outputStream* st) {
  assert_lock_strong(SpaceManager::expand_lock());
  st->print_cr("Free chunk total " SIZE_FORMAT "  count " SIZE_FORMAT,
                _free_chunks_total, _free_chunks_count);
}

void ChunkManager::locked_print_sum_free_chunks(outputStream* st) {
  assert_lock_strong(SpaceManager::expand_lock());
  st->print_cr("Sum free chunk total " SIZE_FORMAT "  count " SIZE_FORMAT,
                sum_free_chunks(), sum_free_chunks_count());
}
ChunkList* ChunkManager::free_chunks(ChunkIndex index) {
  return &_free_chunks[index];
}

// These methods that sum the free chunk lists are used in printing
// methods that are used in product builds.
size_t ChunkManager::sum_free_chunks() {
  assert_lock_strong(SpaceManager::expand_lock());
  size_t result = 0;
  for (ChunkIndex i = ZeroIndex; i < NumberOfFreeLists; i = next_chunk_index(i)) {
    ChunkList* list = free_chunks(i);

    if (list == NULL) {
      continue;
    }

    result = result + list->sum_list_capacity();
  }
  result = result + humongous_dictionary()->total_size();
  return result;
}

size_t ChunkManager::sum_free_chunks_count() {
  assert_lock_strong(SpaceManager::expand_lock());
  size_t count = 0;
  for (ChunkIndex i = ZeroIndex; i < NumberOfFreeLists; i = next_chunk_index(i)) {
    ChunkList* list = free_chunks(i);
    if (list == NULL) {
      continue;
    }
    count = count + list->sum_list_count();
  }
  count = count + humongous_dictionary()->total_free_blocks();
  return count;
}

ChunkList* ChunkManager::find_free_chunks_list(size_t word_size) {
  ChunkIndex index = list_index(word_size);
  assert(index < HumongousIndex, "No humongous list");
  return free_chunks(index);
}

void ChunkManager::free_chunks_put(Metachunk* chunk) {
  assert_lock_strong(SpaceManager::expand_lock());
  ChunkList* free_list = find_free_chunks_list(chunk->word_size());
  chunk->set_next(free_list->head());
  free_list->set_head(chunk);
  // chunk is being returned to the chunk free list
  inc_free_chunks_total(chunk->capacity_word_size());
  slow_locked_verify();
}

void ChunkManager::chunk_freelist_deallocate(Metachunk* chunk) {
  // The deallocation of a chunk originates in the freelist
  // manangement code for a Metaspace and does not hold the
  // lock.
  assert(chunk != NULL, "Deallocating NULL");
  assert_lock_strong(SpaceManager::expand_lock());
  slow_locked_verify();
  if (TraceMetadataChunkAllocation) {
    tty->print_cr("ChunkManager::chunk_freelist_deallocate: chunk "
                  PTR_FORMAT "  size " SIZE_FORMAT,
                  chunk, chunk->word_size());
  }
  free_chunks_put(chunk);
}

Metachunk* ChunkManager::free_chunks_get(size_t word_size) {
  assert_lock_strong(SpaceManager::expand_lock());

  slow_locked_verify();

  Metachunk* chunk = NULL;
  if (list_index(word_size) != HumongousIndex) {
    ChunkList* free_list = find_free_chunks_list(word_size);
    assert(free_list != NULL, "Sanity check");

    chunk = free_list->head();
    debug_only(Metachunk* debug_head = chunk;)

    if (chunk == NULL) {
      return NULL;
    }

    // Remove the chunk as the head of the list.
    free_list->set_head(chunk->next());

    // Chunk is being removed from the chunks free list.
    dec_free_chunks_total(chunk->capacity_word_size());

    if (TraceMetadataChunkAllocation && Verbose) {
      tty->print_cr("ChunkManager::free_chunks_get: free_list "
                    PTR_FORMAT " head " PTR_FORMAT " size " SIZE_FORMAT,
                    free_list, chunk, chunk->word_size());
    }
  } else {
    chunk = humongous_dictionary()->get_chunk(
      word_size,
      FreeBlockDictionary<Metachunk>::atLeast);

    if (chunk != NULL) {
      if (TraceMetadataHumongousAllocation) {
        size_t waste = chunk->word_size() - word_size;
        tty->print_cr("Free list allocate humongous chunk size " SIZE_FORMAT
                      " for requested size " SIZE_FORMAT
                      " waste " SIZE_FORMAT,
                      chunk->word_size(), word_size, waste);
      }
      // Chunk is being removed from the chunks free list.
      dec_free_chunks_total(chunk->capacity_word_size());
#ifdef ASSERT
      chunk->set_is_free(false);
#endif
    } else {
      return NULL;
    }
  }

  // Remove it from the links to this freelist
  chunk->set_next(NULL);
  chunk->set_prev(NULL);
  slow_locked_verify();
  return chunk;
}

Metachunk* ChunkManager::chunk_freelist_allocate(size_t word_size) {
  assert_lock_strong(SpaceManager::expand_lock());
  slow_locked_verify();

  // Take from the beginning of the list
  Metachunk* chunk = free_chunks_get(word_size);
  if (chunk == NULL) {
    return NULL;
  }

  assert((word_size <= chunk->word_size()) ||
         list_index(chunk->word_size() == HumongousIndex),
         "Non-humongous variable sized chunk");
  if (TraceMetadataChunkAllocation) {
    size_t list_count;
    if (list_index(word_size) < HumongousIndex) {
      ChunkList* list = find_free_chunks_list(word_size);
      list_count = list->sum_list_count();
    } else {
      list_count = humongous_dictionary()->total_count();
    }
    tty->print("ChunkManager::chunk_freelist_allocate: " PTR_FORMAT " chunk "
               PTR_FORMAT "  size " SIZE_FORMAT " count " SIZE_FORMAT " ",
               this, chunk, chunk->word_size(), list_count);
    locked_print_free_chunks(tty);
  }

  return chunk;
}

void ChunkManager::print_on(outputStream* out) {
  if (PrintFLSStatistics != 0) {
    humongous_dictionary()->report_statistics();
  }
}

// SpaceManager methods

void SpaceManager::get_initial_chunk_sizes(Metaspace::MetaspaceType type,
                                           size_t* chunk_word_size,
                                           size_t* class_chunk_word_size) {
  switch (type) {
  case Metaspace::BootMetaspaceType:
    *chunk_word_size = Metaspace::first_chunk_word_size();
    *class_chunk_word_size = Metaspace::first_class_chunk_word_size();
    break;
  case Metaspace::ROMetaspaceType:
    *chunk_word_size = SharedReadOnlySize / wordSize;
    *class_chunk_word_size = ClassSpecializedChunk;
    break;
  case Metaspace::ReadWriteMetaspaceType:
    *chunk_word_size = SharedReadWriteSize / wordSize;
    *class_chunk_word_size = ClassSpecializedChunk;
    break;
  case Metaspace::AnonymousMetaspaceType:
  case Metaspace::ReflectionMetaspaceType:
    *chunk_word_size = SpecializedChunk;
    *class_chunk_word_size = ClassSpecializedChunk;
    break;
  default:
    *chunk_word_size = SmallChunk;
    *class_chunk_word_size = ClassSmallChunk;
    break;
  }
  assert(*chunk_word_size != 0 && *class_chunk_word_size != 0,
    err_msg("Initial chunks sizes bad: data  " SIZE_FORMAT
            " class " SIZE_FORMAT,
            *chunk_word_size, *class_chunk_word_size));
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
  if (chunk != NULL) {
    Metachunk* prev = chunk;
    while (chunk != NULL && chunk != current_chunk()) {
      result += chunk->free_word_size();
      prev = chunk;
      chunk = chunk->next();
    }
  }
  return result;
}

size_t SpaceManager::sum_capacity_in_chunks_in_use() const {
  MutexLockerEx cl(lock(), Mutex::_no_safepoint_check_flag);
  size_t sum = 0;
  for (ChunkIndex i = ZeroIndex; i < NumberOfInUseLists; i = next_chunk_index(i)) {
    Metachunk* chunk = chunks_in_use(i);
    while (chunk != NULL) {
      // Just changed this sum += chunk->capacity_word_size();
      // sum += chunk->word_size() - Metachunk::overhead();
      sum += chunk->capacity_word_size();
      chunk = chunk->next();
    }
  }
  return sum;
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
                 chunk_size_name(i), chunk);
    if (chunk != NULL) {
      st->print_cr(" free " SIZE_FORMAT,
                   chunk->free_word_size());
    } else {
      st->print_cr("");
    }
  }

  vs_list()->chunk_manager()->locked_print_free_chunks(st);
  vs_list()->chunk_manager()->locked_print_sum_free_chunks(st);
}

size_t SpaceManager::calc_chunk_size(size_t word_size) {

  // Decide between a small chunk and a medium chunk.  Up to
  // _small_chunk_limit small chunks can be allocated but
  // once a medium chunk has been allocated, no more small
  // chunks will be allocated.
  size_t chunk_word_size;
  if (chunks_in_use(MediumIndex) == NULL &&
      (!has_small_chunk_limit() ||
       sum_count_in_chunks_in_use(SmallIndex) < _small_chunk_limit)) {
    chunk_word_size = (size_t) small_chunk_size();
    if (word_size + Metachunk::overhead() > small_chunk_size()) {
      chunk_word_size = medium_chunk_size();
    }
  } else {
    chunk_word_size = medium_chunk_size();
  }

  // Might still need a humongous chunk.  Enforce an
  // eight word granularity to facilitate reuse (some
  // wastage but better chance of reuse).
  size_t if_humongous_sized_chunk =
    align_size_up(word_size + Metachunk::overhead(),
                  HumongousChunkGranularity);
  chunk_word_size =
    MAX2((size_t) chunk_word_size, if_humongous_sized_chunk);

  assert(!SpaceManager::is_humongous(word_size) ||
         chunk_word_size == if_humongous_sized_chunk,
         err_msg("Size calculation is wrong, word_size " SIZE_FORMAT
                 " chunk_word_size " SIZE_FORMAT,
                 word_size, chunk_word_size));
  if (TraceMetadataHumongousAllocation &&
      SpaceManager::is_humongous(word_size)) {
    gclog_or_tty->print_cr("Metadata humongous allocation:");
    gclog_or_tty->print_cr("  word_size " PTR_FORMAT, word_size);
    gclog_or_tty->print_cr("  chunk_word_size " PTR_FORMAT,
                           chunk_word_size);
    gclog_or_tty->print_cr("    chunk overhead " PTR_FORMAT,
                           Metachunk::overhead());
  }
  return chunk_word_size;
}

MetaWord* SpaceManager::grow_and_allocate(size_t word_size) {
  assert(vs_list()->current_virtual_space() != NULL,
         "Should have been set");
  assert(current_chunk() == NULL ||
         current_chunk()->allocate(word_size) == NULL,
         "Don't need to expand");
  MutexLockerEx cl(SpaceManager::expand_lock(), Mutex::_no_safepoint_check_flag);

  if (TraceMetadataChunkAllocation && Verbose) {
    size_t words_left = 0;
    size_t words_used = 0;
    if (current_chunk() != NULL) {
      words_left = current_chunk()->free_word_size();
      words_used = current_chunk()->used_word_size();
    }
    gclog_or_tty->print_cr("SpaceManager::grow_and_allocate for " SIZE_FORMAT
                           " words " SIZE_FORMAT " words used " SIZE_FORMAT
                           " words left",
                            word_size, words_used, words_left);
  }

  // Get another chunk out of the virtual space
  size_t grow_chunks_by_words = calc_chunk_size(word_size);
  Metachunk* next = get_new_chunk(word_size, grow_chunks_by_words);

  // If a chunk was available, add it to the in-use chunk list
  // and do an allocation from it.
  if (next != NULL) {
    Metadebug::deallocate_chunk_a_lot(this, grow_chunks_by_words);
    // Add to this manager's list of chunks in use.
    add_chunk(next, false);
    return next->allocate(word_size);
  }
  return NULL;
}

void SpaceManager::print_on(outputStream* st) const {

  for (ChunkIndex i = ZeroIndex;
       i < NumberOfInUseLists ;
       i = next_chunk_index(i) ) {
    st->print_cr("  chunks_in_use " PTR_FORMAT " chunk size " PTR_FORMAT,
                 chunks_in_use(i),
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

SpaceManager::SpaceManager(Mutex* lock,
                           VirtualSpaceList* vs_list) :
  _vs_list(vs_list),
  _allocation_total(0),
  _lock(lock)
{
  initialize();
}

void SpaceManager::initialize() {
  Metadebug::init_allocation_fail_alot_count();
  for (ChunkIndex i = ZeroIndex; i < NumberOfInUseLists; i = next_chunk_index(i)) {
    _chunks_in_use[i] = NULL;
  }
  _current_chunk = NULL;
  if (TraceMetadataChunkAllocation && Verbose) {
    gclog_or_tty->print_cr("SpaceManager(): " PTR_FORMAT, this);
  }
}

SpaceManager::~SpaceManager() {
  MutexLockerEx fcl(SpaceManager::expand_lock(),
                    Mutex::_no_safepoint_check_flag);

  ChunkManager* chunk_manager = vs_list()->chunk_manager();

  chunk_manager->slow_locked_verify();

  if (TraceMetadataChunkAllocation && Verbose) {
    gclog_or_tty->print_cr("~SpaceManager(): " PTR_FORMAT, this);
    locked_print_chunks_in_use_on(gclog_or_tty);
  }

  // Mangle freed memory.
  NOT_PRODUCT(mangle_freed_chunks();)

  // Have to update before the chunks_in_use lists are emptied
  // below.
  chunk_manager->inc_free_chunks_total(sum_capacity_in_chunks_in_use(),
                                       sum_count_in_chunks_in_use());

  // Add all the chunks in use by this space manager
  // to the global list of free chunks.

  // Follow each list of chunks-in-use and add them to the
  // free lists.  Each list is NULL terminated.

  for (ChunkIndex i = ZeroIndex; i < HumongousIndex; i = next_chunk_index(i)) {
    if (TraceMetadataChunkAllocation && Verbose) {
      gclog_or_tty->print_cr("returned %d %s chunks to freelist",
                             sum_count_in_chunks_in_use(i),
                             chunk_size_name(i));
    }
    Metachunk* chunks = chunks_in_use(i);
    chunk_manager->free_chunks(i)->add_at_head(chunks);
    set_chunks_in_use(i, NULL);
    if (TraceMetadataChunkAllocation && Verbose) {
      gclog_or_tty->print_cr("updated freelist count %d %s",
                             chunk_manager->free_chunks(i)->sum_list_count(),
                             chunk_size_name(i));
    }
    assert(i != HumongousIndex, "Humongous chunks are handled explicitly later");
  }

  // The medium chunk case may be optimized by passing the head and
  // tail of the medium chunk list to add_at_head().  The tail is often
  // the current chunk but there are probably exceptions.

  // Humongous chunks
  if (TraceMetadataChunkAllocation && Verbose) {
    gclog_or_tty->print_cr("returned %d %s humongous chunks to dictionary",
                            sum_count_in_chunks_in_use(HumongousIndex),
                            chunk_size_name(HumongousIndex));
    gclog_or_tty->print("Humongous chunk dictionary: ");
  }
  // Humongous chunks are never the current chunk.
  Metachunk* humongous_chunks = chunks_in_use(HumongousIndex);

  while (humongous_chunks != NULL) {
#ifdef ASSERT
    humongous_chunks->set_is_free(true);
#endif
    if (TraceMetadataChunkAllocation && Verbose) {
      gclog_or_tty->print(PTR_FORMAT " (" SIZE_FORMAT ") ",
                          humongous_chunks,
                          humongous_chunks->word_size());
    }
    assert(humongous_chunks->word_size() == (size_t)
           align_size_up(humongous_chunks->word_size(),
                             HumongousChunkGranularity),
           err_msg("Humongous chunk size is wrong: word size " SIZE_FORMAT
                   " granularity %d",
                   humongous_chunks->word_size(), HumongousChunkGranularity));
    Metachunk* next_humongous_chunks = humongous_chunks->next();
    chunk_manager->humongous_dictionary()->return_chunk(humongous_chunks);
    humongous_chunks = next_humongous_chunks;
  }
  if (TraceMetadataChunkAllocation && Verbose) {
    gclog_or_tty->print_cr("");
    gclog_or_tty->print_cr("updated dictionary count %d %s",
                     chunk_manager->humongous_dictionary()->total_count(),
                     chunk_size_name(HumongousIndex));
  }
  set_chunks_in_use(HumongousIndex, NULL);
  chunk_manager->slow_locked_verify();
}

const char* SpaceManager::chunk_size_name(ChunkIndex index) const {
  switch (index) {
    case SpecializedIndex:
      return "Specialized";
    case SmallIndex:
      return "Small";
    case MediumIndex:
      return "Medium";
    case HumongousIndex:
      return "Humongous";
    default:
      return NULL;
  }
}

ChunkIndex ChunkManager::list_index(size_t size) {
  switch (size) {
    case SpecializedChunk:
      assert(SpecializedChunk == ClassSpecializedChunk,
             "Need branch for ClassSpecializedChunk");
      return SpecializedIndex;
    case SmallChunk:
    case ClassSmallChunk:
      return SmallIndex;
    case MediumChunk:
    case ClassMediumChunk:
      return MediumIndex;
    default:
      assert(size > MediumChunk || size > ClassMediumChunk,
             "Not a humongous chunk");
      return HumongousIndex;
  }
}

void SpaceManager::deallocate(MetaWord* p, size_t word_size) {
  assert_lock_strong(_lock);
  size_t min_size = TreeChunk<Metablock, FreeList>::min_size();
  assert(word_size >= min_size,
    err_msg("Should not deallocate dark matter " SIZE_FORMAT, word_size));
  block_freelists()->return_block(p, word_size);
}

// Adds a chunk to the list of chunks in use.
void SpaceManager::add_chunk(Metachunk* new_chunk, bool make_current) {

  assert(new_chunk != NULL, "Should not be NULL");
  assert(new_chunk->next() == NULL, "Should not be on a list");

  new_chunk->reset_empty();

  // Find the correct list and and set the current
  // chunk for that list.
  ChunkIndex index = ChunkManager::list_index(new_chunk->word_size());

  if (index != HumongousIndex) {
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

  assert(new_chunk->is_empty(), "Not ready for reuse");
  if (TraceMetadataChunkAllocation && Verbose) {
    gclog_or_tty->print("SpaceManager::add_chunk: %d) ",
                        sum_count_in_chunks_in_use());
    new_chunk->print_on(gclog_or_tty);
    vs_list()->chunk_manager()->locked_print_free_chunks(tty);
  }
}

Metachunk* SpaceManager::get_new_chunk(size_t word_size,
                                       size_t grow_chunks_by_words) {

  Metachunk* next = vs_list()->get_new_chunk(word_size,
                                             grow_chunks_by_words,
                                             medium_chunk_bunch());

  if (TraceMetadataHumongousAllocation &&
      SpaceManager::is_humongous(next->word_size())) {
    gclog_or_tty->print_cr("  new humongous chunk word size " PTR_FORMAT,
                           next->word_size());
  }

  return next;
}

MetaWord* SpaceManager::allocate(size_t word_size) {
  MutexLockerEx cl(lock(), Mutex::_no_safepoint_check_flag);

  // If only the dictionary is going to be used (i.e., no
  // indexed free list), then there is a minimum size requirement.
  // MinChunkSize is a placeholder for the real minimum size JJJ
  size_t byte_size = word_size * BytesPerWord;

  size_t byte_size_with_overhead = byte_size + Metablock::overhead();

  size_t raw_bytes_size = MAX2(byte_size_with_overhead,
                               Metablock::min_block_byte_size());
  raw_bytes_size = ARENA_ALIGN(raw_bytes_size);
  size_t raw_word_size = raw_bytes_size / BytesPerWord;
  assert(raw_word_size * BytesPerWord == raw_bytes_size, "Size problem");

  BlockFreelist* fl =  block_freelists();
  MetaWord* p = NULL;
  // Allocation from the dictionary is expensive in the sense that
  // the dictionary has to be searched for a size.  Don't allocate
  // from the dictionary until it starts to get fat.  Is this
  // a reasonable policy?  Maybe an skinny dictionary is fast enough
  // for allocations.  Do some profiling.  JJJ
  if (fl->total_size() > allocation_from_dictionary_limit) {
    p = fl->get_block(raw_word_size);
  }
  if (p == NULL) {
    p = allocate_work(raw_word_size);
  }
  Metadebug::deallocate_block_a_lot(this, raw_word_size);

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

  // For DumpSharedSpaces, only allocate out of the current chunk which is
  // never null because we gave it the size we wanted.   Caller reports out
  // of memory if this returns null.
  if (DumpSharedSpaces) {
    assert(current_chunk() != NULL, "should never happen");
    inc_allocation_total(word_size);
    return current_chunk()->allocate(word_size); // caller handles null result
  }
  if (current_chunk() != NULL) {
    result = current_chunk()->allocate(word_size);
  }

  if (result == NULL) {
    result = grow_and_allocate(word_size);
  }
  if (result > 0) {
    inc_allocation_total(word_size);
    assert(result != (MetaWord*) chunks_in_use(MediumIndex),
           "Head of the list is being allocated");
  }

  return result;
}

void SpaceManager::verify() {
  // If there are blocks in the dictionary, then
  // verfication of chunks does not work since
  // being in the dictionary alters a chunk.
  if (block_freelists()->total_size() == 0) {
    for (ChunkIndex i = ZeroIndex; i < NumberOfInUseLists; i = next_chunk_index(i)) {
      Metachunk* curr = chunks_in_use(i);
      while (curr != NULL) {
        curr->verify();
        verify_chunk_size(curr);
        curr = curr->next();
      }
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
void SpaceManager::verify_allocation_total() {
  // Verification is only guaranteed at a safepoint.
  if (SafepointSynchronize::is_at_safepoint()) {
    gclog_or_tty->print_cr("Chunk " PTR_FORMAT " allocation_total " SIZE_FORMAT
                           " sum_used_in_chunks_in_use " SIZE_FORMAT,
                           this,
                           allocation_total(),
                           sum_used_in_chunks_in_use());
  }
  MutexLockerEx cl(lock(), Mutex::_no_safepoint_check_flag);
  assert(allocation_total() == sum_used_in_chunks_in_use(),
    err_msg("allocation total is not consistent " SIZE_FORMAT
            " vs " SIZE_FORMAT,
            allocation_total(), sum_used_in_chunks_in_use()));
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
      if (TraceMetadataChunkAllocation && Verbose) {
        block_freelists()->print_on(out);
      }
      curr_total += curr->word_size();
      used += curr->used_word_size();
      capacity += curr->capacity_word_size();
      waste += curr->free_word_size() + curr->overhead();;
    }
  }

  size_t free = current_chunk() == NULL ? 0 : current_chunk()->free_word_size();
  // Free space isn't wasted.
  waste -= free;

  out->print_cr("total of all chunks "  SIZE_FORMAT " used " SIZE_FORMAT
                " free " SIZE_FORMAT " capacity " SIZE_FORMAT
                " waste " SIZE_FORMAT, curr_total, used, free, capacity, waste);
}

#ifndef PRODUCT
void SpaceManager::mangle_freed_chunks() {
  for (ChunkIndex index = ZeroIndex;
       index < NumberOfInUseLists;
       index = next_chunk_index(index)) {
    for (Metachunk* curr = chunks_in_use(index);
         curr != NULL;
         curr = curr->next()) {
      curr->mangle();
    }
  }
}
#endif // PRODUCT

// MetaspaceAux

size_t MetaspaceAux::used_in_bytes(Metaspace::MetadataType mdtype) {
  size_t used = 0;
  ClassLoaderDataGraphMetaspaceIterator iter;
  while (iter.repeat()) {
    Metaspace* msp = iter.get_next();
    // Sum allocation_total for each metaspace
    if (msp != NULL) {
      used += msp->used_words(mdtype);
    }
  }
  return used * BytesPerWord;
}

size_t MetaspaceAux::free_in_bytes(Metaspace::MetadataType mdtype) {
  size_t free = 0;
  ClassLoaderDataGraphMetaspaceIterator iter;
  while (iter.repeat()) {
    Metaspace* msp = iter.get_next();
    if (msp != NULL) {
      free += msp->free_words(mdtype);
    }
  }
  return free * BytesPerWord;
}

size_t MetaspaceAux::capacity_in_bytes(Metaspace::MetadataType mdtype) {
  size_t capacity = free_chunks_total(mdtype);
  ClassLoaderDataGraphMetaspaceIterator iter;
  while (iter.repeat()) {
    Metaspace* msp = iter.get_next();
    if (msp != NULL) {
      capacity += msp->capacity_words(mdtype);
    }
  }
  return capacity * BytesPerWord;
}

size_t MetaspaceAux::reserved_in_bytes(Metaspace::MetadataType mdtype) {
  size_t reserved = (mdtype == Metaspace::ClassType) ?
                       Metaspace::class_space_list()->virtual_space_total() :
                       Metaspace::space_list()->virtual_space_total();
  return reserved * BytesPerWord;
}

size_t MetaspaceAux::min_chunk_size() { return Metaspace::first_chunk_word_size(); }

size_t MetaspaceAux::free_chunks_total(Metaspace::MetadataType mdtype) {
  ChunkManager* chunk = (mdtype == Metaspace::ClassType) ?
                            Metaspace::class_space_list()->chunk_manager() :
                            Metaspace::space_list()->chunk_manager();
  chunk->slow_verify();
  return chunk->free_chunks_total();
}

size_t MetaspaceAux::free_chunks_total_in_bytes(Metaspace::MetadataType mdtype) {
  return free_chunks_total(mdtype) * BytesPerWord;
}

void MetaspaceAux::print_metaspace_change(size_t prev_metadata_used) {
  gclog_or_tty->print(", [Metaspace:");
  if (PrintGCDetails && Verbose) {
    gclog_or_tty->print(" "  SIZE_FORMAT
                        "->" SIZE_FORMAT
                        "("  SIZE_FORMAT "/" SIZE_FORMAT ")",
                        prev_metadata_used,
                        used_in_bytes(),
                        capacity_in_bytes(),
                        reserved_in_bytes());
  } else {
    gclog_or_tty->print(" "  SIZE_FORMAT "K"
                        "->" SIZE_FORMAT "K"
                        "("  SIZE_FORMAT "K/" SIZE_FORMAT "K)",
                        prev_metadata_used / K,
                        used_in_bytes()/ K,
                        capacity_in_bytes()/K,
                        reserved_in_bytes()/ K);
  }

  gclog_or_tty->print("]");
}

// This is printed when PrintGCDetails
void MetaspaceAux::print_on(outputStream* out) {
  Metaspace::MetadataType ct = Metaspace::ClassType;
  Metaspace::MetadataType nct = Metaspace::NonClassType;

  out->print_cr(" Metaspace total "
                SIZE_FORMAT "K, used " SIZE_FORMAT "K,"
                " reserved " SIZE_FORMAT "K",
                capacity_in_bytes()/K, used_in_bytes()/K, reserved_in_bytes()/K);
  out->print_cr("  data space     "
                SIZE_FORMAT "K, used " SIZE_FORMAT "K,"
                " reserved " SIZE_FORMAT "K",
                capacity_in_bytes(nct)/K, used_in_bytes(nct)/K, reserved_in_bytes(nct)/K);
  out->print_cr("  class space    "
                SIZE_FORMAT "K, used " SIZE_FORMAT "K,"
                " reserved " SIZE_FORMAT "K",
                capacity_in_bytes(ct)/K, used_in_bytes(ct)/K, reserved_in_bytes(ct)/K);
}

// Print information for class space and data space separately.
// This is almost the same as above.
void MetaspaceAux::print_on(outputStream* out, Metaspace::MetadataType mdtype) {
  size_t free_chunks_capacity_bytes = free_chunks_total_in_bytes(mdtype);
  size_t capacity_bytes = capacity_in_bytes(mdtype);
  size_t used_bytes = used_in_bytes(mdtype);
  size_t free_bytes = free_in_bytes(mdtype);
  size_t used_and_free = used_bytes + free_bytes +
                           free_chunks_capacity_bytes;
  out->print_cr("  Chunk accounting: used in chunks " SIZE_FORMAT
             "K + unused in chunks " SIZE_FORMAT "K  + "
             " capacity in free chunks " SIZE_FORMAT "K = " SIZE_FORMAT
             "K  capacity in allocated chunks " SIZE_FORMAT "K",
             used_bytes / K,
             free_bytes / K,
             free_chunks_capacity_bytes / K,
             used_and_free / K,
             capacity_bytes / K);
  // Accounting can only be correct if we got the values during a safepoint
  assert(!SafepointSynchronize::is_at_safepoint() || used_and_free == capacity_bytes, "Accounting is wrong");
}

// Print total fragmentation for class and data metaspaces separately
void MetaspaceAux::print_waste(outputStream* out) {

  size_t specialized_waste = 0, small_waste = 0, medium_waste = 0, large_waste = 0;
  size_t specialized_count = 0, small_count = 0, medium_count = 0, large_count = 0;
  size_t cls_specialized_waste = 0, cls_small_waste = 0, cls_medium_waste = 0, cls_large_waste = 0;
  size_t cls_specialized_count = 0, cls_small_count = 0, cls_medium_count = 0, cls_large_count = 0;

  ClassLoaderDataGraphMetaspaceIterator iter;
  while (iter.repeat()) {
    Metaspace* msp = iter.get_next();
    if (msp != NULL) {
      specialized_waste += msp->vsm()->sum_waste_in_chunks_in_use(SpecializedIndex);
      specialized_count += msp->vsm()->sum_count_in_chunks_in_use(SpecializedIndex);
      small_waste += msp->vsm()->sum_waste_in_chunks_in_use(SmallIndex);
      small_count += msp->vsm()->sum_count_in_chunks_in_use(SmallIndex);
      medium_waste += msp->vsm()->sum_waste_in_chunks_in_use(MediumIndex);
      medium_count += msp->vsm()->sum_count_in_chunks_in_use(MediumIndex);
      large_waste += msp->vsm()->sum_waste_in_chunks_in_use(HumongousIndex);
      large_count += msp->vsm()->sum_count_in_chunks_in_use(HumongousIndex);

      cls_specialized_waste += msp->class_vsm()->sum_waste_in_chunks_in_use(SpecializedIndex);
      cls_specialized_count += msp->class_vsm()->sum_count_in_chunks_in_use(SpecializedIndex);
      cls_small_waste += msp->class_vsm()->sum_waste_in_chunks_in_use(SmallIndex);
      cls_small_count += msp->class_vsm()->sum_count_in_chunks_in_use(SmallIndex);
      cls_medium_waste += msp->class_vsm()->sum_waste_in_chunks_in_use(MediumIndex);
      cls_medium_count += msp->class_vsm()->sum_count_in_chunks_in_use(MediumIndex);
      cls_large_waste += msp->class_vsm()->sum_waste_in_chunks_in_use(HumongousIndex);
      cls_large_count += msp->class_vsm()->sum_count_in_chunks_in_use(HumongousIndex);
    }
  }
  out->print_cr("Total fragmentation waste (words) doesn't count free space");
  out->print_cr("  data: " SIZE_FORMAT " specialized(s) " SIZE_FORMAT ", "
                        SIZE_FORMAT " small(s) " SIZE_FORMAT ", "
                        SIZE_FORMAT " medium(s) " SIZE_FORMAT,
             specialized_count, specialized_waste, small_count,
             small_waste, medium_count, medium_waste);
  out->print_cr(" class: " SIZE_FORMAT " specialized(s) " SIZE_FORMAT ", "
                           SIZE_FORMAT " small(s) " SIZE_FORMAT,
             cls_specialized_count, cls_specialized_waste,
             cls_small_count, cls_small_waste);
}

// Dump global metaspace things from the end of ClassLoaderDataGraph
void MetaspaceAux::dump(outputStream* out) {
  out->print_cr("All Metaspace:");
  out->print("data space: "); print_on(out, Metaspace::NonClassType);
  out->print("class space: "); print_on(out, Metaspace::ClassType);
  print_waste(out);
}

void MetaspaceAux::verify_free_chunks() {
  Metaspace::space_list()->chunk_manager()->verify();
  Metaspace::class_space_list()->chunk_manager()->verify();
}

// Metaspace methods

size_t Metaspace::_first_chunk_word_size = 0;
size_t Metaspace::_first_class_chunk_word_size = 0;

Metaspace::Metaspace(Mutex* lock, MetaspaceType type) {
  initialize(lock, type);
}

Metaspace::~Metaspace() {
  delete _vsm;
  delete _class_vsm;
}

VirtualSpaceList* Metaspace::_space_list = NULL;
VirtualSpaceList* Metaspace::_class_space_list = NULL;

#define VIRTUALSPACEMULTIPLIER 2

void Metaspace::global_initialize() {
  // Initialize the alignment for shared spaces.
  int max_alignment = os::vm_page_size();
  MetaspaceShared::set_max_alignment(max_alignment);

  if (DumpSharedSpaces) {
    SharedReadOnlySize = align_size_up(SharedReadOnlySize, max_alignment);
    SharedReadWriteSize = align_size_up(SharedReadWriteSize, max_alignment);
    SharedMiscDataSize  = align_size_up(SharedMiscDataSize, max_alignment);
    SharedMiscCodeSize  = align_size_up(SharedMiscCodeSize, max_alignment);

    // Initialize with the sum of the shared space sizes.  The read-only
    // and read write metaspace chunks will be allocated out of this and the
    // remainder is the misc code and data chunks.
    size_t total = align_size_up(SharedReadOnlySize + SharedReadWriteSize +
                                 SharedMiscDataSize + SharedMiscCodeSize,
                                 os::vm_allocation_granularity());
    size_t word_size = total/wordSize;
    _space_list = new VirtualSpaceList(word_size);
  } else {
    // If using shared space, open the file that contains the shared space
    // and map in the memory before initializing the rest of metaspace (so
    // the addresses don't conflict)
    if (UseSharedSpaces) {
      FileMapInfo* mapinfo = new FileMapInfo();
      memset(mapinfo, 0, sizeof(FileMapInfo));

      // Open the shared archive file, read and validate the header. If
      // initialization fails, shared spaces [UseSharedSpaces] are
      // disabled and the file is closed.
      // Map in spaces now also
      if (mapinfo->initialize() && MetaspaceShared::map_shared_spaces(mapinfo)) {
        FileMapInfo::set_current_info(mapinfo);
      } else {
        assert(!mapinfo->is_open() && !UseSharedSpaces,
               "archive file not closed or shared spaces not disabled.");
      }
    }

    // Initialize these before initializing the VirtualSpaceList
    _first_chunk_word_size = InitialBootClassLoaderMetaspaceSize / BytesPerWord;
    _first_chunk_word_size = align_word_size_up(_first_chunk_word_size);
    // Make the first class chunk bigger than a medium chunk so it's not put
    // on the medium chunk list.   The next chunk will be small and progress
    // from there.  This size calculated by -version.
    _first_class_chunk_word_size = MIN2((size_t)MediumChunk*6,
                                       (ClassMetaspaceSize/BytesPerWord)*2);
    _first_class_chunk_word_size = align_word_size_up(_first_class_chunk_word_size);
    // Arbitrarily set the initial virtual space to a multiple
    // of the boot class loader size.
    size_t word_size = VIRTUALSPACEMULTIPLIER * first_chunk_word_size();
    // Initialize the list of virtual spaces.
    _space_list = new VirtualSpaceList(word_size);
  }
}

// For UseCompressedKlassPointers the class space is reserved as a piece of the
// Java heap because the compression algorithm is the same for each.  The
// argument passed in is at the top of the compressed space
void Metaspace::initialize_class_space(ReservedSpace rs) {
  // The reserved space size may be bigger because of alignment, esp with UseLargePages
  assert(rs.size() >= ClassMetaspaceSize,
         err_msg(SIZE_FORMAT " != " UINTX_FORMAT, rs.size(), ClassMetaspaceSize));
  _class_space_list = new VirtualSpaceList(rs);
}

void Metaspace::initialize(Mutex* lock,
                           MetaspaceType type) {

  assert(space_list() != NULL,
    "Metadata VirtualSpaceList has not been initialized");

  _vsm = new SpaceManager(lock, space_list());
  if (_vsm == NULL) {
    return;
  }
  size_t word_size;
  size_t class_word_size;
  vsm()->get_initial_chunk_sizes(type,
                                 &word_size,
                                 &class_word_size);

  assert(class_space_list() != NULL,
    "Class VirtualSpaceList has not been initialized");

  // Allocate SpaceManager for classes.
  _class_vsm = new SpaceManager(lock, class_space_list());
  if (_class_vsm == NULL) {
    return;
  }

  MutexLockerEx cl(SpaceManager::expand_lock(), Mutex::_no_safepoint_check_flag);

  // Allocate chunk for metadata objects
  Metachunk* new_chunk =
     space_list()->get_initialization_chunk(word_size,
                                            vsm()->medium_chunk_bunch());
  assert(!DumpSharedSpaces || new_chunk != NULL, "should have enough space for both chunks");
  if (new_chunk != NULL) {
    // Add to this manager's list of chunks in use and current_chunk().
    vsm()->add_chunk(new_chunk, true);
  }

  // Allocate chunk for class metadata objects
  Metachunk* class_chunk =
     class_space_list()->get_initialization_chunk(class_word_size,
                                                  class_vsm()->medium_chunk_bunch());
  if (class_chunk != NULL) {
    class_vsm()->add_chunk(class_chunk, true);
  }
}

size_t Metaspace::align_word_size_up(size_t word_size) {
  size_t byte_size = word_size * wordSize;
  return ReservedSpace::allocation_align_size_up(byte_size) / wordSize;
}

MetaWord* Metaspace::allocate(size_t word_size, MetadataType mdtype) {
  // DumpSharedSpaces doesn't use class metadata area (yet)
  if (mdtype == ClassType && !DumpSharedSpaces) {
    return  class_vsm()->allocate(word_size);
  } else {
    return  vsm()->allocate(word_size);
  }
}

MetaWord* Metaspace::expand_and_allocate(size_t word_size, MetadataType mdtype) {
  MetaWord* result;
  MetaspaceGC::set_expand_after_GC(true);
  size_t before_inc = MetaspaceGC::capacity_until_GC();
  size_t delta_words = MetaspaceGC::delta_capacity_until_GC(word_size);
  MetaspaceGC::inc_capacity_until_GC(delta_words);
  if (PrintGCDetails && Verbose) {
    gclog_or_tty->print_cr("Increase capacity to GC from " SIZE_FORMAT
      " to " SIZE_FORMAT, before_inc, MetaspaceGC::capacity_until_GC());
  }

  result = allocate(word_size, mdtype);

  return result;
}

// Space allocated in the Metaspace.  This may
// be across several metadata virtual spaces.
char* Metaspace::bottom() const {
  assert(DumpSharedSpaces, "only useful and valid for dumping shared spaces");
  return (char*)vsm()->current_chunk()->bottom();
}

size_t Metaspace::used_words(MetadataType mdtype) const {
  // return vsm()->allocation_total();
  return mdtype == ClassType ? class_vsm()->sum_used_in_chunks_in_use() :
                               vsm()->sum_used_in_chunks_in_use();  // includes overhead!
}

size_t Metaspace::free_words(MetadataType mdtype) const {
  return mdtype == ClassType ? class_vsm()->sum_free_in_chunks_in_use() :
                               vsm()->sum_free_in_chunks_in_use();
}

// Space capacity in the Metaspace.  It includes
// space in the list of chunks from which allocations
// have been made. Don't include space in the global freelist and
// in the space available in the dictionary which
// is already counted in some chunk.
size_t Metaspace::capacity_words(MetadataType mdtype) const {
  return mdtype == ClassType ? class_vsm()->sum_capacity_in_chunks_in_use() :
                               vsm()->sum_capacity_in_chunks_in_use();
}

void Metaspace::deallocate(MetaWord* ptr, size_t word_size, bool is_class) {
  if (SafepointSynchronize::is_at_safepoint()) {
    assert(Thread::current()->is_VM_thread(), "should be the VM thread");
    // Don't take Heap_lock
    MutexLocker ml(vsm()->lock());
    if (word_size < TreeChunk<Metablock, FreeList>::min_size()) {
      // Dark matter.  Too small for dictionary.
#ifdef ASSERT
      Copy::fill_to_words((HeapWord*)ptr, word_size, 0xf5f5f5f5);
#endif
      return;
    }
    if (is_class) {
       class_vsm()->deallocate(ptr, word_size);
    } else {
      vsm()->deallocate(ptr, word_size);
    }
  } else {
    MutexLocker ml(vsm()->lock());

    if (word_size < TreeChunk<Metablock, FreeList>::min_size()) {
      // Dark matter.  Too small for dictionary.
#ifdef ASSERT
      Copy::fill_to_words((HeapWord*)ptr, word_size, 0xf5f5f5f5);
#endif
      return;
    }
    if (is_class) {
      class_vsm()->deallocate(ptr, word_size);
    } else {
      vsm()->deallocate(ptr, word_size);
    }
  }
}

Metablock* Metaspace::allocate(ClassLoaderData* loader_data, size_t word_size,
                              bool read_only, MetadataType mdtype, TRAPS) {
  if (HAS_PENDING_EXCEPTION) {
    assert(false, "Should not allocate with exception pending");
    return NULL;  // caller does a CHECK_NULL too
  }

  // SSS: Should we align the allocations and make sure the sizes are aligned.
  MetaWord* result = NULL;

  assert(loader_data != NULL, "Should never pass around a NULL loader_data. "
        "ClassLoaderData::the_null_class_loader_data() should have been used.");
  // Allocate in metaspaces without taking out a lock, because it deadlocks
  // with the SymbolTable_lock.  Dumping is single threaded for now.  We'll have
  // to revisit this for application class data sharing.
  if (DumpSharedSpaces) {
    if (read_only) {
      result = loader_data->ro_metaspace()->allocate(word_size, NonClassType);
    } else {
      result = loader_data->rw_metaspace()->allocate(word_size, NonClassType);
    }
    if (result == NULL) {
      report_out_of_shared_space(read_only ? SharedReadOnly : SharedReadWrite);
    }
    return Metablock::initialize(result, word_size);
  }

  result = loader_data->metaspace_non_null()->allocate(word_size, mdtype);

  if (result == NULL) {
    // Try to clean out some memory and retry.
    result =
      Universe::heap()->collector_policy()->satisfy_failed_metadata_allocation(
        loader_data, word_size, mdtype);

    // If result is still null, we are out of memory.
    if (result == NULL) {
      if (Verbose && TraceMetadataChunkAllocation) {
        gclog_or_tty->print_cr("Metaspace allocation failed for size "
          SIZE_FORMAT, word_size);
        if (loader_data->metaspace_or_null() != NULL) loader_data->metaspace_or_null()->dump(gclog_or_tty);
        MetaspaceAux::dump(gclog_or_tty);
      }
      // -XX:+HeapDumpOnOutOfMemoryError and -XX:OnOutOfMemoryError support
      report_java_out_of_memory("Metadata space");

      if (JvmtiExport::should_post_resource_exhausted()) {
        JvmtiExport::post_resource_exhausted(
            JVMTI_RESOURCE_EXHAUSTED_OOM_ERROR,
            "Metadata space");
      }
      THROW_OOP_0(Universe::out_of_memory_error_perm_gen());
    }
  }
  return Metablock::initialize(result, word_size);
}

void Metaspace::print_on(outputStream* out) const {
  // Print both class virtual space counts and metaspace.
  if (Verbose) {
      vsm()->print_on(out);
      class_vsm()->print_on(out);
  }
}

bool Metaspace::contains(const void * ptr) {
  if (MetaspaceShared::is_in_shared_space(ptr)) {
    return true;
  }
  // This is checked while unlocked.  As long as the virtualspaces are added
  // at the end, the pointer will be in one of them.  The virtual spaces
  // aren't deleted presently.  When they are, some sort of locking might
  // be needed.  Note, locking this can cause inversion problems with the
  // caller in MetaspaceObj::is_metadata() function.
  return space_list()->contains(ptr) || class_space_list()->contains(ptr);
}

void Metaspace::verify() {
  vsm()->verify();
  class_vsm()->verify();
}

void Metaspace::dump(outputStream* const out) const {
  if (UseMallocOnly) {
    // Just print usage for now
    out->print_cr("usage %d", used_words(Metaspace::NonClassType));
  }
  out->print_cr("\nVirtual space manager: " INTPTR_FORMAT, vsm());
  vsm()->dump(out);
  out->print_cr("\nClass space manager: " INTPTR_FORMAT, class_vsm());
  class_vsm()->dump(out);
}
