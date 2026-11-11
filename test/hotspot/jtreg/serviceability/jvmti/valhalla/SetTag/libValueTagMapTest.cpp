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

#include <jvmti.h>
#include "jvmti_common.hpp"

namespace {

jvmtiEnv *jvmti = nullptr;

}

extern "C" JNIEXPORT void JNICALL Java_ValueTagMapTest_setTag0(JNIEnv* jni_env, jclass clazz, jobject object, jlong tag) {
  jvmtiError err = jvmti->SetTag(object, tag);
  check_jvmti_error(err, "could not set tag");
}

extern "C" JNIEXPORT jlong JNICALL Java_ValueTagMapTest_getTag0(JNIEnv* jni_env, jclass clazz, jobject object) {
  jlong tag;
  check_jvmti_error(jvmti->GetTag(object, &tag), "could not get tag");
  return tag;
}

extern "C" JNIEXPORT jint JNICALL Agent_OnLoad(JavaVM *vm, char *options, void *reserved) {
  if (vm->GetEnv(reinterpret_cast<void **>(&jvmti), JVMTI_VERSION) != JNI_OK || !jvmti) {
    LOG("Could not initialize JVMTI\n");
    abort();
  }
  jvmtiCapabilities capabilities;
  memset(&capabilities, 0, sizeof(capabilities));
  capabilities.can_tag_objects = 1;
  check_jvmti_error(jvmti->AddCapabilities(&capabilities), "adding capabilities");
  return JVMTI_ERROR_NONE;
}

