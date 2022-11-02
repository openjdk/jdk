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
  static void patch_native_pointers();
#endif /* INCLUDE_CDS_JAVA_HEAP */

public:
  // Can this VM map archived heap regions?
  static bool can_use() {
    CDS_JAVA_HEAP_ONLY(return ((UseG1GC || UseEpsilonGC || UseParallelGC || UseSerialGC) && UseCompressedClassPointers);)
    NOT_CDS_JAVA_HEAP(return false;)
  }
  static bool are_archived_strings_available() {
    return closed_regions_mapped();
  }
  static bool is_archived_heap_available() {
    return closed_regions_mapped() && open_regions_mapped();
  }
  static bool are_archived_mirrors_available() {
    return is_archived_heap_available();
  }

  static bool closed_regions_mapped() {
    CDS_JAVA_HEAP_ONLY(return _closed_heap_regions.is_mapped();)
    NOT_CDS_JAVA_HEAP_RETURN_(false);
  }
  static bool open_regions_mapped() {
    CDS_JAVA_HEAP_ONLY(return _open_heap_regions.is_mapped();)
    NOT_CDS_JAVA_HEAP_RETURN_(false);
  }

  static void map_heap_regions(FileMapInfo* map_info) NOT_CDS_JAVA_HEAP_RETURN;
  static void complete_heap_regions_mapping() NOT_CDS_JAVA_HEAP_RETURN;
  static ArchiveOopDecoder* get_oop_decoder(FileMapInfo* map_info) NOT_CDS_JAVA_HEAP_RETURN_(NULL);
  static void patch_heap_embedded_pointers(FileMapInfo* map_info) NOT_CDS_JAVA_HEAP_RETURN;
  static bool is_archived_object(oop object) NOT_CDS_JAVA_HEAP_RETURN_(false);
  static void finish_initialization() NOT_CDS_JAVA_HEAP_RETURN;
  static void fixup_regions() NOT_CDS_JAVA_HEAP_RETURN;
};

#endif // SHARE_CDS_ARCHIVEHEAPLOADER_HPP
