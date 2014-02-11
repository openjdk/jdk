/*
 * Copyright (c) 2000, 2002, Oracle and/or its affiliates. All rights reserved.
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

#include <stdlib.h>
#include <jni.h>
#include <windows.h>
#ifdef __cplusplus
extern "C" {
#endif
    JNIEXPORT jintArray JNICALL Java_java_util_prefs_WindowsPreferences_WindowsRegOpenKey
               (JNIEnv* env, jclass this_class, jint hKey, jbyteArray lpSubKey, jint securityMask) {
        HKEY handle;
        char* str;
        int tmp[2];
        int errorCode=-1;
        jintArray result;
        str = (*env)->GetByteArrayElements(env, lpSubKey, NULL);
        errorCode =  RegOpenKeyEx((HKEY)hKey, str, 0, securityMask, &handle);
        (*env)->ReleaseByteArrayElements(env, lpSubKey, str, 0);
        tmp[0]= (int) handle;
        tmp[1]= errorCode;
        result = (*env)->NewIntArray(env,2);
        (*env)->SetIntArrayRegion(env, result, 0, 2, tmp);
        return result;
    }

    JNIEXPORT jint JNICALL Java_java_util_prefs_WindowsPreferences_WindowsRegCloseKey
               (JNIEnv* env, jclass this_class, jint hKey) {
        return (jint) RegCloseKey((HKEY) hKey);
    };

    JNIEXPORT jintArray JNICALL Java_java_util_prefs_WindowsPreferences_WindowsRegCreateKeyEx
               (JNIEnv* env, jclass this_class, jint hKey, jbyteArray lpSubKey) {
        HKEY handle;
        char* str;
        int tmp[3];
        DWORD lpdwDisposition;
        int errorCode;
        jintArray result;
        str = (*env)->GetByteArrayElements(env, lpSubKey, NULL);
        errorCode =  RegCreateKeyEx((HKEY)hKey, str, 0, NULL,
                      REG_OPTION_NON_VOLATILE, KEY_READ,
                      NULL, &handle, &lpdwDisposition);
        (*env)->ReleaseByteArrayElements(env, lpSubKey, str, 0);
        tmp[0]= (int) handle;
        tmp[1]= errorCode;
        tmp[2]= lpdwDisposition;
        result = (*env)->NewIntArray(env,3);
        (*env)->SetIntArrayRegion(env, result, 0, 3, tmp);
        return result;
    }

    JNIEXPORT jint JNICALL Java_java_util_prefs_WindowsPreferences_WindowsRegDeleteKey
              (JNIEnv* env, jclass this_class, jint hKey, jbyteArray lpSubKey) {
        char* str;
        int result;
        str = (*env)->GetByteArrayElements(env, lpSubKey, NULL);
        result = RegDeleteKey((HKEY)hKey, str);
        (*env)->ReleaseByteArrayElements(env, lpSubKey, str, 0);
        return  result;

    };

    JNIEXPORT jint JNICALL Java_java_util_prefs_WindowsPreferences_WindowsRegFlushKey
        (JNIEnv* env, jclass this_class, jint hKey) {
        return RegFlushKey ((HKEY)hKey);
        }

    JNIEXPORT jbyteArray JNICALL Java_java_util_prefs_WindowsPreferences_WindowsRegQueryValueEx
         (JNIEnv* env, jclass this_class, jint hKey, jbyteArray valueName) {
        char* valueNameStr;
        char* buffer;
        jbyteArray result;
        DWORD valueType;
        DWORD valueSize;
        valueNameStr = (*env)->GetByteArrayElements(env, valueName, NULL);
        if (RegQueryValueEx((HKEY)hKey, valueNameStr, NULL, &valueType, NULL,
                                                 &valueSize) != ERROR_SUCCESS) {
        (*env)->ReleaseByteArrayElements(env, valueName, valueNameStr, 0);
        return NULL;
        }

        buffer = (char*)malloc(valueSize);

        if (RegQueryValueEx((HKEY)hKey, valueNameStr, NULL, &valueType, buffer,
            &valueSize) != ERROR_SUCCESS) {
            free(buffer);
            (*env)->ReleaseByteArrayElements(env, valueName, valueNameStr, 0);
        return NULL;
        }

        if (valueType == REG_SZ) {
        result = (*env)->NewByteArray(env, valueSize);
        (*env)->SetByteArrayRegion(env, result, 0, valueSize, buffer);
        } else {
        result = NULL;
        }
        free(buffer);
        (*env)->ReleaseByteArrayElements(env, valueName, valueNameStr, 0);
        return result;
    }




    JNIEXPORT jint JNICALL Java_java_util_prefs_WindowsPreferences_WindowsRegSetValueEx
    (JNIEnv* env, jclass this_class, jint hKey, jbyteArray valueName, jbyteArray data) {
        char* valueNameStr;
        char* dataStr;
        int size = -1;
        int nameSize = -1;
        int error_code = -1;
        if ((valueName == NULL)||(data == NULL)) {return -1;}
        size = (*env)->GetArrayLength(env, data);
        dataStr = (*env)->GetByteArrayElements(env, data, NULL);
        valueNameStr = (*env)->GetByteArrayElements(env, valueName, NULL);
        error_code = RegSetValueEx((HKEY)hKey, valueNameStr, 0,
                                                        REG_SZ, dataStr, size);
        (*env)->ReleaseByteArrayElements(env, data, dataStr, 0);
        (*env)->ReleaseByteArrayElements(env, valueName, valueNameStr, 0);
        return error_code;
    }

     JNIEXPORT jint JNICALL Java_java_util_prefs_WindowsPreferences_WindowsRegDeleteValue
            (JNIEnv* env, jclass this_class, jint hKey, jbyteArray valueName) {
        char* valueNameStr;
        int error_code = -1;
        if (valueName == NULL) {return -1;}
        valueNameStr = (*env)->GetByteArrayElements(env, valueName, NULL);
        error_code = RegDeleteValue((HKEY)hKey, valueNameStr);
        (*env)->ReleaseByteArrayElements(env, valueName, valueNameStr, 0);
        return error_code;
     }

    JNIEXPORT jintArray JNICALL Java_java_util_prefs_WindowsPreferences_WindowsRegQueryInfoKey
                                  (JNIEnv* env, jclass this_class, jint hKey) {
        jintArray result;
        int tmp[5];
        int valuesNumber = -1;
        int maxValueNameLength = -1;
        int maxSubKeyLength = -1;
        int subKeysNumber = -1;
        int errorCode = -1;
        errorCode = RegQueryInfoKey((HKEY)hKey, NULL, NULL, NULL,
                 &subKeysNumber, &maxSubKeyLength, NULL,
                 &valuesNumber, &maxValueNameLength,
                 NULL, NULL, NULL);
        tmp[0]= subKeysNumber;
        tmp[1]= (int)errorCode;
        tmp[2]= valuesNumber;
        tmp[3]= maxSubKeyLength;
        tmp[4]= maxValueNameLength;
        result = (*env)->NewIntArray(env,5);
        (*env)->SetIntArrayRegion(env, result, 0, 5, tmp);
        return result;
    }

     JNIEXPORT jbyteArray JNICALL Java_java_util_prefs_WindowsPreferences_WindowsRegEnumKeyEx
     (JNIEnv* env, jclass this_class, jint hKey , jint subKeyIndex, jint maxKeyLength) {
        int size = maxKeyLength;
        jbyteArray result;
        char* buffer = NULL;
        buffer = (char*)malloc(maxKeyLength);
        if (RegEnumKeyEx((HKEY) hKey, subKeyIndex, buffer, &size, NULL, NULL,
                                                 NULL, NULL) != ERROR_SUCCESS){
        free(buffer);
        return NULL;
        }
        result = (*env)->NewByteArray(env, size + 1);
        (*env)->SetByteArrayRegion(env, result, 0, size + 1, buffer);
        free(buffer);
        return result;
     }

     JNIEXPORT jbyteArray JNICALL Java_java_util_prefs_WindowsPreferences_WindowsRegEnumValue
          (JNIEnv* env, jclass this_class, jint hKey , jint valueIndex, jint maxValueNameLength){
          int size = maxValueNameLength;
          jbyteArray result;
          char* buffer = NULL;
          int error_code;
          buffer = (char*)malloc(maxValueNameLength);
          error_code = RegEnumValue((HKEY) hKey, valueIndex, buffer,
                                             &size, NULL, NULL, NULL, NULL);
          if (error_code!= ERROR_SUCCESS){
            free(buffer);
            return NULL;
          }
          result = (*env)->NewByteArray(env, size + 1);
          (*env)->SetByteArrayRegion(env, result, 0, size + 1, buffer);
          free(buffer);
          return result;
     }


#ifdef __cplusplus
}
#endif
