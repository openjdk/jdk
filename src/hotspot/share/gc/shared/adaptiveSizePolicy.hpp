/*
 * Copyright (c) 2004, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_SHARED_ADAPTIVESIZEPOLICY_HPP
#define SHARE_GC_SHARED_ADAPTIVESIZEPOLICY_HPP

#include "gc/shared/gc_globals.hpp"
#include "gc/shared/gcCause.hpp"
#include "gc/shared/gcUtil.hpp"
#include "memory/allocation.hpp"
#include "runtime/os.hpp"
#include "utilities/numberSeq.hpp"

// This class keeps statistical information and computes the
// size of the heap.

class AdaptiveSizePolicy : public CHeapObj<mtGC> {
 protected:
  // [0, 1]; closer to 1 means assigning more weight to most recent samples.
  constexpr static double seq_default_alpha_value = 0.75;

  // Minimal distance between two consecutive GC pauses; shorter distance (more
  // frequent gc) can hinder app throughput. Additionally, too frequent gc
  // means objs haven't got time to die yet, so #promoted objs will be high.
  // Default: 100ms.
  static constexpr double MinGCDistanceSecond = 0.100;
  static_assert(MinGCDistanceSecond >= 0.001, "inv");

  // Goal for the fraction of the total time during which application
  // threads run
  const double _throughput_goal;

  // pause and interval times for collections
  static elapsedTimer _minor_timer;

  // Major collection timers, used to determine both
  // pause and interval times for collections
  static elapsedTimer _major_timer;

  // To measure wall-clock time between two GCs, i.e. mutator running time, and record them.
  elapsedTimer _gc_distance_timer;
  NumberSeq _gc_distance_seconds_seq;

  static constexpr uint NumOfGCSample = 32;
  // Recording the last NumOfGCSample number of minor/major gc durations
  TruncatedSeq _trimmed_minor_gc_time_seconds;
  TruncatedSeq _trimmed_major_gc_time_seconds;

  // A ring buffer with fixed size (NumOfGCSample) to record the most recent
  // samples of gc-duration (minor and major) so that we can calculate
  // mutator-wall-clock-time percentage for the given window.
  class GCSampleRingBuffer {
    double _start_instants[NumOfGCSample];
    double _durations[NumOfGCSample];
    double _duration_sum;
    uint _sample_index;
    uint _num_of_samples;

  public:
    GCSampleRingBuffer()
      : _duration_sum(0.0), _sample_index(0), _num_of_samples(0) {}

    double duration_sum() const { return _duration_sum; }

    void record_sample(double gc_duration) {
      if (_num_of_samples < NumOfGCSample) {
        _num_of_samples++;
      } else {
        assert(_num_of_samples == NumOfGCSample, "inv");
        _duration_sum -= _durations[_sample_index];
      }

      double gc_start_instant = os::elapsedTime() - gc_duration;
      _start_instants[_sample_index] = gc_start_instant;
      _durations[_sample_index] = gc_duration;
      _duration_sum += gc_duration;

      _sample_index = (_sample_index + 1) % NumOfGCSample;
    }

    double trimmed_window_duration() const {
      double current_time = os::elapsedTime();
      double oldest_gc_start_instant;
      if (_num_of_samples < NumOfGCSample) {
        oldest_gc_start_instant = _start_instants[0];
      } else {
        oldest_gc_start_instant = _start_instants[_sample_index];
      }
      return current_time - oldest_gc_start_instant;
    }
  };

  GCSampleRingBuffer _gc_samples;

  // The number of bytes promoted to old-gen after a young-gc
  NumberSeq _promoted_bytes;

  // The number of bytes in to-space after a young-gc
  NumberSeq _survived_bytes;

  // The rate of promotion to old-gen
  NumberSeq _promotion_rate_bytes_per_sec;

  // The peak of used bytes in old-gen before/after young/full-gc
  NumberSeq _peak_old_used_bytes_seq;

  // Variable for estimating the major and minor pause times.
  // These variables represent linear least-squares fits of
  // the data.
  //   minor pause time vs. young gen size
  LinearLeastSquareFit* _minor_pause_young_estimator;

  // Allowed difference between major and minor GC times, used
  // for computing tenuring_threshold
  const double _threshold_tolerance_percent;

  const double _gc_pause_goal_sec; // Goal for maximum GC pause

  // Flag indicating that the adaptive policy is ready to use
  bool _young_gen_policy_is_ready;

  // Accessors
  double gc_pause_goal_sec() const { return _gc_pause_goal_sec; }

  double minor_gc_time_sum() const {
    return _trimmed_minor_gc_time_seconds.sum();
  }
  double major_gc_time_sum() const {
    return _trimmed_major_gc_time_seconds.sum();
  }

  void record_gc_duration(double gc_duration) {
    _gc_samples.record_sample(gc_duration);
  }

  // Percent of GC wall-clock time.
  double gc_time_percent() const {
    double total_time = _gc_samples.trimmed_window_duration();
    double gc_time = _gc_samples.duration_sum();
    double gc_percent = gc_time / total_time;
    assert(gc_percent <= 1.0, "inv");
    assert(gc_percent >= 0, "inv");
    return gc_percent;
  }

  bool young_gen_policy_is_ready() { return _young_gen_policy_is_ready; }

  size_t eden_increment(size_t cur_eden);
  size_t eden_increment(size_t cur_eden, uint percent_change);

public:
  AdaptiveSizePolicy(double gc_pause_goal_sec,
                     uint gc_cost_ratio);

  void record_gc_pause_end_instant() {
    _gc_distance_timer.reset();
    _gc_distance_timer.start();
  }

  void record_gc_pause_start_instant() {
    _gc_distance_timer.stop();
    _gc_distance_seconds_seq.add(_gc_distance_timer.seconds());
  }

  double minor_gc_time_estimate() const {
    return _trimmed_minor_gc_time_seconds.davg()
         + _trimmed_minor_gc_time_seconds.dsd();
  }

  double minor_gc_time_conservative_estimate() const {
    double davg_plus_dsd = _trimmed_minor_gc_time_seconds.davg()
                         + _trimmed_minor_gc_time_seconds.dsd();
    double avg_plus_sd =  _trimmed_minor_gc_time_seconds.avg()
                         + _trimmed_minor_gc_time_seconds.sd();
    return MAX2(davg_plus_dsd, avg_plus_sd);
  }

  double major_gc_time_estimate() const {
    return _trimmed_major_gc_time_seconds.davg()
         + _trimmed_major_gc_time_seconds.dsd();
  }

  void sample_old_gen_used_bytes(size_t used_bytes) {
    _peak_old_used_bytes_seq.add(used_bytes);
  }

  double peak_old_gen_used_estimate() const {
    return _peak_old_used_bytes_seq.davg()
         + _peak_old_used_bytes_seq.dsd();
  }

  double promoted_bytes_estimate() const {
    return _promoted_bytes.davg()
         + _promoted_bytes.dsd();
  }

  double promotion_rate_bytes_per_sec_estimate() const {
    return _promotion_rate_bytes_per_sec.davg()
         + _promotion_rate_bytes_per_sec.dsd();
  }

  double survived_bytes_estimate() const {
    // Conservative estimate to minimize promotion to old-gen
    double avg_plus_sd = _survived_bytes.avg()
                       + _survived_bytes.sd();
    double davg_plus_dsd = _survived_bytes.davg()
                         + _survived_bytes.dsd();
    return MAX2(avg_plus_sd, davg_plus_dsd);
  }

  // Percent of mutator wall-clock time.
  double mutator_time_percent() const {
    double result = 1.0 - gc_time_percent();
    return result;
  }

  // Methods indicating events of interest to the adaptive size policy,
  // called by GC algorithms. It is the responsibility of users of this
  // policy to call these methods at the correct times!
  void minor_collection_begin();
  void minor_collection_end(size_t eden_capacity_in_bytes);

  LinearLeastSquareFit* minor_pause_young_estimator() {
    return _minor_pause_young_estimator;
  }
};

#endif // SHARE_GC_SHARED_ADAPTIVESIZEPOLICY_HPP
