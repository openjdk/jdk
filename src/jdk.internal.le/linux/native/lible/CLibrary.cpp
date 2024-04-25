/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "jni_util.h"
#include "jvm.h"
#include "jdk_internal_org_jline_terminal_impl_jna_linux_CLibraryImpl.h"

#include <errno.h>
#include <stdlib.h>
#include <termios.h>
#include <unistd.h>
#include <sys/ioctl.h>

static jclass lastErrorExceptionClass;
static jmethodID lastErrorExceptionConstructor;

static jclass termios_j;
static jfieldID c_iflag;
static jfieldID c_oflag;
static jfieldID c_cflag;
static jfieldID c_lflag;
static jfieldID c_line;
static jfieldID c_cc;
static jfieldID c_ispeed;
static jfieldID c_ospeed;

static jclass winsize_j;
static jfieldID ws_row;
static jfieldID ws_col;
static jfieldID ws_xpixel;
static jfieldID ws_ypixel;

static void throw_errno(JNIEnv *env);

JNIEXPORT void JNICALL Java_jdk_internal_org_jline_terminal_impl_jna_linux_CLibraryImpl_initIDs
  (JNIEnv *env, jclass unused) {
    jclass cls;
    cls = env->FindClass("jdk/internal/org/jline/terminal/impl/jna/LastErrorException");
    CHECK_NULL(cls);
    lastErrorExceptionClass = (jclass) env->NewGlobalRef(cls);
    lastErrorExceptionConstructor = env->GetMethodID(lastErrorExceptionClass, "<init>", "(J)V");
    CHECK_NULL(lastErrorExceptionConstructor);

    cls = env->FindClass("jdk/internal/org/jline/terminal/impl/jna/linux/CLibrary$termios");
    CHECK_NULL(cls);
    termios_j = (jclass) env->NewGlobalRef(cls);
    c_iflag = env->GetFieldID(termios_j, "c_iflag", "I");
    CHECK_NULL(c_iflag);
    c_oflag = env->GetFieldID(termios_j, "c_oflag", "I");
    CHECK_NULL(c_oflag);
    c_cflag = env->GetFieldID(termios_j, "c_cflag", "I");
    CHECK_NULL(c_cflag);
    c_lflag = env->GetFieldID(termios_j, "c_lflag", "I");
    CHECK_NULL(c_lflag);
    c_line = env->GetFieldID(termios_j, "c_line", "B");
    CHECK_NULL(c_line);
    c_cc = env->GetFieldID(termios_j, "c_cc", "[B");
    CHECK_NULL(c_cc);
    c_ispeed = env->GetFieldID(termios_j, "c_ispeed", "I");
    CHECK_NULL(c_ispeed);
    c_ospeed = env->GetFieldID(termios_j, "c_ospeed", "I");
    CHECK_NULL(c_ospeed);

    cls = env->FindClass("jdk/internal/org/jline/terminal/impl/jna/linux/CLibrary$winsize");
    CHECK_NULL(cls);
    winsize_j = (jclass) env->NewGlobalRef(cls);
    ws_row = env->GetFieldID(winsize_j, "ws_row", "S");
    CHECK_NULL(ws_row);
    ws_col = env->GetFieldID(winsize_j, "ws_col", "S");
    CHECK_NULL(ws_col);
    ws_xpixel= env->GetFieldID(winsize_j, "ws_xpixel", "S");
    CHECK_NULL(ws_xpixel);
    ws_ypixel= env->GetFieldID(winsize_j, "ws_ypixel", "S");
    CHECK_NULL(ws_ypixel);
}

JNIEXPORT void JNICALL Java_jdk_internal_org_jline_terminal_impl_jna_linux_CLibraryImpl_tcgetattr
  (JNIEnv *env, jobject, jint fd, jobject result) {
    termios data;

    if (tcgetattr(fd, &data) != 0) {
        throw_errno(env);
        return ;
    }

    env->SetIntField(result, c_iflag, data.c_iflag);
    env->SetIntField(result, c_oflag, data.c_oflag);
    env->SetIntField(result, c_cflag, data.c_cflag);
    env->SetIntField(result, c_lflag, data.c_lflag);
    env->SetIntField(result, c_line, data.c_line);
    jbyteArray c_ccValue = (jbyteArray) env->GetObjectField(result, c_cc);
    env->SetByteArrayRegion(c_ccValue, 0, NCCS, (signed char *) data.c_cc);//TODO: cast?
    env->SetIntField(result, c_ispeed, cfgetispeed(&data));
    env->SetIntField(result, c_ospeed, cfgetospeed(&data));
}

/*
 * Class:     jdk_internal_org_jline_terminal_impl_jna_linux_CLibraryImpl
 * Method:    tcsetattr
 * Signature: (IILjdk/internal/org/jline/terminal/impl/jna/linux/CLibrary/termios;)V
 */
JNIEXPORT void JNICALL Java_jdk_internal_org_jline_terminal_impl_jna_linux_CLibraryImpl_tcsetattr
  (JNIEnv *env, jobject, jint fd, jint cmd, jobject input) {
    termios data;

    data.c_iflag = env->GetIntField(input, c_iflag);
    data.c_oflag = env->GetIntField(input, c_oflag);
    data.c_cflag = env->GetIntField(input, c_cflag);
    data.c_lflag = env->GetIntField(input, c_lflag);
    data.c_line = env->GetIntField(input, c_line);
    jbyteArray c_ccValue = (jbyteArray) env->GetObjectField(input, c_cc);
    env->GetByteArrayRegion(c_ccValue, 0, NCCS, (jbyte *) data.c_cc);
    cfsetispeed(&data, env->GetIntField(input, c_ispeed));
    cfsetospeed(&data, env->GetIntField(input, c_ospeed));

    if (tcsetattr(fd, cmd, &data) != 0) {
        throw_errno(env);
    }
}

/*
 * Class:     jdk_internal_org_jline_terminal_impl_jna_linux_CLibraryImpl
 * Method:    ioctl0
 * Signature: (IILjdk/internal/org/jline/terminal/impl/jna/linux/CLibrary/winsize;)V
 */
JNIEXPORT void JNICALL Java_jdk_internal_org_jline_terminal_impl_jna_linux_CLibraryImpl_ioctl0
  (JNIEnv *env, jobject, jint fd, jint cmd, jobject data) {
    winsize ws;

    ws.ws_row = env->GetShortField(data, ws_row);
    ws.ws_col = env->GetShortField(data, ws_col);
    ws.ws_xpixel = env->GetShortField(data, ws_xpixel);
    ws.ws_ypixel = env->GetShortField(data, ws_ypixel);

    if (ioctl(fd, cmd, &ws) != 0) {
        throw_errno(env);
        return ;
    }

    env->SetShortField(data, ws_row, ws.ws_row);
    env->SetShortField(data, ws_col, ws.ws_col);
    env->SetShortField(data, ws_xpixel, ws.ws_xpixel);
    env->SetShortField(data, ws_ypixel, ws.ws_ypixel);
}

/*
 * Class:     jdk_internal_org_jline_terminal_impl_jna_linux_CLibraryImpl
 * Method:    isatty
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL Java_jdk_internal_org_jline_terminal_impl_jna_linux_CLibraryImpl_isatty
  (JNIEnv *, jobject, jint fd) {
    return isatty(fd);
}

/*
 * Class:     jdk_internal_org_jline_terminal_impl_jna_linux_CLibraryImpl
 * Method:    ttyname_r
 * Signature: (I[BI)V
 */
JNIEXPORT void JNICALL Java_jdk_internal_org_jline_terminal_impl_jna_linux_CLibraryImpl_ttyname_1r
  (JNIEnv *env, jobject, jint fd, jbyteArray buf, jint len) {
    char *data = new char[len];
    int error = ttyname_r(fd, data, len);

    if (error != 0) {
        delete[] data;
        throw_errno(env);
        return ;
    }

    env->SetByteArrayRegion(buf, 0, len, (jbyte *) data);
    delete[] data;
}

/*
 * Throws LastErrorException based on the errno:
 */
static void throw_errno(JNIEnv *env) {
    jobject exc = env->NewObject(lastErrorExceptionClass,
                                 lastErrorExceptionConstructor,
                                 errno);
    env->Throw((jthrowable) exc);
}
