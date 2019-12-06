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

#include "Helpers.h"
#include "PlatformString.h"
#include "PropertyFile.h"


bool Helpers::SplitOptionIntoNameValue(
        TString option, TString& Name, TString& Value) {
    bool hasValue = false;
    Name = _T("");
    Value = _T("");
    unsigned int index = 0;

    for (; index < option.length(); index++) {
        TCHAR c = option[index];

        switch (c) {
            case '=': {
                index++;
                hasValue = true;
                break;
            }

            case '\\': {
                if (index + 1 < option.length()) {
                    c = option[index + 1];

                    switch (c) {
                        case '\\': {
                            index++;
                            Name += '\\';
                            break;
                        }

                        case '=': {
                            index++;
                            Name += '=';
                            break;
                        }
                    }

                }

                continue;
            }

            default: {
                Name += c;
                continue;
            }
        }

        break;
    }

    if (hasValue) {
        Value = option.substr(index, index - option.length());
    }

    return (option.length() > 0);
}


TString Helpers::ReplaceString(TString subject, const TString& search,
                            const TString& replace) {
    size_t pos = 0;
    while((pos = subject.find(search, pos)) != TString::npos) {
            subject.replace(pos, search.length(), replace);
            pos += replace.length();
    }
    return subject;
}

TString Helpers::ConvertIdToFilePath(TString Value) {
    TString search;
    search = '.';
    TString replace;
    replace = '/';
    TString result = ReplaceString(Value, search, replace);
    return result;
}

TString Helpers::ConvertIdToJavaPath(TString Value) {
    TString search;
    search = '.';
    TString replace;
    replace = '/';
    TString result = ReplaceString(Value, search, replace);
    search = '\\';
    result = ReplaceString(result, search, replace);
    return result;
}

TString Helpers::ConvertJavaPathToId(TString Value) {
    TString search;
    search = '/';
    TString replace;
    replace = '.';
    TString result = ReplaceString(Value, search, replace);
    return result;
}

OrderedMap<TString, TString>
        Helpers::GetJavaOptionsFromConfig(IPropertyContainer* config) {
    OrderedMap<TString, TString> result;

    for (unsigned int index = 0; index < config->GetCount(); index++) {
        TString argname =
                TString(_T("jvmarg.")) + PlatformString(index + 1).toString();
        TString argvalue;

        if (config->GetValue(argname, argvalue) == false) {
            break;
        }
        else if (argvalue.empty() == false) {
            TString name;
            TString value;
            if (Helpers::SplitOptionIntoNameValue(argvalue, name, value)) {
                result.Append(name, value);
            }
        }
    }

    return result;
}

std::list<TString> Helpers::GetArgsFromConfig(IPropertyContainer* config) {
    std::list<TString> result;

    for (unsigned int index = 0; index < config->GetCount(); index++) {
        TString argname = TString(_T("arg."))
                + PlatformString(index + 1).toString();
        TString argvalue;

        if (config->GetValue(argname, argvalue) == false) {
            break;
        }
        else if (argvalue.empty() == false) {
            result.push_back((argvalue));
        }
    }

    return result;
}

std::list<TString>
        Helpers::MapToNameValueList(OrderedMap<TString, TString> Map) {
    std::list<TString> result;
    std::vector<TString> keys = Map.GetKeys();

    for (OrderedMap<TString, TString>::const_iterator iterator = Map.begin();
            iterator != Map.end(); iterator++) {
       JPPair<TString, TString> *item = *iterator;
       TString key = item->first;
       TString value = item->second;

       if (value.length() == 0) {
           result.push_back(key);
       } else {
           result.push_back(key + _T('=') + value);
        }
    }

    return result;
}

TString Helpers::NameValueToString(TString name, TString value) {
    TString result;

    if (value.empty() == true) {
        result = name;
    }
    else {
        result = name + TString(_T("=")) + value;
    }

    return result;
}

std::list<TString> Helpers::StringToArray(TString Value) {
    std::list<TString> result;
    TString line;

    for (unsigned int index = 0; index < Value.length(); index++) {
        TCHAR c = Value[index];

        switch (c) {
            case '\n': {
                result.push_back(line);
                line = _T("");
                break;
            }

            case '\r': {
                result.push_back(line);
                line = _T("");

                if (Value[index + 1] == '\n')
                    index++;

                break;
            }

            default: {
                line += c;
            }
        }
    }

    // The buffer may not have ended with a Carriage Return/Line Feed.
    if (line.length() > 0) {
        result.push_back(line);
    }

    return result;
}
