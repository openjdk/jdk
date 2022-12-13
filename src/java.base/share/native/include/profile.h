/*
 * Copyright (c) 1997, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

#ifndef JVM_PROFILE_H
#define JVM_PROFILE_H

#include <sys/stat.h>
#include <sys/types.h>
#include <stdint.h>

#include "jni.h"


// error codes, equivalent to the forte error codes for AsyncGetCallTrace
enum ASGST_Error {
  ASGST_NO_JAVA_FRAME         =   0,
  ASGST_NO_CLASS_LOAD         =  -1,
  ASGST_GC_ACTIVE             =  -2,
  ASGST_UNKNOWN_NOT_JAVA      =  -3,
  ASGST_NOT_WALKABLE_NOT_JAVA =  -4,
  ASGST_UNKNOWN_JAVA          =  -5,
  ASGST_UNKNOWN_STATE         =  -7,
  ASGST_THREAD_EXIT           =  -8,
  ASGST_DEOPT                 =  -9,
  ASGST_THREAD_NOT_JAVA       = -10
};

enum ASGST_FrameTypeId {
  ASGST_FRAME_JAVA         = 1, // JIT compiled and interpreted
  ASGST_FRAME_JAVA_INLINED = 2, // inlined JIT compiled
  ASGST_FRAME_NATIVE       = 3, // native wrapper to call C methods from Java
  ASGST_FRAME_STUB         = 4, // VM generated stubs
  ASGST_FRAME_CPP          = 5  // C/C++/... frames
};

typedef struct {
  uint8_t type;            // frame type
  int8_t comp_level;      // compilation level, 0 is interpreted, -1 is undefined, > 1 is JIT compiled
  uint16_t bci;            // 0 < bci < 65536
  jmethodID method_id;
} ASGST_JavaFrame;         // used for FRAME_JAVA, FRAME_JAVA_INLINED and FRAME_NATIVE

typedef struct {
  uint8_t type;      // frame type
  void *pc;          // current program counter inside this frame
} ASGST_NonJavaFrame; // used for FRAME_STUB, FRAME_CPP

typedef union {
  uint8_t type;     // to distinguish between JavaFrame and NonJavaFrame
  ASGST_JavaFrame java_frame;
  ASGST_NonJavaFrame non_java_frame;
} ASGST_CallFrame;

// Enumeration to distinguish tiers of compilation, only >= 0 are used
/*enum CompLevel {
  CompLevel_any               = -1,        // Used for querying the state
  CompLevel_all               = -1,        // Used for changing the state  // unused
  CompLevel_none              = 0,         // Interpreter
  CompLevel_simple            = 1,         // C1
  CompLevel_limited_profile   = 2,         // C1, invocation & backedge counters
  CompLevel_full_profile      = 3,         // C1, invocation & backedge counters + mdo
  CompLevel_full_optimization = 4          // C2 or JVMCI
};*/

enum ASGST_TRACE_KIND {
  ASGST_JAVA_TRACE     = 0,
  ASGST_CPP_TRACE      = 1,
  ASGST_GC_TRACE       = 2,
  ASGST_DEOPT_TRACE    = 3,
  ASGST_UNKNOWN_TRACE  = 4,
};

typedef struct {
  jint num_frames;                // number of frames in this trace,
                                  // (< 0 indicates the frame is not walkable).
  uint8_t kind;                   // kind of the trace
  ASGST_CallFrame *frames;        // frames that make up this trace. Callee followed by callers.
  void* frame_info;               // more information on frames
} ASGST_CallTrace;


enum ASGST_Options {
  ASGST_INCLUDE_C_FRAMES         = 1, // include C and stub frames too
  ASGST_INCLUDE_NON_JAVA_THREADS = 2, // walk the stacks of C/Cpp, GC and deopt threads too
};


// Asynchronous profiling entry point which is usually called from signal
// handler. It is a replacement of AsyncGetCallTrace.
//
// This function must only be called when JVM/TI
// CLASS_LOAD events have been enabled since agent startup. The enabled
// event will cause the jmethodIDs to be allocated at class load time.
// The jmethodIDs cannot be allocated in a signal handler because locks
// cannot be grabbed in a signal handler safely.
//
// void AsyncGetStackTrace(ASGST_CallTrace *trace, jint depth, void* ucontext, int32_t options)
//
// Called by the profiler to obtain the current method call stack trace for
// a given thread. The profiler agent should allocate a ASGST_CallTrace
// structure with enough memory for the requested stack depth. The VM fills in
// the frames buffer, the num_frames and the kind field.
//
// Arguments:
//
//   trace    - trace data structure to be filled by the VM.
//   depth    - depth of the call stack trace.
//   ucontext - ucontext_t of the LWP
//   options  - bit flags for additional configuration
extern "C" JNIEXPORT
void AsyncGetStackTrace(ASGST_CallTrace *trace, jint depth, void* ucontext, int32_t options);

#endif // JVM_PROFILE_H