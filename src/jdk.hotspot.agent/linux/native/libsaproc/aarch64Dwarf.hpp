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

#ifndef AARCH64_DWARF_H
#define AARCH64_DWARF_H

#include <stack>

#include "dwarf.hpp"

enum RASignState {
  RA_NOT_SIGNED = 0,
  RA_SIGNED_SP,
  RA_SIGNED_SP_PC
};

class AARCH64DwarfParser : public DwarfParser {
  private:
    RASignState _sign_state; // RA_SIGN_STATE pseudo DWARF register
    std::stack<RASignState> remember_state;

  protected:
    virtual bool process_arch_specific_dwarf_instructions(const unsigned char op);
    virtual void remember_arch_specific_state();
    virtual void restore_arch_specific_state();

  public:
    // RA_SIGN_STATE should be initialized by DW_AARCH64_RA_NOT_SIGNED.
    AARCH64DwarfParser(lib_info* lib) : DwarfParser(lib), _sign_state(RA_NOT_SIGNED), remember_state() {}
    ~AARCH64DwarfParser() {}

    bool is_ra_signed() { return _sign_state != RA_NOT_SIGNED; }
};

#endif
