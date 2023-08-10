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
#include <type_traits>
#include "utilities/globalDefinitions.hpp"
#include "utilities/powerOfTwo.hpp"

// Compute magic multiplier and shift constant for converting a 32/64 bit
// division by constant into a multiply/shift series.
//
// For signed division, this function finds M, s such that:
// 2**(N + s) < M * d <= 2**(N + s) + 2**(s + 1)
// For each s, we find the smallest number M such that M * d > 2**(N + s)
// and check if M * d - 2**(N + s) <= 2**(s + 1).
//
// For unsigned division, this function finds M, s such that:
// 2**(N + s) <= M * d <= 2**(N + s) + 2**s
// For each s, we find the smallest number M such that M * d > 2**(N + s)
// and check if M * d - 2**(N + s) <= 2**s.
//
// Detailed theory can be found in: Granlund, Torbjorn & Montgomery, Peter.
// (2004). Division by Invariant Integers using Multiplication. 
template <class T, bool is_unsigned>
void magic_divide_constant(T d, T& M, jint& s) {
  assert(d > 1, "sanity");
  assert((d & (d - 1)) != 0, "this case should be handled separately");
  // base case, s = 0
  s = 0;
  M = -d / d + 2;
  T r = M * d;
  T bound = is_unsigned ? 1 : 2;
  while (r > bound) {
    // induction,
    // M * d = 2**(N + s) + r implies
    // M * 2 * d = 2**(N + s + 1) + r * 2 and
    // (M * 2 - 1) * d = 2**(N + s + 1) + (r * 2 - d)
    s++;
    bound *= 2;
    r *= 2;
    T newM;
    if (r > d) {
      r -= d;
      newM = M * 2 - 1;
    } else {
      newM = M * 2;
    }
    assert(newM > M || (r <= bound && is_unsigned), "cannot overflow");
    M = newM;
  }

  assert(s >= 0 && s < sizeof(T) * 8 + is_unsigned, "sanity");
}
template void magic_divide_constant<juint, true>(juint, juint&, jint&);
template void magic_divide_constant<juint, false>(juint, juint&, jint&);
template void magic_divide_constant<julong, true>(julong, julong&, jint&);
template void magic_divide_constant<julong, false>(julong, julong&, jint&);

// The constant of a N-bit signed division lies in the range of N-bit unsigned
// integers. As a result, the product of the dividend and the magic constant cannot
// overflow a 2N-bit signed integer.
//
// For unsigned division however, the magic constant may lie outside the range
// of N-bit unsigned integers, which means the product of it and the dividend can
// overflow a 2N-bit unsigned integer. In those cases, given s = floor(log2(d))
// floor(x / d) = floor((x + 1) * floor(2**(N + s) / d) / 2**(N + s)) with all
// values of x in [0, 2**N).
//
// The proof can be found at: Robison, A.D.. (2005). N-bit unsigned division via
// N-bit multiply-add. Proceedings - Symposium on Computer Arithmetic. 131- 139.
// 10.1109/ARITH.2005.31. 
template <class T>
void magic_divide_constant_round_up(T d, T& M, jint& s) {
  assert(d > 1, "sanity");
  assert((d & (d - 1)) != 0, "this case should be handled separately");
  
  s = log2i_graceful(d);
  // Calculate 2**(N + s) / d from 2**N / d similar to above
  M = -d / d + 1;
  r = -M * d;
  for (int i = 0; i < s; i++) {
    r *= 2;
    if (r >= d) {
      M = M * 2 + 1;
      r -= d;
    } else {
      M = M * 2;
    }
  }
}
template void magic_divide_constant_round_up<juint>(juint, juint&, jint&);
template void magic_divide_constant_round_up<julong>(julong, julong&, jint&);
