/*
 * Copyright (c) 2012, 2015, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_SHARED_GCTRACETIME_INLINE_HPP
#define SHARE_VM_GC_SHARED_GCTRACETIME_INLINE_HPP

#include "gc/shared/collectedHeap.hpp"
#include "gc/shared/gcTimer.hpp"
#include "gc/shared/gcTrace.hpp"
#include "gc/shared/gcTraceTime.hpp"
#include "logging/log.hpp"
#include "logging/logStream.hpp"
#include "memory/universe.hpp"
#include "utilities/ticks.hpp"

#define LOG_STOP_HEAP_FORMAT SIZE_FORMAT "M->" SIZE_FORMAT "M("  SIZE_FORMAT "M)"

inline void GCTraceTimeImpl::log_start(jlong start_counter) {
  if (_out_start.is_enabled()) {
    LogStream out(_out_start);

    out.print("%s", _title);
    if (_gc_cause != GCCause::_no_gc) {
      out.print(" (%s)", GCCause::to_string(_gc_cause));
    }
    out.cr();
  }
}

inline void GCTraceTimeImpl::log_stop(jlong start_counter, jlong stop_counter) {
  double duration_in_ms = TimeHelper::counter_to_millis(stop_counter - start_counter);
  double start_time_in_secs = TimeHelper::counter_to_seconds(start_counter);
  double stop_time_in_secs = TimeHelper::counter_to_seconds(stop_counter);

  LogStream out(_out_stop);

  out.print("%s", _title);

  if (_gc_cause != GCCause::_no_gc) {
    out.print(" (%s)", GCCause::to_string(_gc_cause));
  }

  if (_heap_usage_before != SIZE_MAX) {
    CollectedHeap* heap = Universe::heap();
    size_t used_before_m = _heap_usage_before / M;
    size_t used_m = heap->used() / M;
    size_t capacity_m = heap->capacity() / M;
    out.print(" " LOG_STOP_HEAP_FORMAT, used_before_m, used_m, capacity_m);
  }

  out.print_cr(" %.3fms", duration_in_ms);
}

inline void GCTraceTimeImpl::time_stamp(Ticks& ticks) {
  if (_enabled || _timer != NULL) {
    ticks.stamp();
  }
}

inline GCTraceTimeImpl::GCTraceTimeImpl(LogTargetHandle out_start, LogTargetHandle out_stop, const char* title, GCTimer* timer, GCCause::Cause gc_cause, bool log_heap_usage) :
  _enabled(out_stop.is_enabled()),
  _out_start(out_start),
  _out_stop(out_stop),
  _start_ticks(),
  _heap_usage_before(SIZE_MAX),
  _title(title),
  _gc_cause(gc_cause),
  _timer(timer) {

  time_stamp(_start_ticks);
  if (_enabled) {
    if (log_heap_usage) {
      _heap_usage_before = Universe::heap()->used();
    }
    log_start(_start_ticks.value());
  }
  if (_timer != NULL) {
    _timer->register_gc_phase_start(_title, _start_ticks);
  }
}

inline GCTraceTimeImpl::~GCTraceTimeImpl() {
  Ticks stop_ticks;
  time_stamp(stop_ticks);
  if (_enabled) {
    log_stop(_start_ticks.value(), stop_ticks.value());
  }
  if (_timer != NULL) {
    _timer->register_gc_phase_end(stop_ticks);
  }
}

template <LogLevelType Level, LogTagType T0, LogTagType T1, LogTagType T2, LogTagType T3, LogTagType T4, LogTagType GuardTag >
GCTraceConcTimeImpl<Level, T0, T1, T2, T3, T4, GuardTag>::GCTraceConcTimeImpl(const char* title) :
  _enabled(LogImpl<T0, T1, T2, T3, T4, GuardTag>::is_level(Level)), _start_time(os::elapsed_counter()), _title(title) {
  if (_enabled) {
    LogImpl<T0, T1, T2, T3, T4>::template write<Level>("%s", _title);
  }
}

template <LogLevelType Level, LogTagType T0, LogTagType T1, LogTagType T2, LogTagType T3, LogTagType T4, LogTagType GuardTag >
GCTraceConcTimeImpl<Level, T0, T1, T2, T3, T4, GuardTag>::~GCTraceConcTimeImpl() {
  if (_enabled) {
    jlong stop_time = os::elapsed_counter();
    LogImpl<T0, T1, T2, T3, T4>::template write<Level>("%s %0.3fms", _title,
                                                       TimeHelper::counter_to_millis(stop_time - _start_time));
  }
}

// Figure out the first __NO_TAG position and replace it with 'start'.
#define INJECT_START_TAG(T1, T2, T3, T4) \
    ((                          T1 == LogTag::__NO_TAG) ? PREFIX_LOG_TAG(start) : T1), \
    ((T1 != LogTag::__NO_TAG && T2 == LogTag::__NO_TAG) ? PREFIX_LOG_TAG(start) : T2), \
    ((T2 != LogTag::__NO_TAG && T3 == LogTag::__NO_TAG) ? PREFIX_LOG_TAG(start) : T3), \
    ((T3 != LogTag::__NO_TAG && T4 == LogTag::__NO_TAG) ? PREFIX_LOG_TAG(start) : T4)

template <LogLevelType level, LogTagType T0, LogTagType T1, LogTagType T2, LogTagType T3, LogTagType T4, LogTagType GuardTag>
GCTraceTimeImplWrapper<level, T0, T1, T2, T3, T4, GuardTag>::GCTraceTimeImplWrapper(
    const char* title, GCTimer* timer, GCCause::Cause gc_cause, bool log_heap_usage)
    : _impl(
        LogTargetHandle::create<level, T0, INJECT_START_TAG(T1, T2, T3, T4), GuardTag>(),
        LogTargetHandle::create<level, T0, T1, T2, T3, T4, GuardTag>(),
        title,
        timer,
        gc_cause,
        log_heap_usage) {

  STATIC_ASSERT(T0 != LogTag::__NO_TAG); // Need some tag to log on.
  STATIC_ASSERT(T4 == LogTag::__NO_TAG); // Need to leave at least the last tag for the "start" tag in log_start()
}

#undef INJECT_START_TAG

template <LogLevelType Level, LogTagType T0, LogTagType T1, LogTagType T2, LogTagType T3, LogTagType T4, LogTagType GuardTag>
GCTraceTimeImplWrapper<Level, T0, T1, T2, T3, T4, GuardTag>::~GCTraceTimeImplWrapper() {}

#define GCTraceTime(Level, ...) GCTraceTimeImplWrapper<LogLevel::Level, LOG_TAGS(__VA_ARGS__)>
#define GCTraceConcTime(Level, ...) GCTraceConcTimeImpl<LogLevel::Level, LOG_TAGS(__VA_ARGS__)>

#endif // SHARE_VM_GC_SHARED_GCTRACETIME_INLINE_HPP
