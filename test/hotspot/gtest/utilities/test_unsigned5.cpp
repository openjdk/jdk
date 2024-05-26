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
#include "runtime/os.hpp"
#include "utilities/unsigned5.hpp"
#include "unittest.hpp"

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
