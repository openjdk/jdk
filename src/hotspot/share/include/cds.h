/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

// This file declares the CDS data structures that are used by the HotSpot Serviceability Agent
// (see C sources inside src/jdk.hotspot.agent).
//
// We should use only standard C types. Do not use custom types such as bool, intx,
// etc, to avoid introducing unnecessary dependencies to other HotSpot type declarations.
//
// Also, this is a C header file. Do not use C++ here.

#define NUM_CDS_REGIONS 9
#define CDS_ARCHIVE_MAGIC 0xf00baba2
#define CURRENT_CDS_ARCHIVE_VERSION 5
#define INVALID_CDS_ARCHIVE_VERSION -1

struct CDSFileMapRegion {
  int        _crc;           // crc checksum of the current space
  size_t     _file_offset;   // sizeof(this) rounded to vm page size
  union {
    char*    _base;          // copy-on-write base address
    size_t   _offset;        // offset from the compressed oop encoding base, only used
                             // by archive heap space
  } _addr;
  size_t     _used;          // for setting space top on read
  int        _read_only;     // read only space?
  int        _allow_exec;    // executable code in space?
  void*      _oopmap;        // bitmap for relocating embedded oops
  size_t     _oopmap_size_in_bits;
};

struct CDSFileMapHeaderBase {
  unsigned int _magic;           // identify file type
  int          _crc;             // header crc checksum
  int          _version;         // must be CURRENT_CDS_ARCHIVE_VERSION
  struct CDSFileMapRegion _space[NUM_CDS_REGIONS];
};

typedef struct CDSFileMapHeaderBase CDSFileMapHeaderBase;

#endif // SHARE_INCLUDE_CDS_H
