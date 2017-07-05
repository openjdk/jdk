/*
 * Copyright (c) 2011, 2013, Oracle and/or its affiliates. All rights reserved.
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

#import <Accelerate/Accelerate.h> // for vImage_Buffer
#import <JavaNativeFoundation/JavaNativeFoundation.h>

#import "CGGlyphImages.h"
#import "CoreTextSupport.h"
#import "fontscalerdefs.h" // contains the definition of GlyphInfo struct

#import "sun_awt_SunHints.h"

//#define USE_IMAGE_ALIGNED_MEMORY 1
//#define CGGI_DEBUG 1
//#define CGGI_DEBUG_DUMP 1
//#define CGGI_DEBUG_HIT_COUNT 1

#define PRINT_TX(x) \
    NSLog(@"(%f, %f, %f, %f, %f, %f)", x.a, x.b, x.c, x.d, x.tx, x.ty);

/*
 * The GlyphCanvas is a global shared CGContext that characters are struck into.
 * For each character, the glyph is struck, copied into a GlyphInfo struct, and
 * the canvas is cleared for the next glyph.
 *
 * If the necessary canvas is too large, the shared one will not be used and a
 * temporary one will be provided.
 */
@interface CGGI_GlyphCanvas : NSObject {
@public
    CGContextRef context;
    vImage_Buffer *image;
}
@end;

@implementation CGGI_GlyphCanvas
@end


#pragma mark --- Debugging Helpers ---

/*
 * These debug functions are only compiled when CGGI_DEBUG is activated.
 * They will print out a full UInt8 canvas and any pixels struck (assuming
 * the canvas is not too big).
 *
 * As another debug feature, the entire canvas will be filled with a light
 * alpha value so it is easy to see where the glyph painting regions are
 * at runtime.
 */

#ifdef CGGI_DEBUG_DUMP
static void
DUMP_PIXELS(const char msg[], const UInt8 pixels[],
            const size_t bytesPerPixel, const int width, const int height)
{
    printf("| %s: (%d, %d)\n", msg, width, height);

    if (width > 80 || height > 80) {
        printf("| too big\n");
        return;
    }

    size_t i, j = 0, k, size = width * height;
    for (i = 0; i < size; i++) {
        for (k = 0; k < bytesPerPixel; k++) {
            if (pixels[i * bytesPerPixel + k] > 0x80) j++;
        }
    }

    if (j == 0) {
        printf("| empty\n");
        return;
    }

    printf("|_");
    int x, y;
    for (x = 0; x < width; x++) {
        printf("__");
    }
    printf("_\n");

    for (y = 0; y < height; y++) {
        printf("| ");
        for (x = 0; x < width; x++) {
            int p = 0;
            for(k = 0; k < bytesPerPixel; k++) {
                p += pixels[(y * width + x) * bytesPerPixel + k];
            }

            if (p < 0x80) {
                printf("  ");
            } else {
                printf("[]");
            }
        }
        printf(" |\n");
    }
}

static void
DUMP_IMG_PIXELS(const char msg[], const vImage_Buffer *image)
{
    const void *pixels = image->data;
    const size_t pixelSize = image->rowBytes / image->width;
    const size_t width = image->width;
    const size_t height = image->height;

    DUMP_PIXELS(msg, pixels, pixelSize, width, height);
}

static void
PRINT_CGSTATES_INFO(const CGContextRef cgRef)
{
    // TODO(cpc): lots of SPI use in this method; remove/rewrite?
#if 0
    CGRect clip = CGContextGetClipBoundingBox(cgRef);
    fprintf(stderr, "    clip: ((%f, %f), (%f, %f))\n",
            clip.origin.x, clip.origin.y, clip.size.width, clip.size.height);

    CGAffineTransform ctm = CGContextGetCTM(cgRef);
    fprintf(stderr, "    ctm: (%f, %f, %f, %f, %f, %f)\n",
            ctm.a, ctm.b, ctm.c, ctm.d, ctm.tx, ctm.ty);

    CGAffineTransform txtTx = CGContextGetTextMatrix(cgRef);
    fprintf(stderr, "    txtTx: (%f, %f, %f, %f, %f, %f)\n",
            txtTx.a, txtTx.b, txtTx.c, txtTx.d, txtTx.tx, txtTx.ty);

    if (CGContextIsPathEmpty(cgRef) == 0) {
        CGPoint pathpoint = CGContextGetPathCurrentPoint(cgRef);
        CGRect pathbbox = CGContextGetPathBoundingBox(cgRef);
        fprintf(stderr, "    [pathpoint: (%f, %f)] [pathbbox: ((%f, %f), (%f, %f))]\n",
                pathpoint.x, pathpoint.y, pathbbox.origin.x, pathbbox.origin.y,
                pathbbox.size.width, pathbbox.size.width);
    }

    CGFloat linewidth = CGContextGetLineWidth(cgRef);
    CGLineCap linecap = CGContextGetLineCap(cgRef);
    CGLineJoin linejoin = CGContextGetLineJoin(cgRef);
    CGFloat miterlimit = CGContextGetMiterLimit(cgRef);
    size_t dashcount = CGContextGetLineDashCount(cgRef);
    fprintf(stderr, "    [linewidth: %f] [linecap: %d] [linejoin: %d] [miterlimit: %f] [dashcount: %lu]\n",
            linewidth, linecap, linejoin, miterlimit, (unsigned long)dashcount);

    CGFloat smoothness = CGContextGetSmoothness(cgRef);
    bool antialias = CGContextGetShouldAntialias(cgRef);
    bool smoothfont = CGContextGetShouldSmoothFonts(cgRef);
    JRSFontRenderingStyle fRendMode = CGContextGetFontRenderingMode(cgRef);
    fprintf(stderr, "    [smoothness: %f] [antialias: %d] [smoothfont: %d] [fontrenderingmode: %d]\n",
            smoothness, antialias, smoothfont, fRendMode);
#endif
}
#endif

#ifdef CGGI_DEBUG

static void
DUMP_GLYPHINFO(const GlyphInfo *info)
{
    printf("size: (%d, %d) pixelSize: %d\n",
           info->width, info->height, info->rowBytes / info->width);
    printf("adv: (%f, %f) top: (%f, %f)\n",
           info->advanceX, info->advanceY, info->topLeftX, info->topLeftY);

#ifdef CGGI_DEBUG_DUMP
    DUMP_PIXELS("Glyph Info Struct",
                info->image, info->rowBytes / info->width,
                info->width, info->height);
#endif
}

#endif


#pragma mark --- Font Rendering Mode Descriptors ---

static inline void
CGGI_CopyARGBPixelToRGBPixel(const UInt32 p, UInt8 *dst)
{
#if __LITTLE_ENDIAN__
    *(dst + 2) = 0xFF - (p >> 24 & 0xFF);
    *(dst + 1) = 0xFF - (p >> 16 & 0xFF);
    *(dst) = 0xFF - (p >> 8 & 0xFF);
#else
    *(dst) = 0xFF - (p >> 16 & 0xFF);
    *(dst + 1) = 0xFF - (p >> 8 & 0xFF);
    *(dst + 2) = 0xFF - (p & 0xFF);
#endif
}

static void
CGGI_CopyImageFromCanvasToRGBInfo(CGGI_GlyphCanvas *canvas, GlyphInfo *info)
{
    UInt32 *src = (UInt32 *)canvas->image->data;
    size_t srcRowWidth = canvas->image->width;

    UInt8 *dest = (UInt8 *)info->image;
    size_t destRowWidth = info->width;

    size_t height = info->height;

    size_t y;
    for (y = 0; y < height; y++) {
        size_t destRow = y * destRowWidth * 3;
        size_t srcRow = y * srcRowWidth;

        size_t x;
        for (x = 0; x < destRowWidth; x++) {
            // size_t x3 = x * 3;
            // UInt32 p = src[srcRow + x];
            // dest[destRow + x3] = 0xFF - (p >> 16 & 0xFF);
            // dest[destRow + x3 + 1] = 0xFF - (p >> 8 & 0xFF);
            // dest[destRow + x3 + 2] = 0xFF - (p & 0xFF);
            CGGI_CopyARGBPixelToRGBPixel(src[srcRow + x],
                                         dest + destRow + x * 3);
        }
    }
}

//static void CGGI_copyImageFromCanvasToAlphaInfo
//(CGGI_GlyphCanvas *canvas, GlyphInfo *info)
//{
//    vImage_Buffer infoBuffer;
//    infoBuffer.data = info->image;
//    infoBuffer.width = info->width;
//    infoBuffer.height = info->height;
//    infoBuffer.rowBytes = info->width; // three bytes per RGB pixel
//
//    UInt8 scrapPixel[info->width * info->height];
//    vImage_Buffer scrapBuffer;
//    scrapBuffer.data = &scrapPixel;
//    scrapBuffer.width = info->width;
//    scrapBuffer.height = info->height;
//    scrapBuffer.rowBytes = info->width;
//
//    vImageConvert_ARGB8888toPlanar8(canvas->image, &infoBuffer,
//        &scrapBuffer, &scrapBuffer, &scrapBuffer, kvImageNoFlags);
//}

static inline UInt8
CGGI_ConvertPixelToGreyBit(UInt32 p)
{
#ifdef __LITTLE_ENDIAN__
    return 0xFF - ((p >> 24 & 0xFF) + (p >> 16 & 0xFF) + (p >> 8 & 0xFF)) / 3;
#else
    return 0xFF - ((p >> 16 & 0xFF) + (p >> 8 & 0xFF) + (p & 0xFF)) / 3;
#endif
}

static void
CGGI_CopyImageFromCanvasToAlphaInfo(CGGI_GlyphCanvas *canvas, GlyphInfo *info)
{
    UInt32 *src = (UInt32 *)canvas->image->data;
    size_t srcRowWidth = canvas->image->width;

    UInt8 *dest = (UInt8 *)info->image;
    size_t destRowWidth = info->width;

    size_t height = info->height;

    size_t y;
    for (y = 0; y < height; y++) {
        size_t destRow = y * destRowWidth;
        size_t srcRow = y * srcRowWidth;

        size_t x;
        for (x = 0; x < destRowWidth; x++) {
            UInt32 p = src[srcRow + x];
            dest[destRow + x] = CGGI_ConvertPixelToGreyBit(p);
        }
    }
}


#pragma mark --- Pixel Size, Modes, and Canvas Shaping Helper Functions ---

typedef struct CGGI_GlyphInfoDescriptor {
    size_t pixelSize;
    void (*copyFxnPtr)(CGGI_GlyphCanvas *canvas, GlyphInfo *info);
} CGGI_GlyphInfoDescriptor;

typedef struct CGGI_RenderingMode {
    CGGI_GlyphInfoDescriptor *glyphDescriptor;
    JRSFontRenderingStyle cgFontMode;
} CGGI_RenderingMode;

static CGGI_GlyphInfoDescriptor grey =
    { 1, &CGGI_CopyImageFromCanvasToAlphaInfo };
static CGGI_GlyphInfoDescriptor rgb =
    { 3, &CGGI_CopyImageFromCanvasToRGBInfo };

static inline CGGI_RenderingMode
CGGI_GetRenderingMode(const AWTStrike *strike)
{
    CGGI_RenderingMode mode;
    mode.cgFontMode = strike->fStyle;

    switch (strike->fAAStyle) {
    case sun_awt_SunHints_INTVAL_TEXT_ANTIALIAS_DEFAULT:
    case sun_awt_SunHints_INTVAL_TEXT_ANTIALIAS_OFF:
    case sun_awt_SunHints_INTVAL_TEXT_ANTIALIAS_ON:
    case sun_awt_SunHints_INTVAL_TEXT_ANTIALIAS_GASP:
    default:
        mode.glyphDescriptor = &grey;
        break;
    case sun_awt_SunHints_INTVAL_TEXT_ANTIALIAS_LCD_HRGB:
    case sun_awt_SunHints_INTVAL_TEXT_ANTIALIAS_LCD_HBGR:
    case sun_awt_SunHints_INTVAL_TEXT_ANTIALIAS_LCD_VRGB:
    case sun_awt_SunHints_INTVAL_TEXT_ANTIALIAS_LCD_VBGR:
        mode.glyphDescriptor = &rgb;
        break;
    }

    return mode;
}


#pragma mark --- Canvas Managment ---

/*
 * Creates a new canvas of a fixed size, and initializes the CGContext as
 * an 32-bit ARGB BitmapContext with some generic RGB color space.
 */
static inline void
CGGI_InitCanvas(CGGI_GlyphCanvas *canvas,
                const vImagePixelCount width, const vImagePixelCount height)
{
    // our canvas is *always* 4-byte ARGB
    size_t bytesPerRow = width * sizeof(UInt32);
    size_t byteCount = bytesPerRow * height;

    canvas->image = malloc(sizeof(vImage_Buffer));
    canvas->image->width = width;
    canvas->image->height = height;
    canvas->image->rowBytes = bytesPerRow;

    canvas->image->data = (void *)calloc(byteCount, sizeof(UInt32));
    if (canvas->image->data == NULL) {
        [[NSException exceptionWithName:NSMallocException
            reason:@"Failed to allocate memory for the buffer which backs the CGContext for glyph strikes." userInfo:nil] raise];
    }

    CGColorSpaceRef colorSpace = CGColorSpaceCreateWithName(kCGColorSpaceGenericRGB);
    canvas->context = CGBitmapContextCreate(canvas->image->data,
                                            width, height, 8, bytesPerRow,
                                            colorSpace,
                                            kCGImageAlphaPremultipliedFirst);

    CGContextSetRGBFillColor(canvas->context, 0.0f, 0.0f, 0.0f, 1.0f);
    CGContextSetFontSize(canvas->context, 1);
    CGContextSaveGState(canvas->context);

    CGColorSpaceRelease(colorSpace);
}

/*
 * Releases the BitmapContext and the associated memory backing it.
 */
static inline void
CGGI_FreeCanvas(CGGI_GlyphCanvas *canvas)
{
    if (canvas->context != NULL) {
        CGContextRelease(canvas->context);
    }

    if (canvas->image != NULL) {
        if (canvas->image->data != NULL) {
            free(canvas->image->data);
        }
        free(canvas->image);
    }
}

/*
 * This is the slack space that is preallocated for the global GlyphCanvas
 * when it needs to be expanded. It has been set somewhat liberally to
 * avoid re-upsizing frequently.
 */
#define CGGI_GLYPH_CANVAS_SLACK 2.5

/*
 * Quick and easy inline to check if this canvas is big enough.
 */
static inline void
CGGI_SizeCanvas(CGGI_GlyphCanvas *canvas, const vImagePixelCount width, const vImagePixelCount height, const JRSFontRenderingStyle style)
{
    if (canvas->image != NULL &&
        width  < canvas->image->width &&
        height < canvas->image->height)
    {
        return;
    }

    // if we don't have enough space to strike the largest glyph in the
    // run, resize the canvas
    CGGI_FreeCanvas(canvas);
    CGGI_InitCanvas(canvas,
                    width * CGGI_GLYPH_CANVAS_SLACK,
                    height * CGGI_GLYPH_CANVAS_SLACK);
    JRSFontSetRenderingStyleOnContext(canvas->context, style);
}

/*
 * Clear the canvas by blitting white only into the region of interest
 * (the rect which we will copy out of once the glyph is struck).
 */
static inline void
CGGI_ClearCanvas(CGGI_GlyphCanvas *canvas, GlyphInfo *info)
{
    vImage_Buffer canvasRectToClear;
    canvasRectToClear.data = canvas->image->data;
    canvasRectToClear.height = info->height;
    canvasRectToClear.width = info->width;
    // use the row stride of the canvas, not the info
    canvasRectToClear.rowBytes = canvas->image->rowBytes;

    // clean the canvas
#ifdef CGGI_DEBUG
    Pixel_8888 opaqueWhite = { 0xE0, 0xE0, 0xE0, 0xE0 };
#else
    Pixel_8888 opaqueWhite = { 0xFF, 0xFF, 0xFF, 0xFF };
#endif

    vImageBufferFill_ARGB8888(&canvasRectToClear, opaqueWhite, kvImageNoFlags);
}


#pragma mark --- GlyphInfo Creation & Copy Functions ---

/*
 * Creates a GlyphInfo with exactly the correct size image and measurements.
 */
#define CGGI_GLYPH_BBOX_PADDING 2.0f
static inline GlyphInfo *
CGGI_CreateNewGlyphInfoFrom(CGSize advance, CGRect bbox,
                            const AWTStrike *strike,
                            const CGGI_RenderingMode *mode)
{
    size_t pixelSize = mode->glyphDescriptor->pixelSize;

    // adjust the bounding box to be 1px bigger on each side than what
    // CGFont-whatever suggests - because it gives a bounding box that
    // is too tight
    bbox.size.width += CGGI_GLYPH_BBOX_PADDING * 2.0f;
    bbox.size.height += CGGI_GLYPH_BBOX_PADDING * 2.0f;
    bbox.origin.x -= CGGI_GLYPH_BBOX_PADDING;
    bbox.origin.y -= CGGI_GLYPH_BBOX_PADDING;

    vImagePixelCount width = ceilf(bbox.size.width);
    vImagePixelCount height = ceilf(bbox.size.height);

    // if the glyph is larger than 1MB, don't even try...
    // the GlyphVector path should have taken over by now
    // and zero pixels is ok
    if (width * height > 1024 * 1024) {
        width = 1;
        height = 1;
    }
    advance = CGSizeApplyAffineTransform(advance, strike->fFontTx);
    if (!JRSFontStyleUsesFractionalMetrics(strike->fStyle)) {
        advance.width = round(advance.width);
        advance.height = round(advance.height);
    }
    advance = CGSizeApplyAffineTransform(advance, strike->fDevTx);

#ifdef USE_IMAGE_ALIGNED_MEMORY
    // create separate memory
    GlyphInfo *glyphInfo = (GlyphInfo *)malloc(sizeof(GlyphInfo));
    void *image = (void *)malloc(height * width * pixelSize);
#else
    // create a GlyphInfo struct fused to the image it points to
    GlyphInfo *glyphInfo = (GlyphInfo *)malloc(sizeof(GlyphInfo) +
                                               height * width * pixelSize);
#endif

    glyphInfo->advanceX = advance.width;
    glyphInfo->advanceY = advance.height;
    glyphInfo->topLeftX = round(bbox.origin.x);
    glyphInfo->topLeftY = round(bbox.origin.y);
    glyphInfo->width = width;
    glyphInfo->height = height;
    glyphInfo->rowBytes = width * pixelSize;
    glyphInfo->cellInfo = NULL;

#ifdef USE_IMAGE_ALIGNED_MEMORY
    glyphInfo->image = image;
#else
    glyphInfo->image = ((void *)glyphInfo) + sizeof(GlyphInfo);
#endif

    return glyphInfo;
}


#pragma mark --- Glyph Striking onto Canvas ---

/*
 * Clears the canvas, strikes the glyph with CoreGraphics, and then
 * copies the struck pixels into the GlyphInfo image.
 */
static inline void
CGGI_CreateImageForGlyph
    (CGGI_GlyphCanvas *canvas, const CGGlyph glyph,
     GlyphInfo *info, const CGGI_RenderingMode *mode)
{
    // clean the canvas
    CGGI_ClearCanvas(canvas, info);

    // strike the glyph in the upper right corner
    CGContextShowGlyphsAtPoint(canvas->context,
                               -info->topLeftX,
                               canvas->image->height + info->topLeftY,
                               &glyph, 1);

    // copy the glyph from the canvas into the info
    (*mode->glyphDescriptor->copyFxnPtr)(canvas, info);
}

/*
 * CoreText path...
 */
static inline GlyphInfo *
CGGI_CreateImageForUnicode
    (CGGI_GlyphCanvas *canvas, const AWTStrike *strike,
     const CGGI_RenderingMode *mode, const UniChar uniChar)
{
    // save the state of the world
    CGContextSaveGState(canvas->context);

    // get the glyph, measure it using CG
    CGGlyph glyph;
    CTFontRef fallback;
    if (uniChar > 0xFFFF) {
        UTF16Char charRef[2];
        CTS_BreakupUnicodeIntoSurrogatePairs(uniChar, charRef);
        CGGlyph glyphTmp[2];
        fallback = CTS_CopyCTFallbackFontAndGlyphForUnicode(strike->fAWTFont, (const UTF16Char *)&charRef, (CGGlyph *)&glyphTmp, 2);
        glyph = glyphTmp[0];
    } else {
        UTF16Char charRef;
        charRef = (UTF16Char) uniChar; // truncate.
        fallback = CTS_CopyCTFallbackFontAndGlyphForUnicode(strike->fAWTFont, (const UTF16Char *)&charRef, &glyph, 1);
    }

    CGAffineTransform tx = strike->fTx;
    JRSFontRenderingStyle style = JRSFontAlignStyleForFractionalMeasurement(strike->fStyle);

    CGRect bbox;
    JRSFontGetBoundingBoxesForGlyphsAndStyle(fallback, &tx, style, &glyph, 1, &bbox);

    CGSize advance;
    CTFontGetAdvancesForGlyphs(fallback, kCTFontDefaultOrientation, &glyph, &advance, 1);

    // create the Sun2D GlyphInfo we are going to strike into
    GlyphInfo *info = CGGI_CreateNewGlyphInfoFrom(advance, bbox, strike, mode);

    // fix the context size, just in case the substituted character is unexpectedly large
    CGGI_SizeCanvas(canvas, info->width, info->height, mode->cgFontMode);

    // align the transform for the real CoreText strike
    CGContextSetTextMatrix(canvas->context, strike->fAltTx);

    const CGFontRef cgFallback = CTFontCopyGraphicsFont(fallback, NULL);
    CGContextSetFont(canvas->context, cgFallback);
    CFRelease(cgFallback);

    // clean the canvas - align, strike, and copy the glyph from the canvas into the info
    CGGI_CreateImageForGlyph(canvas, glyph, info, mode);

    // restore the state of the world
    CGContextRestoreGState(canvas->context);

    CFRelease(fallback);
#ifdef CGGI_DEBUG
    DUMP_GLYPHINFO(info);
#endif

#ifdef CGGI_DEBUG_DUMP
    DUMP_IMG_PIXELS("CGGI Canvas", canvas->image);
#if 0
    PRINT_CGSTATES_INFO(NULL);
#endif
#endif

    return info;
}


#pragma mark --- GlyphInfo Filling and Canvas Managment ---

/*
 * Sets all the per-run properties for the canvas, and then iterates through
 * the character run, and creates images in the GlyphInfo structs.
 *
 * Not inlined because it would create two copies in the function below
 */
static void
CGGI_FillImagesForGlyphsWithSizedCanvas(CGGI_GlyphCanvas *canvas,
                                        const AWTStrike *strike,
                                        const CGGI_RenderingMode *mode,
                                        jlong glyphInfos[],
                                        const UniChar uniChars[],
                                        const CGGlyph glyphs[],
                                        const CFIndex len)
{
    CGContextSetTextMatrix(canvas->context, strike->fAltTx);

    CGContextSetFont(canvas->context, strike->fAWTFont->fNativeCGFont);
    JRSFontSetRenderingStyleOnContext(canvas->context, strike->fStyle);

    CFIndex i;
    for (i = 0; i < len; i++) {
        GlyphInfo *info = (GlyphInfo *)jlong_to_ptr(glyphInfos[i]);
        if (info != NULL) {
            CGGI_CreateImageForGlyph(canvas, glyphs[i], info, mode);
        } else {
            info = CGGI_CreateImageForUnicode(canvas, strike, mode, uniChars[i]);
            glyphInfos[i] = ptr_to_jlong(info);
        }
#ifdef CGGI_DEBUG
        DUMP_GLYPHINFO(info);
#endif

#ifdef CGGI_DEBUG_DUMP
        DUMP_IMG_PIXELS("CGGI Canvas", canvas->image);
#endif
    }
#ifdef CGGI_DEBUG_DUMP
    DUMP_IMG_PIXELS("CGGI Canvas", canvas->image);
    PRINT_CGSTATES_INFO(canvas->context);
#endif
}

static NSString *threadLocalCanvasKey =
    @"Java CoreGraphics Text Renderer Cached Canvas";

/*
 * This is the maximum length and height times the above slack squared
 * to determine if we go with the global canvas, or malloc one on the spot.
 */
#define CGGI_GLYPH_CANVAS_MAX 100

/*
 * Based on the space needed to strike the largest character in the run,
 * either use the global shared canvas, or make one up on the spot, strike
 * the glyphs, and destroy it.
 */
static inline void
CGGI_FillImagesForGlyphs(jlong *glyphInfos, const AWTStrike *strike,
                         const CGGI_RenderingMode *mode,
                         const UniChar uniChars[], const CGGlyph glyphs[],
                         const size_t maxWidth, const size_t maxHeight,
                         const CFIndex len)
{
    if (maxWidth*maxHeight*CGGI_GLYPH_CANVAS_SLACK*CGGI_GLYPH_CANVAS_SLACK >
        CGGI_GLYPH_CANVAS_MAX*CGGI_GLYPH_CANVAS_MAX*CGGI_GLYPH_CANVAS_SLACK*CGGI_GLYPH_CANVAS_SLACK)
    {
        CGGI_GlyphCanvas *tmpCanvas = [[CGGI_GlyphCanvas alloc] init];
        CGGI_InitCanvas(tmpCanvas, maxWidth, maxHeight);
        CGGI_FillImagesForGlyphsWithSizedCanvas(tmpCanvas, strike,
                                                mode, glyphInfos, uniChars,
                                                glyphs, len);
        CGGI_FreeCanvas(tmpCanvas);

        [tmpCanvas release];
        return;
    }

    NSMutableDictionary *threadDict =
        [[NSThread currentThread] threadDictionary];
    CGGI_GlyphCanvas *canvas = [threadDict objectForKey:threadLocalCanvasKey];
    if (canvas == nil) {
        canvas = [[CGGI_GlyphCanvas alloc] init];
        [threadDict setObject:canvas forKey:threadLocalCanvasKey];
    }

    CGGI_SizeCanvas(canvas, maxWidth, maxHeight, mode->cgFontMode);
    CGGI_FillImagesForGlyphsWithSizedCanvas(canvas, strike, mode,
                                            glyphInfos, uniChars, glyphs, len);
}

/*
 * Finds the advances and bounding boxes of the characters in the run,
 * cycles through all the bounds and calculates the maximum canvas space
 * required by the largest glyph.
 *
 * Creates a GlyphInfo struct with a malloc that also encapsulates the
 * image the struct points to.  This is done to meet memory layout
 * expectations in the Sun text rasterizer memory managment code.
 * The image immediately follows the struct physically in memory.
 */
static inline void
CGGI_CreateGlyphInfos(jlong *glyphInfos, const AWTStrike *strike,
                      const CGGI_RenderingMode *mode,
                      const UniChar uniChars[], const CGGlyph glyphs[],
                      CGSize advances[], CGRect bboxes[], const CFIndex len)
{
    AWTFont *font = strike->fAWTFont;
    CGAffineTransform tx = strike->fTx;
    JRSFontRenderingStyle bboxCGMode = JRSFontAlignStyleForFractionalMeasurement(strike->fStyle);

    JRSFontGetBoundingBoxesForGlyphsAndStyle((CTFontRef)font->fFont, &tx, bboxCGMode, glyphs, len, bboxes);
    CTFontGetAdvancesForGlyphs((CTFontRef)font->fFont, kCTFontDefaultOrientation, glyphs, advances, len);

    size_t maxWidth = 1;
    size_t maxHeight = 1;

    CFIndex i;
    for (i = 0; i < len; i++)
    {
        if (uniChars[i] != 0)
        {
            glyphInfos[i] = 0L;
            continue; // will be handled later
        }

        CGSize advance = advances[i];
        CGRect bbox = bboxes[i];

        GlyphInfo *glyphInfo = CGGI_CreateNewGlyphInfoFrom(advance, bbox, strike, mode);

        if (maxWidth < glyphInfo->width)   maxWidth = glyphInfo->width;
        if (maxHeight < glyphInfo->height) maxHeight = glyphInfo->height;

        glyphInfos[i] = ptr_to_jlong(glyphInfo);
    }

    CGGI_FillImagesForGlyphs(glyphInfos, strike, mode, uniChars,
                             glyphs, maxWidth, maxHeight, len);
}


#pragma mark --- Temporary Buffer Allocations and Initialization ---

/*
 * This stage separates the already valid glyph codes from the unicode values
 * that need special handling - the rawGlyphCodes array is no longer used
 * after this stage.
 */
static void
CGGI_CreateGlyphsAndScanForComplexities(jlong *glyphInfos,
                                        const AWTStrike *strike,
                                        const CGGI_RenderingMode *mode,
                                        jint rawGlyphCodes[],
                                        UniChar uniChars[], CGGlyph glyphs[],
                                        CGSize advances[], CGRect bboxes[],
                                        const CFIndex len)
{
    CFIndex i;
    for (i = 0; i < len; i++) {
        jint code = rawGlyphCodes[i];
        if (code < 0) {
            glyphs[i] = 0;
            uniChars[i] = -code;
        } else {
            glyphs[i] = code;
            uniChars[i] = 0;
        }
    }

    CGGI_CreateGlyphInfos(glyphInfos, strike, mode,
                          uniChars, glyphs, advances, bboxes, len);

#ifdef CGGI_DEBUG_HIT_COUNT
    static size_t hitCount = 0;
    hitCount++;
    printf("%d\n", (int)hitCount);
#endif
}

/*
 * Conditionally stack allocates buffers for glyphs, bounding boxes,
 * and advances.  Unfortunately to use CG or CT in bulk runs (which is
 * faster than calling them per character), we have to copy into and out
 * of these buffers. Still a net win though.
 */
void
CGGlyphImages_GetGlyphImagePtrs(jlong glyphInfos[],
                                const AWTStrike *strike,
                                jint rawGlyphCodes[], const CFIndex len)
{
    const CGGI_RenderingMode mode = CGGI_GetRenderingMode(strike);

    if (len < MAX_STACK_ALLOC_GLYPH_BUFFER_SIZE) {
        CGRect bboxes[len];
        CGSize advances[len];
        CGGlyph glyphs[len];
        UniChar uniChars[len];

        CGGI_CreateGlyphsAndScanForComplexities(glyphInfos, strike, &mode,
                                                rawGlyphCodes, uniChars, glyphs,
                                                advances, bboxes, len);

        return;
    }

    // just do one malloc, and carve it up for all the buffers
    void *buffer = malloc(sizeof(CGRect) * sizeof(CGSize) *
                          sizeof(CGGlyph) * sizeof(UniChar) * len);
    if (buffer == NULL) {
        [[NSException exceptionWithName:NSMallocException
            reason:@"Failed to allocate memory for the temporary glyph strike and measurement buffers." userInfo:nil] raise];
    }

    CGRect *bboxes = (CGRect *)(buffer);
    CGSize *advances = (CGSize *)(bboxes + sizeof(CGRect) * len);
    CGGlyph *glyphs = (CGGlyph *)(advances + sizeof(CGGlyph) * len);
    UniChar *uniChars = (UniChar *)(glyphs + sizeof(UniChar) * len);

    CGGI_CreateGlyphsAndScanForComplexities(glyphInfos, strike, &mode,
                                            rawGlyphCodes, uniChars, glyphs,
                                            advances, bboxes, len);

    free(buffer);
}
