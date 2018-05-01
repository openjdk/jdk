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
#include "nsk_tools.h"

#ifdef __cplusplus
extern "C" {
#endif

#ifndef JNI_ENV_PTR

#ifdef __cplusplus
#define JNI_ENV_ARG_2(x, y) y
#define JNI_ENV_ARG_3(x, y, z) y, z
#define JNI_ENV_ARG_4(x, y, z. a) y, z, a
#define JNI_ENV_PTR(x) x
#else
#define JNI_ENV_ARG_2(x,y) x, y
#define JNI_ENV_ARG_3(x, y, z) x, y, z
#define JNI_ENV_ARG_4(x, y, z, a) x, y, z, a
#define JNI_ENV_PTR(x) (*x)
#endif

#endif

JNIEXPORT void JNICALL
Java_nsk_share_locks_JNIMonitorLocker_doLock(JNIEnv *env, jobject thisObject)
{
/*
This method executes JNI analog for following Java code:

        JNI_MonitorEnter(this);

        step1.unlockAll();
        step2.waitFor();
        readyWicket.unlock();
        inner.lock();

        JNI_MonitorExit(this);
*/
        jint success;
        jfieldID field;
        jclass thisObjectClass;

        // fields 'step1' and 'step2'
        jobject wicketObject;

        // class for fields 'step1', 'step2', 'readyWicket'
        jclass wicketClass;

        // field 'inner'
        jobject innerObject;

        // class for field 'inner'
        jclass deadlockLockerClass;

        success = JNI_ENV_PTR(env)->MonitorEnter(JNI_ENV_ARG_2(env, thisObject));

        if(success != 0)
        {
                NSK_COMPLAIN1("MonitorEnter return non-zero: %d\n", success);

                JNI_ENV_PTR(env)->ThrowNew(JNI_ENV_ARG_3(env, JNI_ENV_PTR(env)->FindClass(JNI_ENV_ARG_2(env, "nsk/share/TestJNIError")), "MonitorEnter return non-zero"));
        }

        thisObjectClass = JNI_ENV_PTR(env)->GetObjectClass(JNI_ENV_ARG_2(env, thisObject));

        // step1.unlockAll()
        field = JNI_ENV_PTR(env)->GetFieldID(JNI_ENV_ARG_4(env, thisObjectClass, "step1", "Lnsk/share/Wicket;"));

        wicketObject = JNI_ENV_PTR(env)->GetObjectField(JNI_ENV_ARG_3(env, thisObject, field));
        wicketClass = JNI_ENV_PTR(env)->GetObjectClass(JNI_ENV_ARG_2(env, wicketObject));

        JNI_ENV_PTR(env)->CallVoidMethod(JNI_ENV_ARG_3(env, wicketObject, JNI_ENV_PTR(env)->GetMethodID(JNI_ENV_ARG_4(env, wicketClass, "unlockAll", "()V"))));

        // step2.waitFor()
        field = JNI_ENV_PTR(env)->GetFieldID(JNI_ENV_ARG_4(env, thisObjectClass, "step2", "Lnsk/share/Wicket;"));
        wicketObject = JNI_ENV_PTR(env)->GetObjectField(JNI_ENV_ARG_3(env, thisObject, field));

        JNI_ENV_PTR(env)->CallVoidMethod(JNI_ENV_ARG_3(env, wicketObject, JNI_ENV_PTR(env)->GetMethodID(JNI_ENV_ARG_4(env, wicketClass, "waitFor", "()V"))));

        // readyWicket.unlock()
        field = JNI_ENV_PTR(env)->GetFieldID(JNI_ENV_ARG_4(env, thisObjectClass, "readyWicket", "Lnsk/share/Wicket;"));
        wicketObject = JNI_ENV_PTR(env)->GetObjectField(JNI_ENV_ARG_3(env, thisObject, field));

        JNI_ENV_PTR(env)->CallVoidMethod(JNI_ENV_ARG_3(env, wicketObject, JNI_ENV_PTR(env)->GetMethodID(JNI_ENV_ARG_4(env, wicketClass, "unlock", "()V"))));

        // inner.lock()
        field = JNI_ENV_PTR(env)->GetFieldID(JNI_ENV_ARG_4(env, thisObjectClass, "inner", "Lnsk/share/locks/DeadlockLocker;"));
        innerObject = JNI_ENV_PTR(env)->GetObjectField(JNI_ENV_ARG_3(env, thisObject, field));
        deadlockLockerClass = JNI_ENV_PTR(env)->GetObjectClass(JNI_ENV_ARG_2(env, innerObject));

        JNI_ENV_PTR(env)->CallVoidMethod(JNI_ENV_ARG_3(env, innerObject, JNI_ENV_PTR(env)->GetMethodID(JNI_ENV_ARG_4(env, deadlockLockerClass, "lock", "()V"))));

        success = JNI_ENV_PTR(env)->MonitorExit(JNI_ENV_ARG_2(env, thisObject));

        if(success != 0)
        {
                NSK_COMPLAIN1("MonitorExit return non-zero: %d\n", success);

                JNI_ENV_PTR(env)->ThrowNew(JNI_ENV_ARG_3(env, JNI_ENV_PTR(env)->FindClass(JNI_ENV_ARG_2(env, "nsk/share/TestJNIError")), "MonitorExit return non-zero"));
        }
}

#ifdef __cplusplus
}
#endif
