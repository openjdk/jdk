/*
 * Copyright (c) 2026, Datadog, Inc. All rights reserved.
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

#include "jfr/jfrEvents.hpp"
#include "jfr/periodic/sampling/jfrStackTraceRequest.hpp"
#include "jfr/periodic/sampling/jfrStackWalker.hpp"
#include "jfr/recorder/jfrEventSetting.hpp"
#include "jfr/support/jfrThreadLocal.hpp"
#include "runtime/javaThread.hpp"
#include "runtime/orderAccess.hpp"

static void send_lost_event(JavaThread* jt) {
  const u4 lost_requests = jt->jfr_thread_local()->get_and_reset_stack_trace_requests_lost();
  if (lost_requests == 0) {
    return;
  }
  // Lost requests belong to the platform thread that owns the queue.
  Thread* const current = Thread::current();
  const traceid tid = JfrThreadLocal::jvm_thread_id(jt);
  JfrThreadLocal::impersonate(current, tid);
  assert(JfrThreadLocal::thread_id(current) == tid, "invariant");
  EventStackTraceRequestsLost event(UNTIMED);
  event.set_starttime(JfrTicks::now());
  event.set_lostRequests(lost_requests);
  event.commit();
  JfrThreadLocal::stop_impersonating(current);
}

class JfrStackTraceRequestCallback : public JfrStackWalkerCallback {
  JfrTicks _sample_ticks;
  jlong _user_data;

  void send_event(JavaThread* jt, traceid sid, traceid tid, bool failed, bool biased) {
    EventStackTraceRequest event(UNTIMED);
    event.set_starttime(_sample_ticks);
    if (event.should_commit()) {
      event.set_eventThread(tid);
      event.set_stackTrace(sid);
      event.set_userData(_user_data);
      event.set_failed(failed);
      event.set_biased(biased);
      event.commit();
    } else {
      // Throttled, or the event was disabled after the request was made.
      jt->jfr_thread_local()->increment_stack_trace_requests_lost();
    }
    send_lost_event(jt);
  }

 public:
  JfrStackTraceRequestCallback(jlong user_data) :
    _sample_ticks(JfrTicks::now()),
    _user_data(user_data) {
  }

  void on_stacktrace(JavaThread* jt, traceid sid, traceid tid, bool biased) final {
    send_event(jt, sid, tid, false, biased);
  }

  void on_failure(JavaThread* jt, traceid tid) final {
    send_event(jt, 0, tid, true, false);
  }

  void on_dropped(JavaThread* jt) final {
    jt->jfr_thread_local()->increment_stack_trace_requests_lost();
  }
};

bool JfrStackTraceRequest::is_enabled() {
  return EventStackTraceRequest::is_enabled();
}

void JfrStackTraceRequest::set_enabled(bool enabled) {
  // Called serially by the recorder when it applies event settings.
  static bool active = false;
  // RequestJFRStackTrace checks is_enabled() before it submits a request, so the
  // stack walker must be running before the event is enabled and until after it
  // is disabled.
  if (enabled && !active) {
    JfrStackWalker::initialize();
    OrderAccess::storestore();
  }
  JfrEventSetting::set_enabled(EventStackTraceRequest::eventId, enabled);
  if (!enabled && active) {
    JfrStackWalker::teardown();
  }
  active = enabled;
}

bool JfrStackTraceRequest::request(JavaThread* target, const void* ucontext, bool thread_is_suspended, jlong user_data) {
  assert(target != nullptr, "invariant");
  JfrStackWalkRequest request;
  request.construct_callback<JfrStackTraceRequestCallback>(user_data);
  if (!JfrStackWalker::request_stack_trace(request, target, ucontext, thread_is_suspended)) {
    target->jfr_thread_local()->increment_stack_trace_requests_lost();
    return false;
  }
  return true;
}

void JfrStackTraceRequest::on_javathread_terminate(JavaThread* jt) {
  assert(jt != nullptr, "invariant");
  send_lost_event(jt);
}
