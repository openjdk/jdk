/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "classfile/classPrinter.hpp"
#include "memory/resourceArea.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/javaThread.hpp"
#include "utilities/ostream.hpp"
#include "unittest.hpp"

using testing::ContainsRegex;
using testing::HasSubstr;

TEST_VM(ClassPrinter, print_classes) {
  JavaThread* THREAD = JavaThread::current();
  ThreadInVMfromNative invm(THREAD);
  ResourceMark rm;

  stringStream s1;
  ClassPrinter::print_classes("java/lang/Object", 0x03, &s1);
  const char* o1 = s1.freeze();

  ASSERT_THAT(o1, HasSubstr("class: java/lang/Object mirror:")) << "must find java/lang/Object";
  ASSERT_THAT(o1, HasSubstr("method wait : (J)V")) << "must find java/lang/Object::wait";
  ASSERT_THAT(o1, HasSubstr("method finalize : ()V\n   0 return")) << "must find java/lang/Object::finalize and disasm";

  // "." should also work as separator in class name
  stringStream s2;
  ClassPrinter::print_classes("java.lang.Object", 0x03, &s2);
  const char* o2 = s2.freeze();
  ASSERT_THAT(o2, HasSubstr("class: java/lang/Object mirror:")) << "must find java/lang/Object";

  // 0x20 is PRINT_CLASS_DETAILS
  stringStream s3;
  ClassPrinter::print_classes("java.lang.Integer", 0x20, &s3);
  const char* o3 = s3.freeze();
  ASSERT_THAT(o3, HasSubstr("class: java/lang/Integer mirror:")) << "must find java/lang/Integer";
  ASSERT_THAT(o3, HasSubstr("InstanceKlass: java.lang.Integer {0x")) << "must print InstanceKlass";
  ASSERT_THAT(o3, HasSubstr("Java mirror oop for java/lang/Integer:")) << "must print mirror oop";
#if GTEST_USES_POSIX_RE
  // Complex regex not available on Windows
  ASSERT_THAT(o3, ContainsRegex("public static final 'MIN_VALUE' 'I'.* -2147483648 [(]0x80000000[)]")) << "must print static fields";
#endif
}

TEST_VM(ClassPrinter, print_methods) {
  JavaThread* THREAD = JavaThread::current();
  ThreadInVMfromNative invm(THREAD);
  ResourceMark rm;

  stringStream s1;
  ClassPrinter::print_methods("*ang/Object*", "wait", 0x1, &s1);
  const char* o1 = s1.freeze();
  ASSERT_THAT(o1, HasSubstr("class: java/lang/Object mirror:")) << "must find java/lang/Object";
  ASSERT_THAT(o1, HasSubstr("method wait : (J)V")) << "must find java/lang/Object::wait(long)";
  ASSERT_THAT(o1, HasSubstr("method wait : ()V")) << "must find java/lang/Object::wait()";
  ASSERT_THAT(o1, Not(HasSubstr("method finalize : ()V"))) << "must not find java/lang/Object::finalize";

  stringStream s2;
  ClassPrinter::print_methods("j*ang/Object*", "wait:(*J*)V", 0x1, &s2);
  const char* o2 = s2.freeze();
  ASSERT_THAT(o2, HasSubstr("class: java/lang/Object mirror:")) << "must find java/lang/Object";
  ASSERT_THAT(o2, HasSubstr("method wait : (J)V")) << "must find java/lang/Object::wait(long)";
  ASSERT_THAT(o2, HasSubstr("method wait : (JI)V")) << "must find java/lang/Object::wait(long,int)";
  ASSERT_THAT(o2, Not(HasSubstr("method wait : ()V"))) << "must not find java/lang/Object::wait()";

  // 0x02 is PRINT_BYTECODE
  // 0x04 is PRINT_BYTECODE_ADDRESS
  // 0x40 is PRINT_METHOD_DETAILS
  stringStream s3;
  ClassPrinter::print_methods("java.lang.Object", "wait:()V", 0x46, &s3);
  const char* o3 = s3.freeze();
  ASSERT_THAT(o3, HasSubstr("method wait : ()V")) << "must find java/lang/Object::wait()";

#ifndef PRODUCT
  // PRINT_METHOD_DETAILS -- available only in debug builds
  ASSERT_THAT(o3, HasSubstr("{method}")) << "must print Method metadata";
#if GTEST_USES_POSIX_RE
  // Complex regex not available on Windows
  ASSERT_THAT(o3, ContainsRegex("method holder:.*'java/lang/Object'")) << "must print Method metadata details";
  ASSERT_THAT(o3, ContainsRegex("name: *'wait'")) << "must print Method metadata details";
#endif
#endif

#if GTEST_USES_POSIX_RE
  // Bytecodes: we should have at least one 'return' bytecide for Object.wait()
  // The print out should look like this:
  // 0x000000004adf73ad    5 return
  ASSERT_THAT(o3, ContainsRegex("0x[0-9a-f]+ +[0-9]+ +return")) << "must print return bytecode";
#endif

}
