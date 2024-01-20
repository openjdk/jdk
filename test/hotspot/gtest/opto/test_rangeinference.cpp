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
      T nlo = lo;
      T nhi = hi;
      U nulo = std::numeric_limits<U>::min();
      U nuhi = std::numeric_limits<U>::max();
      U nzeros = 0;
      U nones = 0;
      bool empty = false;
      normalize_constraints(empty, nlo, nhi, nulo, nuhi, nzeros, nones);
      DEBUG_ONLY(verify_constraints(nlo, nhi, nulo, nuhi, nzeros, nones));
      ASSERT_FALSE(empty);
      ASSERT_EQ(lo, nlo);
      ASSERT_EQ(hi, nhi);
      if (U(lo) <= U(hi)) {
        ASSERT_EQ(U(lo), nulo);
        ASSERT_EQ(U(hi), nuhi);
      } else {
        ASSERT_EQ(std::numeric_limits<U>::min(), nulo);
        ASSERT_EQ(std::numeric_limits<U>::max(), nuhi);
      }
    }

    {
      U ulo = MIN2<U>(a, b);
      U uhi = MAX2<U>(a, b);
      T nlo = std::numeric_limits<T>::min();
      T nhi = std::numeric_limits<T>::max();
      U nulo = ulo;
      U nuhi = uhi;
      U nzeros = 0;
      U nones = 0;
      bool empty = false;
      normalize_constraints(empty, nlo, nhi, nulo, nuhi, nzeros, nones);
      DEBUG_ONLY(verify_constraints(nlo, nhi, nulo, nuhi, nzeros, nones));
      ASSERT_FALSE(empty);
      ASSERT_EQ(ulo, nulo);
      ASSERT_EQ(uhi, nuhi);
      if (T(ulo) <= T(uhi)) {
        ASSERT_EQ(T(ulo), nlo);
        ASSERT_EQ(T(uhi), nhi);
      } else {
        ASSERT_EQ(std::numeric_limits<T>::min(), nlo);
        ASSERT_EQ(std::numeric_limits<T>::max(), nhi);
      }
    }

    {
      U intersection = a & b;
      U zeros = a ^ intersection;
      U ones = b ^ intersection;
      T nlo = std::numeric_limits<T>::min();
      T nhi = std::numeric_limits<T>::max();
      U nulo = std::numeric_limits<U>::min();
      U nuhi = std::numeric_limits<U>::max();
      U nzeros = zeros;
      U nones = ones;
      bool empty = false;
      normalize_constraints(empty, nlo, nhi, nulo, nuhi, nzeros, nones);
      DEBUG_ONLY(verify_constraints(nlo, nhi, nulo, nuhi, nzeros, nones));
      ASSERT_FALSE(empty);
      ASSERT_EQ(zeros, nzeros);
      ASSERT_EQ(ones, nones);
      ASSERT_EQ(ones, nulo);
      ASSERT_EQ(~zeros, nuhi);
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
    T nlo = lo;
    T nhi = hi;
    U nulo = ulo;
    U nuhi = uhi;
    U nzeros = zeros;
    U nones = ones;
    bool empty = false;
    normalize_constraints(empty, nlo, nhi, nulo, nuhi, nzeros, nones);
    auto contains = [](T lo, T hi, U ulo, U uhi, U zeros, U ones, T value) {
      U u = value;
      return value >= lo && value <= hi && u >= ulo && u <= uhi &&
             (u & zeros) == 0 && (~u & ones) == 0;
    };
#ifdef ASSERT
    if (!empty) {
      verify_constraints(nlo, nhi, nulo, nuhi, nzeros, nones);
    }
#endif // ASSERT
    for (int j = 0; j < samples; j++) {
      T v = uniform_random<U>();
      if (empty) {
        ASSERT_FALSE(contains(lo, hi, ulo, uhi, zeros, ones, v));
      } else {
        ASSERT_EQ(contains(lo, hi, ulo, uhi, zeros, ones, v), contains(nlo, nhi, nulo, nuhi, nzeros, nones, v));
      }
    }
  }
}

TEST_VM(opto, normalize_constraints) {
  test_normalize_constraints_simple<jint, juint>();
  test_normalize_constraints_simple<jlong, julong>();
  test_normalize_constraints_random<jint, juint>();
  test_normalize_constraints_random<jlong, julong>();
}
