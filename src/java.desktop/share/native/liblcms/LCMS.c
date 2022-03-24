/*
 * Copyright (c) 2007, 2022, Oracle and/or its affiliates. All rights reserved.
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
#include <lcms2.h>
#include <lcms2_plugin.h>
#include "jlong.h"

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

typedef union {
    cmsTagSignature cms;
    jint j;
} TagSignature_t, *TagSignature_p;

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
    if (!(*env)->ExceptionCheck(env)) { // errorHandler may throw it before
        JNU_ThrowByName(env, "java/awt/color/CMMException", errMsg);
    }
}

JNIEXPORT jint JNICALL DEF_JNI_OnLoad(JavaVM *jvm, void *reserved) {
    javaVM = jvm;

    cmsSetLogErrorHandler(errorHandler);
    return JNI_VERSION_1_6;
}

void LCMS_freeProfile(JNIEnv *env, jlong ptr) {
    lcmsProfile_p p = (lcmsProfile_p)jlong_to_ptr(ptr);

    if (p != NULL) {
        if (p->pf != NULL) {
            cmsCloseProfile(p->pf);
        }
        free(p);
    }
}

void LCMS_freeTransform(JNIEnv *env, jlong ID)
{
    cmsHTRANSFORM sTrans = jlong_to_ptr(ID);
    /* Passed ID is always valid native ref so there is no check for zero */
    cmsDeleteTransform(sTrans);
}

/*
 * Throw an IllegalArgumentException and init the cause.
 */
static void ThrowIllegalArgumentException(JNIEnv *env, const char *msg) {
    jthrowable cause = (*env)->ExceptionOccurred(env);
    if (cause != NULL) {
        (*env)->ExceptionClear(env);
    }
    jstring str = JNU_NewStringPlatform(env, msg);
    if (str != NULL) {
        jobject iae = JNU_NewObjectByName(env,
                                "java/lang/IllegalArgumentException",
                                "(Ljava/lang/String;Ljava/lang/Throwable;)V",
                                str, cause);
        if (iae != NULL) {
            (*env)->Throw(env, iae);
        }
    }
}

/*
 * Class:     sun_java2d_cmm_lcms_LCMS
 * Method:    createNativeTransform
 * Signature: ([JIIZIZLjava/lang/Object;)J
 */
JNIEXPORT jlong JNICALL Java_sun_java2d_cmm_lcms_LCMS_createNativeTransform
  (JNIEnv *env, jclass cls, jlongArray profileIDs, jint renderType,
   jint inFormatter, jboolean isInIntPacked,
   jint outFormatter, jboolean isOutIntPacked, jobject disposerRef)
{
    cmsHPROFILE _iccArray[DF_ICC_BUF_SIZE];
    cmsHPROFILE *iccArray = &_iccArray[0];
    cmsHTRANSFORM sTrans = NULL;
    int i, j, size;
    jlong* ids;

    size = (*env)->GetArrayLength (env, profileIDs);
    ids = (*env)->GetLongArrayElements(env, profileIDs, 0);
    if (ids == NULL) {
        // An exception should have already been thrown.
        return 0L;
    }

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
        cmsColorSpaceSignature cs;
        lcmsProfile_p profilePtr = (lcmsProfile_p)jlong_to_ptr(ids[i]);
        cmsHPROFILE icc = profilePtr->pf;

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

    sTrans = cmsCreateMultiprofileTransform(iccArray, j,
        inFormatter, outFormatter, renderType, cmsFLAGS_COPY_ALPHA);

    (*env)->ReleaseLongArrayElements(env, profileIDs, ids, 0);

    if (sTrans == NULL) {
        J2dRlsTraceLn(J2D_TRACE_ERROR, "LCMS_createNativeTransform: "
                                       "sTrans == NULL");
        if (!(*env)->ExceptionCheck(env)) { // errorHandler may throw it
            JNU_ThrowByName(env, "java/awt/color/CMMException",
                            "Cannot get color transform");
        }
    } else {
        Disposer_AddRecord(env, disposerRef, LCMS_freeTransform, ptr_to_jlong(sTrans));
    }

    if (iccArray != &_iccArray[0]) {
        free(iccArray);
    }
    return ptr_to_jlong(sTrans);
}


/*
 * Class:     sun_java2d_cmm_lcms_LCMS
 * Method:    loadProfileNative
 * Signature: ([BLjava/lang/Object;)J
 */
JNIEXPORT jlong JNICALL Java_sun_java2d_cmm_lcms_LCMS_loadProfileNative
  (JNIEnv *env, jclass cls, jbyteArray data, jobject disposerRef)
{
    jbyte* dataArray;
    jint dataSize;
    lcmsProfile_p sProf = NULL;
    cmsHPROFILE pf;

    if (JNU_IsNull(env, data)) {
        ThrowIllegalArgumentException(env, "Invalid profile data");
        return 0L;
    }

    dataArray = (*env)->GetByteArrayElements (env, data, 0);
    if (dataArray == NULL) {
        // An exception should have already been thrown.
        return 0L;
    }

    dataSize = (*env)->GetArrayLength (env, data);

    pf = cmsOpenProfileFromMem((const void *)dataArray,
                                     (cmsUInt32Number) dataSize);

    (*env)->ReleaseByteArrayElements (env, data, dataArray, 0);

    if (pf == NULL) {
        ThrowIllegalArgumentException(env, "Invalid profile data");
    } else {
        /* Sanity check: try to save the profile in order
         * to force basic validation.
         */
        cmsUInt32Number pfSize = 0;
        if (!cmsSaveProfileToMem(pf, NULL, &pfSize) ||
            pfSize < sizeof(cmsICCHeader))
        {
            ThrowIllegalArgumentException(env, "Invalid profile data");
            cmsCloseProfile(pf);
            pf = NULL;
        }
    }

    if (pf != NULL) {
        // create profile holder
        sProf = (lcmsProfile_p)malloc(sizeof(lcmsProfile_t));
        if (sProf != NULL) {
            // register the disposer record
            sProf->pf = pf;
            Disposer_AddRecord(env, disposerRef, LCMS_freeProfile, ptr_to_jlong(sProf));
        } else {
            cmsCloseProfile(pf);
        }
    }

    return ptr_to_jlong(sProf);
}

/*
 * Class:     sun_java2d_cmm_lcms_LCMS
 * Method:    getProfileDataNative
 * Signature: (J)[B
 */
JNIEXPORT jbyteArray JNICALL Java_sun_java2d_cmm_lcms_LCMS_getProfileDataNative
  (JNIEnv *env, jclass cls, jlong id)
{
    lcmsProfile_p sProf = (lcmsProfile_p)jlong_to_ptr(id);
    cmsUInt32Number pfSize = 0;

    // determine actual profile size
    if (!cmsSaveProfileToMem(sProf->pf, NULL, &pfSize)) {
        if (!(*env)->ExceptionCheck(env)) { // errorHandler may throw it
            JNU_ThrowByName(env, "java/awt/color/CMMException",
                            "Can not access specified profile.");
        }
        return NULL;
    }

    jbyteArray data = (*env)->NewByteArray(env, pfSize);
    if (data == NULL) {
        // An exception should have already been thrown.
        return NULL;
    }

    jbyte* dataArray = (*env)->GetByteArrayElements(env, data, 0);
    if (dataArray == NULL) {
        // An exception should have already been thrown.
        return NULL;
    }

    cmsBool status = cmsSaveProfileToMem(sProf->pf, dataArray, &pfSize);

    (*env)->ReleaseByteArrayElements(env, data, dataArray, 0);

    if (!status) {
        if (!(*env)->ExceptionCheck(env)) { // errorHandler may throw it
            JNU_ThrowByName(env, "java/awt/color/CMMException",
                            "Can not access specified profile.");
        }
        return NULL;
    }
    return data;
}

/* Get profile header info */
static cmsBool _getHeaderInfo(cmsHPROFILE pf, jbyte* pBuffer, jint bufferSize);
static cmsBool _setHeaderInfo(cmsHPROFILE pf, jbyte* pBuffer, jint bufferSize);
static cmsHPROFILE _writeCookedTag(cmsHPROFILE pfTarget, cmsTagSignature sig, jbyte *pData, jint size);


/*
 * Class:     sun_java2d_cmm_lcms_LCMS
 * Method:    getTagNative
 * Signature: (JI)[B
 */
JNIEXPORT jbyteArray JNICALL Java_sun_java2d_cmm_lcms_LCMS_getTagNative
  (JNIEnv *env, jclass cls, jlong id, jint tagSig)
{
    lcmsProfile_p sProf = (lcmsProfile_p)jlong_to_ptr(id);
    TagSignature_t sig;
    cmsUInt32Number tagSize;

    jbyte* dataArray = NULL;
    jbyteArray data = NULL;

    cmsUInt32Number bufSize;

    sig.j = tagSig;

    if (tagSig == SigHead) {
        cmsBool status;

        // allocate java array
        bufSize = sizeof(cmsICCHeader);
        data = (*env)->NewByteArray(env, bufSize);

        if (data == NULL) {
            // An exception should have already been thrown.
            return NULL;
        }

        dataArray = (*env)->GetByteArrayElements (env, data, 0);

        if (dataArray == NULL) {
            // An exception should have already been thrown.
            return NULL;
        }

        status = _getHeaderInfo(sProf->pf, dataArray, bufSize);

        (*env)->ReleaseByteArrayElements (env, data, dataArray, 0);

        if (!status) {
            if (!(*env)->ExceptionCheck(env)) { // errorHandler may throw it
                JNU_ThrowByName(env, "java/awt/color/CMMException",
                                "ICC Profile header not found");
            }
            return NULL;
        }

        return data;
    }

    if (cmsIsTag(sProf->pf, sig.cms)) {
        tagSize = cmsReadRawTag(sProf->pf, sig.cms, NULL, 0);
    } else {
        if (!(*env)->ExceptionCheck(env)) { // errorHandler may throw it
            JNU_ThrowByName(env, "java/awt/color/CMMException",
                            "ICC profile tag not found");
        }
        return NULL;
    }

    // allocate java array
    data = (*env)->NewByteArray(env, tagSize);
    if (data == NULL) {
        // An exception should have already been thrown.
        return NULL;
    }

    dataArray = (*env)->GetByteArrayElements (env, data, 0);

    if (dataArray == NULL) {
        // An exception should have already been thrown.
        return NULL;
    }

    bufSize = cmsReadRawTag(sProf->pf, sig.cms, dataArray, tagSize);

    (*env)->ReleaseByteArrayElements (env, data, dataArray, 0);

    if (bufSize != tagSize) {
        if (!(*env)->ExceptionCheck(env)) { // errorHandler may throw it
            JNU_ThrowByName(env, "java/awt/color/CMMException",
                            "Can not get tag data.");
        }
        return NULL;
    }
    return data;
}

/*
 * Class:     sun_java2d_cmm_lcms_LCMS
 * Method:    setTagDataNative
 * Signature: (JI[B)V
 */
JNIEXPORT void JNICALL Java_sun_java2d_cmm_lcms_LCMS_setTagDataNative
  (JNIEnv *env, jclass cls, jlong id, jint tagSig, jbyteArray data)
{
    lcmsProfile_p sProf = (lcmsProfile_p)jlong_to_ptr(id);
    cmsHPROFILE pfReplace = NULL;

    TagSignature_t sig;
    cmsBool status = FALSE;
    jbyte* dataArray;
    int tagSize;

    sig.j = tagSig;

    if (JNU_IsNull(env, data)) {
        ThrowIllegalArgumentException(env, "Can not write tag data.");
        return;
    }

    tagSize =(*env)->GetArrayLength(env, data);

    dataArray = (*env)->GetByteArrayElements(env, data, 0);

    if (dataArray == NULL) {
        // An exception should have already been thrown.
        return;
    }

    if (tagSig == SigHead) {
        status  = _setHeaderInfo(sProf->pf, dataArray, tagSize);
    } else {
        /*
        * New strategy for generic tags: create a place holder,
        * dump all existing tags there, dump externally supplied
        * tag, and return the new profile to the java.
        */
        pfReplace = _writeCookedTag(sProf->pf, sig.cms, dataArray, tagSize);
        status = (pfReplace != NULL);
    }

    (*env)->ReleaseByteArrayElements(env, data, dataArray, 0);

    if (!status) {
        ThrowIllegalArgumentException(env, "Can not write tag data.");
    } else if (pfReplace != NULL) {
        cmsCloseProfile(sProf->pf);
        sProf->pf = pfReplace;
    }
}

static void *getILData(JNIEnv *env, jobject data, jint type) {
    switch (type) {
        case DT_BYTE:
            return (*env)->GetByteArrayElements(env, data, 0);
        case DT_SHORT:
            return (*env)->GetShortArrayElements(env, data, 0);
        case DT_INT:
            return (*env)->GetIntArrayElements(env, data, 0);
        case DT_DOUBLE:
            return (*env)->GetDoubleArrayElements(env, data, 0);
        default:
            return NULL;
    }
}

static void releaseILData(JNIEnv *env, void *pData, jint type, jobject data,
                          jint mode) {
    switch (type) {
        case DT_BYTE:
            (*env)->ReleaseByteArrayElements(env, data, (jbyte *) pData, mode);
            break;
        case DT_SHORT:
            (*env)->ReleaseShortArrayElements(env, data, (jshort *) pData, mode);
            break;
        case DT_INT:
            (*env)->ReleaseIntArrayElements(env, data, (jint *) pData, mode);
            break;
        case DT_DOUBLE:
            (*env)->ReleaseDoubleArrayElements(env, data, (jdouble *) pData, mode);
            break;
    }
}

/*
 * Class:     sun_java2d_cmm_lcms_LCMS
 * Method:    colorConvert
 * Signature: (JIIIIIIZZLjava/lang/Object;Ljava/lang/Object;)V
 */
JNIEXPORT void JNICALL Java_sun_java2d_cmm_lcms_LCMS_colorConvert
  (JNIEnv *env, jclass cls, jlong ID, jint width, jint height, jint srcOffset,
   jint srcNextRowOffset, jint dstOffset, jint dstNextRowOffset,
   jboolean srcAtOnce, jboolean dstAtOnce,
   jobject srcData, jobject dstData, jint srcDType, jint dstDType)
{
    cmsHTRANSFORM sTrans = jlong_to_ptr(ID);

    if (sTrans == NULL) {
        J2dRlsTraceLn(J2D_TRACE_ERROR, "LCMS_colorConvert: transform == NULL");
        JNU_ThrowByName(env, "java/awt/color/CMMException",
                        "Cannot get color transform");
        return;
    }

    void *inputBuffer = getILData(env, srcData, srcDType);
    if (inputBuffer == NULL) {
        J2dRlsTraceLn(J2D_TRACE_ERROR, "");
        // An exception should have already been thrown.
        return;
    }

    void *outputBuffer = getILData(env, dstData, dstDType);
    if (outputBuffer == NULL) {
        releaseILData(env, inputBuffer, srcDType, srcData, JNI_ABORT);
        // An exception should have already been thrown.
        return;
    }

    char *inputRow = (char *) inputBuffer + srcOffset;
    char *outputRow = (char *) outputBuffer + dstOffset;

    if (srcAtOnce && dstAtOnce) {
        cmsDoTransform(sTrans, inputRow, outputRow, width * height);
    } else {
        for (int i = 0; i < height; i++) {
            cmsDoTransform(sTrans, inputRow, outputRow, width);
            inputRow += srcNextRowOffset;
            outputRow += dstNextRowOffset;
        }
    }

    releaseILData(env, inputBuffer, srcDType, srcData, JNI_ABORT);
    releaseILData(env, outputBuffer, dstDType, dstData, 0);
}

/*
 * Class:     sun_java2d_cmm_lcms_LCMS
 * Method:    getProfileID
 * Signature: (Ljava/awt/color/ICC_Profile;)Lsun/java2d/cmm/lcms/LCMSProfile;
 */
JNIEXPORT jobject JNICALL Java_sun_java2d_cmm_lcms_LCMS_getProfileID
  (JNIEnv *env, jclass cls, jobject pf)
{
    if (pf == NULL) {
        return NULL;
    }
    jclass pcls = (*env)->GetObjectClass(env, pf);
    if (pcls == NULL) {
        return NULL;
    }
    jmethodID mid = (*env)->GetMethodID(env, pcls, "cmmProfile",
                                        "()Lsun/java2d/cmm/Profile;");
    if (mid == NULL) {
        return NULL;
    }
    jobject cmmProfile = (*env)->CallObjectMethod(env, pf, mid);
    if ((*env)->ExceptionCheck(env)) {
        return NULL;
    }
    jclass lcmsPCls = (*env)->FindClass(env, "sun/java2d/cmm/lcms/LCMSProfile");
    if (lcmsPCls == NULL) {
        return NULL;
    }
    if ((*env)->IsInstanceOf(env, cmmProfile, lcmsPCls)) {
        return cmmProfile;
    }
    return NULL;
}

static cmsBool _getHeaderInfo(cmsHPROFILE pf, jbyte* pBuffer, jint bufferSize)
{
  cmsUInt32Number pfSize = 0;
  cmsUInt8Number* pfBuffer = NULL;
  cmsBool status = FALSE;

  if (!cmsSaveProfileToMem(pf, NULL, &pfSize) ||
      pfSize < sizeof(cmsICCHeader) ||
      bufferSize < (jint)sizeof(cmsICCHeader))
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
  cmsICCHeader pfHeader;

  if (pBuffer == NULL || bufferSize < (jint)sizeof(cmsICCHeader)) {
    return FALSE;
  }

  memcpy(&pfHeader, pBuffer, sizeof(cmsICCHeader));

  // now set header fields, which we can access using the lcms2 public API
  cmsSetHeaderFlags(pf, _cmsAdjustEndianess32(pfHeader.flags));
  cmsSetHeaderManufacturer(pf, _cmsAdjustEndianess32(pfHeader.manufacturer));
  cmsSetHeaderModel(pf, _cmsAdjustEndianess32(pfHeader.model));
  cmsUInt64Number attributes;
  _cmsAdjustEndianess64(&attributes, &pfHeader.attributes);
  cmsSetHeaderAttributes(pf, attributes);
  cmsSetHeaderProfileID(pf, (cmsUInt8Number*)&(pfHeader.profileID));
  cmsSetHeaderRenderingIntent(pf, _cmsAdjustEndianess32(pfHeader.renderingIntent));
  cmsSetPCS(pf, _cmsAdjustEndianess32(pfHeader.pcs));
  cmsSetColorSpace(pf, _cmsAdjustEndianess32(pfHeader.colorSpace));
  cmsSetDeviceClass(pf, _cmsAdjustEndianess32(pfHeader.deviceClass));
  cmsSetEncodedICCversion(pf, _cmsAdjustEndianess32(pfHeader.version));

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

    cmsICCHeader hdr;

    cmsHPROFILE p = cmsCreateProfilePlaceholder(NULL);

    if (NULL == p) {
        return NULL;
    }
    memset(&hdr, 0, sizeof(cmsICCHeader));

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
        const cmsUInt32Number tagSize = cmsReadRawTag(pfTarget, s, NULL, 0);

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
    void* buf = NULL;
    if (cmsSaveProfileToMem(p, NULL, &pfSize)) {
        buf = malloc(pfSize);
        if (buf != NULL) {
            // load raw profile data into the buffer
            if (cmsSaveProfileToMem(p, buf, &pfSize)) {
                pfSanity = cmsOpenProfileFromMem(buf, pfSize);
            }
        }
    }

    cmsCloseProfile(p); // No longer needed.

    if (pfSanity == NULL) {
        // for some reason, we failed to save and read the updated profile
        // It likely indicates that the profile is not correct, so we report
        // a failure here.
        free(buf);
        return NULL;
    } else {
        // do final check whether we can read and handle the target tag.
        const void* pTag = cmsReadTag(pfSanity, sig);
        if (pTag == NULL) {
            // the tag can not be cooked
            free(buf);
            cmsCloseProfile(pfSanity);
            return NULL;
        }
        // The profile we used for sanity checking needs to be returned
        // since the one we updated is raw - not cooked.
        // Except we want to re-open it since the call to cmsReadTag()
        // means we may not get back the same bytes as we set.
        // Whilst this may change later anyway, we can at least prevent
        // it from happening immediately.
        cmsCloseProfile(pfSanity);
        pfSanity = cmsOpenProfileFromMem(buf, pfSize);
        free(buf);
        return pfSanity;
    }
}
