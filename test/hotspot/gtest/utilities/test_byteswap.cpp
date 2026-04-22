/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "utilities/byteswap.hpp"
#include "utilities/globalDefinitions.hpp"
#include "unittest.hpp"

template<typename T>
static inline void test_byteswap() {
  const int  NBIT = sizeof(T) * 8;
  const bool IS_U = (T)-1 > 0;
  const int XOR_REV_BITS = (NBIT - 1);
  const int XOR_REV_BITS_IN_BYTES = 7;  // only flip position in byte
  const int XOR_REV_BYTES = XOR_REV_BITS ^ XOR_REV_BITS_IN_BYTES;
  ASSERT_EQ(byteswap<T>((T)0), (T)0);
  ASSERT_EQ(byteswap<T>((T)-1), (T)-1);
  for (int i1 = 0; i1 < NBIT; i1++) {
    T mask1 = (T)1 << i1;
    T rbym1 = (T)1 << (i1 ^ XOR_REV_BYTES);
    for (int i2 = 0; i2 <= i1; i2++) {
      T mask2 = (T)1 << i2;
      T rbym2 = (T)1 << (i2 ^ XOR_REV_BYTES);
      T mask = mask1|mask2;
#define STUFF (IS_U?"u":"s") << NBIT << "@" << i1 << "," << i2
      ASSERT_EQ(byteswap<T>(mask), rbym1|rbym2) << STUFF;
      ASSERT_EQ((T)~byteswap<T>((T)~mask), rbym1|rbym2) << STUFF;
    }
  }
}

TEST_VM(utilities, byteswap) {
  test_byteswap<int64_t>();
  test_byteswap<uint64_t>();
  test_byteswap<int32_t>();
  test_byteswap<uint32_t>();
  test_byteswap<int16_t>();
  test_byteswap<uint16_t>();
  test_byteswap<int8_t>();
  test_byteswap<uint8_t>();
}

// Here is some object code to look at if we want to do a manual
// study.  One could find the build file named test_byteswap.o.cmdline
// and hand-edit the command line to produce assembly code in
// test_byteswap.s.
//
// Or, given the two empty "fence functions", one could do a
// quick scan like this:
//
// $ objdump -D $(find build/*release -name test_byteswap.o) \
//   | sed -n '/start_code_quality/,$p;/end_code_quality/q' \
//   | egrep -B10 bswap  # or grep -B20 cfi_endproc

void start_code_quality_byteswap() { }

int32_t code_quality_reverse_bytes_32(int32_t x) {
  return byteswap(x);
}

int64_t code_quality_reverse_bytes_64(int64_t x) {
  return byteswap(x);
}
