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
#include "utilities/ostream.hpp"

template <>
void WorkerDataArray<double>::WDAPrinter::summary(outputStream* out, const char* title, double min, double avg, double max, double diff, double sum, bool print_sum) {
  out->print("%-25s Min: %4.1lf, Avg: %4.1lf, Max: %4.1lf, Diff: %4.1lf", title, min * MILLIUNITS, avg * MILLIUNITS, max * MILLIUNITS, diff* MILLIUNITS);
  if (print_sum) {
    out->print_cr(", Sum: %4.1lf", sum * MILLIUNITS);
  } else {
    out->cr();
  }
}

template <>
void WorkerDataArray<size_t>::WDAPrinter::summary(outputStream* out, const char* title, size_t min, double avg, size_t max, size_t diff, size_t sum, bool print_sum) {
  out->print("%-25s Min: " SIZE_FORMAT ", Avg: %4.1lf, Max: " SIZE_FORMAT ", Diff: " SIZE_FORMAT, title, min, avg, max, diff);
  if (print_sum) {
    out->print_cr(", Sum: " SIZE_FORMAT, sum);
  } else {
    out->cr();
  }
}

template <>
void WorkerDataArray<double>::WDAPrinter::details(const WorkerDataArray<double>* phase, outputStream* out, uint active_threads) {
  out->print("%-25s", "");
  for (uint i = 0; i < active_threads; ++i) {
    out->print(" %4.1lf", phase->get(i) * 1000.0);
  }
  out->cr();
}

template <>
void WorkerDataArray<size_t>::WDAPrinter::details(const WorkerDataArray<size_t>* phase, outputStream* out, uint active_threads) {
  out->print("%-25s", "");
  for (uint i = 0; i < active_threads; ++i) {
    out->print("  " SIZE_FORMAT, phase->get(i));
  }
  out->cr();
}

#ifndef PRODUCT
void WorkerDataArray_test() {
  const uint length = 3;
  const char* title = "Test array";

  WorkerDataArray<size_t> array(length, title);
  assert(strncmp(array.title(), title, strlen(title)) == 0 , "Expected titles to match");

  const size_t expected[length] = {5, 3, 7};
  for (uint i = 0; i < length; i++) {
    array.set(i, expected[i]);
  }
  for (uint i = 0; i < length; i++) {
    assert(array.get(i) == expected[i], "Expected elements to match");
  }

  assert(array.sum(length) == (5 + 3 + 7), "Expected sums to match");
  assert(array.average(length) == 5.0, "Expected averages to match");

  for (uint i = 0; i < length; i++) {
    array.add(i, 1);
  }
  for (uint i = 0; i < length; i++) {
    assert(array.get(i) == expected[i] + 1, "Expected add to increment values");
  }
}
#endif
