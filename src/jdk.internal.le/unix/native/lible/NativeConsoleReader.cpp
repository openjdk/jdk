/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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
#include "jdk_internal_console_NativeConsoleReader.h"

#include <errno.h>
#include <stdlib.h>
#include <termios.h>
#include <unistd.h>
#include <sys/ioctl.h>

static jclass lastErrorExceptionClass;
static jmethodID lastErrorExceptionConstructor;

static void throw_errno(JNIEnv *env);

JNIEXPORT void JNICALL Java_jdk_internal_console_NativeConsoleReader_initIDs
  (JNIEnv *env, jclass) {
    jclass cls;
    cls = env->FindClass("jdk/internal/console/LastErrorException");
    CHECK_NULL(cls);
    lastErrorExceptionClass = (jclass) env->NewGlobalRef(cls);
    lastErrorExceptionConstructor = env->GetMethodID(lastErrorExceptionClass, "<init>", "(J)V");
    CHECK_NULL(lastErrorExceptionConstructor);
}

JNIEXPORT jbyteArray JNICALL Java_jdk_internal_console_NativeConsoleReader_switchToRaw
  (JNIEnv *env, jclass) {
    int fd = 0;
    termios data;

    if (tcgetattr(fd, &data) != 0) {
        throw_errno(env);
        return NULL;
    }

    size_t termios_size = sizeof(termios);
    jbyteArray result = env->NewByteArray(termios_size);
    env->SetByteArrayRegion(result, 0, termios_size, (jbyte *) &data);

    data.c_iflag &= ~(BRKINT | IGNPAR | ICRNL | IXON | IMAXBEL) | IXOFF;
    data.c_lflag &= ~(ICANON | ECHO);

    if (tcsetattr(fd, TCSADRAIN, &data) != 0) {
        throw_errno(env);
        return NULL;
    }

    return result;
}

JNIEXPORT void JNICALL Java_jdk_internal_console_NativeConsoleReader_restore
  (JNIEnv *env, jclass, jbyteArray storedData) {
    int fd = 0;
    termios data;

    size_t termios_size = sizeof(termios);
    env->GetByteArrayRegion(storedData, 0, termios_size, (jbyte*) &data);

    if (tcsetattr(fd, TCSADRAIN, &data) != 0) {
        throw_errno(env);
    }
}

JNIEXPORT jint JNICALL Java_jdk_internal_console_NativeConsoleReader_terminalWidth
  (JNIEnv *env, jclass) {
    int fd = 0;
    winsize ws;

    if (ioctl(fd, TIOCGWINSZ, &ws) != 0) {
        throw_errno(env);
        return -1;
    }

    return ws.ws_col;
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
