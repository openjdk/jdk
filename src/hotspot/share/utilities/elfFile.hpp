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

#include "jvm_md.h"
#include "globalDefinitions.hpp"
#include "memory/allocation.hpp"
#include "utilities/checkedCast.hpp"
#include "utilities/decoder.hpp"

#ifdef ASSERT
// Helper macros to print different log levels during DWARF parsing
#define DWARF_LOG_SUMMARY(format, ...) DWARF_LOG_WITH_LEVEL(1, format, ##__VA_ARGS__) // Same level as error logging
#define DWARF_LOG_ERROR(format, ...) DWARF_LOG_WITH_LEVEL(1, format, ##__VA_ARGS__)
#define DWARF_LOG_INFO(format, ...) DWARF_LOG_WITH_LEVEL(2, format, ##__VA_ARGS__)
#define DWARF_LOG_DEBUG(format, ...) DWARF_LOG_WITH_LEVEL(3, format, ##__VA_ARGS__)
#define DWARF_LOG_TRACE(format, ...) DWARF_LOG_WITH_LEVEL(4, format, ##__VA_ARGS__)

#define DWARF_LOG_WITH_LEVEL(level, format, ...) \
    if (TraceDwarfLevel >= level) {         \
      tty->print("[dwarf] ");               \
      tty->print_cr(format, ##__VA_ARGS__); \
    }
#else
#define DWARF_LOG_SUMMARY(format, ...)
#define DWARF_LOG_ERROR(format, ...)
#define DWARF_LOG_INFO(format, ...)
#define DWARF_LOG_DEBUG(format, ...)
#define DWARF_LOG_TRACE(format, ...)
#endif

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
  size_t read_buffer(void* buf, size_t size);
  virtual bool set_position(long offset);
};

// Mark current position, so we can get back to it after
// reads.
class MarkedFileReader : public FileReader {
 protected:
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

  DwarfFile* _dwarf_file;
  static const char* USR_LIB_DEBUG_DIRECTORY;
 protected:
  // Elf header
  Elf_Ehdr          _elfHdr;

 public:
  ElfFile(const char* filepath);
  virtual ~ElfFile();

  bool decode(address addr, char* buf, int buflen, int* offset);

  bool same_elf_file(const char* filepath) const {
    assert(filepath != nullptr, "null file path");
    return (_filepath != nullptr && !strcmp(filepath, _filepath));
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

  bool get_source_info(uint32_t offset_in_library, char* filename, size_t filename_len, int* line, bool is_pc_after_call);

 private:
  // sanity check, if the file is a real elf file
  static bool is_elf_file(Elf_Ehdr&);

  // parse this elf file
  NullDecoder::decoder_status parse_elf(const char* filename);

  // load string, symbol and function descriptor tables from the elf file
  NullDecoder::decoder_status load_tables();

  ElfFile*  next() const { return _next; }
  void set_next(ElfFile* file) { _next = file; }

#if defined(PPC64) && !defined(ABI_ELFv2)
  // find a section by name, return section index
  // if there is no such section, return -1
  int section_by_name(const char* name, Elf_Shdr& hdr);
#endif

  // string tables are stored in a linked list
  void add_string_table(ElfStringTable* table);

  // symbol tables are stored in a linked list
  void add_symbol_table(ElfSymbolTable* table);

  // return a string table at specified section index
  ElfStringTable* get_string_table(int index);

  // Cleanup string, symbol and function descriptor tables
  void cleanup_tables();

  bool create_new_dwarf_file(const char* filepath);

  // Struct to store the debug info read from the .gnu_debuglink section.
  struct DebugInfo {
    static const uint8_t CRC_LEN = 4;

    char _dwarf_filename[JVM_MAXPATHLEN];
    uint32_t _crc;
  };

  // Helper class to create DWARF paths when loading a DWARF file.
  class DwarfFilePath {
   private:
    static const uint16_t MAX_DWARF_PATH_LENGTH = JVM_MAXPATHLEN;
    const char* _filename;
    char _path[MAX_DWARF_PATH_LENGTH];
    const uint32_t _crc;
    uint16_t _null_terminator_index; // Index for the current null terminator of the string stored in _path

    bool check_valid_path() const {
      return _path[MAX_DWARF_PATH_LENGTH - 1] == '\0';
    }

    void update_null_terminator_index() {
      _null_terminator_index = checked_cast<uint16_t>(strlen(_path));
    }

    bool copy_to_path_index(uint16_t index_in_path, const char* src);

   public:
    DwarfFilePath(DebugInfo& debug_info)
      : _filename(debug_info._dwarf_filename), _crc(debug_info._crc), _null_terminator_index(0) {
      _path[MAX_DWARF_PATH_LENGTH - 1] = '\0';  // Ensures to have a null terminated string and not read beyond the buffer limit.
    }

    const char* path() const {
      return _path;
    }

    const char* filename() const {
      return _filename;
    }

    uint32_t crc() const {
      return _crc;
    }

    bool set(const char* src);

    bool set_filename_after_last_slash() {
      return set_after_last_slash(_filename);
    }

    bool set_after_last_slash(const char* src);
    bool append(const char* src);
  };

  // Load the DWARF file (.debuginfo) that belongs to this file either from (checked in listed order):
  // - Same directory as the library file.
  // - User defined path in environmental variable _JVM_DWARF_PATH.
  // - Subdirectory .debug in same directory as the library file.
  // - /usr/lib/debug directory
  bool load_dwarf_file();


  bool read_debug_info(DebugInfo* debug_info) const;

  bool load_dwarf_file_from_same_directory(DwarfFilePath& dwarf_file_path);
  bool load_dwarf_file_from_env_var_path(DwarfFilePath& dwarf_file_path);
  bool load_dwarf_file_from_env_path_folder(DwarfFilePath& dwarf_file_path, const char* dwarf_path_from_env, const char* folder);
  bool load_dwarf_file_from_debug_sub_directory(DwarfFilePath& dwarf_file_path);
  bool load_dwarf_file_from_usr_lib_debug(DwarfFilePath& dwarf_file_path);
  bool open_valid_debuginfo_file(const DwarfFilePath& dwarf_file_path);
  static uint32_t get_file_crc(FILE* const file);
  static uint gnu_debuglink_crc32(uint32_t crc, uint8_t* buf, size_t len);

 protected:
  FILE* fd() const { return _file; }

  // Read the section header of section 'name'.
  bool read_section_header(const char* name, Elf_Shdr& hdr) const;
  bool is_valid_dwarf_file() const;

 public:
  // For whitebox test
  static bool _do_not_cache_elf_section;
};


/*
 * This class parses and reads filename and line number information from an associated .debuginfo file that belongs to
 * this ELF file or directly from this ELF file if there is no separate .debuginfo file. The debug info is written by GCC
 * in DWARF - a standardized debugging data format. There are special sections where the DWARF info is written to. These
 * sections can either be put into the same ELF file or a separate .debuginfo file. For simplicity, when referring to the
 * "DWARF file" or the ".debuginfo file" we just mean the file that contains the required DWARF sections. The current version
 * of GCC uses DWARF version 4 as default which is defined in the official standard: http://www.dwarfstd.org/doc/DWARF4.pdf.
 * This class is able to parse 32-bit DWARF version 4 for 32 and 64-bit Linux builds. GCC does not emit 64-bit DWARF and
 * therefore is not supported by this parser. For some reason, GCC emits DWARF version 3 for the .debug_line section as a
 * default. This parser was therefore adapted to support DWARF version 3 and 4 for the .debug_line section. Apart from that,
 * other DWARF versions, especially the newest version 5, are not (yet) supported.
 *
 * Description of used DWARF file sections:
 * - .debug_aranges: A table that consists of sets of variable length entries, each set describing the portion of the
 *                   program's address space that is covered by a single compilation unit. In other words, the entries
 *                   describe a mapping between addresses and compilation units.
 * - .debug_info:    The core DWARF data containing DWARF Information Entries (DIEs). Each DIE consists of a tag and a
 *                   series of attributes. Each (normal) compilation unit is represented by a DIE with the tag
 *                   DW_TAG_compile_unit and contains children. For our purposes, we are only interested in this DIE to
 *                   get to the .debug_line section. We do not care about the children. This parser currently only
 *                   supports normal compilation units and no partial compilation or type units.
 * - .debug_abbrev:  Represents abbreviation tables for all compilation units. A table for a specific compilation unit
 *                   consists of a series of abbreviation declarations. Each declaration specifies a tag and attributes
 *                   for a DIE. The DIEs from the compilation units in the .debug_info section need the abbreviation table
 *                   to decode their attributes (their meaning and size).
 * - .debug_line:    Contains filename and line number information for each compilation unit. To get the information, a
 *                   state machine needs to be executed which generates a matrix. Each row of this matrix describes the
 *                   filename and line number (among other information) for a specific offset in the associated ELF library
 *                   file. The state machine is executed until the row for the requested offset is found. The filename and
 *                   line number information can then be fetched with the current register values of the state machine.
 *
 * Algorithm
 * ---------
 * Given: Offset into the ELF file library.
 * Return: Filename and line number for this offset.
 * (1) First, the path to the .debuginfo DWARF file is found by inspecting the .gnu_debuglink section of the library file.
 *     The DWARF file is then opened by calling the constructor of this class. Once this is done, the processing of the
 *     DWARF file is initiated by calling find_filename_and_line_number().
 * (2) Find the compilation unit offset by reading entries from the section .debug_aranges, which contain address range
 *     descriptors, until we find the correct descriptor that includes the library offset.
 * (3) Find the .debug_line offset for the line number information program from the .debug_info section:
 *     (a) Parse the compilation unit header from the .debug_info section at the offset obtained by (2).
 *     (b) Read the debug_abbrev_offset into the .debug_abbrev section that belongs to this compilation unit from the
 *         header obtained in (3a).
 *     (c) Read the abbreviation code that immediately follows the compilation unit header from (3a) which is needed to
 *         find the correct entry in the .debug_abbrev section.
 *     (d) Find the correct entry in the abbreviation table in the .debug_abbrev section by starting to parse entries at
 *         the debug_abbrev_offset from (3b) until we find the correct one matching the abbreviation code from (3c).
 *     (e) Read the specified attributes of the abbreviation entry from (3d) from the compilation unit (in the .debug_info
 *         section) until we find the attribute DW_AT_stmt_list. This attributes represents an offset into the .debug_line
 *         section which contains the line number program information to get the filename and the line number.
 *  (4) Find the filename and line number belonging to the given library offset by running the line number program state
 *      machine with its registers. This creates a matrix where each row stores information for specific addresses (library
 *      offsets). The state machine executes different opcodes which modify the state machine registers. Certain opcodes
 *      will add a new row to the matrix by taking the current values of state machine registers. As soon as the correct
 *      matrix row matching the library offset is found, we can read the line number from the line register of the state
 *      machine and parse the filename from the line number program header with the given file index from the file register
 *      of the state machine.
 *
 *  More details about the different phases can be found at the associated classes and methods. A visualization of the
 *  algorithm inside the different sections can be found in the class comments for DebugAranges, DebugAbbrev and
 *  LineNumberProgram further down in this file.
 *
 *  Available (develop) log levels (-XX:TraceDwarfLevel=[1,4]) which are only present in debug builds. Each level prints
 *  all the logs of the previous levels and adds some more fine-grained logging:
 *  - Level 1 (summary + errors):
 *    - Prints the path of parsed DWARF file together with the resulting source information.
 *    - Prints all errors.
 *  - Level 2 (info):
 *    - Prints the found offsets of all DWARF sections
 *  - Level 3 (debug):
 *    - Prints the results of the steps (1) - (4) together with the generated line information matrix.
 *  - Level 4 (trace):
 *    - Complete information about intermediate states/results when parsing the DWARF file.
 */
class DwarfFile : public ElfFile {

  static constexpr uint8_t ADDRESS_SIZE = NOT_LP64(4) LP64_ONLY(8);
  // We only support 32-bit DWARF (emitted by GCC) which uses 32-bit values for DWARF section lengths and offsets
  // relative to the beginning of a section.
  static constexpr uint8_t DWARF_SECTION_OFFSET_SIZE = 4;

  class MarkedDwarfFileReader : public MarkedFileReader {
   private:
    long _current_pos;
    long _max_pos; // Used to guarantee that we stop reading in case we reached the end of a section.

    bool read_leb128(uint64_t* result, int8_t check_size, bool is_signed);
   public:
    MarkedDwarfFileReader(FILE* const fd) : MarkedFileReader(fd), _current_pos(-1), _max_pos(-1) {}

    virtual bool set_position(long new_pos);
    long get_position() const { return _current_pos; }
    void set_max_pos(long max_pos) { _max_pos = max_pos; }
    // Have we reached the limit of maximally allowable bytes to read? Used to ensure to stop reading when a section ends.
    bool has_bytes_left() const;
    // Call this if another file reader has changed the position of the same file handle.
    bool update_to_stored_position();
    // Must be called to restore the old position before this file reader changed it with update_to_stored_position().
    bool reset_to_previous_position();
    bool move_position(long offset);
    bool read_byte(void* result);
    bool read_word(uint16_t* result);
    bool read_dword(uint32_t* result);
    bool read_qword(uint64_t* result);
    bool read_uleb128_ignore(int8_t check_size = -1);
    bool read_uleb128(uint64_t* result, int8_t check_size = -1);
    bool read_sleb128(int64_t* result, int8_t check_size = -1);
    // Reads 4 bytes for 32-bit and 8 bytes for 64-bit builds.
    bool read_address_sized(uintptr_t* result);
    bool read_string(char* result = nullptr, size_t result_len = 0);
    bool read_non_null_char(char* result);
  };

  // (2) Processing the .debug_aranges section to find the compilation unit which covers offset_in_library.
  // This is specified in section 6.1.2 of the DWARF 4 spec.
  //
  // Structure of .debug_aranges:
  //   Section Header
  //   % Table of variable length sets describing the address space covered by a compilation unit
  //     % Set 1
  //     ...
  //     % Set i:
  //       % Set header
  //         ...
  //         debug_info_offset -> offset to compilation unit
  //       % Series of address range descriptors [beginning_address, range_length]:
  //         % Descriptor 1
  //         ...
  //         % Descriptor j:
  //           beginning_address <= offset_in_library < beginning_address + range_length?
  //           => Found the correct set covering offset_in_library. Take debug_info_offset from the set header to get
  //              to the correct compilation unit in .debug_info.
  class DebugAranges {

    // The header is defined in section 6.1.2 of the DWARF 4 spec.
    struct DebugArangesSetHeader {
      // The total length of all of the entries for that set, not including the length field itself.
      uint32_t _unit_length;

      // This number is specific to the address lookup table and is independent of the DWARF version number.
      uint16_t _version;

      // The offset from the beginning of the .debug_info or .debug_types section of the compilation unit header referenced
      // by the set. In this parser we only use it as offset into .debug_info. This must be 4 bytes for 32-bit DWARF.
      uint32_t _debug_info_offset;

      // The size of an address in bytes on the target architecture, 4 bytes for 32-bit and 8 bytes for 64-bit Linux builds.
      uint8_t _address_size;

      // The size of a segment selector in bytes on the target architecture. This should be 0.
      uint8_t _segment_size;
    };

    // Address descriptor defining a range that is covered by a compilation unit. It is defined in section 6.1.2 after
    // the set header in the DWARF 4 spec.
    struct AddressDescriptor {
      uintptr_t beginning_address = 0;
      uintptr_t range_length = 0;
    };

    DwarfFile* _dwarf_file;
    MarkedDwarfFileReader _reader;
    uintptr_t _section_start_address;

    // a calculated end position
    long _entry_end;

    bool read_section_header();
    bool read_set_header(DebugArangesSetHeader& header);
    bool read_address_descriptors(const DwarfFile::DebugAranges::DebugArangesSetHeader& header,
                                  uint32_t offset_in_library, bool& found_matching_set);
    bool read_address_descriptor(AddressDescriptor& descriptor);
    static bool does_match_offset(uint32_t offset_in_library, const AddressDescriptor& descriptor) ;
    bool is_terminating_entry(const DwarfFile::DebugAranges::DebugArangesSetHeader& header,
                              const AddressDescriptor& descriptor);
   public:
    DebugAranges(DwarfFile* dwarf_file) : _dwarf_file(dwarf_file), _reader(dwarf_file->fd()),
                                          _section_start_address(0), _entry_end(0) {}
    bool find_compilation_unit_offset(uint32_t offset_in_library, uint32_t* compilation_unit_offset);

  };

  // (3a-c,e) The compilation unit is read from the .debug_info section. The structure of .debug_info is shown in the
  // comments of class DebugAbbrev.
  class CompilationUnit {

    // Attribute form encodings from Figure 21 in section 7.5 of the DWARF 4 spec.
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

    // The header is defined in section 7.5.1.1 of the DWARF 4 spec.
    struct CompilationUnitHeader {
      // The length of the .debug_info contribution for that compilation unit, not including the length field itself.
      uint32_t _unit_length;

      // The version of the DWARF information for the compilation unit. The value in this field is 4 for DWARF 4.
      uint16_t _version;

      // The offset into the .debug_abbrev section. This offset associates the compilation unit with a particular set of
      // debugging information entry abbreviations.
      uint32_t _debug_abbrev_offset;

      // The size in bytes of an address on the target architecture, 4 bytes for 32-bit and 8 bytes for 64-bit Linux builds.
      uint8_t  _address_size;
    };

    DwarfFile* _dwarf_file;
    MarkedDwarfFileReader _reader;
    CompilationUnitHeader _header;
    const uint32_t _compilation_unit_offset;

    // Result of a request initiated by find_debug_line_offset().
    uint32_t _debug_line_offset;

    bool read_header();
   public:
    CompilationUnit(DwarfFile* dwarf_file, uint32_t compilation_unit_offset)
      : _dwarf_file(dwarf_file), _reader(dwarf_file->fd()), _compilation_unit_offset(compilation_unit_offset), _debug_line_offset(0) {}

    bool find_debug_line_offset(uint32_t* debug_line_offset);
    bool read_attribute_value(uint64_t attribute_form, bool is_DW_AT_stmt_list_attribute);
  };

  // (3d) Read from the .debug_abbrev section at the debug_abbrev_offset specified by the compilation unit header.
  //
  // The interplay between the .debug_info and .debug_abbrev sections is more complex. The following visualization of the structure
  // of both sections support the comments found in the parsing steps of the CompilationUnit and DebugAbbrev class.
  //
  // Structure of .debug_abbrev:
  //   Section Header
  //   % Series of abbreviation tables
  //     % Abbreviation table 1
  //     ...
  //     % Abbreviation table for compilation unit at debug_abbrev_offset:
  //       % Series of declarations:
  //         % Declaration 1:
  //           abbreviation code
  //           tag
  //           DW_CHILDREN_yes/no
  //           % Series of attribute specifications
  //             % Attribute specification 1:
  //             attribute name
  //             attribute form
  //             ...
  //             % Last attribute specification:
  //             0
  //             0
  //         ...
  //         % Declaration i:
  //           Abbrev code read from compilation unit [AC]
  //           DW_TAG_compile_unit
  //           DW_CHILDREN_yes
  //           % Series of attribute specifications
  //             % Attribute specification 1 [AS1]
  //             ...
  //             % Attribute specification j [ASj]:
  //             DW_AT_stmt_list
  //             DW_FORM_sec_offset
  //
  //
  // Structure of .debug_info:
  //   Section Header
  //   % Series of compilation units
  //     % Compilation unit 1
  //     ...
  //     % Compilation unit i for library offset fetched from .debug_aranges:
  //       % Compilation unit header:
  //         ...
  //         debug_abbrev_offset -> offset for abbreviation table in .debug_abbrev for this compilation unit
  //         ...
  //       Abbrev code -> used in .debug_abbrev to find the correct declaration [AC]
  //       % Series of attribute values
  //         Attribute value 1 (in the format defined by attribute specification 1 [AS1])
  //         ...
  //         Attribute value j (in the format defined by attribute specification j [ASj]):
  //         => Specifies Offset to line number program for this compilation unit in .debug_line
  class DebugAbbrev {

    struct AbbreviationDeclaration {
      uint64_t _abbrev_code;
      uint64_t _tag;
      uint8_t _has_children;
    };

    struct AttributeSpecification {
      uint64_t _name;
      uint64_t _form;
    };

    // Tag encoding from Figure 18 in section 7.5 of the DWARF 4 spec.
    static constexpr uint8_t DW_TAG_compile_unit = 0x11;

    // Child determination encoding from Figure 19 in section 7.5 of the DWARF 4 spec.
    static constexpr uint8_t DW_CHILDREN_yes = 0x01;

    // Attribute encoding from Figure 20 in section 7.5 of the DWARF 4 spec.
    static constexpr uint8_t DW_AT_stmt_list = 0x10;

    /* There is no specific header for this section */

    DwarfFile* _dwarf_file;
    MarkedDwarfFileReader _reader;
    CompilationUnit* _compilation_unit; // Need to read from compilation unit while parsing the entries in .debug_abbrev.

    // Result field of a request
    uint32_t* _debug_line_offset;

    bool read_declaration(AbbreviationDeclaration& declaration);
    static bool is_wrong_or_unsupported_format(const AbbreviationDeclaration& declaration);
    bool read_attribute_specifications(bool is_DW_TAG_compile_unit);
    bool read_attribute_specification(AttributeSpecification& specification);
    static bool is_terminating_specification(const AttributeSpecification& attribute_specification) ;

   public:
    DebugAbbrev(DwarfFile* dwarf_file, CompilationUnit* compilation_unit) :
      _dwarf_file(dwarf_file), _reader(_dwarf_file->fd()), _compilation_unit(compilation_unit),
      _debug_line_offset(nullptr) {}

    bool read_section_header(uint32_t debug_abbrev_offset);
    bool find_debug_line_offset(uint64_t abbrev_code);
  };

  // (4) The line number program for the compilation unit at the offset of the .debug_line obtained by (3).
  // For some reason, earlier GCC versions emit the line number program in DWARF 2 or 3 format even though the
  // default is DWARF 4. It also mixes the standards (see comments in the parsing code).
  //
  // Therefore, this class supports DWARF 2, 3 and 4 parsing as specified in section 6.2 of the DWARF specs.
  // The parsing of DWARF 2 is already covered by the parsing of DWARF 3 as they use the shared opcodes in the same way.
  // The parsing of DWARF 4, however, needs some adaptation as it consumes more data for some shared opcodes.
  //
  // DWARF 2 standard: https://dwarfstd.org/doc/dwarf-2.0.0.pdf
  // DWARF 3 standard: https://dwarfstd.org/doc/Dwarf3.pdf
  //
  //
  // Structure of .debug_ling:
  //   Section Header
  //   % Series of line number program entries for each compilation unit
  //     % Line number program 1
  //     ...
  //     % Line number program i for our compilation unit:
  //       % Line program header unit header:
  //         ...
  //         version -> currently emits version 3 by default
  //         ...
  //         file_name -> sequence of file names
  //       % Sequence of opcodes as part of the line number program to build the line number information matrix:
  //          % Format of matrix: [offset, line, directory_index, file_index]
  //          % Line 1
  //          ...
  //          % Line j:
  //            [offset matching offset_in_library, line, directory_index, file_index]
  //            => Get line number + look up file_index in file_name list (pick file_index'th string)
  class LineNumberProgram {

    // Standard opcodes for the line number program defined in section 6.2.5.2 of the DWARF 4 spec.
    static constexpr uint8_t DW_LNS_copy = 1;
    static constexpr uint8_t DW_LNS_advance_pc = 2;
    static constexpr uint8_t DW_LNS_advance_line = 3;
    static constexpr uint8_t DW_LNS_set_file = 4;
    static constexpr uint8_t DW_LNS_set_column = 5;
    static constexpr uint8_t DW_LNS_negate_stmt = 6;
    static constexpr uint8_t DW_LNS_set_basic_block = 7;
    static constexpr uint8_t DW_LNS_const_add_pc = 8;
    static constexpr uint8_t DW_LNS_fixed_advance_pc = 9;
    static constexpr uint8_t DW_LNS_set_prologue_end = 10; // Introduced with DWARF 3
    static constexpr uint8_t DW_LNS_set_epilogue_begin = 11; // Introduced with DWARF 3
    static constexpr uint8_t DW_LNS_set_isa = 12; // Introduced with DWARF 3

    // Extended opcodes for the line number program defined in section 6.2.5.2 of the DWARF 4 spec.
    static constexpr uint8_t DW_LNE_end_sequence = 1;
    static constexpr uint8_t DW_LNE_set_address = 2;
    static constexpr uint8_t DW_LNE_define_file = 3;
    static constexpr uint8_t DW_LNE_set_discriminator = 4; // Introduced with DWARF 4

    static constexpr const char* overflow_filename = "<OVERFLOW>";
    static constexpr const char minimal_overflow_filename = 'L';

    // The header is defined in section 6.2.4 of the DWARF 4 spec.
    struct LineNumberProgramHeader {
      // The size in bytes of the line number information for this compilation unit, not including the unit_length
      // field itself. 32-bit DWARF uses 4 bytes.
      uint32_t _unit_length;

      // The version of the DWARF information for the line number program unit. The value in this field should be 4 for
      // DWARF 4 and version 3 as used for DWARF 3.
      uint16_t _version;

      // The number of bytes following the header_length field to the beginning of the first byte of the line number
      // program itself. 32-bit DWARF uses 4 bytes.
      uint32_t _header_length;

      // The size in bytes of the smallest target machine instruction. Line number program opcodes that alter the address
      // and op_index registers use this and maximum_operations_per_instruction in their calculations.
      uint8_t _minimum_instruction_length;

      // The maximum number of individual operations that may be encoded in an instruction. Line number program opcodes
      // that alter the address and op_index registers use this and minimum_instruction_length in their calculations.
      // For non-VLIW architectures, this field is 1, the op_index register is always 0, and the operation pointer is
      // simply the address register. This is only used with DWARF 4.
      uint8_t _maximum_operations_per_instruction;

      // The initial value of the is_stmt register.
      uint8_t _default_is_stmt;

      // This parameter affects the meaning of the special opcodes.
      int8_t _line_base;

      // This parameter affects the meaning of the special opcodes.
      uint8_t _line_range;

      // The number assigned to the first special opcode.
      uint8_t _opcode_base;

      // This array specifies the number of LEB128 operands for each of the standard opcodes. The first element of the
      // array corresponds to the opcode whose value is 1, and the last element corresponds to the opcode whose value is
      // opcode_base-1. DWARF 2 uses 9 standard opcodes while DWARF 3 and 4 use 12.
      uint8_t _standard_opcode_lengths[12];

      /*
       * The following fields are not part of the real header and are only used for the implementation.
       */
      // Offset where the filename strings are starting in header.
      long _file_names_offset;

      // _header_length only specifies the number of bytes following the _header_length field. It does not include
      // the size of _unit_length, _version and _header_length itself. This constant represents the number of missing
      // bytes to get the real size of the header:
      // sizeof(_unit_length) + sizeof(_version) + sizeof(_header_length) = 4 + 2 + 4 = 10
      static constexpr uint8_t HEADER_DESCRIPTION_BYTES = 10;
    };

    // The line number program state consists of several registers that hold the current state of the line number program
    // state machine. The state/different state registers are defined in section 6.2.2 of the DWARF 4 spec. Most of these
    // fields (state registers) are not used to get the filename and the line number information.
    struct LineNumberProgramState : public CHeapObj<mtInternal> {
      // The program-counter value corresponding to a machine instruction generated by the compiler.
      // 4 bytes on 32-bit and 8 bytes on 64-bit.
      uintptr_t _address;

      // The index of an operation within a VLIW instruction. The index of the first operation is 0. For non-VLIW
      // architectures, this register will always be 0.
      // The address and op_index registers, taken together, form an operation pointer that can reference any
      // individual operation with the instruction stream. This field was introduced with DWARF 4.
      uint32_t _op_index;

      // The identity of the source file corresponding to a machine instruction.
      uint32_t _file;

      // A source line number. Lines are numbered beginning at 1. The compiler may emit the value 0 in cases where an
      // instruction cannot be attributed to any source line.
      uint32_t _line;

      // A column number within a source line. Columns are numbered beginning at 1. The value 0 is reserved to indicate
      // that a statement begins at the "left edge" of the line.
      uint32_t _column;

      // Indicates that the current instruction is a recommended breakpoint location.
      bool _is_stmt;

      // Indicates that the current instruction is the beginning of a basic block.
      bool _basic_block;

      // Indicates that the current address is that of the first byte after the end of a sequence of target machine
      // instructions. end_sequence terminates a sequence of lines.
      bool _end_sequence;

      // Indicates that the current address is one (of possibly many) where execution should be suspended for an entry
      // breakpoint of a function. This field was introduced with DWARF 3.
      bool _prologue_end;

      // Indicates that the current address is one (of possibly many) where execution should be suspended for an exit
      // breakpoint of a function. This field was introduced with DWARF 3.
      bool _epilogue_begin;

      // Encodes the applicable instruction set architecture for the current instruction.
      // This field was introduced with DWARF 3.
      uint32_t _isa;

      // Identifies the block to which the current instruction belongs. This field was introduced with DWARF 4.
      uint32_t _discriminator;

      /*
       * Additional fields which are not part of the actual state as described in DWARF spec.
       */
      // Header fields
      // Specifies which DWARF version is used in the .debug_line section. Supported version: DWARF 2, 3, and 4.
      const uint16_t _dwarf_version;
      const bool _initial_is_stmt;

      // Implementation specific fields
      bool _append_row;
      bool _do_reset;
      bool _first_entry_in_sequence;
      bool _can_sequence_match_offset;
      bool _found_match;

      LineNumberProgramState(const LineNumberProgramHeader& header)
        : _is_stmt(header._default_is_stmt != 0), _dwarf_version(header._version),
        _initial_is_stmt(header._default_is_stmt != 0), _found_match(false) {
        reset_fields();
      }

      void reset_fields();
      // Defined in section 6.2.5.1 of the DWARF spec 4. add_to_address_register() must always be executed before set_index_register.
      void add_to_address_register(uint32_t operation_advance, const LineNumberProgramHeader& header);
      void set_index_register(uint32_t operation_advance, const LineNumberProgramHeader& header);
    };

    DwarfFile* _dwarf_file;
    MarkedDwarfFileReader _reader;
    LineNumberProgramHeader _header;
    LineNumberProgramState* _state;
    const uint32_t _offset_in_library;
    const uint64_t _debug_line_offset;
    bool _is_pc_after_call;

    bool read_header();
    bool run_line_number_program(char* filename, size_t filename_len, int* line);
    bool apply_opcode();
    bool apply_extended_opcode();
    bool apply_standard_opcode(uint8_t opcode);
    void apply_special_opcode(uint8_t opcode);
    bool does_offset_match_entry(uintptr_t previous_address, uint32_t previous_file, uint32_t previous_line);
    void print_and_store_prev_entry(uint32_t previous_file, uint32_t previous_line);
    bool get_filename_from_header(uint32_t file_index, char* filename, size_t filename_len);
    bool read_filename(char* filename, size_t filename_len);
    static void write_filename_for_overflow(char* filename, size_t filename_len) ;

   public:
    LineNumberProgram(DwarfFile* dwarf_file, uint32_t offset_in_library, uint64_t debug_line_offset, bool is_pc_after_call)
      : _dwarf_file(dwarf_file), _reader(dwarf_file->fd()), _offset_in_library(offset_in_library),
        _debug_line_offset(debug_line_offset), _is_pc_after_call(is_pc_after_call) {}

    ~LineNumberProgram() { delete _state; }

    bool find_filename_and_line_number(char* filename, size_t filename_len, int* line);
  };

 public:
  DwarfFile(const char* filepath) : ElfFile(filepath) {}

  /*
   * Starting point of reading line number and filename information from the DWARF file.
   *
   * Given:  Offset into the ELF library file, a filename buffer of size filename_size, a line number pointer.
   * Return: True:  The filename is set in the 'filename' buffer and the line number at the address pointed to by 'line'.
   *         False: Something went wrong either while reading from the file or during parsing due to an unexpected format.
   *                This could happen if the DWARF file is in an unsupported or wrong format.
   *
   *  More details about the different phases can be found at the associated methods.
   */
  bool get_filename_and_line_number(uint32_t offset_in_library, char* filename, size_t filename_len, int* line, bool is_pc_after_call);
};

#endif // !_WINDOWS && !__APPLE__

#endif // SHARE_UTILITIES_ELFFILE_HPP
