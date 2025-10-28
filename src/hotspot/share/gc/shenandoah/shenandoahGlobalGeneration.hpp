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

#ifndef SHARE_VM_GC_SHENANDOAH_SHENANDOAHGLOBALGENERATION_HPP
#define SHARE_VM_GC_SHENANDOAH_SHENANDOAHGLOBALGENERATION_HPP

#include "gc/shenandoah/shenandoahGeneration.hpp"
#include "gc/shenandoah/shenandoahOldGeneration.hpp"
#include "gc/shenandoah/shenandoahYoungGeneration.hpp"

// A "generation" that represents the whole heap.
class ShenandoahGlobalGeneration : public ShenandoahGeneration {
public:
  ShenandoahGlobalGeneration(bool generational, uint max_queues, size_t max_capacity)
  : ShenandoahGeneration(generational ? GLOBAL : NON_GEN, max_queues, max_capacity) { }

public:
  const char* name() const override;

  size_t max_capacity() const override;
  size_t used_regions() const override;
  size_t used_regions_size() const override;
  size_t available() const override;
  size_t soft_available() const override;

  void set_concurrent_mark_in_progress(bool in_progress) override;

  bool contains(ShenandoahAffiliation affiliation) const override;
  bool contains(ShenandoahHeapRegion* region) const override;

  bool contains(oop obj) const override {
    return ShenandoahHeap::heap()->is_in_reserved(obj);
  }

  void parallel_heap_region_iterate(ShenandoahHeapRegionClosure* cl) override;

  void heap_region_iterate(ShenandoahHeapRegionClosure* cl) override;

  bool is_concurrent_mark_in_progress() override;

  void set_mark_complete() override;

  void set_mark_incomplete() override;

  ShenandoahHeuristics* initialize_heuristics(ShenandoahMode* gc_mode) override;

  virtual void prepare_gc() override;
};

#endif // SHARE_VM_GC_SHENANDOAH_SHENANDOAHGLOBALGENERATION_HPP

