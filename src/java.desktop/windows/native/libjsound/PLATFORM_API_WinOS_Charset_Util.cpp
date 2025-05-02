/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates. All rights reserved.
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

#include "PLATFORM_API_WinOS_Charset_Util.h"

#include <cstring>

#ifdef __cplusplus
extern "C" {
#endif

LPSTR UnicodeToUTF8(const LPCWSTR lpUnicodeStr)
{
    DWORD dwUTF8Len = WideCharToMultiByte(CP_UTF8, 0, lpUnicodeStr, -1, nullptr, 0, nullptr, nullptr);
    LPSTR lpUTF8Str = new CHAR[dwUTF8Len];
    if (lpUTF8Str == NULL) return NULL;
    memset(lpUTF8Str, 0, sizeof(CHAR) * (dwUTF8Len));
    int nb = WideCharToMultiByte(CP_UTF8, 0, lpUnicodeStr, -1, lpUTF8Str, dwUTF8Len, nullptr, nullptr);
    if (nb > 0) {
        return lpUTF8Str;
    }
    delete[] lpUTF8Str;
    return NULL;
}

void UnicodeToUTF8AndCopy(LPSTR dest, LPCWSTR src, SIZE_T maxLength) {
    LPSTR utf8EncodedName = UnicodeToUTF8(src);
    if (utf8EncodedName != NULL) {
        strncpy(dest, utf8EncodedName, maxLength - 1);
        delete[] utf8EncodedName;
        dest[maxLength - 1] = '\0';
    } else {
        if (maxLength > 0) dest[0] = '\0';
    }
}

#ifdef __cplusplus
}
#endif
