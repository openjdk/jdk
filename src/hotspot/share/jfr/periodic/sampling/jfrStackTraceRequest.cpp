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

struct JfrStackTraceRequestPayload {
  JfrTicks sample_ticks;
  jlong user_data;
};

static void process_stack_trace_request(JavaThread* jt, JfrStackWalkRequest& request) {
  const JfrStackTraceRequestPayload payload = request.payload<JfrStackTraceRequestPayload>();
  // Throttle before walking, so a throttled request does not pay for a walk.
  // Unlike for an UNTIMED event, should_commit() evaluates the throttle here
  // and commit() reuses the decision.
  EventStackTraceRequest event;
  if (event.should_commit()) {
    traceid sid;
    traceid tid;
    bool biased;
    const bool walked = JfrStackWalker::walk(request, jt, sid, tid, biased);
    event.set_starttime(payload.sample_ticks);
    event.set_stackTrace(sid);
    event.set_userData(payload.user_data);
    event.set_failed(!walked);
    event.set_biased(walked && biased);
    // The event belongs to the requested thread, whichever thread processes it.
    Thread* const current = Thread::current();
    JfrThreadLocal::impersonate(current, tid);
    assert(JfrThreadLocal::thread_id(current) == tid, "invariant");
    event.commit();
    JfrThreadLocal::stop_impersonating(current);
  } else {
    // Throttled, or the event was disabled after the request was made.
    jt->jfr_thread_local()->increment_stack_trace_requests_lost();
  }
  send_lost_event(jt);
}

static void drop_stack_trace_request(JavaThread* jt, const JfrStackWalkRequest& request) {
  jt->jfr_thread_local()->increment_stack_trace_requests_lost();
}

static const JfrStackWalkerConsumer stack_trace_request_consumer = {
  process_stack_trace_request,
  drop_stack_trace_request
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
  const JfrStackTraceRequestPayload payload = { JfrTicks::now(), user_data };
  JfrStackWalkRequest request(&stack_trace_request_consumer, payload);
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
