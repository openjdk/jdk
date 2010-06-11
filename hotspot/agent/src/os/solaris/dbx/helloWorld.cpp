/*
 * Copyright (c) 2000, Oracle and/or its affiliates. All rights reserved.
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

#include <stdio.h>
#include <inttypes.h>

extern "C" {
  const char* helloWorldString = "Hello, world!";
  // Do not change these values without changing TestDebugger.java as well
  // FIXME: should make these jbyte, jshort, etc...
  volatile int8_t  testByte     = 132;
  volatile int16_t testShort    = 27890;
  volatile int32_t testInt      = 1020304050;
  volatile int64_t testLong     = 102030405060708090LL;
  volatile float   testFloat    = 35.4F;
  volatile double  testDouble   = 1.23456789;

  volatile int helloWorldTrigger = 0;
}

int
main(int, char**) {
  while (1) {
    while (helloWorldTrigger == 0) {
    }

    fprintf(stderr, "%s\n", helloWorldString);
    fprintf(stderr, "testByte=%d\n", testByte);
    fprintf(stderr, "testShort=%d\n", testShort);
    fprintf(stderr, "testInt=%d\n", testInt);
    fprintf(stderr, "testLong=%d\n", testLong);
    fprintf(stderr, "testFloat=%d\n", testFloat);
    fprintf(stderr, "testDouble=%d\n", testDouble);

    while (helloWorldTrigger != 0) {
    }
  }
}
