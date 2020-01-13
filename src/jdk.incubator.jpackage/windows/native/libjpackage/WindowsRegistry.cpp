/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

#include <Windows.h>
#include <tchar.h>
#include <strsafe.h>
#include <jni.h>

#include "Utils.h"

// Max value name size per MSDN plus NULL
#define VALUE_NAME_SIZE 16384

#ifdef __cplusplus
extern "C" {
#endif
#undef jdk_incubator_jpackage_internal_WindowsRegistry_HKEY_LOCAL_MACHINE
#define jdk_incubator_jpackage_internal_WindowsRegistry_HKEY_LOCAL_MACHINE 1L

    /*
     * Class:     jdk_incubator_jpackage_internal_WindowsRegistry
     * Method:    readDwordValue
     * Signature: (ILjava/lang/String;Ljava/lang/String;I)I
     */
    JNIEXPORT jint JNICALL
            Java_jdk_incubator_jpackage_internal_WindowsRegistry_readDwordValue(
            JNIEnv *pEnv, jclass c, jint key, jstring jSubKey,
            jstring jValue, jint defaultValue) {
        jint jResult = defaultValue;

        if (key != jdk_incubator_jpackage_internal_WindowsRegistry_HKEY_LOCAL_MACHINE) {
            return jResult;
        }

        wstring subKey = GetStringFromJString(pEnv, jSubKey);
        wstring value = GetStringFromJString(pEnv, jValue);

        HKEY hSubKey = NULL;
        LSTATUS status = RegOpenKeyEx(HKEY_LOCAL_MACHINE, subKey.c_str(), 0,
                KEY_QUERY_VALUE, &hSubKey);
        if (status == ERROR_SUCCESS) {
            DWORD dwValue = 0;
            DWORD cbData = sizeof (DWORD);
            status = RegQueryValueEx(hSubKey, value.c_str(), NULL, NULL,
                    (LPBYTE) & dwValue, &cbData);
            if (status == ERROR_SUCCESS) {
                jResult = (jint) dwValue;
            }

            RegCloseKey(hSubKey);
        }

        return jResult;
    }

    /*
     * Class:     jdk_incubator_jpackage_internal_WindowsRegistry
     * Method:    openRegistryKey
     * Signature: (ILjava/lang/String;)J
     */
    JNIEXPORT jlong JNICALL
            Java_jdk_incubator_jpackage_internal_WindowsRegistry_openRegistryKey(
            JNIEnv *pEnv, jclass c, jint key, jstring jSubKey) {
        if (key != jdk_incubator_jpackage_internal_WindowsRegistry_HKEY_LOCAL_MACHINE) {
            return 0;
        }

        wstring subKey = GetStringFromJString(pEnv, jSubKey);
        HKEY hSubKey = NULL;
        LSTATUS status = RegOpenKeyEx(HKEY_LOCAL_MACHINE, subKey.c_str(), 0,
                KEY_QUERY_VALUE, &hSubKey);
        if (status == ERROR_SUCCESS) {
            return (jlong)hSubKey;
        }

        return 0;
    }

    /*
     * Class:     jdk_incubator_jpackage_internal_WindowsRegistry
     * Method:    enumRegistryValue
     * Signature: (JI)Ljava/lang/String;
     */
    JNIEXPORT jstring JNICALL
            Java_jdk_incubator_jpackage_internal_WindowsRegistry_enumRegistryValue(
            JNIEnv *pEnv, jclass c, jlong lKey, jint jIndex) {
        HKEY hKey = (HKEY)lKey;
        TCHAR valueName[VALUE_NAME_SIZE] = {0}; // Max size per MSDN plus NULL
        DWORD cchValueName = VALUE_NAME_SIZE;
        LSTATUS status = RegEnumValue(hKey, (DWORD)jIndex, valueName,
                &cchValueName, NULL, NULL, NULL, NULL);
        if (status == ERROR_SUCCESS) {
            size_t chLength = 0;
            if (StringCchLength(valueName, VALUE_NAME_SIZE, &chLength)
                    == S_OK) {
                return GetJStringFromString(pEnv, valueName, (jsize)chLength);
            }
        }

        return NULL;
    }

    /*
     * Class:     jdk_incubator_jpackage_internal_WindowsRegistry
     * Method:    closeRegistryKey
     * Signature: (J)V
     */
    JNIEXPORT void JNICALL
            Java_jdk_incubator_jpackage_internal_WindowsRegistry_closeRegistryKey(
            JNIEnv *pEnc, jclass c, jlong lKey) {
        HKEY hKey = (HKEY)lKey;
        RegCloseKey(hKey);
    }

    /*
     * Class:     jdk_incubator_jpackage_internal_WindowsRegistry
     * Method:    comparePaths
     * Signature: (Ljava/lang/String;Ljava/lang/String;)Z
     */
     JNIEXPORT jboolean JNICALL
            Java_jdk_incubator_jpackage_internal_WindowsRegistry_comparePaths(
            JNIEnv *pEnv, jclass c, jstring jPath1, jstring jPath2) {
         wstring path1 = GetStringFromJString(pEnv, jPath1);
         wstring path2 = GetStringFromJString(pEnv, jPath2);

         path1 = GetLongPath(path1);
         path2 = GetLongPath(path2);

         if (path1.length() == 0 || path2.length() == 0) {
             return JNI_FALSE;
         }

         if (path1.length() != path2.length()) {
             return JNI_FALSE;
         }

         if (_tcsnicmp(path1.c_str(), path2.c_str(), path1.length()) == 0) {
             return JNI_TRUE;
         }

         return JNI_FALSE;
     }

#ifdef __cplusplus
}
#endif
