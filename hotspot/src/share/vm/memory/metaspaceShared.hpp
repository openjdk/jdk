/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
#ifndef SHARE_VM_MEMORY_METASPACE_SHARED_HPP
#define SHARE_VM_MEMORY_METASPACE_SHARED_HPP

#include "memory/allocation.hpp"
#include "memory/memRegion.hpp"
#include "runtime/virtualspace.hpp"
#include "utilities/exceptions.hpp"
#include "utilities/macros.hpp"

class FileMapInfo;

// Class Data Sharing Support
class MetaspaceShared : AllStatic {

  // CDS support
  static ReservedSpace* _shared_rs;
  static int _max_alignment;

 public:
  enum {
    vtbl_list_size = 17, // number of entries in the shared space vtable list.
    num_virtuals = 200   // maximum number of virtual functions
                         // If virtual functions are added to Metadata,
                         // this number needs to be increased.  Also,
                         // SharedMiscCodeSize will need to be increased.
  };

  enum {
    ro = 0,  // read-only shared space in the heap
    rw = 1,  // read-write shared space in the heap
    md = 2,  // miscellaneous data for initializing tables, etc.
    mc = 3,  // miscellaneous code - vtable replacement.
    n_regions = 4
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

  static void preload_and_dump(TRAPS) NOT_CDS_RETURN;

  static ReservedSpace* shared_rs() {
    CDS_ONLY(return _shared_rs);
    NOT_CDS(return NULL);
  }

  static void set_shared_rs(ReservedSpace* rs) {
    CDS_ONLY(_shared_rs = rs;)
  }

  static bool map_shared_spaces(FileMapInfo* mapinfo) NOT_CDS_RETURN_(false);
  static void initialize_shared_spaces() NOT_CDS_RETURN;

  // Return true if given address is in the mapped shared space.
  static bool is_in_shared_space(const void* p) NOT_CDS_RETURN_(false);

  static void generate_vtable_methods(void** vtbl_list,
                                      void** vtable,
                                      char** md_top, char* md_end,
                                      char** mc_top, char* mc_end);
  static void serialize(SerializeClosure* sc);

  // JVM/TI RedefineClasses() support:
  // Remap the shared readonly space to shared readwrite, private if
  // sharing is enabled. Simply returns true if sharing is not enabled
  // or if the remapping has already been done by a prior call.
  static bool remap_shared_readonly_as_readwrite() NOT_CDS_RETURN_(true);

  static void print_shared_spaces();
};
#endif // SHARE_VM_MEMORY_METASPACE_SHARED_HPP
