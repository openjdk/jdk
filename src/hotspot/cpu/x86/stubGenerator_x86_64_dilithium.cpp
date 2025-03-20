/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

#define xmm(i) as_XMMRegister(i)

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
    8380417, // dilithium_q
    16382, // toMont((dilithium_n)^-1 (mod dilithium_q))
    2365951, // montRSquareModQ
    5373807 // addend for modular reduce
};

static address dilithiumAvx512ConstsAddr() {
  return (address) dilithiumAvx512Consts;
}

ATTRIBUTE_ALIGNED(64) static const uint32_t dilithiumAvx512Perms[] = {
     // collect montmul results into the destination register
    17, 1, 19, 3, 21, 5, 23, 7, 25, 9, 27, 11, 29, 13, 31, 15,
    // ntt
    0, 1, 2, 3, 4, 5, 6, 7, 16, 17, 18, 19, 20, 21, 22, 23,
    8, 9, 10, 11, 12, 13, 14, 15, 24, 25, 26, 27, 28, 29, 30, 31,
    0, 1, 2, 3, 16, 17, 18, 19, 8, 9, 10, 11, 24, 25, 26, 27,
    4, 5, 6, 7, 20, 21, 22, 23, 12, 13, 14, 15, 28, 29, 30, 31,
    0, 1, 16, 17, 4, 5, 20, 21, 8, 9, 24, 25, 12, 13, 28, 29,
    2, 3, 18, 19, 6, 7, 22, 23, 10, 11, 26, 27, 14, 15, 30, 31,
    0, 16, 2, 18, 4, 20, 6, 22, 8, 24, 10, 26, 12, 28, 14, 30,
    1, 17, 3, 19, 5, 21, 7, 23, 9, 25, 11, 27, 13, 29, 15, 31,
    0, 16, 1, 17, 2, 18, 3, 19, 4, 20, 5, 21, 6, 22, 7, 23,
    8, 24, 9, 25, 10, 26, 11, 27, 12, 28, 13, 29, 14, 30, 15, 31,
    // ntt inverse
    0, 2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26, 28, 30,
    1, 3, 5, 7, 9, 11, 13, 15, 17, 19, 21, 23, 25, 27, 29, 31,
    0, 16, 2, 18, 4, 20, 6, 22, 8, 24, 10, 26, 12, 28, 14, 30,
    1, 17, 3, 19, 5, 21, 7, 23, 9, 25, 11, 27, 13, 29, 15, 31,
    0, 1, 16, 17, 4, 5, 20, 21, 8, 9, 24, 25, 12, 13, 28, 29,
    2, 3, 18, 19, 6, 7, 22, 23, 10, 11, 26, 27, 14, 15, 30, 31,
    0, 1, 2, 3, 16, 17, 18, 19, 8, 9, 10, 11, 24, 25, 26, 27,
    4, 5, 6, 7, 20, 21, 22, 23, 12, 13, 14, 15, 28, 29, 30, 31,
    0, 1, 2, 3, 4, 5, 6, 7, 16, 17, 18, 19, 20, 21, 22, 23,
    8, 9, 10, 11, 12, 13, 14, 15, 24, 25, 26, 27, 28, 29, 30, 31
};

static address dilithiumAvx512PermsAddr() {
  return (address) dilithiumAvx512Perms;
}

// We do Montgomery multiplications of two vectors of 16 ints each in 4 steps:
// 1. Do the multiplications of the corresponding even numbered slots into
//    the odd numbered slots of a third register using montmulEven().
// 2. Swap the even and odd numbered slots of the original input registers.
// 3. Similar to step 1, but into a different output register.
// 4. Combine the outputs of step 1 and step 3 into the output of the Montgomery
//    multiplication.
// (For levels 0-6 in the Ntt and levels 1-7 of the inverse Ntt we only swap the
// odd-even slots of the first multiplicand as in the second (zetas) the
// odd slots contain the same number as the corresponding even one.)

// Montgomery multiplication of the *even* numbered slices of sequences
// of parCnt consecutive registers. Zmm_inputReg1 and Zmm_inputReg2 are the
// starts of the two sequences. When inputReg2 == 29, we use that register as
// the second multiplicand in each multiplication.
// The result goes to the *odd* numbered slices of Zmm_outputReg.
// The parCnt long cosecutive sequences of registers that start with
// Zmm_scratch1 and Zmm_scrath2 are used as scratch registers, so their
// contents will be clobbered.
// The output register sequence  can overlap any of the input and scratch
// register sequences, however the two scratch register sequences should be
// non-overlapping.
// Zmm_31 should contain q and
// Zmm_30 should contain q^-1 mod 2^32 in all of their slices.
static void montmulEven(int outputReg, int inputReg1,  int inputReg2,
                        int scratchReg1, int scratchReg2,
                        int parCnt, MacroAssembler *_masm) {
  for (int i = 0; i < parCnt; i++) {
    __ vpmuldq(xmm(i + scratchReg1), xmm(i + inputReg1),
               xmm((inputReg2 == 29) ? 29 : inputReg2 + i), Assembler::AVX_512bit);
  }
  for (int i = 0; i < parCnt; i++) {
    __ vpmulld(xmm(i + scratchReg2), xmm(i + scratchReg1), xmm30,
               Assembler::AVX_512bit);
  }
  for (int i = 0; i < parCnt; i++) {
    __ vpmuldq(xmm(i + scratchReg2), xmm(i + scratchReg2), xmm31,
               Assembler::AVX_512bit);
  }
  for (int i = 0; i < parCnt; i++) {
    __ evpsubd(xmm(i + outputReg), k0, xmm(i + scratchReg1),
               xmm(i + scratchReg2), false, Assembler::AVX_512bit);
  }
}

// Full Montgomery multiplication of the corresponding slices of two register
// sets of 4 registers each. The indexes of the registers to be multiplied
// are in inputRegs1[] and inputRegs[2].
// The results go to the registers whose indexes are in outputRegs.
// scratchRegs should contain 12 different register indexes.
// The set in outputRegs should not overlap with the set of the middle four
// scratch registers.
// The sets in inputRegs1 and inputRegs2 cannot overlap with the set of the
// first eight scratch registers.
// In most of the cases, the odd and the corresponding even slices of the
// registers indexed by the numbers in inputRegs2 will contain the same number,
// this should be indicated by calling this function with
// input2NeedsShuffle=false .
//
static void montMul64(int outputRegs[], int inputRegs1[], int inputRegs2[],
                      int scratchRegs[], bool input2NeedsShuffle,
                      MacroAssembler *_masm) {

  for (int i = 0; i < 4; i++) {
    __ vpmuldq(xmm(scratchRegs[i]), xmm(inputRegs1[i]), xmm(inputRegs2[i]),
               Assembler::AVX_512bit);
  }
  for (int i = 0; i < 4; i++) {
    __ vpmulld(xmm(scratchRegs[i + 4]), xmm(scratchRegs[i]), xmm30,
               Assembler::AVX_512bit);
  }
  for (int i = 0; i < 4; i++) {
    __ vpmuldq(xmm(scratchRegs[i + 4]), xmm(scratchRegs[i + 4]), xmm31,
               Assembler::AVX_512bit);
  }
  for (int i = 0; i < 4; i++) {
    __ evpsubd(xmm(scratchRegs[i + 4]), k0, xmm(scratchRegs[i]),
               xmm(scratchRegs[i + 4]), false, Assembler::AVX_512bit);
  }

  for (int i = 0; i < 4; i++) {
    __ vpshufd(xmm(inputRegs1[i]), xmm(inputRegs1[i]), 0xB1,
               Assembler::AVX_512bit);
    if (input2NeedsShuffle) {
       __ vpshufd(xmm(inputRegs2[i]), xmm(inputRegs2[i]), 0xB1,
                  Assembler::AVX_512bit);
    }
  }

  for (int i = 0; i < 4; i++) {
    __ vpmuldq(xmm(scratchRegs[i]), xmm(inputRegs1[i]), xmm(inputRegs2[i]),
               Assembler::AVX_512bit);
  }
  for (int i = 0; i < 4; i++) {
    __ vpmulld(xmm(scratchRegs[i + 8]), xmm(scratchRegs[i]), xmm30,
               Assembler::AVX_512bit);
  }
  for (int i = 0; i < 4; i++) {
    __ vpmuldq(xmm(scratchRegs[i + 8]), xmm(scratchRegs[i + 8]), xmm31,
               Assembler::AVX_512bit);
  }
  for (int i = 0; i < 4; i++) {
    __ evpsubd(xmm(outputRegs[i]), k0, xmm(scratchRegs[i]),
               xmm(scratchRegs[i + 8]), false, Assembler::AVX_512bit);
  }

  for (int i = 0; i < 4; i++) {
    __ evpermt2d(xmm(outputRegs[i]), xmm28, xmm(scratchRegs[i + 4]),
                 Assembler::AVX_512bit);
  }
}

static void montMul64(int outputRegs[], int inputRegs1[], int inputRegs2[],
                       int scratchRegs[], MacroAssembler *_masm) {
   montMul64(outputRegs, inputRegs1, inputRegs2, scratchRegs, false, _masm);
}

// input in Zmm0-Zmm7, the constant is repeated in all slots of Zmm29
// qinvmodR and q are repeated in all slots of Zmm30 and Zmm31, resp.
// Zmm8-Zmm23 used as scratch registers
// result goes to Zmm0-Zmm7
static void montMulByConst128(MacroAssembler *_masm) {
  montmulEven(8, 0, 29, 8, 16, 8, _masm);

  for (int i = 0; i < 8; i++) {
    __ vpshufd(xmm(i),xmm(i), 0xB1, Assembler::AVX_512bit);
  }

  montmulEven(0, 0, 29, 0, 16, 8, _masm);

  for (int i = 0; i < 8; i++) {
    __ evpermt2d(xmm(i), xmm28, xmm(i + 8), Assembler::AVX_512bit);
  }
}

static void sub_add(int subResult[], int addResult[],
                    int input1[], int input2[], MacroAssembler *_masm) {

  for (int i = 0; i < 4; i++) {
    __ evpsubd(xmm(subResult[i]), k0, xmm(input1[i]), xmm(input2[i]), false,
               Assembler::AVX_512bit);
  }

  for (int i = 0; i < 4; i++) {
    __ evpaddd(xmm(addResult[i]), k0, xmm(input1[i]), xmm(input2[i]), false,
               Assembler::AVX_512bit);
  }
}

static int xmm0_3[] = {0, 1, 2, 3};
static int xmm0145[] = {0, 1, 4, 5};
static int xmm0246[] = {0, 2, 4, 6};
static int xmm0426[] = {0, 4, 2, 6};
static int xmm1357[] = {1, 3, 5, 7};
static int xmm1537[] = {1, 5, 3, 7};
static int xmm2367[] = {2, 3, 6, 7};
static int xmm4_7[] = {4, 5, 6, 7};
static int xmm8_11[] = {8, 9, 10, 11};
static int xmm12_15[] = {12, 13, 14, 15};
static int xmm16_19[] = {16, 17, 18, 19};
static int xmm20222426[] = {20, 22, 24, 26};
static int xmm21232527[] = {21, 23, 25, 27};
static int xmm24_27[] = {24, 25, 26, 27};
static int xmm4_20_24[] = {4, 5, 6, 7, 20, 21, 22, 23, 24, 25, 26, 27};
static int xmm16_27[] = {16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27};
static int xmm29_29[] = {29, 29, 29, 29};

// Dilithium NTT function except for the final "normalization" to |coeff| < Q.
// Implements
// static int implDilithiumAlmostNtt(int[] coeffs, int zetas[]) {}
//
// coeffs (int[256]) = c_rarg0
// zetas (int[256]) = c_rarg1
//
//
static address generate_dilithiumAlmostNtt_avx512(StubGenerator *stubgen, MacroAssembler *_masm) {

  __ align(CodeEntryAlignment);
  StubGenStubId stub_id = dilithiumAlmostNtt_id;
  StubCodeMark mark(stubgen, stub_id);
  address start = __ pc();
  __ enter();

  Label L_loop, L_end;

  const Register coeffs = c_rarg0;
  const Register zetas = c_rarg1;

  const Register iterations = c_rarg2;

  const Register dilithiumConsts = r10;
  const Register perms = r11;

  __ lea(perms, ExternalAddress(dilithiumAvx512PermsAddr()));
  __ lea(dilithiumConsts, ExternalAddress(dilithiumAvx512ConstsAddr()));

  __ evmovdqul(xmm28, Address(perms, 0), Assembler::AVX_512bit);
  __ evmovdqul(xmm29, Address(zetas, 0), Assembler::AVX_512bit);
  __ vpbroadcastd(xmm30, Address(dilithiumConsts, 0), Assembler::AVX_512bit); // q^-1 mod 2^32
  __ vpbroadcastd(xmm31, Address(dilithiumConsts, 4), Assembler::AVX_512bit); // q

  // load all coefficients into the vector registers Zmm_0-Zmm_15,
  // 16 coefficients into each
  for (int i = 0; i < 16; i++) {
    __ evmovdqul(xmm(i), Address(coeffs, i * 64), Assembler::AVX_512bit);
  }

  // level 0 and 1 can be done entirely in registers as the zetas on these
  // levels are the same for all the montmuls that we can do in parallel

  // level 0

  montMul64(xmm16_19, xmm8_11, xmm29_29, xmm16_27, _masm);
  sub_add(xmm8_11, xmm0_3, xmm0_3, xmm16_19, _masm);
  montMul64(xmm16_19, xmm12_15, xmm29_29, xmm16_27, _masm);
  __ evmovdqul(xmm29, Address(zetas, 512), Assembler::AVX_512bit); // for level 1
  sub_add(xmm12_15, xmm4_7, xmm4_7, xmm16_19, _masm);

  // level 1

  montMul64(xmm16_19, xmm4_7, xmm29_29, xmm16_27, _masm);
  __ evmovdqul(xmm29, Address(zetas, 768), Assembler::AVX_512bit);
  sub_add(xmm4_7, xmm0_3, xmm0_3, xmm16_19, _masm);
  montMul64(xmm16_19, xmm12_15, xmm29_29, xmm16_27, _masm);
  sub_add(xmm12_15, xmm8_11, xmm8_11, xmm16_19, _masm);

  // levels 2 to 7 are done in 2 batches, by first saving half of the coefficients
  // from level 1 into memory, doing all the level 2 to level 7 computations
  // on the remaining half in the vector registers, saving the result to
  // memory after level 7, then loading back the coefficients that we saved after
  // level 1 and do the same computation with those

  for (int i = 0; i < 16; i++) {
    __ evmovdqul(Address(coeffs, i * 64), xmm(i), Assembler::AVX_512bit);
  }

  __ movl(iterations, 2);

  __ align(OptoLoopAlignment);
  __ BIND(L_loop);

  __ subl(iterations, 1);

  // level 2
  __ evmovdqul(xmm12, Address(zetas, 1024), Assembler::AVX_512bit);
  __ evmovdqul(xmm13, Address(zetas, 1088), Assembler::AVX_512bit);
  __ evmovdqul(xmm14, Address(zetas, 1152), Assembler::AVX_512bit);
  __ evmovdqul(xmm15, Address(zetas, 1216), Assembler::AVX_512bit);

  montMul64(xmm16_19, xmm2367, xmm12_15, xmm16_27, _masm);

  __ evmovdqul(xmm12, Address(zetas, 1536), Assembler::AVX_512bit); // for level 3
  __ evmovdqul(xmm13, Address(zetas, 1600), Assembler::AVX_512bit);
  __ evmovdqul(xmm14, Address(zetas, 1664), Assembler::AVX_512bit);
  __ evmovdqul(xmm15, Address(zetas, 1728), Assembler::AVX_512bit);
  sub_add(xmm2367, xmm0145, xmm0145, xmm16_19, _masm);

  // level 3

  montMul64(xmm16_19, xmm1357, xmm12_15, xmm16_27, _masm);
  sub_add(xmm1357, xmm0246, xmm0246, xmm16_19, _masm);

  // level 4
  __ evmovdqul(xmm16, Address(perms, 64), Assembler::AVX_512bit);
  __ evmovdqul(xmm17, xmm16, Assembler::AVX_512bit);
  __ evmovdqul(xmm18, xmm16, Assembler::AVX_512bit);
  __ evmovdqul(xmm19, xmm16, Assembler::AVX_512bit);
  __ evmovdqul(xmm12, Address(perms, 128), Assembler::AVX_512bit);
  __ evmovdqul(xmm13, xmm12, Assembler::AVX_512bit);
  __ evmovdqul(xmm14, xmm12, Assembler::AVX_512bit);
  __ evmovdqul(xmm15, xmm12, Assembler::AVX_512bit);

  __ evmovdqul(xmm24, Address(zetas, 4 * 512), Assembler::AVX_512bit);
  __ evmovdqul(xmm25, Address(zetas, 4 * 512 + 64), Assembler::AVX_512bit);
  __ evmovdqul(xmm26, Address(zetas, 4 * 512 + 128), Assembler::AVX_512bit);
  __ evmovdqul(xmm27, Address(zetas, 4 * 512 + 192), Assembler::AVX_512bit);

  for (int i = 0; i < 8; i += 2) {
    __ evpermi2d(xmm(i/2 + 16), xmm(i), xmm(i + 1), Assembler::AVX_512bit);
  }
  for (int i = 0; i < 8; i += 2) {
    __ evpermi2d(xmm(i / 2 + 12), xmm(i), xmm(i + 1), Assembler::AVX_512bit);
  }

  montMul64(xmm12_15, xmm12_15, xmm24_27, xmm4_20_24, _masm);
  sub_add(xmm1357, xmm0246, xmm16_19, xmm12_15, _masm);

  // level 5
  __ evmovdqul(xmm16, Address(perms, 192), Assembler::AVX_512bit);
  __ evmovdqul(xmm17, xmm16, Assembler::AVX_512bit);
  __ evmovdqul(xmm18, xmm16, Assembler::AVX_512bit);
  __ evmovdqul(xmm19, xmm16, Assembler::AVX_512bit);
  __ evmovdqul(xmm12, Address(perms, 256), Assembler::AVX_512bit);
  __ evmovdqul(xmm13, xmm12, Assembler::AVX_512bit);
  __ evmovdqul(xmm14, xmm12, Assembler::AVX_512bit);
  __ evmovdqul(xmm15, xmm12, Assembler::AVX_512bit);

  __ evmovdqul(xmm24, Address(zetas, 5 * 512), Assembler::AVX_512bit);
  __ evmovdqul(xmm25, Address(zetas, 5 * 512 + 64), Assembler::AVX_512bit);
  __ evmovdqul(xmm26, Address(zetas, 5 * 512 + 128), Assembler::AVX_512bit);
  __ evmovdqul(xmm27, Address(zetas, 5 * 512 + 192), Assembler::AVX_512bit);

  for (int i = 0; i < 8; i += 2) {
    __ evpermi2d(xmm(i/2 + 16), xmm(i), xmm(i + 1), Assembler::AVX_512bit);
  }
  for (int i = 0; i < 8; i += 2) {
    __ evpermi2d(xmm(i / 2 + 12), xmm(i), xmm(i + 1), Assembler::AVX_512bit);
  }

  montMul64(xmm12_15, xmm12_15, xmm24_27, xmm4_20_24, _masm);
  sub_add(xmm1357, xmm0246, xmm16_19, xmm12_15, _masm);

  // level 6
  __ evmovdqul(xmm16, Address(perms, 320), Assembler::AVX_512bit);
  __ evmovdqul(xmm17, xmm16, Assembler::AVX_512bit);
  __ evmovdqul(xmm18, xmm16, Assembler::AVX_512bit);
  __ evmovdqul(xmm19, xmm16, Assembler::AVX_512bit);
  __ evmovdqul(xmm12, Address(perms, 384), Assembler::AVX_512bit);
  __ evmovdqul(xmm13, xmm12, Assembler::AVX_512bit);
  __ evmovdqul(xmm14, xmm12, Assembler::AVX_512bit);
  __ evmovdqul(xmm15, xmm12, Assembler::AVX_512bit);

  __ evmovdqul(xmm24, Address(zetas, 6 * 512), Assembler::AVX_512bit);
  __ evmovdqul(xmm25, Address(zetas, 6 * 512 + 64), Assembler::AVX_512bit);
  __ evmovdqul(xmm26, Address(zetas, 6 * 512 + 128), Assembler::AVX_512bit);
  __ evmovdqul(xmm27, Address(zetas, 6 * 512 + 192), Assembler::AVX_512bit);

  for (int i = 0; i < 8; i += 2) {
    __ evpermi2d(xmm(i/2 + 16), xmm(i), xmm(i + 1), Assembler::AVX_512bit);
  }
  for (int i = 0; i < 8; i += 2) {
    __ evpermi2d(xmm(i / 2 + 12), xmm(i), xmm(i + 1), Assembler::AVX_512bit);
  }

  montMul64(xmm12_15, xmm12_15, xmm24_27, xmm4_20_24, _masm);
  sub_add(xmm1357, xmm0246, xmm16_19, xmm12_15, _masm);

  // level 7
  __ evmovdqul(xmm16, Address(perms, 448), Assembler::AVX_512bit);
  __ evmovdqul(xmm17, xmm16, Assembler::AVX_512bit);
  __ evmovdqul(xmm18, xmm16, Assembler::AVX_512bit);
  __ evmovdqul(xmm19, xmm16, Assembler::AVX_512bit);
  __ evmovdqul(xmm12, Address(perms, 512), Assembler::AVX_512bit);
  __ evmovdqul(xmm13, xmm12, Assembler::AVX_512bit);
  __ evmovdqul(xmm14, xmm12, Assembler::AVX_512bit);
  __ evmovdqul(xmm15, xmm12, Assembler::AVX_512bit);

  __ evmovdqul(xmm24, Address(zetas, 7 * 512), Assembler::AVX_512bit);
  __ evmovdqul(xmm25, Address(zetas, 7 * 512 + 64), Assembler::AVX_512bit);
  __ evmovdqul(xmm26, Address(zetas, 7 * 512 + 128), Assembler::AVX_512bit);
  __ evmovdqul(xmm27, Address(zetas, 7 * 512 + 192), Assembler::AVX_512bit);

  for (int i = 0; i < 8; i += 2) {
    __ evpermi2d(xmm(i / 2 + 16), xmm(i), xmm(i + 1), Assembler::AVX_512bit);
  }
  for (int i = 0; i < 8; i += 2) {
    __ evpermi2d(xmm(i / 2 + 12), xmm(i), xmm(i + 1), Assembler::AVX_512bit);
  }

  montMul64(xmm12_15, xmm12_15, xmm24_27, xmm4_20_24, true, _masm);

  __ evmovdqul(xmm0, Address(perms, 576), Assembler::AVX_512bit);
  __ evmovdqul(xmm2, xmm0, Assembler::AVX_512bit);
  __ evmovdqul(xmm4, xmm0, Assembler::AVX_512bit);
  __ evmovdqul(xmm6, xmm0, Assembler::AVX_512bit);
  __ evmovdqul(xmm1, Address(perms, 640), Assembler::AVX_512bit);
  __ evmovdqul(xmm3, xmm1, Assembler::AVX_512bit);
  __ evmovdqul(xmm5, xmm1, Assembler::AVX_512bit);
  __ evmovdqul(xmm7, xmm1, Assembler::AVX_512bit);

  sub_add(xmm21232527, xmm20222426, xmm16_19, xmm12_15, _masm);

  for (int i = 0; i < 8; i += 2) {
    __ evpermi2d(xmm(i), xmm(i + 20), xmm(i + 21), Assembler::AVX_512bit);
    __ evpermi2d(xmm(i + 1), xmm(i + 20), xmm(i + 21), Assembler::AVX_512bit);
  }

  __ cmpl(iterations, 0);
  __ jcc(Assembler::equal, L_end);

  for (int i = 0; i < 8; i++) {
    __ evmovdqul(Address(coeffs, i * 64), xmm(i), Assembler::AVX_512bit);
  }

  for (int i = 8; i < 16; i++) {
    __ evmovdqul(xmm(i - 8), Address(coeffs, i * 64), Assembler::AVX_512bit);
  }

  __ addptr(zetas, 256);

  __ jmp(L_loop);

  __ BIND(L_end);

  for (int i = 0; i < 8; i++) {
    __ evmovdqul(Address(coeffs, (i + 8) * 64), xmm(i), Assembler::AVX_512bit);
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
// zetas (int[256]) = c_rarg1
static address generate_dilithiumAlmostInverseNtt_avx512(StubGenerator *stubgen,
                                                         MacroAssembler *_masm) {

  __ align(CodeEntryAlignment);
  StubGenStubId stub_id = dilithiumAlmostInverseNtt_id;
  StubCodeMark mark(stubgen, stub_id);
  address start = __ pc();
  __ enter();

  Label L_loop, L_end;

  const Register coeffs = c_rarg0;
  const Register zetas = c_rarg1;

  const Register iterations = c_rarg2;

  const Register dilithiumConsts = r10;
  const Register perms = r11;

  __ lea(perms, ExternalAddress(dilithiumAvx512PermsAddr()));
  __ lea(dilithiumConsts, ExternalAddress(dilithiumAvx512ConstsAddr()));

  __ evmovdqul(xmm28, Address(perms, 0), Assembler::AVX_512bit);
  __ vpbroadcastd(xmm30, Address(dilithiumConsts, 0), Assembler::AVX_512bit); // q^-1 mod 2^32
  __ vpbroadcastd(xmm31, Address(dilithiumConsts, 4), Assembler::AVX_512bit); // q

  // We do levels 0-6 in two batches, each batch entirely in the vector registers
  for (int i = 0; i < 8; i++) {
    __ evmovdqul(xmm(i), Address(coeffs, i * 64), Assembler::AVX_512bit);
  }

  __ movl(iterations, 2);

  __ align(OptoLoopAlignment);
  __ BIND(L_loop);

  __ subl(iterations, 1);

  // level 0
  __ evmovdqul(xmm8, Address(perms, 704), Assembler::AVX_512bit);
  __ evmovdqul(xmm9, xmm8, Assembler::AVX_512bit);
  __ evmovdqul(xmm10, xmm8, Assembler::AVX_512bit);
  __ evmovdqul(xmm11, xmm8, Assembler::AVX_512bit);
  __ evmovdqul(xmm12, Address(perms, 768), Assembler::AVX_512bit);
  __ evmovdqul(xmm13, xmm12, Assembler::AVX_512bit);
  __ evmovdqul(xmm14, xmm12, Assembler::AVX_512bit);
  __ evmovdqul(xmm15, xmm12, Assembler::AVX_512bit);

  for (int i = 0; i < 8; i += 2) {
    __ evpermi2d(xmm(i / 2 + 8), xmm(i), xmm(i + 1), Assembler::AVX_512bit);
    __ evpermi2d(xmm(i / 2 + 12), xmm(i), xmm(i + 1), Assembler::AVX_512bit);
  }

  __ evmovdqul(xmm4, Address(zetas, 0), Assembler::AVX_512bit);
  __ evmovdqul(xmm5, Address(zetas, 64), Assembler::AVX_512bit);
  __ evmovdqul(xmm6, Address(zetas, 128), Assembler::AVX_512bit);
  __ evmovdqul(xmm7, Address(zetas, 192), Assembler::AVX_512bit);

  sub_add(xmm24_27, xmm0_3, xmm8_11, xmm12_15, _masm);
  montMul64(xmm4_7, xmm4_7, xmm24_27, xmm16_27, true, _masm);

  // level 1
  __ evmovdqul(xmm8, Address(perms, 832), Assembler::AVX_512bit);
  __ evmovdqul(xmm9, xmm8, Assembler::AVX_512bit);
  __ evmovdqul(xmm10, xmm8, Assembler::AVX_512bit);
  __ evmovdqul(xmm11, xmm8, Assembler::AVX_512bit);
  __ evmovdqul(xmm12, Address(perms, 896), Assembler::AVX_512bit);
  __ evmovdqul(xmm13, xmm12, Assembler::AVX_512bit);
  __ evmovdqul(xmm14, xmm12, Assembler::AVX_512bit);
  __ evmovdqul(xmm15, xmm12, Assembler::AVX_512bit);

  for (int i = 0; i < 4; i++) {
    __ evpermi2d(xmm(i + 8), xmm(i), xmm(i + 4), Assembler::AVX_512bit);
    __ evpermi2d(xmm(i + 12), xmm(i), xmm(i + 4), Assembler::AVX_512bit);
  }

  __ evmovdqul(xmm4, Address(zetas, 512), Assembler::AVX_512bit);
  __ evmovdqul(xmm5, Address(zetas, 512 + 64), Assembler::AVX_512bit);
  __ evmovdqul(xmm6, Address(zetas, 512 + 128), Assembler::AVX_512bit);
  __ evmovdqul(xmm7, Address(zetas, 512 + 192), Assembler::AVX_512bit);

  sub_add(xmm24_27, xmm0_3, xmm8_11, xmm12_15, _masm);
  montMul64(xmm4_7, xmm24_27, xmm4_7, xmm16_27, _masm);

  // level 2
  __ evmovdqul(xmm8, Address(perms, 960), Assembler::AVX_512bit);
  __ evmovdqul(xmm9, xmm8, Assembler::AVX_512bit);
  __ evmovdqul(xmm10, xmm8, Assembler::AVX_512bit);
  __ evmovdqul(xmm11, xmm8, Assembler::AVX_512bit);
  __ evmovdqul(xmm12, Address(perms, 1024), Assembler::AVX_512bit);
  __ evmovdqul(xmm13, xmm12, Assembler::AVX_512bit);
  __ evmovdqul(xmm14, xmm12, Assembler::AVX_512bit);
  __ evmovdqul(xmm15, xmm12, Assembler::AVX_512bit);

  for (int i = 0; i < 4; i++) {
    __ evpermi2d(xmm(i + 8), xmm(i), xmm(i + 4), Assembler::AVX_512bit);
    __ evpermi2d(xmm(i + 12), xmm(i), xmm(i + 4), Assembler::AVX_512bit);
  }

  __ evmovdqul(xmm4, Address(zetas, 2 * 512), Assembler::AVX_512bit);
  __ evmovdqul(xmm5, Address(zetas, 2 * 512 + 64), Assembler::AVX_512bit);
  __ evmovdqul(xmm6, Address(zetas, 2 * 512 + 128), Assembler::AVX_512bit);
  __ evmovdqul(xmm7, Address(zetas, 2 * 512 + 192), Assembler::AVX_512bit);

  sub_add(xmm24_27, xmm0_3, xmm8_11, xmm12_15, _masm);
  montMul64(xmm4_7, xmm24_27, xmm4_7, xmm16_27, _masm);

  // level 3
  __ evmovdqul(xmm8, Address(perms, 1088), Assembler::AVX_512bit);
  __ evmovdqul(xmm9, xmm8, Assembler::AVX_512bit);
  __ evmovdqul(xmm10, xmm8, Assembler::AVX_512bit);
  __ evmovdqul(xmm11, xmm8, Assembler::AVX_512bit);
  __ evmovdqul(xmm12, Address(perms, 1152), Assembler::AVX_512bit);
  __ evmovdqul(xmm13, xmm12, Assembler::AVX_512bit);
  __ evmovdqul(xmm14, xmm12, Assembler::AVX_512bit);
  __ evmovdqul(xmm15, xmm12, Assembler::AVX_512bit);

  for (int i = 0; i < 4; i++) {
    __ evpermi2d(xmm(i + 8), xmm(i), xmm(i + 4), Assembler::AVX_512bit);
    __ evpermi2d(xmm(i + 12), xmm(i), xmm(i + 4), Assembler::AVX_512bit);
  }

  __ evmovdqul(xmm4, Address(zetas, 3 * 512), Assembler::AVX_512bit);
  __ evmovdqul(xmm5, Address(zetas, 3 * 512 + 64), Assembler::AVX_512bit);
  __ evmovdqul(xmm6, Address(zetas, 3 * 512 + 128), Assembler::AVX_512bit);
  __ evmovdqul(xmm7, Address(zetas, 3 * 512 + 192), Assembler::AVX_512bit);

  sub_add(xmm24_27, xmm0_3, xmm8_11, xmm12_15, _masm);
  montMul64(xmm4_7, xmm24_27, xmm4_7, xmm16_27, _masm);

  // level 4
  __ evmovdqul(xmm8, Address(perms, 1216), Assembler::AVX_512bit);
  __ evmovdqul(xmm9, xmm8, Assembler::AVX_512bit);
  __ evmovdqul(xmm10, xmm8, Assembler::AVX_512bit);
  __ evmovdqul(xmm11, xmm8, Assembler::AVX_512bit);
  __ evmovdqul(xmm12, Address(perms, 1280), Assembler::AVX_512bit);
  __ evmovdqul(xmm13, xmm12, Assembler::AVX_512bit);
  __ evmovdqul(xmm14, xmm12, Assembler::AVX_512bit);
  __ evmovdqul(xmm15, xmm12, Assembler::AVX_512bit);

  for (int i = 0; i < 4; i++) {
    __ evpermi2d(xmm(i + 8), xmm(i), xmm(i + 4), Assembler::AVX_512bit);
    __ evpermi2d(xmm(i + 12), xmm(i), xmm(i + 4), Assembler::AVX_512bit);
  }
  __ evmovdqul(xmm4, Address(zetas, 4 * 512), Assembler::AVX_512bit);
  __ evmovdqul(xmm5, Address(zetas, 4 * 512 + 64), Assembler::AVX_512bit);
  __ evmovdqul(xmm6, Address(zetas, 4 * 512 + 128), Assembler::AVX_512bit);
  __ evmovdqul(xmm7, Address(zetas, 4 * 512 + 192), Assembler::AVX_512bit);

  sub_add(xmm24_27, xmm0_3, xmm8_11, xmm12_15, _masm);
  montMul64(xmm4_7, xmm24_27, xmm4_7, xmm16_27, _masm);

  // level 5
  __ evmovdqul(xmm12, Address(zetas, 5 * 512), Assembler::AVX_512bit);
  __ evmovdqul(xmm13, Address(zetas, 5 * 512 + 64), Assembler::AVX_512bit);
  __ evmovdqul(xmm14, Address(zetas, 5 * 512 + 128), Assembler::AVX_512bit);
  __ evmovdqul(xmm15, Address(zetas, 5 * 512 + 192), Assembler::AVX_512bit);

  sub_add(xmm8_11, xmm0_3, xmm0426, xmm1537, _masm);
  montMul64(xmm4_7, xmm8_11, xmm12_15, xmm16_27, _masm);

  // level 6
  __ evmovdqul(xmm12, Address(zetas, 6 * 512), Assembler::AVX_512bit);
  __ evmovdqul(xmm13, Address(zetas, 6 * 512 + 64), Assembler::AVX_512bit);
  __ evmovdqul(xmm14, Address(zetas, 6 * 512 + 128), Assembler::AVX_512bit);
  __ evmovdqul(xmm15, Address(zetas, 6 * 512 + 192), Assembler::AVX_512bit);

  sub_add(xmm8_11, xmm0_3, xmm0145, xmm2367, _masm);
  montMul64(xmm4_7, xmm8_11, xmm12_15, xmm16_27, _masm);

  for (int i = 0; i < 4; i++) {
    __ evpermt2d(xmm(i + 4), xmm28, xmm(i + 20), Assembler::AVX_512bit);
  }

  __ cmpl(iterations, 0);
  __ jcc(Assembler::equal, L_end);

  // save the coefficients of the first batch, adjust the zetas
  // and load the second batch of coefficients
  for (int i = 0; i < 8; i++) {
    __ evmovdqul(Address(coeffs, i * 64), xmm(i), Assembler::AVX_512bit);
  }

  __ addptr(zetas, 256);

  for (int i = 0; i < 8; i++) {
    __ evmovdqul(xmm(i), Address(coeffs, i * 64 + 512), Assembler::AVX_512bit);
  }

  __ jmp(L_loop);

  __ BIND(L_end);

  // load the coeffs of the first batch of coefficients that were saved after
  // level 6 into Zmm_8-Zmm_15 and do the last level entirely in the vector
  // registers
  for (int i = 0; i < 8; i++) {
    __ evmovdqul(xmm(i + 8), Address(coeffs, i * 64), Assembler::AVX_512bit);
  }

  // level 7

  __ evmovdqul(xmm29, Address(zetas, 7 * 512), Assembler::AVX_512bit);

  for (int i = 0; i < 8; i++) {
    __ evpaddd(xmm(i + 16), k0, xmm(i), xmm(i + 8), false, Assembler::AVX_512bit);
  }

  for (int i = 0; i < 8; i++) {
    __ evpsubd(xmm(i), k0, xmm(i + 8), xmm(i), false, Assembler::AVX_512bit);
  }

  for (int i = 0; i < 8; i++) {
    __ evmovdqul(Address(coeffs, i * 64), xmm(i + 16), Assembler::AVX_512bit);
  }
  montMulByConst128(_masm);

  for (int i = 0; i < 8; i++) {
    __ evmovdqul(Address(coeffs, i * 64 + 512), xmm(i), Assembler::AVX_512bit);
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
static address generate_dilithiumNttMult_avx512(StubGenerator *stubgen,
                                                MacroAssembler *_masm) {

  __ align(CodeEntryAlignment);
  StubGenStubId stub_id = dilithiumNttMult_id;
  StubCodeMark mark(stubgen, stub_id);
  address start = __ pc();
  __ enter();

  Label L_loop;

  const Register result = c_rarg0;
  const Register poly1 = c_rarg1;
  const Register poly2 = c_rarg2;

  const Register dilithiumConsts = c_rarg3; // not used for argument
  const Register perms = r10;
  const Register len = r11;

  __ lea(dilithiumConsts, ExternalAddress(dilithiumAvx512ConstsAddr()));
  __ lea(perms, ExternalAddress(dilithiumAvx512PermsAddr()));

  __ vpbroadcastd(xmm30, Address(dilithiumConsts, 0), Assembler::AVX_512bit); // q^-1 mod 2^32
  __ vpbroadcastd(xmm31, Address(dilithiumConsts, 4), Assembler::AVX_512bit); // q
  __ vpbroadcastd(xmm29, Address(dilithiumConsts, 12), Assembler::AVX_512bit); // 2^64 mod q
  __ evmovdqul(xmm28, Address(perms, 0), Assembler::AVX_512bit);

  __ movl(len, 4);

  __ align(OptoLoopAlignment);
  __ BIND(L_loop);

  for (int i = 0; i < 4; i++) {
    __ evmovdqul(xmm(i + 4), Address(poly2, i * 64), Assembler::AVX_512bit);
    __ evmovdqul(xmm(i), Address(poly1, i * 64), Assembler::AVX_512bit);
  }

  montMul64(xmm4_7, xmm4_7, xmm29_29, xmm16_27, _masm);

  montMul64(xmm0_3, xmm0_3, xmm4_7, xmm16_27, true, _masm);

  for (int i = 0; i < 4; i++) {
    __ evmovdqul(Address(result, i * 64), xmm(i), Assembler::AVX_512bit);
  }
  __ subl(len, 1);
  __ addptr(poly1, 256);
  __ addptr(poly2, 256);
  __ addptr(result, 256);
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
static address generate_dilithiumMontMulByConstant_avx512(StubGenerator *stubgen,
                                                          MacroAssembler *_masm) {

  __ align(CodeEntryAlignment);
  StubGenStubId stub_id = dilithiumMontMulByConstant_id;
  StubCodeMark mark(stubgen, stub_id);
  address start = __ pc();
  __ enter();

  Label L_loop;

  const Register coeffs = c_rarg0;
  const Register constant = c_rarg1;

  const Register perms = c_rarg2; // not used for argument
  const Register dilithiumConsts = c_rarg3; // not used for argument
  const Register len = r10;

  __ lea(dilithiumConsts, ExternalAddress(dilithiumAvx512ConstsAddr()));
  __ lea(perms, ExternalAddress(dilithiumAvx512PermsAddr()));

  __ vpbroadcastd(xmm30, Address(dilithiumConsts, 0), Assembler::AVX_512bit); // q^-1 mod 2^32
  __ vpbroadcastd(xmm31, Address(dilithiumConsts, 4), Assembler::AVX_512bit); // q
  __ evmovdqul(xmm28, Address(perms, 0), Assembler::AVX_512bit);

  __ evpbroadcastd(xmm29, constant, Assembler::AVX_512bit); // constant multiplier

  __ movl(len, 2);

  __ align(OptoLoopAlignment);
  __ BIND(L_loop);

  for (int i = 0; i < 8; i++) {
    __ evmovdqul(xmm(i), Address(coeffs, i * 64), Assembler::AVX_512bit);
  }

  montMulByConst128(_masm);

  for (int i = 0; i < 8; i++) {
    __ evmovdqul(Address(coeffs, i * 64), xmm(i), Assembler::AVX_512bit);
  }

  __ subl(len, 1);
  __ addptr(coeffs, 512);
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
static address generate_dilithiumDecomposePoly_avx512(StubGenerator *stubgen,
                                                      MacroAssembler *_masm) {

  __ align(CodeEntryAlignment);
  StubGenStubId stub_id = dilithiumDecomposePoly_id;
  StubCodeMark mark(stubgen, stub_id);
  address start = __ pc();
  __ enter();

  Label L_loop;

  const Register input = c_rarg0;
  const Register lowPart = c_rarg1;
  const Register highPart = c_rarg2;
  const Register twoGamma2 = c_rarg3;

  const Register len = c_rarg3; // len is used only after twoGamma2 is consumed
  const Register dilithiumConsts = r10;
  const Register tmp = r11;

  __ lea(dilithiumConsts, ExternalAddress(dilithiumAvx512ConstsAddr()));

  __ xorl(tmp, tmp);
  __ evpbroadcastd(xmm24, tmp, Assembler::AVX_512bit); // 0
  __ addl(tmp, 1);
  __ evpbroadcastd(xmm25, tmp, Assembler::AVX_512bit); // 1
  __ vpbroadcastd(xmm30, Address(dilithiumConsts, 4), Assembler::AVX_512bit); // q
  __ vpbroadcastd(xmm31, Address(dilithiumConsts, 16), Assembler::AVX_512bit); // addend for mod q reduce

  __ evpbroadcastd(xmm28, twoGamma2, Assembler::AVX_512bit); // 2 * gamma2

  #ifndef _WIN64
    const Register multiplier = c_rarg4;
  #else
    const Address multiplier_mem(rbp, 6 * wordSize);
    const Register multiplier = c_rarg3;
    __ movptr(multiplier, multiplier_mem);
  #endif
  __ evpbroadcastd(xmm29, multiplier, Assembler::AVX_512bit); // multiplier for mod 2 * gamma2 reduce

  __ evpsubd(xmm26, k0, xmm30, xmm25, false, Assembler::AVX_512bit); // q - 1
  __ evpsrad(xmm27, k0, xmm28, 1, false, Assembler::AVX_512bit); // gamma2

  __ movl(len, 1024);

  __ align(OptoLoopAlignment);
  __ BIND(L_loop);

  __ evmovdqul(xmm0, Address(input, 0), Assembler::AVX_512bit);
  __ evmovdqul(xmm1, Address(input, 64), Assembler::AVX_512bit);
  __ evmovdqul(xmm2, Address(input, 128), Assembler::AVX_512bit);
  __ evmovdqul(xmm3, Address(input, 192), Assembler::AVX_512bit);

  __ addptr(input, 256);

  // rplus in xmm0
  // rplus = rplus - ((rplus + 5373807) >> 23) * dilithium_q;
  __ evpaddd(xmm4, k0, xmm0, xmm31, false, Assembler::AVX_512bit);
  __ evpaddd(xmm5, k0, xmm1, xmm31, false, Assembler::AVX_512bit);
  __ evpaddd(xmm6, k0, xmm2, xmm31, false, Assembler::AVX_512bit);
  __ evpaddd(xmm7, k0, xmm3, xmm31, false, Assembler::AVX_512bit);

  __ evpsrad(xmm4, k0, xmm4, 23, false, Assembler::AVX_512bit);
  __ evpsrad(xmm5, k0, xmm5, 23, false, Assembler::AVX_512bit);
  __ evpsrad(xmm6, k0, xmm6, 23, false, Assembler::AVX_512bit);
  __ evpsrad(xmm7, k0, xmm7, 23, false, Assembler::AVX_512bit);

  __ evpmulld(xmm4, k0, xmm4, xmm30, false, Assembler::AVX_512bit);
  __ evpmulld(xmm5, k0, xmm5, xmm30, false, Assembler::AVX_512bit);
  __ evpmulld(xmm6, k0, xmm6, xmm30, false, Assembler::AVX_512bit);
  __ evpmulld(xmm7, k0, xmm7, xmm30, false, Assembler::AVX_512bit);

  __ evpsubd(xmm0, k0, xmm0, xmm4, false, Assembler::AVX_512bit);
  __ evpsubd(xmm1, k0, xmm1, xmm5, false, Assembler::AVX_512bit);
  __ evpsubd(xmm2, k0, xmm2, xmm6, false, Assembler::AVX_512bit);
  __ evpsubd(xmm3, k0, xmm3, xmm7, false, Assembler::AVX_512bit);

  // rplus in xmm0
  // rplus = rplus + ((rplus >> 31) & dilithium_q);
  __ evpsrad(xmm4, k0, xmm0, 31, false, Assembler::AVX_512bit);
  __ evpsrad(xmm5, k0, xmm1, 31, false, Assembler::AVX_512bit);
  __ evpsrad(xmm6, k0, xmm2, 31, false, Assembler::AVX_512bit);
  __ evpsrad(xmm7, k0, xmm3, 31, false, Assembler::AVX_512bit);

  __ evpandd(xmm4, k0, xmm4, xmm30, false, Assembler::AVX_512bit);
  __ evpandd(xmm5, k0, xmm5, xmm30, false, Assembler::AVX_512bit);
  __ evpandd(xmm6, k0, xmm6, xmm30, false, Assembler::AVX_512bit);
  __ evpandd(xmm7, k0, xmm7, xmm30, false, Assembler::AVX_512bit);

  __ evpaddd(xmm0, k0, xmm0, xmm4, false, Assembler::AVX_512bit);
  __ evpaddd(xmm1, k0, xmm1, xmm5, false, Assembler::AVX_512bit);
  __ evpaddd(xmm2, k0, xmm2, xmm6, false, Assembler::AVX_512bit);
  __ evpaddd(xmm3, k0, xmm3, xmm7, false, Assembler::AVX_512bit);

  // rplus in xmm0
  // int quotient = (rplus * multiplier) >> 22;
  __ evpmulld(xmm4, k0, xmm0, xmm29, false, Assembler::AVX_512bit);
  __ evpmulld(xmm5, k0, xmm1, xmm29, false, Assembler::AVX_512bit);
  __ evpmulld(xmm6, k0, xmm2, xmm29, false, Assembler::AVX_512bit);
  __ evpmulld(xmm7, k0, xmm3, xmm29, false, Assembler::AVX_512bit);

  __ evpsrad(xmm4, k0, xmm4, 22, false, Assembler::AVX_512bit);
  __ evpsrad(xmm5, k0, xmm5, 22, false, Assembler::AVX_512bit);
  __ evpsrad(xmm6, k0, xmm6, 22, false, Assembler::AVX_512bit);
  __ evpsrad(xmm7, k0, xmm7, 22, false, Assembler::AVX_512bit);

  // quotient in xmm4
  // int r0 = rplus - quotient * twoGamma2;
  __ evpmulld(xmm8, k0, xmm4, xmm28, false, Assembler::AVX_512bit);
  __ evpmulld(xmm9, k0, xmm5, xmm28, false, Assembler::AVX_512bit);
  __ evpmulld(xmm10, k0, xmm6, xmm28, false, Assembler::AVX_512bit);
  __ evpmulld(xmm11, k0, xmm7, xmm28, false, Assembler::AVX_512bit);

  __ evpsubd(xmm8, k0, xmm0, xmm8, false, Assembler::AVX_512bit);
  __ evpsubd(xmm9, k0, xmm1, xmm9, false, Assembler::AVX_512bit);
  __ evpsubd(xmm10, k0, xmm2, xmm10, false, Assembler::AVX_512bit);
  __ evpsubd(xmm11, k0, xmm3, xmm11, false, Assembler::AVX_512bit);

  // r0 in xmm8
  // int mask = (twoGamma2 - r0) >> 22;
  __ evpsubd(xmm12, k0, xmm28, xmm8, false, Assembler::AVX_512bit);
  __ evpsubd(xmm13, k0, xmm28, xmm9, false, Assembler::AVX_512bit);
  __ evpsubd(xmm14, k0, xmm28, xmm10, false, Assembler::AVX_512bit);
  __ evpsubd(xmm15, k0, xmm28, xmm11, false, Assembler::AVX_512bit);

  __ evpsrad(xmm12, k0, xmm12, 22, false, Assembler::AVX_512bit);
  __ evpsrad(xmm13, k0, xmm13, 22, false, Assembler::AVX_512bit);
  __ evpsrad(xmm14, k0, xmm14, 22, false, Assembler::AVX_512bit);
  __ evpsrad(xmm15, k0, xmm15, 22, false, Assembler::AVX_512bit);

  // mask in xmm12
  // r0 -= (mask & twoGamma2);
  __ evpandd(xmm16, k0, xmm12, xmm28, false, Assembler::AVX_512bit);
  __ evpandd(xmm17, k0, xmm13, xmm28, false, Assembler::AVX_512bit);
  __ evpandd(xmm18, k0, xmm14, xmm28, false, Assembler::AVX_512bit);
  __ evpandd(xmm19, k0, xmm15, xmm28, false, Assembler::AVX_512bit);

  __ evpsubd(xmm8, k0, xmm8, xmm16, false, Assembler::AVX_512bit);
  __ evpsubd(xmm9, k0, xmm9, xmm17, false, Assembler::AVX_512bit);
  __ evpsubd(xmm10, k0, xmm10, xmm18, false, Assembler::AVX_512bit);
  __ evpsubd(xmm11, k0, xmm11, xmm19, false, Assembler::AVX_512bit);

  // r0 in xmm8
  // quotient += (mask & 1);
  __ evpandd(xmm16, k0, xmm12, xmm25, false, Assembler::AVX_512bit);
  __ evpandd(xmm17, k0, xmm13, xmm25, false, Assembler::AVX_512bit);
  __ evpandd(xmm18, k0, xmm14, xmm25, false, Assembler::AVX_512bit);
  __ evpandd(xmm19, k0, xmm15, xmm25, false, Assembler::AVX_512bit);

  __ evpaddd(xmm4, k0, xmm4, xmm16, false, Assembler::AVX_512bit);
  __ evpaddd(xmm5, k0, xmm5, xmm17, false, Assembler::AVX_512bit);
  __ evpaddd(xmm6, k0, xmm6, xmm18, false, Assembler::AVX_512bit);
  __ evpaddd(xmm7, k0, xmm7, xmm19, false, Assembler::AVX_512bit);

  // mask = (twoGamma2 / 2 - r0) >> 31;
  __ evpsubd(xmm12, k0, xmm27, xmm8, false, Assembler::AVX_512bit);
  __ evpsubd(xmm13, k0, xmm27, xmm9, false, Assembler::AVX_512bit);
  __ evpsubd(xmm14, k0, xmm27, xmm10, false, Assembler::AVX_512bit);
  __ evpsubd(xmm15, k0, xmm27, xmm11, false, Assembler::AVX_512bit);

  __ evpsrad(xmm12, k0, xmm12, 31, false, Assembler::AVX_512bit);
  __ evpsrad(xmm13, k0, xmm13, 31, false, Assembler::AVX_512bit);
  __ evpsrad(xmm14, k0, xmm14, 31, false, Assembler::AVX_512bit);
  __ evpsrad(xmm15, k0, xmm15, 31, false, Assembler::AVX_512bit);

  // r0 -= (mask & twoGamma2);
  __ evpandd(xmm16, k0, xmm12, xmm28, false, Assembler::AVX_512bit);
  __ evpandd(xmm17, k0, xmm13, xmm28, false, Assembler::AVX_512bit);
  __ evpandd(xmm18, k0, xmm14, xmm28, false, Assembler::AVX_512bit);
  __ evpandd(xmm19, k0, xmm15, xmm28, false, Assembler::AVX_512bit);

  __ evpsubd(xmm8, k0, xmm8, xmm16, false, Assembler::AVX_512bit);
  __ evpsubd(xmm9, k0, xmm9, xmm17, false, Assembler::AVX_512bit);
  __ evpsubd(xmm10, k0, xmm10, xmm18, false, Assembler::AVX_512bit);
  __ evpsubd(xmm11, k0, xmm11, xmm19, false, Assembler::AVX_512bit);

  // r0 in xmm8
  // quotient += (mask & 1);
  __ evpandd(xmm16, k0, xmm12, xmm25, false, Assembler::AVX_512bit);
  __ evpandd(xmm17, k0, xmm13, xmm25, false, Assembler::AVX_512bit);
  __ evpandd(xmm18, k0, xmm14, xmm25, false, Assembler::AVX_512bit);
  __ evpandd(xmm19, k0, xmm15, xmm25, false, Assembler::AVX_512bit);

  __ evpaddd(xmm4, k0, xmm4, xmm16, false, Assembler::AVX_512bit);
  __ evpaddd(xmm5, k0, xmm5, xmm17, false, Assembler::AVX_512bit);
  __ evpaddd(xmm6, k0, xmm6, xmm18, false, Assembler::AVX_512bit);
  __ evpaddd(xmm7, k0, xmm7, xmm19, false, Assembler::AVX_512bit);

  // quotient in xmm4
  // int r1 = rplus - r0 - (dilithium_q - 1);
  __ evpsubd(xmm16, k0, xmm0, xmm8, false, Assembler::AVX_512bit);
  __ evpsubd(xmm17, k0, xmm1, xmm9, false, Assembler::AVX_512bit);
  __ evpsubd(xmm18, k0, xmm2, xmm10, false, Assembler::AVX_512bit);
  __ evpsubd(xmm19, k0, xmm3, xmm11, false, Assembler::AVX_512bit);

  __ evpsubd(xmm16, k0, xmm16, xmm26, false, Assembler::AVX_512bit);
  __ evpsubd(xmm17, k0, xmm17, xmm26, false, Assembler::AVX_512bit);
  __ evpsubd(xmm18, k0, xmm18, xmm26, false, Assembler::AVX_512bit);
  __ evpsubd(xmm19, k0, xmm19, xmm26, false, Assembler::AVX_512bit);

  // r1 in xmm16
  // r1 = (r1 | (-r1)) >> 31; // 0 if rplus - r0 == (dilithium_q - 1), -1 otherwise
  __ evpsubd(xmm20, k0, xmm24, xmm16, false, Assembler::AVX_512bit);
  __ evpsubd(xmm21, k0, xmm24, xmm17, false, Assembler::AVX_512bit);
  __ evpsubd(xmm22, k0, xmm24, xmm18, false, Assembler::AVX_512bit);
  __ evpsubd(xmm23, k0, xmm24, xmm19, false, Assembler::AVX_512bit);

  __ evporq(xmm16, k0, xmm16, xmm20, false, Assembler::AVX_512bit);
  __ evporq(xmm17, k0, xmm17, xmm21, false, Assembler::AVX_512bit);
  __ evporq(xmm18, k0, xmm18, xmm22, false, Assembler::AVX_512bit);
  __ evporq(xmm19, k0, xmm19, xmm23, false, Assembler::AVX_512bit);

  __ evpsubd(xmm12, k0, xmm24, xmm25, false, Assembler::AVX_512bit); // -1

  __ evpsrad(xmm0, k0, xmm16, 31, false, Assembler::AVX_512bit);
  __ evpsrad(xmm1, k0, xmm17, 31, false, Assembler::AVX_512bit);
  __ evpsrad(xmm2, k0, xmm18, 31, false, Assembler::AVX_512bit);
  __ evpsrad(xmm3, k0, xmm19, 31, false, Assembler::AVX_512bit);

  // r1 in xmm0
  // r0 += ~r1;
  __ evpxorq(xmm20, k0, xmm0, xmm12, false, Assembler::AVX_512bit);
  __ evpxorq(xmm21, k0, xmm1, xmm12, false, Assembler::AVX_512bit);
  __ evpxorq(xmm22, k0, xmm2, xmm12, false, Assembler::AVX_512bit);
  __ evpxorq(xmm23, k0, xmm3, xmm12, false, Assembler::AVX_512bit);

  __ evpaddd(xmm8, k0, xmm8, xmm20, false, Assembler::AVX_512bit);
  __ evpaddd(xmm9, k0, xmm9, xmm21, false, Assembler::AVX_512bit);
  __ evpaddd(xmm10, k0, xmm10, xmm22, false, Assembler::AVX_512bit);
  __ evpaddd(xmm11, k0, xmm11, xmm23, false, Assembler::AVX_512bit);

  // r0 in xmm8
  // r1 = r1 & quotient;
  __ evpandd(xmm0, k0, xmm4, xmm0, false, Assembler::AVX_512bit);
  __ evpandd(xmm1, k0, xmm5, xmm1, false, Assembler::AVX_512bit);
  __ evpandd(xmm2, k0, xmm6, xmm2, false, Assembler::AVX_512bit);
  __ evpandd(xmm3, k0, xmm7, xmm3, false, Assembler::AVX_512bit);

  // r1 in xmm0
  // lowPart[m] = r0;
  // highPart[m] = r1;
  __ evmovdqul(Address(highPart, 0), xmm0, Assembler::AVX_512bit);
  __ evmovdqul(Address(highPart, 64), xmm1, Assembler::AVX_512bit);
  __ evmovdqul(Address(highPart, 128), xmm2, Assembler::AVX_512bit);
  __ evmovdqul(Address(highPart, 192), xmm3, Assembler::AVX_512bit);

  __ evmovdqul(Address(lowPart, 0), xmm8, Assembler::AVX_512bit);
  __ evmovdqul(Address(lowPart, 64), xmm9, Assembler::AVX_512bit);
  __ evmovdqul(Address(lowPart, 128), xmm10, Assembler::AVX_512bit);
  __ evmovdqul(Address(lowPart, 192), xmm11, Assembler::AVX_512bit);

  __ subl(len, 256);
  __ addptr(highPart, 256);
  __ addptr(lowPart, 256);
  __ cmpl(len, 0);
  __ jcc(Assembler::notEqual, L_loop);

  __ leave(); // required for proper stackwalking of RuntimeStub frame
  __ mov64(rax, 0); // return 0
  __ ret(0);

  return start;
}

void StubGenerator::generate_dilithium_stubs() {
  // Generate Dilithium intrinsics code
  if (UseDilithiumIntrinsics) {
      StubRoutines::_dilithiumAlmostNtt =
        generate_dilithiumAlmostNtt_avx512(this, _masm);
      StubRoutines::_dilithiumAlmostInverseNtt =
        generate_dilithiumAlmostInverseNtt_avx512(this, _masm);
      StubRoutines::_dilithiumNttMult =
        generate_dilithiumNttMult_avx512(this, _masm);
      StubRoutines::_dilithiumMontMulByConstant =
        generate_dilithiumMontMulByConstant_avx512(this, _masm);
      StubRoutines::_dilithiumDecomposePoly =
        generate_dilithiumDecomposePoly_avx512(this, _masm);
  }
}
