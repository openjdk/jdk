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

#define XMMBYTES 64

// Constants
//
ATTRIBUTE_ALIGNED(64) static const uint32_t dilithiumAvx512Consts[] = {
    58728449, // montQInvModR
    8380417, // dilithium_q
    2365951, // montRSquareModQ
    5373807 // Barrett addend for modular reduction
};

const int montQInvModRIdx = 0;
const int dilithium_qIdx = 4;
const int montRSquareModQIdx = 8;
const int barrettAddendIdx = 12;

static address dilithiumAvx512ConstsAddr(int offset) {
  return ((address) dilithiumAvx512Consts) + offset;
}

const Register scratch = r10;
const XMMRegister montMulPerm = xmm28;
const XMMRegister montQInvModR = xmm30;
const XMMRegister dilithium_q = xmm31;


ATTRIBUTE_ALIGNED(64) static const uint32_t dilithiumAvx512Perms[] = {
     // collect montmul results into the destination register
    17, 1, 19, 3, 21, 5, 23, 7, 25, 9, 27, 11, 29, 13, 31, 15,
    // ntt
    // level 4
    0, 1, 2, 3, 4, 5, 6, 7, 16, 17, 18, 19, 20, 21, 22, 23,
    8, 9, 10, 11, 12, 13, 14, 15, 24, 25, 26, 27, 28, 29, 30, 31,
    // level 5
    0, 1, 2, 3, 16, 17, 18, 19, 8, 9, 10, 11, 24, 25, 26, 27,
    4, 5, 6, 7, 20, 21, 22, 23, 12, 13, 14, 15, 28, 29, 30, 31,
    // level 6
    0, 1, 16, 17, 4, 5, 20, 21, 8, 9, 24, 25, 12, 13, 28, 29,
    2, 3, 18, 19, 6, 7, 22, 23, 10, 11, 26, 27, 14, 15, 30, 31,
    // level 7
    0, 16, 2, 18, 4, 20, 6, 22, 8, 24, 10, 26, 12, 28, 14, 30,
    1, 17, 3, 19, 5, 21, 7, 23, 9, 25, 11, 27, 13, 29, 15, 31,
    0, 16, 1, 17, 2, 18, 3, 19, 4, 20, 5, 21, 6, 22, 7, 23,
    8, 24, 9, 25, 10, 26, 11, 27, 12, 28, 13, 29, 14, 30, 15, 31,

    // ntt inverse
    // level 0
    0, 2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26, 28, 30,
    1, 3, 5, 7, 9, 11, 13, 15, 17, 19, 21, 23, 25, 27, 29, 31,
    // level 1
    0, 16, 2, 18, 4, 20, 6, 22, 8, 24, 10, 26, 12, 28, 14, 30,
    1, 17, 3, 19, 5, 21, 7, 23, 9, 25, 11, 27, 13, 29, 15, 31,
    // level 2
    0, 1, 16, 17, 4, 5, 20, 21, 8, 9, 24, 25, 12, 13, 28, 29,
    2, 3, 18, 19, 6, 7, 22, 23, 10, 11, 26, 27, 14, 15, 30, 31,
    // level 3
    0, 1, 2, 3, 16, 17, 18, 19, 8, 9, 10, 11, 24, 25, 26, 27,
    4, 5, 6, 7, 20, 21, 22, 23, 12, 13, 14, 15, 28, 29, 30, 31,
    // level 4
    0, 1, 2, 3, 4, 5, 6, 7, 16, 17, 18, 19, 20, 21, 22, 23,
    8, 9, 10, 11, 12, 13, 14, 15, 24, 25, 26, 27, 28, 29, 30, 31
};

const int montMulPermsIdx = 0;
const int nttL4PermsIdx = 64;
const int nttL5PermsIdx = 192;
const int nttL6PermsIdx = 320;
const int nttL7PermsIdx = 448;
const int nttInvL0PermsIdx = 704;
const int nttInvL1PermsIdx = 832;
const int nttInvL2PermsIdx = 960;
const int nttInvL3PermsIdx = 1088;
const int nttInvL4PermsIdx = 1216;

static address dilithiumAvx512PermsAddr() {
  return (address) dilithiumAvx512Perms;
}

// We do Montgomery multiplications of two vectors of 16 ints each in 4 steps:
// 1. Do the multiplications of the corresponding even numbered slots into
//    the odd numbered slots of a third register.
// 2. Swap the even and odd numbered slots of the original input registers.
// 3. Similar to step 1, but into a different output register.
// 4. Combine the outputs of step 1 and step 3 into the output of the Montgomery
//    multiplication.
// (For levels 0-6 in the Ntt and levels 1-7 of the inverse Ntt we only swap the
// odd-even slots of the first multiplicand as in the second (zetas) the
// odd slots contain the same number as the corresponding even one.)
// The indexes of the registers to be multiplied
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
    __ vpmulld(xmm(scratchRegs[i + 4]), xmm(scratchRegs[i]), montQInvModR,
               Assembler::AVX_512bit);
  }
  for (int i = 0; i < 4; i++) {
    __ vpmuldq(xmm(scratchRegs[i + 4]), xmm(scratchRegs[i + 4]), dilithium_q,
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
    __ vpmulld(xmm(scratchRegs[i + 8]), xmm(scratchRegs[i]), montQInvModR,
               Assembler::AVX_512bit);
  }
  for (int i = 0; i < 4; i++) {
    __ vpmuldq(xmm(scratchRegs[i + 8]), xmm(scratchRegs[i + 8]), dilithium_q,
               Assembler::AVX_512bit);
  }
  for (int i = 0; i < 4; i++) {
    __ evpsubd(xmm(outputRegs[i]), k0, xmm(scratchRegs[i]),
               xmm(scratchRegs[i + 8]), false, Assembler::AVX_512bit);
  }

  for (int i = 0; i < 4; i++) {
    __ evpermt2d(xmm(outputRegs[i]), montMulPerm, xmm(scratchRegs[i + 4]),
                 Assembler::AVX_512bit);
  }
}

static void montMul64(int outputRegs[], int inputRegs1[], int inputRegs2[],
                       int scratchRegs[], MacroAssembler *_masm) {
   montMul64(outputRegs, inputRegs1, inputRegs2, scratchRegs, false, _masm);
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

static void loadPerm(int destinationRegs[], Register perms,
                      int offset, MacroAssembler *_masm) {
  __ evmovdqul(xmm(destinationRegs[0]), Address(perms, offset),
                 Assembler::AVX_512bit);
  for (int i = 1; i < 4; i++) {
      __ evmovdqul(xmm(destinationRegs[i]), xmm(destinationRegs[0]),
                   Assembler::AVX_512bit);
    }
}

static void load4Xmms(int destinationRegs[], Register source, int offset,
                       MacroAssembler *_masm) {
  for (int i = 0; i < 4; i++) {
    __ evmovdqul(xmm(destinationRegs[i]), Address(source, offset + i * XMMBYTES),
                 Assembler::AVX_512bit);
  }
}

static void loadXmm29(Register source, int offset, MacroAssembler *_masm) {
    __ evmovdqul(xmm29, Address(source, offset), Assembler::AVX_512bit);
}

static void store4Xmms(Register destination, int offset, int xmmRegs[],
                       MacroAssembler *_masm) {
  for (int i = 0; i < 4; i++) {
    __ evmovdqul(Address(destination, offset + i * XMMBYTES), xmm(xmmRegs[i]),
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
static int xmm20_23[] = {20, 21, 22, 23};
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
static address generate_dilithiumAlmostNtt_avx512(StubGenerator *stubgen,
                                                  MacroAssembler *_masm) {

  __ align(CodeEntryAlignment);
  StubId stub_id = StubId::stubgen_dilithiumAlmostNtt_id;
  StubCodeMark mark(stubgen, stub_id);
  address start = __ pc();
  __ enter();

  Label L_loop, L_end;

  const Register coeffs = c_rarg0;
  const Register zetas = c_rarg1;
  const Register iterations = c_rarg2;

  const Register perms = r11;

  __ lea(perms, ExternalAddress(dilithiumAvx512PermsAddr()));

  __ evmovdqul(montMulPerm, Address(perms, montMulPermsIdx), Assembler::AVX_512bit);

  // Each level represents one iteration of the outer for loop of the Java version
  // In each of these iterations half of the coefficients are (Montgomery)
  // multiplied by a zeta corresponding to the coefficient and then these
  // products will be added to and subtracted from the other half of the
  // coefficients. In each level we just collect the coefficients (using
  // evpermi2d() instructions where necessary, i.e. in levels 4-7) that need to
  // be multiplied by the zetas in one set, the rest to another set of vector
  // registers, then redistribute the addition/substraction results.

  // For levels 0 and 1 the zetas are not different within the 4 xmm registers
  // that we would use for them, so we use only one, xmm29.
  loadXmm29(zetas, 0, _masm);
  __ vpbroadcastd(montQInvModR,
                  ExternalAddress(dilithiumAvx512ConstsAddr(montQInvModRIdx)),
                  Assembler::AVX_512bit, scratch); // q^-1 mod 2^32
  __ vpbroadcastd(dilithium_q,
                  ExternalAddress(dilithiumAvx512ConstsAddr(dilithium_qIdx)),
                  Assembler::AVX_512bit, scratch); // q

  // load all coefficients into the vector registers Zmm_0-Zmm_15,
  // 16 coefficients into each
  load4Xmms(xmm0_3, coeffs, 0, _masm);
  load4Xmms(xmm4_7, coeffs, 4 * XMMBYTES, _masm);
  load4Xmms(xmm8_11, coeffs, 8 * XMMBYTES, _masm);
  load4Xmms(xmm12_15, coeffs, 12 * XMMBYTES, _masm);

  // level 0 and 1 can be done entirely in registers as the zetas on these
  // levels are the same for all the montmuls that we can do in parallel

  // level 0
  montMul64(xmm16_19, xmm8_11, xmm29_29, xmm16_27, _masm);
  sub_add(xmm8_11, xmm0_3, xmm0_3, xmm16_19, _masm);
  montMul64(xmm16_19, xmm12_15, xmm29_29, xmm16_27, _masm);
  loadXmm29(zetas, 512, _masm); // for level 1
  sub_add(xmm12_15, xmm4_7, xmm4_7, xmm16_19, _masm);

  // level 1

  montMul64(xmm16_19, xmm4_7, xmm29_29, xmm16_27, _masm);
  loadXmm29(zetas, 768, _masm);
  sub_add(xmm4_7, xmm0_3, xmm0_3, xmm16_19, _masm);
  montMul64(xmm16_19, xmm12_15, xmm29_29, xmm16_27, _masm);
  sub_add(xmm12_15, xmm8_11, xmm8_11, xmm16_19, _masm);

  // levels 2 to 7 are done in 2 batches, by first saving half of the coefficients
  // from level 1 into memory, doing all the level 2 to level 7 computations
  // on the remaining half in the vector registers, saving the result to
  // memory after level 7, then loading back the coefficients that we saved after
  // level 1 and do the same computation with those

  store4Xmms(coeffs, 8 * XMMBYTES, xmm8_11, _masm);
  store4Xmms(coeffs, 12 * XMMBYTES, xmm12_15, _masm);

  __ movl(iterations, 2);

  __ align(OptoLoopAlignment);
  __ BIND(L_loop);

  __ subl(iterations, 1);

  // level 2
  load4Xmms(xmm12_15, zetas, 2 * 512, _masm);
  montMul64(xmm16_19, xmm2367, xmm12_15, xmm16_27, _masm);
  load4Xmms(xmm12_15, zetas, 3 * 512, _masm); // for level 3
  sub_add(xmm2367, xmm0145, xmm0145, xmm16_19, _masm);

  // level 3

  montMul64(xmm16_19, xmm1357, xmm12_15, xmm16_27, _masm);
  sub_add(xmm1357, xmm0246, xmm0246, xmm16_19, _masm);

  // level 4
  loadPerm(xmm16_19, perms, nttL4PermsIdx, _masm);
  loadPerm(xmm12_15, perms, nttL4PermsIdx + 64, _masm);
  load4Xmms(xmm24_27, zetas, 4 * 512, _masm);

  for (int i = 0; i < 8; i += 2) {
    __ evpermi2d(xmm(i/2 + 16), xmm(i), xmm(i + 1), Assembler::AVX_512bit);
  }
  for (int i = 0; i < 8; i += 2) {
    __ evpermi2d(xmm(i / 2 + 12), xmm(i), xmm(i + 1), Assembler::AVX_512bit);
  }

  montMul64(xmm12_15, xmm12_15, xmm24_27, xmm4_20_24, _masm);
  sub_add(xmm1357, xmm0246, xmm16_19, xmm12_15, _masm);

  // level 5
  loadPerm(xmm16_19, perms, nttL5PermsIdx, _masm);
  loadPerm(xmm12_15, perms, nttL5PermsIdx + 64, _masm);
  load4Xmms(xmm24_27, zetas, 5 * 512, _masm);

  for (int i = 0; i < 8; i += 2) {
    __ evpermi2d(xmm(i/2 + 16), xmm(i), xmm(i + 1), Assembler::AVX_512bit);
  }
  for (int i = 0; i < 8; i += 2) {
    __ evpermi2d(xmm(i / 2 + 12), xmm(i), xmm(i + 1), Assembler::AVX_512bit);
  }

  montMul64(xmm12_15, xmm12_15, xmm24_27, xmm4_20_24, _masm);
  sub_add(xmm1357, xmm0246, xmm16_19, xmm12_15, _masm);

  // level 6
  loadPerm(xmm16_19, perms, nttL6PermsIdx, _masm);
  loadPerm(xmm12_15, perms, nttL6PermsIdx + 64, _masm);
  load4Xmms(xmm24_27, zetas, 6 * 512, _masm);

  for (int i = 0; i < 8; i += 2) {
    __ evpermi2d(xmm(i/2 + 16), xmm(i), xmm(i + 1), Assembler::AVX_512bit);
  }
  for (int i = 0; i < 8; i += 2) {
    __ evpermi2d(xmm(i / 2 + 12), xmm(i), xmm(i + 1), Assembler::AVX_512bit);
  }

  montMul64(xmm12_15, xmm12_15, xmm24_27, xmm4_20_24, _masm);
  sub_add(xmm1357, xmm0246, xmm16_19, xmm12_15, _masm);

  // level 7
  loadPerm(xmm16_19, perms, nttL7PermsIdx, _masm);
  loadPerm(xmm12_15, perms, nttL7PermsIdx + 64, _masm);
  load4Xmms(xmm24_27, zetas, 7 * 512, _masm);

  for (int i = 0; i < 8; i += 2) {
    __ evpermi2d(xmm(i / 2 + 16), xmm(i), xmm(i + 1), Assembler::AVX_512bit);
  }
  for (int i = 0; i < 8; i += 2) {
    __ evpermi2d(xmm(i / 2 + 12), xmm(i), xmm(i + 1), Assembler::AVX_512bit);
  }

  montMul64(xmm12_15, xmm12_15, xmm24_27, xmm4_20_24, true, _masm);
  loadPerm(xmm0246, perms, nttL7PermsIdx + 2 * XMMBYTES, _masm);
  loadPerm(xmm1357, perms, nttL7PermsIdx + 3 * XMMBYTES, _masm);
  sub_add(xmm21232527, xmm20222426, xmm16_19, xmm12_15, _masm);

  for (int i = 0; i < 8; i += 2) {
    __ evpermi2d(xmm(i), xmm(i + 20), xmm(i + 21), Assembler::AVX_512bit);
    __ evpermi2d(xmm(i + 1), xmm(i + 20), xmm(i + 21), Assembler::AVX_512bit);
  }

  __ cmpl(iterations, 0);
  __ jcc(Assembler::equal, L_end);

  store4Xmms(coeffs, 0, xmm0_3, _masm);
  store4Xmms(coeffs, 4 * XMMBYTES, xmm4_7, _masm);

  load4Xmms(xmm0_3, coeffs, 8 * XMMBYTES, _masm);
  load4Xmms(xmm4_7, coeffs, 12 * XMMBYTES, _masm);

  __ addptr(zetas, 4 * XMMBYTES);

  __ jmp(L_loop);

  __ BIND(L_end);

  store4Xmms(coeffs, 8 * XMMBYTES, xmm0_3, _masm);
  store4Xmms(coeffs, 12 * XMMBYTES, xmm4_7, _masm);

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
  StubId stub_id = StubId::stubgen_dilithiumAlmostInverseNtt_id;
  StubCodeMark mark(stubgen, stub_id);
  address start = __ pc();
  __ enter();

  Label L_loop, L_end;

  const Register coeffs = c_rarg0;
  const Register zetas = c_rarg1;

  const Register iterations = c_rarg2;

  const Register perms = r11;

  __ lea(perms, ExternalAddress(dilithiumAvx512PermsAddr()));

  __ evmovdqul(montMulPerm, Address(perms, montMulPermsIdx), Assembler::AVX_512bit);
  __ vpbroadcastd(montQInvModR,
                  ExternalAddress(dilithiumAvx512ConstsAddr(montQInvModRIdx)),
                  Assembler::AVX_512bit, scratch); // q^-1 mod 2^32
  __ vpbroadcastd(dilithium_q,
                  ExternalAddress(dilithiumAvx512ConstsAddr(dilithium_qIdx)),
                  Assembler::AVX_512bit, scratch); // q

  // Each level represents one iteration of the outer for loop of the
  // Java version.
  // In each of these iterations half of the coefficients are added to and
  // subtracted from the other half of the coefficients then the result of
  // the substartion is (Montgomery) multiplied by the corresponding zetas.
  // In each level we just collect the coefficients (using evpermi2d()
  // instructions where necessary, i.e. on levels 0-4) so that the results of
  // the additions and subtractions go to the vector registers so that they
  // align with each other and the zetas.

  // We do levels 0-6 in two batches, each batch entirely in the vector registers
  load4Xmms(xmm0_3, coeffs, 0, _masm);
  load4Xmms(xmm4_7, coeffs, 4 * XMMBYTES, _masm);

  __ movl(iterations, 2);

  __ align(OptoLoopAlignment);
  __ BIND(L_loop);

  __ subl(iterations, 1);

  // level 0
  loadPerm(xmm8_11, perms, nttInvL0PermsIdx, _masm);
  loadPerm(xmm12_15, perms, nttInvL0PermsIdx + 64, _masm);

  for (int i = 0; i < 8; i += 2) {
    __ evpermi2d(xmm(i / 2 + 8), xmm(i), xmm(i + 1), Assembler::AVX_512bit);
    __ evpermi2d(xmm(i / 2 + 12), xmm(i), xmm(i + 1), Assembler::AVX_512bit);
  }

  load4Xmms(xmm4_7, zetas, 0, _masm);
  sub_add(xmm24_27, xmm0_3, xmm8_11, xmm12_15, _masm);
  montMul64(xmm4_7, xmm4_7, xmm24_27, xmm16_27, true, _masm);

  // level 1
  loadPerm(xmm8_11, perms, nttInvL1PermsIdx, _masm);
  loadPerm(xmm12_15, perms, nttInvL1PermsIdx + 64, _masm);

  for (int i = 0; i < 4; i++) {
    __ evpermi2d(xmm(i + 8), xmm(i), xmm(i + 4), Assembler::AVX_512bit);
    __ evpermi2d(xmm(i + 12), xmm(i), xmm(i + 4), Assembler::AVX_512bit);
  }

  load4Xmms(xmm4_7, zetas, 512, _masm);
  sub_add(xmm24_27, xmm0_3, xmm8_11, xmm12_15, _masm);
  montMul64(xmm4_7, xmm24_27, xmm4_7, xmm16_27, _masm);

  // level 2
  loadPerm(xmm8_11, perms, nttInvL2PermsIdx, _masm);
  loadPerm(xmm12_15, perms, nttInvL2PermsIdx + 64, _masm);

  for (int i = 0; i < 4; i++) {
    __ evpermi2d(xmm(i + 8), xmm(i), xmm(i + 4), Assembler::AVX_512bit);
    __ evpermi2d(xmm(i + 12), xmm(i), xmm(i + 4), Assembler::AVX_512bit);
  }

  load4Xmms(xmm4_7, zetas, 2 * 512, _masm);
  sub_add(xmm24_27, xmm0_3, xmm8_11, xmm12_15, _masm);
  montMul64(xmm4_7, xmm24_27, xmm4_7, xmm16_27, _masm);

  // level 3
  loadPerm(xmm8_11, perms, nttInvL3PermsIdx, _masm);
  loadPerm(xmm12_15, perms, nttInvL3PermsIdx + 64, _masm);

  for (int i = 0; i < 4; i++) {
    __ evpermi2d(xmm(i + 8), xmm(i), xmm(i + 4), Assembler::AVX_512bit);
    __ evpermi2d(xmm(i + 12), xmm(i), xmm(i + 4), Assembler::AVX_512bit);
  }

  load4Xmms(xmm4_7, zetas, 3 * 512, _masm);
  sub_add(xmm24_27, xmm0_3, xmm8_11, xmm12_15, _masm);
  montMul64(xmm4_7, xmm24_27, xmm4_7, xmm16_27, _masm);

  // level 4
  loadPerm(xmm8_11, perms, nttInvL4PermsIdx, _masm);
  loadPerm(xmm12_15, perms, nttInvL4PermsIdx + 64, _masm);

  for (int i = 0; i < 4; i++) {
    __ evpermi2d(xmm(i + 8), xmm(i), xmm(i + 4), Assembler::AVX_512bit);
    __ evpermi2d(xmm(i + 12), xmm(i), xmm(i + 4), Assembler::AVX_512bit);
  }

  load4Xmms(xmm4_7, zetas, 4 * 512, _masm);
  sub_add(xmm24_27, xmm0_3, xmm8_11, xmm12_15, _masm);
  montMul64(xmm4_7, xmm24_27, xmm4_7, xmm16_27, _masm);

  // level 5
  load4Xmms(xmm12_15, zetas, 5 * 512, _masm);
  sub_add(xmm8_11, xmm0_3, xmm0426, xmm1537, _masm);
  montMul64(xmm4_7, xmm8_11, xmm12_15, xmm16_27, _masm);

  // level 6
  load4Xmms(xmm12_15, zetas, 6 * 512, _masm);
  sub_add(xmm8_11, xmm0_3, xmm0145, xmm2367, _masm);
  montMul64(xmm4_7, xmm8_11, xmm12_15, xmm16_27, _masm);

  __ cmpl(iterations, 0);
  __ jcc(Assembler::equal, L_end);

  // save the coefficients of the first batch, adjust the zetas
  // and load the second batch of coefficients
  store4Xmms(coeffs, 0, xmm0_3, _masm);
  store4Xmms(coeffs, 4 * XMMBYTES, xmm4_7, _masm);

  __ addptr(zetas, 4 * XMMBYTES);

  load4Xmms(xmm0_3, coeffs, 8 * XMMBYTES, _masm);
  load4Xmms(xmm4_7, coeffs, 12 * XMMBYTES, _masm);

  __ jmp(L_loop);

  __ BIND(L_end);

  // load the coeffs of the first batch of coefficients that were saved after
  // level 6 into Zmm_8-Zmm_15 and do the last level entirely in the vector
  // registers
  load4Xmms(xmm8_11, coeffs, 0, _masm);
  load4Xmms(xmm12_15, coeffs, 4 * XMMBYTES, _masm);

  // level 7

  loadXmm29(zetas, 7 * 512, _masm);

  for (int i = 0; i < 8; i++) {
    __ evpaddd(xmm(i + 16), k0, xmm(i), xmm(i + 8), false, Assembler::AVX_512bit);
  }

  for (int i = 0; i < 8; i++) {
    __ evpsubd(xmm(i), k0, xmm(i + 8), xmm(i), false, Assembler::AVX_512bit);
  }

  store4Xmms(coeffs, 0, xmm16_19, _masm);
  store4Xmms(coeffs, 4 * XMMBYTES, xmm20_23, _masm);
  montMul64(xmm0_3, xmm0_3, xmm29_29, xmm16_27, _masm);
  montMul64(xmm4_7, xmm4_7, xmm29_29, xmm16_27, _masm);
  store4Xmms(coeffs, 8 * XMMBYTES, xmm0_3, _masm);
  store4Xmms(coeffs, 12 * XMMBYTES, xmm4_7, _masm);

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
  StubId stub_id = StubId::stubgen_dilithiumNttMult_id;
  StubCodeMark mark(stubgen, stub_id);
  address start = __ pc();
  __ enter();

  Label L_loop;

  const Register result = c_rarg0;
  const Register poly1 = c_rarg1;
  const Register poly2 = c_rarg2;

  const Register perms = r10; // scratch reused after not needed any more
  const Register len = r11;

  const XMMRegister montRSquareModQ = xmm29;

  __ vpbroadcastd(montQInvModR,
                  ExternalAddress(dilithiumAvx512ConstsAddr(montQInvModRIdx)),
                  Assembler::AVX_512bit, scratch); // q^-1 mod 2^32
  __ vpbroadcastd(dilithium_q,
                  ExternalAddress(dilithiumAvx512ConstsAddr(dilithium_qIdx)),
                  Assembler::AVX_512bit, scratch); // q
  __ vpbroadcastd(montRSquareModQ,
                  ExternalAddress(dilithiumAvx512ConstsAddr(montRSquareModQIdx)),
                  Assembler::AVX_512bit, scratch); // 2^64 mod q

  __ lea(perms, ExternalAddress(dilithiumAvx512PermsAddr()));
  __ evmovdqul(montMulPerm, Address(perms, montMulPermsIdx), Assembler::AVX_512bit);

  __ movl(len, 4);

  __ align(OptoLoopAlignment);
  __ BIND(L_loop);

  load4Xmms(xmm4_7, poly2, 0, _masm);
  load4Xmms(xmm0_3, poly1, 0, _masm);
  montMul64(xmm4_7, xmm4_7, xmm29_29, xmm16_27, _masm);
  montMul64(xmm0_3, xmm0_3, xmm4_7, xmm16_27, true, _masm);
  store4Xmms(result, 0, xmm0_3, _masm);

  __ subl(len, 1);
  __ addptr(poly1, 4 * XMMBYTES);
  __ addptr(poly2, 4 * XMMBYTES);
  __ addptr(result, 4 * XMMBYTES);
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
  StubId stub_id = StubId::stubgen_dilithiumMontMulByConstant_id;
  StubCodeMark mark(stubgen, stub_id);
  address start = __ pc();
  __ enter();

  Label L_loop;

  const Register coeffs = c_rarg0;
  const Register rConstant = c_rarg1;

  const Register perms = c_rarg2; // not used for argument
  const Register len = r11;

  const XMMRegister constant = xmm29;

  __ lea(perms, ExternalAddress(dilithiumAvx512PermsAddr()));

  // the following four vector registers are used in montMul64
  __ vpbroadcastd(montQInvModR,
                  ExternalAddress(dilithiumAvx512ConstsAddr(montQInvModRIdx)),
                  Assembler::AVX_512bit, scratch); // q^-1 mod 2^32
  __ vpbroadcastd(dilithium_q,
                  ExternalAddress(dilithiumAvx512ConstsAddr(dilithium_qIdx)),
                  Assembler::AVX_512bit, scratch); // q
  __ evmovdqul(montMulPerm, Address(perms, montMulPermsIdx), Assembler::AVX_512bit);
  __ evpbroadcastd(constant, rConstant, Assembler::AVX_512bit); // constant multiplier

  __ movl(len, 2);

  __ align(OptoLoopAlignment);
  __ BIND(L_loop);

  load4Xmms(xmm0_3, coeffs, 0, _masm);
  load4Xmms(xmm4_7, coeffs, 4 * XMMBYTES, _masm);
  montMul64(xmm0_3, xmm0_3, xmm29_29, xmm16_27, _masm);
  montMul64(xmm4_7, xmm4_7, xmm29_29, xmm16_27, _masm);
  store4Xmms(coeffs, 0, xmm0_3, _masm);
  store4Xmms(coeffs, 4 * XMMBYTES, xmm4_7, _masm);

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
  StubId stub_id = StubId::stubgen_dilithiumDecomposePoly_id;
  StubCodeMark mark(stubgen, stub_id);
  address start = __ pc();
  __ enter();

  Label L_loop;

  const Register input = c_rarg0;
  const Register lowPart = c_rarg1;
  const Register highPart = c_rarg2;
  const Register rTwoGamma2 = c_rarg3;

  const Register len = r11;
  const XMMRegister zero = xmm24;
  const XMMRegister one = xmm25;
  const XMMRegister qMinus1 = xmm26;
  const XMMRegister gamma2 = xmm27;
  const XMMRegister twoGamma2 = xmm28;
  const XMMRegister barrettMultiplier = xmm29;
  const XMMRegister barrettAddend = xmm30;

  __ vpxor(zero, zero, zero, Assembler::AVX_512bit); // 0
  __ vpternlogd(xmm0, 0xff, xmm0, xmm0, Assembler::AVX_512bit); // -1
  __ vpsubd(one, zero, xmm0, Assembler::AVX_512bit); // 1
  __ vpbroadcastd(dilithium_q,
                  ExternalAddress(dilithiumAvx512ConstsAddr(dilithium_qIdx)),
                  Assembler::AVX_512bit, scratch); // q
  __ vpbroadcastd(barrettAddend,
                  ExternalAddress(dilithiumAvx512ConstsAddr(barrettAddendIdx)),
                  Assembler::AVX_512bit, scratch); // addend for Barrett reduction

  __ evpbroadcastd(twoGamma2, rTwoGamma2, Assembler::AVX_512bit); // 2 * gamma2

  #ifndef _WIN64
    const Register rMultiplier = c_rarg4;
  #else
    const Address multiplier_mem(rbp, 6 * wordSize);
    const Register rMultiplier = c_rarg3; // arg3 is already consumed, reused here
    __ movptr(rMultiplier, multiplier_mem);
  #endif
  __ evpbroadcastd(barrettMultiplier, rMultiplier,
                   Assembler::AVX_512bit); // multiplier for mod 2 * gamma2 reduce

  __ evpsubd(qMinus1, k0, dilithium_q, one, false, Assembler::AVX_512bit); // q - 1
  __ evpsrad(gamma2, k0, twoGamma2, 1, false, Assembler::AVX_512bit); // gamma2

  __ movl(len, 1024);

  __ align(OptoLoopAlignment);
  __ BIND(L_loop);

  load4Xmms(xmm0_3, input, 0, _masm);

  __ addptr(input, 4 * XMMBYTES);

  // rplus in xmm0
  // rplus = rplus - ((rplus + 5373807) >> 23) * dilithium_q;
  __ evpaddd(xmm4, k0, xmm0, barrettAddend, false, Assembler::AVX_512bit);
  __ evpaddd(xmm5, k0, xmm1, barrettAddend, false, Assembler::AVX_512bit);
  __ evpaddd(xmm6, k0, xmm2, barrettAddend, false, Assembler::AVX_512bit);
  __ evpaddd(xmm7, k0, xmm3, barrettAddend, false, Assembler::AVX_512bit);

  __ evpsrad(xmm4, k0, xmm4, 23, false, Assembler::AVX_512bit);
  __ evpsrad(xmm5, k0, xmm5, 23, false, Assembler::AVX_512bit);
  __ evpsrad(xmm6, k0, xmm6, 23, false, Assembler::AVX_512bit);
  __ evpsrad(xmm7, k0, xmm7, 23, false, Assembler::AVX_512bit);

  __ evpmulld(xmm4, k0, xmm4, dilithium_q, false, Assembler::AVX_512bit);
  __ evpmulld(xmm5, k0, xmm5, dilithium_q, false, Assembler::AVX_512bit);
  __ evpmulld(xmm6, k0, xmm6, dilithium_q, false, Assembler::AVX_512bit);
  __ evpmulld(xmm7, k0, xmm7, dilithium_q, false, Assembler::AVX_512bit);

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

  __ evpandd(xmm4, k0, xmm4, dilithium_q, false, Assembler::AVX_512bit);
  __ evpandd(xmm5, k0, xmm5, dilithium_q, false, Assembler::AVX_512bit);
  __ evpandd(xmm6, k0, xmm6, dilithium_q, false, Assembler::AVX_512bit);
  __ evpandd(xmm7, k0, xmm7, dilithium_q, false, Assembler::AVX_512bit);

  __ evpaddd(xmm0, k0, xmm0, xmm4, false, Assembler::AVX_512bit);
  __ evpaddd(xmm1, k0, xmm1, xmm5, false, Assembler::AVX_512bit);
  __ evpaddd(xmm2, k0, xmm2, xmm6, false, Assembler::AVX_512bit);
  __ evpaddd(xmm3, k0, xmm3, xmm7, false, Assembler::AVX_512bit);
  // rplus in xmm0
  // int quotient = (rplus * barrettMultiplier) >> 22;
  __ evpmulld(xmm4, k0, xmm0, barrettMultiplier, false, Assembler::AVX_512bit);
  __ evpmulld(xmm5, k0, xmm1, barrettMultiplier, false, Assembler::AVX_512bit);
  __ evpmulld(xmm6, k0, xmm2, barrettMultiplier, false, Assembler::AVX_512bit);
  __ evpmulld(xmm7, k0, xmm3, barrettMultiplier, false, Assembler::AVX_512bit);

  __ evpsrad(xmm4, k0, xmm4, 22, false, Assembler::AVX_512bit);
  __ evpsrad(xmm5, k0, xmm5, 22, false, Assembler::AVX_512bit);
  __ evpsrad(xmm6, k0, xmm6, 22, false, Assembler::AVX_512bit);
  __ evpsrad(xmm7, k0, xmm7, 22, false, Assembler::AVX_512bit);
  // quotient in xmm4
  // int r0 = rplus - quotient * twoGamma2;
  __ evpmulld(xmm8, k0, xmm4, twoGamma2, false, Assembler::AVX_512bit);
  __ evpmulld(xmm9, k0, xmm5, twoGamma2, false, Assembler::AVX_512bit);
  __ evpmulld(xmm10, k0, xmm6, twoGamma2, false, Assembler::AVX_512bit);
  __ evpmulld(xmm11, k0, xmm7, twoGamma2, false, Assembler::AVX_512bit);

  __ evpsubd(xmm8, k0, xmm0, xmm8, false, Assembler::AVX_512bit);
  __ evpsubd(xmm9, k0, xmm1, xmm9, false, Assembler::AVX_512bit);
  __ evpsubd(xmm10, k0, xmm2, xmm10, false, Assembler::AVX_512bit);
  __ evpsubd(xmm11, k0, xmm3, xmm11, false, Assembler::AVX_512bit);
  // r0 in xmm8
  // int mask = (twoGamma2 - r0) >> 22;
  __ evpsubd(xmm12, k0, twoGamma2, xmm8, false, Assembler::AVX_512bit);
  __ evpsubd(xmm13, k0, twoGamma2, xmm9, false, Assembler::AVX_512bit);
  __ evpsubd(xmm14, k0, twoGamma2, xmm10, false, Assembler::AVX_512bit);
  __ evpsubd(xmm15, k0, twoGamma2, xmm11, false, Assembler::AVX_512bit);

  __ evpsrad(xmm12, k0, xmm12, 22, false, Assembler::AVX_512bit);
  __ evpsrad(xmm13, k0, xmm13, 22, false, Assembler::AVX_512bit);
  __ evpsrad(xmm14, k0, xmm14, 22, false, Assembler::AVX_512bit);
  __ evpsrad(xmm15, k0, xmm15, 22, false, Assembler::AVX_512bit);
  // mask in xmm12
  // r0 -= (mask & twoGamma2);
  __ evpandd(xmm16, k0, xmm12, twoGamma2, false, Assembler::AVX_512bit);
  __ evpandd(xmm17, k0, xmm13, twoGamma2, false, Assembler::AVX_512bit);
  __ evpandd(xmm18, k0, xmm14, twoGamma2, false, Assembler::AVX_512bit);
  __ evpandd(xmm19, k0, xmm15, twoGamma2, false, Assembler::AVX_512bit);

  __ evpsubd(xmm8, k0, xmm8, xmm16, false, Assembler::AVX_512bit);
  __ evpsubd(xmm9, k0, xmm9, xmm17, false, Assembler::AVX_512bit);
  __ evpsubd(xmm10, k0, xmm10, xmm18, false, Assembler::AVX_512bit);
  __ evpsubd(xmm11, k0, xmm11, xmm19, false, Assembler::AVX_512bit);
  // r0 in xmm8
  // quotient += (mask & 1);
  __ evpandd(xmm16, k0, xmm12, one, false, Assembler::AVX_512bit);
  __ evpandd(xmm17, k0, xmm13, one, false, Assembler::AVX_512bit);
  __ evpandd(xmm18, k0, xmm14, one, false, Assembler::AVX_512bit);
  __ evpandd(xmm19, k0, xmm15, one, false, Assembler::AVX_512bit);

  __ evpaddd(xmm4, k0, xmm4, xmm16, false, Assembler::AVX_512bit);
  __ evpaddd(xmm5, k0, xmm5, xmm17, false, Assembler::AVX_512bit);
  __ evpaddd(xmm6, k0, xmm6, xmm18, false, Assembler::AVX_512bit);
  __ evpaddd(xmm7, k0, xmm7, xmm19, false, Assembler::AVX_512bit);

  // mask = (twoGamma2 / 2 - r0) >> 31;
  __ evpsubd(xmm12, k0, gamma2, xmm8, false, Assembler::AVX_512bit);
  __ evpsubd(xmm13, k0, gamma2, xmm9, false, Assembler::AVX_512bit);
  __ evpsubd(xmm14, k0, gamma2, xmm10, false, Assembler::AVX_512bit);
  __ evpsubd(xmm15, k0, gamma2, xmm11, false, Assembler::AVX_512bit);

  __ evpsrad(xmm12, k0, xmm12, 31, false, Assembler::AVX_512bit);
  __ evpsrad(xmm13, k0, xmm13, 31, false, Assembler::AVX_512bit);
  __ evpsrad(xmm14, k0, xmm14, 31, false, Assembler::AVX_512bit);
  __ evpsrad(xmm15, k0, xmm15, 31, false, Assembler::AVX_512bit);

  // r0 -= (mask & twoGamma2);
  __ evpandd(xmm16, k0, xmm12, twoGamma2, false, Assembler::AVX_512bit);
  __ evpandd(xmm17, k0, xmm13, twoGamma2, false, Assembler::AVX_512bit);
  __ evpandd(xmm18, k0, xmm14, twoGamma2, false, Assembler::AVX_512bit);
  __ evpandd(xmm19, k0, xmm15, twoGamma2, false, Assembler::AVX_512bit);

  __ evpsubd(xmm8, k0, xmm8, xmm16, false, Assembler::AVX_512bit);
  __ evpsubd(xmm9, k0, xmm9, xmm17, false, Assembler::AVX_512bit);
  __ evpsubd(xmm10, k0, xmm10, xmm18, false, Assembler::AVX_512bit);
  __ evpsubd(xmm11, k0, xmm11, xmm19, false, Assembler::AVX_512bit);
  // r0 in xmm8
  // quotient += (mask & 1);
  __ evpandd(xmm16, k0, xmm12, one, false, Assembler::AVX_512bit);
  __ evpandd(xmm17, k0, xmm13, one, false, Assembler::AVX_512bit);
  __ evpandd(xmm18, k0, xmm14, one, false, Assembler::AVX_512bit);
  __ evpandd(xmm19, k0, xmm15, one, false, Assembler::AVX_512bit);

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
  __ evpsubd(xmm20, k0, zero, xmm16, false, Assembler::AVX_512bit);
  __ evpsubd(xmm21, k0, zero, xmm17, false, Assembler::AVX_512bit);
  __ evpsubd(xmm22, k0, zero, xmm18, false, Assembler::AVX_512bit);
  __ evpsubd(xmm23, k0, zero, xmm19, false, Assembler::AVX_512bit);

  __ evporq(xmm16, k0, xmm16, xmm20, false, Assembler::AVX_512bit);
  __ evporq(xmm17, k0, xmm17, xmm21, false, Assembler::AVX_512bit);
  __ evporq(xmm18, k0, xmm18, xmm22, false, Assembler::AVX_512bit);
  __ evporq(xmm19, k0, xmm19, xmm23, false, Assembler::AVX_512bit);

  __ evpsubd(xmm12, k0, zero, one, false, Assembler::AVX_512bit); // -1

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
  store4Xmms(highPart, 0, xmm0_3, _masm);
  store4Xmms(lowPart, 0, xmm8_11, _masm);

  __ addptr(highPart, 4 * XMMBYTES);
  __ addptr(lowPart, 4 * XMMBYTES);
  __ subl(len, 4 * XMMBYTES);
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
