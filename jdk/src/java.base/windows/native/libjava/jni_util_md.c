/*
 * Copyright (c) 2004, 2014 Oracle and/or its affiliates. All rights reserved.
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
#include <string.h>
#include <windows.h>
#include <locale.h>

#include "jni.h"
#include "jni_util.h"

static void getParent(const TCHAR *path, TCHAR *dest) {
    char* lastSlash = max(strrchr(path, '\\'), strrchr(path, '/'));
    if (lastSlash == NULL) {
        *dest = 0;
        return;
    }
    if (path != dest)
        strcpy(dest, path);
    *lastSlash = 0;
}

void* getProcessHandle() {
    return (void*)GetModuleHandle(NULL);
}

/*
 * Windows symbols can be simple like JNI_OnLoad or __stdcall format
 * like _JNI_OnLoad@8. We need to handle both.
 */
void buildJniFunctionName(const char *sym, const char *cname,
                          char *jniEntryName) {
    if (cname != NULL) {
        char *p = strrchr(sym, '@');
        if (p != NULL && p != sym) {
            // sym == _JNI_OnLoad@8
            strncpy(jniEntryName, sym, (p - sym));
            jniEntryName[(p-sym)] = '\0';
            // jniEntryName == _JNI_OnLoad
            strcat(jniEntryName, "_");
            strcat(jniEntryName, cname);
            strcat(jniEntryName, p);
            //jniEntryName == _JNI_OnLoad_cname@8
        } else {
            strcpy(jniEntryName, sym);
            strcat(jniEntryName, "_");
            strcat(jniEntryName, cname);
        }
    } else {
        strcpy(jniEntryName, sym);
    }
    return;
}

size_t
getLastErrorString(char *utf8_jvmErrorMsg, size_t cbErrorMsg)
{
    size_t n = 0;
    if (cbErrorMsg > 0) {
        BOOLEAN noError = FALSE;
        WCHAR *utf16_osErrorMsg = (WCHAR *)malloc(cbErrorMsg*sizeof(WCHAR));
        if (utf16_osErrorMsg == NULL) {
            // OOM accident
            strncpy(utf8_jvmErrorMsg, "Out of memory", cbErrorMsg);
            // truncate if too long
            utf8_jvmErrorMsg[cbErrorMsg - 1] = '\0';
            n = strlen(utf8_jvmErrorMsg);
        } else {
            DWORD errval = GetLastError();
            if (errval != 0) {
                // WIN32 error
                n = (size_t)FormatMessageW(
                    FORMAT_MESSAGE_FROM_SYSTEM|FORMAT_MESSAGE_IGNORE_INSERTS,
                    NULL,
                    errval,
                    0,
                    utf16_osErrorMsg,
                    (DWORD)cbErrorMsg,
                    NULL);
                if (n > 3) {
                    // Drop final '.', CR, LF
                    if (utf16_osErrorMsg[n - 1] == L'\n') --n;
                    if (utf16_osErrorMsg[n - 1] == L'\r') --n;
                    if (utf16_osErrorMsg[n - 1] == L'.') --n;
                    utf16_osErrorMsg[n] = L'\0';
                }
            } else if (errno != 0) {
                // C runtime error that has no corresponding WIN32 error code
                const WCHAR *rtError = _wcserror(errno);
                if (rtError != NULL) {
                    wcsncpy(utf16_osErrorMsg, rtError, cbErrorMsg);
                    // truncate if too long
                    utf16_osErrorMsg[cbErrorMsg - 1] = L'\0';
                    n = wcslen(utf16_osErrorMsg);
                }
            } else
                noError = TRUE; //OS has no error to report

            if (!noError) {
                if (n > 0) {
                    n = WideCharToMultiByte(
                        CP_UTF8,
                        0,
                        utf16_osErrorMsg,
                        n,
                        utf8_jvmErrorMsg,
                        cbErrorMsg,
                        NULL,
                        NULL);

                    // no way to die
                    if (n > 0)
                        utf8_jvmErrorMsg[min(cbErrorMsg - 1, n)] = '\0';
                }

                if (n <= 0) {
                    strncpy(utf8_jvmErrorMsg, "Secondary error while OS message extraction", cbErrorMsg);
                    // truncate if too long
                    utf8_jvmErrorMsg[cbErrorMsg - 1] = '\0';
                    n = strlen(utf8_jvmErrorMsg);
                }
            }
            free(utf16_osErrorMsg);
        }
    }
    return n;
}
