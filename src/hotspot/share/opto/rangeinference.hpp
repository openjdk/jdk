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
};

#endif // SHARE_OPTO_RANGEINFERENCE_HPP
