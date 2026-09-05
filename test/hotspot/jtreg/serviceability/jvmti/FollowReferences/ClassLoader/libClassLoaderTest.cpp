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

#include <string.h>
#include <stdint.h>
#include "jvmti.h"
#include "jvmti_common.hpp"

extern "C" {

static jvmtiEnv* jvmti = nullptr;

JNIEXPORT jint JNICALL Agent_OnLoad(JavaVM *jvm, char *options, void *reserved) {
  jint res = jvm->GetEnv((void **)&jvmti, JVMTI_VERSION_1_1);
  if (res != JNI_OK || jvmti == nullptr) {
    printf("jvm->GetEnv failed\n");
    fflush(nullptr);
    return JNI_ERR;
  }

  jvmtiCapabilities caps;
  memset(&caps, 0, sizeof(caps));
  caps.can_tag_objects = 1;
  jvmtiError err = jvmti->AddCapabilities(&caps);
  if (err != JVMTI_ERROR_NONE) {
    printf("AddCapabilities failed: %s (%d)\n", TranslateError(err), err);
    fflush(nullptr);
    return JNI_ERR;
  }

  return JNI_OK;
}

static constexpr jlong TARGET_TAG = 0x1234;
static bool target_seen;

static jint JNICALL reference_callback(
        jvmtiHeapReferenceKind kind,
        const jvmtiHeapReferenceInfo* info,
        jlong class_tag,
        jlong referrer_class_tag,
        jlong size,
        jlong* tag_ptr,
        jlong* referrer_tag_ptr,
        jint length,
        void* user_data) {
    if (*tag_ptr == TARGET_TAG) {
        target_seen = true;
        printf("Reached tagged Test.class, reference kind: %d\n", kind);
    }
    return JVMTI_VISIT_OBJECTS;
}

JNIEXPORT jboolean JNICALL
Java_ClassLoaderTest_targetReachedFrom(
        JNIEnv* env, jclass, jobject loader, jclass target) {
    target_seen = false;

    check_jvmti_error(jvmti->SetTag(target, TARGET_TAG), "SetTag target");

    jvmtiHeapCallbacks callbacks = {};
    callbacks.heap_reference_callback = reference_callback;

    check_jvmti_error(
        jvmti->FollowReferences(0,        // do not filter tagged objects
                                nullptr,  // no class filter
                                loader,   // initial object
                                &callbacks,
                                nullptr),
        "FollowReferences");

    check_jvmti_error(jvmti->SetTag(target, 0), "clear target tag");
    return target_seen;
}

}
