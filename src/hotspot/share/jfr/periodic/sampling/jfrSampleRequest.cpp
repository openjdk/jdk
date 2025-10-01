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
#include "asm/codeBuffer.hpp"
#include "interpreter/interpreter.hpp"
#include "jfr/periodic/sampling/jfrSampleRequest.hpp"
#include "jfr/utilities/jfrTime.hpp"
#include "runtime/continuationEntry.hpp"
#include "runtime/frame.inline.hpp"
#include "runtime/javaThread.inline.hpp"
#include "runtime/os.hpp"
#include "runtime/safepointMechanism.inline.hpp"
#include "runtime/stubRoutines.hpp"

static inline bool is_entry_frame(address pc) {
  return StubRoutines::returns_to_call_stub(pc);
}

static inline bool is_entry_frame(const JfrSampleRequest& request) {
  return is_entry_frame(static_cast<address>(request._sample_pc));
}

static inline bool is_interpreter(address pc) {
  return Interpreter::contains(pc);
}

static inline bool is_interpreter(const JfrSampleRequest& request) {
  return is_interpreter(static_cast<address>(request._sample_pc));
}

static inline address interpreter_frame_bcp(const JfrSampleRequest& request) {
  assert(is_interpreter(request), "invariant");
  return frame::interpreter_bcp(static_cast<intptr_t*>(request._sample_bcp));
}

static inline bool in_stack(intptr_t* ptr, JavaThread* jt) {
  assert(jt != nullptr, "invariant");
  return jt->is_in_full_stack_checked(reinterpret_cast<address>(ptr));
}

static inline bool sp_in_stack(const JfrSampleRequest& request, JavaThread* jt) {
  return in_stack(static_cast<intptr_t*>(request._sample_sp), jt);
}

static inline bool fp_in_stack(const JfrSampleRequest& request, JavaThread* jt) {
  return in_stack(static_cast<intptr_t*>(request._sample_bcp), jt);
}

static inline void update_interpreter_frame_sender_pc(JfrSampleRequest& request, intptr_t* fp) {
  request._sample_pc = frame::interpreter_return_address(fp);
}

static inline void update_interpreter_frame_pc(JfrSampleRequest& request, JavaThread* jt) {
  assert(fp_in_stack(request, jt), "invariant");
  assert(is_interpreter(request), "invariant");
  request._sample_pc = frame::interpreter_return_address(static_cast<intptr_t*>(request._sample_bcp));
}

static inline address interpreter_frame_return_address(const JfrSampleRequest& request) {
  assert(is_interpreter(request), "invariant");
  return frame::interpreter_return_address(static_cast<intptr_t*>(request._sample_bcp));
}

static inline intptr_t* frame_sender_sp(const JfrSampleRequest& request, JavaThread* jt) {
  assert(fp_in_stack(request, jt), "invariant");
  return frame::sender_sp(static_cast<intptr_t*>(request._sample_bcp));
}

static inline void update_frame_sender_sp(JfrSampleRequest& request, JavaThread* jt) {
  request._sample_sp = frame_sender_sp(request, jt);
}

static inline void update_frame_sender_sp(JfrSampleRequest& request, intptr_t* fp) {
  request._sample_sp = frame::sender_sp(fp);
}

static inline intptr_t* frame_link(const JfrSampleRequest& request) {
  return frame::link(static_cast<intptr_t*>(request._sample_bcp));
}

static inline void update_sp(JfrSampleRequest& request, int frame_size) {
  assert(frame_size >= 0, "invariant");
  request._sample_sp = static_cast<intptr_t*>(request._sample_sp) + frame_size;
}

static inline void update_pc(JfrSampleRequest& request) {
  assert(request._sample_sp != nullptr, "invariant");
  request._sample_pc = frame::return_address(static_cast<intptr_t*>(request._sample_sp));
}

static inline void update_fp(JfrSampleRequest& request) {
  assert(request._sample_sp != nullptr, "invariant");
  request._sample_bcp = is_interpreter(request) ? frame::fp(static_cast<intptr_t*>(request._sample_sp)) : nullptr;
}

// Less extensive sanity checks for an interpreter frame.
static bool is_valid_interpreter_frame(const JfrSampleRequest& request, JavaThread* jt) {
  assert(sp_in_stack(request, jt), "invariant");
  assert(fp_in_stack(request, jt), "invariant");
  return frame::is_interpreter_frame_setup_at(static_cast<intptr_t*>(request._sample_bcp), request._sample_sp);
}

static inline bool is_continuation_frame(address pc) {
  return ContinuationEntry::return_pc() == pc;
}

static inline bool is_continuation_frame(const JfrSampleRequest& request) {
  return is_continuation_frame(static_cast<address>(request._sample_pc));
}

static intptr_t* sender_for_interpreter_frame(JfrSampleRequest& request, JavaThread* jt) {
  update_interpreter_frame_pc(request, jt); // pick up return address
  if (is_continuation_frame(request) || is_entry_frame(request)) {
    request._sample_pc = nullptr;
    return nullptr;
  }
  update_frame_sender_sp(request, jt);
  intptr_t* fp = nullptr;
  if (is_interpreter(request)) {
    fp = frame_link(request);
  }
  request._sample_bcp = nullptr;
  return fp;
}

static bool build(JfrSampleRequest& request, intptr_t* fp, JavaThread* jt);

static bool build_for_interpreter(JfrSampleRequest& request, JavaThread* jt) {
  assert(is_interpreter(request), "invariant");
  assert(jt != nullptr, "invariant");
  if (!fp_in_stack(request, jt)) {
    return false;
  }
  if (is_valid_interpreter_frame(request, jt)) {
    // Set fp as sp for interpreter frames.
    request._sample_sp = request._sample_bcp;
    // Get real bcp.
    void* const bcp = interpreter_frame_bcp(request);
    // Setting bcp = 1 marks the sample request to represent a native method.
    request._sample_bcp = bcp != nullptr ? bcp : reinterpret_cast<address>(1);
    return true;
  }
  intptr_t* fp = sender_for_interpreter_frame(request, jt);
  if (request._sample_pc == nullptr || request._sample_sp == nullptr) {
    return false;
  }
  return build(request, fp, jt);
}

// Attempt to build a Jfr sample request.
static bool build(JfrSampleRequest& request, intptr_t* fp, JavaThread* jt) {
  assert(request._sample_sp != nullptr, "invariant");
  assert(request._sample_pc != nullptr, "invariant");
  assert(jt != nullptr, "invariant");
  assert(jt->thread_state() == _thread_in_Java || jt->thread_state() == _thread_in_native, "invariant");

  // 1. Interpreter frame?
  if (is_interpreter(request)) {
    request._sample_bcp = fp;
    return build_for_interpreter(request, jt);
  }
  const CodeBlob* const cb = CodeCache::find_blob(request._sample_pc);
  if (cb != nullptr) {
    // 2. Is nmethod?
    return cb->is_nmethod();
    // 3. What kind of CodeBlob or Stub?
    // Longer plan is to make stubs and blobs parsable,
    // and we will have a list of cases here for each blob type
    // describing how to locate the sender. We can't get to the
    // sender of a blob or stub until they have a standardized
    // layout and proper metadata descriptions.
  }
  return false;
}

static bool build_from_ljf(JfrSampleRequest& request,
                           const JfrThreadLocal* tl,
                           JavaThread* jt) {
  assert(tl != nullptr, "invariant");
  assert(jt != nullptr, "invariant");
  assert(jt->jfr_thread_local() == tl, "invariant");
  assert(sp_in_stack(request, jt), "invariant");
  // Last Java frame is available, but might not be walkable, fix it.
  address last_pc = jt->last_Java_pc();
  if (last_pc == nullptr) {
    last_pc = frame::return_address(static_cast<intptr_t*>(request._sample_sp));
    if (last_pc == nullptr) {
      return false;
    }
  }
  assert(last_pc != nullptr, "invariant");
  if (is_interpreter(last_pc)) {
    if (tl->in_sampling_critical_section()) {
      return false;
    }
    request._sample_pc = last_pc;
    request._sample_bcp = jt->frame_anchor()->last_Java_fp();
    return build_for_interpreter(request, jt);
  }
  request._sample_pc = last_pc;
  return build(request, nullptr, jt);
}

static bool build_from_context(JfrSampleRequest& request,
                               const void* ucontext,
                               const JfrThreadLocal* tl,
                               JavaThread* jt) {
  assert(ucontext != nullptr, "invariant");
  assert(tl != nullptr, "invariant");
  assert(jt != nullptr, "invariant");
  assert(jt->jfr_thread_local() == tl, "invariant");
  assert(!jt->has_last_Java_frame(), "invariant");
  intptr_t* fp;
  request._sample_pc = os::fetch_frame_from_context(ucontext, reinterpret_cast<intptr_t**>(&request._sample_sp), &fp);
  assert(sp_in_stack(request, jt), "invariant");
  if (is_interpreter(request)) {
    if (tl->in_sampling_critical_section() || !in_stack(fp, jt)) {
      return false;
    }
    if (frame::is_interpreter_frame_setup_at(fp, request._sample_sp)) {
      // Set fp as sp for interpreter frames.
      request._sample_sp = fp;
      void* bcp = os::fetch_bcp_from_context(ucontext);
      // Setting bcp = 1 marks the sample request to represent a native method.
      request._sample_bcp = bcp != nullptr ? bcp : reinterpret_cast<void*>(1);
      return true;
    }
    request._sample_bcp = fp;
    fp = sender_for_interpreter_frame(request, jt);
    if (request._sample_pc == nullptr || request._sample_sp == nullptr) {
      return false;
    }
  }
  return build(request, fp, jt);
}

static inline JfrSampleResult set_request_and_arm_local_poll(JfrSampleRequest& request, JfrThreadLocal* tl, JavaThread* jt) {
  assert(tl != nullptr, "invariant");
  assert(jt->jfr_thread_local() == tl, "invariant");
  tl->set_sample_state(JAVA_SAMPLE);
  SafepointMechanism::arm_local_poll_release(jt);
  // For a Java sample, request._sample_ticks is also the start time for the SafepointLatency event.
  request._sample_ticks = JfrTicks::now();
  tl->set_sample_request(request);
  return SAMPLE_JAVA;
}

// A biased sample request is denoted by an empty bcp and an empty pc.
static inline JfrSampleResult set_biased_java_sample(JfrSampleRequest& request, JfrThreadLocal* tl, JavaThread* jt) {
  if (request._sample_bcp != nullptr) {
    request._sample_bcp = nullptr;
  }
  assert(request._sample_bcp == nullptr, "invariant");
  request._sample_pc = nullptr;
  return set_request_and_arm_local_poll(request, tl, jt);
}

static inline JfrSampleResult set_unbiased_java_sample(JfrSampleRequest& request, JfrThreadLocal* tl, JavaThread* jt) {
  assert(request._sample_sp != nullptr, "invariant");
  assert(sp_in_stack(request, jt), "invariant");
  assert(request._sample_bcp != nullptr || !is_interpreter(request), "invariant");
  return set_request_and_arm_local_poll(request, tl, jt);
}

JfrSampleResult JfrSampleRequestBuilder::build_java_sample_request(const void* ucontext,
                                                                   JfrThreadLocal* tl,
                                                                   JavaThread* jt) {
  assert(ucontext != nullptr, "invariant");
  assert(tl != nullptr, "invariant");
  assert(tl->sample_state() == NO_SAMPLE, "invariant");
  assert(jt != nullptr, "invariant");
  assert(jt->thread_state() == _thread_in_Java, "invariant");

  JfrSampleRequest request;

  // Prioritize the ljf, if one exists.
  request._sample_sp = jt->last_Java_sp();
  if (request._sample_sp != nullptr) {
    if (build_from_ljf(request, tl, jt)) {
      return set_unbiased_java_sample(request, tl, jt);
    }
  } else if (build_from_context(request, ucontext, tl, jt)) {
    return set_unbiased_java_sample(request, tl, jt);
  }
  return set_biased_java_sample(request, tl, jt);
}


// A biased sample request is denoted by an empty bcp and an empty pc.
static inline void set_cpu_time_biased_sample(JfrSampleRequest& request, JavaThread* jt) {
  if (request._sample_bcp != nullptr) {
    request._sample_bcp = nullptr;
  }
  assert(request._sample_bcp == nullptr, "invariant");
  request._sample_pc = nullptr;
}

void JfrSampleRequestBuilder::build_cpu_time_sample_request(JfrSampleRequest& request,
                                                            void* ucontext,
                                                            JavaThread* jt,
                                                            JfrThreadLocal* tl,
                                                            JfrTicks& now) {
  assert(jt != nullptr, "invariant");
  request._sample_ticks = now;

  // Prioritize the ljf, if one exists.
  request._sample_sp = jt->last_Java_sp();
  if (request._sample_sp == nullptr || !build_from_ljf(request, tl, jt)) {
    intptr_t* fp;
    request._sample_pc = os::fetch_frame_from_context(ucontext, reinterpret_cast<intptr_t**>(&request._sample_sp), &fp);
    assert(sp_in_stack(request, jt), "invariant");
    if (!build(request, fp, jt)) {
      set_cpu_time_biased_sample(request, jt);
    }
  }
}
