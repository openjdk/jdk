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

#include "stdhdrs.h"
#include "alloc.h"
#include "awt_Debug.h"
#include "UnicowsLoader.h"

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
 * IS_NT returns TRUE on NT, 2000, XP
 * IS_WIN2000 returns TRUE on 2000, XP
 * IS_WINXP returns TRUE on XP
 * IS_WIN95 returns TRUE on 95, 98, ME
 * IS_WIN98 returns TRUE on 98, ME
 * IS_WINME returns TRUE on ME
 * IS_WIN32 returns TRUE on 32-bit Pentium and
 * 64-bit Itanium.
 * IS_WIN64 returns TRUE on 64-bit Itanium
 *
 * uname -s returns Windows_95 on 95
 * uname -s returns Windows_98 on 98 and ME
 * uname -s returns Windows_NT on NT and 2000 and XP
 */
#if defined (WIN32)
    #define IS_WIN32 TRUE
#else
    #define IS_WIN32 FALSE
#endif
#if defined (_WIN64)
    #define IS_WIN64 TRUE
#else
    #define IS_WIN64 FALSE
#endif
#define IS_NT      (IS_WIN32 && !(::GetVersion() & 0x80000000))
#define IS_WIN2000 (IS_NT && LOBYTE(LOWORD(::GetVersion())) >= 5)
#define IS_WINXP   (IS_NT && (IS_WIN2000 && HIBYTE(LOWORD(::GetVersion())) >= 1) || LOBYTE(LOWORD(::GetVersion())) > 5)
#define IS_WINVISTA (IS_NT && LOBYTE(LOWORD(::GetVersion())) >= 6)
#define IS_WIN32S  (IS_WIN32 && !IS_NT && LOBYTE(LOWORD(::GetVersion())) < 4)
#define IS_WIN95   (IS_WIN32 && !IS_NT && LOBYTE(LOWORD(::GetVersion())) >= 4)
#define IS_WIN98   (IS_WIN95 && HIBYTE(LOWORD(::GetVersion())) >= 10)
#define IS_WINME   (IS_WIN95 && HIBYTE(LOWORD(::GetVersion())) >= 90)
#define IS_WIN4X   (IS_WIN32 && LOBYTE(::GetVersion()) >= 4)
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
   unsigned int fpu_cw = _CW_DEFAULT;   \
   if (IS_WIN95) {  \
       fpu_cw = _control87(0, 0);  \
   }

#define RESTORE_CONTROLWORD   \
   if (IS_WIN95) { \
       if ( _control87(0, 0) != fpu_cw) {  \
              _control87(fpu_cw, 0xfffff);   \
       }   \
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
