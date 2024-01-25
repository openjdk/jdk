/*
 * Copyright (c) 2015, 2019, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "gc/x/xHeap.inline.hpp"
#include "gc/x/xLiveMap.inline.hpp"
#include "gc/x/xStat.hpp"
#include "gc/x/xThread.inline.hpp"
#include "logging/log.hpp"
#include "runtime/atomic.hpp"
#include "utilities/debug.hpp"
#include "utilities/powerOfTwo.hpp"

static const XStatCounter XCounterMarkSeqNumResetContention("Contention", "Mark SeqNum Reset Contention", XStatUnitOpsPerSecond);
static const XStatCounter XCounterMarkSegmentResetContention("Contention", "Mark Segment Reset Contention", XStatUnitOpsPerSecond);

static size_t bitmap_size(uint32_t size, size_t nsegments) {
  // We need at least one bit per segment
  return MAX2<size_t>(size, nsegments) * 2;
}

XLiveMap::XLiveMap(uint32_t size) :
    _seqnum(0),
    _live_objects(0),
    _live_bytes(0),
    _segment_live_bits(0),
    _segment_claim_bits(0),
    _bitmap(bitmap_size(size, nsegments)),
    _segment_shift(exact_log2(segment_size())) {}

void XLiveMap::reset(size_t index) {
  const uint32_t seqnum_initializing = (uint32_t)-1;
  bool contention = false;

  // Multiple threads can enter here, make sure only one of them
  // resets the marking information while the others busy wait.
  for (uint32_t seqnum = Atomic::load_acquire(&_seqnum);
       seqnum != XGlobalSeqNum;
       seqnum = Atomic::load_acquire(&_seqnum)) {
    if ((seqnum != seqnum_initializing) &&
        (Atomic::cmpxchg(&_seqnum, seqnum, seqnum_initializing) == seqnum)) {
      // Reset marking information
      _live_bytes = 0;
      _live_objects = 0;

      // Clear segment claimed/live bits
      segment_live_bits().clear();
      segment_claim_bits().clear();

      assert(_seqnum == seqnum_initializing, "Invalid");

      // Make sure the newly reset marking information is ordered
      // before the update of the page seqnum, such that when the
      // up-to-date seqnum is load acquired, the bit maps will not
      // contain stale information.
      Atomic::release_store(&_seqnum, XGlobalSeqNum);
      break;
    }

    // Mark reset contention
    if (!contention) {
      // Count contention once
      XStatInc(XCounterMarkSeqNumResetContention);
      contention = true;

      log_trace(gc)("Mark seqnum reset contention, thread: " PTR_FORMAT " (%s), map: " PTR_FORMAT ", bit: " SIZE_FORMAT,
                    XThread::id(), XThread::name(), p2i(this), index);
    }
  }
}

void XLiveMap::reset_segment(BitMap::idx_t segment) {
  bool contention = false;

  if (!claim_segment(segment)) {
    // Already claimed, wait for live bit to be set
    while (!is_segment_live(segment)) {
      // Mark reset contention
      if (!contention) {
        // Count contention once
        XStatInc(XCounterMarkSegmentResetContention);
        contention = true;

        log_trace(gc)("Mark segment reset contention, thread: " PTR_FORMAT " (%s), map: " PTR_FORMAT ", segment: " SIZE_FORMAT,
                      XThread::id(), XThread::name(), p2i(this), segment);
      }
    }

    // Segment is live
    return;
  }

  // Segment claimed, clear it
  const BitMap::idx_t start_index = segment_start(segment);
  const BitMap::idx_t end_index   = segment_end(segment);
  if (segment_size() / BitsPerWord >= 32) {
    _bitmap.clear_large_range(start_index, end_index);
  } else {
    _bitmap.clear_range(start_index, end_index);
  }

  // Set live bit
  const bool success = set_segment_live(segment);
  assert(success, "Should never fail");
}

void XLiveMap::resize(uint32_t size) {
  const size_t new_bitmap_size = bitmap_size(size, nsegments);
  if (_bitmap.size() != new_bitmap_size) {
    _bitmap.reinitialize(new_bitmap_size, false /* clear */);
    _segment_shift = exact_log2(segment_size());
  }
}
