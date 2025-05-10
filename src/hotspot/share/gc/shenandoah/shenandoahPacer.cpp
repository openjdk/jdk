/*
 * Copyright (c) 2018, 2019, Red Hat, Inc. All rights reserved.
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


#include "gc/shenandoah/shenandoahFreeSet.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahPacer.hpp"
#include "gc/shenandoah/shenandoahPhaseTimings.hpp"
#include "runtime/atomic.hpp"
#include "runtime/javaThread.inline.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/threadSMR.hpp"

/*
 * In normal concurrent cycle, we have to pace the application to let GC finish.
 *
 * Here, we do not know how large would be the collection set, and what are the
 * relative performances of the each stage in the concurrent cycle, and so we have to
 * make some assumptions.
 *
 * For concurrent mark, there is no clear notion of progress. The moderately accurate
 * and easy to get metric is the amount of live objects the mark had encountered. But,
 * that does directly correlate with the used heap, because the heap might be fully
 * dead or fully alive. We cannot assume either of the extremes: we would either allow
 * application to run out of memory if we assume heap is fully dead but it is not, and,
 * conversely, we would pacify application excessively if we assume heap is fully alive
 * but it is not. So we need to guesstimate the particular expected value for heap liveness.
 * The best way to do this is apparently recording the past history.
 *
 * For concurrent evac and update-refs, we are walking the heap per-region, and so the
 * notion of progress is clear: we get reported the "used" size from the processed regions
 * and use the global heap-used as the baseline.
 *
 * The allocatable space when GC is running is "free" at the start of phase, but the
 * accounted budget is based on "used". So, we need to adjust the tax knowing that.
 */

void ShenandoahPacer::setup_for_mark() {
  assert(ShenandoahPacing, "Only be here when pacing is enabled");

  size_t live = update_and_get_progress_history();
  size_t free = _heap->free_set()->available();
  assert(free != ShenandoahFreeSet::FreeSetUnderConstruction, "Avoid this race");

  size_t non_taxable = free * ShenandoahPacingCycleSlack / 100;
  size_t taxable = free - non_taxable;
  taxable = MAX2<size_t>(1, taxable);

  double tax = 1.0 * live / taxable; // base tax for available free space
  tax *= 1;                          // mark can succeed with immediate garbage, claim all available space
  tax *= ShenandoahPacingSurcharge;  // additional surcharge to help unclutter heap

  restart_with(non_taxable, tax);

  log_info(gc, ergo)("Pacer for Mark. Expected Live: %zu%s, Free: %zu%s, "
                     "Non-Taxable: %zu%s, Alloc Tax Rate: %.1fx",
                     byte_size_in_proper_unit(live),        proper_unit_for_byte_size(live),
                     byte_size_in_proper_unit(free),        proper_unit_for_byte_size(free),
                     byte_size_in_proper_unit(non_taxable), proper_unit_for_byte_size(non_taxable),
                     tax);
}

void ShenandoahPacer::setup_for_evac() {
  assert(ShenandoahPacing, "Only be here when pacing is enabled");

  size_t used = _heap->collection_set()->used();
  size_t free = _heap->free_set()->available();
  assert(free != ShenandoahFreeSet::FreeSetUnderConstruction, "Avoid this race");

  size_t non_taxable = free * ShenandoahPacingCycleSlack / 100;
  size_t taxable = free - non_taxable;
  taxable = MAX2<size_t>(1, taxable);

  double tax = 1.0 * used / taxable; // base tax for available free space
  tax *= 2;                          // evac is followed by update-refs, claim 1/2 of remaining free
  tax = MAX2<double>(1, tax);        // never allocate more than GC processes during the phase
  tax *= ShenandoahPacingSurcharge;  // additional surcharge to help unclutter heap

  restart_with(non_taxable, tax);

  log_info(gc, ergo)("Pacer for Evacuation. Used CSet: %zu%s, Free: %zu%s, "
                     "Non-Taxable: %zu%s, Alloc Tax Rate: %.1fx",
                     byte_size_in_proper_unit(used),        proper_unit_for_byte_size(used),
                     byte_size_in_proper_unit(free),        proper_unit_for_byte_size(free),
                     byte_size_in_proper_unit(non_taxable), proper_unit_for_byte_size(non_taxable),
                     tax);
}

void ShenandoahPacer::setup_for_update_refs() {
  assert(ShenandoahPacing, "Only be here when pacing is enabled");

  size_t used = _heap->used();
  size_t free = _heap->free_set()->available();
  assert(free != ShenandoahFreeSet::FreeSetUnderConstruction, "Avoid this race");

  size_t non_taxable = free * ShenandoahPacingCycleSlack / 100;
  size_t taxable = free - non_taxable;
  taxable = MAX2<size_t>(1, taxable);

  double tax = 1.0 * used / taxable; // base tax for available free space
  tax *= 1;                          // update-refs is the last phase, claim the remaining free
  tax = MAX2<double>(1, tax);        // never allocate more than GC processes during the phase
  tax *= ShenandoahPacingSurcharge;  // additional surcharge to help unclutter heap

  restart_with(non_taxable, tax);

  log_info(gc, ergo)("Pacer for Update Refs. Used: %zu%s, Free: %zu%s, "
                     "Non-Taxable: %zu%s, Alloc Tax Rate: %.1fx",
                     byte_size_in_proper_unit(used),        proper_unit_for_byte_size(used),
                     byte_size_in_proper_unit(free),        proper_unit_for_byte_size(free),
                     byte_size_in_proper_unit(non_taxable), proper_unit_for_byte_size(non_taxable),
                     tax);
}

/*
 * In idle phase, we have to pace the application to let control thread react with GC start.
 *
 * Here, we have rendezvous with concurrent thread that adds up the budget as it acknowledges
 * it had seen recent allocations. It will naturally pace the allocations if control thread is
 * not catching up. To bootstrap this feedback cycle, we need to start with some initial budget
 * for applications to allocate at.
 */

void ShenandoahPacer::setup_for_idle() {
  assert(ShenandoahPacing, "Only be here when pacing is enabled");

  size_t initial = _heap->max_capacity() / 100 * ShenandoahPacingIdleSlack;
  double tax = 1;

  restart_with(initial, tax);

  log_info(gc, ergo)("Pacer for Idle. Initial: %zu%s, Alloc Tax Rate: %.1fx",
                     byte_size_in_proper_unit(initial), proper_unit_for_byte_size(initial),
                     tax);
}

/*
 * There is no useful notion of progress for these operations. To avoid stalling
 * the allocators unnecessarily, allow them to run unimpeded.
 */

void ShenandoahPacer::setup_for_reset() {
  assert(ShenandoahPacing, "Only be here when pacing is enabled");

  size_t initial = _heap->max_capacity();
  restart_with(initial, 1.0);

  log_info(gc, ergo)("Pacer for Reset. Non-Taxable: %zu%s",
                     byte_size_in_proper_unit(initial), proper_unit_for_byte_size(initial));
}

size_t ShenandoahPacer::update_and_get_progress_history() {
  if (_progress == -1) {
    // First initialization, report some prior
    Atomic::store(&_progress, (intptr_t)PACING_PROGRESS_ZERO);
    return (size_t) (_heap->max_capacity() * 0.1);
  } else {
    // Record history, and reply historical data
    _progress_history->add(_progress);
    Atomic::store(&_progress, (intptr_t)PACING_PROGRESS_ZERO);
    return (size_t) (_progress_history->avg() * HeapWordSize);
  }
}

void ShenandoahPacer::restart_with(size_t non_taxable_bytes, double tax_rate) {
  size_t initial = (size_t)(non_taxable_bytes * tax_rate) >> LogHeapWordSize;
  STATIC_ASSERT(sizeof(size_t) <= sizeof(intptr_t));
  Atomic::xchg(&_budget, (intptr_t)initial, memory_order_relaxed);
  Atomic::store(&_tax_rate, tax_rate);
  Atomic::inc(&_epoch);

  // Shake up stalled waiters after budget update.
  _need_notify_waiters.try_set();
}

template<bool FORCE>
bool ShenandoahPacer::claim_for_alloc(size_t words) {
  assert(ShenandoahPacing, "Only be here when pacing is enabled");

  intptr_t tax = MAX2<intptr_t>(1, words * Atomic::load(&_tax_rate));

  intptr_t cur = 0;
  intptr_t new_val = 0;
  do {
    cur = Atomic::load(&_budget);
    if (cur < tax && !FORCE) {
      // Progress depleted, alas.
      return false;
    }
    new_val = cur - tax;
  } while (Atomic::cmpxchg(&_budget, cur, new_val, memory_order_relaxed) != cur);
  return true;
}

template bool ShenandoahPacer::claim_for_alloc<true>(size_t words);
template bool ShenandoahPacer::claim_for_alloc<false>(size_t words);

void ShenandoahPacer::unpace_for_alloc(intptr_t epoch, size_t words) {
  assert(ShenandoahPacing, "Only be here when pacing is enabled");

  if (Atomic::load(&_epoch) != epoch) {
    // Stale ticket, no need to unpace.
    return;
  }

  size_t tax = MAX2<size_t>(1, words * Atomic::load(&_tax_rate));
  add_budget(tax);
}

intptr_t ShenandoahPacer::epoch() {
  return Atomic::load(&_epoch);
}

void ShenandoahPacer::pace_for_alloc(size_t words) {
  assert(ShenandoahPacing, "Only be here when pacing is enabled");

  // Fast path: try to allocate right away
  bool claimed = claim_for_alloc<false>(words);
  if (claimed) {
    return;
  }

  // Threads that are attaching should not block at all: they are not
  // fully initialized yet. Blocking them would be awkward.
  // This is probably the path that allocates the thread oop itself.
  //
  // Thread which is not an active Java thread should also not block.
  // This can happen during VM init when main thread is still not an
  // active Java thread.
  JavaThread* current = JavaThread::current();
  if (current->is_attaching_via_jni() ||
      !current->is_active_Java_thread()) {
    claim_for_alloc<true>(words);
    return;
  }

  jlong const start_time = os::javaTimeNanos();
  jlong const deadline = start_time + (ShenandoahPacingMaxDelay * NANOSECS_PER_MILLISEC);
  while (!claimed && os::javaTimeNanos() < deadline) {
    // We could instead assist GC, but this would suffice for now.
    wait(1);
    claimed = claim_for_alloc<false>(words);
  }
  if (!claimed) {
    // Spent local time budget to wait for enough GC progress.
    // Force allocating anyway, which may mean we outpace GC,
    // and start Degenerated GC cycle.
    claimed = claim_for_alloc<true>(words);
    assert(claimed, "Should always succeed");
  }
  ShenandoahThreadLocalData::add_paced_time(current, (double)(os::javaTimeNanos() - start_time) / NANOSECS_PER_SEC);
}

void ShenandoahPacer::wait(size_t time_ms) {
  // Perform timed wait. It works like like sleep(), except without modifying
  // the thread interruptible status. MonitorLocker also checks for safepoints.
  assert(time_ms > 0, "Should not call this with zero argument, as it would stall until notify");
  assert(time_ms <= LONG_MAX, "Sanity");
  MonitorLocker locker(_wait_monitor);
  _wait_monitor->wait(time_ms);
}

void ShenandoahPacer::notify_waiters() {
  if (_need_notify_waiters.try_unset()) {
    MonitorLocker locker(_wait_monitor);
    _wait_monitor->notify_all();
  }
}

void ShenandoahPacer::flush_stats_to_cycle() {
  double sum = 0;
  for (JavaThreadIteratorWithHandle jtiwh; JavaThread *t = jtiwh.next(); ) {
    sum += ShenandoahThreadLocalData::paced_time(t);
  }
  ShenandoahHeap::heap()->phase_timings()->record_phase_time(ShenandoahPhaseTimings::pacing, sum);
}

void ShenandoahPacer::print_cycle_on(outputStream* out) {
  MutexLocker lock(Threads_lock);

  double now = os::elapsedTime();
  double total = now - _last_time;
  _last_time = now;

  out->cr();
  out->print_cr("Allocation pacing accrued:");

  size_t threads_total = 0;
  size_t threads_nz = 0;
  double sum = 0;
  for (JavaThreadIteratorWithHandle jtiwh; JavaThread *t = jtiwh.next(); ) {
    double d = ShenandoahThreadLocalData::paced_time(t);
    if (d > 0) {
      threads_nz++;
      sum += d;
      out->print_cr("  %5.0f of %5.0f ms (%5.1f%%): %s",
              d * 1000, total * 1000, d/total*100, t->name());
    }
    threads_total++;
    ShenandoahThreadLocalData::reset_paced_time(t);
  }
  out->print_cr("  %5.0f of %5.0f ms (%5.1f%%): <total>",
          sum * 1000, total * 1000, sum/total*100);

  if (threads_total > 0) {
    out->print_cr("  %5.0f of %5.0f ms (%5.1f%%): <average total>",
            sum / threads_total * 1000, total * 1000, sum / threads_total / total * 100);
  }
  if (threads_nz > 0) {
    out->print_cr("  %5.0f of %5.0f ms (%5.1f%%): <average non-zero>",
            sum / threads_nz * 1000, total * 1000, sum / threads_nz / total * 100);
  }
  out->cr();
}

void ShenandoahPeriodicPacerNotifyTask::task() {
  assert(ShenandoahPacing, "Should not be here otherwise");
  _pacer->notify_waiters();
}
