/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
extern "C" {

#include "jni.h"
#include "libTestDwarfHelper.h"
#include <stdio.h>

int zero = 0;
int result = 0;

JNIEXPORT void JNICALL Java_compiler_debug_TestDwarf_crashNativeDivByZero(JNIEnv* env, jclass jclazz) {
  result = 34 / zero; // Crash
}

JNIEXPORT void JNICALL Java_compiler_debug_TestDwarf_crashNativeDereferenceNull(JNIEnv* env, jclass jclazz) {
  dereference_null();
}

struct Super {
  virtual void foo() { printf("Super"); }
  virtual ~Super() {}
};

struct Sub1 : public Super {
  virtual void foo() { printf("Sub1\n"); }
};

struct Sub2 : public Super {
  virtual void foo() { printf("Sub2\n"); }
};

struct Sub3 : public Super {
  virtual void foo() {
    printf("Sub3\n");
    result = 34 / zero; // Crash
  }
};

struct Sub4 : public Super {
  virtual void foo() { printf("Sub4\n"); }
};

JNIEXPORT void JNICALL Java_compiler_debug_TestDwarf_crashNativeMultipleMethods(JNIEnv* env, jclass jclazz, jint x) {
  Super* s;
  // Make sure that compiler cannot inline foo() as it is not statically known, which version of foo() is called.
  if (x == 1) {
    s = new Sub1();
  } else if (x == 2) {
    s = new Sub2();
  } else if (x == 3) {
    s = new Sub3();
  } else {
    s = new Sub4();
  }
  s->foo();
  delete s;
}
}
