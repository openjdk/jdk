/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/g1/workerDataArray.inline.hpp"

#ifndef PRODUCT
void WorkerDataArray_test() {
  const uint length = 3;
  const char* title = "Test array";
  const bool print_sum = false;
  const int log_level = 3;
  const uint indent_level = 2;

  WorkerDataArray<size_t> array(length, title, print_sum, log_level, indent_level);
  assert(strncmp(array.title(), title, strlen(title)) == 0 , "Expected titles to match");
  assert(array.should_print_sum() == print_sum, "Expected should_print_sum to match print_sum");
  assert(array.log_level() == log_level, "Expected log levels to match");
  assert(array.indentation() == indent_level, "Expected indentation to match");

  const size_t expected[length] = {5, 3, 7};
  for (uint i = 0; i < length; i++) {
    array.set(i, expected[i]);
  }
  for (uint i = 0; i < length; i++) {
    assert(array.get(i) == expected[i], "Expected elements to match");
  }

  assert(array.sum(length) == (5 + 3 + 7), "Expected sums to match");
  assert(array.minimum(length) == 3, "Expected mininum to match");
  assert(array.maximum(length) == 7, "Expected maximum to match");
  assert(array.diff(length) == (7 - 3), "Expected diffs to match");
  assert(array.average(length) == 5, "Expected averages to match");

  for (uint i = 0; i < length; i++) {
    array.add(i, 1);
  }
  for (uint i = 0; i < length; i++) {
    assert(array.get(i) == expected[i] + 1, "Expected add to increment values");
  }
}
#endif
