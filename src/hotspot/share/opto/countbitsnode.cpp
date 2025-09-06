/*
 * Copyright (c) 2014, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "opto/countbitsnode.hpp"
#include "opto/opcodes.hpp"
#include "opto/phaseX.hpp"
#include "opto/type.hpp"
#include "utilities/population_count.hpp"

//------------------------------Value------------------------------------------
const Type* CountLeadingZerosINode::Value(PhaseGVN* phase) const {
  const Type* t = phase->type(in(1));
  if (t == Type::TOP) return Type::TOP;
  const TypeInt* ti = t->isa_int();
  if (ti && ti->is_con()) {
    jint i = ti->get_con();
    // HD, Figure 5-6
    if (i == 0)
    return TypeInt::make(BitsPerInt);
    int n = 1;
    unsigned int x = i;
    if (x >> 16 == 0) { n += 16; x <<= 16; }
    if (x >> 24 == 0) { n +=  8; x <<=  8; }
    if (x >> 28 == 0) { n +=  4; x <<=  4; }
    if (x >> 30 == 0) { n +=  2; x <<=  2; }
    n -= x >> 31;
    return TypeInt::make(n);
  }
  return TypeInt::INT;
}

//------------------------------Value------------------------------------------
const Type* CountLeadingZerosLNode::Value(PhaseGVN* phase) const {
  const Type* t = phase->type(in(1));
  if (t == Type::TOP) return Type::TOP;
  const TypeLong* tl = t->isa_long();
  if (tl && tl->is_con()) {
    jlong l = tl->get_con();
    // HD, Figure 5-6
    if (l == 0)
    return TypeInt::make(BitsPerLong);
    int n = 1;
    unsigned int x = (((julong) l) >> 32);
    if (x == 0) { n += 32; x = (int) l; }
    if (x >> 16 == 0) { n += 16; x <<= 16; }
    if (x >> 24 == 0) { n +=  8; x <<=  8; }
    if (x >> 28 == 0) { n +=  4; x <<=  4; }
    if (x >> 30 == 0) { n +=  2; x <<=  2; }
    n -= x >> 31;
    return TypeInt::make(n);
  }
  return TypeInt::INT;
}

//------------------------------Value------------------------------------------
const Type* CountTrailingZerosINode::Value(PhaseGVN* phase) const {
  const Type* t = phase->type(in(1));
  if (t == Type::TOP) return Type::TOP;
  const TypeInt* ti = t->isa_int();
  if (ti && ti->is_con()) {
    jint i = ti->get_con();
    // HD, Figure 5-14
    int y;
    if (i == 0)
    return TypeInt::make(BitsPerInt);
    int n = 31;
    y = i << 16; if (y != 0) { n = n - 16; i = y; }
    y = i <<  8; if (y != 0) { n = n -  8; i = y; }
    y = i <<  4; if (y != 0) { n = n -  4; i = y; }
    y = i <<  2; if (y != 0) { n = n -  2; i = y; }
    y = i <<  1; if (y != 0) { n = n -  1; }
    return TypeInt::make(n);
  }
  return TypeInt::INT;
}

//------------------------------Value------------------------------------------
const Type* CountTrailingZerosLNode::Value(PhaseGVN* phase) const {
  const Type* t = phase->type(in(1));
  if (t == Type::TOP) return Type::TOP;
  const TypeLong* tl = t->isa_long();
  if (tl && tl->is_con()) {
    jlong l = tl->get_con();
    // HD, Figure 5-14
    int x, y;
    if (l == 0)
    return TypeInt::make(BitsPerLong);
    int n = 63;
    y = (int) l; if (y != 0) { n = n - 32; x = y; } else x = (((julong) l) >> 32);
    y = x << 16; if (y != 0) { n = n - 16; x = y; }
    y = x <<  8; if (y != 0) { n = n -  8; x = y; }
    y = x <<  4; if (y != 0) { n = n -  4; x = y; }
    y = x <<  2; if (y != 0) { n = n -  2; x = y; }
    y = x <<  1; if (y != 0) { n = n -  1; }
    return TypeInt::make(n);
  }
  return TypeInt::INT;
}

/*
Lemma 1: For a given known bits information, _lo and _hi bounds of the corresponding value
        range is computed using the following formulas:-
        - _hi = ~ZEROS
        - _lo = ONES
Proof:-
  - KnownBits.ZEROS and KnownBits.ONES are inferred out of the common prefix of the value range
    delimiting bounds.

  - Thus, ~KnownBits.ZEROS not only includes set bits in the common prefix but also optimistically assumes
    that all other bits not included in the common prefix are also set.

  - Consider the following illustration, which performs round-trip translation
    of a value range via knowbits information, e.g.
    A) Initial value range bounds to infer knownbits.
      _lo = 0b11000100
      _hi = 0b11000110
      _common_prefix      = 0b11000100
      _common_prefix_mask = 0b11111100
      _known_bits.ones    = _lo & _common_prefix_mask  = 0b11000100
      _known_bits.zeros   = ~_lo & _common_prefix_mask = 0b00111000

    B) Now, transform the computed knownbits back to the value range.
      _new_lo = _known_bits.ones  = 0b11000100
      _new_hi = ~known_bits.zeros = 0b11000111

  - We now know that ~KnownBits.ZEROS >= UB >= LB >= KnownBits.ONES
  - Therefore, popcount(ONES) and popcount(~ZEROS) can safely be assumed as the upper and lower
    bounds of the result value range.
*/
const Type* PopCountINode::Value(PhaseGVN* phase) const {
  const Type* t = phase->type(in(1));
  if (t == Type::TOP) {
    return Type::TOP;
  }
  KnownBits<juint> bits = t->isa_int()->_bits;
  return TypeInt::make(population_count(bits._ones), population_count(~bits._zeros), Type::WidenMax);

}

const Type* PopCountLNode::Value(PhaseGVN* phase) const {
  const Type* t = phase->type(in(1));
  if (t == Type::TOP) {
    return Type::TOP;
  }
  KnownBits<julong> bits = t->isa_long()->_bits;
  return TypeInt::make(population_count(bits._ones), population_count(~bits._zeros), Type::WidenMax);
}
