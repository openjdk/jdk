/*
 * Copyright (c) 2011, 2013, Oracle and/or its affiliates. All rights reserved.
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
#include "runtime/virtualspace.hpp"
#include "utilities/exceptions.hpp"

// Metaspace
//
// Metaspaces are Arenas for the VM's metadata.
// They are allocated one per class loader object, and one for the null
// bootstrap class loader
// Eventually for bootstrap loader we'll have a read-only section and read-write
// to write for DumpSharedSpaces and read for UseSharedSpaces
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
class Metablock;
class MetaWord;
class Mutex;
class outputStream;
class SpaceManager;

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

class VirtualSpaceList;

class Metaspace : public CHeapObj<mtClass> {
  friend class VMStructs;
  friend class SpaceManager;
  friend class VM_CollectForMetadataAllocation;
  friend class MetaspaceGC;
  friend class MetaspaceAux;

 public:
  enum MetadataType {ClassType = 0,
                     NonClassType = ClassType + 1,
                     MetadataTypeCount = ClassType + 2
  };
  enum MetaspaceType {
    StandardMetaspaceType,
    BootMetaspaceType,
    ROMetaspaceType,
    ReadWriteMetaspaceType,
    AnonymousMetaspaceType,
    ReflectionMetaspaceType
  };

 private:
  void initialize(Mutex* lock, MetaspaceType type);

  // Align up the word size to the allocation word size
  static size_t align_word_size_up(size_t);

  // Aligned size of the metaspace.
  static size_t _class_metaspace_size;

  static size_t class_metaspace_size() {
    return _class_metaspace_size;
  }
  static void set_class_metaspace_size(size_t metaspace_size) {
    _class_metaspace_size = metaspace_size;
  }

  static size_t _first_chunk_word_size;
  static size_t _first_class_chunk_word_size;

  SpaceManager* _vsm;
  SpaceManager* vsm() const { return _vsm; }

  SpaceManager* _class_vsm;
  SpaceManager* class_vsm() const { return _class_vsm; }

  // Allocate space for metadata of type mdtype. This is space
  // within a Metachunk and is used by
  //   allocate(ClassLoaderData*, size_t, bool, MetadataType, TRAPS)
  // which returns a Metablock.
  MetaWord* allocate(size_t word_size, MetadataType mdtype);

  // Virtual Space lists for both classes and other metadata
  static VirtualSpaceList* _space_list;
  static VirtualSpaceList* _class_space_list;

  static VirtualSpaceList* space_list()       { return _space_list; }
  static VirtualSpaceList* class_space_list() { return _class_space_list; }
  static VirtualSpaceList* get_space_list(MetadataType mdtype) {
    assert(mdtype != MetadataTypeCount, "MetadaTypeCount can't be used as mdtype");
    return mdtype == ClassType ? class_space_list() : space_list();
  }

  // This is used by DumpSharedSpaces only, where only _vsm is used. So we will
  // maintain a single list for now.
  void record_allocation(void* ptr, MetaspaceObj::Type type, size_t word_size);

#ifdef _LP64
  static void set_narrow_klass_base_and_shift(address metaspace_base, address cds_base);

  // Returns true if can use CDS with metaspace allocated as specified address.
  static bool can_use_cds_with_metaspace_addr(char* metaspace_base, address cds_base);

  static void allocate_metaspace_compressed_klass_ptrs(char* requested_addr, address cds_base);

  static void initialize_class_space(ReservedSpace rs);
#endif

  class AllocRecord : public CHeapObj<mtClass> {
  public:
    AllocRecord(address ptr, MetaspaceObj::Type type, int byte_size)
      : _next(NULL), _ptr(ptr), _type(type), _byte_size(byte_size) {}
    AllocRecord *_next;
    address _ptr;
    MetaspaceObj::Type _type;
    int _byte_size;
  };

  AllocRecord * _alloc_record_head;
  AllocRecord * _alloc_record_tail;

 public:

  Metaspace(Mutex* lock, MetaspaceType type);
  ~Metaspace();

  // Initialize globals for Metaspace
  static void global_initialize();

  static size_t first_chunk_word_size() { return _first_chunk_word_size; }
  static size_t first_class_chunk_word_size() { return _first_class_chunk_word_size; }

  char*  bottom() const;
  size_t used_words_slow(MetadataType mdtype) const;
  size_t free_words_slow(MetadataType mdtype) const;
  size_t capacity_words_slow(MetadataType mdtype) const;

  size_t used_bytes_slow(MetadataType mdtype) const;
  size_t capacity_bytes_slow(MetadataType mdtype) const;

  static Metablock* allocate(ClassLoaderData* loader_data, size_t word_size,
                             bool read_only, MetaspaceObj::Type type, TRAPS);
  void deallocate(MetaWord* ptr, size_t byte_size, bool is_class);

  MetaWord* expand_and_allocate(size_t size,
                                MetadataType mdtype);

  static bool contains(const void *ptr);
  void dump(outputStream* const out) const;

  // Free empty virtualspaces
  static void purge();

  void print_on(outputStream* st) const;
  // Debugging support
  void verify();

  class AllocRecordClosure :  public StackObj {
  public:
    virtual void doit(address ptr, MetaspaceObj::Type type, int byte_size) = 0;
  };

  void iterate(AllocRecordClosure *closure);

  // Return TRUE only if UseCompressedClassPointers is True and DumpSharedSpaces is False.
  static bool using_class_space() {
    return NOT_LP64(false) LP64_ONLY(UseCompressedClassPointers && !DumpSharedSpaces);
  }

};

class MetaspaceAux : AllStatic {
  static size_t free_chunks_total_words(Metaspace::MetadataType mdtype);

  // These methods iterate over the classloader data graph
  // for the given Metaspace type.  These are slow.
  static size_t used_bytes_slow(Metaspace::MetadataType mdtype);
  static size_t free_bytes_slow(Metaspace::MetadataType mdtype);
  static size_t capacity_bytes_slow(Metaspace::MetadataType mdtype);
  static size_t capacity_bytes_slow();

  // Running sum of space in all Metachunks that has been
  // allocated to a Metaspace.  This is used instead of
  // iterating over all the classloaders. One for each
  // type of Metadata
  static size_t _allocated_capacity_words[Metaspace:: MetadataTypeCount];
  // Running sum of space in all Metachunks that have
  // are being used for metadata. One for each
  // type of Metadata.
  static size_t _allocated_used_words[Metaspace:: MetadataTypeCount];

 public:
  // Decrement and increment _allocated_capacity_words
  static void dec_capacity(Metaspace::MetadataType type, size_t words);
  static void inc_capacity(Metaspace::MetadataType type, size_t words);

  // Decrement and increment _allocated_used_words
  static void dec_used(Metaspace::MetadataType type, size_t words);
  static void inc_used(Metaspace::MetadataType type, size_t words);

  // Total of space allocated to metadata in all Metaspaces.
  // This sums the space used in each Metachunk by
  // iterating over the classloader data graph
  static size_t used_bytes_slow() {
    return used_bytes_slow(Metaspace::ClassType) +
           used_bytes_slow(Metaspace::NonClassType);
  }

  // Used by MetaspaceCounters
  static size_t free_chunks_total_words();
  static size_t free_chunks_total_bytes();
  static size_t free_chunks_total_bytes(Metaspace::MetadataType mdtype);

  static size_t allocated_capacity_words(Metaspace::MetadataType mdtype) {
    return _allocated_capacity_words[mdtype];
  }
  static size_t allocated_capacity_words() {
    return allocated_capacity_words(Metaspace::NonClassType) +
           allocated_capacity_words(Metaspace::ClassType);
  }
  static size_t allocated_capacity_bytes(Metaspace::MetadataType mdtype) {
    return allocated_capacity_words(mdtype) * BytesPerWord;
  }
  static size_t allocated_capacity_bytes() {
    return allocated_capacity_words() * BytesPerWord;
  }

  static size_t allocated_used_words(Metaspace::MetadataType mdtype) {
    return _allocated_used_words[mdtype];
  }
  static size_t allocated_used_words() {
    return allocated_used_words(Metaspace::NonClassType) +
           allocated_used_words(Metaspace::ClassType);
  }
  static size_t allocated_used_bytes(Metaspace::MetadataType mdtype) {
    return allocated_used_words(mdtype) * BytesPerWord;
  }
  static size_t allocated_used_bytes() {
    return allocated_used_words() * BytesPerWord;
  }

  static size_t free_bytes();
  static size_t free_bytes(Metaspace::MetadataType mdtype);

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

  // Print change in used metadata.
  static void print_metaspace_change(size_t prev_metadata_used);
  static void print_on(outputStream * out);
  static void print_on(outputStream * out, Metaspace::MetadataType mdtype);

  static void print_class_waste(outputStream* out);
  static void print_waste(outputStream* out);
  static void dump(outputStream* out);
  static void verify_free_chunks();
  // Checks that the values returned by allocated_capacity_bytes() and
  // capacity_bytes_slow() are the same.
  static void verify_capacity();
  static void verify_used();
  static void verify_metrics();
};

// Metaspace are deallocated when their class loader are GC'ed.
// This class implements a policy for inducing GC's to recover
// Metaspaces.

class MetaspaceGC : AllStatic {

  // The current high-water-mark for inducing a GC.  When
  // the capacity of all space in the virtual lists reaches this value,
  // a GC is induced and the value is increased.  This should be changed
  // to the space actually used for allocations to avoid affects of
  // fragmentation losses to partially used chunks.  Size is in words.
  static size_t _capacity_until_GC;

  // After a GC is done any allocation that fails should try to expand
  // the capacity of the Metaspaces.  This flag is set during attempts
  // to allocate in the VMGCOperation that does the GC.
  static bool _expand_after_GC;

  // For a CMS collection, signal that a concurrent collection should
  // be started.
  static bool _should_concurrent_collect;

  static uint _shrink_factor;

  static void set_capacity_until_GC(size_t v) { _capacity_until_GC = v; }

  static size_t shrink_factor() { return _shrink_factor; }
  void set_shrink_factor(uint v) { _shrink_factor = v; }

 public:

  static size_t capacity_until_GC() { return _capacity_until_GC; }
  static void inc_capacity_until_GC(size_t v) { _capacity_until_GC += v; }
  static void dec_capacity_until_GC(size_t v) {
    _capacity_until_GC = _capacity_until_GC > v ? _capacity_until_GC - v : 0;
  }
  static bool expand_after_GC()           { return _expand_after_GC; }
  static void set_expand_after_GC(bool v) { _expand_after_GC = v; }

  static bool should_concurrent_collect() { return _should_concurrent_collect; }
  static void set_should_concurrent_collect(bool v) {
    _should_concurrent_collect = v;
  }

  // The amount to increase the high-water-mark (_capacity_until_GC)
  static size_t delta_capacity_until_GC(size_t word_size);

  // It is expected that this will be called when the current capacity
  // has been used and a GC should be considered.
  static bool should_expand(VirtualSpaceList* vsl, size_t word_size);

  // Calculate the new high-water mark at which to induce
  // a GC.
  static void compute_new_size();
};

#endif // SHARE_VM_MEMORY_METASPACE_HPP
