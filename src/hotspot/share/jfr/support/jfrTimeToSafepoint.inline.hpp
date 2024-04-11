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

#ifndef SHARE_JFR_SUPPORT_JFRTIMETOSAFEPOINT_INLINE_HPP
#define SHARE_JFR_SUPPORT_JFRTIMETOSAFEPOINT_INLINE_HPP

#include "jfr/support/jfrTimeToSafepoint.hpp"

#include "jfr/jfrEvents.hpp"
#include "jfr/recorder/jfrEventSetting.inline.hpp"
#include "runtime/safepoint.hpp"
#include "runtime/vmThread.hpp"

inline void JfrTimeToSafepoint::on_synchronizing() {
  assert(Thread::current()->is_VM_thread(), "invariant");
  assert(SafepointSynchronize::is_synchronizing(), "invariant");

  _active = EventTimeToSafepoint::is_enabled();
  if (_active) {
    _start = JfrTicks::now();
  } else if (_entries != nullptr) {
    assert(_entries->length() == 0, "invariant");
    delete _entries;
    _entries = nullptr;
  }
}

inline void JfrTimeToSafepoint::on_thread_not_running(JavaThread* thread, int iterations) {
  assert(Thread::current()->is_VM_thread(), "invariant");
  assert(SafepointSynchronize::is_synchronizing(), "invariant");
  assert(thread != nullptr && iterations >= 0, "invariant");

  if (!_active) {
    return;
  }

  assert(_start.value() > 0, "invariant");

  JfrTicks end = JfrTicks::now();
  if ((end - _start).value() <= JfrEventSetting::threshold(EventTimeToSafepoint::eventId)) {
    return;
  }

  if (_entries == nullptr) {
    _entries = new (mtTracing) GrowableArray<Entry>(4, mtTracing);
  }

  Entry entry = {thread, end, iterations};
  _entries->append(entry);
}

inline void JfrTimeToSafepoint::on_synchronized() {
  assert(Thread::current()->is_VM_thread(), "invariant");
  assert(SafepointSynchronize::is_at_safepoint(), "invariant");

  if (!_active || _entries == nullptr || _entries->length() == 0) {
    return;
  }

  JfrThreadLocal* tl = VMThread::vm_thread()->jfr_thread_local();
  assert(!tl->has_cached_stack_trace(), "invariant");

  bool stacktrace_enabled = EventTimeToSafepoint::is_stacktrace_enabled();
  for (int i = 0; i < _entries->length(); i++) {
    Entry& entry = _entries->at(i);

    EventTimeToSafepoint event(UNTIMED);
    event.set_starttime(_start);
    event.set_endtime(entry.end);

    event.set_safepointId(SafepointSynchronize::safepoint_id());
    event.set_iterations(entry.iterations);

    JavaThread* jt = entry.thread;
    event.set_thread(JfrThreadLocal::thread_id(jt));

    if (stacktrace_enabled && jt->has_last_Java_frame()) {
      JfrStackTrace stacktrace(tl->stackframes(), tl->stackdepth());
      if (stacktrace.record(jt, jt->last_frame(), 0, -1)) {
        tl->set_cached_stack_trace_id(JfrStackTraceRepository::add(stacktrace));
      } else {
        tl->set_cached_stack_trace_id(0);
      }
    } else {
      tl->set_cached_stack_trace_id(0);
    }
    event.commit();
  }

  tl->clear_cached_stack_trace();

  // Should we shrink it?
  _entries->clear();
}

#endif // SHARE_JFR_SUPPORT_JFRTIMETOSAFEPOINT_INLINE_HPP
