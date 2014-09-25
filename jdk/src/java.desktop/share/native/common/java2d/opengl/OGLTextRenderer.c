/*
 * Copyright (c) 2003, 2013, Oracle and/or its affiliates. All rights reserved.
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

#ifndef HEADLESS

#include <stdlib.h>
#include <math.h>
#include <jlong.h>

#include "sun_java2d_opengl_OGLTextRenderer.h"

#include "SurfaceData.h"
#include "OGLContext.h"
#include "OGLSurfaceData.h"
#include "OGLRenderQueue.h"
#include "OGLTextRenderer.h"
#include "OGLVertexCache.h"
#include "AccelGlyphCache.h"
#include "fontscalerdefs.h"

/**
 * The following constants define the inner and outer bounds of the
 * accelerated glyph cache.
 */
#define OGLTR_CACHE_WIDTH       512
#define OGLTR_CACHE_HEIGHT      512
#define OGLTR_CACHE_CELL_WIDTH  16
#define OGLTR_CACHE_CELL_HEIGHT 16

/**
 * The current "glyph mode" state.  This variable is used to track the
 * codepath used to render a particular glyph.  This variable is reset to
 * MODE_NOT_INITED at the beginning of every call to OGLTR_DrawGlyphList().
 * As each glyph is rendered, the glyphMode variable is updated to reflect
 * the current mode, so if the current mode is the same as the mode used
 * to render the previous glyph, we can avoid doing costly setup operations
 * each time.
 */
typedef enum {
    MODE_NOT_INITED,
    MODE_USE_CACHE_GRAY,
    MODE_USE_CACHE_LCD,
    MODE_NO_CACHE_GRAY,
    MODE_NO_CACHE_LCD
} GlyphMode;
static GlyphMode glyphMode = MODE_NOT_INITED;

/**
 * This enum indicates the current state of the hardware glyph cache.
 * Initially the CacheStatus is set to CACHE_NOT_INITED, and then it is
 * set to either GRAY or LCD when the glyph cache is initialized.
 */
typedef enum {
    CACHE_NOT_INITED,
    CACHE_GRAY,
    CACHE_LCD
} CacheStatus;
static CacheStatus cacheStatus = CACHE_NOT_INITED;

/**
 * This is the one glyph cache.  Once it is initialized as either GRAY or
 * LCD, it stays in that mode for the duration of the application.  It should
 * be safe to use this one glyph cache for all screens in a multimon
 * environment, since the glyph cache texture is shared between all contexts,
 * and (in theory) OpenGL drivers should be smart enough to manage that
 * texture across all screens.
 */
static GlyphCacheInfo *glyphCache = NULL;

/**
 * The handle to the LCD text fragment program object.
 */
static GLhandleARB lcdTextProgram = 0;

/**
 * The size of one of the gamma LUT textures in any one dimension along
 * the edge, in texels.
 */
#define LUT_EDGE 16

/**
 * These are the texture object handles for the gamma and inverse gamma
 * lookup tables.
 */
static GLuint gammaLutTextureID = 0;
static GLuint invGammaLutTextureID = 0;

/**
 * This value tracks the previous LCD contrast setting, so if the contrast
 * value hasn't changed since the last time the lookup tables were
 * generated (not very common), then we can skip updating the tables.
 */
static jint lastLCDContrast = -1;

/**
 * This value tracks the previous LCD rgbOrder setting, so if the rgbOrder
 * value has changed since the last time, it indicates that we need to
 * invalidate the cache, which may already store glyph images in the reverse
 * order.  Note that in most real world applications this value will not
 * change over the course of the application, but tests like Font2DTest
 * allow for changing the ordering at runtime, so we need to handle that case.
 */
static jboolean lastRGBOrder = JNI_TRUE;

/**
 * This constant defines the size of the tile to use in the
 * OGLTR_DrawLCDGlyphNoCache() method.  See below for more on why we
 * restrict this value to a particular size.
 */
#define OGLTR_NOCACHE_TILE_SIZE 32

/**
 * These constants define the size of the "cached destination" texture.
 * This texture is only used when rendering LCD-optimized text, as that
 * codepath needs direct access to the destination.  There is no way to
 * access the framebuffer directly from an OpenGL shader, so we need to first
 * copy the destination region corresponding to a particular glyph into
 * this cached texture, and then that texture will be accessed inside the
 * shader.  Copying the destination into this cached texture can be a very
 * expensive operation (accounting for about half the rendering time for
 * LCD text), so to mitigate this cost we try to bulk read a horizontal
 * region of the destination at a time.  (These values are empirically
 * derived for the common case where text runs horizontally.)
 *
 * Note: It is assumed in various calculations below that:
 *     (OGLTR_CACHED_DEST_WIDTH  >= OGLTR_CACHE_CELL_WIDTH)  &&
 *     (OGLTR_CACHED_DEST_WIDTH  >= OGLTR_NOCACHE_TILE_SIZE) &&
 *     (OGLTR_CACHED_DEST_HEIGHT >= OGLTR_CACHE_CELL_HEIGHT) &&
 *     (OGLTR_CACHED_DEST_HEIGHT >= OGLTR_NOCACHE_TILE_SIZE)
 */
#define OGLTR_CACHED_DEST_WIDTH  512
#define OGLTR_CACHED_DEST_HEIGHT 32

/**
 * The handle to the "cached destination" texture object.
 */
static GLuint cachedDestTextureID = 0;

/**
 * The current bounds of the "cached destination" texture, in destination
 * coordinate space.  The width/height of these bounds will not exceed the
 * OGLTR_CACHED_DEST_WIDTH/HEIGHT values defined above.  These bounds are
 * only considered valid when the isCachedDestValid flag is JNI_TRUE.
 */
static SurfaceDataBounds cachedDestBounds;

/**
 * This flag indicates whether the "cached destination" texture contains
 * valid data.  This flag is reset to JNI_FALSE at the beginning of every
 * call to OGLTR_DrawGlyphList().  Once we copy valid destination data
 * into the cached texture, this flag is set to JNI_TRUE.  This way, we can
 * limit the number of times we need to copy destination data, which is a
 * very costly operation.
 */
static jboolean isCachedDestValid = JNI_FALSE;

/**
 * The bounds of the previously rendered LCD glyph, in destination
 * coordinate space.  We use these bounds to determine whether the glyph
 * currently being rendered overlaps the previously rendered glyph (i.e.
 * its bounding box intersects that of the previously rendered glyph).  If
 * so, we need to re-read the destination area associated with that previous
 * glyph so that we can correctly blend with the actual destination data.
 */
static SurfaceDataBounds previousGlyphBounds;

/**
 * Initializes the one glyph cache (texture and data structure).
 * If lcdCache is JNI_TRUE, the texture will contain RGB data,
 * otherwise we will simply store the grayscale/monochrome glyph images
 * as intensity values (which work well with the GL_MODULATE function).
 */
static jboolean
OGLTR_InitGlyphCache(jboolean lcdCache)
{
    GlyphCacheInfo *gcinfo;
    GLclampf priority = 1.0f;
    GLenum internalFormat = lcdCache ? GL_RGB8 : GL_INTENSITY8;
    GLenum pixelFormat = lcdCache ? GL_RGB : GL_LUMINANCE;

    J2dTraceLn(J2D_TRACE_INFO, "OGLTR_InitGlyphCache");

    // init glyph cache data structure
    gcinfo = AccelGlyphCache_Init(OGLTR_CACHE_WIDTH,
                                  OGLTR_CACHE_HEIGHT,
                                  OGLTR_CACHE_CELL_WIDTH,
                                  OGLTR_CACHE_CELL_HEIGHT,
                                  OGLVertexCache_FlushVertexCache);
    if (gcinfo == NULL) {
        J2dRlsTraceLn(J2D_TRACE_ERROR,
                      "OGLTR_InitGlyphCache: could not init OGL glyph cache");
        return JNI_FALSE;
    }

    // init cache texture object
    j2d_glGenTextures(1, &gcinfo->cacheID);
    j2d_glBindTexture(GL_TEXTURE_2D, gcinfo->cacheID);
    j2d_glPrioritizeTextures(1, &gcinfo->cacheID, &priority);
    j2d_glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
    j2d_glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);

    j2d_glTexImage2D(GL_TEXTURE_2D, 0, internalFormat,
                     OGLTR_CACHE_WIDTH, OGLTR_CACHE_HEIGHT, 0,
                     pixelFormat, GL_UNSIGNED_BYTE, NULL);

    cacheStatus = (lcdCache ? CACHE_LCD : CACHE_GRAY);
    glyphCache = gcinfo;

    return JNI_TRUE;
}

/**
 * Adds the given glyph to the glyph cache (texture and data structure)
 * associated with the given OGLContext.
 */
static void
OGLTR_AddToGlyphCache(GlyphInfo *glyph, jboolean rgbOrder)
{
    GLenum pixelFormat;
    CacheCellInfo *ccinfo;

    J2dTraceLn(J2D_TRACE_INFO, "OGLTR_AddToGlyphCache");

    if ((glyphCache == NULL) || (glyph->image == NULL)) {
        return;
    }

    if (cacheStatus == CACHE_LCD) {
        pixelFormat = rgbOrder ? GL_RGB : GL_BGR;
    } else {
        pixelFormat = GL_LUMINANCE;
    }

    AccelGlyphCache_AddGlyph(glyphCache, glyph);
    ccinfo = (CacheCellInfo *) glyph->cellInfo;

    if (ccinfo != NULL) {
        // store glyph image in texture cell
        j2d_glTexSubImage2D(GL_TEXTURE_2D, 0,
                            ccinfo->x, ccinfo->y,
                            glyph->width, glyph->height,
                            pixelFormat, GL_UNSIGNED_BYTE, glyph->image);
    }
}

/**
 * This is the GLSL fragment shader source code for rendering LCD-optimized
 * text.  Do not be frightened; it is much easier to understand than the
 * equivalent ASM-like fragment program!
 *
 * The "uniform" variables at the top are initialized once the program is
 * linked, and are updated at runtime as needed (e.g. when the source color
 * changes, we will modify the "src_adj" value in OGLTR_UpdateLCDTextColor()).
 *
 * The "main" function is executed for each "fragment" (or pixel) in the
 * glyph image.  We have determined that the pow() function can be quite
 * slow and it only operates on scalar values, not vectors as we require.
 * So instead we build two 3D textures containing gamma (and inverse gamma)
 * lookup tables that allow us to approximate a component-wise pow() function
 * with a single 3D texture lookup.  This approach is at least 2x faster
 * than the equivalent pow() calls.
 *
 * The variables involved in the equation can be expressed as follows:
 *
 *   Cs = Color component of the source (foreground color) [0.0, 1.0]
 *   Cd = Color component of the destination (background color) [0.0, 1.0]
 *   Cr = Color component to be written to the destination [0.0, 1.0]
 *   Ag = Glyph alpha (aka intensity or coverage) [0.0, 1.0]
 *   Ga = Gamma adjustment in the range [1.0, 2.5]
 *   (^ means raised to the power)
 *
 * And here is the theoretical equation approximated by this shader:
 *
 *            Cr = (Ag*(Cs^Ga) + (1-Ag)*(Cd^Ga)) ^ (1/Ga)
 */
static const char *lcdTextShaderSource =
    "uniform vec3 src_adj;"
    "uniform sampler2D glyph_tex;"
    "uniform sampler2D dst_tex;"
    "uniform sampler3D invgamma_tex;"
    "uniform sampler3D gamma_tex;"
    ""
    "void main(void)"
    "{"
         // load the RGB value from the glyph image at the current texcoord
    "    vec3 glyph_clr = vec3(texture2D(glyph_tex, gl_TexCoord[0].st));"
    "    if (glyph_clr == vec3(0.0)) {"
             // zero coverage, so skip this fragment
    "        discard;"
    "    }"
         // load the RGB value from the corresponding destination pixel
    "    vec3 dst_clr = vec3(texture2D(dst_tex, gl_TexCoord[1].st));"
         // gamma adjust the dest color using the invgamma LUT
    "    vec3 dst_adj = vec3(texture3D(invgamma_tex, dst_clr.stp));"
         // linearly interpolate the three color values
    "    vec3 result = mix(dst_adj, src_adj, glyph_clr);"
         // gamma re-adjust the resulting color (alpha is always set to 1.0)
    "    gl_FragColor = vec4(vec3(texture3D(gamma_tex, result.stp)), 1.0);"
    "}";

/**
 * Compiles and links the LCD text shader program.  If successful, this
 * function returns a handle to the newly created shader program; otherwise
 * returns 0.
 */
static GLhandleARB
OGLTR_CreateLCDTextProgram()
{
    GLhandleARB lcdTextProgram;
    GLint loc;

    J2dTraceLn(J2D_TRACE_INFO, "OGLTR_CreateLCDTextProgram");

    lcdTextProgram = OGLContext_CreateFragmentProgram(lcdTextShaderSource);
    if (lcdTextProgram == 0) {
        J2dRlsTraceLn(J2D_TRACE_ERROR,
                      "OGLTR_CreateLCDTextProgram: error creating program");
        return 0;
    }

    // "use" the program object temporarily so that we can set the uniforms
    j2d_glUseProgramObjectARB(lcdTextProgram);

    // set the "uniform" values
    loc = j2d_glGetUniformLocationARB(lcdTextProgram, "glyph_tex");
    j2d_glUniform1iARB(loc, 0); // texture unit 0
    loc = j2d_glGetUniformLocationARB(lcdTextProgram, "dst_tex");
    j2d_glUniform1iARB(loc, 1); // texture unit 1
    loc = j2d_glGetUniformLocationARB(lcdTextProgram, "invgamma_tex");
    j2d_glUniform1iARB(loc, 2); // texture unit 2
    loc = j2d_glGetUniformLocationARB(lcdTextProgram, "gamma_tex");
    j2d_glUniform1iARB(loc, 3); // texture unit 3

    // "unuse" the program object; it will be re-bound later as needed
    j2d_glUseProgramObjectARB(0);

    return lcdTextProgram;
}

/**
 * Initializes a 3D texture object for use as a three-dimensional gamma
 * lookup table.  Note that the wrap mode is initialized to GL_LINEAR so
 * that the table will interpolate adjacent values when the index falls
 * somewhere in between.
 */
static GLuint
OGLTR_InitGammaLutTexture()
{
    GLuint lutTextureID;

    j2d_glGenTextures(1, &lutTextureID);
    j2d_glBindTexture(GL_TEXTURE_3D, lutTextureID);
    j2d_glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    j2d_glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    j2d_glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    j2d_glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    j2d_glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_R, GL_CLAMP_TO_EDGE);

    return lutTextureID;
}

/**
 * Updates the lookup table in the given texture object with the float
 * values in the given system memory buffer.  Note that we could use
 * glTexSubImage3D() when updating the texture after its first
 * initialization, but since we're updating the entire table (with
 * power-of-two dimensions) and this is a relatively rare event, we'll
 * just stick with glTexImage3D().
 */
static void
OGLTR_UpdateGammaLutTexture(GLuint texID, GLfloat *lut, jint size)
{
    j2d_glBindTexture(GL_TEXTURE_3D, texID);
    j2d_glTexImage3D(GL_TEXTURE_3D, 0, GL_RGB8,
                     size, size, size, 0, GL_RGB, GL_FLOAT, lut);
}

/**
 * (Re)Initializes the gamma lookup table textures.
 *
 * The given contrast value is an int in the range [100, 250] which we will
 * then scale to fit in the range [1.0, 2.5].  We create two LUTs, one
 * that essentially calculates pow(x, gamma) and the other calculates
 * pow(x, 1/gamma).  These values are replicated in all three dimensions, so
 * given a single 3D texture coordinate (typically this will be a triplet
 * in the form (r,g,b)), the 3D texture lookup will return an RGB triplet:
 *
 *     (pow(r,g), pow(y,g), pow(z,g)
 *
 * where g is either gamma or 1/gamma, depending on the table.
 */
static jboolean
OGLTR_UpdateLCDTextContrast(jint contrast)
{
    double gamma = ((double)contrast) / 100.0;
    double ig = gamma;
    double g = 1.0 / ig;
    GLfloat lut[LUT_EDGE][LUT_EDGE][LUT_EDGE][3];
    GLfloat invlut[LUT_EDGE][LUT_EDGE][LUT_EDGE][3];
    int min = 0;
    int max = LUT_EDGE - 1;
    int x, y, z;

    J2dTraceLn1(J2D_TRACE_INFO,
                "OGLTR_UpdateLCDTextContrast: contrast=%d", contrast);

    for (z = min; z <= max; z++) {
        double zval = ((double)z) / max;
        GLfloat gz = (GLfloat)pow(zval, g);
        GLfloat igz = (GLfloat)pow(zval, ig);

        for (y = min; y <= max; y++) {
            double yval = ((double)y) / max;
            GLfloat gy = (GLfloat)pow(yval, g);
            GLfloat igy = (GLfloat)pow(yval, ig);

            for (x = min; x <= max; x++) {
                double xval = ((double)x) / max;
                GLfloat gx = (GLfloat)pow(xval, g);
                GLfloat igx = (GLfloat)pow(xval, ig);

                lut[z][y][x][0] = gx;
                lut[z][y][x][1] = gy;
                lut[z][y][x][2] = gz;

                invlut[z][y][x][0] = igx;
                invlut[z][y][x][1] = igy;
                invlut[z][y][x][2] = igz;
            }
        }
    }

    if (gammaLutTextureID == 0) {
        gammaLutTextureID = OGLTR_InitGammaLutTexture();
    }
    OGLTR_UpdateGammaLutTexture(gammaLutTextureID, (GLfloat *)lut, LUT_EDGE);

    if (invGammaLutTextureID == 0) {
        invGammaLutTextureID = OGLTR_InitGammaLutTexture();
    }
    OGLTR_UpdateGammaLutTexture(invGammaLutTextureID,
                                (GLfloat *)invlut, LUT_EDGE);

    return JNI_TRUE;
}

/**
 * Updates the current gamma-adjusted source color ("src_adj") of the LCD
 * text shader program.  Note that we could calculate this value in the
 * shader (e.g. just as we do for "dst_adj"), but would be unnecessary work
 * (and a measurable performance hit, maybe around 5%) since this value is
 * constant over the entire glyph list.  So instead we just calculate the
 * gamma-adjusted value once and update the uniform parameter of the LCD
 * shader as needed.
 */
static jboolean
OGLTR_UpdateLCDTextColor(jint contrast)
{
    double gamma = ((double)contrast) / 100.0;
    GLfloat radj, gadj, badj;
    GLfloat clr[4];
    GLint loc;

    J2dTraceLn1(J2D_TRACE_INFO,
                "OGLTR_UpdateLCDTextColor: contrast=%d", contrast);

    /*
     * Note: Ideally we would update the "src_adj" uniform parameter only
     * when there is a change in the source color.  Fortunately, the cost
     * of querying the current OpenGL color state and updating the uniform
     * value is quite small, and in the common case we only need to do this
     * once per GlyphList, so we gain little from trying to optimize too
     * eagerly here.
     */

    // get the current OpenGL primary color state
    j2d_glGetFloatv(GL_CURRENT_COLOR, clr);

    // gamma adjust the primary color
    radj = (GLfloat)pow(clr[0], gamma);
    gadj = (GLfloat)pow(clr[1], gamma);
    badj = (GLfloat)pow(clr[2], gamma);

    // update the "src_adj" parameter of the shader program with this value
    loc = j2d_glGetUniformLocationARB(lcdTextProgram, "src_adj");
    j2d_glUniform3fARB(loc, radj, gadj, badj);

    return JNI_TRUE;
}

/**
 * Enables the LCD text shader and updates any related state, such as the
 * gamma lookup table textures.
 */
static jboolean
OGLTR_EnableLCDGlyphModeState(GLuint glyphTextureID, jint contrast)
{
    // bind the texture containing glyph data to texture unit 0
    j2d_glActiveTextureARB(GL_TEXTURE0_ARB);
    j2d_glBindTexture(GL_TEXTURE_2D, glyphTextureID);

    // bind the texture tile containing destination data to texture unit 1
    j2d_glActiveTextureARB(GL_TEXTURE1_ARB);
    if (cachedDestTextureID == 0) {
        cachedDestTextureID =
            OGLContext_CreateBlitTexture(GL_RGB8, GL_RGB,
                                         OGLTR_CACHED_DEST_WIDTH,
                                         OGLTR_CACHED_DEST_HEIGHT);
        if (cachedDestTextureID == 0) {
            return JNI_FALSE;
        }
    }
    j2d_glBindTexture(GL_TEXTURE_2D, cachedDestTextureID);

    // note that GL_TEXTURE_2D was already enabled for texture unit 0,
    // but we need to explicitly enable it for texture unit 1
    j2d_glEnable(GL_TEXTURE_2D);

    // create the LCD text shader, if necessary
    if (lcdTextProgram == 0) {
        lcdTextProgram = OGLTR_CreateLCDTextProgram();
        if (lcdTextProgram == 0) {
            return JNI_FALSE;
        }
    }

    // enable the LCD text shader
    j2d_glUseProgramObjectARB(lcdTextProgram);

    // update the current contrast settings, if necessary
    if (lastLCDContrast != contrast) {
        if (!OGLTR_UpdateLCDTextContrast(contrast)) {
            return JNI_FALSE;
        }
        lastLCDContrast = contrast;
    }

    // update the current color settings
    if (!OGLTR_UpdateLCDTextColor(contrast)) {
        return JNI_FALSE;
    }

    // bind the gamma LUT textures
    j2d_glActiveTextureARB(GL_TEXTURE2_ARB);
    j2d_glBindTexture(GL_TEXTURE_3D, invGammaLutTextureID);
    j2d_glEnable(GL_TEXTURE_3D);
    j2d_glActiveTextureARB(GL_TEXTURE3_ARB);
    j2d_glBindTexture(GL_TEXTURE_3D, gammaLutTextureID);
    j2d_glEnable(GL_TEXTURE_3D);

    return JNI_TRUE;
}

void
OGLTR_EnableGlyphVertexCache(OGLContext *oglc)
{
    J2dTraceLn(J2D_TRACE_INFO, "OGLTR_EnableGlyphVertexCache");

    if (!OGLVertexCache_InitVertexCache(oglc)) {
        return;
    }

    if (glyphCache == NULL) {
        if (!OGLTR_InitGlyphCache(JNI_FALSE)) {
            return;
        }
    }

    j2d_glEnable(GL_TEXTURE_2D);
    j2d_glBindTexture(GL_TEXTURE_2D, glyphCache->cacheID);
    j2d_glPixelStorei(GL_UNPACK_ALIGNMENT, 1);

    // for grayscale/monochrome text, the current OpenGL source color
    // is modulated with the glyph image as part of the texture
    // application stage, so we use GL_MODULATE here
    OGLC_UPDATE_TEXTURE_FUNCTION(oglc, GL_MODULATE);
}

void
OGLTR_DisableGlyphVertexCache(OGLContext *oglc)
{
    J2dTraceLn(J2D_TRACE_INFO, "OGLTR_DisableGlyphVertexCache");

    OGLVertexCache_FlushVertexCache();
    OGLVertexCache_RestoreColorState(oglc);

    j2d_glDisable(GL_TEXTURE_2D);
    j2d_glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
    j2d_glPixelStorei(GL_UNPACK_SKIP_PIXELS, 0);
    j2d_glPixelStorei(GL_UNPACK_SKIP_ROWS, 0);
    j2d_glPixelStorei(GL_UNPACK_ROW_LENGTH, 0);
}

/**
 * Disables any pending state associated with the current "glyph mode".
 */
static void
OGLTR_DisableGlyphModeState()
{
    switch (glyphMode) {
    case MODE_NO_CACHE_LCD:
        j2d_glPixelStorei(GL_UNPACK_SKIP_PIXELS, 0);
        j2d_glPixelStorei(GL_UNPACK_SKIP_ROWS, 0);
        /* FALLTHROUGH */

    case MODE_USE_CACHE_LCD:
        j2d_glPixelStorei(GL_UNPACK_ROW_LENGTH, 0);
        j2d_glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
        j2d_glUseProgramObjectARB(0);
        j2d_glActiveTextureARB(GL_TEXTURE3_ARB);
        j2d_glDisable(GL_TEXTURE_3D);
        j2d_glActiveTextureARB(GL_TEXTURE2_ARB);
        j2d_glDisable(GL_TEXTURE_3D);
        j2d_glActiveTextureARB(GL_TEXTURE1_ARB);
        j2d_glDisable(GL_TEXTURE_2D);
        j2d_glActiveTextureARB(GL_TEXTURE0_ARB);
        break;

    case MODE_NO_CACHE_GRAY:
    case MODE_USE_CACHE_GRAY:
    case MODE_NOT_INITED:
    default:
        break;
    }
}

static jboolean
OGLTR_DrawGrayscaleGlyphViaCache(OGLContext *oglc,
                                 GlyphInfo *ginfo, jint x, jint y)
{
    CacheCellInfo *cell;
    jfloat x1, y1, x2, y2;

    if (glyphMode != MODE_USE_CACHE_GRAY) {
        OGLTR_DisableGlyphModeState();
        CHECK_PREVIOUS_OP(OGL_STATE_GLYPH_OP);
        glyphMode = MODE_USE_CACHE_GRAY;
    }

    if (ginfo->cellInfo == NULL) {
        // attempt to add glyph to accelerated glyph cache
        OGLTR_AddToGlyphCache(ginfo, JNI_FALSE);

        if (ginfo->cellInfo == NULL) {
            // we'll just no-op in the rare case that the cell is NULL
            return JNI_TRUE;
        }
    }

    cell = (CacheCellInfo *) (ginfo->cellInfo);
    cell->timesRendered++;

    x1 = (jfloat)x;
    y1 = (jfloat)y;
    x2 = x1 + ginfo->width;
    y2 = y1 + ginfo->height;

    OGLVertexCache_AddGlyphQuad(oglc,
                                cell->tx1, cell->ty1,
                                cell->tx2, cell->ty2,
                                x1, y1, x2, y2);

    return JNI_TRUE;
}

/**
 * Evaluates to true if the rectangle defined by gx1/gy1/gx2/gy2 is
 * inside outerBounds.
 */
#define INSIDE(gx1, gy1, gx2, gy2, outerBounds) \
    (((gx1) >= outerBounds.x1) && ((gy1) >= outerBounds.y1) && \
     ((gx2) <= outerBounds.x2) && ((gy2) <= outerBounds.y2))

/**
 * Evaluates to true if the rectangle defined by gx1/gy1/gx2/gy2 intersects
 * the rectangle defined by bounds.
 */
#define INTERSECTS(gx1, gy1, gx2, gy2, bounds) \
    ((bounds.x2 > (gx1)) && (bounds.y2 > (gy1)) && \
     (bounds.x1 < (gx2)) && (bounds.y1 < (gy2)))

/**
 * This method checks to see if the given LCD glyph bounds fall within the
 * cached destination texture bounds.  If so, this method can return
 * immediately.  If not, this method will copy a chunk of framebuffer data
 * into the cached destination texture and then update the current cached
 * destination bounds before returning.
 */
static void
OGLTR_UpdateCachedDestination(OGLSDOps *dstOps, GlyphInfo *ginfo,
                              jint gx1, jint gy1, jint gx2, jint gy2,
                              jint glyphIndex, jint totalGlyphs)
{
    jint dx1, dy1, dx2, dy2;
    jint dx1adj, dy1adj;

    if (isCachedDestValid && INSIDE(gx1, gy1, gx2, gy2, cachedDestBounds)) {
        // glyph is already within the cached destination bounds; no need
        // to read back the entire destination region again, but we do
        // need to see if the current glyph overlaps the previous glyph...

        if (INTERSECTS(gx1, gy1, gx2, gy2, previousGlyphBounds)) {
            // the current glyph overlaps the destination region touched
            // by the previous glyph, so now we need to read back the part
            // of the destination corresponding to the previous glyph
            dx1 = previousGlyphBounds.x1;
            dy1 = previousGlyphBounds.y1;
            dx2 = previousGlyphBounds.x2;
            dy2 = previousGlyphBounds.y2;

            // this accounts for lower-left origin of the destination region
            dx1adj = dstOps->xOffset + dx1;
            dy1adj = dstOps->yOffset + dstOps->height - dy2;

            // copy destination into subregion of cached texture tile:
            //   dx1-cachedDestBounds.x1 == +xoffset from left side of texture
            //   cachedDestBounds.y2-dy2 == +yoffset from bottom of texture
            j2d_glActiveTextureARB(GL_TEXTURE1_ARB);
            j2d_glCopyTexSubImage2D(GL_TEXTURE_2D, 0,
                                    dx1 - cachedDestBounds.x1,
                                    cachedDestBounds.y2 - dy2,
                                    dx1adj, dy1adj,
                                    dx2-dx1, dy2-dy1);
        }
    } else {
        jint remainingWidth;

        // destination region is not valid, so we need to read back a
        // chunk of the destination into our cached texture

        // position the upper-left corner of the destination region on the
        // "top" line of glyph list
        // REMIND: this isn't ideal; it would be better if we had some idea
        //         of the bounding box of the whole glyph list (this is
        //         do-able, but would require iterating through the whole
        //         list up front, which may present its own problems)
        dx1 = gx1;
        dy1 = gy1;

        if (ginfo->advanceX > 0) {
            // estimate the width based on our current position in the glyph
            // list and using the x advance of the current glyph (this is just
            // a quick and dirty heuristic; if this is a "thin" glyph image,
            // then we're likely to underestimate, and if it's "thick" then we
            // may end up reading back more than we need to)
            remainingWidth =
                (jint)(ginfo->advanceX * (totalGlyphs - glyphIndex));
            if (remainingWidth > OGLTR_CACHED_DEST_WIDTH) {
                remainingWidth = OGLTR_CACHED_DEST_WIDTH;
            } else if (remainingWidth < ginfo->width) {
                // in some cases, the x-advance may be slightly smaller
                // than the actual width of the glyph; if so, adjust our
                // estimate so that we can accommodate the entire glyph
                remainingWidth = ginfo->width;
            }
        } else {
            // a negative advance is possible when rendering rotated text,
            // in which case it is difficult to estimate an appropriate
            // region for readback, so we will pick a region that
            // encompasses just the current glyph
            remainingWidth = ginfo->width;
        }
        dx2 = dx1 + remainingWidth;

        // estimate the height (this is another sloppy heuristic; we'll
        // make the cached destination region tall enough to encompass most
        // glyphs that are small enough to fit in the glyph cache, and then
        // we add a little something extra to account for descenders
        dy2 = dy1 + OGLTR_CACHE_CELL_HEIGHT + 2;

        // this accounts for lower-left origin of the destination region
        dx1adj = dstOps->xOffset + dx1;
        dy1adj = dstOps->yOffset + dstOps->height - dy2;

        // copy destination into cached texture tile (the lower-left corner
        // of the destination region will be positioned at the lower-left
        // corner (0,0) of the texture)
        j2d_glActiveTextureARB(GL_TEXTURE1_ARB);
        j2d_glCopyTexSubImage2D(GL_TEXTURE_2D, 0,
                                0, 0, dx1adj, dy1adj,
                                dx2-dx1, dy2-dy1);

        // update the cached bounds and mark it valid
        cachedDestBounds.x1 = dx1;
        cachedDestBounds.y1 = dy1;
        cachedDestBounds.x2 = dx2;
        cachedDestBounds.y2 = dy2;
        isCachedDestValid = JNI_TRUE;
    }

    // always update the previous glyph bounds
    previousGlyphBounds.x1 = gx1;
    previousGlyphBounds.y1 = gy1;
    previousGlyphBounds.x2 = gx2;
    previousGlyphBounds.y2 = gy2;
}

static jboolean
OGLTR_DrawLCDGlyphViaCache(OGLContext *oglc, OGLSDOps *dstOps,
                           GlyphInfo *ginfo, jint x, jint y,
                           jint glyphIndex, jint totalGlyphs,
                           jboolean rgbOrder, jint contrast)
{
    CacheCellInfo *cell;
    jint dx1, dy1, dx2, dy2;
    jfloat dtx1, dty1, dtx2, dty2;

    if (glyphMode != MODE_USE_CACHE_LCD) {
        OGLTR_DisableGlyphModeState();
        CHECK_PREVIOUS_OP(GL_TEXTURE_2D);
        j2d_glPixelStorei(GL_UNPACK_ALIGNMENT, 1);

        if (glyphCache == NULL) {
            if (!OGLTR_InitGlyphCache(JNI_TRUE)) {
                return JNI_FALSE;
            }
        }

        if (rgbOrder != lastRGBOrder) {
            // need to invalidate the cache in this case; see comments
            // for lastRGBOrder above
            AccelGlyphCache_Invalidate(glyphCache);
            lastRGBOrder = rgbOrder;
        }

        if (!OGLTR_EnableLCDGlyphModeState(glyphCache->cacheID, contrast)) {
            return JNI_FALSE;
        }

        // when a fragment shader is enabled, the texture function state is
        // ignored, so the following line is not needed...
        // OGLC_UPDATE_TEXTURE_FUNCTION(oglc, GL_MODULATE);

        glyphMode = MODE_USE_CACHE_LCD;
    }

    if (ginfo->cellInfo == NULL) {
        // rowBytes will always be a multiple of 3, so the following is safe
        j2d_glPixelStorei(GL_UNPACK_ROW_LENGTH, ginfo->rowBytes / 3);

        // make sure the glyph cache texture is bound to texture unit 0
        j2d_glActiveTextureARB(GL_TEXTURE0_ARB);

        // attempt to add glyph to accelerated glyph cache
        OGLTR_AddToGlyphCache(ginfo, rgbOrder);

        if (ginfo->cellInfo == NULL) {
            // we'll just no-op in the rare case that the cell is NULL
            return JNI_TRUE;
        }
    }

    cell = (CacheCellInfo *) (ginfo->cellInfo);
    cell->timesRendered++;

    // location of the glyph in the destination's coordinate space
    dx1 = x;
    dy1 = y;
    dx2 = dx1 + ginfo->width;
    dy2 = dy1 + ginfo->height;

    // copy destination into second cached texture, if necessary
    OGLTR_UpdateCachedDestination(dstOps, ginfo,
                                  dx1, dy1, dx2, dy2,
                                  glyphIndex, totalGlyphs);

    // texture coordinates of the destination tile
    dtx1 = ((jfloat)(dx1 - cachedDestBounds.x1)) / OGLTR_CACHED_DEST_WIDTH;
    dty1 = ((jfloat)(cachedDestBounds.y2 - dy1)) / OGLTR_CACHED_DEST_HEIGHT;
    dtx2 = ((jfloat)(dx2 - cachedDestBounds.x1)) / OGLTR_CACHED_DEST_WIDTH;
    dty2 = ((jfloat)(cachedDestBounds.y2 - dy2)) / OGLTR_CACHED_DEST_HEIGHT;

    // render composed texture to the destination surface
    j2d_glBegin(GL_QUADS);
    j2d_glMultiTexCoord2fARB(GL_TEXTURE0_ARB, cell->tx1, cell->ty1);
    j2d_glMultiTexCoord2fARB(GL_TEXTURE1_ARB, dtx1, dty1);
    j2d_glVertex2i(dx1, dy1);
    j2d_glMultiTexCoord2fARB(GL_TEXTURE0_ARB, cell->tx2, cell->ty1);
    j2d_glMultiTexCoord2fARB(GL_TEXTURE1_ARB, dtx2, dty1);
    j2d_glVertex2i(dx2, dy1);
    j2d_glMultiTexCoord2fARB(GL_TEXTURE0_ARB, cell->tx2, cell->ty2);
    j2d_glMultiTexCoord2fARB(GL_TEXTURE1_ARB, dtx2, dty2);
    j2d_glVertex2i(dx2, dy2);
    j2d_glMultiTexCoord2fARB(GL_TEXTURE0_ARB, cell->tx1, cell->ty2);
    j2d_glMultiTexCoord2fARB(GL_TEXTURE1_ARB, dtx1, dty2);
    j2d_glVertex2i(dx1, dy2);
    j2d_glEnd();

    return JNI_TRUE;
}

static jboolean
OGLTR_DrawGrayscaleGlyphNoCache(OGLContext *oglc,
                                GlyphInfo *ginfo, jint x, jint y)
{
    jint tw, th;
    jint sx, sy, sw, sh;
    jint x0;
    jint w = ginfo->width;
    jint h = ginfo->height;

    if (glyphMode != MODE_NO_CACHE_GRAY) {
        OGLTR_DisableGlyphModeState();
        CHECK_PREVIOUS_OP(OGL_STATE_MASK_OP);
        glyphMode = MODE_NO_CACHE_GRAY;
    }

    x0 = x;
    tw = OGLVC_MASK_CACHE_TILE_WIDTH;
    th = OGLVC_MASK_CACHE_TILE_HEIGHT;

    for (sy = 0; sy < h; sy += th, y += th) {
        x = x0;
        sh = ((sy + th) > h) ? (h - sy) : th;

        for (sx = 0; sx < w; sx += tw, x += tw) {
            sw = ((sx + tw) > w) ? (w - sx) : tw;

            OGLVertexCache_AddMaskQuad(oglc,
                                       sx, sy, x, y, sw, sh,
                                       w, ginfo->image);
        }
    }

    return JNI_TRUE;
}

static jboolean
OGLTR_DrawLCDGlyphNoCache(OGLContext *oglc, OGLSDOps *dstOps,
                          GlyphInfo *ginfo, jint x, jint y,
                          jint rowBytesOffset,
                          jboolean rgbOrder, jint contrast)
{
    GLfloat tx1, ty1, tx2, ty2;
    GLfloat dtx1, dty1, dtx2, dty2;
    jint tw, th;
    jint sx, sy, sw, sh, dxadj, dyadj;
    jint x0;
    jint w = ginfo->width;
    jint h = ginfo->height;
    GLenum pixelFormat = rgbOrder ? GL_RGB : GL_BGR;

    if (glyphMode != MODE_NO_CACHE_LCD) {
        OGLTR_DisableGlyphModeState();
        CHECK_PREVIOUS_OP(GL_TEXTURE_2D);
        j2d_glPixelStorei(GL_UNPACK_ALIGNMENT, 1);

        if (oglc->blitTextureID == 0) {
            if (!OGLContext_InitBlitTileTexture(oglc)) {
                return JNI_FALSE;
            }
        }

        if (!OGLTR_EnableLCDGlyphModeState(oglc->blitTextureID, contrast)) {
            return JNI_FALSE;
        }

        // when a fragment shader is enabled, the texture function state is
        // ignored, so the following line is not needed...
        // OGLC_UPDATE_TEXTURE_FUNCTION(oglc, GL_MODULATE);

        glyphMode = MODE_NO_CACHE_LCD;
    }

    // rowBytes will always be a multiple of 3, so the following is safe
    j2d_glPixelStorei(GL_UNPACK_ROW_LENGTH, ginfo->rowBytes / 3);

    x0 = x;
    tx1 = 0.0f;
    ty1 = 0.0f;
    dtx1 = 0.0f;
    dty2 = 0.0f;
    tw = OGLTR_NOCACHE_TILE_SIZE;
    th = OGLTR_NOCACHE_TILE_SIZE;

    for (sy = 0; sy < h; sy += th, y += th) {
        x = x0;
        sh = ((sy + th) > h) ? (h - sy) : th;

        for (sx = 0; sx < w; sx += tw, x += tw) {
            sw = ((sx + tw) > w) ? (w - sx) : tw;

            // update the source pointer offsets
            j2d_glPixelStorei(GL_UNPACK_SKIP_PIXELS, sx);
            j2d_glPixelStorei(GL_UNPACK_SKIP_ROWS, sy);

            // copy LCD mask into glyph texture tile
            j2d_glActiveTextureARB(GL_TEXTURE0_ARB);
            j2d_glTexSubImage2D(GL_TEXTURE_2D, 0,
                                0, 0, sw, sh,
                                pixelFormat, GL_UNSIGNED_BYTE,
                                ginfo->image + rowBytesOffset);

            // update the lower-right glyph texture coordinates
            tx2 = ((GLfloat)sw) / OGLC_BLIT_TILE_SIZE;
            ty2 = ((GLfloat)sh) / OGLC_BLIT_TILE_SIZE;

            // this accounts for lower-left origin of the destination region
            dxadj = dstOps->xOffset + x;
            dyadj = dstOps->yOffset + dstOps->height - (y + sh);

            // copy destination into cached texture tile (the lower-left
            // corner of the destination region will be positioned at the
            // lower-left corner (0,0) of the texture)
            j2d_glActiveTextureARB(GL_TEXTURE1_ARB);
            j2d_glCopyTexSubImage2D(GL_TEXTURE_2D, 0,
                                    0, 0,
                                    dxadj, dyadj,
                                    sw, sh);

            // update the remaining destination texture coordinates
            dtx2 = ((GLfloat)sw) / OGLTR_CACHED_DEST_WIDTH;
            dty1 = ((GLfloat)sh) / OGLTR_CACHED_DEST_HEIGHT;

            // render composed texture to the destination surface
            j2d_glBegin(GL_QUADS);
            j2d_glMultiTexCoord2fARB(GL_TEXTURE0_ARB, tx1, ty1);
            j2d_glMultiTexCoord2fARB(GL_TEXTURE1_ARB, dtx1, dty1);
            j2d_glVertex2i(x, y);
            j2d_glMultiTexCoord2fARB(GL_TEXTURE0_ARB, tx2, ty1);
            j2d_glMultiTexCoord2fARB(GL_TEXTURE1_ARB, dtx2, dty1);
            j2d_glVertex2i(x + sw, y);
            j2d_glMultiTexCoord2fARB(GL_TEXTURE0_ARB, tx2, ty2);
            j2d_glMultiTexCoord2fARB(GL_TEXTURE1_ARB, dtx2, dty2);
            j2d_glVertex2i(x + sw, y + sh);
            j2d_glMultiTexCoord2fARB(GL_TEXTURE0_ARB, tx1, ty2);
            j2d_glMultiTexCoord2fARB(GL_TEXTURE1_ARB, dtx1, dty2);
            j2d_glVertex2i(x, y + sh);
            j2d_glEnd();
        }
    }

    return JNI_TRUE;
}

// see DrawGlyphList.c for more on this macro...
#define FLOOR_ASSIGN(l, r) \
    if ((r)<0) (l) = ((int)floor(r)); else (l) = ((int)(r))

void
OGLTR_DrawGlyphList(JNIEnv *env, OGLContext *oglc, OGLSDOps *dstOps,
                    jint totalGlyphs, jboolean usePositions,
                    jboolean subPixPos, jboolean rgbOrder, jint lcdContrast,
                    jfloat glyphListOrigX, jfloat glyphListOrigY,
                    unsigned char *images, unsigned char *positions)
{
    int glyphCounter;

    J2dTraceLn(J2D_TRACE_INFO, "OGLTR_DrawGlyphList");

    RETURN_IF_NULL(oglc);
    RETURN_IF_NULL(dstOps);
    RETURN_IF_NULL(images);
    if (usePositions) {
        RETURN_IF_NULL(positions);
    }

    glyphMode = MODE_NOT_INITED;
    isCachedDestValid = JNI_FALSE;

    for (glyphCounter = 0; glyphCounter < totalGlyphs; glyphCounter++) {
        jint x, y;
        jfloat glyphx, glyphy;
        jboolean grayscale, ok;
        GlyphInfo *ginfo = (GlyphInfo *)jlong_to_ptr(NEXT_LONG(images));

        if (ginfo == NULL) {
            // this shouldn't happen, but if it does we'll just break out...
            J2dRlsTraceLn(J2D_TRACE_ERROR,
                          "OGLTR_DrawGlyphList: glyph info is null");
            break;
        }

        grayscale = (ginfo->rowBytes == ginfo->width);

        if (usePositions) {
            jfloat posx = NEXT_FLOAT(positions);
            jfloat posy = NEXT_FLOAT(positions);
            glyphx = glyphListOrigX + posx + ginfo->topLeftX;
            glyphy = glyphListOrigY + posy + ginfo->topLeftY;
            FLOOR_ASSIGN(x, glyphx);
            FLOOR_ASSIGN(y, glyphy);
        } else {
            glyphx = glyphListOrigX + ginfo->topLeftX;
            glyphy = glyphListOrigY + ginfo->topLeftY;
            FLOOR_ASSIGN(x, glyphx);
            FLOOR_ASSIGN(y, glyphy);
            glyphListOrigX += ginfo->advanceX;
            glyphListOrigY += ginfo->advanceY;
        }

        if (ginfo->image == NULL) {
            continue;
        }

        if (grayscale) {
            // grayscale or monochrome glyph data
            if (cacheStatus != CACHE_LCD &&
                ginfo->width <= OGLTR_CACHE_CELL_WIDTH &&
                ginfo->height <= OGLTR_CACHE_CELL_HEIGHT)
            {
                ok = OGLTR_DrawGrayscaleGlyphViaCache(oglc, ginfo, x, y);
            } else {
                ok = OGLTR_DrawGrayscaleGlyphNoCache(oglc, ginfo, x, y);
            }
        } else {
            // LCD-optimized glyph data
            jint rowBytesOffset = 0;

            if (subPixPos) {
                jint frac = (jint)((glyphx - x) * 3);
                if (frac != 0) {
                    rowBytesOffset = 3 - frac;
                    x += 1;
                }
            }

            if (rowBytesOffset == 0 &&
                cacheStatus != CACHE_GRAY &&
                ginfo->width <= OGLTR_CACHE_CELL_WIDTH &&
                ginfo->height <= OGLTR_CACHE_CELL_HEIGHT)
            {
                ok = OGLTR_DrawLCDGlyphViaCache(oglc, dstOps,
                                                ginfo, x, y,
                                                glyphCounter, totalGlyphs,
                                                rgbOrder, lcdContrast);
            } else {
                ok = OGLTR_DrawLCDGlyphNoCache(oglc, dstOps,
                                               ginfo, x, y,
                                               rowBytesOffset,
                                               rgbOrder, lcdContrast);
            }
        }

        if (!ok) {
            break;
        }
    }

    OGLTR_DisableGlyphModeState();
}

JNIEXPORT void JNICALL
Java_sun_java2d_opengl_OGLTextRenderer_drawGlyphList
    (JNIEnv *env, jobject self,
     jint numGlyphs, jboolean usePositions,
     jboolean subPixPos, jboolean rgbOrder, jint lcdContrast,
     jfloat glyphListOrigX, jfloat glyphListOrigY,
     jlongArray imgArray, jfloatArray posArray)
{
    unsigned char *images;

    J2dTraceLn(J2D_TRACE_INFO, "OGLTextRenderer_drawGlyphList");

    images = (unsigned char *)
        (*env)->GetPrimitiveArrayCritical(env, imgArray, NULL);
    if (images != NULL) {
        OGLContext *oglc = OGLRenderQueue_GetCurrentContext();
        OGLSDOps *dstOps = OGLRenderQueue_GetCurrentDestination();

        if (usePositions) {
            unsigned char *positions = (unsigned char *)
                (*env)->GetPrimitiveArrayCritical(env, posArray, NULL);
            if (positions != NULL) {
                OGLTR_DrawGlyphList(env, oglc, dstOps,
                                    numGlyphs, usePositions,
                                    subPixPos, rgbOrder, lcdContrast,
                                    glyphListOrigX, glyphListOrigY,
                                    images, positions);
                (*env)->ReleasePrimitiveArrayCritical(env, posArray,
                                                      positions, JNI_ABORT);
            }
        } else {
            OGLTR_DrawGlyphList(env, oglc, dstOps,
                                numGlyphs, usePositions,
                                subPixPos, rgbOrder, lcdContrast,
                                glyphListOrigX, glyphListOrigY,
                                images, NULL);
        }

        // 6358147: reset current state, and ensure rendering is
        // flushed to dest
        if (oglc != NULL) {
            RESET_PREVIOUS_OP();
            j2d_glFlush();
        }

        (*env)->ReleasePrimitiveArrayCritical(env, imgArray,
                                              images, JNI_ABORT);
    }
}

#endif /* !HEADLESS */
