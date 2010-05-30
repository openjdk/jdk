/*
 * Copyright (c) 2003, 2009, Oracle and/or its affiliates. All rights reserved.
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
    CK_BYTE IBUF[MAX_STACK_BUFFER_LEN];
    CK_BYTE OBUF[MAX_STACK_BUFFER_LEN];
    CK_BYTE_PTR inBufP;
    CK_BYTE_PTR outBufP;
    CK_ULONG ckEncryptedPartLen;

    CK_FUNCTION_LIST_PTR ckpFunctions = getFunctionList(env, obj);
    if (ckpFunctions == NULL) { return 0; }

    ckSessionHandle = jLongToCKULong(jSessionHandle);

    if (jInLen > MAX_STACK_BUFFER_LEN) {
      inBufP = (CK_BYTE_PTR)malloc((size_t)jInLen);
      if (inBufP == NULL) {
        JNU_ThrowOutOfMemoryError(env, 0);
        return 0;
      }
    } else {
      inBufP = IBUF;
    }
    (*env)->GetByteArrayRegion(env, jIn, jInOfs, jInLen, (jbyte *)inBufP);
    if ((*env)->ExceptionCheck(env)) {
      if (inBufP != IBUF) { free(inBufP); }
      return 0;
    }

    ckEncryptedPartLen = jOutLen;
    if (jOutLen > MAX_STACK_BUFFER_LEN) {
      outBufP = (CK_BYTE_PTR)malloc((size_t)jOutLen);
      if (outBufP == NULL) {
        if (inBufP != IBUF) {
          free(inBufP);
        }
        JNU_ThrowOutOfMemoryError(env, 0);
        return 0;
      }
    } else {
      outBufP = OBUF;
    }

    rv = (*ckpFunctions->C_Encrypt)(ckSessionHandle, inBufP, jInLen,
                                    outBufP, &ckEncryptedPartLen);

    if (ckAssertReturnValueOK(env, rv) == CK_ASSERT_OK) {
      if (ckEncryptedPartLen > 0) {
        (*env)->SetByteArrayRegion(env, jOut, jOutOfs, ckEncryptedPartLen,
                                   (jbyte *)outBufP);
      }
    }
    if (inBufP != IBUF) {
      free(inBufP);
    }
    if (outBufP != OBUF) {
      free(outBufP);
    }
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
    CK_BYTE IBUF[MAX_STACK_BUFFER_LEN];
    CK_BYTE OBUF[MAX_STACK_BUFFER_LEN];
    CK_BYTE_PTR inBufP;
    CK_BYTE_PTR outBufP;
    CK_ULONG ckEncryptedPartLen;

    CK_FUNCTION_LIST_PTR ckpFunctions = getFunctionList(env, obj);
    if (ckpFunctions == NULL) { return 0; }

    ckSessionHandle = jLongToCKULong(jSessionHandle);

    if (directIn != 0) {
      inBufP = (CK_BYTE_PTR)(directIn + jInOfs);
    } else {
      if (jInLen > MAX_STACK_BUFFER_LEN) {
        inBufP = (CK_BYTE_PTR)malloc((size_t)jInLen);
        if (inBufP == NULL) {
          JNU_ThrowOutOfMemoryError(env, 0);
          return 0;
        }
      } else {
        inBufP = IBUF;
      }
      (*env)->GetByteArrayRegion(env, jIn, jInOfs, jInLen, (jbyte *)inBufP);
      if ((*env)->ExceptionCheck(env)) {
        if (directIn == 0 && inBufP != IBUF) { free(inBufP); }
        return 0;
      }
    }

    ckEncryptedPartLen = jOutLen;
    if (directOut != 0) {
      outBufP = (CK_BYTE_PTR)(directOut + jOutOfs);
    } else {
      if (jOutLen > MAX_STACK_BUFFER_LEN) {
        outBufP = (CK_BYTE_PTR)malloc((size_t)jOutLen);
        if (outBufP == NULL) {
          if (directIn == 0 && inBufP != IBUF) {
            free(inBufP);
          }
          JNU_ThrowOutOfMemoryError(env, 0);
          return 0;
        }
      } else {
        outBufP = OBUF;
      }
    }

    //printf("EU: inBufP=%i, jInOfs=%i, jInLen=%i, outBufP=%i\n",
    //       inBufP, jInOfs, jInLen, outBufP);

    rv = (*ckpFunctions->C_EncryptUpdate)(ckSessionHandle,
                                          inBufP, jInLen,
                                          outBufP, &ckEncryptedPartLen);

    //printf("EU: ckEncryptedPartLen=%i\n", ckEncryptedPartLen);

    if (directIn == 0 && inBufP != IBUF) {
      free(inBufP);
    }

    if (ckAssertReturnValueOK(env, rv) == CK_ASSERT_OK) {
      if (directOut == 0 && ckEncryptedPartLen > 0) {
        (*env)->SetByteArrayRegion(env, jOut, jOutOfs, ckEncryptedPartLen,
                                   (jbyte *)outBufP);
      }
    }
    if (directOut == 0 && outBufP != OBUF) {
      free(outBufP);
    }
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
    CK_BYTE BUF[MAX_STACK_BUFFER_LEN];
    CK_BYTE_PTR outBufP;
    CK_ULONG ckLastEncryptedPartLen;

    CK_FUNCTION_LIST_PTR ckpFunctions = getFunctionList(env, obj);
    if (ckpFunctions == NULL) { return 0; }

    ckSessionHandle = jLongToCKULong(jSessionHandle);

    ckLastEncryptedPartLen = jOutLen;
    if (directOut != 0) {
      outBufP = (CK_BYTE_PTR)(directOut + jOutOfs);
    } else {
      // output length should always be less than MAX_STACK_BUFFER_LEN
      outBufP = BUF;
    }

    //printf("EF: outBufP=%i\n", outBufP);

    rv = (*ckpFunctions->C_EncryptFinal)(ckSessionHandle, outBufP,
                                         &ckLastEncryptedPartLen);

    //printf("EF: ckLastEncryptedPartLen=%i", ckLastEncryptedPartLen);

    if (ckAssertReturnValueOK(env, rv) == CK_ASSERT_OK) {
      if (directOut == 0 && ckLastEncryptedPartLen > 0) {
        (*env)->SetByteArrayRegion(env, jOut, jOutOfs, ckLastEncryptedPartLen,
                                   (jbyte *)outBufP);
      }
    }

    if (directOut == 0 && outBufP != BUF) {
      free(outBufP);
    }
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
    CK_BYTE IBUF[MAX_STACK_BUFFER_LEN];
    CK_BYTE OBUF[MAX_STACK_BUFFER_LEN];
    CK_BYTE_PTR inBufP;
    CK_BYTE_PTR outBufP;
    CK_ULONG ckPartLen;

    CK_FUNCTION_LIST_PTR ckpFunctions = getFunctionList(env, obj);
    if (ckpFunctions == NULL) { return 0; }

    ckSessionHandle = jLongToCKULong(jSessionHandle);

    if (jInLen > MAX_STACK_BUFFER_LEN) {
      inBufP = (CK_BYTE_PTR)malloc((size_t)jInLen);
      if (inBufP == NULL) {
        JNU_ThrowOutOfMemoryError(env, 0);
        return 0;
      }
    } else {
      inBufP = IBUF;
    }
    (*env)->GetByteArrayRegion(env, jIn, jInOfs, jInLen, (jbyte *)inBufP);
    if ((*env)->ExceptionCheck(env)) {
      if (inBufP != IBUF) { free(inBufP); }
      return 0;
    }

    ckPartLen = jOutLen;
    if (jOutLen > MAX_STACK_BUFFER_LEN) {
      outBufP = (CK_BYTE_PTR)malloc((size_t)jOutLen);
      if (outBufP == NULL) {
        if (inBufP != IBUF) {
          free(inBufP);
        }
        JNU_ThrowOutOfMemoryError(env, 0);
        return 0;
      }
    } else {
      outBufP = OBUF;
    }
    rv = (*ckpFunctions->C_Decrypt)(ckSessionHandle, inBufP, jInLen,
                                    outBufP, &ckPartLen);

    if (ckAssertReturnValueOK(env, rv) == CK_ASSERT_OK) {
      if (ckPartLen > 0) {
        (*env)->SetByteArrayRegion(env, jOut, jOutOfs, ckPartLen,
                                   (jbyte *)outBufP);
      }
    }
    if (inBufP != IBUF) {
      free(inBufP);
    }
    if (outBufP != OBUF) {
      free(outBufP);
    }

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
    CK_BYTE IBUF[MAX_STACK_BUFFER_LEN];
    CK_BYTE OBUF[MAX_STACK_BUFFER_LEN];
    CK_BYTE_PTR inBufP;
    CK_BYTE_PTR outBufP;
    CK_ULONG ckDecryptedPartLen;

    CK_FUNCTION_LIST_PTR ckpFunctions = getFunctionList(env, obj);
    if (ckpFunctions == NULL) { return 0; }

    ckSessionHandle = jLongToCKULong(jSessionHandle);

    if (directIn != 0) {
      inBufP = (CK_BYTE_PTR)(directIn + jInOfs);
    } else {
      if (jInLen > MAX_STACK_BUFFER_LEN) {
        inBufP = (CK_BYTE_PTR)malloc((size_t)jInLen);
        if (inBufP == NULL) {
          JNU_ThrowOutOfMemoryError(env, 0);
          return 0;
        }
      } else {
        inBufP = IBUF;
      }
      (*env)->GetByteArrayRegion(env, jIn, jInOfs, jInLen, (jbyte *)inBufP);
      if ((*env)->ExceptionCheck(env)) {
        if (directIn == 0 && inBufP != IBUF) { free(inBufP); }
        return 0;
      }
    }

    ckDecryptedPartLen = jOutLen;
    if (directOut != 0) {
      outBufP = (CK_BYTE_PTR)(directOut + jOutOfs);
    } else {
      if (jOutLen > MAX_STACK_BUFFER_LEN) {
        outBufP = (CK_BYTE_PTR)malloc((size_t)jOutLen);
        if (outBufP == NULL) {
          if (directIn == 0 && inBufP != IBUF) {
            free(inBufP);
          }
          JNU_ThrowOutOfMemoryError(env, 0);
          return 0;
      }
      } else {
        outBufP = OBUF;
      }
    }

    rv = (*ckpFunctions->C_DecryptUpdate)(ckSessionHandle, inBufP, jInLen,
                                          outBufP, &ckDecryptedPartLen);

    if (directIn == 0 && inBufP != IBUF) {
      free(inBufP);
    }

    if (ckAssertReturnValueOK(env, rv) == CK_ASSERT_OK) {
      if (directOut == 0 && ckDecryptedPartLen > 0) {
        (*env)->SetByteArrayRegion(env, jOut, jOutOfs, ckDecryptedPartLen,
                                   (jbyte *)outBufP);
      }
    }

    if (directOut == 0 && outBufP != OBUF) {
      free(outBufP);
    }
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
    CK_BYTE BUF[MAX_STACK_BUFFER_LEN];
    CK_BYTE_PTR outBufP;
    CK_ULONG ckLastPartLen;

    CK_FUNCTION_LIST_PTR ckpFunctions = getFunctionList(env, obj);
    if (ckpFunctions == NULL) { return 0; }

    ckSessionHandle = jLongToCKULong(jSessionHandle);

    ckLastPartLen = jOutLen;
    if (directOut != 0) {
      outBufP = (CK_BYTE_PTR)(directOut + jOutOfs);
    } else {
      // jOutLen should always be less than MAX_STACK_BUFFER_LEN
      outBufP = BUF;
    }

    rv = (*ckpFunctions->C_DecryptFinal)(ckSessionHandle, outBufP,
                                         &ckLastPartLen);

    if (ckAssertReturnValueOK(env, rv) == CK_ASSERT_OK) {
      if (directOut == 0 && ckLastPartLen > 0) {
        (*env)->SetByteArrayRegion(env, jOut, jOutOfs, ckLastPartLen,
                                   (jbyte *)outBufP);
      }
    }

    if (directOut == 0 && outBufP != BUF) {
      free(outBufP);
    }
    return ckLastPartLen;
}
#endif
