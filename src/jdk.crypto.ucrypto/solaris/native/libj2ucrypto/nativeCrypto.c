/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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

#include <stdlib.h>
#include <string.h>
#include <strings.h>
#include <jni.h>
#include "jni_util.h"
#include "nativeCrypto.h"
#include "nativeFunc.h"
#include "com_oracle_security_ucrypto_NativeCipher.h"
#include "com_oracle_security_ucrypto_NativeDigest.h"
#include "com_oracle_security_ucrypto_NativeKey.h"
#include "com_oracle_security_ucrypto_NativeKey.h"
#include "com_oracle_security_ucrypto_NativeRSACipher.h"
#include "com_oracle_security_ucrypto_NativeRSASignature.h"
#include "com_oracle_security_ucrypto_UcryptoProvider.h"

/*
 * Dumps out byte array in hex with and name and length info
 */
void printError(char* header, int mech, int rv) {
  if (mech != -1) {
    printf("%s, mech = %d, rv = 0x%0x\n", header, mech, rv);
  } else {
    printf("%s, rv = 0x%0x\n", header, rv);
  }
  if (*ftab->ucryptoStrerror != NULL) {
    char * reason = (*ftab->ucryptoStrerror)(rv);
    printf("\tcause = %s\n", reason);
    free(reason);
  }
}

/*
 * Dumps out byte array in hex with and name and length info
 */
void printBytes(char* header, unsigned char* bytes, int len) {
  int i;

  printf("%s", header);
  printf("len=%d {", len);
  for (i = 0; i < len; i++) {
    if (i > 0) printf(":");
    printf("%02X", bytes[i]);
  }
  printf("}\n");
}

/*
 * Throws java.lang.OutOfMemoryError
 */
void throwOutOfMemoryError(JNIEnv *env, const char *msg)
{
  jclass jExClass = (*env)->FindClass(env, "java/lang/OutOfMemoryError");
  if (jExClass != 0) /* Otherwise an exception has already been thrown */ {
    (*env)->ThrowNew(env, jExClass, msg);
  }
  /* free the local ref */
  (*env)->DeleteLocalRef(env, jExClass);
}

/*
 * De-allocates all memory associated with crypto_ctx_t
 */
void freeContext(crypto_ctx_t *context) {
  if (ftab->ucryptoFreeContext != NULL) {
    (*ftab->ucryptoFreeContext)(context);
  }
  free(context);
}

JNIEXPORT jint JNICALL DEF_JNI_OnLoad(JavaVM *vm, void *reserved) {
    return JNI_VERSION_1_4;
}

/*
 * Class:     com_oracle_security_ucrypto_UcryptoProvider
 * Method:    loadLibraries
 * Signature: ()[Z
 */
JNIEXPORT jbooleanArray JNICALL Java_com_oracle_security_ucrypto_UcryptoProvider_loadLibraries
(JNIEnv *env, jclass jcls) {
  jbooleanArray jResult;
  jboolean *result;
  jResult = (*env)->NewBooleanArray(env, 2);

  if (jResult != NULL) {
    result = loadNative();
    (*env)->SetBooleanArrayRegion(env, jResult, 0, 2, result);
    free(result);
  }
  return jResult;
}

/*
 * Class:     com_oracle_security_ucrypto_UcryptoProvider
 * Method:    getMechList
 * Signature: ()Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_com_oracle_security_ucrypto_UcryptoProvider_getMechList
(JNIEnv *env, jclass jcls) {
  jstring jResult;
  char* result;
  int length;

  jResult = NULL;
  if (ftab->ucryptoVersion != NULL && ftab->ucryptoGetMechList != NULL) {
      length = (*ftab->ucryptoGetMechList)(NULL);
      if (J2UC_DEBUG) printf("mech list length: %d\n", length);
      result = malloc(length);
      if (result == NULL) {
        throwOutOfMemoryError(env, NULL);
        return NULL;
      }
      length = (*ftab->ucryptoGetMechList)(result);
      if (J2UC_DEBUG) printf("mech list: %s\n", result);
      jResult = (*env)->NewStringUTF(env, result);
      free(result);
  } else {
      // version 0 on Solaris 10
      result = "CRYPTO_AES_ECB,CRYPTO_AES_CBC,CRYPTO_AES_CFB128,";
      jResult = (*env)->NewStringUTF(env, result);
  }
  return jResult;
}

/*
 * Utility function for throwing a UcryptoException when rv is not CRYPTO_OK(0)
 */
void throwUCExceptionUsingRV(JNIEnv *env, int rv) {
  jclass jExClass;
  jmethodID jConstructor;
  jthrowable jException;

  if ((*env)->ExceptionCheck(env)) return;

  jExClass = (*env)->FindClass(env, "com/oracle/security/ucrypto/UcryptoException");
  /* if jExClass is NULL, an exception has already been thrown */
  if (jExClass != NULL) {
    jConstructor = (*env)->GetMethodID(env, jExClass, "<init>", "(I)V");
    if (jConstructor != NULL) {
      jException = (jthrowable) (*env)->NewObject(env, jExClass, jConstructor, rv);
      if (jException != NULL) {
        (*env)->Throw(env, jException);
      }
    }
  }
  /* free the local ref */
  (*env)->DeleteLocalRef(env, jExClass);
}

/*
 * Utility function for duplicating a byte array from jbyteArray
 * If anything went wrong, no memory will be allocated.
 * NOTE: caller is responsible for freeing the allocated memory
 * once this method returned successfully.
 */
jbyte* getBytes(JNIEnv *env, jbyteArray bytes, int offset, int len) {
  jbyte* result = NULL;

  if (!(*env)->ExceptionCheck(env)) {
    result = (jbyte*) calloc(len, sizeof(char));
    if (result == NULL) {
      throwOutOfMemoryError(env, NULL);
      return NULL;
    }
    (*env)->GetByteArrayRegion(env, bytes, offset, len, result);
    if ((*env)->ExceptionCheck(env)) {
        // free allocated memory if error occurred
        free(result);
        return NULL;
    }
  }
  return result;
}


int
CipherInit(crypto_ctx_t *context, int encrypt, ucrypto_mech_t mech,
           unsigned char *jKey, int jKeyLen, unsigned char *jIv, int jIvLen,
           int tagLen, unsigned char *jAad, int jAadLen)

{
  int rv = 0;
  void *iv;
  size_t ivLen;

  if (J2UC_DEBUG) printf("CipherInit: mech %i, key %i(%i), iv %i(%i) tagLen %i, aad %i(%i)\n",
                    mech, jKey, jKeyLen, jIv, jIvLen, tagLen, jAad, jAadLen);
  if (mech == CRYPTO_AES_CTR) {
    ivLen = sizeof(CK_AES_CTR_PARAMS);
    iv = (CK_AES_CTR_PARAMS*) malloc(ivLen);
    if (iv == NULL) return -1;

    ((CK_AES_CTR_PARAMS*)iv)->ulCounterBits = 32;
    memcpy(((CK_AES_CTR_PARAMS*)iv)->cb, jIv, 16);
  } else if (mech == CRYPTO_AES_GCM) {
    ivLen = sizeof(CK_AES_GCM_PARAMS);
    iv = (CK_AES_GCM_PARAMS*) malloc(ivLen);
    if (iv == NULL) return -1;

    ((CK_AES_GCM_PARAMS*)iv)->pIv = (uchar_t *)jIv;
    ((CK_AES_GCM_PARAMS*)iv)->ulIvLen = (ulong_t)jIvLen;
    ((CK_AES_GCM_PARAMS*)iv)->ulIvBits = 96;
    ((CK_AES_GCM_PARAMS*)iv)->pAAD = (uchar_t *)jAad;
    ((CK_AES_GCM_PARAMS*)iv)->ulAADLen = (ulong_t)jAadLen;
    ((CK_AES_GCM_PARAMS*)iv)->ulTagBits = (ulong_t)tagLen;
  } else {
    // normal bytes
    iv = jIv;
    ivLen = jIvLen;
  }
  if (encrypt) {
    rv = (*ftab->ucryptoEncryptInit)(context, mech, jKey, (size_t)jKeyLen, iv, ivLen);
    if (rv != 0 && J2UC_DEBUG) printError("ucryptoEncryptInit", mech, rv);
  } else {
    rv =(*ftab->ucryptoDecryptInit)(context, mech, jKey, (size_t)jKeyLen, iv, ivLen);
    if (rv != 0 && J2UC_DEBUG) printError("ucryptoDecryptInit", mech, rv);
  }

  if (iv != jIv) {
    if (mech == CRYPTO_AES_CTR) {
      free((CK_AES_CTR_PARAMS*)iv);
    } else {
      free((CK_AES_GCM_PARAMS*)iv);
    }
  }

  return rv;
}

int
CipherUpdate(crypto_ctx_t *context, int encrypt, unsigned char *bufIn, int inOfs,
             int inLen, unsigned char *bufOut, int outOfs, int *outLen)
{
  int rv = 0;
  size_t outLength;

  outLength = (size_t) *outLen;
  if (J2UC_DEBUG) {
    printf("CipherUpdate: Inofs %i, InLen %i, OutOfs %i, OutLen %i\n", inOfs, inLen, outOfs, *outLen);
    printBytes("BufIn=", (unsigned char*)(bufIn+inOfs), inLen);
  }
  if (encrypt) {
    rv = (*ftab->ucryptoEncryptUpdate)(context, (unsigned char*)(bufIn+inOfs), (size_t)inLen, (unsigned char*)(bufOut+outOfs), &outLength);
    if (rv) {
      if (J2UC_DEBUG) printError("ucryptoEncryptUpdate", -1, rv);
    } else {
      *outLen = (int)outLength;
    }
  } else {
    rv = (*ftab->ucryptoDecryptUpdate)(context, (unsigned char*)(bufIn+inOfs), (size_t)inLen, (unsigned char*)(bufOut+outOfs), &outLength);
    if (rv) {
      if (J2UC_DEBUG) printError("ucryptoDecryptUpdate", -1, rv);
    } else {
      if (J2UC_DEBUG) printBytes("BufOut=", (unsigned char*)(bufOut+outOfs), outLength);
      *outLen = (int)outLength;
    }
  }

  return rv;
}

int
CipherFinal(crypto_ctx_t *context, int encrypt, unsigned char *bufOut, int outOfs, int *outLen)
{
  int rv = 0;
  size_t outLength;

  outLength = (size_t)*outLen;

  if (J2UC_DEBUG) printf("CipherFinal: OutOfs %i, outLen %i\n", outOfs, *outLen);
  if (encrypt) {
    rv = (*ftab->ucryptoEncryptFinal)(context, (unsigned char*)(bufOut+outOfs), &outLength);
    if (rv) {
      if (J2UC_DEBUG) printError("ucryptoDecryptFinal", -1, rv);
    } else {
      if (J2UC_DEBUG) printBytes("BufOut=", (unsigned char*)(bufOut+outOfs), outLength);
      *outLen = (int)outLength;
    }
  } else {
    rv = (*ftab->ucryptoDecryptFinal)(context, (unsigned char*)(bufOut+outOfs), &outLength);
    if (rv) {
      if (J2UC_DEBUG) printError("ucryptoDecryptFinal", -1, rv);
    } else {
      if (J2UC_DEBUG) printBytes("BufOut=", (unsigned char*)(bufOut+outOfs), outLength);
      *outLen = (int)outLength;
    }
  }
  return rv;
}

////////////////////////////////////////////////////////
// SPECIAL ENTRIES FOR JVM JNI-BYPASSING OPTIMIZATION
////////////////////////////////////////////////////////
JNIEXPORT jlong JNICALL
JavaCritical_com_oracle_security_ucrypto_NativeDigest_nativeInit(jint mech) {
  crypto_ctx_t *context = NULL;
  int rv;

  context = malloc(sizeof(crypto_ctx_t));
  if (context != NULL) {
    rv = (*ftab->ucryptoDigestInit)(context, (ucrypto_mech_t) mech, NULL, 0);
    if (rv) {
      freeContext(context);
      if (J2UC_DEBUG) printError("ucryptoDigestInit", mech, rv);
      return 0L;
    }
  }
  return (jlong) context;
}

JNIEXPORT jint JNICALL
JavaCritical_com_oracle_security_ucrypto_NativeDigest_nativeUpdate
  (jint mech, jlong pContext, int notUsed, unsigned char* in, jint ofs, jint len) {
  crypto_ctx_t *context;
  jint rv = 0;

  context = (crypto_ctx_t *) pContext;
  rv = (*ftab->ucryptoDigestUpdate)(context, (const unsigned char*)(in + ofs),
                                    (size_t) len);

  if (rv) {
    freeContext(context);
    if (J2UC_DEBUG) printError("ucryptoDigestUpdate", mech, rv);
  }

  return -rv; // use negative value to indicate error
}

JNIEXPORT jint JNICALL
JavaCritical_com_oracle_security_ucrypto_NativeDigest_nativeDigest
  (jint mech, jlong pContext, int notUsed, unsigned char* out, jint ofs, jint digestLen) {
  crypto_ctx_t *context;
  jint rv = 0;
  size_t digest_len = digestLen;

  context = (crypto_ctx_t *) pContext;
  rv = (*ftab->ucryptoDigestFinal)(context, (unsigned char*)(out + ofs),
                                   &digest_len);
  if (rv) {
    freeContext(context);
    if (J2UC_DEBUG) printError("ucryptoDigestFinal", mech, rv);
  }

  return -rv; // use negative value to indicate error
}

JNIEXPORT void JNICALL
JavaCritical_com_oracle_security_ucrypto_NativeDigest_nativeFree
  (jint mech, jlong pContext) {
  crypto_ctx_t *context;

  context = (crypto_ctx_t *) pContext;
  freeContext(context);
}

// AES
JNIEXPORT jlong JNICALL
JavaCritical_com_oracle_security_ucrypto_NativeCipher_nativeInit
  (jint mech, jboolean encrypt, int keyLen, unsigned char* bufKey,
   int ivLen, unsigned char* bufIv, jint tagLen, int aadLen, unsigned char* bufAad) {
  crypto_ctx_t *context = NULL;
  int rv;

  context = malloc(sizeof(crypto_ctx_t));
  if (context != NULL) {
    rv = CipherInit(context, encrypt, (ucrypto_mech_t) mech, bufKey, keyLen,
                    bufIv, ivLen, tagLen, bufAad, aadLen);
    if (rv) {
      freeContext(context);
      return 0L;
    }
  }
  return (jlong)context;
}

/*
 * Class:     com_oracle_security_ucrypto_NativeCipher
 * Method:    nativeUpdate
 * Signature: (JZ[BII[BI)I
 */
JNIEXPORT jint JNICALL
JavaCritical_com_oracle_security_ucrypto_NativeCipher_nativeUpdate
  (jlong pContext, jboolean encrypt, int notUsed, jbyte* bufIn, jint inOfs, jint inLen,
   int outCapacity, jbyte* bufOut, jint outOfs) {
  crypto_ctx_t *context;
  int rv = 0;
  int outLen = outCapacity - outOfs; // recalculate the real out length

  context = (crypto_ctx_t *) pContext;
  rv = CipherUpdate(context, encrypt, (unsigned char*)bufIn, inOfs, inLen, (unsigned char*)bufOut, outOfs, &outLen);
  if (rv) {
    freeContext(context);
    return -rv; // use negative value to indicate error!
  }

  return outLen;
}

/*
 * Class:     com_oracle_security_ucrypto_NativeCipher
 * Method:    nativeFinal
 * Signature: (JZ[BI)I
 */
JNIEXPORT jint JNICALL
JavaCritical_com_oracle_security_ucrypto_NativeCipher_nativeFinal
  (jlong pContext, jboolean encrypt, int outLen, jbyte* out, jint outOfs) {
  crypto_ctx_t *context;
  int rv = 0;
  unsigned char* bufOut = (unsigned char*) out;

  context = (crypto_ctx_t *) pContext;
  // Avoid null output buffer to workaround Solaris bug21481818 (fixed in S12)
  if (bufOut == NULL) {
    bufOut = (unsigned char*)(&outLen);
    outLen = 0;
  }
  rv = CipherFinal(context, encrypt, bufOut, outOfs, &outLen);
  freeContext(context);
  if (rv) {
     return -rv; // use negative value to indicate error!
  }

  return outLen;
}

/*
 * Class:     com_oracle_security_ucrypto_NativeDigest
 * Method:    nativeInit
 * Signature: (I)J
 */
JNIEXPORT jlong JNICALL Java_com_oracle_security_ucrypto_NativeDigest_nativeInit
  (JNIEnv *env, jclass jcls, jint mech) {
  jlong result = JavaCritical_com_oracle_security_ucrypto_NativeDigest_nativeInit(mech);
  if (result == NULL) {
     throwOutOfMemoryError(env, NULL);
  }
  return result;
}

/*
 * Class:     com_oracle_security_ucrypto_NativeDigest
 * Method:    nativeUpdate
 * Signature: (IJ[BII)I
 */
JNIEXPORT jint JNICALL Java_com_oracle_security_ucrypto_NativeDigest_nativeUpdate
  (JNIEnv *env, jclass jcls, jint mech, jlong pContext, jbyteArray jIn, jint jOfs, jint jLen) {
  unsigned char *bufIn;
  jint rv = 0;


  bufIn = (unsigned char *) getBytes(env, jIn, jOfs, jLen);
  if (!(*env)->ExceptionCheck(env)) {
    rv = JavaCritical_com_oracle_security_ucrypto_NativeDigest_nativeUpdate(mech, pContext, jLen, bufIn, 0, jLen);
    free(bufIn);
  }
  return rv;
}

/*
 * Class:     com_oracle_security_ucrypto_NativeDigest
 * Method:    nativeDigest
 * Signature: (IJ[BII)I
 */
JNIEXPORT jint JNICALL Java_com_oracle_security_ucrypto_NativeDigest_nativeDigest
  (JNIEnv *env, jclass jcls, jint mech, jlong pContext, jbyteArray jOut, jint jOutOfs, jint digestLen) {
  unsigned char *bufOut;
  jint rv = 0;

  bufOut = (unsigned char *) malloc(digestLen);
  if (bufOut == NULL) {
    throwOutOfMemoryError(env, NULL);
    return 0;
  }

  rv = JavaCritical_com_oracle_security_ucrypto_NativeDigest_nativeDigest(mech, pContext, digestLen, bufOut, 0, digestLen);
  if (rv == 0) {
      (*env)->SetByteArrayRegion(env, jOut, jOutOfs, digestLen, (jbyte *) bufOut);
  }
  free(bufOut);
  return rv;
}

/*
 * Class:     com_oracle_security_ucrypto_NativeDigest
 * Method:    nativeFree
 * Signature: (IJ)V
 */
JNIEXPORT void JNICALL Java_com_oracle_security_ucrypto_NativeDigest_nativeFree
  (JNIEnv *env, jclass jcls, jint mech, jlong pContext) {
  JavaCritical_com_oracle_security_ucrypto_NativeDigest_nativeFree(mech, pContext);
}

/*
 * Class:     com_oracle_security_ucrypto_NativeCipher
 * Method:    nativeInit
 * Signature: (IZ[B[BI[B)J
 */
JNIEXPORT jlong JNICALL Java_com_oracle_security_ucrypto_NativeCipher_nativeInit
(JNIEnv *env, jclass jcls, jint mech, jboolean encrypt, jbyteArray jKey,
 jbyteArray jIv, jint tagLen, jbyteArray jAad) {

  crypto_ctx_t *context;
  unsigned char *bufKey;
  unsigned char *bufIv;
  unsigned char *bufAad;
  int keyLen, ivLen, aadLen, rv = 0;
  jlong result = 0L;

  bufKey = bufIv = bufAad = NULL;
  keyLen = ivLen = aadLen = 0;
  context = malloc(sizeof(crypto_ctx_t));
  if (context == NULL) {
    throwOutOfMemoryError(env, NULL);
    return 0L;
  }

  // jKey MUST NOT BE NULL;
  keyLen = (*env)->GetArrayLength(env, jKey);
  bufKey = (unsigned char *) (*env)->GetByteArrayElements(env, jKey, NULL);
  if (bufKey == NULL) {
    goto cleanup;
  }

  if (jIv != NULL) {
    ivLen = (*env)->GetArrayLength(env, jIv);
    bufIv = (unsigned char *) (*env)->GetByteArrayElements(env, jIv, NULL);
    if (bufIv == NULL) {
      goto cleanup;
    }
  }

  if (jAad != NULL) {
    aadLen = (*env)->GetArrayLength(env, jAad);
    bufAad = (unsigned char *) (*env)->GetByteArrayElements(env, jAad, NULL);
    if (bufAad == NULL) {
      goto cleanup;
    }
  }

  rv = CipherInit(context, encrypt, mech, bufKey, keyLen, bufIv, ivLen, tagLen, bufAad, aadLen);
  if (rv != 0) {
    throwUCExceptionUsingRV(env, rv);
  } else {
     result = (jlong) context;
  }

cleanup:
  if ((result == 0L) && (context != NULL)) {
    freeContext(context);
  }
  if (bufKey != NULL) {
    (*env)->ReleaseByteArrayElements(env, jKey, (jbyte *)bufKey, 0);
  }
  if (bufIv != NULL) {
    (*env)->ReleaseByteArrayElements(env, jIv, (jbyte *)bufIv, 0);
  }
  if (bufAad != NULL) {
    (*env)->ReleaseByteArrayElements(env, jAad, (jbyte *)bufAad, 0);
  }

  return result;
}

/*
 * Class:     com_oracle_security_ucrypto_NativeCipher
 * Method:    nativeUpdate
 * Signature: (JZ[BII[BI)I
 */
JNIEXPORT jint JNICALL Java_com_oracle_security_ucrypto_NativeCipher_nativeUpdate
  (JNIEnv *env, jclass jcls, jlong contextID, jboolean encrypt,
    jbyteArray jIn, jint inOfs, jint inLen, jbyteArray jOut, jint outOfs) {
  crypto_ctx_t *context;
  unsigned char *bufIn;
  unsigned char *bufOut;
  int outLen, rv = 0;

  context = (crypto_ctx_t *) contextID;
  bufIn = (unsigned char *) getBytes(env, jIn, inOfs, inLen);
  if ((*env)->ExceptionCheck(env)) {
    return 0;
  }

  outLen = (*env)->GetArrayLength(env, jOut) - outOfs;
  bufOut = calloc(outLen, sizeof(char));
  if (bufOut == NULL) {
    free(bufIn);
    throwOutOfMemoryError(env, NULL);
    return 0;
  }

  rv = CipherUpdate(context, encrypt, bufIn, 0, inLen, bufOut, 0, &outLen);
  if (rv) {
    freeContext(context);
    free(bufIn);
    free(bufOut);
    return -rv;
  } else {
    (*env)->SetByteArrayRegion(env, jOut, outOfs, outLen, (jbyte *)bufOut);
    free(bufIn);
    free(bufOut);
    return outLen;
  }
}

/*
 * Class:     com_oracle_security_ucrypto_NativeCipher
 * Method:    nativeFinal
 * Signature: (JZ[BI)I
 */
JNIEXPORT jint JNICALL Java_com_oracle_security_ucrypto_NativeCipher_nativeFinal
  (JNIEnv *env, jclass jCls, jlong contextID, jboolean encrypt,
   jbyteArray out, jint outOfs) {
  crypto_ctx_t *context;
  unsigned char *bufIn;
  unsigned char *bufOut;
  int outLen, rv = 0;
  jint rc;

  context = (crypto_ctx_t *) contextID;

  // out is null when nativeFinal() is called solely for resource clean up
  if (out == NULL) {
    // Avoid null output buffer to workaround Solaris bug21481818 (fixed in S12)
    bufOut = (unsigned char *)(&outLen);
    outLen = 0;
  } else {
    outLen = (*env)->GetArrayLength(env, out) - outOfs;
    bufOut = calloc(outLen, sizeof(char));
    if (bufOut == NULL) {
      throwOutOfMemoryError(env, NULL);
      return 0;
    }
  }
  rv = CipherFinal(context, encrypt, bufOut, 0, &outLen);
  if (rv) {
    rc = -rv;
  } else {
    if (outLen > 0) {
      (*env)->SetByteArrayRegion(env, out, outOfs, outLen, (jbyte *)bufOut);
    }
    rc = outLen;
  }
  free(context);
  if (bufOut != (unsigned char *)(&outLen)) {
    free(bufOut);
  }
  return rc;
}


/*
 * Class:     com_oracle_security_ucrypto_NativeKey
 * Method:    nativeFree
 * Signature: (JI)V
 */
JNIEXPORT void JNICALL
JavaCritical_com_oracle_security_ucrypto_NativeKey_nativeFree
  (jlong id, jint numOfComponents) {
  crypto_object_attribute_t* pKey;
  int i;

  pKey = (crypto_object_attribute_t*) id;
  for (i = 0; i < numOfComponents; i++) {
    free(pKey[i].oa_value);
  }
  free(pKey);
}

JNIEXPORT void JNICALL Java_com_oracle_security_ucrypto_NativeKey_nativeFree
  (JNIEnv *env, jclass jCls, jlong id, jint numOfComponents) {
  JavaCritical_com_oracle_security_ucrypto_NativeKey_nativeFree(id, numOfComponents);
}

/*
 * Class:     com_oracle_security_ucrypto_NativeKey_RSAPrivate
 * Method:    nativeInit
 * Signature: ([B[B)J
 */
JNIEXPORT jlong JNICALL
JavaCritical_com_oracle_security_ucrypto_NativeKey_00024RSAPrivate_nativeInit
(int modLen, jbyte* jMod, int privLen, jbyte* jPriv) {

  unsigned char *mod, *priv;
  crypto_object_attribute_t* pKey = NULL;

  pKey = calloc(2, sizeof(crypto_object_attribute_t));
  if (pKey == NULL) {
    return 0L;
  }
  mod = priv = NULL;
  mod = malloc(modLen);
  priv = malloc(privLen);
  if (mod == NULL || priv == NULL) {
    free(pKey);
    free(mod);
    free(priv);
    return 0L;
  } else {
    memcpy(mod, jMod, modLen);
    memcpy(priv, jPriv, privLen);
  }

  // NOTE: numOfComponents should be 2
  pKey[0].oa_type = SUN_CKA_MODULUS;
  pKey[0].oa_value = (char*) mod;
  pKey[0].oa_value_len = (size_t) modLen;
  pKey[1].oa_type = SUN_CKA_PRIVATE_EXPONENT;
  pKey[1].oa_value = (char*) priv;
  pKey[1].oa_value_len = (size_t) privLen;

  return (jlong) pKey;
}

JNIEXPORT jlong JNICALL
Java_com_oracle_security_ucrypto_NativeKey_00024RSAPrivate_nativeInit
  (JNIEnv *env, jclass jCls, jbyteArray jMod, jbyteArray jPriv) {

  int modLen, privLen;
  jbyte *bufMod, *bufPriv;
  crypto_object_attribute_t* pKey = NULL;

  bufMod = bufPriv = NULL;

  modLen = (*env)->GetArrayLength(env, jMod);
  bufMod = getBytes(env, jMod, 0, modLen);
  if ((*env)->ExceptionCheck(env)) goto cleanup;

  privLen = (*env)->GetArrayLength(env, jPriv);
  bufPriv = getBytes(env, jPriv, 0, privLen);
  if ((*env)->ExceptionCheck(env)) goto cleanup;

  // proceed if no error; otherwise free allocated memory
  pKey = calloc(2, sizeof(crypto_object_attribute_t));
  if (pKey == NULL) {
    throwOutOfMemoryError(env, NULL);
    goto cleanup;
  }

  // NOTE: numOfComponents should be 2
  pKey[0].oa_type = SUN_CKA_MODULUS;
  pKey[0].oa_value = (char*) bufMod;
  pKey[0].oa_value_len = (size_t) modLen;
  pKey[1].oa_type = SUN_CKA_PRIVATE_EXPONENT;
  pKey[1].oa_value = (char*) bufPriv;
  pKey[1].oa_value_len = (size_t) privLen;
  return (jlong) pKey;

cleanup:
  free(bufMod);
  free(bufPriv);

  return 0L;
}

/*
 * Class:     com_oracle_security_ucrypto_NativeKey_RSAPrivateCrt
 * Method:    nativeInit
 * Signature: ([B[B[B[B[B[B[B[B)J
 */
JNIEXPORT jlong JNICALL
JavaCritical_com_oracle_security_ucrypto_NativeKey_00024RSAPrivateCrt_nativeInit
(int modLen, jbyte* jMod, int pubLen, jbyte* jPub, int privLen, jbyte* jPriv,
 int pLen, jbyte* jP, int qLen, jbyte* jQ, int expPLen, jbyte* jExpP,
 int expQLen, jbyte* jExpQ, int crtCoeffLen, jbyte* jCrtCoeff) {

  unsigned char *mod, *pub, *priv, *p, *q, *expP, *expQ, *crtCoeff;
  crypto_object_attribute_t* pKey = NULL;

  pKey = calloc(8, sizeof(crypto_object_attribute_t));
  if (pKey == NULL) {
    return 0L;
  }
  mod = pub = priv = p = q = expP = expQ = crtCoeff = NULL;
  mod = malloc(modLen);
  pub = malloc(pubLen);
  priv = malloc(privLen);
  p = malloc(pLen);
  q = malloc(qLen);
  expP = malloc(expPLen);
  expQ = malloc(expQLen);
  crtCoeff = malloc(crtCoeffLen);
  if (mod == NULL || pub == NULL || priv == NULL || p == NULL ||
      q == NULL || expP == NULL || expQ == NULL || crtCoeff == NULL) {
    free(pKey);
    free(mod);
    free(pub);
    free(priv);
    free(p);
    free(q);
    free(expP);
    free(expQ);
    free(crtCoeff);
    return 0L;
  } else {
    memcpy(mod, jMod, modLen);
    memcpy(pub, jPub, pubLen);
    memcpy(priv, jPriv, privLen);
    memcpy(p, jP, pLen);
    memcpy(q, jQ, qLen);
    memcpy(expP, jExpP, expPLen);
    memcpy(expQ, jExpQ, expQLen);
    memcpy(crtCoeff, jCrtCoeff, crtCoeffLen);
  }

  // NOTE: numOfComponents should be 8
  pKey[0].oa_type = SUN_CKA_MODULUS;
  pKey[0].oa_value = (char*) mod;
  pKey[0].oa_value_len = (size_t) modLen;
  pKey[1].oa_type = SUN_CKA_PUBLIC_EXPONENT;
  pKey[1].oa_value = (char*) pub;
  pKey[1].oa_value_len = (size_t) pubLen;
  pKey[2].oa_type = SUN_CKA_PRIVATE_EXPONENT;
  pKey[2].oa_value = (char*) priv;
  pKey[2].oa_value_len = (size_t) privLen;
  pKey[3].oa_type = SUN_CKA_PRIME_1;
  pKey[3].oa_value = (char*) p;
  pKey[3].oa_value_len = (size_t) pLen;
  pKey[4].oa_type = SUN_CKA_PRIME_2;
  pKey[4].oa_value = (char*) q;
  pKey[4].oa_value_len = (size_t) qLen;
  pKey[5].oa_type = SUN_CKA_EXPONENT_1;
  pKey[5].oa_value = (char*) expP;
  pKey[5].oa_value_len = (size_t) expPLen;
  pKey[6].oa_type = SUN_CKA_EXPONENT_2;
  pKey[6].oa_value = (char*) expQ;
  pKey[6].oa_value_len = (size_t) expQLen;
  pKey[7].oa_type = SUN_CKA_COEFFICIENT;
  pKey[7].oa_value = (char*) crtCoeff;
  pKey[7].oa_value_len = (size_t) crtCoeffLen;

  return (jlong) pKey;
}


JNIEXPORT jlong JNICALL
Java_com_oracle_security_ucrypto_NativeKey_00024RSAPrivateCrt_nativeInit
  (JNIEnv *env, jclass jCls, jbyteArray jMod, jbyteArray jPub, jbyteArray jPriv,
   jbyteArray jP, jbyteArray jQ, jbyteArray jExpP, jbyteArray jExpQ,
   jbyteArray jCrtCoeff) {

  int modLen, pubLen, privLen, pLen, qLen, expPLen, expQLen, crtCoeffLen;
  jbyte *bufMod, *bufPub, *bufPriv, *bufP, *bufQ, *bufExpP, *bufExpQ, *bufCrtCoeff;
  crypto_object_attribute_t* pKey = NULL;

  bufMod = bufPub = bufPriv = bufP = bufQ = bufExpP = bufExpQ = bufCrtCoeff = NULL;

  modLen = (*env)->GetArrayLength(env, jMod);
  bufMod = getBytes(env, jMod, 0, modLen);
  if ((*env)->ExceptionCheck(env)) goto cleanup;

  pubLen = (*env)->GetArrayLength(env, jPub);
  bufPub = getBytes(env, jPub, 0, pubLen);
  if ((*env)->ExceptionCheck(env)) goto cleanup;

  privLen = (*env)->GetArrayLength(env, jPriv);
  bufPriv = getBytes(env, jPriv, 0, privLen);
  if ((*env)->ExceptionCheck(env)) goto cleanup;

  pLen = (*env)->GetArrayLength(env, jP);
  bufP = getBytes(env, jP, 0, pLen);
  if ((*env)->ExceptionCheck(env)) goto cleanup;

  qLen = (*env)->GetArrayLength(env, jQ);
  bufQ = getBytes(env, jQ, 0, qLen);
  if ((*env)->ExceptionCheck(env)) goto cleanup;

  expPLen = (*env)->GetArrayLength(env, jExpP);
  bufExpP = getBytes(env, jExpP, 0, expPLen);
  if ((*env)->ExceptionCheck(env)) goto cleanup;

  expQLen = (*env)->GetArrayLength(env, jExpQ);
  bufExpQ = getBytes(env, jExpQ, 0, expQLen);
  if ((*env)->ExceptionCheck(env)) goto cleanup;

  crtCoeffLen = (*env)->GetArrayLength(env, jCrtCoeff);
  bufCrtCoeff = getBytes(env, jCrtCoeff, 0, crtCoeffLen);
  if ((*env)->ExceptionCheck(env)) goto cleanup;

  // proceed if no error; otherwise free allocated memory
  pKey = calloc(8, sizeof(crypto_object_attribute_t));
  if (pKey == NULL) {
    throwOutOfMemoryError(env, NULL);
    goto cleanup;
  }

  // NOTE: numOfComponents should be 8
  pKey[0].oa_type = SUN_CKA_MODULUS;
  pKey[0].oa_value = (char*) bufMod;
  pKey[0].oa_value_len = (size_t) modLen;
  pKey[1].oa_type = SUN_CKA_PUBLIC_EXPONENT;
  pKey[1].oa_value = (char*) bufPub;
  pKey[1].oa_value_len = (size_t) pubLen;
  pKey[2].oa_type = SUN_CKA_PRIVATE_EXPONENT;
  pKey[2].oa_value = (char*) bufPriv;
  pKey[2].oa_value_len = (size_t) privLen;
  pKey[3].oa_type = SUN_CKA_PRIME_1;
  pKey[3].oa_value = (char*) bufP;
  pKey[3].oa_value_len = (size_t) pLen;
  pKey[4].oa_type = SUN_CKA_PRIME_2;
  pKey[4].oa_value = (char*) bufQ;
  pKey[4].oa_value_len = (size_t) qLen;
  pKey[5].oa_type = SUN_CKA_EXPONENT_1;
  pKey[5].oa_value = (char*) bufExpP;
  pKey[5].oa_value_len = (size_t) expPLen;
  pKey[6].oa_type = SUN_CKA_EXPONENT_2;
  pKey[6].oa_value = (char*) bufExpQ;
  pKey[6].oa_value_len = (size_t) expQLen;
  pKey[7].oa_type = SUN_CKA_COEFFICIENT;
  pKey[7].oa_value = (char*) bufCrtCoeff;
  pKey[7].oa_value_len = (size_t) crtCoeffLen;
  return (jlong) pKey;

cleanup:
  free(bufMod);
  free(bufPub);
  free(bufPriv);
  free(bufP);
  free(bufQ);
  free(bufExpP);
  free(bufExpQ);
  free(bufCrtCoeff);

  return 0L;
}

/*
 * Class:     com_oracle_security_ucrypto_NativeKey_RSAPublic
 * Method:    nativeInit
 * Signature: ([B[B)J
 */

JNIEXPORT jlong JNICALL
JavaCritical_com_oracle_security_ucrypto_NativeKey_00024RSAPublic_nativeInit
(int modLen, jbyte* jMod, int pubLen, jbyte* jPub) {
  unsigned char *mod, *pub;
  crypto_object_attribute_t* pKey = NULL;

  pKey = calloc(2, sizeof(crypto_object_attribute_t));
  if (pKey == NULL) {
    return 0L;
  }
  mod = pub = NULL;
  mod = malloc(modLen);
  pub = malloc(pubLen);
  if (mod == NULL || pub == NULL) {
    free(pKey);
    free(mod);
    free(pub);
    return 0L;
  } else {
    memcpy(mod, jMod, modLen);
    memcpy(pub, jPub, pubLen);
  }

  if (J2UC_DEBUG) {
    printf("RSAPublicKey.nativeInit: keyValue=%ld, keyLen=2\n", pKey);
    printBytes("\tmod: ", (unsigned char*) mod, modLen);
    printBytes("\tpubExp: ", (unsigned char*) pub, pubLen);
  }

  pKey[0].oa_type = SUN_CKA_MODULUS;
  pKey[0].oa_value = (char*) mod;
  pKey[0].oa_value_len = (size_t) modLen;
  pKey[1].oa_type = SUN_CKA_PUBLIC_EXPONENT;
  pKey[1].oa_value = (char*) pub;
  pKey[1].oa_value_len = (size_t) pubLen;

  return (jlong) pKey;
}

JNIEXPORT jlong JNICALL
Java_com_oracle_security_ucrypto_NativeKey_00024RSAPublic_nativeInit
(JNIEnv *env, jclass jCls, jbyteArray jMod, jbyteArray jPub) {
  int modLen, pubLen;
  jbyte *bufMod, *bufPub;
  crypto_object_attribute_t* pKey = NULL;

  bufMod = bufPub = NULL;

  modLen = (*env)->GetArrayLength(env, jMod);
  bufMod = getBytes(env, jMod, 0, modLen);
  if ((*env)->ExceptionCheck(env)) {
    return 0L;
  }

  pubLen = (*env)->GetArrayLength(env, jPub);
  bufPub = getBytes(env, jPub, 0, pubLen);
  if ((*env)->ExceptionCheck(env)) {
    free(bufMod);
    return 0L;
  }

  // proceed if no error; otherwise free allocated memory
  pKey = calloc(2, sizeof(crypto_object_attribute_t));
  if (pKey != NULL) {
    // NOTE: numOfComponents should be 2
    pKey[0].oa_type = SUN_CKA_MODULUS;
    pKey[0].oa_value = (char*) bufMod;
    pKey[0].oa_value_len = (size_t) modLen;
    pKey[1].oa_type = SUN_CKA_PUBLIC_EXPONENT;
    pKey[1].oa_value = (char*) bufPub;
    pKey[1].oa_value_len = (size_t) pubLen;
    return (jlong) pKey;
  } else {
    free(bufMod);
    free(bufPub);
    throwOutOfMemoryError(env, NULL);
    return 0L;
  }
}

////////////////////////
// NativeRSASignature
////////////////////////

int
SignatureInit(crypto_ctx_t *context, jint mechVal, jboolean sign,
              uchar_t *pKey, size_t keyLength) {
  ucrypto_mech_t mech;
  int rv = 0;

  mech = (ucrypto_mech_t) mechVal;

  if (sign) {
    rv = (*ftab->ucryptoSignInit)(context, mech, pKey, keyLength,
                                  NULL, 0);
  } else {
    rv = (*ftab->ucryptoVerifyInit)(context, mech, pKey, keyLength,
                                    NULL, 0);
  }
  if (J2UC_DEBUG) {
    printf("SignatureInit: context=%ld, mech=%d, sign=%d, keyValue=%ld, keyLength=%d\n",
           context, mech, sign, pKey, keyLength);
    printError("SignatureInit", mech, rv);
  }
  return rv;
}

/*
 * Class:     com_oracle_security_ucrypto_NativeRSASignature
 * Method:    nativeInit
 * Signature: (IZJI[B)J
 */
JNIEXPORT jlong JNICALL
JavaCritical_com_oracle_security_ucrypto_NativeRSASignature_nativeInit
(jint mech, jboolean sign, jlong jKey, jint keyLength) {
  crypto_ctx_t *context;
  int rv;
  uchar_t *pKey;

  context = malloc(sizeof(crypto_ctx_t));
  if (context != NULL) {
    pKey = (uchar_t *) jKey;
    rv = SignatureInit(context, mech, sign, pKey, (size_t)keyLength);
    if (rv) {
      freeContext(context);
      return 0L;
    }
  }
  return (jlong)context;
}

JNIEXPORT jlong JNICALL Java_com_oracle_security_ucrypto_NativeRSASignature_nativeInit
(JNIEnv *env, jclass jCls, jint mech, jboolean sign, jlong jKey, jint keyLength) {
  crypto_ctx_t *context;
  int rv = 0;
  uchar_t *pKey;

  context = malloc(sizeof(crypto_ctx_t));
  if (context == NULL) {
    throwOutOfMemoryError(env, NULL);
    return 0L;
  }

  pKey = (uchar_t *) jKey;
  rv = SignatureInit(context, mech, sign, pKey, (size_t)keyLength);
  if (rv) {
    freeContext(context);
    throwUCExceptionUsingRV(env, rv);
    return 0L;
  }

  return (jlong)context;
}

/*
 * Class:     com_oracle_security_ucrypto_NativeRSASignature
 * Method:    nativeUpdate
 * Signature: (JZ[BII)I
 */
JNIEXPORT jint JNICALL
JavaCritical_com_oracle_security_ucrypto_NativeRSASignature_nativeUpdate__JZ_3BII
(jlong pCtxt, jboolean sign, int notUsed, jbyte* jIn, jint jInOfs, jint jInLen) {
  crypto_ctx_t *context;
  int rv = 0;

  context = (crypto_ctx_t *) pCtxt;
  if (J2UC_DEBUG) {
    printf("NativeRSASignature.nativeUpdate: context=%ld, sign=%d, jIn=%ld, jInOfs=%d, jInLen=%d\n",
           context, sign, jIn, jInOfs, jInLen);
  }
  if (sign) {
    rv = (*ftab->ucryptoSignUpdate)(context, (uchar_t *) (jIn + jInOfs), (size_t) jInLen);
  } else {
    rv = (*ftab->ucryptoVerifyUpdate)(context, (uchar_t *) (jIn + jInOfs), (size_t) jInLen);
  }
  if (rv) {
    freeContext(context);
    if (J2UC_DEBUG) printError("NativeRSASignature.nativeUpdate", -1, rv);
    return -rv; // use negative value to indicate error!
  }

  return 0;
}

JNIEXPORT jint JNICALL Java_com_oracle_security_ucrypto_NativeRSASignature_nativeUpdate__JZ_3BII
(JNIEnv *env, jclass jCls, jlong pCtxt, jboolean sign, jbyteArray jIn, jint inOfs, jint inLen) {
  int rv = 0;
  jbyte* bufIn;

  bufIn = getBytes(env, jIn, inOfs, inLen);
  if ((*env)->ExceptionCheck(env)) {
    return -1; // use negative value to indicate error!
  }

  if (J2UC_DEBUG) printBytes("Update w/ data: ", (unsigned char*)bufIn, (size_t) inLen);

  rv = JavaCritical_com_oracle_security_ucrypto_NativeRSASignature_nativeUpdate__JZ_3BII
    (pCtxt, sign, inLen, bufIn, 0, inLen);

  free(bufIn);
  return rv;
}

/*
 * Class:     com_oracle_security_ucrypto_NativeRSASignature
 * Method:    nativeUpdate
 * Signature: (JZJI)I
 */
JNIEXPORT jint JNICALL
JavaCritical_com_oracle_security_ucrypto_NativeRSASignature_nativeUpdate__JZJI
(jlong pCtxt, jboolean sign, jlong inAddr, jint inLen) {

  return JavaCritical_com_oracle_security_ucrypto_NativeRSASignature_nativeUpdate__JZ_3BII
    (pCtxt, sign, inLen, (jbyte*)inAddr, 0, inLen);
}

JNIEXPORT jint JNICALL Java_com_oracle_security_ucrypto_NativeRSASignature_nativeUpdate__JZJI
(JNIEnv *env, jclass jCls, jlong pCtxt, jboolean sign, jlong inAddr, jint inLen) {

  return JavaCritical_com_oracle_security_ucrypto_NativeRSASignature_nativeUpdate__JZ_3BII
    (pCtxt, sign, inLen, (jbyte*)inAddr, 0, inLen);
}

/*
 * Class:     com_oracle_security_ucrypto_NativeRSASignature
 * Method:    nativeFinal
 * Signature: (JZ[BII)I
 */
JNIEXPORT jint JNICALL
JavaCritical_com_oracle_security_ucrypto_NativeRSASignature_nativeFinal
(jlong pCtxt, jboolean sign, int notUsed, jbyte* bufSig, jint sigOfs, jint jSigLen) {

  crypto_ctx_t *context;
  int rv = 0;
  size_t sigLength = (size_t) jSigLen;

  context = (crypto_ctx_t *) pCtxt;
  if (J2UC_DEBUG) {
      printf("NativeRSASignature.nativeFinal: context=%ld, sign=%d, bufSig=%ld, sigOfs=%d, sigLen=%d\n",
             context, sign, bufSig, sigOfs, jSigLen);
      printBytes("Before: SigBytes ", (unsigned char*) (bufSig + sigOfs), jSigLen);
  }
  if (sign) {
    rv = (*ftab->ucryptoSignFinal)(context, (uchar_t *) (bufSig + sigOfs), &sigLength);
  } else {
    rv = (*ftab->ucryptoVerifyFinal)(context, (uchar_t *) (bufSig + sigOfs), &sigLength);
  }

  freeContext(context);
  if (rv) {
    if (J2UC_DEBUG) {
      printError("NativeRSASignature.nativeFinal", -1, rv);
      if (sigLength != jSigLen) {
        printf("NativeRSASignature.nativeFinal out sig len=%d\n", sigLength);
      }
      if (sign) {
        printBytes("After: SigBytes ", (unsigned char*) (bufSig + sigOfs), jSigLen);
      }
    }
    return -rv;
  } else return 0;
}

JNIEXPORT jint JNICALL Java_com_oracle_security_ucrypto_NativeRSASignature_nativeFinal
(JNIEnv *env, jclass jCls, jlong pCtxt, jboolean sign, jbyteArray jSig, jint jSigOfs, jint jSigLen) {
  int rv = 0;
  jbyte* bufSig = NULL;

  if (jSigLen != 0) {
    bufSig = calloc(jSigLen, sizeof(char));
    if (bufSig == NULL) {
      throwOutOfMemoryError(env, NULL);
      return 0;
    }
    if (!sign) {
      // need to copy over the to-be-verified signature bytes
      (*env)->GetByteArrayRegion(env, jSig, jSigOfs, jSigLen, (jbyte *)bufSig);
    }
  }

  if (!(*env)->ExceptionCheck(env)) {
    // Frees context + converts rv to negative if error occurred
    rv = JavaCritical_com_oracle_security_ucrypto_NativeRSASignature_nativeFinal
      (pCtxt, sign, jSigLen, bufSig, 0, jSigLen);

    if (rv == 0 && sign) {
      // need to copy the generated signature bytes to the java bytearray
      (*env)->SetByteArrayRegion(env, jSig, jSigOfs, jSigLen, (jbyte *)bufSig);
    }
  } else {
    // set rv to negative to indicate error
    rv = -1;
  }

  free(bufSig);

  return rv;
}

/*
 * Class:     com_oracle_security_ucrypto_NativeRSACipher
 * Method:    nativeAtomic
 * Signature: (IZJI[BI[BII)I
 */
JNIEXPORT jint JNICALL
JavaCritical_com_oracle_security_ucrypto_NativeRSACipher_nativeAtomic
  (jint mech, jboolean encrypt, jlong keyValue, jint keyLength,
   int notUsed1, jbyte* bufIn, jint jInLen,
   int notUsed2, jbyte* bufOut, jint jOutOfs, jint jOutLen) {

  uchar_t *pKey;
  crypto_object_attribute_t* pKey2;
  int rv = 0;
  size_t outLength = (size_t) jOutLen;

  pKey = (uchar_t *) keyValue;
  if (J2UC_DEBUG) {
    printf("NativeRSACipher.nativeAtomic: mech=%d, encrypt=%d, pKey=%ld, keyLength=%d\n",
           mech, encrypt, pKey, keyLength);
    printBytes("Before: in  = ", (unsigned char*) bufIn, jInLen);
    printBytes("Before: out = ", (unsigned char*) (bufOut + jOutOfs), jOutLen);
  }

  if (encrypt) {
    rv = (*ftab->ucryptoEncrypt)((ucrypto_mech_t)mech, pKey, (size_t)keyLength,
      NULL, 0, (uchar_t *)bufIn, (size_t)jInLen,
      (uchar_t *)(bufOut + jOutOfs), &outLength);
  } else {
    rv = (*ftab->ucryptoDecrypt)((ucrypto_mech_t)mech, pKey, (size_t)keyLength,
      NULL, 0, (uchar_t *)bufIn, (size_t)jInLen,
      (uchar_t *)(bufOut + jOutOfs), &outLength);
  }
  if (J2UC_DEBUG) {
    printError("NativeRSACipher.nativeAtomic", mech, rv);
    if (outLength != jOutLen) {
      printf("NativeRSACipher.nativeAtomic out len=%d\n", outLength);
    }
    printBytes("After: ", (unsigned char*) (bufOut + jOutOfs), outLength);
  }

  if (rv) {
    return -rv;
  } else return outLength;
}

JNIEXPORT jint JNICALL Java_com_oracle_security_ucrypto_NativeRSACipher_nativeAtomic
  (JNIEnv *env, jclass jCls, jint mech, jboolean encrypt,
   jlong keyValue, jint keyLength, jbyteArray jIn, jint jInLen,
   jbyteArray jOut, jint jOutOfs, jint jOutLen) {
  int rv = 0;
  jbyte *bufIn = NULL;
  jbyte *bufOut = NULL;

  if (jInLen != 0) {
    bufIn = (*env)->GetByteArrayElements(env, jIn, NULL);
    if (bufIn == NULL) {
      return 0;
    }
  }
  bufOut = calloc(jOutLen, sizeof(jbyte));
  if (bufOut == NULL) {
    (*env)->ReleaseByteArrayElements(env, jIn, bufIn, 0);
    throwOutOfMemoryError(env, NULL);
    return 0;
  }

  // rv: output length or error code (if negative)
  rv = JavaCritical_com_oracle_security_ucrypto_NativeRSACipher_nativeAtomic
    (mech, encrypt, keyValue, keyLength, jInLen, bufIn, jInLen,
     jOutLen, bufOut, 0, jOutLen);

  if (rv > 0) {
    (*env)->SetByteArrayRegion(env, jOut, jOutOfs, rv, (jbyte *)bufOut);
  }

  if (bufIn != NULL) {
    (*env)->ReleaseByteArrayElements(env, jIn, bufIn, 0);
  }
  free(bufOut);
  return rv;
}
