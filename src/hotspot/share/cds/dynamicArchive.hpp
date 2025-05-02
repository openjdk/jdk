/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_CDS_DYNAMICARCHIVE_HPP
#define SHARE_CDS_DYNAMICARCHIVE_HPP

#include "cds/filemap.hpp"
#include "classfile/compactHashtable.hpp"
#include "memory/allStatic.hpp"
#include "memory/memRegion.hpp"
#include "oops/array.hpp"
#include "oops/oop.hpp"
#include "utilities/exceptions.hpp"
#include "utilities/growableArray.hpp"
#include "utilities/macros.hpp"

#if INCLUDE_CDS

class DynamicArchiveHeader : public FileMapHeader {
  friend class CDSConstants;
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
  void print(outputStream* st);
};

class DynamicArchive : AllStatic {
private:
  static GrowableArray<ObjArrayKlass*>* _array_klasses;
  static Array<ObjArrayKlass*>* _dynamic_archive_array_klasses;
public:
  static void dump_for_jcmd(const char* archive_name, TRAPS);
  static void dump_at_exit(JavaThread* current);
  static void dump_impl(bool jcmd_request, const char* archive_name, TRAPS);
  static bool is_mapped() { return FileMapInfo::dynamic_info() != nullptr; }
  static bool validate(FileMapInfo* dynamic_info);
  static void dump_array_klasses();
  static void setup_array_klasses();
  static void append_array_klass(ObjArrayKlass* oak);
  static void serialize_array_klasses(SerializeClosure* soc);
  static void make_array_klasses_shareable();
  static void post_dump();
  static int  num_array_klasses();
};
#endif // INCLUDE_CDS
#endif // SHARE_CDS_DYNAMICARCHIVE_HPP
