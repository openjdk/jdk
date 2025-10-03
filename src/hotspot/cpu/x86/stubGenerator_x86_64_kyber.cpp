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
ATTRIBUTE_ALIGNED(64) static const uint16_t kyberAvx512Consts[] = {
    0xF301, 0xF301, 0xF301, 0xF301, // q^-1 mod montR
    0x0D01, 0x0D01, 0x0D01, 0x0D01, // q
    0x4EBF, 0x4EBF, 0x4EBF, 0x4EBF, // Barrett multiplier
    0x0200, 0x0200, 0x0200, 0x0200, //(dim/2)^-1 mod q
    0x0549, 0x0549, 0x0549, 0x0549, // montR^2 mod q
    0x0F00, 0x0F00, 0x0F00, 0x0F00  // mask for kyber12to16
  };

static int qInvModROffset = 0;
static int qOffset = 8;
static int barretMultiplierOffset = 16;
static int dimHalfInverseOffset = 24;
static int montRSquareModqOffset = 32;
static int f00Offset = 40;

static address kyberAvx512ConstsAddr(int offset) {
  return ((address) kyberAvx512Consts) + offset;
}

const Register scratch = r10;

ATTRIBUTE_ALIGNED(64) static const uint16_t kyberAvx512NttPerms[] = {
// 0
    0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17,
    0x18, 0x19, 0x1A, 0x1B, 0x1C, 0x1D, 0x1E, 0x1F,
    0x30, 0x31, 0x32, 0x33, 0x34, 0x35, 0x36, 0x37,
    0x38, 0x39, 0x3A, 0x3B, 0x3C, 0x3D, 0x3E, 0x3F,
    0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07,
    0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F,
    0x20, 0x21, 0x22, 0x23, 0x24, 0x25, 0x26, 0x27,
    0x28, 0x29, 0x2A, 0x2B, 0x2C, 0x2D, 0x2E, 0x2F,
// 128
    0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07,
    0x20, 0x21, 0x22, 0x23, 0x24, 0x25, 0x26, 0x27,
    0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17,
    0x30, 0x31, 0x32, 0x33, 0x34, 0x35, 0x36, 0x37,
    0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F,
    0x28, 0x29, 0x2A, 0x2B, 0x2C, 0x2D, 0x2E, 0x2F,
    0x18, 0x19, 0x1A, 0x1B, 0x1C, 0x1D, 0x1E, 0x1F,
    0x38, 0x39, 0x3A, 0x3B, 0x3C, 0x3D, 0x3E, 0x3F,
// 256
    0x00, 0x01, 0x02, 0x03, 0x20, 0x21, 0x22, 0x23,
    0x08, 0x09, 0x0A, 0x0B, 0x28, 0x29, 0x2A, 0x2B,
    0x10, 0x11, 0x12, 0x13, 0x30, 0x31, 0x32, 0x33,
    0x18, 0x19, 0x1A, 0x1B, 0x38, 0x39, 0x3A, 0x3B,
    0x04, 0x05, 0x06, 0x07, 0x24, 0x25, 0x26, 0x27,
    0x0C, 0x0D, 0x0E, 0x0F, 0x2C, 0x2D, 0x2E, 0x2F,
    0x14, 0x15, 0x16, 0x17, 0x34, 0x35, 0x36, 0x37,
    0x1C, 0x1D, 0x1E, 0x1F, 0x3C, 0x3D, 0x3E, 0x3F,
// 384
    0x00, 0x01, 0x20, 0x21, 0x04, 0x05, 0x24, 0x25,
    0x08, 0x09, 0x28, 0x29, 0x0C, 0x0D, 0x2C, 0x2D,
    0x10, 0x11, 0x30, 0x31, 0x14, 0x15, 0x34, 0x35,
    0x18, 0x19, 0x38, 0x39, 0x1C, 0x1D, 0x3C, 0x3D,
    0x02, 0x03, 0x22, 0x23, 0x06, 0x07, 0x26, 0x27,
    0x0A, 0x0B, 0x2A, 0x2B, 0x0E, 0x0F, 0x2E, 0x2F,
    0x12, 0x13, 0x32, 0x33, 0x16, 0x17, 0x36, 0x37,
    0x1A, 0x1B, 0x3A, 0x3B, 0x1E, 0x1F, 0x3E, 0x3F,
// 512
    0x10, 0x11, 0x30, 0x31, 0x12, 0x13, 0x32, 0x33,
    0x14, 0x15, 0x34, 0x35, 0x16, 0x17, 0x36, 0x37,
    0x18, 0x19, 0x38, 0x39, 0x1A, 0x1B, 0x3A, 0x3B,
    0x1C, 0x1D, 0x3C, 0x3D, 0x1E, 0x1F, 0x3E, 0x3F,
    0x00, 0x01, 0x20, 0x21, 0x02, 0x03, 0x22, 0x23,
    0x04, 0x05, 0x24, 0x25, 0x06, 0x07, 0x26, 0x27,
    0x08, 0x09, 0x28, 0x29, 0x0A, 0x0B, 0x2A, 0x2B,
    0x0C, 0x0D, 0x2C, 0x2D, 0x0E, 0x0F, 0x2E, 0x2F
  };

static address kyberAvx512NttPermsAddr() {
  return (address) kyberAvx512NttPerms;
}

ATTRIBUTE_ALIGNED(64) static const uint16_t kyberAvx512InverseNttPerms[] = {
// 0
    0x02, 0x03, 0x06, 0x07, 0x0A, 0x0B, 0x0E, 0x0F,
    0x12, 0x13, 0x16, 0x17, 0x1A, 0x1B, 0x1E, 0x1F,
    0x22, 0x23, 0x26, 0x27, 0x2A, 0x2B, 0x2E, 0x2F,
    0x32, 0x33, 0x36, 0x37, 0x3A, 0x3B, 0x3E, 0x3F,
    0x00, 0x01, 0x04, 0x05, 0x08, 0x09, 0x0C, 0x0D,
    0x10, 0x11, 0x14, 0x15, 0x18, 0x19, 0x1C, 0x1D,
    0x20, 0x21, 0x24, 0x25, 0x28, 0x29, 0x2C, 0x2D,
    0x30, 0x31, 0x34, 0x35, 0x38, 0x39, 0x3C, 0x3D,
// 128
    0x00, 0x01, 0x20, 0x21, 0x04, 0x05, 0x24, 0x25,
    0x08, 0x09, 0x28, 0x29, 0x0C, 0x0D, 0x2C, 0x2D,
    0x10, 0x11, 0x30, 0x31, 0x14, 0x15, 0x34, 0x35,
    0x18, 0x19, 0x38, 0x39, 0x1C, 0x1D, 0x3C, 0x3D,
    0x02, 0x03, 0x22, 0x23, 0x06, 0x07, 0x26, 0x27,
    0x0A, 0x0B, 0x2A, 0x2B, 0x0E, 0x0F, 0x2E, 0x2F,
    0x12, 0x13, 0x32, 0x33, 0x16, 0x17, 0x36, 0x37,
    0x1A, 0x1B, 0x3A, 0x3B, 0x1E, 0x1F, 0x3E, 0x3F,
// 256
    0x00, 0x01, 0x02, 0x03, 0x20, 0x21, 0x22, 0x23,
    0x08, 0x09, 0x0A, 0x0B, 0x28, 0x29, 0x2A, 0x2B,
    0x10, 0x11, 0x12, 0x13, 0x30, 0x31, 0x32, 0x33,
    0x18, 0x19, 0x1A, 0x1B, 0x38, 0x39, 0x3A, 0x3B,
    0x04, 0x05, 0x06, 0x07, 0x24, 0x25, 0x26, 0x27,
    0x0C, 0x0D, 0x0E, 0x0F, 0x2C, 0x2D, 0x2E, 0x2F,
    0x14, 0x15, 0x16, 0x17, 0x34, 0x35, 0x36, 0x37,
    0x1C, 0x1D, 0x1E, 0x1F, 0x3C, 0x3D, 0x3E, 0x3F,
// 384
    0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07,
    0x20, 0x21, 0x22, 0x23, 0x24, 0x25, 0x26, 0x27,
    0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17,
    0x30, 0x31, 0x32, 0x33, 0x34, 0x35, 0x36, 0x37,
    0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F,
    0x28, 0x29, 0x2A, 0x2B, 0x2C, 0x2D, 0x2E, 0x2F,
    0x18, 0x19, 0x1A, 0x1B, 0x1C, 0x1D, 0x1E, 0x1F,
    0x38, 0x39, 0x3A, 0x3B, 0x3C, 0x3D, 0x3E, 0x3F,
// 512
    0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17,
    0x18, 0x19, 0x1A, 0x1B, 0x1C, 0x1D, 0x1E, 0x1F,
    0x30, 0x31, 0x32, 0x33, 0x34, 0x35, 0x36, 0x37,
    0x38, 0x39, 0x3A, 0x3B, 0x3C, 0x3D, 0x3E, 0x3F,
    0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07,
    0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F,
    0x20, 0x21, 0x22, 0x23, 0x24, 0x25, 0x26, 0x27,
    0x28, 0x29, 0x2A, 0x2B, 0x2C, 0x2D, 0x2E, 0x2F
  };

static address kyberAvx512InverseNttPermsAddr() {
  return (address) kyberAvx512InverseNttPerms;
}

ATTRIBUTE_ALIGNED(64) static const uint16_t kyberAvx512_nttMultPerms[] = {
    0x00, 0x02, 0x04, 0x06, 0x08, 0x0A, 0x0C, 0x0E,
    0x10, 0x12, 0x14, 0x16, 0x18, 0x1A, 0x1C, 0x1E,
    0x20, 0x22, 0x24, 0x26, 0x28, 0x2A, 0x2C, 0x2E,
    0x30, 0x32, 0x34, 0x36, 0x38, 0x3A, 0x3C, 0x3E,

    0x01, 0x03, 0x05, 0x07, 0x09, 0x0B, 0x0D, 0x0F,
    0x11, 0x13, 0x15, 0x17, 0x19, 0x1B, 0x1D, 0x1F,
    0x21, 0x23, 0x25, 0x27, 0x29, 0x2B, 0x2D, 0x2F,
    0x31, 0x33, 0x35, 0x37, 0x39, 0x3B, 0x3D, 0x3F,

    0x00, 0x20, 0x01, 0x21, 0x02, 0x22, 0x03, 0x23,
    0x04, 0x24, 0x05, 0x25, 0x06, 0x26, 0x07, 0x27,
    0x08, 0x28, 0x09, 0x29, 0x0A, 0x2A, 0x0B, 0x2B,
    0x0C, 0x2C, 0x0D, 0x2D, 0x0E, 0x2E, 0x0F, 0x2F,

    0x10, 0x30, 0x11, 0x31, 0x12, 0x32, 0x13, 0x33,
    0x14, 0x34, 0x15, 0x35, 0x16, 0x36, 0x17, 0x37,
    0x18, 0x38, 0x19, 0x39, 0x1A, 0x3A, 0x1B, 0x3B,
    0x1C, 0x3C, 0x1D, 0x3D, 0x1E, 0x3E, 0x1F, 0x3F
  };

static address kyberAvx512_nttMultPermsAddr() {
  return (address) kyberAvx512_nttMultPerms;
}

  ATTRIBUTE_ALIGNED(64) static const uint16_t kyberAvx512_12To16Perms[] = {
// 0
    0x00, 0x03, 0x06, 0x09, 0x0C, 0x0F, 0x12, 0x15,
    0x18, 0x1B, 0x1E, 0x21, 0x24, 0x27, 0x2A, 0x2D,
    0x30, 0x33, 0x36, 0x39, 0x3C, 0x3F, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x01, 0x04, 0x07, 0x0A, 0x0D, 0x10, 0x13, 0x16,
    0x19, 0x1C, 0x1F, 0x22, 0x25, 0x28, 0x2B, 0x2E,
    0x31, 0x34, 0x37, 0x3A, 0x3D, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
// 128
    0x02, 0x05, 0x08, 0x0B, 0x0E, 0x11, 0x14, 0x17,
    0x1A, 0x1D, 0x20, 0x23, 0x26, 0x29, 0x2C, 0x2F,
    0x32, 0x35, 0x38, 0x3B, 0x3E, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07,
    0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F,
    0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x22, 0x25,
    0x28, 0x2B, 0x2E, 0x31, 0x34, 0x37, 0x3A, 0x3D,
// 256
    0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07,
    0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F,
    0x10, 0x11, 0x12, 0x13, 0x14, 0x20, 0x23, 0x26,
    0x29, 0x2C, 0x2F, 0x32, 0x35, 0x38, 0x3B, 0x3E,
    0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07,
    0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F,
    0x10, 0x11, 0x12, 0x13, 0x14, 0x21, 0x24, 0x27,
    0x2A, 0x2D, 0x30, 0x33, 0x36, 0x39, 0x3C, 0x3F,
// 384
    0x00, 0x20, 0x01, 0x21, 0x02, 0x22, 0x03, 0x23,
    0x04, 0x24, 0x05, 0x25, 0x06, 0x26, 0x07, 0x27,
    0x08, 0x28, 0x09, 0x29, 0x0A, 0x2A, 0x0B, 0x2B,
    0x0C, 0x2C, 0x0D, 0x2D, 0x0E, 0x2E, 0x0F, 0x2F,
    0x10, 0x30, 0x11, 0x31, 0x12, 0x32, 0x13, 0x33,
    0x14, 0x34, 0x15, 0x35, 0x16, 0x36, 0x17, 0x37,
    0x18, 0x38, 0x19, 0x39, 0x1A, 0x3A, 0x1B, 0x3B,
    0x1C, 0x3C, 0x1D, 0x3D, 0x1E, 0x3E, 0x1F, 0x3F
  };

static address kyberAvx512_12To16PermsAddr() {
  return (address) kyberAvx512_12To16Perms;
}

static void load4regs(int destRegs[], Register address, int offset,
                      MacroAssembler *_masm) {
  for (int i = 0; i < 4; i++) {
    __ evmovdquw(xmm(destRegs[i]), Address(address, offset + i * 64),
                 Assembler::AVX_512bit);
  }
}

// For z = montmul(a,b), z will be  between -q and q and congruent
// to a * b * R^-1 mod q, where R > 2 * q, R is a power of 2,
// -R/2 * q <= a * b < R/2 * q.
// (See e.g. Algorithm 3 in https://eprint.iacr.org/2018/039.pdf)
// For the Java code, we use R = 2^20 and for the intrinsic, R = 2^16.
// In our computations, b is always c * R mod q, so the montmul() really
// computes a * c mod q. In the Java code, we use 32-bit numbers for the
// computations, and we use R = 2^20 because that way the a * b numbers
// that occur during all computations stay in the required range.
// For the intrinsics, we use R = 2^16, because this way we can do twice
// as much work in parallel, the only drawback is that we should do some Barrett
// reductions in kyberInverseNtt so that the numbers stay in the required range.
static void montmul(int outputRegs[], int inputRegs1[], int inputRegs2[],
             int scratchRegs1[], int scratchRegs2[], MacroAssembler *_masm) {
   for (int i = 0; i < 4; i++) {
     __ evpmullw(xmm(scratchRegs1[i]), k0, xmm(inputRegs1[i]),
                 xmm(inputRegs2[i]), false, Assembler::AVX_512bit);
   }
   for (int i = 0; i < 4; i++) {
     __ evpmulhw(xmm(scratchRegs2[i]), k0, xmm(inputRegs1[i]),
                 xmm(inputRegs2[i]), false, Assembler::AVX_512bit);
   }
   for (int i = 0; i < 4; i++) {
     __ evpmullw(xmm(scratchRegs1[i]), k0, xmm(scratchRegs1[i]),
                 xmm31, false, Assembler::AVX_512bit);
   }
   for (int i = 0; i < 4; i++) {
     __ evpmulhw(xmm(scratchRegs1[i]), k0, xmm(scratchRegs1[i]),
                 xmm30, false, Assembler::AVX_512bit);
   }
   for (int i = 0; i < 4; i++) {
     __ evpsubw(xmm(outputRegs[i]), k0, xmm(scratchRegs2[i]),
                xmm(scratchRegs1[i]), false, Assembler::AVX_512bit);
   }
}

static void sub_add(int subResult[], int addResult[], int input1[], int input2[],
                    MacroAssembler *_masm) {
  for (int i = 0; i < 4; i++) {
    __ evpsubw(xmm(subResult[i]), k0, xmm(input1[i]), xmm(input2[i]),
               false, Assembler::AVX_512bit);
    __ evpaddw(xmm(addResult[i]), k0, xmm(input1[i]), xmm(input2[i]),
               false, Assembler::AVX_512bit);
  }
}

// result2 also acts as input1
// result1 also acts as perm1
static void permute(int result1[], int result2[], int input2[], int perm2,
                    MacroAssembler *_masm) {

  for (int i = 1; i < 4; i++) {
    __ evmovdquw(xmm(result1[i]), xmm(result1[0]), Assembler::AVX_512bit);
  }

  for (int i = 0; i < 4; i++) {
    __ evpermi2w(xmm(result1[i]), xmm(result2[i]), xmm(input2[i]),
                 Assembler::AVX_512bit);
    __ evpermt2w(xmm(result2[i]), xmm(perm2), xmm(input2[i]),
                 Assembler::AVX_512bit);
  }
}

static void store4regs(Register address, int offset, int sourceRegs[],
                       MacroAssembler *_masm) {
  for (int i = 0; i < 4; i++) {
    __ evmovdquw(Address(address, offset + i * 64), xmm(sourceRegs[i]),
                 Assembler::AVX_512bit);
  }
}

// In all 3 invocations of this function we use the same registers:
// xmm0-xmm7 for the input and the result,
// xmm8-xmm15 as scratch registers and
// xmm16-xmm17 for the constants,
// so we don't pass register arguments.
static void barrettReduce(MacroAssembler *_masm) {
  for (int i = 0; i < 8; i++) {
    __ evpmulhw(xmm(i + 8), k0, xmm(i), xmm16, false, Assembler::AVX_512bit);
  }

  for (int i = 0; i < 8; i++) {
    __ evpsraw(xmm(i + 8), k0, xmm(i + 8), 10, false, Assembler::AVX_512bit);
  }

  for (int i = 0; i < 8; i++) {
    __ evpmullw(xmm(i + 8), k0, xmm(i + 8), xmm17, false, Assembler::AVX_512bit);
  }

  for (int i = 0; i < 8; i++) {
    __ evpsubw(xmm(i), k0, xmm(i), xmm(i + 8), false, Assembler::AVX_512bit);
  }
}

static int xmm0_3[] = {0, 1, 2, 3};
static int xmm0145[] = {0, 1, 4, 5};
static int xmm0246[] = {0, 2, 4, 6};
static int xmm0829[] = {0, 8, 2, 9};
static int xmm1001[] = {1, 0, 0, 1};
static int xmm1357[] = {1, 3, 5, 7};
static int xmm2367[] = {2, 3, 6, 7};
static int xmm2_0_10_8[] = {2, 0, 10, 8};
static int xmm3223[] = {3, 2, 2, 3};
static int xmm4_7[] = {4, 5, 6, 7};
static int xmm5454[] = {5, 4, 5, 4};
static int xmm7676[] = {7, 6, 7, 6};
static int xmm8_11[] = {8, 9, 10, 11};
static int xmm12_15[] = {12, 13, 14, 15};
static int xmm16_19[] = {16, 17, 18, 19};
static int xmm20_23[] = {20, 21, 22, 23};
static int xmm23_23[] = {23, 23, 23, 23};
static int xmm24_27[] = {24, 25, 26, 27};
static int xmm26_29[] = {26, 27, 28, 29};
static int xmm28_31[] = {28, 29, 30, 31};
static int xmm29_29[] = {29, 29, 29, 29};

// Kyber NTT function.
//
// coeffs (short[256]) = c_rarg0
// ntt_zetas (short[256]) = c_rarg1
address generate_kyberNtt_avx512(StubGenerator *stubgen,
                                 MacroAssembler *_masm) {
  __ align(CodeEntryAlignment);
  StubId stub_id = StubId::stubgen_kyberNtt_id;
  StubCodeMark mark(stubgen, stub_id);
  address start = __ pc();
  __ enter();

  const Register coeffs = c_rarg0;
  const Register zetas = c_rarg1;

  const Register perms = r11;

  __ lea(perms, ExternalAddress(kyberAvx512NttPermsAddr()));

  load4regs(xmm4_7, coeffs, 256, _masm);
  load4regs(xmm20_23, zetas, 0, _masm);

  __ vpbroadcastq(xmm30,
                  ExternalAddress(kyberAvx512ConstsAddr(qOffset)),
                  Assembler::AVX_512bit, scratch); // q
  __ vpbroadcastq(xmm31,
                  ExternalAddress(kyberAvx512ConstsAddr(qInvModROffset)),
                  Assembler::AVX_512bit, scratch); // q^-1 mod montR

  load4regs(xmm0_3, coeffs, 0, _masm);

  // Each level represents one iteration of the outer for loop of the Java version.
  // level 0
  montmul(xmm8_11, xmm4_7, xmm20_23, xmm8_11, xmm4_7, _masm);
  load4regs(xmm20_23, zetas, 256, _masm);
  sub_add(xmm4_7, xmm0_3, xmm0_3, xmm8_11, _masm);

  //level 1
  montmul(xmm12_15, xmm2367, xmm20_23, xmm12_15, xmm8_11, _masm);
  load4regs(xmm20_23, zetas, 512, _masm);
  sub_add(xmm2367, xmm0145, xmm0145, xmm12_15, _masm);

  // level 2
  montmul(xmm8_11, xmm1357, xmm20_23, xmm12_15, xmm8_11, _masm);
  __ evmovdquw(xmm12, Address(perms, 0), Assembler::AVX_512bit);
  __ evmovdquw(xmm16, Address(perms, 64), Assembler::AVX_512bit);
  load4regs(xmm20_23, zetas, 768, _masm);
  sub_add(xmm1357, xmm0246, xmm0246, xmm8_11, _masm);

  //level 3
  permute(xmm12_15, xmm0246, xmm1357, 16, _masm);
  montmul(xmm8_11, xmm12_15, xmm20_23, xmm16_19, xmm8_11, _masm);
  __ evmovdquw(xmm16, Address(perms, 128), Assembler::AVX_512bit);
  __ evmovdquw(xmm24, Address(perms, 192), Assembler::AVX_512bit);
  load4regs(xmm20_23, zetas, 1024, _masm);
  sub_add(xmm1357, xmm0246, xmm0246, xmm8_11, _masm);

  // level 4
  permute(xmm16_19, xmm0246, xmm1357, 24, _masm);
  montmul(xmm8_11, xmm0246, xmm20_23, xmm24_27, xmm8_11, _masm);
  __ evmovdquw(xmm1, Address(perms, 256), Assembler::AVX_512bit);
  __ evmovdquw(xmm24, Address(perms, 320), Assembler::AVX_512bit);
  load4regs(xmm20_23, zetas, 1280, _masm);
  sub_add(xmm12_15, xmm0246, xmm16_19, xmm8_11, _masm);

  // level 5
  permute(xmm1357, xmm0246, xmm12_15, 24, _masm);
  montmul(xmm16_19, xmm0246, xmm20_23, xmm16_19, xmm8_11, _masm);

  __ evmovdquw(xmm12, Address(perms, 384), Assembler::AVX_512bit);
  __ evmovdquw(xmm8, Address(perms, 448), Assembler::AVX_512bit);

  load4regs(xmm20_23, zetas, 1536, _masm);
  sub_add(xmm24_27, xmm0246, xmm1357, xmm16_19, _masm);

  // level 6
  permute(xmm12_15, xmm0246, xmm24_27, 8, _masm);

  __ evmovdquw(xmm1, Address(perms, 512), Assembler::AVX_512bit);
  __ evmovdquw(xmm24, Address(perms, 576), Assembler::AVX_512bit);

  montmul(xmm16_19, xmm0246, xmm20_23, xmm16_19, xmm8_11, _masm);
  sub_add(xmm20_23, xmm0246, xmm12_15, xmm16_19, _masm);

  permute(xmm1357, xmm0246, xmm20_23, 24, _masm);

  store4regs(coeffs, 0, xmm0_3, _masm);
  store4regs(coeffs, 256, xmm4_7, _masm);

  __ leave(); // required for proper stackwalking of RuntimeStub frame
  __ mov64(rax, 0); // return 0
  __ ret(0);

  return start;
}

// Kyber Inverse NTT function
//
// coeffs (short[256]) = c_rarg0
// ntt_zetas (short[256]) = c_rarg1
address generate_kyberInverseNtt_avx512(StubGenerator *stubgen,
                                        MacroAssembler *_masm) {

  __ align(CodeEntryAlignment);
  StubId stub_id = StubId::stubgen_kyberInverseNtt_id;
  StubCodeMark mark(stubgen, stub_id);
  address start = __ pc();
  __ enter();

  const Register coeffs = c_rarg0;
  const Register zetas = c_rarg1;

  const Register perms = r11;

  __ lea(perms, ExternalAddress(kyberAvx512InverseNttPermsAddr()));
  __ evmovdquw(xmm12, Address(perms, 0), Assembler::AVX_512bit);
  __ evmovdquw(xmm16, Address(perms, 64), Assembler::AVX_512bit);

  __ vpbroadcastq(xmm31,
                  ExternalAddress(kyberAvx512ConstsAddr(qInvModROffset)),
                  Assembler::AVX_512bit, scratch); // q^-1 mod montR
  __ vpbroadcastq(xmm30,
                  ExternalAddress(kyberAvx512ConstsAddr(qOffset)),
                  Assembler::AVX_512bit, scratch); // q
  __ vpbroadcastq(xmm29,
                  ExternalAddress(kyberAvx512ConstsAddr(dimHalfInverseOffset)),
                  Assembler::AVX_512bit, scratch); // (dim/2)^-1 mod q

  load4regs(xmm0_3, coeffs, 0, _masm);
  load4regs(xmm4_7, coeffs, 256, _masm);

  // Each level represents one iteration of the outer for loop of the Java version.
  // level 0
  load4regs(xmm8_11, zetas, 0, _masm);
  permute(xmm12_15, xmm0246, xmm1357, 16, _masm);

  __ evmovdquw(xmm1, Address(perms, 128), Assembler::AVX_512bit);
  __ evmovdquw(xmm20, Address(perms, 192), Assembler::AVX_512bit);

  sub_add(xmm16_19, xmm0246, xmm0246, xmm12_15, _masm);
  montmul(xmm12_15, xmm16_19, xmm8_11, xmm12_15, xmm8_11, _masm);

  // level 1
  load4regs(xmm8_11, zetas, 256, _masm);
  permute(xmm1357, xmm0246, xmm12_15, 20, _masm);
  sub_add(xmm16_19, xmm0246, xmm1357, xmm0246, _masm);

  __ evmovdquw(xmm1, Address(perms, 256), Assembler::AVX_512bit);
  __ evmovdquw(xmm20, Address(perms, 320), Assembler::AVX_512bit);

  montmul(xmm12_15, xmm16_19, xmm8_11, xmm12_15, xmm8_11, _masm);

  // level2
  load4regs(xmm8_11, zetas, 512, _masm);
  permute(xmm1357, xmm0246, xmm12_15, 20, _masm);
  sub_add(xmm16_19, xmm0246, xmm1357,  xmm0246,_masm);

  __ evmovdquw(xmm1, Address(perms, 384), Assembler::AVX_512bit);
  __ evmovdquw(xmm20, Address(perms, 448), Assembler::AVX_512bit);

  montmul(xmm12_15, xmm16_19, xmm8_11, xmm12_15, xmm8_11, _masm);

  __ vpbroadcastq(xmm16,
                  ExternalAddress(kyberAvx512ConstsAddr(barretMultiplierOffset)),
                  Assembler::AVX_512bit, scratch); // Barrett multiplier
  __ vpbroadcastq(xmm17,
                  ExternalAddress(kyberAvx512ConstsAddr(qOffset)),
                  Assembler::AVX_512bit, scratch); // q

  permute(xmm1357, xmm0246, xmm12_15, 20, _masm);
  barrettReduce(_masm);

// level 3
  load4regs(xmm8_11, zetas, 768, _masm);
  sub_add(xmm16_19, xmm0246, xmm1357, xmm0246, _masm);

  __ evmovdquw(xmm1, Address(perms, 512), Assembler::AVX_512bit);
  __ evmovdquw(xmm20, Address(perms, 576), Assembler::AVX_512bit);

  montmul(xmm12_15, xmm16_19, xmm8_11, xmm12_15, xmm8_11, _masm);
  permute(xmm1357, xmm0246, xmm12_15, 20, _masm);

  // level 4
  load4regs(xmm8_11, zetas, 1024, _masm);

  __ vpbroadcastq(xmm16,
                  ExternalAddress(kyberAvx512ConstsAddr(barretMultiplierOffset)),
                  Assembler::AVX_512bit, scratch); // Barrett multiplier
  __ vpbroadcastq(xmm17,
                  ExternalAddress(kyberAvx512ConstsAddr(qOffset)),
                  Assembler::AVX_512bit, scratch); // q

  sub_add(xmm12_15, xmm0246, xmm0246, xmm1357, _masm);
  montmul(xmm1357, xmm12_15, xmm8_11, xmm1357, xmm8_11, _masm);
  barrettReduce(_masm);

  // level 5
  load4regs(xmm8_11, zetas, 1280, _masm);
  sub_add(xmm12_15, xmm0145, xmm0145, xmm2367, _masm);
  montmul(xmm2367, xmm12_15, xmm8_11, xmm2367, xmm8_11, _masm);

  // level 6
  load4regs(xmm8_11, zetas, 1536, _masm);
  sub_add(xmm12_15, xmm0_3, xmm0_3, xmm4_7, _masm);
  montmul(xmm4_7, xmm12_15, xmm8_11, xmm4_7, xmm8_11, _masm);

  montmul(xmm8_11, xmm29_29, xmm0_3, xmm8_11, xmm0_3, _masm);
  montmul(xmm12_15, xmm29_29, xmm4_7, xmm12_15, xmm4_7, _masm);

  store4regs(coeffs, 0, xmm8_11, _masm);
  store4regs(coeffs, 256, xmm12_15, _masm);

  __ leave(); // required for proper stackwalking of RuntimeStub frame
  __ mov64(rax, 0); // return 0
  __ ret(0);

  return start;
}

// Kyber multiply polynomials in the NTT domain.
//
// result (short[256]) = c_rarg0
// ntta (short[256]) = c_rarg1
// nttb (short[256]) = c_rarg2
// zetas (short[128]) = c_rarg3
address generate_kyberNttMult_avx512(StubGenerator *stubgen,
                                     MacroAssembler *_masm) {

  __ align(CodeEntryAlignment);
  StubId stub_id = StubId::stubgen_kyberNttMult_id;
  StubCodeMark mark(stubgen, stub_id);
  address start = __ pc();
  __ enter();

  const Register result = c_rarg0;
  const Register ntta = c_rarg1;
  const Register nttb = c_rarg2;
  const Register zetas = c_rarg3;

  const Register perms = r11;
  const Register loopCnt = r12;

  __ push_ppx(r12);
  __ movl(loopCnt, 2);

  Label Loop;

  __ lea(perms, ExternalAddress(kyberAvx512_nttMultPermsAddr()));


  load4regs(xmm26_29, perms, 0, _masm);
  __ vpbroadcastq(xmm31,
                  ExternalAddress(kyberAvx512ConstsAddr(qInvModROffset)),
                  Assembler::AVX_512bit, scratch); // q^-1 mod montR
  __ vpbroadcastq(xmm30,
                  ExternalAddress(kyberAvx512ConstsAddr(qOffset)),
                  Assembler::AVX_512bit, scratch); // q
  __ vpbroadcastq(xmm23,
                  ExternalAddress(kyberAvx512ConstsAddr(montRSquareModqOffset)),
                  Assembler::AVX_512bit, scratch); // montR^2 mod q

  __ BIND(Loop);

    __ evmovdquw(xmm1, Address(ntta, 0), Assembler::AVX_512bit);
    __ evmovdquw(xmm8, Address(ntta, 64), Assembler::AVX_512bit);
    __ evmovdquw(xmm3, Address(ntta, 128), Assembler::AVX_512bit);
    __ evmovdquw(xmm9, Address(ntta, 192), Assembler::AVX_512bit);

    __ evmovdquw(xmm5, Address(nttb, 0), Assembler::AVX_512bit);
    __ evmovdquw(xmm10, Address(nttb, 64), Assembler::AVX_512bit);
    __ evmovdquw(xmm7, Address(nttb, 128), Assembler::AVX_512bit);
    __ evmovdquw(xmm11, Address(nttb, 192), Assembler::AVX_512bit);

    __ evmovdquw(xmm0, xmm26, Assembler::AVX_512bit);
    __ evmovdquw(xmm2, xmm26, Assembler::AVX_512bit);
    __ evmovdquw(xmm4, xmm26, Assembler::AVX_512bit);
    __ evmovdquw(xmm6, xmm26, Assembler::AVX_512bit);

    __ evpermi2w(xmm0, xmm1, xmm8, Assembler::AVX_512bit);
    __ evpermt2w(xmm1, xmm27, xmm8, Assembler::AVX_512bit);
    __ evpermi2w(xmm2, xmm3, xmm9, Assembler::AVX_512bit);
    __ evpermt2w(xmm3, xmm27, xmm9, Assembler::AVX_512bit);

    __ evpermi2w(xmm4, xmm5, xmm10, Assembler::AVX_512bit);
    __ evpermt2w(xmm5, xmm27, xmm10, Assembler::AVX_512bit);
    __ evpermi2w(xmm6, xmm7, xmm11, Assembler::AVX_512bit);
    __ evpermt2w(xmm7, xmm27, xmm11, Assembler::AVX_512bit);

    __ evmovdquw(xmm24, Address(zetas, 0), Assembler::AVX_512bit);
    __ evmovdquw(xmm25, Address(zetas, 64), Assembler::AVX_512bit);

    montmul(xmm16_19, xmm1001, xmm5454, xmm16_19, xmm12_15, _masm);

    montmul(xmm0145, xmm3223, xmm7676, xmm0145, xmm12_15, _masm);

    __ evpmullw(xmm2, k0, xmm16, xmm24, false, Assembler::AVX_512bit);
    __ evpmullw(xmm3, k0, xmm0, xmm25, false, Assembler::AVX_512bit);
    __ evpmulhw(xmm12, k0, xmm16, xmm24, false, Assembler::AVX_512bit);
    __ evpmulhw(xmm13, k0, xmm0, xmm25, false, Assembler::AVX_512bit);

    __ evpmullw(xmm2, k0, xmm2, xmm31, false, Assembler::AVX_512bit);
    __ evpmullw(xmm3, k0, xmm3, xmm31, false, Assembler::AVX_512bit);
    __ evpmulhw(xmm2, k0, xmm30, xmm2, false, Assembler::AVX_512bit);
    __ evpmulhw(xmm3, k0, xmm30, xmm3, false, Assembler::AVX_512bit);

    __ evpsubw(xmm2, k0, xmm12, xmm2, false, Assembler::AVX_512bit);
    __ evpsubw(xmm3, k0, xmm13, xmm3, false, Assembler::AVX_512bit);

    __ evpaddw(xmm0, k0, xmm2, xmm17, false, Assembler::AVX_512bit);
    __ evpaddw(xmm8, k0, xmm3, xmm1, false, Assembler::AVX_512bit);
    __ evpaddw(xmm2, k0, xmm18, xmm19, false, Assembler::AVX_512bit);
    __ evpaddw(xmm9, k0, xmm4, xmm5, false, Assembler::AVX_512bit);

    montmul(xmm1357, xmm0829, xmm23_23, xmm1357, xmm0829, _masm);

    __ evmovdquw(xmm0, xmm28, Assembler::AVX_512bit);
    __ evmovdquw(xmm2, xmm28, Assembler::AVX_512bit);
    __ evpermi2w(xmm0, xmm1, xmm5, Assembler::AVX_512bit);
    __ evpermt2w(xmm1, xmm29, xmm5, Assembler::AVX_512bit);
    __ evpermi2w(xmm2, xmm3, xmm7, Assembler::AVX_512bit);
    __ evpermt2w(xmm3, xmm29, xmm7, Assembler::AVX_512bit);

    store4regs(result, 0, xmm0_3, _masm);

    __ addptr(ntta, 256);
    __ addptr(nttb, 256);
    __ addptr(result, 256);
    __ addptr(zetas, 128);
    __ subl(loopCnt, 1);
    __ jcc(Assembler::greater, Loop);

  __ pop_ppx(r12);

  __ leave(); // required for proper stackwalking of RuntimeStub frame
  __ mov64(rax, 0); // return 0
  __ ret(0);

  return start;
}

// Kyber add 2 polynomials.
//
// result (short[256]) = c_rarg0
// a (short[256]) = c_rarg1
// b (short[256]) = c_rarg2
address generate_kyberAddPoly_2_avx512(StubGenerator *stubgen,
                                       MacroAssembler *_masm) {

  __ align(CodeEntryAlignment);
  StubId stub_id = StubId::stubgen_kyberAddPoly_2_id;
  StubCodeMark mark(stubgen, stub_id);
  address start = __ pc();
  __ enter();

  const Register result = c_rarg0;
  const Register a = c_rarg1;
  const Register b = c_rarg2;

  __ vpbroadcastq(xmm31,
                  ExternalAddress(kyberAvx512ConstsAddr(qOffset)),
                  Assembler::AVX_512bit, scratch); // q

  for (int i = 0; i < 8; i++) {
    __ evmovdquw(xmm(i), Address(a, 64 * i), Assembler::AVX_512bit);
    __ evmovdquw(xmm(i + 8), Address(b, 64 * i), Assembler::AVX_512bit);
  }

  for (int i = 0; i < 8; i++) {
    __ evpaddw(xmm(i), k0, xmm(i), xmm(i + 8), false, Assembler::AVX_512bit);
  }

  for (int i = 0; i < 8; i++) {
    __ evpaddw(xmm(i), k0, xmm(i), xmm31, false, Assembler::AVX_512bit);
  }

  store4regs(result, 0, xmm0_3, _masm);
  store4regs(result, 256, xmm4_7, _masm);

  __ leave(); // required for proper stackwalking of RuntimeStub frame
  __ mov64(rax, 0); // return 0
  __ ret(0);

  return start;
}

// Kyber add 3 polynomials.
//
// result (short[256]) = c_rarg0
// a (short[256]) = c_rarg1
// b (short[256]) = c_rarg2
// c (short[256]) = c_rarg3
address generate_kyberAddPoly_3_avx512(StubGenerator *stubgen,
                                       MacroAssembler *_masm) {

  __ align(CodeEntryAlignment);
  StubId stub_id = StubId::stubgen_kyberAddPoly_3_id;
  StubCodeMark mark(stubgen, stub_id);
  address start = __ pc();
  __ enter();

  const Register result = c_rarg0;
  const Register a = c_rarg1;
  const Register b = c_rarg2;
  const Register c = c_rarg3;

  __ vpbroadcastq(xmm31,
                  ExternalAddress(kyberAvx512ConstsAddr(qOffset)),
                  Assembler::AVX_512bit, scratch); // q

  for (int i = 0; i < 8; i++) {
    __ evmovdquw(xmm(i), Address(a, 64 * i), Assembler::AVX_512bit);
    __ evmovdquw(xmm(i + 8), Address(b, 64 * i), Assembler::AVX_512bit);
    __ evmovdquw(xmm(i + 16), Address(c, 64 * i), Assembler::AVX_512bit);
  }

  __ evpaddw(xmm31, k0, xmm31, xmm31, false, Assembler::AVX_512bit);

  for (int i = 0; i < 8; i++) {
    __ evpaddw(xmm(i), k0, xmm(i), xmm(i + 8), false, Assembler::AVX_512bit);
  }

  for (int i = 0; i < 8; i++) {
    __ evpaddw(xmm(i), k0, xmm(i), xmm(i + 16), false, Assembler::AVX_512bit);
  }

  for (int i = 0; i < 8; i++) {
    __ evpaddw(xmm(i), k0, xmm(i), xmm31, false, Assembler::AVX_512bit);
  }

  store4regs(result, 0, xmm0_3, _masm);
  store4regs(result, 256, xmm4_7, _masm);

  __ leave(); // required for proper stackwalking of RuntimeStub frame
  __ mov64(rax, 0); // return 0
  __ ret(0);

  return start;
}

// Kyber parse XOF output to polynomial coefficient candidates.
//
// condensed (byte[168]) = c_rarg0
// condensedOffs (int) = c_rarg1
// parsed (short[112]) = c_rarg2
// parsedLength (int) = c_rarg3
address generate_kyber12To16_avx512(StubGenerator *stubgen,
                                    MacroAssembler *_masm) {

  __ align(CodeEntryAlignment);
  StubId stub_id = StubId::stubgen_kyber12To16_id;
  StubCodeMark mark(stubgen, stub_id);
  address start = __ pc();
  __ enter();

  const Register condensed = c_rarg0;
  const Register condensedOffs = c_rarg1;
  const Register parsed = c_rarg2;
  const Register parsedLength = c_rarg3;

  const Register perms = r11;

  Label Loop;

  __ addptr(condensed, condensedOffs);

  __ lea(perms, ExternalAddress(kyberAvx512_12To16PermsAddr()));

  load4regs(xmm24_27, perms, 0, _masm);
  load4regs(xmm28_31, perms, 256, _masm);
  __ vpbroadcastq(xmm23,
                  ExternalAddress(kyberAvx512ConstsAddr(f00Offset)),
                  Assembler::AVX_512bit, scratch); // 0xF00

  __ BIND(Loop);
    __ evmovdqub(xmm0, Address(condensed, 0),Assembler::AVX_256bit);
    __ evmovdqub(xmm1, Address(condensed, 32),Assembler::AVX_256bit);
    __ evmovdqub(xmm2, Address(condensed, 64),Assembler::AVX_256bit);
    __ evmovdqub(xmm8, Address(condensed, 96),Assembler::AVX_256bit);
    __ evmovdqub(xmm9, Address(condensed, 128),Assembler::AVX_256bit);
    __ evmovdqub(xmm10, Address(condensed, 160),Assembler::AVX_256bit);
    __ vpmovzxbw(xmm0, xmm0, Assembler::AVX_512bit);
    __ vpmovzxbw(xmm1, xmm1, Assembler::AVX_512bit);
    __ vpmovzxbw(xmm2, xmm2, Assembler::AVX_512bit);
    __ vpmovzxbw(xmm8, xmm8, Assembler::AVX_512bit);
    __ vpmovzxbw(xmm9, xmm9, Assembler::AVX_512bit);
    __ vpmovzxbw(xmm10, xmm10, Assembler::AVX_512bit);
    __ evmovdquw(xmm3, xmm24, Assembler::AVX_512bit);
    __ evmovdquw(xmm4, xmm25, Assembler::AVX_512bit);
    __ evmovdquw(xmm5, xmm26, Assembler::AVX_512bit);
    __ evmovdquw(xmm11, xmm24, Assembler::AVX_512bit);
    __ evmovdquw(xmm12, xmm25, Assembler::AVX_512bit);
    __ evmovdquw(xmm13, xmm26, Assembler::AVX_512bit);
    __ evpermi2w(xmm3, xmm0, xmm1, Assembler::AVX_512bit);
    __ evpermi2w(xmm4, xmm0, xmm1, Assembler::AVX_512bit);
    __ evpermi2w(xmm5, xmm0, xmm1, Assembler::AVX_512bit);
    __ evpermi2w(xmm11, xmm8, xmm9, Assembler::AVX_512bit);
    __ evpermi2w(xmm12, xmm8, xmm9, Assembler::AVX_512bit);
    __ evpermi2w(xmm13, xmm8, xmm9, Assembler::AVX_512bit);
    __ evpermt2w(xmm3, xmm27, xmm2, Assembler::AVX_512bit);
    __ evpermt2w(xmm4, xmm28, xmm2, Assembler::AVX_512bit);
    __ evpermt2w(xmm5, xmm29, xmm2, Assembler::AVX_512bit);
    __ evpermt2w(xmm11, xmm27, xmm10, Assembler::AVX_512bit);
    __ evpermt2w(xmm12, xmm28, xmm10, Assembler::AVX_512bit);
    __ evpermt2w(xmm13, xmm29, xmm10, Assembler::AVX_512bit);

    __ evpsraw(xmm2, k0, xmm4, 4, false, Assembler::AVX_512bit);
    __ evpsllw(xmm0, k0, xmm4, 8, false, Assembler::AVX_512bit);
    __ evpsllw(xmm1, k0, xmm5, 4, false, Assembler::AVX_512bit);
    __ evpsllw(xmm8, k0, xmm12, 8, false, Assembler::AVX_512bit);
    __ evpsraw(xmm10, k0, xmm12, 4, false, Assembler::AVX_512bit);
    __ evpsllw(xmm9, k0, xmm13, 4, false, Assembler::AVX_512bit);
    __ evpandq(xmm0, k0, xmm0, xmm23, false, Assembler::AVX_512bit);
    __ evpandq(xmm8, k0, xmm8, xmm23, false, Assembler::AVX_512bit);
    __ evpaddw(xmm1, k0, xmm1, xmm2, false, Assembler::AVX_512bit);
    __ evpaddw(xmm0, k0, xmm0, xmm3, false, Assembler::AVX_512bit);
    __ evmovdquw(xmm2, xmm30, Assembler::AVX_512bit);
    __ evpaddw(xmm9, k0, xmm9, xmm10, false, Assembler::AVX_512bit);
    __ evpaddw(xmm8, k0, xmm8, xmm11, false, Assembler::AVX_512bit);
    __ evmovdquw(xmm10, xmm30, Assembler::AVX_512bit);
    __ evpermi2w(xmm2, xmm0, xmm1, Assembler::AVX_512bit);
    __ evpermt2w(xmm0, xmm31, xmm1, Assembler::AVX_512bit);
    __ evpermi2w(xmm10, xmm8, xmm9, Assembler::AVX_512bit);
    __ evpermt2w(xmm8, xmm31, xmm9, Assembler::AVX_512bit);

    store4regs(parsed, 0, xmm2_0_10_8, _masm);

    __ addptr(condensed, 192);
    __ addptr(parsed, 256);
    __ subl(parsedLength, 128);
    __ jcc(Assembler::greater, Loop);

  __ leave(); // required for proper stackwalking of RuntimeStub frame
  __ mov64(rax, 0); // return 0
  __ ret(0);

  return start;
}


// Kyber barrett reduce function.
//
// coeffs (short[256]) = c_rarg0
address generate_kyberBarrettReduce_avx512(StubGenerator *stubgen,
                                           MacroAssembler *_masm) {

  __ align(CodeEntryAlignment);
  StubId stub_id = StubId::stubgen_kyberBarrettReduce_id;
  StubCodeMark mark(stubgen, stub_id);
  address start = __ pc();
  __ enter();

  const Register coeffs = c_rarg0;

  __ vpbroadcastq(xmm16,
                  ExternalAddress(kyberAvx512ConstsAddr(barretMultiplierOffset)),
                  Assembler::AVX_512bit, scratch); // Barrett multiplier
  __ vpbroadcastq(xmm17,
                  ExternalAddress(kyberAvx512ConstsAddr(qOffset)),
                  Assembler::AVX_512bit, scratch); // q

  load4regs(xmm0_3, coeffs, 0, _masm);
  load4regs(xmm4_7, coeffs, 256, _masm);

  barrettReduce(_masm);

  store4regs(coeffs, 0, xmm0_3, _masm);
  store4regs(coeffs, 256, xmm4_7, _masm);

  __ leave(); // required for proper stackwalking of RuntimeStub frame
  __ mov64(rax, 0); // return 0
  __ ret(0);

  return start;
}

void StubGenerator::generate_kyber_stubs() {
  // Generate Kyber intrinsics code
  if (UseKyberIntrinsics) {
    if (VM_Version::supports_evex()) {
      StubRoutines::_kyberNtt = generate_kyberNtt_avx512(this, _masm);
      StubRoutines::_kyberInverseNtt = generate_kyberInverseNtt_avx512(this, _masm);
      StubRoutines::_kyberNttMult = generate_kyberNttMult_avx512(this, _masm);
      StubRoutines::_kyberAddPoly_2 = generate_kyberAddPoly_2_avx512(this, _masm);
      StubRoutines::_kyberAddPoly_3 = generate_kyberAddPoly_3_avx512(this, _masm);
      StubRoutines::_kyber12To16 = generate_kyber12To16_avx512(this, _masm);
      StubRoutines::_kyberBarrettReduce = generate_kyberBarrettReduce_avx512(this, _masm);
    }
  }
}
