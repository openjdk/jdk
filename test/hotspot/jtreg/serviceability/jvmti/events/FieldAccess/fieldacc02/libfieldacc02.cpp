/*
 * Copyright (c) 2003, 2024, Oracle and/or its affiliates. All rights reserved.
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

#include <stdio.h>
#include <string.h>
#include <inttypes.h>
#include "jvmti.h"
#include "jvmti_common.h"

extern "C" {


#define PASSED 0
#define STATUS_FAILED 2

typedef struct {
  jfieldID fid;
  char *m_cls;
  char *m_name;
  char *m_sig;
  jlocation loc;
  char *f_cls;
  char *f_name;
  char *f_sig;
  jboolean is_static;
} writable_watch_info;

typedef struct {
  jfieldID fid;
  const char *m_cls;
  const char *m_name;
  const char *m_sig;
  jlocation loc;
  const char *f_cls;
  const char *f_name;
  const char *f_sig;
  jboolean is_static;
} watch_info;

static jvmtiEnv *jvmti;
static jvmtiEventCallbacks callbacks;
static jvmtiCapabilities caps;
static jint result = PASSED;
static volatile jboolean isVirtualExpected = JNI_FALSE;
static int eventsExpected = 0;
static int eventsCount = 0;
static watch_info watches[] = {
    { nullptr, "Lfieldacc02;", "check", "(Ljava/lang/Object;)I", 0,
        "Lfieldacc02a;", "staticBoolean", "Z", JNI_TRUE },
    { nullptr, "Lfieldacc02;", "check", "(Ljava/lang/Object;)I", 0,
        "Lfieldacc02a;", "staticByte", "B", JNI_TRUE },
    { nullptr, "Lfieldacc02;", "check", "(Ljava/lang/Object;)I", 0,
        "Lfieldacc02a;", "staticShort", "S", JNI_TRUE },
    { nullptr, "Lfieldacc02;", "check", "(Ljava/lang/Object;)I", 0,
        "Lfieldacc02a;", "staticInt", "I", JNI_TRUE },
    { nullptr, "Lfieldacc02;", "check", "(Ljava/lang/Object;)I", 0,
        "Lfieldacc02a;", "staticLong", "J", JNI_TRUE },
    { nullptr, "Lfieldacc02;", "check", "(Ljava/lang/Object;)I", 0,
        "Lfieldacc02a;", "staticFloat", "F", JNI_TRUE },
    { nullptr, "Lfieldacc02;", "check", "(Ljava/lang/Object;)I", 0,
        "Lfieldacc02a;", "staticDouble", "D", JNI_TRUE },
    { nullptr, "Lfieldacc02;", "check", "(Ljava/lang/Object;)I", 0,
        "Lfieldacc02a;", "staticChar", "C", JNI_TRUE },
    { nullptr, "Lfieldacc02;", "check", "(Ljava/lang/Object;)I", 0,
        "Lfieldacc02a;", "staticObject", "Ljava/lang/Object;", JNI_TRUE },
    { nullptr, "Lfieldacc02;", "check", "(Ljava/lang/Object;)I", 0,
        "Lfieldacc02a;", "staticArrInt", "[I", JNI_TRUE },

    { nullptr, "Lfieldacc02;", "check", "(Ljava/lang/Object;)I", 0,
        "Lfieldacc02a;", "instanceBoolean", "Z", JNI_FALSE },
    { nullptr, "Lfieldacc02;", "check", "(Ljava/lang/Object;)I", 0,
        "Lfieldacc02a;", "instanceByte", "B", JNI_FALSE },
    { nullptr, "Lfieldacc02;", "check", "(Ljava/lang/Object;)I", 0,
        "Lfieldacc02a;", "instanceShort", "S", JNI_FALSE },
    { nullptr, "Lfieldacc02;", "check", "(Ljava/lang/Object;)I", 0,
        "Lfieldacc02a;", "instanceInt", "I", JNI_FALSE },
    { nullptr, "Lfieldacc02;", "check", "(Ljava/lang/Object;)I", 0,
        "Lfieldacc02a;", "instanceLong", "J", JNI_FALSE },
    { nullptr, "Lfieldacc02;", "check", "(Ljava/lang/Object;)I", 0,
        "Lfieldacc02a;", "instanceFloat", "F", JNI_FALSE },
    { nullptr, "Lfieldacc02;", "check", "(Ljava/lang/Object;)I", 0,
        "Lfieldacc02a;", "instanceDouble", "D", JNI_FALSE },
    { nullptr, "Lfieldacc02;", "check", "(Ljava/lang/Object;)I", 0,
        "Lfieldacc02a;", "instanceChar", "C", JNI_FALSE },
    { nullptr, "Lfieldacc02;", "check", "(Ljava/lang/Object;)I", 0,
        "Lfieldacc02a;", "instanceObject", "Ljava/lang/Object;", JNI_FALSE },
    { nullptr, "Lfieldacc02;", "check", "(Ljava/lang/Object;)I", 0,
        "Lfieldacc02a;", "instanceArrInt", "[I", JNI_FALSE }
};

void JNICALL FieldAccess(jvmtiEnv *jvmti, JNIEnv *jni,
                         jthread thr, jmethodID method,
                         jlocation location, jclass field_klass, jobject obj, jfieldID field) {
  jvmtiError err;
  jclass cls;
  writable_watch_info watch;
  char *generic;

  eventsCount++;
  LOG(">>> retrieving access watch info ...\n");

  watch.fid = field;
  watch.loc = location;
  watch.is_static = (obj == nullptr) ? JNI_TRUE : JNI_FALSE;
  err = jvmti->GetMethodDeclaringClass(method, &cls);
  if (err != JVMTI_ERROR_NONE) {
    LOG("(GetMethodDeclaringClass) unexpected error: %s (%d)\n", TranslateError(err), err);
    result = STATUS_FAILED;
    return;
  }
  err = jvmti->GetClassSignature(cls, &watch.m_cls, &generic);
  if (err != JVMTI_ERROR_NONE) {
    LOG("(GetClassSignature) unexpected error: %s (%d)\n", TranslateError(err), err);
    result = STATUS_FAILED;
    return;
  }
  err = jvmti->GetMethodName(method, &watch.m_name, &watch.m_sig, &generic);
  if (err != JVMTI_ERROR_NONE) {
    LOG("(GetMethodName) unexpected error: %s (%d)\n", TranslateError(err), err);
    result = STATUS_FAILED;
    return;
  }
  err = jvmti->GetClassSignature(field_klass, &watch.f_cls, &generic);
  if (err != JVMTI_ERROR_NONE) {
    LOG("(GetClassSignature) unexpected error: %s (%d)\n", TranslateError(err), err);
    result = STATUS_FAILED;
    return;
  }
  err = jvmti->GetFieldName(field_klass, field, &watch.f_name, &watch.f_sig, &generic);
  if (err != JVMTI_ERROR_NONE) {
    LOG("(GetFieldName) unexpected error: %s (%d)\n", TranslateError(err), err);
    result = STATUS_FAILED;
    return;
  }

  LOG(">>>      class: \"%s\"\n", watch.m_cls);
  LOG(">>>     method: \"%s%s\"\n", watch.m_name, watch.m_sig);
  LOG(">>>   location: 0x%x%08x\n", (jint)(watch.loc >> 32), (jint)watch.loc);
  LOG(">>>  field cls: \"%s\"\n", watch.f_cls);
  LOG(">>>      field: \"%s:%s\"\n", watch.f_name, watch.f_sig);
  LOG(">>>     object: 0x%p\n", obj);

  for (size_t i = 0; i < sizeof(watches)/sizeof(watch_info); i++) {
    if (watch.fid == watches[i].fid) {
      if (watch.m_cls == nullptr || strcmp(watch.m_cls, watches[i].m_cls) != 0) {
        LOG("(watch#%" PRIuPTR ") wrong class: \"%s\", expected: \"%s\"\n", i, watch.m_cls, watches[i].m_cls);
        result = STATUS_FAILED;
      }
      if (watch.m_name == nullptr || strcmp(watch.m_name, watches[i].m_name) != 0) {
        LOG("(watch#%" PRIuPTR ") wrong method name: \"%s\"", i, watch.m_name);
        LOG(", expected: \"%s\"\n", watches[i].m_name);
        result = STATUS_FAILED;
      }
      if (watch.m_sig == nullptr || strcmp(watch.m_sig, watches[i].m_sig) != 0) {
        LOG("(watch#%" PRIuPTR ") wrong method sig: \"%s\"", i, watch.m_sig);
        LOG(", expected: \"%s\"\n", watches[i].m_sig);
        result = STATUS_FAILED;
      }
      if (watch.loc != watches[i].loc) {
        LOG("(watch#%" PRIuPTR ") wrong location: 0x%x%08x", i, (jint)(watch.loc >> 32), (jint)watch.loc);
        LOG(", expected: 0x%x%08x\n", (jint)(watches[i].loc >> 32), (jint)watches[i].loc);
        result = STATUS_FAILED;
      }
      if (watch.f_name == nullptr || strcmp(watch.f_name, watches[i].f_name) != 0) {
        LOG("(watch#%" PRIuPTR ") wrong field name: \"%s\"", i, watch.f_name);
        LOG(", expected: \"%s\"\n", watches[i].f_name);
        result = STATUS_FAILED;
      }
      if (watch.f_sig == nullptr || strcmp(watch.f_sig, watches[i].f_sig) != 0) {
        LOG("(watch#%" PRIuPTR ") wrong field sig: \"%s\"", i, watch.f_sig);
        LOG(", expected: \"%s\"\n", watches[i].f_sig);
        result = STATUS_FAILED;
      }
      if (watch.is_static != watches[i].is_static) {
        LOG("(watch#%" PRIuPTR ") wrong field type: %s", i, (watch.is_static == JNI_TRUE) ? "static" : "instance");
        LOG(", expected: %s\n", (watches[i].is_static == JNI_TRUE) ? "static" : "instance");
        result = STATUS_FAILED;
      }
      jboolean isVirtual = jni->IsVirtualThread(thr);
      if (isVirtualExpected != isVirtual) {
        LOG("The thread IsVirtualThread %d differs from expected %d.\n", isVirtual, isVirtualExpected);
        result = STATUS_FAILED;
      }
      return;
    }
  }
  LOG("Unexpected field access catched: 0x%p\n", watch.fid);
  result = STATUS_FAILED;
}

jint Agent_Initialize(JavaVM *jvm, char *options, void *reserved) {
  jvmtiCapabilities caps;
  jvmtiError err;
  jint res;

  res = jvm->GetEnv((void **) &jvmti, JVMTI_VERSION_1_1);
  if (res != JNI_OK || jvmti == nullptr) {
    LOG("Wrong result of a valid call to GetEnv!\n");
    return JNI_ERR;
  }

  memset(&caps, 0, sizeof(jvmtiCapabilities));
  caps.can_generate_field_access_events = 1;
  caps.can_support_virtual_threads = 1;

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

  if (caps.can_generate_field_access_events) {
    callbacks.FieldAccess = &FieldAccess;
    err = jvmti->SetEventCallbacks(&callbacks, sizeof(callbacks));
    if (err != JVMTI_ERROR_NONE) {
      LOG("(SetEventCallbacks) unexpected error: %s (%d)\n", TranslateError(err), err);
      return JNI_ERR;
    }

    err = jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_FIELD_ACCESS, nullptr);
    if (err != JVMTI_ERROR_NONE) {
      LOG("Failed to enable JVMTI_EVENT_FIELD_ACCESS: %s (%d)\n",
             TranslateError(err), err);
      return JNI_ERR;
    }
  } else {
    LOG("Warning: FieldAccess watch is not implemented\n");
  }

  return JNI_OK;
}

JNIEXPORT void JNICALL Java_fieldacc02_getReady(JNIEnv *jni, jclass clz) {
  jvmtiError err;
  jclass cls;
  jthread thread;

  LOG(">>> setting field access watches ...\n");

  cls = jni->FindClass("fieldacc02a");
  if (cls == nullptr) {
    LOG("Cannot find fieldacc02a class!\n");
    result = STATUS_FAILED;
    return;
  }

  err = jvmti->GetCurrentThread(&thread);
  if (err != JVMTI_ERROR_NONE) {
    LOG("Failed to get current thread: %s (%d)\n", TranslateError(err), err);
    result = STATUS_FAILED;
    return;
  }

  eventsCount = 0;
  eventsExpected = 0;
  isVirtualExpected = jni->IsVirtualThread(thread);

  for (size_t i = 0; i < sizeof(watches)/sizeof(watch_info); i++) {
    if (watches[i].is_static == JNI_TRUE) {
      watches[i].fid = jni->GetStaticFieldID(cls, watches[i].f_name, watches[i].f_sig);
    } else {
      watches[i].fid = jni->GetFieldID(cls, watches[i].f_name, watches[i].f_sig);
    }
    if (watches[i].fid == nullptr) {
      LOG("Cannot find field \"%s\"!\n", watches[i].f_name);
      result = STATUS_FAILED;
      return;
    }
    err = jvmti->SetFieldAccessWatch(cls, watches[i].fid);
    if (err == JVMTI_ERROR_NONE) {
      eventsExpected++;
    } else {
      LOG("(SetFieldAccessWatch#%" PRIuPTR ") unexpected error: %s (%d)\n",
             i, TranslateError(err), err);
      result = STATUS_FAILED;
    }
  }

  LOG(">>> ... done\n");

}

JNIEXPORT jint JNICALL
Java_fieldacc02_check(JNIEnv *jni, jclass clz, jobject obj) {
  jclass cls;

  LOG(">>> accessing fields ...\n");

  cls = jni->FindClass("fieldacc02a");
  if (cls == nullptr) {
    LOG("Cannot find fieldacc02a class!\n");
    return STATUS_FAILED;
  }

  jni->GetStaticBooleanField(cls, watches[0].fid);
  jni->GetStaticByteField(cls, watches[1].fid);
  jni->GetStaticShortField(cls, watches[2].fid);
  jni->GetStaticIntField(cls, watches[3].fid);
  jni->GetStaticLongField(cls, watches[4].fid);
  jni->GetStaticFloatField(cls, watches[5].fid);
  jni->GetStaticDoubleField(cls, watches[6].fid);
  jni->GetStaticCharField(cls, watches[7].fid);
  jni->GetStaticObjectField(cls, watches[8].fid);
  jni->GetStaticObjectField(cls, watches[9].fid);

  jni->GetBooleanField(obj, watches[10].fid);
  jni->GetByteField(obj, watches[11].fid);
  jni->GetShortField(obj, watches[12].fid);
  jni->GetIntField(obj, watches[13].fid);
  jni->GetLongField(obj, watches[14].fid);
  jni->GetFloatField(obj, watches[15].fid);
  jni->GetDoubleField(obj, watches[16].fid);
  jni->GetCharField(obj, watches[17].fid);
  jni->GetObjectField(obj, watches[18].fid);
  jni->GetObjectField(obj, watches[19].fid);

  LOG(">>> ... done\n");

  if (eventsCount != eventsExpected) {
    LOG("Wrong number of field access events: %d, expected: %d\n", eventsCount, eventsExpected);
    result = STATUS_FAILED;
  }

  for (size_t i = 0; i < sizeof(watches)/sizeof(watch_info); i++) {
    jvmtiError err = jvmti->ClearFieldAccessWatch(cls, watches[i].fid);
    if (err == JVMTI_ERROR_NONE) {
      eventsExpected++;
    } else {
      LOG("(ClearFieldAccessWatch#%" PRIuPTR ") unexpected error: %s (%d)\n", i, TranslateError(err), err);
      result = STATUS_FAILED;
    }
  }
  return result;
}

JNIEXPORT jint JNICALL Agent_OnLoad(JavaVM *jvm, char *options, void *reserved) {
  return Agent_Initialize(jvm, options, reserved);
}

JNIEXPORT jint JNICALL Agent_OnAttach(JavaVM *jvm, char *options, void *reserved) {
  return Agent_Initialize(jvm, options, reserved);
}

}
