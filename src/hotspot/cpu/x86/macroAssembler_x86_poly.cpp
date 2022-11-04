/*
 * Copyright (c) 2022, Intel Corporation. All rights reserved.
 *
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
#include "runtime/stubRoutines.hpp"
#include "macroAssembler_x86.hpp"

#ifdef _LP64
// References:
//  - (Normative) RFC7539 - ChaCha20 and Poly1305 for IETF Protocols
//  - M. Goll and S. Gueron, "Vectorization of Poly1305 Message Authentication Code"
//  - "The design of Poly1305" https://loup-vaillant.fr/tutorials/poly1305-design

// Explanation for the 'well known' modular arithmetic optimization, reduction by pseudo-Mersene prime 2^130-5:
//
// Reduction by 2^130-5 can be expressed as follows:
//    ( a×2^130 + b ) mod 2^130-5     //i.e. number split along the 130-bit boundary
//                                 = ( a×2^130 - 5×a + 5×a + b ) mod 2^130-5
//                                 = ( a×(2^130 - 5) + 5×a + b ) mod 2^130-5 // i.e. adding multiples of modulus is a noop
//                                 = ( 5×a + b ) mod 2^130-5
// QED: shows mathematically the well known algorithm of 'split the number down the middle, multiply upper and add'
// This is particularly useful to understand when combining with 'odd-sized' limbs that might cause misallignment
//

// Pseudocode for this file (in general):
//    * used for poly1305_multiply_scalar
//    × used for poly1305_multiply8_avx512
//    lower-case variables are scalar numbers in 3×44-bit limbs (in gprs)
//    upper-case variables are 8-element vector numbers in 3×44-bit limbs (in zmm registers)
//    [ ] used to denote vector numbers (with their elements)

// Register Map:
// GPRs:
//   input        = rdi
//   length       = rbx
//   accumulator  = rcx
//   R   = r8
//   a0  = rsi
//   a1  = r9
//   a2  = r10
//   r0  = r11
//   r1  = r12
//   c1  = r8;
//   t1  = r13
//   t2  = r14
//   t3  = r15
//   t0  = r14
//   polyCP = r13
//   stack(rsp, rbp)
//   imul(rax, rdx)
// ZMMs:
//   T: xmm0-6
//   C: xmm7-9
//   A: xmm13-18
//   B: xmm19-24
//   R: xmm25-29

// Constant Pool OFfsets:
enum polyCPOffset {
  high_bit = 0,
  mask_44 = 64,
  mask_42 = 128,
};

// Compute product for 8 16-byte message blocks,
// i.e. For each block, compute [a2 a1 a0] = [a2 a1 a0] × [r2 r1 r0]
//
// Each block/number is represented by 3 44-bit limb digits, start with multiplication
//
//      a2       a1       a0
// ×    r2       r1       r0
// ----------------------------------
//     a2×r0    a1×r0    a0×r0
// +   a1×r1    a0×r1  5×a2×r1'     (r1' = r1<<2)
// +   a0×r2  5×a2×r2' 5×a1×r2'     (r2' = r2<<2)
// ----------------------------------
//        p2       p1       p0
//
// Then, propagate the carry (bits after bit 44) from lower limbs into higher limbs.
// Then, modular reduction from upper limb wrapped to lower limbs
//
// Math Note 1: 'carry propagation' from p2 to p0 involves multiplication by 5 (i.e. slightly modified modular reduction from above):
//    ( p2×2^88 ) mod 2^130-5
//                             = ( p2'×2^88 + p2''×2^130) mod 2^130-5 // Split on 130-bit boudary
//                             = ( p2'×2^88 + p2''×2^130 - 5×p2'' + 5×p2'') mod 2^130-5
//                             = ( p2'×2^88 + p2''×(2^130 - 5) + 5×p2'') mod 2^130-5 // i.e. adding multiples of modulus is a noop
//                             = ( p2'×2^88 + 5×p2'') mod 2^130-5
//
// Math Note 2: R1P = 4*5*R1 and R2P = 4*5*R2; This precomputation allows simultaneous reduction and multiplication.
// This is not the standard 'multiply-upper-by-5', here is why the factor is 4*5 instead of 5.
// For example, partial product (a2×r2):
//    (a2×2^88)×(r2×2^88) mod 2^130-5
//                                    = (a2×r2 × 2^176) mod 2^130-5
//                                    = (a2×r2 × 2^46×2^130) mod 2^130-5
//                                    = (a2×r2×2^46 × 2^130- 5×a2×r2×2^46 + 5×a2×r2×2^46) mod 2^130-5
//                                    = (a2×r2×2^46 × (2^130- 5) + 5×a2×r2×2^46) mod 2^130-5 // i.e. adding multiples of modulus is a noop
//                                    = (5×a2×r2×2^46) mod 2^130-5
//                                    = (a2×5×r2×2^2 × 2^44) mod 2^130-5 // Align to limb boudary
//                                    = (a2×[5×r2×4] × 2^44) mod 2^130-5
//                                    = (a2×R2P × 2^44) mod 2^130-5 // i.e. R2P = 4*5*R2
//
void MacroAssembler::poly1305_multiply8_avx512(
  const XMMRegister A0, const XMMRegister A1, const XMMRegister A2,
  const XMMRegister R0, const XMMRegister R1, const XMMRegister R2, const XMMRegister R1P, const XMMRegister R2P, const Register polyCP)
{
  const XMMRegister P0_L = xmm0;
  const XMMRegister P0_H = xmm1;
  const XMMRegister P1_L = xmm2;
  const XMMRegister P1_H = xmm3;
  const XMMRegister P2_L = xmm4;
  const XMMRegister P2_H = xmm5;
  const XMMRegister TMP1 = xmm6;

  // Reset partial sums
  evpxorq(P0_L, P0_L, P0_L, Assembler::AVX_512bit);
  evpxorq(P0_H, P0_H, P0_H, Assembler::AVX_512bit);
  evpxorq(P1_L, P1_L, P1_L, Assembler::AVX_512bit);
  evpxorq(P1_H, P1_H, P1_H, Assembler::AVX_512bit);
  evpxorq(P2_L, P2_L, P2_L, Assembler::AVX_512bit);
  evpxorq(P2_H, P2_H, P2_H, Assembler::AVX_512bit);

  // Calculate partial products
  evpmadd52luq(P0_L, A2, R1P, Assembler::AVX_512bit);
  evpmadd52huq(P0_H, A2, R1P, Assembler::AVX_512bit);
  evpmadd52luq(P1_L, A2, R2P, Assembler::AVX_512bit);
  evpmadd52huq(P1_H, A2, R2P, Assembler::AVX_512bit);
  evpmadd52luq(P2_L, A2, R0, Assembler::AVX_512bit);
  evpmadd52huq(P2_H, A2, R0, Assembler::AVX_512bit);

  evpmadd52luq(P1_L, A0, R1, Assembler::AVX_512bit);
  evpmadd52huq(P1_H, A0, R1, Assembler::AVX_512bit);
  evpmadd52luq(P2_L, A0, R2, Assembler::AVX_512bit);
  evpmadd52huq(P2_H, A0, R2, Assembler::AVX_512bit);
  evpmadd52luq(P0_L, A0, R0, Assembler::AVX_512bit);
  evpmadd52huq(P0_H, A0, R0, Assembler::AVX_512bit);

  evpmadd52luq(P0_L, A1, R2P, Assembler::AVX_512bit);
  evpmadd52huq(P0_H, A1, R2P, Assembler::AVX_512bit);
  evpmadd52luq(P1_L, A1, R0, Assembler::AVX_512bit);
  evpmadd52huq(P1_H, A1, R0, Assembler::AVX_512bit);
  evpmadd52luq(P2_L, A1, R1, Assembler::AVX_512bit);
  evpmadd52huq(P2_H, A1, R1, Assembler::AVX_512bit);

  // Carry propagation:
  // (Not quite aligned)                           | More mathematically correct:
  //          P2_L   P1_L   P0_L                   |                  P2_L×2^88 + P1_L×2^44 + P0_L×2^0
  // + P2_H   P1_H   P0_H                          |   + P2_H×2^140 + P1_H×2^96 + P0_H×2^52
  // ---------------------------                   |   -----------------------------------------------
  // = P2_H    A2    A1     A0                     |   = P2_H×2^130 +   A2×2^88 +   A1×2^44 +   A0×2^0
  //
  vpsrlq(TMP1, P0_L, 44, Assembler::AVX_512bit);
  evpandq(A0, P0_L, Address(polyCP, mask_44), Assembler::AVX_512bit); // Clear top 20 bits

  vpsllq(P0_H, P0_H, 8, Assembler::AVX_512bit);
  vpaddq(P0_H, P0_H, TMP1, Assembler::AVX_512bit);
  vpaddq(P1_L, P1_L, P0_H, Assembler::AVX_512bit);
  evpandq(A1, P1_L, Address(polyCP, mask_44), Assembler::AVX_512bit); // Clear top 20 bits

  vpsrlq(TMP1, P1_L, 44, Assembler::AVX_512bit);
  vpsllq(P1_H, P1_H, 8, Assembler::AVX_512bit);
  vpaddq(P1_H, P1_H, TMP1, Assembler::AVX_512bit);
  vpaddq(P2_L, P2_L, P1_H, Assembler::AVX_512bit);
  evpandq(A2, P2_L, Address(polyCP, mask_42), Assembler::AVX_512bit); // Clear top 22 bits

  vpsrlq(TMP1, P2_L, 42, Assembler::AVX_512bit);
  vpsllq(P2_H, P2_H, 10, Assembler::AVX_512bit);
  vpaddq(P2_H, P2_H, TMP1, Assembler::AVX_512bit);

  // Reduction: p2->a0->a1
  // Multiply by 5 the highest bits (p2 is above 130 bits)
  vpaddq(A0, A0, P2_H, Assembler::AVX_512bit);
  vpsllq(P2_H, P2_H, 2, Assembler::AVX_512bit);
  vpaddq(A0, A0, P2_H, Assembler::AVX_512bit);
  vpsrlq(TMP1, A0, 44, Assembler::AVX_512bit);
  evpandq(A0, A0, Address(polyCP, mask_44), Assembler::AVX_512bit);
  vpaddq(A1, A1, TMP1, Assembler::AVX_512bit);
}

// Compute product for a single 16-byte message blocks
// - Assumes that r = [r1 r0] is only 128 bits (not 130)
// - When only128 is set, Input [a2 a1 a0] is 128 bits (i.e. a2==0)
// - Output [a2 a1 a0] is at least 130 bits (i.e. a2 is used)
//
// Note 1: a2 here is only two bits so anything above is subject of reduction.
// Note 2: Constant c1 = 5xr1 = r1 + (r1 << 2) simplifies multiply with less operations
//
// Flow of the code below is as follows:
//
//          a2        a1        a0
//        x           r1        r0
//   -----------------------------
//       a2×r0     a1×r0     a0×r0
//   +             a0×r1
//   +           5xa2xr1   5xa1xr1
//   -----------------------------
//     [0|L2L] [L1H|L1L] [L0H|L0L]
//
//   Registers:  t3:t2     t1:a0
//
// Completing the multiply and adding (with carry) 3x128-bit limbs into
// 192-bits again (3x64-bits):
// a0 = L0L
// a1 = L0H + L1L
// t3 = L1H + L2L
void MacroAssembler::poly1305_multiply_scalar(
  const Register a0, const Register a1, const Register a2,
  const Register r0, const Register r1, const Register c1, bool only128)
{
  const Register t1 = r13;
  const Register t2 = r14;
  const Register t3 = r15;
  // Note mulq instruction requires/clobers rax, rdx

  // t3:t2 = (a0 * r1)
  movq(rax, r1);
  mulq(a0);
  movq(t2, rax);
  movq(t3, rdx);

  // t1:a0 = (a0 * r0)
  movq(rax, r0);
  mulq(a0);
  movq(a0, rax); // a0 not used in other operations
  movq(t1, rdx);

  // t3:t2 += (a1 * r0)
  movq(rax, r0);
  mulq(a1);
  addq(t2, rax);
  adcq(t3, rdx);

  // t1:a0 += (a1 * r1x5)
  movq(rax, c1);
  mulq(a1);
  addq(a0, rax);
  adcq(t1, rdx);

  // Note: a2 is clamped to 2-bits,
  //       r1/r0 is clamped to 60-bits,
  //       their product is less than 2^64.

  if (only128) { // Accumulator only 128 bits, i.e. a2 == 0
    // just move and add t1-t2 to a1
    movq(a1, t1);
    addq(a1, t2);
    adcq(t3, 0);
  } else {
    // t3:t2 += (a2 * r1x5)
    movq(a1, a2); // use a1 for a2
    imulq(a1, c1);
    addq(t2, a1);
    adcq(t3, 0);

    movq(a1, t1); // t1:a0 => a1:a0

    // t3:a1 += (a2 * r0):t2
    imulq(a2, r0);
    addq(a1, t2);
    adcq(t3, a2);
  }

  // At this point, 3 64-bit limbs are in t3:a1:a0
  // t3 can span over more than 2 bits so final partial reduction step is needed.
  //
  // Partial reduction (just to fit into 130 bits)
  //    a2 = t3 & 3
  //    k = (t3 & ~3) + (t3 >> 2)
  //         Y    x4  +  Y    x1
  //    a2:a1:a0 += k
  //
  // Result will be in a2:a1:a0
  movq(t1, t3);
  movl(a2, t3); // DWORD
  andq(t1, ~3);
  shrq(t3, 2);
  addq(t1, t3);
  andl(a2, 3); // DWORD

  // a2:a1:a0 += k (kept in t1)
  addq(a0, t1);
  adcq(a1, 0);
  adcl(a2, 0); // DWORD
}

// Convert array of 128-bit numbers in quadwords (in D0:D1) into 128-bit numbers across 44-bit limbs (in L0:L1:L2)
// Optionally pad all the numbers (i.e. add 2^128)
//
//         +-------------------------+-------------------------+
//  D0:D1  | h0 h1 g0 g1 f0 f1 e0 e1 | d0 d1 c0 c1 b0 b1 a0 a1 |
//         +-------------------------+-------------------------+
//         +-------------------------+
//  L2     | h2 d2 g2 c2 f2 b2 e2 a2 |
//         +-------------------------+
//         +-------------------------+
//  L1     | h1 d1 g1 c1 f1 b1 e1 a1 |
//         +-------------------------+
//         +-------------------------+
//  L0     | h0 d0 g0 c0 f0 b0 e0 a0 |
//         +-------------------------+
//
void MacroAssembler::poly1305_limbs_avx512(
    const XMMRegister D0, const XMMRegister D1,
    const XMMRegister L0, const XMMRegister L1, const XMMRegister L2, bool padMSG, const Register polyCP)
{
  const XMMRegister TMP1 = xmm0;
  const XMMRegister TMP2 = xmm1;
  // Interleave blocks of data
  evpunpckhqdq(TMP1, D0, D1, Assembler::AVX_512bit);
  evpunpcklqdq(L0, D0, D1, Assembler::AVX_512bit);

  // Highest 42-bit limbs of new blocks
  vpsrlq(L2, TMP1, 24, Assembler::AVX_512bit);
  if (padMSG) {
    evporq(L2, L2, Address(polyCP, high_bit), Assembler::AVX_512bit); // Add 2^128 to all 8 final qwords of the message
  }

  // Middle 44-bit limbs of new blocks
  vpsrlq(L1, L0, 44, Assembler::AVX_512bit);
  vpsllq(TMP2, TMP1, 20, Assembler::AVX_512bit);
  vpternlogq(L1, 0xA8, TMP2, Address(polyCP, mask_44), Assembler::AVX_512bit); // (A OR B AND C)

  // Lowest 44-bit limbs of new blocks
  evpandq(L0, L0, Address(polyCP, mask_44), Assembler::AVX_512bit);
}

/**
 * Copy 5×26-bit (unreduced) limbs stored at Register limbs into  a2:a1:a0 (3×64-bit limbs)
 *
 * a2 is optional. When only128 is set, limbs are expected to fit into 128-bits (i.e. a1:a0 such as clamped R)
 */
void MacroAssembler::poly1305_limbs(const Register limbs, const Register a0, const Register a1, const Register a2, bool only128)
{
  const Register t1 = r13;
  const Register t2 = r14;

  movq(a0, Address(limbs, 0));
  movq(t1, Address(limbs, 8));
  shlq(t1, 26);
  addq(a0, t1);
  movq(t1, Address(limbs, 16));
  movq(t2, Address(limbs, 24));
  movq(a1, t1);
  shlq(t1, 52);
  shrq(a1, 12);
  shlq(t2, 14);
  addq(a0, t1);
  adcq(a1, t2);
  movq(t1, Address(limbs, 32));
  if (!only128) {
    movq(a2, t1);
    shrq(a2, 24);
  }
  shlq(t1, 40);
  addq(a1, t1);
  if (only128) {
    return;
  }
  adcq(a2, 0);

  // One round of reduction
  // Take bits above 130 in a2, multiply by 5 and add to a2:a1:a0
  movq(t1, a2);
  andq(t1, ~3);
  andq(a2, 3);
  movq(t2, t1);
  shrq(t2, 2);
  addq(t1, t2);

  addq(a0, t1);
  adcq(a1, 0);
  adcq(a2, 0);
}


/**
 * Break 3×64-bit a2:a1:a0 limbs into 5×26-bit limbs and store out into 5 quadwords at address `limbs`
 */
void MacroAssembler::poly1305_limbs_out(const Register a0, const Register a1, const Register a2, const Register limbs)
{
  const Register t1 = r13;
  const Register t2 = r14;

  // Extra round of reduction
  // Take bits above 130 in a2, multiply by 5 and add to a2:a1:a0
  movq(t1, a2);
  andq(t1, ~3);
  andq(a2, 3);
  movq(t2, t1);
  shrq(t2, 2);
  addq(t1, t2);

  addq(a0, t1);
  adcq(a1, 0);
  adcq(a2, 0);

  // Chop a2:a1:a0 into 26-bit limbs
  movl(t1, a0);
  andl(t1, 0x3ffffff);
  movq(Address(limbs, 0), t1);

  shrq(a0, 26);
  movl(t1, a0);
  andl(t1, 0x3ffffff);
  movq(Address(limbs, 8), t1);

  shrq(a0, 26); // 12 bits left in a0, concatenate 14 from a1
  movl(t1, a1);
  shll(t1, 12);
  addl(t1, a0);
  andl(t1, 0x3ffffff);
  movq(Address(limbs, 16), t1);

  shrq(a1, 14); // already used up 14 bits
  shlq(a2, 50); // a2 contains 2 bits when reduced, but $Element.limbs dont have to be fully reduced
  addq(a1, a2); // put remaining bits into a1

  movl(t1, a1);
  andl(t1, 0x3ffffff);
  movq(Address(limbs, 24), t1);

  shrq(a1, 26);
  movl(t1, a1);
  //andl(t1, 0x3ffffff); doesnt have to be fully reduced, leave remaining bit(s)
  movq(Address(limbs, 32), t1);
}

// This function consumes as many whole 16*16-byte blocks as available in input
// After execution, input and length will point at remaining (unprocessed) data
// and [a2 a1 a0] will contain the current accumulator value
//
// Math Note:
//    Put simply, main loop in this function multiplies each message block by r^16; why this works? 'Math' happens before and after.. why as follows:
//
//     hash = ((((m1*r + m2)*r + m3)*r ...  mn)*r
//          = m1*r^n + m2*r^(n-1) + ... +mn_1*r^2 + mn*r  // Horner's rule
//
//          = m1*r^n     + m4*r^(n-4) + m8*r^(n-8) ...    // split into 4 groups for brevity, same applies to 16
//          + m2*r^(n-1) + m5*r^(n-5) + m9*r^(n-9) ...
//          + m3*r^(n-2) + m6*r^(n-6) + m10*r^(n-10) ...
//          + m4*r^(n-3) + m7*r^(n-7) + m11*r^(n-11) ...
//
//          = r^4 * (m1*r^(n-4) + m4*r^(n-8) + m8 *r^(n-16) ... + mn_3)   // factor out r^4..r; same applies to 16 but r^16..r factors
//          + r^3 * (m2*r^(n-4) + m5*r^(n-8) + m9 *r^(n-16) ... + mn_2)
//          + r^2 * (m3*r^(n-4) + m6*r^(n-8) + m10*r^(n-16) ... + mn_1)
//          + r^1 * (m4*r^(n-4) + m7*r^(n-8) + m11*r^(n-16) ... + mn_0)   // Note last message group has no multiplier
//
//          = r^4 * (((m1*r^4 + m4)*r^4 + m8 )*r^4 ... + mn_3)   // reverse Horner's rule, for each group
//          + r^3 * (((m2*r^4 + m5)*r^4 + m9 )*r^4 ... + mn_2)
//          + r^2 * (((m3*r^4 + m6)*r^4 + m10)*r^4 ... + mn_1)
//          + r^1 * (((m4*r^4 + m7)*r^4 + m11)*r^4 ... + mn_0)
//
// Also see M. Goll and S. Gueron, "Vectorization of Poly1305 Message Authentication Code"
//
// Pseudocode for this function:
//  * used for poly1305_multiply_scalar
//  × used for poly1305_multiply8_avx512
//  lower-case variables are scalar numbers in 3×44-bit limbs (in gprs)
//  upper-case variables are 8&16-element vector numbers in 3×44-bit limbs (in zmm registers)
//
//    C = a       // [0 0 0 0 0 0 0 a]
//    AL = limbs(input)
//    AH = limbs(input+8)
//    AL = AL + C
//    input+=16, length-=16
//
//    a = r
//    a = a*r
//  r^2 = a
//    a = a*r
//  r^3 = a
//    r = a*r
//  r^4 = a
//
//    T = r^4 || r^3 || r^2 || r
//    B = limbs(T)           // [r^4  0  r^3  0  r^2  0  r^1  0 ]
//    C = B >> 1             // [ 0  r^4  0  r^3  0  r^2  0  r^1]
//    R = r^4 || r^4 || ..   // [r^4 r^4 r^4 r^4 r^4 r^4 r^4 r^4]
//    B = B×R                // [r^8  0  r^7  0  r^6  0  r^5  0 ]
//    B = B | C              // [r^8 r^4 r^7 r^3 r^6 r^2 r^5 r^1]
//    push(B)
//    R = r^8 || r^8 || ..   // [r^8 r^8 r^8 r^8 r^8 r^8 r^8 r^8]
//    B = B × R              // [r^16 r^12 r^15 r^11 r^14 r^10 r^13 r^9]
//    push(B)
//    R = r^16 || r^16 || .. // [r^16 r^16 r^16 r^16 r^16 r^16 r^16 r^16]
//
// for (;length>=16; input+=16, length-=16)
//     BL = limbs(input)
//     BH = limbs(input+8)
//     AL = AL × R
//     AH = AH × R
//     AL = AL + BL
//     AH = AH + BH
//
//  B = pop()
//  R = pop()
//  AL = AL × R
//  AH = AH × B
//  A = AL + AH // 16->8 blocks
//  T = A >> 4  // 8 ->4 blocks
//  A = A + T
//  T = A >> 2  // 4 ->2 blocks
//  A = A + T
//  T = A >> 1  // 2 ->1 blocks
//  A = A + T
//  a = A
void MacroAssembler::poly1305_process_blocks_avx512(const Register input, const Register length,
  const Register a0, const Register a1, const Register a2,
  const Register r0, const Register r1, const Register c1)
{
  Label L_process256Loop, L_process256LoopDone;
  // Register Map:
  // reserved: rsp, rbp, rcx
  // PARAMs: rdi, rbx, rsi, r8-r12
  // poly1305_multiply_scalar clobbers: r13-r15, rax, rdx
  const Register t0 = r14;
  const Register t1 = r13;
  const Register polyCP = r13;

  // poly1305_limbs_avx512 clobbers: xmm0, xmm1
  // poly1305_multiply8_avx512 clobbers: xmm0-xmm6
  const XMMRegister T0 = xmm2;
  const XMMRegister T1 = xmm3;
  const XMMRegister T2 = xmm4;

  const XMMRegister C0 = xmm7;
  const XMMRegister C1 = xmm8;
  const XMMRegister C2 = xmm9;

  const XMMRegister A0 = xmm13;
  const XMMRegister A1 = xmm14;
  const XMMRegister A2 = xmm15;
  const XMMRegister A3 = xmm16;
  const XMMRegister A4 = xmm17;
  const XMMRegister A5 = xmm18;

  const XMMRegister B0 = xmm19;
  const XMMRegister B1 = xmm20;
  const XMMRegister B2 = xmm21;
  const XMMRegister B3 = xmm22;
  const XMMRegister B4 = xmm23;
  const XMMRegister B5 = xmm24;

  const XMMRegister R0 = xmm25;
  const XMMRegister R1 = xmm26;
  const XMMRegister R2 = xmm27;
  const XMMRegister R1P = xmm28;
  const XMMRegister R2P = xmm29;

  subq(rsp, 512/8*6); // Make room to store 6 zmm registers (powers of R)
  lea(polyCP, ExternalAddress(StubRoutines::x86::poly1305_mask_addr()));

  // Spread accumulator into 44-bit limbs in quadwords C0,C1,C2
  movq(t0, a0);
  andq(t0, Address(polyCP, mask_44)); // First limb (Acc[43:0])
  movq(C0, t0);

  movq(t0, a1);
  shrdq(a0, t0, 44);
  andq(a0, Address(polyCP, mask_44)); // Second limb (Acc[77:52])
  movq(C1, a0);

  shrdq(a1, a2, 24);
  andq(a1, Address(polyCP, mask_42)); // Third limb (Acc[129:88])
  movq(C2, a1);

  // To add accumulator, we must unroll first loop iteration

  // Load first block of data (128 bytes) and pad
  // A0 to have bits 0-43 of all 8 blocks in 8 qwords
  // A1 to have bits 87-44 of all 8 blocks in 8 qwords
  // A2 to have bits 127-88 of all 8 blocks in 8 qwords
  evmovdquq(T0, Address(input, 0), Assembler::AVX_512bit);
  evmovdquq(T1, Address(input, 64), Assembler::AVX_512bit);
  poly1305_limbs_avx512(T0, T1, A0, A1, A2, true, polyCP);

  // Add accumulator to the fist message block
  vpaddq(A0, A0, C0, Assembler::AVX_512bit);
  vpaddq(A1, A1, C1, Assembler::AVX_512bit);
  vpaddq(A2, A2, C2, Assembler::AVX_512bit);

  // Load next blocks of data (128 bytes)  and pad
  // A3 to have bits 0-43 of all 8 blocks in 8 qwords
  // A4 to have bits 87-44 of all 8 blocks in 8 qwords
  // A5 to have bits 127-88 of all 8 blocks in 8 qwords
  evmovdquq(T0, Address(input, 64*2), Assembler::AVX_512bit);
  evmovdquq(T1, Address(input, 64*3), Assembler::AVX_512bit);
  poly1305_limbs_avx512(T0, T1, A3, A4, A5, true, polyCP);

  subl(length, 16*16);
  lea(input, Address(input,16*16));

  // Compute the powers of R^1..R^4 and form 44-bit limbs of each
  // T0 to have bits 0-127 in 4 quadword pairs
  // T1 to have bits 128-129 in alternating 8 qwords
  vpxorq(T1, T1, T1, Assembler::AVX_512bit);
  movq(T2, r0);
  vpinsrq(T2, T2, r1, 1);
  vinserti32x4(T0, T0, T2, 3);

  // Calculate R^2
  movq(a0, r0);
  movq(a1, r1);
  // "Clever": a2 not set because poly1305_multiply_scalar has a flag to indicate 128-bit accumulator
  poly1305_multiply_scalar(a0, a1, a2, r0, r1, c1, true);

  movq(T2, a0);
  vpinsrq(T2, T2, a1, 1);
  vinserti32x4(T0, T0, T2, 2);
  movq(T2, a2);
  vinserti32x4(T1, T1, T2, 2);

  // Calculate R^3
  poly1305_multiply_scalar(a0, a1, a2, r0, r1, c1, false);

  movq(T2, a0);
  vpinsrq(T2, T2, a1, 1);
  vinserti32x4(T0, T0, T2, 1);
  movq(T2, a2);
  vinserti32x4(T1, T1, T2, 1);

  // Calculate R^4
  poly1305_multiply_scalar(a0, a1, a2, r0, r1, c1, false);

  movq(T2, a0);
  vpinsrq(T2, T2, a1, 1);
  vinserti32x4(T0, T0, T2, 0);
  movq(T2, a2);
  vinserti32x4(T1, T1, T2, 0);

  // Interleave the powers of R^1..R^4 to form 44-bit limbs (half-empty)
  // B0 to have bits 0-43 of all 4 blocks in alternating 8 qwords
  // B1 to have bits 87-44 of all 4 blocks in alternating 8 qwords
  // B2 to have bits 127-88 of all 4 blocks in alternating 8 qwords
  lea(polyCP, ExternalAddress(StubRoutines::x86::poly1305_mask_addr()));
  vpxorq(T2, T2, T2, Assembler::AVX_512bit);
  poly1305_limbs_avx512(T0, T2, B0, B1, B2, false, polyCP);

  // T1 contains the 2 highest bits of the powers of R
  vpsllq(T1, T1, 40, Assembler::AVX_512bit);
  evporq(B2, B2, T1, Assembler::AVX_512bit);

  // Broadcast 44-bit limbs of R^4 into R0,R1,R2
  mov(t0, a0);
  andq(t0, Address(polyCP, mask_44)); // First limb (R^4[43:0])
  evpbroadcastq(R0, t0, Assembler::AVX_512bit);

  movq(t0, a1);
  shrdq(a0, t0, 44);
  andq(a0, Address(polyCP, mask_44)); // Second limb (R^4[87:44])
  evpbroadcastq(R1, a0, Assembler::AVX_512bit);

  shrdq(a1, a2, 24);
  andq(a1, Address(polyCP, mask_42)); // Third limb (R^4[129:88])
  evpbroadcastq(R2, a1, Assembler::AVX_512bit);

  // Generate 4*5*R^4 into {R2P,R1P}
  // Used as multiplier in poly1305_multiply8_avx512 so can
  // ignore bottom limb and carry propagation
  vpsllq(R1P, R1, 2, Assembler::AVX_512bit);    // 4*R^4
  vpsllq(R2P, R2, 2, Assembler::AVX_512bit);
  vpaddq(R1P, R1P, R1, Assembler::AVX_512bit);  // 5*R^4
  vpaddq(R2P, R2P, R2, Assembler::AVX_512bit);
  vpsllq(R1P, R1P, 2, Assembler::AVX_512bit);   // 4*5*R^4
  vpsllq(R2P, R2P, 2, Assembler::AVX_512bit);

  // Move R^4..R^1 one element over
  vpslldq(C0, B0, 8, Assembler::AVX_512bit);
  vpslldq(C1, B1, 8, Assembler::AVX_512bit);
  vpslldq(C2, B2, 8, Assembler::AVX_512bit);

  // Calculate R^8-R^5
  poly1305_multiply8_avx512(B0, B1, B2,             // ACC=R^4..R^1
                            R0, R1, R2, R1P, R2P,   // R^4..R^4, 4*5*R^4
                            polyCP);

  // Interleave powers of R: R^8 R^4 R^7 R^3 R^6 R^2 R^5 R
  evporq(B0, B0, C0, Assembler::AVX_512bit);
  evporq(B1, B1, C1, Assembler::AVX_512bit);
  evporq(B2, B2, C2, Assembler::AVX_512bit);

  // Broadcast R^8
  vpbroadcastq(R0, B0, Assembler::AVX_512bit);
  vpbroadcastq(R1, B1, Assembler::AVX_512bit);
  vpbroadcastq(R2, B2, Assembler::AVX_512bit);

  // Generate 4*5*R^8
  vpsllq(R1P, R1, 2, Assembler::AVX_512bit);
  vpsllq(R2P, R2, 2, Assembler::AVX_512bit);
  vpaddq(R1P, R1P, R1, Assembler::AVX_512bit);    // 5*R^8
  vpaddq(R2P, R2P, R2, Assembler::AVX_512bit);
  vpsllq(R1P, R1P, 2, Assembler::AVX_512bit);     // 4*5*R^8
  vpsllq(R2P, R2P, 2, Assembler::AVX_512bit);

  // Store R^8-R for later use
  evmovdquq(Address(rsp, 64*0), B0, Assembler::AVX_512bit);
  evmovdquq(Address(rsp, 64*1), B1, Assembler::AVX_512bit);
  evmovdquq(Address(rsp, 64*2), B2, Assembler::AVX_512bit);

  // Calculate R^16-R^9
  poly1305_multiply8_avx512(B0, B1, B2,           // ACC=R^8..R^1
                            R0, R1, R2, R1P, R2P, // R^8..R^8, 4*5*R^8
                            polyCP);

  // Store R^16-R^9 for later use
  evmovdquq(Address(rsp, 64*3), B0, Assembler::AVX_512bit);
  evmovdquq(Address(rsp, 64*4), B1, Assembler::AVX_512bit);
  evmovdquq(Address(rsp, 64*5), B2, Assembler::AVX_512bit);

  // Broadcast R^16
  vpbroadcastq(R0, B0, Assembler::AVX_512bit);
  vpbroadcastq(R1, B1, Assembler::AVX_512bit);
  vpbroadcastq(R2, B2, Assembler::AVX_512bit);

  // Generate 4*5*R^16
  vpsllq(R1P, R1, 2, Assembler::AVX_512bit);
  vpsllq(R2P, R2, 2, Assembler::AVX_512bit);
  vpaddq(R1P, R1P, R1, Assembler::AVX_512bit);  // 5*R^16
  vpaddq(R2P, R2P, R2, Assembler::AVX_512bit);
  vpsllq(R1P, R1P, 2, Assembler::AVX_512bit);   // 4*5*R^16
  vpsllq(R2P, R2P, 2, Assembler::AVX_512bit);

  // VECTOR LOOP: process 16 * 16-byte message block at a time
  bind(L_process256Loop);
  cmpl(length, 16*16);
  jcc(Assembler::less, L_process256LoopDone);

  // Load and interleave next block of data (128 bytes)
  evmovdquq(T0, Address(input, 0), Assembler::AVX_512bit);
  evmovdquq(T1, Address(input, 64), Assembler::AVX_512bit);
  poly1305_limbs_avx512(T0, T1, B0, B1, B2, true, polyCP);

  // Load and interleave next block of data (128 bytes)
  evmovdquq(T0, Address(input, 64*2), Assembler::AVX_512bit);
  evmovdquq(T1, Address(input, 64*3), Assembler::AVX_512bit);
  poly1305_limbs_avx512(T0, T1, B3, B4, B5, true, polyCP);

  poly1305_multiply8_avx512(A0, A1, A2,            // MSG/ACC 16 blocks
                            R0, R1, R2, R1P, R2P,  //R^16..R^16, 4*5*R^16
                            polyCP);
  poly1305_multiply8_avx512(A3, A4, A5,            // MSG/ACC 16 blocks
                            R0, R1, R2, R1P, R2P,  //R^16..R^16, 4*5*R^16
                            polyCP);

  vpaddq(A0, A0, B0, Assembler::AVX_512bit); // Add low 42-bit bits from new blocks to accumulator
  vpaddq(A1, A1, B1, Assembler::AVX_512bit); // Add medium 42-bit bits from new blocks to accumulator
  vpaddq(A2, A2, B2, Assembler::AVX_512bit); //Add highest bits from new blocks to accumulator
  vpaddq(A3, A3, B3, Assembler::AVX_512bit); // Add low 42-bit bits from new blocks to accumulator
  vpaddq(A4, A4, B4, Assembler::AVX_512bit); // Add medium 42-bit bits from new blocks to accumulator
  vpaddq(A5, A5, B5, Assembler::AVX_512bit); // Add highest bits from new blocks to accumulator

  subl(length, 16*16);
  lea(input, Address(input,16*16));
  jmp(L_process256Loop);

  bind(L_process256LoopDone);

  // Tail processing: Need to multiply ACC by R^16..R^1 and add it all up into a single scalar value
  // Read R^16-R^9
  evmovdquq(B0, Address(rsp, 64*3), Assembler::AVX_512bit);
  evmovdquq(B1, Address(rsp, 64*4), Assembler::AVX_512bit);
  evmovdquq(B2, Address(rsp, 64*5), Assembler::AVX_512bit);
  // Read R^8-R
  evmovdquq(R0, Address(rsp, 64*0), Assembler::AVX_512bit);
  evmovdquq(R1, Address(rsp, 64*1), Assembler::AVX_512bit);
  evmovdquq(R2, Address(rsp, 64*2), Assembler::AVX_512bit);

  // Generate 4*5*[R^16..R^9] (ignore lowest limb)
  vpsllq(T0, B1, 2, Assembler::AVX_512bit);
  vpaddq(B3, B1, T0, Assembler::AVX_512bit); // R1' (R1*5)
  vpsllq(T0, B2, 2, Assembler::AVX_512bit);
  vpaddq(B4, B2, T0, Assembler::AVX_512bit); // R2' (R2*5)
  vpsllq(B3, B3, 2, Assembler::AVX_512bit);  // 4*5*R
  vpsllq(B4, B4, 2, Assembler::AVX_512bit);

  // Generate 4*5*[R^8..R^1] (ignore lowest limb)
  vpsllq(T0, R1, 2, Assembler::AVX_512bit);
  vpaddq(R1P, R1, T0, Assembler::AVX_512bit); // R1' (R1*5)
  vpsllq(T0, R2, 2, Assembler::AVX_512bit);
  vpaddq(R2P, R2, T0, Assembler::AVX_512bit); // R2' (R2*5)
  vpsllq(R1P, R1P, 2, Assembler::AVX_512bit); // 4*5*R
  vpsllq(R2P, R2P, 2, Assembler::AVX_512bit);

  poly1305_multiply8_avx512(A0, A1, A2,            // MSG/ACC 16 blocks
                              B0, B1, B2, B3, B4,  // R^16-R^9, R1P, R2P
                              polyCP);
  poly1305_multiply8_avx512(A3, A4, A5,              // MSG/ACC 16 blocks
                              R0, R1, R2, R1P, R2P,  // R^8-R, R1P, R2P
                              polyCP);

  // Add all blocks (horizontally)
  // 16->8 blocks
  vpaddq(A0, A0, A3, Assembler::AVX_512bit);
  vpaddq(A1, A1, A4, Assembler::AVX_512bit);
  vpaddq(A2, A2, A5, Assembler::AVX_512bit);

  // 8 -> 4 blocks
  vextracti64x4(T0, A0, 1);
  vextracti64x4(T1, A1, 1);
  vextracti64x4(T2, A2, 1);
  vpaddq(A0, A0, T0, Assembler::AVX_256bit);
  vpaddq(A1, A1, T1, Assembler::AVX_256bit);
  vpaddq(A2, A2, T2, Assembler::AVX_256bit);

  // 4 -> 2 blocks
  vextracti32x4(T0, A0, 1);
  vextracti32x4(T1, A1, 1);
  vextracti32x4(T2, A2, 1);
  vpaddq(A0, A0, T0, Assembler::AVX_128bit);
  vpaddq(A1, A1, T1, Assembler::AVX_128bit);
  vpaddq(A2, A2, T2, Assembler::AVX_128bit);

  // 2 -> 1 blocks
  vpsrldq(T0, A0, 8, Assembler::AVX_128bit);
  vpsrldq(T1, A1, 8, Assembler::AVX_128bit);
  vpsrldq(T2, A2, 8, Assembler::AVX_128bit);

  // Finish folding and clear second qword
  mov64(t0, 0xfd);
  kmovql(k1, t0);
  evpaddq(A0, k1, A0, T0, false, Assembler::AVX_512bit);
  evpaddq(A1, k1, A1, T1, false, Assembler::AVX_512bit);
  evpaddq(A2, k1, A2, T2, false, Assembler::AVX_512bit);

  // Carry propagation
  vpsrlq(T0, A0, 44, Assembler::AVX_512bit);
  evpandq(A0, A0, Address(polyCP, mask_44), Assembler::AVX_512bit); // Clear top 20 bits
  vpaddq(A1, A1, T0, Assembler::AVX_512bit);
  vpsrlq(T0, A1, 44, Assembler::AVX_512bit);
  evpandq(A1, A1, Address(polyCP, mask_44), Assembler::AVX_512bit); // Clear top 20 bits
  vpaddq(A2, A2, T0, Assembler::AVX_512bit);
  vpsrlq(T0, A2, 42, Assembler::AVX_512bit);
  evpandq(A2, A2, Address(polyCP, mask_42), Assembler::AVX_512bit); // Clear top 22 bits
  vpsllq(T1, T0, 2, Assembler::AVX_512bit);
  vpaddq(T0, T0, T1, Assembler::AVX_512bit);
  vpaddq(A0, A0, T0, Assembler::AVX_512bit);

  // Put together A (accumulator)
  movq(a0, A0);

  movq(t0, A1);
  movq(t1, t0);
  shlq(t1, 44);
  orq(a0, t1);

  shrq(t0, 20);
  movq(a2, A2);
  movq(a1, a2);
  shlq(a1, 24);
  orq(a1, t0);
  shrq(a2, 40);

  // Cleanup
  vpxorq(xmm0, xmm0, xmm0, Assembler::AVX_512bit);
  vpxorq(xmm1, xmm1, xmm1, Assembler::AVX_512bit);
  vpxorq(T0, T0, T0, Assembler::AVX_512bit);
  vpxorq(T1, T1, T1, Assembler::AVX_512bit);
  vpxorq(T2, T2, T2, Assembler::AVX_512bit);
  vpxorq(C0, C0, C0, Assembler::AVX_512bit);
  vpxorq(C1, C1, C1, Assembler::AVX_512bit);
  vpxorq(C2, C2, C2, Assembler::AVX_512bit);
  vpxorq(A0, A0, A0, Assembler::AVX_512bit);
  vpxorq(A1, A1, A1, Assembler::AVX_512bit);
  vpxorq(A2, A2, A2, Assembler::AVX_512bit);
  vpxorq(A3, A3, A3, Assembler::AVX_512bit);
  vpxorq(A4, A4, A4, Assembler::AVX_512bit);
  vpxorq(A5, A5, A5, Assembler::AVX_512bit);
  vpxorq(B0, B0, B0, Assembler::AVX_512bit);
  vpxorq(B1, B1, B1, Assembler::AVX_512bit);
  vpxorq(B2, B2, B2, Assembler::AVX_512bit);
  vpxorq(B3, B3, B3, Assembler::AVX_512bit);
  vpxorq(B4, B4, B4, Assembler::AVX_512bit);
  vpxorq(B5, B5, B5, Assembler::AVX_512bit);
  vpxorq(R0, R0, R0, Assembler::AVX_512bit);
  vpxorq(R1, R1, R1, Assembler::AVX_512bit);
  vpxorq(R2, R2, R2, Assembler::AVX_512bit);
  vpxorq(R1P, R1P, R1P, Assembler::AVX_512bit);
  vpxorq(R2P, R2P, R2P, Assembler::AVX_512bit);
  evmovdquq(Address(rsp, 64*3), A0, Assembler::AVX_512bit);
  evmovdquq(Address(rsp, 64*4), A0, Assembler::AVX_512bit);
  evmovdquq(Address(rsp, 64*5), A0, Assembler::AVX_512bit);
  evmovdquq(Address(rsp, 64*0), A0, Assembler::AVX_512bit);
  evmovdquq(Address(rsp, 64*1), A0, Assembler::AVX_512bit);
  evmovdquq(Address(rsp, 64*2), A0, Assembler::AVX_512bit);
  addq(rsp, 512/8*6); // (powers of R)
}

// This function consumes as many whole 16-byte blocks as available in input
// After execution, input and length will point at remaining (unprocessed) data
// and accumulator will point to the current accumulator value
//
void MacroAssembler::poly1305_process_blocks(Register input, Register length, Register accumulator, Register R)
{
  // Register Map:
  //     input        = rdi;
  //     length       = rbx;
  //     accumulator  = rcx;
  //     R            = r8;

  const Register a0 = rsi;  // [in/out] accumulator bits 63..0
  const Register a1 = r9;   // [in/out] accumulator bits 127..64
  const Register a2 = r10;  // [in/out] accumulator bits 195..128
  const Register r0 = r11;  // R constant bits 63..0
  const Register r1 = r12;  // R constant bits 127..64
  const Register c1 = r8;   // 5*R (upper limb only)

  Label L_process16Loop, L_process16LoopDone;

  // Load R into r1:r0
  poly1305_limbs(R, r0, r1, r1, true);

  // Compute 5*R (Upper limb only)
  movq(c1, r1);
  shrq(c1, 2);
  addq(c1, r1); // c1 = r1 + (r1 >> 2)

  // Load accumulator into a2:a1:a0
  poly1305_limbs(accumulator, a0, a1, a2, false);

  // VECTOR LOOP: Minimum of 256 bytes to run vectorized code
  cmpl(length, 16*16);
  jcc(Assembler::less, L_process16Loop);

  poly1305_process_blocks_avx512(input, length,
                                  a0, a1, a2,
                                  r0, r1, c1);

  // SCALAR LOOP: process one 16-byte message block at a time
  bind(L_process16Loop);
  cmpl(length, 16);
  jcc(Assembler::less, L_process16LoopDone);

  addq(a0, Address(input,0));
  adcq(a1, Address(input,8));
  adcq(a2,1);
  poly1305_multiply_scalar(a0, a1, a2, r0, r1, c1, false);

  subl(length, 16);
  lea(input, Address(input,16));
  jmp(L_process16Loop);
  bind(L_process16LoopDone);

  // Write output
  poly1305_limbs_out(a0, a1, a2, accumulator);
}

#endif // _LP64
