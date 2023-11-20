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

#ifdef INCLUDE_GEN2

typedef AbstractRegSet<FloatRegister> vRegSet;

class Regs {
public:
  Register _regs[5];
  Regs(RegSetIterator<Register> &it, int n) {
    for (int i = 0; i < n; i++) {
      _regs[i] = *it++;
    }
  }
  Regs(Register R0, Register R1, Register R2) {
    _regs[0] = R0, _regs[1] = R1, _regs[2] = R2;
  }

  Register operator[](int n) { return _regs[n]; }
  Register *operator *() { return _regs; }

  operator Register*() { return _regs; }
};

class RegPairs {
public:
  RegPair _reg_pairs[3];
  RegPairs(RegSetIterator<Register> &it, int n) {
    for (int i = 0; i < n; i++) {
      RegPair r(*it++, *it++);
      _reg_pairs[i] = r;
    }
  }
  operator RegPair*() { return _reg_pairs; }
};


address generate_poly1305_processBlocks2() {
  static constexpr int POLY1305_BLOCK_LENGTH = 16;

  address consts = __ pc();
  __ emit_int64(5);
  __ emit_int64(5);

  __ align(CodeEntryAlignment);
  StubCodeMark mark(this, "StubRoutines", "poly1305_processBlocks2");
  address start = __ pc();
  Label here;

  // __ set_last_Java_frame(sp, rfp, lr, rscratch1);
  __ enter();
  RegSet callee_saved = RegSet::range(r19, r28);
  __ push(callee_saved, sp);

  auto regs = (RegSet::range(c_rarg0, r28) - r18_tls - rscratch1 - rscratch2 + lr).begin();
  auto vregs = (vRegSet::range(v0, v7) + vRegSet::range(v16, v31)).begin();

  // Arguments
  const Register input_start = *regs++, length = *regs++, acc_start = *regs++, r_start = *regs++;

  // Rn is the key, packed into three registers
  Regs R(regs, 3);
  __ pack_26(R[0], R[1], R[2], r_start);

  // Sn is to be the sum of Un and the next block of data
  Regs S0(regs, 3), S1(regs, 3);

  // Un is the current checksum
  RegPairs u0(regs, 3), u1(regs, 3);

  // Load the initial state
  __ pack_26(u0[0]._lo, u0[1]._lo, u0[2]._lo, acc_start);

  Register RR2 = *regs++;
  __ lsl(RR2, R[2], 26);
  __ add(RR2, RR2, RR2, __ LSL, 2);

  // Just one block?
  Label SMALL;
  __ subs(zr, length, POLY1305_BLOCK_LENGTH * 8);
  __ br(__ LE, SMALL);

  // We're going to use R**4
  {
    Regs u1_lo(u1[0]._lo, u1[1]._lo, u1[2]._lo);

    poo = __ pc();

    __ poly1305_field_multiply(u1, R, R, RR2, regs);
    __ copy_3_regs(R, u1_lo);

    __ lsl(RR2, R[2], 26);
    __ add(RR2, RR2, RR2, __ LSL, 2);

    __ poly1305_field_multiply(u1, R, R, RR2, regs);
    __ copy_3_regs(R, u1_lo);

    __ lsl(RR2, R[2], 26);
    __ add(RR2, RR2, RR2, __ LSL, 2);
  }

  // u0 contains the initial state. Clear the others.
  for (int i = 0; i < 3; i++) {
    __ mov(u0[i]._hi, 0);
    __ mov(u1[i]._lo, 0); __ mov(u1[i]._hi, 0);
  }

  const FloatRegister v_u0[] = {*vregs++, *vregs++, *vregs++, *vregs++, *vregs++};
  const FloatRegister v_s0[] = {*vregs++, *vregs++, *vregs++};

  const FloatRegister v_u1[] = {*vregs++, *vregs++, *vregs++, *vregs++, *vregs++};
  const FloatRegister v_s1[] = {*vregs++, *vregs++, *vregs++};

  const FloatRegister zero = *vregs++;
  const FloatRegister r_v[] = {*vregs++, *vregs++};
  const FloatRegister rr_v[] = {*vregs++, *vregs++};

  // if (use_vec) {
    __ movi(zero, __ T16B, 0);

    __ copy_3_regs_to_5_elements(r_v, R[0], R[1], R[2]);

    // rr_v = r_v * 5
    { FloatRegister vtmp = *vregs;
      __ shl(vtmp, __ T4S, r_v[0], 2);
      __ addv(rr_v[0], __ T4S, r_v[0], vtmp);
      __ shl(vtmp, __ T4S, r_v[1], 2);
      __ addv(rr_v[1], __ T4S, r_v[1], vtmp);
    }

    for (int i = 0; i < 5; i++) {
      __ movi(v_u0[i], __ T16B, 0);
    }
  //   __ copy_3_to_5_regs(v_u0, u0[0]._lo, u0[1]._lo, u0[2]._lo);
  // }

    __ m_print52(u0[2]._lo, u0[1]._lo, u0[0]._lo, "\n\nBefore\n  u0");
    __ m_print52(u1[2]._lo, u1[1]._lo, u1[0]._lo, "  u1");
    __ m_print26(__ D, v_u0[4], v_u0[3], v_u0[2], v_u0[1], v_u0[0], 0, "v[2]");
    __ m_print26(__ D, v_u0[4], v_u0[3], v_u0[2], v_u0[1], v_u0[0], 1, "v[3]");

    {
    Label DONE, LOOP;

    int BLOCKS_PER_ITERATION = 6;

    __ subsw(rscratch1, length, POLY1305_BLOCK_LENGTH * BLOCKS_PER_ITERATION * 2);
    __ br(Assembler::LT, DONE);

    __ align(OptoLoopAlignment);
    __ bind(LOOP);
    {
      // __ poly1305_load(S0, input_start);
      // __ poly1305_load(S1, input_start);

      constexpr int COLS = 4;
      LambdaAccumulator gen[COLS];

      __ poly1305_step(gen[0], S0, u0, input_start);
      __ poly1305_field_multiply(gen[0], u0, S0, R, RR2, regs);

      __ poly1305_step(gen[1], S1, u1, input_start);
      __ poly1305_field_multiply(gen[1], u1, S1, R, RR2, regs);

      __ poly1305_step_vec(gen[2], v_s0, v_u0, zero, input_start, vregs.remaining());
      __ poly1305_multiply_vec(gen[2], v_u0, vregs.remaining(), v_s0, r_v, rr_v);
      __ poly1305_reduce_vec(gen[2], v_u0, zero, vregs.remaining());

      __ poly1305_step_vec(gen[3], v_s1, v_u1, zero, input_start, vregs.remaining());
      __ poly1305_multiply_vec(gen[3], v_u1, vregs.remaining(), v_s1, r_v, rr_v);
      __ poly1305_reduce_vec(gen[3], v_u1, zero, vregs.remaining());

      LambdaAccumulator::Iterator it[COLS];
      int len[COLS];

      int l_max = INT_MIN;
      for (int col = 0; col < COLS; col++) {
        it[col] = gen[col].iterator();
        len[col] = gen[col].length();
        l_max = MAX2(l_max, len[col]);
      }

      int err[COLS];
      for (int col = 0; col < COLS; col++) {
        err[col] = 0;
      }

      for (int i = 0; i < l_max; i++) {
        for (int col = 0; col < COLS; col++) {
          err[col] -= len[col];
          if (err[col] < 0) {
            err[col] += l_max;
            (it[col]++)();
          }
        }
      }

      // for (int col = 0; col < COLS; col++) {
      //   for (int i = 0; i < len[col]; i++) {
      //     (it[col]++)();
      //   }
      // }

      __ m_print52(u0[2]._lo, u0[1]._lo, u0[0]._lo, "  u0");
      __ m_print52(u1[2]._lo, u1[1]._lo, u1[0]._lo, "  u1");
      __ m_print26(__ D, v_u0[4], v_u0[3], v_u0[2], v_u0[1], v_u0[0], 0, "u[2]");
      __ m_print26(__ D, v_u0[4], v_u0[3], v_u0[2], v_u0[1], v_u0[0], 1, "u[3]");

      for (int col = 0; col < COLS; col++) {
        assert(*(it[col]) == nullptr, "Make sure all generators are exhausted");
      }
    }

    __ subw(length, length, POLY1305_BLOCK_LENGTH * BLOCKS_PER_ITERATION);
    __ subsw(rscratch1, length, POLY1305_BLOCK_LENGTH * BLOCKS_PER_ITERATION * 2);
    __ br(Assembler::GE, LOOP);

    __ bind(DONE);
  }

  // Last four parallel blocks
  {
    // Load R**1
    __ pack_26(R[0], R[1], R[2], r_start);
    __ lsl(RR2, R[2], 26);
    __ add(RR2, RR2, RR2, __ LSL, 2);

    __ poly1305_load(S0, input_start);
    __ poly1305_add(S0, u0);
    __ poly1305_field_multiply(u0, S0, R, RR2, regs);

    __ poly1305_load(S0, input_start);
    __ poly1305_add(S0, u0);
    __ poly1305_add(S0, u1);
    __ poly1305_field_multiply(u0, S0, R, RR2, regs);

    __ poly1305_load(S0, input_start);
    __ poly1305_add(S0, u0);
    __ poly1305_transfer(u1, v_u0, 0, *vregs);
    __ poly1305_add(S0, u1);
    __ poly1305_field_multiply(u0, S0, R, RR2, regs);

    __ poly1305_load(S0, input_start);
    __ poly1305_add(S0, u0);
    __ poly1305_transfer(u1, v_u0, 1, *vregs);
    __ poly1305_add(S0, u1);
    __ poly1305_field_multiply(u0, S0, R, RR2, regs);

    __ subw(length, length, POLY1305_BLOCK_LENGTH * 4);
  }

  // Maybe some last blocks
  __ bind(SMALL);
  {
    Label DONE, LOOP;

    __ bind(LOOP);
    __ subsw(length, length, POLY1305_BLOCK_LENGTH);
    __ br(__ LT, DONE);

    __ poly1305_step(S0, u0, input_start);
    __ poly1305_field_multiply(u0, S0, R, RR2, regs);

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
  // __ reset_last_Java_frame(true);
  __ leave();
  __ ret(lr);

  return start;
}

#endif // INCLUDE_GEN2
