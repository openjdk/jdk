/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2014, 2020, Red Hat Inc. All rights reserved.
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

#include <stdlib.h> // do not reorder
#include <stdint.h> // do not reorder

#include "immediate_aarch64.hpp"
#include "metaprogramming/primitiveConversions.hpp"
#include "utilities/globalDefinitions.hpp"

// there are at most 2^13 possible logical immediate encodings
// however, some combinations of immr and imms are invalid
static const unsigned  LI_TABLE_SIZE = (1 << 13);

static int li_table_entry_count;

// for forward lookup we just use a direct array lookup
// and assume that the cient has supplied a valid encoding
// table[encoding] = immediate
static uint64_t LITable[LI_TABLE_SIZE];

// for reverse lookup we need a sparse map so we store a table of
// immediate and encoding pairs sorted by immediate value

struct li_pair {
  uint64_t immediate;
  uint32_t encoding;
};

static struct li_pair InverseLITable[LI_TABLE_SIZE];

// comparator to sort entries in the inverse table
static int compare_immediate_pair(const void *i1, const void *i2)
{
  struct li_pair *li1 = (struct li_pair *)i1;
  struct li_pair *li2 = (struct li_pair *)i2;
  if (li1->immediate < li2->immediate) {
    return -1;
  }
  if (li1->immediate > li2->immediate) {
    return 1;
  }
  return 0;
}

// helper functions used by expandLogicalImmediate

// for i = 1, ... N result<i-1> = 1 other bits are zero
static inline uint64_t ones(int N)
{
  return (N == 64 ? -1ULL : (1ULL << N) - 1);
}

/*
 * bit twiddling helpers for instruction decode
 */

// 32 bit mask with bits [hi,...,lo] set
static inline uint32_t mask32(int hi = 31, int lo = 0)
{
  int nbits = (hi + 1) - lo;
  return ((1 << nbits) - 1) << lo;
}

static inline uint64_t mask64(int hi = 63, int lo = 0)
{
  int nbits = (hi + 1) - lo;
  return ((1L << nbits) - 1) << lo;
}

// pick bits [hi,...,lo] from val
static inline uint32_t pick32(uint32_t val, int hi = 31, int lo = 0)
{
  return (val & mask32(hi, lo));
}

// pick bits [hi,...,lo] from val
static inline uint64_t pick64(uint64_t val, int hi = 31, int lo = 0)
{
  return (val & mask64(hi, lo));
}

// mask [hi,lo] and shift down to start at bit 0
static inline uint32_t pickbits32(uint32_t val, int hi = 31, int lo = 0)
{
  return (pick32(val, hi, lo) >> lo);
}

// mask [hi,lo] and shift down to start at bit 0
static inline uint64_t pickbits64(uint64_t val, int hi = 63, int lo = 0)
{
  return (pick64(val, hi, lo) >> lo);
}

// result<0> to val<N>
static inline uint64_t pickbit(uint64_t val, int N)
{
  return pickbits64(val, N, N);
}

static inline uint32_t uimm(uint32_t val, int hi, int lo)
{
  return pickbits32(val, hi, lo);
}

// SPEC
//
// bits(M*N) Replicate(bits(M) B, integer N);
//
// given bit string B of width M (M > 0) and count N (N > 0)
// concatenate N copies of B to generate a bit string of width N * M
// (N * M <= 64)
//
// inputs
// bits : bit string to be replicated starting from bit 0
// nbits : width of the bit string string passed in bits
// count : number of copies of bit string to be concatenated
//
// result
// a bit string containing count copies of input bit string
//
static uint64_t replicate(uint64_t bits, int nbits, int count)
{
  assert(count > 0, "must be");
  assert(nbits > 0, "must be");
  assert(count * nbits <= 64, "must be");

  // Special case nbits == 64 since the shift below with that nbits value
  // would result in undefined behavior.
  if (nbits == 64) {
    return bits;
  }

  uint64_t result = 0;
  uint64_t mask = ones(nbits);
  for (int i = 0; i < count ; i++) {
    result <<= nbits;
    result |= (bits & mask);
  }
  return result;
}

// construct a 64 bit immediate value for a logical immediate operation
//
// SPEC:
//
// {(0,_), (1, uint64)} = expandLogicalImmediate(immN, immr, imms)
//
// For valid combinations of immN, immr and imms, this function
// replicates a derived bit string, whose width is a power of 2, into
// a 64 bit result and returns 1.
//
// for invalid combinations it fails and returns 0
//
// - immN and imms together define
//
//    1) the size, 2^k, of the bit string to be replicated (0 < k <= 6)
//
//    2) the number of bits, p, to set in the string (0 < p < 2^k)
//
// - immr defines a right rotation on the bit string determined by
//   immN and imms
//
// bit field construction:
//
// create a bit string of width 2^k
//
// set the bottom p bits to 1
//
// rotate the bit string right by immr bits
//
// replicate the 2^k bit string into 64 bits
//
// derivation of k and p and validity checks:
//
// when immN is 1 then k == 6 and immr/imms are masked to 6 bit
// integers
//
// when immN is 0 then k is the index of the first 0 bit in imms and
// immr/imms are masked to k-bit integers (i.e. any leading 1s and the
// first 0 in imms determine dead bits of imms/immr)
//
// if (pre-masking) immr >= 2^k then fail and return 0 (this is a
// uniqueness constraint that ensures each output bit string is only
// generated by one valid combination of immN, imms and immr).
//
// if k == 0 then fail and return 0. Note that this means that
// 2^k > 1 or equivalently 2^k - 1 > 0
//
// If imms == all 1s (modulo 2^k) then fail and return 0. Note that
// this means that 0 <= imms < 2^k - 1
//
// set p = imms + 1. Consequently, 0 < p < 2^k which is the condition
// that an all 0s or all 1s bit pattern is never generated.
//
// example output:
//
//   11001111_11001111_11001111_11001111_11001111_11001111_11001111_11001111
//
// which corresponds to the inputs
//
//   immN = 0, imms = 110101, immr = 000010
//
// For these inputs k = 3,  2^k = 8, p = 6, rotation = 2
//
// implementation note:
//
// For historical reasons the implementation of this function is much
// more convoluted than is really necessary.

static int expandLogicalImmediate(uint32_t immN, uint32_t immr,
                                  uint32_t imms, uint64_t &bimm)
{
  int len;                 // ought to be <= 6
  uint32_t levels;         // 6 bits
  uint32_t tmask_and;      // 6 bits
  uint32_t wmask_and;      // 6 bits
  uint32_t tmask_or;       // 6 bits
  uint32_t wmask_or;       // 6 bits
  uint64_t imm64;          // 64 bits
  uint64_t tmask, wmask;   // 64 bits
  uint32_t S, R, diff;     // 6 bits?

  if (immN == 1) {
    len = 6; // looks like 7 given the spec above but this cannot be!
  } else {
    len = 0;
    uint32_t val = (~imms & 0x3f);
    for (int i = 5; i > 0; i--) {
      if (val & (1 << i)) {
        len = i;
        break;
      }
    }
    if (len < 1) {
      return 0;
    }
    // for valid inputs leading 1s in immr must be less than leading
    // zeros in imms
    int len2 = 0;                   // ought to be < len
    uint32_t val2 = (~immr & 0x3f);
    for (int i = 5; i > 0; i--) {
      if (!(val2 & (1 << i))) {
        len2 = i;
        break;
      }
    }
    if (len2 >= len) {
      return 0;
    }
  }

  levels = (1 << len) - 1;

  if ((imms & levels) == levels) {
    return 0;
  }

  S = imms & levels;
  R = immr & levels;

 // 6 bit arithmetic!
  diff = S - R;
  tmask_and = (diff | ~levels) & 0x3f;
  tmask_or = (diff & levels) & 0x3f;
  tmask = 0xffffffffffffffffULL;

  for (int i = 0; i < 6; i++) {
    int nbits = 1 << i;
    uint64_t and_bit = pickbit(tmask_and, i);
    uint64_t or_bit = pickbit(tmask_or, i);
    uint64_t and_bits_sub = replicate(and_bit, 1, nbits);
    uint64_t or_bits_sub = replicate(or_bit, 1, nbits);
    uint64_t and_bits_top = (and_bits_sub << nbits) | ones(nbits);
    uint64_t or_bits_top = (UCONST64(0) << nbits) | or_bits_sub;

    tmask = ((tmask
              & (replicate(and_bits_top, 2 * nbits, 32 / nbits)))
             | replicate(or_bits_top, 2 * nbits, 32 / nbits));
  }

  wmask_and = (immr | ~levels) & 0x3f;
  wmask_or = (immr & levels) & 0x3f;

  wmask = 0;

  for (int i = 0; i < 6; i++) {
    int nbits = 1 << i;
    uint64_t and_bit = pickbit(wmask_and, i);
    uint64_t or_bit = pickbit(wmask_or, i);
    uint64_t and_bits_sub = replicate(and_bit, 1, nbits);
    uint64_t or_bits_sub = replicate(or_bit, 1, nbits);
    uint64_t and_bits_top = (ones(nbits) << nbits) | and_bits_sub;
    uint64_t or_bits_top = (or_bits_sub << nbits) | 0;

    wmask = ((wmask
              & (replicate(and_bits_top, 2 * nbits, 32 / nbits)))
             | replicate(or_bits_top, 2 * nbits, 32 / nbits));
  }

  if (diff & (1U << 6)) {
    imm64 = tmask & wmask;
  } else {
    imm64 = tmask | wmask;
  }


  bimm = imm64;
  return 1;
}

// constructor to initialise the lookup tables

static void initLITables();
// Use an empty struct with a constructor as MSVC doesn't support `__attribute__ ((constructor))`
// See https://stackoverflow.com/questions/1113409/attribute-constructor-equivalent-in-vc
static struct initLITables_t { initLITables_t(void) { initLITables(); } } _initLITables;
static void initLITables()
{
  li_table_entry_count = 0;
  for (unsigned index = 0; index < LI_TABLE_SIZE; index++) {
    uint32_t N = uimm(index, 12, 12);
    uint32_t immr = uimm(index, 11, 6);
    uint32_t imms = uimm(index, 5, 0);
    if (expandLogicalImmediate(N, immr, imms, LITable[index])) {
      InverseLITable[li_table_entry_count].immediate = LITable[index];
      InverseLITable[li_table_entry_count].encoding = index;
      li_table_entry_count++;
    }
  }
  // now sort the inverse table
  qsort(InverseLITable, li_table_entry_count,
        sizeof(InverseLITable[0]), compare_immediate_pair);
}

// public APIs provided for logical immediate lookup and reverse lookup

uint64_t logical_immediate_for_encoding(uint32_t encoding)
{
  return LITable[encoding];
}

uint32_t encoding_for_logical_immediate(uint64_t immediate)
{
  struct li_pair pair;
  struct li_pair *result;

  pair.immediate = immediate;

  result = (struct li_pair *)
    bsearch(&pair, InverseLITable, li_table_entry_count,
            sizeof(InverseLITable[0]), compare_immediate_pair);

  if (result) {
    return result->encoding;
  }

  return 0xffffffff;
}

// floating point immediates are encoded in 8 bits
// fpimm[7] = sign bit
// fpimm[6:4] = signed exponent
// fpimm[3:0] = fraction (assuming leading 1)
// i.e. F = s * 1.f * 2^(e - b)

uint64_t fp_immediate_for_encoding(uint32_t imm8, int is_dp)
{
  union {
    float fpval;
    double dpval;
    uint64_t val;
  };

  uint32_t s, e, f;
  s = (imm8 >> 7 ) & 0x1;
  e = (imm8 >> 4) & 0x7;
  f = imm8 & 0xf;
  // the fp value is s * n/16 * 2r where n is 16+e
  fpval = (16.0 + f) / 16.0;
  // n.b. exponent is signed
  if (e < 4) {
    int epos = e;
    for (int i = 0; i <= epos; i++) {
      fpval *= 2.0;
    }
  } else {
    int eneg = 7 - e;
    for (int i = 0; i < eneg; i++) {
      fpval /= 2.0;
    }
  }

  if (s) {
    fpval = -fpval;
  }
  if (is_dp) {
    dpval = (double)fpval;
  }
  return val;
}

uint32_t encoding_for_fp_immediate(float immediate)
{
  // given a float which is of the form
  //
  //     s * n/16 * 2r
  //
  // where n is 16+f and imm1:s, imm4:f, simm3:r
  // return the imm8 result [s:r:f]
  //

  uint32_t val = PrimitiveConversions::cast<uint32_t>(immediate);
  uint32_t s, r, f, res;
  // sign bit is 31
  s = (val >> 31) & 0x1;
  // exponent is bits 30-23 but we only want the bottom 3 bits
  // strictly we ought to check that the bits bits 30-25 are
  // either all 1s or all 0s
  r = (val >> 23) & 0x7;
  // fraction is bits 22-0
  f = (val >> 19) & 0xf;
  res = (s << 7) | (r << 4) | f;
  return res;
}
