/*
 * Copyright (c) 2018, 2019, Red Hat, Inc. All rights reserved.
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

#include "gc/shenandoah/shenandoahFreeSet.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahPacer.hpp"
#include "runtime/atomic.hpp"
#include "runtime/mutexLocker.hpp"

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
 * The allocatable space when GC is running is "free" at the start of cycle, but the
 * accounted budget is based on "used". So, we need to adjust the tax knowing that.
 * Also, since we effectively count the used space three times (mark, evac, update-refs),
 * we need to multiply the tax by 3. Example: for 10 MB free and 90 MB used, GC would
 * come back with 3*90 MB budget, and thus for each 1 MB of allocation, we have to pay
 * 3*90 / 10 MBs. In the end, we would pay back the entire budget.
 */

void ShenandoahPacer::setup_for_mark() {
  assert(ShenandoahPacing, "Only be here when pacing is enabled");

  size_t live = update_and_get_progress_history();
  size_t free = _heap->free_set()->available();

  size_t non_taxable = free * ShenandoahPacingCycleSlack / 100;
  size_t taxable = free - non_taxable;

  double tax = 1.0 * live / taxable; // base tax for available free space
  tax *= 3;                          // mark is phase 1 of 3, claim 1/3 of free for it
  tax *= ShenandoahPacingSurcharge;  // additional surcharge to help unclutter heap

  restart_with(non_taxable, tax);

  log_info(gc, ergo)("Pacer for Mark. Expected Live: " SIZE_FORMAT "%s, Free: " SIZE_FORMAT "%s, "
                     "Non-Taxable: " SIZE_FORMAT "%s, Alloc Tax Rate: %.1fx",
                     byte_size_in_proper_unit(live),        proper_unit_for_byte_size(live),
                     byte_size_in_proper_unit(free),        proper_unit_for_byte_size(free),
                     byte_size_in_proper_unit(non_taxable), proper_unit_for_byte_size(non_taxable),
                     tax);
}

void ShenandoahPacer::setup_for_evac() {
  assert(ShenandoahPacing, "Only be here when pacing is enabled");

  size_t used = _heap->collection_set()->used();
  size_t free = _heap->free_set()->available();

  size_t non_taxable = free * ShenandoahPacingCycleSlack / 100;
  size_t taxable = free - non_taxable;

  double tax = 1.0 * used / taxable; // base tax for available free space
  tax *= 2;                          // evac is phase 2 of 3, claim 1/2 of remaining free
  tax = MAX2<double>(1, tax);        // never allocate more than GC processes during the phase
  tax *= ShenandoahPacingSurcharge;  // additional surcharge to help unclutter heap

  restart_with(non_taxable, tax);

  log_info(gc, ergo)("Pacer for Evacuation. Used CSet: " SIZE_FORMAT "%s, Free: " SIZE_FORMAT "%s, "
                     "Non-Taxable: " SIZE_FORMAT "%s, Alloc Tax Rate: %.1fx",
                     byte_size_in_proper_unit(used),        proper_unit_for_byte_size(used),
                     byte_size_in_proper_unit(free),        proper_unit_for_byte_size(free),
                     byte_size_in_proper_unit(non_taxable), proper_unit_for_byte_size(non_taxable),
                     tax);
}

void ShenandoahPacer::setup_for_updaterefs() {
  assert(ShenandoahPacing, "Only be here when pacing is enabled");

  size_t used = _heap->used();
  size_t free = _heap->free_set()->available();

  size_t non_taxable = free * ShenandoahPacingCycleSlack / 100;
  size_t taxable = free - non_taxable;

  double tax = 1.0 * used / taxable; // base tax for available free space
  tax *= 1;                          // update-refs is phase 3 of 3, claim the remaining free
  tax = MAX2<double>(1, tax);        // never allocate more than GC processes during the phase
  tax *= ShenandoahPacingSurcharge;  // additional surcharge to help unclutter heap

  restart_with(non_taxable, tax);

  log_info(gc, ergo)("Pacer for Update Refs. Used: " SIZE_FORMAT "%s, Free: " SIZE_FORMAT "%s, "
                     "Non-Taxable: " SIZE_FORMAT "%s, Alloc Tax Rate: %.1fx",
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

  log_info(gc, ergo)("Pacer for Idle. Initial: " SIZE_FORMAT "%s, Alloc Tax Rate: %.1fx",
                     byte_size_in_proper_unit(initial), proper_unit_for_byte_size(initial),
                     tax);
}

/*
 * There is no useful notion of progress for these operations. To avoid stalling
 * the allocators unnecessarily, allow them to run unimpeded.
 */

void ShenandoahPacer::setup_for_preclean() {
  assert(ShenandoahPacing, "Only be here when pacing is enabled");

  size_t initial = _heap->max_capacity();
  restart_with(initial, 1.0);

  log_info(gc, ergo)("Pacer for Precleaning. Non-Taxable: " SIZE_FORMAT "%s",
                     byte_size_in_proper_unit(initial), proper_unit_for_byte_size(initial));
}

void ShenandoahPacer::setup_for_reset() {
  assert(ShenandoahPacing, "Only be here when pacing is enabled");

  size_t initial = _heap->max_capacity();
  restart_with(initial, 1.0);

  log_info(gc, ergo)("Pacer for Reset. Non-Taxable: " SIZE_FORMAT "%s",
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
  Atomic::xchg(&_budget, (intptr_t)initial);
  Atomic::store(&_tax_rate, tax_rate);
  Atomic::inc(&_epoch);
}

bool ShenandoahPacer::claim_for_alloc(size_t words, bool force) {
  assert(ShenandoahPacing, "Only be here when pacing is enabled");

  intptr_t tax = MAX2<intptr_t>(1, words * Atomic::load(&_tax_rate));

  intptr_t cur = 0;
  intptr_t new_val = 0;
  do {
    cur = Atomic::load(&_budget);
    if (cur < tax && !force) {
      // Progress depleted, alas.
      return false;
    }
    new_val = cur - tax;
  } while (Atomic::cmpxchg(&_budget, cur, new_val) != cur);
  return true;
}

void ShenandoahPacer::unpace_for_alloc(intptr_t epoch, size_t words) {
  assert(ShenandoahPacing, "Only be here when pacing is enabled");

  if (_epoch != epoch) {
    // Stale ticket, no need to unpace.
    return;
  }

  intptr_t tax = MAX2<intptr_t>(1, words * Atomic::load(&_tax_rate));
  Atomic::add(&_budget, tax);
}

intptr_t ShenandoahPacer::epoch() {
  return Atomic::load(&_epoch);
}

void ShenandoahPacer::pace_for_alloc(size_t words) {
  assert(ShenandoahPacing, "Only be here when pacing is enabled");

  // Fast path: try to allocate right away
  if (claim_for_alloc(words, false)) {
    return;
  }

  // Threads that are attaching should not block at all: they are not
  // fully initialized yet. Blocking them would be awkward.
  // This is probably the path that allocates the thread oop itself.
  // Forcefully claim without waiting.
  if (JavaThread::current()->is_attaching_via_jni()) {
    claim_for_alloc(words, true);
    return;
  }

  size_t max = ShenandoahPacingMaxDelay;
  double start = os::elapsedTime();

  size_t total = 0;
  size_t cur = 0;

  while (true) {
    // We could instead assist GC, but this would suffice for now.
    // This code should also participate in safepointing.
    // Perform the exponential backoff, limited by max.

    cur = cur * 2;
    if (total + cur > max) {
      cur = (max > total) ? (max - total) : 0;
    }
    cur = MAX2<size_t>(1, cur);

    wait(cur);

    double end = os::elapsedTime();
    total = (size_t)((end - start) * 1000);

    if (total > max) {
      // Spent local time budget to wait for enough GC progress.
      // Breaking out and allocating anyway, which may mean we outpace GC,
      // and start Degenerated GC cycle.
      _delays.add(total);

      // Forcefully claim the budget: it may go negative at this point, and
      // GC should replenish for this and subsequent allocations
      claim_for_alloc(words, true);
      break;
    }

    if (claim_for_alloc(words, false)) {
      // Acquired enough permit, nice. Can allocate now.
      _delays.add(total);
      break;
    }
  }
}

void ShenandoahPacer::wait(long time_ms) {
  // Perform timed wait. It works like like sleep(), except without modifying
  // the thread interruptible status. MonitorLocker also checks for safepoints.
  assert(time_ms > 0, "Should not call this with zero argument, as it would stall until notify");
  MonitorLocker locker(_wait_monitor);
  _wait_monitor->wait(time_ms);
}

void ShenandoahPacer::notify_waiters() {
  MonitorLocker locker(_wait_monitor);
  _wait_monitor->notify_all();
}

void ShenandoahPacer::print_on(outputStream* out) const {
  out->print_cr("ALLOCATION PACING:");
  out->cr();

  out->print_cr("Max pacing delay is set for " UINTX_FORMAT " ms.", ShenandoahPacingMaxDelay);
  out->cr();

  out->print_cr("Higher delay would prevent application outpacing the GC, but it will hide the GC latencies");
  out->print_cr("from the STW pause times. Pacing affects the individual threads, and so it would also be");
  out->print_cr("invisible to the usual profiling tools, but would add up to end-to-end application latency.");
  out->print_cr("Raise max pacing delay with care.");
  out->cr();

  out->print_cr("Actual pacing delays histogram:");
  out->cr();

  out->print_cr("%10s - %10s  %12s%12s", "From", "To", "Count", "Sum");

  size_t total_count = 0;
  size_t total_sum = 0;
  for (int c = _delays.min_level(); c <= _delays.max_level(); c++) {
    int l = (c == 0) ? 0 : 1 << (c - 1);
    int r = 1 << c;
    size_t count = _delays.level(c);
    size_t sum   = count * (r - l) / 2;
    total_count += count;
    total_sum   += sum;

    out->print_cr("%7d ms - %7d ms: " SIZE_FORMAT_W(12) SIZE_FORMAT_W(12) " ms", l, r, count, sum);
  }
  out->print_cr("%23s: " SIZE_FORMAT_W(12) SIZE_FORMAT_W(12) " ms", "Total", total_count, total_sum);
  out->cr();
  out->print_cr("Pacing delays are measured from entering the pacing code till exiting it. Therefore,");
  out->print_cr("observed pacing delays may be higher than the threshold when paced thread spent more");
  out->print_cr("time in the pacing code. It usually happens when thread is de-scheduled while paced,");
  out->print_cr("OS takes longer to unblock the thread, or JVM experiences an STW pause.");
  out->cr();
}
