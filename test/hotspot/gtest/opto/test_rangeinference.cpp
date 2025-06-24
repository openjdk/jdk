/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
#include "utilities/intn_t.hpp"
#include "unittest.hpp"

template <class U>
static U uniform_random();

template <>
juint uniform_random<juint>() {
  return os::random();
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
