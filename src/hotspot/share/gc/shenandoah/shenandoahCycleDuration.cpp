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

#include "gc/shenandoah/shenandoahCycleDuration.hpp"
#include "logging/log.hpp"
#include "logging/logTag.hpp"
#include "runtime/mutexLocker.hpp"

#include <cmath>


ShenandoahCycleDuration::ShenandoahCycleDuration(uint size)
  : _gc_times_lock(Mutex::nosafepoint - 2, "ShenandoahCycleTimes_lock", true)
  , _gc_times(size) {}

void ShenandoahCycleDuration::record_duration(double timestamp_at_start, double duration) {
  log_debug(gc, sampling)("Cycle started at: %.3f, completed in %.3fs", timestamp_at_start, duration);
  MonitorLocker locker(&_gc_times_lock, Mutex::_no_safepoint_check_flag);
  _gc_times.add(timestamp_at_start, duration);
}

double ShenandoahCycleDuration::predict_duration(double timestamp_at_start, double margin_of_error) {
  MonitorLocker locker(&_gc_times_lock, Mutex::_no_safepoint_check_flag);

  const double prediction = _gc_times.predict_y(timestamp_at_start);
  if (std::isfinite(prediction) && prediction > 0.0) {
    return prediction + _gc_times.residual_sd() * margin_of_error;
  }

  // return average time, rather than negative or zero time
  return _gc_times.average() + _gc_times.sd() * margin_of_error;
}
