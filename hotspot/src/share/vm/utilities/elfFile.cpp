/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

#include <string.h>
#include <stdio.h>
#include <limits.h>
#include <new>

#include "memory/allocation.inline.hpp"
#include "utilities/decoder.hpp"
#include "utilities/elfFile.hpp"
#include "utilities/elfStringTable.hpp"
#include "utilities/elfSymbolTable.hpp"


ElfFile::ElfFile(const char* filepath) {
  assert(filepath, "null file path");
  memset(&m_elfHdr, 0, sizeof(m_elfHdr));
  m_string_tables = NULL;
  m_symbol_tables = NULL;
  m_next = NULL;
  m_status = NullDecoder::no_error;

  int len = strlen(filepath) + 1;
  m_filepath = (const char*)os::malloc(len * sizeof(char), mtInternal);
  if (m_filepath != NULL) {
    strcpy((char*)m_filepath, filepath);
    m_file = fopen(filepath, "r");
    if (m_file != NULL) {
      load_tables();
    } else {
      m_status = NullDecoder::file_not_found;
    }
  } else {
    m_status = NullDecoder::out_of_memory;
  }
}

ElfFile::~ElfFile() {
  if (m_string_tables != NULL) {
    delete m_string_tables;
  }

  if (m_symbol_tables != NULL) {
    delete m_symbol_tables;
  }

  if (m_file != NULL) {
    fclose(m_file);
  }

  if (m_filepath != NULL) {
    os::free((void*)m_filepath);
  }

  if (m_next != NULL) {
    delete m_next;
  }
};


//Check elf header to ensure the file is valid.
bool ElfFile::is_elf_file(Elf_Ehdr& hdr) {
  return (ELFMAG0 == hdr.e_ident[EI_MAG0] &&
      ELFMAG1 == hdr.e_ident[EI_MAG1] &&
      ELFMAG2 == hdr.e_ident[EI_MAG2] &&
      ELFMAG3 == hdr.e_ident[EI_MAG3] &&
      ELFCLASSNONE != hdr.e_ident[EI_CLASS] &&
      ELFDATANONE != hdr.e_ident[EI_DATA]);
}

bool ElfFile::load_tables() {
  assert(m_file, "file not open");
  assert(!NullDecoder::is_error(m_status), "already in error");

  // read elf file header
  if (fread(&m_elfHdr, sizeof(m_elfHdr), 1, m_file) != 1) {
    m_status = NullDecoder::file_invalid;
    return false;
  }

  if (!is_elf_file(m_elfHdr)) {
    m_status = NullDecoder::file_invalid;
    return false;
  }

  // walk elf file's section headers, and load string tables
  Elf_Shdr shdr;
  if (!fseek(m_file, m_elfHdr.e_shoff, SEEK_SET)) {
    if (NullDecoder::is_error(m_status)) return false;

    for (int index = 0; index < m_elfHdr.e_shnum; index ++) {
      if (fread((void*)&shdr, sizeof(Elf_Shdr), 1, m_file) != 1) {
        m_status = NullDecoder::file_invalid;
        return false;
      }
      // string table
      if (shdr.sh_type == SHT_STRTAB) {
        ElfStringTable* table = new (std::nothrow) ElfStringTable(m_file, shdr, index);
        if (table == NULL) {
          m_status = NullDecoder::out_of_memory;
          return false;
        }
        add_string_table(table);
      } else if (shdr.sh_type == SHT_SYMTAB || shdr.sh_type == SHT_DYNSYM) {
        ElfSymbolTable* table = new (std::nothrow) ElfSymbolTable(m_file, shdr);
        if (table == NULL) {
          m_status = NullDecoder::out_of_memory;
          return false;
        }
        add_symbol_table(table);
      }
    }
  }
  return true;
}

bool ElfFile::decode(address addr, char* buf, int buflen, int* offset) {
  // something already went wrong, just give up
  if (NullDecoder::is_error(m_status)) {
    return false;
  }
  ElfSymbolTable* symbol_table = m_symbol_tables;
  int string_table_index;
  int pos_in_string_table;
  int off = INT_MAX;
  bool found_symbol = false;
  while (symbol_table != NULL) {
    if (symbol_table->lookup(addr, &string_table_index, &pos_in_string_table, &off)) {
      found_symbol = true;
    }
    symbol_table = symbol_table->m_next;
  }
  if (!found_symbol) return false;

  ElfStringTable* string_table = get_string_table(string_table_index);

  if (string_table == NULL) {
    m_status = NullDecoder::file_invalid;
    return false;
  }
  if (offset) *offset = off;

  return string_table->string_at(pos_in_string_table, buf, buflen);
}


void ElfFile::add_symbol_table(ElfSymbolTable* table) {
  if (m_symbol_tables == NULL) {
    m_symbol_tables = table;
  } else {
    table->m_next = m_symbol_tables;
    m_symbol_tables = table;
  }
}

void ElfFile::add_string_table(ElfStringTable* table) {
  if (m_string_tables == NULL) {
    m_string_tables = table;
  } else {
    table->m_next = m_string_tables;
    m_string_tables = table;
  }
}

ElfStringTable* ElfFile::get_string_table(int index) {
  ElfStringTable* p = m_string_tables;
  while (p != NULL) {
    if (p->index() == index) return p;
    p = p->m_next;
  }
  return NULL;
}

#ifdef LINUX
bool ElfFile::specifies_noexecstack() {
  Elf_Phdr phdr;
  if (!m_file)  return true;

  if (!fseek(m_file, m_elfHdr.e_phoff, SEEK_SET)) {
    for (int index = 0; index < m_elfHdr.e_phnum; index ++) {
      if (fread((void*)&phdr, sizeof(Elf_Phdr), 1, m_file) != 1) {
        m_status = NullDecoder::file_invalid;
        return false;
      }
      if (phdr.p_type == PT_GNU_STACK) {
        if (phdr.p_flags == (PF_R | PF_W))  {
          return true;
        } else {
          return false;
        }
      }
    }
  }
  return false;
}
#endif

#endif // _WINDOWS
