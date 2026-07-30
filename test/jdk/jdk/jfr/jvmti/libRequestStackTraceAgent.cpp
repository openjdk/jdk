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

#include <jvmti.h>

#include <string.h>

#ifndef _WIN32
#include <atomic>
#include <pthread.h>
#include <signal.h>
#endif

extern "C" {

typedef jvmtiError (JNICALL *RequestJFRStackTraceFn)(jvmtiEnv*, jthread, void*, jlong);

static jvmtiEnv*              g_jvmti              = nullptr;
static RequestJFRStackTraceFn g_request_stacktrace = nullptr;

#ifndef _WIN32
// Per-call slot used by the SIGUSR1 handler to communicate userData in and
// the JVMTI return code out. The signal is delivered synchronously to the
// thread that raised it on Linux, so a single global is safe under the
// single-threaded usage pattern of the helpers below.
static std::atomic<jlong>      g_signal_user_data{0};
static std::atomic<jvmtiError> g_signal_result{JVMTI_ERROR_NONE};

static void signal_handler(int /*sig*/, siginfo_t* /*info*/, void* ucontext) {
  if (g_request_stacktrace == nullptr) {
    g_signal_result.store(JVMTI_ERROR_NOT_AVAILABLE);
    return;
  }
  const jlong user_data = g_signal_user_data.load();
  const jvmtiError rc = g_request_stacktrace(g_jvmti, nullptr, ucontext, user_data);
  g_signal_result.store(rc);
}
#endif // !_WIN32

static jint resolve_request_stacktrace() {
  jint count = 0;
  jvmtiExtensionFunctionInfo* funcs = nullptr;
  if (g_jvmti->GetExtensionFunctions(&count, &funcs) != JVMTI_ERROR_NONE) {
    return JNI_ERR;
  }
  for (jint i = 0; i < count; i++) {
    if (strcmp(funcs[i].id, "com.sun.hotspot.functions.RequestJFRStackTrace") == 0) {
      g_request_stacktrace = (RequestJFRStackTraceFn) funcs[i].func;
    }
    g_jvmti->Deallocate((unsigned char*) funcs[i].id);
    g_jvmti->Deallocate((unsigned char*) funcs[i].short_description);
    for (jint p = 0; p < funcs[i].param_count; p++) {
      g_jvmti->Deallocate((unsigned char*) funcs[i].params[p].name);
    }
    g_jvmti->Deallocate((unsigned char*) funcs[i].params);
    g_jvmti->Deallocate((unsigned char*) funcs[i].errors);
  }
  g_jvmti->Deallocate((unsigned char*) funcs);
  return g_request_stacktrace != nullptr ? JNI_OK : JNI_ERR;
}

#ifndef _WIN32
static jint install_signal_handler() {
  struct sigaction sa;
  memset(&sa, 0, sizeof(sa));
  sa.sa_sigaction = signal_handler;
  sa.sa_flags = SA_SIGINFO | SA_RESTART;
  sigemptyset(&sa.sa_mask);
  return sigaction(SIGUSR1, &sa, nullptr) == 0 ? JNI_OK : JNI_ERR;
}
#endif // !_WIN32

JNIEXPORT jint JNICALL
Agent_OnLoad(JavaVM* vm, char* /*options*/, void* /*reserved*/) {
  if (vm->GetEnv((void**) &g_jvmti, JVMTI_VERSION_1_2) != JNI_OK) {
    return JNI_ERR;
  }
  if (resolve_request_stacktrace() != JNI_OK) {
    return JNI_ERR;
  }
#ifndef _WIN32
  if (install_signal_handler() != JNI_OK) {
    return JNI_ERR;
  }
#endif
  return JNI_OK;
}

JNIEXPORT jint JNICALL
Java_jdk_jfr_jvmti_RequestStackTraceHelper_requestStackTrace
  (JNIEnv* /*env*/, jclass /*cls*/, jlong user_data) {
  if (g_request_stacktrace == nullptr) {
    return JVMTI_ERROR_NOT_AVAILABLE;
  }
  return (jint) g_request_stacktrace(g_jvmti, nullptr, nullptr, user_data);
}

JNIEXPORT jint JNICALL
Java_jdk_jfr_jvmti_RequestStackTraceHelper_requestStackTraceWithThread
  (JNIEnv* /*env*/, jclass /*cls*/, jthread thread, jlong user_data) {
  if (g_request_stacktrace == nullptr) {
    return JVMTI_ERROR_NOT_AVAILABLE;
  }
  return (jint) g_request_stacktrace(g_jvmti, thread, nullptr, user_data);
}

JNIEXPORT jint JNICALL
Java_jdk_jfr_jvmti_RequestStackTraceHelper_requestStackTraceFromSignalHandler
  (JNIEnv* /*env*/, jclass /*cls*/, jlong user_data) {
#ifdef _WIN32
  (void) user_data;
  return JVMTI_ERROR_NOT_AVAILABLE;
#else
  if (g_request_stacktrace == nullptr) {
    return JVMTI_ERROR_NOT_AVAILABLE;
  }
  g_signal_user_data.store(user_data);
  g_signal_result.store((jvmtiError) -1);
  if (pthread_kill(pthread_self(), SIGUSR1) != 0) {
    return JNI_ERR;
  }
  // Self-targeted SIGUSR1 is delivered synchronously before pthread_kill
  // returns, so g_signal_result is already populated.
  return (jint) g_signal_result.load();
#endif
}

} // extern "C"
