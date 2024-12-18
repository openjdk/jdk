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

#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHGENERATIONALEVACUATIONTASK_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAHGENERATIONALEVACUATIONTASK_HPP

#include "gc/shared/workerThread.hpp"

class ShenandoahGenerationalHeap;
class ShenandoahHeapRegion;
class ShenandoahRegionIterator;

// Unlike ShenandoahEvacuationTask, this iterates over all regions rather than just the collection set.
// This is needed in order to promote humongous start regions if age() >= tenure threshold.
class ShenandoahGenerationalEvacuationTask : public WorkerTask {
private:
  ShenandoahGenerationalHeap* const _heap;
  ShenandoahRegionIterator* _regions;
  bool _concurrent;
  bool _only_promote_regions;
  uint _tenuring_threshold;

public:
  ShenandoahGenerationalEvacuationTask(ShenandoahGenerationalHeap* sh,
                                       ShenandoahRegionIterator* iterator,
                                       bool concurrent, bool only_promote_regions);
  void work(uint worker_id) override;
private:
  void do_work();
  void promote_regions();
  void evacuate_and_promote_regions();
  void maybe_promote_region(ShenandoahHeapRegion* region);
  void promote_in_place(ShenandoahHeapRegion* region);
  void promote_humongous(ShenandoahHeapRegion* region);
};

#endif //SHARE_GC_SHENANDOAH_SHENANDOAHGENERATIONALEVACUATIONTASK_HPP
