/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_CDS_ARCHIVEHEAPLOADER_HPP
#define SHARE_CDS_ARCHIVEHEAPLOADER_HPP

#include "cds/archiveUtils.hpp"
#include "gc/shared/gc_globals.hpp"
#include "memory/allocation.hpp"
#include "memory/allStatic.hpp"
#include "runtime/globals.hpp"
#include "oops/oopsHierarchy.hpp"
#include "memory/memRegion.hpp"
#include "utilities/macros.hpp"

class  FileMapInfo;
struct LoadedArchiveHeapRegion;

class ArchiveHeapLoader : AllStatic {
#if INCLUDE_CDS_JAVA_HEAP
private:
  static ArchiveOopDecoder* _oop_decoder;
  static ArchiveHeapRegions _closed_heap_regions;
  static ArchiveHeapRegions _open_heap_regions;

  static bool _heap_pointers_need_patching;

  static void init_archive_heap_regions(FileMapInfo* map_info, int first_region_idx, int last_region_idx, ArchiveHeapRegions* heap_regions);
  static void cleanup_regions(FileMapInfo* map_info, ArchiveHeapRegions* heap_regions);
  static void cleanup(FileMapInfo* map_info);
  static bool get_heap_range_for_archive_regions(ArchiveHeapRegions* heap_regions, bool is_open);
  static bool is_pointer_patching_needed(FileMapInfo* map_info);
  static void log_mapped_regions(ArchiveHeapRegions* heap_regions, bool is_open);
  static bool map_heap_regions(FileMapInfo* map_info, ArchiveHeapRegions* heap_regions);
  static bool dealloc_heap_regions(ArchiveHeapRegions* heap_regions);
  static void patch_embedded_pointers(FileMapInfo* map_info, MemRegion region, address oopmap, size_t oopmap_size_in_bits);
  static void patch_heap_embedded_pointers(FileMapInfo* map_info, ArchiveHeapRegions* heap_regions);
  static void fill_failed_mapped_regions();
#endif /* INCLUDE_CDS_JAVA_HEAP */

public:
  // At runtime, heap regions in the CDS archive can be used in two different ways,
  // depending on the GC type:
  // - Mapped: (G1 only) the regions are directly mapped into the Java heap
  // - Loaded: At VM start-up, the objects in the heap regions are copied into the
  //           Java heap. This is easier to implement than mapping but
  //           slightly less efficient, as the embedded pointers need to be relocated.
  static bool can_use() { return can_map() || can_load(); }

  // Can this VM map archived heap regions? Currently only G1+compressed{oops,cp}
  static bool can_map() {
    CDS_JAVA_HEAP_ONLY(return (UseG1GC && UseCompressedClassPointers);)
    NOT_CDS_JAVA_HEAP(return false;)
  }
  static bool is_mapped() {
    return closed_regions_mapped() && open_regions_mapped();
  }

  // Can this VM load the objects from archived heap regions into the heap at start-up?
  static bool can_load()  NOT_CDS_JAVA_HEAP_RETURN_(false);
  static void finish_initialization() NOT_CDS_JAVA_HEAP_RETURN;
  static bool is_loaded() {
    CDS_JAVA_HEAP_ONLY(return _is_loaded;)
    NOT_CDS_JAVA_HEAP(return false;)
  }

  static bool are_archived_strings_available() {
    return is_loaded() || closed_regions_mapped();
  }
  static bool are_archived_mirrors_available() {
    return is_fully_available();
  }
  static bool is_fully_available() {
    return is_loaded() || is_mapped();
  }

  static bool closed_regions_mapped() {
    CDS_JAVA_HEAP_ONLY(return _closed_heap_regions.is_mapped();)
    NOT_CDS_JAVA_HEAP_RETURN_(false);
  }
  static bool open_regions_mapped() {
    CDS_JAVA_HEAP_ONLY(return _open_heap_regions.is_mapped();)
    NOT_CDS_JAVA_HEAP_RETURN_(false);
  }

  // NarrowOops stored in the CDS archive may use a different encoding scheme
  // than CompressedOops::{base,shift} -- see FileMapInfo::map_heap_regions_impl.
  // To decode them, do not use CompressedOops::decode_not_null. Use this
  // function instead.
  inline static oop decode_from_archive(narrowOop v) NOT_CDS_JAVA_HEAP_RETURN_(NULL);

  static void init_narrow_oop_decoding(address base, int shift) NOT_CDS_JAVA_HEAP_RETURN;

  static void patch_embedded_pointers(MemRegion region, address oopmap,
                                      size_t oopmap_in_bits) NOT_CDS_JAVA_HEAP_RETURN;

  static void fixup_regions() NOT_CDS_JAVA_HEAP_RETURN;

  static void map_heap_regions(FileMapInfo* map_info) NOT_CDS_JAVA_HEAP_RETURN;
  static void complete_heap_regions_mapping() NOT_CDS_JAVA_HEAP_RETURN;
  static ArchiveOopDecoder* get_oop_decoder(FileMapInfo* map_info) NOT_CDS_JAVA_HEAP_RETURN_(NULL);
  static void patch_heap_embedded_pointers(FileMapInfo* map_info) NOT_CDS_JAVA_HEAP_RETURN;

#if INCLUDE_CDS_JAVA_HEAP
private:
  static bool _closed_regions_mapped;
  static bool _open_regions_mapped;
  static bool _is_loaded;

  // Support for loaded archived heap. These are cached values from
  // LoadedArchiveHeapRegion's.
  static uintptr_t _dumptime_base_0;
  static uintptr_t _dumptime_base_1;
  static uintptr_t _dumptime_base_2;
  static uintptr_t _dumptime_base_3;
  static uintptr_t _dumptime_top;
  static intx _runtime_offset_0;
  static intx _runtime_offset_1;
  static intx _runtime_offset_2;
  static intx _runtime_offset_3;

  static uintptr_t _loaded_heap_bottom;
  static uintptr_t _loaded_heap_top;
  static bool _loading_failed;

  // UseCompressedOops only: Used by decode_from_archive
  static address _narrow_oop_base;
  static int     _narrow_oop_shift;

  static int init_loaded_regions(FileMapInfo* mapinfo, LoadedArchiveHeapRegion* loaded_regions,
                                 MemRegion& archive_space);
  static void sort_loaded_regions(LoadedArchiveHeapRegion* loaded_regions, int num_loaded_regions,
                                  uintptr_t buffer);
  static bool load_regions(FileMapInfo* mapinfo, LoadedArchiveHeapRegion* loaded_regions,
                           int num_loaded_regions, uintptr_t buffer);
  static void init_loaded_heap_relocation(LoadedArchiveHeapRegion* reloc_info,
                                          int num_loaded_regions);
  static void patch_native_pointers();
  static void finish_loaded_heap();
  static void verify_loaded_heap();
  static void fill_failed_loaded_heap();

  static bool is_in_loaded_heap(uintptr_t o) {
    return (_loaded_heap_bottom <= o && o < _loaded_heap_top);
  }

public:

  static bool load_heap_regions(FileMapInfo* mapinfo);
  static void assert_in_loaded_heap(uintptr_t o) {
    assert(is_in_loaded_heap(o), "must be");
  }

#endif // INCLUDE_CDS_JAVA_HEAP

};

#endif // SHARE_CDS_ARCHIVEHEAPLOADER_HPP
