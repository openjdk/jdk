/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/shared/workerDataArray.inline.hpp"
#include "utilities/ostream.hpp"

template <>
size_t WorkerDataArray<size_t>::uninitialized() {
  return SIZE_MAX;
}

template <>
double WorkerDataArray<double>::uninitialized() {
  return -1.0;
}

#define WDA_TIME_FORMAT "%4.2lf"

template <>
void WorkerDataArray<double>::WDAPrinter::summary(outputStream* out, double min, double avg, double max, double diff, double sum, bool print_sum) {
  out->print(" Min: " WDA_TIME_FORMAT ", Avg: " WDA_TIME_FORMAT ", Max: " WDA_TIME_FORMAT ", Diff: " WDA_TIME_FORMAT,
             min * MILLIUNITS, avg * MILLIUNITS, max * MILLIUNITS, diff* MILLIUNITS);
  if (print_sum) {
    out->print(", Sum: " WDA_TIME_FORMAT, sum * MILLIUNITS);
  }
}

template <>
void WorkerDataArray<size_t>::WDAPrinter::summary(outputStream* out, size_t min, double avg, size_t max, size_t diff, size_t sum, bool print_sum) {
  out->print(" Min: %zu, Avg: %4.1lf, Max: %zu, Diff: %zu", min, avg, max, diff);
  if (print_sum) {
    out->print(", Sum: %zu", sum);
  }
}

template <>
void WorkerDataArray<double>::WDAPrinter::details(const WorkerDataArray<double>* phase, outputStream* out) {
  out->print("%-30s", "");
  for (uint i = 0; i < phase->_length; ++i) {
    double value = phase->get(i);
    if (value != phase->uninitialized()) {
      out->print(" " WDA_TIME_FORMAT, phase->get(i) * 1000.0);
    } else {
      out->print(" -");
    }
  }
  out->cr();
}

template <>
void WorkerDataArray<size_t>::WDAPrinter::details(const WorkerDataArray<size_t>* phase, outputStream* out) {
  out->print("%-30s", "");
  for (uint i = 0; i < phase->_length; ++i) {
    size_t value = phase->get(i);
    if (value != phase->uninitialized()) {
      out->print("  %zu", phase->get(i));
    } else {
      out->print(" -");
    }
  }
  out->cr();
}
