/*
 * Copyright (c) 2014, 2021, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include <cstddef>
#include "cds.h"
#include "cds/cdsConstants.hpp"
#include "cds/dynamicArchive.hpp"
#include "cds/filemap.hpp"

struct {
  const char* _name;
  size_t _size;
} cds_offsets[] = {
  { "CDSFileMapHeaderBase::_magic",           offset_of(CDSFileMapHeaderBase, _magic)           },
  { "CDSFileMapHeaderBase::_crc",             offset_of(CDSFileMapHeaderBase, _crc)             },
  { "CDSFileMapHeaderBase::_version",         offset_of(CDSFileMapHeaderBase, _version)         },
  { "CDSFileMapHeaderBase::_space[0]",        offset_of(CDSFileMapHeaderBase, _space)           },
  { "FileMapHeader::_jvm_ident",              offset_of(FileMapHeader, _jvm_ident)              },
  { "FileMapHeader::_base_archive_name_size", offset_of(FileMapHeader, _base_archive_name_size) },
  { "CDSFileMapRegion::_crc",                 offset_of(CDSFileMapRegion, _crc)                 },
  { "CDSFileMapRegion::_used",                offset_of(CDSFileMapRegion, _used)                },
  { "DynamicArchiveHeader::_base_region_crc", offset_of(DynamicArchiveHeader, _base_region_crc) }
};

constexpr struct {
  const char* _name;
  int         _value;
} cds_constants[] = {
  { "static_magic",                 (int)CDS_ARCHIVE_MAGIC         },
  { "dynamic_magic",                (int)CDS_DYNAMIC_ARCHIVE_MAGIC },
  { "int_size",                     sizeof(int)                    },
  { "CDSFileMapRegion_size",        sizeof(CDSFileMapRegion)       },
  { "static_file_header_size",      sizeof(FileMapHeader)          },
  { "dynamic_archive_header_size",  sizeof(DynamicArchiveHeader)   },
  { "size_t_size",                  sizeof(size_t)                 }
};

size_t get_cds_offset(const char* name) {
  for (int i = 0; i < (int)(sizeof(cds_offsets)/sizeof(cds_offsets[0])); i++) {
    if (strcmp(name, cds_offsets[i]._name) == 0) {
        return cds_offsets[i]._size;
    }
  }
  return (size_t)-1;
}

int get_cds_constant(const char* name) {
  for (int i = 0; i < (int)(sizeof(cds_constants)/sizeof(cds_constants[0])); i++) {
    if (strcmp(name, cds_constants[i]._name) == 0) {
        return cds_constants[i]._value;
    }
  }
  return -1;
}
