/* pkcs-11v2-20a3.h include file for the PKCS #11 Version 2.20 Amendment 3
   document. */

/* $Revision: 1.4 $ */

/* License to copy and use this software is granted provided that it is
 * identified as "RSA Security Inc. PKCS #11 Cryptographic Token Interface
 * (Cryptoki) Version 2.20 Amendment 3" in all material mentioning or
 * referencing this software.

 * RSA Security Inc. makes no representations concerning either the
 * merchantability of this software or the suitability of this software for
 * any particular purpose. It is provided "as is" without express or implied
 * warranty of any kind.
 */

/* This file is preferably included after inclusion of pkcs11.h */

#ifndef _PKCS_11V2_20A3_H_
#define _PKCS_11V2_20A3_H_ 1

/* Are the definitions of this file already included in pkcs11t.h ? */
#ifndef CKK_CAMELLIA

#ifdef __cplusplus
extern "C" {
#endif

/* Key types */

/* Camellia is new for PKCS #11 v2.20 amendment 3 */
#define CKK_CAMELLIA                   0x00000025
/* ARIA is new for PKCS #11 v2.20 amendment 3 */
#define CKK_ARIA                       0x00000026


/* Mask-generating functions */

/* SHA-224 is new for PKCS #11 v2.20 amendment 3 */
#define CKG_MGF1_SHA224                0x00000005


/* Mechanism Identifiers */

/* SHA-224 is new for PKCS #11 v2.20 amendment 3 */
#define CKM_SHA224                     0x00000255
#define CKM_SHA224_HMAC                0x00000256
#define CKM_SHA224_HMAC_GENERAL        0x00000257

/* SHA-224 key derivation is new for PKCS #11 v2.20 amendment 3 */
#define CKM_SHA224_KEY_DERIVATION      0x00000396

/* SHA-224 RSA mechanisms are new for PKCS #11 v2.20 amendment 3 */
#define CKM_SHA224_RSA_PKCS            0x00000046
#define CKM_SHA224_RSA_PKCS_PSS        0x00000047

/* AES counter mode is new for PKCS #11 v2.20 amendment 3 */
#define CKM_AES_CTR                    0x00001086

/* Camellia is new for PKCS #11 v2.20 amendment 3 */
#define CKM_CAMELLIA_KEY_GEN           0x00000550
#define CKM_CAMELLIA_ECB               0x00000551
#define CKM_CAMELLIA_CBC               0x00000552
#define CKM_CAMELLIA_MAC               0x00000553
#define CKM_CAMELLIA_MAC_GENERAL       0x00000554
#define CKM_CAMELLIA_CBC_PAD           0x00000555
#define CKM_CAMELLIA_ECB_ENCRYPT_DATA  0x00000556
#define CKM_CAMELLIA_CBC_ENCRYPT_DATA  0x00000557
#define CKM_CAMELLIA_CTR               0x00000558

/* ARIA is new for PKCS #11 v2.20 amendment 3 */
#define CKM_ARIA_KEY_GEN               0x00000560
#define CKM_ARIA_ECB                   0x00000561
#define CKM_ARIA_CBC                   0x00000562
#define CKM_ARIA_MAC                   0x00000563
#define CKM_ARIA_MAC_GENERAL           0x00000564
#define CKM_ARIA_CBC_PAD               0x00000565
#define CKM_ARIA_ECB_ENCRYPT_DATA      0x00000566
#define CKM_ARIA_CBC_ENCRYPT_DATA      0x00000567


/* Mechanism parameters */

/* CK_AES_CTR_PARAMS is new for PKCS #11 v2.20 amendment 3 */
typedef struct CK_AES_CTR_PARAMS {
    CK_ULONG ulCounterBits;
    CK_BYTE cb[16];
} CK_AES_CTR_PARAMS;

typedef CK_AES_CTR_PARAMS CK_PTR CK_AES_CTR_PARAMS_PTR;

/* CK_CAMELLIA_CTR_PARAMS is new for PKCS #11 v2.20 amendment 3 */
typedef struct CK_CAMELLIA_CTR_PARAMS {
    CK_ULONG ulCounterBits;
    CK_BYTE cb[16];
} CK_CAMELLIA_CTR_PARAMS;

typedef CK_CAMELLIA_CTR_PARAMS CK_PTR CK_CAMELLIA_CTR_PARAMS_PTR;

/* CK_CAMELLIA_CBC_ENCRYPT_DATA_PARAMS is new for PKCS #11 v2.20 amendment 3 */
typedef struct CK_CAMELLIA_CBC_ENCRYPT_DATA_PARAMS {
    CK_BYTE      iv[16];
    CK_BYTE_PTR  pData;
    CK_ULONG     length;
} CK_CAMELLIA_CBC_ENCRYPT_DATA_PARAMS;

typedef CK_CAMELLIA_CBC_ENCRYPT_DATA_PARAMS CK_PTR CK_CAMELLIA_CBC_ENCRYPT_DATA_PARAMS_PTR;

/* CK_ARIA_CBC_ENCRYPT_DATA_PARAMS is new for PKCS #11 v2.20 amendment 3 */
typedef struct CK_ARIA_CBC_ENCRYPT_DATA_PARAMS {
    CK_BYTE      iv[16];
    CK_BYTE_PTR  pData;
    CK_ULONG     length;
} CK_ARIA_CBC_ENCRYPT_DATA_PARAMS;

typedef CK_ARIA_CBC_ENCRYPT_DATA_PARAMS CK_PTR CK_ARIA_CBC_ENCRYPT_DATA_PARAMS_PTR;

#ifdef __cplusplus
}
#endif

#endif

#endif
