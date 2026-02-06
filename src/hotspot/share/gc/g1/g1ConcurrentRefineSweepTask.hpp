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

#ifndef SHARE_GC_G1_G1CONCURRENTREFINESWEEPTASK_HPP
#define SHARE_GC_G1_G1CONCURRENTREFINESWEEPTASK_HPP

#include "gc/shared/workerThread.hpp"

class G1CardTableClaimTable;
class G1ConcurrentRefineStats;

class G1ConcurrentRefineSweepTask : public WorkerTask {
  G1CardTableClaimTable* _scan_state;
  G1ConcurrentRefineStats* _stats;
  uint _max_workers;
  bool _sweep_completed;

public:

  G1ConcurrentRefineSweepTask(G1CardTableClaimTable* scan_state, G1ConcurrentRefineStats* stats, uint max_workers);

  void work(uint worker_id) override;

  bool sweep_completed() const;
};

#endif /* SHARE_GC_G1_G1CONCURRENTREFINESWEEPTASK_HPP */
