/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "jni.h"
#include <stdint.h>
#include <string.h>

JNIEXPORT jint JNICALL
Java_NMTPrintMallocSiteOfCorruptedMemory_modifyHeaderCanary(JNIEnv *env, jclass cls, jlong addr) {
  *((jint*)(uintptr_t)addr - 1) = 0;
  return 0;
}

JNIEXPORT jint JNICALL
Java_NMTPrintMallocSiteOfCorruptedMemory_modifyFooterCanary(JNIEnv *env, jclass cls, jlong addr, jint size) {
  *((jbyte*)(uintptr_t)addr + size + 1) = 0;
  return 0;
}
JNIEXPORT jint JNICALL
Java_NMTPrintMallocSiteOfCorruptedMemory_modifyHeaderCanaryAndSiteMarker(JNIEnv *env, jclass cls, jlong addr) {
  jbyte* p = (jbyte*)(uintptr_t)addr - 16;
  memset(p, 0xFF , 16);
  return 0;
}

JNIEXPORT jint JNICALL
Java_NMTPrintMallocSiteOfCorruptedMemory_modifyFooterCanaryAndSiteMarker(JNIEnv *env, jclass cls, jlong addr, jint size) {
  jbyte* p = (jbyte*)(uintptr_t)addr - 16;
  memset(p, 0xFF , 16);
  *((jbyte*)(uintptr_t)addr + size + 1) = 0;
  return 0;
}
