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

#ifndef JAVAVIRTUALMACHINE_H
#define JAVAVIRTUALMACHINE_H


#include "jni.h"
#include "Platform.h"
#include "Library.h"

struct JavaOptionItem {
    TString name;
    TString value;
    void* extraInfo;
};

class JavaOptions {
private:
    std::list<JavaOptionItem> FItems;
    JavaVMOption* FOptions;

public:
    JavaOptions();
    ~JavaOptions();

    void AppendValue(const TString Key, TString Value, void* Extra);
    void AppendValue(const TString Key, TString Value);
    void AppendValue(const TString Key);
    void AppendValues(OrderedMap<TString, TString> Values);
    void ReplaceValue(const TString Key, TString Value);
    std::list<TString> ToList();
    size_t GetCount();
};

class JavaVirtualMachine {
private:
    JavaLibrary javaLibrary;

    void configureLibrary();
    bool launchVM(JavaOptions& options, std::list<TString>& vmargs);
public:
    JavaVirtualMachine();
    ~JavaVirtualMachine(void);

    bool StartJVM();
};

bool RunVM();

#endif // JAVAVIRTUALMACHINE_H
