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

#include <windows.h>

#include <jni.h>

#include "sun_security_pkcs11_wrapper_PKCS11.h"

/*
 * Class:     sun_security_pkcs11_wrapper_PKCS11
 * Method:    connect
 * Signature: (Ljava/lang/String;)Lsun/security/pkcs11/wrapper/CK_VERSION;
 */
JNIEXPORT jobject JNICALL Java_sun_security_pkcs11_wrapper_PKCS11_connect
    (JNIEnv *env, jobject obj, jstring jPkcs11ModulePath,
    jstring jGetFunctionList)
{
    HINSTANCE hModule;
    int i = 0;
    CK_ULONG ulCount = 0;
    CK_C_GetInterfaceList C_GetInterfaceList = NULL;
    CK_INTERFACE_PTR iList = NULL;
    CK_C_GetInterface C_GetInterface = NULL;
    CK_INTERFACE_PTR interface = NULL;
    CK_C_GetFunctionList C_GetFunctionList = NULL;
    CK_RV rv = CK_ASSERT_OK;
    ModuleData *moduleData = NULL;
    jobject globalPKCS11ImplementationReference;
    LPVOID lpMsgBuf = NULL;
    char *exceptionMessage = NULL;
    const char *getFunctionListStr;

    const char *libraryNameStr = (*env)->GetStringUTFChars(env,
            jPkcs11ModulePath, 0);
    TRACE1("DEBUG: connect to PKCS#11 module: %s ... ", libraryNameStr);

    /*
     * Load the PKCS #11 DLL
     */
    hModule = LoadLibrary(libraryNameStr);
    if (hModule == NULL) {
        FormatMessage(
            FORMAT_MESSAGE_ALLOCATE_BUFFER |
            FORMAT_MESSAGE_FROM_SYSTEM |
            FORMAT_MESSAGE_IGNORE_INSERTS,
            NULL,
            GetLastError(),
            0, /* Default language */
            (LPTSTR) &lpMsgBuf,
            0,
            NULL
        );
        exceptionMessage = (char *) malloc(sizeof(char) *
                (strlen((LPTSTR) lpMsgBuf) + strlen(libraryNameStr) + 1));
        if (exceptionMessage == NULL) {
            p11ThrowOutOfMemoryError(env, 0);
            goto cleanup;
        }
        strcpy(exceptionMessage, (LPTSTR) lpMsgBuf);
        strcat(exceptionMessage, libraryNameStr);
        p11ThrowIOException(env, (LPTSTR) exceptionMessage);
        goto cleanup;
    }

#ifdef DEBUG
    /*
     * Get function pointer to C_GetInterfaceList
     */
    C_GetInterfaceList = (CK_C_GetInterfaceList) GetProcAddress(hModule,
            "C_GetInterfaceList");
    if (C_GetInterfaceList != NULL) {
        TRACE0("Found C_GetInterfaceList func\n");
        rv = (C_GetInterfaceList)(NULL, &ulCount);
        if (rv == CKR_OK) {
            /* get copy of interfaces */
            iList = (CK_INTERFACE_PTR)
                    malloc(ulCount*sizeof(CK_INTERFACE));
            rv = C_GetInterfaceList(iList, &ulCount);
            for (i=0; i < (int)ulCount; i++) {
                printf("interface %s version %d.%d funcs %p flags 0x%lu\n",
                        iList[i].pInterfaceName,
                        ((CK_VERSION *)iList[i].pFunctionList)->major,
                        ((CK_VERSION *)iList[i].pFunctionList)->minor,
                        iList[i].pFunctionList, iList[i].flags);
            }
        } else {
            TRACE0("Connect: error polling interface list size\n");
        }
    } else {
        TRACE0("Connect: No C_GetInterfaceList func\n");
    }
#endif

    if (jGetFunctionList != NULL) {
        getFunctionListStr = (*env)->GetStringUTFChars(env,
                jGetFunctionList, 0);
        if (getFunctionListStr == NULL) {
            goto cleanup;
        }
        C_GetFunctionList = (CK_C_GetFunctionList) GetProcAddress(hModule,
                getFunctionListStr);
        if (C_GetFunctionList == NULL) {
            TRACE1("Connect: No %s func\n", getFunctionListStr);
            FormatMessage(
                FORMAT_MESSAGE_ALLOCATE_BUFFER |
                FORMAT_MESSAGE_FROM_SYSTEM |
                FORMAT_MESSAGE_IGNORE_INSERTS,
                NULL,
                GetLastError(),
                0, /* Default language */
                (LPTSTR) &lpMsgBuf,
                0,
                NULL
            );
            p11ThrowIOException(env, (LPTSTR) lpMsgBuf);
            goto cleanup;
        }
        TRACE1("Connect: Found %s func\n", getFunctionListStr);
    } else {
        // if none specified, then we try 3.0 API first before trying 2.40
        C_GetInterface = (CK_C_GetInterface) GetProcAddress(hModule,
            "C_GetInterface");
        if (C_GetInterface != NULL) {
            TRACE0("Connect: Found C_GetInterface func\n");
            rv = (C_GetInterface)(NULL, NULL, &interface, 0);
            if (rv == CKR_OK && interface != NULL) {
                goto setModuleData;
            }
        }
        C_GetFunctionList = (CK_C_GetFunctionList) GetProcAddress(hModule,
                "C_GetFunctionList");
        if (C_GetFunctionList == NULL) {
            TRACE0("Connect: No C_GetFunctionList func\n");
            FormatMessage(
                FORMAT_MESSAGE_ALLOCATE_BUFFER |
                FORMAT_MESSAGE_FROM_SYSTEM |
                FORMAT_MESSAGE_IGNORE_INSERTS,
                NULL,
                GetLastError(),
                0, /* Default language */
                (LPTSTR) &lpMsgBuf,
                0,
                NULL
            );
            p11ThrowIOException(env, (LPTSTR) lpMsgBuf);
            goto cleanup;
        }
        TRACE0("Connect: Found C_GetFunctionList func\n");
    }

setModuleData:
    /*
     * Get function pointers to all PKCS #11 functions
     */
    moduleData = (ModuleData *) malloc(sizeof(ModuleData));
    if (moduleData == NULL) {
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
    /* Free up allocated buffers we no longer need */
    if (lpMsgBuf != NULL) {
        LocalFree( lpMsgBuf );
    }
    if (libraryNameStr != NULL) {
        (*env)->ReleaseStringUTFChars(env, jPkcs11ModulePath, libraryNameStr);
    }
    if (jGetFunctionList != NULL && getFunctionListStr != NULL) {
        (*env)->ReleaseStringUTFChars(env, jGetFunctionList,
            getFunctionListStr);
    }
    if (exceptionMessage != NULL) {
        free(exceptionMessage);
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
            FreeLibrary(moduleData->hModule);
        }

        free(moduleData);
    }

    TRACE0("FINISHED\n");
}
