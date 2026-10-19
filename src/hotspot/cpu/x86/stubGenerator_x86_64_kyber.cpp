/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
ATTRIBUTE_ALIGNED(64) static const uint16_t kyberAvxConsts[] = {
  0xF301, 0xF301, 0xF301, 0xF301, // q^-1 mod montR
  0x0D01, 0x0D01, 0x0D01, 0x0D01, // q
  0x4EBF, 0x4EBF, 0x4EBF, 0x4EBF, // Barrett multiplier
  0x0200, 0x0200, 0x0200, 0x0200, //(dim/2)^-1 mod q
  0x0549, 0x0549, 0x0549, 0x0549, // montR^2 mod q
  0x0F00, 0x0F00, 0x0F00, 0x0F00, // mask for kyber12to16
  0x0010, 0x0001, 0x0010, 0x0001, // multiplier for kyber12to16 variable shift
};

static int qInvModROffset = 0;
static int qOffset = 8;
static int barretMultiplierOffset = 16;
static int dimHalfInverseOffset = 24;
static int montRSquareModqOffset = 32;
static int f00Offset = 40;
static int k12t16MultOffset = 48;

static address kyberAvxConstsAddr(int offset) {
  return ((address) kyberAvxConsts) + offset;
}

ATTRIBUTE_ALIGNED(64) static const uint8_t kyberNttMultShuffle[] = {
  2, 3, 0, 1, 6, 7, 4, 5, 10, 11, 8, 9, 14, 15, 12, 13,
  2, 3, 0, 1, 6, 7, 4, 5, 10, 11, 8, 9, 14, 15, 12, 13,
  2, 3, 0, 1, 6, 7, 4, 5, 10, 11, 8, 9, 14, 15, 12, 13,
  2, 3, 0, 1, 6, 7, 4, 5, 10, 11, 8, 9, 14, 15, 12, 13
};

static address kyberNttMultShuffleAddr() {
  return (address) kyberNttMultShuffle;
}

ATTRIBUTE_ALIGNED(64) static const uint8_t kyberAvx212To16Shuffle[] = {
   0, 1,  1, 2,  3, 4,  4, 5,  6, 7,  7, 8,  9, 10, 10, 11,
   4, 5,  5, 6,  7, 8,  8, 9, 10, 11, 11, 12, 13, 14, 14, 15,
   4, 5,  5, 6,  7, 8,  8, 9, 10, 11, 11, 12, 13, 14, 14, 15,
   4, 5,  5, 6,  7, 8,  8, 9, 10, 11, 11, 12, 13, 14, 14, 15
};

static address kyberAvx212To16ShuffleAddr() {
  return (address) kyberAvx212To16Shuffle;
}

ATTRIBUTE_ALIGNED(64) static const uint32_t unshufflePerms[] = {
  // Shuffle for the 128-bit element swap (uint64_t)
  0, 0, 1,  0, 8,  0, 9, 0, 4, 0, 5, 0, 12, 0, 13, 0,
  10, 0, 11, 0, 2, 0, 3, 0, 14, 0, 15, 0, 6, 0, 7, 0,

  // Final shuffle for KyberNtt
  0, 16, 1, 17, 2, 18, 3, 19, 4, 20, 5, 21, 6, 22, 7, 23,
  24, 8, 25, 9, 26, 10, 27, 11, 28, 12, 29, 13, 30, 14, 31, 15,

  // Initial shuffle for KyberInverseNtt
  0, 2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26, 28, 30,
  17, 19, 21, 23, 25, 27, 29, 31, 1, 3, 5, 7, 9, 11, 13, 15
};

static address unshufflePermsAddr(int offset) {
  return ((address) unshufflePerms) + offset*64;
}

// Table from FIPS 203, Appendix A, converted to montgomery domain (x*2^16 %3329)
// and biased around -q/2 to q/2 then rearranged to match generate_kyberNttMult_avx
// order (for AVX512: interleave kyberNttMultZetas[0-15] and [16-31] and so on,
// for AVX2: interleave kyberNttMultZetas[0-7] and [8-15] and so on)
ATTRIBUTE_ALIGNED(64) static const int16_t kyberNttMultZetas[] = {
  // AVX512
  -1103, 422, 1103, -422, 430, 587, -430, -587, 555, 177, -555, -177, 843, -235, -843, 235,
  -1251, -291, 1251, 291, 871, -460, -871, 460, 1550, 1574, -1550, -1574, 105, 1653, -105, -1653,
  -246, -1590, 246, 1590, 778, 644, -778, -644, 1159, -872, -1159, 872, -147, 349, 147, -349,
  -777, 418, 777, -418, 1483, 329, -1483, -329, -602, -156, 602, 156, 1119, -75, -1119, 75,
  817, -1215, -817, 1215, 1097, -136, -1097, 136, 603, 1218, -603, -1218, 610, -1335, -610, 1335,
  1322, -874, -1322, 874, -1285, 220, 1285, -220, -1465, -1187, 1465, 1187, 384, -1659, -384, 1659,
  -1185, -108, 1185, 108, -1530, -308, 1530, 308, -1278, 996, 1278, -996, 794, 991, -794, -991,
  -1510, 958, 1510, -958, -854, -1460, 854, 1460, -870, 1522, 870, -1522, 478, 1628, -478, -1628,

  // AVX2
  -1103, -1251, 1103, 1251, 430, 871, -430, -871, 555, 1550, -555, -1550, 843, 105, -843, -105,
  422, -291, -422, 291, 587, -460, -587, 460, 177, 1574, -177, -1574, -235, 1653, 235, -1653,
  -246, -777, 246, 777, 778, 1483, -778, -1483, 1159, -602, -1159, 602, -147, 1119, 147, -1119,
  -1590, 418, 1590, -418, 644, 329, -644, -329, -872, -156, 872, 156, 349, -75, -349, 75,
  817, 1322, -817, -1322, 1097, -1285, -1097, 1285, 603, -1465, -603, 1465, 610, 384, -610, -384,
  -1215, -874, 1215, 874, -136, 220, 136, -220, 1218, -1187, -1218, 1187, -1335, -1659, 1335, 1659,
  -1185, -1510, 1185, 1510, -1530, -854, 1530, 854, -1278, -870, 1278, 870, 794, 478, -794, -478,
  -108, 958, 108, -958, -308, -1460, 308, 1460, 996, 1522, -996, -1522, 991, 1628, -991, -1628,
};

static address kyberNttMultZetasAddr(int offset, int vector_len) {
  offset += vector_len == Assembler::AVX_512bit ? 0 : 256;
  return ((address) kyberNttMultZetas) + offset;
}

ATTRIBUTE_ALIGNED(64) static const uint8_t kyberAvx512_12To16Dup[] = {
// 0 - 63
    0, 1, 1, 2, 3, 4, 4, 5, 6, 7, 7, 8, 9, 10, 10, 11, 12, 13, 13, 14, 15, 16,
    16, 17, 18, 19, 19, 20, 21, 22, 22, 23, 24, 25, 25, 26, 27, 28, 28, 29, 30,
    31, 31, 32, 33, 34, 34, 35, 36, 37, 37, 38, 39, 40, 40, 41, 42, 43, 43, 44,
    45, 46, 46, 47
  };

static address kyberAvx512_12To16DupAddr() {
  return (address) kyberAvx512_12To16Dup;
}

ATTRIBUTE_ALIGNED(64) static const uint16_t kyberAvx512_12To16Shift[] = {
// 0 - 31
    0, 4, 0, 4, 0, 4, 0, 4, 0, 4, 0, 4, 0, 4, 0, 4, 0, 4, 0, 4, 0, 4, 0, 4, 0,
    4, 0, 4, 0, 4, 0, 4
  };

static address kyberAvx512_12To16ShiftAddr() {
  return (address) kyberAvx512_12To16Shift;
}

ATTRIBUTE_ALIGNED(64) static const uint64_t kyberAvx512_12To16And[] = {
// 0 - 7
    0x0FFF0FFF0FFF0FFF, 0x0FFF0FFF0FFF0FFF, 0x0FFF0FFF0FFF0FFF,
    0x0FFF0FFF0FFF0FFF, 0x0FFF0FFF0FFF0FFF, 0x0FFF0FFF0FFF0FFF,
    0x0FFF0FFF0FFF0FFF, 0x0FFF0FFF0FFF0FFF
  };

static address kyberAvx512_12To16AndAddr() {
  return (address) kyberAvx512_12To16And;
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
static void montMul(const XMMRegister output[], const XMMRegister input1[], const XMMRegister input2[],
  const XMMRegister scratch[], const XMMRegister qInvModR, const XMMRegister kyber_q, int vector_len, MacroAssembler *_masm, int regCnt = 4) {
  // This function is on a path with most register pressure, so there are several 'clever'
  // uses that deserve explanation
  // - input1 and scratch might be the same
  // - input2 and scratch might overlap
  // since this function is internal to this file, we dont go out of the way to overly verify inputs
  const XMMRegister* scratch1 = scratch == input1 ? output : scratch;
  const XMMRegister* scratch2 = scratch == input1 ? scratch : output;
  bool input2ScratchOverlap = input2[0] == scratch1[1];

  for (int i = 0; i < regCnt; i++) {
    // input2 and scratch2|output might be partially overlapping, both instructions together
    // (later loop iteration might overwrite input2 otherwise)
    __ vpmullw(scratch1[i], input1[i], input2[i], vector_len);
    if (input2ScratchOverlap) {
      __ vpmulhw(scratch2[i], input1[i], input2[i], vector_len);
    }
  }
  for (int i = 0; !input2ScratchOverlap && i < regCnt; i++) {
    // the 'else' case of input2ScratchOverlap.. emit vpmulhw if it wasn't before
    __ vpmulhw(scratch2[i], input1[i], input2[i], vector_len);
  }
  for (int i = 0; i < regCnt; i++) {
    __ vpmullw(scratch1[i], scratch1[i], qInvModR, vector_len);
  }
  for (int i = 0; i < regCnt; i++) {
    __ vpmulhw(scratch1[i], scratch1[i], kyber_q, vector_len);
  }
  for (int i = 0; i < regCnt; i++) {
    __ vpsubw(output[i], scratch2[i], scratch1[i], vector_len);
  }
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
// NOTE: size 0 and 1 are used for final and initial shuffles respectively of
// kyberNtt and kyberInverseNtt. For size 0 and 1, input1[]
// and input2[] are modified in-place (and output2 is used as a temporary)
//
// Using C++ lambdas for improved readability (to hide parameters that always repeat)
static auto whole_shuffle(KRegister mergeMask1, KRegister mergeMask2,
  const XMMRegister unshuffle1, const XMMRegister unshuffle2, int vector_len, MacroAssembler *_masm, int regCnt = 4) {

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
        case 16:
          for (int i = 0; i < regCnt; i++) {
            __ vpshufb(output2[i], input1[i], unshuffle1, vector_len);
          }
          for (int i = 0; i < regCnt; i++) {
            __ vpblendw(output2[i], output2[i], input2[i], 0b10101010, vector_len);
          }
          for (int i = 0; i < regCnt; i++) {
            __ vpshufb(input2[i], input2[i], unshuffle1, vector_len);
          }
          for (int i = 0; i < regCnt; i++) {
            __ vpblendw(input1[i], input1[i], input2[i], 0b10101010, vector_len);
          }
          break;
        // Special cases
        case 1: // initial shuffle for kyberInverseNtt
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
        case 0: // final unshuffle for kyberNtt
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
        case 16:
          for (int i = 0; i < regCnt; i++) {
            __ vmovdqu(output2[i], input2[i], vector_len);
            __ evpshufb(output2[i], mergeMask1, input1[i], unshuffle1, true, vector_len);
            __ evpshufb(input1[i], mergeMask2, input2[i], unshuffle1, true, vector_len);
          }
          break;
        // Special cases
        case 1: // initial shuffle for kyberInverseNtt
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
        case 0: // final unshuffle for kyberNtt
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

static void sub_add(const XMMRegister subResult[], const XMMRegister addResult[],
                    const XMMRegister input1[], const XMMRegister input2[],
                    int vector_len, MacroAssembler *_masm) {
  int regCnt = 4;
  for (int i = 0; i < regCnt; i++) {
    __ vpsubw(subResult[i], input1[i], input2[i], vector_len);
  }

  for (int i = 0; i < regCnt; i++) {
    __ vpaddw(addResult[i], input1[i], input2[i], vector_len);
  }
}

static void store4regs(Register address, int offset, int sourceRegs[],
                       MacroAssembler *_masm) {
  for (int i = 0; i < 4; i++) {
    __ evmovdquw(Address(address, offset + i * 64), xmm(sourceRegs[i]),
                 Assembler::AVX_512bit);
  }
}

static void loadXmms(const XMMRegister destinationRegs[], Register source, int offset,
                     int vector_len, MacroAssembler *_masm, int memStep = -1) {
  if (memStep == -1) {
    memStep = vector_len == Assembler::AVX_512bit ? 64 : 32;
  }

  for (int i = 0; i < 4; i++) {
    __ vmovdqu(destinationRegs[i], Address(source, offset + i * memStep), vector_len);
  }
}

static void storeXmms(Register destination, int offset, const XMMRegister xmmRegs[],
                      int vector_len, MacroAssembler *_masm, int memStep = -1) {
  if (memStep == -1) {
    memStep = vector_len == Assembler::AVX_512bit ? 64 : 32;
  }

  for (int i = 0; i < 4; i++) {
    __ vmovdqu(Address(destination, offset + i * memStep), xmmRegs[i], vector_len);
  }
}

static void barrettReduce(const XMMRegister output[], const XMMRegister scratch[],
     const XMMRegister barretMultiplier, const XMMRegister kyber_q, int vector_len, MacroAssembler *_masm) {
  for (int i = 0; i < 4; i++) {
    __ vpmulhw(scratch[i], output[i], barretMultiplier, vector_len);
  }

  for (int i = 0; i < 4; i++) {
    __ vpsraw(scratch[i], scratch[i], 10, vector_len);
  }

  for (int i = 0; i < 4; i++) {
    __ vpmullw(scratch[i], scratch[i], kyber_q, vector_len);
  }

  for (int i = 0; i < 4; i++) {
    __ vpsubw(output[i], output[i], scratch[i], vector_len);
  }
}

address generate_kyberNtt_avx(StubGenerator *stubgen, int vector_len,
                                 MacroAssembler *_masm) {
  StubId stub_id = StubId::stubgen_kyberNtt_id;
  int entry_count = StubInfo::entry_count(stub_id);
  assert(entry_count == 1, "sanity check");
  address start = stubgen->load_archive_data(stub_id);
  if (start != nullptr) {
    return start;
  }
  __ align(CodeEntryAlignment);
  StubCodeMark mark(stubgen, stub_id);
  start = __ pc();
  __ enter();

  const Register coeffs = c_rarg0;
  const Register zetas = c_rarg1;
  const Register scratch = r10;

  const XMMRegister Coeffs1[] = {xmm0, xmm1, xmm2, xmm3};
  const XMMRegister Coeffs2[] = {xmm4, xmm5, xmm6, xmm7};
  const XMMRegister Zetas1[]  = {xmm12, xmm12, xmm12, xmm12};
  const XMMRegister Zetas2[]  = {xmm12, xmm12, xmm13, xmm13};
  const XMMRegister Zetas3[]   = {xmm9, xmm10, xmm11, xmm12};

  // constants
  const XMMRegister qInvModR = xmm14;
  const XMMRegister kyber_q = xmm15;

  // register-level swaps
  const XMMRegister Coeffs1_1[] = {xmm0, xmm1, xmm4, xmm5};
  const XMMRegister Coeffs2_1[] = {xmm2, xmm3, xmm6, xmm7};
  const XMMRegister Coeffs1_2[] = {xmm0, xmm2, xmm4, xmm6};
  const XMMRegister Coeffs2_2[] = {xmm1, xmm3, xmm5, xmm7};

  // AVX512-only constants
  const XMMRegister unshuffle1 = xmm28;
  const XMMRegister unshuffle2 = xmm29;
  const XMMRegister unshuffle3 = xmm30;
  const XMMRegister unshuffle4 = xmm31;
  KRegister mergeMask1 = k1;
  KRegister mergeMask2 = k2;
  auto shuffle = whole_shuffle(mergeMask1, mergeMask2, unshuffle1, unshuffle2,
                               vector_len, _masm);

  __ vpbroadcastq(qInvModR,
                  ExternalAddress(kyberAvxConstsAddr(qInvModROffset)),
                  vector_len, scratch); // q^-1 mod montR
  __ vpbroadcastq(kyber_q,
                  ExternalAddress(kyberAvxConstsAddr(qOffset)),
                  vector_len, scratch); // q

  if (vector_len == Assembler::AVX_512bit) {
    // have enough registers (unlike AVX2), so less careful reg-alloc
    const XMMRegister Scratch[] =  {xmm16, xmm17, xmm18, xmm19};
    const XMMRegister Scratch2[] = {xmm20, xmm21, xmm22, xmm23};

    // Constants for shuffle
    __ vmovdqu(unshuffle1, ExternalAddress(unshufflePermsAddr(0)), vector_len, scratch);
    __ vmovdqu(unshuffle2, ExternalAddress(unshufflePermsAddr(1)), vector_len, scratch);
    __ vmovdqu(unshuffle3, ExternalAddress(unshufflePermsAddr(2)), vector_len, scratch);
    __ vmovdqu(unshuffle4, ExternalAddress(unshufflePermsAddr(3)), vector_len, scratch);
    __ mov64(scratch, 0b1010101010101010);
    __ kmovwl(mergeMask1, scratch);
    __ knotwl(mergeMask2, mergeMask1);

    int memStep = 4 * 64; // 4*64-byte registers
    loadXmms(Coeffs1, coeffs, 0*memStep, vector_len, _masm);
    loadXmms(Coeffs2, coeffs, 1*memStep, vector_len, _masm);

    // level 0
    // coeffs2 = coeffs2 * zetas1
    // coeffs2, coeffs1 = coeffs1 ± coeffs2
    __ vmovdqu(Zetas1[0], Address(zetas, 0), vector_len);
    montMul(Scratch, Coeffs2, Zetas1, Coeffs2, qInvModR, kyber_q, vector_len, _masm);
    sub_add(Coeffs2, Coeffs1, Coeffs1, Scratch, vector_len, _masm);

    // level 1
    __ vmovdqu(Zetas2[0], Address(zetas,       256), vector_len);
    __ vmovdqu(Zetas2[2], Address(zetas, 128 + 256), vector_len);
    montMul(Scratch, Coeffs2_1, Zetas2, Coeffs2_1, qInvModR, kyber_q, vector_len, _masm);
    sub_add(Coeffs2_1, Coeffs1_1, Coeffs1_1, Scratch, vector_len, _masm);

    // level 2
    loadXmms(Zetas3, zetas, 2 * 256, vector_len, _masm);
    montMul(Scratch, Coeffs2_2, Zetas3, Coeffs2_2, qInvModR, kyber_q, vector_len, _masm);
    sub_add(Coeffs2_2, Coeffs1_2, Coeffs1_2, Scratch, vector_len, _masm);

    for (int level = 3, distance = 16; level<7; level++, distance /= 2) {
      // coeffs1_2, scratch1 = shuffle(coeffs1_2, coeffs2_2)
      // zetas = load(level * 256)
      // scratch1 = scratch1 * zetas
      // coeffs2_2 = coeffs1_2 - scratch1
      // coeffs1_2 = coeffs1_2 + scratch1
      shuffle(Scratch, Coeffs1_2, Coeffs2_2, distance * 16); // Coeffs2_2 freed
      loadXmms(Coeffs2_2, zetas, level * 256, vector_len, _masm);
      montMul(Scratch, Scratch, Coeffs2_2, Scratch2, qInvModR, kyber_q, vector_len, _masm);
      sub_add(Coeffs2_2, Coeffs1_2, Coeffs1_2, Scratch, vector_len, _masm);
    }

    __ vmovdqu(unshuffle1, unshuffle3, vector_len);
    __ vmovdqu(unshuffle2, unshuffle4, vector_len);
    shuffle(Scratch, Coeffs1_2, Coeffs2_2, 0);

    storeXmms(coeffs, 0*memStep, Coeffs1, vector_len, _masm);
    storeXmms(coeffs, 1*memStep, Coeffs2, vector_len, _masm);
  } else {
    // scratch registers for montMul; arranged carefully so not to clobber inputs
    const XMMRegister Scratch[] = {xmm8, xmm9, xmm10, xmm11};  // pair to Zetas3
    const XMMRegister Scratch2[] = {xmm12, xmm1, xmm3, xmm5};  // pair to Coeffs2_2

    // Two batches of 4 registers each, 64 bytes apart
    for (int i = 0; i < 2; i++) {
      loadXmms(Coeffs1, coeffs, i*32 + 0*64, vector_len, _masm, 64);
      loadXmms(Coeffs2, coeffs, i*32 + 4*64, vector_len, _masm, 64);

      // level 0
      // coeffs2 = coeffs2 * zetas1
      // coeffs2, coeffs1 = coeffs1 ± coeffs2
      __ vmovdqu(Zetas1[0], Address(zetas, 0), vector_len);
      montMul(Scratch, Coeffs2, Zetas1, Coeffs2, qInvModR, kyber_q, vector_len, _masm);
      sub_add(Coeffs2, Coeffs1, Coeffs1, Scratch, vector_len, _masm);

      // level 1
      __ vmovdqu(Zetas2[0], Address(zetas,       256), vector_len);
      __ vmovdqu(Zetas2[2], Address(zetas, 128 + 256), vector_len);
      montMul(Scratch, Coeffs2_1, Zetas2, Coeffs2_1, qInvModR, kyber_q, vector_len, _masm);
      sub_add(Coeffs2_1, Coeffs1_1, Coeffs1_1, Scratch, vector_len, _masm);

      // level 2
      loadXmms(Zetas3, zetas, 2 * 256, vector_len, _masm, 64);
      montMul(Scratch, Coeffs2_2, Zetas3, Coeffs2_2, qInvModR, kyber_q, vector_len, _masm);
      sub_add(Coeffs2_2, Coeffs1_2, Coeffs1_2, Scratch, vector_len, _masm);

      storeXmms(coeffs, i*32 + 0*64, Coeffs1, vector_len, _masm, 64);
      storeXmms(coeffs, i*32 + 4*64, Coeffs2, vector_len, _masm, 64);
    }

    // Two batches of 8 registers, consecutive loads
    for (int i=0; i<2; i++) {
      loadXmms(Coeffs1, coeffs,       i*256, vector_len, _masm);
      loadXmms(Coeffs2, coeffs, 128 + i*256, vector_len, _masm);

      // level 3
      loadXmms(Zetas3, zetas, i*128 + 3 * 256, vector_len, _masm);
      montMul(Scratch, Coeffs2_2, Zetas3, Coeffs2_2, qInvModR, kyber_q, vector_len, _masm);
      sub_add(Coeffs2_2, Coeffs1_2, Coeffs1_2, Scratch, vector_len, _masm);

      for (int level = 4, distance = 8; level<7; level++, distance /= 2) {
        // coeffs1_2, scratch1 = shuffle(coeffs1_2, coeffs2_2)
        // zetas = load(level * 256)
        // scratch1 = scratch1 * zetas
        // coeffs2_2 = coeffs1_2 - scratch1
        // coeffs1_2 = coeffs1_2 + scratch1
        shuffle(Scratch, Coeffs1_2, Coeffs2_2, distance * 16); // Coeffs2_2 freed
        loadXmms(Coeffs2_2, zetas, i*128 + level * 256, vector_len, _masm);
        montMul(Scratch, Scratch, Coeffs2_2, Scratch2, qInvModR, kyber_q, vector_len, _masm);
        sub_add(Coeffs2_2, Coeffs1_2, Coeffs1_2, Scratch, vector_len, _masm);
      }

      shuffle(Scratch, Coeffs1_2, Coeffs2_2, 0);

      storeXmms(coeffs,       i*256, Coeffs1, vector_len, _masm);
      storeXmms(coeffs, 128 + i*256, Coeffs2, vector_len, _masm);
    }
  }

  __ leave(); // required for proper stackwalking of RuntimeStub frame
  __ mov64(rax, 0); // Intrinsic returns a value of 0, whereas Java callees return 1
  __ ret(0);

  // record the stub entry and end
  stubgen->store_archive_data(stub_id, start, __ pc());

  return start;
}

// Kyber Inverse NTT function
//
// coeffs (short[256]) = c_rarg0
// ntt_zetas (short[256]) = c_rarg1
address generate_kyberInverseNtt_avx(StubGenerator *stubgen, int vector_len,
                                        MacroAssembler *_masm) {
  StubId stub_id = StubId::stubgen_kyberInverseNtt_id;
  int entry_count = StubInfo::entry_count(stub_id);
  assert(entry_count == 1, "sanity check");
  address start = stubgen->load_archive_data(stub_id);
  if (start != nullptr) {
    return start;
  }
  __ align(CodeEntryAlignment);
  StubCodeMark mark(stubgen, stub_id);
  start = __ pc();
  __ enter();

  const Register coeffs = c_rarg0;
  const Register zetas = c_rarg1;
  const Register scratch = r10;

  const XMMRegister Coeffs1[] = {xmm0, xmm1, xmm2, xmm3};
  const XMMRegister Coeffs2[] = {xmm4, xmm5, xmm6, xmm7};
  const XMMRegister Scratch[] = {xmm8, xmm9, xmm10, xmm11};

  // constants
  const XMMRegister barretMultiplier = xmm13;
  const XMMRegister qInvModR = xmm14;
  const XMMRegister kyber_q = xmm15;

  // register-level swaps
  const XMMRegister Coeffs1_1[] = {xmm0, xmm1, xmm4, xmm5};
  const XMMRegister Coeffs2_1[] = {xmm2, xmm3, xmm6, xmm7};
  const XMMRegister Coeffs1_2[] = {xmm0, xmm2, xmm4, xmm6};
  const XMMRegister Coeffs2_2[] = {xmm1, xmm3, xmm5, xmm7};

  // AVX512-only constants
  const XMMRegister unshuffle1 = xmm28;
  const XMMRegister unshuffle2 = xmm29;
  const XMMRegister unshuffle3 = xmm30;
  const XMMRegister unshuffle4 = xmm31;
  KRegister mergeMask1 = k1;
  KRegister mergeMask2 = k2;
  auto shuffle = whole_shuffle(mergeMask1, mergeMask2, unshuffle1, unshuffle2,
                               vector_len, _masm);

  __ vpbroadcastq(qInvModR,
                  ExternalAddress(kyberAvxConstsAddr(qInvModROffset)),
                  vector_len, scratch); // q^-1 mod montR
  __ vpbroadcastq(kyber_q,
                  ExternalAddress(kyberAvxConstsAddr(qOffset)),
                  vector_len, scratch); // q
  __ vpbroadcastq(barretMultiplier,
                  ExternalAddress(kyberAvxConstsAddr(barretMultiplierOffset)),
                  vector_len, scratch); // Barret Multiplier

  if (vector_len == Assembler::AVX_512bit) {
    // Zetas
    const XMMRegister Zetas1[] = {xmm12, xmm12, xmm12, xmm12};
    const XMMRegister Zetas2[] = {xmm24, xmm24, xmm25, xmm25};

    // have enough registers (unlike AVX2), so less careful reg-alloc
    const XMMRegister Scratch2[] = {xmm16, xmm17, xmm18, xmm19};
    const XMMRegister Scratch3[] = {xmm20, xmm21, xmm22, xmm23};

    // Constants for shuffles
    __ vmovdqu(unshuffle1, ExternalAddress(unshufflePermsAddr(4)), vector_len, scratch);
    __ vmovdqu(unshuffle2, ExternalAddress(unshufflePermsAddr(5)), vector_len, scratch);
    __ vmovdqu(unshuffle3, ExternalAddress(unshufflePermsAddr(0)), vector_len, scratch);
    __ vmovdqu(unshuffle4, ExternalAddress(unshufflePermsAddr(1)), vector_len, scratch);

    // Constants for shuffle
    __ mov64(scratch, 0b1010101010101010);
    __ kmovwl(mergeMask1, scratch);
    __ knotwl(mergeMask2, mergeMask1);

    int memStep = 4 * 64; // 4*64-byte registers
    loadXmms(Coeffs1, coeffs, 0*memStep, vector_len, _masm);
    loadXmms(Coeffs2, coeffs, 1*memStep, vector_len, _masm);
    shuffle(Scratch, Coeffs1_2, Coeffs2_2, 1);
    __ vmovdqu(unshuffle1, unshuffle3, vector_len);
    __ vmovdqu(unshuffle2, unshuffle4, vector_len);

    for (int level = 0, distance = 2; level < 4; level++, distance *= 2) {
      // coeffs1_2, scratch1 = coeffs1_2 ± coeffs2_2
      // coeffs2_2 = load(zetas, level * 256)
      // scratch1 = scratch1 * coeffs2_2
      // coeffs1_2, coeffs2_2 = shuffle(coeffs1_2, scratch1)
      sub_add(Scratch, Coeffs1_2, Coeffs1_2, Coeffs2_2, vector_len, _masm); // Coeffs2_2 freed
      loadXmms(Coeffs2_2, zetas, level * 256, vector_len, _masm);
      montMul(Scratch, Scratch, Coeffs2_2, Scratch2, qInvModR, kyber_q, vector_len, _masm);
      if (level == 2) {
        barrettReduce(Coeffs1_2, Coeffs2_2, barretMultiplier, kyber_q, vector_len, _masm); // Coeffs2_2 as scratch
      }
      shuffle(Coeffs2_2, Coeffs1_2, Scratch, distance * 16);
    }

    // level 4
    sub_add(Scratch, Coeffs1_2, Coeffs1_2, Coeffs2_2, vector_len, _masm); // Coeffs2_2 freed
    loadXmms(Coeffs2_2, zetas, 4 * 256, vector_len, _masm);
    montMul(Coeffs2_2, Coeffs2_2, Scratch, Scratch3, qInvModR, kyber_q, vector_len, _masm);

    barrettReduce(Coeffs1_2, Scratch, barretMultiplier, kyber_q, vector_len, _masm);

    // level 5
    sub_add(Scratch, Coeffs1_1, Coeffs1_1, Coeffs2_1, vector_len, _masm); // Coeffs2_1 freed
    __ vmovdqu(Zetas2[0], Address(zetas,       5 * 256), vector_len);
    __ vmovdqu(Zetas2[2], Address(zetas, 128 + 5 * 256), vector_len);
    montMul(Coeffs2_1, Zetas2, Scratch, Scratch3, qInvModR, kyber_q, vector_len, _masm);

    // level 6
    __ vmovdqu(Zetas1[0], Address(zetas,       6 * 256), vector_len);
    sub_add(Scratch, Coeffs1, Coeffs1, Coeffs2, vector_len, _masm); // Coeffs2 freed
    montMul(Coeffs2, Scratch, Zetas1, Scratch, qInvModR, kyber_q, vector_len, _masm);

    __ vpbroadcastq(Zetas1[0],
                ExternalAddress(kyberAvxConstsAddr(dimHalfInverseOffset)),
                vector_len, scratch); // (dim/2)^-1 mod q
    montMul(Coeffs1, Coeffs1, Zetas1, Scratch, qInvModR, kyber_q, vector_len, _masm);
    montMul(Coeffs2, Coeffs2, Zetas1, Scratch, qInvModR, kyber_q, vector_len, _masm);

    storeXmms(coeffs, 0*memStep, Coeffs1, vector_len, _masm);
    storeXmms(coeffs, 1*memStep, Coeffs2, vector_len, _masm);
  } else {
    // Zetas
    const XMMRegister Zetas1[] = {xmm12, xmm12, xmm12, xmm12}; // NOT overloaded
    const XMMRegister Zetas2[] = { xmm6,  xmm6,  xmm7,  xmm7}; // pair to Coeffs2_1

    // scratch registers for montMul; arranged carefully so not to clobber inputs
    const XMMRegister Scratch2[] = {xmm12, xmm1, xmm3, xmm5};  // pair to Coeffs2_2
    const XMMRegister Scratch3[] = {xmm12, xmm8, xmm9, xmm10}; // pair to Scratch

    // Two batches of 8 registers, consecutive loads
    for (int i = 0; i < 2; i++) {
      loadXmms(Coeffs1, coeffs,       i*256, vector_len, _masm);
      loadXmms(Coeffs2, coeffs, 128 + i*256, vector_len, _masm);

      shuffle(Scratch, Coeffs1_2, Coeffs2_2, 1);

      for (int level = 0, distance = 2; level < 3; level++, distance *= 2) {
        // coeffs1_2, scratch1 = coeffs1_2 ± coeffs2_2
        // coeffs2_2 = load(zetas, level * 256)
        // scratch1 = scratch1 * coeffs2_2
        // coeffs1_2, coeffs2_2 = shuffle(coeffs1_2, scratch1)
        sub_add(Scratch, Coeffs1_2, Coeffs1_2, Coeffs2_2, vector_len, _masm); // Coeffs2_2 freed
        loadXmms(Coeffs2_2, zetas, i*128 + level * 256, vector_len, _masm);
        montMul(Scratch, Scratch, Coeffs2_2, Scratch2, qInvModR, kyber_q, vector_len, _masm);
        if (level == 2) {
          barrettReduce(Coeffs1_2, Coeffs2_2, barretMultiplier, kyber_q, vector_len, _masm); // Coeffs2_2 as scratch
        }
        shuffle(Coeffs2_2, Coeffs1_2, Scratch, distance * 16);
      }

      // level 3
      sub_add(Scratch, Coeffs1_2, Coeffs1_2, Coeffs2_2, vector_len, _masm); // Coeffs2_2 freed
      loadXmms(Coeffs2_2, zetas, i*128 + 3 * 256, vector_len, _masm);
      montMul(Coeffs2_2, Coeffs2_2, Scratch, Scratch3, qInvModR, kyber_q, vector_len, _masm);

      storeXmms(coeffs,       i*256, Coeffs1, vector_len, _masm);
      storeXmms(coeffs, 128 + i*256, Coeffs2, vector_len, _masm);
    }

    // Two batches of 4 registers each, 64 bytes apart
    for (int i = 0; i < 2; i++) {
      loadXmms(Coeffs1, coeffs, i*32 + 0*64, vector_len, _masm, 64);
      loadXmms(Coeffs2, coeffs, i*32 + 4*64, vector_len, _masm, 64);

      // level 4
      sub_add(Scratch, Coeffs1_2, Coeffs1_2, Coeffs2_2, vector_len, _masm); // Coeffs2_2 freed
      loadXmms(Coeffs2_2, zetas, 4 * 256, vector_len, _masm, 64);
      montMul(Coeffs2_2, Coeffs2_2, Scratch, Scratch3, qInvModR, kyber_q, vector_len, _masm);

      // level 5
      sub_add(Scratch, Coeffs1_1, Coeffs1_1, Coeffs2_1, vector_len, _masm); // Coeffs2_1 freed
      __ vmovdqu(Zetas2[0], Address(zetas,       5 * 256), vector_len);
      __ vmovdqu(Zetas2[2], Address(zetas, 128 + 5 * 256), vector_len);
      montMul(Coeffs2_1, Zetas2, Scratch, Scratch3, qInvModR, kyber_q, vector_len, _masm);

      barrettReduce(Coeffs1_1, Scratch, barretMultiplier, kyber_q, vector_len, _masm);

      // level 6
      __ vmovdqu(Zetas1[0], Address(zetas,       6 * 256), vector_len);
      sub_add(Scratch, Coeffs1, Coeffs1, Coeffs2, vector_len, _masm); // Coeffs2 freed
      montMul(Coeffs2, Scratch, Zetas1, Scratch, qInvModR, kyber_q, vector_len, _masm);

      __ vpbroadcastq(Zetas1[0],
                  ExternalAddress(kyberAvxConstsAddr(dimHalfInverseOffset)),
                  vector_len, scratch); // (dim/2)^-1 mod q
      montMul(Coeffs1, Coeffs1, Zetas1, Scratch, qInvModR, kyber_q, vector_len, _masm);
      montMul(Coeffs2, Coeffs2, Zetas1, Scratch, qInvModR, kyber_q, vector_len, _masm);

      storeXmms(coeffs, i*32 + 0*64, Coeffs1, vector_len, _masm, 64);
      storeXmms(coeffs, i*32 + 4*64, Coeffs2, vector_len, _masm, 64);
    }
  }

  __ leave(); // required for proper stackwalking of RuntimeStub frame
  __ mov64(rax, 0); // Intrinsic returns a value of 0, whereas Java callees return 1
  __ ret(0);

  // record the stub entry and end
  stubgen->store_archive_data(stub_id, start, __ pc());

  return start;
}

// Kyber multiply polynomials in the NTT domain.
// Implements
// static int implKyberNttMult(
//              short[] result, short[] ntta, short[] nttb, short[] zetas) {}
//
// The actual algorithm that is used here differs from the one in the Java
// implementation, it uses Montgomery multiplications instead of Barrett
// reduction, but the end result modulo MLKEM_Q is the same. This is the
// Java equivalent of this intrinsic implementation:
// static void implKyberNttMultJava(short[] result, short[] ntta, short[] nttb) {
//         for (int m = 0; m < ML_KEM_N / 2; m++) {
//             int a0 = ntta[2 * m];
//             int a1 = ntta[2 * m + 1];
//             int b0 = nttb[2 * m];
//             int b1 = nttb[2 * m + 1];
//             int r = montMul(a0, b0) +
//                     montMul(montMul(a1, b1), MONT_ZETAS_FOR_NTT_MULT[m]);
//             result[2 * m] = (short) montMul(r, MONT_R_SQUARE_MOD_Q);
//             result[2 * m + 1] = (short) montMul(
//                     (montMul(a0, b1) + montMul(a1, b0)), MONT_R_SQUARE_MOD_Q);
//          }
// }
//
// result (short[256]) = c_rarg0
// ntta (short[256]) = c_rarg1
// nttb (short[256]) = c_rarg2
// zetas (short[128]) = c_rarg3
address generate_kyberNttMult_avx(StubGenerator *stubgen, int vector_len,
                                     MacroAssembler *_masm) {
  StubId stub_id = StubId::stubgen_kyberNttMult_id;
  int entry_count = StubInfo::entry_count(stub_id);
  assert(entry_count == 1, "sanity check");
  address start = stubgen->load_archive_data(stub_id);
  if (start != nullptr) {
    return start;
  }
  __ align(CodeEntryAlignment);
  StubCodeMark mark(stubgen, stub_id);
  start = __ pc();
  __ enter();
  // for each pair
  //   res[i]   = Const2 * ( a[i]*b[i] + a[i+1]*b[i+1]*ZetaConst1[i] )
  //   res[i+1] = Const2 * ( a[i]*b[i+1] + a[i+1]*b[i] )
  // montMult(b, c) // High/Low split at 20 bit on java, 16 in intrinsic
  //   a = b * c
  //   aLow = aLow * Const1
  //   r = aHigh - HighBits(aLow * Const2)

  const Register result = c_rarg0;
  const Register ntta = c_rarg1;
  const Register nttb = c_rarg2;
  const Register zetas = c_rarg3; // Unused: have own Zetas preshuffled for shuffle(16)
  const Register scratch = r10;
  int memStep = vector_len == Assembler::AVX_512bit ? 64 : 32;
  int regCnt = vector_len == Assembler::AVX_512bit ? 4 : 2;
  int itrCnt = vector_len == Assembler::AVX_512bit ? 1 : 4;

  const XMMRegister A[] = {xmm0, xmm8, xmm1, xmm9, xmm16, xmm24, xmm17, xmm25};
  const XMMRegister A1[] = {xmm0, xmm1, xmm16, xmm17};
  const XMMRegister A2[] = {xmm2, xmm3, xmm18, xmm19};
  const XMMRegister T1[] = {xmm8, xmm9, xmm24, xmm25};
  const XMMRegister B[] = {xmm4, xmm10, xmm5, xmm11, xmm20, xmm26, xmm21, xmm27};
  const XMMRegister B1[] = {xmm4, xmm5, xmm20, xmm21};
  const XMMRegister B2[] = {xmm6, xmm7, xmm22, xmm23};
  const XMMRegister T2[] = {xmm10, xmm11, xmm26, xmm27};
  const XMMRegister montRSquareModq[] = {xmm12, xmm12, xmm12, xmm12};
  const XMMRegister shuffleWords = xmm13;
  const XMMRegister qInvModR = xmm14;
  const XMMRegister kyber_q = xmm15;
  KRegister mergeMask1 = k1;
  KRegister mergeMask2 = k2;
  auto shuffle = whole_shuffle(mergeMask1, mergeMask2, shuffleWords, xnoreg,
                               vector_len, _masm, regCnt);

  if (vector_len == Assembler::AVX_512bit) {
    __ mov64(scratch, 0b0011001100110011001100110011001100110011001100110011001100110011);
    __ kmovql(mergeMask1, scratch);
    __ knotql(mergeMask2, mergeMask1);
  }

  __ vpbroadcastq(montRSquareModq[0],
                  ExternalAddress(kyberAvxConstsAddr(montRSquareModqOffset)),
                  vector_len, scratch); // montR^2 mod q
  __ vpbroadcastq(qInvModR,
                  ExternalAddress(kyberAvxConstsAddr(qInvModROffset)),
                  vector_len, scratch); // q^-1 mod montR
  __ vpbroadcastq(kyber_q,
                  ExternalAddress(kyberAvxConstsAddr(qOffset)),
                  vector_len, scratch); // q
  __ vmovdqu(shuffleWords,
                  ExternalAddress(kyberNttMultShuffleAddr()),
                  vector_len, scratch); // vpshufb table

  for (int iteration = 0; iteration < itrCnt; iteration++) {
    int memOffset = iteration * memStep * regCnt;
    for (int i = 0; i < 2 * regCnt; i++) {
      __ vmovdqu(A[i], Address(ntta, 2 * memOffset + i * memStep), vector_len);
      __ vmovdqu(B[i], Address(nttb, 2 * memOffset + i * memStep), vector_len);
    }

    shuffle(A2, A1, T1, 16);
    shuffle(B2, B1, T2, 16);
    montMul(T1, A1, B1, T2, qInvModR, kyber_q, vector_len, _masm, regCnt);
    const XMMRegister* Scratch = A1;
    montMul(T2, A1, B2, Scratch, qInvModR, kyber_q, vector_len, _masm, regCnt);
    montMul(B1, B1, A2, Scratch, qInvModR, kyber_q, vector_len, _masm, regCnt);

    for (int i = 0; i < regCnt; i++) {
      __ vpaddw(T2[i], T2[i], B1[i], vector_len);
    }
    for (int i = 0; i < regCnt; i++) {
      __ vmovdqu(B1[i], ExternalAddress(kyberNttMultZetasAddr(memOffset + i * memStep, vector_len)), vector_len, scratch);
    }

    montMul(T2, T2, montRSquareModq, Scratch, qInvModR, kyber_q, vector_len, _masm, regCnt);
    montMul(B2, B2, A2, Scratch, qInvModR, kyber_q, vector_len, _masm, regCnt);
    montMul(B2, B2, B1, Scratch, qInvModR, kyber_q, vector_len, _masm, regCnt);
    for (int i = 0; i < regCnt; i++) {
      __ vpaddw(T1[i], T1[i], B2[i], vector_len);
    }
    montMul(A1, T1, montRSquareModq, T1, qInvModR, kyber_q, vector_len, _masm, regCnt);
    shuffle(T1, A1, T2, 16);
    for (int i = 0; i < 2 * regCnt; i++) {
      __ vmovdqu(Address(result, 2 * memOffset + i * memStep), A[i], vector_len);
    }
  }

  __ leave(); // required for proper stackwalking of RuntimeStub frame
  __ mov64(rax, 0); // Intrinsic returns a value of 0, whereas Java callees return 1
  __ ret(0);

  // record the stub entry and end
  stubgen->store_archive_data(stub_id, start, __ pc());

  return start;
}

// Kyber add 2 polynomials.
//
// result (short[256]) = c_rarg0
// a (short[256]) = c_rarg1
// b (short[256]) = c_rarg2
address generate_kyberAddPoly_2_avx(StubGenerator *stubgen, int vector_len,
                                       MacroAssembler *_masm) {
  StubId stub_id = StubId::stubgen_kyberAddPoly_2_id;
  int entry_count = StubInfo::entry_count(stub_id);
  assert(entry_count == 1, "sanity check");
  address start = stubgen->load_archive_data(stub_id);
  if (start != nullptr) {
    return start;
  }
  __ align(CodeEntryAlignment);
  StubCodeMark mark(stubgen, stub_id);
  start = __ pc();
  __ enter();

  const Register result = c_rarg0;
  const Register a = c_rarg1;
  const Register b = c_rarg2;
  const Register scratch = r10;

  int regCnt = 4;
  int memStep = 32;
  if (vector_len == Assembler::AVX_512bit) {
    regCnt = 8;
    memStep = 64;
  }

  const XMMRegister kyber_q = xmm0;
  XMMRegister A[8];
  XMMRegister B[8];

  XMMRegister _next = xmm1;
  for (int i = 0; i < regCnt; i++) {
    A[i] = _next;
    _next = _next->successor();
    B[i] = _next;
    _next = _next->successor();
  }

  __ vpbroadcastq(kyber_q,
                  ExternalAddress(kyberAvxConstsAddr(qOffset)),
                  vector_len, scratch); // q

  for (int memOffset = 0; memOffset < 512; memOffset += regCnt * memStep) {
    for (int i = 0; i < regCnt; i++) {
      __ vmovdqu(A[i], Address(a, memOffset + memStep * i), vector_len);
      __ vmovdqu(B[i], Address(b, memOffset + memStep * i), vector_len);
    }

    for (int i = 0; i < regCnt; i++) {
      __ vpaddw(A[i], A[i], B[i], vector_len);
    }

    for (int i = 0; i < regCnt; i++) {
      __ vpaddw(A[i], A[i], kyber_q, vector_len);
    }

    for (int i = 0; i < regCnt; i++) {
      __ vmovdqu(Address(result, memOffset + memStep * i), A[i], vector_len);
    }
  }

  __ leave(); // required for proper stackwalking of RuntimeStub frame
  __ mov64(rax, 0); // Intrinsic returns a value of 0, whereas Java callees return 1
  __ ret(0);

  // record the stub entry and end
  stubgen->store_archive_data(stub_id, start, __ pc());

  return start;
}

// Kyber add 3 polynomials.
//
// result (short[256]) = c_rarg0
// a (short[256]) = c_rarg1
// b (short[256]) = c_rarg2
// c (short[256]) = c_rarg3
address generate_kyberAddPoly_3_avx(StubGenerator *stubgen, int vector_len,
                                       MacroAssembler *_masm) {
  StubId stub_id = StubId::stubgen_kyberAddPoly_3_id;
  int entry_count = StubInfo::entry_count(stub_id);
  assert(entry_count == 1, "sanity check");
  address start = stubgen->load_archive_data(stub_id);
  if (start != nullptr) {
    return start;
  }
  __ align(CodeEntryAlignment);
  StubCodeMark mark(stubgen, stub_id);
  start = __ pc();
  __ enter();

  const Register result = c_rarg0;
  const Register a = c_rarg1;
  const Register b = c_rarg2;
  const Register c = c_rarg3;
  const Register scratch = r10;

  int regCnt = 4;
  int memStep = 32;
  if (vector_len == Assembler::AVX_512bit) {
    regCnt = 8;
    memStep = 64;
  }

  const XMMRegister kyber_q = xmm0;
  XMMRegister A[8];
  XMMRegister B[8];
  XMMRegister C[8];

  XMMRegister _next = xmm1;
  for (int i = 0; i < regCnt; i++) {
    A[i] = _next;
    _next = _next->successor();
    B[i] = _next;
    _next = _next->successor();
    C[i] = _next;
    _next = _next->successor();
  }

  __ vpbroadcastq(kyber_q,
                  ExternalAddress(kyberAvxConstsAddr(qOffset)),
                  vector_len, scratch); // q
  __ vpaddw(kyber_q, kyber_q, kyber_q, vector_len);

  for (int memOffset = 0; memOffset < 512; memOffset += regCnt * memStep) {
    for (int i = 0; i < regCnt; i++) {
      __ vmovdqu(A[i], Address(a, memOffset + memStep * i), vector_len);
      __ vmovdqu(B[i], Address(b, memOffset + memStep * i), vector_len);
      __ vmovdqu(C[i], Address(c, memOffset + memStep * i), vector_len);
    }

    for (int i = 0; i < regCnt; i++) {
      __ vpaddw(A[i], A[i], B[i], vector_len);
    }

    for (int i = 0; i < regCnt; i++) {
      __ vpaddw(A[i], A[i], C[i], vector_len);
    }

    for (int i = 0; i < regCnt; i++) {
      __ vpaddw(A[i], A[i], kyber_q, vector_len);
    }

    for (int i = 0; i < regCnt; i++) {
      __ vmovdqu(Address(result, memOffset + memStep * i), A[i], vector_len);
    }
  }

  __ leave(); // required for proper stackwalking of RuntimeStub frame
  __ mov64(rax, 0); // Intrinsic returns a value of 0, whereas Java callees return 1
  __ ret(0);

  // record the stub entry and end
  stubgen->store_archive_data(stub_id, start, __ pc());

  return start;
}

// Kyber parse XOF output to polynomial coefficient candidates.
//
// condensed (byte[168]) = c_rarg0
// condensedOffs (int) = c_rarg1
// parsed (short[112]) = c_rarg2
// parsedLength (int) = c_rarg3
address generate_kyber12To16_avx(StubGenerator *stubgen, int vector_len,
                                    MacroAssembler *_masm) {
  StubId stub_id = StubId::stubgen_kyber12To16_id;
  int entry_count = StubInfo::entry_count(stub_id);
  assert(entry_count == 1, "sanity check");
  address start = stubgen->load_archive_data(stub_id);
  if (start != nullptr) {
    return start;
  }
  __ align(CodeEntryAlignment);
  StubCodeMark mark(stubgen, stub_id);
  start = __ pc();
  __ enter();

  const Register condensed = c_rarg0;
  const Register condensedOffs = c_rarg1;
  const Register parsed = c_rarg2;
  const Register parsedLength = c_rarg3;

  const Register scratch = r10;
  const Register perms = r11;

  Label Loop, VBMILoop;

  __ addptr(condensed, condensedOffs);

  if (vector_len == Assembler::AVX_256bit) {
    const XMMRegister shuffleWords = xmm0;
    const XMMRegister varShift = xmm1;
    XMMRegister P[8];

    int regCnt = 8;
    int memStepBig = 32;
    int memStepSmall = 24;

    XMMRegister _next = xmm2;
    for (int i = 0; i < 8; i++) {
      P[i] = _next;
      _next = _next->successor();
    }

    __ vpbroadcastq(varShift,
                ExternalAddress(kyberAvxConstsAddr(k12t16MultOffset)),
                vector_len, scratch); // {16, 1} repeated
    __ vmovdqu(shuffleWords,
                ExternalAddress(kyberAvx212To16ShuffleAddr()),
                vector_len, scratch); // vpshufb table

    __ align(OptoLoopAlignment);
    __ BIND(Loop);

    for (int i = 0; i < regCnt; i++) { //load 0-127 bits, stride 192|384 bits
      __ vmovdqu(P[i], Address(condensed, i*memStepSmall), Assembler::AVX_128bit);
    }
    for (int i = 0; i < regCnt; i++) { //load 64-191 bits, stride 192|384 bits
      __ vinserti128(P[i], P[i], Address(condensed, 8 + i*memStepSmall), 1);
    }

    for (int i = 0; i < regCnt; i++) {
      __ vpshufb(P[i], P[i], shuffleWords, vector_len);
    }
    for (int i = 0; i < regCnt; i++) {
      __ vpmullw(P[i], P[i], varShift, vector_len);
    }
    for (int i = 0; i < regCnt; i++) {
      __ vpsrlw(P[i], P[i], 4, vector_len);
    }
    for (int i = 0; i < regCnt; i++) {
      __ vmovdqu(Address(parsed, i*memStepBig), P[i], vector_len);
    }

    __ addptr(condensed, 192);
    __ addptr(parsed, 256);
    __ subl(parsedLength, 128);
    __ jcc(Assembler::greater, Loop);

    __ leave(); // required for proper stackwalking of RuntimeStub frame
    __ mov64(rax, 0); // Intrinsic returns a value of 0, whereas Java callees return 1
    __ ret(0);

    // record the stub entry and end
    stubgen->store_archive_data(stub_id, start, __ pc());

    return start;
  }

  int xmm0_3[] = {0, 1, 2, 3};
  int xmm24_27[] = {24, 25, 26, 27};
  int xmm28_31[] = {28, 29, 30, 31};
  int xmm2_0_10_8[] = {2, 0, 10, 8};

  if (VM_Version::supports_avx512_vbmi()) {
    // mask load for the first 48 bytes of each vector
    __ mov64(rax, 0x0000FFFFFFFFFFFF);
    __ kmovql(k1, rax);

    __ lea(perms, ExternalAddress(kyberAvx512_12To16DupAddr()));
    __ evmovdqub(xmm20, Address(perms), Assembler::AVX_512bit);

    __ lea(perms, ExternalAddress(kyberAvx512_12To16ShiftAddr()));
    __ evmovdquw(xmm21, Address(perms), Assembler::AVX_512bit);

    __ lea(perms, ExternalAddress(kyberAvx512_12To16AndAddr()));
    __ evmovdquq(xmm22, Address(perms), Assembler::AVX_512bit);

    __ align(OptoLoopAlignment);
    __ BIND(VBMILoop);

      __ evmovdqub(xmm0, k1, Address(condensed, 0), false,
                   Assembler::AVX_512bit);
      __ evmovdqub(xmm1, k1, Address(condensed, 48), false,
                   Assembler::AVX_512bit);
      __ evmovdqub(xmm2, k1, Address(condensed, 96), false,
                   Assembler::AVX_512bit);
      __ evmovdqub(xmm3, k1, Address(condensed, 144), false,
                   Assembler::AVX_512bit);

      __ evpermb(xmm4, k0, xmm20, xmm0, false, Assembler::AVX_512bit);
      __ evpermb(xmm5, k0, xmm20, xmm1, false, Assembler::AVX_512bit);
      __ evpermb(xmm6, k0, xmm20, xmm2, false, Assembler::AVX_512bit);
      __ evpermb(xmm7, k0, xmm20, xmm3, false, Assembler::AVX_512bit);

      __ evpsrlvw(xmm4, xmm4, xmm21, Assembler::AVX_512bit);
      __ evpsrlvw(xmm5, xmm5, xmm21, Assembler::AVX_512bit);
      __ evpsrlvw(xmm6, xmm6, xmm21, Assembler::AVX_512bit);
      __ evpsrlvw(xmm7, xmm7, xmm21, Assembler::AVX_512bit);

      __ evpandq(xmm0, xmm22, xmm4, Assembler::AVX_512bit);
      __ evpandq(xmm1, xmm22, xmm5, Assembler::AVX_512bit);
      __ evpandq(xmm2, xmm22, xmm6, Assembler::AVX_512bit);
      __ evpandq(xmm3, xmm22, xmm7, Assembler::AVX_512bit);

      store4regs(parsed, 0, xmm0_3, _masm);

      __ addptr(condensed, 192);
      __ addptr(parsed, 256);
      __ subl(parsedLength, 128);
      __ jcc(Assembler::greater, VBMILoop);

    __ leave(); // required for proper stackwalking of RuntimeStub frame
    __ mov64(rax, 0); // Intrinsic returns a value of 0, whereas Java callees return 1
    __ ret(0);

    // record the stub entry and end
    stubgen->store_archive_data(stub_id, start, __ pc());

    return start;
  }

  __ lea(perms, ExternalAddress(kyberAvx512_12To16PermsAddr()));

  load4regs(xmm24_27, perms, 0, _masm);
  load4regs(xmm28_31, perms, 256, _masm);
  __ vpbroadcastq(xmm23,
                  ExternalAddress(kyberAvxConstsAddr(f00Offset)),
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
  __ mov64(rax, 0); // Intrinsic returns a value of 0, whereas Java callees return 1
  __ ret(0);

  // record the stub entry and end
  stubgen->store_archive_data(stub_id, start, __ pc());

  return start;
}

// Kyber barrett reduce function.
//
// coeffs (short[256]) = c_rarg0
address generate_kyberBarrettReduce_avx(StubGenerator *stubgen, int vector_len,
                                           MacroAssembler *_masm) {
  StubId stub_id = StubId::stubgen_kyberBarrettReduce_id;
  int entry_count = StubInfo::entry_count(stub_id);
  assert(entry_count == 1, "sanity check");
  address start = stubgen->load_archive_data(stub_id);
  if (start != nullptr) {
    return start;
  }
  __ align(CodeEntryAlignment);
  StubCodeMark mark(stubgen, stub_id);
  start = __ pc();
  __ enter();

  const Register coeffs = c_rarg0;
  const Register scratch = r10;

  const XMMRegister barretMultiplier = xmm0;
  const XMMRegister kyber_q = xmm1;
  const XMMRegister Scratch[] = {xmm2, xmm3, xmm4, xmm5};
  const XMMRegister Coeffs[] = {xmm6, xmm7, xmm8, xmm9};

  __ vpbroadcastq(kyber_q,
                  ExternalAddress(kyberAvxConstsAddr(qOffset)),
                  vector_len, scratch); // q
  __ vpbroadcastq(barretMultiplier,
                  ExternalAddress(kyberAvxConstsAddr(barretMultiplierOffset)),
                  vector_len, scratch); // Barret Multiplier

  int memStep = 32;
  if (vector_len == Assembler::AVX_512bit) {
    memStep = 64;
  }

  for (int memOffset = 0; memOffset < 512; memOffset += 4 * memStep) {
    loadXmms(Coeffs, coeffs, memOffset, vector_len, _masm);
    barrettReduce(Coeffs, Scratch, barretMultiplier, kyber_q, vector_len, _masm);
    storeXmms(coeffs, memOffset, Coeffs, vector_len, _masm);
  }

  __ leave(); // required for proper stackwalking of RuntimeStub frame
  __ mov64(rax, 0); // Intrinsic returns a value of 0, whereas Java callees return 1
  __ ret(0);

  // record the stub entry and end
  stubgen->store_archive_data(stub_id, start, __ pc());

  return start;
}

void StubGenerator::generate_kyber_stubs() {
  int vector_len = Assembler::AVX_256bit;
  if (VM_Version::supports_evex() && VM_Version::supports_avx512bw()) {
    vector_len = Assembler::AVX_512bit;
  }

  // Generate Kyber intrinsics code
  if (UseKyberIntrinsics) {
    StubRoutines::_kyberNtt = generate_kyberNtt_avx(this, vector_len, _masm);
    StubRoutines::_kyberNttMult = generate_kyberNttMult_avx(this, vector_len, _masm);
    StubRoutines::_kyberInverseNtt = generate_kyberInverseNtt_avx(this, vector_len, _masm);
    StubRoutines::_kyberAddPoly_2 = generate_kyberAddPoly_2_avx(this, vector_len, _masm);
    StubRoutines::_kyberAddPoly_3 = generate_kyberAddPoly_3_avx(this, vector_len, _masm);
    StubRoutines::_kyberBarrettReduce = generate_kyberBarrettReduce_avx(this, vector_len, _masm);
    StubRoutines::_kyber12To16 = generate_kyber12To16_avx(this, vector_len, _masm);
  }
}

#if INCLUDE_CDS
void StubGenerator::init_AOTAddressTable_kyber(GrowableArray<address>& external_addresses) {
#define ADD(addr) external_addresses.append((address)(addr))
  // use accessors to correctly identify the relevant addresses
  ADD(kyberAvx512_12To16PermsAddr());
  ADD(kyberAvx512_12To16DupAddr());
  ADD(kyberAvx512_12To16ShiftAddr());
  ADD(kyberAvx512_12To16AndAddr());
  ADD(kyberNttMultShuffleAddr());
  ADD(kyberAvx212To16ShuffleAddr());
  for (int o = 0; o < 6; o++)       { ADD(unshufflePermsAddr(o)); }
  for (int o = 0; o < 256; o += 64) { ADD(kyberNttMultZetasAddr(o, Assembler::AVX_512bit)); }
  for (int o = 0; o < 256; o += 32) { ADD(kyberNttMultZetasAddr(o, Assembler::AVX_256bit)); }
  ADD(kyberAvxConstsAddr(qOffset));
  ADD(kyberAvxConstsAddr(qInvModROffset));
  ADD(kyberAvxConstsAddr(dimHalfInverseOffset));
  ADD(kyberAvxConstsAddr(barretMultiplierOffset));
  ADD(kyberAvxConstsAddr(montRSquareModqOffset));
  ADD(kyberAvxConstsAddr(f00Offset));
  ADD(kyberAvxConstsAddr(k12t16MultOffset));
#undef ADD
}
#endif // INCLUDE_CDS
