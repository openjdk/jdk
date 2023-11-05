/*
 * Copyright (c) 2012, 2019, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_G1_G1EVACFAILURE_HPP
#define SHARE_GC_G1_G1EVACFAILURE_HPP

#include "gc/shared/workerThread.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/bitMap.hpp"

class G1CollectedHeap;
class G1ConcurrentMark;
class G1EvacFailureRegions;

// Task to fixup self-forwarding pointers within the objects installed as a result
// of an evacuation failure.
class G1RemoveSelfForwardsTask : public WorkerTask {
  G1CollectedHeap* _g1h;
  G1ConcurrentMark* _cm;

  G1EvacFailureRegions* _evac_failure_regions;
  CHeapBitMap _chunk_bitmap;

  uint _num_chunks_per_region;
  uint _num_evac_fail_regions;
  size_t _chunk_size;

  bool claim_chunk(uint chunk_idx) {
    return _chunk_bitmap.par_set_bit(chunk_idx);
  }

  void process_chunk(uint worker_id, uint chunk_idx);

public:
  explicit G1RemoveSelfForwardsTask(G1EvacFailureRegions* evac_failure_regions);

  void work(uint worker_id);
};

#endif // SHARE_GC_G1_G1EVACFAILURE_HPP
