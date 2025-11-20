/*
 * Copyright (c) 2007, 2025, Oracle and/or its affiliates. All rights reserved.
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
#pragma once

#ifdef DEBUG
#define D3D_DEBUG_INFO
#endif // DEBUG

// this include ensures that with debug build we get
// awt's overridden debug "new" and "delete" operators
#include "awt.h"

#include <windows.h>
#include <d3d9.h>
#include "Trace.h"

#define DebugPrintD3DError(res, msg) \
    J2dTraceLn(J2D_TRACE_ERROR, "D3D Error: " msg " res=%d", res)

// some helper macros
#define SAFE_RELEASE(RES) \
do {                      \
    if ((RES)!= NULL) {   \
        (RES)->Release(); \
        (RES) = NULL;     \
    }                     \
} while (0);

#define SAFE_DELETE(RES)  \
do {                      \
    if ((RES)!= NULL) {   \
        delete (RES);     \
        (RES) = NULL;     \
    }                     \
} while (0);

#ifdef DEBUG
#define SAFE_PRINTLN(RES) \
do {                      \
    if ((RES)!= NULL) {   \
        J2dTraceLn(J2D_TRACE_VERBOSE, "  " #RES "=0x%x", (RES)); \
    } else {              \
        J2dTraceLn(J2D_TRACE_VERBOSE, "  " #RES "=NULL"); \
    }                     \
} while (0);
#else // DEBUG
#define SAFE_PRINTLN(RES)
#endif // DEBUG

/*
 * The following macros allow the caller to return (or continue) if the
 * provided value is NULL.  (The strange else clause is included below to
 * allow for a trailing ';' after RETURN/CONTINUE_IF_NULL() invocations.)
 */
#define ACT_IF_NULL(ACTION, value)         \
    if ((value) == NULL) {                 \
        J2dTraceLn(J2D_TRACE_ERROR,        \
                   "%s is null in %s:%d", #value, __FILE__, __LINE__); \
        ACTION;                            \
    } else do { } while (0)
#define RETURN_IF_NULL(value)   ACT_IF_NULL(return, value)
#define CONTINUE_IF_NULL(value) ACT_IF_NULL(continue, value)
#define RETURN_STATUS_IF_NULL(value, status) \
        ACT_IF_NULL(return (status), value)

#define RETURN_STATUS_IF_EXP_FAILED(EXPR) \
    if (FAILED(res = (EXPR))) {                    \
        DebugPrintD3DError(res, " " #EXPR " failed in " __FILE__); \
        return res;                   \
    } else do { } while (0)

#define RETURN_STATUS_IF_FAILED(status) \
    if (FAILED((status))) {                    \
        DebugPrintD3DError((status), " failed in " __FILE__ ", return;");\
        return (status);                   \
    } else do { } while (0)
