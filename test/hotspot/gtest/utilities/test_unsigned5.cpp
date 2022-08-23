/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 */

#include "precompiled.hpp"
#include "memory/allocation.hpp"
#include "utilities/unsigned5.hpp"
#include "unittest.hpp"

// T.I.L.
// $ sh ./configure ... --with-gtest=<...>/googletest ...
// $ make exploded-test TEST=gtest:unsigned5

TEST_VM(unsigned5, max_encoded_in_length) {
  int maxlen = UNSIGNED5::MAX_LENGTH;
  EXPECT_EQ(maxlen, 5);
  for (int i = 0; i <= 190; i++) {
    u4 interesting = i;
    EXPECT_EQ(UNSIGNED5::encoded_length(interesting), 1);
    EXPECT_EQ(UNSIGNED5::encoded_length(~interesting), maxlen);
  }
  for (int len = 1; len <= maxlen; len++) {
    u4 interesting = UNSIGNED5::max_encoded_in_length(len);
    EXPECT_EQ(UNSIGNED5::encoded_length(interesting-1), len);
    EXPECT_EQ(UNSIGNED5::encoded_length(interesting), len);
    if (len < 5) {
      EXPECT_EQ(UNSIGNED5::encoded_length(interesting+1), len+1);
      EXPECT_EQ(UNSIGNED5::encoded_length(interesting*2), len+1);
    }
    const int offset = -123;
    const int good_limit = offset + len;
    const int bad_limit  = good_limit - 1;
    EXPECT_TRUE(UNSIGNED5::fits_in_limit(interesting, offset, good_limit));
    EXPECT_TRUE(!UNSIGNED5::fits_in_limit(interesting, offset, bad_limit));
  }
}

// Call FN on a nice list of "interesting" u4 values to encode/decode.
// For each length in [1..5], the maximum encodable value of that
// length is "interesting", as are one more and one less than that
// value.  For each nybble (aligned 4-bit field) of a u4, each
// possible value (in [0..15]) stored in that nybble is "interesting".
// Also "interesting" are some other values created by perturbing
// lower bits of that nybble-bearing number, by subtracting a power
// of -7 (up to -7^7).  That makes just over 1000 distinct numbers.
//
// Calls to this function are repeatable, so you can call it to pack
// an output array, and then call it again to read an input array
// verifying that the retrieved values match the stored ones.
template<typename FN>
inline int enumerate_cases(FN fn) {
  // boundary values around the maximum encoded in each byte-length
  for (int len = 1; len <= 5; len++) {
    u4 interesting = UNSIGNED5::max_encoded_in_length(len);
    int res = fn(interesting-1);
    if (res)  return res;
    res = fn(interesting);
    if (res)  return res;
    if (interesting < (u4)-1) {
      res = fn(interesting+1);
      if (res)  return res;
    }
  }
  // for each nybble, for each value in the nybble
  for (u4 npos = 0; npos < 32; npos += 4) {
    for (u4 nval = 0; nval <= 15; nval++) {
      u4 interesting = nval << npos;
      int res = fn(interesting);
      if (res)  return res;
      // mix in some crazy-looking values: powers of -7 to -7^7
      for (int pon7 = 1; pon7 < 1000000; pon7 *= -7) {
        u4 interesting2 = interesting - pon7; 
        res = fn(interesting2);
        if (res)  return res;
      }
    }
  }
  return 0;
}

TEST_VM(unsigned5, transcode_single) {
  const int limit = UNSIGNED5::MAX_LENGTH;
  u_char buffer[limit + 1];
  auto each_case = [&](u4 value) -> u4 {
    //printf("case %08X len=%d\n", value, UNSIGNED5::encoded_length(value));
    int offset = 0;
    UNSIGNED5::write_u4(value, buffer, offset, limit);
    int length = offset;
    EXPECT_TRUE(length <= UNSIGNED5::MAX_LENGTH);
    EXPECT_EQ(length, UNSIGNED5::encoded_length(value)) << "for value=" << value;
    buffer[length] = 0;
    offset = 0;
    u4 check = UNSIGNED5::read_u4(buffer, offset, limit);
    EXPECT_EQ(offset, length) << "for value=" << value;
    EXPECT_EQ(value, check);
    return 0;
  };
  auto z = enumerate_cases(each_case);
  EXPECT_TRUE(!z);
}

static int count_cases() {
  int case_count = 0;
  auto inc_case_count = [&](u4){ ++case_count; return 0; };
  enumerate_cases(inc_case_count);
  return case_count;
}

// try out a command from debug.cpp:
extern "C" intptr_t u5p(intptr_t addr, intptr_t limit, int count);

TEST_VM(unsigned5, transcode_multiple) {
  int case_count = count_cases();
  const int limit = 200;
  ASSERT_TRUE(limit < case_count*UNSIGNED5::MAX_LENGTH);
  u_char buffer[limit + 1];
  //printf("%d cases total\n", case_count);  //1166 cases total
  for (int sublimit = limit - 20; sublimit < limit; sublimit++) {
    int offset = 0;
    int count = 0;
    // write each number into an array
    auto write_case = [&](u4 value) -> u4 {
      if (!UNSIGNED5::fits_in_limit(value, offset, sublimit))
        return value|1;
      UNSIGNED5::write_u4(value, buffer, offset, sublimit);
      count++;
      return 0;
    };
    auto done = enumerate_cases(write_case);
    EXPECT_TRUE(done) << "must have hit the sublimit";
    EXPECT_TRUE(count < case_count);
    int length = offset;
    EXPECT_TRUE(length <= sublimit && length + UNSIGNED5::MAX_LENGTH > sublimit)
           << "length=" << length << " sublimit=" << sublimit;
    for (int i = length; i <= sublimit; i++) {
      buffer[i] = 0;
    }
    if (sublimit == limit-1) {
      u5p((intptr_t)&buffer[0], (intptr_t)&buffer[sublimit], case_count + 1);
    }
    //printf("encoded %d values in %d bytes: [[%s]]\n", count, length, buffer);
    // now read it all back
    offset = 0;
    int count2 = 0;
    auto read_back_case = [&](u4 value) -> u4 {
      int clen = UNSIGNED5::check_length(buffer, offset, sublimit);
      if (clen == 0)  return value|1;
      EXPECT_EQ(clen, UNSIGNED5::encoded_length(value));
      int begin = offset;
      u4 check = UNSIGNED5::read_u4(buffer, offset, sublimit);
      EXPECT_EQ(offset, begin + clen);
      EXPECT_EQ(value, check);
      count2++;
      return 0;
    };
    auto done2 = enumerate_cases(read_back_case);
    EXPECT_EQ(done, done2);
    EXPECT_EQ(count, count2);
    EXPECT_EQ(offset, length);
  }
}
