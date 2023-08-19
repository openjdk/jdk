/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

#include <random>
#include <type_traits>
#include <vector>
#include "utilities/javaArithmetic.hpp"
#include "utilities/powerOfTwo.hpp"
#include "unittest.hpp"

template <class T, class U>
static void test_division(T d, T N_neg, T N_pos, juint min_s) {
  constexpr juint W = sizeof(T) * 8;

  if ((N_neg < d && N_pos < d) || (d & (d - 1)) == 0) {
    return;
  }

  T c;
  bool c_ovf;
  juint s;
  magic_divide_constants(d, N_neg, N_pos, min_s, c, c_ovf, s);

  U lo = -U(N_neg);
  U hi = N_pos;
  U d_long = d;
  U c_long = c;

  auto test = [&](U l) {
    if (l < lo || l > hi) {
      return;
    }

    U expected = l / d;
    U actual;
    if (!c_ovf) {
      actual = ((l * U(c)) >> s) + (l < 0 ? U(1) : U(0));
    } else {
      ASSERT_EQ(N_neg, T(0));
      if (sizeof(U) > sizeof(T) * 2) {
        constexpr U wrap_amount = U(T(-1)) + 1;
        actual = (l * (U(c) + wrap_amount)) >> s;
      } else {
        U mul_hi = (l * U(c)) >> W;
        actual = (((l - mul_hi) >> 1) + mul_hi) >> (s - 1 - W);
      }
    }
    ASSERT_EQ(expected, actual);
  };

  test(0);
  if (hi >= d_long) {
    test(d_long);
    test(hi - (hi + 1) % d_long);
  }
  if (N_neg >= d_long) {
    test(-d_long);
    test(lo - (lo - 1) % d_long);
  }
}

template <class T, class U>
static void test_division_random() {
  constexpr int ITER = 10000;
  using UT = typename std::make_unsigned<T>::type;
  std::random_device rd;
  std::minstd_rand rng(rd());
  std::uniform_int_distribution<UT> N_dist(0, std::numeric_limits<T>::max());
  std::uniform_int_distribution<UT> d_dist(0, std::numeric_limits<T>::max());
  std::uniform_int_distribution<juint> min_s_dist(0, sizeof(T) * 8);
  for (int i = 0; i < ITER; i++) {
    UT d = d_dist(rng);
    UT N_neg = std::is_signed<T>::value ? N_dist(rng) + 1 : 0;
    UT N_pos = N_dist(rng);
    juint min_s = min_s_dist(rng);
    test_division<UT, U>(d, N_neg, N_pos, min_s);
  }
}

template <class T, class U>
static void test_division_fixed(const std::vector<julong>& values) {
  using UT = typename std::make_unsigned<T>::type;
  for (julong N_neg : values) {
    if (N_neg > UT(std::numeric_limits<T>::min())) {
      continue;
    }
    for (julong N_pos : values) {
      if (N_pos > UT(std::numeric_limits<T>::max())) {
        continue;
      }
      for (julong d : values) {
        for (juint min_s = 0; min_s <= sizeof(T) * 8; min_s++) {
          test_division<UT, U>(d, N_neg, N_pos, min_s);
        }
      }
    }
  }
}

TEST(opto, divide_by_constants) {
  std::vector<julong> values{0, 1, 2, 3, 5, 6, 7, 8, 11, 14, 15, 19, 29, 60, 101, 1000, 9999, 1000000,
    max_jint - 10, max_jint - 1, max_jint, julong(max_jint) + 1, julong(max_jint) + 2, julong(max_jint) + 11,
    max_juint - 10, max_juint - 1, max_juint, julong(max_juint) + 1, julong(max_juint) + 2, julong(max_juint) + 11,
    max_jlong - 10, max_jlong - 1, max_jlong, julong(max_jlong) + 1, julong(max_jlong) + 2, julong(max_jlong) + 11,
    max_julong - 10, max_julong - 1, max_julong};

#ifdef __SIZEOF_INT128__
  test_division_fixed<jint, __int128>(values);
  test_division_fixed<juint, __int128>(values);
  test_division_fixed<jlong, __int128>(values);
  test_division_fixed<julong, unsigned __int128>(values);
  test_division_random<jint, __int128>();
  test_division_random<juint, __int128>();
  test_division_random<jlong, __int128>();
  test_division_random<julong, unsigned __int128>();
#else
  test_division_fixed<jint, jlong>(values);
  test_division_fixed<juint, julong>(values);
  test_division_random<jint, jlong>();
  test_division_random<juint, julong>();
#endif // __SIZEOF_INT128__
}
