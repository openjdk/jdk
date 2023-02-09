/*
 * Copyright (c) 2003, 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2014, 2022, Red Hat Inc. All rights reserved.
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

#if 0
#endif // 0

#ifdef INCLUDE_GEN2

address generate_poly1305_processBlocks2() {
  __ align(CodeEntryAlignment);
  StubCodeMark mark(this, "StubRoutines", "poly1305_processBlocks");
  address start = __ pc();
  Label here;
  __ enter();
  RegSet callee_saved = RegSet::range(r19, r28);
  __ push(callee_saved, sp);

  RegSetIterator<Register> regs = (RegSet::range(c_rarg0, r28) - r18_tls - rscratch1 - rscratch2).begin();

  // Arguments
  const Register input_start = *regs, length = *++regs, acc_start = *++regs, r_start = *++regs;

  // Rn is the randomly-generated key, packed into three registers
  Register R[] = { *++regs, *++regs, *++regs,};
  __ pack_26(R[0], R[1], R[2], r_start);

  // Un is the current checksum
  const RegPair u[] = {{*++regs, *++regs},
                       {*++regs, *++regs},
                       {*++regs, *++regs},};
  __ pack_26(u[0]._lo, u[1]._lo, u[2]._lo, acc_start);

  static constexpr int BLOCK_LENGTH = 16;
  Label DONE, LOOP;

  // Sn is to be the sum of Un and the next block of data
  Register S[] = { *++regs, *++regs, *++regs,};

  // Compute (R2 << 26) * 5.
  Register RR2 = *++regs;

  __ lsl(RR2, R[2], 26);
  __ add(RR2, RR2, RR2, __ LSL, 2);

  __ subsw(length, length, BLOCK_LENGTH);
  __ br(~ Assembler::GE, DONE); {
    __ bind(LOOP);

    __ poly1305_step(S, u, input_start);
    __ poly1305_multiply(u, S, R, RR2, regs);
    __ poly1305_reduce(u);

    __ subsw(length, length, BLOCK_LENGTH);
    __ br(Assembler::GE, LOOP);
  }

  // Fully reduce modulo 2^130 - 5
  __ adds(u[0]._lo, u[0]._lo, u[1]._lo, __ LSL, 52);
  __ lsr(u[1]._lo, u[1]._lo, 12);
  __ lsl(rscratch1, u[2]._lo, 40);
  __ adcs(u[1]._lo, u[1]._lo, rscratch1);
  __ lsr(u[2]._lo, u[2]._lo, 24);
  __ adc(u[2]._lo, u[2]._lo, zr);

  // Subtract 2^130 - 5
  // = 0x3_ffffffffffffffff_fffffffffffffffb
  __ mov(rscratch1, 0xfffffffffffffffb); __ subs(S[0], u[0]._lo, rscratch1);
  __ mov(rscratch1, 0xffffffffffffffff); __ sbcs(S[1], u[1]._lo, rscratch1);
  __ mov(rscratch1, 0x3);                __ sbcs(S[2], u[2]._lo, rscratch1);
  __ csel(u[0]._lo, S[0], u[0]._lo, __ HS);
  __ csel(u[1]._lo, S[1], u[1]._lo, __ HS);
  __ csel(u[2]._lo, S[2], u[2]._lo, __ HS);

  // And store it all back
  __ ubfiz(rscratch1, u[0]._lo, 0, 26);
  __ ubfx(rscratch2, u[0]._lo, 26, 26);
  __ stp(rscratch1, rscratch2, Address(acc_start));

  __ ubfx(rscratch1, u[0]._lo, 52, 12);
  __ bfi(rscratch1, u[1]._lo, 12, 14);
  __ ubfx(rscratch2, u[1]._lo, 14, 26);
  __ stp(rscratch1, rscratch2, Address(acc_start, 2 * sizeof (jlong)));

  __ extr(rscratch1, u[2]._lo, u[1]._lo, 40);
  __ str(rscratch1, Address(acc_start, 4 * sizeof (jlong)));

  __ bind(DONE);
  __ pop(callee_saved, sp);
  __ leave();
  __ ret(lr);

  return start;
}

#endif // INCLUDE_GEN2

