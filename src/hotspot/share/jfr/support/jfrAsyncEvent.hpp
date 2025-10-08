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

#ifndef SHARE_JFR_SUPPORT_JFRASYNCEVENT_HPP
#define SHARE_JFR_SUPPORT_JFRASYNCEVENT_HPP

#include "jfr/utilities/jfrTime.hpp"
#include "jfr/utilities/jfrTypes.hpp"
#include "jni.h"
#include "utilities/exceptions.hpp"

class JfrAsyncEvent : public JfrCHeapObj {
private:
  long    _event_id;
  jbyte*  _payload;
  int     _payload_size;
  bool    _has_duration;
  bool    _has_event_thread;
  bool    _has_has_stack_trace;

public:
  // Send asynchronous event to its target
  static void send(JavaThread* const jt,
                   jobject target,
                   jlong event_id,
                   jboolean has_duration,
                   jboolean has_event_thread,
                   jboolean has_stack_trace,
                   jbyteArray payload);
private:
  JfrAsyncEvent(long event_id, bool has_duration, bool has_event_thread, bool has_stack_trace, typeArrayOop payloadOop);
  ~JfrAsyncEvent();

  long event_id()         const { return _event_id; }
  bool has_duration()     const { return _has_duration; }
  bool has_event_thread() const { return _has_event_thread; }
  bool has_stack_trace()  const { return _has_has_stack_trace; }
  int  payload_size()     const { return _payload_size; }
  jbyte* payload()        const { return _payload; }

  // Callback
  static void async_event_callback(JfrSampleCallbackReason reason,
                                    const JfrTicks* start_time,
                                    const JfrTicks* end_time,
                                    traceid sid,
                                    traceid tid,
                                    void* context);
  static bool write_sized_event(JfrBuffer* buffer,
                                Thread* thread,
                                const JfrTicks* start_time,
                                const JfrTicks* end_time,
                                traceid tid,
                                traceid sid,
                                JfrAsyncEvent* event,
                                bool large_size);
};

#endif // SHARE_JFR_SUPPORT_JFRASYNCEVENT_HPP
