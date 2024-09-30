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

#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHMARKCLOSURES_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAHMARKCLOSURES_HPP

#include "gc/shenandoah/shenandoahHeap.hpp"
#include "gc/shenandoah/shenandoahAgeCensus.hpp"

class ShenandoahMarkingContext;
class ShenandoahHeapRegion;

class ShenandoahFinalMarkUpdateRegionStateClosure : public ShenandoahHeapRegionClosure {
private:
  ShenandoahMarkingContext* const _ctx;
  ShenandoahHeapLock* const _lock;
public:
  explicit ShenandoahFinalMarkUpdateRegionStateClosure(ShenandoahMarkingContext* ctx);

  void heap_region_do(ShenandoahHeapRegion* r);

  bool is_thread_safe() { return true; }
};

// Add [TAMS, top) volume over young regions. Used to correct age 0 cohort census
// for adaptive tenuring when census is taken during marking.
// In non-product builds, for the purposes of verification, we also collect the total
// live objects in young regions as well.
class ShenandoahUpdateCensusZeroCohortClosure : public ShenandoahHeapRegionClosure {
private:
  ShenandoahMarkingContext* const _ctx;
  // Population size units are words (not bytes)
  size_t _age0_pop;                // running tally of age0 population size
  size_t _total_pop;               // total live population size
public:
  ShenandoahUpdateCensusZeroCohortClosure(ShenandoahMarkingContext* ctx);

  void heap_region_do(ShenandoahHeapRegion* r);

  size_t get_age0_population()  { return _age0_pop; }
  size_t get_total_population() { return _total_pop; }
};
#endif // SHARE_GC_SHENANDOAH_SHENANDOAHMARKCLOSURES_HPP
