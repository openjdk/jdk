/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
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


#include "ddrawUtils.h"
#include "D3DUtils.h"
#include "D3DSurfaceData.h"

#ifdef DEBUG
// These strings must be in the same order as pixel
// formats in D3DSurfaceData.java
char * TR_NAMES[] = {
    "TR_OPAQUE",
    "TR_BITMASK",
    "TR_TRANSLUCENT"
};

char * PF_NAMES[] = {
    "PF_INVALID" ,
    "PF_INT_ARGB" ,
    "PF_INT_RGB" ,
    "PF_INT_RGBX",
    "PF_INT_BGR" ,
    "PF_USHORT_565_RGB" ,
    "PF_USHORT_555_RGB" ,
    "PF_USHORT_555_RGBX" ,
    "PF_INT_ARGB_PRE" ,
    "PF_USHORT_4444_ARGB"
};
#endif // DEBUG

/**
 * This structure could be used when searching for a pixel
 * format with preferred bit depth.
 */
typedef struct {
    // Pointer to a DDPIXELFORMAT structure where the found pixel
    // format will be copied to
    DDPIXELFORMAT *pddpf;
    // If TRUE, the search was successful, FALSE otherwise
    BOOL bFoundFormat;
    // Preferred bit depth
    int preferredDepth;
} PixelFormatSearchStruct;

jint D3DUtils_GetPixelFormatType(DDPIXELFORMAT*lpddpf);

HRESULT WINAPI EnumAlphaTextureFormatsCallback(DDPIXELFORMAT* pddpf,
                                               VOID* pContext )
{
    J2dTraceLn(J2D_TRACE_VERBOSE, "EnumAlphaTextureFormatsCallback");
    DDPIXELFORMAT* pddpfOut = (DDPIXELFORMAT*)pContext;

    // Looking for a 8-bit luminance texture (and probably not alpha-luminance)
    if((pddpf->dwFlags & DDPF_ALPHA) && (pddpf->dwAlphaBitDepth == 8))
    {
        memcpy(pddpfOut, pddpf, sizeof(DDPIXELFORMAT));
        return D3DENUMRET_CANCEL;
    }

    return D3DENUMRET_OK;
}

HRESULT CALLBACK
D3DUtils_TextureSearchCallback(DDPIXELFORMAT *lpddpf,
                               void *param)
{
    J2dTraceLn(J2D_TRACE_VERBOSE, "D3DUtils_TextureSearchCallback");
    jint pfType = D3DUtils_GetPixelFormatType(lpddpf);
    if (pfType == PF_INVALID) {
        return DDENUMRET_OK;
    }

    DWORD dwAlphaBitCount = 0;
    if (lpddpf->dwFlags & DDPF_ALPHAPIXELS) {
        DWORD dwMask = lpddpf->dwRGBAlphaBitMask;
        while( dwMask ) {
            dwMask = dwMask & ( dwMask - 1 );
            dwAlphaBitCount++;
        }
    }

    DWORD dwRGBBitCount = lpddpf->dwRGBBitCount;
    WORD wDepthIndex = D3D_DEPTH_IDX(dwRGBBitCount);
    WORD wTransparencyIndex =
        dwAlphaBitCount > 0 ? TR_TRANSLUCENT_IDX : TR_OPAQUE_IDX;

    D3DTextureTable *table = (D3DTextureTable*)param;
    D3DTextureTableCell *cell = &(*table)[wTransparencyIndex][wDepthIndex];
    if (cell->pfType == PF_INVALID || pfType < cell->pfType) {
        // set only if it wasn't set or if current pfType is better than
        // the one found previously: it's better to use 565 than 555
        memcpy(&cell->pddpf, lpddpf, sizeof(DDPIXELFORMAT));
        cell->pfType = pfType;
    }
    // continue for all pixel formats
    return DDENUMRET_OK;
}

HRESULT
WINAPI EnumZBufferFormatsCallback(DDPIXELFORMAT* pddpf,
                                  VOID* pContext )
{
    J2dTraceLn(J2D_TRACE_VERBOSE, "EnumZBufferFormatsCallback");
    PixelFormatSearchStruct *ppfss = (PixelFormatSearchStruct*)pContext;
    DDPIXELFORMAT* pddpfOut = ppfss->pddpf;

    // if found a format with the exact depth, return it
    if (pddpf->dwZBufferBitDepth == (DWORD)ppfss->preferredDepth) {
        ppfss->bFoundFormat = TRUE;
        memcpy(pddpfOut, pddpf, sizeof(DDPIXELFORMAT));
        return D3DENUMRET_CANCEL;
    }
    // If a format with exact depth can't be found, look for the best
    // available, preferring those with the lowest bit depth to save
    // video memory. Also, prefer formats with no stencil bits.
    if (!ppfss->bFoundFormat ||
        (pddpfOut->dwZBufferBitDepth > pddpf->dwZBufferBitDepth &&
         !(pddpf->dwFlags & DDPF_STENCILBUFFER)))
    {
        ppfss->bFoundFormat = TRUE;
        memcpy(pddpfOut, pddpf, sizeof(DDPIXELFORMAT));
    }

    return D3DENUMRET_OK;
}

HRESULT
WINAPI DeviceEnumCallback(LPSTR strDesc, LPSTR strName,
                          LPD3DDEVICEDESC7 pDesc,
                          LPVOID pParentInfo)
{
    J2dTraceLn(J2D_TRACE_VERBOSE, "DeviceEnumCallback");
    DEVICES_INFO *devinfo = (DEVICES_INFO*)pParentInfo;

    if (pDesc->deviceGUID == IID_IDirect3DHALDevice) {
        devinfo->pGUIDs[HAL_IDX] = &IID_IDirect3DHALDevice;
    } else if (pDesc->deviceGUID == IID_IDirect3DTnLHalDevice) {
        devinfo->pGUIDs[TNL_IDX] = &IID_IDirect3DTnLHalDevice;
    } else if (pDesc->deviceGUID == IID_IDirect3DRGBDevice) {
        devinfo->pGUIDs[RGB_IDX] = &IID_IDirect3DRGBDevice;
    } else if (pDesc->deviceGUID == IID_IDirect3DRefDevice) {
        devinfo->pGUIDs[REF_IDX] = &IID_IDirect3DRefDevice;
    }
    return D3DENUMRET_OK;
}

HRESULT
D3DUtils_FindMaskTileTextureFormat(IDirect3DDevice7 *d3dDevice,
                                   DDPIXELFORMAT* pddpf)
{
    J2dTraceLn(J2D_TRACE_INFO, "D3DUtils_FindMaskTileTextureFormat");
    d3dDevice->EnumTextureFormats(EnumAlphaTextureFormatsCallback,
                                  (void*)pddpf);
    if (pddpf->dwAlphaBitDepth == 8) {
        return D3D_OK;
    }
    return DDERR_GENERIC;
}

HRESULT
D3DUtils_FindDepthBufferFormat(IDirect3D7 *d3dObject,
                               int preferredDepth,
                               DDPIXELFORMAT* pddpf,
                               const GUID *pDeviceGUID)
{
    J2dTraceLn(J2D_TRACE_INFO, "D3DUtils_FindDepthBufferFormat");
    PixelFormatSearchStruct pfss;
    pfss.pddpf = pddpf;
    pfss.bFoundFormat = FALSE;
    pfss.preferredDepth = preferredDepth;

    d3dObject->EnumZBufferFormats(*pDeviceGUID,
                                  EnumZBufferFormatsCallback,
                                  (void*)&pfss);

    return pfss.bFoundFormat ? D3D_OK : DDERR_GENERIC;
}

jint D3DUtils_GetPixelFormatType(DDPIXELFORMAT*lpddpf)
{
    J2dTraceLn(J2D_TRACE_VERBOSE, "D3DUtils_GetPixelFormatType");

    if (lpddpf == NULL) return PF_INVALID;

    DWORD dwFlags = lpddpf->dwFlags;
    // skip weird formats
    if (lpddpf->dwRGBBitCount < 16   ||
        dwFlags & DDPF_ALPHA         || dwFlags & DDPF_ZBUFFER       ||
        dwFlags & DDPF_ZPIXELS       || dwFlags & DDPF_LUMINANCE     ||
        dwFlags & DDPF_FOURCC        || dwFlags & DDPF_STENCILBUFFER ||
        dwFlags & DDPF_BUMPLUMINANCE || dwFlags & DDPF_BUMPDUDV)
    {
        return PF_INVALID;
    }

    jint pfType = PF_INVALID;
    DWORD aMask = lpddpf->dwRGBAlphaBitMask;
    DWORD rMask = lpddpf->dwRBitMask;
    DWORD gMask = lpddpf->dwGBitMask;
    DWORD bMask = lpddpf->dwBBitMask;

    if (rMask == 0x0000f800 &&
        gMask == 0x000007e0 &&
        bMask == 0x0000001f &&
        aMask == 0x00000000)
    {
        pfType = PF_USHORT_565_RGB;
    } else if (rMask == 0x00007C00 &&
               gMask == 0x000003E0 &&
               bMask == 0x0000001f &&
               aMask == 0x00000000)
    {
        pfType = PF_USHORT_555_RGB;
    } else if (rMask == 0x00000f00 &&
               gMask == 0x000000f0 &&
               bMask == 0x0000000f &&
               aMask == 0x0000f000)
    {
        // REMIND: we currently don't support this
        // pixel format, since we don't have the loops for a
        // premultiplied version of it. So we'll just use INT_ARGB
        // for now
        pfType = PF_INVALID;
        // pfType = PF_USHORT_4444_ARGB;
    } else if (rMask == 0x00ff0000 &&
               gMask == 0x0000ff00 &&
               bMask == 0x000000ff)
    {
        if (lpddpf->dwRGBBitCount == 32) {
            pfType = (dwFlags & DDPF_ALPHAPIXELS) ?
                PF_INT_ARGB : PF_INT_RGB;
        } else {
            // We currently don't support this format.
            // pfType = PF_3BYTE_BGR;
            pfType = PF_INVALID;
        }
    }

    return pfType;
}

void
D3DUtils_SetupTextureFormats(IDirect3DDevice7 *d3dDevice,
                             D3DTextureTable &table)
{
    J2dTraceLn(J2D_TRACE_INFO, "D3DUtils_SetupTextureFormats");
    if (d3dDevice == NULL || table == NULL) {
        return;
    }

    ZeroMemory(table, sizeof(D3DTextureTable));
    int t;
    for (t = TR_OPAQUE_IDX; t < TR_MAX_IDX; t++) {
        for (int d = DEPTH16_IDX; d < DEPTH_MAX_IDX; d++) {
            table[t][d].pfType = PF_INVALID;
        }
    }
    d3dDevice->EnumTextureFormats(D3DUtils_TextureSearchCallback, table);

    // We've retrieved the pixel formats for this device. The matrix may
    // look something like this, depending on the formats the device supports:
    // Transparency/Depth        Depth 16            Depth 24          Depth 32
    // ------------------------------------------------------------------------
    //      TR_OPAQUE   PF_USHORT_565_RGB          PF_INVALID        PF_INT_RGB
    //     TR_BITMASK          PF_INVALID          PF_INVALID        PF_INVALID
    // TR_TRANSLUCENT          PF_INVALID          PF_INVALID       PF_INT_ARGB


    // we'll be using translucent pixel formats for bitmask images
    // for now, this may change later
    memcpy(&table[TR_BITMASK_IDX], &table[TR_TRANSLUCENT_IDX],
           sizeof(D3DTextureTableCell[DEPTH_MAX_IDX]));
    // Transparency/Depth        Depth 16            Depth 24          Depth 32
    // ------------------------------------------------------------------------
    //      TR_OPAQUE   PF_USHORT_565_RGB          PF_INVALID        PF_INT_RGB
    //     TR_BITMASK          PF_INVALID          PF_INVALID       PF_INT_ARGB
    // TR_TRANSLUCENT          PF_INVALID          PF_INVALID       PF_INT_ARGB

    // REMIND: crude force
    // Find substitutes for pixel formats which we didn't find.
    // For example, if we didn't find a 24-bit format, 32-bit will be
    // a first choice for substitution. But if it wasn't found either,
    // then use 16-bit format
    D3DTextureTableCell *cell16, *cell24, *cell32;
    for (t = TR_OPAQUE_IDX; t < TR_MAX_IDX; t++) {
        cell16 = &table[t][DEPTH16_IDX];
        cell24 = &table[t][DEPTH24_IDX];
        cell32 = &table[t][DEPTH32_IDX];
        if (cell32->pfType == PF_INVALID) {
            if (cell24->pfType != PF_INVALID) {
                memcpy(cell32, cell24, sizeof(D3DTextureTableCell));
            } else if (cell16->pfType != PF_INVALID) {
                memcpy(cell32, cell16, sizeof(D3DTextureTableCell));
            } else {
                // no valid pixel formats for this transparency
                // type were found
                continue;
            }
        }
        // now we know that 32-bit is valid
        if (cell24->pfType == PF_INVALID) {
            // use 32-bit format as a substitution for 24-bit
            memcpy(cell24, cell32, sizeof(D3DTextureTableCell));
        }
        // now we know that 32- and 24-bit are valid
        if (cell16->pfType == PF_INVALID) {
            // use 24-bit format as a substitution for 16-bit
            memcpy(cell16, cell24, sizeof(D3DTextureTableCell));
        }
    }
    // After this loop the matrix may look something like this:
    // Transparency/Depth        Depth 16            Depth 24          Depth 32
    // ------------------------------------------------------------------------
    //      TR_OPAQUE   PF_USHORT_565_RGB          PF_INT_RGB        PF_INT_RGB
    //     TR_BITMASK         PF_INT_ARGB         PF_INT_ARGB       PF_INT_ARGB
    // TR_TRANSLUCENT         PF_INT_ARGB         PF_INT_ARGB       PF_INT_ARGB

#ifdef DEBUG
    // Print out the matrix (should look something like the comment above)
    J2dTraceLn1(J2D_TRACE_INFO,
                "Texutre formats table for device %x", d3dDevice);
    J2dTraceLn(J2D_TRACE_INFO, "Transparency/Depth     Depth 16            "\
               "Depth 24            Depth 32");
    J2dTraceLn(J2D_TRACE_INFO, "-------------------------------------------"\
               "----------------------------");
    for (t = TR_OPAQUE_IDX; t < TR_MAX_IDX; t++) {
        J2dTrace1(J2D_TRACE_INFO, "%15s", TR_NAMES[t]);
        for (int d = DEPTH16_IDX; d < DEPTH_MAX_IDX; d++) {
            J2dTrace1(J2D_TRACE_INFO, "%20s",
                      PF_NAMES[table[t][d].pfType]);
        }
        J2dTrace(J2D_TRACE_INFO, "\n");
    }
#endif // DEBUG
}

const GUID *
D3DUtils_SelectDeviceGUID(IDirect3D7 *d3dObject)
{
    static char * RASTERIZER_NAMES[] = {
        "TNL", "HAL", "REFERENCE", "RGB"
    };
    // try to use TnL rasterizer by default
    int defIndex = TNL_IDX;

    J2dTraceLn(J2D_TRACE_INFO, "D3DUtils_SelectDeviceGUID");
    // unless a different one was requested
    char *pRasterizer = getenv("J2D_D3D_RASTERIZER");
    if (pRasterizer != NULL) {
        if (strncmp(pRasterizer, "ref", 3) == 0) {
            defIndex = REF_IDX;
        } else if (strncmp(pRasterizer, "rgb", 3) == 0) {
            defIndex = RGB_IDX;
        } else if (strncmp(pRasterizer, "hal", 3) == 0) {
            defIndex = HAL_IDX;
        } else if (strncmp(pRasterizer, "tnl", 3) == 0) {
            defIndex = TNL_IDX;
        }
        J2dTraceLn1(J2D_TRACE_VERBOSE,
                    "  rasterizer requested: %s",
                    RASTERIZER_NAMES[defIndex]);
    }

    DEVICES_INFO devInfo;
    memset(&devInfo, 0, sizeof(devInfo));
    HRESULT res;
    if (FAILED(res = d3dObject->EnumDevices(DeviceEnumCallback,
                                            (VOID*)&devInfo)))
    {
        DebugPrintDirectDrawError(res, "D3DUtils_SelectDeviceGUID: "\
                                  "EnumDevices failed");
        return NULL;
    }

    // return requested rasterizer's guid if it's present
    if (devInfo.pGUIDs[defIndex] != NULL) {
        J2dRlsTraceLn1(J2D_TRACE_VERBOSE,
                       "D3DUtils_SelectDeviceGUID: using %s rasterizer",
                       RASTERIZER_NAMES[defIndex]);
        return devInfo.pGUIDs[defIndex];
    }
    // if not, try to find one, starting with the best available
    defIndex = TNL_IDX;
    do {
        if (devInfo.pGUIDs[defIndex] != NULL) {
            J2dRlsTraceLn1(J2D_TRACE_VERBOSE,
                           "D3DUtils_SelectDeviceGUID: using %s rasterizer",
                           RASTERIZER_NAMES[defIndex]);
            return devInfo.pGUIDs[defIndex];
        }
        // While we could use the rgb and ref rasterizers if tnl and
        // hal aren't present, it's not practical for performance purposes.
        // so we just leave an opportunity to force them.
    } while (++defIndex < REF_IDX /*DEV_IDX_MAX*/);


    J2dRlsTraceLn(J2D_TRACE_ERROR,
                  "D3DUtils_SelectDeviceGUID: "\
                  "No Accelerated Rasterizers Found");
    return NULL;
}


/*
 * This function sets passed matrix to be a custom left-hand off-center
 * orthogonal matrix. The output is identical to D3DX's function call
 * D3DXMatrixOrthoOffCenterLH((D3DXMATRIX*)&tx,
 *                            0.0, width, height, 0.0, -1.0, 1.0);
 */
void
D3DUtils_SetOrthoMatrixOffCenterLH(D3DMATRIX *m,
                                   float width, float height)
{
    DASSERT((m != NULL) && (width > 0.0f) && (height > 0.0f));
    memset(m, 0, sizeof(D3DMATRIX));
    m->_11 =  2.0f/width;
    m->_22 = -2.0f/height;
    m->_33 =  0.5f;
    m->_44 =  1.0f;

    m->_41 = -1.0f;
    m->_42 =  1.0f;
    m->_43 =  0.5f;
}

void
D3DUtils_SetIdentityMatrix(D3DMATRIX *m, BOOL adjust)
{
    DASSERT(m != NULL);
    m->_12 = m->_13 = m->_14 = m->_21 = m->_23 = m->_24 = 0.0f;
    m->_31 = m->_32 = m->_34 = m->_43 = 0.0f;
    m->_11 = m->_22 = m->_33 = m->_44 = 1.0f;
    if (adjust) {
        // This is required for proper texel alignment
        m->_41 = m->_42 = -0.5f;
    } else {
        m->_41 = m->_42 = 0.0f;
    }
}

DDrawSurface *
D3DUtils_CreatePlainSurface(JNIEnv *env,
                            DDraw *ddObject,
                            D3DContext *d3dContext,
                            int w, int h)
{
    DXSurface *dxSurface;
    jint pType;
    J2dTraceLn(J2D_TRACE_INFO, "D3DUtils_CreatePlainSurface");
    if (FAILED(d3dContext->CreateSurface(env, w, h, 32,
                                         TR_OPAQUE, D3D_PLAIN_SURFACE,
                                         &dxSurface, &pType)))
    {
        return NULL;
    }
    return new DDrawSurface(ddObject, dxSurface);
}

DDrawSurface *
D3DUtils_CreateTexture(JNIEnv *env,
                       DDraw *ddObject,
                       D3DContext *d3dContext,
                       int transparency,
                       int w, int h)
{
    J2dTraceLn(J2D_TRACE_INFO, "D3DUtils_CreateTexture");
    DXSurface *dxSurface;
    jint pType;
    if (FAILED(d3dContext->CreateSurface(env, w, h, 32,
                                         transparency, D3D_TEXTURE_SURFACE,
                                         &dxSurface, &pType)))
    {
        return NULL;
    }
    return new DDrawSurface(ddObject, dxSurface);
}

HRESULT
D3DUtils_UploadIntImageToXRGBTexture(DDrawSurface *lpTexture,
                                     int *pSrc, int width, int height)
{
    HRESULT res;
    int texW = lpTexture->GetDXSurface()->GetWidth();
    int texH = lpTexture->GetDXSurface()->GetHeight();
    int srcStride = width * 4;

    J2dTraceLn(J2D_TRACE_INFO, "D3DUtils_UploadIntImageToXRGBTexture");
    if (width > texW) {
        width = texW;
    }
    if (height > texH) {
        height = texH;
    }

    SurfaceDataRasInfo rasInfo;
    if (SUCCEEDED(res = lpTexture->Lock(NULL, &rasInfo,
                                        DDLOCK_WAIT|DDLOCK_NOSYSLOCK, NULL)))
    {
        void *pDstPixels = rasInfo.rasBase;
        void *pSrcPixels = (void*)pSrc;

        // REMIND: clear the dest first
        memset(pDstPixels, 0, texH * rasInfo.scanStride);
        do {
            memcpy(pDstPixels, pSrcPixels, width * 4);
            pSrcPixels = PtrAddBytes(pSrcPixels, srcStride);
            pDstPixels = PtrAddBytes(pDstPixels, rasInfo.scanStride);
        } while (--height > 0);
        res = lpTexture->Unlock(NULL);
    }
    return res;
}

HRESULT
D3DUtils_CheckD3DCaps(LPD3DDEVICEDESC7 lpDesc7)
{
    // The device must support fast rasterization
    static DWORD dwDevCaps =
        (D3DDEVCAPS_DRAWPRIMTLVERTEX | D3DDEVCAPS_HWRASTERIZATION);
    BOOL vt = lpDesc7->dwDevCaps & D3DDEVCAPS_DRAWPRIMTLVERTEX;
    BOOL rz = lpDesc7->dwDevCaps & D3DDEVCAPS_HWRASTERIZATION;

    J2dTraceLn(J2D_TRACE_INFO, "D3DUtils_CheckD3DCaps");
    return (lpDesc7->dwDevCaps & dwDevCaps) ?
        D3D_OK :
        DDERR_GENERIC;
}

HRESULT
D3DUtils_CheckTextureCaps(LPD3DDEVICEDESC7 lpDesc7)
{
    J2dTraceLn(J2D_TRACE_INFO, "D3DUtils_CheckTextureCaps");
    // REMIND: we should really check both Tri and Lin caps,
    // but hopefully we won't be using line strips soon
    LPD3DPRIMCAPS lpDpcTriCaps = &lpDesc7->dpcTriCaps;
    // Filtering requirements
    static DWORD dwFilterCaps =
        (D3DPTFILTERCAPS_LINEAR | D3DPTFILTERCAPS_NEAREST);
    // Check for caps used for alpha compositing (implementation of
    // Porter-Duff rules)
    static DWORD dwBlendCaps =
        (D3DPBLENDCAPS_ZERO | D3DPBLENDCAPS_ONE |
         D3DPBLENDCAPS_SRCALPHA  | D3DPBLENDCAPS_INVSRCALPHA |
         D3DPBLENDCAPS_DESTALPHA | D3DPBLENDCAPS_INVDESTALPHA);

    if ((lpDesc7->dwTextureOpCaps & D3DTEXOPCAPS_MODULATE) &&
        (lpDpcTriCaps->dwTextureFilterCaps & dwFilterCaps) &&
        (lpDpcTriCaps->dwSrcBlendCaps  & dwBlendCaps) &&
        (lpDpcTriCaps->dwDestBlendCaps & dwBlendCaps))
    {
        return D3D_OK;
    }
    return DDERR_GENERIC;
}

HRESULT
D3DUtils_CheckDeviceCaps(LPD3DDEVICEDESC7 lpDesc7) {
    J2dTraceLn(J2D_TRACE_INFO, "D3DUtils_CheckDeviceCaps");
    if (SUCCEEDED(D3DUtils_CheckD3DCaps(lpDesc7)) &&
        SUCCEEDED(D3DUtils_CheckTextureCaps(lpDesc7)) &&
        SUCCEEDED(D3DUtils_CheckDepthCaps(lpDesc7)))
    {
        return D3D_OK;
    }
    return DDERR_GENERIC;
}

HRESULT
D3DUtils_CheckDepthCaps(LPD3DDEVICEDESC7 lpDesc7)
{
    J2dTraceLn(J2D_TRACE_INFO, "D3DUtils_CheckDepthCaps");
    // Check for required depth-buffer operations
    // (see D3DContext::SetClip() for more info).
    static DWORD dwZCmpCaps = (D3DPCMPCAPS_ALWAYS | D3DPCMPCAPS_LESS);
    // D3DPMISCCAPS_MASKZ capability allows enabling/disabling
    // depth buffer updates.
    if ((lpDesc7->dpcTriCaps.dwMiscCaps & D3DPMISCCAPS_MASKZ) &&
        (lpDesc7->dpcTriCaps.dwZCmpCaps & dwZCmpCaps))
    {
        return D3D_OK;
    }
    return DDERR_GENERIC;
}
