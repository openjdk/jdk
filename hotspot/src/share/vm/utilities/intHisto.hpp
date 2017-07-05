/*
 * Copyright (c) 2001, 2012, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_UTILITIES_INTHISTO_HPP
#define SHARE_VM_UTILITIES_INTHISTO_HPP

#include "memory/allocation.hpp"
#include "utilities/growableArray.hpp"

// This class implements a simple histogram.

// A histogram summarizes a series of "measurements", each of which is
// assumed (required in this implementation) to have an outcome that is a
// non-negative integer.  The histogram efficiently maps measurement outcomes
// to the number of measurements had that outcome.

// To print the results, invoke print() on your Histogram*.

// Note: there is already an existing "Histogram" class, in file
// histogram.{hpp,cpp}, but to my mind that's not a histogram, it's a table
// mapping strings to counts.  To be a histogram (IMHO) it needs to map
// numbers (in fact, integers) to number of occurrences of that number.

// ysr: (i am not sure i agree with the above note.) i suspect we want to have a
// histogram template that will map an arbitrary type (with a defined order
// relation) to a count.


class IntHistogram : public CHeapObj<mtInternal> {
 protected:
  int _max;
  int _tot;
  GrowableArray<int>* _elements;

public:
  // Create a new, empty table.  "est" is an estimate of the maximum outcome
  // that will be added, and "max" is an outcome such that all outcomes at
  // least that large will be bundled with it.
  IntHistogram(int est, int max);
  // Add a measurement with the given outcome to the sequence.
  void add_entry(int outcome);
  // Return the number of entries recorded so far with the given outcome.
  int  entries_for_outcome(int outcome);
  // Return the total number of entries recorded so far.
  int  total_entries() { return _tot; }
  // Return the number of entries recorded so far with the given outcome as
  // a fraction of the total number recorded so far.
  double fraction_for_outcome(int outcome) {
    return
      (double)entries_for_outcome(outcome)/
      (double)total_entries();
  }
  // Print the histogram on the given output stream.
  void print_on(outputStream* st) const;
};

#endif // SHARE_VM_UTILITIES_INTHISTO_HPP
