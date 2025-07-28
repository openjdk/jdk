/*
 * Copyright (c) 2002, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_PARALLEL_PSADAPTIVESIZEPOLICY_HPP
#define SHARE_GC_PARALLEL_PSADAPTIVESIZEPOLICY_HPP

#include "gc/shared/adaptiveSizePolicy.hpp"
#include "gc/shared/gcUtil.hpp"
#include "utilities/align.hpp"

// This class keeps statistical information and computes the
// optimal free space for both the young and old generation
// based on current application characteristics (based on gc cost
// and application footprint).

class PSAdaptiveSizePolicy : public AdaptiveSizePolicy {
  // Statistics for promoted objs
  AdaptivePaddedNoZeroDevAverage*   _avg_promoted;

  const size_t _space_alignment; // alignment for eden, survivors

  // To facilitate faster growth at start up, supplement the normal
  // growth percentage for the young gen eden and the
  // old gen space for promotion with these value which decay
  // with increasing collections.
  uint _young_gen_size_increment_supplement;

  size_t decrease_eden_for_minor_pause_time(size_t current_eden_size);

  size_t increase_eden(size_t current_eden_size);

  // Size in bytes for an increment or decrement of eden.
  size_t eden_decrement_aligned_down(size_t cur_eden);
  size_t eden_increment_with_supplement_aligned_up(size_t cur_eden);

  AdaptivePaddedNoZeroDevAverage*  avg_promoted() const {
    return _avg_promoted;
  }
 public:

  // NEEDS_CLEANUP this is a singleton object
  PSAdaptiveSizePolicy(size_t space_alignment,
                       double gc_pause_goal_sec,
                       uint gc_time_ratio);

  // Methods indicating events of interest to the adaptive size policy,
  // called by GC algorithms. It is the responsibility of users of this
  // policy to call these methods at the correct times!
  void major_collection_begin();
  void major_collection_end();

  void print_stats(bool is_survivor_overflowing);

  // Accessors

  size_t average_promoted_in_bytes() const {
    return (size_t)avg_promoted()->average();
  }

  size_t padded_average_promoted_in_bytes() const {
    return (size_t)avg_promoted()->padded_average();
  }

  size_t compute_desired_eden_size(bool is_survivor_overflowing, size_t cur_eden);

  size_t compute_desired_survivor_size(size_t current_survivor_size, size_t max_gen_size);

  size_t compute_old_gen_shrink_bytes(size_t old_gen_free_bytes, size_t max_shrink_bytes);

  uint compute_tenuring_threshold(bool is_survivor_overflowing,
                                  uint tenuring_threshold);

  // Return the maximum size of a survivor space if the young generation were of
  // size gen_size.
  size_t max_survivor_size(size_t gen_size) {
    // Never allow the target survivor size to grow more than MinSurvivorRatio
    // of the young generation size.  We cannot grow into a two semi-space
    // system, with Eden zero sized.  Even if the survivor space grows, from()
    // might grow by moving the bottom boundary "down" -- so from space will
    // remain almost full anyway (top() will be near end(), but there will be a
    // large filler object at the bottom).
    const size_t sz = gen_size / MinSurvivorRatio;
    const size_t alignment = _space_alignment;
    return sz > alignment ? align_down(sz, alignment) : alignment;
  }

  // Update averages that are always used (even
  // if adaptive sizing is turned off).
  void update_averages(bool is_survivor_overflow,
                       size_t survived,
                       size_t promoted);

  // Decay the supplemental growth additive.
  void decay_supplemental_growth(uint num_minor_gcs);
};

#endif // SHARE_GC_PARALLEL_PSADAPTIVESIZEPOLICY_HPP
