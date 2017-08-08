/*
 * Copyright (c) 2012, 2017, Oracle and/or its affiliates. All rights reserved.
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

#define MAX_SHARED_DELTA                (0x7FFFFFFF)

class FileMapInfo;

class MetaspaceSharedStats VALUE_OBJ_CLASS_SPEC {
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
    mc = 0,  // miscellaneous code for method trampolines
    rw = 1,  // read-write shared space in the heap
    ro = 2,  // read-only shared space in the heap
    md = 3,  // miscellaneous data for initializing tables, etc.
    max_strings = 2, // max number of string regions in string space
    num_non_strings = 4, // number of non-string regions
    first_string = num_non_strings, // index of first string region
    // The optional data region is the last region.
    // Currently it only contains class file data.
    od = max_strings + num_non_strings,
    last_valid_region = od,
    n_regions = od + 1 // total number of regions
  };

  static void prepare_for_dumping() NOT_CDS_RETURN;
  static void preload_and_dump(TRAPS) NOT_CDS_RETURN;
  static int preload_classes(const char * class_list_path,
                             TRAPS) NOT_CDS_RETURN_(0);

  static ReservedSpace* shared_rs() {
    CDS_ONLY(return &_shared_rs);
    NOT_CDS(return NULL);
  }
  static void commit_shared_space_to(char* newtop) NOT_CDS_RETURN;
  static size_t core_spaces_size() {
    return _core_spaces_size;
  }
  static void initialize_shared_rs() NOT_CDS_RETURN;

  // Delta of this object from the bottom of the archive.
  static uintx object_delta(void* obj) {
    assert(DumpSharedSpaces, "supported only for dumping");
    assert(shared_rs()->contains(obj), "must be");
    address base_address = address(shared_rs()->base());
    uintx delta = address(obj) - base_address;
    return delta;
  }

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
  static bool is_string_region(int idx) {
      CDS_ONLY(return (idx >= first_string && idx < first_string + max_strings));
      NOT_CDS(return false);
  }
  static bool is_in_trampoline_frame(address addr) NOT_CDS_RETURN_(false);

  static void allocate_cpp_vtable_clones();
  static intptr_t* clone_cpp_vtables(intptr_t* p);
  static void zero_cpp_vtable_clones_for_writing();
  static void patch_cpp_vtable_pointers();
  static bool is_valid_shared_method(const Method* m) NOT_CDS_RETURN_(false);
  static void serialize(SerializeClosure* sc);


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

  static void print_shared_spaces();

  static bool try_link_class(InstanceKlass* ik, TRAPS);
  static void link_and_cleanup_shared_classes(TRAPS);
  static void check_shared_class_loader_type(Klass* obj);

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
};
#endif // SHARE_VM_MEMORY_METASPACESHARED_HPP
