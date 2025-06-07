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

#include "gc/shared/gc_globals.hpp"
#include "gc/z/zAbort.inline.hpp"
#include "gc/z/zCollectedHeap.hpp"
#include "gc/z/zCPU.inline.hpp"
#include "gc/z/zDirector.hpp"
#include "gc/z/zDriver.hpp"
#include "gc/z/zGeneration.inline.hpp"
#include "gc/z/zGlobals.hpp"
#include "gc/z/zNMethodTable.hpp"
#include "gc/z/zPageAge.inline.hpp"
#include "gc/z/zPageAllocator.inline.hpp"
#include "gc/z/zRelocationSetSelector.inline.hpp"
#include "gc/z/zStat.hpp"
#include "gc/z/zTracer.inline.hpp"
#include "gc/z/zUtils.inline.hpp"
#include "memory/metaspaceUtils.hpp"
#include "memory/resourceArea.hpp"
#include "runtime/atomic.hpp"
#include "runtime/os.hpp"
#include "runtime/thread.hpp"
#include "runtime/timer.hpp"
#include "utilities/align.hpp"
#include "utilities/debug.hpp"
#include "utilities/ticks.hpp"

#include <limits>

#define ZSIZE_FMT                       "%zuM(%.0f%%)"
#define ZSIZE_ARGS_WITH_MAX(size, max)  ((size) / M), (percent_of(size, max))
#define ZSIZE_ARGS(size)                ZSIZE_ARGS_WITH_MAX(size, ZStatHeap::max_capacity())

#define ZTABLE_ARGS_NA                  "%9s", "-"
#define ZTABLE_ARGS(size)               "%8zuM (%.0f%%)", \
                                        ((size) / M), (percent_of(size, ZStatHeap::max_capacity()))

//
// Stat sampler/counter data
//
struct ZStatSamplerData {
  uint64_t _nsamples;
  uint64_t _sum;
  uint64_t _max;

  ZStatSamplerData()
    : _nsamples(0),
      _sum(0),
      _max(0) {}

  void add(const ZStatSamplerData& new_sample) {
    _nsamples += new_sample._nsamples;
    _sum += new_sample._sum;
    _max = MAX2(_max, new_sample._max);
  }
};

struct ZStatCounterData {
  uint64_t _counter;

  ZStatCounterData()
    : _counter(0) {}
};

//
// Stat sampler history
//
template <size_t size>
class ZStatSamplerHistoryInterval {
private:
  size_t           _next;
  ZStatSamplerData _samples[size];
  ZStatSamplerData _accumulated;
  ZStatSamplerData _total;

public:
  ZStatSamplerHistoryInterval()
    : _next(0),
      _samples(),
      _accumulated(),
      _total() {}

  bool add(const ZStatSamplerData& new_sample) {
    // Insert sample
    const ZStatSamplerData old_sample = _samples[_next];
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
      const ZStatSamplerData zero;
      _accumulated = zero;

      // Became full
      return true;
    }

    // Not yet full
    return false;
  }

  const ZStatSamplerData& total() const {
    return _total;
  }

  const ZStatSamplerData& accumulated() const {
    return _accumulated;
  }
};

class ZStatSamplerHistory : public CHeapObj<mtGC> {
private:
  ZStatSamplerHistoryInterval<10> _10seconds;
  ZStatSamplerHistoryInterval<60> _10minutes;
  ZStatSamplerHistoryInterval<60> _10hours;
  ZStatSamplerData                _total;

  uint64_t avg(uint64_t sum, uint64_t nsamples) const {
    return (nsamples > 0) ? sum / nsamples : 0;
  }

public:
  ZStatSamplerHistory()
    : _10seconds(),
      _10minutes(),
      _10hours(),
      _total() {}

  void add(const ZStatSamplerData& new_sample) {
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
void ZStatUnitTime(LogTargetHandle log, const ZStatSampler& sampler, const ZStatSamplerHistory& history) {
  log.print(" %16s: %-41s "
            "%9.3f / %-9.3f "
            "%9.3f / %-9.3f "
            "%9.3f / %-9.3f "
            "%9.3f / %-9.3f   ms",
            sampler.group(),
            sampler.name(),
            TimeHelper::counter_to_millis((jlong)history.avg_10_seconds()),
            TimeHelper::counter_to_millis((jlong)history.max_10_seconds()),
            TimeHelper::counter_to_millis((jlong)history.avg_10_minutes()),
            TimeHelper::counter_to_millis((jlong)history.max_10_minutes()),
            TimeHelper::counter_to_millis((jlong)history.avg_10_hours()),
            TimeHelper::counter_to_millis((jlong)history.max_10_hours()),
            TimeHelper::counter_to_millis((jlong)history.avg_total()),
            TimeHelper::counter_to_millis((jlong)history.max_total()));
}

void ZStatUnitBytes(LogTargetHandle log, const ZStatSampler& sampler, const ZStatSamplerHistory& history) {
  log.print(" %16s: %-41s "
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

void ZStatUnitThreads(LogTargetHandle log, const ZStatSampler& sampler, const ZStatSamplerHistory& history) {
  log.print(" %16s: %-41s "
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

void ZStatUnitBytesPerSecond(LogTargetHandle log, const ZStatSampler& sampler, const ZStatSamplerHistory& history) {
  log.print(" %16s: %-41s "
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

void ZStatUnitOpsPerSecond(LogTargetHandle log, const ZStatSampler& sampler, const ZStatSamplerHistory& history) {
  log.print(" %16s: %-41s "
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
uintptr_t ZStatValue::_base = 0;
uint32_t  ZStatValue::_cpu_offset = 0;

ZStatValue::ZStatValue(const char* group,
                          const char* name,
                          uint32_t id,
                          uint32_t size)
  : _group(group),
    _name(name),
    _id(id),
    _offset(_cpu_offset) {
  assert(_base == 0, "Already initialized");
  _cpu_offset += size;
}

template <typename T>
T* ZStatValue::get_cpu_local(uint32_t cpu) const {
  assert(_base != 0, "Not initialized");
  const uintptr_t cpu_base = _base + (_cpu_offset * cpu);
  const uintptr_t value_addr = cpu_base + _offset;
  return (T*)value_addr;
}

void ZStatValue::initialize() {
  // Finalize and align CPU offset
  _cpu_offset = align_up(_cpu_offset, (uint32_t)ZCacheLineSize);

  // Allocation aligned memory
  const size_t size = _cpu_offset * ZCPU::count();
  _base = ZUtils::alloc_aligned_unfreeable(ZCacheLineSize, size);
}

const char* ZStatValue::group() const {
  return _group;
}

const char* ZStatValue::name() const {
  return _name;
}

uint32_t ZStatValue::id() const {
  return _id;
}

//
// Stat iterable value
//
template <typename T> uint32_t ZStatIterableValue<T>::_count = 0;
template <typename T> T*       ZStatIterableValue<T>::_first = nullptr;

template <typename T>
ZStatIterableValue<T>::ZStatIterableValue(const char* group,
                                          const char* name,
                                          uint32_t size) :
    ZStatValue(group, name, _count++, size),
    _next(insert()) {}

template <typename T>
T* ZStatIterableValue<T>::insert() const {
  T* const next = _first;
  _first = (T*)this;
  return next;
}

template <typename T>
void ZStatIterableValue<T>::sort() {
  T* first_unsorted = _first;
  _first = nullptr;

  while (first_unsorted != nullptr) {
    T* const value = first_unsorted;
    first_unsorted = value->_next;
    value->_next = nullptr;

    T** current = &_first;

    while (*current != nullptr) {
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
ZStatSampler::ZStatSampler(const char* group, const char* name, ZStatUnitPrinter printer)
  : ZStatIterableValue<ZStatSampler>(group, name, sizeof(ZStatSamplerData)),
    _printer(printer) {}

ZStatSamplerData* ZStatSampler::get() const {
  return get_cpu_local<ZStatSamplerData>(ZCPU::id());
}

ZStatSamplerData ZStatSampler::collect_and_reset() const {
  ZStatSamplerData all;

  const uint32_t ncpus = ZCPU::count();
  for (uint32_t i = 0; i < ncpus; i++) {
    ZStatSamplerData* const cpu_data = get_cpu_local<ZStatSamplerData>(i);
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

ZStatUnitPrinter ZStatSampler::printer() const {
  return _printer;
}

//
// Stat counter
//
ZStatCounter::ZStatCounter(const char* group, const char* name, ZStatUnitPrinter printer)
  : ZStatIterableValue<ZStatCounter>(group, name, sizeof(ZStatCounterData)),
    _sampler(group, name, printer) {}

ZStatCounterData* ZStatCounter::get() const {
  return get_cpu_local<ZStatCounterData>(ZCPU::id());
}

void ZStatCounter::sample_and_reset() const {
  uint64_t counter = 0;

  const uint32_t ncpus = ZCPU::count();
  for (uint32_t i = 0; i < ncpus; i++) {
    ZStatCounterData* const cpu_data = get_cpu_local<ZStatCounterData>(i);
    counter += Atomic::xchg(&cpu_data->_counter, (uint64_t)0);
  }

  ZStatSample(_sampler, counter);
}

//
// Stat unsampled counter
//
ZStatUnsampledCounter::ZStatUnsampledCounter(const char* name)
  : ZStatIterableValue<ZStatUnsampledCounter>("Unsampled", name, sizeof(ZStatCounterData)) {}

ZStatCounterData* ZStatUnsampledCounter::get() const {
  return get_cpu_local<ZStatCounterData>(ZCPU::id());
}

ZStatCounterData ZStatUnsampledCounter::collect_and_reset() const {
  ZStatCounterData all;

  const uint32_t ncpus = ZCPU::count();
  for (uint32_t i = 0; i < ncpus; i++) {
    ZStatCounterData* const cpu_data = get_cpu_local<ZStatCounterData>(i);
    all._counter += Atomic::xchg(&cpu_data->_counter, (uint64_t)0);
  }

  return all;
}

//
// Stat MMU (Minimum Mutator Utilization)
//
ZStatMMUPause::ZStatMMUPause()
  : _start(0.0),
    _end(0.0) {}

ZStatMMUPause::ZStatMMUPause(const Ticks& start, const Ticks& end)
  : _start(TimeHelper::counter_to_millis(start.value())),
    _end(TimeHelper::counter_to_millis(end.value())) {}

double ZStatMMUPause::end() const {
  return _end;
}

double ZStatMMUPause::overlap(double start, double end) const {
  const double start_max = MAX2(start, _start);
  const double end_min = MIN2(end, _end);

  if (end_min > start_max) {
    // Overlap found
    return end_min - start_max;
  }

  // No overlap
  return 0.0;
}

size_t ZStatMMU::_next = 0;
size_t ZStatMMU::_npauses = 0;
ZStatMMUPause ZStatMMU::_pauses[200];
double ZStatMMU::_mmu_2ms = 100.0;
double ZStatMMU::_mmu_5ms = 100.0;
double ZStatMMU::_mmu_10ms = 100.0;
double ZStatMMU::_mmu_20ms = 100.0;
double ZStatMMU::_mmu_50ms = 100.0;
double ZStatMMU::_mmu_100ms = 100.0;

const ZStatMMUPause& ZStatMMU::pause(size_t index) {
  return _pauses[(_next - index - 1) % ARRAY_SIZE(_pauses)];
}

double ZStatMMU::calculate_mmu(double time_slice) {
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

void ZStatMMU::register_pause(const Ticks& start, const Ticks& end) {
  // Add pause
  const size_t index = _next++ % ARRAY_SIZE(_pauses);
  _pauses[index] = ZStatMMUPause(start, end);
  _npauses = MIN2(_npauses + 1, ARRAY_SIZE(_pauses));

  // Recalculate MMUs
  _mmu_2ms    = MIN2(_mmu_2ms,   calculate_mmu(2));
  _mmu_5ms    = MIN2(_mmu_5ms,   calculate_mmu(5));
  _mmu_10ms   = MIN2(_mmu_10ms,  calculate_mmu(10));
  _mmu_20ms   = MIN2(_mmu_20ms,  calculate_mmu(20));
  _mmu_50ms   = MIN2(_mmu_50ms,  calculate_mmu(50));
  _mmu_100ms  = MIN2(_mmu_100ms, calculate_mmu(100));
}

void ZStatMMU::print() {
  log_info(gc, mmu)("MMU: 2ms/%.1f%%, 5ms/%.1f%%, 10ms/%.1f%%, 20ms/%.1f%%, 50ms/%.1f%%, 100ms/%.1f%%",
                    _mmu_2ms, _mmu_5ms, _mmu_10ms, _mmu_20ms, _mmu_50ms, _mmu_100ms);
}

//
// Stat phases
//

ZStatPhase::ZStatPhase(const char* group, const char* name)
  : _sampler(group, name, ZStatUnitTime) {}

void ZStatPhase::log_start(LogTargetHandle log, bool thread) const {
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

void ZStatPhase::log_end(LogTargetHandle log, const Tickspan& duration, bool thread) const {
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

const char* ZStatPhase::name() const {
  return _sampler.name();
}

ZStatPhaseCollection::ZStatPhaseCollection(const char* name, bool minor)
  : ZStatPhase(minor ? "Minor Collection" : "Major Collection", name),
    _minor(minor) {}

GCTracer* ZStatPhaseCollection::jfr_tracer() const {
  return _minor
      ? ZDriver::minor()->jfr_tracer()
      : ZDriver::major()->jfr_tracer();
}

void ZStatPhaseCollection::set_used_at_start(size_t used) const {
  if (_minor) {
    ZDriver::minor()->set_used_at_start(used);
  } else {
    ZDriver::major()->set_used_at_start(used);
  }
}

size_t ZStatPhaseCollection::used_at_start() const {
  return _minor
      ? ZDriver::minor()->used_at_start()
      : ZDriver::major()->used_at_start();
}

void ZStatPhaseCollection::register_start(ConcurrentGCTimer* timer, const Ticks& start) const {
  const GCCause::Cause cause = _minor ? ZDriver::minor()->gc_cause() : ZDriver::major()->gc_cause();

  timer->register_gc_start(start);

  jfr_tracer()->report_gc_start(cause, start);
  ZCollectedHeap::heap()->trace_heap_before_gc(jfr_tracer());

  set_used_at_start(ZHeap::heap()->used());

  log_info(gc)("%s (%s)", name(), GCCause::to_string(cause));
}

void ZStatPhaseCollection::register_end(ConcurrentGCTimer* timer, const Ticks& start, const Ticks& end) const {
  const GCCause::Cause cause = _minor ? ZDriver::minor()->gc_cause() : ZDriver::major()->gc_cause();

  if (ZAbort::should_abort()) {
    log_info(gc)("%s (%s) Aborted", name(), GCCause::to_string(cause));
    return;
  }

  timer->register_gc_end(end);

  jfr_tracer()->report_gc_end(end, timer->time_partitions());
  ZCollectedHeap::heap()->trace_heap_after_gc(jfr_tracer());

  const Tickspan duration = end - start;
  ZStatDurationSample(_sampler, duration);

  const size_t used_at_end = ZHeap::heap()->used();

  log_info(gc)("%s (%s) " ZSIZE_FMT "->" ZSIZE_FMT " %.3fs",
               name(),
               GCCause::to_string(cause),
               ZSIZE_ARGS(used_at_start()),
               ZSIZE_ARGS(used_at_end),
               duration.seconds());
}

ZStatPhaseGeneration::ZStatPhaseGeneration(const char* name, ZGenerationId id)
  : ZStatPhase(id == ZGenerationId::old ? "Old Generation" : "Young Generation", name),
    _id(id) {}

ZGenerationTracer* ZStatPhaseGeneration::jfr_tracer() const {
  return _id == ZGenerationId::young
      ? ZGeneration::young()->jfr_tracer()
      : ZGeneration::old()->jfr_tracer();
}

void ZStatPhaseGeneration::register_start(ConcurrentGCTimer* timer, const Ticks& start) const {
  ZCollectedHeap::heap()->print_before_gc();

  jfr_tracer()->report_start(start);

  log_info(gc, phases)("%s", name());
}

void ZStatPhaseGeneration::register_end(ConcurrentGCTimer* timer, const Ticks& start, const Ticks& end) const {
  if (ZAbort::should_abort()) {
    log_info(gc, phases)("%s Aborted", name());
    return;
  }

  jfr_tracer()->report_end(end);

  ZCollectedHeap::heap()->print_after_gc();

  const Tickspan duration = end - start;
  ZStatDurationSample(_sampler, duration);

  ZGeneration* const generation = ZGeneration::generation(_id);

  generation->stat_heap()->print_stalls();
  ZStatLoad::print();
  ZStatMMU::print();
  generation->stat_mark()->print();
  ZStatNMethods::print();
  ZStatMetaspace::print();
  if (generation->is_old()) {
    ZStatReferences::print();
  }

  generation->stat_relocation()->print_page_summary();
  if (generation->is_young()) {
    generation->stat_relocation()->print_age_table();
  }

  generation->stat_heap()->print(generation);

  log_info(gc, phases)("%s " ZSIZE_FMT "->" ZSIZE_FMT " %.3fs",
                       name(),
                       ZSIZE_ARGS(generation->stat_heap()->used_at_collection_start()),
                       ZSIZE_ARGS(generation->stat_heap()->used_at_collection_end()),
                       duration.seconds());
}

Tickspan ZStatPhasePause::_max;

ZStatPhasePause::ZStatPhasePause(const char* name, ZGenerationId id)
  : ZStatPhase(id == ZGenerationId::young ? "Young Pause" : "Old Pause", name) {}

const Tickspan& ZStatPhasePause::max() {
  return _max;
}

void ZStatPhasePause::register_start(ConcurrentGCTimer* timer, const Ticks& start) const {
  timer->register_gc_pause_start(name(), start);

  LogTarget(Debug, gc, phases, start) log;
  log_start(log);
}

void ZStatPhasePause::register_end(ConcurrentGCTimer* timer, const Ticks& start, const Ticks& end) const {
  timer->register_gc_pause_end(end);

  const Tickspan duration = end - start;
  ZStatDurationSample(_sampler, duration);

  // Track max pause time
  if (_max < duration) {
    _max = duration;
  }

  // Track minimum mutator utilization
  ZStatMMU::register_pause(start, end);

  LogTarget(Info, gc, phases) log;
  log_end(log, duration);
}

ZStatPhaseConcurrent::ZStatPhaseConcurrent(const char* name, ZGenerationId id)
  : ZStatPhase(id == ZGenerationId::young ? "Young Phase" : "Old Phase", name) {}

void ZStatPhaseConcurrent::register_start(ConcurrentGCTimer* timer, const Ticks& start) const {
  timer->register_gc_concurrent_start(name(), start);

  LogTarget(Debug, gc, phases, start) log;
  log_start(log);
}

void ZStatPhaseConcurrent::register_end(ConcurrentGCTimer* timer, const Ticks& start, const Ticks& end) const {
  if (ZAbort::should_abort()) {
    return;
  }

  timer->register_gc_concurrent_end(end);

  const Tickspan duration = end - start;
  ZStatDurationSample(_sampler, duration);

  LogTarget(Info, gc, phases) log;
  log_end(log, duration);
}

ZStatSubPhase::ZStatSubPhase(const char* name, ZGenerationId id)
  : ZStatPhase(id == ZGenerationId::young ? "Young Subphase" : "Old Subphase", name) {}

void ZStatSubPhase::register_start(ConcurrentGCTimer* timer, const Ticks& start) const {
  if (timer != nullptr && !ZAbort::should_abort()) {
    assert(!Thread::current()->is_Worker_thread(), "Unexpected timer value");
    timer->register_gc_phase_start(name(), start);
  }

  if (Thread::current()->is_Worker_thread()) {
    LogTarget(Trace, gc, phases, start) log;
    log_start(log, true /* thread */);
  } else {
    LogTarget(Debug, gc, phases, start) log;
    log_start(log, false /* thread */);
  }
}

void ZStatSubPhase::register_end(ConcurrentGCTimer* timer, const Ticks& start, const Ticks& end) const {
  if (ZAbort::should_abort()) {
    return;
  }

  if (timer != nullptr) {
    assert(!Thread::current()->is_Worker_thread(), "Unexpected timer value");
    timer->register_gc_phase_end(end);
  }

  ZTracer::report_thread_phase(name(), start, end);

  const Tickspan duration = end - start;
  ZStatDurationSample(_sampler, duration);

  if (Thread::current()->is_Worker_thread()) {
    LogTarget(Trace, gc, phases) log;
    log_end(log, duration, true /* thread */);
  } else {
    LogTarget(Debug, gc, phases) log;
    log_end(log, duration, false /* thread */);
  }
}

ZStatCriticalPhase::ZStatCriticalPhase(const char* name, bool verbose)
  : ZStatPhase("Critical", name),
    _counter("Critical", name, ZStatUnitOpsPerSecond),
    _verbose(verbose) {}

void ZStatCriticalPhase::register_start(ConcurrentGCTimer* timer, const Ticks& start) const {
  // This is called from sensitive contexts, for example before an allocation stall
  // has been resolved. This means we must not access any oops in here since that
  // could lead to infinite recursion. Without access to the thread name we can't
  // really log anything useful here.
}

void ZStatCriticalPhase::register_end(ConcurrentGCTimer* timer, const Ticks& start, const Ticks& end) const {
  ZTracer::report_thread_phase(name(), start, end);

  const Tickspan duration = end - start;
  ZStatDurationSample(_sampler, duration);
  ZStatInc(_counter);

  if (_verbose) {
    LogTarget(Info, gc) log;
    log_end(log, duration, true /* thread */);
  } else {
    LogTarget(Debug, gc) log;
    log_end(log, duration, true /* thread */);
  }
}

ZStatTimerYoung::ZStatTimerYoung(const ZStatPhase& phase)
  : ZStatTimer(phase, ZGeneration::young()->gc_timer()) {}

ZStatTimerOld::ZStatTimerOld(const ZStatPhase& phase)
  : ZStatTimer(phase, ZGeneration::old()->gc_timer()) {}

ZStatTimerWorker::ZStatTimerWorker(const ZStatPhase& phase)
  : ZStatTimer(phase, nullptr /* gc_timer */) {
  assert(Thread::current()->is_Worker_thread(), "Should only be called by worker thread");
}

//
// Stat sample/inc
//
void ZStatSample(const ZStatSampler& sampler, uint64_t value) {
  ZStatSamplerData* const cpu_data = sampler.get();
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

  ZTracer::report_stat_sampler(sampler, value);
}

void ZStatDurationSample(const ZStatSampler& sampler, const Tickspan& duration) {
  ZStatSample(sampler, (uint64_t)duration.value());
}

void ZStatInc(const ZStatCounter& counter, uint64_t increment) {
  ZStatCounterData* const cpu_data = counter.get();
  const uint64_t value = Atomic::add(&cpu_data->_counter, increment);

  ZTracer::report_stat_counter(counter, increment, value);
}

void ZStatInc(const ZStatUnsampledCounter& counter, uint64_t increment) {
  ZStatCounterData* const cpu_data = counter.get();
  Atomic::add(&cpu_data->_counter, increment);
}

//
// Stat mutator allocation rate
//
ZLock*          ZStatMutatorAllocRate::_stat_lock;
jlong           ZStatMutatorAllocRate::_last_sample_time;
volatile size_t ZStatMutatorAllocRate::_sampling_granule;
volatile size_t ZStatMutatorAllocRate::_allocated_since_sample;
TruncatedSeq    ZStatMutatorAllocRate::_samples_time(100);
TruncatedSeq    ZStatMutatorAllocRate::_samples_bytes(100);
TruncatedSeq    ZStatMutatorAllocRate::_rate(100);

void ZStatMutatorAllocRate::initialize() {
  _last_sample_time = os::elapsed_counter();
  _stat_lock = new ZLock();
  update_sampling_granule();
}

void ZStatMutatorAllocRate::update_sampling_granule() {
  const size_t sampling_heap_granules = 128;
  const size_t soft_max_capacity = ZHeap::heap()->soft_max_capacity();
  _sampling_granule = align_up(soft_max_capacity / sampling_heap_granules, ZGranuleSize);
}

void ZStatMutatorAllocRate::sample_allocation(size_t allocation_bytes) {
  const size_t allocated = Atomic::add(&_allocated_since_sample, allocation_bytes);

  if (allocated < Atomic::load(&_sampling_granule)) {
    // No need for sampling yet
    return;
  }

  if (!_stat_lock->try_lock()) {
    // Someone beat us to it
    return;
  }

  const size_t allocated_sample = Atomic::load(&_allocated_since_sample);

  if (allocated_sample < _sampling_granule) {
    // Someone beat us to it
    _stat_lock->unlock();
    return;
  }

  const jlong now = os::elapsed_counter();
  const jlong elapsed = now - _last_sample_time;

  if (elapsed <= 0) {
    // Avoid sampling nonsense allocation rates
    _stat_lock->unlock();
    return;
  }

  Atomic::sub(&_allocated_since_sample, allocated_sample);

  _samples_time.add(elapsed);
  _samples_bytes.add(allocated_sample);

  const double last_sample_bytes = _samples_bytes.sum();
  const double elapsed_time = _samples_time.sum();

  const double elapsed_seconds = elapsed_time / os::elapsed_frequency();
  const double bytes_per_second = double(last_sample_bytes) / elapsed_seconds;
  _rate.add(bytes_per_second);

  update_sampling_granule();

  _last_sample_time = now;

  log_debug(gc, alloc)("Mutator Allocation Rate: %.1fMB/s Predicted: %.1fMB/s, Avg: %.1f(+/-%.1f)MB/s",
                       bytes_per_second / M,
                       _rate.predict_next() / M,
                       _rate.avg() / M,
                       _rate.sd() / M);

  _stat_lock->unlock();

  ZDirector::evaluate_rules();
}

ZStatMutatorAllocRateStats ZStatMutatorAllocRate::stats() {
  ZLocker<ZLock> locker(_stat_lock);
  return {_rate.avg(), _rate.predict_next(), _rate.sd()};
}

//
// Stat thread
//
ZStat::ZStat()
  : _metronome(SampleHz) {
  set_name("ZStat");
  create_and_start();
  ZStatMutatorAllocRate::initialize();
}

void ZStat::sample_and_collect(ZStatSamplerHistory* history) const {
  // Sample counters
  for (const ZStatCounter* counter = ZStatCounter::first(); counter != nullptr; counter = counter->next()) {
    counter->sample_and_reset();
  }

  // Collect samples
  for (const ZStatSampler* sampler = ZStatSampler::first(); sampler != nullptr; sampler = sampler->next()) {
    ZStatSamplerHistory& sampler_history = history[sampler->id()];
    sampler_history.add(sampler->collect_and_reset());
  }
}

bool ZStat::should_print(LogTargetHandle log) const {
  static uint64_t print_at = ZStatisticsInterval;
  const uint64_t now = (uint64_t)os::elapsedTime();

  if (now < print_at) {
    return false;
  }

  print_at = ((now / ZStatisticsInterval) * ZStatisticsInterval) + ZStatisticsInterval;

  return log.is_enabled();
}

void ZStat::print(LogTargetHandle log, const ZStatSamplerHistory* history) const {
  // Print
  log.print("=== Garbage Collection Statistics =======================================================================================================================");
  log.print("                                                             Last 10s              Last 10m              Last 10h                Total");
  log.print("                                                             Avg / Max             Avg / Max             Avg / Max             Avg / Max");

  for (const ZStatSampler* sampler = ZStatSampler::first(); sampler != nullptr; sampler = sampler->next()) {
    const ZStatSamplerHistory& sampler_history = history[sampler->id()];
    const ZStatUnitPrinter printer = sampler->printer();
    printer(log, *sampler, sampler_history);
  }

  log.print("=========================================================================================================================================================");
}

void ZStat::run_thread() {
  ZStatSamplerHistory* const history = new ZStatSamplerHistory[ZStatSampler::count()];
  LogTarget(Debug, gc, stats) log;

  ZStatSampler::sort();

  // Main loop
  while (_metronome.wait_for_tick()) {
    sample_and_collect(history);
    if (should_print(log)) {
      print(log, history);
    }
  }

  // At exit print the final stats
  LogTarget(Info, gc, stats) exit_log;
  if (exit_log.is_enabled()) {
    print(exit_log, history);
  }

  delete [] history;
}

void ZStat::terminate() {
  _metronome.stop();
}

//
// Stat table
//
class ZStatTablePrinter {
private:
  static const size_t BufferSize = 256;

  const size_t _column0_width;
  const size_t _columnN_width;
  char         _buffer[BufferSize];

public:
  class ZColumn {
  private:
    char* const  _buffer;
    const size_t _position;
    const size_t _width;
    const size_t _width_next;

    ZColumn next() const {
      // Insert space between columns
      _buffer[_position + _width] = ' ';
      return ZColumn(_buffer, _position + _width + 1, _width_next, _width_next);
    }

    size_t print(size_t position, const char* fmt, va_list va) {
      const int res = jio_vsnprintf(_buffer + position, BufferSize - position, fmt, va);
      if (res < 0) {
        return 0;
      }

      return (size_t)res;
    }

  public:
    ZColumn(char* buffer, size_t position, size_t width, size_t width_next)
      : _buffer(buffer),
        _position(position),
        _width(width),
        _width_next(width_next) {}

    ZColumn left(const char* fmt, ...) ATTRIBUTE_PRINTF(2, 3) {
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

    ZColumn right(const char* fmt, ...) ATTRIBUTE_PRINTF(2, 3) {
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

    ZColumn center(const char* fmt, ...) ATTRIBUTE_PRINTF(2, 3) {
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

    ZColumn fill(char filler = ' ') {
      memset(_buffer + _position, filler, _width);
      return next();
    }

    const char* end() {
      _buffer[_position] = '\0';
      return _buffer;
    }
  };

public:
  ZStatTablePrinter(size_t column0_width, size_t columnN_width)
    : _column0_width(column0_width),
      _columnN_width(columnN_width) {}

  ZColumn operator()() {
    return ZColumn(_buffer, 0, _column0_width, _columnN_width);
  }
};

//
// Stat cycle
//
ZStatCycle::ZStatCycle()
  : _stat_lock(),
    _nwarmup_cycles(0),
    _start_of_last(),
    _end_of_last(),
    _cycle_intervals(0.7 /* alpha */),
    _serial_time(0.7 /* alpha */),
    _parallelizable_time(0.7 /* alpha */),
    _parallelizable_duration(0.7 /* alpha */),
    _last_active_workers(0.0) {}

void ZStatCycle::at_start() {
  ZLocker<ZLock> locker(&_stat_lock);
  _start_of_last = Ticks::now();
}

void ZStatCycle::at_end(ZStatWorkers* stat_workers, bool record_stats) {
  ZLocker<ZLock> locker(&_stat_lock);
  const Ticks end_of_last = _end_of_last;
  _end_of_last = Ticks::now();

  if (ZDriver::major()->gc_cause() == GCCause::_z_warmup && _nwarmup_cycles < 3) {
    _nwarmup_cycles++;
  }

  // Calculate serial and parallelizable GC cycle times
  const double duration = (_end_of_last - _start_of_last).seconds();
  const double workers_duration = stat_workers->get_and_reset_duration();
  const double workers_time = stat_workers->get_and_reset_time();
  const double serial_time = duration - workers_duration;

  _last_active_workers = workers_time / workers_duration;

  if (record_stats) {
    _serial_time.add(serial_time);
    _parallelizable_time.add(workers_time);
    _parallelizable_duration.add(workers_duration);
    if (end_of_last.value() != 0) {
      const double cycle_interval = (_end_of_last - end_of_last).seconds();
      _cycle_intervals.add(cycle_interval);
    }
  }
}

bool ZStatCycle::is_warm() {
  return _nwarmup_cycles >= 3;
}

bool ZStatCycle::is_time_trustable() {
  // The times are considered trustable if we
  // have completed at least one warmup cycle.
  return _nwarmup_cycles > 0;
}

double ZStatCycle::last_active_workers() {
  return _last_active_workers;
}

double ZStatCycle::duration_since_start() {
  const Ticks start = _start_of_last;
  if (start.value() == 0) {
    // No end recorded yet, return time since VM start
    return 0.0;
  }

  const Ticks now = Ticks::now();
  const Tickspan duration_since_start = now - start;
  return duration_since_start.seconds();
}

double ZStatCycle::time_since_last() {
  if (_end_of_last.value() == 0) {
    // No end recorded yet, return time since VM start
    return os::elapsedTime();
  }

  const Ticks now = Ticks::now();
  const Tickspan time_since_last = now - _end_of_last;
  return time_since_last.seconds();
}

ZStatCycleStats ZStatCycle::stats() {
  ZLocker<ZLock> locker(&_stat_lock);

  return {
    is_warm(),
    _nwarmup_cycles,
    is_time_trustable(),
    time_since_last(),
    last_active_workers(),
    duration_since_start(),
    _cycle_intervals.davg(),
    _serial_time.davg(),
    _serial_time.dsd(),
    _parallelizable_time.davg(),
    _parallelizable_time.dsd(),
    _parallelizable_duration.davg(),
    _parallelizable_duration.dsd()
  };
}

//
// Stat workers
//
ZStatWorkers::ZStatWorkers()
  : _stat_lock(),
    _active_workers(0),
    _start_of_last(),
    _accumulated_duration(),
    _accumulated_time() {}

void ZStatWorkers::at_start(uint active_workers) {
  ZLocker<ZLock> locker(&_stat_lock);
  _start_of_last = Ticks::now();
  _active_workers = active_workers;
}

void ZStatWorkers::at_end() {
  ZLocker<ZLock> locker(&_stat_lock);
  const Ticks now = Ticks::now();
  const Tickspan duration = now - _start_of_last;
  Tickspan time = duration;
  for (uint i = 1; i < _active_workers; ++i) {
    time += duration;
  }
  _accumulated_time += time;
  _accumulated_duration += duration;
  _active_workers = 0;
}

double ZStatWorkers::accumulated_time() {
  const uint nworkers = _active_workers;
  const Ticks now = Ticks::now();
  const Ticks start = _start_of_last;
  Tickspan time = _accumulated_time;
  if (nworkers != 0) {
    for (uint i = 0; i < nworkers; ++i) {
      time += now - start;
    }
  }
  return time.seconds();
}

double ZStatWorkers::accumulated_duration() {
  const Ticks now = Ticks::now();
  const Ticks start = _start_of_last;
  Tickspan duration = _accumulated_duration;
  if (_active_workers != 0) {
    duration += now - start;
  }
  return duration.seconds();
}

uint ZStatWorkers::active_workers() {
  return _active_workers;
}

double ZStatWorkers::get_and_reset_duration() {
  ZLocker<ZLock> locker(&_stat_lock);
  const double duration = _accumulated_duration.seconds();
  const Ticks now = Ticks::now();
  _accumulated_duration = now - now;
  return duration;
}

double ZStatWorkers::get_and_reset_time() {
  ZLocker<ZLock> locker(&_stat_lock);
  const double time = _accumulated_time.seconds();
  const Ticks now = Ticks::now();
  _accumulated_time = now - now;
  return time;
}

ZStatWorkersStats ZStatWorkers::stats() {
  ZLocker<ZLock> locker(&_stat_lock);
  return {
    accumulated_time(),
    accumulated_duration()
  };
}

//
// Stat load
//
void ZStatLoad::print() {
  double loadavg[3] = {};
  os::loadavg(loadavg, ARRAY_SIZE(loadavg));
  log_info(gc, load)("Load: %.2f (%.0f%%) / %.2f (%.0f%%) / %.2f (%.0f%%)",
                     loadavg[0], percent_of(loadavg[0], (double) ZCPU::count()),
                     loadavg[1], percent_of(loadavg[1], (double) ZCPU::count()),
                     loadavg[2], percent_of(loadavg[2], (double) ZCPU::count()));
}

//
// Stat mark
//
ZStatMark::ZStatMark()
  : _nstripes(),
    _nproactiveflush(),
    _nterminateflush(),
    _ntrycomplete(),
    _ncontinue() {}

void ZStatMark::at_mark_start(size_t nstripes) {
  _nstripes = nstripes;
}

void ZStatMark::at_mark_end(size_t nproactiveflush,
                            size_t nterminateflush,
                            size_t ntrycomplete,
                            size_t ncontinue) {
  _nproactiveflush = nproactiveflush;
  _nterminateflush = nterminateflush;
  _ntrycomplete = ntrycomplete;
  _ncontinue = ncontinue;
}

void ZStatMark::print() {
  log_info(gc, marking)("Mark: "
                        "%zu stripe(s), "
                        "%zu proactive flush(es), "
                        "%zu terminate flush(es), "
                        "%zu completion(s), "
                        "%zu continuation(s) ",
                        _nstripes,
                        _nproactiveflush,
                        _nterminateflush,
                        _ntrycomplete,
                        _ncontinue);
}

//
// Stat relocation
//
ZStatRelocation::ZStatRelocation()
  : _selector_stats(),
    _forwarding_usage(),
    _small_selected(),
    _small_in_place_count(),
    _medium_selected(),
    _medium_in_place_count() {}

void ZStatRelocation::at_select_relocation_set(const ZRelocationSetSelectorStats& selector_stats) {
  _selector_stats = selector_stats;
}

void ZStatRelocation::at_install_relocation_set(size_t forwarding_usage) {
  _forwarding_usage = forwarding_usage;
}

void ZStatRelocation::at_relocate_end(size_t small_in_place_count, size_t medium_in_place_count) {
  _small_in_place_count = small_in_place_count;
  _medium_in_place_count = medium_in_place_count;
}

void ZStatRelocation::print_page_summary() {
  LogTarget(Info, gc, reloc) lt;

  if (!_selector_stats.has_relocatable_pages() || !lt.is_enabled()) {
    // Nothing to log or logging not enabled.
    return;
  }

  // Zero initialize
  ZStatRelocationSummary small_summary{};
  ZStatRelocationSummary medium_summary{};
  ZStatRelocationSummary large_summary{};

  auto account_page_size = [&](ZStatRelocationSummary& summary, const ZRelocationSetSelectorGroupStats& stats) {
    summary.npages_candidates += stats.npages_candidates();
    summary.total += stats.total();
    summary.empty += stats.empty();
    summary.npages_selected += stats.npages_selected();
    summary.relocate += stats.relocate();
  };

  for (ZPageAge age : ZPageAgeRange()) {
    account_page_size(small_summary, _selector_stats.small(age));
    account_page_size(medium_summary, _selector_stats.medium(age));
    account_page_size(large_summary, _selector_stats.large(age));
  }

  ZStatTablePrinter pages(20, 12);
  lt.print("%s", pages()
           .fill()
           .right("Candidates")
           .right("Selected")
           .right("In-Place")
           .right("Size")
           .right("Empty")
           .right("Relocated")
           .end());

  auto print_summary = [&](const char* name, ZStatRelocationSummary& summary, size_t in_place_count) {
    lt.print("%s", pages()
             .left("%s Pages:", name)
             .right("%zu", summary.npages_candidates)
             .right("%zu", summary.npages_selected)
             .right("%zu", in_place_count)
             .right("%zuM", summary.total / M)
             .right("%zuM", summary.empty / M)
             .right("%zuM", summary.relocate /M)
             .end());
  };

  print_summary("Small", small_summary, _small_in_place_count);
  if (ZPageSizeMediumEnabled) {
    print_summary("Medium", medium_summary, _medium_in_place_count);
  }
  print_summary("Large", large_summary, 0 /* in_place_count */);

  lt.print("Forwarding Usage: %zuM", _forwarding_usage / M);
}

void ZStatRelocation::print_age_table() {
  LogTarget(Info, gc, reloc) lt;
  if (!_selector_stats.has_relocatable_pages() || !lt.is_enabled()) {
    // Nothing to log or logging not enabled.
    return;
  }

  ZStatTablePrinter age_table(11, 18);
  lt.print("Age Table:");
  lt.print("%s", age_table()
           .fill()
           .center("Live")
           .center("Garbage")
           .center("Small")
           .center("Medium")
           .center("Large")
           .end());

  size_t live[ZPageAgeCount] = {};
  size_t total[ZPageAgeCount] = {};

  uint oldest_none_empty_age = 0;

  for (ZPageAge age : ZPageAgeRange()) {
    uint i = untype(age);
    auto summarize_pages = [&](const ZRelocationSetSelectorGroupStats& stats) {
      live[i] += stats.live();
      total[i] += stats.total();
    };

    summarize_pages(_selector_stats.small(age));
    summarize_pages(_selector_stats.medium(age));
    summarize_pages(_selector_stats.large(age));

    if (total[i] != 0) {
      oldest_none_empty_age = i;
    }
  }

  for (uint i = 0; i <= oldest_none_empty_age; ++i) {
    ZPageAge age = to_zpageage(i);

    FormatBuffer<> age_str("");
    if (age == ZPageAge::eden) {
      age_str.append("Eden");
    } else if (age != ZPageAge::old) {
      age_str.append("Survivor %d", i);
    }

    auto create_age_table = [&]() {
      if (live[i] == 0) {
        return age_table()
              .left("%s", age_str.buffer())
              .left(ZTABLE_ARGS_NA);
      } else {
        return age_table()
              .left("%s", age_str.buffer())
              .left(ZTABLE_ARGS(live[i]));
      }
    };

    lt.print("%s", create_age_table()
              .left(ZTABLE_ARGS(total[i] - live[i]))
              .left("%7zu / %zu",
                    _selector_stats.small(age).npages_candidates(),
                    _selector_stats.small(age).npages_selected())
              .left("%7zu / %zu",
                    _selector_stats.medium(age).npages_candidates(),
                    _selector_stats.medium(age).npages_selected())
              .left("%7zu / %zu",
                    _selector_stats.large(age).npages_candidates(),
                    _selector_stats.large(age).npages_selected())
              .end());
  }
}

//
// Stat nmethods
//
void ZStatNMethods::print() {
  log_info(gc, nmethod)("NMethods: %zu registered, %zu unregistered",
                        ZNMethodTable::registered_nmethods(),
                        ZNMethodTable::unregistered_nmethods());
}

//
// Stat metaspace
//
void ZStatMetaspace::print() {
  const MetaspaceCombinedStats stats = MetaspaceUtils::get_combined_statistics();
  log_info(gc, metaspace)("Metaspace: "
                          "%zuM used, "
                          "%zuM committed, %zuM reserved",
                          stats.used() / M,
                          stats.committed() / M,
                          stats.reserved() / M);
}

//
// Stat references
//
ZStatReferences::ZCount ZStatReferences::_soft;
ZStatReferences::ZCount ZStatReferences::_weak;
ZStatReferences::ZCount ZStatReferences::_final;
ZStatReferences::ZCount ZStatReferences::_phantom;

void ZStatReferences::set(ZCount* count, size_t encountered, size_t discovered, size_t enqueued) {
  count->encountered = encountered;
  count->discovered = discovered;
  count->enqueued = enqueued;
}

void ZStatReferences::set_soft(size_t encountered, size_t discovered, size_t enqueued) {
  set(&_soft, encountered, discovered, enqueued);
}

void ZStatReferences::set_weak(size_t encountered, size_t discovered, size_t enqueued) {
  set(&_weak, encountered, discovered, enqueued);
}

void ZStatReferences::set_final(size_t encountered, size_t discovered, size_t enqueued) {
  set(&_final, encountered, discovered, enqueued);
}

void ZStatReferences::set_phantom(size_t encountered, size_t discovered, size_t enqueued) {
  set(&_phantom, encountered, discovered, enqueued);
}

void ZStatReferences::print() {
  LogTarget(Info, gc, ref) lt;
  if (!lt.is_enabled()) {
    // Nothing to log
    return;
  }

  ZStatTablePrinter refs(20, 12);
  lt.print("%s", refs()
           .fill()
           .right("Encountered")
           .right("Discovered")
           .right("Enqueued")
           .end());

  auto ref_print = [&] (const char* name, const ZStatReferences::ZCount& ref) {
    lt.print("%s", refs()
             .left("%s References:", name)
             .right("%zu", ref.encountered)
             .right("%zu", ref.discovered)
             .right("%zu", ref.enqueued)
             .end());
  };

  ref_print("Soft", _soft);
  ref_print("Weak", _weak);
  ref_print("Final", _final);
  ref_print("Phantom", _phantom);
}

//
// Stat heap
//

ZStatHeap::ZStatHeap()
  : _stat_lock(),
    _at_collection_start(),
    _at_mark_start(),
    _at_mark_end(),
    _at_relocate_start(),
    _at_relocate_end(),
    _reclaimed_bytes(0.7 /* alpha */) {}

ZStatHeap::ZAtInitialize ZStatHeap::_at_initialize;

size_t ZStatHeap::capacity_high() const {
  return MAX4(_at_mark_start.capacity,
              _at_mark_end.capacity,
              _at_relocate_start.capacity,
              _at_relocate_end.capacity);
}

size_t ZStatHeap::capacity_low() const {
  return MIN4(_at_mark_start.capacity,
              _at_mark_end.capacity,
              _at_relocate_start.capacity,
              _at_relocate_end.capacity);
}

size_t ZStatHeap::free(size_t used) const {
  return _at_initialize.max_capacity - used;
}

size_t ZStatHeap::mutator_allocated(size_t used_generation, size_t freed, size_t relocated) const {
  // The amount of allocated memory between point A and B is used(B) - used(A).
  // However, we might also have reclaimed memory between point A and B. This
  // means the current amount of used memory must be incremented by the amount
  // reclaimed, so that used(B) represents the amount of used memory we would
  // have had if we had not reclaimed anything.
  const size_t used_generation_delta = used_generation - _at_mark_start.used_generation;
  return  used_generation_delta + freed - relocated;
}

size_t ZStatHeap::garbage(size_t freed, size_t relocated, size_t promoted) const {
  return _at_mark_end.garbage - (freed - promoted - relocated);
}

size_t ZStatHeap::reclaimed(size_t freed, size_t relocated, size_t promoted) const {
  return freed - relocated - promoted;
}

void ZStatHeap::at_initialize(size_t min_capacity, size_t max_capacity) {
  ZLocker<ZLock> locker(&_stat_lock);

  _at_initialize.min_capacity = min_capacity;
  _at_initialize.max_capacity = max_capacity;
}

void ZStatHeap::at_collection_start(const ZPageAllocatorStats& stats) {
  ZLocker<ZLock> locker(&_stat_lock);

  _at_collection_start.soft_max_capacity = stats.soft_max_capacity();
  _at_collection_start.capacity = stats.capacity();
  _at_collection_start.free = free(stats.used());
  _at_collection_start.used = stats.used();
  _at_collection_start.used_generation = stats.used_generation();
}

void ZStatHeap::at_mark_start(const ZPageAllocatorStats& stats) {
  ZLocker<ZLock> locker(&_stat_lock);

  _at_mark_start.soft_max_capacity = stats.soft_max_capacity();
  _at_mark_start.capacity = stats.capacity();
  _at_mark_start.free = free(stats.used());
  _at_mark_start.used = stats.used();
  _at_mark_start.used_generation = stats.used_generation();
  _at_mark_start.allocation_stalls = stats.allocation_stalls();
}

void ZStatHeap::at_mark_end(const ZPageAllocatorStats& stats) {
  ZLocker<ZLock> locker(&_stat_lock);

  _at_mark_end.capacity = stats.capacity();
  _at_mark_end.free = free(stats.used());
  _at_mark_end.used = stats.used();
  _at_mark_end.used_generation = stats.used_generation();
  _at_mark_end.mutator_allocated = mutator_allocated(stats.used_generation(), 0 /* reclaimed */, 0 /* relocated */);
  _at_mark_end.allocation_stalls = stats.allocation_stalls();
}

void ZStatHeap::at_select_relocation_set(const ZRelocationSetSelectorStats& stats) {
  ZLocker<ZLock> locker(&_stat_lock);

  size_t live = 0;
  for (ZPageAge age : ZPageAgeRange()) {
    live += stats.small(age).live() + stats.medium(age).live() + stats.large(age).live();
  }
  _at_mark_end.live = live;
  _at_mark_end.garbage = _at_mark_start.used_generation - live;
}

void ZStatHeap::at_relocate_start(const ZPageAllocatorStats& stats) {
  ZLocker<ZLock> locker(&_stat_lock);

  assert(stats.compacted() == 0, "Nothing should have been compacted");

  _at_relocate_start.capacity = stats.capacity();
  _at_relocate_start.free = free(stats.used());
  _at_relocate_start.used = stats.used();
  _at_relocate_start.used_generation = stats.used_generation();
  _at_relocate_start.live = _at_mark_end.live - stats.promoted();
  _at_relocate_start.garbage = garbage(stats.freed(), stats.compacted(), stats.promoted());
  _at_relocate_start.mutator_allocated = mutator_allocated(stats.used_generation(), stats.freed(), stats.compacted());
  _at_relocate_start.reclaimed = reclaimed(stats.freed(), stats.compacted(), stats.promoted());
  _at_relocate_start.promoted = stats.promoted();
  _at_relocate_start.compacted = stats.compacted();
  _at_relocate_start.allocation_stalls = stats.allocation_stalls();
}

void ZStatHeap::at_relocate_end(const ZPageAllocatorStats& stats, bool record_stats) {
  ZLocker<ZLock> locker(&_stat_lock);

  _at_relocate_end.capacity = stats.capacity();
  _at_relocate_end.capacity_high = capacity_high();
  _at_relocate_end.capacity_low = capacity_low();
  _at_relocate_end.free = free(stats.used());
  _at_relocate_end.free_high = free(stats.used_low());
  _at_relocate_end.free_low = free(stats.used_high());
  _at_relocate_end.used = stats.used();
  _at_relocate_end.used_high = stats.used_high();
  _at_relocate_end.used_low = stats.used_low();
  _at_relocate_end.used_generation = stats.used_generation();
  _at_relocate_end.live = _at_mark_end.live - stats.promoted();
  _at_relocate_end.garbage = garbage(stats.freed(), stats.compacted(), stats.promoted());
  _at_relocate_end.mutator_allocated = mutator_allocated(stats.used_generation(), stats.freed(), stats.compacted());
  _at_relocate_end.reclaimed = reclaimed(stats.freed(), stats.compacted(), stats.promoted());
  _at_relocate_end.promoted = stats.promoted();
  _at_relocate_end.compacted = stats.compacted();
  _at_relocate_end.allocation_stalls = stats.allocation_stalls();

  if (record_stats) {
    _reclaimed_bytes.add(_at_relocate_end.reclaimed);
  }
}

double ZStatHeap::reclaimed_avg() {
  // Make sure the reclaimed average is greater than 0.0 to avoid division by zero.
  return _reclaimed_bytes.davg() + std::numeric_limits<double>::denorm_min();
}

size_t ZStatHeap::max_capacity() {
  return _at_initialize.max_capacity;
}

size_t ZStatHeap::used_at_collection_start() const {
  return _at_collection_start.used;
}

size_t ZStatHeap::used_at_mark_start() const {
  return _at_mark_start.used;
}

size_t ZStatHeap::used_generation_at_mark_start() const {
  return _at_mark_start.used_generation;
}

size_t ZStatHeap::live_at_mark_end() const {
  return _at_mark_end.live;
}

size_t ZStatHeap::allocated_at_mark_end() const {
  return _at_mark_end.mutator_allocated;
}

size_t ZStatHeap::garbage_at_mark_end() const {
  return _at_mark_end.garbage;
}

size_t ZStatHeap::used_at_relocate_end() const {
  return _at_relocate_end.used;
}

size_t ZStatHeap::used_at_collection_end() const {
  return used_at_relocate_end();
}

size_t ZStatHeap::stalls_at_mark_start() const {
  return _at_mark_start.allocation_stalls;
}

size_t ZStatHeap::stalls_at_mark_end() const {
  return _at_mark_end.allocation_stalls;
}

size_t ZStatHeap::stalls_at_relocate_start() const {
  return _at_relocate_start.allocation_stalls;
}

size_t ZStatHeap::stalls_at_relocate_end() const {
  return _at_relocate_end.allocation_stalls;
}

ZStatHeapStats ZStatHeap::stats() {
  ZLocker<ZLock> locker(&_stat_lock);

  return {
    live_at_mark_end(),
    used_at_relocate_end(),
    reclaimed_avg()
  };
}

void ZStatHeap::print(const ZGeneration* generation) const {
  log_info(gc, heap)("Min Capacity: "
                     ZSIZE_FMT, ZSIZE_ARGS(_at_initialize.min_capacity));
  log_info(gc, heap)("Max Capacity: "
                     ZSIZE_FMT, ZSIZE_ARGS(_at_initialize.max_capacity));
  log_info(gc, heap)("Soft Max Capacity: "
                     ZSIZE_FMT, ZSIZE_ARGS(_at_mark_start.soft_max_capacity));

  log_info(gc, heap)("Heap Statistics:");
  ZStatTablePrinter heap_table(10, 18);
  log_info(gc, heap)("%s", heap_table()
                     .fill()
                     .center("Mark Start")
                     .center("Mark End")
                     .center("Relocate Start")
                     .center("Relocate End")
                     .center("High")
                     .center("Low")
                     .end());
  log_info(gc, heap)("%s", heap_table()
                     .right("Capacity:")
                     .left(ZTABLE_ARGS(_at_mark_start.capacity))
                     .left(ZTABLE_ARGS(_at_mark_end.capacity))
                     .left(ZTABLE_ARGS(_at_relocate_start.capacity))
                     .left(ZTABLE_ARGS(_at_relocate_end.capacity))
                     .left(ZTABLE_ARGS(_at_relocate_end.capacity_high))
                     .left(ZTABLE_ARGS(_at_relocate_end.capacity_low))
                     .end());
  log_info(gc, heap)("%s", heap_table()
                     .right("Free:")
                     .left(ZTABLE_ARGS(_at_mark_start.free))
                     .left(ZTABLE_ARGS(_at_mark_end.free))
                     .left(ZTABLE_ARGS(_at_relocate_start.free))
                     .left(ZTABLE_ARGS(_at_relocate_end.free))
                     .left(ZTABLE_ARGS(_at_relocate_end.free_high))
                     .left(ZTABLE_ARGS(_at_relocate_end.free_low))
                     .end());
  log_info(gc, heap)("%s", heap_table()
                     .right("Used:")
                     .left(ZTABLE_ARGS(_at_mark_start.used))
                     .left(ZTABLE_ARGS(_at_mark_end.used))
                     .left(ZTABLE_ARGS(_at_relocate_start.used))
                     .left(ZTABLE_ARGS(_at_relocate_end.used))
                     .left(ZTABLE_ARGS(_at_relocate_end.used_high))
                     .left(ZTABLE_ARGS(_at_relocate_end.used_low))
                     .end());

  log_info(gc, heap)("%s Generation Statistics:", generation->is_young() ? "Young" : "Old");
  ZStatTablePrinter gen_table(10, 18);
  log_info(gc, heap)("%s", gen_table()
                     .fill()
                     .center("Mark Start")
                     .center("Mark End")
                     .center("Relocate Start")
                     .center("Relocate End")
                     .end());
  log_info(gc, heap)("%s", gen_table()
                     .right("Used:")
                     .left(ZTABLE_ARGS(_at_mark_start.used_generation))
                     .left(ZTABLE_ARGS(_at_mark_end.used_generation))
                     .left(ZTABLE_ARGS(_at_relocate_start.used_generation))
                     .left(ZTABLE_ARGS(_at_relocate_end.used_generation))
                     .end());
  log_info(gc, heap)("%s", gen_table()
                     .right("Live:")
                     .left(ZTABLE_ARGS_NA)
                     .left(ZTABLE_ARGS(_at_mark_end.live))
                     .left(ZTABLE_ARGS(_at_relocate_start.live))
                     .left(ZTABLE_ARGS(_at_relocate_end.live))
                     .end());
  log_info(gc, heap)("%s", gen_table()
                     .right("Garbage:")
                     .left(ZTABLE_ARGS_NA)
                     .left(ZTABLE_ARGS(_at_mark_end.garbage))
                     .left(ZTABLE_ARGS(_at_relocate_start.garbage))
                     .left(ZTABLE_ARGS(_at_relocate_end.garbage))
                     .end());
  log_info(gc, heap)("%s", gen_table()
                     .right("Allocated:")
                     .left(ZTABLE_ARGS_NA)
                     .left(ZTABLE_ARGS(_at_mark_end.mutator_allocated))
                     .left(ZTABLE_ARGS(_at_relocate_start.mutator_allocated))
                     .left(ZTABLE_ARGS(_at_relocate_end.mutator_allocated))
                     .end());
  log_info(gc, heap)("%s", gen_table()
                     .right("Reclaimed:")
                     .left(ZTABLE_ARGS_NA)
                     .left(ZTABLE_ARGS_NA)
                     .left(ZTABLE_ARGS(_at_relocate_start.reclaimed))
                     .left(ZTABLE_ARGS(_at_relocate_end.reclaimed))
                     .end());
  if (generation->is_young()) {
    log_info(gc, heap)("%s", gen_table()
                       .right("Promoted:")
                       .left(ZTABLE_ARGS_NA)
                       .left(ZTABLE_ARGS_NA)
                       .left(ZTABLE_ARGS(_at_relocate_start.promoted))
                       .left(ZTABLE_ARGS(_at_relocate_end.promoted))
                       .end());
  }
  log_info(gc, heap)("%s", gen_table()
                     .right("Compacted:")
                     .left(ZTABLE_ARGS_NA)
                     .left(ZTABLE_ARGS_NA)
                     .left(ZTABLE_ARGS_NA)
                     .left(ZTABLE_ARGS(_at_relocate_end.compacted))
                     .end());
}

void ZStatHeap::print_stalls() const {
  ZStatTablePrinter stall_table(20, 16);
  log_info(gc, alloc)("%s", stall_table()
                     .fill()
                     .center("Mark Start")
                     .center("Mark End")
                     .center("Relocate Start")
                     .center("Relocate End")
                     .end());
  log_info(gc, alloc)("%s", stall_table()
                     .left("%s", "Allocation Stalls:")
                     .center("%zu", _at_mark_start.allocation_stalls)
                     .center("%zu", _at_mark_end.allocation_stalls)
                     .center("%zu", _at_relocate_start.allocation_stalls)
                     .center("%zu", _at_relocate_end.allocation_stalls)
                     .end());
}
