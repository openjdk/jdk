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

#include <windows.h>
#include <winsock2.h>

#include "jvm.h"
#include "jni_util.h"
#include "net_util.h"

#include "java_net_AbstractPlainDatagramSocketImpl.h"

static jfieldID IO_fd_fdID = NULL;
static jfieldID apdsi_fdID = NULL;

static jfieldID apdsi_fd1ID = NULL;
static jclass two_stacks_clazz = NULL;


/*
 * Class:     java_net_AbstractPlainDatagramSocketImpl
 * Method:    init
 * Signature: ()V
 */
JNIEXPORT void JNICALL
Java_java_net_AbstractPlainDatagramSocketImpl_init(JNIEnv *env, jclass cls) {

    apdsi_fdID = (*env)->GetFieldID(env, cls, "fd",
                                   "Ljava/io/FileDescriptor;");
    CHECK_NULL(apdsi_fdID);
    IO_fd_fdID = NET_GetFileDescriptorID(env);
    CHECK_NULL(IO_fd_fdID);

    two_stacks_clazz = (*env)->FindClass(env, "java/net/TwoStacksPlainDatagramSocketImpl");
    CHECK_NULL(two_stacks_clazz);

    /* Handle both TwoStacks and DualStack here */

    if (JNU_Equals(env, cls, two_stacks_clazz)) {
        /* fd1 present only in TwoStack.. */
        apdsi_fd1ID = (*env)->GetFieldID(env, cls, "fd1",
                                   "Ljava/io/FileDescriptor;");
        CHECK_NULL(apdsi_fd1ID);
    }

    JNU_CHECK_EXCEPTION(env);
}

/*
 * Class:     java_net_AbstractPlainDatagramSocketImpl
 * Method:    dataAvailable
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_java_net_AbstractPlainDatagramSocketImpl_dataAvailable
(JNIEnv *env, jobject this) {
    SOCKET fd;
    SOCKET fd1;
    int  rv = -1, rv1 = -1;
    jobject fdObj = (*env)->GetObjectField(env, this, apdsi_fdID);

    if (!IS_NULL(fdObj)) {
        int retval = 0;
        fd = (SOCKET)(*env)->GetIntField(env, fdObj, IO_fd_fdID);
        rv = ioctlsocket(fd, FIONREAD, &retval);
        if (retval > 0) {
            return retval;
        }
    }

    if (!IS_NULL(apdsi_fd1ID)) {
        /* TwoStacks */
        jobject fd1Obj = (*env)->GetObjectField(env, this, apdsi_fd1ID);
        if (!IS_NULL(fd1Obj)) {
            int retval = 0;
            fd1 = (SOCKET)(*env)->GetIntField(env, fd1Obj, IO_fd_fdID);
            rv1 = ioctlsocket(fd1, FIONREAD, &retval);
            if (retval > 0) {
                return retval;
            }
        }
    }

    if (rv < 0 && rv1 < 0) {
        JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException",
                            "Socket closed");
        return -1;
    }

    return 0;
}

