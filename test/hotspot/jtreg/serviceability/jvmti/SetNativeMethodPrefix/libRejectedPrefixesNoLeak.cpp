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

#include <stdlib.h>
#include <string.h>
#include "jvmti.h"
#include "jvmti_common.hpp"

extern "C" {

static jvmtiEnv* jvmti = nullptr;

// Implements RejectedPrefixesNoLeak.rejectPrefixes(). Each call passes a
// two-element array whose second element is null, so the VM copies the first
// prefix, rejects the second one and cleans up. Returns the error code of the
// last call, which the caller checks.
JNIEXPORT jint JNICALL
Java_RejectedPrefixesNoLeak_rejectPrefixes(JNIEnv* jni, jclass cls, jint iterations, jint prefix_length) {
  char* prefix = (char*)malloc((size_t)prefix_length + 1);
  if (prefix == nullptr) {
    LOG("Could not allocate a %d byte prefix\n", (int)prefix_length);
    return JVMTI_ERROR_OUT_OF_MEMORY;
  }
  memset(prefix, 'a', (size_t)prefix_length);
  prefix[prefix_length] = '\0';

  // The null entry is what makes the VM reject the array, after it has already
  // copied the prefix in front of it.
  char* prefixes[2] = { prefix, nullptr };

  jvmtiError err = JVMTI_ERROR_NONE;
  for (jint i = 0; i < iterations; i++) {
    err = jvmti->SetNativeMethodPrefixes(2, prefixes);
    if (err != JVMTI_ERROR_NULL_POINTER) {
      LOG("SetNativeMethodPrefixes returned %d on iteration %d\n", err, (int)i);
      break;
    }
  }

  free(prefix);
  return (jint)err;
}

JNIEXPORT jint JNICALL
Agent_OnLoad(JavaVM* jvm, char* options, void* reserved) {
  if (jvm->GetEnv((void**)&jvmti, JVMTI_VERSION) != JNI_OK || jvmti == nullptr) {
    LOG("Could not initialize JVMTI env\n");
    return JNI_ERR;
  }

  jvmtiCapabilities caps;
  memset(&caps, 0, sizeof(caps));
  caps.can_set_native_method_prefix = 1;

  jvmtiError err = jvmti->AddCapabilities(&caps);
  if (err != JVMTI_ERROR_NONE) {
    LOG("AddCapabilities failed with %d\n", err);
    return JNI_ERR;
  }
  return JNI_OK;
}

} // extern "C"
