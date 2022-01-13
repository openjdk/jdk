/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
#include "code/nmethod.hpp"
#include "classfile/classLoaderDataGraph.hpp"
#include "gc/shared/gcLocker.hpp"
#include "gc/shared/isGCActiveMark.hpp"
#include "gc/shared/gcVMOperations.hpp"
#include "gc/shared/suspendibleThreadSet.hpp"
#include "gc/z/zAllocator.inline.hpp"
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
#include "logging/log.hpp"
#include "memory/universe.hpp"
#include "prims/jvmtiTagMap.hpp"
#include "runtime/atomic.hpp"
#include "runtime/handshake.hpp"
#include "runtime/safepoint.hpp"
#include "runtime/vmOperations.hpp"
#include "runtime/vmThread.hpp"
#include "utilities/debug.hpp"
#include "utilities/events.hpp"

static const ZStatPhaseGeneration ZPhaseGenerationYoung[] {
  ZStatPhaseGeneration("Young: Generation Collection (Minor)", ZGenerationId::young),
  ZStatPhaseGeneration("Young: Generation Collection (Major Preclean)", ZGenerationId::young),
  ZStatPhaseGeneration("Young: Generation Collection (Major Roots)", ZGenerationId::young)
};

static const ZStatPhaseGeneration ZPhaseGenerationOld("Old: Generation Collection (Major)", ZGenerationId::old);

static const ZStatPhasePause      ZPhasePauseMarkStartYoung("Young: Pause Mark Start");
static const ZStatPhasePause      ZPhasePauseMarkStartYoungAndOld("Young + Old: Pause Mark Start");
static const ZStatPhaseConcurrent ZPhaseConcurrentMarkYoung("Young: Concurrent Mark");
static const ZStatPhaseConcurrent ZPhaseConcurrentMarkContinueYoung("Young: Concurrent Mark Continue");
static const ZStatPhasePause      ZPhasePauseMarkEndYoung("Young: Pause Mark End");
static const ZStatPhaseConcurrent ZPhaseConcurrentMarkFreeYoung("Young: Concurrent Mark Free");
static const ZStatPhaseConcurrent ZPhaseConcurrentResetRelocationSetYoung("Young: Concurrent Reset Relocation Set");
static const ZStatPhaseConcurrent ZPhaseConcurrentSelectRelocationSetYoung("Young: Concurrent Select Relocation Set");
static const ZStatPhasePause      ZPhasePauseRelocateStartYoung("Young: Pause Relocate Start");
static const ZStatPhaseConcurrent ZPhaseConcurrentRelocatedYoung("Young: Concurrent Relocate");

static const ZStatPhaseConcurrent ZPhaseConcurrentMarkOld("Old: Concurrent Mark");
static const ZStatPhaseConcurrent ZPhaseConcurrentMarkContinueOld("Old: Concurrent Mark Continue");
static const ZStatPhasePause      ZPhasePauseMarkEndOld("Old: Pause Mark End");
static const ZStatPhaseConcurrent ZPhaseConcurrentMarkFreeOld("Old: Concurrent Mark Free");
static const ZStatPhaseConcurrent ZPhaseConcurrentProcessNonStrongOld("Old: Concurrent Process Non-Strong");
static const ZStatPhaseConcurrent ZPhaseConcurrentResetRelocationSetOld("Old: Concurrent Reset Relocation Set");
static const ZStatPhaseConcurrent ZPhaseConcurrentSelectRelocationSetOld("Old: Concurrent Select Relocation Set");
static const ZStatPhasePause      ZPhasePauseRelocateStartOld("Old: Pause Relocate Start");
static const ZStatPhaseConcurrent ZPhaseConcurrentRelocatedOld("Old: Concurrent Relocate");
static const ZStatPhaseConcurrent ZPhaseConcurrentRemapRootsOld("Old: Concurrent Remap Roots");

static const ZStatSubPhase ZSubPhaseConcurrentMarkRootsYoung("Young: Concurrent Mark Roots");
static const ZStatSubPhase ZSubPhaseConcurrentMarkFollowYoung("Young: Concurrent Mark Follow");
static const ZStatSubPhase ZSubPhaseConcurrentMarkRememberedSetYoung("Young: Concurrent Mark Remset");

static const ZStatSubPhase ZSubPhaseConcurrentMarkRootsOld("Old: Concurrent Mark Roots");
static const ZStatSubPhase ZSubPhaseConcurrentMarkFollowOld("Old: Concurrent Mark Follow");
static const ZStatSubPhase ZSubPhaseConcurrentRemapRootsColoredOld("Old: Concurrent Remap Roots Colored");
static const ZStatSubPhase ZSubPhaseConcurrentRemapRootsUncoloredOld("Old: Concurrent Remap Roots Uncolored");

static const ZStatSampler         ZSamplerJavaThreads("System", "Java Threads", ZStatUnitThreads);

ZGenerationYoung* ZGeneration::_young;
ZGenerationOld*   ZGeneration::_old;

ZGeneration::ZGeneration(ZGenerationId id, ZPageTable* page_table, ZPageAllocator* page_allocator) :
    _id(id),
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
    _gc_timer(NULL) {
}

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
    size_t freed = ZHeap::heap()->free_pages(selector->empty_pages());
    increase_freed(freed);
    selector->clear_empty_pages();
  }
}

void ZGeneration::flip_age_pages(const ZRelocationSetSelector* selector) {
  if (is_young()) {
    const bool promote_all = selector->promote_all();
    _relocate.flip_age_pages(selector->not_selected_small(), promote_all);
    _relocate.flip_age_pages(selector->not_selected_medium(), promote_all);
    _relocate.flip_age_pages(selector->not_selected_large(), promote_all);
  }
}

void ZGeneration::select_relocation_set(bool promote_all) {
  // Register relocatable pages with selector
  ZRelocationSetSelector selector(promote_all);
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
  assert(_gc_timer == NULL, "Incorrect scoping");
  _gc_timer = gc_timer;
}

void ZGeneration::clear_gc_timer() {
  assert(_gc_timer != NULL, "Incorrect scoping");
  _gc_timer = NULL;
}

void ZGeneration::log_phase_switch(Phase from, Phase to) {
  const char* str[] = {
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
  stat_cycle()->at_end(stat_workers());
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
  const uint _gc_id;
  bool       _success;

public:
  VM_ZOperation() :
      _gc_id(GCId::current()),
      _success(false) {}

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
    IsGCActiveMark gc_active_mark;

    // Verify before operation
    ZVerify::before_zoperation();

    // Execute operation
    _success = do_operation();

    // Update statistics
    ZStatSample(ZSamplerJavaThreads, Threads::number_of_threads());
  }

  virtual void doit_epilogue() {
    Heap_lock->unlock();
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

ZGenerationYoung::ZGenerationYoung(ZPageTable* page_table, ZPageAllocator* page_allocator) :
    ZGeneration(ZGenerationId::young, page_table, page_allocator),
    _active_type(ZYoungType::none),
    _remembered(page_table, page_allocator) {
  ZGeneration::_young = this;
}

class ZGenerationCollectionScopeYoung : public StackObj {
private:
  ZYoungTypeSetter _type_setter;
  ZStatTimer       _stat_timer;

public:
  ZGenerationCollectionScopeYoung(ZYoungType type, ConcurrentGCTimer* gc_timer) :
      _type_setter(type),
      _stat_timer(ZPhaseGenerationYoung[(int)type], gc_timer) {
    // Update statistics and set the GC timer
    ZGeneration::young()->at_collection_start(gc_timer);
  }

  ~ZGenerationCollectionScopeYoung() {
    // Update statistics and clear the GC timer
    ZGeneration::young()->at_collection_end();
  }
};

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

class VM_ZMarkStartYoung : public VM_ZOperation {
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


void ZGenerationYoung::pause_mark_start() {
  if (type() == ZYoungType::major_roots) {
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

class VM_ZMarkEndYoung : public VM_ZOperation {
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

void ZGenerationYoung::concurrent_select_relocation_set() {
  ZStatTimerYoung timer(ZPhaseConcurrentSelectRelocationSetYoung);
  const bool promote_all = type() == ZYoungType::major_preclean;
  select_relocation_set(promote_all);
}

class VM_ZRelocateStartYoung : public VM_ZOperation {
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

  // Flip address view
  ZGlobalsPointers::flip_young_mark_start();

  // Retire allocating pages
  ZAllocator::eden()->retire_pages();
  ZAllocator::survivor()->retire_pages();

  // Reset allocated/reclaimed/used statistics
  reset_statistics();

  // Increment sequence number
  _seqnum++;

  // Enter mark phase
  set_phase(Phase::Mark);

  // Reset marking information and mark roots
  _mark.start();

  // Flip remembered set bits
  flip_remembered_sets();

  // Update statistics
  stat_heap()->at_mark_start(_page_allocator->stats(this));
}

void ZGenerationYoung::mark_roots() {
  ZStatTimerYoung timer(ZSubPhaseConcurrentMarkRootsYoung);
  _mark.mark_roots();
}

void ZGenerationYoung::mark_follow() {
  ZStatTimerYoung timer(ZSubPhaseConcurrentMarkFollowYoung);
  _mark.mark_follow();
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

  // Flip address view
  ZGlobalsPointers::flip_young_relocate_start();

  // Enter relocate phase
  set_phase(Phase::Relocate);

  // Update statistics
  stat_heap()->at_relocate_start(_page_allocator->stats(this));

  // Notify JVMTI
  JvmtiTagMap::set_needs_rehashing();

  _relocate.start();
}

void ZGenerationYoung::relocate() {
  // Relocate relocation set
  _relocate.relocate(&_relocation_set);

  // Update statistics
  stat_heap()->at_relocate_end(_page_allocator->stats(this));
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

void ZGenerationYoung::scan_remembered_sets() {
  ZStatTimerYoung timer(ZSubPhaseConcurrentMarkRememberedSetYoung);
  _remembered.scan();
}

void ZGenerationYoung::flip_remembered_sets() {
  _remembered.flip();
}

ZGenerationOld::ZGenerationOld(ZPageTable* page_table, ZPageAllocator* page_allocator) :
  ZGeneration(ZGenerationId::old, page_table, page_allocator),
  _reference_processor(&_workers),
  _weak_roots_processor(&_workers),
  _unload(&_workers),
  _total_collections_at_end(0) {
  ZGeneration::_old = this;
}

class ZGenerationCollectionScopeOld : public StackObj {
private:
  ZStatTimer      _stat_timer;
  ZDriverUnlocker _unlocker;

public:
  ZGenerationCollectionScopeOld(ConcurrentGCTimer* gc_timer) :
      _stat_timer(ZPhaseGenerationOld, gc_timer),
      _unlocker() {
    // Update statistics and set the GC timer
    ZGeneration::old()->at_collection_start(gc_timer);
  }

  ~ZGenerationCollectionScopeOld() {
    // Update statistics and clear the GC timer
    ZGeneration::old()->at_collection_end();
  }
};

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
    concurrent_remap_roots();

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

void ZGenerationOld::concurrent_mark() {
  ZStatTimerOld timer(ZPhaseConcurrentMarkOld);
  ZBreakpoint::at_after_marking_started();
  mark_roots();
  mark_follow();
  ZBreakpoint::at_before_marking_completed();
}

class VM_ZMarkEndOld : public VM_ZOperation {
public:
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
  ZDriverLocker locker;
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
  select_relocation_set(false /* promote_all */);
}

class VM_ZRelocateStartOld : public VM_ZOperation {
public:
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

void ZGenerationOld::concurrent_remap_roots() {
  ZStatTimerOld timer(ZPhaseConcurrentRemapRootsOld);
  remap_roots();
}

void ZGenerationOld::mark_start() {
  assert(SafepointSynchronize::is_at_safepoint(), "Should be at safepoint");

  // Verification
  ClassLoaderDataGraph::verify_claimed_marks_not(ClassLoaderData::_claim_strong);

  // Flip address view
  ZGlobalsPointers::flip_old_mark_start();

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

  // Reset marking information and mark roots
  _mark.start();

  // Update statistics
  stat_heap()->at_mark_start(_page_allocator->stats(this));
}

void ZGenerationOld::mark_roots() {
  ZStatTimerOld timer(ZSubPhaseConcurrentMarkRootsOld);
  _mark.mark_roots();
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

  return true;
}

void ZGenerationOld::set_soft_reference_policy(bool clear) {
  _reference_processor.set_soft_reference_policy(clear);
}

class ZRendezvousClosure : public HandshakeClosure {
public:
  ZRendezvousClosure() :
    HandshakeClosure("ZRendezvous") {}

  void do_thread(Thread* thread) {
    // Does nothing
  }
};

void ZGenerationOld::process_non_strong_references() {
  // Process Soft/Weak/Final/PhantomReferences
  _reference_processor.process_references();

  // Process weak roots
  _weak_roots_processor.process_weak_roots();

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
  ZRendezvousClosure cl;
  Handshake::execute(&cl);

  VM_None op("Handshake GC threads");
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

  // Flip address view
  ZGlobalsPointers::flip_old_relocate_start();

  // Enter relocate phase
  set_phase(Phase::Relocate);

  // Update statistics
  stat_heap()->at_relocate_start(_page_allocator->stats(this));

  // Notify JVMTI
  JvmtiTagMap::set_needs_rehashing();

  _relocate.start();
}

void ZGenerationOld::relocate() {
  // Relocate relocation set
  _relocate.relocate(&_relocation_set);

  // Update statistics
  stat_heap()->at_relocate_end(_page_allocator->stats(this));
  _total_collections_at_end = ZCollectedHeap::heap()->total_collections();
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
    StackWatermarkSet::finish_processing(jt, NULL, StackWatermarkKind::gc);
  }
};

class ZRemapNMethodClosure : public NMethodClosure {
private:
  ZBarrierSetNMethod* const _bs_nm;

public:
  ZRemapNMethodClosure() :
      _bs_nm(static_cast<ZBarrierSetNMethod*>(BarrierSet::barrier_set()->barrier_set_nmethod())) {}

  virtual void do_nmethod(nmethod* nm) {
    ZLocker<ZReentrantLock> locker(ZNMethod::lock_for_nmethod(nm));
    if (!nm->is_alive()) {
      return;
    }

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

class ZRemapRootsTask : public ZTask {
private:
  ZRootsIteratorAllColored   _roots_colored;
  ZRootsIteratorAllUncolored _roots_uncolored;

  ZRemapOopClosure           _cl_colored;
  ZRemapCLDClosure           _cld_cl;

  ZRemapThreadClosure        _thread_cl;
  ZRemapNMethodClosure       _nm_cl;

public:
  ZRemapRootsTask() :
      ZTask("ZRemapRootsTask"),
      _roots_colored(),
      _roots_uncolored(),
      _cl_colored(),
      _cld_cl(&_cl_colored),
      _thread_cl(),
      _nm_cl() {
    // FIXME: Needed?
    ClassLoaderDataGraph_lock->lock();
  }

  ~ZRemapRootsTask() {
    ClassLoaderDataGraph_lock->unlock();
  }

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
  }
};

void ZGenerationOld::remap_remembered_sets() {
  ZGenerationPagesIterator iter(_page_table, ZGenerationId::old, _page_allocator);
  for (ZPage* page; iter.next(&page);) {
    // Visit all object fields that potentially pointing into young generation
    page->oops_do_current_remembered(ZBarrier::load_barrier_on_oop_field);
  }
}

void ZGenerationOld::remap_roots() {
  SuspendibleThreadSetJoiner sts_joiner;

  // Remap remembered sets
  remap_remembered_sets();

  sts_joiner.yield();

  ZRemapRootsTask task;
  workers()->run(&task);
}

int ZGenerationOld::total_collections_at_end() const {
  return _total_collections_at_end;
}
