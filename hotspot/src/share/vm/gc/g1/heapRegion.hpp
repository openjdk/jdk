/*
 * Copyright (c) 2001, 2016, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_G1_HEAPREGION_HPP
#define SHARE_VM_GC_G1_HEAPREGION_HPP

#include "gc/g1/g1AllocationContext.hpp"
#include "gc/g1/g1BlockOffsetTable.hpp"
#include "gc/g1/g1HeapRegionTraceType.hpp"
#include "gc/g1/heapRegionTracer.hpp"
#include "gc/g1/heapRegionType.hpp"
#include "gc/g1/survRateGroup.hpp"
#include "gc/shared/ageTable.hpp"
#include "gc/shared/spaceDecorator.hpp"
#include "utilities/macros.hpp"

// A HeapRegion is the smallest piece of a G1CollectedHeap that
// can be collected independently.

// NOTE: Although a HeapRegion is a Space, its
// Space::initDirtyCardClosure method must not be called.
// The problem is that the existence of this method breaks
// the independence of barrier sets from remembered sets.
// The solution is to remove this method from the definition
// of a Space.

// Each heap region is self contained. top() and end() can never
// be set beyond the end of the region. For humongous objects,
// the first region is a StartsHumongous region. If the humongous
// object is larger than a heap region, the following regions will
// be of type ContinuesHumongous. In this case the top() of the
// StartHumongous region and all ContinuesHumongous regions except
// the last will point to their own end. For the last ContinuesHumongous
// region, top() will equal the object's top.

class G1CollectedHeap;
class HeapRegionRemSet;
class HeapRegionRemSetIterator;
class HeapRegion;
class HeapRegionSetBase;
class nmethod;

#define HR_FORMAT "%u:(%s)[" PTR_FORMAT "," PTR_FORMAT "," PTR_FORMAT "]"
#define HR_FORMAT_PARAMS(_hr_) \
                (_hr_)->hrm_index(), \
                (_hr_)->get_short_type_str(), \
                p2i((_hr_)->bottom()), p2i((_hr_)->top()), p2i((_hr_)->end())

// sentinel value for hrm_index
#define G1_NO_HRM_INDEX ((uint) -1)

// A dirty card to oop closure for heap regions. It
// knows how to get the G1 heap and how to use the bitmap
// in the concurrent marker used by G1 to filter remembered
// sets.

class HeapRegionDCTOC : public DirtyCardToOopClosure {
private:
  HeapRegion* _hr;
  G1ParPushHeapRSClosure* _rs_scan;
  G1CollectedHeap* _g1;

  // Walk the given memory region from bottom to (actual) top
  // looking for objects and applying the oop closure (_cl) to
  // them. The base implementation of this treats the area as
  // blocks, where a block may or may not be an object. Sub-
  // classes should override this to provide more accurate
  // or possibly more efficient walking.
  void walk_mem_region(MemRegion mr, HeapWord* bottom, HeapWord* top);

public:
  HeapRegionDCTOC(G1CollectedHeap* g1,
                  HeapRegion* hr,
                  G1ParPushHeapRSClosure* cl,
                  CardTableModRefBS::PrecisionStyle precision);
};

// The complicating factor is that BlockOffsetTable diverged
// significantly, and we need functionality that is only in the G1 version.
// So I copied that code, which led to an alternate G1 version of
// OffsetTableContigSpace.  If the two versions of BlockOffsetTable could
// be reconciled, then G1OffsetTableContigSpace could go away.

// The idea behind time stamps is the following. We want to keep track of
// the highest address where it's safe to scan objects for each region.
// This is only relevant for current GC alloc regions so we keep a time stamp
// per region to determine if the region has been allocated during the current
// GC or not. If the time stamp is current we report a scan_top value which
// was saved at the end of the previous GC for retained alloc regions and which is
// equal to the bottom for all other regions.
// There is a race between card scanners and allocating gc workers where we must ensure
// that card scanners do not read the memory allocated by the gc workers.
// In order to enforce that, we must not return a value of _top which is more recent than the
// time stamp. This is due to the fact that a region may become a gc alloc region at
// some point after we've read the timestamp value as being < the current time stamp.
// The time stamps are re-initialized to zero at cleanup and at Full GCs.
// The current scheme that uses sequential unsigned ints will fail only if we have 4b
// evacuation pauses between two cleanups, which is _highly_ unlikely.
class G1ContiguousSpace: public CompactibleSpace {
  friend class VMStructs;
  HeapWord* volatile _top;
  HeapWord* volatile _scan_top;
 protected:
  G1BlockOffsetTablePart _bot_part;
  Mutex _par_alloc_lock;
  volatile uint _gc_time_stamp;
  // When we need to retire an allocation region, while other threads
  // are also concurrently trying to allocate into it, we typically
  // allocate a dummy object at the end of the region to ensure that
  // no more allocations can take place in it. However, sometimes we
  // want to know where the end of the last "real" object we allocated
  // into the region was and this is what this keeps track.
  HeapWord* _pre_dummy_top;

 public:
  G1ContiguousSpace(G1BlockOffsetTable* bot);

  void set_top(HeapWord* value) { _top = value; }
  HeapWord* top() const { return _top; }

 protected:
  // Reset the G1ContiguousSpace.
  virtual void initialize(MemRegion mr, bool clear_space, bool mangle_space);

  HeapWord* volatile* top_addr() { return &_top; }
  // Try to allocate at least min_word_size and up to desired_size from this Space.
  // Returns NULL if not possible, otherwise sets actual_word_size to the amount of
  // space allocated.
  // This version assumes that all allocation requests to this Space are properly
  // synchronized.
  inline HeapWord* allocate_impl(size_t min_word_size, size_t desired_word_size, size_t* actual_word_size);
  // Try to allocate at least min_word_size and up to desired_size from this Space.
  // Returns NULL if not possible, otherwise sets actual_word_size to the amount of
  // space allocated.
  // This version synchronizes with other calls to par_allocate_impl().
  inline HeapWord* par_allocate_impl(size_t min_word_size, size_t desired_word_size, size_t* actual_word_size);

 public:
  void reset_after_compaction() { set_top(compaction_top()); }

  size_t used() const { return byte_size(bottom(), top()); }
  size_t free() const { return byte_size(top(), end()); }
  bool is_free_block(const HeapWord* p) const { return p >= top(); }

  MemRegion used_region() const { return MemRegion(bottom(), top()); }

  void object_iterate(ObjectClosure* blk);
  void safe_object_iterate(ObjectClosure* blk);

  void mangle_unused_area() PRODUCT_RETURN;
  void mangle_unused_area_complete() PRODUCT_RETURN;

  HeapWord* scan_top() const;
  void record_timestamp();
  void reset_gc_time_stamp() { _gc_time_stamp = 0; }
  uint get_gc_time_stamp() { return _gc_time_stamp; }
  void record_retained_region();

  // See the comment above in the declaration of _pre_dummy_top for an
  // explanation of what it is.
  void set_pre_dummy_top(HeapWord* pre_dummy_top) {
    assert(is_in(pre_dummy_top) && pre_dummy_top <= top(), "pre-condition");
    _pre_dummy_top = pre_dummy_top;
  }
  HeapWord* pre_dummy_top() {
    return (_pre_dummy_top == NULL) ? top() : _pre_dummy_top;
  }
  void reset_pre_dummy_top() { _pre_dummy_top = NULL; }

  virtual void clear(bool mangle_space);

  HeapWord* block_start(const void* p);
  HeapWord* block_start_const(const void* p) const;

  // Allocation (return NULL if full).  Assumes the caller has established
  // mutually exclusive access to the space.
  HeapWord* allocate(size_t min_word_size, size_t desired_word_size, size_t* actual_word_size);
  // Allocation (return NULL if full).  Enforces mutual exclusion internally.
  HeapWord* par_allocate(size_t min_word_size, size_t desired_word_size, size_t* actual_word_size);

  virtual HeapWord* allocate(size_t word_size);
  virtual HeapWord* par_allocate(size_t word_size);

  HeapWord* saved_mark_word() const { ShouldNotReachHere(); return NULL; }

  // MarkSweep support phase3
  virtual HeapWord* initialize_threshold();
  virtual HeapWord* cross_threshold(HeapWord* start, HeapWord* end);

  virtual void print() const;

  void reset_bot() {
    _bot_part.reset_bot();
  }

  void print_bot_on(outputStream* out) {
    _bot_part.print_on(out);
  }
};

class HeapRegion: public G1ContiguousSpace {
  friend class VMStructs;
  // Allow scan_and_forward to call (private) overrides for auxiliary functions on this class
  template <typename SpaceType>
  friend void CompactibleSpace::scan_and_forward(SpaceType* space, CompactPoint* cp);
 private:

  // The remembered set for this region.
  // (Might want to make this "inline" later, to avoid some alloc failure
  // issues.)
  HeapRegionRemSet* _rem_set;

  // Auxiliary functions for scan_and_forward support.
  // See comments for CompactibleSpace for more information.
  inline HeapWord* scan_limit() const {
    return top();
  }

  inline bool scanned_block_is_obj(const HeapWord* addr) const {
    return true; // Always true, since scan_limit is top
  }

  inline size_t scanned_block_size(const HeapWord* addr) const {
    return HeapRegion::block_size(addr); // Avoid virtual call
  }

  void report_region_type_change(G1HeapRegionTraceType::Type to);

 protected:
  // The index of this region in the heap region sequence.
  uint  _hrm_index;

  AllocationContext_t _allocation_context;

  HeapRegionType _type;

  // For a humongous region, region in which it starts.
  HeapRegion* _humongous_start_region;

  // True iff an attempt to evacuate an object in the region failed.
  bool _evacuation_failed;

  // A heap region may be a member one of a number of special subsets, each
  // represented as linked lists through the field below.  Currently, there
  // is only one set:
  //   The collection set.
  HeapRegion* _next_in_special_set;

  // next region in the young "generation" region set
  HeapRegion* _next_young_region;

  // Fields used by the HeapRegionSetBase class and subclasses.
  HeapRegion* _next;
  HeapRegion* _prev;
#ifdef ASSERT
  HeapRegionSetBase* _containing_set;
#endif // ASSERT

  // We use concurrent marking to determine the amount of live data
  // in each heap region.
  size_t _prev_marked_bytes;    // Bytes known to be live via last completed marking.
  size_t _next_marked_bytes;    // Bytes known to be live via in-progress marking.

  // The calculated GC efficiency of the region.
  double _gc_efficiency;

  int  _young_index_in_cset;
  SurvRateGroup* _surv_rate_group;
  int  _age_index;

  // The start of the unmarked area. The unmarked area extends from this
  // word until the top and/or end of the region, and is the part
  // of the region for which no marking was done, i.e. objects may
  // have been allocated in this part since the last mark phase.
  // "prev" is the top at the start of the last completed marking.
  // "next" is the top at the start of the in-progress marking (if any.)
  HeapWord* _prev_top_at_mark_start;
  HeapWord* _next_top_at_mark_start;
  // If a collection pause is in progress, this is the top at the start
  // of that pause.

  void init_top_at_mark_start() {
    assert(_prev_marked_bytes == 0 &&
           _next_marked_bytes == 0,
           "Must be called after zero_marked_bytes.");
    HeapWord* bot = bottom();
    _prev_top_at_mark_start = bot;
    _next_top_at_mark_start = bot;
  }

  // Cached attributes used in the collection set policy information

  // The RSet length that was added to the total value
  // for the collection set.
  size_t _recorded_rs_length;

  // The predicted elapsed time that was added to total value
  // for the collection set.
  double _predicted_elapsed_time_ms;

  // The predicted number of bytes to copy that was added to
  // the total value for the collection set.
  size_t _predicted_bytes_to_copy;

 public:
  HeapRegion(uint hrm_index,
             G1BlockOffsetTable* bot,
             MemRegion mr);

  // Initializing the HeapRegion not only resets the data structure, but also
  // resets the BOT for that heap region.
  // The default values for clear_space means that we will do the clearing if
  // there's clearing to be done ourselves. We also always mangle the space.
  virtual void initialize(MemRegion mr, bool clear_space = false, bool mangle_space = SpaceDecorator::Mangle);

  static int    LogOfHRGrainBytes;
  static int    LogOfHRGrainWords;

  static size_t GrainBytes;
  static size_t GrainWords;
  static size_t CardsPerRegion;

  static size_t align_up_to_region_byte_size(size_t sz) {
    return (sz + (size_t) GrainBytes - 1) &
                                      ~((1 << (size_t) LogOfHRGrainBytes) - 1);
  }


  // Returns whether a field is in the same region as the obj it points to.
  template <typename T>
  static bool is_in_same_region(T* p, oop obj) {
    assert(p != NULL, "p can't be NULL");
    assert(obj != NULL, "obj can't be NULL");
    return (((uintptr_t) p ^ cast_from_oop<uintptr_t>(obj)) >> LogOfHRGrainBytes) == 0;
  }

  static size_t max_region_size();
  static size_t min_region_size_in_words();

  // It sets up the heap region size (GrainBytes / GrainWords), as
  // well as other related fields that are based on the heap region
  // size (LogOfHRGrainBytes / LogOfHRGrainWords /
  // CardsPerRegion). All those fields are considered constant
  // throughout the JVM's execution, therefore they should only be set
  // up once during initialization time.
  static void setup_heap_region_size(size_t initial_heap_size, size_t max_heap_size);

  // All allocated blocks are occupied by objects in a HeapRegion
  bool block_is_obj(const HeapWord* p) const;

  // Returns the object size for all valid block starts
  // and the amount of unallocated words if called on top()
  size_t block_size(const HeapWord* p) const;

  // Override for scan_and_forward support.
  void prepare_for_compaction(CompactPoint* cp);

  inline HeapWord* par_allocate_no_bot_updates(size_t min_word_size, size_t desired_word_size, size_t* word_size);
  inline HeapWord* allocate_no_bot_updates(size_t word_size);
  inline HeapWord* allocate_no_bot_updates(size_t min_word_size, size_t desired_word_size, size_t* actual_size);

  // If this region is a member of a HeapRegionManager, the index in that
  // sequence, otherwise -1.
  uint hrm_index() const { return _hrm_index; }

  // The number of bytes marked live in the region in the last marking phase.
  size_t marked_bytes()    { return _prev_marked_bytes; }
  size_t live_bytes() {
    return (top() - prev_top_at_mark_start()) * HeapWordSize + marked_bytes();
  }

  // The number of bytes counted in the next marking.
  size_t next_marked_bytes() { return _next_marked_bytes; }
  // The number of bytes live wrt the next marking.
  size_t next_live_bytes() {
    return
      (top() - next_top_at_mark_start()) * HeapWordSize + next_marked_bytes();
  }

  // A lower bound on the amount of garbage bytes in the region.
  size_t garbage_bytes() {
    size_t used_at_mark_start_bytes =
      (prev_top_at_mark_start() - bottom()) * HeapWordSize;
    return used_at_mark_start_bytes - marked_bytes();
  }

  // Return the amount of bytes we'll reclaim if we collect this
  // region. This includes not only the known garbage bytes in the
  // region but also any unallocated space in it, i.e., [top, end),
  // since it will also be reclaimed if we collect the region.
  size_t reclaimable_bytes() {
    size_t known_live_bytes = live_bytes();
    assert(known_live_bytes <= capacity(), "sanity");
    return capacity() - known_live_bytes;
  }

  // An upper bound on the number of live bytes in the region.
  size_t max_live_bytes() { return used() - garbage_bytes(); }

  void add_to_marked_bytes(size_t incr_bytes) {
    _next_marked_bytes = _next_marked_bytes + incr_bytes;
  }

  void zero_marked_bytes()      {
    _prev_marked_bytes = _next_marked_bytes = 0;
  }

  const char* get_type_str() const { return _type.get_str(); }
  const char* get_short_type_str() const { return _type.get_short_str(); }
  G1HeapRegionTraceType::Type get_trace_type() { return _type.get_trace_type(); }

  bool is_free() const { return _type.is_free(); }

  bool is_young()    const { return _type.is_young();    }
  bool is_eden()     const { return _type.is_eden();     }
  bool is_survivor() const { return _type.is_survivor(); }

  bool is_humongous() const { return _type.is_humongous(); }
  bool is_starts_humongous() const { return _type.is_starts_humongous(); }
  bool is_continues_humongous() const { return _type.is_continues_humongous();   }

  bool is_old() const { return _type.is_old(); }

  // A pinned region contains objects which are not moved by garbage collections.
  // Humongous regions and archive regions are pinned.
  bool is_pinned() const { return _type.is_pinned(); }

  // An archive region is a pinned region, also tagged as old, which
  // should not be marked during mark/sweep. This allows the address
  // space to be shared by JVM instances.
  bool is_archive() const { return _type.is_archive(); }

  // For a humongous region, region in which it starts.
  HeapRegion* humongous_start_region() const {
    return _humongous_start_region;
  }

  // Makes the current region be a "starts humongous" region, i.e.,
  // the first region in a series of one or more contiguous regions
  // that will contain a single "humongous" object.
  //
  // obj_top : points to the top of the humongous object.
  // fill_size : size of the filler object at the end of the region series.
  void set_starts_humongous(HeapWord* obj_top, size_t fill_size);

  // Makes the current region be a "continues humongous'
  // region. first_hr is the "start humongous" region of the series
  // which this region will be part of.
  void set_continues_humongous(HeapRegion* first_hr);

  // Unsets the humongous-related fields on the region.
  void clear_humongous();

  // If the region has a remembered set, return a pointer to it.
  HeapRegionRemSet* rem_set() const {
    return _rem_set;
  }

  inline bool in_collection_set() const;

  inline HeapRegion* next_in_collection_set() const;
  inline void set_next_in_collection_set(HeapRegion* r);

  void set_allocation_context(AllocationContext_t context) {
    _allocation_context = context;
  }

  AllocationContext_t  allocation_context() const {
    return _allocation_context;
  }

  // Methods used by the HeapRegionSetBase class and subclasses.

  // Getter and setter for the next and prev fields used to link regions into
  // linked lists.
  HeapRegion* next()              { return _next; }
  HeapRegion* prev()              { return _prev; }

  void set_next(HeapRegion* next) { _next = next; }
  void set_prev(HeapRegion* prev) { _prev = prev; }

  // Every region added to a set is tagged with a reference to that
  // set. This is used for doing consistency checking to make sure that
  // the contents of a set are as they should be and it's only
  // available in non-product builds.
#ifdef ASSERT
  void set_containing_set(HeapRegionSetBase* containing_set) {
    assert((containing_set == NULL && _containing_set != NULL) ||
           (containing_set != NULL && _containing_set == NULL),
           "containing_set: " PTR_FORMAT " "
           "_containing_set: " PTR_FORMAT,
           p2i(containing_set), p2i(_containing_set));

    _containing_set = containing_set;
  }

  HeapRegionSetBase* containing_set() { return _containing_set; }
#else // ASSERT
  void set_containing_set(HeapRegionSetBase* containing_set) { }

  // containing_set() is only used in asserts so there's no reason
  // to provide a dummy version of it.
#endif // ASSERT

  HeapRegion* get_next_young_region() { return _next_young_region; }
  void set_next_young_region(HeapRegion* hr) {
    _next_young_region = hr;
  }

  // Reset HR stuff to default values.
  void hr_clear(bool par, bool clear_space, bool locked = false);
  void par_clear();

  // Get the start of the unmarked area in this region.
  HeapWord* prev_top_at_mark_start() const { return _prev_top_at_mark_start; }
  HeapWord* next_top_at_mark_start() const { return _next_top_at_mark_start; }

  // Note the start or end of marking. This tells the heap region
  // that the collector is about to start or has finished (concurrently)
  // marking the heap.

  // Notify the region that concurrent marking is starting. Initialize
  // all fields related to the next marking info.
  inline void note_start_of_marking();

  // Notify the region that concurrent marking has finished. Copy the
  // (now finalized) next marking info fields into the prev marking
  // info fields.
  inline void note_end_of_marking();

  // Notify the region that it will be used as to-space during a GC
  // and we are about to start copying objects into it.
  inline void note_start_of_copying(bool during_initial_mark);

  // Notify the region that it ceases being to-space during a GC and
  // we will not copy objects into it any more.
  inline void note_end_of_copying(bool during_initial_mark);

  // Notify the region that we are about to start processing
  // self-forwarded objects during evac failure handling.
  void note_self_forwarding_removal_start(bool during_initial_mark,
                                          bool during_conc_mark);

  // Notify the region that we have finished processing self-forwarded
  // objects during evac failure handling.
  void note_self_forwarding_removal_end(bool during_initial_mark,
                                        bool during_conc_mark,
                                        size_t marked_bytes);

  // Returns "false" iff no object in the region was allocated when the
  // last mark phase ended.
  bool is_marked() { return _prev_top_at_mark_start != bottom(); }

  void reset_during_compaction() {
    assert(is_humongous(),
           "should only be called for humongous regions");

    zero_marked_bytes();
    init_top_at_mark_start();
  }

  void calc_gc_efficiency(void);
  double gc_efficiency() { return _gc_efficiency;}

  int  young_index_in_cset() const { return _young_index_in_cset; }
  void set_young_index_in_cset(int index) {
    assert( (index == -1) || is_young(), "pre-condition" );
    _young_index_in_cset = index;
  }

  int age_in_surv_rate_group() {
    assert( _surv_rate_group != NULL, "pre-condition" );
    assert( _age_index > -1, "pre-condition" );
    return _surv_rate_group->age_in_group(_age_index);
  }

  void record_surv_words_in_group(size_t words_survived) {
    assert( _surv_rate_group != NULL, "pre-condition" );
    assert( _age_index > -1, "pre-condition" );
    int age_in_group = age_in_surv_rate_group();
    _surv_rate_group->record_surviving_words(age_in_group, words_survived);
  }

  int age_in_surv_rate_group_cond() {
    if (_surv_rate_group != NULL)
      return age_in_surv_rate_group();
    else
      return -1;
  }

  SurvRateGroup* surv_rate_group() {
    return _surv_rate_group;
  }

  void install_surv_rate_group(SurvRateGroup* surv_rate_group) {
    assert( surv_rate_group != NULL, "pre-condition" );
    assert( _surv_rate_group == NULL, "pre-condition" );
    assert( is_young(), "pre-condition" );

    _surv_rate_group = surv_rate_group;
    _age_index = surv_rate_group->next_age_index();
  }

  void uninstall_surv_rate_group() {
    if (_surv_rate_group != NULL) {
      assert( _age_index > -1, "pre-condition" );
      assert( is_young(), "pre-condition" );

      _surv_rate_group = NULL;
      _age_index = -1;
    } else {
      assert( _age_index == -1, "pre-condition" );
    }
  }

  void set_free();

  void set_eden();
  void set_eden_pre_gc();
  void set_survivor();

  void set_old();

  void set_archive();

  // Determine if an object has been allocated since the last
  // mark performed by the collector. This returns true iff the object
  // is within the unmarked area of the region.
  bool obj_allocated_since_prev_marking(oop obj) const {
    return (HeapWord *) obj >= prev_top_at_mark_start();
  }
  bool obj_allocated_since_next_marking(oop obj) const {
    return (HeapWord *) obj >= next_top_at_mark_start();
  }

  // Returns the "evacuation_failed" property of the region.
  bool evacuation_failed() { return _evacuation_failed; }

  // Sets the "evacuation_failed" property of the region.
  void set_evacuation_failed(bool b) {
    _evacuation_failed = b;

    if (b) {
      _next_marked_bytes = 0;
    }
  }

  // Requires that "mr" be entirely within the region.
  // Apply "cl->do_object" to all objects that intersect with "mr".
  // If the iteration encounters an unparseable portion of the region,
  // or if "cl->abort()" is true after a closure application,
  // terminate the iteration and return the address of the start of the
  // subregion that isn't done.  (The two can be distinguished by querying
  // "cl->abort()".)  Return of "NULL" indicates that the iteration
  // completed.
  HeapWord*
  object_iterate_mem_careful(MemRegion mr, ObjectClosure* cl);

  // filter_young: if true and the region is a young region then we
  // skip the iteration.
  // card_ptr: if not NULL, and we decide that the card is not young
  // and we iterate over it, we'll clean the card before we start the
  // iteration.
  HeapWord*
  oops_on_card_seq_iterate_careful(MemRegion mr,
                                   FilterOutOfRegionClosure* cl,
                                   bool filter_young,
                                   jbyte* card_ptr);

  size_t recorded_rs_length() const        { return _recorded_rs_length; }
  double predicted_elapsed_time_ms() const { return _predicted_elapsed_time_ms; }
  size_t predicted_bytes_to_copy() const   { return _predicted_bytes_to_copy; }

  void set_recorded_rs_length(size_t rs_length) {
    _recorded_rs_length = rs_length;
  }

  void set_predicted_elapsed_time_ms(double ms) {
    _predicted_elapsed_time_ms = ms;
  }

  void set_predicted_bytes_to_copy(size_t bytes) {
    _predicted_bytes_to_copy = bytes;
  }

  virtual CompactibleSpace* next_compaction_space() const;

  virtual void reset_after_compaction();

  // Routines for managing a list of code roots (attached to the
  // this region's RSet) that point into this heap region.
  void add_strong_code_root(nmethod* nm);
  void add_strong_code_root_locked(nmethod* nm);
  void remove_strong_code_root(nmethod* nm);

  // Applies blk->do_code_blob() to each of the entries in
  // the strong code roots list for this region
  void strong_code_roots_do(CodeBlobClosure* blk) const;

  // Verify that the entries on the strong code root list for this
  // region are live and include at least one pointer into this region.
  void verify_strong_code_roots(VerifyOption vo, bool* failures) const;

  void print() const;
  void print_on(outputStream* st) const;

  // vo == UsePrevMarking  -> use "prev" marking information,
  // vo == UseNextMarking -> use "next" marking information
  // vo == UseMarkWord    -> use the mark word in the object header
  //
  // NOTE: Only the "prev" marking information is guaranteed to be
  // consistent most of the time, so most calls to this should use
  // vo == UsePrevMarking.
  // Currently, there is only one case where this is called with
  // vo == UseNextMarking, which is to verify the "next" marking
  // information at the end of remark.
  // Currently there is only one place where this is called with
  // vo == UseMarkWord, which is to verify the marking during a
  // full GC.
  void verify(VerifyOption vo, bool *failures) const;

  // Override; it uses the "prev" marking information
  virtual void verify() const;

  void verify_rem_set(VerifyOption vo, bool *failures) const;
  void verify_rem_set() const;
};

// HeapRegionClosure is used for iterating over regions.
// Terminates the iteration when the "doHeapRegion" method returns "true".
class HeapRegionClosure : public StackObj {
  friend class HeapRegionManager;
  friend class G1CollectedHeap;

  bool _complete;
  void incomplete() { _complete = false; }

 public:
  HeapRegionClosure(): _complete(true) {}

  // Typically called on each region until it returns true.
  virtual bool doHeapRegion(HeapRegion* r) = 0;

  // True after iteration if the closure was applied to all heap regions
  // and returned "false" in all cases.
  bool complete() { return _complete; }
};

#endif // SHARE_VM_GC_G1_HEAPREGION_HPP
