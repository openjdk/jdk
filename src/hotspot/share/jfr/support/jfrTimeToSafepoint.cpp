/*
 * Copyright (c) 2024, Alibaba Group Holding Limited. All rights reserved.
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

#include "jfr/jfrEvents.hpp"
#include "jfr/recorder/jfrEventSetting.inline.hpp"
#include "jfr/support/jfrTimeToSafepoint.hpp"

#include "runtime/safepoint.hpp"
#include "runtime/vmThread.hpp"

struct Entry {
  JavaThread* thread;
  Ticks start;
  Ticks end;
  int iterations;
};

static GrowableArray<Entry>* _events = nullptr;

void JfrTimeToSafepoint::record(JavaThread* thread, Ticks& start, Ticks& end, int iterations) {
  assert(VMThread::vm_thread() == Thread::current(), "invariant");
  assert(!SafepointSynchronize::is_at_safepoint(), "invariant");
  assert(start.value() > 0 && end.value() > 0, "invariant");

  jlong duration = (end - start).value();
  if (duration <= JfrEventSetting::threshold(EventTimeToSafepoint::eventId)) {
    return;
  }

  if (_events == nullptr) {
    _events = new (mtTracing) GrowableArray<Entry>(8, mtTracing);
  }

  Entry entry = {thread, start, end, iterations};
  _events->append(entry);
}

void JfrTimeToSafepoint::emit_events() {
  assert(VMThread::vm_thread() == Thread::current(), "invariant");
  assert(SafepointSynchronize::is_at_safepoint(), "invariant");

  if (_events == nullptr) {
    return;
  }

  for (int i = 0; i < _events->length(); i++) {
    Entry& entry = _events->at(i);
    EventTimeToSafepoint event(UNTIMED);

    event.set_starttime(entry.start);
    event.set_endtime(entry.end);

    if (event.should_commit()) {
      event.set_safepointId(SafepointSynchronize::safepoint_id());
      event.set_iterations(entry.iterations);

      JavaThread* jt = entry.thread;
      event.set_thread(JfrThreadLocal::thread_id(jt));
      
      if (jt->has_last_Java_frame()) {
        JfrThreadLocal* tl = VMThread::vm_thread()->jfr_thread_local();
        JfrStackTrace stacktrace(tl->stackframes(), tl->stackdepth());
        if (stacktrace.record(jt, jt->last_frame(), 0, -1)) {
          event.set_stackTrace(JfrStackTraceRepository::add(stacktrace));
        } else {
          event.set_stackTrace(0);
        }
      } else {
        event.set_stackTrace(0);
      }
      
      event.commit();
    }
  }
  _events->clear();
}
