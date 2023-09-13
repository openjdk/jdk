/*
 * Copyright (c) 1997, 2023, Oracle and/or its affiliates. All rights reserved.
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

#include "jvm_io.h"
#include "logging/log.hpp"
#include "memory/allocation.inline.hpp"
#include "utilities/checkedCast.hpp"
#include "utilities/decoder.hpp"
#include "utilities/elfFile.hpp"
#include "utilities/elfFuncDescTable.hpp"
#include "utilities/elfStringTable.hpp"
#include "utilities/elfSymbolTable.hpp"
#include "utilities/ostream.hpp"

#include <string.h>
#include <stdio.h>
#include <limits.h>
#include <new>

const char* ElfFile::USR_LIB_DEBUG_DIRECTORY = "/usr/lib/debug";

// For test only, disable elf section cache and force to read from file directly.
bool ElfFile::_do_not_cache_elf_section = false;

ElfSection::ElfSection(FILE* fd, const Elf_Shdr& hdr) : _section_data(nullptr) {
  _stat = load_section(fd, hdr);
}

ElfSection::~ElfSection() {
  if (_section_data != nullptr) {
    os::free(_section_data);
  }
}

NullDecoder::decoder_status ElfSection::load_section(FILE* const fd, const Elf_Shdr& shdr) {
  memcpy((void*)&_section_hdr, (const void*)&shdr, sizeof(shdr));

  if (ElfFile::_do_not_cache_elf_section) {
    log_develop_debug(decoder)("Elf section cache is disabled");
    return NullDecoder::no_error;
  }

  _section_data = os::malloc(shdr.sh_size, mtInternal);
  // No enough memory for caching. It is okay, we can try to read from
  // file instead.
  if (_section_data == nullptr) return NullDecoder::no_error;

  MarkedFileReader mfd(fd);
  if (mfd.has_mark() &&
      mfd.set_position(shdr.sh_offset) &&
      mfd.read(_section_data, shdr.sh_size)) {
    return NullDecoder::no_error;
  } else {
    os::free(_section_data);
    _section_data = nullptr;
    return NullDecoder::file_invalid;
  }
}

bool FileReader::read(void* buf, size_t size) {
  assert(buf != nullptr, "no buffer");
  assert(size > 0, "no space");
  return fread(buf, size, 1, _fd) == 1;
}

size_t FileReader::read_buffer(void* buf, size_t size) {
  assert(buf != nullptr, "no buffer");
  assert(size > 0, "no space");
  return fread(buf, 1, size, _fd);
}

bool FileReader::set_position(long offset) {
  return fseek(_fd, offset, SEEK_SET) == 0;
}

MarkedFileReader::MarkedFileReader(FILE* fd) : FileReader(fd), _marked_pos(ftell(fd)) {
}

MarkedFileReader::~MarkedFileReader() {
  if (_marked_pos != -1) {
    set_position(_marked_pos);
  }
}

ElfFile::ElfFile(const char* filepath) :
  _next(nullptr), _filepath(os::strdup(filepath)), _file(nullptr),
  _symbol_tables(nullptr), _string_tables(nullptr), _shdr_string_table(nullptr), _funcDesc_table(nullptr),
  _status(NullDecoder::no_error), _dwarf_file(nullptr) {
  memset(&_elfHdr, 0, sizeof(_elfHdr));
  if (_filepath == nullptr) {
    _status = NullDecoder::out_of_memory;
  } else {
    _status = parse_elf(filepath);
  }
}

ElfFile::~ElfFile() {
  cleanup_tables();

  if (_file != nullptr) {
    fclose(_file);
  }

  if (_filepath != nullptr) {
    os::free((void*) _filepath);
    _filepath = nullptr;
  }

  if (_shdr_string_table != nullptr) {
    delete _shdr_string_table;
    _shdr_string_table = nullptr;
  }

  if (_next != nullptr) {
    delete _next;
    _next = nullptr;
  }

  if (_dwarf_file != nullptr) {
    delete _dwarf_file;
    _dwarf_file = nullptr;
  }
}

void ElfFile::cleanup_tables() {
  if (_string_tables != nullptr) {
    delete _string_tables;
    _string_tables = nullptr;
  }
  if (_symbol_tables != nullptr) {
    delete _symbol_tables;
    _symbol_tables = nullptr;
  }
  if (_funcDesc_table != nullptr) {
    delete _funcDesc_table;
    _funcDesc_table = nullptr;
  }
}

NullDecoder::decoder_status ElfFile::parse_elf(const char* filepath) {
  assert(filepath, "null file path");

  _file = os::fopen(filepath, "r");
  if (_file != nullptr) {
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
      if (table == nullptr) {
        return NullDecoder::out_of_memory;
      }
      if (index == _elfHdr.e_shstrndx) {
        assert(_shdr_string_table == nullptr, "Only set once");
        _shdr_string_table = table;
      } else {
        add_string_table(table);
      }
    } else if (shdr.sh_type == SHT_SYMTAB || shdr.sh_type == SHT_DYNSYM) {
      // symbol tables
      ElfSymbolTable* table = new (std::nothrow) ElfSymbolTable(fd(), shdr);
      if (table == nullptr) {
        return NullDecoder::out_of_memory;
      }
      add_symbol_table(table);
    }
  }
#if defined(PPC64) && !defined(ABI_ELFv2)
  // Now read the .opd section which contains the PPC64 function descriptor table.
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
  if (_funcDesc_table == nullptr) {
      return NullDecoder::out_of_memory;
  }
#endif
  return NullDecoder::no_error;
}

#if defined(PPC64) && !defined(ABI_ELFv2)
int ElfFile::section_by_name(const char* name, Elf_Shdr& hdr) {
  assert(name != nullptr, "No section name");
  size_t len = strlen(name) + 1;
  char* buf = (char*)os::malloc(len, mtInternal);
  if (buf == nullptr) {
    return -1;
  }

  assert(_shdr_string_table != nullptr, "Section header string table should be loaded");
  ElfStringTable* const table = _shdr_string_table;
  MarkedFileReader mfd(fd());
  if (!mfd.has_mark() || !mfd.set_position(_elfHdr.e_shoff)) return -1;

  int sect_index = -1;
  for (int index = 0; index < _elfHdr.e_shnum; index ++) {
    if (!mfd.read((void*)&hdr, sizeof(hdr))) {
      break;
    }
    if (table->string_at(hdr.sh_name, buf, len)) {
      if (strncmp(buf, name, len) == 0) {
        sect_index = index;
        break;
      }
    }
  }

  os::free(buf);

  return sect_index;
}
#endif

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

  while (symbol_table != nullptr) {
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

  if (string_table == nullptr) {
    _status = NullDecoder::file_invalid;
    return false;
  }
  if (offset) *offset = off;

  return string_table->string_at(pos_in_string_table, buf, buflen);
}

void ElfFile::add_symbol_table(ElfSymbolTable* table) {
  if (_symbol_tables == nullptr) {
    _symbol_tables = table;
  } else {
    table->set_next(_symbol_tables);
    _symbol_tables = table;
  }
}

void ElfFile::add_string_table(ElfStringTable* table) {
  if (_string_tables == nullptr) {
    _string_tables = table;
  } else {
    table->set_next(_string_tables);
    _string_tables = table;
  }
}

ElfStringTable* ElfFile::get_string_table(int index) {
  ElfStringTable* p = _string_tables;
  while (p != nullptr) {
    if (p->index() == index) return p;
    p = p->next();
  }
  return nullptr;
}

// Use unified logging to report errors rather than assert() throughout this method as this code is already part of the error reporting
// and the debug symbols might be in an unsupported DWARF version or wrong format.
bool ElfFile::get_source_info(const uint32_t offset_in_library, char* filename, const size_t filename_len, int* line, bool is_pc_after_call) {
  if (!load_dwarf_file()) {
    // Some ELF libraries do not provide separate .debuginfo files. Check if the current ELF file has the required
    // DWARF sections. If so, treat the current ELF file as DWARF file.
    if (!is_valid_dwarf_file()) {
      DWARF_LOG_ERROR("Failed to load DWARF file for library %s or find DWARF sections directly inside it.", _filepath);
      return false;
    }
    DWARF_LOG_INFO("No separate .debuginfo file for library %s. It already contains the required DWARF sections.",
                   _filepath);
    if (!create_new_dwarf_file(_filepath)) {
      return false;
    }
  }

  // Store result in filename and line pointer.
  if (!_dwarf_file->get_filename_and_line_number(offset_in_library, filename, filename_len, line, is_pc_after_call)) {
    DWARF_LOG_ERROR("Failed to retrieve file and line number information for %s at offset: " UINT32_FORMAT_X_0, _filepath,
                    offset_in_library);
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
// Adapted from Serviceability Agent.
bool ElfFile::load_dwarf_file() {
  if (_dwarf_file != nullptr) {
    return true; // Already opened.
  }

  DebugInfo debug_info;
  if (!read_debug_info(&debug_info)) {
    DWARF_LOG_DEBUG("Could not read debug info from .gnu_debuglink section");
    return false;
  }

  DwarfFilePath dwarf_file_path(debug_info);
  return load_dwarf_file_from_same_directory(dwarf_file_path)
         || load_dwarf_file_from_env_var_path(dwarf_file_path)
         || load_dwarf_file_from_debug_sub_directory(dwarf_file_path)
         || load_dwarf_file_from_usr_lib_debug(dwarf_file_path);
}

// Read .gnu_debuglink section which contains:
// Filename (null terminated) + 0-3 padding bytes (to 4 byte align) + CRC (4 bytes)
bool ElfFile::read_debug_info(DebugInfo* debug_info) const {
  Elf_Shdr shdr;
  if (!read_section_header(".gnu_debuglink", shdr)) {
    DWARF_LOG_DEBUG("Failed to read the .gnu_debuglink header.");
    return false;
  }

  if (shdr.sh_size % 4 != 0) {
    DWARF_LOG_ERROR(".gnu_debuglink section is not 4 byte aligned (i.e. file is corrupted)");
    return false;
  }

  MarkedFileReader mfd(fd());
  if (!mfd.has_mark() || !mfd.set_position(_elfHdr.e_shoff)) {
    return false;
  }

  uint64_t filename_max_len = shdr.sh_size - DebugInfo::CRC_LEN;
  mfd.set_position(shdr.sh_offset);
  if (!mfd.read(&debug_info->_dwarf_filename, filename_max_len)) {
    return false;
  }

  if (debug_info->_dwarf_filename[filename_max_len - 1] != '\0') {
    // Filename not null-terminated (i.e. overflowed).
    DWARF_LOG_ERROR("Dwarf filename is not null-terminated");
    return false;
  }

  return mfd.read(&debug_info->_crc, DebugInfo::CRC_LEN);
}

bool ElfFile::DwarfFilePath::set(const char* src) {
  int bytes_written = jio_snprintf(_path, MAX_DWARF_PATH_LENGTH, "%s", src);
  if (bytes_written < 0 || bytes_written >= MAX_DWARF_PATH_LENGTH) {
    DWARF_LOG_ERROR("Dwarf file path buffer is too small");
    return false;
  }
  update_null_terminator_index();
  return check_valid_path(); // Sanity check
}

bool ElfFile::DwarfFilePath::set_after_last_slash(const char* src) {
  char* last_slash = strrchr(_path, *os::file_separator());
  if (last_slash == nullptr) {
    // Should always find a slash.
    return false;
  }

  uint16_t index_after_slash = (uint16_t)(last_slash + 1 - _path);
  return copy_to_path_index(index_after_slash, src);
}

bool ElfFile::DwarfFilePath::append(const char* src) {
  return copy_to_path_index(_null_terminator_index, src);
}

bool ElfFile::DwarfFilePath::copy_to_path_index(uint16_t index_in_path, const char* src) {
  if (index_in_path >= MAX_DWARF_PATH_LENGTH - 1) {
    // Should not override '\0' at _path[MAX_DWARF_PATH_LENGTH - 1]
    DWARF_LOG_ERROR("Dwarf file path buffer is too small");
    return false;
  }

  uint16_t max_len = MAX_DWARF_PATH_LENGTH - index_in_path;
  int bytes_written = jio_snprintf(_path + index_in_path, max_len, "%s", src);
  if (bytes_written < 0 || bytes_written >= max_len) {
    DWARF_LOG_ERROR("Dwarf file path buffer is too small");
    return false;
  }
  update_null_terminator_index();
  return check_valid_path(); // Sanity check
}

// Try to load the dwarf file from the same directory as the library file.
bool ElfFile::load_dwarf_file_from_same_directory(DwarfFilePath& dwarf_file_path) {
  if (!dwarf_file_path.set(_filepath)
      || !dwarf_file_path.set_filename_after_last_slash()) {
    return false;
  }
  return open_valid_debuginfo_file(dwarf_file_path);
}

// Try to load the dwarf file from a user specified path in environmental variable _JVM_DWARF_PATH.
bool ElfFile::load_dwarf_file_from_env_var_path(DwarfFilePath& dwarf_file_path) {
  const char* dwarf_path_from_env = ::getenv("_JVM_DWARF_PATH");
  if (dwarf_path_from_env != nullptr) {
    DWARF_LOG_DEBUG("_JVM_DWARF_PATH: %s", dwarf_path_from_env);
    return (load_dwarf_file_from_env_path_folder(dwarf_file_path, dwarf_path_from_env, "/lib/server/")
            || load_dwarf_file_from_env_path_folder(dwarf_file_path, dwarf_path_from_env, "/lib/")
            || load_dwarf_file_from_env_path_folder(dwarf_file_path, dwarf_path_from_env, "/bin/")
            || load_dwarf_file_from_env_path_folder(dwarf_file_path, dwarf_path_from_env, "/"));
  }
  return false;
}

bool ElfFile::load_dwarf_file_from_env_path_folder(DwarfFilePath& dwarf_file_path, const char* dwarf_path_from_env,
                                                   const char* folder) {
  if (!dwarf_file_path.set(dwarf_path_from_env)
      || !dwarf_file_path.append(folder)
      || !dwarf_file_path.append(dwarf_file_path.filename())) {
    DWARF_LOG_ERROR("Dwarf file path buffer is too small");
    return false;
  }
  return open_valid_debuginfo_file(dwarf_file_path);
}

// Try to load the dwarf file from a subdirectory named .debug within the directory of the library file.
bool ElfFile::load_dwarf_file_from_debug_sub_directory(DwarfFilePath& dwarf_file_path) {
  if (!dwarf_file_path.set(_filepath)
      || !dwarf_file_path.set_after_last_slash(".debug/")
      || !dwarf_file_path.append(dwarf_file_path.filename())) {
    DWARF_LOG_ERROR("Dwarf file path buffer is too small");
    return false;
  }
  return open_valid_debuginfo_file(dwarf_file_path);
}

// Try to load the dwarf file from /usr/lib/debug + the full pathname.
bool ElfFile::load_dwarf_file_from_usr_lib_debug(DwarfFilePath& dwarf_file_path) {
  if (!dwarf_file_path.set(USR_LIB_DEBUG_DIRECTORY)
      || !dwarf_file_path.append(_filepath)
      || !dwarf_file_path.set_filename_after_last_slash()) {
    DWARF_LOG_ERROR("Dwarf file path buffer is too small");
    return false;
  }
  return open_valid_debuginfo_file(dwarf_file_path);
}

bool ElfFile::read_section_header(const char* name, Elf_Shdr& hdr) const {
  if (_shdr_string_table == nullptr) {
    assert(false, "section header string table should be loaded");
    return false;
  }
  const uint8_t buf_len = 24;
  char buf[buf_len];
  size_t len = strlen(name) + 1;
  if (len > buf_len) {
    DWARF_LOG_ERROR("Section header name buffer is too small: Required: %zu, Found: %d", len, buf_len);
    return false;
  }

  MarkedFileReader mfd(fd());
  if (!mfd.has_mark() || !mfd.set_position(_elfHdr.e_shoff)) {
    return false;
  }

  for (int index = 0; index < _elfHdr.e_shnum; index++) {
    if (!mfd.read((void*)&hdr, sizeof(hdr))) {
      return false;
    }
    if (_shdr_string_table->string_at(hdr.sh_name, buf, buf_len)) {
      if (strncmp(buf, name, buf_len) == 0) {
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

bool ElfFile::open_valid_debuginfo_file(const DwarfFilePath& dwarf_file_path) {
  if (_dwarf_file != nullptr) {
    // Already opened.
    return true;
  }

  const char* filepath = dwarf_file_path.path();
  FILE* file = fopen(filepath, "r");
  if (file == nullptr) {
    DWARF_LOG_DEBUG("Could not open dwarf file %s (%s)", filepath, os::strerror(errno));
    return false;
  }

  uint32_t file_crc = get_file_crc(file);
  fclose(file); // Close it here to reopen it again when the DwarfFile object is created below.

  if (dwarf_file_path.crc() != file_crc) {
    // Must be equal, otherwise the file is corrupted.
    DWARF_LOG_ERROR("CRC did not match. Expected: " INT32_FORMAT_X_0 ", found: " INT32_FORMAT_X_0, dwarf_file_path.crc(),
                    file_crc);
    return false;
  }
  return create_new_dwarf_file(filepath);
}

uint32_t ElfFile::get_file_crc(FILE* const file) {
  uint32_t file_crc = 0;
  uint8_t buffer[8 * 1024];
  MarkedFileReader reader(file);
  while (true) {
    size_t len = reader.read_buffer(buffer, sizeof(buffer));
    if (len == 0) {
      break;
    }
    file_crc = gnu_debuglink_crc32(file_crc, buffer, len);
  }
  return file_crc;
}

// The CRC used in gnu_debuglink, retrieved from
// http://sourceware.org/gdb/current/onlinedocs/gdb/Separate-Debug-Files.html#Separate-Debug-Files.
uint32_t ElfFile::gnu_debuglink_crc32(uint32_t crc, uint8_t* buf, const size_t len) {
  crc = ~crc;
  for (uint8_t* end = buf + len; buf < end; buf++) {
    crc = crc32_table[(crc ^ *buf) & 0xffu] ^ (crc >> 8u);
  }
  return ~crc;
}

bool ElfFile::create_new_dwarf_file(const char* filepath) {
  DWARF_LOG_SUMMARY("Open DWARF file: %s", filepath);
  _dwarf_file = new (std::nothrow) DwarfFile(filepath);
  if (_dwarf_file == nullptr) {
    DWARF_LOG_ERROR("Failed to create new DwarfFile object for %s.", _filepath);
    return false;
  }
  if (!_dwarf_file->is_valid_dwarf_file()) {
    DWARF_LOG_ERROR("Did not find required DWARF sections in %s", filepath);
    return false;
  }
  return true;
}

// Starting point of reading line number and filename information from the DWARF file.
bool DwarfFile::get_filename_and_line_number(const uint32_t offset_in_library, char* filename, const size_t filename_len,
                                             int* line, const bool is_pc_after_call) {
  DebugAranges debug_aranges(this);
  uint32_t compilation_unit_offset = 0; // 4-bytes for 32-bit DWARF
  if (!debug_aranges.find_compilation_unit_offset(offset_in_library, &compilation_unit_offset)) {
    DWARF_LOG_ERROR("Failed to find .debug_info offset for the compilation unit.");
    return false;
  }
  DWARF_LOG_INFO(".debug_info offset:    " INT32_FORMAT_X_0, compilation_unit_offset);

  CompilationUnit compilation_unit(this, compilation_unit_offset);
  uint32_t debug_line_offset = 0;  // 4-bytes for 32-bit DWARF
  if (!compilation_unit.find_debug_line_offset(&debug_line_offset)) {
    DWARF_LOG_ERROR("Failed to find .debug_line offset for the line number program.");
    return false;
  }
  DWARF_LOG_INFO(".debug_line offset:    " INT32_FORMAT_X_0, debug_line_offset);

  LineNumberProgram line_number_program(this, offset_in_library, debug_line_offset, is_pc_after_call);
  if (!line_number_program.find_filename_and_line_number(filename, filename_len, line)) {
    DWARF_LOG_ERROR("Failed to process the line number program correctly.");
    return false;
  }
  return true;
}

// (2) The .debug_aranges section contains a number of entries/sets. Each set contains one or multiple address range descriptors of the
// form [beginning_address, beginning_address+length). Start reading these sets and their descriptors until we find one that contains
// 'offset_in_library'. Read the debug_info_offset field from the header of this set which defines the offset for the compilation unit.
// This process is described in section 6.1.2 of the DWARF 4 spec.
bool DwarfFile::DebugAranges::find_compilation_unit_offset(const uint32_t offset_in_library, uint32_t* compilation_unit_offset) {
  if (!read_section_header()) {
    DWARF_LOG_ERROR("Failed to read a .debug_aranges header.");
    return false;
  }

  DebugArangesSetHeader set_header;
  bool found_matching_set = false;
  while (_reader.has_bytes_left()) {
    // Read multiple sets and therefore multiple headers.
    if (!read_set_header(set_header)) {
      DWARF_LOG_ERROR("Failed to read a .debug_aranges header.");
      return false;
    }

    if (!read_address_descriptors(set_header, offset_in_library, found_matching_set)) {
      return false;
    }

    if (found_matching_set) {
      // Found the correct set, read the debug_info_offset from the header of this set.
      DWARF_LOG_INFO(".debug_aranges offset: " UINT32_FORMAT, (uint32_t)_reader.get_position());
      *compilation_unit_offset = set_header._debug_info_offset;
      return true;
    }
  }

  assert(false, "No address descriptor found containing offset_in_library.");
  return false;
}

bool DwarfFile::DebugAranges::read_section_header() {
  Elf_Shdr shdr;
  if (!_dwarf_file->read_section_header(".debug_aranges", shdr)) {
    return false;
  }

  _section_start_address = shdr.sh_offset;
  _reader.set_max_pos(shdr.sh_offset + shdr.sh_size);
  return _reader.set_position(shdr.sh_offset);
}

// Parse set header as specified in section 6.1.2 of the DWARF 4 spec.
bool DwarfFile::DebugAranges::read_set_header(DebugArangesSetHeader& header) {
  if (!_reader.read_dword(&header._unit_length) || header._unit_length == 0xFFFFFFFF) {
    // For 64-bit DWARF, the first 32-bit value is 0xFFFFFFFF. The current implementation only supports 32-bit DWARF
    // format since GCC only emits 32-bit DWARF.
    DWARF_LOG_ERROR("64-bit DWARF is not supported for .debug_aranges")
    return false;
  }

  _entry_end = _reader.get_position() + header._unit_length;

  if (!_reader.read_word(&header._version) || header._version != 2) {
    // DWARF 4 uses version 2 as specified in Appendix F of the DWARF 4 spec.
    DWARF_LOG_ERROR(".debug_aranges in unsupported DWARF version %" PRIu16, header._version)
    return false;
  }

  if (!_reader.read_dword(&header._debug_info_offset)) {
    return false;
  }

  if (!_reader.read_byte(&header._address_size) || header._address_size != DwarfFile::ADDRESS_SIZE) {
    // Addresses must be either 4 bytes for 32-bit architectures or 8 bytes for 64-bit architectures.
    DWARF_LOG_ERROR(".debug_aranges specifies wrong address size %" PRIu8, header._address_size);
    return false;
  }

  if (!_reader.read_byte(&header._segment_size) || header._segment_size != 0) {
    // Segment size should be 0.
    DWARF_LOG_ERROR(".debug_aranges segment size is non-zero: %" PRIu8, header._segment_size);
    return false;
  }

  // We must align to twice the address size.
  uint8_t alignment = DwarfFile::ADDRESS_SIZE * 2;
  long padding = alignment - (_reader.get_position() - _section_start_address) % alignment;
  return _reader.move_position(padding);
}

bool DwarfFile::DebugAranges::read_address_descriptors(const DwarfFile::DebugAranges::DebugArangesSetHeader& header,
                                                       const uint32_t offset_in_library, bool& found_matching_set) {
  AddressDescriptor descriptor;
  do {
    if (!read_address_descriptor(descriptor)) {
      return false;
    }

    if (does_match_offset(offset_in_library, descriptor)) {
      found_matching_set = true;
      return true;
    }
  } while (!is_terminating_entry(header, descriptor) && _reader.has_bytes_left());

  // Set does not match offset_in_library. Continue with next.
  return true;
}

bool DwarfFile::DebugAranges::read_address_descriptor(AddressDescriptor& descriptor) {
  return _reader.read_address_sized(&descriptor.beginning_address)
         && _reader.read_address_sized(&descriptor.range_length);
}

bool DwarfFile::DebugAranges::does_match_offset(const uint32_t offset_in_library, const AddressDescriptor& descriptor) {
  return descriptor.beginning_address <= offset_in_library
         && offset_in_library < descriptor.beginning_address + descriptor.range_length;
}

bool DwarfFile::DebugAranges::is_terminating_entry(const DwarfFile::DebugAranges::DebugArangesSetHeader& header,
                                                   const AddressDescriptor& descriptor) {
  bool is_terminating = _reader.get_position() >= _entry_end;
  assert(!is_terminating || (descriptor.beginning_address == 0 && descriptor.range_length == 0),
         "a terminating entry needs a pair of zero");
  return is_terminating;
}

// Find the .debug_line offset for the line number program by reading from the .debug_abbrev and .debug_info section.
bool DwarfFile::CompilationUnit::find_debug_line_offset(uint32_t* debug_line_offset) {
  // (3a,b)
  if (!read_header()) {
    DWARF_LOG_ERROR("Failed to read the compilation unit header.");
    return false;
  }

  // (3c) Read the abbreviation code immediately following the compilation unit header which is an offset to the
  // correct abbreviation table in .debug_abbrev for this compilation unit.
  uint64_t abbrev_code;
  if (!_reader.read_uleb128(&abbrev_code)) {
    return false;
  }

  DebugAbbrev debug_abbrev(_dwarf_file, this);
  if (!debug_abbrev.read_section_header(_header._debug_abbrev_offset)) {
    DWARF_LOG_ERROR("Failed to read the .debug_abbrev header at " UINT32_FORMAT_X_0, _header._debug_abbrev_offset);
    return false;
  }
  if (!debug_abbrev.find_debug_line_offset(abbrev_code)) {
    return false;
  }
  *debug_line_offset = _debug_line_offset; // Result was stored in _debug_line_offset.
  return true;
}

// (3a) Parse header as specified in section 7.5.1.1 of the DWARF 4 spec.
bool DwarfFile::CompilationUnit::read_header() {
  Elf_Shdr shdr;
  if (!_dwarf_file->read_section_header(".debug_info", shdr)) {
    DWARF_LOG_ERROR("Failed to read the .debug_info section header.");
    return false;
  }

  if (!_reader.set_position(shdr.sh_offset + _compilation_unit_offset)) {
    return false;
  }

  if (!_reader.read_dword(&_header._unit_length) || _header._unit_length == 0xFFFFFFFF) {
    // For 64-bit DWARF, the first 32-bit value is 0xFFFFFFFF. The current implementation only supports 32-bit DWARF
    // format since GCC only emits 32-bit DWARF.
    DWARF_LOG_ERROR("64-bit DWARF is not supported for .debug_info")
    return false;
  }

  if (!_reader.read_word(&_header._version) || _header._version != 4) {
    // DWARF 4 uses version 4 as specified in Appendix F of the DWARF 4 spec.
    DWARF_LOG_ERROR(".debug_info in unsupported DWARF version %" PRIu16, _header._version)
    return false;
  }

  // (3b) Offset into .debug_abbrev section.
  if (!_reader.read_dword(&_header._debug_abbrev_offset)) {
    return false;
  }

  if (!_reader.read_byte(&_header._address_size) || _header._address_size != DwarfFile::ADDRESS_SIZE) {
    // Addresses must be either 4 bytes for 32-bit architectures or 8 bytes for 64-bit architectures.
    DWARF_LOG_ERROR(".debug_info specifies wrong address size %" PRIu8, _header._address_size);
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

// (3d) The abbreviations table for a compilation unit consists of a series of abbreviation declarations. Each declaration
// specifies an abbrev code and a tag. Parse all declarations until we find the declaration which matches 'abbrev_code'.
// Read the attribute values from the compilation unit in .debug_info by using the format described in the declaration.
// This process is described in section 7.5 and 7.5.3 of the DWARF 4 spec.
bool DwarfFile::DebugAbbrev::find_debug_line_offset(const uint64_t abbrev_code) {
  DWARF_LOG_TRACE("Series of declarations [code, tag]:");
  AbbreviationDeclaration declaration;
  while (_reader.has_bytes_left()) {
    if (!read_declaration(declaration)) {
      return false;
    }

    DWARF_LOG_TRACE("  Series of attributes [name, form]:");
    if (declaration._abbrev_code == abbrev_code) {
      // Found the correct declaration.
      if (is_wrong_or_unsupported_format(declaration)) {
        return false;
      }
      DWARF_LOG_INFO(".debug_abbrev offset:  " UINT32_FORMAT_X_0, (uint32_t)_reader.get_position());
      DWARF_LOG_TRACE("  Read the following attribute values from compilation unit:");
      return read_attribute_specifications(true);
    } else {
      // Not the correct declaration. Read its attributes and continue with the next declaration.
      if (!read_attribute_specifications(false)) {
        return false;
      }
    }
  }

  assert(false, ".debug_line offset not found");
  return false;
}

bool DwarfFile::DebugAbbrev::read_declaration(DwarfFile::DebugAbbrev::AbbreviationDeclaration& declaration) {
  if (!_reader.read_uleb128(&declaration._abbrev_code)) {
    return false;
  }

  if (declaration._abbrev_code == 0) {
    // Reached the end of the abbreviation declarations for this compilation unit.
    DWARF_LOG_ERROR("abbrev_code not found in any declaration");
    return false;
  }

  if (!_reader.read_uleb128(&declaration._tag) || !_reader.read_byte(&declaration._has_children)) {
    return false;
  }

  DWARF_LOG_TRACE("Code: " UINT64_FORMAT_X ", Tag: " UINT64_FORMAT_X, declaration._abbrev_code, declaration._tag);
  return true;
}

bool DwarfFile::DebugAbbrev::is_wrong_or_unsupported_format(const DwarfFile::DebugAbbrev::AbbreviationDeclaration& declaration) {
  if (declaration._tag != DW_TAG_compile_unit) {
    // Is not DW_TAG_compile_unit as specified in Figure 18 in section 7.5 of the DWARF 4 spec. It could also
    // be DW_TAG_partial_unit (0x3c) which is currently not supported by this parser.
    DWARF_LOG_ERROR("Found unsupported tag in compilation unit: " UINT64_FORMAT_X, declaration._tag);
    return true;
  }
  if (declaration._has_children != DW_CHILDREN_yes) {
    DWARF_LOG_ERROR("Must have children but none specified");
    return true;
  }
  return false;
}

// Read the attribute names and forms which define the actual attribute values that follow the abbrev code in the compilation unit. All
// attributes need to be read from the compilation unit until we reach the DW_AT_stmt_list attribute which specifies the offset for the
// line number program into the .debug_line section. The offset is stored in the _debug_line_offset field of the compilation unit.
bool DwarfFile::DebugAbbrev::read_attribute_specifications(const bool is_DW_TAG_compile_unit) {
  AttributeSpecification attribute_specification;
  while (_reader.has_bytes_left()) {
    if (!read_attribute_specification(attribute_specification)) {
      return false;
    }

    if (is_terminating_specification(attribute_specification)) {
      // Parsed all attributes of this declaration.
      if (is_DW_TAG_compile_unit) {
        DWARF_LOG_ERROR("Did not find DW_AT_stmt_list in .debug_abbrev");
        return false;
      } else {
        // Continue with next declaration if this was not DW_TAG_compile_unit.
        return true;
      }
    }

    if (is_DW_TAG_compile_unit) {
      // Read attribute from compilation unit
      if (attribute_specification._name == DW_AT_stmt_list) {
        // This attribute represents the .debug_line offset. Read it and then stop parsing.
        return _compilation_unit->read_attribute_value(attribute_specification._form, true);
      } else {
        // Not DW_AT_stmt_list, read it and continue with the next attribute.
        if (!_compilation_unit->read_attribute_value(attribute_specification._form, false)) {
          return false;
        }
      }
    }
  }

  assert(false, ".debug_abbrev section appears to be corrupted");
  return false;
}

bool DwarfFile::DebugAbbrev::read_attribute_specification(DwarfFile::DebugAbbrev::AttributeSpecification& specification) {
  bool result = _reader.read_uleb128(&specification._name) && _reader.read_uleb128(&specification._form);
  DWARF_LOG_TRACE("  Name: " UINT64_FORMAT_X ", Form: " UINT64_FORMAT_X,
                   specification._name, specification._form);
  return result;
}

bool DwarfFile::DebugAbbrev::is_terminating_specification(const DwarfFile::DebugAbbrev::AttributeSpecification& specification) {
  return specification._name == 0 && specification._form == 0;
}


// (3e) Read the actual attribute values from the compilation unit in the .debug_info section. Each attribute has an encoding
// that specifies which values need to be read for it. This is specified in section 7.5.4 of the DWARF 4 spec.
// If is_DW_AT_stmt_list_attribute is:
// - False: Ignore the read attribute value.
// - True:  We are going to read the attribute value of the DW_AT_stmt_list attribute which specifies the offset into the
//          .debug_line section for the line number program. Store this offset in the _debug_line_offset field.
bool DwarfFile::CompilationUnit::read_attribute_value(const uint64_t attribute_form, const bool is_DW_AT_stmt_list_attribute) {
  // Reset to the stored _cur_pos of the reader since the DebugAbbrev reader changed the index into the file with its reader.
  _reader.update_to_stored_position();
  uint8_t next_byte = 0;
  uint16_t next_word = 0;
  uint32_t next_dword = 0;
  uint64_t next_qword = 0;

  switch (attribute_form) {
    case DW_FORM_addr:
      // Move position by the size of an address.
      _reader.move_position(DwarfFile::ADDRESS_SIZE);
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
      DWARF_LOG_ERROR("DW_FORM_indirect is not supported.");
      return false;
    case DW_FORM_sec_offset:
      if (is_DW_AT_stmt_list_attribute) {
        // DW_AT_stmt_list has the DW_FORM_sec_offset attribute encoding. Store the result in _debug_line_offset.
        // 4 bytes for 32-bit DWARF.
        DWARF_LOG_TRACE("    Name: DW_AT_stmt_list, Form: DW_FORM_sec_offset");
        DWARF_LOG_TRACE("    Reading .debug_line offset from compilation unit at " UINT32_FORMAT_X_0,
                        (uint32_t)_reader.get_position());
        if (!_reader.read_dword(&_debug_line_offset)) {
          return false;
        }
        break;
      } else {
        if (!_reader.move_position(DwarfFile::DWARF_SECTION_OFFSET_SIZE)) {
          return false;
        }
        break;
      }
    default:
      assert(false, "Unknown DW_FORM_* attribute encoding.");
      return false;
  }
  // Reset the index into the file to the original position where the DebugAbbrev reader stopped reading before calling this method.
  _reader.reset_to_previous_position();
  return true;
}

bool DwarfFile::LineNumberProgram::find_filename_and_line_number(char* filename, const size_t filename_len, int* line) {
  if (!read_header()) {
    DWARF_LOG_ERROR("Failed to parse the line number program header correctly.");
    return false;
  }
  return run_line_number_program(filename, filename_len, line);
}

// Parsing header as specified in section 6.2.4 of DWARF 4 spec. We do not read the file_names field, yet.
bool DwarfFile::LineNumberProgram::read_header() {
  Elf_Shdr shdr;
  if (!_dwarf_file->read_section_header(".debug_line", shdr)) {
    DWARF_LOG_ERROR("Failed to read the .debug_line section header.");
    return false;
  }

  if (!_reader.set_position(shdr.sh_offset + _debug_line_offset)) {
    return false;
  }

  if (!_reader.read_dword(&_header._unit_length) || _header._unit_length == 0xFFFFFFFF) {
    // For 64-bit DWARF, the first 32-bit value is 0xFFFFFFFF. The current implementation only supports 32-bit DWARF
    // format since GCC only emits 32-bit DWARF.
    DWARF_LOG_ERROR("64-bit DWARF is not supported for .debug_line")
    return false;
  }

  if (!_reader.read_word(&_header._version) || _header._version < 2 || _header._version > 4) {
    // DWARF 3 uses version 3 and DWARF 4 uses version 4 as specified in Appendix F of the DWARF 3 and 4 spec, respectively.
    // For some reason, GCC is not following the standard here. While GCC emits DWARF 4 for the other parsed sections,
    // it chooses a different DWARF standard for .debug_line based on the GCC version:
    // - GCC 8 and earlier: .debug_line is in DWARF 2 format (= version 2).
    // - GCC 9 and 10:      .debug_line is in DWARF 3 format (= version 3).
    // - GCC 11:            .debug_line is in DWARF 4 format (= version 4).
    DWARF_LOG_ERROR(".debug_line in unsupported DWARF version %" PRIu16, _header._version)
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

  if (!_reader.read_byte(&_header._line_base)) {
    return false;
  }

  if (!_reader.read_byte(&_header._line_range)) {
    return false;
  }

  if (!_reader.read_byte(&_header._opcode_base) || _header._opcode_base - 1 != 12) {
    // There are 12 standard opcodes for DWARF 3 and 4.
    DWARF_LOG_ERROR("Wrong number of opcodes: %" PRIu8, _header._opcode_base)
    return false;
  }

  for (uint8_t i = 0; i < _header._opcode_base - 1; i++) {
    if (!_reader.read_byte(&_header._standard_opcode_lengths[i])) {
      return false;
    }
  }

  // Read field include_directories which is a sequence of path names. These are terminated by a single null byte.
  // We do not care about them, just read the strings and move on.
  while (_reader.read_string()) { }

  // Delay reading file_names until we found the correct file index in the line number program. Store the position where
  // the file names start to parse them later. We directly jump to the line number program which starts at offset
  // header_size (=HEADER_DESCRIPTION_BYTES + _header_length) + _debug_line_offset
  _header._file_names_offset = _reader.get_position();
  uint32_t header_size = LineNumberProgramHeader::HEADER_DESCRIPTION_BYTES + _header._header_length;
  if (!_reader.set_position(shdr.sh_offset + header_size + _debug_line_offset)) {
    return false;
  }

  // Now reset the max position to where the line number information for this compilation unit ends (i.e. where the state
  // machine gets terminated). Add 4 bytes to the offset because the size of the _unit_length field is not included in this
  // value.
  _reader.set_max_pos(shdr.sh_offset + _debug_line_offset + _header._unit_length + 4);
  return true;
}

// Create the line number information matrix as described in section 6.2 of the DWARF 4 spec. Try to find the correct entry
// by comparing the address register belonging to each matrix row with _offset_in_library. Once it is found, we can read
// the line number from the line register and the filename by parsing the file_names list from the header until we reach
// the correct filename as specified by the file register.
//
// If space was not a problem, the .debug_line section could provide a large matrix that contains an entry for each
// compiler instruction that contains the line number, the column number, the filename etc. But that's impractical.
// Two techniques optimize such a matrix:
// (1) If two offsets share the same file, line and column (and discriminator) information, the row is dropped.
// (2) We store a stream of bytes that represent opcodes to be executed in a well-defined state machine language
//     instead of actually storing the entire matrix row by row.
//
// Let's consider a simple example:
// 25: int iFld = 42;
// 26:
// 27: void bar(int i) {
// 28: }
// 29:
// 30: void foo() {
// 31:   bar(*iFld);
// 32: }
//
// Disassembly of foo() with source code:
// 30:  void foo() {
//           0x55d132:       55                      push   rbp
//           0x55d133:       48 89 e5                mov    rbp,rsp
// 31:    bar(*iFld);
//           0x55d136:       48 8b 05 b3 ee e8 01    mov    rax,QWORD PTR [rip+0x1e8eeb3]        # 23ebff0 <iFld>
//           0x55d13d:       8b 00                   mov    eax,DWORD PTR [rax]
//           0x55d13f:       89 c7                   mov    edi,eax
//           0x55d141:       e8 e2 ff ff ff          call   55d128 <_Z3bari>
// 32:   }
//           0x55d146:       90                      nop
//           0x55d147:       5d                      pop    rbp
//           0x55d148:       c3                      ret
//
// This would produce the following matrix for foo() where duplicated lines (0x55d133, 0x55d13d, 0x55d13f) were removed
// according to (1):
// Address:    Line:    Column:   File:
// 0x55d132    30       12        1
// 0x55d136    31       6         1
// 0x55d146    32       1         1
//
// When trying to get the line number for a PC, which is translated into an offset address x into the library file, we can either:
// - Directly find the last entry in the matrix for which address == x (there could be multiple entries with the same address).
// - If there is no matching address for x:
//   1. Find two consecutive entries in the matrix for which: address_entry_1 < x < address_entry_2.
//   2. Then take the entry of address_entry_1.
//      E.g. x = 0x55d13f -> 0x55d136 < 0x55d13f < 0x55d146 -> Take entry 0x55d136.
//
// Enable logging with debug level to print the generated line number information matrix.
bool DwarfFile::LineNumberProgram::run_line_number_program(char* filename, const size_t filename_len, int* line) {
  DWARF_LOG_DEBUG("");
  DWARF_LOG_DEBUG("Line Number Information Matrix");
  DWARF_LOG_DEBUG("------------------------------");
#ifndef _LP64
  DWARF_LOG_DEBUG("Address:      Line:    Column:   File:");
#else
  DWARF_LOG_DEBUG("Address:              Line:    Column:   File:");
#endif
  _state = new (std::nothrow) LineNumberProgramState(_header);
  if (_state == nullptr) {
    DWARF_LOG_ERROR("Failed to create new LineNumberProgramState object");
    return false;
  }
  uintptr_t previous_address = 0;
  uint32_t previous_file = 0;
  uint32_t previous_line = 0;
  bool found_entry = false;
  bool candidate = false;
  bool first_in_sequence = true;
  while (_reader.has_bytes_left()) {
    if (!apply_opcode()) {
      assert(false, "Could not apply opcode");
      return false;
    }

    if (_state->_append_row) {
      // Append a new line to the line number information matrix.
      if (_state->_first_entry_in_sequence) {
        // First entry in sequence: Check if _offset_in_library >= _state->address. If not, then all following entries
        // belonging to this sequence cannot match our _offset_in_library because the addresses are always increasing
        // in a sequence.
        _state->_can_sequence_match_offset = _offset_in_library >= _state->_address;
        _state->_first_entry_in_sequence = false;
      }
      if (does_offset_match_entry(previous_address, previous_file, previous_line)) {
        // We are using an int for the line number which should never be larger than INT_MAX for any files.
        *line = (int)_state->_line;
        return get_filename_from_header(_state->_file, filename, filename_len);
      }

      // We do not actually store the matrix while searching the correct entry. Enable logging to print/debug it.
      DWARF_LOG_DEBUG(INTPTR_FORMAT "    %-5u    %-3u       %-4u",
                      _state->_address, _state->_line, _state->_column, _state->_file);
      previous_file = _state->_file;
      previous_line = _state->_line;
      previous_address = _state->_address;
      _state->_append_row = false;
      if (_state->_do_reset) {
        // Current sequence terminated.
        _state->reset_fields();
      }
    }
  }

  assert(false, "Did not find an entry in the line number information matrix that matches " UINT32_FORMAT_X_0, _offset_in_library);
  return false;
}

// Apply next opcode to update the state machine.
bool DwarfFile::LineNumberProgram::apply_opcode() {
  uint8_t opcode;
  if (!_reader.read_byte(&opcode)) {
    return false;
  }

  DWARF_LOG_TRACE("  Opcode: 0x%02x ", opcode);
  if (opcode == 0) {
    // Extended opcodes start with a zero byte.
    if (!apply_extended_opcode()) {
      assert(false, "Could not apply extended opcode");
      return false;
    }
  } else if (opcode <= 12) {
    // 12 standard opcodes in DWARF 3 and 4.
    if (!apply_standard_opcode(opcode)) {
      assert(false, "Could not apply standard opcode");
      return false;
    }
  } else {
    // Special opcodes range from 13 until 255.
    apply_special_opcode(opcode);
  }
  return true;
}

// Specified in section 6.2.5.3 of the DWARF 4 spec.
bool DwarfFile::LineNumberProgram::apply_extended_opcode() {
  uint64_t extended_opcode_length; // Does not include the already written zero byte and the length leb128.
  uint8_t extended_opcode;
  if (!_reader.read_uleb128(&extended_opcode_length) || !_reader.read_byte(&extended_opcode)) {
    return false;
  }

  switch (extended_opcode) {
    case DW_LNE_end_sequence: // No operands
      DWARF_LOG_TRACE("    DW_LNE_end_sequence");
      _state->_end_sequence = true;
      _state->_append_row = true;
      _state->_do_reset = true;
      break;
    case DW_LNE_set_address: // 1 operand
      if (!_reader.read_address_sized(&_state->_address)) {
        return false;
      }
      DWARF_LOG_TRACE("    DW_LNE_set_address " INTPTR_FORMAT, _state->_address);
      if (_state->_dwarf_version == 4) {
        _state->_op_index = 0;
      }
      break;
    case DW_LNE_define_file: // 4 operands
    DWARF_LOG_TRACE("    DW_LNE_define_file");
      if (!_reader.read_string()) {
        return false;
      }
      // Operand 2-4: uleb128 numbers we do not care about.
      if (!_reader.read_uleb128_ignore()
          || !_reader.read_uleb128_ignore()
          || !_reader.read_uleb128_ignore()) {
        return false;
      }
      break;
    case DW_LNE_set_discriminator: // 1 operand
      DWARF_LOG_TRACE("    DW_LNE_set_discriminator");
      uint64_t discriminator;
      // For some reason, GCC emits this opcode even for earlier versions than DWARF 4 which introduced this opcode.
      // We need to consume it.
      if (!_reader.read_uleb128(&discriminator, 4)) {
        // Must be an unsigned integer as specified in section 6.2.2 of the DWARF 4 spec for the discriminator register.
        return false;
      }
      _state->_discriminator = static_cast<uint32_t>(discriminator);
      break;
    default:
      assert(false, "Unknown extended opcode");
      return false;
  }
  return true;
}

// Specified in section 6.2.5.2 of the DWARF 4 spec.
bool DwarfFile::LineNumberProgram::apply_standard_opcode(const uint8_t opcode) {
  switch (opcode) {
    case DW_LNS_copy: // No operands
      DWARF_LOG_TRACE("    DW_LNS_copy");
      _state->_append_row = true;
      _state->_basic_block = false;
      _state->_prologue_end = false;
      _state->_epilogue_begin = false;
      if (_state->_dwarf_version == 4) {
        _state->_discriminator = 0;
      }
      break;
    case DW_LNS_advance_pc: { // 1 operand
      uint64_t adv;
      if (!_reader.read_uleb128(&adv, 4)) {
        // Must be at most 4 bytes because the index register is only 4 bytes wide.
        return false;
      }
      uint32_t operation_advance = checked_cast<uint32_t>(adv);
      _state->add_to_address_register(operation_advance, _header);
      if (_state->_dwarf_version == 4) {
        _state->set_index_register(operation_advance, _header);
      }
      DWARF_LOG_TRACE("    DW_LNS_advance_pc (" INTPTR_FORMAT ")", _state->_address);
      break;
    }
    case DW_LNS_advance_line: // 1 operand
      int64_t line;
      if (!_reader.read_sleb128(&line, 4)) {
        // line register is 4 bytes wide.
        return false;
      }
      _state->_line += static_cast<uint32_t>(line);
      DWARF_LOG_TRACE("    DW_LNS_advance_line (%d)", _state->_line);
      break;
    case DW_LNS_set_file: // 1 operand
      uint64_t file;
      if (!_reader.read_uleb128(&file, 4)) {
        // file register is 4 bytes wide.
        return false;
      }
      _state->_file = static_cast<uint32_t>(file);
      DWARF_LOG_TRACE("    DW_LNS_set_file (%u)", _state->_file);
      break;
    case DW_LNS_set_column: // 1 operand
      uint64_t column;
      if (!_reader.read_uleb128(&column, 4)) {
        // column register is 4 bytes wide.
        return false;
      }
      _state->_column = static_cast<uint32_t>(column);
      DWARF_LOG_TRACE("    DW_LNS_set_column (%u)", _state->_column);
      break;
    case DW_LNS_negate_stmt: // No operands
      DWARF_LOG_TRACE("    DW_LNS_negate_stmt");
      _state->_is_stmt = !_state->_is_stmt;
      break;
    case DW_LNS_set_basic_block: // No operands
      DWARF_LOG_TRACE("    DW_LNS_set_basic_block");
      _state->_basic_block = true;
      break;
    case DW_LNS_const_add_pc: { // No operands
      // Update address and op_index registers by the increments of special opcode 255.
      uint8_t adjusted_opcode_255 = 255 - _header._opcode_base;
      uint8_t operation_advance = adjusted_opcode_255 / _header._line_range;
      uintptr_t old_address = _state->_address;
      _state->add_to_address_register(operation_advance, _header);
      if (_state->_dwarf_version == 4) {
        _state->set_index_register(operation_advance, _header);
      }
      DWARF_LOG_TRACE("    DW_LNS_const_add_pc (" INTPTR_FORMAT ")", _state->_address - old_address);
      break;
    }
    case DW_LNS_fixed_advance_pc: // 1 operand
      uint16_t operand;
      if (!_reader.read_word(&operand)) {
        return false;
      }
      _state->_address += operand;
      _state->_op_index = 0;
      DWARF_LOG_TRACE("    DW_LNS_fixed_advance_pc (" INTPTR_FORMAT ")", _state->_address);
      break;
    case DW_LNS_set_prologue_end: // No operands
      DWARF_LOG_TRACE("    DW_LNS_set_basic_block");
      _state->_prologue_end = true;
      break;
    case DW_LNS_set_epilogue_begin: // No operands
      DWARF_LOG_TRACE("    DW_LNS_set_epilogue_begin");
      _state->_epilogue_begin = true;
      break;
    case DW_LNS_set_isa: // 1 operand
      uint64_t isa;
      if (!_reader.read_uleb128(&isa, 4)) {
        // isa register is 4 bytes wide.
        return false;
      }
      _state->_isa = static_cast<uint32_t>(isa);  // only save 4 bytes
      DWARF_LOG_TRACE("    DW_LNS_set_isa (%u)", _state->_isa);
      break;
    default:
      assert(false, "Unknown standard opcode");
      return false;
  }
  return true;
}

// Specified in section 6.2.5.1 of the DWARF 4 spec.
void DwarfFile::LineNumberProgram::apply_special_opcode(const uint8_t opcode) {
  uintptr_t old_address = _state->_address;
  uint32_t old_line = _state->_line;
  uint8_t adjusted_opcode = opcode - _header._opcode_base;
  uint8_t operation_advance = adjusted_opcode / _header._line_range;
  _state->add_to_address_register(operation_advance, _header);
  if (_state->_dwarf_version == 4) {
    _state->set_index_register(operation_advance, _header);
    _state->_discriminator = 0;
  }
  _state->_line += _header._line_base + (adjusted_opcode % _header._line_range);
  DWARF_LOG_TRACE("    address += " INTPTR_FORMAT ", line += %d", _state->_address - old_address,
                  _state->_line - old_line);
  _state->_append_row = true;
  _state->_basic_block = false;
  _state->_prologue_end = false;
  _state->_epilogue_begin = false;
}

bool DwarfFile::LineNumberProgram::does_offset_match_entry(const uintptr_t previous_address, const uint32_t previous_file,
                                                           const uint32_t previous_line) {
  if (_state->_can_sequence_match_offset) {
    bool matches_entry_directly = _offset_in_library == _state->_address;
    if (matches_entry_directly
         || (_offset_in_library > previous_address && _offset_in_library < _state->_address)) { // in between two entries
      _state->_found_match = true;
      if (!matches_entry_directly || _is_pc_after_call) {
        // We take the previous row in the matrix either when:
        // - We try to match an offset that is between two entries.
        // - We have an offset from a PC that is at a call-site in which case we need to get the line information for
        //   the call instruction in the previous entry.
        print_and_store_prev_entry(previous_file, previous_line);
        return true;
      } else if (!_reader.has_bytes_left()) {
        // We take the current entry when this is the very last entry in the matrix (i.e. must be the right one).
        DWARF_LOG_DEBUG("^^^ Found line for requested offset " UINT32_FORMAT_X_0 " ^^^", _offset_in_library);
        return true;
      }
      // Else: Exact match. We cannot take this entry because we do not know if there are more entries following this
      //       one with the same offset (we could have multiple entries for the same address in the matrix). Continue
      //       to parse entries. When we have the first non-exact match, then we know that the previous entry is the
      //       correct one to take (handled in the else-if-case below). If this is the very last entry in a matrix,
      //       we will take the current entry (handled in else-if-case above).
    } else if (_state->_found_match) {
      // We found an entry before with an exact match. This is now the first entry with a new offset. Pick the previous
      // entry which matches our offset and is guaranteed to be the last entry which matches our offset (if there are
      // multiple entries with the same offset).
      print_and_store_prev_entry(previous_file, previous_line);
      return true;
    }
  }
  return false;
}

void DwarfFile::LineNumberProgram::print_and_store_prev_entry(const uint32_t previous_file, const uint32_t previous_line) {
  _state->_file = previous_file;
  _state->_line = previous_line;
  DWARF_LOG_DEBUG("^^^ Found line for requested offset " UINT32_FORMAT_X_0 " ^^^", _offset_in_library);
  // Also print the currently parsed entry.
  DWARF_LOG_DEBUG(INTPTR_FORMAT "    %-5u    %-3u       %-4u",
                  _state->_address, _state->_line, _state->_column, _state->_file);
}

// Read field file_names from the header as specified in section 6.2.4 of the DWARF 4 spec.
bool DwarfFile::LineNumberProgram::get_filename_from_header(const uint32_t file_index, char* filename, const size_t filename_len) {
  // We do not need to restore the position afterwards as this is the last step of parsing from the file for this compilation unit.
  _reader.set_position(_header._file_names_offset);
  uint32_t current_index = 1; // file_names start at index 1
  while (_reader.has_bytes_left()) {
    if (current_index == file_index) {
      // Found correct file.
      return read_filename(filename, filename_len);
    } else if (!_reader.read_string()) { // We don't care about this filename string. Read and ignore it.
      // Either an error while reading or we have reached the end of the file_names section before reaching the file_index.
      // Both should not happen.
      return false;
    }

    // We don't care about these values.
    if (!_reader.read_uleb128_ignore() // Read directory index
        || !_reader.read_uleb128_ignore()  // Read last modification of file
        || !_reader.read_uleb128_ignore()) { // Read file length
      return false;
    }
    current_index++;
  }
  DWARF_LOG_DEBUG("Did not find filename entry at index " UINT32_FORMAT " in .debug_line header", file_index);
  return false;
}

// Read the filename into the provided 'filename' buffer. If it does not fit, an alternative smaller tag will be emitted
// in order to let the DWARF parser succeed. The line number with a function name will almost always be sufficient to get
// to the actual source code location.
bool DwarfFile::LineNumberProgram::read_filename(char* filename, const size_t filename_len) {
  char next_char;
  if (!_reader.read_non_null_char(&next_char)) {
    // Either error while reading or read an empty string which indicates the end of the file_names section.
    // Both should not happen.
    return false;
  }

  filename[0] = next_char;
  size_t index = 1;
  bool overflow_filename = false; // Is the currently read filename overflowing the provided 'filename' buffer?
  while (next_char != '\0' && _reader.has_bytes_left()) {
    if (!_reader.read_byte(&next_char)) {
      return false;
    }
    if (next_char == *os::file_separator()) {
      // Skip file separator to get to the actual filename and reset the buffer and overflow flag. GCC does not emit
      // file separators while Clang does.
      index = 0;
      overflow_filename = false;
    } else if (index == filename_len) {
      // Just keep reading as we could read another file separator and reset the buffer again. But don't bother to store
      // the additionally read characters as it would not fit into the buffer anyway.
      overflow_filename = true;
    } else {
      assert(!overflow_filename, "sanity check");
      filename[index] = next_char;
      index++;
    }
  }

  if (overflow_filename) {
    // 'filename' buffer overflow. Store either a generic overflow message or a minimal filename.
    write_filename_for_overflow(filename, filename_len);
  }
  return true;
}

// Try to write a generic overflow message to the provided buffer. If it does not fit, store the minimal filename "L"
// which always fits to get the source information in the form "L:line_number".
void DwarfFile::LineNumberProgram::write_filename_for_overflow(char* filename, const size_t filename_len) {
  DWARF_LOG_ERROR("DWARF filename string is too large to fit into the provided buffer of size %zu.", filename_len);
  const size_t filename_overflow_message_length = strlen(overflow_filename) + 1;
  if (filename_overflow_message_length <= filename_len) {
    jio_snprintf(filename, filename_overflow_message_length, "%s", overflow_filename);
    DWARF_LOG_ERROR("Use overflow filename: %s", overflow_filename);
  } else {
    // Buffer too small of generic overflow message.
    DWARF_LOG_ERROR("Too small for overflow filename, use minimal filename: %c", minimal_overflow_filename);
    assert(filename_len > 1, "sanity check");
    filename[0] = minimal_overflow_filename;
    filename[1] = '\0';
  }
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
  _append_row = false;
  _do_reset = false;
  _first_entry_in_sequence = true;
  _can_sequence_match_offset = false;
}

// Defined in section 6.2.5.1 of the DWARF 4 spec.
void DwarfFile::LineNumberProgram::LineNumberProgramState::add_to_address_register(const uint32_t operation_advance,
                                                                                   const LineNumberProgramHeader& header) {
  if (_dwarf_version == 2 || _dwarf_version == 3) {
    _address += (uintptr_t)(operation_advance * header._minimum_instruction_length);
  } else if (_dwarf_version == 4) {
    _address += (uintptr_t)(header._minimum_instruction_length *
                ((_op_index + operation_advance) / header._maximum_operations_per_instruction));
  }
}

// Defined in section 6.2.5.1 of the DWARF 4 spec.
void DwarfFile::LineNumberProgram::LineNumberProgramState::set_index_register(const uint32_t operation_advance,
                                                                              const LineNumberProgramHeader& header) {
  _op_index = (_op_index + operation_advance) % header._maximum_operations_per_instruction;
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
  if (offset == 0) {
    return true;
  }
  return set_position(_current_pos + offset);
}

bool DwarfFile::MarkedDwarfFileReader::read_byte(void* result) {
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
  _current_pos += DwarfFile::ADDRESS_SIZE;
  return read(result, DwarfFile::ADDRESS_SIZE);
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
    // Invalid leb128 encoding or the read leb128 was larger than expected.
    return false;
  }

  if (is_signed && (shift < 64) && (buf & 0x40u)) {
    *result |= static_cast<uint64_t>(-1L) << shift;
  }
  return true;
}

bool DwarfFile::MarkedDwarfFileReader::read_uleb128_ignore(const int8_t check_size) {
  uint64_t dont_care;
  return read_leb128(&dont_care, check_size, false);
}

bool DwarfFile::MarkedDwarfFileReader::read_uleb128(uint64_t* result, const int8_t check_size) {
  return read_leb128(result, check_size, false);
}

bool DwarfFile::MarkedDwarfFileReader::read_sleb128(int64_t* result, const int8_t check_size) {
  return read_leb128((uint64_t*)result, check_size, true);
}

// If result is a null, we do not care about the content of the string being read.
bool DwarfFile::MarkedDwarfFileReader::read_string(char* result, const size_t result_len) {
  char first_char;
  if (!read_non_null_char(&first_char)) {
    return false;
  }

  if (result != nullptr) {
    if (result_len < 2) {
      // Strings must contain at least one non-null byte and a null byte terminator.
      return false;
    }
    result[0] = first_char;
  }

  uint8_t next_byte;
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
        result[char_index] = (char)next_byte;
      }
      char_index++;
    }
    if (next_byte == 0) {
      if (exceeded_buffer) {
        result[result_len - 1] = '\0'; // Mark end of string.
        DWARF_LOG_ERROR("Tried to read " SIZE_FORMAT " bytes but exceeded buffer size of " SIZE_FORMAT ". Truncating string.",
                        char_index, result_len);
      }
      return true;
    }
  }
  return false;
}

bool DwarfFile::MarkedDwarfFileReader::read_non_null_char(char* result) {
  if (!read_byte(result)) {
    return false;
  }
  return *result != '\0';
}

#endif // !_WINDOWS && !__APPLE__
