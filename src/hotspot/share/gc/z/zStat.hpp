/*
 * Copyright (c) 2015, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_Z_ZSTAT_HPP
#define SHARE_GC_Z_ZSTAT_HPP

#include "gc/shared/gcCause.hpp"
#include "gc/shared/gcTimer.hpp"
#include "gc/z/zGenerationId.hpp"
#include "gc/z/zLock.hpp"
#include "gc/z/zMetronome.hpp"
#include "gc/z/zRelocationSetSelector.hpp"
#include "gc/z/zThread.hpp"
#include "gc/z/zTracer.hpp"
#include "logging/logHandle.hpp"
#include "memory/allocation.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/numberSeq.hpp"
#include "utilities/ticks.hpp"

class GCTracer;
class ZGeneration;
class ZPage;
class ZPageAllocatorStats;
class ZRelocationSetSelectorGroupStats;
class ZStatSampler;
class ZStatSamplerHistory;
class ZStatWorkers;
struct ZStatCounterData;
struct ZStatSamplerData;

//
// Stat unit printers
//
typedef void (*ZStatUnitPrinter)(LogTargetHandle log, const ZStatSampler&, const ZStatSamplerHistory&);

void ZStatUnitTime(LogTargetHandle log, const ZStatSampler& sampler, const ZStatSamplerHistory& history);
void ZStatUnitBytes(LogTargetHandle log, const ZStatSampler& sampler, const ZStatSamplerHistory& history);
void ZStatUnitThreads(LogTargetHandle log, const ZStatSampler& sampler, const ZStatSamplerHistory& history);
void ZStatUnitBytesPerSecond(LogTargetHandle log, const ZStatSampler& sampler, const ZStatSamplerHistory& history);
void ZStatUnitOpsPerSecond(LogTargetHandle log, const ZStatSampler& sampler, const ZStatSamplerHistory& history);

//
// Stat value
//
class ZStatValue {
private:
  static uintptr_t _base;
  static uint32_t  _cpu_offset;

  const char* const _group;
  const char* const _name;
  const uint32_t    _id;
  const uint32_t    _offset;

protected:
  ZStatValue(const char* group,
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
class ZStatIterableValue : public ZStatValue {
private:
  static uint32_t _count;
  static T*       _first;

  T* _next;

  T* insert() const;

protected:
  ZStatIterableValue(const char* group,
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

//
// Stat sampler
//
class ZStatSampler : public ZStatIterableValue<ZStatSampler> {
private:
  const ZStatUnitPrinter _printer;

public:
  ZStatSampler(const char* group,
               const char* name,
               ZStatUnitPrinter printer);

  ZStatSamplerData* get() const;
  ZStatSamplerData collect_and_reset() const;

  ZStatUnitPrinter printer() const;
};

//
// Stat counter
//
class ZStatCounter : public ZStatIterableValue<ZStatCounter> {
private:
  const ZStatSampler _sampler;

public:
  ZStatCounter(const char* group,
               const char* name,
               ZStatUnitPrinter printer);

  ZStatCounterData* get() const;
  void sample_and_reset() const;
};

//
// Stat unsampled counter
//
class ZStatUnsampledCounter : public ZStatIterableValue<ZStatUnsampledCounter> {
public:
  ZStatUnsampledCounter(const char* name);

  ZStatCounterData* get() const;
  ZStatCounterData collect_and_reset() const;
};

//
// Stat MMU (Minimum Mutator Utilization)
//
class ZStatMMUPause {
private:
  double _start;
  double _end;

public:
  ZStatMMUPause();
  ZStatMMUPause(const Ticks& start, const Ticks& end);

  double end() const;
  double overlap(double start, double end) const;
};

class ZStatMMU {
private:
  static size_t        _next;
  static size_t        _npauses;
  static ZStatMMUPause _pauses[200]; // Record the last 200 pauses

  static double _mmu_2ms;
  static double _mmu_5ms;
  static double _mmu_10ms;
  static double _mmu_20ms;
  static double _mmu_50ms;
  static double _mmu_100ms;

  static const ZStatMMUPause& pause(size_t index);
  static double calculate_mmu(double time_slice);

public:
  static void register_pause(const Ticks& start, const Ticks& end);

  static void print();
};

//
// Stat phases
//
class ZStatPhase {
protected:
  const ZStatSampler _sampler;

  ZStatPhase(const char* group, const char* name);

  void log_start(LogTargetHandle log, bool thread = false) const;
  void log_end(LogTargetHandle log, const Tickspan& duration, bool thread = false) const;

public:
  const char* name() const;

  virtual void register_start(ConcurrentGCTimer* timer, const Ticks& start) const = 0;
  virtual void register_end(ConcurrentGCTimer* timer, const Ticks& start, const Ticks& end) const = 0;
};

class ZStatPhaseCollection : public ZStatPhase {
private:
  const bool _minor;

  GCTracer* jfr_tracer() const;

  void set_used_at_start(size_t used) const;
  size_t used_at_start() const;

public:
  ZStatPhaseCollection(const char* name, bool minor);

  virtual void register_start(ConcurrentGCTimer* timer, const Ticks& start) const;
  virtual void register_end(ConcurrentGCTimer* timer, const Ticks& start, const Ticks& end) const;
};

class ZStatPhaseGeneration : public ZStatPhase {
private:
  const ZGenerationId _id;

  ZGenerationTracer* jfr_tracer() const;

public:
  ZStatPhaseGeneration(const char* name, ZGenerationId id);

  virtual void register_start(ConcurrentGCTimer* timer, const Ticks& start) const;
  virtual void register_end(ConcurrentGCTimer* timer, const Ticks& start, const Ticks& end) const;
};

class ZStatPhasePause : public ZStatPhase {
private:
  static Tickspan _max; // Max pause time

public:
  ZStatPhasePause(const char* name, ZGenerationId id);

  static const Tickspan& max();

  virtual void register_start(ConcurrentGCTimer* timer, const Ticks& start) const;
  virtual void register_end(ConcurrentGCTimer* timer, const Ticks& start, const Ticks& end) const;
};

class ZStatPhaseConcurrent : public ZStatPhase {
public:
  ZStatPhaseConcurrent(const char* name, ZGenerationId id);

  virtual void register_start(ConcurrentGCTimer* timer, const Ticks& start) const;
  virtual void register_end(ConcurrentGCTimer* timer, const Ticks& start, const Ticks& end) const;
};

class ZStatSubPhase : public ZStatPhase {
public:
  ZStatSubPhase(const char* name, ZGenerationId id);

  virtual void register_start(ConcurrentGCTimer* timer, const Ticks& start) const;
  virtual void register_end(ConcurrentGCTimer* timer, const Ticks& start, const Ticks& end) const;
};

class ZStatCriticalPhase : public ZStatPhase {
private:
  const ZStatCounter _counter;
  const bool         _verbose;

public:
  ZStatCriticalPhase(const char* name, bool verbose = true);

  virtual void register_start(ConcurrentGCTimer* timer, const Ticks& start) const;
  virtual void register_end(ConcurrentGCTimer* timer, const Ticks& start, const Ticks& end) const;
};

//
// Stat timer
//
class ZStatTimer : public StackObj {
private:
  ConcurrentGCTimer* const _gc_timer;
  const ZStatPhase&        _phase;
  const Ticks              _start;

public:
  ZStatTimer(const ZStatPhase& phase, ConcurrentGCTimer* gc_timer)
    : _gc_timer(gc_timer),
      _phase(phase),
      _start(Ticks::now()) {
    _phase.register_start(_gc_timer, _start);
  }

  ZStatTimer(const ZStatSubPhase& phase)
    : ZStatTimer(phase, nullptr /* timer */) {
  }

  ZStatTimer(const ZStatCriticalPhase& phase)
    : ZStatTimer(phase, nullptr /* timer */) {
  }

  ~ZStatTimer() {
    const Ticks end = Ticks::now();
    _phase.register_end(_gc_timer, _start, end);
  }
};

class ZStatTimerYoung : public ZStatTimer {
public:
  ZStatTimerYoung(const ZStatPhase& phase);
};

class ZStatTimerOld : public ZStatTimer {
public:
  ZStatTimerOld(const ZStatPhase& phase);
};

class ZStatTimerWorker : public ZStatTimer {
public:
  ZStatTimerWorker(const ZStatPhase& phase);
};

//
// Stat sample/increment
//
void ZStatSample(const ZStatSampler& sampler, uint64_t value);
void ZStatDurationSample(const ZStatSampler& sampler, const Tickspan& duration);
void ZStatInc(const ZStatCounter& counter, uint64_t increment = 1);
void ZStatInc(const ZStatUnsampledCounter& counter, uint64_t increment = 1);

struct ZStatMutatorAllocRateStats {
  double _avg;
  double _predict;
  double _sd;
};

//
// Stat mutator allocation rate
//
class ZStatMutatorAllocRate : public AllStatic {
private:
  static ZLock*          _stat_lock;
  static jlong           _last_sample_time;
  static volatile size_t _sampling_granule;
  static volatile size_t _allocated_since_sample;
  static TruncatedSeq    _samples_time;
  static TruncatedSeq    _samples_bytes;
  static TruncatedSeq    _rate;

  static void update_sampling_granule();

public:
  static const ZStatUnsampledCounter& counter();
  static void sample_allocation(size_t allocation_bytes);

  static void initialize();

  static ZStatMutatorAllocRateStats stats();
};

//
// Stat thread
//
class ZStat : public ZThread {
private:
  static const uint64_t sample_hz = 1;

  ZMetronome _metronome;

  void sample_and_collect(ZStatSamplerHistory* history) const;
  bool should_print(LogTargetHandle log) const;
  void print(LogTargetHandle log, const ZStatSamplerHistory* history) const;

protected:
  virtual void run_thread();
  virtual void terminate();

public:
  ZStat();
};

struct ZStatCycleStats {
  bool _is_warm;
  uint64_t _nwarmup_cycles;
  bool _is_time_trustable;
  double _time_since_last;
  double _last_active_workers;
  double _duration_since_start;
  double _avg_cycle_interval;
  double _avg_serial_time;
  double _sd_serial_time;
  double _avg_parallelizable_time;
  double _sd_parallelizable_time;
  double _avg_parallelizable_duration;
  double _sd_parallelizable_duration;
};

//
// Stat cycle
//
class ZStatCycle {
private:
  ZLock     _stat_lock;
  uint64_t  _nwarmup_cycles;
  Ticks     _start_of_last;
  Ticks     _end_of_last;
  NumberSeq _cycle_intervals;
  NumberSeq _serial_time;
  NumberSeq _parallelizable_time;
  NumberSeq _parallelizable_duration;
  double    _last_active_workers;

  bool is_warm();
  bool is_time_trustable();
  double last_active_workers();
  double duration_since_start();
  double time_since_last();

public:
  ZStatCycle();

  void at_start();
  void at_end(ZStatWorkers* stats_workers, bool record_stats);

  ZStatCycleStats stats();
};

struct ZStatWorkersStats {
  double _accumulated_time;
  double _accumulated_duration;
};

//
// Stat workers
//
class ZStatWorkers {
private:
  ZLock    _stat_lock;
  uint     _active_workers;
  Ticks    _start_of_last;
  Tickspan _accumulated_duration;
  Tickspan _accumulated_time;

  double accumulated_duration();
  double accumulated_time();
  uint active_workers();

public:
  ZStatWorkers();

  void at_start(uint active_workers);
  void at_end();

  double get_and_reset_duration();
  double get_and_reset_time();

  ZStatWorkersStats stats();
};

//
// Stat load
//
class ZStatLoad : public AllStatic {
public:
  static void print();
};

//
// Stat mark
//
class ZStatMark {
private:
  size_t _nstripes;
  size_t _nproactiveflush;
  size_t _nterminateflush;
  size_t _ntrycomplete;
  size_t _ncontinue;
  size_t _mark_stack_usage;

public:
  ZStatMark();

  void at_mark_start(size_t nstripes);
  void at_mark_end(size_t nproactiveflush,
                   size_t nterminateflush,
                   size_t ntrycomplete,
                   size_t ncontinue);
  void at_mark_free(size_t mark_stack_usage);

  void print();
};

struct ZStatRelocationSummary {
  size_t npages_candidates;
  size_t total;
  size_t live;
  size_t empty;
  size_t npages_selected;
  size_t relocate;
};

//
// Stat relocation
//
class ZStatRelocation {
private:
  ZRelocationSetSelectorStats _selector_stats;
  size_t                      _forwarding_usage;
  size_t                      _small_selected;
  size_t                      _small_in_place_count;
  size_t                      _medium_selected;
  size_t                      _medium_in_place_count;

  void print(const char* name,
             ZStatRelocationSummary selector_group,
             size_t in_place_count);

public:
  ZStatRelocation();

  void at_select_relocation_set(const ZRelocationSetSelectorStats& selector_stats);
  void at_install_relocation_set(size_t forwarding_usage);
  void at_relocate_end(size_t small_in_place_count, size_t medium_in_place_count);

  void print_page_summary();
  void print_age_table();
};

//
// Stat nmethods
//
class ZStatNMethods : public AllStatic {
public:
  static void print();
};

//
// Stat metaspace
//
class ZStatMetaspace : public AllStatic {
public:
  static void print();
};

//
// Stat references
//
class ZStatReferences : public AllStatic {
private:
  static struct ZCount {
    size_t encountered;
    size_t discovered;
    size_t enqueued;
  } _soft, _weak, _final, _phantom;

  static void set(ZCount* count, size_t encountered, size_t discovered, size_t enqueued);

public:
  static void set_soft(size_t encountered, size_t discovered, size_t enqueued);
  static void set_weak(size_t encountered, size_t discovered, size_t enqueued);
  static void set_final(size_t encountered, size_t discovered, size_t enqueued);
  static void set_phantom(size_t encountered, size_t discovered, size_t enqueued);

  static void print();
};

struct ZStatHeapStats {
  size_t _live_at_mark_end;
  size_t _used_at_relocate_end;
  size_t _reclaimed_avg;
};

//
// Stat heap
//
class ZStatHeap {
private:
  ZLock _stat_lock;

  static struct ZAtInitialize {
    size_t min_capacity;
    size_t max_capacity;
  } _at_initialize;

  struct ZAtGenerationCollectionStart {
    size_t soft_max_capacity;
    size_t capacity;
    size_t free;
    size_t used;
    size_t used_generation;
  } _at_collection_start;

  struct ZAtMarkStart {
    size_t soft_max_capacity;
    size_t capacity;
    size_t free;
    size_t used;
    size_t used_generation;
    size_t allocation_stalls;
  } _at_mark_start;

  struct ZAtMarkEnd {
    size_t capacity;
    size_t free;
    size_t used;
    size_t used_generation;
    size_t live;
    size_t garbage;
    size_t mutator_allocated;
    size_t allocation_stalls;
  } _at_mark_end;

  struct ZAtRelocateStart {
    size_t capacity;
    size_t free;
    size_t used;
    size_t used_generation;
    size_t live;
    size_t garbage;
    size_t mutator_allocated;
    size_t reclaimed;
    size_t promoted;
    size_t compacted;
    size_t allocation_stalls;
  } _at_relocate_start;

  struct ZAtRelocateEnd {
    size_t capacity;
    size_t capacity_high;
    size_t capacity_low;
    size_t free;
    size_t free_high;
    size_t free_low;
    size_t used;
    size_t used_high;
    size_t used_low;
    size_t used_generation;
    size_t live;
    size_t garbage;
    size_t mutator_allocated;
    size_t reclaimed;
    size_t promoted;
    size_t compacted;
    size_t allocation_stalls;
  } _at_relocate_end;

  NumberSeq _reclaimed_bytes;

  size_t capacity_high() const;
  size_t capacity_low() const;
  size_t free(size_t used) const;
  size_t mutator_allocated(size_t used, size_t freed, size_t relocated) const;
  size_t garbage(size_t freed, size_t relocated, size_t promoted) const;
  size_t reclaimed(size_t freed, size_t relocated, size_t promoted) const;

public:
  ZStatHeap();

  void at_initialize(size_t min_capacity, size_t max_capacity);
  void at_collection_start(const ZPageAllocatorStats& stats);
  void at_mark_start(const ZPageAllocatorStats& stats);
  void at_mark_end(const ZPageAllocatorStats& stats);
  void at_select_relocation_set(const ZRelocationSetSelectorStats& stats);
  void at_relocate_start(const ZPageAllocatorStats& stats);
  void at_relocate_end(const ZPageAllocatorStats& stats, bool record_stats);

  static size_t max_capacity();
  size_t used_at_collection_start() const;
  size_t used_at_mark_start() const;
  size_t used_generation_at_mark_start() const;
  size_t live_at_mark_end() const;
  size_t allocated_at_mark_end() const;
  size_t garbage_at_mark_end() const;
  size_t used_at_relocate_end() const;
  size_t used_at_collection_end() const;
  size_t stalls_at_mark_start() const;
  size_t stalls_at_mark_end() const;
  size_t stalls_at_relocate_start() const;
  size_t stalls_at_relocate_end() const;

  size_t reclaimed_avg();

  ZStatHeapStats stats();

  void print(const ZGeneration* generation) const;
  void print_stalls() const;
};

#endif // SHARE_GC_Z_ZSTAT_HPP
