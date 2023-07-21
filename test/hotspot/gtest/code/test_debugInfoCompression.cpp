/*
 * Copyright (c) 2023, BELLSOFT. All rights reserved.
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
#include "memory/resourceArea.hpp"
#include "code/debugInfo.hpp"
#include "unittest.hpp"

class CompressedSparseDataWriteStreamTest {
public:
  void check_read_write(int variant) {
    ResourceMark rm;
    DebugInfoWriteStream out(NULL, 100);
    u_char* buf1 = out.buffer();

    for (int i = 0; i < 1024; i++) {
      for (int j = 0; j < i; j++) {
        out.write_int(0);
        // mix zeroes with position() calls or with other data
        if (variant == 1) {
          // position() call breaks zero sequence optimizaion
          out.position();
        } else if (variant == 2) {
          out.write_byte((jbyte)i);
        }
      }
      out.write_int(i);
    }

    // 523776 zero values is written
    // optionally: 523776 position() calls
    // optionally: 523776 bytes is written
    // 1024 int values is written
    int expected_position = (variant == 0) ? 6982 :
                            (variant == 1) ? 525633 : 1049409;
    ASSERT_TRUE(out.position() == expected_position);

    u_char* buf2 = out.buffer();
    // the initial buffer is small and must be replaced with a bigger one
    ASSERT_TRUE(buf1 != buf2);
    CompressedReadStream in(buf2, 0);

    for (int i = 0; i < 1024; i++) {
      for (int j = 0; j < i; j++) {
        ASSERT_TRUE(in.read_int() == 0);
        if (variant == 2) {
          ASSERT_TRUE(in.read_byte() == (jbyte)i);
        }
      }
      ASSERT_TRUE(in.read_int() == i);
    }
  }

  void check_read_write() {
    ResourceMark rm;
    DebugInfoWriteStream out(NULL, 100);

    for (int i = 0; i < 1000*1000; i++) {
      out.write_int(i);
      out.write_bool((bool)i);
      out.write_byte((jbyte)i);
      out.write_signed_int((jint)i);
      out.write_double((jdouble)i);
      out.write_long((jlong)i);
    }

    u_char* buf = out.buffer();
    CompressedReadStream in(buf, 0);

    for (int i = 0; i < 1000*1000; i++) {
      ASSERT_TRUE(in.read_int() == i);
      ASSERT_TRUE(in.read_bool() == (jboolean)(bool)i);
      ASSERT_TRUE(in.read_byte() == (jbyte)i);
      ASSERT_TRUE(in.read_signed_int() == (jint)i);
      ASSERT_TRUE(in.read_double() == (jdouble)i);
      ASSERT_TRUE(in.read_long() == (jlong)i);
    }
  }

  void check_int_encoding() {
    ResourceMark rm;
    DebugInfoWriteStream out(NULL, 100);
    const u_char* buf = out.buffer();

    out.write_int(0);
    out.write_int(0);
    out.write_int(0);
    out.write_int(0);
    out.write_int(0);
    out.write_int(0);
    out.write_int(0);
    out.write_int(0);
    ASSERT_TRUE(out.position() == 2 && buf[0] == 0 && buf[1] == 8);

    out.set_position(0);
    out.write_int(1);
    ASSERT_TRUE(out.position() == 1 && buf[0] == 0x2);

    out.set_position(0);
    out.write_int(0xff);
    ASSERT_TRUE(out.position() == 2 && buf[0] == 0xC0 && buf[1] == 0x2);

    out.set_position(0);
    out.write_int(0xffff);
    ASSERT_TRUE(out.position() == 3 && buf[0] == 0xC0 && buf[1] == 0xfe && buf[2] == 0xD);

    out.set_position(0);
    out.write_int(0xffffffff);
    ASSERT_TRUE(out.position() == 5 && buf[0] == 0xC0 && buf[1] == 0xfe && buf[2] == 0xFD && buf[3] == 0xFD);
  }

  void check_buffer_grow() {
    ResourceMark rm;
    DebugInfoWriteStream out(NULL, 100);
    for (int i = 0; i < 99; i++) { out.write_int(1); }
    out.write_int(0);
    out.write_int(1);
    out.write_int(2);
    const u_char* buf = out.buffer();
    ASSERT_TRUE(out.position() == 102 && buf[99] == 1 && buf[100] == 2 && buf[101] == 3);
  }
};

TEST_VM(DebugInfo, basic_test)
{
  CompressedSparseDataWriteStreamTest test;
  test.check_read_write(0);
  test.check_read_write(1);
  test.check_read_write(2);
  test.check_read_write();
  test.check_int_encoding();
  test.check_buffer_grow();
}
