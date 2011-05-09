/*
 * Copyright (c) 2005, 2011, Oracle and/or its affiliates. All rights reserved.
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

//=--------------------------------------------------------------------------=
// security.cpp    by Stanley Man-Kit Ho
//=--------------------------------------------------------------------------=
//

#include <jni.h>
#include <stdlib.h>
#include <windows.h>
#include <BaseTsd.h>
#include <wincrypt.h>
#include <stdio.h>


#define OID_EKU_ANY         "2.5.29.37.0"

#define CERTIFICATE_PARSING_EXCEPTION \
                            "java/security/cert/CertificateParsingException"
#define INVALID_KEY_EXCEPTION \
                            "java/security/InvalidKeyException"
#define KEY_EXCEPTION       "java/security/KeyException"
#define KEYSTORE_EXCEPTION  "java/security/KeyStoreException"
#define PROVIDER_EXCEPTION  "java/security/ProviderException"
#define SIGNATURE_EXCEPTION "java/security/SignatureException"

extern "C" {

/*
 * Throws an arbitrary Java exception.
 * The exception message is a Windows system error message.
 */
void ThrowException(JNIEnv *env, char *exceptionName, DWORD dwError)
{
    char szMessage[1024];
    szMessage[0] = '\0';

    FormatMessage(FORMAT_MESSAGE_FROM_SYSTEM, NULL, dwError, NULL, szMessage,
        1024, NULL);

    jclass exceptionClazz = env->FindClass(exceptionName);
    env->ThrowNew(exceptionClazz, szMessage);
}


/*
 * Maps the name of a hash algorithm to an algorithm identifier.
 */
ALG_ID MapHashAlgorithm(JNIEnv *env, jstring jHashAlgorithm) {

    const char* pszHashAlgorithm = NULL;
    ALG_ID algId = 0;

    pszHashAlgorithm = env->GetStringUTFChars(jHashAlgorithm, NULL);

    if ((strcmp("SHA", pszHashAlgorithm) == 0) ||
        (strcmp("SHA1", pszHashAlgorithm) == 0) ||
        (strcmp("SHA-1", pszHashAlgorithm) == 0)) {

        algId = CALG_SHA1;
    } else if (strcmp("SHA1+MD5", pszHashAlgorithm) == 0) {
        algId = CALG_SSL3_SHAMD5; // a 36-byte concatenation of SHA-1 and MD5
    } else if (strcmp("SHA-256", pszHashAlgorithm) == 0) {
        algId = CALG_SHA_256;
    } else if (strcmp("SHA-384", pszHashAlgorithm) == 0) {
        algId = CALG_SHA_384;
    } else if (strcmp("SHA-512", pszHashAlgorithm) == 0) {
        algId = CALG_SHA_512;
    } else if (strcmp("MD5", pszHashAlgorithm) == 0) {
        algId = CALG_MD5;
    } else if (strcmp("MD2", pszHashAlgorithm) == 0) {
        algId = CALG_MD2;
    }

    if (pszHashAlgorithm)
        env->ReleaseStringUTFChars(jHashAlgorithm, pszHashAlgorithm);

   return algId;
}


/*
 * Returns a certificate chain context given a certificate context and key
 * usage identifier.
 */
bool GetCertificateChain(LPSTR lpszKeyUsageIdentifier, PCCERT_CONTEXT pCertContext, PCCERT_CHAIN_CONTEXT* ppChainContext)
{
    CERT_ENHKEY_USAGE        EnhkeyUsage;
    CERT_USAGE_MATCH         CertUsage;
    CERT_CHAIN_PARA          ChainPara;
    DWORD                    dwFlags = 0;
    LPSTR                    szUsageIdentifierArray[1];

    szUsageIdentifierArray[0] = lpszKeyUsageIdentifier;
    EnhkeyUsage.cUsageIdentifier = 1;
    EnhkeyUsage.rgpszUsageIdentifier = szUsageIdentifierArray;
    CertUsage.dwType = USAGE_MATCH_TYPE_AND;
    CertUsage.Usage  = EnhkeyUsage;
    ChainPara.cbSize = sizeof(CERT_CHAIN_PARA);
    ChainPara.RequestedUsage=CertUsage;

    // Build a chain using CertGetCertificateChain
    // and the certificate retrieved.
    return (::CertGetCertificateChain(NULL,     // use the default chain engine
                pCertContext,   // pointer to the end certificate
                NULL,           // use the default time
                NULL,           // search no additional stores
                &ChainPara,     // use AND logic and enhanced key usage
                                //  as indicated in the ChainPara
                                //  data structure
                dwFlags,
                NULL,           // currently reserved
                ppChainContext) == TRUE);       // return a pointer to the chain created
}


/////////////////////////////////////////////////////////////////////////////
//

/*
 * Class:     sun_security_mscapi_PRNG
 * Method:    generateSeed
 * Signature: (I[B)[B
 */
JNIEXPORT jbyteArray JNICALL Java_sun_security_mscapi_PRNG_generateSeed
  (JNIEnv *env, jclass clazz, jint length, jbyteArray seed)
{

    HCRYPTPROV hCryptProv = NULL;
    BYTE*      pbData = NULL;
    jbyte*     reseedBytes = NULL;
    jbyte*     seedBytes = NULL;
    jbyteArray result = NULL;

    __try
    {
        //  Acquire a CSP context.
        if(::CryptAcquireContext(
           &hCryptProv,
           NULL,
           NULL,
           PROV_RSA_FULL,
           CRYPT_VERIFYCONTEXT) == FALSE)
        {
            ThrowException(env, PROVIDER_EXCEPTION, GetLastError());
            __leave;
        }

        /*
         * If length is negative then use the supplied seed to re-seed the
         * generator and return null.
         * If length is non-zero then generate a new seed according to the
         * requested length and return the new seed.
         * If length is zero then overwrite the supplied seed with a new
         * seed of the same length and return the seed.
         */
        if (length < 0) {
            length = env->GetArrayLength(seed);
            reseedBytes = env->GetByteArrayElements(seed, 0);

            if (::CryptGenRandom(
                hCryptProv,
                length,
                (BYTE *) reseedBytes) == FALSE) {

                ThrowException(env, PROVIDER_EXCEPTION, GetLastError());
                __leave;
            }

            result = NULL;

        } else if (length > 0) {

            pbData = new BYTE[length];

            if (::CryptGenRandom(
                hCryptProv,
                length,
                pbData) == FALSE) {

                ThrowException(env, PROVIDER_EXCEPTION, GetLastError());
                __leave;
            }

            result = env->NewByteArray(length);
            env->SetByteArrayRegion(result, 0, length, (jbyte*) pbData);

        } else { // length == 0

            length = env->GetArrayLength(seed);
            seedBytes = env->GetByteArrayElements(seed, 0);

            if (::CryptGenRandom(
                hCryptProv,
                length,
                (BYTE *) seedBytes) == FALSE) {

                ThrowException(env, PROVIDER_EXCEPTION, GetLastError());
                __leave;
            }

            result = seed; // seed will be updated when seedBytes gets released
        }
    }
    __finally
    {
        //--------------------------------------------------------------------
        // Clean up.

        if (reseedBytes)
            env->ReleaseByteArrayElements(seed, reseedBytes, JNI_ABORT);

        if (pbData)
            delete [] pbData;

        if (seedBytes)
            env->ReleaseByteArrayElements(seed, seedBytes, 0); // update orig

        if (hCryptProv)
            ::CryptReleaseContext(hCryptProv, 0);
    }

    return result;
}


/*
 * Class:     sun_security_mscapi_KeyStore
 * Method:    loadKeysOrCertificateChains
 * Signature: (Ljava/lang/String;Ljava/util/Collection;)V
 */
JNIEXPORT void JNICALL Java_sun_security_mscapi_KeyStore_loadKeysOrCertificateChains
  (JNIEnv *env, jobject obj, jstring jCertStoreName, jobject jCollections)
{
    /**
     * Certificate in cert store has enhanced key usage extension
     * property (or EKU property) that is not part of the certificate itself. To determine
     * if the certificate should be returned, both the enhanced key usage in certificate
     * extension block and the extension property stored along with the certificate in
     * certificate store should be examined. Otherwise, we won't be able to determine
     * the proper key usage from the Java side because the information is not stored as
     * part of the encoded certificate.
     */

    const char* pszCertStoreName = NULL;
    HCERTSTORE hCertStore = NULL;
    PCCERT_CONTEXT pCertContext = NULL;
    char* pszNameString = NULL; // certificate's friendly name
    DWORD cchNameString = 0;


    __try
    {
        // Open a system certificate store.
        pszCertStoreName = env->GetStringUTFChars(jCertStoreName, NULL);
        if ((hCertStore = ::CertOpenSystemStore(NULL, pszCertStoreName))
            == NULL) {

            ThrowException(env, KEYSTORE_EXCEPTION, GetLastError());
            __leave;
        }

        // Determine clazz and method ID to generate certificate
        jclass clazzArrayList = env->FindClass("java/util/ArrayList");

        jmethodID mNewArrayList = env->GetMethodID(clazzArrayList, "<init>", "()V");

        jmethodID mGenCert = env->GetMethodID(env->GetObjectClass(obj),
                                              "generateCertificate",
                                              "([BLjava/util/Collection;)V");

        // Determine method ID to generate certificate chain
        jmethodID mGenCertChain = env->GetMethodID(env->GetObjectClass(obj),
                                                   "generateCertificateChain",
                                                   "(Ljava/lang/String;Ljava/util/Collection;Ljava/util/Collection;)V");

        // Determine method ID to generate RSA certificate chain
        jmethodID mGenRSAKeyAndCertChain = env->GetMethodID(env->GetObjectClass(obj),
                                                   "generateRSAKeyAndCertificateChain",
                                                   "(Ljava/lang/String;JJILjava/util/Collection;Ljava/util/Collection;)V");

        // Use CertEnumCertificatesInStore to get the certificates
        // from the open store. pCertContext must be reset to
        // NULL to retrieve the first certificate in the store.
        while (pCertContext = ::CertEnumCertificatesInStore(hCertStore, pCertContext))
        {
            // Check if private key available - client authentication certificate
            // must have private key available.
            HCRYPTPROV hCryptProv = NULL;
            DWORD dwKeySpec = 0;
            HCRYPTKEY hUserKey = NULL;
            BOOL bCallerFreeProv = FALSE;
            BOOL bHasNoPrivateKey = FALSE;
            DWORD dwPublicKeyLength = 0;

            if (::CryptAcquireCertificatePrivateKey(pCertContext, NULL, NULL,
                                                    &hCryptProv, &dwKeySpec, &bCallerFreeProv) == FALSE)
            {
                bHasNoPrivateKey = TRUE;

            } else {
                // Private key is available

            BOOL bGetUserKey = ::CryptGetUserKey(hCryptProv, dwKeySpec, &hUserKey);

            // Skip certificate if cannot find private key
            if (bGetUserKey == FALSE)
            {
                if (bCallerFreeProv)
                    ::CryptReleaseContext(hCryptProv, NULL);

                continue;
            }

            // Set cipher mode to ECB
            DWORD dwCipherMode = CRYPT_MODE_ECB;
            ::CryptSetKeyParam(hUserKey, KP_MODE, (BYTE*)&dwCipherMode, NULL);


            // If the private key is present in smart card, we may not be able to
            // determine the key length by using the private key handle. However,
            // since public/private key pairs must have the same length, we could
            // determine the key length of the private key by using the public key
            // in the certificate.
            dwPublicKeyLength = ::CertGetPublicKeyLength(X509_ASN_ENCODING | PKCS_7_ASN_ENCODING,
                                                               &(pCertContext->pCertInfo->SubjectPublicKeyInfo));

}
            PCCERT_CHAIN_CONTEXT pCertChainContext = NULL;

            // Build certificate chain by using system certificate store.
            // Add cert chain into collection for any key usage.
            //
            if (GetCertificateChain(OID_EKU_ANY, pCertContext,
                &pCertChainContext))
            {

                for (unsigned int i=0; i < pCertChainContext->cChain; i++)
                {
                    // Found cert chain
                    PCERT_SIMPLE_CHAIN rgpChain =
                        pCertChainContext->rgpChain[i];

                    // Create ArrayList to store certs in each chain
                    jobject jArrayList =
                        env->NewObject(clazzArrayList, mNewArrayList);

                    for (unsigned int j=0; j < rgpChain->cElement; j++)
                    {
                        PCERT_CHAIN_ELEMENT rgpElement =
                            rgpChain->rgpElement[j];
                        PCCERT_CONTEXT pc = rgpElement->pCertContext;

                        // Retrieve the friendly name of the first certificate
                        // in the chain
                        if (j == 0) {

                            // If the cert's name cannot be retrieved then
                            // pszNameString remains set to NULL.
                            // (An alias name will be generated automatically
                            // when storing this cert in the keystore.)

                            // Get length of friendly name
                            if ((cchNameString = CertGetNameString(pc,
                                CERT_NAME_FRIENDLY_DISPLAY_TYPE, 0, NULL,
                                NULL, 0)) > 1) {

                                // Found friendly name
                                pszNameString = new char[cchNameString];
                                CertGetNameString(pc,
                                    CERT_NAME_FRIENDLY_DISPLAY_TYPE, 0, NULL,
                                    pszNameString, cchNameString);
                            }
                        }

                        BYTE* pbCertEncoded = pc->pbCertEncoded;
                        DWORD cbCertEncoded = pc->cbCertEncoded;

                        // Allocate and populate byte array
                        jbyteArray byteArray = env->NewByteArray(cbCertEncoded);
                        env->SetByteArrayRegion(byteArray, 0, cbCertEncoded,
                            (jbyte*) pbCertEncoded);

                        // Generate certificate from byte array and store into
                        // cert collection
                        env->CallVoidMethod(obj, mGenCert, byteArray, jArrayList);
                    }
                    if (bHasNoPrivateKey)
                    {
                        // Generate certificate chain and store into cert chain
                        // collection
                        env->CallVoidMethod(obj, mGenCertChain,
                            env->NewStringUTF(pszNameString),
                            jArrayList, jCollections);
                    }
                    else
                    {
                    // Determine key type: RSA or DSA
                    DWORD dwData = CALG_RSA_KEYX;
                    DWORD dwSize = sizeof(DWORD);
                    ::CryptGetKeyParam(hUserKey, KP_ALGID, (BYTE*)&dwData,
                        &dwSize, NULL);

                    if ((dwData & ALG_TYPE_RSA) == ALG_TYPE_RSA)
                    {
                        // Generate RSA certificate chain and store into cert
                        // chain collection
                        env->CallVoidMethod(obj, mGenRSAKeyAndCertChain,
                            env->NewStringUTF(pszNameString),
                            (jlong) hCryptProv, (jlong) hUserKey,
                            dwPublicKeyLength, jArrayList, jCollections);
                    }
}
                }

                // Free cert chain
                if (pCertChainContext)
                    ::CertFreeCertificateChain(pCertChainContext);
            }
        }
    }
    __finally
    {
        if (hCertStore)
            ::CertCloseStore(hCertStore, 0);

        if (pszCertStoreName)
            env->ReleaseStringUTFChars(jCertStoreName, pszCertStoreName);

        if (pszNameString)
            delete [] pszNameString;
    }
}


/*
 * Class:     sun_security_mscapi_Key
 * Method:    cleanUp
 * Signature: (JJ)V
 */
JNIEXPORT void JNICALL Java_sun_security_mscapi_Key_cleanUp
  (JNIEnv *env, jclass clazz, jlong hCryptProv, jlong hCryptKey)
{
    if (hCryptKey != NULL)
        ::CryptDestroyKey((HCRYPTKEY) hCryptKey);

    if (hCryptProv != NULL)
        ::CryptReleaseContext((HCRYPTPROV) hCryptProv, NULL);
}


/*
 * Class:     sun_security_mscapi_RSASignature
 * Method:    signHash
 * Signature: (Z[BILjava/lang/String;JJ)[B
 */
JNIEXPORT jbyteArray JNICALL Java_sun_security_mscapi_RSASignature_signHash
  (JNIEnv *env, jclass clazz, jboolean noHashOID, jbyteArray jHash,
        jint jHashSize, jstring jHashAlgorithm, jlong hCryptProv,
        jlong hCryptKey)
{
    HCRYPTHASH hHash = NULL;
    jbyte* pHashBuffer = NULL;
    jbyte* pSignedHashBuffer = NULL;
    jbyteArray jSignedHash = NULL;
    HCRYPTPROV hCryptProvAlt = NULL;

    __try
    {
        // Map hash algorithm
        ALG_ID algId = MapHashAlgorithm(env, jHashAlgorithm);

        // Acquire a hash object handle.
        if (::CryptCreateHash(HCRYPTPROV(hCryptProv), algId, 0, 0, &hHash) == FALSE)
        {
            // Failover to using the PROV_RSA_AES CSP

            DWORD cbData = 256;
            BYTE pbData[256];
            pbData[0] = '\0';

            // Get name of the key container
            ::CryptGetProvParam((HCRYPTPROV)hCryptProv, PP_CONTAINER,
                (BYTE *)pbData, &cbData, 0);

            // Acquire an alternative CSP handle
            if (::CryptAcquireContext(&hCryptProvAlt, LPCSTR(pbData), NULL,
                PROV_RSA_AES, 0) == FALSE)
            {

                ThrowException(env, SIGNATURE_EXCEPTION, GetLastError());
                __leave;
            }

            // Acquire a hash object handle.
            if (::CryptCreateHash(HCRYPTPROV(hCryptProvAlt), algId, 0, 0,
                &hHash) == FALSE)
            {
                ThrowException(env, SIGNATURE_EXCEPTION, GetLastError());
                __leave;
            }
        }

        // Copy hash from Java to native buffer
        pHashBuffer = new jbyte[jHashSize];
        env->GetByteArrayRegion(jHash, 0, jHashSize, pHashBuffer);

        // Set hash value in the hash object
        if (::CryptSetHashParam(hHash, HP_HASHVAL, (BYTE*)pHashBuffer, NULL) == FALSE)
        {
            ThrowException(env, SIGNATURE_EXCEPTION, GetLastError());
            __leave;
        }

        // Determine key spec.
        DWORD dwKeySpec = AT_SIGNATURE;
        ALG_ID dwAlgId;
        DWORD dwAlgIdLen = sizeof(ALG_ID);

        if (! ::CryptGetKeyParam((HCRYPTKEY) hCryptKey, KP_ALGID, (BYTE*)&dwAlgId, &dwAlgIdLen, 0)) {
            ThrowException(env, SIGNATURE_EXCEPTION, GetLastError());
            __leave;

        }
        if (CALG_RSA_KEYX == dwAlgId) {
            dwKeySpec = AT_KEYEXCHANGE;
        }

        // Determine size of buffer
        DWORD dwBufLen = 0;
        DWORD dwFlags = 0;

        if (noHashOID == JNI_TRUE) {
            dwFlags = CRYPT_NOHASHOID; // omit hash OID in NONEwithRSA signature
        }

        if (::CryptSignHash(hHash, dwKeySpec, NULL, dwFlags, NULL, &dwBufLen) == FALSE)
        {
            ThrowException(env, SIGNATURE_EXCEPTION, GetLastError());
            __leave;
        }

        pSignedHashBuffer = new jbyte[dwBufLen];
        if (::CryptSignHash(hHash, dwKeySpec, NULL, dwFlags, (BYTE*)pSignedHashBuffer, &dwBufLen) == FALSE)
        {
            ThrowException(env, SIGNATURE_EXCEPTION, GetLastError());
            __leave;
        }

        // Create new byte array
        jbyteArray temp = env->NewByteArray(dwBufLen);

        // Copy data from native buffer
        env->SetByteArrayRegion(temp, 0, dwBufLen, pSignedHashBuffer);

        jSignedHash = temp;
    }
    __finally
    {
        if (hCryptProvAlt)
            ::CryptReleaseContext(hCryptProvAlt, 0);

        if (pSignedHashBuffer)
            delete [] pSignedHashBuffer;

        if (pHashBuffer)
            delete [] pHashBuffer;

        if (hHash)
            ::CryptDestroyHash(hHash);
    }

    return jSignedHash;
}

/*
 * Class:     sun_security_mscapi_RSASignature
 * Method:    verifySignedHash
 * Signature: ([BIL/java/lang/String;[BIJJ)Z
 */
JNIEXPORT jboolean JNICALL Java_sun_security_mscapi_RSASignature_verifySignedHash
  (JNIEnv *env, jclass clazz, jbyteArray jHash, jint jHashSize,
        jstring jHashAlgorithm, jbyteArray jSignedHash, jint jSignedHashSize,
        jlong hCryptProv, jlong hCryptKey)
{
    HCRYPTHASH hHash = NULL;
    jbyte* pHashBuffer = NULL;
    jbyte* pSignedHashBuffer = NULL;
    DWORD dwSignedHashBufferLen = jSignedHashSize;
    jboolean result = JNI_FALSE;
    HCRYPTPROV hCryptProvAlt = NULL;

    __try
    {
        // Map hash algorithm
        ALG_ID algId = MapHashAlgorithm(env, jHashAlgorithm);

        // Acquire a hash object handle.
        if (::CryptCreateHash(HCRYPTPROV(hCryptProv), algId, 0, 0, &hHash)
            == FALSE)
        {
            // Failover to using the PROV_RSA_AES CSP

            DWORD cbData = 256;
            BYTE pbData[256];
            pbData[0] = '\0';

            // Get name of the key container
            ::CryptGetProvParam((HCRYPTPROV)hCryptProv, PP_CONTAINER,
                (BYTE *)pbData, &cbData, 0);

            // Acquire an alternative CSP handle
            if (::CryptAcquireContext(&hCryptProvAlt, LPCSTR(pbData), NULL,
                PROV_RSA_AES, 0) == FALSE)
            {

                ThrowException(env, SIGNATURE_EXCEPTION, GetLastError());
                __leave;
            }

            // Acquire a hash object handle.
            if (::CryptCreateHash(HCRYPTPROV(hCryptProvAlt), algId, 0, 0,
                &hHash) == FALSE)
            {
                ThrowException(env, SIGNATURE_EXCEPTION, GetLastError());
                __leave;
            }
        }

        // Copy hash and signedHash from Java to native buffer
        pHashBuffer = new jbyte[jHashSize];
        env->GetByteArrayRegion(jHash, 0, jHashSize, pHashBuffer);
        pSignedHashBuffer = new jbyte[jSignedHashSize];
        env->GetByteArrayRegion(jSignedHash, 0, jSignedHashSize,
            pSignedHashBuffer);

        // Set hash value in the hash object
        if (::CryptSetHashParam(hHash, HP_HASHVAL, (BYTE*) pHashBuffer, NULL)
            == FALSE)
        {
            ThrowException(env, SIGNATURE_EXCEPTION, GetLastError());
            __leave;
        }

        // For RSA, the hash encryption algorithm is normally the same as the
        // public key algorithm, so AT_SIGNATURE is used.

        // Verify the signature
        if (::CryptVerifySignatureA(hHash, (BYTE *) pSignedHashBuffer,
            dwSignedHashBufferLen, (HCRYPTKEY) hCryptKey, NULL, 0) == TRUE)
        {
            result = JNI_TRUE;
        }
    }

    __finally
    {
        if (hCryptProvAlt)
            ::CryptReleaseContext(hCryptProvAlt, 0);

        if (pSignedHashBuffer)
            delete [] pSignedHashBuffer;

        if (pHashBuffer)
            delete [] pHashBuffer;

        if (hHash)
            ::CryptDestroyHash(hHash);
    }

    return result;
}

/*
 * Class:     sun_security_mscapi_RSAKeyPairGenerator
 * Method:    generateRSAKeyPair
 * Signature: (ILjava/lang/String;)Lsun/security/mscapi/RSAKeyPair;
 */
JNIEXPORT jobject JNICALL Java_sun_security_mscapi_RSAKeyPairGenerator_generateRSAKeyPair
  (JNIEnv *env, jclass clazz, jint keySize, jstring keyContainerName)
{
    HCRYPTPROV hCryptProv = NULL;
    HCRYPTKEY hKeyPair;
    DWORD dwFlags = (keySize << 16) | CRYPT_EXPORTABLE;
    jobject keypair = NULL;
    const char* pszKeyContainerName = NULL; // UUID

    __try
    {
        pszKeyContainerName = env->GetStringUTFChars(keyContainerName, NULL);

        // Acquire a CSP context (create a new key container).
        // Prefer a PROV_RSA_AES CSP, when available, due to its support
        // for SHA-2-based signatures.
        if (::CryptAcquireContext(
            &hCryptProv,
            pszKeyContainerName,
            NULL,
            PROV_RSA_AES,
            CRYPT_NEWKEYSET) == FALSE)
        {
            // Failover to using the default CSP (PROV_RSA_FULL)

            if (::CryptAcquireContext(
                &hCryptProv,
                pszKeyContainerName,
                NULL,
                PROV_RSA_FULL,
                CRYPT_NEWKEYSET) == FALSE)
            {
                ThrowException(env, KEY_EXCEPTION, GetLastError());
                __leave;
            }
        }

        // Generate an RSA keypair
        if(::CryptGenKey(
           hCryptProv,
           AT_KEYEXCHANGE,
           dwFlags,
           &hKeyPair) == FALSE)
        {
            ThrowException(env, KEY_EXCEPTION, GetLastError());
            __leave;
        }

        // Get the method ID for the RSAKeyPair constructor
        jclass clazzRSAKeyPair =
            env->FindClass("sun/security/mscapi/RSAKeyPair");

        jmethodID mNewRSAKeyPair =
            env->GetMethodID(clazzRSAKeyPair, "<init>", "(JJI)V");

        // Create a new RSA keypair
        keypair = env->NewObject(clazzRSAKeyPair, mNewRSAKeyPair,
            (jlong) hCryptProv, (jlong) hKeyPair, keySize);

    }
    __finally
    {
        //--------------------------------------------------------------------
        // Clean up.

        if (pszKeyContainerName)
            env->ReleaseStringUTFChars(keyContainerName, pszKeyContainerName);
    }

    return keypair;
}

/*
 * Class:     sun_security_mscapi_Key
 * Method:    getContainerName
 * Signature: (J)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_sun_security_mscapi_Key_getContainerName
  (JNIEnv *env, jclass jclazz, jlong hCryptProv)
{
    DWORD cbData = 256;
    BYTE pbData[256];
    pbData[0] = '\0';

    ::CryptGetProvParam(
        (HCRYPTPROV)hCryptProv,
        PP_CONTAINER,
        (BYTE *)pbData,
        &cbData,
        0);

    return env->NewStringUTF((const char*)pbData);
}

/*
 * Class:     sun_security_mscapi_Key
 * Method:    getKeyType
 * Signature: (J)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_sun_security_mscapi_Key_getKeyType
  (JNIEnv *env, jclass jclazz, jlong hCryptKey)
{
    ALG_ID dwAlgId;
    DWORD dwAlgIdLen = sizeof(ALG_ID);

    if (::CryptGetKeyParam((HCRYPTKEY) hCryptKey, KP_ALGID, (BYTE*)&dwAlgId, &dwAlgIdLen, 0)) {

        if (CALG_RSA_SIGN == dwAlgId) {
            return env->NewStringUTF("Signature");

        } else if (CALG_RSA_KEYX == dwAlgId) {
            return env->NewStringUTF("Exchange");

        } else {
            char buffer[64];
            if (sprintf(buffer, "%lu", dwAlgId)) {
                return env->NewStringUTF(buffer);
            }
        }
    }

    return env->NewStringUTF("<Unknown>");
}

/*
 * Class:     sun_security_mscapi_KeyStore
 * Method:    storeCertificate
 * Signature: (Ljava/lang/String;Ljava/lang/String;[BIJJ)V
 */
JNIEXPORT void JNICALL Java_sun_security_mscapi_KeyStore_storeCertificate
  (JNIEnv *env, jobject obj, jstring jCertStoreName, jstring jCertAliasName,
        jbyteArray jCertEncoding, jint jCertEncodingSize, jlong hCryptProv,
        jlong hCryptKey)
{
    const char* pszCertStoreName = NULL;
    HCERTSTORE hCertStore = NULL;
    PCCERT_CONTEXT pCertContext = NULL;
    PWCHAR pszCertAliasName = NULL;
    jbyte* pbCertEncoding = NULL;
    const jchar* jCertAliasChars = NULL;
    const char* pszContainerName = NULL;
    const char* pszProviderName = NULL;
    WCHAR * pwszContainerName = NULL;
    WCHAR * pwszProviderName = NULL;

    __try
    {
        // Open a system certificate store.
        pszCertStoreName = env->GetStringUTFChars(jCertStoreName, NULL);
        if ((hCertStore = ::CertOpenSystemStore(NULL, pszCertStoreName)) == NULL) {
            ThrowException(env, KEYSTORE_EXCEPTION, GetLastError());
            __leave;
        }

        // Copy encoding from Java to native buffer
        pbCertEncoding = new jbyte[jCertEncodingSize];
        env->GetByteArrayRegion(jCertEncoding, 0, jCertEncodingSize, pbCertEncoding);

        // Create a certificate context from the encoded cert
        if (!(pCertContext = ::CertCreateCertificateContext(X509_ASN_ENCODING,
            (BYTE*) pbCertEncoding, jCertEncodingSize))) {

            ThrowException(env, CERTIFICATE_PARSING_EXCEPTION, GetLastError());
            __leave;
        }

        // Set the certificate's friendly name
        int size = env->GetStringLength(jCertAliasName);
        pszCertAliasName = new WCHAR[size + 1];

        jCertAliasChars = env->GetStringChars(jCertAliasName, NULL);
        memcpy(pszCertAliasName, jCertAliasChars, size * sizeof(WCHAR));
        pszCertAliasName[size] = 0; // append the string terminator

        CRYPT_DATA_BLOB friendlyName = {
            sizeof(WCHAR) * (size + 1),
            (BYTE *) pszCertAliasName
        };

        env->ReleaseStringChars(jCertAliasName, jCertAliasChars);

        if (! ::CertSetCertificateContextProperty(pCertContext,
            CERT_FRIENDLY_NAME_PROP_ID, 0, &friendlyName)) {

            ThrowException(env, KEYSTORE_EXCEPTION, GetLastError());
            __leave;
        }

        // Attach the certificate's private key (if supplied)
        if (hCryptProv != 0 && hCryptKey != 0) {

            CRYPT_KEY_PROV_INFO keyProviderInfo;
            DWORD dwDataLen;

            // Get the name of the key container
            if (! ::CryptGetProvParam(
                (HCRYPTPROV) hCryptProv,
                PP_CONTAINER,
                NULL,
                &dwDataLen,
                0)) {

                ThrowException(env, KEYSTORE_EXCEPTION, GetLastError());
                __leave;
            }

            pszContainerName = new char[dwDataLen];

            if (! ::CryptGetProvParam(
                (HCRYPTPROV) hCryptProv,
                PP_CONTAINER,
                (BYTE *) pszContainerName,
                &dwDataLen,
                0)) {

                ThrowException(env, KEYSTORE_EXCEPTION, GetLastError());
                __leave;
            }

            // Convert to a wide char string
            pwszContainerName = new WCHAR[dwDataLen];

            if (mbstowcs(pwszContainerName, pszContainerName, dwDataLen) == 0) {
                ThrowException(env, KEYSTORE_EXCEPTION, GetLastError());
                __leave;
            }

            // Set the name of the key container
            keyProviderInfo.pwszContainerName = pwszContainerName;


            // Get the name of the provider
            if (! ::CryptGetProvParam(
                (HCRYPTPROV) hCryptProv,
                PP_NAME,
                NULL,
                &dwDataLen,
                0)) {

                ThrowException(env, KEYSTORE_EXCEPTION, GetLastError());
                __leave;
            }

            pszProviderName = new char[dwDataLen];

            if (! ::CryptGetProvParam(
                (HCRYPTPROV) hCryptProv,
                PP_NAME,
                (BYTE *) pszProviderName,
                &dwDataLen,
                0)) {

                ThrowException(env, KEYSTORE_EXCEPTION, GetLastError());
                __leave;
            }

            // Convert to a wide char string
            pwszProviderName = new WCHAR[dwDataLen];

            if (mbstowcs(pwszProviderName, pszProviderName, dwDataLen) == 0) {
                ThrowException(env, KEYSTORE_EXCEPTION, GetLastError());
                __leave;
            }

            // Set the name of the provider
            keyProviderInfo.pwszProvName = pwszProviderName;

            // Get and set the type of the provider
            if (! ::CryptGetProvParam(
                (HCRYPTPROV) hCryptProv,
                PP_PROVTYPE,
                (LPBYTE) &keyProviderInfo.dwProvType,
                &dwDataLen,
                0)) {

                ThrowException(env, KEYSTORE_EXCEPTION, GetLastError());
                __leave;
            }

            // Set no provider flags
            keyProviderInfo.dwFlags = 0;

            // Set no provider parameters
            keyProviderInfo.cProvParam = 0;
            keyProviderInfo.rgProvParam = NULL;

            // Get the key's algorithm ID
            if (! ::CryptGetKeyParam(
                (HCRYPTKEY) hCryptKey,
                KP_ALGID,
                (LPBYTE) &keyProviderInfo.dwKeySpec,
                &dwDataLen,
                0)) {

                ThrowException(env, KEYSTORE_EXCEPTION, GetLastError());
                __leave;
            }
            // Set the key spec (using the algorithm ID).
            switch (keyProviderInfo.dwKeySpec) {
            case CALG_RSA_KEYX:
            case CALG_DH_SF:
                keyProviderInfo.dwKeySpec = AT_KEYEXCHANGE;
                break;

            case CALG_RSA_SIGN:
            case CALG_DSS_SIGN:
                keyProviderInfo.dwKeySpec = AT_SIGNATURE;
                break;

            default:
                ThrowException(env, KEYSTORE_EXCEPTION, NTE_BAD_ALGID);
                __leave;
            }

            if (! ::CertSetCertificateContextProperty(pCertContext,
                CERT_KEY_PROV_INFO_PROP_ID, 0, &keyProviderInfo)) {

                ThrowException(env, KEYSTORE_EXCEPTION, GetLastError());
                __leave;
            }
        }

        // Import encoded certificate
        if (!::CertAddCertificateContextToStore(hCertStore, pCertContext,
            CERT_STORE_ADD_REPLACE_EXISTING, NULL))
        {
            ThrowException(env, KEYSTORE_EXCEPTION, GetLastError());
            __leave;
        }

    }
    __finally
    {
        //--------------------------------------------------------------------
        // Clean up.

        if (hCertStore)
            ::CertCloseStore(hCertStore, 0);

        if (pszCertStoreName)
            env->ReleaseStringUTFChars(jCertStoreName, pszCertStoreName);

        if (pbCertEncoding)
            delete [] pbCertEncoding;

        if (pszCertAliasName)
            delete [] pszCertAliasName;

        if (pszContainerName)
            delete [] pszContainerName;

        if (pwszContainerName)
            delete [] pwszContainerName;

        if (pszProviderName)
            delete [] pszProviderName;

        if (pwszProviderName)
            delete [] pwszProviderName;

        if (pCertContext)
            ::CertFreeCertificateContext(pCertContext);
    }
}

/*
 * Class:     sun_security_mscapi_KeyStore
 * Method:    removeCertificate
 * Signature: (Ljava/lang/String;Ljava/lang/String;[BI)V
 */
JNIEXPORT void JNICALL Java_sun_security_mscapi_KeyStore_removeCertificate
  (JNIEnv *env, jobject obj, jstring jCertStoreName, jstring jCertAliasName,
  jbyteArray jCertEncoding, jint jCertEncodingSize) {

    const char* pszCertStoreName = NULL;
    const char* pszCertAliasName = NULL;
    HCERTSTORE hCertStore = NULL;
    PCCERT_CONTEXT pCertContext = NULL;
    PCCERT_CONTEXT pTBDCertContext = NULL;
    jbyte* pbCertEncoding = NULL;
    DWORD cchNameString = 0;
    char* pszNameString = NULL; // certificate's friendly name
    BOOL bDeleteAttempted = FALSE;

    __try
    {
        // Open a system certificate store.
        pszCertStoreName = env->GetStringUTFChars(jCertStoreName, NULL);
        if ((hCertStore = ::CertOpenSystemStore(NULL, pszCertStoreName)) == NULL) {
            ThrowException(env, KEYSTORE_EXCEPTION, GetLastError());
            __leave;
        }

        // Copy encoding from Java to native buffer
        pbCertEncoding = new jbyte[jCertEncodingSize];
        env->GetByteArrayRegion(jCertEncoding, 0, jCertEncodingSize, pbCertEncoding);

        // Create a certificate context from the encoded cert
        if (!(pCertContext = ::CertCreateCertificateContext(X509_ASN_ENCODING,
            (BYTE*) pbCertEncoding, jCertEncodingSize))) {

            ThrowException(env, CERTIFICATE_PARSING_EXCEPTION, GetLastError());
            __leave;
        }

        // Find the certificate to be deleted
        if (!(pTBDCertContext = ::CertFindCertificateInStore(hCertStore,
            X509_ASN_ENCODING, 0, CERT_FIND_EXISTING, pCertContext, NULL))) {

            ThrowException(env, KEYSTORE_EXCEPTION, GetLastError());
            __leave;
        }

        // Check that its friendly name matches the supplied alias
        if ((cchNameString = ::CertGetNameString(pTBDCertContext,
                CERT_NAME_FRIENDLY_DISPLAY_TYPE, 0, NULL, NULL, 0)) > 1) {

            pszNameString = new char[cchNameString];

            ::CertGetNameString(pTBDCertContext,
                CERT_NAME_FRIENDLY_DISPLAY_TYPE, 0, NULL, pszNameString,
                cchNameString);

            // Compare the certificate's friendly name with supplied alias name
            pszCertAliasName = env->GetStringUTFChars(jCertAliasName, NULL);
            if (strcmp(pszCertAliasName, pszNameString) == 0) {

                // Only delete the certificate if the alias names matches
                if (! ::CertDeleteCertificateFromStore(pTBDCertContext)) {

                    // pTBDCertContext is always freed by the
                    //  CertDeleteCertificateFromStore method
                    bDeleteAttempted = TRUE;

                    ThrowException(env, KEYSTORE_EXCEPTION, GetLastError());
                    __leave;
                }
            }
        }

    }
    __finally
    {
        //--------------------------------------------------------------------
        // Clean up.

        if (hCertStore)
            ::CertCloseStore(hCertStore, 0);

        if (pszCertStoreName)
            env->ReleaseStringUTFChars(jCertStoreName, pszCertStoreName);

        if (pszCertAliasName)
            env->ReleaseStringUTFChars(jCertAliasName, pszCertAliasName);

        if (pbCertEncoding)
            delete [] pbCertEncoding;

        if (pszNameString)
            delete [] pszNameString;

        if (pCertContext)
            ::CertFreeCertificateContext(pCertContext);

        if (bDeleteAttempted && pTBDCertContext)
            ::CertFreeCertificateContext(pTBDCertContext);
    }
}

/*
 * Class:     sun_security_mscapi_KeyStore
 * Method:    destroyKeyContainer
 * Signature: (Ljava/lang/String;)V
 */
JNIEXPORT void JNICALL Java_sun_security_mscapi_KeyStore_destroyKeyContainer
  (JNIEnv *env, jclass clazz, jstring keyContainerName)
{
    HCRYPTPROV hCryptProv = NULL;
    const char* pszKeyContainerName = NULL;

    __try
    {
        pszKeyContainerName = env->GetStringUTFChars(keyContainerName, NULL);

        // Destroying the default key container is not permitted
        // (because it may contain more one keypair).
        if (pszKeyContainerName == NULL) {

            ThrowException(env, KEYSTORE_EXCEPTION, NTE_BAD_KEYSET_PARAM);
            __leave;
        }

        // Acquire a CSP context (to the key container).
        if (::CryptAcquireContext(
            &hCryptProv,
            pszKeyContainerName,
            NULL,
            PROV_RSA_FULL,
            CRYPT_DELETEKEYSET) == FALSE)
        {
            ThrowException(env, KEYSTORE_EXCEPTION, GetLastError());
            __leave;
        }

    }
    __finally
    {
        //--------------------------------------------------------------------
        // Clean up.

        if (pszKeyContainerName)
            env->ReleaseStringUTFChars(keyContainerName, pszKeyContainerName);
    }
}




/*
 * Class:     sun_security_mscapi_RSACipher
 * Method:    findCertificateUsingAlias
 * Signature: (Ljava/lang/String;Ljava/lang/String;)J
 */
JNIEXPORT jlong JNICALL Java_sun_security_mscapi_RSACipher_findCertificateUsingAlias
  (JNIEnv *env, jobject obj, jstring jCertStoreName, jstring jCertAliasName)
{
    const char* pszCertStoreName = NULL;
    const char* pszCertAliasName = NULL;
    HCERTSTORE hCertStore = NULL;
    PCCERT_CONTEXT pCertContext = NULL;
    char* pszNameString = NULL; // certificate's friendly name
    DWORD cchNameString = 0;

    __try
    {
        pszCertStoreName = env->GetStringUTFChars(jCertStoreName, NULL);
        pszCertAliasName = env->GetStringUTFChars(jCertAliasName, NULL);

        // Open a system certificate store.
        if ((hCertStore = ::CertOpenSystemStore(NULL, pszCertStoreName)) == NULL) {
            ThrowException(env, KEYSTORE_EXCEPTION, GetLastError());
            __leave;
        }

        // Use CertEnumCertificatesInStore to get the certificates
        // from the open store. pCertContext must be reset to
        // NULL to retrieve the first certificate in the store.
        while (pCertContext = ::CertEnumCertificatesInStore(hCertStore, pCertContext))
        {
            if ((cchNameString = ::CertGetNameString(pCertContext,
                CERT_NAME_FRIENDLY_DISPLAY_TYPE, 0, NULL, NULL, 0)) == 1) {

                continue; // not found
            }

            pszNameString = new char[cchNameString];

            if (::CertGetNameString(pCertContext,
                CERT_NAME_FRIENDLY_DISPLAY_TYPE, 0, NULL, pszNameString,
                cchNameString) == 1) {

                continue; // not found
            }

            // Compare the certificate's friendly name with supplied alias name
            if (strcmp(pszCertAliasName, pszNameString) == 0) {
                delete [] pszNameString;
                break;

            } else {
                delete [] pszNameString;
            }
        }
    }
    __finally
    {
        if (hCertStore)
            ::CertCloseStore(hCertStore, 0);

        if (pszCertStoreName)
            env->ReleaseStringUTFChars(jCertStoreName, pszCertStoreName);

        if (pszCertAliasName)
            env->ReleaseStringUTFChars(jCertAliasName, pszCertAliasName);
    }

    return (jlong) pCertContext;
}

/*
 * Class:     sun_security_mscapi_RSACipher
 * Method:    getKeyFromCert
 * Signature: (JZ)J
 */
JNIEXPORT jlong JNICALL Java_sun_security_mscapi_RSACipher_getKeyFromCert
  (JNIEnv *env, jobject obj, jlong pCertContext, jboolean usePrivateKey)
{
    HCRYPTPROV hCryptProv = NULL;
    HCRYPTKEY hKey = NULL;
    DWORD dwKeySpec;

    __try
    {
        if (usePrivateKey == JNI_TRUE) {
            // Locate the key container for the certificate's private key
            if (!(::CryptAcquireCertificatePrivateKey(
                (PCCERT_CONTEXT) pCertContext, 0, NULL, &hCryptProv,
                &dwKeySpec, NULL))) {

                ThrowException(env, KEYSTORE_EXCEPTION, GetLastError());
                __leave;
            }

            // Get a handle to the private key
            if (!(::CryptGetUserKey(hCryptProv, dwKeySpec, &hKey))) {
                ThrowException(env, KEY_EXCEPTION, GetLastError());
                __leave;
            }

        } else { // use public key

            //  Acquire a CSP context.
            if(::CryptAcquireContext(
               &hCryptProv,
               "J2SE",
               NULL,
               PROV_RSA_FULL,
               0) == FALSE)
            {
                // If CSP context hasn't been created, create one.
                //
                if (::CryptAcquireContext(
                    &hCryptProv,
                    "J2SE",
                    NULL,
                    PROV_RSA_FULL,
                    CRYPT_NEWKEYSET) == FALSE)
                {
                    ThrowException(env, KEYSTORE_EXCEPTION, GetLastError());
                    __leave;
                }
            }

            // Import the certificate's public key into the key container
            if (!(::CryptImportPublicKeyInfo(hCryptProv, X509_ASN_ENCODING,
                &(((PCCERT_CONTEXT) pCertContext)->pCertInfo->SubjectPublicKeyInfo),
                &hKey))) {

                ThrowException(env, KEY_EXCEPTION, GetLastError());
                __leave;
            }
        }
    }
    __finally
    {
        //--------------------------------------------------------------------
        // Clean up.

        if (hCryptProv)
            ::CryptReleaseContext(hCryptProv, 0);
    }

    return hKey;        // TODO - when finished with this key, call
                        //              CryptDestroyKey(hKey)
}

/*
 * Class:     sun_security_mscapi_KeyStore
 * Method:    getKeyLength
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_sun_security_mscapi_KeyStore_getKeyLength
  (JNIEnv *env, jobject obj, jlong hKey)
{
    DWORD dwDataLen = sizeof(DWORD);
    BYTE pbData[sizeof(DWORD)];
    DWORD length = 0;

    __try
    {
        // Get key length (in bits)
        //TODO - may need to use KP_BLOCKLEN instead?
        if (!(::CryptGetKeyParam((HCRYPTKEY) hKey, KP_KEYLEN, (BYTE *)pbData, &dwDataLen,
            0))) {

            ThrowException(env, KEY_EXCEPTION, GetLastError());
            __leave;
        }
        length = (DWORD) pbData;
    }
    __finally
    {
        // no cleanup required
    }

    return (jint) length;
}

/*
 * Class:     sun_security_mscapi_RSACipher
 * Method:    encryptDecrypt
 * Signature: ([BIJZ)[B
 */
JNIEXPORT jbyteArray JNICALL Java_sun_security_mscapi_RSACipher_encryptDecrypt
  (JNIEnv *env, jclass clazz, jbyteArray jData, jint jDataSize, jlong hKey,
   jboolean doEncrypt)
{
    jbyteArray result = NULL;
    jbyte* pData = NULL;
    DWORD dwDataLen = jDataSize;
    DWORD dwBufLen = env->GetArrayLength(jData);
    DWORD i;
    BYTE tmp;

    __try
    {
        // Copy data from Java buffer to native buffer
        pData = new jbyte[dwBufLen];
        env->GetByteArrayRegion(jData, 0, dwBufLen, pData);

        if (doEncrypt == JNI_TRUE) {
            // encrypt
            if (! ::CryptEncrypt((HCRYPTKEY) hKey, 0, TRUE, 0, (BYTE *)pData,
                &dwDataLen, dwBufLen)) {

                ThrowException(env, KEY_EXCEPTION, GetLastError());
                __leave;
            }
            dwBufLen = dwDataLen;

            // convert from little-endian
            for (i = 0; i < dwBufLen / 2; i++) {
                tmp = pData[i];
                pData[i] = pData[dwBufLen - i -1];
                pData[dwBufLen - i - 1] = tmp;
            }
        } else {
            // convert to little-endian
            for (i = 0; i < dwBufLen / 2; i++) {
                tmp = pData[i];
                pData[i] = pData[dwBufLen - i -1];
                pData[dwBufLen - i - 1] = tmp;
            }

            // decrypt
            if (! ::CryptDecrypt((HCRYPTKEY) hKey, 0, TRUE, 0, (BYTE *)pData,
                &dwBufLen)) {

                ThrowException(env, KEY_EXCEPTION, GetLastError());
                __leave;
            }
        }

        // Create new byte array
        result = env->NewByteArray(dwBufLen);

        // Copy data from native buffer to Java buffer
        env->SetByteArrayRegion(result, 0, dwBufLen, (jbyte*) pData);
    }
    __finally
    {
        if (pData)
            delete [] pData;
    }

    return result;
}

/*
 * Class:     sun_security_mscapi_RSAPublicKey
 * Method:    getPublicKeyBlob
 * Signature: (J)[B
 */
JNIEXPORT jbyteArray JNICALL Java_sun_security_mscapi_RSAPublicKey_getPublicKeyBlob
    (JNIEnv *env, jclass clazz, jlong hCryptKey) {

    jbyteArray blob = NULL;
    DWORD dwBlobLen;
    BYTE* pbKeyBlob = NULL;

    __try
    {

        // Determine the size of the blob
        if (! ::CryptExportKey((HCRYPTKEY) hCryptKey, 0, PUBLICKEYBLOB, 0, NULL,
            &dwBlobLen)) {

            ThrowException(env, KEY_EXCEPTION, GetLastError());
            __leave;
        }

        pbKeyBlob = new BYTE[dwBlobLen];

        // Generate key blob
        if (! ::CryptExportKey((HCRYPTKEY) hCryptKey, 0, PUBLICKEYBLOB, 0,
            pbKeyBlob, &dwBlobLen)) {

            ThrowException(env, KEY_EXCEPTION, GetLastError());
            __leave;
        }

        // Create new byte array
        blob = env->NewByteArray(dwBlobLen);

        // Copy data from native buffer to Java buffer
        env->SetByteArrayRegion(blob, 0, dwBlobLen, (jbyte*) pbKeyBlob);
    }
    __finally
    {
        if (pbKeyBlob)
            delete [] pbKeyBlob;
    }

    return blob;
}

/*
 * Class:     sun_security_mscapi_RSAPublicKey
 * Method:    getExponent
 * Signature: ([B)[B
 */
JNIEXPORT jbyteArray JNICALL Java_sun_security_mscapi_RSAPublicKey_getExponent
    (JNIEnv *env, jclass clazz, jbyteArray jKeyBlob) {

    jbyteArray exponent = NULL;
    jbyte*     exponentBytes = NULL;
    jbyte*     keyBlob = NULL;

    __try {

        jsize length = env->GetArrayLength(jKeyBlob);
        keyBlob = env->GetByteArrayElements(jKeyBlob, 0);

        PUBLICKEYSTRUC* pPublicKeyStruc = (PUBLICKEYSTRUC *) keyBlob;

        // Check BLOB type
        if (pPublicKeyStruc->bType != PUBLICKEYBLOB) {
            ThrowException(env, KEY_EXCEPTION, NTE_BAD_TYPE);
            __leave;
        }

        RSAPUBKEY* pRsaPubKey =
            (RSAPUBKEY *) (keyBlob + sizeof(PUBLICKEYSTRUC));
        int len = sizeof(pRsaPubKey->pubexp);
        exponentBytes = new jbyte[len];

        // convert from little-endian while copying from blob
        for (int i = 0, j = len - 1; i < len; i++, j--) {
            exponentBytes[i] = ((BYTE*) &pRsaPubKey->pubexp)[j];
        }

        exponent = env->NewByteArray(len);
        env->SetByteArrayRegion(exponent, 0, len, exponentBytes);
    }
    __finally
    {
        if (keyBlob)
            env->ReleaseByteArrayElements(jKeyBlob, keyBlob, JNI_ABORT);

        if (exponentBytes)
            delete [] exponentBytes;
    }

    return exponent;
}

/*
 * Class:     sun_security_mscapi_RSAPublicKey
 * Method:    getModulus
 * Signature: ([B)[B
 */
JNIEXPORT jbyteArray JNICALL Java_sun_security_mscapi_RSAPublicKey_getModulus
    (JNIEnv *env, jclass clazz, jbyteArray jKeyBlob) {

    jbyteArray modulus = NULL;
    jbyte*     modulusBytes = NULL;
    jbyte*     keyBlob = NULL;

    __try {

        jsize length = env->GetArrayLength(jKeyBlob);
        keyBlob = env->GetByteArrayElements(jKeyBlob, 0);

        PUBLICKEYSTRUC* pPublicKeyStruc = (PUBLICKEYSTRUC *) keyBlob;

        // Check BLOB type
        if (pPublicKeyStruc->bType != PUBLICKEYBLOB) {
            ThrowException(env, KEY_EXCEPTION, NTE_BAD_TYPE);
            __leave;
        }

        RSAPUBKEY* pRsaPubKey =
            (RSAPUBKEY *) (keyBlob + sizeof(PUBLICKEYSTRUC));
        int len = pRsaPubKey->bitlen / 8;

        modulusBytes = new jbyte[len];
        BYTE * pbModulus =
            (BYTE *) (keyBlob + sizeof(PUBLICKEYSTRUC) + sizeof(RSAPUBKEY));

        // convert from little-endian while copying from blob
        for (int i = 0, j = len - 1; i < len; i++, j--) {
            modulusBytes[i] = pbModulus[j];
        }

        modulus = env->NewByteArray(len);
        env->SetByteArrayRegion(modulus, 0, len, modulusBytes);
    }
    __finally
    {
        if (keyBlob)
            env->ReleaseByteArrayElements(jKeyBlob, keyBlob, JNI_ABORT);

        if (modulusBytes)
            delete [] modulusBytes;
    }

    return modulus;
}

/*
 * Convert an array in big-endian byte order into little-endian byte order.
 */
int convertToLittleEndian(JNIEnv *env, jbyteArray source, jbyte* destination,
    int destinationLength) {

    int count = 0;
    int sourceLength = env->GetArrayLength(source);

    if (sourceLength < destinationLength) {
        return -1;
    }

    jbyte* sourceBytes = env->GetByteArrayElements(source, 0);

    // Copy bytes from the end of the source array to the beginning of the
    // destination array (until the destination array is full).
    // This ensures that the sign byte from the source array will be excluded.
    for (int i = 0; i < destinationLength; i++) {
        destination[i] = sourceBytes[sourceLength - i - 1];
        count++;
    }
    if (sourceBytes)
        env->ReleaseByteArrayElements(source, sourceBytes, JNI_ABORT);

    return count;
}

/*
 * The Microsoft Base Cryptographic Provider supports public-key BLOBs
 * that have the following format:
 *
 *     PUBLICKEYSTRUC publickeystruc;
 *     RSAPUBKEY rsapubkey;
 *     BYTE modulus[rsapubkey.bitlen/8];
 *
 * and private-key BLOBs that have the following format:
 *
 *     PUBLICKEYSTRUC publickeystruc;
 *     RSAPUBKEY rsapubkey;
 *     BYTE modulus[rsapubkey.bitlen/8];
 *     BYTE prime1[rsapubkey.bitlen/16];
 *     BYTE prime2[rsapubkey.bitlen/16];
 *     BYTE exponent1[rsapubkey.bitlen/16];
 *     BYTE exponent2[rsapubkey.bitlen/16];
 *     BYTE coefficient[rsapubkey.bitlen/16];
 *     BYTE privateExponent[rsapubkey.bitlen/8];
 *
 * This method generates such BLOBs from the key elements supplied.
 */
jbyteArray generateKeyBlob(
        JNIEnv *env,
        jint jKeyBitLength,
        jbyteArray jModulus,
        jbyteArray jPublicExponent,
        jbyteArray jPrivateExponent,
        jbyteArray jPrimeP,
        jbyteArray jPrimeQ,
        jbyteArray jExponentP,
        jbyteArray jExponentQ,
        jbyteArray jCrtCoefficient)
{
    jsize jKeyByteLength = jKeyBitLength / 8;
    jsize jBlobLength;
    BOOL bGeneratePrivateKeyBlob;

    // Determine whether to generate a public-key or a private-key BLOB
    if (jPrivateExponent != NULL &&
        jPrimeP != NULL &&
        jPrimeQ != NULL &&
        jExponentP != NULL &&
        jExponentQ != NULL &&
        jCrtCoefficient != NULL) {

        bGeneratePrivateKeyBlob = TRUE;
        jBlobLength = sizeof(BLOBHEADER) +
                        sizeof(RSAPUBKEY) +
                        ((jKeyBitLength / 8) * 4) +
                        (jKeyBitLength / 16);

    } else {
        bGeneratePrivateKeyBlob = FALSE;
        jBlobLength = sizeof(BLOBHEADER) +
                        sizeof(RSAPUBKEY) +
                        (jKeyBitLength / 8);
    }

    jbyte* jBlobBytes = new jbyte[jBlobLength];
    jbyte* jBlobElement;
    jbyteArray jBlob = NULL;
    jsize  jElementLength;

    __try {

        BLOBHEADER *pBlobHeader = (BLOBHEADER *) jBlobBytes;
        if (bGeneratePrivateKeyBlob) {
            pBlobHeader->bType = PRIVATEKEYBLOB;  // 0x07
        } else {
            pBlobHeader->bType = PUBLICKEYBLOB;   // 0x06
        }
        pBlobHeader->bVersion = CUR_BLOB_VERSION; // 0x02
        pBlobHeader->reserved = 0;                // 0x0000
        pBlobHeader->aiKeyAlg = CALG_RSA_KEYX;    // 0x0000a400

        RSAPUBKEY *pRsaPubKey =
            (RSAPUBKEY *) (jBlobBytes + sizeof(PUBLICKEYSTRUC));
        if (bGeneratePrivateKeyBlob) {
            pRsaPubKey->magic = 0x32415352;       // "RSA2"
        } else {
            pRsaPubKey->magic = 0x31415352;       // "RSA1"
        }
        pRsaPubKey->bitlen = jKeyBitLength;
        pRsaPubKey->pubexp = 0; // init

        // Sanity check
        jsize jPublicExponentLength = env->GetArrayLength(jPublicExponent);
        if (jPublicExponentLength > sizeof(pRsaPubKey->pubexp)) {
            ThrowException(env, INVALID_KEY_EXCEPTION, NTE_BAD_TYPE);
            __leave;
        }
        // The length argument must be the smaller of jPublicExponentLength
        // and sizeof(pRsaPubKey->pubkey)
        convertToLittleEndian(env, jPublicExponent,
            (jbyte *) &(pRsaPubKey->pubexp), jPublicExponentLength);

        // Modulus n
        jBlobElement =
            (jbyte *) (jBlobBytes + sizeof(PUBLICKEYSTRUC) + sizeof(RSAPUBKEY));
        jElementLength = convertToLittleEndian(env, jModulus, jBlobElement,
            jKeyByteLength);

        if (bGeneratePrivateKeyBlob) {
            // Prime p
            jBlobElement += jElementLength;
            jElementLength = convertToLittleEndian(env, jPrimeP, jBlobElement,
                jKeyByteLength / 2);

            // Prime q
            jBlobElement += jElementLength;
            jElementLength = convertToLittleEndian(env, jPrimeQ, jBlobElement,
                jKeyByteLength / 2);

            // Prime exponent p
            jBlobElement += jElementLength;
            jElementLength = convertToLittleEndian(env, jExponentP,
                jBlobElement, jKeyByteLength / 2);

            // Prime exponent q
            jBlobElement += jElementLength;
            jElementLength = convertToLittleEndian(env, jExponentQ,
                jBlobElement, jKeyByteLength / 2);

            // CRT coefficient
            jBlobElement += jElementLength;
            jElementLength = convertToLittleEndian(env, jCrtCoefficient,
                jBlobElement, jKeyByteLength / 2);

            // Private exponent
            jBlobElement += jElementLength;
            convertToLittleEndian(env, jPrivateExponent, jBlobElement,
                jKeyByteLength);
        }

        jBlob = env->NewByteArray(jBlobLength);
        env->SetByteArrayRegion(jBlob, 0, jBlobLength, jBlobBytes);

    }
    __finally
    {
        if (jBlobBytes)
            delete [] jBlobBytes;
    }

    return jBlob;
}

/*
 * Class:     sun_security_mscapi_KeyStore
 * Method:    generatePrivateKeyBlob
 * Signature: (I[B[B[B[B[B[B[B[B)[B
 */
JNIEXPORT jbyteArray JNICALL Java_sun_security_mscapi_KeyStore_generatePrivateKeyBlob
    (JNIEnv *env, jclass clazz,
        jint jKeyBitLength,
        jbyteArray jModulus,
        jbyteArray jPublicExponent,
        jbyteArray jPrivateExponent,
        jbyteArray jPrimeP,
        jbyteArray jPrimeQ,
        jbyteArray jExponentP,
        jbyteArray jExponentQ,
        jbyteArray jCrtCoefficient)
{
    return generateKeyBlob(env, jKeyBitLength, jModulus, jPublicExponent,
        jPrivateExponent, jPrimeP, jPrimeQ, jExponentP, jExponentQ,
        jCrtCoefficient);
}

/*
 * Class:     sun_security_mscapi_RSASignature
 * Method:    generatePublicKeyBlob
 * Signature: (I[B[B)[B
 */
JNIEXPORT jbyteArray JNICALL Java_sun_security_mscapi_RSASignature_generatePublicKeyBlob
    (JNIEnv *env, jclass clazz,
        jint jKeyBitLength,
        jbyteArray jModulus,
        jbyteArray jPublicExponent)
{
    return generateKeyBlob(env, jKeyBitLength, jModulus, jPublicExponent,
        NULL, NULL, NULL, NULL, NULL, NULL);
}

/*
 * Class:     sun_security_mscapi_KeyStore
 * Method:    storePrivateKey
 * Signature: ([BLjava/lang/String;I)Lsun/security/mscapi/RSAPrivateKey;
 */
JNIEXPORT jobject JNICALL Java_sun_security_mscapi_KeyStore_storePrivateKey
    (JNIEnv *env, jclass clazz, jbyteArray keyBlob, jstring keyContainerName,
     jint keySize)
{
    HCRYPTPROV hCryptProv = NULL;
    HCRYPTKEY hKey = NULL;
    DWORD dwBlobLen;
    BYTE * pbKeyBlob = NULL;
    const char* pszKeyContainerName = NULL; // UUID
    jobject privateKey = NULL;

    __try
    {
        pszKeyContainerName = env->GetStringUTFChars(keyContainerName, NULL);
        dwBlobLen = env->GetArrayLength(keyBlob);
        pbKeyBlob = (BYTE *) env->GetByteArrayElements(keyBlob, 0);

        // Acquire a CSP context (create a new key container).
        if (::CryptAcquireContext(
            &hCryptProv,
            pszKeyContainerName,
            NULL,
            PROV_RSA_FULL,
            CRYPT_NEWKEYSET) == FALSE)
        {
            ThrowException(env, KEYSTORE_EXCEPTION, GetLastError());
            __leave;
        }

        // Import the private key
        if (::CryptImportKey(
            hCryptProv,
            pbKeyBlob,
            dwBlobLen,
            0,
            CRYPT_EXPORTABLE,
            &hKey) == FALSE)
        {
            ThrowException(env, KEYSTORE_EXCEPTION, GetLastError());
            __leave;
        }

        // Get the method ID for the RSAPrivateKey constructor
        jclass clazzRSAPrivateKey =
            env->FindClass("sun/security/mscapi/RSAPrivateKey");

        jmethodID mNewRSAPrivateKey =
            env->GetMethodID(clazzRSAPrivateKey, "<init>", "(JJI)V");

        // Create a new RSA private key
        privateKey = env->NewObject(clazzRSAPrivateKey, mNewRSAPrivateKey,
            (jlong) hCryptProv, (jlong) hKey, keySize);

    }
    __finally
    {
        //--------------------------------------------------------------------
        // Clean up.

        if (pszKeyContainerName)
            env->ReleaseStringUTFChars(keyContainerName, pszKeyContainerName);

        if (pbKeyBlob)
            env->ReleaseByteArrayElements(keyBlob, (jbyte *) pbKeyBlob,
                JNI_ABORT);
    }

    return privateKey;
}

/*
 * Class:     sun_security_mscapi_RSASignature
 * Method:    importPublicKey
 * Signature: ([BI)Lsun/security/mscapi/RSAPublicKey;
 */
JNIEXPORT jobject JNICALL Java_sun_security_mscapi_RSASignature_importPublicKey
    (JNIEnv *env, jclass clazz, jbyteArray keyBlob, jint keySize)
{
    HCRYPTPROV hCryptProv = NULL;
    HCRYPTKEY hKey = NULL;
    DWORD dwBlobLen;
    BYTE * pbKeyBlob = NULL;
    jobject publicKey = NULL;

    __try
    {
        dwBlobLen = env->GetArrayLength(keyBlob);
        pbKeyBlob = (BYTE *) env->GetByteArrayElements(keyBlob, 0);

        // Acquire a CSP context (create a new key container).
        // Prefer a PROV_RSA_AES CSP, when available, due to its support
        // for SHA-2-based signatures.
        if (::CryptAcquireContext(
            &hCryptProv,
            NULL,
            NULL,
            PROV_RSA_AES,
            CRYPT_VERIFYCONTEXT) == FALSE)
        {
            // Failover to using the default CSP (PROV_RSA_FULL)

            if (::CryptAcquireContext(
                &hCryptProv,
                NULL,
                NULL,
                PROV_RSA_FULL,
                CRYPT_VERIFYCONTEXT) == FALSE)
            {
                ThrowException(env, KEYSTORE_EXCEPTION, GetLastError());
                __leave;
            }
        }

        // Import the public key
        if (::CryptImportKey(
            hCryptProv,
            pbKeyBlob,
            dwBlobLen,
            0,
            CRYPT_EXPORTABLE,
            &hKey) == FALSE)
        {
            ThrowException(env, KEYSTORE_EXCEPTION, GetLastError());
            __leave;
        }

        // Get the method ID for the RSAPublicKey constructor
        jclass clazzRSAPublicKey =
            env->FindClass("sun/security/mscapi/RSAPublicKey");

        jmethodID mNewRSAPublicKey =
            env->GetMethodID(clazzRSAPublicKey, "<init>", "(JJI)V");

        // Create a new RSA public key
        publicKey = env->NewObject(clazzRSAPublicKey, mNewRSAPublicKey,
            (jlong) hCryptProv, (jlong) hKey, keySize);

    }
    __finally
    {
        //--------------------------------------------------------------------
        // Clean up.

        if (pbKeyBlob)
            env->ReleaseByteArrayElements(keyBlob, (jbyte *) pbKeyBlob,
                JNI_ABORT);
    }

    return publicKey;
}

} /* extern "C" */
