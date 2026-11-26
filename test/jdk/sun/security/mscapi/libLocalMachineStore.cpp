/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
#include <windows.h>
#include <wincrypt.h>

static void ThrowAssertionError(JNIEnv *env, const char *message, DWORD errorCode) {
    jclass errorClass = env->FindClass("java/lang/AssertionError");
    if (errorClass != nullptr) {
        char fullMessage[256];
        snprintf(fullMessage, sizeof(fullMessage), "%s: 0x%08lx", message, errorCode);
        env->ThrowNew(errorClass, fullMessage);
    }
}

extern "C" JNIEXPORT void JNICALL Java_LocalMachineStore_copyCertWithKeyAssociationToLocalMachine(
    JNIEnv *env, jclass clazz, jbyteArray encoded) {
    jsize len = env->GetArrayLength(encoded);
    jbyte* bytes = env->GetByteArrayElements(encoded, nullptr);
    if (bytes == nullptr) {
        return;
    }

    PCCERT_CONTEXT pCertContext = nullptr;
    HCERTSTORE userStore = nullptr;
    PCCERT_CONTEXT sourceContext = nullptr;
    HCERTSTORE machineStore = nullptr;

    __try {

        pCertContext = CertCreateCertificateContext(
            X509_ASN_ENCODING,
            (const BYTE*) bytes,
            (DWORD) len);

        DWORD lastError = GetLastError();
        env->ReleaseByteArrayElements(encoded, bytes, JNI_ABORT);
        if (pCertContext == nullptr) {
            ThrowAssertionError(env, "CertCreateCertificateContext failed", lastError);
            __leave;
        }

        userStore = CertOpenStore(
                CERT_STORE_PROV_SYSTEM_W,
                0,
                NULL,
                CERT_SYSTEM_STORE_CURRENT_USER | CERT_STORE_OPEN_EXISTING_FLAG,
                L"MY");
        if (userStore == nullptr) {
            ThrowAssertionError(env, "CertOpenStore for Current User failed", GetLastError());
            __leave;
        }

        sourceContext = CertFindCertificateInStore(
                userStore,
                X509_ASN_ENCODING | PKCS_7_ASN_ENCODING,
                0,
                CERT_FIND_EXISTING,
                pCertContext,
                nullptr);

        if (sourceContext == nullptr) {
            ThrowAssertionError(env, "CertFindCertificateInStore failed", GetLastError());
            __leave;
        }

        machineStore = CertOpenStore(
                CERT_STORE_PROV_SYSTEM_W,
                0,
                NULL,
                CERT_SYSTEM_STORE_LOCAL_MACHINE | CERT_STORE_OPEN_EXISTING_FLAG,
                L"MY");
        if (machineStore == nullptr) {
            ThrowAssertionError(env, "CertOpenStore for Local Machine failed", GetLastError());
            __leave;
        }

        if (!CertAddCertificateContextToStore(machineStore, sourceContext, CERT_STORE_ADD_REPLACE_EXISTING, nullptr)) {
            ThrowAssertionError(env, "CertAddCertificateContextToStore failed", GetLastError());
        }
    } __finally {
        if (pCertContext != nullptr) {
            CertFreeCertificateContext(pCertContext);
        }
        if (sourceContext != nullptr) {
            CertFreeCertificateContext(sourceContext);
        }
        if (userStore != nullptr) {
            CertCloseStore(userStore, 0);
        }
        if (machineStore != nullptr) {
            CertCloseStore(machineStore, 0);
        }
    }
}
