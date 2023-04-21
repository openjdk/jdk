/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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
#include "jdk_internal_org_jline_terminal_impl_jna_osx_CLibraryImpl.h"

#include <stdlib.h>
#include <termios.h>
#include <unistd.h>
#include <sys/ioctl.h>

static jclass termios_j;
static jfieldID c_iflag;
static jfieldID c_oflag;
static jfieldID c_cflag;
static jfieldID c_lflag;
static jfieldID c_cc;
static jfieldID c_ispeed;
static jfieldID c_ospeed;

static jclass winsize_j;
static jfieldID ws_row;
static jfieldID ws_col;
static jfieldID ws_xpixel;
static jfieldID ws_ypixel;

static jclass nativelong_j;
static jfieldID nativelong_value;

JNIEXPORT void JNICALL Java_jdk_internal_org_jline_terminal_impl_jna_osx_CLibraryImpl_initIDs
  (JNIEnv *env, jclass) {
    termios_j = env->FindClass("jdk/internal/org/jline/terminal/impl/jna/osx/CLibrary$termios");
    CHECK_NULL(termios_j);
    c_iflag = env->GetFieldID(termios_j, "c_iflag", "Ljdk/internal/org/jline/terminal/impl/jna/osx/NativeLong;");
    CHECK_NULL(c_iflag);
    c_oflag = env->GetFieldID(termios_j, "c_oflag", "Ljdk/internal/org/jline/terminal/impl/jna/osx/NativeLong;");
    CHECK_NULL(c_oflag);
    c_cflag = env->GetFieldID(termios_j, "c_cflag", "Ljdk/internal/org/jline/terminal/impl/jna/osx/NativeLong;");
    CHECK_NULL(c_cflag);
    c_lflag = env->GetFieldID(termios_j, "c_lflag", "Ljdk/internal/org/jline/terminal/impl/jna/osx/NativeLong;");
    CHECK_NULL(c_lflag);
    c_cc = env->GetFieldID(termios_j, "c_cc", "[B");
    CHECK_NULL(c_cc);
    c_ispeed = env->GetFieldID(termios_j, "c_ispeed", "Ljdk/internal/org/jline/terminal/impl/jna/osx/NativeLong;");
    CHECK_NULL(c_ispeed);
    c_ospeed = env->GetFieldID(termios_j, "c_ospeed", "Ljdk/internal/org/jline/terminal/impl/jna/osx/NativeLong;");
    CHECK_NULL(c_ospeed);

    winsize_j = env->FindClass("jdk/internal/org/jline/terminal/impl/jna/osx/CLibrary$winsize");
    CHECK_NULL(winsize_j);
    ws_row = env->GetFieldID(winsize_j, "ws_row", "S");
    CHECK_NULL(ws_row);
    ws_col = env->GetFieldID(winsize_j, "ws_col", "S");
    CHECK_NULL(ws_col);
    ws_xpixel= env->GetFieldID(winsize_j, "ws_xpixel", "S");
    CHECK_NULL(ws_xpixel);
    ws_ypixel= env->GetFieldID(winsize_j, "ws_ypixel", "S");
    CHECK_NULL(ws_ypixel);

    nativelong_j = env->FindClass("jdk/internal/org/jline/terminal/impl/jna/osx/NativeLong");
    CHECK_NULL(nativelong_j);
    nativelong_value = env->GetFieldID(nativelong_j, "value", "J");
    CHECK_NULL(nativelong_value);
}

JNIEXPORT void JNICALL Java_jdk_internal_org_jline_terminal_impl_jna_osx_CLibraryImpl_tcgetattr
  (JNIEnv *env, jobject, jint fd, jobject result) {
    termios data;

    tcgetattr(fd, &data);

    env->SetLongField(env->GetObjectField(result, c_iflag), nativelong_value, data.c_iflag);
    env->SetLongField(env->GetObjectField(result, c_oflag), nativelong_value, data.c_oflag);
    env->SetLongField(env->GetObjectField(result, c_cflag), nativelong_value, data.c_cflag);
    env->SetLongField(env->GetObjectField(result, c_lflag), nativelong_value, data.c_lflag);
    jbyteArray c_ccValue = (jbyteArray) env->GetObjectField(result, c_cc);
    env->SetByteArrayRegion(c_ccValue, 0, NCCS, (signed char *) data.c_cc);
    env->SetLongField(env->GetObjectField(result, c_ispeed), nativelong_value, data.c_ispeed);
    env->SetLongField(env->GetObjectField(result, c_ospeed), nativelong_value, data.c_ospeed);
}

/*
 * Class:     jdk_internal_org_jline_terminal_impl_jna_osx_CLibraryImpl
 * Method:    tcsetattr
 * Signature: (IILjdk/internal/org/jline/terminal/impl/jna/osx/CLibrary/termios;)V
 */
JNIEXPORT void JNICALL Java_jdk_internal_org_jline_terminal_impl_jna_osx_CLibraryImpl_tcsetattr
  (JNIEnv *env, jobject, jint fd, jint cmd, jobject input) {
    termios data;

    data.c_iflag = env->GetLongField(env->GetObjectField(input, c_iflag), nativelong_value);
    data.c_oflag = env->GetLongField(env->GetObjectField(input, c_oflag), nativelong_value);
    data.c_cflag = env->GetLongField(env->GetObjectField(input, c_cflag), nativelong_value);
    data.c_lflag = env->GetLongField(env->GetObjectField(input, c_lflag), nativelong_value);
    jbyteArray c_ccValue = (jbyteArray) env->GetObjectField(input, c_cc);
    env->GetByteArrayRegion(c_ccValue, 0, NCCS, (signed char *) data.c_cc);
    data.c_ispeed = env->GetLongField(env->GetObjectField(input, c_ispeed), nativelong_value);
    data.c_ospeed = env->GetLongField(env->GetObjectField(input, c_ospeed), nativelong_value);

    tcsetattr(fd, cmd, &data);
}

/*
 * Class:     jdk_internal_org_jline_terminal_impl_jna_osx_CLibraryImpl
 * Method:    ioctl0
 * Signature: (IILjdk/internal/org/jline/terminal/impl/jna/osx/CLibrary/winsize;)V
 */
JNIEXPORT void JNICALL Java_jdk_internal_org_jline_terminal_impl_jna_osx_CLibraryImpl_ioctl0
  (JNIEnv *env, jobject, jint fd, jlong cmd, jobject data) {
    winsize ws;

    ws.ws_row = env->GetIntField(data, ws_row);
    ws.ws_col = env->GetIntField(data, ws_col);
    ws.ws_xpixel = env->GetIntField(data, ws_xpixel);
    ws.ws_ypixel = env->GetIntField(data, ws_ypixel);

    ioctl(fd, cmd, &ws);

    env->SetIntField(data, ws_row, ws.ws_row);
    env->SetIntField(data, ws_col, ws.ws_col);
    env->SetIntField(data, ws_xpixel, ws.ws_xpixel);
    env->SetIntField(data, ws_ypixel, ws.ws_ypixel);
}

/*
 * Class:     jdk_internal_org_jline_terminal_impl_jna_osx_CLibraryImpl
 * Method:    isatty
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL Java_jdk_internal_org_jline_terminal_impl_jna_osx_CLibraryImpl_isatty
  (JNIEnv *, jobject, jint fd) {
    return isatty(fd);
}

/*
 * Class:     jdk_internal_org_jline_terminal_impl_jna_osx_CLibraryImpl
 * Method:    ttyname_r
 * Signature: (I[BI)V
 */
JNIEXPORT void JNICALL Java_jdk_internal_org_jline_terminal_impl_jna_osx_CLibraryImpl_ttyname_1r
  (JNIEnv *env, jobject, jint fd, jbyteArray buf, jint len) {
    char *data = new char[len];
    int ignored = ttyname_r(fd, data, len);

    env->SetByteArrayRegion(buf, 0, len, (signed char *) data);
    delete[] data;
}
