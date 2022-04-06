/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

#include <windows.h>
#include <winsock2.h>

#include "jni.h"
#include "jni_util.h"
#include "jvm.h"
#include "jlong.h"
#include "nio.h"
#include "nio_util.h"

static int socketFamily(jint fd) {
    struct sockaddr_storage st;
    struct sockaddr *sa = (struct sockaddr *)&st;
    socklen_t sa_len = sizeof(st);

    if (getsockname(fd, sa, &sa_len) == 0) {
        return sa->sa_family;
    }
    return -1;
}

/*
 * Class:     jdk_net_WindowsSocketOptions
 * Method:    setIpDontFragment0
 * Signature: (IZ)V
 */
JNIEXPORT void JNICALL Java_jdk_net_WindowsSocketOptions_setIpDontFragment0
(JNIEnv *env, jobject unused, jint fd, jboolean optval) {
    jint rv, optsetting;
    jint family = socketFamily(fd);
    if (family == -1) {
        handleError(env, family, "get socket family failed");
        return;
    }

    optsetting = optval ? IP_PMTUDISC_DO : IP_PMTUDISC_DONT;

    if (family == AF_INET) {
        rv = setsockopt(fd, IPPROTO_IP, IP_MTU_DISCOVER, &optsetting, sizeof(optsetting));
    } else {
        rv = setsockopt(fd, IPPROTO_IPV6, IP_MTU_DISCOVER, &optsetting, sizeof(optsetting));
    }
    handleError(env, rv, "set option IP_DONTFRAGMENT failed");
}

/*
 * Class:     jdk_net_WindowsSocketOptions
 * Method:    getIpDontFragment0
 * Signature: (I)Z;
 */
JNIEXPORT jboolean JNICALL Java_jdk_net_WindowsSocketOptions_getIpDontFragment0
(JNIEnv *env, jobject unused, jint fd) {
    jint optval, rv;
    rv = getsockopt(fd, IPPROTO_IP, IP_MTU_DISCOVER, &optval, sizeof (optval));
    handleError(env, rv, "get option IP_DONTFRAGMENT failed");
    return optval == IP_PMTUDISC_DO ? JNI_TRUE : JNI_FALSE;
}

