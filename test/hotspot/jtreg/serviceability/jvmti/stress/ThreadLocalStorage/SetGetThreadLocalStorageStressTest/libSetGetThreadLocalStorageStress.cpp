/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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

#include <string.h>
#include "jvmti.h"
#include "jvmti_common.hpp"
#include "jvmti_thread.hpp"


/* constant names */
#define THREAD_NAME     "TestedThread"

/* constants */
#define STORAGE_DATA_SIZE       1024
#define THREAD_NAME_LENGTH      100

/* storage structure */
typedef struct _StorageStructure {
  void *self_pointer;
  char data[STORAGE_DATA_SIZE];
} StorageStructure;

StorageStructure* check_tls(jvmtiEnv * jvmti, JNIEnv * jni, jthread thread, const char* source) {
  jvmtiThreadInfo thread_info;
  check_jvmti_status(jni, jvmti->GetThreadInfo(thread, &thread_info), "Error in GetThreadInfo");

  StorageStructure *storage;

  jvmtiError err = jvmti->GetThreadLocalStorage(thread, (void **) &storage);
  if (err == JVMTI_ERROR_THREAD_NOT_ALIVE) {
    return nullptr;
  }
  check_jvmti_status(jni, err, "Error in GetThreadLocalStorage");
  LOG("Check %s with %p in %s\n", thread_info.name, storage, source);


  if (storage == nullptr) {
    // Might be not set
    return nullptr;
  }

  if (storage->self_pointer != storage || (strcmp(thread_info.name, storage->data) != 0)) {
    LOG("Unexpected value in storage storage=%p, the self_pointer=%p, data (owner thread name): %s\n",
           storage, storage->self_pointer, storage->data);
    print_thread_info(jvmti, jni, thread);
    jni->FatalError("Incorrect value in storage.");
  }
  return storage;
}

void check_delete_tls(jvmtiEnv * jvmti, JNIEnv * jni, jthread thread, const char* source) {
  StorageStructure *storage = check_tls(jvmti, jni, thread, source);

  if (storage == nullptr) {
    return;
  }

  check_jvmti_status(jni, jvmti->Deallocate((unsigned char *)storage), "Deallocation failed.");
  jvmtiError err = jvmti->SetThreadLocalStorage(thread, nullptr);
  if (err == JVMTI_ERROR_THREAD_NOT_ALIVE) {
    return;
  }
  check_jvmti_status(jni, err, "Error in SetThreadLocalStorage");
}


void check_reset_tls(jvmtiEnv * jvmti, JNIEnv * jni, jthread thread, const char* source) {
  check_delete_tls(jvmti, jni, thread, source);
  jvmtiThreadInfo thread_info;
  check_jvmti_status(jni, jvmti->GetThreadInfo(thread, &thread_info), "Error in GetThreadInfo");

  unsigned char *tmp;
  check_jvmti_status(jni, jvmti->Allocate(sizeof(StorageStructure), &tmp), "Allocation failed.");

  StorageStructure* storage = (StorageStructure *)tmp;

  LOG("Init %s with %p in %s\n", thread_info.name, storage, source);


  // Fill data
  storage->self_pointer = storage;
  strncpy(storage->data, thread_info.name, THREAD_NAME_LENGTH);
  jvmtiError err = jvmti->SetThreadLocalStorage(thread, (void *) storage);
  if (err == JVMTI_ERROR_THREAD_NOT_ALIVE) {
    return;
  }
  check_jvmti_status(jni, err, "Error in SetThreadLocalStorage");

  check_tls(jvmti, jni, thread, "check_reset_tls");
}

jrawMonitorID monitor;
int is_vm_running = false;

/** Agent algorithm. */
static void JNICALL
agentProc(jvmtiEnv * jvmti, JNIEnv * jni, void * arg) {
  /* scaffold objects */
  static jlong timeout = 0;
  LOG("Wait for thread to start\n");
  if (!agent_wait_for_sync(timeout))
    return;
  if (!agent_resume_sync())
    return;
  LOG("Started.....\n");

  while (true) {
    jthread *threads = nullptr;
    jint count = 0;

    sleep_ms(10);

    RawMonitorLocker rml(jvmti, jni, monitor);
    if (!is_vm_running) {
      return;
    }
    check_jvmti_status(jni, jvmti->GetAllThreads(&count, &threads), "Error in GetAllThreads");
    for (int i = 0; i < count; i++) {
      jthread testedThread = nullptr;
      jvmtiError err;

      err = GetVirtualThread(jvmti, jni, threads[i], &testedThread);
      if (err == JVMTI_ERROR_THREAD_NOT_ALIVE) {
        continue;
      }
      check_jvmti_status(jni, err,  "Error in GetVirtualThread");

      if (testedThread == nullptr) {
        testedThread = threads[i];
        continue;
      }

      check_reset_tls(jvmti, jni, testedThread, "agentThread");

    }
    check_jvmti_status(jni, jvmti->Deallocate((unsigned char *) threads), "Error Deallocating memory.");
  }

}

/** callback functions **/
void JNICALL VMInit(jvmtiEnv *jvmti, JNIEnv *jni, jthread thread) {
  RawMonitorLocker rml(jvmti, jni, monitor);
  LOG("Starting ...\n");
  is_vm_running = true;
}

void JNICALL VMDeath(jvmtiEnv *jvmti, JNIEnv *jni) {
  RawMonitorLocker rml(jvmti, jni, monitor);
  LOG("Exiting ...\n");
  is_vm_running = false;
}

void JNICALL ThreadStart(jvmtiEnv *jvmti, JNIEnv *jni, jthread thread) {
  RawMonitorLocker rml(jvmti, jni, monitor);
  if (is_vm_running) {
    check_reset_tls(jvmti, jni, thread, "ThreadStart");
  }
}

void JNICALL ThreadEnd(jvmtiEnv *jvmti, JNIEnv *jni, jthread thread) {
  RawMonitorLocker rml(jvmti, jni, monitor);
  if (is_vm_running) {
    check_reset_tls(jvmti, jni, thread, "ThreadEnd");
  }
}

static void JNICALL
VirtualThreadStart(jvmtiEnv *jvmti, JNIEnv *jni, jthread vthread) {
  RawMonitorLocker rml(jvmti, jni, monitor);
  if (is_vm_running) {
    check_reset_tls(jvmti, jni, vthread, "VirtualThreadStart");
  }
}

static void JNICALL
VirtualThreadEnd(jvmtiEnv *jvmti, JNIEnv *jni, jthread vthread) {
  RawMonitorLocker rml(jvmti, jni, monitor);
  if (is_vm_running) {
    check_reset_tls(jvmti, jni, vthread, "VirtualThreadEnd");
  }
}

/* ============================================================================= */

JNIEXPORT jint JNICALL
Agent_OnLoad(JavaVM *jvm, char *options, void *reserved) {
  jvmtiEnv * jvmti = nullptr;

  jvmtiEventCallbacks callbacks;
  jvmtiCapabilities caps;
  jvmtiError err;
  jint res;

  res = jvm->GetEnv((void **) &jvmti, JVMTI_VERSION_1_1);
  if (res != JNI_OK || jvmti == nullptr) {
    LOG("Wrong result of a valid call to GetEnv!\n");
    return JNI_ERR;
  }

  monitor = create_raw_monitor(jvmti, "Monitor");

  /* add capability to generate compiled method events */
  memset(&caps, 0, sizeof(jvmtiCapabilities));
  caps.can_support_virtual_threads = 1;
  caps.can_generate_method_entry_events = 1;
  err = jvmti->AddCapabilities(&caps);
  if (err != JVMTI_ERROR_NONE) {
    LOG("(AddCapabilities) unexpected error: %s (%d)\n", TranslateError(err), err);
    return JNI_ERR;
  }

  err = jvmti->GetCapabilities(&caps);
  if (err != JVMTI_ERROR_NONE) {
    LOG("(GetCapabilities) unexpected error: %s (%d)\n", TranslateError(err), err);
    return JNI_ERR;
  }

  /* set event callback */
  LOG("setting event callbacks ...\n");
  (void) memset(&callbacks, 0, sizeof(callbacks));
  callbacks.VMInit = &VMInit;
  callbacks.VMDeath = &VMDeath;
  callbacks.ThreadStart = &ThreadStart;
  callbacks.ThreadEnd = &ThreadEnd;
  callbacks.VirtualThreadStart = &VirtualThreadStart;
  callbacks.VirtualThreadEnd = &VirtualThreadEnd;

  err = jvmti->SetEventCallbacks(&callbacks, sizeof(callbacks));
  if (err != JVMTI_ERROR_NONE) {
    return JNI_ERR;
  }
  jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_VM_INIT, nullptr);
  jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_VM_DEATH, nullptr);
  jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_THREAD_START, nullptr);
  jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_THREAD_END, nullptr);
  jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_VIRTUAL_THREAD_START, nullptr);
  jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_VIRTUAL_THREAD_END, nullptr);

  err = init_agent_data(jvmti, &agent_data);
  if (err != JVMTI_ERROR_NONE) {
    return JNI_ERR;
  }

  if (set_agent_proc(agentProc, nullptr) != JNI_TRUE) {
    return JNI_ERR;
  }
  return JNI_OK;
}
