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
// We use the KnownBits information from the integer types to derive how many one bits
// we have at least and at most.
// From the definition of KnownBits, we know:
//   zeros: Indicates which bits must be 0: zeros[i]=1 -> t[i]=0
//   ones:  Indicates which bits must be 1: ones[i]=1 -> t[i]=1
//
// From this, we derive:
//   numer_of_zeros_in_t >= pop_count(zeros)
//   -> number_of_ones_in_t <= bits_per_type - pop_count(zeros) = pop_count(~zeros)
//   number_of_ones_in_t >= pop_count(ones)
//
// By definition:
//   pop_count(t) = number_of_ones_in_t
//
// It follows:
//   pop_count(ones) <= pop_count(t) <= pop_count(~zeros)
//
// Note: signed _lo and _hi, as well as unsigned _ulo and _uhi bounds of the integer types
//       are already reflected in the KnownBits information, see TypeInt / TypeLong definitions.
const Type* PopCountINode::Value(PhaseGVN* phase) const {
  const Type* t = phase->type(in(1));
  if (t == Type::TOP) {
    return Type::TOP;
  }
  const TypeInt* tint = t->is_int();
  KnownBits<juint> bits = tint->_bits;
  return TypeInt::make(population_count(bits._ones), population_count(~bits._zeros), tint->_widen);

}

const Type* PopCountLNode::Value(PhaseGVN* phase) const {
  const Type* t = phase->type(in(1));
  if (t == Type::TOP) {
    return Type::TOP;
  }
  const TypeLong* tlong = t->is_long();
  KnownBits<julong> bits = tlong->_bits;
  return TypeInt::make(population_count(bits._ones), population_count(~bits._zeros), tlong->_widen);
}
