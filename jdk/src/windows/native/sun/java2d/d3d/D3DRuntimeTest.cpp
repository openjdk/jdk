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

#include "dxInit.h"
#include "ddrawUtils.h"
#include "RegistryKey.h"
#include "D3DTestRaster.h"
#include "WindowsFlags.h"
#include "D3DRuntimeTest.h"
#include "D3DSurfaceData.h"
#include "D3DUtils.h"

#ifdef DEBUG
void TestRasterOutput(byte *rasPtr, int x, int y, int w, int h,
                      int scanStride, int pixelStride,
                      TIntTestRaster goldenArray = NULL);
#endif // DEBUG
void PrintD3DCaps(int caps);

/**
 * Test whether we should enable d3d rendering on this device.
 * This includes checking whether there were problems creating
 * the necessary offscreen surface, problems during any of the
 * rendering calls (Blts and d3d lines) and any rendering artifacts
 * caused by d3d lines.  The rendering artifact tests are
 * performed by checking a pre-rendered test pattern (produced
 * by our software renderer) against that same pattern rendered
 * on this device.  If there are any pixels which differ between
 * the two patterns we disable d3d line rendering on the device.
 * Differences in the test pattern rendering can be caused
 * by different rendering algorithms used by our software
 * renderer and the driver or hardware on this device.  For example,
 * some Intel cards (e.g., i815) are known to use polygon renderers
 * for their lines, which sometimes result in wide lines.
 * The test pattern is stored in d3dTestRaster.h, which is generated
 * by a Java test program
 * (src/share/test/java2d/VolatileImage/D3DTestPattern/D3DTestPattern.java).
 */

int TestForBadHardware(DxCapabilities *dxCaps)
{
    // Check this device against a list of bad d3d devices and
    // disable as necessary
    static WCHAR *badDeviceStrings[] = {
        L"Trident Video Accelerator",
        L"RAGE PRO",
        L"RAGE XL",
        L"Rage Fury",
    };
    static int numBadDevices = 4;
    WCHAR *dxDeviceName = dxCaps->GetDeviceName();
    for (int i = 0; i < numBadDevices; ++i) {
        if (wcsstr(dxDeviceName, badDeviceStrings[i]) != NULL) {
            // REMIND: For now, we disable d3d for all operations because
            // of one bad d3d device in the system.  This is because we
            // should avoid registering the d3d rendering loops at the
            // Java level since we cannot use d3d at the native level.
            // A real fix would instead understand the difference between
            // a surface that could handle d3d native rendering and one
            // that could not and would use the appropriate rendering loop
            // so that disabling d3d on simply one device would be
            // sufficient.
            // Note that this disable-all approach is okay for now because
            // the single bad device (Trident) that triggers this error
            // is generally found on laptops, where multiple graphics
            // devices are not even possible, so disabling d3d for all
            // devices is equivalent to disabling d3d for this single
            // device.
            J2dRlsTraceLn1(J2D_TRACE_ERROR,
                           "TestForBadHardware: Found match: %S. Test FAILED",
                           badDeviceStrings[i]);
            return J2D_D3D_FAILURE;
        }
    }
    return J2D_D3D_HW_OK;
}

int TestTextureFormats(D3DContext *d3dContext)
{
    int testRes = J2D_D3D_FAILURE;

    D3DTextureTable &table = d3dContext->GetTextureTable();
    int pfExists;
    // Check that there's at least one valid pixel format
    // for each transparency type (opaque, bitmask, translucent)
    for (int t = TR_OPAQUE_IDX; t < TR_MAX_IDX; t++) {
        pfExists = FALSE;
        for (int d = DEPTH16_IDX; d < DEPTH_MAX_IDX; d++) {
            if (table[t][d].pfType != PF_INVALID) {
                pfExists = TRUE;
                break;
            }
        }
        if (pfExists == FALSE) {
            // couldn't find a pixel formap for this transparency type
            J2dRlsTraceLn1(J2D_TRACE_ERROR,
                           "D3DTest::TestTextureFormats no texture formats"\
                           " for %d transparency", t);
            break;
        }
    }

    // we must have ARGB texture format (may be used for text rendering)
    if (pfExists == TRUE &&
        table[TR_TRANSLUCENT_IDX][DEPTH32_IDX].pfType == PF_INT_ARGB)
    {
        testRes |= J2D_D3D_PIXEL_FORMATS_OK;
    } else {
        J2dRlsTraceLn1(J2D_TRACE_ERROR,
                       "D3DTest::TestTextureFormats: FAILED pfType=%d",
                       table[TR_TRANSLUCENT_IDX][DEPTH32_IDX].pfType);
    }
    return testRes;
}

int TestSetClip(JNIEnv *env, D3DContext *d3dContext,
                DDrawSurface *lpPlainSurface)
{
    int testRes = J2D_D3D_FAILURE;

    if (SUCCEEDED(d3dContext->SetRenderTarget(lpPlainSurface))) {
        jobject clip =
            JNU_CallStaticMethodByName(env, NULL,
                                       "sun/java2d/pipe/Region",
                                       "getInstanceXYWH",
                                       "(IIII)Lsun/java2d/pipe/Region;",
                                       0, 0, D3D_TEST_RASTER_W, D3D_TEST_RASTER_H).l;
        if (!JNU_IsNull(env, clip)) {
            if (SUCCEEDED(d3dContext->SetClip(env, clip, JNI_TRUE,
                                              0, 0,
                                              D3D_TEST_RASTER_W,
                                              D3D_TEST_RASTER_H)))
            {
                testRes |= J2D_D3D_DEPTH_SURFACE_OK;
            }
            env->DeleteLocalRef(clip);
        }
    }
    return testRes;
}

int TestRenderingResults(DDrawSurface *lpPlainSurface,
                         TIntTestRaster goldenArray)
{
    // Now, check the results of the test raster against our d3d drawing
    SurfaceDataRasInfo rasInfo;
    if (FAILED(lpPlainSurface->Lock(NULL, &rasInfo, DDLOCK_WAIT, NULL))) {
        return J2D_D3D_FAILURE;
    }

    byte *rasPtr = (byte*)rasInfo.rasBase;
    int pixelStride = rasInfo.pixelStride;
    int scanStride = rasInfo.scanStride;
    for (int row = 0; row < D3D_TEST_RASTER_H; ++row) {
        byte *tmpRasPtr = rasPtr + row * scanStride;
        for (int col = 0; col < D3D_TEST_RASTER_W; ++col) {
            DWORD pixelVal;
            switch (pixelStride) {
            case 1:
                pixelVal = *tmpRasPtr;
                break;
            case 2:
                pixelVal = *((unsigned short*)tmpRasPtr);
                break;
            default:
                pixelVal = *((unsigned int*)tmpRasPtr);
                break;
            }
            tmpRasPtr += pixelStride;
            // The test is simple: if the test raster pixel has value 0, then
            // we expect 0 in the d3d surface.  If the test raster has a nonzero
            // value, then we expect the d3d surface to also have non-zero value.
            // All other results represent failure.
            int goldenValue = (goldenArray[row][col] & 0x00ffffff);
            if ((goldenValue == 0 && pixelVal != 0) ||
                (goldenValue != 0 && pixelVal == 0))
            {
                J2dRlsTraceLn3(J2D_TRACE_WARNING,
                               "TestRenderingResults: Quality test failed due "\
                               "to value %x at (%d, %d)", pixelVal, col, row);
#ifdef DEBUG
                // This section is not necessary, but it might be
                // nice to know why we are failing D3DTest on some
                // systems.  If tracing is enabled, this section will
                // produce an ascii representation of the test pattern,
                // the result on this device, and the pixels that were
                // in error.
                J2dTraceLn(J2D_TRACE_VERBOSE, "TestRaster:");
                TestRasterOutput((byte*)goldenArray, 0, 0, D3D_TEST_RASTER_W,
                                 D3D_TEST_RASTER_H, D3D_TEST_RASTER_W*4, 4);
                J2dTraceLn(J2D_TRACE_VERBOSE, "D3D Raster:");
                TestRasterOutput(rasPtr, 0, 0, D3D_TEST_RASTER_W,
                                 D3D_TEST_RASTER_H, scanStride, pixelStride);
                J2dTraceLn(J2D_TRACE_VERBOSE, "Deltas (x indicates problem pixel):");
                TestRasterOutput(rasPtr, 0, 0, D3D_TEST_RASTER_W,
                                 D3D_TEST_RASTER_H, scanStride, pixelStride,
                                 goldenArray);
#endif // DEBUG
                lpPlainSurface->Unlock(NULL);
                return  J2D_D3D_FAILURE;
            }
        }
    }

    lpPlainSurface->Unlock(NULL);
    return (J2D_D3D_LINES_OK | J2D_D3D_LINE_CLIPPING_OK);
}

int TestLineRenderingQuality(JNIEnv *env, D3DContext *d3dContext,
                             DDrawSurface *lpPlainSurface)
{
    static J2D_XY_C_VERTEX lineVerts[] = {
#ifdef USE_SINGLE_VERTEX_FORMAT
        { 0, 0, 0, 0xffffffff, 0.0f, 0.0f },
        { 0, 0, 0, 0xffffffff, 0.0f, 0.0f },
        { 0, 0, 0, 0xffffffff, 0.0f, 0.0f },
        { 0, 0, 0, 0xffffffff, 0.0f, 0.0f },
        { 0, 0, 0, 0xffffffff, 0.0f, 0.0f },
#else
        { 0, 0, 0, 0xffffffff }, // x, y, z, color
        { 0, 0, 0, 0xffffffff },
        { 0, 0, 0, 0xffffffff },
        { 0, 0, 0, 0xffffffff },
        { 0, 0, 0, 0xffffffff },
#endif // USE_SINGLE_VERTEX_FORMAT
    };
    IDirect3DDevice7 *d3dDevice = d3dContext->Get3DDevice();
    HRESULT res;

    d3dDevice->Clear(0, NULL, D3DCLEAR_TARGET, 0x0, 0.0, 0);

    if (FAILED(d3dContext->BeginScene(STATE_RENDEROP))) {
        return J2D_D3D_FAILURE;
    }

    int i;

    for (i = 0; i < d3dNumTestLines * 4; i += 4) {
        lineVerts[0].x = d3dTestLines[i + 0];
        lineVerts[0].y = d3dTestLines[i + 1];
        lineVerts[1].x = d3dTestLines[i + 2];
        lineVerts[1].y = d3dTestLines[i + 3];
        if (FAILED(res = d3dDevice->DrawPrimitive(D3DPT_LINESTRIP,
                                                  D3DFVF_J2D_XY_C,
                                                  lineVerts, 2, 0)))
        {
            d3dContext->ForceEndScene();
            return J2D_D3D_FAILURE;
        }
        // REMIND: needed for the test to pass on ATI some boards
        d3dDevice->DrawPrimitive(D3DPT_POINTLIST, D3DFVF_J2D_XY_C,
                                 &(lineVerts[1]), 1, 0);
    }

    for (i = 0; i < d3dNumTestRects * 4; i += 4) {
        float x1 = d3dTestRects[i + 0];
        float y1 = d3dTestRects[i + 1];
        float x2 = d3dTestRects[i + 2];
        float y2 = d3dTestRects[i + 3];
        D3DU_INIT_VERTEX_PENT_XY(lineVerts, x1, y1, x2, y2);
        if (FAILED(res = d3dDevice->DrawPrimitive(D3DPT_LINESTRIP,
                                                  D3DFVF_J2D_XY_C,
                                                  lineVerts, 5, 0)))
        {
            d3dContext->ForceEndScene();
            return J2D_D3D_FAILURE;
        }
    }
    d3dContext->ForceEndScene();

    // REMIND: add rendering of clipped lines

    return TestRenderingResults(lpPlainSurface, d3dTestRaster);
}

int TestTextureMappingQuality(JNIEnv *env, DDraw *ddObject,
                              D3DContext *d3dContext,
                              DDrawSurface *lpPlainSurface)
{
    static J2DLVERTEX quadVerts[4] = {
        { 0.0f, 0.0f, 0.0f, 0xffffffff, 0.0f, 0.0f },
        { 0.0f, 0.0f, 0.0f, 0xffffffff, 0.0f, 0.0f },
        { 0.0f, 0.0f, 0.0f, 0xffffffff, 0.0f, 0.0f },
        { 0.0f, 0.0f, 0.0f, 0xffffffff, 0.0f, 0.0f }
    };

    int testRes = TestTextureFormats(d3dContext);

    if (testRes & J2D_D3D_PIXEL_FORMATS_OK) {

        DDrawSurface *lpTexture =
            D3DUtils_CreateTexture(env, ddObject, d3dContext, TR_TRANSLUCENT,
                                   D3D_TEXTURE_RASTER_W, D3D_TEXTURE_RASTER_H);
        if (lpTexture) {
            D3DUtils_UploadIntImageToXRGBTexture(lpTexture,
                                                 (int *)srcImageArray,
                                                 D3D_TEXTURE_RASTER_W,
                                                 D3D_TEXTURE_RASTER_H);

            float u2 = ((float)D3D_TEXTURE_RASTER_W) /
                       (float)lpTexture->GetDXSurface()->GetWidth();
            float v2 = ((float)D3D_TEXTURE_RASTER_H) /
                       (float)lpTexture->GetDXSurface()->GetHeight();
            D3DU_INIT_VERTEX_QUAD_UV(quadVerts, 0.0f, 0.0f, u2, v2);

            IDirect3DDevice7 *d3dDevice = d3dContext->Get3DDevice();
            d3dDevice->Clear(0, NULL, D3DCLEAR_TARGET, 0x00000000, 0.0, 0);

            d3dContext->SetAlphaComposite(3/*SrcOver*/,
                                          1.0f, D3DC_NO_CONTEXT_FLAGS);
            d3dDevice->SetTextureStageState(0, D3DTSS_MAGFILTER, D3DTFG_POINT);
            d3dDevice->SetTextureStageState(0, D3DTSS_MINFILTER, D3DTFG_POINT);

            HRESULT res;
            if (SUCCEEDED(res = d3dContext->BeginScene(STATE_BLITOP))) {
                DXSurface *dxSurface = lpTexture->GetDXSurface();
                if (SUCCEEDED(d3dContext->SetTexture(dxSurface))) {
                    for (int i = 0; i < d3dNumTextureRects * 4; i += 4) {
                        float x1 = d3dTextureRects[i + 0];
                        float y1 = d3dTextureRects[i + 1];
                        float x2 = d3dTextureRects[i + 2];
                        float y2 = d3dTextureRects[i + 3];
                        D3DU_INIT_VERTEX_QUAD_XY(quadVerts, x1, y1, x2, y2);
                        d3dDevice->DrawPrimitive(D3DPT_TRIANGLEFAN,
                                                 D3DFVF_J2DLVERTEX,
                                                 quadVerts, 4, 0);
                    }
                }
                res = d3dContext->ForceEndScene();
                d3dContext->SetTexture(NULL);
            }
            // REMIND: at this point we ignore the results of
            // the test.
            TestRenderingResults(lpPlainSurface, linInterpArray);
            if (SUCCEEDED(res)) {
                testRes |= (J2D_D3D_TR_TEXTURE_SURFACE_OK |
                            J2D_D3D_TEXTURE_BLIT_OK       |
                            J2D_D3D_TEXTURE_TRANSFORM_OK);

                // REMIND: add tests for opaque and bitmask textures
                testRes |= (J2D_D3D_OP_TEXTURE_SURFACE_OK |
                            J2D_D3D_BM_TEXTURE_SURFACE_OK);
            }
            delete lpTexture;
        } else {
            J2dRlsTraceLn(J2D_TRACE_ERROR,
                          "TestTextureMappingQuality: "\
                          "CreateTexture(TRANSLUCENT) FAILED");
        }
    }
    return testRes;
}

int TestD3DDevice(DDraw *ddObject,
                  D3DContext *d3dContext,
                  DxCapabilities *dxCaps)
{
    int testRes = TestForBadHardware(dxCaps);
    if (!(testRes & J2D_D3D_HW_OK) || !d3dContext) {
        return testRes;
    }

    D3DDEVICEDESC7 d3dDevDesc;
    IDirect3DDevice7 *d3dDevice = d3dContext->Get3DDevice();
    if (d3dDevice == NULL  ||
        FAILED(d3dDevice->GetCaps(&d3dDevDesc)) ||
        FAILED(D3DUtils_CheckDeviceCaps(&d3dDevDesc)))
    {
        J2dRlsTraceLn(J2D_TRACE_ERROR,
                      "TestD3DDevice: device caps testing FAILED");
        return testRes;
    }
    testRes |= J2D_D3D_DEVICE_OK;

    JNIEnv *env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);
    DDrawSurface *lpPlainSurface =
        D3DUtils_CreatePlainSurface(env, ddObject, d3dContext,
                                    D3D_TEST_RASTER_W, D3D_TEST_RASTER_H);
    if (!lpPlainSurface) {
        J2dRlsTraceLn(J2D_TRACE_ERROR,
                      "TestD3DDevice: CreatePlainSurface FAILED");
        return testRes;
    }
    testRes |= J2D_D3D_PLAIN_SURFACE_OK;

    // Set identity transform
    if (FAILED(d3dContext->SetTransform(NULL, 0, 0, 0, 0, 0, 0))) {
        J2dRlsTraceLn(J2D_TRACE_ERROR,
                      "TestD3DDevice: SetTransform FAILED");
        delete lpPlainSurface;
        return testRes;
    }
    testRes |= J2D_D3D_SET_TRANSFORM_OK;

    // Test setting the target surface, create depth buffer, and
    // clip
    testRes |= TestSetClip(env, d3dContext, lpPlainSurface);
    if (!(testRes & J2D_D3D_DEPTH_SURFACE_OK)) {
        J2dRlsTraceLn(J2D_TRACE_ERROR, "TestD3DDevice: SetClip FAILED");
        delete lpPlainSurface;
        return testRes;
    }

    // Test drawLines
    testRes |= TestLineRenderingQuality(env, d3dContext, lpPlainSurface);

    // Test texture mapping
    testRes |= TestTextureMappingQuality(env, ddObject, d3dContext,
                                         lpPlainSurface);

    d3dContext->SetRenderTarget(NULL);

    delete lpPlainSurface;
    return testRes;
}

#ifdef DEBUG
/**
 * Output test raster (produced in D3DTest function).  Utility
 * used in debugging only.  Enable by setting J2D_TRACE_LEVEL=J2D_VERBOSE
 * prior to running application with debug java.  The output from this will
 * be seen only if D3DTest fails.
 */
void TestRasterOutput(byte *rasPtr, int x, int y, int w, int h,
                      int scanStride, int pixelStride,
                      TIntTestRaster goldenArray)
{
    int goldenValue;
    for (int traceRow = y; traceRow < h; ++traceRow) {
        byte *tmpRasPtr = rasPtr + traceRow * scanStride;
        for (int traceCol = x; traceCol < w; ++traceCol) {
            DWORD pixelVal;
            switch (pixelStride) {
            case 1:
                pixelVal = *tmpRasPtr;
                break;
            case 2:
                pixelVal = *((unsigned short*)tmpRasPtr);
                break;
            default:
                pixelVal = *((unsigned int*)tmpRasPtr) & 0x00ffffff;
                break;
            }
            tmpRasPtr += pixelStride;
            if (goldenArray == NULL) {
                if (pixelVal) {
                    J2dTrace(J2D_TRACE_VERBOSE, "1");
                } else {
                    J2dTrace(J2D_TRACE_VERBOSE, "0");
                }
            } else {
                goldenValue = (goldenArray[traceRow][traceCol] & 0x00ffffff);
                if ((goldenValue == 0 && pixelVal != 0) ||
                    (goldenValue != 0 && pixelVal == 0))
                {
                    J2dTrace(J2D_TRACE_VERBOSE, "x");
                } else {
                    J2dTrace(J2D_TRACE_VERBOSE, "-");
                }
            }

        }
        J2dTrace(J2D_TRACE_VERBOSE, "\n");
    }
}
#endif // DEBUG

void PrintD3DCaps(int caps)
{
    J2dTraceLn(J2D_TRACE_VERBOSE, "{")
    if (caps == J2D_D3D_FAILURE) {
        J2dTraceLn(J2D_TRACE_VERBOSE, "  J2D_D3D_FAILURE");
    } else {
        if (caps & J2D_D3D_DEPTH_SURFACE_OK) {
            J2dTraceLn(J2D_TRACE_VERBOSE, "  J2D_D3D_DEPTH_SURFACE_OK,");
        }
        if (caps & J2D_D3D_PLAIN_SURFACE_OK) {
            J2dTraceLn(J2D_TRACE_VERBOSE, "  J2D_D3D_PLAIN_SURFACE_OK,");
        }
        if (caps & J2D_D3D_OP_TEXTURE_SURFACE_OK) {
            J2dTraceLn(J2D_TRACE_VERBOSE, "  J2D_D3D_OP_TEXTURE_SURFACE_OK,");
        }
        if (caps & J2D_D3D_BM_TEXTURE_SURFACE_OK) {
            J2dTraceLn(J2D_TRACE_VERBOSE, "  J2D_D3D_BM_TEXTURE_SURFACE_OK,");
        }
        if (caps & J2D_D3D_TR_TEXTURE_SURFACE_OK) {
            J2dTraceLn(J2D_TRACE_VERBOSE, "  J2D_D3D_TR_TEXTURE_SURFACE_OK,");
        }
        if (caps & J2D_D3D_OP_RTT_SURFACE_OK) {
            J2dTraceLn(J2D_TRACE_VERBOSE, "  J2D_D3D_OP_RTT_SURFACE_OK,");
        }
        if (caps & J2D_D3D_LINE_CLIPPING_OK) {
            J2dTraceLn(J2D_TRACE_VERBOSE, "  J2D_D3D_LINE_CLIPPING_OK,");
        }
        if (caps & J2D_D3D_LINES_OK) {
            J2dTraceLn(J2D_TRACE_VERBOSE, "  J2D_D3D_LINES_OK,");
        }
        if (caps & J2D_D3D_TEXTURE_BLIT_OK) {
            J2dTraceLn(J2D_TRACE_VERBOSE, "  J2D_D3D_TEXTURE_BLIT_OK,");
        }
        if (caps & J2D_D3D_TEXTURE_TRANSFORM_OK) {
            J2dTraceLn(J2D_TRACE_VERBOSE, "  J2D_D3D_TEXTURE_TRANSFORM_OK,");
        }
        if (caps & J2D_D3D_DEVICE_OK) {
            J2dTraceLn(J2D_TRACE_VERBOSE, "  J2D_D3D_DEVICE_OK,");
        }
        if (caps & J2D_D3D_PIXEL_FORMATS_OK) {
            J2dTraceLn(J2D_TRACE_VERBOSE, "  J2D_D3D_SET_TRANSFORM_OK,");
        }
        if (caps & J2D_D3D_HW_OK) {
            J2dTraceLn(J2D_TRACE_VERBOSE, "  J2D_D3D_HW_OK,");
        }
    }
    J2dTraceLn(J2D_TRACE_VERBOSE, "}");
}
