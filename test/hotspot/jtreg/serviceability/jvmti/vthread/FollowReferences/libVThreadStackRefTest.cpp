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
#include <string.h>

namespace {

jvmtiEnv *jvmti = nullptr;

const int TAG_START = 100;

struct RefCounters {
  jint test_class_count;
  jint *count;
  jlong *thread_id;

  RefCounters(): test_class_count(0), count(nullptr) {}

  void* alloc(JNIEnv* env, jlong size) {
    unsigned char* ptr;
    jvmtiError err = jvmti->Allocate(size, &ptr);
    if (err != JVMTI_ERROR_NONE) {
      env->FatalError("jvmti->Allocate failed");
    }
    memset(ptr, 0, size);
    return ptr;
  }

  void init(JNIEnv* env, jint test_class_count) {
    this->test_class_count = test_class_count;
    count = (jint*)alloc(env, sizeof(count[0]) *  test_class_count);
    thread_id = (jlong*)alloc(env, sizeof(thread_id[0]) *  test_class_count);
  }
} refCounters;

}

/////////////////////////////////////////
// Agent functions
/////////////////////////////////////////
jint JNICALL
HeapReferenceCallback(jvmtiHeapReferenceKind reference_kind,
                      const jvmtiHeapReferenceInfo* reference_info,
                      jlong class_tag, jlong referrer_class_tag, jlong size,
                      jlong* tag_ptr, jlong* referrer_tag_ptr, jint length, void* user_data) {
  if (class_tag >= TAG_START) {
    jlong index = class_tag - TAG_START;
    switch (reference_kind) {
    case JVMTI_HEAP_REFERENCE_STACK_LOCAL: {
      jvmtiHeapReferenceInfoStackLocal *stackInfo = (jvmtiHeapReferenceInfoStackLocal *)reference_info;
      refCounters.count[index]++;
      refCounters.thread_id[index] = stackInfo->thread_id;
      LOG("Stack local: index = %d, thread_id = %d\n",
          (int)index, (int)stackInfo->thread_id);
      if (refCounters.count[index] > 1) {
        LOG("ERROR: count > 1: %d\n", (int)refCounters.count[index]);
      }
    }
    break;
    case JVMTI_HEAP_REFERENCE_JNI_LOCAL: {
      jvmtiHeapReferenceInfoJniLocal *jniInfo = (jvmtiHeapReferenceInfoJniLocal *)reference_info;
      refCounters.count[index]++;
      refCounters.thread_id[index] = jniInfo->thread_id;
      LOG("JNI local: index = %d, thread_id = %d\n",
          (int)index, (int)jniInfo->thread_id);
      if (refCounters.count[index] > 1) {
        LOG("ERROR: count > 1: %d\n", (int)refCounters.count[index]);
      }
    }
    break;
    default:
      // unexpected ref.kind
      LOG("ERROR: unexpected ref_kind for class %d: %d\n",
          (int)index, (int)reference_kind);
    }
  }
  return JVMTI_VISIT_OBJECTS;
}

extern "C" JNIEXPORT jint JNICALL
Agent_OnLoad(JavaVM *vm, char *options, void *reserved) {
  if (vm->GetEnv((void **)&jvmti, JVMTI_VERSION) != JNI_OK) {
    LOG("Could not initialize JVMTI\n");
    return JNI_ERR;
  }
  jvmtiCapabilities capabilities;
  memset(&capabilities, 0, sizeof(capabilities));
  capabilities.can_tag_objects = 1;
  jvmtiError err = jvmti->AddCapabilities(&capabilities);
  if (err != JVMTI_ERROR_NONE) {
    LOG("JVMTI AddCapabilities error: %d\n", err);
    return JNI_ERR;
  }

  return JNI_OK;
}


/////////////////////////////////////////
// Test native methods
/////////////////////////////////////////
extern "C" JNIEXPORT void JNICALL
Java_VThreadStackRefTest_test(JNIEnv* env, jclass clazz, jobjectArray classes) {
  jsize classes_count = env->GetArrayLength(classes);
  for (int i = 0; i < classes_count; i++) {
    jvmti->SetTag(env->GetObjectArrayElement(classes, i), TAG_START + i);
  }
  refCounters.init(env, classes_count);
  jvmtiHeapCallbacks callbacks;
  memset(&callbacks, 0, sizeof(jvmtiHeapCallbacks));
  callbacks.heap_reference_callback = HeapReferenceCallback;
  jvmtiError err = jvmti->FollowReferences(0, nullptr, nullptr, &callbacks, nullptr);
  if (err != JVMTI_ERROR_NONE) {
    LOG("JVMTI FollowReferences error: %d\n", err);
    env->FatalError("FollowReferences failed");
  }
}

extern "C" JNIEXPORT jint JNICALL
Java_VThreadStackRefTest_getRefCount(JNIEnv* env, jclass clazz, jint index) {
  return refCounters.count[index];
}

extern "C" JNIEXPORT jlong JNICALL
Java_VThreadStackRefTest_getRefThreadID(JNIEnv* env, jclass clazz, jint index) {
  return refCounters.thread_id[index];
}

static void print_created_class(JNIEnv* env, jclass cls) {
  jmethodID mid = env->GetMethodID(cls, "toString", "()Ljava/lang/String;");
  if (mid == nullptr) {
    env->FatalError("failed to get toString method");
    return;
  }
  jstring jstr = (jstring)env->CallObjectMethod(cls, mid);
  const char* str = env->GetStringUTFChars(jstr, 0);
  LOG("created %s\n", str);
  env->ReleaseStringUTFChars(jstr, str);
}

// Creates object of the the specified class (local JNI)
// and calls the provided callback.
extern "C" JNIEXPORT void JNICALL
Java_VThreadStackRefTest_createObjAndCallback(JNIEnv* env, jclass clazz, jclass cls, jobject callback) {
  jobject jobj = env->AllocObject(cls);
  print_created_class(env, cls);

  jclass callbackClass = env->GetObjectClass(callback);
  jmethodID mid = env->GetMethodID(callbackClass, "run", "()V");
  if (mid == nullptr) {
    env->FatalError("cannot get run method");
    return;
  }
  env->CallVoidMethod(callback, mid);
}

static std::atomic<bool> time_to_exit(false);

// Creates object of the the specified class (local JNI),
// sets mountedVthreadReady static field,
// and then waits until endWait() method is called.
extern "C" JNIEXPORT void JNICALL
Java_VThreadStackRefTest_createObjAndWait(JNIEnv* env, jclass clazz, jclass cls) {
  jobject jobj = env->AllocObject(cls);
  print_created_class(env, cls);

  // Notify main thread that we are ready
  jfieldID fid = env->GetStaticFieldID(clazz, "mountedVthreadReady", "Z");
  if (fid == nullptr) {
    env->FatalError("cannot get mountedVthreadReady field");
    return;
  }
  env->SetStaticBooleanField(clazz, fid, JNI_TRUE);

  while (!time_to_exit) {
    sleep_ms(100);
  }
}

// Signals createObjAndWait() to exit.
extern "C" JNIEXPORT void JNICALL
Java_VThreadStackRefTest_endWait(JNIEnv* env, jclass clazz) {
  time_to_exit = true;
}
