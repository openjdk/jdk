/*
 * Copyright (c) 1994, 2026, Oracle and/or its affiliates. All rights reserved.
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

#include "jni.h"
#include "jvm.h"

#include "java_lang_Thread.h"

#define THD "Ljava/lang/Thread;"
#define OBJ "Ljava/lang/Object;"
#define STE "Ljava/lang/StackTraceElement;"
#define STR "Ljava/lang/String;"

#define ARRAY_LENGTH(a) (sizeof(a)/sizeof(a[0]))

static JNINativeMethod methods[] = {
    {"start0",           "()V",        (void *)&JVM_StartThread},
    {"setPriority0",     "(I)V",       (void *)&JVM_SetThreadPriority},
    {"yield0",           "()V",        (void *)&JVM_Yield},
    {"sleepNanos0",      "(J)V",       (void *)&JVM_SleepNanos},
    {"currentCarrierThread", "()" THD, (void *)&JVM_CurrentCarrierThread},
    {"currentThread",    "()" THD,     (void *)&JVM_CurrentThread},
    {"setCurrentThread", "(" THD ")V", (void *)&JVM_SetCurrentThread},
    {"interrupt0",       "()V",        (void *)&JVM_Interrupt},
    {"holdsLock",        "(" OBJ ")Z", (void *)&JVM_HoldsLock},
    {"getThreads",       "()[" THD,    (void *)&JVM_GetAllThreads},
    {"dumpThreads",      "([" THD ")[[" STE, (void *)&JVM_DumpThreads},
    {"getStackTrace0",   "()[" STE,    (void *)&JVM_GetStackTrace},
    {"setNativeName",    "(" STR ")V", (void *)&JVM_SetNativeThreadName},
    {"scopedValueCache", "()[" OBJ,    (void *)&JVM_ScopedValueCache},
    {"setScopedValueCache", "([" OBJ ")V",(void *)&JVM_SetScopedValueCache},
    {"getNextThreadIdOffset", "()J",   (void *)&JVM_GetNextThreadIdOffset},
    {"findScopedValueBindings", "()" OBJ, (void *)&JVM_FindScopedValueBindings},
    {"ensureMaterializedForStackWalk",
                         "(" OBJ ")V", (void*)&JVM_EnsureMaterializedForStackWalk_func},
};

#undef THD
#undef OBJ
#undef STE
#undef STR

JNIEXPORT void JNICALL
Java_java_lang_Thread_registerNatives(JNIEnv *env, jclass cls)
{
    (*env)->RegisterNatives(env, cls, methods, ARRAY_LENGTH(methods));
}

JNIEXPORT void JNICALL
Java_java_lang_Thread_clearInterruptEvent(JNIEnv *env, jclass cls)
{
#if defined(_WIN32)
    // Need to reset the interrupt event used by Process.waitFor
    ResetEvent((HANDLE) JVM_GetThreadInterruptEvent());
#endif
}
