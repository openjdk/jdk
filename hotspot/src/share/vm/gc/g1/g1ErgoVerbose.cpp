/*
 * Copyright (c) 2011, 2015, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/g1/g1ErgoVerbose.hpp"
#include "utilities/ostream.hpp"

ErgoLevel G1ErgoVerbose::_level;
bool G1ErgoVerbose::_enabled[ErgoHeuristicNum];

void G1ErgoVerbose::initialize() {
  set_level(ErgoLow);
  set_enabled(false);
}

void G1ErgoVerbose::set_level(ErgoLevel level) {
  _level = level;
}

void G1ErgoVerbose::set_enabled(ErgoHeuristic n, bool enabled) {
  assert(0 <= n && n < ErgoHeuristicNum, "pre-condition");
  _enabled[n] = enabled;
}

void G1ErgoVerbose::set_enabled(bool enabled) {
  for (int n = 0; n < ErgoHeuristicNum; n += 1) {
    set_enabled((ErgoHeuristic) n, enabled);
  }
}

const char* G1ErgoVerbose::to_string(int tag) {
  ErgoHeuristic n = extract_heuristic(tag);
  switch (n) {
  case ErgoHeapSizing:        return "Heap Sizing";
  case ErgoCSetConstruction:  return "CSet Construction";
  case ErgoConcCycles:        return "Concurrent Cycles";
  case ErgoMixedGCs:          return "Mixed GCs";
  case ErgoTiming:            return "Timing";
  case ErgoIHOP:              return "IHOP";
  default:
    ShouldNotReachHere();
    // Keep the Windows compiler happy
    return NULL;
  }
}
