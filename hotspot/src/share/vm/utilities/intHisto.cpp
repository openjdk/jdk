/*
 * Copyright (c) 2001, 2007, Oracle and/or its affiliates. All rights reserved.
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

# include "incls/_precompiled.incl"
# include "incls/_intHisto.cpp.incl"

IntHistogram::IntHistogram(int est, int max) : _max(max), _tot(0) {
  assert(0 <= est && est <= max, "Preconditions");
  _elements = new (ResourceObj::C_HEAP) GrowableArray<int>(est, true);
  guarantee(_elements != NULL, "alloc failure");
}

void IntHistogram::add_entry(int outcome) {
  if (outcome > _max) outcome = _max;
  int new_count = _elements->at_grow(outcome) + 1;
  _elements->at_put(outcome, new_count);
  _tot++;
}

int IntHistogram::entries_for_outcome(int outcome) {
  return _elements->at_grow(outcome);
}

void IntHistogram::print_on(outputStream* st) const {
  double tot_d = (double)_tot;
  st->print_cr("Outcome     # of occurrences   %% of occurrences");
  st->print_cr("-----------------------------------------------");
  for (int i=0; i < _elements->length()-2; i++) {
    int cnt = _elements->at(i);
    if (cnt != 0) {
      st->print_cr("%7d        %10d         %8.4f",
                   i, cnt, (double)cnt/tot_d);
    }
  }
  // Does it have any max entries?
  if (_elements->length()-1 == _max) {
    int cnt = _elements->at(_max);
    st->print_cr(">= %4d        %10d         %8.4f",
                 _max, cnt, (double)cnt/tot_d);
  }
  st->print_cr("-----------------------------------------------");
  st->print_cr("    All        %10d         %8.4f", _tot, 1.0);
}
