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

#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHGENERATIONSIZER_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAHGENERATIONSIZER_HPP

#include "utilities/globalDefinitions.hpp"

class ShenandoahGeneration;
class ShenandoahGenerationalHeap;

class ShenandoahGenerationSizer {
private:
  enum SizerKind {
    SizerDefaults,
    SizerNewSizeOnly,
    SizerMaxNewSizeOnly,
    SizerMaxAndNewSize,
    SizerNewRatio
  };
  SizerKind _sizer_kind;

  size_t _min_desired_young_regions;
  size_t _max_desired_young_regions;

  static size_t calculate_min_young_regions(size_t heap_region_count);
  static size_t calculate_max_young_regions(size_t heap_region_count);

  // Update the given values for minimum and maximum young gen length in regions
  // given the number of heap regions depending on the kind of sizing algorithm.
  void recalculate_min_max_young_length(size_t heap_region_count);

  // This will attempt to transfer regions from the `src` generation to `dst` generation.
  // If the transfer would violate the configured minimum size for the source or the configured
  // maximum size of the destination, it will not perform the transfer and will return false.
  // Returns true if the transfer is performed.
  bool transfer_regions(ShenandoahGeneration* src, ShenandoahGeneration* dst, size_t regions) const;

  // Return the configured maximum size in bytes for the given generation.
  size_t max_size_for(ShenandoahGeneration* generation) const;

  // Return the configured minimum size in bytes for the given generation.
  size_t min_size_for(ShenandoahGeneration* generation) const;

public:
  ShenandoahGenerationSizer();

  // Calculate the maximum length of the young gen given the number of regions
  // depending on the sizing algorithm.
  void heap_size_changed(size_t heap_size);

  // Minimum size of young generation in bytes as multiple of region size.
  size_t min_young_size() const;
  size_t min_young_regions() const {
    return _min_desired_young_regions;
  }

  // Maximum size of young generation in bytes as multiple of region size.
  size_t max_young_size() const;
  size_t max_young_regions() const {
    return _max_desired_young_regions;
  }

  // True if transfer succeeds, else false. See transfer_regions.
  bool transfer_to_young(size_t regions) const;
  bool transfer_to_old(size_t regions) const;

  // force transfer is used when we promote humongous objects.  May violate min/max limits on generation sizes
  void force_transfer_to_old(size_t regions) const;
};

#endif //SHARE_GC_SHENANDOAH_SHENANDOAHGENERATIONSIZER_HPP
