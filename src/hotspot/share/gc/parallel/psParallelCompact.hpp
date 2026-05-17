/*
 * Copyright (c) 2005, 2026, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_PARALLEL_PSPARALLELCOMPACT_HPP
#define SHARE_GC_PARALLEL_PSPARALLELCOMPACT_HPP

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
class ParCompactionManager;
class PSParallelCompact;
class MoveAndUpdateClosure;
class ParallelOldTracer;
class STWGCTimer;

class SpaceInfo
{
public:
  MutableSpace* space() const     { return _space; }
  void set_space(MutableSpace* s) { _space = s; }

  // Live words belonging to this space.
  size_t live_words() const              { return _live_words; }
  void set_live_words(size_t live_words) { _live_words = live_words; }

private:
  MutableSpace* _space;
  size_t        _live_words;
};

class ParallelCompactData
{
public:
  // Sizes are in HeapWords, unless indicated otherwise.
  static const size_t Log2RegionSize;
  static const size_t RegionSize;
  static const size_t RegionSizeBytes;

  // Mask for the bits in a size_t to get an offset within a region.
  static const size_t RegionSizeOffsetMask;
  // Mask for the bits in a pointer to get an offset within a region.
  static const size_t RegionAddrOffsetMask;
  // Mask for the bits in a pointer to get the address of the start of a region.
  static const size_t RegionAddrMask;

  class RegionData
  {
  public:
    // Destination for the first live word in this region.
    // Therefore, the new addr for every live obj on this region can be calculated as:
    //
    // new_addr := _destination + live_words_offset(old_addr);
    //
    // where, live_words_offset is the number of live words accumulated from
    // region-start to old_addr.
    HeapWord* destination() const { return _destination; }

    // A destination region can have multiple source regions; only the first
    // one is recorded. Since all live objs are slided down, subsequent source
    // regions can be found via plain heap-region iteration.
    size_t source_region() const { return _source_region; }

    // Reuse _source_region to store the corresponding shadow region index
    size_t shadow_region() const { return _source_region; }

    // The starting address of the partial object extending onto the region.
    HeapWord* partial_obj_addr() const { return _partial_obj_addr; }

    // Size of the partial object extending onto the region (words).
    size_t partial_obj_size() const { return _partial_obj_size; }

    // Size of live data that lies within this region due to objects that start
    // in this region (words).  This does not include the partial object
    // extending onto the region (if any), or the part of an object that extends
    // onto the next region (if any).
    size_t live_obj_size() const { return _live_obj_size.load_relaxed(); }

    // Total live data that lies within the region (words).
    size_t data_size() const { return partial_obj_size() + live_obj_size(); }

    // The destination_count is the number of other regions to which data from
    // this region will be copied.  At the end of the summary phase, the valid
    // values of destination_count are
    //
    // 0 - data from the region will be compacted completely into itself, or the
    //     region is empty. The region can be filled immediately.
    // 1 - data from the region will be compacted into 1 other region; some
    //     data from the region may also be compacted into the region itself.
    // 2 - data from the region will be copied to 2 other regions.
    //
    // During compaction as regions are emptied, the destination_count is
    // decremented atomically. When it reaches 0, the region has no remaining
    // destination dependencies.
    uint destination_count() const { return state_dcount(state()); }

    // Whether this region has no remaining destination dependencies or has been completed.
    bool available() const { return destination_count() == 0; }
    bool completed() const { return state_status(state()) == status_completed; }

    // These are not atomic.
    void set_destination(HeapWord* addr)       { _destination = addr; }
    void set_source_region(size_t region)      { _source_region = region; }
    void set_shadow_region(size_t region)      { _source_region = region; }
    void set_partial_obj_addr(HeapWord* addr)  { _partial_obj_addr = addr; }
    void set_partial_obj_size(size_t words)    {
      _partial_obj_size = (region_sz_t) words;
    }

    inline void set_destination_count(uint count);

    inline void set_completed();
    inline bool available_to_normal();

    // These are atomic.
    void add_live_obj(size_t words) {
      assert(words <= RegionSize - live_obj_size(), "overflow");
      _live_obj_size.add_then_fetch(static_cast<region_sz_t>(words));
    }
    inline void decrement_destination_count();
    inline bool try_mark_normal();
    inline bool try_complete_filled_shadow();

    // Mark the current region as shadow to enter the shadow processing path.
    inline bool try_mark_shadow();
    // Mark the shadow region as filled. Returns true if it can be copied back immediately.
    inline bool mark_shadow_filled();
    // Special case: see PSParallelCompact::fill_and_update_shadow_region.
    inline void shadow_to_normal();
    bool is_normal() const { return state_status(state()) == status_normal; }
    bool is_shadow() const { return state_status(state()) == status_shadow; }

    bool is_clear();

    void verify_clear() NOT_DEBUG_RETURN;

  private:
    // The type used to represent object sizes within a region.
    typedef uint region_sz_t;
    // The type used to represent destination count and compaction state.
    typedef uint state_t;

    // Layout of _state:
    //
    //    31-5     4-2      1-0
    // +--------+--------+--------+
    //   unused   status   dcount
    // +--------+--------+--------+
    // |        |        |        |
    // |        |        |* 1-0 dcount: destination regions still to be copied
    //                                  from this source region (0-2)
    // |        |* 2-4 status: compaction path progress, see StateBits
    // |* 5-31 unused
    //
    // Complete status routes:
    //   1. Normal fill, initially or later available:
    //     status_unused -> status_normal -> status_completed
    //     The first transition happens during initial task setup or when dcount reaches 0.
    //
    //   2. Shadow selected, but no shadow region is available when dcount reaches 0:
    //     status_unused -> status_shadow -> status_normal -> status_completed
    //     status_shadow with dcount 0 can fall back to normal filling.
    //
    //   3. Shadow fill completes before dcount reaches 0:
    //     status_unused -> status_shadow -> status_filled_shadow -> status_completed
    //     The last transition happens when dcount reaches 0 and the filled shadow is copied back.
    //
    //   4. Dcount reaches 0 before shadow fill completes:
    //     status_unused -> status_shadow -> status_completed
    //     The last transition happens when shadow fill completes and is copied back immediately.
    enum StateBits : state_t {
      max_destination_count = 2,
      dcount_mask = 0x3,
      status_shift = 2,
      status_mask = 0x7 << status_shift,

      status_unused = 0 << status_shift,
      status_normal = 1 << status_shift,
      status_shadow = 2 << status_shift,
      status_filled_shadow = 3 << status_shift,
      status_completed = 4 << status_shift
    };

    // The destination count occupies the low status_shift bits, so decrementing the whole
    // word in decrement_destination_count() decrements the count alone.
    static_assert(dcount_mask == ((1U << status_shift) - 1),
                  "dcount field width must equal status_shift");
    static_assert(max_destination_count <= dcount_mask,
                  "max_destination_count must fit in the dcount field");

    HeapWord*            _destination;
    size_t               _source_region;
    HeapWord*            _partial_obj_addr;
    region_sz_t          _partial_obj_size;
    Atomic<region_sz_t>  _live_obj_size;
    Atomic<state_t>      _state;

    state_t state() const { return _state.load_relaxed(); }
    static state_t make_state(state_t dcount, state_t status) {
      return dcount | status;
    }
    static state_t state_dcount(state_t state) { return state & dcount_mask; }
    static state_t state_status(state_t state) { return state & status_mask; }
    static state_t with_dcount(state_t state, state_t dcount) {
      return (state & ~dcount_mask) | dcount;
    }
    static state_t with_status(state_t state, state_t status) {
      return (state & ~status_mask) | status;
    }
#ifdef ASSERT
   public:
    uint                 _pushed;   // 0 until region is pushed onto a stack
#endif
  };

public:
  ParallelCompactData();
  bool initialize(MemRegion reserved_heap);

  size_t region_count() const { return _region_count; }
  size_t reserved_byte_size() const { return _reserved_byte_size; }

  // Convert region indices to/from RegionData pointers.
  inline RegionData* region(size_t region_idx) const;
  inline size_t     region(const RegionData* const region_ptr) const;

  size_t live_words_in_space(const MutableSpace* space,
                             HeapWord** full_region_prefix_end = nullptr);

  void summarize(HeapWord* source_beg,
                 HeapWord* source_end,
                 HeapWord** new_top_addr);

  void clear_range(size_t beg_region, size_t end_region);

  // Return the number of words between addr and the start of the region
  // containing addr.
  inline size_t     region_offset(const HeapWord* addr) const;

  // Convert addresses to/from a region index or region pointer.
  inline size_t     addr_to_region_idx(const HeapWord* addr) const;
  inline RegionData* addr_to_region_ptr(const HeapWord* addr) const;
  inline HeapWord*  region_to_addr(size_t region) const;
  inline HeapWord*  region_to_addr(const RegionData* region) const;

  inline HeapWord*  region_align_down(HeapWord* addr) const;
  inline HeapWord*  region_align_up(HeapWord* addr) const;
  inline bool       is_region_aligned(HeapWord* addr) const;

#ifdef  ASSERT
  void verify_clear();
#endif  // #ifdef ASSERT

private:
  HeapWord*       _heap_start;
#ifdef  ASSERT
  HeapWord*       _heap_end;
#endif  // #ifdef ASSERT

  PSVirtualSpace* _region_vspace;
  size_t          _reserved_byte_size;
  RegionData*     _region_data;
  size_t          _region_count;
};

inline void
ParallelCompactData::RegionData::set_destination_count(uint count)
{
  assert(count <= max_destination_count, "count too large");
  const state_t old_state = state();
  assert(state_status(old_state) == status_unused, "cannot reset processed region");
  _state.store_relaxed(with_dcount(old_state, static_cast<state_t>(count)));
}

inline void ParallelCompactData::RegionData::decrement_destination_count()
{
  state_t old_state = _state.fetch_then_sub(static_cast<state_t>(1));
  assert(state_dcount(old_state) > 0, "count would go negative");
}

inline void ParallelCompactData::RegionData::set_completed()
{
  const state_t old_state = state();
  assert(state_status(old_state) == status_normal, "can only complete normal regions");
  _state.store_relaxed(with_status(old_state, status_completed));
}

inline bool ParallelCompactData::RegionData::available_to_normal()
{
  const state_t old_state = state();
  assert(state_status(old_state) == status_unused, "initial regions must be unprocessed");
  if (state_dcount(old_state) == 0) {
    _state.store_relaxed(make_state(0, status_normal));
    return true;
  }
  return false;
}

inline bool ParallelCompactData::RegionData::try_mark_normal()
{
  assert(available(), "region must have no remaining destination dependencies");
  return _state.compare_set(make_state(0, status_unused), make_state(0, status_normal));
}

inline bool ParallelCompactData::RegionData::try_complete_filled_shadow()
{
  assert(available(), "region must have no remaining destination dependencies");
  return _state.compare_set(make_state(0, status_filled_shadow), make_state(0, status_completed));
}

inline bool ParallelCompactData::RegionData::try_mark_shadow() {
  state_t old_state = state();
  while (true) {
    if (state_dcount(old_state) == 0 ||
        state_status(old_state) != status_unused) {
      return false;
    }
    const state_t new_state = with_status(old_state, status_shadow);
    if (_state.compare_set(old_state, new_state)) {
      return true;
    }
    old_state = state();
  }
}

inline bool ParallelCompactData::RegionData::mark_shadow_filled() {
  state_t old_state = state();
  while (true) {
    state_t new_state;
    bool copy_now = false;
    assert(state_status(old_state) == status_shadow, "must be shadow");
    if (state_dcount(old_state) == 0) {
      new_state = with_status(old_state, status_completed);
      copy_now = true;
    } else {
      new_state = with_status(old_state, status_filled_shadow);
    }
    if (_state.compare_set(old_state, new_state)) {
      return copy_now;
    }
    old_state = state();
  }
}

void ParallelCompactData::RegionData::shadow_to_normal() {
  assert(available(), "region must have no remaining destination dependencies");
  const state_t old_state = make_state(0, status_shadow);
  const state_t new_state = make_state(0, status_normal);
  bool result = _state.compare_set(old_state, new_state);
  assert(result, "Fail to mark the region as normal");
}

inline ParallelCompactData::RegionData*
ParallelCompactData::region(size_t region_idx) const
{
  assert(region_idx <= region_count(), "bad arg");
  return _region_data + region_idx;
}

inline size_t
ParallelCompactData::region(const RegionData* const region_ptr) const
{
  assert(region_ptr >= _region_data, "bad arg");
  assert(region_ptr <= _region_data + region_count(), "bad arg");
  return pointer_delta(region_ptr, _region_data, sizeof(RegionData));
}

inline size_t
ParallelCompactData::region_offset(const HeapWord* addr) const
{
  assert(addr >= _heap_start, "bad addr");
  // This method would mistakenly return 0 for _heap_end; hence exclusive.
  assert(addr < _heap_end, "bad addr");
  return (size_t(addr) & RegionAddrOffsetMask) >> LogHeapWordSize;
}

inline size_t
ParallelCompactData::addr_to_region_idx(const HeapWord* addr) const
{
  assert(addr >= _heap_start, "bad addr " PTR_FORMAT " _heap_start " PTR_FORMAT, p2i(addr), p2i(_heap_start));
  assert(addr <= _heap_end, "bad addr " PTR_FORMAT " _heap_end " PTR_FORMAT, p2i(addr), p2i(_heap_end));
  return pointer_delta(addr, _heap_start) >> Log2RegionSize;
}

inline ParallelCompactData::RegionData*
ParallelCompactData::addr_to_region_ptr(const HeapWord* addr) const
{
  return region(addr_to_region_idx(addr));
}

inline HeapWord*
ParallelCompactData::region_to_addr(size_t region) const
{
  assert(region <= _region_count, "region out of range");
  return _heap_start + (region << Log2RegionSize);
}

inline HeapWord*
ParallelCompactData::region_to_addr(const RegionData* region) const
{
  return region_to_addr(pointer_delta(region, _region_data,
                                      sizeof(RegionData)));
}

inline HeapWord*
ParallelCompactData::region_align_down(HeapWord* addr) const
{
  assert(addr >= _heap_start, "bad addr");
  assert(addr < _heap_end + RegionSize, "bad addr");
  return (HeapWord*)(size_t(addr) & RegionAddrMask);
}

inline HeapWord*
ParallelCompactData::region_align_up(HeapWord* addr) const
{
  assert(addr >= _heap_start, "bad addr");
  assert(addr <= _heap_end, "bad addr");
  return region_align_down(addr + RegionSizeOffsetMask);
}

inline bool
ParallelCompactData::is_region_aligned(HeapWord* addr) const
{
  return (size_t(addr) & RegionAddrOffsetMask) == 0;
}

// Abstract closure for use with ParMarkBitMap::iterate(), which will invoke the
// do_addr() method.
//
// The closure is initialized with the number of heap words to process
// (words_remaining()), and becomes 'full' when it reaches 0.  The do_addr()
// methods in subclasses should update the total as words are processed.  Since
// only one subclass actually uses this mechanism to terminate iteration, the
// default initial value is > 0.  The implementation is here and not in the
// single subclass that uses it to avoid making is_full() virtual, and thus
// adding a virtual call per live object.


// The Parallel collector is a stop-the-world garbage collector that
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
//      - calculating destination-region for each region for better parallellism in following phases
//      - calculate the destination of each object at the end of the collection
//      - adjust pointers to reflect new destination of objects
//      - move the objects to their destination
//      - update some references and reinitialize some variables
//
// A space that is being collected is divided into regions and with each region
// is associated an object of type ParallelCompactData.  Each region is of a
// fixed size and typically will contain more than 1 object and may have parts
// of objects at the front and back of the region.
//
// region            -----+---------------------+----------
// objects covered   [ AAA  )[ BBB )[ CCC   )[ DDD     )
//
// The marking phase does a complete marking of all live objects in the heap.
// The marking also compiles the size of the data for all live objects covered
// by the region.  This size includes the part of any live object spanning onto
// the region (part of AAA if it is live) from the front, all live objects
// contained in the region (BBB and/or CCC if they are live), and the part of
// any live objects covered by the region that extends off the region (part of
// DDD if it is live).  The marking phase uses multiple GC threads and marking
// is done in a bit array of type ParMarkBitMap.  The marking of the bit map is
// done atomically as is the accumulation of the size of the live objects
// covered by a region.
//
// The summary phase calculates the total live data to the left of each region
// XXX.  Based on that total and the bottom of the space, it can calculate the
// starting location of the live data in XXX.  The summary phase calculates for
// each region XXX quantities such as
//
//      - the amount of live data at the beginning of a region from an object
//        entering the region.
//      - the location of the first live data on the region
//      - a count of the number of regions receiving live data from XXX.
//
// See ParallelCompactData for precise details.  The summary phase also
// calculates the dense prefix for the compaction.  The dense prefix is a
// portion at the beginning of the space that is not moved.  The objects in the
// dense prefix do need to have their object references updated.  See method
// summarize_dense_prefix().
//
// The forward (to new address) phase calculates the new address of each
// objects and records old-addr-to-new-addr asssociation.
//
// The adjust pointers phase remap all pointers to reflect the new address of each object.
//
// The compaction phase moves objects to their new location.
//
// Compaction is done on a region basis.  A region that is ready to be filled is
// put on a ready list and GC threads take region off the list and fill them.  A
// region is ready to be filled if it empty of live objects.  Such a region may
// have been initially empty (only contained dead objects) or may have had all
// its live objects copied out already.  A region that compacts into itself is
// also ready for filling.  The ready list is initially filled with empty
// regions and regions compacting into themselves.  There is always at least 1
// region that can be put on the ready list.  The regions are atomically added
// and removed from the ready list.
//
// During compaction, there is a natural task dependency among regions because
// destination regions may also be source regions themselves.  Consequently, the
// destination regions are not available for processing until all live objects
// within them are evacuated to their destinations.  These dependencies lead to
// limited thread utilization as threads spin waiting on regions to be ready.
// Shadow regions are utilized to address these region dependencies.  The basic
// idea is that, if a region is unavailable because it still contains live
// objects and thus cannot serve as a destination momentarily, the GC thread
// may allocate a shadow region as a substitute destination and directly copy
// live objects into this shadow region.  Live objects in the shadow region will
// be copied into the target destination region when it becomes available.
//
// For more details on shadow regions, please refer to §4.2 of the VEE'19 paper:
// Haoyu Li, Mingyu Wu, Binyu Zang, and Haibo Chen.  2019.  ScissorGC: scalable
// and efficient compaction for Java full garbage collection.  In Proceedings of
// the 15th ACM SIGPLAN/SIGOPS International Conference on Virtual Execution
// Environments (VEE 2019).  ACM, New York, NY, USA, 108-121.  DOI:
// https://doi.org/10.1145/3313808.3313820

class PSParallelCompact : AllStatic {
public:
  // Convenient access to type names.
  typedef ParallelCompactData::RegionData RegionData;

  // By the end of full-gc, all live objs are compacted into the old-gen.
  typedef enum {
    // Old-gen; single space
    old_space_id,

    // Young-gen; 3 spaces. Use [to, from, eden] layout so that the subsequent
    // young-gc after this full-gc can do resize. Young-gc can resize only when
    // objs are at the beginning of the young-gen.
    first_young_gen_space_id,
    to_space_id = first_young_gen_space_id,
    from_space_id,
    eden_space_id,

    // Place holder for iteration (exclusive)
    last_space_id
  } SpaceId;

  // Inline closure decls
  //
  class IsAliveClosure: public BoolObjectClosure {
   public:
    virtual bool do_object_b(oop p);
  };

private:
  static STWGCTimer           _gc_timer;
  static ParallelOldTracer    _gc_tracer;
  static elapsedTimer         _accumulated_time;
  static unsigned int         _maximum_compaction_gc_num;
  static CollectorCounters*   _counters;
  static ParMarkBitMap        _mark_bitmap;
  static ParallelCompactData  _summary_data;
  static IsAliveClosure       _is_alive_closure;
  static SpaceInfo            _space_info[last_space_id];
  static HeapWord*            _old_space_dense_prefix;

  // The new top pointer of old-space (old-gen) after full-gc.
  static HeapWord*            _old_space_new_top;

  // Reference processing (used in ...follow_contents)
  static SpanSubjectToDiscoveryClosure  _span_based_discoverer;
  static ReferenceProcessor*  _ref_processor;

public:
  static ParallelOldTracer* gc_tracer() { return &_gc_tracer; }

private:

  static void initialize_space_info();

  // Clear the marking bitmap and summary data that cover the specified space.
  static void clear_data_covering_space(SpaceId id);

  static void pre_compact();
  static void post_compact(PSPendingAllocation pending_allocation);

  static bool check_maximum_compaction(bool should_do_max_compaction,
                                       size_t total_live_words,
                                       MutableSpace* const old_space,
                                       HeapWord* full_region_prefix_end);

  // Compute the dense prefix end for old space.
  // When should_do_max_compaction is true, the dense prefix starts at
  // full_region_prefix_end; otherwise the prefix end is determined by the
  // max_waste_bytes budget.  Does NOT set _old_space_dense_prefix.
  static HeapWord* compute_dense_prefix_for_old_space(MutableSpace* old_space,
                                                      HeapWord* full_region_prefix_end,
                                                      size_t max_waste_bytes,
                                                      bool should_do_max_compaction);

  // Try to place a 2-word filler at the dense-prefix boundary when
  // there is a 1-word gap right before the boundary.  Returns true if
  // the filler was placed.  _old_space_dense_prefix must have been set.
  static bool try_fill_gap_at_dense_prefix_end();

  // Compute the dense prefix for old space and the assumed post-GC live bytes.
  // Calls compute_dense_prefix_for_old_space and try_fill_gap_at_dense_prefix_end.
  // Sets _old_space_dense_prefix as a side effect.
  static size_t compute_dense_prefix_and_assumed_live_bytes(bool should_do_max_compaction,
                                                            size_t total_live_words,
                                                            PSOldGen* old_gen,
                                                            HeapWord* full_region_prefix_end);

  // Mark live objects
  static void marking_phase(ParallelOldTracer *gc_tracer);

  static void summary_phase(bool should_do_max_compaction);

  static void adjust_pointers();
  static void forward_to_new_addr();

  static void verify_forward() NOT_DEBUG_RETURN;
  static void verify_filler_in_dense_prefix() NOT_DEBUG_RETURN;

  // Move objects to new locations.
  static void compact();

  static void report_object_count_after_gc();
  // Add available regions to the stack and draining tasks to the task queue.
  static void prepare_region_draining_tasks(uint parallel_gc_threads);

  static void fill_range_in_dense_prefix(HeapWord* start, HeapWord* end);

  static void summarize_spaces(size_t assumed_live_bytes);

public:
  static void fill_dead_objs_in_dense_prefix();

  // This method invokes a full collection.
  // clear_all_soft_refs controls whether soft-refs should be cleared or not.
  // should_do_max_compaction controls whether all spaces for dead objs should be reclaimed.
  static bool invoke(bool clear_all_soft_refs, bool should_do_max_compaction);
  static bool invoke(bool clear_all_soft_refs,
                     bool should_do_max_compaction,
                     PSPendingAllocation pending_allocation,
                     size_t promoted_before_full_gc = 0);

  static void adjust_in_space_helper(SpaceId id, Atomic<uint>* claim_counter);

  static size_t adjust_in_obj_with_limit(HeapWord* obj_start, HeapWord* left, HeapWord* right);

  static void adjust_in_stripe(HeapWord* stripe_start, HeapWord* stripe_end);

  static void adjust_pointers_in_spaces(uint worker_id, Atomic<uint>* claim_counter);

  static void post_initialize();
  // Perform initialization for PSParallelCompact that requires
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

  static HeapWord* old_space_new_top() { return _old_space_new_top; }

  // Return the address of the count + 1st live word in the range [beg, end).
  static HeapWord* skip_live_words(HeapWord* beg, HeapWord* end, size_t count);

  // Return the address of the word to be copied to dest_addr, which must be
  // aligned to a region boundary.
  static HeapWord* first_src_addr(HeapWord* const dest_addr,
                                  size_t src_region_idx);

  // Determine the next source region, set closure.source() to the start of the
  // new region return the region index.  Parameter end_addr is the address one
  // beyond the end of source range just processed.  If necessary, switch to a
  // new source space and set src_space_id (in-out parameter) and src_space_top
  // (out parameter) accordingly.
  static size_t next_src_region(MoveAndUpdateClosure& closure,
                                SpaceId& src_space_id,
                                HeapWord*& src_space_top,
                                HeapWord* end_addr);

  // Decrement the destination count for each non-empty source region in the
  // range [beg_region, region(region_align_up(end_addr))).  If the destination
  // count for a region goes to 0 and it needs to be filled, enqueue it.
  static void decrement_destination_counts(ParCompactionManager* cm,
                                           SpaceId src_space_id,
                                           size_t beg_region,
                                           HeapWord* end_addr);

  static HeapWord* partial_obj_end(HeapWord* region_start_addr);

  static void fill_region(ParCompactionManager* cm, MoveAndUpdateClosure& closure, size_t region);
  static void fill_and_update_region(ParCompactionManager* cm, size_t region);

  static bool steal_unavailable_region(ParCompactionManager* cm, size_t& region_idx);
  static void fill_and_update_shadow_region(ParCompactionManager* cm, size_t region);
  // Copy the content of a shadow region back to its corresponding heap region
  static void copy_back(HeapWord* shadow_addr, HeapWord* region_addr);
  // Collect empty regions as shadow regions and initialize the
  // _next_shadow_region filed for each compact manager
  static void initialize_shadow_regions(uint parallel_gc_threads);

  static ParMarkBitMap* mark_bitmap() { return &_mark_bitmap; }
  static ParallelCompactData& summary_data() { return _summary_data; }

  // Reference Processing
  static ReferenceProcessor* ref_processor() { return _ref_processor; }

  static STWGCTimer* gc_timer() { return &_gc_timer; }

  // Return the SpaceId for the given address.
  static SpaceId space_id(HeapWord* addr);

  static void print_on(outputStream* st);

#ifdef  ASSERT
  // Sanity check the new location of a word in the heap.
  static inline void check_new_location(HeapWord* old_addr, HeapWord* new_addr);
  // Verify that all the regions have been emptied.
  static void verify_regions_after_compaction();
#endif  // #ifdef ASSERT
};

class MoveAndUpdateClosure: public StackObj {
private:
  ParMarkBitMap* const        _bitmap;
  size_t                      _words_remaining; // Words left to copy.
  static inline size_t calculate_words_remaining(size_t region);

protected:
  HeapWord*               _source;          // Next addr that would be read.
  HeapWord*               _destination;     // Next addr to be written.
  ObjectStartArray* const _start_array;
  size_t                  _offset;

  inline void decrement_words_remaining(size_t words);
  // Update variables to indicate that word_count words were processed.
  inline void update_state(size_t words);

public:
  ParMarkBitMap*        bitmap() const { return _bitmap; }

  size_t    words_remaining()    const { return _words_remaining; }
  bool      is_full()            const { return _words_remaining == 0; }
  HeapWord* source()             const { return _source; }
  void      set_source(HeapWord* addr) {
    assert(addr != nullptr, "precondition");
    _source = addr;
  }

  // If the object will fit (size <= words_remaining()), copy it to the current
  // destination, update the interior oops and the start array.
  void do_addr(HeapWord* addr, size_t words, markWord mark);

  inline MoveAndUpdateClosure(ParMarkBitMap* bitmap, size_t region);

  // Accessors.
  HeapWord* destination() const         { return _destination; }
  HeapWord* copy_destination() const    { return _destination + _offset; }

  // Copy enough words to fill this closure or to the end of an object,
  // whichever is smaller, starting at source(). The start array is not
  // updated.
  void copy_partial_obj(size_t partial_obj_size);

  virtual void complete_region(HeapWord* dest_addr, PSParallelCompact::RegionData* region_ptr);
};

inline void MoveAndUpdateClosure::decrement_words_remaining(size_t words) {
  assert(_words_remaining >= words, "processed too many words");
  _words_remaining -= words;
}

inline size_t MoveAndUpdateClosure::calculate_words_remaining(size_t region) {
  HeapWord* dest_addr = PSParallelCompact::summary_data().region_to_addr(region);
  HeapWord* new_top = PSParallelCompact::old_space_new_top();
  return MIN2(pointer_delta(new_top, dest_addr),
              ParallelCompactData::RegionSize);
}

static ObjectStartArray* start_array_for_addr(void* addr) {
  assert(addr < PSParallelCompact::old_space_new_top(), "precondition");
  return ParallelScavengeHeap::heap()->start_array();
}

inline
MoveAndUpdateClosure::MoveAndUpdateClosure(ParMarkBitMap* bitmap, size_t region_idx) :
  _bitmap(bitmap),
  _words_remaining(calculate_words_remaining(region_idx)),
  _source(nullptr),
  _destination(PSParallelCompact::summary_data().region_to_addr(region_idx)),
  _start_array(start_array_for_addr(_destination)),
  _offset(0) {}

inline void MoveAndUpdateClosure::update_state(size_t words)
{
  decrement_words_remaining(words);
  _source += words;
  _destination += words;
}

class MoveAndUpdateShadowClosure: public MoveAndUpdateClosure {
  inline size_t calculate_shadow_offset(size_t region_idx, size_t shadow_idx);
public:
  inline MoveAndUpdateShadowClosure(ParMarkBitMap* bitmap, size_t region, size_t shadow);

  virtual void complete_region(HeapWord* dest_addr, PSParallelCompact::RegionData* region_ptr);

private:
  size_t _shadow;
};

inline size_t MoveAndUpdateShadowClosure::calculate_shadow_offset(size_t region_idx, size_t shadow_idx) {
  ParallelCompactData& sd = PSParallelCompact::summary_data();
  HeapWord* dest_addr = sd.region_to_addr(region_idx);
  HeapWord* shadow_addr = sd.region_to_addr(shadow_idx);
  return pointer_delta(shadow_addr, dest_addr);
}

inline
MoveAndUpdateShadowClosure::MoveAndUpdateShadowClosure(ParMarkBitMap* bitmap, size_t region, size_t shadow) :
  MoveAndUpdateClosure(bitmap, region),
  _shadow(shadow) {
  _offset = calculate_shadow_offset(region, shadow);
}

void steal_marking_work(TaskTerminator& terminator, uint worker_id);

#endif // SHARE_GC_PARALLEL_PSPARALLELCOMPACT_HPP
