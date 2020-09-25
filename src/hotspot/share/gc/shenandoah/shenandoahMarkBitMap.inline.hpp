/*
 * Copyright (c) 2020, Red Hat, Inc. and/or its affiliates.
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

#ifndef SHARE_VM_GC_SHENANDOAH_SHENANDOAHMARKBITMAP_INLINE_HPP
#define SHARE_VM_GC_SHENANDOAH_SHENANDOAHMARKBITMAP_INLINE_HPP

#include "gc/shenandoah/shenandoahMarkBitMap.hpp"
#include "utilities/bitMap.inline.hpp"

inline size_t ShenandoahMarkBitMap::address_to_index(const HeapWord* addr) const {
  return (pointer_delta(addr, _covered.start()) << 1) >> _shift;
}

inline HeapWord* ShenandoahMarkBitMap::index_to_address(size_t offset) const {
  return _covered.start() + ((offset >> 1) << _shift);
}

inline bool ShenandoahMarkBitMap::is_marked_strong(HeapWord* addr)  const {
  check_mark(addr);
  return _bit_map.at(address_to_index(addr));
}

inline bool ShenandoahMarkBitMap::mark_strong(HeapWord* addr) {
  check_mark(addr);
  return _bit_map.par_set_bit(address_to_index(addr));
}

inline bool ShenandoahMarkBitMap::is_marked_final(HeapWord* addr) const {
  check_mark(addr);
  return _bit_map.at(address_to_index(addr) + 1);
}

inline bool ShenandoahMarkBitMap::mark_final(HeapWord* addr) {
  check_mark(addr);
  if (is_marked_strong(addr)) {
    return false;
  }
  return _bit_map.par_set_bit(address_to_index(addr) + 1);
}

#endif // SHARE_VM_GC_SHENANDOAH_SHENANDOAHMARKBITMAP_INLINE_HPP
