/*
 * Copyright (c) 2007, 2018, Oracle and/or its affiliates. All rights reserved.
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

#include "jni.h"

extern "C" {


int always_true = 1;

JNIEXPORT jint JNICALL
Java_nsk_jdwp_ThreadReference_ForceEarlyReturn_forceEarlyReturn002_forceEarlyReturn002a_nativeMethod(JNIEnv *env, jobject classObject, jobject object)
{
    int dummy_counter = 0;
    // notify another thread that thread in native method
    jclass klass = env->GetObjectClass(object);
    jfieldID field = env->GetFieldID(klass, "threadInNative", "Z");
    env->SetBooleanField(object, field, 1);

    // execute infinite loop to be sure that thread in native method
    while (always_true)
    {
        // Need some dummy code so the optimizer does not remove this loop.
        dummy_counter = dummy_counter < 1000 ? 0 : dummy_counter + 1;
    }
    // The optimizer can be surprisingly clever.
    // Use dummy_counter so it can never be optimized out.
    // This statement will always return 0.
    return dummy_counter >= 0 ? 0 : 1;
}

}
