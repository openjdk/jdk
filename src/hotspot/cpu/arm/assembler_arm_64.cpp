/*
 * Copyright (c) 2008, 2018, Oracle and/or its affiliates. All rights reserved.
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
#include "ci/ciEnv.hpp"
#include "gc/shared/cardTableBarrierSet.hpp"
#include "gc/shared/collectedHeap.inline.hpp"
#include "interpreter/interpreter.hpp"
#include "interpreter/interpreterRuntime.hpp"
#include "interpreter/templateInterpreterGenerator.hpp"
#include "memory/resourceArea.hpp"
#include "prims/jvm_misc.hpp"
#include "prims/methodHandles.hpp"
#include "runtime/biasedLocking.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/objectMonitor.hpp"
#include "runtime/os.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/stubRoutines.hpp"
#include "utilities/hashtable.hpp"
#include "utilities/macros.hpp"

// Returns whether given imm has equal bit fields <0:size-1> and <size:2*size-1>.
inline bool Assembler::LogicalImmediate::has_equal_subpatterns(uintx imm, int size) {
  uintx mask = right_n_bits(size);
  uintx subpattern1 = mask_bits(imm, mask);
  uintx subpattern2 = mask_bits(imm >> size, mask);
  return subpattern1 == subpattern2;
}

// Returns least size that is a power of two from 2 to 64 with the proviso that given
// imm is composed of repeating patterns of this size.
inline int Assembler::LogicalImmediate::least_pattern_size(uintx imm) {
  int size = BitsPerWord;
  while (size > 2 && has_equal_subpatterns(imm, size >> 1)) {
    size >>= 1;
  }
  return size;
}

// Returns count of set bits in given imm. Based on variable-precision SWAR algorithm.
inline int Assembler::LogicalImmediate::population_count(uintx x) {
  x -= ((x >> 1) & 0x5555555555555555L);
  x = (((x >> 2) & 0x3333333333333333L) + (x & 0x3333333333333333L));
  x = (((x >> 4) + x) & 0x0f0f0f0f0f0f0f0fL);
  x += (x >> 8);
  x += (x >> 16);
  x += (x >> 32);
  return(x & 0x7f);
}

// Let given x be <A:B> where B = 0 and least bit of A = 1. Returns <A:C>, where C is B-size set bits.
inline uintx Assembler::LogicalImmediate::set_least_zeroes(uintx x) {
  return x | (x - 1);
}


#ifdef ASSERT

// Restores immediate by encoded bit masks.
uintx Assembler::LogicalImmediate::decode() {
  assert (_encoded, "should be");

  int len_code = (_immN << 6) | ((~_imms) & 0x3f);
  assert (len_code != 0, "should be");

  int len = 6;
  while (!is_set_nth_bit(len_code, len)) len--;
  int esize = 1 << len;
  assert (len > 0, "should be");
  assert ((_is32bit ? 32 : 64) >= esize, "should be");

  int levels = right_n_bits(len);
  int S = _imms & levels;
  int R = _immr & levels;

  assert (S != levels, "should be");

  uintx welem = right_n_bits(S + 1);
  uintx wmask = (R == 0) ? welem : ((welem >> R) | (welem << (esize - R)));

  for (int size = esize; size < 64; size <<= 1) {
    wmask |= (wmask << size);
  }

  return wmask;
}

#endif


// Constructs LogicalImmediate by given imm. Figures out if given imm can be used in AArch64 logical
// instructions (AND, ANDS, EOR, ORR) and saves its encoding.
void Assembler::LogicalImmediate::construct(uintx imm, bool is32) {
  _is32bit = is32;

  if (is32) {
    assert(((imm >> 32) == 0) || (((intx)imm >> 31) == -1), "32-bit immediate is out of range");

    // Replicate low 32 bits.
    imm &= 0xffffffff;
    imm |= imm << 32;
  }

  // All-zeroes and all-ones can not be encoded.
  if (imm != 0 && (~imm != 0)) {

    // Let LPS (least pattern size) be the least size (power of two from 2 to 64) of repeating
    // patterns in the immediate. If immediate value can be encoded, it is encoded by pattern
    // of exactly LPS size (due to structure of valid patterns). In order to verify
    // that immediate value can be encoded, LPS is calculated and <LPS-1:0> bits of immediate
    // are verified to be valid pattern.
    int lps = least_pattern_size(imm);
    uintx lps_mask = right_n_bits(lps);

    // A valid pattern has one of the following forms:
    //  | 0 x A | 1 x B | 0 x C |, where B > 0 and C > 0, or
    //  | 1 x A | 0 x B | 1 x C |, where B > 0 and C > 0.
    // For simplicity, the second form of the pattern is inverted into the first form.
    bool inverted = imm & 0x1;
    uintx pattern = (inverted ? ~imm : imm) & lps_mask;

    //  | 0 x A | 1 x (B + C)   |
    uintx without_least_zeroes = set_least_zeroes(pattern);

    // Pattern is valid iff without least zeroes it is a power of two - 1.
    if ((without_least_zeroes & (without_least_zeroes + 1)) == 0) {

      // Count B as population count of pattern.
      int bits_count = population_count(pattern);

      // Count B+C as population count of pattern without least zeroes
      int left_range = population_count(without_least_zeroes);

      // S-prefix is a part of imms field which encodes LPS.
      //  LPS  |  S prefix
      //   64  |     not defined
      //   32  |     0b0
      //   16  |     0b10
      //    8  |     0b110
      //    4  |     0b1110
      //    2  |     0b11110
      int s_prefix = (lps == 64) ? 0 : ~set_least_zeroes(lps) & 0x3f;

      // immN bit is set iff LPS == 64.
      _immN = (lps == 64) ? 1 : 0;
      assert (!is32 || (_immN == 0), "32-bit immediate should be encoded with zero N-bit");

      // immr is the rotation size.
      _immr = lps + (inverted ? 0 : bits_count) - left_range;

      // imms is the field that encodes bits count and S-prefix.
      _imms = ((inverted ? (lps - bits_count) : bits_count) - 1) | s_prefix;

      _encoded = true;
      assert (decode() == imm, "illegal encoding");

      return;
    }
  }

  _encoded = false;
}
