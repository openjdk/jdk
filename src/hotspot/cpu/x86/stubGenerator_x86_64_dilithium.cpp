/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2025, Intel Corporation. All rights reserved.
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
#include "runtime/stubRoutines.hpp"
#include "macroAssembler_x86.hpp"
#include "stubGenerator_x86_64.hpp"

#define __ _masm->

#ifdef PRODUCT
#define BLOCK_COMMENT(str) /* nothing */
#else
#define BLOCK_COMMENT(str) __ block_comment(str)
#endif // PRODUCT

#define BIND(label) bind(label); BLOCK_COMMENT(#label ":")

// Constants
//
ATTRIBUTE_ALIGNED(64) static const uint32_t dilithiumAvx512Consts[] = {
    58728449, // montQInvModR
    8380417,  // dilithium_q
    2365951,  // montRSquareModQ
    5373807   // Barrett addend for modular reduction
};

const int montQInvModRIdx = 0;
const int dilithium_qIdx = 4;
const int montRSquareModQIdx = 8;
const int barrettAddendIdx = 12;

static address dilithiumAvx512ConstsAddr(int offset) {
  return ((address) dilithiumAvx512Consts) + offset;
}

ATTRIBUTE_ALIGNED(64) static const uint32_t unshufflePerms[] = {
  // Shuffle for the 128-bit element swap (uint64_t)
  0, 0, 1,  0, 8,  0, 9, 0, 4, 0, 5, 0, 12, 0, 13, 0,
  10, 0, 11, 0, 2, 0, 3, 0, 14, 0, 15, 0, 6, 0, 7, 0,

  // Final shuffle for AlmostNtt
  0, 16, 1, 17, 2, 18, 3, 19, 4, 20, 5, 21, 6, 22, 7, 23,
  24, 8, 25, 9, 26, 10, 27, 11, 28, 12, 29, 13, 30, 14, 31, 15,

  // Initial shuffle for AlmostInverseNtt
  0, 2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26, 28, 30,
  17, 19, 21, 23, 25, 27, 29, 31, 1, 3, 5, 7, 9, 11, 13, 15
};

static address unshufflePermsAddr(int offset) {
  return ((address) unshufflePerms) + offset*64;
}

// The following function swaps elements A<->B, C<->D, and so forth.
// input1[] is shuffled in place; shuffle of input2[] is copied to output2[].
// Element size (in bits) is specified by size parameter.
// +-----+-----+-----+-----+-----
// |     |  A  |     |  C  | ...
// +-----+-----+-----+-----+-----
// +-----+-----+-----+-----+-----
// |  B  |     |  D  |     | ...
// +-----+-----+-----+-----+-----
//
// NOTE: size 0 and 1 are used for initial and final shuffles respectively of
// dilithiumAlmostInverseNtt and dilithiumAlmostNtt. For size 0 and 1, input1[]
// and input2[] are modified in-place (and output2 is used as a temporary)
//
// Using C++ lambdas for improved readability (to hide parameters that always repeat)
static auto whole_shuffle(Register scratch, KRegister mergeMask1, KRegister mergeMask2,
  const XMMRegister unshuffle1, const XMMRegister unshuffle2, int vector_len, MacroAssembler *_masm) {

  int regCnt = 4;
  if (vector_len == Assembler::AVX_256bit) {
    regCnt = 2;
  }

  return [=](const XMMRegister output2[], const XMMRegister input1[],
    const XMMRegister input2[], int size) {
    if (vector_len == Assembler::AVX_256bit) {
      switch (size) {
        case 128:
          for (int i = 0; i < regCnt; i++) {
            __ vperm2i128(output2[i], input1[i], input2[i], 0b110001);
          }
          for (int i = 0; i < regCnt; i++) {
            __ vinserti128(input1[i], input1[i], input2[i], 1);
          }
          break;
        case 64:
          for (int i = 0; i < regCnt; i++) {
            __ vshufpd(output2[i], input1[i], input2[i], 0b11111111, vector_len);
          }
          for (int i = 0; i < regCnt; i++) {
            __ vshufpd(input1[i], input1[i], input2[i], 0b00000000, vector_len);
          }
          break;
        case 32:
          for (int i = 0; i < regCnt; i++) {
            __ vmovshdup(output2[i], input1[i], vector_len);
          }
          for (int i = 0; i < regCnt; i++) {
            __ vpblendd(output2[i], output2[i], input2[i], 0b10101010, vector_len);
          }
          for (int i = 0; i < regCnt; i++) {
            __ vmovsldup(input2[i], input2[i], vector_len);
          }
          for (int i = 0; i < regCnt; i++) {
            __ vpblendd(input1[i], input1[i], input2[i], 0b10101010, vector_len);
          }
          break;
        // Special cases
        case 1: // initial shuffle for dilithiumAlmostInverseNtt
          // shuffle all even 32bit columns to input1, and odd to input2
          for (int i = 0; i < regCnt; i++) {
            // 0b-3-1-3-1
            __ vshufps(output2[i], input1[i], input2[i], 0b11011101, vector_len);
          }
          for (int i = 0; i < regCnt; i++) {
            // 0b-2-0-2-0
            __ vshufps(input1[i], input1[i], input2[i], 0b10001000, vector_len);
          }
          for (int i = 0; i < regCnt; i++) {
            __ vpermq(input2[i], output2[i], 0b11011000, vector_len);
          }
          for (int i = 0; i < regCnt; i++) {
            // 0b-3-1-2-0
            __ vpermq(input1[i], input1[i], 0b11011000, vector_len);
          }
          break;
        case 0: // final unshuffle for dilithiumAlmostNtt
          // reverse case 1: all even are in input1 and odd in input2, put back
          for (int i = 0; i < regCnt; i++) {
            __ vpunpckhdq(output2[i], input1[i], input2[i], vector_len);
          }
          for (int i = 0; i < regCnt; i++) {
            __ vpunpckldq(input1[i], input1[i], input2[i], vector_len);
          }
          for (int i = 0; i < regCnt; i++) {
            __ vperm2i128(input2[i], input1[i], output2[i], 0b110001);
          }
          for (int i = 0; i < regCnt; i++) {
            __ vinserti128(input1[i], input1[i], output2[i], 1);
          }
          break;
        default:
          assert(false, "Don't call here");
      }
    } else {
      switch (size) {
        case 256:
          for (int i = 0; i < regCnt; i++) {
            // 0b-3-2-3-2
            __ evshufi64x2(output2[i], input1[i], input2[i], 0b11101110, vector_len);
          }
          for (int i = 0; i < regCnt; i++) {
            __ vinserti64x4(input1[i], input1[i], input2[i], 1);
          }
          break;
        case 128:
          for (int i = 0; i < regCnt; i++) {
            __ vmovdqu(output2[i], input2[i], vector_len);
          }
          for (int i = 0; i < regCnt; i++) {
            __ evpermt2q(output2[i], unshuffle2, input1[i], vector_len);
          }
          for (int i = 0; i < regCnt; i++) {
            __ evpermt2q(input1[i], unshuffle1, input2[i], vector_len);
          }

          break;
        case 64:
          for (int i = 0; i < regCnt; i++) {
            __ vshufpd(output2[i], input1[i], input2[i], 0b11111111, vector_len);
          }
          for (int i = 0; i < regCnt; i++) {
            __ vshufpd(input1[i], input1[i], input2[i], 0b00000000, vector_len);
          }
          break;
        case 32:
          for (int i = 0; i < regCnt; i++) {
            __ vmovdqu(output2[i], input2[i], vector_len);
          }
          for (int i = 0; i < regCnt; i++) {
            __ evmovshdup(output2[i], mergeMask2, input1[i], true, vector_len);
          }
          for (int i = 0; i < regCnt; i++) {
            __ evmovsldup(input1[i], mergeMask1, input2[i], true, vector_len);
          }
          break;
        // Special cases
        case 1: // initial shuffle for dilithiumAlmostInverseNtt
          // shuffle all even 32bit columns to input1, and odd to input2
          for (int i = 0; i < regCnt; i++) {
            __ vmovdqu(output2[i], input2[i], vector_len);
          }
          for (int i = 0; i < regCnt; i++) {
            __ evpermt2d(input2[i], unshuffle2, input1[i], vector_len);
          }
          for (int i = 0; i < regCnt; i++) {
            __ evpermt2d(input1[i], unshuffle1, output2[i], vector_len);
          }
          break;
        case 0: // final unshuffle for dilithiumAlmostNtt
          // reverse case 1: all even are in input1 and odd in input2, put back
          for (int i = 0; i < regCnt; i++) {
            __ vmovdqu(output2[i], input2[i], vector_len);
          }
          for (int i = 0; i < regCnt; i++) {
            __ evpermt2d(input2[i], unshuffle2, input1[i], vector_len);
          }
          for (int i = 0; i < regCnt; i++) {
            __ evpermt2d(input1[i], unshuffle1, output2[i], vector_len);
          }
          break;
        default:
          assert(false, "Don't call here");
      }
    }
  }; // return
}

// We do Montgomery multiplications of two AVX registers in 4 steps:
// 1. Do the multiplications of the corresponding even numbered slots into
//    the odd numbered slots of the scratch2 register.
// 2. Swap the even and odd numbered slots of the original input registers.(*Note)
// 3. Similar to step 1, but multiplication result is placed into output register.
// 4. Combine odd/even slots respectively from the scratch2 and output registers
//    into the output register for the final result of the Montgomery multiplication.
// (*Note: For levels 0-6 in the Ntt and levels 1-7 of the inverse Ntt, need NOT
//         swap the second operand (zetas) since the odd slots contain the same number
//         as the corresponding even one. This is indicated by input2NeedsShuffle=false)
//
// The registers to be multiplied are in input1[] and inputs2[]. The results go
// into output[]. Two scratch[] register arrays are expected. input1[] can
// overlap with either output[] or scratch1[]
// - If AVX512, all register arrays are of length 4
// - If AVX2, first two registers of each array are in xmm0-xmm15 range
// Constants montQInvModR, dilithium_q and mergeMask expected to have already
// been loaded.
//
// Using C++ lambdas for improved readability (to hide parameters that always repeat)
static auto whole_montMul(XMMRegister montQInvModR, XMMRegister dilithium_q,
    KRegister mergeMask, int vector_len, MacroAssembler *_masm) {
  int regCnt = 4;
  int regSize = 64;
  if (vector_len == Assembler::AVX_256bit) {
    regCnt = 2;
    regSize = 32;
  }

  return [=](const XMMRegister output[], const XMMRegister input1[],
    const XMMRegister input2[], const XMMRegister scratch1[],
    const XMMRegister scratch2[], bool input2NeedsShuffle = false) {
    // (Register overloading) Can't always use scratch1 (could override input1).
    // If so, use output:
    const XMMRegister* scratch = scratch1 == input1 ? output: scratch1;

    // scratch = input1_even * intput2_even
    for (int i = 0; i < regCnt; i++) {
      __ vpmuldq(scratch[i], input1[i], input2[i], vector_len);
    }

    // scratch2_low = scratch_low * montQInvModR
    for (int i = 0; i < regCnt; i++) {
      __ vpmuldq(scratch2[i], scratch[i], montQInvModR, vector_len);
    }

    // scratch2 = scratch2_low * dilithium_q
    for (int i = 0; i < regCnt; i++) {
      __ vpmuldq(scratch2[i], scratch2[i], dilithium_q, vector_len);
    }

    // scratch2_high = scratch2_high - scratch_high
    for (int i = 0; i < regCnt; i++) {
      __ vpsubd(scratch2[i], scratch[i], scratch2[i], vector_len);
    }

    // input1_even = input1_odd
    // input2_even = input2_odd
    for (int i = 0; i < regCnt; i++) {
      __ vpshufd(input1[i], input1[i], 0xB1, vector_len);
      if (input2NeedsShuffle) {
        __ vpshufd(input2[i], input2[i], 0xB1, vector_len);
      }
    }

    // scratch1 = input1_even*intput2_even
    for (int i = 0; i < regCnt; i++) {
      __ vpmuldq(scratch1[i], input1[i], input2[i], vector_len);
    }

    // output = scratch1_low * montQInvModR
    for (int i = 0; i < regCnt; i++) {
      __ vpmuldq(output[i], scratch1[i], montQInvModR, vector_len);
    }

    // output = output * dilithium_q
    for (int i = 0; i < regCnt; i++) {
      __ vpmuldq(output[i], output[i], dilithium_q, vector_len);
    }

    // output_high = scratch1_high - output_high
    for (int i = 0; i < regCnt; i++) {
      __ vpsubd(output[i], scratch1[i], output[i], vector_len);
    }

    // output = select(output_high, scratch2_high)
    if (vector_len == Assembler::AVX_256bit) {
      for (int i = 0; i < regCnt; i++) {
        __ vmovshdup(scratch2[i], scratch2[i], vector_len);
      }
      for (int i = 0; i < regCnt; i++) {
        __ vpblendd(output[i], output[i], scratch2[i], 0b01010101, vector_len);
      }
    } else {
      for (int i = 0; i < regCnt; i++) {
        __ evmovshdup(output[i], mergeMask, scratch2[i], true, vector_len);
      }
    }
  }; // return
}

static void sub_add(const XMMRegister subResult[], const XMMRegister addResult[],
                    const XMMRegister input1[], const XMMRegister input2[],
                    int vector_len, MacroAssembler *_masm) {
  int regCnt = 4;
  if (vector_len == Assembler::AVX_256bit) {
    regCnt = 2;
  }

  for (int i = 0; i < regCnt; i++) {
    __ vpsubd(subResult[i], input1[i], input2[i], vector_len);
  }

  for (int i = 0; i < regCnt; i++) {
    __ vpaddd(addResult[i], input1[i], input2[i], vector_len);
  }
}

static void loadXmms(const XMMRegister destinationRegs[], Register source, int offset,
                     int vector_len, MacroAssembler *_masm, int regCnt = -1, int memStep = -1) {

  if (vector_len == Assembler::AVX_256bit) {
    regCnt = regCnt == -1 ? 2 : regCnt;
    memStep = memStep == -1 ? 32 : memStep;
  } else {
    regCnt = 4;
    memStep = 64;
  }

  for (int i = 0; i < regCnt; i++) {
    __ vmovdqu(destinationRegs[i], Address(source, offset + i * memStep), vector_len);
  }
}

static void storeXmms(Register destination, int offset, const XMMRegister xmmRegs[],
                      int vector_len, MacroAssembler *_masm, int regCnt = -1, int memStep = -1) {
  if (vector_len == Assembler::AVX_256bit) {
    regCnt = regCnt == -1 ? 2 : regCnt;
    memStep = memStep == -1 ? 32 : memStep;
  } else {
    regCnt = 4;
    memStep = 64;
  }

  for (int i = 0; i < regCnt; i++) {
    __ vmovdqu(Address(destination, offset + i * memStep), xmmRegs[i], vector_len);
  }
}

// Dilithium NTT function except for the final "normalization" to |coeff| < Q.
// Implements
// static int implDilithiumAlmostNtt(int[] coeffs, int zetas[]) {}
//
// coeffs (int[256]) = c_rarg0
// zetas (int[128*8]) = c_rarg1
//
static address generate_dilithiumAlmostNtt_avx(StubGenerator *stubgen,
                                               int vector_len, MacroAssembler *_masm) {
  __ align(CodeEntryAlignment);
  StubId stub_id = StubId::stubgen_dilithiumAlmostNtt_id;
  StubCodeMark mark(stubgen, stub_id);
  address start = __ pc();
  __ enter();

  const Register coeffs = c_rarg0;
  const Register zetas = c_rarg1;
  const Register scratch = r10;

  // Each level represents one iteration of the outer for loop of the Java version
  // In each of these iterations half of the coefficients are (Montgomery)
  // multiplied by a zeta corresponding to the coefficient and then these
  // products will be added to and subtracted from the other half of the
  // coefficients. In each level we just shuffle the coefficients that need to
  // be multiplied by the zetas in one set, the rest to another set of vector
  // registers, then redistribute the addition/substraction results.

  // For levels 0 and 1 the zetas are not different within the 4 xmm registers
  // that we would use for them, so we use only one register.

  // AVX2 version uses the first half of these arrays
  const XMMRegister Coeffs1[] = {xmm0, xmm1, xmm16, xmm17};
  const XMMRegister Coeffs2[] = {xmm2, xmm3, xmm18, xmm19};
  const XMMRegister Coeffs3[] = {xmm4, xmm5, xmm20, xmm21};
  const XMMRegister Coeffs4[] = {xmm6, xmm7, xmm22, xmm23};
  const XMMRegister Scratch1[] = {xmm8, xmm9, xmm24, xmm25};
  const XMMRegister Scratch2[] = {xmm10, xmm11, xmm26, xmm27};
  const XMMRegister Zetas1[] = {xmm12, xmm12, xmm12, xmm12};
  const XMMRegister Zetas2[] = {xmm12, xmm12, xmm13, xmm13};
  const XMMRegister Zetas3[] = {xmm12, xmm13, xmm28, xmm29};
  const XMMRegister montQInvModR = xmm14;
  const XMMRegister dilithium_q = xmm15;
  const XMMRegister unshuffle1 = xmm30;
  const XMMRegister unshuffle2 = xmm31;
  KRegister mergeMask1 = k1;
  KRegister mergeMask2 = k2;
  // lambdas to hide repeated parameters
  auto shuffle = whole_shuffle(scratch, mergeMask1, mergeMask2, unshuffle1, unshuffle2, vector_len, _masm);
  auto montMul64 = whole_montMul(montQInvModR, dilithium_q, mergeMask2, vector_len, _masm);

  __ vpbroadcastd(montQInvModR,
                  ExternalAddress(dilithiumAvx512ConstsAddr(montQInvModRIdx)),
                  vector_len, scratch); // q^-1 mod 2^32
  __ vpbroadcastd(dilithium_q,
                  ExternalAddress(dilithiumAvx512ConstsAddr(dilithium_qIdx)),
                  vector_len, scratch); // q

  if (vector_len == Assembler::AVX_512bit) {
    // levels 0-3, register shuffles:
    const XMMRegister Coeffs1_1[] = {xmm0, xmm1, xmm2, xmm3};
    const XMMRegister Coeffs2_1[] = {xmm16, xmm17, xmm18, xmm19};
    const XMMRegister Coeffs3_1[] = {xmm4, xmm5, xmm6, xmm7};
    const XMMRegister Coeffs4_1[] = {xmm20, xmm21, xmm22, xmm23};
    const XMMRegister Coeffs1_2[] = {xmm0, xmm16, xmm2, xmm18};
    const XMMRegister Coeffs2_2[] = {xmm1, xmm17, xmm3, xmm19};
    const XMMRegister Coeffs3_2[] = {xmm4, xmm20, xmm6, xmm22};
    const XMMRegister Coeffs4_2[] = {xmm5, xmm21, xmm7, xmm23};

    // Constants for shuffle and montMul64
    __ mov64(scratch, 0b1010101010101010);
    __ kmovwl(mergeMask1, scratch);
    __ knotwl(mergeMask2, mergeMask1);
    __ vmovdqu(unshuffle1, ExternalAddress(unshufflePermsAddr(0)), vector_len, scratch);
    __ vmovdqu(unshuffle2, ExternalAddress(unshufflePermsAddr(1)), vector_len, scratch);

    int memStep = 4 * 64; // 4*64-byte registers
    loadXmms(Coeffs1, coeffs, 0*memStep, vector_len, _masm);
    loadXmms(Coeffs2, coeffs, 1*memStep, vector_len, _masm);
    loadXmms(Coeffs3, coeffs, 2*memStep, vector_len, _masm);
    loadXmms(Coeffs4, coeffs, 3*memStep, vector_len, _masm);

    // level 0-3 can be done by shuffling registers (also notice fewer zetas loads, they repeat)
    // level 0 - 128
    // scratch1 = coeffs3 * zetas1
    // coeffs3, coeffs1 = coeffs1 ± scratch1
    // scratch1 = coeffs4 * zetas1
    // coeffs4, coeffs2 = coeffs2 ± scratch1
    __ vmovdqu(Zetas1[0], Address(zetas, 0), vector_len);
    montMul64(Scratch1, Coeffs3, Zetas1, Coeffs3, Scratch2);
    sub_add(Coeffs3, Coeffs1, Coeffs1, Scratch1, vector_len, _masm);
    montMul64(Scratch1, Coeffs4, Zetas1, Coeffs4, Scratch2);
    sub_add(Coeffs4, Coeffs2, Coeffs2, Scratch1, vector_len, _masm);

    // level 1 - 64
    __ vmovdqu(Zetas1[0], Address(zetas,        512), vector_len);
    montMul64(Scratch1, Coeffs2, Zetas1, Coeffs2, Scratch2);
    sub_add(Coeffs2, Coeffs1, Coeffs1, Scratch1, vector_len, _masm);

    __ vmovdqu(Zetas1[0], Address(zetas, 4*64 + 512), vector_len);
    montMul64(Scratch1, Coeffs4, Zetas1, Coeffs4, Scratch2);
    sub_add(Coeffs4, Coeffs3, Coeffs3, Scratch1, vector_len, _masm);

    // level 2 - 32
    __ vmovdqu(Zetas2[0], Address(zetas,        2 * 512), vector_len);
    __ vmovdqu(Zetas2[2], Address(zetas, 2*64 + 2 * 512), vector_len);
    montMul64(Scratch1, Coeffs2_1, Zetas2, Coeffs2_1, Scratch2);
    sub_add(Coeffs2_1, Coeffs1_1, Coeffs1_1, Scratch1, vector_len, _masm);

    __ vmovdqu(Zetas2[0], Address(zetas, 4*64 + 2 * 512), vector_len);
    __ vmovdqu(Zetas2[2], Address(zetas, 6*64 + 2 * 512), vector_len);
    montMul64(Scratch1, Coeffs4_1, Zetas2, Coeffs4_1, Scratch2);
    sub_add(Coeffs4_1, Coeffs3_1, Coeffs3_1, Scratch1, vector_len, _masm);

    // level 3 - 16
    loadXmms(Zetas3, zetas, 3 * 512, vector_len, _masm);
    montMul64(Scratch1, Coeffs2_2, Zetas3, Coeffs2_2, Scratch2);
    sub_add(Coeffs2_2, Coeffs1_2, Coeffs1_2, Scratch1, vector_len, _masm);

    loadXmms(Zetas3, zetas, 4*64 + 3 * 512, vector_len, _masm);
    montMul64(Scratch1, Coeffs4_2, Zetas3, Coeffs4_2, Scratch2);
    sub_add(Coeffs4_2, Coeffs3_2, Coeffs3_2, Scratch1, vector_len, _masm);

    for (int level = 4, distance = 8; level<8; level++, distance /= 2) {
      // zetas = load(level * 512)
      // coeffs1_2, scratch1 = shuffle(coeffs1_2, coeffs2_2)
      // scratch1 = scratch1 * zetas
      // coeffs2_2 = coeffs1_2 - scratch1
      // coeffs1_2 = coeffs1_2 + scratch1
      loadXmms(Zetas3, zetas, level * 512, vector_len, _masm);
      shuffle(Scratch1, Coeffs1_2, Coeffs2_2, distance * 32); // Coeffs2_2 freed
      montMul64(Scratch1, Scratch1, Zetas3, Coeffs2_2, Scratch2, level==7);
      sub_add(Coeffs2_2, Coeffs1_2, Coeffs1_2, Scratch1, vector_len, _masm);

      loadXmms(Zetas3, zetas, 4*64 + level * 512, vector_len, _masm);
      shuffle(Scratch1, Coeffs3_2, Coeffs4_2, distance * 32); // Coeffs4_2 freed
      montMul64(Scratch1, Scratch1, Zetas3, Coeffs4_2, Scratch2, level==7);
      sub_add(Coeffs4_2, Coeffs3_2, Coeffs3_2, Scratch1, vector_len, _masm);
    }

    // Constants for final unshuffle
    __ vmovdqu(unshuffle1, ExternalAddress(unshufflePermsAddr(2)), vector_len, scratch);
    __ vmovdqu(unshuffle2, ExternalAddress(unshufflePermsAddr(3)), vector_len, scratch);
    shuffle(Scratch1, Coeffs1_2, Coeffs2_2, 0);
    shuffle(Scratch1, Coeffs3_2, Coeffs4_2, 0);

    storeXmms(coeffs, 0*memStep, Coeffs1, vector_len, _masm);
    storeXmms(coeffs, 1*memStep, Coeffs2, vector_len, _masm);
    storeXmms(coeffs, 2*memStep, Coeffs3, vector_len, _masm);
    storeXmms(coeffs, 3*memStep, Coeffs4, vector_len, _masm);
  } else { // Assembler::AVX_256bit
    // levels 0-4, register shuffles:
    const XMMRegister Coeffs1_1[] = {xmm0, xmm2};
    const XMMRegister Coeffs2_1[] = {xmm1, xmm3};
    const XMMRegister Coeffs3_1[] = {xmm4, xmm6};
    const XMMRegister Coeffs4_1[] = {xmm5, xmm7};

    const XMMRegister Coeffs1_2[] = {xmm0, xmm1, xmm2, xmm3};
    const XMMRegister Coeffs2_2[] = {xmm4, xmm5, xmm6, xmm7};

    // Since we cannot fit the entire payload into registers, we process the
    // input in two stages. For the first half, load 8 registers, each 32 integers
    // apart. With one load, we can process level 0-2 (128-, 64- and 32-integers
    // apart). For the remaining levels, load 8 registers from consecutive memory
    // (16-, 8-, 4-, 2-, 1-integer apart)
    // Levels 5, 6, 7 (4-, 2-, 1-integer apart) require shuffles within registers.
    // On the other levels, shuffles can be done by rearranging the register order

    // Four batches of 8 registers each, 128 bytes apart
    for (int i=0; i<4; i++) {
      loadXmms(Coeffs1_2, coeffs, i*32 + 0*128, vector_len, _masm, 4, 128);
      loadXmms(Coeffs2_2, coeffs, i*32 + 4*128, vector_len, _masm, 4, 128);

      // level 0-2 can be done by shuffling registers (also notice fewer zetas loads, they repeat)
      // level 0 - 128
      __ vmovdqu(Zetas1[0], Address(zetas, 0), vector_len);
      montMul64(Scratch1, Coeffs3, Zetas1, Coeffs3, Scratch2);
      sub_add(Coeffs3, Coeffs1, Coeffs1, Scratch1, vector_len, _masm);
      montMul64(Scratch1, Coeffs4, Zetas1, Coeffs4, Scratch2);
      sub_add(Coeffs4, Coeffs2, Coeffs2, Scratch1, vector_len, _masm);

      // level 1 - 64
      __ vmovdqu(Zetas1[0], Address(zetas,        512), vector_len);
      montMul64(Scratch1, Coeffs2, Zetas1, Coeffs2, Scratch2);
      sub_add(Coeffs2, Coeffs1, Coeffs1, Scratch1, vector_len, _masm);

      __ vmovdqu(Zetas1[0], Address(zetas, 4*64 + 512), vector_len);
      montMul64(Scratch1, Coeffs4, Zetas1, Coeffs4, Scratch2);
      sub_add(Coeffs4, Coeffs3, Coeffs3, Scratch1, vector_len, _masm);

      // level 2 - 32
      loadXmms(Zetas3, zetas, 2 * 512, vector_len, _masm, 2, 128);
      montMul64(Scratch1, Coeffs2_1, Zetas3, Coeffs2_1, Scratch2);
      sub_add(Coeffs2_1, Coeffs1_1, Coeffs1_1, Scratch1, vector_len, _masm);

      loadXmms(Zetas3, zetas, 4*64 + 2 * 512, vector_len, _masm, 2, 128);
      montMul64(Scratch1, Coeffs4_1, Zetas3, Coeffs4_1, Scratch2);
      sub_add(Coeffs4_1, Coeffs3_1, Coeffs3_1, Scratch1, vector_len, _masm);

      storeXmms(coeffs, i*32 + 0*128, Coeffs1_2, vector_len, _masm, 4, 128);
      storeXmms(coeffs, i*32 + 4*128, Coeffs2_2, vector_len, _masm, 4, 128);
    }

    // Four batches of 8 registers, consecutive loads
    for (int i=0; i<4; i++) {
      loadXmms(Coeffs1_2, coeffs,       i*256, vector_len, _masm, 4);
      loadXmms(Coeffs2_2, coeffs, 128 + i*256, vector_len, _masm, 4);

      // level 3 - 16
      __ vmovdqu(Zetas1[0], Address(zetas, i*128 + 3 * 512), vector_len);
      montMul64(Scratch1, Coeffs2, Zetas1, Coeffs2, Scratch2);
      sub_add(Coeffs2, Coeffs1, Coeffs1, Scratch1, vector_len, _masm);

      __ vmovdqu(Zetas1[0], Address(zetas, i*128 + 64 + 3 * 512), vector_len);
      montMul64(Scratch1, Coeffs4, Zetas1, Coeffs4, Scratch2);
      sub_add(Coeffs4, Coeffs3, Coeffs3, Scratch1, vector_len, _masm);

      // level 4 - 8
      loadXmms(Zetas3, zetas, i*128 + 4 * 512, vector_len, _masm);
      montMul64(Scratch1, Coeffs2_1, Zetas3, Coeffs2_1, Scratch2);
      sub_add(Coeffs2_1, Coeffs1_1, Coeffs1_1, Scratch1, vector_len, _masm);

      loadXmms(Zetas3, zetas, i*128 + 64 + 4 * 512, vector_len, _masm);
      montMul64(Scratch1, Coeffs4_1, Zetas3, Coeffs4_1, Scratch2);
      sub_add(Coeffs4_1, Coeffs3_1, Coeffs3_1, Scratch1, vector_len, _masm);

      for (int level = 5, distance = 4; level<8; level++, distance /= 2) {
        // zetas = load(level * 512)
        // coeffs1_2, scratch1 = shuffle(coeffs1_2, coeffs2_2)
        // scratch1 = scratch1 * zetas
        // coeffs2_2 = coeffs1_2 - scratch1
        // coeffs1_2 = coeffs1_2 + scratch1
        loadXmms(Zetas3, zetas, i*128 + level * 512, vector_len, _masm);
        shuffle(Scratch1, Coeffs1_1, Coeffs2_1, distance * 32); //Coeffs2_2 freed
        montMul64(Scratch1, Scratch1, Zetas3, Coeffs2_1, Scratch2, level==7);
        sub_add(Coeffs2_1, Coeffs1_1, Coeffs1_1, Scratch1, vector_len, _masm);

        loadXmms(Zetas3, zetas, i*128 + 64 + level * 512, vector_len, _masm);
        shuffle(Scratch1, Coeffs3_1, Coeffs4_1, distance * 32); //Coeffs4_2 freed
        montMul64(Scratch1, Scratch1, Zetas3, Coeffs4_1, Scratch2, level==7);
        sub_add(Coeffs4_1, Coeffs3_1, Coeffs3_1, Scratch1, vector_len, _masm);
      }

      shuffle(Scratch1, Coeffs1_1, Coeffs2_1, 0);
      shuffle(Scratch1, Coeffs3_1, Coeffs4_1, 0);

      storeXmms(coeffs,       i*256, Coeffs1_2, vector_len, _masm, 4);
      storeXmms(coeffs, 128 + i*256, Coeffs2_2, vector_len, _masm, 4);
    }
  }

  __ leave(); // required for proper stackwalking of RuntimeStub frame
  __ mov64(rax, 0); // return 0
  __ ret(0);

  return start;
}

// Dilithium Inverse NTT function except the final mod Q division by 2^256.
// Implements
// static int implDilithiumAlmostInverseNtt(int[] coeffs, int[] zetas) {}
//
// coeffs (int[256]) = c_rarg0
// zetas (int[128*8]) = c_rarg1
static address generate_dilithiumAlmostInverseNtt_avx(StubGenerator *stubgen,
                                        int vector_len, MacroAssembler *_masm) {
  __ align(CodeEntryAlignment);
  StubId stub_id = StubId::stubgen_dilithiumAlmostInverseNtt_id;
  StubCodeMark mark(stubgen, stub_id);
  address start = __ pc();
  __ enter();

  const Register coeffs = c_rarg0;
  const Register zetas = c_rarg1;
  const Register scratch = r10;

  // AVX2 version uses the first half of these arrays
  const XMMRegister Coeffs1[] = {xmm0, xmm1, xmm16, xmm17};
  const XMMRegister Coeffs2[] = {xmm2, xmm3, xmm18, xmm19};
  const XMMRegister Coeffs3[] = {xmm4, xmm5, xmm20, xmm21};
  const XMMRegister Coeffs4[] = {xmm6, xmm7, xmm22, xmm23};
  const XMMRegister Scratch1[] = {xmm8, xmm9, xmm24, xmm25};
  const XMMRegister Scratch2[] = {xmm10, xmm11, xmm26, xmm27};
  const XMMRegister Zetas1[] = {xmm12, xmm12, xmm12, xmm12};
  const XMMRegister Zetas2[] = {xmm12, xmm12, xmm13, xmm13};
  const XMMRegister Zetas3[] = {xmm12, xmm13, xmm28, xmm29};
  const XMMRegister montQInvModR = xmm14;
  const XMMRegister dilithium_q = xmm15;
  const XMMRegister unshuffle1 = xmm30;
  const XMMRegister unshuffle2 = xmm31;
  KRegister mergeMask1 = k1;
  KRegister mergeMask2 = k2;
  // lambdas to hide repeated parameters
  auto shuffle = whole_shuffle(scratch, mergeMask1, mergeMask2, unshuffle1, unshuffle2, vector_len, _masm);
  auto montMul64 = whole_montMul(montQInvModR, dilithium_q, mergeMask2, vector_len, _masm);

  __ vpbroadcastd(montQInvModR,
                  ExternalAddress(dilithiumAvx512ConstsAddr(montQInvModRIdx)),
                  vector_len, scratch); // q^-1 mod 2^32
  __ vpbroadcastd(dilithium_q,
                  ExternalAddress(dilithiumAvx512ConstsAddr(dilithium_qIdx)),
                  vector_len, scratch); // q

  // Each level represents one iteration of the outer for loop of the
  // Java version.
  // In each of these iterations half of the coefficients are added to and
  // subtracted from the other half of the coefficients then the result of
  // the subtraction is (Montgomery) multiplied by the corresponding zetas.
  // In each level we just shuffle the coefficients so that the results of
  // the additions and subtractions go to the vector registers so that they
  // align with each other and the zetas.

  if (vector_len == Assembler::AVX_512bit) {
    // levels 4-7, register shuffles:
    const XMMRegister Coeffs1_1[] = {xmm0, xmm1, xmm2, xmm3};
    const XMMRegister Coeffs2_1[] = {xmm16, xmm17, xmm18, xmm19};
    const XMMRegister Coeffs3_1[] = {xmm4, xmm5, xmm6, xmm7};
    const XMMRegister Coeffs4_1[] = {xmm20, xmm21, xmm22, xmm23};
    const XMMRegister Coeffs1_2[] = {xmm0, xmm16, xmm2, xmm18};
    const XMMRegister Coeffs2_2[] = {xmm1, xmm17, xmm3, xmm19};
    const XMMRegister Coeffs3_2[] = {xmm4, xmm20, xmm6, xmm22};
    const XMMRegister Coeffs4_2[] = {xmm5, xmm21, xmm7, xmm23};

    // Constants for shuffle and montMul64
    __ mov64(scratch, 0b1010101010101010);
    __ kmovwl(mergeMask1, scratch);
    __ knotwl(mergeMask2, mergeMask1);
    __ vmovdqu(unshuffle1, ExternalAddress(unshufflePermsAddr(4)), vector_len, scratch);
    __ vmovdqu(unshuffle2, ExternalAddress(unshufflePermsAddr(5)), vector_len, scratch);

    int memStep = 4 * 64;
    loadXmms(Coeffs1, coeffs, 0*memStep, vector_len, _masm);
    loadXmms(Coeffs2, coeffs, 1*memStep, vector_len, _masm);
    loadXmms(Coeffs3, coeffs, 2*memStep, vector_len, _masm);
    loadXmms(Coeffs4, coeffs, 3*memStep, vector_len, _masm);

    shuffle(Scratch1, Coeffs1_2, Coeffs2_2, 1);
    shuffle(Scratch1, Coeffs3_2, Coeffs4_2, 1);

    // Constants for shuffle(128)
    __ vmovdqu(unshuffle1, ExternalAddress(unshufflePermsAddr(0)), vector_len, scratch);
    __ vmovdqu(unshuffle2, ExternalAddress(unshufflePermsAddr(1)), vector_len, scratch);
    for (int level = 0, distance = 1; level<4; level++, distance *= 2) {
      // zetas = load(level * 512)
      // coeffs1_2 = coeffs1_2 + coeffs2_2
      // scratch1 = coeffs1_2 - coeffs2_2
      // scratch1 = scratch1 * zetas
      // coeffs1_2, coeffs2_2 = shuffle(coeffs1_2, scratch1)
      loadXmms(Zetas3, zetas, level * 512, vector_len, _masm);
      sub_add(Scratch1, Coeffs1_2, Coeffs1_2, Coeffs2_2, vector_len, _masm); // Coeffs2_2 freed
      montMul64(Scratch1, Scratch1, Zetas3, Coeffs2_2, Scratch2, level==0);
      shuffle(Coeffs2_2, Coeffs1_2, Scratch1, distance * 32);

      loadXmms(Zetas3, zetas, 4*64 + level * 512, vector_len, _masm);
      sub_add(Scratch1, Coeffs3_2, Coeffs3_2, Coeffs4_2, vector_len, _masm); // Coeffs4_2 freed
      montMul64(Scratch1, Scratch1, Zetas3, Coeffs4_2, Scratch2, level==0);
      shuffle(Coeffs4_2, Coeffs3_2, Scratch1, distance * 32);
    }

    // level 4
    loadXmms(Zetas3, zetas, 4 * 512, vector_len, _masm);
    sub_add(Scratch1, Coeffs1_2, Coeffs1_2, Coeffs2_2, vector_len, _masm); // Coeffs2_2 freed
    montMul64(Coeffs2_2, Scratch1, Zetas3, Scratch1, Scratch2);

    loadXmms(Zetas3, zetas, 4*64 + 4 * 512, vector_len, _masm);
    sub_add(Scratch1, Coeffs3_2, Coeffs3_2, Coeffs4_2, vector_len, _masm); // Coeffs4_2 freed
    montMul64(Coeffs4_2, Scratch1, Zetas3, Scratch1, Scratch2);

    // level 5
    __ vmovdqu(Zetas2[0], Address(zetas,        5 * 512), vector_len);
    __ vmovdqu(Zetas2[2], Address(zetas, 2*64 + 5 * 512), vector_len);
    sub_add(Scratch1, Coeffs1_1, Coeffs1_1, Coeffs2_1, vector_len, _masm); // Coeffs2_1 freed
    montMul64(Coeffs2_1, Scratch1, Zetas2, Scratch1, Scratch2);

    __ vmovdqu(Zetas2[0], Address(zetas, 4*64 + 5 * 512), vector_len);
    __ vmovdqu(Zetas2[2], Address(zetas, 6*64 + 5 * 512), vector_len);
    sub_add(Scratch1, Coeffs3_1, Coeffs3_1, Coeffs4_1, vector_len, _masm); // Coeffs4_1 freed
    montMul64(Coeffs4_1, Scratch1, Zetas2, Scratch1, Scratch2);

    // level 6
    __ vmovdqu(Zetas1[0], Address(zetas,        6 * 512), vector_len);
    sub_add(Scratch1, Coeffs1, Coeffs1, Coeffs2, vector_len, _masm); // Coeffs2 freed
    montMul64(Coeffs2, Scratch1, Zetas1, Scratch1, Scratch2);

    __ vmovdqu(Zetas1[0], Address(zetas, 4*64 + 6 * 512), vector_len);
    sub_add(Scratch1, Coeffs3, Coeffs3, Coeffs4, vector_len, _masm); // Coeffs4 freed
    montMul64(Coeffs4, Scratch1, Zetas1, Scratch1, Scratch2);

    // level 7
    __ vmovdqu(Zetas1[0], Address(zetas, 7 * 512), vector_len);
    sub_add(Scratch1, Coeffs1, Coeffs1, Coeffs3, vector_len, _masm); // Coeffs3 freed
    montMul64(Coeffs3, Scratch1, Zetas1, Scratch1, Scratch2);
    sub_add(Scratch1, Coeffs2, Coeffs2, Coeffs4, vector_len, _masm); // Coeffs4 freed
    montMul64(Coeffs4, Scratch1, Zetas1, Scratch1, Scratch2);

    storeXmms(coeffs, 0*memStep, Coeffs1, vector_len, _masm);
    storeXmms(coeffs, 1*memStep, Coeffs2, vector_len, _masm);
    storeXmms(coeffs, 2*memStep, Coeffs3, vector_len, _masm);
    storeXmms(coeffs, 3*memStep, Coeffs4, vector_len, _masm);
  } else { // Assembler::AVX_256bit
    // Permutations of Coeffs1, Coeffs2, Coeffs3 and Coeffs4
    const XMMRegister Coeffs1_1[] = {xmm0, xmm2};
    const XMMRegister Coeffs2_1[] = {xmm1, xmm3};
    const XMMRegister Coeffs3_1[] = {xmm4, xmm6};
    const XMMRegister Coeffs4_1[] = {xmm5, xmm7};

    const XMMRegister Coeffs1_2[] = {xmm0, xmm1, xmm2, xmm3};
    const XMMRegister Coeffs2_2[] = {xmm4, xmm5, xmm6, xmm7};

    // Four batches of 8 registers, consecutive loads
    for (int i=0; i<4; i++) {
      loadXmms(Coeffs1_2, coeffs,       i*256, vector_len, _masm, 4);
      loadXmms(Coeffs2_2, coeffs, 128 + i*256, vector_len, _masm, 4);

      shuffle(Scratch1, Coeffs1_1, Coeffs2_1, 1);
      shuffle(Scratch1, Coeffs3_1, Coeffs4_1, 1);

      for (int level = 0, distance = 1; level <= 2; level++, distance *= 2) {
        // zetas = load(level * 512)
        // coeffs1_2 = coeffs1_2 + coeffs2_2
        // scratch1 = coeffs1_2 - coeffs2_2
        // scratch1 = scratch1 * zetas
        // coeffs1_2, coeffs2_2 = shuffle(coeffs1_2, scratch1)
        loadXmms(Zetas3, zetas, i*128 + level * 512, vector_len, _masm);
        sub_add(Scratch1, Coeffs1_1, Coeffs1_1, Coeffs2_1, vector_len, _masm); // Coeffs2_1 freed
        montMul64(Scratch1, Scratch1, Zetas3, Coeffs2_1, Scratch2, level==0);
        shuffle(Coeffs2_1, Coeffs1_1, Scratch1, distance * 32);

        loadXmms(Zetas3, zetas, i*128 + 64 + level * 512, vector_len, _masm);
        sub_add(Scratch1, Coeffs3_1, Coeffs3_1, Coeffs4_1, vector_len, _masm); // Coeffs4_1 freed
        montMul64(Scratch1, Scratch1, Zetas3, Coeffs4_1, Scratch2, level==0);
        shuffle(Coeffs4_1, Coeffs3_1, Scratch1, distance * 32);
      }

      // level 3
      loadXmms(Zetas3, zetas, i*128 + 3 * 512, vector_len, _masm);
      sub_add(Scratch1, Coeffs1_1, Coeffs1_1, Coeffs2_1, vector_len, _masm); // Coeffs2_1 freed
      montMul64(Coeffs2_1, Scratch1, Zetas3, Scratch1, Scratch2);

      loadXmms(Zetas3, zetas, i*128 + 64 + 3 * 512, vector_len, _masm);
      sub_add(Scratch1, Coeffs3_1, Coeffs3_1, Coeffs4_1, vector_len, _masm); // Coeffs4_1 freed
      montMul64(Coeffs4_1, Scratch1, Zetas3, Scratch1, Scratch2);

      // level 4
      __ vmovdqu(Zetas1[0], Address(zetas, i*128 + 4 * 512), vector_len);
      sub_add(Scratch1, Coeffs1, Coeffs1, Coeffs2, vector_len, _masm); // Coeffs2 freed
      montMul64(Coeffs2, Scratch1, Zetas1, Scratch1, Scratch2);

      __ vmovdqu(Zetas1[0], Address(zetas, i*128 + 64 + 4 * 512), vector_len);
      sub_add(Scratch1, Coeffs3, Coeffs3, Coeffs4, vector_len, _masm); // Coeffs4 freed
      montMul64(Coeffs4, Scratch1, Zetas1, Scratch1, Scratch2);

      storeXmms(coeffs,       i*256, Coeffs1_2, vector_len, _masm, 4);
      storeXmms(coeffs, 128 + i*256, Coeffs2_2, vector_len, _masm, 4);
    }

    // Four batches of 8 registers each, 128 bytes apart
    for (int i=0; i<4; i++) {
      loadXmms(Coeffs1_2, coeffs, i*32 + 0*128, vector_len, _masm, 4, 128);
      loadXmms(Coeffs2_2, coeffs, i*32 + 4*128, vector_len, _masm, 4, 128);

      // level 5
      loadXmms(Zetas3, zetas, 5 * 512, vector_len, _masm, 2, 128);
      sub_add(Scratch1, Coeffs1_1, Coeffs1_1, Coeffs2_1, vector_len, _masm); // Coeffs2_1 freed
      montMul64(Coeffs2_1, Scratch1, Zetas3, Scratch1, Scratch2);

      loadXmms(Zetas3, zetas, 4*64 + 5 * 512, vector_len, _masm, 2, 128);
      sub_add(Scratch1, Coeffs3_1, Coeffs3_1, Coeffs4_1, vector_len, _masm); // Coeffs4_1 freed
      montMul64(Coeffs4_1, Scratch1, Zetas3, Scratch1, Scratch2);

      // level 6
      __ vmovdqu(Zetas1[0], Address(zetas,        6 * 512), vector_len);
      sub_add(Scratch1, Coeffs1, Coeffs1, Coeffs2, vector_len, _masm); // Coeffs2 freed
      montMul64(Coeffs2, Scratch1, Zetas1, Scratch1, Scratch2);

      __ vmovdqu(Zetas1[0], Address(zetas, 4*64 + 6 * 512), vector_len);
      sub_add(Scratch1, Coeffs3, Coeffs3, Coeffs4, vector_len, _masm); // Coeffs4 freed
      montMul64(Coeffs4, Scratch1, Zetas1, Scratch1, Scratch2);

      // level 7
      __ vmovdqu(Zetas1[0], Address(zetas, 7 * 512), vector_len);
      sub_add(Scratch1, Coeffs1, Coeffs1, Coeffs3, vector_len, _masm); // Coeffs3 freed
      montMul64(Coeffs3, Scratch1, Zetas1, Scratch1, Scratch2);
      sub_add(Scratch1, Coeffs2, Coeffs2, Coeffs4, vector_len, _masm); // Coeffs4 freed
      montMul64(Coeffs4, Scratch1, Zetas1, Scratch1, Scratch2);

      storeXmms(coeffs, i*32 + 0*128, Coeffs1_2, vector_len, _masm, 4, 128);
      storeXmms(coeffs, i*32 + 4*128, Coeffs2_2, vector_len, _masm, 4, 128);
    }
  }

  __ leave(); // required for proper stackwalking of RuntimeStub frame
  __ mov64(rax, 0); // return 0
  __ ret(0);

  return start;
}

// Dilithium multiply polynomials in the NTT domain.
// Implements
// static int implDilithiumNttMult(
//              int[] result, int[] ntta, int[] nttb {}
//
// result (int[256]) = c_rarg0
// poly1 (int[256]) = c_rarg1
// poly2 (int[256]) = c_rarg2
static address generate_dilithiumNttMult_avx(StubGenerator *stubgen,
                                     int vector_len, MacroAssembler *_masm) {

  __ align(CodeEntryAlignment);
  StubId stub_id = StubId::stubgen_dilithiumNttMult_id;
  StubCodeMark mark(stubgen, stub_id);
  address start = __ pc();
  __ enter();

  Label L_loop;

  const Register result = c_rarg0;
  const Register poly1 = c_rarg1;
  const Register poly2 = c_rarg2;
  const Register scratch = r10;
  const Register len = r11;

  const XMMRegister montQInvModR = xmm8;
  const XMMRegister dilithium_q = xmm9;

  const XMMRegister Poly1[] = {xmm0, xmm1, xmm16, xmm17};
  const XMMRegister Poly2[] = {xmm2, xmm3, xmm18, xmm19};
  const XMMRegister Scratch1[] = {xmm4, xmm5, xmm20, xmm21};
  const XMMRegister Scratch2[] = {xmm6, xmm7, xmm22, xmm23};
  const XMMRegister MontRSquareModQ[] = {xmm10, xmm10, xmm10, xmm10};
  KRegister mergeMask = k1;
  // lambda to hide repeated parameters
  auto montMul64 = whole_montMul(montQInvModR, dilithium_q, mergeMask, vector_len, _masm);

  __ vpbroadcastd(montQInvModR,
                  ExternalAddress(dilithiumAvx512ConstsAddr(montQInvModRIdx)),
                  vector_len, scratch); // q^-1 mod 2^32
  __ vpbroadcastd(dilithium_q,
                  ExternalAddress(dilithiumAvx512ConstsAddr(dilithium_qIdx)),
                  vector_len, scratch); // q
  __ vpbroadcastd(MontRSquareModQ[0],
                  ExternalAddress(dilithiumAvx512ConstsAddr(montRSquareModQIdx)),
                  vector_len, scratch); // 2^64 mod q
  if (vector_len == Assembler::AVX_512bit) {
    __ mov64(scratch, 0b0101010101010101);
    __ kmovwl(mergeMask, scratch);
  }

  // Total payload is 256*int32s.
  // - memStep is number of bytes one iteration processes.
  // - loopCnt is number of iterations it will take to process entire payload.
  int loopCnt = 4;
  int memStep = 4 * 64;
  if (vector_len == Assembler::AVX_256bit) {
    loopCnt = 16;
    memStep = 2 * 32;
  }

  __ movl(len, loopCnt);

  __ align(OptoLoopAlignment);
  __ BIND(L_loop);

  loadXmms(Poly2, poly2, 0, vector_len, _masm);
  loadXmms(Poly1, poly1, 0, vector_len, _masm);
  montMul64(Poly2, Poly2, MontRSquareModQ, Scratch1, Scratch2);
  montMul64(Poly1, Poly1, Poly2,           Scratch1, Scratch2, true);
  storeXmms(result, 0, Poly1, vector_len, _masm);

  __ subl(len, 1);
  __ addptr(poly1, memStep);
  __ addptr(poly2, memStep);
  __ addptr(result, memStep);
  __ cmpl(len, 0);
  __ jcc(Assembler::notEqual, L_loop);

  __ leave(); // required for proper stackwalking of RuntimeStub frame
  __ mov64(rax, 0); // return 0
  __ ret(0);

  return start;
}

// Dilithium Motgomery multiply an array by a constant.
// Implements
// static int implDilithiumMontMulByConstant(int[] coeffs, int constant) {}
//
// coeffs (int[256]) = c_rarg0
// constant (int) = c_rarg1
static address generate_dilithiumMontMulByConstant_avx(StubGenerator *stubgen,
                                        int vector_len, MacroAssembler *_masm) {

  __ align(CodeEntryAlignment);
  StubId stub_id = StubId::stubgen_dilithiumMontMulByConstant_id;
  StubCodeMark mark(stubgen, stub_id);
  address start = __ pc();
  __ enter();

  Label L_loop;

  const Register coeffs = c_rarg0;
  const Register rConstant = c_rarg1;
  const Register scratch = r10;
  const Register len = r11;

  const XMMRegister montQInvModR = xmm8;
  const XMMRegister dilithium_q = xmm9;

  const XMMRegister Coeffs1[] = {xmm0, xmm1, xmm16, xmm17};
  const XMMRegister Coeffs2[] = {xmm2, xmm3, xmm18, xmm19};
  const XMMRegister Scratch1[] = {xmm4, xmm5, xmm20, xmm21};
  const XMMRegister Scratch2[] = {xmm6, xmm7, xmm22, xmm23};
  const XMMRegister Constant[] = {xmm10, xmm10, xmm10, xmm10};
  XMMRegister constant = Constant[0];
  KRegister mergeMask = k1;
  // lambda to hide repeated parameters
  auto montMul64 = whole_montMul(montQInvModR, dilithium_q, mergeMask, vector_len, _masm);

  // load constants for montMul64
  __ vpbroadcastd(montQInvModR,
                  ExternalAddress(dilithiumAvx512ConstsAddr(montQInvModRIdx)),
                  vector_len, scratch); // q^-1 mod 2^32
  __ vpbroadcastd(dilithium_q,
                  ExternalAddress(dilithiumAvx512ConstsAddr(dilithium_qIdx)),
                  vector_len, scratch); // q
  if (vector_len == Assembler::AVX_256bit) {
    __ movdl(constant, rConstant);
    __ vpbroadcastd(constant, constant, vector_len); // constant multiplier
  } else {
    __ evpbroadcastd(constant, rConstant, Assembler::AVX_512bit); // constant multiplier

    __ mov64(scratch, 0b0101010101010101); //dw-mask
    __ kmovwl(mergeMask, scratch);
  }

  // Total payload is 256*int32s.
  // - memStep is number of bytes one montMul64 processes.
  // - loopCnt is number of iterations it will take to process entire payload.
  // - (two memSteps per loop)
  int memStep = 4 * 64;
  int loopCnt = 2;
  if (vector_len == Assembler::AVX_256bit) {
    memStep = 2 * 32;
    loopCnt = 8;
  }

  __ movl(len, loopCnt);

  __ align(OptoLoopAlignment);
  __ BIND(L_loop);

  loadXmms(Coeffs1, coeffs, 0,       vector_len, _masm);
  loadXmms(Coeffs2, coeffs, memStep, vector_len, _masm);
  montMul64(Coeffs1, Coeffs1, Constant, Scratch1, Scratch2);
  montMul64(Coeffs2, Coeffs2, Constant, Scratch1, Scratch2);
  storeXmms(coeffs, 0,       Coeffs1, vector_len, _masm);
  storeXmms(coeffs, memStep, Coeffs2, vector_len, _masm);

  __ subl(len, 1);
  __ addptr(coeffs, 2 * memStep);
  __ cmpl(len, 0);
  __ jcc(Assembler::notEqual, L_loop);

  __ leave(); // required for proper stackwalking of RuntimeStub frame
  __ mov64(rax, 0); // return 0
  __ ret(0);

  return start;
}

// Dilithium decompose poly.
// Implements
// static int implDilithiumDecomposePoly(int[] coeffs, int constant) {}
//
// input (int[256]) = c_rarg0
// lowPart (int[256]) = c_rarg1
// highPart (int[256]) = c_rarg2
// twoGamma2  (int) = c_rarg3
// multiplier (int) = c_rarg4
static address generate_dilithiumDecomposePoly_avx(StubGenerator *stubgen,
                                      int vector_len, MacroAssembler *_masm) {
  __ align(CodeEntryAlignment);
  StubId stub_id = StubId::stubgen_dilithiumDecomposePoly_id;
  StubCodeMark mark(stubgen, stub_id);
  address start = __ pc();
  __ enter();

  Label L_loop;

  const Register input = c_rarg0;
  const Register lowPart = c_rarg1;
  const Register highPart = c_rarg2;
  const Register rTwoGamma2 = c_rarg3;

  const Register scratch = r10;
  const Register len = r11;

  const XMMRegister one = xmm0;
  const XMMRegister gamma2 = xmm1;
  const XMMRegister twoGamma2 = xmm2;
  const XMMRegister barrettMultiplier = xmm3;
  const XMMRegister barrettAddend = xmm4;
  const XMMRegister dilithium_q = xmm5;
  const XMMRegister zero = xmm29;     // AVX512-only
  const XMMRegister minusOne = xmm30; // AVX512-only
  const XMMRegister qMinus1 = xmm31;  // AVX512-only

  XMMRegister RPlus[] = {xmm6, xmm7, xmm16, xmm17};
  XMMRegister Quotient[] = {xmm8, xmm9, xmm18, xmm19};
  XMMRegister R0[] = {xmm10, xmm11, xmm20, xmm21};
  XMMRegister Mask[] = {xmm12, xmm13, xmm22, xmm23};
  XMMRegister Tmp1[] = {xmm14, xmm15, xmm24, xmm25};

  __ vpbroadcastd(dilithium_q,
                  ExternalAddress(dilithiumAvx512ConstsAddr(dilithium_qIdx)),
                  vector_len, scratch); // q
  __ vpbroadcastd(barrettAddend,
                  ExternalAddress(dilithiumAvx512ConstsAddr(barrettAddendIdx)),
                  vector_len, scratch); // addend for Barrett reduction
  if (vector_len == Assembler::AVX_512bit) {
    __ vpxor(zero, zero, zero, vector_len); // 0
    __ vpternlogd(minusOne, 0xff, minusOne, minusOne, vector_len); // -1
    __ vpsrld(one, minusOne, 31, vector_len);
    __ vpsubd(qMinus1, dilithium_q, one, vector_len); // q - 1
    __ evpbroadcastd(twoGamma2, rTwoGamma2, vector_len); // 2 * gamma2
  } else {
    __ vpcmpeqd(one, one, one, vector_len);
    __ vpsrld(one, one, 31, vector_len);
    __ movdl(twoGamma2, rTwoGamma2);
    __ vpbroadcastd(twoGamma2, twoGamma2, vector_len); // 2 * gamma2
  }

  __ vpsrad(gamma2, twoGamma2, 1, vector_len); // gamma2

  #ifndef _WIN64
    const Register rMultiplier = c_rarg4;
  #else
    const Address multiplier_mem(rbp, 6 * wordSize);
    const Register rMultiplier = c_rarg3; // arg3 is already consumed, reused here
    __ movptr(rMultiplier, multiplier_mem);
  #endif
  if (vector_len == Assembler::AVX_512bit) {
    __ evpbroadcastd(barrettMultiplier, rMultiplier,
                  vector_len); // multiplier for mod 2 * gamma2 reduce
  } else {
    __ movdl(barrettMultiplier, rMultiplier);
    __ vpbroadcastd(barrettMultiplier, barrettMultiplier, vector_len);
  }

  // Total payload is 1024 bytes
  int memStep = 4 * 64; // Number of bytes per loop iteration
  int regCnt = 4; // Register array length
  if (vector_len == Assembler::AVX_256bit) {
    memStep = 2 * 32;
    regCnt = 2;
  }

  __ movl(len, 1024);

  __ align(OptoLoopAlignment);
  __ BIND(L_loop);

  loadXmms(RPlus, input, 0, vector_len, _masm);

  __ addptr(input, memStep);

  // rplus = rplus - ((rplus + 5373807) >> 23) * dilithium_q;
  for (int i = 0; i < regCnt; i++) {
    __ vpaddd(Tmp1[i], RPlus[i], barrettAddend, vector_len);
  }

  for (int i = 0; i < regCnt; i++) {
    __ vpsrad(Tmp1[i], Tmp1[i], 23, vector_len);
  }

  for (int i = 0; i < regCnt; i++) {
    __ vpmulld(Tmp1[i], Tmp1[i], dilithium_q, vector_len);
  }

  for (int i = 0; i < regCnt; i++) {
    __ vpsubd(RPlus[i], RPlus[i], Tmp1[i], vector_len);
  }

  // rplus = rplus + ((rplus >> 31) & dilithium_q);
  for (int i = 0; i < regCnt; i++) {
    __ vpsrad(Tmp1[i], RPlus[i], 31, vector_len);
  }

  for (int i = 0; i < regCnt; i++) {
    __ vpand(Tmp1[i], Tmp1[i], dilithium_q, vector_len);
  }

  for (int i = 0; i < regCnt; i++) {
    __ vpaddd(RPlus[i], RPlus[i], Tmp1[i], vector_len);
  }

  // int quotient = (rplus * barrettMultiplier) >> 22;
  for (int i = 0; i < regCnt; i++) {
    __ vpmulld(Quotient[i], RPlus[i], barrettMultiplier, vector_len);
  }

  for (int i = 0; i < regCnt; i++) {
    __ vpsrad(Quotient[i], Quotient[i], 22, vector_len);
  }

  // int r0 = rplus - quotient * twoGamma2;
  for (int i = 0; i < regCnt; i++) {
    __ vpmulld(R0[i], Quotient[i], twoGamma2, vector_len);
  }

  for (int i = 0; i < regCnt; i++) {
    __ vpsubd(R0[i], RPlus[i], R0[i], vector_len);
  }

  // int mask = (twoGamma2 - r0) >> 22;
  for (int i = 0; i < regCnt; i++) {
    __ vpsubd(Mask[i], twoGamma2, R0[i], vector_len);
  }

  for (int i = 0; i < regCnt; i++) {
    __ vpsrad(Mask[i], Mask[i], 22, vector_len);
  }

  // r0 -= (mask & twoGamma2);
  for (int i = 0; i < regCnt; i++) {
    __ vpand(Tmp1[i], Mask[i], twoGamma2, vector_len);
  }

  for (int i = 0; i < regCnt; i++) {
    __ vpsubd(R0[i], R0[i], Tmp1[i], vector_len);
  }

  // quotient += (mask & 1);
  for (int i = 0; i < regCnt; i++) {
    __ vpand(Tmp1[i], Mask[i], one, vector_len);
  }

  for (int i = 0; i < regCnt; i++) {
    __ vpaddd(Quotient[i], Quotient[i], Tmp1[i], vector_len);
  }

  // mask = (twoGamma2 / 2 - r0) >> 31;
  for (int i = 0; i < regCnt; i++) {
    __ vpsubd(Mask[i], gamma2, R0[i], vector_len);
  }

  for (int i = 0; i < regCnt; i++) {
    __ vpsrad(Mask[i], Mask[i], 31, vector_len);
  }

  // r0 -= (mask & twoGamma2);
  for (int i = 0; i < regCnt; i++) {
    __ vpand(Tmp1[i], Mask[i], twoGamma2, vector_len);
  }

  for (int i = 0; i < regCnt; i++) {
    __ vpsubd(R0[i], R0[i], Tmp1[i], vector_len);
  }

  // quotient += (mask & 1);
  for (int i = 0; i < regCnt; i++) {
    __ vpand(Tmp1[i], Mask[i], one, vector_len);
  }

  for (int i = 0; i < regCnt; i++) {
    __ vpaddd(Quotient[i], Quotient[i], Tmp1[i], vector_len);
  }
  // r1 in RPlus
  // int r1 = rplus - r0 - (dilithium_q - 1);
  // r1 = (r1 | (-r1)) >> 31; // 0 if rplus - r0 == (dilithium_q - 1), -1 otherwise
  for (int i = 0; i < regCnt; i++) {
    __ vpsubd(RPlus[i], RPlus[i], R0[i], vector_len);
  }

  if (vector_len == Assembler::AVX_512bit) {
    KRegister EqMsk[] = {k1, k2, k3, k4};
    for (int i = 0; i < regCnt; i++) {
      __ evpcmpeqd(EqMsk[i], k0, RPlus[i], qMinus1, vector_len);
    }

    // r0 += ~r1; // add -1 or keep as is, using EqMsk as filter
    for (int i = 0; i < regCnt; i++) {
      __ evpaddd(R0[i], EqMsk[i], R0[i], minusOne, true, vector_len);
    }

    // r1 in Quotient
    // r1 = r1 & quotient; // copy 0 or keep as is, using EqMsk as filter
    for (int i = 0; i < regCnt; i++) {
      __ evpandd(Quotient[i], EqMsk[i], Quotient[i], zero, true, vector_len);
    }
  } else {
    const XMMRegister qMinus1 = Tmp1[0];
    __ vpsubd(qMinus1, dilithium_q, one, vector_len); // q - 1

    for (int i = 0; i < regCnt; i++) {
      __ vpcmpeqd(Mask[i], RPlus[i], qMinus1, vector_len);
    }

    // r0 += ~r1;
    // Mask already negated
    for (int i = 0; i < regCnt; i++) {
      __ vpaddd(R0[i], R0[i], Mask[i], vector_len);
    }

    // r1 in Quotient
    // r1 = r1 & quotient;
    for (int i = 0; i < regCnt; i++) {
      __ vpandn(Quotient[i], Mask[i], Quotient[i], vector_len);
    }
  }

  // r1 in Quotient
  // lowPart[m] = r0;
  // highPart[m] = r1;
  storeXmms(highPart, 0, Quotient, vector_len, _masm);
  storeXmms(lowPart, 0, R0, vector_len, _masm);

  __ addptr(highPart, memStep);
  __ addptr(lowPart, memStep);
  __ subl(len, memStep);
  __ jcc(Assembler::notEqual, L_loop);

  __ leave(); // required for proper stackwalking of RuntimeStub frame
  __ mov64(rax, 0); // return 0
  __ ret(0);

  return start;
}

void StubGenerator::generate_dilithium_stubs() {
  int vector_len = Assembler::AVX_256bit;
  if (VM_Version::supports_evex() && VM_Version::supports_avx512bw()) {
    vector_len = Assembler::AVX_512bit;
  }
  // Generate Dilithium intrinsics code
  if (UseDilithiumIntrinsics) {
    StubRoutines::_dilithiumAlmostNtt =
        generate_dilithiumAlmostNtt_avx(this, vector_len, _masm);
    StubRoutines::_dilithiumAlmostInverseNtt =
        generate_dilithiumAlmostInverseNtt_avx(this, vector_len, _masm);
    StubRoutines::_dilithiumNttMult =
        generate_dilithiumNttMult_avx(this, vector_len, _masm);
    StubRoutines::_dilithiumMontMulByConstant =
        generate_dilithiumMontMulByConstant_avx(this, vector_len, _masm);
    StubRoutines::_dilithiumDecomposePoly =
        generate_dilithiumDecomposePoly_avx(this, vector_len, _masm);
  }
}
