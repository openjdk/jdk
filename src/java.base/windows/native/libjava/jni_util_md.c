/*
 * Copyright (c) 2004, 2024, Oracle and/or its affiliates. All rights reserved.
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

#include <stdlib.h>
#include <string.h>
#include <windows.h>
#include <locale.h>

#include "jni.h"
#include "jni_util.h"

void* getProcessHandle() {
    return (void*)GetModuleHandle(NULL);
}

jstring
getLastErrorString(JNIEnv *env) {

#define BUFSIZE 256
    DWORD errval;
    WCHAR buf[BUFSIZE];

    if ((errval = GetLastError()) != 0) {
        // DOS error
        jsize n = FormatMessageW(
                FORMAT_MESSAGE_FROM_SYSTEM|FORMAT_MESSAGE_IGNORE_INSERTS,
                NULL,
                errval,
                0,
                buf,
                BUFSIZE,
                NULL);
        if (n > 3) {
            // Drop final '.', CR, LF
            if (buf[n - 1] == L'\n') n--;
            if (buf[n - 1] == L'\r') n--;
            if (buf[n - 1] == L'.') n--;
            buf[n] = L'\0';
        }
        jstring s = (*env)->NewString(env, buf, n);
        return s;
    }
    return NULL;
}

JNIEXPORT int JNICALL
getErrorString(int err, char *buf, size_t len)
{
    int ret = 0;
    if (err == 0 || len < 1) return 0;
    ret = strerror_s(buf, len, err);
    return ret;
}
