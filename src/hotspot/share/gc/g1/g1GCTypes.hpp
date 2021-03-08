/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_G1_G1GCTYPES_HPP
#define SHARE_GC_G1_G1GCTYPES_HPP

#include "utilities/debug.hpp"

enum G1YCPhase {
  Normal,
  ConcurrentStart,
  DuringMarkOrRebuild,
  Mixed,
  G1YCPhaseEndSentinel
};

enum G1GCType {
  YoungGC,
  LastYoungGC,
  ConcurrentStartMarkGC,
  ConcurrentStartUndoGC,
  Cleanup,
  Remark,
  MixedGC,
  FullGC,
  G1GCTypeEndSentinel
};

class G1GCTypeHelper {
 public:

  static bool is_young_only_pause(G1GCType type) {
    assert(type != FullGC, "must be");
    assert(type != Remark, "must be");
    assert(type != Cleanup, "must be");
    return type == ConcurrentStartUndoGC ||
           type == ConcurrentStartMarkGC ||
           type == LastYoungGC ||
           type == YoungGC;
  }

  static bool is_mixed_pause(G1GCType type) {
    assert(type != FullGC, "must be");
    assert(type != Remark, "must be");
    assert(type != Cleanup, "must be");
    return type == MixedGC;
  }

  static bool is_last_young_pause(G1GCType type) {
    return type == LastYoungGC;
  }

  static bool is_concurrent_start_pause(G1GCType type) {
    return type == ConcurrentStartMarkGC || type == ConcurrentStartUndoGC;
  }

  static const char* to_string(G1YCPhase type) {
    switch(type) {
      case Normal: return "Normal";
      case ConcurrentStart: return "Concurrent Start";
      case DuringMarkOrRebuild: return "During Mark";
      case Mixed: return "Mixed";
      default: ShouldNotReachHere(); return NULL;
    }
  }

};

#endif // SHARE_GC_G1_G1GCTYPES_HPP
