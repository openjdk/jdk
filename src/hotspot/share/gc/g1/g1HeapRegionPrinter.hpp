/*
 * Copyright (c) 2011, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_G1_G1HEAPREGIONPRINTER_HPP
#define SHARE_GC_G1_G1HEAPREGIONPRINTER_HPP

#include "gc/g1/g1HeapRegion.hpp"
#include "logging/log.hpp"
#include "memory/allStatic.hpp"

class FreeRegionList;

class G1HeapRegionPrinter : public AllStatic {

  // Print an action event.
  static void print(const char* action, G1HeapRegion* hr) {
    log_trace(gc, region)("G1HR %s(%s) [" PTR_FORMAT ", " PTR_FORMAT ", " PTR_FORMAT "]",
                          action, hr->get_type_str(), p2i(hr->bottom()), p2i(hr->top()), p2i(hr->end()));
  }

public:
  // In some places we iterate over a list in order to generate output
  // for the list's elements. By exposing this we can avoid this
  // iteration if the printer is not active.
  static bool is_active() { return log_is_enabled(Trace, gc, region); }

  // The methods below are convenient wrappers for the print() method.

  static void alloc(G1HeapRegion* hr)                     { print("ALLOC", hr); }

  static void retire(G1HeapRegion* hr)                    { print("RETIRE", hr); }

  static void reuse(G1HeapRegion* hr)                     { print("REUSE", hr); }

  static void cset(G1HeapRegion* hr)                      { print("CSET", hr); }

  static void evac_failure(G1HeapRegion* hr)              { print("EVAC-FAILURE", hr); }

  static void mark_reclaim(G1HeapRegion* hr)              { print("MARK-RECLAIM", hr); }

  static void eager_reclaim(G1HeapRegion* hr)             { print("EAGER-RECLAIM", hr); }

  static void evac_reclaim(G1HeapRegion* hr)              { print("EVAC-RECLAIM", hr); }

  static void post_compaction(G1HeapRegion* hr)           { print("POST-COMPACTION", hr); }

  static void commit(G1HeapRegion* hr)                    { print("COMMIT", hr); }

  static void active(G1HeapRegion* hr)                    { print("ACTIVE", hr); }

  static void inactive(G1HeapRegion* hr)                  { print("INACTIVE", hr); }

  static void uncommit(G1HeapRegion* hr)                  { print("UNCOMMIT", hr); }
};

#endif // SHARE_GC_G1_G1HEAPREGIONPRINTER_HPP
