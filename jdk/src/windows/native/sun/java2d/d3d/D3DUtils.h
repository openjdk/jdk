/*
 * Copyright 2005 Sun Microsystems, Inc.  All Rights Reserved.
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

#ifndef D3DUTILS_H
#define D3DUTILS_H

#include "D3DContext.h"

// - Types and macros used in SelectDeviceGUID -----------------------
// Indexes for the rasterizers:
// TNL, HAL, REF, RGB
#define TNL_IDX (0)
#define HAL_IDX (1)
#define REF_IDX (2)
#define RGB_IDX (3)
#define DEV_IDX_MAX (RGB_IDX+1)

typedef struct {
    const GUID *pGUIDs[4];
} DEVICES_INFO;

// - Utility funcions for dealing with pixel formats ----------------
const GUID *
D3DUtils_SelectDeviceGUID(IDirect3D7 *d3dObject);

HRESULT
D3DUtils_FindDepthBufferFormat(IDirect3D7 *d3dObject,
                               int preferredDepth,
                               DDPIXELFORMAT* pddpf,
                               const GUID *pDeviceGUID);
HRESULT
D3DUtils_FindMaskTileTextureFormat(IDirect3DDevice7 *d3dDevice,
                                   DDPIXELFORMAT* pddpf);
void
D3DUtils_SetupTextureFormats(IDirect3DDevice7 *d3dDevice,
                             D3DTextureTable &table);

// - Utility funcions for working with matricies ---------------------
void
D3DUtils_SetIdentityMatrix(D3DMATRIX *m, BOOL adjust = TRUE);

void
D3DUtils_SetOrthoMatrixOffCenterLH(D3DMATRIX *m,
                                   float width, float height);
DDrawSurface *
D3DUtils_CreatePlainSurface(JNIEnv *env, DDraw *ddObject,
                            D3DContext *d3dContext,
                            int w, int h);

DDrawSurface *
D3DUtils_CreateTexture(JNIEnv *env, DDraw *ddObject,
                       D3DContext *d3dContext,
                       int transparency,
                       int w, int h);

HRESULT
D3DUtils_UploadIntImageToXRGBTexture(DDrawSurface *lpTexture,
                                     int *pSrc, int width, int height);

// - Utility functions for checking various capabilities of the device

HRESULT
D3DUtils_CheckD3DCaps(LPD3DDEVICEDESC7 lpDesc7);

HRESULT
D3DUtils_CheckDepthCaps(LPD3DDEVICEDESC7 lpDesc7);

HRESULT
D3DUtils_CheckTextureCaps(LPD3DDEVICEDESC7 lpDesc7);

HRESULT
D3DUtils_CheckDeviceCaps(LPD3DDEVICEDESC7 lpDesc7);

// - Utility macros error handling of d3d operations -----------------

/*  #define NO_D3D_CHECKING */

#ifdef NO_D3D_CHECKING

#define D3DU_PRIM_LOOP_BEGIN(RES, DST_WSDO)
#define D3DU_PRIM2_LOOP_BEGIN(RES, SRC_WSDO, DST_WSDO)
#define D3DU_PRIM_LOOP_END(ENV, RES, DST_WSDO, PRIM)
#define D3DU_PRIM2_LOOP_END(ENV, RES, SRC_WSDO, DST_WSDO, PRIM)

#else /* NO_D3D_CHECKING */

#ifndef MAX_BUSY_ATTEMPTS
  #define MAX_BUSY_ATTEMPTS 50  // Arbitrary number of times to attempt
#endif


#define D3DU_PRIM_LOOP_BEGIN(RES, DST_WSDO) \
do { \
    int attempts = 0; \
    while (attempts++ < MAX_BUSY_ATTEMPTS) { \
        if (FAILED((DST_WSDO)->lpSurface->IsLost())) { \
            RES = DDERR_SURFACELOST; \
        } else {

#define D3DU_PRIM2_LOOP_BEGIN(RES, SRC_WSDO, DST_WSDO) \
do { \
    int attempts = 0; \
    while (attempts++ < MAX_BUSY_ATTEMPTS) { \
        if (FAILED((DST_WSDO)->lpSurface->IsLost()) || \
            FAILED((SRC_WSDO)->lpSurface->IsLost())) \
        { \
            RES = DDERR_SURFACELOST; \
        } else {

#define D3DU_PRIM_LOOP_END(ENV, RES, DST_WSDO, PRIM) \
        } \
        if (SUCCEEDED(RES)) { \
            break; \
        } else if (RES == DDERR_SURFACEBUSY || RES == DDERR_WASSTILLDRAWING) { \
            J2dTraceLn(J2D_TRACE_VERBOSE, #PRIM ## ": surface is busy."); \
            continue; \
        } else if (RES == DDERR_SURFACELOST) { \
            J2dTraceLn(J2D_TRACE_INFO, #PRIM ## ": dest surface lost."); \
            DST_WSDO->RestoreSurface(ENV, DST_WSDO); \
            break; \
        } else { \
            DebugPrintDirectDrawError(RES, #PRIM); \
        } \
     } \
} while (0)

#define D3DU_PRIM2_LOOP_END(ENV, RES, SRC_WSDO, DST_WSDO, PRIM) \
        } \
        if (SUCCEEDED(RES)) { \
            break; \
        } else if (RES == DDERR_SURFACEBUSY || RES == DDERR_WASSTILLDRAWING) { \
            J2dTraceLn(J2D_TRACE_VERBOSE, #PRIM ## ": surface is busy."); \
            continue; \
        } else if (RES == DDERR_SURFACELOST) { \
            if (FAILED((DST_WSDO)->lpSurface->IsLost())) { \
                J2dTraceLn(J2D_TRACE_INFO, #PRIM ## ": dst surface lost."); \
                (DST_WSDO)->RestoreSurface(ENV, (DST_WSDO)); \
            } \
            if (FAILED((SRC_WSDO)->lpSurface->IsLost())) { \
                J2dTraceLn(J2D_TRACE_INFO, #PRIM ## ": src surface lost."); \
                (SRC_WSDO)->RestoreSurface(ENV, (SRC_WSDO)); \
            } \
            break; \
        } else { \
            DebugPrintDirectDrawError(RES, #PRIM); \
        } \
     } \
} while (0)

#endif /* NO_D3D_CHECKING */

// - Utility macros for initializing vertex structures ---------------

#define D3D_EXEC_PRIM_LOOP(ENV, RES, DST_WSDO, PRIM) \
  D3DU_PRIM_LOOP_BEGIN(RES, DST_WSDO); \
  RES = (PRIM); \
  D3DU_PRIM_LOOP_END(ENV, RES, DST_WSDO, PRIM);

#define D3DU_INIT_VERTEX_PENT_XY(VQUAD, X1, Y1, X2, Y2) \
do { \
    D3DU_INIT_VERTEX_QUAD_XY(VQUAD, X1, Y1, X2, Y2); \
    (VQUAD)[4].x = (X1); (VQUAD)[4].y = (Y1); \
} while (0)

#define D3DU_INIT_VERTEX_PENT_COLOR(VQUAD, VCOLOR) \
do { \
    D3DU_INIT_VERTEX_QUAD_COLOR(VQUAD, VCOLOR); \
    (VQUAD)[4].color = (VCOLOR); \
} while (0)

#define D3DU_INIT_VERTEX_QUAD_XY(VQUAD, X1, Y1, X2, Y2) \
do { \
    (VQUAD)[0].x = (X1); (VQUAD)[0].y = (Y1); \
    (VQUAD)[1].x = (X2); (VQUAD)[1].y = (Y1); \
    (VQUAD)[2].x = (X2); (VQUAD)[2].y = (Y2); \
    (VQUAD)[3].x = (X1); (VQUAD)[3].y = (Y2); \
} while (0)

#define D3DU_INIT_VERTEX_QUAD_XYZ(VQUAD, X1, Y1, X2, Y2, Z) \
do { \
    D3DU_INIT_VERTEX_QUAD_XY(VQUAD, X1, Y1, X2, Y2); \
    (VQUAD)[0].z = (Z); \
    (VQUAD)[1].z = (Z); \
    (VQUAD)[2].z = (Z); \
    (VQUAD)[3].z = (Z); \
} while (0)

#define D3DU_INIT_VERTEX_QUAD_COLOR(VQUAD, VCOLOR) \
do { \
    (VQUAD)[0].color = (VCOLOR); \
    (VQUAD)[1].color = (VCOLOR); \
    (VQUAD)[2].color = (VCOLOR); \
    (VQUAD)[3].color = (VCOLOR); \
} while (0)

#define D3DU_INIT_VERTEX_QUAD_UV(VQUAD, TU1, TV1, TU2, TV2) \
do { \
    (VQUAD)[0].tu = (TU1);  (VQUAD)[0].tv = (TV1); \
    (VQUAD)[1].tu = (TU2);  (VQUAD)[1].tv = (TV1); \
    (VQUAD)[2].tu = (TU2);  (VQUAD)[2].tv = (TV2); \
    (VQUAD)[3].tu = (TU1);  (VQUAD)[3].tv = (TV2); \
} while (0)

#define D3DU_INIT_VERTEX_QUAD_XYUV(VQUAD, X1, Y1, X2, Y2, TU1, TV1, TU2, TV2) \
do { \
    D3DU_INIT_VERTEX_QUAD_XY(VQUAD, X1, Y1, X2, Y2); \
    D3DU_INIT_VERTEX_QUAD_UV(VQUAD, TU1, TV1, TU2, TV2); \
} while (0)

#define D3DU_INIT_VERTEX_QUAD(VQUAD, X1, Y1, X2, Y2, VCOLOR, TU1, TV1, TU2, TV2) \
do { \
    D3DU_INIT_VERTEX_QUAD_XYUV(VQUAD, X1, Y1, X2, Y2, TU1, TV1, TU2, TV2); \
    D3DU_INIT_VERTEX_QUAD_COLOR(VQUAD, VCOLOR); \
} while (0)

#define D3DU_INIT_VERTEX_6(VQUAD, X1, Y1, X2, Y2, VCOLOR, TU1, TV1, TU2, TV2) \
do { \
    D3DU_INIT_VERTEX_XY_6(VHEX, X1, Y1, X2, Y2); \
    D3DU_INIT_VERTEX_UV_6(VHEX, TU1, TV1, TU2, TV2); \
    D3DU_INIT_VERTEX_COLOR_6(VHEX, VCOLOR); \
} while (0)

#define D3DU_INIT_VERTEX_UV_6(VHEX, TU1, TV1, TU2, TV2) \
do { \
    (VHEX)[0].tu = TU1;  (VHEX)[0].tv = TV1; \
    (VHEX)[1].tu = TU2;  (VHEX)[1].tv = TV1; \
    (VHEX)[2].tu = TU1;  (VHEX)[2].tv = TV2; \
    (VHEX)[3].tu = TU1;  (VHEX)[3].tv = TV2; \
    (VHEX)[4].tu = TU2;  (VHEX)[4].tv = TV1; \
    (VHEX)[5].tu = TU2;  (VHEX)[5].tv = TV2; \
} while (0)

#define D3DU_INIT_VERTEX_COLOR_6(VHEX, VCOLOR) \
do { \
    (VHEX)[0].color = VCOLOR; \
    (VHEX)[1].color = VCOLOR; \
    (VHEX)[2].color = VCOLOR; \
    (VHEX)[3].color = VCOLOR; \
    (VHEX)[4].color = VCOLOR; \
    (VHEX)[5].color = VCOLOR; \
} while (0)

#define D3DU_INIT_VERTEX_XY_6(VHEX, X1, Y1, X2, Y2) \
do { \
    (VHEX)[0].x = X1;  (VHEX)[0].y = Y1; \
    (VHEX)[1].x = X2;  (VHEX)[1].y = Y1; \
    (VHEX)[2].x = X1;  (VHEX)[2].y = Y2; \
    (VHEX)[3].x = X1;  (VHEX)[3].y = Y2; \
    (VHEX)[4].x = X2;  (VHEX)[4].y = Y1; \
    (VHEX)[5].x = X2;  (VHEX)[5].y = Y2; \
} while (0)

#define D3DU_INIT_VERTEX_XYZ_6(VHEX, X1, Y1, X2, Y2, Z) \
do { \
    D3DU_INIT_VERTEX_XY_6(VHEX, X1, Y1, X2, Y2); \
    (VHEX)[0].z = (Z);  \
    (VHEX)[1].z = (Z);  \
    (VHEX)[2].z = (Z);  \
    (VHEX)[3].z = (Z);  \
    (VHEX)[4].z = (Z);  \
    (VHEX)[5].z = (Z);  \
} while (0)


#endif // D3DUTILS_H
