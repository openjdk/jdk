/*
 * Copyright (c) 2001, 2003, Oracle and/or its affiliates. All rights reserved.
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
# include "incls/_specialized_oop_closures.cpp.incl"

// For keeping stats on effectiveness.
#ifndef PRODUCT
#if ENABLE_SPECIALIZATION_STATS

int SpecializationStats::_numCallsAll;

int SpecializationStats::_numCallsTotal[NUM_Kinds];
int SpecializationStats::_numCalls_nv[NUM_Kinds];

int SpecializationStats::_numDoOopCallsTotal[NUM_Kinds];
int SpecializationStats::_numDoOopCalls_nv[NUM_Kinds];

void SpecializationStats::clear() {
  _numCallsAll = 0;
  for (int k = ik; k < NUM_Kinds; k++) {
    _numCallsTotal[k] = 0;
    _numCalls_nv[k] = 0;

    _numDoOopCallsTotal[k] = 0;
    _numDoOopCalls_nv[k] = 0;
  }
}

void SpecializationStats::print() {
  const char* header_format = "    %20s %10s %11s %10s";
  const char* line_format   = "    %20s %10d %11d %9.2f%%";
  int all_numCallsTotal =
    _numCallsTotal[ik] + _numCallsTotal[irk] + _numCallsTotal[oa];
  int all_numCalls_nv =
    _numCalls_nv[ik] + _numCalls_nv[irk] + _numCalls_nv[oa];
  gclog_or_tty->print_cr("\nOf %d oop_oop_iterate calls %d (%6.3f%%) are in (ik, irk, oa).",
                _numCallsAll, all_numCallsTotal,
                100.0 * (float)all_numCallsTotal / (float)_numCallsAll);
  // irk calls are double-counted.
  int real_ik_numCallsTotal = _numCallsTotal[ik] - _numCallsTotal[irk];
  int real_ik_numCalls_nv   = _numCalls_nv[ik]   - _numCalls_nv[irk];
  gclog_or_tty->print_cr("");
  gclog_or_tty->print_cr(header_format, "oop_oop_iterate:", "calls", "non-virtual", "pct");
  gclog_or_tty->print_cr(header_format,
                "----------",
                "----------",
                "-----------",
                "----------");
  gclog_or_tty->print_cr(line_format, "all",
                all_numCallsTotal,
                all_numCalls_nv,
                100.0 * (float)all_numCalls_nv / (float)all_numCallsTotal);
  gclog_or_tty->print_cr(line_format, "ik",
                real_ik_numCallsTotal, real_ik_numCalls_nv,
                100.0 * (float)real_ik_numCalls_nv /
                (float)real_ik_numCallsTotal);
  gclog_or_tty->print_cr(line_format, "irk",
                _numCallsTotal[irk], _numCalls_nv[irk],
                100.0 * (float)_numCalls_nv[irk] / (float)_numCallsTotal[irk]);
  gclog_or_tty->print_cr(line_format, "oa",
                _numCallsTotal[oa], _numCalls_nv[oa],
                100.0 * (float)_numCalls_nv[oa] / (float)_numCallsTotal[oa]);


  gclog_or_tty->print_cr("");
  gclog_or_tty->print_cr(header_format, "do_oop:", "calls", "non-virtual", "pct");
  gclog_or_tty->print_cr(header_format,
                "----------",
                "----------",
                "-----------",
                "----------");
  int all_numDoOopCallsTotal =
    _numDoOopCallsTotal[ik] + _numDoOopCallsTotal[irk] + _numDoOopCallsTotal[oa];
  int all_numDoOopCalls_nv =
    _numDoOopCalls_nv[ik] + _numDoOopCalls_nv[irk] + _numDoOopCalls_nv[oa];
  gclog_or_tty->print_cr(line_format, "all",
                all_numDoOopCallsTotal, all_numDoOopCalls_nv,
                100.0 * (float)all_numDoOopCalls_nv /
                (float)all_numDoOopCallsTotal);
  const char* kind_names[] = { "ik", "irk", "oa" };
  for (int k = ik; k < NUM_Kinds; k++) {
    gclog_or_tty->print_cr(line_format, kind_names[k],
                  _numDoOopCallsTotal[k], _numDoOopCalls_nv[k],
                  (_numDoOopCallsTotal[k] > 0 ?
                   100.0 * (float)_numDoOopCalls_nv[k] /
                   (float)_numDoOopCallsTotal[k]
                   : 0.0));
  }
}

#endif  // ENABLE_SPECIALIZATION_STATS
#endif  // !PRODUCT
