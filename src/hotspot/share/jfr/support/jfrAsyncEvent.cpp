/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "jfr/jni/jfrJavaSupport.hpp"
#include "jfr/periodic/sampling/jfrThreadSampler.hpp"
#include "jfr/recorder/storage/jfrBuffer.hpp"
#include "jfr/support/jfrAsyncEvent.hpp"
#include "jfr/utilities/jfrAllocation.hpp"
#include "jfr/writers/jfrNativeEventWriter.hpp"
#include "oops/oop.hpp"
#include "oops/typeArrayOop.hpp"
#include "runtime/jniHandles.inline.hpp"
#include "runtime/os.hpp"
#include "runtime/threadSMR.hpp"

JfrAsyncEvent::JfrAsyncEvent(long event_id, bool has_duration, bool has_event_thread, bool has_stack_trace, typeArrayOop payloadOop) :
  _event_id(event_id), _has_duration(has_duration), _has_event_thread(has_event_thread), _has_has_stack_trace(has_stack_trace) {
  DEBUG_ONLY(JfrJavaSupport::check_java_thread_in_vm(JavaThread::current()));

  _payload_size = payloadOop->length();
  _payload = (jbyte*)os::malloc(_payload_size, mtTracing);
  jbyte* addr = payloadOop->byte_at_addr(0);
  memcpy(_payload, addr, _payload_size);
}

JfrAsyncEvent::~JfrAsyncEvent() {
  os::free(_payload);
}

void JfrAsyncEvent::async_event_callback(JfrSampleCallbackReason reason,
                                         const JfrTicks* start_time,
                                         const JfrTicks* end_time,
                                         traceid sid,
                                         traceid tid,
                                         void* context) {
  assert(context != nullptr, "invariant");
  JfrAsyncEvent* event = (JfrAsyncEvent*)context;

  if (reason == COMMIT_EVENT) {
    Thread* const thread = Thread::current();
    JfrThreadLocal* const tl = thread->jfr_thread_local();
    JfrBuffer* const buffer = tl->native_buffer();
    if (buffer != nullptr) {
      if (!write_sized_event(buffer, thread, start_time, end_time, tid, sid, event, false)) {
        // Try large size.
        write_sized_event(buffer, thread, start_time, end_time, tid, sid, event, true);
      }
    }
  }
  // Delivered, done!
  delete event;
}

bool JfrAsyncEvent::write_sized_event(JfrBuffer* buffer,
                                      Thread* thread,
                                      const JfrTicks* start_time,
                                      const JfrTicks* end_time,
                                      traceid tid,
                                      traceid sid,
                                      JfrAsyncEvent* event,
                                      bool large_size) {
    assert(start_time != nullptr, "invariant");
    assert(end_time != nullptr, "invariant");
    JfrNativeEventWriter writer(buffer, thread);
    writer.begin_event_write(large_size);
    writer.write<u8>(event->event_id());

    assert(start_time->value() != 0, "invariant");
    writer.write(*start_time);
    if (event->has_duration()) {
      writer.write(*end_time - *start_time);
    }
    if (event->has_event_thread()) {
      writer.write(tid);
    }

    if (event->has_stack_trace()) {
      writer.write(sid);
    }
    // Write payload
    writer.write_bytes((void*)event->payload(), size_t(event->payload_size()));
    return writer.end_event_write(large_size) > 0;
}


void JfrAsyncEvent::send_async_event(jobject target,
                                     jlong event_id,
                                     jboolean has_duration,
                                     jboolean has_event_thread,
                                     jboolean has_stack_trace,
                                     jbyteArray payload,
                                     JavaThread* const jt) {
  DEBUG_ONLY(JfrJavaSupport::check_java_thread_in_vm(JavaThread::current()));

  typeArrayOop payloadOop = typeArrayOop(JNIHandles::resolve(payload));
  JfrAsyncEvent* event = new JfrAsyncEvent(event_id, has_duration, has_event_thread, has_event_thread, payloadOop);
  JfrThreadSampler::sample_thread(jt, target, async_event_callback, (void*)event);
}
