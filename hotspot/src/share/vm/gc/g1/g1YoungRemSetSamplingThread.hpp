/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_G1_G1YOUNGREMSETSAMPLINGTHREAD_HPP
#define SHARE_VM_GC_G1_G1YOUNGREMSETSAMPLINGTHREAD_HPP

#include "gc/shared/concurrentGCThread.hpp"

// The G1YoungRemSetSamplingThread is used to re-assess the validity of
// the prediction for the remembered set lengths of the young generation.
//
// At the end of the GC G1 determines the length of the young gen based on
// how much time the next GC can take, and when the next GC may occur
// according to the MMU.
//
// The assumption is that a significant part of the GC is spent on scanning
// the remembered sets (and many other components), so this thread constantly
// reevaluates the prediction for the remembered set scanning costs, and potentially
// G1CollectorPolicy resizes the young gen. This may do a premature GC or even
// increase the young gen size to keep pause time length goal.
class G1YoungRemSetSamplingThread: public ConcurrentGCThread {
private:
  Monitor* _monitor;

  void sample_young_list_rs_lengths();

  void run_service();
  void stop_service();

  void sleep_before_next_cycle();

  double _vtime_accum;  // Accumulated virtual time.

public:
  G1YoungRemSetSamplingThread();
  double vtime_accum() { return _vtime_accum; }

  virtual void run();
  void stop();
};

#endif /* SHARE_VM_GC_G1_G1YOUNGREMSETSAMPLINGTHREAD_HPP */
