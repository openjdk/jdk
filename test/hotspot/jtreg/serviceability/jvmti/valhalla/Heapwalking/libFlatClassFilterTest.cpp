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

#include <jvmti.h>
#include "jvmti_common.hpp"

extern "C" {

static jvmtiEnv* jvmti = nullptr;
static jlong wanted_tag = 0;
static jint object_count = 0;

static jint JNICALL
heap_iteration(jlong class_tag, jlong size, jlong* tag_ptr, jint length, void* user_data) {
  if (class_tag == wanted_tag) {
    object_count++;
  }
  return 0;
}

JNIEXPORT jint JNICALL
Java_FlatClassFilterTest_count(JNIEnv* jni, jclass ignored, jclass filter,
                               jclass tagged_class, jlong class_tag) {
  jvmtiError err = jvmti->SetTag(tagged_class, class_tag);
  check_jvmti_status(jni, err, "SetTag");

  jvmtiHeapCallbacks callbacks;
  memset(&callbacks, 0, sizeof(callbacks));

  wanted_tag = class_tag;
  object_count = 0;
  callbacks.heap_iteration_callback = heap_iteration;

  err = jvmti->IterateThroughHeap(0, filter, &callbacks, nullptr);
  check_jvmti_status(jni, err, "IterateThroughHeap");

  return object_count;
}

JNIEXPORT jint JNICALL
Agent_OnLoad(JavaVM* vm, char* options, void* reserved) {
  if (vm->GetEnv(reinterpret_cast<void**>(&jvmti), JVMTI_VERSION_1_2) != JNI_OK) {
    return JNI_ERR;
  }
  jvmtiCapabilities caps;
  memset(&caps, 0, sizeof(caps));
  caps.can_tag_objects = 1;

  jvmtiError err = jvmti->AddCapabilities(&caps);
  check_jvmti_error(err, "AddCapabilities");

  return JNI_OK;
}

} // extern "C"

