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

#include "Windows.h"
#include "Utils.h"

#define BUFFER_SIZE 4096

wstring GetStringFromJString(JNIEnv *pEnv, jstring jstr) {
    const jchar *pJChars = pEnv->GetStringChars(jstr, NULL);
    if (pJChars == NULL) {
        return wstring(L"");
    }

    wstring wstr(pJChars);

    pEnv->ReleaseStringChars(jstr, pJChars);

    return wstr;
}

jstring GetJStringFromString(JNIEnv *pEnv,
            const jchar *unicodeChars, jsize len) {
    return pEnv->NewString(unicodeChars, len);
}

wstring GetLongPath(wstring path) {
    wstring result(L"");

    size_t len = path.length();
    if (len > 1) {
        if (path.at(len - 1) == '\\') {
            path.erase(len - 1);
        }
    }

    TCHAR *pBuffer = new TCHAR[BUFFER_SIZE];
    if (pBuffer != NULL) {
        DWORD dwResult = GetLongPathName(path.c_str(), pBuffer, BUFFER_SIZE);
        if (dwResult > 0 && dwResult < BUFFER_SIZE) {
            result = wstring(pBuffer);
        } else {
            delete [] pBuffer;
            pBuffer = new TCHAR[dwResult];
            if (pBuffer != NULL) {
                DWORD dwResult2 =
                        GetLongPathName(path.c_str(), pBuffer, dwResult);
                if (dwResult2 == (dwResult - 1)) {
                    result = wstring(pBuffer);
                }
            }
        }

        if (pBuffer != NULL) {
            delete [] pBuffer;
        }
    }

    return result;
}
