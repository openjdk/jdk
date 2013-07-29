/*
 * Copyright (c) 2005, 2013, Oracle and/or its affiliates. All rights reserved.
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
#include <string.h>

// #define SECMOD_DEBUG

#include "j2secmod.h"


JNIEXPORT jboolean JNICALL Java_sun_security_pkcs11_Secmod_nssVersionCheck
  (JNIEnv *env, jclass thisClass, jlong jHandle, jstring jVersion)
{
    const char *requiredVersion = (*env)->GetStringUTFChars(env, jVersion, NULL);
    int res;
    FPTR_VersionCheck versionCheck =
        (FPTR_VersionCheck)findFunction(env, jHandle, "NSS_VersionCheck");

    if (versionCheck == NULL) {
        return JNI_FALSE;
    }

    res = versionCheck(requiredVersion);
    dprintf2("-version >=%s: %d\n", requiredVersion, res);
    (*env)->ReleaseStringUTFChars(env, jVersion, requiredVersion);

    return (res == 0) ? JNI_FALSE : JNI_TRUE;
}

/*
 * Initializes NSS.
 * The NSS_INIT_OPTIMIZESPACE flag is supplied by the caller.
 * The NSS_Init* functions are mapped to the NSS_Initialize function.
 */
JNIEXPORT jboolean JNICALL Java_sun_security_pkcs11_Secmod_nssInitialize
  (JNIEnv *env, jclass thisClass, jstring jFunctionName, jlong jHandle, jstring jConfigDir, jboolean jNssOptimizeSpace)
{
    const char *functionName =
        (*env)->GetStringUTFChars(env, jFunctionName, NULL);
    const char *configDir = (jConfigDir == NULL)
        ? NULL : (*env)->GetStringUTFChars(env, jConfigDir, NULL);
    FPTR_Initialize initialize =
        (FPTR_Initialize)findFunction(env, jHandle, "NSS_Initialize");
    int res = 0;
    unsigned int flags = 0x00;

    if (jNssOptimizeSpace == JNI_TRUE) {
        flags = 0x20; // NSS_INIT_OPTIMIZESPACE flag
    }

    if (initialize != NULL) {
        /*
         * If the NSS_Init function is requested then call NSS_Initialize to
         * open the Cert, Key and Security Module databases, read only.
         */
        if (strcmp("NSS_Init", functionName) == 0) {
            flags = flags | 0x01; // NSS_INIT_READONLY flag
            res = initialize(configDir, "", "", "secmod.db", flags);

        /*
         * If the NSS_InitReadWrite function is requested then call
         * NSS_Initialize to open the Cert, Key and Security Module databases,
         * read/write.
         */
        } else if (strcmp("NSS_InitReadWrite", functionName) == 0) {
            res = initialize(configDir, "", "", "secmod.db", flags);

        /*
         * If the NSS_NoDB_Init function is requested then call
         * NSS_Initialize without creating Cert, Key or Security Module
         * databases.
         */
        } else if (strcmp("NSS_NoDB_Init", functionName) == 0) {
            flags = flags | 0x02  // NSS_INIT_NOCERTDB flag
                          | 0x04  // NSS_INIT_NOMODDB flag
                          | 0x08  // NSS_INIT_FORCEOPEN flag
                          | 0x10; // NSS_INIT_NOROOTINIT flag
            res = initialize("", "", "", "", flags);

        } else {
            res = 2;
        }
    } else {
        res = 1;
    }
    (*env)->ReleaseStringUTFChars(env, jFunctionName, functionName);
    if (configDir != NULL) {
        (*env)->ReleaseStringUTFChars(env, jConfigDir, configDir);
    }
    dprintf1("-res: %d\n", res);

    return (res == 0) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jobject JNICALL Java_sun_security_pkcs11_Secmod_nssGetModuleList
  (JNIEnv *env, jclass thisClass, jlong jHandle, jstring jLibDir)
{
    FPTR_GetDBModuleList getModuleList =
        (FPTR_GetDBModuleList)findFunction(env, jHandle, "SECMOD_GetDefaultModuleList");

    SECMODModuleList *list;
    SECMODModule *module;
    jclass jListClass, jModuleClass;
    jobject jList, jModule;
    jmethodID jListConstructor, jAdd, jModuleConstructor;
    jstring jCommonName, jDllName;
    jboolean jFIPS;
    jint i;

    if (getModuleList == NULL) {
        dprintf("-getmodulelist function not found\n");
        return NULL;
    }
    list = getModuleList();
    if (list == NULL) {
        dprintf("-module list is null\n");
        return NULL;
    }

    jListClass = (*env)->FindClass(env, "java/util/ArrayList");
    jListConstructor = (*env)->GetMethodID(env, jListClass, "<init>", "()V");
    jAdd = (*env)->GetMethodID(env, jListClass, "add", "(Ljava/lang/Object;)Z");
    jList = (*env)->NewObject(env, jListClass, jListConstructor);

    jModuleClass = (*env)->FindClass(env, "sun/security/pkcs11/Secmod$Module");
    jModuleConstructor = (*env)->GetMethodID(env, jModuleClass, "<init>",
        "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;ZI)V");

    while (list != NULL) {
        module = list->module;
        // assert module != null
        dprintf1("-commonname: %s\n", module->commonName);
        dprintf1("-dllname: %s\n", (module->dllName != NULL) ? module->dllName : "NULL");
        dprintf1("-slots: %d\n", module->slotCount);
        dprintf1("-loaded: %d\n", module->loaded);
        dprintf1("-internal: %d\n", module->internal);
        dprintf1("-fips: %d\n", module->isFIPS);
        jCommonName = (*env)->NewStringUTF(env, module->commonName);
        if (module->dllName == NULL) {
            jDllName = NULL;
        } else {
            jDllName = (*env)->NewStringUTF(env, module->dllName);
        }
        jFIPS = module->isFIPS;
        for (i = 0; i < module->slotCount; i++ ) {
            jModule = (*env)->NewObject(env, jModuleClass, jModuleConstructor,
                jLibDir, jDllName, jCommonName, jFIPS, i);
            (*env)->CallVoidMethod(env, jList, jAdd, jModule);
        }
        list = list->next;
    }
    dprintf("-ok\n");

    return jList;
}
