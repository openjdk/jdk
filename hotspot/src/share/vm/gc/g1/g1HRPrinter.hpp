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

#ifndef SHARE_VM_GC_G1_G1HRPRINTER_HPP
#define SHARE_VM_GC_G1_G1HRPRINTER_HPP

#include "gc/g1/heapRegion.hpp"
#include "logging/log.hpp"
#include "memory/allocation.hpp"

#define SKIP_RETIRED_FULL_REGIONS 1

class G1HRPrinter VALUE_OBJ_CLASS_SPEC {
public:
  typedef enum {
    Alloc,
    AllocForce,
    Retire,
    Reuse,
    CSet,
    EvacFailure,
    Cleanup,
    PostCompaction,
    Commit,
    Uncommit
  } ActionType;

  typedef enum {
    Unset,
    Eden,
    Survivor,
    Old,
    StartsHumongous,
    ContinuesHumongous,
    Archive
  } RegionType;

private:
  static const char* action_name(ActionType action);
  static const char* region_type_name(RegionType type);

  // Print an action event. This version is used in most scenarios and
  // only prints the region's bottom. The parameters type and top are
  // optional (the "not set" values are Unset and NULL).
  static void print(ActionType action, RegionType type,
                    HeapRegion* hr, HeapWord* top);

  // Print an action event. This version prints both the region's
  // bottom and end. Used for Commit / Uncommit events.
  static void print(ActionType action, HeapWord* bottom, HeapWord* end);

public:
  // In some places we iterate over a list in order to generate output
  // for the list's elements. By exposing this we can avoid this
  // iteration if the printer is not active.
  const bool is_active() { return log_is_enabled(Trace, gc, region); }

  // The methods below are convenient wrappers for the print() methods.

  void alloc(HeapRegion* hr, RegionType type, bool force = false) {
    if (is_active()) {
      print((!force) ? Alloc : AllocForce, type, hr, NULL);
    }
  }

  void alloc(RegionType type, HeapRegion* hr, HeapWord* top) {
    if (is_active()) {
      print(Alloc, type, hr, top);
    }
  }

  void retire(HeapRegion* hr) {
    if (is_active()) {
      if (!SKIP_RETIRED_FULL_REGIONS || hr->top() < hr->end()) {
        print(Retire, Unset, hr, hr->top());
      }
    }
  }

  void reuse(HeapRegion* hr) {
    if (is_active()) {
      print(Reuse, Unset, hr, NULL);
    }
  }

  void cset(HeapRegion* hr) {
    if (is_active()) {
      print(CSet, Unset, hr, NULL);
    }
  }

  void evac_failure(HeapRegion* hr) {
    if (is_active()) {
      print(EvacFailure, Unset, hr, NULL);
    }
  }

  void cleanup(HeapRegion* hr) {
    if (is_active()) {
      print(Cleanup, Unset, hr, NULL);
    }
  }

  void post_compaction(HeapRegion* hr, RegionType type) {
    if (is_active()) {
      print(PostCompaction, type, hr, hr->top());
    }
  }

  void commit(HeapWord* bottom, HeapWord* end) {
    if (is_active()) {
      print(Commit, bottom, end);
    }
  }

  void uncommit(HeapWord* bottom, HeapWord* end) {
    if (is_active()) {
      print(Uncommit, bottom, end);
    }
  }
};

#endif // SHARE_VM_GC_G1_G1HRPRINTER_HPP
