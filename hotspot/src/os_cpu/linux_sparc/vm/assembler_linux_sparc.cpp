/*
 * Copyright (c) 1999, 2010, Oracle and/or its affiliates. All rights reserved.
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
#include "asm/assembler.hpp"
#include "assembler_sparc.inline.hpp"
#include "runtime/os.hpp"
#include "runtime/threadLocalStorage.hpp"

#include <asm-sparc/traps.h>

void MacroAssembler::read_ccr_trap(Register ccr_save) {
  // No implementation
  breakpoint_trap();
}

void MacroAssembler::write_ccr_trap(Register ccr_save, Register scratch1, Register scratch2) {
  // No implementation
  breakpoint_trap();
}

void MacroAssembler::flush_windows_trap() { trap(SP_TRAP_FWIN); }
void MacroAssembler::clean_windows_trap() { trap(SP_TRAP_CWIN); }

// Use software breakpoint trap until we figure out how to do this on Linux
void MacroAssembler::get_psr_trap()       { trap(SP_TRAP_SBPT); }
void MacroAssembler::set_psr_trap()       { trap(SP_TRAP_SBPT); }
