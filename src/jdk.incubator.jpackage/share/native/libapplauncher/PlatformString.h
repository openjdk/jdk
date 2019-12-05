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

#ifndef PLATFORMSTRING_H
#define PLATFORMSTRING_H


#include <string>
#include <list>
#include <stdio.h>
#include <stdlib.h>

#include "jni.h"
#include "Platform.h"


template <typename T>
class DynamicBuffer {
private:
    T* FData;
    size_t FSize;

public:
    DynamicBuffer(size_t Size) {
        FSize = 0;
        FData = NULL;
        Resize(Size);
    }

    ~DynamicBuffer() {
        delete[] FData;
    }

    T* GetData() { return FData; }
    size_t GetSize() { return FSize; }

    bool Resize(size_t Size) {
        FSize = Size;

        if (FData != NULL) {
            delete[] FData;
            FData = NULL;
        }

        if (FSize != 0) {
            FData = new T[FSize];
            if (FData != NULL) {
                Zero();
            } else {
                return false;
            }
        }

        return true;
    }

    void Zero() {
        memset(FData, 0, FSize * sizeof(T));
    }

    T& operator[](size_t index) {
        return FData[index];
    }
};

class PlatformString {
private:
    char* FData; // Stored as UTF-8
    size_t FLength;
    wchar_t* FWideTStringToFree;

    void initialize();

// Prohibit Heap-Based PlatformStrings
private:
    static void *operator new(size_t size);
    static void operator delete(void *ptr);

public:
    PlatformString(void);
    PlatformString(const PlatformString &value);
    PlatformString(const char* value);
    PlatformString(const wchar_t* value);
    PlatformString(const std::string &value);
    PlatformString(const std::wstring &value);
    PlatformString(size_t Value);

    static TString Format(const TString value, ...);

    ~PlatformString(void);

    size_t length();

    char* c_str();
    char* toMultibyte();
    wchar_t* toWideString();
    std::wstring toUnicodeString();
    std::string toStdString();
    TCHAR* toPlatformString();
    TString toString();

    operator char* ();
    operator wchar_t* ();
    operator std::wstring ();

    // Caller must free result using delete[].
    static char* duplicate(const char* Value);

    // Caller must free result using delete[].
    static wchar_t* duplicate(const wchar_t* Value);
};


#endif // PLATFORMSTRING_H
