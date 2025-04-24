/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "utilities/globalDefinitions.hpp"
#include "utilities/reverse_bits.hpp"
#include "unittest.hpp"

template<typename T>
static inline void test_reverse_bits() {
  const int  NBIT = sizeof(T) * 8;
  const bool IS_U = (T)-1 > 0;
  const int XOR_REV_BITS = (NBIT - 1);
  ASSERT_EQ(reverse_bits((T)0), (T)0);
  ASSERT_EQ(reverse_bits((T)-1), (T)-1);
  for (int i1 = 0; i1 < NBIT; i1++) {
    T mask1 = (T)1 << i1;
    T revm1 = (T)1 << (i1 ^ XOR_REV_BITS);
    for (int i2 = 0; i2 <= i1; i2++) {
      T mask2 = (T)1 << i2;
      T revm2 = (T)1 << (i2 ^ XOR_REV_BITS);
      T mask = mask1|mask2;
#define STUFF (IS_U?"u":"s") << NBIT << "@" << i1 << "," << i2
      ASSERT_EQ(reverse_bits(mask), revm1|revm2) << STUFF;
      ASSERT_EQ((T)~reverse_bits((T)~mask), revm1|revm2) << STUFF;
    }
  }
}

TEST_VM(utilities, reverse_bits) {
  test_reverse_bits<int64_t>();
  test_reverse_bits<uint64_t>();
  test_reverse_bits<int32_t>();
  test_reverse_bits<uint32_t>();
  test_reverse_bits<int16_t>();
  test_reverse_bits<uint16_t>();
  test_reverse_bits<int8_t>();
  test_reverse_bits<uint8_t>();
}

// Here is some object code to look at if we want to do a manual
// study.  One could find the build file named test_reverse_bits.o.cmdline
// and hand-edit the command line to produce assembly code in
// test_reverse_bits.s.
//
// Or, given the two empty "fence functions", one could do a
// quick scan like this:
//
// $ objdump -D $(find build/*release -name test_reverse_bits.o) \
//   | sed -n '/start_code_quality/,$p;/end_code_quality/q' \
//   | egrep -B10 bswap  # or grep -B20 cfi_endproc

void start_code_quality_reverse_bits() { }

int32_t code_quality_reverse_bits_32(int32_t x) {
  return reverse_bits(x);
}

int64_t code_quality_reverse_bits_64(int64_t x) {
  return reverse_bits(x);
}
