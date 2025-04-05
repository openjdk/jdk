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
#include "jdk_internal_console_WindowsTerminal.h"

#include <stdlib.h>
#include <Windows.h>

static jclass lastErrorExceptionClass;
static jmethodID lastErrorExceptionConstructor;

static jclass KEY_EVENT_Class;
static jmethodID KEY_EVENT_Constructor;

static jclass WINDOW_SIZE_EVENT_Class;
static jmethodID WINDOW_SIZE_EVENT_Constructor;

static void throw_errno(JNIEnv *env);

JNIEXPORT void JNICALL Java_jdk_internal_console_WindowsTerminal_initIDs
  (JNIEnv *env, jclass) {
    jclass cls;
    cls = env->FindClass("jdk/internal/console/LastErrorException");
    CHECK_NULL(cls);
    lastErrorExceptionClass = (jclass) env->NewGlobalRef(cls);
    lastErrorExceptionConstructor = env->GetMethodID(lastErrorExceptionClass, "<init>", "(J)V");
    CHECK_NULL(lastErrorExceptionConstructor);
    KEY_EVENT_Class = (jclass) env->NewGlobalRef(env->FindClass("jdk/internal/console/WindowsTerminal$KeyEvent"));
    CHECK_NULL(KEY_EVENT_Class);
    KEY_EVENT_Constructor = env->GetMethodID(KEY_EVENT_Class, "<init>", "(ZSCI)V");
    CHECK_NULL(KEY_EVENT_Constructor);
    WINDOW_SIZE_EVENT_Class = (jclass) env->NewGlobalRef(env->FindClass("jdk/internal/console/WindowsTerminal$WindowSizeEvent"));
    CHECK_NULL(WINDOW_SIZE_EVENT_Class);
    WINDOW_SIZE_EVENT_Constructor = env->GetMethodID(WINDOW_SIZE_EVENT_Class, "<init>", "()V");
    CHECK_NULL(WINDOW_SIZE_EVENT_Constructor);
}

JNIEXPORT jbyteArray JNICALL Java_jdk_internal_console_WindowsTerminal_switchToRaw
  (JNIEnv *env, jclass) {
    HANDLE inHandle = GetStdHandle(STD_INPUT_HANDLE);
    DWORD origInMode;

    if (!GetConsoleMode(inHandle, &origInMode)) {
        throw_errno(env);
        return NULL;
    }

    HANDLE outHandle = GetStdHandle(STD_OUTPUT_HANDLE);
    DWORD origOutMode;

    if (!GetConsoleMode(outHandle, &origOutMode)) {
        throw_errno(env);
        return NULL;
    }

    if (!SetConsoleMode(inHandle, ENABLE_PROCESSED_INPUT)) {
        throw_errno(env);
        return NULL;
    }

    if (!SetConsoleMode(outHandle, ENABLE_VIRTUAL_TERMINAL_PROCESSING | ENABLE_PROCESSED_OUTPUT)) {
        throw_errno(env);
        return NULL;
    }

    jsize dword_size = (jsize) sizeof(DWORD);
    jbyteArray result = env->NewByteArray(2 * dword_size);

    env->SetByteArrayRegion(result, 0, dword_size, (jbyte *) &origInMode);
    env->SetByteArrayRegion(result, dword_size, dword_size, (jbyte *) &origOutMode);

    return result;
}

JNIEXPORT void JNICALL Java_jdk_internal_console_WindowsTerminal_restore
  (JNIEnv *env, jclass, jbyteArray storedData) {
    jsize dword_size = (jsize) sizeof(DWORD);
    DWORD origInMode;
    DWORD origOutMode;

    env->GetByteArrayRegion(storedData, 0, dword_size, (jbyte*) &origInMode);
    env->GetByteArrayRegion(storedData, dword_size, dword_size, (jbyte*) &origOutMode);

    HANDLE inHandle = GetStdHandle(STD_INPUT_HANDLE);

    if (!SetConsoleMode(inHandle, origInMode)) {
        throw_errno(env);
        return ;
    }

    HANDLE outHandle = GetStdHandle(STD_OUTPUT_HANDLE);

    if (!SetConsoleMode(outHandle, origOutMode)) {
        throw_errno(env);
        return ;
    }
}

JNIEXPORT jint JNICALL Java_jdk_internal_console_WindowsTerminal_terminalWidth
  (JNIEnv *env, jclass) {
    HANDLE h = GetStdHandle(STD_OUTPUT_HANDLE);
    CONSOLE_SCREEN_BUFFER_INFO buffer;
    if (!GetConsoleScreenBufferInfo(h, &buffer)) {
        DWORD error = GetLastError();
        jobject exc = env->NewObject(lastErrorExceptionClass,
                                     lastErrorExceptionConstructor,
                                     (jlong) error);
        env->Throw((jthrowable) exc);
        return -1;
    }
    return buffer.dwSize.X;
}

JNIEXPORT jint JNICALL Java_jdk_internal_console_WindowsTerminal_cursorX
  (JNIEnv *env, jclass) {
    HANDLE h = GetStdHandle(STD_OUTPUT_HANDLE);
    CONSOLE_SCREEN_BUFFER_INFO buffer;
    if (!GetConsoleScreenBufferInfo(h, &buffer)) {
        DWORD error = GetLastError();
        jobject exc = env->NewObject(lastErrorExceptionClass,
                                     lastErrorExceptionConstructor,
                                     (jlong) error);
        env->Throw((jthrowable) exc);
        return -1;
    }
    return buffer.dwCursorPosition.X;
}

JNIEXPORT jobject JNICALL Java_jdk_internal_console_WindowsTerminal_readEvent
  (JNIEnv *env, jclass) {
    HANDLE h = GetStdHandle(STD_INPUT_HANDLE);
    INPUT_RECORD buffer;
    DWORD numberOfEventsRead;
    if (!ReadConsoleInputW(h, &buffer, 1, &numberOfEventsRead)) {
        throw_errno(env);
        return NULL;
    }
    switch (buffer.EventType) {
        case KEY_EVENT: {
                jobject keyEvent = env->NewObject(KEY_EVENT_Class,
                                                  KEY_EVENT_Constructor,
                                                  buffer.Event.KeyEvent.bKeyDown,
                                                  buffer.Event.KeyEvent.wVirtualKeyCode,
                                                  buffer.Event.KeyEvent.uChar.UnicodeChar,
                                                  buffer.Event.KeyEvent.dwControlKeyState);
                return keyEvent;
            }
        case WINDOW_BUFFER_SIZE_EVENT: {
            jobject windowSizeEvent = env->NewObject(WINDOW_SIZE_EVENT_Class,
                                                     WINDOW_SIZE_EVENT_Constructor);

            return windowSizeEvent;
        }
    }
    return NULL;
}

/*
 * Throws LastErrorException based on GetLastError:
 */
static void throw_errno(JNIEnv *env) {
    DWORD error = GetLastError();
    jobject exc = env->NewObject(lastErrorExceptionClass,
                                 lastErrorExceptionConstructor,
                                 (jlong) error);
    env->Throw((jthrowable) exc);
}
