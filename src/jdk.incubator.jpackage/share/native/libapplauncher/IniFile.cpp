/*
 * Copyright (c) 2015, 2019, Oracle and/or its affiliates. All rights reserved.
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

#include "IniFile.h"
#include "Helpers.h"

#include <string>


IniFile::IniFile() : ISectionalPropertyContainer() {
}

IniFile::~IniFile() {
    for (OrderedMap<TString, IniSectionData*>::iterator iterator =
            FMap.begin(); iterator != FMap.end(); iterator++) {
        JPPair<TString, IniSectionData*> *item = *iterator;
        delete item->second;
    }
}

bool IniFile::LoadFromFile(const TString FileName) {
    bool result = false;
    Platform& platform = Platform::GetInstance();

    std::list<TString> contents = platform.LoadFromFile(FileName);

    if (contents.empty() == false) {
        bool found = false;

        // Determine the if file is an INI file or property file.
        // Assign FDefaultSection if it is
        // an INI file. Otherwise FDefaultSection is NULL.
        for (std::list<TString>::const_iterator iterator = contents.begin();
                iterator != contents.end(); iterator++) {
            TString line = *iterator;

            if (line[0] == ';') {
                // Semicolon is a comment so ignore the line.
                continue;
            }
            else {
                if (line[0] == '[') {
                    found = true;
                }

                break;
            }
        }

        if (found == true) {
            TString sectionName;

            for (std::list<TString>::const_iterator iterator = contents.begin();
                    iterator != contents.end(); iterator++) {
                TString line = *iterator;

                if (line[0] == ';') {
                    // Semicolon is a comment so ignore the line.
                    continue;
                }
                else if (line[0] == '[' && line[line.length() - 1] == ']') {
                    sectionName = line.substr(1, line.size() - 2);
                }
                else if (sectionName.empty() == false) {
                    TString name;
                    TString value;

                    if (Helpers::SplitOptionIntoNameValue(
                            line, name, value) == true) {
                        Append(sectionName, name, value);
                    }
                }
            }

            result = true;
        }
    }

    return result;
}

bool IniFile::SaveToFile(const TString FileName, bool ownerOnly) {
    bool result = false;

    std::list<TString> contents;
    std::vector<TString> keys = FMap.GetKeys();

    for (unsigned int index = 0; index < keys.size(); index++) {
        TString name = keys[index];
        IniSectionData *section = NULL;

        if (FMap.GetValue(name, section) == true && section != NULL) {
            contents.push_back(_T("[") + name + _T("]"));
            std::list<TString> lines = section->GetLines();
            contents.insert(contents.end(), lines.begin(), lines.end());
            contents.push_back(_T(""));
        }
    }

    Platform& platform = Platform::GetInstance();
    platform.SaveToFile(FileName, contents, ownerOnly);
    result = true;
    return result;
}

void IniFile::Append(const TString SectionName,
        const TString Key, TString Value) {
    if (FMap.ContainsKey(SectionName) == true) {
        IniSectionData* section = NULL;

        if (FMap.GetValue(SectionName, section) == true && section != NULL) {
            section->SetValue(Key, Value);
        }
    }
    else {
        IniSectionData *section = new IniSectionData();
        section->SetValue(Key, Value);
        FMap.Append(SectionName, section);
    }
}

void IniFile::AppendSection(const TString SectionName,
        OrderedMap<TString, TString> Values) {
    if (FMap.ContainsKey(SectionName) == true) {
        IniSectionData* section = NULL;

        if (FMap.GetValue(SectionName, section) == true && section != NULL) {
            section->Append(Values);
        }
    }
    else {
        IniSectionData *section = new IniSectionData(Values);
        FMap.Append(SectionName, section);
    }
}

bool IniFile::GetValue(const TString SectionName,
        const TString Key, TString& Value) {
    bool result = false;
    IniSectionData* section = NULL;

    if (FMap.GetValue(SectionName, section) == true && section != NULL) {
        result = section->GetValue(Key, Value);
    }

    return result;
}

bool IniFile::SetValue(const TString SectionName,
        const TString Key, TString Value) {
    bool result = false;
    IniSectionData* section = NULL;

    if (FMap.GetValue(SectionName, section) && section != NULL) {
        result = section->SetValue(Key, Value);
    }
    else {
        Append(SectionName, Key, Value);
    }


    return result;
}

bool IniFile::GetSection(const TString SectionName,
        OrderedMap<TString, TString> &Data) {
    bool result = false;

    if (FMap.ContainsKey(SectionName) == true) {
        IniSectionData* section = NULL;

        if (FMap.GetValue(SectionName, section) == true && section != NULL) {
            OrderedMap<TString, TString> data = section->GetData();
            Data.Append(data);
            result = true;
        }
    }

    return result;
}

bool IniFile::ContainsSection(const TString SectionName) {
    return FMap.ContainsKey(SectionName);
}

//----------------------------------------------------------------------------

IniSectionData::IniSectionData() {
    FMap.SetAllowDuplicates(true);
}

IniSectionData::IniSectionData(OrderedMap<TString, TString> Values) {
    FMap = Values;
}

std::vector<TString> IniSectionData::GetKeys() {
    return FMap.GetKeys();
}

std::list<TString> IniSectionData::GetLines() {
    std::list<TString> result;
    std::vector<TString> keys = FMap.GetKeys();

    for (unsigned int index = 0; index < keys.size(); index++) {
        TString name = keys[index];
        TString value;

        if (FMap.GetValue(name, value) == true) {
            name = Helpers::ReplaceString(name, _T("="), _T("\\="));
            value = Helpers::ReplaceString(value, _T("="), _T("\\="));

            TString line = name + _T('=') + value;
            result.push_back(line);
        }
    }

    return result;
}

OrderedMap<TString, TString> IniSectionData::GetData() {
    OrderedMap<TString, TString> result = FMap;
    return result;
}

bool IniSectionData::GetValue(const TString Key, TString& Value) {
    return FMap.GetValue(Key, Value);
}

bool IniSectionData::SetValue(const TString Key, TString Value) {
    return FMap.SetValue(Key, Value);
}

void IniSectionData::Append(OrderedMap<TString, TString> Values) {
    FMap.Append(Values);
}

size_t IniSectionData::GetCount() {
    return FMap.Count();
}
