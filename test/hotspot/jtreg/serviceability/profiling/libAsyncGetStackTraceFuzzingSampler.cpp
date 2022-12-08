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

#include <assert.h>
#include <ctime>
#include <dlfcn.h>
#include <signal.h>
#include <stdio.h>
#include <stddef.h>
#include <stdlib.h>
#include <string.h>
#include <sys/types.h>
#include <sys/time.h>
#include <sys/ucontext.h>
#include <ucontext.h>
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

// AsyncGetStackTrace needs class loading events to be turned on!
static void JNICALL OnClassLoad(jvmtiEnv *jvmti, JNIEnv *jni_env,
                                jthread thread, jclass klass) {
}

static void JNICALL OnClassPrepare(jvmtiEnv *jvmti, JNIEnv *jni_env,
                                   jthread thread, jclass klass) {
  // We need to do this to "prime the pump" and get jmethodIDs primed.
  GetJMethodIDs(klass);
}

static SigAction installSignalHandler(int signo, SigAction action, SigHandler handler = NULL) {
    struct sigaction sa;
    struct sigaction oldsa;
    sigemptyset(&sa.sa_mask);

    if (handler != NULL) {
        sa.sa_handler = handler;
        sa.sa_flags = 0;
    } else {
        sa.sa_sigaction = action;
        sa.sa_flags = SA_SIGINFO | SA_RESTART;
    }

    sigaction(signo, &sa, &oldsa);
    return oldsa.sa_sigaction;
}

// max increase of sp
int sp_max_fuzz = 250;
// max increase of fp
int fp_max_fuzz = 250;

int sp_max_random_fuzz = 1000000;
int fp_max_random_fuzz = 1000000;

bool iterative = false;


void iterativeFuzzingAsyncGetStackTraceLike(ASGST_CallTrace *trace, int max_depth, int options, void* ucontext) {
  ucontext_t uc = *((ucontext_t *)ucontext);
  AsyncGetStackTrace(trace, max_depth, &uc, options);
  long long initial_sp = uc.uc_mcontext.gregs[REG_RSP];
  long long initial_fp = uc.uc_mcontext.gregs[REG_RBP];
  for (long i = 0; i < sp_max_fuzz; i++) {
    for (long j = 0; j < fp_max_fuzz; j++) {
      uc.uc_mcontext.gregs[REG_RSP] = initial_sp + i;
      uc.uc_mcontext.gregs[REG_RBP] = initial_fp + j;
      AsyncGetStackTrace(trace, max_depth, &uc, options);
    }
  }
}

void fuzzingAsyncGetStackTraceLike(ASGST_CallTrace *trace, int max_depth, int options, int sp_add, int fp_add, void* ucontext) {
  ucontext_t uc = *((ucontext_t *)ucontext);
  AsyncGetStackTrace(trace, max_depth, &uc, options);
  uc.uc_mcontext.gregs[REG_RSP] += sp_add;
  uc.uc_mcontext.gregs[REG_RBP] += fp_add;
  AsyncGetStackTrace(trace, max_depth, &uc, options);
}

static void signalHandler(int signo, siginfo_t* siginfo, void* ucontext) {
  const int MAX_DEPTH = 512;
  static ASGST_CallFrame frames[MAX_DEPTH];
  ASGST_CallTrace trace;
  trace.frames = frames;
  trace.frame_info = NULL;
  trace.num_frames = 0;
  printf("iterative: %d\n", iterative);
  if (iterative) {
    iterativeFuzzingAsyncGetStackTraceLike(&trace, MAX_DEPTH, ASGST_INCLUDE_C_FRAMES, ucontext);
  } else {
    fuzzingAsyncGetStackTraceLike(&trace, MAX_DEPTH, ASGST_INCLUDE_C_FRAMES, rand() % sp_max_random_fuzz, rand() % fp_max_random_fuzz, ucontext);
  }
}

static bool startITimerSampler(long interval_ns) {
  time_t sec = interval_ns / 1000000000;
  suseconds_t usec = (interval_ns % 1000000000) / 1000;
  struct itimerval tv = {{sec, usec}, {sec, usec}};

  installSignalHandler(SIGPROF, signalHandler);

  if (setitimer(ITIMER_PROF, &tv, NULL) != 0) {
    return false;
  }
  fprintf(stderr, "=== asgst sampler initialized ===\n");
  return true;
}

static void JNICALL OnVMInit(jvmtiEnv *jvmti, JNIEnv *jni_env, jthread thread) {
  jint class_count = 0;
  // Get any previously loaded classes that won't have gone through the
  // OnClassPrepare callback to prime the jmethods for AsyncGetStackTrace.
  JvmtiDeallocator<jclass*> classes;
  jvmtiError err = jvmti->GetLoadedClasses(&class_count, classes.get_addr());
  if (err != JVMTI_ERROR_NONE) {
    fprintf(stderr, "OnVMInit: Error in GetLoadedClasses: %d\n", err);
    return;
  }

  // Prime any class already loaded and try to get the jmethodIDs set up.
  jclass *classList = classes.get();
  for (int i = 0; i < class_count; ++i) {
    GetJMethodIDs(classList[i]);
  }

  startITimerSampler(1000); // 1us
}


extern "C" {

static
jint Agent_Initialize(JavaVM *jvm, char *options, void *reserved) {
  if (strcmp("iterative", options) == 0) {
    iterative = true;
  } else if (strcmp("random", options) == 0) {
    iterative = false;
  } else {
    fprintf(stderr, "Unknown option: %s", options);
    return JNI_ERR;
  }
  jint res = jvm->GetEnv((void **) &jvmti, JVMTI_VERSION);
  if (res != JNI_OK || jvmti == NULL) {
    fprintf(stderr, "Error: wrong result of a valid call to GetEnv!\n");
    return JNI_ERR;
  }

  jvmtiError err;
  jvmtiCapabilities caps;
  memset(&caps, 0, sizeof(caps));
  caps.can_get_line_numbers = 1;
  caps.can_get_source_file_name = 1;

  err = jvmti->AddCapabilities(&caps);
  if (err != JVMTI_ERROR_NONE) {
    fprintf(stderr, "AgentInitialize: Error in AddCapabilities: %d\n", err);
    return JNI_ERR;
  }

  jvmtiEventCallbacks callbacks;
  memset(&callbacks, 0, sizeof(callbacks));
  callbacks.ClassLoad = &OnClassLoad;
  callbacks.VMInit = &OnVMInit;
  callbacks.ClassPrepare = &OnClassPrepare;

  err = jvmti->SetEventCallbacks(&callbacks, sizeof(jvmtiEventCallbacks));
  if (err != JVMTI_ERROR_NONE) {
    fprintf(stderr, "AgentInitialize: Error in SetEventCallbacks: %d\n", err);
    return JNI_ERR;
  }

  err = jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_CLASS_LOAD, NULL);
  if (err != JVMTI_ERROR_NONE) {
    fprintf(stderr, "AgentInitialize: Error in SetEventNotificationMode for CLASS_LOAD: %d\n", err);
    return JNI_ERR;
  }

  err = jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_CLASS_PREPARE, NULL);
  if (err != JVMTI_ERROR_NONE) {
    fprintf(stderr,
            "AgentInitialize: Error in SetEventNotificationMode for CLASS_PREPARE: %d\n",
            err);
    return JNI_ERR;
  }

  err = jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_VM_INIT, NULL);
  if (err != JVMTI_ERROR_NONE) {
    fprintf(
        stderr, "AgentInitialize: Error in SetEventNotificationMode for VM_INIT: %d\n",
        err);
    return JNI_ERR;
  }

  return JNI_OK;
}

JNIEXPORT
jint JNICALL Agent_OnLoad(JavaVM *jvm, char *options, void *reserved) {
  return Agent_Initialize(jvm, options, reserved);
}

JNIEXPORT
jint JNICALL Agent_OnAttach(JavaVM *jvm, char *options, void *reserved) {
  return Agent_Initialize(jvm, options, reserved);
}

JNIEXPORT
jint JNICALL JNI_OnLoad(JavaVM *jvm, void *reserved) {
  return JNI_VERSION_1_8;
}

}
