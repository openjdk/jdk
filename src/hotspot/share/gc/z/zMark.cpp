/*
 * Copyright (c) 2015, 2021, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/classLoaderData.hpp"
#include "classfile/classLoaderDataGraph.hpp"
#include "code/nmethod.hpp"
#include "gc/shared/gc_globals.hpp"
#include "gc/shared/suspendibleThreadSet.hpp"
#include "gc/z/zAbort.inline.hpp"
#include "gc/z/zAddress.inline.hpp"
#include "gc/z/zBarrier.inline.hpp"
#include "gc/z/zCollector.inline.hpp"
#include "gc/z/zDriver.hpp"
#include "gc/z/zHeap.inline.hpp"
#include "gc/z/zLock.inline.hpp"
#include "gc/z/zMark.inline.hpp"
#include "gc/z/zMarkCache.inline.hpp"
#include "gc/z/zMarkStack.inline.hpp"
#include "gc/z/zMarkTerminate.inline.hpp"
#include "gc/z/zNMethod.hpp"
#include "gc/z/zPage.hpp"
#include "gc/z/zPageTable.inline.hpp"
#include "gc/z/zRootsIterator.hpp"
#include "gc/z/zStackWatermark.hpp"
#include "gc/z/zStat.hpp"
#include "gc/z/zTask.hpp"
#include "gc/z/zThread.inline.hpp"
#include "gc/z/zThreadLocalAllocBuffer.hpp"
#include "gc/z/zUncoloredRoot.inline.hpp"
#include "gc/z/zUtils.inline.hpp"
#include "gc/z/zWorkers.hpp"
#include "logging/log.hpp"
#include "memory/iterator.inline.hpp"
#include "oops/objArrayOop.inline.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/atomic.hpp"
#include "runtime/handshake.hpp"
#include "runtime/prefetch.inline.hpp"
#include "runtime/safepointMechanism.hpp"
#include "runtime/stackWatermark.hpp"
#include "runtime/stackWatermarkSet.inline.hpp"
#include "runtime/thread.hpp"
#include "runtime/vmThread.hpp"
#include "utilities/align.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/powerOfTwo.hpp"
#include "utilities/ticks.hpp"

static const ZStatSubPhase ZSubPhaseConcurrentMinorMarkRootUncolored("Concurrent Minor Mark Root Uncolored");
static const ZStatSubPhase ZSubPhaseConcurrentMinorMarkRootColored("Concurrent Minor Mark Root Colored");
static const ZStatSubPhase ZSubPhaseConcurrentMark("Concurrent Mark");
static const ZStatSubPhase ZSubPhaseConcurrentMarkTryFlush("Concurrent Mark Try Flush");
static const ZStatSubPhase ZSubPhaseConcurrentMarkTryTerminate("Concurrent Mark Try Terminate");

ZMark::ZMark(ZCollector* collector, ZPageTable* page_table) :
    _collector(collector),
    _page_table(page_table),
    _allocator(),
    _stripes(_allocator.start()),
    _terminate(),
    _work_nproactiveflush(0),
    _work_nterminateflush(0),
    _nproactiveflush(0),
    _nterminateflush(0),
    _ntrycomplete(0),
    _ncontinue(0),
    _nworkers(0) {}

bool ZMark::is_initialized() const {
  return _allocator.is_initialized();
}

size_t ZMark::calculate_nstripes(uint nworkers) const {
  // Calculate the number of stripes from the number of workers we use,
  // where the number of stripes must be a power of two and we want to
  // have at least one worker per stripe.
  const size_t nstripes = round_down_power_of_2(nworkers);
  return MIN2(nstripes, ZMarkStripesMax);
}

void ZMark::start() {
  // Verification
  if (ZVerifyMarking) {
    verify_all_stacks_empty();
  }

  // Reset flush/continue counters
  _nproactiveflush = 0;
  _nterminateflush = 0;
  _ntrycomplete = 0;
  _ncontinue = 0;

  // Set number of workers to use
  _nworkers = workers()->active_workers();

  // Set number of mark stripes to use, based on number
  // of workers we will use in the concurrent mark phase.
  const size_t nstripes = calculate_nstripes(_nworkers);
  _stripes.set_nstripes(nstripes);

  // Update statistics
  _collector->stat_mark()->set_at_mark_start(nstripes);

  // Print worker/stripe distribution
  LogTarget(Debug, gc, marking) log;
  if (log.is_enabled()) {
    log.print("Mark Worker/Stripe Distribution");
    for (uint worker_id = 0; worker_id < _nworkers; worker_id++) {
      const ZMarkStripe* const stripe = _stripes.stripe_for_worker(_nworkers, worker_id);
      const size_t stripe_id = _stripes.stripe_id(stripe);
      log.print("  Worker %u(%u) -> Stripe " SIZE_FORMAT "(" SIZE_FORMAT ")",
                worker_id, _nworkers, stripe_id, nstripes);
    }
  }
}

ZWorkers* ZMark::workers() const {
  return _collector->workers();
}

void ZMark::prepare_work() {
  assert(_nworkers == workers()->active_workers(), "Invalid number of workers");
  // Set number of active workers
  _terminate.reset(_nworkers);

  // Reset flush counters
  _work_nproactiveflush = _work_nterminateflush = 0;
}

void ZMark::finish_work() {
  // Accumulate proactive/terminate flush counters
  _nproactiveflush += _work_nproactiveflush;
  _nterminateflush += _work_nterminateflush;
}

bool ZMark::is_array(zaddress addr) const {
  return to_oop(addr)->is_objArray();
}

void ZMark::push_partial_array(uintptr_t addr, size_t size, bool finalizable) {
  assert(is_aligned(addr, ZMarkPartialArrayMinSize), "Address misaligned");
  ZMarkThreadLocalStacks* const stacks = ZThreadLocalData::mark_stacks(Thread::current(), _collector->id());
  ZMarkStripe* const stripe = _stripes.stripe_for_addr(addr);
  const uintptr_t offset = untype(ZAddress::offset(to_zaddress(addr))) >> ZMarkPartialArrayMinSizeShift;
  const uintptr_t length = size / oopSize;
  const ZMarkStackEntry entry(offset, length, finalizable);

  log_develop_trace(gc, marking)("Array push partial: " PTR_FORMAT " (" SIZE_FORMAT "), stripe: " SIZE_FORMAT,
                                 addr, size, _stripes.stripe_id(stripe));

  stacks->push(&_allocator, &_stripes, stripe, entry, false /* publish */);
}

static void mark_barrier_on_oop_array(volatile zpointer* p, size_t length, bool finalizable, bool young) {
  for (volatile const zpointer* const end = p + length; p < end; p++) {
    if (young) {
      ZBarrier::mark_barrier_on_young_oop_field(p);
    } else {
      ZBarrier::mark_barrier_on_oop_field(p, finalizable);
    }
  }
}

void ZMark::follow_small_array(uintptr_t addr, size_t size, bool finalizable) {
  assert(size <= ZMarkPartialArrayMinSize, "Too large, should be split");
  const size_t length = size / oopSize;

  log_develop_trace(gc, marking)("Array follow small: " PTR_FORMAT " (" SIZE_FORMAT ")", addr, size);

  mark_barrier_on_oop_array((zpointer*)addr, length, finalizable, _collector->is_minor());
}

void ZMark::follow_large_array(uintptr_t addr, size_t size, bool finalizable) {
  assert(size <= (size_t)arrayOopDesc::max_array_length(T_OBJECT) * oopSize, "Too large");
  assert(size > ZMarkPartialArrayMinSize, "Too small, should not be split");
  const uintptr_t start = addr;
  const uintptr_t end = start + size;

  // Calculate the aligned middle start/end/size, where the middle start
  // should always be greater than the start (hence the +1 below) to make
  // sure we always do some follow work, not just split the array into pieces.
  const uintptr_t middle_start = align_up(start + 1, ZMarkPartialArrayMinSize);
  const size_t    middle_size = align_down(end - middle_start, ZMarkPartialArrayMinSize);
  const uintptr_t middle_end = middle_start + middle_size;

  log_develop_trace(gc, marking)("Array follow large: " PTR_FORMAT "-" PTR_FORMAT" (" SIZE_FORMAT "), "
                                 "middle: " PTR_FORMAT "-" PTR_FORMAT " (" SIZE_FORMAT ")",
                                 start, end, size, middle_start, middle_end, middle_size);

  // Push unaligned trailing part
  if (end > middle_end) {
    const uintptr_t trailing_addr = middle_end;
    const size_t trailing_size = end - middle_end;
    push_partial_array(trailing_addr, trailing_size, finalizable);
  }

  // Push aligned middle part(s)
  uintptr_t partial_addr = middle_end;
  while (partial_addr > middle_start) {
    const size_t parts = 2;
    const size_t partial_size = align_up((partial_addr - middle_start) / parts, ZMarkPartialArrayMinSize);
    partial_addr -= partial_size;
    push_partial_array(partial_addr, partial_size, finalizable);
  }

  // Follow leading part
  assert(start < middle_start, "Miscalculated middle start");
  const uintptr_t leading_addr = start;
  const size_t leading_size = middle_start - start;
  follow_small_array(leading_addr, leading_size, finalizable);
}

void ZMark::follow_array(uintptr_t addr, size_t size, bool finalizable) {
  if (size <= ZMarkPartialArrayMinSize) {
    follow_small_array(addr, size, finalizable);
  } else {
    follow_large_array(addr, size, finalizable);
  }
}

void ZMark::follow_partial_array(ZMarkStackEntry entry, bool finalizable) {
  const uintptr_t addr = untype(ZOffset::address(to_zoffset(entry.partial_array_offset() << ZMarkPartialArrayMinSizeShift)));
  const size_t size = entry.partial_array_length() * oopSize;

  follow_array(addr, size, finalizable);
}

template <bool finalizable, bool young>
class ZMarkBarrierOldGenOopClosure : public ClaimMetadataVisitingOopIterateClosure {
private:
  static int claim_value() {
    return finalizable ? ClassLoaderData::_claim_finalizable
                       : ClassLoaderData::_claim_strong;
  }

  static ReferenceDiscoverer* discoverer() {
    if (!finalizable) {
      return ZHeap::heap()->major_collector()->reference_discoverer();
    } else {
      return NULL;
    }
  }

  static bool visit_metadata() {
    // Only visit metadata if we're marking through the major cycle
    return ZHeap::heap()->major_collector()->is_phase_mark();
  }

  const bool _visit_metadata;

public:
  ZMarkBarrierOldGenOopClosure() :
      ClaimMetadataVisitingOopIterateClosure(claim_value(),
                                             discoverer()),
      _visit_metadata(visit_metadata()) {}

  virtual void do_oop(oop* p) {
    if (young) {
      ZBarrier::mark_barrier_on_young_oop_field((zpointer*)p);
    } else {
      ZBarrier::mark_barrier_on_oop_field((zpointer*)p, finalizable);
    }
  }

  virtual void do_oop(narrowOop* p) {
    ShouldNotReachHere();
  }

  virtual bool do_metadata() final {
    // Only help out with metadata visiting
    return _visit_metadata;
  }
};

void ZMark::follow_array_object(objArrayOop obj, bool finalizable) {
  if (_collector->is_major()) {
    if (finalizable) {
      ZMarkBarrierOldGenOopClosure<true /* finalizable */, false /* young */> cl;
      cl.do_klass(obj->klass());
    } else {
      ZMarkBarrierOldGenOopClosure<false /* finalizable */, false /* young */> cl;
      cl.do_klass(obj->klass());
    }
  }

  assert(is_valid(to_zaddress(obj)), "Should be converitble to colorless oop");

  // FIXME: Don't use uintptr_t
  const uintptr_t addr = (uintptr_t)obj->base();
  const size_t size = (size_t)obj->length() * oopSize;

  follow_array(addr, size, finalizable);
}

void ZMark::follow_object(oop obj, bool finalizable) {
  if (_collector->is_major()) {
    if (ZHeap::heap()->is_old(to_zaddress(obj))) {
      if (finalizable) {
        ZMarkBarrierOldGenOopClosure<true /* finalizable */, false /* young */> cl;
        ZIterator::oop_iterate(obj, &cl);
      } else {
        ZMarkBarrierOldGenOopClosure<false /* finalizable */, false /* young */> cl;
        ZIterator::oop_iterate(obj, &cl);
      }
    } else {
      fatal("Catch me!");
    }
  } else {
    // Young gen must help out with major marking
    ZMarkBarrierOldGenOopClosure<false /* finalizable */, true /* young */> cl;
    ZIterator::oop_iterate(obj, &cl);
  }
}

void ZMark::mark_and_follow(ZMarkCache* cache, ZMarkStackEntry entry) {
  // Decode flags
  const bool finalizable = entry.finalizable();
  const bool partial_array = entry.partial_array();

  if (partial_array) {
    follow_partial_array(entry, finalizable);
    return;
  }

  // Decode object address and additional flags
  const zaddress addr = ZOffset::address(to_zoffset(entry.object_address()));
  const bool mark = entry.mark();
  bool inc_live = entry.inc_live();
  const bool follow = entry.follow();

  ZPage* const page = _page_table->get(addr);
  assert(page->is_relocatable(), "Invalid page state");

  // Mark
  if (mark && !page->mark_object(addr, finalizable, inc_live)) {
    // Already marked
    return;
  }

  // Increment live
  if (inc_live) {
    // Update live objects/bytes for page. We use the aligned object
    // size since that is the actual number of bytes used on the page
    // and alignment paddings can never be reclaimed.
    const size_t size = ZUtils::object_size(addr);
    const size_t aligned_size = align_up(size, page->object_alignment());
    cache->inc_live(page, aligned_size);
  }

  // Follow
  if (follow) {
    if (is_array(addr)) {
      follow_array_object(objArrayOop(to_oop(addr)), finalizable);
    } else {
      follow_object(to_oop(addr), finalizable);
    }
  }
}

bool ZMark::drain(ZMarkStripe* stripe, ZMarkThreadLocalStacks* stacks, ZMarkCache* cache) {
  ZMarkStackEntry entry;
  size_t processed = 0;

  // Drain stripe stacks
  while (stacks->pop(&_allocator, &_stripes, stripe, entry)) {
    mark_and_follow(cache, entry);

    if ((processed++ & 31) == 0) {
      // Yield once per 32 oops
      SuspendibleThreadSet::yield();
      if (ZAbort::should_abort()) {
        return false;
      }
    }
  }

  return true;
}

bool ZMark::try_steal_local(ZMarkStripe* stripe, ZMarkThreadLocalStacks* stacks) {
  // Try to steal a local stack from another stripe
  for (ZMarkStripe* victim_stripe = _stripes.stripe_next(stripe);
       victim_stripe != stripe;
       victim_stripe = _stripes.stripe_next(victim_stripe)) {
    ZMarkStack* const stack = stacks->steal(&_stripes, victim_stripe);
    if (stack != NULL) {
      // Success, install the stolen stack
      stacks->install(&_stripes, stripe, stack);
      return true;
    }
  }

  // Nothing to steal
  return false;
}

bool ZMark::try_steal_global(ZMarkStripe* stripe, ZMarkThreadLocalStacks* stacks) {
  // Try to steal a stack from another stripe
  for (ZMarkStripe* victim_stripe = _stripes.stripe_next(stripe);
       victim_stripe != stripe;
       victim_stripe = _stripes.stripe_next(victim_stripe)) {
    ZMarkStack* const stack = victim_stripe->steal_stack();
    if (stack != NULL) {
      // Success, install the stolen stack
      stacks->install(&_stripes, stripe, stack);
      return true;
    }
  }

  // Nothing to steal
  return false;
}

bool ZMark::try_steal(ZMarkStripe* stripe, ZMarkThreadLocalStacks* stacks) {
  return try_steal_local(stripe, stacks) || try_steal_global(stripe, stacks);
}

void ZMark::idle() const {
  SuspendibleThreadSet::yield();
  os::naked_short_sleep(1);
}

class ZMarkFlushAndFreeStacksClosure : public HandshakeClosure {
private:
  ZMark* const _mark;
  bool         _flushed;

public:
  ZMarkFlushAndFreeStacksClosure(ZMark* mark) :
      HandshakeClosure("ZMarkFlushAndFreeStacks"),
      _mark(mark),
      _flushed(false) {}

  void do_thread(Thread* thread) {
    if (_mark->flush_and_free(thread)) {
      _flushed = true;
      if (SafepointSynchronize::is_at_safepoint()) {
        log_debug(gc, marking)("Thread broke mark termination %s", thread->name());
      }
    }
  }

  bool flushed() const {
    return _flushed;
  }
};

class VM_ZMarkFlushOperation : public VM_Operation {
private:
  ThreadClosure* _cl;
  bool           _gc_threads;

public:
  VM_ZMarkFlushOperation(ThreadClosure* cl, bool gc_threads) :
      _cl(cl),
      _gc_threads(gc_threads) {}

  virtual bool evaluate_at_safepoint() const {
    return false;
  }

  virtual void doit() {
    // Flush GC threads
    if (_gc_threads) {
      SuspendibleThreadSet::synchronize();
      ZHeap::heap()->minor_collector()->workers()->threads_do(_cl);
      ZHeap::heap()->major_collector()->workers()->threads_do(_cl);
      SuspendibleThreadSet::desynchronize();
    }
    // Flush VM thread
    Thread* thread = Thread::current();
    _cl->do_thread(thread);
  }

  virtual VMOp_Type type() const {
    return VMOp_ZMarkFlushOperation;
  }
};

bool ZMark::flush(bool gc_threads) {
  ZMarkFlushAndFreeStacksClosure cl(this);
  VM_ZMarkFlushOperation vm_cl(&cl, gc_threads);
  Handshake::execute(&cl);
  VMThread::execute(&vm_cl);

  // Returns true if more work is available
  return cl.flushed() || !_stripes.is_empty();
}

bool ZMark::try_terminate_flush() {
  Atomic::inc(&_work_nterminateflush);
  _terminate.set_resurrected(false);

  ZStatTimer timer(ZSubPhaseConcurrentMarkTryFlush, _collector->timer());
  return flush(true /* gc_threads */) ||
         _terminate.resurrected();
}

bool ZMark::try_proactive_flush() {
  Atomic::inc(&_work_nproactiveflush);

  // Only do proactive flushes from worker 0
  if (ZThread::worker_id() != 0) {
    return false;
  }

  if (Atomic::load(&_work_nproactiveflush) == ZMarkProactiveFlushMax) {
    // Limit reached or we're trying to terminate
    return false;
  }

  ZStatTimer timer(ZSubPhaseConcurrentMarkTryFlush);
  SuspendibleThreadSetLeaver sts;
  return flush(false /* gc_threads */);
}

bool ZMark::try_terminate() {
  ZStatTimer timer(ZSubPhaseConcurrentMarkTryTerminate);
  for (;;) {
    if (_terminate.enter()) {
      // Last thread entered, terminate
      return true;
    }

    // Idle to give the other threads
    // a chance to enter termination.
    idle();

    if (ZAbort::should_abort()) {
      return true;
    }

    if (!_terminate.try_exit()) {
      // All workers entered, terminate
      return true;
    }
  }
}

void ZMark::work() {
  ZStatTimer timer(ZSubPhaseConcurrentMark);
  SuspendibleThreadSetJoiner sts;
  ZMarkCache cache(_stripes.nstripes());
  ZMarkStripe* const stripe = _stripes.stripe_for_worker(_nworkers, ZThread::worker_id());
  ZMarkThreadLocalStacks* const stacks = ZThreadLocalData::mark_stacks(Thread::current(), _collector->id());

  for (;;) {
    if (!drain(stripe, stacks, &cache)) {
      break;
    }

    if (try_steal(stripe, stacks)) {
      // Stole work
      continue;
    }

    if (try_proactive_flush()) {
      // Work available
      continue;
    }

    if (try_terminate()) {
      // Terminate
      break;
    }
  }

  // Free remaining stacks
  stacks->free(&_allocator);
}

class ZMarkOopClosure : public OopClosure {
public:
  virtual void do_oop(oop* p) {
    ZBarrier::mark_barrier_on_oop_field((zpointer*)p, false /* finalizable */);
  }

  virtual void do_oop(narrowOop* p) {
    ShouldNotReachHere();
  }
};

class ZMarkYoungOopClosure : public OopClosure {
public:
  virtual void do_oop(oop* p) {
    ZBarrier::mark_minor_good_barrier_on_oop_field((zpointer*)p);
  }

  virtual void do_oop(narrowOop* p) {
    ShouldNotReachHere();
  }
};

class ZMarkThreadClosure : public ThreadClosure {
private:
  static ZUncoloredRoot::RootFunction root_function() {
    return ZUncoloredRoot::mark;
  }

public:
  ZMarkThreadClosure() {
    ZThreadLocalAllocBuffer::reset_statistics();
  }
  ~ZMarkThreadClosure() {
    ZThreadLocalAllocBuffer::publish_statistics();
  }

  virtual void do_thread(Thread* thread) {
    JavaThread* const jt = JavaThread::cast(thread);

    StackWatermarkSet::finish_processing(jt, (void*)root_function(), StackWatermarkKind::gc);
    ZThreadLocalAllocBuffer::update_stats(jt);
  }
};

class ZMarkNMethodClosure : public NMethodClosure {
private:
  ZBarrierSetNMethod* const _bs_nm;

public:
  ZMarkNMethodClosure() :
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
      ZUncoloredRootMarkOopClosure cl(ZNMethod::color(nm));
      ZNMethod::nmethod_oops_do_inner(nm, &cl);

      log_trace(gc, nmethod)("nmethod: " PTR_FORMAT " visited by old", p2i(nm));

      // Disarm
      _bs_nm->disarm(nm);
    }
  }
};

class ZMarkYoungNMethodClosure : public NMethodClosure {
private:
  ZBarrierSetNMethod* const _bs_nm;

public:
  ZMarkYoungNMethodClosure() :
      _bs_nm(static_cast<ZBarrierSetNMethod*>(BarrierSet::barrier_set()->barrier_set_nmethod())) {}

  virtual void do_nmethod(nmethod* nm) {
    ZLocker<ZReentrantLock> locker(ZNMethod::lock_for_nmethod(nm));
    if (!nm->is_alive() || nm->is_unloading()) {
      return;
    }

    if (_bs_nm->is_armed(nm)) {
      // NOTE: Not for young marking
      // Heal barriers
      // ZNMethod::nmethod_patch_barriers(nm);

      const uintptr_t prev_color = ZNMethod::color(nm);

      // Heal oops
      ZUncoloredRootMarkYoungOopClosure cl(prev_color);
      ZNMethod::nmethod_oops_do_inner(nm, &cl);

      // Disarm only the minor marking, not any potential major marking cycle

      const uintptr_t major_marked_mask = ZPointerMarkedMask ^ (ZPointerMarkedMinor0 | ZPointerMarkedMinor1);
      const uintptr_t major_marked = prev_color & major_marked_mask;

      const zpointer new_disarm_value_ptr = ZAddress::color(zaddress::null, ZPointerLoadGoodMask | ZPointerMarkedMinor | major_marked | ZPointerRemembered);

      // Check if disarming for minor mark, completely disarms the nmethod entry barrier
      const bool complete_disarm = ZPointer::is_mark_good(new_disarm_value_ptr);

      if (complete_disarm) {
        // We are about to completely disarm the nmethod, must take responsibility to patch all barriers before disarming
        ZNMethod::nmethod_patch_barriers(nm);
      }

      _bs_nm->disarm_with_value(nm, (int)untype(new_disarm_value_ptr));

      if (complete_disarm) {
        log_trace(gc, nmethod)("nmethod: " PTR_FORMAT " visited by young (complete) [" PTR_FORMAT " -> " PTR_FORMAT "]", p2i(nm), prev_color, untype(new_disarm_value_ptr));
        assert(!_bs_nm->is_armed(nm), "Must not be considered armed anymore");
      } else {
        log_trace(gc, nmethod)("nmethod: " PTR_FORMAT " visited by young (incomplete) [" PTR_FORMAT " -> " PTR_FORMAT "]", p2i(nm), prev_color, untype(new_disarm_value_ptr));
        assert(_bs_nm->is_armed(nm), "Must be considered armed");
      }
    }
  }
};

typedef ClaimingCLDToOopClosure<ClassLoaderData::_claim_strong> ZMarkOldGenCLDClosure;

class ZMarkOldGenRootsTask : public ZTask {
private:
  ZMark* const                  _mark;
  ZColoredRootsStrongIterator   _roots_colored;
  ZUncoloredRootsStrongIterator _roots_uncolored;

  ZMarkOopClosure               _cl_colored;
  ZMarkOldGenCLDClosure         _cld_cl;

  ZMarkThreadClosure            _thread_cl;
  ZMarkNMethodClosure           _nm_cl;


public:
  ZMarkOldGenRootsTask(ZMark* mark) :
      ZTask("ZMarkOldGenRootsTask"),
      _mark(mark),
      _roots_colored(),
      _roots_uncolored(),
      _cl_colored(),
      _cld_cl(&_cl_colored),
      _thread_cl(),
      _nm_cl() {
    ClassLoaderDataGraph_lock->lock();
  }

  ~ZMarkOldGenRootsTask() {
    ClassLoaderDataGraph_lock->unlock();
  }

  virtual void work() {
    _roots_colored.apply(&_cl_colored,
                         &_cld_cl);

    _roots_uncolored.apply(&_thread_cl,
                           &_nm_cl);

    // Flush and free worker stacks. Needed here since
    // the set of workers executing during root scanning
    // can be different from the set of workers executing
    // during mark.
    _mark->flush_and_free();
  }
};

class ZMarkRememberedSetIterator {
private:
  bool _task_taken;

public:
  ZMarkRememberedSetIterator() : _task_taken(false) {}

  void apply(OopClosure* cl) {
    // FIXME: Only visited by one thread at the moment
    if (Atomic::cmpxchg(&_task_taken, false, true) == false) {
    }
  }
};

class ZExtraYoungRootsIterator {
private:
  ZParallelApply<ZMarkRememberedSetIterator> _remember_set;

public:
  void apply(OopClosure* cl) {
    _remember_set.apply(cl);
  }
};

typedef ClaimingCLDToOopClosure<ClassLoaderData::_claim_none> ZMarkYoungGenCLDClosure;

class ZMarkYoungGenRootsTask : public ZTask {
private:
  ZMark* const               _mark;
  ZColoredRootsAllIterator   _roots_colored;
  ZUncoloredRootsAllIterator _roots_uncolored;

  ZMarkYoungOopClosure       _cl_colored;
  ZMarkYoungGenCLDClosure    _cld_cl;

  ZMarkThreadClosure         _thread_cl;
  ZMarkYoungNMethodClosure   _nm_cl;

public:
  ZMarkYoungGenRootsTask(ZMark* mark) :
      ZTask("ZMarkYoungGenRootsTask"),
      _mark(mark),
      _roots_colored(),
      _roots_uncolored(),
      _cl_colored(),
      _cld_cl(&_cl_colored),
      _thread_cl(),
      _nm_cl() {
    // FIXME: Needed?
    ClassLoaderDataGraph_lock->lock();
  }

  ~ZMarkYoungGenRootsTask() {
    ClassLoaderDataGraph_lock->unlock();
  }

  virtual void work() {
    {
      ZStatTimerMinor timer(ZSubPhaseConcurrentMinorMarkRootColored);
      _roots_colored.apply(&_cl_colored,
                           &_cld_cl);
    }

    {
      ZStatTimerMinor timer(ZSubPhaseConcurrentMinorMarkRootUncolored);
      _roots_uncolored.apply(&_thread_cl,
                             &_nm_cl);
    }
    // Flush and free worker stacks. Needed here since
    // the set of workers executing during root scanning
    // can be different from the set of workers executing
    // during mark.
    _mark->flush_and_free();
  }
};

class ZMarkTask : public ZTask {
private:
  ZMark* const _mark;

public:
  ZMarkTask(ZMark* mark) :
      ZTask("ZMarkTask"),
      _mark(mark) {
    _mark->prepare_work();
  }

  ~ZMarkTask() {
    _mark->finish_work();
  }

  virtual void work() {
    _mark->work();
  }
};

void ZMark::mark_roots() {
  SuspendibleThreadSetJoiner sts_joiner;

  if (_collector->is_major()) {
    ZMarkOldGenRootsTask task(this);
    workers()->run(&task);
  } else {
    // Mark from old-to-young pointers
    ZHeap::heap()->young_generation()->scan_remembered_sets();

    ZMarkYoungGenRootsTask task(this);
    workers()->run(&task);
  }
}

void ZMark::mark_follow() {
  do {
    ZMarkTask task(this);
    workers()->run(&task);
  } while (!ZAbort::should_abort() && try_terminate_flush());
}

bool ZMark::try_end() {
  if (_terminate.resurrected()) {
    // An oop was resurrected after concurrent termination.
    return false;
  }

  // Try end marking
  ZMarkFlushAndFreeStacksClosure cl(this);
  Threads::non_java_threads_do(&cl);

  // Check if non-java threads have any pending marking
  if (cl.flushed() || !_stripes.is_empty()) {
    return false;
  }

  // Mark completed
  return true;
}

bool ZMark::end() {
  // Try end marking
  if (!try_end()) {
    // Mark not completed
    _ncontinue++;
    return false;
  }

  // Verification
  if (ZVerifyMarking) {
    verify_all_stacks_empty();
  }

  // Update statistics
  _collector->stat_mark()->set_at_mark_end(_nproactiveflush, _nterminateflush, _ntrycomplete, _ncontinue);

  // Mark completed
  return true;
}

void ZMark::free() {
  // Free any unused mark stack space
  _allocator.free();

  // Update statistics
  _collector->stat_mark()->set_at_mark_free(_allocator.size());
}

void ZMark::flush_and_free() {
  Thread* const thread = Thread::current();
  flush_and_free(thread);
}

bool ZMark::flush_and_free(Thread* thread) {
  if (thread->is_Java_thread()) {
    ZThreadLocalData::store_barrier_buffer(thread)->flush();
  }
  ZMarkThreadLocalStacks* const stacks = ZThreadLocalData::mark_stacks(thread, _collector->id());
  const bool flushed = stacks->flush(&_allocator, &_stripes);
  stacks->free(&_allocator);
  return flushed;
}

class ZVerifyMarkStacksEmptyClosure : public ThreadClosure {
private:
  const ZMarkStripeSet* const _stripes;
  const ZCollectorId _collector_id;

public:
  ZVerifyMarkStacksEmptyClosure(const ZMarkStripeSet* stripes, ZCollectorId collector_id) :
      _stripes(stripes),
      _collector_id(collector_id) {}

  void do_thread(Thread* thread) {
    ZMarkThreadLocalStacks* const stacks = ZThreadLocalData::mark_stacks(thread, _collector_id);
    guarantee(stacks->is_empty(_stripes), "Should be empty");
  }
};

void ZMark::verify_all_stacks_empty() const {
  // Verify thread stacks
  ZVerifyMarkStacksEmptyClosure cl(&_stripes, _collector->id());
  Threads::threads_do(&cl);

  // Verify stripe stacks
  guarantee(_stripes.is_empty(), "Should be empty");
}
