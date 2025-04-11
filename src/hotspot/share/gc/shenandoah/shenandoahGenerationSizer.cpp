/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

#include "gc/shared/gc_globals.hpp"
#include "gc/shenandoah/shenandoahGeneration.hpp"
#include "gc/shenandoah/shenandoahGenerationSizer.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahHeapRegion.hpp"
#include "gc/shenandoah/shenandoahOldGeneration.hpp"
#include "gc/shenandoah/shenandoahYoungGeneration.hpp"
#include "logging/log.hpp"
#include "runtime/globals_extension.hpp"


ShenandoahGenerationSizer::ShenandoahGenerationSizer()
        : _sizer_kind(SizerDefaults),
          _min_desired_young_regions(0),
          _max_desired_young_regions(0) {

  if (FLAG_IS_CMDLINE(NewRatio)) {
    if (FLAG_IS_CMDLINE(NewSize) || FLAG_IS_CMDLINE(MaxNewSize)) {
      log_warning(gc, ergo)("-XX:NewSize and -XX:MaxNewSize override -XX:NewRatio");
    } else {
      _sizer_kind = SizerNewRatio;
      return;
    }
  }

  if (NewSize > MaxNewSize) {
    if (FLAG_IS_CMDLINE(MaxNewSize)) {
      log_warning(gc, ergo)("NewSize (%zuk) is greater than the MaxNewSize (%zuk). "
                            "A new max generation size of %zuk will be used.",
              NewSize/K, MaxNewSize/K, NewSize/K);
    }
    FLAG_SET_ERGO(MaxNewSize, NewSize);
  }

  if (FLAG_IS_CMDLINE(NewSize)) {
    _min_desired_young_regions = MAX2(uint(NewSize / ShenandoahHeapRegion::region_size_bytes()), 1U);
    if (FLAG_IS_CMDLINE(MaxNewSize)) {
      _max_desired_young_regions = MAX2(uint(MaxNewSize / ShenandoahHeapRegion::region_size_bytes()), 1U);
      _sizer_kind = SizerMaxAndNewSize;
    } else {
      _sizer_kind = SizerNewSizeOnly;
    }
  } else if (FLAG_IS_CMDLINE(MaxNewSize)) {
    _max_desired_young_regions = MAX2(uint(MaxNewSize / ShenandoahHeapRegion::region_size_bytes()), 1U);
    _sizer_kind = SizerMaxNewSizeOnly;
  }
}

size_t ShenandoahGenerationSizer::calculate_min_young_regions(size_t heap_region_count) {
  size_t min_young_regions = (heap_region_count * ShenandoahMinYoungPercentage) / 100;
  return MAX2(min_young_regions, (size_t) 1U);
}

size_t ShenandoahGenerationSizer::calculate_max_young_regions(size_t heap_region_count) {
  size_t max_young_regions = (heap_region_count * ShenandoahMaxYoungPercentage) / 100;
  return MAX2(max_young_regions, (size_t) 1U);
}

void ShenandoahGenerationSizer::recalculate_min_max_young_length(size_t heap_region_count) {
  assert(heap_region_count > 0, "Heap must be initialized");

  switch (_sizer_kind) {
    case SizerDefaults:
      _min_desired_young_regions = calculate_min_young_regions(heap_region_count);
      _max_desired_young_regions = calculate_max_young_regions(heap_region_count);
      break;
    case SizerNewSizeOnly:
      _max_desired_young_regions = calculate_max_young_regions(heap_region_count);
      _max_desired_young_regions = MAX2(_min_desired_young_regions, _max_desired_young_regions);
      break;
    case SizerMaxNewSizeOnly:
      _min_desired_young_regions = calculate_min_young_regions(heap_region_count);
      _min_desired_young_regions = MIN2(_min_desired_young_regions, _max_desired_young_regions);
      break;
    case SizerMaxAndNewSize:
      // Do nothing. Values set on the command line, don't update them at runtime.
      break;
    case SizerNewRatio:
      _min_desired_young_regions = MAX2(uint(heap_region_count / (NewRatio + 1)), 1U);
      _max_desired_young_regions = _min_desired_young_regions;
      break;
    default:
      ShouldNotReachHere();
  }

  assert(_min_desired_young_regions <= _max_desired_young_regions, "Invalid min/max young gen size values");
}

void ShenandoahGenerationSizer::heap_size_changed(size_t heap_size) {
  recalculate_min_max_young_length(heap_size / ShenandoahHeapRegion::region_size_bytes());
}

bool ShenandoahGenerationSizer::transfer_regions(ShenandoahGeneration* src, ShenandoahGeneration* dst, size_t regions) const {
  const size_t bytes_to_transfer = regions * ShenandoahHeapRegion::region_size_bytes();

  if (src->free_unaffiliated_regions() < regions) {
    // Source does not have enough free regions for this transfer. The caller should have
    // already capped the transfer based on available unaffiliated regions.
    return false;
  }

  if (dst->max_capacity() + bytes_to_transfer > max_size_for(dst)) {
    // This transfer would cause the destination generation to grow above its configured maximum size.
    return false;
  }

  if (src->max_capacity() - bytes_to_transfer < min_size_for(src)) {
    // This transfer would cause the source generation to shrink below its configured minimum size.
    return false;
  }

  src->decrease_capacity(bytes_to_transfer);
  dst->increase_capacity(bytes_to_transfer);
  const size_t new_size = dst->max_capacity();
  log_info(gc, ergo)("Transfer %zu region(s) from %s to %s, yielding increased size: " PROPERFMT,
                     regions, src->name(), dst->name(), PROPERFMTARGS(new_size));
  return true;
}


size_t ShenandoahGenerationSizer::max_size_for(ShenandoahGeneration* generation) const {
  switch (generation->type()) {
    case YOUNG:
      return max_young_size();
    case OLD:
      // On the command line, max size of OLD is specified indirectly, by setting a minimum size of young.
      // OLD is what remains within the heap after YOUNG has been sized.
      return ShenandoahHeap::heap()->max_capacity() - min_young_size();
    default:
      ShouldNotReachHere();
      return 0;
  }
}

size_t ShenandoahGenerationSizer::min_size_for(ShenandoahGeneration* generation) const {
  switch (generation->type()) {
    case YOUNG:
      return min_young_size();
    case OLD:
      // On the command line, min size of OLD is specified indirectly, by setting a maximum size of young.
      // OLD is what remains within the heap after YOUNG has been sized.
      return ShenandoahHeap::heap()->max_capacity() - max_young_size();
    default:
      ShouldNotReachHere();
      return 0;
  }
}


// Returns true iff transfer is successful
bool ShenandoahGenerationSizer::transfer_to_old(size_t regions) const {
  ShenandoahGenerationalHeap* heap = ShenandoahGenerationalHeap::heap();
  return transfer_regions(heap->young_generation(), heap->old_generation(), regions);
}

// This is used when promoting humongous or highly utilized regular regions in place.  It is not required in this situation
// that the transferred regions be unaffiliated.
void ShenandoahGenerationSizer::force_transfer_to_old(size_t regions) const {
  ShenandoahGenerationalHeap* heap = ShenandoahGenerationalHeap::heap();
  ShenandoahGeneration* old_gen = heap->old_generation();
  ShenandoahGeneration* young_gen = heap->young_generation();
  const size_t bytes_to_transfer = regions * ShenandoahHeapRegion::region_size_bytes();

  young_gen->decrease_capacity(bytes_to_transfer);
  old_gen->increase_capacity(bytes_to_transfer);
  const size_t new_size = old_gen->max_capacity();
  log_info(gc, ergo)("Forcing transfer of %zu region(s) from %s to %s, yielding increased size: " PROPERFMT,
                     regions, young_gen->name(), old_gen->name(), PROPERFMTARGS(new_size));
}


bool ShenandoahGenerationSizer::transfer_to_young(size_t regions) const {
  ShenandoahGenerationalHeap* heap = ShenandoahGenerationalHeap::heap();
  return transfer_regions(heap->old_generation(), heap->young_generation(), regions);
}

size_t ShenandoahGenerationSizer::min_young_size() const {
  return min_young_regions() * ShenandoahHeapRegion::region_size_bytes();
}

size_t ShenandoahGenerationSizer::max_young_size() const {
  return max_young_regions() * ShenandoahHeapRegion::region_size_bytes();
}
