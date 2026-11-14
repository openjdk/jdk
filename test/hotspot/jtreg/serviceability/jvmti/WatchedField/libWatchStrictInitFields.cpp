/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

#include <stdint.h>
#include <string.h>

#include "jni.h"
#include "jvmti.h"

static jvmtiEnv* jvmti;

static jint mod_count;
static jint access_count;

// Prints the class and field that triggered a watch event.
// (Prints to stderr so that it inlines with the JUnit test output)
static void print_watched_field_event(const char* event_name, jclass field_klass, jfieldID field_id) {
  char* class_signature = nullptr;
  char* field_name = nullptr;
  jvmtiError class_error = jvmti->GetClassSignature(field_klass, &class_signature, nullptr);
  jvmtiError field_error = jvmti->GetFieldName(field_klass, field_id, &field_name, nullptr, nullptr);
  if (class_error == JVMTI_ERROR_NONE && field_error == JVMTI_ERROR_NONE) {
    const char* class_name = class_signature;
    size_t class_name_len = strlen(class_signature);
    if (class_name_len >= 2 && class_signature[0] == 'L' && class_signature[class_name_len - 1] == ';') {
      class_name++;
      class_name_len -= 2;
    }
    fprintf(stderr, "  %s: %.*s.%s\n", event_name, static_cast<int>(class_name_len), class_name, field_name);
  }
  if (class_signature != nullptr) {
    jvmti->Deallocate(reinterpret_cast<unsigned char*>(class_signature));
  }
  if (field_name != nullptr) {
    jvmti->Deallocate(reinterpret_cast<unsigned char*>(field_name));
  }
}

// FieldModification callback.
static void JNICALL field_modification(jvmtiEnv*, JNIEnv*, jthread, jmethodID, jlocation,
                                       jclass field_klass, jobject, jfieldID field_id, char, jvalue) {
  print_watched_field_event("FieldModification", field_klass, field_id);
  mod_count++;
}

// FieldAccess callback.
static void JNICALL field_access(jvmtiEnv*, JNIEnv*, jthread, jmethodID, jlocation,
                                 jclass field_klass, jobject, jfieldID field_id) {
  print_watched_field_event("FieldAccess", field_klass, field_id);
  access_count++;
}

extern "C" {

// Initializes the JVMTI agent and enables field watch events.
JNIEXPORT jint JNICALL Agent_OnLoad(JavaVM* vm, char* options, void* reserved) {
  if (vm->GetEnv((void**)&jvmti, JVMTI_VERSION) != JNI_OK) {
    return JNI_ERR;
  }

  jvmtiCapabilities capabilities;
  memset(&capabilities, 0, sizeof(capabilities));
  capabilities.can_generate_field_access_events = 1;
  capabilities.can_generate_field_modification_events = 1;
  if (jvmti->AddCapabilities(&capabilities) != JVMTI_ERROR_NONE) {
    return JNI_ERR;
  }

  jvmtiEventCallbacks callbacks;
  memset(&callbacks, 0, sizeof(callbacks));
  callbacks.FieldAccess = field_access;
  callbacks.FieldModification = field_modification;
  if (jvmti->SetEventCallbacks(&callbacks, sizeof(callbacks)) != JVMTI_ERROR_NONE) {
      return JNI_ERR;
  }
  if (jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_FIELD_ACCESS, nullptr) != JVMTI_ERROR_NONE) {
    return JNI_ERR;
  }
  if (jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_FIELD_MODIFICATION, nullptr) != JVMTI_ERROR_NONE) {
    return JNI_ERR;
  }

  return JNI_OK;
}

// Returns the JNI FieldID for a field without initializing its class.
JNIEXPORT jlong JNICALL
Java_WatchStrictInitFields_fieldID(JNIEnv* env, jclass, jclass field_klass, jstring field_name) {
  const char* wanted_name = env->GetStringUTFChars(field_name, nullptr);
  if (wanted_name == nullptr) {
    return 0; // pending OOME
  }

  jint field_count = 0;
  jfieldID* fields = nullptr;
  jvmtiError error = jvmti->GetClassFields(field_klass, &field_count, &fields);

  jfieldID result = nullptr;
  if (error == JVMTI_ERROR_NONE) {
    for (jint i = 0; i < field_count; i++) {
      char* name = nullptr;
      error = jvmti->GetFieldName(field_klass, fields[i], &name, nullptr, nullptr);
      if (error != JVMTI_ERROR_NONE) {
        break;
      }
      bool found = (strcmp(name, wanted_name) == 0);
      jvmti->Deallocate(reinterpret_cast<unsigned char*>(name));
      if (found) {
        result = fields[i];
        break;
      }
    }
  }

  if (fields != nullptr) {
    jvmti->Deallocate(reinterpret_cast<unsigned char*>(fields));
  }
  env->ReleaseStringUTFChars(field_name, wanted_name);

  if (error != JVMTI_ERROR_NONE || result == nullptr) {
    env->FatalError("GetClassFields, GetFieldName, or field lookup failed");
  }

  return static_cast<jlong>(reinterpret_cast<uintptr_t>(result));
}

JNIEXPORT void JNICALL
Java_WatchStrictInitFields_setFieldModificationWatch(JNIEnv* env, jclass, jclass field_klass, jlong id) {
  jfieldID field_id = reinterpret_cast<jfieldID>(static_cast<uintptr_t>(id));
  if (jvmti->SetFieldModificationWatch(field_klass, field_id) != JVMTI_ERROR_NONE) {
    env->FatalError("SetFieldModificationWatch failed");
  }
}

JNIEXPORT void JNICALL
Java_WatchStrictInitFields_clearFieldModificationWatch(JNIEnv* env, jclass, jclass field_klass, jlong id) {
  jfieldID field_id = reinterpret_cast<jfieldID>(static_cast<uintptr_t>(id));
  if (jvmti->ClearFieldModificationWatch(field_klass, field_id) != JVMTI_ERROR_NONE) {
    env->FatalError("ClearFieldModificationWatch failed");
  }
}

JNIEXPORT void JNICALL
Java_WatchStrictInitFields_setFieldAccessWatch(JNIEnv* env, jclass, jclass field_klass, jlong id) {
  jfieldID field_id = reinterpret_cast<jfieldID>(static_cast<uintptr_t>(id));
  if (jvmti->SetFieldAccessWatch(field_klass, field_id) != JVMTI_ERROR_NONE) {
    env->FatalError("SetFieldAccessWatch failed");
  }
}

JNIEXPORT void JNICALL
Java_WatchStrictInitFields_clearFieldAccessWatch(JNIEnv* env, jclass, jclass field_klass, jlong id) {
  jfieldID field_id = reinterpret_cast<jfieldID>(static_cast<uintptr_t>(id));
  if (jvmti->ClearFieldAccessWatch(field_klass, field_id) != JVMTI_ERROR_NONE) {
    env->FatalError("ClearFieldAccessWatch failed");
  }
}

JNIEXPORT jint JNICALL
Java_WatchStrictInitFields_modCount(JNIEnv*, jclass) {
  return mod_count;
}

JNIEXPORT void JNICALL
Java_WatchStrictInitFields_resetModCount(JNIEnv*, jclass) {
  mod_count = 0;
}

JNIEXPORT jint JNICALL
Java_WatchStrictInitFields_accessCount(JNIEnv*, jclass) {
  return access_count;
}

JNIEXPORT void JNICALL
Java_WatchStrictInitFields_resetAccessCount(JNIEnv*, jclass) {
  access_count = 0;
}

} // extern "C"
