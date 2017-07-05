/*
 * Copyright (c) 2005, 2015, Oracle and/or its affiliates. All rights reserved.
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

/*
 * A class to manage AccessBridge debugging
 */

#include "AccessBridgeDebug.h"
#include <stdarg.h>
#include <stdio.h>
#include <windows.h>

#ifdef __cplusplus
extern "C" {
#endif

    /**
     * Send debugging info to the appropriate place
     */
    void PrintDebugString(char *msg, ...) {
#ifdef DEBUGGING_ON
        char buf[1024];
        va_list argprt;

        va_start(argprt, msg);     // set up argptr
        vsprintf(buf, msg, argprt);
#ifdef SEND_TO_OUTPUT_DEBUG_STRING
        OutputDebugString(buf);
#endif
#ifdef SEND_TO_CONSOLE
        printf(buf);
        printf("\r\n");
#endif
#endif
    }

    /**
     * Send Java debugging info to the appropriate place
     */
    void PrintJavaDebugString2(char *msg, ...) {
#ifdef JAVA_DEBUGGING_ON
        char buf[1024];
        va_list argprt;

        va_start(argprt, msg);     // set up argptr
        vsprintf(buf, msg, argprt);
#ifdef SEND_TO_OUTPUT_DEBUG_STRING
        OutputDebugString(buf);
#endif
#ifdef SEND_TO_CONSOLE
        printf(buf);
        printf("\r\n");
#endif
#endif
    }
    /**
     * Wide version of the method to send debugging info to the appropriate place
     */
    void wPrintDebugString(wchar_t *msg, ...) {
#ifdef DEBUGGING_ON
        char buf[1024];
        char charmsg[256];
        va_list argprt;

        va_start(argprt, msg);          // set up argptr
        sprintf(charmsg, "%ls", msg);  // convert format string to multi-byte
        vsprintf(buf, charmsg, argprt);
#ifdef SEND_TO_OUTPUT_DEBUG_STRING
        OutputDebugString(buf);
#endif
#ifdef SEND_TO_CONSOLE
        printf(buf);
        printf("\r\n");
#endif
#endif
    }

    /**
     * Wide version of the method to send Java debugging info to the appropriate place
     */
    void wPrintJavaDebugString(wchar_t *msg, ...) {
#ifdef JAVA_DEBUGGING_ON
        char buf[1024];
        char charmsg[256];
        va_list argprt;

        va_start(argprt, msg);          // set up argptr
        sprintf(charmsg, "%ls", msg);  // convert format string to multi-byte
        vsprintf(buf, charmsg, argprt);
#ifdef SEND_TO_OUTPUT_DEBUG_STRING
        OutputDebugString(buf);
#endif
#ifdef SEND_TO_CONSOLE
        printf(buf);
        printf("\r\n");
#endif
#endif
    }
#ifdef __cplusplus
}
#endif
