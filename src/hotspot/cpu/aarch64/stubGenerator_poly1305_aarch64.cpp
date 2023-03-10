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

typedef AbstractRegSet<FloatRegister> vRegSet;

address generate_poly1305_processBlocks2() {
  __ align(CodeEntryAlignment);
  StubCodeMark mark(this, "StubRoutines", "poly1305_processBlocks2");
  address start = __ pc();
  Label here;
  __ enter();
  RegSet callee_saved = RegSet::range(r19, r28);
  __ push(callee_saved, sp);

  RegSetIterator<Register> regs = (RegSet::range(c_rarg0, r28) - r18_tls - rscratch1 - rscratch2 + lr).begin();
  auto vregs = (vRegSet::range(v0, v7) + vRegSet::range(v16, v31)).begin();

  // Arguments
  const Register input_start = *regs, length = *++regs, acc_start = *++regs, r_start = *++regs;

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

  // Load the initial state
  __ pack_26(u0[0]._lo, u0[1]._lo, u0[2]._lo, acc_start);

  // Just one block?
  Label SMALL;
  __ subs(zr, length, BLOCK_LENGTH * 4);
  __ br(__ LE, SMALL);

  // We're going to use R**2
  {
    __ poly1305_multiply(u1, R, R, RR2, regs);
    __ poly1305_reduce(u1);
    Register u1_lo[] = {u1[0]._lo, u1[1]._lo, u1[2]._lo};
    __ copy_3_regs(R, u1_lo);

    __ lsl(RR2, R[2], 26);
    __ add(RR2, RR2, RR2, __ LSL, 2);
  }

  // u0 contains the initial state. Clear the others.
  for (int i = 0; i < 3; i++) {
    __ mov(u0[i]._hi, 0);
    __ mov(u1[i]._lo, 0); __ mov(u1[i]._hi, 0);
  }

  static constexpr int BLOCK_LENGTH = 16;

  const FloatRegister v_u1[] = {*vregs++, *vregs++, *vregs++, *vregs++, *vregs++};
  FloatRegister s_v[] = {*vregs++, *vregs++, *vregs++, *vregs++, *vregs++};

  FloatRegister upper_bits = *vregs++;
  __ movi(upper_bits, __ T16B, 0xff);
  __ shl(upper_bits, __ T2D, upper_bits, 26);  // upper_bits == 0xfffffffffc000000

  FloatRegister r_v[] = {*vregs++, *vregs++};
  FloatRegister rr_v[] = {*vregs++, *vregs++};
  __ copy_3_regs_to_5_elements(r_v, R[0], R[1], R[2]);

  { FloatRegister vtmp = *vregs;
    __ shl(vtmp, __ T4S, r_v[0], 2);
    __ addv(rr_v[0], __ T4S, r_v[0], vtmp);
    __ shl(vtmp, __ T4S, r_v[1], 2);
    __ addv(rr_v[1], __ T4S, r_v[1], vtmp);
  }

  __ copy_3_to_5_regs(v_u1, u1[0]._lo, u1[1]._lo, u1[2]._lo);
  {
    Label DONE, LOOP;

    __ bind(LOOP);
    __ subsw(rscratch1, length, BLOCK_LENGTH * 4);
    __ br(Assembler::LT, DONE);

    poo = __ pc();
    __ poly1305_step_foo(s_v, v_u1, upper_bits, input_start);
    __ poly1305_multiply_foo(v_u1, vregs.remaining(), s_v, r_v, rr_v);
    __ poly1305_reduce_foo(v_u1, vregs.remaining());

    __ poly1305_step(S0, u0, input_start);
    __ poly1305_multiply(u0, S0, R, RR2, regs);
    __ poly1305_reduce(u0);

    __ incrementw(Address(&trips), 1);

    __ subw(length, length, BLOCK_LENGTH * 2);
    __ b(LOOP);

    __ bind(DONE);
  }

  __ poly1305_transfer(u1, v_u1, *vregs);

  // Last two parallel blocks
  {
    __ poly1305_step(S1, u1, input_start);
    __ poly1305_multiply(u1, S1, R, RR2, regs);
    __ poly1305_reduce(u1);

    __ pack_26(R[0], R[1], R[2], r_start);
    __ lsl(RR2, R[2], 26);
    __ add(RR2, RR2, RR2, __ LSL, 2);

    // Load R**1
    __ poly1305_step(S0, u0, input_start);
    __ poly1305_multiply(u0, S0, R, RR2, regs);
    __ poly1305_reduce(u0);

    __ subw(length, length, BLOCK_LENGTH * 2);
  }

  // __ add_3_reg_pairs(u0, u1);

  __ add(u0[0]._lo, u0[0]._lo, u1[0]._lo);
  __ add(u0[1]._lo, u0[1]._lo, u1[1]._lo);
  __ add(u0[2]._lo, u0[2]._lo, u1[2]._lo);

  // Maybe some last blocks
  __ bind(SMALL);
  {
    Label DONE, LOOP;

    __ bind(LOOP);
    __ subsw(length, length, BLOCK_LENGTH);
    __ br(__ LT, DONE);

    __ poly1305_step(S0, u0, input_start);
    __ poly1305_multiply(u0, S0, R, RR2, regs);
    __ poly1305_reduce(u0);

    __ b(LOOP);
    __ bind(DONE);
  }
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
