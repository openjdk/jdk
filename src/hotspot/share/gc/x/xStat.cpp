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
#include "gc/shared/gc_globals.hpp"
#include "gc/x/xAbort.inline.hpp"
#include "gc/x/xCollectedHeap.hpp"
#include "gc/x/xCPU.inline.hpp"
#include "gc/x/xGlobals.hpp"
#include "gc/x/xNMethodTable.hpp"
#include "gc/x/xPageAllocator.inline.hpp"
#include "gc/x/xRelocationSetSelector.inline.hpp"
#include "gc/x/xStat.hpp"
#include "gc/x/xThread.inline.hpp"
#include "gc/x/xTracer.inline.hpp"
#include "gc/x/xUtils.hpp"
#include "memory/metaspaceUtils.hpp"
#include "memory/resourceArea.hpp"
#include "runtime/atomic.hpp"
#include "runtime/os.hpp"
#include "runtime/timer.hpp"
#include "utilities/align.hpp"
#include "utilities/debug.hpp"
#include "utilities/ticks.hpp"

#define XSIZE_FMT                       SIZE_FORMAT "M(%.0f%%)"
#define XSIZE_ARGS_WITH_MAX(size, max)  ((size) / M), (percent_of(size, max))
#define XSIZE_ARGS(size)                XSIZE_ARGS_WITH_MAX(size, XStatHeap::max_capacity())

#define XTABLE_ARGS_NA                  "%9s", "-"
#define XTABLE_ARGS(size)               SIZE_FORMAT_W(8) "M (%.0f%%)", \
                                        ((size) / M), (percent_of(size, XStatHeap::max_capacity()))

//
// Stat sampler/counter data
//
struct XStatSamplerData {
  uint64_t _nsamples;
  uint64_t _sum;
  uint64_t _max;

  XStatSamplerData() :
    _nsamples(0),
    _sum(0),
    _max(0) {}

  void add(const XStatSamplerData& new_sample) {
    _nsamples += new_sample._nsamples;
    _sum += new_sample._sum;
    _max = MAX2(_max, new_sample._max);
  }
};

struct XStatCounterData {
  uint64_t _counter;

  XStatCounterData() :
    _counter(0) {}
};

//
// Stat sampler history
//
template <size_t size>
class XStatSamplerHistoryInterval {
private:
  size_t           _next;
  XStatSamplerData _samples[size];
  XStatSamplerData _accumulated;
  XStatSamplerData _total;

public:
  XStatSamplerHistoryInterval() :
      _next(0),
      _samples(),
      _accumulated(),
      _total() {}

  bool add(const XStatSamplerData& new_sample) {
    // Insert sample
    const XStatSamplerData old_sample = _samples[_next];
    _samples[_next] = new_sample;

    // Adjust accumulated
    _accumulated._nsamples += new_sample._nsamples;
    _accumulated._sum += new_sample._sum;
    _accumulated._max = MAX2(_accumulated._max, new_sample._max);

    // Adjust total
    _total._nsamples -= old_sample._nsamples;
    _total._sum -= old_sample._sum;
    _total._nsamples += new_sample._nsamples;
    _total._sum += new_sample._sum;
    if (_total._max < new_sample._max) {
      // Found new max
      _total._max = new_sample._max;
    } else if (_total._max == old_sample._max) {
      // Removed old max, reset and find new max
      _total._max = 0;
      for (size_t i = 0; i < size; i++) {
        if (_total._max < _samples[i]._max) {
          _total._max = _samples[i]._max;
        }
      }
    }

    // Adjust next
    if (++_next == size) {
      _next = 0;

      // Clear accumulated
      const XStatSamplerData zero;
      _accumulated = zero;

      // Became full
      return true;
    }

    // Not yet full
    return false;
  }

  const XStatSamplerData& total() const {
    return _total;
  }

  const XStatSamplerData& accumulated() const {
    return _accumulated;
  }
};

class XStatSamplerHistory : public CHeapObj<mtGC> {
private:
  XStatSamplerHistoryInterval<10> _10seconds;
  XStatSamplerHistoryInterval<60> _10minutes;
  XStatSamplerHistoryInterval<60> _10hours;
  XStatSamplerData                _total;

  uint64_t avg(uint64_t sum, uint64_t nsamples) const {
    return (nsamples > 0) ? sum / nsamples : 0;
  }

public:
  XStatSamplerHistory() :
      _10seconds(),
      _10minutes(),
      _10hours(),
      _total() {}

  void add(const XStatSamplerData& new_sample) {
    if (_10seconds.add(new_sample)) {
      if (_10minutes.add(_10seconds.total())) {
        if (_10hours.add(_10minutes.total())) {
          _total.add(_10hours.total());
        }
      }
    }
  }

  uint64_t avg_10_seconds() const {
    const uint64_t sum      = _10seconds.total()._sum;
    const uint64_t nsamples = _10seconds.total()._nsamples;
    return avg(sum, nsamples);
  }

  uint64_t avg_10_minutes() const {
    const uint64_t sum      = _10seconds.accumulated()._sum +
                              _10minutes.total()._sum;
    const uint64_t nsamples = _10seconds.accumulated()._nsamples +
                              _10minutes.total()._nsamples;
    return avg(sum, nsamples);
  }

  uint64_t avg_10_hours() const {
    const uint64_t sum      = _10seconds.accumulated()._sum +
                              _10minutes.accumulated()._sum +
                              _10hours.total()._sum;
    const uint64_t nsamples = _10seconds.accumulated()._nsamples +
                              _10minutes.accumulated()._nsamples +
                              _10hours.total()._nsamples;
    return avg(sum, nsamples);
  }

  uint64_t avg_total() const {
    const uint64_t sum      = _10seconds.accumulated()._sum +
                              _10minutes.accumulated()._sum +
                              _10hours.accumulated()._sum +
                              _total._sum;
    const uint64_t nsamples = _10seconds.accumulated()._nsamples +
                              _10minutes.accumulated()._nsamples +
                              _10hours.accumulated()._nsamples +
                              _total._nsamples;
    return avg(sum, nsamples);
  }

  uint64_t max_10_seconds() const {
    return _10seconds.total()._max;
  }

  uint64_t max_10_minutes() const {
    return MAX2(_10seconds.accumulated()._max,
                _10minutes.total()._max);
  }

  uint64_t max_10_hours() const {
    return MAX3(_10seconds.accumulated()._max,
                _10minutes.accumulated()._max,
                _10hours.total()._max);
  }

  uint64_t max_total() const {
    return MAX4(_10seconds.accumulated()._max,
                _10minutes.accumulated()._max,
                _10hours.accumulated()._max,
                _total._max);
  }
};

//
// Stat unit printers
//
void XStatUnitTime(LogTargetHandle log, const XStatSampler& sampler, const XStatSamplerHistory& history) {
  log.print(" %10s: %-41s "
            "%9.3f / %-9.3f "
            "%9.3f / %-9.3f "
            "%9.3f / %-9.3f "
            "%9.3f / %-9.3f   ms",
            sampler.group(),
            sampler.name(),
            TimeHelper::counter_to_millis(history.avg_10_seconds()),
            TimeHelper::counter_to_millis(history.max_10_seconds()),
            TimeHelper::counter_to_millis(history.avg_10_minutes()),
            TimeHelper::counter_to_millis(history.max_10_minutes()),
            TimeHelper::counter_to_millis(history.avg_10_hours()),
            TimeHelper::counter_to_millis(history.max_10_hours()),
            TimeHelper::counter_to_millis(history.avg_total()),
            TimeHelper::counter_to_millis(history.max_total()));
}

void XStatUnitBytes(LogTargetHandle log, const XStatSampler& sampler, const XStatSamplerHistory& history) {
  log.print(" %10s: %-41s "
            UINT64_FORMAT_W(9) " / " UINT64_FORMAT_W(-9) " "
            UINT64_FORMAT_W(9) " / " UINT64_FORMAT_W(-9) " "
            UINT64_FORMAT_W(9) " / " UINT64_FORMAT_W(-9) " "
            UINT64_FORMAT_W(9) " / " UINT64_FORMAT_W(-9) "   MB",
            sampler.group(),
            sampler.name(),
            history.avg_10_seconds() / M,
            history.max_10_seconds() / M,
            history.avg_10_minutes() / M,
            history.max_10_minutes() / M,
            history.avg_10_hours() / M,
            history.max_10_hours() / M,
            history.avg_total() / M,
            history.max_total() / M);
}

void XStatUnitThreads(LogTargetHandle log, const XStatSampler& sampler, const XStatSamplerHistory& history) {
  log.print(" %10s: %-41s "
            UINT64_FORMAT_W(9) " / " UINT64_FORMAT_W(-9) " "
            UINT64_FORMAT_W(9) " / " UINT64_FORMAT_W(-9) " "
            UINT64_FORMAT_W(9) " / " UINT64_FORMAT_W(-9) " "
            UINT64_FORMAT_W(9) " / " UINT64_FORMAT_W(-9) "   threads",
            sampler.group(),
            sampler.name(),
            history.avg_10_seconds(),
            history.max_10_seconds(),
            history.avg_10_minutes(),
            history.max_10_minutes(),
            history.avg_10_hours(),
            history.max_10_hours(),
            history.avg_total(),
            history.max_total());
}

void XStatUnitBytesPerSecond(LogTargetHandle log, const XStatSampler& sampler, const XStatSamplerHistory& history) {
  log.print(" %10s: %-41s "
            UINT64_FORMAT_W(9) " / " UINT64_FORMAT_W(-9) " "
            UINT64_FORMAT_W(9) " / " UINT64_FORMAT_W(-9) " "
            UINT64_FORMAT_W(9) " / " UINT64_FORMAT_W(-9) " "
            UINT64_FORMAT_W(9) " / " UINT64_FORMAT_W(-9) "   MB/s",
            sampler.group(),
            sampler.name(),
            history.avg_10_seconds() / M,
            history.max_10_seconds() / M,
            history.avg_10_minutes() / M,
            history.max_10_minutes() / M,
            history.avg_10_hours() / M,
            history.max_10_hours() / M,
            history.avg_total() / M,
            history.max_total() / M);
}

void XStatUnitOpsPerSecond(LogTargetHandle log, const XStatSampler& sampler, const XStatSamplerHistory& history) {
  log.print(" %10s: %-41s "
            UINT64_FORMAT_W(9) " / " UINT64_FORMAT_W(-9) " "
            UINT64_FORMAT_W(9) " / " UINT64_FORMAT_W(-9) " "
            UINT64_FORMAT_W(9) " / " UINT64_FORMAT_W(-9) " "
            UINT64_FORMAT_W(9) " / " UINT64_FORMAT_W(-9) "   ops/s",
            sampler.group(),
            sampler.name(),
            history.avg_10_seconds(),
            history.max_10_seconds(),
            history.avg_10_minutes(),
            history.max_10_minutes(),
            history.avg_10_hours(),
            history.max_10_hours(),
            history.avg_total(),
            history.max_total());
}

//
// Stat value
//
uintptr_t XStatValue::_base = 0;
uint32_t  XStatValue::_cpu_offset = 0;

XStatValue::XStatValue(const char* group,
                          const char* name,
                          uint32_t id,
                          uint32_t size) :
    _group(group),
    _name(name),
    _id(id),
    _offset(_cpu_offset) {
  assert(_base == 0, "Already initialized");
  _cpu_offset += size;
}

template <typename T>
T* XStatValue::get_cpu_local(uint32_t cpu) const {
  assert(_base != 0, "Not initialized");
  const uintptr_t cpu_base = _base + (_cpu_offset * cpu);
  const uintptr_t value_addr = cpu_base + _offset;
  return (T*)value_addr;
}

void XStatValue::initialize() {
  // Finalize and align CPU offset
  _cpu_offset = align_up(_cpu_offset, (uint32_t)XCacheLineSize);

  // Allocation aligned memory
  const size_t size = _cpu_offset * XCPU::count();
  _base = XUtils::alloc_aligned(XCacheLineSize, size);
}

const char* XStatValue::group() const {
  return _group;
}

const char* XStatValue::name() const {
  return _name;
}

uint32_t XStatValue::id() const {
  return _id;
}

//
// Stat iterable value
//

template <typename T>
XStatIterableValue<T>::XStatIterableValue(const char* group,
                                          const char* name,
                                          uint32_t size) :
    XStatValue(group, name, _count++, size),
    _next(insert()) {}

template <typename T>
T* XStatIterableValue<T>::insert() const {
  T* const next = _first;
  _first = (T*)this;
  return next;
}

template <typename T>
void XStatIterableValue<T>::sort() {
  T* first_unsorted = _first;
  _first = NULL;

  while (first_unsorted != NULL) {
    T* const value = first_unsorted;
    first_unsorted = value->_next;
    value->_next = NULL;

    T** current = &_first;

    while (*current != NULL) {
      // First sort by group, then by name
      const int group_cmp = strcmp((*current)->group(), value->group());
      if ((group_cmp > 0) || (group_cmp == 0 && strcmp((*current)->name(), value->name()) > 0)) {
        break;
      }

      current = &(*current)->_next;
    }
    value->_next = *current;
    *current = value;
  }
}

//
// Stat sampler
//
XStatSampler::XStatSampler(const char* group, const char* name, XStatUnitPrinter printer) :
    XStatIterableValue<XStatSampler>(group, name, sizeof(XStatSamplerData)),
    _printer(printer) {}

XStatSamplerData* XStatSampler::get() const {
  return get_cpu_local<XStatSamplerData>(XCPU::id());
}

XStatSamplerData XStatSampler::collect_and_reset() const {
  XStatSamplerData all;

  const uint32_t ncpus = XCPU::count();
  for (uint32_t i = 0; i < ncpus; i++) {
    XStatSamplerData* const cpu_data = get_cpu_local<XStatSamplerData>(i);
    if (cpu_data->_nsamples > 0) {
      const uint64_t nsamples = Atomic::xchg(&cpu_data->_nsamples, (uint64_t)0);
      const uint64_t sum = Atomic::xchg(&cpu_data->_sum, (uint64_t)0);
      const uint64_t max = Atomic::xchg(&cpu_data->_max, (uint64_t)0);
      all._nsamples += nsamples;
      all._sum += sum;
      if (all._max < max) {
        all._max = max;
      }
    }
  }

  return all;
}

XStatUnitPrinter XStatSampler::printer() const {
  return _printer;
}

//
// Stat counter
//
XStatCounter::XStatCounter(const char* group, const char* name, XStatUnitPrinter printer) :
    XStatIterableValue<XStatCounter>(group, name, sizeof(XStatCounterData)),
    _sampler(group, name, printer) {}

XStatCounterData* XStatCounter::get() const {
  return get_cpu_local<XStatCounterData>(XCPU::id());
}

void XStatCounter::sample_and_reset() const {
  uint64_t counter = 0;

  const uint32_t ncpus = XCPU::count();
  for (uint32_t i = 0; i < ncpus; i++) {
    XStatCounterData* const cpu_data = get_cpu_local<XStatCounterData>(i);
    counter += Atomic::xchg(&cpu_data->_counter, (uint64_t)0);
  }

  XStatSample(_sampler, counter);
}

//
// Stat unsampled counter
//
XStatUnsampledCounter::XStatUnsampledCounter(const char* name) :
    XStatIterableValue<XStatUnsampledCounter>("Unsampled", name, sizeof(XStatCounterData)) {}

XStatCounterData* XStatUnsampledCounter::get() const {
  return get_cpu_local<XStatCounterData>(XCPU::id());
}

XStatCounterData XStatUnsampledCounter::collect_and_reset() const {
  XStatCounterData all;

  const uint32_t ncpus = XCPU::count();
  for (uint32_t i = 0; i < ncpus; i++) {
    XStatCounterData* const cpu_data = get_cpu_local<XStatCounterData>(i);
    all._counter += Atomic::xchg(&cpu_data->_counter, (uint64_t)0);
  }

  return all;
}

//
// Stat MMU (Minimum Mutator Utilization)
//
XStatMMUPause::XStatMMUPause() :
    _start(0.0),
    _end(0.0) {}

XStatMMUPause::XStatMMUPause(const Ticks& start, const Ticks& end) :
    _start(TimeHelper::counter_to_millis(start.value())),
    _end(TimeHelper::counter_to_millis(end.value())) {}

double XStatMMUPause::end() const {
  return _end;
}

double XStatMMUPause::overlap(double start, double end) const {
  const double start_max = MAX2(start, _start);
  const double end_min = MIN2(end, _end);

  if (end_min > start_max) {
    // Overlap found
    return end_min - start_max;
  }

  // No overlap
  return 0.0;
}

size_t XStatMMU::_next = 0;
size_t XStatMMU::_npauses = 0;
XStatMMUPause XStatMMU::_pauses[200];
double XStatMMU::_mmu_2ms = 100.0;
double XStatMMU::_mmu_5ms = 100.0;
double XStatMMU::_mmu_10ms = 100.0;
double XStatMMU::_mmu_20ms = 100.0;
double XStatMMU::_mmu_50ms = 100.0;
double XStatMMU::_mmu_100ms = 100.0;

const XStatMMUPause& XStatMMU::pause(size_t index) {
  return _pauses[(_next - index - 1) % ARRAY_SIZE(_pauses)];
}

double XStatMMU::calculate_mmu(double time_slice) {
  const double end = pause(0).end();
  const double start = end - time_slice;
  double time_paused = 0.0;

  // Find all overlapping pauses
  for (size_t i = 0; i < _npauses; i++) {
    const double overlap = pause(i).overlap(start, end);
    if (overlap == 0.0) {
      // No overlap
      break;
    }

    time_paused += overlap;
  }

  // Calculate MMU
  const double time_mutator = time_slice - time_paused;
  return percent_of(time_mutator, time_slice);
}

void XStatMMU::register_pause(const Ticks& start, const Ticks& end) {
  // Add pause
  const size_t index = _next++ % ARRAY_SIZE(_pauses);
  _pauses[index] = XStatMMUPause(start, end);
  _npauses = MIN2(_npauses + 1, ARRAY_SIZE(_pauses));

  // Recalculate MMUs
  _mmu_2ms    = MIN2(_mmu_2ms,   calculate_mmu(2));
  _mmu_5ms    = MIN2(_mmu_5ms,   calculate_mmu(5));
  _mmu_10ms   = MIN2(_mmu_10ms,  calculate_mmu(10));
  _mmu_20ms   = MIN2(_mmu_20ms,  calculate_mmu(20));
  _mmu_50ms   = MIN2(_mmu_50ms,  calculate_mmu(50));
  _mmu_100ms  = MIN2(_mmu_100ms, calculate_mmu(100));
}

void XStatMMU::print() {
  log_info(gc, mmu)("MMU: 2ms/%.1f%%, 5ms/%.1f%%, 10ms/%.1f%%, 20ms/%.1f%%, 50ms/%.1f%%, 100ms/%.1f%%",
                    _mmu_2ms, _mmu_5ms, _mmu_10ms, _mmu_20ms, _mmu_50ms, _mmu_100ms);
}

//
// Stat phases
//
ConcurrentGCTimer XStatPhase::_timer;

XStatPhase::XStatPhase(const char* group, const char* name) :
    _sampler(group, name, XStatUnitTime) {}

void XStatPhase::log_start(LogTargetHandle log, bool thread) const {
  if (!log.is_enabled()) {
    return;
  }

  if (thread) {
    ResourceMark rm;
    log.print("%s (%s)", name(), Thread::current()->name());
  } else {
    log.print("%s", name());
  }
}

void XStatPhase::log_end(LogTargetHandle log, const Tickspan& duration, bool thread) const {
  if (!log.is_enabled()) {
    return;
  }

  if (thread) {
    ResourceMark rm;
    log.print("%s (%s) %.3fms", name(), Thread::current()->name(), TimeHelper::counter_to_millis(duration.value()));
  } else {
    log.print("%s %.3fms", name(), TimeHelper::counter_to_millis(duration.value()));
  }
}

ConcurrentGCTimer* XStatPhase::timer() {
  return &_timer;
}

const char* XStatPhase::name() const {
  return _sampler.name();
}

XStatPhaseCycle::XStatPhaseCycle(const char* name) :
    XStatPhase("Collector", name) {}

void XStatPhaseCycle::register_start(const Ticks& start) const {
  timer()->register_gc_start(start);

  XTracer::tracer()->report_gc_start(XCollectedHeap::heap()->gc_cause(), start);

  XCollectedHeap::heap()->print_heap_before_gc();
  XCollectedHeap::heap()->trace_heap_before_gc(XTracer::tracer());

  log_info(gc, start)("Garbage Collection (%s)",
                       GCCause::to_string(XCollectedHeap::heap()->gc_cause()));
}

void XStatPhaseCycle::register_end(const Ticks& start, const Ticks& end) const {
  if (XAbort::should_abort()) {
    log_info(gc)("Garbage Collection (%s) Aborted",
                 GCCause::to_string(XCollectedHeap::heap()->gc_cause()));
    return;
  }

  timer()->register_gc_end(end);

  XCollectedHeap::heap()->print_heap_after_gc();
  XCollectedHeap::heap()->trace_heap_after_gc(XTracer::tracer());

  XTracer::tracer()->report_gc_end(end, timer()->time_partitions());

  const Tickspan duration = end - start;
  XStatSample(_sampler, duration.value());

  XStatLoad::print();
  XStatMMU::print();
  XStatMark::print();
  XStatNMethods::print();
  XStatMetaspace::print();
  XStatReferences::print();
  XStatRelocation::print();
  XStatHeap::print();

  log_info(gc)("Garbage Collection (%s) " XSIZE_FMT "->" XSIZE_FMT,
               GCCause::to_string(XCollectedHeap::heap()->gc_cause()),
               XSIZE_ARGS(XStatHeap::used_at_mark_start()),
               XSIZE_ARGS(XStatHeap::used_at_relocate_end()));
}

Tickspan XStatPhasePause::_max;

XStatPhasePause::XStatPhasePause(const char* name) :
    XStatPhase("Phase", name) {}

const Tickspan& XStatPhasePause::max() {
  return _max;
}

void XStatPhasePause::register_start(const Ticks& start) const {
  timer()->register_gc_pause_start(name(), start);

  LogTarget(Debug, gc, phases, start) log;
  log_start(log);
}

void XStatPhasePause::register_end(const Ticks& start, const Ticks& end) const {
  timer()->register_gc_pause_end(end);

  const Tickspan duration = end - start;
  XStatSample(_sampler, duration.value());

  // Track max pause time
  if (_max < duration) {
    _max = duration;
  }

  // Track minimum mutator utilization
  XStatMMU::register_pause(start, end);

  LogTarget(Info, gc, phases) log;
  log_end(log, duration);
}

XStatPhaseConcurrent::XStatPhaseConcurrent(const char* name) :
    XStatPhase("Phase", name) {}

void XStatPhaseConcurrent::register_start(const Ticks& start) const {
  timer()->register_gc_concurrent_start(name(), start);

  LogTarget(Debug, gc, phases, start) log;
  log_start(log);
}

void XStatPhaseConcurrent::register_end(const Ticks& start, const Ticks& end) const {
  if (XAbort::should_abort()) {
    return;
  }

  timer()->register_gc_concurrent_end(end);

  const Tickspan duration = end - start;
  XStatSample(_sampler, duration.value());

  LogTarget(Info, gc, phases) log;
  log_end(log, duration);
}

XStatSubPhase::XStatSubPhase(const char* name) :
    XStatPhase("Subphase", name) {}

void XStatSubPhase::register_start(const Ticks& start) const {
  if (XThread::is_worker()) {
    LogTarget(Trace, gc, phases, start) log;
    log_start(log, true /* thread */);
  } else {
    LogTarget(Debug, gc, phases, start) log;
    log_start(log, false /* thread */);
  }
}

void XStatSubPhase::register_end(const Ticks& start, const Ticks& end) const {
  if (XAbort::should_abort()) {
    return;
  }

  XTracer::tracer()->report_thread_phase(name(), start, end);

  const Tickspan duration = end - start;
  XStatSample(_sampler, duration.value());

  if (XThread::is_worker()) {
    LogTarget(Trace, gc, phases) log;
    log_end(log, duration, true /* thread */);
  } else {
    LogTarget(Debug, gc, phases) log;
    log_end(log, duration, false /* thread */);
  }
}

XStatCriticalPhase::XStatCriticalPhase(const char* name, bool verbose) :
    XStatPhase("Critical", name),
    _counter("Critical", name, XStatUnitOpsPerSecond),
    _verbose(verbose) {}

void XStatCriticalPhase::register_start(const Ticks& start) const {
  // This is called from sensitive contexts, for example before an allocation stall
  // has been resolved. This means we must not access any oops in here since that
  // could lead to infinite recursion. Without access to the thread name we can't
  // really log anything useful here.
}

void XStatCriticalPhase::register_end(const Ticks& start, const Ticks& end) const {
  XTracer::tracer()->report_thread_phase(name(), start, end);

  const Tickspan duration = end - start;
  XStatSample(_sampler, duration.value());
  XStatInc(_counter);

  if (_verbose) {
    LogTarget(Info, gc) log;
    log_end(log, duration, true /* thread */);
  } else {
    LogTarget(Debug, gc) log;
    log_end(log, duration, true /* thread */);
  }
}

//
// Stat timer
//
THREAD_LOCAL uint32_t XStatTimerDisable::_active = 0;

//
// Stat sample/inc
//
void XStatSample(const XStatSampler& sampler, uint64_t value) {
  XStatSamplerData* const cpu_data = sampler.get();
  Atomic::add(&cpu_data->_nsamples, 1u);
  Atomic::add(&cpu_data->_sum, value);

  uint64_t max = cpu_data->_max;
  for (;;) {
    if (max >= value) {
      // Not max
      break;
    }

    const uint64_t new_max = value;
    const uint64_t prev_max = Atomic::cmpxchg(&cpu_data->_max, max, new_max);
    if (prev_max == max) {
      // Success
      break;
    }

    // Retry
    max = prev_max;
  }

  XTracer::tracer()->report_stat_sampler(sampler, value);
}

void XStatInc(const XStatCounter& counter, uint64_t increment) {
  XStatCounterData* const cpu_data = counter.get();
  const uint64_t value = Atomic::add(&cpu_data->_counter, increment);

  XTracer::tracer()->report_stat_counter(counter, increment, value);
}

void XStatInc(const XStatUnsampledCounter& counter, uint64_t increment) {
  XStatCounterData* const cpu_data = counter.get();
  Atomic::add(&cpu_data->_counter, increment);
}

//
// Stat allocation rate
//
const XStatUnsampledCounter XStatAllocRate::_counter("Allocation Rate");
TruncatedSeq                XStatAllocRate::_samples(XStatAllocRate::sample_hz);
TruncatedSeq                XStatAllocRate::_rate(XStatAllocRate::sample_hz);

const XStatUnsampledCounter& XStatAllocRate::counter() {
  return _counter;
}

uint64_t XStatAllocRate::sample_and_reset() {
  const XStatCounterData bytes_per_sample = _counter.collect_and_reset();
  _samples.add(bytes_per_sample._counter);

  const uint64_t bytes_per_second = _samples.sum();
  _rate.add(bytes_per_second);

  return bytes_per_second;
}

double XStatAllocRate::predict() {
  return _rate.predict_next();
}

double XStatAllocRate::avg() {
  return _rate.avg();
}

double XStatAllocRate::sd() {
  return _rate.sd();
}

//
// Stat thread
//
XStat::XStat() :
    _metronome(sample_hz) {
  set_name("XStat");
  create_and_start();
}

void XStat::sample_and_collect(XStatSamplerHistory* history) const {
  // Sample counters
  for (const XStatCounter* counter = XStatCounter::first(); counter != NULL; counter = counter->next()) {
    counter->sample_and_reset();
  }

  // Collect samples
  for (const XStatSampler* sampler = XStatSampler::first(); sampler != NULL; sampler = sampler->next()) {
    XStatSamplerHistory& sampler_history = history[sampler->id()];
    sampler_history.add(sampler->collect_and_reset());
  }
}

bool XStat::should_print(LogTargetHandle log) const {
  static uint64_t print_at = ZStatisticsInterval;
  const uint64_t now = os::elapsedTime();

  if (now < print_at) {
    return false;
  }

  print_at = ((now / ZStatisticsInterval) * ZStatisticsInterval) + ZStatisticsInterval;

  return log.is_enabled();
}

void XStat::print(LogTargetHandle log, const XStatSamplerHistory* history) const {
  // Print
  log.print("=== Garbage Collection Statistics =======================================================================================================================");
  log.print("                                                             Last 10s              Last 10m              Last 10h                Total");
  log.print("                                                             Avg / Max             Avg / Max             Avg / Max             Avg / Max");

  for (const XStatSampler* sampler = XStatSampler::first(); sampler != NULL; sampler = sampler->next()) {
    const XStatSamplerHistory& sampler_history = history[sampler->id()];
    const XStatUnitPrinter printer = sampler->printer();
    printer(log, *sampler, sampler_history);
  }

  log.print("=========================================================================================================================================================");
}

void XStat::run_service() {
  XStatSamplerHistory* const history = new XStatSamplerHistory[XStatSampler::count()];
  LogTarget(Info, gc, stats) log;

  XStatSampler::sort();

  // Main loop
  while (_metronome.wait_for_tick()) {
    sample_and_collect(history);
    if (should_print(log)) {
      print(log, history);
    }
  }

  delete [] history;
}

void XStat::stop_service() {
  _metronome.stop();
}

//
// Stat table
//
class XStatTablePrinter {
private:
  static const size_t _buffer_size = 256;

  const size_t _column0_width;
  const size_t _columnN_width;
  char         _buffer[_buffer_size];

public:
  class XColumn {
  private:
    char* const  _buffer;
    const size_t _position;
    const size_t _width;
    const size_t _width_next;

    XColumn next() const {
      // Insert space between columns
      _buffer[_position + _width] = ' ';
      return XColumn(_buffer, _position + _width + 1, _width_next, _width_next);
    }

    size_t print(size_t position, const char* fmt, va_list va) {
      const int res = jio_vsnprintf(_buffer + position, _buffer_size - position, fmt, va);
      if (res < 0) {
        return 0;
      }

      return (size_t)res;
    }

  public:
    XColumn(char* buffer, size_t position, size_t width, size_t width_next) :
        _buffer(buffer),
        _position(position),
        _width(width),
        _width_next(width_next) {}

    XColumn left(const char* fmt, ...) ATTRIBUTE_PRINTF(2, 3) {
      va_list va;

      va_start(va, fmt);
      const size_t written = print(_position, fmt, va);
      va_end(va);

      if (written < _width) {
        // Fill empty space
        memset(_buffer + _position + written, ' ', _width - written);
      }

      return next();
    }

    XColumn right(const char* fmt, ...) ATTRIBUTE_PRINTF(2, 3) {
      va_list va;

      va_start(va, fmt);
      const size_t written = print(_position, fmt, va);
      va_end(va);

      if (written > _width) {
        // Line too long
        return fill('?');
      }

      if (written < _width) {
        // Short line, move all to right
        memmove(_buffer + _position + _width - written, _buffer + _position, written);

        // Fill empty space
        memset(_buffer + _position, ' ', _width - written);
      }

      return next();
    }

    XColumn center(const char* fmt, ...) ATTRIBUTE_PRINTF(2, 3) {
      va_list va;

      va_start(va, fmt);
      const size_t written = print(_position, fmt, va);
      va_end(va);

      if (written > _width) {
        // Line too long
        return fill('?');
      }

      if (written < _width) {
        // Short line, move all to center
        const size_t start_space = (_width - written) / 2;
        const size_t end_space = _width - written - start_space;
        memmove(_buffer + _position + start_space, _buffer + _position, written);

        // Fill empty spaces
        memset(_buffer + _position, ' ', start_space);
        memset(_buffer + _position + start_space + written, ' ', end_space);
      }

      return next();
    }

    XColumn fill(char filler = ' ') {
      memset(_buffer + _position, filler, _width);
      return next();
    }

    const char* end() {
      _buffer[_position] = '\0';
      return _buffer;
    }
  };

public:
  XStatTablePrinter(size_t column0_width, size_t columnN_width) :
      _column0_width(column0_width),
      _columnN_width(columnN_width) {}

  XColumn operator()() {
    return XColumn(_buffer, 0, _column0_width, _columnN_width);
  }
};

//
// Stat cycle
//
uint64_t  XStatCycle::_nwarmup_cycles = 0;
Ticks     XStatCycle::_start_of_last;
Ticks     XStatCycle::_end_of_last;
NumberSeq XStatCycle::_serial_time(0.7 /* alpha */);
NumberSeq XStatCycle::_parallelizable_time(0.7 /* alpha */);
uint      XStatCycle::_last_active_workers = 0;

void XStatCycle::at_start() {
  _start_of_last = Ticks::now();
}

void XStatCycle::at_end(GCCause::Cause cause, uint active_workers) {
  _end_of_last = Ticks::now();

  if (cause == GCCause::_z_warmup) {
    _nwarmup_cycles++;
  }

  _last_active_workers = active_workers;

  // Calculate serial and parallelizable GC cycle times
  const double duration = (_end_of_last - _start_of_last).seconds();
  const double workers_duration = XStatWorkers::get_and_reset_duration();
  const double serial_time = duration - workers_duration;
  const double parallelizable_time = workers_duration * active_workers;
  _serial_time.add(serial_time);
  _parallelizable_time.add(parallelizable_time);
}

bool XStatCycle::is_warm() {
  return _nwarmup_cycles >= 3;
}

uint64_t XStatCycle::nwarmup_cycles() {
  return _nwarmup_cycles;
}

bool XStatCycle::is_time_trustable() {
  // The times are considered trustable if we
  // have completed at least one warmup cycle.
  return _nwarmup_cycles > 0;
}

const AbsSeq& XStatCycle::serial_time() {
  return _serial_time;
}

const AbsSeq& XStatCycle::parallelizable_time() {
  return _parallelizable_time;
}

uint XStatCycle::last_active_workers() {
  return _last_active_workers;
}

double XStatCycle::time_since_last() {
  if (_end_of_last.value() == 0) {
    // No end recorded yet, return time since VM start
    return os::elapsedTime();
  }

  const Ticks now = Ticks::now();
  const Tickspan time_since_last = now - _end_of_last;
  return time_since_last.seconds();
}

//
// Stat workers
//
Ticks XStatWorkers::_start_of_last;
Tickspan XStatWorkers::_accumulated_duration;

void XStatWorkers::at_start() {
  _start_of_last = Ticks::now();
}

void XStatWorkers::at_end() {
  const Ticks now = Ticks::now();
  const Tickspan duration = now - _start_of_last;
  _accumulated_duration += duration;
}

double XStatWorkers::get_and_reset_duration() {
  const double duration = _accumulated_duration.seconds();
  const Ticks now = Ticks::now();
  _accumulated_duration = now - now;
  return duration;
}

//
// Stat load
//
void XStatLoad::print() {
  double loadavg[3] = {};
  os::loadavg(loadavg, ARRAY_SIZE(loadavg));
  log_info(gc, load)("Load: %.2f/%.2f/%.2f", loadavg[0], loadavg[1], loadavg[2]);
}

//
// Stat mark
//
size_t XStatMark::_nstripes;
size_t XStatMark::_nproactiveflush;
size_t XStatMark::_nterminateflush;
size_t XStatMark::_ntrycomplete;
size_t XStatMark::_ncontinue;
size_t XStatMark::_mark_stack_usage;

void XStatMark::set_at_mark_start(size_t nstripes) {
  _nstripes = nstripes;
}

void XStatMark::set_at_mark_end(size_t nproactiveflush,
                                size_t nterminateflush,
                                size_t ntrycomplete,
                                size_t ncontinue) {
  _nproactiveflush = nproactiveflush;
  _nterminateflush = nterminateflush;
  _ntrycomplete = ntrycomplete;
  _ncontinue = ncontinue;
}

void XStatMark::set_at_mark_free(size_t mark_stack_usage) {
  _mark_stack_usage = mark_stack_usage;
}

void XStatMark::print() {
  log_info(gc, marking)("Mark: "
                        SIZE_FORMAT " stripe(s), "
                        SIZE_FORMAT " proactive flush(es), "
                        SIZE_FORMAT " terminate flush(es), "
                        SIZE_FORMAT " completion(s), "
                        SIZE_FORMAT " continuation(s) ",
                        _nstripes,
                        _nproactiveflush,
                        _nterminateflush,
                        _ntrycomplete,
                        _ncontinue);

  log_info(gc, marking)("Mark Stack Usage: " SIZE_FORMAT "M", _mark_stack_usage / M);
}

//
// Stat relocation
//
XRelocationSetSelectorStats XStatRelocation::_selector_stats;
size_t                      XStatRelocation::_forwarding_usage;
size_t                      XStatRelocation::_small_in_place_count;
size_t                      XStatRelocation::_medium_in_place_count;

void XStatRelocation::set_at_select_relocation_set(const XRelocationSetSelectorStats& selector_stats) {
  _selector_stats = selector_stats;
}

void XStatRelocation::set_at_install_relocation_set(size_t forwarding_usage) {
  _forwarding_usage = forwarding_usage;
}

void XStatRelocation::set_at_relocate_end(size_t small_in_place_count, size_t medium_in_place_count) {
  _small_in_place_count = small_in_place_count;
  _medium_in_place_count = medium_in_place_count;
}

void XStatRelocation::print(const char* name,
                            const XRelocationSetSelectorGroupStats& selector_group,
                            size_t in_place_count) {
  log_info(gc, reloc)("%s Pages: " SIZE_FORMAT " / " SIZE_FORMAT "M, Empty: " SIZE_FORMAT "M, "
                      "Relocated: " SIZE_FORMAT "M, In-Place: " SIZE_FORMAT,
                      name,
                      selector_group.npages_candidates(),
                      selector_group.total() / M,
                      selector_group.empty() / M,
                      selector_group.relocate() / M,
                      in_place_count);
}

void XStatRelocation::print() {
  print("Small", _selector_stats.small(), _small_in_place_count);
  if (XPageSizeMedium != 0) {
    print("Medium", _selector_stats.medium(), _medium_in_place_count);
  }
  print("Large", _selector_stats.large(), 0 /* in_place_count */);

  log_info(gc, reloc)("Forwarding Usage: " SIZE_FORMAT "M", _forwarding_usage / M);
}

//
// Stat nmethods
//
void XStatNMethods::print() {
  log_info(gc, nmethod)("NMethods: " SIZE_FORMAT " registered, " SIZE_FORMAT " unregistered",
                        XNMethodTable::registered_nmethods(),
                        XNMethodTable::unregistered_nmethods());
}

//
// Stat metaspace
//
void XStatMetaspace::print() {
  MetaspaceCombinedStats stats = MetaspaceUtils::get_combined_statistics();
  log_info(gc, metaspace)("Metaspace: "
                          SIZE_FORMAT "M used, "
                          SIZE_FORMAT "M committed, " SIZE_FORMAT "M reserved",
                          stats.used() / M,
                          stats.committed() / M,
                          stats.reserved() / M);
}

//
// Stat references
//
XStatReferences::XCount XStatReferences::_soft;
XStatReferences::XCount XStatReferences::_weak;
XStatReferences::XCount XStatReferences::_final;
XStatReferences::XCount XStatReferences::_phantom;

void XStatReferences::set(XCount* count, size_t encountered, size_t discovered, size_t enqueued) {
  count->encountered = encountered;
  count->discovered = discovered;
  count->enqueued = enqueued;
}

void XStatReferences::set_soft(size_t encountered, size_t discovered, size_t enqueued) {
  set(&_soft, encountered, discovered, enqueued);
}

void XStatReferences::set_weak(size_t encountered, size_t discovered, size_t enqueued) {
  set(&_weak, encountered, discovered, enqueued);
}

void XStatReferences::set_final(size_t encountered, size_t discovered, size_t enqueued) {
  set(&_final, encountered, discovered, enqueued);
}

void XStatReferences::set_phantom(size_t encountered, size_t discovered, size_t enqueued) {
  set(&_phantom, encountered, discovered, enqueued);
}

void XStatReferences::print(const char* name, const XStatReferences::XCount& ref) {
  log_info(gc, ref)("%s: "
                    SIZE_FORMAT " encountered, "
                    SIZE_FORMAT " discovered, "
                    SIZE_FORMAT " enqueued",
                    name,
                    ref.encountered,
                    ref.discovered,
                    ref.enqueued);
}

void XStatReferences::print() {
  print("Soft", _soft);
  print("Weak", _weak);
  print("Final", _final);
  print("Phantom", _phantom);
}

//
// Stat heap
//
XStatHeap::XAtInitialize XStatHeap::_at_initialize;
XStatHeap::XAtMarkStart XStatHeap::_at_mark_start;
XStatHeap::XAtMarkEnd XStatHeap::_at_mark_end;
XStatHeap::XAtRelocateStart XStatHeap::_at_relocate_start;
XStatHeap::XAtRelocateEnd XStatHeap::_at_relocate_end;

size_t XStatHeap::capacity_high() {
  return MAX4(_at_mark_start.capacity,
              _at_mark_end.capacity,
              _at_relocate_start.capacity,
              _at_relocate_end.capacity);
}

size_t XStatHeap::capacity_low() {
  return MIN4(_at_mark_start.capacity,
              _at_mark_end.capacity,
              _at_relocate_start.capacity,
              _at_relocate_end.capacity);
}

size_t XStatHeap::free(size_t used) {
  return _at_initialize.max_capacity - used;
}

size_t XStatHeap::allocated(size_t used, size_t reclaimed) {
  // The amount of allocated memory between point A and B is used(B) - used(A).
  // However, we might also have reclaimed memory between point A and B. This
  // means the current amount of used memory must be incremented by the amount
  // reclaimed, so that used(B) represents the amount of used memory we would
  // have had if we had not reclaimed anything.
  return (used + reclaimed) - _at_mark_start.used;
}

size_t XStatHeap::garbage(size_t reclaimed) {
  return _at_mark_end.garbage - reclaimed;
}

void XStatHeap::set_at_initialize(const XPageAllocatorStats& stats) {
  _at_initialize.min_capacity = stats.min_capacity();
  _at_initialize.max_capacity = stats.max_capacity();
}

void XStatHeap::set_at_mark_start(const XPageAllocatorStats& stats) {
  _at_mark_start.soft_max_capacity = stats.soft_max_capacity();
  _at_mark_start.capacity = stats.capacity();
  _at_mark_start.free = free(stats.used());
  _at_mark_start.used = stats.used();
}

void XStatHeap::set_at_mark_end(const XPageAllocatorStats& stats) {
  _at_mark_end.capacity = stats.capacity();
  _at_mark_end.free = free(stats.used());
  _at_mark_end.used = stats.used();
  _at_mark_end.allocated = allocated(stats.used(), 0 /* reclaimed */);
}

void XStatHeap::set_at_select_relocation_set(const XRelocationSetSelectorStats& stats) {
  const size_t live = stats.small().live() + stats.medium().live() + stats.large().live();
  _at_mark_end.live = live;
  _at_mark_end.garbage = _at_mark_start.used - live;
}

void XStatHeap::set_at_relocate_start(const XPageAllocatorStats& stats) {
  _at_relocate_start.capacity = stats.capacity();
  _at_relocate_start.free = free(stats.used());
  _at_relocate_start.used = stats.used();
  _at_relocate_start.allocated = allocated(stats.used(), stats.reclaimed());
  _at_relocate_start.garbage = garbage(stats.reclaimed());
  _at_relocate_start.reclaimed = stats.reclaimed();
}

void XStatHeap::set_at_relocate_end(const XPageAllocatorStats& stats, size_t non_worker_relocated) {
  const size_t reclaimed = stats.reclaimed() - MIN2(non_worker_relocated, stats.reclaimed());

  _at_relocate_end.capacity = stats.capacity();
  _at_relocate_end.capacity_high = capacity_high();
  _at_relocate_end.capacity_low = capacity_low();
  _at_relocate_end.free = free(stats.used());
  _at_relocate_end.free_high = free(stats.used_low());
  _at_relocate_end.free_low = free(stats.used_high());
  _at_relocate_end.used = stats.used();
  _at_relocate_end.used_high = stats.used_high();
  _at_relocate_end.used_low = stats.used_low();
  _at_relocate_end.allocated = allocated(stats.used(), reclaimed);
  _at_relocate_end.garbage = garbage(reclaimed);
  _at_relocate_end.reclaimed = reclaimed;
}

size_t XStatHeap::max_capacity() {
  return _at_initialize.max_capacity;
}

size_t XStatHeap::used_at_mark_start() {
  return _at_mark_start.used;
}

size_t XStatHeap::used_at_relocate_end() {
  return _at_relocate_end.used;
}

void XStatHeap::print() {
  log_info(gc, heap)("Min Capacity: "
                     XSIZE_FMT, XSIZE_ARGS(_at_initialize.min_capacity));
  log_info(gc, heap)("Max Capacity: "
                     XSIZE_FMT, XSIZE_ARGS(_at_initialize.max_capacity));
  log_info(gc, heap)("Soft Max Capacity: "
                     XSIZE_FMT, XSIZE_ARGS(_at_mark_start.soft_max_capacity));

  XStatTablePrinter table(10, 18);
  log_info(gc, heap)("%s", table()
                     .fill()
                     .center("Mark Start")
                     .center("Mark End")
                     .center("Relocate Start")
                     .center("Relocate End")
                     .center("High")
                     .center("Low")
                     .end());
  log_info(gc, heap)("%s", table()
                     .right("Capacity:")
                     .left(XTABLE_ARGS(_at_mark_start.capacity))
                     .left(XTABLE_ARGS(_at_mark_end.capacity))
                     .left(XTABLE_ARGS(_at_relocate_start.capacity))
                     .left(XTABLE_ARGS(_at_relocate_end.capacity))
                     .left(XTABLE_ARGS(_at_relocate_end.capacity_high))
                     .left(XTABLE_ARGS(_at_relocate_end.capacity_low))
                     .end());
  log_info(gc, heap)("%s", table()
                     .right("Free:")
                     .left(XTABLE_ARGS(_at_mark_start.free))
                     .left(XTABLE_ARGS(_at_mark_end.free))
                     .left(XTABLE_ARGS(_at_relocate_start.free))
                     .left(XTABLE_ARGS(_at_relocate_end.free))
                     .left(XTABLE_ARGS(_at_relocate_end.free_high))
                     .left(XTABLE_ARGS(_at_relocate_end.free_low))
                     .end());
  log_info(gc, heap)("%s", table()
                     .right("Used:")
                     .left(XTABLE_ARGS(_at_mark_start.used))
                     .left(XTABLE_ARGS(_at_mark_end.used))
                     .left(XTABLE_ARGS(_at_relocate_start.used))
                     .left(XTABLE_ARGS(_at_relocate_end.used))
                     .left(XTABLE_ARGS(_at_relocate_end.used_high))
                     .left(XTABLE_ARGS(_at_relocate_end.used_low))
                     .end());
  log_info(gc, heap)("%s", table()
                     .right("Live:")
                     .left(XTABLE_ARGS_NA)
                     .left(XTABLE_ARGS(_at_mark_end.live))
                     .left(XTABLE_ARGS(_at_mark_end.live /* Same as at mark end */))
                     .left(XTABLE_ARGS(_at_mark_end.live /* Same as at mark end */))
                     .left(XTABLE_ARGS_NA)
                     .left(XTABLE_ARGS_NA)
                     .end());
  log_info(gc, heap)("%s", table()
                     .right("Allocated:")
                     .left(XTABLE_ARGS_NA)
                     .left(XTABLE_ARGS(_at_mark_end.allocated))
                     .left(XTABLE_ARGS(_at_relocate_start.allocated))
                     .left(XTABLE_ARGS(_at_relocate_end.allocated))
                     .left(XTABLE_ARGS_NA)
                     .left(XTABLE_ARGS_NA)
                     .end());
  log_info(gc, heap)("%s", table()
                     .right("Garbage:")
                     .left(XTABLE_ARGS_NA)
                     .left(XTABLE_ARGS(_at_mark_end.garbage))
                     .left(XTABLE_ARGS(_at_relocate_start.garbage))
                     .left(XTABLE_ARGS(_at_relocate_end.garbage))
                     .left(XTABLE_ARGS_NA)
                     .left(XTABLE_ARGS_NA)
                     .end());
  log_info(gc, heap)("%s", table()
                     .right("Reclaimed:")
                     .left(XTABLE_ARGS_NA)
                     .left(XTABLE_ARGS_NA)
                     .left(XTABLE_ARGS(_at_relocate_start.reclaimed))
                     .left(XTABLE_ARGS(_at_relocate_end.reclaimed))
                     .left(XTABLE_ARGS_NA)
                     .left(XTABLE_ARGS_NA)
                     .end());
}
