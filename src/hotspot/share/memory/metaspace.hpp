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
#ifndef SHARE_VM_MEMORY_METASPACE_HPP
#define SHARE_VM_MEMORY_METASPACE_HPP

#include "memory/allocation.hpp"
#include "memory/memRegion.hpp"
#include "memory/metaspaceChunkFreeListSummary.hpp"
#include "memory/virtualspace.hpp"
#include "utilities/exceptions.hpp"

// Metaspace
//
// Metaspaces are Arenas for the VM's metadata.
// They are allocated one per class loader object, and one for the null
// bootstrap class loader
//
//    block X ---+       +-------------------+
//               |       |  Virtualspace     |
//               |       |                   |
//               |       |                   |
//               |       |-------------------|
//               |       || Chunk            |
//               |       ||                  |
//               |       ||----------        |
//               +------>||| block 0 |       |
//                       ||----------        |
//                       ||| block 1 |       |
//                       ||----------        |
//                       ||                  |
//                       |-------------------|
//                       |                   |
//                       |                   |
//                       +-------------------+
//

class ClassLoaderData;
class MetaspaceTracer;
class MetaWord;
class Mutex;
class outputStream;

class CollectedHeap;

namespace metaspace {
  class ChunkManager;
  class ClassLoaderMetaspaceStatistics;
  class Metablock;
  class Metachunk;
  class PrintCLDMetaspaceInfoClosure;
  class SpaceManager;
  class VirtualSpaceList;
  class VirtualSpaceNode;
}

// Metaspaces each have a  SpaceManager and allocations
// are done by the SpaceManager.  Allocations are done
// out of the current Metachunk.  When the current Metachunk
// is exhausted, the SpaceManager gets a new one from
// the current VirtualSpace.  When the VirtualSpace is exhausted
// the SpaceManager gets a new one.  The SpaceManager
// also manages freelists of available Chunks.
//
// Currently the space manager maintains the list of
// virtual spaces and the list of chunks in use.  Its
// allocate() method returns a block for use as a
// quantum of metadata.

// Namespace for important central static functions
// (auxiliary stuff goes into MetaspaceUtils)
class Metaspace : public AllStatic {

  friend class MetaspaceShared;

 public:
  enum MetadataType {
    ClassType,
    NonClassType,
    MetadataTypeCount
  };
  enum MetaspaceType {
    ZeroMetaspaceType = 0,
    StandardMetaspaceType = ZeroMetaspaceType,
    BootMetaspaceType = StandardMetaspaceType + 1,
    UnsafeAnonymousMetaspaceType = BootMetaspaceType + 1,
    ReflectionMetaspaceType = UnsafeAnonymousMetaspaceType + 1,
    MetaspaceTypeCount
  };

 private:

  // Align up the word size to the allocation word size
  static size_t align_word_size_up(size_t);

  // Aligned size of the metaspace.
  static size_t _compressed_class_space_size;

  static size_t compressed_class_space_size() {
    return _compressed_class_space_size;
  }

  static void set_compressed_class_space_size(size_t size) {
    _compressed_class_space_size = size;
  }

  static size_t _first_chunk_word_size;
  static size_t _first_class_chunk_word_size;

  static size_t _commit_alignment;
  static size_t _reserve_alignment;
  DEBUG_ONLY(static bool   _frozen;)

  // Virtual Space lists for both classes and other metadata
  static metaspace::VirtualSpaceList* _space_list;
  static metaspace::VirtualSpaceList* _class_space_list;

  static metaspace::ChunkManager* _chunk_manager_metadata;
  static metaspace::ChunkManager* _chunk_manager_class;

  static const MetaspaceTracer* _tracer;

 public:
  static metaspace::VirtualSpaceList* space_list()       { return _space_list; }
  static metaspace::VirtualSpaceList* class_space_list() { return _class_space_list; }
  static metaspace::VirtualSpaceList* get_space_list(MetadataType mdtype) {
    assert(mdtype != MetadataTypeCount, "MetadaTypeCount can't be used as mdtype");
    return mdtype == ClassType ? class_space_list() : space_list();
  }

  static metaspace::ChunkManager* chunk_manager_metadata() { return _chunk_manager_metadata; }
  static metaspace::ChunkManager* chunk_manager_class()    { return _chunk_manager_class; }
  static metaspace::ChunkManager* get_chunk_manager(MetadataType mdtype) {
    assert(mdtype != MetadataTypeCount, "MetadaTypeCount can't be used as mdtype");
    return mdtype == ClassType ? chunk_manager_class() : chunk_manager_metadata();
  }

  // convenience function
  static metaspace::ChunkManager* get_chunk_manager(bool is_class) {
    return is_class ? chunk_manager_class() : chunk_manager_metadata();
  }

  static const MetaspaceTracer* tracer() { return _tracer; }
  static void freeze() {
    assert(DumpSharedSpaces, "sanity");
    DEBUG_ONLY(_frozen = true;)
  }
  static void assert_not_frozen() {
    assert(!_frozen, "sanity");
  }
#ifdef _LP64
  static void allocate_metaspace_compressed_klass_ptrs(char* requested_addr, address cds_base);
#endif

 private:

#ifdef _LP64
  static void set_narrow_klass_base_and_shift(address metaspace_base, address cds_base);

  // Returns true if can use CDS with metaspace allocated as specified address.
  static bool can_use_cds_with_metaspace_addr(char* metaspace_base, address cds_base);

  static void initialize_class_space(ReservedSpace rs);
#endif

 public:

  static void ergo_initialize();
  static void global_initialize();
  static void post_initialize();

  static void verify_global_initialization();

  static size_t first_chunk_word_size() { return _first_chunk_word_size; }
  static size_t first_class_chunk_word_size() { return _first_class_chunk_word_size; }

  static size_t reserve_alignment()       { return _reserve_alignment; }
  static size_t reserve_alignment_words() { return _reserve_alignment / BytesPerWord; }
  static size_t commit_alignment()        { return _commit_alignment; }
  static size_t commit_alignment_words()  { return _commit_alignment / BytesPerWord; }

  static MetaWord* allocate(ClassLoaderData* loader_data, size_t word_size,
                            MetaspaceObj::Type type, TRAPS);

  static bool contains(const void* ptr);
  static bool contains_non_shared(const void* ptr);

  // Free empty virtualspaces
  static void purge(MetadataType mdtype);
  static void purge();

  static void report_metadata_oome(ClassLoaderData* loader_data, size_t word_size,
                                   MetaspaceObj::Type type, MetadataType mdtype, TRAPS);

  static const char* metadata_type_name(Metaspace::MetadataType mdtype);

  static void print_compressed_class_space(outputStream* st, const char* requested_addr = 0) NOT_LP64({});

  // Return TRUE only if UseCompressedClassPointers is True.
  static bool using_class_space() {
    return NOT_LP64(false) LP64_ONLY(UseCompressedClassPointers);
  }

  static bool is_class_space_allocation(MetadataType mdType) {
    return mdType == ClassType && using_class_space();
  }

};

// Manages the metaspace portion belonging to a class loader
class ClassLoaderMetaspace : public CHeapObj<mtClass> {
  friend class CollectedHeap; // For expand_and_allocate()
  friend class ZCollectedHeap; // For expand_and_allocate()
  friend class ShenandoahHeap; // For expand_and_allocate()
  friend class Metaspace;
  friend class MetaspaceUtils;
  friend class metaspace::PrintCLDMetaspaceInfoClosure;
  friend class VM_CollectForMetadataAllocation; // For expand_and_allocate()

 private:

  void initialize(Mutex* lock, Metaspace::MetaspaceType type);

  // Initialize the first chunk for a Metaspace.  Used for
  // special cases such as the boot class loader, reflection
  // class loader and anonymous class loader.
  void initialize_first_chunk(Metaspace::MetaspaceType type, Metaspace::MetadataType mdtype);
  metaspace::Metachunk* get_initialization_chunk(Metaspace::MetaspaceType type, Metaspace::MetadataType mdtype);

  const Metaspace::MetaspaceType _space_type;
  Mutex* const  _lock;
  metaspace::SpaceManager* _vsm;
  metaspace::SpaceManager* _class_vsm;

  metaspace::SpaceManager* vsm() const { return _vsm; }
  metaspace::SpaceManager* class_vsm() const { return _class_vsm; }
  metaspace::SpaceManager* get_space_manager(Metaspace::MetadataType mdtype) {
    assert(mdtype != Metaspace::MetadataTypeCount, "MetadaTypeCount can't be used as mdtype");
    return mdtype == Metaspace::ClassType ? class_vsm() : vsm();
  }

  Mutex* lock() const { return _lock; }

  MetaWord* expand_and_allocate(size_t size, Metaspace::MetadataType mdtype);

  size_t class_chunk_size(size_t word_size);

  // Adds to the given statistic object. Must be locked with CLD metaspace lock.
  void add_to_statistics_locked(metaspace::ClassLoaderMetaspaceStatistics* out) const;

  Metaspace::MetaspaceType space_type() const { return _space_type; }

 public:

  ClassLoaderMetaspace(Mutex* lock, Metaspace::MetaspaceType type);
  ~ClassLoaderMetaspace();

  // Allocate space for metadata of type mdtype. This is space
  // within a Metachunk and is used by
  //   allocate(ClassLoaderData*, size_t, bool, MetadataType, TRAPS)
  MetaWord* allocate(size_t word_size, Metaspace::MetadataType mdtype);

  size_t allocated_blocks_bytes() const;
  size_t allocated_chunks_bytes() const;

  void deallocate(MetaWord* ptr, size_t byte_size, bool is_class);

  void print_on(outputStream* st) const;
  // Debugging support
  void verify();

  // Adds to the given statistic object. Will lock with CLD metaspace lock.
  void add_to_statistics(metaspace::ClassLoaderMetaspaceStatistics* out) const;

}; // ClassLoaderMetaspace

class MetaspaceUtils : AllStatic {

  // Spacemanager updates running counters.
  friend class metaspace::SpaceManager;

  // Special access for error reporting (checks without locks).
  friend class oopDesc;
  friend class Klass;

  // Running counters for statistics concerning in-use chunks.
  // Note: capacity = used + free + waste + overhead. Note that we do not
  // count free and waste. Their sum can be deduces from the three other values.
  // For more details, one should call print_report() from within a safe point.
  static size_t _capacity_words [Metaspace:: MetadataTypeCount];
  static size_t _overhead_words [Metaspace:: MetadataTypeCount];
  static volatile size_t _used_words [Metaspace:: MetadataTypeCount];

  // Atomically decrement or increment in-use statistic counters
  static void dec_capacity(Metaspace::MetadataType mdtype, size_t words);
  static void inc_capacity(Metaspace::MetadataType mdtype, size_t words);
  static void dec_used(Metaspace::MetadataType mdtype, size_t words);
  static void inc_used(Metaspace::MetadataType mdtype, size_t words);
  static void dec_overhead(Metaspace::MetadataType mdtype, size_t words);
  static void inc_overhead(Metaspace::MetadataType mdtype, size_t words);


  // Getters for the in-use counters.
  static size_t capacity_words(Metaspace::MetadataType mdtype)        { return _capacity_words[mdtype]; }
  static size_t used_words(Metaspace::MetadataType mdtype)            { return _used_words[mdtype]; }
  static size_t overhead_words(Metaspace::MetadataType mdtype)        { return _overhead_words[mdtype]; }

  static size_t free_chunks_total_words(Metaspace::MetadataType mdtype);

  // Helper for print_xx_report.
  static void print_vs(outputStream* out, size_t scale);

  // Utils to check if a pointer or range is part of a committed metaspace region
  // without acquiring any locks.
  static metaspace::VirtualSpaceNode* find_enclosing_virtual_space(const void* p);
  static bool is_in_committed(const void* p);
  static bool is_range_in_committed(const void* from, const void* to);

public:

  // Collect used metaspace statistics. This involves walking the CLDG. The resulting
  // output will be the accumulated values for all live metaspaces.
  // Note: method does not do any locking.
  static void collect_statistics(metaspace::ClassLoaderMetaspaceStatistics* out);

  // Used by MetaspaceCounters
  static size_t free_chunks_total_words();
  static size_t free_chunks_total_bytes();
  static size_t free_chunks_total_bytes(Metaspace::MetadataType mdtype);

  static size_t capacity_words() {
    return capacity_words(Metaspace::NonClassType) +
           capacity_words(Metaspace::ClassType);
  }
  static size_t capacity_bytes(Metaspace::MetadataType mdtype) {
    return capacity_words(mdtype) * BytesPerWord;
  }
  static size_t capacity_bytes() {
    return capacity_words() * BytesPerWord;
  }

  static size_t used_words() {
    return used_words(Metaspace::NonClassType) +
           used_words(Metaspace::ClassType);
  }
  static size_t used_bytes(Metaspace::MetadataType mdtype) {
    return used_words(mdtype) * BytesPerWord;
  }
  static size_t used_bytes() {
    return used_words() * BytesPerWord;
  }

  // Space committed but yet unclaimed by any class loader.
  static size_t free_in_vs_bytes();
  static size_t free_in_vs_bytes(Metaspace::MetadataType mdtype);

  static size_t reserved_bytes(Metaspace::MetadataType mdtype);
  static size_t reserved_bytes() {
    return reserved_bytes(Metaspace::ClassType) +
           reserved_bytes(Metaspace::NonClassType);
  }

  static size_t committed_bytes(Metaspace::MetadataType mdtype);
  static size_t committed_bytes() {
    return committed_bytes(Metaspace::ClassType) +
           committed_bytes(Metaspace::NonClassType);
  }

  static size_t min_chunk_size_words();
  static size_t min_chunk_size_bytes() {
    return min_chunk_size_words() * BytesPerWord;
  }

  // Flags for print_report().
  enum ReportFlag {
    // Show usage by class loader.
    rf_show_loaders                 = (1 << 0),
    // Breaks report down by chunk type (small, medium, ...).
    rf_break_down_by_chunktype      = (1 << 1),
    // Breaks report down by space type (anonymous, reflection, ...).
    rf_break_down_by_spacetype      = (1 << 2),
    // Print details about the underlying virtual spaces.
    rf_show_vslist                  = (1 << 3),
    // Print metaspace map.
    rf_show_vsmap                   = (1 << 4),
    // If show_loaders: show loaded classes for each loader.
    rf_show_classes                 = (1 << 5)
  };

  // This will print out a basic metaspace usage report but
  // unlike print_report() is guaranteed not to lock or to walk the CLDG.
  static void print_basic_report(outputStream* st, size_t scale);

  // Prints a report about the current metaspace state.
  // Optional parts can be enabled via flags.
  // Function will walk the CLDG and will lock the expand lock; if that is not
  // convenient, use print_basic_report() instead.
  static void print_report(outputStream* out, size_t scale = 0, int flags = 0);

  static bool has_chunk_free_list(Metaspace::MetadataType mdtype);
  static MetaspaceChunkFreeListSummary chunk_free_list_summary(Metaspace::MetadataType mdtype);

  // Print change in used metadata.
  static void print_metaspace_change(size_t prev_metadata_used);
  static void print_on(outputStream * out);

  // Prints an ASCII representation of the given space.
  static void print_metaspace_map(outputStream* out, Metaspace::MetadataType mdtype);

  static void dump(outputStream* out);
  static void verify_free_chunks();
  // Check internal counters (capacity, used).
  static void verify_metrics();
};

// Metaspace are deallocated when their class loader are GC'ed.
// This class implements a policy for inducing GC's to recover
// Metaspaces.

class MetaspaceGC : AllStatic {

  // The current high-water-mark for inducing a GC.
  // When committed memory of all metaspaces reaches this value,
  // a GC is induced and the value is increased. Size is in bytes.
  static volatile size_t _capacity_until_GC;

  // For a CMS collection, signal that a concurrent collection should
  // be started.
  static bool _should_concurrent_collect;

  static uint _shrink_factor;

  static size_t shrink_factor() { return _shrink_factor; }
  void set_shrink_factor(uint v) { _shrink_factor = v; }

 public:

  static void initialize();
  static void post_initialize();

  static size_t capacity_until_GC();
  static bool inc_capacity_until_GC(size_t v,
                                    size_t* new_cap_until_GC = NULL,
                                    size_t* old_cap_until_GC = NULL);
  static size_t dec_capacity_until_GC(size_t v);

  static bool should_concurrent_collect() { return _should_concurrent_collect; }
  static void set_should_concurrent_collect(bool v) {
    _should_concurrent_collect = v;
  }

  // The amount to increase the high-water-mark (_capacity_until_GC)
  static size_t delta_capacity_until_GC(size_t bytes);

  // Tells if we have can expand metaspace without hitting set limits.
  static bool can_expand(size_t words, bool is_class);

  // Returns amount that we can expand without hitting a GC,
  // measured in words.
  static size_t allowed_expansion();

  // Calculate the new high-water mark at which to induce
  // a GC.
  static void compute_new_size();
};

#endif // SHARE_VM_MEMORY_METASPACE_HPP
