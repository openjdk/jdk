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
  StubCodeMark mark(this, "StubRoutines", "poly1305_processBlocks2");
  address start = __ pc();
  Label here;
  __ enter();
  RegSet callee_saved = RegSet::range(r19, r28);
  __ push(callee_saved, sp);

  RegSetIterator<Register> regs = (RegSet::range(c_rarg0, r28) - r18_tls - rscratch1 - rscratch2 + lr).begin();

  // Arguments
  const Register input_start = *regs, length = *++regs, acc_start = *++regs, r_start = *++regs;

  __ incrementw(Address(&trips), 1);

  // Rn is the randomly-generated key, packed into three registers
  Register R[] = { *++regs, *++regs, *++regs,};
  __ pack_26(R[0], R[1], R[2], r_start);

  // Sn is to be the sum of Un and the next block of data
  Register S0[] = { *++regs, *++regs, *++regs,};
  Register S1[] = { *++regs, *++regs, *++regs,};

  // Un is the current checksum
  const RegPair u0[] = {{*++regs, *++regs},
                       {*++regs, *++regs},
                       {*++regs, *++regs},};
  const RegPair u1[] = {{*++regs, *++regs},
                       {*++regs, *++regs},
                       {*++regs, *++regs},};

  Register RR2 = *++regs;
  __ lsl(RR2, R[2], 26);
  __ add(RR2, RR2, RR2, __ LSL, 2);

  // We're going to use R**2
  {
    __ poly1305_multiply(u0, R, R, RR2, regs);
    __ poly1305_reduce(u0);
    Register u0_lo[] = {u0[0]._lo, u0[1]._lo, u0[2]._lo};
    __ copy_3_regs(R, u0_lo);

    __ lsl(RR2, R[2], 26);
    __ add(RR2, RR2, RR2, __ LSL, 2);
  }

  __ pack_26(u0[0]._lo, u0[1]._lo, u0[2]._lo, acc_start);
  // u0 contains the initial state. Clear the others.
  for (int i = 0; i < 3; i++) {
    __ mov(u0[i]._hi, 0);
    __ mov(u1[i]._lo, 0); __ mov(u1[i]._hi, 0);
  }

  static constexpr int BLOCK_LENGTH = 16;
  Label DONE, LOOP;

  __ subsw(rscratch1, length, BLOCK_LENGTH * 2);
  __ br(~ Assembler::GT, DONE); {
    __ bind(LOOP);

    __ poly1305_step(S1, u1, input_start);
    __ poly1305_multiply(u1, S1, R, RR2, regs);
    __ poly1305_reduce(u1);

    __ poly1305_step(S0, u0, input_start);
    __ poly1305_multiply(u0, S0, R, RR2, regs);
    __ poly1305_reduce(u0);

    __ subw(length, length, BLOCK_LENGTH * 2);
    __ subsw(rscratch1, length, BLOCK_LENGTH * 2);
    __ br(Assembler::GT, LOOP);

    poo = __ pc();
  }
  __ bind(DONE);

  {
    Label one;
    __ subsw(rscratch1, length, BLOCK_LENGTH * 2);
    __ br(__ LT, one);

    __ poly1305_step(S1, u1, input_start);
    __ poly1305_multiply(u1, S1, R, RR2, regs);
    __ poly1305_reduce(u1);

  __ bind(one);
    __ pack_26(R[0], R[1], R[2], r_start);
    __ lsl(RR2, R[2], 26);
    __ add(RR2, RR2, RR2, __ LSL, 2);

    __ poly1305_step(S0, u0, input_start);
    __ poly1305_multiply(u0, S0, R, RR2, regs);
    __ poly1305_reduce(u0);
  }
  // __ add_3_reg_pairs(u0, u1);

  __ add(u0[0]._lo, u0[0]._lo, u1[0]._lo);
  __ add(u0[1]._lo, u0[1]._lo, u1[1]._lo);
  __ add(u0[2]._lo, u0[2]._lo, u1[2]._lo);
  __ poly1305_fully_reduce(S0, u0);

  // And store it all back
  __ ubfiz(rscratch1, S0[0], 0, 26);
  __ ubfx(rscratch2, S0[0], 26, 26);
  __ stp(rscratch1, rscratch2, Address(acc_start));

  __ ubfx(rscratch1, S0[0], 52, 12);
  __ bfi(rscratch1, S0[1], 12, 14);
  __ ubfx(rscratch2, S0[1], 14, 26);
  __ stp(rscratch1, rscratch2, Address(acc_start, 2 * sizeof (jlong)));

  __ extr(rscratch1, S0[2], S0[1], 40);
  __ str(rscratch1, Address(acc_start, 4 * sizeof (jlong)));

  __ pop(callee_saved, sp);
  __ leave();
  __ ret(lr);

  return start;
}

#endif // INCLUDE_GEN2
