/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
#include <krb5/krb5.h>
#include <string.h>
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <limits.h>

#include "NativeCredentialCacheHelper.h"

// Global krb5 context
static krb5_context g_context = NULL;

/**
 * Initialize krb5 context with OneKDC config
 */
static krb5_error_code ensure_context() {
    // Check if OneKDC config file exists or needs to be created
    if (access("localkdc-krb5.conf", F_OK) != -1) {
        char *current_path = realpath("localkdc-krb5.conf", NULL);
        if (current_path != NULL) {
            setenv("KRB5_CONFIG", current_path, 1);
            free(current_path);

            // If context already exists, reinitialize it
            if (g_context != NULL) {
                krb5_free_context(g_context);
                g_context = NULL;
            }
        }
    }

    if (g_context == NULL) {
        return krb5_init_context(&g_context);
    }
    return 0;
}

/**
 * Convert Java string to C string
 */
static char* jstring_to_cstring(JNIEnv *env, jstring jstr) {
    if (jstr == NULL) return NULL;

    const char *utf_chars = (*env)->GetStringUTFChars(env, jstr, NULL);
    if (utf_chars == NULL) return NULL;

    char *result = strdup(utf_chars);
    if (result == NULL) return NULL;

    (*env)->ReleaseStringUTFChars(env, jstr, utf_chars);
    return result;
}

/**
 * Print error messages for krb5 errors
 */
static void print_krb5_error(const char *operation, krb5_error_code code) {
    if (code != 0) {
        printf("krb5 error in %s: %s\n", operation, error_message(code));
    }
}

/**
 * Create an in-memory credential cache using native krb5 API.
 */
JNIEXPORT jboolean JNICALL Java_NativeCredentialCacheHelper_createInMemoryCache
  (JNIEnv *env, jclass cls, jstring cacheName)
{
    krb5_error_code ret;
    krb5_ccache ccache;
    char *cache_name = NULL;

    ret = ensure_context();
    if (ret) {
        print_krb5_error("ensure_context", ret);
        return JNI_FALSE;
    }

    cache_name = jstring_to_cstring(env, cacheName);
    if (cache_name == NULL) {
        return JNI_FALSE;
    }

    // Resolve the memory cache
    ret = krb5_cc_resolve(g_context, cache_name, &ccache);
    if (ret) {
        print_krb5_error("krb5_cc_resolve", ret);
        free(cache_name);
        return JNI_FALSE;
    }

    printf("Created memory cache: %s\n", cache_name);

    krb5_cc_close(g_context, ccache);
    free(cache_name);
    return JNI_TRUE;
}

/**
 * Set KRB5CCNAME so that the test will pick up the in-memory credential cache.
 */
JNIEXPORT jboolean JNICALL Java_NativeCredentialCacheHelper_setDefaultCache
  (JNIEnv *env, jclass cls, jstring cacheName)
{
    char *cache_name = jstring_to_cstring(env, cacheName);
    if (cache_name == NULL) {
        return JNI_FALSE;
    }

    // Set KRB5CCNAME environment variable
    if (setenv("KRB5CCNAME", cache_name, 1) != 0) {
        free(cache_name);
        return JNI_FALSE;
    }

    printf("Set default cache to: %s\n", cache_name);
    free(cache_name);
    return JNI_TRUE;
}

/**
 * Copy real Kerberos credentials from a source cache to an in-memory cache.
 * in-memory cache.  Used to move OneKDC-generated TGTs to an in-memory cache
 * for testing.
 */
JNIEXPORT jboolean JNICALL Java_NativeCredentialCacheHelper_copyCredentialsToInMemoryCache
  (JNIEnv *env, jclass cls, jstring inMemoryCacheName, jstring sourceCacheName)
{
    krb5_error_code ret;
    krb5_ccache source_ccache = NULL;
    krb5_ccache in_memory_ccache = NULL;
    krb5_cc_cursor cursor;
    krb5_creds creds;
    char *in_memory_cache_name = NULL;
    char *source_cache_name = NULL;
    int copied_count = 0;

    ret = ensure_context();
    if (ret) {
        print_krb5_error("ensure_context", ret);
        return JNI_FALSE;
    }

    // Convert Java strings
    in_memory_cache_name = jstring_to_cstring(env, inMemoryCacheName);
    if (sourceCacheName != NULL) {
        source_cache_name = jstring_to_cstring(env, sourceCacheName);
    }

    if (!in_memory_cache_name) {
        printf("Failed to get in-memory cache name\n");
        goto cleanup;
    }

    printf("Copying credentials to in-memory cache: %s from source: %s\n",
        in_memory_cache_name,
        source_cache_name ? source_cache_name : "default cache"
    );

    // Open source cache (or default cache if sourceCacheName is null)
    if (source_cache_name) {
        ret = krb5_cc_resolve(g_context, source_cache_name, &source_ccache);
        if (ret) {
            print_krb5_error("krb5_cc_resolve (source)", ret);
            goto cleanup;
        }
    } else {
        ret = krb5_cc_default(g_context, &source_ccache);
        if (ret) {
            print_krb5_error("krb5_cc_default", ret);
            goto cleanup;
        }
    }

    // Open/resolve memory cache
    ret = krb5_cc_resolve(g_context, in_memory_cache_name, &in_memory_ccache);
    if (ret) {
        print_krb5_error("krb5_cc_resolve (in-memory)", ret);
        goto cleanup;
    }

    // Get principal from source cache for initialization
    krb5_principal principal = NULL;
    ret = krb5_cc_get_principal(g_context, source_ccache, &principal);
    if (ret) {
        print_krb5_error("krb5_cc_get_principal", ret);
        goto cleanup;
    }

    // Initialize in-memory cache with the principal
    ret = krb5_cc_initialize(g_context, in_memory_ccache, principal);
    if (ret) {
        print_krb5_error("krb5_cc_initialize", ret);
        krb5_free_principal(g_context, principal);
        goto cleanup;
    }

    // Start credential cursor on source cache
    ret = krb5_cc_start_seq_get(g_context, source_ccache, &cursor);
    if (ret) {
        print_krb5_error("krb5_cc_start_seq_get", ret);
        krb5_free_principal(g_context, principal);
        goto cleanup;
    }

    // Copy each credential from source to memory cache
    while ((ret = krb5_cc_next_cred(g_context, source_ccache, &cursor, &creds)) == 0) {
        ret = krb5_cc_store_cred(g_context, in_memory_ccache, &creds);
        if (ret) {
            print_krb5_error("krb5_cc_store_cred", ret);
            krb5_free_cred_contents(g_context, &creds);
            break;
        }

        printf("Copied in-memory credential: %s -> %s\n",
               creds.client ? "client" : "unknown",
               creds.server ? "server" : "unknown");

        copied_count++;
        krb5_free_cred_contents(g_context, &creds);
    }

    // End the cursor (expected to return KRB5_CC_END)
    krb5_cc_end_seq_get(g_context, source_ccache, &cursor);

    // Success if we copied at least one credential
    if (copied_count > 0) {
        printf("Successfully copied %d credentials to in-memory cache: %s\n",
               copied_count, in_memory_cache_name);
        ret = 0;
    } else {
        printf("No credentials found in source cache to copy to in-memory cache\n");
        ret = KRB5_CC_NOTFOUND;
    }

    krb5_free_principal(g_context, principal);

cleanup:
    if (source_ccache) krb5_cc_close(g_context, source_ccache);
    if (in_memory_ccache) krb5_cc_close(g_context, in_memory_ccache);
    if (in_memory_cache_name) free(in_memory_cache_name);
    if (source_cache_name) free(source_cache_name);

    return (ret == 0) ? JNI_TRUE : JNI_FALSE;
}
