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

#include "PropertyFile.h"

#include "Helpers.h"
#include "FilePath.h"

#include <string>


PropertyFile::PropertyFile(void) : IPropertyContainer() {
    FReadOnly = false;
    FModified = false;
}

PropertyFile::PropertyFile(const TString FileName) : IPropertyContainer() {
    FReadOnly = true;
    FModified = false;
    LoadFromFile(FileName);
}

PropertyFile::PropertyFile(OrderedMap<TString, TString> Value) {
    FData.Append(Value);
}

PropertyFile::PropertyFile(PropertyFile &Value) {
    FData = Value.FData;
    FReadOnly = Value.FReadOnly;
    FModified = Value.FModified;
}

PropertyFile::~PropertyFile(void) {
    FData.Clear();
}

void PropertyFile::SetModified(bool Value) {
    FModified = Value;
}

bool PropertyFile::IsModified() {
    return FModified;
}

bool PropertyFile::GetReadOnly() {
    return FReadOnly;
}

void PropertyFile::SetReadOnly(bool Value) {
    FReadOnly = Value;
}

bool PropertyFile::LoadFromFile(const TString FileName) {
    bool result = false;
    Platform& platform = Platform::GetInstance();

    std::list<TString> contents = platform.LoadFromFile(FileName);

    if (contents.empty() == false) {
        for (std::list<TString>::const_iterator iterator = contents.begin();
                iterator != contents.end(); iterator++) {
            TString line = *iterator;
            TString name;
            TString value;

            if (Helpers::SplitOptionIntoNameValue(line, name, value) == true) {
                FData.Append(name, value);
            }
        }

        SetModified(false);
        result = true;
    }

    return result;
}

bool PropertyFile::SaveToFile(const TString FileName, bool ownerOnly) {
    bool result = false;

    if (GetReadOnly() == false && IsModified()) {
        std::list<TString> contents;
        std::vector<TString> keys = FData.GetKeys();

        for (size_t index = 0; index < keys.size(); index++) {
            TString name = keys[index];

            try {
                TString value;// = FData[index];

                if (FData.GetValue(name, value) == true) {
                    TString line = name + _T('=') + value;
                    contents.push_back(line);
                }
            }
            catch (std::out_of_range &) {
            }
        }

        Platform& platform = Platform::GetInstance();
        platform.SaveToFile(FileName, contents, ownerOnly);

        SetModified(false);
        result = true;
    }

    return result;
}

bool PropertyFile::GetValue(const TString Key, TString& Value) {
    return FData.GetValue(Key, Value);
}

bool PropertyFile::SetValue(const TString Key, TString Value) {
    bool result = false;

    if (GetReadOnly() == false) {
        FData.SetValue(Key, Value);
        SetModified(true);
        result = true;
    }

    return result;
}

bool PropertyFile::RemoveKey(const TString Key) {
    bool result = false;

    if (GetReadOnly() == false) {
        result = FData.RemoveByKey(Key);

        if (result == true) {
            SetModified(true);
        }
    }

    return result;
}

size_t PropertyFile::GetCount() {
    return FData.Count();
}

OrderedMap<TString, TString> PropertyFile::GetData() {
    return FData;
}
