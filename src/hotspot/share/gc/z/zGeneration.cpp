/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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
 */

#include "precompiled.hpp"
#include "classfile/classLoaderDataGraph.hpp"
#include "code/nmethod.hpp"
#include "gc/shared/classUnloadingContext.hpp"
#include "gc/shared/gcLocker.hpp"
#include "gc/shared/gcVMOperations.hpp"
#include "gc/shared/isGCActiveMark.hpp"
#include "gc/shared/suspendibleThreadSet.hpp"
#include "gc/z/zAllocator.inline.hpp"
#include "gc/z/zBarrierSet.hpp"
#include "gc/z/zBarrierSetAssembler.hpp"
#include "gc/z/zBarrierSetNMethod.hpp"
#include "gc/z/zBreakpoint.hpp"
#include "gc/z/zCollectedHeap.hpp"
#include "gc/z/zDriver.hpp"
#include "gc/z/zForwarding.hpp"
#include "gc/z/zForwardingTable.inline.hpp"
#include "gc/z/zGeneration.inline.hpp"
#include "gc/z/zHeap.inline.hpp"
#include "gc/z/zJNICritical.hpp"
#include "gc/z/zMark.inline.hpp"
#include "gc/z/zPageAllocator.hpp"
#include "gc/z/zRelocationSet.inline.hpp"
#include "gc/z/zRelocationSetSelector.inline.hpp"
#include "gc/z/zRemembered.hpp"
#include "gc/z/zRootsIterator.hpp"
#include "gc/z/zStat.hpp"
#include "gc/z/zTask.hpp"
#include "gc/z/zUncoloredRoot.inline.hpp"
#include "gc/z/zVerify.hpp"
#include "gc/z/zWorkers.hpp"
#include "interpreter/oopMapCache.hpp"
#include "logging/log.hpp"
#include "memory/universe.hpp"
#include "prims/jvmtiTagMap.hpp"
#include "runtime/atomic.hpp"
#include "runtime/continuation.hpp"
#include "runtime/handshake.hpp"
#include "runtime/safepoint.hpp"
#include "runtime/threads.hpp"
#include "runtime/vmOperations.hpp"
#include "runtime/vmThread.hpp"
#include "utilities/debug.hpp"
#include "utilities/events.hpp"

static const ZStatPhaseGeneration ZPhaseGenerationYoung[] {
  ZStatPhaseGeneration("Young Generation", ZGenerationId::young),
  ZStatPhaseGeneration("Young Generation (Promote All)", ZGenerationId::young),
  ZStatPhaseGeneration("Young Generation (Collect Roots)", ZGenerationId::young),
  ZStatPhaseGeneration("Young Generation", ZGenerationId::young)
};

static const ZStatPhaseGeneration ZPhaseGenerationOld("Old Generation", ZGenerationId::old);

static const ZStatPhasePause      ZPhasePauseMarkStartYoung("Pause Mark Start", ZGenerationId::young);
static const ZStatPhasePause      ZPhasePauseMarkStartYoungAndOld("Pause Mark Start (Major)", ZGenerationId::young);
static const ZStatPhaseConcurrent ZPhaseConcurrentMarkYoung("Concurrent Mark", ZGenerationId::young);
static const ZStatPhaseConcurrent ZPhaseConcurrentMarkContinueYoung("Concurrent Mark Continue", ZGenerationId::young);
static const ZStatPhasePause      ZPhasePauseMarkEndYoung("Pause Mark End", ZGenerationId::young);
static const ZStatPhaseConcurrent ZPhaseConcurrentMarkFreeYoung("Concurrent Mark Free", ZGenerationId::young);
static const ZStatPhaseConcurrent ZPhaseConcurrentResetRelocationSetYoung("Concurrent Reset Relocation Set", ZGenerationId::young);
static const ZStatPhaseConcurrent ZPhaseConcurrentSelectRelocationSetYoung("Concurrent Select Relocation Set", ZGenerationId::young);
static const ZStatPhasePause      ZPhasePauseRelocateStartYoung("Pause Relocate Start", ZGenerationId::young);
static const ZStatPhaseConcurrent ZPhaseConcurrentRelocatedYoung("Concurrent Relocate", ZGenerationId::young);

static const ZStatPhaseConcurrent ZPhaseConcurrentMarkOld("Concurrent Mark", ZGenerationId::old);
static const ZStatPhaseConcurrent ZPhaseConcurrentMarkContinueOld("Concurrent Mark Continue", ZGenerationId::old);
static const ZStatPhasePause      ZPhasePauseMarkEndOld("Pause Mark End", ZGenerationId::old);
static const ZStatPhaseConcurrent ZPhaseConcurrentMarkFreeOld("Concurrent Mark Free", ZGenerationId::old);
static const ZStatPhaseConcurrent ZPhaseConcurrentProcessNonStrongOld("Concurrent Process Non-Strong", ZGenerationId::old);
static const ZStatPhaseConcurrent ZPhaseConcurrentResetRelocationSetOld("Concurrent Reset Relocation Set", ZGenerationId::old);
static const ZStatPhaseConcurrent ZPhaseConcurrentSelectRelocationSetOld("Concurrent Select Relocation Set", ZGenerationId::old);
static const ZStatPhasePause      ZPhasePauseRelocateStartOld("Pause Relocate Start", ZGenerationId::old);
static const ZStatPhaseConcurrent ZPhaseConcurrentRelocatedOld("Concurrent Relocate", ZGenerationId::old);
static const ZStatPhaseConcurrent ZPhaseConcurrentRemapRootsOld("Concurrent Remap Roots", ZGenerationId::old);

static const ZStatSubPhase ZSubPhaseConcurrentMarkRootsYoung("Concurrent Mark Roots", ZGenerationId::young);
static const ZStatSubPhase ZSubPhaseConcurrentMarkFollowYoung("Concurrent Mark Follow", ZGenerationId::young);

static const ZStatSubPhase ZSubPhaseConcurrentMarkRootsOld("Concurrent Mark Roots", ZGenerationId::old);
static const ZStatSubPhase ZSubPhaseConcurrentMarkFollowOld("Concurrent Mark Follow", ZGenerationId::old);
static const ZStatSubPhase ZSubPhaseConcurrentRemapRootsColoredOld("Concurrent Remap Roots Colored", ZGenerationId::old);
static const ZStatSubPhase ZSubPhaseConcurrentRemapRootsUncoloredOld("Concurrent Remap Roots Uncolored", ZGenerationId::old);
static const ZStatSubPhase ZSubPhaseConcurrentRemapRememberedOld("Concurrent Remap Remembered", ZGenerationId::old);

static const ZStatSampler ZSamplerJavaThreads("System", "Java Threads", ZStatUnitThreads);

ZGenerationYoung* ZGeneration::_young;
ZGenerationOld*   ZGeneration::_old;

ZGeneration::ZGeneration(ZGenerationId id, ZPageTable* page_table, ZPageAllocator* page_allocator)
  : _id(id),
    _page_allocator(page_allocator),
    _page_table(page_table),
    _forwarding_table(),
    _workers(id, &_stat_workers),
    _mark(this, page_table),
    _relocate(this),
    _relocation_set(this),
    _freed(0),
    _promoted(0),
    _compacted(0),
    _phase(Phase::Relocate),
    _seqnum(1),
    _stat_heap(),
    _stat_cycle(),
    _stat_workers(),
    _stat_mark(),
    _stat_relocation(),
    _gc_timer(nullptr) {}

bool ZGeneration::is_initialized() const {
  return _mark.is_initialized();
}

ZWorkers* ZGeneration::workers() {
  return &_workers;
}

uint ZGeneration::active_workers() const {
  return _workers.active_workers();
}

void ZGeneration::set_active_workers(uint nworkers) {
  _workers.set_active_workers(nworkers);
}

void ZGeneration::threads_do(ThreadClosure* tc) const {
  _workers.threads_do(tc);
}

void ZGeneration::mark_flush_and_free(Thread* thread) {
  _mark.flush_and_free(thread);
}

void ZGeneration::mark_free() {
   _mark.free();
}

void ZGeneration::free_empty_pages(ZRelocationSetSelector* selector, int bulk) {
  // Freeing empty pages in bulk is an optimization to avoid grabbing
  // the page allocator lock, and trying to satisfy stalled allocations
  // too frequently.
  if (selector->should_free_empty_pages(bulk)) {
    const size_t freed = ZHeap::heap()->free_empty_pages(selector->empty_pages());
    increase_freed(freed);
    selector->clear_empty_pages();
  }
}

void ZGeneration::flip_age_pages(const ZRelocationSetSelector* selector) {
  if (is_young()) {
    _relocate.flip_age_pages(selector->not_selected_small());
    _relocate.flip_age_pages(selector->not_selected_medium());
    _relocate.flip_age_pages(selector->not_selected_large());
  }
}

static double fragmentation_limit(ZGenerationId generation) {
  if (generation == ZGenerationId::old) {
    return ZFragmentationLimit;
  } else {
    return ZYoungCompactionLimit;
  }
}

void ZGeneration::select_relocation_set(ZGenerationId generation, bool promote_all) {
  // Register relocatable pages with selector
  ZRelocationSetSelector selector(fragmentation_limit(generation));
  {
    ZGenerationPagesIterator pt_iter(_page_table, _id, _page_allocator);
    for (ZPage* page; pt_iter.next(&page);) {
      if (!page->is_relocatable()) {
        // Not relocatable, don't register
        // Note that the seqnum can change under our feet here as the page
        // can be concurrently freed and recycled by a concurrent generation
        // collection. However this property is stable across such transitions.
        // If it was not relocatable before recycling, then it won't be
        // relocatable after it gets recycled either, as the seqnum atomically
        // becomes allocating for the given generation. The opposite property
        // also holds: if the page is relocatable, then it can't have been
        // concurrently freed; if it was re-allocated it would not be
        // relocatable, and if it was not re-allocated we know that it was
        // allocated earlier than mark start of the current generation
        // collection.
        continue;
      }

      if (page->is_marked()) {
        // Register live page
        selector.register_live_page(page);
      } else {
        // Register empty page
        selector.register_empty_page(page);

        // Reclaim empty pages in bulk

        // An active iterator blocks immediate recycle and delete of pages.
        // The intent it to allow the code that iterates over the pages to
        // safely read the properties of the pages without them being changed
        // by another thread. However, this function both iterates over the
        // pages AND frees/recycles them. We "yield" the iterator, so that we
        // can perform immediate recycling (as long as no other thread is
        // iterating over the pages). The contract is that the pages that are
        // about to be freed are "owned" by this thread, and no other thread
        // will change their states.
        pt_iter.yield([&]() {
          free_empty_pages(&selector, 64 /* bulk */);
        });
      }
    }

    // Reclaim remaining empty pages
    free_empty_pages(&selector, 0 /* bulk */);
  }

  // Select relocation set
  selector.select();

  // Selecting tenuring threshold must be done after select
  // which produces the liveness data, but before install,
  // which consumes the tenuring threshold.
  if (generation == ZGenerationId::young) {
    ZGeneration::young()->select_tenuring_threshold(selector.stats(), promote_all);
  }

  // Install relocation set
  _relocation_set.install(&selector);

  // Flip age young pages that were not selected
  flip_age_pages(&selector);

  // Setup forwarding table
  ZRelocationSetIterator rs_iter(&_relocation_set);
  for (ZForwarding* forwarding; rs_iter.next(&forwarding);) {
    _forwarding_table.insert(forwarding);
  }

  // Update statistics
  stat_relocation()->at_select_relocation_set(selector.stats());
  stat_heap()->at_select_relocation_set(selector.stats());
}

ZRelocationSetParallelIterator ZGeneration::relocation_set_parallel_iterator() {
  return ZRelocationSetParallelIterator(&_relocation_set);
}

void ZGeneration::reset_relocation_set() {
  // Reset forwarding table
  ZRelocationSetIterator iter(&_relocation_set);
  for (ZForwarding* forwarding; iter.next(&forwarding);) {
    _forwarding_table.remove(forwarding);
  }

  // Reset relocation set
  _relocation_set.reset(_page_allocator);
}

void ZGeneration::synchronize_relocation() {
  _relocate.synchronize();
}

void ZGeneration::desynchronize_relocation() {
  _relocate.desynchronize();
}

bool ZGeneration::is_relocate_queue_active() const {
  return _relocate.is_queue_active();
}

void ZGeneration::reset_statistics() {
  assert(SafepointSynchronize::is_at_safepoint(), "Should be at safepoint");
  _freed = 0;
  _promoted = 0;
  _compacted = 0;
  _page_allocator->reset_statistics(_id);
}

ssize_t ZGeneration::freed() const {
  return _freed;
}

void ZGeneration::increase_freed(size_t size) {
  Atomic::add(&_freed, size, memory_order_relaxed);
}

size_t ZGeneration::promoted() const {
  return _promoted;
}

void ZGeneration::increase_promoted(size_t size) {
  Atomic::add(&_promoted, size, memory_order_relaxed);
}

size_t ZGeneration::compacted() const {
  return _compacted;
}

void ZGeneration::increase_compacted(size_t size) {
  Atomic::add(&_compacted, size, memory_order_relaxed);
}

ConcurrentGCTimer* ZGeneration::gc_timer() const {
  return _gc_timer;
}

void ZGeneration::set_gc_timer(ConcurrentGCTimer* gc_timer) {
  assert(_gc_timer == nullptr, "Incorrect scoping");
  _gc_timer = gc_timer;
}

void ZGeneration::clear_gc_timer() {
  assert(_gc_timer != nullptr, "Incorrect scoping");
  _gc_timer = nullptr;
}

void ZGeneration::log_phase_switch(Phase from, Phase to) {
  const char* const str[] = {
    "Young Mark Start",
    "Young Mark End",
    "Young Relocate Start",
    "Old Mark Start",
    "Old Mark End",
    "Old Relocate Start"
  };

  size_t index = 0;

  if (is_old()) {
    index += 3;
  }

  if (to == Phase::Relocate) {
    index += 2;
  }

  if (from == Phase::Mark && to == Phase::MarkComplete) {
    index += 1;
  }

  assert(index < ARRAY_SIZE(str), "OOB: " SIZE_FORMAT " < " SIZE_FORMAT, index, ARRAY_SIZE(str));

  Events::log_zgc_phase_switch("%-21s %4u", str[index], seqnum());
}

void ZGeneration::set_phase(Phase new_phase) {
  log_phase_switch(_phase, new_phase);

  _phase = new_phase;
}

void ZGeneration::at_collection_start(ConcurrentGCTimer* gc_timer) {
  set_gc_timer(gc_timer);
  stat_cycle()->at_start();
  stat_heap()->at_collection_start(_page_allocator->stats(this));
  workers()->set_active();
}

void ZGeneration::at_collection_end() {
  workers()->set_inactive();
  stat_cycle()->at_end(stat_workers(), should_record_stats());
  // The heap at collection end data is gathered at relocate end
  clear_gc_timer();
}

const char* ZGeneration::phase_to_string() const {
  switch (_phase) {
  case Phase::Mark:
    return "Mark";

  case Phase::MarkComplete:
    return "MarkComplete";

  case Phase::Relocate:
    return "Relocate";

  default:
    return "Unknown";
  }
}

class VM_ZOperation : public VM_Operation {
private:
  const uint           _gc_id;
  const GCCause::Cause _gc_cause;
  bool                 _success;

public:
  VM_ZOperation(GCCause::Cause gc_cause)
    : _gc_id(GCId::current()),
      _gc_cause(gc_cause),
      _success(false) {}

  virtual const char* cause() const {
    return GCCause::to_string(_gc_cause);
  }

  virtual bool block_jni_critical() const {
    // Blocking JNI critical regions is needed in operations where we change
    // the bad mask or move objects. Changing the bad mask will invalidate all
    // oops, which makes it conceptually the same thing as moving all objects.
    return false;
  }

  virtual bool skip_thread_oop_barriers() const {
    return true;
  }

  virtual bool do_operation() = 0;

  virtual bool doit_prologue() {
    Heap_lock->lock();
    return true;
  }

  virtual void doit() {
    // Setup GC id and active marker
    GCIdMark gc_id_mark(_gc_id);
    IsSTWGCActiveMark gc_active_mark;

    // Verify before operation
    ZVerify::before_zoperation();

    // Execute operation
    _success = do_operation();

    // Update statistics
    ZStatSample(ZSamplerJavaThreads, Threads::number_of_threads());
  }

  virtual void doit_epilogue() {
    Heap_lock->unlock();

    // GC thread root traversal likely used OopMapCache a lot, which
    // might have created lots of old entries. Trigger the cleanup now.
    OopMapCache::trigger_cleanup();
  }

  bool success() const {
    return _success;
  }

  bool pause() {
    if (block_jni_critical()) {
      ZJNICritical::block();
    }

    VMThread::execute(this);

    if (block_jni_critical()) {
      ZJNICritical::unblock();
    }

    return _success;
  }
};

ZYoungTypeSetter::ZYoungTypeSetter(ZYoungType type) {
  assert(ZGeneration::young()->_active_type == ZYoungType::none, "Invalid type");
  ZGeneration::young()->_active_type = type;
}

ZYoungTypeSetter::~ZYoungTypeSetter() {
  assert(ZGeneration::young()->_active_type != ZYoungType::none, "Invalid type");
  ZGeneration::young()->_active_type = ZYoungType::none;
}

ZGenerationYoung::ZGenerationYoung(ZPageTable* page_table,
                                   const ZForwardingTable* old_forwarding_table,
                                   ZPageAllocator* page_allocator)
  : ZGeneration(ZGenerationId::young, page_table, page_allocator),
    _active_type(ZYoungType::none),
    _tenuring_threshold(0),
    _remembered(page_table, old_forwarding_table, page_allocator),
    _jfr_tracer() {
  ZGeneration::_young = this;
}

uint ZGenerationYoung::tenuring_threshold() {
  return _tenuring_threshold;
}

class ZGenerationCollectionScopeYoung : public StackObj {
private:
  ZYoungTypeSetter _type_setter;
  ZStatTimer       _stat_timer;

public:
  ZGenerationCollectionScopeYoung(ZYoungType type, ConcurrentGCTimer* gc_timer)
    : _type_setter(type),
      _stat_timer(ZPhaseGenerationYoung[(int)type], gc_timer) {
    // Update statistics and set the GC timer
    ZGeneration::young()->at_collection_start(gc_timer);
  }

  ~ZGenerationCollectionScopeYoung() {
    // Update statistics and clear the GC timer
    ZGeneration::young()->at_collection_end();
  }
};

bool ZGenerationYoung::should_record_stats() {
  return type() == ZYoungType::minor ||
         type() == ZYoungType::major_partial_roots;
}

void ZGenerationYoung::collect(ZYoungType type, ConcurrentGCTimer* timer) {
  ZGenerationCollectionScopeYoung scope(type, timer);

  // Phase 1: Pause Mark Start
  pause_mark_start();

  // Phase 2: Concurrent Mark
  concurrent_mark();

  abortpoint();

  // Phase 3: Pause Mark End
  while (!pause_mark_end()) {
    // Phase 3.5: Concurrent Mark Continue
    concurrent_mark_continue();

    abortpoint();
  }

  // Phase 4: Concurrent Mark Free
  concurrent_mark_free();

  abortpoint();

  // Phase 5: Concurrent Reset Relocation Set
  concurrent_reset_relocation_set();

  abortpoint();

  // Phase 6: Concurrent Select Relocation Set
  concurrent_select_relocation_set();

  abortpoint();

  // Phase 7: Pause Relocate Start
  pause_relocate_start();

  // Note that we can't have an abortpoint here. We need
  // to let concurrent_relocate() call abort_page()
  // on the remaining entries in the relocation set.

  // Phase 8: Concurrent Relocate
  concurrent_relocate();
}

class VM_ZMarkStartYoungAndOld : public VM_ZOperation {
public:
  VM_ZMarkStartYoungAndOld()
    : VM_ZOperation(ZDriver::major()->gc_cause()) {}

  virtual VMOp_Type type() const {
    return VMOp_ZMarkStartYoungAndOld;
  }

  virtual bool block_jni_critical() const {
    return true;
  }

  virtual bool do_operation() {
    ZStatTimerYoung timer(ZPhasePauseMarkStartYoungAndOld);
    ZServiceabilityPauseTracer tracer;

    ZCollectedHeap::heap()->increment_total_collections(true /* full */);
    ZGeneration::young()->mark_start();
    ZGeneration::old()->mark_start();

    return true;
  }
};

class VM_ZYoungOperation : public VM_ZOperation {
private:
  static ZDriver* driver() {
    if (ZGeneration::young()->type() == ZYoungType::minor) {
      return ZDriver::minor();
    } else {
      return ZDriver::major();
    }
  }

public:
  VM_ZYoungOperation()
    : VM_ZOperation(driver()->gc_cause()) {}
};

class VM_ZMarkStartYoung : public VM_ZYoungOperation {
public:
  virtual VMOp_Type type() const {
    return VMOp_ZMarkStartYoung;
  }

  virtual bool block_jni_critical() const {
    return true;
  }

  virtual bool do_operation() {
    ZStatTimerYoung timer(ZPhasePauseMarkStartYoung);
    ZServiceabilityPauseTracer tracer;

    ZCollectedHeap::heap()->increment_total_collections(false /* full */);
    ZGeneration::young()->mark_start();

    return true;
  }
};

void ZGenerationYoung::flip_mark_start() {
  ZGlobalsPointers::flip_young_mark_start();
  ZBarrierSet::assembler()->patch_barriers();
  ZVerify::on_color_flip();
}

void ZGenerationYoung::flip_relocate_start() {
  ZGlobalsPointers::flip_young_relocate_start();
  ZBarrierSet::assembler()->patch_barriers();
  ZVerify::on_color_flip();
}

void ZGenerationYoung::pause_mark_start() {
  if (type() == ZYoungType::major_full_roots ||
      type() == ZYoungType::major_partial_roots) {
    VM_ZMarkStartYoungAndOld().pause();
  } else {
    VM_ZMarkStartYoung().pause();
  }
}

void ZGenerationYoung::concurrent_mark() {
  ZStatTimerYoung timer(ZPhaseConcurrentMarkYoung);
  mark_roots();
  mark_follow();
}

class VM_ZMarkEndYoung : public VM_ZYoungOperation {
public:
  virtual VMOp_Type type() const {
    return VMOp_ZMarkEndYoung;
  }

  virtual bool do_operation() {
    ZStatTimerYoung timer(ZPhasePauseMarkEndYoung);
    ZServiceabilityPauseTracer tracer;

    return ZGeneration::young()->mark_end();
  }
};


bool ZGenerationYoung::pause_mark_end() {
  return VM_ZMarkEndYoung().pause();
}

void ZGenerationYoung::concurrent_mark_continue() {
  ZStatTimerYoung timer(ZPhaseConcurrentMarkContinueYoung);
  mark_follow();
}

void ZGenerationYoung::concurrent_mark_free() {
  ZStatTimerYoung timer(ZPhaseConcurrentMarkFreeYoung);
  mark_free();
}

void ZGenerationYoung::concurrent_reset_relocation_set() {
  ZStatTimerYoung timer(ZPhaseConcurrentResetRelocationSetYoung);
  reset_relocation_set();
}

void ZGenerationYoung::select_tenuring_threshold(ZRelocationSetSelectorStats stats, bool promote_all) {
  const char* reason = "";
  if (promote_all) {
    _tenuring_threshold = 0;
    reason = "Promote All";
  } else if (ZTenuringThreshold != -1) {
    _tenuring_threshold = static_cast<uint>(ZTenuringThreshold);
    reason = "ZTenuringThreshold";
  } else {
    _tenuring_threshold = compute_tenuring_threshold(stats);
    reason = "Computed";
  }
  log_info(gc, reloc)("Using tenuring threshold: %d (%s)", _tenuring_threshold, reason);
}

uint ZGenerationYoung::compute_tenuring_threshold(ZRelocationSetSelectorStats stats) {
  size_t young_live_total = 0;
  size_t young_live_last = 0;
  double young_life_expectancy_sum = 0.0;
  uint young_life_expectancy_samples = 0;
  uint last_populated_age = 0;
  size_t last_populated_live = 0;

  for (uint i = 0; i <= ZPageAgeMax; ++i) {
    const ZPageAge age = static_cast<ZPageAge>(i);
    const size_t young_live = stats.small(age).live() + stats.medium(age).live() + stats.large(age).live();
    if (young_live > 0) {
      last_populated_age = i;
      last_populated_live = young_live;
      if (young_live_last > 0) {
        young_life_expectancy_sum += double(young_live) / double(young_live_last);
        young_life_expectancy_samples++;
      }
    }
    young_live_total += young_live;
    young_live_last = young_live;
  }

  if (young_live_total == 0) {
    return 0;
  }

  const size_t young_used_at_mark_start = ZGeneration::young()->stat_heap()->used_generation_at_mark_start();
  const size_t young_garbage = ZGeneration::young()->stat_heap()->garbage_at_mark_end();
  const size_t young_allocated = ZGeneration::young()->stat_heap()->allocated_at_mark_end();
  const size_t soft_max_capacity = ZHeap::heap()->soft_max_capacity();

  // The life expectancy shows by what factor on average one age changes between
  // two ages in the age table. Values below 1 indicate generational behaviour where
  // the live bytes is shrinking from age to age. Values at or above 1 indicate
  // anti-generational patterns where the live bytes isn't going down or grows
  // from age to age.
  const double young_life_expectancy = young_life_expectancy_samples == 0 ? 1.0 : young_life_expectancy_sum / young_life_expectancy_samples;

  // The life decay factor is the reciprocal of the life expectancy. Therefore,
  // values at or below 1 indicate anti-generational behaviour where the live
  // bytes either stays the same or grows from age to age. Conversely, values
  // above 1 indicate generational behaviour where the live bytes shrinks from
  // age to age. The more it shrinks from age to age, the higher the value.
  // Therefore, the higher this value is, the higher we want the tenuring
  // threshold to be, as we exponentially avoid promotions to the old generation.
  const double young_life_decay_factor = 1.0 / young_life_expectancy;

  // The young residency reciprocal indicates the inverse of how small the
  // resident part of the young generation is compared to the entire heap. Values
  // below 1 indicate it is relatively big. Conversely, values above 1 indicate
  // it is relatively small.
  const double young_residency_reciprocal = double(soft_max_capacity) / double(young_live_total);

  // The old residency factor clamps the old residency reciprocal to
  // at least 1. That implies this factor is 1 unless the resident memory of
  // the old generation is small compared to the residency of the heap. The
  // smaller the old generation is, the higher this value is. The reasoning
  // is that the less memory that is resident in the old generation, the less
  // point there is in promoting objects to the old generation, as the amount
  // of work it removes from the young generation collections becomes less
  // and less valuable, the smaller the old generation is.
  const double young_residency_factor = MAX2(young_residency_reciprocal, 1.0);

  // The allocated to garbage ratio, compares the ratio of newly allocated
  // memory since GC started to how much garbage we are freeing up. The higher
  // the value, the harder it is for the YC to keep up with the allocation rate.
  const double allocated_garbage_ratio = double(young_allocated) / double(young_garbage + 1);

  // We slow down the young residency factor with a log. A larger log slows
  // it down faster. We select a log between 2 - 16 scaled by the allocated
  // to garbage factor. This selects a larger log when the GC has a harder
  // time keeping up, which causes more promotions to the old generation,
  // making the young collections faster so they can catch up.
  const double young_log = MAX2(MIN2(allocated_garbage_ratio, 1.0) * 16, 2.0);

  // The young log residency is essentially the young residency factor, but slowed
  // down by the log_{young_log}(X) function described above.
  const double young_log_residency = log(young_residency_factor) / log(young_log);

  // The tenuring threshold is computed as the young life decay factor times
  // the young residency factor. That takes into consideration that the
  // value should be higher the more generational the age table is, and higher
  // the more insignificant the footprint of young resident memory is, yet breaks
  // if the GC is finding it hard to keep up with the allocation rate.
  const double tenuring_threshold_raw = young_life_decay_factor * young_log_residency;

  log_trace(gc, reloc)("Young Allocated: " SIZE_FORMAT "M", young_allocated / M);
  log_trace(gc, reloc)("Young Garbage: " SIZE_FORMAT "M", young_garbage / M);
  log_debug(gc, reloc)("Allocated To Garbage: %.1f", allocated_garbage_ratio);
  log_trace(gc, reloc)("Young Log: %.1f", young_log);
  log_trace(gc, reloc)("Young Residency Reciprocal: %.1f", young_residency_reciprocal);
  log_trace(gc, reloc)("Young Residency Factor: %.1f", young_residency_factor);
  log_debug(gc, reloc)("Young Log Residency: %.1f", young_log_residency);
  log_debug(gc, reloc)("Life Decay Factor: %.1f", young_life_decay_factor);

  // Round to an integer as we can't have non-integral tenuring threshold.
  const uint upper_bound = MIN2(last_populated_age + 1u, (uint)MaxTenuringThreshold);
  const uint lower_bound = MIN2(1u, upper_bound);
  const uint tenuring_threshold = clamp((uint)round(tenuring_threshold_raw), lower_bound, upper_bound);

  return tenuring_threshold;
}

void ZGenerationYoung::concurrent_select_relocation_set() {
  ZStatTimerYoung timer(ZPhaseConcurrentSelectRelocationSetYoung);
  const bool promote_all = type() == ZYoungType::major_full_preclean;
  select_relocation_set(_id, promote_all);
}

class VM_ZRelocateStartYoung : public VM_ZYoungOperation {

public:
  virtual VMOp_Type type() const {
    return VMOp_ZRelocateStartYoung;
  }

  virtual bool block_jni_critical() const {
    return true;
  }

  virtual bool do_operation() {
    ZStatTimerYoung timer(ZPhasePauseRelocateStartYoung);
    ZServiceabilityPauseTracer tracer;

    ZGeneration::young()->relocate_start();

    return true;
  }
};

void ZGenerationYoung::pause_relocate_start() {
  VM_ZRelocateStartYoung().pause();
}

void ZGenerationYoung::concurrent_relocate() {
  ZStatTimerYoung timer(ZPhaseConcurrentRelocatedYoung);
  relocate();
}

void ZGenerationYoung::mark_start() {
  assert(SafepointSynchronize::is_at_safepoint(), "Should be at safepoint");

  // Change good colors
  flip_mark_start();

  // Retire allocating pages
  ZAllocator::eden()->retire_pages();
  for (ZPageAge i = ZPageAge::survivor1; i <= ZPageAge::survivor14; i = static_cast<ZPageAge>(static_cast<uint>(i) + 1)) {
    ZAllocator::relocation(i)->retire_pages();
  }

  // Reset allocated/reclaimed/used statistics
  reset_statistics();

  // Increment sequence number
  _seqnum++;

  // Enter mark phase
  set_phase(Phase::Mark);

  // Reset marking information
  _mark.start();

  // Flip remembered set bits
  _remembered.flip();

  // Update statistics
  stat_heap()->at_mark_start(_page_allocator->stats(this));
}

void ZGenerationYoung::mark_roots() {
  ZStatTimerYoung timer(ZSubPhaseConcurrentMarkRootsYoung);
  _mark.mark_young_roots();
}

void ZGenerationYoung::mark_follow() {
  // Combine following with scanning the remembered set
  ZStatTimerYoung timer(ZSubPhaseConcurrentMarkFollowYoung);
  _remembered.scan_and_follow(&_mark);
}

bool ZGenerationYoung::mark_end() {
  assert(SafepointSynchronize::is_at_safepoint(), "Should be at safepoint");

  // End marking
  if (!_mark.end()) {
    // Marking not completed, continue concurrent mark
    return false;
  }

  // Enter mark completed phase
  set_phase(Phase::MarkComplete);

  // Update statistics
  stat_heap()->at_mark_end(_page_allocator->stats(this));

  // Notify JVMTI that some tagmap entry objects may have died.
  JvmtiTagMap::set_needs_cleaning();

  return true;
}

void ZGenerationYoung::relocate_start() {
  assert(SafepointSynchronize::is_at_safepoint(), "Should be at safepoint");

  // Change good colors
  flip_relocate_start();

  // Enter relocate phase
  set_phase(Phase::Relocate);

  // Update statistics
  stat_heap()->at_relocate_start(_page_allocator->stats(this));

  _relocate.start();
}

void ZGenerationYoung::relocate() {
  // Relocate relocation set
  _relocate.relocate(&_relocation_set);

  // Update statistics
  stat_heap()->at_relocate_end(_page_allocator->stats(this), should_record_stats());
}

void ZGenerationYoung::flip_promote(ZPage* from_page, ZPage* to_page) {
  _page_table->replace(from_page, to_page);

  // Update statistics
  _page_allocator->promote_used(from_page->size());
  increase_freed(from_page->size());
  increase_promoted(from_page->live_bytes());
}

void ZGenerationYoung::in_place_relocate_promote(ZPage* from_page, ZPage* to_page) {
  _page_table->replace(from_page, to_page);

  // Update statistics
  _page_allocator->promote_used(from_page->size());
}

void ZGenerationYoung::register_flip_promoted(const ZArray<ZPage*>& pages) {
  _relocation_set.register_flip_promoted(pages);
}

void ZGenerationYoung::register_in_place_relocate_promoted(ZPage* page) {
  _relocation_set.register_in_place_relocate_promoted(page);
}

void ZGenerationYoung::register_with_remset(ZPage* page) {
  _remembered.register_found_old(page);
}

ZGenerationTracer* ZGenerationYoung::jfr_tracer() {
  return &_jfr_tracer;
}

ZGenerationOld::ZGenerationOld(ZPageTable* page_table, ZPageAllocator* page_allocator)
  : ZGeneration(ZGenerationId::old, page_table, page_allocator),
    _reference_processor(&_workers),
    _weak_roots_processor(&_workers),
    _unload(&_workers),
    _total_collections_at_start(0),
    _young_seqnum_at_reloc_start(0),
    _jfr_tracer() {
  ZGeneration::_old = this;
}

class ZGenerationCollectionScopeOld : public StackObj {
private:
  ZStatTimer      _stat_timer;
  ZDriverUnlocker _unlocker;

public:
  ZGenerationCollectionScopeOld(ConcurrentGCTimer* gc_timer)
    : _stat_timer(ZPhaseGenerationOld, gc_timer),
      _unlocker() {
    // Update statistics and set the GC timer
    ZGeneration::old()->at_collection_start(gc_timer);
  }

  ~ZGenerationCollectionScopeOld() {
    // Update statistics and clear the GC timer
    ZGeneration::old()->at_collection_end();
  }
};

bool ZGenerationOld::should_record_stats() {
  return true;
}

void ZGenerationOld::collect(ConcurrentGCTimer* timer) {
  ZGenerationCollectionScopeOld scope(timer);

  // Phase 1: Concurrent Mark
  concurrent_mark();

  abortpoint();

  // Phase 2: Pause Mark End
  while (!pause_mark_end()) {
    // Phase 2.5: Concurrent Mark Continue
    concurrent_mark_continue();

    abortpoint();
  }

  // Phase 3: Concurrent Mark Free
  concurrent_mark_free();

  abortpoint();

  // Phase 4: Concurrent Process Non-Strong References
  concurrent_process_non_strong_references();

  abortpoint();

  // Phase 5: Concurrent Reset Relocation Set
  concurrent_reset_relocation_set();

  abortpoint();

  // Phase 6: Pause Verify
  pause_verify();

  // Phase 7: Concurrent Select Relocation Set
  concurrent_select_relocation_set();

  abortpoint();

  {
    ZDriverLocker locker;

    // Phase 8: Concurrent Remap Roots
    concurrent_remap_young_roots();

    abortpoint();

    // Phase 9: Pause Relocate Start
    pause_relocate_start();
  }

  // Note that we can't have an abortpoint here. We need
  // to let concurrent_relocate() call abort_page()
  // on the remaining entries in the relocation set.

  // Phase 10: Concurrent Relocate
  concurrent_relocate();
}

void ZGenerationOld::flip_mark_start() {
  ZGlobalsPointers::flip_old_mark_start();
  ZBarrierSet::assembler()->patch_barriers();
  ZVerify::on_color_flip();
}

void ZGenerationOld::flip_relocate_start() {
  ZGlobalsPointers::flip_old_relocate_start();
  ZBarrierSet::assembler()->patch_barriers();
  ZVerify::on_color_flip();
}

void ZGenerationOld::concurrent_mark() {
  ZStatTimerOld timer(ZPhaseConcurrentMarkOld);
  ZBreakpoint::at_after_marking_started();
  mark_roots();
  mark_follow();
  ZBreakpoint::at_before_marking_completed();
}

class VM_ZMarkEndOld : public VM_ZOperation {
public:
  VM_ZMarkEndOld()
    : VM_ZOperation(ZDriver::major()->gc_cause()) {}

  virtual VMOp_Type type() const {
    return VMOp_ZMarkEndOld;
  }

  virtual bool do_operation() {
    ZStatTimerOld timer(ZPhasePauseMarkEndOld);
    ZServiceabilityPauseTracer tracer;

    return ZGeneration::old()->mark_end();
  }
};

bool ZGenerationOld::pause_mark_end() {
  return VM_ZMarkEndOld().pause();
}

void ZGenerationOld::concurrent_mark_continue() {
  ZStatTimerOld timer(ZPhaseConcurrentMarkContinueOld);
  mark_follow();
}

void ZGenerationOld::concurrent_mark_free() {
  ZStatTimerOld timer(ZPhaseConcurrentMarkFreeOld);
  mark_free();
}

void ZGenerationOld::concurrent_process_non_strong_references() {
  ZStatTimerOld timer(ZPhaseConcurrentProcessNonStrongOld);
  ZBreakpoint::at_after_reference_processing_started();
  process_non_strong_references();
}

void ZGenerationOld::concurrent_reset_relocation_set() {
  ZStatTimerOld timer(ZPhaseConcurrentResetRelocationSetOld);
  reset_relocation_set();
}

class VM_ZVerifyOld : public VM_Operation {
public:
  virtual VMOp_Type type() const {
    return VMOp_ZVerifyOld;
  }

  virtual bool skip_thread_oop_barriers() const {
    return true;
  }

  virtual void doit() {
    ZVerify::after_weak_processing();
  }

  void pause() {
    VMThread::execute(this);
  }
};

void ZGenerationOld::pause_verify() {
  // Note that we block out concurrent young collections when performing the
  // verification. The verification checks that store good oops in the
  // old generation have a corresponding remembered set entry, or is in
  // a store barrier buffer (hence asynchronously creating such entries).
  // That lookup would otherwise race with installation of base pointers
  // into the store barrier buffer. We dodge that race by blocking out
  // young collections during this verification.
  if (ZVerifyRoots || ZVerifyObjects) {
    // Limited verification
    ZDriverLocker locker;
    VM_ZVerifyOld().pause();
  }
}

void ZGenerationOld::concurrent_select_relocation_set() {
  ZStatTimerOld timer(ZPhaseConcurrentSelectRelocationSetOld);
  select_relocation_set(_id, false /* promote_all */);
}

class VM_ZRelocateStartOld : public VM_ZOperation {
public:
  VM_ZRelocateStartOld()
    : VM_ZOperation(ZDriver::major()->gc_cause()) {}

  virtual VMOp_Type type() const {
    return VMOp_ZRelocateStartOld;
  }

  virtual bool block_jni_critical() const {
    return true;
  }

  virtual bool do_operation() {
    ZStatTimerOld timer(ZPhasePauseRelocateStartOld);
    ZServiceabilityPauseTracer tracer;

    ZGeneration::old()->relocate_start();

    return true;
  }
};

void ZGenerationOld::pause_relocate_start() {
  VM_ZRelocateStartOld().pause();
}

void ZGenerationOld::concurrent_relocate() {
  ZStatTimerOld timer(ZPhaseConcurrentRelocatedOld);
  relocate();
}

void ZGenerationOld::concurrent_remap_young_roots() {
  ZStatTimerOld timer(ZPhaseConcurrentRemapRootsOld);
  remap_young_roots();
}

void ZGenerationOld::mark_start() {
  assert(SafepointSynchronize::is_at_safepoint(), "Should be at safepoint");

  // Verification
  ClassLoaderDataGraph::verify_claimed_marks_cleared(ClassLoaderData::_claim_strong);

  // Change good colors
  flip_mark_start();

  // Retire allocating pages
  ZAllocator::old()->retire_pages();

  // Reset allocated/reclaimed/used statistics
  reset_statistics();

  // Reset encountered/dropped/enqueued statistics
  _reference_processor.reset_statistics();

  // Increment sequence number
  _seqnum++;

  // Enter mark phase
  set_phase(Phase::Mark);

  // Reset marking information
  _mark.start();

  // Update statistics
  stat_heap()->at_mark_start(_page_allocator->stats(this));

  // Note that we start a marking cycle.
  // Unlike other GCs, the color switch implicitly changes the nmethods
  // to be armed, and the thread-local disarm values are lazily updated
  // when JavaThreads wake up from safepoints.
  CodeCache::on_gc_marking_cycle_start();

  _total_collections_at_start = ZCollectedHeap::heap()->total_collections();
}

void ZGenerationOld::mark_roots() {
  ZStatTimerOld timer(ZSubPhaseConcurrentMarkRootsOld);
  _mark.mark_old_roots();
}

void ZGenerationOld::mark_follow() {
  ZStatTimerOld timer(ZSubPhaseConcurrentMarkFollowOld);
  _mark.mark_follow();
}

bool ZGenerationOld::mark_end() {
  assert(SafepointSynchronize::is_at_safepoint(), "Should be at safepoint");

  // Try end marking
  if (!_mark.end()) {
    // Marking not completed, continue concurrent mark
    return false;
  }

  // Enter mark completed phase
  set_phase(Phase::MarkComplete);

  // Verify after mark
  ZVerify::after_mark();

  // Update statistics
  stat_heap()->at_mark_end(_page_allocator->stats(this));

  // Block resurrection of weak/phantom references
  ZResurrection::block();

  // Prepare to unload stale metadata and nmethods
  _unload.prepare();

  // Notify JVMTI that some tagmap entry objects may have died.
  JvmtiTagMap::set_needs_cleaning();

  // Note that we finished a marking cycle.
  // Unlike other GCs, we do not arm the nmethods
  // when marking terminates.
  CodeCache::on_gc_marking_cycle_finish();

  return true;
}

void ZGenerationOld::set_soft_reference_policy(bool clear) {
  _reference_processor.set_soft_reference_policy(clear);
}

class ZRendezvousHandshakeClosure : public HandshakeClosure {
public:
  ZRendezvousHandshakeClosure()
    : HandshakeClosure("ZRendezvous") {}

  void do_thread(Thread* thread) {
    // Does nothing
  }
};

class ZRendezvousGCThreads: public VM_Operation {
 public:
  VMOp_Type type() const { return VMOp_ZRendezvousGCThreads; }

  virtual bool evaluate_at_safepoint() const {
    // We only care about synchronizing the GC threads.
    // Leave the Java threads running.
    return false;
  }

  virtual bool skip_thread_oop_barriers() const {
    fatal("Concurrent VMOps should not call this");
    return true;
  }

  void doit() {
    // Light weight "handshake" of the GC threads
    SuspendibleThreadSet::synchronize();
    SuspendibleThreadSet::desynchronize();
  };
};


void ZGenerationOld::process_non_strong_references() {
  // Process Soft/Weak/Final/PhantomReferences
  _reference_processor.process_references();

  // Process weak roots
  _weak_roots_processor.process_weak_roots();

  ClassUnloadingContext ctx(_workers.active_workers(),
                            true /* unregister_nmethods_during_purge */,
                            true /* lock_nmethod_free_separately */);

  // Unlink stale metadata and nmethods
  _unload.unlink();

  // Perform a handshake. This is needed 1) to make sure that stale
  // metadata and nmethods are no longer observable. And 2), to
  // prevent the race where a mutator first loads an oop, which is
  // logically null but not yet cleared. Then this oop gets cleared
  // by the reference processor and resurrection is unblocked. At
  // this point the mutator could see the unblocked state and pass
  // this invalid oop through the normal barrier path, which would
  // incorrectly try to mark the oop.
  ZRendezvousHandshakeClosure cl;
  Handshake::execute(&cl);

  // GC threads are not part of the handshake above.
  // Explicitly "handshake" them.
  ZRendezvousGCThreads op;
  VMThread::execute(&op);

  // Unblock resurrection of weak/phantom references
  ZResurrection::unblock();

  // Purge stale metadata and nmethods that were unlinked
  _unload.purge();

  // Enqueue Soft/Weak/Final/PhantomReferences. Note that this
  // must be done after unblocking resurrection. Otherwise the
  // Finalizer thread could call Reference.get() on the Finalizers
  // that were just enqueued, which would incorrectly return null
  // during the resurrection block window, since such referents
  // are only Finalizable marked.
  _reference_processor.enqueue_references();

  // Clear old markings claim bits.
  // Note: Clearing _claim_strong also clears _claim_finalizable.
  ClassLoaderDataGraph::clear_claimed_marks(ClassLoaderData::_claim_strong);
}

void ZGenerationOld::relocate_start() {
  assert(SafepointSynchronize::is_at_safepoint(), "Should be at safepoint");

  // Finish unloading stale metadata and nmethods
  _unload.finish();

  // Change good colors
  flip_relocate_start();

  // Enter relocate phase
  set_phase(Phase::Relocate);

  // Update statistics
  stat_heap()->at_relocate_start(_page_allocator->stats(this));

  // Need to know the remset parity when relocating objects
  _young_seqnum_at_reloc_start = ZGeneration::young()->seqnum();

  _relocate.start();
}

void ZGenerationOld::relocate() {
  // Relocate relocation set
  _relocate.relocate(&_relocation_set);

  // Update statistics
  stat_heap()->at_relocate_end(_page_allocator->stats(this), should_record_stats());
}

class ZRemapOopClosure : public OopClosure {
public:
  virtual void do_oop(oop* p) {
    ZBarrier::load_barrier_on_oop_field((volatile zpointer*)p);
  }

  virtual void do_oop(narrowOop* p) {
    ShouldNotReachHere();
  }
};

class ZRemapThreadClosure : public ThreadClosure {
public:
  virtual void do_thread(Thread* thread) {
    JavaThread* const jt = JavaThread::cast(thread);
    StackWatermarkSet::finish_processing(jt, nullptr, StackWatermarkKind::gc);
  }
};

class ZRemapNMethodClosure : public NMethodClosure {
private:
  ZBarrierSetNMethod* const _bs_nm;

public:
  ZRemapNMethodClosure()
    : _bs_nm(static_cast<ZBarrierSetNMethod*>(BarrierSet::barrier_set()->barrier_set_nmethod())) {}

  virtual void do_nmethod(nmethod* nm) {
    ZLocker<ZReentrantLock> locker(ZNMethod::lock_for_nmethod(nm));
    if (_bs_nm->is_armed(nm)) {
      // Heal barriers
      ZNMethod::nmethod_patch_barriers(nm);

      // Heal oops
      ZUncoloredRootProcessOopClosure cl(ZNMethod::color(nm));
      ZNMethod::nmethod_oops_do_inner(nm, &cl);

      log_trace(gc, nmethod)("nmethod: " PTR_FORMAT " visited by old remapping", p2i(nm));

      // Disarm
      _bs_nm->disarm(nm);
    }
  }
};

typedef ClaimingCLDToOopClosure<ClassLoaderData::_claim_none> ZRemapCLDClosure;

class ZRemapYoungRootsTask : public ZTask {
private:
  ZGenerationPagesParallelIterator _old_pages_parallel_iterator;

  ZRootsIteratorAllColored         _roots_colored;
  ZRootsIteratorAllUncolored       _roots_uncolored;

  ZRemapOopClosure                 _cl_colored;
  ZRemapCLDClosure                 _cld_cl;

  ZRemapThreadClosure              _thread_cl;
  ZRemapNMethodClosure             _nm_cl;

public:
  ZRemapYoungRootsTask(ZPageTable* page_table, ZPageAllocator* page_allocator)
    : ZTask("ZRemapYoungRootsTask"),
      _old_pages_parallel_iterator(page_table, ZGenerationId::old, page_allocator),
      _roots_colored(ZGenerationIdOptional::old),
      _roots_uncolored(ZGenerationIdOptional::old),
      _cl_colored(),
      _cld_cl(&_cl_colored),
      _thread_cl(),
      _nm_cl() {}

  virtual void work() {
    {
      ZStatTimerWorker timer(ZSubPhaseConcurrentRemapRootsColoredOld);
      _roots_colored.apply(&_cl_colored,
                           &_cld_cl);
    }

    {
      ZStatTimerWorker timer(ZSubPhaseConcurrentRemapRootsUncoloredOld);
      _roots_uncolored.apply(&_thread_cl,
                             &_nm_cl);
    }

    {
      ZStatTimerWorker timer(ZSubPhaseConcurrentRemapRememberedOld);
      _old_pages_parallel_iterator.do_pages([&](ZPage* page) {
        // Visit all object fields that potentially pointing into young generation
        page->oops_do_current_remembered(ZBarrier::load_barrier_on_oop_field);
        return true;
      });
    }
  }
};

// This function is used by the old generation to purge roots to the young generation from
// young remap bit errors, before the old generation performs old relocate start. By doing
// that, we can know that double remap bit errors don't need to be concerned with double
// remap bit errors, in the young generation roots. That makes it possible to figure out
// which generation table to use when remapping a pointer, without needing an extra adjust
// phase that walks the entire heap.
void ZGenerationOld::remap_young_roots() {
  // We upgrade the number of workers to the number last used by the young generation. The
  // reason is that this code is run under the driver lock, which means that a young generation
  // collection might be waiting for this code to complete.
  uint prev_nworkers = _workers.active_workers();
  uint remap_nworkers = clamp(ZGeneration::young()->workers()->active_workers() + prev_nworkers, 1u, ZOldGCThreads);
  _workers.set_active_workers(remap_nworkers);

  // TODO: The STS joiner is only needed to satisfy ZBarrier::assert_is_state_barrier_safe that doesn't
  // understand the driver locker. Consider making the assert aware of the driver locker.
  SuspendibleThreadSetJoiner sts_joiner;

  ZRemapYoungRootsTask task(_page_table, _page_allocator);
  workers()->run(&task);
  _workers.set_active_workers(prev_nworkers);
}

uint ZGenerationOld::total_collections_at_start() const {
  return _total_collections_at_start;
}

ZGenerationTracer* ZGenerationOld::jfr_tracer() {
  return &_jfr_tracer;
}
