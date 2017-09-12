/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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
#include "asm/assembler.inline.hpp"

#include "assembler_sparc.hpp"

int AbstractAssembler::code_fill_byte() {
  return 0x00;                  // illegal instruction 0x00000000
}

#ifdef VALIDATE_PIPELINE
/* Walk over the current code section and verify that there are no obvious
 * pipeline hazards exposed in the code generated.
 */
void Assembler::validate_no_pipeline_hazards() {
  const CodeSection* csect = code_section();

  address addr0 = csect->start();
  address addrN = csect->end();
  uint32_t prev = 0;

  assert((addrN - addr0) % BytesPerInstWord == 0, "must be");

  for (address pc = addr0; pc != addrN; pc += BytesPerInstWord) {
    uint32_t insn = *reinterpret_cast<uint32_t*>(pc);

    // 1. General case: No CTI immediately after other CTI
    assert(!(is_cti(prev) && is_cti(insn)), "CTI-CTI not allowed.");

    // 2. Special case: No CTI immediately after/before RDPC
    assert(!(is_cti(prev) && is_rdpc(insn)), "CTI-RDPC not allowed.");
    assert(!(is_rdpc(prev) && is_cti(insn)), "RDPC-CTI not allowed.");

    prev = insn;
  }
}
#endif
