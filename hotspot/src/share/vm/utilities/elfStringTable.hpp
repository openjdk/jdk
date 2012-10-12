/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_UTILITIES_ELF_STRING_TABLE_HPP
#define SHARE_VM_UTILITIES_ELF_STRING_TABLE_HPP

#if !defined(_WINDOWS) && !defined(__APPLE__)

#include "memory/allocation.hpp"
#include "utilities/decoder.hpp"
#include "utilities/elfFile.hpp"


// The string table represents a string table section in an elf file.
// Whenever there is enough memory, it will load whole string table as
// one blob. Otherwise, it will load string from file when requested.
class ElfStringTable: CHeapObj<mtInternal> {
  friend class ElfFile;
 public:
  ElfStringTable(FILE* file, Elf_Shdr shdr, int index);
  ~ElfStringTable();

  // section index
  int index() { return m_index; };

  // get string at specified offset
  bool string_at(int offset, char* buf, int buflen);

  // get status code
  NullDecoder::decoder_status get_status() { return m_status; };

 protected:
  ElfStringTable*        m_next;

  // section index
  int                      m_index;

  // holds complete string table if can
  // allocate enough memory
  const char*              m_table;

  // file contains string table
  FILE*                    m_file;

  // section header
  Elf_Shdr                 m_shdr;

  // error code
  NullDecoder::decoder_status  m_status;
};

#endif // _WINDOWS and _APPLE

#endif // SHARE_VM_UTILITIES_ELF_STRING_TABLE_HPP
