/*
 * Copyright (c) 1997, 1998, Oracle and/or its affiliates. All rights reserved.
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

#include <string.h>

#include "jni.h"
#include "jni_util.h"
#include "awt_parseImage.h"
#include "imageInitIDs.h"
#include "sun_awt_image_ImageRepresentation.h"

static int compareLUTs(unsigned int *lut1, int numLut1, int transIdx,
                       unsigned int *lut2, int numLut2, unsigned char *cvtLut,
                       int *retNumLut1, int *retTransIdx, int *jniFlagP);

static int findIdx(unsigned int rgb, unsigned int *lut, int numLut1);

#define ALPHA_MASK    0xff000000
#ifndef FALSE
#  define FALSE 0
#endif
#ifndef TRUE
#  define TRUE 1
#endif

static jfieldID s_JnumSrcLUTID;
static jfieldID s_JsrcLUTtransIndexID;

JNIEXPORT void JNICALL
Java_sun_awt_image_ImageRepresentation_initIDs(JNIEnv *env, jclass cls) {
    s_JnumSrcLUTID = (*env)->GetFieldID(env, cls, "numSrcLUT", "I");
    s_JsrcLUTtransIndexID = (*env)->GetFieldID(env, cls, "srcLUTtransIndex",
                                               "I");
}

/*
 * This routine is used to draw ICM pixels into a default color model
 */
JNIEXPORT void JNICALL
Java_sun_awt_image_ImageRepresentation_setICMpixels(JNIEnv *env, jclass cls,
                                                    jint x, jint y, jint w,
                                                    jint h, jintArray jlut,
                                                    jbyteArray jpix, jint off,
                                                    jint scansize,
                                                    jobject jict)
{
    unsigned char *srcData = NULL;
    int *dstData;
    int *dstP, *dstyP;
    unsigned char *srcyP, *srcP;
    int *srcLUT = NULL;
    int yIdx, xIdx;
    int sStride;
    int *cOffs;
    int pixelStride;
    jobject joffs = NULL;
    jobject jdata = NULL;

    if (JNU_IsNull(env, jlut)) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        return;
    }

    if (JNU_IsNull(env, jpix)) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        return;
    }

    sStride = (*env)->GetIntField(env, jict, g_ICRscanstrID);
    pixelStride = (*env)->GetIntField(env, jict, g_ICRpixstrID);
    joffs = (*env)->GetObjectField(env, jict, g_ICRdataOffsetsID);
    jdata = (*env)->GetObjectField(env, jict, g_ICRdataID);

    srcLUT = (int *) (*env)->GetPrimitiveArrayCritical(env, jlut, NULL);
    if (srcLUT == NULL) {
        JNU_ThrowNullPointerException(env, "Null IndexColorModel LUT");
        return;
    }

    srcData = (unsigned char *) (*env)->GetPrimitiveArrayCritical(env, jpix,
                                                                  NULL);
    if (srcData == NULL) {
        (*env)->ReleasePrimitiveArrayCritical(env, jlut, srcLUT, JNI_ABORT);
        JNU_ThrowNullPointerException(env, "Null data array");
        return;
    }

    cOffs = (int *) (*env)->GetPrimitiveArrayCritical(env, joffs, NULL);
    if (cOffs == NULL) {
        (*env)->ReleasePrimitiveArrayCritical(env, jlut, srcLUT, JNI_ABORT);
        (*env)->ReleasePrimitiveArrayCritical(env, jpix, srcData, JNI_ABORT);
        JNU_ThrowNullPointerException(env, "Null channel offset array");
        return;
    }

    dstData = (int *) (*env)->GetPrimitiveArrayCritical(env, jdata, NULL);
    if (dstData == NULL) {
        (*env)->ReleasePrimitiveArrayCritical(env, jlut, srcLUT, JNI_ABORT);
        (*env)->ReleasePrimitiveArrayCritical(env, jpix, srcData, JNI_ABORT);
        (*env)->ReleasePrimitiveArrayCritical(env, joffs, cOffs, JNI_ABORT);
        JNU_ThrowNullPointerException(env, "Null tile data array");
        return;
    }

    dstyP = dstData + cOffs[0] + y*sStride + x*pixelStride;
    srcyP = srcData + off;
    for (yIdx = 0; yIdx < h; yIdx++, srcyP += scansize, dstyP+=sStride) {
        srcP = srcyP;
        dstP = dstyP;
        for (xIdx = 0; xIdx < w; xIdx++, dstP+=pixelStride) {
            *dstP = srcLUT[*srcP++];
        }
    }

    /* Release the locked arrays */
    (*env)->ReleasePrimitiveArrayCritical(env, jlut, srcLUT,  JNI_ABORT);
    (*env)->ReleasePrimitiveArrayCritical(env, jpix, srcData, JNI_ABORT);
    (*env)->ReleasePrimitiveArrayCritical(env, joffs, cOffs, JNI_ABORT);
    (*env)->ReleasePrimitiveArrayCritical(env, jdata, dstData, JNI_ABORT);

}

JNIEXPORT jint JNICALL
Java_sun_awt_image_ImageRepresentation_setDiffICM(JNIEnv *env, jclass cls,
                                                  jint x, jint y, jint w,
                                                  jint h, jintArray jlut,
                                                  jint transIdx, jint numLut,
                                                  jobject jicm,
                                                  jbyteArray jpix, jint off,
                                                  jint scansize,
                                                  jobject jbct, jint chanOff)
{
    unsigned int *srcLUT = NULL;
    unsigned int *newLUT = NULL;
    int sStride;
    int pixelStride;
    int mapSize;
    jobject jdata = NULL;
    jobject jnewlut = NULL;
    unsigned char *srcData;
    unsigned char *dstData;
    unsigned char *dataP;
    unsigned char *pixP;
    int i;
    int j;
    int newNumLut;
    int newTransIdx;
    int jniFlag = JNI_ABORT;
    unsigned char *ydataP;
    unsigned char *ypixP;
    unsigned char cvtLut[256];

    if (JNU_IsNull(env, jlut)) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        return 0;
    }

    if (JNU_IsNull(env, jpix)) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        return 0;
    }

    sStride = (*env)->GetIntField(env, jbct, g_BCRscanstrID);
    pixelStride =(*env)->GetIntField(env, jbct, g_BCRpixstrID);
    jdata = (*env)->GetObjectField(env, jbct, g_BCRdataID);
    jnewlut = (*env)->GetObjectField(env, jicm, g_ICMrgbID);
    mapSize = (*env)->GetIntField(env, jicm, g_ICMmapSizeID);

    if (numLut < 0 || numLut > 256 || mapSize < 0 || mapSize > 256) {
        /* Ether old or new ICM has a palette that exceeds capacity
           of byte data type, so we have to convert the image data
           to default representation.
        */
        return 0;
    }
    srcLUT = (unsigned int *) (*env)->GetPrimitiveArrayCritical(env, jlut,
                                                                NULL);
    if (srcLUT == NULL) {
        /* out of memory error already thrown */
        return 0;
    }

    newLUT = (unsigned int *) (*env)->GetPrimitiveArrayCritical(env, jnewlut,
                                                                NULL);
    if (newLUT == NULL) {
        (*env)->ReleasePrimitiveArrayCritical(env, jlut, srcLUT,
                                              JNI_ABORT);
        /* out of memory error already thrown */
        return 0;
    }

    newNumLut = numLut;
    newTransIdx = transIdx;
    if (compareLUTs(srcLUT, numLut, transIdx, newLUT, mapSize,
                    cvtLut, &newNumLut, &newTransIdx, &jniFlag) == FALSE) {
        /* Need to convert to ICR */
        (*env)->ReleasePrimitiveArrayCritical(env, jlut, srcLUT,
                                              JNI_ABORT);
        (*env)->ReleasePrimitiveArrayCritical(env, jnewlut, newLUT, JNI_ABORT);
        return 0;
    }

    /* Don't need these any more */
    (*env)->ReleasePrimitiveArrayCritical(env, jlut, srcLUT, jniFlag);
    (*env)->ReleasePrimitiveArrayCritical(env, jnewlut, newLUT, JNI_ABORT);

    if (newNumLut != numLut) {
        /* Need to write back new number of entries in lut */
        (*env)->SetIntField(env, cls, s_JnumSrcLUTID, newNumLut);
    }

    if (newTransIdx != transIdx) {
        (*env)->SetIntField(env, cls, s_JsrcLUTtransIndexID, newTransIdx);
    }

    srcData = (unsigned char *) (*env)->GetPrimitiveArrayCritical(env, jpix,
                                                                  NULL);
    if (srcData == NULL) {
        /* out of memory error already thrown */
        return 0;
    }

    dstData = (unsigned char *) (*env)->GetPrimitiveArrayCritical(env, jdata,
                                                                  NULL);
    if (dstData == NULL) {
        (*env)->ReleasePrimitiveArrayCritical(env, jpix, srcData, JNI_ABORT);
        /* out of memory error already thrown */
        return 0;
    }

    ydataP = dstData + chanOff + y*sStride + x*pixelStride;
    ypixP  = srcData + off;

    for (i=0; i < h; i++) {
        dataP = ydataP;
        pixP = ypixP;
        for (j=0; j < w; j++) {
            *dataP = cvtLut[*pixP];
            dataP += pixelStride;
            pixP++;
        }
        ydataP += sStride;
        ypixP  += scansize;
    }

    (*env)->ReleasePrimitiveArrayCritical(env, jpix, srcData, JNI_ABORT);
    (*env)->ReleasePrimitiveArrayCritical(env, jdata, dstData, JNI_ABORT);

    return 1;
}

static int compareLUTs(unsigned int *lut1, int numLut1, int transIdx,
                       unsigned int *lut2, int numLut2, unsigned char *cvtLut,
                       int *retNumLut1, int *retTransIdx, int *jniFlagP)
{
    int i;
    int idx;
    int newTransIdx = -1;
    unsigned int rgb;
    int changed = FALSE;
    int maxSize = (numLut1 > numLut2 ? numLut1 : numLut2);

    *jniFlagP = JNI_ABORT;

    for (i=0; i < maxSize; i++) {
        cvtLut[i] = i;
    }

    for (i=0; i < numLut2; i++) {
        /* If this slot in new palette is different from the
         * same slot in current palette, then we try to find
         * this color in other slots. On failure, add this color
         * to current palette.
         */
        if ((i >= numLut1) ||
            (lut1[i] != lut2[i]))
        {
            rgb = lut2[i];
            /* Transparent */
            if ((rgb & ALPHA_MASK) == 0) {
                if (transIdx == -1) {
                    if (numLut1 < 256) {
                        cvtLut[i] = numLut1;
                        newTransIdx = i;
                        transIdx = i;
                        numLut1++;
                        changed = TRUE;
                    }
                    else {
                        return FALSE;
                    }
                }
                cvtLut[i] = transIdx;
            }
            else {
                if ((idx = findIdx(rgb, lut1, numLut1)) == -1) {
                    if (numLut1 < 256) {
                        lut1[numLut1] = rgb;
                        cvtLut[i] = numLut1;
                        numLut1++;
                        changed = TRUE;
                    }
                    else {
                        /* Bad news...  need to convert image */
                        return FALSE;
                    }
                } else {
                    cvtLut[i] = idx;
                }
            }
        }
    }

    if (changed) {
        *jniFlagP = 0;
        *retNumLut1 = numLut1;
        if (newTransIdx != -1) {
            *retTransIdx = newTransIdx;
        }
    }
    return TRUE;
}

static int findIdx(unsigned int rgb, unsigned int *lut, int numLut) {
    int i;

    if ((rgb&0xff000000)==0) {
        for (i=0; i < numLut; i++) {
            if ((lut[i]&0xff000000)==0) return i;
        }
    }
    else {
        for (i=0; i < numLut; i++) {
            if (lut[i] == rgb) return i;
        }
    }
    return -1;
}
