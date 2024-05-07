/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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

#include <jni.h>
#include <jvmti.h>
#include <jvmti_common.hpp>
#include <atomic>

static jvmtiEnv *jvmti = nullptr;
static jint error_count = 0;

extern "C" JNIEXPORT jint JNICALL
Agent_OnLoad(JavaVM *vm, char *options, void *reserved) {
  if (vm->GetEnv((void **)&jvmti, JVMTI_VERSION) != JNI_OK) {
    LOG("Could not initialize JVMTI\n");
    return JNI_ERR;
  }
  jvmtiCapabilities caps;
  memset(&caps, 0, sizeof(caps));
  caps.can_support_virtual_threads = 1;
  caps.can_suspend = 1;
  caps.can_signal_thread = 1;
  jvmtiError err = jvmti->AddCapabilities(&caps);
  if (err != JVMTI_ERROR_NONE) {
    LOG("JVMTI AddCapabilities error: %d\n", err);
    return JNI_ERR;
  }

  return JNI_OK;
}


extern "C" JNIEXPORT jboolean JNICALL
Java_GetThreadStateMountedTest_trySuspendInWaitingState(JNIEnv* jni, jclass clazz, jthread vthread) {
  const int max_retries = 10;
  for (int i = 0; i < max_retries; i++) {
    // wait a bit
    sleep_ms(100);

    // suspend the thread
    LOG("suspend vthread (%d)\n", i);
    suspend_thread(jvmti, jni, vthread);

    jint state = get_thread_state(jvmti, jni, vthread);
    if ((state & JVMTI_THREAD_STATE_WAITING) != 0) {
      LOG("suspended in WAITING state\n");
      return JNI_TRUE;
    }
    LOG("suspended vthread is not waiting: state = %x (%s)\n", state, TranslateState(state));
    LOG("resume vthread\n");
    resume_thread(jvmti, jni, vthread);
  }
  LOG("ERROR: failed to suspend in WAITING state in %d tries\n", max_retries);
  return JNI_FALSE;

}

static void verify_thread_state(const char *name, JNIEnv* jni,
  jthread thread, jint expected_strong, jint expected_weak)
{
  jint state = get_thread_state(jvmti, jni, thread);
  LOG("%s state(%x): %s\n", name, state, TranslateState(state));
  bool failed = false;
  // check 1: all expected_strong bits are set
  jint actual_strong = state & expected_strong;
  if (actual_strong != expected_strong) {
    failed = true;
    jint missed = expected_strong - actual_strong;
    LOG("  ERROR: some mandatory bits are not set (%x): %s\n",
        missed, TranslateState(missed));
  }
  // check 2: no bits other than (expected_strong | expected_weak) are set
  jint actual_full = state & (expected_strong | expected_weak);
  if (actual_full != state) {
    failed = true;
    jint unexpected = state - actual_full;
    LOG("  ERROR: some unexpected bits are set (%x): %s\n",
        unexpected, TranslateState(unexpected));
  }
  // check 3: expected_weak checks
  if (expected_weak != 0) {
    // check 3a: at least 1 bit from expected_weak is set
    if ((state & expected_weak) == 0) {
      failed = true;
      LOG("  ERROR: no expected 'weak' bits are set\n");
    }
    // check 3b: not all expected_weak bits are set
    if ((state & expected_weak) == expected_weak) {
      failed = true;
      LOG("  ERROR: all expected 'weak' bits are set\n");
    }
  }

  if (failed) {
    LOG("  expected 'strong' state (%x): %s\n", expected_strong, TranslateState(expected_strong));
    LOG("  expected 'weak' state (%x): %s\n", expected_weak, TranslateState(expected_weak));
    error_count++;
  }
}

extern "C" JNIEXPORT void JNICALL
Java_GetThreadStateMountedTest_testThread(
  JNIEnv* jni, jclass clazz, jthread vthread, jboolean is_vthread_suspended,
  jboolean test_interrupt,
  jint expected_strong, jint expected_weak)
{
  jint exp_ct_state = JVMTI_THREAD_STATE_ALIVE
                      | JVMTI_THREAD_STATE_WAITING
                      | JVMTI_THREAD_STATE_WAITING_INDEFINITELY;
  jint exp_vt_state = expected_strong
                      | JVMTI_THREAD_STATE_ALIVE;

  jthread cthread = get_carrier_thread(jvmti, jni, vthread);

  verify_thread_state("cthread", jni, cthread,
                      exp_ct_state, 0);
  verify_thread_state("vthread", jni, vthread,
                      exp_vt_state | (is_vthread_suspended ? JVMTI_THREAD_STATE_SUSPENDED : 0),
                      expected_weak);

  // suspend ctread and verify
  LOG("suspend cthread\n");
  suspend_thread(jvmti, jni, cthread);
  verify_thread_state("cthread", jni, cthread,
                      exp_ct_state | JVMTI_THREAD_STATE_SUSPENDED, 0);
  verify_thread_state("vthread", jni, vthread,
                      exp_vt_state | (is_vthread_suspended ? JVMTI_THREAD_STATE_SUSPENDED : 0),
                      expected_weak);

  // suspend vthread and verify
  if (!is_vthread_suspended) {
    LOG("suspend vthread\n");
    suspend_thread(jvmti, jni, vthread);
    verify_thread_state("cthread", jni, cthread,
                        exp_ct_state | JVMTI_THREAD_STATE_SUSPENDED, 0);
    verify_thread_state("vthread", jni, vthread,
                        exp_vt_state | JVMTI_THREAD_STATE_SUSPENDED, expected_weak);
  }

  // resume cthread and verify
  LOG("resume cthread\n");
  resume_thread(jvmti, jni, cthread);
  verify_thread_state("cthread", jni, cthread,
                      exp_ct_state, 0);
  verify_thread_state("vthread", jni, vthread,
                      exp_vt_state | JVMTI_THREAD_STATE_SUSPENDED, expected_weak);

  if (test_interrupt) {
    // interrupt vthread (while it's suspended)
    LOG("interrupt vthread\n");
    check_jvmti_status(jni, jvmti->InterruptThread(vthread), "error in JVMTI InterruptThread");
    verify_thread_state("cthread", jni, cthread,
                        exp_ct_state, 0);
    verify_thread_state("vthread", jni, vthread,
                        exp_vt_state | JVMTI_THREAD_STATE_SUSPENDED | JVMTI_THREAD_STATE_INTERRUPTED,
                        expected_weak);
  }

  // resume vthread
  LOG("resume vthread\n");
  resume_thread(jvmti, jni, vthread);

  // don't verify thread state after InterruptThread and ResumeThread
}

extern "C" JNIEXPORT int JNICALL
Java_GetThreadStateMountedTest_getErrorCount(JNIEnv* jni, jclass clazz) {
  return error_count;
}


static std::atomic<bool> time_to_exit(false);

extern "C" JNIEXPORT void JNICALL
Java_GetThreadStateMountedTest_waitInNative(JNIEnv* jni, jclass clazz) {
  // Notify main thread that we are ready
  jfieldID fid = jni->GetStaticFieldID(clazz, "waitInNativeReady", "Z");
  if (fid == nullptr) {
    jni->FatalError("cannot get waitInNativeReady field");
    return;
  }
  jni->SetStaticBooleanField(clazz, fid, JNI_TRUE);

  while (!time_to_exit) {
    sleep_ms(100);
  }
}

extern "C" JNIEXPORT void JNICALL
Java_GetThreadStateMountedTest_endWait(JNIEnv* jni, jclass clazz) {
  time_to_exit = true;
}
