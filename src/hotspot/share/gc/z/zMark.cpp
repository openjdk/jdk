/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/z/zBarrier.inline.hpp"
#include "gc/z/zMark.inline.hpp"
#include "gc/z/zMarkCache.inline.hpp"
#include "gc/z/zMarkStack.inline.hpp"
#include "gc/z/zMarkTerminate.inline.hpp"
#include "gc/z/zOopClosures.inline.hpp"
#include "gc/z/zPage.hpp"
#include "gc/z/zPageTable.inline.hpp"
#include "gc/z/zRootsIterator.hpp"
#include "gc/z/zStat.hpp"
#include "gc/z/zStatTLAB.hpp"
#include "gc/z/zTask.hpp"
#include "gc/z/zThread.hpp"
#include "gc/z/zUtils.inline.hpp"
#include "gc/z/zWorkers.inline.hpp"
#include "logging/log.hpp"
#include "memory/iterator.inline.hpp"
#include "oops/objArrayOop.inline.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/atomic.hpp"
#include "runtime/handshake.hpp"
#include "runtime/orderAccess.hpp"
#include "runtime/prefetch.inline.hpp"
#include "runtime/thread.hpp"
#include "utilities/align.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/ticks.hpp"

static const ZStatSubPhase ZSubPhaseConcurrentMark("Concurrent Mark");
static const ZStatSubPhase ZSubPhaseConcurrentMarkTryFlush("Concurrent Mark Try Flush");
static const ZStatSubPhase ZSubPhaseConcurrentMarkIdle("Concurrent Mark Idle");
static const ZStatSubPhase ZSubPhaseConcurrentMarkTryTerminate("Concurrent Mark Try Terminate");
static const ZStatSubPhase ZSubPhaseMarkTryComplete("Pause Mark Try Complete");

ZMark::ZMark(ZWorkers* workers, ZPageTable* pagetable) :
    _workers(workers),
    _pagetable(pagetable),
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

bool ZMark::is_initialized() const {
  return _allocator.is_initialized();
}

size_t ZMark::calculate_nstripes(uint nworkers) const {
  // Calculate the number of stripes from the number of workers we use,
  // where the number of stripes must be a power of two and we want to
  // have at least one worker per stripe.
  const size_t nstripes = ZUtils::round_down_power_of_2(nworkers);
  return MIN2(nstripes, ZMarkStripesMax);
}

void ZMark::prepare_mark() {
  // Increment global sequence number to invalidate
  // marking information for all pages.
  ZGlobalSeqNum++;

  // Reset flush/continue counters
  _nproactiveflush = 0;
  _nterminateflush = 0;
  _ntrycomplete = 0;
  _ncontinue = 0;

  // Set number of workers to use
  _nworkers = _workers->nconcurrent();

  // Set number of mark stripes to use, based on number
  // of workers we will use in the concurrent mark phase.
  const size_t nstripes = calculate_nstripes(_nworkers);
  _stripes.set_nstripes(nstripes);

  // Update statistics
  ZStatMark::set_at_mark_start(nstripes);

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

class ZMarkRootsIteratorClosure : public ZRootsIteratorClosure {
public:
  ZMarkRootsIteratorClosure() {
    ZStatTLAB::reset();
  }

  ~ZMarkRootsIteratorClosure() {
    ZStatTLAB::publish();
  }

  virtual void do_thread(Thread* thread) {
    ZRootsIteratorClosure::do_thread(thread);

    // Update thread local address bad mask
    ZThreadLocalData::set_address_bad_mask(thread, ZAddressBadMask);

    // Retire TLAB
    if (UseTLAB && thread->is_Java_thread()) {
      thread->tlab().retire(ZStatTLAB::get());
      thread->tlab().resize();
    }
  }

  virtual void do_oop(oop* p) {
    ZBarrier::mark_barrier_on_root_oop_field(p);
  }

  virtual void do_oop(narrowOop* p) {
    ShouldNotReachHere();
  }
};

class ZMarkRootsTask : public ZTask {
private:
  ZMark* const              _mark;
  ZRootsIterator            _roots;
  ZMarkRootsIteratorClosure _cl;

public:
  ZMarkRootsTask(ZMark* mark) :
      ZTask("ZMarkRootsTask"),
      _mark(mark),
      _roots() {}

  virtual void work() {
    _roots.oops_do(&_cl);

    // Flush and free worker stacks. Needed here since
    // the set of workers executing during root scanning
    // can be different from the set of workers executing
    // during mark.
    _mark->flush_and_free();
  }
};

void ZMark::start() {
  // Verification
  if (ZVerifyMarking) {
    verify_all_stacks_empty();
  }

  // Prepare for concurrent mark
  prepare_mark();

  // Mark roots
  ZMarkRootsTask task(this);
  _workers->run_parallel(&task);
}

void ZMark::prepare_work() {
  assert(_nworkers == _workers->nconcurrent(), "Invalid number of workers");

  // Set number of active workers
  _terminate.reset(_nworkers);

  // Reset flush counters
  _work_nproactiveflush = _work_nterminateflush = 0;
  _work_terminateflush = true;
}

void ZMark::finish_work() {
  // Accumulate proactive/terminate flush counters
  _nproactiveflush += _work_nproactiveflush;
  _nterminateflush += _work_nterminateflush;
}

bool ZMark::is_array(uintptr_t addr) const {
  return ZOop::to_oop(addr)->is_objArray();
}

void ZMark::push_partial_array(uintptr_t addr, size_t size, bool finalizable) {
  assert(is_aligned(addr, ZMarkPartialArrayMinSize), "Address misaligned");
  ZMarkThreadLocalStacks* const stacks = ZThreadLocalData::stacks(Thread::current());
  ZMarkStripe* const stripe = _stripes.stripe_for_addr(addr);
  const uintptr_t offset = ZAddress::offset(addr) >> ZMarkPartialArrayMinSizeShift;
  const uintptr_t length = size / oopSize;
  const ZMarkStackEntry entry(offset, length, finalizable);

  log_develop_trace(gc, marking)("Array push partial: " PTR_FORMAT " (" SIZE_FORMAT "), stripe: " SIZE_FORMAT,
                                 addr, size, _stripes.stripe_id(stripe));

  stacks->push(&_allocator, &_stripes, stripe, entry, false /* publish */);
}

void ZMark::follow_small_array(uintptr_t addr, size_t size, bool finalizable) {
  assert(size <= ZMarkPartialArrayMinSize, "Too large, should be split");
  const size_t length = size / oopSize;

  log_develop_trace(gc, marking)("Array follow small: " PTR_FORMAT " (" SIZE_FORMAT ")", addr, size);

  ZBarrier::mark_barrier_on_oop_array((oop*)addr, length, finalizable);
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
  const uintptr_t addr = ZAddress::good(entry.partial_array_offset() << ZMarkPartialArrayMinSizeShift);
  const size_t size = entry.partial_array_length() * oopSize;

  follow_array(addr, size, finalizable);
}

void ZMark::follow_array_object(objArrayOop obj, bool finalizable) {
  if (finalizable) {
    ZMarkBarrierOopClosure<true /* finalizable */> cl;
    cl.do_klass(obj->klass());
  } else {
    ZMarkBarrierOopClosure<false /* finalizable */> cl;
    cl.do_klass(obj->klass());
  }

  const uintptr_t addr = (uintptr_t)obj->base();
  const size_t size = (size_t)obj->length() * oopSize;

  follow_array(addr, size, finalizable);
}

void ZMark::follow_object(oop obj, bool finalizable) {
  if (finalizable) {
    ZMarkBarrierOopClosure<true /* finalizable */> cl;
    obj->oop_iterate(&cl);
  } else {
    ZMarkBarrierOopClosure<false /* finalizable */> cl;
    obj->oop_iterate(&cl);
  }
}

bool ZMark::try_mark_object(ZMarkCache* cache, uintptr_t addr, bool finalizable) {
  ZPage* const page = _pagetable->get(addr);
  if (page->is_allocating()) {
    // Newly allocated objects are implicitly marked
    return false;
  }

  // Try mark object
  bool inc_live = false;
  const bool success = page->mark_object(addr, finalizable, inc_live);
  if (inc_live) {
    // Update live objects/bytes for page. We use the aligned object
    // size since that is the actual number of bytes used on the page
    // and alignment paddings can never be reclaimed.
    const size_t size = ZUtils::object_size(addr);
    const size_t aligned_size = align_up(size, page->object_alignment());
    cache->inc_live(page, aligned_size);
  }

  return success;
}

void ZMark::mark_and_follow(ZMarkCache* cache, ZMarkStackEntry entry) {
  // Decode flags
  const bool finalizable = entry.finalizable();
  const bool partial_array = entry.partial_array();

  if (partial_array) {
    follow_partial_array(entry, finalizable);
    return;
  }

  // Decode object address
  const uintptr_t addr = entry.object_address();

  if (!try_mark_object(cache, addr, finalizable)) {
    // Already marked
    return;
  }

  if (is_array(addr)) {
    follow_array_object(objArrayOop(ZOop::to_oop(addr)), finalizable);
  } else {
    follow_object(ZOop::to_oop(addr), finalizable);
  }
}

template <typename T>
bool ZMark::drain(ZMarkStripe* stripe, ZMarkThreadLocalStacks* stacks, ZMarkCache* cache, T* timeout) {
  ZMarkStackEntry entry;

  // Drain stripe stacks
  while (stacks->pop(&_allocator, &_stripes, stripe, entry)) {
    mark_and_follow(cache, entry);

    // Check timeout
    if (timeout->has_expired()) {
      // Timeout
      return false;
    }
  }

  // Success
  return true;
}

template <typename T>
bool ZMark::drain_and_flush(ZMarkStripe* stripe, ZMarkThreadLocalStacks* stacks, ZMarkCache* cache, T* timeout) {
  const bool success = drain(stripe, stacks, cache, timeout);

  // Flush and publish worker stacks
  stacks->flush(&_allocator, &_stripes);

  return success;
}

bool ZMark::try_steal(ZMarkStripe* stripe, ZMarkThreadLocalStacks* stacks) {
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

void ZMark::idle() const {
  ZStatTimer timer(ZSubPhaseConcurrentMarkIdle);
  os::naked_short_sleep(1);
}

class ZMarkFlushAndFreeStacksClosure : public ThreadClosure {
private:
  ZMark* const _mark;
  bool         _flushed;

public:
  ZMarkFlushAndFreeStacksClosure(ZMark* mark) :
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

bool ZMark::flush(bool at_safepoint) {
  ZMarkFlushAndFreeStacksClosure cl(this);
  if (at_safepoint) {
    Threads::threads_do(&cl);
  } else {
    Handshake::execute(&cl);
  }

  // Returns true if more work is available
  return cl.flushed() || !_stripes.is_empty();
}

bool ZMark::try_flush(volatile size_t* nflush) {
  // Only flush if handshakes are enabled
  if (!ThreadLocalHandshakes) {
    return false;
  }

  Atomic::inc(nflush);

  ZStatTimer timer(ZSubPhaseConcurrentMarkTryFlush);
  return flush(false /* at_safepoint */);
}

bool ZMark::try_proactive_flush() {
  // Only do proactive flushes from worker 0
  if (ZThread::worker_id() != 0) {
    return false;
  }

  if (Atomic::load(&_work_nproactiveflush) == ZMarkProactiveFlushMax ||
      Atomic::load(&_work_nterminateflush) != 0) {
    // Limit reached or we're trying to terminate
    return false;
  }

  return try_flush(&_work_nproactiveflush);
}

bool ZMark::try_terminate() {
  ZStatTimer timer(ZSubPhaseConcurrentMarkTryTerminate);

  if (_terminate.enter_stage0()) {
    // Last thread entered stage 0, flush
    if (Atomic::load(&_work_terminateflush) &&
        Atomic::load(&_work_nterminateflush) != ZMarkTerminateFlushMax) {
      // Exit stage 0 to allow other threads to continue marking
      _terminate.exit_stage0();

      // Flush before termination
      if (!try_flush(&_work_nterminateflush)) {
        // No more work available, skip further flush attempts
        Atomic::store(false, &_work_terminateflush);
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

class ZMarkNoTimeout : public StackObj {
public:
  bool has_expired() {
    return false;
  }
};

void ZMark::work_without_timeout(ZMarkCache* cache, ZMarkStripe* stripe, ZMarkThreadLocalStacks* stacks) {
  ZStatTimer timer(ZSubPhaseConcurrentMark);
  ZMarkNoTimeout no_timeout;

  for (;;) {
    drain_and_flush(stripe, stacks, cache, &no_timeout);

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
}

class ZMarkTimeout : public StackObj {
private:
  const Ticks    _start;
  const uint64_t _timeout;
  const uint64_t _check_interval;
  uint64_t       _check_at;
  uint64_t       _check_count;
  bool           _expired;

public:
  ZMarkTimeout(uint64_t timeout_in_millis) :
      _start(Ticks::now()),
      _timeout(_start.value() + TimeHelper::millis_to_counter(timeout_in_millis)),
      _check_interval(200),
      _check_at(_check_interval),
      _check_count(0),
      _expired(false) {}

  ~ZMarkTimeout() {
    const Tickspan duration = Ticks::now() - _start;
    log_debug(gc, marking)("Mark With Timeout (%s): %s, " UINT64_FORMAT " oops, %.3fms",
                           ZThread::name(), _expired ? "Expired" : "Completed",
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

void ZMark::work_with_timeout(ZMarkCache* cache, ZMarkStripe* stripe, ZMarkThreadLocalStacks* stacks, uint64_t timeout_in_millis) {
  ZStatTimer timer(ZSubPhaseMarkTryComplete);
  ZMarkTimeout timeout(timeout_in_millis);

  for (;;) {
    if (!drain_and_flush(stripe, stacks, cache, &timeout)) {
      // Timed out
      break;
    }

    if (try_steal(stripe, stacks)) {
      // Stole work
      continue;
    }

    // Terminate
    break;
  }
}

void ZMark::work(uint64_t timeout_in_millis) {
  ZMarkCache cache(_stripes.nstripes());
  ZMarkStripe* const stripe = _stripes.stripe_for_worker(_nworkers, ZThread::worker_id());
  ZMarkThreadLocalStacks* const stacks = ZThreadLocalData::stacks(Thread::current());

  if (timeout_in_millis == 0) {
    work_without_timeout(&cache, stripe, stacks);
  } else {
    work_with_timeout(&cache, stripe, stacks, timeout_in_millis);
  }

  // Make sure stacks have been flushed
  assert(stacks->is_empty(&_stripes), "Should be empty");

  // Free remaining stacks
  stacks->free(&_allocator);
}

class ZMarkConcurrentRootsIteratorClosure : public ZRootsIteratorClosure {
public:
  virtual void do_oop(oop* p) {
    ZBarrier::mark_barrier_on_oop_field(p, false /* finalizable */);
  }

  virtual void do_oop(narrowOop* p) {
    ShouldNotReachHere();
  }
};


class ZMarkConcurrentRootsTask : public ZTask {
private:
  ZConcurrentRootsIterator            _roots;
  ZMarkConcurrentRootsIteratorClosure _cl;

public:
  ZMarkConcurrentRootsTask(ZMark* mark) :
      ZTask("ZMarkConcurrentRootsTask"),
      _roots(true /* marking */),
      _cl() {}

  virtual void work() {
    _roots.oops_do(&_cl);
  }
};

class ZMarkTask : public ZTask {
private:
  ZMark* const   _mark;
  const uint64_t _timeout_in_millis;

public:
  ZMarkTask(ZMark* mark, uint64_t timeout_in_millis = 0) :
      ZTask("ZMarkTask"),
      _mark(mark),
      _timeout_in_millis(timeout_in_millis) {
    _mark->prepare_work();
  }

  ~ZMarkTask() {
    _mark->finish_work();
  }

  virtual void work() {
    _mark->work(_timeout_in_millis);
  }
};

void ZMark::mark(bool initial) {
  if (initial) {
    ZMarkConcurrentRootsTask task(this);
    _workers->run_concurrent(&task);
  }

  ZMarkTask task(this);
  _workers->run_concurrent(&task);
}

bool ZMark::try_complete() {
  _ntrycomplete++;

  // Use nconcurrent number of worker threads to maintain the
  // worker/stripe distribution used during concurrent mark.
  ZMarkTask task(this, ZMarkCompleteTimeout);
  _workers->run_concurrent(&task);

  // Successful if all stripes are empty
  return _stripes.is_empty();
}

bool ZMark::try_end() {
  // Flush all mark stacks
  if (!flush(true /* at_safepoint */)) {
    // Mark completed
    return true;
  }

  // Try complete marking by doing a limited
  // amount of mark work in this phase.
  return try_complete();
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
  ZStatMark::set_at_mark_end(_nproactiveflush, _nterminateflush, _ntrycomplete, _ncontinue);

  // Mark completed
  return true;
}

void ZMark::flush_and_free() {
  Thread* const thread = Thread::current();
  flush_and_free(thread);
}

bool ZMark::flush_and_free(Thread* thread) {
  ZMarkThreadLocalStacks* const stacks = ZThreadLocalData::stacks(thread);
  const bool flushed = stacks->flush(&_allocator, &_stripes);
  stacks->free(&_allocator);
  return flushed;
}

class ZVerifyMarkStacksEmptyClosure : public ThreadClosure {
private:
  const ZMarkStripeSet* const _stripes;

public:
  ZVerifyMarkStacksEmptyClosure(const ZMarkStripeSet* stripes) :
      _stripes(stripes) {}

  void do_thread(Thread* thread) {
    ZMarkThreadLocalStacks* const stacks = ZThreadLocalData::stacks(thread);
    guarantee(stacks->is_empty(_stripes), "Should be empty");
  }
};

void ZMark::verify_all_stacks_empty() const {
  // Verify thread stacks
  ZVerifyMarkStacksEmptyClosure cl(&_stripes);
  Threads::threads_do(&cl);

  // Verify stripe stacks
  guarantee(_stripes.is_empty(), "Should be empty");
}
