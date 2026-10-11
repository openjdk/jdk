/*
 * Copyright (c) 2026, Justus Garbe. All rights reserved.
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

#include <atomic>
#include "jvmti.h"
#include "jvmti_common.hpp"

extern "C" {

static jvmtiEnv* jvmti = nullptr;
static jmethodID target = nullptr;
static std::atomic<int> hits{0};

static void JNICALL
Breakpoint(jvmtiEnv* env, JNIEnv* jni, jthread thread, jmethodID method, jlocation location) {
  hits++;
}

JNIEXPORT jint JNICALL
Agent_OnLoad(JavaVM* vm, char* options, void* reserved) {
  if (vm->GetEnv((void**)&jvmti, JVMTI_VERSION_1_0) != JNI_OK) {
    LOG("Agent_OnLoad: GetEnv failed");
    return JNI_ERR;
  }

  jvmtiCapabilities caps;
  memset(&caps, 0, sizeof(caps));
  caps.can_generate_breakpoint_events = 1;
  check_jvmti_error(jvmti->AddCapabilities(&caps), "AddCapabilities");

  jvmtiEventCallbacks callbacks;
  memset(&callbacks, 0, sizeof(callbacks));
  callbacks.Breakpoint = Breakpoint;
  check_jvmti_error(jvmti->SetEventCallbacks(&callbacks, sizeof(callbacks)),
                   "SetEventCallbacks");

  check_jvmti_error(jvmti->SetEventNotificationMode(JVMTI_ENABLE,
                                                    JVMTI_EVENT_BREAKPOINT,
                                                    nullptr),
                   "SetEventNotificationMode");

  return JNI_OK;
}

// Resolve the jmethodID while metaspace still has room: FromReflectedMethod can allocate,
// and the whole point of the test is to call setBreakpoint() once metaspace is exhausted.
JNIEXPORT void JNICALL
Java_BreakpointMetaspaceOOM_prepare(JNIEnv* env, jclass cls, jobject method) {
  target = env->FromReflectedMethod(method);
}

// Returns the raw JVMTI error code so the Java side can see exactly what SetBreakpoint
// reported under the out-of-memory condition.
JNIEXPORT jint JNICALL
Java_BreakpointMetaspaceOOM_setBreakpoint(JNIEnv* env, jclass cls) {
  return jvmti->SetBreakpoint(target, 0);
}

JNIEXPORT jint JNICALL
Java_BreakpointMetaspaceOOM_breakpointHits(JNIEnv* env, jclass cls) {
  return hits;
}

}
