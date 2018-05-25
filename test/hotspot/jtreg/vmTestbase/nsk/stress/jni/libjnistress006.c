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

#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include "jnihelper.h"

JNIEXPORT jboolean JNICALL
Java_nsk_stress_jni_JNIter006_refs (JNIEnv *env, jobject jobj, jobject tobj, jint LIMIT) {

    static jobject *globRefsArray = 0;
    static int upper = 0;

    jclass clazz;
    jmethodID jmethod;
    jboolean res=JNI_FALSE;
    const char *classname = "nsk/stress/jni/JNIter006";
    const char *getmethodname="get_i";
    const char *setmethodname="set_i";
    const char *getsig="()I";
    const char *setsig="(I)V";
    const char *setdone = "halt";
    const char *setdonesig = "()V";
    int i = 0;

    if (upper >= LIMIT) return JNI_TRUE;

    if (upper==0)
    globRefsArray=(jobject*)(malloc(LIMIT*sizeof(jobject)));

    globRefsArray[upper]=(*env)->NewGlobalRef(env, tobj); CE
    if ((*env)->IsSameObject(env, tobj, globRefsArray[upper])) {
    (*env)->DeleteLocalRef(env, tobj); CE
    clazz=(*env)->GetObjectClass(env, globRefsArray[upper]); CE
    }
    else {
    fprintf(stderr,"Objects are different\n");
    (*env)->MonitorExit(env, jobj); CE
    return res;
    }
    jmethod=(*env)->GetStaticMethodID(env, clazz, setmethodname, setsig); CE
    (*env)->CallStaticVoidMethod(env, clazz, jmethod, (jint)upper); CE
    (*env)->MonitorEnter(env, jobj); CE
    ++upper;
    res=JNI_TRUE;
    (*env)->MonitorExit(env, jobj); CE
/* If upper == LIMIT than flush ref's array and set */
/* 'done' flag in JNIter006 class to JNI_TRUE */
    if (upper == LIMIT) {
    fprintf(stderr,"\n\tTotal memory allocated: %zd bytes\n",
        LIMIT*sizeof(jobject));
    clazz=(*env)->FindClass(env, classname); CE
    jmethod=(*env)->GetMethodID(env, clazz, setdone, setdonesig); CE
    (*env)->CallVoidMethod(env, jobj, jmethod); CE

    for (i=0;i<LIMIT;i++) {
        (*env)->DeleteGlobalRef(env, globRefsArray[i]); CE
    }
        free(globRefsArray);
    }
    return res;
}
