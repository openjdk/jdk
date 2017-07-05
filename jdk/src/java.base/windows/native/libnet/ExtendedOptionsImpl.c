/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

#include <jni.h>
#include <string.h>

#include "net_util.h"

/*
 * Class:     sun_net_ExtendedOptionsImpl
 * Method:    init
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_sun_net_ExtendedOptionsImpl_init
  (JNIEnv *env, jclass UNUSED)
{
}

/* Non Solaris. Functionality is not supported. So, throw UnsupportedOpExc */

JNIEXPORT void JNICALL Java_sun_net_ExtendedOptionsImpl_setFlowOption
  (JNIEnv *env, jclass UNUSED, jobject fileDesc, jobject flow)
{
    JNU_ThrowByName(env, "java/lang/UnsupportedOperationException",
        "unsupported socket option");
}

JNIEXPORT void JNICALL Java_sun_net_ExtendedOptionsImpl_getFlowOption
  (JNIEnv *env, jclass UNUSED, jobject fileDesc, jobject flow)
{
    JNU_ThrowByName(env, "java/lang/UnsupportedOperationException",
        "unsupported socket option");
}

static jboolean flowSupported0()  {
    return JNI_FALSE;
}

JNIEXPORT jboolean JNICALL Java_sun_net_ExtendedOptionsImpl_flowSupported
  (JNIEnv *env, jclass UNUSED)
{
    return JNI_FALSE;
}
