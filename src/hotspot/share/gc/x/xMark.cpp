/*
 * Copyright (c) 2015, 2022, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/javaClasses.inline.hpp"
#include "code/nmethod.hpp"
#include "gc/shared/continuationGCSupport.inline.hpp"
#include "gc/shared/gc_globals.hpp"
#include "gc/shared/stringdedup/stringDedup.hpp"
#include "gc/shared/suspendibleThreadSet.hpp"
#include "gc/x/xAbort.inline.hpp"
#include "gc/x/xBarrier.inline.hpp"
#include "gc/x/xHeap.inline.hpp"
#include "gc/x/xLock.inline.hpp"
#include "gc/x/xMark.inline.hpp"
#include "gc/x/xMarkCache.inline.hpp"
#include "gc/x/xMarkContext.inline.hpp"
#include "gc/x/xMarkStack.inline.hpp"
#include "gc/x/xMarkTerminate.inline.hpp"
#include "gc/x/xNMethod.hpp"
#include "gc/x/xOop.inline.hpp"
#include "gc/x/xPage.hpp"
#include "gc/x/xPageTable.inline.hpp"
#include "gc/x/xRootsIterator.hpp"
#include "gc/x/xStackWatermark.hpp"
#include "gc/x/xStat.hpp"
#include "gc/x/xTask.hpp"
#include "gc/x/xThread.inline.hpp"
#include "gc/x/xThreadLocalAllocBuffer.hpp"
#include "gc/x/xUtils.inline.hpp"
#include "gc/x/xWorkers.hpp"
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
#include "utilities/align.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/powerOfTwo.hpp"
#include "utilities/ticks.hpp"

static const XStatSubPhase XSubPhaseConcurrentMark("Concurrent Mark");
static const XStatSubPhase XSubPhaseConcurrentMarkTryFlush("Concurrent Mark Try Flush");
static const XStatSubPhase XSubPhaseConcurrentMarkTryTerminate("Concurrent Mark Try Terminate");
static const XStatSubPhase XSubPhaseMarkTryComplete("Pause Mark Try Complete");

XMark::XMark(XWorkers* workers, XPageTable* page_table) :
    _workers(workers),
    _page_table(page_table),
    _allocator(),
    _stripes(),
    _terminate(),
    _work_terminateflush(true),
    _work_nproactiveflush(0),
    _work_nterminateflush(0),
    _nproactiveflush(0),
    _nterminateflush(0),
    _ntrycomplete(0),
    _ncontinue(0),
    _nworkers(0) {}

bool XMark::is_initialized() const {
  return _allocator.is_initialized();
}

size_t XMark::calculate_nstripes(uint nworkers) const {
  // Calculate the number of stripes from the number of workers we use,
  // where the number of stripes must be a power of two and we want to
  // have at least one worker per stripe.
  const size_t nstripes = round_down_power_of_2(nworkers);
  return MIN2(nstripes, XMarkStripesMax);
}

void XMark::start() {
  // Verification
  if (ZVerifyMarking) {
    verify_all_stacks_empty();
  }

  // Increment global sequence number to invalidate
  // marking information for all pages.
  XGlobalSeqNum++;

  // Note that we start a marking cycle.
  // Unlike other GCs, the color switch implicitly changes the nmethods
  // to be armed, and the thread-local disarm values are lazily updated
  // when JavaThreads wake up from safepoints.
  CodeCache::on_gc_marking_cycle_start();

  // Reset flush/continue counters
  _nproactiveflush = 0;
  _nterminateflush = 0;
  _ntrycomplete = 0;
  _ncontinue = 0;

  // Set number of workers to use
  _nworkers = _workers->active_workers();

  // Set number of mark stripes to use, based on number
  // of workers we will use in the concurrent mark phase.
  const size_t nstripes = calculate_nstripes(_nworkers);
  _stripes.set_nstripes(nstripes);

  // Update statistics
  XStatMark::set_at_mark_start(nstripes);

  // Print worker/stripe distribution
  LogTarget(Debug, gc, marking) log;
  if (log.is_enabled()) {
    log.print("Mark Worker/Stripe Distribution");
    for (uint worker_id = 0; worker_id < _nworkers; worker_id++) {
      const XMarkStripe* const stripe = _stripes.stripe_for_worker(_nworkers, worker_id);
      const size_t stripe_id = _stripes.stripe_id(stripe);
      log.print("  Worker %u(%u) -> Stripe " SIZE_FORMAT "(" SIZE_FORMAT ")",
                worker_id, _nworkers, stripe_id, nstripes);
    }
  }
}

void XMark::prepare_work() {
  assert(_nworkers == _workers->active_workers(), "Invalid number of workers");

  // Set number of active workers
  _terminate.reset(_nworkers);

  // Reset flush counters
  _work_nproactiveflush = _work_nterminateflush = 0;
  _work_terminateflush = true;
}

void XMark::finish_work() {
  // Accumulate proactive/terminate flush counters
  _nproactiveflush += _work_nproactiveflush;
  _nterminateflush += _work_nterminateflush;
}

bool XMark::is_array(uintptr_t addr) const {
  return XOop::from_address(addr)->is_objArray();
}

void XMark::push_partial_array(uintptr_t addr, size_t size, bool finalizable) {
  assert(is_aligned(addr, XMarkPartialArrayMinSize), "Address misaligned");
  XMarkThreadLocalStacks* const stacks = XThreadLocalData::stacks(Thread::current());
  XMarkStripe* const stripe = _stripes.stripe_for_addr(addr);
  const uintptr_t offset = XAddress::offset(addr) >> XMarkPartialArrayMinSizeShift;
  const uintptr_t length = size / oopSize;
  const XMarkStackEntry entry(offset, length, finalizable);

  log_develop_trace(gc, marking)("Array push partial: " PTR_FORMAT " (" SIZE_FORMAT "), stripe: " SIZE_FORMAT,
                                 addr, size, _stripes.stripe_id(stripe));

  stacks->push(&_allocator, &_stripes, stripe, entry, false /* publish */);
}

void XMark::follow_small_array(uintptr_t addr, size_t size, bool finalizable) {
  assert(size <= XMarkPartialArrayMinSize, "Too large, should be split");
  const size_t length = size / oopSize;

  log_develop_trace(gc, marking)("Array follow small: " PTR_FORMAT " (" SIZE_FORMAT ")", addr, size);

  XBarrier::mark_barrier_on_oop_array((oop*)addr, length, finalizable);
}

void XMark::follow_large_array(uintptr_t addr, size_t size, bool finalizable) {
  assert(size <= (size_t)arrayOopDesc::max_array_length(T_OBJECT) * oopSize, "Too large");
  assert(size > XMarkPartialArrayMinSize, "Too small, should not be split");
  const uintptr_t start = addr;
  const uintptr_t end = start + size;

  // Calculate the aligned middle start/end/size, where the middle start
  // should always be greater than the start (hence the +1 below) to make
  // sure we always do some follow work, not just split the array into pieces.
  const uintptr_t middle_start = align_up(start + 1, XMarkPartialArrayMinSize);
  const size_t    middle_size = align_down(end - middle_start, XMarkPartialArrayMinSize);
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
    const size_t partial_size = align_up((partial_addr - middle_start) / parts, XMarkPartialArrayMinSize);
    partial_addr -= partial_size;
    push_partial_array(partial_addr, partial_size, finalizable);
  }

  // Follow leading part
  assert(start < middle_start, "Miscalculated middle start");
  const uintptr_t leading_addr = start;
  const size_t leading_size = middle_start - start;
  follow_small_array(leading_addr, leading_size, finalizable);
}

void XMark::follow_array(uintptr_t addr, size_t size, bool finalizable) {
  if (size <= XMarkPartialArrayMinSize) {
    follow_small_array(addr, size, finalizable);
  } else {
    follow_large_array(addr, size, finalizable);
  }
}

void XMark::follow_partial_array(XMarkStackEntry entry, bool finalizable) {
  const uintptr_t addr = XAddress::good(entry.partial_array_offset() << XMarkPartialArrayMinSizeShift);
  const size_t size = entry.partial_array_length() * oopSize;

  follow_array(addr, size, finalizable);
}

template <bool finalizable>
class XMarkBarrierOopClosure : public ClaimMetadataVisitingOopIterateClosure {
public:
  XMarkBarrierOopClosure() :
      ClaimMetadataVisitingOopIterateClosure(finalizable
                                                 ? ClassLoaderData::_claim_finalizable
                                                 : ClassLoaderData::_claim_strong,
                                             finalizable
                                                 ? nullptr
                                                 : XHeap::heap()->reference_discoverer()) {}

  virtual void do_oop(oop* p) {
    XBarrier::mark_barrier_on_oop_field(p, finalizable);
  }

  virtual void do_oop(narrowOop* p) {
    ShouldNotReachHere();
  }

  virtual void do_nmethod(nmethod* nm) {
    assert(!finalizable, "Can't handle finalizable marking of nmethods");
    nm->run_nmethod_entry_barrier();
  }
};

void XMark::follow_array_object(objArrayOop obj, bool finalizable) {
  if (finalizable) {
    XMarkBarrierOopClosure<true /* finalizable */> cl;
    cl.do_klass(obj->klass());
  } else {
    XMarkBarrierOopClosure<false /* finalizable */> cl;
    cl.do_klass(obj->klass());
  }

  const uintptr_t addr = (uintptr_t)obj->base();
  const size_t size = (size_t)obj->length() * oopSize;

  follow_array(addr, size, finalizable);
}

void XMark::follow_object(oop obj, bool finalizable) {
  if (ContinuationGCSupport::relativize_stack_chunk(obj)) {
    // Loom doesn't support mixing of finalizable marking and strong marking of
    // stack chunks. See: RelativizeDerivedOopClosure.
    XMarkBarrierOopClosure<false /* finalizable */> cl;
    obj->oop_iterate(&cl);
    return;
  }

  if (finalizable) {
    XMarkBarrierOopClosure<true /* finalizable */> cl;
    obj->oop_iterate(&cl);
  } else {
    XMarkBarrierOopClosure<false /* finalizable */> cl;
    obj->oop_iterate(&cl);
  }
}

static void try_deduplicate(XMarkContext* context, oop obj) {
  if (!StringDedup::is_enabled()) {
    // Not enabled
    return;
  }

  if (!java_lang_String::is_instance(obj)) {
    // Not a String object
    return;
  }

  if (java_lang_String::test_and_set_deduplication_requested(obj)) {
    // Already requested deduplication
    return;
  }

  // Request deduplication
  context->string_dedup_requests()->add(obj);
}

void XMark::mark_and_follow(XMarkContext* context, XMarkStackEntry entry) {
  // Decode flags
  const bool finalizable = entry.finalizable();
  const bool partial_array = entry.partial_array();

  if (partial_array) {
    follow_partial_array(entry, finalizable);
    return;
  }

  // Decode object address and additional flags
  const uintptr_t addr = entry.object_address();
  const bool mark = entry.mark();
  bool inc_live = entry.inc_live();
  const bool follow = entry.follow();

  XPage* const page = _page_table->get(addr);
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
    const size_t size = XUtils::object_size(addr);
    const size_t aligned_size = align_up(size, page->object_alignment());
    context->cache()->inc_live(page, aligned_size);
  }

  // Follow
  if (follow) {
    if (is_array(addr)) {
      follow_array_object(objArrayOop(XOop::from_address(addr)), finalizable);
    } else {
      const oop obj = XOop::from_address(addr);
      follow_object(obj, finalizable);

      if (!finalizable) {
        // Try deduplicate
        try_deduplicate(context, obj);
      }
    }
  }
}

template <typename T>
bool XMark::drain(XMarkContext* context, T* timeout) {
  XMarkStripe* const stripe = context->stripe();
  XMarkThreadLocalStacks* const stacks = context->stacks();
  XMarkStackEntry entry;

  // Drain stripe stacks
  while (stacks->pop(&_allocator, &_stripes, stripe, entry)) {
    mark_and_follow(context, entry);

    // Check timeout
    if (timeout->has_expired()) {
      // Timeout
      return false;
    }
  }

  // Success
  return !timeout->has_expired();
}

bool XMark::try_steal_local(XMarkContext* context) {
  XMarkStripe* const stripe = context->stripe();
  XMarkThreadLocalStacks* const stacks = context->stacks();

  // Try to steal a local stack from another stripe
  for (XMarkStripe* victim_stripe = _stripes.stripe_next(stripe);
       victim_stripe != stripe;
       victim_stripe = _stripes.stripe_next(victim_stripe)) {
    XMarkStack* const stack = stacks->steal(&_stripes, victim_stripe);
    if (stack != nullptr) {
      // Success, install the stolen stack
      stacks->install(&_stripes, stripe, stack);
      return true;
    }
  }

  // Nothing to steal
  return false;
}

bool XMark::try_steal_global(XMarkContext* context) {
  XMarkStripe* const stripe = context->stripe();
  XMarkThreadLocalStacks* const stacks = context->stacks();

  // Try to steal a stack from another stripe
  for (XMarkStripe* victim_stripe = _stripes.stripe_next(stripe);
       victim_stripe != stripe;
       victim_stripe = _stripes.stripe_next(victim_stripe)) {
    XMarkStack* const stack = victim_stripe->steal_stack();
    if (stack != nullptr) {
      // Success, install the stolen stack
      stacks->install(&_stripes, stripe, stack);
      return true;
    }
  }

  // Nothing to steal
  return false;
}

bool XMark::try_steal(XMarkContext* context) {
  return try_steal_local(context) || try_steal_global(context);
}

void XMark::idle() const {
  os::naked_short_sleep(1);
}

class XMarkFlushAndFreeStacksClosure : public HandshakeClosure {
private:
  XMark* const _mark;
  bool         _flushed;

public:
  XMarkFlushAndFreeStacksClosure(XMark* mark) :
      HandshakeClosure("XMarkFlushAndFreeStacks"),
      _mark(mark),
      _flushed(false) {}

  void do_thread(Thread* thread) {
    if (_mark->flush_and_free(thread)) {
      _flushed = true;
    }
  }

  bool flushed() const {
    return _flushed;
  }
};

bool XMark::flush(bool at_safepoint) {
  XMarkFlushAndFreeStacksClosure cl(this);
  if (at_safepoint) {
    Threads::threads_do(&cl);
  } else {
    Handshake::execute(&cl);
  }

  // Returns true if more work is available
  return cl.flushed() || !_stripes.is_empty();
}

bool XMark::try_flush(volatile size_t* nflush) {
  Atomic::inc(nflush);

  XStatTimer timer(XSubPhaseConcurrentMarkTryFlush);
  return flush(false /* at_safepoint */);
}

bool XMark::try_proactive_flush() {
  // Only do proactive flushes from worker 0
  if (XThread::worker_id() != 0) {
    return false;
  }

  if (Atomic::load(&_work_nproactiveflush) == XMarkProactiveFlushMax ||
      Atomic::load(&_work_nterminateflush) != 0) {
    // Limit reached or we're trying to terminate
    return false;
  }

  return try_flush(&_work_nproactiveflush);
}

bool XMark::try_terminate() {
  XStatTimer timer(XSubPhaseConcurrentMarkTryTerminate);

  if (_terminate.enter_stage0()) {
    // Last thread entered stage 0, flush
    if (Atomic::load(&_work_terminateflush) &&
        Atomic::load(&_work_nterminateflush) != XMarkTerminateFlushMax) {
      // Exit stage 0 to allow other threads to continue marking
      _terminate.exit_stage0();

      // Flush before termination
      if (!try_flush(&_work_nterminateflush)) {
        // No more work available, skip further flush attempts
        Atomic::store(&_work_terminateflush, false);
      }

      // Don't terminate, regardless of whether we successfully
      // flushed out more work or not. We've already exited
      // termination stage 0, to allow other threads to continue
      // marking, so this thread has to return false and also
      // make another round of attempted marking.
      return false;
    }
  }

  for (;;) {
    if (_terminate.enter_stage1()) {
      // Last thread entered stage 1, terminate
      return true;
    }

    // Idle to give the other threads
    // a chance to enter termination.
    idle();

    if (!_terminate.try_exit_stage1()) {
      // All workers in stage 1, terminate
      return true;
    }

    if (_terminate.try_exit_stage0()) {
      // More work available, don't terminate
      return false;
    }
  }
}

class XMarkNoTimeout : public StackObj {
public:
  bool has_expired() {
    // No timeout, but check for signal to abort
    return XAbort::should_abort();
  }
};

void XMark::work_without_timeout(XMarkContext* context) {
  XStatTimer timer(XSubPhaseConcurrentMark);
  XMarkNoTimeout no_timeout;

  for (;;) {
    if (!drain(context, &no_timeout)) {
      // Abort
      break;
    }

    if (try_steal(context)) {
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
}

class XMarkTimeout : public StackObj {
private:
  const Ticks    _start;
  const uint64_t _timeout;
  const uint64_t _check_interval;
  uint64_t       _check_at;
  uint64_t       _check_count;
  bool           _expired;

public:
  XMarkTimeout(uint64_t timeout_in_micros) :
      _start(Ticks::now()),
      _timeout(_start.value() + TimeHelper::micros_to_counter(timeout_in_micros)),
      _check_interval(200),
      _check_at(_check_interval),
      _check_count(0),
      _expired(false) {}

  ~XMarkTimeout() {
    const Tickspan duration = Ticks::now() - _start;
    log_debug(gc, marking)("Mark With Timeout (%s): %s, " UINT64_FORMAT " oops, %.3fms",
                           XThread::name(), _expired ? "Expired" : "Completed",
                           _check_count, TimeHelper::counter_to_millis(duration.value()));
  }

  bool has_expired() {
    if (++_check_count == _check_at) {
      _check_at += _check_interval;
      if ((uint64_t)Ticks::now().value() >= _timeout) {
        // Timeout
        _expired = true;
      }
    }

    return _expired;
  }
};

void XMark::work_with_timeout(XMarkContext* context, uint64_t timeout_in_micros) {
  XStatTimer timer(XSubPhaseMarkTryComplete);
  XMarkTimeout timeout(timeout_in_micros);

  for (;;) {
    if (!drain(context, &timeout)) {
      // Timed out
      break;
    }

    if (try_steal(context)) {
      // Stole work
      continue;
    }

    // Terminate
    break;
  }
}

void XMark::work(uint64_t timeout_in_micros) {
  XMarkStripe* const stripe = _stripes.stripe_for_worker(_nworkers, XThread::worker_id());
  XMarkThreadLocalStacks* const stacks = XThreadLocalData::stacks(Thread::current());
  XMarkContext context(_stripes.nstripes(), stripe, stacks);

  if (timeout_in_micros == 0) {
    work_without_timeout(&context);
  } else {
    work_with_timeout(&context, timeout_in_micros);
  }

  // Flush and publish stacks
  stacks->flush(&_allocator, &_stripes);

  // Free remaining stacks
  stacks->free(&_allocator);
}

class XMarkOopClosure : public OopClosure {
  virtual void do_oop(oop* p) {
    XBarrier::mark_barrier_on_oop_field(p, false /* finalizable */);
  }

  virtual void do_oop(narrowOop* p) {
    ShouldNotReachHere();
  }
};

class XMarkThreadClosure : public ThreadClosure {
private:
  OopClosure* const _cl;

public:
  XMarkThreadClosure(OopClosure* cl) :
      _cl(cl) {
    XThreadLocalAllocBuffer::reset_statistics();
  }
  ~XMarkThreadClosure() {
    XThreadLocalAllocBuffer::publish_statistics();
  }
  virtual void do_thread(Thread* thread) {
    JavaThread* const jt = JavaThread::cast(thread);
    StackWatermarkSet::finish_processing(jt, _cl, StackWatermarkKind::gc);
    XThreadLocalAllocBuffer::update_stats(jt);
  }
};

class XMarkNMethodClosure : public NMethodClosure {
private:
  OopClosure* const _cl;

public:
  XMarkNMethodClosure(OopClosure* cl) :
      _cl(cl) {}

  virtual void do_nmethod(nmethod* nm) {
    XLocker<XReentrantLock> locker(XNMethod::lock_for_nmethod(nm));
    if (XNMethod::is_armed(nm)) {
      XNMethod::nmethod_oops_do_inner(nm, _cl);

      // CodeCache unloading support
      nm->mark_as_maybe_on_stack();

      XNMethod::disarm(nm);
    }
  }
};

typedef ClaimingCLDToOopClosure<ClassLoaderData::_claim_strong> XMarkCLDClosure;

class XMarkRootsTask : public XTask {
private:
  XMark* const               _mark;
  SuspendibleThreadSetJoiner _sts_joiner;
  XRootsIterator             _roots;

  XMarkOopClosure            _cl;
  XMarkCLDClosure            _cld_cl;
  XMarkThreadClosure         _thread_cl;
  XMarkNMethodClosure        _nm_cl;

public:
  XMarkRootsTask(XMark* mark) :
      XTask("XMarkRootsTask"),
      _mark(mark),
      _sts_joiner(),
      _roots(ClassLoaderData::_claim_strong),
      _cl(),
      _cld_cl(&_cl),
      _thread_cl(&_cl),
      _nm_cl(&_cl) {
    ClassLoaderDataGraph_lock->lock();
  }

  ~XMarkRootsTask() {
    ClassLoaderDataGraph_lock->unlock();
  }

  virtual void work() {
    _roots.apply(&_cl,
                 &_cld_cl,
                 &_thread_cl,
                 &_nm_cl);

    // Flush and free worker stacks. Needed here since
    // the set of workers executing during root scanning
    // can be different from the set of workers executing
    // during mark.
    _mark->flush_and_free();
  }
};

class XMarkTask : public XTask {
private:
  XMark* const   _mark;
  const uint64_t _timeout_in_micros;

public:
  XMarkTask(XMark* mark, uint64_t timeout_in_micros = 0) :
      XTask("XMarkTask"),
      _mark(mark),
      _timeout_in_micros(timeout_in_micros) {
    _mark->prepare_work();
  }

  ~XMarkTask() {
    _mark->finish_work();
  }

  virtual void work() {
    _mark->work(_timeout_in_micros);
  }
};

void XMark::mark(bool initial) {
  if (initial) {
    XMarkRootsTask task(this);
    _workers->run(&task);
  }

  XMarkTask task(this);
  _workers->run(&task);
}

bool XMark::try_complete() {
  _ntrycomplete++;

  // Use nconcurrent number of worker threads to maintain the
  // worker/stripe distribution used during concurrent mark.
  XMarkTask task(this, XMarkCompleteTimeout);
  _workers->run(&task);

  // Successful if all stripes are empty
  return _stripes.is_empty();
}

bool XMark::try_end() {
  // Flush all mark stacks
  if (!flush(true /* at_safepoint */)) {
    // Mark completed
    return true;
  }

  // Try complete marking by doing a limited
  // amount of mark work in this phase.
  return try_complete();
}

bool XMark::end() {
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
  XStatMark::set_at_mark_end(_nproactiveflush, _nterminateflush, _ntrycomplete, _ncontinue);

  // Note that we finished a marking cycle.
  // Unlike other GCs, we do not arm the nmethods
  // when marking terminates.
  CodeCache::on_gc_marking_cycle_finish();

  // Mark completed
  return true;
}

void XMark::free() {
  // Free any unused mark stack space
  _allocator.free();

  // Update statistics
  XStatMark::set_at_mark_free(_allocator.size());
}

void XMark::flush_and_free() {
  Thread* const thread = Thread::current();
  flush_and_free(thread);
}

bool XMark::flush_and_free(Thread* thread) {
  XMarkThreadLocalStacks* const stacks = XThreadLocalData::stacks(thread);
  const bool flushed = stacks->flush(&_allocator, &_stripes);
  stacks->free(&_allocator);
  return flushed;
}

class XVerifyMarkStacksEmptyClosure : public ThreadClosure {
private:
  const XMarkStripeSet* const _stripes;

public:
  XVerifyMarkStacksEmptyClosure(const XMarkStripeSet* stripes) :
      _stripes(stripes) {}

  void do_thread(Thread* thread) {
    XMarkThreadLocalStacks* const stacks = XThreadLocalData::stacks(thread);
    guarantee(stacks->is_empty(_stripes), "Should be empty");
  }
};

void XMark::verify_all_stacks_empty() const {
  // Verify thread stacks
  XVerifyMarkStacksEmptyClosure cl(&_stripes);
  Threads::threads_do(&cl);

  // Verify stripe stacks
  guarantee(_stripes.is_empty(), "Should be empty");
}
