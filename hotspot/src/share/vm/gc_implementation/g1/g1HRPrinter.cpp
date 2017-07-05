/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
#include "gc_implementation/g1/g1HRPrinter.hpp"
#include "gc_implementation/g1/heapRegion.hpp"
#include "utilities/ostream.hpp"

const char* G1HRPrinter::action_name(ActionType action) {
  switch(action) {
    case Alloc:          return "ALLOC";
    case AllocForce:     return "ALLOC-FORCE";
    case Retire:         return "RETIRE";
    case Reuse:          return "REUSE";
    case CSet:           return "CSET";
    case EvacFailure:    return "EVAC-FAILURE";
    case Cleanup:        return "CLEANUP";
    case PostCompaction: return "POST-COMPACTION";
    case Commit:         return "COMMIT";
    case Uncommit:       return "UNCOMMIT";
    default:             ShouldNotReachHere();
  }
  // trying to keep the Windows compiler happy
  return NULL;
}

const char* G1HRPrinter::region_type_name(RegionType type) {
  switch (type) {
    case Unset:              return NULL;
    case Eden:               return "Eden";
    case Survivor:           return "Survivor";
    case Old:                return "Old";
    case SingleHumongous:    return "SingleH";
    case StartsHumongous:    return "StartsH";
    case ContinuesHumongous: return "ContinuesH";
    default:                 ShouldNotReachHere();
  }
  // trying to keep the Windows compiler happy
  return NULL;
}

const char* G1HRPrinter::phase_name(PhaseType phase) {
  switch (phase) {
    case StartGC:     return "StartGC";
    case EndGC:       return "EndGC";
    case StartFullGC: return "StartFullGC";
    case EndFullGC:   return "EndFullGC";
    default:          ShouldNotReachHere();
  }
  // trying to keep the Windows compiler happy
  return NULL;
}

#define G1HR_PREFIX     " G1HR"

void G1HRPrinter::print(ActionType action, RegionType type,
                        HeapRegion* hr, HeapWord* top) {
  const char* action_str = action_name(action);
  const char* type_str   = region_type_name(type);
  HeapWord* bottom = hr->bottom();

  if (type_str != NULL) {
    if (top != NULL) {
      gclog_or_tty->print_cr(G1HR_PREFIX" %s(%s) "PTR_FORMAT" "PTR_FORMAT,
                             action_str, type_str, bottom, top);
    } else {
      gclog_or_tty->print_cr(G1HR_PREFIX" %s(%s) "PTR_FORMAT,
                             action_str, type_str, bottom);
    }
  } else {
    if (top != NULL) {
      gclog_or_tty->print_cr(G1HR_PREFIX" %s "PTR_FORMAT" "PTR_FORMAT,
                             action_str, bottom, top);
    } else {
      gclog_or_tty->print_cr(G1HR_PREFIX" %s "PTR_FORMAT,
                             action_str, bottom);
    }
  }
}

void G1HRPrinter::print(ActionType action, HeapWord* bottom, HeapWord* end) {
  const char* action_str = action_name(action);

  gclog_or_tty->print_cr(G1HR_PREFIX" %s ["PTR_FORMAT","PTR_FORMAT"]",
                         action_str, bottom, end);
}

void G1HRPrinter::print(PhaseType phase, size_t phase_num) {
  const char* phase_str = phase_name(phase);
  gclog_or_tty->print_cr(G1HR_PREFIX" #%s "SIZE_FORMAT, phase_str, phase_num);
}
