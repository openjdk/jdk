/*
 * Copyright 1994-2000 Sun Microsystems, Inc.  All Rights Reserved.
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

/*
 *      Implementation of class Throwable
 *
 *      former classruntime.c, Wed Jun 26 18:43:20 1991
 */

#include <stdio.h>
#include <signal.h>

#include "jni.h"
#include "jvm.h"

#include "java_lang_Throwable.h"

/*
 * Fill in the current stack trace in this exception.  This is
 * usually called automatically when the exception is created but it
 * may also be called explicitly by the user.  This routine returns
 * `this' so you can write 'throw e.fillInStackTrace();'
 */
JNIEXPORT jobject JNICALL
Java_java_lang_Throwable_fillInStackTrace(JNIEnv *env, jobject throwable)
{
    JVM_FillInStackTrace(env, throwable);
    return throwable;
}

JNIEXPORT jint JNICALL
Java_java_lang_Throwable_getStackTraceDepth(JNIEnv *env, jobject throwable)
{
    return JVM_GetStackTraceDepth(env, throwable);
}

JNIEXPORT jobject JNICALL
Java_java_lang_Throwable_getStackTraceElement(JNIEnv *env,
                                              jobject throwable, jint index)
{
    return JVM_GetStackTraceElement(env, throwable, index);
}
