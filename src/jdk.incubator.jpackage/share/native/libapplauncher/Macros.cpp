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

#include "Macros.h"
#include "Package.h"
#include "Helpers.h"


Macros::Macros(void) {
}

Macros::~Macros(void) {
}

void Macros::Initialize() {
    Package& package = Package::GetInstance();
    Macros& macros = Macros::GetInstance();

    // Public macros.
    macros.AddMacro(_T("$ROOTDIR"), package.GetPackageRootDirectory());
    macros.AddMacro(_T("$APPDIR"), package.GetPackageAppDirectory());
    macros.AddMacro(_T("$BINDIR"), package.GetPackageLauncherDirectory());
}

Macros& Macros::GetInstance() {
    static Macros instance;
    return instance;
}

TString Macros::ExpandMacros(TString Value) {
    TString result = Value;

    for (std::map<TString, TString>::iterator iterator = FData.begin();
        iterator != FData.end();
        iterator++) {

        TString name = iterator->first;

        if (Value.find(name) != TString::npos) {
            TString lvalue = iterator->second;
            result = Helpers::ReplaceString(Value, name, lvalue);
            result = ExpandMacros(result);
            break;
        }
    }

    return result;
}

void Macros::AddMacro(TString Key, TString Value) {
    FData.insert(std::map<TString, TString>::value_type(Key, Value));
}
