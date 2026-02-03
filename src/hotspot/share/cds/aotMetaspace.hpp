/*
 * Copyright (c) 2012, 2026, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_CDS_AOTMETASPACE_HPP
#define SHARE_CDS_AOTMETASPACE_HPP

#include "memory/allocation.hpp"
#include "memory/memRegion.hpp"
#include "memory/reservedSpace.hpp"
#include "memory/virtualspace.hpp"
#include "oops/oop.hpp"
#include "utilities/macros.hpp"

class ArchiveBuilder;
class AOTMappedHeapInfo;
class AOTStreamedHeapInfo;
class FileMapInfo;
class Method;
class outputStream;
class SerializeClosure;
class StaticArchiveBuilder;

template<class E> class Array;

enum MapArchiveResult {
  MAP_ARCHIVE_SUCCESS,
  MAP_ARCHIVE_MMAP_FAILURE,
  MAP_ARCHIVE_OTHER_FAILURE
};

// Class Data Sharing Support
class AOTMetaspace : AllStatic {
  static ReservedSpace _symbol_rs;  // used only during -Xshare:dump
  static VirtualSpace _symbol_vs;   // used only during -Xshare:dump
  static bool _archive_loading_failed;
  static bool _remapped_readwrite;
  static void* _aot_metaspace_static_top;
  static intx _relocation_delta;
  static char* _requested_base_address;
  static bool _use_optimized_module_handling;
  static Array<Method*>* _archived_method_handle_intrinsics;
  static int volatile _preimage_static_archive_dumped;
  static FileMapInfo* _output_mapinfo;

 public:
  enum {
    // core archive spaces
    rw = 0,  // read-write
    ro = 1,  // read-only
    bm = 2,  // relocation bitmaps (freed after file mapping is finished)
    hp = 3,  // heap region
    ac = 4,  // aot code
    num_core_region = 2,       // rw and ro
    n_regions = 5              // total number of regions
  };

  static void dump_static_archive(TRAPS) NOT_CDS_RETURN;
#ifdef _LP64
  static void adjust_heap_sizes_for_dumping() NOT_CDS_JAVA_HEAP_RETURN;
#endif

private:
  static void exercise_runtime_cds_code(TRAPS) NOT_CDS_RETURN;
  static void dump_static_archive_impl(StaticArchiveBuilder& builder, TRAPS) NOT_CDS_RETURN;
  static void load_classes(TRAPS) NOT_CDS_RETURN;

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
  static bool in_aot_cache(const void* p) {
    return MetaspaceObj::in_aot_cache((const MetaspaceObj*)p);
  }

  static void set_aot_metaspace_range(void* base, void *static_top, void* top) NOT_CDS_RETURN;

  // inside the metaspace of the AOT cache, or the static CDS archive
  static bool in_aot_cache_static_region(void* p) NOT_CDS_RETURN_(false);

  // inside the metaspace of the dynamic static CDS archive
  static bool in_aot_cache_dynamic_region(void* p) NOT_CDS_RETURN_(false);

  static bool preimage_static_archive_dumped() NOT_CDS_RETURN_(false);

  static void unrecoverable_loading_error(const char* message = "unrecoverable error");
  static void report_loading_error(const char* format, ...) ATTRIBUTE_PRINTF(1, 0);
  static void unrecoverable_writing_error(const char* message = nullptr);
  static void writing_error(const char* message = nullptr);

  static void make_method_handle_intrinsics_shareable() NOT_CDS_RETURN;
  static void write_method_handle_intrinsics() NOT_CDS_RETURN;
  static Array<Method*>* archived_method_handle_intrinsics() { return _archived_method_handle_intrinsics; }
  static void early_serialize(SerializeClosure* sc) NOT_CDS_RETURN;
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
  static void link_all_loaded_classes(JavaThread* current);
  static void link_shared_classes(TRAPS) NOT_CDS_RETURN;
  static bool may_be_eagerly_linked(InstanceKlass* ik) NOT_CDS_RETURN_(false);

#if INCLUDE_CDS
  // Alignment for the 2 core CDS regions (RW/RO) only.
  // (Heap region alignments are decided by GC).
  static size_t core_region_alignment();
  static size_t protection_zone_size();
  static void rewrite_bytecodes_and_calculate_fingerprints(Thread* thread, InstanceKlass* ik);
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
  static void fork_and_dump_final_static_archive(TRAPS);
  static void open_output_mapinfo();
  static bool write_static_archive(ArchiveBuilder* builder,
                                   FileMapInfo* map_info,
                                   AOTMappedHeapInfo* mapped_heap_info,
                                   AOTStreamedHeapInfo* streamed_heap_info);
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
#endif // SHARE_CDS_AOTMETASPACE_HPP
