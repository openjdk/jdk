/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/altHashing.hpp"
#include "unittest.hpp"

// Internal test for alternate hashing.  Translated from JDK version
// test/sun/misc/Hashing.java
static const jbyte ONE_BYTE[] = {(jbyte) 0x80};
static const jbyte TWO_BYTE[] = {(jbyte) 0x80, (jbyte) 0x81};
static const jchar ONE_CHAR[] = {(jchar) 0x8180};
static const jbyte THREE_BYTE[] = {(jbyte) 0x80, (jbyte) 0x81, (jbyte) 0x82};
static const jbyte FOUR_BYTE[] = {(jbyte) 0x80, (jbyte) 0x81, (jbyte) 0x82, (jbyte) 0x83};
static const jchar TWO_CHAR[] = {(jchar) 0x8180, (jchar) 0x8382};
static const jint ONE_INT[] = {(jint) 0x83828180};
static const jbyte SIX_BYTE[] = {(jbyte) 0x80, (jbyte) 0x81, (jbyte) 0x82, (jbyte) 0x83, (jbyte) 0x84, (jbyte) 0x85};
static const jchar THREE_CHAR[] = {(jchar) 0x8180, (jchar) 0x8382, (jchar) 0x8584};
static const jbyte EIGHT_BYTE[] = {
  (jbyte) 0x80, (jbyte) 0x81, (jbyte) 0x82,
  (jbyte) 0x83, (jbyte) 0x84, (jbyte) 0x85,
  (jbyte) 0x86, (jbyte) 0x87
};
static const jchar FOUR_CHAR[] = {
  (jchar) 0x8180, (jchar) 0x8382,
  (jchar) 0x8584, (jchar) 0x8786
};

static const jint TWO_INT[] = {(jint) 0x83828180, (jint) 0x87868584};
static const juint MURMUR3_32_X86_CHECK_VALUE = 0xB0F57EE3;

class AltHashingTest : public ::testing::Test {
 public:

  static juint murmur3_32(const jint* data, int len) {
    return AltHashing::murmur3_32(data, len);
  }
};

TEST_F(AltHashingTest, murmur3_32_byte_array_test) {
  jbyte vector[256];
  jbyte hashes[4 * 256];

  for (int i = 0; i < 256; i++) {
    vector[i] = (jbyte) i;
  }

  // Hash subranges {}, {0}, {0,1}, {0,1,2}, ..., {0,...,255}
  for (int i = 0; i < 256; i++) {
    juint hash = AltHashing::murmur3_32(256 - i, vector, i);
    hashes[i * 4] = (jbyte) hash;
    hashes[i * 4 + 1] = (jbyte) (hash >> 8);
    hashes[i * 4 + 2] = (jbyte) (hash >> 16);
    hashes[i * 4 + 3] = (jbyte) (hash >> 24);
  }

  // hash to get const result.
  juint final_hash = AltHashing::murmur3_32(0, hashes, 4 * 256);

  ASSERT_EQ(MURMUR3_32_X86_CHECK_VALUE, final_hash)
          << "Calculated hash result not as expected.";
}

TEST_F(AltHashingTest, equivalent_hashes_test) {
  juint jbytes, jchars, ints;

  jbytes = AltHashing::murmur3_32(0, TWO_BYTE, 2);
  jchars = AltHashing::murmur3_32(0, ONE_CHAR, 1);
  ASSERT_EQ(jbytes, jchars) << "Hashes did not match.";

  jbytes = AltHashing::murmur3_32(0, FOUR_BYTE, 4);
  jchars = AltHashing::murmur3_32(0, TWO_CHAR, 2);
  ints = AltHashingTest::murmur3_32(ONE_INT, 1);

  ASSERT_EQ(jbytes, jchars) << "Hashes did not match.";
  ASSERT_EQ(jbytes, ints) << "Hashes did not match.";

  jbytes = AltHashing::murmur3_32(0, SIX_BYTE, 6);
  jchars = AltHashing::murmur3_32(0, THREE_CHAR, 3);
  ASSERT_EQ(jbytes, jchars) << "Hashes did not match.";

  jbytes = AltHashing::murmur3_32(0, EIGHT_BYTE, 8);
  jchars = AltHashing::murmur3_32(0, FOUR_CHAR, 4);
  ints = AltHashingTest::murmur3_32(TWO_INT, 2);

  ASSERT_EQ(jbytes, jchars) << "Hashes did not match.";
  ASSERT_EQ(jbytes, ints) << "Hashes did not match.";
}
