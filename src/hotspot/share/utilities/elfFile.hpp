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

  // Standard Opcodes for line number program from section 6.2.5.2 in DWARF 4 spec
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

  // Exnteded Opcodes for line number program from section 6.2.5.3 in DWARF 4 spec
  static constexpr uint8_t DW_LNE_end_sequence = 1;
  static constexpr uint8_t DW_LNE_set_address = 2;
  static constexpr uint8_t DW_LNE_define_file = 3;
  static constexpr uint8_t DW_LNE_set_discriminator = 4; // DWARF 4 only

  class MarkedDwarfFileReader : public MarkedFileReader {
   private:
    long _current_pos;
    long _start_pos;
    long _max_pos; // Used to guarantee that we stop reading in case of a corrupted DWARF file

    bool read_leb128(uint64_t* result, int8_t check_size, bool is_signed);
   public:
    MarkedDwarfFileReader(FILE* const fd) : MarkedFileReader(fd),
                                            _current_pos(-1), _start_pos(-1), _max_pos(-1) {}
    bool set_position(long new_pos);
    void set_max_pos(long max_pos) { _max_pos = max_pos; }
    bool has_bytes_left() const; // Have we reached the limit of maximally allowable bytes to read?
    bool update_to_stored_position(); // Call this if another file reader has changed the position of the same file handle
    bool reset_to_previous_position();
    bool move_position(long offset);
    bool read_sbyte(int8_t* result);
    bool read_byte(uint8_t* result);
    bool read_word(uint16_t* result);
    bool read_dword(uint32_t* result);
    bool read_qword(uint64_t* result);
    bool read_uleb128(uint64_t* result, int8_t check_size = -1);
    bool read_sleb128(int64_t* result, int8_t check_size = -1);
    bool read_string(/* ignore result */);
  };

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

    void print_fields() const {
      tty->print_cr("%x", unit_length);
      tty->print_cr("%x", version);
      tty->print_cr("%x", debug_info_offset);
      tty->print_cr("%x", address_size);
      tty->print_cr("%x", segment_size);
    }
  };

  struct DebugArangesSet64 {
    uint64_t beginning_address;
    uint64_t length;
  };

  static bool is_terminating_set(DebugArangesSet64 set) {
    return set.beginning_address == 0 && set.length == 0;
  }

  // See DWARF4 spec section 7.5.1.1
  struct CompilationUnitHeader32 {
    // The length of the .debug_info contribution for that compilation unit, not including the length field itself.
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

  // See DWARF 4 spec, section 6.2.4
  struct LineNumberProgramHeader32 {
    // The size in bytes of the line number information for this compilation unit, not including the
    // unit_length field itself. 32-bit DWARF uses 4 bytes.
    uint32_t unit_length;

    // A version number (see Appendix F). This number is specific to the line number information
    // and is independent of the DWARF version number.
    uint16_t version;

    // The number of bytes following the header_length field to the beginning of the first byte of
    // the line number program itself. In the 32-bit DWARF format uses 4 bytes.
    uint32_t header_length;

    // The size in bytes of the smallest target machine instruction. Line number program opcodes that alter the address
    // and op_index registers use this and maximum_operations_per_instruction in their calculations.
    uint8_t minimum_instruction_length;

    // The maximum number of individual operations that may be encoded in an instruction. Line number program opcodes
    // that alter the address and op_index registers use this and minimum_instruction_length in their calculations.
    // For non-VLIW architectures, this field is 1, the op_index register is always 0, and the operation pointer is
    // simply the address register.
    uint8_t maximum_operations_per_instruction;

    // The initial value of the is_stmt register.
    uint8_t default_is_stmt;

    // This parameter affects the meaning of the special opcodes.
    int8_t line_base;

    // This parameter affects the meaning of the special opcodes.
    uint8_t line_range;

    // The number assigned to the first special opcode.
    uint8_t opcode_base;

    // This array specifies the number of LEB128 operands for each of the standard opcodes. The
    // first element of the array corresponds to the opcode whose value is 1, and the last element
    // corresponds to the opcode whose value is opcode_base - 1. DWARF 4 uses 12 standard opcodes.
    uint8_t standard_opcode_lengths[12];
  };

  // Defined in DWARF 4, Section 6.2.2
  struct LineNumberState {

    // Specifies which DWARF version is used in the .debug_line section. Currently supported: DWARF 3 + 4
    const uint8_t dwarf_version;

    // The program-counter value corresponding to a machine instruction generated by the compiler.
    uint64_t address; // TODO 32 bit?

    // The index of an operation within a VLIW instruction. The index of the first operation is 0. For non-VLIW
    // architectures, this register will always be 0.
    // The address and op_index registers, taken together, form an operation pointer that can reference any
    // individual operation with the instruction stream. This field was added in DWARF 4.
    uint32_t op_index;

    // The identity of the source file corresponding to a machine instruction.
    uint32_t file;

    // A source line number. Lines are numbered beginning at 1. The compiler may emit the value 0 in cases where an
    // instruction cannot be attributed to any source line.
    uint32_t line;

    // A column number within a source line. Columns are numbered beginning at 1. The value 0 is reserved to indicate
    // that a statement begins at the “left edge” of the line.
    uint32_t column;

    const bool initial_is_stmt;
    bool is_stmt;
    bool basic_block;
    bool end_sequence;
    bool prologue_end;
    bool epilogue_begin;
    uint32_t isa;
    uint32_t discriminator; // This field was added in DWARF 4

    // Implementation specific
    bool first_row;
    bool append_row;
    bool do_reset;

    // Could the current sequence be a candidate which contains the pc? (pc must be smaller than the address of the first row in the matrix)
    bool sequence_candidate;

    LineNumberState(uint8_t dwarf_version, bool default_is_stmt) :
    dwarf_version(dwarf_version),
    address(0),
    op_index(0),
    file(1),
    line(1),
    column(0),
    initial_is_stmt(default_is_stmt),
    is_stmt(default_is_stmt),
    basic_block(false),
    end_sequence(false),
    prologue_end(false),
    epilogue_begin(false),
    isa(0),
    discriminator(0),
    first_row(true),
    append_row(false),
    do_reset(false),
    sequence_candidate(false) {}

    void reset_fields() {
      address = 0;
      op_index = 0;
      file = 1;
      line = 1;
      column = 0;
      is_stmt = initial_is_stmt;
      basic_block = false;
      end_sequence = false;
      prologue_end = false;
      epilogue_begin = false;
      isa = 0;
      discriminator = 0;
      first_row = true;
      append_row = false;
      do_reset = false;
      sequence_candidate = false;
    }

    // Defined in section 6.2.5.1. set_address_register must always be executed before set_index_register
    void add_to_address_register(LineNumberProgramHeader32* header, uint8_t operation_advance);
    void set_index_register(LineNumberProgramHeader32* header, uint8_t operation_advance);
  };

  // .debug_aranges
  bool find_compilation_unit_offset(int offset_in_library, uint64_t* compilation_unit_offset);
  static bool read_debug_aranges_set_header(DebugArangesSetHeader32* header, MarkedDwarfFileReader* reader);

  // .debug_abbrev and .debug_info
  bool find_debug_line_offset(int offset_in_library, uint64_t compilation_unit_offset, uint32_t* debug_line_offset);
  static bool read_compilation_unit_header(CompilationUnitHeader32 *compilation_unit_header, MarkedDwarfFileReader* reader);
  bool get_debug_line_offset_from_debug_abbrev(const CompilationUnitHeader32* cu_header, uint64_t abbrev_code, uint32_t* debug_line_offset);
  bool process_attribute_specs(MarkedDwarfFileReader* debug_abbrev_reader, uint8_t address_size,
                               MarkedDwarfFileReader* debug_info_reader, uint32_t* debug_line_offset);
  static bool process_attribute(uint64_t attribute, uint8_t address_size, MarkedDwarfFileReader* debug_info_reader, uint32_t* debug_line_offset = nullptr);
  static bool read_attribute_specs(MarkedDwarfFileReader* debug_abbrev_reader);

  // .debug_line
  bool find_line_number(int offset_in_library, uint64_t debug_line_offset, int* line);
  static bool read_line_number_program_header(LineNumberProgramHeader32* header, MarkedDwarfFileReader* reader);
  bool read_line_number_program(int offset_in_library, LineNumberProgramHeader32* header, MarkedDwarfFileReader* reader, int* line);
  bool read_extended_opcode(LineNumberState* state, MarkedDwarfFileReader* reader);
  bool read_standard_opcode(uint8_t opcode, LineNumberState* state, LineNumberProgramHeader32* header, MarkedDwarfFileReader* reader);
  bool read_special_opcode(uint8_t opcode, LineNumberState* state, LineNumberProgramHeader32* header, MarkedDwarfFileReader* reader);

 public:
  DwarfFile(const char* filepath) : ElfFile(filepath) {}
  bool get_line_number(int offset_in_library, char* buf, size_t buflen, int* line);
};

#endif // !_WINDOWS && !__APPLE__

#endif // SHARE_UTILITIES_ELFFILE_HPP
