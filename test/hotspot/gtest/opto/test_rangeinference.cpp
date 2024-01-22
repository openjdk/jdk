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
#include "runtime/os.hpp"
#include "unittest.hpp"

#include <limits>

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

template <class T, class U>
static void test_normalize_constraints_simple() {
  constexpr int parameters = 10;
  for (int i = 0; i < parameters; i++) {
    T a = uniform_random<U>();
    T b = uniform_random<U>();

    {
      T lo = MIN2<T>(a, b);
      T hi = MAX2<T>(a, b);
      TypeIntPrototype<T, U> t{{lo, hi}, {std::numeric_limits<U>::min(), std::numeric_limits<U>::max()},
                               {0, 0}};
      auto new_t = t.normalize_constraints();
      ASSERT_TRUE(new_t.first);
      DEBUG_ONLY(new_t.second.verify_constraints());
      ASSERT_EQ(lo, new_t.second._srange._lo);
      ASSERT_EQ(hi, new_t.second._srange._hi);
      if (U(lo) <= U(hi)) {
        ASSERT_EQ(U(lo), new_t.second._urange._lo);
        ASSERT_EQ(U(hi), new_t.second._urange._hi);
      } else {
        ASSERT_EQ(std::numeric_limits<U>::min(), new_t.second._urange._lo);
        ASSERT_EQ(std::numeric_limits<U>::max(), new_t.second._urange._hi);
      }
    }

    {
      U ulo = MIN2<U>(a, b);
      U uhi = MAX2<U>(a, b);
      TypeIntPrototype<T, U> t{{std::numeric_limits<T>::min(), std::numeric_limits<T>::max()},
                               {ulo, uhi}, {0, 0}};
      auto new_t = t.normalize_constraints();
      ASSERT_TRUE(new_t.first);
      DEBUG_ONLY(new_t.second.verify_constraints());
      ASSERT_EQ(ulo, new_t.second._urange._lo);
      ASSERT_EQ(uhi, new_t.second._urange._hi);
      if (T(ulo) <= T(uhi)) {
        ASSERT_EQ(T(ulo), new_t.second._srange._lo);
        ASSERT_EQ(T(uhi), new_t.second._srange._hi);
      } else {
        ASSERT_EQ(std::numeric_limits<T>::min(), new_t.second._srange._lo);
        ASSERT_EQ(std::numeric_limits<T>::max(), new_t.second._srange._hi);
      }
    }

    {
      U intersection = a & b;
      U zeros = a ^ intersection;
      U ones = b ^ intersection;
      TypeIntPrototype<T, U> t{{std::numeric_limits<T>::min(), std::numeric_limits<T>::max()},
                               {std::numeric_limits<U>::min(), std::numeric_limits<U>::max()}, {zeros, ones}};
      auto new_t = t.normalize_constraints();
      ASSERT_TRUE(new_t.first);
      DEBUG_ONLY(new_t.second.verify_constraints());
      ASSERT_EQ(zeros, new_t.second._bits._zeros);
      ASSERT_EQ(ones, new_t.second._bits._ones);
      ASSERT_EQ(ones, new_t.second._urange._lo);
      ASSERT_EQ(~zeros, new_t.second._urange._hi);
    }
  }
}

template <class T, class U>
static void test_normalize_constraints_random() {
  constexpr int samples = 1000;
  constexpr int parameters = 1000;
  int non_empty = 0;
  for (int i = 0; i < parameters; i++) {
    T s1 = uniform_random<U>();
    T s2 = uniform_random<U>();
    T lo = MIN2(s1, s2);
    T hi = MAX2(s1, s2);
    U u1 = uniform_random<U>();
    U u2 = uniform_random<U>();
    U ulo = MIN2(u1, u2);
    U uhi = MAX2(u1, u2);
    U b1 = uniform_random<U>();
    U b2 = uniform_random<U>();
    U intersection = b1 & b2;
    U zeros = b1 ^ intersection;
    U ones = b2 ^ intersection;
    TypeIntPrototype<T, U> t{{lo, hi}, {ulo, uhi}, {zeros, ones}};
    auto new_t = t.normalize_constraints();
#ifdef ASSERT
    if (new_t.first) {
      new_t.second.verify_constraints();
    }
    for (int j = 0; j < samples; j++) {
      T v = uniform_random<U>();
      if (!new_t.first) {
        ASSERT_FALSE(t.contains(v));
      } else {
        ASSERT_EQ(t.contains(v), new_t.second.contains(v));
      }
    }
  }
#endif // ASSERT
}

TEST_VM(opto, normalize_constraints) {
  test_normalize_constraints_simple<jint, juint>();
  test_normalize_constraints_simple<jlong, julong>();
  test_normalize_constraints_random<jint, juint>();
  test_normalize_constraints_random<jlong, julong>();
}
