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
#include "gc/shared/suspendibleThreadSet.hpp"
#include "gc/z/zBarrierSetNMethod.hpp"
#include "gc/z/zCollectedHeap.hpp"
#include "gc/z/zCollector.inline.hpp"
#include "gc/z/zForwarding.hpp"
#include "gc/z/zForwardingTable.inline.hpp"
#include "gc/z/zGeneration.inline.hpp"
#include "gc/z/zHeap.inline.hpp"
#include "gc/z/zMark.inline.hpp"
#include "gc/z/zPageAllocator.hpp"
#include "gc/z/zRelocationSet.inline.hpp"
#include "gc/z/zRelocationSetSelector.inline.hpp"
#include "gc/z/zRemember.hpp"
#include "gc/z/zRootsIterator.hpp"
#include "gc/z/zStat.hpp"
#include "gc/z/zTask.hpp"
#include "gc/z/zUncoloredRoot.inline.hpp"
#include "gc/z/zVerify.hpp"
#include "gc/z/zWorkers.hpp"
#include "prims/jvmtiTagMap.hpp"
#include "runtime/handshake.hpp"
#include "runtime/safepoint.hpp"
#include "runtime/vmOperations.hpp"
#include "runtime/vmThread.hpp"
#include "utilities/debug.hpp"
#include "utilities/events.hpp"

static const ZStatSubPhase ZSubPhaseConcurrentMinorMarkRoots("Concurrent Minor Mark Roots");
static const ZStatSubPhase ZSubPhaseConcurrentMinorMarkFollow("Concurrent Minor Mark Follow");

static const ZStatSubPhase ZSubPhaseConcurrentMajorMarkRoots("Concurrent Major Mark Roots");
static const ZStatSubPhase ZSubPhaseConcurrentMajorMarkFollow("Concurrent Major Mark Follow");
static const ZStatSubPhase ZSubPhaseConcurrentMajorRemapRootUncolored("Concurrent Major Remap Root Uncolored");

ZCollector::ZCollector(ZCollectorId id, const char* worker_prefix, ZPageTable* page_table, ZPageAllocator* page_allocator) :
    _id(id),
    _page_allocator(page_allocator),
    _page_table(page_table),
    _forwarding_table(),
    _workers(worker_prefix),
    _mark(this, page_table),
    _relocate(this),
    _relocation_set(this),
    _used_high(),
    _used_low(),
    _reclaimed(),
    _phase(Phase::Relocate),
    _seqnum(1),
    _stat_heap(),
    _stat_cycle(),
    _stat_mark() {
}

bool ZCollector::is_initialized() const {
  return _mark.is_initialized();
}

ZWorkers* ZCollector::workers() {
  return &_workers;
}

uint ZCollector::active_workers() const {
  return _workers.active_workers();
}

void ZCollector::set_active_workers(uint nworkers) {
  _workers.set_active_workers(nworkers);
}

void ZCollector::threads_do(ThreadClosure* tc) const {
  _workers.threads_do(tc);
}

void ZCollector::mark_flush_and_free(Thread* thread) {
  _mark.flush_and_free(thread);
}

void ZCollector::mark_free() {
   _mark.free();
}

void ZCollector::free_empty_pages(ZRelocationSetSelector* selector, int bulk) {
  // Freeing empty pages in bulk is an optimization to avoid grabbing
  // the page allocator lock, and trying to satisfy stalled allocations
  // too frequently.
  if (selector->should_free_empty_pages(bulk)) {
    ZHeap::heap()->free_pages(selector->empty_pages(), this);
    selector->clear_empty_pages();
  }
}

void ZCollector::promote_pages(ZRelocationSetSelector* selector) {
  if (is_minor()) {
    _relocate.promote_pages(selector->not_selected_small());
    _relocate.promote_pages(selector->not_selected_medium());
    _relocate.promote_pages(selector->not_selected_large());
  }
}

void ZCollector::select_relocation_set() {
  ZGenerationId collected_generation = ZHeap::heap()->generation(_id)->generation_id();

  // Register relocatable pages with selector
  ZRelocationSetSelector selector;
  {
    ZGenerationPagesIterator pt_iter(_page_table, collected_generation, _page_allocator);
    for (ZPage* page; pt_iter.next(&page);) {
      if (!page->is_relocatable()) {
        // Not relocatable, don't register
        // Note that the seqnum can change under our feet here as the page
        // can be concurrently freed and recycled by a concurrent ZCollector.
        // However this property is stable across such transitions. If it
        // was not relocatable before recycling, then it won't be relocatable
        // after it gets recycled either, as the seqnum atomically becomes
        // allocating for the given generation. The opposite property also
        // holds: if the page is relocatable, then it can't have been
        // concurrently freed; if it was re-allocated it would not be
        // relocatable, and if it was not re-allocated we know that it
        // was allocated earlier than mark start of the current ZCollector.
        continue;
      }

      if (page->is_marked()) {
        // Register live page
        selector.register_live_page(page);
      } else {
        // Register empty page
        selector.register_empty_page(page);

        // Reclaim empty pages in bulk
        free_empty_pages(&selector, 64 /* bulk */);
      }
    }

    // Reclaim remaining empty pages
    free_empty_pages(&selector, 0 /* bulk */);
  }

  // Select relocation set
  selector.select(collected_generation);

  // Install relocation set
  _relocation_set.install(&selector);

  promote_pages(&selector);

  // Setup forwarding table
  ZRelocationSetIterator rs_iter(&_relocation_set);
  for (ZForwarding* forwarding; rs_iter.next(&forwarding);) {
    _forwarding_table.insert(forwarding);
  }

  // Update statistics
  stat_relocation()->set_at_select_relocation_set(selector.stats());
  stat_heap()->set_at_select_relocation_set(selector.stats());
}

void ZCollector::reset_relocation_set() {
  // Reset forwarding table
  ZRelocationSetIterator iter(&_relocation_set);
  for (ZForwarding* forwarding; iter.next(&forwarding);) {
    _forwarding_table.remove(forwarding);
  }

  // Reset relocation set
  _relocation_set.reset();
}

void ZCollector::synchronize_relocation() {
  _relocate.synchronize();
}

void ZCollector::desynchronize_relocation() {
  _relocate.desynchronize();
}

void ZCollector::reset_statistics() {
  assert(SafepointSynchronize::is_at_safepoint(), "Should be at safepoint");
  _reclaimed = 0;
  _used_high = _used_low = _page_allocator->used();
}

size_t ZCollector::used_high() const {
  return _used_high;
}

size_t ZCollector::used_low() const {
  return _used_low;
}

ssize_t ZCollector::reclaimed() const {
  return _reclaimed;
}

void ZCollector::increase_reclaimed(size_t size) {
  _reclaimed += size;
}

void ZCollector::decrease_reclaimed(size_t size) {
  _reclaimed -= size;
}

void ZCollector::update_used(size_t used) {
  if (used > _used_high) {
    _used_high = used;
  }
  if (used < _used_low) {
    _used_low = used;
  }
}

ConcurrentGCTimer* ZCollector::timer() {
  return &_timer;
}

void ZCollector::log_phase_switch(Phase from, Phase to) {
  const char* str[] = {
    "Minor Mark Start",
    "Minor Mark End",
    "Minor Relocate Start",
    "Major Mark Start",
    "Major Mark End",
    "Major Relocate Start"
  };

  size_t index = 0;

  if (is_major()) {
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

void ZCollector::set_phase(Phase new_phase) {
#if 0
  const Phase old_phase = _phase;

  assert((new_phase == Phase::Mark && old_phase == Phase::Relocate) ||
         (new_phase == Phase::MarkComplete && old_phase == Phase::Mark) ||
         (new_phase == Phase::Relocate && old_phase == Phase::MarkComplete),
         "Invalid phase change");
#endif

  if (new_phase == Phase::Mark) {
    // Increment sequence number
    _seqnum++;
  }

  log_phase_switch(_phase, new_phase);

  _phase = new_phase;
}

const char* ZCollector::phase_to_string() const {
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

ZMinorCollector::ZMinorCollector(ZPageTable* page_table, ZPageAllocator* page_allocator) :
    ZCollector(ZCollectorId::_minor, "ZWorkerMinor", page_table, page_allocator),
    _skip_mark_start(false) {}

bool ZMinorCollector::should_skip_mark_start() {
  SuspendibleThreadSetJoiner sts_joiner;
  if (_skip_mark_start) {
    _skip_mark_start = false;
    return true;
  }
  return false;
}

void ZMinorCollector::skip_mark_start() {
  _skip_mark_start = true;
}

void ZMinorCollector::mark_start() {
  assert(SafepointSynchronize::is_at_safepoint(), "Should be at safepoint");

  // Flip address view
  ZGlobalsPointers::flip_minor_mark_start();

  // Retire allocating pages
  ZHeap::heap()->young_generation()->retire_pages();

  // Reset allocated/reclaimed/used statistics
  reset_statistics();

    // Enter mark phase
  set_phase(Phase::Mark);

  // Reset marking information and mark roots
  _mark.start();

  // Flip remembered set bits
  ZHeap::heap()->young_generation()->flip_remembered_set();

  // Update statistics
  stat_heap()->set_at_mark_start(_page_allocator->stats(this));
}

void ZMinorCollector::mark_roots() {
  ZStatTimerMinor timer(ZSubPhaseConcurrentMinorMarkRoots);
  _mark.mark_roots();
}

void ZMinorCollector::mark_follow() {
  ZStatTimerMinor timer(ZSubPhaseConcurrentMinorMarkFollow);
  _mark.mark_follow();
}

bool ZMinorCollector::mark_end() {
  assert(SafepointSynchronize::is_at_safepoint(), "Should be at safepoint");

  // End marking
  if (!_mark.end()) {
    // Marking not completed, continue concurrent mark
    return false;
  }

  // Enter mark completed phase
  set_phase(Phase::MarkComplete);

  if (!ZResurrection::is_blocked()) {
    // FIXME: Always verify
    // Verify after mark

    // FIXME: Need to turn this off because it assumes that strong roots will have been major marked as well.
    // ZVerify::after_mark();
  }

  // Update statistics
  stat_heap()->set_at_mark_end(_page_allocator->stats(this));

  // Notify JVMTI that some tagmap entry objects may have died.
  JvmtiTagMap::set_needs_cleaning();

  return true;
}

void ZMinorCollector::relocate_start() {
  assert(SafepointSynchronize::is_at_safepoint(), "Should be at safepoint");

  // Flip address view
  ZGlobalsPointers::flip_minor_relocate_start();

  // Enter relocate phase
  set_phase(Phase::Relocate);


  // Update statistics
  stat_heap()->set_at_relocate_start(_page_allocator->stats(this));

  // Notify JVMTI
  JvmtiTagMap::set_needs_rehashing();

  _relocate.start();
}

void ZMinorCollector::relocate() {
  // Relocate relocation set
  _relocate.relocate(&_relocation_set);

  // Update statistics
  stat_heap()->set_at_relocate_end(_page_allocator->stats(this), ZHeap::heap()->young_generation()->relocated());
}

void ZMinorCollector::promote_flip(ZPage* old_page, ZPage* new_page) {
  _page_table->replace(old_page, new_page);
  _relocation_set.register_promote_flip_page(old_page);

  ZHeap::heap()->young_generation()->decrease_used(old_page->size());
  ZHeap::heap()->old_generation()->increase_used(old_page->size());
}

void ZMinorCollector::promote_reloc(ZPage* old_page, ZPage* new_page) {
  _page_table->replace(old_page, new_page);
  _relocation_set.register_promote_reloc_page(old_page);

  ZHeap::heap()->young_generation()->decrease_used(old_page->size());
  ZHeap::heap()->old_generation()->increase_used(old_page->size());
}

ZMajorCollector::ZMajorCollector(ZPageTable* page_table, ZPageAllocator* page_allocator) :
  ZCollector(ZCollectorId::_major, "ZWorkerMajor", page_table, page_allocator),
  _reference_processor(&_workers),
  _weak_roots_processor(&_workers),
  _unload(&_workers),
  _total_collections_at_end(0) {}

void ZMajorCollector::reset_statistics() {
  ZCollector::reset_statistics();

  // The alloc stalled count is used by the major driver,
  // so reset it from the major cycle.
  _page_allocator->reset_alloc_stalled();
}

void ZMajorCollector::mark_start() {
  assert(SafepointSynchronize::is_at_safepoint(), "Should be at safepoint");

  // Flip address view
  ZGlobalsPointers::flip_major_mark_start();

  // Retire allocating pages
  ZHeap::heap()->old_generation()->retire_pages();

  // Reset allocated/reclaimed/used statistics
  reset_statistics();

  // Reset encountered/dropped/enqueued statistics
  _reference_processor.reset_statistics();

  // Enter mark phase
  set_phase(Phase::Mark);

  // Reset marking information and mark roots
  _mark.start();

  // Update statistics
  stat_heap()->set_at_mark_start(_page_allocator->stats(this));
}

void ZMajorCollector::mark_roots() {
  ZStatTimerMajor timer(ZSubPhaseConcurrentMajorMarkRoots);
  _mark.mark_roots();
}

void ZMajorCollector::mark_follow() {
  ZStatTimerMajor timer(ZSubPhaseConcurrentMajorMarkFollow);
  _mark.mark_follow();
}

bool ZMajorCollector::mark_end() {
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
  stat_heap()->set_at_mark_end(_page_allocator->stats(this));

  // Block resurrection of weak/phantom references
  ZResurrection::block();

  // Prepare to unload stale metadata and nmethods
  _unload.prepare();

  // Notify JVMTI that some tagmap entry objects may have died.
  JvmtiTagMap::set_needs_cleaning();

  return true;
}

void ZMajorCollector::set_soft_reference_policy(bool clear) {
  _reference_processor.set_soft_reference_policy(clear);
}

class ZRendezvousClosure : public HandshakeClosure {
public:
  ZRendezvousClosure() :
    HandshakeClosure("ZRendezvous") {}

  void do_thread(Thread* thread) {
  }
};

void ZMajorCollector::process_non_strong_references() {
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

  // Clear major markings claim bits.
  // Note: Clearing _claim_strong also clears _claim_finalizable.
  ClassLoaderDataGraph::clear_claimed_marks(ClassLoaderData::_claim_strong);
}

void ZMajorCollector::relocate_start() {
  assert(SafepointSynchronize::is_at_safepoint(), "Should be at safepoint");

  // Finish unloading stale metadata and nmethods
  _unload.finish();

  // Flip address view
  ZGlobalsPointers::flip_major_relocate_start();

  // Enter relocate phase
  set_phase(Phase::Relocate);

  // Update statistics
  stat_heap()->set_at_relocate_start(_page_allocator->stats(this));

  // Notify JVMTI
  JvmtiTagMap::set_needs_rehashing();

  _relocate.start();
}

void ZMajorCollector::relocate() {
  // Relocate relocation set
  _relocate.relocate(&_relocation_set);

  // Update statistics
  stat_heap()->set_at_relocate_end(_page_allocator->stats(this), ZHeap::heap()->old_generation()->relocated());
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
  ZColoredRootsAllIterator   _roots_colored;
  ZUncoloredRootsAllIterator _roots_uncolored;

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
      ZStatTimerMajor timer(ZSubPhaseConcurrentMajorRemapRootUncolored);
      _roots_colored.apply(&_cl_colored,
                           &_cld_cl);
    }

    {
      ZStatTimerMajor timer(ZSubPhaseConcurrentMajorRemapRootUncolored);
      _roots_uncolored.apply(&_thread_cl,
                             &_nm_cl);
    }
  }
};

void ZMajorCollector::roots_remap() {
  SuspendibleThreadSetJoiner sts_joiner;

  {
    ZGenerationPagesIterator iter(_page_table, ZGenerationId::old, _page_allocator);
    for (ZPage* page; iter.next(&page);) {
      if (!ZRemember::should_scan(page)) {
        continue;
      }
      // Visit all entries pointing into young gen
      page->oops_do_current_remembered([&](volatile zpointer* p) {
        ZBarrier::load_barrier_on_oop_field(p);
      });
    }
  }

  sts_joiner.yield();

  ZRemapRootsTask task;
  workers()->run(&task);
}

int ZMajorCollector::total_collections_at_end() const {
  return _total_collections_at_end;
}
