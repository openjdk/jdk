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

#include "precompiled.hpp"

#if !defined(_WINDOWS) && !defined(__APPLE__)

#include "memory/allocation.inline.hpp"
#include "runtime/os.hpp"
#include "utilities/elfStringTable.hpp"

// We will try to load whole string table into memory if we can.
// Otherwise, fallback to more expensive file operation.
ElfStringTable::ElfStringTable(FILE* file, Elf_Shdr shdr, int index) {
  assert(file, "null file handle");
  m_table = NULL;
  m_index = index;
  m_next = NULL;
  m_file = file;
  m_status = NullDecoder::no_error;

  // try to load the string table
  long cur_offset = ftell(file);
  m_table = (char*)os::malloc(sizeof(char) * shdr.sh_size, mtInternal);
  if (m_table != NULL) {
    // if there is an error, mark the error
    if (fseek(file, shdr.sh_offset, SEEK_SET) ||
      fread((void*)m_table, shdr.sh_size, 1, file) != 1 ||
      fseek(file, cur_offset, SEEK_SET)) {
      m_status = NullDecoder::file_invalid;
      os::free((void*)m_table);
      m_table = NULL;
    }
  } else {
    memcpy(&m_shdr, &shdr, sizeof(Elf_Shdr));
  }
}

ElfStringTable::~ElfStringTable() {
  if (m_table != NULL) {
    os::free((void*)m_table);
  }

  if (m_next != NULL) {
    delete m_next;
  }
}

bool ElfStringTable::string_at(int pos, char* buf, int buflen) {
  if (NullDecoder::is_error(m_status)) {
    return false;
  }
  if (m_table != NULL) {
    jio_snprintf(buf, buflen, "%s", (const char*)(m_table + pos));
    return true;
  } else {
    long cur_pos = ftell(m_file);
    if (cur_pos == -1 ||
      fseek(m_file, m_shdr.sh_offset + pos, SEEK_SET) ||
      fread(buf, 1, buflen, m_file) <= 0 ||
      fseek(m_file, cur_pos, SEEK_SET)) {
      m_status = NullDecoder::file_invalid;
      return false;
    }
    return true;
  }
}

#endif // _WINDOWS
