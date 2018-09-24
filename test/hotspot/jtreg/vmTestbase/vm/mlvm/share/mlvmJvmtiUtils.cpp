/*
 * Copyright (c) 2010, 2018, Oracle and/or its affiliates. All rights reserved.
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

#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include "jvmti.h"
#include "agent_common.h"
#include "JVMTITools.h"
#include "jvmti_tools.h"
#include "mlvmJvmtiUtils.h"

extern "C" {

void copyFromJString(JNIEnv * pEnv, jstring src, char ** dst) {
    const char * pStr;
        jsize len;

    if ( ! NSK_VERIFY((pStr = NSK_CPP_STUB3(GetStringUTFChars, pEnv, src, NULL)) != NULL) ) {
        return;
    }

    len = NSK_CPP_STUB2(GetStringUTFLength, pEnv, src) + 1;
    *dst = (char*) malloc(len);
    strncpy(*dst, pStr, len);

    NSK_CPP_STUB3(ReleaseStringUTFChars, pEnv, src, pStr);
}

struct MethodName * getMethodName(jvmtiEnv * pJvmtiEnv, jmethodID method) {
    char * szName;
    char * szSignature;
    jclass clazz;
    struct MethodName * mn;

    if ( ! NSK_JVMTI_VERIFY(NSK_CPP_STUB5(GetMethodName, pJvmtiEnv, method, &szName, NULL, NULL)) ) {
        return NULL;
    }

    if ( ! NSK_JVMTI_VERIFY(NSK_CPP_STUB3(GetMethodDeclaringClass, pJvmtiEnv, method, &clazz)) ) {
        NSK_JVMTI_VERIFY(NSK_CPP_STUB2(Deallocate, pJvmtiEnv, (unsigned char*) szName));
        return NULL;
    }

    if ( ! NSK_JVMTI_VERIFY(NSK_CPP_STUB4(GetClassSignature, pJvmtiEnv, clazz, &szSignature, NULL)) ) {
        NSK_JVMTI_VERIFY(NSK_CPP_STUB2(Deallocate, pJvmtiEnv, (unsigned char*) szName));
        return NULL;
    }

    mn = (MethodName*) malloc(sizeof(MethodNameStruct));
    strncpy(mn->methodName, szName, sizeof(mn->methodName));
    strncpy(mn->classSig, szSignature, sizeof(mn->classSig));

    NSK_JVMTI_VERIFY(NSK_CPP_STUB2(Deallocate, pJvmtiEnv, (unsigned char*) szName));
    NSK_JVMTI_VERIFY(NSK_CPP_STUB2(Deallocate, pJvmtiEnv, (unsigned char*) szSignature));
    return mn;
}

char * locationToString(jvmtiEnv * pJvmtiEnv, jmethodID method, jlocation location) {
    struct MethodName * pMN;
    int len;
    char * result;
    const char * const format = "%s .%s :" JLONG_FORMAT;

    pMN = getMethodName(pJvmtiEnv, method);
    if ( ! pMN )
        return strdup("NONE");

    len = snprintf(NULL, 0, format, pMN->classSig, pMN->methodName, location) + 1;

    if (len <= 0) {
        free(pMN);
        return NULL;
    }

    result = (char*) malloc(len);
    if (result == NULL) {
        free(pMN);
        return NULL;
    }

    snprintf(result, len, format, pMN->classSig, pMN->methodName, location);

    free(pMN);
    return result;
}

void * getTLS(jvmtiEnv * pJvmtiEnv, jthread thread, jsize sizeToAllocate) {
    void * tls;
    if ( ! NSK_JVMTI_VERIFY(NSK_CPP_STUB3(GetThreadLocalStorage, pJvmtiEnv, thread, &tls)) )
        return NULL;

    if ( ! tls) {
        if ( ! NSK_VERIFY((tls = malloc(sizeToAllocate)) != NULL) )
            return NULL;

        memset(tls, 0, sizeToAllocate);

        if ( ! NSK_JVMTI_VERIFY(NSK_CPP_STUB3(SetThreadLocalStorage, pJvmtiEnv, thread, tls)) )
            return NULL;
    }

    return tls;
}

}
