/*
 * Copyright (c) 2012, 2019, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_MEMORY_METASPACESHARED_HPP
#define SHARE_MEMORY_METASPACESHARED_HPP

#include "classfile/compactHashtable.hpp"
#include "memory/allocation.hpp"
#include "memory/memRegion.hpp"
#include "memory/virtualspace.hpp"
#include "oops/oop.hpp"
#include "utilities/exceptions.hpp"
#include "utilities/macros.hpp"
#include "utilities/resourceHash.hpp"

#define MAX_SHARED_DELTA                (0x7FFFFFFF)

class FileMapInfo;

class MetaspaceSharedStats {
public:
  MetaspaceSharedStats() {
    memset(this, 0, sizeof(*this));
  }
  CompactHashtableStats symbol;
  CompactHashtableStats string;
};

// Class Data Sharing Support
class MetaspaceShared : AllStatic {

  // CDS support
  static ReservedSpace _shared_rs;
  static VirtualSpace _shared_vs;
  static int _max_alignment;
  static MetaspaceSharedStats _stats;
  static bool _has_error_classes;
  static bool _archive_loading_failed;
  static bool _remapped_readwrite;
  static address _cds_i2i_entry_code_buffers;
  static size_t  _cds_i2i_entry_code_buffers_size;
  static size_t  _core_spaces_size;
 public:
  enum {
    // core archive spaces
    mc = 0,  // miscellaneous code for method trampolines
    rw = 1,  // read-write shared space in the heap
    ro = 2,  // read-only shared space in the heap
    md = 3,  // miscellaneous data for initializing tables, etc.
    num_core_spaces = 4, // number of non-string regions
    num_non_heap_spaces = 4,

    // mapped java heap regions
    first_closed_archive_heap_region = md + 1,
    max_closed_archive_heap_region = 2,
    last_closed_archive_heap_region = first_closed_archive_heap_region + max_closed_archive_heap_region - 1,
    first_open_archive_heap_region = last_closed_archive_heap_region + 1,
    max_open_archive_heap_region = 2,
    last_open_archive_heap_region = first_open_archive_heap_region + max_open_archive_heap_region - 1,

    last_valid_region = last_open_archive_heap_region,
    n_regions =  last_valid_region + 1 // total number of regions
  };

  static void prepare_for_dumping() NOT_CDS_RETURN;
  static void preload_and_dump(TRAPS) NOT_CDS_RETURN;
  static int preload_classes(const char * class_list_path,
                             TRAPS) NOT_CDS_RETURN_(0);

  static GrowableArray<Klass*>* collected_klasses();

  static ReservedSpace* shared_rs() {
    CDS_ONLY(return &_shared_rs);
    NOT_CDS(return NULL);
  }
  static void commit_shared_space_to(char* newtop) NOT_CDS_RETURN;
  static size_t core_spaces_size() {
    assert(DumpSharedSpaces || UseSharedSpaces, "sanity");
    assert(_core_spaces_size != 0, "sanity");
    return _core_spaces_size;
  }
  static void initialize_dumptime_shared_and_meta_spaces() NOT_CDS_RETURN;
  static void initialize_runtime_shared_and_meta_spaces() NOT_CDS_RETURN;
  static void post_initialize(TRAPS) NOT_CDS_RETURN;

  // Delta of this object from the bottom of the archive.
  static uintx object_delta_uintx(void* obj) {
    assert(DumpSharedSpaces, "supported only for dumping");
    assert(shared_rs()->contains(obj), "must be");
    address base_address = address(shared_rs()->base());
    uintx deltax = address(obj) - base_address;
    return deltax;
  }

  static u4 object_delta_u4(void* obj) {
    // offset is guaranteed to be less than MAX_SHARED_DELTA in DumpRegion::expand_top_to()
    uintx deltax = object_delta_uintx(obj);
    guarantee(deltax <= MAX_SHARED_DELTA, "must be 32-bit offset");
    return (u4)deltax;
  }

  static void set_archive_loading_failed() {
    _archive_loading_failed = true;
  }
  static bool map_shared_spaces(FileMapInfo* mapinfo) NOT_CDS_RETURN_(false);
  static void initialize_shared_spaces() NOT_CDS_RETURN;

  // Return true if given address is in the shared metaspace regions (i.e., excluding any
  // mapped shared heap regions.)
  static bool is_in_shared_metaspace(const void* p) {
    // If no shared metaspace regions are mapped, MetaspceObj::_shared_metaspace_{base,top} will
    // both be NULL and all values of p will be rejected quickly.
    return (p < MetaspaceObj::shared_metaspace_top() && p >= MetaspaceObj::shared_metaspace_base());
  }

  // Return true if given address is in the shared region corresponding to the idx
  static bool is_in_shared_region(const void* p, int idx) NOT_CDS_RETURN_(false);

  static bool is_in_trampoline_frame(address addr) NOT_CDS_RETURN_(false);

  static void allocate_cpp_vtable_clones();
  static intptr_t* clone_cpp_vtables(intptr_t* p);
  static void zero_cpp_vtable_clones_for_writing();
  static void patch_cpp_vtable_pointers();
  static bool is_valid_shared_method(const Method* m) NOT_CDS_RETURN_(false);
  static void serialize(SerializeClosure* sc) NOT_CDS_RETURN;

  static MetaspaceSharedStats* stats() {
    return &_stats;
  }

  static void report_out_of_space(const char* name, size_t needed_bytes);

  // JVM/TI RedefineClasses() support:
  // Remap the shared readonly space to shared readwrite, private if
  // sharing is enabled. Simply returns true if sharing is not enabled
  // or if the remapping has already been done by a prior call.
  static bool remap_shared_readonly_as_readwrite() NOT_CDS_RETURN_(true);
  static bool remapped_readwrite() {
    CDS_ONLY(return _remapped_readwrite);
    NOT_CDS(return false);
  }

  static bool try_link_class(InstanceKlass* ik, TRAPS);
  static void link_and_cleanup_shared_classes(TRAPS);

  // Allocate a block of memory from the "mc", "ro", or "rw" regions.
  static char* misc_code_space_alloc(size_t num_bytes);
  static char* read_only_space_alloc(size_t num_bytes);

  template <typename T>
  static Array<T>* new_ro_array(int length) {
#if INCLUDE_CDS
    size_t byte_size = Array<T>::byte_sizeof(length, sizeof(T));
    Array<T>* array = (Array<T>*)read_only_space_alloc(byte_size);
    array->initialize(length);
    return array;
#else
    return NULL;
#endif
  }

  static address cds_i2i_entry_code_buffers(size_t total_size);

  static address cds_i2i_entry_code_buffers() {
    return _cds_i2i_entry_code_buffers;
  }
  static size_t cds_i2i_entry_code_buffers_size() {
    return _cds_i2i_entry_code_buffers_size;
  }
  static void relocate_klass_ptr(oop o);

  static Klass* get_relocated_klass(Klass *k);

private:
  static void read_extra_data(const char* filename, TRAPS) NOT_CDS_RETURN;
};
#endif // SHARE_MEMORY_METASPACESHARED_HPP
