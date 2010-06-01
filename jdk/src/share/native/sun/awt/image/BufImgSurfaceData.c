/*
 * Copyright (c) 1999, 2007, Oracle and/or its affiliates. All rights reserved.
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

#include "BufImgSurfaceData.h"
#include "malloc.h"

#include "sun_awt_image_BufImgSurfaceData.h"

#include "img_util_md.h"
#include "jni_util.h"
/* Define uintptr_t */
#include "gdefs.h"

/**
 * This include file contains support code for loops using the
 * SurfaceData interface to talk to an X11 drawable from native
 * code.
 */

static LockFunc                 BufImg_Lock;
static GetRasInfoFunc           BufImg_GetRasInfo;
static ReleaseFunc              BufImg_Release;
static DisposeFunc              BufImg_Dispose;

static ColorData *BufImg_SetupICM(JNIEnv *env, BufImgSDOps *bisdo);

static jfieldID         rgbID;
static jfieldID         mapSizeID;
static jfieldID         CMpDataID;
static jfieldID         allGrayID;

/*
 * Class:     sun_awt_image_BufImgSurfaceData
 * Method:    initIDs
 * Signature: ()V
 */
JNIEXPORT void JNICALL
Java_sun_awt_image_BufImgSurfaceData_initIDs
    (JNIEnv *env, jclass bisd, jclass icm)
{
    if (sizeof(BufImgRIPrivate) > SD_RASINFO_PRIVATE_SIZE) {
        JNU_ThrowInternalError(env, "Private RasInfo structure too large!");
        return;
    }

    rgbID = (*env)->GetFieldID(env, icm, "rgb", "[I");
    allGrayID = (*env)->GetFieldID(env, icm, "allgrayopaque", "Z");
    mapSizeID = (*env)->GetFieldID(env, icm, "map_size", "I");
    CMpDataID = (*env)->GetFieldID(env, icm, "pData", "J");
    if (allGrayID == 0 || rgbID == 0 || mapSizeID == 0 || CMpDataID == 0) {
        JNU_ThrowInternalError(env, "Could not get field IDs");
    }
}

/*
 * Class:     sun_java2d_SurfaceData
 * Method:    freeNativeICMData
 * Signature: (Ljava/awt/image/IndexColorModel;)V
 */
JNIEXPORT void JNICALL
Java_sun_awt_image_BufImgSurfaceData_freeNativeICMData
    (JNIEnv *env, jclass sd, jobject icm)
{
    jlong pData;
    ColorData *cdata;

    if (JNU_IsNull(env, icm)) {
        JNU_ThrowNullPointerException(env, "IndexColorModel cannot be null");
        return;
    }

    pData = (*env)->GetLongField (env, icm, CMpDataID);
    cdata = (ColorData *)pData;
    freeICMColorData(cdata);
}

/*
 * Class:     sun_awt_image_BufImgSurfaceData
 * Method:    initOps
 * Signature: (Ljava/lang/Object;IIIII)V
 */
JNIEXPORT void JNICALL
Java_sun_awt_image_BufImgSurfaceData_initRaster(JNIEnv *env, jobject bisd,
                                                jobject array,
                                                jint offset, jint bitoffset,
                                                jint width, jint height,
                                                jint pixStr, jint scanStr,
                                                jobject icm)
{
    BufImgSDOps *bisdo =
        (BufImgSDOps*)SurfaceData_InitOps(env, bisd, sizeof(BufImgSDOps));
    bisdo->sdOps.Lock = BufImg_Lock;
    bisdo->sdOps.GetRasInfo = BufImg_GetRasInfo;
    bisdo->sdOps.Release = BufImg_Release;
    bisdo->sdOps.Unlock = NULL;
    bisdo->sdOps.Dispose = BufImg_Dispose;
    bisdo->array = (*env)->NewWeakGlobalRef(env, array);
    bisdo->offset = offset;
    bisdo->bitoffset = bitoffset;
    bisdo->scanStr = scanStr;
    bisdo->pixStr = pixStr;
    if (JNU_IsNull(env, icm)) {
        bisdo->lutarray = NULL;
        bisdo->lutsize = 0;
        bisdo->icm = NULL;
    } else {
        jobject lutarray = (*env)->GetObjectField(env, icm, rgbID);
        bisdo->lutarray = (*env)->NewWeakGlobalRef(env, lutarray);
        bisdo->lutsize = (*env)->GetIntField(env, icm, mapSizeID);
        bisdo->icm = (*env)->NewWeakGlobalRef(env, icm);
    }
    bisdo->rasbounds.x1 = 0;
    bisdo->rasbounds.y1 = 0;
    bisdo->rasbounds.x2 = width;
    bisdo->rasbounds.y2 = height;
}

/*
 * Method for disposing native BufImgSD
 */
static void BufImg_Dispose(JNIEnv *env, SurfaceDataOps *ops)
{
    /* ops is assumed non-null as it is checked in SurfaceData_DisposeOps */
    BufImgSDOps *bisdo = (BufImgSDOps *)ops;
    (*env)->DeleteWeakGlobalRef(env, bisdo->array);
    if (bisdo->lutarray != NULL) {
        (*env)->DeleteWeakGlobalRef(env, bisdo->lutarray);
    }
    if (bisdo->icm != NULL) {
        (*env)->DeleteWeakGlobalRef(env, bisdo->icm);
    }
}

static jint BufImg_Lock(JNIEnv *env,
                        SurfaceDataOps *ops,
                        SurfaceDataRasInfo *pRasInfo,
                        jint lockflags)
{
    BufImgSDOps *bisdo = (BufImgSDOps *)ops;
    BufImgRIPrivate *bipriv = (BufImgRIPrivate *) &(pRasInfo->priv);

    if ((lockflags & (SD_LOCK_LUT)) != 0 && JNU_IsNull(env, bisdo->lutarray)) {
        /* REMIND: Should this be an InvalidPipe exception? */
        JNU_ThrowNullPointerException(env, "Attempt to lock missing colormap");
        return SD_FAILURE;
    }
    if ((lockflags & SD_LOCK_INVCOLOR) != 0 ||
        (lockflags & SD_LOCK_INVGRAY) != 0)
    {
        bipriv->cData = BufImg_SetupICM(env, bisdo);
        if (bipriv->cData == NULL) {
            JNU_ThrowNullPointerException(env, "Could not initialize "
                                          "inverse tables");
            return SD_FAILURE;
        }
    } else {
        bipriv->cData = NULL;
    }

    bipriv->lockFlags = lockflags;
    bipriv->base = NULL;
    bipriv->lutbase = NULL;

    SurfaceData_IntersectBounds(&pRasInfo->bounds, &bisdo->rasbounds);

    return SD_SUCCESS;
}

static void BufImg_GetRasInfo(JNIEnv *env,
                              SurfaceDataOps *ops,
                              SurfaceDataRasInfo *pRasInfo)
{
    BufImgSDOps *bisdo = (BufImgSDOps *)ops;
    BufImgRIPrivate *bipriv = (BufImgRIPrivate *) &(pRasInfo->priv);

    if ((bipriv->lockFlags & (SD_LOCK_RD_WR)) != 0) {
        bipriv->base =
            (*env)->GetPrimitiveArrayCritical(env, bisdo->array, NULL);
    }
    if ((bipriv->lockFlags & (SD_LOCK_LUT)) != 0) {
        bipriv->lutbase =
            (*env)->GetPrimitiveArrayCritical(env, bisdo->lutarray, NULL);
    }

    if (bipriv->base == NULL) {
        pRasInfo->rasBase = NULL;
        pRasInfo->pixelStride = 0;
        pRasInfo->pixelBitOffset = 0;
        pRasInfo->scanStride = 0;
    } else {
        pRasInfo->rasBase = (void *)
            (((uintptr_t) bipriv->base) + bisdo->offset);
        pRasInfo->pixelStride = bisdo->pixStr;
        pRasInfo->pixelBitOffset = bisdo->bitoffset;
        pRasInfo->scanStride = bisdo->scanStr;
    }
    if (bipriv->lutbase == NULL) {
        pRasInfo->lutBase = NULL;
        pRasInfo->lutSize = 0;
    } else {
        pRasInfo->lutBase = bipriv->lutbase;
        pRasInfo->lutSize = bisdo->lutsize;
    }
    if (bipriv->cData == NULL) {
        pRasInfo->invColorTable = NULL;
        pRasInfo->redErrTable = NULL;
        pRasInfo->grnErrTable = NULL;
        pRasInfo->bluErrTable = NULL;
    } else {
        pRasInfo->invColorTable = bipriv->cData->img_clr_tbl;
        pRasInfo->redErrTable = bipriv->cData->img_oda_red;
        pRasInfo->grnErrTable = bipriv->cData->img_oda_green;
        pRasInfo->bluErrTable = bipriv->cData->img_oda_blue;
        pRasInfo->invGrayTable = bipriv->cData->pGrayInverseLutData;
    }
}

static void BufImg_Release(JNIEnv *env,
                           SurfaceDataOps *ops,
                           SurfaceDataRasInfo *pRasInfo)
{
    BufImgSDOps *bisdo = (BufImgSDOps *)ops;
    BufImgRIPrivate *bipriv = (BufImgRIPrivate *) &(pRasInfo->priv);

    if (bipriv->base != NULL) {
        jint mode = (((bipriv->lockFlags & (SD_LOCK_WRITE)) != 0)
                     ? 0 : JNI_ABORT);
        (*env)->ReleasePrimitiveArrayCritical(env, bisdo->array,
                                              bipriv->base, mode);
    }
    if (bipriv->lutbase != NULL) {
        (*env)->ReleasePrimitiveArrayCritical(env, bisdo->lutarray,
                                              bipriv->lutbase, JNI_ABORT);
    }
}

static ColorData *BufImg_SetupICM(JNIEnv *env,
                                  BufImgSDOps *bisdo)
{
    ColorData *cData;

    if (JNU_IsNull(env, bisdo->icm)) {
        return (ColorData *) NULL;
    }

    cData = (ColorData *) JNU_GetLongFieldAsPtr(env, bisdo->icm, CMpDataID);

    if (cData == NULL) {
        cData = (ColorData*)calloc(1, sizeof(ColorData));

        if (cData != NULL) {
            jboolean allGray
                = (*env)->GetBooleanField(env, bisdo->icm, allGrayID);
            int *pRgb = (int *)
                ((*env)->GetPrimitiveArrayCritical(env, bisdo->lutarray, NULL));
            cData->img_clr_tbl = initCubemap(pRgb, bisdo->lutsize, 32);
            if (allGray == JNI_TRUE) {
                initInverseGrayLut(pRgb, bisdo->lutsize, cData);
            }
            (*env)->ReleasePrimitiveArrayCritical(env, bisdo->lutarray, pRgb,
                                                  JNI_ABORT);

            initDitherTables(cData);

            JNU_SetLongFieldFromPtr(env, bisdo->icm, CMpDataID, cData);
        }
    }

    return cData;
}
