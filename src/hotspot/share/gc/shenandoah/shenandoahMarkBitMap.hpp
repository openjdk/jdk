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

#ifndef SHARE_VM_GC_SHENANDOAH_SHENANDOAHMARKBITMAP_HPP
#define SHARE_VM_GC_SHENANDOAH_SHENANDOAHMARKBITMAP_HPP

#include "memory/memRegion.hpp"
#include "utilities/bitMap.hpp"
#include "utilities/globalDefinitions.hpp"

class ShenandoahMarkBitMap {
private:
  int const _shift;
  MemRegion _covered;
  BitMapView _bit_map;

  inline size_t address_to_index(const HeapWord* addr) const;
  inline HeapWord* index_to_address(size_t offset) const;

  void check_mark(HeapWord* addr) const NOT_DEBUG_RETURN;

public:
  static size_t compute_size(size_t heap_size);
  // Returns the amount of bytes on the heap between two marks in the bitmap.
  static size_t mark_distance();
  // Returns how many bytes (or bits) of the heap a single byte (or bit) of the
  // mark bitmap corresponds to. This is the same as the mark distance above.
  static size_t heap_map_factor() {
    return mark_distance();
  }

  ShenandoahMarkBitMap(MemRegion heap, MemRegion storage);

  // Return true if the word is marked strong.
  inline bool is_marked_strong(HeapWord* w)  const;

  // Mark word as 'strong' if it hasn't been marked strong yet.
  // Return true if the word has been marked strong, false if it has already been
  // marked strong or if another thread has beat us by marking it
  // strong.
  // Words that have been marked final before or by a concurrent thread will be
  // upgraded to strong. In this case, this method also returns true.
  inline bool mark_strong(HeapWord* w);

  // Return true if the word is marked final.
  inline bool is_marked_final(HeapWord* w) const;

  // Mark word as 'final' if it hasn't been marked final or strong yet.
  // Return true if the word has been marked final, false if it has already been
  // marked strong or final or if another thread has beat us by marking it
  // strong or final.
  inline bool mark_final(HeapWord* w);

  // Return the address corresponding to the next marked bit at or after
  // "addr", and before "limit", if "limit" is non-NULL.  If there is no
  // such bit, returns "limit" if that is non-NULL, or else "endWord()".
  HeapWord* get_next_marked_addr(const HeapWord* addr,
                                 const HeapWord* limit) const;

  void clear_range_large(MemRegion mr);

};

#endif // SHARE_VM_GC_SHENANDOAH_SHENANDOAHMARKBITMAP_HPP
