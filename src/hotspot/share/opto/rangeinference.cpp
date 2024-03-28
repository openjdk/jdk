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
#include "opto/type.hpp"
#include "utilities/tuple.hpp"

constexpr juint SMALLINT = 3; // a value too insignificant to consider widening

template <class T>
class AdjustResult {
public:
  bool _progress;
  bool _present;
  T _data;
};

template <class T>
class NormalizeSimpleResult {
public:
  bool _present;
  RangeInt<T> _bounds;
  KnownBits<T> _bits;
};

// Try to tighten the bound constraints from the known bit information
// E.g: if lo = 0 but the lowest bit is always 1 then we can tighten
// lo = 1
template <class T>
static AdjustResult<RangeInt<T>>
adjust_bounds_from_bits(const RangeInt<T>& bounds, const KnownBits<T>& bits) {
  static_assert(std::is_unsigned<T>::value, "");

  auto adjust_lo = [](T lo, const KnownBits<T>& bits) {
    constexpr size_t W = sizeof(T) * 8;
    T zero_violation = lo & bits._zeros;
    T one_violation = ~lo & bits._ones;
    if (zero_violation == one_violation) {
      return lo;
    }

    if (zero_violation < one_violation) {
      // Align the last violation of ones unset all the lower bits
      // so we don't care about violations of zeros
      juint last_violation = W - 1 - count_leading_zeros(one_violation);
      T alignment = T(1) << last_violation;
      lo = (lo & -alignment) + alignment;
      return lo | bits._ones;
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
    T either = lo | bits._zeros;
    T tmp = ~either & find_mask;
    T alignment = tmp & (-tmp);
    lo = (lo & -alignment) + alignment;
    return lo | bits._ones;
  };

  T new_lo = adjust_lo(bounds._lo, bits);
  if (new_lo < bounds._lo) {
    return {true, false, {}};
  }
  T new_hi = ~adjust_lo(~bounds._hi, {bits._ones, bits._zeros});
  if (new_hi > bounds._hi) {
    return {true, false, {}};
  }

  bool progress = (new_lo != bounds._lo) || (new_hi != bounds._hi);
  bool present = new_lo <= new_hi;
  return {progress, present, {new_lo, new_hi}};
}

// Try to tighten the known bit constraints from the bound information
// E.g: if lo = 0 and hi = 10, then all but the lowest 4 bits must be 0
template <class T>
static AdjustResult<KnownBits<T>>
adjust_bits_from_bounds(const KnownBits<T>& bits, const RangeInt<T>& bounds) {
  static_assert(std::is_unsigned<T>::value, "");
  T mismatch = bounds._lo ^ bounds._hi;
  T match_mask = mismatch == 0 ? std::numeric_limits<T>::max()
                               : ~(std::numeric_limits<T>::max() >> count_leading_zeros(mismatch));
  T new_zeros = bits._zeros | (match_mask &~ bounds._lo);
  T new_ones = bits._ones | (match_mask & bounds._lo);
  bool progress = (new_zeros != bits._zeros) || (new_ones != bits._ones);
  bool present = ((new_zeros & new_ones) == 0);
  return {progress, present, {new_zeros, new_ones}};
}

// Try to tighten both the bounds and the bits at the same time
// Iteratively tighten 1 using the other until no progress is made.
// This function converges because bit constraints converge fast.
template <class T>
static NormalizeSimpleResult<T>
normalize_constraints_simple(const RangeInt<T>& bounds, const KnownBits<T>& bits) {
  AdjustResult<KnownBits<T>> nbits = adjust_bits_from_bounds(bits, bounds);
  if (!nbits._present) {
    return {false, {}, {}};
  }
  AdjustResult<RangeInt<T>> nbounds{true, true, bounds};
  while (true) {
    nbounds = adjust_bounds_from_bits(nbounds._data, nbits._data);
    if (!nbounds._progress || !nbounds._present) {
      return {nbounds._present, nbounds._data, nbits._data};
    }
    nbits = adjust_bits_from_bounds(nbits._data, nbounds._data);
    if (!nbits._progress || !nbits._present) {
      return {nbits._present, nbounds._data, nbits._data};
    }
  }
}

// Tighten all constraints of a TypeIntPrototype to its canonical form.
// i.e the result represents the same set as the input, each bound belongs to
// the set and for each bit position that is not constrained, there exists 2
// values with the bit value at that position being set and unset, respectively,
// such that both belong to the set represented by the constraints.
template <class T, class U>
Pair<bool, TypeIntPrototype<T, U>>
TypeIntPrototype<T, U>::normalize_constraints() const {
  static_assert(std::is_signed<T>::value, "");
  static_assert(std::is_unsigned<U>::value, "");
  static_assert(sizeof(T) == sizeof(U), "");

  RangeInt<T> srange = _srange;
  RangeInt<U> urange = _urange;
  if (srange._lo > srange._hi ||
      urange._lo > urange._hi ||
      (_bits._zeros & _bits._ones) != 0) {
    return {false, {}};
  }

  if (T(urange._lo) > T(urange._hi)) {
    if (T(urange._hi) < srange._lo) {
      urange._hi = std::numeric_limits<T>::max();
    } else if (T(urange._lo) > srange._hi) {
      urange._lo = std::numeric_limits<T>::min();
    }
  }

  if (T(urange._lo) <= T(urange._hi)) {
    // [lo, hi] and [ulo, uhi] represent the same range
    urange._lo = MAX2<T>(urange._lo, srange._lo);
    urange._hi = MIN2<T>(urange._hi, srange._hi);
    if (urange._lo > urange._hi) {
      return {false, {}};
    }

    auto type = normalize_constraints_simple(urange, _bits);
    return {type._present, {{T(type._bounds._lo), T(type._bounds._hi)},
                            type._bounds, type._bits}};
  }

  // [lo, hi] intersects with [ulo, uhi] in 2 ranges:
  // [lo, uhi], which consists of negative values
  // [ulo, hi] which consists of non-negative values
  // We process these 2 separately and combine the results
  auto neg_type = normalize_constraints_simple({U(srange._lo), urange._hi}, _bits);
  auto pos_type = normalize_constraints_simple({urange._lo, U(srange._hi)}, _bits);

  if (!neg_type._present && !pos_type._present) {
    return {false, {}};
  } else if (!neg_type._present) {
    return {true, {{T(pos_type._bounds._lo), T(pos_type._bounds._hi)},
                   pos_type._bounds, pos_type._bits}};
  } else if (!pos_type._present) {
    return {true, {{T(neg_type._bounds._lo), T(neg_type._bounds._hi)},
                   neg_type._bounds, neg_type._bits}};
  } else {
    return {true, {{T(neg_type._bounds._lo), T(pos_type._bounds._hi)},
                   {pos_type._bounds._lo, neg_type._bounds._hi},
                   {neg_type._bits._zeros & pos_type._bits._zeros, neg_type._bits._ones & pos_type._bits._ones}}};
  }
}

template <class T, class U>
int TypeIntPrototype<T, U>::normalize_widen(int w) const {
  // Certain normalizations keep us sane when comparing types.
  // The 'SMALLINT' covers constants and also CC and its relatives.
  if (cardinality_from_bounds(_srange, _urange) <= SMALLINT) {
    return Type::WidenMin;
  }
  if (_srange._lo == std::numeric_limits<T>::min() && _srange._hi == std::numeric_limits<T>::max() &&
      _urange._lo == std::numeric_limits<U>::min() && _urange._hi == std::numeric_limits<U>::max() &&
      _bits._zeros == 0 && _bits._ones == 0) {
    // bottom type
    return Type::WidenMax;
  }
  return w;
}

#ifdef ASSERT
template <class T, class U>
bool TypeIntPrototype<T, U>::contains(T v) const {
  return v >= _srange._lo && v <= _srange._hi && U(v) >= _urange._lo && U(v) <= _urange._hi &&
         (v & _bits._zeros) == 0 && (~v & _bits._ones) == 0;
}

// Verify that this set representation is canonical
template <class T, class U>
void TypeIntPrototype<T, U>::verify_constraints() const {
  static_assert(std::is_signed<T>::value, "");
  static_assert(std::is_unsigned<U>::value, "");
  static_assert(sizeof(T) == sizeof(U), "");

  // Assert that the bounds cannot be further tightened
  assert(contains(_srange._lo) && contains(_srange._hi) &&
         contains(_urange._lo) && contains(_urange._hi), "");

  // Assert that the bits cannot be further tightened
  if (U(_srange._lo) == _urange._lo) {
    assert(!adjust_bits_from_bounds(_bits, _urange)._progress, "");
  } else {
    RangeInt<U> neg_range{U(_srange._lo), _urange._hi};
    auto neg_bits = adjust_bits_from_bounds(_bits, neg_range);
    assert(neg_bits._present, "");
    assert(!adjust_bounds_from_bits(neg_range, neg_bits._data)._progress, "");

    RangeInt<U> pos_range{_urange._lo, U(_srange._hi)};
    auto pos_bits = adjust_bits_from_bounds(_bits, pos_range);
    assert(pos_bits._present, "");
    assert(!adjust_bounds_from_bits(pos_range, pos_bits._data)._progress, "");

    assert((neg_bits._data._zeros & pos_bits._data._zeros) == _bits._zeros &&
           (neg_bits._data._ones & pos_bits._data._ones) == _bits._ones, "");
  }
}
#endif // ASSERT

template class TypeIntPrototype<jint, juint>;
template class TypeIntPrototype<jlong, julong>;

// Compute the meet of 2 types, when dual is true, we are actually computing the
// join.
template <class CT, class T, class UT>
const Type* int_type_xmeet(const CT* i1, const Type* t2, const Type* (*make)(const TypeIntPrototype<T, UT>&, int, bool), bool dual) {
  // Perform a fast test for common case; meeting the same types together.
  if (i1 == t2 || t2 == Type::TOP) {
    return i1;
  }
  const CT* i2 = CT::try_cast(t2);
  if (i2 != nullptr) {
    if (!dual) {
    // meet
      return make(TypeIntPrototype<T, UT>{{MIN2(i1->_lo, i2->_lo), MAX2(i1->_hi, i2->_hi)},
                                          {MIN2(i1->_ulo, i2->_ulo), MAX2(i1->_uhi, i2->_uhi)},
                                          {i1->_zeros & i2->_zeros, i1->_ones & i2->_ones}},
                  MAX2(i1->_widen, i2->_widen), false);
    }
    // join
    return make(TypeIntPrototype<T, UT>{{MAX2(i1->_lo, i2->_lo), MIN2(i1->_hi, i2->_hi)},
                                        {MAX2(i1->_ulo, i2->_ulo), MIN2(i1->_uhi, i2->_uhi)},
                                        {i1->_zeros | i2->_zeros, i1->_ones | i2->_ones}},
                MIN2(i1->_widen, i2->_widen), true);
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
                                    const Type* (*make)(const TypeIntPrototype<jint, juint>&, int, bool), bool dual);
template const Type* int_type_xmeet(const TypeLong* i1, const Type* t2,
                                    const Type* (*make)(const TypeIntPrototype<jlong, julong>&, int, bool), bool dual);

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
    TypeIntPrototype<T, U> prototype{{nt->_lo, nt->_hi}, {nt->_ulo, nt->_uhi}, {nt->_zeros, nt->_ones}};
    return CT::make(prototype, nt->_widen + 1);
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
  TypeIntPrototype<T, U> prototype{{min, max}, {umin, umax}, {zeros, ones}};
  return CT::make(prototype, Type::WidenMax);
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
  U oc = cardinality_from_bounds(RangeInt<T>{ot->_lo, ot->_hi},
                                 RangeInt<U>{ot->_ulo, ot->_uhi});
  U nc = cardinality_from_bounds(RangeInt<T>{nt->_lo, nt->_hi},
                                 RangeInt<U>{nt->_ulo, nt->_uhi});
  return (nc > (oc >> 1) + (SMALLINT * 2)) ? ot : nt;
}
template const Type* int_type_narrow(const TypeInt* nt, const TypeInt* ot);
template const Type* int_type_narrow(const TypeLong* nt, const TypeLong* ot);


#ifndef PRODUCT
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
#endif // PRODUCT
