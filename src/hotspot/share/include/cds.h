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

#ifndef SHARE_INCLUDE_CDS_H
#define SHARE_INCLUDE_CDS_H

#include <stddef.h>

// This file declares the CDS data structures that are used by the HotSpot Serviceability Agent
// (see C sources inside src/jdk.hotspot.agent).
//
// We should use only standard C types. Do not use custom types such as bool, intx,
// etc, to avoid introducing unnecessary dependencies to other HotSpot type declarations.
//
// Also, this is a C header file. Do not use C++ here.

#define NUM_CDS_REGIONS 7 // this must be the same as MetaspaceShared::n_regions
#define CDS_ARCHIVE_MAGIC 0xf00baba2
#define CDS_DYNAMIC_ARCHIVE_MAGIC 0xf00baba8
#define CDS_GENERIC_HEADER_SUPPORTED_MIN_VERSION 13
#define CURRENT_CDS_ARCHIVE_VERSION 14

typedef struct CDSFileMapRegion {
  int     _crc;               // CRC checksum of this region.
  int     _read_only;         // read only region?
  int     _allow_exec;        // executable code in this region?
  int     _is_heap_region;    // Used by SA and debug build.
  int     _is_bitmap_region;  // Relocation bitmap for RO/RW regions (used by SA and debug build).
  int     _mapped_from_file;  // Is this region mapped from a file?
                              // If false, this region was initialized using ::read().
  size_t  _file_offset;       // Data for this region starts at this offset in the archive file.
  size_t  _mapping_offset;    // This region should be mapped at this offset from the base address
                              // - for non-heap regions, the base address is SharedBaseAddress
                              // - for heap regions, the base address is the compressed oop encoding base
  size_t  _used;              // Number of bytes actually used by this region (excluding padding bytes added
                              // for alignment purposed.
  size_t  _oopmap_offset;     // Bitmap for relocating embedded oops (offset from SharedBaseAddress).
  size_t  _oopmap_size_in_bits;
  char*   _mapped_base;       // Actually mapped address (NULL if this region is not mapped).
} CDSFileMapRegion;

// This portion of the archive file header must remain unchanged for
// _version >= CDS_GENERIC_HEADER_SUPPORTED_MIN_VERSION (12).
// This makes it possible to read important information from a CDS archive created by
// a different version of HotSpot, so that we can automatically regenerate the archive as necessary.
typedef struct GenericCDSFileMapHeader {
  unsigned int _magic;                    // identification of file type
  int          _crc;                      // header crc checksum, start from _base_archive_name_offset
  int          _version;                  // CURRENT_CDS_ARCHIVE_VERSION of the jdk that dumped the this archive
  unsigned int _header_size;              // total size of the header, in bytes
  unsigned int _base_archive_name_offset; // offset where the base archive name is stored
                                          //   static archive:  0
                                          //   dynamic archive:
                                          //     0 for default base archive
                                          //     non-zero for non-default base archive
                                          //       (char*)this + _base_archive_name_offset
                                          //       points to a 0-terminated string for the base archive name
  unsigned int _base_archive_name_size;   // size of base archive name including ending '\0'
                                          //   static:  0
                                          //   dynamic:
                                          //     0 for default base archive
                                          //     non-zero for non-default base archive
} GenericCDSFileMapHeader;

// This type is used by the Serviceability Agent to access the contents of
// a memory-mapped CDS archive.
typedef struct CDSFileMapHeaderBase {
  // We cannot inherit from GenericCDSFileMapHeader as this type may be used
  // by both C and C++ code.
  GenericCDSFileMapHeader _generic_header;
  CDSFileMapRegion _space[NUM_CDS_REGIONS];
} CDSFileMapHeaderBase;

#endif // SHARE_INCLUDE_CDS_H
