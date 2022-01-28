/*
 * Copyright (c) 2021, Huawei Technologies Co. Ltd. All rights reserved.
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

#ifndef SHARE_GC_G1_G1HEAPREGIONCHUNK_HPP
#define SHARE_GC_G1_G1HEAPREGIONCHUNK_HPP

#include "runtime/atomic.hpp"
#include "utilities/bitMap.hpp"
#include "utilities/globalDefinitions.hpp"

class G1CMBitMap;
class HeapRegion;

class G1HeapRegionChunk {
  const uint _chunk_size;
  HeapRegion* _region;
  uint _chunk_idx;
  const G1CMBitMap* const _bitmap;

  // _start < _first_obj_in_chunk <= _limit <= _next_obj_in_region
  HeapWord * _start;
  HeapWord * _limit;
  HeapWord * _first_obj_in_chunk;
  HeapWord * _next_obj_in_region;

  bool _include_first_obj_in_region;
  bool _include_last_obj_in_region;

public:
  G1HeapRegionChunk(HeapRegion* region, uint chunk_idx, uint chunk_size, const G1CMBitMap* const bitmap);

  // All objects that failed evacuation has been marked in the prev bitmap.
  // Use the bitmap to apply the above closure to all failing objects.
  template<typename ApplyToMarkedClosure>
  void apply_to_marked_objects(ApplyToMarkedClosure* closure);

  HeapRegion* heap_region() const {
    return _region;
  }

  HeapWord* first_obj_in_chunk() const {
    return _first_obj_in_chunk;
  }

  HeapWord* next_obj_in_region() const {
    return _next_obj_in_region;
  }

  bool empty() const {
    return _first_obj_in_chunk >= _limit;
  }

  bool include_first_obj_in_region() const {
    return _include_first_obj_in_region;
  }

  bool include_last_obj_in_region() const {
    return _include_last_obj_in_region;
  }
};

class G1HeapRegionChunkClosure {
public:
  // Typically called on each region until it returns true.
  virtual void do_heap_region_chunk(G1HeapRegionChunk* c) = 0;
};

class G1HeapRegionChunksClaimer {
  const uint _chunk_num;
  const uint _chunk_size;
  const uint _region_idx;
  CHeapBitMap _chunks;

public:
  G1HeapRegionChunksClaimer(uint region_idx, bool region_ready = false);

  bool claim_chunk(uint chunk_idx);

  uint chunk_size() {
    return _chunk_size;
  }
  uint chunk_num() {
    return _chunk_num;
  }
};

// Iterate through chunks of regions, for each region do single preparation.
class G1ScanChunksInHeapRegionClosure : public HeapRegionClosure {
  G1HeapRegionChunksClaimer** _chunk_claimers;
  G1HeapRegionChunkClosure* _closure;
  uint _worker_id;
  const G1CMBitMap* const _bitmap;

public:
  G1ScanChunksInHeapRegionClosure(G1HeapRegionChunksClaimer** chunk_claimers,
                                  G1HeapRegionChunkClosure* closure,
                                  uint worker_id);

  bool do_heap_region(HeapRegion* r) override;
};

#endif //SHARE_GC_G1_G1HEAPREGIONCHUNK_HPP