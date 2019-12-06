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

#ifndef PROPERTYFILE_H
#define PROPERTYFILE_H

#include "Platform.h"
#include "Helpers.h"


class PropertyFile : public IPropertyContainer {
private:
    bool FReadOnly;
    bool FModified;
    OrderedMap<TString, TString> FData;

    void SetModified(bool Value);

public:
    PropertyFile(void);
    PropertyFile(const TString FileName);
    PropertyFile(OrderedMap<TString, TString> Value);
    PropertyFile(PropertyFile &Value);
    virtual ~PropertyFile(void);

    bool IsModified();
    bool GetReadOnly();
    void SetReadOnly(bool Value);

    bool LoadFromFile(const TString FileName);
    bool SaveToFile(const TString FileName, bool ownerOnly = true);

    bool SetValue(const TString Key, TString Value);
    bool RemoveKey(const TString Key);

    OrderedMap<TString, TString> GetData();

    // IPropertyContainer
    virtual bool GetValue(const TString Key, TString& Value);
    virtual size_t GetCount();
};

#endif // PROPERTYFILE_H
