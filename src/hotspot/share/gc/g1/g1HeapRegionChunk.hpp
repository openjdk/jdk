/*
 * Copyright (c) 2022, Huawei Technologies Co. Ltd. All rights reserved.
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
  // chunk index in a region, zero based.
  uint _chunk_idx;
  const G1CMBitMap* const _bitmap;

  // _start < _first_obj_in_chunk <= _limit <= _next_obj_in_region
  HeapWord * _start;
  HeapWord * _limit;
  HeapWord * _first_obj_in_chunk;
  HeapWord * _next_obj_in_region;

  bool _include_first_obj_in_region;

public:
  G1HeapRegionChunk(HeapRegion* region, uint chunk_idx, uint chunk_size, const G1CMBitMap* const bitmap);

  // All objects that failed evacuation has been marked in the prev bitmap.
  // Use the bitmap to apply the above closure to all failing objects.
  template<typename ApplyToMarkedClosure>
  void apply_to_marked_objects(ApplyToMarkedClosure* closure);

  HeapRegion* heap_region() const { return _region;}

  HeapWord* first_obj_in_chunk() const { return _first_obj_in_chunk; }

  HeapWord* next_obj_in_region() const { return _next_obj_in_region; }

  bool empty() const { return _first_obj_in_chunk >= _limit; }

  bool include_first_obj_in_region() const { return _include_first_obj_in_region; }
};

class G1HeapRegionChunkClosure {
public:
  virtual void do_heap_region_chunk(G1HeapRegionChunk* c) = 0;
};

class G1ScanChunksInHeapRegions {
  const G1CMBitMap* const _bitmap;
  CHeapBitMap _chunks;
  const uint* _evac_failure_regions;
  uint _chunks_per_region;
  uint _chunk_size;

  bool claim_chunk(uint id);
  void process_chunk(G1HeapRegionChunkClosure* chunk_closure, uint chunk_id, uint worker_id);

public:
  G1ScanChunksInHeapRegions();
  void initialize(const uint* evac_failure_regions, uint evac_failure_regions_length, uint num_workers, const char* task_name);

  void par_iterate_chunks_in_regions(G1HeapRegionChunkClosure* chunk_closure, const uint worker_id);
};

#endif //SHARE_GC_G1_G1HEAPREGIONCHUNK_HPP
