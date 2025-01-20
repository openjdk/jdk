/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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
  static const char* const expected_strings[] = {
    // Method entry tests:
    "Klass:", "Method:", "CP Index:", "Resolved References Index:", "Table Index:",
    "TOS:", "Number of Parameters:", "Is Virtual Final:", "Is Final", "Is Forced Virtual",
    "Has Appendix:", "Has Local Signature", "Bytecode 1:", "Bytecode 2:",

    // field entry test
    "Offset:", "Field Index:", "CP Index:", "TOS:", "Is Final:", "Is Volatile:",
    "Put Bytecode:", "Get Bytecode:",
    nullptr
  };

  for (int i = 0; expected_strings[i] != nullptr; i++) {
    ASSERT_THAT(output, testing::HasSubstr(expected_strings[i]));
  }

}
