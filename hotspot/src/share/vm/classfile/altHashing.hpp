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

#ifndef SHARE_VM_CLASSFILE_ALTHASHING_HPP
#define SHARE_VM_CLASSFILE_ALTHASHING_HPP

#include "prims/jni.h"
#include "classfile/symbolTable.hpp"

/**
 * Hashing utilities.
 *
 * Implementation of Murmur3 hashing.
 * This code was translated from src/share/classes/sun/misc/Hashing.java
 * code in the JDK.
 */

class AltHashing : AllStatic {

  // utility function copied from java/lang/Integer
  static juint Integer_rotateLeft(juint i, int distance) {
    return (i << distance) | (i >> (32-distance));
  }
  static juint murmur3_32(const int* data, int len);
  static juint murmur3_32(juint seed, const int* data, int len);

#ifndef PRODUCT
  // Hashing functions used for internal testing
  static juint murmur3_32(const jbyte* data, int len);
  static juint murmur3_32(const jchar* data, int len);
  static void testMurmur3_32_ByteArray();
  static void testEquivalentHashes();
#endif // PRODUCT

 public:
  static juint compute_seed();
  static juint murmur3_32(juint seed, const jbyte* data, int len);
  static juint murmur3_32(juint seed, const jchar* data, int len);
  NOT_PRODUCT(static void test_alt_hash();)
};
#endif // SHARE_VM_CLASSFILE_ALTHASHING_HPP
