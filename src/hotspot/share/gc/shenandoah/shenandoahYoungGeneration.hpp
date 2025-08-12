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

#ifndef SHARE_VM_GC_SHENANDOAH_SHENANDOAHYOUNGGENERATION_HPP
#define SHARE_VM_GC_SHENANDOAH_SHENANDOAHYOUNGGENERATION_HPP

#include "gc/shenandoah/heuristics/shenandoahYoungHeuristics.hpp"
#include "gc/shenandoah/shenandoahGeneration.hpp"

class ShenandoahYoungGeneration : public ShenandoahGeneration {
private:
  ShenandoahObjToScanQueueSet* _old_gen_task_queues;
  ShenandoahYoungHeuristics* _young_heuristics;

public:
  ShenandoahYoungGeneration(uint max_queues, size_t max_capacity);

  ShenandoahHeuristics* initialize_heuristics(ShenandoahMode* gc_mode) override;

  const char* name() const override {
    return "Young";
  }

  ShenandoahYoungHeuristics* heuristics() const override {
    return _young_heuristics;
  }

  void set_concurrent_mark_in_progress(bool in_progress) override;
  bool is_concurrent_mark_in_progress() override;

  void parallel_heap_region_iterate(ShenandoahHeapRegionClosure* cl) override;

  void parallel_heap_region_iterate_free(ShenandoahHeapRegionClosure* cl) override;

  void heap_region_iterate(ShenandoahHeapRegionClosure* cl) override;

  bool contains(ShenandoahAffiliation affiliation) const override;
  bool contains(ShenandoahHeapRegion* region) const override;
  bool contains(oop obj) const override;

  void reserve_task_queues(uint workers) override;
  void set_old_gen_task_queues(ShenandoahObjToScanQueueSet* old_gen_queues) {
    _old_gen_task_queues = old_gen_queues;
  }
  ShenandoahObjToScanQueueSet* old_gen_task_queues() const override {
    return _old_gen_task_queues;
  }

  // Returns true if the young generation is configured to enqueue old
  // oops for the old generation mark queues.
  bool is_bootstrap_cycle() {
    return _old_gen_task_queues != nullptr;
  }

  size_t available() const override;

  // Do not override available_with_reserve() because that needs to see memory reserved for Collector

  size_t soft_available() const override;

  void prepare_gc() override;
};

#endif // SHARE_VM_GC_SHENANDOAH_SHENANDOAHYOUNGGENERATION_HPP
