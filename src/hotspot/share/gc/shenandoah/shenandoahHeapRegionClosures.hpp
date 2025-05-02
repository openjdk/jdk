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

#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHHEAPREGIONCLOSURES_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAHHEAPREGIONCLOSURES_HPP


#include "gc/shenandoah/shenandoahHeap.hpp"
#include "gc/shenandoah/shenandoahHeapRegion.inline.hpp"

// Applies the given closure to all regions with the given affiliation
template<ShenandoahAffiliation AFFILIATION>
class ShenandoahIncludeRegionClosure : public ShenandoahHeapRegionClosure {
private:
  ShenandoahHeapRegionClosure* _closure;

public:
  explicit ShenandoahIncludeRegionClosure(ShenandoahHeapRegionClosure* closure): _closure(closure) {}

  void heap_region_do(ShenandoahHeapRegion* r) override {
    if (r->affiliation() == AFFILIATION) {
      _closure->heap_region_do(r);
    }
  }

  bool is_thread_safe() override {
    return _closure->is_thread_safe();
  }
};

// Applies the given closure to all regions without the given affiliation
template<ShenandoahAffiliation AFFILIATION>
class ShenandoahExcludeRegionClosure : public ShenandoahHeapRegionClosure {
private:
  ShenandoahHeapRegionClosure* _closure;

public:
  explicit ShenandoahExcludeRegionClosure(ShenandoahHeapRegionClosure* closure): _closure(closure) {}

  void heap_region_do(ShenandoahHeapRegion* r) override {
    if (r->affiliation() != AFFILIATION) {
      _closure->heap_region_do(r);
    }
  }

  bool is_thread_safe() override {
    return _closure->is_thread_safe();
  }
};

// Makes regions pinned or unpinned according to the region's pin count
class ShenandoahSynchronizePinnedRegionStates : public ShenandoahHeapRegionClosure {
private:
  ShenandoahHeapLock* const _lock;

public:
  ShenandoahSynchronizePinnedRegionStates();

  void heap_region_do(ShenandoahHeapRegion* r) override;
  bool is_thread_safe() override { return true; }

  void synchronize_pin_count(ShenandoahHeapRegion* r);
};

class ShenandoahMarkingContext;

// Synchronizes region pinned status, sets update watermark and adjust live data tally for regions
class ShenandoahFinalMarkUpdateRegionStateClosure : public ShenandoahHeapRegionClosure {
private:
  ShenandoahMarkingContext* const _ctx;
  ShenandoahSynchronizePinnedRegionStates _pins;
public:
  explicit ShenandoahFinalMarkUpdateRegionStateClosure(ShenandoahMarkingContext* ctx);

  void heap_region_do(ShenandoahHeapRegion* r) override;
  bool is_thread_safe() override { return true; }
};

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHHEAPREGIONCLOSURES_HPP
