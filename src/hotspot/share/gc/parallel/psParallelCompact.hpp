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

// The SplitInfo class holds the information needed to 'split' a source region
// so that the live data can be copied to two destination *spaces*.  Normally,
// all the live data in a region is copied to a single destination space (e.g.,
// everything live in a region in eden is copied entirely into the old gen).
// However, when the heap is nearly full, all the live data in eden may not fit
// into the old gen.  Copying only some of the regions from eden to old gen
// requires finding a region that does not contain a partial object (i.e., no
// live object crosses the region boundary) somewhere near the last object that
// does fit into the old gen.  Since it's not always possible to find such a
// region, splitting is necessary for predictable behavior.
//
// A region is always split at the end of the partial object.  This avoids
// additional tests when calculating the new location of a pointer, which is a
// very hot code path.  The partial object and everything to its left will be
// copied to another space (call it dest_space_1).  The live data to the right
// of the partial object will be copied either within the space itself, or to a
// different destination space (distinct from dest_space_1).
//
// Split points are identified during the summary phase, when region
// destinations are computed:  data about the split, including the
// partial_object_size, is recorded in a SplitInfo record and the
// partial_object_size field in the summary data is set to zero.  The zeroing is
// possible (and necessary) since the partial object will move to a different
// destination space than anything to its right, thus the partial object should
// not affect the locations of any objects to its right.
//
// The recorded data is used during the compaction phase, but only rarely:  when
// the partial object on the split region will be copied across a destination
// region boundary.  This test is made once each time a region is filled, and is
// a simple address comparison, so the overhead is negligible (see
// PSParallelCompact::first_src_addr()).
//
// Notes:
//
// Only regions with partial objects are split; a region without a partial
// object does not need any extra bookkeeping.
//
// At most one region is split per space, so the amount of data required is
// constant.
//
// A region is split only when the destination space would overflow.  Once that
// happens, the destination space is abandoned and no other data (even from
// other source spaces) is targeted to that destination space.  Abandoning the
// destination space may leave a somewhat large unused area at the end, if a
// large object caused the overflow.
//
// Future work:
//
// More bookkeeping would be required to continue to use the destination space.
// The most general solution would allow data from regions in two different
// source spaces to be "joined" in a single destination region.  At the very
// least, additional code would be required in next_src_region() to detect the
// join and skip to an out-of-order source region.  If the join region was also
// the last destination region to which a split region was copied (the most
// likely case), then additional work would be needed to get fill_region() to
// stop iteration and switch to a new source region at the right point.  Basic
// idea would be to use a fake value for the top of the source space.  It is
// doable, if a bit tricky.
//
// A simpler (but less general) solution would fill the remainder of the
// destination region with a dummy object and continue filling the next
// destination region.

class SplitInfo
{
public:
  // Return true if this split info is valid (i.e., if a split has been
  // recorded).  The very first region cannot have a partial object and thus is
  // never split, so 0 is the 'invalid' value.
  bool is_valid() const { return _split_region_idx > 0; }

  // Return true if this split holds data for the specified source region.
  inline bool is_split(size_t region_idx) const;

  // Obj at the split point doesn't fit the previous space and will be relocated to the next space.
  HeapWord* split_point() const { return _split_point; }

  // Number of live words before the split point on this region.
  size_t preceding_live_words() const { return _preceding_live_words; }

  // A split region has two "destinations", living in two spaces. This method
  // returns the first one -- destination for the first live word on
  // this split region.
  HeapWord* preceding_destination() const {
    assert(_preceding_destination != nullptr, "inv");
    return _preceding_destination;
  }

  // Number of regions the preceding live words are relocated into.
  uint preceding_destination_count() const { return _preceding_destination_count; }

  void record(size_t split_region_idx, HeapWord* split_point, size_t preceding_live_words);

  void clear();

  DEBUG_ONLY(void verify_clear();)

private:
  size_t       _split_region_idx;
  HeapWord*    _split_point;
  size_t       _preceding_live_words;
  HeapWord*    _preceding_destination;
  uint         _preceding_destination_count;
};

inline bool SplitInfo::is_split(size_t region_idx) const
{
  return _split_region_idx == region_idx && is_valid();
}

class SpaceInfo
{
public:
  MutableSpace* space() const { return _space; }

  // Where the free space will start after the collection.  Valid only after the
  // summary phase completes.
  HeapWord* new_top() const { return _new_top; }

  // Allows new_top to be set.
  HeapWord** new_top_addr() { return &_new_top; }

  // Where the dense prefix ends, or the compacted region begins.
  HeapWord* dense_prefix() const { return _dense_prefix; }

  // The start array for the (generation containing the) space, or null if there
  // is no start array.
  ObjectStartArray* start_array() const { return _start_array; }

  SplitInfo& split_info() { return _split_info; }

  void set_space(MutableSpace* s)           { _space = s; }
  void set_new_top(HeapWord* addr)          { _new_top = addr; }
  void set_dense_prefix(HeapWord* addr)     { _dense_prefix = addr; }
  void set_start_array(ObjectStartArray* s) { _start_array = s; }

private:
  MutableSpace*     _space;
  HeapWord*         _new_top;
  HeapWord*         _dense_prefix;
  ObjectStartArray* _start_array;
  SplitInfo         _split_info;
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
    size_t live_obj_size() const { return dc_and_los() & los_mask; }

    // Total live data that lies within the region (words).
    size_t data_size() const { return partial_obj_size() + live_obj_size(); }

    // The destination_count is the number of other regions to which data from
    // this region will be copied.  At the end of the summary phase, the valid
    // values of destination_count are
    //
    // 0 - data from the region will be compacted completely into itself, or the
    //     region is empty.  The region can be claimed and then filled.
    // 1 - data from the region will be compacted into 1 other region; some
    //     data from the region may also be compacted into the region itself.
    // 2 - data from the region will be copied to 2 other regions.
    //
    // During compaction as regions are emptied, the destination_count is
    // decremented (atomically) and when it reaches 0, it can be claimed and
    // then filled.
    //
    // A region is claimed for processing by atomically changing the
    // destination_count to the claimed value (dc_claimed).  After a region has
    // been filled, the destination_count should be set to the completed value
    // (dc_completed).
    inline uint destination_count() const;
    inline uint destination_count_raw() const;

    // Whether this region is available to be claimed, has been claimed, or has
    // been completed.
    //
    // Minor subtlety:  claimed() returns true if the region is marked
    // completed(), which is desirable since a region must be claimed before it
    // can be completed.
    bool available() const { return dc_and_los() < dc_one; }
    bool claimed()   const { return dc_and_los() >= dc_claimed; }
    bool completed() const { return dc_and_los() >= dc_completed; }

    // These are not atomic.
    void set_destination(HeapWord* addr)       { _destination = addr; }
    void set_source_region(size_t region)      { _source_region = region; }
    void set_shadow_region(size_t region)      { _source_region = region; }
    void set_partial_obj_addr(HeapWord* addr)  { _partial_obj_addr = addr; }
    void set_partial_obj_size(size_t words)    {
      _partial_obj_size = (region_sz_t) words;
    }

    inline void set_destination_count(uint count);
    inline void set_live_obj_size(size_t words);

    inline void set_completed();
    inline bool claim_unsafe();

    // These are atomic.
    inline void add_live_obj(size_t words);
    inline void decrement_destination_count();
    inline bool claim();

    // Possible values of _shadow_state, and transition is as follows
    // Normal Path:
    // UnusedRegion -> mark_normal() -> NormalRegion
    // Shadow Path:
    // UnusedRegion -> mark_shadow() -> ShadowRegion ->
    // mark_filled() -> FilledShadow -> mark_copied() -> CopiedShadow
    static const int UnusedRegion = 0; // The region is not collected yet
    static const int ShadowRegion = 1; // Stolen by an idle thread, and a shadow region is created for it
    static const int FilledShadow = 2; // Its shadow region has been filled and ready to be copied back
    static const int CopiedShadow = 3; // The data of the shadow region has been copied back
    static const int NormalRegion = 4; // The region will be collected by the original parallel algorithm

    // Mark the current region as normal or shadow to enter different processing paths
    inline bool mark_normal();
    inline bool mark_shadow();
    // Mark the shadow region as filled and ready to be copied back
    inline void mark_filled();
    // Mark the shadow region as copied back to avoid double copying.
    inline bool mark_copied();
    // Special case: see the comment in PSParallelCompact::fill_and_update_shadow_region.
    // Return to the normal path here
    inline void shadow_to_normal();

    int shadow_state() { return _shadow_state.load_relaxed(); }

    bool is_clear();

    void verify_clear() NOT_DEBUG_RETURN;

  private:
    // The type used to represent object sizes within a region.
    typedef uint region_sz_t;

    // Constants for manipulating the _dc_and_los field, which holds both the
    // destination count and live obj size.  The live obj size lives at the
    // least significant end so no masking is necessary when adding.
    static const region_sz_t dc_shift;           // Shift amount.
    static const region_sz_t dc_mask;            // Mask for destination count.
    static const region_sz_t dc_one;             // 1, shifted appropriately.
    static const region_sz_t dc_claimed;         // Region has been claimed.
    static const region_sz_t dc_completed;       // Region has been completed.
    static const region_sz_t los_mask;           // Mask for live obj size.

    HeapWord*            _destination;
    size_t               _source_region;
    HeapWord*            _partial_obj_addr;
    region_sz_t          _partial_obj_size;
    Atomic<region_sz_t>  _dc_and_los;
    Atomic<int>          _shadow_state;

    region_sz_t dc_and_los() const { return _dc_and_los.load_relaxed(); }
#ifdef ASSERT
   public:
    uint                 _pushed;   // 0 until region is pushed onto a stack
   private:
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

  HeapWord* summarize_split_space(size_t src_region, SplitInfo& split_info,
                                  HeapWord* destination, HeapWord* target_end,
                                  HeapWord** target_next);

  size_t live_words_in_space(const MutableSpace* space,
                             HeapWord** full_region_prefix_end = nullptr);

  bool summarize(SplitInfo& split_info,
                 HeapWord* source_beg, HeapWord* source_end,
                 HeapWord** source_next,
                 HeapWord* target_beg, HeapWord* target_end,
                 HeapWord** target_next);

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
  bool initialize_region_data(size_t heap_size);
  PSVirtualSpace* create_vspace(size_t count, size_t element_size);

  HeapWord*       _heap_start;
#ifdef  ASSERT
  HeapWord*       _heap_end;
#endif  // #ifdef ASSERT

  PSVirtualSpace* _region_vspace;
  size_t          _reserved_byte_size;
  RegionData*     _region_data;
  size_t          _region_count;
};

inline uint
ParallelCompactData::RegionData::destination_count_raw() const
{
  return dc_and_los() & dc_mask;
}

inline uint
ParallelCompactData::RegionData::destination_count() const
{
  return destination_count_raw() >> dc_shift;
}

inline void
ParallelCompactData::RegionData::set_destination_count(uint count)
{
  assert(count <= (dc_completed >> dc_shift), "count too large");
  const region_sz_t live_sz = (region_sz_t) live_obj_size();
  _dc_and_los.store_relaxed((count << dc_shift) | live_sz);
}

inline void ParallelCompactData::RegionData::set_live_obj_size(size_t words)
{
  assert(words <= los_mask, "would overflow");
  _dc_and_los.store_relaxed(destination_count_raw() | (region_sz_t)words);
}

inline void ParallelCompactData::RegionData::decrement_destination_count()
{
  assert(dc_and_los() < dc_claimed, "already claimed");
  assert(dc_and_los() >= dc_one, "count would go negative");
  _dc_and_los.add_then_fetch(dc_mask);
}

inline void ParallelCompactData::RegionData::set_completed()
{
  assert(claimed(), "must be claimed first");
  _dc_and_los.store_relaxed(dc_completed | (region_sz_t) live_obj_size());
}

// MT-unsafe claiming of a region.  Should only be used during single threaded
// execution.
inline bool ParallelCompactData::RegionData::claim_unsafe()
{
  if (available()) {
    _dc_and_los.store_relaxed(dc_and_los() | dc_claimed);
    return true;
  }
  return false;
}

inline void ParallelCompactData::RegionData::add_live_obj(size_t words)
{
  assert(words <= (size_t)los_mask - live_obj_size(), "overflow");
  _dc_and_los.add_then_fetch(static_cast<region_sz_t>(words));
}

inline bool ParallelCompactData::RegionData::claim()
{
  const region_sz_t los = static_cast<region_sz_t>(live_obj_size());
  return _dc_and_los.compare_set(los, dc_claimed | los);
}

inline bool ParallelCompactData::RegionData::mark_normal() {
  return _shadow_state.compare_set(UnusedRegion, NormalRegion);
}

inline bool ParallelCompactData::RegionData::mark_shadow() {
  if (shadow_state() != UnusedRegion) return false;
  return _shadow_state.compare_set(UnusedRegion, ShadowRegion);
}

inline void ParallelCompactData::RegionData::mark_filled() {
  int old = _shadow_state.compare_exchange(ShadowRegion, FilledShadow);
  assert(old == ShadowRegion, "Fail to mark the region as filled");
}

inline bool ParallelCompactData::RegionData::mark_copied() {
  return _shadow_state.compare_set(FilledShadow, CopiedShadow);
}

void ParallelCompactData::RegionData::shadow_to_normal() {
  int old = _shadow_state.compare_exchange(ShadowRegion, NormalRegion);
  assert(old == ShadowRegion, "Fail to mark the region as finish");
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

  // By the end of full-gc, all live objs are compacted into the first three spaces, old, eden, and from.
  typedef enum {
    old_space_id,
    eden_space_id,
    from_space_id,
    to_space_id,
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
  static void post_compact();

  static bool check_maximum_compaction(bool should_do_max_compaction,
                                       size_t total_live_words,
                                       MutableSpace* const old_space,
                                       HeapWord* full_region_prefix_end);

  // Mark live objects
  static void marking_phase(ParallelOldTracer *gc_tracer);

  // Identify the dense-fix in the old-space to avoid moving much memory with little reclaimed.
  static HeapWord* compute_dense_prefix_for_old_space(MutableSpace* old_space,
                                                      HeapWord* full_region_prefix_end);

  // Create a filler obj (if needed) right before the dense-prefix-boundary to
  // make the heap parsable.
  static void fill_dense_prefix_end(SpaceId id);

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

public:
  static void fill_dead_objs_in_dense_prefix();

  // This method invokes a full collection.
  // clear_all_soft_refs controls whether soft-refs should be cleared or not.
  // should_do_max_compaction controls whether all spaces for dead objs should be reclaimed.
  static bool invoke(bool clear_all_soft_refs, bool should_do_max_compaction);

  template<typename Func>
  static void adjust_in_space_helper(SpaceId id, Atomic<uint>* claim_counter, Func&& on_stripe);

  static void adjust_in_old_space(Atomic<uint>* claim_counter);

  static void adjust_in_young_space(SpaceId id, Atomic<uint>* claim_counter);

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
  static inline HeapWord*         new_top(SpaceId space_id);
  static inline HeapWord*         dense_prefix(SpaceId space_id);
  static inline ObjectStartArray* start_array(SpaceId space_id);

  // Return the address of the count + 1st live word in the range [beg, end).
  static HeapWord* skip_live_words(HeapWord* beg, HeapWord* end, size_t count);

  // Return the address of the word to be copied to dest_addr, which must be
  // aligned to a region boundary.
  static HeapWord* first_src_addr(HeapWord* const dest_addr,
                                  SpaceId src_space_id,
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
  static void verify_complete(SpaceId space_id);
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
  void do_addr(HeapWord* addr, size_t words);

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
  PSParallelCompact::SpaceId dest_space_id = PSParallelCompact::space_id(dest_addr);
  HeapWord* new_top = PSParallelCompact::new_top(dest_space_id);
  return MIN2(pointer_delta(new_top, dest_addr),
              ParallelCompactData::RegionSize);
}

inline
MoveAndUpdateClosure::MoveAndUpdateClosure(ParMarkBitMap* bitmap, size_t region_idx) :
  _bitmap(bitmap),
  _words_remaining(calculate_words_remaining(region_idx)),
  _source(nullptr),
  _destination(PSParallelCompact::summary_data().region_to_addr(region_idx)),
  _start_array(PSParallelCompact::start_array(PSParallelCompact::space_id(_destination))),
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
