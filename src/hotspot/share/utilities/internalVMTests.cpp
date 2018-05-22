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
 *
 */

#include "precompiled.hpp"

#ifndef PRODUCT
#include <locale.h>

#include "utilities/internalVMTests.hpp"
#include "utilities/macros.hpp"
#include "utilities/ostream.hpp"

#define run_unit_test(unit_test_function_call)                \
  void unit_test_function_call();                             \
  run_test(#unit_test_function_call, unit_test_function_call);

void InternalVMTests::run_test(const char* name, void (*test)()) {
  tty->print_cr("Running test: %s", name);
  test();
}

void InternalVMTests::run() {
  tty->print_cr("Running internal VM tests");
  run_unit_test(TestReservedSpace_test);
  run_unit_test(TestReserveMemorySpecial_test);
  run_unit_test(TestVirtualSpace_test);
  run_unit_test(TestMetaspaceUtils_test);
  run_unit_test(GCTimer_test);
  run_unit_test(ObjectMonitor_test);
  // These tests require the "C" locale to correctly parse decimal values
  const char* orig_locale = setlocale(LC_NUMERIC, NULL);
  setlocale(LC_NUMERIC, "C");
  run_unit_test(DirectivesParser_test);
  setlocale(LC_NUMERIC, orig_locale);
  tty->print_cr("All internal VM tests passed");
}

#endif
