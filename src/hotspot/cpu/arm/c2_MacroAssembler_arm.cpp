/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "asm/assembler.hpp"
#include "asm/assembler.inline.hpp"
#include "opto/c2_MacroAssembler.hpp"
#include "runtime/basicLock.hpp"

// TODO: 8 bytes at a time? pre-fetch?
// Compare char[] arrays aligned to 4 bytes.
void C2_MacroAssembler::char_arrays_equals(Register ary1, Register ary2,
                                           Register limit, Register result,
                                           Register chr1, Register chr2, Label& Ldone) {
  Label Lvector, Lloop;

  // if (ary1 == ary2)
  //     return true;
  cmpoop(ary1, ary2);
  b(Ldone, eq);

  // Note: limit contains number of bytes (2*char_elements) != 0.
  tst(limit, 0x2); // trailing character ?
  b(Lvector, eq);

  // compare the trailing char
  sub(limit, limit, sizeof(jchar));
  ldrh(chr1, Address(ary1, limit));
  ldrh(chr2, Address(ary2, limit));
  cmp(chr1, chr2);
  mov(result, 0, ne);     // not equal
  b(Ldone, ne);

  // only one char ?
  tst(limit, limit);
  mov(result, 1, eq);
  b(Ldone, eq);

  // word by word compare, don't need alignment check
  bind(Lvector);

  // Shift ary1 and ary2 to the end of the arrays, negate limit
  add(ary1, limit, ary1);
  add(ary2, limit, ary2);
  neg(limit, limit);

  bind(Lloop);
  ldr_u32(chr1, Address(ary1, limit));
  ldr_u32(chr2, Address(ary2, limit));
  cmp_32(chr1, chr2);
  mov(result, 0, ne);     // not equal
  b(Ldone, ne);
  adds(limit, limit, 2*sizeof(jchar));
  b(Lloop, ne);

  // Caller should set it:
  // mov(result_reg, 1);  //equal
}

void C2_MacroAssembler::fast_lock(Register Roop, Register Rbox, Register Rscratch, Register Rscratch2) {
  assert(VM_Version::supports_ldrex(), "unsupported, yet?");
  assert_different_registers(Roop, Rbox, Rscratch, Rscratch2);

  Label done;

  if (DiagnoseSyncOnValueBasedClasses != 0) {
    load_klass(Rscratch, Roop);
    ldrb(Rscratch, Address(Rscratch, Klass::misc_flags_offset()));
    tst(Rscratch, KlassFlags::_misc_is_value_based_class);
    b(done, ne);
  }

  lightweight_lock(Roop /* obj */, Rbox /* t1 */, Rscratch /* t2 */, Rscratch2 /* t3 */,
                   1 /* savemask (save t1) */, done);

  cmp(Roop, Roop); // Success: set Z
  bind(done);

  // At this point flags are set as follows:
  //  EQ -> Success
  //  NE -> Failure, branch to slow path
}

void C2_MacroAssembler::fast_unlock(Register Roop, Register Rbox, Register Rscratch, Register Rscratch2) {
  assert(VM_Version::supports_ldrex(), "unsupported, yet?");
  assert_different_registers(Roop, Rbox, Rscratch, Rscratch2);

  Label done;

  lightweight_unlock(Roop /* obj */, Rbox /* t1 */, Rscratch /* t2 */, Rscratch2 /* t3 */,
                     1 /* savemask (save t1) */, done);

  cmp(Roop, Roop); // Success: Set Z
  // Fall through

  bind(done);

  // At this point flags are set as follows:
  //  EQ -> Success
  //  NE -> Failure, branch to slow path
}
