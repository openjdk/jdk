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

#include "precompiled.hpp"

#include "gc/shenandoah/shenandoahCollectorPolicy.hpp"
#include "gc/shenandoah/shenandoahGenerationalHeap.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahInitLogger.hpp"
#include "gc/shenandoah/shenandoahMemoryPool.hpp"
#include "gc/shenandoah/shenandoahOldGeneration.hpp"
#include "gc/shenandoah/shenandoahRegulatorThread.hpp"
#include "gc/shenandoah/shenandoahYoungGeneration.hpp"

#include "logging/log.hpp"

class ShenandoahGenerationalInitLogger : public ShenandoahInitLogger {
public:
  static void print() {
    ShenandoahGenerationalInitLogger logger;
    logger.print_all();
  }

  void print_heap() override {
    ShenandoahInitLogger::print_heap();

    ShenandoahGenerationalHeap* heap = ShenandoahGenerationalHeap::heap();

    ShenandoahYoungGeneration* young = heap->young_generation();
    log_info(gc, init)("Young Generation Soft Size: " EXACTFMT, EXACTFMTARGS(young->soft_max_capacity()));
    log_info(gc, init)("Young Generation Max: " EXACTFMT, EXACTFMTARGS(young->max_capacity()));

    ShenandoahOldGeneration* old = heap->old_generation();
    log_info(gc, init)("Old Generation Soft Size: " EXACTFMT, EXACTFMTARGS(old->soft_max_capacity()));
    log_info(gc, init)("Old Generation Max: " EXACTFMT, EXACTFMTARGS(old->max_capacity()));
  }

protected:
  void print_gc_specific() override {
    ShenandoahInitLogger::print_gc_specific();

    ShenandoahGenerationalHeap* heap = ShenandoahGenerationalHeap::heap();
    log_info(gc, init)("Young Heuristics: %s", heap->young_generation()->heuristics()->name());
    log_info(gc, init)("Old Heuristics: %s", heap->old_generation()->heuristics()->name());
  }
};

ShenandoahGenerationalHeap* ShenandoahGenerationalHeap::heap() {
  shenandoah_assert_generational();
  CollectedHeap* heap = Universe::heap();
  return checked_cast<ShenandoahGenerationalHeap*>(heap);
}

size_t ShenandoahGenerationalHeap::calculate_min_plab() const {
  return align_up(PLAB::min_size(), CardTable::card_size_in_words());
}

size_t ShenandoahGenerationalHeap::calculate_max_plab() const {
  size_t MaxTLABSizeWords = ShenandoahHeapRegion::max_tlab_size_words();
  return ((ShenandoahMaxEvacLABRatio > 0)?
          align_down(MIN2(MaxTLABSizeWords, PLAB::min_size() * ShenandoahMaxEvacLABRatio), CardTable::card_size_in_words()):
          align_down(MaxTLABSizeWords, CardTable::card_size_in_words()));
}

ShenandoahGenerationalHeap::ShenandoahGenerationalHeap(ShenandoahCollectorPolicy* policy) :
  ShenandoahHeap(policy),
  _min_plab_size(calculate_min_plab()),
  _max_plab_size(calculate_max_plab()),
  _regulator_thread(nullptr) {
  assert(is_aligned(_min_plab_size, CardTable::card_size_in_words()), "min_plab_size must be aligned");
  assert(is_aligned(_max_plab_size, CardTable::card_size_in_words()), "max_plab_size must be aligned");
}

void ShenandoahGenerationalHeap::print_init_logger() const {
  ShenandoahGenerationalInitLogger logger;
  logger.print_all();
}

void ShenandoahGenerationalHeap::initialize_serviceability() {
  assert(mode()->is_generational(), "Only for the generational mode");
  _young_gen_memory_pool = new ShenandoahYoungGenMemoryPool(this);
  _old_gen_memory_pool = new ShenandoahOldGenMemoryPool(this);
  cycle_memory_manager()->add_pool(_young_gen_memory_pool);
  cycle_memory_manager()->add_pool(_old_gen_memory_pool);
  stw_memory_manager()->add_pool(_young_gen_memory_pool);
  stw_memory_manager()->add_pool(_old_gen_memory_pool);
}

GrowableArray<MemoryPool*> ShenandoahGenerationalHeap::memory_pools() {
  assert(mode()->is_generational(), "Only for the generational mode");
  GrowableArray<MemoryPool*> memory_pools(2);
  memory_pools.append(_young_gen_memory_pool);
  memory_pools.append(_old_gen_memory_pool);
  return memory_pools;
}

void ShenandoahGenerationalHeap::initialize_controller() {
  auto control_thread = new ShenandoahGenerationalControlThread();
  _control_thread = control_thread;
  _regulator_thread = new ShenandoahRegulatorThread(control_thread);
}

void ShenandoahGenerationalHeap::gc_threads_do(ThreadClosure* tcl) const {
  if (!shenandoah_policy()->is_at_shutdown()) {
    ShenandoahHeap::gc_threads_do(tcl);
    tcl->do_thread(regulator_thread());
  }
}

void ShenandoahGenerationalHeap::stop() {
  regulator_thread()->stop();
  ShenandoahHeap::stop();
}

ShenandoahGenerationalHeap::TransferResult ShenandoahGenerationalHeap::balance_generations() {
  shenandoah_assert_heaplocked_or_safepoint();

  ShenandoahOldGeneration* old_gen = old_generation();
  const ssize_t old_region_balance = old_gen->get_region_balance();
  old_gen->set_region_balance(0);

  if (old_region_balance > 0) {
    const auto old_region_surplus = checked_cast<size_t>(old_region_balance);
    const bool success = generation_sizer()->transfer_to_young(old_region_surplus);
    return TransferResult {
      success, old_region_surplus, "young"
    };
  }

  if (old_region_balance < 0) {
    const auto old_region_deficit = checked_cast<size_t>(-old_region_balance);
    const bool success = generation_sizer()->transfer_to_old(old_region_deficit);
    if (!success) {
      old_gen->handle_failed_transfer();
    }
    return TransferResult {
      success, old_region_deficit, "old"
    };
  }

  return TransferResult {true, 0, "none"};
}

// Make sure old-generation is large enough, but no larger than is necessary, to hold mixed evacuations
// and promotions, if we anticipate either. Any deficit is provided by the young generation, subject to
// xfer_limit, and any surplus is transferred to the young generation.
// xfer_limit is the maximum we're able to transfer from young to old.
void ShenandoahGenerationalHeap::compute_old_generation_balance(size_t old_xfer_limit, size_t old_cset_regions) {

  // We can limit the old reserve to the size of anticipated promotions:
  // max_old_reserve is an upper bound on memory evacuated from old and promoted to old,
  // clamped by the old generation space available.
  //
  // Here's the algebra.
  // Let SOEP = ShenandoahOldEvacRatioPercent,
  //     OE = old evac,
  //     YE = young evac, and
  //     TE = total evac = OE + YE
  // By definition:
  //            SOEP/100 = OE/TE
  //                     = OE/(OE+YE)
  //  => SOEP/(100-SOEP) = OE/((OE+YE)-OE)      // componendo-dividendo: If a/b = c/d, then a/(b-a) = c/(d-c)
  //                     = OE/YE
  //  =>              OE = YE*SOEP/(100-SOEP)

  // We have to be careful in the event that SOEP is set to 100 by the user.
  assert(ShenandoahOldEvacRatioPercent <= 100, "Error");
  const size_t old_available = old_generation()->available();
  // The free set will reserve this amount of memory to hold young evacuations
  const size_t young_reserve = (young_generation()->max_capacity() * ShenandoahEvacReserve) / 100;

  // In the case that ShenandoahOldEvacRatioPercent equals 100, max_old_reserve is limited only by xfer_limit.

  const size_t bound_on_old_reserve = old_available + old_xfer_limit + young_reserve;
  const size_t max_old_reserve = (ShenandoahOldEvacRatioPercent == 100)?
                                 bound_on_old_reserve: MIN2((young_reserve * ShenandoahOldEvacRatioPercent) / (100 - ShenandoahOldEvacRatioPercent),
                                                            bound_on_old_reserve);

  const size_t region_size_bytes = ShenandoahHeapRegion::region_size_bytes();

  // Decide how much old space we should reserve for a mixed collection
  size_t reserve_for_mixed = 0;
  const size_t mixed_candidates = old_heuristics()->unprocessed_old_collection_candidates();
  const bool doing_mixed = (mixed_candidates > 0);
  if (doing_mixed) {
    // We want this much memory to be unfragmented in order to reliably evacuate old.  This is conservative because we
    // may not evacuate the entirety of unprocessed candidates in a single mixed evacuation.
    const size_t max_evac_need = (size_t)
            (old_heuristics()->unprocessed_old_collection_candidates_live_memory() * ShenandoahOldEvacWaste);
    assert(old_available >= old_generation()->free_unaffiliated_regions() * region_size_bytes,
           "Unaffiliated available must be less than total available");
    const size_t old_fragmented_available =
            old_available - old_generation()->free_unaffiliated_regions() * region_size_bytes;
    reserve_for_mixed = max_evac_need + old_fragmented_available;
    if (reserve_for_mixed > max_old_reserve) {
      reserve_for_mixed = max_old_reserve;
    }
  }

  // Decide how much space we should reserve for promotions from young
  size_t reserve_for_promo = 0;
  const size_t promo_load = old_generation()->get_promotion_potential();
  const bool doing_promotions = promo_load > 0;
  if (doing_promotions) {
    // We're promoting and have a bound on the maximum amount that can be promoted
    assert(max_old_reserve >= reserve_for_mixed, "Sanity");
    const size_t available_for_promotions = max_old_reserve - reserve_for_mixed;
    reserve_for_promo = MIN2((size_t)(promo_load * ShenandoahPromoEvacWaste), available_for_promotions);
  }

  // This is the total old we want to ideally reserve
  const size_t old_reserve = reserve_for_mixed + reserve_for_promo;
  assert(old_reserve <= max_old_reserve, "cannot reserve more than max for old evacuations");

  // We now check if the old generation is running a surplus or a deficit.
  const size_t max_old_available = old_generation()->available() + old_cset_regions * region_size_bytes;
  if (max_old_available >= old_reserve) {
    // We are running a surplus, so the old region surplus can go to young
    const size_t old_surplus = (max_old_available - old_reserve) / region_size_bytes;
    const size_t unaffiliated_old_regions = old_generation()->free_unaffiliated_regions() + old_cset_regions;
    const size_t old_region_surplus = MIN2(old_surplus, unaffiliated_old_regions);
    old_generation()->set_region_balance(checked_cast<ssize_t>(old_region_surplus));
  } else {
    // We are running a deficit which we'd like to fill from young.
    // Ignore that this will directly impact young_generation()->max_capacity(),
    // indirectly impacting young_reserve and old_reserve.  These computations are conservative.
    // Note that deficit is rounded up by one region.
    const size_t old_need = (old_reserve - max_old_available + region_size_bytes - 1) / region_size_bytes;
    const size_t max_old_region_xfer = old_xfer_limit / region_size_bytes;

    // Round down the regions we can transfer from young to old. If we're running short
    // on young-gen memory, we restrict the xfer. Old-gen collection activities will be
    // curtailed if the budget is restricted.
    const size_t old_region_deficit = MIN2(old_need, max_old_region_xfer);
    old_generation()->set_region_balance(0 - checked_cast<ssize_t>(old_region_deficit));
  }
}

void ShenandoahGenerationalHeap::reset_generation_reserves() {
  young_generation()->set_evacuation_reserve(0);
  old_generation()->set_evacuation_reserve(0);
  old_generation()->set_promoted_reserve(0);
}

void ShenandoahGenerationalHeap::TransferResult::print_on(const char* when, outputStream* ss) const {
  auto heap = ShenandoahGenerationalHeap::heap();
  ShenandoahYoungGeneration* const young_gen = heap->young_generation();
  ShenandoahOldGeneration* const old_gen = heap->old_generation();
  const size_t young_available = young_gen->available();
  const size_t old_available = old_gen->available();
  ss->print_cr("After %s, %s " SIZE_FORMAT " regions to %s to prepare for next gc, old available: "
                     PROPERFMT ", young_available: " PROPERFMT,
                     when,
                     success? "successfully transferred": "failed to transfer", region_count, region_destination,
                     PROPERFMTARGS(old_available), PROPERFMTARGS(young_available));
}
