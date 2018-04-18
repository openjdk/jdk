/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_G1_G1CONCURRENTMARKBITMAP_HPP
#define SHARE_VM_GC_G1_G1CONCURRENTMARKBITMAP_HPP

#include "gc/g1/g1RegionToSpaceMapper.hpp"
#include "memory/memRegion.hpp"
#include "oops/oopsHierarchy.hpp"
#include "utilities/bitMap.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"

class G1CMBitMap;
class G1CMTask;
class G1ConcurrentMark;
class HeapRegion;

// Closure for iteration over bitmaps
class G1CMBitMapClosure {
  G1ConcurrentMark* const _cm;
  G1CMTask* const _task;
public:
  G1CMBitMapClosure(G1CMTask *task, G1ConcurrentMark* cm) : _task(task), _cm(cm) { }

  bool do_addr(HeapWord* const addr);
};

class G1CMBitMapMappingChangedListener : public G1MappingChangedListener {
  G1CMBitMap* _bm;
public:
  G1CMBitMapMappingChangedListener() : _bm(NULL) {}

  void set_bitmap(G1CMBitMap* bm) { _bm = bm; }

  virtual void on_commit(uint start_idx, size_t num_regions, bool zero_filled);
};

// A generic mark bitmap for concurrent marking.  This is essentially a wrapper
// around the BitMap class that is based on HeapWords, with one bit per (1 << _shifter) HeapWords.
class G1CMBitMap {
  MemRegion _covered;    // The heap area covered by this bitmap.

  const int _shifter;    // Shift amount from heap index to bit index in the bitmap.

  BitMapView _bm;        // The actual bitmap.

  G1CMBitMapMappingChangedListener _listener;

  inline void check_mark(HeapWord* addr) NOT_DEBUG_RETURN;

  // Convert from bit offset to address.
  HeapWord* offset_to_addr(size_t offset) const {
    return _covered.start() + (offset << _shifter);
  }
  // Convert from address to bit offset.
  size_t addr_to_offset(const HeapWord* addr) const {
    return pointer_delta(addr, _covered.start()) >> _shifter;
  }
public:
  static size_t compute_size(size_t heap_size);
  // Returns the amount of bytes on the heap between two marks in the bitmap.
  static size_t mark_distance();
  // Returns how many bytes (or bits) of the heap a single byte (or bit) of the
  // mark bitmap corresponds to. This is the same as the mark distance above.
  static size_t heap_map_factor() {
    return mark_distance();
  }

  G1CMBitMap() : _covered(), _bm(), _shifter(LogMinObjAlignment), _listener() { _listener.set_bitmap(this); }

  // Initializes the underlying BitMap to cover the given area.
  void initialize(MemRegion heap, G1RegionToSpaceMapper* storage);

  // Read marks
  bool is_marked(oop obj) const;
  bool is_marked(HeapWord* addr) const {
    assert(_covered.contains(addr),
           "Address " PTR_FORMAT " is outside underlying space from " PTR_FORMAT " to " PTR_FORMAT,
           p2i(addr), p2i(_covered.start()), p2i(_covered.end()));
    return _bm.at(addr_to_offset(addr));
  }

  // Apply the closure to the addresses that correspond to marked bits in the bitmap.
  inline bool iterate(G1CMBitMapClosure* cl, MemRegion mr);

  // Return the address corresponding to the next marked bit at or after
  // "addr", and before "limit", if "limit" is non-NULL.  If there is no
  // such bit, returns "limit" if that is non-NULL, or else "endWord()".
  inline HeapWord* get_next_marked_addr(const HeapWord* addr,
                                        const HeapWord* limit) const;

  void print_on_error(outputStream* st, const char* prefix) const;

  // Write marks.
  inline void mark(HeapWord* addr);
  inline void clear(HeapWord* addr);
  inline void clear(oop obj);
  inline bool par_mark(HeapWord* addr);
  inline bool par_mark(oop obj);

  void clear_range(MemRegion mr);
  void clear_region(HeapRegion* hr);
};

#endif // SHARE_VM_GC_G1_G1CONCURRENTMARKBITMAP_HPP
