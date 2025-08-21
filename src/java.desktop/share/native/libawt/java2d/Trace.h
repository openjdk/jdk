/*
 * Copyright (c) 2003, 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef _Included_Trace
#define _Included_Trace

#include <jni.h>
#include "debug_trace.h"

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

/**
 * J2dTrace
 * Trace utility used throughout Java 2D code.  Uses a "level"
 * parameter that allows user to specify how much detail
 * they want traced at runtime.  Tracing is only enabled
 * in debug mode, to avoid overhead running release build.
 */

#define J2D_TRACE_INVALID       -1
#define J2D_TRACE_OFF           0
#define J2D_TRACE_ERROR         1
#define J2D_TRACE_WARNING       2
#define J2D_TRACE_INFO          3
#define J2D_TRACE_VERBOSE       4
#define J2D_TRACE_VERBOSE2      5
#define J2D_TRACE_MAX           (J2D_TRACE_VERBOSE2+1)

JNIEXPORT void JNICALL
J2dTraceImpl(int level, jboolean cr, const char *string, ...);

#ifndef DEBUG
#define J2dTrace(level, ...)
#define J2dTraceLn(level, ...)
#else /* DEBUG */
#define J2dTrace(level, ...) \
            J2dTraceImpl(level, JNI_FALSE, __VA_ARGS__)
#define J2dTraceLn(level, ...) \
            J2dTraceImpl(level, JNI_TRUE, __VA_ARGS__)
#endif /* DEBUG */


/**
 * NOTE: Use the following RlsTrace calls very carefully; they are compiled
 * into the code and should thus not be put in any performance-sensitive
 * areas.
 */

#define J2dRlsTrace(level, ...) \
            J2dTraceImpl(level, JNI_FALSE, __VA_ARGS__)
#define J2dRlsTraceLn(level, ...) \
            J2dTraceImpl(level, JNI_TRUE, __VA_ARGS__)

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* _Included_Trace */
