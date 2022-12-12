/*
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

#include <array>
#include <assert.h>
#include <signal.h>
#include <stdio.h>
#include <stddef.h>
#include <stdlib.h>
#include <string.h>
#include <sys/types.h>
#include <sys/time.h>
#include <ucontext.h>
#include <utility>
#include "jni.h"
#include "jvmti.h"
#include "profile.h"

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
  size_t expected_pc_start = (size_t)method;
  size_t expected_pc_end = (size_t)method + 0x100;
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

bool isStubFrame(ASGST_CallFrame frame, const char* msg_prefix) {
  if (frame.type != ASGST_FRAME_STUB) {
    fprintf(stderr, "%s: Expected STUB frame, got %d", msg_prefix, frame.type);
    return false;
  }
  return true;
}

void printJavaFrame(FILE *stream, ASGST_JavaFrame frame) {
  JvmtiDeallocator<char*> name;
  jvmtiError err = jvmti->GetMethodName(frame.method_id, name.get_addr(), NULL, NULL);
  if (err != JVMTI_ERROR_NONE) {
    fprintf(stream, "Error in GetMethodName: %d", err);
    return;
  }
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
  fprintf(stream, " frame, method = %s, bci = %d", name.get(), frame.bci);
}

template <size_t N = 0> const char* lookForMethod(void* pc, std::array<std::pair<const char*, void*>, N> methods = {}) {
  std::array<std::pair<const char*, size_t>, N> distances;
  int i = 0;
  for (auto method : methods) {
    if (pc >= method.second && pc < (void*)((size_t)method.second + 0x100)) {
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
  } else if (frame.type == ASGST_FRAME_STUB) {
    fprintf(stream, "Stub frame, pc = %p", frame.pc);
  } else {
    fprintf(stream, "Unknown frame type: %d", frame.type);
  }

  const char* method_name = lookForMethod(frame.pc, methods);
  if (method_name != NULL) {
    fprintf(stream, " (%s)", method_name);
  } else {
    fprintf(stream, " (%p)", frame.pc);
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
    case ASGST_FRAME_STUB:
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