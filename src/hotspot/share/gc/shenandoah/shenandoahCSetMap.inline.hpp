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

#ifndef SHARE_VM_GC_SHENANDOAH_SHENANDOAHCSETMAP_INLINE_HPP
#define SHARE_VM_GC_SHENANDOAH_SHENANDOAHCSETMAP_INLINE_HPP

#include "shenandoahCSetMap.hpp"
#include "gc/shenandoah/shenandoahCSetMap.hpp"
#include "oops/oopsHierarchy.hpp"
#include "utilities/globalDefinitions.hpp"

inline CSetState ShenandoahCSetMap::cset_state(size_t region_idx) const {
  return _cset_map[region_idx];
}
inline CSetState ShenandoahCSetMap::cset_state(ShenandoahHeapRegion* region) const {
  return cset_state(region->index());
}

inline CSetState ShenandoahCSetMap::cset_state(void* loc) const {
  uintptr_t index = reinterpret_cast<uintptr_t>(loc) >> _region_size_bytes_shift;
  // no need to subtract the bottom of the heap from p,
  // _biased_cset_map is biased
  return _biased_cset_map[index];
}

inline CSetState ShenandoahCSetMap::cset_state(oop obj) const {
  return cset_state(cast_from_oop<void*>(obj));
}

inline bool ShenandoahCSetMap::is_in(CSetState state) const {
  return state >= CSetState::IN_CSET;
}

inline bool ShenandoahCSetMap::is_in(ShenandoahHeapRegion* region) const {
  return is_in(cset_state(region));
}

inline bool ShenandoahCSetMap::is_in(size_t region_idx) const {
  return is_in(cset_state(region_idx));
}

inline bool ShenandoahCSetMap::is_in(oop obj) const {
  return is_in(cset_state(obj));
}

inline bool ShenandoahCSetMap::is_in_loc(void* loc) const {
  return is_in(cset_state(loc));
}

inline bool ShenandoahCSetMap::use_forward_table(CSetState state) const {
  return state >= CSetState::FWDTABLE_COMPACT;
}

inline bool ShenandoahCSetMap::use_forward_table(oop obj) const {
  return use_forward_table(cset_state(obj));
}

inline bool ShenandoahCSetMap::use_forward_table(ShenandoahHeapRegion* region) const {
  return use_forward_table(cset_state(region));
}

#endif // SHARE_VM_GC_SHENANDOAH_SHENANDOAHCSETMAP_INLINE_HPP
