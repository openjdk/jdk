/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
#include <jvmti_common.h>
#include <string.h>

namespace {

jvmtiEnv *jvmti = nullptr;

const int TAG_START = 100;

struct RefCounters {
  jint testClassCount;
  jint *count;
  jlong *threadId;

  RefCounters(): testClassCount(0), count(nullptr) {}

  void* alloc(JNIEnv* env, jlong size) {
    unsigned char* ptr;
    jvmtiError err = jvmti->Allocate(size, &ptr);
    if (err != JVMTI_ERROR_NONE) {
      env->FatalError("jvmti->Allocate failed");
    }
    memset(ptr, 0, size);
    return ptr;
  }

  void init(JNIEnv* env, jint testClassCount) {
    this->testClassCount = testClassCount;
    count = (jint*)alloc(env, sizeof(count[0]) *  testClassCount);
    threadId = (jlong*)alloc(env, sizeof(threadId[0]) *  testClassCount);
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
      refCounters.threadId[index] = stackInfo->thread_id;
      LOG("Stack local: index = %d, threadId = %d\n",
          (int)index, (int)stackInfo->thread_id);
      if (refCounters.count[index] > 1) {
        LOG("ERROR: count > 1: %d\n", (int)refCounters.count[index]);
      }
    }
    break;
    case JVMTI_HEAP_REFERENCE_JNI_LOCAL: {
      jvmtiHeapReferenceInfoJniLocal *jniInfo = (jvmtiHeapReferenceInfoJniLocal *)reference_info;
      refCounters.count[index]++;
      refCounters.threadId[index] = jniInfo->thread_id;
      LOG("JNI local: index = %d, threadId = %d\n",
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
  if (vm->GetEnv(reinterpret_cast<void **>(&jvmti), JVMTI_VERSION) != JNI_OK || jvmti == nullptr) {
    LOG("Could not initialize JVMTI\n");
    return JNI_ERR;
  }
  jvmtiCapabilities capabilities;
  memset(&capabilities, 0, sizeof(capabilities));
  capabilities.can_tag_objects = 1;
  //capabilities.can_support_virtual_threads = 1;
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
  jsize classesCount = env->GetArrayLength(classes);
  for (int i=0; i<classesCount; i++) {
    jvmti->SetTag(env->GetObjectArrayElement(classes, i), TAG_START + i);
  }
  refCounters.init(env, classesCount);
  jvmtiHeapCallbacks heapCallBacks;
  memset(&heapCallBacks, 0, sizeof(jvmtiHeapCallbacks));
  heapCallBacks.heap_reference_callback = HeapReferenceCallback;
  jvmtiError err = jvmti->FollowReferences(0, nullptr, nullptr, &heapCallBacks, nullptr);
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
  return refCounters.threadId[index];
}

static void printtCreatedClass(JNIEnv* env, jclass cls) {
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

extern "C" JNIEXPORT void JNICALL
Java_VThreadStackRefTest_createObjAndCallback(JNIEnv* env, jclass clazz, jclass cls, jobject callback) {
  jobject jobj = env->AllocObject(cls);
  printtCreatedClass(env, cls);

  jclass callbackClass = env->GetObjectClass(callback);
  jmethodID mid = env->GetMethodID(callbackClass, "run", "()V");
  if (mid == nullptr) {
    env->FatalError("cannot get run method");
    return;
  }
  env->CallVoidMethod(callback, mid);
}

static volatile bool timeToExit = false;

extern "C" JNIEXPORT void JNICALL
Java_VThreadStackRefTest_createObjAndWait(JNIEnv* env, jclass clazz, jclass cls) {
  jobject jobj = env->AllocObject(cls);
  printtCreatedClass(env, cls);

  // Notify main thread that we are ready
  jfieldID fid = env->GetStaticFieldID(clazz, "mountedVthreadReady", "Z");
  if (fid == nullptr) {
    env->FatalError("cannot get mountedVthreadReady field");
    return;
  }
  env->SetStaticBooleanField(clazz, fid, JNI_TRUE);

  while (!timeToExit) {
    sleep_ms(100);
  }
}

extern "C" JNIEXPORT void JNICALL
Java_VThreadStackRefTest_endWait(JNIEnv* env, jclass clazz) {
  timeToExit = true;
}
