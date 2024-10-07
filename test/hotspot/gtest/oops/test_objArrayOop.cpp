/*
 * Copyright Amazon.com Inc. or its affiliates. All rights reserved.
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
#include "oops/objArrayOop.hpp"
#include "unittest.hpp"
#include "utilities/globalDefinitions.hpp"

TEST_VM(objArrayOop, osize) {
  static const struct {
    int objal; bool ccp; bool coops; int result;
  } x[] = {
//    ObjAligInB, UseCCP, UseCoops, object size in heap words
#ifdef _LP64
    { 8,          false,  false,    4 },  // 20 byte header, 8 byte oops
    { 8,          false,  true,     3 },  // 20 byte header, 4 byte oops
    { 8,          true,   false,    3 },  // 16 byte header, 8 byte oops
    { 8,          true,   true,     3 },  // 16 byte header, 4 byte oops
    { 16,         false,  false,    4 },  // 20 byte header, 8 byte oops, 16-byte align
    { 16,         false,  true,     4 },  // 20 byte header, 4 byte oops, 16-byte align
    { 16,         true,   false,    4 },  // 16 byte header, 8 byte oops, 16-byte align
    { 16,         true,   true,     4 },  // 16 byte header, 4 byte oops, 16-byte align
    { 256,        false,  false,    32 }, // 20 byte header, 8 byte oops, 256-byte align
    { 256,        false,  true,     32 }, // 20 byte header, 4 byte oops, 256-byte align
    { 256,        true,   false,    32 }, // 16 byte header, 8 byte oops, 256-byte align
    { 256,        true,   true,     32 }, // 16 byte header, 4 byte oops, 256-byte align
#else
    { 8,          false,  false,    4 }, // 12 byte header, 4 byte oops, wordsize 4
#endif
    { -1,         false,  false,   -1 }
  };
  for (int i = 0; x[i].result != -1; i++) {
    if (x[i].objal == (int)ObjectAlignmentInBytes && x[i].ccp == UseCompressedClassPointers && x[i].coops == UseCompressedOops) {
      EXPECT_EQ(objArrayOopDesc::object_size(1), (size_t)x[i].result);
    }
  }
}
