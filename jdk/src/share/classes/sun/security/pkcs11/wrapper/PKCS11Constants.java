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

package sun.security.pkcs11.wrapper;



/**
 * This interface holds constants of the PKCS#11 v2.11 standard.
 * This is mainly the content of the 'pkcs11t.h' header file.
 *
 * Mapping of primitiv data types to Java types:
 * <pre>
 *   TRUE .......................................... true
 *   FALSE ......................................... false
 *   CK_BYTE ....................................... byte
 *   CK_CHAR ....................................... char
 *   CK_UTF8CHAR ................................... char
 *   CK_BBOOL ...................................... boolean
 *   CK_ULONG ...................................... long
 *   CK_LONG ....................................... long
 *   CK_FLAGS ...................................... long
 *   CK_NOTIFICATION ............................... long
 *   CK_SLOT_ID .................................... long
 *   CK_SESSION_HANDLE ............................. long
 *   CK_USER_TYPE .................................. long
 *   CK_SESSION_HANDLE ............................. long
 *   CK_STATE ...................................... long
 *   CK_OBJECT_HANDLE .............................. long
 *   CK_OBJECT_CLASS ............................... long
 *   CK_HW_FEATURE_TYPE ............................ long
 *   CK_KEY_TYPE ................................... long
 *   CK_CERTIFICATE_TYPE ........................... long
 *   CK_ATTRIBUTE_TYPE ............................. long
 *   CK_VOID_PTR ................................... Object[]
 *   CK_BYTE_PTR ................................... byte[]
 *   CK_CHAR_PTR ................................... char[]
 *   CK_UTF8CHAR_PTR ............................... char[]
 *   CK_MECHANISM_TYPE ............................. long
 *   CK_RV ......................................... long
 *   CK_RSA_PKCS_OAEP_MGF_TYPE ..................... long
 *   CK_RSA_PKCS_OAEP_SOURCE_TYPE .................. long
 *   CK_RC2_PARAMS ................................. long
 *   CK_MAC_GENERAL_PARAMS ......................... long
 *   CK_EXTRACT_PARAMS ............................. long
 *   CK_PKCS5_PBKD2_PSEUDO_RANDOM_FUNCTION_TYPE .... long
 *   CK_PKCS5_PBKDF2_SALT_SOURCE_TYPE .............. long
 *   CK_EC_KDF_TYPE ................................ long
 *   CK_X9_42_DH_KDF_TYPE .......................... long
 * </pre>
 *
 * @author <a href="mailto:Karl.Scheibelhofer@iaik.at"> Karl Scheibelhofer </a>
 * @invariants
 */
public interface PKCS11Constants {

    public static final boolean TRUE = true;

    public static final boolean FALSE = false;

    public static final Object NULL_PTR = null;

    /* some special values for certain CK_ULONG variables */

    // Cryptoki defines CK_UNAVAILABLE_INFORMATION as (~0UL)
    // This means it is 0xffffffff in ILP32/LLP64 but 0xffffffffffffffff in LP64.
    // To avoid these differences on the Java side, the native code treats
    // CK_UNAVAILABLE_INFORMATION specially and always returns (long)-1 for it.
    // See ckULongSpecialToJLong() in pkcs11wrapper.h
    public static final long CK_UNAVAILABLE_INFORMATION = -1;
    public static final long CK_EFFECTIVELY_INFINITE = 0L;

    /* The following value is always invalid if used as a session */
    /* handle or object handle */
    public static final long CK_INVALID_HANDLE = 0L;

    /* CK_NOTIFICATION enumerates the types of notifications that
     * Cryptoki provides to an application */
    /* CK_NOTIFICATION has been changed from an enum to a CK_ULONG
     * for v2.0 */
    public static final long CKN_SURRENDER = 0L;

    /* flags: bit flags that provide capabilities of the slot
     *      Bit Flag              Mask        Meaning
     */
    public static final long CKF_TOKEN_PRESENT = 0x00000001L;
    public static final long CKF_REMOVABLE_DEVICE = 0x00000002L;
    public static final long CKF_HW_SLOT = 0x00000004L;

    /* The flags parameter is defined as follows:
     *      Bit Flag                    Mask        Meaning
     */
    /* has random # generator */
    public static final long  CKF_RNG                     = 0x00000001L;

    /* token is write-protected */
    public static final long  CKF_WRITE_PROTECTED         = 0x00000002L;

    /* user must login */
    public static final long  CKF_LOGIN_REQUIRED          = 0x00000004L;

    /* normal user's PIN is set */
    public static final long  CKF_USER_PIN_INITIALIZED    = 0x00000008L;

    /* CKF_RESTORE_KEY_NOT_NEEDED is new for v2.0.  If it is set,
     * that means that *every* time the state of cryptographic
     * operations of a session is successfully saved, all keys
     * needed to continue those operations are stored in the state */
    public static final long  CKF_RESTORE_KEY_NOT_NEEDED  = 0x00000020L;

    /* CKF_CLOCK_ON_TOKEN is new for v2.0.  If it is set, that means
     * that the token has some sort of clock.  The time on that
     * clock is returned in the token info structure */
    public static final long  CKF_CLOCK_ON_TOKEN          = 0x00000040L;

    /* CKF_PROTECTED_AUTHENTICATION_PATH is new for v2.0.  If it is
     * set, that means that there is some way for the user to login
     * without sending a PIN through the Cryptoki library itself */
    public static final long  CKF_PROTECTED_AUTHENTICATION_PATH = 0x00000100L;

    /* CKF_DUAL_CRYPTO_OPERATIONS is new for v2.0.  If it is true,
     * that means that a single session with the token can perform
     * dual simultaneous cryptographic operations (digest and
     * encrypt; decrypt and digest; sign and encrypt; and decrypt
     * and sign) */
    public static final long  CKF_DUAL_CRYPTO_OPERATIONS  = 0x00000200L;

    /* CKF_TOKEN_INITIALIZED if new for v2.10. If it is true, the
     * token has been initialized using C_InitializeToken or an
     * equivalent mechanism outside the scope of PKCS #11.
     * Calling C_InitializeToken when this flag is set will cause
     * the token to be reinitialized. */
    public static final long  CKF_TOKEN_INITIALIZED       = 0x00000400L;

    /* CKF_SECONDARY_AUTHENTICATION if new for v2.10. If it is
     * true, the token supports secondary authentication for
     * private key objects. */
    public static final long  CKF_SECONDARY_AUTHENTICATION  = 0x00000800L;

    /* CKF_USER_PIN_COUNT_LOW if new for v2.10. If it is true, an
     * incorrect user login PIN has been entered at least once
     * since the last successful authentication. */
    public static final long  CKF_USER_PIN_COUNT_LOW       = 0x00010000L;

    /* CKF_USER_PIN_FINAL_TRY if new for v2.10. If it is true,
     * supplying an incorrect user PIN will it to become locked. */
    public static final long  CKF_USER_PIN_FINAL_TRY       = 0x00020000L;

    /* CKF_USER_PIN_LOCKED if new for v2.10. If it is true, the
     * user PIN has been locked. User login to the token is not
     * possible. */
    public static final long  CKF_USER_PIN_LOCKED          = 0x00040000L;

    /* CKF_USER_PIN_TO_BE_CHANGED if new for v2.10. If it is true,
     * the user PIN value is the default value set by token
     * initialization or manufacturing. */
    public static final long  CKF_USER_PIN_TO_BE_CHANGED   = 0x00080000L;

    /* CKF_SO_PIN_COUNT_LOW if new for v2.10. If it is true, an
     * incorrect SO login PIN has been entered at least once since
     * the last successful authentication. */
    public static final long  CKF_SO_PIN_COUNT_LOW         = 0x00100000L;

    /* CKF_SO_PIN_FINAL_TRY if new for v2.10. If it is true,
     * supplying an incorrect SO PIN will it to become locked. */
    public static final long  CKF_SO_PIN_FINAL_TRY         = 0x00200000L;

    /* CKF_SO_PIN_LOCKED if new for v2.10. If it is true, the SO
     * PIN has been locked. SO login to the token is not possible.
     */
    public static final long  CKF_SO_PIN_LOCKED            = 0x00400000L;

    /* CKF_SO_PIN_TO_BE_CHANGED if new for v2.10. If it is true,
     * the SO PIN value is the default value set by token
     * initialization or manufacturing. */
    public static final long  CKF_SO_PIN_TO_BE_CHANGED     = 0x00800000L;


    /* CK_USER_TYPE enumerates the types of Cryptoki users */
    /* CK_USER_TYPE has been changed from an enum to a CK_ULONG for
     * v2.0 */
    /* Security Officer */
    public static final long CKU_SO = 0L;
    /* Normal user */
    public static final long CKU_USER = 1L;

    /* CK_STATE enumerates the session states */
    /* CK_STATE has been changed from an enum to a CK_ULONG for
     * v2.0 */
    public static final long  CKS_RO_PUBLIC_SESSION = 0L;
    public static final long  CKS_RO_USER_FUNCTIONS = 1L;
    public static final long  CKS_RW_PUBLIC_SESSION = 2L;
    public static final long  CKS_RW_USER_FUNCTIONS = 3L;
    public static final long  CKS_RW_SO_FUNCTIONS   = 4L;


    /* The flags are defined in the following table:
     *      Bit Flag                Mask        Meaning
     */
    /* session is r/w */
    public static final long  CKF_RW_SESSION        = 0x00000002L;
    /* no parallel */
    public static final long  CKF_SERIAL_SESSION    = 0x00000004L;


    /* The following classes of objects are defined: */
    /* CKO_HW_FEATURE is new for v2.10 */
    /* CKO_DOMAIN_PARAMETERS is new for v2.11 */
    public static final long  CKO_DATA              = 0x00000000L;
    public static final long  CKO_CERTIFICATE       = 0x00000001L;
    public static final long  CKO_PUBLIC_KEY        = 0x00000002L;
    public static final long  CKO_PRIVATE_KEY       = 0x00000003L;
    public static final long  CKO_SECRET_KEY        = 0x00000004L;
    public static final long  CKO_HW_FEATURE        = 0x00000005L;
    public static final long  CKO_DOMAIN_PARAMETERS = 0x00000006L;
    public static final long  CKO_VENDOR_DEFINED    = 0x80000000L;

    // pseudo object class ANY (for template manager)
    public static final long  PCKO_ANY              = 0x7FFFFF23L;


    /* The following hardware feature types are defined */
    public static final long  CKH_MONOTONIC_COUNTER = 0x00000001L;
    public static final long  CKH_CLOCK             = 0x00000002L;
    public static final long  CKH_VENDOR_DEFINED    = 0x80000000L;

    /* the following key types are defined: */
    public static final long  CKK_RSA             = 0x00000000L;
    public static final long  CKK_DSA             = 0x00000001L;
    public static final long  CKK_DH              = 0x00000002L;

    /* CKK_ECDSA and CKK_KEA are new for v2.0 */
    /* CKK_ECDSA is deprecated in v2.11, CKK_EC is preferred. */
    public static final long  CKK_ECDSA           = 0x00000003L;
    public static final long  CKK_EC              = 0x00000003L;
    public static final long  CKK_X9_42_DH        = 0x00000004L;
    public static final long  CKK_KEA             = 0x00000005L;

    public static final long  CKK_GENERIC_SECRET  = 0x00000010L;
    public static final long  CKK_RC2             = 0x00000011L;
    public static final long  CKK_RC4             = 0x00000012L;
    public static final long  CKK_DES             = 0x00000013L;
    public static final long  CKK_DES2            = 0x00000014L;
    public static final long  CKK_DES3            = 0x00000015L;

    /* all these key types are new for v2.0 */
    public static final long  CKK_CAST            = 0x00000016L;
    public static final long  CKK_CAST3           = 0x00000017L;
    /* CKK_CAST5 is deprecated in v2.11, CKK_CAST128 is preferred. */
    public static final long  CKK_CAST5           = 0x00000018L;
    /* CAST128=CAST5 */
    public static final long  CKK_CAST128         = 0x00000018L;
    public static final long  CKK_RC5             = 0x00000019L;
    public static final long  CKK_IDEA            = 0x0000001AL;
    public static final long  CKK_SKIPJACK        = 0x0000001BL;
    public static final long  CKK_BATON           = 0x0000001CL;
    public static final long  CKK_JUNIPER         = 0x0000001DL;
    public static final long  CKK_CDMF            = 0x0000001EL;
    public static final long  CKK_AES             = 0x0000001FL;
    // v2.20
    public static final long  CKK_BLOWFISH        = 0x00000020L;

    public static final long  CKK_VENDOR_DEFINED  = 0x80000000L;

    // pseudo key type ANY (for template manager)
    public static final long  PCKK_ANY            = 0x7FFFFF22L;

    public static final long  PCKK_HMAC            = 0x7FFFFF23L;
    public static final long  PCKK_SSLMAC          = 0x7FFFFF24L;
    public static final long  PCKK_TLSPREMASTER    = 0x7FFFFF25L;
    public static final long  PCKK_TLSRSAPREMASTER = 0x7FFFFF26L;
    public static final long  PCKK_TLSMASTER       = 0x7FFFFF27L;

    /* The following certificate types are defined: */
    /* CKC_X_509_ATTR_CERT is new for v2.10 */
    public static final long  CKC_X_509           = 0x00000000L;
    public static final long  CKC_X_509_ATTR_CERT = 0x00000001L;
    public static final long  CKC_VENDOR_DEFINED  = 0x80000000L;


    /* The following attribute types are defined: */
    public static final long  CKA_CLASS              = 0x00000000L;
    public static final long  CKA_TOKEN              = 0x00000001L;
    public static final long  CKA_PRIVATE            = 0x00000002L;
    public static final long  CKA_LABEL              = 0x00000003L;
    public static final long  CKA_APPLICATION        = 0x00000010L;
    public static final long  CKA_VALUE              = 0x00000011L;

    /* CKA_OBJECT_ID is new for v2.10 */
    public static final long  CKA_OBJECT_ID          = 0x00000012L;

    public static final long  CKA_CERTIFICATE_TYPE   = 0x00000080L;
    public static final long  CKA_ISSUER             = 0x00000081L;
    public static final long  CKA_SERIAL_NUMBER      = 0x00000082L;

    /* CKA_AC_ISSUER, CKA_OWNER, and CKA_ATTR_TYPES are new L;
     * for v2.10 */
    public static final long  CKA_AC_ISSUER          = 0x00000083L;
    public static final long  CKA_OWNER              = 0x00000084L;
    public static final long  CKA_ATTR_TYPES         = 0x00000085L;

    /* CKA_TRUSTED is new for v2.11 */
    public static final long  CKA_TRUSTED            = 0x00000086L;

    public static final long  CKA_KEY_TYPE           = 0x00000100L;
    public static final long  CKA_SUBJECT            = 0x00000101L;
    public static final long  CKA_ID                 = 0x00000102L;
    public static final long  CKA_SENSITIVE          = 0x00000103L;
    public static final long  CKA_ENCRYPT            = 0x00000104L;
    public static final long  CKA_DECRYPT            = 0x00000105L;
    public static final long  CKA_WRAP               = 0x00000106L;
    public static final long  CKA_UNWRAP             = 0x00000107L;
    public static final long  CKA_SIGN               = 0x00000108L;
    public static final long  CKA_SIGN_RECOVER       = 0x00000109L;
    public static final long  CKA_VERIFY             = 0x0000010AL;
    public static final long  CKA_VERIFY_RECOVER     = 0x0000010BL;
    public static final long  CKA_DERIVE             = 0x0000010CL;
    public static final long  CKA_START_DATE         = 0x00000110L;
    public static final long  CKA_END_DATE           = 0x00000111L;
    public static final long  CKA_MODULUS            = 0x00000120L;
    public static final long  CKA_MODULUS_BITS       = 0x00000121L;
    public static final long  CKA_PUBLIC_EXPONENT    = 0x00000122L;
    public static final long  CKA_PRIVATE_EXPONENT   = 0x00000123L;
    public static final long  CKA_PRIME_1            = 0x00000124L;
    public static final long  CKA_PRIME_2            = 0x00000125L;
    public static final long  CKA_EXPONENT_1         = 0x00000126L;
    public static final long  CKA_EXPONENT_2         = 0x00000127L;
    public static final long  CKA_COEFFICIENT        = 0x00000128L;
    public static final long  CKA_PRIME              = 0x00000130L;
    public static final long  CKA_SUBPRIME           = 0x00000131L;
    public static final long  CKA_BASE               = 0x00000132L;

    /* CKA_PRIME_BITS and CKA_SUB_PRIME_BITS are new for v2.11 */
    public static final long  CKA_PRIME_BITS         = 0x00000133L;
    public static final long  CKA_SUB_PRIME_BITS     = 0x00000134L;

    public static final long  CKA_VALUE_BITS         = 0x00000160L;
    public static final long  CKA_VALUE_LEN          = 0x00000161L;

    /* CKA_EXTRACTABLE, CKA_LOCAL, CKA_NEVER_EXTRACTABLE,
     * CKA_ALWAYS_SENSITIVE, CKA_MODIFIABLE, CKA_ECDSA_PARAMS,
     * and CKA_EC_POINT are new for v2.0 */
    public static final long  CKA_EXTRACTABLE        = 0x00000162L;
    public static final long  CKA_LOCAL              = 0x00000163L;
    public static final long  CKA_NEVER_EXTRACTABLE  = 0x00000164L;
    public static final long  CKA_ALWAYS_SENSITIVE   = 0x00000165L;

    /* CKA_KEY_GEN_MECHANISM is new for v2.11 */
    public static final long  CKA_KEY_GEN_MECHANISM  = 0x00000166L;

    public static final long  CKA_MODIFIABLE         = 0x00000170L;

    /* CKA_ECDSA_PARAMS is deprecated in v2.11,
     * CKA_EC_PARAMS is preferred. */
    public static final long  CKA_ECDSA_PARAMS       = 0x00000180L;
    public static final long  CKA_EC_PARAMS          = 0x00000180L;
    public static final long  CKA_EC_POINT           = 0x00000181L;

    /* CKA_SECONDARY_AUTH, CKA_AUTH_PIN_FLAGS,
     * CKA_HW_FEATURE_TYPE, CKA_RESET_ON_INIT, and CKA_HAS_RESET
     * are new for v2.10 */
    public static final long  CKA_SECONDARY_AUTH     = 0x00000200L;
    public static final long  CKA_AUTH_PIN_FLAGS     = 0x00000201L;
    public static final long  CKA_HW_FEATURE_TYPE    = 0x00000300L;
    public static final long  CKA_RESET_ON_INIT      = 0x00000301L;
    public static final long  CKA_HAS_RESET          = 0x00000302L;

    public static final long  CKA_VENDOR_DEFINED     = 0x80000000L;

    /* the following mechanism types are defined: */
    public static final long  CKM_RSA_PKCS_KEY_PAIR_GEN      = 0x00000000L;
    public static final long  CKM_RSA_PKCS                   = 0x00000001L;
    public static final long  CKM_RSA_9796                   = 0x00000002L;
    public static final long  CKM_RSA_X_509                  = 0x00000003L;

    /* CKM_MD2_RSA_PKCS, CKM_MD5_RSA_PKCS, and CKM_SHA1_RSA_PKCS
     * are new for v2.0.  They are mechanisms which hash and sign */
    public static final long  CKM_MD2_RSA_PKCS               = 0x00000004L;
    public static final long  CKM_MD5_RSA_PKCS               = 0x00000005L;
    public static final long  CKM_SHA1_RSA_PKCS              = 0x00000006L;

    /* CKM_RIPEMD128_RSA_PKCS, CKM_RIPEMD160_RSA_PKCS, and
     * CKM_RSA_PKCS_OAEP are new for v2.10 */
    public static final long  CKM_RIPEMD128_RSA_PKCS         = 0x00000007L;
    public static final long  CKM_RIPEMD160_RSA_PKCS         = 0x00000008L;
    public static final long  CKM_RSA_PKCS_OAEP              = 0x00000009L;

    /* CKM_RSA_X9_31_KEY_PAIR_GEN, CKM_RSA_X9_31, CKM_SHA1_RSA_X9_31,
     * CKM_RSA_PKCS_PSS, and CKM_SHA1_RSA_PKCS_PSS are new for v2.11 */
    public static final long  CKM_RSA_X9_31_KEY_PAIR_GEN     = 0x0000000AL;
    public static final long  CKM_RSA_X9_31                  = 0x0000000BL;
    public static final long  CKM_SHA1_RSA_X9_31             = 0x0000000CL;
    public static final long  CKM_RSA_PKCS_PSS               = 0x0000000DL;
    public static final long  CKM_SHA1_RSA_PKCS_PSS          = 0x0000000EL;

    public static final long  CKM_DSA_KEY_PAIR_GEN           = 0x00000010L;
    public static final long  CKM_DSA                        = 0x00000011L;
    public static final long  CKM_DSA_SHA1                   = 0x00000012L;
    public static final long  CKM_DH_PKCS_KEY_PAIR_GEN       = 0x00000020L;
    public static final long  CKM_DH_PKCS_DERIVE             = 0x00000021L;

    /* CKM_X9_42_DH_KEY_PAIR_GEN, CKM_X9_42_DH_DERIVE,
     * CKM_X9_42_DH_HYBRID_DERIVE, and CKM_X9_42_MQV_DERIVE are new for
     * v2.11 */
    public static final long  CKM_X9_42_DH_KEY_PAIR_GEN      = 0x00000030L;
    public static final long  CKM_X9_42_DH_DERIVE            = 0x00000031L;
    public static final long  CKM_X9_42_DH_HYBRID_DERIVE     = 0x00000032L;
    public static final long  CKM_X9_42_MQV_DERIVE           = 0x00000033L;

    // v2.20
    public static final long  CKM_SHA256_RSA_PKCS            = 0x00000040L;
    public static final long  CKM_SHA384_RSA_PKCS            = 0x00000041L;
    public static final long  CKM_SHA512_RSA_PKCS            = 0x00000042L;

    public static final long  CKM_RC2_KEY_GEN                = 0x00000100L;
    public static final long  CKM_RC2_ECB                    = 0x00000101L;
    public static final long  CKM_RC2_CBC                    = 0x00000102L;
    public static final long  CKM_RC2_MAC                    = 0x00000103L;

    /* CKM_RC2_MAC_GENERAL and CKM_RC2_CBC_PAD are new for v2.0 */
    public static final long  CKM_RC2_MAC_GENERAL            = 0x00000104L;
    public static final long  CKM_RC2_CBC_PAD                = 0x00000105L;

    public static final long  CKM_RC4_KEY_GEN                = 0x00000110L;
    public static final long  CKM_RC4                        = 0x00000111L;
    public static final long  CKM_DES_KEY_GEN                = 0x00000120L;
    public static final long  CKM_DES_ECB                    = 0x00000121L;
    public static final long  CKM_DES_CBC                    = 0x00000122L;
    public static final long  CKM_DES_MAC                    = 0x00000123L;

    /* CKM_DES_MAC_GENERAL and CKM_DES_CBC_PAD are new for v2.0 */
    public static final long  CKM_DES_MAC_GENERAL            = 0x00000124L;
    public static final long  CKM_DES_CBC_PAD                = 0x00000125L;

    public static final long  CKM_DES2_KEY_GEN               = 0x00000130L;
    public static final long  CKM_DES3_KEY_GEN               = 0x00000131L;
    public static final long  CKM_DES3_ECB                   = 0x00000132L;
    public static final long  CKM_DES3_CBC                   = 0x00000133L;
    public static final long  CKM_DES3_MAC                   = 0x00000134L;

    /* CKM_DES3_MAC_GENERAL, CKM_DES3_CBC_PAD, CKM_CDMF_KEY_GEN,
     * CKM_CDMF_ECB, CKM_CDMF_CBC, CKM_CDMF_MAC,
     * CKM_CDMF_MAC_GENERAL, and CKM_CDMF_CBC_PAD are new for v2.0 */
    public static final long  CKM_DES3_MAC_GENERAL           = 0x00000135L;
    public static final long  CKM_DES3_CBC_PAD               = 0x00000136L;
    public static final long  CKM_CDMF_KEY_GEN               = 0x00000140L;
    public static final long  CKM_CDMF_ECB                   = 0x00000141L;
    public static final long  CKM_CDMF_CBC                   = 0x00000142L;
    public static final long  CKM_CDMF_MAC                   = 0x00000143L;
    public static final long  CKM_CDMF_MAC_GENERAL           = 0x00000144L;
    public static final long  CKM_CDMF_CBC_PAD               = 0x00000145L;

    public static final long  CKM_MD2                        = 0x00000200L;

    /* CKM_MD2_HMAC and CKM_MD2_HMAC_GENERAL are new for v2.0 */
    public static final long  CKM_MD2_HMAC                   = 0x00000201L;
    public static final long  CKM_MD2_HMAC_GENERAL           = 0x00000202L;

    public static final long  CKM_MD5                        = 0x00000210L;

    /* CKM_MD5_HMAC and CKM_MD5_HMAC_GENERAL are new for v2.0 */
    public static final long  CKM_MD5_HMAC                   = 0x00000211L;
    public static final long  CKM_MD5_HMAC_GENERAL           = 0x00000212L;

    public static final long  CKM_SHA_1                      = 0x00000220L;

    /* CKM_SHA_1_HMAC and CKM_SHA_1_HMAC_GENERAL are new for v2.0 */
    public static final long  CKM_SHA_1_HMAC                 = 0x00000221L;
    public static final long  CKM_SHA_1_HMAC_GENERAL         = 0x00000222L;

    /* CKM_RIPEMD128, CKM_RIPEMD128_HMAC,
     * CKM_RIPEMD128_HMAC_GENERAL, CKM_RIPEMD160, CKM_RIPEMD160_HMAC,
     * and CKM_RIPEMD160_HMAC_GENERAL are new for v2.10 */
    public static final long  CKM_RIPEMD128                  = 0x00000230L;
    public static final long  CKM_RIPEMD128_HMAC             = 0x00000231L;
    public static final long  CKM_RIPEMD128_HMAC_GENERAL     = 0x00000232L;
    public static final long  CKM_RIPEMD160                  = 0x00000240L;
    public static final long  CKM_RIPEMD160_HMAC             = 0x00000241L;
    public static final long  CKM_RIPEMD160_HMAC_GENERAL     = 0x00000242L;

    // v2.20
    public static final long  CKM_SHA256                     = 0x00000250L;
    public static final long  CKM_SHA256_HMAC                = 0x00000251L;
    public static final long  CKM_SHA256_HMAC_GENERAL        = 0x00000252L;

    public static final long  CKM_SHA384                     = 0x00000260L;
    public static final long  CKM_SHA384_HMAC                = 0x00000261L;
    public static final long  CKM_SHA384_HMAC_GENERAL        = 0x00000262L;

    public static final long  CKM_SHA512                     = 0x00000270L;
    public static final long  CKM_SHA512_HMAC                = 0x00000271L;
    public static final long  CKM_SHA512_HMAC_GENERAL        = 0x00000272L;

    /* All of the following mechanisms are new for v2.0 */
    /* Note that CAST128 and CAST5 are the same algorithm */
    public static final long  CKM_CAST_KEY_GEN               = 0x00000300L;
    public static final long  CKM_CAST_ECB                   = 0x00000301L;
    public static final long  CKM_CAST_CBC                   = 0x00000302L;
    public static final long  CKM_CAST_MAC                   = 0x00000303L;
    public static final long  CKM_CAST_MAC_GENERAL           = 0x00000304L;
    public static final long  CKM_CAST_CBC_PAD               = 0x00000305L;
    public static final long  CKM_CAST3_KEY_GEN              = 0x00000310L;
    public static final long  CKM_CAST3_ECB                  = 0x00000311L;
    public static final long  CKM_CAST3_CBC                  = 0x00000312L;
    public static final long  CKM_CAST3_MAC                  = 0x00000313L;
    public static final long  CKM_CAST3_MAC_GENERAL          = 0x00000314L;
    public static final long  CKM_CAST3_CBC_PAD              = 0x00000315L;
    public static final long  CKM_CAST5_KEY_GEN              = 0x00000320L;
    public static final long  CKM_CAST128_KEY_GEN            = 0x00000320L;
    public static final long  CKM_CAST5_ECB                  = 0x00000321L;
    public static final long  CKM_CAST128_ECB                = 0x00000321L;
    public static final long  CKM_CAST5_CBC                  = 0x00000322L;
    public static final long  CKM_CAST128_CBC                = 0x00000322L;
    public static final long  CKM_CAST5_MAC                  = 0x00000323L;
    public static final long  CKM_CAST128_MAC                = 0x00000323L;
    public static final long  CKM_CAST5_MAC_GENERAL          = 0x00000324L;
    public static final long  CKM_CAST128_MAC_GENERAL        = 0x00000324L;
    public static final long  CKM_CAST5_CBC_PAD              = 0x00000325L;
    public static final long  CKM_CAST128_CBC_PAD            = 0x00000325L;
    public static final long  CKM_RC5_KEY_GEN                = 0x00000330L;
    public static final long  CKM_RC5_ECB                    = 0x00000331L;
    public static final long  CKM_RC5_CBC                    = 0x00000332L;
    public static final long  CKM_RC5_MAC                    = 0x00000333L;
    public static final long  CKM_RC5_MAC_GENERAL            = 0x00000334L;
    public static final long  CKM_RC5_CBC_PAD                = 0x00000335L;
    public static final long  CKM_IDEA_KEY_GEN               = 0x00000340L;
    public static final long  CKM_IDEA_ECB                   = 0x00000341L;
    public static final long  CKM_IDEA_CBC                   = 0x00000342L;
    public static final long  CKM_IDEA_MAC                   = 0x00000343L;
    public static final long  CKM_IDEA_MAC_GENERAL           = 0x00000344L;
    public static final long  CKM_IDEA_CBC_PAD               = 0x00000345L;
    public static final long  CKM_GENERIC_SECRET_KEY_GEN     = 0x00000350L;
    public static final long  CKM_CONCATENATE_BASE_AND_KEY   = 0x00000360L;
    public static final long  CKM_CONCATENATE_BASE_AND_DATA  = 0x00000362L;
    public static final long  CKM_CONCATENATE_DATA_AND_BASE  = 0x00000363L;
    public static final long  CKM_XOR_BASE_AND_DATA          = 0x00000364L;
    public static final long  CKM_EXTRACT_KEY_FROM_KEY       = 0x00000365L;
    public static final long  CKM_SSL3_PRE_MASTER_KEY_GEN    = 0x00000370L;
    public static final long  CKM_SSL3_MASTER_KEY_DERIVE     = 0x00000371L;
    public static final long  CKM_SSL3_KEY_AND_MAC_DERIVE    = 0x00000372L;

    /* CKM_SSL3_MASTER_KEY_DERIVE_DH, CKM_TLS_PRE_MASTER_KEY_GEN,
     * CKM_TLS_MASTER_KEY_DERIVE, CKM_TLS_KEY_AND_MAC_DERIVE, and
     * CKM_TLS_MASTER_KEY_DERIVE_DH are new for v2.11 */
    public static final long  CKM_SSL3_MASTER_KEY_DERIVE_DH  = 0x00000373L;
    public static final long  CKM_TLS_PRE_MASTER_KEY_GEN     = 0x00000374L;
    public static final long  CKM_TLS_MASTER_KEY_DERIVE      = 0x00000375L;
    public static final long  CKM_TLS_KEY_AND_MAC_DERIVE     = 0x00000376L;
    public static final long  CKM_TLS_MASTER_KEY_DERIVE_DH   = 0x00000377L;
    public static final long  CKM_TLS_PRF                    = 0x00000378L;

    public static final long  CKM_SSL3_MD5_MAC               = 0x00000380L;
    public static final long  CKM_SSL3_SHA1_MAC              = 0x00000381L;
    public static final long  CKM_MD5_KEY_DERIVATION         = 0x00000390L;
    public static final long  CKM_MD2_KEY_DERIVATION         = 0x00000391L;
    public static final long  CKM_SHA1_KEY_DERIVATION        = 0x00000392L;

    // v2.20
    public static final long  CKM_SHA256_KEY_DERIVATION      = 0x00000393L;
    public static final long  CKM_SHA384_KEY_DERIVATION      = 0x00000394L;
    public static final long  CKM_SHA512_KEY_DERIVATION      = 0x00000395L;

    public static final long  CKM_PBE_MD2_DES_CBC            = 0x000003A0L;
    public static final long  CKM_PBE_MD5_DES_CBC            = 0x000003A1L;
    public static final long  CKM_PBE_MD5_CAST_CBC           = 0x000003A2L;
    public static final long  CKM_PBE_MD5_CAST3_CBC          = 0x000003A3L;
    public static final long  CKM_PBE_MD5_CAST5_CBC          = 0x000003A4L;
    public static final long  CKM_PBE_MD5_CAST128_CBC        = 0x000003A4L;
    public static final long  CKM_PBE_SHA1_CAST5_CBC         = 0x000003A5L;
    public static final long  CKM_PBE_SHA1_CAST128_CBC       = 0x000003A5L;
    public static final long  CKM_PBE_SHA1_RC4_128           = 0x000003A6L;
    public static final long  CKM_PBE_SHA1_RC4_40            = 0x000003A7L;
    public static final long  CKM_PBE_SHA1_DES3_EDE_CBC      = 0x000003A8L;
    public static final long  CKM_PBE_SHA1_DES2_EDE_CBC      = 0x000003A9L;
    public static final long  CKM_PBE_SHA1_RC2_128_CBC       = 0x000003AAL;
    public static final long  CKM_PBE_SHA1_RC2_40_CBC        = 0x000003ABL;

    /* CKM_PKCS5_PBKD2 is new for v2.10 */
    public static final long  CKM_PKCS5_PBKD2                = 0x000003B0L;

    public static final long  CKM_PBA_SHA1_WITH_SHA1_HMAC    = 0x000003C0L;
    public static final long  CKM_KEY_WRAP_LYNKS             = 0x00000400L;
    public static final long  CKM_KEY_WRAP_SET_OAEP          = 0x00000401L;

    /* Fortezza mechanisms */
    public static final long  CKM_SKIPJACK_KEY_GEN           = 0x00001000L;
    public static final long  CKM_SKIPJACK_ECB64             = 0x00001001L;
    public static final long  CKM_SKIPJACK_CBC64             = 0x00001002L;
    public static final long  CKM_SKIPJACK_OFB64             = 0x00001003L;
    public static final long  CKM_SKIPJACK_CFB64             = 0x00001004L;
    public static final long  CKM_SKIPJACK_CFB32             = 0x00001005L;
    public static final long  CKM_SKIPJACK_CFB16             = 0x00001006L;
    public static final long  CKM_SKIPJACK_CFB8              = 0x00001007L;
    public static final long  CKM_SKIPJACK_WRAP              = 0x00001008L;
    public static final long  CKM_SKIPJACK_PRIVATE_WRAP      = 0x00001009L;
    public static final long  CKM_SKIPJACK_RELAYX            = 0x0000100AL;
    public static final long  CKM_KEA_KEY_PAIR_GEN           = 0x00001010L;
    public static final long  CKM_KEA_KEY_DERIVE             = 0x00001011L;
    public static final long  CKM_FORTEZZA_TIMESTAMP         = 0x00001020L;
    public static final long  CKM_BATON_KEY_GEN              = 0x00001030L;
    public static final long  CKM_BATON_ECB128               = 0x00001031L;
    public static final long  CKM_BATON_ECB96                = 0x00001032L;
    public static final long  CKM_BATON_CBC128               = 0x00001033L;
    public static final long  CKM_BATON_COUNTER              = 0x00001034L;
    public static final long  CKM_BATON_SHUFFLE              = 0x00001035L;
    public static final long  CKM_BATON_WRAP                 = 0x00001036L;

    /* CKM_ECDSA_KEY_PAIR_GEN is deprecated in v2.11,
     * CKM_EC_KEY_PAIR_GEN is preferred */
    public static final long  CKM_ECDSA_KEY_PAIR_GEN         = 0x00001040L;
    public static final long  CKM_EC_KEY_PAIR_GEN            = 0x00001040L;

    public static final long  CKM_ECDSA                      = 0x00001041L;
    public static final long  CKM_ECDSA_SHA1                 = 0x00001042L;

    /* CKM_ECDH1_DERIVE, CKM_ECDH1_COFACTOR_DERIVE, and CKM_ECMQV_DERIVE
     * are new for v2.11 */
    public static final long  CKM_ECDH1_DERIVE               = 0x00001050L;
    public static final long  CKM_ECDH1_COFACTOR_DERIVE      = 0x00001051L;
    public static final long  CKM_ECMQV_DERIVE               = 0x00001052L;

    public static final long  CKM_JUNIPER_KEY_GEN            = 0x00001060L;
    public static final long  CKM_JUNIPER_ECB128             = 0x00001061L;
    public static final long  CKM_JUNIPER_CBC128             = 0x00001062L;
    public static final long  CKM_JUNIPER_COUNTER            = 0x00001063L;
    public static final long  CKM_JUNIPER_SHUFFLE            = 0x00001064L;
    public static final long  CKM_JUNIPER_WRAP               = 0x00001065L;
    public static final long  CKM_FASTHASH                   = 0x00001070L;

    /* CKM_AES_KEY_GEN, CKM_AES_ECB, CKM_AES_CBC, CKM_AES_MAC,
     * CKM_AES_MAC_GENERAL, CKM_AES_CBC_PAD, CKM_DSA_PARAMETER_GEN,
     * CKM_DH_PKCS_PARAMETER_GEN, and CKM_X9_42_DH_PARAMETER_GEN are
     * new for v2.11 */
    public static final long  CKM_AES_KEY_GEN                = 0x00001080L;
    public static final long  CKM_AES_ECB                    = 0x00001081L;
    public static final long  CKM_AES_CBC                    = 0x00001082L;
    public static final long  CKM_AES_MAC                    = 0x00001083L;
    public static final long  CKM_AES_MAC_GENERAL            = 0x00001084L;
    public static final long  CKM_AES_CBC_PAD                = 0x00001085L;
    // v2.20
    public static final long  CKM_BLOWFISH_KEY_GEN           = 0x00001090L;
    public static final long  CKM_BLOWFISH_CBC               = 0x00001091L;
    public static final long  CKM_DSA_PARAMETER_GEN          = 0x00002000L;
    public static final long  CKM_DH_PKCS_PARAMETER_GEN      = 0x00002001L;
    public static final long  CKM_X9_42_DH_PARAMETER_GEN     = 0x00002002L;

    public static final long  CKM_VENDOR_DEFINED             = 0x80000000L;

    // NSS private
    public static final long  CKM_NSS_TLS_PRF_GENERAL        = 0x80000373L;

    // ids for our pseudo mechanisms SecureRandom and KeyStore
    public static final long  PCKM_SECURERANDOM              = 0x7FFFFF20L;
    public static final long  PCKM_KEYSTORE                  = 0x7FFFFF21L;

    /* The flags are defined as follows:
     *      Bit Flag               Mask        Meaning */
    /* performed by HW */
    public static final long  CKF_HW                 = 0x00000001L;

    /* The flags CKF_ENCRYPT, CKF_DECRYPT, CKF_DIGEST, CKF_SIGN,
     * CKG_SIGN_RECOVER, CKF_VERIFY, CKF_VERIFY_RECOVER,
     * CKF_GENERATE, CKF_GENERATE_KEY_PAIR, CKF_WRAP, CKF_UNWRAP,
     * and CKF_DERIVE are new for v2.0.  They specify whether or not
     * a mechanism can be used for a particular task */
    public static final long  CKF_ENCRYPT            = 0x00000100L;
    public static final long  CKF_DECRYPT            = 0x00000200L;
    public static final long  CKF_DIGEST             = 0x00000400L;
    public static final long  CKF_SIGN               = 0x00000800L;
    public static final long  CKF_SIGN_RECOVER       = 0x00001000L;
    public static final long  CKF_VERIFY             = 0x00002000L;
    public static final long  CKF_VERIFY_RECOVER     = 0x00004000L;
    public static final long  CKF_GENERATE           = 0x00008000L;
    public static final long  CKF_GENERATE_KEY_PAIR  = 0x00010000L;
    public static final long  CKF_WRAP               = 0x00020000L;
    public static final long  CKF_UNWRAP             = 0x00040000L;
    public static final long  CKF_DERIVE             = 0x00080000L;

    /* CKF_EC_F_P, CKF_EC_F_2M, CKF_EC_ECPARAMETERS, CKF_EC_NAMEDCURVE,
     * CKF_EC_UNCOMPRESS, and CKF_EC_COMPRESS are new for v2.11. They
     * describe a token's EC capabilities not available in mechanism
     * information. */
    public static final long  CKF_EC_F_P              = 0x00100000L;
    public static final long  CKF_EC_F_2M           = 0x00200000L;
    public static final long  CKF_EC_ECPARAMETERS   = 0x00400000L;
    public static final long  CKF_EC_NAMEDCURVE     = 0x00800000L;
    public static final long  CKF_EC_UNCOMPRESS     = 0x01000000L;
    public static final long  CKF_EC_COMPRESS       = 0x02000000L;

    /* FALSE for 2.01 */
    public static final long  CKF_EXTENSION          = 0x80000000L;


    /* CK_RV is a value that identifies the return value of a
     * Cryptoki function */
    /* CK_RV was changed from CK_USHORT to CK_ULONG for v2.0 */
    public static final long  CKR_OK                                = 0x00000000L;
    public static final long  CKR_CANCEL                            = 0x00000001L;
    public static final long  CKR_HOST_MEMORY                       = 0x00000002L;
    public static final long  CKR_SLOT_ID_INVALID                   = 0x00000003L;

    /* CKR_FLAGS_INVALID was removed for v2.0 */

    /* CKR_GENERAL_ERROR and CKR_FUNCTION_FAILED are new for v2.0 */
    public static final long  CKR_GENERAL_ERROR                     = 0x00000005L;
    public static final long  CKR_FUNCTION_FAILED                   = 0x00000006L;

    /* CKR_ARGUMENTS_BAD, CKR_NO_EVENT, CKR_NEED_TO_CREATE_THREADS,
     * and CKR_CANT_LOCK are new for v2.01 */
    public static final long  CKR_ARGUMENTS_BAD                     = 0x00000007L;
    public static final long  CKR_NO_EVENT                          = 0x00000008L;
    public static final long  CKR_NEED_TO_CREATE_THREADS            = 0x00000009L;
    public static final long  CKR_CANT_LOCK                         = 0x0000000AL;

    public static final long  CKR_ATTRIBUTE_READ_ONLY               = 0x00000010L;
    public static final long  CKR_ATTRIBUTE_SENSITIVE               = 0x00000011L;
    public static final long  CKR_ATTRIBUTE_TYPE_INVALID            = 0x00000012L;
    public static final long  CKR_ATTRIBUTE_VALUE_INVALID           = 0x00000013L;
    public static final long  CKR_DATA_INVALID                      = 0x00000020L;
    public static final long  CKR_DATA_LEN_RANGE                    = 0x00000021L;
    public static final long  CKR_DEVICE_ERROR                      = 0x00000030L;
    public static final long  CKR_DEVICE_MEMORY                     = 0x00000031L;
    public static final long  CKR_DEVICE_REMOVED                    = 0x00000032L;
    public static final long  CKR_ENCRYPTED_DATA_INVALID            = 0x00000040L;
    public static final long  CKR_ENCRYPTED_DATA_LEN_RANGE          = 0x00000041L;
    public static final long  CKR_FUNCTION_CANCELED                 = 0x00000050L;
    public static final long  CKR_FUNCTION_NOT_PARALLEL             = 0x00000051L;

    /* CKR_FUNCTION_NOT_SUPPORTED is new for v2.0 */
    public static final long  CKR_FUNCTION_NOT_SUPPORTED            = 0x00000054L;

    public static final long  CKR_KEY_HANDLE_INVALID                = 0x00000060L;

    /* CKR_KEY_SENSITIVE was removed for v2.0 */

    public static final long  CKR_KEY_SIZE_RANGE                    = 0x00000062L;
    public static final long  CKR_KEY_TYPE_INCONSISTENT             = 0x00000063L;

    /* CKR_KEY_NOT_NEEDED, CKR_KEY_CHANGED, CKR_KEY_NEEDED,
     * CKR_KEY_INDIGESTIBLE, CKR_KEY_FUNCTION_NOT_PERMITTED,
     * CKR_KEY_NOT_WRAPPABLE, and CKR_KEY_UNEXTRACTABLE are new for
     * v2.0 */
    public static final long  CKR_KEY_NOT_NEEDED                    = 0x00000064L;
    public static final long  CKR_KEY_CHANGED                       = 0x00000065L;
    public static final long  CKR_KEY_NEEDED                        = 0x00000066L;
    public static final long  CKR_KEY_INDIGESTIBLE                  = 0x00000067L;
    public static final long  CKR_KEY_FUNCTION_NOT_PERMITTED        = 0x00000068L;
    public static final long  CKR_KEY_NOT_WRAPPABLE                 = 0x00000069L;
    public static final long  CKR_KEY_UNEXTRACTABLE                 = 0x0000006AL;

    public static final long  CKR_MECHANISM_INVALID                 = 0x00000070L;
    public static final long  CKR_MECHANISM_PARAM_INVALID           = 0x00000071L;

    /* CKR_OBJECT_CLASS_INCONSISTENT and CKR_OBJECT_CLASS_INVALID
     * were removed for v2.0 */
    public static final long  CKR_OBJECT_HANDLE_INVALID             = 0x00000082L;
    public static final long  CKR_OPERATION_ACTIVE                  = 0x00000090L;
    public static final long  CKR_OPERATION_NOT_INITIALIZED         = 0x00000091L;
    public static final long  CKR_PIN_INCORRECT                     = 0x000000A0L;
    public static final long  CKR_PIN_INVALID                       = 0x000000A1L;
    public static final long  CKR_PIN_LEN_RANGE                     = 0x000000A2L;

    /* CKR_PIN_EXPIRED and CKR_PIN_LOCKED are new for v2.0 */
    public static final long  CKR_PIN_EXPIRED                       = 0x000000A3L;
    public static final long  CKR_PIN_LOCKED                        = 0x000000A4L;

    public static final long  CKR_SESSION_CLOSED                    = 0x000000B0L;
    public static final long  CKR_SESSION_COUNT                     = 0x000000B1L;
    public static final long  CKR_SESSION_HANDLE_INVALID            = 0x000000B3L;
    public static final long  CKR_SESSION_PARALLEL_NOT_SUPPORTED    = 0x000000B4L;
    public static final long  CKR_SESSION_READ_ONLY                 = 0x000000B5L;
    public static final long  CKR_SESSION_EXISTS                    = 0x000000B6L;

    /* CKR_SESSION_READ_ONLY_EXISTS and
     * CKR_SESSION_READ_WRITE_SO_EXISTS are new for v2.0 */
    public static final long  CKR_SESSION_READ_ONLY_EXISTS          = 0x000000B7L;
    public static final long  CKR_SESSION_READ_WRITE_SO_EXISTS      = 0x000000B8L;

    public static final long  CKR_SIGNATURE_INVALID                 = 0x000000C0L;
    public static final long  CKR_SIGNATURE_LEN_RANGE               = 0x000000C1L;
    public static final long  CKR_TEMPLATE_INCOMPLETE               = 0x000000D0L;
    public static final long  CKR_TEMPLATE_INCONSISTENT             = 0x000000D1L;
    public static final long  CKR_TOKEN_NOT_PRESENT                 = 0x000000E0L;
    public static final long  CKR_TOKEN_NOT_RECOGNIZED              = 0x000000E1L;
    public static final long  CKR_TOKEN_WRITE_PROTECTED             = 0x000000E2L;
    public static final long  CKR_UNWRAPPING_KEY_HANDLE_INVALID     = 0x000000F0L;
    public static final long  CKR_UNWRAPPING_KEY_SIZE_RANGE         = 0x000000F1L;
    public static final long  CKR_UNWRAPPING_KEY_TYPE_INCONSISTENT  = 0x000000F2L;
    public static final long  CKR_USER_ALREADY_LOGGED_IN            = 0x00000100L;
    public static final long  CKR_USER_NOT_LOGGED_IN                = 0x00000101L;
    public static final long  CKR_USER_PIN_NOT_INITIALIZED          = 0x00000102L;
    public static final long  CKR_USER_TYPE_INVALID                 = 0x00000103L;

    /* CKR_USER_ANOTHER_ALREADY_LOGGED_IN and CKR_USER_TOO_MANY_TYPES
     * are new to v2.01 */
    public static final long  CKR_USER_ANOTHER_ALREADY_LOGGED_IN    = 0x00000104L;
    public static final long  CKR_USER_TOO_MANY_TYPES               = 0x00000105L;

    public static final long  CKR_WRAPPED_KEY_INVALID               = 0x00000110L;
    public static final long  CKR_WRAPPED_KEY_LEN_RANGE             = 0x00000112L;
    public static final long  CKR_WRAPPING_KEY_HANDLE_INVALID       = 0x00000113L;
    public static final long  CKR_WRAPPING_KEY_SIZE_RANGE           = 0x00000114L;
    public static final long  CKR_WRAPPING_KEY_TYPE_INCONSISTENT    = 0x00000115L;
    public static final long  CKR_RANDOM_SEED_NOT_SUPPORTED         = 0x00000120L;

    /* These are new to v2.0 */
    public static final long  CKR_RANDOM_NO_RNG                     = 0x00000121L;

    /* These are new to v2.11 */
    public static final long  CKR_DOMAIN_PARAMS_INVALID             = 0x00000130L;

    /* These are new to v2.0 */
    public static final long  CKR_BUFFER_TOO_SMALL                  = 0x00000150L;
    public static final long  CKR_SAVED_STATE_INVALID               = 0x00000160L;
    public static final long  CKR_INFORMATION_SENSITIVE             = 0x00000170L;
    public static final long  CKR_STATE_UNSAVEABLE                  = 0x00000180L;

    /* These are new to v2.01 */
    public static final long  CKR_CRYPTOKI_NOT_INITIALIZED          = 0x00000190L;
    public static final long  CKR_CRYPTOKI_ALREADY_INITIALIZED      = 0x00000191L;
    public static final long  CKR_MUTEX_BAD                         = 0x000001A0L;
    public static final long  CKR_MUTEX_NOT_LOCKED                  = 0x000001A1L;

    public static final long  CKR_VENDOR_DEFINED                    = 0x80000000L;


    /* flags: bit flags that provide capabilities of the slot
     *        Bit Flag = Mask
     */
    public static final long  CKF_LIBRARY_CANT_CREATE_OS_THREADS = 0x00000001L;
    public static final long  CKF_OS_LOCKING_OK                  = 0x00000002L;


    /* CKF_DONT_BLOCK is for the function C_WaitForSlotEvent */
    public static final long  CKF_DONT_BLOCK =    1L;


    /* The following MGFs are defined */
    public static final long  CKG_MGF1_SHA1       =  0x00000001L;


    /* The following encoding parameter sources are defined */
    public static final long  CKZ_DATA_SPECIFIED   = 0x00000001L;


    /* The following PRFs are defined in PKCS #5 v2.0. */
    public static final long  CKP_PKCS5_PBKD2_HMAC_SHA1 = 0x00000001L;


    /* The following salt value sources are defined in PKCS #5 v2.0. */
    public static final long CKZ_SALT_SPECIFIED        = 0x00000001L;

    /* the following EC Key Derivation Functions are defined */
    public static final long CKD_NULL                 = 0x00000001L;
    public static final long CKD_SHA1_KDF             = 0x00000002L;

    /* the following X9.42 Diffie-Hellman Key Derivation Functions are defined */
    public static final long CKD_SHA1_KDF_ASN1        = 0x00000003L;
    public static final long CKD_SHA1_KDF_CONCATENATE = 0x00000004L;


    // private NSS attribute (for DSA and DH private keys)
    public static final long  CKA_NETSCAPE_DB         = 0xD5A0DB00L;

    // base number of NSS private attributes
    public static final long  CKA_NETSCAPE_BASE       = 0x80000000L + 0x4E534350L;

    // object type for NSS trust
    public static final long  CKO_NETSCAPE_TRUST      = CKA_NETSCAPE_BASE + 3;

    // base number for NSS trust attributes
    public static final long  CKA_NETSCAPE_TRUST_BASE = CKA_NETSCAPE_BASE + 0x2000;

    // attributes for NSS trust
    public static final long  CKA_NETSCAPE_TRUST_SERVER_AUTH      = CKA_NETSCAPE_TRUST_BASE +   8;
    public static final long  CKA_NETSCAPE_TRUST_CLIENT_AUTH      = CKA_NETSCAPE_TRUST_BASE +   9;
    public static final long  CKA_NETSCAPE_TRUST_CODE_SIGNING     = CKA_NETSCAPE_TRUST_BASE +  10;
    public static final long  CKA_NETSCAPE_TRUST_EMAIL_PROTECTION = CKA_NETSCAPE_TRUST_BASE +  11;
    public static final long  CKA_NETSCAPE_CERT_SHA1_HASH         = CKA_NETSCAPE_TRUST_BASE + 100;
    public static final long  CKA_NETSCAPE_CERT_MD5_HASH          = CKA_NETSCAPE_TRUST_BASE + 101;

    // trust values for each of the NSS trust attributes
    public static final long  CKT_NETSCAPE_TRUSTED           = CKA_NETSCAPE_BASE + 1;
    public static final long  CKT_NETSCAPE_TRUSTED_DELEGATOR = CKA_NETSCAPE_BASE + 2;
    public static final long  CKT_NETSCAPE_UNTRUSTED         = CKA_NETSCAPE_BASE + 3;
    public static final long  CKT_NETSCAPE_MUST_VERIFY       = CKA_NETSCAPE_BASE + 4;
    public static final long  CKT_NETSCAPE_TRUST_UNKNOWN     = CKA_NETSCAPE_BASE + 5; /* default */
    public static final long  CKT_NETSCAPE_VALID             = CKA_NETSCAPE_BASE + 10;
    public static final long  CKT_NETSCAPE_VALID_DELEGATOR   = CKA_NETSCAPE_BASE + 11;

}
