/*
 * Copyright (c) 2007, 2008, Oracle and/or its affiliates. All rights reserved.
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

#include "stdlib.h"
#include "malloc.h"
#include "string.h"
#include "gdefs.h"
#include "jlong.h"
#include "sunfontids.h"
#include "fontscalerdefs.h"
#include "sun_font_FontManager.h"
#include "sun_font_NullFontScaler.h"
#include "sun_font_StrikeCache.h"

static void *theNullScalerContext = NULL;
extern void AccelGlyphCache_RemoveAllCellInfos(GlyphInfo *glyph);


JNIEXPORT jlong JNICALL
Java_sun_font_NullFontScaler_getNullScalerContext
    (JNIEnv *env, jclass scalerClass) {

    if (theNullScalerContext == NULL) {
        theNullScalerContext = malloc(1);
    }
    return ptr_to_jlong(theNullScalerContext);
}

int isNullScalerContext(void *context) {
    return theNullScalerContext == context;
}

/* Eventually we may rework it to be a singleton.
 * This will require additional checks in freeLongMemory/freeIntMemory
 * and on other hand malformed fonts (main source of null glyph images)
 * are supposed to be collected fast.
 * But perhaps it is still right thing to do.
 * Even better is to eliminate the need to have this native method
 * but for this it is necessary to rework Strike and drawing logic
 * to be able to live with NULL pointers without performance hit.
 */
JNIEXPORT jlong JNICALL Java_sun_font_NullFontScaler_getGlyphImage
  (JNIEnv *env, jobject scaler, jlong pContext, jint glyphCode) {
    void *nullscaler = calloc(sizeof(GlyphInfo), 1);
    return ptr_to_jlong(nullscaler);
}



void initLCDGammaTables();

/* placeholder for extern variable */
FontManagerNativeIDs sunFontIDs;

JNIEXPORT void JNICALL
Java_sun_font_SunFontManager_initIDs
    (JNIEnv *env, jclass cls) {

     jclass tmpClass = (*env)->FindClass(env, "sun/font/TrueTypeFont");
     sunFontIDs.ttReadBlockMID =
         (*env)->GetMethodID(env, tmpClass, "readBlock",
                             "(Ljava/nio/ByteBuffer;II)I");
     sunFontIDs.ttReadBytesMID =
         (*env)->GetMethodID(env, tmpClass, "readBytes", "(II)[B");

     tmpClass = (*env)->FindClass(env, "sun/font/Type1Font");
     sunFontIDs.readFileMID =
         (*env)->GetMethodID(env, tmpClass,
                             "readFile", "(Ljava/nio/ByteBuffer;)V");

     tmpClass = (*env)->FindClass(env, "java/awt/geom/Point2D$Float");
     sunFontIDs.pt2DFloatClass = (jclass)(*env)->NewGlobalRef(env, tmpClass);
     sunFontIDs.pt2DFloatCtr =
         (*env)->GetMethodID(env, sunFontIDs.pt2DFloatClass, "<init>","(FF)V");

     sunFontIDs.xFID =
         (*env)->GetFieldID(env, sunFontIDs.pt2DFloatClass, "x", "F");
     sunFontIDs.yFID =
         (*env)->GetFieldID(env, sunFontIDs.pt2DFloatClass, "y", "F");

     tmpClass = (*env)->FindClass(env, "sun/font/StrikeMetrics");
     sunFontIDs.strikeMetricsClass=(jclass)(*env)->NewGlobalRef(env, tmpClass);

     sunFontIDs.strikeMetricsCtr =
         (*env)->GetMethodID(env, sunFontIDs.strikeMetricsClass,
                             "<init>", "(FFFFFFFFFF)V");

     tmpClass = (*env)->FindClass(env, "java/awt/geom/Rectangle2D$Float");
     sunFontIDs.rect2DFloatClass = (jclass)(*env)->NewGlobalRef(env, tmpClass);
     sunFontIDs.rect2DFloatCtr =
       (*env)->GetMethodID(env, sunFontIDs.rect2DFloatClass, "<init>", "()V");
     sunFontIDs.rect2DFloatCtr4 =
       (*env)->GetMethodID(env, sunFontIDs.rect2DFloatClass,
                           "<init>", "(FFFF)V");
     sunFontIDs.rectF2DX =
         (*env)->GetFieldID(env, sunFontIDs.rect2DFloatClass, "x", "F");
     sunFontIDs.rectF2DY =
         (*env)->GetFieldID(env, sunFontIDs.rect2DFloatClass, "y", "F");
     sunFontIDs.rectF2DWidth =
         (*env)->GetFieldID(env, sunFontIDs.rect2DFloatClass, "width", "F");
     sunFontIDs.rectF2DHeight =
         (*env)->GetFieldID(env, sunFontIDs.rect2DFloatClass, "height", "F");

     tmpClass = (*env)->FindClass(env, "java/awt/geom/GeneralPath");
     sunFontIDs.gpClass = (jclass)(*env)->NewGlobalRef(env, tmpClass);
     sunFontIDs.gpCtr =
       (*env)->GetMethodID(env, sunFontIDs.gpClass, "<init>", "(I[BI[FI)V");
     sunFontIDs.gpCtrEmpty =
       (*env)->GetMethodID(env, sunFontIDs.gpClass, "<init>", "()V");

     tmpClass = (*env)->FindClass(env, "sun/font/Font2D");
     sunFontIDs.f2dCharToGlyphMID =
         (*env)->GetMethodID(env, tmpClass, "charToGlyph", "(I)I");
     sunFontIDs.getMapperMID =
         (*env)->GetMethodID(env, tmpClass, "getMapper",
                             "()Lsun/font/CharToGlyphMapper;");
     sunFontIDs.getTableBytesMID =
         (*env)->GetMethodID(env, tmpClass, "getTableBytes", "(I)[B");
     sunFontIDs.canDisplayMID =
         (*env)->GetMethodID(env, tmpClass, "canDisplay", "(C)Z");

     tmpClass = (*env)->FindClass(env, "sun/font/CharToGlyphMapper");
     sunFontIDs.charToGlyphMID =
        (*env)->GetMethodID(env, tmpClass, "charToGlyph", "(I)I");

     tmpClass = (*env)->FindClass(env, "sun/font/PhysicalStrike");
     sunFontIDs.getGlyphMetricsMID =
         (*env)->GetMethodID(env, tmpClass, "getGlyphMetrics",
                             "(I)Ljava/awt/geom/Point2D$Float;");
     sunFontIDs.getGlyphPointMID =
         (*env)->GetMethodID(env, tmpClass, "getGlyphPoint",
                             "(II)Ljava/awt/geom/Point2D$Float;");
     sunFontIDs.adjustPointMID =
         (*env)->GetMethodID(env, tmpClass, "adjustPoint",
                             "(Ljava/awt/geom/Point2D$Float;)V");
     sunFontIDs.pScalerContextFID =
         (*env)->GetFieldID(env, tmpClass, "pScalerContext", "J");

     tmpClass = (*env)->FindClass(env, "sun/font/GlyphList");
     sunFontIDs.glyphListX = (*env)->GetFieldID(env, tmpClass, "x", "F");
     sunFontIDs.glyphListY = (*env)->GetFieldID(env, tmpClass, "y", "F");
     sunFontIDs.glyphListLen = (*env)->GetFieldID(env, tmpClass, "len", "I");
     sunFontIDs.glyphImages =
         (*env)->GetFieldID(env, tmpClass, "images", "[J");
     sunFontIDs.glyphListUsePos =
         (*env)->GetFieldID(env, tmpClass, "usePositions", "Z");
     sunFontIDs.glyphListPos =
         (*env)->GetFieldID(env, tmpClass, "positions", "[F");
     sunFontIDs.lcdRGBOrder =
         (*env)->GetFieldID(env, tmpClass, "lcdRGBOrder", "Z");
     sunFontIDs.lcdSubPixPos =
         (*env)->GetFieldID(env, tmpClass, "lcdSubPixPos", "Z");

     initLCDGammaTables();
}

JNIEXPORT FontManagerNativeIDs getSunFontIDs() {
    return sunFontIDs;
}

/*
 * Class:     sun_font_StrikeCache
 * Method:    freeIntPointer
 * Signature: (I)V
 */
JNIEXPORT void JNICALL Java_sun_font_StrikeCache_freeIntPointer
    (JNIEnv *env, jclass cacheClass, jint ptr) {

    /* Note this is used for freeing a glyph which was allocated
     * but never placed into the glyph cache. The caller holds the
     * only reference, therefore it is unnecessary to invalidate any
     * accelerated glyph cache cells as we do in freeInt/LongMemory().
     */
    if (ptr != 0) {
        free((void*)ptr);
    }
}

/*
 * Class:     sun_font_StrikeCache
 * Method:    freeLongPointer
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_sun_font_StrikeCache_freeLongPointer
    (JNIEnv *env, jclass cacheClass, jlong ptr) {

    /* Note this is used for freeing a glyph which was allocated
     * but never placed into the glyph cache. The caller holds the
     * only reference, therefore it is unnecessary to invalidate any
     * accelerated glyph cache cells as we do in freeInt/LongMemory().
     */
    if (ptr != 0L) {
        free(jlong_to_ptr(ptr));
    }
}

/*
 * Class:     sun_font_StrikeCache
 * Method:    freeIntMemory
 * Signature: ([I)V
 */
JNIEXPORT void JNICALL Java_sun_font_StrikeCache_freeIntMemory
    (JNIEnv *env, jclass cacheClass, jintArray jmemArray, jlong pContext) {

    int len = (*env)->GetArrayLength(env, jmemArray);
    jint* ptrs =
        (jint*)(*env)->GetPrimitiveArrayCritical(env, jmemArray, NULL);
    int i;

    if (ptrs) {
        for (i=0; i< len; i++) {
            if (ptrs[i] != 0) {
                GlyphInfo *ginfo = (GlyphInfo *)ptrs[i];
                if (ginfo->cellInfo != NULL) {
                    // invalidate this glyph's accelerated cache cell
                    AccelGlyphCache_RemoveAllCellInfos(ginfo);
                }
                free((void*)ginfo);
            }
        }
        (*env)->ReleasePrimitiveArrayCritical(env, jmemArray, ptrs, JNI_ABORT);
    }
    if (!isNullScalerContext(jlong_to_ptr(pContext))) {
        free(jlong_to_ptr(pContext));
    }
}

/*
 * Class:     sun_font_StrikeCache
 * Method:    freeLongMemory
 * Signature: ([J)V
 */
JNIEXPORT void JNICALL Java_sun_font_StrikeCache_freeLongMemory
    (JNIEnv *env, jclass cacheClass, jlongArray jmemArray, jlong pContext) {

    int len = (*env)->GetArrayLength(env, jmemArray);
    jlong* ptrs =
        (jlong*)(*env)->GetPrimitiveArrayCritical(env, jmemArray, NULL);
    int i;

    if (ptrs) {
        for (i=0; i< len; i++) {
            if (ptrs[i] != 0L) {
                GlyphInfo *ginfo = (GlyphInfo *) jlong_to_ptr(ptrs[i]);
                if (ginfo->cellInfo != NULL) {
                    AccelGlyphCache_RemoveAllCellInfos(ginfo);
                }
                free((void*)ginfo);
            }
        }
        (*env)->ReleasePrimitiveArrayCritical(env, jmemArray, ptrs, JNI_ABORT);
    }
    if (!isNullScalerContext(jlong_to_ptr(pContext))) {
        free(jlong_to_ptr(pContext));
    }
}

JNIEXPORT void JNICALL
Java_sun_font_StrikeCache_getGlyphCacheDescription
  (JNIEnv *env, jclass cls, jlongArray results) {

    jlong* nresults;
    GlyphInfo *info;
    size_t baseAddr;

    if ((*env)->GetArrayLength(env, results) < 10) {
        return;
    }

    nresults = (jlong*)(*env)->GetPrimitiveArrayCritical(env, results, NULL);
    if (nresults == NULL) {
        return;
    }
    info = (GlyphInfo*) calloc(1, sizeof(GlyphInfo));
    if (info == NULL) {
        (*env)->ReleasePrimitiveArrayCritical(env, results, nresults, 0);
        return;
    }
    baseAddr = (size_t)info;
    nresults[0] = sizeof(void*);
    nresults[1] = sizeof(GlyphInfo);
    nresults[2] = 0;
    nresults[3] = (size_t)&(info->advanceY)-baseAddr;
    nresults[4] = (size_t)&(info->width)-baseAddr;
    nresults[5] = (size_t)&(info->height)-baseAddr;
    nresults[6] = (size_t)&(info->rowBytes)-baseAddr;
    nresults[7] = (size_t)&(info->topLeftX)-baseAddr;
    nresults[8] = (size_t)&(info->topLeftY)-baseAddr;
    nresults[9] = (size_t)&(info->image)-baseAddr;
    nresults[10] = (jlong)(uintptr_t)info; /* invisible glyph */
    (*env)->ReleasePrimitiveArrayCritical(env, results, nresults, 0);
}

JNIEXPORT TTLayoutTableCache* newLayoutTableCache() {
  TTLayoutTableCache* ltc = calloc(1, sizeof(TTLayoutTableCache));
  if (ltc) {
    ltc->gsub_len = -1;
    ltc->gpos_len = -1;
    ltc->gdef_len = -1;
    ltc->mort_len = -1;
    ltc->kern_len = -1;
  }
  return ltc;
}

JNIEXPORT void freeLayoutTableCache(TTLayoutTableCache* ltc) {
  if (ltc) {
    if (ltc->gsub) free(ltc->gsub);
    if (ltc->gpos) free(ltc->gpos);
    if (ltc->gdef) free(ltc->gdef);
    if (ltc->mort) free(ltc->mort);
    if (ltc->kern) free(ltc->kern);
    if (ltc->kernPairs) free(ltc->kernPairs);
    free(ltc);
  }
}
