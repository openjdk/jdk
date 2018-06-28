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

#ifndef SHARE_GC_Z_ZSTAT_HPP
#define SHARE_GC_Z_ZSTAT_HPP

#include "gc/shared/concurrentGCThread.hpp"
#include "gc/shared/gcTimer.hpp"
#include "gc/z/zMetronome.hpp"
#include "logging/logHandle.hpp"
#include "memory/allocation.hpp"
#include "utilities/numberSeq.hpp"
#include "utilities/ticks.hpp"

class ZPage;
class ZStatSampler;
class ZStatSamplerHistory;
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
private:
  static ConcurrentGCTimer _timer;

protected:
  const ZStatSampler _sampler;

  ZStatPhase(const char* group, const char* name);

  void log_start(LogTargetHandle log, bool thread = false) const;
  void log_end(LogTargetHandle log, const Tickspan& duration, bool thread = false) const;

public:
  static ConcurrentGCTimer* timer();

  const char* name() const;

  virtual void register_start(const Ticks& start) const = 0;
  virtual void register_end(const Ticks& start, const Ticks& end) const = 0;
};

class ZStatPhaseCycle : public ZStatPhase {
public:
  ZStatPhaseCycle(const char* name);

  virtual void register_start(const Ticks& start) const;
  virtual void register_end(const Ticks& start, const Ticks& end) const;
};

class ZStatPhasePause : public ZStatPhase {
private:
  static Tickspan _max; // Max pause time

public:
  ZStatPhasePause(const char* name);

  static const Tickspan& max();

  virtual void register_start(const Ticks& start) const;
  virtual void register_end(const Ticks& start, const Ticks& end) const;
};

class ZStatPhaseConcurrent : public ZStatPhase {
public:
  ZStatPhaseConcurrent(const char* name);

  virtual void register_start(const Ticks& start) const;
  virtual void register_end(const Ticks& start, const Ticks& end) const;
};

class ZStatSubPhase : public ZStatPhase {
public:
  ZStatSubPhase(const char* name);

  virtual void register_start(const Ticks& start) const;
  virtual void register_end(const Ticks& start, const Ticks& end) const;
};

class ZStatCriticalPhase : public ZStatPhase {
private:
  const ZStatCounter _counter;
  const bool         _verbose;

public:
  ZStatCriticalPhase(const char* name, bool verbose = true);

  virtual void register_start(const Ticks& start) const;
  virtual void register_end(const Ticks& start, const Ticks& end) const;
};

//
// Stat timer
//
class ZStatTimer : public StackObj {
private:
  const ZStatPhase& _phase;
  const Ticks       _start;

public:
  ZStatTimer(const ZStatPhase& phase) :
      _phase(phase),
      _start(Ticks::now()) {
    _phase.register_start(_start);
  }

  ~ZStatTimer() {
    const Ticks end = Ticks::now();
    _phase.register_end(_start, end);
  }
};

//
// Stat sample/increment
//
void ZStatSample(const ZStatSampler& sampler, uint64_t value, bool trace = ZStatisticsForceTrace);
void ZStatInc(const ZStatCounter& counter, uint64_t increment = 1, bool trace = ZStatisticsForceTrace);
void ZStatInc(const ZStatUnsampledCounter& counter, uint64_t increment = 1);

//
// Stat allocation rate
//
class ZStatAllocRate : public AllStatic {
private:
  static const ZStatUnsampledCounter _counter;
  static TruncatedSeq                _rate;     // B/s
  static TruncatedSeq                _rate_avg; // B/s

public:
  static const uint64_t sample_window_sec = 1; // seconds
  static const uint64_t sample_hz         = 10;

  static const ZStatUnsampledCounter& counter();
  static uint64_t sample_and_reset();

  static double avg();
  static double avg_sd();
};

//
// Stat thread
//
class ZStat : public ConcurrentGCThread {
private:
  static const uint64_t sample_hz = 1;

  ZMetronome _metronome;

  void sample_and_collect(ZStatSamplerHistory* history) const;
  bool should_print(LogTargetHandle log) const;
  void print(LogTargetHandle log, const ZStatSamplerHistory* history) const;

protected:
  virtual void run_service();
  virtual void stop_service();

public:
  ZStat();
};

//
// Stat cycle
//
class ZStatCycle : public AllStatic {
private:
  static uint64_t  _ncycles;
  static Ticks     _start_of_last;
  static Ticks     _end_of_last;
  static NumberSeq _normalized_duration;

public:
  static void at_start();
  static void at_end(double boost_factor);

  static uint64_t ncycles();
  static const AbsSeq& normalized_duration();
  static double time_since_last();
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
class ZStatMark : public AllStatic {
private:
  static size_t _nstripes;
  static size_t _nproactiveflush;
  static size_t _nterminateflush;
  static size_t _ntrycomplete;
  static size_t _ncontinue;

public:
  static void set_at_mark_start(size_t nstripes);
  static void set_at_mark_end(size_t nproactiveflush,
                              size_t nterminateflush,
                              size_t ntrycomplete,
                              size_t ncontinue);

  static void print();
};

//
// Stat relocation
//
class ZStatRelocation : public AllStatic {
private:
  static size_t _relocating;
  static bool   _success;

public:
  static void set_at_select_relocation_set(size_t relocating);
  static void set_at_relocate_end(bool success);

  static void print();
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
  static void print(const char* name, const ZCount& ref);

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
class ZStatHeap : public AllStatic {
private:
  static struct ZAtInitialize {
    size_t max_capacity;
    size_t max_reserve;
  } _at_initialize;

  static struct ZAtMarkStart {
    size_t capacity;
    size_t reserve;
    size_t used;
    size_t free;
  } _at_mark_start;

  static struct ZAtMarkEnd {
    size_t capacity;
    size_t reserve;
    size_t allocated;
    size_t used;
    size_t free;
    size_t live;
    size_t garbage;
  } _at_mark_end;

  static struct ZAtRelocateStart {
    size_t capacity;
    size_t reserve;
    size_t garbage;
    size_t allocated;
    size_t reclaimed;
    size_t used;
    size_t free;
  } _at_relocate_start;

  static struct ZAtRelocateEnd {
    size_t capacity;
    size_t capacity_high;
    size_t capacity_low;
    size_t reserve;
    size_t reserve_high;
    size_t reserve_low;
    size_t garbage;
    size_t allocated;
    size_t reclaimed;
    size_t used;
    size_t used_high;
    size_t used_low;
    size_t free;
    size_t free_high;
    size_t free_low;
  } _at_relocate_end;

  static size_t available(size_t used);
  static size_t reserve(size_t used);
  static size_t free(size_t used);

public:
  static void set_at_initialize(size_t max_capacity,
                                size_t max_reserve);
  static void set_at_mark_start(size_t capacity,
                                size_t used);
  static void set_at_mark_end(size_t capacity,
                              size_t allocated,
                              size_t used);
  static void set_at_select_relocation_set(size_t live,
                                           size_t garbage,
                                           size_t reclaimed);
  static void set_at_relocate_start(size_t capacity,
                                    size_t allocated,
                                    size_t used);
  static void set_at_relocate_end(size_t capacity,
                                  size_t allocated,
                                  size_t reclaimed,
                                  size_t used,
                                  size_t used_high,
                                  size_t used_low);

  static size_t max_capacity();
  static size_t used_at_mark_start();
  static size_t used_at_relocate_end();

  static void print();
};

#endif // SHARE_GC_Z_ZSTAT_HPP
