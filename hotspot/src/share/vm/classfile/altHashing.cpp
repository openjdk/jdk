/*
 * Copyright (c) 2012, 2014, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "classfile/altHashing.hpp"
#include "classfile/symbolTable.hpp"
#include "classfile/systemDictionary.hpp"
#include "oops/markOop.hpp"
#include "runtime/thread.hpp"

// Get the hash code of the classes mirror if it exists, otherwise just
// return a random number, which is one of the possible hash code used for
// objects.  We don't want to call the synchronizer hash code to install
// this value because it may safepoint.
intptr_t object_hash(Klass* k) {
  intptr_t hc = k->java_mirror()->mark()->hash();
  return hc != markOopDesc::no_hash ? hc : os::random();
}

// Seed value used for each alternative hash calculated.
juint AltHashing::compute_seed() {
  jlong nanos = os::javaTimeNanos();
  jlong now = os::javaTimeMillis();
  int SEED_MATERIAL[8] = {
            (int) object_hash(SystemDictionary::String_klass()),
            (int) object_hash(SystemDictionary::System_klass()),
            (int) os::random(),  // current thread isn't a java thread
            (int) (((julong)nanos) >> 32),
            (int) nanos,
            (int) (((julong)now) >> 32),
            (int) now,
            (int) (os::javaTimeNanos() >> 2)
  };

  return murmur3_32(SEED_MATERIAL, 8);
}


// Murmur3 hashing for Symbol
juint AltHashing::murmur3_32(juint seed, const jbyte* data, int len) {
  juint h1 = seed;
  int count = len;
  int offset = 0;

  // body
  while (count >= 4) {
    juint k1 = (data[offset] & 0x0FF)
        | (data[offset + 1] & 0x0FF) << 8
        | (data[offset + 2] & 0x0FF) << 16
        | data[offset + 3] << 24;

    count -= 4;
    offset += 4;

    k1 *= 0xcc9e2d51;
    k1 = Integer_rotateLeft(k1, 15);
    k1 *= 0x1b873593;

    h1 ^= k1;
    h1 = Integer_rotateLeft(h1, 13);
    h1 = h1 * 5 + 0xe6546b64;
  }

  // tail

  if (count > 0) {
    juint k1 = 0;

    switch (count) {
      case 3:
        k1 ^= (data[offset + 2] & 0xff) << 16;
      // fall through
      case 2:
        k1 ^= (data[offset + 1] & 0xff) << 8;
      // fall through
      case 1:
        k1 ^= (data[offset] & 0xff);
      // fall through
      default:
        k1 *= 0xcc9e2d51;
        k1 = Integer_rotateLeft(k1, 15);
        k1 *= 0x1b873593;
        h1 ^= k1;
    }
  }

  // finalization
  h1 ^= len;

  // finalization mix force all bits of a hash block to avalanche
  h1 ^= h1 >> 16;
  h1 *= 0x85ebca6b;
  h1 ^= h1 >> 13;
  h1 *= 0xc2b2ae35;
  h1 ^= h1 >> 16;

  return h1;
}

// Murmur3 hashing for Strings
juint AltHashing::murmur3_32(juint seed, const jchar* data, int len) {
  juint h1 = seed;

  int off = 0;
  int count = len;

  // body
  while (count >= 2) {
    jchar d1 = data[off++] & 0xFFFF;
    jchar d2 = data[off++];
    juint k1 = (d1 | d2 << 16);

    count -= 2;

    k1 *= 0xcc9e2d51;
    k1 = Integer_rotateLeft(k1, 15);
    k1 *= 0x1b873593;

    h1 ^= k1;
    h1 = Integer_rotateLeft(h1, 13);
    h1 = h1 * 5 + 0xe6546b64;
  }

  // tail

  if (count > 0) {
    juint k1 = (juint)data[off];

    k1 *= 0xcc9e2d51;
    k1 = Integer_rotateLeft(k1, 15);
    k1 *= 0x1b873593;
    h1 ^= k1;
  }

  // finalization
  h1 ^= len * 2; // (Character.SIZE / Byte.SIZE);

  // finalization mix force all bits of a hash block to avalanche
  h1 ^= h1 >> 16;
  h1 *= 0x85ebca6b;
  h1 ^= h1 >> 13;
  h1 *= 0xc2b2ae35;
  h1 ^= h1 >> 16;

  return h1;
}

// Hash used for the seed.
juint AltHashing::murmur3_32(juint seed, const int* data, int len) {
  juint h1 = seed;

  int off = 0;
  int end = len;

  // body
  while (off < end) {
    juint k1 = (juint)data[off++];

    k1 *= 0xcc9e2d51;
    k1 = Integer_rotateLeft(k1, 15);
    k1 *= 0x1b873593;

    h1 ^= k1;
    h1 = Integer_rotateLeft(h1, 13);
    h1 = h1 * 5 + 0xe6546b64;
  }

  // tail (always empty, as body is always 32-bit chunks)

  // finalization

  h1 ^= len * 4; // (Integer.SIZE / Byte.SIZE);

  // finalization mix force all bits of a hash block to avalanche
  h1 ^= h1 >> 16;
  h1 *= 0x85ebca6b;
  h1 ^= h1 >> 13;
  h1 *= 0xc2b2ae35;
  h1 ^= h1 >> 16;

  return h1;
}

juint AltHashing::murmur3_32(const int* data, int len) {
  return murmur3_32(0, data, len);
}

#ifndef PRODUCT
// Overloaded versions for internal test.
juint AltHashing::murmur3_32(const jbyte* data, int len) {
  return murmur3_32(0, data, len);
}

juint AltHashing::murmur3_32(const jchar* data, int len) {
  return murmur3_32(0, data, len);
}

// Internal test for alternate hashing.  Translated from JDK version
// test/sun/misc/Hashing.java
static const jbyte ONE_BYTE[] = { (jbyte) 0x80};
static const jbyte TWO_BYTE[] = { (jbyte) 0x80, (jbyte) 0x81};
static const jchar ONE_CHAR[] = { (jchar) 0x8180};
static const jbyte THREE_BYTE[] = { (jbyte) 0x80, (jbyte) 0x81, (jbyte) 0x82};
static const jbyte FOUR_BYTE[] = { (jbyte) 0x80, (jbyte) 0x81, (jbyte) 0x82, (jbyte) 0x83};
static const jchar TWO_CHAR[] = { (jchar) 0x8180, (jchar) 0x8382};
static const jint ONE_INT[] = { (jint)0x83828180};
static const jbyte SIX_BYTE[] = { (jbyte) 0x80, (jbyte) 0x81, (jbyte) 0x82, (jbyte) 0x83, (jbyte) 0x84, (jbyte) 0x85};
static const jchar THREE_CHAR[] = { (jchar) 0x8180, (jchar) 0x8382, (jchar) 0x8584};
static const jbyte EIGHT_BYTE[] = {
  (jbyte) 0x80, (jbyte) 0x81, (jbyte) 0x82,
  (jbyte) 0x83, (jbyte) 0x84, (jbyte) 0x85,
  (jbyte) 0x86, (jbyte) 0x87};
static const jchar FOUR_CHAR[] = {
  (jchar) 0x8180, (jchar) 0x8382,
  (jchar) 0x8584, (jchar) 0x8786};

static const jint TWO_INT[] = { (jint)0x83828180, (jint)0x87868584};

static const juint MURMUR3_32_X86_CHECK_VALUE = 0xB0F57EE3;

void AltHashing::testMurmur3_32_ByteArray() {
  // printf("testMurmur3_32_ByteArray\n");

  jbyte vector[256];
  jbyte hashes[4 * 256];

  for (int i = 0; i < 256; i++) {
    vector[i] = (jbyte) i;
  }

  // Hash subranges {}, {0}, {0,1}, {0,1,2}, ..., {0,...,255}
  for (int i = 0; i < 256; i++) {
    juint hash = murmur3_32(256 - i, vector, i);
    hashes[i * 4] = (jbyte) hash;
    hashes[i * 4 + 1] = (jbyte)(hash >> 8);
    hashes[i * 4 + 2] = (jbyte)(hash >> 16);
    hashes[i * 4 + 3] = (jbyte)(hash >> 24);
  }

  // hash to get const result.
  juint final_hash = murmur3_32(hashes, 4*256);

  assert (MURMUR3_32_X86_CHECK_VALUE == final_hash,
          "Calculated hash result not as expected. Expected %08X got %08X\n",
          MURMUR3_32_X86_CHECK_VALUE,
          final_hash);
}

void AltHashing::testEquivalentHashes() {
  juint jbytes, jchars, ints;

  // printf("testEquivalentHashes\n");

  jbytes = murmur3_32(TWO_BYTE, 2);
  jchars = murmur3_32(ONE_CHAR, 1);
  assert (jbytes == jchars,
          "Hashes did not match. b:%08x != c:%08x\n", jbytes, jchars);

  jbytes = murmur3_32(FOUR_BYTE, 4);
  jchars = murmur3_32(TWO_CHAR, 2);
  ints = murmur3_32(ONE_INT, 1);
  assert ((jbytes == jchars) && (jbytes == ints),
          "Hashes did not match. b:%08x != c:%08x != i:%08x\n", jbytes, jchars, ints);

  jbytes = murmur3_32(SIX_BYTE, 6);
  jchars = murmur3_32(THREE_CHAR, 3);
  assert (jbytes == jchars,
         "Hashes did not match. b:%08x != c:%08x\n", jbytes, jchars);

  jbytes = murmur3_32(EIGHT_BYTE, 8);
  jchars = murmur3_32(FOUR_CHAR, 4);
  ints = murmur3_32(TWO_INT, 2);
  assert ((jbytes == jchars) && (jbytes == ints),
          "Hashes did not match. b:%08x != c:%08x != i:%08x\n", jbytes, jchars, ints);
}

// Returns true if the alternate hashcode is correct
void AltHashing::test_alt_hash() {
  testMurmur3_32_ByteArray();
  testEquivalentHashes();
}

void AltHashing_test() {
  AltHashing::test_alt_hash();
}
#endif // PRODUCT
