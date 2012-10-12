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
#include "utilities/elfSymbolTable.hpp"

ElfSymbolTable::ElfSymbolTable(FILE* file, Elf_Shdr shdr) {
  assert(file, "null file handle");
  m_symbols = NULL;
  m_next = NULL;
  m_file = file;
  m_status = NullDecoder::no_error;

  // try to load the string table
  long cur_offset = ftell(file);
  if (cur_offset != -1) {
    // call malloc so we can back up if memory allocation fails.
    m_symbols = (Elf_Sym*)os::malloc(shdr.sh_size, mtInternal);
    if (m_symbols) {
      if (fseek(file, shdr.sh_offset, SEEK_SET) ||
        fread((void*)m_symbols, shdr.sh_size, 1, file) != 1 ||
        fseek(file, cur_offset, SEEK_SET)) {
        m_status = NullDecoder::file_invalid;
        os::free(m_symbols);
        m_symbols = NULL;
      }
    }
    if (!NullDecoder::is_error(m_status)) {
      memcpy(&m_shdr, &shdr, sizeof(Elf_Shdr));
    }
  } else {
    m_status = NullDecoder::file_invalid;
  }
}

ElfSymbolTable::~ElfSymbolTable() {
  if (m_symbols != NULL) {
    os::free(m_symbols);
  }

  if (m_next != NULL) {
    delete m_next;
  }
}

bool ElfSymbolTable::lookup(address addr, int* stringtableIndex, int* posIndex, int* offset) {
  assert(stringtableIndex, "null string table index pointer");
  assert(posIndex, "null string table offset pointer");
  assert(offset, "null offset pointer");

  if (NullDecoder::is_error(m_status)) {
    return false;
  }

  address pc = 0;
  size_t  sym_size = sizeof(Elf_Sym);
  assert((m_shdr.sh_size % sym_size) == 0, "check size");
  int count = m_shdr.sh_size / sym_size;
  if (m_symbols != NULL) {
    for (int index = 0; index < count; index ++) {
      if (STT_FUNC == ELF_ST_TYPE(m_symbols[index].st_info)) {
        address sym_addr = (address)m_symbols[index].st_value;
        if (sym_addr < addr && (addr - sym_addr) < *offset) {
          pc = (address)m_symbols[index].st_value;
          *offset = (int)(addr - pc);
          *posIndex = m_symbols[index].st_name;
          *stringtableIndex = m_shdr.sh_link;
        }
      }
    }
  } else {
    long cur_pos;
    if ((cur_pos = ftell(m_file)) == -1 ||
      fseek(m_file, m_shdr.sh_offset, SEEK_SET)) {
      m_status = NullDecoder::file_invalid;
      return false;
    }

    Elf_Sym sym;
    for (int index = 0; index < count; index ++) {
      if (fread(&sym, sym_size, 1, m_file) == 1) {
        if (STT_FUNC == ELF_ST_TYPE(sym.st_info)) {
          address sym_addr = (address)sym.st_value;
          if (sym_addr < addr && (addr - sym_addr) < *offset) {
            pc = (address)sym.st_value;
            *offset = (int)(addr - pc);
            *posIndex = sym.st_name;
            *stringtableIndex = m_shdr.sh_link;
          }
        }
      } else {
        m_status = NullDecoder::file_invalid;
        return false;
      }
    }
    fseek(m_file, cur_pos, SEEK_SET);
  }
  return true;
}

#endif // _WINDOWS
