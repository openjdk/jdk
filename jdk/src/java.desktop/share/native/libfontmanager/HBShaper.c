/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

#include <jni_util.h>
#include <stdlib.h>
#include "hb.h"
#include "hb-jdk.h"
#include "hb-ot.h"
#ifdef MACOSX
#include "hb-coretext.h"
#endif
#include "scriptMapping.h"

static jclass gvdClass = 0;
static const char* gvdClassName = "sun/font/GlyphLayout$GVData";
static jfieldID gvdCountFID = 0;
static jfieldID gvdFlagsFID = 0;
static jfieldID gvdGlyphsFID = 0;
static jfieldID gvdPositionsFID = 0;
static jfieldID gvdIndicesFID = 0;
static int jniInited = 0;

static void getFloat(JNIEnv* env, jobject pt, jfloat *x, jfloat *y) {
    *x = (*env)->GetFloatField(env, pt, sunFontIDs.xFID);
    *y = (*env)->GetFloatField(env, pt, sunFontIDs.yFID);
}

static void putFloat(JNIEnv* env, jobject pt, jfloat x, jfloat y) {
    (*env)->SetFloatField(env, pt, sunFontIDs.xFID, x);
    (*env)->SetFloatField(env, pt, sunFontIDs.yFID, y);
}

static int init_JNI_IDs(JNIEnv *env) {
    if (jniInited) {
        return jniInited;
    }
    CHECK_NULL_RETURN(gvdClass = (*env)->FindClass(env, gvdClassName), 0);
    CHECK_NULL_RETURN(gvdClass = (jclass)(*env)->NewGlobalRef(env, gvdClass), 0);
    CHECK_NULL_RETURN(gvdCountFID = (*env)->GetFieldID(env, gvdClass, "_count", "I"), 0);
    CHECK_NULL_RETURN(gvdFlagsFID = (*env)->GetFieldID(env, gvdClass, "_flags", "I"), 0);
    CHECK_NULL_RETURN(gvdGlyphsFID = (*env)->GetFieldID(env, gvdClass, "_glyphs", "[I"), 0);
    CHECK_NULL_RETURN(gvdPositionsFID = (*env)->GetFieldID(env, gvdClass, "_positions", "[F"), 0);
    CHECK_NULL_RETURN(gvdIndicesFID = (*env)->GetFieldID(env, gvdClass, "_indices", "[I"), 0);
    jniInited = 1;
    return jniInited;
}

// gmask is the composite font slot mask
// baseindex is to be added to the character (code point) index.
int storeGVData(JNIEnv* env,
               jobject gvdata, jint slot, jint baseIndex, jobject startPt,
               int glyphCount, hb_glyph_info_t *glyphInfo,
               hb_glyph_position_t *glyphPos, hb_direction_t direction) {

    int i;
    float x=0, y=0;
    float startX, startY;
    float scale = 1.0f/64.0f;
    unsigned int* glyphs;
    float* positions;
    int initialCount, glyphArrayLen, posArrayLen, maxGlyphs, storeadv;
    unsigned int* indices;
    jarray glyphArray, posArray, inxArray;

    if (!init_JNI_IDs(env)) {
        return 0;
    }

    initialCount = (*env)->GetIntField(env, gvdata, gvdCountFID);
    glyphArray =
       (jarray)(*env)->GetObjectField(env, gvdata, gvdGlyphsFID);
    posArray =
        (jarray)(*env)->GetObjectField(env, gvdata, gvdPositionsFID);

    if (glyphArray == NULL || posArray == NULL)
    {
        JNU_ThrowArrayIndexOutOfBoundsException(env, "");
        return 0;
    }

    // The Java code catches the IIOBE and expands the storage
    // and re-invokes layout. I suppose this is expected to be rare
    // because at least in a single threaded case there should be
    // re-use of the same container, but it is a little wasteful/distateful.
    glyphArrayLen = (*env)->GetArrayLength(env, glyphArray);
    posArrayLen = (*env)->GetArrayLength(env, posArray);
    maxGlyphs = glyphCount + initialCount;
    if ((maxGlyphs >  glyphArrayLen) ||
        (maxGlyphs * 2 + 2 >  posArrayLen))
    {
        JNU_ThrowArrayIndexOutOfBoundsException(env, "");
        return 0;
    }

    getFloat(env, startPt, &startX, &startY);

    glyphs =
        (unsigned int*)(*env)->GetPrimitiveArrayCritical(env, glyphArray, NULL);
    positions = (jfloat*)(*env)->GetPrimitiveArrayCritical(env, posArray, NULL);
    for (i = 0; i < glyphCount; i++) {
        int storei = i + initialCount;
        int index = glyphInfo[i].codepoint | slot;
        if (i<glyphCount)glyphs[storei] = (unsigned int)index;
        positions[(storei*2)] = startX + x + glyphPos[i].x_offset * scale;
        positions[(storei*2)+1] = startY + y - glyphPos[i].y_offset * scale;
        x += glyphPos[i].x_advance * scale;
        y += glyphPos[i].y_advance * scale;
    }
    storeadv = initialCount+glyphCount;
    // The final slot in the positions array is important
    // because when the GlyphVector is created from this
    // data it determines the overall advance of the glyphvector
    // and this is used in positioning the next glyphvector
    // during rendering where text is broken into runs.
    // We also need to report it back into "pt", so layout can
    // pass it back down for that next run in this code.
    positions[(storeadv*2)] = startX + x;
    positions[(storeadv*2)+1] = startY + y;
    (*env)->ReleasePrimitiveArrayCritical(env, glyphArray, glyphs, 0);
    (*env)->ReleasePrimitiveArrayCritical(env, posArray, positions, 0);
    putFloat(env, startPt,positions[(storeadv*2)],positions[(storeadv*2)+1] );
    inxArray =
        (jarray)(*env)->GetObjectField(env, gvdata, gvdIndicesFID);
    indices =
        (unsigned int*)(*env)->GetPrimitiveArrayCritical(env, inxArray, NULL);
    for (i = 0; i < glyphCount; i++) {
        int cluster = glyphInfo[i].cluster;
        if (direction == HB_DIRECTION_LTR) {
            // I need to understand what hb does when processing a substring
            // I expected the cluster index to be from the start of the text
            // to process.
            // Instead it appears to be from the start of the whole thing.
            indices[i+initialCount] = cluster;
        } else {
            indices[i+initialCount] = baseIndex + glyphCount -1 -i;
        }
    }
    (*env)->ReleasePrimitiveArrayCritical(env, inxArray, indices, 0);
    (*env)->SetIntField(env, gvdata, gvdCountFID, initialCount+glyphCount);
    return initialCount+glyphCount;
}

static float euclidianDistance(float a, float b)
{
    float root;
    if (a < 0) {
        a = -a;
    }

    if (b < 0) {
        b = -b;
    }

    if (a == 0) {
        return b;
    }

    if (b == 0) {
        return a;
    }

    /* Do an initial approximation, in root */
    root = a > b ? a + (b / 2) : b + (a / 2);

    /* An unrolled Newton-Raphson iteration sequence */
    root = (root + (a * (a / root)) + (b * (b / root)) + 1) / 2;
    root = (root + (a * (a / root)) + (b * (b / root)) + 1) / 2;
    root = (root + (a * (a / root)) + (b * (b / root)) + 1) / 2;

    return root;
}

JDKFontInfo*
     createJDKFontInfo(JNIEnv *env,
                       jobject font2D,
                       jobject fontStrike,
                       jfloat ptSize,
                       jlong pScaler,
                       jlong pNativeFont,
                       jfloatArray matrix,
                       jboolean aat) {


    JDKFontInfo *fi = (JDKFontInfo*)malloc(sizeof(JDKFontInfo));
    if (!fi) {
       return NULL;
    }
    fi->env = env; // this is valid only for the life of this JNI call.
    fi->font2D = font2D;
    fi->fontStrike = fontStrike;
    fi->nativeFont = pNativeFont;
    fi->aat = aat;
    (*env)->GetFloatArrayRegion(env, matrix, 0, 4, fi->matrix);
    fi->ptSize = ptSize;
    fi->xPtSize = euclidianDistance(fi->matrix[0], fi->matrix[1]);
    fi->yPtSize = euclidianDistance(fi->matrix[2], fi->matrix[3]);

    return fi;
}


#define TYPO_RTL 0x80000000

JNIEXPORT jboolean JNICALL Java_sun_font_SunLayoutEngine_shape
    (JNIEnv *env, jclass cls,
     jobject font2D,
     jobject fontStrike,
     jfloat ptSize,
     jfloatArray matrix,
     jlong pScaler,
     jlong pNativeFont,
     jboolean aat,
     jcharArray text,
     jobject gvdata,
     jint script,
     jint offset,
     jint limit,
     jint baseIndex,
     jobject startPt,
     jint flags,
     jint slot) {

     hb_buffer_t *buffer;
     hb_font_t* hbfont;
     jchar  *chars;
     jsize len;
     int glyphCount;
     hb_glyph_info_t *glyphInfo;
     hb_glyph_position_t *glyphPos;
     hb_direction_t direction = HB_DIRECTION_LTR;
     hb_feature_t *features =  NULL;
     int featureCount = 0;

     int i;
     unsigned int buflen;

     JDKFontInfo *jdkFontInfo =
         createJDKFontInfo(env, font2D, fontStrike, ptSize,
                           pScaler, pNativeFont, matrix, aat);
     if (!jdkFontInfo) {
        return JNI_FALSE;
     }
     jdkFontInfo->env = env; // this is valid only for the life of this JNI call.
     jdkFontInfo->font2D = font2D;
     jdkFontInfo->fontStrike = fontStrike;

     hbfont = hb_jdk_font_create(jdkFontInfo, NULL);

     buffer = hb_buffer_create();
     hb_buffer_set_script(buffer, getHBScriptCode(script));
     hb_buffer_set_language(buffer,
                            hb_ot_tag_to_language(HB_OT_TAG_DEFAULT_LANGUAGE));
     if ((flags & TYPO_RTL) != 0) {
         direction = HB_DIRECTION_RTL;
     }
     hb_buffer_set_direction(buffer, direction);

     chars = (*env)->GetCharArrayElements(env, text, NULL);
     len = (*env)->GetArrayLength(env, text);

     hb_buffer_add_utf16(buffer, chars, len, offset, limit-offset);

     hb_shape_full(hbfont, buffer, features, featureCount, 0);
     glyphCount = hb_buffer_get_length(buffer);
     glyphInfo = hb_buffer_get_glyph_infos(buffer, 0);
     glyphPos = hb_buffer_get_glyph_positions(buffer, &buflen);
     for (i = 0; i < glyphCount; i++) {
         int index = glyphInfo[i].codepoint;
         int xadv = (glyphPos[i].x_advance);
         int yadv = (glyphPos[i].y_advance);
     }
     // On "input" HB assigns a cluster index to each character in UTF-16.
     // On output where a sequence of characters have been mapped to
     // a glyph they are all mapped to the cluster index of the first character.
     // The next cluster index will be that of the first character in the
     // next cluster. So cluster indexes may 'skip' on output.
     // This can also happen if there are supplementary code-points
     // such that two UTF-16 characters are needed to make one codepoint.
     // In RTL text you need to count down.
     // So the following code tries to build the reverse map as expected
     // by calling code.

     storeGVData(env, gvdata, slot, baseIndex, startPt,
                 glyphCount, glyphInfo, glyphPos, direction);

     hb_buffer_destroy (buffer);
     hb_font_destroy(hbfont);
     free((void*)jdkFontInfo);
     if (features != NULL) free(features);

     return JNI_TRUE;
}

