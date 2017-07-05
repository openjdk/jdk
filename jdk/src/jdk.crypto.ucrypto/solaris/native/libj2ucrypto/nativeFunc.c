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

#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <dlfcn.h>
#include <link.h>
#include "nativeFunc.h"

/* standard md5/md/softcrypto method names (ordering is from mapfile) */
static const char MD5_INIT[]                     = "MD5Init";
static const char MD5_UPDATE[]                   = "MD5Update";
static const char MD5_FINAL[]                    = "MD5Final";
static const char SHA1_INIT[]                    = "SHA1Init";
static const char SHA1_UPDATE[]                  = "SHA1Update";
static const char SHA1_FINAL[]                   = "SHA1Final";
static const char SHA2_INIT[]                    = "SHA2Init";
static const char SHA2_UPDATE[]                  = "SHA2Update";
static const char SHA2_FINAL[]                   = "SHA2Final";
static const char UCRYPTO_VERSION[]              = "ucrypto_version";
static const char UCRYPTO_GET_MECHLIST[]         = "ucrypto_get_mechlist";
static const char UCRYPTO_ENCRYPT_INIT[]         = "ucrypto_encrypt_init";
static const char UCRYPTO_ENCRYPT_UPDATE[]       = "ucrypto_encrypt_update";
static const char UCRYPTO_ENCRYPT_FINAL[]        = "ucrypto_encrypt_final";
static const char UCRYPTO_ENCRYPT[]              = "ucrypto_encrypt";
static const char UCRYPTO_DECRYPT_INIT[]         = "ucrypto_decrypt_init";
static const char UCRYPTO_DECRYPT_UPDATE[]       = "ucrypto_decrypt_update";
static const char UCRYPTO_DECRYPT_FINAL[]        = "ucrypto_decrypt_final";
static const char UCRYPTO_DECRYPT[]              = "ucrypto_decrypt";
static const char UCRYPTO_SIGN_INIT[]            = "ucrypto_sign_init";
static const char UCRYPTO_SIGN_UPDATE[]          = "ucrypto_sign_update";
static const char UCRYPTO_SIGN_FINAL[]           = "ucrypto_sign_final";
static const char UCRYPTO_VERIFY_INIT[]          = "ucrypto_verify_init";
static const char UCRYPTO_VERIFY_UPDATE[]        = "ucrypto_verify_update";
static const char UCRYPTO_VERIFY_FINAL[]         = "ucrypto_verify_final";

/**
 * Initialize native T4 crypto function pointers
 */
jboolean* loadNative() {

  jboolean* buf;
  void *lib;

  buf = malloc(2 * sizeof(jboolean));
  buf[0] = buf[1] = JNI_FALSE;
  ftab = (T4CRYPTO_FUNCTION_TABLE_PTR) calloc(1, sizeof(T4CRYPTO_FUNCTION_TABLE));
  if (ftab == NULL) {
    free(buf);
    return NULL;
  }

  lib = dlopen("libmd.so", RTLD_NOW);
  if (lib != NULL) {
    ftab->md5Init = (MD5INIT_FN_PTR) dlsym(lib, MD5_INIT);
    ftab->md5Update = (MD5UPDATE_FN_PTR) dlsym(lib, MD5_UPDATE);
    ftab->md5Final = (MD5FINAL_FN_PTR) dlsym(lib, MD5_FINAL);
    ftab->sha1Init = (SHA1INIT_FN_PTR) dlsym(lib, SHA1_INIT);
    ftab->sha1Update = (SHA1UPDATE_FN_PTR) dlsym(lib, SHA1_UPDATE);
    ftab->sha1Final = (SHA1FINAL_FN_PTR) dlsym(lib, SHA1_FINAL);
    ftab->sha2Init = (SHA2INIT_FN_PTR) dlsym(lib, SHA2_INIT);
    ftab->sha2Update = (SHA2UPDATE_FN_PTR) dlsym(lib, SHA2_UPDATE);
    ftab->sha2Final = (SHA2FINAL_FN_PTR) dlsym(lib, SHA2_FINAL);
    if (ftab->md5Init != NULL && ftab->md5Update != NULL &&
        ftab->md5Final != NULL && ftab->sha1Init != NULL &&
        ftab->sha1Update != NULL && ftab->sha1Final != NULL &&
        ftab->sha2Init != NULL && ftab->sha2Update != NULL &&
        ftab->sha2Final != NULL) {
      buf[0] = JNI_TRUE;
    } else {
      dlclose(lib);
    }
  }

  lib = dlopen("libsoftcrypto.so", RTLD_NOW);
  if (lib != NULL) {
    // These APIs aren't available for v0 lib on Solaris 10
    ftab->ucryptoVersion = (UCRYPTO_VERSION_FN_PTR)
      dlsym(lib, UCRYPTO_VERSION);
    ftab->ucryptoGetMechList = (UCRYPTO_GET_MECHLIST_FN_PTR)
      dlsym(lib, UCRYPTO_GET_MECHLIST);
    //??
    ftab->ucryptoSignInit = (UCRYPTO_SIGN_INIT_FN_PTR)
      dlsym(lib, UCRYPTO_SIGN_INIT);
    ftab->ucryptoSignUpdate = (UCRYPTO_SIGN_UPDATE_FN_PTR)
      dlsym(lib, UCRYPTO_SIGN_UPDATE);
    ftab->ucryptoSignFinal = (UCRYPTO_SIGN_FINAL_FN_PTR)
      dlsym(lib, UCRYPTO_SIGN_FINAL);
    ftab->ucryptoVerifyInit = (UCRYPTO_VERIFY_INIT_FN_PTR)
      dlsym(lib, UCRYPTO_VERIFY_INIT);
    ftab->ucryptoVerifyUpdate = (UCRYPTO_VERIFY_UPDATE_FN_PTR)
      dlsym(lib, UCRYPTO_VERIFY_UPDATE);
    ftab->ucryptoVerifyFinal = (UCRYPTO_VERIFY_FINAL_FN_PTR)
      dlsym(lib, UCRYPTO_VERIFY_FINAL);

    // These should be avilable for all libsoftcrypto libs
    ftab->ucryptoEncryptInit = (UCRYPTO_ENCRYPT_INIT_FN_PTR)
      dlsym(lib, UCRYPTO_ENCRYPT_INIT);
    ftab->ucryptoEncryptUpdate = (UCRYPTO_ENCRYPT_UPDATE_FN_PTR)
      dlsym(lib, UCRYPTO_ENCRYPT_UPDATE);
    ftab->ucryptoEncryptFinal = (UCRYPTO_ENCRYPT_FINAL_FN_PTR)
      dlsym(lib, UCRYPTO_ENCRYPT_FINAL);
    ftab->ucryptoEncrypt = (UCRYPTO_ENCRYPT_FN_PTR)
      dlsym(lib, UCRYPTO_ENCRYPT);

    ftab->ucryptoDecryptInit = (UCRYPTO_DECRYPT_INIT_FN_PTR)
      dlsym(lib, UCRYPTO_DECRYPT_INIT);
    ftab->ucryptoDecryptUpdate = (UCRYPTO_DECRYPT_UPDATE_FN_PTR)
      dlsym(lib, UCRYPTO_DECRYPT_UPDATE);
    ftab->ucryptoDecryptFinal = (UCRYPTO_DECRYPT_FINAL_FN_PTR)
      dlsym(lib, UCRYPTO_DECRYPT_FINAL);
    ftab->ucryptoDecrypt = (UCRYPTO_DECRYPT_FN_PTR)
      dlsym(lib, UCRYPTO_DECRYPT);

    if (ftab->ucryptoEncryptInit != NULL &&
        ftab->ucryptoEncryptUpdate != NULL &&
        ftab->ucryptoEncryptFinal != NULL &&
        ftab->ucryptoEncrypt != NULL &&
        ftab->ucryptoDecryptInit != NULL &&
        ftab->ucryptoDecryptUpdate != NULL &&
        ftab->ucryptoDecryptFinal != NULL &&
        ftab->ucryptoDecrypt != NULL) {
      buf[1] = JNI_TRUE;
    } else {
      dlclose(lib);
    }
  }

  return buf;
}
