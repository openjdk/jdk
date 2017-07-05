/*
 * Portions Copyright 2003-2005 Sun Microsystems, Inc.  All Rights Reserved.
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
    assert(jDateClass != 0);

    /* load CK_DATE constructor */
    jCtrId = (*env)->GetMethodID(env, jDateClass, "<init>", "([C[C[C)V");
    assert(jCtrId != 0);

    /* prep all fields */
    jYear = ckCharArrayToJCharArray(env, (CK_CHAR_PTR)(ckpDate->year), 4);
    jMonth = ckCharArrayToJCharArray(env, (CK_CHAR_PTR)(ckpDate->month), 2);
    jDay = ckCharArrayToJCharArray(env, (CK_CHAR_PTR)(ckpDate->day), 2);

    /* create new CK_DATE object */
    jDateObject =
      (*env)->NewObject(env, jDateClass, jCtrId, jYear, jMonth, jDay);
    assert(jDateObject != 0);

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
    assert(jVersionClass != 0);

    /* load CK_VERSION constructor */
    jCtrId = (*env)->GetMethodID(env, jVersionClass, "<init>", "(II)V");
    assert(jCtrId != 0);

    /* prep both fields */
    jMajor = ckpVersion->major;
    jMinor = ckpVersion->minor;

    /* create new CK_VERSION object */
    jVersionObject =
      (*env)->NewObject(env, jVersionClass, jCtrId, jMajor, jMinor);
    assert(jVersionObject != 0);

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
    assert(jSessionInfoClass != 0);

    /* load CK_SESSION_INFO constructor */
    jCtrId = (*env)->GetMethodID(env, jSessionInfoClass, "<init>", "(JJJJ)V");
    assert(jCtrId != 0);

    /* prep all fields */
    jSlotID = ckULongToJLong(ckpSessionInfo->slotID);
    jState = ckULongToJLong(ckpSessionInfo->state);
    jFlags = ckULongToJLong(ckpSessionInfo->flags);
    jDeviceError = ckULongToJLong(ckpSessionInfo->ulDeviceError);

    /* create new CK_SESSION_INFO object */
    jSessionInfoObject =
      (*env)->NewObject(env, jSessionInfoClass, jCtrId, jSlotID, jState,
                        jFlags, jDeviceError);
    assert(jSessionInfoObject != 0);

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
    assert(jAttributeClass != 0);

    /* load CK_INFO constructor */
    jCtrId = (*env)->GetMethodID(env, jAttributeClass, "<init>", "(JLjava/lang/Object;)V");
    assert(jCtrId != 0);

    /* prep both fields */
    jType = ckULongToJLong(ckpAttribute->type);
    jPValue = ckAttributeValueToJObject(env, ckpAttribute);

    /* create new CK_ATTRIBUTE object */
    jAttributeObject =
      (*env)->NewObject(env, jAttributeClass, jCtrId, jType, jPValue);
    assert(jAttributeObject != 0);

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

    /* allocate memory for CK_VERSION pointer */
    ckpVersion = (CK_VERSION_PTR) malloc(sizeof(CK_VERSION));

    /* get CK_VERSION class */
    jVersionClass = (*env)->GetObjectClass(env, jVersion);
    assert(jVersionClass != 0);

    /* get Major */
    jFieldID = (*env)->GetFieldID(env, jVersionClass, "major", "B");
    assert(jFieldID != 0);
    jMajor = (*env)->GetByteField(env, jVersion, jFieldID);
    ckpVersion->major = jByteToCKByte(jMajor);

    /* get Minor */
    jFieldID = (*env)->GetFieldID(env, jVersionClass, "minor", "B");
    assert(jFieldID != 0);
    jMinor = (*env)->GetByteField(env, jVersion, jFieldID);
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

    /* allocate memory for CK_DATE pointer */
    ckpDate = (CK_DATE *) malloc(sizeof(CK_DATE));

    /* get CK_DATE class */
    jDateClass = (*env)->FindClass(env, CLASS_DATE);
    assert(jDateClass != 0);

    /* get Year */
    jFieldID = (*env)->GetFieldID(env, jDateClass, "year", "[C");
    assert(jFieldID != 0);
    jYear = (*env)->GetObjectField(env, jDate, jFieldID);

    if (jYear == NULL) {
        ckpDate->year[0] = 0;
        ckpDate->year[1] = 0;
        ckpDate->year[2] = 0;
        ckpDate->year[3] = 0;
    } else {
        ckLength = (*env)->GetArrayLength(env, jYear);
        jTempChars = (jchar*) malloc((ckLength) * sizeof(jchar));
        (*env)->GetCharArrayRegion(env, jYear, 0, ckLength, jTempChars);
        for (i = 0; (i < ckLength) && (i < 4) ; i++) {
            ckpDate->year[i] = jCharToCKChar(jTempChars[i]);
        }
        free(jTempChars);
    }

    /* get Month */
    jFieldID = (*env)->GetFieldID(env, jDateClass, "month", "[C");
    assert(jFieldID != 0);
    jMonth = (*env)->GetObjectField(env, jDate, jFieldID);

    if (jMonth == NULL) {
        ckpDate->month[0] = 0;
        ckpDate->month[1] = 0;
    } else {
        ckLength = (*env)->GetArrayLength(env, jMonth);
        jTempChars = (jchar*) malloc((ckLength) * sizeof(jchar));
        (*env)->GetCharArrayRegion(env, jMonth, 0, ckLength, jTempChars);
        for (i = 0; (i < ckLength) && (i < 4) ; i++) {
            ckpDate->month[i] = jCharToCKChar(jTempChars[i]);
        }
        free(jTempChars);
    }

    /* get Day */
    jFieldID = (*env)->GetFieldID(env, jDateClass, "day", "[C");
    assert(jFieldID != 0);
    jDay = (*env)->GetObjectField(env, jDate, jFieldID);

    if (jDay == NULL) {
        ckpDate->day[0] = 0;
        ckpDate->day[1] = 0;
    } else {
        ckLength = (*env)->GetArrayLength(env, jDay);
        jTempChars = (jchar*) malloc((ckLength) * sizeof(jchar));
        (*env)->GetCharArrayRegion(env, jDay, 0, ckLength, jTempChars);
        for (i = 0; (i < ckLength) && (i < 4) ; i++) {
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

    TRACE0("\nDEBUG: jAttributeToCKAttribute");
    /* get CK_ATTRIBUTE class */
    TRACE0(", getting attribute object class");
    jAttributeClass = (*env)->GetObjectClass(env, jAttribute);
    assert(jAttributeClass != 0);

    /* get type */
    TRACE0(", getting type field");
    jFieldID = (*env)->GetFieldID(env, jAttributeClass, "type", "J");
    assert(jFieldID != 0);
    jType = (*env)->GetLongField(env, jAttribute, jFieldID);
    TRACE1(", type=0x%X", jType);

    /* get pValue */
    TRACE0(", getting pValue field");
    jFieldID = (*env)->GetFieldID(env, jAttributeClass, "pValue", "Ljava/lang/Object;");
    assert(jFieldID != 0);
    jPValue = (*env)->GetObjectField(env, jAttribute, jFieldID);
    TRACE1(", pValue=%p", jPValue);

    ckAttribute.type = jLongToCKULong(jType);
    TRACE0(", converting pValue to primitive object");

    /* convert the Java pValue object to a CK-type pValue pointer */
    jObjectToPrimitiveCKObjectPtrPtr(env, jPValue, &(ckAttribute.pValue), &(ckAttribute.ulValueLen));

    TRACE0("\nFINISHED\n");

    return ckAttribute ;
}

/*
 * converts the Java CK_SSL3_MASTER_KEY_DERIVE_PARAMS object to a
 * CK_SSL3_MASTER_KEY_DERIVE_PARAMS structure
 *
 * @param env - used to call JNI funktions to get the Java classes and objects
 * @param jParam - the Java CK_SSL3_MASTER_KEY_DERIVE_PARAMS object to convert
 * @return - the new CK_SSL3_MASTER_KEY_DERIVE_PARAMS structure
 */
CK_SSL3_MASTER_KEY_DERIVE_PARAMS jSsl3MasterKeyDeriveParamToCKSsl3MasterKeyDeriveParam(JNIEnv *env, jobject jParam)
{
    // XXX don't return structs
    // XXX prefetch class and field ids
    jclass jSsl3MasterKeyDeriveParamsClass = (*env)->FindClass(env, CLASS_SSL3_MASTER_KEY_DERIVE_PARAMS);
    CK_SSL3_MASTER_KEY_DERIVE_PARAMS ckParam;
    jfieldID fieldID;
    jobject jObject;
    jclass jSsl3RandomDataClass;
    jobject jRandomInfo;

    /* get RandomInfo */
    jSsl3RandomDataClass = (*env)->FindClass(env, CLASS_SSL3_RANDOM_DATA);
    fieldID = (*env)->GetFieldID(env, jSsl3MasterKeyDeriveParamsClass, "RandomInfo", "Lsun/security/pkcs11/wrapper/CK_SSL3_RANDOM_DATA;");
    assert(fieldID != 0);
    jRandomInfo = (*env)->GetObjectField(env, jParam, fieldID);

    /* get pClientRandom and ulClientRandomLength out of RandomInfo */
    fieldID = (*env)->GetFieldID(env, jSsl3RandomDataClass, "pClientRandom", "[B");
    assert(fieldID != 0);
    jObject = (*env)->GetObjectField(env, jRandomInfo, fieldID);
    jByteArrayToCKByteArray(env, jObject, &(ckParam.RandomInfo.pClientRandom), &(ckParam.RandomInfo.ulClientRandomLen));

    /* get pServerRandom and ulServerRandomLength out of RandomInfo */
    fieldID = (*env)->GetFieldID(env, jSsl3RandomDataClass, "pServerRandom", "[B");
    assert(fieldID != 0);
    jObject = (*env)->GetObjectField(env, jRandomInfo, fieldID);
    jByteArrayToCKByteArray(env, jObject, &(ckParam.RandomInfo.pServerRandom), &(ckParam.RandomInfo.ulServerRandomLen));

    /* get pVersion */
    fieldID = (*env)->GetFieldID(env, jSsl3MasterKeyDeriveParamsClass, "pVersion",  "Lsun/security/pkcs11/wrapper/CK_VERSION;");
    assert(fieldID != 0);
    jObject = (*env)->GetObjectField(env, jParam, fieldID);
    ckParam.pVersion = jVersionToCKVersionPtr(env, jObject);

    return ckParam ;
}


/*
 * converts the Java CK_TLS_PRF_PARAMS object to a CK_TLS_PRF_PARAMS structure
 */
CK_TLS_PRF_PARAMS jTlsPrfParamsToCKTlsPrfParam(JNIEnv *env, jobject jParam)
{
    jclass jTlsPrfParamsClass = (*env)->FindClass(env, CLASS_TLS_PRF_PARAMS);
    CK_TLS_PRF_PARAMS ckParam;
    jfieldID fieldID;
    jobject jObject;

    fieldID = (*env)->GetFieldID(env, jTlsPrfParamsClass, "pSeed", "[B");
    assert(fieldID != 0);
    jObject = (*env)->GetObjectField(env, jParam, fieldID);
    jByteArrayToCKByteArray(env, jObject, &(ckParam.pSeed), &(ckParam.ulSeedLen));

    fieldID = (*env)->GetFieldID(env, jTlsPrfParamsClass, "pLabel", "[B");
    assert(fieldID != 0);
    jObject = (*env)->GetObjectField(env, jParam, fieldID);
    jByteArrayToCKByteArray(env, jObject, &(ckParam.pLabel), &(ckParam.ulLabelLen));

    ckParam.pulOutputLen = malloc(sizeof(CK_ULONG));

    fieldID = (*env)->GetFieldID(env, jTlsPrfParamsClass, "pOutput", "[B");
    assert(fieldID != 0);
    jObject = (*env)->GetObjectField(env, jParam, fieldID);
    jByteArrayToCKByteArray(env, jObject, &(ckParam.pOutput), ckParam.pulOutputLen);

    return ckParam ;
}

/*
 * converts the Java CK_SSL3_KEY_MAT_PARAMS object to a CK_SSL3_KEY_MAT_PARAMS structure
 *
 * @param env - used to call JNI funktions to get the Java classes and objects
 * @param jParam - the Java CK_SSL3_KEY_MAT_PARAMS object to convert
 * @return - the new CK_SSL3_KEY_MAT_PARAMS structure
 */
CK_SSL3_KEY_MAT_PARAMS jSsl3KeyMatParamToCKSsl3KeyMatParam(JNIEnv *env, jobject jParam)
{
    // XXX don't return structs
    // XXX prefetch class and field ids
    jclass jSsl3KeyMatParamsClass = (*env)->FindClass(env, CLASS_SSL3_KEY_MAT_PARAMS);
    CK_SSL3_KEY_MAT_PARAMS ckParam;
    jfieldID fieldID;
    jlong jLong;
    jboolean jBoolean;
    jobject jObject;
    jobject jRandomInfo;
    jobject jReturnedKeyMaterial;
    jclass jSsl3RandomDataClass;
    jclass jSsl3KeyMatOutClass;
    CK_ULONG ckTemp;

    /* get ulMacSizeInBits */
    fieldID = (*env)->GetFieldID(env, jSsl3KeyMatParamsClass, "ulMacSizeInBits", "J");
    assert(fieldID != 0);
    jLong = (*env)->GetLongField(env, jParam, fieldID);
    ckParam.ulMacSizeInBits = jLongToCKULong(jLong);

    /* get ulKeySizeInBits */
    fieldID = (*env)->GetFieldID(env, jSsl3KeyMatParamsClass, "ulKeySizeInBits", "J");
    assert(fieldID != 0);
    jLong = (*env)->GetLongField(env, jParam, fieldID);
    ckParam.ulKeySizeInBits = jLongToCKULong(jLong);

    /* get ulIVSizeInBits */
    fieldID = (*env)->GetFieldID(env, jSsl3KeyMatParamsClass, "ulIVSizeInBits", "J");
    assert(fieldID != 0);
    jLong = (*env)->GetLongField(env, jParam, fieldID);
    ckParam.ulIVSizeInBits = jLongToCKULong(jLong);

    /* get bIsExport */
    fieldID = (*env)->GetFieldID(env, jSsl3KeyMatParamsClass, "bIsExport", "Z");
    assert(fieldID != 0);
    jBoolean = (*env)->GetBooleanField(env, jParam, fieldID);
    ckParam.bIsExport = jBooleanToCKBBool(jBoolean);

    /* get RandomInfo */
    jSsl3RandomDataClass = (*env)->FindClass(env, CLASS_SSL3_RANDOM_DATA);
    fieldID = (*env)->GetFieldID(env, jSsl3KeyMatParamsClass, "RandomInfo",  "Lsun/security/pkcs11/wrapper/CK_SSL3_RANDOM_DATA;");
    assert(fieldID != 0);
    jRandomInfo = (*env)->GetObjectField(env, jParam, fieldID);

    /* get pClientRandom and ulClientRandomLength out of RandomInfo */
    fieldID = (*env)->GetFieldID(env, jSsl3RandomDataClass, "pClientRandom", "[B");
    assert(fieldID != 0);
    jObject = (*env)->GetObjectField(env, jRandomInfo, fieldID);
    jByteArrayToCKByteArray(env, jObject, &(ckParam.RandomInfo.pClientRandom), &(ckParam.RandomInfo.ulClientRandomLen));

    /* get pServerRandom and ulServerRandomLength out of RandomInfo */
    fieldID = (*env)->GetFieldID(env, jSsl3RandomDataClass, "pServerRandom", "[B");
    assert(fieldID != 0);
    jObject = (*env)->GetObjectField(env, jRandomInfo, fieldID);
    jByteArrayToCKByteArray(env, jObject, &(ckParam.RandomInfo.pServerRandom), &(ckParam.RandomInfo.ulServerRandomLen));

    /* get pReturnedKeyMaterial */
    jSsl3KeyMatOutClass = (*env)->FindClass(env, CLASS_SSL3_KEY_MAT_OUT);
    fieldID = (*env)->GetFieldID(env, jSsl3KeyMatParamsClass, "pReturnedKeyMaterial",  "Lsun/security/pkcs11/wrapper/CK_SSL3_KEY_MAT_OUT;");
    assert(fieldID != 0);
    jReturnedKeyMaterial = (*env)->GetObjectField(env, jParam, fieldID);

    /* allocate memory for pRetrunedKeyMaterial */
    ckParam.pReturnedKeyMaterial = (CK_SSL3_KEY_MAT_OUT_PTR) malloc(sizeof(CK_SSL3_KEY_MAT_OUT));

    // the handles are output params only, no need to fetch them from Java
    ckParam.pReturnedKeyMaterial->hClientMacSecret = 0;
    ckParam.pReturnedKeyMaterial->hServerMacSecret = 0;
    ckParam.pReturnedKeyMaterial->hClientKey = 0;
    ckParam.pReturnedKeyMaterial->hServerKey = 0;

    /* get pIVClient out of pReturnedKeyMaterial */
    fieldID = (*env)->GetFieldID(env, jSsl3KeyMatOutClass, "pIVClient", "[B");
    assert(fieldID != 0);
    jObject = (*env)->GetObjectField(env, jReturnedKeyMaterial, fieldID);
    jByteArrayToCKByteArray(env, jObject, &(ckParam.pReturnedKeyMaterial->pIVClient), &ckTemp);

    /* get pIVServer out of pReturnedKeyMaterial */
    fieldID = (*env)->GetFieldID(env, jSsl3KeyMatOutClass, "pIVServer", "[B");
    assert(fieldID != 0);
    jObject = (*env)->GetObjectField(env, jReturnedKeyMaterial, fieldID);
    jByteArrayToCKByteArray(env, jObject, &(ckParam.pReturnedKeyMaterial->pIVServer), &ckTemp);

    return ckParam ;
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
        /* printf("slow path jMechanismParameterToCKMechanismParameter\n"); */
        jMechanismParameterToCKMechanismParameterSlow(env, jParam, ckpParamPtr, ckpLength);
    }
}

void jMechanismParameterToCKMechanismParameterSlow(JNIEnv *env, jobject jParam, CK_VOID_PTR *ckpParamPtr, CK_ULONG *ckpLength)
{
    /* get all Java mechanism parameter classes */
    jclass jVersionClass    = (*env)->FindClass(env, CLASS_VERSION);
    jclass jRsaPkcsOaepParamsClass = (*env)->FindClass(env, CLASS_RSA_PKCS_OAEP_PARAMS);
    jclass jPbeParamsClass = (*env)->FindClass(env, CLASS_PBE_PARAMS);
    jclass jPkcs5Pbkd2ParamsClass = (*env)->FindClass(env, CLASS_PKCS5_PBKD2_PARAMS);

    jclass jRsaPkcsPssParamsClass = (*env)->FindClass(env, CLASS_RSA_PKCS_PSS_PARAMS);
    jclass jEcdh1DeriveParamsClass = (*env)->FindClass(env, CLASS_ECDH1_DERIVE_PARAMS);
    jclass jEcdh2DeriveParamsClass = (*env)->FindClass(env, CLASS_ECDH2_DERIVE_PARAMS);
    jclass jX942Dh1DeriveParamsClass = (*env)->FindClass(env, CLASS_X9_42_DH1_DERIVE_PARAMS);
    jclass jX942Dh2DeriveParamsClass = (*env)->FindClass(env, CLASS_X9_42_DH2_DERIVE_PARAMS);

    jclass jSsl3MasterKeyDeriveParamsClass = (*env)->FindClass(env, CLASS_SSL3_MASTER_KEY_DERIVE_PARAMS);
    jclass jSsl3KeyMatParamsClass = (*env)->FindClass(env, CLASS_SSL3_KEY_MAT_PARAMS);
    jclass jTlsPrfParamsClass = (*env)->FindClass(env, CLASS_TLS_PRF_PARAMS);

    TRACE0("\nDEBUG: jMechanismParameterToCKMechanismParameter");

    /* first check the most common cases */
/*
    if (jParam == NULL) {
        *ckpParamPtr = NULL;
        *ckpLength = 0;
    } else if ((*env)->IsInstanceOf(env, jParam, jByteArrayClass)) {
        jByteArrayToCKByteArray(env, jParam, (CK_BYTE_PTR *)ckpParamPtr, ckpLength);
    } else if ((*env)->IsInstanceOf(env, jParam, jLongClass)) {
        *ckpParamPtr = jLongObjectToCKULongPtr(env, jParam);
        *ckpLength = sizeof(CK_ULONG);
    } else if ((*env)->IsInstanceOf(env, jParam, jVersionClass)) {
*/
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

    } else if ((*env)->IsInstanceOf(env, jParam, jSsl3MasterKeyDeriveParamsClass)) {
        /*
         * CK_SSL3_MASTER_KEY_DERIVE_PARAMS
         */

        CK_SSL3_MASTER_KEY_DERIVE_PARAMS_PTR ckpParam;

        ckpParam = (CK_SSL3_MASTER_KEY_DERIVE_PARAMS_PTR) malloc(sizeof(CK_SSL3_MASTER_KEY_DERIVE_PARAMS));

        /* convert jParameter to CKParameter */
        *ckpParam = jSsl3MasterKeyDeriveParamToCKSsl3MasterKeyDeriveParam(env, jParam);

        /* get length and pointer of parameter */
        *ckpLength = sizeof(CK_SSL3_MASTER_KEY_DERIVE_PARAMS);
        *ckpParamPtr = ckpParam;

    } else if ((*env)->IsInstanceOf(env, jParam, jSsl3KeyMatParamsClass)) {
        /*
         * CK_SSL3_KEY_MAT_PARAMS
         */

        CK_SSL3_KEY_MAT_PARAMS_PTR ckpParam;

        ckpParam = (CK_SSL3_KEY_MAT_PARAMS_PTR) malloc(sizeof(CK_SSL3_KEY_MAT_PARAMS));

        /* convert jParameter to CKParameter */
        *ckpParam = jSsl3KeyMatParamToCKSsl3KeyMatParam(env, jParam);

        /* get length and pointer of parameter */
        *ckpLength = sizeof(CK_SSL3_KEY_MAT_PARAMS);
        *ckpParamPtr = ckpParam;

    } else if ((*env)->IsInstanceOf(env, jParam, jTlsPrfParamsClass)) {
        //
        // CK_TLS_PRF_PARAMS
        //

        CK_TLS_PRF_PARAMS_PTR ckpParam;

        ckpParam = (CK_TLS_PRF_PARAMS_PTR) malloc(sizeof(CK_TLS_PRF_PARAMS));

        // convert jParameter to CKParameter
        *ckpParam = jTlsPrfParamsToCKTlsPrfParam(env, jParam);

        // get length and pointer of parameter
        *ckpLength = sizeof(CK_TLS_PRF_PARAMS);
        *ckpParamPtr = ckpParam;

    } else if ((*env)->IsInstanceOf(env, jParam, jRsaPkcsOaepParamsClass)) {
        /*
         * CK_RSA_PKCS_OAEP_PARAMS
         */

        CK_RSA_PKCS_OAEP_PARAMS_PTR ckpParam;

        ckpParam = (CK_RSA_PKCS_OAEP_PARAMS_PTR) malloc(sizeof(CK_RSA_PKCS_OAEP_PARAMS));

        /* convert jParameter to CKParameter */
        *ckpParam = jRsaPkcsOaepParamToCKRsaPkcsOaepParam(env, jParam);

        /* get length and pointer of parameter */
        *ckpLength = sizeof(CK_RSA_PKCS_OAEP_PARAMS);
        *ckpParamPtr = ckpParam;

    } else if ((*env)->IsInstanceOf(env, jParam, jPbeParamsClass)) {
        /*
         * CK_PBE_PARAMS
         */

        CK_PBE_PARAMS_PTR ckpParam;

        ckpParam = (CK_PBE_PARAMS_PTR) malloc(sizeof(CK_PBE_PARAMS));

        /* convert jParameter to CKParameter */
        *ckpParam = jPbeParamToCKPbeParam(env, jParam);

        /* get length and pointer of parameter */
        *ckpLength = sizeof(CK_PBE_PARAMS);
        *ckpParamPtr = ckpParam;

    } else if ((*env)->IsInstanceOf(env, jParam, jPkcs5Pbkd2ParamsClass)) {
        /*
         * CK_PKCS5_PBKD2_PARAMS
         */

        CK_PKCS5_PBKD2_PARAMS_PTR ckpParam;

        ckpParam = (CK_PKCS5_PBKD2_PARAMS_PTR) malloc(sizeof(CK_PKCS5_PBKD2_PARAMS));

        /* convert jParameter to CKParameter */
        *ckpParam = jPkcs5Pbkd2ParamToCKPkcs5Pbkd2Param(env, jParam);

        /* get length and pointer of parameter */
        *ckpLength = sizeof(CK_PKCS5_PBKD2_PARAMS);
        *ckpParamPtr = ckpParam;

    } else if ((*env)->IsInstanceOf(env, jParam, jRsaPkcsPssParamsClass)) {
        /*
         * CK_RSA_PKCS_PSS_PARAMS
         */

        CK_RSA_PKCS_PSS_PARAMS_PTR ckpParam;

        ckpParam = (CK_RSA_PKCS_PSS_PARAMS_PTR) malloc(sizeof(CK_RSA_PKCS_PSS_PARAMS));

        /* convert jParameter to CKParameter */
        *ckpParam = jRsaPkcsPssParamToCKRsaPkcsPssParam(env, jParam);

        /* get length and pointer of parameter */
        *ckpLength = sizeof(CK_RSA_PKCS_PSS_PARAMS);
        *ckpParamPtr = ckpParam;

    } else if ((*env)->IsInstanceOf(env, jParam, jEcdh1DeriveParamsClass)) {
        /*
         * CK_ECDH1_DERIVE_PARAMS
         */

        CK_ECDH1_DERIVE_PARAMS_PTR ckpParam;

        ckpParam = (CK_ECDH1_DERIVE_PARAMS_PTR) malloc(sizeof(CK_ECDH1_DERIVE_PARAMS));

        /* convert jParameter to CKParameter */
        *ckpParam = jEcdh1DeriveParamToCKEcdh1DeriveParam(env, jParam);

        /* get length and pointer of parameter */
        *ckpLength = sizeof(CK_ECDH1_DERIVE_PARAMS);
        *ckpParamPtr = ckpParam;

    } else if ((*env)->IsInstanceOf(env, jParam, jEcdh2DeriveParamsClass)) {
        /*
         * CK_ECDH2_DERIVE_PARAMS
         */

        CK_ECDH2_DERIVE_PARAMS_PTR ckpParam;

        ckpParam = (CK_ECDH2_DERIVE_PARAMS_PTR) malloc(sizeof(CK_ECDH2_DERIVE_PARAMS));

        /* convert jParameter to CKParameter */
        *ckpParam = jEcdh2DeriveParamToCKEcdh2DeriveParam(env, jParam);

        /* get length and pointer of parameter */
        *ckpLength = sizeof(CK_ECDH2_DERIVE_PARAMS);
        *ckpParamPtr = ckpParam;

    } else if ((*env)->IsInstanceOf(env, jParam, jX942Dh1DeriveParamsClass)) {
        /*
         * CK_X9_42_DH1_DERIVE_PARAMS
         */

        CK_X9_42_DH1_DERIVE_PARAMS_PTR ckpParam;

        ckpParam = (CK_X9_42_DH1_DERIVE_PARAMS_PTR) malloc(sizeof(CK_X9_42_DH1_DERIVE_PARAMS));

        /* convert jParameter to CKParameter */
        *ckpParam = jX942Dh1DeriveParamToCKX942Dh1DeriveParam(env, jParam);

        /* get length and pointer of parameter */
        *ckpLength = sizeof(CK_X9_42_DH1_DERIVE_PARAMS);
        *ckpParamPtr = ckpParam;

    } else if ((*env)->IsInstanceOf(env, jParam, jX942Dh2DeriveParamsClass)) {
        /*
         * CK_X9_42_DH2_DERIVE_PARAMS
         */

        CK_X9_42_DH2_DERIVE_PARAMS_PTR ckpParam;

        ckpParam = (CK_X9_42_DH2_DERIVE_PARAMS_PTR) malloc(sizeof(CK_X9_42_DH2_DERIVE_PARAMS));

        /* convert jParameter to CKParameter */
        *ckpParam = jX942Dh2DeriveParamToCKX942Dh2DeriveParam(env, jParam);

        /* get length and pointer of parameter */
        *ckpLength = sizeof(CK_X9_42_DH2_DERIVE_PARAMS);
        *ckpParamPtr = ckpParam;

    } else {
        /* if everything faild up to here */
        /* try if the parameter is a primitive Java type */
        jObjectToPrimitiveCKObjectPtrPtr(env, jParam, ckpParamPtr, ckpLength);
        /* *ckpParamPtr = jObjectToCKVoidPtr(jParam); */
        /* *ckpLength = 1; */
    }

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
    jclass jRsaPkcsOaepParamsClass = (*env)->FindClass(env, CLASS_RSA_PKCS_OAEP_PARAMS);
    CK_RSA_PKCS_OAEP_PARAMS ckParam;
    jfieldID fieldID;
    jlong jLong;
    jobject jObject;
    CK_BYTE_PTR ckpByte;

    /* get hashAlg */
    fieldID = (*env)->GetFieldID(env, jRsaPkcsOaepParamsClass, "hashAlg", "J");
    assert(fieldID != 0);
    jLong = (*env)->GetLongField(env, jParam, fieldID);
    ckParam.hashAlg = jLongToCKULong(jLong);

    /* get mgf */
    fieldID = (*env)->GetFieldID(env, jRsaPkcsOaepParamsClass, "mgf", "J");
    assert(fieldID != 0);
    jLong = (*env)->GetLongField(env, jParam, fieldID);
    ckParam.mgf = jLongToCKULong(jLong);

    /* get source */
    fieldID = (*env)->GetFieldID(env, jRsaPkcsOaepParamsClass, "source", "J");
    assert(fieldID != 0);
    jLong = (*env)->GetLongField(env, jParam, fieldID);
    ckParam.source = jLongToCKULong(jLong);

    /* get sourceData and sourceDataLength */
    fieldID = (*env)->GetFieldID(env, jRsaPkcsOaepParamsClass, "pSourceData", "[B");
    assert(fieldID != 0);
    jObject = (*env)->GetObjectField(env, jParam, fieldID);
    jByteArrayToCKByteArray(env, jObject, &ckpByte, &(ckParam.ulSourceDataLen));
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
    jclass jPbeParamsClass = (*env)->FindClass(env, CLASS_PBE_PARAMS);
    CK_PBE_PARAMS ckParam;
    jfieldID fieldID;
    jlong jLong;
    jobject jObject;
    CK_ULONG ckTemp;

    /* get pInitVector */
    fieldID = (*env)->GetFieldID(env, jPbeParamsClass, "pInitVector", "[C");
    assert(fieldID != 0);
    jObject = (*env)->GetObjectField(env, jParam, fieldID);
    jCharArrayToCKCharArray(env, jObject, &(ckParam.pInitVector), &ckTemp);

    /* get pPassword and ulPasswordLength */
    fieldID = (*env)->GetFieldID(env, jPbeParamsClass, "pPassword", "[C");
    assert(fieldID != 0);
    jObject = (*env)->GetObjectField(env, jParam, fieldID);
    jCharArrayToCKCharArray(env, jObject, &(ckParam.pPassword), &(ckParam.ulPasswordLen));

    /* get pSalt and ulSaltLength */
    fieldID = (*env)->GetFieldID(env, jPbeParamsClass, "pSalt", "[C");
    assert(fieldID != 0);
    jObject = (*env)->GetObjectField(env, jParam, fieldID);
    jCharArrayToCKCharArray(env, jObject, &(ckParam.pSalt), &(ckParam.ulSaltLen));

    /* get ulIteration */
    fieldID = (*env)->GetFieldID(env, jPbeParamsClass, "ulIteration", "J");
    assert(fieldID != 0);
    jLong = (*env)->GetLongField(env, jParam, fieldID);
    ckParam.ulIteration = jLongToCKULong(jLong);

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
    jclass jMechanismClass= (*env)->FindClass(env, CLASS_MECHANISM);
    jclass jPbeParamsClass = (*env)->FindClass(env, CLASS_PBE_PARAMS);
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
    fieldID = (*env)->GetFieldID(env, jMechanismClass, "mechanism", "J");
    assert(fieldID != 0);
    jMechanismType = (*env)->GetLongField(env, jMechanism, fieldID);
    ckMechanismType = jLongToCKULong(jMechanismType);
    if (ckMechanismType != ckMechanism->mechanism) {
        /* we do not have maching types, this should not occur */
        return;
    }

    ckParam = (CK_PBE_PARAMS *) ckMechanism->pParameter;
    if (ckParam != NULL_PTR) {
        initVector = ckParam->pInitVector;
        if (initVector != NULL_PTR) {
            /* get pParameter */
            fieldID = (*env)->GetFieldID(env, jMechanismClass, "pParameter", "Ljava/lang/Object;");
            assert(fieldID != 0);
            jParameter = (*env)->GetObjectField(env, jMechanism, fieldID);
            fieldID = (*env)->GetFieldID(env, jPbeParamsClass, "pInitVektor", "[C");
            assert(fieldID != 0);
            jInitVector = (*env)->GetObjectField(env, jParameter, fieldID);

            if (jInitVector != NULL) {
                jInitVectorLength = (*env)->GetArrayLength(env, jInitVector);
                jInitVectorChars = (*env)->GetCharArrayElements(env, jInitVector, NULL);
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
    jclass jPkcs5Pbkd2ParamsClass = (*env)->FindClass(env, CLASS_PKCS5_PBKD2_PARAMS);
    CK_PKCS5_PBKD2_PARAMS ckParam;
    jfieldID fieldID;
    jlong jLong;
    jobject jObject;

    /* get saltSource */
    fieldID = (*env)->GetFieldID(env, jPkcs5Pbkd2ParamsClass, "saltSource", "J");
    assert(fieldID != 0);
    jLong = (*env)->GetLongField(env, jParam, fieldID);
    ckParam.saltSource = jLongToCKULong(jLong);

    /* get pSaltSourceData */
    fieldID = (*env)->GetFieldID(env, jPkcs5Pbkd2ParamsClass, "pSaltSourceData", "[B");
    assert(fieldID != 0);
    jObject = (*env)->GetObjectField(env, jParam, fieldID);
    jByteArrayToCKByteArray(env, jObject, (CK_BYTE_PTR *) &(ckParam.pSaltSourceData), &(ckParam.ulSaltSourceDataLen));

    /* get iterations */
    fieldID = (*env)->GetFieldID(env, jPkcs5Pbkd2ParamsClass, "iterations", "J");
    assert(fieldID != 0);
    jLong = (*env)->GetLongField(env, jParam, fieldID);
    ckParam.iterations = jLongToCKULong(jLong);

    /* get prf */
    fieldID = (*env)->GetFieldID(env, jPkcs5Pbkd2ParamsClass, "prf", "J");
    assert(fieldID != 0);
    jLong = (*env)->GetLongField(env, jParam, fieldID);
    ckParam.prf = jLongToCKULong(jLong);

    /* get pPrfData and ulPrfDataLength in byte */
    fieldID = (*env)->GetFieldID(env, jPkcs5Pbkd2ParamsClass, "pPrfData", "[B");
    assert(fieldID != 0);
    jObject = (*env)->GetObjectField(env, jParam, fieldID);
    jByteArrayToCKByteArray(env, jObject, (CK_BYTE_PTR *) &(ckParam.pPrfData), &(ckParam.ulPrfDataLen));

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
    jclass jRsaPkcsPssParamsClass = (*env)->FindClass(env, CLASS_RSA_PKCS_PSS_PARAMS);
    CK_RSA_PKCS_PSS_PARAMS ckParam;
    jfieldID fieldID;
    jlong jLong;

    /* get hashAlg */
    fieldID = (*env)->GetFieldID(env, jRsaPkcsPssParamsClass, "hashAlg", "J");
    assert(fieldID != 0);
    jLong = (*env)->GetLongField(env, jParam, fieldID);
    ckParam.hashAlg = jLongToCKULong(jLong);

    /* get mgf */
    fieldID = (*env)->GetFieldID(env, jRsaPkcsPssParamsClass, "mgf", "J");
    assert(fieldID != 0);
    jLong = (*env)->GetLongField(env, jParam, fieldID);
    ckParam.mgf = jLongToCKULong(jLong);

    /* get sLen */
    fieldID = (*env)->GetFieldID(env, jRsaPkcsPssParamsClass, "sLen", "J");
    assert(fieldID != 0);
    jLong = (*env)->GetLongField(env, jParam, fieldID);
    ckParam.sLen = jLongToCKULong(jLong);

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
    jclass jEcdh1DeriveParamsClass = (*env)->FindClass(env, CLASS_ECDH1_DERIVE_PARAMS);
    CK_ECDH1_DERIVE_PARAMS ckParam;
    jfieldID fieldID;
    jlong jLong;
    jobject jObject;

    /* get kdf */
    fieldID = (*env)->GetFieldID(env, jEcdh1DeriveParamsClass, "kdf", "J");
    assert(fieldID != 0);
    jLong = (*env)->GetLongField(env, jParam, fieldID);
    ckParam.kdf = jLongToCKULong(jLong);

    /* get pSharedData and ulSharedDataLen */
    fieldID = (*env)->GetFieldID(env, jEcdh1DeriveParamsClass, "pSharedData", "[B");
    assert(fieldID != 0);
    jObject = (*env)->GetObjectField(env, jParam, fieldID);
    jByteArrayToCKByteArray(env, jObject, &(ckParam.pSharedData), &(ckParam.ulSharedDataLen));

    /* get pPublicData and ulPublicDataLen */
    fieldID = (*env)->GetFieldID(env, jEcdh1DeriveParamsClass, "pPublicData", "[B");
    assert(fieldID != 0);
    jObject = (*env)->GetObjectField(env, jParam, fieldID);
    jByteArrayToCKByteArray(env, jObject, &(ckParam.pPublicData), &(ckParam.ulPublicDataLen));

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
    jclass jEcdh2DeriveParamsClass = (*env)->FindClass(env, CLASS_ECDH2_DERIVE_PARAMS);
    CK_ECDH2_DERIVE_PARAMS ckParam;
    jfieldID fieldID;
    jlong jLong;
    jobject jObject;

    /* get kdf */
    fieldID = (*env)->GetFieldID(env, jEcdh2DeriveParamsClass, "kdf", "J");
    assert(fieldID != 0);
    jLong = (*env)->GetLongField(env, jParam, fieldID);
    ckParam.kdf = jLongToCKULong(jLong);

    /* get pSharedData and ulSharedDataLen */
    fieldID = (*env)->GetFieldID(env, jEcdh2DeriveParamsClass, "pSharedData", "[B");
    assert(fieldID != 0);
    jObject = (*env)->GetObjectField(env, jParam, fieldID);
    jByteArrayToCKByteArray(env, jObject, &(ckParam.pSharedData), &(ckParam.ulSharedDataLen));

    /* get pPublicData and ulPublicDataLen */
    fieldID = (*env)->GetFieldID(env, jEcdh2DeriveParamsClass, "pPublicData", "[B");
    assert(fieldID != 0);
    jObject = (*env)->GetObjectField(env, jParam, fieldID);
    jByteArrayToCKByteArray(env, jObject, &(ckParam.pPublicData), &(ckParam.ulPublicDataLen));

    /* get ulPrivateDataLen */
    fieldID = (*env)->GetFieldID(env, jEcdh2DeriveParamsClass, "ulPrivateDataLen", "J");
    assert(fieldID != 0);
    jLong = (*env)->GetLongField(env, jParam, fieldID);
    ckParam.ulPrivateDataLen = jLongToCKULong(jLong);

    /* get hPrivateData */
    fieldID = (*env)->GetFieldID(env, jEcdh2DeriveParamsClass, "hPrivateData", "J");
    assert(fieldID != 0);
    jLong = (*env)->GetLongField(env, jParam, fieldID);
    ckParam.hPrivateData = jLongToCKULong(jLong);

    /* get pPublicData2 and ulPublicDataLen2 */
    fieldID = (*env)->GetFieldID(env, jEcdh2DeriveParamsClass, "pPublicData2", "[B");
    assert(fieldID != 0);
    jObject = (*env)->GetObjectField(env, jParam, fieldID);
    jByteArrayToCKByteArray(env, jObject, &(ckParam.pPublicData2), &(ckParam.ulPublicDataLen2));

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
    jclass jX942Dh1DeriveParamsClass = (*env)->FindClass(env, CLASS_X9_42_DH1_DERIVE_PARAMS);
    CK_X9_42_DH1_DERIVE_PARAMS ckParam;
    jfieldID fieldID;
    jlong jLong;
    jobject jObject;

    /* get kdf */
    fieldID = (*env)->GetFieldID(env, jX942Dh1DeriveParamsClass, "kdf", "J");
    assert(fieldID != 0);
    jLong = (*env)->GetLongField(env, jParam, fieldID);
    ckParam.kdf = jLongToCKULong(jLong);

    /* get pOtherInfo and ulOtherInfoLen */
    fieldID = (*env)->GetFieldID(env, jX942Dh1DeriveParamsClass, "pOtherInfo", "[B");
    assert(fieldID != 0);
    jObject = (*env)->GetObjectField(env, jParam, fieldID);
    jByteArrayToCKByteArray(env, jObject, &(ckParam.pOtherInfo), &(ckParam.ulOtherInfoLen));

    /* get pPublicData and ulPublicDataLen */
    fieldID = (*env)->GetFieldID(env, jX942Dh1DeriveParamsClass, "pPublicData", "[B");
    assert(fieldID != 0);
    jObject = (*env)->GetObjectField(env, jParam, fieldID);
    jByteArrayToCKByteArray(env, jObject, &(ckParam.pPublicData), &(ckParam.ulPublicDataLen));

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
    jclass jX942Dh2DeriveParamsClass = (*env)->FindClass(env, CLASS_X9_42_DH2_DERIVE_PARAMS);
    CK_X9_42_DH2_DERIVE_PARAMS ckParam;
    jfieldID fieldID;
    jlong jLong;
    jobject jObject;

    /* get kdf */
    fieldID = (*env)->GetFieldID(env, jX942Dh2DeriveParamsClass, "kdf", "J");
    assert(fieldID != 0);
    jLong = (*env)->GetLongField(env, jParam, fieldID);
    ckParam.kdf = jLongToCKULong(jLong);

    /* get pOtherInfo and ulOtherInfoLen */
    fieldID = (*env)->GetFieldID(env, jX942Dh2DeriveParamsClass, "pOtherInfo", "[B");
    assert(fieldID != 0);
    jObject = (*env)->GetObjectField(env, jParam, fieldID);
    jByteArrayToCKByteArray(env, jObject, &(ckParam.pOtherInfo), &(ckParam.ulOtherInfoLen));

    /* get pPublicData and ulPublicDataLen */
    fieldID = (*env)->GetFieldID(env, jX942Dh2DeriveParamsClass, "pPublicData", "[B");
    assert(fieldID != 0);
    jObject = (*env)->GetObjectField(env, jParam, fieldID);
    jByteArrayToCKByteArray(env, jObject, &(ckParam.pPublicData), &(ckParam.ulPublicDataLen));

    /* get ulPrivateDataLen */
    fieldID = (*env)->GetFieldID(env, jX942Dh2DeriveParamsClass, "ulPrivateDataLen", "J");
    assert(fieldID != 0);
    jLong = (*env)->GetLongField(env, jParam, fieldID);
    ckParam.ulPrivateDataLen = jLongToCKULong(jLong);

    /* get hPrivateData */
    fieldID = (*env)->GetFieldID(env, jX942Dh2DeriveParamsClass, "hPrivateData", "J");
    assert(fieldID != 0);
    jLong = (*env)->GetLongField(env, jParam, fieldID);
    ckParam.hPrivateData = jLongToCKULong(jLong);

    /* get pPublicData2 and ulPublicDataLen2 */
    fieldID = (*env)->GetFieldID(env, jX942Dh2DeriveParamsClass, "pPublicData2", "[B");
    assert(fieldID != 0);
    jObject = (*env)->GetObjectField(env, jParam, fieldID);
    jByteArrayToCKByteArray(env, jObject, &(ckParam.pPublicData2), &(ckParam.ulPublicDataLen2));

    return ckParam ;
}
