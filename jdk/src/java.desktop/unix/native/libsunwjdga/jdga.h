/*
 * Copyright (c) 1998, 2001, Oracle and/or its affiliates. All rights reserved.
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
 * The JDGA interface enables "Direct Graphics Access" to the pixels
 * of X11 drawables for the Java runtime graphics implementation.
 *
 * This include file defines the external interface that the
 * Solaris X11 port of the Java(tm) 2D API uses to communicate
 * with a dynamically loadable object library to obtain information
 * for rendering directly to the memory mapped surfaces that store
 * the pixel information for an X11 Window (or technically any X11
 * Drawable).
 *
 * The 2D graphics library will link to an object file, either
 * through direct linking at compile time or through dynamic
 * loading at runtime, and use an entry point defined as
 *
 *      JDgaLibInitFunc JDgaLibInit;
 *
 * to initialize the library and obtain a copy of a JDgaLibInfo
 * structure that will be used to communicate with the library
 * to obtain information about X11 Drawable IDs and the memory
 * used to store their pixels.
 *
 * Some parts of this interface use interfaces and structures
 * defined by the JNI native interface technology.
 */

#ifndef HEADLESS
/*
 *
 */
#define JDGALIB_MAJOR_VERSION 1
#define JDGALIB_MINOR_VERSION 0

/*
 * Definitions for the return status codes for most of the JDGA
 * access functions.
 */
#ifndef _DEFINE_JDGASTATUS_
#define _DEFINE_JDGASTATUS_
typedef enum {
    JDGA_SUCCESS        = 0,    /* operation succeeded */
    JDGA_FAILED         = 1,     /* unable to complete operation */
    JDGA_UNAVAILABLE    = 2     /* DGA not available on attached devices */
} JDgaStatus;
#endif

/*
 * This structure defines the location and size of a rectangular
 * region of a drawing surface.
 *
 *      lox, loy - coordinates that point to the pixel just inside
 *                      the top left-hand corner of the region.
 *      hix, hiy - coordinates that point to the pixel just beyond
 *                      the bottom right-hand corner of the region.
 *
 * Thus, the region is a rectangle containing (hiy-loy) rows of
 * (hix-lox) columns of pixels.
 */
typedef struct {
    jint        lox;
    jint        loy;
    jint        hix;
    jint        hiy;
} JDgaBounds;

typedef struct {
    /*
     * Information describing the global memory partition containing
     * the pixel information for the window.
     */
    void        *basePtr;       /* Base address of memory partition. */
    jint        surfaceScan;    /* Number of pixels from one row to the next */
    jint        surfaceWidth;   /* Total accessible pixels across */
    jint        surfaceHeight;  /* Total accessible pixels down */
    jint        surfaceDepth;   /* Mapped depth */

    /*
     * Location and size information of the entire window (may include
     * portions outside of the memory partition).
     *
     * The coordinates are relative to the "basePtr" origin of the screen.
     */
    JDgaBounds  window;

    /*
     * Location and size information of the visible portion of the
     * window (includes only portions that are inside the writable
     * portion of the memory partition and not covered by other windows)
     *
     * This rectangle may represent a subset of the rendering
     * rectangle supplied in the JDgaGetLock function if that
     * rectangle is partially clipped and the remaining visible
     * portion is exactly rectangular.
     *
     * The coordinates are relative to the "basePtr" origin of the screen.
     */
    JDgaBounds  visible;

} JDgaSurfaceInfo;

typedef struct _JDgaLibInfo JDgaLibInfo;

/*
 * This function is called to initialize the JDGA implementation
 * library for access to the given X11 Display.
 * This function stores a pointer to a structure that holds function
 * pointers for the rest of the requests as well as any additinoal
 * data that that library needs to track the indicated display.
 *
 * @return
 *      JDGA_SUCCESS if library was successfully initialized
 *      JDGA_FAILED if library is unable to perform operations
 *              on the given X11 Display.
 */
typedef JDgaStatus
JDgaLibInitFunc(JNIEnv *env, JDgaLibInfo *ppInfo);

/*
 * This function is called to lock the given X11 Drawable into
 * a locally addressable memory location and to return specific
 * rendering information about the location and geometry of the
 * display memory that the Drawable occupies.
 *
 * Information provided to this function includes:
 *
 *      lox, loy - the X and Y coordinates of the pixel just inside
 *              the upper left corner of the region to be rendered
 *      hix, hiy - the X and Y coordinates of the pixel just beyond
 *              the lower right corner of the region to be rendered
 *
 * Information obtained via this function includes:
 *
 *      *pSurface - A pointer to a JDgaSurfaceInfo structure which is
 *              filled in with information about the drawing area for
 *              the specified Drawable.
 *
 * The return value indicates whether or not the library was able
 * to successfully lock the drawable into memory and obtain the
 * specific geometry information required to render to the Drawable's
 * pixel memory.  Failure indicates only a temporary inability to
 * lock down the memory for this Drawable and does not imply a general
 * inability to lock this or other Drawable's at a later time.
 *
 * If the indicated rendering region is not visible at all then this
 * function should indicate JDGA_SUCCESS and return an empty
 * "visible" rectangle.
 * If the indicated rendering region has a visible portion that cannot
 * be expressed as a single rectangle in the JDgaSurfaceInfo structure
 * then JDGA_FAILED should be indicated so that the rendering library
 * can back off to another rendering mechanism.
 *
 * @return
 *      JDGA_SUCCESS memory successfully locked and described
 *      JDGA_FAILED temporary failure to lock the specified Drawable
 */
typedef JDgaStatus
JDgaGetLockFunc(JNIEnv *env, Display *display, void **dgaDev,
                    Drawable d, JDgaSurfaceInfo *pSurface,
                    jint lox, jint loy, jint hix, jint hiy);

/*
 * This function is called to unlock the locally addressable memory
 * associated with the given X11 Drawable until the next rendering
 * operation.  The JDgaSurfaceInfo structure supplied is the same
 * structure that was supplied in the dga_get_lock function and
 * can be used to determine implementation specific data needed to
 * manage the access lock for the indicated drawable.
 *
 * The return value indicates whether or not the library was able
 * to successfully remove its lock.  Typically failure indicates
 * only that the lock had been invalidated through external means
 * before the rendering library completed its work and is for
 * informational purposes only, though it could also mean that
 * the rendering library asked to unlock a Drawable that it had
 * never locked.
 *
 * @return
 *      JDGA_SUCCESS lock successfully released
 *      JDGA_FAILED unable to release lock for some reason,
 *              typically the lock was already invalid
 */
typedef JDgaStatus
JDgaReleaseLockFunc(JNIEnv *env, void *dgaDev, Drawable d);

/*
 * This function is called to inform the JDGA library that the
 * AWT rendering library has enqueued an X11 request for the
 * indicated Drawable.  The JDGA library will have to synchronize
 * the X11 output buffer with the server before this drawable
 * is again locked in order to prevent race conditions between
 * the rendering operations in the X11 queue and the rendering
 * operations performed directly between calls to the GetLockFunc
 * and the ReleaseLockFunc.
 */
typedef void
JDgaXRequestSentFunc(JNIEnv *env, void *dgaDev, Drawable d);

/*
 * This function is called to shut down a JDGA library implementation
 * and dispose of any resources that it is using for a given display.
 *
 */

typedef void
JDgaLibDisposeFunc(JNIEnv *env);

struct _JDgaLibInfo {
    /*
     * The X11 display structure that this instance of JDgaLibInfo
     * structure is tracking.
     */
    Display                     *display;

    /*
     * Pointers to the utility functions to query information about
     * X11 drawables and perform synchronization on them.
     */
    JDgaGetLockFunc             *pGetLock;
    JDgaReleaseLockFunc         *pReleaseLock;
    JDgaXRequestSentFunc        *pXRequestSent;
    JDgaLibDisposeFunc          *pLibDispose;

    /*
     * Since the JDGA library is responsible for allocating this
     * structure, implementation specific information can be tracked
     * by the library by declaring its own structure that contains
     * data following the above members.
     */
};
#endif /* !HEADLESS */
