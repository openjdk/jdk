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

#include "code/codeCache.hpp"
#include "interpreter/interpreter.hpp"
#include "runtime/javaThread.hpp"
#include "runtime/stackWalker.hpp"
#include "runtime/stubRoutines.hpp"

static bool is_entry_frame(address pc) {
  return StubRoutines::returns_to_call_stub(pc);
}

static bool is_entry_frame(const StackWalkRequest& request) {
  return is_entry_frame(static_cast<address>(request._sample_pc));
}

static bool is_interpreter(address pc) {
  return Interpreter::contains(pc);
}

static bool is_interpreter(const StackWalkRequest& request) {
  return is_interpreter(static_cast<address>(request._sample_pc));
}

static address interpreter_frame_bcp(const StackWalkRequest& request) {
  assert(is_interpreter(request), "invariant");
  return frame::interpreter_bcp(static_cast<intptr_t*>(request._sample_bcp));
}

static bool in_stack(intptr_t* ptr, JavaThread* jt) {
  assert(jt != nullptr, "invariant");
  return jt->is_in_full_stack_checked(reinterpret_cast<address>(ptr));
}

static bool sp_in_stack(const StackWalkRequest& request, JavaThread* jt) {
  return in_stack(static_cast<intptr_t*>(request._sample_sp), jt);
}

static bool fp_in_stack(const StackWalkRequest& request, JavaThread* jt) {
  return in_stack(static_cast<intptr_t*>(request._sample_bcp), jt);
}

static intptr_t* frame_sender_sp(const StackWalkRequest& request, JavaThread* jt) {
  assert(fp_in_stack(request, jt), "invariant");
  return frame::sender_sp(static_cast<intptr_t*>(request._sample_bcp));
}

static void update_interpreter_frame_pc(StackWalkRequest& request, JavaThread* jt) {
  assert(fp_in_stack(request, jt), "invariant");
  assert(is_interpreter(request), "invariant");
  request._sample_pc = frame::interpreter_return_address(static_cast<intptr_t*>(request._sample_bcp));
}

static void update_frame_sender_sp(StackWalkRequest& request, JavaThread* jt) {
  request._sample_sp = frame_sender_sp(request, jt);
}

static intptr_t* frame_link(const StackWalkRequest& request) {
  return frame::link(static_cast<intptr_t*>(request._sample_bcp));
}

// Less extensive sanity checks for an interpreter frame.
static bool is_valid_interpreter_frame(const StackWalkRequest& request, JavaThread* jt) {
  assert(sp_in_stack(request, jt), "invariant");
  assert(fp_in_stack(request, jt), "invariant");
  return frame::is_interpreter_frame_setup_at(static_cast<intptr_t*>(request._sample_bcp), request._sample_sp);
}

static bool is_continuation_frame(address pc) {
  return ContinuationEntry::return_pc() == pc;
}

static bool is_continuation_frame(const StackWalkRequest& request) {
  return is_continuation_frame(static_cast<address>(request._sample_pc));
}

static intptr_t* sender_for_interpreter_frame(StackWalkRequest& request, JavaThread* jt) {
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

static bool build(StackWalkRequest& request, intptr_t* fp, JavaThread* jt);

static bool build_for_interpreter(StackWalkRequest& request, JavaThread* jt) {
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
static bool build(StackWalkRequest& request, intptr_t* fp, JavaThread* jt) {
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

static bool build_from_ljf(StackWalkRequest& request,
                           JavaThread* jt) {
  assert(jt != nullptr, "invariant");
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
    // TODO
    //if (tl->in_sampling_critical_section()) {
    //  return false;
    //}
    request._sample_pc = last_pc;
    request._sample_bcp = jt->frame_anchor()->last_Java_fp();
    return build_for_interpreter(request, jt);
  }
  request._sample_pc = last_pc;
  return build(request, nullptr, jt);
}

// A biased stack-walk request is denoted by an empty bcp and an empty pc.
static void set_biased(StackWalkRequest& request, JavaThread* jt) {
  if (request._sample_bcp != nullptr) {
    request._sample_bcp = nullptr;
  }
  assert(request._sample_bcp == nullptr, "invariant");
  request._sample_pc = nullptr;
}

void StackWalker::build_stack_walk_request(StackWalkRequest& request, void* ucontext, JavaThread* java_thread) {
  assert(java_thread != nullptr, "invariant");

  // Prioritize the ljf, if one exists.
  request._sample_sp = java_thread->last_Java_sp();
  if (request._sample_sp == nullptr || !build_from_ljf(request, java_thread)) {
    intptr_t* fp;
    request._sample_pc = os::fetch_frame_from_context(ucontext, reinterpret_cast<intptr_t**>(&request._sample_sp), &fp);
    assert(sp_in_stack(request, java_thread), "invariant");
    if (!build(request, fp, java_thread)) {
      set_biased(request, java_thread);
    }
  }
}