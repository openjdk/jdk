/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2026, NTT DATA.
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

#include "aarch64Dwarf.hpp"


bool AARCH64DwarfParser::process_arch_specific_dwarf_instructions(const unsigned char op) {
  switch(op) {
    case 0x2d: // DW_CFA_AARCH64_negate_ra_state
      if (_sign_state == RA_NOT_SIGNED) {
        _sign_state = RA_SIGNED_SP;
      } else if (_sign_state == RA_SIGNED_SP) {
        _sign_state = RA_NOT_SIGNED;
      } else {
        print_error("DWARF: DW_CFA_AARCH64_negate_ra_state: illegal state (%d)\n", _sign_state);
        return false;
      }
      break;
    case 0x2c: // DW_CFA_AARCH64_negate_ra_state_with_pc
      if (_sign_state == RA_NOT_SIGNED) {
        _sign_state = RA_SIGNED_SP_PC;
      } else if (_sign_state == RA_SIGNED_SP_PC) {
        _sign_state = RA_NOT_SIGNED;
      } else {
        print_error("DWARF: DW_CFA_AARCH64_negate_ra_state_with_pc: illegal state (%d)\n", _sign_state);
        return false;
      }
      break;
    case 0x2b: // DW_CFA_AARCH64_set_ra_state
      _sign_state = static_cast<RASignState>(read_leb(false));
      read_leb(false); // operand 2: dummy
      break;
    default:
      return false;
  }
  return true;
}

void AARCH64DwarfParser::remember_arch_specific_state() {
  remember_state.push(_sign_state);
}

void AARCH64DwarfParser::restore_arch_specific_state() {
  if (remember_state.empty()) {
    print_error("DWARF Error: DW_CFA_restore_state for AArch64 is empty.\n");
    return;
  }
  _sign_state = remember_state.top();
  remember_state.pop();
}
