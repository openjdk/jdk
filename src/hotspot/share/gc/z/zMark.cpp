/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "classfile/classLoaderData.hpp"
#include "classfile/classLoaderDataGraph.hpp"
#include "classfile/javaClasses.inline.hpp"
#include "code/nmethod.hpp"
#include "gc/shared/continuationGCSupport.inline.hpp"
#include "gc/shared/gc_globals.hpp"
#include "gc/shared/suspendibleThreadSet.hpp"
#include "gc/shared/workerThread.hpp"
#include "gc/z/zAbort.inline.hpp"
#include "gc/z/zAddress.inline.hpp"
#include "gc/z/zBarrier.inline.hpp"
#include "gc/z/zBarrierSetNMethod.hpp"
#include "gc/z/zGeneration.inline.hpp"
#include "gc/z/zGenerationId.hpp"
#include "gc/z/zHeap.inline.hpp"
#include "gc/z/zLock.inline.hpp"
#include "gc/z/zMark.inline.hpp"
#include "gc/z/zMarkCache.inline.hpp"
#include "gc/z/zMarkContext.inline.hpp"
#include "gc/z/zMarkStack.inline.hpp"
#include "gc/z/zMarkTerminate.inline.hpp"
#include "gc/z/zNMethod.hpp"
#include "gc/z/zPage.hpp"
#include "gc/z/zPageTable.inline.hpp"
#include "gc/z/zRootsIterator.hpp"
#include "gc/z/zStackWatermark.hpp"
#include "gc/z/zStat.hpp"
#include "gc/z/zTask.hpp"
#include "gc/z/zThreadLocalAllocBuffer.hpp"
#include "gc/z/zUncoloredRoot.inline.hpp"
#include "gc/z/zUtils.inline.hpp"
#include "gc/z/zWorkers.hpp"
#include "logging/log.hpp"
#include "memory/iterator.inline.hpp"
#include "oops/objArrayOop.inline.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/atomic.hpp"
#include "runtime/continuation.hpp"
#include "runtime/handshake.hpp"
#include "runtime/javaThread.hpp"
#include "runtime/prefetch.inline.hpp"
#include "runtime/safepointMechanism.hpp"
#include "runtime/stackWatermark.hpp"
#include "runtime/stackWatermarkSet.inline.hpp"
#include "runtime/threads.hpp"
#include "runtime/vmThread.hpp"
#include "utilities/align.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/powerOfTwo.hpp"
#include "utilities/ticks.hpp"

static const ZStatSubPhase ZSubPhaseConcurrentMarkRootUncoloredYoung("Concurrent Mark Root Uncolored", ZGenerationId::young);
static const ZStatSubPhase ZSubPhaseConcurrentMarkRootColoredYoung("Concurrent Mark Root Colored", ZGenerationId::young);
static const ZStatSubPhase ZSubPhaseConcurrentMarkRootUncoloredOld("Concurrent Mark Root Uncolored", ZGenerationId::old);
static const ZStatSubPhase ZSubPhaseConcurrentMarkRootColoredOld("Concurrent Mark Root Colored", ZGenerationId::old);

ZMark::ZMark(ZGeneration* generation, ZPageTable* page_table)
  : _generation(generation),
    _page_table(page_table),
    _marking_smr(),
    _stripes(),
    _terminate(),
    _work_nproactiveflush(0),
    _work_nterminateflush(0),
    _nproactiveflush(0),
    _nterminateflush(0),
    _ntrycomplete(0),
    _ncontinue(0),
    _nworkers(0) {}

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
  _generation->stat_mark()->at_mark_start(nstripes);

  // Print worker/stripe distribution
  LogTarget(Debug, gc, marking) log;
  if (log.is_enabled()) {
    log.print("Mark Worker/Stripe Distribution");
    for (uint worker_id = 0; worker_id < _nworkers; worker_id++) {
      const ZMarkStripe* const stripe = _stripes.stripe_for_worker(_nworkers, worker_id);
      const size_t stripe_id = _stripes.stripe_id(stripe);
      log.print("  Worker %u(%u) -> Stripe %zu(%zu)",
                worker_id, _nworkers, stripe_id, nstripes);
    }
  }
}

ZWorkers* ZMark::workers() const {
  return _generation->workers();
}

void ZMark::prepare_work() {
  // Set number of workers to use
  _nworkers = workers()->active_workers();

  // Set number of mark stripes to use, based on number
  // of workers we will use in the concurrent mark phase.
  const size_t nstripes = calculate_nstripes(_nworkers);
  _stripes.set_nstripes(nstripes);

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

void ZMark::follow_work_complete() {
  follow_work(false /* partial */);
}

bool ZMark::follow_work_partial() {
  return follow_work(true /* partial */);
}

bool ZMark::is_array(zaddress addr) const {
  return to_oop(addr)->is_objArray();
}

static uintptr_t encode_partial_array_offset(zpointer* addr) {
  return untype(ZAddress::offset(to_zaddress((uintptr_t)addr))) >> ZMarkPartialArrayMinSizeShift;
}

static zpointer* decode_partial_array_offset(uintptr_t offset) {
  return (zpointer*)ZOffset::address(to_zoffset(offset << ZMarkPartialArrayMinSizeShift));
}

void ZMark::push_partial_array(zpointer* addr, size_t length, bool finalizable) {
  assert(is_aligned(addr, ZMarkPartialArrayMinSize), "Address misaligned");
  ZMarkThreadLocalStacks* const stacks = ZThreadLocalData::mark_stacks(Thread::current(), _generation->id());
  ZMarkStripe* const stripe = _stripes.stripe_for_addr((uintptr_t)addr);
  const uintptr_t offset = encode_partial_array_offset(addr);
  const ZMarkStackEntry entry(offset, length, finalizable);

  log_develop_trace(gc, marking)("Array push partial: " PTR_FORMAT " (%zu), stripe: %zu",
                                 p2i(addr), length, _stripes.stripe_id(stripe));

  stacks->push(&_stripes, stripe, &_terminate, entry, false /* publish */);
}

static void mark_barrier_on_oop_array(volatile zpointer* p, size_t length, bool finalizable, bool young) {
  for (volatile const zpointer* const end = p + length; p < end; p++) {
    if (young) {
      ZBarrier::mark_barrier_on_young_oop_field(p);
    } else {
      ZBarrier::mark_barrier_on_old_oop_field(p, finalizable);
    }
  }
}

void ZMark::follow_array_elements_small(zpointer* addr, size_t length, bool finalizable) {
  assert(length <= ZMarkPartialArrayMinLength, "Too large, should be split");

  log_develop_trace(gc, marking)("Array follow small: " PTR_FORMAT " (%zu)", p2i(addr), length);

  mark_barrier_on_oop_array(addr, length, finalizable, _generation->is_young());
}

void ZMark::follow_array_elements_large(zpointer* addr, size_t length, bool finalizable) {
  assert(length <= (size_t)arrayOopDesc::max_array_length(T_OBJECT), "Too large");
  assert(length > ZMarkPartialArrayMinLength, "Too small, should not be split");

  zpointer* const start = addr;
  zpointer* const end = start + length;

  // Calculate the aligned middle start/end/size, where the middle start
  // should always be greater than the start (hence the +1 below) to make
  // sure we always do some follow work, not just split the array into pieces.
  zpointer* const middle_start = align_up(start + 1, ZMarkPartialArrayMinSize);
  const size_t    middle_length = align_down(end - middle_start, ZMarkPartialArrayMinLength);
  zpointer* const middle_end = middle_start + middle_length;

  log_develop_trace(gc, marking)("Array follow large: " PTR_FORMAT "-" PTR_FORMAT" (%zu), "
                                 "middle: " PTR_FORMAT "-" PTR_FORMAT " (%zu)",
                                 p2i(start), p2i(end), length, p2i(middle_start), p2i(middle_end), middle_length);

  // Push unaligned trailing part
  if (end > middle_end) {
    zpointer* const trailing_addr = middle_end;
    const size_t trailing_length = end - middle_end;
    push_partial_array(trailing_addr, trailing_length, finalizable);
  }

  // Push aligned middle part(s)
  zpointer* partial_addr = middle_end;
  while (partial_addr > middle_start) {
    const size_t parts = 2;
    const size_t partial_length = align_up((partial_addr - middle_start) / parts, ZMarkPartialArrayMinLength);
    partial_addr -= partial_length;
    push_partial_array(partial_addr, partial_length, finalizable);
  }

  // Follow leading part
  assert(start < middle_start, "Miscalculated middle start");
  zpointer* const leading_addr = start;
  const size_t leading_length = middle_start - start;
  follow_array_elements_small(leading_addr, leading_length, finalizable);
}

void ZMark::follow_array_elements(zpointer* addr, size_t length, bool finalizable) {
  if (length <= ZMarkPartialArrayMinLength) {
    follow_array_elements_small(addr, length, finalizable);
  } else {
    follow_array_elements_large(addr, length, finalizable);
  }
}

void ZMark::follow_partial_array(ZMarkStackEntry entry, bool finalizable) {
  zpointer* const addr = decode_partial_array_offset(entry.partial_array_offset());
  const size_t length = entry.partial_array_length();

  follow_array_elements(addr, length, finalizable);
}

template <bool finalizable, ZGenerationIdOptional generation>
class ZMarkBarrierFollowOopClosure : public OopIterateClosure {
private:
  static int claim_value() {
    return finalizable ? ClassLoaderData::_claim_finalizable
                       : ClassLoaderData::_claim_strong;
  }

  static ReferenceDiscoverer* discoverer() {
    if (!finalizable) {
      return ZGeneration::old()->reference_discoverer();
    } else {
      return nullptr;
    }
  }

  static bool visit_metadata() {
    // Only visit metadata if we're marking through the old generation
    return ZGeneration::old()->is_phase_mark();
  }

  const bool _visit_metadata;

public:
  ZMarkBarrierFollowOopClosure()
    : OopIterateClosure(discoverer()),
      _visit_metadata(visit_metadata()) {}

  virtual void do_oop(oop* p) {
    switch (generation) {
    case ZGenerationIdOptional::young:
      ZBarrier::mark_barrier_on_young_oop_field((volatile zpointer*)p);
      break;
    case ZGenerationIdOptional::old:
      ZBarrier::mark_barrier_on_old_oop_field((volatile zpointer*)p, finalizable);
      break;
    case ZGenerationIdOptional::none:
      ZBarrier::mark_barrier_on_oop_field((volatile zpointer*)p, finalizable);
      break;
    }
  }

  virtual void do_oop(narrowOop* p) {
    ShouldNotReachHere();
  }

  virtual bool do_metadata() final {
    // Only help out with metadata visiting
    return _visit_metadata;
  }

  virtual void do_nmethod(nmethod* nm) {
    assert(do_metadata(), "Don't call otherwise");
    assert(!finalizable, "Can't handle finalizable marking of nmethods");
    nm->run_nmethod_entry_barrier();
  }

  virtual void do_method(Method* m) {
    // Mark interpreted frames for class redefinition
    m->record_gc_epoch();
  }

  virtual void do_klass(Klass* klass) {
    ClassLoaderData* cld = klass->class_loader_data();
    ZMarkBarrierFollowOopClosure<finalizable, ZGenerationIdOptional::none> cl;
    cld->oops_do(&cl, claim_value());
  }

  virtual void do_cld(ClassLoaderData* cld) {
    ZMarkBarrierFollowOopClosure<finalizable, ZGenerationIdOptional::none> cl;
    cld->oops_do(&cl, claim_value());
  }
};

void ZMark::follow_array_object(objArrayOop obj, bool finalizable) {
  if (_generation->is_old()) {
    if (finalizable) {
      ZMarkBarrierFollowOopClosure<true /* finalizable */, ZGenerationIdOptional::old> cl;
      cl.do_klass(obj->klass());
    } else {
      ZMarkBarrierFollowOopClosure<false /* finalizable */, ZGenerationIdOptional::old> cl;
      cl.do_klass(obj->klass());
    }
  } else {
    ZMarkBarrierFollowOopClosure<false /* finalizable */, ZGenerationIdOptional::none> cl;
    if (cl.do_metadata()) {
      cl.do_klass(obj->klass());
    }
  }

  // Should be convertible to colorless oop
  check_is_valid_zaddress(obj);

  zpointer* const addr = (zpointer*)obj->base();
  const size_t length = (size_t)obj->length();

  follow_array_elements(addr, length, finalizable);
}

void ZMark::follow_object(oop obj, bool finalizable) {
  if (_generation->is_old()) {
    assert(ZHeap::heap()->is_old(to_zaddress(obj)), "Should only follow objects from old gen");
    if (obj->is_stackChunk()) {
      // No support for tracing through stack chunks as finalizably reachable
      ZMarkBarrierFollowOopClosure<false /* finalizable */, ZGenerationIdOptional::old> cl;
      ZIterator::oop_iterate(obj, &cl);
    } else if (finalizable) {
      ZMarkBarrierFollowOopClosure<true /* finalizable */, ZGenerationIdOptional::old> cl;
      ZIterator::oop_iterate(obj, &cl);
    } else {
      ZMarkBarrierFollowOopClosure<false /* finalizable */, ZGenerationIdOptional::old> cl;
      ZIterator::oop_iterate(obj, &cl);
    }
  } else {
    // Young gen must help out with old marking
    ZMarkBarrierFollowOopClosure<false /* finalizable */, ZGenerationIdOptional::young> cl;
    ZIterator::oop_iterate(obj, &cl);
  }
}


void ZMark::mark_and_follow(ZMarkContext* context, ZMarkStackEntry entry) {
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
    context->cache()->inc_live(page, aligned_size);
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

// This function returns true if we need to stop working to resize threads or
// abort marking
bool ZMark::rebalance_work(ZMarkContext* context) {
  const size_t assumed_nstripes = context->nstripes();
  const size_t nstripes = _stripes.nstripes();

  if (assumed_nstripes != nstripes) {
    // The number of stripes has changed; reflect that change locally
    context->set_nstripes(nstripes);
  } else if (nstripes < calculate_nstripes(_nworkers) && _stripes.is_crowded()) {
    // We are running on a reduced number of threads to minimize the amount of work
    // hidden in local stacks when the stripes are less well balanced. When this situation
    // starts getting crowded, we bump the number of stripes again.
    const size_t new_nstripes = nstripes << 1;
    if (_stripes.try_set_nstripes(nstripes, new_nstripes)) {
      context->set_nstripes(new_nstripes);
    }
  }

  ZMarkStripe* stripe = _stripes.stripe_for_worker(_nworkers, WorkerThread::worker_id());
  if (context->stripe() != stripe) {
    // Need to switch stripe
    context->set_stripe(stripe);
    flush(Thread::current());
  } else if (!_terminate.saturated()) {
    // Work imbalance detected; striped marking is likely going to be in the way
    flush(Thread::current());
  }

  SuspendibleThreadSet::yield();

  return ZAbort::should_abort() || _generation->should_worker_resize();
}

bool ZMark::drain(ZMarkContext* context) {
  ZMarkThreadLocalStacks* const stacks = context->stacks();
  ZMarkStackEntry entry;
  size_t processed = 0;

  context->set_stripe(_stripes.stripe_for_worker(_nworkers, WorkerThread::worker_id()));
  context->set_nstripes(_stripes.nstripes());

  // Drain stripe stacks
  while (stacks->pop(&_marking_smr, &_stripes, context->stripe(), &entry)) {
    mark_and_follow(context, entry);

    if ((processed++ & 31) == 0 && rebalance_work(context)) {
      return false;
    }
  }

  return true;
}

bool ZMark::try_steal_local(ZMarkContext* context) {
  ZMarkStripe* const stripe = context->stripe();
  ZMarkThreadLocalStacks* const stacks = context->stacks();

  // Try to steal a local stack from another stripe
  for (ZMarkStripe* victim_stripe = _stripes.stripe_next(stripe);
       victim_stripe != stripe;
       victim_stripe = _stripes.stripe_next(victim_stripe)) {
    ZMarkStack* const stack = stacks->steal(&_stripes, victim_stripe);
    if (stack != nullptr) {
      // Success, install the stolen stack
      stacks->install(&_stripes, stripe, stack);
      return true;
    }
  }

  // Nothing to steal
  return false;
}

bool ZMark::try_steal_global(ZMarkContext* context) {
  ZMarkStripe* const stripe = context->stripe();
  ZMarkThreadLocalStacks* const stacks = context->stacks();

  // Try to steal a stack from another stripe
  for (ZMarkStripe* victim_stripe = _stripes.stripe_next(stripe);
       victim_stripe != stripe;
       victim_stripe = _stripes.stripe_next(victim_stripe)) {
    ZMarkStack* const stack = victim_stripe->steal_stack(&_marking_smr);
    if (stack != nullptr) {
      // Success, install the stolen stack
      stacks->install(&_stripes, stripe, stack);
      return true;
    }
  }

  // Nothing to steal
  return false;
}

bool ZMark::try_steal(ZMarkContext* context) {
  return try_steal_local(context) || try_steal_global(context);
}

class ZMarkFlushStacksHandshakeClosure : public HandshakeClosure {
private:
  ZMark* const _mark;
  bool         _flushed;

public:
  ZMarkFlushStacksHandshakeClosure(ZMark* mark)
    : HandshakeClosure("ZMarkFlushStacks"),
      _mark(mark),
      _flushed(false) {}

  void do_thread(Thread* thread) {
    if (_mark->flush(thread)) {
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

public:
  VM_ZMarkFlushOperation(ThreadClosure* cl)
    : _cl(cl) {}

  virtual bool evaluate_at_safepoint() const {
    return false;
  }

  virtual void doit() {
    // Flush VM thread
    Thread* const thread = Thread::current();
    _cl->do_thread(thread);
  }

  virtual VMOp_Type type() const {
    return VMOp_ZMarkFlushOperation;
  }

  virtual bool is_gc_operation() const {
    return true;
  }
};

bool ZMark::flush() {
  ZMarkFlushStacksHandshakeClosure cl(this);
  VM_ZMarkFlushOperation vm_cl(&cl);
  Handshake::execute(&cl);
  VMThread::execute(&vm_cl);

  // Returns true if more work is available
  return cl.flushed() || !_stripes.is_empty();
}

bool ZMark::try_terminate_flush() {
  Atomic::inc(&_work_nterminateflush);
  _terminate.set_resurrected(false);

  if (ZVerifyMarking) {
    verify_worker_stacks_empty();
  }

  return flush() || _terminate.resurrected();
}

bool ZMark::try_proactive_flush() {
  // Only do proactive flushes from worker 0
  if (WorkerThread::worker_id() != 0) {
    return false;
  }

  if (Atomic::load(&_work_nproactiveflush) == ZMarkProactiveFlushMax) {
    // Limit reached or we're trying to terminate
    return false;
  }

  Atomic::inc(&_work_nproactiveflush);

  SuspendibleThreadSetLeaver sts_leaver;
  return flush();
}

bool ZMark::try_terminate(ZMarkContext* context) {
  return _terminate.try_terminate(&_stripes, context->nstripes());
}

void ZMark::leave() {
  _terminate.leave();
}

// Returning true means marking finished successfully after marking as far as it could.
// Returning false means that marking finished unsuccessfully due to abort or resizing.
bool ZMark::follow_work(bool partial) {
  ZMarkStripe* const stripe = _stripes.stripe_for_worker(_nworkers, WorkerThread::worker_id());
  ZMarkThreadLocalStacks* const stacks = ZThreadLocalData::mark_stacks(Thread::current(), _generation->id());
  ZMarkContext context(ZMarkStripesMax, stripe, stacks);

  for (;;) {
    if (!drain(&context)) {
      leave();
      return false;
    }

    if (try_steal(&context)) {
      // Stole work
      continue;
    }

    if (partial) {
      return true;
    }

    if (try_proactive_flush()) {
      // Work available
      continue;
    }

    if (try_terminate(&context)) {
      // Terminate
      return true;
    }
  }
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
    ZBarrier::mark_young_good_barrier_on_oop_field((zpointer*)p);
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
  ZMarkNMethodClosure()
    : _bs_nm(static_cast<ZBarrierSetNMethod*>(BarrierSet::barrier_set()->barrier_set_nmethod())) {}

  virtual void do_nmethod(nmethod* nm) {
    ZLocker<ZReentrantLock> locker(ZNMethod::lock_for_nmethod(nm));
    if (_bs_nm->is_armed(nm)) {
      // Heal barriers
      ZNMethod::nmethod_patch_barriers(nm);

      // Heal oops
      ZUncoloredRootMarkOopClosure cl(ZNMethod::color(nm));
      ZNMethod::nmethod_oops_do_inner(nm, &cl);

      // CodeCache unloading support
      nm->mark_as_maybe_on_stack();

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
  ZMarkYoungNMethodClosure()
    : _bs_nm(static_cast<ZBarrierSetNMethod*>(BarrierSet::barrier_set()->barrier_set_nmethod())) {}

  virtual void do_nmethod(nmethod* nm) {
    ZLocker<ZReentrantLock> locker(ZNMethod::lock_for_nmethod(nm));
    if (nm->is_unloading()) {
      return;
    }

    if (_bs_nm->is_armed(nm)) {
      const uintptr_t prev_color = ZNMethod::color(nm);

      // Heal oops
      ZUncoloredRootMarkYoungOopClosure cl(prev_color);
      ZNMethod::nmethod_oops_do_inner(nm, &cl);

      // Disarm only the young marking, not any potential old marking cycle

      const uintptr_t old_marked_mask = ZPointerMarkedMask ^ (ZPointerMarkedYoung0 | ZPointerMarkedYoung1);
      const uintptr_t old_marked = prev_color & old_marked_mask;

      const zpointer new_disarm_value_ptr = ZAddress::color(zaddress::null, ZPointerLoadGoodMask | ZPointerMarkedYoung | old_marked | ZPointerRemembered);

      // Check if disarming for young mark, completely disarms the nmethod entry barrier
      const bool complete_disarm = ZPointer::is_store_good(new_disarm_value_ptr);

      if (complete_disarm) {
        // We are about to completely disarm the nmethod, must take responsibility to patch all barriers before disarming
        ZNMethod::nmethod_patch_barriers(nm);
      }

      _bs_nm->guard_with(nm, (int)untype(new_disarm_value_ptr));

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

typedef ClaimingCLDToOopClosure<ClassLoaderData::_claim_strong> ZMarkOldCLDClosure;

class ZMarkOldRootsTask : public ZTask {
private:
  ZMark* const                  _mark;
  ZRootsIteratorStrongColored   _roots_colored;
  ZRootsIteratorStrongUncolored _roots_uncolored;

  ZMarkOopClosure               _cl_colored;
  ZMarkOldCLDClosure            _cld_cl;

  ZMarkThreadClosure            _thread_cl;
  ZMarkNMethodClosure           _nm_cl;

public:
  ZMarkOldRootsTask(ZMark* mark)
    : ZTask("ZMarkOldRootsTask"),
      _mark(mark),
      _roots_colored(ZGenerationIdOptional::old),
      _roots_uncolored(ZGenerationIdOptional::old),
      _cl_colored(),
      _cld_cl(&_cl_colored),
      _thread_cl(),
      _nm_cl() {}

  virtual void work() {
    {
      ZStatTimerWorker timer(ZSubPhaseConcurrentMarkRootColoredOld);
      _roots_colored.apply(&_cl_colored,
                           &_cld_cl);
    }

    {
      ZStatTimerWorker timer(ZSubPhaseConcurrentMarkRootUncoloredOld);
      _roots_uncolored.apply(&_thread_cl,
                             &_nm_cl);
    }

    // Flush and free worker stacks. Needed here since
    // the set of workers executing during root scanning
    // can be different from the set of workers executing
    // during mark.
    ZHeap::heap()->mark_flush(Thread::current());
  }
};

class ZMarkYoungCLDClosure : public ClaimingCLDToOopClosure<ClassLoaderData::_claim_none> {
public:
  virtual void do_cld(ClassLoaderData* cld) {
    if (!cld->is_alive()) {
      // Skip marking through concurrently unloading CLDs
      return;
    }
    ClaimingCLDToOopClosure<ClassLoaderData::_claim_none>::do_cld(cld);
  }

  ZMarkYoungCLDClosure(OopClosure* cl)
    : ClaimingCLDToOopClosure<ClassLoaderData::_claim_none>(cl) {}
};

class ZMarkYoungRootsTask : public ZTask {
private:
  ZMark* const               _mark;
  ZRootsIteratorAllColored   _roots_colored;
  ZRootsIteratorAllUncolored _roots_uncolored;

  ZMarkYoungOopClosure       _cl_colored;
  ZMarkYoungCLDClosure       _cld_cl;

  ZMarkThreadClosure         _thread_cl;
  ZMarkYoungNMethodClosure   _nm_cl;

public:
  ZMarkYoungRootsTask(ZMark* mark)
    : ZTask("ZMarkYoungRootsTask"),
      _mark(mark),
      _roots_colored(ZGenerationIdOptional::young),
      _roots_uncolored(ZGenerationIdOptional::young),
      _cl_colored(),
      _cld_cl(&_cl_colored),
      _thread_cl(),
      _nm_cl() {}

  virtual void work() {
    {
      ZStatTimerWorker timer(ZSubPhaseConcurrentMarkRootColoredYoung);
      _roots_colored.apply(&_cl_colored,
                           &_cld_cl);
    }

    {
      ZStatTimerWorker timer(ZSubPhaseConcurrentMarkRootUncoloredYoung);
      _roots_uncolored.apply(&_thread_cl,
                             &_nm_cl);
    }

    // Flush and free worker stacks. Needed here since
    // the set of workers executing during root scanning
    // can be different from the set of workers executing
    // during mark.
    ZHeap::heap()->mark_flush(Thread::current());
  }
};

class ZMarkTask : public ZRestartableTask {
private:
  ZMark* const _mark;

public:
  ZMarkTask(ZMark* mark)
    : ZRestartableTask("ZMarkTask"),
      _mark(mark) {
    _mark->prepare_work();
  }

  ~ZMarkTask() {
    _mark->finish_work();
  }

  virtual void work() {
    SuspendibleThreadSetJoiner sts_joiner;
    _mark->follow_work_complete();
    // We might have found pointers into the other generation, and then we want to
    // publish such marking stacks to prevent that generation from getting a mark continue.
    // We also flush in case of a resize where a new worker thread continues the marking
    // work, causing a mark continue for the collected generation.
    ZHeap::heap()->mark_flush(Thread::current());
  }

  virtual void resize_workers(uint nworkers) {
    _mark->resize_workers(nworkers);
  }
};

void ZMark::resize_workers(uint nworkers) {
  _nworkers = nworkers;
  const size_t nstripes = calculate_nstripes(nworkers);
  _stripes.set_nstripes(nstripes);
  _terminate.reset(nworkers);
}

void ZMark::mark_young_roots() {
  SuspendibleThreadSetJoiner sts_joiner;
  ZMarkYoungRootsTask task(this);
  workers()->run(&task);
}

void ZMark::mark_old_roots() {
  SuspendibleThreadSetJoiner sts_joiner;
  ZMarkOldRootsTask task(this);
  workers()->run(&task);
}

void ZMark::mark_follow() {
  for (;;) {
    ZMarkTask task(this);
    workers()->run(&task);
    if (ZAbort::should_abort() || !try_terminate_flush()) {
      break;
    }
  }
}

bool ZMark::try_end() {
  if (_terminate.resurrected()) {
    // An oop was resurrected after concurrent termination.
    return false;
  }

  // Try end marking
  ZMarkFlushStacksHandshakeClosure cl(this);
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
  _generation->stat_mark()->at_mark_end(_nproactiveflush, _nterminateflush, _ntrycomplete, _ncontinue);

  // Mark completed
  return true;
}

void ZMark::free() {
  // Free any unused mark stack space
  _marking_smr.free();
}

bool ZMark::flush(Thread* thread) {
  if (thread->is_Java_thread()) {
    ZThreadLocalData::store_barrier_buffer(thread)->flush();
  }
  ZMarkThreadLocalStacks* const stacks = ZThreadLocalData::mark_stacks(thread, _generation->id());
  return stacks->flush(&_stripes, &_terminate);
}

class ZVerifyMarkStacksEmptyClosure : public ThreadClosure {
private:
  const ZMarkStripeSet* const _stripes;
  const ZGenerationId _generation_id;

public:
  ZVerifyMarkStacksEmptyClosure(const ZMarkStripeSet* stripes, ZGenerationId id)
    : _stripes(stripes),
      _generation_id(id) {}

  void do_thread(Thread* thread) {
    ZMarkThreadLocalStacks* const stacks = ZThreadLocalData::mark_stacks(thread, _generation_id);
    guarantee(stacks->is_empty(_stripes), "Should be empty");
  }
};

void ZMark::verify_all_stacks_empty() const {
  // Verify thread stacks
  ZVerifyMarkStacksEmptyClosure cl(&_stripes, _generation->id());
  Threads::threads_do(&cl);

  // Verify stripe stacks
  guarantee(_stripes.is_empty(), "Should be empty");
}

void ZMark::verify_worker_stacks_empty() const {
  // Verify thread stacks
  ZVerifyMarkStacksEmptyClosure cl(&_stripes, _generation->id());
  workers()->threads_do(&cl);
}
