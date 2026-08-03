/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

#include <inttypes.h>
#include <atomic>

#include "jvmti.h"
#include "jni.h"
#include "jvmti_common.hpp"

jvmtiEnv* jvmti_env;

// The event counters are used to check events from different threads.
static std::atomic<jint> access_cnt{0};
static std::atomic<jint> modify_cnt{0};


static const char* TEST_CLASS_NAME    = "LFieldEventsFromJNI;";

static const char* ACCESS_FIELD_NAME  = "accessField";
static const char* ACCESS_METHOD_NAME = "enableEventsAndAccessField";
static const char* MODIFY_FIELD_NAME  = "modifyField";
static const char* MODIFY_METHOD_NAME = "enableEventsAndModifyField";


static void JNICALL
cbFieldAccess(jvmtiEnv *jvmti, JNIEnv* jni, jthread thread, jmethodID method,
              jlocation location, jclass field_klass, jobject object, jfieldID field) {
  char* m_name = get_method_name(jvmti, jni, method);
  LOG("The field access triggered from method '%s'\n", m_name);
  if (strcmp(m_name, ACCESS_METHOD_NAME) != 0) {
    fatal(jni, "The method's name is incorrect.");
  }
  deallocate(jvmti,jni, m_name);

  LOG("The location is %" PRId64 "\n", (int64_t)location);
  if (location != 0) {
    fatal(jni, "The method's location should be 0 for jni call.");
  }

  char* f_name = get_field_name(jvmti, jni, field_klass, field);
  LOG("The field name '%s'\n", f_name);
  if (strcmp(f_name, ACCESS_FIELD_NAME) != 0) {
    fatal(jni, "The access field is incorrect.");
  }
  deallocate(jvmti,jni, f_name);

  char* obj_class_name = get_object_class_name(jvmti, jni, object);
  LOG("The object class '%s'\n", obj_class_name);
  if (strcmp(obj_class_name, TEST_CLASS_NAME) != 0) {
    fatal(jni, "The fields's class name is incorrect.");
  }
  deallocate(jvmti,jni, obj_class_name);

  access_cnt++;
}

static void JNICALL
cbFieldModification(jvmtiEnv *jvmti, JNIEnv* jni, jthread thread, jmethodID method,
                    jlocation location, jclass field_klass, jobject object, jfieldID field,
                    char signature_type, jvalue new_value) {
  char* m_name = get_method_name(jvmti, jni, method);
  LOG("The field modification triggered from method '%s'\n", m_name);
  if (strcmp(m_name, MODIFY_METHOD_NAME) != 0) {
    fatal(jni, "The method's name is incorrect.");
  }
  deallocate(jvmti,jni, m_name);

  LOG("The location is %" PRId64 "\n", (int64_t)location);
  if (location != 0) {
    fatal(jni, "The method's location should be 0 for jni call.");
  }

  char* f_name = get_field_name(jvmti, jni, field_klass, field);
  LOG("The field name '%s'\n", f_name);
  if (strcmp(f_name, MODIFY_FIELD_NAME) != 0) {
    fatal(jni, "The access field is incorrect.");
  }
  deallocate(jvmti,jni, f_name);

  char* obj_class_name = get_object_class_name(jvmti, jni, object);
  LOG("The object class '%s'\n", obj_class_name);
  if (strcmp(obj_class_name, TEST_CLASS_NAME) != 0) {
    fatal(jni, "The fields's class name is incorrect.");
  }
  deallocate(jvmti,jni, obj_class_name);

  modify_cnt++;
}

JNIEXPORT jint JNICALL
Agent_OnLoad(JavaVM *vm, char *options, void *reserved) {
  jvmtiEnv *jvmti = nullptr;
  jint res = vm->GetEnv((void **)&jvmti, JVMTI_VERSION_21);
  if (res != JNI_OK) {
    return JNI_ERR;
  }
  jvmtiError err = JVMTI_ERROR_NONE;
  jvmtiCapabilities capabilities;
  (void)memset(&capabilities, 0, sizeof (capabilities));
  capabilities.can_generate_field_access_events = true;
  capabilities.can_generate_field_modification_events = true;
  err = jvmti->AddCapabilities(&capabilities);
  check_jvmti_error(err, "AddCapabilities");
  jvmtiEventCallbacks callbacks;
  (void)memset(&callbacks, 0, sizeof (callbacks));
  callbacks.FieldAccess = &cbFieldAccess;
  callbacks.FieldModification = &cbFieldModification;
  err = jvmti->SetEventCallbacks(&callbacks, (int)sizeof (jvmtiEventCallbacks));
  check_jvmti_error(err, "SetEventCallbacks");
  jvmti_env = jvmti;
  return JNI_OK;
}

extern "C" {
JNIEXPORT void JNICALL
Java_FieldEventsFromJNI_enableEventsAndAccessField(
    JNIEnv *jni, jobject self, jint numOfEventsExpected, jthread eventThread) {

  jvmtiError err = JVMTI_ERROR_NONE;

  jclass cls = jni->GetObjectClass(self);
  if (cls == nullptr) {
    fatal(jni, "No class found");
  }
  jfieldID fieldToRead = jni->GetFieldID(cls, ACCESS_FIELD_NAME, "Ljava/lang/String;");
  if (fieldToRead == nullptr) {
    fatal(jni, "No field found");
  }

  // Set watch and access field without returning to calling java code
  access_cnt = 0;
  err = jvmti_env->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_FIELD_ACCESS, eventThread);
  check_jvmti_error(err, "SetEventNotificationMode");
  err = jvmti_env->SetFieldAccessWatch(cls, fieldToRead);
  check_jvmti_error(err, "SetFieldAccessWatch");

  jstring jvalue = (jstring)jni->GetObjectField(self, fieldToRead);

  err = jvmti_env->ClearFieldAccessWatch(cls, fieldToRead);
  check_jvmti_error(err, "ClearFieldAccessWatch");
  err = jvmti_env->SetEventNotificationMode(JVMTI_DISABLE, JVMTI_EVENT_FIELD_ACCESS, eventThread);
  check_jvmti_error(err, "SetEventNotificationMode");

  const char* value_str = jni->GetStringUTFChars(jvalue, nullptr);

  if (access_cnt != numOfEventsExpected) {
    char buffer[100];
    snprintf(buffer, sizeof(buffer),
        "Incorrect field access count: %d. Should be %d.",
        (int)access_cnt, numOfEventsExpected);
    fatal(jni, buffer);
  }
  jni->ReleaseStringUTFChars(jvalue, value_str);
}

JNIEXPORT void JNICALL
Java_FieldEventsFromJNI_enableEventsAndModifyField(
    JNIEnv *jni, jobject self, jint numOfEventsExpected, jthread eventThread) {
  jvmtiError err = JVMTI_ERROR_NONE;
  jclass cls = jni->GetObjectClass(self);
  if (cls == nullptr) {
    fatal(jni, "No class found");
  }
  jfieldID fieldToModify = jni->GetFieldID(cls, MODIFY_FIELD_NAME, "Ljava/lang/String;");
  if (fieldToModify == nullptr) {
    fatal(jni, "No field found");
  }

  modify_cnt = 0;
  err = jvmti_env->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_FIELD_MODIFICATION, eventThread);
  check_jvmti_error(err, "SetEventNotificationMode");
  err = jvmti_env->SetFieldModificationWatch(cls, fieldToModify);
  check_jvmti_error(err, "SetFieldAccessWatch");
  jstring jvalue = jni->NewStringUTF("newValue");

  jni->SetObjectField(self, fieldToModify, jvalue);

  err = jvmti_env->ClearFieldModificationWatch(cls, fieldToModify);
  check_jvmti_error(err, "ClearFieldAccessWatch");
  err = jvmti_env->SetEventNotificationMode(JVMTI_DISABLE, JVMTI_EVENT_FIELD_MODIFICATION, eventThread);
  check_jvmti_error(err, "SetEventNotificationMode");

  if (modify_cnt != numOfEventsExpected) {
    char buffer[100];
    snprintf(buffer, sizeof(buffer),
        "Incorrect field modification count: %d. Should be %d.",
        (int)modify_cnt, numOfEventsExpected);
    fatal(jni, buffer);
  }
}

}
