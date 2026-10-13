/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHCYCLEDURATION_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAHCYCLEDURATION_HPP

#include "gc/shenandoah/shenandoahWeightedSeq.hpp"
#include "runtime/mutex.hpp"

class ShenandoahCycleDuration {
  // To enable detection of GC time trends, we keep separate track of the recent history of gc time.  During initialization,
  // for example, the amount of live memory may be increasing, which is likely to cause the GC times to increase.  This history
  // allows us to predict increasing GC times rather than always assuming average recent GC time is the best predictor.
  static constexpr uint GC_TIME_SAMPLE_SIZE = 15;

  // Written by control thread, read by regulator thread
  Monitor _gc_times_lock;
  ShenandoahWeightedSeq _gc_times;

public:
  explicit ShenandoahCycleDuration(uint size = GC_TIME_SAMPLE_SIZE);
  void record_duration(double timestamp_at_start, double duration);
  double predict_duration(double timestamp_at_start, double margin_of_error);
};

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHCYCLEDURATION_HPP
