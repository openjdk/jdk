/*
 * Copyright (c) 2013, 2018, Red Hat, Inc. All rights reserved.
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

#ifndef SHARE_VM_GC_SHENANDOAH_SHENANDOAHHEAPREGIONSET_HPP
#define SHARE_VM_GC_SHENANDOAH_SHENANDOAHHEAPREGIONSET_HPP

#include "memory/allocation.hpp"
#include "gc/shenandoah/shenandoahHeap.hpp"
#include "gc/shenandoah/shenandoahHeapRegion.hpp"

class ShenandoahHeapRegionSet;

class ShenandoahHeapRegionSetIterator : public StackObj {
private:
  const ShenandoahHeapRegionSet* _set;
  ShenandoahHeap* const _heap;

  DEFINE_PAD_MINUS_SIZE(0, DEFAULT_CACHE_LINE_SIZE, sizeof(volatile jint));
  volatile jint _current_index;
  DEFINE_PAD_MINUS_SIZE(1, DEFAULT_CACHE_LINE_SIZE, 0);

  // No implicit copying: iterators should be passed by reference to capture the state
  ShenandoahHeapRegionSetIterator(const ShenandoahHeapRegionSetIterator& that);
  ShenandoahHeapRegionSetIterator& operator=(const ShenandoahHeapRegionSetIterator& o);

public:
  ShenandoahHeapRegionSetIterator(const ShenandoahHeapRegionSet* const set);

  // Reset existing iterator to new set
  void reset(const ShenandoahHeapRegionSet* const set);

  // MT version
  ShenandoahHeapRegion* claim_next();

  // Single-thread version
  ShenandoahHeapRegion* next();
};

class ShenandoahHeapRegionSet : public CHeapObj<mtGC> {
  friend class ShenandoahHeap;
private:
  ShenandoahHeap* const _heap;
  size_t const          _map_size;
  size_t const          _region_size_bytes_shift;
  jbyte* const          _set_map;
  // Bias set map's base address for fast test if an oop is in set
  jbyte* const          _biased_set_map;
  size_t                _region_count;

public:
  ShenandoahHeapRegionSet();
  ~ShenandoahHeapRegionSet();

  // Add region to set
  void add_region(ShenandoahHeapRegion* r);
  bool add_region_check_for_duplicates(ShenandoahHeapRegion* r);

  // Remove region from set
  void remove_region(ShenandoahHeapRegion* r);

  size_t count()  const { return _region_count; }
  bool is_empty() const { return _region_count == 0; }

  inline bool is_in(ShenandoahHeapRegion* r) const;
  inline bool is_in(size_t region_number)    const;
  inline bool is_in(HeapWord* p)             const;

  void print_on(outputStream* out) const;

  void clear();

private:
  jbyte* biased_map_address() const {
    return _biased_set_map;
  }
};

#endif //SHARE_VM_GC_SHENANDOAH_SHENANDOAHHEAPREGIONSET_HPP
