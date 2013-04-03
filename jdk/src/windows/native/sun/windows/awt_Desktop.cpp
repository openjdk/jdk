/*
 * Copyright (c) 2005, 2013, Oracle and/or its affiliates. All rights reserved.
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

#include "awt.h"
#include <jni.h>
#include <shellapi.h>
#include <float.h>

#ifdef __cplusplus
extern "C" {
#endif

/*
 * Class:     sun_awt_windows_WDesktopPeer
 * Method:    ShellExecute
 * Signature: (Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_sun_awt_windows_WDesktopPeer_ShellExecute
  (JNIEnv *env, jclass cls, jstring fileOrUri_j, jstring verb_j)
{
    LPCWSTR fileOrUri_c = JNU_GetStringPlatformChars(env, fileOrUri_j, JNI_FALSE);
    LPCWSTR verb_c = JNU_GetStringPlatformChars(env, verb_j, JNI_FALSE);

    // 6457572: ShellExecute possibly changes FPU control word - saving it here
    unsigned oldcontrol87 = _control87(0, 0);
    HINSTANCE retval = ::ShellExecute(NULL, verb_c, fileOrUri_c, NULL, NULL, SW_SHOWNORMAL);
    _control87(oldcontrol87, 0xffffffff);

    JNU_ReleaseStringPlatformChars(env, fileOrUri_j, fileOrUri_c);
    JNU_ReleaseStringPlatformChars(env, verb_j, verb_c);

    if ((int)retval <= 32) {
        // ShellExecute failed.
        LPTSTR buffer = NULL;
        int len = ::FormatMessage(
                    FORMAT_MESSAGE_ALLOCATE_BUFFER |
                    FORMAT_MESSAGE_FROM_SYSTEM  |
                    FORMAT_MESSAGE_IGNORE_INSERTS,
                    NULL,
                    (int)retval,
                    MAKELANGID(LANG_NEUTRAL, SUBLANG_DEFAULT), // Default language
                    (LPTSTR)&buffer,
                    0,
                    NULL );

        if (buffer) {
            jstring errmsg = JNU_NewStringPlatform(env, buffer);
            LocalFree(buffer);
            return errmsg;
        }
    }

    return NULL;
}

#ifdef __cplusplus
}
#endif
