/*
 * Copyright (c) 2024, 2025, Intel Corporation. All rights reserved.
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

ATTRIBUTE_ALIGNED(64) constexpr uint64_t MASKL5[] = {
  0xFFFFFFFFFFFFFFFFULL, 0xFFFFFFFFFFFFFFFFULL,
  0xFFFFFFFFFFFFFFFFULL, 0x0000000000000000ULL,
};
static address mask_limb5() {
  return (address)MASKL5;
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
  XMMRegister Carry = xmm13;

  // // Constants
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

/**
 * Unrolled Word-by-Word Montgomery Multiplication
 * r = a * b * 2^-260 (mod P)
 *
 * Use vpmadd52{l,h}uq multiply for upper four limbs and use
 * scalar mulq for the lowest limb.
 *
 * One has to be careful with mulq vs vpmadd52 'crossovers'; mulq high/low
 * is split as 40:64 bits vs 52:52 in the vector version. Shifts are required
 * to line up values before addition (see following ascii art)
 *
 * Pseudocode:
 *
 *                                                     +--+--+--+--+  +--+
 *   M = load(*modulus_p256)                           |m5|m4|m3|m2|  |m1|
 *                                                     +--+--+--+--+  +--+
 *   A = load(*aLimbs)                                 |a5|a4|a3|a2|  |a1|
 *                                                     +--+--+--+--+  +--+
 *   Acc1 = 0                                          | 0| 0| 0| 0|  | 0|
 *                                                     +--+--+--+--+  +--+
 *      ---- for i = 0 to 4
 *                                                     +--+--+--+--+  +--+
 *          Acc2 = 0                                   | 0| 0| 0| 0|  | 0|
 *                                                     +--+--+--+--+  +--+
 *          B = replicate(bLimbs[i])                   |bi|bi|bi|bi|  |bi|
 *                                                     +--+--+--+--+  +--+
 *                                                     +--+--+--+--+  +--+
 *                                                     |a5|a4|a3|a2|  |a1|
 *          Acc1 += A *  B                            *|bi|bi|bi|bi|  |bi|
 *                                               Acc1+=|c5|c4|c3|c2|  |c1|
 *                                                     +--+--+--+--+  +--+
 *                                                     |a5|a4|a3|a2|  |a1|
 *          Acc2 += A *h B                           *h|bi|bi|bi|bi|  |bi|
 *                                               Acc2+=|d5|d4|d3|d2|  |d1|
 *                                                     +--+--+--+--+  +--+
 *          N = replicate(Acc1[0])                     |n0|n0|n0|n0|  |n0|
 *                                                     +--+--+--+--+  +--+
 *                                                     +--+--+--+--+  +--+
 *                                                     |m5|m4|m3|m2|  |m1|
 *          Acc1 += M *  N                            *|n0|n0|n0|n0|  |n0|
 *                                               Acc1+=|c5|c4|c3|c2|  |c1| Note: 52 low bits of c1 == 0 due to Montgomery!
 *                                                     +--+--+--+--+  +--+
 *                                                     |m5|m4|m3|m2|  |m1|
 *          Acc2 += M *h N                           *h|n0|n0|n0|n0|  |n0|
 *                                               Acc2+=|d5|d4|d3|d2|  |d1|
 *                                                     +--+--+--+--+  +--+
 *          // Combine high/low partial sums Acc1 + Acc2
 *                                                                    +--+
 *          carry = Acc1[0] >> 52                                     |c1|
 *                                                                    +--+
 *          Acc2[0] += carry                                          |d1|
 *                                                                    +--+
 *                                                     +--+--+--+--+  +--+
 *          Acc1 = Acc1 shift one q element>>          | 0|c5|c4|c3|  |c2|
 *                                                    +|d5|d4|d3|d2|  |d1|
 *          Acc1 = Acc1 + Acc2                   Acc1+=|c5|c4|c3|c2|  |c1|
 *                                                     +--+--+--+--+  +--+
 *      ---- done
 *                                                     +--+--+--+--+  +--+
 *   Acc2 = Acc1 - M                                   |d5|d4|d3|d2|  |d1|
 *                                                     +--+--+--+--+  +--+
 *   Carry propagate Acc2
 *   Carry propagate Acc1
 *   Mask = sign(Acc2)
 *   Result = select(Mask ? Acc1 or Acc2)
 *
 * Acc1 can overflow by one modulus (hence Acc2); Either Acc1 or Acc2 contain
 * the correct result. However, they both need carry propagation (i.e. normalize
 * limbs down to 52 bits each).
 *
 * Carry propagation would require relatively expensive vector lane operations,
 * so instead dump to memory and read as scalar registers
 *
 * Note: the order of reduce-then-propagate vs propagate-then-reduce is different
 * in Java
 */
void montgomeryMultiplyAVX2(const Register aLimbs, const Register bLimbs, const Register rLimbs,
  const Register tmp_rax, const Register tmp_rdx, const Register tmp1, const Register tmp2,
  const Register tmp3, const Register tmp4, const Register tmp5, const Register tmp6,
  const Register tmp7, MacroAssembler* _masm) {
  Register rscratch = tmp1;

  // Inputs
  Register    a = tmp1;
  XMMRegister A = xmm0;
  XMMRegister B = xmm1;

  // Intermediates
  Register    acc1  = tmp2;
  XMMRegister Acc1  = xmm3;
  Register    acc2  = tmp3;
  XMMRegister Acc2  = xmm4;
  XMMRegister N     = xmm5;
  XMMRegister Carry = xmm6;

  // Constants
  Register    modulus   = tmp4;
  XMMRegister Modulus   = xmm7;
  Register    mask52    = tmp5;
  XMMRegister Mask52    = xmm8;
  XMMRegister MaskLimb5 = xmm9;
  XMMRegister Zero      = xmm10;

  __ mov64(mask52, P256_MASK52[0]);
  __ movq(Mask52, mask52);
  __ vpbroadcastq(Mask52, Mask52, Assembler::AVX_256bit);
  __ vmovdqa(MaskLimb5, ExternalAddress(mask_limb5()), Assembler::AVX_256bit, rscratch);
  __ vpxor(Zero, Zero, Zero, Assembler::AVX_256bit);

  // M = load(*modulus_p256)
  __ movq(modulus, mask52);
  __ vmovdqu(Modulus, ExternalAddress(modulus_p256(1)), Assembler::AVX_256bit, rscratch);

  // A = load(*aLimbs);
  __ movq(a, Address(aLimbs, 0));
  __ vmovdqu(A, Address(aLimbs, 8)); //Assembler::AVX_256bit

  // Acc1 = 0
  __ vpxor(Acc1, Acc1, Acc1, Assembler::AVX_256bit);
  for (int i = 0; i< 5; i++) {
      // Acc2 = 0
      __ vpxor(Acc2, Acc2, Acc2, Assembler::AVX_256bit);

      // B = replicate(bLimbs[i])
      __ movq(tmp_rax, Address(bLimbs, i*8)); //(b==rax)
      __ vpbroadcastq(B, Address(bLimbs, i*8), Assembler::AVX_256bit);

      // Acc1 += A * B
      // Acc2 += A *h B
      __ mulq(a); // rdx:rax = a*rax
      if (i == 0) {
        __ movq(acc1, tmp_rax);
        __ movq(acc2, tmp_rdx);
      } else {
        // Careful with limb size/carries; from mulq, tmp_rax uses full 64 bits
        __ xorq(acc2, acc2);
        __ addq(acc1, tmp_rax);
        __ adcq(acc2, tmp_rdx);
      }
      __ vpmadd52luq(Acc1, A, B, Assembler::AVX_256bit);
      __ vpmadd52huq(Acc2, A, B, Assembler::AVX_256bit);

      // N = replicate(Acc1[0])
      if  (i != 0) {
        __ movq(tmp_rax, acc1); // (n==rax)
      }
      __ andq(tmp_rax, mask52);
      __ movq(N, acc1); // masking implicit in vpmadd52
      __ vpbroadcastq(N, N, Assembler::AVX_256bit);

      // Acc1 += M *  N
      __ mulq(modulus); // rdx:rax = modulus*rax
      __ vpmadd52luq(Acc1, Modulus, N, Assembler::AVX_256bit);
      __ addq(acc1, tmp_rax); //carry flag set!

      // Acc2 += M *h N
      __ adcq(acc2, tmp_rdx);
      __ vpmadd52huq(Acc2, Modulus, N, Assembler::AVX_256bit);

      // Combine high/low partial sums Acc1 + Acc2

      // carry = Acc1[0] >> 52
      __ shrq(acc1, 52); // low 52 of acc1 ignored, is zero, because Montgomery

      // Acc2[0] += carry
      __ shlq(acc2, 12);
      __ addq(acc2, acc1);

      // Acc1 = Acc1 shift one q element >>
      __ movq(acc1, Acc1);
      __ vpermq(Acc1, Acc1, 0b11111001, Assembler::AVX_256bit);
      __ vpand(Acc1, Acc1, MaskLimb5, Assembler::AVX_256bit);

      // Acc1 = Acc1 + Acc2
      __ addq(acc1, acc2);
      __ vpaddq(Acc1, Acc1, Acc2, Assembler::AVX_256bit);
  }

  __ movq(acc2, acc1);
  __ subq(acc2, modulus);
  __ vpsubq(Acc2, Acc1, Modulus, Assembler::AVX_256bit);
  __ vmovdqa(Address(rsp, 0), Acc2); //Assembler::AVX_256bit

  // Carry propagate the subtraction result Acc2 first (since the last carry is
  // used to select result). Careful, following registers overlap:
  // acc1  = tmp2; acc2  = tmp3; mask52 = tmp5
  // Note that Acc2 limbs are signed (i.e. result of a subtract with modulus)
  // i.e. using signed shift is needed for correctness
  Register limb[] = {acc2, tmp1, tmp4, tmp_rdx, tmp6};
  Register carry = tmp_rax;
  for (int i = 0; i<5; i++) {
    if (i > 0) {
      __ movq(limb[i], Address(rsp, -8+i*8));
      __ addq(limb[i], carry);
    }
    __ movq(carry, limb[i]);
    if (i==4) break;
    __ sarq(carry, 52);
  }
  __ sarq(carry, 63);
  __ notq(carry); //select
  Register select = carry;
  carry = tmp7;

  // Now carry propagate the multiply result and (constant-time) select correct
  // output digit
  Register digit = acc1;
  __ vmovdqa(Address(rsp, 0), Acc1); //Assembler::AVX_256bit

  for (int i = 0; i<5; i++) {
    if (i>0) {
      __ movq(digit, Address(rsp, -8+i*8));
      __ addq(digit, carry);
    }
    __ movq(carry, digit);
    __ sarq(carry, 52);

    // long dummyLimbs = maskValue & (a[i] ^ b[i]);
    // a[i] = dummyLimbs ^ a[i];
    __ xorq(limb[i], digit);
    __ andq(limb[i], select);
    __ xorq(digit, limb[i]);

    __ andq(digit, mask52);
    __ movq(Address(rLimbs, i*8), digit);
  }

  // Cleanup
  // Zero out ymm0-ymm15.
  __ vzeroall();
  __ vpxor(Acc1, Acc1, Acc1, Assembler::AVX_256bit);
  __ vmovdqa(Address(rsp, 0), Acc1); //Assembler::AVX_256bit
}

address StubGenerator::generate_intpoly_montgomeryMult_P256() {
  __ align(CodeEntryAlignment);
  StubId stub_id = StubId::stubgen_intpoly_montgomeryMult_P256_id;
  StubCodeMark mark(this, stub_id);
  address start = __ pc();
  __ enter();

  if (VM_Version::supports_avx512ifma() && VM_Version::supports_avx512vlbw()) {
    // Register Map
    const Register aLimbs  = c_rarg0; // rdi | rcx
    const Register bLimbs  = c_rarg1; // rsi | rdx
    const Register rLimbs  = c_rarg2; // rdx | r8
    const Register tmp     = r9;

    montgomeryMultiply(aLimbs, bLimbs, rLimbs, tmp, _masm);
  } else {
    assert(VM_Version::supports_avxifma(), "Require AVX_IFMA support");
    __ push_ppx(r12);
    __ push_ppx(r13);
    __ push_ppx(r14);
    #ifdef _WIN64
    __ push_ppx(rsi);
    __ push_ppx(rdi);
    #endif
    __ push_ppx(rbp);
    __ movq(rbp, rsp);
  __ andq(rsp, -32);
  __ subptr(rsp, 32);

    // Register Map
    const Register aLimbs  = c_rarg0; // c_rarg0: rdi | rcx
    const Register bLimbs  = rsi;     // c_rarg1: rsi | rdx
    const Register rLimbs  = r8;      // c_rarg2: rdx | r8
    const Register tmp1    = r9;
    const Register tmp2    = r10;
    const Register tmp3    = r11;
    const Register tmp4    = r12;
    const Register tmp5    = r13;
    const Register tmp6    = r14;
    #ifdef _WIN64
    const Register tmp7    = rdi;
    __ movq(bLimbs, c_rarg1); // free-up rdx
    #else
    const Register tmp7    = rcx;
    __ movq(rLimbs, c_rarg2); // free-up rdx
    #endif

    montgomeryMultiplyAVX2(aLimbs, bLimbs, rLimbs, rax, rdx,
                           tmp1, tmp2, tmp3, tmp4, tmp5, tmp6, tmp7, _masm);

    __ movq(rsp, rbp);
    __ pop_ppx(rbp);
    #ifdef _WIN64
    __ pop_ppx(rdi);
    __ pop_ppx(rsi);
    #endif
    __ pop_ppx(r14);
    __ pop_ppx(r13);
    __ pop_ppx(r12);
  }

  __ leave();
  __ ret(0);
  return start;
}

// A = B if select
// Must be:
//  - constant time (i.e. no branches)
//  - no-side channel (i.e. all memory must always be accessed, and in same order)
void assign_avx(Register aBase, Register bBase, int offset, XMMRegister select, XMMRegister tmp, XMMRegister aTmp, int vector_len, MacroAssembler* _masm) {
  if (vector_len == Assembler::AVX_512bit && UseAVX < 3) {
    assign_avx(aBase, bBase, offset,      select, tmp, aTmp, Assembler::AVX_256bit, _masm);
    assign_avx(aBase, bBase, offset + 32, select, tmp, aTmp, Assembler::AVX_256bit, _masm);
    return;
  }

  Address aAddr = Address(aBase, offset);
  Address bAddr = Address(bBase, offset);

  // Original java:
  // long dummyLimbs = maskValue & (a[i] ^ b[i]);
  // a[i] = dummyLimbs ^ a[i];
  __ vmovdqu(tmp, aAddr, vector_len);
  __ vmovdqu(aTmp, tmp, vector_len);
  __ vpxor(tmp, tmp, bAddr, vector_len);
  __ vpand(tmp, tmp, select, vector_len);
  __ vpxor(tmp, tmp, aTmp, vector_len);
  __ vmovdqu(aAddr, tmp, vector_len);
}

void assign_scalar(Register aBase, Register bBase, int offset, Register select, Register tmp, MacroAssembler* _masm) {
  // Original java:
  // long dummyLimbs = maskValue & (a[i] ^ b[i]);
  // a[i] = dummyLimbs ^ a[i];

  Address aAddr = Address(aBase, offset);
  Address bAddr = Address(bBase, offset);

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
  StubId stub_id = StubId::stubgen_intpoly_assign_id;
  StubCodeMark mark(this, stub_id);
  address start = __ pc();
  __ enter();

  // Inputs
  const Register set     = c_rarg0;
  const Register aLimbs  = c_rarg1;
  const Register bLimbs  = c_rarg2;
  const Register length  = c_rarg3;
  XMMRegister A = xmm0;
  XMMRegister B = xmm1;
  XMMRegister select = xmm2;

  Register tmp = r9;
  Label L_Length5, L_Length10, L_Length14, L_Length16, L_Length19, L_DefaultLoop, L_Done;

  __ negq(set);
  if (UseAVX > 2) {
    __ evpbroadcastq(select, set, Assembler::AVX_512bit);
  } else {
    __ movq(select, set);
    __ vpbroadcastq(select, select, Assembler::AVX_256bit);
  }

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
  assign_scalar(aLimbs, bLimbs, 0, set, tmp, _masm);
  __ subl(length, 1);
  __ lea(aLimbs, Address(aLimbs,8));
  __ lea(bLimbs, Address(bLimbs,8));
  __ cmpl(length, 0);
  __ jcc(Assembler::greater, L_DefaultLoop);
  __ jmp(L_Done);

  __ bind(L_Length5); // 1 + 4
  assign_scalar(aLimbs, bLimbs, 0, set, tmp, _masm);
  assign_avx   (aLimbs, bLimbs, 8, select, A, B, Assembler::AVX_256bit, _masm);
  __ jmp(L_Done);

  __ bind(L_Length10); // 2 + 8
  assign_avx(aLimbs, bLimbs,  0, select, A, B, Assembler::AVX_128bit, _masm);
  assign_avx(aLimbs, bLimbs, 16, select, A, B, Assembler::AVX_512bit, _masm);
  __ jmp(L_Done);

  __ bind(L_Length14); // 2 + 4 + 8
  assign_avx(aLimbs, bLimbs,   0, select, A, B, Assembler::AVX_128bit, _masm);
  assign_avx(aLimbs, bLimbs,  16, select, A, B, Assembler::AVX_256bit, _masm);
  assign_avx(aLimbs, bLimbs,  48, select, A, B, Assembler::AVX_512bit, _masm);
  __ jmp(L_Done);

  __ bind(L_Length16); // 8 + 8
  assign_avx(aLimbs, bLimbs,   0, select, A, B, Assembler::AVX_512bit, _masm);
  assign_avx(aLimbs, bLimbs,  64, select, A, B, Assembler::AVX_512bit, _masm);
  __ jmp(L_Done);

  __ bind(L_Length19); // 1 + 2 + 8 + 8
  assign_scalar(aLimbs, bLimbs,  0, set, tmp, _masm);
  assign_avx   (aLimbs, bLimbs,  8, select, A, B, Assembler::AVX_128bit, _masm);
  assign_avx   (aLimbs, bLimbs, 24, select, A, B, Assembler::AVX_512bit, _masm);
  assign_avx   (aLimbs, bLimbs, 88, select, A, B, Assembler::AVX_512bit, _masm);

  __ bind(L_Done);
  __ leave();
  __ ret(0);
  return start;
}
