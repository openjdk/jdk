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

#ifndef SHARE_VM_GC_SHARED_GCTRACETIME_HPP
#define SHARE_VM_GC_SHARED_GCTRACETIME_HPP

#include "logging/log.hpp"
#include "logging/logHandle.hpp"
#include "logging/logStream.hpp"
#include "memory/allocation.hpp"
#include "utilities/ticks.hpp"

class GCTraceCPUTime : public StackObj {
  bool _active;                 // true if times will be measured and printed
  double _starting_user_time;   // user time at start of measurement
  double _starting_system_time; // system time at start of measurement
  double _starting_real_time;   // real time at start of measurement
 public:
  GCTraceCPUTime();
  ~GCTraceCPUTime();
};

class GCTimer;

class GCTraceTimeImpl : public StackObj {
 private:
  LogTargetHandle _out_start;
  LogTargetHandle _out_stop;
  bool _enabled;
  Ticks _start_ticks;
  const char* _title;
  GCCause::Cause _gc_cause;
  GCTimer* _timer;
  size_t _heap_usage_before;

  void log_start(jlong start_counter);
  void log_stop(jlong start_counter, jlong stop_counter);
  void time_stamp(Ticks& ticks);

 public:
  GCTraceTimeImpl(LogTargetHandle out_start, LogTargetHandle out_end, const char* title, GCTimer* timer = NULL, GCCause::Cause gc_cause = GCCause::_no_gc, bool log_heap_usage = false);
  ~GCTraceTimeImpl();
};

template <LogLevelType Level, LogTagType T0, LogTagType T1, LogTagType T2, LogTagType T3, LogTagType T4, LogTagType GuardTag>
class GCTraceTimeImplWrapper : public StackObj {
  GCTraceTimeImpl _impl;
public:
  GCTraceTimeImplWrapper(const char* title, GCTimer* timer = NULL, GCCause::Cause gc_cause = GCCause::_no_gc, bool log_heap_usage = false);
  ~GCTraceTimeImplWrapper();
};

// Similar to GCTraceTimeImpl but is intended for concurrent phase logging,
// which is a bit simpler and should always print the start line, i.e. not add the "start" tag.
template <LogLevelType Level, LogTagType T0, LogTagType T1 = LogTag::__NO_TAG, LogTagType T2 = LogTag::__NO_TAG, LogTagType T3 = LogTag::__NO_TAG,
    LogTagType T4 = LogTag::__NO_TAG, LogTagType GuardTag = LogTag::__NO_TAG>
class GCTraceConcTimeImpl : public StackObj {
 private:
  bool _enabled;
  jlong _start_time;
  const char* _title;
 public:
  GCTraceConcTimeImpl(const char* title);
  ~GCTraceConcTimeImpl();
  jlong start_time() { return _start_time; }
};

#endif // SHARE_VM_GC_SHARED_GCTRACETIME_HPP
