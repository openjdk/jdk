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
 */

#include "memory/allocation.hpp"
#include "runtime/os.hpp"
#include "utilities/unsigned5.hpp"
#include "unittest.hpp"

TEST_VM(unsigned5, max_encoded_in_length) {
  int maxlen = UNSIGNED5::MAX_LENGTH;
  EXPECT_EQ(maxlen, 5);
  for (int i = 0; i <= 190; i++) {
    uint32_t interesting = i;
    EXPECT_EQ(UNSIGNED5::encoded_length(interesting), 1);
    EXPECT_EQ(UNSIGNED5::encoded_length(~interesting), maxlen);
  }
  for (int len = 1; len <= maxlen; len++) {
    uint32_t interesting = UNSIGNED5::max_encoded_in_length(len);
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

// Call FN on a nice list of "interesting" uint32_t values to encode/decode.
// For each length in [1..5], the maximum encodable value of that
// length is "interesting", as are one more and one less than that
// value.  For each nybble (aligned 4-bit field) of a uint32_t, each
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
    uint32_t interesting = UNSIGNED5::max_encoded_in_length(len);
    int res = fn(interesting-1);
    if (res)  return res;
    res = fn(interesting);
    if (res)  return res;
    if (interesting < (uint32_t)-1) {
      res = fn(interesting+1);
      if (res)  return res;
    }
  }
  // for each nybble, for each value in the nybble
  for (uint32_t npos = 0; npos < 32; npos += 4) {
    for (uint32_t nval = 0; nval <= 15; nval++) {
      uint32_t interesting = nval << npos;
      int res = fn(interesting);
      if (res)  return res;
      // mix in some crazy-looking values: powers of -7 to -7^7
      for (int pon7 = 1; pon7 < 1000000; pon7 *= -7) {
        uint32_t interesting2 = interesting - pon7;
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
  auto each_case = [&](uint32_t value) -> uint32_t {
    //printf("case %08X len=%d\n", value, UNSIGNED5::encoded_length(value));
    int offset = 0;
    UNSIGNED5::write_uint(value, buffer, offset, limit);
    int length = offset;
    EXPECT_TRUE(length <= UNSIGNED5::MAX_LENGTH);
    EXPECT_EQ(length, UNSIGNED5::encoded_length(value)) << "for value=" << value;
    buffer[length] = 0;
    offset = 0;
    uint32_t check = UNSIGNED5::read_uint(buffer, offset, limit);
    EXPECT_EQ(offset, length) << "for value=" << value;
    EXPECT_EQ(value, check);
    return 0;
  };
  auto z = enumerate_cases(each_case);
  EXPECT_TRUE(!z);
}

static int count_cases() {
  int case_count = 0;
  auto inc_case_count = [&](uint32_t){ ++case_count; return 0; };
  enumerate_cases(inc_case_count);
  return case_count;
}

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
    auto write_case = [&](uint32_t value) -> uint32_t {
      if (!UNSIGNED5::fits_in_limit(value, offset, sublimit))
        return value|1;
      UNSIGNED5::write_uint(value, buffer, offset, sublimit);
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
      UNSIGNED5::print_count(case_count + 1, &buffer[0], sublimit);
    }
    //printf("encoded %d values in %d bytes: [[%s]]\n", count, length, buffer);
    // now read it all back
    offset = 0;
    int count2 = 0;
    auto read_back_case = [&](uint32_t value) -> uint32_t {
      int clen = UNSIGNED5::check_length(buffer, offset, sublimit);
      if (clen == 0)  return value|1;
      EXPECT_EQ(clen, UNSIGNED5::encoded_length(value));
      int begin = offset;
      uint32_t check = UNSIGNED5::read_uint(buffer, offset, sublimit);
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

inline void init_ints(int len, int* ints) {
  for (int i = 0; i < len; i++) {
    ints[i] = (i * ((i&2) ? i : 1001)) ^ -(i & 1);
  }
}

struct MyReaderHelper {
  uint8_t operator()(char* a, int i) const { return a[i]; }
};
using MyReader = UNSIGNED5::Reader<char*, int, MyReaderHelper>;

TEST_VM(unsigned5, reader) {
  const int LEN = 100;
  int ints[LEN];
  init_ints(LEN, ints);
  int i;
  UNSIGNED5::Sizer<> szr;
  for (i = 0; i < LEN; i++) {
    szr.accept_uint(ints[i]);
  }
  //printf("count=%d, size=%d\n", szr.count(), szr.position());
  char buf[LEN * UNSIGNED5::MAX_LENGTH + 1];
  int buflen;
  {
    int pos = 0;
    for (int i = 0; i < LEN; i++) {
      UNSIGNED5::write_uint(ints[i], buf, pos, 0);
    }
    EXPECT_TRUE(pos+1 < (int)sizeof(buf)) << pos;
    buflen = pos;
    buf[buflen] = 0;
  }
  EXPECT_EQ(szr.position(), buflen);
  MyReader r1(buf);
  i = 0;
  while (r1.has_next()) {
    int x = r1.next_uint();
    int y = ints[i++];
    ASSERT_EQ(x, y) << i;
  }
  ASSERT_EQ(i, LEN);
  MyReader r2(buf, buflen / 2);
  i = 0;
  while (r2.has_next()) {
    int x = r2.next_uint();
    int y = ints[i++];
    ASSERT_EQ(x, y) << i;
  }
  ASSERT_TRUE(i < LEN);
  // copy from reader to writer
  UNSIGNED5::Reader<char*,int> r3(buf);
  int array_limit = 1;
  char* array = new char[array_limit + 1];
  auto array_grow = [&](int){
    array[array_limit] = 0;
    auto oal = array_limit;
    array_limit += 10;
    //printf("growing array from %d to %d\n", oal, array_limit);
    auto na = new char[array_limit + 1];
    strcpy(na, array);
    array = na;
  };
  UNSIGNED5::Writer<char*,int> w3(array, array_limit);
  while (r3.has_next()) {
    w3.accept_grow(r3.next_uint(), array_grow);
  }
  w3.end_byte();  // we always allocated one more than the limit!
  std::string buf_s(buf, buflen);
  std::string arr_s(array, strlen(array));
  ASSERT_EQ(buf_s, arr_s);

  // try printing:
  {
    char stbuf[1000];
    stringStream st(stbuf, sizeof(stbuf)-1);
    UNSIGNED5::Reader<char*,int> printer(buf);
    printer.print_on(&st, 4, "(", ")");
    std::string st_s(st.base(), st.size());
    char buf2[sizeof(stbuf)];
    os::snprintf_checked(buf2, sizeof(buf2), "(%d %d %d %d)", ints[0], ints[1], ints[2], ints[3]);
    std::string exp_s(buf2, strlen(buf2));
    ASSERT_EQ(exp_s, st_s);
  }
}

// Here is some object code to look at if we want to do a manual
// study.  One could find the build file named test_unsigned5.o.cmdline
// and hand-edit the command line to produce assembly code in
// test_unsigned5.s.
//
// Or, given the two empty "fence functions", one could do a
// quick scan like this:
//
// $ objdump -D $(find build/*release -name test_unsigned5.o) \
//   | sed -n /start_code_quality/,/end_code_quality/p \
//   | egrep -B10 bswap  # or grep -B20 cfi_endproc

void start_code_quality_unsigned5() { }

uint32_t code_quality_max_encoded_in_length(int i) {
  return UNSIGNED5::max_encoded_in_length(i);  // should compile like 5-switch
}

int code_quality_encoded_length(uint32_t x) {
  return UNSIGNED5::encoded_length(x);  // should compile to 4-way comparison
}

int code_quality_check_length(char* a) {
  return UNSIGNED5::check_length(a, 0);  // should compile with fast-path
}

int code_quality_read_int(char* a) {
  int i = 0;
  return UNSIGNED5::read_uint(a, i, 0);  // should compile with fast-path
}

int code_quality_int_reader(char* a) {
  MyReader r1(a);
  if (!r1.has_next())  return -1;
  return r1.next_uint();
}

int code_quality_int_sizer(int* a, int n) {
  UNSIGNED5::Sizer<> s;
  for (int i = 0; i < n; i++)  s.accept_uint(a[i]);
  return s.position();
}

void end_code_quality_unsigned5() { }
