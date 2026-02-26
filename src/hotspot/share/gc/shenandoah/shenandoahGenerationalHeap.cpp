/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/shenandoah/shenandoahAgeCensus.hpp"
#include "gc/shenandoah/shenandoahClosures.inline.hpp"
#include "gc/shenandoah/shenandoahCollectorPolicy.hpp"
#include "gc/shenandoah/shenandoahFreeSet.hpp"
#include "gc/shenandoah/shenandoahGeneration.hpp"
#include "gc/shenandoah/shenandoahGenerationalControlThread.hpp"
#include "gc/shenandoah/shenandoahGenerationalEvacuationTask.hpp"
#include "gc/shenandoah/shenandoahGenerationalHeap.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahHeapRegion.hpp"
#include "gc/shenandoah/shenandoahHeapRegionClosures.hpp"
#include "gc/shenandoah/shenandoahInitLogger.hpp"
#include "gc/shenandoah/shenandoahMemoryPool.hpp"
#include "gc/shenandoah/shenandoahMonitoringSupport.hpp"
#include "gc/shenandoah/shenandoahOldGeneration.hpp"
#include "gc/shenandoah/shenandoahPhaseTimings.hpp"
#include "gc/shenandoah/shenandoahRegulatorThread.hpp"
#include "gc/shenandoah/shenandoahScanRemembered.inline.hpp"
#include "gc/shenandoah/shenandoahUtils.hpp"
#include "gc/shenandoah/shenandoahWorkerPolicy.hpp"
#include "gc/shenandoah/shenandoahYoungGeneration.hpp"
#include "logging/log.hpp"
#include "utilities/events.hpp"


class ShenandoahGenerationalInitLogger : public ShenandoahInitLogger {
public:
  static void print() {
    ShenandoahGenerationalInitLogger logger;
    logger.print_all();
  }
protected:
  void print_gc_specific() override {
    ShenandoahInitLogger::print_gc_specific();

    ShenandoahGenerationalHeap* heap = ShenandoahGenerationalHeap::heap();
    log_info(gc, init)("Young Heuristics: %s", heap->young_generation()->heuristics()->name());
    log_info(gc, init)("Old Heuristics: %s", heap->old_generation()->heuristics()->name());
  }
};

size_t ShenandoahGenerationalHeap::calculate_min_plab() {
  return align_up(PLAB::min_size(), CardTable::card_size_in_words());
}

size_t ShenandoahGenerationalHeap::calculate_max_plab() {
  size_t MaxTLABSizeWords = ShenandoahHeapRegion::max_tlab_size_words();
  return align_down(MaxTLABSizeWords, CardTable::card_size_in_words());
}

// Returns size in bytes
size_t ShenandoahGenerationalHeap::unsafe_max_tlab_alloc() const {
  return MIN2(ShenandoahHeapRegion::max_tlab_size_bytes(), young_generation()->available());
}

ShenandoahGenerationalHeap::ShenandoahGenerationalHeap(ShenandoahCollectorPolicy* policy) :
  ShenandoahHeap(policy),
  _age_census(nullptr),
  _min_plab_size(calculate_min_plab()),
  _max_plab_size(calculate_max_plab()),
  _regulator_thread(nullptr),
  _young_gen_memory_pool(nullptr),
  _old_gen_memory_pool(nullptr) {
  assert(is_aligned(_min_plab_size, CardTable::card_size_in_words()), "min_plab_size must be aligned");
  assert(is_aligned(_max_plab_size, CardTable::card_size_in_words()), "max_plab_size must be aligned");
}

void ShenandoahGenerationalHeap::post_initialize() {
  ShenandoahHeap::post_initialize();
  _age_census = new ShenandoahAgeCensus();
}

void ShenandoahGenerationalHeap::print_init_logger() const {
  ShenandoahGenerationalInitLogger logger;
  logger.print_all();
}

void ShenandoahGenerationalHeap::initialize_heuristics() {
  // Initialize global generation and heuristics even in generational mode.
  ShenandoahHeap::initialize_heuristics();

  _young_generation = new ShenandoahYoungGeneration(max_workers());
  _old_generation = new ShenandoahOldGeneration(max_workers());
  _young_generation->initialize_heuristics(mode());
  _old_generation->initialize_heuristics(mode());
}

void ShenandoahGenerationalHeap::post_initialize_heuristics() {
  ShenandoahHeap::post_initialize_heuristics();
  _young_generation->post_initialize(this);
  _old_generation->post_initialize(this);
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
  ShenandoahHeap::stop();
  regulator_thread()->stop();
}

bool ShenandoahGenerationalHeap::requires_barriers(stackChunkOop obj) const {
  if (is_idle()) {
    return false;
  }

  if (is_concurrent_young_mark_in_progress() && is_in_young(obj) && !marking_context()->allocated_after_mark_start(obj)) {
    // We are marking young, this object is in young, and it is below the TAMS
    return true;
  }

  if (is_in_old(obj)) {
    // Card marking barriers are required for objects in the old generation
    return true;
  }

  if (has_forwarded_objects()) {
    // Object may have pointers that need to be updated
    return true;
  }

  return false;
}

void ShenandoahGenerationalHeap::evacuate_collection_set(ShenandoahGeneration* generation, bool concurrent) {
  ShenandoahRegionIterator regions;
  ShenandoahGenerationalEvacuationTask task(this, generation, &regions, concurrent, false /* only promote regions */);
  workers()->run_task(&task);
}

void ShenandoahGenerationalHeap::promote_regions_in_place(ShenandoahGeneration* generation, bool concurrent) {
  ShenandoahRegionIterator regions;
  ShenandoahGenerationalEvacuationTask task(this, generation, &regions, concurrent, true /* only promote regions */);
  workers()->run_task(&task);
}

oop ShenandoahGenerationalHeap::evacuate_object(oop p, Thread* thread) {
  assert(thread == Thread::current(), "Expected thread parameter to be current thread.");
  if (ShenandoahThreadLocalData::is_oom_during_evac(thread)) {
    // This thread went through the OOM during evac protocol and it is safe to return
    // the forward pointer. It must not attempt to evacuate anymore.
    return ShenandoahBarrierSet::resolve_forwarded(p);
  }

  assert(ShenandoahThreadLocalData::is_evac_allowed(thread), "must be enclosed in oom-evac scope");

  ShenandoahHeapRegion* from_region = heap_region_containing(p);
  assert(!from_region->is_humongous(), "never evacuate humongous objects");

  // Try to keep the object in the same generation
  const ShenandoahAffiliation target_gen = from_region->affiliation();

  if (target_gen == YOUNG_GENERATION) {
    markWord mark = p->mark();
    if (mark.is_marked()) {
      // Already forwarded.
      return ShenandoahBarrierSet::resolve_forwarded(p);
    }

    if (mark.has_displaced_mark_helper()) {
      // We don't want to deal with MT here just to ensure we read the right mark word.
      // Skip the potential promotion attempt for this one.
    } else if (age_census()->is_tenurable(from_region->age() + mark.age())) {
      // If the object is tenurable, try to promote it
      oop result = try_evacuate_object<YOUNG_GENERATION, OLD_GENERATION>(p, thread, from_region->age());

      // If we failed to promote this aged object, we'll fall through to code below and evacuate to young-gen.
      if (result != nullptr) {
        return result;
      }
    }
    return try_evacuate_object<YOUNG_GENERATION, YOUNG_GENERATION>(p, thread, from_region->age());
  }

  assert(target_gen == OLD_GENERATION, "Expected evacuation to old");
  return try_evacuate_object<OLD_GENERATION, OLD_GENERATION>(p, thread, from_region->age());
}

// try_evacuate_object registers the object and dirties the associated remembered set information when evacuating
// to OLD_GENERATION.
template<ShenandoahAffiliation FROM_GENERATION, ShenandoahAffiliation TO_GENERATION>
oop ShenandoahGenerationalHeap::try_evacuate_object(oop p, Thread* thread, uint from_region_age) {
  bool alloc_from_lab = true;
  bool has_plab = false;
  HeapWord* copy = nullptr;
  size_t size = ShenandoahForwarding::size(p);
  constexpr bool is_promotion = (TO_GENERATION == OLD_GENERATION) && (FROM_GENERATION == YOUNG_GENERATION);

#ifdef ASSERT
  if (ShenandoahOOMDuringEvacALot &&
      (os::random() & 1) == 0) { // Simulate OOM every ~2nd slow-path call
    copy = nullptr;
  } else {
#endif
    if (UseTLAB) {
      switch (TO_GENERATION) {
        case YOUNG_GENERATION: {
          copy = allocate_from_gclab(thread, size);
          if ((copy == nullptr) && (size < ShenandoahThreadLocalData::gclab_size(thread))) {
            // GCLAB allocation failed because we are bumping up against the limit on young evacuation reserve.  Try resetting
            // the desired GCLAB size and retry GCLAB allocation to avoid cascading of shared memory allocations.
            ShenandoahThreadLocalData::set_gclab_size(thread, PLAB::min_size());
            copy = allocate_from_gclab(thread, size);
            // If we still get nullptr, we'll try a shared allocation below.
          }
          break;
        }
        case OLD_GENERATION: {
          PLAB* plab = ShenandoahThreadLocalData::plab(thread);
          if (plab != nullptr) {
            has_plab = true;
            copy = allocate_from_plab(thread, size, is_promotion);
            if ((copy == nullptr) && (size < ShenandoahThreadLocalData::plab_size(thread)) &&
                ShenandoahThreadLocalData::plab_retries_enabled(thread)) {
              // PLAB allocation failed because we are bumping up against the limit on old evacuation reserve or because
              // the requested object does not fit within the current plab but the plab still has an "abundance" of memory,
              // where abundance is defined as >= ShenGenHeap::plab_min_size().  In the former case, we try shrinking the
              // desired PLAB size to the minimum and retry PLAB allocation to avoid cascading of shared memory allocations.
              // Shrinking the desired PLAB size may allow us to eke out a small PLAB while staying beneath evacuation reserve.
              if (plab->words_remaining() < plab_min_size()) {
                ShenandoahThreadLocalData::set_plab_size(thread, plab_min_size());
                copy = allocate_from_plab(thread, size, is_promotion);
                // If we still get nullptr, we'll try a shared allocation below.
                if (copy == nullptr) {
                  // If retry fails, don't continue to retry until we have success (probably in next GC pass)
                  ShenandoahThreadLocalData::disable_plab_retries(thread);
                }
              }
              // else, copy still equals nullptr.  this causes shared allocation below, preserving this plab for future needs.
            }
          }
          break;
        }
        default: {
          ShouldNotReachHere();
          break;
        }
      }
    }

    if (copy == nullptr) {
      // If we failed to allocate in LAB, we'll try a shared allocation.
      if (!is_promotion || !has_plab || (size > PLAB::min_size())) {
        ShenandoahAllocRequest req = ShenandoahAllocRequest::for_shared_gc(size, TO_GENERATION, is_promotion);
        copy = allocate_memory(req);
        alloc_from_lab = false;
      }
      // else, we leave copy equal to nullptr, signaling a promotion failure below if appropriate.
      // We choose not to promote objects smaller than size_threshold by way of shared allocations as this is too
      // costly.  Instead, we'll simply "evacuate" to young-gen memory (using a GCLAB) and will promote in a future
      // evacuation pass.  This condition is denoted by: is_promotion && has_plab && (size <= size_threshhold).
    }
#ifdef ASSERT
  }
#endif

  if (copy == nullptr) {
    if (TO_GENERATION == OLD_GENERATION) {
      if (FROM_GENERATION == YOUNG_GENERATION) {
        // Signal that promotion failed. Will evacuate this old object somewhere in young gen.
        old_generation()->handle_failed_promotion(thread, size);
        return nullptr;
      } else {
        // Remember that evacuation to old gen failed. We'll want to trigger a full gc to recover from this
        // after the evacuation threads have finished.
        old_generation()->handle_failed_evacuation();
      }
    }

    control_thread()->handle_alloc_failure_evac(size);
    oom_evac_handler()->handle_out_of_memory_during_evacuation();
    return ShenandoahBarrierSet::resolve_forwarded(p);
  }

  if (ShenandoahEvacTracking) {
    evac_tracker()->begin_evacuation(thread, size * HeapWordSize, FROM_GENERATION, TO_GENERATION);
  }

  // Copy the object:
  Copy::aligned_disjoint_words(cast_from_oop<HeapWord*>(p), copy, size);
  oop copy_val = cast_to_oop(copy);

  // Update the age of the evacuated object
  if (TO_GENERATION == YOUNG_GENERATION && is_aging_cycle()) {
    increase_object_age(copy_val, from_region_age + 1);
  }

  // Try to install the new forwarding pointer.
  oop result = ShenandoahForwarding::try_update_forwardee(p, copy_val);
  if (result == copy_val) {
    // Successfully evacuated. Our copy is now the public one!

    // This is necessary for virtual thread support. This uses the mark word without
    // considering that it may now be a forwarding pointer (and could therefore crash).
    // Secondarily, we do not want to spend cycles relativizing stack chunks for oops
    // that lost the evacuation race (and will therefore not become visible). It is
    // safe to do this on the public copy (this is also done during concurrent mark).
    ContinuationGCSupport::relativize_stack_chunk(copy_val);

    if (ShenandoahEvacTracking) {
      // Record that the evacuation succeeded
      evac_tracker()->end_evacuation(thread, size * HeapWordSize, FROM_GENERATION, TO_GENERATION);
    }

    if (TO_GENERATION == OLD_GENERATION) {
      old_generation()->handle_evacuation(copy, size);
    }
  }  else {
    // Failed to evacuate. We need to deal with the object that is left behind. Since this
    // new allocation is certainly after TAMS, it will be considered live in the next cycle.
    // But if it happens to contain references to evacuated regions, those references would
    // not get updated for this stale copy during this cycle, and we will crash while scanning
    // it the next cycle.
    if (alloc_from_lab) {
      // For LAB allocations, it is enough to rollback the allocation ptr. Either the next
      // object will overwrite this stale copy, or the filler object on LAB retirement will
      // do this.
      switch (TO_GENERATION) {
        case YOUNG_GENERATION: {
          ShenandoahThreadLocalData::gclab(thread)->undo_allocation(copy, size);
          break;
        }
        case OLD_GENERATION: {
          ShenandoahThreadLocalData::plab(thread)->undo_allocation(copy, size);
          if (is_promotion) {
            ShenandoahThreadLocalData::subtract_from_plab_promoted(thread, size * HeapWordSize);
          }
          break;
        }
        default: {
          ShouldNotReachHere();
          break;
        }
      }
    } else {
      // For non-LAB allocations, we have no way to retract the allocation, and
      // have to explicitly overwrite the copy with the filler object. With that overwrite,
      // we have to keep the fwdptr initialized and pointing to our (stale) copy.
      assert(size >= ShenandoahHeap::min_fill_size(), "previously allocated object known to be larger than min_size");
      fill_with_object(copy, size);
    }
  }
  shenandoah_assert_correct(nullptr, result);
  return result;
}

template oop ShenandoahGenerationalHeap::try_evacuate_object<YOUNG_GENERATION, YOUNG_GENERATION>(oop p, Thread* thread, uint from_region_age);
template oop ShenandoahGenerationalHeap::try_evacuate_object<YOUNG_GENERATION, OLD_GENERATION>(oop p, Thread* thread, uint from_region_age);
template oop ShenandoahGenerationalHeap::try_evacuate_object<OLD_GENERATION, OLD_GENERATION>(oop p, Thread* thread, uint from_region_age);

inline HeapWord* ShenandoahGenerationalHeap::allocate_from_plab(Thread* thread, size_t size, bool is_promotion) {
  assert(UseTLAB, "TLABs should be enabled");

  PLAB* plab = ShenandoahThreadLocalData::plab(thread);
  HeapWord* obj;

  if (plab == nullptr) {
    assert(!thread->is_Java_thread() && !thread->is_Worker_thread(), "Performance: thread should have PLAB: %s", thread->name());
    // No PLABs in this thread, fallback to shared allocation
    return nullptr;
  } else if (is_promotion && !ShenandoahThreadLocalData::allow_plab_promotions(thread)) {
    return nullptr;
  }
  // if plab->word_size() <= 0, thread's plab not yet initialized for this pass, so allow_plab_promotions() is not trustworthy
  obj = plab->allocate(size);
  if ((obj == nullptr) && (plab->words_remaining() < plab_min_size())) {
    // allocate_from_plab_slow will establish allow_plab_promotions(thread) for future invocations
    obj = allocate_from_plab_slow(thread, size, is_promotion);
  }
  // if plab->words_remaining() >= ShenGenHeap::heap()->plab_min_size(), just return nullptr so we can use a shared allocation
  if (obj == nullptr) {
    return nullptr;
  }

  if (is_promotion) {
    ShenandoahThreadLocalData::add_to_plab_promoted(thread, size * HeapWordSize);
  }
  return obj;
}

// Establish a new PLAB and allocate size HeapWords within it.
HeapWord* ShenandoahGenerationalHeap::allocate_from_plab_slow(Thread* thread, size_t size, bool is_promotion) {
  assert(mode()->is_generational(), "PLABs only relevant to generational GC");

  const size_t plab_min_size = this->plab_min_size();
  // PLABs are aligned to card boundaries to avoid synchronization with concurrent
  // allocations in other PLABs.
  const size_t min_size = (size > plab_min_size)? align_up(size, CardTable::card_size_in_words()): plab_min_size;

  // Figure out size of new PLAB, using value determined at last refill.
  size_t cur_size = ShenandoahThreadLocalData::plab_size(thread);
  if (cur_size == 0) {
    cur_size = plab_min_size;
  }

  // Expand aggressively, doubling at each refill in this epoch, ceiling at plab_max_size()
  const size_t future_size = MIN2(cur_size * 2, plab_max_size());
  // Doubling, starting at a card-multiple, should give us a card-multiple. (Ceiling and floor
  // are card multiples.)
  assert(is_aligned(future_size, CardTable::card_size_in_words()), "Card multiple by construction, future_size: %zu"
          ", card_size: %u, cur_size: %zu, max: %zu",
         future_size, CardTable::card_size_in_words(), cur_size, plab_max_size());

  // Record new heuristic value even if we take any shortcut. This captures
  // the case when moderately-sized objects always take a shortcut. At some point,
  // heuristics should catch up with them.  Note that the requested cur_size may
  // not be honored, but we remember that this is the preferred size.
  log_debug(gc, plab)("Set next PLAB refill size: %zu bytes", future_size * HeapWordSize);
  ShenandoahThreadLocalData::set_plab_size(thread, future_size);

  if (cur_size < size) {
    // The PLAB to be allocated is still not large enough to hold the object. Fall back to shared allocation.
    // This avoids retiring perfectly good PLABs in order to represent a single large object allocation.
    log_debug(gc, plab)("Current PLAB size (%zu) is too small for %zu", cur_size * HeapWordSize, size * HeapWordSize);
    return nullptr;
  }

  // Retire current PLAB, and allocate a new one.
  PLAB* plab = ShenandoahThreadLocalData::plab(thread);
  if (plab->words_remaining() < plab_min_size) {
    // Retire current PLAB. This takes care of any PLAB book-keeping.
    // retire_plab() registers the remnant filler object with the remembered set scanner without a lock.
    // Since PLABs are card-aligned, concurrent registrations in other PLABs don't interfere.
    retire_plab(plab, thread);

    size_t actual_size = 0;
    HeapWord* plab_buf = allocate_new_plab(min_size, cur_size, &actual_size);
    if (plab_buf == nullptr) {
      if (min_size == plab_min_size) {
        // Disable PLAB promotions for this thread because we cannot even allocate a minimal PLAB. This allows us
        // to fail faster on subsequent promotion attempts.
        ShenandoahThreadLocalData::disable_plab_promotions(thread);
      }
      return nullptr;
    } else {
      ShenandoahThreadLocalData::enable_plab_retries(thread);
    }
    // Since the allocated PLAB may have been down-sized for alignment, plab->allocate(size) below may still fail.
    if (ZeroTLAB) {
      // ... and clear it.
      Copy::zero_to_words(plab_buf, actual_size);
    } else {
      // ...and zap just allocated object.
#ifdef ASSERT
      // Skip mangling the space corresponding to the object header to
      // ensure that the returned space is not considered parsable by
      // any concurrent GC thread.
      size_t hdr_size = oopDesc::header_size();
      Copy::fill_to_words(plab_buf + hdr_size, actual_size - hdr_size, badHeapWordVal);
#endif // ASSERT
    }
    assert(is_aligned(actual_size, CardTable::card_size_in_words()), "Align by design");
    plab->set_buf(plab_buf, actual_size);
    if (is_promotion && !ShenandoahThreadLocalData::allow_plab_promotions(thread)) {
      return nullptr;
    }
    return plab->allocate(size);
  } else {
    // If there's still at least min_size() words available within the current plab, don't retire it.  Let's nibble
    // away on this plab as long as we can.  Meanwhile, return nullptr to force this particular allocation request
    // to be satisfied with a shared allocation.  By packing more promotions into the previously allocated PLAB, we
    // reduce the likelihood of evacuation failures, and we reduce the need for downsizing our PLABs.
    return nullptr;
  }
}

HeapWord* ShenandoahGenerationalHeap::allocate_new_plab(size_t min_size, size_t word_size, size_t* actual_size) {
  // Align requested sizes to card-sized multiples.  Align down so that we don't violate max size of TLAB.
  assert(is_aligned(min_size, CardTable::card_size_in_words()), "Align by design");
  assert(word_size >= min_size, "Requested PLAB is too small");

  ShenandoahAllocRequest req = ShenandoahAllocRequest::for_plab(min_size, word_size);
  // Note that allocate_memory() sets a thread-local flag to prohibit further promotions by this thread
  // if we are at risk of infringing on the old-gen evacuation budget.
  HeapWord* res = allocate_memory(req);
  if (res != nullptr) {
    *actual_size = req.actual_size();
  } else {
    *actual_size = 0;
  }
  assert(is_aligned(res, CardTable::card_size_in_words()), "Align by design");
  return res;
}

void ShenandoahGenerationalHeap::retire_plab(PLAB* plab, Thread* thread) {
  // We don't enforce limits on plab evacuations.  We let it consume all available old-gen memory in order to reduce
  // probability of an evacuation failure.  We do enforce limits on promotion, to make sure that excessive promotion
  // does not result in an old-gen evacuation failure.  Note that a failed promotion is relatively harmless.  Any
  // object that fails to promote in the current cycle will be eligible for promotion in a subsequent cycle.

  // When the plab was instantiated, its entirety was treated as if the entire buffer was going to be dedicated to
  // promotions.  Now that we are retiring the buffer, we adjust for the reality that the plab is not entirely promotions.
  //  1. Some of the plab may have been dedicated to evacuations.
  //  2. Some of the plab may have been abandoned due to waste (at the end of the plab).
  size_t not_promoted =
          ShenandoahThreadLocalData::get_plab_actual_size(thread) - ShenandoahThreadLocalData::get_plab_promoted(thread);
  ShenandoahThreadLocalData::reset_plab_promoted(thread);
  ShenandoahThreadLocalData::set_plab_actual_size(thread, 0);
  if (not_promoted > 0) {
    log_debug(gc, plab)("Retire PLAB, unexpend unpromoted: %zu", not_promoted * HeapWordSize);
    old_generation()->unexpend_promoted(not_promoted);
  }
  const size_t original_waste = plab->waste();
  HeapWord* const top = plab->top();

  // plab->retire() overwrites unused memory between plab->top() and plab->hard_end() with a dummy object to make memory parsable.
  // It adds the size of this unused memory, in words, to plab->waste().
  plab->retire();
  if (top != nullptr && plab->waste() > original_waste && is_in_old(top)) {
    // If retiring the plab created a filler object, then we need to register it with our card scanner so it can
    // safely walk the region backing the plab.
    log_debug(gc, plab)("retire_plab() is registering remnant of size %zu at " PTR_FORMAT,
                        (plab->waste() - original_waste) * HeapWordSize, p2i(top));
    // No lock is necessary because the PLAB memory is aligned on card boundaries.
    old_generation()->card_scan()->register_object_without_lock(top);
  }
}

void ShenandoahGenerationalHeap::retire_plab(PLAB* plab) {
  Thread* thread = Thread::current();
  retire_plab(plab, thread);
}

// Make sure old-generation is large enough, but no larger than is necessary, to hold mixed evacuations
// and promotions, if we anticipate either. Any deficit is provided by the young generation, subject to
// mutator_xfer_limit, and any surplus is transferred to the young generation.  mutator_xfer_limit is
// the maximum we're able to transfer from young to old.  This is called at the end of GC, as we prepare
// for the idle span that precedes the next GC.
void ShenandoahGenerationalHeap::compute_old_generation_balance(size_t mutator_xfer_limit,
                                                                size_t old_trashed_regions, size_t young_trashed_regions) {
  shenandoah_assert_heaplocked();
  // We can limit the old reserve to the size of anticipated promotions:
  // max_old_reserve is an upper bound on memory evacuated from old and promoted to old,
  // clamped by the old generation space available.
  //
  // Here's the algebra.
  // Let SOEP = ShenandoahOldEvacPercent,
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
  assert(ShenandoahOldEvacPercent <= 100, "Error");
  const size_t region_size_bytes = ShenandoahHeapRegion::region_size_bytes();

  ShenandoahOldGeneration* old_gen = old_generation();
  size_t old_capacity = old_gen->max_capacity();
  size_t old_usage = old_gen->used(); // includes humongous waste
  size_t old_available = ((old_capacity >= old_usage)? old_capacity - old_usage: 0) + old_trashed_regions * region_size_bytes;

  ShenandoahYoungGeneration* young_gen = young_generation();
  size_t young_capacity = young_gen->max_capacity();
  size_t young_usage = young_gen->used(); // includes humongous waste
  size_t young_available = ((young_capacity >= young_usage)? young_capacity - young_usage: 0);
  size_t freeset_available = free_set()->available_locked();
  if (young_available > freeset_available) {
    young_available = freeset_available;
  }
  young_available += young_trashed_regions * region_size_bytes;

  // The free set will reserve this amount of memory to hold young evacuations (initialized to the ideal reserve)
  size_t young_reserve = (young_generation()->max_capacity() * ShenandoahEvacReserve) / 100;

  // If ShenandoahOldEvacPercent equals 100, max_old_reserve is limited only by mutator_xfer_limit and young_reserve
  const size_t bound_on_old_reserve = ((old_available + mutator_xfer_limit + young_reserve) * ShenandoahOldEvacPercent) / 100;
  size_t proposed_max_old = ((ShenandoahOldEvacPercent == 100)?
                             bound_on_old_reserve:
                             MIN2((young_reserve * ShenandoahOldEvacPercent) / (100 - ShenandoahOldEvacPercent),
                                  bound_on_old_reserve));
  if (young_reserve > young_available) {
    young_reserve = young_available;
  }

  // Decide how much old space we should reserve for a mixed collection
  size_t reserve_for_mixed = 0;
  const size_t old_fragmented_available =
    old_available - (old_generation()->free_unaffiliated_regions() + old_trashed_regions) * region_size_bytes;

  if (old_fragmented_available > proposed_max_old) {
    // After we've promoted regions in place, there may be an abundance of old-fragmented available memory,
    // even more than the desired percentage for old reserve.  We cannot transfer these fragmented regions back
    // to young.  Instead we make the best of the situation by using this fragmented memory for both promotions
    // and evacuations.
    proposed_max_old = old_fragmented_available;
  }
  size_t reserve_for_promo = old_fragmented_available;
  const size_t max_old_reserve = proposed_max_old;
  const size_t mixed_candidate_live_memory = old_generation()->unprocessed_collection_candidates_live_memory();
  const bool doing_mixed = (mixed_candidate_live_memory > 0);
  if (doing_mixed) {
    // We want this much memory to be unfragmented in order to reliably evacuate old.  This is conservative because we
    // may not evacuate the entirety of unprocessed candidates in a single mixed evacuation.
    const size_t max_evac_need = (size_t) (mixed_candidate_live_memory * ShenandoahOldEvacWaste);
    assert(old_available >= old_generation()->free_unaffiliated_regions() * region_size_bytes,
           "Unaffiliated available must be less than total available");

    // We prefer to evacuate all of mixed into unfragmented memory, and will expand old in order to do so, unless
    // we already have too much fragmented available memory in old.
    reserve_for_mixed = max_evac_need;
    if (reserve_for_mixed + reserve_for_promo > max_old_reserve) {
      // In this case, we'll allow old-evac to target some of the fragmented old memory.
      size_t excess_reserves = (reserve_for_mixed + reserve_for_promo) - max_old_reserve;
      if (reserve_for_promo > excess_reserves) {
        reserve_for_promo -= excess_reserves;
      } else {
        excess_reserves -= reserve_for_promo;
        reserve_for_promo = 0;
        reserve_for_mixed -= excess_reserves;
      }
    }
  }

  // Decide how much additional space we should reserve for promotions from young.  We give priority to mixed evacations
  // over promotions.
  const size_t promo_load = old_generation()->get_promotion_potential();
  const bool doing_promotions = promo_load > 0;
  if (doing_promotions) {
    // We've already set aside all of the fragmented available memory within old-gen to represent old objects
    // to be promoted from young generation.  promo_load represents the memory that we anticipate to be promoted
    // from regions that have reached tenure age.  In the ideal, we will always use fragmented old-gen memory
    // to hold individually promoted objects and will use unfragmented old-gen memory to represent the old-gen
    // evacuation workloa.

    // We're promoting and have an estimate of memory to be promoted from aged regions
    assert(max_old_reserve >= (reserve_for_mixed + reserve_for_promo), "Sanity");
    const size_t available_for_additional_promotions = max_old_reserve - (reserve_for_mixed + reserve_for_promo);
    size_t promo_need = (size_t)(promo_load * ShenandoahPromoEvacWaste);
    if (promo_need > reserve_for_promo) {
      reserve_for_promo += MIN2(promo_need - reserve_for_promo, available_for_additional_promotions);
    }
    // We've already reserved all the memory required for the promo_load, and possibly more.  The excess
    // can be consumed by objects promoted from regions that have not yet reached tenure age.
  }

  // This is the total old we want to reserve (initialized to the ideal reserve)
  size_t old_reserve = reserve_for_mixed + reserve_for_promo;

  // We now check if the old generation is running a surplus or a deficit.
  size_t old_region_deficit = 0;
  size_t old_region_surplus = 0;

  size_t mutator_region_xfer_limit = mutator_xfer_limit / region_size_bytes;
  // align the mutator_xfer_limit on region size
  mutator_xfer_limit = mutator_region_xfer_limit * region_size_bytes;

  if (old_available >= old_reserve) {
    // We are running a surplus, so the old region surplus can go to young
    const size_t old_surplus = old_available - old_reserve;
    old_region_surplus = old_surplus / region_size_bytes;
    const size_t unaffiliated_old_regions = old_generation()->free_unaffiliated_regions() + old_trashed_regions;
    old_region_surplus = MIN2(old_region_surplus, unaffiliated_old_regions);
    old_generation()->set_region_balance(checked_cast<ssize_t>(old_region_surplus));
  } else if (old_available + mutator_xfer_limit >= old_reserve) {
    // Mutator's xfer limit is sufficient to satisfy our need: transfer all memory from there
    size_t old_deficit = old_reserve - old_available;
    old_region_deficit = (old_deficit + region_size_bytes - 1) / region_size_bytes;
    old_generation()->set_region_balance(0 - checked_cast<ssize_t>(old_region_deficit));
  } else {
   // We'll try to xfer from both mutator excess and from young collector reserve
    size_t available_reserves = old_available + young_reserve + mutator_xfer_limit;
    size_t old_entitlement = (available_reserves  * ShenandoahOldEvacPercent) / 100;

    // Round old_entitlement down to nearest multiple of regions to be transferred to old
    size_t entitled_xfer = old_entitlement - old_available;
    entitled_xfer = region_size_bytes * (entitled_xfer / region_size_bytes);
    size_t unaffiliated_young_regions = young_generation()->free_unaffiliated_regions();
    size_t unaffiliated_young_memory = unaffiliated_young_regions * region_size_bytes;
    if (entitled_xfer > unaffiliated_young_memory) {
      entitled_xfer = unaffiliated_young_memory;
    }
    old_entitlement = old_available + entitled_xfer;
    if (old_entitlement < old_reserve) {
      // There's not enough memory to satisfy our desire.  Scale back our old-gen intentions.
      size_t budget_overrun = old_reserve - old_entitlement;;
      if (reserve_for_promo > budget_overrun) {
        reserve_for_promo -= budget_overrun;
        old_reserve -= budget_overrun;
      } else {
        budget_overrun -= reserve_for_promo;
        reserve_for_promo = 0;
        reserve_for_mixed = (reserve_for_mixed > budget_overrun)? reserve_for_mixed - budget_overrun: 0;
        old_reserve = reserve_for_promo + reserve_for_mixed;
      }
    }

    // Because of adjustments above, old_reserve may be smaller now than it was when we tested the branch
    //   condition above: "(old_available + mutator_xfer_limit >= old_reserve)
    // Therefore, we do NOT know that: mutator_xfer_limit < old_reserve - old_available

    size_t old_deficit = old_reserve - old_available;
    old_region_deficit = (old_deficit + region_size_bytes - 1) / region_size_bytes;

    // Shrink young_reserve to account for loan to old reserve
    const size_t reserve_xfer_regions = old_region_deficit - mutator_region_xfer_limit;
    young_reserve -= reserve_xfer_regions * region_size_bytes;
    old_generation()->set_region_balance(0 - checked_cast<ssize_t>(old_region_deficit));
  }

  assert(old_region_deficit == 0 || old_region_surplus == 0, "Only surplus or deficit, never both");
  assert(young_reserve + reserve_for_mixed + reserve_for_promo <= old_available + young_available,
         "Cannot reserve more memory than is available: %zu + %zu + %zu <= %zu + %zu",
         young_reserve, reserve_for_mixed, reserve_for_promo, old_available, young_available);

  // deficit/surplus adjustments to generation sizes will precede rebuild
  young_generation()->set_evacuation_reserve(young_reserve);
  old_generation()->set_evacuation_reserve(reserve_for_mixed);
  old_generation()->set_promoted_reserve(reserve_for_promo);
}

void ShenandoahGenerationalHeap::coalesce_and_fill_old_regions(bool concurrent) {
  class ShenandoahGlobalCoalesceAndFill : public WorkerTask {
  private:
      ShenandoahPhaseTimings::Phase _phase;
      ShenandoahRegionIterator _regions;
  public:
    explicit ShenandoahGlobalCoalesceAndFill(ShenandoahPhaseTimings::Phase phase) :
      WorkerTask("Shenandoah Global Coalesce"),
      _phase(phase) {}

    void work(uint worker_id) override {
      ShenandoahWorkerTimingsTracker timer(_phase,
                                           ShenandoahPhaseTimings::ScanClusters,
                                           worker_id, true);
      ShenandoahHeapRegion* region;
      while ((region = _regions.next()) != nullptr) {
        // old region is not in the collection set and was not immediately trashed
        if (region->is_old() && region->is_active() && !region->is_humongous()) {
          // Reset the coalesce and fill boundary because this is a global collect
          // and cannot be preempted by young collects. We want to be sure the entire
          // region is coalesced here and does not resume from a previously interrupted
          // or completed coalescing.
          region->begin_preemptible_coalesce_and_fill();
          region->oop_coalesce_and_fill(false);
        }
      }
    }
  };

  ShenandoahPhaseTimings::Phase phase = concurrent ?
          ShenandoahPhaseTimings::conc_coalesce_and_fill :
          ShenandoahPhaseTimings::degen_gc_coalesce_and_fill;

  // This is not cancellable
  ShenandoahGlobalCoalesceAndFill coalesce(phase);
  workers()->run_task(&coalesce);
  old_generation()->set_parsable(true);
}

template<bool CONCURRENT>
class ShenandoahGenerationalUpdateHeapRefsTask : public WorkerTask {
private:
  // For update refs, _generation will be young or global. Mixed collections use the young generation.
  ShenandoahGeneration* _generation;
  ShenandoahGenerationalHeap* _heap;
  ShenandoahRegionIterator* _regions;
  ShenandoahRegionChunkIterator* _work_chunks;

public:
  ShenandoahGenerationalUpdateHeapRefsTask(ShenandoahGeneration* generation,
                                           ShenandoahRegionIterator* regions,
                                           ShenandoahRegionChunkIterator* work_chunks) :
          WorkerTask("Shenandoah Update References"),
          _generation(generation),
          _heap(ShenandoahGenerationalHeap::heap()),
          _regions(regions),
          _work_chunks(work_chunks)
  {
    const bool old_bitmap_stable = _heap->old_generation()->is_mark_complete();
    log_debug(gc, remset)("Update refs, scan remembered set using bitmap: %s", BOOL_TO_STR(old_bitmap_stable));
  }

  void work(uint worker_id) override {
    if (CONCURRENT) {
      ShenandoahConcurrentWorkerSession worker_session(worker_id);
      ShenandoahSuspendibleThreadSetJoiner stsj;
      do_work<ShenandoahConcUpdateRefsClosure>(worker_id);
    } else {
      ShenandoahParallelWorkerSession worker_session(worker_id);
      do_work<ShenandoahNonConcUpdateRefsClosure>(worker_id);
    }
  }

private:
  template<class T>
  void do_work(uint worker_id) {
    T cl;

    if (CONCURRENT && (worker_id == 0)) {
      // We ask the first worker to replenish the Mutator free set by moving regions previously reserved to hold the
      // results of evacuation.  These reserves are no longer necessary because evacuation has completed.
      size_t cset_regions = _heap->collection_set()->count();

      // Now that evacuation is done, we can reassign any regions that had been reserved to hold the results of evacuation
      // to the mutator free set.  At the end of GC, we will have cset_regions newly evacuated fully empty regions from
      // which we will be able to replenish the Collector free set and the OldCollector free set in preparation for the
      // next GC cycle.
      _heap->free_set()->move_regions_from_collector_to_mutator(cset_regions);
    }
    // If !CONCURRENT, there's no value in expanding Mutator free set

    ShenandoahHeapRegion* r = _regions->next();
    // We update references for global, mixed, and young collections.
    assert(_generation->is_mark_complete(), "Expected complete marking");
    ShenandoahMarkingContext* const ctx = _heap->marking_context();
    bool is_mixed = _heap->collection_set()->has_old_regions();
    while (r != nullptr) {
      HeapWord* update_watermark = r->get_update_watermark();
      assert(update_watermark >= r->bottom(), "sanity");

      log_debug(gc)("Update refs worker " UINT32_FORMAT ", looking at region %zu", worker_id, r->index());
      if (r->is_active() && !r->is_cset()) {
        if (r->is_young()) {
          _heap->marked_object_oop_iterate(r, &cl, update_watermark);
        } else if (r->is_old()) {
          if (_generation->is_global()) {

            _heap->marked_object_oop_iterate(r, &cl, update_watermark);
          }
          // Otherwise, this is an old region in a young or mixed cycle.  Process it during a second phase, below.
        } else {
          // Because updating of references runs concurrently, it is possible that a FREE inactive region transitions
          // to a non-free active region while this loop is executing.  Whenever this happens, the changing of a region's
          // active status may propagate at a different speed than the changing of the region's affiliation.

          // When we reach this control point, it is because a race has allowed a region's is_active() status to be seen
          // by this thread before the region's affiliation() is seen by this thread.

          // It's ok for this race to occur because the newly transformed region does not have any references to be
          // updated.

          assert(r->get_update_watermark() == r->bottom(),
                 "%s Region %zu is_active but not recognized as YOUNG or OLD so must be newly transitioned from FREE",
                 r->affiliation_name(), r->index());
        }
      }

      if (_heap->check_cancelled_gc_and_yield(CONCURRENT)) {
        return;
      }

      r = _regions->next();
    }

    if (_generation->is_young()) {
      // Since this is generational and not GLOBAL, we have to process the remembered set.  There's no remembered
      // set processing if not in generational mode or if GLOBAL mode.

      // After this thread has exhausted its traditional update-refs work, it continues with updating refs within
      // remembered set. The remembered set workload is better balanced between threads, so threads that are "behind"
      // can catch up with other threads during this phase, allowing all threads to work more effectively in parallel.
      update_references_in_remembered_set(worker_id, cl, ctx, is_mixed);
    }
  }

  template<class T>
  void update_references_in_remembered_set(uint worker_id, T &cl, const ShenandoahMarkingContext* ctx, bool is_mixed) {

    struct ShenandoahRegionChunk assignment;
    ShenandoahScanRemembered* scanner = _heap->old_generation()->card_scan();

    while (!_heap->check_cancelled_gc_and_yield(CONCURRENT) && _work_chunks->next(&assignment)) {
      // Keep grabbing next work chunk to process until finished, or asked to yield
      ShenandoahHeapRegion* r = assignment._r;
      if (r->is_active() && !r->is_cset() && r->is_old()) {
        HeapWord* start_of_range = r->bottom() + assignment._chunk_offset;
        HeapWord* end_of_range = r->get_update_watermark();
        if (end_of_range > start_of_range + assignment._chunk_size) {
          end_of_range = start_of_range + assignment._chunk_size;
        }

        if (start_of_range >= end_of_range) {
          continue;
        }

        // Old region in a young cycle or mixed cycle.
        if (is_mixed) {
          if (r->is_humongous()) {
            // Need to examine both dirty and clean cards during mixed evac.
            r->oop_iterate_humongous_slice_all(&cl,start_of_range, assignment._chunk_size);
          } else {
            // Since this is mixed evacuation, old regions that are candidates for collection have not been coalesced
            // and filled.  This will use mark bits to find objects that need to be updated.
            update_references_in_old_region(cl, ctx, scanner, r, start_of_range, end_of_range);
          }
        } else {
          // This is a young evacuation
          size_t cluster_size = CardTable::card_size_in_words() * ShenandoahCardCluster::CardsPerCluster;
          size_t clusters = assignment._chunk_size / cluster_size;
          assert(clusters * cluster_size == assignment._chunk_size, "Chunk assignment must align on cluster boundaries");
          scanner->process_region_slice(r, assignment._chunk_offset, clusters, end_of_range, &cl, true, worker_id);
        }
      }
    }
  }

  template<class T>
  void update_references_in_old_region(T &cl, const ShenandoahMarkingContext* ctx, ShenandoahScanRemembered* scanner,
                                    const ShenandoahHeapRegion* r, HeapWord* start_of_range,
                                    HeapWord* end_of_range) const {
    // In case last object in my range spans boundary of my chunk, I may need to scan all the way to top()
    ShenandoahObjectToOopBoundedClosure<T> objs(&cl, start_of_range, r->top());

    // Any object that begins in a previous range is part of a different scanning assignment.  Any object that
    // starts after end_of_range is also not my responsibility.  (Either allocated during evacuation, so does
    // not hold pointers to from-space, or is beyond the range of my assigned work chunk.)

    // Find the first object that begins in my range, if there is one. Note that `p` will be set to `end_of_range`
    // when no live object is found in the range.
    HeapWord* tams = ctx->top_at_mark_start(r);
    HeapWord* p = get_first_object_start_word(ctx, scanner, tams, start_of_range, end_of_range);

    while (p < end_of_range) {
      // p is known to point to the beginning of marked object obj
      oop obj = cast_to_oop(p);
      objs.do_object(obj);
      HeapWord* prev_p = p;
      p += obj->size();
      if (p < tams) {
        p = ctx->get_next_marked_addr(p, tams);
        // If there are no more marked objects before tams, this returns tams.  Note that tams is
        // either >= end_of_range, or tams is the start of an object that is marked.
      }
      assert(p != prev_p, "Lack of forward progress");
    }
  }

  HeapWord* get_first_object_start_word(const ShenandoahMarkingContext* ctx, ShenandoahScanRemembered* scanner, HeapWord* tams,
                                        HeapWord* start_of_range, HeapWord* end_of_range) const {
    HeapWord* p = start_of_range;

    if (p >= tams) {
      // We cannot use ctx->is_marked(obj) to test whether an object begins at this address.  Instead,
      // we need to use the remembered set crossing map to advance p to the first object that starts
      // within the enclosing card.
      size_t card_index = scanner->card_index_for_addr(start_of_range);
      while (true) {
        HeapWord* first_object = scanner->first_object_in_card(card_index);
        if (first_object != nullptr) {
          p = first_object;
          break;
        } else if (scanner->addr_for_card_index(card_index + 1) < end_of_range) {
          card_index++;
        } else {
          // Signal that no object was found in range
          p = end_of_range;
          break;
        }
      }
    } else if (!ctx->is_marked(cast_to_oop(p))) {
      p = ctx->get_next_marked_addr(p, tams);
      // If there are no more marked objects before tams, this returns tams.
      // Note that tams is either >= end_of_range, or tams is the start of an object that is marked.
    }
    return p;
  }
};

void ShenandoahGenerationalHeap::update_heap_references(ShenandoahGeneration* generation, bool concurrent) {
  assert(!is_full_gc_in_progress(), "Only for concurrent and degenerated GC");
  const uint nworkers = workers()->active_workers();
  ShenandoahRegionChunkIterator work_list(nworkers);
  if (concurrent) {
    ShenandoahGenerationalUpdateHeapRefsTask<true> task(generation, &_update_refs_iterator, &work_list);
    workers()->run_task(&task);
  } else {
    ShenandoahGenerationalUpdateHeapRefsTask<false> task(generation, &_update_refs_iterator, &work_list);
    workers()->run_task(&task);
  }

  if (ShenandoahEnableCardStats) {
    // Only do this if we are collecting card stats
    ShenandoahScanRemembered* card_scan = old_generation()->card_scan();
    assert(card_scan != nullptr, "Card table must exist when card stats are enabled");
    card_scan->log_card_stats(nworkers, CARD_STAT_UPDATE_REFS);
  }
}

struct ShenandoahCompositeRegionClosure {
  template<typename C1, typename C2>
  class Closure : public ShenandoahHeapRegionClosure {
  private:
    C1 &_c1;
    C2 &_c2;

  public:
    Closure(C1 &c1, C2 &c2) : ShenandoahHeapRegionClosure(), _c1(c1), _c2(c2) {}

    void heap_region_do(ShenandoahHeapRegion* r) override {
      _c1.heap_region_do(r);
      _c2.heap_region_do(r);
    }

    bool is_thread_safe() override {
      return _c1.is_thread_safe() && _c2.is_thread_safe();
    }
  };

  template<typename C1, typename C2>
  static Closure<C1, C2> of(C1 &c1, C2 &c2) {
    return Closure<C1, C2>(c1, c2);
  }
};

class ShenandoahUpdateRegionAges : public ShenandoahHeapRegionClosure {
private:
  ShenandoahMarkingContext* _ctx;

public:
  explicit ShenandoahUpdateRegionAges(ShenandoahMarkingContext* ctx) : _ctx(ctx) { }

  void heap_region_do(ShenandoahHeapRegion* r) override {
    // Maintenance of region age must follow evacuation in order to account for
    // evacuation allocations within survivor regions.  We consult region age during
    // the subsequent evacuation to determine whether certain objects need to
    // be promoted.
    if (r->is_young() && r->is_active()) {
      HeapWord *tams = _ctx->top_at_mark_start(r);
      HeapWord *top = r->top();

      // Allocations move the watermark when top moves.  However, compacting
      // objects will sometimes lower top beneath the watermark, after which,
      // attempts to read the watermark will assert out (watermark should not be
      // higher than top).
      if (top > tams) {
        // There have been allocations in this region since the start of the cycle.
        // Any objects new to this region must not assimilate elevated age.
        r->reset_age();
      } else if (ShenandoahGenerationalHeap::heap()->is_aging_cycle()) {
        r->increment_age();
      }
    }
  }

  bool is_thread_safe() override {
    return true;
  }
};

void ShenandoahGenerationalHeap::final_update_refs_update_region_states() {
  ShenandoahSynchronizePinnedRegionStates pins;
  ShenandoahUpdateRegionAges ages(marking_context());
  auto cl = ShenandoahCompositeRegionClosure::of(pins, ages);
  parallel_heap_region_iterate(&cl);
}

void ShenandoahGenerationalHeap::complete_degenerated_cycle() {
  shenandoah_assert_heaplocked_or_safepoint();
  if (!old_generation()->is_parsable()) {
    ShenandoahGCPhase phase(ShenandoahPhaseTimings::degen_gc_coalesce_and_fill);
    coalesce_and_fill_old_regions(false);
  }

  log_info(gc, cset)("Degenerated cycle complete, promotions reserved: %zu, promotions expended: %zu, failed count: %zu, failed bytes: %zu",
                     old_generation()->get_promoted_reserve(), old_generation()->get_promoted_expended(),
                     old_generation()->get_promotion_failed_count(), old_generation()->get_promotion_failed_words() * HeapWordSize);
}

void ShenandoahGenerationalHeap::complete_concurrent_cycle() {
  if (!old_generation()->is_parsable()) {
    // Class unloading may render the card offsets unusable, so we must rebuild them before
    // the next remembered set scan. We _could_ let the control thread do this sometime after
    // the global cycle has completed and before the next young collection, but under memory
    // pressure the control thread may not have the time (that is, because it's running back
    // to back GCs). In that scenario, we would have to make the old regions parsable before
    // we could start a young collection. This could delay the start of the young cycle and
    // throw off the heuristics.
    entry_global_coalesce_and_fill();
  }

  log_info(gc, cset)("Concurrent cycle complete, promotions reserved: %zu, promotions expended: %zu, failed count: %zu, failed bytes: %zu",
                     old_generation()->get_promoted_reserve(), old_generation()->get_promoted_expended(),
                     old_generation()->get_promotion_failed_count(), old_generation()->get_promotion_failed_words() * HeapWordSize);
}

void ShenandoahGenerationalHeap::entry_global_coalesce_and_fill() {
  const char* msg = "Coalescing and filling old regions";
  ShenandoahConcurrentPhase gc_phase(msg, ShenandoahPhaseTimings::conc_coalesce_and_fill);

  TraceCollectorStats tcs(monitoring_support()->concurrent_collection_counters());
  EventMark em("%s", msg);
  ShenandoahWorkerScope scope(workers(),
                              ShenandoahWorkerPolicy::calc_workers_for_conc_marking(),
                              "concurrent coalesce and fill");

  coalesce_and_fill_old_regions(true);
}

void ShenandoahGenerationalHeap::update_region_ages(ShenandoahMarkingContext* ctx) {
  ShenandoahUpdateRegionAges cl(ctx);
  parallel_heap_region_iterate(&cl);
}
