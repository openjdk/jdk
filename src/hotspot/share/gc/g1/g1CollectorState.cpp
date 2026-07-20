/*
 * Copyright (c) 2021, 2026, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/g1/g1CollectorState.inline.hpp"
#include "runtime/safepoint.hpp"
#include "utilities/debug.hpp"

G1CollectorState::Pause G1CollectorState::gc_pause_type(bool concurrent_operation_is_full_mark) const {
  assert(SafepointSynchronize::is_at_safepoint(), "must be");
  switch (_phase) {
    case Phase::YoungNormal: return Pause::Normal;
    case Phase::YoungConcurrentStart:
        return concurrent_operation_is_full_mark ? Pause::ConcurrentStartFull :
                                                   Pause::ConcurrentStartUndo;
    case Phase::YoungPrepareMixed: return Pause::PrepareMixed;
    case Phase::Mixed: return Pause::Mixed;
    case Phase::FullGC: return Pause::Full;
    default: ShouldNotReachHere();
  }
}

const char* G1CollectorState::to_string(Pause type) {
  static const char* pause_strings[] = { "Normal",
                                         "Concurrent Start", // Do not distinguish between the different
                                         "Concurrent Start", // Concurrent Start pauses.
                                         "Prepare Mixed",
                                         "Cleanup",
                                         "Remark",
                                         "Mixed",
                                         "Full" };
  return pause_strings[static_cast<uint>(type)];
}
