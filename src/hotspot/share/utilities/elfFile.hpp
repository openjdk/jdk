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

  bool get_source_info(uint32_t offset_in_library, char* filename, size_t filename_size, int* line);

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

  // Load the DWARF file (.debuginfo) that belongs to this file.
  bool load_dwarf_file();
  static uint gnu_debuglink_crc32(uint32_t crc, uint8_t* buf, size_t len);

protected:
  FILE* const fd() const { return _file; }

  // Find a section by name and return the section index. Return -1 if there is no such section.
  int read_section_header(const char* name, Elf_Shdr& hdr);
public:
  // For whitebox test
  static bool _do_not_cache_elf_section;
};


/*
 * This class parses and read line number and filename information from an associated .debuginfo file that belongs to this ELF file. The .debuginfo
 * file is written by GCC in DWARF - a standardized debugging data format. The current version of GCC uses DWARF version 4 as default which is defined
 * in the official standard: http://www.dwarfstd.org/doc/DWARF4.pdf. This class is able to parse 32-bit DWARF version 4 for 32 and 64-bit Linux builds.
 * GCC does not emit 64-bit DWARF and therefore is not supported by this parser. Other DWARF versions, especially version 5, is not (yet) supported.
 *
 * Description of used DWARF file sections:
 * - .debug_aranges: A table that consists of sets of variable length entries, each set describing the portion of the program's
 *                   address space that is covered by a single compilation unit - a mapping between addresses and compilation units.
 * - .debug_info:    The core DWARF data containing DWARF Information Entries (DIEs). Each DIE consists of a tag and a series of attributes.
 *                   Each (normal) compilation unit is represented by a DIE with the tag DW_TAG_compile_unit and contains children.
 *                   For our purposes, we are only interested in this DIE to get to the .debug_line section. We do not care about the children.
 *                   This method currently only supports normal compilation units and no partial compilation or type units.
 * - .debug_abbrev:  Represents abbreviation tables for all compilation units. A table for a specific compilation unit consists of a series of
 *                   abbreviation declarations. Each declaration specifies a tag and attributes for a DIE. The DIEs from the compilation units
 *                   in the .debug_info section need the .debug_abbrev table to decode their attributes (their meaning and size).
 * - .debug_line:    Contains line number information for each compilation unit. To get the information a state machine has to be executed
 *                   which generates a matrix. Each row describes the line number and filename for a specific pc (among other information).
 *                   The algorithm below runs this state machine until the row for the requested pc is found to fetch the line number and
 *                   the filename from it.
 *
 * Algorithm:
 * (1) First, the path to the .debuginfo DWARF file is found by inspecting the .gnu_debuglink section. The DWARF file is then opened by calling
 *     the constructor of this class. Once this is done, the processing of the DWARF file is initiated by calling get_filename_and_line_number().
 * (2) Find the compilation unit offset by reading entries from the section .debug_aranges, which contain address range descriptors, until we
 *     find the correct descriptor that includes the pc.
 * (3) Find the debug line offset for the line number information program from the .debug_line section:
 *     (a) Parse the compilation unit header from the .debug_info section at the offset obtained by (2).
 *     (b) Read the debug_abbrev_offset into the .debug_abbrev section that belongs to this compilation unit from the header obtained in (3a).
 *     (c) Read the abbreviation code that immediately follows the compilation unit header from (3a) which is needed to find the correct entry
 *         in the .debug_abbrev section.
 *     (d) Find the correct entry in the abbreviation table in the .debug_abbrev section by starting to parse entries at the debug_abbrev_offset
 *         from (3b) until we find the correct one matching the abbreviation code from (3c) .
 *     (e) Read the specified attributes of the abbreviation entry from (3d) from the compilation unit (in the .debug_info section) until we
 *         find the attribute DW_AT_stmt_list. This attributes represents an offset into the .debug_line section which contains the line number
 *         program information to get the filename and the line number.
 *  (4) Find the line number and filename beloning to the given pc by running the line number information state machine. This will create a
 *      matrix of line number information, each row representing information for specific addresses. There are several opcodes which modify
 *      different state registers. Certain opcodes will add a new row to the matrix. As soon as the correct row matching our pc is found,
 *      we can read the line number from the line register of the state machine and parse the filename from the line number program header
 *      with the given file index from the file register of the state machine.
 *
 *  More details about the different phases can be found at the associated classes and methods.
 */
class DwarfFile : public ElfFile {

  class MarkedDwarfFileReader : public MarkedFileReader {
   private:
    long _current_pos;
    long _start_pos; // Where we started reading, set after first set_position() call.
    long _max_pos; // Used to guarantee that we stop reading in case of a corrupted DWARF file.

    bool read_leb128(uint64_t* result, int8_t check_size, bool is_signed);
   public:
    MarkedDwarfFileReader(FILE* const fd) : MarkedFileReader(fd), _current_pos(-1), _start_pos(-1), _max_pos(-1) {}

    bool set_position(long new_pos);
    long get_position() const { return _current_pos; }
    void set_max_pos(long max_pos) { _max_pos = max_pos; }
    // Have we reached the limit of maximally allowable bytes to read? Used to ensure an early bail out if the file is corrupted.
    bool has_bytes_left() const;
    // Call this if another file reader has changed the position of the same file handle.
    bool update_to_stored_position();
    // Must be called to restore the old position before this file reader changed it with update_to_stored_position().
    bool reset_to_previous_position();
    bool move_position(long offset);
    bool read_sbyte(int8_t* result);
    bool read_byte(uint8_t* result);
    bool read_word(uint16_t* result);
    bool read_dword(uint32_t* result);
    bool read_qword(uint64_t* result);
    bool read_uleb128(uint64_t* result, int8_t check_size = -1);
    bool read_sleb128(int64_t* result, int8_t check_size = -1);
    // Reads 4 bytes for 32-bit and 8 bytes for 64-bit Linux builds.
    bool read_address_sized(uintptr_t* result);
    bool read_string(char* result = nullptr, size_t result_len = 0);
  };

  // (2) Processing the .debug_aranges section. This is specified in section 6.1.2 of the DWARF 4 spec.
  class DebugAranges {

    // The header is defined in section 6.1.2 of the DWARF 4 spec.
    struct DebugArangesSetHeader {
      // The total length of all of the entries for that set, not including the length field itself.
      uint32_t _unit_length;

      // This number is specific to the address lookup table and is independent of the DWARF version number.
      uint16_t _version;

      // The offset from the beginning of the .debug_info or .debug_types section of the compilation unit header referenced
      // by the set. In this implementation we only use it as offset into .debug_info. This must be 4 bytes for 32-bit DWARF.
      uint32_t _debug_info_offset;

      // The size of an address in bytes on the target architecture.
      uint8_t _address_size;

      // The size of a segment selector in bytes on the target architecture. This should be 0.
      uint8_t _segment_size;

      bool read_header(MarkedDwarfFileReader* reader);
    };

    DwarfFile* _dwarf_file;
    MarkedDwarfFileReader _reader;

    bool read_section_header();
    static bool is_terminating_set(uintptr_t beginning_address, uintptr_t length) {
      return beginning_address == 0 && length == 0;
    }
   public:
    DebugAranges(DwarfFile* dwarf_file) : _dwarf_file(dwarf_file), _reader(dwarf_file->fd()) {}
    bool find_compilation_unit_offset(uint32_t offset_in_library, uint32_t* compilation_unit_offset);
  };

  // (3a-c,e) The compilation unit is read from the .debug_info section.
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

      // The unsigned offset into the .debug_abbrev section. This offset associates the compilation unit with a particular
      // set of debugging information entry abbreviations.
      uint32_t _debug_abbrev_offset;

      // The size in bytes of an address on the target architecture. If the system uses segmented addressing, this value
      // represents the size of the offset portion of an address.
      uint8_t  _address_size;

      MarkedDwarfFileReader* _reader;
      bool read_header();
    };

    DwarfFile* _dwarf_file;
    MarkedDwarfFileReader _reader;
    CompilationUnitHeader _header;
    const uint32_t _compilation_unit_offset;

    // Result of a request initiated by find_debug_line_offset().
    uint32_t* _debug_line_offset;

    bool read_header();
   public:
    CompilationUnit(DwarfFile* dwarf_file, uint32_t compilation_unit_offset)
      : _dwarf_file(dwarf_file), _reader(dwarf_file->fd()), _compilation_unit_offset(compilation_unit_offset), _debug_line_offset(nullptr) {
      _header._reader = &_reader;
    }
    bool find_debug_line_offset(uint32_t* debug_line_offset);
    bool read_attribute(uint64_t attribute, bool set_result);
  };

  // (3d) Read from the .debug_abbrev section at the debug_abbrev_offset specified by the compilation unit header.
  class DebugAbbrev {

    // Tag encoding from Figure 18 in section 7.5 of the DWARF 4 spec.
    static constexpr uint8_t DW_TAG_compile_unit = 0x11;

    // Child determination encoding from Figure 19 in section 7.5 of the DWARF 4 spec.
    static constexpr uint8_t DW_CHILDREN_yes = 0x01;

    // Attribute encoding from Figure 20 in section 7.5 of the DWARF 4 spec.
    static constexpr uint8_t DW_AT_stmt_list = 0x10;

    /* There is no specific header for this section */

    DwarfFile* _dwarf_file;
    MarkedDwarfFileReader _reader;
    CompilationUnit* _compilation_unit; // Needs to read from compilation unit while parsing the entries in .debug_abbrev

    // Result field of a request
    uint32_t* _debug_line_offset;

    bool skip_attribute_specifications();
    bool read_attribute_specifications();

   public:
    DebugAbbrev(DwarfFile* dwarf_file, CompilationUnit* compilation_unit) :
      _dwarf_file(dwarf_file), _reader(_dwarf_file->fd()), _compilation_unit(compilation_unit) {}
    bool read_section_header(uint32_t debug_abbrev_offset);
    bool get_debug_line_offset(uint64_t abbrev_code);

  };
  // .debug_abbrev and .debug_info

  // (4) The line number program for the compilation unit at the offset of the .debug_line obtained by (3).
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
    static constexpr uint8_t DW_LNS_set_prologue_end = 10;
    static constexpr uint8_t DW_LNS_set_epilogue_begin = 11;
    static constexpr uint8_t DW_LNS_set_isa = 12;

    // Extended opcodes for the line number program defined in section 6.2.5.2 of the DWARF 4 spec.
    static constexpr uint8_t DW_LNE_end_sequence = 1;
    static constexpr uint8_t DW_LNE_set_address = 2;
    static constexpr uint8_t DW_LNE_define_file = 3;
    static constexpr uint8_t DW_LNE_set_discriminator = 4;

    // The header is defined in section 6.2.4 of the DWARF 4 spec.
    struct LineNumberProgramHeader {
      // The size in bytes of the line number information for this compilation unit, not including the
      // unit_length field itself. 32-bit DWARF uses 4 bytes.
      uint32_t _unit_length;

      // A version number (see Appendix F). This number is specific to the line number information
      // and is independent of the DWARF version number.
      uint16_t _version;

      // The number of bytes following the header_length field to the beginning of the first byte of
      // the line number program itself. In the 32-bit DWARF format uses 4 bytes.
      uint32_t _header_length;

      // The size in bytes of the smallest target machine instruction. Line number program opcodes that alter the address
      // and op_index registers use this and maximum_operations_per_instruction in their calculations.
      uint8_t _minimum_instruction_length;

      // The maximum number of individual operations that may be encoded in an instruction. Line number program opcodes
      // that alter the address and op_index registers use this and minimum_instruction_length in their calculations.
      // For non-VLIW architectures, this field is 1, the op_index register is always 0, and the operation pointer is
      // simply the address register.
      uint8_t _maximum_operations_per_instruction;

      // The initial value of the is_stmt register.
      uint8_t _default_is_stmt;

      // This parameter affects the meaning of the special opcodes.
      int8_t _line_base;

      // This parameter affects the meaning of the special opcodes.
      uint8_t _line_range;

      // The number assigned to the first special opcode.
      uint8_t _opcode_base;

      // This array specifies the number of LEB128 operands for each of the standard opcodes. The
      // first element of the array corresponds to the opcode whose value is 1, and the last element
      // corresponds to the opcode whose value is opcode_base - 1. DWARF 4 uses 12 standard opcodes.
      uint8_t _standard_opcode_lengths[12];

      // Not part of the real header, implementation only
      long _file_starting_pos;
      MarkedDwarfFileReader* _reader;

      bool read_header();
    };

    // Defined in DWARF 4, Section 6.2.2
    // Most of the state fields are not used to get the filename and the line number information.
    struct LineNumberProgramState : public CHeapObj<mtInternal> {
      // The program-counter value corresponding to a machine instruction generated by the compiler.
      // 8 bytes on 64-bit and 4 bytes on 32-bit
      uintptr_t _address;

      // The index of an operation within a VLIW instruction. The index of the first operation is 0. For non-VLIW
      // architectures, this register will always be 0.
      // The address and op_index registers, taken together, form an operation pointer that can reference any
      // individual operation with the instruction stream. This field was added in DWARF 4.
      uint32_t _op_index;

      // The identity of the source file corresponding to a machine instruction.
      uint32_t _file;

      // A source line number. Lines are numbered beginning at 1. The compiler may emit the value 0 in cases where an
      // instruction cannot be attributed to any source line.
      uint32_t _line;

      // A column number within a source line. Columns are numbered beginning at 1. The value 0 is reserved to indicate
      // that a statement begins at the “left edge” of the line.
      uint32_t _column;

      // Indicates that the current instruction is a recommended breakpoint location.
      bool _is_stmt;

      // Indicates that the current instruction is the beginning of a basic block.
      bool _basic_block;

      // Indicates that the current address is that of the first byte after the end of a sequence of target machine
      // instructions. end_sequence terminates a sequence of lines.
      bool _end_sequence;

      // Indicates that the current address is one (of possibly many) where execution should be suspended for an entry
      // breakpoint of a function.
      bool _prologue_end;

      // Indicates that the current address is one (of possibly many) where execution should be suspended for an exit
      // breakpoint of a function.
      bool _epilogue_begin;

      // Encodes the applicable instruction set architecture for the current instruction.
      uint32_t _isa;

      // Identifies the block to which the current instruction belongs. This field was added in DWARF 4
      uint32_t _discriminator;

      /*
       * Implementation specific fields
       */
      // Specifies which DWARF version is used in the .debug_line section. Currently supported: DWARF 3 + 4.
      LineNumberProgramHeader* _header;
      const uint8_t _dwarf_version;
      const bool _initial_is_stmt;
      bool _first_row;
      bool _append_row;
      bool _do_reset;

      // Could the current sequence be a candidate which contains the pc? (pc must be smaller than the address of the first row in the matrix)
      bool _sequence_candidate;

      LineNumberProgramState(LineNumberProgramHeader* header)
        : _is_stmt(header->_default_is_stmt != 0), _header(header), _dwarf_version(header->_version),
          _initial_is_stmt(header->_default_is_stmt != 0) {
        reset_fields();
      }

      void reset_fields();
      // Defined in section 6.2.5.1 of the DWARF spec 4. add_to_address_register() must always be executed before set_index_register.
      void add_to_address_register(uint32_t operation_advance);
      void set_index_register(uint32_t operation_advance);
    };

    DwarfFile* _dwarf_file;
    MarkedDwarfFileReader _reader;
    LineNumberProgramHeader _header;
    LineNumberProgramState* _state;
    const uint32_t _offset_in_library;
    const uint64_t _debug_line_offset;

    // Result fields of a request
    int* _line;
    char* _filename;
    size_t _filename_len;

    bool read_header();
    bool read_line_number_program();
    bool apply_extended_opcode();
    bool apply_standard_opcode(uint8_t opcode);
    bool apply_special_opcode(uint8_t opcode);
    bool read_filename_from_header(uint32_t file_index);

   public:
    LineNumberProgram(DwarfFile* dwarf_file, uint32_t offset_in_library, uint64_t debug_line_offset)
      : _dwarf_file(dwarf_file),  _reader(dwarf_file->fd()), _state(nullptr), _offset_in_library(offset_in_library),
        _debug_line_offset(debug_line_offset), _line(nullptr), _filename(nullptr), _filename_len(0) {
      _header._reader = &_reader;
    }
    ~LineNumberProgram() {
      delete _state;
    }
    bool find_filename_and_line_number(char* filename, size_t filename_len, int* line);
  };

 public:
  DwarfFile(const char* filepath) : ElfFile(filepath) {}

  /*
   * Starting point of reading line number and filename information from the DWARF file.
   *
   * Given:  offset (also referred to as 'pc') into the library (this ELF file), a filename buffer of size filename_size, a line number pointer
   * Return: True:  The filename in the 'filename' buffer and the line number at the address pointed to by 'line'.
   *         False: Something went wrong either while reading from the file or during parsing due to an unexpected format.
   *                This could happen if the DWARF file is corrupted or in an unsupported format.
   *
   *  More details about the different phases can be found at the associated methods.
   */
  bool get_filename_and_line_number(uint32_t offset_in_library, char* filename, size_t filename_len, int* line);
};

#endif // !_WINDOWS && !__APPLE__

#endif // SHARE_UTILITIES_ELFFILE_HPP
