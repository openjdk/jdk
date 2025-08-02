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

#ifndef SHARE_GC_G1_G1HEAPEVALUATIONTASK_HPP
#define SHARE_GC_G1_G1HEAPEVALUATIONTASK_HPP

#include "runtime/task.hpp"
#include "gc/g1/g1_globals.hpp"

class G1CollectedHeap;
class G1HeapSizingPolicy;

// Time-based heap evaluation task that runs during idle periods.
// Uses PeriodicTask rather than G1ServiceTask due to build compatibility issues
// in JDK 25+. PeriodicTask's 10ms granularity is adequate for heap evaluation
// which typically runs on intervals of seconds or longer.
class G1HeapEvaluationTask : public PeriodicTask {
  G1CollectedHeap* _g1h;
  G1HeapSizingPolicy* _heap_sizing_policy;

public:
  G1HeapEvaluationTask(G1CollectedHeap* g1h, G1HeapSizingPolicy* heap_sizing_policy);
  virtual void task() override;
};

#endif // SHARE_GC_G1_G1HEAPEVALUATIONTASK_HPP
