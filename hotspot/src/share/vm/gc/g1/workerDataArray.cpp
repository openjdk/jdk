/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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
size_t WorkerDataArray<size_t>::uninitialized() {
  return (size_t)-1;
}

template <>
double WorkerDataArray<double>::uninitialized() {
  return -1.0;
}

template <>
void WorkerDataArray<double>::WDAPrinter::summary(outputStream* out, double min, double avg, double max, double diff, double sum, bool print_sum) {
  out->print(" Min: %4.1lf, Avg: %4.1lf, Max: %4.1lf, Diff: %4.1lf", min * MILLIUNITS, avg * MILLIUNITS, max * MILLIUNITS, diff* MILLIUNITS);
  if (print_sum) {
    out->print(", Sum: %4.1lf", sum * MILLIUNITS);
  }
}

template <>
void WorkerDataArray<size_t>::WDAPrinter::summary(outputStream* out, size_t min, double avg, size_t max, size_t diff, size_t sum, bool print_sum) {
  out->print(" Min: " SIZE_FORMAT ", Avg: %4.1lf, Max: " SIZE_FORMAT ", Diff: " SIZE_FORMAT, min, avg, max, diff);
  if (print_sum) {
    out->print(", Sum: " SIZE_FORMAT, sum);
  }
}

template <>
void WorkerDataArray<double>::WDAPrinter::details(const WorkerDataArray<double>* phase, outputStream* out) {
  out->print("%-25s", "");
  for (uint i = 0; i < phase->_length; ++i) {
    double value = phase->get(i);
    if (value != phase->uninitialized()) {
      out->print(" %4.1lf", phase->get(i) * 1000.0);
    } else {
      out->print(" -");
    }
  }
  out->cr();
}

template <>
void WorkerDataArray<size_t>::WDAPrinter::details(const WorkerDataArray<size_t>* phase, outputStream* out) {
  out->print("%-25s", "");
  for (uint i = 0; i < phase->_length; ++i) {
    size_t value = phase->get(i);
    if (value != phase->uninitialized()) {
      out->print("  " SIZE_FORMAT, phase->get(i));
    } else {
      out->print(" -");
    }
  }
  out->cr();
}

#ifndef PRODUCT

#include "memory/resourceArea.hpp"

void WorkerDataArray_test_verify_string(const char* expected_string, const char* actual_string) {
  const size_t expected_len = strlen(expected_string);

  assert(expected_len == strlen(actual_string),
      "Wrong string length, expected " SIZE_FORMAT " but got " SIZE_FORMAT "(Expected '%s' but got: '%s')",
      expected_len, strlen(actual_string), expected_string, actual_string);

  // Can't use strncmp here because floating point values use different decimal points for different locales.
  // Allow strings to differ in "." vs. "," only. This should still catch most errors.
  for (size_t i = 0; i < expected_len; i++) {
    char e = expected_string[i];
    char a = actual_string[i];
    if (e != a) {
      if ((e == '.' || e == ',') && (a == '.' || a == ',')) {
        // Most likely just a difference in locale
      } else {
        assert(false, "Expected '%s' but got: '%s'", expected_string, actual_string);
      }
    }
  }
}

void WorkerDataArray_test_verify_array(WorkerDataArray<size_t>& array, size_t expected_sum, double expected_avg, const char* expected_summary, const char* exected_details) {
  const double epsilon = 0.0001;
  assert(array.sum() == expected_sum, "Wrong sum, expected: " SIZE_FORMAT " but got: " SIZE_FORMAT, expected_sum, array.sum());
  assert(fabs(array.average() - expected_avg) < epsilon, "Wrong average, expected: %f but got: %f", expected_avg, array.average());

  ResourceMark rm;
  stringStream out;
  array.print_summary_on(&out);
  WorkerDataArray_test_verify_string(expected_summary, out.as_string());
  out.reset();
  array.print_details_on(&out);
  WorkerDataArray_test_verify_string(exected_details, out.as_string());
}

void WorkerDataArray_test_verify_array(WorkerDataArray<double>& array, double expected_sum, double expected_avg, const char* expected_summary, const char* exected_details) {
  const double epsilon = 0.0001;
  assert(fabs(array.sum() - expected_sum) < epsilon, "Wrong sum, expected: %f but got: %f", expected_sum, array.sum());
  assert(fabs(array.average() - expected_avg) < epsilon, "Wrong average, expected: %f but got: %f", expected_avg, array.average());

  ResourceMark rm;
  stringStream out;
  array.print_summary_on(&out);
  WorkerDataArray_test_verify_string(expected_summary, out.as_string());
  out.reset();
  array.print_details_on(&out);
  WorkerDataArray_test_verify_string(exected_details, out.as_string());
}

void WorkerDataArray_test_basic() {
  WorkerDataArray<size_t> array(3, "Test array");
  array.set(0, 5);
  array.set(1, 3);
  array.set(2, 7);

  WorkerDataArray_test_verify_array(array, 15, 5.0,
      "Test array                Min: 3, Avg:  5.0, Max: 7, Diff: 4, Sum: 15, Workers: 3\n",
      "                           5  3  7\n" );
}

void WorkerDataArray_test_add() {
  WorkerDataArray<size_t> array(3, "Test array");
  array.set(0, 5);
  array.set(1, 3);
  array.set(2, 7);

  for (uint i = 0; i < 3; i++) {
    array.add(i, 1);
  }

  WorkerDataArray_test_verify_array(array, 18, 6.0,
      "Test array                Min: 4, Avg:  6.0, Max: 8, Diff: 4, Sum: 18, Workers: 3\n",
      "                           6  4  8\n" );
}

void WorkerDataArray_test_with_uninitialized() {
  WorkerDataArray<size_t> array(3, "Test array");
  array.set(0, 5);
  array.set(1, WorkerDataArray<size_t>::uninitialized());
  array.set(2, 7);

  WorkerDataArray_test_verify_array(array, 12, 6,
      "Test array                Min: 5, Avg:  6.0, Max: 7, Diff: 2, Sum: 12, Workers: 2\n",
      "                           5 -  7\n" );
}

void WorkerDataArray_test_uninitialized() {
  WorkerDataArray<size_t> array(3, "Test array");
  array.set(0, WorkerDataArray<size_t>::uninitialized());
  array.set(1, WorkerDataArray<size_t>::uninitialized());
  array.set(2, WorkerDataArray<size_t>::uninitialized());

  WorkerDataArray_test_verify_array(array, 0, 0.0,
      "Test array                skipped\n",
      "                          - - -\n" );
}

void WorkerDataArray_test_double_with_uninitialized() {
  WorkerDataArray<double> array(3, "Test array");
  array.set(0, 5.1 / MILLIUNITS);
  array.set(1, WorkerDataArray<double>::uninitialized());
  array.set(2, 7.2 / MILLIUNITS);

  WorkerDataArray_test_verify_array(array, 12.3 / MILLIUNITS, 6.15 / MILLIUNITS,
      "Test array                Min:  5.1, Avg:  6.1, Max:  7.2, Diff:  2.1, Sum: 12.3, Workers: 2\n",
      "                           5.1 -  7.2\n" );
}

void WorkerDataArray_test() {
  WorkerDataArray_test_basic();
  WorkerDataArray_test_add();
  WorkerDataArray_test_with_uninitialized();
  WorkerDataArray_test_uninitialized();
  WorkerDataArray_test_double_with_uninitialized();
}

#endif
