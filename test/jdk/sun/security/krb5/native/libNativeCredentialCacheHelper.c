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
 * Creates an in-memory credential cache, copies credentials from a file cache,
 * and sets it as the default cache in one atomic operation.
 */
JNIEXPORT jboolean JNICALL Java_NativeCredentialCacheHelper_createInMemoryCacheFromFileCache
  (JNIEnv *env, jclass cls, jstring inMemoryCacheName, jstring fileCacheName)
{
    krb5_error_code ret;
    krb5_ccache file_ccache = NULL;
    krb5_ccache in_memory_ccache = NULL;
    krb5_cc_cursor cursor;
    krb5_creds creds;
    char *in_memory_cache_name = NULL;
    char *file_cache_name = NULL;
    int copied_count = 0;

    ret = ensure_context();
    if (ret) {
        print_krb5_error("ensure_context", ret);
        return JNI_FALSE;
    }

    in_memory_cache_name = jstring_to_cstring(env, inMemoryCacheName);
    file_cache_name = jstring_to_cstring(env, fileCacheName);
    if (!in_memory_cache_name || !file_cache_name) {
        printf("Failed to get file or in-memory cache names\n");
        goto cleanup;
    }

    printf("Creating in-memory cache: %s from file cache: %s\n",
        in_memory_cache_name, file_cache_name);

    // Resolve FILE: ccache
    ret = krb5_cc_resolve(g_context, file_cache_name, &file_ccache);
    if (ret) {
        print_krb5_error("krb5_cc_resolve (file cache)", ret);
        printf("ERROR: File cache does not exist or cannot be accessed: %s\n", file_cache_name);
        goto cleanup;
    }

    // Resolve in-memory cache
    ret = krb5_cc_resolve(g_context, in_memory_cache_name, &in_memory_ccache);
    if (ret) {
        print_krb5_error("krb5_cc_resolve (in-memory)", ret);
        goto cleanup;
    }

    printf("Created in-memory cache: %s\n", in_memory_cache_name);

    // Get principal from file cache for initialization
    krb5_principal principal = NULL;
    ret = krb5_cc_get_principal(g_context, file_ccache, &principal);
    if (ret) {
        print_krb5_error("krb5_cc_get_principal", ret);
        printf("ERROR: Cannot get principal from file cache: %s\n", file_cache_name);
        goto cleanup;
    }

    // Initialize in-memory cache with the principal
    ret = krb5_cc_initialize(g_context, in_memory_ccache, principal);
    if (ret) {
        print_krb5_error("krb5_cc_initialize", ret);
        krb5_free_principal(g_context, principal);
        goto cleanup;
    }

    // Copy credentials from file cache to in-memory cache
    ret = krb5_cc_start_seq_get(g_context, file_ccache, &cursor);
    if (ret) {
        print_krb5_error("krb5_cc_start_seq_get", ret);
        krb5_free_principal(g_context, principal);
        goto cleanup;
    }

    while ((ret = krb5_cc_next_cred(g_context, file_ccache, &cursor, &creds)) == 0) {
        ret = krb5_cc_store_cred(g_context, in_memory_ccache, &creds);
        if (ret) {
            print_krb5_error("krb5_cc_store_cred", ret);
            krb5_free_cred_contents(g_context, &creds);
            break;
        }

        // Print the actual credential names
        char *client_name = NULL;
        char *server_name = NULL;
        if (creds.client) {
            krb5_unparse_name(g_context, creds.client, &client_name);
        }
        if (creds.server) {
            krb5_unparse_name(g_context, creds.server, &server_name);
        }

        printf("Copied credential: %s -> %s\n",
               client_name ? client_name : "unknown",
               server_name ? server_name : "unknown");

        if (client_name) krb5_free_unparsed_name(g_context, client_name);
        if (server_name) krb5_free_unparsed_name(g_context, server_name);

        copied_count++;
        krb5_free_cred_contents(g_context, &creds);
    }

    // End the cursor (expected to return KRB5_CC_END)
    krb5_cc_end_seq_get(g_context, file_ccache, &cursor);

    if (copied_count == 0) {
        printf("ERROR: No credentials found in file cache to copy: %s\n", file_cache_name);
        ret = KRB5_CC_NOTFOUND;
        krb5_free_principal(g_context, principal);
        goto cleanup;
    }

    printf("Successfully copied %d credentials to in-memory cache: %s\n",
           copied_count, in_memory_cache_name);

    // Set KRB5CCNAME environment variable to point to in-memory cache
    if (setenv("KRB5CCNAME", in_memory_cache_name, 1) != 0) {
        printf("ERROR: Failed to set KRB5CCNAME environment variable\n");
        ret = -1;
        krb5_free_principal(g_context, principal);
        goto cleanup;
    }

    printf("Set KRB5CCNAME to: %s\n", in_memory_cache_name);
    ret = 0;
    krb5_free_principal(g_context, principal);

cleanup:
    if (file_ccache) krb5_cc_close(g_context, file_ccache);
    if (file_cache_name) free(file_cache_name);

    if (in_memory_ccache) krb5_cc_close(g_context, in_memory_ccache);
    if (in_memory_cache_name) free(in_memory_cache_name);

    return (ret == 0) ? JNI_TRUE : JNI_FALSE;
}
