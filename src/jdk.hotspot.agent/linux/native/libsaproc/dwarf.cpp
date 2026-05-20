/*
 * Copyright (c) 2020, 2026, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2026, NTT DATA.
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

#include <cstdlib>
#include <cstring>
#include <stack>

#include "dwarf.hpp"
#include "salibelf.h"
#include "libproc_impl.h"

DwarfParser::DwarfParser(lib_info *lib) : _lib(lib),
                                          _buf(NULL),
                                          _has_augmentation(false),
                                          _fde_ptr_encoding(0),
                                          _code_factor(0),
                                          _data_factor(0),
                                          _current_pc(0L) {
  init_state(_initial_state);
  init_state(_state);
}

void DwarfParser::init_state(struct DwarfState& st) {
  st.cfa_reg = MAX_VALUE;
  st.return_address_reg = MAX_VALUE;
  st.cfa_offset = 0;

  st.offset_from_cfa.clear();
  for (int reg = 0; reg < MAX_VALUE; reg++) {
    st.offset_from_cfa[static_cast<enum DWARF_Register>(reg)] = INT_MAX;
  }
}

/* from read_leb128() in dwarf.c in binutils */
uintptr_t DwarfParser::read_leb(bool sign) {
  uintptr_t result = 0L;
  unsigned char b;
  unsigned int shift = 0;

  while (true) {
    b = *_buf++;
    result |= static_cast<uintptr_t>(b & 0x7f) << shift;
    shift += 7;
    if ((b & 0x80) == 0) {
      break;
    }
  }

  if (sign && (shift < (8 * sizeof(result))) && (b & 0x40)) {
    result |= static_cast<uintptr_t>(-1L) << shift;
  }

  return result;
}

uint64_t DwarfParser::get_entry_length() {
  uint64_t length = *(reinterpret_cast<uint32_t *>(_buf));
  _buf += 4;
  if (!_lib->frame.is_debug_frame && length == 0xffffffff) {
    length = *(reinterpret_cast<uint64_t *>(_buf));
    _buf += 8;
  }
  return length;
}

bool DwarfParser::process_cie(unsigned char *start_of_entry, uint32_t id) {
  unsigned char *orig_pos = _buf;

  // CIE pointer means the offset from FDE in .eh_frame.
  // In .debug_frame, CIE pointer means the offset from start of .debug_frame .
  _buf = _lib->frame.is_debug_frame ? _lib->frame.data + id
                                    : start_of_entry - id;

  uint64_t length = get_entry_length();
  if (length == 0L) {
    return false;
  }
  unsigned char *end = _buf + length;

  _buf += 4; // Skip ID (This value of CIE would be always 0 in .eh_frame / 0xffffffff in .debug_frame)
  _buf++;    // Skip version

  char *augmentation_string = reinterpret_cast<char *>(_buf);
  bool has_ehdata = (strcmp("eh", augmentation_string) == 0);
  _buf += strlen(augmentation_string) + 1; // includes '\0'
  if (has_ehdata) {
    _buf += sizeof(void *); // Skip EH data
  }

  _code_factor = read_leb(false);
  _data_factor = static_cast<int>(read_leb(true));
  enum DWARF_Register initial_ra = static_cast<enum DWARF_Register>(*_buf++);

  if (*augmentation_string == 'z') {
    _has_augmentation = true;
    read_leb(false); // Skip augmentation length
    augmentation_string++; // Skip first char ('z')
    while (*augmentation_string != '\0') {
      if (*augmentation_string == 'R') {
        _fde_ptr_encoding = *_buf++;
      } else if (*augmentation_string == 'P') {
        print_debug("DWARF Warning: Ignore augmentation: P\n");
        unsigned char enc = *_buf++; // first argument (encoding)
        get_decoded_value(enc); // skip second argument (personality routine handler)
      } else if (*augmentation_string == 'L') {
        print_debug("DWARF Warning: Ignore augmentation: L\n");
        _buf++; // skip 1 arguments
      }
      augmentation_string++;
    }
  }

  // Clear state
  _current_pc = 0L;
  init_state(_state);
  _state.return_address_reg = initial_ra;

  parse_dwarf_instructions(0L, static_cast<uintptr_t>(-1L), end);

  _initial_state = _state;
  _buf = orig_pos;
  return true;
}

void DwarfParser::parse_dwarf_instructions(uintptr_t begin, uintptr_t pc, const unsigned char *end) {
  uintptr_t operand1;
  _current_pc = begin;
  std::stack<struct DwarfState> remember_state;

  while ((_buf < end) && (_current_pc < pc)) {
    unsigned char op = *_buf++;
    unsigned char opa = op & 0x3f;
    if (op & 0xc0) {
      op &= 0xc0;
    }

    switch (op) {
      case 0x0:  // DW_CFA_nop
        return;
      case 0x01: // DW_CFA_set_loc
        operand1 = get_decoded_value(_fde_ptr_encoding);
        if (_current_pc != 0L) {
          _current_pc = operand1;
        }
        break;
      case 0x0c: // DW_CFA_def_cfa
        _state.cfa_reg = static_cast<enum DWARF_Register>(read_leb(false));
        _state.cfa_offset = read_leb(false);
        break;
      case 0x80: {// DW_CFA_offset
        operand1 = read_leb(false);
        enum DWARF_Register reg = static_cast<enum DWARF_Register>(opa);
        _state.offset_from_cfa[reg] = operand1 * _data_factor;
        break;
      }
      case 0xe:  // DW_CFA_def_cfa_offset
        _state.cfa_offset = read_leb(false);
        break;
      case 0x40: // DW_CFA_advance_loc
        if (_current_pc != 0L) {
          _current_pc += opa * _code_factor;
        }
        break;
      case 0x02: { // DW_CFA_advance_loc1
        unsigned char ofs = *_buf++;
        if (_current_pc != 0L) {
          _current_pc += ofs * _code_factor;
        }
        break;
      }
      case 0x03: { // DW_CFA_advance_loc2
        unsigned short ofs = *(reinterpret_cast<unsigned short *>(_buf));
        _buf += 2;
        if (_current_pc != 0L) {
          _current_pc += ofs * _code_factor;
        }
        break;
      }
      case 0x04: { // DW_CFA_advance_loc4
        unsigned int ofs = *(reinterpret_cast<unsigned int *>(_buf));
        _buf += 4;
        if (_current_pc != 0L) {
          _current_pc += ofs * _code_factor;
        }
        break;
      }
      case 0x07: { // DW_CFA_undefined
        enum DWARF_Register reg = static_cast<enum DWARF_Register>(read_leb(false));
        _state.offset_from_cfa[reg] = INT_MAX;
        break;
      }
      case 0x0d: // DW_CFA_def_cfa_register
        _state.cfa_reg = static_cast<enum DWARF_Register>(read_leb(false));
        break;
      case 0x0a: // DW_CFA_remember_state
        remember_state.push(_state);
        break;
      case 0x0b: // DW_CFA_restore_state
        if (remember_state.empty()) {
          print_debug("DWARF Error: DW_CFA_restore_state with empty stack.\n");
          return;
        }
        _state = remember_state.top();
        remember_state.pop();
        break;
      case 0xc0: {// DW_CFA_restore
        enum DWARF_Register reg = static_cast<enum DWARF_Register>(opa);
        _state.offset_from_cfa[reg] = _initial_state.offset_from_cfa[reg];
        break;
      }
#ifdef __aarch64__
      // SA hasn't yet supported Pointer Authetication Code (PAC), so following
      // instructions would be ignored with warning message.
      //   https://github.com/ARM-software/abi-aa/blob/2025Q4/aadwarf64/aadwarf64.rst
      case 0x2d: // DW_CFA_AARCH64_negate_ra_state
        print_debug("DWARF: DW_CFA_AARCH64_negate_ra_state is unimplemented.\n", op);
        break;
      case 0x2c: // DW_CFA_AARCH64_negate_ra_state_with_pc
        print_debug("DWARF: DW_CFA_AARCH64_negate_ra_state_with_pc is unimplemented.\n", op);
        break;
      case 0x2b: // DW_CFA_AARCH64_set_ra_state
        print_debug("DWARF: DW_CFA_AARCH64_set_ra_state is unimplemented.\n", op);
        break;
#endif
      default:
        print_debug("DWARF: Unknown opcode: 0x%x\n", op);
        return;
    }
  }
}

/* from dwarf.c in binutils */
uint32_t DwarfParser::get_decoded_value(unsigned char enc) {
  int size;
  uintptr_t result;

  switch (enc & 0x7) {
    case 0:  // DW_EH_PE_absptr
      size = sizeof(void *);
      result = *(reinterpret_cast<uintptr_t *>(_buf));
      break;
    case 2:  // DW_EH_PE_udata2
      size = 2;
      result = *(reinterpret_cast<unsigned int *>(_buf));
      break;
    case 3:  // DW_EH_PE_udata4
      size = 4;
      result = *(reinterpret_cast<uint32_t *>(_buf));
      break;
    case 4:  // DW_EH_PE_udata8
      size = 8;
      result = *(reinterpret_cast<uint64_t *>(_buf));
      break;
    default:
      return 0;
  }

  // On x86-64, we have to handle it as 32 bit value, and it is PC relative.
  //   https://gcc.gnu.org/ml/gcc-help/2010-09/msg00166.html
#if defined(_LP64)
  if (size == 8) {
    result += _lib->frame.v_addr + static_cast<uintptr_t>(_buf - _lib->frame.data);
    size = 4;
  } else
#endif
  if ((enc & 0x70) == 0x10) { // 0x10 = DW_EH_PE_pcrel
    result += _lib->frame.v_addr + static_cast<uintptr_t>(_buf - _lib->frame.data);
  } else  if (size == 2) {
    result = static_cast<int>(result) + _lib->frame.v_addr + static_cast<uintptr_t>(_buf - _lib->frame.data);
    size = 4;
  }

  _buf += size;
  return static_cast<uint32_t>(result);
}

unsigned int DwarfParser::get_pc_range() {
  int size;
  uintptr_t result;

  switch (_fde_ptr_encoding & 0x7) {
    case 0:  // DW_EH_PE_absptr
      size = sizeof(void *);
      result = *(reinterpret_cast<uintptr_t *>(_buf));
      break;
    case 2:  // DW_EH_PE_udata2
      size = 2;
      result = *(reinterpret_cast<unsigned int *>(_buf));
      break;
    case 3:  // DW_EH_PE_udata4
      size = 4;
      result = *(reinterpret_cast<uint32_t *>(_buf));
      break;
    case 4:  // DW_EH_PE_udata8
      size = 8;
      result = *(reinterpret_cast<uint64_t *>(_buf));
      break;
    default:
      return 0;
  }

  // On x86-64, we have to handle it as 32 bit value, and it is PC relative.
  //   https://gcc.gnu.org/ml/gcc-help/2010-09/msg00166.html
#if defined(_LP64)
  if ((size == 8) || (size == 2)) {
    size = 4;
  }
#endif

  _buf += size;
  return static_cast<unsigned int>(result);
}

bool DwarfParser::process_dwarf(const uintptr_t pc) {
  // https://refspecs.linuxfoundation.org/LSB_3.0.0/LSB-PDA/LSB-PDA/ehframechpt.html
  _buf = _lib->frame.data;
  unsigned char *end = _lib->frame.data + _lib->frame.size;
  while (_buf <= end) {
    uint64_t length = get_entry_length();
    if (length == 0L) {
      break; // it means "terminator" in .eh_frame, so go through to .debug_frame
    }
    unsigned char *next_entry = _buf + length;
    unsigned char *start_of_entry = _buf;
    uint32_t id = *(reinterpret_cast<uint32_t *>(_buf));
    _buf += 4;
    // ID for CIE is 0 in .eh_frame, 0xffffffff in .debug_frame
    bool is_fde = (_lib->frame.is_debug_frame ? 0xffffffff : 0) != id;
    if (is_fde) {
      uintptr_t begin_ofs = 0L;
      uintptr_t inst_sz = 0L;
      if (_lib->frame.is_debug_frame) {
        begin_ofs = *(reinterpret_cast<uintptr_t *>(_buf));
        _buf += sizeof(void*);
        inst_sz = *(reinterpret_cast<uintptr_t *>(_buf));
        _buf += sizeof(void*);
      } else {
        begin_ofs = get_decoded_value(_fde_ptr_encoding);
        inst_sz = get_pc_range();
      }
      uintptr_t pc_begin = begin_ofs + _lib->base;
      uintptr_t pc_end = pc_begin + inst_sz;

      if ((pc >= pc_begin) && (pc < pc_end)) {
        // Process CIE
        if (!process_cie(start_of_entry, id)) {
          return false;
        }

        // Skip Augumenation
        if (_has_augmentation) {
          uintptr_t augmentation_length = read_leb(false);
          _buf += augmentation_length;
        }

        // Process FDE
        parse_dwarf_instructions(pc_begin, pc, next_entry);
        return true;
      }
    }

    _buf = next_entry;
  }

  bool result = false;
  // try again with .debug_frame section if it hasn't been tried yet.
  if (!_lib->frame.tried_debug_frame && _lib->fd != -1) {
    // attempts to load .debug_frame from executables.
    frame_info frame = {};
    if (!read_frame(".debug_frame", _lib->fd, &frame)) {
      // attempts again to load .debug_frame from debuginfo if it could not be loaded from executables.
      int debug_fd = open_debuginfo(_lib->name, _lib->fd);
      if (debug_fd != -1 && read_frame(".debug_frame", debug_fd, &frame)) {
        close(debug_fd);
      }
    }
    frame.tried_debug_frame = true;
    _lib->frame.tried_debug_frame = true;

    // try process_dwarf() again with .debug_frame.
    if (frame.data != NULL) {
      if (_lib->frame.data != NULL) {
        free(_lib->frame.data);
      }
      memcpy(&_lib->frame, &frame, sizeof(frame_info));
      result = process_dwarf(pc);
    }
  }

  return result;
}
