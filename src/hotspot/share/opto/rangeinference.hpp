/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_OPTO_RANGEINFERENCE_HPP
#define SHARE_OPTO_RANGEINFERENCE_HPP

#include "opto/type.hpp"
#include "runtime/os.hpp"

template <class T, class U>
void normalize_constraints(bool& empty, T& lo, T& hi, U& ulo, U& uhi, U& zeros, U& ones);

#ifdef ASSERT
template <class T, class U>
void verify_constraints(T lo, T hi, U ulo, U uhi, U zeros, U ones);
#endif

// The result is tuned down by one since we do not have empty type
// and this is not required to be accurate
template <class T, class U>
U cardinality_from_bounds(T lo, T hi, U ulo, U uhi) {
  if (U(lo) == ulo) {
    return uhi - ulo;
  }

  return uhi - U(lo) + U(hi) - ulo + 1;
}

template <class T, class U>
int normalize_widen(T lo, T hi, U ulo, U uhi, U zeros, U ones, int w);

template <class CT, class T = decltype(CT::_lo), class UT = decltype(CT::_hi)>
const Type* int_type_xmeet(const CT* i1, const Type* t2, const Type* (*make)(T, T, UT, UT, UT, UT, int, bool), bool dual);

template <class CT>
bool int_type_equal(const CT* t1, const CT* t2) {
  return t1->_lo == t2->_lo && t1->_hi == t2->_hi && t1->_ulo == t2->_ulo && t1->_uhi == t2->_uhi &&
         t1->_zeros == t2->_zeros && t1->_ones == t2->_ones;
}

template <class CT>
bool int_type_subset(const CT* super, const CT* sub) {
  return super->_lo <= sub->_lo && super->_hi >= sub->_hi && super->_ulo <= sub->_ulo && super->_uhi >= sub->_uhi &&
         (super->_zeros &~ sub->_zeros) == 0 && (super->_ones &~ sub->_ones) == 0;
}

template <class CT>
const Type* int_type_widen(const CT* nt, const CT* ot, const CT* lt);

template <class CT>
const Type* int_type_narrow(const CT* nt, const CT* ot);

#ifndef PRODUCT
const char* intname(char* buf, size_t buf_size, jint n);
const char* uintname(char* buf, size_t buf_size, juint n);
const char* longname(char* buf, size_t buf_size, jlong n);
const char* ulongname(char* buf, size_t buf_size, julong n);

template <class U>
const char* bitname(char* buf, size_t buf_size, U zeros, U ones);
#endif // PRODUCT

#endif // SHARE_OPTO_RANGEINFERENCE_HPP
