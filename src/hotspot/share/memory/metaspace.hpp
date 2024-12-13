/*
 * Copyright (c) 2011, 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2017, 2021 SAP SE. All rights reserved.
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
#include "memory/virtualspace.hpp"
#include "runtime/globals.hpp"
#include "utilities/exceptions.hpp"
#include "utilities/globalDefinitions.hpp"

class ClassLoaderData;
class MetaspaceShared;
class MetaspaceTracer;
class Mutex;
class outputStream;

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
    MetaspaceTypeCount
  };

private:

  static const MetaspaceTracer* _tracer;

  // For quick pointer testing: extent of class space; nullptr if no class space.
  static const void* _class_space_start;
  static const void* _class_space_end;

  static bool _initialized;

public:

  static const MetaspaceTracer* tracer() { return _tracer; }

 private:

#ifdef _LP64

  // Reserve a range of memory that is to contain narrow Klass IDs. If "try_in_low_address_ranges"
  // is true, we will attempt to reserve memory suitable for zero-based encoding.
  static ReservedSpace reserve_address_space_for_compressed_classes(size_t size, bool optimize_for_zero_base);

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

  // Minimum allocation alignment, in bytes. All MetaData shall be aligned correctly
  // to be able to hold 64-bit data types. Unlike malloc, we don't care for larger
  // data types.
  static constexpr size_t min_allocation_alignment_bytes = sizeof(uint64_t);

  // Minimum allocation alignment, in words, Metaspace observes.
  static constexpr size_t min_allocation_alignment_words = min_allocation_alignment_bytes / BytesPerWord;

  // Every allocation will get rounded up to the minimum word size.
  static constexpr size_t min_allocation_word_size = min_allocation_alignment_words;

  static MetaWord* allocate(ClassLoaderData* loader_data, size_t word_size,
                            MetaspaceObj::Type type, bool use_class_space, TRAPS);

  // Non-TRAPS version of allocate which can be called by a non-Java thread, that returns
  // null on failure.
  static MetaWord* allocate(ClassLoaderData* loader_data, size_t word_size,
                            MetaspaceObj::Type type, bool use_class_space);

  // Returns true if the pointer points into class space, non-class metaspace, or the
  // metadata portion of the CDS archive.
  static bool contains(const void* ptr) {
    return is_in_shared_metaspace(ptr) || // in cds
           is_in_class_space(ptr) ||      // in class space
           is_in_nonclass_metaspace(ptr); // in one of the non-class regions?
  }

  // Returns true if the pointer points into class space or into non-class metaspace
  static bool contains_non_shared(const void* ptr) {
    return is_in_class_space(ptr) ||      // in class space
           is_in_nonclass_metaspace(ptr); // in one of the non-class regions?
  }

  // Returns true if pointer points into the CDS klass region.
  static bool is_in_shared_metaspace(const void* ptr);

  // Returns true if pointer points into one of the non-class-space metaspace regions.
  static bool is_in_nonclass_metaspace(const void* ptr);

  // Returns true if pointer points into class space, false if it doesn't or if
  // there is no class space. Class space is a contiguous region, which is why
  // two address comparisons are enough.
  static inline bool is_in_class_space(const void* ptr) {
    return ptr < _class_space_end && ptr >= _class_space_start;
  }

  // Free empty virtualspaces
  static void purge(bool classes_unloaded);

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


#endif // SHARE_MEMORY_METASPACE_HPP
