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

#include "sun_java2d_d3d_D3DContext.h"
#include "jlong.h"
#include "jni_util.h"
#include "Trace.h"

#include "ddrawUtils.h"
#include "awt_Win32GraphicsDevice.h"
#include "sun_java2d_SunGraphics2D.h"

#include "GraphicsPrimitiveMgr.h"

#include "RegistryKey.h"
#include "WindowsFlags.h"

#include "Win32SurfaceData.h"
#include "D3DSurfaceData.h"
#include "D3DUtils.h"
#include "D3DContext.h"
#include "D3DRuntimeTest.h"

#include "IntDcm.h"
#include "IntArgb.h"
#include "Region.h"

typedef struct {
    D3DBLEND src;
    D3DBLEND dst;
} D3DBlendRule;

/**
 * This table contains the standard blending rules (or Porter-Duff compositing
 * factors) used in SetRenderState(), indexed by the rule constants from the
 * AlphaComposite class.
 */
D3DBlendRule StdBlendRules[] = {
    { D3DBLEND_ZERO,         D3DBLEND_ZERO        }, /* 0 - Nothing      */
    { D3DBLEND_ZERO,         D3DBLEND_ZERO        }, /* 1 - RULE_Clear   */
    { D3DBLEND_ONE,          D3DBLEND_ZERO        }, /* 2 - RULE_Src     */
    { D3DBLEND_ONE,          D3DBLEND_INVSRCALPHA }, /* 3 - RULE_SrcOver */
    { D3DBLEND_INVDESTALPHA, D3DBLEND_ONE         }, /* 4 - RULE_DstOver */
    { D3DBLEND_DESTALPHA,    D3DBLEND_ZERO        }, /* 5 - RULE_SrcIn   */
    { D3DBLEND_ZERO,         D3DBLEND_SRCALPHA    }, /* 6 - RULE_DstIn   */
    { D3DBLEND_INVDESTALPHA, D3DBLEND_ZERO        }, /* 7 - RULE_SrcOut  */
    { D3DBLEND_ZERO,         D3DBLEND_INVSRCALPHA }, /* 8 - RULE_DstOut  */
    { D3DBLEND_ZERO,         D3DBLEND_ONE         }, /* 9 - RULE_Dst     */
    { D3DBLEND_DESTALPHA,    D3DBLEND_INVSRCALPHA }, /*10 - RULE_SrcAtop */
    { D3DBLEND_INVDESTALPHA, D3DBLEND_SRCALPHA    }, /*11 - RULE_DstAtop */
    { D3DBLEND_INVDESTALPHA, D3DBLEND_INVSRCALPHA }, /*12 - RULE_AlphaXor*/
};

/**
 * D3DContext
 */
D3DContext* D3DContext::CreateD3DContext(DDraw *ddObject, DXObject* dxObject)
{
    J2dTraceLn(J2D_TRACE_INFO, "D3DContext::CreateD3DContext");
    // create and test the d3d context
    D3DContext *d3dContext = new D3DContext(ddObject, dxObject);
    // if there was a failure while creating or testing the device,
    // dispose of it and return NULL
    if (!(d3dContext->GetDeviceCaps() & J2D_D3D_ENABLED_OK)) {
        delete d3dContext;
        d3dContext = NULL;
    }
    return d3dContext;
}

D3DContext::D3DContext(DDraw *ddObject, DXObject* dxObject)
{
    GetExclusiveAccess();
    J2dRlsTraceLn(J2D_TRACE_INFO, "D3DContext::D3DContext");
    J2dTraceLn2(J2D_TRACE_VERBOSE, "  ddObject=0x%d dxObject=0x%x",
                ddObject, dxObject);
    d3dDevice = NULL;
    d3dObject = NULL;
    ddTargetSurface = NULL;
    lpMaskTexture = NULL;
    lpGlyphCacheTexture = NULL;
    glyphCache = NULL;
    glyphCacheAvailable = TRUE;
    deviceCaps = J2D_D3D_FAILURE;
    bBeginScenePending = FALSE;
    jD3DContext = NULL;

    this->dxObject = dxObject;
    this->ddObject = ddObject;

    if (SUCCEEDED(dxObject->CreateD3DObject(&d3dObject))) {

        // The device type we choose to use doesn't change over time
        pDeviceGUID = D3DUtils_SelectDeviceGUID(d3dObject);
        if (pDeviceGUID) {
            bIsHWRasterizer = (*pDeviceGUID == IID_IDirect3DHALDevice ||
                               *pDeviceGUID == IID_IDirect3DTnLHalDevice);
            CreateD3DDevice();
        } else {
            J2dRlsTraceLn(J2D_TRACE_ERROR,
                          "D3CCoD3DContext::D3DContext: Can't find "\
                          "suitable D3D device");
        }
    } else {
        J2dRlsTraceLn(J2D_TRACE_ERROR,
                      "D3DContext::D3DContext: Can't "\
                      "create IDirect3D7 interface");
    }

    compState = sun_java2d_SunGraphics2D_COMP_ISCOPY;
    extraAlpha = 1.0f;
    colorPixel = 0xffffffff;

    ReleaseExclusiveAccess();
}

void D3DContext::SetJavaContext(JNIEnv *env, jobject newD3Dc) {
    GetExclusiveAccess();

    // Only bother if the new D3DContext object is different
    // from the one we already have reference to.
    if (env->IsSameObject(newD3Dc, jD3DContext) == FALSE) {
        J2dTraceLn(J2D_TRACE_VERBOSE, "D3DContext:SetJavaContext: "\
                   "setting new java context object");
        // invalidate the old context, since we've got a new one
        InvalidateIfTarget(env, ddTargetSurface);

        if (jD3DContext != NULL) {
            env->DeleteWeakGlobalRef(jD3DContext);
        }
        // set the new java-level context object
        jD3DContext = env->NewWeakGlobalRef(newD3Dc);
    }
    ReleaseExclusiveAccess();
}

void D3DContext::Release3DDevice() {
    GetExclusiveAccess();
    J2dTraceLn1(J2D_TRACE_INFO,
                "D3DContext::Release3DDevice: d3dDevice = 0x%x",
                d3dDevice);

    // make sure we do EndScene if one is pending
    FlushD3DQueueForTarget(ddTargetSurface);

    // Let the java-level object know that the context
    // state is no longer valid, forcing it to be reinitialized
    // later.
    JNIEnv *env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);
    InvalidateIfTarget(env, ddTargetSurface);

    // We don't need to release it since we didn't create it
    ddTargetSurface = NULL;

    // disable the use of this context until we ensure the capabilities
    // of the new device and run the tests
    deviceCaps = J2D_D3D_FAILURE;

    if (lpMaskTexture != NULL) {
        lpMaskTexture->Release();
        delete lpMaskTexture;
        lpMaskTexture = NULL;
    }

    // reset the depth buffer format
    memset(&depthBufferFormat, 0, sizeof(depthBufferFormat));

    if (d3dDevice) {
        // setting the texture increases its reference number, so
        // we should reset the textures for all stages to make sure
        // they're released
        for (int stage = 0; stage <= MAX_USED_TEXTURE_STAGE; stage++) {
            d3dDevice->SetTexture(stage, NULL);
            lastTexture[stage] = NULL;
        }
        d3dDevice->Release();
        d3dDevice = NULL;
    }
    ReleaseExclusiveAccess();
}

D3DContext::~D3DContext() {
    J2dTraceLn2(J2D_TRACE_INFO,
                "~D3DContext: d3dDevice=0x%x, d3dObject =0x%x",
                d3dDevice, d3dObject);
    GetExclusiveAccess();
    if (lpGlyphCacheTexture != NULL) {
        lpGlyphCacheTexture->Release();
        delete lpGlyphCacheTexture;
        lpGlyphCacheTexture = NULL;
    }
    Release3DDevice();
    if (d3dObject != NULL) {
        d3dObject->Release();
        d3dObject = NULL;
    }
    ReleaseExclusiveAccess();
}

HRESULT
D3DContext::InitD3DDevice(IDirect3DDevice7 *d3dDevice)
{
    HRESULT res = D3D_OK;
    J2dRlsTraceLn1(J2D_TRACE_INFO,
                   "D3DContext::InitD3DDevice: d3dDevice=Ox%x", d3dDevice);

    d3dDevice->GetCaps(&d3dDevDesc);
    // disable some of the unneeded and costly d3d functionality
    d3dDevice->SetRenderState(D3DRENDERSTATE_CULLMODE, D3DCULL_NONE);
    d3dDevice->SetRenderState(D3DRENDERSTATE_TEXTUREPERSPECTIVE, FALSE);
    d3dDevice->SetRenderState(D3DRENDERSTATE_SPECULARENABLE, FALSE);
    d3dDevice->SetRenderState(D3DRENDERSTATE_LIGHTING,  FALSE);
    d3dDevice->SetRenderState(D3DRENDERSTATE_CLIPPING,  FALSE);
    d3dDevice->SetRenderState(D3DRENDERSTATE_ZENABLE, D3DZB_FALSE);
    d3dDevice->SetRenderState(D3DRENDERSTATE_COLORVERTEX, FALSE);
    d3dDevice->SetRenderState(D3DRENDERSTATE_STENCILENABLE, FALSE);

    d3dDevice->SetTextureStageState(0, D3DTSS_MAGFILTER, D3DTFG_POINT);
    d3dDevice->SetTextureStageState(0, D3DTSS_MINFILTER, D3DTFG_POINT);

    // these states never change
    d3dDevice->SetTextureStageState(0, D3DTSS_ALPHAOP, D3DTOP_MODULATE);
    d3dDevice->SetTextureStageState(0, D3DTSS_COLOROP, D3DTOP_MODULATE);
    d3dDevice->SetTextureStageState(0, D3DTSS_ALPHAARG2, D3DTA_DIFFUSE);
    d3dDevice->SetTextureStageState(0, D3DTSS_COLORARG2, D3DTA_DIFFUSE);
    // init the array of latest textures
    memset(&lastTexture, 0, sizeof(lastTexture));
    // this will force the state initialization on first UpdateState
    opState = STATE_UNDEFINED;

    D3DMATRIX tx;
    D3DUtils_SetIdentityMatrix(&tx);
    d3dDevice->SetTransform(D3DTRANSFORMSTATE_WORLD, &tx);

    bBeginScenePending = FALSE;

    D3DUtils_SetupTextureFormats(d3dDevice, textureTable);

    // REMIND: debugging: allows testing the argb path in
    // UploadImageToTexture on devices with alpha texture support
    if ((getenv("J2D_D3D_NOALPHATEXTURE") != NULL) ||
         FAILED(res = D3DUtils_FindMaskTileTextureFormat(d3dDevice,
                                                         &maskTileTexFormat)))
    {
        // use ARGB if can't find alpha texture (or in case argb
        // was specifically requested)
        J2dTraceLn(J2D_TRACE_VERBOSE,
                   "D3DContext::InitD3DDevice: "\
                   "Using IntARBG instead of Alpha texture");
        if (textureTable[TR_TRANSLUCENT_IDX][DEPTH32_IDX].pfType != PF_INVALID)
        {
            memcpy(&maskTileTexFormat,
                   &textureTable[TR_TRANSLUCENT_IDX][DEPTH32_IDX].pddpf,
                   sizeof(maskTileTexFormat));
            res = D3D_OK;
        }
    } else {
        J2dTraceLn(J2D_TRACE_VERBOSE,
                   "D3DContext::InitD3DDevice: Found Alpha-texture format");
    }
    return res;
}

HRESULT
D3DContext::CreateAndTestD3DDevice(DxCapabilities *dxCaps)
{
    J2dRlsTraceLn(J2D_TRACE_INFO, "D3DContext::CreateAndTestD3DDevice");
    HRESULT res;
    if (pDeviceGUID == NULL) {
        J2dRlsTraceLn(J2D_TRACE_ERROR,
                      "D3DContext::CreateAndTestD3DDevice: "\
                      "No usable d3d device");
        deviceCaps = J2D_D3D_FAILURE;
        return DDERR_GENERIC;
    }

    Release3DDevice();

    // Create a temp surface so we can use it when creating a device
    DXSurface *target = NULL;
    if (FAILED(res = CreateSurface(NULL, 10, 10, 32, TR_OPAQUE,
                                   D3D_PLAIN_SURFACE|D3D_RENDER_TARGET,
                                   &target, NULL)))
    {
        DebugPrintDirectDrawError(res,
                                  "D3DContext::CreateAndTestD3DDevice: "\
                                  "can't create scratch surface");
        return res;
    }

    if (FAILED(res = d3dObject->CreateDevice(*pDeviceGUID,
                                             target->GetDDSurface(),
                                             &d3dDevice)))
    {
        DebugPrintDirectDrawError(res,
                                  "D3DContext::CreateAndTestD3DDevice: "\
                                  "error creating d3d device");
    } else if (FAILED(res = InitD3DDevice(d3dDevice))) {
        DebugPrintDirectDrawError(res,
                                  "D3DContext::CreateAndTestD3DDevice: "\
                                  "error initializing D3D device");
    } else {
        J2dRlsTraceLn(J2D_TRACE_VERBOSE,
                      "D3DContext::CreateAndTestD3DDevice: "\
                      "D3D device creation/initialization successful");
        // the device is successfully created and initialized,
        // now run some tests on it
        deviceCaps = TestD3DDevice(ddObject, this, dxCaps);
    }

    // We can safely dispose the scratch surface here
    if (target != NULL) {
        target->Release();
        delete target;
    }

    return res;
}

void
D3DContext::CreateD3DDevice()
{
    GetExclusiveAccess();
    J2dRlsTraceLn(J2D_TRACE_INFO, "D3DContext::CreateD3DDevice");
    // this is a weird way of getting a handle on the ddInstance
    HMONITOR hMonitor = dxObject->GetHMonitor();
    DxCapabilities *dxCaps =
        AwtWin32GraphicsDevice::GetDxCapsForDevice(hMonitor);

    int d3dCapsValidity = dxCaps->GetD3dCapsValidity();
    // Always run the test unless we crashed doing so the last time.
    // The reasons:
    //   - the user may have disabled d3d acceleration in the display panel
    //     since the last run
    //   - the user may have installed the new drivers, which may cause BSODs
    //   - if the test had failed previously because of quality issues, the
    //     new driver may have fixed the problem, but we'd never know since we
    //     never try again
    //   - user (or developer, rather) may have specified a
    //     different rasterizer via env. variable
    if (d3dCapsValidity != J2D_ACCEL_TESTING) {
        dxCaps->SetD3dCapsValidity(J2D_ACCEL_TESTING);

        // this will create the device, test it and set the
        // deviceCaps
        CreateAndTestD3DDevice(dxCaps);

        dxCaps->SetD3dDeviceCaps(deviceCaps);
        dxCaps->SetD3dCapsValidity(J2D_ACCEL_SUCCESS);
    }
    int requiredResults = forceD3DUsage ?
        J2D_D3D_REQUIRED_RESULTS : J2D_D3D_DESIRED_RESULTS;

#ifdef DEBUG
    J2dTraceLn(J2D_TRACE_VERBOSE, "CreateD3DDevice: requested caps:");
    PrintD3DCaps(requiredResults);
    J2dTraceLn(J2D_TRACE_VERBOSE, " caps supported by the device:");
    PrintD3DCaps(deviceCaps);
    J2dTraceLn(J2D_TRACE_VERBOSE, " missing caps:");
    PrintD3DCaps(requiredResults & ~deviceCaps);
#endif // DEBUG

    if ((deviceCaps & requiredResults) != requiredResults) {
        if (!(deviceCaps & J2D_D3D_HW_OK)) {
            // disable d3d for all devices, because we've encountered
            // known bad hardware. See comment in TestForBadHardware().
            J2dRlsTraceLn(J2D_TRACE_ERROR,
                          "CreateD3DDevice: bad hardware found,"\
                          " disabling d3d for all devices.");
            SetD3DEnabledFlag(NULL, FALSE, FALSE);
        } else {
            J2dRlsTraceLn(J2D_TRACE_ERROR,
                          "CreateD3DDevice: tests FAILED, d3d disabled.");
        }
        // REMIND: the first time the context initialization fails,
        // deviceUseD3D is set to FALSE in DDrawObjectStruct, and because of
        // this we never attempt to initialize it again later.
        // For example, if the app switches to a display mode where
        // d3d is not supported, we disable d3d, but it stays disabled
        // even when the display mode is switched back to a supported one.
        // May be we should disable it only in case of a hard error.
        ddObject->DisableD3D();
        Release3DDevice();
    } else {
        deviceCaps |= J2D_D3D_ENABLED_OK;
        J2dRlsTraceLn1(J2D_TRACE_INFO,
                       "CreateD3DDevice: tests PASSED, "\
                       "d3d enabled (forced: %s).",
                       forceD3DUsage ? "yes" : "no");
    }

    ReleaseExclusiveAccess();
}

HRESULT
D3DContext::SetRenderTarget(DDrawSurface *ddSurface)
{
    static D3DVIEWPORT7 vp = { 0, 0, 0, 0, 0.0f, 1.0f };
    static D3DMATRIX tx;
    BOOL bSetProjectionMatrix = FALSE;
    HRESULT res = DDERR_GENERIC;
    GetExclusiveAccess();

    J2dTraceLn2(J2D_TRACE_INFO,
                "D3DContext::SetRenderTarget: old=0x%x new=0x%x",
                ddTargetSurface, ddSurface);

    ddTargetSurface = NULL;

    DXSurface *dxSurface = NULL;
    if (d3dDevice == NULL || ddSurface == NULL ||
        (dxSurface = ddSurface->GetDXSurface()) == NULL)
    {
        ReleaseExclusiveAccess();
        J2dTraceLn3(J2D_TRACE_WARNING,
                    "D3DContext::SetRenderTarget invalid state:"\
                    "d3dDevice=0x%x ddSurface=0x%x dxSurface=0x%x",
                    d3dDevice, ddSurface, dxSurface);
        return res;
    }

    if (FAILED(res = ddSurface->IsLost())) {
        ReleaseExclusiveAccess();
        DebugPrintDirectDrawError(res, "D3DContext::SetRenderTarget: "\
                                  "target surface (and/or depth buffer) lost");
        return res;
    }

    ForceEndScene();

    if (FAILED(res = d3dDevice->SetRenderTarget(dxSurface->GetDDSurface(), 0)))
    {
        ReleaseExclusiveAccess();
        DebugPrintDirectDrawError(res, "D3DContext::SetRenderTarget: "\
                                  "error setting render target");
        return res;
    }

    int width = dxSurface->GetWidth();
    int height = dxSurface->GetHeight();
    // set the projection matrix if the the dimensions of the new
    // rendertarget are different from the old one.
    if (FAILED(d3dDevice->GetViewport(&vp)) ||
        (int)vp.dwWidth != width  || (int)vp.dwHeight != height)
    {
        bSetProjectionMatrix = TRUE;
    }

    vp.dwX = vp.dwY = 0;
    vp.dwWidth  = width;
    vp.dwHeight = height;
    vp.dvMinZ = 0.0f;
    vp.dvMaxZ = 1.0f;

    if (FAILED(res = d3dDevice->SetViewport(&vp))) {
        DebugPrintDirectDrawError(res, "D3DContext::SetRenderTarget: "\
                                  "error setting viewport");
        ReleaseExclusiveAccess();
        return res;
    }

    if (bSetProjectionMatrix) {
        D3DUtils_SetOrthoMatrixOffCenterLH(&tx, (float)width, (float)height);
        res = d3dDevice->SetTransform(D3DTRANSFORMSTATE_PROJECTION, &tx);
    }

    if (SUCCEEDED(res)) {
        ddTargetSurface = ddSurface;
        J2dTraceLn1(J2D_TRACE_VERBOSE,
                    "D3DContext::SetRenderTarget: succeeded, "\
                    "new target=0x%x", ddTargetSurface);
    } else {
        DebugPrintDirectDrawError(res, "D3DContext::SetRenderTarget: failed");
    }

    ReleaseExclusiveAccess();
    return res;
}

HRESULT
D3DContext::SetTransform(jobject xform,
                         jdouble m00, jdouble m10,
                         jdouble m01, jdouble m11,
                         jdouble m02, jdouble m12)
{
    GetExclusiveAccess();

    J2dTraceLn(J2D_TRACE_INFO, "D3DContext::SetTransform");
    if (d3dDevice == NULL) {
        ReleaseExclusiveAccess();
        return DDERR_GENERIC;
    }
    HRESULT res = D3D_OK;
    D3DMATRIX tx;

    if (xform == NULL) {
        J2dTraceLn(J2D_TRACE_VERBOSE, "  disabling transform");
        D3DUtils_SetIdentityMatrix(&tx);
    } else {
        J2dTraceLn(J2D_TRACE_VERBOSE, "  enabling transform");

        // copy values from AffineTransform object into native matrix array
        memset(&tx, 0, sizeof(D3DMATRIX));
        tx._11 = (float)m00;
        tx._12 = (float)m10;
        tx._21 = (float)m01;
        tx._22 = (float)m11;
        // The -0.5 adjustment is needed to correctly align texels to
        // pixels with orgthogonal projection matrix.
        // Note that we readjust vertex coordinates for cases
        // when we don't do texture mapping or use D3DPT_LINESTRIP.
        tx._41 = (float)m02-0.5f;
        tx._42 = (float)m12-0.5f;

        tx._33 = 1.0f;
        tx._44 = 1.0f;
    }

    J2dTraceLn(J2D_TRACE_VERBOSE, "  setting new tx matrix");
    J2dTraceLn4(J2D_TRACE_VERBOSE,
                "  %5f %5f %5f %5f", tx._11, tx._12, tx._13, tx._14);
    J2dTraceLn4(J2D_TRACE_VERBOSE,
                "  %5f %5f %5f %5f", tx._21, tx._22, tx._23, tx._24);
    J2dTraceLn4(J2D_TRACE_VERBOSE,
                "  %5f %5f %5f %5f", tx._31, tx._32, tx._33, tx._34);
    J2dTraceLn4(J2D_TRACE_VERBOSE,
                "  %5f %5f %5f %5f", tx._41, tx._42, tx._43, tx._44);
    if (FAILED(res = d3dDevice->SetTransform(D3DTRANSFORMSTATE_WORLD, &tx))) {
        DebugPrintDirectDrawError(res, "D3DContext::SetTransform failed");
    }

    ReleaseExclusiveAccess();
    return res;
}

/**
 * This method assumes that ::SetRenderTarget has already
 * been called. SetRenderTarget creates and attaches a
 * depth buffer to the target surface prior to setting it
 * as target surface to the device.
 */
HRESULT
D3DContext::SetClip(JNIEnv *env, jobject clip,
                    jboolean isRect,
                    int x1, int y1,
                    int x2, int y2)
{
    HRESULT res;
    static J2D_XY_VERTEX clipRect[] = {
#ifdef USE_SINGLE_VERTEX_FORMAT
        { 0.0f, 0.0f, 1.0f, 0xffffffff, 0.0f, 0.0f },
        { 0.0f, 0.0f, 1.0f, 0xffffffff, 0.0f, 0.0f },
        { 0.0f, 0.0f, 1.0f, 0xffffffff, 0.0f, 0.0f },
        { 0.0f, 0.0f, 1.0f, 0xffffffff, 0.0f, 0.0f }
#else
        // Note that we use D3DFVF_XYZ vertex format
        // implies 0xffffffff diffuse color, so we don't
        // have to specify it.
        { 0.0f, 0.0f, 1.0f },
        { 0.0f, 0.0f, 1.0f },
        { 0.0f, 0.0f, 1.0f },
        { 0.0f, 0.0f, 1.0f },
#endif // USE_SINGLE_VERTEX_FORMAT
    };
    static J2DXY_HEXA spanVx[MAX_CACHED_SPAN_VX_NUM];

    J2dTraceLn(J2D_TRACE_INFO, "D3DContext::SetClip");
    J2dTraceLn5(J2D_TRACE_VERBOSE,
                "  x1=%-4d y1=%-4d x2=%-4d y2=%-4d isRect=%-2d",
                x1, y1, x2, y2, isRect);
    GetExclusiveAccess();
    // the target surface must already be set
    if (d3dDevice == NULL || ddTargetSurface == NULL) {
        ReleaseExclusiveAccess();
        return DDERR_GENERIC;
    }


    // Must do EndScene prior to setting a new clip, otherwise the
    // primitives which are already in the pipeline will be rendered with
    // the new clip when we do EndScene.
    ForceEndScene();

    if (clip == NULL) {
        J2dTraceLn(J2D_TRACE_VERBOSE,
                   "D3DContext::SetClip: disabling clip (== NULL)");
        res = d3dDevice->SetRenderState(D3DRENDERSTATE_ZENABLE, D3DZB_FALSE);
        ReleaseExclusiveAccess();
        return res;
    } else if (isRect) {
        // optimization: disable depth buffer if the clip is equal to
        // the size of the viewport
        int w = ddTargetSurface->GetDXSurface()->GetWidth();
        int h = ddTargetSurface->GetDXSurface()->GetHeight();
        if (x1 == 0 && y1 == 0 && x2 == w && y2 == h) {
            J2dTraceLn(J2D_TRACE_VERBOSE,
                       "D3DContext::SetClip: disabling clip (== viewport)");
            res = d3dDevice->SetRenderState(D3DRENDERSTATE_ZENABLE, D3DZB_FALSE);
            ReleaseExclusiveAccess();
            return res;
        }
    }

    // save the old settings
    DWORD dwAlphaSt, dwSrcBlendSt, dwDestBlendSt;
    d3dDevice->GetRenderState(D3DRENDERSTATE_ALPHABLENDENABLE, &dwAlphaSt);
    d3dDevice->GetRenderState(D3DRENDERSTATE_SRCBLEND, &dwSrcBlendSt);
    d3dDevice->GetRenderState(D3DRENDERSTATE_DESTBLEND, &dwDestBlendSt);

    d3dDevice->SetRenderState(D3DRENDERSTATE_ALPHABLENDENABLE, TRUE);
    d3dDevice->SetRenderState(D3DRENDERSTATE_SRCBLEND, D3DBLEND_ZERO);
    d3dDevice->SetRenderState(D3DRENDERSTATE_DESTBLEND, D3DBLEND_ONE);

    // disable texturing
    if (lastTexture[0] != NULL) {
        // note that we do not restore the texture after we set the clip,
        // it will be reset the next time a texturing operation is performed
        SetTexture(NULL);
    }

    D3DMATRIX tx, idTx;
    d3dDevice->GetTransform(D3DTRANSFORMSTATE_WORLD, &tx);
    D3DUtils_SetIdentityMatrix(&idTx);
    d3dDevice->SetTransform(D3DTRANSFORMSTATE_WORLD, &idTx);

    // The depth buffer is first cleared with zeroes, which is the farthest
    // plane from the viewer (our projection matrix is an inversed orthogonal
    // transform).
    // To set the clip we'll render the clip spans with Z coordinates of 1.0f
    // (the closest to the viewer). Since all rendering primitives
    // have their vertices' Z coordinate set to 0.0, they will effectively be
    // clipped because the Z depth test for them will fail (vertex with 1.0
    // depth is closer than the one with 0.0f)
    d3dDevice->SetRenderState(D3DRENDERSTATE_ZENABLE, D3DZB_TRUE);
    d3dDevice->SetRenderState(D3DRENDERSTATE_ZWRITEENABLE, TRUE);
    d3dDevice->SetRenderState(D3DRENDERSTATE_ZFUNC, D3DCMP_ALWAYS);
    d3dDevice->Clear(0, NULL, D3DCLEAR_ZBUFFER, 0L, 0.0f, 0x0L);

    float fx1, fy1, fx2, fy2;
    if (SUCCEEDED(d3dDevice->BeginScene())) {
        if (isRect) {
            fx1 = (float)x1; fy1 = (float)y1;
            fx2 = (float)x2; fy2 = (float)y2;
            D3DU_INIT_VERTEX_QUAD_XY(clipRect, fx1, fy1, fx2, fy2);
            res = d3dDevice->DrawPrimitive(D3DPT_TRIANGLEFAN, D3DFVF_XY_VERTEX,
                                           clipRect, 4, NULL);
        } else {
            RegionData clipInfo;
            Region_GetInfo(env, clip, &clipInfo);
            SurfaceDataBounds span;
            J2DXY_HEXA *pHexa = (J2DXY_HEXA*)spanVx;
            jint numOfCachedSpans = 0;

            Region_StartIteration(env, &clipInfo);
            while (Region_NextIteration(&clipInfo, &span)) {
                fx1 = (float)(span.x1); fy1 = (float)(span.y1);
                fx2 = (float)(span.x2); fy2 = (float)(span.y2);
                D3DU_INIT_VERTEX_XYZ_6(*pHexa, fx1, fy1, fx2, fy2, 1.0f);
                numOfCachedSpans++;
                pHexa = (J2DXY_HEXA*)PtrAddBytes(pHexa, sizeof(J2DXY_HEXA));
                if (numOfCachedSpans >= MAX_CACHED_SPAN_VX_NUM) {
                    res = d3dDevice->DrawPrimitive(D3DPT_TRIANGLELIST,
                                                   D3DFVF_XY_VERTEX,
                                                   (void*)spanVx,
                                                   6*numOfCachedSpans, NULL);
                    numOfCachedSpans = 0;
                    pHexa = (J2DXY_HEXA*)spanVx;
                    if (FAILED(res)) {
                        break;
                    }
                }
            }
            if (numOfCachedSpans > 0) {
                res = d3dDevice->DrawPrimitive(D3DPT_TRIANGLELIST,
                                               D3DFVF_XY_VERTEX,
                                               (void*)spanVx,
                                               6*numOfCachedSpans, NULL);
            }
            Region_EndIteration(env, &clipInfo);
        }
        res = d3dDevice->EndScene();
    }

    // reset the transform
    d3dDevice->SetTransform(D3DTRANSFORMSTATE_WORLD, &tx);

    // reset the alpha compositing
    d3dDevice->SetRenderState(D3DRENDERSTATE_ALPHABLENDENABLE, dwAlphaSt);
    d3dDevice->SetRenderState(D3DRENDERSTATE_SRCBLEND, dwSrcBlendSt);
    d3dDevice->SetRenderState(D3DRENDERSTATE_DESTBLEND, dwDestBlendSt);

    // Setup the depth buffer.
    // We disable further updates to the depth buffer: it should only
    // be updated in SetClip method.
    d3dDevice->SetRenderState(D3DRENDERSTATE_ZWRITEENABLE, FALSE);
    d3dDevice->SetRenderState(D3DRENDERSTATE_ZFUNC, D3DCMP_LESS);

    ReleaseExclusiveAccess();
    return res;
}

DXSurface *
D3DContext::GetMaskTexture()
{
    if (lpMaskTexture != NULL) {
        // This in theory should never happen since
        // we're using managed textures, but in case
        // we switch to using something else.
        if (FAILED(lpMaskTexture->IsLost())) {
            lpMaskTexture->Restore();
        }
        return lpMaskTexture;
    }
    InitMaskTileTexture();
    return lpMaskTexture;
}


HRESULT
D3DContext::InitMaskTileTexture()
{
    HRESULT res;

    J2dTraceLn(J2D_TRACE_INFO, "D3DContext::InitMaskTileTexture");
    if (lpMaskTexture != NULL) {
        lpMaskTexture->Release();
    }
    lpMaskTexture = NULL;

    DWORD caps2 = 0, caps = DDSCAPS_TEXTURE;
    if (bIsHWRasterizer) {
        caps2 = DDSCAPS2_TEXTUREMANAGE;
    } else {
        caps |= DDSCAPS_SYSTEMMEMORY;
    }

    if (FAILED(res =
               dxObject->CreateSurface(DDSD_WIDTH|DDSD_HEIGHT|DDSD_CAPS|
                                       DDSD_PIXELFORMAT|DDSD_TEXTURESTAGE,
                                       caps,
                                       caps2,
                                       &maskTileTexFormat,
                                       D3DSD_MASK_TILE_SIZE, D3DSD_MASK_TILE_SIZE,
                                       (DXSurface **)&lpMaskTexture, 0)))
    {
        // in case we want to do something here later..
        DebugPrintDirectDrawError(res,
                                  "D3DContext::InitMaskTileTexture: "\
                                  "failed to create mask tile texture");
    }
    return res;
}

HRESULT
D3DContext::UploadImageToTexture(DXSurface *texture, jubyte *pixels,
                                 jint dstx, jint dsty,
                                 jint srcx, jint srcy,
                                 jint srcWidth, jint srcHeight,
                                 jint srcStride)
{
    HRESULT res = D3D_OK;
    SurfaceDataRasInfo rasInfo;


    RECT r = { dstx, dsty, dstx+srcWidth, dsty+srcHeight };
    J2dTraceLn(J2D_TRACE_INFO, "D3DContext::UploadImageToTexture");
    J2dTraceLn4(J2D_TRACE_VERBOSE,
                " rect={%-4d, %-4d, %-4d, %-4d}",
                r.left, r.top, r.right, r.bottom);
    // REMIND: it may be faster to lock for NULL instead of
    // rect, need to test later.
    if (FAILED(res = texture->Lock(&r, &rasInfo,
                                   DDLOCK_WAIT|DDLOCK_NOSYSLOCK, NULL)))
    {
        DebugPrintDirectDrawError(res,
                                  "D3DContext::UploadImageToTexture: could "\
                                  "not lock texture");
        return res;
    }

    if (rasInfo.pixelStride == 1) {
        // 8bpp alpha texture
        void *pSrcPixels = PtrCoord(pixels, srcx, 1, srcy, srcStride);
        void *pDstPixels = rasInfo.rasBase;
        do {
            memcpy(pDstPixels, pSrcPixels, srcWidth);
            pSrcPixels = PtrAddBytes(pSrcPixels, srcStride);
            pDstPixels = PtrAddBytes(pDstPixels, rasInfo.scanStride);
        } while (--srcHeight > 0);
    } else {
        // ARGB texture
        jubyte *pSrcPixels = (jubyte*)PtrCoord(pixels, srcx, 1, srcy, srcStride);
        jint *pDstPixels = (jint*)rasInfo.rasBase;
        for (int yy = 0; yy < srcHeight; yy++) {
            for (int xx = 0; xx < srcWidth; xx++) {
                jubyte pix = pSrcPixels[xx];
                StoreIntArgbFrom4ByteArgb(pDstPixels, 0, xx,
                                          pix, pix, pix, pix);
            }
            pSrcPixels = (jubyte*)PtrAddBytes(pSrcPixels, srcStride);
            pDstPixels = (jint*)PtrAddBytes(pDstPixels, rasInfo.scanStride);
        }
    }
    return texture->Unlock(&r);
}

HRESULT
D3DContext::InitGlyphCache()
{
    HRESULT res = D3D_OK;

    if (glyphCache != NULL) {
        return D3D_OK;
    }

    if (!glyphCacheAvailable) {
        return DDERR_GENERIC;
    }

    J2dTraceLn(J2D_TRACE_INFO, "D3DContext::InitGlyphCache");

    // init glyph cache data structure
    glyphCache = AccelGlyphCache_Init(D3D_GCACHE_WIDTH,
                                      D3D_GCACHE_HEIGHT,
                                      D3D_GCACHE_CELL_WIDTH,
                                      D3D_GCACHE_CELL_HEIGHT,
                                      NULL);
    if (glyphCache == NULL) {
        J2dRlsTraceLn(J2D_TRACE_ERROR,
                      "D3DContext::InitGlyphCache: "\
                      "could not init D3D glyph cache");
        glyphCacheAvailable = FALSE;
        return DDERR_GENERIC;
    }

    DWORD caps2 = 0, caps = DDSCAPS_TEXTURE;
    if (bIsHWRasterizer) {
        caps2 = DDSCAPS2_TEXTUREMANAGE;
    } else {
        caps |= DDSCAPS_SYSTEMMEMORY;
    }
    if (FAILED(res =
               dxObject->CreateSurface(DDSD_WIDTH|DDSD_HEIGHT|DDSD_CAPS|
                                       DDSD_PIXELFORMAT|DDSD_TEXTURESTAGE,
                                       caps,
                                       caps2,
                                       &maskTileTexFormat,
                                       D3D_GCACHE_WIDTH, D3D_GCACHE_HEIGHT,
                                       (DXSurface **)&lpGlyphCacheTexture, 0)))
    {
        DebugPrintDirectDrawError(res,
                                  "D3DContext::InitGlyphCache: glyph cache "\
                                  "texture creation failed");
        glyphCacheAvailable = FALSE;
        return res;
    }
    return res;
}

HRESULT
D3DContext::GlyphCacheAdd(JNIEnv *env, GlyphInfo *glyph)
{
    HRESULT res = D3D_OK;
    if (!glyphCacheAvailable || glyph->image == NULL) {
        return DDERR_GENERIC;
    }

    AccelGlyphCache_AddGlyph(glyphCache, glyph);

    if (glyph->cellInfo != NULL) {
        // store glyph image in texture cell
        res = UploadImageToTexture(lpGlyphCacheTexture, (jubyte*)glyph->image,
                                   glyph->cellInfo->x, glyph->cellInfo->y,
                                   0, 0,
                                   glyph->width, glyph->height,
                                   glyph->width);
    }

    return res;
}

void
D3DContext::SetColor(jint eargb, jint flags)
{
    J2dTraceLn2(J2D_TRACE_INFO,
                "D3DContext::SetColor: eargb=%08x flags=%d", eargb, flags);

    /*
     * The colorPixel field is a 32-bit ARGB premultiplied color
     * value.  The incoming eargb field is a 32-bit ARGB value
     * that is not premultiplied.  If the alpha is not 1.0 (255)
     * then we need to premultiply the color components before
     * storing it in the colorPixel field.
     */
    jint a = (eargb >> 24) & 0xff;

    if (a == 0xff) {
        colorPixel = eargb;
    } else {
        jint a2 = a + (a >> 7);
        jint r = (((eargb >> 16) & 0xff) * a2) >> 8;
        jint g = (((eargb >>  8) & 0xff) * a2) >> 8;
        jint b = (((eargb      ) & 0xff) * a2) >> 8;
        colorPixel = (a << 24) | (r << 16) | (g << 8) | (b << 0);
    }
    J2dTraceLn1(J2D_TRACE_VERBOSE, "  updated color: colorPixel=%08x",
                colorPixel);
}

void
D3DContext::ResetComposite()
{
    J2dTraceLn(J2D_TRACE_INFO, "D3DContext::ResetComposite");
    GetExclusiveAccess();
    if (d3dDevice == NULL) {
        ReleaseExclusiveAccess();
        return;
    }
    d3dDevice->SetRenderState(D3DRENDERSTATE_ALPHABLENDENABLE, TRUE);
    compState = sun_java2d_SunGraphics2D_COMP_ISCOPY;
    extraAlpha = 1.0f;
    ReleaseExclusiveAccess();
}

void
D3DContext::SetAlphaComposite(jint rule, jfloat ea, jint flags)
{
    J2dTraceLn3(J2D_TRACE_INFO,
                "D3DContext::SetAlphaComposite: rule=%-1d ea=%f flags=%d",
                rule, ea, flags);
    GetExclusiveAccess();

    if (d3dDevice == NULL) {
        ReleaseExclusiveAccess();
        return;
    }

    // we can safely disable blending when:
    //   - comp is SrcNoEa or SrcOverNoEa, and
    //   - the source is opaque
    // (turning off blending can have a large positive impact on
    // performance);
    if ((rule == RULE_Src || rule == RULE_SrcOver) &&
        (ea == 1.0f) &&
        (flags & D3DC_SRC_IS_OPAQUE))
     {
         J2dTraceLn1(J2D_TRACE_VERBOSE,
                     "  disabling alpha comp rule=%-1d ea=1.0 src=opq)", rule);
         d3dDevice->SetRenderState(D3DRENDERSTATE_ALPHABLENDENABLE, FALSE);
     } else {
        J2dTraceLn2(J2D_TRACE_VERBOSE,
                    "  enabling alpha comp (rule=%-1d ea=%f)", rule, ea);
        d3dDevice->SetRenderState(D3DRENDERSTATE_ALPHABLENDENABLE, TRUE);

        d3dDevice->SetRenderState(D3DRENDERSTATE_SRCBLEND,
                                  StdBlendRules[rule].src);
        d3dDevice->SetRenderState(D3DRENDERSTATE_DESTBLEND,
                                  StdBlendRules[rule].dst);
     }

    // update state
    compState = sun_java2d_SunGraphics2D_COMP_ALPHA;
    extraAlpha = ea;

    if (extraAlpha == 1.0f) {
        blitPolygonPixel = 0xffffffff;
    } else {
        // the 0xffffffff pixel needs to be premultiplied by extraAlpha
        jint ea = (jint)(extraAlpha * 255.0f + 0.5f) & 0xff;
        blitPolygonPixel = (ea << 24) | (ea << 16) | (ea << 8) | (ea << 0);
    }

    ReleaseExclusiveAccess();
}

HRESULT D3DContext::CreateSurface(JNIEnv *env, jint width, jint height,
                                  jint depth, jint transparency,
                                  jint d3dSurfaceType,
                                  DXSurface **dxSurface, jint* pType)
{
    DWORD dwFlags = 0, ddsCaps = 0, ddsCaps2 = 0;
    D3DTextureTableCell *cell = NULL;
    DXSurface *lpRetSurface = NULL;
    HRESULT res;

    GetExclusiveAccess();

    dwFlags = DDSD_CAPS | DDSD_HEIGHT | DDSD_WIDTH;

    if (d3dSurfaceType & D3D_TEXTURE_SURFACE) {
        ddsCaps |= DDSCAPS_TEXTURE;
        dwFlags |= DDSD_PIXELFORMAT | DDSD_TEXTURESTAGE;

        jint trIdx = D3D_TR_IDX(transparency);
        jint depthIdx = D3D_DEPTH_IDX(depth);
        cell = &textureTable[trIdx][depthIdx];
        if (cell->pfType == PF_INVALID) {
            ReleaseExclusiveAccess();
            J2dTraceLn2(J2D_TRACE_ERROR,
                        "D3DContext::CreateSurface: no texture "\
                        "pixel format for depth: %d transparency=%d",
                        depth, transparency);
            return DDERR_NOTFOUND;
        }
        if (pType != NULL) *pType = cell->pfType;
        if (d3dSurfaceType & D3D_RENDER_TARGET) {
            // RTT is requested => must be allocated non-managed and
            // non-systemmemory pool.
            // REMIND: must check if this is supported by
            // the device, as it may not have a local video memory, only AGP
            // may be we should just use VIDEOMEMORY
            // NOTE: this will likely fail if the device is not accelerated
            ddsCaps |= DDSCAPS_LOCALVIDMEM;
        } else {
            // This is a normal texture, allocate in managed pool if the device
            // is accelerated, otherwise must use system memory.
            if (bIsHWRasterizer) {
                ddsCaps2 |= DDSCAPS2_TEXTUREMANAGE;
            } else {
                ddsCaps |= DDSCAPS_SYSTEMMEMORY;
            }
        }

        if (IsPow2TexturesOnly()) {
            jint w, h;
            for (w = 1; width  > w; w <<= 1);
            for (h = 1; height > h; h <<= 1);
            width = w;
            height = h;
        }
        if (IsSquareTexturesOnly()) {
            if (width > height) {
                height = width;
            } else {
                width = height;
            }
        }

        DWORD dwRatio = GetMaxTextureAspectRatio();
        // Note: Reference rasterizer returns ratio '0',
        // which presumably means 'any'.
        if ((DWORD)width  > GetMaxTextureWidth()    ||
            (DWORD)height > GetMaxTextureHeight()   ||
            (DWORD)width  < GetMinTextureWidth()    ||
            (DWORD)height < GetMinTextureHeight()   ||
            ((dwRatio > 0) && ((DWORD)(width/height) > dwRatio ||
                               (DWORD)(height/width) > dwRatio)))
        {
            ReleaseExclusiveAccess();
            J2dRlsTraceLn2(J2D_TRACE_ERROR,
                           "D3DContext::CreateSurface: failed to create"\
                           " texture: dimensions %dx%d not supported.",
                           width, height);
            J2dRlsTraceLn5(J2D_TRACE_ERROR,
                           "  Supported texture dimensions: %dx%d-%dxd% "\
                           " with max ratio %f.",
                           GetMinTextureWidth(), GetMinTextureHeight(),
                           GetMaxTextureWidth(), GetMaxTextureHeight(),
                           GetMaxTextureAspectRatio());
            return D3DERR_TEXTURE_BADSIZE;
        }
    } else if (d3dSurfaceType & D3D_PLAIN_SURFACE) {
        ddsCaps |= DDSCAPS_OFFSCREENPLAIN |
            (bIsHWRasterizer ? DDSCAPS_VIDEOMEMORY : DDSCAPS_SYSTEMMEMORY);
    } else if (d3dSurfaceType & D3D_ATTACHED_SURFACE) {
        // can't handle this for now
        J2dRlsTraceLn(J2D_TRACE_ERROR,
                      "D3DContext::CreateSurface: Can't create attached"\
                      " surfaces using this code path yet");
        ReleaseExclusiveAccess();
        return DDERR_GENERIC;
    }
    if (d3dSurfaceType & D3D_RENDER_TARGET) {
        ddsCaps |= DDSCAPS_3DDEVICE;
    }

    if (SUCCEEDED(res = dxObject->CreateSurface(dwFlags, ddsCaps, ddsCaps2,
                                                (cell != NULL) ?
                                                    &cell->pddpf : NULL,
                                                width, height,
                                                &lpRetSurface,
                                                0/*backbuffers*/)))
    {
        if (d3dSurfaceType & D3D_RENDER_TARGET) {
            if (FAILED(res = AttachDepthBuffer(lpRetSurface))) {
                lpRetSurface->Release();
                delete lpRetSurface;
                ReleaseExclusiveAccess();
                return res;
            }
            // Attempt to set the new surface as a temporary render target;
            // in some cases this may fail. For example, if undocumented maximum
            // Direct3D target surface dimensions were exceeded (2048 in some
            // cases).
            if (d3dDevice != NULL) {
                FlushD3DQueueForTarget(NULL);

                IDirectDrawSurface7 *lpDDSurface = NULL;
                HRESULT res1 = d3dDevice->GetRenderTarget(&lpDDSurface);

                // we are holding a lock for the context, so we can
                // change/restore the current render target safely
                res = d3dDevice->SetRenderTarget(lpRetSurface->GetDDSurface(), 0);
                if (SUCCEEDED(res1) && lpDDSurface != NULL) {
                    d3dDevice->SetRenderTarget(lpDDSurface, 0);
                }
                if (FAILED(res)) {
                    DebugPrintDirectDrawError(res,
                        "D3DContext::CreateSurface: cannot set new surface as "\
                        "temp. render target");
                    lpRetSurface->Release();
                    delete lpRetSurface;
                    ReleaseExclusiveAccess();
                    return res;
                }
            }
        }
        *dxSurface = lpRetSurface;
    } else {
        DebugPrintDirectDrawError(res,
                                  "D3DContext::CreateSurface: error"\
                                  " creating surface");
    }

    ReleaseExclusiveAccess();
    return res;
}

HRESULT
D3DContext::AttachDepthBuffer(DXSurface *dxSurface)
{
    HRESULT res;

    J2dTraceLn(J2D_TRACE_INFO, "D3DContext::AttachDepthBuffer");

    if (dxSurface == NULL) {
        return DDERR_GENERIC;
    }

    GetExclusiveAccess();

    // initialize the depth buffer format it needed
    if (depthBufferFormat.dwSize == 0) {
        // Some hardware has a restriction that the target surface and the
        // attached depth buffer must have the same bit depth, so we should
        // attempt to find a depth pixel format with the same depth as
        // the target.
        DWORD prefDepth = dxSurface->ddsd.ddpfPixelFormat.dwRGBBitCount;
        if (FAILED(res = D3DUtils_FindDepthBufferFormat(d3dObject,
                                                        prefDepth,
                                                        &depthBufferFormat,
                                                        pDeviceGUID)))
        {
            DebugPrintDirectDrawError(res,
                                      "D3DContext::AttachDepthBuffer: "\
                                      "can't find depth buffer format");
            ReleaseExclusiveAccess();
            return res;
        }
    }
    if (FAILED(res = dxSurface->AttachDepthBuffer(dxObject,
                                                  bIsHWRasterizer,
                                                  &depthBufferFormat)))
    {
        DebugPrintDirectDrawError(res,
                                  "D3DContext::AttachDepthBuffer: "\
                                  "can't attach depth buffer or it is lost");
    }

    ReleaseExclusiveAccess();
    return res;
}

/**
 * We go into the pains of maintaining the list of set textures
 * instead of just calling GetTexture() and comparing the old one
 * with the new one because it's actually noticeably slower to call
 * GetTexture() (note that we'd have to then call Release() on the
 * texture since GetTexture() increases texture's ref. count).
 */
HRESULT /*NOLOCK*/
D3DContext::SetTexture(DXSurface *dxSurface, DWORD dwStage)
{
    HRESULT res = D3D_OK;
    IDirectDrawSurface7 *newTexture =
        dxSurface == NULL ? NULL : dxSurface->GetDDSurface();

    if (dwStage < 0 || dwStage > MAX_USED_TEXTURE_STAGE) {
        J2dTraceLn1(J2D_TRACE_ERROR,
                    "D3DContext::SetTexture: incorrect stage: %d", dwStage);
        return DDERR_GENERIC;
    }
    if (lastTexture[dwStage] != newTexture) {
        J2dTraceLn1(J2D_TRACE_VERBOSE,
                    "D3DContext::SetTexture: new texture=0x%x", newTexture);
        res = d3dDevice->SetTexture(dwStage, newTexture);
        lastTexture[dwStage] = SUCCEEDED(res) ? newTexture : NULL;
    }
    return res;
}

void
D3DContext::FlushD3DQueueForTarget(DDrawSurface *ddSurface)
{
    GetExclusiveAccess();
    J2dTraceLn2(J2D_TRACE_VERBOSE,
                "D3DContext::FlushD3DQueueForTarget surface=0x%x target=0x%x",
                ddSurface, ddTargetSurface);

    if ((ddSurface == ddTargetSurface || ddSurface == NULL) &&
        d3dDevice != NULL)
    {
        ForceEndScene();
    }
    ReleaseExclusiveAccess();
}

void
D3DContext::InvalidateIfTarget(JNIEnv *env, DDrawSurface *ddSurface)
{
    GetExclusiveAccess();
    if ((ddSurface == ddTargetSurface) && d3dDevice != NULL &&
        jD3DContext != NULL)
    {
        J2dTraceLn(J2D_TRACE_VERBOSE,
                   "D3DContext:InvalidateIfTarget: invalidating java context");

        jobject jD3DContext_tmp = env->NewLocalRef(jD3DContext);
        if (jD3DContext_tmp != NULL) {
            JNU_CallMethodByName(env, NULL, jD3DContext_tmp,
                                 "invalidateContext", "()V");
            env->DeleteLocalRef(jD3DContext_tmp);
        }
    }
    ReleaseExclusiveAccess();
}

void /*NOLOCK*/
D3DContext::UpdateState(jbyte newState)
{
    // Try to minimize context switching by only changing
    // attributes when necessary.
    if (newState != opState) {
        // if the new context is texture rendering
        if (newState & STATE_TEXTURE) {
            // we can be here because of two reasons:
            // old context wasn't STATE_TEXTURE or
            // the new STATE_TEXTURE_STAGE is different

            // do the appropriate texture stage setup if needed
            DWORD dwAA1, dwCA1;
            BOOL bUpdateStateNeeded = FALSE;
            if ((newState & STATE_TEXTURE_STAGE_MASK) &&
                !(opState & STATE_TEXTURE_STAGE_MASK))
            {
                // setup mask rendering
                dwAA1 = (D3DTA_TEXTURE|D3DTA_ALPHAREPLICATE);
                dwCA1 = (D3DTA_TEXTURE|D3DTA_ALPHAREPLICATE);
                bUpdateStateNeeded = TRUE;
                J2dTraceLn(J2D_TRACE_VERBOSE,
                           "UpdateState: STATE_TEXTURE_STAGE_MASK");
            } else if ((newState & STATE_TEXTURE_STAGE_BLIT) &&
                       !(opState & STATE_TEXTURE_STAGE_BLIT))
            {
                // setup blit rendering
                dwAA1 = D3DTA_TEXTURE;
                dwCA1 = D3DTA_TEXTURE;
                bUpdateStateNeeded = TRUE;
                J2dTraceLn(J2D_TRACE_VERBOSE,
                           "UpdateState: STATE_TEXTURE_STAGE_BLIT");
            }

            // this optimization makes sense because if the state
            // is changing from non-texture to texture, we don't necessarily
            // need to update the texture stage state
            if (bUpdateStateNeeded) {
                d3dDevice->SetTextureStageState(0, D3DTSS_ALPHAARG1, dwAA1);
                d3dDevice->SetTextureStageState(0, D3DTSS_COLORARG1, dwCA1);
            } else {
                J2dTraceLn2(J2D_TRACE_WARNING,
                            "UpdateState: no context changes were made! "\
                            "current=0x%x new=0x%x", opState, newState);
            }
        } else {
            J2dTraceLn(J2D_TRACE_VERBOSE,
                       "UpdateState: STATE_RENDEROP");
            // if switching from a texture rendering state
            if (opState & STATE_TEXTURE) {
                // disable texture rendering
                // we don't need to change texture stage states
                // because they're irrelevant if the texture
                // is not set
                // REMIND: another possible optimiziation: instead of
                // setting texture to NULL, change the texture stage state
                SetTexture(NULL);
            }
        }
        opState = newState;
    }
}

HRESULT D3DContext::BeginScene(jbyte newState)
{
    if (!d3dDevice) {
        return DDERR_GENERIC;
    } else {
        UpdateState(newState);
        if (!bBeginScenePending) {
            bBeginScenePending = TRUE;
#ifdef DEBUG
            endSceneQueueDepth = 0;
#endif /* DEBUG */
            HRESULT res = d3dDevice->BeginScene();
            J2dTraceLn(J2D_TRACE_INFO, "D3DContext::BeginScene");
            if (FAILED(res)) {
                // this will cause context reinitialization
                opState = STATE_UNDEFINED;
            }
            return res;
        }
        return D3D_OK;
    }
}

HRESULT D3DContext::EndScene(HRESULT ddResult) {
    if (FAILED(ddResult)) {
        return ForceEndScene();
    }
#ifdef DEBUG
    endSceneQueueDepth++;
#endif /* DEBUG */
    return D3D_OK;
}

HRESULT D3DContext::ForceEndScene() {
    if (bBeginScenePending) {
        bBeginScenePending = FALSE;
        J2dTraceLn(J2D_TRACE_INFO, "D3DContext::ForceEndScene");
#ifdef DEBUG
        J2dTraceLn1(J2D_TRACE_VERBOSE, "  queue depth=%d",
                    endSceneQueueDepth);
        endSceneQueueDepth = 0;
#endif /* DEBUG */
        return d3dDevice->EndScene();
    }
    return D3D_OK;
}

/**
 * Utility function: checks the result, calls RestoreSurface
 * on the destination surface, and throws InvalidPipeException.
 */
static void
D3DContext_CheckResult(JNIEnv *env, HRESULT res, jlong pDest) {
    J2dTraceLn(J2D_TRACE_INFO, "D3DContext_CheckResult");
    if (FAILED(res)) {
        J2dTraceLn(J2D_TRACE_ERROR,
                   "D3DContext_CheckResult: failed, restoring dest surface");
        Win32SDOps *dstOps = (Win32SDOps *)jlong_to_ptr(pDest);
        if (dstOps != NULL) {
            // RestoreSurface for surfaces associated
            // with VolatileImages only marks them lost, not
            // attempting to restore. This is done later
            // when VolatileImage.validate() is called.
            dstOps->RestoreSurface(env, dstOps);

            // if this is an "unexpected" error, disable acceleration
            // of this image to avoid an infinite recreate/render/error loop
            if (res != DDERR_SURFACELOST && res != DDERR_INVALIDMODE &&
                res != DDERR_GENERIC && res != DDERR_WASSTILLDRAWING &&
                res != DDERR_SURFACEBUSY)
            {
                jobject sdObject = env->NewLocalRef(dstOps->sdOps.sdObject);
                if (sdObject != NULL) {
                    JNU_CallMethodByName(env, NULL, sdObject,
                                         "disableD3D", "()V");
                    env->DeleteLocalRef(sdObject);
                }
            }

        }
        SurfaceData_ThrowInvalidPipeException(env, "Surface Lost");
    }
}

/*
 * Class:     sun_java2d_d3d_D3DContext
 * Method:    setTransform
 * Signature: (JLLjava/awt/geom/AffineTransform;DDDDDD)V
 */
JNIEXPORT void JNICALL Java_sun_java2d_d3d_D3DContext_setTransform
    (JNIEnv *env, jobject d3dc, jlong pCtx, jlong pDest, jobject xform,
     jdouble m00, jdouble m10,
     jdouble m01, jdouble m11,
     jdouble m02, jdouble m12)
{
    D3DContext *pd3dc = (D3DContext *)jlong_to_ptr(pCtx);

    J2dTraceLn(J2D_TRACE_INFO, "D3DContext_setTransform");
    if (pd3dc != NULL) {
        HRESULT res = pd3dc->SetTransform(xform,
                                          m00,  m10,
                                          m01,  m11,
                                          m02,  m12);
        D3DContext_CheckResult(env, res, pDest);
    }
}

/*
 * Class:     sun_java2d_d3d_D3DContext
 * Method:    resetTransform
 * Signature: (JLL)V
 */
JNIEXPORT void JNICALL Java_sun_java2d_d3d_D3DContext_resetTransform
    (JNIEnv *env, jobject d3dc, jlong pCtx, jlong pDest)
{
    D3DContext *pd3dc = (D3DContext *)jlong_to_ptr(pCtx);

    J2dTraceLn(J2D_TRACE_INFO, "D3DContext_resetTransform");
    if (pd3dc != NULL) {
        HRESULT res = pd3dc->SetTransform(NULL, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
        D3DContext_CheckResult(env, res, pDest);
    }
}

/*
 * Class:     sun_java2d_d3d_D3DContext
 * Method:    setClip
 * Signature: (JLLsun/java2d/pipe/Region;ZIIII)V
 */
JNIEXPORT void JNICALL Java_sun_java2d_d3d_D3DContext_setClip
    (JNIEnv *env, jobject d3dc, jlong pCtx, jlong pDest,
     jobject clip, jboolean isRect,
     jint x1, jint y1, jint x2, jint y2)
{
    D3DContext *pd3dc = (D3DContext *)jlong_to_ptr(pCtx);

    J2dTraceLn(J2D_TRACE_INFO, "D3DContext_setClip");
    if (pd3dc != NULL) {
        HRESULT res = pd3dc->SetClip(env, clip, isRect, x1, y1, x2, y2);
        D3DContext_CheckResult(env, res, pDest);
    }
}

/*
 * Class:     sun_java2d_d3d_D3DContext
 * Method:    resetClip
 * Signature: (JLLsun/java2d/pipe/Region;Z)V
 */
JNIEXPORT void JNICALL Java_sun_java2d_d3d_D3DContext_resetClip
    (JNIEnv *env, jobject d3dc, jlong pCtx, jlong pDest)
{
    D3DContext *pd3dc = (D3DContext *)jlong_to_ptr(pCtx);

    J2dTraceLn(J2D_TRACE_INFO, "D3DContext_resetClip");
    if (pd3dc != NULL) {
        HRESULT res = pd3dc->SetClip(env, NULL, JNI_FALSE, 0, 0, 0, 0);
        D3DContext_CheckResult(env, res, pDest);
    }
}

/*
 * Class:     sun_java2d_d3d_D3DContext
 * Method:    setRenderTarget
 * Signature: (JJ)V
 */
JNIEXPORT void JNICALL Java_sun_java2d_d3d_D3DContext_setRenderTarget
    (JNIEnv *env, jobject d3dc, jlong pCtx, jlong pDest)
{
    D3DContext *pd3dc = (D3DContext *)jlong_to_ptr(pCtx);
    Win32SDOps *dstOps = (Win32SDOps *)jlong_to_ptr(pDest);

    J2dTraceLn(J2D_TRACE_INFO, "D3DContext_setRenderTarget");
    if (pd3dc != NULL && dstOps != NULL) {
        HRESULT res = pd3dc->SetRenderTarget(dstOps->lpSurface);
        D3DContext_CheckResult(env, res, pDest);
    }
}

/*
 * Class:     sun_java2d_d3d_D3DContext
 * Method:    setColor
 * Signature: (JII)V
 */
JNIEXPORT void JNICALL
Java_sun_java2d_d3d_D3DContext_setColor(JNIEnv *env, jobject oc,
                                        jlong pCtx,
                                        jint pixel, jint flags)
{
    D3DContext *d3dc = (D3DContext *)jlong_to_ptr(pCtx);

    J2dTraceLn(J2D_TRACE_INFO, "D3DContext_setColor");
    if (d3dc != NULL) {
        d3dc->SetColor(pixel, flags);
    }
}

/*
 * Class:     sun_java2d_d3d_D3DContext
 * Method:    setAlphaComposite
 * Signature: (JIFI)V
 */
JNIEXPORT void JNICALL
Java_sun_java2d_d3d_D3DContext_setAlphaComposite(JNIEnv *env, jobject oc,
                                                 jlong pCtx,
                                                 jint rule,
                                                 jfloat extraAlpha,
                                                 jint flags)
{
    D3DContext *d3dc = (D3DContext *)jlong_to_ptr(pCtx);

    J2dTraceLn(J2D_TRACE_INFO, "D3DContext_setAlphaComposite");
    if (d3dc != NULL) {
        d3dc->SetAlphaComposite(rule, extraAlpha, flags);
    }

}

JNIEXPORT void JNICALL
Java_sun_java2d_d3d_D3DContext_resetComposite(JNIEnv *env, jobject oc,
                                              jlong pCtx)
{
    D3DContext *d3dc = (D3DContext *)jlong_to_ptr(pCtx);

    J2dTraceLn(J2D_TRACE_INFO, "D3DContext_resetComposite");
    if (d3dc != NULL) {
        d3dc->ResetComposite();
    }
}

/*
 * Class:     sun_java2d_d3d_D3DContext
 * Method:    initNativeContext
 * Signature: (I)J
 */
JNIEXPORT jlong JNICALL
Java_sun_java2d_d3d_D3DContext_initNativeContext
  (JNIEnv *env, jobject d3dc, jint screen)
{
    J2dTraceLn1(J2D_TRACE_INFO, "D3DContext_initNativeContext screen=%d",
                screen);

    HMONITOR hMon = (HMONITOR)AwtWin32GraphicsDevice::GetMonitor(screen);
    DDrawObjectStruct *tmpDdInstance = GetDDInstanceForDevice(hMon);
    D3DContext *d3dContext = NULL;

    if (tmpDdInstance != NULL && tmpDdInstance->ddObject != NULL) {
        AwtToolkit::GetInstance().SendMessage(WM_AWT_D3D_CREATE_DEVICE,
                                              (WPARAM)tmpDdInstance->ddObject,
                                              NULL);
        d3dContext = tmpDdInstance->ddObject->GetD3dContext();
    }
    J2dTraceLn1(J2D_TRACE_VERBOSE,
                "D3DContext_initNativeContext created d3dContext=0x%x",
                d3dContext);

    return ptr_to_jlong(d3dContext);
}

/*
 * Class:     sun_java2d_d3d_D3DContext
 * Method:    getNativeDeviceCaps
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_sun_java2d_d3d_D3DContext_getNativeDeviceCaps
  (JNIEnv *env, jobject d3dc, jlong pCtx)
{
    D3DContext *d3dContext = (D3DContext *)jlong_to_ptr(pCtx);

    J2dTraceLn(J2D_TRACE_INFO, "D3DContext_getNativeDeviceCaps");
    if (d3dContext != NULL) {
        d3dContext->SetJavaContext(env, d3dc);
        return (jint)d3dContext->GetDeviceCaps();
    }
    return J2D_D3D_FAILURE;
}
