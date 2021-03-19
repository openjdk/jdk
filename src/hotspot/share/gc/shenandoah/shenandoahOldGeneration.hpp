/*
 * Copyright (c) 2021, Amazon.com, Inc. or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_SHENANDOAH_SHENANDOAHOLDGENERATION_HPP
#define SHARE_VM_GC_SHENANDOAH_SHENANDOAHOLDGENERATION_HPP

#include "gc/shenandoah/shenandoahGeneration.hpp"

class ShenandoahHeapRegion;
class ShenandoahHeapRegionClosure;

class ShenandoahOldGeneration : public ShenandoahGeneration {
 public:
  ShenandoahOldGeneration(uint max_queues, size_t max_capacity, size_t soft_max_capacity);

  const char* name() const;

  bool contains(ShenandoahHeapRegion* region) const;
  void parallel_heap_region_iterate(ShenandoahHeapRegionClosure* cl);

  void heap_region_iterate(ShenandoahHeapRegionClosure* cl);

  void set_concurrent_mark_in_progress(bool in_progress);

  // We leave the SATB barrier on for the entirety of the old generation
  // marking phase. In some cases, this can cause a write to a perfectly
  // reachable oop to enqueue a pointer that later becomes garbage (because
  // it points at an object in the collection set, for example). There are
  // also cases where the referent of a weak reference ends up in the SATB
  // and is later collected. In these cases the oop in the SATB buffer becomes
  // invalid and the _next_ cycle will crash during its marking phase. To
  // avoid this problem, we "purge" the SATB buffers during the final update
  // references phase if (and only if) an old generation mark is in progress.
  // At this stage we can safely determine if any of the oops in the SATB
  // buffer belong to trashed regions (before they are recycled). As it
  // happens, flushing a SATB queue also filters out oops which have already
  // been marked - which is the case for anything that is being evacuated
  // from the collection set.
  //
  // Alternatively, we could inspect the state of the heap and the age of the
  // object at the barrier, but we reject this approach because it is likely
  // the performance impact would be too severe.
  void purge_satb_buffers(bool abandon);
 protected:
  bool is_concurrent_mark_in_progress();
};


#endif //SHARE_VM_GC_SHENANDOAH_SHENANDOAHOLDGENERATION_HPP
