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

#ifndef SHARE_OPTO_RANGEINFERENCE_HPP
#define SHARE_OPTO_RANGEINFERENCE_HPP

#include "cppstdlib/limits.hpp"
#include "cppstdlib/type_traits.hpp"
#include "utilities/globalDefinitions.hpp"

class outputStream;
class Type;
class TypeInt;
class TypeLong;

// A simple range in the signed or unsigned domain
template <class T>
class RangeInt {
public:
  T _lo;
  T _hi;
};

/**
 * Bits that are known to be 0 or 1. A value v satisfies this constraint iff
 * (v & zeros) == 0 && (v & ones) == ones. I.e, any bit that is 1 in zeros must
 * be 0 in v, and any bit that is 1 in ones must be 1 in v.
 *
 * I.e, for each bit position from 0 to sizeof(U) - 1, the corresponding bits
 * of zeros, ones and the allowed bit in v must follow:
 *
 * zeros    ones    allowed bits
 * 0        0       0 or 1
 * 1        0       0
 * 0        1       1
 * 1        1       none (impossible state)
 *
 * E.g:
 * zeros: 00110100
 * ones:  10000010
 * Then:  10001010 would satisfy the bit constraints
 * while: 10011000 would not since the bit at the 4th position violates
 * zeros and the bit at the 7th position violates ones
 *
 * A KnownBits is sane if there is no position at which a bit must be both set
 * and unset at the same time. That is (zeros & ones) == 0.
 */
template <class U>
class KnownBits {
  static_assert(U(-1) > U(0), "bit info should be unsigned");

public:
  U _zeros;
  U _ones;

  bool is_satisfied_by(U v) const {
    return (v & _zeros) == U(0) && (v & _ones) == _ones;
  }
};

// All the information needed to construct a TypeInt/TypeLong, the constraints
// here may be arbitrary and need to be canonicalized to construct a
// TypeInt/TypeLong
template <class S, class U>
class TypeIntPrototype {
public:
  static_assert(S(-1) < S(0), "");
  static_assert(U(-1) > U(0), "");
  static_assert(sizeof(S) == sizeof(U), "");

  RangeInt<S> _srange;
  RangeInt<U> _urange;
  KnownBits<U> _bits;

  // A canonicalized version of a TypeIntPrototype, if the prototype represents
  // an empty type, _present is false, otherwise, _data is canonical
  class CanonicalizedTypeIntPrototype {
  public:
    bool _present; // whether this is an empty set
    TypeIntPrototype<S, U> _data;

    bool empty() const {
      return !_present;
    }

    static CanonicalizedTypeIntPrototype make_empty() {
      return {false, {}};
    }
  };

  CanonicalizedTypeIntPrototype canonicalize_constraints() const;
  int normalize_widen(int w) const;
#ifdef ASSERT
  bool contains(S v) const;
  void verify_constraints() const;
#endif // ASSERT
};

// Various helper functions for TypeInt/TypeLong operations
class TypeIntHelper {
public:
  // Calculate the cardinality of a TypeInt/TypeLong ignoring the bits
  // constraints, the return value is the cardinality minus 1 to not overflow
  // with the bottom type
  template <class S, class U>
  static U cardinality_from_bounds(const RangeInt<S>& srange, const RangeInt<U>& urange) {
    static_assert(S(-1) < S(0), "");
    static_assert(U(-1) > U(0), "");
    static_assert(sizeof(S) == sizeof(U), "");

    if (U(srange._lo) == urange._lo) {
      // srange is the same as urange
      assert(U(srange._hi) == urange._hi, "");
      // The cardinality is (hi - lo + 1), we return the result minus 1
      return urange._hi - urange._lo;
    }

    // srange intersects with urange in 2 intervals [srange._lo, urange._hi]
    // and [urange._lo, srange._hi]
    // The cardinality is (uhi - lo + 1) + (hi - ulo + 1), we return the result
    // minus 1
    return (urange._hi - U(srange._lo)) + (U(srange._hi) - urange._lo) + U(1);
  }

  template <class CT>
  static const Type* int_type_xmeet(const CT* i1, const Type* t2);

  template <class CTP>
  static auto int_type_union(CTP t1, CTP t2) {
    using CT = std::conditional_t<std::is_pointer_v<CTP>, std::remove_pointer_t<CTP>, CTP>;
    using S = std::remove_const_t<decltype(CT::_lo)>;
    using U = std::remove_const_t<decltype(CT::_ulo)>;
    return CT::make(TypeIntPrototype<S, U>{{MIN2(t1->_lo, t2->_lo), MAX2(t1->_hi, t2->_hi)},
                                           {MIN2(t1->_ulo, t2->_ulo), MAX2(t1->_uhi, t2->_uhi)},
                                           {t1->_bits._zeros & t2->_bits._zeros, t1->_bits._ones & t2->_bits._ones}},
                    MAX2(t1->_widen, t2->_widen));
  }

  template <class CTP>
  static bool int_type_is_equal(const CTP t1, const CTP t2) {
    return t1->_lo == t2->_lo && t1->_hi == t2->_hi &&
           t1->_ulo == t2->_ulo && t1->_uhi == t2->_uhi &&
           t1->_bits._zeros == t2->_bits._zeros && t1->_bits._ones == t2->_bits._ones;
  }

  template <class CTP>
  static bool int_type_is_subset(const CTP super, const CTP sub) {
    using U = decltype(super->_ulo);
    return super->_lo <= sub->_lo && super->_hi >= sub->_hi &&
           super->_ulo <= sub->_ulo && super->_uhi >= sub->_uhi &&
           // All bits that are known in super must also be known to be the same
           // value in sub, &~ (and not) is the same as a set subtraction on bit
           // sets
           (super->_bits._zeros &~ sub->_bits._zeros) == U(0) && (super->_bits._ones &~ sub->_bits._ones) == U(0);
  }

  template <class CT>
  static const Type* int_type_widen(const CT* new_type, const CT* old_type, const CT* limit_type);

  template <class CT>
  static const Type* int_type_narrow(const CT* new_type, const CT* old_type);

#ifndef PRODUCT
  static const char* intname(char* buf, size_t buf_size, jint n);
  static const char* uintname(char* buf, size_t buf_size, juint n);
  static const char* longname(char* buf, size_t buf_size, jlong n);
  static const char* ulongname(char* buf, size_t buf_size, julong n);

  template <class U>
  static const char* bitname(char* buf, size_t buf_size, U zeros, U ones);

  static void int_type_dump(const TypeInt* t, outputStream* st, bool verbose);
  static void int_type_dump(const TypeLong* t, outputStream* st, bool verbose);
#endif // PRODUCT
};

// A TypeIntMirror is structurally similar to a TypeInt or a TypeLong but it decouples the range
// inference from the Type infrastructure of the compiler. It also allows more flexibility with the
// bit width of the integer type. As a result, it is more efficient to use for intermediate steps
// of inference, as well as more flexible to perform testing on different integer types.
template <class S, class U>
class TypeIntMirror {
public:
  S _lo;
  S _hi;
  U _ulo;
  U _uhi;
  KnownBits<U> _bits;
  int _widen = 0; // dummy field to mimic the same field in TypeInt, useful in testing

  static TypeIntMirror make(const TypeIntPrototype<S, U>& t, int widen = 0) {
    auto canonicalized_t = t.canonicalize_constraints();
    assert(!canonicalized_t.empty(), "must not be empty");
    return TypeIntMirror{canonicalized_t._data._srange._lo, canonicalized_t._data._srange._hi,
                         canonicalized_t._data._urange._lo, canonicalized_t._data._urange._hi,
                         canonicalized_t._data._bits};
  }

  TypeIntMirror meet(const TypeIntMirror& o) const {
    return TypeIntHelper::int_type_union(this, &o);
  }

  // These allow TypeIntMirror to mimick the behaviors of TypeInt* and TypeLong*, so they can be
  // passed into RangeInference methods. These are only used in testing, so they are implemented in
  // the test file.
  static TypeIntMirror make(const TypeIntMirror& t, int widen);
  const TypeIntMirror* operator->() const;
  bool contains(U u) const;
  bool contains(const TypeIntMirror& o) const;
  bool operator==(const TypeIntMirror& o) const;

  template <class T>
  TypeIntMirror cast() const;
};

// This class contains methods for inferring the Type of the result of several arithmetic
// operations from those of the corresponding inputs. For example, given a, b such that the Type of
// a is [0, 1] and the Type of b is [-1, 3], then the Type of the sum a + b is [-1, 4].
// The methods in this class receive one or more template parameters which are often TypeInt* or
// TypeLong*, or they can be TypeIntMirror which behave similar to TypeInt* and TypeLong* during
// testing. This allows us to verify the correctness of the implementation without coupling with
// the hotspot compiler allocation infrastructure.
class RangeInference {
private:
  // If CTP is a pointer, get the underlying type. For the test helper classes, using the struct
  // directly allows straightfoward equality comparison.
  template <class CTP>
  using CT = std::remove_const_t<std::conditional_t<std::is_pointer_v<CTP>, std::remove_pointer_t<CTP>, CTP>>;

  // The type of CT::_lo, should be jint for TypeInt* and jlong for TypeLong*
  template <class CTP>
  using S = std::remove_const_t<decltype(CT<CTP>::_lo)>;

  // The type of CT::_ulo, should be juint for TypeInt* and julong for TypeLong*
  template <class CTP>
  using U = std::remove_const_t<decltype(CT<CTP>::_ulo)>;

  // A TypeInt consists of 1 or 2 simple intervals, each of which will lie either in the interval
  // [0, max_signed] or [min_signed, -1]. It is more optimal to analyze each simple interval
  // separately when doing inference. For example, consider a, b whose Types are both [-2, 2]. By
  // analyzing the interval [-2, -1] and [0, 2] separately, we can easily see that the result of
  // a & b must also be in the interval [-2, 2]. This is much harder if we want to work with the
  // whole value range at the same time.
  // This class offers a convenient way to traverse all the simple interval of a TypeInt.
  template <class CTP>
  class SimpleIntervalIterable {
  private:
    TypeIntMirror<S<CTP>, U<CTP>> _first_interval;
    TypeIntMirror<S<CTP>, U<CTP>> _second_interval;
    int _interval_num;

  public:
    SimpleIntervalIterable(CTP t) {
      if (U<CTP>(t->_lo) <= U<CTP>(t->_hi)) {
        _interval_num = 1;
        _first_interval = TypeIntMirror<S<CTP>, U<CTP>>{t->_lo, t->_hi, t->_ulo, t->_uhi, t->_bits};
      } else {
        _interval_num = 2;
        _first_interval = TypeIntMirror<S<CTP>, U<CTP>>::make(TypeIntPrototype<S<CTP>, U<CTP>>{{t->_lo, S<CTP>(t->_uhi)}, {U<CTP>(t->_lo), t->_uhi}, t->_bits}, 0);
        _second_interval = TypeIntMirror<S<CTP>, U<CTP>>::make(TypeIntPrototype<S<CTP>, U<CTP>>{{S<CTP>(t->_ulo), t->_hi}, {t->_ulo, U<CTP>(t->_hi)}, t->_bits}, 0);
      }
    }

    class Iterator {
    private:
      const SimpleIntervalIterable& _iterable;
      int _current_interval;

      Iterator(const SimpleIntervalIterable& iterable) : _iterable(iterable), _current_interval(0) {}

      friend class SimpleIntervalIterable;
    public:
      const TypeIntMirror<S<CTP>, U<CTP>>& operator*() const {
        assert(_current_interval < _iterable._interval_num, "out of bounds, %d - %d", _current_interval, _iterable._interval_num);
        if (_current_interval == 0) {
          return _iterable._first_interval;
        } else {
          return _iterable._second_interval;
        }
      }

      Iterator& operator++() {
        assert(_current_interval < _iterable._interval_num, "out of bounds, %d - %d", _current_interval, _iterable._interval_num);
        _current_interval++;
        return *this;
      }

      bool operator!=(const Iterator& o) const {
        assert(&_iterable == &o._iterable, "not on the same iterable");
        return _current_interval != o._current_interval;
      }
    };

    Iterator begin() const {
      return Iterator(*this);
    }

    Iterator end() const {
      Iterator res(*this);
      res._current_interval = _interval_num;
      return res;
    }
  };

  // Infer a result given the input types of a binary operation
  template <class CTP, class Inference>
  static CTP infer_binary(CTP t1, CTP t2, Inference infer) {
    TypeIntMirror<S<CTP>, U<CTP>> res;
    bool is_init = false;

    SimpleIntervalIterable<CTP> t1_simple_intervals(t1);
    SimpleIntervalIterable<CTP> t2_simple_intervals(t2);

    for (auto& st1 : t1_simple_intervals) {
      for (auto& st2 : t2_simple_intervals) {
        TypeIntMirror<S<CTP>, U<CTP>> current = infer(st1, st2);

        if (is_init) {
          res = res.meet(current);
        } else {
          is_init = true;
          res = current;
        }
      }
    }

    assert(is_init, "must be initialized");
    // It is important that widen is computed on the whole result instead of during each step. This
    // is because we normalize the widen of small Type instances to 0, so computing the widen value
    // for each step and taking the union of them may return a widen value that conflicts with
    // other computations, trigerring the monotonicity assert during CCP.
    //
    // For example, let us consider the operation r = x ^ y:
    // - During the first step of CCP, type(x) = {0}, type(y) = [-2, 2], w = 3.
    //   Since x is a constant that is the identity element of the xor operation, type(r) = type(y) = [-2, 2], w = 3
    // - During the second step, type(x) is widened to [0, 2], w = 0.
    //   We then compute the range for:
    //   r1 = x ^ y1, type(x) = [0, 2], w = 0, type(y1) = [0, 2], w = 0
    //   r2 = x ^ y2, type(x) = [0, 2], w = 0, type(y2) = [-2, -1], w = 0
    //   This results in type(r1) = [0, 3], w = 0, and type(r2) = [-4, -1], w = 0
    //   So the union of type(r1) and type(r2) is [-4, 3], w = 0. This widen value is smaller than
    //   that of the previous step, triggering the monotonicity assert.
    return CT<CTP>::make(res, MAX2(t1->_widen, t2->_widen));
  }

public:
  template <class CTP>
  static CTP infer_and(CTP t1, CTP t2) {
    return infer_binary(t1, t2, [&](const TypeIntMirror<S<CTP>, U<CTP>>& st1, const TypeIntMirror<S<CTP>, U<CTP>>& st2) {
      S<CTP> lo = std::numeric_limits<S<CTP>>::min();
      S<CTP> hi = std::numeric_limits<S<CTP>>::max();
      U<CTP> ulo = std::numeric_limits<U<CTP>>::min();
      // The unsigned value of the result of 'and' is always not greater than both of its inputs
      // since there is no position at which the bit is 1 in the result and 0 in either input
      U<CTP> uhi = MIN2(st1._uhi, st2._uhi);
      U<CTP> zeros = st1._bits._zeros | st2._bits._zeros;
      U<CTP> ones = st1._bits._ones & st2._bits._ones;
      return TypeIntMirror<S<CTP>, U<CTP>>::make(TypeIntPrototype<S<CTP>, U<CTP>>{{lo, hi}, {ulo, uhi}, {zeros, ones}});
    });
  }

  template <class CTP>
  static CTP infer_or(CTP t1, CTP t2) {
    return infer_binary(t1, t2, [&](const TypeIntMirror<S<CTP>, U<CTP>>& st1, const TypeIntMirror<S<CTP>, U<CTP>>& st2) {
      S<CTP> lo = std::numeric_limits<S<CTP>>::min();
      S<CTP> hi = std::numeric_limits<S<CTP>>::max();
      // The unsigned value of the result of 'or' is always not less than both of its inputs since
      // there is no position at which the bit is 0 in the result and 1 in either input
      U<CTP> ulo = MAX2(st1._ulo, st2._ulo);
      U<CTP> uhi = std::numeric_limits<U<CTP>>::max();
      U<CTP> zeros = st1._bits._zeros & st2._bits._zeros;
      U<CTP> ones = st1._bits._ones | st2._bits._ones;
      return TypeIntMirror<S<CTP>, U<CTP>>::make(TypeIntPrototype<S<CTP>, U<CTP>>{{lo, hi}, {ulo, uhi}, {zeros, ones}});
    });
  }

  template <class CTP>
  static CTP infer_xor(CTP t1, CTP t2) {
    return infer_binary(t1, t2, [&](const TypeIntMirror<S<CTP>, U<CTP>>& st1, const TypeIntMirror<S<CTP>, U<CTP>>& st2) {
      S<CTP> lo = std::numeric_limits<S<CTP>>::min();
      S<CTP> hi = std::numeric_limits<S<CTP>>::max();
      U<CTP> ulo = std::numeric_limits<U<CTP>>::min();
      U<CTP> uhi = std::numeric_limits<U<CTP>>::max();
      U<CTP> zeros = (st1._bits._zeros & st2._bits._zeros) | (st1._bits._ones & st2._bits._ones);
      U<CTP> ones = (st1._bits._zeros & st2._bits._ones) | (st1._bits._ones & st2._bits._zeros);
      return TypeIntMirror<S<CTP>, U<CTP>>::make(TypeIntPrototype<S<CTP>, U<CTP>>{{lo, hi}, {ulo, uhi}, {zeros, ones}});
    });
  }

  template <class CTP>
  static CTP infer_add(CTP t1, CTP t2) {
    return infer_binary(t1, t2, [&](const TypeIntMirror<S<CTP>, U<CTP>>& st1, const TypeIntMirror<S<CTP>, U<CTP>>& st2) {
      S<CTP> lo = std::numeric_limits<S<CTP>>::min();
      S<CTP> hi = std::numeric_limits<S<CTP>>::max();
      U<CTP> ulo = std::numeric_limits<U<CTP>>::min();
      U<CTP> uhi = std::numeric_limits<U<CTP>>::max();

      // Reminder: st1 is a simple interval, which means:
      // + (st1._lo < 0) == (st1._hi < 0)
      // + st1._lo == st1._ulo
      // + st1._hi == st1._uhi
      // The same is true for st2.
      // Consider unsigned values v1 and v2 satisfying st1 and st2, respectively.
      if ((st1._lo < S<CTP>(0)) == (st2._lo < S<CTP>(0))) {
        // All calculations in this section are done on the set of integers, not the set of
        // congruence classes mod 2^n (the set of values of an intn_t).
        //
        // If st1._lo >= 0 and st2._lo >= 0, and since st1._lo == st1._ulo, st1._hi == st1._uhi,
        // st2._lo == st2._ulo, st2._hi == st2._uhi, we have both:
        // + 0 <= st1._ulo <= v1 <= st1._uhi <= 2^(n-1) - 1
        // + 0 <= st2._ulo <= v2 <= st2._uhi <= 2^(n-1) - 1
        // Which means 0 <= st1._ulo + st2._ulo <= v1 + v2 <= st1._uhi + st2._uhi <= 2^n - 2, so
        // 0 <= (st1._ulo + st2._ulo) mod 2^n <= (v1 + v2) mod 2^n <= (st1._uhi + st2._uhi) mod 2^n
        //
        // Similarly, if st1._lo < 0 and st2._lo < 0, we have:
        // + 2^(n-1) <= st1._ulo <= v1 <= st1._uhi <= 2^n - 1
        // + 2^(n-1) <= st2._ulo <= v2 <= st2._uhi <= 2^n - 1
        // Which means 2^n <= st1._ulo + st2._ulo <= v1 + v2 <= st1._uhi + st2._uhi <= 2^n + 2^n - 2
        // For all value x such that 2^n <= x <= 2^n + 2^n - 1, we have x mod 2^n == x - 2^n. So:
        // 0 <= (st1._ulo + st2._ulo) mod 2^n <= (v1 + v2) mod 2^n <= (st1._uhi + st2._uhi) mod 2^n
        //
        // In other words, we can calculate the unsigned bounds.
        ulo = st1._ulo + st2._ulo;
        uhi = st1._uhi + st2._uhi;
        // We don't actually need to calculate the signed bounds for the sum, because:
        // - If the addition of the ranges does not overflow, then the bounds are
        //   [st1._lo + st2._lo, st1._hi + st2._hi], which is equivalent to the unsigned bounds
        //   calculation.
        // - If the addition of the ranges overflows, then the bounds are [min_signed, max_signed].
        // In both cases, the signed bounds can be inferred from the computed unsigned bounds.
      } else {
        // Similarly, in this case, since one of the ranges is negative, and the other is
        // non-negative, the signed addition does not overflow, we can compute it directly.
        lo = S<CTP>(st1._ulo + st2._ulo);
        hi = S<CTP>(st1._uhi + st2._uhi);
        // We do not need to compute the unsigned bounds because they can be inferred from the
        // computed signed bounds.
      }

      // Consider the addition of v1 and v2, denote v[i] the i-th bit of v, since:
      // - If st1._bits._ones[i] == 1, then v1[i] == 1.
      // - If st1._bits._zeros[i] == 1, then v1[i] == 0.
      // We have: st1._bits._ones[i] <= v1[i] <= (~st1._bits._zeros)[i]
      //
      // Try to calculate the sum bits by bits:
      // carry[n - 1] = 0;
      // for (int i = n - 1; i >= 0; i--) {
      //   int bit = v1[i] + v2[i] + carry[i];
      //   sum[i] = bit & 1;
      //   carry[i - 1] = (bit >= 2);
      // }
      //
      // Then, try to calculate the min and max of carry[i] from the bounds of v1[i] and v2[i]:
      // min_carry[n - 1] = 0;
      // max_carry[n - 1] = 0;
      // for (int i = n - 1; i >= 0; i--) {
      //   int min_bit = min_v1[i] + min_v2[i] + min_carry[i];
      //   int max_bit = max_v1[i] + max_v2[i] + max_carry[i];
      //   min_carry[i - 1] = (min_bit >= 2);
      //   max_carry[i - 1] = (max_bit >= 2);
      // }
      //
      // In other word:
      // min_carry[n - 1] = 0;
      // for (int i = n - 1; i >= 0; i--) {
      //   int min_bit = min_v1[i] + min_v2[i] + min_carry[i];
      //   min_carry[i - 1] = (min_bit >= 2);
      // }
      //
      // Since st1._bits._ones[i] <= v1[i], we have:
      // min_carry[n - 1] = 0;
      // for (int i = n - 1; i >= 0; i--) {
      //   int min_bit = st1._bits._ones[i] + st2._bits._ones[i] + min_carry[i];
      //   min_carry[i - 1] = (min_bit >= 2);
      // }
      //
      // If we gather the min_bits into a value tmp, it is clear that
      // tmp = st1._bits._ones + st2._bits._ones:
      // min_carry[n - 1] = 0;
      // for (int i = n - 1; i >= 0; i--) {
      //   int min_bit = st1._bits._ones[i] + st2._bits._ones[i] + min_carry[i];
      //   tmp[i] = min_bit & 1;
      //   min_carry[i - 1] = (min_bit >= 2)
      // }
      //
      // Since st1._bits._ones[i], st2._bits._ones[i], min_carry[i] can only be 0 or 1,
      // min_bit >= 2 if and only if either:
      // + st1._bits._ones[i] == st2._bits._ones[i] == 1
      // + (st1._bits._ones[i] != st2._bits._ones[i]) && ((min_bit & 1) == 0)
      //
      // In other words:
      // min_carry[i - 1] == 1 iff either:
      // + (st1._bits._ones[i] & st2._bits._ones[i]) == 1
      // + ((st1._bits._ones[i] | st2._bits._ones[i]) & (~tmp[i])) == 1
      //
      // As a result, we can calculate min_carry:
      // min_carry = ((st1._bits._ones & st2._bits._ones) | ((st1._bits._ones | st2._bits._ones) & (~(st1._bits._ones + st2._bits._ones)))) << 1
      U<CTP> min_carry = ((st1._bits._ones & st2._bits._ones) |
                          ((st1._bits._ones | st2._bits._ones) & (~(st1._bits._ones + st2._bits._ones))));
      min_carry = min_carry << 1;
      // Similarly, we can calculate max_carry from ~st1._bits._zeros and ~st2._bits._zeros
      U<CTP> max_carry = ((~st1._bits._zeros & ~st2._bits._zeros) |
                          ((~st1._bits._zeros | ~st2._bits._zeros) & (~(~st1._bits._zeros + ~st2._bits._zeros))));
      max_carry = max_carry << 1;
      // A bit carry[i] is known iff min_carry[i] == max_carry[i], or (min_carry[i] ^ max_carry[i]) == 0
      U<CTP> carry_known_bits = ~(min_carry ^ max_carry);
      // A bit of sum is only known if the corresponding bit in all of v1, v2, and carry is known,
      // and the value of sum[i] then would be (v1[i] + v2[i] + carry[i]) & 1, or
      // (v1[i] ^ v2[i] ^ carry[i])
      U<CTP> known_bits = (st1._bits._ones | st1._bits._zeros) & (st2._bits._ones | st2._bits._zeros) & carry_known_bits;
      // Calculate the result and filter in the bit positions that are known
      U<CTP> res = st1._bits._ones ^ st2._bits._ones ^ min_carry;
      U<CTP> zeros = known_bits & ~res;
      U<CTP> ones = known_bits & res;
      return CT<CTP>::make(TypeIntPrototype<S<CTP>, U<CTP>>{{lo, hi}, {ulo, uhi}, {zeros, ones}}, MAX2(t1->_widen, t2->_widen));
    });
  }

  template <class CTP>
  static CTP infer_sub(CTP t1, CTP t2) {
    return infer_binary(t1, t2, [&](const TypeIntMirror<S<CTP>, U<CTP>>& st1, const TypeIntMirror<S<CTP>, U<CTP>>& st2) {
      // The reasoning is very similar to infer_add, so I go through it quickly
      S<CTP> lo = std::numeric_limits<S<CTP>>::min();
      S<CTP> hi = std::numeric_limits<S<CTP>>::max();
      U<CTP> ulo = std::numeric_limits<U<CTP>>::min();
      U<CTP> uhi = std::numeric_limits<U<CTP>>::max();

      // Consider unsigned values v1 and v2 satisfying st1 and st2, respectively.
      if ((st1._lo < S<CTP>(0)) == (st2._lo < S<CTP>(0))) {
        // Signed subtraction of 2 values with the same sign cannot overflow, we can directly
        // compute the signed bounds.
        lo = S<CTP>(st1._ulo - st2._uhi);
        hi = S<CTP>(st1._uhi - st2._ulo);
        // The unsigned bounds can be inferred from the signed bounds, so there is no need to
        // compute them.
      } else {
        // Unsigned subtraction of 2 values v1, v2 such that 0 <= vi < 2^(n-1) <= vj < 2^n
        // If i == 1, j == 2, the operation always overflows, and the result in the mod 2^n
        // arithmetic is always v1 - v2 + 2^n. Which means that we still satisfy:
        // (st1._ulo - st2._uhi) mod 2^n <= (v1 - v2) mod 2^n <= (st1._uhi - st2._ulo) mod 2^n
        //
        // On the other hand, if i == 2, j == 1, the subtraction never overflows, and the bounds
        // can be computed trivially:
        // (st1._ulo - st2._uhi) mod 2^n <= (v1 - v2) mod 2^n <= (st1._uhi - st2._ulo) mod 2^n
        ulo = st1._ulo - st2._uhi;
        uhi = st1._uhi - st2._ulo;
        // The signed bounds can be inferred from the unsigned bounds, so there is no need to
        // compute them.
      }

      // Bit calculation is similar to infer_add:
      // max_carry[n - 1] = 0;
      // for (int i = n - 1; i >= 0; i--) {
      //   int min_bit = st1._bits._ones[i] - (~st2._bits._zeros)[i] - max_carry[i];
      //   tmp[i] = min_bit & 1;
      //   max_carry[i - 1] = (min_bit < 0)
      // }
      //
      // Since st1._bits._ones[i], (~st2._bits._ones)[i], max_carry[i] can only be 0 or 1,
      // min_bit < 0 if and only if either:
      // + st1._bits._ones[i] == 0 && (~st2._bits._zeros)[i] == 1
      // + st1._bits._ones[i] == (~st2._bits._zeros)[i] && ((min_bit & 1) == 1)
      //
      // In other words:
      // max_carry[i - 1] == 1 iff either:
      // + ((~st1._bits._ones)[i] | (~st2._bits._zeros)[i]) == 1
      // + ((st1._bits._ones[i] ^ st2._bits._zeros[i]) & tmp[i]) == 1
      U<CTP> max_carry = ((~st1._bits._ones & ~st2._bits._zeros) |
                          ((st1._bits._ones ^ st2._bits._zeros) & (st1._bits._ones - (~st2._bits._zeros))));
      max_carry = max_carry << 1;
      // Similarly, we can calculate min_carry, just substitute st1._bits._ones and
      // ~st2._bits._zeros from above with ~st1._bits._zeros and st2._bits._ones, respectively.
      // Note that x ^ y == ~x ^ ~y.
      U<CTP> min_carry = ((st1._bits._zeros & st2._bits._ones) |
                          ((st1._bits._zeros ^ st2._bits._ones) & ((~st1._bits._zeros) - st2._bits._ones)));
      min_carry = min_carry << 1;
      // A bit of the result is only known if the corresponding bit in all of v1, v2, and carry is
      // known.
      U<CTP> carry_known_bits = ~(min_carry ^ max_carry);
      U<CTP> known_bits = (st1._bits._ones | st1._bits._zeros) & (st2._bits._ones | st2._bits._zeros) & carry_known_bits;
      // Calculate the result and filter in the bit positions that are known, carry-less bit
      // subtraction is also bitwise-xor.
      U<CTP> res = st1._bits._ones ^ st2._bits._ones ^ min_carry;
      U<CTP> zeros = known_bits & ~res;
      U<CTP> ones = known_bits & res;
      return CT<CTP>::make(TypeIntPrototype<S<CTP>, U<CTP>>{{lo, hi}, {ulo, uhi}, {zeros, ones}}, MAX2(t1->_widen, t2->_widen));
    });
  }
};

#endif // SHARE_OPTO_RANGEINFERENCE_HPP
