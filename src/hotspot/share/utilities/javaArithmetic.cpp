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
#include <limits>
#include <type_traits>
#include "utilities/powerOfTwo.hpp"

/*
Compute magic multiplier and shift constant for converting a 32/64 bit
division by constant into a multiply/shift series.

(1) Theory:
Motivated by Henry S. Warren. 2012. Hacker's Delight (2nd. ed.). Addison-Wesley Professional.

Given positive integers d <= N, call v the largest integer not larger than
N such that v + 1 is divisible by d.

(a) For positive values c, m such that:

m <= c * d < m + m / v

We have:

floor(x / d) = floor(x * c / m) for every integer x in [0, N]

(b) For positive values c, m such that:

m < c * d <= m + m / v

We have:

ceil(x / d) = floor(x * c / m) + 1 for every integer x in [-N, 0)

(2) Proof:

(a) The conclusion is trivial for x = 0

For 0 < x <= v

Since 1 / d <= c / m < (1 / d) * ((v + 1) / v)

We have x / d <= x * c / m < (x * ((v + 1) / v)) / d

As a result, since x * ((v + 1) / v) <= x * ((x + 1) / x) = x + 1

x / d <= x * c / m < (x + 1) / d, which implies floor(x / d) = floor(x * c / m) since
there can be no integer in (x / d, (x + 1) / d)

For v + 1 <= x <= v + d - 1, since v >= d - 1, we have x <= 2v
As a result, x * ((v + 1) / v) <= x * ((x + 2) / x)

floor(x / d) = (v + 1) / d
floor(x * ((v + 1) / v) / d) <= floor(x * ((x + 2) / x) / d) = floor((x + 2) / d)
                             <= floor((v + d - 1 + 2) / d) = (v + 1) / d + 1
Which means (v + 1) / d <= floor(x * c / m) < (v + 1) / d + 1 with (v + 1) / d being an integer
This implies floor(x / d) = floor(x * c / m) for v + 1 <= x <= v + d - 1

Combining all the cases gives us the conclusion.

(b) Since ceil(a / b) = floor((a - 1) / b) + 1, we need to prove:

floor((x - 1) / d) = floor(x * c / m)

For 0 > x >= -v

Since 1 / d < c / m <= (1 / d) * ((v + 1) / v)

We have x / d > x * c / m >= (x / d) * ((v + 1) / v)

since x * ((v + 1) / v) >= x * ((x - 1) / x) = x - 1
x / d > x * c / m >= (x - 1) / d, which implies floor((x - 1) / d) = floor(x * c / m) since
there can be no integer in ((x - 1) / d, x / d)

For -v - d + 1 <= x <= -v - 1, since v >= d - 1, we have x >= -2v
As a result, x * ((v + 1) / v) >= x * ((x - 2) / x) = x - 2

x / d <= (-v - 1) / d
floor((x - 1) / d) = (-v - 1) / d - 1
floor(x * ((v + 1) / v) / d) >= floor((x - 2) / d) >= (-v - d + 1 - 2) / d = (-v - 1) / d - 1
which means (-v - 1) / d >= x / d > x * c / m >= floor(x * c / m) >= (-v - 1) / d - 1
This implies floor((x - 1) / d) = floor(x * c / m) for -v - 1 >= x >= -v - d + 1

Combining all the cases gives us the conclusion.

(3) Discussion:

Let x be v, v - d + 1, -v, -v + d - 1, it can be seen that these bounds are indeed optimal

(4) Implementation:

For computation efficiency, we only consider the values m = 2**s.
This function does not handle the cases d being a power of 2, which means
that c * d is never equal to m.

We find the value of c, m such that it satisfies the bounds for both the nonnegative
and negative ranges of x. This can be done by finding v_neg and v_pos and the bounds of
c * d - m is the intersection of (0, m / v_neg] and (0, m / v_pos). Which is (0, m / v_pos)
if v_pos >= v_neg and (0, m / v_neg] otherwise.

Given v = max(v_neg, v_pos). The function inductively calculates c, rc, qv, rv such that:

c * d - rc = 2**s with 0 < rc <= d
qv * v + rv = 2**s with 0 <= rv < v
*/
template <class T>
void magic_divide_constants(T d, T N_neg, T N_pos, juint min_s, T& c, bool& c_ovf, juint& s) {
  static_assert(std::is_unsigned<T>::value, "calculations must be done in the unsigned domain");
  assert(!is_power_of_2(d), "this case should be handled separately");
  assert(d <= N_neg || d <= N_pos, "this should just be idealized to 0");
  constexpr T min_signed = std::numeric_limits<std::make_signed_t<T>>::min();
  T v_neg = N_neg < d - 1 ? 0 : N_neg - ((N_neg - d + 1) % d);
  T v_pos = N_pos < d - 1 ? 0 : N_pos - ((N_pos - d + 1) % d);
  T v = MAX2(v_neg, v_pos);
  bool tolerate_equal = v_neg > v_pos;

  // base case, s = 0
  s = 0;
  c_ovf = false;
  c = 1;
  T rc = d - 1;
  bool qv_ovf = false;
  T qv = 0;
  T rv = 1;

  while (true) {
    if (s >= min_s) {
      if (qv_ovf || rc < qv || (rc == qv && (tolerate_equal || rv > 0))) {
        break;
      }
    }
    assert(!c_ovf, "must be");
    s++;

    T new_rc = rc * 2;
    if (new_rc < rc || new_rc > d) {
      c_ovf = c > min_signed;
      c = c * 2 - 1;
      rc = new_rc - d;
    } else {
      c_ovf = c >= min_signed;
      c = c * 2;
      rc = new_rc;
    }

    T new_rv = rv * 2;
    if (new_rv < rv || new_rv >= v) {
      qv_ovf = qv >= min_signed;
      qv = qv * 2 + 1;
      rv = new_rv - v;
    } else {
      qv_ovf = qv >= min_signed;
      qv = qv * 2;
      rv = new_rv;
    }
  }
}
template void magic_divide_constants<juint>(juint, juint, juint, juint, juint&, bool&, juint&);
template void magic_divide_constants<julong>(julong, julong, julong, juint, julong&, bool&, juint&);
template void magic_divide_constants<std::make_unsigned_t<jlong>>(julong, julong, julong, juint, julong&, bool&, juint&);

// The constant of a W-bit signed division lies in the range of W-bit unsigned
// integers. As a result, the product of the dividend and the magic constant cannot
// overflow a 2W-bit signed integer.
//
// For unsigned division however, the magic constant may lie outside the range
// of W-bit unsigned integers, which means the product of it and the dividend can
// overflow a 2W-bit unsigned integer. In those cases, we use another algorithm.
//
// Given s = floor(log2(d)) + W, c = floor(2**s / d) we have
//
// floor(x / d) = floor((x + 1) * c / 2**s) for every integer x in [0, 2**W).
//
// The proof can be found at: Robison, A.D.. (2005). N-bit unsigned division via
// N-bit multiply-add. Proceedings - Symposium on Computer Arithmetic. 131- 139.
// 10.1109/ARITH.2005.31.
//
// This is called round down because we round 1 / d down to c / 2**s
void magic_divide_constants_round_down(juint d, juint& c, juint& s) {
  assert(!is_power_of_2(d), "this case should be handled separately");
  constexpr juint W = 32;

  s = log2i_graceful(d) + W;
  julong t = (julong(1) << s) / julong(d);
  c = t;
#ifdef ASSERT
  julong r = ((t + 1) * julong(d)) & julong(max_juint);
  assert(r > (julong(1) << (s - W)), "Should call up first since it is more efficient");
#endif
}
