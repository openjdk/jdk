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

#ifndef D3DCONTEXT_H
#define D3DCONTEXT_H

#include "java_awt_Transparency.h"
#include "sun_java2d_d3d_D3DContext.h"
#include "ddrawObject.h"
extern "C" {
#include "glyphblitting.h"
#include "AccelGlyphCache.h"
}
#include "j2d_md.h"


// - State switching optimizations -----------------------------------

/**
 * The goal is to reduce device state switching as much as possible.
 * This means: don't reset the texture if not needed, don't change
 * the texture stage states unless necessary.
 * For this we need to track the current device state. So each operation
 * supplies its own operation type to BeginScene, which updates the state
 * as necessary.
 *
 * Another optimization is to use a single vertex format for
 * all primitives.
 *
 * See D3DContext::UpdateState() and D3DContext::BeginScene() for
 * more information.
 */

// The state is undefined, assume that complete initialization is
// needed.
#define STATE_UNDEFINED          (0 << 0)
// Current state uses texture mapping
#define STATE_TEXTURE            (1 << 0)
// Texture stage state which is used when mask is involved
// (text rendering, maskfill)
#define STATE_TEXTURE_STAGE_MASK (1 << 1)
// Texture stage state which is used when doing texture
// mapping in blits
#define STATE_TEXTURE_STAGE_BLIT (1 << 2)
// Texture stage state which is used when not doing
// texture mapping, only use diffuse color
#define STATE_TEXTURE_STAGE_POLY (1 << 3)
// Texture mapping operation which involves mask texture
#define STATE_MASKOP             (STATE_TEXTURE|STATE_TEXTURE_STAGE_MASK)
// Texture mapping operation which involves image texture
#define STATE_BLITOP             (STATE_TEXTURE|STATE_TEXTURE_STAGE_BLIT)
// Rendering operation which doesn't use texture mapping
#define STATE_RENDEROP           (STATE_TEXTURE_STAGE_POLY)

// The max. stage number we currently use (could not be
// larger than 7)
#define MAX_USED_TEXTURE_STAGE 0

// - Texture pixel format table  -------------------------------------
#define TR_OPAQUE      java_awt_Transparency_OPAQUE
#define TR_BITMASK     java_awt_Transparency_BITMASK
#define TR_TRANSLUCENT java_awt_Transparency_TRANSLUCENT

// depth indices for the D3DTextureTable type
#define DEPTH16_IDX 0
#define DEPTH24_IDX 1
#define DEPTH32_IDX 2
#define DEPTH_MAX_IDX 3

// corresponding transparency indices for the D3DTextureTable type
#define TR_OPAQUE_IDX 0
#define TR_BITMASK_IDX 1
#define TR_TRANSLUCENT_IDX 2
#define TR_MAX_IDX 3

typedef struct
{
    DDPIXELFORMAT pddpf;
    jint  pfType;
} D3DTextureTableCell;

// texture table:
// [transparency={OPAQUE,BITMASK,TRANCLUCENT},depth={16,24,32}]
typedef D3DTextureTableCell D3DTextureTable[TR_MAX_IDX][DEPTH_MAX_IDX];

// - D3DContext class  -----------------------------------------------

/**
 * This class provides the following functionality:
 *  - holds the state of D3DContext java class (current pixel color,
 *    alpha compositing mode, extra alpha)
 *  - provides access to IDirect3DDevice7 interface (creation,
 *    disposal, exclusive access)
 *  - handles state changes of the direct3d device (transform,
 *    compositing mode, current texture)
 *  - provides means of creating textures, plain surfaces
 *  - holds a glyph cache texture for the associated device
 *  - implements primitives batching mechanism
 */
class D3DContext {
public:
    /**
     * Creates and returns D3DContext instance. If created context was
     * unable to initialize d3d device or if the device tests failed,
     * returns NULL.
     */
    static D3DContext* CreateD3DContext(DDraw *ddObject, DXObject* dxObject);
    /**
     * Releases the old device (if there was one) and all associated
     * resources, re-creates, initializes and tests the new device.
     *
     * If the device doesn't pass the test, it's released.
     *
     * Used when the context is first created, and then after a
     * display change event.
     *
     * Note that this method also does the necessary registry checks,
     * and if the registry shows that we've crashed when attempting
     * to initialize and test the device last time, it doesn't attempt
     * to create/init/test the device.
     */
    void    CreateD3DDevice();
    void    Release3DDevice();
    virtual ~D3DContext();

    /**
     * Stores a weak reference of passed D3DContext object.
     * This method is called from _getNativeDeviceCaps method, and does the
     * association of the native D3DContext with the corresponding java object.
     * We need a reference to the java object so it can be notified when
     * the native context is released or recreated.
     *
     * See jobject jD3DContext field
     */
    void SetJavaContext(JNIEnv *env, jobject jd3dc);

    /**
     * Methods to get/release exclusive access to the direct3d device
     * interface. Note that some methods of this class assume that the
     * lock is already taken. They're marked with 'NOLOCK' comment.
     * Those methods not dealing with the d3d device interface are not
     * required to obtain the lock (and not marked with NOLOCK)
     */
    void GetExclusiveAccess() { CRITICAL_SECTION_ENTER(deviceLock);}
    void ReleaseExclusiveAccess() { CRITICAL_SECTION_LEAVE(deviceLock);}

    // methods replicating java-level D3DContext objext
    void SetColor(jint eargb, jint flags);
    void SetAlphaComposite(jint rule, jfloat extraAlpha, jint flags);
    void ResetComposite();

    // Glyph cache-related methods
    HRESULT /*NOLOCK*/ InitGlyphCache();
    HRESULT /*NOLOCK*/ GlyphCacheAdd(JNIEnv *env, GlyphInfo *glyph);
    HRESULT /*NOLOCK*/ UploadImageToTexture(DXSurface *texture, jubyte *pixels,
                                            jint dstx, jint dsty,
                                            jint srcx, jint srcy,
                                            jint srcWidth, jint srcHeight,
                                            jint srcStride);
    DXSurface /*NOLOCK*/ *GetGlyphCacheTexture() { return lpGlyphCacheTexture; }
    DXSurface /*NOLOCK*/ *GetMaskTexture();
    GlyphCacheInfo *GetGlyphCache() { return glyphCache; }

    HRESULT CreateSurface(JNIEnv *env,
                          jint width, jint height, jint depth,
                          jint transparency, jint d3dSurfaceType,
                          DXSurface** dxSurface, jint* pType);

    /**
     * Attaches a depth buffer to the specified dxSurface.
     * If depthBufferFormat is not initialized (depthBufferFormat.dwSize == 0),
     * it will be initialized at the time of the call.
     *
     * If the buffer for this surface already exists, a "lost" status of the
     * depth buffer is returned.
     */
    HRESULT AttachDepthBuffer(DXSurface *dxSurface);

    // methods for dealing with device capabilities as determined by
    // methods in D3DRuntimeTest
    int GetDeviceCaps() { return deviceCaps; }
    void SetDeviceCaps(int caps) { deviceCaps = caps; }

    // Returns the texture pixel format table
    D3DTextureTable &GetTextureTable() { return textureTable; }

    DDrawSurface *GetTargetSurface() { return ddTargetSurface; }
    IDirect3DDevice7 *Get3DDevice() { return d3dDevice; }

    // IDirect3DDevice7-delegation methods

    /**
     * This method only sets the texture if it's not already set.
     */
    HRESULT /*NOLOCK*/ SetTexture(DXSurface *dxSurface, DWORD dwStage = 0);
    HRESULT SetRenderTarget(DDrawSurface *lpSurface);
    HRESULT SetTransform(jobject xform,
                         jdouble m00, jdouble m10,
                         jdouble m01, jdouble m11,
                         jdouble m02, jdouble m12);
    HRESULT SetClip(JNIEnv *env, jobject clip,
                    jboolean isRect,
                    int x1, int y1, int x2, int y2);

    DWORD GetMinTextureWidth() { return d3dDevDesc.dwMinTextureWidth; }
    DWORD GetMinTextureHeight() { return d3dDevDesc.dwMinTextureHeight; }
    DWORD GetMaxTextureWidth() { return d3dDevDesc.dwMaxTextureWidth; }
    DWORD GetMaxTextureHeight() { return d3dDevDesc.dwMaxTextureHeight; }
    DWORD GetMaxTextureAspectRatio()
        { return d3dDevDesc.dwMaxTextureAspectRatio; };
    BOOL IsPow2TexturesOnly()
        { return d3dDevDesc.dpcTriCaps.dwTextureCaps & D3DPTEXTURECAPS_POW2; };
    BOOL IsSquareTexturesOnly()
        { return d3dDevDesc.dpcTriCaps.dwTextureCaps &
              D3DPTEXTURECAPS_SQUAREONLY; }

    /**
     * This method invalidates the java-level D3DContext object if
     * the passed DDrawSurface is the current render target.
     * The invalidation needs to be done so that the D3DContext object
     * resets itself in case the native d3d device has been recreated, or
     * the target surface has been lost (in which case this method is called
     * from D3DSD_RestoreSurface function, see D3DSD_RestoreSurface for
     * more info).
     */
    void InvalidateIfTarget(JNIEnv *env, DDrawSurface *lpSurface);

    // primitives batching-related methods
    /**
     * Calls devices's BeginScene if there weren't one already pending,
     * sets the pending flag.
     */
    HRESULT /*NOLOCK*/ BeginScene(jbyte newState);
    /**
     * Only calls device's EndScene if ddResult is an error.
     */
    HRESULT /*NOLOCK*/ EndScene(HRESULT ddResult);
    /**
     * forces the end of batching by calling EndScene if
     * there was BeginScene pending.
     */
    HRESULT /*NOLOCK*/ ForceEndScene();
    /**
     * flushes the queue if the argument is this device's render target
     */
    void    FlushD3DQueueForTarget(DDrawSurface *ddSurface);

    // fields replicating D3DContext class' fields
    jint       compState;
    jfloat     extraAlpha;
    jint       colorPixel;

    // pixel for vertices used in blits via texture mapping,
    // set in SetAlphaComposite()
    jint       blitPolygonPixel;

    /**
     * Current operation state.
     * See STATE_* macros above.
     */
    jbyte      opState;

private:
    D3DContext(DDraw *ddObject, DXObject* dxObject);
    HRESULT InitD3DDevice(IDirect3DDevice7 *d3dDevice);
    /**
     * This method releases an old device, creates a new one,
     * runs d3d caps tests on it and sets the device caps according
     * to the results.
     */
    HRESULT /*NOLOCK*/ CreateAndTestD3DDevice(DxCapabilities *dxCaps);
    HRESULT /*NOLOCK*/ InitMaskTileTexture();
    void    /*NOLOCK*/ UpdateState(jbyte newState);

    IDirect3DDevice7        *d3dDevice;
    IDirect3D7              *d3dObject;
    DDraw                   *ddObject;
    DXObject                *dxObject;
    const GUID              *pDeviceGUID;
    DDrawSurface            *ddTargetSurface;
    DXSurface               *lpMaskTexture;
    DXSurface               *lpGlyphCacheTexture;
    D3DTextureTable         textureTable;
    DDPIXELFORMAT           depthBufferFormat;
    DDPIXELFORMAT           maskTileTexFormat;
    GlyphCacheInfo          *glyphCache;
    BOOL                    glyphCacheAvailable;
    // array of the textures currently set to the device
    IDirectDrawSurface7     *lastTexture[MAX_USED_TEXTURE_STAGE+1];

    /**
     * A weak reference to the java-level D3DContext object.
     * Used to invalidate the java D3DContext object if the device has been
     * recreated.
     * See SetJavaContext() method.
     */
    jobject jD3DContext;

    D3DDEVICEDESC7 d3dDevDesc;
    int deviceCaps;
    BOOL bIsHWRasterizer;

    /**
     * Used to implement simple primitive batching.
     * See BeginScene/EndScene/ForceEndScene.
     */
    BOOL    bBeginScenePending;
#ifdef DEBUG
    int endSceneQueueDepth;
#endif /* DEBUG */

    CriticalSection deviceLock;
};

// - Various vertex formats -------------------------------------------

#define D3DFVF_J2DLVERTEX (D3DFVF_XYZ | D3DFVF_DIFFUSE | D3DFVF_TEX1)
typedef struct _J2DLVERTEX {
    float x, y, z;
    DWORD color;
    float tu, tv;
} J2DLVERTEX;

/**
 * We're still debating whether to use a single vertex format
 * for all primitives or specific per-primitive formats.
 * Using different vertex formats reduces the amount of
 * data being sent to the video board, and this shows
 * benetits when running Java2D benchmarks.
 *
 * However, in a typical Swing application the number
 * of primitives of the same type rendered in a row is
 * relatively small, which means that the driver has
 * to spend more time state switching to account for different
 * vertex formats (and according to MSDN, switching vertex format
 * is a very expensive operation). So for this kind of application
 * it's better to stick with a single vertex format.
 */
#define USE_SINGLE_VERTEX_FORMAT

#ifndef USE_SINGLE_VERTEX_FORMAT

#define D3DFVF_J2D_XY_C (D3DFVF_XYZ | D3DFVF_DIFFUSE)
#define D3DFVF_XY_VERTEX D3DFVF_XYZ

typedef struct _J2D_XY_C_VERTEX {
    float x, y, z;
    DWORD color;
} J2D_XY_C_VERTEX;
typedef struct _J2D_XY_VERTEX {
    float x, y, z;
} J2D_XY_VERTEX;

#else // USE_SINGLE_VERTEX_FORMAT

// When using a single vertex format, define
// every format as J2DLVERTEX

#define D3DFVF_J2D_XY_C D3DFVF_J2DLVERTEX
#define D3DFVF_XY_VERTEX D3DFVF_J2DLVERTEX
typedef J2DLVERTEX J2D_XY_C_VERTEX;
typedef J2DLVERTEX J2D_XY_VERTEX;

#endif // USE_SINGLE_VERTEX_FORMAT

typedef J2DLVERTEX      J2DLV_QUAD[4];
typedef J2DLVERTEX      J2DLV_HEXA[6];
typedef J2D_XY_C_VERTEX J2DXYC_HEXA[6];
typedef J2D_XY_VERTEX   J2DXY_HEXA[6];
#define MAX_CACHED_SPAN_VX_NUM 100

// - Helper Macros ---------------------------------------------------

#define D3D_DEPTH_IDX(DEPTH) \
  (((DEPTH) <= 16) ? DEPTH16_IDX : \
    (((DEPTH) <= 24) ? DEPTH24_IDX : DEPTH32_IDX))

#define D3D_TR_IDX(TRAN) ((TRAN) - 1)

#define D3DSD_MASK_TILE_SIZE 32
#define D3D_GCACHE_WIDTH 512
#define D3D_GCACHE_HEIGHT 512
#define D3D_GCACHE_CELL_WIDTH 16
#define D3D_GCACHE_CELL_HEIGHT 16

#define D3DC_NO_CONTEXT_FLAGS \
    sun_java2d_d3d_D3DContext_NO_CONTEXT_FLAGS
#define D3DC_SRC_IS_OPAQUE    \
    sun_java2d_d3d_D3DContext_SRC_IS_OPAQUE

#define J2D_D3D_FAILURE \
    sun_java2d_d3d_D3DContext_J2D_D3D_FAILURE
#define J2D_D3D_PLAIN_SURFACE_OK \
    sun_java2d_d3d_D3DContext_J2D_D3D_PLAIN_SURFACE_OK
#define J2D_D3D_OP_TEXTURE_SURFACE_OK \
    sun_java2d_d3d_D3DContext_J2D_D3D_OP_TEXTURE_SURFACE_OK
#define J2D_D3D_BM_TEXTURE_SURFACE_OK \
    sun_java2d_d3d_D3DContext_J2D_D3D_BM_TEXTURE_SURFACE_OK
#define J2D_D3D_TR_TEXTURE_SURFACE_OK \
    sun_java2d_d3d_D3DContext_J2D_D3D_TR_TEXTURE_SURFACE_OK
#define J2D_D3D_DEPTH_SURFACE_OK \
    sun_java2d_d3d_D3DContext_J2D_D3D_DEPTH_SURFACE_OK
#define J2D_D3D_OP_RTT_SURFACE_OK \
    sun_java2d_d3d_D3DContext_J2D_D3D_OP_RTT_SURFACE_OK
#define J2D_D3D_LINES_OK \
    sun_java2d_d3d_D3DContext_J2D_D3D_LINES_OK
#define J2D_D3D_TEXTURE_BLIT_OK \
    sun_java2d_d3d_D3DContext_J2D_D3D_TEXTURE_BLIT_OK
#define J2D_D3D_TEXTURE_TRANSFORM_OK \
    sun_java2d_d3d_D3DContext_J2D_D3D_TEXTURE_TRANSFORM_OK
#define J2D_D3D_LINE_CLIPPING_OK \
    sun_java2d_d3d_D3DContext_J2D_D3D_LINE_CLIPPING_OK
#define J2D_D3D_DEVICE_OK \
    sun_java2d_d3d_D3DContext_J2D_D3D_DEVICE_OK
#define J2D_D3D_PIXEL_FORMATS_OK \
    sun_java2d_d3d_D3DContext_J2D_D3D_PIXEL_FORMATS_OK
#define J2D_D3D_SET_TRANSFORM_OK \
    sun_java2d_d3d_D3DContext_J2D_D3D_SET_TRANSFORM_OK
#define J2D_D3D_HW_OK \
    sun_java2d_d3d_D3DContext_J2D_D3D_HW_OK
#define J2D_D3D_ENABLED_OK \
    sun_java2d_d3d_D3DContext_J2D_D3D_ENABLED_OK

#endif D3DCONTEXT_H
