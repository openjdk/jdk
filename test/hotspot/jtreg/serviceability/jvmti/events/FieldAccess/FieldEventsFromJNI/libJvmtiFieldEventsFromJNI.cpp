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

#include "jvmti.h"
#include "jni.h"
#include "jvmti_common.hpp"

jvmtiEnv* jvmti_env;

static int access_cnt = 0;
static int modify_cnt = 0;

static char*
get_object_class_name(jvmtiEnv *jvmti, JNIEnv* jni, jobject object) {
  char *obj_class_name = nullptr;
  jclass object_class = jni->GetObjectClass(object);
  jvmtiError err = jvmti->GetClassSignature(object_class, &obj_class_name, nullptr);
  check_jvmti_error(err, "GetClassSignature");
  jni->DeleteLocalRef(object_class);
  return obj_class_name;
}

static void JNICALL
cbFieldAccess(jvmtiEnv *jvmti, JNIEnv* jni, jthread thread, jmethodID method,
              jlocation location, jclass field_klass, jobject object, jfieldID field) {

  char* m_name = get_method_name(jvmti, jni, method);
  LOG("The field access triggered from method '%s'\n", m_name);
  if (strcmp(m_name, "enableEventsAndAccessField") != 0) {
    fatal(jni, "The method's name is incorrect.");
  }
  deallocate(jvmti,jni, m_name);


  LOG("The location = %ld\n", location);
  if (location != 0) {
    fatal(jni, "The method's location should be 0 for jni call.");
  }

  char* f_name = get_field_name(jvmti, jni, field_klass, field);
  LOG("The field name '%s'\n", f_name);
  if (strcmp(f_name, "accessField") != 0) {
    fatal(jni, "The access field is incorrect.");
  }
  deallocate(jvmti,jni, f_name);


  char* obj_class_name = get_object_class_name(jvmti, jni, object);
  LOG("The object class '%s'\n", obj_class_name);
  if (strcmp(obj_class_name, "LTestFieldsEventsFromJNI;") != 0) {
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
  if (strcmp(m_name, "enableEventsAndModifyField") != 0) {
    fatal(jni, "The method's name is incorrect.");
  }
  deallocate(jvmti,jni, m_name);

  LOG("The location = %ld\n", location);
  if (location != 0) {
    fatal(jni, "The method's location should be 0 for jni call.");
  }

  char* f_name = get_field_name(jvmti, jni, field_klass, field);
  LOG("The field name '%s'\n", f_name);
  if (strcmp(f_name, "modifyField") != 0) {
    fatal(jni, "The access field is incorrect.");
  }
  deallocate(jvmti,jni, f_name);


  char* obj_class_name = get_object_class_name(jvmti, jni, object);
  LOG("The object class '%s'\n", obj_class_name);
  if (strcmp(obj_class_name, "LTestFieldsEventsFromJNI;") != 0) {
    fatal(jni, "The fields's class name is incorrect.");
  }
  deallocate(jvmti,jni, obj_class_name);

  modify_cnt++;
}

JNIEXPORT jint JNICALL
Agent_OnLoad(JavaVM *vm, char *options, void *reserved) {
  jvmtiEnv *jvmti = nullptr;
  jint res = vm->GetEnv((void **) &jvmti, JVMTI_VERSION_21);
  if (res != JNI_OK) {
    return JNI_ERR;
  }
  jvmtiError err = JVMTI_ERROR_NONE;
  jvmtiCapabilities capabilities;
  (void) memset(&capabilities, 0, sizeof (capabilities));
  capabilities.can_generate_field_access_events = true;
  capabilities.can_generate_field_modification_events = true;
  err = jvmti->AddCapabilities(&capabilities);
  check_jvmti_error(err, "AddCapabilities");
  jvmtiEventCallbacks callbacks;
  (void) memset(&callbacks, 0, sizeof (callbacks));
  callbacks.FieldAccess = &cbFieldAccess;
  callbacks.FieldModification = &cbFieldModification;
  err = jvmti->SetEventCallbacks(&callbacks, (int) sizeof (jvmtiEventCallbacks));
  check_jvmti_error(err, "SetEventCallbacks");
  jvmti_env = jvmti;
  return JNI_OK;
}


extern "C" {
JNIEXPORT void JNICALL
Java_TestFieldsEventsFromJNI_enableEventsAndAccessField(JNIEnv *jni, jobject self) {
  jvmtiError err = JVMTI_ERROR_NONE;


 jclass cls = jni->GetObjectClass(self);
  if (cls == nullptr) {
    fatal(jni, "No class found");
  }
  jfieldID fieldToRead = jni->GetFieldID(cls, "accessField", "Ljava/lang/String;");
  if (fieldToRead == nullptr) {
    fatal(jni, "No field found");
  }
  // Set watch and access field without returning to calling java code
  err = jvmti_env->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_FIELD_ACCESS, nullptr);
  check_jvmti_error(err, "SetEventNotificationMode");
  err = jvmti_env->SetFieldAccessWatch(cls, fieldToRead);
  check_jvmti_error(err, "SetFieldAccessWatch");

  jstring jname = (jstring)jni->GetObjectField(self, fieldToRead);

  err = jvmti_env->ClearFieldAccessWatch(cls, fieldToRead);
  check_jvmti_error(err, "ClearFieldAccessWatch");

  err = jvmti_env->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_FIELD_ACCESS, nullptr);
  check_jvmti_error(err, "SetEventNotificationMode");

  const char* name_str = jni->GetStringUTFChars(jname, nullptr);
  printf("The field %s\n", name_str);
  if (strcmp(name_str, "accessFieldValue") != 0) {
    fatal(jni, "The field value is incorrect.");
  }
  if (access_cnt != 1) {
    fatal(jni, "The field access count should be 1.");
  }
  jni->ReleaseStringUTFChars(jname, name_str);
}


JNIEXPORT void JNICALL
Java_TestFieldsEventsFromJNI_enableEventsAndModifyField(JNIEnv *jni, jobject self) {
  jvmtiError err = JVMTI_ERROR_NONE;
  err = jvmti_env->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_FIELD_MODIFICATION, nullptr);
  check_jvmti_error(err, "SetEventNotificationMode");

  jclass cls = jni->GetObjectClass(self);
  if (cls == nullptr) {
    fatal(jni, "No class found");
  }
  jfieldID fieldToModify = jni->GetFieldID(cls, "modifyField", "Ljava/lang/String;");
  if (fieldToModify == nullptr) {
    fatal(jni, "No field found");
  }
  err = jvmti_env->SetFieldModificationWatch(cls, fieldToModify);
  check_jvmti_error(err, "SetFieldAccessWatch");
  jstring jval = jni->NewStringUTF("newValue");

  jni->SetObjectField(self, fieldToModify, jval);

  err = jvmti_env->ClearFieldModificationWatch(cls, fieldToModify);
  check_jvmti_error(err, "ClearFieldAccessWatch");

  err = jvmti_env->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_FIELD_MODIFICATION, nullptr);
  check_jvmti_error(err, "SetEventNotificationMode");

  if (modify_cnt != 1) {
    fatal(jni, "The field access count should be 1.");
  }
}


}
