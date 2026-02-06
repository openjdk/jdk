/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

#ifndef SHARE_VM_GC_SHENANDOAH_SHENANDOAHOLDGENERATION_HPP
#define SHARE_VM_GC_SHENANDOAH_SHENANDOAHOLDGENERATION_HPP

#include "gc/shenandoah/heuristics/shenandoahOldHeuristics.hpp"
#include "gc/shenandoah/shenandoahAllocRequest.hpp"
#include "gc/shenandoah/shenandoahGeneration.hpp"
#include "gc/shenandoah/shenandoahGenerationalHeap.hpp"
#include "gc/shenandoah/shenandoahScanRemembered.hpp"
#include "gc/shenandoah/shenandoahSharedVariables.hpp"

class LogStream;
class ShenandoahHeapRegion;
class ShenandoahHeapRegionClosure;
class ShenandoahOldHeuristics;

class ShenandoahOldGeneration : public ShenandoahGeneration {
private:
  ShenandoahHeapRegion** _coalesce_and_fill_region_array;
  ShenandoahOldHeuristics* _old_heuristics;

  // After determining the desired size of the old generation (see compute_old_generation_balance), this
  // quantity represents the number of regions above (surplus) or below (deficit) that size.
  // This value is computed prior to the actual exchange of any regions. A positive value represents
  // a surplus of old regions which will be transferred from old _to_ young. A negative value represents
  // a deficit of regions that will be replenished by a transfer _from_ young to old.
  ssize_t _region_balance;

  // Set when evacuation in the old generation fails. When this is set, the control thread will initiate a
  // full GC instead of a futile degenerated cycle.
  ShenandoahSharedFlag _failed_evacuation;

  // Bytes reserved within old-gen to hold the results of promotion. This is separate from
  // and in addition to the evacuation reserve for intra-generation evacuations (ShenandoahGeneration::_evacuation_reserve).
  // If there is more data ready to be promoted than can fit within this reserve, the promotion of some objects will be
  // deferred until a subsequent evacuation pass.
  size_t _promoted_reserve;

  // Bytes of old-gen memory expended on promotions. This may be modified concurrently
  // by mutators and gc workers when promotion LABs are retired during evacuation. It
  // is therefore always accessed through atomic operations. This is increased when a
  // PLAB is allocated for promotions. The value is decreased by the amount of memory
  // remaining in a PLAB when it is retired.
  Atomic<size_t> _promoted_expended;

  // Represents the quantity of live bytes we expect to promote during the next GC cycle, either by
  // evacuation or by promote-in-place.  This value is used by the young heuristic to trigger mixed collections.
  // It is also used when computing the optimum size for the old generation.
  size_t _promotion_potential;

  // When a region is selected to be promoted in place, the remaining free memory is filled
  // in to prevent additional allocations (preventing premature promotion of newly allocated
  // objects). This field records the total amount of padding used for such regions.
  size_t _pad_for_promote_in_place;

  // Keep track of the number and size of promotions that failed. Perhaps we should use this to increase
  // the size of the old generation for the next collection cycle.
  Atomic<size_t> _promotion_failure_count;
  Atomic<size_t> _promotion_failure_words;

  // During construction of the collection set, we keep track of regions that are eligible
  // for promotion in place. These fields track the count of those humongous and regular regions.
  // This data is used to force the evacuation phase even when the collection set is otherwise
  // empty.
  size_t _promotable_humongous_regions;
  size_t _promotable_regular_regions;

  // True if old regions may be safely traversed by the remembered set scan.
  bool _is_parsable;

  bool coalesce_and_fill();

public:
  ShenandoahOldGeneration(uint max_queues);

  ShenandoahHeuristics* initialize_heuristics(ShenandoahMode* gc_mode) override;

  const char* name() const override {
    return "Old";
  }

  ShenandoahOldHeuristics* heuristics() const override {
    return _old_heuristics;
  }

  // See description in field declaration
  void set_promoted_reserve(size_t new_val);
  size_t get_promoted_reserve() const;

  // The promotion reserve is increased when rebuilding the free set transfers a region to the old generation
  void augment_promoted_reserve(size_t increment);

  // This zeros out the expended promotion count after the promotion reserve is computed
  void reset_promoted_expended();

  // This is incremented when allocations are made to copy promotions into the old generation
  size_t expend_promoted(size_t increment);

  // This is used to return unused memory from a retired promotion LAB
  size_t unexpend_promoted(size_t decrement);

  // This is used on the allocation path to gate promotions that would exceed the reserve
  size_t get_promoted_expended() const;

  // Return the count and size (in words) of failed promotions since the last reset
  size_t get_promotion_failed_count() const { return _promotion_failure_count.load_relaxed(); }
  size_t get_promotion_failed_words() const { return _promotion_failure_words.load_relaxed(); }

  // Test if there is enough memory reserved for this promotion
  bool can_promote(size_t requested_bytes) const {
    size_t promotion_avail = get_promoted_reserve();
    size_t promotion_expended = get_promoted_expended();
    return promotion_expended + requested_bytes <= promotion_avail;
  }

  // Test if there is enough memory available in the old generation to accommodate this request.
  // The request will be subject to constraints on promotion and evacuation reserves.
  bool can_allocate(const ShenandoahAllocRequest& req) const;

  // Updates the promotion expenditure tracking and configures whether the plab may be used
  // for promotions and evacuations, or just evacuations.
  void configure_plab_for_current_thread(const ShenandoahAllocRequest &req);

  // See description in field declaration
  void set_region_balance(ssize_t balance) {
    _region_balance = balance;
  }
  ssize_t get_region_balance() const { return _region_balance; }

  // See description in field declaration
  void set_promotion_potential(size_t val) { _promotion_potential = val; }
  size_t get_promotion_potential() const { return _promotion_potential; }

  // See description in field declaration
  void set_pad_for_promote_in_place(size_t pad) { _pad_for_promote_in_place = pad; }
  size_t get_pad_for_promote_in_place() const { return _pad_for_promote_in_place; }

  // See description in field declaration
  void set_expected_humongous_region_promotions(size_t region_count) { _promotable_humongous_regions = region_count; }
  void set_expected_regular_region_promotions(size_t region_count) { _promotable_regular_regions = region_count; }
  size_t get_expected_in_place_promotions() const { return _promotable_humongous_regions + _promotable_regular_regions; }
  bool has_in_place_promotions() const { return get_expected_in_place_promotions() > 0; }

  // Class unloading may render the card table offsets unusable, if they refer to unmarked objects
  bool is_parsable() const   { return _is_parsable; }
  void set_parsable(bool parsable);

  // This will signal the heuristic to trigger an old generation collection
  void handle_failed_transfer();

  // This will signal the control thread to run a full GC instead of a futile degenerated gc
  void handle_failed_evacuation();

  // Increment promotion failure counters, optionally log a more detailed message
  void handle_failed_promotion(Thread* thread, size_t size);
  void log_failed_promotion(LogStream& ls, Thread* thread, size_t size) const;

  // A successful evacuation re-dirties the cards and registers the object with the remembered set
  void handle_evacuation(HeapWord* obj, size_t words) const;

  // Clear the flag after it is consumed by the control thread
  bool clear_failed_evacuation() {
    return _failed_evacuation.try_unset();
  }

  // Transition to the next state after mixed evacuations have completed
  void complete_mixed_evacuations();

  // Abandon any future mixed collections. This is invoked when all old regions eligible for
  // inclusion in a mixed evacuation are pinned. This should be rare.
  void abandon_mixed_evacuations();

private:
  ShenandoahScanRemembered* _card_scan;

public:
  ShenandoahScanRemembered* card_scan() { return _card_scan; }

  // Clear cards for given region
  void clear_cards_for(ShenandoahHeapRegion* region);

  // Mark card for this location as dirty
  void mark_card_as_dirty(void* location);

  void parallel_heap_region_iterate(ShenandoahHeapRegionClosure* cl) override;

  void parallel_heap_region_iterate_free(ShenandoahHeapRegionClosure* cl) override;

  void heap_region_iterate(ShenandoahHeapRegionClosure* cl) override;

  bool contains(ShenandoahAffiliation affiliation) const override;
  bool contains(ShenandoahHeapRegion* region) const override;
  bool contains(oop obj) const override;

  void set_concurrent_mark_in_progress(bool in_progress) override;
  bool is_concurrent_mark_in_progress() override;

  bool entry_coalesce_and_fill();
  void prepare_for_mixed_collections_after_global_gc();
  void prepare_gc() override;
  void prepare_regions_and_collection_set(bool concurrent) override;
  void record_success_concurrent(bool abbreviated) override;
  void cancel_marking() override;

  // Cancels old gc and transitions to the idle state
  void cancel_gc();

  // The SATB barrier will be "enabled" until old marking completes. This means it is
  // possible for an entire young collection cycle to execute while the SATB barrier is enabled.
  // Consider a situation like this, where we have a pointer 'B' at an object 'A' which is in
  // the young collection set:
  //
  //      +--Young, CSet------+     +--Young, Regular----+
  //      |                   |     |                    |
  //      |                   |     |                    |
  //      |       A <--------------------+ B             |
  //      |                   |     |                    |
  //      |                   |     |                    |
  //      +-------------------+     +--------------------+
  //
  // If a mutator thread overwrites pointer B, the SATB barrier will dutifully enqueue
  // object A. However, this object will be trashed when the young cycle completes. We must,
  // therefore, filter this object from the SATB buffer before any old mark threads see it.
  // We do this with a handshake before final-update-refs (see shenandoahConcurrentGC.cpp).
  //
  // This method is here only for degenerated cycles. A concurrent cycle may be cancelled before
  // we have a chance to execute the handshake to flush the SATB in final-update-refs.
  void transfer_pointers_from_satb() const;

  // True if there are old regions waiting to be selected for a mixed collection
  bool has_unprocessed_collection_candidates();

  bool is_doing_mixed_evacuations() const {
    return state() == EVACUATING || state() == EVACUATING_AFTER_GLOBAL;
  }

  bool is_preparing_for_mark() const {
    return state() == FILLING;
  }

  bool is_idle() const {
    return state() == WAITING_FOR_BOOTSTRAP;
  }

  bool is_bootstrapping() const {
    return state() == BOOTSTRAPPING;
  }

  // Amount of live memory (bytes) in regions waiting for mixed collections
  size_t unprocessed_collection_candidates_live_memory();

  // Abandon any regions waiting for mixed collections
  void abandon_collection_candidates();

public:
  enum State {
    FILLING, WAITING_FOR_BOOTSTRAP, BOOTSTRAPPING, MARKING, EVACUATING, EVACUATING_AFTER_GLOBAL
  };

#ifdef ASSERT
  bool validate_waiting_for_bootstrap();
#endif

private:
  State _state;

  // During initialization of the JVM, we search for the correct old-gen size by initially performing old-gen
  // collection when old-gen usage is 50% more (INITIAL_GROWTH_PERCENT_BEFORE_COLLECTION) than the initial old-gen size
  // estimate (16% of heap).  With each successive old-gen collection, we divide the growth trigger by two, but
  // never use a growth trigger smaller than ShenandoahMinOldGenGrowthPercent.
  static const size_t INITIAL_GROWTH_PERCENT_BEFORE_COLLECTION = 50;

  // INITIAL_LIVE_PERCENT represents the initial guess of how large old-gen should be.  We estimate that old gen
  // needs to consume 16% of the total heap size.  And we "pretend" that we start out with this amount of live
  // old-gen memory.  The first old-collection trigger will occur when old-gen occupies 50% more than this initial
  // approximation of the old-gen memory requirement, in other words when old-gen usage is 150% of 16%, which
  // is 24% of the heap size.
  static const size_t INITIAL_LIVE_PERCENT = 16;

  size_t _live_bytes_at_last_mark;

  // How much growth in usage before we trigger old collection as a percent of soft_max_capacity
  size_t _growth_percent_before_collection;

  void validate_transition(State new_state) NOT_DEBUG_RETURN;

public:
  State state() const {
    return _state;
  }

  const char* state_name() const {
    return state_name(_state);
  }

  void transition_to(State new_state);

  size_t get_live_bytes_at_last_mark() const;
  void set_live_bytes_at_last_mark(size_t new_live);

  size_t usage_trigger_threshold() const;

  bool can_start_gc() {
    return _state == WAITING_FOR_BOOTSTRAP;
  }

  static const char* state_name(State state);

  size_t bytes_allocated_since_gc_start() const override;
  size_t used() const override;
  size_t used_regions() const override;
  size_t used_regions_size() const override;
  size_t get_humongous_waste() const override;
  size_t free_unaffiliated_regions() const override;
  size_t get_affiliated_region_count() const override;
  size_t max_capacity() const override;
};


#endif //SHARE_VM_GC_SHENANDOAH_SHENANDOAHOLDGENERATION_HPP
