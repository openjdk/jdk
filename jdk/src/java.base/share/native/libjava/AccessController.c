/*
 * Copyright (c) 1997, 1998, Oracle and/or its affiliates. All rights reserved.
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

/*-
 *      Implementation of class java.security.AccessController
 *
 */

#include <string.h>

#include "jni.h"
#include "jvm.h"
#include "java_security_AccessController.h"

/*
 * Class:     java_security_AccessController
 * Method:    doPrivileged
 * Signature: (Ljava/security/PrivilegedAction;)Ljava/lang/Object;
 */
JNIEXPORT jobject JNICALL Java_java_security_AccessController_doPrivileged__Ljava_security_PrivilegedAction_2
  (JNIEnv *env, jclass cls, jobject action)
{
    return JVM_DoPrivileged(env, cls, action, NULL, JNI_FALSE);
}

/*
 * Class:     java_security_AccessController
 * Method:    doPrivileged
 * Signature: (Ljava/security/PrivilegedAction;Ljava/security/AccessControlContext;)Ljava/lang/Object;
 */
JNIEXPORT jobject JNICALL Java_java_security_AccessController_doPrivileged__Ljava_security_PrivilegedAction_2Ljava_security_AccessControlContext_2
  (JNIEnv *env, jclass cls, jobject action, jobject context)
{
    return JVM_DoPrivileged(env, cls, action, context, JNI_FALSE);
}

/*
 * Class:     java_security_AccessController
 * Method:    doPrivileged
 * Signature: (Ljava/security/PrivilegedExceptionAction;)Ljava/lang/Object;
 */
JNIEXPORT jobject JNICALL Java_java_security_AccessController_doPrivileged__Ljava_security_PrivilegedExceptionAction_2
  (JNIEnv *env, jclass cls, jobject action)
{
    return JVM_DoPrivileged(env, cls, action, NULL, JNI_TRUE);
}

/*
 * Class:     java_security_AccessController
 * Method:    doPrivileged
 * Signature: (Ljava/security/PrivilegedExceptionAction;Ljava/security/AccessControlContext;)Ljava/lang/Object;
 */
JNIEXPORT jobject JNICALL Java_java_security_AccessController_doPrivileged__Ljava_security_PrivilegedExceptionAction_2Ljava_security_AccessControlContext_2
  (JNIEnv *env, jclass cls, jobject action, jobject context)
{
    return JVM_DoPrivileged(env, cls, action, context, JNI_TRUE);
}

JNIEXPORT jobject JNICALL
Java_java_security_AccessController_getStackAccessControlContext(
                                                              JNIEnv *env,
                                                              jobject this)
{
    return JVM_GetStackAccessControlContext(env, this);
}


JNIEXPORT jobject JNICALL
Java_java_security_AccessController_getInheritedAccessControlContext(
                                                              JNIEnv *env,
                                                              jobject this)
{
    return JVM_GetInheritedAccessControlContext(env, this);
}
