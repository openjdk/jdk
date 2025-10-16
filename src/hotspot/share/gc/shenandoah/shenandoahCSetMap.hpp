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
 */

#ifndef SHARE_VM_GC_SHENANDOAH_SHENANDOAHCSETMAP_HPP
#define SHARE_VM_GC_SHENANDOAH_SHENANDOAHCSETMAP_HPP

#include "oops/oopsHierarchy.hpp"

class ShenandoahHeapRegion;

enum class CSetState : char {
  NOT_IN_CSET = 0,
  IN_CSET = 1,
  FWDTABLE_COMPACT = 2,
  FWDTABLE_WIDE = 3
};

class ShenandoahCSetMap {
  friend class ShenandoahCollectionSet;

  size_t          _region_size_bytes_shift;
  CSetState*      _cset_map;
  // Bias cset map's base address for fast test if an oop is in cset
  CSetState*      _biased_cset_map;

  char* cset_map() const {
    return reinterpret_cast<char*>(_cset_map);
  }
  char* biased_cset_map() const {
    return reinterpret_cast<char*>(_biased_cset_map);
  }
  ShenandoahCSetMap(size_t region_size_bytes_shift, char* map, char* heap_base) :
    _region_size_bytes_shift(region_size_bytes_shift),
    _cset_map(reinterpret_cast<CSetState*>(map + ((uintx)heap_base >> region_size_bytes_shift))),
    _biased_cset_map(reinterpret_cast<CSetState*>(map)) {}

public:
  ShenandoahCSetMap() : _region_size_bytes_shift(0), _cset_map(nullptr), _biased_cset_map(nullptr) {}

  inline CSetState cset_state(ShenandoahHeapRegion* region) const;
  inline CSetState cset_state(size_t region_idx)            const;
  inline CSetState cset_state(oop obj)                      const;
  inline CSetState cset_state(void* loc)                    const;

  inline bool is_in(CSetState state) const;
  inline bool is_in(ShenandoahHeapRegion* r) const;
  inline bool is_in(size_t region_idx)       const;
  inline bool is_in(oop obj)                 const;
  inline bool is_in_loc(void* loc)           const;

  inline bool use_forward_table(CSetState) const;
  inline bool use_forward_table(oop obj) const;
  inline bool use_forward_table(ShenandoahHeapRegion* r) const;

};

#endif // SHARE_VM_GC_SHENANDOAH_SHENANDOAHCSETMAP_HPP
