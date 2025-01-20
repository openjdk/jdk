/*
 * Copyright (c) 2014, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "cds/cdsConstants.hpp"
#include "cds/dynamicArchive.hpp"
#include "cds/filemap.hpp"
#include "include/cds.h"
#include "utilities/globalDefinitions.hpp"

CDSConst CDSConstants::offsets[] = {
  { "GenericCDSFileMapHeader::_magic",                    offset_of(GenericCDSFileMapHeader, _magic)                    },
  { "GenericCDSFileMapHeader::_crc",                      offset_of(GenericCDSFileMapHeader, _crc)                      },
  { "GenericCDSFileMapHeader::_version",                  offset_of(GenericCDSFileMapHeader, _version)                  },
  { "GenericCDSFileMapHeader::_header_size",              offset_of(GenericCDSFileMapHeader, _header_size)              },
  { "GenericCDSFileMapHeader::_base_archive_name_offset", offset_of(GenericCDSFileMapHeader, _base_archive_name_offset) },
  { "GenericCDSFileMapHeader::_base_archive_name_size",   offset_of(GenericCDSFileMapHeader, _base_archive_name_size)   },
  { "CDSFileMapHeaderBase::_regions[0]",                  offset_of(CDSFileMapHeaderBase, _regions)                     },
  { "FileMapHeader::_jvm_ident",                          offset_of(FileMapHeader, _jvm_ident)                          },
  { "FileMapHeader::_common_app_classpath_prefix_size",   offset_of(FileMapHeader, _common_app_classpath_prefix_size)   },
  { "CDSFileMapRegion::_crc",                             offset_of(CDSFileMapRegion, _crc)                             },
  { "CDSFileMapRegion::_used",                            offset_of(CDSFileMapRegion, _used)                            },
  { "DynamicArchiveHeader::_base_region_crc",             offset_of(DynamicArchiveHeader, _base_region_crc)             }
};

CDSConst CDSConstants::constants[] = {
  { "static_magic",                 (size_t)CDS_ARCHIVE_MAGIC         },
  { "dynamic_magic",                (size_t)CDS_DYNAMIC_ARCHIVE_MAGIC },
  { "int_size",                     sizeof(int)                       },
  { "CDSFileMapRegion_size",        sizeof(CDSFileMapRegion)          },
  { "static_file_header_size",      sizeof(FileMapHeader)             },
  { "dynamic_archive_header_size",  sizeof(DynamicArchiveHeader)      },
  { "size_t_size",                  sizeof(size_t)                    }
};

size_t CDSConstants::get_cds_offset(const char* name) {
  for (int i = 0; i < (int)ARRAY_SIZE(offsets); i++) {
    if (strcmp(name, offsets[i]._name) == 0) {
        return offsets[i]._value;
    }
  }
  return -1;
}

size_t CDSConstants::get_cds_constant(const char* name) {
  for (int i = 0; i < (int)ARRAY_SIZE(constants); i++) {
    if (strcmp(name, constants[i]._name) == 0) {
        return constants[i]._value;
    }
  }
  return -1;
}
