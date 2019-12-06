/*
 * Copyright (c) 2014, 2019, Oracle and/or its affiliates. All rights reserved.
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

#include "PlatformString.h"

#include "Helpers.h"

#include <stdio.h>
#include <stdlib.h>
#include <stdlib.h>
#include <memory.h>
#include <sstream>
#include <string.h>

#include "jni.h"

void PlatformString::initialize() {
    FWideTStringToFree = NULL;
    FLength = 0;
    FData = NULL;
}

PlatformString::PlatformString(void) {
    initialize();
}

PlatformString::~PlatformString(void) {
    if (FData != NULL) {
        delete[] FData;
    }

    if (FWideTStringToFree != NULL) {
        delete[] FWideTStringToFree;
    }
}

PlatformString::PlatformString(const PlatformString &value) {
    initialize();
    FLength = value.FLength;
    FData = new char[FLength + 1];
    Platform::CopyString(FData, FLength + 1, value.FData);
}

PlatformString::PlatformString(const char* value) {
    initialize();
    FLength = strlen(value);
    FData = new char[FLength + 1];
    Platform::CopyString(FData, FLength + 1, value);
}

PlatformString::PlatformString(size_t Value) {
    initialize();

    std::stringstream ss;
    std::string s;
    ss << Value;
    s = ss.str();

    FLength = strlen(s.c_str());
    FData = new char[FLength + 1];
    Platform::CopyString(FData, FLength + 1, s.c_str());
}

PlatformString::PlatformString(const wchar_t* value) {
    initialize();
    MultibyteString temp = Platform::WideStringToMultibyteString(value);
    FLength = temp.length;
    FData = temp.data;
}

PlatformString::PlatformString(const std::string &value) {
    initialize();
    const char* lvalue = value.data();
    FLength = value.size();
    FData = new char[FLength + 1];
    Platform::CopyString(FData, FLength + 1, lvalue);
}

PlatformString::PlatformString(const std::wstring &value) {
    initialize();
    const wchar_t* lvalue = value.data();
    MultibyteString temp = Platform::WideStringToMultibyteString(lvalue);
    FLength = temp.length;
    FData = temp.data;
}

TString PlatformString::Format(const TString value, ...) {
    TString result = value;

    va_list arglist;
    va_start(arglist, value);

    while (1) {
        size_t pos = result.find(_T("%s"), 0);

        if (pos == TString::npos) {
            break;
        }
        else {
            TCHAR* arg = va_arg(arglist, TCHAR*);

            if (arg == NULL) {
                break;
            }
            else {
                result.replace(pos, StringLength(_T("%s")), arg);
            }
        }
    }

    va_end(arglist);

    return result;
}

size_t PlatformString::length() {
    return FLength;
}

char* PlatformString::c_str() {
    return FData;
}

char* PlatformString::toMultibyte() {
    return FData;
}

wchar_t* PlatformString::toWideString() {
    WideString result = Platform::MultibyteStringToWideString(FData);

    if (result.data != NULL) {
        if (FWideTStringToFree != NULL) {
            delete [] FWideTStringToFree;
        }

        FWideTStringToFree = result.data;
    }

    return result.data;
}

std::wstring PlatformString::toUnicodeString() {
    std::wstring result;
    wchar_t* data = toWideString();

    if (FLength != 0 && data != NULL) {
        // NOTE: Cleanup of result is handled by PlatformString destructor.
        result = data;
    }

    return result;
}

std::string PlatformString::toStdString() {
    std::string result;
    char* data = toMultibyte();

    if (FLength > 0 && data != NULL) {
        result = data;
    }

    return result;
}

TCHAR* PlatformString::toPlatformString() {
#ifdef _UNICODE
    return toWideString();
#else
    return c_str();
#endif //_UNICODE
}

TString PlatformString::toString() {
#ifdef _UNICODE
    return toUnicodeString();
#else
    return toStdString();
#endif //_UNICODE
}

PlatformString::operator char* () {
    return c_str();
}

PlatformString::operator wchar_t* () {
    return toWideString();
}

PlatformString::operator std::wstring () {
    return toUnicodeString();
}

char* PlatformString::duplicate(const char* Value) {
    size_t length = strlen(Value);
    char* result = new char[length + 1];
    Platform::CopyString(result, length + 1, Value);
    return result;
}

wchar_t* PlatformString::duplicate(const wchar_t* Value) {
    size_t length = wcslen(Value);
    wchar_t* result = new wchar_t[length + 1];
    Platform::CopyString(result, length + 1, Value);
    return result;
}
