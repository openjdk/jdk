/*
 * Copyright (c) 1997, 2019, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_UTILITIES_ELFFILE_HPP
#define SHARE_UTILITIES_ELFFILE_HPP

#if !defined(_WINDOWS) && !defined(__APPLE__) && !defined(_AIX)

#if defined(__OpenBSD__)
#include <sys/exec_elf.h>
#else
#include <elf.h>
#endif
#include <stdio.h>

#ifdef _LP64

typedef Elf64_Half      Elf_Half;
typedef Elf64_Word      Elf_Word;
typedef Elf64_Off       Elf_Off;
typedef Elf64_Addr      Elf_Addr;

typedef Elf64_Ehdr      Elf_Ehdr;
typedef Elf64_Shdr      Elf_Shdr;
typedef Elf64_Phdr      Elf_Phdr;
typedef Elf64_Sym       Elf_Sym;

#if !defined(_ALLBSD_SOURCE) || defined(__APPLE__)
#define ELF_ST_TYPE ELF64_ST_TYPE
#endif

#else

typedef Elf32_Half      Elf_Half;
typedef Elf32_Word      Elf_Word;
typedef Elf32_Off       Elf_Off;
typedef Elf32_Addr      Elf_Addr;

typedef Elf32_Ehdr      Elf_Ehdr;
typedef Elf32_Shdr      Elf_Shdr;
typedef Elf32_Phdr      Elf_Phdr;
typedef Elf32_Sym       Elf_Sym;

#if !defined(_ALLBSD_SOURCE) || defined(__APPLE__)
#define ELF_ST_TYPE ELF32_ST_TYPE
#endif
#endif

#include "globalDefinitions.hpp"
#include "memory/allocation.hpp"
#include "utilities/decoder.hpp"

class ElfStringTable;
class ElfSymbolTable;
class ElfFuncDescTable;
class DwarfFile;

// ELF section, may or may not have cached data
class ElfSection {
private:
  Elf_Shdr      _section_hdr;
  void*         _section_data;
  NullDecoder::decoder_status _stat;
public:
  ElfSection(FILE* fd, const Elf_Shdr& hdr);
  ~ElfSection();

  NullDecoder::decoder_status status() const { return _stat; }

  const Elf_Shdr* section_header() const { return &_section_hdr; }
  const void*     section_data()   const { return (const void*)_section_data; }
private:
  // load this section.
  // it return no_error, when it fails to cache the section data due to lack of memory
  NullDecoder::decoder_status load_section(FILE* const file, const Elf_Shdr& hdr);
};

class FileReader : public StackObj {
protected:
  FILE* const _fd;
public:
  FileReader(FILE* const fd) : _fd(fd) {};
  bool read(void* buf, size_t size);
  int  read_buffer(void* buf, size_t size);
  bool set_position(long offset);
};

// Mark current position, so we can get back to it after
// reads.
class MarkedFileReader : public FileReader {
private:
  long  _marked_pos;
public:
  MarkedFileReader(FILE* const fd);
  ~MarkedFileReader();

  bool has_mark() const { return _marked_pos >= 0; }
};

// ElfFile is basically an elf file parser, which can lookup the symbol
// that is the nearest to the given address.
// Beware, this code is called from vm error reporting code, when vm is already
// in "error" state, so there are scenarios, lookup will fail. We want this
// part of code to be very defensive, and bait out if anything went wrong.
class ElfFile: public CHeapObj<mtInternal> {
  friend class ElfDecoder;

private:
  // link ElfFiles
  ElfFile*          _next;

  // Elf file
  char*             _filepath;
  FILE*             _file;

  // symbol tables
  ElfSymbolTable*   _symbol_tables;

  // regular string tables
  ElfStringTable*   _string_tables;

  // section header string table, used for finding section name
  ElfStringTable*   _shdr_string_table;

  // function descriptors table
  ElfFuncDescTable* _funcDesc_table;

  NullDecoder::decoder_status  _status;

  DwarfFile* _debuginfo_file;
protected:
  // Elf header
  Elf_Ehdr          _elfHdr;

public:
  ElfFile(const char* filepath);
  ~ElfFile();

  bool decode(address addr, char* buf, int buflen, int* offset);

  const char* filepath() const {
    return _filepath;
  }

  bool same_elf_file(const char* filepath) const {
    assert(filepath != NULL, "null file path");
    return (_filepath != NULL && !strcmp(filepath, _filepath));
  }

  NullDecoder::decoder_status get_status() const {
    return _status;
  }

  // Returns true if the elf file is marked NOT to require an executable stack,
  // or if the file could not be opened.
  // Returns false if the elf file requires an executable stack, the stack flag
  // is not set at all, or if the file can not be read.
  // On systems other than linux it always returns false.
  static bool specifies_noexecstack(const char* filepath) NOT_LINUX({ return false; });


  bool open_valid_debuginfo_file(const char* path_name, uint crc);

  bool get_source_info(int offset_in_library, char* buf, size_t buflen, int* line);

private:
  // sanity check, if the file is a real elf file
  static bool is_elf_file(Elf_Ehdr&);

  // parse this elf file
  NullDecoder::decoder_status parse_elf(const char* filename);

  // load string, symbol and function descriptor tables from the elf file
  NullDecoder::decoder_status load_tables();

  ElfFile*  next() const { return _next; }
  void set_next(ElfFile* file) { _next = file; }

  // string tables are stored in a linked list
  void add_string_table(ElfStringTable* table);

  // symbol tables are stored in a linked list
  void add_symbol_table(ElfSymbolTable* table);

  // return a string table at specified section index
  ElfStringTable* get_string_table(int index);



  // Cleanup string, symbol and function descriptor tables
  void cleanup_tables();

  static uint gnu_debuglink_crc32(uint crc, unsigned char* buf, size_t len);

  bool load_debuginfo_file();

protected:
  FILE* const fd() const { return _file; }

  // find a section by name, return section index
  // if there is no such section, return -1
  int section_by_name(const char* name, Elf_Shdr& hdr);
public:
  // For whitebox test
  static bool _do_not_cache_elf_section;
};

class DwarfFile : public ElfFile {

  // Tag encoding from Figure 18 in DWARF 4 spec
  static constexpr uint8_t DW_TAG_compile_unit = 0x11;

  // Child determination encoding from Figure 19 in DWARF 4 spec
  static constexpr uint8_t DW_CHILDREN_yes = 0x01;

  // Attribute encoding from Figure 20 in DWARF 4 spec
  static constexpr uint8_t DW_AT_stmt_list = 0x10;

  // Attribute form encodings from Figure 21 in DWARF 4 spec
  static constexpr uint8_t DW_FORM_addr = 0x01; // address
  static constexpr uint8_t DW_FORM_block2 = 0x03; // block
  static constexpr uint8_t DW_FORM_block4 = 0x04; // block
  static constexpr uint8_t DW_FORM_data2 = 0x05; // constant
  static constexpr uint8_t DW_FORM_data4 = 0x06; // constant
  static constexpr uint8_t DW_FORM_data8 = 0x07; // constant
  static constexpr uint8_t DW_FORM_string = 0x08; // string
  static constexpr uint8_t DW_FORM_block = 0x09; // block
  static constexpr uint8_t DW_FORM_block1 = 0x0a; // block
  static constexpr uint8_t DW_FORM_data1 = 0x0b; // constant
  static constexpr uint8_t DW_FORM_flag = 0x0c; // flag
  static constexpr uint8_t DW_FORM_sdata = 0x0d; // constant
  static constexpr uint8_t DW_FORM_strp = 0x0e; // string
  static constexpr uint8_t DW_FORM_udata = 0x0f; // constant
  static constexpr uint8_t DW_FORM_ref_addr = 0x10; // reference0;
  static constexpr uint8_t DW_FORM_ref1 = 0x11; // reference
  static constexpr uint8_t DW_FORM_ref2 = 0x12; // reference
  static constexpr uint8_t DW_FORM_ref4 = 0x13; // reference
  static constexpr uint8_t DW_FORM_ref8 = 0x14; // reference
  static constexpr uint8_t DW_FORM_ref_udata = 0x15; // reference
  static constexpr uint8_t DW_FORM_indirect = 0x16; // see Section 7.5.3
  static constexpr uint8_t DW_FORM_sec_offset = 0x17; // lineptr, loclistptr, macptr, rangelistptr
  static constexpr uint8_t DW_FORM_exprloc = 0x18;// exprloc
  static constexpr uint8_t DW_FORM_flag_present = 0x19; // flag
  static constexpr uint8_t DW_FORM_ref_sig8 = 0x20; // reference


  // See DWARF4 specification section 6.1.2.
  struct DebugArangesSetHeader32 {
    // The total length of all of the entries for that set, not including the length field itself
    uint32_t unit_length;

    // This number is specific to the address lookup table and is independent of the DWARF version number.
    uint16_t version;

    // The offset from the beginning of the .debug_info or .debug_types section of the compilation unit header referenced
    // by the set. In this implementation we only use it as offset into .debug_info.
    uint32_t debug_info_offset;

    // The size of an address in bytes on the target architecture. For segmented addressing, this is the size of the offset
    // portion of the address.
    uint8_t address_size;

    // The size of a segment selector in bytes on the target architecture. If the target system uses a flat address space,
    // this value is 0.
    uint8_t segment_size;

    static const uint SIZE = 12; // 4 + 2 + 4 + 1 + 1

    void print_fields() const {
      tty->print_cr("%x", unit_length);
      tty->print_cr("%x", version);
      tty->print_cr("%x", debug_info_offset);
      tty->print_cr("%x", address_size);
      tty->print_cr("%x", segment_size);
    }
  };

  struct DebugArangesSet64 {
    address beginning_address;
    uint64_t length;
  };

  static bool is_terminating_set(DebugArangesSet64 set) {
    return set.beginning_address == 0 && set.length == 0;
  }

  // See DWARF4 spec section 7.5.1.1
  struct CompilationUnitHeader32 {
    //  the length of the .debug_info contribution for that compilation unit, not including the length field itself.
    uint32_t unit_length;

    // The version of the DWARF information for the compilation unit. The value in this field is 4 for DWARF 4.
    uint16_t version;

    // The unsigned offset into the .debug_abbrev section. This offset associates the compilation unit with a particular
    // set of debugging information entry abbreviations.
    uint32_t debug_abbrev_offset;

    // The size in bytes of an address on the target architecture. If the system uses segmented addressing, this value
    // represents the size of the offset portion of an address.
    uint8_t  address_size;
  };

  static bool read_uleb128(MarkedFileReader* mfd, uint64_t* result);

 public:
  bool find_compilation_unit_offset(const int offset_in_library, uint64_t* compilation_unit_offset);
  bool find_debug_line_offset(const int offset_in_library, const uint64_t compilation_unit_offset, uint64_t* debug_line_offset);

  DwarfFile(const char* filepath) : ElfFile(filepath) {}
  bool get_line_number(int offset_in_library, char* buf, size_t buflen, int* line);

  bool get_debug_line_offset_from_debug_abbrev(MarkedFileReader* debug_info_reader, const CompilationUnitHeader32 *cu_header, const uint64_t abbrev_code, uint64_t *debug_line_offset);

  bool process_attribute(const uint64_t attribute, const uint8_t address_size, long* current_debug_info_position,uint64_t* debug_line_offset = nullptr);
};

#endif // !_WINDOWS && !__APPLE__

#endif // SHARE_UTILITIES_ELFFILE_HPP
