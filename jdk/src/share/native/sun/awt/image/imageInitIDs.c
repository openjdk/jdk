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
#include <jni.h>
#include "jni_util.h"
#define IMGEXTERN
#include "imageInitIDs.h"

JNIEXPORT void JNICALL
Java_java_awt_image_BufferedImage_initIDs(JNIEnv *env, jclass cls) {
    g_BImgRasterID     = (*env)->GetFieldID(env, cls, "raster",
                                            "Ljava/awt/image/WritableRaster;");
    g_BImgTypeID = (*env)->GetFieldID(env, cls, "imageType", "I");
    g_BImgCMID = (*env)->GetFieldID(env, cls, "colorModel",
                                    "Ljava/awt/image/ColorModel;");
    g_BImgGetRGBMID = (*env)->GetMethodID(env, cls, "getRGB",
                                          "(IIII[III)[I");
    g_BImgSetRGBMID = (*env)->GetMethodID(env, cls, "setRGB",
                                          "(IIII[III)V");
    if (g_BImgRasterID == NULL || g_BImgTypeID == NULL || g_BImgCMID == NULL
        || g_BImgGetRGBMID == NULL) {
        JNU_ThrowNullPointerException(env, "Unable to grab field ids");
    }
}

JNIEXPORT void JNICALL
Java_java_awt_image_Raster_initIDs(JNIEnv *env, jclass cls) {
    g_RasterWidthID    = (*env)->GetFieldID(env, cls, "width", "I");
    g_RasterHeightID   = (*env)->GetFieldID(env, cls, "height", "I");
    g_RasterNumBandsID = (*env)->GetFieldID(env, cls, "numBands", "I");
    g_RasterGetDataMID = (*env)->GetMethodID(env, cls, "getDataElements",
                              "(IIIILjava/lang/Object;)Ljava/lang/Object;");
    g_RasterMinXID  = (*env)->GetFieldID(env, cls, "minX", "I");
    g_RasterMinYID  = (*env)->GetFieldID(env, cls, "minY", "I");
    g_RasterBaseOriginXID  = (*env)->GetFieldID(env, cls,
                                 "sampleModelTranslateX", "I");
    g_RasterBaseOriginYID  = (*env)->GetFieldID(env, cls,
                                 "sampleModelTranslateY", "I");
    g_RasterSampleModelID = (*env)->GetFieldID(env, cls,
                                 "sampleModel","Ljava/awt/image/SampleModel;");
    g_RasterNumDataElementsID = (*env)->GetFieldID(env, cls, "numDataElements",
                                                   "I");
    g_RasterNumBandsID = (*env)->GetFieldID(env, cls, "numBands", "I");
    g_RasterDataBufferID = (*env)->GetFieldID(env, cls, "dataBuffer",
                                              "Ljava/awt/image/DataBuffer;");
    if (g_RasterWidthID == NULL || g_RasterHeightID == NULL
        || g_RasterNumBandsID == NULL || g_RasterGetDataMID == NULL
        || g_RasterMinXID == NULL || g_RasterMinYID == NULL
        || g_RasterBaseOriginXID == NULL || g_RasterBaseOriginYID == NULL
        || g_RasterSampleModelID == NULL || g_RasterNumDataElementsID == NULL
        || g_RasterNumBandsID == NULL || g_RasterDataBufferID == NULL)
    {
        JNU_ThrowNullPointerException(env, "Unable to grab field ids");
    }
}

JNIEXPORT void JNICALL
Java_sun_awt_image_ByteComponentRaster_initIDs(JNIEnv *env, jclass cls) {
    g_BCRdataID = (*env)->GetFieldID(env, cls, "data", "[B");
    g_BCRscanstrID = (*env)->GetFieldID(env, cls, "scanlineStride", "I");
    g_BCRpixstrID = (*env)->GetFieldID(env, cls, "pixelStride", "I");
    g_BCRbandoffsID = (*env)->GetFieldID(env, cls, "bandOffset", "I");
    g_BCRdataOffsetsID = (*env)->GetFieldID(env, cls, "dataOffsets", "[I");
    g_BCRtypeID = (*env)->GetFieldID(env, cls, "type", "I");
    if (g_BCRdataID == NULL || g_BCRscanstrID == NULL ||
        g_BCRpixstrID == NULL || g_BCRbandoffsID == NULL ||
        g_BCRtypeID == NULL)
    {
        JNU_ThrowNullPointerException(env, "Unable to grab field ids");
    }
}

JNIEXPORT void JNICALL
Java_sun_awt_image_BytePackedRaster_initIDs(JNIEnv *env, jclass cls) {
    g_BPRdataID = (*env)->GetFieldID(env, cls, "data", "[B");
    g_BPRscanstrID = (*env)->GetFieldID(env, cls, "scanlineStride", "I");
    g_BPRpixstrID = (*env)->GetFieldID(env, cls, "pixelBitStride", "I");
    g_BPRtypeID = (*env)->GetFieldID(env, cls, "type", "I");
    g_BPRdataBitOffsetID = (*env)->GetFieldID(env, cls, "dataBitOffset", "I");
    if (g_BPRdataID == NULL || g_BPRscanstrID == NULL ||
        g_BPRpixstrID == NULL ||  g_BPRtypeID == NULL)
    {
        JNU_ThrowNullPointerException(env, "Unable to grab field ids");
    }
}

JNIEXPORT void JNICALL
Java_sun_awt_image_ShortComponentRaster_initIDs(JNIEnv *env, jclass cls) {
    g_SCRdataID = (*env)->GetFieldID(env, cls, "data", "[S");
    g_SCRscanstrID = (*env)->GetFieldID(env, cls, "scanlineStride", "I");
    g_SCRpixstrID = (*env)->GetFieldID(env, cls, "pixelStride", "I");
    g_SCRbandoffsID = (*env)->GetFieldID(env, cls, "bandOffset", "I");
    g_SCRdataOffsetsID = (*env)->GetFieldID(env, cls, "dataOffsets", "[I");
    g_SCRtypeID = (*env)->GetFieldID(env, cls, "type", "I");
    if (g_SCRdataID == NULL || g_SCRscanstrID == NULL ||
        g_SCRpixstrID == NULL || g_SCRbandoffsID == NULL ||
        g_SCRdataOffsetsID == NULL || g_SCRtypeID == NULL)
    {
        JNU_ThrowNullPointerException(env, "Unable to grab field ids");
    }
}
JNIEXPORT void JNICALL
Java_sun_awt_image_IntegerComponentRaster_initIDs(JNIEnv *env, jclass cls) {
    g_ICRdataID = (*env)->GetFieldID(env, cls, "data", "[I");
    g_ICRscanstrID = (*env)->GetFieldID(env, cls, "scanlineStride", "I");
    g_ICRpixstrID = (*env)->GetFieldID(env, cls, "pixelStride", "I");
    g_ICRdataOffsetsID = (*env)->GetFieldID(env, cls, "dataOffsets", "[I");
    g_ICRbandoffsID = (*env)->GetFieldID(env, cls, "bandOffset", "I");
    g_ICRputDataMID  = (*env)->GetMethodID(env, cls, "setDataElements",
                                     "(IIIILjava/lang/Object;)V");
    g_ICRtypeID = (*env)->GetFieldID(env, cls, "type", "I");
    if (g_ICRdataID == NULL || g_ICRscanstrID == NULL
        || g_ICRpixstrID == NULL || g_ICRbandoffsID == NULL
        || g_ICRputDataMID == NULL || g_ICRdataOffsetsID == NULL || g_ICRtypeID == NULL)
    {
        JNU_ThrowNullPointerException(env, "Unable to grab field ids");
    }
}

JNIEXPORT void JNICALL
Java_java_awt_image_SinglePixelPackedSampleModel_initIDs(JNIEnv *env,
                                                         jclass cls) {
    g_SPPSMmaskArrID = (*env)->GetFieldID(env, cls, "bitMasks", "[I");
    g_SPPSMmaskOffID = (*env)->GetFieldID(env, cls, "bitOffsets", "[I");
    g_SPPSMnBitsID   = (*env)->GetFieldID(env, cls, "bitSizes", "[I");
    g_SPPSMmaxBitID  = (*env)->GetFieldID(env, cls, "maxBitSize", "I");

    if (g_SPPSMmaskArrID == NULL || g_SPPSMmaskOffID == NULL ||
        g_SPPSMnBitsID == NULL || g_SPPSMmaxBitID == NULL) {
        JNU_ThrowNullPointerException(env, "Unable to grab field ids");
    }
}

JNIEXPORT void JNICALL
Java_java_awt_image_ColorModel_initIDs(JNIEnv *env, jclass cls) {
    g_CMpDataID = (*env)->GetFieldID (env, cls, "pData", "J");
    g_CMnBitsID  = (*env)->GetFieldID(env, cls, "nBits", "[I");
    g_CMcspaceID = (*env)->GetFieldID(env, cls, "colorSpace",
                                    "Ljava/awt/color/ColorSpace;");
    g_CMnumComponentsID = (*env)->GetFieldID(env, cls, "numComponents", "I");
    g_CMsuppAlphaID  = (*env)->GetFieldID(env, cls, "supportsAlpha", "Z");
    g_CMisAlphaPreID = (*env)->GetFieldID(env, cls, "isAlphaPremultiplied",
                                          "Z");
    g_CMtransparencyID = (*env)->GetFieldID(env, cls, "transparency", "I");
    g_CMgetRGBMID      = (*env)->GetMethodID(env, cls, "getRGB",
                                             "(Ljava/lang/Object;)I");
    g_CMcsTypeID       = (*env)->GetFieldID(env, cls, "colorSpaceType", "I");
    g_CMis_sRGBID      = (*env)->GetFieldID(env, cls, "is_sRGB", "Z");
    g_CMgetRGBdefaultMID   = (*env)->GetStaticMethodID(env, cls,
                                                       "getRGBdefault",
                                             "()Ljava/awt/image/ColorModel;");
    if (g_CMnBitsID == NULL || g_CMcspaceID == NULL
        || g_CMnumComponentsID == NULL || g_CMsuppAlphaID == NULL
        || g_CMisAlphaPreID == NULL || g_CMtransparencyID == NULL
        || g_CMgetRGBMID == NULL || g_CMgetRGBMID == NULL
        || g_CMis_sRGBID == NULL || g_CMgetRGBdefaultMID == NULL
        || g_CMpDataID == NULL)
    {
        JNU_ThrowNullPointerException(env, "Unable to grab field ids");
    }
}

JNIEXPORT void JNICALL
Java_java_awt_image_IndexColorModel_initIDs(JNIEnv *env, jclass cls) {
    g_ICMtransIdxID = (*env)->GetFieldID(env, cls, "transparent_index", "I");
    g_ICMmapSizeID  = (*env)->GetFieldID(env, cls, "map_size", "I");
    g_ICMrgbID      = (*env)->GetFieldID(env, cls, "rgb", "[I");
    if (g_ICMtransIdxID == NULL || g_ICMmapSizeID == NULL
        || g_ICMrgbID == NULL) {
        JNU_ThrowNullPointerException(env, "Unable to grab field ids");
    }
}

JNIEXPORT void JNICALL
Java_java_awt_image_SampleModel_initIDs(JNIEnv *env, jclass cls) {
    g_SMWidthID = (*env)->GetFieldID(env, cls, "width","I");
    g_SMHeightID = (*env)->GetFieldID(env, cls, "height","I");
    g_SMGetPixelsMID = (*env)->GetMethodID(env, cls, "getPixels",
                                      "(IIII[ILjava/awt/image/DataBuffer;)[I");
    g_SMSetPixelsMID = (*env)->GetMethodID(env, cls, "setPixels",
                                      "(IIII[ILjava/awt/image/DataBuffer;)V");
    if (g_SMWidthID == NULL || g_SMHeightID == NULL || g_SMGetPixelsMID == NULL
        || g_SMSetPixelsMID == NULL) {
        JNU_ThrowNullPointerException(env, "Unable to grab field ids");
    }
}

JNIEXPORT void JNICALL
Java_java_awt_image_ComponentSampleModel_initIDs(JNIEnv *env, jclass cls) {
    g_CSMPixStrideID = (*env)->GetFieldID(env, cls, "pixelStride", "I");
    g_CSMScanStrideID = (*env)->GetFieldID(env, cls, "scanlineStride", "I");
    g_CSMBandOffsetsID = (*env)->GetFieldID(env, cls, "bandOffsets", "[I");
    if (g_CSMPixStrideID == NULL || g_CSMScanStrideID == NULL ||
        g_CSMBandOffsetsID == NULL) {
        JNU_ThrowNullPointerException(env, "Unable to grab field ids");
    }
}

JNIEXPORT void JNICALL
Java_java_awt_image_Kernel_initIDs(JNIEnv *env, jclass cls) {
    g_KernelWidthID   = (*env)->GetFieldID(env, cls, "width", "I");
    g_KernelHeightID  = (*env)->GetFieldID(env, cls, "height", "I");
    g_KernelDataID    = (*env)->GetFieldID(env, cls, "data", "[F");
    if (g_KernelWidthID == NULL || g_KernelHeightID == NULL
        || g_KernelDataID == NULL)
    {
        JNU_ThrowNullPointerException(env, "Unable to grab field ids");
    }
}

JNIEXPORT void JNICALL
Java_java_awt_image_DataBufferInt_initIDs(JNIEnv *env, jclass cls) {
    g_DataBufferIntPdataID = (*env)->GetFieldID(env, cls, "pData", "J");
    if (g_DataBufferIntPdataID == NULL) {
        JNU_ThrowNullPointerException(env, "Unable to grab DataBufferInt.pData");
        return;
    }
}
