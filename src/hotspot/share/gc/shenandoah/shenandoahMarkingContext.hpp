/*
 * Copyright (c) 2018, 2021, Red Hat, Inc. All rights reserved.
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

#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHMARKINGCONTEXT_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAHMARKINGCONTEXT_HPP

#include "gc/shenandoah/shenandoahMarkBitMap.hpp"
#include "gc/shenandoah/shenandoahSharedVariables.hpp"
#include "memory/allocation.hpp"
#include "memory/memRegion.hpp"
#include "oops/oopsHierarchy.hpp"

class ShenandoahObjToScanQueueSet;
class ShenandoahHeapRegion;

/**
 * Encapsulate a marking bitmap with the top-at-mark-start and top-bitmaps array.
 */
class ShenandoahMarkingContext : public CHeapObj<mtGC> {
private:
  // Marking bitmap
  ShenandoahMarkBitMap _mark_bit_map;

  HeapWord** const _top_bitmaps;
  HeapWord** const _top_at_mark_starts_base;
  HeapWord** const _top_at_mark_starts;

public:
  ShenandoahMarkingContext(MemRegion heap_region, MemRegion bitmap_region, size_t num_regions);

  /*
   * Marks the object. Returns true if the object has not been marked before and has
   * been marked by this thread. Returns false if the object has already been marked,
   * or if a competing thread succeeded in marking this object.
   */
  inline bool mark_strong(oop obj, bool& was_upgraded);
  inline bool mark_weak(oop obj);

  // Simple versions of marking accessors, to be used outside of marking (e.g. no possible concurrent updates)
  inline bool is_marked(oop obj) const;
  inline bool is_marked(HeapWord* raw_obj) const;
  inline bool is_marked_strong(oop obj) const;
  inline bool is_marked_strong(HeapWord* raw_obj) const;
  inline bool is_marked_weak(oop obj) const;
  inline bool is_marked_or_old(oop obj) const;
  inline bool is_marked_strong_or_old(oop obj) const;

  inline HeapWord* get_next_marked_addr(const HeapWord* addr, const HeapWord* limit) const;

  inline bool allocated_after_mark_start(const oop obj) const;
  inline bool allocated_after_mark_start(const HeapWord* addr) const;

  inline HeapWord* top_at_mark_start(const ShenandoahHeapRegion* r) const;
  inline void capture_top_at_mark_start(ShenandoahHeapRegion* r);
  inline void reset_top_at_mark_start(ShenandoahHeapRegion* r);
  void initialize_top_at_mark_start(ShenandoahHeapRegion* r);

  HeapWord* top_bitmap(ShenandoahHeapRegion* r);

  inline void reset_top_bitmap(ShenandoahHeapRegion *r);
  void clear_bitmap(ShenandoahHeapRegion *r);

  bool is_bitmap_clear() const;
  bool is_bitmap_range_within_region_clear(const HeapWord* start, const HeapWord* end) const;
};

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHMARKINGCONTEXT_HPP
