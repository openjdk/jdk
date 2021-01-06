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
  _status(NullDecoder::no_error), _dwarf_file(nullptr) {
  memset(&_elfHdr, 0, sizeof(_elfHdr));

  size_t len = strlen(filepath) + 1;
  _filepath = (char*)os::malloc(len * sizeof(char), mtInternal);
  if (_filepath == NULL) {
    _status = NullDecoder::out_of_memory;
    return;
  }
  strcpy(_filepath, filepath);
  _status = parse_elf(filepath);
}

ElfFile::~ElfFile() {
  cleanup_tables();

  if (_file != NULL) {
    fclose(_file);
  }

  if (_filepath != NULL) {
    os::free((void*)_filepath);
  }

  delete _shdr_string_table;
  _shdr_string_table = NULL;
  delete _next;
  _next = NULL;
  delete _dwarf_file;
  _dwarf_file = nullptr;
}

void ElfFile::cleanup_tables() {
  delete _string_tables;
  _string_tables = NULL;
  delete _symbol_tables;
  _symbol_tables = NULL;
  delete _funcDesc_table;
  _funcDesc_table = NULL;
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

// Use unified logging rather than assert() throughout this method as this code is already part of the error reporting.
bool ElfFile::get_source_info(const uint32_t offset_in_library, char* filename, const size_t filename_size, int* line) {
  ResourceMark rm;
  // (1)
  if (!load_dwarf_file()) {
    // Some ELF libraries do not provide separate .debuginfo files. Check if the current ELF file has the required
    // DWARF sections. If so, treat the current ELF file as DWARF file.
    Elf_Shdr shdr;
    if (!is_valid_dwarf_file()) {
      log_info(dwarf)("Failed to load DWARF file or find DWARF sections directly inside library %s ", _filepath);
      return false;
    }
    log_debug(dwarf)("No separate .debuginfo file for library %s. It already contains the required DWARF sections.", _filepath);
    _dwarf_file = new (std::nothrow) DwarfFile(_filepath);
  }

  if (!_dwarf_file->get_filename_and_line_number(offset_in_library, filename, filename_size, line)) {
    log_warning(dwarf)("Failed to retrieve file and line number information for %s at offset: " PTR32_FORMAT, _filepath, offset_in_library);
    return false;
  }
  return true;
}

bool ElfFile::is_valid_dwarf_file() const {
  Elf_Shdr shdr;
  return read_section_header(".debug_abbrev", shdr) && read_section_header(".debug_aranges", shdr)
         && read_section_header(".debug_info", shdr) && read_section_header(".debug_line", shdr);
}

// (1) Load the debuginfo file from the path specified in this ELF file in the .gnu_debuglink section.
// Adapted from Servicability Agent.
bool ElfFile::load_dwarf_file() {
  if (_dwarf_file != nullptr) {
    return true;
  }

  const char* debug_filename = get_debug_filename();
  if (debug_filename == nullptr) {
    return false;
  }

  size_t offset = (strlen(debug_filename) + 4) >> 2;
  const uint32_t crc = ((uint32_t*)debug_filename)[offset];
  const char* debug_file_directory = "/usr/lib/debug";
  char* debug_pathname = NEW_RESOURCE_ARRAY(char, strlen(debug_filename) + strlen(_filepath) + strlen(".debug/")
                                            + strlen(debug_file_directory) + 2);
  if (debug_pathname == nullptr) {
    return false;
  }

  strcpy(debug_pathname, _filepath);
  char* last_slash = strrchr(debug_pathname, '/');
  if (last_slash == nullptr) {
    return false;
  }

  // Look in the same directory as the object.
  strcpy(last_slash + 1, debug_filename);
  if (open_valid_debuginfo_file(debug_pathname, crc)) {
    return true;
  }

  // Look in a subdirectory named ".debug".
  strcpy(last_slash + 1, ".debug/");
  strcat(last_slash, debug_filename);
  if (open_valid_debuginfo_file(debug_pathname, crc)) {
    return true;
  }

  // Look in /usr/lib/debug + the full pathname.
  strcpy(debug_pathname, debug_file_directory);
  strcat(debug_pathname, _filepath);
  last_slash = strrchr(debug_pathname, '/');
  strcpy(last_slash + 1, debug_filename);
  if (open_valid_debuginfo_file(debug_pathname, crc)) {
    return true;
  }
  return false;
}

char* ElfFile::get_debug_filename() const {
  Elf_Shdr shdr;
  if (!read_section_header(".gnu_debuglink", shdr)) {
    log_debug(dwarf)("Failed to read the .gnu_debuglink header.");
    // Section not found.
    return nullptr;
  }

  MarkedFileReader mfd(fd());
  if (!mfd.has_mark() || !mfd.set_position(_elfHdr.e_shoff)) {
    return nullptr;
  }

  char* debug_filename = NEW_RESOURCE_ARRAY(char, shdr.sh_size);
  if (debug_filename == nullptr) {
    return nullptr;
  }

  mfd.set_position(shdr.sh_offset);
  if (!mfd.read(debug_filename, shdr.sh_size)) {
    return nullptr;
  }
  return debug_filename;
}

bool ElfFile::read_section_header(const char* name, Elf_Shdr& hdr) const {
  if (_shdr_string_table == nullptr) {
    // Section header string table should be loaded
    return false;
  }
  size_t len = strlen(name) + 1;
  ResourceMark rm;
  char* buf = NEW_RESOURCE_ARRAY(char, len);
  if (buf == nullptr) {
    return false;
  }

  ElfStringTable* const table = _shdr_string_table;
  MarkedFileReader mfd(fd());
  if (!mfd.has_mark() || !mfd.set_position(_elfHdr.e_shoff)) {
    return false;
  }

  for (int index = 0; index < _elfHdr.e_shnum; index++) {
    if (!mfd.read((void*)&hdr, sizeof(hdr))) {
      return false;
    }
    if (table->string_at(hdr.sh_name, buf, len)) {
      if (strncmp(buf, name, len) == 0) {
        return true;
      }
    }
  }
  return false;
}

// Taken from https://sourceware.org/gdb/current/onlinedocs/gdb/Separate-Debug-Files.html#Separate-Debug-Files
static const uint32_t crc32_table[256] = {
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

bool ElfFile::open_valid_debuginfo_file(const char* filepath, const uint32_t crc) {
  if (_dwarf_file != nullptr) {
    // Return cached file.
    return true;
  }

  FILE* file = fopen(filepath, "r");
  if (file == nullptr) {
    log_info(dwarf)("Could not open dwarf file %s (%s)", filepath, os::strerror(errno));
    return false;
  }

  uint32_t file_crc = 0;
  uint8_t buffer[8 * 1024];
  MarkedFileReader reader(file);

  while (true) {
    int len = reader.read_buffer(buffer, sizeof(buffer));
    if (len <= 0) {
      break;
    }
    file_crc = gnu_debuglink_crc32(file_crc, buffer, len);
  }
  fclose(file); // Close it here to reopen it again when the DwarfFile object is created below.

  if (crc == file_crc) {
    // Must be equal, otherwise the file is corrupted.
    log_info(dwarf)("Open DWARF file: %s", filepath);
    _dwarf_file = new (std::nothrow) DwarfFile(filepath);
    bool is_valid = _dwarf_file->is_valid_dwarf_file();
    if (!is_valid) {
      log_info(dwarf)("Did not find required DWARF sections in %s", filepath);
      return false;
    }
    return true;
  }

  log_info(dwarf)("CRC did not match. Expected: " PTR32_FORMAT ", found: " PTR32_FORMAT, crc, file_crc);
  return false;
}

// The CRC used in gnu_debuglink, retrieved from
// http://sourceware.org/gdb/current/onlinedocs/gdb/Separate-Debug-Files.html#Separate-Debug-Files.
uint32_t ElfFile::gnu_debuglink_crc32(uint32_t crc, uint8_t* buf, size_t len) {
  crc = ~crc & 0xffffffff;
  for (uint8_t* end = buf + len; buf < end; buf++)
    crc = crc32_table[(crc ^ *buf) & 0xffu] ^ (crc >> 8u);
  return ~crc & 0xffffffff;
}

// Starting point of reading line number and filename information from the DWARF file.
bool DwarfFile::get_filename_and_line_number(const uint32_t offset_in_library, char* filename, const size_t filename_len, int* line) {
  DebugAranges debug_aranges(this);
  uint32_t compilation_unit_offset = 0; // 4-bytes for 32-bit DWARF
  if (!debug_aranges.find_compilation_unit_offset(offset_in_library, &compilation_unit_offset)) {
    log_info(dwarf)("Failed to find .debug_info offset for the compilation unit.");
    return false;
  }
  log_debug(dwarf)(".debug_info offset:    " PTR32_FORMAT, compilation_unit_offset);

  CompilationUnit compilation_unit(this, compilation_unit_offset);
  uint32_t debug_line_offset = 0;  // 4-bytes for 32-bit DWARF
  if (!compilation_unit.find_debug_line_offset(&debug_line_offset)) {
    log_info(dwarf)("Failed to find .debug_line offset for the line number program.");
    return false;
  }
  log_debug(dwarf)(".debug_line offset:    " PTR32_FORMAT, debug_line_offset);

  LineNumberProgram line_number_program(this, offset_in_library, debug_line_offset);
  if (!line_number_program.find_filename_and_line_number(filename, filename_len, line)) {
    log_info(dwarf)("Failed to process the line number program correctly.");
    return false;
  }
  return true;
}

// (2) The .debug_aranges section contains a number of entries/sets. Each set contains one or multiple address range descriptors of the
// form [beginning_address, beginning_address+length). Start reading these sets and its descriptors until we find one that contains
// 'offset_in_library'. Read the debug_info_offset field from the header of this set which defines the offset for the compilation unit.
// This process is described in section 6.1.2 of the DWARF 4 spec.
bool DwarfFile::DebugAranges::find_compilation_unit_offset(const uint32_t offset_in_library, uint32_t* compilation_unit_offset) {
  uint32_t section_start;
  if (!read_section_header(&section_start)) {
    log_info(dwarf)("Failed to read a .debug_aranges header.");
    return false;
  }

  while (_reader.has_bytes_left()) {
    // Read multiple sets and therefore multiple headers.
    if (!read_header(section_start)) {
      log_info(dwarf)("Failed to read a .debug_aranges header.");
      return false;
    }

    uintptr_t beginning_address = 0;
    uintptr_t length = 0;
    do {
      if (!_reader.read_address_sized(&beginning_address) || !_reader.read_address_sized(&length)) {
        return false;
      }

      if (beginning_address <= offset_in_library && offset_in_library < beginning_address + length) {
        // Found the correct set, read the debug_info_offset from the header of this set.
        log_debug(dwarf)(".debug_aranges offset: " PTR32_FORMAT, (uint32_t)_reader.get_position());
        *compilation_unit_offset = _header._debug_info_offset;
        return true;
      }
    } while (!is_terminating_set(beginning_address, length) && _reader.has_bytes_left());
  }

  // No compilation unit found for offset_in_library.
  return false;
}

bool DwarfFile::DebugAranges::read_section_header(uint32_t* section_start) {
  Elf_Shdr shdr;
  if (!_dwarf_file->read_section_header(".debug_aranges", shdr)) {
    return false;
  }

  *section_start = shdr.sh_offset;
  _reader.set_max_pos(shdr.sh_offset + shdr.sh_size);
  if (!_reader.set_position(shdr.sh_offset)) {
    return false;
  }
  return true;
}

// Parsing header as specified in section 6.1.2 of the DWARF 4 spec.
bool DwarfFile::DebugAranges::read_header(const uint32_t section_start) {
  if (!_reader.read_dword(&_header._unit_length) || _header._unit_length == 0xFFFFFFFF) {
    // For 64-bit DWARF, the first 32-bit value is 0xFFFFFFFF. The current implementation only supports 32-bit DWARF format since GCC
    // only emits 32-bit DWARF.
    return false;
  }

  if (!_reader.read_word(&_header._version) || _header._version != 2) {
    // DWARF 4 uses version 2 as specified in Appendix F of the DWARF 4 spec.
    return false;
  }

  if (!_reader.read_dword(&_header._debug_info_offset)) {
    return false;
  }

  if (!_reader.read_byte(&_header._address_size) || NOT_LP64(_header._address_size != 4)  LP64_ONLY( _header._address_size != 8)) {
    // Addresses must be either 4 bytes for 32-bit architectures or 8 bytes for 64-bit architectures.
    return false;
  }

  if (!_reader.read_byte(&_header._segment_size) || _header._segment_size != 0) {
    // Segment size should be 0.
    return false;
  }

  // We must align to twice the address size.
#ifndef _LP64
  // 8 byte alignment for 32-bit.
  uint8_t alignment = 8;
#else
  // 16 byte alignment for 64-bit.
  uint8_t alignment = 16;
#endif
  uint8_t padding = alignment - (_reader.get_position() - section_start) % alignment;
  if (!_reader.move_position(padding)) {
    return false;
  }
  return true;
}

// Find the .debug_line offset for the line number program by reading from the .debug_abbrev and .debug_info section.
bool DwarfFile::CompilationUnit::find_debug_line_offset(uint32_t* debug_line_offset) {
  _debug_line_offset = debug_line_offset;
  // (3a,b)
  if (!read_header()) {
    log_info(dwarf)("Failed to read the compilation unit header.");
    return false;
  }

  // (3c) Read the abbreviation code immediately following the compilation unit header.
  uint64_t abbrev_code;
  if (!_reader.read_uleb128(&abbrev_code)) {
    return false;
  }

  DebugAbbrev debug_abbrev(_dwarf_file, this);
  if (!debug_abbrev.read_section_header(_header._debug_abbrev_offset)) {
    log_info(dwarf)("Failed to read the .debug_abbrev header at " PTR32_FORMAT, _header._debug_abbrev_offset);
    return false;
  }
  return debug_abbrev.get_debug_line_offset(abbrev_code);
}

// (3a) Parse header as specified in section 7.5.1.1 of the DWARF 4 spec.
bool DwarfFile::CompilationUnit::read_header() {
  Elf_Shdr shdr;
  if (!_dwarf_file->read_section_header(".debug_info", shdr)) {
    log_info(dwarf)("Failed to read the .debug_info section header.");
    return false;
  }

  if (!_reader.set_position(shdr.sh_offset + _compilation_unit_offset)) {
    return false;
  }

  if (!_reader.read_dword(&_header._unit_length) || _header._unit_length == 0xFFFFFFFF) {
    // For 64-bit DWARF, the first 32-bit value is 0xFFFFFFFF. The current implementation only supports 32-bit DWARF format since GCC
    // only emits 32-bit DWARF.
    return false;
  }

  if (!_reader.read_word(&_header._version) || _header._version != 4) {
    // DWARF 4 uses version 4 as specified in Appendix F of the DWARF 4 spec.
    return false;
  }

  // (3b) Offset into .debug_abbrev section.
  if (!_reader.read_dword(&_header._debug_abbrev_offset)) {
    return false;
  }

  if (!_reader.read_byte(&_header._address_size) || NOT_LP64(_header._address_size != 4)  LP64_ONLY( _header._address_size != 8)) {
    // Addresses must be either 4 bytes for 32-bit architectures or 8 bytes for 64-bit architectures.
    return false;
  }

  // Add because _unit_length is not included.
  _reader.set_max_pos(_reader.get_position() + _header._unit_length + 4);
  return true;
}

bool DwarfFile::DebugAbbrev::read_section_header(uint32_t debug_abbrev_offset) {
  Elf_Shdr shdr;
  if (!_dwarf_file->read_section_header(".debug_abbrev", shdr)) {
    return false;
  }

  _reader.set_max_pos(shdr.sh_offset + shdr.sh_size);
  if (!_reader.set_position(shdr.sh_offset + debug_abbrev_offset)) {
    return false;
  }
  return true;
}

// (3d) Follows the parsing instructions of section 7.5.3 of the DWARF 4 spec. Skip over all entries until we find the correct
// entry that matches the 'abbrev_code'. Read the attribute specifications of this entry.
bool DwarfFile::DebugAbbrev::get_debug_line_offset(const uint64_t abbrev_code) {
  while (_reader.has_bytes_left()) {
    uint64_t next_abbrev_code;
    if (!_reader.read_uleb128(&next_abbrev_code)) {
      return false;
    }

    if (next_abbrev_code == 0) {
      break;
    }

    uint64_t next_tag;
    if (!_reader.read_uleb128(&next_tag)) {
      return false;
    }

    log_trace(dwarf)("Code: " UINT64_FORMAT_X ", Tag: " UINT64_FORMAT, next_abbrev_code, next_tag);

    uint8_t has_children;
    if (!_reader.read_byte(&has_children)) {
      return false;
    }

    if (next_abbrev_code == abbrev_code) {
      // Found the correct abbreviation table entry.
      if (next_tag != DW_TAG_compile_unit || has_children != DW_CHILDREN_yes) {
        // Is not DW_TAG_compile_unit as specified in Figure 18 in section 7.5 of the DWARF 4 spec. It could also
        // be DW_TAG_partial_unit (0x3c) which is currently not supported by this parser. Must have children.
        if (next_tag != DW_TAG_compile_unit) {
          log_info(dwarf)("Found unsupported tag in compilation unit: " UINT64_FORMAT_X, next_tag);
        }
        return false;
      }
      return read_attribute_specifications();
    } else {
      if (!skip_attribute_specifications()) {
        return false;
      }
    }
  }
  // Debug line offset not found.
  return false;
}

// Read the attribute names and forms which define the actual attribute values that follow the abbrev code in the compilation unit. All
// attributes need to be read from the compilation unit until we find the DW_AT_stmt_list attribute which specifies the offset for the
// line number program into the .debug_line section. The offset is stored in the _debug_line_offset field of the compilation unit.
bool DwarfFile::DebugAbbrev::read_attribute_specifications() {
  log_debug(dwarf)(".debug_abbrev offset:  " PTR32_FORMAT, (uint32_t)_reader.get_position());
  uint64_t next_attribute_name;
  uint64_t next_attribute_form;
  while (_reader.has_bytes_left()) {
    if (!_reader.read_uleb128(&next_attribute_name)) {
      return false;
    }

    if (!_reader.read_uleb128(&next_attribute_form)) {
      return false;
    }
    log_trace(dwarf)("  Attribute: " UINT64_FORMAT_X ", Form: " UINT64_FORMAT_X, next_attribute_name, next_attribute_form);

    if (next_attribute_name == 0 && next_attribute_form == 0) {
      // Did not find DW_AT_stmt_list.
      return false;
    }

    if (next_attribute_name == DW_AT_stmt_list) {
      return _compilation_unit->read_attribute(next_attribute_form, true);
    } else {
      // Not DW_AT_stmt_list, need to read it from the compilation unit and then continue with the next attribute.
      if (!_compilation_unit->read_attribute(next_attribute_form, false)) {
        return false;
      }
    }
  }

  // .debug_abbrev section is corrupted.
  return false;
}

// (3e) Read the actual attribute values from the compilation unit in the .debug_info section. Each attribute has an encoding
// that specifies which values need to be read for it. This is specified in section 7.5.4 of the DWARF 4 spec. Read all
// attributes while 'set_result' is false. Once it is true, we have reached the attribute DW_AT_stmt_list. Read its value
// which specifies the offset for the line number program into the .debug_line section. Store the result in _debug_info_result
// which is returned to the caller of find_debug_line_offset() which initiated the entire request.
bool DwarfFile::CompilationUnit::read_attribute(const uint64_t attribute, bool set_result) {
  // Reset to the stored _cur_pos of the reader since the DebugAbbrev reader changed the index into the file with its reader.
  _reader.update_to_stored_position();
  uint8_t next_byte = 0;
  uint16_t next_word = 0;
  uint32_t next_dword = 0;
  uint64_t next_qword = 0;

  switch (attribute) {
    case DW_FORM_addr:
      // Move position by the size of an address.
#ifndef _LP64
      _reader.move_position(4);
#else
      _reader.move_position(8);
#endif
      break;
    case DW_FORM_block2:
      // New position: length + data length (next_word)
      if (!_reader.read_word(&next_word) || !_reader.move_position(next_word)) {
        return false;
      }
      break;
    case DW_FORM_block4:
      // New position: length + data length (next_dword)
      if (!_reader.read_dword(&next_dword) || !_reader.move_position(next_dword)) {
        return false;
      }
      break;
    case DW_FORM_data2:
    case DW_FORM_ref2:
      if (!_reader.move_position(2)) {
        return false;
      }
      break;
    case DW_FORM_data4:
    case DW_FORM_strp: // 4 bytes in 32-bit DWARF
    case DW_FORM_ref_addr: // second type of reference: 4 bytes in 32-bit DWARF
    case DW_FORM_ref4:
      if (!_reader.move_position(4)) {
        return false;
      }
      break;
    case DW_FORM_data8:
    case DW_FORM_ref8:
    case DW_FORM_ref_sig8: // 64-bit type signature
      if (!_reader.move_position(8)) {
        return false;
      }
      break;
    case DW_FORM_string:
      if (!_reader.read_string()) {
        return false;
      }
      break;
    case DW_FORM_block:
    case DW_FORM_exprloc:
      next_qword = 0;
      // New position: length + data length (next_qword).
      if (!_reader.read_uleb128(&next_qword) || !_reader.move_position(next_qword)) {
        return false;
      }
      break;
    case DW_FORM_block1:
      // New position: length + data length (next_byte).
      if (!_reader.read_byte(&next_byte) || !_reader.move_position(next_byte)) {
        return false;
      }
      break;
    case DW_FORM_data1:
    case DW_FORM_ref1:
    case DW_FORM_flag:
    case DW_FORM_flag_present:
      if (!_reader.move_position(1)) {
        return false;
      }
      break;
    case DW_FORM_sdata:
    case DW_FORM_udata:
    case DW_FORM_ref_udata:
      if (!_reader.read_uleb128(&next_qword)) {
        return false;
      }
      break;
    case DW_FORM_indirect:
      // Should not be used and therefore is not supported by this parser.
      return false;
    case DW_FORM_sec_offset:
      // The one we are interested in for DW_AT_stmt_list.
      if (set_result) {
        // 4 bytes for 32-bit DWARF.
        if (!_reader.read_dword(_debug_line_offset)) {
          return false;
        }
        break;
      } else {
        if (!_reader.move_position(4)) {
          return false;
        }
        break;
      }
    default:
      // Unknown attribute encoding.
      return false;
  }
  // Reset the index into the file to the original position where the DebugAbbrev reader stopped reading before calling this method.
  _reader.reset_to_previous_position();
  return true;
}

// Read the attribute specifications for this entry but do not process them in any way as we are not interested in them.
bool DwarfFile::DebugAbbrev::skip_attribute_specifications() {
  uint64_t next_attribute_name;
  uint64_t next_attribute_form;
  while (_reader.has_bytes_left()) {
    if (!_reader.read_uleb128(&next_attribute_name)) {
      return false;
    }

    if (!_reader.read_uleb128(&next_attribute_form)) {
      return false;
    }

    log_trace(dwarf)("  Attribute: " UINT64_FORMAT_X ", Form: " UINT64_FORMAT_X, next_attribute_name, next_attribute_form);
    if (next_attribute_name == 0 && next_attribute_form == 0) {
      // Processed all attributes. New entry starts.
      return true;
    }
  }
  return false;
}

bool DwarfFile::LineNumberProgram::find_filename_and_line_number(char* filename, size_t filename_len, int* line) {
  _filename = filename;
  _filename_len = filename_len;
  _line = line;
  if (!read_header()) {
    log_info(dwarf)("Failed to parse the line number program header correctly.");
    return false;
  }

  if (!read_line_number_program()) {
    return false;
  }
  return true;
}

// Parsing header as specified in section 6.2.4 of DWARF 4 spec. We do not read the file_names field, yet.
bool DwarfFile::LineNumberProgram::read_header() {
  Elf_Shdr shdr;
  if (!_dwarf_file->read_section_header(".debug_line", shdr)) {
    log_info(dwarf)("Failed to read the .debug_line section header.");
    return false;
  }

  if (!_reader.set_position(shdr.sh_offset + _debug_line_offset)) {
    return false;
  }

  if (!_reader.read_dword(&_header._unit_length) || _header._unit_length == 0xFFFFFFFF) {
    // For 64-bit DWARF, the first 32-bit value is 0xFFFFFFFF. The current implementation only supports 32-bit DWARF format since GCC
    // only emits 32-bit DWARF.
    return false;
  }

  if (!_reader.read_word(&_header._version) || (_header._version != 3 && _header._version != 4)) {
    // DWARF 3 uses version 3 and DWARF 4 uses version 4 as specified in Appendix F of the DWARF 3 and 4 spec, respectively. For some
    // reason, GCC is currently using version 3 as specified in the DWARF 3 spec for the line number program even though GCC should
    // be using version 4 for DWARF 4 as it emits DWARF 4 by default.
    return false;
  }

  if (!_reader.read_dword(&_header._header_length)) {
    return false;
  }

  // To ensure not to read too many bytes in case of file corruption when reading the path_names field.
  _reader.set_max_pos(_reader.get_position() + _header._header_length);

  if (!_reader.read_byte(&_header._minimum_instruction_length)) {
    return false;
  }

  if (_header._version == 4) {
    if (!_reader.read_byte(&_header._maximum_operations_per_instruction)) {
      return false;
    }
  }

  if (!_reader.read_byte(&_header._default_is_stmt)) {
    return false;
  }

  if (!_reader.read_sbyte(&_header._line_base)) {
    return false;
  }

  if (!_reader.read_byte(&_header._line_range)) {
    return false;
  }

  if (!_reader.read_byte(&_header._opcode_base) || _header._opcode_base - 1 != 12) {
    // There are 12 standard opcodes for DWARF 3 and 4.
    return false;
  }

  for (uint8_t i = 0; i < _header._opcode_base - 1; i++) {
    if (!_reader.read_byte(&_header._standard_opcode_lengths[i])) {
      return false;
    }
  }

  // Read include_directories which are a sequence of path names. These are terminated by a single null byte.
  while (_reader.read_string()) { }

  // Delay reading file_names until we found the correct file index in the line number program. Store the position where the file
  // names start to parse them later. We directly jump to the line number program which starts at offset _debug_line_offset + 10
  // (=sizeof(_unit_length) + sizeof(_version) + sizeof(_header_length)) + _header_length.
  _header._file_starting_pos = _reader.get_position();
  if (!_reader.set_position(shdr.sh_offset + _debug_line_offset + 10 + _header._header_length)) {
    return false;
  }

  // Add 4 because _unit_length is not included.
  _reader.set_max_pos(shdr.sh_offset + _debug_line_offset + _header._unit_length + 4);
  return true;
}

// Create the line number program matrix as described in section 6.2 of the DWARF 4 spec. Try to find the correct entry by comparing
// the address register belonging to each matrix row with _offset_in_library. Once it is found, we can read the line number from the
// line register and the filename by parsing the file_names list from the header until we reach the correct filename as specified by
// the file register.
bool DwarfFile::LineNumberProgram::read_line_number_program() {
  log_debug(dwarf)("");
  log_debug(dwarf)("Line Number Program Matrix");
  log_debug(dwarf)("--------------------------");
#ifndef _LP64
  log_debug(dwarf)("Address:      Line:    Column:   File:");
#else
  log_debug(dwarf)("Address:              Line:    Column:   File:");
#endif
  _state = new (std::nothrow) LineNumberProgramState(&_header);
  uint64_t previous_addr = 0;
  uint32_t previous_line = 0;
  while (_reader.has_bytes_left()) {
    uint8_t opcode;
    if (!_reader.read_byte(&opcode)) {
      return false;
    }

    log_trace(dwarf)("%02x ", opcode);
    if (opcode == 0) {
      // Extended opcodes start with a zero byte.
      if (!apply_extended_opcode()) {
        return false;
      }
    } else if (opcode <= 12) {
      // 12 standard opcodes in DWARF 3 and 4.
      if (!apply_standard_opcode(opcode)) {
        return false;
      }
    } else {
      // Special opcodes start at 13 until 255.
      if (!apply_special_opcode(opcode)) {
        return false;
      }
    }

    if (_state->_append_row) {
      // Append a new line to the line number program matrix.
      if (_state->_first_row) {
        // If this is the first row check if _offset_in_library is bigger than _state->address. If that is not the case, then all following
        // entries belonging to this sequence cannot belong to our _offset_in_library because the addresses are always increasing.
        if (_offset_in_library >= _state->_address) {
          _state->_sequence_candidate = true;
        }
        _state->_first_row = false;
      } else {
        if (_state->_sequence_candidate) {
          // Pick the previous row in the matrix as _offset_in_library always points to the next instruction at this point (e.g. after a call).
          if (_offset_in_library > previous_addr && _offset_in_library <= _state->_address) {
            // If _offset_in_library is between the previous address and the current address then we found the correct entry in the matrix.
            // The matrix is defined in such a way that all addresses in between which would have the same register values are not added again.
            if (!read_filename_from_header(_state->_file)) {
              return false;
            }
            *_line = previous_line;
            log_debug(dwarf)("^^^ Found line for requested offset " PTR32_FORMAT " ^^^", _offset_in_library);
            log_debug(dwarf)("(" INTPTR_FORMAT "    %-5u    %-3u       %-4u)", _state->_address, _state->_line, _state->_column, _state->_file);
            return true;
          }
        }
      }

      log_debug(dwarf)(INTPTR_FORMAT "    %-5u    %-3u       %-4u", _state->_address, _state->_line, _state->_column, _state->_file);
      previous_addr = _state->_address;
      previous_line = _state->_line;
      _state->_append_row = false;
      if (_state->_do_reset) {
        _state->reset_fields();
      }
    }
  }
  // Have not found an entry in the matrix that matches _offset_in_library.
  return false;
}

// Specified in section 6.2.5.3 of the DWARF 4 spec.
bool DwarfFile::LineNumberProgram::apply_extended_opcode() {
  uint64_t extended_opcode_length; // Does not include the already written zero byte and the length leb128.
  if (!_reader.read_uleb128(&extended_opcode_length)) {
    return false;
  }

  uint8_t extended_opcode;
  if (!_reader.read_byte(&extended_opcode)) {
    return false;
  }

  switch (extended_opcode) {
    case DW_LNE_end_sequence: // No operands
      log_trace(dwarf)("DW_LNE_end_sequence");
      _state->_end_sequence = true;
      _state->_append_row = true;
      _state->_do_reset = true;
      break;
    case DW_LNE_set_address: // 1 operand
      if (!_reader.read_address_sized(&_state->_address)) {
        return false;
      }
      log_trace(dwarf)("DW_LNE_set_address " INTPTR_FORMAT, _state->_address);
      if (_state->_dwarf_version == 4) {
        _state->_op_index = 0;
      }
      break;
    case DW_LNE_define_file: // 4 operands
    log_trace(dwarf)("DW_LNE_define_file");
      if (!_reader.read_string()) {
        return false;
      }
      uint64_t dont_care;
      // Operand 2-4: uleb128 numbers we do not care about.
      if (!_reader.read_uleb128(&dont_care)
          || !_reader.read_uleb128(&dont_care)
          || !_reader.read_uleb128(&dont_care)) {
        return false;
      }
      break;
    case DW_LNE_set_discriminator: // 1 operand
      log_trace(dwarf)("DW_LNE_set_discriminator");
      uint64_t discriminator;
      // For some reason, GCC emits this opcode even for earlier versions than DWARF 4 which introduced this opcode.
      // We need to consume it.
      if (!_reader.read_uleb128(&discriminator, 4)) {
        // Must be an unsigned integer as specified in section 6.2.2 of the DWARF 4 spec for the discriminator register.
        return false;
      }
      _state->_discriminator = discriminator;
      break;
    default:
      // Unknown extended opcode.
      return false;
  }
  return true;
}

// Specified in section 6.2.5.2 of the DWARF 4 spec.
bool DwarfFile::LineNumberProgram::apply_standard_opcode(const uint8_t opcode) {
  switch (opcode) {
    case DW_LNS_copy: // No operands
      log_trace(dwarf)("DW_LNS_copy");
      _state->_append_row = true;
      _state->_basic_block = false;
      _state->_prologue_end = false;
      _state->_epilogue_begin = false;
      if (_state->_dwarf_version == 4) {
        _state->_discriminator = 0;
      }
      break;
    case DW_LNS_advance_pc: { // 1 operand
      uint64_t operation_advance;
      if (!_reader.read_uleb128(&operation_advance, 4)) {
        // Must be at most 4 bytes since the value as we are setting the index register which is only 4 bytes wide.
        return false;
      }
      _state->add_to_address_register(operation_advance);
      if (_state->_dwarf_version == 4) {
        _state->set_index_register(operation_advance);
      }
      log_trace(dwarf)("DW_LNS_advance_pc (" INTPTR_FORMAT ")", _state->_address);
      break;
    }
    case DW_LNS_advance_line: // 1 operand
      int64_t line;
      if (!_reader.read_sleb128(&line, 4)) {
        // line register is 4 bytes wide.
        return false;
      }
      _state->_line += line;
      log_trace(dwarf)("DW_LNS_advance_line (%d)", _state->_line);
      break;
    case DW_LNS_set_file: // 1 operand
      uint64_t file;
      if (!_reader.read_uleb128(&file, 4)) {
        // file register is 4 bytes wide.
        return false;
      }
      _state->_file = file;
      log_trace(dwarf)("DW_LNS_set_file (%u)", _state->_file);
      break;
    case DW_LNS_set_column: // 1 operand
      uint64_t column;
      if (!_reader.read_uleb128(&column, 4)) {
        // column register is 4 bytes wide.
        return false;
      }
      _state->_column = column;
      log_trace(dwarf)("DW_LNS_set_column (%u)", _state->_column);
      break;
    case DW_LNS_negate_stmt: // No operands
      log_trace(dwarf)("DW_LNS_negate_stmt");
      _state->_is_stmt = !_state->_is_stmt;
      break;
    case DW_LNS_set_basic_block: // No operands
      log_trace(dwarf)("DW_LNS_set_basic_block");
      _state->_basic_block = true;
      break;
    case DW_LNS_const_add_pc: { // No operands
      // Update address and op_index registers by the increments of special opcode 255.
      uint8_t adjusted_opcode_255 = 255 - _header._opcode_base;
      uint8_t operation_advance = adjusted_opcode_255 / _header._line_range;
      uintptr_t old_address = _state->_address;
      _state->add_to_address_register(operation_advance);
      if (_state->_dwarf_version == 4) {
        _state->set_index_register(operation_advance);
      }
      log_trace(dwarf)("DW_LNS_const_add_pc (" INTPTR_FORMAT ")", _state->_address - old_address);
      break;
    }
    case DW_LNS_fixed_advance_pc: // 1 operand
      uint16_t operand;
      if (!_reader.read_word(&operand)) {
        return false;
      }
      _state->_address += operand;
      _state->_op_index = 0;
      log_trace(dwarf)("DW_LNS_fixed_advance_pc (" INTPTR_FORMAT ")", _state->_address);
      break;
    case DW_LNS_set_prologue_end: // No operands
      log_trace(dwarf)("DW_LNS_set_basic_block");
      _state->_prologue_end = true;
      break;
    case DW_LNS_set_epilogue_begin: // No operands
      log_trace(dwarf)("DW_LNS_set_epilogue_begin");
      _state->_epilogue_begin = true;
      break;
    case DW_LNS_set_isa: // 1 operand
      uint64_t isa;
      if (!_reader.read_uleb128(&isa, 4)) {
        // isa register is 4 bytes wide.
        return false;
      }
      _state->_isa = isa;
      log_trace(dwarf)("DW_LNS_set_isa (%u)", _state->_isa);
      break;
    default:
      // Unknown standard opcode.
      return false;
  }
  return true;
}

// Specified in section 6.2.5.1 of the DWARF 4 spec.
bool DwarfFile::LineNumberProgram::apply_special_opcode(const uint8_t opcode) {
  uintptr_t old_address = _state->_address;
  uint32_t old_line = _state->_line;
  uint8_t adjusted_opcode = opcode - _header._opcode_base;
  uint8_t operation_advance = adjusted_opcode / _header._line_range;
  _state->add_to_address_register(operation_advance);
  if (_state->_dwarf_version == 4) {
    _state->set_index_register(operation_advance);
    _state->_discriminator = 0;
  }
  _state->_line += _header._line_base + (adjusted_opcode % _header._line_range);
  log_trace(dwarf)("address += " INTPTR_FORMAT ", line += %d", _state->_address - old_address, _state->_line - old_line);
  _state->_append_row = true;
  _state->_basic_block = false;
  _state->_prologue_end = false;
  _state->_epilogue_begin = false;
  return true;
}

// Read field file_names from the header as specified in section 6.2.4 of the DWARF 4 spec.
bool DwarfFile::LineNumberProgram::read_filename_from_header(uint32_t file_index) {
  // We do not need to restore the position afterwards as this is the last step of parsing from the file for this compilation unit.
  _reader.set_position(_header._file_starting_pos);
  uint32_t current_index = 1; // file_names start at index 1
  while (_reader.has_bytes_left()) {
    if (!_reader.read_string(_filename, _filename_len)) {
      // Either an error while reading or we have reached the end of the file_names. Both should not happen.
      return false;
    }

    if (current_index == file_index) {
      return true;
    }

    uint64_t dont_care;
    if (!_reader.read_uleb128(&dont_care) || !_reader.read_uleb128(&dont_care) || !_reader.read_uleb128(&dont_care)) {
      return false;
    }
    current_index++;
  }
  return true;
}

void DwarfFile::LineNumberProgram::LineNumberProgramState::reset_fields() {
  _address = 0;
  _op_index = 0;
  _file = 1;
  _line = 1;
  _column = 0;
  _is_stmt = _initial_is_stmt;
  _basic_block = false;
  _end_sequence = false;
  _prologue_end = false;
  _epilogue_begin = false;
  _isa = 0;
  _discriminator = 0;
  _first_row = true;
  _append_row = false;
  _do_reset = false;
  _sequence_candidate = false;
}

// Defined in section 6.2.5.1 of the DWARF 4 spec.
void DwarfFile::LineNumberProgram::LineNumberProgramState::add_to_address_register(uint32_t operation_advance) {
  if (_dwarf_version == 3) {
    _address += operation_advance * _header->_minimum_instruction_length;
  } else if (_dwarf_version == 4) {
    _address += _header->_minimum_instruction_length *
                ((_op_index + operation_advance) / _header->_maximum_operations_per_instruction);
  }
}

// Defined in section 6.2.5.1 of the DWARF 4 spec.
void DwarfFile::LineNumberProgram::LineNumberProgramState::set_index_register(uint32_t operation_advance) {
  _op_index = (_op_index + operation_advance) % _header->_maximum_operations_per_instruction;
}

bool DwarfFile::MarkedDwarfFileReader::set_position(const long new_pos) {
  if (new_pos < 0) {
    return false;
  }
  _current_pos = new_pos;
  return FileReader::set_position(new_pos);
}

bool DwarfFile::MarkedDwarfFileReader::has_bytes_left() const {
  if (_max_pos == -1) {
    return false;
  }
  return _current_pos < _max_pos;
}

bool DwarfFile::MarkedDwarfFileReader::update_to_stored_position() {
  _marked_pos = ftell(_fd);
  if (_marked_pos < 0) {
    return false;
  }
  return FileReader::set_position(_current_pos);
}

bool DwarfFile::MarkedDwarfFileReader::reset_to_previous_position() {
  return FileReader::set_position(_marked_pos);
}

bool DwarfFile::MarkedDwarfFileReader::move_position(const long offset) {
  return set_position(_current_pos + offset);
}

bool DwarfFile::MarkedDwarfFileReader::read_sbyte(int8_t* result) {
  _current_pos++;
  return read(result, 1);
}

bool DwarfFile::MarkedDwarfFileReader::read_byte(uint8_t* result) {
  _current_pos++;
  return read(result, 1);
}

bool DwarfFile::MarkedDwarfFileReader::read_word(uint16_t* result) {
  _current_pos += 2;
  return read(result, 2);
}

bool DwarfFile::MarkedDwarfFileReader::read_dword(uint32_t* result) {
  _current_pos += 4;
  return read(result, 4);
}

bool DwarfFile::MarkedDwarfFileReader::read_qword(uint64_t* result) {
  _current_pos += 8;
  return read(result, 8);
}

bool DwarfFile::MarkedDwarfFileReader::read_address_sized(uintptr_t* result) {
#ifndef _LP64
  uint8_t len = 4;
#else
  uint8_t len = 8;
#endif
  _current_pos += len;
  return read(result, len);
}

// See Figure 46/47 in Appendix C of the DWARF 4 spec.
bool DwarfFile::MarkedDwarfFileReader::read_leb128(uint64_t* result, const int8_t check_size, bool is_signed) {
  *result = 0; // Ensure a proper result by zeroing it first.
  uint8_t buf;
  uint8_t shift = 0;
  uint8_t bytes_read = 0;
  // leb128 is not larger than 8 bytes.
  while (bytes_read < 8) {
    if (!read_byte(&buf)) {
      return false;
    }
    bytes_read++;
    *result |= (buf & 0x7fu) << shift;
    shift += 7;
    if ((buf & 0x80u) == 0) {
      break;
    }
  }
  if (bytes_read > 8 || (check_size != -1 && bytes_read > check_size)) {
    // Invalid leb128 encoding or the read leb128 was bigger than expected.
    return false;
  }

  if (is_signed && (shift < 64) && (buf & 0x40u)) {
    *result |= static_cast<uint64_t>(-1L) << shift;
  }
  return true;
}

bool DwarfFile::MarkedDwarfFileReader::read_uleb128(uint64_t* result, const int8_t check_size) {
  return read_leb128(result, check_size, false);
}

bool DwarfFile::MarkedDwarfFileReader::read_sleb128(int64_t* result, const int8_t check_size) {
  return read_leb128((uint64_t*)result, check_size, true);
}

// If result is a nullptr, we do not care about the content of the string being read.
bool DwarfFile::MarkedDwarfFileReader::read_string(char* result, const size_t result_len) {
  uint8_t next_byte;
  if (!read_byte(&next_byte)) {
    return false;
  }

  if (next_byte == 0) {
    // Strings must contain at least one non-null byte.
    return false;
  }

  if (result != nullptr) {
    if (result_len < 2) {
      // Strings must contain at least one non-null byte and a null byte terminator.
      return false;
    }
    result[0] = next_byte;
  }

  size_t char_index = 1;
  bool exceeded_buffer = false;
  while (has_bytes_left()) {
    // Read until we find a null byte which terminates the string.
    if (!read_byte(&next_byte)) {
      return false;
    }

    if (result != nullptr) {
      if (char_index >= result_len) {
        // Exceeded buffer size of 'result'.
        exceeded_buffer = true;
      } else {
        result[char_index] = next_byte;
      }
      char_index++;
    }
    if (next_byte == 0) {
      if (exceeded_buffer) {
        result[result_len - 1] = '\0'; // Mark end of string.
        log_info(dwarf)("Tried to read %lu bytes but exceeded buffer size of %lu. Truncating string.", char_index, result_len);
      }
      return true;
    }
  }
  return false;
}

#endif // !_WINDOWS && !__APPLE__
