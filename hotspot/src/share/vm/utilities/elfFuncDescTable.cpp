/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
 * Copyright 2012, 2013 SAP AG. All rights reserved.
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
#include "utilities/elfFuncDescTable.hpp"

ElfFuncDescTable::ElfFuncDescTable(FILE* file, Elf_Shdr shdr, int index) {
  assert(file, "null file handle");
  // The actual function address (i.e. function entry point) is always the
  // first value in the function descriptor (on IA64 and PPC64 they look as follows):
  // PPC64: [function entry point, TOC pointer, environment pointer]
  // IA64 : [function entry point, GP (global pointer) value]
  // Unfortunately 'shdr.sh_entsize' doesn't always seem to contain this size (it's zero on PPC64) so we can't assert
  // assert(IA64_ONLY(2) PPC64_ONLY(3) * sizeof(address) == shdr.sh_entsize, "Size mismatch for '.opd' section entries");

  m_funcDescs = NULL;
  m_file = file;
  m_index = index;
  m_status = NullDecoder::no_error;

  // try to load the function descriptor table
  long cur_offset = ftell(file);
  if (cur_offset != -1) {
    // call malloc so we can back up if memory allocation fails.
    m_funcDescs = (address*)os::malloc(shdr.sh_size, mtInternal);
    if (m_funcDescs) {
      if (fseek(file, shdr.sh_offset, SEEK_SET) ||
          fread((void*)m_funcDescs, shdr.sh_size, 1, file) != 1 ||
          fseek(file, cur_offset, SEEK_SET)) {
        m_status = NullDecoder::file_invalid;
        os::free(m_funcDescs);
        m_funcDescs = NULL;
      }
    }
    if (!NullDecoder::is_error(m_status)) {
      memcpy(&m_shdr, &shdr, sizeof(Elf_Shdr));
    }
  } else {
    m_status = NullDecoder::file_invalid;
  }
}

ElfFuncDescTable::~ElfFuncDescTable() {
  if (m_funcDescs != NULL) {
    os::free(m_funcDescs);
  }
}

address ElfFuncDescTable::lookup(Elf_Word index) {
  if (NullDecoder::is_error(m_status)) {
    return NULL;
  }

  if (m_funcDescs != NULL) {
    if (m_shdr.sh_size > 0 && m_shdr.sh_addr <= index && index <= m_shdr.sh_addr + m_shdr.sh_size) {
      // Notice that 'index' is a byte-offset into the function descriptor table.
      return m_funcDescs[(index - m_shdr.sh_addr) / sizeof(address)];
    }
    return NULL;
  } else {
    long cur_pos;
    address addr;
    if (!(m_shdr.sh_size > 0 && m_shdr.sh_addr <= index && index <= m_shdr.sh_addr + m_shdr.sh_size)) {
      // don't put the whole decoder in error mode if we just tried a wrong index
      return NULL;
    }
    if ((cur_pos = ftell(m_file)) == -1 ||
        fseek(m_file, m_shdr.sh_offset + index - m_shdr.sh_addr, SEEK_SET) ||
        fread(&addr, sizeof(addr), 1, m_file) != 1 ||
        fseek(m_file, cur_pos, SEEK_SET)) {
      m_status = NullDecoder::file_invalid;
      return NULL;
    }
    return addr;
  }
}

#endif // !_WINDOWS && !__APPLE__
