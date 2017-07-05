/*
 * Copyright 2003-2004 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

#include <jni.h>
#include "management.h"
#include "sun_management_MemoryImpl.h"

JNIEXPORT void JNICALL Java_sun_management_MemoryImpl_setVerboseGC
  (JNIEnv *env, jobject dummy, jboolean flag) {
    jmm_interface->SetBoolAttribute(env, JMM_VERBOSE_GC, flag);
}

JNIEXPORT jobject JNICALL Java_sun_management_MemoryImpl_getMemoryPools0
  (JNIEnv *env, jclass dummy) {
    return jmm_interface->GetMemoryPools(env, NULL);
}

JNIEXPORT jobject JNICALL Java_sun_management_MemoryImpl_getMemoryManagers0
  (JNIEnv *env, jclass dummy) {
    return jmm_interface->GetMemoryManagers(env, NULL);
}

JNIEXPORT jobject JNICALL Java_sun_management_MemoryImpl_getMemoryUsage0
  (JNIEnv *env, jobject dummy, jboolean heap) {
    return jmm_interface->GetMemoryUsage(env, heap);
}
