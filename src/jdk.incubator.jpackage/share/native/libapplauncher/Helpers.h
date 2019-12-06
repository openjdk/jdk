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

#ifndef HELPERS_H
#define HELPERS_H

#include "Platform.h"
#include "OrderedMap.h"
#include "IniFile.h"


class Helpers {
private:
    Helpers(void) {}
    ~Helpers(void) {}

public:
    // Supports two formats for option:
    // Example 1:
    // foo=bar
    //
    // Example 2:
    // <name=foo=, value=goo>
    static bool SplitOptionIntoNameValue(TString option,
            TString& Name, TString& Value);
    static TString ReplaceString(TString subject, const TString& search,
            const TString& replace);
    static TString ConvertIdToFilePath(TString Value);
    static TString ConvertIdToJavaPath(TString Value);
    static TString ConvertJavaPathToId(TString Value);

    static OrderedMap<TString, TString>
            GetJavaOptionsFromConfig(IPropertyContainer* config);
    static std::list<TString> GetArgsFromConfig(IPropertyContainer* config);

    static std::list<TString>
            MapToNameValueList(OrderedMap<TString, TString> Map);

    static TString NameValueToString(TString name, TString value);

    static std::list<TString> StringToArray(TString Value);
};

#endif // HELPERS_H
