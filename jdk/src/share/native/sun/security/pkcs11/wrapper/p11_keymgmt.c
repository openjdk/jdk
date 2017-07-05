/*
 * Portions Copyright 2003-2006 Sun Microsystems, Inc.  All Rights Reserved.
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

#include "pkcs11wrapper.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <assert.h>

#include "sun_security_pkcs11_wrapper_PKCS11.h"

#ifdef P11_ENABLE_C_GENERATEKEY
/*
 * Class:     sun_security_pkcs11_wrapper_PKCS11
 * Method:    C_GenerateKey
 * Signature: (JLsun/security/pkcs11/wrapper/CK_MECHANISM;[Lsun/security/pkcs11/wrapper/CK_ATTRIBUTE;)J
 * Parametermapping:                    *PKCS11*
 * @param   jlong jSessionHandle        CK_SESSION_HANDLE hSession
 * @param   jobject jMechanism          CK_MECHANISM_PTR pMechanism
 * @param   jobjectArray jTemplate      CK_ATTRIBUTE_PTR pTemplate
 *                                      CK_ULONG ulCount
 * @return  jlong jKeyHandle            CK_OBJECT_HANDLE_PTR phKey
 */
JNIEXPORT jlong JNICALL Java_sun_security_pkcs11_wrapper_PKCS11_C_1GenerateKey
    (JNIEnv *env, jobject obj, jlong jSessionHandle, jobject jMechanism, jobjectArray jTemplate)
{
    CK_SESSION_HANDLE ckSessionHandle;
    CK_MECHANISM ckMechanism;
    CK_ATTRIBUTE_PTR ckpAttributes = NULL_PTR;
    CK_ULONG ckAttributesLength;
    CK_OBJECT_HANDLE ckKeyHandle;
    jlong jKeyHandle;
    CK_ULONG i;
    CK_RV rv;

    CK_FUNCTION_LIST_PTR ckpFunctions = getFunctionList(env, obj);
    if (ckpFunctions == NULL) { return 0L; }

    ckSessionHandle = jLongToCKULong(jSessionHandle);
    jMechanismToCKMechanism(env, jMechanism, &ckMechanism);
    if ((*env)->ExceptionOccurred(env)) { return 0L ; }
    jAttributeArrayToCKAttributeArray(env, jTemplate, &ckpAttributes, &ckAttributesLength);

    rv = (*ckpFunctions->C_GenerateKey)(ckSessionHandle, &ckMechanism, ckpAttributes, ckAttributesLength, &ckKeyHandle);

    jKeyHandle = ckULongToJLong(ckKeyHandle);
    for(i=0; i<ckAttributesLength; i++) {
        if(ckpAttributes[i].pValue != NULL_PTR) {
            free(ckpAttributes[i].pValue);
        }
    }
    free(ckpAttributes);

    /* cheack, if we must give a initialization vector back to Java */
    switch (ckMechanism.mechanism) {
        case CKM_PBE_MD2_DES_CBC:
        case CKM_PBE_MD5_DES_CBC:
        case CKM_PBE_MD5_CAST_CBC:
        case CKM_PBE_MD5_CAST3_CBC:
        case CKM_PBE_MD5_CAST128_CBC:
        /* case CKM_PBE_MD5_CAST5_CBC:  the same as CKM_PBE_MD5_CAST128_CBC */
        case CKM_PBE_SHA1_CAST128_CBC:
        /* case CKM_PBE_SHA1_CAST5_CBC: the same as CKM_PBE_SHA1_CAST128_CBC */
            /* we must copy back the initialization vector to the jMechanism object */
            copyBackPBEInitializationVector(env, &ckMechanism, jMechanism);
            break;
    }

    if(ckMechanism.pParameter != NULL_PTR) {
        free(ckMechanism.pParameter);
    }

    if(ckAssertReturnValueOK(env, rv) != CK_ASSERT_OK) { return 0L ; }

    return jKeyHandle ;
}
#endif

#ifdef P11_ENABLE_C_GENERATEKEYPAIR
/*
 * Class:     sun_security_pkcs11_wrapper_PKCS11
 * Method:    C_GenerateKeyPair
 * Signature: (JLsun/security/pkcs11/wrapper/CK_MECHANISM;[Lsun/security/pkcs11/wrapper/CK_ATTRIBUTE;[Lsun/security/pkcs11/wrapper/CK_ATTRIBUTE;)[J
 * Parametermapping:                          *PKCS11*
 * @param   jlong jSessionHandle              CK_SESSION_HANDLE hSession
 * @param   jobject jMechanism                CK_MECHANISM_PTR pMechanism
 * @param   jobjectArray jPublicKeyTemplate   CK_ATTRIBUTE_PTR pPublicKeyTemplate
 *                                            CK_ULONG ulPublicKeyAttributeCount
 * @param   jobjectArray jPrivateKeyTemplate  CK_ATTRIBUTE_PTR pPrivateKeyTemplate
 *                                            CK_ULONG ulPrivateKeyAttributeCount
 * @return  jlongArray jKeyHandles            CK_OBJECT_HANDLE_PTR phPublicKey
 *                                            CK_OBJECT_HANDLE_PTR phPublicKey
 */
JNIEXPORT jlongArray JNICALL Java_sun_security_pkcs11_wrapper_PKCS11_C_1GenerateKeyPair
    (JNIEnv *env, jobject obj, jlong jSessionHandle, jobject jMechanism,
     jobjectArray jPublicKeyTemplate, jobjectArray jPrivateKeyTemplate)
{
    CK_SESSION_HANDLE ckSessionHandle;
    CK_MECHANISM ckMechanism;
    CK_ATTRIBUTE_PTR ckpPublicKeyAttributes = NULL_PTR;
    CK_ATTRIBUTE_PTR ckpPrivateKeyAttributes = NULL_PTR;
    CK_ULONG ckPublicKeyAttributesLength;
    CK_ULONG ckPrivateKeyAttributesLength;
    CK_OBJECT_HANDLE_PTR ckpPublicKeyHandle;  /* pointer to Public Key */
    CK_OBJECT_HANDLE_PTR ckpPrivateKeyHandle; /* pointer to Private Key */
    CK_OBJECT_HANDLE_PTR ckpKeyHandles;     /* pointer to array with Public and Private Key */
    jlongArray jKeyHandles;
    CK_ULONG i;
    CK_RV rv;

    CK_FUNCTION_LIST_PTR ckpFunctions = getFunctionList(env, obj);
    if (ckpFunctions == NULL) { return NULL; }

    ckSessionHandle = jLongToCKULong(jSessionHandle);
    jMechanismToCKMechanism(env, jMechanism, &ckMechanism);
    jAttributeArrayToCKAttributeArray(env, jPublicKeyTemplate, &ckpPublicKeyAttributes, &ckPublicKeyAttributesLength);
    jAttributeArrayToCKAttributeArray(env, jPrivateKeyTemplate, &ckpPrivateKeyAttributes, &ckPrivateKeyAttributesLength);
    ckpKeyHandles = (CK_OBJECT_HANDLE_PTR) malloc(2 * sizeof(CK_OBJECT_HANDLE));
    ckpPublicKeyHandle = ckpKeyHandles;   /* first element of array is Public Key */
    ckpPrivateKeyHandle = (ckpKeyHandles + 1);  /* second element of array is Private Key */

    rv = (*ckpFunctions->C_GenerateKeyPair)(ckSessionHandle, &ckMechanism,
                     ckpPublicKeyAttributes, ckPublicKeyAttributesLength,
                     ckpPrivateKeyAttributes, ckPrivateKeyAttributesLength,
                     ckpPublicKeyHandle, ckpPrivateKeyHandle);

    jKeyHandles = ckULongArrayToJLongArray(env, ckpKeyHandles, 2);

    for(i=0; i<ckPublicKeyAttributesLength; i++) {
        if(ckpPublicKeyAttributes[i].pValue != NULL_PTR) {
            free(ckpPublicKeyAttributes[i].pValue);
        }
    }
    free(ckpPublicKeyAttributes);

    for(i=0; i<ckPrivateKeyAttributesLength; i++) {
        if(ckpPrivateKeyAttributes[i].pValue != NULL_PTR) {
            free(ckpPrivateKeyAttributes[i].pValue);
        }
    }
    free(ckpPrivateKeyAttributes);

    if(ckMechanism.pParameter != NULL_PTR) {
        free(ckMechanism.pParameter);
    }

    free(ckpKeyHandles);

    if(ckAssertReturnValueOK(env, rv) != CK_ASSERT_OK) { return NULL; }

    return jKeyHandles ;
}
#endif

#ifdef P11_ENABLE_C_WRAPKEY
/*
 * Class:     sun_security_pkcs11_wrapper_PKCS11
 * Method:    C_WrapKey
 * Signature: (JLsun/security/pkcs11/wrapper/CK_MECHANISM;JJ)[B
 * Parametermapping:                    *PKCS11*
 * @param   jlong jSessionHandle        CK_SESSION_HANDLE hSession
 * @param   jobject jMechanism          CK_MECHANISM_PTR pMechanism
 * @param   jlong jWrappingKeyHandle    CK_OBJECT_HANDLE hWrappingKey
 * @param   jlong jKeyHandle            CK_OBJECT_HANDLE hKey
 * @return  jbyteArray jWrappedKey      CK_BYTE_PTR pWrappedKey
 *                                      CK_ULONG_PTR pulWrappedKeyLen
 */
JNIEXPORT jbyteArray JNICALL Java_sun_security_pkcs11_wrapper_PKCS11_C_1WrapKey
    (JNIEnv *env, jobject obj, jlong jSessionHandle, jobject jMechanism, jlong jWrappingKeyHandle, jlong jKeyHandle)
{
    CK_SESSION_HANDLE ckSessionHandle;
    CK_MECHANISM ckMechanism;
    CK_OBJECT_HANDLE ckWrappingKeyHandle;
    CK_OBJECT_HANDLE ckKeyHandle;
    jbyteArray jWrappedKey;
    CK_RV rv;
    CK_BYTE BUF[MAX_STACK_BUFFER_LEN];
    CK_BYTE_PTR ckpWrappedKey = BUF;
    CK_ULONG ckWrappedKeyLength = MAX_STACK_BUFFER_LEN;

    CK_FUNCTION_LIST_PTR ckpFunctions = getFunctionList(env, obj);
    if (ckpFunctions == NULL) { return NULL; }

    ckSessionHandle = jLongToCKULong(jSessionHandle);
    jMechanismToCKMechanism(env, jMechanism, &ckMechanism);
    ckWrappingKeyHandle = jLongToCKULong(jWrappingKeyHandle);
    ckKeyHandle = jLongToCKULong(jKeyHandle);

    rv = (*ckpFunctions->C_WrapKey)(ckSessionHandle, &ckMechanism, ckWrappingKeyHandle, ckKeyHandle, ckpWrappedKey, &ckWrappedKeyLength);
    if (rv == CKR_BUFFER_TOO_SMALL) {
        ckpWrappedKey = (CK_BYTE_PTR) malloc(ckWrappedKeyLength);
        rv = (*ckpFunctions->C_WrapKey)(ckSessionHandle, &ckMechanism, ckWrappingKeyHandle, ckKeyHandle, ckpWrappedKey, &ckWrappedKeyLength);
    }
    if (ckAssertReturnValueOK(env, rv) == CK_ASSERT_OK) {
        jWrappedKey = ckByteArrayToJByteArray(env, ckpWrappedKey, ckWrappedKeyLength);
    }

    if (ckpWrappedKey != BUF) {
        free(ckpWrappedKey);
    }
    if(ckMechanism.pParameter != NULL_PTR)
        free(ckMechanism.pParameter);

    return jWrappedKey ;
}
#endif

#ifdef P11_ENABLE_C_UNWRAPKEY
/*
 * Class:     sun_security_pkcs11_wrapper_PKCS11
 * Method:    C_UnwrapKey
 * Signature: (JLsun/security/pkcs11/wrapper/CK_MECHANISM;J[B[Lsun/security/pkcs11/wrapper/CK_ATTRIBUTE;)J
 * Parametermapping:                    *PKCS11*
 * @param   jlong jSessionHandle        CK_SESSION_HANDLE hSession
 * @param   jobject jMechanism          CK_MECHANISM_PTR pMechanism
 * @param   jlong jUnwrappingKeyHandle  CK_OBJECT_HANDLE hUnwrappingKey
 * @param   jbyteArray jWrappedKey      CK_BYTE_PTR pWrappedKey
 *                                      CK_ULONG_PTR pulWrappedKeyLen
 * @param   jobjectArray jTemplate      CK_ATTRIBUTE_PTR pTemplate
 *                                      CK_ULONG ulCount
 * @return  jlong jKeyHandle            CK_OBJECT_HANDLE_PTR phKey
 */
JNIEXPORT jlong JNICALL Java_sun_security_pkcs11_wrapper_PKCS11_C_1UnwrapKey
    (JNIEnv *env, jobject obj, jlong jSessionHandle, jobject jMechanism, jlong jUnwrappingKeyHandle,
     jbyteArray jWrappedKey, jobjectArray jTemplate)
{
    CK_SESSION_HANDLE ckSessionHandle;
    CK_MECHANISM ckMechanism;
    CK_OBJECT_HANDLE ckUnwrappingKeyHandle;
    CK_BYTE_PTR ckpWrappedKey = NULL_PTR;
    CK_ULONG ckWrappedKeyLength;
    CK_ATTRIBUTE_PTR ckpAttributes = NULL_PTR;
    CK_ULONG ckAttributesLength;
    CK_OBJECT_HANDLE ckKeyHandle;
    jlong jKeyHandle;
    CK_ULONG i;
    CK_RV rv;

    CK_FUNCTION_LIST_PTR ckpFunctions = getFunctionList(env, obj);
    if (ckpFunctions == NULL) { return 0L; }

    ckSessionHandle = jLongToCKULong(jSessionHandle);
    jMechanismToCKMechanism(env, jMechanism, &ckMechanism);
    ckUnwrappingKeyHandle = jLongToCKULong(jUnwrappingKeyHandle);
    jByteArrayToCKByteArray(env, jWrappedKey, &ckpWrappedKey, &ckWrappedKeyLength);
    jAttributeArrayToCKAttributeArray(env, jTemplate, &ckpAttributes, &ckAttributesLength);

    rv = (*ckpFunctions->C_UnwrapKey)(ckSessionHandle, &ckMechanism, ckUnwrappingKeyHandle,
                 ckpWrappedKey, ckWrappedKeyLength,
                 ckpAttributes, ckAttributesLength, &ckKeyHandle);

    jKeyHandle = ckLongToJLong(ckKeyHandle);

    for(i=0; i<ckAttributesLength; i++) {
        if(ckpAttributes[i].pValue != NULL_PTR) {
            free(ckpAttributes[i].pValue);
        }
    }
    free(ckpAttributes);

#if 0
    /* cheack, if we must give a initialization vector back to Java */
    if (ckMechanism.mechanism == CKM_KEY_WRAP_SET_OAEP) {
        /* we must copy back the unwrapped key info to the jMechanism object */
        copyBackSetUnwrappedKey(env, &ckMechanism, jMechanism);
    }
#endif

    free(ckpWrappedKey);
    if(ckMechanism.pParameter != NULL_PTR) {
        free(ckMechanism.pParameter);
    }

    if(ckAssertReturnValueOK(env, rv) != CK_ASSERT_OK) { return 0L ; }

    return jKeyHandle ;
}
#endif

#ifdef P11_ENABLE_C_DERIVEKEY

void freeMasterKeyDeriveParams(CK_MECHANISM_PTR ckMechanism) {
    CK_SSL3_MASTER_KEY_DERIVE_PARAMS *params = (CK_SSL3_MASTER_KEY_DERIVE_PARAMS *) ckMechanism->pParameter;
    if (params == NULL) {
        return;
    }

    if (params->RandomInfo.pClientRandom != NULL) {
        free(params->RandomInfo.pClientRandom);
    }
    if (params->RandomInfo.pServerRandom != NULL) {
        free(params->RandomInfo.pServerRandom);
    }
    if (params->pVersion != NULL) {
        free(params->pVersion);
    }
}

void freeEcdh1DeriveParams(CK_MECHANISM_PTR ckMechanism) {
    CK_ECDH1_DERIVE_PARAMS *params = (CK_ECDH1_DERIVE_PARAMS *) ckMechanism->pParameter;
    if (params == NULL) {
        return;
    }

    if (params->pSharedData != NULL) {
        free(params->pSharedData);
    }
    if (params->pPublicData != NULL) {
        free(params->pPublicData);
    }
}

/*
 * Copy back the PRF output to Java.
 */
void copyBackTLSPrfParams(JNIEnv *env, CK_MECHANISM *ckMechanism, jobject jMechanism)
{
    jclass jMechanismClass= (*env)->FindClass(env, CLASS_MECHANISM);
    jclass jTLSPrfParamsClass = (*env)->FindClass(env, CLASS_TLS_PRF_PARAMS);
    CK_TLS_PRF_PARAMS *ckTLSPrfParams;
    jobject jTLSPrfParams;
    jfieldID fieldID;
    CK_MECHANISM_TYPE ckMechanismType;
    jlong jMechanismType;
    CK_BYTE_PTR output;
    jobject jOutput;
    jint jLength;
    jbyte* jBytes;
    int i;

    /* get mechanism */
    fieldID = (*env)->GetFieldID(env, jMechanismClass, "mechanism", "J");
    assert(fieldID != 0);
    jMechanismType = (*env)->GetLongField(env, jMechanism, fieldID);
    ckMechanismType = jLongToCKULong(jMechanismType);
    if (ckMechanismType != ckMechanism->mechanism) {
        /* we do not have maching types, this should not occur */
        return;
    }

    /* get the native CK_TLS_PRF_PARAMS */
    ckTLSPrfParams = (CK_TLS_PRF_PARAMS *) ckMechanism->pParameter;
    if (ckTLSPrfParams != NULL_PTR) {
        /* get the Java CK_TLS_PRF_PARAMS object (pParameter) */
        fieldID = (*env)->GetFieldID(env, jMechanismClass, "pParameter", "Ljava/lang/Object;");
        assert(fieldID != 0);
        jTLSPrfParams = (*env)->GetObjectField(env, jMechanism, fieldID);

        /* copy back the client IV */
        fieldID = (*env)->GetFieldID(env, jTLSPrfParamsClass, "pOutput", "[B");
        assert(fieldID != 0);
        jOutput = (*env)->GetObjectField(env, jTLSPrfParams, fieldID);
        output = ckTLSPrfParams->pOutput;

        // Note: we assume that the token returned exactly as many bytes as we
        // requested. Anything else would not make sense.
        if (jOutput != NULL) {
            jLength = (*env)->GetArrayLength(env, jOutput);
            jBytes = (*env)->GetByteArrayElements(env, jOutput, NULL);
            /* copy the bytes to the Java buffer */
            for (i=0; i < jLength; i++) {
                jBytes[i] = ckByteToJByte(output[i]);
            }
            /* copy back the Java buffer to the object */
            (*env)->ReleaseByteArrayElements(env, jOutput, jBytes, 0);
            // free malloc'd data
            free(output);
        }

        // free malloc'd data
        if (ckTLSPrfParams->pSeed != NULL) {
            free(ckTLSPrfParams->pSeed);
        }
        if (ckTLSPrfParams->pLabel != NULL) {
            free(ckTLSPrfParams->pLabel);
        }
        if (ckTLSPrfParams->pulOutputLen != NULL) {
            free(ckTLSPrfParams->pulOutputLen);
        }
    }
}

/*
 * Class:     sun_security_pkcs11_wrapper_PKCS11
 * Method:    C_DeriveKey
 * Signature: (JLsun/security/pkcs11/wrapper/CK_MECHANISM;J[Lsun/security/pkcs11/wrapper/CK_ATTRIBUTE;)J
 * Parametermapping:                    *PKCS11*
 * @param   jlong jSessionHandle        CK_SESSION_HANDLE hSession
 * @param   jobject jMechanism          CK_MECHANISM_PTR pMechanism
 * @param   jlong jBaseKeyHandle        CK_OBJECT_HANDLE hBaseKey
 * @param   jobjectArray jTemplate      CK_ATTRIBUTE_PTR pTemplate
 *                                      CK_ULONG ulCount
 * @return  jlong jKeyHandle            CK_OBJECT_HANDLE_PTR phKey
 */
JNIEXPORT jlong JNICALL Java_sun_security_pkcs11_wrapper_PKCS11_C_1DeriveKey
    (JNIEnv *env, jobject obj, jlong jSessionHandle, jobject jMechanism, jlong jBaseKeyHandle, jobjectArray jTemplate)
{
    CK_SESSION_HANDLE ckSessionHandle;
    CK_MECHANISM ckMechanism;
    CK_OBJECT_HANDLE ckBaseKeyHandle;
    CK_ATTRIBUTE_PTR ckpAttributes = NULL_PTR;
    CK_ULONG ckAttributesLength;
    CK_OBJECT_HANDLE ckKeyHandle = 0;
    jlong jKeyHandle;
    CK_ULONG i;
    CK_RV rv;
    CK_OBJECT_HANDLE_PTR phKey = &ckKeyHandle;

    CK_FUNCTION_LIST_PTR ckpFunctions = getFunctionList(env, obj);
    if (ckpFunctions == NULL) { return 0L; }

    ckSessionHandle = jLongToCKULong(jSessionHandle);
    jMechanismToCKMechanism(env, jMechanism, &ckMechanism);
    ckBaseKeyHandle = jLongToCKULong(jBaseKeyHandle);
    jAttributeArrayToCKAttributeArray(env, jTemplate, &ckpAttributes, &ckAttributesLength);

    switch (ckMechanism.mechanism) {
    case CKM_SSL3_KEY_AND_MAC_DERIVE:
    case CKM_TLS_KEY_AND_MAC_DERIVE:
    case CKM_TLS_PRF:
        // these mechanism do not return a key handle via phKey
        // set to NULL in case pedantic implementations check for it
        phKey = NULL;
        break;
    default:
        // empty
        break;
    }

    rv = (*ckpFunctions->C_DeriveKey)(ckSessionHandle, &ckMechanism, ckBaseKeyHandle,
                 ckpAttributes, ckAttributesLength, phKey);

    jKeyHandle = ckLongToJLong(ckKeyHandle);
    for(i=0; i<ckAttributesLength; i++) {
        if(ckpAttributes[i].pValue != NULL_PTR) {
            free(ckpAttributes[i].pValue);
        }
    }
    if (ckpAttributes != NULL) {
        free(ckpAttributes);
    }

    switch (ckMechanism.mechanism) {
    case CKM_SSL3_MASTER_KEY_DERIVE:
    case CKM_TLS_MASTER_KEY_DERIVE:
        /* we must copy back the client version */
        copyBackClientVersion(env, &ckMechanism, jMechanism);
        freeMasterKeyDeriveParams(&ckMechanism);
        break;
    case CKM_SSL3_MASTER_KEY_DERIVE_DH:
    case CKM_TLS_MASTER_KEY_DERIVE_DH:
        freeMasterKeyDeriveParams(&ckMechanism);
        break;
    case CKM_SSL3_KEY_AND_MAC_DERIVE:
    case CKM_TLS_KEY_AND_MAC_DERIVE:
        /* we must copy back the unwrapped key info to the jMechanism object */
        copyBackSSLKeyMatParams(env, &ckMechanism, jMechanism);
        break;
    case CKM_TLS_PRF:
        copyBackTLSPrfParams(env, &ckMechanism, jMechanism);
        break;
    case CKM_ECDH1_DERIVE:
        freeEcdh1DeriveParams(&ckMechanism);
        break;
    default:
        // empty
        break;
    }

    if(ckMechanism.pParameter != NULL_PTR) {
        free(ckMechanism.pParameter);
    }

    if(ckAssertReturnValueOK(env, rv) != CK_ASSERT_OK) { return 0L ; }

    return jKeyHandle ;
}

/*
 * Copy back the client version information from the native
 * structure to the Java object. This is only used for the
 * CKM_SSL3_MASTER_KEY_DERIVE mechanism when used for deriving a key.
 *
 */
void copyBackClientVersion(JNIEnv *env, CK_MECHANISM *ckMechanism, jobject jMechanism)
{
  jclass jMechanismClass= (*env)->FindClass(env, CLASS_MECHANISM);
  jclass jSSL3MasterKeyDeriveParamsClass = (*env)->FindClass(env, CLASS_SSL3_MASTER_KEY_DERIVE_PARAMS);
  jclass jVersionClass = (*env)->FindClass(env, CLASS_VERSION);
  CK_SSL3_MASTER_KEY_DERIVE_PARAMS *ckSSL3MasterKeyDeriveParams;
  CK_VERSION *ckVersion;
  jfieldID fieldID;
  CK_MECHANISM_TYPE ckMechanismType;
  jlong jMechanismType;
  jobject jSSL3MasterKeyDeriveParams;
  jobject jVersion;

  /* get mechanism */
  fieldID = (*env)->GetFieldID(env, jMechanismClass, "mechanism", "J");
  assert(fieldID != 0);
  jMechanismType = (*env)->GetLongField(env, jMechanism, fieldID);
  ckMechanismType = jLongToCKULong(jMechanismType);
  if (ckMechanismType != ckMechanism->mechanism) {
    /* we do not have maching types, this should not occur */
    return;
  }

  /* get the native CK_SSL3_MASTER_KEY_DERIVE_PARAMS */
  ckSSL3MasterKeyDeriveParams = (CK_SSL3_MASTER_KEY_DERIVE_PARAMS *) ckMechanism->pParameter;
  if (ckSSL3MasterKeyDeriveParams != NULL_PTR) {
    /* get the native CK_VERSION */
    ckVersion = ckSSL3MasterKeyDeriveParams->pVersion;
    if (ckVersion != NULL_PTR) {
      /* get the Java CK_SSL3_MASTER_KEY_DERIVE_PARAMS (pParameter) */
      fieldID = (*env)->GetFieldID(env, jMechanismClass, "pParameter", "Ljava/lang/Object;");
      assert(fieldID != 0);
      jSSL3MasterKeyDeriveParams = (*env)->GetObjectField(env, jMechanism, fieldID);

      /* get the Java CK_VERSION */
      fieldID = (*env)->GetFieldID(env, jSSL3MasterKeyDeriveParamsClass, "pVersion", "L"CLASS_VERSION";");
      assert(fieldID != 0);
      jVersion = (*env)->GetObjectField(env, jSSL3MasterKeyDeriveParams, fieldID);

      /* now copy back the version from the native structure to the Java structure */

      /* copy back the major version */
      fieldID = (*env)->GetFieldID(env, jVersionClass, "major", "B");
      assert(fieldID != 0);
      (*env)->SetByteField(env, jVersion, fieldID, ckByteToJByte(ckVersion->major));

      /* copy back the minor version */
      fieldID = (*env)->GetFieldID(env, jVersionClass, "minor", "B");
      assert(fieldID != 0);
      (*env)->SetByteField(env, jVersion, fieldID, ckByteToJByte(ckVersion->minor));
    }

  }
}


/*
 * Copy back the derived keys and initialization vectors from the native
 * structure to the Java object. This is only used for the
 * CKM_SSL3_KEY_AND_MAC_DERIVE mechanism when used for deriving a key.
 *
 */
void copyBackSSLKeyMatParams(JNIEnv *env, CK_MECHANISM *ckMechanism, jobject jMechanism)
{
  jclass jMechanismClass= (*env)->FindClass(env, CLASS_MECHANISM);
  jclass jSSL3KeyMatParamsClass = (*env)->FindClass(env, CLASS_SSL3_KEY_MAT_PARAMS);
  jclass jSSL3KeyMatOutClass = (*env)->FindClass(env, CLASS_SSL3_KEY_MAT_OUT);
  CK_SSL3_KEY_MAT_PARAMS *ckSSL3KeyMatParam;
  CK_SSL3_KEY_MAT_OUT *ckSSL3KeyMatOut;
  jfieldID fieldID;
  CK_MECHANISM_TYPE ckMechanismType;
  jlong jMechanismType;
  CK_BYTE_PTR iv;
  jobject jSSL3KeyMatParam;
  jobject jSSL3KeyMatOut;
  jobject jIV;
  jint jLength;
  jbyte* jBytes;
  int i;

  /* get mechanism */
  fieldID = (*env)->GetFieldID(env, jMechanismClass, "mechanism", "J");
  assert(fieldID != 0);
  jMechanismType = (*env)->GetLongField(env, jMechanism, fieldID);
  ckMechanismType = jLongToCKULong(jMechanismType);
  if (ckMechanismType != ckMechanism->mechanism) {
    /* we do not have maching types, this should not occur */
    return;
  }

  /* get the native CK_SSL3_KEY_MAT_PARAMS */
  ckSSL3KeyMatParam = (CK_SSL3_KEY_MAT_PARAMS *) ckMechanism->pParameter;
  if (ckSSL3KeyMatParam != NULL_PTR) {
    // free malloc'd data
    if (ckSSL3KeyMatParam->RandomInfo.pClientRandom != NULL) {
        free(ckSSL3KeyMatParam->RandomInfo.pClientRandom);
    }
    if (ckSSL3KeyMatParam->RandomInfo.pServerRandom != NULL) {
        free(ckSSL3KeyMatParam->RandomInfo.pServerRandom);
    }

    /* get the native CK_SSL3_KEY_MAT_OUT */
    ckSSL3KeyMatOut = ckSSL3KeyMatParam->pReturnedKeyMaterial;
    if (ckSSL3KeyMatOut != NULL_PTR) {
      /* get the Java CK_SSL3_KEY_MAT_PARAMS (pParameter) */
      fieldID = (*env)->GetFieldID(env, jMechanismClass, "pParameter", "Ljava/lang/Object;");
      assert(fieldID != 0);
      jSSL3KeyMatParam = (*env)->GetObjectField(env, jMechanism, fieldID);

      /* get the Java CK_SSL3_KEY_MAT_OUT */
      fieldID = (*env)->GetFieldID(env, jSSL3KeyMatParamsClass, "pReturnedKeyMaterial", "L"CLASS_SSL3_KEY_MAT_OUT";");
      assert(fieldID != 0);
      jSSL3KeyMatOut = (*env)->GetObjectField(env, jSSL3KeyMatParam, fieldID);

      /* now copy back all the key handles and the initialization vectors */
      /* copy back client MAC secret handle */
      fieldID = (*env)->GetFieldID(env, jSSL3KeyMatOutClass, "hClientMacSecret", "J");
      assert(fieldID != 0);
      (*env)->SetLongField(env, jSSL3KeyMatOut, fieldID, ckULongToJLong(ckSSL3KeyMatOut->hClientMacSecret));

      /* copy back server MAC secret handle */
      fieldID = (*env)->GetFieldID(env, jSSL3KeyMatOutClass, "hServerMacSecret", "J");
      assert(fieldID != 0);
      (*env)->SetLongField(env, jSSL3KeyMatOut, fieldID, ckULongToJLong(ckSSL3KeyMatOut->hServerMacSecret));

      /* copy back client secret key handle */
      fieldID = (*env)->GetFieldID(env, jSSL3KeyMatOutClass, "hClientKey", "J");
      assert(fieldID != 0);
      (*env)->SetLongField(env, jSSL3KeyMatOut, fieldID, ckULongToJLong(ckSSL3KeyMatOut->hClientKey));

      /* copy back server secret key handle */
      fieldID = (*env)->GetFieldID(env, jSSL3KeyMatOutClass, "hServerKey", "J");
      assert(fieldID != 0);
      (*env)->SetLongField(env, jSSL3KeyMatOut, fieldID, ckULongToJLong(ckSSL3KeyMatOut->hServerKey));

      /* copy back the client IV */
      fieldID = (*env)->GetFieldID(env, jSSL3KeyMatOutClass, "pIVClient", "[B");
      assert(fieldID != 0);
      jIV = (*env)->GetObjectField(env, jSSL3KeyMatOut, fieldID);
      iv = ckSSL3KeyMatOut->pIVClient;

      if (jIV != NULL) {
        jLength = (*env)->GetArrayLength(env, jIV);
        jBytes = (*env)->GetByteArrayElements(env, jIV, NULL);
        /* copy the bytes to the Java buffer */
        for (i=0; i < jLength; i++) {
          jBytes[i] = ckByteToJByte(iv[i]);
        }
        /* copy back the Java buffer to the object */
        (*env)->ReleaseByteArrayElements(env, jIV, jBytes, 0);
        // free malloc'd data
        free(iv);
      }

      /* copy back the server IV */
      fieldID = (*env)->GetFieldID(env, jSSL3KeyMatOutClass, "pIVServer", "[B");
      assert(fieldID != 0);
      jIV = (*env)->GetObjectField(env, jSSL3KeyMatOut, fieldID);
      iv = ckSSL3KeyMatOut->pIVServer;

      if (jIV != NULL) {
        jLength = (*env)->GetArrayLength(env, jIV);
        jBytes = (*env)->GetByteArrayElements(env, jIV, NULL);
        /* copy the bytes to the Java buffer */
        for (i=0; i < jLength; i++) {
          jBytes[i] = ckByteToJByte(iv[i]);
        }
        /* copy back the Java buffer to the object */
        (*env)->ReleaseByteArrayElements(env, jIV, jBytes, 0);
        // free malloc'd data
        free(iv);
      }

      // free malloc'd data
      free(ckSSL3KeyMatOut);
    }
  }
}

#endif
