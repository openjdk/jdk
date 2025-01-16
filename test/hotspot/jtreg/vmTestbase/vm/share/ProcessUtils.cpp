/*
 * Copyright (c) 2007, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
#ifdef _WIN32
#include <windows.h>
#else /* _WIN32 */
#include <unistd.h>
#include <signal.h>
#endif /* _WIN32 */
#include "jni_tools.hpp"

extern "C" {

/*
 * Class:     vm_share_ProcessUtils
 * Method:    sendCtrlBreak
 * Signature: ()Z
 */
JNIEXPORT jboolean JNICALL Java_vm_share_ProcessUtils_sendCtrlBreak
(JNIEnv *env, jclass klass) {
#ifdef _WIN32
        int dw;
        LPVOID lpMsgBuf;
        if (!GenerateConsoleCtrlEvent(CTRL_BREAK_EVENT, 0)) {
                dw = GetLastError();
                FormatMessage(FORMAT_MESSAGE_ALLOCATE_BUFFER | FORMAT_MESSAGE_FROM_SYSTEM,
                                nullptr,
                                dw,
                                MAKELANGID(LANG_NEUTRAL, SUBLANG_DEFAULT),
                                (LPTSTR) &lpMsgBuf,
                                0,
                                nullptr
                             );
                printf("%s\n", (LPTSTR)lpMsgBuf);
                LocalFree(lpMsgBuf);
                return JNI_FALSE;
        }
        return JNI_TRUE;
#else /* _WIN32 */
        if (kill(getpid(), SIGQUIT) < 0)
                return JNI_FALSE;
        return JNI_TRUE;
#endif /* _WIN32 */
}

}
