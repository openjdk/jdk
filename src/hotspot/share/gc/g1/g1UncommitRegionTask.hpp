/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_G1_G1UNCOMMITREGIONTASK_HPP
#define SHARE_GC_G1_G1UNCOMMITREGIONTASK_HPP

#include "gc/g1/g1ServiceThread.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/ticks.hpp"

class G1UncommitRegionTask : public G1ServiceTask {
  static const uint UncommitChunkSize = 64;

  static G1UncommitRegionTask* _instance;
  static void initialize();
  static G1UncommitRegionTask* instance();

  // The state is not guarded by any lock because the places
  // where it is updated can never run concurrently. The state
  // is set to active only from a safepoint and it is set to
  // inactive while running on the service thread joined with
  // the suspendible thread set.
  enum class TaskState { active, inactive };
  TaskState _state;

  // Members to keep a summary of the current concurrent uncommit
  // work. Used for printing when no more work is available.
  Tickspan _summary_duration;
  uint _summary_region_count;

  G1UncommitRegionTask();
  bool is_active();
  void set_state(TaskState state);

  void report_execution(Tickspan time, uint regions);
  void report_summary();
  void clear_summary();

public:
  static void activate();
  virtual void execute();
};

#endif