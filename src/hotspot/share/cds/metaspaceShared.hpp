/*
 * Copyright (c) 2012, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_CDS_METASPACESHARED_HPP
#define SHARE_CDS_METASPACESHARED_HPP

#include "memory/allocation.hpp"
#include "memory/memRegion.hpp"
#include "memory/virtualspace.hpp"
#include "oops/oop.hpp"
#include "utilities/macros.hpp"

class FileMapInfo;
class outputStream;
class SerializeClosure;

template<class E> class GrowableArray;

enum MapArchiveResult {
  MAP_ARCHIVE_SUCCESS,
  MAP_ARCHIVE_MMAP_FAILURE,
  MAP_ARCHIVE_OTHER_FAILURE
};

// Class Data Sharing Support
class MetaspaceShared : AllStatic {
  static ReservedSpace _symbol_rs;  // used only during -Xshare:dump
  static VirtualSpace _symbol_vs;   // used only during -Xshare:dump
  static bool _archive_loading_failed;
  static bool _remapped_readwrite;
  static void* _shared_metaspace_static_top;
  static intx _relocation_delta;
  static char* _requested_base_address;
  static bool _use_optimized_module_handling;

 public:
  enum {
    // core archive spaces
    rw = 0,  // read-write shared space
    ro = 1,  // read-only shared space
    bm = 2,  // relocation bitmaps (freed after file mapping is finished)
    hp = 3,  // heap region
    num_core_region = 2,       // rw and ro
    n_regions = 4              // total number of regions
  };

  static void prepare_for_dumping() NOT_CDS_RETURN;
  static void preload_and_dump() NOT_CDS_RETURN;
#ifdef _LP64
  static void adjust_heap_sizes_for_dumping() NOT_CDS_JAVA_HEAP_RETURN;
#endif

private:
  static void preload_and_dump_impl(TRAPS) NOT_CDS_RETURN;
  static void preload_classes(TRAPS) NOT_CDS_RETURN;

public:
  static Symbol* symbol_rs_base() {
    return (Symbol*)_symbol_rs.base();
  }

  static void initialize_for_static_dump() NOT_CDS_RETURN;
  static void initialize_runtime_shared_and_meta_spaces() NOT_CDS_RETURN;
  static void post_initialize(TRAPS) NOT_CDS_RETURN;

  static void print_on(outputStream* st);

  static void set_archive_loading_failed() {
    _archive_loading_failed = true;
  }

  static void initialize_shared_spaces() NOT_CDS_RETURN;

  // Return true if given address is in the shared metaspace regions (i.e., excluding the
  // mapped heap region.)
  static bool is_in_shared_metaspace(const void* p) {
    return MetaspaceObj::is_shared((const MetaspaceObj*)p);
  }

  static void set_shared_metaspace_range(void* base, void *static_top, void* top) NOT_CDS_RETURN;

  static bool is_shared_dynamic(void* p) NOT_CDS_RETURN_(false);
  static bool is_shared_static(void* p) NOT_CDS_RETURN_(false);

  static void unrecoverable_loading_error(const char* message = nullptr);
  static void unrecoverable_writing_error(const char* message = nullptr);

  static void serialize(SerializeClosure* sc) NOT_CDS_RETURN;

  // JVM/TI RedefineClasses() support:
  // Remap the shared readonly space to shared readwrite, private if
  // sharing is enabled. Simply returns true if sharing is not enabled
  // or if the remapping has already been done by a prior call.
  static bool remap_shared_readonly_as_readwrite() NOT_CDS_RETURN_(true);
  static bool remapped_readwrite() {
    CDS_ONLY(return _remapped_readwrite);
    NOT_CDS(return false);
  }

  static bool try_link_class(JavaThread* current, InstanceKlass* ik);
  static void link_shared_classes(bool jcmd_request, TRAPS) NOT_CDS_RETURN;
  static bool link_class_for_cds(InstanceKlass* ik, TRAPS) NOT_CDS_RETURN_(false);
  static bool may_be_eagerly_linked(InstanceKlass* ik) NOT_CDS_RETURN_(false);

#if INCLUDE_CDS
  // Alignment for the 2 core CDS regions (RW/RO) only.
  // (Heap region alignments are decided by GC).
  static size_t core_region_alignment();
  static void rewrite_nofast_bytecodes_and_calculate_fingerprints(Thread* thread, InstanceKlass* ik);
  // print loaded classes names to file.
  static void dump_loaded_classes(const char* file_name, TRAPS);
#endif

  // Allocate a block of memory from the temporary "symbol" region.
  static char* symbol_space_alloc(size_t num_bytes);

  // This is the base address as specified by -XX:SharedBaseAddress during -Xshare:dump.
  // Both the base/top archives are written using this as their base address.
  //
  // During static dump: _requested_base_address == SharedBaseAddress.
  //
  // During dynamic dump: _requested_base_address is not always the same as SharedBaseAddress:
  // - SharedBaseAddress is used for *reading the base archive*. I.e., CompactHashtable uses
  //   it to convert offsets to pointers to Symbols in the base archive.
  //   The base archive may be mapped to an OS-selected address due to ASLR. E.g.,
  //   you may have SharedBaseAddress == 0x00ff123400000000.
  // - _requested_base_address is used for *writing the output archive*. It's usually
  //   0x800000000 (unless it was set by -XX:SharedBaseAddress during -Xshare:dump).
  static char* requested_base_address() {
    return _requested_base_address;
  }

  // Non-zero if the archive(s) need to be mapped a non-default location due to ASLR.
  static intx relocation_delta() { return _relocation_delta; }

  static bool use_windows_memory_mapping() {
    const bool is_windows = (NOT_WINDOWS(false) WINDOWS_ONLY(true));
    //const bool is_windows = true; // enable this to allow testing the windows mmap semantics on Linux, etc.
    return is_windows;
  }

  // Can we skip some expensive operations related to modules?
  static bool use_optimized_module_handling() { return NOT_CDS(false) CDS_ONLY(_use_optimized_module_handling); }
  static void disable_optimized_module_handling() { _use_optimized_module_handling = false; }

private:
  static void read_extra_data(JavaThread* current, const char* filename) NOT_CDS_RETURN;
  static FileMapInfo* open_static_archive();
  static FileMapInfo* open_dynamic_archive();
  // use_requested_addr: If true (default), attempt to map at the address the
  static MapArchiveResult map_archives(FileMapInfo* static_mapinfo, FileMapInfo* dynamic_mapinfo,
                                       bool use_requested_addr);
  static char* reserve_address_space_for_archives(FileMapInfo* static_mapinfo,
                                                  FileMapInfo* dynamic_mapinfo,
                                                  bool use_archive_base_addr,
                                                  ReservedSpace& total_space_rs,
                                                  ReservedSpace& archive_space_rs,
                                                  ReservedSpace& class_space_rs);
 static void release_reserved_spaces(ReservedSpace& total_space_rs,
                                     ReservedSpace& archive_space_rs,
                                     ReservedSpace& class_space_rs);
  static MapArchiveResult map_archive(FileMapInfo* mapinfo, char* mapped_base_address, ReservedSpace rs);
  static void unmap_archive(FileMapInfo* mapinfo);
  static void get_default_classlist(char* default_classlist, const size_t buf_size);
};
#endif // SHARE_CDS_METASPACESHARED_HPP
