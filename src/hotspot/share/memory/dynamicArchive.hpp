/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_MEMORY_DYNAMICARCHIVE_HPP
#define SHARE_VM_MEMORY_DYNAMICARCHIVE_HPP

#if INCLUDE_CDS

#include "classfile/compactHashtable.hpp"
#include "memory/allocation.hpp"
#include "memory/filemap.hpp"
#include "memory/memRegion.hpp"
#include "memory/virtualspace.hpp"
#include "oops/oop.hpp"
#include "utilities/exceptions.hpp"
#include "utilities/macros.hpp"
#include "utilities/resourceHash.hpp"

class DynamicArchiveHeader : public FileMapHeader {
  friend class CDSOffsets;
private:
  int _base_header_crc;
  int _base_region_crc[MetaspaceShared::n_regions];

public:
  int base_header_crc() const { return _base_header_crc; }
  int base_region_crc(int i) const {
    assert(is_valid_region(i), "must be");
    return _base_region_crc[i];
  }

  void set_base_header_crc(int c) { _base_header_crc = c; }
  void set_base_region_crc(int i, int c) {
    assert(is_valid_region(i), "must be");
    _base_region_crc[i] = c;
  }
};

class DynamicArchive : AllStatic {
  static class DynamicArchiveBuilder* _builder;
  static address original_to_target_impl(address orig_obj);
  static address original_to_buffer_impl(address orig_obj);
  static address buffer_to_target_impl(address buff_obj);

public:
  static void dump();

  // obj is a copy of a MetaspaceObj, stored in the dumping buffer.
  //
  // The return value is the runtime targeted location of this object as
  // mapped from the dynamic archive.
  template <typename T> static T buffer_to_target(T buff_obj) {
    return (T)buffer_to_target_impl(address(buff_obj));
  }

  // obj is an original MetaspaceObj used by the JVM (e.g., a valid Symbol* in the
  // SymbolTable).
  //
  // The return value is the runtime targeted location of this object as
  // mapped from the dynamic archive.
  template <typename T> static T original_to_target(T obj) {
    return (T)original_to_target_impl(address(obj));
  }

  // obj is an original MetaspaceObj use by the JVM (e.g., a valid Symbol* in the
  // SymbolTable).
  //
  // The return value is the location of this object in the dump time
  // buffer space
  template <typename T> static T original_to_buffer(T obj) {
    return (T)original_to_buffer_impl(address(obj));
  }

  // Delta of this object from SharedBaseAddress
  static uintx object_delta_uintx(void* buff_obj);

  // Does obj point to an address inside the runtime target space of the dynamic
  // archive?
  static bool is_in_target_space(void *obj);

  static address map();
  static bool is_mapped();
  static bool validate(FileMapInfo* dynamic_info);
  static void disable();
private:
  static address map_impl(FileMapInfo* mapinfo);
  static void map_failed(FileMapInfo* mapinfo);
};
#endif // INCLUDE_CDS
#endif // SHARE_VM_MEMORY_DYNAMICARCHIVE_HPP
