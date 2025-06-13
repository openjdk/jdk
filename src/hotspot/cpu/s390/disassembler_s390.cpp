/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2019 SAP SE. All rights reserved.
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

#include "asm/assembler.inline.hpp"
#include "asm/macroAssembler.hpp"
#include "code/codeCache.hpp"
#include "compiler/disassembler.hpp"
#include "gc/shared/collectedHeap.hpp"
#include "gc/shared/cardTableBarrierSet.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/stubCodeGenerator.hpp"
#include "runtime/stubRoutines.hpp"
#include "utilities/align.hpp"

// This method does plain instruction decoding, no frills.
// It may be called before the binutils disassembler kicks in
// to handle special cases the binutils disassembler does not.
// Instruction address, comments, and the like have to be output by caller.
address Disassembler::decode_instruction0(address here, outputStream * st, address virtual_begin) {
  if (is_abstract()) {
    // The disassembler library was not loaded (yet),
    // use AbstractDisassembler's decode-method.
    return decode_instruction_abstract(here, st, Assembler::instr_len(here), Assembler::instr_maxlen());
  }

  // Currently, "special decoding" doesn't work when decoding error files.
  // When decoding an instruction from a hs_err file, the given
  // instruction address 'start' points to the instruction's virtual address
  // which is not equal to the address where the instruction is located.
  // Therefore, we will either crash or decode garbage.
  if (is_decode_error_file()) {
    return here;
  }

  //---<  Decode some well-known "instructions"  >---

  address  next;
  uint16_t instruction_2bytes = *(uint16_t*)here;

  if (Assembler::is_z_nop((long)instruction_2bytes)) {
#if 1
    st->print("nop     ");  // fill up to operand column, leads to better code comment alignment
    next = here + 2;
#else
    // Compact disassembler output. Does not work the easy way.
    // Currently unusable, search does not terminate, risk of crash.
    // TODO: rework required.
    // Terminate search loop when reaching CodeEntryAlignment-aligned offset
    // or, at the latest, when reaching the next page boundary.
    int n_nops = 0;
    while(is_same_page(here, here+2*n_nops) && Assembler::is_z_nop((long)instruction_2bytes)) {
      n_nops++;
      instruction_2bytes   = *(uint16_t*)(here+2*n_nops);
    }
    if (n_nops <= 4) { // do not group few subsequent nops
      st->print("nop     ");  // fill up to operand column, leads to better code comment alignment
      next = here + 2;
    } else {
      st->print("nop     count=%d", n_nops);
      next = here + 2*n_nops;
    }
#endif
  } else if (Assembler::is_z_sync((long)instruction_2bytes)) {
    // Specific names. Make use of lightweight sync.
    st->print("sync   ");
    if (Assembler::is_z_sync_full((long)instruction_2bytes) ) st->print("heavyweight");
    if (Assembler::is_z_sync_light((long)instruction_2bytes)) st->print("lightweight");
    next = here + 2;
  } else if (instruction_2bytes == 0x0000) {
#if 1
    st->print("illtrap .nodata");
    next = here + 2;
#else
    // Compact disassembler output. Does not work the easy way.
    // Currently unusable, search does not terminate, risk of crash.
    // TODO: rework required.
    // Terminate search loop when reaching CodeEntryAlignment-aligned offset
    // or, at the latest, when reaching the next page boundary.
    int n_traps = 0;
    while(is_same_page(here, here+2*n_nops) && (instruction_2bytes == 0x0000)) {
      n_traps++;
      instruction_2bytes   = *(uint16_t*)(here+2*n_traps);
    }
    if (n_traps <= 4) { // do not group few subsequent illtraps
      st->print("illtrap .nodata");
      next = here + 2;
    } else {
      st->print("illtrap .nodata count=%d", n_traps);
      next = here + 2*n_traps;
    }
#endif
  } else if ((instruction_2bytes & 0xff00) == 0x0000) {
    st->print("illtrap .data 0x%2.2x", instruction_2bytes & 0x00ff);
    next = here + 2;
  } else {
     next = here;
  }
  return next;
}

// Print annotations (value of loaded constant)
void Disassembler::annotate(address here, outputStream* st) {
  // Currently, annotation doesn't work when decoding error files.
  // When decoding an instruction from a hs_err file, the given
  // instruction address 'start' points to the instruction's virtual address
  // which is not equal to the address where the instruction is located.
  // Therefore, we will either crash or decode garbage.
  if (is_decode_error_file()) {
    return;
  }

  if (MacroAssembler::is_load_const(here)) {
    long      value = MacroAssembler::get_const(here);
    const int tsize = 8;

    st->fill_to(60);
    st->print(";const %p | %ld | %23.15e", (void *)value, value, (double)value);
  }
}
