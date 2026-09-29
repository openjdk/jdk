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

static jvmtiEnv *jvmti = nullptr;
static int class_counter = 0;
static int other_counter = 0;

static jint JNICALL
heap_reference_callback(jvmtiHeapReferenceKind reference_kind,
                        const jvmtiHeapReferenceInfo* reference_info,
                        jlong class_tag,
                        jlong referrer_class_tag,
                        jlong size,
                        jlong* tag_ptr,
                        jlong* referrer_tag_ptr,
                        jint length,
                        void* user_data) {
  switch (reference_kind) {
  case JVMTI_HEAP_REFERENCE_SYSTEM_CLASS:
    *tag_ptr = ++class_counter;
    break;
  case JVMTI_HEAP_REFERENCE_OTHER:
    ++other_counter;
    break;
  default:
    break;
  }
  return JVMTI_VISIT_OBJECTS;
}

extern "C" JNIEXPORT jint JNICALL
Java_KindSystemClass_tagSysClasses(JNIEnv* jni, jclass clazz) {
  jvmtiHeapCallbacks callbacks = {};
  callbacks.heap_reference_callback = heap_reference_callback;

  jvmtiError err = jvmti->FollowReferences(0 /* filter nothing */,
                                           nullptr /* no class filter */,
                                           nullptr /* no initial object, follow roots */,
                                           &callbacks,
                                           nullptr);
  check_jvmti_error(err, "FollowReferences failed");

  LOG("JVMTI_HEAP_REFERENCE_SYSTEM_CLASS: %d, JVMTI_HEAP_REFERENCE_OTHER: %d\n", class_counter, other_counter);

  return class_counter;
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_KindSystemClass_getObjectsWithTags(JNIEnv* jni, jclass clazz) {
  // request tagged objects with tags 1..class_counter
  jlong* tags = nullptr;
  jvmtiError err = jvmti->Allocate(class_counter * sizeof(jlong), (unsigned char**)&tags);
  check_jvmti_error(err, "Allocate failed");

  for (int i = 0; i < class_counter; i++) {
    tags[i] = i + 1;
  }

  jint count = 0;
  jobject* objects = nullptr;

  err = jvmti->GetObjectsWithTags(class_counter, tags,
                                  &count, &objects, nullptr);
  check_jvmti_error(err, "GetObjectsWithTags failed");

  jclass object_klass = jni->FindClass("java/lang/Object");
  jobjectArray array = jni->NewObjectArray(count, object_klass, nullptr);

  for (jint i = 0; i < count; i++) {
    jni->SetObjectArrayElement(array, i, objects[i]);
  }

  deallocate(jvmti, jni, objects);

  return array;
}

extern "C" JNIEXPORT jint JNICALL Agent_OnLoad(JavaVM *vm, char *options, void *reserved) {
  if (vm->GetEnv(reinterpret_cast<void**>(&jvmti), JVMTI_VERSION) != JNI_OK || !jvmti) {
    LOG("Could not initialize JVMTI\n");
    abort();
  }
  jvmtiCapabilities capabilities;
  memset(&capabilities, 0, sizeof(capabilities));
  capabilities.can_tag_objects = 1;
  check_jvmti_error(jvmti->AddCapabilities(&capabilities), "adding capabilities");
  return JVMTI_ERROR_NONE;
}
