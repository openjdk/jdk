/*
 * Copyright (c) 1997, 2014, Oracle and/or its affiliates. All rights reserved.
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
#include "memory/allocation.inline.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/osThread.hpp"
#include "runtime/thread.inline.hpp"
#include "runtime/threadCritical.hpp"
#include "runtime/threadLocalStorage.hpp"
#include "runtime/timer.hpp"
#include "utilities/events.hpp"


EventLog* Events::_logs = NULL;
StringEventLog* Events::_messages = NULL;
StringEventLog* Events::_exceptions = NULL;
StringEventLog* Events::_deopt_messages = NULL;

EventLog::EventLog() {
  // This normally done during bootstrap when we're only single
  // threaded but use a ThreadCritical to ensure inclusion in case
  // some are created slightly late.
  ThreadCritical tc;
  _next = Events::_logs;
  Events::_logs = this;
}

// For each registered event logger, print out the current contents of
// the buffer.  This is normally called when the JVM is crashing.
void Events::print_all(outputStream* out) {
  EventLog* log = _logs;
  while (log != NULL) {
    log->print_log_on(out);
    log = log->next();
  }
}

void Events::print() {
  print_all(tty);
}

void Events::init() {
  if (LogEvents) {
    _messages = new StringEventLog("Events");
    _exceptions = new StringEventLog("Internal exceptions");
    _deopt_messages = new StringEventLog("Deoptimization events");
  }
}

void eventlog_init() {
  Events::init();
}

///////////////////////////////////////////////////////////////////////////
// EventMark

EventMark::EventMark(const char* format, ...) {
  if (LogEvents) {
    va_list ap;
    va_start(ap, format);
    // Save a copy of begin message and log it.
    _buffer.printv(format, ap);
    Events::log(NULL, "%s", _buffer.buffer());
    va_end(ap);
  }
}

EventMark::~EventMark() {
  if (LogEvents) {
    // Append " done" to the begin message and log it
    _buffer.append(" done");
    Events::log(NULL, "%s", _buffer.buffer());
  }
}
