/*
 * Copyright (c) 1997, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef IMAGEINITIDS_H
#define IMAGEINITIDS_H

#include "jni.h"

/* BufferedImage ids */
extern jfieldID g_BImgRasterID;
extern jfieldID g_BImgTypeID;
extern jfieldID g_BImgCMID;
extern jmethodID g_BImgGetRGBMID;
extern jmethodID g_BImgSetRGBMID;

/* Raster ids */
extern jfieldID g_RasterWidthID;
extern jfieldID g_RasterHeightID;
extern jfieldID g_RasterMinXID;
extern jfieldID g_RasterMinYID;
extern jfieldID g_RasterBaseOriginXID;
extern jfieldID g_RasterBaseOriginYID;
extern jfieldID g_RasterSampleModelID;
extern jfieldID g_RasterDataBufferID;
extern jfieldID g_RasterNumDataElementsID;
extern jfieldID g_RasterNumBandsID;

extern jfieldID g_BCRdataID;
extern jfieldID g_BCRscanstrID;
extern jfieldID g_BCRpixstrID;
extern jfieldID g_BCRdataOffsetsID;
extern jfieldID g_BCRtypeID;
extern jfieldID g_BPRdataID;
extern jfieldID g_BPRscanstrID;
extern jfieldID g_BPRpixstrID;
extern jfieldID g_BPRtypeID;
extern jfieldID g_BPRdataBitOffsetID;
extern jfieldID g_SCRdataID;
extern jfieldID g_SCRscanstrID;
extern jfieldID g_SCRpixstrID;
extern jfieldID g_SCRdataOffsetsID;
extern jfieldID g_SCRtypeID;
extern jfieldID g_ICRdataID;
extern jfieldID g_ICRscanstrID;
extern jfieldID g_ICRpixstrID;
extern jfieldID g_ICRdataOffsetsID;
extern jfieldID g_ICRtypeID;

/* Color Model ids */
extern jfieldID g_CMnBitsID;
extern jfieldID g_CMcspaceID;
extern jfieldID g_CMnumComponentsID;
extern jfieldID g_CMsuppAlphaID;
extern jfieldID g_CMisAlphaPreID;
extern jfieldID g_CMtransparencyID;
extern jfieldID g_CMcsTypeID;
extern jfieldID g_CMis_sRGBID;
extern jmethodID g_CMgetRGBdefaultMID;

extern jfieldID g_ICMtransIdxID;
extern jfieldID g_ICMmapSizeID;
extern jfieldID g_ICMrgbID;

/* Sample Model ids */
extern jfieldID g_SMWidthID;
extern jfieldID g_SMHeightID;
extern jmethodID g_SMGetPixelsMID;
extern jmethodID g_SMSetPixelsMID;

/* Single Pixel Packed Sample Model ids */
extern jfieldID g_SPPSMmaskArrID;
extern jfieldID g_SPPSMmaskOffID;
extern jfieldID g_SPPSMnBitsID;
extern jfieldID g_SPPSMmaxBitID;

/* Kernel ids */
extern jfieldID g_KernelWidthID;
extern jfieldID g_KernelHeightID;
extern jfieldID g_KernelDataID;

#endif /* IMAGEINITIDS_H */
