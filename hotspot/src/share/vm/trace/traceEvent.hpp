/*
 * Copyright (c) 2012, 2017, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_TRACE_TRACEEVENT_HPP
#define SHARE_VM_TRACE_TRACEEVENT_HPP

#include "trace/traceTime.hpp"
#include "utilities/macros.hpp"

enum EventStartTime {
  UNTIMED,
  TIMED
};

#if INCLUDE_TRACE
#include "trace/traceBackend.hpp"
#include "tracefiles/traceEventIds.hpp"
#include "utilities/ticks.hpp"

template<typename T>
class TraceEvent {
 private:
  bool _started;

 protected:
  jlong _startTime;
  jlong _endTime;
  DEBUG_ONLY(bool _committed;)

  void set_starttime(const TracingTime& time) {
    _startTime = time;
  }

  void set_endtime(const TracingTime& time) {
    _endTime = time;
  }

  TraceEvent(EventStartTime timing=TIMED) :
    _startTime(0),
    _endTime(0),
    _started(false)
#ifdef ASSERT
    , _committed(false)
#endif
  {
    if (T::is_enabled()) {
      _started = true;
      if (TIMED == timing && !T::isInstant) {
        static_cast<T*>(this)->set_starttime(Tracing::time());
      }
    }
  }

 public:
  void set_starttime(const Ticks& time) {
    _startTime = time.value();
  }

  void set_endtime(const Ticks& time) {
    _endTime = time.value();
  }

  static bool is_enabled() {
    return Tracing::is_event_enabled(T::eventId);
  }

  bool should_commit() {
    return _started;
  }

  void commit() {
    if (!should_commit()) {
      return;
    }
    assert(!_committed, "event already committed");
    if (_startTime == 0) {
      static_cast<T*>(this)->set_starttime(Tracing::time());
    } else if (_endTime == 0) {
      static_cast<T*>(this)->set_endtime(Tracing::time());
    }
    if (static_cast<T*>(this)->should_write()) {
      static_cast<T*>(this)->writeEvent();
      DEBUG_ONLY(_committed = true;)
    }
  }

  static TraceEventId id() {
    return T::eventId;
  }

  static bool is_instant() {
    return T::isInstant;
  }

  static bool is_requestable() {
    return T::isRequestable;
  }

  static bool has_thread() {
    return T::hasThread;
  }

  static bool has_stacktrace() {
    return T::hasStackTrace;
  }
};

#endif // INCLUDE_TRACE
#endif // SHARE_VM_TRACE_TRACEEVENT_HPP
