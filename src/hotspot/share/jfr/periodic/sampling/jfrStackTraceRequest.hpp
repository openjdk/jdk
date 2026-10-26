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

#ifndef SHARE_JFR_PERIODIC_SAMPLING_JFRSTACKTRACEREQUEST_HPP
#define SHARE_JFR_PERIODIC_SAMPLING_JFRSTACKTRACEREQUEST_HPP

#include "jni.h"
#include "memory/allStatic.hpp"

class JavaThread;

// Stack-trace requests made through the JVMTI RequestJFRStackTrace extension,
// reported as jdk.StackTraceRequest events. Dropped requests are counted per
// thread and reported as jdk.StackTraceRequestsLost events.
class JfrStackTraceRequest : AllStatic {
 public:
  static bool is_enabled();
  // Enables or disables jdk.StackTraceRequest, starting or stopping the stack
  // walker so that it is running whenever the event can be observed as enabled.
  static void set_enabled(bool enabled);

  // Returns false if the request was dropped. Signal-safe when the target is
  // the current thread.
  static bool request(JavaThread* target, const void* ucontext, bool thread_is_suspended, jlong user_data);

  static void on_javathread_terminate(JavaThread* jt);
};

#endif // SHARE_JFR_PERIODIC_SAMPLING_JFRSTACKTRACEREQUEST_HPP
