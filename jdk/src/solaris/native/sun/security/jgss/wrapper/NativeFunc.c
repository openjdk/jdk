/*
 * Copyright (c) 2005, Oracle and/or its affiliates. All rights reserved.
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

#include <stdio.h>
#include <stdlib.h>
#include <dlfcn.h>
#include <link.h>
#include "NativeFunc.h"

/* standard GSS method names (ordering is from mapfile) */
static const char RELEASE_NAME[]                = "gss_release_name";
static const char IMPORT_NAME[]                 = "gss_import_name";
static const char COMPARE_NAME[]                = "gss_compare_name";
static const char CANONICALIZE_NAME[]           = "gss_canonicalize_name";
static const char EXPORT_NAME[]                 = "gss_export_name";
static const char DISPLAY_NAME[]                = "gss_display_name";
static const char ACQUIRE_CRED[]                = "gss_acquire_cred";
static const char RELEASE_CRED[]                = "gss_release_cred";
static const char INQUIRE_CRED[]                = "gss_inquire_cred";
static const char IMPORT_SEC_CONTEXT[]          = "gss_import_sec_context";
static const char INIT_SEC_CONTEXT[]            = "gss_init_sec_context";
static const char ACCEPT_SEC_CONTEXT[]          = "gss_accept_sec_context";
static const char INQUIRE_CONTEXT[]             = "gss_inquire_context";
static const char DELETE_SEC_CONTEXT[]          = "gss_delete_sec_context";
static const char CONTEXT_TIME[]                = "gss_context_time";
static const char WRAP_SIZE_LIMIT[]             = "gss_wrap_size_limit";
static const char EXPORT_SEC_CONTEXT[]          = "gss_export_sec_context";
static const char GET_MIC[]                     = "gss_get_mic";
static const char VERIFY_MIC[]                  = "gss_verify_mic";
static const char WRAP[]                        = "gss_wrap";
static const char UNWRAP[]                      = "gss_unwrap";
static const char INDICATE_MECHS[]              = "gss_indicate_mechs";
static const char INQUIRE_NAMES_FOR_MECH[]      = "gss_inquire_names_for_mech";

/* additional GSS methods not public thru mapfile */

static const char ADD_OID_SET_MEMBER[]          = "gss_add_oid_set_member";
static const char DISPLAY_STATUS[]              = "gss_display_status";
static const char CREATE_EMPTY_OID_SET[]        = "gss_create_empty_oid_set";
static const char RELEASE_OID_SET[]             = "gss_release_oid_set";
static const char RELEASE_BUFFER[]              = "gss_release_buffer";

/**
 * Initialize native GSS function pointers
 */
char* loadNative(const char *libName) {

    char *error;
    void *gssLib;
    int failed;
    OM_uint32 minor, major;

    ftab = NULL;
    failed = FALSE;
    error = NULL;

    gssLib = dlopen(libName, RTLD_NOW);
    if (gssLib == NULL) {
        failed = TRUE;
        goto out;
    }

    /* global function table instance */
    ftab = (GSS_FUNCTION_TABLE_PTR)malloc(sizeof(GSS_FUNCTION_TABLE));
    if (ftab == NULL) {
        failed = TRUE;
        goto out;
    }

    ftab->releaseName = (RELEASE_NAME_FN_PTR)dlsym(gssLib, RELEASE_NAME);
    if (ftab->releaseName == NULL) {
        failed = TRUE;
        goto out;
    }

    ftab->importName = (IMPORT_NAME_FN_PTR)dlsym(gssLib, IMPORT_NAME);
    if (ftab->importName == NULL) {
        failed = TRUE;
        goto out;
    }

    ftab->compareName = (COMPARE_NAME_FN_PTR)dlsym(gssLib, COMPARE_NAME);
    if (ftab->compareName == NULL) {
        failed = TRUE;
        goto out;
    }

    ftab->canonicalizeName = (CANONICALIZE_NAME_FN_PTR)
                                dlsym(gssLib, CANONICALIZE_NAME);
    if (ftab->canonicalizeName == NULL) {
        failed = TRUE;
        goto out;
    }

    ftab->exportName = (EXPORT_NAME_FN_PTR)dlsym(gssLib, EXPORT_NAME);
    if (ftab->exportName == NULL) {
        failed = TRUE;
        goto out;
    }

    ftab->displayName = (DISPLAY_NAME_FN_PTR)dlsym(gssLib, DISPLAY_NAME);
    if (ftab->displayName == NULL) {
        failed = TRUE;
        goto out;
    }

    ftab->acquireCred = (ACQUIRE_CRED_FN_PTR)dlsym(gssLib, ACQUIRE_CRED);
    if (ftab->acquireCred == NULL) {
        failed = TRUE;
        goto out;
    }

    ftab->releaseCred = (RELEASE_CRED_FN_PTR)dlsym(gssLib, RELEASE_CRED);
    if (ftab->releaseCred == NULL) {
        failed = TRUE;
        goto out;
    }

    ftab->inquireCred = (INQUIRE_CRED_FN_PTR)dlsym(gssLib, INQUIRE_CRED);
    if (ftab->inquireCred == NULL) {
        failed = TRUE;
        goto out;
    }

    ftab->importSecContext = (IMPORT_SEC_CONTEXT_FN_PTR)
                        dlsym(gssLib, IMPORT_SEC_CONTEXT);
    if (ftab->importSecContext == NULL) {
        failed = TRUE;
        goto out;
    }

    ftab->initSecContext = (INIT_SEC_CONTEXT_FN_PTR)
                        dlsym(gssLib, INIT_SEC_CONTEXT);
    if (ftab->initSecContext == NULL) {
        failed = TRUE;
        goto out;
    }

    ftab->acceptSecContext = (ACCEPT_SEC_CONTEXT_FN_PTR)
                        dlsym(gssLib, ACCEPT_SEC_CONTEXT);
    if (ftab->acceptSecContext == NULL) {
        failed = TRUE;
        goto out;
    }

    ftab->inquireContext = (INQUIRE_CONTEXT_FN_PTR)
                        dlsym(gssLib, INQUIRE_CONTEXT);
    if (ftab->inquireContext == NULL) {
        failed = TRUE;
        goto out;
    }

    ftab->deleteSecContext = (DELETE_SEC_CONTEXT_FN_PTR)
                        dlsym(gssLib, DELETE_SEC_CONTEXT);
    if (ftab->deleteSecContext == NULL) {
        failed = TRUE;
        goto out;
    }

    ftab->contextTime = (CONTEXT_TIME_FN_PTR)dlsym(gssLib, CONTEXT_TIME);
    if (ftab->contextTime == NULL) {
        failed = TRUE;
        goto out;
    }

    ftab->wrapSizeLimit = (WRAP_SIZE_LIMIT_FN_PTR)
                        dlsym(gssLib, WRAP_SIZE_LIMIT);
    if (ftab->wrapSizeLimit == NULL) {
        failed = TRUE;
        goto out;
    }

    ftab->exportSecContext = (EXPORT_SEC_CONTEXT_FN_PTR)
                        dlsym(gssLib, EXPORT_SEC_CONTEXT);
    if (ftab->exportSecContext == NULL) {
        failed = TRUE;
        goto out;
    }

    ftab->getMic = (GET_MIC_FN_PTR)dlsym(gssLib, GET_MIC);
    if (ftab->getMic == NULL) {
        failed = TRUE;
        goto out;
    }

    ftab->verifyMic = (VERIFY_MIC_FN_PTR)dlsym(gssLib, VERIFY_MIC);
    if (ftab->verifyMic == NULL) {
        failed = TRUE;
        goto out;
    }

    ftab->wrap = (WRAP_FN_PTR)dlsym(gssLib, WRAP);
    if (ftab->wrap == NULL) {
        failed = TRUE;
        goto out;
    }

    ftab->unwrap = (UNWRAP_FN_PTR)dlsym(gssLib, UNWRAP);
    if (ftab->unwrap == NULL) {
        failed = TRUE;
        goto out;
    }

    ftab->indicateMechs = (INDICATE_MECHS_FN_PTR)dlsym(gssLib, INDICATE_MECHS);
    if (ftab->indicateMechs == NULL) {
        failed = TRUE;
        goto out;
    }

    ftab->inquireNamesForMech = (INQUIRE_NAMES_FOR_MECH_FN_PTR)
                        dlsym(gssLib, INQUIRE_NAMES_FOR_MECH);
    if (ftab->inquireNamesForMech == NULL) {
        failed = TRUE;
        goto out;
    }

    ftab->addOidSetMember = (ADD_OID_SET_MEMBER_FN_PTR)
                        dlsym(gssLib, ADD_OID_SET_MEMBER);
    if (ftab->addOidSetMember == NULL) {
        failed = TRUE;
        goto out;
    }

    ftab->displayStatus = (DISPLAY_STATUS_FN_PTR)
                        dlsym(gssLib, DISPLAY_STATUS);
    if (ftab->displayStatus == NULL) {
        failed = TRUE;
        goto out;
    }

    ftab->createEmptyOidSet = (CREATE_EMPTY_OID_SET_FN_PTR)
                        dlsym(gssLib, CREATE_EMPTY_OID_SET);
    if (ftab->createEmptyOidSet == NULL) {
        failed = TRUE;
        goto out;
    }

    ftab->releaseOidSet = (RELEASE_OID_SET_FN_PTR)
                        dlsym(gssLib, RELEASE_OID_SET);
    if (ftab->releaseOidSet == NULL) {
        failed = TRUE;
        goto out;
    }

    ftab->releaseBuffer = (RELEASE_BUFFER_FN_PTR)
                        dlsym(gssLib, RELEASE_BUFFER);
    if (ftab->releaseBuffer == NULL) {
        failed = TRUE;
        goto out;
    }

    ftab->mechs = GSS_C_NO_OID_SET;
    major = (*ftab->indicateMechs)(&minor, &(ftab->mechs));
    if (ftab->mechs == NULL || ftab->mechs == GSS_C_NO_OID_SET) {
        failed = TRUE;
        goto out;
    }


out:
    if (failed == TRUE) {
        error = dlerror();
        if (gssLib != NULL) dlclose(gssLib);
        if (ftab != NULL) free(ftab);
    }
    return error;
}
