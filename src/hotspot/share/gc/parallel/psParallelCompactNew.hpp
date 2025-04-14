/*
 * Copyright (c) 2005, 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
 * * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
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

#ifndef SHARE_GC_PARALLEL_PSPARALLELCOMPACTNEW_HPP
#define SHARE_GC_PARALLEL_PSPARALLELCOMPACTNEW_HPP

#include "gc/parallel/mutableSpace.hpp"
#include "gc/parallel/objectStartArray.hpp"
#include "gc/parallel/parallelScavengeHeap.hpp"
#include "gc/parallel/parMarkBitMap.hpp"
#include "gc/shared/collectedHeap.hpp"
#include "gc/shared/collectorCounters.hpp"
#include "gc/shared/referenceProcessor.hpp"
#include "gc/shared/taskTerminator.hpp"
#include "oops/oop.hpp"
#include "runtime/atomic.hpp"
#include "runtime/orderAccess.hpp"

class ParallelScavengeHeap;
class PSAdaptiveSizePolicy;
class PSYoungGen;
class PSOldGen;
class ParCompactionManagerNew;
class PSParallelCompactNew;
class ParallelOldTracer;
class STWGCTimer;

class SpaceInfoNew
{
public:
  MutableSpace* space() const { return _space; }

  // The start array for the (generation containing the) space, or null if there
  // is no start array.
  ObjectStartArray* start_array() const { return _start_array; }

  void set_space(MutableSpace* s)           { _space = s; }
  void set_start_array(ObjectStartArray* s) { _start_array = s; }

private:
  MutableSpace*     _space;
  ObjectStartArray* _start_array;
};

// The Parallel compaction collector is a stop-the-world garbage collector that
// does parts of the collection using parallel threads.  The collection includes
// the tenured generation and the young generation.
//
// A collection consists of the following phases.
//
//      - marking phase
//      - summary phase (single-threaded)
//      - forward (to new address) phase
//      - adjust pointers phase
//      - compacting phase
//      - clean up phase
//
// Roughly speaking these phases correspond, respectively, to
//
//      - mark all the live objects
//      - set-up temporary regions to enable parallelism in following phases
//      - calculate the destination of each object at the end of the collection
//      - adjust pointers to reflect new destination of objects
//      - move the objects to their destination
//      - update some references and reinitialize some variables
//
// A space that is being collected is divided into regions and with each region
// is associated an object of type PCRegionData. Regions are targeted to be of
// a mostly uniform size, but if an object would cross a region boundary, then
// the boundary is adjusted to be after the end of that object.
//
// The marking phase does a complete marking of all live objects in the heap.
// The marking phase uses multiple GC threads and marking is done in a bit
// array of type ParMarkBitMap.  The marking of the bit map is done atomically.
//
// The summary phase sets up the regions, such that region covers roughly
// uniform memory regions (currently same size as SpaceAlignment). However,
// if that would result in an object crossing a region boundary, then
// the upper bounds is adjusted such that the region ends after that object.
// This way we can ensure that a GC worker thread can fully 'own' a region
// during the forwarding, adjustment and compaction phases, without worrying
// about other threads messing with parts of the object. The summary phase
// also sets up an alternative set of regions, where each region covers
// a single space. This is used for a serial compaction mode which achieves
// maximum compaction at the expense of parallelism during the forwarding
// compaction phases.
//
// The forwarding phase calculates the new address of each live
// object and records old-addr-to-new-addr association. It does this using
// multiple GC threads. Each thread 'claims' a source region and appends it to a
// local work-list. The region is also set as the current compaction region
// for that thread. All live objects in the region are then visited and its
// new location calculated by tracking the compaction point in the compaction
// region. Once the source region is exhausted, the next source region is
// claimed from the global pool and appended to the end of the local work-list.
// Once the compaction region is exhausted, the top of the old compaction region
// is recorded, and the next compaction region is fetched from the front of the
// local work-list (which is guaranteed to already have finished processing, or
// is the same as the source region). This way, each worker forms a local
// list of regions in which the worker can compact as if it were a serial
// compaction.
//
// The adjust pointers phase remaps all pointers to reflect the new address of each
// object. Again, this uses multiple GC worker threads. Each thread claims
// a region, processes all references in all live objects of that region. Then
// the thread proceeds to claim the next region from the global pool, until
// all regions have been processed.
//
// The compaction phase moves objects to their new location. Again, this uses
// multiple GC worker threads. Each worker processes the local work-list that
// has been set-up during the forwarding phase and processes it from bottom
// to top, copying each live object to its new location (which is guaranteed
// to be lower in that threads parts of the heap, and thus would never overwrite
// other objects).
//
// This algorithm will usually leave gaps of non-fillable memory at the end
// of regions, and potentially whole empty regions at the end of compaction.
// The post-compaction phase fills those gaps with filler objects to ensure
// that the heap remains parsable.
//
// In some situations, this inefficiency of leaving gaps can lead to a
// situation where after a full GC, it is still not possible to satisfy an
// allocation, even though there should be enough memory available. When
// that happens, the collector switches to a serial mode, where we only
// have 4 regions which correspond exaxtly to the 4 spaces, and the forwarding
// and compaction phases are executed using only a single thread. This
// achieves maximum compaction. This serial mode is also invoked when
// System.gc() is called *and* UseMaximumCompactionOnSystemGC is set to
// true (which is the default), or when the number of full GCs exceeds
// the HeapMaximumCompactionInterval.
//
// Possible improvements to the algorithm include:
// - Identify and ignore a 'dense prefix'. This requires that we collect
//   liveness data during marking, or that we scan the prefix object-by-object
//   during the summary phase.
// - When an object does not fit into a remaining gap of a region, and the
//   object is rather large, we could attempt to forward/compact subsequent
//   objects 'around' that large object in an attempt to minimize the
//   resulting gap. This could be achieved by reconfiguring the regions
//   to exclude the large object.
// - Instead of finding out *after* the whole compaction that an allocation
//   can still not be satisfied, and then re-running the whole compaction
//   serially, we could determine that after the forwarding phase, and
//   re-do only forwarding serially, thus avoiding running marking,
//   adjusting references and compaction twice.
class PCRegionData /*: public CHeapObj<mtGC> */ {
  // A region index
  size_t const _idx;

  // The start of the region
  HeapWord* const _bottom;
  // The top of the region. (first word after last live object in containing space)
  HeapWord* const _top;
  // The end of the region (first word after last word of the region)
  HeapWord* const _end;

  // The next compaction address
  HeapWord* _new_top;

  // Points to the next region in the GC-worker-local work-list
  PCRegionData* _local_next;

  // Parallel workers claiming protocol, used during adjust-references phase.
  volatile bool _claimed;

public:

  PCRegionData(size_t idx, HeapWord* bottom, HeapWord* top, HeapWord* end) :
    _idx(idx), _bottom(bottom), _top(top), _end(end), _new_top(bottom),
          _local_next(nullptr), _claimed(false) {}

  size_t idx() const { return _idx; };

  HeapWord* bottom() const { return _bottom; }
  HeapWord* top() const { return _top; }
  HeapWord* end()   const { return _end;   }

  PCRegionData*  local_next() const { return _local_next; }
  PCRegionData** local_next_addr() { return &_local_next; }

  HeapWord* new_top() const {
    return _new_top;
  }
  void set_new_top(HeapWord* new_top) {
    _new_top = new_top;
  }

  bool contains(oop obj) {
    auto* obj_start = cast_from_oop<HeapWord*>(obj);
    HeapWord* obj_end = obj_start + obj->size();
    return _bottom <= obj_start && obj_start < _end && _bottom < obj_end && obj_end <= _end;
  }

  bool claim() {
    bool claimed =  _claimed;
    if (claimed) {
      return false;
    }
    return !Atomic::cmpxchg(&_claimed, false, true);
  }
};

class PSParallelCompactNew : AllStatic {
public:
  typedef enum {
    old_space_id, eden_space_id,
    from_space_id, to_space_id, last_space_id
  } SpaceId;

public:
  class IsAliveClosure: public BoolObjectClosure {
  public:
    bool do_object_b(oop p) final;
  };

private:
  static STWGCTimer           _gc_timer;
  static ParallelOldTracer    _gc_tracer;
  static elapsedTimer         _accumulated_time;
  static unsigned int         _maximum_compaction_gc_num;
  static CollectorCounters*   _counters;
  static ParMarkBitMap        _mark_bitmap;
  static IsAliveClosure       _is_alive_closure;
  static SpaceInfoNew         _space_info[last_space_id];

  // The head of the global region data list.
  static size_t               _num_regions;
  static PCRegionData*        _region_data_array;
  static PCRegionData**       _per_worker_region_data;

  static size_t               _num_regions_serial;
  static PCRegionData*        _region_data_array_serial;
  static bool                 _serial;

  // Reference processing (used in ...follow_contents)
  static SpanSubjectToDiscoveryClosure  _span_based_discoverer;
  static ReferenceProcessor*  _ref_processor;

  static uint get_num_workers() { return _serial ? 1 : ParallelScavengeHeap::heap()->workers().active_workers(); }
  static size_t get_num_regions() { return _serial ? _num_regions_serial : _num_regions; }
  static PCRegionData* get_region_data_array() { return _serial ? _region_data_array_serial : _region_data_array; }

public:
  static ParallelOldTracer* gc_tracer() { return &_gc_tracer; }

private:

  static void initialize_space_info();

  // Clear the marking bitmap and summary data that cover the specified space.
  static void clear_data_covering_space(SpaceId id);

  static void pre_compact();

  static void post_compact();

  static bool check_maximum_compaction();

  // Mark live objects
  static void marking_phase(ParallelOldTracer *gc_tracer);

  static void summary_phase();
  static void setup_regions_parallel();
  static void setup_regions_serial();

  static void adjust_pointers();
  static void forward_to_new_addr();

  // Move objects to new locations.
  static void compact();

public:
  static bool invoke(bool maximum_heap_compaction, bool serial);
  static bool invoke_no_policy(bool maximum_heap_compaction, bool serial);

  static void adjust_pointers_in_spaces(uint worker_id);

  static void post_initialize();
  // Perform initialization for PSParallelCompactNew that requires
  // allocations.  This should be called during the VM initialization
  // at a pointer where it would be appropriate to return a JNI_ENOMEM
  // in the event of a failure.
  static bool initialize_aux_data();

  // Closure accessors
  static BoolObjectClosure* is_alive_closure()     { return &_is_alive_closure; }

  // Public accessors
  static elapsedTimer* accumulated_time() { return &_accumulated_time; }

  static CollectorCounters* counters()    { return _counters; }

  static inline bool is_marked(oop obj);

  template <class T> static inline void adjust_pointer(T* p);

  // Convenience wrappers for per-space data kept in _space_info.
  static inline MutableSpace*     space(SpaceId space_id);
  static inline ObjectStartArray* start_array(SpaceId space_id);

  static ParMarkBitMap* mark_bitmap() { return &_mark_bitmap; }

  // Reference Processing
  static ReferenceProcessor* ref_processor() { return _ref_processor; }

  static STWGCTimer* gc_timer() { return &_gc_timer; }

  // Return the SpaceId for the given address.
  static SpaceId space_id(HeapWord* addr);

  static void print_on_error(outputStream* st);
};

void steal_marking_work_new(TaskTerminator& terminator, uint worker_id);

#endif // SHARE_GC_PARALLEL_PSPARALLELCOMPACTNEW_HPP
