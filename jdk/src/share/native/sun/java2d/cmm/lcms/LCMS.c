/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
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

typedef struct lcmsProfile_s {
    cmsHPROFILE pf;
} lcmsProfile_t, *lcmsProfile_p;

typedef union storeID_s {    /* store SProfile stuff in a Java Long */
    lcmsProfile_p lcmsPf;
    cmsHTRANSFORM xf;
    jobject jobj;
    jlong j;
} storeID_t, *storeID_p;

typedef union {
    cmsTagSignature cms;
    jint j;
} TagSignature_t, *TagSignature_p;

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
static jfieldID IL_imageAtOnce_fID;

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

void LCMS_freeProfile(JNIEnv *env, jlong ptr) {
    storeID_t sProfile;
    sProfile.j = ptr;

    if (sProfile.lcmsPf != NULL) {
        if (sProfile.lcmsPf->pf != NULL) {
            cmsCloseProfile(sProfile.lcmsPf->pf);
        }
        free(sProfile.lcmsPf);
    }
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
   jint inFormatter, jboolean isInIntPacked,
   jint outFormatter, jboolean isOutIntPacked, jobject disposerRef)
{
    cmsHPROFILE _iccArray[DF_ICC_BUF_SIZE];
    cmsHPROFILE *iccArray = &_iccArray[0];
    storeID_t sTrans;
    int i, j, size;
    jlong* ids;

    size = (*env)->GetArrayLength (env, profileIDs);
    ids = (*env)->GetLongArrayElements(env, profileIDs, 0);

#ifdef _LITTLE_ENDIAN
    /* Reversing data packed into int for LE archs */
    if (isInIntPacked) {
        inFormatter ^= DOSWAP_SH(1);
    }
    if (isOutIntPacked) {
        outFormatter ^= DOSWAP_SH(1);
    }
#endif

    if (DF_ICC_BUF_SIZE < size*2) {
        iccArray = (cmsHPROFILE*) malloc(
            size*2*sizeof(cmsHPROFILE));
        if (iccArray == NULL) {
            (*env)->ReleaseLongArrayElements(env, profileIDs, ids, 0);

            J2dRlsTraceLn(J2D_TRACE_ERROR, "getXForm: iccArray == NULL");
            return 0L;
        }
    }

    j = 0;
    for (i = 0; i < size; i++) {
        cmsHPROFILE icc;
        cmsColorSpaceSignature cs;

        sTrans.j = ids[i];
        icc = sTrans.lcmsPf->pf;
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

    (*env)->ReleaseLongArrayElements(env, profileIDs, ids, 0);

    if (sTrans.xf == NULL) {
        J2dRlsTraceLn(J2D_TRACE_ERROR, "LCMS_createNativeTransform: "
                                       "sTrans.xf == NULL");
        if ((*env)->ExceptionOccurred(env) == NULL) {
            JNU_ThrowByName(env, "java/awt/color/CMMException",
                            "Cannot get color transform");
        }
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
 * Signature: ([B,Lsun/java2d/cmm/lcms/LCMSProfile;)V
 */
JNIEXPORT jlong JNICALL Java_sun_java2d_cmm_lcms_LCMS_loadProfileNative
  (JNIEnv *env, jobject obj, jbyteArray data, jobject disposerRef)
{
    jbyte* dataArray;
    jint dataSize;
    storeID_t sProf;
    cmsHPROFILE pf;

    if (JNU_IsNull(env, data)) {
        JNU_ThrowIllegalArgumentException(env, "Invalid profile data");
        return 0L;
    }

    sProf.j = 0L;

    dataArray = (*env)->GetByteArrayElements (env, data, 0);
    dataSize = (*env)->GetArrayLength (env, data);

    if (dataArray == NULL) {
        JNU_ThrowIllegalArgumentException(env, "Invalid profile data");
        return 0L;
    }

    pf = cmsOpenProfileFromMem((const void *)dataArray,
                                     (cmsUInt32Number) dataSize);

    (*env)->ReleaseByteArrayElements (env, data, dataArray, 0);

    if (pf == NULL) {
        JNU_ThrowIllegalArgumentException(env, "Invalid profile data");
    } else {
        /* Sanity check: try to save the profile in order
         * to force basic validation.
         */
        cmsUInt32Number pfSize = 0;
        if (!cmsSaveProfileToMem(pf, NULL, &pfSize) ||
            pfSize < sizeof(cmsICCHeader))
        {
            JNU_ThrowIllegalArgumentException(env, "Invalid profile data");

            cmsCloseProfile(pf);
            pf = NULL;
        }
    }

    if (pf != NULL) {
        // create profile holder
        sProf.lcmsPf = (lcmsProfile_p)malloc(sizeof(lcmsProfile_t));
        if (sProf.lcmsPf != NULL) {
            // register the disposer record
            sProf.lcmsPf->pf = pf;
            Disposer_AddRecord(env, disposerRef, LCMS_freeProfile, sProf.j);
        } else {
            cmsCloseProfile(pf);
        }
    }

    return sProf.j;
}

/*
 * Class:     sun_java2d_cmm_lcms_LCMS
 * Method:    getProfileSizeNative
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_sun_java2d_cmm_lcms_LCMS_getProfileSizeNative
  (JNIEnv *env, jobject obj, jlong id)
{
    storeID_t sProf;
    cmsUInt32Number pfSize = 0;
    sProf.j = id;

    if (cmsSaveProfileToMem(sProf.lcmsPf->pf, NULL, &pfSize) && ((jint)pfSize > 0)) {
        return (jint)pfSize;
    } else {
      JNU_ThrowByName(env, "java/awt/color/CMMException",
                      "Can not access specified profile.");
        return -1;
    }
}

/*
 * Class:     sun_java2d_cmm_lcms_LCMS
 * Method:    getProfileDataNative
 * Signature: (J[B)V
 */
JNIEXPORT void JNICALL Java_sun_java2d_cmm_lcms_LCMS_getProfileDataNative
  (JNIEnv *env, jobject obj, jlong id, jbyteArray data)
{
    storeID_t sProf;
    jint size;
    jbyte* dataArray;
    cmsUInt32Number pfSize = 0;
    cmsBool status;

    sProf.j = id;

    // determine actual profile size
    if (!cmsSaveProfileToMem(sProf.lcmsPf->pf, NULL, &pfSize)) {
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

    status = cmsSaveProfileToMem(sProf.lcmsPf->pf, dataArray, &pfSize);

    (*env)->ReleaseByteArrayElements (env, data, dataArray, 0);

    if (!status) {
        JNU_ThrowByName(env, "java/awt/color/CMMException",
                        "Can not access specified profile.");
        return;
    }
}

/* Get profile header info */
static cmsBool _getHeaderInfo(cmsHPROFILE pf, jbyte* pBuffer, jint bufferSize);
static cmsBool _setHeaderInfo(cmsHPROFILE pf, jbyte* pBuffer, jint bufferSize);
static cmsHPROFILE _writeCookedTag(cmsHPROFILE pfTarget, cmsTagSignature sig, jbyte *pData, jint size);


/*
 * Class:     sun_java2d_cmm_lcms_LCMS
 * Method:    getTagData
 * Signature: (JI[B)V
 */
JNIEXPORT jbyteArray JNICALL Java_sun_java2d_cmm_lcms_LCMS_getTagNative
  (JNIEnv *env, jobject obj, jlong id, jint tagSig)
{
    storeID_t sProf;
    TagSignature_t sig;
    cmsInt32Number tagSize;

    jbyte* dataArray = NULL;
    jbyteArray data = NULL;

    jint bufSize;

    sProf.j = id;
    sig.j = tagSig;

    if (tagSig == SigHead) {
        cmsBool status;

        // allocate java array
        bufSize = sizeof(cmsICCHeader);
        data = (*env)->NewByteArray(env, bufSize);

        if (data == NULL) {
            JNU_ThrowByName(env, "java/awt/color/CMMException",
                            "Unable to allocate buffer");
            return NULL;
        }

        dataArray = (*env)->GetByteArrayElements (env, data, 0);

        if (dataArray == NULL) {
           JNU_ThrowByName(env, "java/awt/color/CMMException",
                            "Unable to get buffer");
           return NULL;
        }

        status = _getHeaderInfo(sProf.lcmsPf->pf, dataArray, bufSize);

        (*env)->ReleaseByteArrayElements (env, data, dataArray, 0);

        if (!status) {
            JNU_ThrowByName(env, "java/awt/color/CMMException",
                            "ICC Profile header not found");
            return NULL;
        }

        return data;
    }

    if (cmsIsTag(sProf.lcmsPf->pf, sig.cms)) {
        tagSize = cmsReadRawTag(sProf.lcmsPf->pf, sig.cms, NULL, 0);
    } else {
        JNU_ThrowByName(env, "java/awt/color/CMMException",
                        "ICC profile tag not found");
        return NULL;
    }

    // allocate java array
    data = (*env)->NewByteArray(env, tagSize);
    if (data == NULL) {
        JNU_ThrowByName(env, "java/awt/color/CMMException",
                        "Unable to allocate buffer");
        return NULL;
    }

    dataArray = (*env)->GetByteArrayElements (env, data, 0);

    if (dataArray == NULL) {
        JNU_ThrowByName(env, "java/awt/color/CMMException",
                        "Unable to get buffer");
        return NULL;
    }

    bufSize = cmsReadRawTag(sProf.lcmsPf->pf, sig.cms, dataArray, tagSize);

    (*env)->ReleaseByteArrayElements (env, data, dataArray, 0);

    if (bufSize != tagSize) {
        JNU_ThrowByName(env, "java/awt/color/CMMException",
                        "Can not get tag data.");
        return NULL;
    }
    return data;
}

/*
 * Class:     sun_java2d_cmm_lcms_LCMS
 * Method:    setTagData
 * Signature: (JI[B)V
 */
JNIEXPORT void JNICALL Java_sun_java2d_cmm_lcms_LCMS_setTagDataNative
  (JNIEnv *env, jobject obj, jlong id, jint tagSig, jbyteArray data)
{
    storeID_t sProf;
    cmsHPROFILE pfReplace = NULL;

    TagSignature_t sig;
    cmsBool status = FALSE;
    jbyte* dataArray;
    int tagSize;

    sProf.j = id;
    sig.j = tagSig;

    if (JNU_IsNull(env, data)) {
        JNU_ThrowIllegalArgumentException(env, "Can not write tag data.");
        return;
    }

    tagSize =(*env)->GetArrayLength(env, data);

    dataArray = (*env)->GetByteArrayElements(env, data, 0);

    if (dataArray == NULL) {
        JNU_ThrowIllegalArgumentException(env, "Can not write tag data.");
        return;
    }

    if (tagSig == SigHead) {
        status  = _setHeaderInfo(sProf.lcmsPf->pf, dataArray, tagSize);
    } else {
        /*
        * New strategy for generic tags: create a place holder,
        * dump all existing tags there, dump externally supplied
        * tag, and return the new profile to the java.
        */
        pfReplace = _writeCookedTag(sProf.lcmsPf->pf, sig.cms, dataArray, tagSize);
        status = (pfReplace != NULL);
    }

    (*env)->ReleaseByteArrayElements(env, data, dataArray, 0);

    if (!status) {
        JNU_ThrowIllegalArgumentException(env, "Can not write tag data.");
    } else if (pfReplace != NULL) {
        cmsCloseProfile(sProf.lcmsPf->pf);
        sProf.lcmsPf->pf = pfReplace;
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
    int srcDType, dstDType;
    int srcOffset, srcNextRowOffset, dstOffset, dstNextRowOffset;
    int width, height, i;
    void* inputBuffer;
    void* outputBuffer;
    char* inputRow;
    char* outputRow;
    jobject srcData, dstData;
    jboolean srcAtOnce = JNI_FALSE, dstAtOnce = JNI_FALSE;

    srcOffset = (*env)->GetIntField (env, src, IL_offset_fID);
    srcNextRowOffset = (*env)->GetIntField (env, src, IL_nextRowOffset_fID);
    dstOffset = (*env)->GetIntField (env, dst, IL_offset_fID);
    dstNextRowOffset = (*env)->GetIntField (env, dst, IL_nextRowOffset_fID);
    width = (*env)->GetIntField (env, src, IL_width_fID);
    height = (*env)->GetIntField (env, src, IL_height_fID);

    srcAtOnce = (*env)->GetBooleanField(env, src, IL_imageAtOnce_fID);
    dstAtOnce = (*env)->GetBooleanField(env, dst, IL_imageAtOnce_fID);

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

    if (srcAtOnce && dstAtOnce) {
        cmsDoTransform(sTrans.xf, inputRow, outputRow, width * height);
    } else {
        for (i = 0; i < height; i++) {
            cmsDoTransform(sTrans.xf, inputRow, outputRow, width);
            inputRow += srcNextRowOffset;
            outputRow += dstNextRowOffset;
        }
    }

    releaseILData(env, inputBuffer, srcDType, srcData);
    releaseILData(env, outputBuffer, dstDType, dstData);
}

/*
 * Class:     sun_java2d_cmm_lcms_LCMS
 * Method:    getProfileID
 * Signature: (Ljava/awt/color/ICC_Profile;)Lsun/java2d/cmm/lcms/LCMSProfile
 */
JNIEXPORT jobject JNICALL Java_sun_java2d_cmm_lcms_LCMS_getProfileID
  (JNIEnv *env, jclass cls, jobject pf)
{
    jfieldID fid = (*env)->GetFieldID (env,
        (*env)->GetObjectClass(env, pf),
        "cmmProfile", "Lsun/java2d/cmm/Profile;");

    jclass clsLcmsProfile = (*env)->FindClass(env,
            "sun/java2d/cmm/lcms/LCMSProfile");

    jobject cmmProfile = (*env)->GetObjectField (env, pf, fid);

    if (JNU_IsNull(env, cmmProfile)) {
        return NULL;
    }
    if ((*env)->IsInstanceOf(env, cmmProfile, clsLcmsProfile)) {
        return cmmProfile;
    }
    return NULL;
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
    IL_imageAtOnce_fID = (*env)->GetFieldID (env, IL, "imageAtOnce", "Z");
    IL_nextRowOffset_fID = (*env)->GetFieldID (env, IL, "nextRowOffset", "I");
}

static cmsBool _getHeaderInfo(cmsHPROFILE pf, jbyte* pBuffer, jint bufferSize)
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

static cmsBool _setHeaderInfo(cmsHPROFILE pf, jbyte* pBuffer, jint bufferSize)
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

/* Returns new profile handler, if it was created successfully,
   NULL otherwise.
   */
static cmsHPROFILE _writeCookedTag(const cmsHPROFILE pfTarget,
                               const cmsTagSignature sig,
                               jbyte *pData, jint size)
{
    cmsUInt32Number pfSize = 0;
    const cmsInt32Number tagCount = cmsGetTagCount(pfTarget);
    cmsInt32Number i;
    cmsHPROFILE pfSanity = NULL;

    cmsICCHeader hdr = { 0 };

    cmsHPROFILE p = cmsCreateProfilePlaceholder(NULL);

    if (NULL == p) {
        return NULL;
    }

    // Populate the placeholder's header according to target profile
    hdr.flags = cmsGetHeaderFlags(pfTarget);
    hdr.renderingIntent = cmsGetHeaderRenderingIntent(pfTarget);
    hdr.manufacturer = cmsGetHeaderManufacturer(pfTarget);
    hdr.model = cmsGetHeaderModel(pfTarget);
    hdr.pcs = cmsGetPCS(pfTarget);
    hdr.colorSpace = cmsGetColorSpace(pfTarget);
    hdr.deviceClass = cmsGetDeviceClass(pfTarget);
    hdr.version = cmsGetEncodedICCversion(pfTarget);
    cmsGetHeaderAttributes(pfTarget, &hdr.attributes);
    cmsGetHeaderProfileID(pfTarget, (cmsUInt8Number*)&hdr.profileID);

    cmsSetHeaderFlags(p, hdr.flags);
    cmsSetHeaderManufacturer(p, hdr.manufacturer);
    cmsSetHeaderModel(p, hdr.model);
    cmsSetHeaderAttributes(p, hdr.attributes);
    cmsSetHeaderProfileID(p, (cmsUInt8Number*)&(hdr.profileID));
    cmsSetHeaderRenderingIntent(p, hdr.renderingIntent);
    cmsSetPCS(p, hdr.pcs);
    cmsSetColorSpace(p, hdr.colorSpace);
    cmsSetDeviceClass(p, hdr.deviceClass);
    cmsSetEncodedICCversion(p, hdr.version);

    // now write the user supplied tag
    if (size <= 0 || !cmsWriteRawTag(p, sig, pData, size)) {
        cmsCloseProfile(p);
        return NULL;
    }

    // copy tags from the original profile
    for (i = 0; i < tagCount; i++) {
        cmsBool isTagReady = FALSE;
        const cmsTagSignature s = cmsGetTagSignature(pfTarget, i);
        const cmsInt32Number tagSize = cmsReadRawTag(pfTarget, s, NULL, 0);

        if (s == sig) {
            // skip the user supplied tag
            continue;
        }

        // read raw tag from the original profile
        if (tagSize > 0) {
            cmsUInt8Number* buf = (cmsUInt8Number*)malloc(tagSize);
            if (buf != NULL) {
                if (tagSize ==  cmsReadRawTag(pfTarget, s, buf, tagSize)) {
                    // now we are ready to write the tag
                    isTagReady = cmsWriteRawTag(p, s, buf, tagSize);
                }
                free(buf);
            }
        }

        if (!isTagReady) {
            cmsCloseProfile(p);
            return NULL;
        }
    }

    // now we have all tags moved to the new profile.
    // do some sanity checks: write it to a memory buffer and read again.
    if (cmsSaveProfileToMem(p, NULL, &pfSize)) {
        void* buf = malloc(pfSize);
        if (buf != NULL) {
            // load raw profile data into the buffer
            if (cmsSaveProfileToMem(p, buf, &pfSize)) {
                pfSanity = cmsOpenProfileFromMem(buf, pfSize);
            }
            free(buf);
        }
    }

    if (pfSanity == NULL) {
        // for some reason, we failed to save and read the updated profile
        // It likely indicates that the profile is not correct, so we report
        // a failure here.
        cmsCloseProfile(p);
        p =  NULL;
    } else {
        // do final check whether we can read and handle the the target tag.
        const void* pTag = cmsReadTag(pfSanity, sig);
        if (pTag == NULL) {
            // the tag can not be cooked
            cmsCloseProfile(p);
            p = NULL;
        }
        cmsCloseProfile(pfSanity);
        pfSanity = NULL;
    }

    return p;
}
