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
#include "incls/_assembler_solaris_sparc.cpp.incl"

#include <sys/trap.h>          // For trap numbers
#include <v9/sys/psr_compat.h> // For V8 compatibility

bool MacroAssembler::needs_explicit_null_check(intptr_t offset) {
  // The first page of virtual addresses is unmapped on SPARC.
  // Thus, any access the VM makes through a null pointer with an offset of
  // less than 4K will get a recognizable SIGSEGV, which the signal handler
  // will transform into a NullPointerException.
  // (Actually, the first 64K or so is unmapped, but it's simpler
  // to depend only on the first 4K or so.)

  bool offset_in_first_page = 0 <= offset && offset < os::vm_page_size();
  return !offset_in_first_page;
}

void MacroAssembler::read_ccr_trap(Register ccr_save) {
  // Execute a trap to get the PSR, mask and shift
  // to get the condition codes.
  get_psr_trap();
  nop();
  set(PSR_ICC, ccr_save);
  and3(O0, ccr_save, ccr_save);
  srl(ccr_save, PSR_ICC_SHIFT, ccr_save);
}

void MacroAssembler::write_ccr_trap(Register ccr_save, Register scratch1, Register scratch2) {
  // Execute a trap to get the PSR, shift back
  // the condition codes, mask the condition codes
  // back into and PSR and trap to write back the
  // PSR.
  sll(ccr_save, PSR_ICC_SHIFT, scratch2);
  get_psr_trap();
  nop();
  set(~PSR_ICC, scratch1);
  and3(O0, scratch1, O0);
  or3(O0, scratch2, O0);
  set_psr_trap();
  nop();
}

void MacroAssembler::flush_windows_trap() { trap(ST_FLUSH_WINDOWS); }
void MacroAssembler::clean_windows_trap() { trap(ST_CLEAN_WINDOWS); }
void MacroAssembler::get_psr_trap()       { trap(ST_GETPSR); }
void MacroAssembler::set_psr_trap()       { trap(ST_SETPSR); }
