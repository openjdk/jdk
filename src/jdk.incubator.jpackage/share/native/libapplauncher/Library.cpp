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

#include "Library.h"
#include "Platform.h"
#include "Messages.h"
#include "PlatformString.h"

#include <fstream>
#include <locale>

Library::Library() {
    Initialize();
}

Library::Library(const TString &FileName) {
    Initialize();
    Load(FileName);
}

Library::~Library() {
    Unload();
}

void Library::Initialize() {
    FModule = NULL;
    FDependentLibraryNames = NULL;
    FDependenciesLibraries = NULL;
}

void Library::InitializeDependencies() {
    if (FDependentLibraryNames == NULL) {
        FDependentLibraryNames = new std::vector<TString>();
    }

    if (FDependenciesLibraries == NULL) {
        FDependenciesLibraries = new std::vector<Library*>();
    }
}

void Library::LoadDependencies() {
    if (FDependentLibraryNames != NULL && FDependenciesLibraries != NULL) {
        for (std::vector<TString>::const_iterator iterator =
                FDependentLibraryNames->begin();
                iterator != FDependentLibraryNames->end(); iterator++) {
            Library* library = new Library();

            if (library->Load(*iterator) == true) {
                FDependenciesLibraries->push_back(library);
            }
        }

        delete FDependentLibraryNames;
        FDependentLibraryNames = NULL;
    }
}

void Library::UnloadDependencies() {
    if (FDependenciesLibraries != NULL) {
        for (std::vector<Library*>::const_iterator iterator =
                FDependenciesLibraries->begin();
                iterator != FDependenciesLibraries->end(); iterator++) {
            Library* library = *iterator;

            if (library != NULL) {
                library->Unload();
                delete library;
            }
        }

        delete FDependenciesLibraries;
        FDependenciesLibraries = NULL;
    }
}

Procedure Library::GetProcAddress(const std::string& MethodName) const {
    Platform& platform = Platform::GetInstance();
    return platform.GetProcAddress(FModule, MethodName);
}

bool Library::Load(const TString &FileName) {
    bool result = true;

    if (FModule == NULL) {
        LoadDependencies();
        Platform& platform = Platform::GetInstance();
        FModule = platform.LoadLibrary(FileName);

        if (FModule == NULL) {
            Messages& messages = Messages::GetInstance();
            platform.ShowMessage(messages.GetMessage(LIBRARY_NOT_FOUND),
                    FileName);
            result = false;
        } else {
            fname = PlatformString(FileName).toStdString();
        }
    }

    return result;
}

bool Library::Unload() {
    bool result = false;

    if (FModule != NULL) {
        Platform& platform = Platform::GetInstance();
        platform.FreeLibrary(FModule);
        FModule = NULL;
        UnloadDependencies();
        result = true;
    }

    return result;
}

void Library::AddDependency(const TString &FileName) {
    InitializeDependencies();

    if (FDependentLibraryNames != NULL) {
        FDependentLibraryNames->push_back(FileName);
    }
}

void Library::AddDependencies(const std::vector<TString> &Dependencies) {
    if (Dependencies.size() > 0) {
        InitializeDependencies();

        if (FDependentLibraryNames != NULL) {
            for (std::vector<TString>::const_iterator iterator =
                    FDependentLibraryNames->begin();
                    iterator != FDependentLibraryNames->end(); iterator++) {
                TString fileName = *iterator;
                AddDependency(fileName);
            }
        }
    }
}

JavaLibrary::JavaLibrary() : Library(), FCreateProc(NULL) {
}

bool JavaLibrary::JavaVMCreate(size_t argc, char *argv[]) {
    if (FCreateProc == NULL) {
        FCreateProc = (JAVA_CREATE) GetProcAddress(LAUNCH_FUNC);
    }

    if (FCreateProc == NULL) {
        Platform& platform = Platform::GetInstance();
        Messages& messages = Messages::GetInstance();
        platform.ShowMessage(
                messages.GetMessage(FAILED_LOCATING_JVM_ENTRY_POINT));
        return false;
    }

    return FCreateProc((int) argc, argv,
            0, NULL,
            0, NULL,
            "",
            "",
            "java",
            "java",
            false,
            false,
            false,
            0) == 0;
}
