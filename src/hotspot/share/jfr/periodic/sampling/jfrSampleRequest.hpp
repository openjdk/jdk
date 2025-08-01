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

#ifndef SHARE_JFR_PERIODIC_SAMPLING_JFRSAMPLEREQUEST_HPP
#define SHARE_JFR_PERIODIC_SAMPLING_JFRSAMPLEREQUEST_HPP

#include "jfr/utilities/jfrTime.hpp"
#include "memory/allocation.hpp"
#include "utilities/growableArray.hpp"

class JavaThread;
class JfrThreadLocal;

enum JfrSampleResult {
  THREAD_SUSPENSION_ERROR,
  WRONG_THREAD_STATE,
  UNPARSABLE_TOP_FRAME,
  INVALID_STACK_TRACE,
  CRASH,
  NO_LAST_JAVA_FRAME,
  UNKNOWN,
  FAIL,
  SKIP,
  SAMPLE_NATIVE,
  SAMPLE_JAVA,
  NOF_SAMPLING_RESULTS
};

enum JfrSampleRequestType {
  NO_SAMPLE,
  JAVA_SAMPLE,
  NATIVE_SAMPLE,
  WAITING_FOR_NATIVE_SAMPLE,
  NOF_SAMPLE_STATES
};

struct JfrSampleRequest {
  void* _sample_sp;
  void* _sample_pc;
  void* _sample_bcp;
  JfrTicks _sample_ticks;

  JfrSampleRequest() :
    _sample_sp(nullptr),
    _sample_pc(nullptr),
    _sample_bcp(nullptr),
    _sample_ticks() {}

  JfrSampleRequest(const JfrTicks& ticks) :
    _sample_sp(nullptr),
    _sample_pc(nullptr),
    _sample_bcp(nullptr),
    _sample_ticks(ticks) {}
};

typedef GrowableArrayCHeap<JfrSampleRequest, mtTracing> JfrSampleRequestQueue;

class JfrSampleRequestBuilder : AllStatic {
 public:
  static JfrSampleResult build_java_sample_request(const void* ucontext,
                                                   JfrThreadLocal* tl,
                                                   JavaThread* jt);
  static void build_cpu_time_sample_request(JfrSampleRequest &request,
                                            void* ucontext,
                                            JavaThread* jt,
                                            JfrThreadLocal* tl,
                                            JfrTicks& now);
};

#endif // SHARE_JFR_PERIODIC_SAMPLING_JFRSAMPLEREQUEST_HPP
