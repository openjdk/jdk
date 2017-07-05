/*
 * Copyright (c) 1997, 2006, Oracle and/or its affiliates. All rights reserved.
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

#include <stdio.h>
#include <stdlib.h>
#include "awt_parseImage.h"
#include "imageInitIDs.h"
#include "java_awt_Transparency.h"
#include "java_awt_image_BufferedImage.h"
#include "sun_awt_image_IntegerComponentRaster.h"
#include "sun_awt_image_ImagingLib.h"
#include "java_awt_color_ColorSpace.h"
#include "awt_Mlib.h"
#include "safe_alloc.h"

static int setHints(JNIEnv *env, BufImageS_t *imageP);



/* Parse the buffered image.  All of the raster information is returned in the
 * imagePP structure.
 *
 * The handleCustom parameter specifies whether or not the caller
 * can use custom channels.  If it is false and a custom channel
 * is encountered, the returned value will be 0 and all structures
 * will be deallocated.
 *
 * Return value:
 *    -1:     Exception
 *     0:     Can't do it.
 *     1:     Success
 */
int awt_parseImage(JNIEnv *env, jobject jimage, BufImageS_t **imagePP,
                   int handleCustom) {
    BufImageS_t *imageP;
    int status;
    jobject jraster;
    jobject jcmodel;

    /* Make sure the image exists */
    if (JNU_IsNull(env, jimage)) {
        JNU_ThrowNullPointerException(env, "null BufferedImage object");
        return -1;
    }

    if ((imageP = (BufImageS_t *) calloc(1, sizeof(BufImageS_t))) == NULL) {
        JNU_ThrowOutOfMemoryError(env, "Out of memory");
        return -1;
    }
    imageP->jimage = jimage;

    /* Retrieve the raster */
    if ((jraster = (*env)->GetObjectField(env, jimage,
                                          g_BImgRasterID)) == NULL) {
        free((void *) imageP);
        JNU_ThrowNullPointerException(env, "null Raster object");
        return 0;
    }

    /* Retrieve the image type */
    imageP->imageType = (*env)->GetIntField(env, jimage, g_BImgTypeID);

    /* Parse the raster */
    if ((status = awt_parseRaster(env, jraster, &imageP->raster)) <= 0) {
        free((void *)imageP);
        return status;
    }

    /* Retrieve the color model */
    if ((jcmodel = (*env)->GetObjectField(env, jimage, g_BImgCMID)) == NULL) {
        free((void *) imageP);
        JNU_ThrowNullPointerException(env, "null Raster object");
        return 0;
    }

    /* Parse the color model */
    if ((status = awt_parseColorModel(env, jcmodel, imageP->imageType,
                                      &imageP->cmodel)) <= 0) {
        awt_freeParsedRaster(&imageP->raster, FALSE);
        free((void *)imageP);
        return 0;
    }

    /* Set hints  */
    if ((status = setHints(env, imageP)) <= 0) {
        awt_freeParsedImage(imageP, TRUE);
        return 0;
    }

    *imagePP = imageP;

    return status;
}

/* Parse the raster.  All of the raster information is returned in the
 * rasterP structure.
 *
 * Return value:
 *    -1:     Exception
 *     0:     Can't do it (Custom channel)
 *     1:     Success
 */
int awt_parseRaster(JNIEnv *env, jobject jraster, RasterS_t *rasterP) {
    jobject joffs = NULL;
    /* int status;*/
    int isDiscrete = TRUE;

    if (JNU_IsNull(env, jraster)) {
        JNU_ThrowNullPointerException(env, "null Raster object");
        return -1;
    }

    rasterP->jraster = jraster;
    rasterP->width   = (*env)->GetIntField(env, jraster, g_RasterWidthID);
    rasterP->height  = (*env)->GetIntField(env, jraster, g_RasterHeightID);
    rasterP->numDataElements = (*env)->GetIntField(env, jraster,
                                                   g_RasterNumDataElementsID);
    rasterP->numBands = (*env)->GetIntField(env, jraster,
                                            g_RasterNumBandsID);

    rasterP->baseOriginX = (*env)->GetIntField(env, jraster,
                                               g_RasterBaseOriginXID);
    rasterP->baseOriginY = (*env)->GetIntField(env, jraster,
                                               g_RasterBaseOriginYID);
    rasterP->minX = (*env)->GetIntField(env, jraster, g_RasterMinXID);
    rasterP->minY = (*env)->GetIntField(env, jraster, g_RasterMinYID);

    rasterP->jsampleModel = (*env)->GetObjectField(env, jraster,
                                                   g_RasterSampleModelID);

    if (JNU_IsNull(env, rasterP->jsampleModel)) {
        JNU_ThrowNullPointerException(env, "null Raster object");
        return -1;
    }

    if (rasterP->numBands <= 0 ||
        rasterP->numBands > MAX_NUMBANDS)
    {
        /*
         * we can't handle such kind of rasters due to limitations
         * of SPPSampleModelS_t structure and expand/set methods.
         */
        return 0;
    }

    if ((*env)->IsInstanceOf(env, rasterP->jsampleModel,
       (*env)->FindClass(env,"java/awt/image/SinglePixelPackedSampleModel"))) {
        jobject jmask, joffs, jnbits;
        rasterP->sppsm.maxBitSize = (*env)->GetIntField(env,
                                                        rasterP->jsampleModel,
                                                        g_SPPSMmaxBitID);
        jmask = (*env)->GetObjectField(env, rasterP->jsampleModel,
                                       g_SPPSMmaskArrID);
        joffs = (*env)->GetObjectField(env, rasterP->jsampleModel,
                                       g_SPPSMmaskOffID);
        jnbits = (*env)->GetObjectField(env, rasterP->jsampleModel,
                                        g_SPPSMnBitsID);
        if (jmask == NULL || joffs == NULL || jnbits == NULL ||
            rasterP->sppsm.maxBitSize < 0 || rasterP->sppsm.maxBitSize > 8)
        {
            JNU_ThrowInternalError(env, "Can't grab SPPSM fields");
            return -1;
        }
        (*env)->GetIntArrayRegion(env, jmask, 0,
                                  rasterP->numBands, rasterP->sppsm.maskArray);
        (*env)->GetIntArrayRegion(env, joffs, 0,
                                  rasterP->numBands, rasterP->sppsm.offsets);
        (*env)->GetIntArrayRegion(env, jnbits, 0,
                                  rasterP->numBands, rasterP->sppsm.nBits);

    }
    rasterP->baseRasterWidth = (*env)->GetIntField(env, rasterP->jsampleModel,
                                                   g_SMWidthID);
    rasterP->baseRasterHeight = (*env)->GetIntField(env,
                                                    rasterP->jsampleModel,
                                                    g_SMHeightID);

    if ((*env)->IsInstanceOf(env, jraster,
         (*env)->FindClass(env, "sun/awt/image/IntegerComponentRaster"))){
        rasterP->jdata = (*env)->GetObjectField(env, jraster, g_ICRdataID);
        rasterP->dataType = INT_DATA_TYPE;
        rasterP->dataSize = 4;
        rasterP->dataIsShared = TRUE;
        rasterP->rasterType = COMPONENT_RASTER_TYPE;
        rasterP->type = (*env)->GetIntField(env, jraster, g_ICRtypeID);
        rasterP->scanlineStride = (*env)->GetIntField(env, jraster, g_ICRscanstrID);
        rasterP->pixelStride = (*env)->GetIntField(env, jraster, g_ICRpixstrID);
        joffs = (*env)->GetObjectField(env, jraster, g_ICRdataOffsetsID);
    }
    else if ((*env)->IsInstanceOf(env, jraster,
            (*env)->FindClass(env, "sun/awt/image/ByteComponentRaster"))){
        rasterP->jdata = (*env)->GetObjectField(env, jraster, g_BCRdataID);
        rasterP->dataType = BYTE_DATA_TYPE;
        rasterP->dataSize = 1;
        rasterP->dataIsShared = TRUE;
        rasterP->rasterType = COMPONENT_RASTER_TYPE;
        rasterP->type = (*env)->GetIntField(env, jraster, g_BCRtypeID);
        rasterP->scanlineStride = (*env)->GetIntField(env, jraster, g_BCRscanstrID);
        rasterP->pixelStride = (*env)->GetIntField(env, jraster, g_BCRpixstrID);
        joffs = (*env)->GetObjectField(env, jraster, g_BCRdataOffsetsID);
    }
    else if ((*env)->IsInstanceOf(env, jraster,
            (*env)->FindClass(env, "sun/awt/image/ShortComponentRaster"))){
        rasterP->jdata = (*env)->GetObjectField(env, jraster, g_SCRdataID);
        rasterP->dataType = SHORT_DATA_TYPE;
        rasterP->dataSize = 2;
        rasterP->dataIsShared = TRUE;
        rasterP->rasterType = COMPONENT_RASTER_TYPE;
        rasterP->type = (*env)->GetIntField(env, jraster, g_SCRtypeID);
        rasterP->scanlineStride = (*env)->GetIntField(env, jraster, g_SCRscanstrID);
        rasterP->pixelStride = (*env)->GetIntField(env, jraster, g_SCRpixstrID);
        joffs = (*env)->GetObjectField(env, jraster, g_SCRdataOffsetsID);
    }
    else if ((*env)->IsInstanceOf(env, jraster,
            (*env)->FindClass(env, "sun/awt/image/BytePackedRaster"))){
        rasterP->rasterType = PACKED_RASTER_TYPE;
        rasterP->dataType = BYTE_DATA_TYPE;
        rasterP->dataSize = 1;
        rasterP->scanlineStride = (*env)->GetIntField(env, jraster, g_BPRscanstrID);
        rasterP->pixelStride = (*env)->GetIntField(env, jraster, g_BPRpixstrID);
        rasterP->jdata = (*env)->GetObjectField(env, jraster, g_BPRdataID);
        rasterP->type = (*env)->GetIntField(env, jraster, g_BPRtypeID);
        rasterP->chanOffsets = NULL;
        if (SAFE_TO_ALLOC_2(rasterP->numDataElements, sizeof(jint))) {
            rasterP->chanOffsets =
                (jint *)malloc(rasterP->numDataElements * sizeof(jint));
        }
        if (rasterP->chanOffsets == NULL) {
            /* Out of memory */
            JNU_ThrowOutOfMemoryError(env, "Out of memory");
            return -1;
        }
        rasterP->chanOffsets[0] = (*env)->GetIntField(env, jraster, g_BPRdataBitOffsetID);
        rasterP->dataType = BYTE_DATA_TYPE;
        isDiscrete = FALSE;
    }
    else {
        rasterP->type = sun_awt_image_IntegerComponentRaster_TYPE_CUSTOM;
        rasterP->dataType = UNKNOWN_DATA_TYPE;
        rasterP->rasterType = UNKNOWN_RASTER_TYPE;
        rasterP->chanOffsets = NULL;
        /* Custom raster */
        return 0;
    }

    if (isDiscrete) {
        rasterP->chanOffsets = NULL;
        if (SAFE_TO_ALLOC_2(rasterP->numDataElements, sizeof(jint))) {
            rasterP->chanOffsets =
                (jint *)malloc(rasterP->numDataElements * sizeof(jint));
        }
        if (rasterP->chanOffsets == NULL) {
            /* Out of memory */
            JNU_ThrowOutOfMemoryError(env, "Out of memory");
            return -1;
        }
        (*env)->GetIntArrayRegion(env, joffs, 0, rasterP->numDataElements,
                                  rasterP->chanOffsets);
    }

#if 0
    fprintf(stderr,"---------------------\n");
    fprintf(stderr,"Width  : %d\n",rasterP->width);
    fprintf(stderr,"Height : %d\n",rasterP->height);
    fprintf(stderr,"X      : %d\n",rasterP->x);
    fprintf(stderr,"Y      : %d\n",rasterP->y);
    fprintf(stderr,"numC   : %d\n",rasterP->numDataElements);
    fprintf(stderr,"SS     : %d\n",rasterP->scanlineStride);
    fprintf(stderr,"PS     : %d\n",rasterP->pixelStride);
    fprintf(stderr,"CO     : %d\n",rasterP->chanOffsets);
    fprintf(stderr,"shared?: %d\n",rasterP->dataIsShared);
    fprintf(stderr,"RasterT: %d\n",rasterP->rasterType);
    fprintf(stderr,"DataT  : %d\n",rasterP->dataType);
    fprintf(stderr,"---------------------\n");
#endif

    return 1;
}

int awt_parseColorModel (JNIEnv *env, jobject jcmodel, int imageType,
                         ColorModelS_t *cmP) {
    /*jmethodID jID;   */
    jobject jnBits;
    int i;
    static jobject s_jdefCM = NULL;

    if (JNU_IsNull(env, jcmodel)) {
        JNU_ThrowNullPointerException(env, "null ColorModel object");
        return -1;
    }

    cmP->jcmodel = jcmodel;

    cmP->jcspace = (*env)->GetObjectField(env, jcmodel, g_CMcspaceID);

    cmP->numComponents = (*env)->GetIntField(env, jcmodel,
                                             g_CMnumComponentsID);
    cmP->supportsAlpha = (*env)->GetBooleanField(env, jcmodel,
                                                 g_CMsuppAlphaID);
    cmP->isAlphaPre = (*env)->GetBooleanField(env,jcmodel,
                                              g_CMisAlphaPreID);
    cmP->transparency = (*env)->GetIntField(env, jcmodel,
                                            g_CMtransparencyID);

    if (imageType == java_awt_image_BufferedImage_TYPE_INT_ARGB) {
        cmP->isDefaultCM = TRUE;
        cmP->isDefaultCompatCM = TRUE;
    } else if (imageType == java_awt_image_BufferedImage_TYPE_INT_ARGB_PRE ||
             imageType == java_awt_image_BufferedImage_TYPE_INT_RGB) {
        cmP->isDefaultCompatCM = TRUE;
    } else if (imageType == java_awt_image_BufferedImage_TYPE_INT_BGR ||
               imageType == java_awt_image_BufferedImage_TYPE_4BYTE_ABGR ||
               imageType == java_awt_image_BufferedImage_TYPE_4BYTE_ABGR_PRE){
        cmP->isDefaultCompatCM = TRUE;
    }
    else {
        /* Figure out if this is the default CM */
        if (s_jdefCM == NULL) {
            jobject defCM;
            jclass jcm = (*env)->FindClass(env, "java/awt/image/ColorModel");
            defCM = (*env)->CallStaticObjectMethod(env, jcm,
                                                   g_CMgetRGBdefaultMID, NULL);
            s_jdefCM = (*env)->NewGlobalRef(env, defCM);
            if (defCM == NULL || s_jdefCM == NULL) {
                JNU_ThrowNullPointerException(env,
                                              "Unable to find default CM");
                return -1;
            }
        }
        cmP->isDefaultCM = ((*env)->IsSameObject(env, s_jdefCM, jcmodel));
        cmP->isDefaultCompatCM = cmP->isDefaultCM;
    }

    if (cmP->isDefaultCompatCM) {
        cmP->cmType = DIRECT_CM_TYPE;
        cmP->nBits = (jint *) malloc(sizeof(jint)*4);
        cmP->nBits[0] = cmP->nBits[1] = cmP->nBits[2] = cmP->nBits[3] = 8;
        cmP->maxNbits = 8;
        cmP->is_sRGB = TRUE;
        cmP->csType  = java_awt_color_ColorSpace_TYPE_RGB;

        return 1;
    }

    jnBits = (*env)->GetObjectField(env, jcmodel, g_CMnBitsID);
    if (jnBits == NULL) {
        JNU_ThrowNullPointerException(env, "null nBits structure in CModel");
        return -1;
    }

    cmP->nBits = NULL;
    if (SAFE_TO_ALLOC_2(cmP->numComponents, sizeof(jint))) {
        cmP->nBits = (jint *)malloc(cmP->numComponents * sizeof(jint));
    }
    if (cmP->nBits == NULL){
        JNU_ThrowOutOfMemoryError(env, "Out of memory");
        return -1;
    }
    (*env)->GetIntArrayRegion(env, jnBits, 0, cmP->numComponents,
                              cmP->nBits);
    cmP->maxNbits = 0;
    for (i=0; i < cmP->numComponents; i++) {
        if (cmP->maxNbits < cmP->nBits[i]) {
            cmP->maxNbits = cmP->nBits[i];
        }
    }

    cmP->is_sRGB = (*env)->GetBooleanField(env, cmP->jcmodel, g_CMis_sRGBID);

    cmP->csType = (*env)->GetIntField(env, cmP->jcmodel, g_CMcsTypeID);

    /* Find out what type of colol model */
    if (imageType == java_awt_image_BufferedImage_TYPE_BYTE_INDEXED ||
        (*env)->IsInstanceOf(env, jcmodel,
                 (*env)->FindClass(env, "java/awt/image/IndexColorModel")))
    {
        cmP->cmType = INDEX_CM_TYPE;
        cmP->transIdx = (*env)->GetIntField(env, jcmodel, g_ICMtransIdxID);
        cmP->mapSize = (*env)->GetIntField(env, jcmodel, g_ICMmapSizeID);
        cmP->jrgb    = (*env)->GetObjectField(env, jcmodel, g_ICMrgbID);
        if (cmP->transIdx == -1) {
            /* Need to find the transparent index */
            int *rgb = (int *) (*env)->GetPrimitiveArrayCritical(env,
                                                                 cmP->jrgb,
                                                                 NULL);
            if (rgb == NULL) {
                return -1;
            }
            for (i=0; i < cmP->mapSize; i++) {
                if ((rgb[i]&0xff000000) == 0) {
                    cmP->transIdx = i;
                    break;
                }
            }
            (*env)->ReleasePrimitiveArrayCritical(env, cmP->jrgb, rgb,
                                                  JNI_ABORT);
            if (cmP->transIdx == -1) {
                /* Now what? No transparent pixel... */
                cmP->transIdx = 0;
            }
        }
    }
    else if ((*env)->IsInstanceOf(env, jcmodel,
                 (*env)->FindClass(env, "java/awt/image/PackedColorModel")))
    {
        if  ((*env)->IsInstanceOf(env, jcmodel,
                (*env)->FindClass(env, "java/awt/image/DirectColorModel"))){
            cmP->cmType = DIRECT_CM_TYPE;
        }
        else {
            cmP->cmType = PACKED_CM_TYPE;
        }
    }
    else if ((*env)->IsInstanceOf(env, jcmodel,
                 (*env)->FindClass(env, "java/awt/image/ComponentColorModel")))
    {
        cmP->cmType = COMPONENT_CM_TYPE;
    }
    else if ((*env)->IsInstanceOf(env, jcmodel,
              (*env)->FindClass(env, "java/awt/image/PackedColorModel")))
    {
        cmP->cmType = PACKED_CM_TYPE;
    }
    else {
        cmP->cmType = UNKNOWN_CM_TYPE;
    }


    return 1;
}

void awt_freeParsedRaster(RasterS_t *rasterP, int freeRasterP) {
    if (rasterP->chanOffsets) {
        free((void *) rasterP->chanOffsets);
    }

    if (freeRasterP) {
        free((void *) rasterP);
    }
}

void awt_freeParsedImage(BufImageS_t *imageP, int freeImageP) {
    if (imageP->hints.colorOrder) {
        free ((void *) imageP->hints.colorOrder);
    }

    if (imageP->cmodel.nBits) {
        free ((void *) imageP->cmodel.nBits);
    }

    /* Free the raster */
    awt_freeParsedRaster(&imageP->raster, FALSE);

    if (freeImageP) {
        free((void *) imageP);
    }
}


static int
setHints(JNIEnv *env, BufImageS_t *imageP) {
    HintS_t *hintP = &imageP->hints;
    RasterS_t *rasterP = &imageP->raster;
    ColorModelS_t *cmodelP = &imageP->cmodel;
    int imageType = imageP->imageType;

    hintP->numChans = imageP->cmodel.numComponents;
    hintP->colorOrder = NULL;
    if (SAFE_TO_ALLOC_2(hintP->numChans, sizeof(int))) {
        hintP->colorOrder = (int *)malloc(hintP->numChans * sizeof(int));
    }
    if (hintP->colorOrder == NULL) {
        JNU_ThrowOutOfMemoryError(env, "Out of memory");
        return -1;
    }
    if (imageType != java_awt_image_BufferedImage_TYPE_CUSTOM) {
        awt_getBIColorOrder(imageType, hintP->colorOrder);
    }
    if (imageType == java_awt_image_BufferedImage_TYPE_INT_ARGB ||
        imageType == java_awt_image_BufferedImage_TYPE_INT_ARGB_PRE ||
        imageType == java_awt_image_BufferedImage_TYPE_INT_RGB)
    {
        hintP->channelOffset = rasterP->chanOffsets[0];
        /* These hints are #bytes  */
        hintP->dataOffset    = hintP->channelOffset*rasterP->dataSize;
        hintP->sStride = rasterP->scanlineStride*rasterP->dataSize;
        hintP->pStride = rasterP->pixelStride*rasterP->dataSize;
        hintP->packing = BYTE_INTERLEAVED;
    } else if (imageType ==java_awt_image_BufferedImage_TYPE_4BYTE_ABGR ||
               imageType==java_awt_image_BufferedImage_TYPE_4BYTE_ABGR_PRE||
               imageType == java_awt_image_BufferedImage_TYPE_3BYTE_BGR ||
               imageType == java_awt_image_BufferedImage_TYPE_INT_BGR)
    {
        if (imageType == java_awt_image_BufferedImage_TYPE_INT_BGR) {
            hintP->channelOffset = rasterP->chanOffsets[0];
        }
        else {
            hintP->channelOffset = rasterP->chanOffsets[hintP->numChans-1];
        }
        hintP->dataOffset    = hintP->channelOffset*rasterP->dataSize;
        hintP->sStride = rasterP->scanlineStride*rasterP->dataSize;
        hintP->pStride = rasterP->pixelStride*rasterP->dataSize;
        hintP->packing = BYTE_INTERLEAVED;
    } else if (imageType==java_awt_image_BufferedImage_TYPE_USHORT_565_RGB ||
               imageType==java_awt_image_BufferedImage_TYPE_USHORT_555_RGB) {
        hintP->needToExpand  = TRUE;
        hintP->expandToNbits = 8;
        hintP->packing = PACKED_SHORT_INTER;
    } else if (cmodelP->cmType == INDEX_CM_TYPE) {
        int i;
        hintP->numChans = 1;
        hintP->channelOffset = rasterP->chanOffsets[0];
        hintP->dataOffset    = hintP->channelOffset*rasterP->dataSize;
        hintP->sStride = rasterP->scanlineStride*rasterP->dataSize;
        hintP->pStride = rasterP->pixelStride*rasterP->dataSize;
        switch(rasterP->dataType ) {
        case BYTE_DATA_TYPE:
            if (rasterP->rasterType == PACKED_RASTER_TYPE) {
                hintP->needToExpand = TRUE;
                hintP->expandToNbits = 8;
                hintP->packing = BYTE_PACKED_BAND;
            }
            else {
                hintP->packing = BYTE_SINGLE_BAND;
            }
            break;
        case SHORT_DATA_TYPE:
            hintP->packing = SHORT_SINGLE_BAND;
            break;
        case INT_DATA_TYPE:
        default:
            hintP->packing = UNKNOWN_PACKING;
            break;
        }
        for (i=0; i < hintP->numChans; i++) {
            hintP->colorOrder[i] = i;
        }
    }
    else if (cmodelP->cmType == COMPONENT_CM_TYPE) {
        /* Figure out if it is interleaved */
        int bits=1;
        int i;
        int low = rasterP->chanOffsets[0];
        int diff;
        int banded = 0;
        for (i=1; i < hintP->numChans; i++) {
            if (rasterP->chanOffsets[i] < low) {
                low = rasterP->chanOffsets[i];
            }
        }
        for (i=1; i < hintP->numChans; i++) {
            diff = rasterP->chanOffsets[i]-low;
            if (diff < hintP->numChans) {
                if (bits & (1<<diff)) {
                    /* Overlapping samples */
                    /* Could just copy */
                    return -1;
                }
                bits |= (1<<diff);
            }
            else if (diff >= rasterP->width) {
                banded = 1;
            }
            /* Ignore the case if bands are overlapping */
        }
        hintP->channelOffset = low;
        hintP->dataOffset    = low*rasterP->dataSize;
        hintP->sStride       = rasterP->scanlineStride*rasterP->dataSize;
        hintP->pStride       = rasterP->pixelStride*rasterP->dataSize;
        switch(rasterP->dataType) {
        case BYTE_DATA_TYPE:
            hintP->packing = BYTE_COMPONENTS;
            break;
        case SHORT_DATA_TYPE:
            hintP->packing = SHORT_COMPONENTS;
            break;
        default:
            /* Don't handle any other case */
            return -1;
        }
        if (bits == ((1<<hintP->numChans)-1)) {
            hintP->packing |= INTERLEAVED;
            for (i=0; i < hintP->numChans; i++) {
                hintP->colorOrder[rasterP->chanOffsets[i]-low] = i;
            }
        }
        else if (banded == 1) {
            int bandSize = rasterP->width*rasterP->height;
            hintP->packing |= BANDED;
            for (i=0; i < hintP->numChans; i++) {
                /* REMIND: Not necessarily correct */
                hintP->colorOrder[(rasterP->chanOffsets[i]-low)%bandSize] = i;
            }
        }
        else {
            return -1;
        }
    }
    else if (cmodelP->cmType == DIRECT_CM_TYPE || cmodelP->cmType == PACKED_CM_TYPE) {
        int i;
        if (cmodelP->maxNbits > 8) {
            hintP->needToExpand = TRUE;
            hintP->expandToNbits = cmodelP->maxNbits;
        }
        else if (rasterP->sppsm.offsets != NULL) {
            for (i=0; i < rasterP->numBands; i++) {
                if (!(rasterP->sppsm.offsets[i] % 8)) {
                    hintP->needToExpand  = TRUE;
                    hintP->expandToNbits = 8;
                    break;
                }
                else {
                    hintP->colorOrder[i] = rasterP->sppsm.offsets[i]>>3;
                }
            }
        }

        hintP->channelOffset = rasterP->chanOffsets[0];
        hintP->dataOffset    = hintP->channelOffset*rasterP->dataSize;
        hintP->sStride = rasterP->scanlineStride*rasterP->dataSize;
        hintP->pStride = rasterP->pixelStride*rasterP->dataSize;
        if (hintP->needToExpand) {
            switch(rasterP->dataType) {
            case BYTE_DATA_TYPE:
                hintP->packing = PACKED_BYTE_INTER;
                break;
            case SHORT_DATA_TYPE:
                hintP->packing = PACKED_SHORT_INTER;
                break;
            case INT_DATA_TYPE:
                hintP->packing = PACKED_INT_INTER;
                break;
            default:
                /* Don't know what it is */
                return -1;
            }
        }
        else {
            hintP->packing = BYTE_INTERLEAVED;

        }
    }
    else {
        /* REMIND: Need to handle more cases */
        return -1;
    }

    return 1;
}

/*
 * This routine will fill in a buffer of data for either 1 band or all
 * bands (if band == -1).
 */
#define MAX_TO_GRAB (10240)

int awt_getPixelByte(JNIEnv *env, int band, RasterS_t *rasterP,
                     unsigned char *bufferP) {
    int w = rasterP->width;
    int h = rasterP->height;
    int numBands = rasterP->numBands;
    int y;
    int i;
    int maxLines = (h < MAX_TO_GRAB/w ? h : MAX_TO_GRAB/w);
    jobject jsm;
    int off;
    jarray jdata = NULL;
    jobject jdatabuffer;
    int *dataP;
    int maxBytes = w;

    jsm = (*env)->GetObjectField(env, rasterP->jraster, g_RasterSampleModelID);
    jdatabuffer = (*env)->GetObjectField(env, rasterP->jraster,
                                         g_RasterDataBufferID);
    jdata = (*env)->NewIntArray(env, maxBytes*rasterP->numBands*maxLines);
    if (JNU_IsNull(env, jdata)) {
        JNU_ThrowOutOfMemoryError(env, "Out of Memory");
        return -1;
    }

    /* Here is the generic code */
    if (band >= 0) {
        int dOff;
        if (band >= numBands) {
            (*env)->DeleteLocalRef(env, jdata);
            JNU_ThrowInternalError(env, "Band out of range.");
            return -1;
        }
        off = 0;
        for (y=0; y < h; ) {
            (*env)->CallObjectMethod(env, jsm, g_SMGetPixelsMID,
                                     0, y, w,
                                     maxLines, jdata, jdatabuffer);
            dataP = (int *) (*env)->GetPrimitiveArrayCritical(env, jdata,
                                                              NULL);
            if (dataP == NULL) {
                (*env)->DeleteLocalRef(env, jdata);
                return -1;
            }
            dOff = band;
            for (i=0; i < maxBytes; i++, dOff += numBands) {
                bufferP[off++] = (unsigned char) dataP[dOff];
            }

            (*env)->ReleasePrimitiveArrayCritical(env, jdata, dataP,
                                                  JNI_ABORT);

            if (y+maxLines < h) {
                y += maxLines;
            }
            else {
                y++;
                maxBytes = w;
            }
        }
    }
    else {
        off = 0;
        maxBytes *= numBands;
        for (y=0; y < h; ) {
            (*env)->CallObjectMethod(env, jsm, g_SMGetPixelsMID,
                                     0, y, w,
                                     maxLines, jdata, jdatabuffer);
            dataP = (int *) (*env)->GetPrimitiveArrayCritical(env, jdata,
                                                              NULL);
            if (dataP == NULL) {
                (*env)->DeleteLocalRef(env, jdata);
                return -1;
            }
            for (i=0; i < maxBytes; i++) {
                bufferP[off++] = (unsigned char) dataP[i];
            }

            (*env)->ReleasePrimitiveArrayCritical(env, jdata, dataP,
                                                  JNI_ABORT);

            if (y+maxLines < h) {
                y += maxLines;
            }
            else {
                y++;
                maxBytes = w*numBands;
            }
        }

    }
    (*env)->DeleteLocalRef(env, jdata);

    return 0;
}
int awt_setPixelByte(JNIEnv *env, int band, RasterS_t *rasterP,
                     unsigned char *bufferP) {
    int w = rasterP->width;
    int h = rasterP->height;
    int numBands = rasterP->numBands;
    int y;
    int i;
    int maxLines = (h < MAX_TO_GRAB/w ? h : MAX_TO_GRAB/w);
    jobject jsm;
    int off;
    jarray jdata = NULL;
    jobject jdatabuffer;
    int *dataP;
    int maxBytes = w;

    jsm = (*env)->GetObjectField(env, rasterP->jraster, g_RasterSampleModelID);
    jdatabuffer = (*env)->GetObjectField(env, rasterP->jraster,
                                         g_RasterDataBufferID);
    /* Here is the generic code */
    jdata = (*env)->NewIntArray(env, maxBytes*rasterP->numBands*maxLines);
    if (JNU_IsNull(env, jdata)) {
        JNU_ThrowOutOfMemoryError(env, "Out of Memory");
        return -1;
    }
    if (band >= 0) {
        int dOff;
        if (band >= numBands) {
            (*env)->DeleteLocalRef(env, jdata);
            JNU_ThrowInternalError(env, "Band out of range.");
            return -1;
        }
        off = 0;
        for (y=0; y < h; y+=maxLines) {
            if (y+maxLines > h) {
                maxBytes = w*numBands;
                maxLines = h - y;
            }
            dataP = (int *) (*env)->GetPrimitiveArrayCritical(env, jdata,
                                                              NULL);
            if (dataP == NULL) {
                (*env)->DeleteLocalRef(env, jdata);
                return -1;
            }
            dOff = band;
            for (i=0; i < maxBytes; i++, dOff += numBands) {
                dataP[dOff] = bufferP[off++];
            }

            (*env)->ReleasePrimitiveArrayCritical(env, jdata, dataP,
                                                  JNI_ABORT);

            (*env)->CallVoidMethod(env, jsm, g_SMSetPixelsMID,
                                   0, y, w,
                                   maxLines, jdata, jdatabuffer);
        }
    }
    else {
        off = 0;
        maxBytes *= numBands;
        for (y=0; y < h; y+=maxLines) {
            if (y+maxLines > h) {
                maxBytes = w*numBands;
                maxLines = h - y;
            }
            dataP = (int *) (*env)->GetPrimitiveArrayCritical(env, jdata,
                                                              NULL);
            if (dataP == NULL) {
                (*env)->DeleteLocalRef(env, jdata);
                return -1;
            }
            for (i=0; i < maxBytes; i++) {
                dataP[i] = bufferP[off++];
            }

            (*env)->ReleasePrimitiveArrayCritical(env, jdata, dataP,
                                                  JNI_ABORT);

            (*env)->CallVoidMethod(env, jsm, g_SMSetPixelsMID,
                                     0, y, w,
                                     maxLines, jdata, jdatabuffer);
        }

    }

    (*env)->DeleteLocalRef(env, jdata);

    return 0;
}
int awt_getPixelShort(JNIEnv *env, int band, RasterS_t *rasterP,
                     unsigned short *bufferP) {
    int w = rasterP->width;
    int h = rasterP->height;
    int numBands = rasterP->numBands;
    int y;
    int i;
    int maxLines = (h < MAX_TO_GRAB/w ? h : MAX_TO_GRAB/w);
    jobject jsm;
    int off;
    jarray jdata = NULL;
    jobject jdatabuffer;
    int *dataP;
    int maxBytes = w*maxLines;

    jsm = (*env)->GetObjectField(env, rasterP->jraster, g_RasterSampleModelID);
    jdatabuffer = (*env)->GetObjectField(env, rasterP->jraster,
                                         g_RasterDataBufferID);
    jdata = (*env)->NewIntArray(env, maxBytes*rasterP->numBands*maxLines);
    if (JNU_IsNull(env, jdata)) {
        JNU_ThrowOutOfMemoryError(env, "Out of Memory");
        return -1;
    }
    /* Here is the generic code */
    if (band >= 0) {
        int dOff;
        if (band >= numBands) {
            (*env)->DeleteLocalRef(env, jdata);
            JNU_ThrowInternalError(env, "Band out of range.");
            return -1;
        }
        off = 0;
        for (y=0; y < h; y += maxLines) {
            if (y+maxLines > h) {
                maxBytes = w*numBands;
                maxLines = h - y;
            }
            (*env)->CallObjectMethod(env, jsm, g_SMGetPixelsMID,
                                     0, y, w,
                                     maxLines, jdata, jdatabuffer);
            dataP = (int *) (*env)->GetPrimitiveArrayCritical(env, jdata,
                                                              NULL);
            if (dataP == NULL) {
                (*env)->DeleteLocalRef(env, jdata);
                return -1;
            }

            dOff = band;
            for (i=0; i < maxBytes; i++, dOff += numBands) {
                bufferP[off++] = (unsigned short) dataP[dOff];
            }

            (*env)->ReleasePrimitiveArrayCritical(env, jdata, dataP,
                                                  JNI_ABORT);
        }
    }
    else {
        off = 0;
        maxBytes *= numBands;
        for (y=0; y < h; y+=maxLines) {
            if (y+maxLines > h) {
                maxBytes = w*numBands;
                maxLines = h - y;
            }
            (*env)->CallObjectMethod(env, jsm, g_SMGetPixelsMID,
                                     0, y, w,
                                     maxLines, jdata, jdatabuffer);
            dataP = (int *) (*env)->GetPrimitiveArrayCritical(env, jdata,
                                                              NULL);
            if (dataP == NULL) {
                (*env)->DeleteLocalRef(env, jdata);
                return -1;
            }
            for (i=0; i < maxBytes; i++) {
                bufferP[off++] = (unsigned short) dataP[i];
            }

            (*env)->ReleasePrimitiveArrayCritical(env, jdata, dataP,
                                                  JNI_ABORT);
        }

    }

    (*env)->DeleteLocalRef(env, jdata);
    return 0;
}
int awt_setPixelShort(JNIEnv *env, int band, RasterS_t *rasterP,
                      unsigned short *bufferP) {
    int w = rasterP->width;
    int h = rasterP->height;
    int numBands = rasterP->numBands;
    int y;
    int i;
    int maxLines = (h < MAX_TO_GRAB/w ? h : MAX_TO_GRAB/w);
    jobject jsm;
    int off;
    jarray jdata = NULL;
    jobject jdatabuffer;
    int *dataP;
    int maxBytes = w;

    jsm = (*env)->GetObjectField(env, rasterP->jraster, g_RasterSampleModelID);
    jdatabuffer = (*env)->GetObjectField(env, rasterP->jraster,
                                         g_RasterDataBufferID);
    /* Here is the generic code */
    jdata = (*env)->NewIntArray(env, maxBytes*rasterP->numBands*maxLines);
    if (JNU_IsNull(env, jdata)) {
        JNU_ThrowOutOfMemoryError(env, "Out of Memory");
        return -1;
    }
    if (band >= 0) {
        int dOff;
        if (band >= numBands) {
            (*env)->DeleteLocalRef(env, jdata);
            JNU_ThrowInternalError(env, "Band out of range.");
            return -1;
        }
        off = 0;
        for (y=0; y < h; y+=maxLines) {
            if (y+maxLines > h) {
                maxBytes = w*numBands;
                maxLines = h - y;
            }
            dataP = (int *) (*env)->GetPrimitiveArrayCritical(env, jdata,
                                                              NULL);
            if (dataP == NULL) {
                (*env)->DeleteLocalRef(env, jdata);
                return -1;
            }
            dOff = band;
            for (i=0; i < maxBytes; i++, dOff += numBands) {
                dataP[dOff] = bufferP[off++];
            }

            (*env)->ReleasePrimitiveArrayCritical(env, jdata, dataP,
                                                  JNI_ABORT);

            (*env)->CallVoidMethod(env, jsm, g_SMSetPixelsMID,
                                   0, y, w,
                                   maxLines, jdata, jdatabuffer);
        }
    }
    else {
        off = 0;
        maxBytes *= numBands;
        for (y=0; y < h; y+=maxLines) {
            if (y+maxLines > h) {
                maxBytes = w*numBands;
                maxLines = h - y;
            }
            dataP = (int *) (*env)->GetPrimitiveArrayCritical(env, jdata,
                                                              NULL);
            if (dataP == NULL) {
                (*env)->DeleteLocalRef(env, jdata);
                return -1;
            }
            for (i=0; i < maxBytes; i++) {
                dataP[i] = bufferP[off++];
            }

            (*env)->ReleasePrimitiveArrayCritical(env, jdata, dataP,
                                                  JNI_ABORT);

            (*env)->CallVoidMethod(env, jsm, g_SMSetPixelsMID,
                                   0, y, w,
                                   maxLines, jdata, jdatabuffer);
        }

    }

    (*env)->DeleteLocalRef(env, jdata);
    return 0;
}
