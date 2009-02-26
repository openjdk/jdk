/*
 * Copyright 1996-2008 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

#ifndef _AWT_H_
#define _AWT_H_

#ifndef _WIN32_WINNT
#define _WIN32_WINNT 0x0600
#endif

#ifndef _WIN32_IE
#define _WIN32_IE 0x0600
#endif

//#ifndef NTDDI_VERSION
//#define NTDDI_VERSION NTDDI_LONGHORN
//#endif

#include "stdhdrs.h"
#include "alloc.h"
#include "awt_Debug.h"

extern COLORREF DesktopColor2RGB(int colorIndex);

class AwtObject;
typedef AwtObject* PDATA;

#define JNI_CHECK_NULL_GOTO(obj, msg, where) {                            \
    if (obj == NULL) {                                                    \
        JNU_ThrowNullPointerException(env, msg);                          \
        goto where;                                                       \
    }                                                                     \
}

#define JNI_CHECK_PEER_GOTO(peer, where) {                                \
    JNI_CHECK_NULL_GOTO(peer, "peer", where);                             \
    pData = JNI_GET_PDATA(peer);                                          \
    if (pData == NULL) {                                                  \
        THROW_NULL_PDATA_IF_NOT_DESTROYED(peer);                          \
        goto where;                                                       \
    }                                                                     \
}

#define JNI_CHECK_NULL_RETURN(obj, msg) {                                 \
    if (obj == NULL) {                                                    \
        JNU_ThrowNullPointerException(env, msg);                          \
        return;                                                           \
    }                                                                     \
}

#define JNI_CHECK_PEER_RETURN(peer) {                                     \
    JNI_CHECK_NULL_RETURN(peer, "peer");                                  \
    pData = JNI_GET_PDATA(peer);                                          \
    if (pData == NULL) {                                                  \
        THROW_NULL_PDATA_IF_NOT_DESTROYED(peer);                          \
        return;                                                           \
    }                                                                     \
}

#define JNI_CHECK_PEER_CREATION_RETURN(peer) {                            \
    if (peer == NULL ) {                                                  \
        return;                                                           \
    }                                                                     \
    pData = JNI_GET_PDATA(peer);                                          \
    if (pData == NULL) {                                                  \
        return;                                                           \
    }                                                                     \
}

#define JNI_CHECK_NULL_RETURN_NULL(obj, msg) {                            \
    if (obj == NULL) {                                                    \
        JNU_ThrowNullPointerException(env, msg);                          \
        return 0;                                                         \
    }                                                                     \
}

#define JNI_CHECK_NULL_RETURN_VAL(obj, msg, val) {                        \
    if (obj == NULL) {                                                    \
        JNU_ThrowNullPointerException(env, msg);                          \
        return val;                                                       \
    }                                                                     \
}

#define JNI_CHECK_PEER_RETURN_NULL(peer) {                                \
    JNI_CHECK_NULL_RETURN_NULL(peer, "peer");                             \
    pData = JNI_GET_PDATA(peer);                                          \
    if (pData == NULL) {                                                  \
        THROW_NULL_PDATA_IF_NOT_DESTROYED(peer);                          \
        return 0;                                                         \
    }                                                                     \
}

#define JNI_CHECK_PEER_RETURN_VAL(peer, val) {                            \
    JNI_CHECK_NULL_RETURN_VAL(peer, "peer", val);                         \
    pData = JNI_GET_PDATA(peer);                                          \
    if (pData == NULL) {                                                  \
        THROW_NULL_PDATA_IF_NOT_DESTROYED(peer);                          \
        return val;                                                       \
    }                                                                     \
}

#define THROW_NULL_PDATA_IF_NOT_DESTROYED(peer) {                         \
    jboolean destroyed = JNI_GET_DESTROYED(peer);                         \
    if (destroyed != JNI_TRUE) {                                          \
        JNU_ThrowNullPointerException(env, "null pData");                 \
    }                                                                     \
}

#define JNI_GET_PDATA(peer) (PDATA) env->GetLongField(peer, AwtObject::pDataID)
#define JNI_GET_DESTROYED(peer) env->GetBooleanField(peer, AwtObject::destroyedID)

#define JNI_SET_PDATA(peer, data) env->SetLongField(peer,                  \
                                                    AwtObject::pDataID,    \
                                                    (jlong)data)
#define JNI_SET_DESTROYED(peer) env->SetBooleanField(peer,                   \
                                                     AwtObject::destroyedID, \
                                                     JNI_TRUE)
/*  /NEW JNI */

/*
 * IS_WIN64 returns TRUE on 64-bit Itanium
 */
#if defined (_WIN64)
    #define IS_WIN64 TRUE
#else
    #define IS_WIN64 FALSE
#endif

/*
 * IS_WIN2000 returns TRUE on 2000, XP and Vista
 * IS_WINXP returns TRUE on XP and Vista
 * IS_WINVISTA returns TRUE on Vista
 */
#define IS_WIN2000 (LOBYTE(LOWORD(::GetVersion())) >= 5)
#define IS_WINXP ((IS_WIN2000 && HIBYTE(LOWORD(::GetVersion())) >= 1) || LOBYTE(LOWORD(::GetVersion())) > 5)
#define IS_WINVISTA (LOBYTE(LOWORD(::GetVersion())) >= 6)

#define IS_WINVER_ATLEAST(maj, min) \
                   ((maj) < LOBYTE(LOWORD(::GetVersion())) || \
                      (maj) == LOBYTE(LOWORD(::GetVersion())) && \
                      (min) <= HIBYTE(LOWORD(::GetVersion())))

/*
 * macros to crack a LPARAM into two ints -- used for signed coordinates,
 * such as with mouse messages.
 */
#define LO_INT(l)           ((int)(short)(l))
#define HI_INT(l)           ((int)(short)(((DWORD)(l) >> 16) & 0xFFFF))

extern JavaVM *jvm;

// Platform encoding is Unicode (UTF-16), re-define JNU_ functions
// to proper JNI functions.
#define JNU_NewStringPlatform(env, x) env->NewString(reinterpret_cast<jchar*>(x), static_cast<jsize>(_tcslen(x)))
#define JNU_GetStringPlatformChars(env, x, y) reinterpret_cast<LPCWSTR>(env->GetStringChars(x, y))
#define JNU_ReleaseStringPlatformChars(env, x, y) env->ReleaseStringChars(x, reinterpret_cast<const jchar*>(y))

/*
 * Itanium symbols needed for 64-bit compilation.
 * These are defined in winuser.h in the August 2001 MSDN update.
 */
#ifndef GCLP_HBRBACKGROUND
    #ifdef _WIN64
        #error Macros for GetClassLongPtr, etc. are for 32-bit windows only
    #endif /* !_WIN64 */
    #define GetClassLongPtr GetClassLong
    #define SetClassLongPtr SetClassLong
    #define GCLP_HBRBACKGROUND GCL_HBRBACKGROUND
    #define GCLP_HCURSOR GCL_HCURSOR
    #define GCLP_HICON GCL_HICON
    #define GCLP_HICONSM GCL_HICONSM
    #define GCLP_HMODULE GCL_HMODULE
    #define GCLP_MENUNAME GCL_MENUNAME
    #define GCLP_WNDPROC GCL_WNDPROC
    #define GetWindowLongPtr GetWindowLong
    #define SetWindowLongPtr SetWindowLong
    #define GWLP_WNDPROC GWL_WNDPROC
    #define GWLP_HINSTANCE GWL_HINSTANCE
    #define GWLP_HWNDPARENT GWL_HWNDPARENT
    #define GWLP_ID GWL_ID
    #define GWLP_USERDATA GWL_USERDATA
    #define DWLP_DLGPROC DWL_DLGPROC
    #define DWLP_MSGRESULT DWL_MSGRESULT
    #define DWLP_USER DWL_USER
#endif /* !GCLP_HBRBACKGROUND */

/*
 * macros for saving and restoring FPU control word
 * NOTE: float.h must be defined if using these macros
 */
#define SAVE_CONTROLWORD  \
  unsigned int fpu_cw = _control87(0, 0);

#define RESTORE_CONTROLWORD  \
  if (_control87(0, 0) != fpu_cw) {  \
    _control87(fpu_cw, 0xffffffff);  \
  }

/*
 * checks if the current thread is/isn't the toolkit thread
 */
#if defined(DEBUG) || defined(INTERNAL_BUILD)
#define CHECK_IS_TOOLKIT_THREAD() \
  if (GetCurrentThreadId() != AwtToolkit::MainThread())  \
  { JNU_ThrowInternalError(env,"Operation is not permitted on non-toolkit thread!\n"); }
#define CHECK_ISNOT_TOOLKIT_THREAD()  \
  if (GetCurrentThreadId() == AwtToolkit::MainThread())  \
  { JNU_ThrowInternalError(env,"Operation is not permitted on toolkit thread!\n"); }
#else
#define CHECK_IS_TOOLKIT_THREAD()
#define CHECK_ISNOT_TOOLKIT_THREAD()
#endif

#endif  /* _AWT_H_ */
