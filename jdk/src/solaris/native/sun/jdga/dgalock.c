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

#if sparc

/* #define DGA_DEBUG */

#ifdef DGA_DEBUG
#define DEBUG_PRINT(x)  printf x
#else
#define DEBUG_PRINT(x)
#endif

#include <dga/dga.h>
#include <unistd.h>     /* ioctl */
#include <stdlib.h>
#include <sys/mman.h>   /* mmap */
#include <sys/visual_io.h>
#include <string.h>

/* X11 */
#include <X11/Xlib.h>

#include "jni.h"
#include "jdga.h"
#include "jdgadevice.h"

#include <dlfcn.h>

#define min(x, y)       ((x) < (y) ? (x) : (y))
#define max(x, y)       ((x) > (y) ? (x) : (y))

typedef struct _SolarisDgaLibInfo SolarisDgaLibInfo;

struct _SolarisDgaLibInfo {
    /* The general (non-device specific) information */
    unsigned long       count;
    Drawable            drawable;
    Drawable            virtual_drawable;

    /* The device specific memory mapping information */
    SolarisJDgaDevInfo  *devInfo;
    SolarisJDgaWinInfo  winInfo;
};

typedef Bool IsXineramaOnFunc(Display *display);
typedef Drawable GetVirtualDrawableFunc(Display *display, Drawable drawable);

#define MAX_CACHED_INFO 16
static SolarisDgaLibInfo cachedInfo[MAX_CACHED_INFO];
static jboolean needsSync = JNI_FALSE;

#define MAX_FB_TYPES 16
static SolarisJDgaDevInfo devicesInfo[MAX_FB_TYPES];

static IsXineramaOnFunc *IsXineramaOn = NULL;
static GetVirtualDrawableFunc GetVirtualDrawableStub;

Drawable GetVirtualDrawableStub(Display *display, Drawable drawable) {
    return drawable;
}
static GetVirtualDrawableFunc * GetVirtualDrawable = GetVirtualDrawableStub;

static void Solaris_DGA_XineramaInit(Display *display) {
    void * handle = 0;
    if (IsXineramaOn == NULL) {
        handle = dlopen("libxinerama.so", RTLD_NOW);
        if (handle != 0) {
            void *sym = dlsym(handle, "IsXineramaOn");
            IsXineramaOn = (IsXineramaOnFunc *)sym;
            if (IsXineramaOn != 0 && (*IsXineramaOn)(display)) {
                sym = dlsym(handle, "GetVirtualDrawable");
                if (sym != 0) {
                    GetVirtualDrawable = (GetVirtualDrawableFunc *)sym;
                }
            } else {
                dlclose(handle);
            }
        }
    }
}

static SolarisJDgaDevInfo * getDevInfo(Dga_drawable dgadraw) {
    void *handle = 0;
    struct vis_identifier visid;
    int fd;
    char libName[64];
    int i;
    SolarisJDgaDevInfo *curDevInfo = devicesInfo;

    fd = dga_draw_devfd(dgadraw);
    if (ioctl(fd, VIS_GETIDENTIFIER, &visid) != 1) {
        /* check in the devices list */
        for (i = 0; (i < MAX_FB_TYPES) && (curDevInfo->visidName);
             i++, curDevInfo++) {
            if (strcmp(visid.name, curDevInfo->visidName) == 0) {
                /* we already have such a device, return it */
                return curDevInfo;
            }
        }
        if (i == MAX_FB_TYPES) {
            /* we're out of slots, return NULL */
            return NULL;
        }

        strcpy(libName, "libjdga");
        strcat(libName, visid.name);
        strcat(libName,".so");
        /* we use RTLD_NOW because of bug 4032715 */
        handle = dlopen(libName, RTLD_NOW);
        if (handle != 0) {
            JDgaStatus ret = JDGA_FAILED;
            void *sym = dlsym(handle, "SolarisJDgaDevOpen");
            if (sym != 0) {
                curDevInfo->majorVersion = JDGALIB_MAJOR_VERSION;
                curDevInfo->minorVersion = JDGALIB_MINOR_VERSION;
                ret = (*(SolarisJDgaDevOpenFunc *)sym)(curDevInfo);
            }
            if (ret == JDGA_SUCCESS) {
                curDevInfo->visidName = strdup(visid.name);
                return curDevInfo;
            }
            dlclose(handle);
        }
    }
    return NULL;
}
static int
mmap_dgaDev(SolarisDgaLibInfo *libInfo, Dga_drawable dgadraw)
{

    if (!libInfo->devInfo) {
        libInfo->devInfo = getDevInfo(dgadraw);
        if (!libInfo->devInfo) {
            return JDGA_FAILED;
        }
    }
    return (*libInfo->devInfo->function->winopen)(&(libInfo->winInfo));
}

static void
unmap_dgaDev(SolarisDgaLibInfo *pDevInfo)
{
    DEBUG_PRINT(("winclose() called\n"));
   (*pDevInfo->devInfo->function->winclose)(&(pDevInfo->winInfo));
}

static jboolean
Solaris_DGA_Available(Display *display)
{
    Window root;
    int screen;
    Dga_drawable dgaDrawable;
    SolarisJDgaDevInfo * devinfo;

    /* return true if any screen supports DGA and we
     have a library for this type of framebuffer */
    for (screen = 0; screen < XScreenCount(display); screen++) {
        root = RootWindow(display, screen);

        dgaDrawable = XDgaGrabDrawable(display, root);
        if (dgaDrawable != 0) {
            devinfo = getDevInfo(dgaDrawable);
            XDgaUnGrabDrawable(dgaDrawable);
            if (devinfo != NULL) {
                return JNI_TRUE;
            }
        }
    }
    return JNI_FALSE;
}

static JDgaLibInitFunc          Solaris_DGA_LibInit;
static JDgaGetLockFunc          Solaris_DGA_GetLock;
static JDgaReleaseLockFunc      Solaris_DGA_ReleaseLock;
static JDgaXRequestSentFunc     Solaris_DGA_XRequestSent;
static JDgaLibDisposeFunc       Solaris_DGA_LibDispose;
static int firstInitDone = 0;

#pragma weak JDgaLibInit = Solaris_DGA_LibInit

static JDgaStatus
Solaris_DGA_LibInit(JNIEnv *env, JDgaLibInfo *ppInfo)
{
    /* Note: DGA_INIT can be called multiple times according to docs */
    DEBUG_PRINT(("DGA_INIT called\n"));
    DGA_INIT();

    if (!Solaris_DGA_Available(ppInfo->display)) {
        return JDGA_FAILED;
    }
    Solaris_DGA_XineramaInit(ppInfo->display);

    ppInfo->pGetLock = Solaris_DGA_GetLock;
    ppInfo->pReleaseLock = Solaris_DGA_ReleaseLock;
    ppInfo->pXRequestSent = Solaris_DGA_XRequestSent;
    ppInfo->pLibDispose = Solaris_DGA_LibDispose;

    return JDGA_SUCCESS;
}

static JDgaStatus
Solaris_DGA_GetLock(JNIEnv *env, Display *display, void **dgaDev,
                        Drawable drawable, JDgaSurfaceInfo *pSurface,
                        jint lox, jint loy, jint hix, jint hiy)
{
    SolarisDgaLibInfo *pDevInfo;
    SolarisDgaLibInfo *pCachedInfo = cachedInfo;
    int vis;
    int dlox, dloy, dhix, dhiy;
    int i;
    int type, site;
    unsigned long k;
    Drawable prev_virtual_drawable = 0;
    Dga_drawable dgaDrawable;

    if (*dgaDev) {
        if (((SolarisDgaLibInfo *)(*dgaDev))->drawable != drawable) {
            *dgaDev = 0;
        }
    }

    if (*dgaDev == 0) {
        pCachedInfo = cachedInfo;
        for (i = 0 ; (i < MAX_CACHED_INFO) && (pCachedInfo->drawable) ;
             i++, pCachedInfo++) {
            if (pCachedInfo->drawable == drawable) {
                *dgaDev = pCachedInfo;
                break;
            }
        }
        if (*dgaDev == 0) {
            if (i < MAX_CACHED_INFO) { /* slot can be used for new info */
                 *dgaDev = pCachedInfo;
            } else {
                pCachedInfo = cachedInfo;
                /* find the least used slot but does not handle an overflow of
                   the counter */
                for (i = 0, k = 0xffffffff; i < MAX_CACHED_INFO ;
                     i++, pCachedInfo++) {
                    if (k > pCachedInfo->count) {
                        k = pCachedInfo->count;
                        *dgaDev = pCachedInfo;
                    }
                    pCachedInfo->count = 0; /* reset all counters */
                }
                pCachedInfo = *dgaDev;
                if (pCachedInfo->winInfo.dgaDraw != 0) {
                    XDgaUnGrabDrawable(pCachedInfo->winInfo.dgaDraw);
                }
                pCachedInfo->winInfo.dgaDraw = 0;
                /* the slot might be used for another device */
                pCachedInfo->devInfo = 0;
            }
        }
    }

    pDevInfo = *dgaDev;
    pDevInfo->drawable = drawable;

    prev_virtual_drawable = pDevInfo->virtual_drawable;
    pDevInfo->virtual_drawable = GetVirtualDrawable(display, drawable);
    if (pDevInfo->virtual_drawable == NULL) {
        /* this usually means that the drawable is spanned across
           screens in xinerama mode - we can't handle this for now */
        return JDGA_FAILED;
    } else {
        /* check if the drawable has been moved to another screen
           since last time */
        if (pDevInfo->winInfo.dgaDraw != 0 &&
            pDevInfo->virtual_drawable != prev_virtual_drawable) {
            XDgaUnGrabDrawable(pDevInfo->winInfo.dgaDraw);
            pDevInfo->winInfo.dgaDraw = 0;
        }
    }

    pDevInfo->count++;

    if (pDevInfo->winInfo.dgaDraw == 0) {
        pDevInfo->winInfo.dgaDraw = XDgaGrabDrawable(display, pDevInfo->virtual_drawable);
        if (pDevInfo->winInfo.dgaDraw == 0) {
            DEBUG_PRINT(("DgaGrabDrawable failed for 0x%08x\n", drawable));
            return JDGA_UNAVAILABLE;
        }
        type = dga_draw_type(pDevInfo->winInfo.dgaDraw);
        if (type != DGA_DRAW_PIXMAP &&
            mmap_dgaDev(pDevInfo, pDevInfo->winInfo.dgaDraw) != JDGA_SUCCESS) {
            DEBUG_PRINT(("memory map failed for 0x%08x (depth = %d)\n",
                         drawable, dga_draw_depth(pDevInfo->winInfo.dgaDraw)));
            XDgaUnGrabDrawable(pDevInfo->winInfo.dgaDraw);
            pDevInfo->winInfo.dgaDraw = 0;
            return JDGA_UNAVAILABLE;
        }
    } else {
        type = dga_draw_type(pDevInfo->winInfo.dgaDraw);
    }

    if (needsSync) {
        XSync(display, False);
        needsSync = JNI_FALSE;
    }

    dgaDrawable = pDevInfo->winInfo.dgaDraw;

    DGA_DRAW_LOCK(dgaDrawable, -1);

    site = dga_draw_site(dgaDrawable);
    if (type == DGA_DRAW_PIXMAP) {
        if (site == DGA_SITE_SYSTEM) {
            pDevInfo->winInfo.mapDepth = dga_draw_depth(dgaDrawable);
            pDevInfo->winInfo.mapAddr = dga_draw_address(dgaDrawable);
            dga_draw_bbox(dgaDrawable, &dlox, &dloy, &dhix, &dhiy);
            pDevInfo->winInfo.mapWidth = dhix;
            pDevInfo->winInfo.mapHeight = dhiy;
            if (pDevInfo->winInfo.mapDepth == 8) {
                pDevInfo->winInfo.mapLineStride = dga_draw_linebytes(dgaDrawable);
                pDevInfo->winInfo.mapPixelStride = 1;
            } else {
                pDevInfo->winInfo.mapLineStride = dga_draw_linebytes(dgaDrawable)/4;
                pDevInfo->winInfo.mapPixelStride = 4;
            }
        } else {
            XDgaUnGrabDrawable(dgaDrawable);
            pDevInfo->winInfo.dgaDraw = 0;
            return JDGA_UNAVAILABLE;
        }
    } else {
        if (site == DGA_SITE_NULL) {
            DEBUG_PRINT(("zombie drawable = 0x%08x\n", dgaDrawable));
            DGA_DRAW_UNLOCK(dgaDrawable);
            unmap_dgaDev(pDevInfo);
            XDgaUnGrabDrawable(dgaDrawable);
            pDevInfo->winInfo.dgaDraw = 0;
            return JDGA_UNAVAILABLE;
        }
        dga_draw_bbox(dgaDrawable, &dlox, &dloy, &dhix, &dhiy);
    }

    /* get the screen address of the drawable */
    dhix += dlox;
    dhiy += dloy;
    DEBUG_PRINT(("window at (%d, %d) => (%d, %d)\n", dlox, dloy, dhix, dhiy));
    pSurface->window.lox = dlox;
    pSurface->window.loy = dloy;
    pSurface->window.hix = dhix;
    pSurface->window.hiy = dhiy;

            /* translate rendering coordinates relative to device bbox */
    lox += dlox;
    loy += dloy;
    hix += dlox;
    hiy += dloy;
    DEBUG_PRINT(("render at (%d, %d) => (%d, %d)\n", lox, loy, hix, hiy));

    vis = dga_draw_visibility(dgaDrawable);
    switch (vis) {
    case DGA_VIS_UNOBSCURED:
        pSurface->visible.lox = max(dlox, lox);
        pSurface->visible.loy = max(dloy, loy);
        pSurface->visible.hix = min(dhix, hix);
        pSurface->visible.hiy = min(dhiy, hiy);
        DEBUG_PRINT(("unobscured vis at (%d, %d) => (%d, %d)\n",
                     pSurface->visible.lox,
                     pSurface->visible.loy,
                     pSurface->visible.hix,
                     pSurface->visible.hiy));
        break;
    case DGA_VIS_PARTIALLY_OBSCURED: {
        /*
         * fix for #4305271
         * the dga_draw_clipinfo call returns the clipping bounds
         * in short ints, but use only full size ints for all comparisons.
         */
        short *ptr;
        int x0, y0, x1, y1;
        int cliplox, cliploy, cliphix, cliphiy;

        /*
         * iterate to find out whether the clipped blit draws to a
         * single clipping rectangle
         */
        cliplox = cliphix = lox;
        cliploy = cliphiy = loy;
        ptr = dga_draw_clipinfo(dgaDrawable);
        while (*ptr != DGA_Y_EOL) {
            y0 = *ptr++;
            y1 = *ptr++;
            DEBUG_PRINT(("DGA y range loy=%d hiy=%d\n", y0, y1));
            if (y0 < loy) {
                y0 = loy;
            }
            if (y1 > hiy) {
                y1 = hiy;
            }
            while (*ptr != DGA_X_EOL) {
                x0 = *ptr++;
                x1 = *ptr++;
                DEBUG_PRINT(("  DGA x range lox=%d hix=%d\n", x0, x1));
                if (x0 < lox) {
                    x0 = lox;
                }
                if (x1 > hix) {
                    x1 = hix;
                }
                if (x0 < x1 && y0 < y1) {
                    if (cliploy == cliphiy) {
                                /* First rectangle intersection */
                        cliplox = x0;
                        cliploy = y0;
                        cliphix = x1;
                        cliphiy = y1;
                    } else {
                                /* Can we merge this rect with previous? */
                        if (cliplox == x0 && cliphix == x1 &&
                            cliploy <= y1 && cliphiy >= y0)
                            {
                                /* X ranges match, Y ranges touch */
                                /* => absorb the Y ranges together */
                                cliploy = min(cliploy, y0);
                                cliphiy = max(cliphiy, y1);
                            } else if (cliploy == y0 && cliphiy == y1 &&
                                       cliplox <= x1 && cliphix >= x0)
                                {
                                    /* Y ranges match, X ranges touch */
                                    /* => Absorb the X ranges together */
                                    cliplox = min(cliplox, x0);
                                    cliphix = max(cliphix, x1);
                                } else {
                                    /* Assertion: any other combination */
                                    /* means non-rectangular intersect */
                                    DGA_DRAW_UNLOCK(dgaDrawable);
                                    return JDGA_FAILED;
                                }
                    }
                }
            }
            ptr++; /* advance past DGA_X_EOL */
        }
        DEBUG_PRINT(("DGA drawable fits\n"));
        pSurface->visible.lox = cliplox;
        pSurface->visible.loy = cliploy;
        pSurface->visible.hix = cliphix;
        pSurface->visible.hiy = cliphiy;
        break;
    }
    case DGA_VIS_FULLY_OBSCURED:
        pSurface->visible.lox =
            pSurface->visible.hix = lox;
        pSurface->visible.loy =
            pSurface->visible.hiy = loy;
        DEBUG_PRINT(("fully obscured vis\n"));
        break;
    default:
        DEBUG_PRINT(("unknown visibility = %d!\n", vis));
        DGA_DRAW_UNLOCK(dgaDrawable);
        return JDGA_FAILED;
    }

    pSurface->basePtr = pDevInfo->winInfo.mapAddr;
    pSurface->surfaceScan = pDevInfo->winInfo.mapLineStride;
    pSurface->surfaceWidth = pDevInfo->winInfo.mapWidth;
    pSurface->surfaceHeight = pDevInfo->winInfo.mapHeight;
    pSurface->surfaceDepth = pDevInfo->winInfo.mapDepth;

    return JDGA_SUCCESS;
}

static JDgaStatus
Solaris_DGA_ReleaseLock(JNIEnv *env, void *dgaDev, Drawable drawable)
{
    SolarisDgaLibInfo *pDevInfo = (SolarisDgaLibInfo *) dgaDev;

    if (pDevInfo != 0 && pDevInfo->drawable == drawable &&
        pDevInfo->winInfo.dgaDraw != 0) {
        DGA_DRAW_UNLOCK(pDevInfo->winInfo.dgaDraw);
    }
    return JDGA_SUCCESS;
}

static void
Solaris_DGA_XRequestSent(JNIEnv *env, void *dgaDev, Drawable drawable)
{
    needsSync = JNI_TRUE;
}

static void
Solaris_DGA_LibDispose(JNIEnv *env)
{
    SolarisDgaLibInfo *pCachedInfo = cachedInfo;
    SolarisJDgaDevInfo *curDevInfo = devicesInfo;
    int i;

    for (i = 0 ; (i < MAX_CACHED_INFO) && (pCachedInfo->drawable) ;
         i++, pCachedInfo++) {
        if (pCachedInfo->winInfo.dgaDraw != 0) {
            if (dga_draw_type(pCachedInfo->winInfo.dgaDraw) == DGA_DRAW_WINDOW &&
                pCachedInfo->winInfo.mapDepth != 0) {
                unmap_dgaDev(pCachedInfo);
            }
            XDgaUnGrabDrawable(pCachedInfo->winInfo.dgaDraw);
            pCachedInfo->winInfo.dgaDraw = 0;
        }
    }
    for (i = 0; (i < MAX_FB_TYPES) && (curDevInfo->visidName);
         i++, curDevInfo++) {
        curDevInfo->function->devclose(curDevInfo);
        free(curDevInfo->visidName);
    }
}
#endif
