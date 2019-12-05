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

#ifndef PACKAGE_H
#define PACKAGE_H


#include "Platform.h"
#include "PlatformString.h"
#include "FilePath.h"
#include "PropertyFile.h"

#include <map>
#include <list>

class PackageBootFields {
public:
    enum MemoryState {msManual, msAuto};

public:
    OrderedMap<TString, TString> FJavaOptions;
    std::list<TString> FArgs;

    TString FPackageRootDirectory;
    TString FPackageAppDirectory;
    TString FPackageLauncherDirectory;
    TString FAppDataDirectory;
    TString FPackageAppDataDirectory;
    TString FClassPath;
    TString FModulePath;
    TString FMainJar;
    TString FMainModule;
    TString FMainClassName;
    TString FJavaRuntimeDirectory;
    TString FJavaLibraryFileName;
    TString FSplashScreenFileName;
    bool FUseJavaPreferences;
    TString FCommandName;

    TString FAppCDSCacheFileName;

    TPlatformNumber FMemorySize;
    MemoryState FMemoryState;
};


class Package {
private:
    Package(Package const&); // Don't Implement.
    void operator=(Package const&); // Don't implement

private:
    bool FInitialized;
    PackageBootFields* FBootFields;
    TString FAppCDSCacheDirectory;

    DebugState FDebugging;

    Package(void);

    TString GetMainJar();
    void ReadJavaOptions(ISectionalPropertyContainer* Config);
    void PromoteAppCDSState(ISectionalPropertyContainer* Config);

public:
    static Package& GetInstance();
    ~Package(void);

    void Initialize();
    void Clear();
    void FreeBootFields();

    void SetCommandLineArguments(int argc, TCHAR* argv[]);

    OrderedMap<TString, TString> GetJavaOptions();
    TString GetMainModule();

    std::list<TString> GetArgs();

    TString GetPackageRootDirectory();
    TString GetPackageAppDirectory();
    TString GetPackageLauncherDirectory();
    TString GetAppDataDirectory();

    TString GetAppCDSCacheDirectory();
    TString GetAppCDSCacheFileName();

    TString GetPackageAppDataDirectory();
    TString GetClassPath();
    TString GetModulePath();
    TString GetMainClassName();
    TString GetJavaLibraryFileName();
    TString GetJavaRuntimeDirectory();
    TString GetSplashScreenFileName();
    bool HasSplashScreen();
    TString GetCommandName();

    TPlatformNumber GetMemorySize();
    PackageBootFields::MemoryState GetMemoryState();

    DebugState Debugging();
};

#endif // PACKAGE_H
