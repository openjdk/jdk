/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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
#include "com_oracle_security_ucrypto_NativeDigestMD.h"

extern void throwOutOfMemoryError(JNIEnv *env, const char *msg);
extern jbyte* getBytes(JNIEnv *env, jbyteArray bytes, int offset, int len);

///////////////////////////////////////////////////////
// SPECIAL ENTRIES FOR JVM JNI-BYPASSING OPTIMIZATION
////////////////////////////////////////////////////////
JNIEXPORT jlong JNICALL
JavaCritical_com_oracle_security_ucrypto_NativeDigestMD_nativeInit(jint mech) {
  void *pContext = NULL;

  switch (mech) {
  case com_oracle_security_ucrypto_NativeDigestMD_MECH_SHA1:
    pContext = malloc(sizeof(SHA1_CTX));
    if (pContext != NULL) {
      (*ftab->sha1Init)((SHA1_CTX *)pContext);
    }
    break;
  case com_oracle_security_ucrypto_NativeDigestMD_MECH_MD5:
    pContext = malloc(sizeof(MD5_CTX));
    if (pContext != NULL) {
      (*ftab->md5Init)((MD5_CTX *)pContext);
    }
    break;
  case com_oracle_security_ucrypto_NativeDigestMD_MECH_SHA256:
    pContext = malloc(sizeof(SHA2_CTX));
    if (pContext != NULL) {
      (*ftab->sha2Init)(SHA256, (SHA2_CTX *)pContext);
    }
    break;
  case com_oracle_security_ucrypto_NativeDigestMD_MECH_SHA384:
    pContext = malloc(sizeof(SHA2_CTX));
    if (pContext != NULL) {
      (*ftab->sha2Init)(SHA384, (SHA2_CTX *)pContext);
    }
    break;
  case com_oracle_security_ucrypto_NativeDigestMD_MECH_SHA512:
    pContext = malloc(sizeof(SHA2_CTX));
    if (pContext != NULL) {
      (*ftab->sha2Init)(SHA512, (SHA2_CTX *)pContext);
    }
    break;
  default:
    if (J2UC_DEBUG) printf("ERROR: Unsupported mech %i\n", mech);
  }
  return (jlong) pContext;
}

JNIEXPORT jint JNICALL
JavaCritical_com_oracle_security_ucrypto_NativeDigestMD_nativeUpdate
  (jint mech, jlong pContext, int notUsed, unsigned char* in, jint ofs, jint len) {
  if (mech == com_oracle_security_ucrypto_NativeDigestMD_MECH_SHA1) {
    (*ftab->sha1Update)((SHA1_CTX*)pContext, (unsigned char*)(in+ofs), len);
  } else if (mech == com_oracle_security_ucrypto_NativeDigestMD_MECH_MD5) {
    (*ftab->md5Update)((MD5_CTX*)pContext, (unsigned char*)(in+ofs), len);
  } else { // SHA-2 family
    (*ftab->sha2Update)((SHA2_CTX*)pContext, (unsigned char*)(in+ofs), len);
  }
  return 0;
}

// Do digest and free the context immediately
JNIEXPORT jint JNICALL
JavaCritical_com_oracle_security_ucrypto_NativeDigestMD_nativeDigest
  (jint mech, jlong pContext, int notUsed, unsigned char* out, jint ofs, jint digestLen) {

  if (mech == com_oracle_security_ucrypto_NativeDigestMD_MECH_SHA1) {
    (*ftab->sha1Final)((unsigned char*)(out + ofs), (SHA1_CTX *)pContext);
    free((SHA1_CTX *)pContext);
  } else if (mech == com_oracle_security_ucrypto_NativeDigestMD_MECH_MD5) {
    (*ftab->md5Final)((unsigned char*)(out + ofs), (MD5_CTX *)pContext);
    free((MD5_CTX *)pContext);
  } else { // SHA-2 family
    (*ftab->sha2Final)((unsigned char*)(out + ofs), (SHA2_CTX *)pContext);
    free((SHA2_CTX *)pContext);
  }
  return 0;
}

JNIEXPORT jlong JNICALL
JavaCritical_com_oracle_security_ucrypto_NativeDigestMD_nativeClone
  (jint mech, jlong pContext) {
  void *copy = NULL;
  size_t len = 0;

  if (mech == com_oracle_security_ucrypto_NativeDigestMD_MECH_SHA1) {
    len = sizeof(SHA1_CTX);
  } else if (mech == com_oracle_security_ucrypto_NativeDigestMD_MECH_MD5) {
    len = sizeof(MD5_CTX);
  } else { // SHA-2 family
    len = sizeof(SHA2_CTX);
  }
  copy = malloc(len);
  if (copy != NULL) {
    bcopy((void *)pContext, copy, len);
  }
  return (jlong) copy;
}

JNIEXPORT void JNICALL
JavaCritical_com_oracle_security_ucrypto_NativeDigestMD_nativeFree
  (jint mech, jlong pContext) {
  if (mech == com_oracle_security_ucrypto_NativeDigestMD_MECH_SHA1) {
    free((SHA1_CTX*) pContext);
  } else if (mech == com_oracle_security_ucrypto_NativeDigestMD_MECH_MD5) {
    free((MD5_CTX*) pContext);
  } else { // SHA-2 family
    free((SHA2_CTX*) pContext);
  }
}


/*
 * Class:     com_oracle_security_ucrypto_NativeDigestMD
 * Method:    nativeInit
 * Signature: (I)J
 */
JNIEXPORT jlong JNICALL Java_com_oracle_security_ucrypto_NativeDigestMD_nativeInit
  (JNIEnv *env, jclass jcls, jint mech) {
  jlong result = JavaCritical_com_oracle_security_ucrypto_NativeDigestMD_nativeInit(mech);
  if (result == NULL) {
     throwOutOfMemoryError(env, NULL);
  }
  return result;
}

/*
 * Class:     com_oracle_security_ucrypto_NativeDigestMD
 * Method:    nativeUpdate
 * Signature: (IJ[BII)I
 */
JNIEXPORT jint JNICALL Java_com_oracle_security_ucrypto_NativeDigestMD_nativeUpdate
  (JNIEnv *env, jclass jcls, jint mech, jlong pContext, jbyteArray jIn, jint jOfs, jint jLen) {
  unsigned char *bufIn;

  bufIn = (unsigned char *) getBytes(env, jIn, jOfs, jLen);
  if (!(*env)->ExceptionCheck(env)) {
    JavaCritical_com_oracle_security_ucrypto_NativeDigestMD_nativeUpdate(mech, pContext, jLen, bufIn, 0, jLen);
    free(bufIn);
  }
  return 0;
}

/*
 * Class:     com_oracle_security_ucrypto_NativeDigestMD
 * Method:    nativeDigest
 * Signature: (IJ[BII)I
 */
JNIEXPORT jint JNICALL Java_com_oracle_security_ucrypto_NativeDigestMD_nativeDigest
  (JNIEnv *env, jclass jcls, jint mech, jlong pContext, jbyteArray jOut, jint jOutOfs, jint digestLen) {
  unsigned char *bufOut;

  bufOut = (unsigned char *) malloc(digestLen);
  if (bufOut == NULL) {
    throwOutOfMemoryError(env, NULL);
    return 0;
  }

  JavaCritical_com_oracle_security_ucrypto_NativeDigestMD_nativeDigest(mech, pContext, digestLen, bufOut, 0, digestLen);

  (*env)->SetByteArrayRegion(env, jOut, jOutOfs, digestLen, (jbyte *) bufOut);
  free(bufOut);
  return 0;
}

/*
 * Class:     com_oracle_security_ucrypto_NativeDigestMD
 * Method:    nativeClone
 * Signature: (IJ)J
 */
JNIEXPORT jlong JNICALL Java_com_oracle_security_ucrypto_NativeDigestMD_nativeClone
  (JNIEnv *env, jclass jcls, jint mech, jlong pContext) {
  return JavaCritical_com_oracle_security_ucrypto_NativeDigestMD_nativeClone(mech, pContext);
}

/*
 * Class:     com_oracle_security_ucrypto_NativeDigestMD
 * Method:    nativeFree
 * Signature: (IJ)V
 */
JNIEXPORT void JNICALL Java_com_oracle_security_ucrypto_NativeDigestMD_nativeFree
  (JNIEnv *env, jclass jcls, jint mech, jlong pContext) {
  JavaCritical_com_oracle_security_ucrypto_NativeDigestMD_nativeFree(mech, pContext);
}

