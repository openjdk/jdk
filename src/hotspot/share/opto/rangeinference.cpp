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

#include "precompiled.hpp"
#include "opto/rangeinference.hpp"

constexpr juint SMALLINT = 3;  // a value too insignificant to consider widening

template <class T>
static bool adjust_bounds_from_bits(bool& empty, T& lo, T& hi, T zeros, T ones) {
  static_assert(std::is_unsigned<T>::value, "");

  auto adjust_lo = [](T lo, T zeros, T ones) {
    constexpr size_t W = sizeof(T) * 8;
    T zero_violation = lo & zeros;
    T one_violation = ~lo & ones;
    if (zero_violation == 0 && one_violation == 0) {
      return lo;
    }

    if (zero_violation < one_violation) {
      // Align the last violation of ones unset all the lower bits
      // so we don't care about violations of zeros
      juint last_violation = W - 1 - count_leading_zeros(one_violation);
      T alignment = T(1) << last_violation;
      lo = (lo & -alignment) + alignment;
      return lo | ones;
    }

    // Suppose lo = 00110010, zeros = 01010010, ones = 10001000
    // Since the 4-th bit must be 0, we need to align up the lower bound.
    // This results in lo = 01000000, but then the 6-th bit does not match,
    // align up again gives us 10000000.
    // We can align up directly to 10000000 by finding the first place after
    // the highest mismatch such that both the corresponding bits are unset.
    // Since all bits lower than the alignment are unset we don't need to
    // align for the violations of ones anymore.
    juint last_violation = W - 1 - count_leading_zeros(zero_violation);
    T find_mask = std::numeric_limits<T>::max() << last_violation;
    T either = lo | zeros;
    T tmp = ~either & find_mask;
    T alignment = tmp & (-tmp);
    lo = (lo & -alignment) + alignment;
    return lo | ones;
  };

  T new_lo = adjust_lo(lo, zeros, ones);
  if (new_lo < lo) {
    empty = true;
    return true;
  }

  T new_hi = ~adjust_lo(~hi, ones, zeros);
  if (new_hi > hi) {
    empty = true;
    return true;
  }
  bool progress = (new_lo != lo) || (new_hi != hi);
  lo = new_lo;
  hi = new_hi;
  empty = lo > hi;
  return progress;
}

template <class T>
static bool adjust_bits_from_bounds(bool& empty, T& zeros, T& ones, T lo, T hi) {
  static_assert(std::is_unsigned<T>::value, "");
  T mismatch = lo ^ hi;
  T match_mask = mismatch == 0 ? std::numeric_limits<T>::max()
                               : ~(std::numeric_limits<T>::max() >> count_leading_zeros(mismatch));
  T new_zeros = zeros | (match_mask &~ lo);
  T new_ones = ones | (match_mask & lo);
  bool progress = (new_zeros != zeros) || (new_ones != ones);
  zeros = new_zeros;
  ones = new_ones;
  empty = ((zeros & ones) != 0);
  return progress;
}

template <class T>
static void normalize_constraints_simple(bool& empty, T& lo, T& hi, T& zeros, T& ones) {
  adjust_bits_from_bounds(empty, zeros, ones, lo, hi);
  if (empty) {
    return;
  }
  while (true) {
    bool progress = adjust_bounds_from_bits(empty, lo, hi, zeros, ones);
    if (!progress || empty) {
      return;
    }
    progress = adjust_bits_from_bounds(empty, zeros, ones, lo, hi);
    if (!progress || empty) {
      return;
    }
  }
}

template <class T, class U>
void normalize_constraints(bool& empty, T& lo, T& hi, U& ulo, U& uhi, U& zeros, U& ones) {
  static_assert(std::is_signed<T>::value, "");
  static_assert(std::is_unsigned<U>::value, "");
  static_assert(sizeof(T) == sizeof(U), "");

  if (lo > hi || ulo > uhi || (zeros & ones) != 0) {
    empty = true;
    return;
  }

  if (T(ulo) > T(uhi)) {
    if (T(uhi) < lo) {
      uhi = std::numeric_limits<T>::max();
    } else if (T(ulo) > hi) {
      ulo = std::numeric_limits<T>::min();
    }
  }

  if (T(ulo) <= T(uhi)) {
    ulo = MAX2<T>(ulo, lo);
    uhi = MIN2<T>(uhi, hi);
    if (ulo > uhi) {
      empty = true;
      return;
    }

    normalize_constraints_simple(empty, ulo, uhi, zeros, ones);
    lo = ulo;
    hi = uhi;
    return;
  }

  bool empty1 = false;
  U lo1 = lo;
  U hi1 = uhi;
  U zeros1 = zeros;
  U ones1 = ones;
  normalize_constraints_simple(empty1, lo1, hi1, zeros1, ones1);

  bool empty2 = false;
  U lo2 = ulo;
  U hi2 = hi;
  U zeros2 = zeros;
  U ones2 = ones;
  normalize_constraints_simple(empty2, lo2, hi2, zeros2, ones2);

  if (empty1 && empty2) {
    empty = true;
  } else if (empty1) {
    lo = lo2;
    hi = hi2;
    ulo = lo2;
    uhi = hi2;
    zeros = zeros2;
    ones = ones2;
  } else if (empty2) {
    lo = lo1;
    hi = hi1;
    ulo = lo1;
    uhi = hi1;
    zeros = zeros1;
    ones = ones1;
  } else {
    lo = lo1;
    hi = hi2;
    ulo = lo2;
    uhi = hi1;
    zeros = zeros1 & zeros2;
    ones = ones1 & ones2;
  }
}
template void normalize_constraints(bool& empty, jint& lo, jint& hi, juint& ulo, juint& uhi, juint& zeros, juint& ones);
template void normalize_constraints(bool& empty, jlong& lo, jlong& hi, julong& ulo, julong& uhi, julong& zeros, julong& ones);

template <class T, class U>
void verify_constraints(T lo, T hi, U ulo, U uhi, U zeros, U ones) {
  static_assert(std::is_signed<T>::value, "");
  static_assert(std::is_unsigned<U>::value, "");
  static_assert(sizeof(T) == sizeof(U), "");

  // Assert that the bounds cannot be further tightened
  assert(lo <= hi && U(lo) >= ulo && U(lo) <= uhi && (lo & zeros) == 0 && (~lo & ones) == 0, "");
  assert(hi >= lo && U(hi) >= ulo && U(hi) <= uhi && (hi & zeros) == 0 && (~hi & ones) == 0, "");
  assert(T(ulo) >= lo && T(ulo) <= hi && ulo <= uhi && (ulo & zeros) == 0 && (~ulo & ones) == 0, "");
  assert(T(uhi) >= lo && T(uhi) <= hi && uhi >= ulo && (uhi & zeros) == 0 && (~uhi & ones) == 0, "");

  // Assert that the bits cannot be further tightened
  if (U(lo) == ulo) {
    bool empty = false;
    assert(!adjust_bits_from_bounds(empty, zeros, ones, ulo, uhi), "");
  } else {
    bool empty1 = false;
    U lo1 = lo;
    U hi1 = uhi;
    U zeros1 = zeros;
    U ones1 = ones;
    adjust_bits_from_bounds(empty1, zeros1, ones1, lo1, hi1);
    assert(!empty1, "");
    assert(!adjust_bounds_from_bits(empty1, lo1, hi1, zeros1, ones1), "");

    bool empty2 = false;
    U lo2 = ulo;
    U hi2 = hi;
    U zeros2 = zeros;
    U ones2 = ones;
    adjust_bits_from_bounds(empty2, zeros2, ones2, lo2, hi2);
    assert(!empty2, "");
    assert(!adjust_bounds_from_bits(empty2, lo2, hi2, zeros2, ones2), "");

    assert((zeros1 & zeros2) == zeros && (ones1 & ones2) == ones, "");
  }
}
template void verify_constraints(jint lo, jint hi, juint ulo, juint uhi, juint zeros, juint ones);
template void verify_constraints(jlong lo, jlong hi, julong ulo, julong uhi, julong zeros, julong ones);

template <class T, class U>
int normalize_widen(T lo, T hi, U ulo, U uhi, U zeros, U ones, int w) {
  // Certain normalizations keep us sane when comparing types.
  // The 'SMALLINT' covers constants and also CC and its relatives.
  if (cardinality_from_bounds(lo, hi, ulo, uhi) <= SMALLINT) {
    return Type::WidenMin;
  }
  if (lo == std::numeric_limits<T>::min() && hi == std::numeric_limits<T>::max() &&
      ulo == std::numeric_limits<U>::min() && uhi == std::numeric_limits<U>::max() &&
      zeros == 0 && ones == 0) {
    // bottom type
    return Type::WidenMax;
  }
  return w;
}
template int normalize_widen(jint lo, jint hi, juint ulo, juint uhi, juint zeros, juint ones, int w);
template int normalize_widen(jlong lo, jlong hi, julong ulo, julong uhi, julong zeros, julong ones, int w);

template <class CT, class T, class UT>
const Type* int_type_xmeet(const CT* i1, const Type* t2, const Type* (*make)(T, T, UT, UT, UT, UT, int, bool), bool dual) {
  // Perform a fast test for common case; meeting the same types together.
  if (i1 == t2 || t2 == Type::TOP) {
    return i1;
  }
  const CT* i2 = CT::try_cast(t2);
  if (i2 != nullptr) {
    if (!dual) {
    // meet
      return make(MIN2(i1->_lo, i2->_lo), MAX2(i1->_hi, i2->_hi), MIN2(i1->_ulo, i2->_ulo), MAX2(i1->_uhi, i2->_uhi),
                  i1->_zeros & i2->_zeros, i1->_ones & i2->_ones, MAX2(i1->_widen, i2->_widen), false);
    }
    // join
    return make(MAX2(i1->_lo, i2->_lo), MIN2(i1->_hi, i2->_hi), MAX2(i1->_ulo, i2->_ulo), MIN2(i1->_uhi, i2->_uhi),
                i1->_zeros | i2->_zeros, i1->_ones | i2->_ones, MIN2(i1->_widen, i2->_widen), true);
  }

  assert(t2->base() != i1->base(), "");
  switch (t2->base()) {          // Switch on original type
  case Type::AnyPtr:                  // Mixing with oops happens when javac
  case Type::RawPtr:                  // reuses local variables
  case Type::OopPtr:
  case Type::InstPtr:
  case Type::AryPtr:
  case Type::MetadataPtr:
  case Type::KlassPtr:
  case Type::InstKlassPtr:
  case Type::AryKlassPtr:
  case Type::NarrowOop:
  case Type::NarrowKlass:
  case Type::Int:
  case Type::Long:
  case Type::FloatTop:
  case Type::FloatCon:
  case Type::FloatBot:
  case Type::DoubleTop:
  case Type::DoubleCon:
  case Type::DoubleBot:
  case Type::Bottom:                  // Ye Olde Default
    return Type::BOTTOM;
  default:                      // All else is a mistake
    i1->typerr(t2);
    return nullptr;
  }
}
template const Type* int_type_xmeet(const TypeInt* i1, const Type* t2,
                                    const Type* (*make)(jint, jint, juint, juint, juint, juint, int, bool), bool dual);
template const Type* int_type_xmeet(const TypeLong* i1, const Type* t2,
                                    const Type* (*make)(jlong, jlong, julong, julong, julong, julong, int, bool), bool dual);

// Called in PhiNode::Value during CCP, monotically widen the value set, do so rigorously
// first, after WidenMax attempts, if the type has still not converged we speed up the
// convergence by abandoning the bounds
template <class CT>
const Type* int_type_widen(const CT* nt, const CT* ot, const CT* lt) {
  using T = std::remove_const_t<decltype(CT::_lo)>;
  using U = std::remove_const_t<decltype(CT::_ulo)>;

  if (ot == nullptr) {
    return nt;
  }

  // If new guy is equal to old guy, no widening
  if (int_type_equal(nt, ot)) {
    return ot;
  }

  // If old guy contains new, then we probably widened too far & dropped to
  // bottom. Return the wider fellow.
  if (int_type_subset(ot, nt)) {
    return ot;
  }

  // Neither contains each other, weird?
  // fatal("Integer value range is not subset");
  // return this;
  if (!int_type_subset(nt, ot)) {
    return CT::TYPE_DOMAIN;
  }

  // If old guy was a constant, do not bother
  if (ot->singleton()) {
    return nt;
  }

  // If new guy contains old, then we widened
  // If new guy is already wider than old, no widening
  if (nt->_widen > ot->_widen) {
    return nt;
  }

  if (nt->_widen < Type::WidenMax) {
    // Returned widened new guy
    return CT::make(nt->_lo, nt->_hi, nt->_ulo, nt->_uhi, nt->_zeros, nt->_ones, nt->_widen + 1);
  }

  // Speed up the convergence by abandoning the bounds, there are only a couple of bits so
  // they converge fast
  T min = std::numeric_limits<T>::min();
  T max = std::numeric_limits<T>::max();
  U umin = std::numeric_limits<U>::min();
  U umax = std::numeric_limits<U>::max();
  U zeros = nt->_zeros;
  U ones = nt->_ones;
  if (lt != nullptr) {
    min = lt->_lo;
    max = lt->_hi;
    umin = lt->_ulo;
    umax = lt->_uhi;
    zeros |= lt->_zeros;
    ones |= lt->_ones;
  }
  return CT::make(min, max, umin, umax, zeros, ones, Type::WidenMax);
}
template const Type* int_type_widen(const TypeInt* nt, const TypeInt* ot, const TypeInt* lt);
template const Type* int_type_widen(const TypeLong* nt, const TypeLong* ot, const TypeLong* lt);

// Called by PhiNode::Value during GVN, monotonically narrow the value set, only
// narrow if the bits change or if the bounds are tightened enough to avoid
// slow convergence
template <class CT>
const Type* int_type_narrow(const CT* nt, const CT* ot) {
  using T = decltype(CT::_lo);
  using U = decltype(CT::_ulo);

  if (nt->singleton() || ot == nullptr) {
    return nt;
  }

  // If new guy is equal to old guy, no narrowing
  if (int_type_equal(nt, ot)) {
    return ot;
  }

  // If old guy was maximum range, allow the narrowing
  if (int_type_equal(ot, CT::TYPE_DOMAIN)) {
    return nt;
  }

  // Doesn't narrow; pretty weird
  if (!int_type_subset(ot, nt)) {
    return nt;
  }

  // Bits change
  if (ot->_zeros != nt->_zeros || ot->_ones != nt->_ones) {
    return nt;
  }

  // Only narrow if the range shrinks a lot
  U oc = cardinality_from_bounds(ot->_lo, ot->_hi, ot->_ulo, ot->_uhi);
  U nc = cardinality_from_bounds(nt->_lo, nt->_hi, nt->_ulo, nt->_uhi);
  return (nc > (oc >> 1) + (SMALLINT * 2)) ? ot : nt;
}
template const Type* int_type_narrow(const TypeInt* nt, const TypeInt* ot);
template const Type* int_type_narrow(const TypeLong* nt, const TypeLong* ot);

template <class T>
static const char* intnamenear(T origin, const char* xname, char* buf, size_t buf_size, T n) {
  if (n < origin) {
    if (n <= origin - 10000) {
      return nullptr;
    }
    os::snprintf_checked(buf, buf_size, "%s-" INT32_FORMAT, xname, jint(origin - n));
  } else if (n > origin) {
    if (n >= origin + 10000) {
      return nullptr;
    }
    os::snprintf_checked(buf, buf_size, "%s+" INT32_FORMAT, xname, jint(n - origin));
  } else {
    return xname;
  }
  return buf;
}

const char* intname(char* buf, size_t buf_size, jint n) {
  const char* str = intnamenear<jint>(max_jint, "maxint", buf, buf_size, n);
  if (str != nullptr) {
    return str;
  }

  str = intnamenear<jint>(min_jint, "minint", buf, buf_size, n);
  if (str != nullptr) {
    return str;
  }

  os::snprintf_checked(buf, buf_size, INT32_FORMAT, n);
  return buf;
}

const char* uintname(char* buf, size_t buf_size, juint n) {
  const char* str = intnamenear<juint>(max_juint, "maxuint", buf, buf_size, n);
  if (str != nullptr) {
    return str;
  }

  str = intnamenear<juint>(max_jint, "maxint", buf, buf_size, n);
  if (str != nullptr) {
    return str;
  }

  os::snprintf_checked(buf, buf_size, UINT32_FORMAT"u", n);
  return buf;
}

const char* longname(char* buf, size_t buf_size, jlong n) {
  const char* str = intnamenear<jlong>(max_jlong, "maxlong", buf, buf_size, n);
  if (str != nullptr) {
    return str;
  }

  str = intnamenear<jlong>(min_jlong, "minlong", buf, buf_size, n);
  if (str != nullptr) {
    return str;
  }

  str = intnamenear<jlong>(max_juint, "maxuint", buf, buf_size, n);
  if (str != nullptr) {
    return str;
  }

  str = intnamenear<jlong>(max_jint, "maxint", buf, buf_size, n);
  if (str != nullptr) {
    return str;
  }

  str = intnamenear<jlong>(min_jint, "minint", buf, buf_size, n);
  if (str != nullptr) {
    return str;
  }

  os::snprintf_checked(buf, buf_size, JLONG_FORMAT, n);
  return buf;
}

const char* ulongname(char* buf, size_t buf_size, julong n) {
  const char* str = intnamenear<julong>(max_julong, "maxulong", buf, buf_size, n);
  if (str != nullptr) {
    return str;
  }

  str = intnamenear<julong>(max_jlong, "maxlong", buf, buf_size, n);
  if (str != nullptr) {
    return str;
  }

  str = intnamenear<julong>(max_juint, "maxuint", buf, buf_size, n);
  if (str != nullptr) {
    return str;
  }

  str = intnamenear<julong>(max_jint, "maxint", buf, buf_size, n);
  if (str != nullptr) {
    return str;
  }

  os::snprintf_checked(buf, buf_size, JULONG_FORMAT"u", n);
  return buf;
}

template <class U>
const char* bitname(char* buf, size_t buf_size, U zeros, U ones) {
  constexpr juint W = sizeof(U) * 8;

  if (buf_size < W + 1) {
    return "#####";
  }

  for (juint i = 0; i < W; i++) {
    U mask = U(1) << (W - 1 - i);
    if ((zeros & mask) != 0) {
      buf[i] = '0';
    } else if ((ones & mask) != 0) {
      buf[i] = '1';
    } else {
      buf[i] = '*';
    }
  }
  buf[W] = 0;
  return buf;
}
template const char* bitname(char* buf, size_t buf_size, juint zeros, juint ones);
template const char* bitname(char* buf, size_t buf_size, julong zeros, julong ones);
