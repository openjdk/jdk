/*
 * Copyright (c) 1997, 2018, Oracle and/or its affiliates. All rights reserved.
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

#include "logging/log.hpp"
#include "memory/allocation.inline.hpp"
#include "memory/resourceArea.hpp"
#include "utilities/decoder.hpp"
#include "utilities/elfFile.hpp"
#include "utilities/elfFuncDescTable.hpp"
#include "utilities/elfStringTable.hpp"
#include "utilities/elfSymbolTable.hpp"
#include "utilities/ostream.hpp"

// For test only, disable elf section cache and force to read from file directly.
bool ElfFile::_do_not_cache_elf_section = false;

ElfSection::ElfSection(FILE* fd, const Elf_Shdr& hdr) : _section_data(NULL) {
  _stat = load_section(fd, hdr);
}

ElfSection::~ElfSection() {
  if (_section_data != NULL) {
    os::free(_section_data);
  }
}

NullDecoder::decoder_status ElfSection::load_section(FILE* const fd, const Elf_Shdr& shdr) {
  memcpy((void*)&_section_hdr, (const void*)&shdr, sizeof(shdr));

  if (ElfFile::_do_not_cache_elf_section) {
    log_debug(decoder)("Elf section cache is disabled");
    return NullDecoder::no_error;
  }

  _section_data = os::malloc(shdr.sh_size, mtInternal);
  // No enough memory for caching. It is okay, we can try to read from
  // file instead.
  if (_section_data == NULL) return NullDecoder::no_error;

  MarkedFileReader mfd(fd);
  if (mfd.has_mark() &&
      mfd.set_position(shdr.sh_offset) &&
      mfd.read(_section_data, shdr.sh_size)) {
    return NullDecoder::no_error;
  } else {
    os::free(_section_data);
    _section_data = NULL;
    return NullDecoder::file_invalid;
  }
}

bool FileReader::read(void* buf, size_t size) {
  assert(buf != NULL, "no buffer");
  assert(size > 0, "no space");
  return fread(buf, size, 1, _fd) == 1;
}

int FileReader::read_buffer(void* buf, size_t size) {
  assert(buf != NULL, "no buffer");
  assert(size > 0, "no space");
  return fread(buf, 1, size, _fd);
}

bool FileReader::set_position(long offset) {
  return fseek(_fd, offset, SEEK_SET) == 0;
}

MarkedFileReader::MarkedFileReader(FILE* fd) : FileReader(fd) {
  _marked_pos = ftell(fd);
}

MarkedFileReader::~MarkedFileReader() {
  if (_marked_pos != -1) {
    set_position(_marked_pos);
  }
}

ElfFile::ElfFile(const char* filepath) :
  _next(NULL), _filepath(NULL), _file(NULL),
  _symbol_tables(NULL), _string_tables(NULL), _shdr_string_table(NULL), _funcDesc_table(NULL),
  _status(NullDecoder::no_error), _debuginfo_file(NULL) {
  memset(&_elfHdr, 0, sizeof(_elfHdr));

  int len = strlen(filepath) + 1;
  _filepath = (char*)os::malloc(len * sizeof(char), mtInternal);
  if (_filepath == NULL) {
    _status = NullDecoder::out_of_memory;
    return;
  }
  strcpy(_filepath, filepath);

  _status = parse_elf(filepath);

  // we no longer need section header string table
//  if (_shdr_string_table != NULL) {
//    delete _shdr_string_table;
//    _shdr_string_table = NULL;
//  }
}

ElfFile::~ElfFile() {
  if (_shdr_string_table != NULL) {
    delete _shdr_string_table;
  }

  cleanup_tables();

  if (_file != NULL) {
    fclose(_file);
  }

  if (_filepath != NULL) {
    os::free((void*)_filepath);
  }

  if (_next != NULL) {
    delete _next;
  }

  if (_debuginfo_file != NULL) {
    delete _debuginfo_file;
  }
}

void ElfFile::cleanup_tables() {
  if (_string_tables != NULL) {
    delete _string_tables;
    _string_tables = NULL;
  }

  if (_symbol_tables != NULL) {
    delete _symbol_tables;
    _symbol_tables = NULL;
  }

  if (_funcDesc_table != NULL) {
    delete _funcDesc_table;
    _funcDesc_table = NULL;
  }
}

NullDecoder::decoder_status ElfFile::parse_elf(const char* filepath) {
  assert(filepath, "null file path");

  _file = fopen(filepath, "r");
  if (_file != NULL) {
    return load_tables();
  } else {
    return NullDecoder::file_not_found;
  }
}

//Check elf header to ensure the file is valid.
bool ElfFile::is_elf_file(Elf_Ehdr& hdr) {
  return (ELFMAG0 == hdr.e_ident[EI_MAG0] &&
      ELFMAG1 == hdr.e_ident[EI_MAG1] &&
      ELFMAG2 == hdr.e_ident[EI_MAG2] &&
      ELFMAG3 == hdr.e_ident[EI_MAG3] &&
      ELFCLASSNONE != hdr.e_ident[EI_CLASS] &&
      ELFDATANONE != hdr.e_ident[EI_DATA]);
}

NullDecoder::decoder_status ElfFile::load_tables() {
  assert(_file, "file not open");
  assert(!NullDecoder::is_error(_status), "already in error");

  FileReader freader(fd());
  // read elf file header
  if (!freader.read(&_elfHdr, sizeof(_elfHdr))) {
    return NullDecoder::file_invalid;
  }

  // Check signature
  if (!is_elf_file(_elfHdr)) {
    return NullDecoder::file_invalid;
  }

  // walk elf file's section headers, and load string tables
  Elf_Shdr shdr;
  if (!freader.set_position(_elfHdr.e_shoff)) {
    return NullDecoder::file_invalid;
  }

  for (int index = 0; index < _elfHdr.e_shnum; index ++) {
    if (!freader.read(&shdr, sizeof(shdr))) {
      return NullDecoder::file_invalid;
    }

    if (shdr.sh_type == SHT_STRTAB) {
      // string tables
      ElfStringTable* table = new (std::nothrow) ElfStringTable(fd(), shdr, index);
      if (table == NULL) {
        return NullDecoder::out_of_memory;
      }
      if (index == _elfHdr.e_shstrndx) {
        assert(_shdr_string_table == NULL, "Only set once");
        _shdr_string_table = table;
      } else {
        add_string_table(table);
      }
    } else if (shdr.sh_type == SHT_SYMTAB || shdr.sh_type == SHT_DYNSYM) {
      // symbol tables
      ElfSymbolTable* table = new (std::nothrow) ElfSymbolTable(fd(), shdr);
      if (table == NULL) {
        return NullDecoder::out_of_memory;
      }
      add_symbol_table(table);
    }
  }
#if defined(PPC64) && !defined(ABI_ELFv2)
  // Now read the .opd section wich contains the PPC64 function descriptor table.
  // The .opd section is only available on PPC64 (see for example:
  // http://refspecs.linuxfoundation.org/LSB_3.1.1/LSB-Core-PPC64/LSB-Core-PPC64/specialsections.html)
  // so this code should do no harm on other platforms but because of performance reasons we only
  // execute it on PPC64 platforms.
  // Notice that we can only find the .opd section after we have successfully read in the string
  // tables in the previous loop, because we need to query the name of each section which is
  // contained in one of the string tables (i.e. the one with the index m_elfHdr.e_shstrndx).

  // Reset the file pointer
  int sect_index = section_by_name(".opd", shdr);

  if (sect_index == -1) {
    return NullDecoder::file_invalid;
  }

  _funcDesc_table = new (std::nothrow) ElfFuncDescTable(_file, shdr, sect_index);
  if (_funcDesc_table == NULL) {
      return NullDecoder::out_of_memory;
  }
#endif
  return NullDecoder::no_error;
}

static const char debug_file_directory[] = "/usr/lib/debug";

bool ElfFile::get_source_info(int offset_in_library, char* buf, size_t buflen, int* line) {
  ResourceMark rm; // TODO: is this correct?
  if (!load_debuginfo_file()) {
    return false;
  }

  assert(_debuginfo_file != NULL, "must be created now");
  tty->print_cr("");
  tty->print_cr("################");
  return _debuginfo_file->get_line_number(offset_in_library, buf, buflen, line);
}

// Requires ResourceMark
bool ElfFile::load_debuginfo_file() {
  if (_debuginfo_file != NULL) {
    return true;
  }

  Elf_Shdr shdr;

  if (section_by_name(".gnu_debuglink", shdr) == -1) {
    // No debuginfo file link found
    return false;
  }

  MarkedFileReader mfd(fd());
  if (!mfd.has_mark() || !mfd.set_position(_elfHdr.e_shoff)) {
    return false;
  }

  char* debug_filename = NEW_RESOURCE_ARRAY(char, shdr.sh_size);
  if (debug_filename == NULL) {
    return false;
  }

  mfd.set_position(shdr.sh_offset);
  if (!mfd.read(debug_filename, shdr.sh_size)) {
    return false;
  }
  tty->print_cr("strlen: %ld", strlen(debug_filename));
  tty->print_cr("str: %s", debug_filename);
  int offset = (strlen(debug_filename) + 4) >> 2;

  uint crc;
  crc = ((uint*)debug_filename)[offset];
  tty->print_cr("crc: %x", crc);

  char* debug_pathname = NEW_RESOURCE_ARRAY(char, strlen(debug_filename)
                                + strlen(_filepath)
                                + strlen(".debug/")
                                + strlen(debug_file_directory)
                                + 2);
  if (debug_pathname == NULL) {
    return false;
  }

  strcpy(debug_pathname, _filepath);
  char *last_slash = strrchr(debug_pathname, '/');
  if (last_slash == NULL) {
    return false;
  }

  /* Look in the same directory as the object.  */
  strcpy(last_slash+1, debug_filename);

  if (open_valid_debuginfo_file(debug_pathname, crc)) {
    return true;
  }

  /* Look in a subdirectory named ".debug".  */
  strcpy(last_slash+1, ".debug/");
  strcat(last_slash, debug_filename);

  if (open_valid_debuginfo_file(debug_pathname, crc)) {
    return true;
  }

  /* Look in /usr/lib/debug + the full pathname.  */
  strcpy(debug_pathname, debug_file_directory);
  strcat(debug_pathname, _filepath);
  last_slash = strrchr(debug_pathname, '/');
  strcpy(last_slash+1, debug_filename);

  if (open_valid_debuginfo_file(debug_pathname, crc)) {
    return true;
  }

  return false;
}

int ElfFile::section_by_name(const char* name, Elf_Shdr& hdr) {
  assert(name != NULL, "No section name");
  size_t len = strlen(name) + 1;
  ResourceMark rm;
  char* buf = NEW_RESOURCE_ARRAY(char, len);
  if (buf == NULL) {
    return -1;
  }

  assert(_shdr_string_table != NULL, "Section header string table should be loaded");
  ElfStringTable* const table = _shdr_string_table;
  MarkedFileReader mfd(fd());
  if (!mfd.has_mark() || !mfd.set_position(_elfHdr.e_shoff)) return -1;

  int sect_index = -1;
  for (int index = 0; index < _elfHdr.e_shnum; index ++) {
    if (!mfd.read((void*)&hdr, sizeof(hdr))) {
      break;
    }
    if (table->string_at(hdr.sh_name, buf, len)) {
      tty->print_cr("Section: %s", buf);
      if (strncmp(buf, name, len) == 0) {
        sect_index = index;



//        if (table->string_at(0, debug_filename, table->get_section_size())) {
//          int offset = (strlen(debug_filename) + 4) >> 2;
////          tty->print_cr("strlen: %ld", strlen(debug_filename));
//          static unsigned int crc;
//          crc = ((unsigned int*)debug_filename)[offset];
//          tty->print_cr("crc: %x", crc);
//        }

        break;
      }
    }
  }
  return sect_index;
}

bool ElfFile::decode(address addr, char* buf, int buflen, int* offset) {
  // something already went wrong, just give up
  if (NullDecoder::is_error(_status)) {
    return false;
  }

  int string_table_index;
  int pos_in_string_table;
  int off = INT_MAX;
  bool found_symbol = false;
  ElfSymbolTable* symbol_table = _symbol_tables;

  while (symbol_table != NULL) {
    if (symbol_table->lookup(addr, &string_table_index, &pos_in_string_table, &off, _funcDesc_table)) {
      found_symbol = true;
      break;
    }
    symbol_table = symbol_table->next();
  }
  if (!found_symbol) {
    return false;
  }

  ElfStringTable* string_table = get_string_table(string_table_index);

  if (string_table == NULL) {
    _status = NullDecoder::file_invalid;
    return false;
  }
  if (offset) *offset = off;

  return string_table->string_at(pos_in_string_table, buf, buflen);
}

void ElfFile::add_symbol_table(ElfSymbolTable* table) {
  if (_symbol_tables == NULL) {
    _symbol_tables = table;
  } else {
    table->set_next(_symbol_tables);
    _symbol_tables = table;
  }
}

void ElfFile::add_string_table(ElfStringTable* table) {
  if (_string_tables == NULL) {
    _string_tables = table;
  } else {
    table->set_next(_string_tables);
    _string_tables = table;
  }
}

ElfStringTable* ElfFile::get_string_table(int index) {
  ElfStringTable* p = _string_tables;
  while (p != NULL) {
    if (p->index() == index) return p;
    p = p->next();
  }
  return NULL;
}

static const uint crc32_table[256] =
 {
   0x00000000, 0x77073096, 0xee0e612c, 0x990951ba, 0x076dc419,
   0x706af48f, 0xe963a535, 0x9e6495a3, 0x0edb8832, 0x79dcb8a4,
   0xe0d5e91e, 0x97d2d988, 0x09b64c2b, 0x7eb17cbd, 0xe7b82d07,
   0x90bf1d91, 0x1db71064, 0x6ab020f2, 0xf3b97148, 0x84be41de,
   0x1adad47d, 0x6ddde4eb, 0xf4d4b551, 0x83d385c7, 0x136c9856,
   0x646ba8c0, 0xfd62f97a, 0x8a65c9ec, 0x14015c4f, 0x63066cd9,
   0xfa0f3d63, 0x8d080df5, 0x3b6e20c8, 0x4c69105e, 0xd56041e4,
   0xa2677172, 0x3c03e4d1, 0x4b04d447, 0xd20d85fd, 0xa50ab56b,
   0x35b5a8fa, 0x42b2986c, 0xdbbbc9d6, 0xacbcf940, 0x32d86ce3,
   0x45df5c75, 0xdcd60dcf, 0xabd13d59, 0x26d930ac, 0x51de003a,
   0xc8d75180, 0xbfd06116, 0x21b4f4b5, 0x56b3c423, 0xcfba9599,
   0xb8bda50f, 0x2802b89e, 0x5f058808, 0xc60cd9b2, 0xb10be924,
   0x2f6f7c87, 0x58684c11, 0xc1611dab, 0xb6662d3d, 0x76dc4190,
   0x01db7106, 0x98d220bc, 0xefd5102a, 0x71b18589, 0x06b6b51f,
   0x9fbfe4a5, 0xe8b8d433, 0x7807c9a2, 0x0f00f934, 0x9609a88e,
   0xe10e9818, 0x7f6a0dbb, 0x086d3d2d, 0x91646c97, 0xe6635c01,
   0x6b6b51f4, 0x1c6c6162, 0x856530d8, 0xf262004e, 0x6c0695ed,
   0x1b01a57b, 0x8208f4c1, 0xf50fc457, 0x65b0d9c6, 0x12b7e950,
   0x8bbeb8ea, 0xfcb9887c, 0x62dd1ddf, 0x15da2d49, 0x8cd37cf3,
   0xfbd44c65, 0x4db26158, 0x3ab551ce, 0xa3bc0074, 0xd4bb30e2,
   0x4adfa541, 0x3dd895d7, 0xa4d1c46d, 0xd3d6f4fb, 0x4369e96a,
   0x346ed9fc, 0xad678846, 0xda60b8d0, 0x44042d73, 0x33031de5,
   0xaa0a4c5f, 0xdd0d7cc9, 0x5005713c, 0x270241aa, 0xbe0b1010,
   0xc90c2086, 0x5768b525, 0x206f85b3, 0xb966d409, 0xce61e49f,
   0x5edef90e, 0x29d9c998, 0xb0d09822, 0xc7d7a8b4, 0x59b33d17,
   0x2eb40d81, 0xb7bd5c3b, 0xc0ba6cad, 0xedb88320, 0x9abfb3b6,
   0x03b6e20c, 0x74b1d29a, 0xead54739, 0x9dd277af, 0x04db2615,
   0x73dc1683, 0xe3630b12, 0x94643b84, 0x0d6d6a3e, 0x7a6a5aa8,
   0xe40ecf0b, 0x9309ff9d, 0x0a00ae27, 0x7d079eb1, 0xf00f9344,
   0x8708a3d2, 0x1e01f268, 0x6906c2fe, 0xf762575d, 0x806567cb,
   0x196c3671, 0x6e6b06e7, 0xfed41b76, 0x89d32be0, 0x10da7a5a,
   0x67dd4acc, 0xf9b9df6f, 0x8ebeeff9, 0x17b7be43, 0x60b08ed5,
   0xd6d6a3e8, 0xa1d1937e, 0x38d8c2c4, 0x4fdff252, 0xd1bb67f1,
   0xa6bc5767, 0x3fb506dd, 0x48b2364b, 0xd80d2bda, 0xaf0a1b4c,
   0x36034af6, 0x41047a60, 0xdf60efc3, 0xa867df55, 0x316e8eef,
   0x4669be79, 0xcb61b38c, 0xbc66831a, 0x256fd2a0, 0x5268e236,
   0xcc0c7795, 0xbb0b4703, 0x220216b9, 0x5505262f, 0xc5ba3bbe,
   0xb2bd0b28, 0x2bb45a92, 0x5cb36a04, 0xc2d7ffa7, 0xb5d0cf31,
   0x2cd99e8b, 0x5bdeae1d, 0x9b64c2b0, 0xec63f226, 0x756aa39c,
   0x026d930a, 0x9c0906a9, 0xeb0e363f, 0x72076785, 0x05005713,
   0x95bf4a82, 0xe2b87a14, 0x7bb12bae, 0x0cb61b38, 0x92d28e9b,
   0xe5d5be0d, 0x7cdcefb7, 0x0bdbdf21, 0x86d3d2d4, 0xf1d4e242,
   0x68ddb3f8, 0x1fda836e, 0x81be16cd, 0xf6b9265b, 0x6fb077e1,
   0x18b74777, 0x88085ae6, 0xff0f6a70, 0x66063bca, 0x11010b5c,
   0x8f659eff, 0xf862ae69, 0x616bffd3, 0x166ccf45, 0xa00ae278,
   0xd70dd2ee, 0x4e048354, 0x3903b3c2, 0xa7672661, 0xd06016f7,
   0x4969474d, 0x3e6e77db, 0xaed16a4a, 0xd9d65adc, 0x40df0b66,
   0x37d83bf0, 0xa9bcae53, 0xdebb9ec5, 0x47b2cf7f, 0x30b5ffe9,
   0xbdbdf21c, 0xcabac28a, 0x53b39330, 0x24b4a3a6, 0xbad03605,
   0xcdd70693, 0x54de5729, 0x23d967bf, 0xb3667a2e, 0xc4614ab8,
   0x5d681b02, 0x2a6f2b94, 0xb40bbe37, 0xc30c8ea1, 0x5a05df1b,
   0x2d02ef8d
 };

/* The CRC used in gnu_debuglink, retrieved from
   http://sourceware.org/gdb/current/onlinedocs/gdb/Separate-Debug-Files.html#Separate-Debug-Files. */
uint ElfFile::gnu_debuglink_crc32(uint crc, unsigned char* buf, size_t len) {
  unsigned char *end;

  crc = ~crc & 0xffffffff;
  for (end = buf + len; buf < end; ++buf)
    crc = crc32_table[(crc ^ *buf) & 0xff] ^ (crc >> 8);
  return ~crc & 0xffffffff;
}

bool ElfFile::open_valid_debuginfo_file(const char* filepath, uint crc) {
  assert(filepath, "null file path");

  if (_debuginfo_file != NULL) {
    return true;
  }

  FILE* file = fopen(filepath, "r");
  if (file == NULL) {
    return false;
  }

  uint file_crc = 0;
  unsigned char buffer[8 * 1024];
  MarkedFileReader freader(file);

  for (;;) {
    int len = freader.read_buffer(buffer, sizeof(buffer));
    if (len <= 0) {
      break;
    }
    file_crc = gnu_debuglink_crc32(file_crc, buffer, len);
  }
  fclose(file);

  if (crc == file_crc) {
    _debuginfo_file = new DwarfFile(filepath);
    return true;
  } else {
    assert(_debuginfo_file == NULL, "still NULL");
    return false;
  }
}

bool DwarfFile::get_line_number(const int offset_in_library, char* buf, size_t buflen, int* line) {

//  !mfd.read((void*)&addr, sizeof(addr))) {

//  tty->print_cr("strlen: %ld", strlen(debug_filename));
//  tty->print_cr("str: %s", debug_filename);
//  int offset = (strlen(debug_filename) + 4) >> 2;

  uint64_t compilation_unit_offset = 0;
  if (!find_compilation_unit_offset(offset_in_library, &compilation_unit_offset)) {
    return false;
  }
  tty->print_cr("Compilation unit offset: 0x%016lx", compilation_unit_offset);

  uint64_t debug_line_offset = 0; // TODO: For 32-bit DWARF it needs to be uint32_t
  if (!find_debug_line_offset(offset_in_library, compilation_unit_offset, &debug_line_offset)) {
    return false;
  }

  tty->print_cr("Line Offset: %lx", debug_line_offset);
  return true;
}

bool DwarfFile::find_compilation_unit_offset(const int offset_in_library, uint64_t *compilation_unit_offset) {

  Elf_Shdr shdr;
  section_by_name(".debug_aranges", shdr);


  MarkedFileReader mfd(fd());
  if (!mfd.has_mark() || !mfd.set_position(_elfHdr.e_shoff)) {
    return false;
  }
  DebugArangesSetHeader32 next_set_header;
  mfd.set_position(shdr.sh_offset);


  Elf64_Xword read_bytes = 0;
  while (read_bytes < shdr.sh_size) {
    if (!mfd.read(&next_set_header.unit_length, sizeof(next_set_header.unit_length))) {
      return false;
    }
    if (next_set_header.unit_length == 0xFFFFFFFF) {
      // For 64-bit DWARF, the first 32-bit value is 0xFFFFFFFF
      // The current implementation only supports 32-bit DWARF format
      return false;
    }

    if (!mfd.read(&next_set_header.version, sizeof(next_set_header.version))) {
      return false;
    }

    if (next_set_header.version != 2) {
      // DWARF 4 uses version 2 as specified in DWARF 4 spec, Appendix F
      return false;
    }

    if (!mfd.read(&next_set_header.debug_info_offset, sizeof(next_set_header.debug_info_offset))) {
      return false;
    }

    if (!mfd.read(&next_set_header.address_size, sizeof(next_set_header.address_size))) {
      return false;
    }

    if (next_set_header.address_size != 8) {
      // The current implementation only supports 64 bit addresses
      return false;
    }

    if (!mfd.read(&next_set_header.segment_size, sizeof(next_set_header.segment_size))) {
      return false;
    }
//    next_set_header.print_fields();

    read_bytes += DebugArangesSetHeader32::SIZE + 4; // Header size + padding (must be alignment that is twice the address size)

    mfd.set_position(shdr.sh_offset + read_bytes); // Account for additional padding

    DebugArangesSet64 set;
    int i = 0;
    do {
      i++;
//      uint32_t bla;
      if (!mfd.read(&set, 16)) {
        return false;
      }
//      uint8_t asdf;
//      if (!mfd.read(&asdf, 1)) {
//           return false;
//         }

//      tty->print_cr("%p", set.beginning_address);
//      tty->print_cr("%lx", set.length);
//      tty->print_cr("%p", set.beginning_address + set.length);
//
//      tty->print_cr("[0x%p - 0x%p)", set.beginning_address, set.beginning_address + set.length);
      read_bytes += sizeof(set); // Header size

      // TODO: needs checks because offset is 32 bit int and beginning address is 64 bit...
      if ((uint64_t)set.beginning_address <= (uint64_t)offset_in_library && (uint64_t)offset_in_library < (uint64_t)set.beginning_address + set.length) {
//        tty->print_cr("Found: ");
//        tty->print_cr("%x", next_set_header.debug_info_offset);
        *compilation_unit_offset = next_set_header.debug_info_offset;
        return true;
      }

    } while (!is_terminating_set(set));
//    tty->print_cr("%lx", read_bytes);
  }
  return false;
}

bool DwarfFile::find_debug_line_offset(const int offset_in_library, const uint64_t compilation_unit_offset, uint64_t *debug_line_offset) {
  Elf_Shdr shdr;
  section_by_name(".debug_info", shdr);

  MarkedFileReader mfd(fd());

  // TODO: DO we need that?
//  if (!mfd.has_mark() || !mfd.set_position(_elfHdr.e_shoff)) {
//    return false;
//  }
  CompilationUnitHeader32 compilation_unit_header;
  mfd.set_position(  shdr.sh_offset + compilation_unit_offset);

  if (!mfd.read(&compilation_unit_header.unit_length, sizeof(compilation_unit_header.unit_length))) {
    return false;
  }
  if (compilation_unit_header.unit_length == 0xFFFFFFFF) {
    // For 64-bit DWARF, the first 32-bit value is 0xFFFFFFFF
    // The current implementation only supports 32-bit DWARF format
    return false;
  }

  if (!mfd.read(&compilation_unit_header.version, sizeof(compilation_unit_header.version))) {
    return false;
  }

  if (compilation_unit_header.version != 4) {
    // DWARF 4 uses version 4 as specified in DWARF 4 spec, Appendix 7.5.1.1
    return false;
  }

  if (!mfd.read(&compilation_unit_header.debug_abbrev_offset, sizeof(compilation_unit_header.debug_abbrev_offset))) {
    return false;
  }

  if (!mfd.read(&compilation_unit_header.address_size, sizeof(compilation_unit_header.address_size))) {
    return false;
  }

  uint64_t abbrev_code;
  if (!read_uleb128(&mfd, &abbrev_code)) {
    return false;
  }

  return get_debug_line_offset_from_debug_abbrev(&mfd, &compilation_unit_header, abbrev_code, debug_line_offset);
}

bool DwarfFile::read_uleb128(MarkedFileReader* mfd, uint64_t* result) {
  uint8_t buf;
  uint32_t shift = 0;
  while (true) {
    if (!mfd->read(&buf, sizeof(buf))) {
      return false;
    }
    *result |= (buf & 0x7fU) << shift;
    shift += 7;
    if ((buf & 0x80U) == 0) {
      break;
    }
  }
  return true;
}

bool DwarfFile::get_debug_line_offset_from_debug_abbrev(MarkedFileReader* debug_info_reader, const CompilationUnitHeader32 *cu_header, const uint64_t abbrev_code, uint64_t *debug_line_offset) {

  long current_debug_info_position = ftell(fd());
  if (current_debug_info_position < 0) {
    // Should be set
    return false;
  }

  Elf_Shdr shdr;
  section_by_name(".debug_abbrev", shdr);

  MarkedFileReader mfd(fd());
  mfd.set_position(shdr.sh_offset + cu_header->debug_abbrev_offset);

  // TODO: add check with size from header to avoid reading further then debug_abbrev section
  while (true) {
    uint64_t next_abbrev_code = 0;
    if (!read_uleb128(&mfd, &next_abbrev_code)) {
      return false;
    }

    if (next_abbrev_code == 0) {
      break;
    }

    bool found_compile_unit = next_abbrev_code == abbrev_code;

    uint64_t next_tag = 0;
    if (!read_uleb128(&mfd, &next_tag)) {
      return false;
    }

    tty->print_cr("Code: %lx, Tag: %lx", next_abbrev_code, next_tag);

    if (found_compile_unit) {
      if (next_tag != DW_TAG_compile_unit) {
        // Is not DW_TAG_compile_unit as specified in Figure 18 in DWARF 4 spec.
        // It could also be DW_TAG_partial_unit (0x3c) which is currently not supported.
        return false;
      }
    }

    uint8_t has_children = 0;
    if (!mfd.read(&has_children, 1)) {
      return false;
    }

    if (found_compile_unit) {
      if (has_children != DW_CHILDREN_yes) {
        // Must have children
        return false;
      }
    }

    uint64_t next_attribute_name;
    uint64_t next_attribute_form;
    // TODO: add check for size to step endless reading
    while (true) {
      next_attribute_name = 0;
      if (!read_uleb128(&mfd, &next_attribute_name)) {
        return false;
      }

      next_attribute_form = 0;
      if (!read_uleb128(&mfd, &next_attribute_form)) {
        return false;
      }

      tty->print_cr("  Attribute: %lx, Form: %lx", next_attribute_name, next_attribute_form);

      if (found_compile_unit) {
        if (next_attribute_name == 0 && next_attribute_form == 0) {
          // Have not found DW_AT_stmt_list
          return false;
        }

        if (next_attribute_name == DW_AT_stmt_list) {
          // Found DW_AT_stmt_list
          return process_attribute(next_attribute_form, cu_header->address_size, &current_debug_info_position,
                                   debug_line_offset);
        } else {
          if (!process_attribute(next_attribute_form, cu_header->address_size, &current_debug_info_position)) {
            return false;
          }
        }
      } else if (next_attribute_name == 0 && next_attribute_form == 0) {
        // Processed all attributes. New entry starts.
        break;
      }
    }

  }
  return true;
}

bool DwarfFile::process_attribute(const uint64_t attribute, const uint8_t address_size, long* current_debug_info_position,uint64_t* debug_line_offset) {

  MarkedFileReader mfd(fd());
  mfd.set_position(*current_debug_info_position);
  uint64_t new_offset = 0;
  uint8_t next_byte = 0;
  uint16_t next_word = 0;
  uint32_t next_dword = 0;
  uint64_t next_qword = 0;

  switch (attribute) {
    case DW_FORM_addr:
      *current_debug_info_position += address_size;
      mfd.set_position(*current_debug_info_position);
      break;
    case DW_FORM_block2:
      if (!mfd.read(&next_word, 2)) {
        return false;
      }
      *current_debug_info_position += 2 + next_word; // length + data length
      mfd.set_position(*current_debug_info_position);
      break;
    case DW_FORM_block4:
      if (!mfd.read(&next_dword, 4)) {
        return false;
      }
      *current_debug_info_position += 4 + next_dword; // length + data length
      mfd.set_position(*current_debug_info_position);
      break;
    case DW_FORM_data2:
    case DW_FORM_ref2:
      *current_debug_info_position += 2;
      mfd.set_position(*current_debug_info_position);
      break;
    case DW_FORM_data4:
    case DW_FORM_strp: // 4 bytes in 32-bit DWARF
    case DW_FORM_ref_addr: // second type of reference: 4 bytes in 32-bit DWARF
    case DW_FORM_ref4:
      *current_debug_info_position += 4;
      mfd.set_position(*current_debug_info_position);
      break;
    case DW_FORM_data8:
    case DW_FORM_ref8:
    case DW_FORM_ref_sig8: // 64-bit type signature
      *current_debug_info_position += 8;
      mfd.set_position(*current_debug_info_position);
      break;
    case DW_FORM_string:
      if (!mfd.read(&next_byte, 1)) {
        return false;
      }
      while (next_byte != 0) {
        if (!mfd.read(&next_byte, 1)) {
          return false;
        }
      }
      break;
    case DW_FORM_block:
    case DW_FORM_exprloc:
      next_qword = 0;
      if (read_uleb128(&mfd, &next_qword)) {
        return false;
      }
      *current_debug_info_position = ftell(fd()) + next_qword; // length + data length
      mfd.set_position(*current_debug_info_position);
      break;
    case DW_FORM_block1:
      if (!mfd.read(&next_byte, 1)) {
        return false;
      }
      *current_debug_info_position += 1 + next_byte; // length + data length
      mfd.set_position(*current_debug_info_position);
      break;
    case DW_FORM_data1:
    case DW_FORM_ref1:
    case DW_FORM_flag:
    case DW_FORM_flag_present:
      *current_debug_info_position += 1;
      mfd.set_position(*current_debug_info_position);
      break;
    case DW_FORM_sdata:
    case DW_FORM_udata:
    case DW_FORM_ref_udata:
      // Reads an LEB128 encoded number. Update by ftell because the read number could be of variable length.
      if (read_uleb128(&mfd, &next_qword)) {
        return false;
      }
      *current_debug_info_position = ftell(fd());
      break;
    case DW_FORM_indirect:
      // Should not be used and therefore is not supported by this parser
      return false;
    case DW_FORM_sec_offset:
      // The one we are interested in
      if (debug_line_offset != nullptr) {
        // 4 bytes for 32-bit DWARF TODO: change debug_line_offset to uint32_t
        if (!mfd.read(debug_line_offset, 4)) {
          return false;
        }
        return true;
      } else {
        *current_debug_info_position += 4;
        mfd.set_position(*current_debug_info_position);
        break;
      }
    default:
      // Unknown encoding TODO warning
      return false;
  }

  return true;
}

#endif // !_WINDOWS && !__APPLE__
