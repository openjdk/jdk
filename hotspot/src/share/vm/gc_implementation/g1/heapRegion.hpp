/*
 * Copyright 2001-2008 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

#ifndef SERIALGC

// A HeapRegion is the smallest piece of a G1CollectedHeap that
// can be collected independently.

// NOTE: Although a HeapRegion is a Space, its
// Space::initDirtyCardClosure method must not be called.
// The problem is that the existence of this method breaks
// the independence of barrier sets from remembered sets.
// The solution is to remove this method from the definition
// of a Space.

class CompactibleSpace;
class ContiguousSpace;
class HeapRegionRemSet;
class HeapRegionRemSetIterator;
class HeapRegion;

// A dirty card to oop closure for heap regions. It
// knows how to get the G1 heap and how to use the bitmap
// in the concurrent marker used by G1 to filter remembered
// sets.

class HeapRegionDCTOC : public ContiguousSpaceDCTOC {
public:
  // Specification of possible DirtyCardToOopClosure filtering.
  enum FilterKind {
    NoFilterKind,
    IntoCSFilterKind,
    OutOfRegionFilterKind
  };

protected:
  HeapRegion* _hr;
  FilterKind _fk;
  G1CollectedHeap* _g1;

  void walk_mem_region_with_cl(MemRegion mr,
                               HeapWord* bottom, HeapWord* top,
                               OopClosure* cl);

  // We don't specialize this for FilteringClosure; filtering is handled by
  // the "FilterKind" mechanism.  But we provide this to avoid a compiler
  // warning.
  void walk_mem_region_with_cl(MemRegion mr,
                               HeapWord* bottom, HeapWord* top,
                               FilteringClosure* cl) {
    HeapRegionDCTOC::walk_mem_region_with_cl(mr, bottom, top,
                                                       (OopClosure*)cl);
  }

  // Get the actual top of the area on which the closure will
  // operate, given where the top is assumed to be (the end of the
  // memory region passed to do_MemRegion) and where the object
  // at the top is assumed to start. For example, an object may
  // start at the top but actually extend past the assumed top,
  // in which case the top becomes the end of the object.
  HeapWord* get_actual_top(HeapWord* top, HeapWord* top_obj) {
    return ContiguousSpaceDCTOC::get_actual_top(top, top_obj);
  }

  // Walk the given memory region from bottom to (actual) top
  // looking for objects and applying the oop closure (_cl) to
  // them. The base implementation of this treats the area as
  // blocks, where a block may or may not be an object. Sub-
  // classes should override this to provide more accurate
  // or possibly more efficient walking.
  void walk_mem_region(MemRegion mr, HeapWord* bottom, HeapWord* top) {
    Filtering_DCTOC::walk_mem_region(mr, bottom, top);
  }

public:
  HeapRegionDCTOC(G1CollectedHeap* g1,
                  HeapRegion* hr, OopClosure* cl,
                  CardTableModRefBS::PrecisionStyle precision,
                  FilterKind fk);
};


// The complicating factor is that BlockOffsetTable diverged
// significantly, and we need functionality that is only in the G1 version.
// So I copied that code, which led to an alternate G1 version of
// OffsetTableContigSpace.  If the two versions of BlockOffsetTable could
// be reconciled, then G1OffsetTableContigSpace could go away.

// The idea behind time stamps is the following. Doing a save_marks on
// all regions at every GC pause is time consuming (if I remember
// well, 10ms or so). So, we would like to do that only for regions
// that are GC alloc regions. To achieve this, we use time
// stamps. For every evacuation pause, G1CollectedHeap generates a
// unique time stamp (essentially a counter that gets
// incremented). Every time we want to call save_marks on a region,
// we set the saved_mark_word to top and also copy the current GC
// time stamp to the time stamp field of the space. Reading the
// saved_mark_word involves checking the time stamp of the
// region. If it is the same as the current GC time stamp, then we
// can safely read the saved_mark_word field, as it is valid. If the
// time stamp of the region is not the same as the current GC time
// stamp, then we instead read top, as the saved_mark_word field is
// invalid. Time stamps (on the regions and also on the
// G1CollectedHeap) are reset at every cleanup (we iterate over
// the regions anyway) and at the end of a Full GC. The current scheme
// that uses sequential unsigned ints will fail only if we have 4b
// evacuation pauses between two cleanups, which is _highly_ unlikely.

class G1OffsetTableContigSpace: public ContiguousSpace {
  friend class VMStructs;
 protected:
  G1BlockOffsetArrayContigSpace _offsets;
  Mutex _par_alloc_lock;
  volatile unsigned _gc_time_stamp;

 public:
  // Constructor.  If "is_zeroed" is true, the MemRegion "mr" may be
  // assumed to contain zeros.
  G1OffsetTableContigSpace(G1BlockOffsetSharedArray* sharedOffsetArray,
                           MemRegion mr, bool is_zeroed = false);

  void set_bottom(HeapWord* value);
  void set_end(HeapWord* value);

  virtual HeapWord* saved_mark_word() const;
  virtual void set_saved_mark();
  void reset_gc_time_stamp() { _gc_time_stamp = 0; }

  virtual void initialize(MemRegion mr, bool clear_space, bool mangle_space);
  virtual void clear(bool mangle_space);

  HeapWord* block_start(const void* p);
  HeapWord* block_start_const(const void* p) const;

  // Add offset table update.
  virtual HeapWord* allocate(size_t word_size);
  HeapWord* par_allocate(size_t word_size);

  // MarkSweep support phase3
  virtual HeapWord* initialize_threshold();
  virtual HeapWord* cross_threshold(HeapWord* start, HeapWord* end);

  virtual void print() const;
};

class HeapRegion: public G1OffsetTableContigSpace {
  friend class VMStructs;
 private:

  enum HumongousType {
    NotHumongous = 0,
    StartsHumongous,
    ContinuesHumongous
  };

  // The next filter kind that should be used for a "new_dcto_cl" call with
  // the "traditional" signature.
  HeapRegionDCTOC::FilterKind _next_fk;

  // Requires that the region "mr" be dense with objects, and begin and end
  // with an object.
  void oops_in_mr_iterate(MemRegion mr, OopClosure* cl);

  // The remembered set for this region.
  // (Might want to make this "inline" later, to avoid some alloc failure
  // issues.)
  HeapRegionRemSet* _rem_set;

  G1BlockOffsetArrayContigSpace* offsets() { return &_offsets; }

 protected:
  // If this region is a member of a HeapRegionSeq, the index in that
  // sequence, otherwise -1.
  int  _hrs_index;

  HumongousType _humongous_type;
  // For a humongous region, region in which it starts.
  HeapRegion* _humongous_start_region;
  // For the start region of a humongous sequence, it's original end().
  HeapWord* _orig_end;

  // True iff the region is in current collection_set.
  bool _in_collection_set;

    // True iff the region is on the unclean list, waiting to be zero filled.
  bool _is_on_unclean_list;

  // True iff the region is on the free list, ready for allocation.
  bool _is_on_free_list;

  // Is this or has it been an allocation region in the current collection
  // pause.
  bool _is_gc_alloc_region;

  // True iff an attempt to evacuate an object in the region failed.
  bool _evacuation_failed;

  // A heap region may be a member one of a number of special subsets, each
  // represented as linked lists through the field below.  Currently, these
  // sets include:
  //   The collection set.
  //   The set of allocation regions used in a collection pause.
  //   Spaces that may contain gray objects.
  HeapRegion* _next_in_special_set;

  // next region in the young "generation" region set
  HeapRegion* _next_young_region;

  // For parallel heapRegion traversal.
  jint _claimed;

  // We use concurrent marking to determine the amount of live data
  // in each heap region.
  size_t _prev_marked_bytes;    // Bytes known to be live via last completed marking.
  size_t _next_marked_bytes;    // Bytes known to be live via in-progress marking.

  // See "sort_index" method.  -1 means is not in the array.
  int _sort_index;

  // Means it has (or at least had) a very large RS, and should not be
  // considered for membership in a collection set.
  enum PopularityState {
    NotPopular,
    PopularPending,
    Popular
  };
  PopularityState _popularity;

  // <PREDICTION>
  double _gc_efficiency;
  // </PREDICTION>

  enum YoungType {
    NotYoung,                   // a region is not young
    ScanOnly,                   // a region is young and scan-only
    Young,                      // a region is young
    Survivor                    // a region is young and it contains
                                // survivor
  };

  YoungType _young_type;
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

  // We've counted the marked bytes of objects below here.
  HeapWord* _top_at_conc_mark_count;

  void init_top_at_mark_start() {
    assert(_prev_marked_bytes == 0 &&
           _next_marked_bytes == 0,
           "Must be called after zero_marked_bytes.");
    HeapWord* bot = bottom();
    _prev_top_at_mark_start = bot;
    _next_top_at_mark_start = bot;
    _top_at_conc_mark_count = bot;
  }

  jint _zfs;  // A member of ZeroFillState.  Protected by ZF_lock.
  Thread* _zero_filler; // If _zfs is ZeroFilling, the thread that (last)
                        // made it so.

  void set_young_type(YoungType new_type) {
    //assert(_young_type != new_type, "setting the same type" );
    // TODO: add more assertions here
    _young_type = new_type;
  }

 public:
  // If "is_zeroed" is "true", the region "mr" can be assumed to contain zeros.
  HeapRegion(G1BlockOffsetSharedArray* sharedOffsetArray,
             MemRegion mr, bool is_zeroed);

  enum SomePublicConstants {
    // HeapRegions are GrainBytes-aligned
    // and have sizes that are multiples of GrainBytes.
    LogOfHRGrainBytes = 20,
    LogOfHRGrainWords = LogOfHRGrainBytes - LogHeapWordSize,
    GrainBytes = 1 << LogOfHRGrainBytes,
    GrainWords = 1 <<LogOfHRGrainWords,
    MaxAge = 2, NoOfAges = MaxAge+1
  };

  enum ClaimValues {
    InitialClaimValue     = 0,
    FinalCountClaimValue  = 1,
    NoteEndClaimValue     = 2,
    ScrubRemSetClaimValue = 3,
    ParVerifyClaimValue   = 4
  };

  // Concurrent refinement requires contiguous heap regions (in which TLABs
  // might be allocated) to be zero-filled.  Each region therefore has a
  // zero-fill-state.
  enum ZeroFillState {
    NotZeroFilled,
    ZeroFilling,
    ZeroFilled,
    Allocated
  };

  // If this region is a member of a HeapRegionSeq, the index in that
  // sequence, otherwise -1.
  int hrs_index() const { return _hrs_index; }
  void set_hrs_index(int index) { _hrs_index = index; }

  // The number of bytes marked live in the region in the last marking phase.
  size_t marked_bytes()    { return _prev_marked_bytes; }
  // The number of bytes counted in the next marking.
  size_t next_marked_bytes() { return _next_marked_bytes; }
  // The number of bytes live wrt the next marking.
  size_t next_live_bytes() {
    return (top() - next_top_at_mark_start())
      * HeapWordSize
      + next_marked_bytes();
  }

  // A lower bound on the amount of garbage bytes in the region.
  size_t garbage_bytes() {
    size_t used_at_mark_start_bytes =
      (prev_top_at_mark_start() - bottom()) * HeapWordSize;
    assert(used_at_mark_start_bytes >= marked_bytes(),
           "Can't mark more than we have.");
    return used_at_mark_start_bytes - marked_bytes();
  }

  // An upper bound on the number of live bytes in the region.
  size_t max_live_bytes() { return used() - garbage_bytes(); }

  void add_to_marked_bytes(size_t incr_bytes) {
    _next_marked_bytes = _next_marked_bytes + incr_bytes;
    guarantee( _next_marked_bytes <= used(), "invariant" );
  }

  void zero_marked_bytes()      {
    _prev_marked_bytes = _next_marked_bytes = 0;
  }

  bool isHumongous() const { return _humongous_type != NotHumongous; }
  bool startsHumongous() const { return _humongous_type == StartsHumongous; }
  bool continuesHumongous() const { return _humongous_type == ContinuesHumongous; }
  // For a humongous region, region in which it starts.
  HeapRegion* humongous_start_region() const {
    return _humongous_start_region;
  }

  // Causes the current region to represent a humongous object spanning "n"
  // regions.
  virtual void set_startsHumongous();

  // The regions that continue a humongous sequence should be added using
  // this method, in increasing address order.
  void set_continuesHumongous(HeapRegion* start);

  void add_continuingHumongousRegion(HeapRegion* cont);

  // If the region has a remembered set, return a pointer to it.
  HeapRegionRemSet* rem_set() const {
    return _rem_set;
  }

  // True iff the region is in current collection_set.
  bool in_collection_set() const {
    return _in_collection_set;
  }
  void set_in_collection_set(bool b) {
    _in_collection_set = b;
  }
  HeapRegion* next_in_collection_set() {
    assert(in_collection_set(), "should only invoke on member of CS.");
    assert(_next_in_special_set == NULL ||
           _next_in_special_set->in_collection_set(),
           "Malformed CS.");
    return _next_in_special_set;
  }
  void set_next_in_collection_set(HeapRegion* r) {
    assert(in_collection_set(), "should only invoke on member of CS.");
    assert(r == NULL || r->in_collection_set(), "Malformed CS.");
    _next_in_special_set = r;
  }

  // True iff it is or has been an allocation region in the current
  // collection pause.
  bool is_gc_alloc_region() const {
    return _is_gc_alloc_region;
  }
  void set_is_gc_alloc_region(bool b) {
    _is_gc_alloc_region = b;
  }
  HeapRegion* next_gc_alloc_region() {
    assert(is_gc_alloc_region(), "should only invoke on member of CS.");
    assert(_next_in_special_set == NULL ||
           _next_in_special_set->is_gc_alloc_region(),
           "Malformed CS.");
    return _next_in_special_set;
  }
  void set_next_gc_alloc_region(HeapRegion* r) {
    assert(is_gc_alloc_region(), "should only invoke on member of CS.");
    assert(r == NULL || r->is_gc_alloc_region(), "Malformed CS.");
    _next_in_special_set = r;
  }

  bool is_reserved() {
    return popular();
  }

  bool is_on_free_list() {
    return _is_on_free_list;
  }

  void set_on_free_list(bool b) {
    _is_on_free_list = b;
  }

  HeapRegion* next_from_free_list() {
    assert(is_on_free_list(),
           "Should only invoke on free space.");
    assert(_next_in_special_set == NULL ||
           _next_in_special_set->is_on_free_list(),
           "Malformed Free List.");
    return _next_in_special_set;
  }

  void set_next_on_free_list(HeapRegion* r) {
    assert(r == NULL || r->is_on_free_list(), "Malformed free list.");
    _next_in_special_set = r;
  }

  bool is_on_unclean_list() {
    return _is_on_unclean_list;
  }

  void set_on_unclean_list(bool b);

  HeapRegion* next_from_unclean_list() {
    assert(is_on_unclean_list(),
           "Should only invoke on unclean space.");
    assert(_next_in_special_set == NULL ||
           _next_in_special_set->is_on_unclean_list(),
           "Malformed unclean List.");
    return _next_in_special_set;
  }

  void set_next_on_unclean_list(HeapRegion* r);

  HeapRegion* get_next_young_region() { return _next_young_region; }
  void set_next_young_region(HeapRegion* hr) {
    _next_young_region = hr;
  }

  // Allows logical separation between objects allocated before and after.
  void save_marks();

  // Reset HR stuff to default values.
  void hr_clear(bool par, bool clear_space);

  void initialize(MemRegion mr, bool clear_space, bool mangle_space);

  // Ensure that "this" is zero-filled.
  void ensure_zero_filled();
  // This one requires that the calling thread holds ZF_mon.
  void ensure_zero_filled_locked();

  // Get the start of the unmarked area in this region.
  HeapWord* prev_top_at_mark_start() const { return _prev_top_at_mark_start; }
  HeapWord* next_top_at_mark_start() const { return _next_top_at_mark_start; }

  // Apply "cl->do_oop" to (the addresses of) all reference fields in objects
  // allocated in the current region before the last call to "save_mark".
  void oop_before_save_marks_iterate(OopClosure* cl);

  // This call determines the "filter kind" argument that will be used for
  // the next call to "new_dcto_cl" on this region with the "traditional"
  // signature (i.e., the call below.)  The default, in the absence of a
  // preceding call to this method, is "NoFilterKind", and a call to this
  // method is necessary for each such call, or else it reverts to the
  // default.
  // (This is really ugly, but all other methods I could think of changed a
  // lot of main-line code for G1.)
  void set_next_filter_kind(HeapRegionDCTOC::FilterKind nfk) {
    _next_fk = nfk;
  }

  DirtyCardToOopClosure*
  new_dcto_closure(OopClosure* cl,
                   CardTableModRefBS::PrecisionStyle precision,
                   HeapRegionDCTOC::FilterKind fk);

#if WHASSUP
  DirtyCardToOopClosure*
  new_dcto_closure(OopClosure* cl,
                   CardTableModRefBS::PrecisionStyle precision,
                   HeapWord* boundary) {
    assert(boundary == NULL, "This arg doesn't make sense here.");
    DirtyCardToOopClosure* res = new_dcto_closure(cl, precision, _next_fk);
    _next_fk = HeapRegionDCTOC::NoFilterKind;
    return res;
  }
#endif

  //
  // Note the start or end of marking. This tells the heap region
  // that the collector is about to start or has finished (concurrently)
  // marking the heap.
  //

  // Note the start of a marking phase. Record the
  // start of the unmarked area of the region here.
  void note_start_of_marking(bool during_initial_mark) {
    init_top_at_conc_mark_count();
    _next_marked_bytes = 0;
    if (during_initial_mark && is_young() && !is_survivor())
      _next_top_at_mark_start = bottom();
    else
      _next_top_at_mark_start = top();
  }

  // Note the end of a marking phase. Install the start of
  // the unmarked area that was captured at start of marking.
  void note_end_of_marking() {
    _prev_top_at_mark_start = _next_top_at_mark_start;
    _prev_marked_bytes = _next_marked_bytes;
    _next_marked_bytes = 0;

    guarantee(_prev_marked_bytes <=
              (size_t) (prev_top_at_mark_start() - bottom()) * HeapWordSize,
              "invariant");
  }

  // After an evacuation, we need to update _next_top_at_mark_start
  // to be the current top.  Note this is only valid if we have only
  // ever evacuated into this region.  If we evacuate, allocate, and
  // then evacuate we are in deep doodoo.
  void note_end_of_copying() {
    assert(top() >= _next_top_at_mark_start,
           "Increase only");
    // Survivor regions will be scanned on the start of concurrent
    // marking.
    if (!is_survivor()) {
      _next_top_at_mark_start = top();
    }
  }

  // Returns "false" iff no object in the region was allocated when the
  // last mark phase ended.
  bool is_marked() { return _prev_top_at_mark_start != bottom(); }

  // If "is_marked()" is true, then this is the index of the region in
  // an array constructed at the end of marking of the regions in a
  // "desirability" order.
  int sort_index() {
    return _sort_index;
  }
  void set_sort_index(int i) {
    _sort_index = i;
  }

  void init_top_at_conc_mark_count() {
    _top_at_conc_mark_count = bottom();
  }

  void set_top_at_conc_mark_count(HeapWord *cur) {
    assert(bottom() <= cur && cur <= end(), "Sanity.");
    _top_at_conc_mark_count = cur;
  }

  HeapWord* top_at_conc_mark_count() {
    return _top_at_conc_mark_count;
  }

  void reset_during_compaction() {
    guarantee( isHumongous() && startsHumongous(),
               "should only be called for humongous regions");

    zero_marked_bytes();
    init_top_at_mark_start();
  }

  bool popular() { return _popularity == Popular; }
  void set_popular(bool b) {
    if (b) {
      _popularity = Popular;
    } else {
      _popularity = NotPopular;
    }
  }
  bool popular_pending() { return _popularity == PopularPending; }
  void set_popular_pending(bool b) {
    if (b) {
      _popularity = PopularPending;
    } else {
      _popularity = NotPopular;
    }
  }

  // <PREDICTION>
  void calc_gc_efficiency(void);
  double gc_efficiency() { return _gc_efficiency;}
  // </PREDICTION>

  bool is_young() const     { return _young_type != NotYoung; }
  bool is_scan_only() const { return _young_type == ScanOnly; }
  bool is_survivor() const  { return _young_type == Survivor; }

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

  void recalculate_age_in_surv_rate_group() {
    assert( _surv_rate_group != NULL, "pre-condition" );
    assert( _age_index > -1, "pre-condition" );
    _age_index = _surv_rate_group->recalculate_age_index(_age_index);
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

  void set_young() { set_young_type(Young); }

  void set_scan_only() { set_young_type(ScanOnly); }

  void set_survivor() { set_young_type(Survivor); }

  void set_not_young() { set_young_type(NotYoung); }

  // Determine if an object has been allocated since the last
  // mark performed by the collector. This returns true iff the object
  // is within the unmarked area of the region.
  bool obj_allocated_since_prev_marking(oop obj) const {
    return (HeapWord *) obj >= prev_top_at_mark_start();
  }
  bool obj_allocated_since_next_marking(oop obj) const {
    return (HeapWord *) obj >= next_top_at_mark_start();
  }

  // For parallel heapRegion traversal.
  bool claimHeapRegion(int claimValue);
  jint claim_value() { return _claimed; }
  // Use this carefully: only when you're sure no one is claiming...
  void set_claim_value(int claimValue) { _claimed = claimValue; }

  // Returns the "evacuation_failed" property of the region.
  bool evacuation_failed() { return _evacuation_failed; }

  // Sets the "evacuation_failed" property of the region.
  void set_evacuation_failed(bool b) {
    _evacuation_failed = b;

    if (b) {
      init_top_at_conc_mark_count();
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

  HeapWord*
  oops_on_card_seq_iterate_careful(MemRegion mr,
                                   FilterOutOfRegionClosure* cl);

  // The region "mr" is entirely in "this", and starts and ends at block
  // boundaries. The caller declares that all the contained blocks are
  // coalesced into one.
  void declare_filled_region_to_BOT(MemRegion mr) {
    _offsets.single_block(mr.start(), mr.end());
  }

  // A version of block start that is guaranteed to find *some* block
  // boundary at or before "p", but does not object iteration, and may
  // therefore be used safely when the heap is unparseable.
  HeapWord* block_start_careful(const void* p) const {
    return _offsets.block_start_careful(p);
  }

  // Requires that "addr" is within the region.  Returns the start of the
  // first ("careful") block that starts at or after "addr", or else the
  // "end" of the region if there is no such block.
  HeapWord* next_block_start_careful(HeapWord* addr);

  // Returns the zero-fill-state of the current region.
  ZeroFillState zero_fill_state() { return (ZeroFillState)_zfs; }
  bool zero_fill_is_allocated() { return _zfs == Allocated; }
  Thread* zero_filler() { return _zero_filler; }

  // Indicate that the contents of the region are unknown, and therefore
  // might require zero-filling.
  void set_zero_fill_needed() {
    set_zero_fill_state_work(NotZeroFilled);
  }
  void set_zero_fill_in_progress(Thread* t) {
    set_zero_fill_state_work(ZeroFilling);
    _zero_filler = t;
  }
  void set_zero_fill_complete();
  void set_zero_fill_allocated() {
    set_zero_fill_state_work(Allocated);
  }

  void set_zero_fill_state_work(ZeroFillState zfs);

  // This is called when a full collection shrinks the heap.
  // We want to set the heap region to a value which says
  // it is no longer part of the heap.  For now, we'll let "NotZF" fill
  // that role.
  void reset_zero_fill() {
    set_zero_fill_state_work(NotZeroFilled);
    _zero_filler = NULL;
  }

#define HeapRegion_OOP_SINCE_SAVE_MARKS_DECL(OopClosureType, nv_suffix)  \
  virtual void oop_since_save_marks_iterate##nv_suffix(OopClosureType* cl);
  SPECIALIZED_SINCE_SAVE_MARKS_CLOSURES(HeapRegion_OOP_SINCE_SAVE_MARKS_DECL)

  CompactibleSpace* next_compaction_space() const;

  virtual void reset_after_compaction();

  void print() const;
  void print_on(outputStream* st) const;

  // Override
  virtual void verify(bool allow_dirty) const;

#ifdef DEBUG
  HeapWord* allocate(size_t size);
#endif
};

// HeapRegionClosure is used for iterating over regions.
// Terminates the iteration when the "doHeapRegion" method returns "true".
class HeapRegionClosure : public StackObj {
  friend class HeapRegionSeq;
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

// A linked lists of heap regions.  It leaves the "next" field
// unspecified; that's up to subtypes.
class RegionList VALUE_OBJ_CLASS_SPEC {
protected:
  virtual HeapRegion* get_next(HeapRegion* chr) = 0;
  virtual void set_next(HeapRegion* chr,
                        HeapRegion* new_next) = 0;

  HeapRegion* _hd;
  HeapRegion* _tl;
  size_t _sz;

  // Protected constructor because this type is only meaningful
  // when the _get/_set next functions are defined.
  RegionList() : _hd(NULL), _tl(NULL), _sz(0) {}
public:
  void reset() {
    _hd = NULL;
    _tl = NULL;
    _sz = 0;
  }
  HeapRegion* hd() { return _hd; }
  HeapRegion* tl() { return _tl; }
  size_t sz() { return _sz; }
  size_t length();

  bool well_formed() {
    return
      ((hd() == NULL && tl() == NULL && sz() == 0)
       || (hd() != NULL && tl() != NULL && sz() > 0))
      && (sz() == length());
  }
  virtual void insert_before_head(HeapRegion* r);
  void prepend_list(RegionList* new_list);
  virtual HeapRegion* pop();
  void dec_sz() { _sz--; }
  // Requires that "r" is an element of the list, and is not the tail.
  void delete_after(HeapRegion* r);
};

class EmptyNonHRegionList: public RegionList {
protected:
  // Protected constructor because this type is only meaningful
  // when the _get/_set next functions are defined.
  EmptyNonHRegionList() : RegionList() {}

public:
  void insert_before_head(HeapRegion* r) {
    //    assert(r->is_empty(), "Better be empty");
    assert(!r->isHumongous(), "Better not be humongous.");
    RegionList::insert_before_head(r);
  }
  void prepend_list(EmptyNonHRegionList* new_list) {
    //    assert(new_list->hd() == NULL || new_list->hd()->is_empty(),
    //     "Better be empty");
    assert(new_list->hd() == NULL || !new_list->hd()->isHumongous(),
           "Better not be humongous.");
    //    assert(new_list->tl() == NULL || new_list->tl()->is_empty(),
    //     "Better be empty");
    assert(new_list->tl() == NULL || !new_list->tl()->isHumongous(),
           "Better not be humongous.");
    RegionList::prepend_list(new_list);
  }
};

class UncleanRegionList: public EmptyNonHRegionList {
public:
  HeapRegion* get_next(HeapRegion* hr) {
    return hr->next_from_unclean_list();
  }
  void set_next(HeapRegion* hr, HeapRegion* new_next) {
    hr->set_next_on_unclean_list(new_next);
  }

  UncleanRegionList() : EmptyNonHRegionList() {}

  void insert_before_head(HeapRegion* r) {
    assert(!r->is_on_free_list(),
           "Better not already be on free list");
    assert(!r->is_on_unclean_list(),
           "Better not already be on unclean list");
    r->set_zero_fill_needed();
    r->set_on_unclean_list(true);
    EmptyNonHRegionList::insert_before_head(r);
  }
  void prepend_list(UncleanRegionList* new_list) {
    assert(new_list->tl() == NULL || !new_list->tl()->is_on_free_list(),
           "Better not already be on free list");
    assert(new_list->tl() == NULL || new_list->tl()->is_on_unclean_list(),
           "Better already be marked as on unclean list");
    assert(new_list->hd() == NULL || !new_list->hd()->is_on_free_list(),
           "Better not already be on free list");
    assert(new_list->hd() == NULL || new_list->hd()->is_on_unclean_list(),
           "Better already be marked as on unclean list");
    EmptyNonHRegionList::prepend_list(new_list);
  }
  HeapRegion* pop() {
    HeapRegion* res = RegionList::pop();
    if (res != NULL) res->set_on_unclean_list(false);
    return res;
  }
};

// Local Variables: ***
// c-indentation-style: gnu ***
// End: ***

#endif // SERIALGC
