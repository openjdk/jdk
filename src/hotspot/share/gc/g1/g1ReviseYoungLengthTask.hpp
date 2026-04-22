/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_G1_G1REVISEYOUNGLENGTHTASK_HPP
#define SHARE_GC_G1_G1REVISEYOUNGLENGTHTASK_HPP

#include "gc/g1/g1CardSetMemory.hpp"
#include "gc/g1/g1HeapRegionRemSet.hpp"
#include "gc/g1/g1MonotonicArenaFreePool.hpp"
#include "gc/g1/g1ServiceThread.hpp"
#include "utilities/growableArray.hpp"
#include "utilities/ticks.hpp"

// ServiceTask to revise the young generation target length.
class G1ReviseYoungLengthTask : public G1ServiceTask {

  // The delay used to reschedule this task.
  jlong reschedule_delay_ms() const;

  class RemSetSamplingClosure; // Helper class for calculating remembered set summary.

  // Adjust the target length (in regions) of the young gen, based on the
  // current length of the remembered sets.
  //
  // At the end of the GC G1 determines the length of the young gen based on
  // how much time the next GC can take, and when the next GC may occur
  // according to the MMU.
  //
  // The assumption is that a significant part of the GC is spent on scanning
  // the remembered sets (and many other components), so this thread constantly
  // reevaluates the prediction for the remembered set scanning costs, and potentially
  // resizes the young gen. This may do a premature GC or even increase the young
  // gen size to keep pause time length goal.
  void adjust_young_list_target_length();

public:
  explicit G1ReviseYoungLengthTask(const char* name);

  void execute() override;
};

#endif // SHARE_GC_G1_G1REVISEYOUNGLENGTHTASK_HPP