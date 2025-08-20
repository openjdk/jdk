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

public:
  ShenandoahGenerationSizer();

  // Return the configured maximum size in bytes for the given generation.
  size_t max_size_for(ShenandoahGeneration* generation) const;

  // Return the configured minimum size in bytes for the given generation.
  size_t min_size_for(ShenandoahGeneration* generation) const;

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
};

#endif //SHARE_GC_SHENANDOAH_SHENANDOAHGENERATIONSIZER_HPP
