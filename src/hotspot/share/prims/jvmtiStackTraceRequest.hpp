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

#ifndef SHARE_PRIMS_JVMTISTACKTRACEREQUEST_HPP
#define SHARE_PRIMS_JVMTISTACKTRACEREQUEST_HPP

#include "jvmtifiles/jvmti.h"
#include "memory/allStatic.hpp"
#include "utilities/macros.hpp"

class JavaThread;
class JvmtiEnv;

// The agent-facing types of the RequestStackTrace extension. Agents declare
// identical types.

enum {
  JVMTI_STACK_FRAME_INTERPRETED = 0,
  JVMTI_STACK_FRAME_COMPILED    = 1,
  JVMTI_STACK_FRAME_INLINED     = 2,
  JVMTI_STACK_FRAME_NATIVE      = 3
};

enum {
  JVMTI_STACK_TRACE_PENDING = 0, // set by RequestStackTrace
  JVMTI_STACK_TRACE_DONE    = 1, // the frames are available
  JVMTI_STACK_TRACE_FAILED  = 2, // the stack could not be walked
  JVMTI_STACK_TRACE_DROPPED = 3  // discarded, e.g. at thread exit or disable
};

typedef struct {
  jmethodID method;   // may become invalid when its class is unloaded, but never dangles
  jlocation location; // the bci, or -1 for native frames
  jint type;          // JVMTI_STACK_FRAME_*
} jvmtiStackTraceFrame;

// A buffer provided by the agent for one request. The agent sets frames,
// frame_capacity and max_depth; the JVM sets everything else, storing status
// last with release semantics, after which the agent owns the buffer again.
// frame_capacity is the storage the agent provides, max_depth the limit on the
// frames reported; the JVM allocates storage for frames beyond frame_capacity
// up to max_depth.
//
// Frames are reported top first: the first frame_capacity frames in frames, the
// rest in overflow, which the JVM allocates with the same allocator as the JVMTI
// Allocate function, and the agent releases with Deallocate.
typedef struct jvmtiStackTraceBuffer {
  struct jvmtiStackTraceBuffer* next; // JVM: completion list link
  volatile jint status;               // JVM: JVMTI_STACK_TRACE_*
  jint frame_capacity;                // agent: entries in frames, may be 0
  jint max_depth;                     // agent: frames to report at most, may be less than frame_capacity
  jint count;                         // JVM: frames reported
  jboolean biased;                    // JVM: the stack trace is biased towards a safepoint
  jboolean truncated;                 // JVM: the stack was deeper than max_depth
  jlong thread_id;                    // JVM: Thread.threadId() of the sampled thread, 0 if unknown
  jvmtiStackTraceFrame* frames;       // agent: storage for frame_capacity frames
  jvmtiStackTraceFrame* overflow;     // JVM: frames beyond frame_capacity, or null
} jvmtiStackTraceBuffer;

// Stack-trace requests made through the JVMTI RequestStackTrace extension. The
// stack is walked like for JFR, by the requested thread at a safepoint poll or by
// the native stack walker thread, directly into the agent's buffer. Completed
// buffers are pushed onto a lock-free list that the agent registered, if any.
// Each JVMTI environment is independent of the others.
class JvmtiStackTraceRequest : AllStatic {
 public:
  static bool is_enabled(JvmtiEnv* env) NOT_JFR_RETURN_(false);
  static jvmtiError enable(JvmtiEnv* env, jvmtiStackTraceBuffer** completed) NOT_JFR_RETURN_(JVMTI_ERROR_NOT_AVAILABLE);
  static jvmtiError disable(JvmtiEnv* env) NOT_JFR_RETURN_(JVMTI_ERROR_NOT_AVAILABLE);
  static void on_vm_initialized() NOT_JFR_RETURN();
  static void on_env_dispose(JvmtiEnv* env) NOT_JFR_RETURN();

  // Returns false if the request was dropped; the buffer then belongs to the
  // agent. Signal-safe when the target is the current thread.
  static bool request(JvmtiEnv* env, jvmtiStackTraceBuffer* buffer, JavaThread* target,
                      const void* ucontext, bool thread_is_suspended) NOT_JFR_RETURN_(false);
};

#endif // SHARE_PRIMS_JVMTISTACKTRACEREQUEST_HPP
