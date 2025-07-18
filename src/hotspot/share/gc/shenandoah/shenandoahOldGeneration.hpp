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
  size_t _promoted_expended;

  // Represents the quantity of live bytes we expect to promote in place during the next
  // evacuation cycle. This value is used by the young heuristic to trigger mixed collections.
  // It is also used when computing the optimum size for the old generation.
  size_t _promotion_potential;

  // When a region is selected to be promoted in place, the remaining free memory is filled
  // in to prevent additional allocations (preventing premature promotion of newly allocated
  // objects. This field records the total amount of padding used for such regions.
  size_t _pad_for_promote_in_place;

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
  ShenandoahOldGeneration(uint max_queues, size_t max_capacity);

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
  void set_region_balance(ssize_t balance) { _region_balance = balance; }
  ssize_t get_region_balance() const { return _region_balance; }
  // See description in field declaration
  void set_promotion_potential(size_t val) { _promotion_potential = val; };
  size_t get_promotion_potential() const { return _promotion_potential; };

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

  // This logs that an evacuation to the old generation has failed
  void handle_failed_promotion(Thread* thread, size_t size);

  // A successful evacuation re-dirties the cards and registers the object with the remembered set
  void handle_evacuation(HeapWord* obj, size_t words, bool promotion);

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

  // We leave the SATB barrier on for the entirety of the old generation
  // marking phase. In some cases, this can cause a write to a perfectly
  // reachable oop to enqueue a pointer that later becomes garbage (because
  // it points at an object that is later chosen for the collection set). There are
  // also cases where the referent of a weak reference ends up in the SATB
  // and is later collected. In these cases the oop in the SATB buffer becomes
  // invalid and the _next_ cycle will crash during its marking phase. To
  // avoid this problem, we "purge" the SATB buffers during the final update
  // references phase if (and only if) an old generation mark is in progress.
  // At this stage we can safely determine if any of the oops in the SATB
  // buffer belong to trashed regions (before they are recycled). As it
  // happens, flushing a SATB queue also filters out oops which have already
  // been marked - which is the case for anything that is being evacuated
  // from the collection set.
  //
  // Alternatively, we could inspect the state of the heap and the age of the
  // object at the barrier, but we reject this approach because it is likely
  // the performance impact would be too severe.
  void transfer_pointers_from_satb() const;
  void concurrent_transfer_pointers_from_satb() const;

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

  static const size_t FRACTIONAL_DENOMINATOR = 65536;

  // During initialization of the JVM, we search for the correct old-gen size by initially performing old-gen
  // collection when old-gen usage is 50% more (INITIAL_GROWTH_BEFORE_COMPACTION) than the initial old-gen size
  // estimate (3.125% of heap).  The next old-gen trigger occurs when old-gen grows 25% larger than its live
  // memory at the end of the first old-gen collection.  Then we trigger again when old-gen grows 12.5%
  // more than its live memory at the end of the previous old-gen collection.  Thereafter, we trigger each time
  // old-gen grows more than 12.5% following the end of its previous old-gen collection.
  static const size_t INITIAL_GROWTH_BEFORE_COMPACTION = FRACTIONAL_DENOMINATOR / 2;        //  50.0%

  // INITIAL_LIVE_FRACTION represents the initial guess of how large old-gen should be.  We estimate that old-gen
  // needs to consume 6.25% of the total heap size.  And we "pretend" that we start out with this amount of live
  // old-gen memory.  The first old-collection trigger will occur when old-gen occupies 50% more than this initial
  // approximation of the old-gen memory requirement, in other words when old-gen usage is 150% of 6.25%, which
  // is 9.375% of the total heap size.
  static const uint16_t INITIAL_LIVE_FRACTION = FRACTIONAL_DENOMINATOR / 16;                //   6.25%

  size_t _live_bytes_after_last_mark;

  // How much growth in usage before we trigger old collection, per FRACTIONAL_DENOMINATOR (65_536)
  size_t _growth_before_compaction;
  const size_t _min_growth_before_compaction;                                               // Default is 12.5%

  void validate_transition(State new_state) NOT_DEBUG_RETURN;

public:
  State state() const {
    return _state;
  }

  const char* state_name() const {
    return state_name(_state);
  }

  void transition_to(State new_state);

  size_t get_live_bytes_after_last_mark() const;
  void set_live_bytes_after_last_mark(size_t new_live);

  size_t usage_trigger_threshold() const;

  bool can_start_gc() {
    return _state == WAITING_FOR_BOOTSTRAP;
  }

  static const char* state_name(State state);

};


#endif //SHARE_VM_GC_SHENANDOAH_SHENANDOAHOLDGENERATION_HPP
