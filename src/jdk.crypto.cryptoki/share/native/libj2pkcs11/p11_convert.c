/*
 * Copyright (c) 2003, 2018, Oracle and/or its affiliates. All rights reserved.
 */

/* Copyright  (c) 2002 Graz University of Technology. All rights reserved.
 *
 * Redistribution and use in  source and binary forms, with or without
 * modification, are permitted  provided that the following conditions are met:
 *
 * 1. Redistributions of  source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in  binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * 3. The end-user documentation included with the redistribution, if any, must
 *    include the following acknowledgment:
 *
 *    "This product includes software developed by IAIK of Graz University of
 *     Technology."
 *
 *    Alternately, this acknowledgment may appear in the software itself, if
 *    and wherever such third-party acknowledgments normally appear.
 *
 * 4. The names "Graz University of Technology" and "IAIK of Graz University of
 *    Technology" must not be used to endorse or promote products derived from
 *    this software without prior written permission.
 *
 * 5. Products derived from this software may not be called
 *    "IAIK PKCS Wrapper", nor may "IAIK" appear in their name, without prior
 *    written permission of Graz University of Technology.
 *
 *  THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESSED OR IMPLIED
 *  WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 *  WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 *  PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE LICENSOR BE
 *  LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY,
 *  OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 *  PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA,
 *  OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 *  ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 *  OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
 *  OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 *  POSSIBILITY  OF SUCH DAMAGE.
 */

/*
 * pkcs11wrapper.c
 * 18.05.2001
 *
 * This is the implementation of the native functions of the Java to PKCS#11 interface.
 * All function use some helper functions to convert the JNI types to PKCS#11 types.
 *
 * @author Karl Scheibelhofer <Karl.Scheibelhofer@iaik.at>
 * @author Martin Schlaeffer <schlaeff@sbox.tugraz.at>
 */


#include "pkcs11wrapper.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <assert.h>

#include "sun_security_pkcs11_wrapper_PKCS11.h"

/* declare file private functions */

void jMechanismParameterToCKMechanismParameterSlow(JNIEnv *env, jobject jParam, CK_VOID_PTR *ckpParamPtr, CK_ULONG *ckpLength);


/*
 * converts a pointer to a CK_DATE structure into a Java CK_DATE Object.
 *
 * @param env - used to call JNI funktions to create the new Java object
 * @param ckpValue - the pointer to the CK_DATE structure
 * @return - the new Java CK_DATE object
 */
jobject ckDatePtrToJDateObject(JNIEnv *env, const CK_DATE *ckpDate)
{
    jclass jDateClass;
    jmethodID jCtrId;
    jobject jDateObject;
    jcharArray jYear;
    jcharArray jMonth;
    jcharArray jDay;

    /* load CK_DATE class */
    jDateClass = (*env)->FindClass(env, CLASS_DATE);
    if (jDateClass == NULL) { return NULL; }

    /* load CK_DATE constructor */
    jCtrId = (*env)->GetMethodID(env, jDateClass, "<init>", "([C[C[C)V");
    if (jCtrId == NULL) { return NULL; }

    /* prep all fields */
    jYear = ckCharArrayToJCharArray(env, (CK_CHAR_PTR)(ckpDate->year), 4);
    if (jYear == NULL) { return NULL; }
    jMonth = ckCharArrayToJCharArray(env, (CK_CHAR_PTR)(ckpDate->month), 2);
    if (jMonth == NULL) { return NULL; }
    jDay = ckCharArrayToJCharArray(env, (CK_CHAR_PTR)(ckpDate->day), 2);
    if (jDay == NULL) { return NULL; }

    /* create new CK_DATE object */
    jDateObject =
      (*env)->NewObject(env, jDateClass, jCtrId, jYear, jMonth, jDay);
    if (jDateObject == NULL) { return NULL; }

    /* free local references */
    (*env)->DeleteLocalRef(env, jDateClass);
    (*env)->DeleteLocalRef(env, jYear);
    (*env)->DeleteLocalRef(env, jMonth);
    (*env)->DeleteLocalRef(env, jDay);

    return jDateObject ;
}

/*
 * converts a pointer to a CK_VERSION structure into a Java CK_VERSION Object.
 *
 * @param env - used to call JNI funktions to create the new Java object
 * @param ckpVersion - the pointer to the CK_VERSION structure
 * @return - the new Java CK_VERSION object
 */
jobject ckVersionPtrToJVersion(JNIEnv *env, const CK_VERSION_PTR ckpVersion)
{
    jclass jVersionClass;
    jmethodID jCtrId;
    jobject jVersionObject;
    jint jMajor;
    jint jMinor;

    /* load CK_VERSION class */
    jVersionClass = (*env)->FindClass(env, CLASS_VERSION);
    if (jVersionClass == NULL) { return NULL; }

    /* load CK_VERSION constructor */
    jCtrId = (*env)->GetMethodID(env, jVersionClass, "<init>", "(II)V");
    if (jCtrId == NULL) { return NULL; }

    /* prep both fields */
    jMajor = ckpVersion->major;
    jMinor = ckpVersion->minor;

    /* create new CK_VERSION object */
    jVersionObject =
      (*env)->NewObject(env, jVersionClass, jCtrId, jMajor, jMinor);
    if (jVersionObject == NULL) { return NULL; }

    /* free local references */
    (*env)->DeleteLocalRef(env, jVersionClass);

    return jVersionObject ;
}

/*
 * converts a pointer to a CK_SESSION_INFO structure into a Java CK_SESSION_INFO Object.
 *
 * @param env - used to call JNI funktions to create the new Java object
 * @param ckpSessionInfo - the pointer to the CK_SESSION_INFO structure
 * @return - the new Java CK_SESSION_INFO object
 */
jobject ckSessionInfoPtrToJSessionInfo(JNIEnv *env, const CK_SESSION_INFO_PTR ckpSessionInfo)
{
    jclass jSessionInfoClass;
    jmethodID jCtrId;
    jobject jSessionInfoObject;
    jlong jSlotID;
    jlong jState;
    jlong jFlags;
    jlong jDeviceError;

    /* load CK_SESSION_INFO class */
    jSessionInfoClass = (*env)->FindClass(env, CLASS_SESSION_INFO);
    if (jSessionInfoClass == NULL) { return NULL; }

    /* load CK_SESSION_INFO constructor */
    jCtrId = (*env)->GetMethodID(env, jSessionInfoClass, "<init>", "(JJJJ)V");
    if (jCtrId == NULL) { return NULL; }

    /* prep all fields */
    jSlotID = ckULongToJLong(ckpSessionInfo->slotID);
    jState = ckULongToJLong(ckpSessionInfo->state);
    jFlags = ckULongToJLong(ckpSessionInfo->flags);
    jDeviceError = ckULongToJLong(ckpSessionInfo->ulDeviceError);

    /* create new CK_SESSION_INFO object */
    jSessionInfoObject =
      (*env)->NewObject(env, jSessionInfoClass, jCtrId, jSlotID, jState,
                        jFlags, jDeviceError);
    if (jSessionInfoObject == NULL) { return NULL; }

    /* free local references */
    (*env)->DeleteLocalRef(env, jSessionInfoClass);

    return jSessionInfoObject ;
}

/*
 * converts a pointer to a CK_ATTRIBUTE structure into a Java CK_ATTRIBUTE Object.
 *
 * @param env - used to call JNI funktions to create the new Java object
 * @param ckpAttribute - the pointer to the CK_ATTRIBUTE structure
 * @return - the new Java CK_ATTRIBUTE object
 */
jobject ckAttributePtrToJAttribute(JNIEnv *env, const CK_ATTRIBUTE_PTR ckpAttribute)
{
    jclass jAttributeClass;
    jmethodID jCtrId;
    jobject jAttributeObject;
    jlong jType;
    jobject jPValue = NULL;

    jAttributeClass = (*env)->FindClass(env, CLASS_ATTRIBUTE);
    if (jAttributeClass == NULL) { return NULL; }

    /* load CK_INFO constructor */
    jCtrId = (*env)->GetMethodID(env, jAttributeClass, "<init>", "(JLjava/lang/Object;)V");
    if (jCtrId == NULL) { return NULL; }

    /* prep both fields */
    jType = ckULongToJLong(ckpAttribute->type);
    jPValue = ckAttributeValueToJObject(env, ckpAttribute);
    if ((*env)->ExceptionCheck(env)) { return NULL; }

    /* create new CK_ATTRIBUTE object */
    jAttributeObject =
      (*env)->NewObject(env, jAttributeClass, jCtrId, jType, jPValue);
    if (jAttributeObject == NULL) { return NULL; }

    /* free local references */
    (*env)->DeleteLocalRef(env, jAttributeClass);
    (*env)->DeleteLocalRef(env, jPValue);

    return jAttributeObject;
}


/*
 * converts a Java CK_VERSION object into a pointer to a CK_VERSION structure
 *
 * @param env - used to call JNI funktions to get the values out of the Java object
 * @param jVersion - the Java CK_VERSION object to convert
 * @return - the pointer to the new CK_VERSION structure
 */
CK_VERSION_PTR jVersionToCKVersionPtr(JNIEnv *env, jobject jVersion)
{
    CK_VERSION_PTR ckpVersion;
    jclass jVersionClass;
    jfieldID jFieldID;
    jbyte jMajor, jMinor;

    if (jVersion == NULL) {
        return NULL;
    }

    /* get CK_VERSION class */
    jVersionClass = (*env)->GetObjectClass(env, jVersion);
    if (jVersionClass == NULL) { return NULL; }

    /* get Major */
    jFieldID = (*env)->GetFieldID(env, jVersionClass, "major", "B");
    if (jFieldID == NULL) { return NULL; }
    jMajor = (*env)->GetByteField(env, jVersion, jFieldID);

    /* get Minor */
    jFieldID = (*env)->GetFieldID(env, jVersionClass, "minor", "B");
    if (jFieldID == NULL) { return NULL; }
    jMinor = (*env)->GetByteField(env, jVersion, jFieldID);

    /* allocate memory for CK_VERSION pointer */
    ckpVersion = (CK_VERSION_PTR) malloc(sizeof(CK_VERSION));
    if (ckpVersion == NULL) {
        throwOutOfMemoryError(env, 0);
        return NULL;
    }
    ckpVersion->major = jByteToCKByte(jMajor);
    ckpVersion->minor = jByteToCKByte(jMinor);

    return ckpVersion ;
}


/*
 * converts a Java CK_DATE object into a pointer to a CK_DATE structure
 *
 * @param env - used to call JNI funktions to get the values out of the Java object
 * @param jVersion - the Java CK_DATE object to convert
 * @return - the pointer to the new CK_DATE structure
 */
CK_DATE * jDateObjectPtrToCKDatePtr(JNIEnv *env, jobject jDate)
{
    CK_DATE * ckpDate;
    CK_ULONG ckLength;
    jclass jDateClass;
    jfieldID jFieldID;
    jobject jYear, jMonth, jDay;
    jchar *jTempChars;
    CK_ULONG i;

    if (jDate == NULL) {
        return NULL;
    }

    /* get CK_DATE class */
    jDateClass = (*env)->FindClass(env, CLASS_DATE);
    if (jDateClass == NULL) { return NULL; }

    /* get Year */
    jFieldID = (*env)->GetFieldID(env, jDateClass, "year", "[C");
    if (jFieldID == NULL) { return NULL; }
    jYear = (*env)->GetObjectField(env, jDate, jFieldID);

    /* get Month */
    jFieldID = (*env)->GetFieldID(env, jDateClass, "month", "[C");
    if (jFieldID == NULL) { return NULL; }
    jMonth = (*env)->GetObjectField(env, jDate, jFieldID);

    /* get Day */
    jFieldID = (*env)->GetFieldID(env, jDateClass, "day", "[C");
    if (jFieldID == NULL) { return NULL; }
    jDay = (*env)->GetObjectField(env, jDate, jFieldID);

    /* allocate memory for CK_DATE pointer */
    ckpDate = (CK_DATE *) malloc(sizeof(CK_DATE));
    if (ckpDate == NULL) {
        throwOutOfMemoryError(env, 0);
        return NULL;
    }

    if (jYear == NULL) {
        ckpDate->year[0] = 0;
        ckpDate->year[1] = 0;
        ckpDate->year[2] = 0;
        ckpDate->year[3] = 0;
    } else {
        ckLength = (*env)->GetArrayLength(env, jYear);
        jTempChars = (jchar*) malloc((ckLength) * sizeof(jchar));
        if (jTempChars == NULL) {
            free(ckpDate);
            throwOutOfMemoryError(env, 0);
            return NULL;
        }
        (*env)->GetCharArrayRegion(env, jYear, 0, ckLength, jTempChars);
        if ((*env)->ExceptionCheck(env)) {
            free(ckpDate);
            free(jTempChars);
            return NULL;
        }

        for (i = 0; (i < ckLength) && (i < 4) ; i++) {
            ckpDate->year[i] = jCharToCKChar(jTempChars[i]);
        }
        free(jTempChars);
    }

    if (jMonth == NULL) {
        ckpDate->month[0] = 0;
        ckpDate->month[1] = 0;
    } else {
        ckLength = (*env)->GetArrayLength(env, jMonth);
        jTempChars = (jchar*) malloc((ckLength) * sizeof(jchar));
        if (jTempChars == NULL) {
            free(ckpDate);
            throwOutOfMemoryError(env, 0);
            return NULL;
        }
        (*env)->GetCharArrayRegion(env, jMonth, 0, ckLength, jTempChars);
        if ((*env)->ExceptionCheck(env)) {
            free(ckpDate);
            free(jTempChars);
            return NULL;
        }

        for (i = 0; (i < ckLength) && (i < 2) ; i++) {
            ckpDate->month[i] = jCharToCKChar(jTempChars[i]);
        }
        free(jTempChars);
    }

    if (jDay == NULL) {
        ckpDate->day[0] = 0;
        ckpDate->day[1] = 0;
    } else {
        ckLength = (*env)->GetArrayLength(env, jDay);
        jTempChars = (jchar*) malloc((ckLength) * sizeof(jchar));
        if (jTempChars == NULL) {
            free(ckpDate);
            throwOutOfMemoryError(env, 0);
            return NULL;
        }
        (*env)->GetCharArrayRegion(env, jDay, 0, ckLength, jTempChars);
        if ((*env)->ExceptionCheck(env)) {
            free(ckpDate);
            free(jTempChars);
            return NULL;
        }

        for (i = 0; (i < ckLength) && (i < 2) ; i++) {
            ckpDate->day[i] = jCharToCKChar(jTempChars[i]);
        }
        free(jTempChars);
    }

    return ckpDate ;
}


/*
 * converts a Java CK_ATTRIBUTE object into a CK_ATTRIBUTE structure
 *
 * @param env - used to call JNI funktions to get the values out of the Java object
 * @param jAttribute - the Java CK_ATTRIBUTE object to convert
 * @return - the new CK_ATTRIBUTE structure
 */
CK_ATTRIBUTE jAttributeToCKAttribute(JNIEnv *env, jobject jAttribute)
{
    CK_ATTRIBUTE ckAttribute;
    jclass jAttributeClass;
    jfieldID jFieldID;
    jlong jType;
    jobject jPValue;
    memset(&ckAttribute, 0, sizeof(CK_ATTRIBUTE));

    // TBD: what if jAttribute == NULL?!

    TRACE0("\nDEBUG: jAttributeToCKAttribute");
    /* get CK_ATTRIBUTE class */
    TRACE0(", getting attribute object class");
    jAttributeClass = (*env)->GetObjectClass(env, jAttribute);
    if (jAttributeClass == NULL) { return ckAttribute; }

    /* get type */
    TRACE0(", getting type field");
    jFieldID = (*env)->GetFieldID(env, jAttributeClass, "type", "J");
    if (jFieldID == NULL) { return ckAttribute; }
    jType = (*env)->GetLongField(env, jAttribute, jFieldID);
    TRACE1(", type=0x%X", jType);

    /* get pValue */
    TRACE0(", getting pValue field");
    jFieldID = (*env)->GetFieldID(env, jAttributeClass, "pValue", "Ljava/lang/Object;");
    if (jFieldID == NULL) { return ckAttribute; }
    jPValue = (*env)->GetObjectField(env, jAttribute, jFieldID);
    TRACE1(", pValue=%p", jPValue);

    ckAttribute.type = jLongToCKULong(jType);
    TRACE0(", converting pValue to primitive object");

    /* convert the Java pValue object to a CK-type pValue pointer */
    jObjectToPrimitiveCKObjectPtrPtr(env, jPValue, &(ckAttribute.pValue), &(ckAttribute.ulValueLen));

    TRACE0("\nFINISHED\n");

    return ckAttribute ;
}

void masterKeyDeriveParamToCKMasterKeyDeriveParam(JNIEnv *env, jobject jParam,
        jclass masterKeyDeriveParamClass,
        CK_VERSION_PTR* cKMasterKeyDeriveParamVersion,
        CK_SSL3_RANDOM_DATA* cKMasterKeyDeriveParamRandomInfo) {
    jfieldID fieldID;
    jclass jSsl3RandomDataClass;
    jobject jRandomInfo, jRIClientRandom, jRIServerRandom, jVersion;

    /* get RandomInfo */
    fieldID = (*env)->GetFieldID(env, masterKeyDeriveParamClass, "RandomInfo",
            "Lsun/security/pkcs11/wrapper/CK_SSL3_RANDOM_DATA;");
    if (fieldID == NULL) { return; }
    jRandomInfo = (*env)->GetObjectField(env, jParam, fieldID);

    /* get pClientRandom and ulClientRandomLength out of RandomInfo */
    jSsl3RandomDataClass = (*env)->FindClass(env, CLASS_SSL3_RANDOM_DATA);
    if (jSsl3RandomDataClass == NULL) { return; }
    fieldID = (*env)->GetFieldID(env, jSsl3RandomDataClass, "pClientRandom", "[B");
    if (fieldID == NULL) { return; }
    jRIClientRandom = (*env)->GetObjectField(env, jRandomInfo, fieldID);

    /* get pServerRandom and ulServerRandomLength out of RandomInfo */
    fieldID = (*env)->GetFieldID(env, jSsl3RandomDataClass, "pServerRandom", "[B");
    if (fieldID == NULL) { return; }
    jRIServerRandom = (*env)->GetObjectField(env, jRandomInfo, fieldID);

    /* get pVersion */
    fieldID = (*env)->GetFieldID(env, masterKeyDeriveParamClass, "pVersion",
            "Lsun/security/pkcs11/wrapper/CK_VERSION;");
    if (fieldID == NULL) { return; }
    jVersion = (*env)->GetObjectField(env, jParam, fieldID);

    /* populate java values */
    *cKMasterKeyDeriveParamVersion = jVersionToCKVersionPtr(env, jVersion);
    if ((*env)->ExceptionCheck(env)) { return; }
    jByteArrayToCKByteArray(env, jRIClientRandom,
            &(cKMasterKeyDeriveParamRandomInfo->pClientRandom),
            &(cKMasterKeyDeriveParamRandomInfo->ulClientRandomLen));
    if ((*env)->ExceptionCheck(env)) {
        free(*cKMasterKeyDeriveParamVersion);
        return;
    }
    jByteArrayToCKByteArray(env, jRIServerRandom,
            &(cKMasterKeyDeriveParamRandomInfo->pServerRandom),
            &(cKMasterKeyDeriveParamRandomInfo->ulServerRandomLen));
    if ((*env)->ExceptionCheck(env)) {
        free(*cKMasterKeyDeriveParamVersion);
        free(cKMasterKeyDeriveParamRandomInfo->pClientRandom);
        return;
    }
}

/*
 * converts the Java CK_SSL3_MASTER_KEY_DERIVE_PARAMS object to a
 * CK_SSL3_MASTER_KEY_DERIVE_PARAMS structure
 *
 * @param env - used to call JNI functions to get the Java classes and objects
 * @param jParam - the Java CK_SSL3_MASTER_KEY_DERIVE_PARAMS object to convert
 * @return - the new CK_SSL3_MASTER_KEY_DERIVE_PARAMS structure
 */
CK_SSL3_MASTER_KEY_DERIVE_PARAMS
jSsl3MasterKeyDeriveParamToCKSsl3MasterKeyDeriveParam(JNIEnv *env,
        jobject jParam)
{
    CK_SSL3_MASTER_KEY_DERIVE_PARAMS ckParam;
    jclass jSsl3MasterKeyDeriveParamsClass;
    memset(&ckParam, 0, sizeof(CK_SSL3_MASTER_KEY_DERIVE_PARAMS));
    jSsl3MasterKeyDeriveParamsClass =
            (*env)->FindClass(env, CLASS_SSL3_MASTER_KEY_DERIVE_PARAMS);
    if (jSsl3MasterKeyDeriveParamsClass == NULL) { return ckParam; }
    masterKeyDeriveParamToCKMasterKeyDeriveParam(env, jParam,
            jSsl3MasterKeyDeriveParamsClass,
            &ckParam.pVersion, &ckParam.RandomInfo);
    return ckParam;
}

/*
 * converts the Java CK_TLS12_MASTER_KEY_DERIVE_PARAMS object to a
 * CK_TLS12_MASTER_KEY_DERIVE_PARAMS structure
 *
 * @param env - used to call JNI functions to get the Java classes and objects
 * @param jParam - the Java CK_TLS12_MASTER_KEY_DERIVE_PARAMS object to convert
 * @return - the new CK_TLS12_MASTER_KEY_DERIVE_PARAMS structure
 */
CK_TLS12_MASTER_KEY_DERIVE_PARAMS
jTls12MasterKeyDeriveParamToCKTls12MasterKeyDeriveParam(JNIEnv *env,
        jobject jParam)
{
    CK_TLS12_MASTER_KEY_DERIVE_PARAMS ckParam;
    jclass jTls12MasterKeyDeriveParamsClass;
    jfieldID fieldID;
    memset(&ckParam, 0, sizeof(CK_TLS12_MASTER_KEY_DERIVE_PARAMS));
    jTls12MasterKeyDeriveParamsClass =
            (*env)->FindClass(env, CLASS_TLS12_MASTER_KEY_DERIVE_PARAMS);
    if (jTls12MasterKeyDeriveParamsClass == NULL) { return ckParam; }
    masterKeyDeriveParamToCKMasterKeyDeriveParam(env, jParam,
            jTls12MasterKeyDeriveParamsClass, &ckParam.pVersion,
            &ckParam.RandomInfo);
    fieldID = (*env)->GetFieldID(env,
            jTls12MasterKeyDeriveParamsClass, "prfHashMechanism", "J");
    if (fieldID != NULL) {
        jlong prfHashMechanism =
                (*env)->GetLongField(env, jParam, fieldID);
        ckParam.prfHashMechanism = (CK_MECHANISM_TYPE)prfHashMechanism;
    }
    return ckParam;
}

/*
 * converts the Java CK_TLS_PRF_PARAMS object to a CK_TLS_PRF_PARAMS structure
 */
CK_TLS_PRF_PARAMS jTlsPrfParamsToCKTlsPrfParam(JNIEnv *env, jobject jParam)
{
    jclass jTlsPrfParamsClass;
    CK_TLS_PRF_PARAMS ckParam;
    jfieldID fieldID;
    jobject jSeed, jLabel, jOutput;
    memset(&ckParam, 0, sizeof(CK_TLS_PRF_PARAMS));

    // TBD: what if jParam == NULL?!

    /* get pSeed */
    jTlsPrfParamsClass = (*env)->FindClass(env, CLASS_TLS_PRF_PARAMS);
    if (jTlsPrfParamsClass == NULL) { return ckParam; }
    fieldID = (*env)->GetFieldID(env, jTlsPrfParamsClass, "pSeed", "[B");
    if (fieldID == NULL) { return ckParam; }
    jSeed = (*env)->GetObjectField(env, jParam, fieldID);

    /* get pLabel */
    fieldID = (*env)->GetFieldID(env, jTlsPrfParamsClass, "pLabel", "[B");
    if (fieldID == NULL) { return ckParam; }
    jLabel = (*env)->GetObjectField(env, jParam, fieldID);

    /* get pOutput */
    fieldID = (*env)->GetFieldID(env, jTlsPrfParamsClass, "pOutput", "[B");
    if (fieldID == NULL) { return ckParam; }
    jOutput = (*env)->GetObjectField(env, jParam, fieldID);

    /* populate java values */
    jByteArrayToCKByteArray(env, jSeed, &(ckParam.pSeed), &(ckParam.ulSeedLen));
    if ((*env)->ExceptionCheck(env)) { return ckParam; }
    jByteArrayToCKByteArray(env, jLabel, &(ckParam.pLabel), &(ckParam.ulLabelLen));
    if ((*env)->ExceptionCheck(env)) {
        free(ckParam.pSeed);
        return ckParam;
    }
    ckParam.pulOutputLen = malloc(sizeof(CK_ULONG));
    if (ckParam.pulOutputLen == NULL) {
        free(ckParam.pSeed);
        free(ckParam.pLabel);
        throwOutOfMemoryError(env, 0);
        return ckParam;
    }
    jByteArrayToCKByteArray(env, jOutput, &(ckParam.pOutput), ckParam.pulOutputLen);
    if ((*env)->ExceptionCheck(env)) {
        free(ckParam.pSeed);
        free(ckParam.pLabel);
        free(ckParam.pulOutputLen);
        return ckParam;
    }

    return ckParam ;
}

/*
 * converts the Java CK_TLS_MAC_PARAMS object to a CK_TLS_MAC_PARAMS structure
 */
CK_TLS_MAC_PARAMS jTlsMacParamsToCKTlsMacParam(JNIEnv *env, jobject jParam)
{
    jclass jTlsMacParamsClass;
    CK_TLS_MAC_PARAMS ckParam;
    jfieldID fieldID;
    jlong jPrfMechanism, jUlMacLength, jUlServerOrClient;
    memset(&ckParam, 0, sizeof(CK_TLS_MAC_PARAMS));

    jTlsMacParamsClass = (*env)->FindClass(env, CLASS_TLS_MAC_PARAMS);
    if (jTlsMacParamsClass == NULL) { return ckParam; }

    /* get prfMechanism */
    fieldID = (*env)->GetFieldID(env, jTlsMacParamsClass, "prfMechanism", "J");
    if (fieldID == NULL) { return ckParam; }
    jPrfMechanism = (*env)->GetLongField(env, jParam, fieldID);

    /* get ulMacLength */
    fieldID = (*env)->GetFieldID(env, jTlsMacParamsClass, "ulMacLength", "J");
    if (fieldID == NULL) { return ckParam; }
    jUlMacLength = (*env)->GetLongField(env, jParam, fieldID);

    /* get ulServerOrClient */
    fieldID = (*env)->GetFieldID(env, jTlsMacParamsClass, "ulServerOrClient", "J");
    if (fieldID == NULL) { return ckParam; }
    jUlServerOrClient = (*env)->GetLongField(env, jParam, fieldID);

    /* populate java values */
    ckParam.prfMechanism = jLongToCKULong(jPrfMechanism);
    ckParam.ulMacLength = jLongToCKULong(jUlMacLength);
    ckParam.ulServerOrClient = jLongToCKULong(jUlServerOrClient);

    return ckParam;
}

void keyMatParamToCKKeyMatParam(JNIEnv *env, jobject jParam,
        jclass jKeyMatParamClass,
        CK_ULONG* cKKeyMatParamUlMacSizeInBits,
        CK_ULONG* cKKeyMatParamUlKeySizeInBits,
        CK_ULONG* cKKeyMatParamUlIVSizeInBits,
        CK_BBOOL* cKKeyMatParamBIsExport,
        CK_SSL3_RANDOM_DATA* cKKeyMatParamRandomInfo,
        CK_SSL3_KEY_MAT_OUT_PTR* cKKeyMatParamPReturnedKeyMaterial)
{
    jclass jSsl3RandomDataClass, jSsl3KeyMatOutClass;
    jfieldID fieldID;
    jlong jMacSizeInBits, jKeySizeInBits, jIVSizeInBits;
    jboolean jIsExport;
    jobject jRandomInfo, jRIClientRandom, jRIServerRandom;
    jobject jReturnedKeyMaterial, jRMIvClient, jRMIvServer;
    CK_ULONG ckTemp;

    /* get ulMacSizeInBits */
    fieldID = (*env)->GetFieldID(env, jKeyMatParamClass, "ulMacSizeInBits", "J");
    if (fieldID == NULL) { return; }
    jMacSizeInBits = (*env)->GetLongField(env, jParam, fieldID);

    /* get ulKeySizeInBits */
    fieldID = (*env)->GetFieldID(env, jKeyMatParamClass, "ulKeySizeInBits", "J");
    if (fieldID == NULL) { return; }
    jKeySizeInBits = (*env)->GetLongField(env, jParam, fieldID);

    /* get ulIVSizeInBits */
    fieldID = (*env)->GetFieldID(env, jKeyMatParamClass, "ulIVSizeInBits", "J");
    if (fieldID == NULL) { return; }
    jIVSizeInBits = (*env)->GetLongField(env, jParam, fieldID);

    /* get bIsExport */
    fieldID = (*env)->GetFieldID(env, jKeyMatParamClass, "bIsExport", "Z");
    if (fieldID == NULL) { return; }
    jIsExport = (*env)->GetBooleanField(env, jParam, fieldID);

    /* get RandomInfo */
    jSsl3RandomDataClass = (*env)->FindClass(env, CLASS_SSL3_RANDOM_DATA);
    if (jSsl3RandomDataClass == NULL) { return; }
    fieldID = (*env)->GetFieldID(env, jKeyMatParamClass, "RandomInfo",
            "Lsun/security/pkcs11/wrapper/CK_SSL3_RANDOM_DATA;");
    if (fieldID == NULL) { return; }
    jRandomInfo = (*env)->GetObjectField(env, jParam, fieldID);

    /* get pClientRandom and ulClientRandomLength out of RandomInfo */
    fieldID = (*env)->GetFieldID(env, jSsl3RandomDataClass, "pClientRandom", "[B");
    if (fieldID == NULL) { return; }
    jRIClientRandom = (*env)->GetObjectField(env, jRandomInfo, fieldID);

    /* get pServerRandom and ulServerRandomLength out of RandomInfo */
    fieldID = (*env)->GetFieldID(env, jSsl3RandomDataClass, "pServerRandom", "[B");
    if (fieldID == NULL) { return; }
    jRIServerRandom = (*env)->GetObjectField(env, jRandomInfo, fieldID);

    /* get pReturnedKeyMaterial */
    jSsl3KeyMatOutClass = (*env)->FindClass(env, CLASS_SSL3_KEY_MAT_OUT);
    if (jSsl3KeyMatOutClass == NULL) { return; }
    fieldID = (*env)->GetFieldID(env, jKeyMatParamClass, "pReturnedKeyMaterial",
            "Lsun/security/pkcs11/wrapper/CK_SSL3_KEY_MAT_OUT;");
    if (fieldID == NULL) { return; }
    jReturnedKeyMaterial = (*env)->GetObjectField(env, jParam, fieldID);

    /* get pIVClient out of pReturnedKeyMaterial */
    fieldID = (*env)->GetFieldID(env, jSsl3KeyMatOutClass, "pIVClient", "[B");
    if (fieldID == NULL) { return; }
    jRMIvClient = (*env)->GetObjectField(env, jReturnedKeyMaterial, fieldID);

    /* get pIVServer out of pReturnedKeyMaterial */
    fieldID = (*env)->GetFieldID(env, jSsl3KeyMatOutClass, "pIVServer", "[B");
    if (fieldID == NULL) { return; }
    jRMIvServer = (*env)->GetObjectField(env, jReturnedKeyMaterial, fieldID);

    /* populate java values */
    *cKKeyMatParamUlMacSizeInBits = jLongToCKULong(jMacSizeInBits);
    *cKKeyMatParamUlKeySizeInBits = jLongToCKULong(jKeySizeInBits);
    *cKKeyMatParamUlIVSizeInBits = jLongToCKULong(jIVSizeInBits);
    *cKKeyMatParamBIsExport = jBooleanToCKBBool(jIsExport);
    jByteArrayToCKByteArray(env, jRIClientRandom,
            &(cKKeyMatParamRandomInfo->pClientRandom),
            &(cKKeyMatParamRandomInfo->ulClientRandomLen));
    if ((*env)->ExceptionCheck(env)) { return; }
    jByteArrayToCKByteArray(env, jRIServerRandom,
            &(cKKeyMatParamRandomInfo->pServerRandom),
            &(cKKeyMatParamRandomInfo->ulServerRandomLen));
    if ((*env)->ExceptionCheck(env)) {
        free(cKKeyMatParamRandomInfo->pClientRandom);
        return;
    }
    /* allocate memory for pRetrunedKeyMaterial */
    *cKKeyMatParamPReturnedKeyMaterial =
            (CK_SSL3_KEY_MAT_OUT_PTR)malloc(sizeof(CK_SSL3_KEY_MAT_OUT));
    if (*cKKeyMatParamPReturnedKeyMaterial == NULL) {
        free(cKKeyMatParamRandomInfo->pClientRandom);
        free(cKKeyMatParamRandomInfo->pServerRandom);
        throwOutOfMemoryError(env, 0);
        return;
    }

    // the handles are output params only, no need to fetch them from Java
    (*cKKeyMatParamPReturnedKeyMaterial)->hClientMacSecret = 0;
    (*cKKeyMatParamPReturnedKeyMaterial)->hServerMacSecret = 0;
    (*cKKeyMatParamPReturnedKeyMaterial)->hClientKey = 0;
    (*cKKeyMatParamPReturnedKeyMaterial)->hServerKey = 0;

    jByteArrayToCKByteArray(env, jRMIvClient,
            &((*cKKeyMatParamPReturnedKeyMaterial)->pIVClient), &ckTemp);
    if ((*env)->ExceptionCheck(env)) {
        free(cKKeyMatParamRandomInfo->pClientRandom);
        free(cKKeyMatParamRandomInfo->pServerRandom);
        free((*cKKeyMatParamPReturnedKeyMaterial));
        return;
    }
    jByteArrayToCKByteArray(env, jRMIvServer,
            &((*cKKeyMatParamPReturnedKeyMaterial)->pIVServer), &ckTemp);
    if ((*env)->ExceptionCheck(env)) {
        free(cKKeyMatParamRandomInfo->pClientRandom);
        free(cKKeyMatParamRandomInfo->pServerRandom);
        free((*cKKeyMatParamPReturnedKeyMaterial)->pIVClient);
        free((*cKKeyMatParamPReturnedKeyMaterial));
        return;
    }

    return;
}
/*
 * converts the Java CK_SSL3_KEY_MAT_PARAMS object to a
 * CK_SSL3_KEY_MAT_PARAMS structure
 *
 * @param env - used to call JNI funktions to get the Java classes and objects
 * @param jParam - the Java CK_SSL3_KEY_MAT_PARAMS object to convert
 * @return - the new CK_SSL3_KEY_MAT_PARAMS structure
 */
CK_SSL3_KEY_MAT_PARAMS
jSsl3KeyMatParamToCKSsl3KeyMatParam(JNIEnv *env, jobject jParam)
{
    CK_SSL3_KEY_MAT_PARAMS ckParam;
    jclass jSsl3KeyMatParamsClass;
    memset(&ckParam, 0, sizeof(CK_SSL3_KEY_MAT_PARAMS));
    jSsl3KeyMatParamsClass = (*env)->FindClass(env,
            CLASS_SSL3_KEY_MAT_PARAMS);
    if (jSsl3KeyMatParamsClass == NULL) { return ckParam; }
    keyMatParamToCKKeyMatParam(env, jParam, jSsl3KeyMatParamsClass,
            &ckParam.ulMacSizeInBits, &ckParam.ulKeySizeInBits,
            &ckParam.ulIVSizeInBits, &ckParam.bIsExport,
            &ckParam.RandomInfo, &ckParam.pReturnedKeyMaterial);
    return ckParam;
}

/*
 * converts the Java CK_TLS12_KEY_MAT_PARAMS object to a
 * CK_TLS12_KEY_MAT_PARAMS structure
 *
 * @param env - used to call JNI functions to get the Java classes and objects
 * @param jParam - the Java CK_TLS12_KEY_MAT_PARAMS object to convert
 * @return - the new CK_TLS12_KEY_MAT_PARAMS structure
 */
CK_TLS12_KEY_MAT_PARAMS jTls12KeyMatParamToCKTls12KeyMatParam(JNIEnv *env,
        jobject jParam)
{
    CK_TLS12_KEY_MAT_PARAMS ckParam;
    jclass jTls12KeyMatParamsClass;
    jfieldID fieldID;
    memset(&ckParam, 0, sizeof(CK_TLS12_KEY_MAT_PARAMS));
    jTls12KeyMatParamsClass = (*env)->FindClass(env,
            CLASS_TLS12_KEY_MAT_PARAMS);
    if (jTls12KeyMatParamsClass == NULL) { return ckParam; }
    keyMatParamToCKKeyMatParam(env, jParam, jTls12KeyMatParamsClass,
            &ckParam.ulMacSizeInBits, &ckParam.ulKeySizeInBits,
            &ckParam.ulIVSizeInBits, &ckParam.bIsExport,
            &ckParam.RandomInfo, &ckParam.pReturnedKeyMaterial);
    fieldID = (*env)->GetFieldID(env, jTls12KeyMatParamsClass,
            "prfHashMechanism", "J");
    if (fieldID != NULL) {
        jlong prfHashMechanism = (*env)->GetLongField(env, jParam, fieldID);
        ckParam.prfHashMechanism = (CK_MECHANISM_TYPE)prfHashMechanism;
    }
    return ckParam;
}

/*
 * converts the Java CK_AES_CTR_PARAMS object to a CK_AES_CTR_PARAMS structure
 *
 * @param env - used to call JNI funktions to get the Java classes and objects
 * @param jParam - the Java CK_AES_CTR_PARAMS object to convert
 * @param ckpParam - pointer to the new CK_AES_CTR_PARAMS structure
 */
void jAesCtrParamsToCKAesCtrParam(JNIEnv *env, jobject jParam,
                                  CK_AES_CTR_PARAMS_PTR ckpParam) {
    jclass jAesCtrParamsClass;
    jfieldID fieldID;
    jlong jCounterBits;
    jobject jCb;
    CK_BYTE_PTR ckBytes;
    CK_ULONG ckTemp;

    /* get ulCounterBits */
    jAesCtrParamsClass = (*env)->FindClass(env, CLASS_AES_CTR_PARAMS);
    if (jAesCtrParamsClass == NULL) { return; }
    fieldID = (*env)->GetFieldID(env, jAesCtrParamsClass, "ulCounterBits", "J");
    if (fieldID == NULL) { return; }
    jCounterBits = (*env)->GetLongField(env, jParam, fieldID);

    /* get cb */
    fieldID = (*env)->GetFieldID(env, jAesCtrParamsClass, "cb", "[B");
    if (fieldID == NULL) { return; }
    jCb = (*env)->GetObjectField(env, jParam, fieldID);

    /* populate java values */
    ckpParam->ulCounterBits = jLongToCKULong(jCounterBits);
    jByteArrayToCKByteArray(env, jCb, &ckBytes, &ckTemp);
    if ((*env)->ExceptionCheck(env)) { return; }
    if (ckTemp != 16) {
        TRACE1("ERROR: WRONG CTR IV LENGTH %d", ckTemp);
    } else {
        memcpy(ckpParam->cb, ckBytes, ckTemp);
        free(ckBytes);
    }
}

/*
 * converts a Java CK_MECHANISM object into a CK_MECHANISM structure
 *
 * @param env - used to call JNI funktions to get the values out of the Java object
 * @param jMechanism - the Java CK_MECHANISM object to convert
 * @return - the new CK_MECHANISM structure
 */
void jMechanismToCKMechanism(JNIEnv *env, jobject jMechanism, CK_MECHANISM_PTR ckMechanismPtr)
{
    jlong jMechanismType = (*env)->GetLongField(env, jMechanism, mech_mechanismID);
    jobject jParameter = (*env)->GetObjectField(env, jMechanism, mech_pParameterID);

    (*ckMechanismPtr).mechanism = jLongToCKULong(jMechanismType);

    /* convert the specific Java mechanism parameter object to a pointer to a CK-type mechanism
     * structure
     */
    if (jParameter == NULL) {
        (*ckMechanismPtr).pParameter = NULL;
        (*ckMechanismPtr).ulParameterLen = 0;
    } else {
        jMechanismParameterToCKMechanismParameter(env, jParameter, &(*ckMechanismPtr).pParameter, &(*ckMechanismPtr).ulParameterLen);
    }
}

/*
 * the following functions convert Attribute and Mechanism value pointers
 *
 * jobject ckAttributeValueToJObject(JNIEnv *env,
 *                                   const CK_ATTRIBUTE_PTR ckpAttribute);
 *
 * void jObjectToPrimitiveCKObjectPtrPtr(JNIEnv *env,
 *                                       jobject jObject,
 *                                       CK_VOID_PTR *ckpObjectPtr,
 *                                       CK_ULONG *pLength);
 *
 * void jMechanismParameterToCKMechanismParameter(JNIEnv *env,
 *                                                jobject jParam,
 *                                                CK_VOID_PTR *ckpParamPtr,
 *                                                CK_ULONG *ckpLength);
 *
 * These functions are used if a PKCS#11 mechanism or attribute structure gets
 * convertet to a Java attribute or mechanism object or vice versa.
 *
 * ckAttributeValueToJObject converts a PKCS#11 attribute value pointer to a Java
 * object depending on the type of the Attribute. A PKCS#11 attribute value can
 * be a CK_ULONG, CK_BYTE[], CK_CHAR[], big integer, CK_BBOOL, CK_UTF8CHAR[],
 * CK_DATE or CK_FLAGS that gets converted to a corresponding Java object.
 *
 * jObjectToPrimitiveCKObjectPtrPtr is used by jAttributeToCKAttributePtr for
 * converting the Java attribute value to a PKCS#11 attribute value pointer.
 * For now only primitive datatypes and arrays of primitive datatypes can get
 * converted. Otherwise this function throws a PKCS#11Exception with the
 * errorcode CKR_VENDOR_DEFINED.
 *
 * jMechanismParameterToCKMechanismParameter converts a Java mechanism parameter
 * to a PKCS#11 mechanism parameter. First this function determines what mechanism
 * parameter the Java object is, then it allocates the memory for the new PKCS#11
 * structure and calls the corresponding function to convert the Java object to
 * a PKCS#11 mechanism parameter structure.
 */

/*
 * converts the pValue of a CK_ATTRIBUTE structure into a Java Object by checking the type
 * of the attribute.
 *
 * @param env - used to call JNI funktions to create the new Java object
 * @param ckpAttribute - the pointer to the CK_ATTRIBUTE structure that contains the type
 *                       and the pValue to convert
 * @return - the new Java object of the CK-type pValue
 */
jobject ckAttributeValueToJObject(JNIEnv *env, const CK_ATTRIBUTE_PTR ckpAttribute)
{
    jint jValueLength;
    jobject jValueObject = NULL;

    jValueLength = ckULongToJInt(ckpAttribute->ulValueLen);

    if ((jValueLength <= 0) || (ckpAttribute->pValue == NULL)) {
        return NULL ;
    }

    switch(ckpAttribute->type) {
        case CKA_CLASS:
            /* value CK_OBJECT_CLASS, defacto a CK_ULONG */
        case CKA_KEY_TYPE:
            /* value CK_KEY_TYPE, defacto a CK_ULONG */
        case CKA_CERTIFICATE_TYPE:
            /* value CK_CERTIFICATE_TYPE, defacto a CK_ULONG */
        case CKA_HW_FEATURE_TYPE:
            /* value CK_HW_FEATURE_TYPE, defacto a CK_ULONG */
        case CKA_MODULUS_BITS:
        case CKA_VALUE_BITS:
        case CKA_VALUE_LEN:
        case CKA_KEY_GEN_MECHANISM:
        case CKA_PRIME_BITS:
        case CKA_SUB_PRIME_BITS:
            /* value CK_ULONG */
            jValueObject = ckULongPtrToJLongObject(env, (CK_ULONG*) ckpAttribute->pValue);
            break;

            /* can be CK_BYTE[],CK_CHAR[] or big integer; defacto always CK_BYTE[] */
        case CKA_VALUE:
        case CKA_OBJECT_ID:
        case CKA_SUBJECT:
        case CKA_ID:
        case CKA_ISSUER:
        case CKA_SERIAL_NUMBER:
        case CKA_OWNER:
        case CKA_AC_ISSUER:
        case CKA_ATTR_TYPES:
        case CKA_ECDSA_PARAMS:
            /* CKA_EC_PARAMS is the same, these two are equivalent */
        case CKA_EC_POINT:
        case CKA_PRIVATE_EXPONENT:
        case CKA_PRIME_1:
        case CKA_PRIME_2:
        case CKA_EXPONENT_1:
        case CKA_EXPONENT_2:
        case CKA_COEFFICIENT:
            /* value CK_BYTE[] */
            jValueObject = ckByteArrayToJByteArray(env, (CK_BYTE*) ckpAttribute->pValue, jValueLength);
            break;

        case CKA_RESET_ON_INIT:
        case CKA_HAS_RESET:
        case CKA_TOKEN:
        case CKA_PRIVATE:
        case CKA_MODIFIABLE:
        case CKA_DERIVE:
        case CKA_LOCAL:
        case CKA_ENCRYPT:
        case CKA_VERIFY:
        case CKA_VERIFY_RECOVER:
        case CKA_WRAP:
        case CKA_SENSITIVE:
        case CKA_SECONDARY_AUTH:
        case CKA_DECRYPT:
        case CKA_SIGN:
        case CKA_SIGN_RECOVER:
        case CKA_UNWRAP:
        case CKA_EXTRACTABLE:
        case CKA_ALWAYS_SENSITIVE:
        case CKA_NEVER_EXTRACTABLE:
        case CKA_TRUSTED:
            /* value CK_BBOOL */
            jValueObject = ckBBoolPtrToJBooleanObject(env, (CK_BBOOL*) ckpAttribute->pValue);
            break;

        case CKA_LABEL:
        case CKA_APPLICATION:
            /* value RFC 2279 (UTF-8) string */
            jValueObject = ckUTF8CharArrayToJCharArray(env, (CK_UTF8CHAR*) ckpAttribute->pValue, jValueLength);
            break;

        case CKA_START_DATE:
        case CKA_END_DATE:
            /* value CK_DATE */
            jValueObject = ckDatePtrToJDateObject(env, (CK_DATE*) ckpAttribute->pValue);
            break;

        case CKA_MODULUS:
        case CKA_PUBLIC_EXPONENT:
        case CKA_PRIME:
        case CKA_SUBPRIME:
        case CKA_BASE:
            /* value big integer, i.e. CK_BYTE[] */
            jValueObject = ckByteArrayToJByteArray(env, (CK_BYTE*) ckpAttribute->pValue, jValueLength);
            break;

        case CKA_AUTH_PIN_FLAGS:
            jValueObject = ckULongPtrToJLongObject(env, (CK_ULONG*) ckpAttribute->pValue);
            /* value FLAGS, defacto a CK_ULONG */
            break;

        case CKA_VENDOR_DEFINED:
            /* we make a CK_BYTE[] out of this */
            jValueObject = ckByteArrayToJByteArray(env, (CK_BYTE*) ckpAttribute->pValue, jValueLength);
            break;

        // Netscape trust attributes
        case CKA_NETSCAPE_TRUST_SERVER_AUTH:
        case CKA_NETSCAPE_TRUST_CLIENT_AUTH:
        case CKA_NETSCAPE_TRUST_CODE_SIGNING:
        case CKA_NETSCAPE_TRUST_EMAIL_PROTECTION:
            /* value CK_ULONG */
            jValueObject = ckULongPtrToJLongObject(env, (CK_ULONG*) ckpAttribute->pValue);
            break;

        default:
            /* we make a CK_BYTE[] out of this */
            jValueObject = ckByteArrayToJByteArray(env, (CK_BYTE*) ckpAttribute->pValue, jValueLength);
            break;
    }

    return jValueObject ;
}

/*
 * the following functions convert a Java mechanism parameter object to a PKCS#11
 * mechanism parameter structure
 *
 * CK_<Param>_PARAMS j<Param>ParamToCK<Param>Param(JNIEnv *env,
 *                                                 jobject jParam);
 *
 * These functions get a Java object, that must be the right Java mechanism
 * object and they return the new PKCS#11 mechanism parameter structure.
 * Every field of the Java object is retrieved, gets converted to a corresponding
 * PKCS#11 type and is set in the new PKCS#11 structure.
 */

/*
 * converts the given Java mechanism parameter to a CK mechanism parameter structure
 * and store the length in bytes in the length variable.
 * The memory of *ckpParamPtr has to be freed after use!
 *
 * @param env - used to call JNI funktions to get the Java classes and objects
 * @param jParam - the Java mechanism parameter object to convert
 * @param ckpParamPtr - the reference of the new pointer to the new CK mechanism parameter
 *                      structure
 * @param ckpLength - the reference of the length in bytes of the new CK mechanism parameter
 *                    structure
 */
void jMechanismParameterToCKMechanismParameter(JNIEnv *env, jobject jParam, CK_VOID_PTR *ckpParamPtr, CK_ULONG *ckpLength)
{
    if (jParam == NULL) {
        *ckpParamPtr = NULL;
        *ckpLength = 0;
    } else if ((*env)->IsInstanceOf(env, jParam, jByteArrayClass)) {
        jByteArrayToCKByteArray(env, jParam, (CK_BYTE_PTR *)ckpParamPtr, ckpLength);
    } else if ((*env)->IsInstanceOf(env, jParam, jLongClass)) {
        *ckpParamPtr = jLongObjectToCKULongPtr(env, jParam);
        *ckpLength = sizeof(CK_ULONG);
    } else {
        TRACE0("\nSLOW PATH jMechanismParameterToCKMechanismParameter\n");
        jMechanismParameterToCKMechanismParameterSlow(env, jParam, ckpParamPtr, ckpLength);
    }
}

void jMechanismParameterToCKMechanismParameterSlow(JNIEnv *env, jobject jParam, CK_VOID_PTR *ckpParamPtr, CK_ULONG *ckpLength)
{
    /* get all Java mechanism parameter classes */
    jclass jVersionClass, jSsl3MasterKeyDeriveParamsClass;
    jclass jTls12MasterKeyDeriveParamsClass, jSsl3KeyMatParamsClass;
    jclass jTls12KeyMatParamsClass;
    jclass jTlsPrfParamsClass, jTlsMacParamsClass, jAesCtrParamsClass;
    jclass jRsaPkcsOaepParamsClass;
    jclass jPbeParamsClass, jPkcs5Pbkd2ParamsClass, jRsaPkcsPssParamsClass;
    jclass jEcdh1DeriveParamsClass, jEcdh2DeriveParamsClass;
    jclass jX942Dh1DeriveParamsClass, jX942Dh2DeriveParamsClass;
    TRACE0("\nDEBUG: jMechanismParameterToCKMechanismParameter");

    /* most common cases, i.e. NULL/byte[]/long, are already handled by
     * jMechanismParameterToCKMechanismParameter before calling this method.
     */
    jVersionClass = (*env)->FindClass(env, CLASS_VERSION);
    if (jVersionClass == NULL) { return; }
    if ((*env)->IsInstanceOf(env, jParam, jVersionClass)) {
        /*
         * CK_VERSION used by CKM_SSL3_PRE_MASTER_KEY_GEN
         */
        CK_VERSION_PTR ckpParam;

        /* convert jParameter to CKParameter */
        ckpParam = jVersionToCKVersionPtr(env, jParam);

        /* get length and pointer of parameter */
        *ckpLength = sizeof(CK_VERSION);
        *ckpParamPtr = ckpParam;
        return;
    }

    jSsl3MasterKeyDeriveParamsClass = (*env)->FindClass(env, CLASS_SSL3_MASTER_KEY_DERIVE_PARAMS);
    if (jSsl3MasterKeyDeriveParamsClass == NULL) { return; }
    if ((*env)->IsInstanceOf(env, jParam, jSsl3MasterKeyDeriveParamsClass)) {
        /*
         * CK_SSL3_MASTER_KEY_DERIVE_PARAMS
         */
        CK_SSL3_MASTER_KEY_DERIVE_PARAMS_PTR ckpParam;

        ckpParam = (CK_SSL3_MASTER_KEY_DERIVE_PARAMS_PTR) malloc(sizeof(CK_SSL3_MASTER_KEY_DERIVE_PARAMS));
        if (ckpParam == NULL) {
            throwOutOfMemoryError(env, 0);
            return;
        }

        /* convert jParameter to CKParameter */
        *ckpParam = jSsl3MasterKeyDeriveParamToCKSsl3MasterKeyDeriveParam(env, jParam);
        if ((*env)->ExceptionCheck(env)) {
            free(ckpParam);
            return;
        }

        /* get length and pointer of parameter */
        *ckpLength = sizeof(CK_SSL3_MASTER_KEY_DERIVE_PARAMS);
        *ckpParamPtr = ckpParam;
        return;
    }

    jSsl3KeyMatParamsClass = (*env)->FindClass(env, CLASS_SSL3_KEY_MAT_PARAMS);
    if (jSsl3KeyMatParamsClass == NULL) { return; }
    if ((*env)->IsInstanceOf(env, jParam, jSsl3KeyMatParamsClass)) {
        /*
         * CK_SSL3_KEY_MAT_PARAMS
         */
        CK_SSL3_KEY_MAT_PARAMS_PTR ckpParam;

        ckpParam = (CK_SSL3_KEY_MAT_PARAMS_PTR) malloc(sizeof(CK_SSL3_KEY_MAT_PARAMS));
        if (ckpParam == NULL) {
            throwOutOfMemoryError(env, 0);
            return;
        }

        /* convert jParameter to CKParameter */
        *ckpParam = jSsl3KeyMatParamToCKSsl3KeyMatParam(env, jParam);
        if ((*env)->ExceptionCheck(env)) {
            free(ckpParam);
            return;
        }

        /* get length and pointer of parameter */
        *ckpLength = sizeof(CK_SSL3_KEY_MAT_PARAMS);
        *ckpParamPtr = ckpParam;
        return;
    }

    jTls12KeyMatParamsClass = (*env)->FindClass(env, CLASS_TLS12_KEY_MAT_PARAMS);
    if (jTls12KeyMatParamsClass == NULL) { return; }
    if ((*env)->IsInstanceOf(env, jParam, jTls12KeyMatParamsClass)) {
        /*
         * CK_TLS12_KEY_MAT_PARAMS
         */
        CK_TLS12_KEY_MAT_PARAMS_PTR ckpParam;

        ckpParam = (CK_TLS12_KEY_MAT_PARAMS_PTR) malloc(sizeof(CK_TLS12_KEY_MAT_PARAMS));
        if (ckpParam == NULL) {
            throwOutOfMemoryError(env, 0);
            return;
        }

        /* convert jParameter to CKParameter */
        *ckpParam = jTls12KeyMatParamToCKTls12KeyMatParam(env, jParam);
        if ((*env)->ExceptionCheck(env)) {
            free(ckpParam);
            return;
        }

        /* get length and pointer of parameter */
        *ckpLength = sizeof(CK_TLS12_KEY_MAT_PARAMS);
        *ckpParamPtr = ckpParam;
        return;
    }

    jTls12MasterKeyDeriveParamsClass =
            (*env)->FindClass(env, CLASS_TLS12_MASTER_KEY_DERIVE_PARAMS);
    if (jTls12MasterKeyDeriveParamsClass == NULL) { return; }
    if ((*env)->IsInstanceOf(env, jParam, jTls12MasterKeyDeriveParamsClass)) {
        /*
         * CK_TLS12_MASTER_KEY_DERIVE_PARAMS
         */
        CK_TLS12_MASTER_KEY_DERIVE_PARAMS_PTR ckpParam;

        ckpParam = (CK_TLS12_MASTER_KEY_DERIVE_PARAMS_PTR)malloc(
                sizeof(CK_TLS12_MASTER_KEY_DERIVE_PARAMS));
        if (ckpParam == NULL) {
            throwOutOfMemoryError(env, 0);
            return;
        }

        /* convert jParameter to CKParameter */
        *ckpParam = jTls12MasterKeyDeriveParamToCKTls12MasterKeyDeriveParam(env, jParam);
        if ((*env)->ExceptionCheck(env)) {
            free(ckpParam);
            return;
        }

        /* get length and pointer of parameter */
        *ckpLength = sizeof(CK_TLS12_MASTER_KEY_DERIVE_PARAMS);
        *ckpParamPtr = ckpParam;
        return;
    }

    jTlsPrfParamsClass = (*env)->FindClass(env, CLASS_TLS_PRF_PARAMS);
    if (jTlsPrfParamsClass == NULL) { return; }
    if ((*env)->IsInstanceOf(env, jParam, jTlsPrfParamsClass)) {
        /*
         * CK_TLS_PRF_PARAMS
         */
        CK_TLS_PRF_PARAMS_PTR ckpParam;

        ckpParam = (CK_TLS_PRF_PARAMS_PTR) malloc(sizeof(CK_TLS_PRF_PARAMS));
        if (ckpParam == NULL) {
            throwOutOfMemoryError(env, 0);
            return;
        }

        /* convert jParameter to CKParameter */
        *ckpParam = jTlsPrfParamsToCKTlsPrfParam(env, jParam);
        if ((*env)->ExceptionCheck(env)) {
            free(ckpParam);
            return;
        }

        /* get length and pointer of parameter */
        *ckpLength = sizeof(CK_TLS_PRF_PARAMS);
        *ckpParamPtr = ckpParam;
        return;
    }

    jTlsMacParamsClass = (*env)->FindClass(env, CLASS_TLS_MAC_PARAMS);
    if (jTlsMacParamsClass == NULL) { return; }
    if ((*env)->IsInstanceOf(env, jParam, jTlsMacParamsClass)) {
        CK_TLS_MAC_PARAMS_PTR ckpParam;

        ckpParam = (CK_TLS_MAC_PARAMS_PTR) malloc(sizeof(CK_TLS_MAC_PARAMS));
        if (ckpParam == NULL) {
            throwOutOfMemoryError(env, 0);
            return;
        }

        /* convert jParameter to CKParameter */
        *ckpParam = jTlsMacParamsToCKTlsMacParam(env, jParam);
        if ((*env)->ExceptionCheck(env)) {
            free(ckpParam);
            return;
        }

        /* get length and pointer of parameter */
        *ckpLength = sizeof(CK_TLS_MAC_PARAMS);
        *ckpParamPtr = ckpParam;
        return;
    }

    jAesCtrParamsClass = (*env)->FindClass(env, CLASS_AES_CTR_PARAMS);
    if (jAesCtrParamsClass == NULL) { return; }
    if ((*env)->IsInstanceOf(env, jParam, jAesCtrParamsClass)) {
        /*
         * CK_AES_CTR_PARAMS
         */
        CK_AES_CTR_PARAMS_PTR ckpParam;

        ckpParam = (CK_AES_CTR_PARAMS_PTR) malloc(sizeof(CK_AES_CTR_PARAMS));
        if (ckpParam == NULL) {
            throwOutOfMemoryError(env, 0);
            return;
        }

        /* convert jParameter to CKParameter */
        jAesCtrParamsToCKAesCtrParam(env, jParam, ckpParam);
        if ((*env)->ExceptionCheck(env)) {
            free(ckpParam);
            return;
        }

        /* get length and pointer of parameter */
        *ckpLength = sizeof(CK_AES_CTR_PARAMS);
        *ckpParamPtr = ckpParam;
        return;
    }

    jRsaPkcsOaepParamsClass = (*env)->FindClass(env, CLASS_RSA_PKCS_OAEP_PARAMS);
    if (jRsaPkcsOaepParamsClass == NULL) { return; }
    if ((*env)->IsInstanceOf(env, jParam, jRsaPkcsOaepParamsClass)) {
        /*
         * CK_RSA_PKCS_OAEP_PARAMS
         */
        CK_RSA_PKCS_OAEP_PARAMS_PTR ckpParam;

        ckpParam = (CK_RSA_PKCS_OAEP_PARAMS_PTR) malloc(sizeof(CK_RSA_PKCS_OAEP_PARAMS));
        if (ckpParam == NULL) {
            throwOutOfMemoryError(env, 0);
            return;
        }

        /* convert jParameter to CKParameter */
        *ckpParam = jRsaPkcsOaepParamToCKRsaPkcsOaepParam(env, jParam);
        if ((*env)->ExceptionCheck(env)) {
            free(ckpParam);
            return;
        }

        /* get length and pointer of parameter */
        *ckpLength = sizeof(CK_RSA_PKCS_OAEP_PARAMS);
        *ckpParamPtr = ckpParam;
        return;
    }

    jPbeParamsClass = (*env)->FindClass(env, CLASS_PBE_PARAMS);
    if (jPbeParamsClass == NULL) { return; }
    if ((*env)->IsInstanceOf(env, jParam, jPbeParamsClass)) {
        /*
         * CK_PBE_PARAMS
         */
        CK_PBE_PARAMS_PTR ckpParam;

        ckpParam = (CK_PBE_PARAMS_PTR) malloc(sizeof(CK_PBE_PARAMS));
        if (ckpParam == NULL) {
            throwOutOfMemoryError(env, 0);
            return;
        }

        /* convert jParameter to CKParameter */
        *ckpParam = jPbeParamToCKPbeParam(env, jParam);
        if ((*env)->ExceptionCheck(env)) {
            free(ckpParam);
            return;
        }

        /* get length and pointer of parameter */
        *ckpLength = sizeof(CK_PBE_PARAMS);
        *ckpParamPtr = ckpParam;
        return;
    }

    jPkcs5Pbkd2ParamsClass = (*env)->FindClass(env, CLASS_PKCS5_PBKD2_PARAMS);
    if (jPkcs5Pbkd2ParamsClass == NULL) { return; }
    if ((*env)->IsInstanceOf(env, jParam, jPkcs5Pbkd2ParamsClass)) {
        /*
         * CK_PKCS5_PBKD2_PARAMS
         */
        CK_PKCS5_PBKD2_PARAMS_PTR ckpParam;

        ckpParam = (CK_PKCS5_PBKD2_PARAMS_PTR) malloc(sizeof(CK_PKCS5_PBKD2_PARAMS));
        if (ckpParam == NULL) {
            throwOutOfMemoryError(env, 0);
            return;
        }

        /* convert jParameter to CKParameter */
        *ckpParam = jPkcs5Pbkd2ParamToCKPkcs5Pbkd2Param(env, jParam);
        if ((*env)->ExceptionCheck(env)) {
            free(ckpParam);
            return;
        }

        /* get length and pointer of parameter */
        *ckpLength = sizeof(CK_PKCS5_PBKD2_PARAMS);
        *ckpParamPtr = ckpParam;
        return;
    }

    jRsaPkcsPssParamsClass = (*env)->FindClass(env, CLASS_RSA_PKCS_PSS_PARAMS);
    if (jRsaPkcsPssParamsClass == NULL) { return; }
    if ((*env)->IsInstanceOf(env, jParam, jRsaPkcsPssParamsClass)) {
        /*
         * CK_RSA_PKCS_PSS_PARAMS
         */
        CK_RSA_PKCS_PSS_PARAMS_PTR ckpParam;

        ckpParam = (CK_RSA_PKCS_PSS_PARAMS_PTR) malloc(sizeof(CK_RSA_PKCS_PSS_PARAMS));
        if (ckpParam == NULL) {
            throwOutOfMemoryError(env, 0);
            return;
        }

        /* convert jParameter to CKParameter */
        *ckpParam = jRsaPkcsPssParamToCKRsaPkcsPssParam(env, jParam);
        if ((*env)->ExceptionCheck(env)) {
            free(ckpParam);
            return;
        }

        /* get length and pointer of parameter */
        *ckpLength = sizeof(CK_RSA_PKCS_PSS_PARAMS);
        *ckpParamPtr = ckpParam;
        return;
    }

    jEcdh1DeriveParamsClass = (*env)->FindClass(env, CLASS_ECDH1_DERIVE_PARAMS);
    if (jEcdh1DeriveParamsClass == NULL) { return; }
    if ((*env)->IsInstanceOf(env, jParam, jEcdh1DeriveParamsClass)) {
        /*
         * CK_ECDH1_DERIVE_PARAMS
         */
        CK_ECDH1_DERIVE_PARAMS_PTR ckpParam;

        ckpParam = (CK_ECDH1_DERIVE_PARAMS_PTR) malloc(sizeof(CK_ECDH1_DERIVE_PARAMS));
        if (ckpParam == NULL) {
            throwOutOfMemoryError(env, 0);
            return;
        }

        /* convert jParameter to CKParameter */
        *ckpParam = jEcdh1DeriveParamToCKEcdh1DeriveParam(env, jParam);
        if ((*env)->ExceptionCheck(env)) {
            free(ckpParam);
            return;
        }

        /* get length and pointer of parameter */
        *ckpLength = sizeof(CK_ECDH1_DERIVE_PARAMS);
        *ckpParamPtr = ckpParam;
        return;
    }

    jEcdh2DeriveParamsClass = (*env)->FindClass(env, CLASS_ECDH2_DERIVE_PARAMS);
    if (jEcdh2DeriveParamsClass == NULL) { return; }
    if ((*env)->IsInstanceOf(env, jParam, jEcdh2DeriveParamsClass)) {
        /*
         * CK_ECDH2_DERIVE_PARAMS
         */
        CK_ECDH2_DERIVE_PARAMS_PTR ckpParam;

        ckpParam = (CK_ECDH2_DERIVE_PARAMS_PTR) malloc(sizeof(CK_ECDH2_DERIVE_PARAMS));
        if (ckpParam == NULL) {
            throwOutOfMemoryError(env, 0);
            return;
        }

        /* convert jParameter to CKParameter */
        *ckpParam = jEcdh2DeriveParamToCKEcdh2DeriveParam(env, jParam);
        if ((*env)->ExceptionCheck(env)) {
            free(ckpParam);
            return;
        }

        /* get length and pointer of parameter */
        *ckpLength = sizeof(CK_ECDH2_DERIVE_PARAMS);
        *ckpParamPtr = ckpParam;
        return;
    }

    jX942Dh1DeriveParamsClass = (*env)->FindClass(env, CLASS_X9_42_DH1_DERIVE_PARAMS);
    if (jX942Dh1DeriveParamsClass == NULL) { return; }
    if ((*env)->IsInstanceOf(env, jParam, jX942Dh1DeriveParamsClass)) {
        /*
         * CK_X9_42_DH1_DERIVE_PARAMS
         */
        CK_X9_42_DH1_DERIVE_PARAMS_PTR ckpParam;

        ckpParam = (CK_X9_42_DH1_DERIVE_PARAMS_PTR) malloc(sizeof(CK_X9_42_DH1_DERIVE_PARAMS));
        if (ckpParam == NULL) {
            throwOutOfMemoryError(env, 0);
            return;
        }

        /* convert jParameter to CKParameter */
        *ckpParam = jX942Dh1DeriveParamToCKX942Dh1DeriveParam(env, jParam);
        if ((*env)->ExceptionCheck(env)) {
            free(ckpParam);
            return;
        }

        /* get length and pointer of parameter */
        *ckpLength = sizeof(CK_X9_42_DH1_DERIVE_PARAMS);
        *ckpParamPtr = ckpParam;
        return;
    }

    jX942Dh2DeriveParamsClass = (*env)->FindClass(env, CLASS_X9_42_DH2_DERIVE_PARAMS);
    if (jX942Dh2DeriveParamsClass == NULL) { return; }
    if ((*env)->IsInstanceOf(env, jParam, jX942Dh2DeriveParamsClass)) {
        /*
         * CK_X9_42_DH2_DERIVE_PARAMS
         */
        CK_X9_42_DH2_DERIVE_PARAMS_PTR ckpParam;

        ckpParam = (CK_X9_42_DH2_DERIVE_PARAMS_PTR) malloc(sizeof(CK_X9_42_DH2_DERIVE_PARAMS));
        if (ckpParam == NULL) {
            throwOutOfMemoryError(env, 0);
            return;
        }

        /* convert jParameter to CKParameter */
        *ckpParam = jX942Dh2DeriveParamToCKX942Dh2DeriveParam(env, jParam);
        if ((*env)->ExceptionCheck(env)) {
            free(ckpParam);
            return;
        }

        /* get length and pointer of parameter */
        *ckpLength = sizeof(CK_X9_42_DH2_DERIVE_PARAMS);
        *ckpParamPtr = ckpParam;
        return;
    }

    /* if everything faild up to here */
    /* try if the parameter is a primitive Java type */
    jObjectToPrimitiveCKObjectPtrPtr(env, jParam, ckpParamPtr, ckpLength);
    /* *ckpParamPtr = jObjectToCKVoidPtr(jParam); */
    /* *ckpLength = 1; */

    TRACE0("FINISHED\n");
}


/* the mechanism parameter convertion functions: */

/*
 * converts the Java CK_RSA_PKCS_OAEP_PARAMS object to a CK_RSA_PKCS_OAEP_PARAMS structure
 *
 * @param env - used to call JNI funktions to get the Java classes and objects
 * @param jParam - the Java CK_RSA_PKCS_OAEP_PARAMS object to convert
 * @return - the new CK_RSA_PKCS_OAEP_PARAMS structure
 */
CK_RSA_PKCS_OAEP_PARAMS jRsaPkcsOaepParamToCKRsaPkcsOaepParam(JNIEnv *env, jobject jParam)
{
    jclass jRsaPkcsOaepParamsClass;
    CK_RSA_PKCS_OAEP_PARAMS ckParam;
    jfieldID fieldID;
    jlong jHashAlg, jMgf, jSource;
    jobject jSourceData;
    CK_BYTE_PTR ckpByte;
    memset(&ckParam, 0, sizeof(CK_RSA_PKCS_OAEP_PARAMS));

    /* get hashAlg */
    jRsaPkcsOaepParamsClass = (*env)->FindClass(env, CLASS_RSA_PKCS_OAEP_PARAMS);
    if (jRsaPkcsOaepParamsClass == NULL) { return ckParam; }
    fieldID = (*env)->GetFieldID(env, jRsaPkcsOaepParamsClass, "hashAlg", "J");
    if (fieldID == NULL) { return ckParam; }
    jHashAlg = (*env)->GetLongField(env, jParam, fieldID);

    /* get mgf */
    fieldID = (*env)->GetFieldID(env, jRsaPkcsOaepParamsClass, "mgf", "J");
    if (fieldID == NULL) { return ckParam; }
    jMgf = (*env)->GetLongField(env, jParam, fieldID);

    /* get source */
    fieldID = (*env)->GetFieldID(env, jRsaPkcsOaepParamsClass, "source", "J");
    if (fieldID == NULL) { return ckParam; }
    jSource = (*env)->GetLongField(env, jParam, fieldID);

    /* get sourceData and sourceDataLength */
    fieldID = (*env)->GetFieldID(env, jRsaPkcsOaepParamsClass, "pSourceData", "[B");
    if (fieldID == NULL) { return ckParam; }
    jSourceData = (*env)->GetObjectField(env, jParam, fieldID);

    /* populate java values */
    ckParam.hashAlg = jLongToCKULong(jHashAlg);
    ckParam.mgf = jLongToCKULong(jMgf);
    ckParam.source = jLongToCKULong(jSource);
    jByteArrayToCKByteArray(env, jSourceData, & ckpByte, &(ckParam.ulSourceDataLen));
    if ((*env)->ExceptionCheck(env)) { return ckParam; }
    ckParam.pSourceData = (CK_VOID_PTR) ckpByte;

    return ckParam ;
}

/*
 * converts the Java CK_PBE_PARAMS object to a CK_PBE_PARAMS structure
 *
 * @param env - used to call JNI funktions to get the Java classes and objects
 * @param jParam - the Java CK_PBE_PARAMS object to convert
 * @return - the new CK_PBE_PARAMS structure
 */
CK_PBE_PARAMS jPbeParamToCKPbeParam(JNIEnv *env, jobject jParam)
{
    jclass jPbeParamsClass;
    CK_PBE_PARAMS ckParam;
    jfieldID fieldID;
    jlong jIteration;
    jobject jInitVector, jPassword, jSalt;
    CK_ULONG ckTemp;
    memset(&ckParam, 0, sizeof(CK_PBE_PARAMS));

    /* get pInitVector */
    jPbeParamsClass = (*env)->FindClass(env, CLASS_PBE_PARAMS);
    if (jPbeParamsClass == NULL) { return ckParam; }
    fieldID = (*env)->GetFieldID(env, jPbeParamsClass, "pInitVector", "[C");
    if (fieldID == NULL) { return ckParam; }
    jInitVector = (*env)->GetObjectField(env, jParam, fieldID);

    /* get pPassword and ulPasswordLength */
    fieldID = (*env)->GetFieldID(env, jPbeParamsClass, "pPassword", "[C");
    if (fieldID == NULL) { return ckParam; }
    jPassword = (*env)->GetObjectField(env, jParam, fieldID);

    /* get pSalt and ulSaltLength */
    fieldID = (*env)->GetFieldID(env, jPbeParamsClass, "pSalt", "[C");
    if (fieldID == NULL) { return ckParam; }
    jSalt = (*env)->GetObjectField(env, jParam, fieldID);

    /* get ulIteration */
    fieldID = (*env)->GetFieldID(env, jPbeParamsClass, "ulIteration", "J");
    if (fieldID == NULL) { return ckParam; }
    jIteration = (*env)->GetLongField(env, jParam, fieldID);

    /* populate java values */
    ckParam.ulIteration = jLongToCKULong(jIteration);
    jCharArrayToCKCharArray(env, jInitVector, &(ckParam.pInitVector), &ckTemp);
    if ((*env)->ExceptionCheck(env)) { return ckParam; }
    jCharArrayToCKCharArray(env, jPassword, &(ckParam.pPassword), &(ckParam.ulPasswordLen));
    if ((*env)->ExceptionCheck(env)) {
        free(ckParam.pInitVector);
        return ckParam;
    }
    jCharArrayToCKCharArray(env, jSalt, &(ckParam.pSalt), &(ckParam.ulSaltLen));
    if ((*env)->ExceptionCheck(env)) {
        free(ckParam.pInitVector);
        free(ckParam.pPassword);
        return ckParam;
    }

    return ckParam ;
}

/*
 * Copy back the initialization vector from the native structure to the
 * Java object. This is only used for CKM_PBE_* mechanisms and their
 * CK_PBE_PARAMS parameters.
 *
 */
void copyBackPBEInitializationVector(JNIEnv *env, CK_MECHANISM *ckMechanism, jobject jMechanism)
{
    jclass jMechanismClass, jPbeParamsClass;
    CK_PBE_PARAMS *ckParam;
    jfieldID fieldID;
    CK_MECHANISM_TYPE ckMechanismType;
    jlong jMechanismType;
    jobject jParameter;
    jobject jInitVector;
    jint jInitVectorLength;
    CK_CHAR_PTR initVector;
    int i;
    jchar* jInitVectorChars;

    /* get mechanism */
    jMechanismClass = (*env)->FindClass(env, CLASS_MECHANISM);
    if (jMechanismClass == NULL) { return; }
    fieldID = (*env)->GetFieldID(env, jMechanismClass, "mechanism", "J");
    if (fieldID == NULL) { return; }
    jMechanismType = (*env)->GetLongField(env, jMechanism, fieldID);
    ckMechanismType = jLongToCKULong(jMechanismType);
    if (ckMechanismType != ckMechanism->mechanism) {
        /* we do not have maching types, this should not occur */
        return;
    }

    jPbeParamsClass = (*env)->FindClass(env, CLASS_PBE_PARAMS);
    if (jPbeParamsClass == NULL) { return; }
    ckParam = (CK_PBE_PARAMS *) ckMechanism->pParameter;
    if (ckParam != NULL_PTR) {
        initVector = ckParam->pInitVector;
        if (initVector != NULL_PTR) {
            /* get pParameter */
            fieldID = (*env)->GetFieldID(env, jMechanismClass, "pParameter", "Ljava/lang/Object;");
            if (fieldID == NULL) { return; }
            jParameter = (*env)->GetObjectField(env, jMechanism, fieldID);
            fieldID = (*env)->GetFieldID(env, jPbeParamsClass, "pInitVektor", "[C");
            if (fieldID == NULL) { return; }
            jInitVector = (*env)->GetObjectField(env, jParameter, fieldID);

            if (jInitVector != NULL) {
                jInitVectorLength = (*env)->GetArrayLength(env, jInitVector);
                jInitVectorChars = (*env)->GetCharArrayElements(env, jInitVector, NULL);
                if (jInitVectorChars == NULL) { return; }

                /* copy the chars to the Java buffer */
                for (i=0; i < jInitVectorLength; i++) {
                    jInitVectorChars[i] = ckCharToJChar(initVector[i]);
                }
                /* copy back the Java buffer to the object */
                (*env)->ReleaseCharArrayElements(env, jInitVector, jInitVectorChars, 0);
            }
        }
    }
}

/*
 * converts the Java CK_PKCS5_PBKD2_PARAMS object to a CK_PKCS5_PBKD2_PARAMS structure
 *
 * @param env - used to call JNI funktions to get the Java classes and objects
 * @param jParam - the Java CK_PKCS5_PBKD2_PARAMS object to convert
 * @return - the new CK_PKCS5_PBKD2_PARAMS structure
 */
CK_PKCS5_PBKD2_PARAMS jPkcs5Pbkd2ParamToCKPkcs5Pbkd2Param(JNIEnv *env, jobject jParam)
{
    jclass jPkcs5Pbkd2ParamsClass;
    CK_PKCS5_PBKD2_PARAMS ckParam;
    jfieldID fieldID;
    jlong jSaltSource, jIteration, jPrf;
    jobject jSaltSourceData, jPrfData;
    memset(&ckParam, 0, sizeof(CK_PKCS5_PBKD2_PARAMS));

    /* get saltSource */
    jPkcs5Pbkd2ParamsClass = (*env)->FindClass(env, CLASS_PKCS5_PBKD2_PARAMS);
    if (jPkcs5Pbkd2ParamsClass == NULL) { return ckParam; }
    fieldID = (*env)->GetFieldID(env, jPkcs5Pbkd2ParamsClass, "saltSource", "J");
    if (fieldID == NULL) { return ckParam; }
    jSaltSource = (*env)->GetLongField(env, jParam, fieldID);

    /* get pSaltSourceData */
    fieldID = (*env)->GetFieldID(env, jPkcs5Pbkd2ParamsClass, "pSaltSourceData", "[B");
    if (fieldID == NULL) { return ckParam; }
    jSaltSourceData = (*env)->GetObjectField(env, jParam, fieldID);

    /* get iterations */
    fieldID = (*env)->GetFieldID(env, jPkcs5Pbkd2ParamsClass, "iterations", "J");
    if (fieldID == NULL) { return ckParam; }
    jIteration = (*env)->GetLongField(env, jParam, fieldID);

    /* get prf */
    fieldID = (*env)->GetFieldID(env, jPkcs5Pbkd2ParamsClass, "prf", "J");
    if (fieldID == NULL) { return ckParam; }
    jPrf = (*env)->GetLongField(env, jParam, fieldID);

    /* get pPrfData and ulPrfDataLength in byte */
    fieldID = (*env)->GetFieldID(env, jPkcs5Pbkd2ParamsClass, "pPrfData", "[B");
    if (fieldID == NULL) { return ckParam; }
    jPrfData = (*env)->GetObjectField(env, jParam, fieldID);

    /* populate java values */
    ckParam.saltSource = jLongToCKULong(jSaltSource);
    jByteArrayToCKByteArray(env, jSaltSourceData, (CK_BYTE_PTR *) &(ckParam.pSaltSourceData), &(ckParam.ulSaltSourceDataLen));
    if ((*env)->ExceptionCheck(env)) { return ckParam; }
    ckParam.iterations = jLongToCKULong(jIteration);
    ckParam.prf = jLongToCKULong(jPrf);
    jByteArrayToCKByteArray(env, jPrfData, (CK_BYTE_PTR *) &(ckParam.pPrfData), &(ckParam.ulPrfDataLen));
    if ((*env)->ExceptionCheck(env)) {
        free(ckParam.pSaltSourceData);
        return ckParam;
    }

    return ckParam ;
}

/*
 * converts the Java CK_RSA_PKCS_PSS_PARAMS object to a CK_RSA_PKCS_PSS_PARAMS structure
 *
 * @param env - used to call JNI funktions to get the Java classes and objects
 * @param jParam - the Java CK_RSA_PKCS_PSS_PARAMS object to convert
 * @return - the new CK_RSA_PKCS_PSS_PARAMS structure
 */
CK_RSA_PKCS_PSS_PARAMS jRsaPkcsPssParamToCKRsaPkcsPssParam(JNIEnv *env, jobject jParam)
{
    jclass jRsaPkcsPssParamsClass;
    CK_RSA_PKCS_PSS_PARAMS ckParam;
    jfieldID fieldID;
    jlong jHashAlg, jMgf, jSLen;
    memset(&ckParam, 0, sizeof(CK_RSA_PKCS_PSS_PARAMS));

    /* get hashAlg */
    jRsaPkcsPssParamsClass = (*env)->FindClass(env, CLASS_RSA_PKCS_PSS_PARAMS);
    if (jRsaPkcsPssParamsClass == NULL) { return ckParam; }
    fieldID = (*env)->GetFieldID(env, jRsaPkcsPssParamsClass, "hashAlg", "J");
    if (fieldID == NULL) { return ckParam; }
    jHashAlg = (*env)->GetLongField(env, jParam, fieldID);

    /* get mgf */
    fieldID = (*env)->GetFieldID(env, jRsaPkcsPssParamsClass, "mgf", "J");
    if (fieldID == NULL) { return ckParam; }
    jMgf = (*env)->GetLongField(env, jParam, fieldID);

    /* get sLen */
    fieldID = (*env)->GetFieldID(env, jRsaPkcsPssParamsClass, "sLen", "J");
    if (fieldID == NULL) { return ckParam; }
    jSLen = (*env)->GetLongField(env, jParam, fieldID);

    /* populate java values */
    ckParam.hashAlg = jLongToCKULong(jHashAlg);
    ckParam.mgf = jLongToCKULong(jMgf);
    ckParam.sLen = jLongToCKULong(jSLen);

    return ckParam ;
}

/*
 * converts the Java CK_ECDH1_DERIVE_PARAMS object to a CK_ECDH1_DERIVE_PARAMS structure
 *
 * @param env - used to call JNI funktions to get the Java classes and objects
 * @param jParam - the Java CK_ECDH1_DERIVE_PARAMS object to convert
 * @return - the new CK_ECDH1_DERIVE_PARAMS structure
 */
CK_ECDH1_DERIVE_PARAMS jEcdh1DeriveParamToCKEcdh1DeriveParam(JNIEnv *env, jobject jParam)
{
    jclass jEcdh1DeriveParamsClass;
    CK_ECDH1_DERIVE_PARAMS ckParam;
    jfieldID fieldID;
    jlong jLong;
    jobject jSharedData, jPublicData;
    memset(&ckParam, 0, sizeof(CK_ECDH1_DERIVE_PARAMS));

    /* get kdf */
    jEcdh1DeriveParamsClass = (*env)->FindClass(env, CLASS_ECDH1_DERIVE_PARAMS);
    if (jEcdh1DeriveParamsClass == NULL) { return ckParam; }
    fieldID = (*env)->GetFieldID(env, jEcdh1DeriveParamsClass, "kdf", "J");
    if (fieldID == NULL) { return ckParam; }
    jLong = (*env)->GetLongField(env, jParam, fieldID);
    ckParam.kdf = jLongToCKULong(jLong);

    /* get pSharedData and ulSharedDataLen */
    fieldID = (*env)->GetFieldID(env, jEcdh1DeriveParamsClass, "pSharedData", "[B");
    if (fieldID == NULL) { return ckParam; }
    jSharedData = (*env)->GetObjectField(env, jParam, fieldID);

    /* get pPublicData and ulPublicDataLen */
    fieldID = (*env)->GetFieldID(env, jEcdh1DeriveParamsClass, "pPublicData", "[B");
    if (fieldID == NULL) { return ckParam; }
    jPublicData = (*env)->GetObjectField(env, jParam, fieldID);

    /* populate java values */
    ckParam.kdf = jLongToCKULong(jLong);
    jByteArrayToCKByteArray(env, jSharedData, &(ckParam.pSharedData), &(ckParam.ulSharedDataLen));
    if ((*env)->ExceptionCheck(env)) { return ckParam; }
    jByteArrayToCKByteArray(env, jPublicData, &(ckParam.pPublicData), &(ckParam.ulPublicDataLen));
    if ((*env)->ExceptionCheck(env)) {
        free(ckParam.pSharedData);
        return ckParam;
    }

    return ckParam ;
}

/*
 * converts the Java CK_ECDH2_DERIVE_PARAMS object to a CK_ECDH2_DERIVE_PARAMS structure
 *
 * @param env - used to call JNI funktions to get the Java classes and objects
 * @param jParam - the Java CK_ECDH2_DERIVE_PARAMS object to convert
 * @return - the new CK_ECDH2_DERIVE_PARAMS structure
 */
CK_ECDH2_DERIVE_PARAMS jEcdh2DeriveParamToCKEcdh2DeriveParam(JNIEnv *env, jobject jParam)
{
    jclass jEcdh2DeriveParamsClass;
    CK_ECDH2_DERIVE_PARAMS ckParam;
    jfieldID fieldID;
    jlong jKdf, jPrivateDataLen, jPrivateData;
    jobject jSharedData, jPublicData, jPublicData2;
    memset(&ckParam, 0, sizeof(CK_ECDH2_DERIVE_PARAMS));

    /* get kdf */
    jEcdh2DeriveParamsClass = (*env)->FindClass(env, CLASS_ECDH2_DERIVE_PARAMS);
    if (jEcdh2DeriveParamsClass == NULL) { return ckParam; }
    fieldID = (*env)->GetFieldID(env, jEcdh2DeriveParamsClass, "kdf", "J");
    if (fieldID == NULL) { return ckParam; }
    jKdf = (*env)->GetLongField(env, jParam, fieldID);

    /* get pSharedData and ulSharedDataLen */
    fieldID = (*env)->GetFieldID(env, jEcdh2DeriveParamsClass, "pSharedData", "[B");
    if (fieldID == NULL) { return ckParam; }
    jSharedData = (*env)->GetObjectField(env, jParam, fieldID);

    /* get pPublicData and ulPublicDataLen */
    fieldID = (*env)->GetFieldID(env, jEcdh2DeriveParamsClass, "pPublicData", "[B");
    if (fieldID == NULL) { return ckParam; }
    jPublicData = (*env)->GetObjectField(env, jParam, fieldID);

    /* get ulPrivateDataLen */
    fieldID = (*env)->GetFieldID(env, jEcdh2DeriveParamsClass, "ulPrivateDataLen", "J");
    if (fieldID == NULL) { return ckParam; }
    jPrivateDataLen = (*env)->GetLongField(env, jParam, fieldID);

    /* get hPrivateData */
    fieldID = (*env)->GetFieldID(env, jEcdh2DeriveParamsClass, "hPrivateData", "J");
    if (fieldID == NULL) { return ckParam; }
    jPrivateData = (*env)->GetLongField(env, jParam, fieldID);

    /* get pPublicData2 and ulPublicDataLen2 */
    fieldID = (*env)->GetFieldID(env, jEcdh2DeriveParamsClass, "pPublicData2", "[B");
    if (fieldID == NULL) { return ckParam; }
    jPublicData2 = (*env)->GetObjectField(env, jParam, fieldID);

    /* populate java values */
    ckParam.kdf = jLongToCKULong(jKdf);
    jByteArrayToCKByteArray(env, jSharedData, &(ckParam.pSharedData), &(ckParam.ulSharedDataLen));
    if ((*env)->ExceptionCheck(env)) { return ckParam; }
    jByteArrayToCKByteArray(env, jPublicData, &(ckParam.pPublicData), &(ckParam.ulPublicDataLen));
    if ((*env)->ExceptionCheck(env)) {
        free(ckParam.pSharedData);
        return ckParam;
    }
    ckParam.ulPrivateDataLen = jLongToCKULong(jPrivateDataLen);
    ckParam.hPrivateData = jLongToCKULong(jPrivateData);
    jByteArrayToCKByteArray(env, jPublicData2, &(ckParam.pPublicData2), &(ckParam.ulPublicDataLen2));
    if ((*env)->ExceptionCheck(env)) {
        free(ckParam.pSharedData);
        free(ckParam.pPublicData);
        return ckParam;
    }
    return ckParam ;
}

/*
 * converts the Java CK_X9_42_DH1_DERIVE_PARAMS object to a CK_X9_42_DH1_DERIVE_PARAMS structure
 *
 * @param env - used to call JNI funktions to get the Java classes and objects
 * @param jParam - the Java CK_X9_42_DH1_DERIVE_PARAMS object to convert
 * @return - the new CK_X9_42_DH1_DERIVE_PARAMS structure
 */
CK_X9_42_DH1_DERIVE_PARAMS jX942Dh1DeriveParamToCKX942Dh1DeriveParam(JNIEnv *env, jobject jParam)
{
    jclass jX942Dh1DeriveParamsClass;
    CK_X9_42_DH1_DERIVE_PARAMS ckParam;
    jfieldID fieldID;
    jlong jKdf;
    jobject jOtherInfo, jPublicData;
    memset(&ckParam, 0, sizeof(CK_X9_42_DH1_DERIVE_PARAMS));

    /* get kdf */
    jX942Dh1DeriveParamsClass = (*env)->FindClass(env, CLASS_X9_42_DH1_DERIVE_PARAMS);
    if (jX942Dh1DeriveParamsClass == NULL) { return ckParam; }
    fieldID = (*env)->GetFieldID(env, jX942Dh1DeriveParamsClass, "kdf", "J");
    if (fieldID == NULL) { return ckParam; }
    jKdf = (*env)->GetLongField(env, jParam, fieldID);

    /* get pOtherInfo and ulOtherInfoLen */
    fieldID = (*env)->GetFieldID(env, jX942Dh1DeriveParamsClass, "pOtherInfo", "[B");
    if (fieldID == NULL) { return ckParam; }
    jOtherInfo = (*env)->GetObjectField(env, jParam, fieldID);

    /* get pPublicData and ulPublicDataLen */
    fieldID = (*env)->GetFieldID(env, jX942Dh1DeriveParamsClass, "pPublicData", "[B");
    if (fieldID == NULL) { return ckParam; }
    jPublicData = (*env)->GetObjectField(env, jParam, fieldID);

    /* populate java values */
    ckParam.kdf = jLongToCKULong(jKdf);
    jByteArrayToCKByteArray(env, jOtherInfo, &(ckParam.pOtherInfo), &(ckParam.ulOtherInfoLen));
    if ((*env)->ExceptionCheck(env)) { return ckParam; }
    jByteArrayToCKByteArray(env, jPublicData, &(ckParam.pPublicData), &(ckParam.ulPublicDataLen));
    if ((*env)->ExceptionCheck(env)) {
        free(ckParam.pOtherInfo);
        return ckParam;
    }

    return ckParam ;
}

/*
 * converts the Java CK_X9_42_DH2_DERIVE_PARAMS object to a CK_X9_42_DH2_DERIVE_PARAMS structure
 *
 * @param env - used to call JNI funktions to get the Java classes and objects
 * @param jParam - the Java CK_X9_42_DH2_DERIVE_PARAMS object to convert
 * @return - the new CK_X9_42_DH2_DERIVE_PARAMS structure
 */
CK_X9_42_DH2_DERIVE_PARAMS jX942Dh2DeriveParamToCKX942Dh2DeriveParam(JNIEnv *env, jobject jParam)
{
    jclass jX942Dh2DeriveParamsClass;
    CK_X9_42_DH2_DERIVE_PARAMS ckParam;
    jfieldID fieldID;
    jlong jKdf, jPrivateDataLen, jPrivateData;
    jobject jOtherInfo, jPublicData, jPublicData2;
    memset(&ckParam, 0, sizeof(CK_X9_42_DH2_DERIVE_PARAMS));

    /* get kdf */
    jX942Dh2DeriveParamsClass = (*env)->FindClass(env, CLASS_X9_42_DH2_DERIVE_PARAMS);
    if (jX942Dh2DeriveParamsClass == NULL) { return ckParam; }
    fieldID = (*env)->GetFieldID(env, jX942Dh2DeriveParamsClass, "kdf", "J");
    if (fieldID == NULL) { return ckParam; }
    jKdf = (*env)->GetLongField(env, jParam, fieldID);

    /* get pOtherInfo and ulOtherInfoLen */
    fieldID = (*env)->GetFieldID(env, jX942Dh2DeriveParamsClass, "pOtherInfo", "[B");
    if (fieldID == NULL) { return ckParam; }
    jOtherInfo = (*env)->GetObjectField(env, jParam, fieldID);

    /* get pPublicData and ulPublicDataLen */
    fieldID = (*env)->GetFieldID(env, jX942Dh2DeriveParamsClass, "pPublicData", "[B");
    if (fieldID == NULL) { return ckParam; }
    jPublicData = (*env)->GetObjectField(env, jParam, fieldID);

    /* get ulPrivateDataLen */
    fieldID = (*env)->GetFieldID(env, jX942Dh2DeriveParamsClass, "ulPrivateDataLen", "J");
    if (fieldID == NULL) { return ckParam; }
    jPrivateDataLen = (*env)->GetLongField(env, jParam, fieldID);

    /* get hPrivateData */
    fieldID = (*env)->GetFieldID(env, jX942Dh2DeriveParamsClass, "hPrivateData", "J");
    if (fieldID == NULL) { return ckParam; }
    jPrivateData = (*env)->GetLongField(env, jParam, fieldID);

    /* get pPublicData2 and ulPublicDataLen2 */
    fieldID = (*env)->GetFieldID(env, jX942Dh2DeriveParamsClass, "pPublicData2", "[B");
    if (fieldID == NULL) { return ckParam; }
    jPublicData2 = (*env)->GetObjectField(env, jParam, fieldID);

    /* populate java values */
    ckParam.kdf = jLongToCKULong(jKdf);
    jByteArrayToCKByteArray(env, jOtherInfo, &(ckParam.pOtherInfo), &(ckParam.ulOtherInfoLen));
    if ((*env)->ExceptionCheck(env)) { return ckParam; }
    jByteArrayToCKByteArray(env, jPublicData, &(ckParam.pPublicData), &(ckParam.ulPublicDataLen));
    if ((*env)->ExceptionCheck(env)) {
        free(ckParam.pOtherInfo);
        return ckParam;
    }
    ckParam.ulPrivateDataLen = jLongToCKULong(jPrivateDataLen);
    ckParam.hPrivateData = jLongToCKULong(jPrivateData);
    jByteArrayToCKByteArray(env, jPublicData2, &(ckParam.pPublicData2), &(ckParam.ulPublicDataLen2));
    if ((*env)->ExceptionCheck(env)) {
        free(ckParam.pOtherInfo);
        free(ckParam.pPublicData);
        return ckParam;
    }

    return ckParam ;
}
