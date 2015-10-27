/*
 * Copyright (c) 2007, 2013, Oracle and/or its affiliates. All rights reserved.
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

#include "jni.h"
#include "jni_util.h"
#include "jlong.h"
#include "sunfontids.h"
#include "sun_font_FreetypeFontScaler.h"

#include<stdlib.h>
#include <math.h>
#include "ft2build.h"
#include FT_FREETYPE_H
#include FT_GLYPH_H
#include FT_BBOX_H
#include FT_SIZES_H
#include FT_OUTLINE_H
#include FT_SYNTHESIS_H

#include "fontscaler.h"

#define  ftFixed1  (FT_Fixed) (1 << 16)
#define  FloatToFTFixed(f) (FT_Fixed)((f) * (float)(ftFixed1))
#define  FTFixedToFloat(x) ((x) / (float)(ftFixed1))
#define  FT26Dot6ToFloat(x)  ((x) / ((float) (1<<6)))
#define  ROUND(x) ((int) (x+0.5))

typedef struct {
    /* Important note:
         JNI forbids sharing same env between different threads.
         We are safe, because pointer is overwritten every time we get into
         JNI call (see setupFTContext).

         Pointer is used by font data reading callbacks
         such as ReadTTFontFileFunc.

         NB: We may consider switching to JNI_GetEnv. */
    JNIEnv* env;
    FT_Library library;
    FT_Face face;
    FT_Stream faceStream;
    jobject font2D;
    jobject directBuffer;

    unsigned char* fontData;
    unsigned fontDataOffset;
    unsigned fontDataLength;
    unsigned fileSize;
    TTLayoutTableCache* layoutTables;
} FTScalerInfo;

typedef struct FTScalerContext {
    FT_Matrix  transform;     /* glyph transform, including device transform */
    jboolean   useSbits;      /* sbit usage enabled? */
    jint       aaType;        /* antialiasing mode (off/on/grey/lcd) */
    jint       fmType;        /* fractional metrics - on/off */
    jboolean   doBold;        /* perform algorithmic bolding? */
    jboolean   doItalize;     /* perform algorithmic italicizing? */
    int        renderFlags;   /* configuration specific to particular engine */
    int        pathType;
    int        ptsz;          /* size in points */
} FTScalerContext;

#ifdef DEBUG
/* These are referenced in the freetype sources if DEBUG macro is defined.
   To simplify work with debuging version of freetype we define
   them here. */
int z_verbose;
void z_error(char *s) {}
#endif

/**************** Error handling utilities *****************/

static jmethodID invalidateScalerMID;

JNIEXPORT void JNICALL
Java_sun_font_FreetypeFontScaler_initIDs(
        JNIEnv *env, jobject scaler, jclass FFSClass) {
    invalidateScalerMID =
        (*env)->GetMethodID(env, FFSClass, "invalidateScaler", "()V");
}

static void freeNativeResources(JNIEnv *env, FTScalerInfo* scalerInfo) {

    if (scalerInfo == NULL)
        return;

    // FT_Done_Face always closes the stream, but only frees the memory
    // of the data structure if it was internally allocated by FT.
    // We hold on to a pointer to the stream structure if we provide it
    // ourselves, so that we can free it here.
    FT_Done_Face(scalerInfo->face);
    FT_Done_FreeType(scalerInfo->library);

    if (scalerInfo->directBuffer != NULL) {
        (*env)->DeleteGlobalRef(env, scalerInfo->directBuffer);
    }

    if (scalerInfo->fontData != NULL) {
        free(scalerInfo->fontData);
    }

    if (scalerInfo->faceStream != NULL) {
        free(scalerInfo->faceStream);
    }
    free(scalerInfo);
}

/* invalidates state of java scaler object */
static void invalidateJavaScaler(JNIEnv *env,
                                 jobject scaler,
                                 FTScalerInfo* scalerInfo) {
    freeNativeResources(env, scalerInfo);
    (*env)->CallVoidMethod(env, scaler, invalidateScalerMID);
}

/******************* I/O handlers ***************************/

#define FILEDATACACHESIZE 1024

static unsigned long ReadTTFontFileFunc(FT_Stream stream,
                                        unsigned long offset,
                                        unsigned char* destBuffer,
                                        unsigned long numBytes)
{
    FTScalerInfo *scalerInfo = (FTScalerInfo *) stream->pathname.pointer;
    JNIEnv* env = scalerInfo->env;
    jobject bBuffer;
    int bread = 0;

    if (numBytes == 0) return 0;

    /* Large reads will bypass the cache and data copying */
    if (numBytes > FILEDATACACHESIZE) {
        bBuffer = (*env)->NewDirectByteBuffer(env, destBuffer, numBytes);
        if (bBuffer != NULL) {
            bread = (*env)->CallIntMethod(env,
                                          scalerInfo->font2D,
                                          sunFontIDs.ttReadBlockMID,
                                          bBuffer, offset, numBytes);
            return bread;
        } else {
            /* We probably hit bug 4845371. For reasons that
             * are currently unclear, the call stacks after the initial
             * createScaler call that read large amounts of data seem to
             * be OK and can create the byte buffer above, but this code
             * is here just in case.
             * 4845371 is fixed now so I don't expect this code path to
             * ever get called but its harmless to leave it here on the
             * small chance its needed.
             */
            jbyteArray byteArray = (jbyteArray)
            (*env)->CallObjectMethod(env, scalerInfo->font2D,
                                     sunFontIDs.ttReadBytesMID,
                                     offset, numBytes);
            (*env)->GetByteArrayRegion(env, byteArray,
                                       0, numBytes, (jbyte*)destBuffer);
            return numBytes;
        }
    } /* Do we have a cache hit? */
      else if (scalerInfo->fontDataOffset <= offset &&
        scalerInfo->fontDataOffset + scalerInfo->fontDataLength >=
                                                         offset + numBytes)
    {
        unsigned cacheOffset = offset - scalerInfo->fontDataOffset;

        memcpy(destBuffer, scalerInfo->fontData+(size_t)cacheOffset, numBytes);
        return numBytes;
    } else {
        /* Must fill the cache */
        scalerInfo->fontDataOffset = offset;
        scalerInfo->fontDataLength =
                 (offset + FILEDATACACHESIZE > scalerInfo->fileSize) ?
                 scalerInfo->fileSize - offset : FILEDATACACHESIZE;
        bBuffer = scalerInfo->directBuffer;
        bread = (*env)->CallIntMethod(env, scalerInfo->font2D,
                                      sunFontIDs.ttReadBlockMID,
                                      bBuffer, offset,
                                      scalerInfo->fontDataLength);
        memcpy(destBuffer, scalerInfo->fontData, numBytes);
        return numBytes;
    }
}

/*
 * Class:     sun_font_FreetypeFontScaler
 * Method:    initNativeScaler
 * Signature: (Lsun/font/Font2D;IIZI)J
 */
JNIEXPORT jlong JNICALL
Java_sun_font_FreetypeFontScaler_initNativeScaler(
        JNIEnv *env, jobject scaler, jobject font2D, jint type,
        jint indexInCollection, jboolean supportsCJK, jint filesize) {
    FTScalerInfo* scalerInfo = NULL;
    FT_Open_Args ft_open_args;
    int error;
    jobject bBuffer;
    scalerInfo = (FTScalerInfo*) calloc(1, sizeof(FTScalerInfo));

    if (scalerInfo == NULL)
        return 0;

    scalerInfo->env = env;
    scalerInfo->font2D = font2D;
    scalerInfo->fontDataOffset = 0;
    scalerInfo->fontDataLength = 0;
    scalerInfo->fileSize = filesize;

    /*
       We can consider sharing freetype library between different
       scalers. However, Freetype docs suggest to use different libraries
       for different threads. Also, our architecture implies that single
       FontScaler object is shared for different sizes/transforms/styles
       of the same font.

       On other hand these methods can not be concurrently executed
       becaused they are "synchronized" in java.
    */
    error = FT_Init_FreeType(&scalerInfo->library);
    if (error) {
        free(scalerInfo);
        return 0;
    }

#define TYPE1_FROM_JAVA        2

    error = 1; /* triggers memory freeing unless we clear it */
    if (type == TYPE1_FROM_JAVA) { /* TYPE1 */
        scalerInfo->fontData = (unsigned char*) malloc(filesize);
        scalerInfo->directBuffer = NULL;
        scalerInfo->layoutTables = NULL;
        scalerInfo->fontDataLength = filesize;

        if (scalerInfo->fontData != NULL) {
            bBuffer = (*env)->NewDirectByteBuffer(env,
                                              scalerInfo->fontData,
                                              scalerInfo->fontDataLength);
            if (bBuffer != NULL) {
                (*env)->CallObjectMethod(env, font2D,
                                   sunFontIDs.readFileMID, bBuffer);

                error = FT_New_Memory_Face(scalerInfo->library,
                                   scalerInfo->fontData,
                                   scalerInfo->fontDataLength,
                                   indexInCollection,
                                   &scalerInfo->face);
            }
        }
    } else { /* Truetype */
        scalerInfo->fontData = (unsigned char*) malloc(FILEDATACACHESIZE);

        if (scalerInfo->fontData != NULL) {
            FT_Stream ftstream = (FT_Stream) calloc(1, sizeof(FT_StreamRec));
            if (ftstream != NULL) {
                scalerInfo->directBuffer = (*env)->NewDirectByteBuffer(env,
                                           scalerInfo->fontData,
                                           FILEDATACACHESIZE);
                if (scalerInfo->directBuffer != NULL) {
                    scalerInfo->directBuffer = (*env)->NewGlobalRef(env,
                                               scalerInfo->directBuffer);
                    ftstream->base = NULL;
                    ftstream->size = filesize;
                    ftstream->pos = 0;
                    ftstream->read = (FT_Stream_IoFunc) ReadTTFontFileFunc;
                    ftstream->close = NULL;
                    ftstream->pathname.pointer = (void *) scalerInfo;

                    memset(&ft_open_args, 0, sizeof(FT_Open_Args));
                    ft_open_args.flags = FT_OPEN_STREAM;
                    ft_open_args.stream = ftstream;

                    error = FT_Open_Face(scalerInfo->library,
                                         &ft_open_args,
                                         indexInCollection,
                                         &scalerInfo->face);
                    if (!error) {
                        scalerInfo->faceStream = ftstream;
                    }
                }
                if (error || scalerInfo->directBuffer == NULL) {
                    free(ftstream);
                }
            }
        }
    }

    if (error) {
        FT_Done_FreeType(scalerInfo->library);
        if (scalerInfo->directBuffer != NULL) {
            (*env)->DeleteGlobalRef(env, scalerInfo->directBuffer);
        }
        if (scalerInfo->fontData != NULL)
            free(scalerInfo->fontData);
        free(scalerInfo);
        return 0;
    }

    return ptr_to_jlong(scalerInfo);
}

static double euclidianDistance(double a, double b) {
    if (a < 0) a=-a;
    if (b < 0) b=-b;

    if (a == 0) return b;
    if (b == 0) return a;

    return sqrt(a*a+b*b);
}

JNIEXPORT jlong JNICALL
Java_sun_font_FreetypeFontScaler_createScalerContextNative(
        JNIEnv *env, jobject scaler, jlong pScaler, jdoubleArray matrix,
        jint aa, jint fm, jfloat boldness, jfloat italic) {
    double dmat[4], ptsz;
    FTScalerContext *context =
            (FTScalerContext*) calloc(1, sizeof(FTScalerContext));
    FTScalerInfo *scalerInfo =
             (FTScalerInfo*) jlong_to_ptr(pScaler);

    if (context == NULL) {
        invalidateJavaScaler(env, scaler, NULL);
        return (jlong) 0;
    }
    (*env)->GetDoubleArrayRegion(env, matrix, 0, 4, dmat);
    ptsz = euclidianDistance(dmat[2], dmat[3]); //i.e. y-size
    if (ptsz < 1.0) {
        //text can not be smaller than 1 point
        ptsz = 1.0;
    }
    context->ptsz = (int)(ptsz * 64);
    context->transform.xx =  FloatToFTFixed((float)dmat[0]/ptsz);
    context->transform.yx = -FloatToFTFixed((float)dmat[1]/ptsz);
    context->transform.xy = -FloatToFTFixed((float)dmat[2]/ptsz);
    context->transform.yy =  FloatToFTFixed((float)dmat[3]/ptsz);
    context->aaType = aa;
    context->fmType = fm;

    /* If using algorithmic styling, the base values are
     * boldness = 1.0, italic = 0.0.
     */
    context->doBold = (boldness != 1.0);
    context->doItalize = (italic != 0);

    return ptr_to_jlong(context);
}

static int setupFTContext(JNIEnv *env,
                          jobject font2D,
                          FTScalerInfo *scalerInfo,
                          FTScalerContext *context) {
    int errCode = 0;

    scalerInfo->env = env;
    scalerInfo->font2D = font2D;

    if (context != NULL) {
        FT_Set_Transform(scalerInfo->face, &context->transform, NULL);

        errCode = FT_Set_Char_Size(scalerInfo->face, 0, context->ptsz, 72, 72);

        if (errCode == 0) {
            errCode = FT_Activate_Size(scalerInfo->face->size);
        }
    }

    return errCode;
}

/* ftsynth.c uses (0x10000, 0x06000, 0x0, 0x10000) matrix to get oblique
   outline.  Therefore x coordinate will change by 0x06000*y.
   Note that y coordinate does not change. */
#define OBLIQUE_MODIFIER(y)  (context->doItalize ? ((y)*6/16) : 0)

/*
 * Class:     sun_font_FreetypeFontScaler
 * Method:    getFontMetricsNative
 * Signature: (Lsun/font/Font2D;J)Lsun/font/StrikeMetrics;
 */
JNIEXPORT jobject JNICALL
Java_sun_font_FreetypeFontScaler_getFontMetricsNative(
        JNIEnv *env, jobject scaler, jobject font2D,
        jlong pScalerContext, jlong pScaler) {

    jobject metrics;
    jfloat ax, ay, dx, dy, bx, by, lx, ly, mx, my;
    jfloat f0 = 0.0;
    FTScalerContext *context =
        (FTScalerContext*) jlong_to_ptr(pScalerContext);
    FTScalerInfo *scalerInfo =
             (FTScalerInfo*) jlong_to_ptr(pScaler);

    int errCode;

    if (isNullScalerContext(context) || scalerInfo == NULL) {
        return (*env)->NewObject(env,
                                 sunFontIDs.strikeMetricsClass,
                                 sunFontIDs.strikeMetricsCtr,
                                 f0, f0, f0, f0, f0, f0, f0, f0, f0, f0);
    }

    errCode = setupFTContext(env, font2D, scalerInfo, context);

    if (errCode) {
        metrics = (*env)->NewObject(env,
                                 sunFontIDs.strikeMetricsClass,
                                 sunFontIDs.strikeMetricsCtr,
                                 f0, f0, f0, f0, f0, f0, f0, f0, f0, f0);
        invalidateJavaScaler(env, scaler, scalerInfo);
        return metrics;
    }

    /* This is ugly and has to be reworked.
       Freetype provide means to add style to glyph but
       it seems there is no way to adjust metrics accordingly.

       So, we have to do adust them explicitly and stay consistent with what
       freetype does to outlines. */


    /**** Note: only some metrics are affected by styling ***/

    /* See https://bugs.debian.org/cgi-bin/bugreport.cgi?bug=657854 */
#define FT_MulFixFloatShift6(a, b) (((float) (a)) * ((float) (b)) / 65536.0 / 64.0)

    /*
     * See FreeType source code: src/base/ftobjs.c ft_recompute_scaled_metrics()
     * http://icedtea.classpath.org/bugzilla/show_bug.cgi?id=1659
     */
    /* ascent */
    ax = 0;
    ay = -(jfloat) (FT_MulFixFloatShift6(
                       ((jlong) scalerInfo->face->ascender),
                       (jlong) scalerInfo->face->size->metrics.y_scale));
    /* descent */
    dx = 0;
    dy = -(jfloat) (FT_MulFixFloatShift6(
                       ((jlong) scalerInfo->face->descender),
                       (jlong) scalerInfo->face->size->metrics.y_scale));
    /* baseline */
    bx = by = 0;

    /* leading */
    lx = 0;
    ly = (jfloat) (FT_MulFixFloatShift6(
                      (jlong) scalerInfo->face->height,
                      (jlong) scalerInfo->face->size->metrics.y_scale))
                  + ay - dy;
    /* max advance */
    mx = (jfloat) FT26Dot6ToFloat(
                     scalerInfo->face->size->metrics.max_advance +
                     OBLIQUE_MODIFIER(scalerInfo->face->size->metrics.height));
    my = 0;

    metrics = (*env)->NewObject(env,
                                sunFontIDs.strikeMetricsClass,
                                sunFontIDs.strikeMetricsCtr,
                                ax, ay, dx, dy, bx, by, lx, ly, mx, my);

    return metrics;
}

/*
 * Class:     sun_font_FreetypeFontScaler
 * Method:    getGlyphAdvanceNative
 * Signature: (Lsun/font/Font2D;JI)F
 */
JNIEXPORT jfloat JNICALL
Java_sun_font_FreetypeFontScaler_getGlyphAdvanceNative(
        JNIEnv *env, jobject scaler, jobject font2D,
        jlong pScalerContext, jlong pScaler, jint glyphCode) {

   /* This method is rarely used because requests for metrics are usually
      coupled with request for bitmap and to large extend work can be reused
      (to find out metrics we need to hint glyph).
      So, we typically go through getGlyphImage code path.

      For initial freetype implementation we delegate
      all work to getGlyphImage but drop result image.
      This is waste of work related to scan conversion and conversion from
      freetype format to our format but for now this seems to be ok.

      NB: investigate performance benefits of refactoring code
      to avoid unnecesary work with bitmaps. */

    GlyphInfo *info;
    jfloat advance;
    jlong image;

    image = Java_sun_font_FreetypeFontScaler_getGlyphImageNative(
                 env, scaler, font2D, pScalerContext, pScaler, glyphCode);
    info = (GlyphInfo*) jlong_to_ptr(image);

    advance = info->advanceX;

    free(info);

    return advance;
}

/*
 * Class:     sun_font_FreetypeFontScaler
 * Method:    getGlyphMetricsNative
 * Signature: (Lsun/font/Font2D;JILjava/awt/geom/Point2D/Float;)V
 */
JNIEXPORT void JNICALL
Java_sun_font_FreetypeFontScaler_getGlyphMetricsNative(
        JNIEnv *env, jobject scaler, jobject font2D, jlong pScalerContext,
        jlong pScaler, jint glyphCode, jobject metrics) {

     /* As initial implementation we delegate all work to getGlyphImage
        but drop result image. This is clearly waste of resorces.

        TODO: investigate performance benefits of refactoring code
              by avoiding bitmap generation and conversion from FT
              bitmap format. */
     GlyphInfo *info;

     jlong image = Java_sun_font_FreetypeFontScaler_getGlyphImageNative(
                                 env, scaler, font2D,
                                 pScalerContext, pScaler, glyphCode);
     info = (GlyphInfo*) jlong_to_ptr(image);

     (*env)->SetFloatField(env, metrics, sunFontIDs.xFID, info->advanceX);
     (*env)->SetFloatField(env, metrics, sunFontIDs.yFID, info->advanceY);

     free(info);
}


static GlyphInfo* getNullGlyphImage() {
    GlyphInfo *glyphInfo =  (GlyphInfo*) calloc(1, sizeof(GlyphInfo));
    return glyphInfo;
}

static void CopyBW2Grey8(const void* srcImage, int srcRowBytes,
                         void* dstImage, int dstRowBytes,
                         int width, int height) {
    const UInt8* srcRow = (UInt8*)srcImage;
    UInt8* dstRow = (UInt8*)dstImage;
    int wholeByteCount = width >> 3;
    int remainingBitsCount = width & 7;
    int i, j;

    while (height--) {
        const UInt8* src8 = srcRow;
        UInt8* dstByte = dstRow;
        unsigned srcValue;

        srcRow += srcRowBytes;
        dstRow += dstRowBytes;

        for (i = 0; i < wholeByteCount; i++) {
            srcValue = *src8++;
            for (j = 0; j < 8; j++) {
                *dstByte++ = (srcValue & 0x80) ? 0xFF : 0;
                srcValue <<= 1;
            }
        }
        if (remainingBitsCount) {
            srcValue = *src8;
            for (j = 0; j < remainingBitsCount; j++) {
                *dstByte++ = (srcValue & 0x80) ? 0xFF : 0;
                srcValue <<= 1;
            }
        }
    }
}

#define Grey4ToAlpha255(value) (((value) << 4) + ((value) >> 3))

static void CopyGrey4ToGrey8(const void* srcImage, int srcRowBytes,
                void* dstImage, int dstRowBytes, int width, int height) {
     const UInt8* srcRow = (UInt8*) srcImage;
     UInt8* dstRow = (UInt8*) dstImage;
     int i;

     while (height--) {
         const UInt8* src8 = srcRow;
         UInt8* dstByte = dstRow;
         unsigned srcValue;

         srcRow += srcRowBytes;
         dstRow += dstRowBytes;

         for (i = 0; i < width; i++) {
             srcValue = *src8++;
             *dstByte++ = Grey4ToAlpha255(srcValue & 0x0f);
             *dstByte++ = Grey4ToAlpha255(srcValue >> 4);
         }
     }
}

/* We need it because FT rows are often padded to 4 byte boundaries
    and our internal format is not padded */
static void CopyFTSubpixelToSubpixel(const void* srcImage, int srcRowBytes,
                                     void* dstImage, int dstRowBytes,
                                     int width, int height) {
    unsigned char *srcRow = (unsigned char *) srcImage;
    unsigned char *dstRow = (unsigned char *) dstImage;

    while (height--) {
        memcpy(dstRow, srcRow, width);
        srcRow += srcRowBytes;
        dstRow += dstRowBytes;
    }
}

/* We need it because FT rows are often padded to 4 byte boundaries
   and our internal format is not padded */
static void CopyFTSubpixelVToSubpixel(const void* srcImage, int srcRowBytes,
                                      void* dstImage, int dstRowBytes,
                                      int width, int height) {
    unsigned char *srcRow = (unsigned char *) srcImage, *srcByte;
    unsigned char *dstRow = (unsigned char *) dstImage, *dstByte;
    int i;

    while (height > 0) {
        srcByte = srcRow;
        dstByte = dstRow;
        for (i = 0; i < width; i++) {
            *dstByte++ = *srcByte;
            *dstByte++ = *(srcByte + srcRowBytes);
            *dstByte++ = *(srcByte + 2*srcRowBytes);
            srcByte++;
        }
        srcRow += 3*srcRowBytes;
        dstRow += dstRowBytes;
        height -= 3;
    }
}


/*
 * Class:     sun_font_FreetypeFontScaler
 * Method:    getGlyphImageNative
 * Signature: (Lsun/font/Font2D;JI)J
 */
JNIEXPORT jlong JNICALL
Java_sun_font_FreetypeFontScaler_getGlyphImageNative(
        JNIEnv *env, jobject scaler, jobject font2D,
        jlong pScalerContext, jlong pScaler, jint glyphCode) {

    int error, imageSize;
    UInt16 width, height;
    GlyphInfo *glyphInfo;
    int glyph_index;
    int renderFlags = FT_LOAD_RENDER, target;
    FT_GlyphSlot ftglyph;

    FTScalerContext* context =
        (FTScalerContext*) jlong_to_ptr(pScalerContext);
    FTScalerInfo *scalerInfo =
             (FTScalerInfo*) jlong_to_ptr(pScaler);

    if (isNullScalerContext(context) || scalerInfo == NULL) {
        return ptr_to_jlong(getNullGlyphImage());
    }

    error = setupFTContext(env, font2D, scalerInfo, context);
    if (error) {
        invalidateJavaScaler(env, scaler, scalerInfo);
        return ptr_to_jlong(getNullGlyphImage());
    }

    /* if algorithmic styling is required then we do not request bitmap */
    if (context->doBold || context->doItalize) {
        renderFlags =  FT_LOAD_DEFAULT;
    }

    /* NB: in case of non identity transform
     we might also prefer to disable transform before hinting,
     and apply it explicitly after hinting is performed.
     Or we can disable hinting. */

    /* select appropriate hinting mode */
    if (context->aaType == TEXT_AA_OFF) {
        target = FT_LOAD_TARGET_MONO;
    } else if (context->aaType == TEXT_AA_ON) {
        target = FT_LOAD_TARGET_NORMAL;
    } else if (context->aaType == TEXT_AA_LCD_HRGB ||
               context->aaType == TEXT_AA_LCD_HBGR) {
        target = FT_LOAD_TARGET_LCD;
    } else {
        target = FT_LOAD_TARGET_LCD_V;
    }
    renderFlags |= target;

    glyph_index = FT_Get_Char_Index(scalerInfo->face, glyphCode);

    error = FT_Load_Glyph(scalerInfo->face, glyphCode, renderFlags);
    if (error) {
        //do not destroy scaler yet.
        //this can be problem of particular context (e.g. with bad transform)
        return ptr_to_jlong(getNullGlyphImage());
    }

    ftglyph = scalerInfo->face->glyph;

    /* apply styles */
    if (context->doBold) { /* if bold style */
        FT_GlyphSlot_Embolden(ftglyph);
    }
    if (context->doItalize) { /* if oblique */
        FT_GlyphSlot_Oblique(ftglyph);
    }

    /* generate bitmap if it is not done yet
     e.g. if algorithmic styling is performed and style was added to outline */
    if (ftglyph->format == FT_GLYPH_FORMAT_OUTLINE) {
        FT_Render_Glyph(ftglyph, FT_LOAD_TARGET_MODE(target));
    }

    width  = (UInt16) ftglyph->bitmap.width;
    height = (UInt16) ftglyph->bitmap.rows;

    imageSize = width*height;
    glyphInfo = (GlyphInfo*) malloc(sizeof(GlyphInfo) + imageSize);
    if (glyphInfo == NULL) {
        glyphInfo = getNullGlyphImage();
        return ptr_to_jlong(glyphInfo);
    }
    glyphInfo->cellInfo  = NULL;
    glyphInfo->managed   = UNMANAGED_GLYPH;
    glyphInfo->rowBytes  = width;
    glyphInfo->width     = width;
    glyphInfo->height    = height;
    glyphInfo->topLeftX  = (float)  ftglyph->bitmap_left;
    glyphInfo->topLeftY  = (float) -ftglyph->bitmap_top;

    if (ftglyph->bitmap.pixel_mode ==  FT_PIXEL_MODE_LCD) {
        glyphInfo->width = width/3;
    } else if (ftglyph->bitmap.pixel_mode ==  FT_PIXEL_MODE_LCD_V) {
        glyphInfo->height = glyphInfo->height/3;
    }

    if (context->fmType == TEXT_FM_ON) {
        double advh = FTFixedToFloat(ftglyph->linearHoriAdvance);
        glyphInfo->advanceX =
            (float) (advh * FTFixedToFloat(context->transform.xx));
        glyphInfo->advanceY =
            (float) (advh * FTFixedToFloat(context->transform.xy));
    } else {
        if (!ftglyph->advance.y) {
            glyphInfo->advanceX =
                (float) ROUND(FT26Dot6ToFloat(ftglyph->advance.x));
            glyphInfo->advanceY = 0;
        } else if (!ftglyph->advance.x) {
            glyphInfo->advanceX = 0;
            glyphInfo->advanceY =
                (float) ROUND(FT26Dot6ToFloat(-ftglyph->advance.y));
        } else {
            glyphInfo->advanceX = FT26Dot6ToFloat(ftglyph->advance.x);
            glyphInfo->advanceY = FT26Dot6ToFloat(-ftglyph->advance.y);
        }
    }

    if (imageSize == 0) {
        glyphInfo->image = NULL;
    } else {
        glyphInfo->image = (unsigned char*) glyphInfo + sizeof(GlyphInfo);
        //convert result to output format
        //output format is either 3 bytes per pixel (for subpixel modes)
        // or 1 byte per pixel for AA and B&W
        if (ftglyph->bitmap.pixel_mode ==  FT_PIXEL_MODE_MONO) {
            /* convert from 8 pixels per byte to 1 byte per pixel */
            CopyBW2Grey8(ftglyph->bitmap.buffer,
                         ftglyph->bitmap.pitch,
                         (void *) glyphInfo->image,
                         width,
                         width,
                         height);
        } else if (ftglyph->bitmap.pixel_mode ==  FT_PIXEL_MODE_GRAY) {
            /* byte per pixel to byte per pixel => just copy */
            memcpy(glyphInfo->image, ftglyph->bitmap.buffer, imageSize);
        } else if (ftglyph->bitmap.pixel_mode ==  FT_PIXEL_MODE_GRAY4) {
            /* 4 bits per pixel to byte per pixel */
            CopyGrey4ToGrey8(ftglyph->bitmap.buffer,
                             ftglyph->bitmap.pitch,
                             (void *) glyphInfo->image,
                             width,
                             width,
                             height);
        } else if (ftglyph->bitmap.pixel_mode ==  FT_PIXEL_MODE_LCD) {
            /* 3 bytes per pixel to 3 bytes per pixel */
            CopyFTSubpixelToSubpixel(ftglyph->bitmap.buffer,
                                     ftglyph->bitmap.pitch,
                                     (void *) glyphInfo->image,
                                     width,
                                     width,
                                     height);
        } else if (ftglyph->bitmap.pixel_mode ==  FT_PIXEL_MODE_LCD_V) {
            /* 3 bytes per pixel to 3 bytes per pixel */
            CopyFTSubpixelVToSubpixel(ftglyph->bitmap.buffer,
                                      ftglyph->bitmap.pitch,
                                      (void *) glyphInfo->image,
                                      width*3,
                                      width,
                                      height);
            glyphInfo->rowBytes *=3;
        } else {
            free(glyphInfo);
            glyphInfo = getNullGlyphImage();
        }
    }

    return ptr_to_jlong(glyphInfo);
}


/*
 * Class:     sun_font_FreetypeFontScaler
 * Method:    getLayoutTableCacheNative
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL
Java_sun_font_FreetypeFontScaler_getLayoutTableCacheNative(
        JNIEnv *env, jobject scaler, jlong pScaler) {
    FTScalerInfo *scalerInfo = (FTScalerInfo*) jlong_to_ptr(pScaler);

    if (scalerInfo == NULL) {
        invalidateJavaScaler(env, scaler, scalerInfo);
        return 0L;
    }

    // init layout table cache in font
    // we're assuming the font is a file font and moreover it is Truetype font
    // otherwise we shouldn't be able to get here...
    if (scalerInfo->layoutTables == NULL) {
        scalerInfo->layoutTables = newLayoutTableCache();
    }

    return ptr_to_jlong(scalerInfo->layoutTables);
}

/*
 * Class:     sun_font_FreetypeFontScaler
 * Method:    disposeNativeScaler
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_sun_font_FreetypeFontScaler_disposeNativeScaler(
        JNIEnv *env, jobject scaler, jobject font2D, jlong pScaler) {
    FTScalerInfo* scalerInfo = (FTScalerInfo *) jlong_to_ptr(pScaler);

    /* Freetype functions *may* cause callback to java
       that can use cached values. Make sure our cache is up to date.
       NB: scaler context is not important at this point, can use NULL. */
    int errCode = setupFTContext(env, font2D, scalerInfo, NULL);
    if (errCode) {
        return;
    }

    freeNativeResources(env, scalerInfo);
}

/*
 * Class:     sun_font_FreetypeFontScaler
 * Method:    getNumGlyphsNative
 * Signature: ()I
 */
JNIEXPORT jint JNICALL
Java_sun_font_FreetypeFontScaler_getNumGlyphsNative(
        JNIEnv *env, jobject scaler, jlong pScaler) {
    FTScalerInfo* scalerInfo = (FTScalerInfo *) jlong_to_ptr(pScaler);

    if (scalerInfo == NULL || scalerInfo->face == NULL) { /* bad/null scaler */
        /* null scaler can render 1 glyph - "missing glyph" with code 0
           (all glyph codes requested by user are mapped to code 0 at
           validation step) */
        invalidateJavaScaler(env, scaler, scalerInfo);
        return (jint) 1;
    }

    return (jint) scalerInfo->face->num_glyphs;
}

/*
 * Class:     sun_font_FreetypeFontScaler
 * Method:    getMissingGlyphCodeNative
 * Signature: ()I
 */
JNIEXPORT jint JNICALL
Java_sun_font_FreetypeFontScaler_getMissingGlyphCodeNative(
        JNIEnv *env, jobject scaler, jlong pScaler) {

    /* Is it always 0 for freetype? */
    return 0;
}

/*
 * Class:     sun_font_FreetypeFontScaler
 * Method:    getGlyphCodeNative
 * Signature: (C)I
 */
JNIEXPORT jint JNICALL
Java_sun_font_FreetypeFontScaler_getGlyphCodeNative(
        JNIEnv *env, jobject scaler,
        jobject font2D, jlong pScaler, jchar charCode) {

    FTScalerInfo* scalerInfo = (FTScalerInfo *) jlong_to_ptr(pScaler);
    int errCode;

    if (scaler == NULL || scalerInfo->face == NULL) { /* bad/null scaler */
        invalidateJavaScaler(env, scaler, scalerInfo);
        return 0;
    }

    /* Freetype functions *may* cause callback to java
       that can use cached values. Make sure our cache is up to date.
       Scaler context is not important here, can use NULL. */
    errCode = setupFTContext(env, font2D, scalerInfo, NULL);
    if (errCode) {
        return 0;
    }

    return FT_Get_Char_Index(scalerInfo->face, charCode);
}


#define FloatToF26Dot6(x) ((unsigned int) ((x)*64))

static FT_Outline* getFTOutline(JNIEnv* env, jobject font2D,
        FTScalerContext *context, FTScalerInfo* scalerInfo,
        jint glyphCode, jfloat xpos, jfloat ypos) {
    int renderFlags;
    int glyph_index;
    FT_Error error;
    FT_GlyphSlot ftglyph;

    if (glyphCode >= INVISIBLE_GLYPHS ||
            isNullScalerContext(context) || scalerInfo == NULL) {
        return NULL;
    }

    error = setupFTContext(env, font2D, scalerInfo, context);
    if (error) {
        return NULL;
    }

    renderFlags = FT_LOAD_NO_HINTING | FT_LOAD_NO_BITMAP;

    glyph_index = FT_Get_Char_Index(scalerInfo->face, glyphCode);

    error = FT_Load_Glyph(scalerInfo->face, glyphCode, renderFlags);
    if (error) {
        return NULL;
    }

    ftglyph = scalerInfo->face->glyph;

    /* apply styles */
    if (context->doBold) { /* if bold style */
        FT_GlyphSlot_Embolden(ftglyph);
    }
    if (context->doItalize) { /* if oblique */
        FT_GlyphSlot_Oblique(ftglyph);
    }

    FT_Outline_Translate(&ftglyph->outline,
                         FloatToF26Dot6(xpos),
                         -FloatToF26Dot6(ypos));

    return &ftglyph->outline;
}

#define F26Dot6ToFloat(n) (((float)(n))/((float) 64))

/* Types of GeneralPath segments.
   TODO: pull constants from other place? */

#define SEG_UNKNOWN -1
#define SEG_MOVETO   0
#define SEG_LINETO   1
#define SEG_QUADTO   2
#define SEG_CUBICTO  3
#define SEG_CLOSE    4

#define WIND_NON_ZERO 0
#define WIND_EVEN_ODD 1

/* Placeholder to accumulate GeneralPath data */
typedef struct {
    jint numTypes;
    jint numCoords;
    jint lenTypes;
    jint lenCoords;
    jint wr;
    jbyte* pointTypes;
    jfloat* pointCoords;
} GPData;

/* returns 0 on failure */
static int allocateSpaceForGP(GPData* gpdata, int npoints, int ncontours) {
    int maxTypes, maxCoords;

    /* we may have up to N intermediate points per contour
       (and for each point can actually cause new curve to be generated)
       In addition we can also have 2 extra point per outline.
     */
    maxTypes  = 2*npoints  + 2*ncontours;
    maxCoords = 4*(npoints + 2*ncontours); //we may need to insert
                                           //up to n-1 intermediate points

    /* first usage - allocate space and intialize all fields */
    if (gpdata->pointTypes == NULL || gpdata->pointCoords == NULL) {
        gpdata->lenTypes  = maxTypes;
        gpdata->lenCoords = maxCoords;
        gpdata->pointTypes  = (jbyte*)
             malloc(gpdata->lenTypes*sizeof(jbyte));
        gpdata->pointCoords = (jfloat*)
             malloc(gpdata->lenCoords*sizeof(jfloat));
        gpdata->numTypes = 0;
        gpdata->numCoords = 0;
        gpdata->wr = WIND_NON_ZERO; /* By default, outlines are filled
                                       using the non-zero winding rule. */
    } else {
        /* do we have enough space? */
        if (gpdata->lenTypes - gpdata->numTypes < maxTypes) {
            gpdata->lenTypes  += maxTypes;
            gpdata->pointTypes  = (jbyte*)
              realloc(gpdata->pointTypes, gpdata->lenTypes*sizeof(jbyte));
        }

        if (gpdata->lenCoords - gpdata->numCoords < maxCoords) {
            gpdata->lenCoords += maxCoords;
            gpdata->pointCoords = (jfloat*)
              realloc(gpdata->pointCoords, gpdata->lenCoords*sizeof(jfloat));
        }
    }

    /* failure if any of mallocs failed */
    if (gpdata->pointTypes == NULL ||  gpdata->pointCoords == NULL)
        return 0;
    else
        return 1;
}

static void addSeg(GPData *gp, jbyte type) {
    gp->pointTypes[gp->numTypes++] = type;
}

static void addCoords(GPData *gp, FT_Vector *p) {
    gp->pointCoords[gp->numCoords++] =  F26Dot6ToFloat(p->x);
    gp->pointCoords[gp->numCoords++] = -F26Dot6ToFloat(p->y);
}

static int moveTo(FT_Vector *to, GPData *gp) {
    if (gp->numCoords)
        addSeg(gp, SEG_CLOSE);
    addCoords(gp, to);
    addSeg(gp, SEG_MOVETO);
    return FT_Err_Ok;
}

static int lineTo(FT_Vector *to, GPData *gp) {
    addCoords(gp, to);
    addSeg(gp, SEG_LINETO);
    return FT_Err_Ok;
}

static int conicTo(FT_Vector *control, FT_Vector *to, GPData *gp) {
    addCoords(gp, control);
    addCoords(gp, to);
    addSeg(gp, SEG_QUADTO);
    return FT_Err_Ok;
}

static int cubicTo(FT_Vector *control1,
                   FT_Vector *control2,
                   FT_Vector *to,
                   GPData    *gp) {
    addCoords(gp, control1);
    addCoords(gp, control2);
    addCoords(gp, to);
    addSeg(gp, SEG_CUBICTO);
    return FT_Err_Ok;
}

static void addToGP(GPData* gpdata, FT_Outline*outline) {
    static const FT_Outline_Funcs outline_funcs = {
        (FT_Outline_MoveToFunc) moveTo,
        (FT_Outline_LineToFunc) lineTo,
        (FT_Outline_ConicToFunc) conicTo,
        (FT_Outline_CubicToFunc) cubicTo,
        0, /* shift */
        0, /* delta */
    };

    FT_Outline_Decompose(outline, &outline_funcs, gpdata);
    if (gpdata->numCoords)
        addSeg(gpdata, SEG_CLOSE);

    /* If set to 1, the outline will be filled using the even-odd fill rule */
    if (outline->flags & FT_OUTLINE_EVEN_ODD_FILL) {
        gpdata->wr = WIND_EVEN_ODD;
    }
}

static void freeGP(GPData* gpdata) {
    if (gpdata->pointCoords != NULL) {
        free(gpdata->pointCoords);
        gpdata->pointCoords = NULL;
        gpdata->numCoords = 0;
        gpdata->lenCoords = 0;
    }
    if (gpdata->pointTypes != NULL) {
        free(gpdata->pointTypes);
        gpdata->pointTypes = NULL;
        gpdata->numTypes = 0;
        gpdata->lenTypes = 0;
    }
}

static jobject getGlyphGeneralPath(JNIEnv* env, jobject font2D,
        FTScalerContext *context, FTScalerInfo *scalerInfo,
        jint glyphCode, jfloat xpos, jfloat ypos) {

    FT_Outline* outline;
    jobject gp = NULL;
    jbyteArray types;
    jfloatArray coords;
    GPData gpdata;

    outline = getFTOutline(env, font2D, context, scalerInfo,
                           glyphCode, xpos, ypos);

    if (outline == NULL || outline->n_points == 0) {
        return gp;
    }

    gpdata.pointTypes  = NULL;
    gpdata.pointCoords = NULL;
    if (!allocateSpaceForGP(&gpdata, outline->n_points, outline->n_contours)) {
        return gp;
    }

    addToGP(&gpdata, outline);

    types  = (*env)->NewByteArray(env, gpdata.numTypes);
    coords = (*env)->NewFloatArray(env, gpdata.numCoords);

    if (types && coords) {
        (*env)->SetByteArrayRegion(env, types, 0,
                                   gpdata.numTypes,
                                   gpdata.pointTypes);
        (*env)->SetFloatArrayRegion(env, coords, 0,
                                    gpdata.numCoords,
                                    gpdata.pointCoords);
        gp = (*env)->NewObject(env,
                               sunFontIDs.gpClass,
                               sunFontIDs.gpCtr,
                               gpdata.wr,
                               types,
                               gpdata.numTypes,
                               coords,
                               gpdata.numCoords);
    }

    freeGP(&gpdata);

    return gp;
}

/*
 * Class:     sun_font_FreetypeFontScaler
 * Method:    getGlyphOutlineNative
 * Signature: (Lsun/font/Font2D;JIFF)Ljava/awt/geom/GeneralPath;
 */
JNIEXPORT jobject JNICALL
Java_sun_font_FreetypeFontScaler_getGlyphOutlineNative(
      JNIEnv *env, jobject scaler, jobject font2D, jlong pScalerContext,
      jlong pScaler, jint glyphCode, jfloat xpos, jfloat ypos) {

    FTScalerContext *context =
         (FTScalerContext*) jlong_to_ptr(pScalerContext);
    FTScalerInfo* scalerInfo = (FTScalerInfo *) jlong_to_ptr(pScaler);

    jobject gp = getGlyphGeneralPath(env,
                               font2D,
                               context,
                               scalerInfo,
                               glyphCode,
                               xpos,
                               ypos);
    if (gp == NULL) { /* can be legal */
        gp = (*env)->NewObject(env,
                               sunFontIDs.gpClass,
                               sunFontIDs.gpCtrEmpty);
    }
    return gp;
}

/*
 * Class:     sun_font_FreetypeFontScaler
 * Method:    getGlyphOutlineBoundsNative
 * Signature: (Lsun/font/Font2D;JI)Ljava/awt/geom/Rectangle2D/Float;
 */
JNIEXPORT jobject JNICALL
Java_sun_font_FreetypeFontScaler_getGlyphOutlineBoundsNative(
        JNIEnv *env, jobject scaler, jobject font2D,
        jlong pScalerContext, jlong pScaler, jint glyphCode) {

    FT_Outline *outline;
    FT_BBox bbox;
    int error;
    jobject bounds;

    FTScalerContext *context =
         (FTScalerContext*) jlong_to_ptr(pScalerContext);
    FTScalerInfo* scalerInfo = (FTScalerInfo *) jlong_to_ptr(pScaler);

    outline = getFTOutline(env, font2D, context, scalerInfo, glyphCode, 0, 0);
    if (outline == NULL || outline->n_points == 0) {
        /* it is legal case, e.g. invisible glyph */
        bounds = (*env)->NewObject(env,
                                 sunFontIDs.rect2DFloatClass,
                                 sunFontIDs.rect2DFloatCtr);
        return bounds;
    }

    error = FT_Outline_Get_BBox(outline, &bbox);

    //convert bbox
    if (error || bbox.xMin >= bbox.xMax || bbox.yMin >= bbox.yMax) {
        bounds = (*env)->NewObject(env,
                                   sunFontIDs.rect2DFloatClass,
                                   sunFontIDs.rect2DFloatCtr);
    } else {
        bounds = (*env)->NewObject(env,
                                   sunFontIDs.rect2DFloatClass,
                                   sunFontIDs.rect2DFloatCtr4,
                                   F26Dot6ToFloat(bbox.xMin),
                                   F26Dot6ToFloat(-bbox.yMax),
                                   F26Dot6ToFloat(bbox.xMax-bbox.xMin),
                                   F26Dot6ToFloat(bbox.yMax-bbox.yMin));
    }

    return bounds;
}

/*
 * Class:     sun_font_FreetypeFontScaler
 * Method:    getGlyphVectorOutlineNative
 * Signature: (Lsun/font/Font2D;J[IIFF)Ljava/awt/geom/GeneralPath;
 */
JNIEXPORT jobject
JNICALL
Java_sun_font_FreetypeFontScaler_getGlyphVectorOutlineNative(
        JNIEnv *env, jobject scaler, jobject font2D,
        jlong pScalerContext, jlong pScaler,
        jintArray glyphArray, jint numGlyphs, jfloat xpos, jfloat ypos) {

    FT_Outline* outline;
    jobject gp = NULL;
    jbyteArray types;
    jfloatArray coords;
    GPData gpdata;
    int i;
    jint *glyphs;

    FTScalerContext *context =
         (FTScalerContext*) jlong_to_ptr(pScalerContext);
    FTScalerInfo *scalerInfo =
             (FTScalerInfo*) jlong_to_ptr(pScaler);

    glyphs = NULL;
    if (numGlyphs > 0 && 0xffffffffu / sizeof(jint) >= numGlyphs) {
        glyphs = (jint*) malloc(numGlyphs*sizeof(jint));
    }
    if (glyphs == NULL) {
        // We reach here if:
        // 1. numGlyphs <= 0,
        // 2. overflow check failed, or
        // 3. malloc failed.
        gp = (*env)->NewObject(env, sunFontIDs.gpClass, sunFontIDs.gpCtrEmpty);
        return gp;
    }

    (*env)->GetIntArrayRegion(env, glyphArray, 0, numGlyphs, glyphs);

    gpdata.numCoords = 0;
    for (i=0; i<numGlyphs;i++) {
        if (glyphs[i] >= INVISIBLE_GLYPHS) {
            continue;
        }
        outline = getFTOutline(env,
                               font2D,
                               context,
                               scalerInfo,
                               glyphs[i],
                               xpos, ypos);

        if (outline == NULL || outline->n_points == 0) {
            continue;
        }

        gpdata.pointTypes  = NULL;
        gpdata.pointCoords = NULL;
        if (!allocateSpaceForGP(&gpdata, outline->n_points,
                                outline->n_contours)) {
            break;
        }

        addToGP(&gpdata, outline);
    }
    free(glyphs);

    if (gpdata.numCoords != 0) {
      types = (*env)->NewByteArray(env, gpdata.numTypes);
      coords = (*env)->NewFloatArray(env, gpdata.numCoords);

      if (types && coords) {
        (*env)->SetByteArrayRegion(env, types, 0,
                                   gpdata.numTypes, gpdata.pointTypes);
        (*env)->SetFloatArrayRegion(env, coords, 0,
                                    gpdata.numCoords, gpdata.pointCoords);

        gp=(*env)->NewObject(env,
                             sunFontIDs.gpClass,
                             sunFontIDs.gpCtr,
                             gpdata.wr,
                             types,
                             gpdata.numTypes,
                             coords,
                             gpdata.numCoords);
        return gp;
      }
    }
    return (*env)->NewObject(env, sunFontIDs.gpClass, sunFontIDs.gpCtrEmpty);
}

JNIEXPORT jlong JNICALL
Java_sun_font_FreetypeFontScaler_getUnitsPerEMNative(
        JNIEnv *env, jobject scaler, jlong pScaler) {

    FTScalerInfo *s = (FTScalerInfo* ) jlong_to_ptr(pScaler);

    /* Freetype doc says:
     The number of font units per EM square for this face.
     This is typically 2048 for TrueType fonts, and 1000 for Type 1 fonts.
     Only relevant for scalable formats.
     However, layout engine might be not tested with anything but 2048.

     NB: test it! */
    if (s != NULL) {
        return s->face->units_per_EM;
    }
    return 2048;
}

/* This native method is called by the OpenType layout engine. */
JNIEXPORT jobject JNICALL
Java_sun_font_FreetypeFontScaler_getGlyphPointNative(
        JNIEnv *env, jobject scaler, jobject font2D, jlong pScalerContext,
        jlong pScaler, jint glyphCode, jint pointNumber) {

    FT_Outline* outline;
    jobject point = NULL;
    jfloat x=0, y=0;
    FTScalerContext *context =
         (FTScalerContext*) jlong_to_ptr(pScalerContext);
    FTScalerInfo *scalerInfo = (FTScalerInfo*) jlong_to_ptr(pScaler);

    outline = getFTOutline(env, font2D, context, scalerInfo, glyphCode, 0, 0);

    if (outline != NULL && outline->n_points > pointNumber) {
        x =  F26Dot6ToFloat(outline->points[pointNumber].x);
        y = -F26Dot6ToFloat(outline->points[pointNumber].y);
    }

    return (*env)->NewObject(env, sunFontIDs.pt2DFloatClass,
                             sunFontIDs.pt2DFloatCtr, x, y);
}
