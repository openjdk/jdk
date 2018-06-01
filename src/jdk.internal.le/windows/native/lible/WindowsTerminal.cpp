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
#include "jdk_internal_jline_WindowsTerminal.h"

#include <stdlib.h>
#include <Wincon.h>
#include <Winuser.h>

static jclass recordClass;
static jmethodID recordConstructor;
static jclass bufferStateClass;
static jmethodID bufferStateConstructor;

JNIEXPORT void JNICALL Java_jdk_internal_jline_WindowsTerminal_initIDs
  (JNIEnv *env, jclass) {
    jclass cls = env->FindClass("jdk/internal/jline/WindowsTerminal$KEY_EVENT_RECORD");
    CHECK_NULL(cls);
    recordClass = (jclass) env->NewGlobalRef(cls);
    CHECK_NULL(recordClass);
    recordConstructor = env->GetMethodID(cls, "<init>", "(ZCIII)V");
    CHECK_NULL(recordConstructor);
    cls = env->FindClass("jdk/internal/jline/extra/AnsiInterpretingOutputStream$BufferState");
    CHECK_NULL(cls);
    bufferStateClass = (jclass) env->NewGlobalRef(cls);
    CHECK_NULL(bufferStateClass);
    bufferStateConstructor = env->GetMethodID(cls, "<init>", "(IIII)V");
    CHECK_NULL(bufferStateConstructor);
}

JNIEXPORT jint JNICALL Java_jdk_internal_jline_WindowsTerminal_getConsoleMode
  (JNIEnv *, jobject) {
    HANDLE hStdIn;
    if ((hStdIn = GetStdHandle(STD_INPUT_HANDLE)) == INVALID_HANDLE_VALUE) {
        return -1;
    }
    DWORD fdwMode;
    if (! GetConsoleMode(hStdIn, &fdwMode)) {
        return -1;
    }
    return fdwMode;
}

JNIEXPORT void JNICALL Java_jdk_internal_jline_WindowsTerminal_setConsoleMode
  (JNIEnv *, jobject, jint mode) {
    HANDLE hStdIn;
    if ((hStdIn = GetStdHandle(STD_INPUT_HANDLE)) == INVALID_HANDLE_VALUE) {
        return ;
    }
    DWORD fdwMode = mode;
    SetConsoleMode(hStdIn, fdwMode);
}

JNIEXPORT jobject JNICALL Java_jdk_internal_jline_WindowsTerminal_readKeyEvent
  (JNIEnv *env, jobject) {
    HANDLE hStdIn;
    if ((hStdIn = GetStdHandle(STD_INPUT_HANDLE)) == INVALID_HANDLE_VALUE) {
        return NULL;
    }
    INPUT_RECORD record;
    DWORD n;
    while (TRUE) {
        if (ReadConsoleInputW(hStdIn, &record, 1, &n) == 0) {
            return NULL;
        }
        if (record.EventType == KEY_EVENT) {
            return env->NewObject(recordClass,
                                  recordConstructor,
                                  record.Event.KeyEvent.bKeyDown,
                                  record.Event.KeyEvent.uChar.UnicodeChar,
                                  record.Event.KeyEvent.dwControlKeyState,
                                  record.Event.KeyEvent.wVirtualKeyCode,
                                  record.Event.KeyEvent.wRepeatCount);
        }
        continue;
    }
}

JNIEXPORT jint JNICALL Java_jdk_internal_jline_WindowsTerminal_getConsoleOutputCodepage
  (JNIEnv *, jobject) {
    return GetConsoleOutputCP();
}

JNIEXPORT jint JNICALL Java_jdk_internal_jline_WindowsTerminal_getWindowsTerminalWidth
  (JNIEnv *, jobject) {
    HANDLE hStdOut;
    if ((hStdOut = GetStdHandle(STD_OUTPUT_HANDLE)) == INVALID_HANDLE_VALUE) {
        return -1;
    }
    CONSOLE_SCREEN_BUFFER_INFO info;
    if (! GetConsoleScreenBufferInfo(hStdOut, &info)) {
        return -1;
    }
    return info.srWindow.Right - info.srWindow.Left;
}

JNIEXPORT jint JNICALL Java_jdk_internal_jline_WindowsTerminal_getWindowsTerminalHeight
  (JNIEnv *, jobject) {
    HANDLE hStdOut;
    if ((hStdOut = GetStdHandle(STD_OUTPUT_HANDLE)) == INVALID_HANDLE_VALUE) {
        return -1;
    }
    CONSOLE_SCREEN_BUFFER_INFO info;
    if (! GetConsoleScreenBufferInfo(hStdOut, &info)) {
        return -1;
    }
    return info.srWindow.Bottom - info.srWindow.Top + 1;
}

JNIEXPORT jobject JNICALL Java_jdk_internal_jline_WindowsTerminal_getBufferState
  (JNIEnv *env, jobject) {
    HANDLE hStdOut;
    if ((hStdOut = GetStdHandle(STD_OUTPUT_HANDLE)) == INVALID_HANDLE_VALUE) {
        return NULL;
    }
    CONSOLE_SCREEN_BUFFER_INFO info;
    if (! GetConsoleScreenBufferInfo(hStdOut, &info)) {
        return NULL;
    }
    return env->NewObject(bufferStateClass,
                          bufferStateConstructor,
                          info.dwCursorPosition.X,
                          info.dwCursorPosition.Y,
                          info.dwSize.X,
                          info.dwSize.Y);
}

JNIEXPORT void JNICALL Java_jdk_internal_jline_WindowsTerminal_setCursorPosition
  (JNIEnv *, jobject, jint x, jint y) {
    HANDLE hStdOut;
    if ((hStdOut = GetStdHandle(STD_OUTPUT_HANDLE)) == INVALID_HANDLE_VALUE) {
        return ;
    }
    COORD coord = {(SHORT) x, (SHORT) y};
    SetConsoleCursorPosition(hStdOut, coord);
}
