/*
 * Copyright (c) 2007, 2010, Oracle and/or its affiliates. All rights reserved.
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
#include <memory.h>
#include "sun_java2d_cmm_lcms_LCMS.h"
#include "jni_util.h"
#include "Trace.h"
#include "Disposer.h"
#include "lcms2.h"


#define ALIGNLONG(x) (((x)+3) & ~(3))         // Aligns to DWORD boundary

#ifdef USE_BIG_ENDIAN
#define AdjustEndianess32(a)
#else

static
void AdjustEndianess32(cmsUInt8Number *pByte)
{
    cmsUInt8Number temp1;
    cmsUInt8Number temp2;

    temp1 = *pByte++;
    temp2 = *pByte++;
    *(pByte-1) = *pByte;
    *pByte++ = temp2;
    *(pByte-3) = *pByte;
    *pByte = temp1;
}

#endif

// Transports to properly encoded values - note that icc profiles does use
// big endian notation.

static
cmsInt32Number TransportValue32(cmsInt32Number Value)
{
    cmsInt32Number Temp = Value;

    AdjustEndianess32((cmsUInt8Number*) &Temp);
    return Temp;
}

#define SigMake(a,b,c,d) \
                    ( ( ((int) ((unsigned char) (a))) << 24) | \
                      ( ((int) ((unsigned char) (b))) << 16) | \
                      ( ((int) ((unsigned char) (c))) <<  8) | \
                          (int) ((unsigned char) (d)))

#define TagIdConst(a, b, c, d) \
                ((int) SigMake ((a), (b), (c), (d)))

#define SigHead TagIdConst('h','e','a','d')

#define DT_BYTE     0
#define DT_SHORT    1
#define DT_INT      2
#define DT_DOUBLE   3

/* Default temp profile list size */
#define DF_ICC_BUF_SIZE 32

#define ERR_MSG_SIZE 256

#ifdef _MSC_VER
# ifndef snprintf
#       define snprintf  _snprintf
# endif
#endif

typedef union storeID_s {    /* store SProfile stuff in a Java Long */
    cmsHPROFILE pf;
    cmsHTRANSFORM xf;
    jobject jobj;
    jlong j;
} storeID_t, *storeID_p;

typedef union {
    cmsTagSignature cms;
    jint j;
} TagSignature_t, *TagSignature_p;

static jfieldID Trans_profileIDs_fID;
static jfieldID Trans_renderType_fID;
static jfieldID Trans_ID_fID;
static jfieldID IL_isIntPacked_fID;
static jfieldID IL_dataType_fID;
static jfieldID IL_pixelType_fID;
static jfieldID IL_dataArray_fID;
static jfieldID IL_offset_fID;
static jfieldID IL_nextRowOffset_fID;
static jfieldID IL_width_fID;
static jfieldID IL_height_fID;
static jfieldID PF_ID_fID;

JavaVM *javaVM;

void errorHandler(cmsContext ContextID, cmsUInt32Number errorCode,
                  const char *errorText) {
    JNIEnv *env;
    char errMsg[ERR_MSG_SIZE];

    int count = snprintf(errMsg, ERR_MSG_SIZE,
                          "LCMS error %d: %s", errorCode, errorText);
    if (count < 0 || count >= ERR_MSG_SIZE) {
        count = ERR_MSG_SIZE - 1;
    }
    errMsg[count] = 0;

    (*javaVM)->AttachCurrentThread(javaVM, (void**)&env, NULL);
    JNU_ThrowByName(env, "java/awt/color/CMMException", errMsg);
}

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *jvm, void *reserved) {
    javaVM = jvm;

    cmsSetLogErrorHandler(errorHandler);
    return JNI_VERSION_1_6;
}

void LCMS_freeTransform(JNIEnv *env, jlong ID)
{
    storeID_t sTrans;
    sTrans.j = ID;
    /* Passed ID is always valid native ref so there is no check for zero */
    cmsDeleteTransform(sTrans.xf);
}

/*
 * Class:     sun_java2d_cmm_lcms_LCMS
 * Method:    createNativeTransform
 * Signature: ([JI)J
 */
JNIEXPORT jlong JNICALL Java_sun_java2d_cmm_lcms_LCMS_createNativeTransform
  (JNIEnv *env, jclass cls, jlongArray profileIDs, jint renderType,
   jint inFormatter, jint outFormatter, jobject disposerRef)
{
    cmsHPROFILE _iccArray[DF_ICC_BUF_SIZE];
    cmsHPROFILE *iccArray = &_iccArray[0];
    storeID_t sTrans;
    int i, j, size;
    jlong* ids;

    size = (*env)->GetArrayLength (env, profileIDs);
    ids = (*env)->GetPrimitiveArrayCritical(env, profileIDs, 0);

    if (DF_ICC_BUF_SIZE < size*2) {
        iccArray = (cmsHPROFILE*) malloc(
            size*2*sizeof(cmsHPROFILE));
        if (iccArray == NULL) {
            J2dRlsTraceLn(J2D_TRACE_ERROR, "getXForm: iccArray == NULL");
            return 0L;
        }
    }

    j = 0;
    for (i = 0; i < size; i++) {
        cmsHPROFILE icc;
        cmsColorSpaceSignature cs;

        sTrans.j = ids[i];
        icc = sTrans.pf;
        iccArray[j++] = icc;

        /* Middle non-abstract profiles should be doubled before passing to
         * the cmsCreateMultiprofileTransform function
         */

        cs = cmsGetColorSpace(icc);
        if (size > 2 && i != 0 && i != size - 1 &&
            cs != cmsSigXYZData && cs != cmsSigLabData)
        {
            iccArray[j++] = icc;
        }
    }

    sTrans.xf = cmsCreateMultiprofileTransform(iccArray, j,
        inFormatter, outFormatter, renderType, 0);

    (*env)->ReleasePrimitiveArrayCritical(env, profileIDs, ids, 0);

    if (sTrans.xf == NULL) {
        J2dRlsTraceLn(J2D_TRACE_ERROR, "LCMS_createNativeTransform: "
                                       "sTrans.xf == NULL");
        JNU_ThrowByName(env, "java/awt/color/CMMException",
                        "Cannot get color transform");
    } else {
        Disposer_AddRecord(env, disposerRef, LCMS_freeTransform, sTrans.j);
    }

    if (iccArray != &_iccArray[0]) {
        free(iccArray);
    }
    return sTrans.j;
}


/*
 * Class:     sun_java2d_cmm_lcms_LCMS
 * Method:    loadProfile
 * Signature: ([B)J
 */
JNIEXPORT jlong JNICALL Java_sun_java2d_cmm_lcms_LCMS_loadProfile
  (JNIEnv *env, jobject obj, jbyteArray data)
{
    jbyte* dataArray;
    jint dataSize;
    storeID_t sProf;

    dataArray = (*env)->GetByteArrayElements (env, data, 0);
    dataSize = (*env)->GetArrayLength (env, data);

    sProf.pf = cmsOpenProfileFromMem((const void *)dataArray,
                                     (cmsUInt32Number) dataSize);

    (*env)->ReleaseByteArrayElements (env, data, dataArray, 0);

    if (sProf.pf == NULL) {
        JNU_ThrowIllegalArgumentException(env, "Invalid profile data");
    }

    return sProf.j;
}

/*
 * Class:     sun_java2d_cmm_lcms_LCMS
 * Method:    freeProfile
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_sun_java2d_cmm_lcms_LCMS_freeProfile
  (JNIEnv *env, jobject obj, jlong id)
{
    storeID_t sProf;

    sProf.j = id;
    if (cmsCloseProfile(sProf.pf) == 0) {
        J2dRlsTraceLn1(J2D_TRACE_ERROR, "LCMS_freeProfile: cmsCloseProfile(%d)"
                       "== 0", id);
        JNU_ThrowByName(env, "java/awt/color/CMMException",
                        "Cannot close profile");
    }

}

/*
 * Class:     sun_java2d_cmm_lcms_LCMS
 * Method:    getProfileSize
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_sun_java2d_cmm_lcms_LCMS_getProfileSize
  (JNIEnv *env, jobject obj, jlong id)
{
    storeID_t sProf;
    cmsUInt32Number pfSize = 0;
    sProf.j = id;

    if (cmsSaveProfileToMem(sProf.pf, NULL, &pfSize) && ((jint)pfSize > 0)) {
        return (jint)pfSize;
    } else {
      JNU_ThrowByName(env, "java/awt/color/CMMException",
                      "Can not access specified profile.");
        return -1;
    }
}

/*
 * Class:     sun_java2d_cmm_lcms_LCMS
 * Method:    getProfileData
 * Signature: (J[B)V
 */
JNIEXPORT void JNICALL Java_sun_java2d_cmm_lcms_LCMS_getProfileData
  (JNIEnv *env, jobject obj, jlong id, jbyteArray data)
{
    storeID_t sProf;
    jint size;
    jbyte* dataArray;
    cmsUInt32Number pfSize = 0;
    cmsBool status;

    sProf.j = id;

    // determine actual profile size
    if (!cmsSaveProfileToMem(sProf.pf, NULL, &pfSize)) {
        JNU_ThrowByName(env, "java/awt/color/CMMException",
                        "Can not access specified profile.");
        return;
    }

    // verify java buffer capacity
    size = (*env)->GetArrayLength(env, data);
    if (0 >= size || pfSize > (cmsUInt32Number)size) {
        JNU_ThrowByName(env, "java/awt/color/CMMException",
                        "Insufficient buffer capacity.");
        return;
    }

    dataArray = (*env)->GetByteArrayElements (env, data, 0);

    status = cmsSaveProfileToMem(sProf.pf, dataArray, &pfSize);

    (*env)->ReleaseByteArrayElements (env, data, dataArray, 0);

    if (!status) {
        JNU_ThrowByName(env, "java/awt/color/CMMException",
                        "Can not access specified profile.");
        return;
    }
}

/* Get profile header info */
cmsBool _getHeaderInfo(cmsHPROFILE pf, jbyte* pBuffer, jint bufferSize);
cmsBool _setHeaderInfo(cmsHPROFILE pf, jbyte* pBuffer, jint bufferSize);

/*
 * Class:     sun_java2d_cmm_lcms_LCMS
 * Method:    getTagSize
 * Signature: (JI)I
 */
JNIEXPORT jint JNICALL Java_sun_java2d_cmm_lcms_LCMS_getTagSize
  (JNIEnv *env, jobject obj, jlong id, jint tagSig)
{
    storeID_t sProf;
    TagSignature_t sig;
    jint result = -1;

    sProf.j = id;
    sig.j = tagSig;

    if (tagSig == SigHead) {
        result = sizeof(cmsICCHeader);
    } else {
      if (cmsIsTag(sProf.pf, sig.cms)) {
            result = cmsReadRawTag(sProf.pf, sig.cms, NULL, 0);
        } else {
            JNU_ThrowByName(env, "java/awt/color/CMMException",
                            "ICC profile tag not found");
        }
    }

    return result;
}

/*
 * Class:     sun_java2d_cmm_lcms_LCMS
 * Method:    getTagData
 * Signature: (JI[B)V
 */
JNIEXPORT void JNICALL Java_sun_java2d_cmm_lcms_LCMS_getTagData
  (JNIEnv *env, jobject obj, jlong id, jint tagSig, jbyteArray data)
{
    storeID_t sProf;
    TagSignature_t sig;
    cmsInt32Number tagSize;

    jbyte* dataArray;
    jint bufSize;

    sProf.j = id;
    sig.j = tagSig;

    if (tagSig == SigHead) {
        cmsBool status;

        bufSize =(*env)->GetArrayLength(env, data);

        if (bufSize < sizeof(cmsICCHeader)) {
           JNU_ThrowByName(env, "java/awt/color/CMMException",
                            "Insufficient buffer capacity");
           return;
        }

        dataArray = (*env)->GetByteArrayElements (env, data, 0);

        if (dataArray == NULL) {
           JNU_ThrowByName(env, "java/awt/color/CMMException",
                            "Unable to get buffer");
           return;
        }

        status = _getHeaderInfo(sProf.pf, dataArray, bufSize);

        (*env)->ReleaseByteArrayElements (env, data, dataArray, 0);

        if (!status) {
            JNU_ThrowByName(env, "java/awt/color/CMMException",
                            "ICC Profile header not found");
        }

        return;
    }

    if (cmsIsTag(sProf.pf, sig.cms)) {
        tagSize = cmsReadRawTag(sProf.pf, sig.cms, NULL, 0);
    } else {
        JNU_ThrowByName(env, "java/awt/color/CMMException",
                        "ICC profile tag not found");
        return;
    }

    // verify data buffer capacity
    bufSize = (*env)->GetArrayLength(env, data);

    if (tagSize < 0 || 0 > bufSize || tagSize > bufSize) {
        JNU_ThrowByName(env, "java/awt/color/CMMException",
                        "Insufficient buffer capacity.");
        return;
    }

    dataArray = (*env)->GetByteArrayElements (env, data, 0);

    if (dataArray == NULL) {
        JNU_ThrowByName(env, "java/awt/color/CMMException",
                        "Unable to get buffer");
        return;
    }

    bufSize = cmsReadRawTag(sProf.pf, sig.cms, dataArray, tagSize);

    (*env)->ReleaseByteArrayElements (env, data, dataArray, 0);

    if (bufSize != tagSize) {
        JNU_ThrowByName(env, "java/awt/color/CMMException",
                        "Can not get tag data.");
    }
    return;
}

/*
 * Class:     sun_java2d_cmm_lcms_LCMS
 * Method:    setTagData
 * Signature: (JI[B)V
 */
JNIEXPORT void JNICALL Java_sun_java2d_cmm_lcms_LCMS_setTagData
  (JNIEnv *env, jobject obj, jlong id, jint tagSig, jbyteArray data)
{
    storeID_t sProf;
    TagSignature_t sig;
    cmsBool status;
    jbyte* dataArray;
    int tagSize;

    sProf.j = id;
    sig.j = tagSig;


    tagSize =(*env)->GetArrayLength(env, data);

    dataArray = (*env)->GetByteArrayElements(env, data, 0);

    if (tagSig == SigHead) {
        status = _setHeaderInfo(sProf.pf, dataArray, tagSize);
    } else {
        status = cmsWriteRawTag(sProf.pf, sig.cms, dataArray, tagSize);
    }

    (*env)->ReleaseByteArrayElements(env, data, dataArray, 0);

    if (!status) {
        JNU_ThrowByName(env, "java/awt/color/CMMException",
                        "Can not write tag data.");
    }
}

void* getILData (JNIEnv *env, jobject img, jint* pDataType,
                 jobject* pDataObject) {
    void* result = NULL;
    *pDataType = (*env)->GetIntField (env, img, IL_dataType_fID);
    *pDataObject = (*env)->GetObjectField(env, img, IL_dataArray_fID);
    switch (*pDataType) {
        case DT_BYTE:
            result = (*env)->GetByteArrayElements (env, *pDataObject, 0);
            break;
        case DT_SHORT:
            result = (*env)->GetShortArrayElements (env, *pDataObject, 0);
            break;
        case DT_INT:
            result = (*env)->GetIntArrayElements (env, *pDataObject, 0);
            break;
        case DT_DOUBLE:
            result = (*env)->GetDoubleArrayElements (env, *pDataObject, 0);
            break;
    }

    return result;
}

void releaseILData (JNIEnv *env, void* pData, jint dataType,
                    jobject dataObject) {
    switch (dataType) {
        case DT_BYTE:
            (*env)->ReleaseByteArrayElements(env,dataObject,(jbyte*)pData,0);
            break;
        case DT_SHORT:
            (*env)->ReleaseShortArrayElements(env,dataObject,(jshort*)pData, 0);
            break;
        case DT_INT:
            (*env)->ReleaseIntArrayElements(env,dataObject,(jint*)pData,0);
            break;
        case DT_DOUBLE:
            (*env)->ReleaseDoubleArrayElements(env,dataObject,(jdouble*)pData,
                                               0);
            break;
    }
}

/*
 * Class:     sun_java2d_cmm_lcms_LCMS
 * Method:    colorConvert
 * Signature: (Lsun/java2d/cmm/lcms/LCMSTransform;Lsun/java2d/cmm/lcms/LCMSImageLayout;Lsun/java2d/cmm/lcms/LCMSImageLayout;)V
 */
JNIEXPORT void JNICALL Java_sun_java2d_cmm_lcms_LCMS_colorConvert
  (JNIEnv *env, jclass obj, jobject trans, jobject src, jobject dst)
{
    storeID_t sTrans;
    int inFmt, outFmt, srcDType, dstDType;
    int srcOffset, srcNextRowOffset, dstOffset, dstNextRowOffset;
    int width, height, i;
    void* inputBuffer;
    void* outputBuffer;
    char* inputRow;
    char* outputRow;
    jobject srcData, dstData;

    inFmt = (*env)->GetIntField (env, src, IL_pixelType_fID);
    outFmt = (*env)->GetIntField (env, dst, IL_pixelType_fID);
    srcOffset = (*env)->GetIntField (env, src, IL_offset_fID);
    srcNextRowOffset = (*env)->GetIntField (env, src, IL_nextRowOffset_fID);
    dstOffset = (*env)->GetIntField (env, dst, IL_offset_fID);
    dstNextRowOffset = (*env)->GetIntField (env, dst, IL_nextRowOffset_fID);
    width = (*env)->GetIntField (env, src, IL_width_fID);
    height = (*env)->GetIntField (env, src, IL_height_fID);
#ifdef _LITTLE_ENDIAN
    /* Reversing data packed into int for LE archs */
    if ((*env)->GetBooleanField (env, src, IL_isIntPacked_fID) == JNI_TRUE) {
        inFmt ^= DOSWAP_SH(1);
    }
    if ((*env)->GetBooleanField (env, dst, IL_isIntPacked_fID) == JNI_TRUE) {
        outFmt ^= DOSWAP_SH(1);
    }
#endif
    sTrans.j = (*env)->GetLongField (env, trans, Trans_ID_fID);

    if (sTrans.xf == NULL) {
        J2dRlsTraceLn(J2D_TRACE_ERROR, "LCMS_colorConvert: transform == NULL");
        JNU_ThrowByName(env, "java/awt/color/CMMException",
                        "Cannot get color transform");
        return;
    }


    inputBuffer = getILData (env, src, &srcDType, &srcData);

    if (inputBuffer == NULL) {
        J2dRlsTraceLn(J2D_TRACE_ERROR, "");
        JNU_ThrowByName(env, "java/awt/color/CMMException",
                        "Cannot get input data");
        return;
    }

    outputBuffer = getILData (env, dst, &dstDType, &dstData);

    if (outputBuffer == NULL) {
        releaseILData(env, inputBuffer, srcDType, srcData);
        JNU_ThrowByName(env, "java/awt/color/CMMException",
                        "Cannot get output data");
        return;
    }

    inputRow = (char*)inputBuffer + srcOffset;
    outputRow = (char*)outputBuffer + dstOffset;

    for (i = 0; i < height; i++) {
        cmsDoTransform(sTrans.xf, inputRow, outputRow, width);
        inputRow += srcNextRowOffset;
        outputRow += dstNextRowOffset;
    }

    releaseILData(env, inputBuffer, srcDType, srcData);
    releaseILData(env, outputBuffer, dstDType, dstData);
}

/*
 * Class:     sun_java2d_cmm_lcms_LCMS
 * Method:    getProfileID
 * Signature: (Ljava/awt/color/ICC_Profile;)J
 */
JNIEXPORT jlong JNICALL Java_sun_java2d_cmm_lcms_LCMS_getProfileID
  (JNIEnv *env, jclass cls, jobject pf)
{
    return (*env)->GetLongField (env, pf, PF_ID_fID);
}

/*
 * Class:     sun_java2d_cmm_lcms_LCMS
 * Method:    initLCMS
 * Signature: (Ljava/lang/Class;Ljava/lang/Class;Ljava/lang/Class;)V
 */
JNIEXPORT void JNICALL Java_sun_java2d_cmm_lcms_LCMS_initLCMS
  (JNIEnv *env, jclass cls, jclass Trans, jclass IL, jclass Pf)
{
    /* TODO: move initialization of the IDs to the static blocks of
     * corresponding classes to avoid problems with invalidating ids by class
     * unloading
     */
    Trans_profileIDs_fID = (*env)->GetFieldID (env, Trans, "profileIDs", "[J");
    Trans_renderType_fID = (*env)->GetFieldID (env, Trans, "renderType", "I");
    Trans_ID_fID = (*env)->GetFieldID (env, Trans, "ID", "J");

    IL_isIntPacked_fID = (*env)->GetFieldID (env, IL, "isIntPacked", "Z");
    IL_dataType_fID = (*env)->GetFieldID (env, IL, "dataType", "I");
    IL_pixelType_fID = (*env)->GetFieldID (env, IL, "pixelType", "I");
    IL_dataArray_fID = (*env)->GetFieldID(env, IL, "dataArray",
                                          "Ljava/lang/Object;");
    IL_width_fID = (*env)->GetFieldID (env, IL, "width", "I");
    IL_height_fID = (*env)->GetFieldID (env, IL, "height", "I");
    IL_offset_fID = (*env)->GetFieldID (env, IL, "offset", "I");
    IL_nextRowOffset_fID = (*env)->GetFieldID (env, IL, "nextRowOffset", "I");

    PF_ID_fID = (*env)->GetFieldID (env, Pf, "ID", "J");
}

cmsBool _getHeaderInfo(cmsHPROFILE pf, jbyte* pBuffer, jint bufferSize)
{
  cmsUInt32Number pfSize = 0;
  cmsUInt8Number* pfBuffer = NULL;
  cmsBool status = FALSE;

  if (!cmsSaveProfileToMem(pf, NULL, &pfSize) ||
      pfSize < sizeof(cmsICCHeader) ||
      bufferSize < sizeof(cmsICCHeader))
  {
    return FALSE;
  }

  pfBuffer = malloc(pfSize);
  if (pfBuffer == NULL) {
    return FALSE;
  }

  // load raw profile data into the buffer
  if (cmsSaveProfileToMem(pf, pfBuffer, &pfSize)) {
    memcpy(pBuffer, pfBuffer, sizeof(cmsICCHeader));
    status = TRUE;
  }
  free(pfBuffer);
  return status;
}

cmsBool _setHeaderInfo(cmsHPROFILE pf, jbyte* pBuffer, jint bufferSize)
{
  cmsICCHeader pfHeader = { 0 };

  if (pBuffer == NULL || bufferSize < sizeof(cmsICCHeader)) {
    return FALSE;
  }

  memcpy(&pfHeader, pBuffer, sizeof(cmsICCHeader));

  // now set header fields, which we can access using the lcms2 public API
  cmsSetHeaderFlags(pf, pfHeader.flags);
  cmsSetHeaderManufacturer(pf, pfHeader.manufacturer);
  cmsSetHeaderModel(pf, pfHeader.model);
  cmsSetHeaderAttributes(pf, pfHeader.attributes);
  cmsSetHeaderProfileID(pf, (cmsUInt8Number*)&(pfHeader.profileID));
  cmsSetHeaderRenderingIntent(pf, pfHeader.renderingIntent);
  cmsSetPCS(pf, pfHeader.pcs);
  cmsSetColorSpace(pf, pfHeader.colorSpace);
  cmsSetDeviceClass(pf, pfHeader.deviceClass);
  cmsSetEncodedICCversion(pf, pfHeader.version);

  return TRUE;
}
