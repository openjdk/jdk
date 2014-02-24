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
#include <assert.h>

#include <dlfcn.h>

#include <winscard.h>

#include "sun_security_smartcardio_PlatformPCSC.h"

#include "pcsc_md.h"

void *hModule;
FPTR_SCardEstablishContext scardEstablishContext;
FPTR_SCardConnect scardConnect;
FPTR_SCardDisconnect scardDisconnect;
FPTR_SCardStatus scardStatus;
FPTR_SCardGetStatusChange scardGetStatusChange;
FPTR_SCardTransmit scardTransmit;
FPTR_SCardListReaders scardListReaders;
FPTR_SCardBeginTransaction scardBeginTransaction;
FPTR_SCardEndTransaction scardEndTransaction;
FPTR_SCardControl scardControl;

/*
 * Throws a Java Exception by name
 */
void throwByName(JNIEnv *env, const char *name, const char *msg)
{
    jclass cls = (*env)->FindClass(env, name);

    if (cls != 0) /* Otherwise an exception has already been thrown */
        (*env)->ThrowNew(env, cls, msg);
}

/*
 * Throws java.lang.NullPointerException
 */
void throwNullPointerException(JNIEnv *env, const char *msg)
{
    throwByName(env, "java/lang/NullPointerException", msg);
}

/*
 * Throws java.io.IOException
 */
void throwIOException(JNIEnv *env, const char *msg)
{
    throwByName(env, "java/io/IOException", msg);
}

void *findFunction(JNIEnv *env, void *hModule, char *functionName) {
    void *fAddress = dlsym(hModule, functionName);
    if (fAddress == NULL) {
        char errorMessage[256];
        snprintf(errorMessage, sizeof(errorMessage), "Symbol not found: %s", functionName);
        throwNullPointerException(env, errorMessage);
        return NULL;
    }
    return fAddress;
}

JNIEXPORT void JNICALL Java_sun_security_smartcardio_PlatformPCSC_initialize
        (JNIEnv *env, jclass thisClass, jstring jLibName) {
    const char *libName = (*env)->GetStringUTFChars(env, jLibName, NULL);
    hModule = dlopen(libName, RTLD_LAZY);
    (*env)->ReleaseStringUTFChars(env, jLibName, libName);

    if (hModule == NULL) {
        throwIOException(env, dlerror());
        return;
    }
    scardEstablishContext = (FPTR_SCardEstablishContext)findFunction(env, hModule, "SCardEstablishContext");
    scardConnect          = (FPTR_SCardConnect)         findFunction(env, hModule, "SCardConnect");
    scardDisconnect       = (FPTR_SCardDisconnect)      findFunction(env, hModule, "SCardDisconnect");
    scardStatus           = (FPTR_SCardStatus)          findFunction(env, hModule, "SCardStatus");
    scardGetStatusChange  = (FPTR_SCardGetStatusChange) findFunction(env, hModule, "SCardGetStatusChange");
    scardTransmit         = (FPTR_SCardTransmit)        findFunction(env, hModule, "SCardTransmit");
    scardListReaders      = (FPTR_SCardListReaders)     findFunction(env, hModule, "SCardListReaders");
    scardBeginTransaction = (FPTR_SCardBeginTransaction)findFunction(env, hModule, "SCardBeginTransaction");
    scardEndTransaction   = (FPTR_SCardEndTransaction)  findFunction(env, hModule, "SCardEndTransaction");
    scardControl          = (FPTR_SCardControl)         findFunction(env, hModule, "SCardControl");
}
