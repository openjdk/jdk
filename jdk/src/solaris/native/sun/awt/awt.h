/*
 * Copyright (c) 1995, 2005, Oracle and/or its affiliates. All rights reserved.
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
 * Common AWT definitions
 */

#ifndef _AWT_
#define _AWT_

#include "jvm.h"
#include "jni_util.h"
#include "debug_util.h"

#ifndef HEADLESS
#include <X11/Intrinsic.h>
#endif /* !HEADLESS */


/* The JVM instance: defined in awt_MToolkit.c */
extern JavaVM *jvm;

extern jclass tkClass;
extern jmethodID awtLockMID;
extern jmethodID awtUnlockMID;
extern jmethodID awtWaitMID;
extern jmethodID awtNotifyMID;
extern jmethodID awtNotifyAllMID;
extern jboolean awtLockInited;

/* Perform sanity and consistency checks on AWT locking */
#ifdef DEBUG
#define DEBUG_AWT_LOCK
#endif

/*
 * The following locking primitives should be defined
 *
#define AWT_LOCK()
#define AWT_NOFLUSH_UNLOCK()
#define AWT_WAIT(tm)
#define AWT_NOTIFY()
#define AWT_NOTIFY_ALL()
 */

/*
 * Convenience macros based on AWT_NOFLUSH_UNLOCK
 */
extern void awt_output_flush();
#define AWT_UNLOCK() AWT_FLUSH_UNLOCK()
#define AWT_FLUSH_UNLOCK() do {                 \
    awt_output_flush();                         \
    AWT_NOFLUSH_UNLOCK();                       \
} while (0)

#define AWT_LOCK_IMPL() \
    (*env)->CallStaticVoidMethod(env, tkClass, awtLockMID)
#define AWT_NOFLUSH_UNLOCK_IMPL() \
    (*env)->CallStaticVoidMethod(env, tkClass, awtUnlockMID)
#define AWT_WAIT_IMPL(tm) \
    (*env)->CallStaticVoidMethod(env, tkClass, awtWaitMID, (jlong)(tm))
#define AWT_NOTIFY_IMPL() \
    (*env)->CallStaticVoidMethod(env, tkClass, awtNotifyMID)
#define AWT_NOTIFY_ALL_IMPL() \
    (*env)->CallStaticVoidMethod(env, tkClass, awtNotifyAllMID)

/*
 * Unfortunately AWT_LOCK debugging does not work with XAWT due to mixed
 * Java/C use of AWT lock.
 */
#if defined(DEBUG_AWT_LOCK) && !defined(XAWT)
extern int awt_locked;
extern char *lastF;
extern int lastL;

#define AWT_LOCK() do {                                                 \
    if (!awtLockInited) {                                               \
        jio_fprintf(stderr, "AWT lock error, awt_lock is null\n");      \
    }                                                                   \
    if (awt_locked < 0) {                                               \
        jio_fprintf(stderr,                                             \
                    "AWT lock error (%s,%d) (last held by %s,%d) %d\n", \
                    __FILE__, __LINE__, lastF, lastL, awt_locked);      \
    }                                                                   \
    lastF = __FILE__;                                                   \
    lastL = __LINE__;                                                   \
    AWT_LOCK_IMPL();                                                    \
    ++awt_locked;                                                       \
} while (0)

#define AWT_NOFLUSH_UNLOCK() do {                               \
    lastF = "";                                                 \
    lastL = -1;                                                 \
    if (awt_locked < 1) {                                       \
        jio_fprintf(stderr, "AWT unlock error (%s,%d,%d)\n",    \
                    __FILE__, __LINE__, awt_locked);            \
    }                                                           \
    --awt_locked;                                               \
    AWT_NOFLUSH_UNLOCK_IMPL();                                  \
} while (0)

#define AWT_WAIT(tm) do {                                       \
    int old_lockcount = awt_locked;                             \
    if (awt_locked < 1) {                                       \
        jio_fprintf(stderr, "AWT wait error (%s,%d,%d)\n",      \
                    __FILE__, __LINE__, awt_locked);            \
    }                                                           \
    awt_locked = 0;                                             \
    AWT_WAIT_IMPL(tm);                                          \
    awt_locked = old_lockcount;                                 \
} while (0)

#define AWT_NOTIFY() do {                                       \
    if (awt_locked < 1) {                                       \
        jio_fprintf(stderr, "AWT notify error (%s,%d,%d)\n",    \
                    __FILE__, __LINE__, awt_locked);            \
    }                                                           \
    AWT_NOTIFY_IMPL();                                          \
} while(0)

#define AWT_NOTIFY_ALL() do {                                           \
    if (awt_locked < 1) {                                               \
        jio_fprintf(stderr, "AWT notify all error (%s,%d,%d)\n",        \
                    __FILE__, __LINE__, awt_locked);                    \
    }                                                                   \
    AWT_NOTIFY_ALL_IMPL();                                              \
} while (0)

#else

#define AWT_LOCK()           AWT_LOCK_IMPL()
#define AWT_NOFLUSH_UNLOCK() AWT_NOFLUSH_UNLOCK_IMPL()
#define AWT_WAIT(tm)         AWT_WAIT_IMPL(tm)
#define AWT_NOTIFY()         AWT_NOTIFY_IMPL()
#define AWT_NOTIFY_ALL()     AWT_NOTIFY_ALL_IMPL()

#endif /* DEBUG_AWT_LOCK && !XAWT */

#ifndef HEADLESS
extern Display         *awt_display;            /* awt_GraphicsEnv.c */
extern XtAppContext     awt_appContext;         /* awt_MToolkit.c */
extern Widget           awt_root_shell;
extern Pixel            awt_defaultBg;
extern Pixel            awt_defaultFg;
extern int              awt_multiclick_time;    /* awt_MToolkit.c */
extern int              awt_multiclick_smudge;  /* canvas.c */
extern unsigned int     awt_MetaMask;           /* awt_MToolkit.c */
extern unsigned int     awt_AltMask;
extern unsigned int     awt_NumLockMask;
extern unsigned int     awt_ModeSwitchMask;
extern Cursor           awt_scrollCursor;       /* awt_MToolkit.c */
extern Boolean          awt_ModLockIsShiftLock;

#endif /* !HEADLESS */

#endif /* ! _AWT_ */
