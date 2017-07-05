/*
 * Copyright 1999-2006 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

#include "incls/_precompiled.incl"
#include "incls/_assembler_linux_sparc.cpp.incl"

#include <asm-sparc/traps.h>

bool MacroAssembler::needs_explicit_null_check(intptr_t offset) {
  // Since the linux kernel resides at the low end of
  // user address space, no null pointer check is needed.
  return offset < 0 || offset >= 0x100000;
}

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
