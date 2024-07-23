/*
 * Copyright (c) 2024, Intel Corporation. All rights reserved.
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
#include "macroAssembler_x86.hpp"
#include "stubGenerator_x86_64.hpp"

#define __ _masm->

ATTRIBUTE_ALIGNED(64) uint64_t MODULUS_P256[] = {
  0x000fffffffffffffULL, 0x00000fffffffffffULL,
  0x0000000000000000ULL, 0x0000001000000000ULL,
  0x0000ffffffff0000ULL, 0x0000000000000000ULL,
  0x0000000000000000ULL, 0x0000000000000000ULL
};
static address modulus_p256() {
  return (address)MODULUS_P256;
}

ATTRIBUTE_ALIGNED(64) uint64_t P256_MASK52[] = {
  0x000fffffffffffffULL, 0x000fffffffffffffULL,
  0x000fffffffffffffULL, 0x000fffffffffffffULL,
  0xffffffffffffffffULL, 0xffffffffffffffffULL,
  0xffffffffffffffffULL, 0xffffffffffffffffULL,
};
static address p256_mask52() {
  return (address)P256_MASK52;
}

ATTRIBUTE_ALIGNED(64) uint64_t SHIFT1R[] = {
  0x0000000000000001ULL, 0x0000000000000002ULL,
  0x0000000000000003ULL, 0x0000000000000004ULL,
  0x0000000000000005ULL, 0x0000000000000006ULL,
  0x0000000000000007ULL, 0x0000000000000000ULL,
};
static address shift_1R() {
  return (address)SHIFT1R;
}

ATTRIBUTE_ALIGNED(64) uint64_t SHIFT1L[] = {
  0x0000000000000007ULL, 0x0000000000000000ULL,
  0x0000000000000001ULL, 0x0000000000000002ULL,
  0x0000000000000003ULL, 0x0000000000000004ULL,
  0x0000000000000005ULL, 0x0000000000000006ULL,
};
static address shift_1L() {
  return (address)SHIFT1L;
}

/**
 * Unrolled Word-by-Word Montgomery Multiplication
 * r = a * b * 2^-260 (mod P)
 *
 * Reference [1]: Shay Gueron and Vlad Krasnov
 *    "Fast Prime Field Elliptic Curve Cryptography with 256 Bit Primes"
 *    See Figure 5. "Algorithm 2: Word-by-Word Montgomery Multiplication for a Montgomery
 *    Friendly modulus p". Note: Step 6. Skipped; Instead use numAdds to reuse existing overflow
 *    logic.
 *
 * Pseudocode:
 *
 *                                                     +--+--+--+--+--+--+--+--+
 *   M = load(*modulus_p256)                           | 0| 0| 0|m5|m4|m3|m2|m1|
 *                                                     +--+--+--+--+--+--+--+--+
 *   A = load(*aLimbs)                                 | 0| 0| 0|a5|a4|a3|a2|a1|
 *                                                     +--+--+--+--+--+--+--+--+
 *   Acc1 = 0                                          | 0| 0| 0| 0| 0| 0| 0| 0|
 *                                                     +--+--+--+--+--+--+--+--+
 *      ---- for i = 0 to 4
 *                                                     +--+--+--+--+--+--+--+--+
 *          Acc2 = 0                                   | 0| 0| 0| 0| 0| 0| 0| 0|
 *                                                     +--+--+--+--+--+--+--+--+
 *          B = replicate(bLimbs[i])                   |bi|bi|bi|bi|bi|bi|bi|bi|
 *                                                     +--+--+--+--+--+--+--+--+
 *                                                     +--+--+--+--+--+--+--+--+
 *                                               Acc1+=| 0| 0| 0|c5|c4|c3|c2|c1|
 *                                                    *| 0| 0| 0|a5|a4|a3|a2|a1|
 *          Acc1 += A *  B                             |bi|bi|bi|bi|bi|bi|bi|bi|
 *                                                     +--+--+--+--+--+--+--+--+
 *                                               Acc2+=| 0| 0| 0| 0| 0| 0| 0| 0|
 *                                                   *h| 0| 0| 0|a5|a4|a3|a2|a1|
 *          Acc2 += A *h B                             |bi|bi|bi|bi|bi|bi|bi|bi|
 *                                                     +--+--+--+--+--+--+--+--+
 *          N = replicate(Acc1[0])                     |n0|n0|n0|n0|n0|n0|n0|n0|
 *                                                     +--+--+--+--+--+--+--+--+
 *                                                     +--+--+--+--+--+--+--+--+
 *                                               Acc1+=| 0| 0| 0|c5|c4|c3|c2|c1|
 *                                                    *| 0| 0| 0|m5|m4|m3|m2|m1|
 *          Acc1 += M *  N                             |n0|n0|n0|n0|n0|n0|n0|n0| Note: 52 low bits of Acc1[0] == 0 due to Montgomery!
 *                                                     +--+--+--+--+--+--+--+--+
 *                                               Acc2+=| 0| 0| 0|d5|d4|d3|d2|d1|
 *                                                   *h| 0| 0| 0|m5|m4|m3|m2|m1|
 *          Acc2 += M *h N                             |n0|n0|n0|n0|n0|n0|n0|n0|
 *                                                     +--+--+--+--+--+--+--+--+
 *          if (i == 4) break;
 *          // Combine high/low partial sums Acc1 + Acc2
 *                                                     +--+--+--+--+--+--+--+--+
 *          carry = Acc1[0] >> 52                      | 0| 0| 0| 0| 0| 0| 0|c1|
 *                                                     +--+--+--+--+--+--+--+--+
 *          Acc2[0] += carry
 *                                                     +--+--+--+--+--+--+--+--+
 *          Acc1 = Acc1 shift one q element>>          | 0| 0| 0| 0|c5|c4|c3|c2|
 *                                                     +--+--+--+--+--+--+--+--+
 *          Acc1 = Acc1 + Acc2
 *      ---- done
 *   // Last Carry round: Combine high/low partial sums Acc1<high_bits> + Acc1 + Acc2
 *   carry = Acc1 >> 52
 *   Acc1 = Acc1 shift one q element >>
 *   Acc1  = mask52(Acc1)
 *   Acc2  += carry
 *   Acc1 = Acc1 + Acc2
 *   output to rLimbs
 */
void montgomeryMultiply(const Register aLimbs, const Register bLimbs, const Register rLimbs, const Register tmp, MacroAssembler* _masm) {
  Register t0 = tmp;
  Register rscratch = tmp;

  // Inputs
  XMMRegister A = xmm0;
  XMMRegister B = xmm1;
  XMMRegister T = xmm2;

  // Intermediates
  XMMRegister Acc1 = xmm10;
  XMMRegister Acc2 = xmm11;
  XMMRegister N    = xmm12;
  XMMRegister carry = xmm13;

  // // Constants
  XMMRegister modulus = xmm20;
  XMMRegister shift1L = xmm21;
  XMMRegister shift1R = xmm22;
  XMMRegister mask52  = xmm23;
  KRegister limb0    = k1;
  KRegister allLimbs = k2;

  __ mov64(t0, 0x1);
  __ kmovql(limb0, t0);
  __ mov64(t0, 0x1f);
  __ kmovql(allLimbs, t0);
  __ evmovdquq(shift1L, allLimbs, ExternalAddress(shift_1L()), false, Assembler::AVX_512bit, rscratch);
  __ evmovdquq(shift1R, allLimbs, ExternalAddress(shift_1R()), false, Assembler::AVX_512bit, rscratch);
  __ evmovdquq(mask52, allLimbs, ExternalAddress(p256_mask52()), false, Assembler::AVX_512bit, rscratch);

  // M = load(*modulus_p256)
  __ evmovdquq(modulus, allLimbs, ExternalAddress(modulus_p256()), false, Assembler::AVX_512bit, rscratch);

  // A = load(*aLimbs);  masked evmovdquq() can be slow. Instead load full 256bit, and compbine with 64bit
  __ evmovdquq(A, Address(aLimbs, 8), Assembler::AVX_256bit);
  __ evpermq(A, allLimbs, shift1L, A, false, Assembler::AVX_512bit);
  __ movq(T, Address(aLimbs, 0));
  __ evporq(A, A, T, Assembler::AVX_512bit);

  // Acc1 = 0
  __ vpxorq(Acc1, Acc1, Acc1, Assembler::AVX_512bit);
  for (int i = 0; i< 5; i++) {
      // Acc2 = 0
      __ vpxorq(Acc2, Acc2, Acc2, Assembler::AVX_512bit);

      // B = replicate(bLimbs[i])
      __ vpbroadcastq(B, Address(bLimbs, i*8), Assembler::AVX_512bit);

      // Acc1 += A * B
      __ evpmadd52luq(Acc1, A, B, Assembler::AVX_512bit);

      // Acc2 += A *h B
      __ evpmadd52huq(Acc2, A, B, Assembler::AVX_512bit);

      // N = replicate(Acc1[0])
      __ vpbroadcastq(N, Acc1, Assembler::AVX_512bit);

      // Acc1 += M *  N
      __ evpmadd52luq(Acc1, modulus, N, Assembler::AVX_512bit);

      // Acc2 += M *h N
      __ evpmadd52huq(Acc2, modulus, N, Assembler::AVX_512bit);

      if (i == 4) break;

      // Combine high/low partial sums Acc1 + Acc2

      // carry = Acc1[0] >> 52
      __ evpsrlq(carry, limb0, Acc1, 52, true, Assembler::AVX_512bit);

      // Acc2[0] += carry
      __ evpaddq(Acc2, limb0, carry, Acc2, true, Assembler::AVX_512bit);

      // Acc1 = Acc1 shift one q element >>
      __ evpermq(Acc1, allLimbs, shift1R, Acc1, false, Assembler::AVX_512bit);

      // Acc1 = Acc1 + Acc2
      __ vpaddq(Acc1, Acc1, Acc2, Assembler::AVX_512bit);
  }

  // Last Carry round: Combine high/low partial sums Acc1<high_bits> + Acc1 + Acc2
  // carry = Acc1 >> 52
  __ evpsrlq(carry, allLimbs, Acc1, 52, true, Assembler::AVX_512bit);

  // Acc1 = Acc1 shift one q element >>
  __ evpermq(Acc1, allLimbs, shift1R, Acc1, false, Assembler::AVX_512bit);

  // Acc1  = mask52(Acc1)
  __ evpandq(Acc1, Acc1, mask52, Assembler::AVX_512bit); // Clear top 12 bits

  // Acc2 += carry
  __ evpaddq(Acc2, allLimbs, carry, Acc2, true, Assembler::AVX_512bit);

  // Acc1 = Acc1 + Acc2
  __ vpaddq(Acc1, Acc1, Acc2, Assembler::AVX_512bit);

  // output to rLimbs (1 + 4 limbs)
  __ movq(Address(rLimbs, 0), Acc1);
  __ evpermq(Acc1, k0, shift1R, Acc1, true, Assembler::AVX_512bit);
  __ evmovdquq(Address(rLimbs, 8), k0, Acc1, true, Assembler::AVX_256bit);
}

address StubGenerator::generate_intpoly_montgomeryMult_P256() {
  __ align(CodeEntryAlignment);
  StubCodeMark mark(this, "StubRoutines", "intpoly_montgomeryMult_P256");
  address start = __ pc();
  __ enter();

  // Register Map
  const Register aLimbs  = c_rarg0; // rdi | rcx
  const Register bLimbs  = c_rarg1; // rsi | rdx
  const Register rLimbs  = c_rarg2; // rdx | r8
  const Register tmp     = r9;

  montgomeryMultiply(aLimbs, bLimbs, rLimbs, tmp, _masm);

  __ leave();
  __ ret(0);
  return start;
}

// A = B if select
// Must be:
//  - constant time (i.e. no branches)
//  - no-side channel (i.e. all memory must always be accessed, and in same order)
void assign_avx(XMMRegister A, Address aAddr, XMMRegister B, Address bAddr, KRegister select, int vector_len, MacroAssembler* _masm) {
  __ evmovdquq(A, aAddr, vector_len);
  __ evmovdquq(B, bAddr, vector_len);
  __ evmovdquq(A, select, B, true, vector_len);
  __ evmovdquq(aAddr, A, vector_len);
}

void assign_scalar(Address aAddr, Address bAddr, Register select, Register tmp, MacroAssembler* _masm) {
  // Original java:
  // long dummyLimbs = maskValue & (a[i] ^ b[i]);
  // a[i] = dummyLimbs ^ a[i];

  __ movq(tmp, aAddr);
  __ xorq(tmp, bAddr);
  __ andq(tmp, select);
  __ xorq(aAddr, tmp);
}

address StubGenerator::generate_intpoly_assign() {
  // KNOWN Lengths:
  //   MontgomeryIntPolynP256:  5 = 4 + 1
  //   IntegerPolynomial1305:   5 = 4 + 1
  //   IntegerPolynomial25519: 10 = 8 + 2
  //   IntegerPolynomialP256:  10 = 8 + 2
  //   Curve25519OrderField:   10 = 8 + 2
  //   Curve25519OrderField:   10 = 8 + 2
  //   P256OrderField:         10 = 8 + 2
  //   IntegerPolynomialP384:  14 = 8 + 4 + 2
  //   P384OrderField:         14 = 8 + 4 + 2
  //   IntegerPolynomial448:   16 = 8 + 8
  //   Curve448OrderField:     16 = 8 + 8
  //   Curve448OrderField:     16 = 8 + 8
  //   IntegerPolynomialP521:  19 = 8 + 8 + 2 + 1
  //   P521OrderField:         19 = 8 + 8 + 2 + 1
  // Special Cases 5, 10, 14, 16, 19

  __ align(CodeEntryAlignment);
  StubCodeMark mark(this, "StubRoutines", "intpoly_assign");
  address start = __ pc();
  __ enter();

  // Inputs
  const Register set     = c_rarg0;
  const Register aLimbs  = c_rarg1;
  const Register bLimbs  = c_rarg2;
  const Register length  = c_rarg3;
  XMMRegister A = xmm0;
  XMMRegister B = xmm1;

  Register tmp = r9;
  KRegister select = k1;
  Label L_Length5, L_Length10, L_Length14, L_Length16, L_Length19, L_DefaultLoop, L_Done;

  __ negq(set);
  __ kmovql(select, set);

  // NOTE! Crypto code cannot branch on user input. However; allowed to branch on number of limbs;
  // Number of limbs is a constant in each IntegerPolynomial (i.e. this side-channel branch leaks
  //   number of limbs which is not a secret)
  __ cmpl(length, 5);
  __ jcc(Assembler::equal, L_Length5);
  __ cmpl(length, 10);
  __ jcc(Assembler::equal, L_Length10);
  __ cmpl(length, 14);
  __ jcc(Assembler::equal, L_Length14);
  __ cmpl(length, 16);
  __ jcc(Assembler::equal, L_Length16);
  __ cmpl(length, 19);
  __ jcc(Assembler::equal, L_Length19);

  // Default copy loop (UNLIKELY)
  __ cmpl(length, 0);
  __ jcc(Assembler::lessEqual, L_Done);
  __ bind(L_DefaultLoop);
  assign_scalar(Address(aLimbs, 0), Address(bLimbs, 0), set, tmp, _masm);
  __ subl(length, 1);
  __ lea(aLimbs, Address(aLimbs,8));
  __ lea(bLimbs, Address(bLimbs,8));
  __ cmpl(length, 0);
  __ jcc(Assembler::greater, L_DefaultLoop);
  __ jmp(L_Done);

  __ bind(L_Length5); // 1 + 4
  assign_scalar(Address(aLimbs, 0), Address(bLimbs, 0), set, tmp, _masm);
  assign_avx(A, Address(aLimbs, 8), B, Address(bLimbs, 8), select, Assembler::AVX_256bit, _masm);
  __ jmp(L_Done);

  __ bind(L_Length10); // 2 + 8
  assign_avx(A, Address(aLimbs, 0),  B, Address(bLimbs, 0),  select, Assembler::AVX_128bit, _masm);
  assign_avx(A, Address(aLimbs, 16), B, Address(bLimbs, 16), select, Assembler::AVX_512bit, _masm);
  __ jmp(L_Done);

  __ bind(L_Length14); // 2 + 4 + 8
  assign_avx(A, Address(aLimbs, 0),  B, Address(bLimbs, 0),  select, Assembler::AVX_128bit, _masm);
  assign_avx(A, Address(aLimbs, 16), B, Address(bLimbs, 16), select, Assembler::AVX_256bit, _masm);
  assign_avx(A, Address(aLimbs, 48), B, Address(bLimbs, 48), select, Assembler::AVX_512bit, _masm);
  __ jmp(L_Done);

  __ bind(L_Length16); // 8 + 8
  assign_avx(A, Address(aLimbs, 0),  B, Address(bLimbs, 0),  select, Assembler::AVX_512bit, _masm);
  assign_avx(A, Address(aLimbs, 64), B, Address(bLimbs, 64), select, Assembler::AVX_512bit, _masm);
  __ jmp(L_Done);

  __ bind(L_Length19); // 1 + 2 + 8 + 8
  assign_scalar(Address(aLimbs, 0), Address(bLimbs, 0), set, tmp, _masm);
  assign_avx(A, Address(aLimbs, 8),  B, Address(bLimbs, 8),  select, Assembler::AVX_128bit, _masm);
  assign_avx(A, Address(aLimbs, 24), B, Address(bLimbs, 24), select, Assembler::AVX_512bit, _masm);
  assign_avx(A, Address(aLimbs, 88), B, Address(bLimbs, 88), select, Assembler::AVX_512bit, _masm);

  __ bind(L_Done);
  __ leave();
  __ ret(0);
  return start;
}
