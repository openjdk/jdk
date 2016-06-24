/*
 * Copyright (c) 2014, 2016, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SPARCT4_NATIVE_FUNC_H
#define SPARCT4_NATIVE_FUNC_H
#include <md5.h>
#include <sha1.h>
#include <sha2.h>
#include <libsoftcrypto.h> // redirects to libucrypto.h starting 11.3

jboolean* loadNative();

/* function pointer definitions */

typedef void (*MD5INIT_FN_PTR)(MD5_CTX *context);

typedef void (*MD5UPDATE_FN_PTR)
     (MD5_CTX *context, unsigned char *input,
      unsigned int inlen);

typedef void (*MD5FINAL_FN_PTR)
     (unsigned char *output, MD5_CTX *context);

typedef void (*SHA1INIT_FN_PTR)(SHA1_CTX *context);

typedef void (*SHA1UPDATE_FN_PTR)
     (SHA1_CTX *context, unsigned char *input,
      unsigned int inlen);

typedef void (*SHA1FINAL_FN_PTR)
     (unsigned char *output, SHA1_CTX *context);

typedef void (*SHA2INIT_FN_PTR)(uint64_t mech, SHA2_CTX *context);

typedef void (*SHA2UPDATE_FN_PTR)
     (SHA2_CTX *context, unsigned char *input,
      unsigned int inlen);

typedef void (*SHA2FINAL_FN_PTR)
     (unsigned char *output, SHA2_CTX *context);

typedef int (*UCRYPTO_VERSION_FN_PTR)();

typedef int (*UCRYPTO_GET_MECHLIST_FN_PTR)(char *str);

typedef int (*UCRYPTO_ENCRYPT_INIT_FN_PTR)
     (crypto_ctx_t *context, ucrypto_mech_t mech_type,
      uchar_t *key_str, size_t key_len,
      void *iv, size_t iv_len);
typedef int (*UCRYPTO_ENCRYPT_UPDATE_FN_PTR)
     (crypto_ctx_t *context, uchar_t *in,
      size_t in_len, uchar_t *out, size_t *out_len);
typedef int (*UCRYPTO_ENCRYPT_FINAL_FN_PTR)
     (crypto_ctx_t *context, uchar_t *out,
      size_t *out_len);
typedef int (*UCRYPTO_ENCRYPT_FN_PTR)
     (ucrypto_mech_t mech_type, uchar_t *key_str,
      size_t key_len, void *iv, size_t iv_len, uchar_t *in,
      size_t in_len, uchar_t *out, size_t *out_len);

typedef int (*UCRYPTO_DECRYPT_INIT_FN_PTR)
     (crypto_ctx_t *context,
      ucrypto_mech_t mech_type, uchar_t *key_str, size_t key_len,
      void *iv, size_t iv_len);
typedef int (*UCRYPTO_DECRYPT_UPDATE_FN_PTR)
     (crypto_ctx_t *context, uchar_t *in,
      size_t in_len, uchar_t *out, size_t *out_len);
typedef int (*UCRYPTO_DECRYPT_FINAL_FN_PTR)
     (crypto_ctx_t *context, uchar_t *out,
      size_t *out_len);
typedef int (*UCRYPTO_DECRYPT_FN_PTR)
     (ucrypto_mech_t mech_type, uchar_t *key_str,
      size_t key_len, void *iv, size_t iv_len, uchar_t *in,
      size_t in_len, uchar_t *out, size_t *out_len);

typedef int (*UCRYPTO_SIGN_INIT_FN_PTR)
     (crypto_ctx_t *context, ucrypto_mech_t mech_type,
      uchar_t *key_str, size_t key_len,
      void *iv, size_t iv_len);
typedef int (*UCRYPTO_SIGN_UPDATE_FN_PTR)
     (crypto_ctx_t *context, uchar_t *data_str, size_t data_len);
typedef int (*UCRYPTO_SIGN_FINAL_FN_PTR)
     (crypto_ctx_t *context, uchar_t *sig_str, size_t *sig_len);

typedef int (*UCRYPTO_VERIFY_INIT_FN_PTR)
     (crypto_ctx_t *context, ucrypto_mech_t mech_type,
      uchar_t *key_str, size_t key_len,
      void *iv, size_t iv_len);
typedef int (*UCRYPTO_VERIFY_UPDATE_FN_PTR)
     (crypto_ctx_t *context, uchar_t *data_str, size_t data_len);
typedef int (*UCRYPTO_VERIFY_FINAL_FN_PTR)
     (crypto_ctx_t *context, uchar_t *sig_str, size_t *sig_len);

typedef int (*UCRYPTO_DIGEST_INIT_FN_PTR)
     (crypto_ctx_t *context, ucrypto_mech_t mech_type,
      void *param, size_t param_len);
typedef int (*UCRYPTO_DIGEST_UPDATE_FN_PTR)
     (crypto_ctx_t *context, const uchar_t *data, size_t data_len);
typedef int (*UCRYPTO_DIGEST_FINAL_FN_PTR)
     (crypto_ctx_t *context, uchar_t *digest, size_t *digest_len);

typedef void (*UCRYPTO_FREE_CONTEXT_FN_PTR)
     (crypto_ctx_t *context);

typedef char* (*UCRYPTO_STRERROR_FN_PTR)(int rv);



/* dynamically resolved functions from libmd, and libsoftcrypto
   libraries */
typedef struct T4CRYPTO_FUNCTION_TABLE {
  MD5INIT_FN_PTR                 md5Init;
  MD5UPDATE_FN_PTR               md5Update;
  MD5FINAL_FN_PTR                md5Final;
  SHA1INIT_FN_PTR                sha1Init;
  SHA1UPDATE_FN_PTR              sha1Update;
  SHA1FINAL_FN_PTR               sha1Final;
  SHA2INIT_FN_PTR                sha2Init;
  SHA2UPDATE_FN_PTR              sha2Update;
  SHA2FINAL_FN_PTR               sha2Final;
  UCRYPTO_VERSION_FN_PTR         ucryptoVersion;
  UCRYPTO_GET_MECHLIST_FN_PTR    ucryptoGetMechList;
  UCRYPTO_ENCRYPT_INIT_FN_PTR    ucryptoEncryptInit;
  UCRYPTO_ENCRYPT_UPDATE_FN_PTR  ucryptoEncryptUpdate;
  UCRYPTO_ENCRYPT_FINAL_FN_PTR   ucryptoEncryptFinal;
  UCRYPTO_ENCRYPT_FN_PTR         ucryptoEncrypt;
  UCRYPTO_DECRYPT_INIT_FN_PTR    ucryptoDecryptInit;
  UCRYPTO_DECRYPT_UPDATE_FN_PTR  ucryptoDecryptUpdate;
  UCRYPTO_DECRYPT_FINAL_FN_PTR   ucryptoDecryptFinal;
  UCRYPTO_DECRYPT_FN_PTR         ucryptoDecrypt;
  UCRYPTO_SIGN_INIT_FN_PTR       ucryptoSignInit;
  UCRYPTO_SIGN_UPDATE_FN_PTR     ucryptoSignUpdate;
  UCRYPTO_SIGN_FINAL_FN_PTR      ucryptoSignFinal;
  UCRYPTO_VERIFY_INIT_FN_PTR     ucryptoVerifyInit;
  UCRYPTO_VERIFY_UPDATE_FN_PTR   ucryptoVerifyUpdate;
  UCRYPTO_VERIFY_FINAL_FN_PTR    ucryptoVerifyFinal;
  UCRYPTO_DIGEST_INIT_FN_PTR     ucryptoDigestInit;
  UCRYPTO_DIGEST_UPDATE_FN_PTR   ucryptoDigestUpdate;
  UCRYPTO_DIGEST_FINAL_FN_PTR    ucryptoDigestFinal;
  UCRYPTO_FREE_CONTEXT_FN_PTR    ucryptoFreeContext;
  UCRYPTO_STRERROR_FN_PTR        ucryptoStrerror;
} T4CRYPTO_FUNCTION_TABLE;

typedef T4CRYPTO_FUNCTION_TABLE *T4CRYPTO_FUNCTION_TABLE_PTR;

/* global function table */
T4CRYPTO_FUNCTION_TABLE_PTR ftab;

#endif
