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

#include "opto/rangeinference.hpp"
#include "opto/type.hpp"
#include "runtime/os.hpp"
#include "unittest.hpp"
#include "utilities/intn_t.hpp"
#include "utilities/rbTree.hpp"
#include <array>
#include <limits>
#include <type_traits>

template <class U>
static U uniform_random() {
  return U(juint(os::random()));
}

template <>
julong uniform_random<julong>() {
  return (julong(os::random()) << 32) | julong(juint(os::random()));
}

static void test_canonicalize_constraints_trivial() {
  ASSERT_FALSE(TypeInt::NON_ZERO->contains(0));
  ASSERT_TRUE(TypeInt::NON_ZERO->contains(1));
  ASSERT_TRUE(TypeInt::NON_ZERO->contains(-1));
  ASSERT_TRUE(TypeInt::CC_NE->contains(-1));
  ASSERT_TRUE(TypeInt::CC_NE->contains(1));
  ASSERT_FALSE(TypeInt::CC_NE->contains(0));
  ASSERT_FALSE(TypeInt::CC_NE->contains(-2));
  ASSERT_FALSE(TypeInt::CC_NE->contains(2));
  ASSERT_FALSE(TypeLong::NON_ZERO->contains(jlong(0)));
  ASSERT_TRUE(TypeLong::NON_ZERO->contains(jlong(1)));
  ASSERT_TRUE(TypeLong::NON_ZERO->contains(jlong(-1)));
}

template <class S, class U>
static void test_canonicalize_constraints_exhaustive() {
  {
    TypeIntPrototype<S, U> t{{S(0), S(0)}, {U(0), U(0)}, {U(-1), U(0)}};
    auto new_t = t.canonicalize_constraints();
    ASSERT_TRUE(new_t._present);
    DEBUG_ONLY(ASSERT_TRUE(t.contains(S(0))));
    DEBUG_ONLY(ASSERT_FALSE(t.contains(S(1))));
  }
  {
    TypeIntPrototype<S, U> t{{S(0), S(0)}, {U(1), U(1)}, {U(-1), U(0)}};
    auto new_t = t.canonicalize_constraints();
    ASSERT_FALSE(new_t._present);
    DEBUG_ONLY(ASSERT_FALSE(t.contains(S(0))));
    DEBUG_ONLY(ASSERT_FALSE(t.contains(S(1))));
  }
  {
    TypeIntPrototype<S, U> t{{S(S::min), S(S::max)}, {U(U::min), U(U::max)}, {U(0), U(0)}};
    auto new_t = t.canonicalize_constraints();
    ASSERT_TRUE(new_t._present);
    for (int v = S::min; v <= S::max; v++) {
      DEBUG_ONLY(ASSERT_TRUE(t.contains(S(v))));
    }
  }
  for (int lo = S::min; lo <= S::max; lo++) {
    for (int hi = lo; hi <= S::max; hi++) {
      for (int ulo = U::min; ulo <= U::max; ulo++) {
        for (int uhi = ulo; uhi <= U::max; uhi++) {
          for (int zeros = U::min; zeros <= U::max; zeros++) {
            for (int ones = U::min; ones <= U::max; ones++) {
              TypeIntPrototype<S, U> t{{S(lo), S(hi)}, {U(ulo), U(uhi)}, {U(zeros), U(ones)}};
              auto new_t = t.canonicalize_constraints();
              if (new_t._present) {
                DEBUG_ONLY(new_t._data.verify_constraints());
              }
              for (int v = S::min; v <= S::max; v++) {
                if (!new_t._present) {
                  DEBUG_ONLY(ASSERT_FALSE(t.contains(S(v))));
                } else {
                  DEBUG_ONLY(ASSERT_EQ(t.contains(S(v)), new_t._data.contains(S(v))));
                }
              }
            }
          }
        }
      }
    }
  }
}

template <class S, class U>
static void test_canonicalize_constraints_simple() {
  constexpr int parameters = 1000;
  for (int i = 0; i < parameters; i++) {
    S a = uniform_random<U>();
    S b = uniform_random<U>();

    {
      S lo = MIN2<S>(a, b);
      S hi = MAX2<S>(a, b);
      TypeIntPrototype<S, U> t{{lo, hi}, {std::numeric_limits<U>::min(), std::numeric_limits<U>::max()},
                               {0, 0}};
      auto new_t = t.canonicalize_constraints();
      ASSERT_TRUE(new_t._present);
      DEBUG_ONLY(new_t._data.verify_constraints());
      ASSERT_EQ(lo, new_t._data._srange._lo);
      ASSERT_EQ(hi, new_t._data._srange._hi);
      if (U(lo) <= U(hi)) {
        ASSERT_EQ(U(lo), new_t._data._urange._lo);
        ASSERT_EQ(U(hi), new_t._data._urange._hi);
      } else {
        ASSERT_EQ(std::numeric_limits<U>::min(), new_t._data._urange._lo);
        ASSERT_EQ(std::numeric_limits<U>::max(), new_t._data._urange._hi);
      }
    }

    {
      U ulo = MIN2<U>(a, b);
      U uhi = MAX2<U>(a, b);
      TypeIntPrototype<S, U> t{{std::numeric_limits<S>::min(), std::numeric_limits<S>::max()},
                               {ulo, uhi}, {0, 0}};
      auto new_t = t.canonicalize_constraints();
      ASSERT_TRUE(new_t._present);
      DEBUG_ONLY(new_t._data.verify_constraints());
      ASSERT_EQ(ulo, new_t._data._urange._lo);
      ASSERT_EQ(uhi, new_t._data._urange._hi);
      if (S(ulo) <= S(uhi)) {
        ASSERT_EQ(S(ulo), new_t._data._srange._lo);
        ASSERT_EQ(S(uhi), new_t._data._srange._hi);
      } else {
        ASSERT_EQ(std::numeric_limits<S>::min(), new_t._data._srange._lo);
        ASSERT_EQ(std::numeric_limits<S>::max(), new_t._data._srange._hi);
      }
    }

    {
      U intersection = a & b;
      U zeros = a ^ intersection;
      U ones = b ^ intersection;
      TypeIntPrototype<S, U> t{{std::numeric_limits<S>::min(), std::numeric_limits<S>::max()},
                               {std::numeric_limits<U>::min(), std::numeric_limits<U>::max()}, {zeros, ones}};
      auto new_t = t.canonicalize_constraints();
      ASSERT_TRUE(new_t._present);
      DEBUG_ONLY(new_t._data.verify_constraints());
      ASSERT_EQ(zeros, new_t._data._bits._zeros);
      ASSERT_EQ(ones, new_t._data._bits._ones);
      ASSERT_EQ(ones, new_t._data._urange._lo);
      ASSERT_EQ(~zeros, new_t._data._urange._hi);
    }
  }
}

template <class S, class U>
static void test_canonicalize_constraints_random() {
  constexpr int samples = 1000;
  constexpr int parameters = 1000;
  for (int i = 0; i < parameters; i++) {
    S s1 = uniform_random<U>();
    S s2 = uniform_random<U>();
    S lo = MIN2(s1, s2);
    S hi = MAX2(s1, s2);
    U u1 = uniform_random<U>();
    U u2 = uniform_random<U>();
    U ulo = MIN2(u1, u2);
    U uhi = MAX2(u1, u2);
    U b1 = uniform_random<U>();
    U b2 = uniform_random<U>();
    U intersection = b1 & b2;
    U zeros = b1 ^ intersection;
    U ones = b2 ^ intersection;
    TypeIntPrototype<S, U> t{{lo, hi}, {ulo, uhi}, {zeros, ones}};
    auto new_t = t.canonicalize_constraints();
    if (new_t._present) {
      DEBUG_ONLY(new_t._data.verify_constraints());
    }
    for (int j = 0; j < samples; j++) {
      S v = uniform_random<U>();
      if (!new_t._present) {
        DEBUG_ONLY(ASSERT_FALSE(t.contains(v)));
      } else {
        DEBUG_ONLY(ASSERT_EQ(t.contains(v), new_t._data.contains(v)));
      }
    }
  }
}

TEST_VM(opto, canonicalize_constraints) {
  test_canonicalize_constraints_trivial();
  test_canonicalize_constraints_exhaustive<intn_t<1>, uintn_t<1>>();
  test_canonicalize_constraints_exhaustive<intn_t<2>, uintn_t<2>>();
  test_canonicalize_constraints_exhaustive<intn_t<3>, uintn_t<3>>();
  test_canonicalize_constraints_exhaustive<intn_t<4>, uintn_t<4>>();
  test_canonicalize_constraints_simple<jint, juint>();
  test_canonicalize_constraints_simple<jlong, julong>();
  test_canonicalize_constraints_random<jint, juint>();
  test_canonicalize_constraints_random<jlong, julong>();
}

// Implementations of TypeIntMirror methods for testing purposes
template <class S, class U>
TypeIntMirror<S, U> TypeIntMirror<S, U>::make(const TypeIntMirror<S, U>& t, int widen) {
  return t;
}

template <class S, class U>
const TypeIntMirror<S, U>* TypeIntMirror<S, U>::operator->() const {
  return this;
}

template <class S, class U>
bool TypeIntMirror<S, U>::contains(U u) const {
  S s = S(u);
  return s >= _lo && s <= _hi && u >= _ulo && u <= _uhi && _bits.is_satisfied_by(u);
}

template <class S, class U>
bool TypeIntMirror<S, U>::contains(const TypeIntMirror& o) const {
  return TypeIntHelper::int_type_is_subset(*this, o);
}

template <class S, class U>
bool TypeIntMirror<S, U>::operator==(const TypeIntMirror& o) const {
  return TypeIntHelper::int_type_is_equal(*this, o);
}

template <class S, class U>
template <class T>
TypeIntMirror<S, U> TypeIntMirror<S, U>::cast() const {
  static_assert(std::is_same_v<T, TypeIntMirror>);
  return *this;
}

// The number of TypeIntMirror instances for integral types with a few bits. These values are
// calculated once and written down for usage in constexpr contexts.
template <class CTP>
static constexpr size_t all_instances_size() {
  using U = decltype(CTP::_ulo);
  constexpr juint max_unsigned = juint(std::numeric_limits<U>::max());
  if constexpr (max_unsigned == 1U) {
    // 1 bit
    return 3;
  } else if constexpr (max_unsigned == 3U) {
    // 2 bits
    return 15;
  } else if constexpr (max_unsigned == 7U) {
    // 3 bits
    return 134;
  } else {
    // 4 bits
    static_assert(max_unsigned == 15U);
    // For more than 4 bits, the number of instances is too large and it is not realistic to
    // compute all of them.
    return 1732;
  }
}

template <class CTP>
static std::array<CTP, all_instances_size<CTP>()> compute_all_instances() {
  using S = decltype(CTP::_lo);
  using U = decltype(CTP::_ulo);

  class CTPComparator {
  public:
    static RBTreeOrdering cmp(const CTP& x, const RBNode<CTP, int>* node) {
      // Quick helper for the tediousness below
      auto f = [](auto x, auto y) {
        assert(x != y, "we only handle lt and gt cases");
        return x < y ? RBTreeOrdering::LT : RBTreeOrdering::GT;
      };

      const CTP& y = node->key();
      if (x._lo != y._lo) {
        return f(x._lo, y._lo);
      } else if (x._hi != y._hi) {
        return f(x._hi, y._hi);
      } else if (x._ulo != y._ulo) {
        return f(x._ulo, y._ulo);
      } else if (x._uhi != y._uhi) {
        return f(x._uhi, y._uhi);
      } else if (x._bits._zeros != y._bits._zeros) {
        return f(x._bits._zeros, y._bits._zeros);
      } else if (x._bits._ones != y._bits._ones) {
        return f(x._bits._ones, y._bits._ones);
      } else {
        return RBTreeOrdering::EQ;
      }
    }
  };

  RBTreeCHeap<CTP, int, CTPComparator, MemTag::mtCompiler> collector;
  for (jint lo = jint(std::numeric_limits<S>::min()); lo <= jint(std::numeric_limits<S>::max()); lo++) {
    for (jint hi = lo; hi <= jint(std::numeric_limits<S>::max()); hi++) {
      for (juint ulo = 0; ulo <= juint(std::numeric_limits<U>::max()); ulo++) {
        for (juint uhi = ulo; uhi <= juint(std::numeric_limits<U>::max()); uhi++) {
          for (juint zeros = 0; zeros <= juint(std::numeric_limits<U>::max()); zeros++) {
            for (juint ones = 0; ones <= juint(std::numeric_limits<U>::max()); ones++) {
              TypeIntPrototype<S, U> t{{S(lo), S(hi)}, {U(ulo), U(uhi)}, {U(zeros), U(ones)}};
              auto canonicalized_t = t.canonicalize_constraints();
              if (canonicalized_t.empty()) {
                continue;
              }

              TypeIntPrototype<S, U> ct = canonicalized_t._data;
              collector.upsert(CTP{ct._srange._lo, ct._srange._hi, ct._urange._lo, ct._urange._hi, ct._bits}, 0);
            }
          }
        }
      }
    }
  }

  assert(collector.size() == all_instances_size<CTP>(), "unexpected size of all_instance, expected %d, actual %d", jint(all_instances_size<CTP>()), jint(collector.size()));
  std::array<CTP, all_instances_size<CTP>()> res;
  size_t idx = 0;
  collector.visit_in_order([&](RBNode<CTP, int>* node) {
    res[idx] = node->key();
    idx++;
    return true;
  });
  return res;
}

template <class CTP>
static const std::array<CTP, all_instances_size<CTP>()>& all_instances() {
  static std::array<CTP, all_instances_size<CTP>()> res = compute_all_instances<CTP>();
  static_assert(std::is_trivially_destructible_v<decltype(res)>);
  return res;
}

// Check the correctness, that is, if v1 is an element of input1, v2 is an element of input2, then
// op(v1, v2) must be an element of infer(input1, input2). This version does the check exhaustively
// on all elements of input1 and input2.
template <class InputType, class Operation, class Inference>
static void test_binary_instance_correctness_exhaustive(Operation op, Inference infer, const InputType& input1, const InputType& input2) {
  using S = std::remove_const_t<decltype(input1->_lo)>;
  using U = std::remove_const_t<decltype(input1->_ulo)>;
  InputType result = infer(input1, input2);

  for (juint v1 = juint(std::numeric_limits<U>::min()); v1 <= juint(std::numeric_limits<U>::max()); v1++) {
    if (!input1.contains(U(v1))) {
      continue;
    }

    for (juint v2 = juint(std::numeric_limits<U>::min()); v2 <= juint(std::numeric_limits<U>::max()); v2++) {
      if (!input2.contains(U(v2))) {
        continue;
      }

      U r = op(U(v1), U(v2));
      ASSERT_TRUE(result.contains(r));
    }
  }
}

// Check the correctness, that is, if v1 is an element of input1, v2 is an element of input2, then
// op(v1, v2) must be an element of infer(input1, input2). This version does the check randomly on
// a number of elements in input1 and input2.
template <class InputType, class Operation, class Inference>
static void test_binary_instance_correctness_samples(Operation op, Inference infer, const InputType& input1, const InputType& input2) {
  using U = std::remove_const_t<decltype(input1->_ulo)>;
  auto result = infer(input1, input2);

  constexpr size_t sample_count = 6;
  U input1_samples[sample_count] {U(input1._lo), U(input1._hi), input1._ulo, input1._uhi, input1._ulo, input1._ulo};
  U input2_samples[sample_count] {U(input2._lo), U(input2._hi), input2._ulo, input2._uhi, input2._ulo, input2._ulo};

  auto random_sample = [](U* samples, const InputType& input) {
    constexpr size_t max_tries = 100;
    constexpr size_t start_random_idx = 4;
    for (size_t tries = 0, idx = start_random_idx; tries < max_tries && idx < sample_count; tries++) {
      U n = uniform_random<U>();
      if (input.contains(n)) {
        samples[idx] = n;
        idx++;
      }
    }
  };
  random_sample(input1_samples, input1);
  random_sample(input2_samples, input2);

  for (size_t i = 0; i < sample_count; i++) {
    for (size_t j = 0; j < sample_count; j++) {
      U r = op(input1_samples[i], input2_samples[j]);
      ASSERT_TRUE(result.contains(r));
    }
  }
}

// Check the monotonicity, that is, if input1 is a subset of super1, input2 is a subset of super2,
// then infer(input1, input2) must be a subset of infer(super1, super2). This version does the
// check exhaustively on all supersets of input1 and input2.
template <class InputType, class Inference>
static void test_binary_instance_monotonicity_exhaustive(Inference infer, const InputType& input1, const InputType& input2) {
  InputType result = infer(input1, input2);

  for (const InputType& super1 : all_instances<InputType>()) {
    if (!super1.contains(input1) || super1 == input1) {
      continue;
    }

    for (const InputType& super2 : all_instances<InputType>()) {
      if (!super2.contains(input2) || super2 == input2) {
        continue;
      }

      ASSERT_TRUE(infer(input1, super2).contains(result));
      ASSERT_TRUE(infer(super1, input2).contains(result));
      ASSERT_TRUE(infer(super1, super2).contains(result));
    }
  }
}

// Check the monotonicity, that is, if input1 is a subset of super1, input2 is a subset of super2,
// then infer(input1, input2) must be a subset of infer(super1, super2). This version does the
// check randomly on a number of supersets of input1 and input2.
template <class InputType, class Inference>
static void test_binary_instance_monotonicity_samples(Inference infer, const InputType& input1, const InputType& input2) {
  using S = std::remove_const_t<decltype(input1->_lo)>;
  using U = std::remove_const_t<decltype(input1->_ulo)>;
  auto result = infer(input1, input2);

  // The set that is a superset of all other sets
  InputType universe = InputType{std::numeric_limits<S>::min(), std::numeric_limits<S>::max(), U(0), U(-1), {U(0), U(0)}};
  ASSERT_TRUE(infer(universe, input2).contains(result));
  ASSERT_TRUE(infer(input1, universe).contains(result));
  ASSERT_TRUE(infer(universe, universe).contains(result));

  auto random_superset = [](const InputType& input) {
    S lo = MIN2(input->_lo, S(uniform_random<U>()));
    S hi = MAX2(input->_hi, S(uniform_random<U>()));
    U ulo = MIN2(input->_ulo, uniform_random<U>());
    U uhi = MAX2(input->_uhi, uniform_random<U>());
    U zeros = input->_bits._zeros & uniform_random<U>();
    U ones = input->_bits._ones & uniform_random<U>();
    InputType super = InputType::make(TypeIntPrototype<S, U>{{lo, hi}, {ulo, uhi}, {zeros, ones}}, 0);
    assert(super.contains(input), "impossible");
    return super;
  };

  InputType super1 = random_superset(input1);
  InputType super2 = random_superset(input2);
  ASSERT_TRUE(infer(super1, input2).contains(result));
  ASSERT_TRUE(infer(input1, super2).contains(result));
  ASSERT_TRUE(infer(super1, super2).contains(result));
}

// Verify the correctness and monotonicity of an inference function by exhautively analyzing all
// instances of InputType
template <class InputType, class Operation, class Inference>
static void test_binary_exhaustive(Operation op, Inference infer) {
  for (const InputType& input1 : all_instances<InputType>()) {
    for (const InputType& input2 : all_instances<InputType>()) {
      test_binary_instance_correctness_exhaustive(op, infer, input1, input2);
      if (all_instances<InputType>().size() < 100) {
        // This effectively covers the cases up to uintn_t<2>
        test_binary_instance_monotonicity_exhaustive(infer, input1, input2);
      } else {
        // This effectively covers the cases of uintn_t<3>
        test_binary_instance_monotonicity_samples(infer, input1, input2);
      }
    }
  }
}

// Verify the correctness and monotonicity of an inference function by randomly sampling instances
// of InputType
template <class InputType, class Operation, class Inference>
static void test_binary_random(Operation op, Inference infer) {
  using S = std::remove_const_t<decltype(InputType::_lo)>;
  using U = std::remove_const_t<decltype(InputType::_ulo)>;

  constexpr size_t sample_count = 100;
  InputType samples[sample_count];

  // Fill with {0}
  for (size_t i = 0; i < sample_count; i++) {
    samples[i] = InputType::make(TypeIntPrototype<S, U>{{S(0), S(0)}, {U(0), U(0)}, {U(0), U(0)}}, 0);
  }

  // {1}
  samples[1] = InputType::make(TypeIntPrototype<S, U>{{S(1), S(1)}, {U(1), U(1)}, {U(0), U(0)}}, 0);
  // {-1}
  samples[2] = InputType::make(TypeIntPrototype<S, U>{{S(-1), S(-1)}, {U(-1), U(-1)}, {U(0), U(0)}}, 0);
  // {0, 1}
  samples[3] = InputType::make(TypeIntPrototype<S, U>{{S(0), S(1)}, {U(0), U(1)}, {U(0), U(0)}}, 0);
  // {-1, 0, 1}
  samples[4] = InputType::make(TypeIntPrototype<S, U>{{S(-1), S(1)}, {U(0), U(-1)}, {U(0), U(0)}}, 0);
  // {-1, 1}
  samples[5] = InputType::make(TypeIntPrototype<S, U>{{S(-1), S(1)}, {U(1), U(-1)}, {U(0), U(0)}}, 0);
  // {0, 1, 2}
  samples[6] = InputType::make(TypeIntPrototype<S, U>{{S(0), S(2)}, {U(0), U(2)}, {U(0), U(0)}}, 0);
  // {0, 2}
  samples[7] = InputType::make(TypeIntPrototype<S, U>{{S(0), S(2)}, {U(0), U(2)}, {U(1), U(0)}}, 0);
  // [min_signed, max_signed]
  samples[8] = InputType::make(TypeIntPrototype<S, U>{{std::numeric_limits<S>::min(), std::numeric_limits<S>::max()}, {U(0), U(-1)}, {U(0), U(0)}}, 0);
  // [0, max_signed]
  samples[9] = InputType::make(TypeIntPrototype<S, U>{{S(0), std::numeric_limits<S>::max()}, {U(0), U(-1)}, {U(0), U(0)}}, 0);
  // [min_signed, 0)
  samples[10] = InputType::make(TypeIntPrototype<S, U>{{std::numeric_limits<S>::min(), S(-1)}, {U(0), U(-1)}, {U(0), U(0)}}, 0);

  constexpr size_t max_tries = 1000;
  constexpr size_t start_random_idx = 11;
  for (size_t tries = 0, idx = start_random_idx; tries < max_tries && idx < sample_count; tries++) {
    // Try to have lo < hi
    S signed_bound1 = S(uniform_random<U>());
    S signed_bound2 = S(uniform_random<U>());
    S lo = MIN2(signed_bound1, signed_bound2);
    S hi = MAX2(signed_bound1, signed_bound2);

    // Try to have ulo < uhi
    U unsigned_bound1 = uniform_random<U>();
    U unsigned_bound2 = uniform_random<U>();
    U ulo = MIN2(unsigned_bound1, unsigned_bound2);
    U uhi = MAX2(unsigned_bound1, unsigned_bound2);

    // Try to have (zeros & ones) == 0
    U zeros = uniform_random<U>();
    U ones = uniform_random<U>();
    U common = zeros & ones;
    zeros = zeros ^ common;
    ones = ones ^ common;

    TypeIntPrototype<S, U> t{{lo, hi}, {ulo, uhi}, {zeros, ones}};
    auto canonicalized_t = t.canonicalize_constraints();
    if (canonicalized_t.empty()) {
      continue;
    }

    samples[idx] = TypeIntMirror<S, U>{canonicalized_t._data._srange._lo, canonicalized_t._data._srange._hi,
                                       canonicalized_t._data._urange._lo, canonicalized_t._data._urange._hi,
                                       canonicalized_t._data._bits};
    idx++;
  }

  for (size_t i = 0; i < sample_count; i++) {
    for (size_t j = 0; j < sample_count; j++) {
      test_binary_instance_correctness_samples(op, infer, samples[i], samples[j]);
      test_binary_instance_monotonicity_samples(infer, samples[i], samples[j]);
    }
  }
}

template <template <class U> class Operation, template <class CTP> class Inference>
static void test_binary() {
  test_binary_exhaustive<TypeIntMirror<intn_t<1>, uintn_t<1>>>(Operation<uintn_t<1>>(), Inference<TypeIntMirror<intn_t<1>, uintn_t<1>>>());
  test_binary_exhaustive<TypeIntMirror<intn_t<2>, uintn_t<2>>>(Operation<uintn_t<2>>(), Inference<TypeIntMirror<intn_t<2>, uintn_t<2>>>());
  test_binary_exhaustive<TypeIntMirror<intn_t<3>, uintn_t<3>>>(Operation<uintn_t<3>>(), Inference<TypeIntMirror<intn_t<3>, uintn_t<3>>>());
  test_binary_random<TypeIntMirror<intn_t<4>, uintn_t<4>>>(Operation<uintn_t<4>>(), Inference<TypeIntMirror<intn_t<4>, uintn_t<4>>>());
  test_binary_random<TypeIntMirror<intn_t<5>, uintn_t<5>>>(Operation<uintn_t<5>>(), Inference<TypeIntMirror<intn_t<5>, uintn_t<5>>>());
  test_binary_random<TypeIntMirror<intn_t<6>, uintn_t<6>>>(Operation<uintn_t<6>>(), Inference<TypeIntMirror<intn_t<6>, uintn_t<6>>>());
  test_binary_random<TypeIntMirror<jint, juint>>(Operation<juint>(), Inference<TypeIntMirror<jint, juint>>());
  test_binary_random<TypeIntMirror<jlong, julong>>(Operation<julong>(), Inference<TypeIntMirror<jlong, julong>>());
}

template <class U>
class OpAnd {
public:
  U operator()(U v1, U v2) const {
    return v1 & v2;
  }
};

template <class CTP>
class InferAnd {
public:
  CTP operator()(CTP t1, CTP t2) const {
    return RangeInference::infer_and(t1, t2);
  }
};

template <class U>
class OpOr {
public:
  U operator()(U v1, U v2) const {
    return v1 | v2;
  }
};

template <class CTP>
class InferOr {
public:
  CTP operator()(CTP t1, CTP t2) const {
    return RangeInference::infer_or(t1, t2);
  }
};

template <class U>
class OpXor {
public:
  U operator()(U v1, U v2) const {
    return v1 ^ v2;
  }
};

template <class CTP>
class InferXor {
public:
  CTP operator()(CTP t1, CTP t2) const {
    return RangeInference::infer_xor(t1, t2);
  }
};

TEST(opto, range_inference) {
  test_binary<OpAnd, InferAnd>();
  test_binary<OpOr, InferOr>();
  test_binary<OpXor, InferXor>();
}
