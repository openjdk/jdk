/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
#include <sys/socket.h>
#include <string.h>
#include <errno.h>
#include <unistd.h>

#include <jni.h>
#include <netinet/tcp.h>
#include "jni_util.h"

/*
 * Class:     jdk_net_LinuxSocketOptions
 * Method:    setQuickAck
 * Signature: (II)V
 */
JNIEXPORT void JNICALL Java_jdk_net_LinuxSocketOptions_setQuickAck0
(JNIEnv *env, jobject unused, jint fd, jboolean on) {
    int optval;
    int rv;
    optval = (on ? 1 : 0);
    rv = setsockopt(fd, SOL_SOCKET, TCP_QUICKACK, &optval, sizeof (optval));
    if (rv < 0) {
        if (errno == ENOPROTOOPT) {
            JNU_ThrowByName(env, "java/lang/UnsupportedOperationException",
                            "unsupported socket option");
        } else {
            JNU_ThrowByNameWithLastError(env, "java/net/SocketException",
                                        "set option TCP_QUICKACK failed");
        }
    }
}

/*
 * Class:     jdk_net_LinuxSocketOptions
 * Method:    getQuickAck
 * Signature: (I)Z;
 */
JNIEXPORT jboolean JNICALL Java_jdk_net_LinuxSocketOptions_getQuickAck0
(JNIEnv *env, jobject unused, jint fd) {
    int on;
    socklen_t sz = sizeof (on);
    int rv = getsockopt(fd, SOL_SOCKET, TCP_QUICKACK, &on, &sz);
    if (rv < 0) {
        if (errno == ENOPROTOOPT) {
            JNU_ThrowByName(env, "java/lang/UnsupportedOperationException",
                            "unsupported socket option");
        } else {
            JNU_ThrowByNameWithLastError(env, "java/net/SocketException",
                                        "get option TCP_QUICKACK failed");
        }
    }
    return on != 0;
}

/*
 * Class:     jdk_net_LinuxSocketOptions
 * Method:    quickAckSupported
 * Signature: ()Z
 */
JNIEXPORT jboolean JNICALL Java_jdk_net_LinuxSocketOptions_quickAckSupported0
(JNIEnv *env, jobject unused) {
    int one = 1;
    int rv, s;
    s = socket(PF_INET, SOCK_STREAM, 0);
    if (s < 0) {
        return JNI_FALSE;
    }
    rv = setsockopt(s, SOL_SOCKET, TCP_QUICKACK, (void *) &one, sizeof (one));
    if (rv != 0 && errno == ENOPROTOOPT) {
        rv = JNI_FALSE;
    } else {
        rv = JNI_TRUE;
    }
    close(s);
    return rv;
}
