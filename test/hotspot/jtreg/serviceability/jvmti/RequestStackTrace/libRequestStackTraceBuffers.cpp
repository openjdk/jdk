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
 */

// Profiler-style agents for the RequestStackTrace extension. Each profiler has
// its own JVMTI environment, pre-allocates a pool of buffers, requests stack
// traces into them (also from a signal handler), and has its completed buffers
// drained on a thread of the agent's own. Two profilers run side by side to
// show that environments are independent.

#include "jvmti.h"

// The agent uses POSIX threads and signals; the test does not run on Windows.
#ifndef _WIN32

#include <atomic>
#include <pthread.h>
#include <signal.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

// Mirrors the types in prims/jvmtiStackTraceRequest.hpp.
enum { STACK_TRACE_PENDING = 0, STACK_TRACE_DONE = 1, STACK_TRACE_FAILED = 2, STACK_TRACE_DROPPED = 3 };

typedef struct {
  jmethodID method;
  jlocation location;
  jint type;
} StackTraceFrame;

typedef struct StackTraceBuffer {
  struct StackTraceBuffer* next;
  volatile jint status;
  jint frame_capacity;
  jint max_depth;
  jint count;
  jboolean biased;
  jboolean truncated;
  jlong thread_id;
  StackTraceFrame* frames;
  StackTraceFrame* overflow;
} StackTraceBuffer;

// Request kinds.
enum { KIND_JNI = 0, KIND_SIGNAL = 1, KIND_FOREIGN = 2, KIND_DETACH = 3, KIND_SLEEPER = 4, KIND_DEPTH = 5, KINDS = 6 };
enum { ACCEPTED = 0, DROPPED_AT_SUBMIT, NO_BUFFER, DONE, FAILED, DROPPED, MATCHED, TRUNCATED, OVERFLOWED,
       WRONG_THREAD, MISROUTED, BAD_DEPTH, COUNTERS };
static const char* const EXPECTED_METHOD[KINDS] = { "nativeCaller", "spin", "spin", "", "", "" };

static const int POOL_SIZE = 512;
static const int PROFILERS = 2;
enum { SLOT_FREE = 0, SLOT_CLAIMED = 1, SLOT_REQUESTED = 2 };

struct Profiler {
  jvmtiEnv* jvmti;
  bool use_list;
  bool active;
  StackTraceBuffer* volatile completed;
  StackTraceBuffer buffers[POOL_SIZE];
  StackTraceFrame* frame_storage;
  std::atomic<int> slot_state[POOL_SIZE];
  int slot_kind[POOL_SIZE];
  std::atomic<int> counts[KINDS][COUNTERS];
};

static Profiler g_profilers[PROFILERS];
static jint g_frame_capacity = 0;
static jint g_max_depth = 0;
// When not negative, max_depth for the next requests (KIND_DEPTH).
static std::atomic<jint> g_depth_override{-1};
static JavaVM* g_vm = nullptr;
static jvmtiExtensionFunction g_request = nullptr;
static jvmtiExtensionFunction g_enable = nullptr;
static jvmtiExtensionFunction g_disable = nullptr;
static jlong g_expected_tid[KINDS];
static pthread_t g_worker;
static std::atomic<bool> g_drain_stop{false};
static pthread_t g_drainer;

static int index_of(const Profiler& p, StackTraceBuffer* buffer) {
  return (int)(buffer - p.buffers);
}

// Signal-safe: claims a free slot without locking or allocating.
static StackTraceBuffer* claim(Profiler& p, int kind) {
  for (int i = 0; i < POOL_SIZE; i++) {
    int expected = SLOT_FREE;
    if (p.slot_state[i].compare_exchange_strong(expected, SLOT_CLAIMED)) {
      StackTraceBuffer* buffer = &p.buffers[i];
      buffer->frame_capacity = g_frame_capacity;
      const jint depth_override = g_depth_override.load();
      buffer->max_depth = depth_override >= 0 ? depth_override : g_max_depth;
      buffer->frames = g_frame_capacity > 0 ? p.frame_storage + (size_t)i * g_frame_capacity : nullptr;
      p.slot_kind[i] = kind;
      // A polling drain thread must not see the previous request's final status:
      // reset it before publishing the slot as outstanding.
      __atomic_store_n(&buffer->status, STACK_TRACE_PENDING, __ATOMIC_RELAXED);
      p.slot_state[i].store(SLOT_REQUESTED, std::memory_order_release);
      return buffer;
    }
  }
  return nullptr;
}

static void request(Profiler& p, jthread thread, void* ucontext, int kind) {
  StackTraceBuffer* buffer = claim(p, kind);
  if (buffer == nullptr) {
    p.counts[kind][NO_BUFFER]++;
    return;
  }
  jvmtiError rc = g_request(p.jvmti, thread, ucontext, buffer);
  if (rc == JVMTI_ERROR_NONE) {
    p.counts[kind][ACCEPTED]++;
  } else {
    // Not accepted: the buffer belongs to the agent again.
    p.counts[kind][DROPPED_AT_SUBMIT]++;
    p.slot_state[index_of(p, buffer)] = SLOT_FREE;
  }
}

static const StackTraceFrame& frame_at(const StackTraceBuffer* buffer, int i) {
  return i < buffer->frame_capacity ? buffer->frames[i] : buffer->overflow[i - buffer->frame_capacity];
}

// Runs on the drain thread, once the buffer belongs to the agent again.
static void process(Profiler& p, StackTraceBuffer* buffer) {
  const int i = index_of(p, buffer);
  if (i < 0 || i >= POOL_SIZE) {
    // Returned on the wrong profiler's list. Count it on the first kind, it is fatal anyway.
    p.counts[0][MISROUTED]++;
    return;
  }
  const int kind = p.slot_kind[i];
  switch (__atomic_load_n(&buffer->status, __ATOMIC_ACQUIRE)) {
    case STACK_TRACE_DONE: {
      p.counts[kind][DONE]++;
      bool matched = false;
      for (int f = 0; f < buffer->count; f++) {
        char* name = nullptr;
        if (p.jvmti->GetMethodName(frame_at(buffer, f).method, &name, nullptr, nullptr) == JVMTI_ERROR_NONE) {
          matched |= strcmp(name, EXPECTED_METHOD[kind]) == 0;
          p.jvmti->Deallocate((unsigned char*)name);
        }
      }
      if (matched) p.counts[kind][MATCHED]++;
      if (buffer->truncated) p.counts[kind][TRUNCATED]++;
      if (buffer->count > buffer->max_depth ||
          (buffer->max_depth <= buffer->frame_capacity && buffer->overflow != nullptr)) {
        p.counts[kind][BAD_DEPTH]++;
      }
      if (g_expected_tid[kind] != 0 && buffer->thread_id != g_expected_tid[kind]) p.counts[kind][WRONG_THREAD]++;
      break;
    }
    case STACK_TRACE_FAILED:  p.counts[kind][FAILED]++;  break;
    case STACK_TRACE_DROPPED: p.counts[kind][DROPPED]++; break;
    default: abort(); // not complete: must not happen
  }
  if (buffer->overflow != nullptr) {
    p.counts[kind][OVERFLOWED]++;
    p.jvmti->Deallocate((unsigned char*)buffer->overflow);
    buffer->overflow = nullptr;
  }
  p.slot_state[i] = SLOT_FREE;
}

static void drain(Profiler& p) {
  if (p.use_list) {
    StackTraceBuffer* buffer = __atomic_exchange_n(&p.completed, (StackTraceBuffer*)nullptr, __ATOMIC_ACQ_REL);
    while (buffer != nullptr) {
      StackTraceBuffer* next = buffer->next;
      process(p, buffer);
      buffer = next;
    }
  } else {
    for (int i = 0; i < POOL_SIZE; i++) {
      if (p.slot_state[i].load(std::memory_order_acquire) == SLOT_REQUESTED &&
          __atomic_load_n(&p.buffers[i].status, __ATOMIC_ACQUIRE) != STACK_TRACE_PENDING) {
        process(p, &p.buffers[i]);
      }
    }
  }
}

static void drain_all() {
  for (int i = 0; i < PROFILERS; i++) {
    if (g_profilers[i].active) {
      drain(g_profilers[i]);
    }
  }
}

static void* drain_loop(void*) {
  JNIEnv* env = nullptr;
  g_vm->AttachCurrentThreadAsDaemon((void**)&env, nullptr);
  while (!g_drain_stop) {
    drain_all();
    usleep(1000);
  }
  drain_all();
  g_vm->DetachCurrentThread();
  return nullptr;
}

static void signal_handler(int, siginfo_t*, void* ucontext) {
  request(g_profilers[0], nullptr, ucontext, KIND_SIGNAL);
}

static jvmtiExtensionFunction find_extension(jvmtiEnv* jvmti, const char* id) {
  jint count = 0;
  jvmtiExtensionFunctionInfo* functions = nullptr;
  if (jvmti->GetExtensionFunctions(&count, &functions) != JVMTI_ERROR_NONE) {
    return nullptr;
  }
  jvmtiExtensionFunction result = nullptr;
  for (jint i = 0; i < count; i++) {
    if (strcmp(functions[i].id, id) == 0) {
      result = functions[i].func;
    }
  }
  return result;
}

static bool init_profiler(Profiler& p, bool use_list) {
  if (g_vm->GetEnv((void**)&p.jvmti, JVMTI_VERSION) != JNI_OK) {
    return false;
  }
  p.use_list = use_list;
  p.active = true;
  if (g_frame_capacity > 0) {
    p.frame_storage = (StackTraceFrame*)calloc((size_t)POOL_SIZE * g_frame_capacity, sizeof(StackTraceFrame));
    if (p.frame_storage == nullptr) {
      return false;
    }
  }
  // Enabling in the OnLoad phase takes effect once the VM is initialized.
  return g_enable(p.jvmti, use_list ? &p.completed : nullptr) == JVMTI_ERROR_NONE;
}

// Options: "vm" (VM allocates all frames), "inline" (agent storage only),
// "hybrid" (small agent storage, VM overflow) or "poll" (hybrid without a
// completion list). The second profiler always uses hybrid storage with a list.
JNIEXPORT jint JNICALL Agent_OnLoad(JavaVM* jvm, char* options, void*) {
  g_vm = jvm;
  const char* mode = options != nullptr ? options : "hybrid";
  bool use_list = true;
  if (strcmp(mode, "vm") == 0) {
    g_frame_capacity = 0;  g_max_depth = 2048;
  } else if (strcmp(mode, "inline") == 0) {
    g_frame_capacity = 64; g_max_depth = 64;
  } else {
    g_frame_capacity = 16; g_max_depth = 2048;
    use_list = strcmp(mode, "poll") != 0;
  }
  jvmtiEnv* jvmti = nullptr;
  if (jvm->GetEnv((void**)&jvmti, JVMTI_VERSION) != JNI_OK) {
    return JNI_ERR;
  }
  g_request = find_extension(jvmti, "com.sun.hotspot.functions.RequestStackTrace");
  g_enable = find_extension(jvmti, "com.sun.hotspot.functions.EnableRequestStackTrace");
  g_disable = find_extension(jvmti, "com.sun.hotspot.functions.DisableRequestStackTrace");
  if (g_request == nullptr || g_enable == nullptr || g_disable == nullptr) {
    return JNI_ERR;
  }
  if (!init_profiler(g_profilers[0], use_list) || !init_profiler(g_profilers[1], true)) {
    return JNI_ERR;
  }
  struct sigaction sa;
  memset(&sa, 0, sizeof(sa));
  sa.sa_sigaction = signal_handler;
  sa.sa_flags = SA_SIGINFO | SA_RESTART;
  sigemptyset(&sa.sa_mask);
  return sigaction(SIGUSR1, &sa, nullptr) == 0 ? JNI_OK : JNI_ERR;
}

extern "C" {

JNIEXPORT void JNICALL
Java_TestRequestStackTraceBuffers_startDrainer(JNIEnv*, jclass) {
  pthread_create(&g_drainer, nullptr, drain_loop, nullptr);
}

JNIEXPORT void JNICALL
Java_TestRequestStackTraceBuffers_stopDrainer(JNIEnv*, jclass) {
  g_drain_stop = true;
  pthread_join(g_drainer, nullptr);
}

JNIEXPORT void JNICALL
Java_TestRequestStackTraceBuffers_requestCurrent(JNIEnv*, jclass, jint n, jlong tid) {
  g_expected_tid[KIND_JNI] = tid;
  for (jint i = 0; i < n; i++) {
    request(g_profilers[0], nullptr, nullptr, KIND_JNI);
    usleep(1000);
  }
}

JNIEXPORT void JNICALL
Java_TestRequestStackTraceBuffers_registerWorker(JNIEnv*, jclass, jlong tid) {
  g_worker = pthread_self();
  g_expected_tid[KIND_SIGNAL] = tid;
  g_expected_tid[KIND_FOREIGN] = tid;
}

JNIEXPORT void JNICALL
Java_TestRequestStackTraceBuffers_signalWorker(JNIEnv*, jclass, jint n) {
  for (jint i = 0; i < n; i++) {
    pthread_kill(g_worker, SIGUSR1);
    usleep(1000);
  }
}

JNIEXPORT void JNICALL
Java_TestRequestStackTraceBuffers_requestOther(JNIEnv*, jclass, jint profiler, jthread thread, jint n, jint kind) {
  for (jint i = 0; i < n; i++) {
    request(g_profilers[profiler], thread, nullptr, kind);
    usleep(1000);
  }
}

static void* attach_request_detach(void* arg) {
  const int n = (int)(intptr_t)arg;
  JNIEnv* env = nullptr;
  if (g_vm->AttachCurrentThread((void**)&env, nullptr) != JNI_OK) {
    return nullptr;
  }
  for (int i = 0; i < n; i++) {
    request(g_profilers[0], nullptr, nullptr, KIND_DETACH);
  }
  // Detach right away, typically with the requests still queued.
  g_vm->DetachCurrentThread();
  return nullptr;
}

JNIEXPORT void JNICALL
Java_TestRequestStackTraceBuffers_requestFromDetachingThread(JNIEnv*, jclass, jint n) {
  pthread_t t;
  if (pthread_create(&t, nullptr, attach_request_detach, (void*)(intptr_t)n) == 0) {
    pthread_join(t, nullptr);
  }
}

JNIEXPORT void JNICALL
Java_TestRequestStackTraceBuffers_requestWithDepth(JNIEnv*, jclass, jthread thread, jint depth, jint n) {
  g_depth_override = depth;
  for (jint i = 0; i < n; i++) {
    request(g_profilers[0], thread, nullptr, KIND_DEPTH);
    usleep(1000);
  }
  g_depth_override = -1;
}

JNIEXPORT jint JNICALL
Java_TestRequestStackTraceBuffers_disable(JNIEnv*, jclass, jint profiler) {
  return g_disable(g_profilers[profiler].jvmti);
}

JNIEXPORT jint JNICALL
Java_TestRequestStackTraceBuffers_dispose(JNIEnv*, jclass, jint profiler) {
  return g_profilers[profiler].jvmti->DisposeEnvironment();
}

JNIEXPORT jint JNICALL
Java_TestRequestStackTraceBuffers_requestOnce(JNIEnv*, jclass, jint profiler) {
  Profiler& p = g_profilers[profiler];
  StackTraceBuffer* buffer = claim(p, KIND_JNI);
  jint rc = g_request(p.jvmti, nullptr, nullptr, buffer);
  if (rc == JVMTI_ERROR_NONE) {
    p.counts[KIND_JNI][ACCEPTED]++;
  } else {
    p.slot_state[index_of(p, buffer)] = SLOT_FREE;
  }
  return rc;
}

JNIEXPORT void JNICALL
Java_TestRequestStackTraceBuffers_drainNow(JNIEnv*, jclass) {
  drain_all();
}

JNIEXPORT jint JNICALL
Java_TestRequestStackTraceBuffers_outstanding(JNIEnv*, jclass, jint profiler) {
  int n = 0;
  for (int i = 0; i < POOL_SIZE; i++) {
    n += g_profilers[profiler].slot_state[i] != SLOT_FREE ? 1 : 0;
  }
  return n;
}

JNIEXPORT jint JNICALL
Java_TestRequestStackTraceBuffers_count(JNIEnv*, jclass, jint profiler, jint kind, jint counter) {
  return g_profilers[profiler].counts[kind][counter].load();
}

}

#endif // !_WIN32
