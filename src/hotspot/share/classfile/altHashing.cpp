/*
 * Copyright (c) 2012, 2017, Oracle and/or its affiliates. All rights reserved.
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
#include "oops/oop.inline.hpp"
#include "runtime/thread.hpp"

// Get the hash code of the classes mirror if it exists, otherwise just
// return a random number, which is one of the possible hash code used for
// objects.  We don't want to call the synchronizer hash code to install
// this value because it may safepoint.
static intptr_t object_hash(Klass* k) {
  intptr_t hc = k->java_mirror()->mark()->hash();
  return hc != markOopDesc::no_hash ? hc : os::random();
}

// Seed value used for each alternative hash calculated.
juint AltHashing::compute_seed() {
  jlong nanos = os::javaTimeNanos();
  jlong now = os::javaTimeMillis();
  jint SEED_MATERIAL[8] = {
            (jint) object_hash(SystemDictionary::String_klass()),
            (jint) object_hash(SystemDictionary::System_klass()),
            (jint) os::random(),  // current thread isn't a java thread
            (jint) (((julong)nanos) >> 32),
            (jint) nanos,
            (jint) (((julong)now) >> 32),
            (jint) now,
            (jint) (os::javaTimeNanos() >> 2)
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
juint AltHashing::murmur3_32(juint seed, const jint* data, int len) {
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

juint AltHashing::murmur3_32(const jint* data, int len) {
  return murmur3_32(0, data, len);
}
