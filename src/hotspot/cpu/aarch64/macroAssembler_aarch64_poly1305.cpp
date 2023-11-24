/*
 * Copyright (c) 1997, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2014, 2021, Red Hat Inc. All rights reserved.
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

#include <sys/types.h>

#include "precompiled.hpp"
#include "asm/assembler.hpp"
#include "asm/assembler.inline.hpp"
#include "macroAssembler_aarch64.hpp"
#include "memory/resourceArea.hpp"
#include "runtime/stubRoutines.hpp"

void MacroAssembler::pack_26(Register dest0, Register dest1, Register dest2, Register src) {
  ldp(dest0, rscratch1, Address(src, 0));
  orr(dest0, dest0, rscratch1, Assembler::LSL, 26);

  ldp(dest1, rscratch1, Address(src, 2 * sizeof (jlong)));
  orr(dest1, dest1, rscratch1, Assembler::LSL, 26);

  ldr(dest2, Address(src, 4 * sizeof (jlong)));
}

void MacroAssembler::wide_mul(RegPair prod, Register n, Register m) {
  mul(prod._lo, n, m);
  umulh(prod._hi, n, m);
}
void MacroAssembler::wide_madd(RegPair sum, Register n, Register m) {
  wide_mul(RegPair(rscratch1, rscratch2), n, m);
  adds(sum._lo, sum._lo, rscratch1);
  adc(sum._hi, sum._hi, rscratch2);
}

void MacroAssembler::poly1305_transfer(const RegPair u0[],
                                       const FloatRegister s[], int index,
                                       FloatRegister vscratch) {
  shl(vscratch, T2D, s[1], 26);
  Assembler::add(vscratch, T2D, s[0], vscratch);
  umov(u0[0]._lo, vscratch, D, index);

  shl(vscratch, T2D, s[3], 26);
  Assembler::add(vscratch, T2D, s[2], vscratch);
  umov(u0[1]._lo, vscratch, D, index);

  umov(u0[2]._lo, s[4], D, index);
}

// Compute d += s >> shift
void MacroAssembler::shifted_add128(const RegPair d, const RegPair s, unsigned int shift,
                                    Register scratch) {
  extr(scratch, s._hi, s._lo, shift);
  adds(d._lo, d._lo, scratch);
  lsr(scratch, s._hi, shift);
  adc(d._hi, d._hi, scratch);
}

void MacroAssembler::clear_above(const RegPair d, int shift) {
  bfc(d._lo, shift, 64-shift);
  mov(d._hi, 0);
}

void MacroAssembler::poly1305_fully_reduce(Register dest[], const RegPair u[]) {
  // Fully reduce modulo 2^130 - 5
  adds(u[0]._lo, u[0]._lo, u[1]._lo, LSL, 52);
  lsr(u[1]._lo, u[1]._lo, 12);
  lsl(rscratch1, u[2]._lo, 40);
  adcs(u[1]._lo, u[1]._lo, rscratch1);
  lsr(u[2]._lo, u[2]._lo, 24);
  adc(u[2]._lo, u[2]._lo, zr);

  // Subtract 2^130 - 5
  // = 0x3_ffffffffffffffff_fffffffffffffffb
  mov(rscratch1, 0xfffffffffffffffb); subs(dest[0], u[0]._lo, rscratch1);
  mov(rscratch1, 0xffffffffffffffff); sbcs(dest[1], u[1]._lo, rscratch1);
  mov(rscratch1, 0x3);                sbcs(dest[2], u[2]._lo, rscratch1);
  csel(dest[0], dest[0], u[0]._lo, HS);
  csel(dest[1], dest[1], u[1]._lo, HS);
  csel(dest[2], dest[2], u[2]._lo, HS);
}

#define _ acc << [=]()
// Widening multiply s * r -> u
void MacroAssembler::poly1305_multiply(AsmGenerator &acc,
                                       const RegPair u[], const Register s[], const Register r[],
                                       Register RR2, RegSetIterator<Register> scratch) {
  _ { wide_mul(u[0], s[0], r[0]); };
  _ { wide_mul(u[2], s[0], r[2]); };
  _ { wide_madd(u[0], s[1], RR2); };
  {
    Register RS2 = *scratch++;
    _ {
      // Compute (S2 << 26) * 5.
      lsl(RS2, s[2], 26);
      add(RS2, RS2, RS2, LSL, 2);
      wide_mul(u[1], RS2, r[2]);
      wide_madd(u[0], RS2, r[1]);
    };
  }
  _ { wide_madd(u[1], s[0], r[1]); };
  _ { wide_madd(u[2], s[1], r[1]); };
  _ { wide_madd(u[1], s[1], r[0]); };
  _ { wide_madd(u[2], s[2], r[0]); };
}

void MacroAssembler::poly1305_reduce(AsmGenerator &acc, const RegPair u[], const char *s) {
#define _ acc << [=]()
  // Partial reduction mod 2**130 - 5

  // Assume:
  // u[2] < 0x200000000000_0000000000000000 (i.e. 109 bits)
  // u[1] < 0x200000000000_0000000000000000 (i.e. 109 bits)
  // u[0] < 0x200000000000_0000000000000000 (i.e. 109 bits)

  // This follows from the inputs to the 3x3 multiplication all being
  // < 54 bits long.

  // Add the high part (i.e. everything from bits 52 up) of u1 to u2
  _ { shifted_add128(u[2], u[1], 52); };
  _ { clear_above(u[1], 52); };                             // u[1] < 0x10000000000000 (i.e. 52 bits)

  // Add the high part of u0 to u1
  _ { shifted_add128(u[1], u[0], 52); };
  _ { clear_above(u[0], 52); };                             // u[0] < 0x10000000000000 (i.e. 52 bits)
                                                     // u[1] < 0x200000000000000 (i.e. 57 bits)

  // Then multiply the high part of u2 by 5 and add it back to u1:u0
  _ { extr(rscratch1, u[2]._hi, u[2]._lo, 26);
        ubfx(rscratch1, rscratch1, 0, 52);
        add(rscratch1, rscratch1, rscratch1, LSL, 2);         // rscratch1 *= 5
        add(u[0]._lo, u[0]._lo, rscratch1); };

  _ { lsr(rscratch1, u[2]._hi, (26+52) % 64);
        add(rscratch1, rscratch1, rscratch1, LSL, 2);         // rscratch1 *= 5
        add(u[1]._lo, u[1]._lo, rscratch1); };
  _ { clear_above(u[2], 26); };                             // u[2] < 0x4000000 (i.e. 26 bits)
                                                     // u[1] < 0x200000000000000 (i.e. 57 bits)
                                                     // u[0] < 0x20000000000000 (i.e. 53 bits)

  // u[1] -> u[2]
  _ { add(u[2]._lo, u[2]._lo, u[1]._lo, LSR, 52); };        // u[2] < 0x8000000 (i.e. 27 bits)
  _ { bfc(u[1]._lo, 52, 64-52); };                          // u[1] < 0x10000000000000 (i.e. 52 bits)

  // u[0] -> u1
  _ { add(u[1]._lo, u[1]._lo, u[0]._lo, LSR, 52); };
  _ { bfc(u[0]._lo, 52, 64-52); };                          // u[0] < 0x10000000000000 (i.e. 52 bits)
                                                     // u[1] < 0x20000000000000 (i.e. 53 bits)
                                                     // u[2] < 0x4000000 (i.e. 27 bits)
}

void MacroAssembler::poly1305_field_multiply(AsmGenerator &acc,
                                             const RegPair u[], const Register s[], const Register r[],
                                             Register RR2, RegSetIterator<Register> scratch) {
  poly1305_multiply(acc, u, s, r, RR2, scratch);
  poly1305_reduce(acc, u, NULL);
}

// Widening multiply s * r -> u
void MacroAssembler::poly1305_multiply_vec(AsmGenerator &acc,
                                           const FloatRegister u[],
                                           const FloatRegister s[],
                                           const FloatRegister r[],
                                           const FloatRegister rr[]) {
  // Five limbs of r and rr (5·r) are packed as 32-bit integers into
  // two 128-bit vectors.

  // // (h + c) * r, without carry propagation
  // u64 u0 = r0*m0 + 5·r1*m4 + 5·r2*m3 + 5·r3*m2 + 5·r4*m1
  // u64 u1 = r0*m1 +   r1*m0 + 5·r2*m4 + 5·r3*m3 + 5·r4*m2
  // u64 u2 = r0*m2 +   r1*m1 +   r2*m0 + 5·r3*m4 + 5·r4*m3
  // u64 u3 = r0*m3 +   r1*m2 +   r2*m1 +   r3*m0 + 5·r4*m4
  // u64 u4 = r0*m4 +   r1*m3 +   r2*m2 +   r3*m1 +   r4*m0

  _ { umull(u[0], T2D, s[0], r[0], 0); };
  _ { umull2(u[1], T2D, s[0], r[0], 0); };
  _ { umull(u[2], T2D, s[1], r[0], 0); };
  _ { umull2(u[3], T2D, s[1], r[0], 0); };
  _ { umull(u[4], T2D, s[2], r[0], 0); };

  _ { umlal(u[0], T2D, s[2], rr[0], 1); };
  _ { umlal(u[1], T2D, s[0],  r[0], 1); };
  _ { umlal2(u[2], T2D, s[0],  r[0], 1); };
  _ { umlal(u[3], T2D, s[1],  r[0], 1); };
  _ { umlal2(u[4], T2D, s[1],  r[0], 1); };

  _ { umlal2(u[0], T2D, s[1], rr[0], 2); };
  _ { umlal(u[1], T2D, s[2], rr[0], 2); };
  _ { umlal(u[2], T2D, s[0],  r[0], 2); };
  _ { umlal2(u[3], T2D, s[0],  r[0], 2); };
  _ { umlal(u[4], T2D, s[1],  r[0], 2); };

  _ { umlal(u[0], T2D, s[1], rr[0], 3); };
  _ { umlal2(u[1], T2D, s[1], rr[0], 3); };
  _ { umlal(u[2], T2D, s[2], rr[0], 3); };
  _ { umlal(u[3], T2D, s[0],  r[0], 3); };
  _ { umlal2(u[4], T2D, s[0],  r[0], 3); };

  _ { umlal2(u[0], T2D, s[0], rr[1], 0); };
  _ { umlal(u[1], T2D, s[1], rr[1], 0); };
  _ { umlal2(u[2], T2D, s[1], rr[1], 0); };
  _ { umlal(u[3], T2D, s[2], rr[1], 0); };
  _ { umlal(u[4], T2D, s[0],  r[1], 0); };
}

void MacroAssembler::mov26(FloatRegister d, Register s, int lsb) {
  ubfx(rscratch1, s, lsb, 26);
  mov(d, S, 0, rscratch1);
}
void MacroAssembler::expand26(Register d, Register r) {
  lsr(d, r, 26);
  lsl(d, d, 32);
  bfxil(d, r, 0, 26);
}

void MacroAssembler::split26(const FloatRegister d[], Register s) {
  ubfx(rscratch1, s, 0, 26);
  mov(d[0], D, 0, rscratch1);
  lsr(rscratch1, s, 26);
  mov(d[1], D, 0, rscratch1);
}

void MacroAssembler::copy_3_to_5_regs(const FloatRegister d[],
                                 const Register s0, const Register s1, const Register s2) {
  split26(&d[0], s0);
  split26(&d[2], s1);
  mov(d[4], D, 0, s2);
}

void MacroAssembler::copy_3_regs_to_5_elements(const FloatRegister d[],
                                 const Register s0, const Register s1, const Register s2) {
  expand26(rscratch2, s0);
  mov(d[0], D, 0, rscratch2);
  expand26(rscratch2, s1);
  mov(d[0], D, 1, rscratch2);
  mov(d[1], D, 0, s2);
}

void MacroAssembler::poly1305_step_vec(AsmGenerator &acc,
                                       const FloatRegister s[], const FloatRegister u[],
                                       const FloatRegister zero, Register input_start) {
  FloatRegister scratch1 = u[2], scratch2 = u[3];

  _ {
    trn1(u[0], T4S, u[0], u[1]);
    trn1(u[1], T4S, u[2], u[3]);

    // The incoming sum is packed into u[0], u[1], u[4]
    // u[2] and u[3] are now free

    ld2(scratch1, scratch2, D, 0, post(input_start, 2 * wordSize));
    ld2(scratch1, scratch2, D, 1, post(input_start, 2 * wordSize));
  };

  _ { ushr(s[2], T2D, scratch2, 14+26); };
  _ { ushr(s[1], T2D, scratch1, 26+26); };
  _ { sli(s[1], T2D, scratch2, 12); };
  _ {
    ushr(scratch2, T2D, scratch2, 14);
    sli(s[1], T2D, scratch2, 32);
    sli(s[1], T4S, zero, 26);
  };
  _ { mov(s[0], T16B, scratch1); };

  _ {
    ushr(scratch1, T2D, scratch1, 26);
    sli(s[0], T2D, scratch1, 32);
    sli(s[0], T4S, zero, 26);
  };

  _ { mov(scratch1, T2D, 1 << 24); };
  _ { addv(s[2], T2D, s[2], scratch1); };
  _ { sli(s[2], T2D, zero, 32); };

  _ { addv(s[0], T4S, s[0], u[0]); };
  _ { addv(s[1], T4S, s[1], u[1]); };
  _ { addv(s[2], T4S, s[2], u[4]); };

  for (int i = 0; i <= 2; i++)
    _ {
      ext(scratch1, T16B, s[i], s[i], 8);
      zip1(s[i], T4S, s[i], scratch1);
    };
}

void MacroAssembler::poly1305_multiply_vec(AsmGenerator &acc,
                                           const FloatRegister u_v[],
                                           AbstractRegSet<FloatRegister> remaining,
                                           const FloatRegister s_v[],
                                           const FloatRegister r_v[],
                                           const FloatRegister rr_v[]) {
  poly1305_multiply_vec(acc, u_v, s_v, r_v, rr_v);
}

void MacroAssembler::poly1305_reduce_step(AsmGenerator &acc,
                                          FloatRegister d, FloatRegister s,
                                          FloatRegister zero, FloatRegister scratch) {
  _ {
    ushr(scratch, T2D, s, 26);
    Assembler::add(d, T2D, d, scratch); };
  _ { sli(s, T2D, zero, 26); };
}

void MacroAssembler::poly1305_reduce_vec(AsmGenerator &acc,
                                         const FloatRegister u[],
                                         const FloatRegister zero,
                                         AbstractRegSet<FloatRegister> scratch) {

  auto r = scratch.begin();
  // Partial reduction mod 2**130 - 5

  FloatRegister vtmp2 = *r++;
  FloatRegister vtmp3 = *r++;

  // Goll-Guerin reduction
  poly1305_reduce_step(acc, u[1], u[0], zero, vtmp2);
  poly1305_reduce_step(acc, u[4], u[3], zero, vtmp2);
  poly1305_reduce_step(acc, u[2], u[1], zero, vtmp2);
  _ {
    ushr(vtmp2, T2D, u[4], 26);
    shl(vtmp3, T2D, vtmp2, 2);
    Assembler::add(vtmp2, T2D, vtmp2, vtmp3); // vtmp2 == 5 * (u[4] >> 26)
    Assembler::add(u[0], T2D, u[0], vtmp2);
    sli(u[4], T2D, zero, 26);
  };
  poly1305_reduce_step(acc, u[3], u[2], zero, vtmp2);
  poly1305_reduce_step(acc, u[1], u[0], zero, vtmp2);
  poly1305_reduce_step(acc, u[4], u[3], zero, vtmp2);
}

void MacroAssembler::poly1305_load(AsmGenerator &acc,
                                   const Register s[], const Register input_start) {
  _ {
    ldp(rscratch1, rscratch2, post(input_start, 2 * wordSize));
    ubfx(s[0], rscratch1, 0, 52);
    extr(s[1], rscratch2, rscratch1, 52);
    ubfx(s[1], s[1], 0, 52);
    ubfx(s[2], rscratch2, 40, 24);
    orr(s[2], s[2], 1 << 24);
  };
}

void MacroAssembler::poly1305_step(AsmGenerator &acc,
                                   const Register s[], const RegPair u[], Register input_start) {
  poly1305_load(acc, s, input_start);
  _ { poly1305_add(s, u); };
}

void MacroAssembler::copy_3_regs(const Register dest[], const Register src[]) {
  for (int i = 0; i < 3; i++) {
    mov(dest[i], src[i]);
  }
}

void MacroAssembler::add_3_reg_pairs(const RegPair dest[], const RegPair src[]) {
  for (int i = 0; i < 3; i++) {
    adds(dest[i]._lo, dest[i]._lo, src[i]._lo);
    adc(dest[i]._hi, dest[i]._hi, src[i]._hi);
  }
}

void MacroAssembler::poly1305_add(const Register dest[], const RegPair src[]) {
  add(dest[0], dest[0], src[0]._lo);
  add(dest[1], dest[1], src[1]._lo);
  add(dest[2], dest[2], src[2]._lo);
}

void MacroAssembler::poly1305_add(AsmGenerator &acc,
                                  const Register dest[], const RegPair src[]) {
  _ { poly1305_add(dest, src); };
}
