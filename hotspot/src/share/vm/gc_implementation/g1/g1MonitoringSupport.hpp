/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_IMPLEMENTATION_G1_G1MONITORINGSUPPORT_HPP
#define SHARE_VM_GC_IMPLEMENTATION_G1_G1MONITORINGSUPPORT_HPP

#include "gc_implementation/shared/hSpaceCounters.hpp"

class G1CollectedHeap;
class G1SpaceMonitoringSupport;

// Class for monitoring logical spaces in G1.
// G1 defines a set of regions as a young
// collection (analogous to a young generation).
// The young collection is a logical generation
// with no fixed chunk (see space.hpp) reflecting
// the address space for the generation.  In addition
// to the young collection there is its complement
// the non-young collection that is simply the regions
// not in the young collection.  The non-young collection
// is treated here as a logical old generation only
// because the monitoring tools expect a generational
// heap.  The monitoring tools expect that a Space
// (see space.hpp) exists that describe the
// address space of young collection and non-young
// collection and such a view is provided here.
//
// This class provides interfaces to access
// the value of variables for the young collection
// that include the "capacity" and "used" of the
// young collection along with constant values
// for the minimum and maximum capacities for
// the logical spaces.  Similarly for the non-young
// collection.
//
// Also provided are counters for G1 concurrent collections
// and stop-the-world full heap collecitons.
//
// Below is a description of how "used" and "capactiy"
// (or committed) is calculated for the logical spaces.
//
// 1) The used space calculation for a pool is not necessarily
// independent of the others. We can easily get from G1 the overall
// used space in the entire heap, the number of regions in the young
// generation (includes both eden and survivors), and the number of
// survivor regions. So, from that we calculate:
//
//  survivor_used = survivor_num * region_size
//  eden_used     = young_region_num * region_size - survivor_used
//  old_gen_used  = overall_used - eden_used - survivor_used
//
// Note that survivor_used and eden_used are upper bounds. To get the
// actual value we would have to iterate over the regions and add up
// ->used(). But that'd be expensive. So, we'll accept some lack of
// accuracy for those two. But, we have to be careful when calculating
// old_gen_used, in case we subtract from overall_used more then the
// actual number and our result goes negative.
//
// 2) Calculating the used space is straightforward, as described
// above. However, how do we calculate the committed space, given that
// we allocate space for the eden, survivor, and old gen out of the
// same pool of regions? One way to do this is to use the used value
// as also the committed value for the eden and survivor spaces and
// then calculate the old gen committed space as follows:
//
//  old_gen_committed = overall_committed - eden_committed - survivor_committed
//
// Maybe a better way to do that would be to calculate used for eden
// and survivor as a sum of ->used() over their regions and then
// calculate committed as region_num * region_size (i.e., what we use
// to calculate the used space now). This is something to consider
// in the future.
//
// 3) Another decision that is again not straightforward is what is
// the max size that each memory pool can grow to. One way to do this
// would be to use the committed size for the max for the eden and
// survivors and calculate the old gen max as follows (basically, it's
// a similar pattern to what we use for the committed space, as
// described above):
//
//  old_gen_max = overall_max - eden_max - survivor_max
//
// Unfortunately, the above makes the max of each pool fluctuate over
// time and, even though this is allowed according to the spec, it
// broke several assumptions in the M&M framework (there were cases
// where used would reach a value greater than max). So, for max we
// use -1, which means "undefined" according to the spec.
//
// 4) Now, there is a very subtle issue with all the above. The
// framework will call get_memory_usage() on the three pools
// asynchronously. As a result, each call might get a different value
// for, say, survivor_num which will yield inconsistent values for
// eden_used, survivor_used, and old_gen_used (as survivor_num is used
// in the calculation of all three). This would normally be
// ok. However, it's possible that this might cause the sum of
// eden_used, survivor_used, and old_gen_used to go over the max heap
// size and this seems to sometimes cause JConsole (and maybe other
// clients) to get confused. There's not a really an easy / clean
// solution to this problem, due to the asynchrounous nature of the
// framework.

class G1MonitoringSupport : public CHeapObj {
  G1CollectedHeap* _g1h;
  VirtualSpace* _g1_storage_addr;

  // jstat performance counters
  //  incremental collections both fully and partially young
  CollectorCounters*   _incremental_collection_counters;
  //  full stop-the-world collections
  CollectorCounters*   _full_collection_counters;
  //  young collection set counters.  The _eden_counters,
  // _from_counters, and _to_counters are associated with
  // this "generational" counter.
  GenerationCounters*  _young_collection_counters;
  //  non-young collection set counters. The _old_space_counters
  // below are associated with this "generational" counter.
  GenerationCounters*  _non_young_collection_counters;
  // Counters for the capacity and used for
  //   the whole heap
  HSpaceCounters*      _old_space_counters;
  //   the young collection
  HSpaceCounters*      _eden_counters;
  //   the survivor collection (only one, _to_counters, is actively used)
  HSpaceCounters*      _from_counters;
  HSpaceCounters*      _to_counters;

  // It returns x - y if x > y, 0 otherwise.
  // As described in the comment above, some of the inputs to the
  // calculations we have to do are obtained concurrently and hence
  // may be inconsistent with each other. So, this provides a
  // defensive way of performing the subtraction and avoids the value
  // going negative (which would mean a very large result, given that
  // the parameter are size_t).
  static size_t subtract_up_to_zero(size_t x, size_t y) {
    if (x > y) {
      return x - y;
    } else {
      return 0;
    }
  }

 public:
  G1MonitoringSupport(G1CollectedHeap* g1h, VirtualSpace* g1_storage_addr);

  G1CollectedHeap* g1h() { return _g1h; }
  VirtualSpace* g1_storage_addr() { return _g1_storage_addr; }

  // Performance Counter accessors
  void update_counters();
  void update_eden_counters();

  CollectorCounters* incremental_collection_counters() {
    return _incremental_collection_counters;
  }
  CollectorCounters* full_collection_counters() {
    return _full_collection_counters;
  }
  GenerationCounters* non_young_collection_counters() {
    return _non_young_collection_counters;
  }
  HSpaceCounters*      old_space_counters() { return _old_space_counters; }
  HSpaceCounters*      eden_counters() { return _eden_counters; }
  HSpaceCounters*      from_counters() { return _from_counters; }
  HSpaceCounters*      to_counters() { return _to_counters; }

  // Monitoring support used by
  //   MemoryService
  //   jstat counters
  size_t overall_committed();
  size_t overall_used();

  size_t eden_space_committed();
  size_t eden_space_used();

  size_t survivor_space_committed();
  size_t survivor_space_used();

  size_t old_space_committed();
  size_t old_space_used();
};

#endif // SHARE_VM_GC_IMPLEMENTATION_G1_G1MONITORINGSUPPORT_HPP
