/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

#ifndef _LIBSOFTCRYPTO_H
#define _LIBSOFTCRYPTO_H

#include <sys/types.h>
#include <stdio.h>
#include <stdlib.h>
#include <errno.h>
#include <unistd.h>
#include <strings.h>

typedef enum ucrypto_mech {
    CRYPTO_AES_ECB = 1,
    CRYPTO_AES_CBC,
    CRYPTO_AES_CBC_PAD,
    CRYPTO_AES_CTR,
    CRYPTO_AES_CCM,
    CRYPTO_AES_GCM,
    CRYPTO_AES_GMAC,
    CRYPTO_AES_CFB128,
    CRYPTO_RSA_PKCS = 31,
    CRYPTO_RSA_X_509,
    CRYPTO_MD5_RSA_PKCS,
    CRYPTO_SHA1_RSA_PKCS,
    CRYPTO_SHA256_RSA_PKCS,
    CRYPTO_SHA384_RSA_PKCS,
    CRYPTO_SHA512_RSA_PKCS
} ucrypto_mech_t;

typedef struct crypto_ctx {
    void *cc_provider;
    uint_t    cc_session;
    void            *cc_provider_private;    /* owned by provider */
    void            *cc_framework_private;    /* owned by framework */
    uint32_t        cc_flags;        /* flags */
    void            *cc_opstate;        /* state */
} crypto_ctx_t;

extern int ucrypto_encrypt_init(crypto_ctx_t *context,
    ucrypto_mech_t mech_type, uchar_t *key_str, size_t key_len,
    void *iv, size_t iv_len);

extern int ucrypto_encrypt_update(crypto_ctx_t *context, uchar_t *in,
    size_t in_len, uchar_t *out, size_t *out_len);

extern int ucrypto_encrypt_final(crypto_ctx_t *context, uchar_t *out,
    size_t *out_len);

/* Encrypt atomic */
extern int ucrypto_encrypt(ucrypto_mech_t mech_type, uchar_t *key_str,
    size_t key_len, void *iv, size_t iv_len, uchar_t *in,
    size_t in_len, uchar_t *out, size_t *out_len);

/* Decrypt multi-part */
extern int ucrypto_decrypt_init(crypto_ctx_t *context,
    ucrypto_mech_t mech_type, uchar_t *key_str, size_t key_len,
    void *iv, size_t iv_len);

extern int ucrypto_decrypt_update(crypto_ctx_t *context, uchar_t *in,
    size_t in_len, uchar_t *out, size_t *out_len);

extern int ucrypto_decrypt_final(crypto_ctx_t *context, uchar_t *out,
    size_t *out_len);

/* Decrypt atomic */
extern int ucrypto_decrypt(ucrypto_mech_t mech_type, uchar_t *key_str,
    size_t key_len, void *iv, size_t iv_len, uchar_t *in,
    size_t in_len, uchar_t *out, size_t *out_len);

/* Sign multi-part */
extern int ucrypto_sign_init(crypto_ctx_t *context, ucrypto_mech_t mech_type,
    uchar_t *key_str, size_t key_len, void *iv, size_t iv_len);

extern int ucrypto_sign_update(crypto_ctx_t *context,
    uchar_t *data_str, size_t data_len);

extern int ucrypto_sign_final(crypto_ctx_t *context,
    uchar_t *sig_str, size_t *sig_len);

/* Sign atomic */
extern int ucrypto_sign(ucrypto_mech_t mech_type,
    uchar_t *key_str, size_t key_len, void *iv, size_t iv_len,
    uchar_t *data_str, size_t data_len, uchar_t *sig_str, size_t *sig_len);

/* Verify multi-part */
extern int ucrypto_verify_init(crypto_ctx_t *context, ucrypto_mech_t mech_type,
    uchar_t *key_str, size_t key_len, void *iv, size_t iv_len);

extern int ucrypto_verify_update(crypto_ctx_t *context,
    uchar_t *data_str, size_t data_len);

extern int ucrypto_verify_final(crypto_ctx_t *context,
    uchar_t *sig_str, size_t *sig_len);

/* Verify atomic */
extern int ucrypto_verify(ucrypto_mech_t mech_type,
    uchar_t *key_str, size_t key_len, void *iv, size_t iv_len,
    uchar_t *data_str, size_t data_len, uchar_t *sig, size_t *sig_len);

extern int ucrypto_get_mechlist(char *str);

extern const char *ucrypto_id2mech(ucrypto_mech_t mech_type);

extern ucrypto_mech_t ucrypto_mech2id(const char *str);

extern int ucrypto_version();

typedef struct CK_AES_CTR_PARAMS {
    ulong_t    ulCounterBits;
    uint8_t cb[16];
} CK_AES_CTR_PARAMS;

typedef struct CK_AES_GCM_PARAMS {
    uchar_t *pIv;
    ulong_t ulIvLen;
    ulong_t ulIvBits;
    uchar_t *pAAD;
    ulong_t ulAADLen;
    ulong_t ulTagBits;
} CK_AES_GCM_PARAMS;

typedef struct crypto_object_attribute {
    uint64_t    oa_type;    /* attribute type */
    caddr_t            oa_value;    /* attribute value */
    ssize_t            oa_value_len;    /* length of attribute value */
} crypto_object_attribute_t;

/* Attribute types to use for passing a RSA public key or a private key. */
#define    SUN_CKA_MODULUS            0x00000120
#define    SUN_CKA_MODULUS_BITS        0x00000121
#define    SUN_CKA_PUBLIC_EXPONENT        0x00000122
#define    SUN_CKA_PRIVATE_EXPONENT    0x00000123
#define    SUN_CKA_PRIME_1            0x00000124
#define    SUN_CKA_PRIME_2            0x00000125
#define    SUN_CKA_EXPONENT_1        0x00000126
#define    SUN_CKA_EXPONENT_2        0x00000127
#define    SUN_CKA_COEFFICIENT        0x00000128
#define    SUN_CKA_PRIME            0x00000130
#define    SUN_CKA_SUBPRIME        0x00000131
#define    SUN_CKA_BASE            0x00000132

#define    CKK_EC            0x00000003
#define    CKK_GENERIC_SECRET    0x00000010
#define    CKK_RC4            0x00000012
#define    CKK_AES            0x0000001F
#define    CKK_DES            0x00000013
#define    CKK_DES2        0x00000014
#define    CKK_DES3        0x00000015

#define    CKO_PUBLIC_KEY        0x00000002
#define    CKO_PRIVATE_KEY        0x00000003
#define    CKA_CLASS        0x00000000
#define    CKA_VALUE        0x00000011
#define    CKA_KEY_TYPE        0x00000100
#define    CKA_VALUE_LEN        0x00000161
#define    CKA_EC_PARAMS        0x00000180
#define    CKA_EC_POINT        0x00000181

#endif /* _LIBSOFTCRYPTO_H */
