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

static jfieldID IO_fd_fdID;

static jfieldID apdsi_fdID;


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
    int  retval;

    jobject fdObj = (*env)->GetObjectField(env, this, apdsi_fdID);

    if (IS_NULL(fdObj)) {
        JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException",
                        "Socket closed");
        return -1;
    }
    fd = (SOCKET)(*env)->GetIntField(env, fdObj, IO_fd_fdID);

    if (ioctlsocket(fd, FIONREAD, &retval) < 0) {
        return -1;
    }
    return retval;
}

