/*
 * Copyright (c) 2022, SAP SE. All rights reserved.
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2019, Google and/or its affiliates. All rights reserved.
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
 */

#ifndef _XOPEN_SOURCE
#define _XOPEN_SOURCE 600
#endif

#include <array>
#include <chrono>
#include <cerrno>
#include <signal.h>
#include <stdio.h>
#include <stddef.h>
#include <stdlib.h>
#include <string.h>
#include <sys/types.h>
#include <sys/time.h>
#include <sys/ucontext.h>
#include <ucontext.h>
#include <utility>
#include "jni.h"
#include "jvmti.h"
#include "profile.h"

#ifdef _GNU_SOURCE
#include <dlfcn.h>
#endif

#ifdef DEBUG
// more space for debug info
const int METHOD_HEADER_SIZE = 0x200;
const int METHOD_PRE_HEADER_SIZE = 0x20;
#else
const int METHOD_HEADER_SIZE = 0x100;
const int METHOD_PRE_HEADER_SIZE = 0x10;
#endif

static jvmtiEnv* jvmti;

typedef void (*SigAction)(int, siginfo_t*, void*);
typedef void (*SigHandler)(int);
typedef void (*TimerCallback)(void*);


template <class T>
class JvmtiDeallocator {
 public:
  JvmtiDeallocator() {
    elem_ = NULL;
  }

  ~JvmtiDeallocator() {
    jvmti->Deallocate(reinterpret_cast<unsigned char*>(elem_));
  }

  T* get_addr() {
    return &elem_;
  }

  T get() {
    return elem_;
  }

 private:
  T elem_;
};

static void GetJMethodIDs(jclass klass) {
  jint method_count = 0;
  JvmtiDeallocator<jmethodID*> methods;
  jvmtiError err = jvmti->GetClassMethods(klass, &method_count, methods.get_addr());

  // If ever the GetClassMethods fails, just ignore it, it was worth a try.
  if (err != JVMTI_ERROR_NONE && err != JVMTI_ERROR_CLASS_NOT_PREPARED) {
    fprintf(stderr, "GetJMethodIDs: Error in GetClassMethods: %d\n", err);
  }
}

// assumes that getcontext is called in the beginning of the function
template <typename T> bool doesFrameBelongToMethod(ASGST_CallFrame frame, T* method, const char* msg_prefix) {
  if (frame.type != ASGST_FRAME_CPP) {
    fprintf(stderr, "%s: Expected CPP frame, got %d\n", msg_prefix, frame.type);
    return false;
  }
  ASGST_NonJavaFrame non_java_frame = frame.non_java_frame;
  size_t pc = (size_t)non_java_frame.pc;
  size_t expected_pc_start = (size_t)method - METHOD_PRE_HEADER_SIZE;
  size_t expected_pc_end = (size_t)method + METHOD_HEADER_SIZE;
  if (pc < expected_pc_start || pc > expected_pc_end) {
    fprintf(stderr, "%s: Expected PC in range [%p, %p], got %p\n", msg_prefix,
      (void*)expected_pc_start, (void*)expected_pc_end, (void*)pc);
    return false;
  }
  return true;
}

bool doesFrameBelongToJavaMethod(ASGST_CallFrame frame, uint8_t type, const char* expected_name, const char* msg_prefix) {
  if (frame.type != type) {
    fprintf(stderr, "%s: Expected type %d but got %d\n", msg_prefix, type, frame.type);
    return false;
  }
  ASGST_JavaFrame java_frame = frame.java_frame;
  JvmtiDeallocator<char*> name;
  jvmtiError err = jvmti->GetMethodName(java_frame.method_id, name.get_addr(), NULL, NULL);
  if (err != JVMTI_ERROR_NONE) {
    fprintf(stderr, "%s: Error in GetMethodName: %d\n", msg_prefix, err);
    return false;
  }
  if (strcmp(expected_name, name.get()) != 0) {
    fprintf(stderr, "%s: Expected method name %s but got %s\n", msg_prefix, expected_name, name.get());
    return false;
  }
  return true;
}

bool isCppFrame(ASGST_CallFrame frame, const char* msg_prefix) {
  if (frame.type != ASGST_FRAME_CPP) {
    fprintf(stderr, "%s: Expected CPP frame, got %d", msg_prefix, frame.type);
    return false;
  }
  return true;
}

void printMethod(FILE* stream, jmethodID method) {
  JvmtiDeallocator<char*> name;
  JvmtiDeallocator<char*> signature;
  jvmtiError err = jvmti->GetMethodName(method, name.get_addr(), signature.get_addr(), NULL);
  if (err != JVMTI_ERROR_NONE) {
    fprintf(stream, "Error in GetMethodName: %d", err);
    return;
  }
  jclass klass;
  JvmtiDeallocator<char*> className;
  jvmti->GetMethodDeclaringClass(method, &klass);
  jvmti->GetClassSignature(klass, className.get_addr(), NULL);
  fprintf(stream, "%s.%s%s", className.get(), name.get(), signature.get());
}

void printJavaFrame(FILE *stream, ASGST_JavaFrame frame) {
  switch (frame.type) {
    case ASGST_FRAME_JAVA:
    fprintf(stream, "Java");
    break;
    case ASGST_FRAME_JAVA_INLINED:
    fprintf(stream, "Java inlined");
    break;
    case ASGST_FRAME_NATIVE:
    fprintf(stream, "Native");
    break;
  }
  if (frame.type != ASGST_FRAME_NATIVE) {
    if (frame.comp_level == 0) {
      fprintf(stderr, " interpreted");
    } else {
      fprintf(stderr, " compiled");
    }
  }
  fprintf(stream, " frame, method = ");
  printMethod(stream, frame.method_id);
  fprintf(stream, ", bci = %d", frame.bci);
}

template <size_t N = 0> const char* lookForMethod(void* pc, std::array<std::pair<const char*, void*>, N> methods = {}) {
  std::array<std::pair<const char*, size_t>, N> distances;
  int i = 0;
  for (auto method : methods) {
    if (pc >= method.second && pc < (void*)((size_t)method.second + METHOD_HEADER_SIZE)) {
      distances[i] = std::make_pair(method.first, (size_t)pc - (size_t)method.second);
    }
    i++;
  }
  if (distances.empty()) {
    return NULL;
  }
  auto min_pair = distances[0];
  for (auto pair : distances) {
    if (pair.second < min_pair.second) {
      min_pair = pair;
    }
  }
  return min_pair.first;
}

template <size_t N = 0> void printNonJavaFrame(FILE* stream, ASGST_NonJavaFrame frame, std::array<std::pair<const char*, void*>, N> methods = {}) {
  if (frame.type == ASGST_FRAME_CPP) {
    fprintf(stream, "CPP frame, pc = %p", frame.pc);
  } else {
    fprintf(stream, "Unknown frame type: %d", frame.type);
  }

  const char* method_name = lookForMethod(frame.pc, methods);
  if (method_name != NULL) {
    fprintf(stream, " (%s)", method_name);
  } else {
    fprintf(stream, " (%p)", frame.pc);
    #ifdef _GNU_SOURCE
    Dl_info info;
    if (dladdr(frame.pc, &info) != 0 && info.dli_sname != NULL) {
      fprintf(stream, " (%s)", info.dli_sname);
    }
    #endif
  }
}

template <size_t N = 0> void printFrame(FILE* stream, ASGST_CallFrame frame, std::array<std::pair<const char*, void*>, N> methods = {}) {
  switch (frame.type) {
    case ASGST_FRAME_JAVA:
    case ASGST_FRAME_JAVA_INLINED:
    case ASGST_FRAME_NATIVE:
      printJavaFrame(stream, frame.java_frame);
      break;
    case ASGST_FRAME_CPP:
      printNonJavaFrame(stream, frame.non_java_frame, methods);
      break;
    default:
        fprintf(stream, "Unknown frame type: %d", frame.type);
  }
}

template <size_t N = 0> void printFrames(FILE* stream, ASGST_CallFrame *frames, int length, std::array<std::pair<const char*, void*>, N> methods = {}) {
  for (int i = 0; i < length; i++) {
    fprintf(stream, "Frame %d: ", i);
    printFrame(stream, frames[i], methods);
    fprintf(stream, "\n");
  }
}

template <size_t N = 0> void printTrace(FILE* stream, ASGST_CallTrace trace, std::array<std::pair<const char*, void*>, N> methods = {}) {
  fprintf(stream, "Trace length: %d\n", trace.num_frames);
  fprintf(stream, "Kind: %d\n", trace.kind);
  if (trace.num_frames > 0) {
    printFrames(stream, trace.frames, trace.num_frames, methods);
  }
}

bool areFramesCPPFrames(ASGST_CallFrame *frames, int start, int inclEnd, const char* msg_prefix) {
  for (int i = start; i <= inclEnd; i++) {
    if (frames[i].type != ASGST_FRAME_CPP) {
      fprintf(stderr, "%s: Expected CPP frame at index %d, got %d", msg_prefix, i, frames[i].type);
      return false;
    }
  }
  return true;
}

long getSecondsSinceEpoch(){
    return std::chrono::duration_cast<std::chrono::seconds>(std::chrono::system_clock::now().time_since_epoch()).count();
}

typedef struct {
    jint lineno;                      // line number in the source file
    jmethodID method_id;              // method executed in this frame
} ASGCT_CallFrame;

typedef struct {
    JNIEnv *env_id;                   // Env where trace was recorded
    jint num_frames;                  // number of frames in this trace
    ASGCT_CallFrame *frames;          // frames
} ASGCT_CallTrace;

typedef void (*ASGCTType)(ASGCT_CallTrace *, jint, void *, bool);

static ASGCTType asgct = nullptr;


bool isASGCTNativeFrame(ASGCT_CallFrame frame) {
  return frame.lineno == -3;
}

void printASGCTFrame(FILE* stream, ASGCT_CallFrame frame) {
  JvmtiDeallocator<char*> name;
  jvmtiError err = jvmti->GetMethodName(frame.method_id, name.get_addr(), NULL, NULL);
  if (err != JVMTI_ERROR_NONE) {
    fprintf(stream, "=== asgst sampler failed: Error in GetMethodName: %d", err);
    return;
  }
  if (isASGCTNativeFrame(frame)) {
    fprintf(stream, "Native frame ");
    printMethod(stream, frame.method_id);
  } else {
    fprintf(stream, "Java frame   ");
    printMethod(stream, frame.method_id);
    fprintf(stream, ": %d", frame.lineno);
  }
}

void printASGCTFrames(FILE* stream, ASGCT_CallFrame *frames, int length) {
  for (int i = 0; i < length; i++) {
    fprintf(stream, "Frame %d: ", i);
    printASGCTFrame(stream, frames[i]);
    fprintf(stream, "\n");
  }
}

void printASGCTTrace(FILE* stream, ASGCT_CallTrace trace) {
  fprintf(stream, "ASGCT Trace length: %d\n", trace.num_frames);
  if (trace.num_frames > 0) {
    printASGCTFrames(stream, trace.frames, trace.num_frames);
  }
  fprintf(stream, "ASGCT Trace end\n");
}

void printGSTFrame(FILE* stream, jvmtiFrameInfo frame) {
  if (frame.location == -1) {
    fprintf(stream, "Native frame");
    printMethod(stream, frame.method);
  } else {
    fprintf(stream, "Java frame   ");
    printMethod(stream, frame.method);
    fprintf(stream, ": %d", frame.location);
  }
}


void printGSTTrace(FILE* stream, jvmtiFrameInfo* frames, int length) {
  fprintf(stream, "GST Trace length: %d\n", length);
  for (int i = 0; i < length; i++) {
    fprintf(stream, "Frame %d: ", i);
    printGSTFrame(stream, frames[i]);
    fprintf(stream, "\n");
  }
  fprintf(stream, "GST Trace end\n");
}

void printTraces(ASGST_CallTrace* trace, ASGCT_CallTrace* asgct_trace) {
  fprintf(stderr, "=== asgst trace ===\n");
  printTrace(stderr, *trace);
  fprintf(stderr, "=== asgct trace ===\n");
  printASGCTTrace(stderr, *asgct_trace);
}

/** should be called in the agent load method*/
void initASGCT() {
  if (asgct != nullptr) {
    return;
  }
  void *mptr = dlsym((void*)-2, "AsyncGetCallTrace");
  if (mptr == nullptr) {
    fprintf(stderr, "Error: could not find AsyncGetCallTrace!\n");
    exit(0);
  }
  asgct = reinterpret_cast<ASGCTType>(mptr);
}

JNIEnv* env;

template<int max_depth> void printASGCT(void* ucontext, JNIEnv* oenv = nullptr) {
  assert(env != nullptr || oenv != nullptr);
  ASGCT_CallTrace asgct_trace;
  static ASGCT_CallFrame asgct_frames[max_depth];
  asgct_trace.frames = asgct_frames;
  asgct_trace.env_id = oenv == nullptr ? env : oenv;
  asgct_trace.num_frames = 0;

  asgct(&asgct_trace, max_depth, ucontext, false);
  printASGCTTrace(stderr, asgct_trace);
}

template<int max_depth> void printGST() {
  jthread thread;
  jvmti->GetCurrentThread(&thread);
  jvmtiFrameInfo gstFrames[max_depth];
  jint gstCount = 0;
  jvmti->GetStackTrace(thread, 0, max_depth, gstFrames, &gstCount);
  printGSTTrace(stderr, gstFrames, gstCount);
}

/** asgst, asgct, gst */
template <int max_depth = 100> void printSyncTraces(JNIEnv* oenv = nullptr) {
  assert(env != nullptr || oenv != nullptr);
  ucontext_t context;
  getcontext(&context);
  ASGST_CallTrace trace;
  ASGST_CallFrame frames[max_depth];
  trace.frames = frames;
  AsyncGetStackTrace(&trace, max_depth, &context, ASGST_WALK_SAME_THREAD);
  printTrace(stderr, trace);
  printASGCT<max_depth>(&context, oenv);
  printGST<max_depth>();
}

/**
 * Test that the ASGST trace conforms to the oracles
 *
 * prints the traces on stderr on error and returns false
 *
 * @param prefix a prefix to identify the error message later
 * @param oenv the env to use for the ASGCT trace (or nullptr to use the global env which the caller set before)
 * @param use_asgct whether to use the ASGCT trace, requires the env and that initASGCT has been called before
 * @param use_gct whether to use the GST trace, cannot be used in a signal handler
 */
template<int max_depth = 100> bool check(const char* prefix, JNIEnv* oenv = nullptr, bool use_asgct = true, bool use_gct = true) {
  assert(env != nullptr || oenv != nullptr || !use_asgct);
  JNIEnv *environ = oenv == nullptr ? env : oenv;
  ucontext_t ucontext;
  int err = getcontext(&ucontext);
  if (err != 0) {
    fprintf(stderr, "Error: getcontext failed: %d", errno);
  }



  // obtain the ASGST trace
  ASGST_CallTrace trace;
  ASGST_CallFrame frames[max_depth];
  trace.frames = frames;
  AsyncGetStackTrace(&trace, max_depth, &ucontext, ASGST_WALK_SAME_THREAD);
  int asgst_count = std::max(0, trace.num_frames);

  auto printAll = [&](){
    printTrace(stderr, trace);
    if (use_asgct) {
      printASGCT<max_depth>(&ucontext, oenv);
    }
    if (use_gct) {
      printGST<max_depth>();
    }
  };

  // obtain the GCT trace
  jvmtiFrameInfo gst_frames[max_depth];
  jint gst_count = 0;
  if (use_gct){
    jthread thread;
    jvmti->GetCurrentThread(&thread);
    jvmti->GetStackTrace(thread, 0, max_depth, gst_frames, &gst_count);
  }

  // obtain the ASGCT trace
  ASGCT_CallTrace asgct_trace;
  ASGCT_CallFrame asgct_frames[max_depth];
  asgct_trace.frames = asgct_frames;
  asgct_trace.env_id = environ;
  if (use_asgct) {
    asgct(&asgct_trace, max_depth, &ucontext, false);
  }
  int asgct_count = std::max(0, asgct_trace.num_frames);

  // check that the ASGST trace conforms to the oracles

  // check first that the lengths are the same
  // we don't care about the error codes

  if (use_gct && asgct_count != gst_count) {
    fprintf(stderr, "Error in %s: ASGST trace length %d does not match GST trace length %d\n", prefix, asgst_count, gst_count);
    printAll();
    return false;
  }

  if (use_asgct && asgst_count != asgct_count) {
    fprintf(stderr, "Error in %s: ASGST trace length %d does not match ASGCT trace length %d\n", prefix, asgst_count, asgct_count);
    printAll();
    return false;
  }

  // now check that the frames have the same method ids
  for (int i = 0; i < asgst_count; i++) {
    ASGST_CallFrame asgst_frame = trace.frames[i];

    ASGST_JavaFrame asgst_java_frame = asgst_frame.java_frame;

    if (use_gct) {
      jvmtiFrameInfo gst_frame = gst_frames[i];
      if (gst_frame.method != asgst_java_frame.method_id) {
        fprintf(stderr, "Error in %s: ASGST frame %d method %p does not match GST frame %d method %p\n", prefix, i, asgst_java_frame.method_id, i, gst_frame.method);
        printAll();
        return false;
      }
    }
    if (use_asgct) {
      ASGCT_CallFrame asgct_frame = asgct_trace.frames[i];
      if (asgct_frame.method_id != asgst_java_frame.method_id) {
        fprintf(stderr, "Error in %s: ASGST frame %d method %p does not match ASGCT frame %d method %p\n", prefix, i, asgst_java_frame.method_id, i, asgct_frame.method_id);
        printAll();
        return false;
      }
    }
  }

  // now check that the frames have the same locations

  for (int i = 0; i < asgst_count; i++) {
    ASGST_CallFrame asgst_frame = trace.frames[i];

    ASGST_JavaFrame asgst_java_frame = asgst_frame.java_frame;

    if (use_gct) {
      jvmtiFrameInfo gst_frame = gst_frames[i];
      if (gst_frame.location < 0) {
        if (asgst_java_frame.type != ASGST_FRAME_NATIVE) {
          fprintf(stderr, "Error in %s: ASGST frame %d is not native but GST frame %d is", prefix, i, i);
          printAll();
          return false;
        }
      } else {
        if (gst_frame.location != asgst_java_frame.bci) {
          fprintf(stderr, "Error in %s: ASGST frame %d location %p does not match GST frame %d location %p\n", prefix, i, asgst_java_frame.bci, i, gst_frame.location);
          printAll();
          return false;
        }
      }
    }
    if (use_asgct) {
      ASGCT_CallFrame asgct_frame = asgct_trace.frames[i];
      if (asgct_frame.lineno < 0) {
        if (asgst_java_frame.type != ASGST_FRAME_NATIVE) {
          fprintf(stderr, "Error in %s: ASGST frame %d is not native but ASGCT frame %d is", prefix, i, i);
          printAll();
          return false;
        }
      } else {
        if (asgct_frame.lineno != asgst_java_frame.bci) {
          fprintf(stderr, "Error in %s: ASGST frame %d location %p does not match ASGCT frame %d location %p\n", prefix, i, asgst_java_frame.bci, i, asgct_frame.lineno);
          printAll();
          return false;
        }
      }
    }
  }
  return true;
}

/**
 * Checks that all frames that appear in the ASGST trace without C frames also appear in the ASGST trace with C frames.
 */
template <int max_depth = 100> bool checkThatWithCAndWithoutAreSimilar(const char* prefix) {
  ucontext_t ucontext;
  int err = getcontext(&ucontext);
  if (err != 0) {
    fprintf(stderr, "Error: getcontext failed: %d", errno);
  }



  // obtain the ASGST trace
  ASGST_CallTrace trace;
  ASGST_CallFrame frames[max_depth];
  trace.frames = frames;
  AsyncGetStackTrace(&trace, max_depth, &ucontext, ASGST_WALK_SAME_THREAD);
  int asgst_count = std::max(0, trace.num_frames);

  ASGST_CallTrace traceWithC;
  ASGST_CallFrame framesWithC[max_depth * 10];
  traceWithC.frames = framesWithC;
  AsyncGetStackTrace(&traceWithC, max_depth, &ucontext, ASGST_INCLUDE_C_FRAMES | ASGST_WALK_SAME_THREAD);
  int asgstWithC_count = std::max(0, traceWithC.num_frames);

  auto printAll = [&]() {
    printTrace(stderr, trace);
    printTrace(stderr, traceWithC);
  };

  if ((asgst_count == 0) != (asgstWithC_count == 0)) {
    fprintf(stderr, "Error in %s: ASGST trace length %d does not match ASGST with C %d in non-null lengthness\n", prefix, asgst_count, asgstWithC_count);
    printAll();
    return false;
  }

  if (asgst_count == 0) {
    return true;
  }

  int c_frame_count = 0;

  for (int i = 0; i < asgst_count; i++) {
    ASGST_CallFrame asgst_frame = trace.frames[i];
    while (traceWithC.frames[c_frame_count].type == ASGST_FRAME_CPP) {
      c_frame_count++;
    }
    ASGST_CallFrame asgstWithC_frame = traceWithC.frames[c_frame_count];
    ASGST_JavaFrame asgst_java_frame = asgst_frame.java_frame;
    ASGST_JavaFrame asgstWithC_java_frame = asgstWithC_frame.java_frame;

    if (asgst_java_frame.method_id != asgstWithC_java_frame.method_id) {
      fprintf(stderr, "Error in %s: ASGST frame %d method %p does not match ASGST with C frame %d method %p\n", prefix, i, asgst_java_frame.method_id, c_frame_count, asgstWithC_java_frame.method_id);
      printAll();
      return false;
    }

    if (asgst_java_frame.bci != asgstWithC_java_frame.bci) {
      fprintf(stderr, "Error in %s: ASGST frame %d location %p does not match ASGST with C frame %d location %p\n", prefix, i, asgst_java_frame.bci, c_frame_count, asgstWithC_java_frame.bci);
      printAll();
      return false;
    }
    c_frame_count++;
  }
  return true;
}