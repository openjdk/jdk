/*
 * Copyright (c) 2001, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_G1_G1HEAPREGION_HPP
#define SHARE_GC_G1_G1HEAPREGION_HPP

#include "gc/g1/g1BlockOffsetTable.hpp"
#include "gc/g1/g1HeapRegionTracer.hpp"
#include "gc/g1/g1HeapRegionTraceType.hpp"
#include "gc/g1/g1HeapRegionType.hpp"
#include "gc/g1/g1SurvRateGroup.hpp"
#include "gc/shared/ageTable.hpp"
#include "gc/shared/spaceDecorator.hpp"
#include "gc/shared/verifyOption.hpp"
#include "runtime/mutex.hpp"
#include "utilities/macros.hpp"

class G1CardSet;
class G1CardSetConfiguration;
class G1CollectedHeap;
class G1CMBitMap;
class G1CSetCandidateGroup;
class G1Predictions;
class G1HeapRegion;
class G1HeapRegionRemSet;
class G1HeapRegionSetBase;
class nmethod;

#define HR_FORMAT "%u:(%s)[" PTR_FORMAT "," PTR_FORMAT "," PTR_FORMAT "]"
#define HR_FORMAT_PARAMS(_hr_) \
                (_hr_)->hrm_index(), \
                (_hr_)->get_short_type_str(), \
                p2i((_hr_)->bottom()), p2i((_hr_)->top()), p2i((_hr_)->end())

// sentinel value for hrm_index
#define G1_NO_HRM_INDEX ((uint) -1)

// A G1HeapRegion is the smallest piece of a G1CollectedHeap that
// can be collected independently.

// Each heap region is self contained. top() and end() can never
// be set beyond the end of the region. For humongous objects,
// the first region is a StartsHumongous region. If the humongous
// object is larger than a heap region, the following regions will
// be of type ContinuesHumongous. In this case the top() of the
// StartHumongous region and all ContinuesHumongous regions except
// the last will point to their own end. The last ContinuesHumongous
// region may have top() equal the end of object if there isn't
// room for filler objects to pad out to the end of the region.
class G1HeapRegion : public CHeapObj<mtGC> {
  friend class VMStructs;

  HeapWord* const _bottom;
  HeapWord* const _end;

  HeapWord* volatile _top;

  G1BlockOffsetTable* _bot;

  // When we need to retire an allocation region, while other threads
  // are also concurrently trying to allocate into it, we typically
  // allocate a dummy object at the end of the region to ensure that
  // no more allocations can take place in it. However, sometimes we
  // want to know where the end of the last "real" object we allocated
  // into the region was and this is what this keeps track.
  HeapWord* _pre_dummy_top;

public:
  HeapWord* bottom() const         { return _bottom; }
  HeapWord* end() const            { return _end;    }

  void set_top(HeapWord* value) { _top = value; }
  HeapWord* top() const { return _top; }

  // See the comment above in the declaration of _pre_dummy_top for an
  // explanation of what it is.
  void set_pre_dummy_top(HeapWord* pre_dummy_top) {
    assert(is_in(pre_dummy_top) && pre_dummy_top <= top(), "pre-condition");
    _pre_dummy_top = pre_dummy_top;
  }
  HeapWord* pre_dummy_top() const { return (_pre_dummy_top == nullptr) ? top() : _pre_dummy_top; }
  void reset_pre_dummy_top() { _pre_dummy_top = nullptr; }

  // Returns true iff the given the heap  region contains the
  // given address as part of an allocated object. This may
  // be a potentially, so we restrict its use to assertion checks only.
  bool is_in(const void* p) const {
    return is_in_reserved(p);
  }
  bool is_in(oop obj) const {
    return is_in((void*)obj);
  }
  // Returns true iff the given reserved memory of the space contains the
  // given address.
  bool is_in_reserved(const void* p) const { return _bottom <= p && p < _end; }

  size_t capacity() const { return byte_size(bottom(), end()); }
  size_t used() const { return byte_size(bottom(), top()); }
  size_t free() const { return byte_size(top(), end()); }

  bool is_empty() const { return used() == 0; }

private:

  void reset_after_full_gc_common();

  void clear(bool mangle_space);

  void mangle_unused_area() PRODUCT_RETURN;

  inline HeapWord* advance_to_block_containing_addr(const void* addr,
                                                    HeapWord* const pb,
                                                    HeapWord* first_block) const;

public:

  // Returns the address of the block reaching into or starting at addr.
  HeapWord* block_start(const void* addr) const;
  HeapWord* block_start(const void* addr, HeapWord* const pb) const;

  void object_iterate(ObjectClosure* blk);

  // At the given address create an object with the given size. If the region
  // is old the BOT will be updated if the object spans a threshold.
  void fill_with_dummy_object(HeapWord* address, size_t word_size, bool zap = true);

  // Create objects in the given range. The BOT will be updated if needed and
  // the created objects will have their header marked to show that they are
  // dead.
  void fill_range_with_dead_objects(HeapWord* start, HeapWord* end);

  // All allocations are done without updating the BOT. The BOT
  // needs to be kept in sync for old generation regions and
  // this is done by explicit updates when crossing thresholds.

  // Try to allocate at least min_word_size and up to desired_size from this HeapRegion.
  // Returns null if not possible, otherwise sets actual_word_size to the amount of
  // space allocated.
  // This version synchronizes with other calls to par_allocate().
  inline HeapWord* par_allocate(size_t min_word_size, size_t desired_word_size, size_t* word_size);
  inline HeapWord* allocate(size_t word_size);
  // Try to allocate at least min_word_size and up to desired_size from this region.
  // Returns null if not possible, otherwise sets actual_word_size to the amount of
  // space allocated.
  // This version assumes that all allocation requests to this HeapRegion are properly
  // synchronized.
  inline HeapWord* allocate(size_t min_word_size, size_t desired_word_size, size_t* actual_size);

  // Full GC support methods.

  inline void update_bot_for_block(HeapWord* start, HeapWord* end);

  void prepare_for_full_gc();
  // Update heap region that has been compacted to be consistent after Full GC.
  void reset_compacted_after_full_gc(HeapWord* new_top);
  // Update skip-compacting heap region to be consistent after Full GC.
  void reset_skip_compacting_after_full_gc();

  // All allocated blocks are occupied by objects in a G1HeapRegion.
  bool block_is_obj(const HeapWord* p, HeapWord* pb) const;

  // Returns the object size for all valid block starts. If parsable_bottom (pb)
  // is given, calculates the block size based on that parsable_bottom, not the
  // current value of this G1HeapRegion.
  size_t block_size(const HeapWord* p) const;
  size_t block_size(const HeapWord* p, HeapWord* pb) const;

  // Scans through the region using the bitmap to determine what
  // objects to call size_t ApplyToMarkedClosure::apply(oop) for.
  template<typename ApplyToMarkedClosure>
  inline void apply_to_marked_objects(G1CMBitMap* bitmap, ApplyToMarkedClosure* closure);

  // Update the BOT for the entire region - assumes that all objects are parsable
  // and contiguous for this region.
  void update_bot();

private:
  // The remembered set for this region.
  G1HeapRegionRemSet* _rem_set;

  // Cached index of this region in the heap region sequence.
  const uint _hrm_index;

  G1HeapRegionType _type;

  // For a humongous region, region in which it starts.
  G1HeapRegion* _humongous_start_region;

  static const uint InvalidCSetIndex = UINT_MAX;

  // The index in the optional regions array, if this region
  // is considered optional during a mixed collections.
  uint _index_in_opt_cset;

  // Fields used by the G1HeapRegionSetBase class and subclasses.
  G1HeapRegion* _next;
  G1HeapRegion* _prev;
#ifdef ASSERT
  G1HeapRegionSetBase* _containing_set;
#endif // ASSERT

  // The area above this limit is fully parsable. This limit
  // is equal to bottom except
  //
  // * from Remark and until the region has been scrubbed concurrently. The
  //   scrubbing ensures that all dead objects (with possibly unloaded classes)
  //   have been replaced with filler objects that are parsable.
  // * after the marking phase in the Full GC pause until the objects have been
  //   moved. Some (debug) code iterates over the heap after marking but before
  //   compaction.
  //
  // Below this limit the marking bitmap must be used to determine size and
  // liveness.
  HeapWord* volatile _parsable_bottom;

  // Amount of dead data in the region.
  size_t _garbage_bytes;

  // Approximate number of references to this regions at the end of concurrent
  // marking. We we do not mark through all objects, so this is an estimate.
  size_t _incoming_refs;

  // Data for young region survivor prediction.
  uint  _young_index_in_cset;
  G1SurvRateGroup* _surv_rate_group;
  uint  _age_index;

  // NUMA node.
  uint _node_index;

  // Number of objects in this region that are currently pinned.
  volatile size_t _pinned_object_count;

  void report_region_type_change(G1HeapRegionTraceType::Type to);

  template <class Closure, bool in_gc_pause>
  inline HeapWord* oops_on_memregion_iterate(MemRegion mr, Closure* cl);

  template <class Closure>
  inline HeapWord* oops_on_memregion_iterate_in_unparsable(MemRegion mr, HeapWord* block_start, Closure* cl);

  // Iterate over the references covered by the given MemRegion in a humongous
  // object and apply the given closure to them.
  // Humongous objects are allocated directly in the old-gen. So we need special
  // handling for concurrent processing encountering an in-progress allocation.
  // Returns the address after the last actually scanned or null if the area could
  // not be scanned (That should only happen when invoked concurrently with the
  // mutator).
  template <class Closure, bool in_gc_pause>
  inline HeapWord* do_oops_on_memregion_in_humongous(MemRegion mr,
                                                     Closure* cl);

  inline bool is_marked_in_bitmap(oop obj) const;

  inline HeapWord* next_live_in_unparsable(G1CMBitMap* bitmap, const HeapWord* p, HeapWord* limit) const;
  inline HeapWord* next_live_in_unparsable(const HeapWord* p, HeapWord* limit) const;

public:
  G1HeapRegion(uint hrm_index,
             G1BlockOffsetTable* bot,
             MemRegion mr,
             G1CardSetConfiguration* config);

  // If this region is a member of a G1HeapRegionManager, the index in that
  // sequence, otherwise -1.
  uint hrm_index() const { return _hrm_index; }

  // Initializing the G1HeapRegion not only resets the data structure, but also
  // resets the BOT for that heap region.
  // The default values for clear_space means that we will do the clearing if
  // there's clearing to be done ourselves. We also always mangle the space.
  void initialize(bool clear_space = false, bool mangle_space = SpaceDecorator::Mangle);

  static uint   LogOfHRGrainBytes;
  static uint   LogCardsPerRegion;

  // Atomically adjust the pinned object count by the given value. Value must not
  // be zero.
  inline void add_pinned_object_count(size_t value);

  static size_t GrainBytes;
  static size_t GrainWords;
  static size_t CardsPerRegion;

  static size_t align_up_to_region_byte_size(size_t sz) {
    return align_up(sz, GrainBytes);
  }

  // Returns whether a field is in the same region as the obj it points to.
  template <typename T>
  static bool is_in_same_region(T* p, oop obj) {
    assert(p != nullptr, "p can't be null");
    assert(obj != nullptr, "obj can't be null");
    return (((uintptr_t) p ^ cast_from_oop<uintptr_t>(obj)) >> LogOfHRGrainBytes) == 0;
  }

  static size_t max_region_size();
  static size_t max_ergonomics_size();
  static size_t min_region_size_in_words();

  // It sets up the heap region size (GrainBytes / GrainWords), as well as
  // other related fields that are based on the heap region size
  // (LogOfHRGrainBytes / CardsPerRegion). All those fields are considered
  // constant throughout the JVM's execution, therefore they should only be set
  // up once during initialization time.
  static void setup_heap_region_size(size_t max_heap_size);

  // An upper bound on the number of live bytes in the region.
  size_t live_bytes() const {
    return used() - garbage_bytes();
  }

  // A lower bound on the amount of garbage bytes in the region.
  size_t garbage_bytes() const { return _garbage_bytes; }

  // Return the amount of bytes we'll reclaim if we collect this
  // region. This includes not only the known garbage bytes in the
  // region but also any unallocated space in it, i.e., [top, end),
  // since it will also be reclaimed if we collect the region.
  size_t reclaimable_bytes() {
    size_t known_live_bytes = live_bytes();
    assert(known_live_bytes <= capacity(), "sanity %u %zu %zu %zu", hrm_index(), known_live_bytes, used(), garbage_bytes());
    return capacity() - known_live_bytes;
  }

  size_t incoming_refs() { return _incoming_refs; }

  inline bool is_collection_set_candidate() const;

  // Retrieve parsable bottom; since it may be modified concurrently, outside a
  // safepoint the _acquire method must be used.
  HeapWord* parsable_bottom() const;
  HeapWord* parsable_bottom_acquire() const;
  void reset_parsable_bottom();

  // Note the start or end of marking. This tells the heap region
  // that the collector is about to start or has finished (concurrently)
  // marking the heap.

  // Notify the region that concurrent marking has finished. Passes TAMS, the number of
  // bytes marked between bottom and TAMS, and the estimate for incoming references.
  inline void note_end_of_marking(HeapWord* top_at_mark_start, size_t marked_bytes, size_t incoming_refs);

  // Notify the region that scrubbing has completed.
  inline void note_end_of_scrubbing();

  // During the concurrent scrubbing phase, can there be any areas with unloaded
  // classes or dead objects in this region?
  // This set only includes old regions - humongous regions only
  // contain a single object which is either dead or live, and young regions are never even
  // considered during concurrent scrub.
  bool needs_scrubbing() const;
  // Same question as above, during full gc. Full gc needs to scrub any region that
  // might be skipped for compaction. This includes young generation regions as the
  // region relabeling to old happens later than scrubbing.
  bool needs_scrubbing_during_full_gc() const { return is_young() || needs_scrubbing(); }

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

  bool is_old_or_humongous() const { return _type.is_old_or_humongous(); }

  size_t pinned_count() const { return Atomic::load(&_pinned_object_count); }
  bool has_pinned_objects() const { return pinned_count() > 0; }

  void set_free();

  void set_eden();
  void set_eden_pre_gc();
  void set_survivor();

  void move_to_old();
  void set_old();

  // For a humongous region, region in which it starts.
  G1HeapRegion* humongous_start_region() const {
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
  void set_continues_humongous(G1HeapRegion* first_hr);

  // Unsets the humongous-related fields on the region.
  void clear_humongous();

  void set_rem_set(G1HeapRegionRemSet* rem_set) { _rem_set = rem_set; }
  // If the region has a remembered set, return a pointer to it.
  G1HeapRegionRemSet* rem_set() const {
    return _rem_set;
  }

  inline bool in_collection_set() const;

  void prepare_remset_for_scan();

  // Methods used by the G1HeapRegionSetBase class and subclasses.

  // Getter and setter for the next and prev fields used to link regions into
  // linked lists.
  void set_next(G1HeapRegion* next) { _next = next; }
  G1HeapRegion* next()              { return _next; }

  void set_prev(G1HeapRegion* prev) { _prev = prev; }
  G1HeapRegion* prev()              { return _prev; }

  void unlink_from_list();

  // Every region added to a set is tagged with a reference to that
  // set. This is used for doing consistency checking to make sure that
  // the contents of a set are as they should be and it's only
  // available in non-product builds.
#ifdef ASSERT
  void set_containing_set(G1HeapRegionSetBase* containing_set) {
    assert((containing_set != nullptr && _containing_set == nullptr) ||
            containing_set == nullptr,
           "containing_set: " PTR_FORMAT " "
           "_containing_set: " PTR_FORMAT,
           p2i(containing_set), p2i(_containing_set));

    _containing_set = containing_set;
  }

  G1HeapRegionSetBase* containing_set() { return _containing_set; }
#else // ASSERT
  void set_containing_set(G1HeapRegionSetBase* containing_set) { }

  // containing_set() is only used in asserts so there's no reason
  // to provide a dummy version of it.
#endif // ASSERT


  // Reset the G1HeapRegion to default values and clear its remembered set.
  // If clear_space is true, clear the G1HeapRegion's memory.
  // Callers must ensure this is not called by multiple threads at the same time.
  void hr_clear(bool clear_space);
  // Clear the card table corresponding to this region.
  void clear_cardtable();

  // Notify the region that an evacuation failure occurred for an object within this
  // region.
  void note_evacuation_failure();

  // Notify the region that we have partially finished processing self-forwarded
  // objects during evacuation failure handling.
  void note_self_forward_chunk_done(size_t garbage_bytes);

  uint index_in_opt_cset() const {
    assert(has_index_in_opt_cset(), "Opt cset index not set.");
    return _index_in_opt_cset;
  }
  bool has_index_in_opt_cset() const { return _index_in_opt_cset != InvalidCSetIndex; }
  void set_index_in_opt_cset(uint index) { _index_in_opt_cset = index; }
  void clear_index_in_opt_cset() { _index_in_opt_cset = InvalidCSetIndex; }

  uint  young_index_in_cset() const { return _young_index_in_cset; }
  void clear_young_index_in_cset() { _young_index_in_cset = 0; }
  void set_young_index_in_cset(uint index) {
    assert(index != UINT_MAX, "just checking");
    assert(index != 0, "just checking");
    assert(is_young(), "pre-condition");
    _young_index_in_cset = index;
  }

  uint age_in_surv_rate_group() const;
  bool has_valid_age_in_surv_rate() const;

  bool has_surv_rate_group() const;

  double surv_rate_prediction(G1Predictions const& predictor) const;

  void install_surv_rate_group(G1SurvRateGroup* surv_rate_group);
  void uninstall_surv_rate_group();

  void install_cset_group(G1CSetCandidateGroup* cset_group);
  void uninstall_cset_group();

  void record_surv_words_in_group(size_t words_survived);

  // Determine if an address is in the parsable or the to-be-scrubbed area.
  inline        bool is_in_parsable_area(const void* const addr) const;
  inline static bool is_in_parsable_area(const void* const addr, const void* const pb);

  // Update the region state after a failed evacuation.
  void handle_evacuation_failure(bool retain);

  // Iterate over the objects overlapping the given memory region, applying cl
  // to all references in the region.  This is a helper for
  // G1RemSet::refine_card*, and is tightly coupled with them.
  // mr must not be empty. Must be trimmed to the allocated/parseable space in this region.
  // This region must be old or humongous.
  // Returns the next unscanned address if the designated objects were successfully
  // processed, null if an unparseable part of the heap was encountered (That should
  // only happen when invoked concurrently with the mutator).
  template <bool in_gc_pause, class Closure>
  inline HeapWord* oops_on_memregion_seq_iterate_careful(MemRegion mr, Closure* cl);

  // Routines for managing a list of code roots (attached to the
  // this region's RSet) that point into this heap region.
  void add_code_root(nmethod* nm);
  void remove_code_root(nmethod* nm);

  // Applies blk->do_nmethod() to each of the entries in
  // the code roots list for this region
  void code_roots_do(NMethodClosure* blk) const;

  uint node_index() const { return _node_index; }
  void set_node_index(uint node_index) { _node_index = node_index; }

  // Verify that the entries on the code root list for this
  // region are live and include at least one pointer into this region.
  // Returns whether there has been a failure.
  bool verify_code_roots(VerifyOption vo) const;
  bool verify_liveness_and_remset(VerifyOption vo) const;

  void print() const;
  void print_on(outputStream* st) const;

  bool verify(VerifyOption vo) const;
};

// G1HeapRegionClosure is used for iterating over regions.
// Terminates the iteration when the "do_heap_region" method returns "true".
class G1HeapRegionClosure : public StackObj {
  friend class G1HeapRegionManager;
  friend class G1CollectionSet;
  friend class G1CollectionSetCandidates;

  bool _is_complete;
  void set_incomplete() { _is_complete = false; }

public:
  G1HeapRegionClosure(): _is_complete(true) {}

  // Typically called on each region until it returns true.
  virtual bool do_heap_region(G1HeapRegion* r) = 0;

  // True after iteration if the closure was applied to all heap regions
  // and returned "false" in all cases.
  bool is_complete() { return _is_complete; }
};

class G1HeapRegionIndexClosure : public StackObj {
  friend class G1HeapRegionManager;
  friend class G1CollectionSet;
  friend class G1CollectionSetCandidates;

  bool _is_complete;
  void set_incomplete() { _is_complete = false; }

public:
  G1HeapRegionIndexClosure(): _is_complete(true) {}

  // Typically called on each region until it returns true.
  virtual bool do_heap_region_index(uint region_index) = 0;

  // True after iteration if the closure was applied to all heap regions
  // and returned "false" in all cases.
  bool is_complete() { return _is_complete; }
};

#endif // SHARE_GC_G1_G1HEAPREGION_HPP
