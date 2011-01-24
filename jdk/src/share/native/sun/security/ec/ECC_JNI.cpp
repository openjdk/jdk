/*
 * Copyright (c) 2009, 2010, Oracle and/or its affiliates. All rights reserved.
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

#include <jni.h>
#include "impl/ecc_impl.h"

#define ILLEGAL_STATE_EXCEPTION "java/lang/IllegalStateException"
#define INVALID_ALGORITHM_PARAMETER_EXCEPTION \
        "java/security/InvalidAlgorithmParameterException"
#define INVALID_PARAMETER_EXCEPTION \
        "java/security/InvalidParameterException"
#define KEY_EXCEPTION   "java/security/KeyException"

extern "C" {

/*
 * Throws an arbitrary Java exception.
 */
void ThrowException(JNIEnv *env, char *exceptionName)
{
    jclass exceptionClazz = env->FindClass(exceptionName);
    env->ThrowNew(exceptionClazz, NULL);
}

/*
 * Deep free of the ECParams struct
 */
void FreeECParams(ECParams *ecparams, jboolean freeStruct)
{
    // Use B_FALSE to free the SECItem->data element, but not the SECItem itself
    // Use B_TRUE to free both

    SECITEM_FreeItem(&ecparams->fieldID.u.prime, B_FALSE);
    SECITEM_FreeItem(&ecparams->curve.a, B_FALSE);
    SECITEM_FreeItem(&ecparams->curve.b, B_FALSE);
    SECITEM_FreeItem(&ecparams->curve.seed, B_FALSE);
    SECITEM_FreeItem(&ecparams->base, B_FALSE);
    SECITEM_FreeItem(&ecparams->order, B_FALSE);
    SECITEM_FreeItem(&ecparams->DEREncoding, B_FALSE);
    SECITEM_FreeItem(&ecparams->curveOID, B_FALSE);
    if (freeStruct)
        free(ecparams);
}

/*
 * Class:     sun_security_ec_ECKeyPairGenerator
 * Method:    generateECKeyPair
 * Signature: (I[B[B)[J
 */
JNIEXPORT jlongArray
JNICALL Java_sun_security_ec_ECKeyPairGenerator_generateECKeyPair
  (JNIEnv *env, jclass clazz, jint keySize, jbyteArray encodedParams, jbyteArray seed)
{
    ECPrivateKey *privKey;      /* contains both public and private values */
    ECParams *ecparams = NULL;
    SECKEYECParams params_item;
    jint jSeedLength;
    jbyte* pSeedBuffer = NULL;
    jlongArray result = NULL;
    jlong* resultElements = NULL;

    // Initialize the ECParams struct
    params_item.len = env->GetArrayLength(encodedParams);
    params_item.data =
        (unsigned char *) env->GetByteArrayElements(encodedParams, 0);

    // Fill a new ECParams using the supplied OID
    if (EC_DecodeParams(&params_item, &ecparams, 0) != SECSuccess) {
        /* bad curve OID */
        ThrowException(env, (char *) INVALID_ALGORITHM_PARAMETER_EXCEPTION);
        goto cleanup;
    }

    // Copy seed from Java to native buffer
    jSeedLength = env->GetArrayLength(seed);
    pSeedBuffer = new jbyte[jSeedLength];
    env->GetByteArrayRegion(seed, 0, jSeedLength, pSeedBuffer);

    // Generate the new keypair (using the supplied seed)
    if (EC_NewKey(ecparams, &privKey, (unsigned char *) pSeedBuffer,
        jSeedLength, 0) != SECSuccess) {
        ThrowException(env, (char *) KEY_EXCEPTION);
        goto cleanup;
    }

    jboolean isCopy;
    result = env->NewLongArray(2);
    resultElements = env->GetLongArrayElements(result, &isCopy);

    resultElements[0] = (jlong) &(privKey->privateValue); // private big integer
    resultElements[1] = (jlong) &(privKey->publicValue); // encoded ec point

    // If the array is a copy then we must write back our changes
    if (isCopy == JNI_TRUE) {
        env->ReleaseLongArrayElements(result, resultElements, 0);
    }

cleanup:
    {
        if (params_item.data)
            env->ReleaseByteArrayElements(encodedParams,
                (jbyte *) params_item.data, JNI_ABORT);

        if (ecparams)
            FreeECParams(ecparams, true);

        if (privKey) {
            FreeECParams(&privKey->ecParams, false);
            SECITEM_FreeItem(&privKey->version, B_FALSE);
            // Don't free privKey->privateValue and privKey->publicValue
        }

        if (pSeedBuffer)
            delete [] pSeedBuffer;
    }

    return result;
}

/*
 * Class:     sun_security_ec_ECKeyPairGenerator
 * Method:    getEncodedBytes
 * Signature: (J)[B
 */
JNIEXPORT jbyteArray
JNICALL Java_sun_security_ec_ECKeyPairGenerator_getEncodedBytes
  (JNIEnv *env, jclass clazz, jlong hSECItem)
{
    SECItem *s = (SECItem *)hSECItem;
    jbyteArray jEncodedBytes = env->NewByteArray(s->len);

    // Copy bytes from a native SECItem buffer to Java byte array
    env->SetByteArrayRegion(jEncodedBytes, 0, s->len, (jbyte *)s->data);

    // Use B_FALSE to free only the SECItem->data
    SECITEM_FreeItem(s, B_FALSE);

    return jEncodedBytes;
}

/*
 * Class:     sun_security_ec_ECDSASignature
 * Method:    signDigest
 * Signature: ([B[B[B[B)[B
 */
JNIEXPORT jbyteArray
JNICALL Java_sun_security_ec_ECDSASignature_signDigest
  (JNIEnv *env, jclass clazz, jbyteArray digest, jbyteArray privateKey, jbyteArray encodedParams, jbyteArray seed)
{
    jbyte* pDigestBuffer = NULL;
    jint jDigestLength = env->GetArrayLength(digest);
    jbyteArray jSignedDigest = NULL;

    SECItem signature_item;
    jbyte* pSignedDigestBuffer = NULL;
    jbyteArray temp;

    jint jSeedLength = env->GetArrayLength(seed);
    jbyte* pSeedBuffer = NULL;

    // Copy digest from Java to native buffer
    pDigestBuffer = new jbyte[jDigestLength];
    env->GetByteArrayRegion(digest, 0, jDigestLength, pDigestBuffer);
    SECItem digest_item;
    digest_item.data = (unsigned char *) pDigestBuffer;
    digest_item.len = jDigestLength;

    ECPrivateKey privKey;

    // Initialize the ECParams struct
    ECParams *ecparams = NULL;
    SECKEYECParams params_item;
    params_item.len = env->GetArrayLength(encodedParams);
    params_item.data =
        (unsigned char *) env->GetByteArrayElements(encodedParams, 0);

    // Fill a new ECParams using the supplied OID
    if (EC_DecodeParams(&params_item, &ecparams, 0) != SECSuccess) {
        /* bad curve OID */
        ThrowException(env, INVALID_ALGORITHM_PARAMETER_EXCEPTION);
        goto cleanup;
    }

    // Extract private key data
    privKey.ecParams = *ecparams; // struct assignment
    privKey.privateValue.len = env->GetArrayLength(privateKey);
    privKey.privateValue.data =
        (unsigned char *) env->GetByteArrayElements(privateKey, 0);

    // Prepare a buffer for the signature (twice the key length)
    pSignedDigestBuffer = new jbyte[ecparams->order.len * 2];
    signature_item.data = (unsigned char *) pSignedDigestBuffer;
    signature_item.len = ecparams->order.len * 2;

    // Copy seed from Java to native buffer
    pSeedBuffer = new jbyte[jSeedLength];
    env->GetByteArrayRegion(seed, 0, jSeedLength, pSeedBuffer);

    // Sign the digest (using the supplied seed)
    if (ECDSA_SignDigest(&privKey, &signature_item, &digest_item,
        (unsigned char *) pSeedBuffer, jSeedLength, 0) != SECSuccess) {
        ThrowException(env, KEY_EXCEPTION);
        goto cleanup;
    }

    // Create new byte array
    temp = env->NewByteArray(signature_item.len);

    // Copy data from native buffer
    env->SetByteArrayRegion(temp, 0, signature_item.len, pSignedDigestBuffer);
    jSignedDigest = temp;

cleanup:
    {
        if (params_item.data)
            env->ReleaseByteArrayElements(encodedParams,
                (jbyte *) params_item.data, JNI_ABORT);

        if (pDigestBuffer)
            delete [] pDigestBuffer;

        if (pSignedDigestBuffer)
            delete [] pSignedDigestBuffer;

        if (pSeedBuffer)
            delete [] pSeedBuffer;

        if (ecparams)
            FreeECParams(ecparams, true);
    }

    return jSignedDigest;
}

/*
 * Class:     sun_security_ec_ECDSASignature
 * Method:    verifySignedDigest
 * Signature: ([B[B[B[B)Z
 */
JNIEXPORT jboolean
JNICALL Java_sun_security_ec_ECDSASignature_verifySignedDigest
  (JNIEnv *env, jclass clazz, jbyteArray signedDigest, jbyteArray digest, jbyteArray publicKey, jbyteArray encodedParams)
{
    jboolean isValid = false;

    // Copy signedDigest from Java to native buffer
    jbyte* pSignedDigestBuffer = NULL;
    jint jSignedDigestLength = env->GetArrayLength(signedDigest);
    pSignedDigestBuffer = new jbyte[jSignedDigestLength];
    env->GetByteArrayRegion(signedDigest, 0, jSignedDigestLength,
        pSignedDigestBuffer);
    SECItem signature_item;
    signature_item.data = (unsigned char *) pSignedDigestBuffer;
    signature_item.len = jSignedDigestLength;

    // Copy digest from Java to native buffer
    jbyte* pDigestBuffer = NULL;
    jint jDigestLength = env->GetArrayLength(digest);
    pDigestBuffer = new jbyte[jDigestLength];
    env->GetByteArrayRegion(digest, 0, jDigestLength, pDigestBuffer);
    SECItem digest_item;
    digest_item.data = (unsigned char *) pDigestBuffer;
    digest_item.len = jDigestLength;

    // Extract public key data
    ECPublicKey pubKey;
    pubKey.publicValue.data = NULL;
    ECParams *ecparams = NULL;
    SECKEYECParams params_item;

    // Initialize the ECParams struct
    params_item.len = env->GetArrayLength(encodedParams);
    params_item.data =
        (unsigned char *) env->GetByteArrayElements(encodedParams, 0);

    // Fill a new ECParams using the supplied OID
    if (EC_DecodeParams(&params_item, &ecparams, 0) != SECSuccess) {
        /* bad curve OID */
        ThrowException(env, INVALID_ALGORITHM_PARAMETER_EXCEPTION);
        goto cleanup;
    }
    pubKey.ecParams = *ecparams; // struct assignment
    pubKey.publicValue.len = env->GetArrayLength(publicKey);
    pubKey.publicValue.data =
        (unsigned char *) env->GetByteArrayElements(publicKey, 0);

    if (ECDSA_VerifyDigest(&pubKey, &signature_item, &digest_item, 0)
            != SECSuccess) {
        goto cleanup;
    }

    isValid = true;

cleanup:
    {
        if (params_item.data)
            env->ReleaseByteArrayElements(encodedParams,
                (jbyte *) params_item.data, JNI_ABORT);

        if (pubKey.publicValue.data)
            env->ReleaseByteArrayElements(publicKey,
                (jbyte *) pubKey.publicValue.data, JNI_ABORT);

        if (ecparams)
            FreeECParams(ecparams, true);

        if (pSignedDigestBuffer)
            delete [] pSignedDigestBuffer;

        if (pDigestBuffer)
            delete [] pDigestBuffer;
    }

    return isValid;
}

/*
 * Class:     sun_security_ec_ECDHKeyAgreement
 * Method:    deriveKey
 * Signature: ([B[B[B)[B
 */
JNIEXPORT jbyteArray
JNICALL Java_sun_security_ec_ECDHKeyAgreement_deriveKey
  (JNIEnv *env, jclass clazz, jbyteArray privateKey, jbyteArray publicKey, jbyteArray encodedParams)
{
    jbyteArray jSecret = NULL;

    // Extract private key value
    SECItem privateValue_item;
    privateValue_item.len = env->GetArrayLength(privateKey);
    privateValue_item.data =
            (unsigned char *) env->GetByteArrayElements(privateKey, 0);

    // Extract public key value
    SECItem publicValue_item;
    publicValue_item.len = env->GetArrayLength(publicKey);
    publicValue_item.data =
        (unsigned char *) env->GetByteArrayElements(publicKey, 0);

    // Initialize the ECParams struct
    ECParams *ecparams = NULL;
    SECKEYECParams params_item;
    params_item.len = env->GetArrayLength(encodedParams);
    params_item.data =
        (unsigned char *) env->GetByteArrayElements(encodedParams, 0);

    // Fill a new ECParams using the supplied OID
    if (EC_DecodeParams(&params_item, &ecparams, 0) != SECSuccess) {
        /* bad curve OID */
        ThrowException(env, INVALID_ALGORITHM_PARAMETER_EXCEPTION);
        goto cleanup;
    }

    // Prepare a buffer for the secret
    SECItem secret_item;
    secret_item.data = NULL;
    secret_item.len = ecparams->order.len * 2;

    if (ECDH_Derive(&publicValue_item, ecparams, &privateValue_item, B_FALSE,
        &secret_item, 0) != SECSuccess) {
        ThrowException(env, ILLEGAL_STATE_EXCEPTION);
        goto cleanup;
    }

    // Create new byte array
    jSecret = env->NewByteArray(secret_item.len);

    // Copy bytes from the SECItem buffer to a Java byte array
    env->SetByteArrayRegion(jSecret, 0, secret_item.len,
        (jbyte *)secret_item.data);

    // Free the SECItem data buffer
    SECITEM_FreeItem(&secret_item, B_FALSE);

cleanup:
    {
        if (privateValue_item.data)
            env->ReleaseByteArrayElements(privateKey,
                (jbyte *) privateValue_item.data, JNI_ABORT);

        if (publicValue_item.data)
            env->ReleaseByteArrayElements(publicKey,
                (jbyte *) publicValue_item.data, JNI_ABORT);

        if (params_item.data)
            env->ReleaseByteArrayElements(encodedParams,
                (jbyte *) params_item.data, JNI_ABORT);

        if (ecparams)
            FreeECParams(ecparams, true);
    }

    return jSecret;
}

} /* extern "C" */
