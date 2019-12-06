/*
 * Copyright (c) 2011, 2019, Oracle and/or its affiliates. All rights reserved.
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
#include <string>
#include <windows.h>

#include "ResourceEditor.h"
#include "WinErrorHandling.h"
#include "IconSwap.h"
#include "VersionInfoSwap.h"
#include "Utils.h"

using namespace std;

#ifdef __cplusplus
extern "C" {
#endif

    /*
     * Class:     jdk_incubator_jpackage_internal_WindowsAppImageBuilder
     * Method:    iconSwap
     * Signature: (Ljava/lang/String;Ljava/lang/String;)I
     */
    JNIEXPORT jint JNICALL
            Java_jdk_incubator_jpackage_internal_WindowsAppImageBuilder_iconSwap(
            JNIEnv *pEnv, jclass c, jstring jIconTarget, jstring jLauncher) {
        wstring iconTarget = GetStringFromJString(pEnv, jIconTarget);
        wstring launcher = GetStringFromJString(pEnv, jLauncher);

        if (ChangeIcon(iconTarget, launcher)) {
            return 0;
        }

        return 1;
    }

    /*
     * Class:     jdk_incubator_jpackage_internal_WindowsAppImageBuilder
     * Method:    versionSwap
     * Signature: (Ljava/lang/String;Ljava/lang/String;)I
     */
    JNIEXPORT jint JNICALL
            Java_jdk_incubator_jpackage_internal_WindowsAppImageBuilder_versionSwap(
            JNIEnv *pEnv, jclass c, jstring jExecutableProperties,
            jstring jLauncher) {

        wstring executableProperties = GetStringFromJString(pEnv,
                jExecutableProperties);
        wstring launcher = GetStringFromJString(pEnv, jLauncher);

        VersionInfoSwap vs(executableProperties, launcher);
        if (vs.PatchExecutable()) {
            return 0;
        }

        return 1;
    }

    /*
     * Class:     jdk_incubator_jpackage_internal_WinExeBundler
     * Method:    embedMSI
     * Signature: (Ljava/lang/String;Ljava/lang/String;)I
     */
    JNIEXPORT jint JNICALL Java_jdk_incubator_jpackage_internal_WinExeBundler_embedMSI(
            JNIEnv *pEnv, jclass c, jstring jexePath, jstring jmsiPath) {

        const wstring exePath = GetStringFromJString(pEnv, jexePath);
        const wstring msiPath = GetStringFromJString(pEnv, jmsiPath);

        JP_TRY;

        ResourceEditor()
            .id(L"msi")
            .type(RT_RCDATA)
            .apply(ResourceEditor::FileLock(exePath), msiPath);

        return 0;

        JP_CATCH_ALL;

        return 1;
    }

    BOOL WINAPI DllMain(HINSTANCE hinstDLL, DWORD fdwReason,
            LPVOID lpvReserved) {
        return TRUE;
    }

#ifdef __cplusplus
}
#endif
