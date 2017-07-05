/*
 * Copyright (c) 2003, 2012, Oracle and/or its affiliates. All rights reserved.
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
 * ===========================================================================
 */

#include "pkcs11wrapper.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <assert.h>

#include "sun_security_pkcs11_wrapper_PKCS11.h"

#ifdef P11_ENABLE_C_ENCRYPTINIT
/*
 * Class:     sun_security_pkcs11_wrapper_PKCS11
 * Method:    C_EncryptInit
 * Signature: (JLsun/security/pkcs11/wrapper/CK_MECHANISM;J)V
 * Parametermapping:                    *PKCS11*
 * @param   jlong jSessionHandle        CK_SESSION_HANDLE hSession
 * @param   jobject jMechanism          CK_MECHANISM_PTR pMechanism
 * @param   jlong jKeyHandle            CK_OBJECT_HANDLE hKey
 */
JNIEXPORT void JNICALL
Java_sun_security_pkcs11_wrapper_PKCS11_C_1EncryptInit
(JNIEnv *env, jobject obj, jlong jSessionHandle,
 jobject jMechanism, jlong jKeyHandle)
{
    CK_SESSION_HANDLE ckSessionHandle;
    CK_MECHANISM ckMechanism;
    CK_OBJECT_HANDLE ckKeyHandle;
    CK_RV rv;

    CK_FUNCTION_LIST_PTR ckpFunctions = getFunctionList(env, obj);
    if (ckpFunctions == NULL) { return; }

    ckSessionHandle = jLongToCKULong(jSessionHandle);
    ckKeyHandle = jLongToCKULong(jKeyHandle);
    jMechanismToCKMechanism(env, jMechanism, &ckMechanism);
    if ((*env)->ExceptionCheck(env)) { return; }

    rv = (*ckpFunctions->C_EncryptInit)(ckSessionHandle, &ckMechanism,
                                        ckKeyHandle);

    if (ckMechanism.pParameter != NULL_PTR) {
        free(ckMechanism.pParameter);
    }

    if (ckAssertReturnValueOK(env, rv) != CK_ASSERT_OK) { return; }
}
#endif

#ifdef P11_ENABLE_C_ENCRYPT
/*
 * Class:     sun_security_pkcs11_wrapper_PKCS11
 * Method:    C_Encrypt
 * Signature: (J[BII[BII)I
 * Parametermapping:                    *PKCS11*
 * @param   jlong jSessionHandle        CK_SESSION_HANDLE hSession
 * @param   jbyteArray jData            CK_BYTE_PTR pData
 *                                      CK_ULONG ulDataLen
 * @return  jbyteArray jEncryptedData   CK_BYTE_PTR pEncryptedData
 *                                      CK_ULONG_PTR pulEncryptedDataLen
 */
JNIEXPORT jint JNICALL
Java_sun_security_pkcs11_wrapper_PKCS11_C_1Encrypt
(JNIEnv *env, jobject obj, jlong jSessionHandle,
 jbyteArray jIn, jint jInOfs, jint jInLen,
 jbyteArray jOut, jint jOutOfs, jint jOutLen)
{
    CK_SESSION_HANDLE ckSessionHandle;
    CK_RV rv;

    CK_BYTE_PTR inBufP;
    CK_BYTE_PTR outBufP;
    CK_ULONG ckEncryptedPartLen;

    CK_FUNCTION_LIST_PTR ckpFunctions = getFunctionList(env, obj);
    if (ckpFunctions == NULL) { return 0; }

    ckSessionHandle = jLongToCKULong(jSessionHandle);

    inBufP = (*env)->GetPrimitiveArrayCritical(env, jIn, NULL);
    if (inBufP == NULL) { return 0; }

    outBufP = (*env)->GetPrimitiveArrayCritical(env, jOut, NULL);
    if (outBufP == NULL) {
        // Make sure to release inBufP
        (*env)->ReleasePrimitiveArrayCritical(env, jIn, inBufP, JNI_ABORT);
        return 0;
    }

    ckEncryptedPartLen = jOutLen;

    rv = (*ckpFunctions->C_Encrypt)(ckSessionHandle,
                                    (CK_BYTE_PTR)(inBufP + jInOfs), jInLen,
                                    (CK_BYTE_PTR)(outBufP + jOutOfs),
                                    &ckEncryptedPartLen);

    (*env)->ReleasePrimitiveArrayCritical(env, jOut, outBufP, JNI_ABORT);
    (*env)->ReleasePrimitiveArrayCritical(env, jIn, inBufP, JNI_ABORT);

    ckAssertReturnValueOK(env, rv);
    return ckEncryptedPartLen;
}
#endif

#ifdef P11_ENABLE_C_ENCRYPTUPDATE
/*
 * Class:     sun_security_pkcs11_wrapper_PKCS11
 * Method:    C_EncryptUpdate
 * Signature: (J[BII[BII)I
 * Parametermapping:                    *PKCS11*
 * @param   jlong jSessionHandle        CK_SESSION_HANDLE hSession
 * @param   jbyteArray jPart            CK_BYTE_PTR pPart
 *                                      CK_ULONG ulPartLen
 * @return  jbyteArray jEncryptedPart   CK_BYTE_PTR pEncryptedPart
 *                                      CK_ULONG_PTR pulEncryptedPartLen
 */
JNIEXPORT jint JNICALL
Java_sun_security_pkcs11_wrapper_PKCS11_C_1EncryptUpdate
(JNIEnv *env, jobject obj, jlong jSessionHandle,
 jlong directIn, jbyteArray jIn, jint jInOfs, jint jInLen,
 jlong directOut, jbyteArray jOut, jint jOutOfs, jint jOutLen)
{
    CK_SESSION_HANDLE ckSessionHandle;
    CK_RV rv;

    CK_BYTE_PTR inBufP;
    CK_BYTE_PTR outBufP;
    CK_ULONG ckEncryptedPartLen;

    CK_FUNCTION_LIST_PTR ckpFunctions = getFunctionList(env, obj);
    if (ckpFunctions == NULL) { return 0; }

    ckSessionHandle = jLongToCKULong(jSessionHandle);

    if (directIn != 0) {
      inBufP = (CK_BYTE_PTR) jlong_to_ptr(directIn);
    } else {
      inBufP = (*env)->GetPrimitiveArrayCritical(env, jIn, NULL);
      if (inBufP == NULL) { return 0; }
    }

    if (directOut != 0) {
      outBufP = (CK_BYTE_PTR) jlong_to_ptr(directOut);
    } else {
      outBufP = (*env)->GetPrimitiveArrayCritical(env, jOut, NULL);
      if (outBufP == NULL) {
          // Make sure to release inBufP
          (*env)->ReleasePrimitiveArrayCritical(env, jIn, inBufP, JNI_ABORT);
          return 0;
      }
    }

    ckEncryptedPartLen = jOutLen;

    //printf("EU: inBufP=%i, jInOfs=%i, jInLen=%i, outBufP=%i\n",
    //       inBufP, jInOfs, jInLen, outBufP);

    rv = (*ckpFunctions->C_EncryptUpdate)(ckSessionHandle,
                                          (CK_BYTE_PTR)(inBufP + jInOfs), jInLen,
                                          (CK_BYTE_PTR)(outBufP + jOutOfs),
                                          &ckEncryptedPartLen);

    //printf("EU: ckEncryptedPartLen=%i\n", ckEncryptedPartLen);

    if (directIn == 0) {
        (*env)->ReleasePrimitiveArrayCritical(env, jIn, inBufP, JNI_ABORT);
    }

    if (directOut == 0) {
        (*env)->ReleasePrimitiveArrayCritical(env, jOut, outBufP, JNI_ABORT);
    }

    ckAssertReturnValueOK(env, rv);

    return ckEncryptedPartLen;
}
#endif

#ifdef P11_ENABLE_C_ENCRYPTFINAL
/*
 * Class:     sun_security_pkcs11_wrapper_PKCS11
 * Method:    C_EncryptFinal
 * Signature: (J[BII)I
 * Parametermapping:                        *PKCS11*
 * @param   jlong jSessionHandle            CK_SESSION_HANDLE hSession
 * @return  jbyteArray jLastEncryptedPart   CK_BYTE_PTR pLastEncryptedDataPart
 *                                          CK_ULONG_PTR pulLastEncryptedDataPartLen
 */
JNIEXPORT jint JNICALL
Java_sun_security_pkcs11_wrapper_PKCS11_C_1EncryptFinal
(JNIEnv *env, jobject obj, jlong jSessionHandle,
 jlong directOut, jbyteArray jOut, jint jOutOfs, jint jOutLen)
{
    CK_SESSION_HANDLE ckSessionHandle;
    CK_RV rv;
    CK_BYTE_PTR outBufP;
    CK_ULONG ckLastEncryptedPartLen;

    CK_FUNCTION_LIST_PTR ckpFunctions = getFunctionList(env, obj);
    if (ckpFunctions == NULL) { return 0; }

    ckSessionHandle = jLongToCKULong(jSessionHandle);

    if (directOut != 0) {
      outBufP = (CK_BYTE_PTR) jlong_to_ptr(directOut);
    } else {
      outBufP = (*env)->GetPrimitiveArrayCritical(env, jOut, NULL);
      if (outBufP == NULL) { return 0; }
    }

    ckLastEncryptedPartLen = jOutLen;

    //printf("EF: outBufP=%i\n", outBufP);

    rv = (*ckpFunctions->C_EncryptFinal)(ckSessionHandle,
                                         (CK_BYTE_PTR)(outBufP + jOutOfs),
                                         &ckLastEncryptedPartLen);

    //printf("EF: ckLastEncryptedPartLen=%i", ckLastEncryptedPartLen);

    if (directOut == 0) {
        (*env)->ReleasePrimitiveArrayCritical(env, jOut, outBufP, JNI_ABORT);
    }

    ckAssertReturnValueOK(env, rv);

    return ckLastEncryptedPartLen;
}
#endif

#ifdef P11_ENABLE_C_DECRYPTINIT
/*
 * Class:     sun_security_pkcs11_wrapper_PKCS11
 * Method:    C_DecryptInit
 * Signature: (JLsun/security/pkcs11/wrapper/CK_MECHANISM;J)V
 * Parametermapping:                    *PKCS11*
 * @param   jlong jSessionHandle        CK_SESSION_HANDLE hSession
 * @param   jobject jMechanism          CK_MECHANISM_PTR pMechanism
 * @param   jlong jKeyHandle            CK_OBJECT_HANDLE hKey
 */
JNIEXPORT void JNICALL
Java_sun_security_pkcs11_wrapper_PKCS11_C_1DecryptInit
(JNIEnv *env, jobject obj, jlong jSessionHandle,
 jobject jMechanism, jlong jKeyHandle)
{
    CK_SESSION_HANDLE ckSessionHandle;
    CK_MECHANISM ckMechanism;
    CK_OBJECT_HANDLE ckKeyHandle;
    CK_RV rv;

    CK_FUNCTION_LIST_PTR ckpFunctions = getFunctionList(env, obj);
    if (ckpFunctions == NULL) { return; }

    ckSessionHandle = jLongToCKULong(jSessionHandle);
    ckKeyHandle = jLongToCKULong(jKeyHandle);
    jMechanismToCKMechanism(env, jMechanism, &ckMechanism);
    if ((*env)->ExceptionCheck(env)) { return; }

    rv = (*ckpFunctions->C_DecryptInit)(ckSessionHandle, &ckMechanism,
                                        ckKeyHandle);

    if (ckMechanism.pParameter != NULL_PTR) {
        free(ckMechanism.pParameter);
    }

    if (ckAssertReturnValueOK(env, rv) != CK_ASSERT_OK) { return; }
}
#endif

#ifdef P11_ENABLE_C_DECRYPT
/*
 * Class:     sun_security_pkcs11_wrapper_PKCS11
 * Method:    C_Decrypt
 * Signature: (J[BII[BII)I
 * Parametermapping:                    *PKCS11*
 * @param   jlong jSessionHandle        CK_SESSION_HANDLE hSession
 * @param   jbyteArray jEncryptedData   CK_BYTE_PTR pEncryptedData
 *                                      CK_ULONG ulEncryptedDataLen
 * @return  jbyteArray jData            CK_BYTE_PTR pData
 *                                      CK_ULONG_PTR pulDataLen
 */
JNIEXPORT jint JNICALL
Java_sun_security_pkcs11_wrapper_PKCS11_C_1Decrypt
(JNIEnv *env, jobject obj, jlong jSessionHandle,
 jbyteArray jIn, jint jInOfs, jint jInLen,
 jbyteArray jOut, jint jOutOfs, jint jOutLen)
{
    CK_SESSION_HANDLE ckSessionHandle;
    CK_RV rv;

    CK_BYTE_PTR inBufP;
    CK_BYTE_PTR outBufP;
    CK_ULONG ckPartLen;

    CK_FUNCTION_LIST_PTR ckpFunctions = getFunctionList(env, obj);
    if (ckpFunctions == NULL) { return 0; }

    ckSessionHandle = jLongToCKULong(jSessionHandle);

    inBufP = (*env)->GetPrimitiveArrayCritical(env, jIn, NULL);
    if (inBufP == NULL) { return 0; }

    outBufP = (*env)->GetPrimitiveArrayCritical(env, jOut, NULL);
    if (outBufP == NULL) {
        // Make sure to release inBufP
        (*env)->ReleasePrimitiveArrayCritical(env, jIn, inBufP, JNI_ABORT);
        return 0;
    }

    ckPartLen = jOutLen;

    rv = (*ckpFunctions->C_Decrypt)(ckSessionHandle,
                                    (CK_BYTE_PTR)(inBufP + jInOfs), jInLen,
                                    (CK_BYTE_PTR)(outBufP + jOutOfs),
                                    &ckPartLen);

    (*env)->ReleasePrimitiveArrayCritical(env, jOut, outBufP, JNI_ABORT);
    (*env)->ReleasePrimitiveArrayCritical(env, jIn, inBufP, JNI_ABORT);

    ckAssertReturnValueOK(env, rv);

    return ckPartLen;
}
#endif

#ifdef P11_ENABLE_C_DECRYPTUPDATE
/*
 * Class:     sun_security_pkcs11_wrapper_PKCS11
 * Method:    C_DecryptUpdate
 * Signature: (J[BII[BII)I
 * Parametermapping:                    *PKCS11*
 * @param   jlong jSessionHandle        CK_SESSION_HANDLE hSession
 * @param   jbyteArray jEncryptedPart   CK_BYTE_PTR pEncryptedPart
 *                                      CK_ULONG ulEncryptedPartLen
 * @return  jbyteArray jPart            CK_BYTE_PTR pPart
 *                                      CK_ULONG_PTR pulPartLen
 */
JNIEXPORT jint JNICALL
Java_sun_security_pkcs11_wrapper_PKCS11_C_1DecryptUpdate
(JNIEnv *env, jobject obj, jlong jSessionHandle,
 jlong directIn, jbyteArray jIn, jint jInOfs, jint jInLen,
 jlong directOut, jbyteArray jOut, jint jOutOfs, jint jOutLen)
{
    CK_SESSION_HANDLE ckSessionHandle;
    CK_RV rv;

    CK_BYTE_PTR inBufP;
    CK_BYTE_PTR outBufP;
    CK_ULONG ckDecryptedPartLen;

    CK_FUNCTION_LIST_PTR ckpFunctions = getFunctionList(env, obj);
    if (ckpFunctions == NULL) { return 0; }

    ckSessionHandle = jLongToCKULong(jSessionHandle);

    if (directIn != 0) {
      inBufP = (CK_BYTE_PTR) jlong_to_ptr(directIn);
    } else {
      inBufP = (*env)->GetPrimitiveArrayCritical(env, jIn, NULL);
      if (inBufP == NULL) { return 0; }
    }

    if (directOut != 0) {
      outBufP = (CK_BYTE_PTR) jlong_to_ptr(directOut);
    } else {
      outBufP = (*env)->GetPrimitiveArrayCritical(env, jOut, NULL);
      if (outBufP == NULL) {
          // Make sure to release inBufP
          (*env)->ReleasePrimitiveArrayCritical(env, jIn, inBufP, JNI_ABORT);
          return 0;
      }
    }

    ckDecryptedPartLen = jOutLen;

    rv = (*ckpFunctions->C_DecryptUpdate)(ckSessionHandle,
                                          (CK_BYTE_PTR)(inBufP + jInOfs), jInLen,
                                          (CK_BYTE_PTR)(outBufP + jOutOfs),
                                          &ckDecryptedPartLen);
    if (directIn == 0) {
        (*env)->ReleasePrimitiveArrayCritical(env, jIn, inBufP, JNI_ABORT);
    }

    if (directOut == 0) {
        (*env)->ReleasePrimitiveArrayCritical(env, jOut, outBufP, JNI_ABORT);
    }

    ckAssertReturnValueOK(env, rv);

    return ckDecryptedPartLen;
}

#endif

#ifdef P11_ENABLE_C_DECRYPTFINAL
/*
 * Class:     sun_security_pkcs11_wrapper_PKCS11
 * Method:    C_DecryptFinal
 * Signature: (J[BII)I
 * Parametermapping:                    *PKCS11*
 * @param   jlong jSessionHandle        CK_SESSION_HANDLE hSession
 * @return  jbyteArray jLastPart        CK_BYTE_PTR pLastPart
 *                                      CK_ULONG_PTR pulLastPartLen
 */
JNIEXPORT jint JNICALL
Java_sun_security_pkcs11_wrapper_PKCS11_C_1DecryptFinal
(JNIEnv *env, jobject obj, jlong jSessionHandle,
 jlong directOut, jbyteArray jOut, jint jOutOfs, jint jOutLen)
{
    CK_SESSION_HANDLE ckSessionHandle;
    CK_RV rv;
    CK_BYTE_PTR outBufP;
    CK_ULONG ckLastPartLen;

    CK_FUNCTION_LIST_PTR ckpFunctions = getFunctionList(env, obj);
    if (ckpFunctions == NULL) { return 0; }

    ckSessionHandle = jLongToCKULong(jSessionHandle);

    if (directOut != 0) {
      outBufP = (CK_BYTE_PTR) jlong_to_ptr(directOut);
    } else {
      outBufP = (*env)->GetPrimitiveArrayCritical(env, jOut, NULL);
      if (outBufP == NULL) { return 0; }
    }

    ckLastPartLen = jOutLen;

    rv = (*ckpFunctions->C_DecryptFinal)(ckSessionHandle,
                                         (CK_BYTE_PTR)(outBufP + jOutOfs),
                                         &ckLastPartLen);

    if (directOut == 0) {
        (*env)->ReleasePrimitiveArrayCritical(env, jOut, outBufP, JNI_ABORT);

    }

    ckAssertReturnValueOK(env, rv);

    return ckLastPartLen;
}
#endif
