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

#ifndef SHARE_GC_X_XSTAT_HPP
#define SHARE_GC_X_XSTAT_HPP

#include "gc/shared/concurrentGCThread.hpp"
#include "gc/shared/gcCause.hpp"
#include "gc/shared/gcTimer.hpp"
#include "gc/x/xMetronome.hpp"
#include "logging/logHandle.hpp"
#include "memory/allocation.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/numberSeq.hpp"
#include "utilities/ticks.hpp"

class XPage;
class XPageAllocatorStats;
class XRelocationSetSelectorGroupStats;
class XRelocationSetSelectorStats;
class XStatSampler;
class XStatSamplerHistory;
struct XStatCounterData;
struct XStatSamplerData;

//
// Stat unit printers
//
typedef void (*XStatUnitPrinter)(LogTargetHandle log, const XStatSampler&, const XStatSamplerHistory&);

void XStatUnitTime(LogTargetHandle log, const XStatSampler& sampler, const XStatSamplerHistory& history);
void XStatUnitBytes(LogTargetHandle log, const XStatSampler& sampler, const XStatSamplerHistory& history);
void XStatUnitThreads(LogTargetHandle log, const XStatSampler& sampler, const XStatSamplerHistory& history);
void XStatUnitBytesPerSecond(LogTargetHandle log, const XStatSampler& sampler, const XStatSamplerHistory& history);
void XStatUnitOpsPerSecond(LogTargetHandle log, const XStatSampler& sampler, const XStatSamplerHistory& history);

//
// Stat value
//
class XStatValue {
private:
  static uintptr_t _base;
  static uint32_t  _cpu_offset;

  const char* const _group;
  const char* const _name;
  const uint32_t    _id;
  const uint32_t    _offset;

protected:
  XStatValue(const char* group,
             const char* name,
             uint32_t id,
             uint32_t size);

  template <typename T> T* get_cpu_local(uint32_t cpu) const;

public:
  static void initialize();

  const char* group() const;
  const char* name() const;
  uint32_t id() const;
};

//
// Stat iterable value
//
template <typename T>
class XStatIterableValue : public XStatValue {
private:
  static uint32_t _count;
  static T*       _first;

  T* _next;

  T* insert() const;

protected:
  XStatIterableValue(const char* group,
                     const char* name,
                     uint32_t size);

public:
  static void sort();

  static uint32_t count() {
    return _count;
  }

  static T* first() {
    return _first;
  }

  T* next() const {
    return _next;
  }
};

template <typename T> uint32_t XStatIterableValue<T>::_count = 0;
template <typename T> T*       XStatIterableValue<T>::_first = NULL;

//
// Stat sampler
//
class XStatSampler : public XStatIterableValue<XStatSampler> {
private:
  const XStatUnitPrinter _printer;

public:
  XStatSampler(const char* group,
               const char* name,
               XStatUnitPrinter printer);

  XStatSamplerData* get() const;
  XStatSamplerData collect_and_reset() const;

  XStatUnitPrinter printer() const;
};

//
// Stat counter
//
class XStatCounter : public XStatIterableValue<XStatCounter> {
private:
  const XStatSampler _sampler;

public:
  XStatCounter(const char* group,
               const char* name,
               XStatUnitPrinter printer);

  XStatCounterData* get() const;
  void sample_and_reset() const;
};

//
// Stat unsampled counter
//
class XStatUnsampledCounter : public XStatIterableValue<XStatUnsampledCounter> {
public:
  XStatUnsampledCounter(const char* name);

  XStatCounterData* get() const;
  XStatCounterData collect_and_reset() const;
};

//
// Stat MMU (Minimum Mutator Utilization)
//
class XStatMMUPause {
private:
  double _start;
  double _end;

public:
  XStatMMUPause();
  XStatMMUPause(const Ticks& start, const Ticks& end);

  double end() const;
  double overlap(double start, double end) const;
};

class XStatMMU {
private:
  static size_t        _next;
  static size_t        _npauses;
  static XStatMMUPause _pauses[200]; // Record the last 200 pauses

  static double _mmu_2ms;
  static double _mmu_5ms;
  static double _mmu_10ms;
  static double _mmu_20ms;
  static double _mmu_50ms;
  static double _mmu_100ms;

  static const XStatMMUPause& pause(size_t index);
  static double calculate_mmu(double time_slice);

public:
  static void register_pause(const Ticks& start, const Ticks& end);

  static void print();
};

//
// Stat phases
//
class XStatPhase {
private:
  static ConcurrentGCTimer _timer;

protected:
  const XStatSampler _sampler;

  XStatPhase(const char* group, const char* name);

  void log_start(LogTargetHandle log, bool thread = false) const;
  void log_end(LogTargetHandle log, const Tickspan& duration, bool thread = false) const;

public:
  static ConcurrentGCTimer* timer();

  const char* name() const;

  virtual void register_start(const Ticks& start) const = 0;
  virtual void register_end(const Ticks& start, const Ticks& end) const = 0;
};

class XStatPhaseCycle : public XStatPhase {
public:
  XStatPhaseCycle(const char* name);

  virtual void register_start(const Ticks& start) const;
  virtual void register_end(const Ticks& start, const Ticks& end) const;
};

class XStatPhasePause : public XStatPhase {
private:
  static Tickspan _max; // Max pause time

public:
  XStatPhasePause(const char* name);

  static const Tickspan& max();

  virtual void register_start(const Ticks& start) const;
  virtual void register_end(const Ticks& start, const Ticks& end) const;
};

class XStatPhaseConcurrent : public XStatPhase {
public:
  XStatPhaseConcurrent(const char* name);

  virtual void register_start(const Ticks& start) const;
  virtual void register_end(const Ticks& start, const Ticks& end) const;
};

class XStatSubPhase : public XStatPhase {
public:
  XStatSubPhase(const char* name);

  virtual void register_start(const Ticks& start) const;
  virtual void register_end(const Ticks& start, const Ticks& end) const;
};

class XStatCriticalPhase : public XStatPhase {
private:
  const XStatCounter _counter;
  const bool         _verbose;

public:
  XStatCriticalPhase(const char* name, bool verbose = true);

  virtual void register_start(const Ticks& start) const;
  virtual void register_end(const Ticks& start, const Ticks& end) const;
};

//
// Stat timer
//
class XStatTimerDisable : public StackObj {
private:
  static THREAD_LOCAL uint32_t _active;

public:
  XStatTimerDisable() {
    _active++;
  }

  ~XStatTimerDisable() {
    _active--;
  }

  static bool is_active() {
    return _active > 0;
  }
};

class XStatTimer : public StackObj {
private:
  const bool        _enabled;
  const XStatPhase& _phase;
  const Ticks       _start;

public:
  XStatTimer(const XStatPhase& phase) :
      _enabled(!XStatTimerDisable::is_active()),
      _phase(phase),
      _start(Ticks::now()) {
    if (_enabled) {
      _phase.register_start(_start);
    }
  }

  ~XStatTimer() {
    if (_enabled) {
      const Ticks end = Ticks::now();
      _phase.register_end(_start, end);
    }
  }
};

//
// Stat sample/increment
//
void XStatSample(const XStatSampler& sampler, uint64_t value);
void XStatInc(const XStatCounter& counter, uint64_t increment = 1);
void XStatInc(const XStatUnsampledCounter& counter, uint64_t increment = 1);

//
// Stat allocation rate
//
class XStatAllocRate : public AllStatic {
private:
  static const XStatUnsampledCounter _counter;
  static TruncatedSeq                _samples;
  static TruncatedSeq                _rate;

public:
  static const uint64_t sample_hz = 10;

  static const XStatUnsampledCounter& counter();
  static uint64_t sample_and_reset();

  static double predict();
  static double avg();
  static double sd();
};

//
// Stat thread
//
class XStat : public ConcurrentGCThread {
private:
  static const uint64_t sample_hz = 1;

  XMetronome _metronome;

  void sample_and_collect(XStatSamplerHistory* history) const;
  bool should_print(LogTargetHandle log) const;
  void print(LogTargetHandle log, const XStatSamplerHistory* history) const;

protected:
  virtual void run_service();
  virtual void stop_service();

public:
  XStat();
};

//
// Stat cycle
//
class XStatCycle : public AllStatic {
private:
  static uint64_t  _nwarmup_cycles;
  static Ticks     _start_of_last;
  static Ticks     _end_of_last;
  static NumberSeq _serial_time;
  static NumberSeq _parallelizable_time;
  static uint      _last_active_workers;

public:
  static void at_start();
  static void at_end(GCCause::Cause cause, uint active_workers);

  static bool is_warm();
  static uint64_t nwarmup_cycles();

  static bool is_time_trustable();
  static const AbsSeq& serial_time();
  static const AbsSeq& parallelizable_time();

  static uint last_active_workers();

  static double time_since_last();
};

//
// Stat workers
//
class XStatWorkers : public AllStatic {
private:
  static Ticks    _start_of_last;
  static Tickspan _accumulated_duration;

public:
  static void at_start();
  static void at_end();

  static double get_and_reset_duration();
};

//
// Stat load
//
class XStatLoad : public AllStatic {
public:
  static void print();
};

//
// Stat mark
//
class XStatMark : public AllStatic {
private:
  static size_t _nstripes;
  static size_t _nproactiveflush;
  static size_t _nterminateflush;
  static size_t _ntrycomplete;
  static size_t _ncontinue;
  static size_t _mark_stack_usage;

public:
  static void set_at_mark_start(size_t nstripes);
  static void set_at_mark_end(size_t nproactiveflush,
                              size_t nterminateflush,
                              size_t ntrycomplete,
                              size_t ncontinue);
  static void set_at_mark_free(size_t mark_stack_usage);

  static void print();
};

//
// Stat relocation
//
class XStatRelocation : public AllStatic {
private:
  static XRelocationSetSelectorStats _selector_stats;
  static size_t                      _forwarding_usage;
  static size_t                      _small_in_place_count;
  static size_t                      _medium_in_place_count;

  static void print(const char* name,
                    const XRelocationSetSelectorGroupStats& selector_group,
                    size_t in_place_count);

public:
  static void set_at_select_relocation_set(const XRelocationSetSelectorStats& selector_stats);
  static void set_at_install_relocation_set(size_t forwarding_usage);
  static void set_at_relocate_end(size_t small_in_place_count, size_t medium_in_place_count);

  static void print();
};

//
// Stat nmethods
//
class XStatNMethods : public AllStatic {
public:
  static void print();
};

//
// Stat metaspace
//
class XStatMetaspace : public AllStatic {
public:
  static void print();
};

//
// Stat references
//
class XStatReferences : public AllStatic {
private:
  static struct XCount {
    size_t encountered;
    size_t discovered;
    size_t enqueued;
  } _soft, _weak, _final, _phantom;

  static void set(XCount* count, size_t encountered, size_t discovered, size_t enqueued);
  static void print(const char* name, const XCount& ref);

public:
  static void set_soft(size_t encountered, size_t discovered, size_t enqueued);
  static void set_weak(size_t encountered, size_t discovered, size_t enqueued);
  static void set_final(size_t encountered, size_t discovered, size_t enqueued);
  static void set_phantom(size_t encountered, size_t discovered, size_t enqueued);

  static void print();
};

//
// Stat heap
//
class XStatHeap : public AllStatic {
private:
  static struct XAtInitialize {
    size_t min_capacity;
    size_t max_capacity;
  } _at_initialize;

  static struct XAtMarkStart {
    size_t soft_max_capacity;
    size_t capacity;
    size_t free;
    size_t used;
  } _at_mark_start;

  static struct XAtMarkEnd {
    size_t capacity;
    size_t free;
    size_t used;
    size_t live;
    size_t allocated;
    size_t garbage;
  } _at_mark_end;

  static struct XAtRelocateStart {
    size_t capacity;
    size_t free;
    size_t used;
    size_t allocated;
    size_t garbage;
    size_t reclaimed;
  } _at_relocate_start;

  static struct XAtRelocateEnd {
    size_t capacity;
    size_t capacity_high;
    size_t capacity_low;
    size_t free;
    size_t free_high;
    size_t free_low;
    size_t used;
    size_t used_high;
    size_t used_low;
    size_t allocated;
    size_t garbage;
    size_t reclaimed;
  } _at_relocate_end;

  static size_t capacity_high();
  static size_t capacity_low();
  static size_t free(size_t used);
  static size_t allocated(size_t used, size_t reclaimed);
  static size_t garbage(size_t reclaimed);

public:
  static void set_at_initialize(const XPageAllocatorStats& stats);
  static void set_at_mark_start(const XPageAllocatorStats& stats);
  static void set_at_mark_end(const XPageAllocatorStats& stats);
  static void set_at_select_relocation_set(const XRelocationSetSelectorStats& stats);
  static void set_at_relocate_start(const XPageAllocatorStats& stats);
  static void set_at_relocate_end(const XPageAllocatorStats& stats, size_t non_worker_relocated);

  static size_t max_capacity();
  static size_t used_at_mark_start();
  static size_t used_at_relocate_end();

  static void print();
};

#endif // SHARE_GC_X_XSTAT_HPP
