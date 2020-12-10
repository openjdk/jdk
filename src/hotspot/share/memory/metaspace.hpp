/*
 * Copyright (c) 2011, 2020, Oracle and/or its affiliates. All rights reserved.
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
#ifndef SHARE_MEMORY_METASPACE_HPP
#define SHARE_MEMORY_METASPACE_HPP

#include "memory/allocation.hpp"
#include "memory/memRegion.hpp"
#include "memory/metaspaceChunkFreeListSummary.hpp"
#include "memory/virtualspace.hpp"
#include "runtime/globals.hpp"
#include "utilities/exceptions.hpp"
#include "utilities/globalDefinitions.hpp"

class ClassLoaderData;
class MetaspaceShared;
class MetaspaceTracer;
class Mutex;
class outputStream;

namespace metaspace {
  class MetaspaceSizesSnapshot;
}

////////////////// Metaspace ///////////////////////

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
    ClassMirrorHolderMetaspaceType = BootMetaspaceType + 1,
    ReflectionMetaspaceType = ClassMirrorHolderMetaspaceType + 1,
    MetaspaceTypeCount
  };

private:

  DEBUG_ONLY(static bool   _frozen;)

  static const MetaspaceTracer* _tracer;

  static bool _initialized;

public:

  static const MetaspaceTracer* tracer() { return _tracer; }
  static void freeze() {
    assert(DumpSharedSpaces, "sanity");
    DEBUG_ONLY(_frozen = true;)
  }
  static void assert_not_frozen() {
    assert(!_frozen, "sanity");
  }

 private:

#ifdef _LP64

  // Reserve a range of memory at an address suitable for en/decoding narrow
  // Klass pointers (see: CompressedClassPointers::is_valid_base()).
  // The returned address shall both be suitable as a compressed class pointers
  //  base, and aligned to Metaspace::reserve_alignment (which is equal to or a
  //  multiple of allocation granularity).
  // On error, returns an unreserved space.
  static ReservedSpace reserve_address_space_for_compressed_classes(size_t size);

  // Given a prereserved space, use that to set up the compressed class space list.
  static void initialize_class_space(ReservedSpace rs);

  // Returns true if class space has been setup (initialize_class_space).
  static bool class_space_is_initialized();

#endif

 public:

  static void ergo_initialize();
  static void global_initialize();
  static void post_initialize();

  // Alignment, in bytes, of metaspace mappings
  static size_t reserve_alignment()       { return reserve_alignment_words() * BytesPerWord; }
  // Alignment, in words, of metaspace mappings
  static size_t reserve_alignment_words();

  // The granularity at which Metaspace is committed and uncommitted.
  // (Todo: Why does this have to be exposed?)
  static size_t commit_alignment()        { return commit_alignment_words() * BytesPerWord; }
  static size_t commit_alignment_words();

  // The largest possible single allocation
  static size_t max_allocation_word_size();

  static MetaWord* allocate(ClassLoaderData* loader_data, size_t word_size,
                            MetaspaceObj::Type type, TRAPS);

  static bool contains(const void* ptr);
  static bool contains_non_shared(const void* ptr);

  // Free empty virtualspaces
  static void purge();

  static void report_metadata_oome(ClassLoaderData* loader_data, size_t word_size,
                                   MetaspaceObj::Type type, MetadataType mdtype, TRAPS);

  static const char* metadata_type_name(Metaspace::MetadataType mdtype);

  static void print_compressed_class_space(outputStream* st) NOT_LP64({});

  // Return TRUE only if UseCompressedClassPointers is True.
  static bool using_class_space() {
    return NOT_LP64(false) LP64_ONLY(UseCompressedClassPointers);
  }

  static bool is_class_space_allocation(MetadataType mdType) {
    return mdType == ClassType && using_class_space();
  }

  static bool initialized();

};

////////////////// MetaspaceGC ///////////////////////

// Metaspace are deallocated when their class loader are GC'ed.
// This class implements a policy for inducing GC's to recover
// Metaspaces.

class MetaspaceGCThresholdUpdater : public AllStatic {
 public:
  enum Type {
    ComputeNewSize,
    ExpandAndAllocate,
    Last
  };

  static const char* to_string(MetaspaceGCThresholdUpdater::Type updater) {
    switch (updater) {
      case ComputeNewSize:
        return "compute_new_size";
      case ExpandAndAllocate:
        return "expand_and_allocate";
      default:
        assert(false, "Got bad updater: %d", (int) updater);
        return NULL;
    };
  }
};

class MetaspaceGC : public AllStatic {

  // The current high-water-mark for inducing a GC.
  // When committed memory of all metaspaces reaches this value,
  // a GC is induced and the value is increased. Size is in bytes.
  static volatile size_t _capacity_until_GC;
  static uint _shrink_factor;

  static size_t shrink_factor() { return _shrink_factor; }
  void set_shrink_factor(uint v) { _shrink_factor = v; }

 public:

  static void initialize();
  static void post_initialize();

  static size_t capacity_until_GC();
  static bool inc_capacity_until_GC(size_t v,
                                    size_t* new_cap_until_GC = NULL,
                                    size_t* old_cap_until_GC = NULL,
                                    bool* can_retry = NULL);
  static size_t dec_capacity_until_GC(size_t v);

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

class MetaspaceUtils : AllStatic {
public:

  // Committed space actually in use by Metadata
  static size_t used_words();
  static size_t used_words(Metaspace::MetadataType mdtype);

  // Space committed for Metaspace
  static size_t committed_words();
  static size_t committed_words(Metaspace::MetadataType mdtype);

  // Space reserved for Metaspace
  static size_t reserved_words();
  static size_t reserved_words(Metaspace::MetadataType mdtype);

  // _bytes() variants for convenience...
  static size_t used_bytes()                                    { return used_words() * BytesPerWord; }
  static size_t used_bytes(Metaspace::MetadataType mdtype)      { return used_words(mdtype) * BytesPerWord; }
  static size_t committed_bytes()                               { return committed_words() * BytesPerWord; }
  static size_t committed_bytes(Metaspace::MetadataType mdtype) { return committed_words(mdtype) * BytesPerWord; }
  static size_t reserved_bytes()                                { return reserved_words() * BytesPerWord; }
  static size_t reserved_bytes(Metaspace::MetadataType mdtype)  { return reserved_words(mdtype) * BytesPerWord; }

  // (See JDK-8251342). Implement or Consolidate.
  static MetaspaceChunkFreeListSummary chunk_free_list_summary(Metaspace::MetadataType mdtype) {
    return MetaspaceChunkFreeListSummary(0,0,0,0,0,0,0,0);
  }

  // Log change in used metadata.
  static void print_metaspace_change(const metaspace::MetaspaceSizesSnapshot& pre_meta_values);

  // This will print out a basic metaspace usage report but
  // unlike print_report() is guaranteed not to lock or to walk the CLDG.
  static void print_basic_report(outputStream* st, size_t scale = 0);

  // Prints a report about the current metaspace state.
  // Function will walk the CLDG and will lock the expand lock; if that is not
  // convenient, use print_basic_report() instead.
  static void print_report(outputStream* out, size_t scale = 0);

  static void print_on(outputStream * out);

  DEBUG_ONLY(static void verify();)

};

#endif // SHARE_MEMORY_METASPACE_HPP
