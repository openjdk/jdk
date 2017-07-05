/*
 * Copyright (c) 2000, 2013, Oracle and/or its affiliates. All rights reserved.
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
#include "com_sun_security_auth_module_SolarisSystem.h"
#include <stdio.h>
#include <pwd.h>
#include <unistd.h>
#include <stdlib.h>
#include <string.h>
#include <pwd.h>

static void throwIllegalArgumentException(JNIEnv *env, const char *msg) {
    jclass clazz = (*env)->FindClass(env, "java/lang/IllegalArgumentException");
    if (clazz != NULL)
        (*env)->ThrowNew(env, clazz, msg);
}

JNIEXPORT void JNICALL
Java_com_sun_security_auth_module_SolarisSystem_getSolarisInfo
                                                (JNIEnv *env, jobject obj) {

    int i;
    char pwd_buf[1024];
    struct passwd pwd;
    jsize numSuppGroups = getgroups(0, NULL);
    jfieldID fid;
    jstring jstr;
    jlongArray jgroups;
    jlong *jgroupsAsArray;
    gid_t *groups;
    jclass cls;

    groups = (gid_t *)calloc(numSuppGroups, sizeof(gid_t));

    if (groups == NULL) {
        jclass cls = (*env)->FindClass(env,"java/lang/OutOfMemoryError");
        if (cls != NULL)
            (*env)->ThrowNew(env, cls, NULL);
        return;
    }

    cls = (*env)->GetObjectClass(env, obj);

    memset(pwd_buf, 0, sizeof(pwd_buf));
    if (getpwuid_r(getuid(), &pwd, pwd_buf, sizeof(pwd_buf)) != NULL &&
        getgroups(numSuppGroups, groups) != -1) {

        /*
         * set username
         */
        fid = (*env)->GetFieldID(env, cls, "username", "Ljava/lang/String;");
        if (fid == 0) {
            (*env)->ExceptionClear(env);
            throwIllegalArgumentException(env, "invalid field: username");
            goto cleanupAndReturn;
        }
        jstr = (*env)->NewStringUTF(env, pwd.pw_name);
        if (jstr == NULL) {
            goto cleanupAndReturn;
        }
        (*env)->SetObjectField(env, obj, fid, jstr);

        /*
         * set uid
         */
        fid = (*env)->GetFieldID(env, cls, "uid", "J");
        if (fid == 0) {
            (*env)->ExceptionClear(env);
            throwIllegalArgumentException(env, "invalid field: uid");
            goto cleanupAndReturn;
        }
        (*env)->SetLongField(env, obj, fid, pwd.pw_uid);

        /*
         * set gid
         */
        fid = (*env)->GetFieldID(env, cls, "gid", "J");
        if (fid == 0) {
            (*env)->ExceptionClear(env);
            throwIllegalArgumentException(env, "invalid field: gid");
            goto cleanupAndReturn;
        }
        (*env)->SetLongField(env, obj, fid, pwd.pw_gid);

        /*
         * set supplementary groups
         */
        fid = (*env)->GetFieldID(env, cls, "groups", "[J");
        if (fid == 0) {
            (*env)->ExceptionClear(env);
            throwIllegalArgumentException(env, "invalid field: groups");
            goto cleanupAndReturn;
        }

        jgroups = (*env)->NewLongArray(env, numSuppGroups);
        if (jgroups == NULL) {
            goto cleanupAndReturn;
        }
        jgroupsAsArray = (*env)->GetLongArrayElements(env, jgroups, 0);
        if (jgroupsAsArray == NULL) {
            goto cleanupAndReturn;
        }
        for (i = 0; i < numSuppGroups; i++)
            jgroupsAsArray[i] = groups[i];
        (*env)->ReleaseLongArrayElements(env, jgroups, jgroupsAsArray, 0);
        (*env)->SetObjectField(env, obj, fid, jgroups);
    }
cleanupAndReturn:
    free(groups);

    return;
}
