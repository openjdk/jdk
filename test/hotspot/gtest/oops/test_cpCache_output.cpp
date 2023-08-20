/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/vmClasses.hpp"
#include "memory/resourceArea.hpp"
#include "oops/constantPool.hpp"
#include "oops/cpCache.hpp"
#include "oops/method.hpp"
#include "runtime/javaThread.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "utilities/ostream.hpp"
#include "unittest.hpp"

// Tests for ConstantPoolCache::print_on() function
TEST_VM(ConstantPoolCache, print_on) {
  JavaThread* THREAD = JavaThread::current();
  ThreadInVMfromNative invm(THREAD);
  ResourceMark rm;
  stringStream ss;

  InstanceKlass* klass = vmClasses::System_klass();
  klass->constants()->cache()->print_on(&ss);

  const char* output = ss.freeze();
  // method entry test
  ASSERT_TRUE(strstr(output, "this") != NULL) << "must have \"this\"";
  ASSERT_TRUE(strstr(output, "bytecode 1:") != NULL) << "must have \"bytecode 1\"";
  ASSERT_TRUE(strstr(output, "bytecode 2:") != NULL) << "must have \"bytecode 2\"";
  ASSERT_TRUE(strstr(output, "cp index:") != NULL) << "must have constant pool index";
  ASSERT_TRUE(strstr(output, "F1:") != NULL) << "must have F1 value";
  ASSERT_TRUE(strstr(output, "F2:") != NULL) << "must have F2 value";
  ASSERT_TRUE(strstr(output, "method:") != NULL) << "must have a method";
  ASSERT_TRUE(strstr(output, "flag values:") != NULL) << "must have a flag";
  ASSERT_TRUE(strstr(output, "tos:") != NULL) << "must have result type";
  ASSERT_TRUE(strstr(output, "local signature:") != NULL) << "must have local signature flag";
  ASSERT_TRUE(strstr(output, "has appendix:") != NULL) << "must have appendix flag";
  ASSERT_TRUE(strstr(output, "forced virtual:") != NULL) << "must have forced virtual flag";
  ASSERT_TRUE(strstr(output, "final:") != NULL) << "must have final flag";
  ASSERT_TRUE(strstr(output, "virtual final:") != NULL) << "must have virtual final flag";
  ASSERT_TRUE(strstr(output, "resolution failed:") != NULL) << "must have resolution failed flag";
  ASSERT_TRUE(strstr(output, "num parameters:") != NULL) << "must have number of parameters";

  // field entry test
  ASSERT_TRUE(strstr(output, "Offset:") != NULL) << "must have field offset";
  ASSERT_TRUE(strstr(output, "Field Index:") != NULL) << "must have field index";
  ASSERT_TRUE(strstr(output, "CP Index:") != NULL) << "must have constant pool index";
  ASSERT_TRUE(strstr(output, "TOS:") != NULL) << "must have type";
  ASSERT_TRUE(strstr(output, "Is Final:") != NULL) << "must have final flag";
  ASSERT_TRUE(strstr(output, "Is Volatile:") != NULL) << "must have volatile flag";
  ASSERT_TRUE(strstr(output, "Put Bytecode:") != NULL) << "must have \"put code\"";
  ASSERT_TRUE(strstr(output, "Get Bytecode:") != NULL) << "must have \"get code\"";
}
