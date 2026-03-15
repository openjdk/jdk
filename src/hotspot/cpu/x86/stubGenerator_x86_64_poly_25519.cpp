/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *  
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 */

#include "macroAssembler_x86.hpp"
#include "stubGenerator_x86_64.hpp"

#define __ _masm->

ATTRIBUTE_ALIGNED(64) constexpr uint64_t MODULUS_P256[] = {
  0x000fffffffffffffULL, 0x00000fffffffffffULL,
  0x0000000000000000ULL, 0x0000001000000000ULL,
  0x0000ffffffff0000ULL, 0x0000000000000000ULL,
  0x0000000000000000ULL, 0x0000000000000000ULL
};
static address modulus_p256(int index = 0) {
  return (address)&MODULUS_P256[index];
}

ATTRIBUTE_ALIGNED(64) constexpr uint64_t P256_MASK52[] = {
  0x000fffffffffffffULL, 0x000fffffffffffffULL,
  0x000fffffffffffffULL, 0x000fffffffffffffULL,
  0xffffffffffffffffULL, 0xffffffffffffffffULL,
  0xffffffffffffffffULL, 0xffffffffffffffffULL,
};
static address p256_mask52() {
  return (address)P256_MASK52;
}

ATTRIBUTE_ALIGNED(64) constexpr uint64_t SHIFT1R[] = {
  0x0000000000000001ULL, 0x0000000000000002ULL,
  0x0000000000000003ULL, 0x0000000000000004ULL,
  0x0000000000000005ULL, 0x0000000000000006ULL,
  0x0000000000000007ULL, 0x0000000000000000ULL,
};
static address shift_1R() {
  return (address)SHIFT1R;
}

ATTRIBUTE_ALIGNED(64) constexpr uint64_t SHIFT1L[] = {
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
 *                                                     | 0| 0| 0|a5|a4|a3|a2|a1|
 *          Acc1 += A *  B                            *|bi|bi|bi|bi|bi|bi|bi|bi|
 *                                               Acc1+=| 0| 0| 0|c5|c4|c3|c2|c1|
 *                                                     +--+--+--+--+--+--+--+--+
 *                                                     | 0| 0| 0|a5|a4|a3|a2|a1|
 *          Acc2 += A *h B                           *h|bi|bi|bi|bi|bi|bi|bi|bi|
 *                                               Acc2+=| 0| 0| 0| d5|d4|d3|d2|d1|
 *                                                     +--+--+--+--+--+--+--+--+
 *          N = replicate(Acc1[0])                     |n0|n0|n0|n0|n0|n0|n0|n0|
 *                                                     +--+--+--+--+--+--+--+--+
 *                                                     +--+--+--+--+--+--+--+--+
 *                                                     | 0| 0| 0|m5|m4|m3|m2|m1|
 *          Acc1 += M *  N                            *|n0|n0|n0|n0|n0|n0|n0|n0|
 *                                               Acc1+=| 0| 0| 0|c5|c4|c3|c2|c1| Note: 52 low bits of c1 == 0 due to Montgomery!
 *                                                     +--+--+--+--+--+--+--+--+
 *                                                     | 0| 0| 0|m5|m4|m3|m2|m1|
 *          Acc2 += M *h N                           *h|n0|n0|n0|n0|n0|n0|n0|n0|
 *                                               Acc2+=| 0| 0| 0|d5|d4|d3|d2|d1|
 *                                                     +--+--+--+--+--+--+--+--+
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
 *
 * At this point the result in Acc1 can overflow by 1 Modulus and needs carry
 * propagation. Subtract one modulus, carry-propagate both results and select
 * (constant-time) the positive number of the two
 *
 * Carry = Acc1[0] >> 52
 * Acc1L = Acc1[0] & mask52
 * Acc1  = Acc1 shift one q element>>
 * Acc1 += Carry
 *
 * Carry = Acc2[0] >> 52
 * Acc2L = Acc2[0] & mask52
 * Acc2  = Acc2 shift one q element>>
 * Acc2 += Carry
 *
 * for col:=1 to 4
 *   Carry = Acc2[col]>>52
 *   Carry = Carry shift one q element<<
 *   Acc2 += Carry
 *
 *   Carry = Acc1[col]>>52
 *   Carry = Carry shift one q element<<
 *   Acc1 += Carry
 * done
 *
 * Acc1 &= mask52
 * Acc2 &= mask52
 * Mask = sign(Acc2)
 * Result = select(Mask ? Acc1 or Acc2)
 */
void multiply_25519_avx512(const Register aLimbs, const Register bLimbs, const Register rLimbs, const Register tmp, MacroAssembler* _masm) {
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
  XMMRegister Carry = xmm13;

  // Constants
  XMMRegister modulus = xmm5;
  XMMRegister shift1L = xmm6;
  XMMRegister shift1R = xmm7;
  XMMRegister Mask52  = xmm8;
  KRegister allLimbs = k1;
  KRegister limb0    = k2;
  KRegister masks[] = {limb0, k3, k4, k5};

  for (int i=0; i<4; i++) {
    __ mov64(t0, 1ULL<<i);
    __ kmovql(masks[i], t0);
  }

  __ mov64(t0, 0x1f);
  __ kmovql(allLimbs, t0);
  __ evmovdqaq(shift1L, allLimbs, ExternalAddress(shift_1L()), false, Assembler::AVX_512bit, rscratch);
  __ evmovdqaq(shift1R, allLimbs, ExternalAddress(shift_1R()), false, Assembler::AVX_512bit, rscratch);
  __ evmovdqaq(Mask52, allLimbs, ExternalAddress(p256_mask52()), false, Assembler::AVX_512bit, rscratch);

  // M = load(*modulus_p256)
  __ evmovdqaq(modulus, allLimbs, ExternalAddress(modulus_p256()), false, Assembler::AVX_512bit, rscratch);

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

      // Combine high/low partial sums Acc1 + Acc2

      // carry = Acc1[0] >> 52
      __ evpsrlq(Carry, limb0, Acc1, 52, true, Assembler::AVX_512bit);

      // Acc2[0] += carry
      __ evpaddq(Acc2, limb0, Carry, Acc2, true, Assembler::AVX_512bit);

      // Acc1 = Acc1 shift one q element >>
      __ evpermq(Acc1, allLimbs, shift1R, Acc1, false, Assembler::AVX_512bit);

      // Acc1 = Acc1 + Acc2
      __ vpaddq(Acc1, Acc1, Acc2, Assembler::AVX_512bit);
  }

  // At this point the result is in Acc1, but needs to be normailized to 52bit
  // limbs (i.e. needs carry propagation) It can also overflow by 1 modulus.
  // Subtract one modulus from Acc1 into Acc2 then carry propagate both
  // simultaneously

  XMMRegister Acc1L = A;
  XMMRegister Acc2L = B;
  __ vpsubq(Acc2, Acc1, modulus, Assembler::AVX_512bit);

  // digit 0 carry out
  // Also split Acc1 and Acc2 into two 256-bit vectors each {Acc1, Acc1L} and
  // {Acc2, Acc2L} to use 256bit operations
  __ evpsraq(Carry, limb0, Acc2, 52, false, Assembler::AVX_256bit);
  __ evpandq(Acc2L, limb0, Acc2, Mask52, false, Assembler::AVX_256bit);
  __ evpermq(Acc2, allLimbs, shift1R, Acc2, false, Assembler::AVX_512bit);
  __ vpaddq(Acc2, Acc2, Carry, Assembler::AVX_256bit);

  __ evpsraq(Carry, limb0, Acc1, 52, false, Assembler::AVX_256bit);
  __ evpandq(Acc1L, limb0, Acc1, Mask52, false, Assembler::AVX_256bit);
  __ evpermq(Acc1, allLimbs, shift1R, Acc1, false, Assembler::AVX_512bit);
  __ vpaddq(Acc1, Acc1, Carry, Assembler::AVX_256bit);

 /* remaining digits carry
  * Note1: Carry register contains just the carry for the particular
  * column (zero-mask the rest) and gets progressively shifted left
  * Note2: 'element shift' with vpermq is more expensive, so using vpalignr when
  * possible. vpalignr shifts 'right' not left, so place the carry appropiately
  *                               +--+--+--+--+    +--+--+--+--+         +--+--+
  * vpalignr(X, X, X, 8):         |x4|x3|x2|x1| >> |x2|x1|x2|x1|         |x1|x2|
  *                               +--+--+--+--+    +--+--+--+--+ >>      +--+--+
  *                                     |          +--+--+--+--+   +--+--+
  *                                     |          |x4|x3|x4|x3|   |x3|x4|
  *                                     |          +--+--+--+--+   +--+--+
  *                                     |                                vv
  *                                     |                          +--+--+--+--+
  *  (x3 and x1 is effectively shifted  +------------------------> |x3|x4|x1|x2|
  *   left; zero-mask everything but one column of interest)       +--+--+--+--+
  */
  for (int i = 1; i<4; i++) {
    __ evpsraq(Carry, masks[i-1], Acc2, 52, false, Assembler::AVX_256bit);
    if (i == 1 || i == 3) {
      __ vpalignr(Carry, Carry, Carry, 8, Assembler::AVX_256bit);
    } else {
      __ vpermq(Carry, Carry, 0b10010011, Assembler::AVX_256bit);
    }
    __ vpaddq(Acc2, Acc2, Carry, Assembler::AVX_256bit);

    __ evpsraq(Carry, masks[i-1], Acc1, 52, false, Assembler::AVX_256bit);
    if (i == 1 || i == 3) {
      __ vpalignr(Carry, Carry, Carry, 8, Assembler::AVX_256bit);
    } else {
      __ vpermq(Carry, Carry, 0b10010011, Assembler::AVX_256bit); //0b-2-1-0-3
    }
    __ vpaddq(Acc1, Acc1, Carry, Assembler::AVX_256bit);
  }

  // Iff Acc2 is negative, then Acc1 contains the result.
  // if Acc2 is negative, upper 12 bits will be set; arithmetic shift by 64 bits
  // generates a mask from Acc2 sign bit
  __ evpsraq(Carry, Acc2, 64, Assembler::AVX_256bit);
  __ vpermq(Carry, Carry, 0b11111111, Assembler::AVX_256bit); //0b-3-3-3-3
  __ evpandq(Acc1, Acc1, Mask52, Assembler::AVX_256bit);
  __ evpandq(Acc2, Acc2, Mask52, Assembler::AVX_256bit);

  // Acc2 = (Acc1 & Mask) | (Acc2 & !Mask)
  __ vpandn(Acc2L, Carry, Acc2L, Assembler::AVX_256bit);
  __ vpternlogq(Acc2L, 0xF8, Carry, Acc1L, Assembler::AVX_256bit); // A | B&C orAandBC
  __ vpandn(Acc2, Carry, Acc2, Assembler::AVX_256bit);
  __ vpternlogq(Acc2, 0xF8, Carry, Acc1, Assembler::AVX_256bit);

  // output to rLimbs (1 + 4 limbs)
  __ movq(Address(rLimbs, 0), Acc2L);
  __ evmovdquq(Address(rLimbs, 8), Acc2, Assembler::AVX_256bit);

  // Cleanup
  // Zero out zmm0-zmm15, higher registers not used by intrinsic.
  __ vzeroall();
}

address StubGenerator::generate_intpoly_mult_25519() {
  __ align(CodeEntryAlignment);
  StubId stub_id = StubId::stubgen_intpoly_mult_25519_id;
  StubCodeMark mark(this, stub_id);
  address start = __ pc();
  __ enter();

  if (VM_Version::supports_avx512ifma() && VM_Version::supports_avx512vlbw()) {
    // Register Map
    const Register aLimbs  = c_rarg0; // rdi | rcx
    const Register bLimbs  = c_rarg1; // rsi | rdx
    const Register rLimbs  = c_rarg2; // rdx | r8
    const Register tmp     = r9;

    multiply_25519_avx512(aLimbs, bLimbs, rLimbs, tmp, _masm);
  }

  __ leave();
  __ ret(0);
  return start;
}
