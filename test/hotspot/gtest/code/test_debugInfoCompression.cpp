/*
 * Copyright (c) 2022, BELLSOFT. All rights reserved.
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

void check_int_encoding() {
  ResourceMark rm;
  DebugInfoWriteStream out(NULL, 100);
  u_char* buf = out.buffer();

  out.set_position(0);
  out.write_int(0);
  out.write_int(0);
  out.write_int(0);
  out.write_int(0);
  out.write_int(0);
  out.write_int(0);
  out.write_int(0);
  out.write_int(0);
  ASSERT_TRUE(out.position() == 1 && buf[0] == 0);

  out.set_position(0);
  out.write_int(1);
  ASSERT_TRUE(out.position() == 1 && buf[0] == 0x81);

  out.set_position(0);
  out.write_int(0xff);
  ASSERT_TRUE(out.position() == 2 && buf[0] == 0xff && buf[1] == 0x3);

  out.set_position(0);
  out.write_int(0xffff);
  ASSERT_TRUE(out.position() == 3 && buf[0] == 0xff && buf[1] == 0xff && buf[2] == 0x7);

  out.set_position(0);
  out.write_int(0xffffffff);
  ASSERT_TRUE(out.position() == 5 && ((buf[0] & buf[1] & buf[2] & buf[3]) == 0xff) && buf[4] == 0x1f);
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
  out.align();

  u_char* buf = out.buffer();
  CompressedSparseDataReadStream in(buf, 0);

  for (int i = 0; i < 1000*1000; i++) {
    ASSERT_TRUE(in.read_int() == i);
    ASSERT_TRUE(in.read_bool() == (jboolean)(bool)i);
    ASSERT_TRUE(in.read_byte() == (jbyte)i);
    ASSERT_TRUE(in.read_signed_int() == (jint)i);
    ASSERT_TRUE(in.read_double() == (jdouble)i);
    ASSERT_TRUE(in.read_long() == (jlong)i);
  }
}

void check_buffer_grow() {
  ResourceMark rm;
  DebugInfoWriteStream out(NULL, 100);
  out.set_position(99);
  out.write_int(0);
  out.align();
  out.write_int(1);
  out.write_int(2);
  u_char* buf = out.buffer();
  ASSERT_TRUE(out.position() == 102 && buf[99] == 0 && buf[100] == 0x81 && buf[101] == 0x82);
}

TEST_VM(DebugInfo, basic_test)
{
  check_int_encoding();
  check_read_write();
  check_buffer_grow();
}
