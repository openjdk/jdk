/*
 * Copyright (c) 2012, 2016, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_MEMORY_METASPACESHARED_HPP
#define SHARE_VM_MEMORY_METASPACESHARED_HPP

#include "classfile/compactHashtable.hpp"
#include "memory/allocation.hpp"
#include "memory/memRegion.hpp"
#include "memory/virtualspace.hpp"
#include "utilities/exceptions.hpp"
#include "utilities/macros.hpp"

#define DEFAULT_VTBL_LIST_SIZE          (17)  // number of entries in the shared space vtable list.
#define DEFAULT_VTBL_VIRTUALS_COUNT     (200) // maximum number of virtual functions
// If virtual functions are added to Metadata,
// this number needs to be increased.  Also,
// SharedMiscCodeSize will need to be increased.
// The following 2 sizes were based on
// MetaspaceShared::generate_vtable_methods()
#define DEFAULT_VTBL_METHOD_SIZE        (16)  // conservative size of the mov1 and jmp instructions
// for the x64 platform
#define DEFAULT_VTBL_COMMON_CODE_SIZE   (1*K) // conservative size of the "common_code" for the x64 platform

#define DEFAULT_SHARED_READ_WRITE_SIZE  (NOT_LP64(12*M) LP64_ONLY(16*M))
#define MIN_SHARED_READ_WRITE_SIZE      (NOT_LP64(7*M) LP64_ONLY(12*M))

#define DEFAULT_SHARED_READ_ONLY_SIZE   (NOT_LP64(12*M) LP64_ONLY(16*M))
#define MIN_SHARED_READ_ONLY_SIZE       (NOT_LP64(9*M) LP64_ONLY(10*M))

// the MIN_SHARED_MISC_DATA_SIZE and MIN_SHARED_MISC_CODE_SIZE estimates are based on
// the sizes required for dumping the archive using the default classlist. The sizes
// are multiplied by 1.5 for a safety margin.

#define DEFAULT_SHARED_MISC_DATA_SIZE   (NOT_LP64(2*M) LP64_ONLY(4*M))
#define MIN_SHARED_MISC_DATA_SIZE       (NOT_LP64(1*M) LP64_ONLY(1200*K))

#define DEFAULT_SHARED_MISC_CODE_SIZE   (120*K)
#define MIN_SHARED_MISC_CODE_SIZE       (NOT_LP64(63*K) LP64_ONLY(69*K))
#define DEFAULT_COMBINED_SIZE           (DEFAULT_SHARED_READ_WRITE_SIZE+DEFAULT_SHARED_READ_ONLY_SIZE+DEFAULT_SHARED_MISC_DATA_SIZE+DEFAULT_SHARED_MISC_CODE_SIZE)

// the max size is the MAX size (ie. 0x7FFFFFFF) - the total size of
// the other 3 sections - page size (to avoid overflow in case the final
// size will get aligned up on page size)
#define SHARED_PAGE                     ((size_t)os::vm_page_size())
#define MAX_SHARED_DELTA                (0x7FFFFFFF)
#define MAX_SHARED_READ_WRITE_SIZE      (MAX_SHARED_DELTA-(MIN_SHARED_READ_ONLY_SIZE+MIN_SHARED_MISC_DATA_SIZE+MIN_SHARED_MISC_CODE_SIZE)-SHARED_PAGE)
#define MAX_SHARED_READ_ONLY_SIZE       (MAX_SHARED_DELTA-(MIN_SHARED_READ_WRITE_SIZE+MIN_SHARED_MISC_DATA_SIZE+MIN_SHARED_MISC_CODE_SIZE)-SHARED_PAGE)
#define MAX_SHARED_MISC_DATA_SIZE       (MAX_SHARED_DELTA-(MIN_SHARED_READ_WRITE_SIZE+MIN_SHARED_READ_ONLY_SIZE+MIN_SHARED_MISC_CODE_SIZE)-SHARED_PAGE)
#define MAX_SHARED_MISC_CODE_SIZE       (MAX_SHARED_DELTA-(MIN_SHARED_READ_WRITE_SIZE+MIN_SHARED_READ_ONLY_SIZE+MIN_SHARED_MISC_DATA_SIZE)-SHARED_PAGE)

#define LargeSharedArchiveSize          (300*M)
#define HugeSharedArchiveSize           (800*M)
#define ReadOnlyRegionPercentage        0.4
#define ReadWriteRegionPercentage       0.55
#define MiscDataRegionPercentage        0.03
#define MiscCodeRegionPercentage        0.02
#define LargeThresholdClassCount        5000
#define HugeThresholdClassCount         40000

#define SET_ESTIMATED_SIZE(type, region)                              \
  Shared ##region## Size  = FLAG_IS_DEFAULT(Shared ##region## Size) ? \
    (uintx)(type ## SharedArchiveSize *  region ## RegionPercentage) : Shared ## region ## Size

class FileMapInfo;

class MetaspaceSharedStats VALUE_OBJ_CLASS_SPEC {
public:
  MetaspaceSharedStats() {
    memset(this, 0, sizeof(*this));
  }
  CompactHashtableStats symbol;
  CompactHashtableStats string;
};

class SharedMiscRegion VALUE_OBJ_CLASS_SPEC {
private:
  VirtualSpace _vs;
  char* _alloc_top;
  SharedSpaceType _space_type;

public:
  void initialize(ReservedSpace rs, size_t committed_byte_size,  SharedSpaceType space_type);
  VirtualSpace* virtual_space() {
    return &_vs;
  }
  char* low() const {
    return _vs.low();
  }
  char* alloc_top() const {
    return _alloc_top;
  }
  char* alloc(size_t num_bytes) NOT_CDS_RETURN_(NULL);
};

// Class Data Sharing Support
class MetaspaceShared : AllStatic {

  // CDS support
  static ReservedSpace* _shared_rs;
  static int _max_alignment;
  static MetaspaceSharedStats _stats;
  static bool _link_classes_made_progress;
  static bool _check_classes_made_progress;
  static bool _has_error_classes;
  static bool _archive_loading_failed;
  static address _cds_i2i_entry_code_buffers;
  static size_t  _cds_i2i_entry_code_buffers_size;

  // Used only during dumping.
  static SharedMiscRegion _md;
  static SharedMiscRegion _mc;
 public:
  enum {
    vtbl_list_size         = DEFAULT_VTBL_LIST_SIZE,
    num_virtuals           = DEFAULT_VTBL_VIRTUALS_COUNT,
    vtbl_method_size       = DEFAULT_VTBL_METHOD_SIZE,
    vtbl_common_code_size  = DEFAULT_VTBL_COMMON_CODE_SIZE
  };

  enum {
    ro = 0,  // read-only shared space in the heap
    rw = 1,  // read-write shared space in the heap
    md = 2,  // miscellaneous data for initializing tables, etc.
    mc = 3,  // miscellaneous code - vtable replacement.
    max_strings = 2, // max number of string regions in string space
    num_non_strings = 4, // number of non-string regions
    first_string = num_non_strings, // index of first string region
    n_regions = max_strings + num_non_strings // total number of regions
  };

  // Accessor functions to save shared space created for metadata, which has
  // extra space allocated at the end for miscellaneous data and code.
  static void set_max_alignment(int alignment) {
    CDS_ONLY(_max_alignment = alignment);
  }

  static int max_alignment() {
    CDS_ONLY(return _max_alignment);
    NOT_CDS(return 0);
  }

  static void prepare_for_dumping() NOT_CDS_RETURN;
  static void preload_and_dump(TRAPS) NOT_CDS_RETURN;
  static int preload_and_dump(const char * class_list_path,
                              GrowableArray<Klass*>* class_promote_order,
                              TRAPS) NOT_CDS_RETURN_(0);

  static ReservedSpace* shared_rs() {
    CDS_ONLY(return _shared_rs);
    NOT_CDS(return NULL);
  }

  static void initialize_shared_rs(ReservedSpace* rs) NOT_CDS_RETURN;

  static void set_archive_loading_failed() {
    _archive_loading_failed = true;
  }
  static bool map_shared_spaces(FileMapInfo* mapinfo) NOT_CDS_RETURN_(false);
  static void initialize_shared_spaces() NOT_CDS_RETURN;
  static void fixup_shared_string_regions() NOT_CDS_RETURN;

  // Return true if given address is in the mapped shared space.
  static bool is_in_shared_space(const void* p) NOT_CDS_RETURN_(false);

  // Return true if given address is in the shared region corresponding to the idx
  static bool is_in_shared_region(const void* p, int idx) NOT_CDS_RETURN_(false);

  static bool is_string_region(int idx) NOT_CDS_RETURN_(false);

  static void generate_vtable_methods(void** vtbl_list,
                                      void** vtable,
                                      char** md_top, char* md_end,
                                      char** mc_top, char* mc_end);
  static void serialize(SerializeClosure* sc, GrowableArray<MemRegion> *string_space,
                        size_t* space_size);

  static MetaspaceSharedStats* stats() {
    return &_stats;
  }

  // JVM/TI RedefineClasses() support:
  // Remap the shared readonly space to shared readwrite, private if
  // sharing is enabled. Simply returns true if sharing is not enabled
  // or if the remapping has already been done by a prior call.
  static bool remap_shared_readonly_as_readwrite() NOT_CDS_RETURN_(true);

  static void print_shared_spaces();

  static bool try_link_class(InstanceKlass* ik, TRAPS);
  static void link_one_shared_class(Klass* obj, TRAPS);
  static void check_one_shared_class(Klass* obj);
  static void check_shared_class_loader_type(Klass* obj);
  static void link_and_cleanup_shared_classes(TRAPS);

  static int count_class(const char* classlist_file);
  static void estimate_regions_size() NOT_CDS_RETURN;

  // Allocate a block of memory from the "mc" or "md" regions.
  static char* misc_code_space_alloc(size_t num_bytes) {  return _mc.alloc(num_bytes); }
  static char* misc_data_space_alloc(size_t num_bytes) {  return _md.alloc(num_bytes); }

  static address cds_i2i_entry_code_buffers(size_t total_size);

  static address cds_i2i_entry_code_buffers() {
    return _cds_i2i_entry_code_buffers;
  }
  static size_t cds_i2i_entry_code_buffers_size() {
    return _cds_i2i_entry_code_buffers_size;
  }

  static SharedMiscRegion* misc_code_region() {
    assert(DumpSharedSpaces, "used during dumping only");
    return &_mc;
  }
  static SharedMiscRegion* misc_data_region() {
    assert(DumpSharedSpaces, "used during dumping only");
    return &_md;
  }
};
#endif // SHARE_VM_MEMORY_METASPACESHARED_HPP
