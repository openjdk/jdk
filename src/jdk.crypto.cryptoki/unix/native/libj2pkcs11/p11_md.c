/*
 * Copyright (c) 2003, 2024, Oracle and/or its affiliates. All rights reserved.
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

/*
 * pkcs11wrapper.c
 * 18.05.2001
 *
 * This module contains the native functions of the Java to PKCS#11 interface
 * which are platform dependent. This includes loading a dynamic link library,
 * retrieving the function list and unloading the dynamic link library.
 *
 * @author Karl Scheibelhofer <Karl.Scheibelhofer@iaik.at>
 */

#include "pkcs11wrapper.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <assert.h>

#include <dlfcn.h>

#include <jni.h>

#include "sun_security_pkcs11_wrapper_PKCS11.h"

/*
 * Class:     sun_security_pkcs11_wrapper_PKCS11
 * Method:    connect
 * Signature: (Ljava/lang/String;)Lsun/security/pkcs11/wrapper/CK_VERSION;
 */
JNIEXPORT jobject JNICALL Java_sun_security_pkcs11_wrapper_PKCS11_connect
    (JNIEnv *env, jobject obj, jstring jPkcs11ModulePath,
    jstring jGetFunctionList) {

    void *hModule;
    int i;
    CK_ULONG ulCount = 0;
    CK_C_GetInterfaceList C_GetInterfaceList = NULL;
    CK_INTERFACE_PTR iList = NULL;
    CK_C_GetInterface C_GetInterface = NULL;
    CK_INTERFACE_PTR interface = NULL;
    CK_C_GetFunctionList C_GetFunctionList = NULL;
    CK_RV rv;
    ModuleData *moduleData = NULL;
    jobject globalPKCS11ImplementationReference;
    char *systemErrorMessage;
    char *exceptionMessage;
    const char *getFunctionListStr = NULL;

    const char *libraryNameStr = (*env)->GetStringUTFChars(env,
            jPkcs11ModulePath, 0);
    if (libraryNameStr == NULL) {
        return NULL;
    }
    TRACE1("Connect: connect to PKCS#11 module: %s ... ", libraryNameStr);

    /*
     * Load the PKCS #11 DLL
     */
#ifdef DEBUG
    hModule = dlopen(libraryNameStr, RTLD_NOW);
#else
    hModule = dlopen(libraryNameStr, RTLD_LAZY);
#endif /* DEBUG */

    if (hModule == NULL) {
        systemErrorMessage = dlerror();
        exceptionMessage = (char *) malloc(sizeof(char) * (strlen(systemErrorMessage) + strlen(libraryNameStr) + 1));
        if (exceptionMessage == NULL) {
            p11ThrowOutOfMemoryError(env, 0);
            goto cleanup;
        }
        strcpy(exceptionMessage, systemErrorMessage);
        strcat(exceptionMessage, libraryNameStr);
        p11ThrowIOException(env, exceptionMessage);
        free(exceptionMessage);
        goto cleanup;
    }

#ifdef DEBUG
    C_GetInterfaceList = (CK_C_GetInterfaceList) dlsym(hModule,
            "C_GetInterfaceList");
    if (C_GetInterfaceList != NULL) {
        TRACE0("Connect: Found C_GetInterfaceList func\n");
        rv = (C_GetInterfaceList)(NULL, &ulCount);
        if (rv == CKR_OK) {
            TRACE1("Connect: interface list size %ld \n", ulCount);
            // retrieve available interfaces and report their info
            iList = (CK_INTERFACE_PTR)
                malloc(ulCount*sizeof(CK_INTERFACE));
            rv = C_GetInterfaceList(iList, &ulCount);
            if (ckAssertReturnValueOK(env, rv) != CK_ASSERT_OK) {
                TRACE0("Connect: error polling interface list\n");
                goto cleanup;
            }
            for (i=0; i < (int)ulCount; i++) {
                TRACE4("Connect: name %s, version %d.%d, flags 0x%lX\n",
                        iList[i].pInterfaceName,
                        ((CK_VERSION *)iList[i].pFunctionList)->major,
                        ((CK_VERSION *)iList[i].pFunctionList)->minor,
                        iList[i].flags);
            }
        } else {
            TRACE0("Connect: error polling interface list size\n");
        }
    } else {
        TRACE0("Connect: No C_GetInterfaceList func\n");
    }
#endif

    // if none specified, then we try 3.0 API first before trying 2.40
    if (jGetFunctionList == NULL) {
        C_GetInterface = (CK_C_GetInterface) dlsym(hModule, "C_GetInterface");
        if (C_GetInterface != NULL) {
            TRACE0("Connect: Found C_GetInterface func\n");
            rv = (C_GetInterface)(NULL, NULL, &interface, 0L);
            // don't use ckAssertReturnValueOK as we want to continue trying
            // C_GetFunctionList() or method named by "getFunctionListStr"
            if (rv == CKR_OK && interface != NULL) {
                goto setModuleData;
            }
        }
        getFunctionListStr = "C_GetFunctionList";
    } else {
        getFunctionListStr = (*env)->GetStringUTFChars(env,
            jGetFunctionList, 0);
        if (getFunctionListStr == NULL) {
            goto cleanup;
        }
    }

    dlerror(); // clear any old error message not fetched
    C_GetFunctionList = (CK_C_GetFunctionList) dlsym(hModule,
            getFunctionListStr);
    if (C_GetFunctionList == NULL) {
        if ((systemErrorMessage = dlerror()) != NULL){
            TRACE2("Connect: error finding %s func: %s\n", getFunctionListStr,
                systemErrorMessage);
            p11ThrowIOException(env, systemErrorMessage);
        } else {
            TRACE1("Connect: No %s func\n", getFunctionListStr);
            p11ThrowIOException(env, "ERROR: C_GetFunctionList == NULL");
        }
        goto cleanup;
    }
    TRACE1("Connect: Found %s func\n", getFunctionListStr);

setModuleData:
    /*
     * Get function pointers to all PKCS #11 functions
     */
    moduleData = (ModuleData *) malloc(sizeof(ModuleData));
    if (moduleData == NULL) {
        dlclose(hModule);
        p11ThrowOutOfMemoryError(env, 0);
        goto cleanup;
    }
    moduleData->hModule = hModule;
    moduleData->applicationMutexHandler = NULL;
    if (C_GetFunctionList != NULL) {
        rv = (C_GetFunctionList)(&(moduleData->ckFunctionListPtr));
        if (ckAssertReturnValueOK(env, rv) != CK_ASSERT_OK) {
            goto cleanup;
        }
    } else if (interface != NULL) {
        moduleData->ckFunctionListPtr = interface->pFunctionList;
    } else {
        // should never happen
        p11ThrowIOException(env, "ERROR: No function list ptr found");
        goto cleanup;
    }
    if (((CK_VERSION *)moduleData->ckFunctionListPtr)->major == 3 &&
            interface != NULL) {
        moduleData->ckFunctionList30Ptr = interface->pFunctionList;
    } else {
        moduleData->ckFunctionList30Ptr = NULL;
    }

    TRACE2("Connect: FunctionListPtr version = %d.%d\n",
        ((CK_VERSION *)moduleData->ckFunctionListPtr)->major,
        ((CK_VERSION *)moduleData->ckFunctionListPtr)->minor);

    globalPKCS11ImplementationReference = (*env)->NewGlobalRef(env, obj);
    putModuleEntry(env, globalPKCS11ImplementationReference, moduleData);

cleanup:
    if (jPkcs11ModulePath != NULL && libraryNameStr != NULL) {
        (*env)->ReleaseStringUTFChars(env, jPkcs11ModulePath, libraryNameStr);
    }
    if (jGetFunctionList != NULL && getFunctionListStr != NULL) {
        (*env)->ReleaseStringUTFChars(env, jGetFunctionList,
        getFunctionListStr);
    }
    TRACE0("Connect: FINISHED\n");
    if (moduleData != NULL) {
        return ckVersionPtrToJVersion(env,
                (CK_VERSION *)moduleData->ckFunctionListPtr);
    } else {
        return NULL;
    }
}


/*
 * Class:     sun_security_pkcs11_wrapper_PKCS11
 * Method:    disconnect
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_sun_security_pkcs11_wrapper_PKCS11_disconnect(
        JNIEnv *env, jclass thisClass, jlong ckpNativeData) {

    TRACE0("DEBUG: disconnecting module...");
    if (ckpNativeData != 0L) {
        ModuleData *moduleData = jlong_to_ptr(ckpNativeData);

        if (moduleData->hModule != NULL) {
            dlclose(moduleData->hModule);
        }

        free(moduleData);
    }

    TRACE0("FINISHED\n");
}
