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

#ifndef PROPERTIES_H
#define PROPERTIES_H

#include "PlatformDefs.h"
#include "OrderedMap.h"

//#include <stdio.h>
//#include <stdlib.h>
//#include <memory.h>
//#include <string>
//#include <map>
//#include <list>
//#include <vector>
//#include <fstream>

//using namespace std;

template <typename ObjectType, typename ValueType,
        ValueType (ObjectType::*getter)(void),
        void (ObjectType::*setter)(ValueType)>
class Property {
private:
    ObjectType* FObject;

public:
    Property() {
        FObject = NULL;
    }

    void SetInstance(ObjectType* Value) {
        FObject = Value;
    }

    // To set the value using the set method.
    ValueType operator =(const ValueType& Value) {
        assert(FObject != NULL);
        (FObject->*setter)(Value);
        return Value;
    }

    // The Property class is treated as the internal type.
    operator ValueType() {
        assert(FObject != NULL);
        return (FObject->*getter)();
    }
};

template <typename ObjectType, typename ValueType,
        ValueType (ObjectType::*getter)(void)>
class ReadProperty {
private:
    ObjectType* FObject;

public:
    ReadProperty() {
        FObject = NULL;
    }

    void SetInstance(ObjectType* Value) {
        FObject = Value;
    }

    // The Property class is treated as the internal type.
    operator ValueType() {
        assert(FObject != NULL);
        return (FObject->*getter)();
    }
};

template <typename ObjectType, typename ValueType,
        void (ObjectType::*setter)(ValueType)>
class WriteProperty {
private:
    ObjectType* FObject;

public:
    WriteProperty() {
        FObject = NULL;
    }

    void SetInstance(ObjectType* Value) {
        FObject = Value;
    }

    // To set the value using the set method.
    ValueType operator =(const ValueType& Value) {
        assert(FObject != NULL);
        (FObject->*setter)(Value);
        return Value;
    }
};

template <typename ValueType,
        ValueType (*getter)(void), void (*setter)(ValueType)>
class StaticProperty {
public:
    StaticProperty() {
    }

    // To set the value using the set method.
    ValueType operator =(const ValueType& Value) {
        (*getter)(Value);
        return Value;
    }

    // The Property class is treated as the internal type which is the getter.
    operator ValueType() {
        return (*setter)();
    }
};

template <typename ValueType, ValueType (*getter)(void)>
class StaticReadProperty {
public:
    StaticReadProperty() {
    }

    // The Property class is treated as the internal type which is the getter.
    operator ValueType() {
        return (*getter)();
    }
};

template <typename ValueType, void (*setter)(ValueType)>
class StaticWriteProperty {
public:
    StaticWriteProperty() {
    }

    // To set the value using the set method.
    ValueType operator =(const ValueType& Value) {
        (*setter)(Value);
        return Value;
    }
};

class IPropertyContainer {
public:
    IPropertyContainer(void) {}
    virtual ~IPropertyContainer(void) {}

    virtual bool GetValue(const TString Key, TString& Value) = 0;
    virtual size_t GetCount() = 0;
};

class ISectionalPropertyContainer {
public:
    ISectionalPropertyContainer(void) {}
    virtual ~ISectionalPropertyContainer(void) {}

    virtual bool GetValue(const TString SectionName,
            const TString Key, TString& Value) = 0;
    virtual bool ContainsSection(const TString SectionName) = 0;
    virtual bool GetSection(const TString SectionName,
            OrderedMap<TString, TString> &Data) = 0;
};

#endif // PROPERTIES_H

