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
#include <stdio.h>
#include <string.h>

static void _log(const char* format, ...) {
  va_list args;
  va_start(args, format);
  vprintf(format, args);
  va_end(args);
  fflush(0);
}

static jvmtiEnv* jvmti = nullptr;

static const char* testClassNames[] = {
    "java/util/Date",               // JDK class in CDS archive
    "java/lang/ProcessBuilder",     // JDK class not in CDS
    "MissedStackMapFrames"          // non-JDK class
};
static const int testClassCount = sizeof(testClassNames) / sizeof(testClassNames[0]);

struct SavedClassBytes {
  struct Buffer {
    unsigned char* bytes;
    jint len;

    Buffer() : bytes(nullptr), len(0) {}

    void save(const unsigned char *bytes, jint len) {
      jvmtiError err = jvmti->Allocate(len, &this->bytes);
      if (err != JVMTI_ERROR_NONE) {
          _log("ClassFileLoadHook: failed to allocate %ld bytes for saved class bytes: %d\n", len, err);
          return;
      }
      memcpy(this->bytes, bytes, len);
      this->len = len;
    }

    jbyteArray get(JNIEnv *env) {
      if (bytes == nullptr) {
        _log("SavedClassBytes: null\n");
        return nullptr;
      }

      jbyteArray result = env->NewByteArray(len);
      if (result == nullptr) {
        _log("SavedClassBytes: NewByteArray(%ld) failed\n", len);
      } else {
        jbyte* arrayPtr = env->GetByteArrayElements(result, nullptr);
        if (arrayPtr == nullptr) {
          _log("SavedClassBytes: Failed to get array elements\n");
          result = nullptr;
        } else {
          memcpy(arrayPtr, bytes, len);
          env->ReleaseByteArrayElements(result, arrayPtr, 0);
        }
      }
      return result;
    }

  };

  jclass klass;

  Buffer load;
  Buffer retransform;

  SavedClassBytes() : klass(nullptr) {}
};

static SavedClassBytes savedBytes[testClassCount];

static int testClassIndex(const char *name) {
  if (name != nullptr) {
    for (int i = 0; i < testClassCount; i++) {
      if (strcmp(name, testClassNames[i]) == 0) {
        return i;
      }
    }
  }
  return -1;
}


extern "C" {

JNIEXPORT void JNICALL
callbackClassFileLoadHook(jvmtiEnv *jvmti_env,
        JNIEnv* jni_env,
        jclass class_being_redefined,
        jobject loader,
        const char* name,
        jobject protection_domain,
        jint class_data_len,
        const unsigned char* class_data,
        jint* new_class_data_len,
        unsigned char** new_class_data) {
  int idx = testClassIndex(name);
  if (idx >= 0) {
    if (class_being_redefined == nullptr) {
      // load
      savedBytes[idx].load.save(class_data, class_data_len);
    } else {
      // retransform/redefine
      savedBytes[idx].retransform.save(class_data, class_data_len);
    }
  }
}

JNIEXPORT jint JNICALL Agent_OnLoad(JavaVM* jvm, char* options, void* reserved) {
  jint res = jvm->GetEnv((void **)&jvmti, JVMTI_VERSION_1_1);
  if (res != JNI_OK) {
    _log("Failed to get JVMTI interface: %ld\n", res);
    return JNI_ERR;
  }

  jvmtiCapabilities caps;
  memset(&caps, 0, sizeof(caps));

  caps.can_retransform_classes = 1;
  jvmtiError err = jvmti->AddCapabilities(&caps);
  if (err != JVMTI_ERROR_NONE) {
    _log("Failed to add capabilities: %d\n", err);
    return JNI_ERR;
  }

  jvmtiEventCallbacks eventCallbacks;
  memset(&eventCallbacks, 0, sizeof(eventCallbacks));
  eventCallbacks.ClassFileLoadHook = callbackClassFileLoadHook;
  err = jvmti->SetEventCallbacks(&eventCallbacks, sizeof(eventCallbacks));
  if (err != JVMTI_ERROR_NONE) {
    _log("Error setting event callbacks: %d\n", err);
    return JNI_ERR;
  }

  err = jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_CLASS_FILE_LOAD_HOOK, nullptr);
  if (err != JVMTI_ERROR_NONE) {
    _log("SetEventNotificationMode(JVMTI_ENABLE) error %d\n", err);
    return JNI_ERR;
  }

  return JNI_OK;
}

JNIEXPORT void JNICALL
Agent_OnUnload(JavaVM* jvm) {
  return;
}


JNIEXPORT jboolean JNICALL
Java_MissedStackMapFrames_doTest(JNIEnv* env, jclass klass) {

  jboolean result = JNI_TRUE;
  _log(">>nTest\n");

  for (int i = 0; i < testClassCount; i++) {
    _log("Loading %s...\n", testClassNames[i]);

    savedBytes[i].klass = env->FindClass(testClassNames[i]);
    if (savedBytes[i].klass == nullptr) {
      _log("Load error\n");
      result = JNI_FALSE;
      continue;
    }
    savedBytes[i].klass = (jclass)env->NewGlobalRef(savedBytes[i].klass);

    _log("Retransforming %s...\n", testClassNames[i]);
    jvmtiError err = jvmti->RetransformClasses(1, &savedBytes[i].klass);
    if (err != JVMTI_ERROR_NONE) {
      _log("RetransformClasses error %d\n", err);
      result = JNI_FALSE;
    }
  }
  _log("<<nTest\n");
  return result;
}

JNIEXPORT jint JNICALL
Java_MissedStackMapFrames_testCount(JNIEnv* env, jclass klass) {
  return testClassCount;
}

JNIEXPORT jclass JNICALL
Java_MissedStackMapFrames_testClass(JNIEnv* env, jclass klass, jint idx) {
  return savedBytes[idx].klass;
}

JNIEXPORT jbyteArray JNICALL
Java_MissedStackMapFrames_loadBytes(JNIEnv* env, jclass klass, jint idx) {
  return savedBytes[idx].load.get(env);
}

JNIEXPORT jbyteArray JNICALL
Java_MissedStackMapFrames_retransformBytes(JNIEnv* env, jclass klass, jint idx) {
  return savedBytes[idx].retransform.get(env);
}

} // extern "C"
