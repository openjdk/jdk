/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/z/zGeneration.inline.hpp"
#include "gc/z/zHeap.inline.hpp"
#include "gc/z/zLiveMap.inline.hpp"
#include "gc/z/zStat.hpp"
#include "gc/z/zUtils.hpp"
#include "logging/log.hpp"
#include "runtime/atomic.hpp"
#include "utilities/debug.hpp"
#include "utilities/powerOfTwo.hpp"
#include "utilities/spinYield.hpp"

static const ZStatCounter ZCounterMarkSeqNumResetContention("Contention", "Mark SeqNum Reset Contention", ZStatUnitOpsPerSecond);
static const ZStatCounter ZCounterMarkSegmentResetContention("Contention", "Mark Segment Reset Contention", ZStatUnitOpsPerSecond);

ZLiveMap::ZLiveMap(uint32_t object_max_count)
  : _segment_size((object_max_count == 1 ? 1u : (object_max_count / NumSegments)) * BitsPerObject),
    _segment_shift(log2i_exact(_segment_size)),
    _seqnum(0),
    _live_objects(0),
    _live_bytes(0),
    _segment_live_bits(0),
    _segment_claim_bits(0),
    _bitmap(0) {}

void ZLiveMap::initialize_bitmap() {
  if (_bitmap.size() == 0) {
    _bitmap.initialize(size_t(_segment_size) * size_t(NumSegments), false /* clear */);
  }
}

void ZLiveMap::reset(ZGenerationId id) {
  ZGeneration* const generation = ZGeneration::generation(id);
  const uint32_t seqnum_initializing = (uint32_t)-1;
  bool contention = false;

  SpinYield yielder(0, 0, 1000);

  // Multiple threads can enter here, make sure only one of them
  // resets the marking information while the others busy wait.
  for (uint32_t seqnum = Atomic::load_acquire(&_seqnum);
       seqnum != generation->seqnum();
       seqnum = Atomic::load_acquire(&_seqnum)) {

    if (seqnum != seqnum_initializing) {
      // No one has claimed initialization of the livemap yet
      if (Atomic::cmpxchg(&_seqnum, seqnum, seqnum_initializing) == seqnum) {
        // This thread claimed the initialization

        // Reset marking information
        _live_bytes = 0;
        _live_objects = 0;

        // Clear segment claimed/live bits
        segment_live_bits().clear();
        segment_claim_bits().clear();

        // We lazily initialize the bitmap the first time the page is marked, i.e.
        // a bit is about to be set for the first time.
        initialize_bitmap();

        assert(_seqnum == seqnum_initializing, "Invalid");

        // Make sure the newly reset marking information is ordered
        // before the update of the page seqnum, such that when the
        // up-to-date seqnum is load acquired, the bit maps will not
        // contain stale information.
        Atomic::release_store(&_seqnum, generation->seqnum());
        break;
      }
    }

    // Mark reset contention
    if (!contention) {
      // Count contention once
      ZStatInc(ZCounterMarkSeqNumResetContention);
      contention = true;

      log_trace(gc)("Mark seqnum reset contention, thread: " PTR_FORMAT " (%s), map: " PTR_FORMAT,
                    p2i(Thread::current()), ZUtils::thread_name(), p2i(this));
    }

    // "Yield" to allow the thread that's resetting the livemap to finish
    yielder.wait();
  }
}

void ZLiveMap::reset_segment(BitMap::idx_t segment) {
  bool contention = false;

  if (!claim_segment(segment)) {
    // Already claimed, wait for live bit to be set
    while (!is_segment_live(segment)) {
      // Mark reset contention
      if (!contention) {
        // Count contention once
        ZStatInc(ZCounterMarkSegmentResetContention);
        contention = true;

        log_trace(gc)("Mark segment reset contention, thread: " PTR_FORMAT " (%s), map: " PTR_FORMAT ", segment: %zu",
                      p2i(Thread::current()), ZUtils::thread_name(), p2i(this), segment);
      }
    }

    // Segment is live
    return;
  }

  // Segment claimed, clear it
  const BitMap::idx_t start_index = segment_start(segment);
  const BitMap::idx_t end_index   = segment_end(segment);
  if (_segment_size / BitsPerWord >= 32) {
    _bitmap.clear_large_range(start_index, end_index);
  } else {
    _bitmap.clear_range(start_index, end_index);
  }

  // Set live bit
  const bool success = set_segment_live(segment);
  assert(success, "Should never fail");
}
