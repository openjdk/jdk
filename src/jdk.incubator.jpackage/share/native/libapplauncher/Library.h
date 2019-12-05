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

#ifndef LIBRARY_H
#define LIBRARY_H

#include "PlatformDefs.h"
//#include "Platform.h"
#include "OrderedMap.h"

#include "jni.h"
#include <stdio.h>
#include <stdlib.h>
#include <memory.h>
#include <string>
#include <map>
#include <list>
#include <vector>
#include <fstream>

using namespace std;

// Private typedef for function pointer casting

#if defined(_WIN32) && !defined(_WIN64)
#define LAUNCH_FUNC "_JLI_Launch@56"
#else
#define LAUNCH_FUNC "JLI_Launch"
#endif


typedef int (JNICALL *JAVA_CREATE)(int argc, char ** argv,
        int jargc, const char** jargv,
        int appclassc, const char** appclassv,
        const char* fullversion,
        const char* dotversion,
        const char* pname,
        const char* lname,
        jboolean javaargs,
        jboolean cpwildcard,
        jboolean javaw,
        jint ergo);

class Library {
private:
    std::vector<TString> *FDependentLibraryNames;
    std::vector<Library*> *FDependenciesLibraries;
    Module FModule;
    std::string fname;

    void Initialize();
    void InitializeDependencies();
    void LoadDependencies();
    void UnloadDependencies();

public:
    void* GetProcAddress(const std::string& MethodName) const;

public:
    Library();
    Library(const TString &FileName);
    ~Library();

    bool Load(const TString &FileName);
    bool Unload();

    const std::string& GetName() const {
        return fname;
    }

    void AddDependency(const TString &FileName);
    void AddDependencies(const std::vector<TString> &Dependencies);
};

class JavaLibrary : public Library {
    JAVA_CREATE FCreateProc;
    JavaLibrary(const TString &FileName);
public:
    JavaLibrary();
    bool JavaVMCreate(size_t argc, char *argv[]);
};

#endif // LIBRARY_H

