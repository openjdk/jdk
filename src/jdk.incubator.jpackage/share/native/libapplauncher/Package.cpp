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

#include "Package.h"
#include "Helpers.h"
#include "Macros.h"
#include "IniFile.h"

#include <assert.h>


Package::Package(void) {
    FInitialized = false;
    Initialize();
}

TPlatformNumber StringToPercentageOfNumber(TString Value,
        TPlatformNumber Number) {
    TPlatformNumber result = 0;
    size_t percentage = atoi(PlatformString(Value.c_str()));

    if (percentage > 0 && Number > 0) {
        result = Number * percentage / 100;
    }

    return result;
}

void Package::Initialize() {
    if (FInitialized == true) {
        return;
    }

    Platform& platform = Platform::GetInstance();

    FBootFields = new PackageBootFields();
    FDebugging = dsNone;

    // Allow duplicates for Java options, so we can have multiple --add-exports
    // or similar args.
    FBootFields->FJavaOptions.SetAllowDuplicates(true);
    FBootFields->FPackageRootDirectory = platform.GetPackageRootDirectory();
    FBootFields->FPackageAppDirectory = platform.GetPackageAppDirectory();
    FBootFields->FPackageLauncherDirectory =
            platform.GetPackageLauncherDirectory();
    FBootFields->FAppDataDirectory = platform.GetAppDataDirectory();

    std::map<TString, TString> keys = platform.GetKeys();

    // Read from configure.cfg/Info.plist
    AutoFreePtr<ISectionalPropertyContainer> config =
            platform.GetConfigFile(platform.GetConfigFileName());

    config->GetValue(keys[CONFIG_SECTION_APPLICATION],
            keys[JPACKAGE_APP_DATA_DIR], FBootFields->FPackageAppDataDirectory);
    FBootFields->FPackageAppDataDirectory =
            FilePath::FixPathForPlatform(FBootFields->FPackageAppDataDirectory);

    // Main JAR.
    config->GetValue(keys[CONFIG_SECTION_APPLICATION],
            keys[CONFIG_MAINJAR_KEY], FBootFields->FMainJar);
    FBootFields->FMainJar = FilePath::FixPathForPlatform(FBootFields->FMainJar);

    // Main Module.
    config->GetValue(keys[CONFIG_SECTION_APPLICATION],
            keys[CONFIG_MAINMODULE_KEY], FBootFields->FMainModule);

    // Classpath.
    // 1. If the provided class path contains main jar then only use
    //    provided class path.
    // 2. If class path provided by config file is empty then add main jar.
    // 3. If main jar is not in provided class path then add it.
    config->GetValue(keys[CONFIG_SECTION_APPLICATION],
            keys[CONFIG_CLASSPATH_KEY], FBootFields->FClassPath);
    FBootFields->FClassPath =
            FilePath::FixPathSeparatorForPlatform(FBootFields->FClassPath);

    if (FBootFields->FClassPath.empty() == true) {
        FBootFields->FClassPath = GetMainJar();
    } else if (FBootFields->FClassPath.find(GetMainJar()) == TString::npos) {
        FBootFields->FClassPath = GetMainJar()
                + FilePath::PathSeparator() + FBootFields->FClassPath;
    }

    // Modulepath.
    config->GetValue(keys[CONFIG_SECTION_APPLICATION],
            keys[CONFIG_MODULEPATH_KEY], FBootFields->FModulePath);
    FBootFields->FModulePath =
            FilePath::FixPathSeparatorForPlatform(FBootFields->FModulePath);

    // Main Class.
    config->GetValue(keys[CONFIG_SECTION_APPLICATION],
            keys[CONFIG_MAINCLASSNAME_KEY], FBootFields->FMainClassName);

    // Splash Screen.
    if (config->GetValue(keys[CONFIG_SECTION_APPLICATION],
            keys[CONFIG_SPLASH_KEY],
            FBootFields->FSplashScreenFileName) == true) {
        FBootFields->FSplashScreenFileName =
            FilePath::IncludeTrailingSeparator(GetPackageAppDirectory())
            + FilePath::FixPathForPlatform(FBootFields->FSplashScreenFileName);

        if (FilePath::FileExists(FBootFields->FSplashScreenFileName) == false) {
            FBootFields->FSplashScreenFileName = _T("");
        }
    }

    // Runtime.
    config->GetValue(keys[CONFIG_SECTION_APPLICATION],
            keys[JAVA_RUNTIME_KEY], FBootFields->FJavaRuntimeDirectory);

    // Read jvmargs.
    PromoteAppCDSState(config);
    ReadJavaOptions(config);

    // Read args if none were passed in.
    if (FBootFields->FArgs.size() == 0) {
        OrderedMap<TString, TString> args;

        if (config->GetSection(keys[CONFIG_SECTION_ARGOPTIONS], args) == true) {
            FBootFields->FArgs = Helpers::MapToNameValueList(args);
        }
    }

    // Auto Memory.
    TString autoMemory;

    if (config->GetValue(keys[CONFIG_SECTION_APPLICATION],
            keys[CONFIG_APP_MEMORY], autoMemory) == true) {
        if (autoMemory == _T("auto") || autoMemory == _T("100%")) {
            FBootFields->FMemoryState = PackageBootFields::msAuto;
            FBootFields->FMemorySize = platform.GetMemorySize();
        } else if (autoMemory.length() == 2 && isdigit(autoMemory[0]) &&
                autoMemory[1] == '%') {
            FBootFields->FMemoryState = PackageBootFields::msAuto;
            FBootFields->FMemorySize =
                    StringToPercentageOfNumber(autoMemory.substr(0, 1),
                    platform.GetMemorySize());
        } else if (autoMemory.length() == 3 && isdigit(autoMemory[0]) &&
                isdigit(autoMemory[1]) && autoMemory[2] == '%') {
            FBootFields->FMemoryState = PackageBootFields::msAuto;
            FBootFields->FMemorySize =
                    StringToPercentageOfNumber(autoMemory.substr(0, 2),
                    platform.GetMemorySize());
        } else {
            FBootFields->FMemoryState = PackageBootFields::msManual;
            FBootFields->FMemorySize = 0;
        }
    }

    // Debug
    TString debug;
    if (config->GetValue(keys[CONFIG_SECTION_APPLICATION],
            keys[CONFIG_APP_DEBUG], debug) == true) {
        FBootFields->FArgs.push_back(debug);
    }
}

void Package::Clear() {
    FreeBootFields();
    FInitialized = false;
}

// This is the only location that the AppCDS state should be modified except
// by command line arguments provided by the user.
//
// The state of AppCDS is as follows:
//
// -> cdsUninitialized
//    -> cdsGenCache If -Xappcds:generatecache
//    -> cdsDisabled If -Xappcds:off
//    -> cdsEnabled If "AppCDSJavaOptions" section is present
//    -> cdsAuto If "AppCDSJavaOptions" section is present and
//               app.appcds.cache=auto
//    -> cdsDisabled Default
//
void Package::PromoteAppCDSState(ISectionalPropertyContainer* Config) {
    Platform& platform = Platform::GetInstance();
    std::map<TString, TString> keys = platform.GetKeys();

    // The AppCDS state can change at this point.
    switch (platform.GetAppCDSState()) {
        case cdsEnabled:
        case cdsAuto:
        case cdsDisabled:
        case cdsGenCache: {
            // Do nothing.
            break;
        }

        case cdsUninitialized: {
            if (Config->ContainsSection(
                    keys[CONFIG_SECTION_APPCDSJAVAOPTIONS]) == true) {
                // If the AppCDS section is present then enable AppCDS.
                TString appCDSCacheValue;

                // If running with AppCDS enabled, and the configuration has
                // been setup so "auto" is enabled, then
                // the launcher will attempt to generate the cache file
                // automatically and run the application.
                if (Config->GetValue(keys[CONFIG_SECTION_APPLICATION],
                        _T("app.appcds.cache"), appCDSCacheValue) == true &&
                    appCDSCacheValue == _T("auto")) {
                    platform.SetAppCDSState(cdsAuto);
                }
                else {
                    platform.SetAppCDSState(cdsEnabled);
                }
            } else {

                platform.SetAppCDSState(cdsDisabled);
            }
        }
    }
}

void Package::ReadJavaOptions(ISectionalPropertyContainer* Config) {
    Platform& platform = Platform::GetInstance();
    std::map<TString, TString> keys = platform.GetKeys();

    // Evaluate based on the current AppCDS state.
    switch (platform.GetAppCDSState()) {
        case cdsUninitialized: {
            throw Exception(_T("Internal Error"));
        }

        case cdsDisabled: {
            Config->GetSection(keys[CONFIG_SECTION_JAVAOPTIONS],
                    FBootFields->FJavaOptions);
            break;
        }

        case cdsGenCache: {
            Config->GetSection(keys[
                    CONFIG_SECTION_APPCDSGENERATECACHEJAVAOPTIONS],
                    FBootFields->FJavaOptions);
            break;
        }

        case cdsAuto:
        case cdsEnabled: {
            if (Config->GetValue(keys[CONFIG_SECTION_APPCDSJAVAOPTIONS],
                    _T( "-XX:SharedArchiveFile"),
                    FBootFields->FAppCDSCacheFileName) == true) {
                // File names may contain the incorrect path separators.
                // The cache file name must be corrected at this point.
                if (FBootFields->FAppCDSCacheFileName.empty() == false) {
                    IniFile* iniConfig = dynamic_cast<IniFile*>(Config);

                    if (iniConfig != NULL) {
                        FBootFields->FAppCDSCacheFileName =
                                FilePath::FixPathForPlatform(
                                FBootFields->FAppCDSCacheFileName);
                        iniConfig->SetValue(keys[
                                CONFIG_SECTION_APPCDSJAVAOPTIONS],
                                _T( "-XX:SharedArchiveFile"),
                                FBootFields->FAppCDSCacheFileName);
                    }
                }

                Config->GetSection(keys[CONFIG_SECTION_APPCDSJAVAOPTIONS],
                        FBootFields->FJavaOptions);
            }

            break;
        }
    }
}

void Package::SetCommandLineArguments(int argc, TCHAR* argv[]) {
    if (argc > 0) {
        std::list<TString> args;

        // Prepare app arguments. Skip value at index 0 -
        // this is path to executable.
        FBootFields->FCommandName = argv[0];

        // Path to executable is at 0 index so start at index 1.
        for (int index = 1; index < argc; index++) {
            TString arg = argv[index];

#ifdef DEBUG
            if (arg == _T("-debug")) {
                FDebugging = dsNative;
            }

            if (arg == _T("-javadebug")) {
                FDebugging = dsJava;
            }
#endif //DEBUG
#ifdef MAC
            if (arg.find(_T("-psn_"), 0) != TString::npos) {
                Platform& platform = Platform::GetInstance();

                if (platform.IsMainThread() == true) {
#ifdef DEBUG
                    printf("%s\n", arg.c_str());
#endif //DEBUG
                    continue;
                }
            }

            if (arg == _T("-NSDocumentRevisionsDebugMode")) {
                // Ignore -NSDocumentRevisionsDebugMode and
                // the following YES/NO
                index++;
                continue;
            }
#endif //MAC

            args.push_back(arg);
        }

        if (args.size() > 0) {
            FBootFields->FArgs = args;
        }
    }
}

Package& Package::GetInstance() {
    static Package instance;
    // Guaranteed to be destroyed. Instantiated on first use.
    return instance;
}

Package::~Package(void) {
    FreeBootFields();
}

void Package::FreeBootFields() {
    if (FBootFields != NULL) {
        delete FBootFields;
        FBootFields = NULL;
    }
}

OrderedMap<TString, TString> Package::GetJavaOptions() {
    return FBootFields->FJavaOptions;
}

std::vector<TString> GetKeysThatAreNotDuplicates(OrderedMap<TString,
        TString> &Defaults, OrderedMap<TString, TString> &Overrides) {
    std::vector<TString> result;
    std::vector<TString> overrideKeys = Overrides.GetKeys();

    for (size_t index = 0; index < overrideKeys.size(); index++) {
        TString overridesKey = overrideKeys[index];
        TString overridesValue;
        TString defaultValue;

        if ((Defaults.ContainsKey(overridesKey) == false) ||
           (Defaults.GetValue(overridesKey, defaultValue) == true &&
            Overrides.GetValue(overridesKey, overridesValue) == true &&
            defaultValue != overridesValue)) {
            result.push_back(overridesKey);
        }
    }

    return result;
}

OrderedMap<TString, TString> CreateOrderedMapFromKeyList(OrderedMap<TString,
        TString> &Map, std::vector<TString> &Keys) {
    OrderedMap<TString, TString> result;

    for (size_t index = 0; index < Keys.size(); index++) {
        TString key = Keys[index];
        TString value;

        if (Map.GetValue(key, value) == true) {
            result.Append(key, value);
        }
    }

    return result;
}

std::vector<TString> GetKeysThatAreNotOverridesOfDefaultValues(
        OrderedMap<TString, TString> &Defaults, OrderedMap<TString,
        TString> &Overrides) {
    std::vector<TString> result;
    std::vector<TString> keys = Overrides.GetKeys();

    for (unsigned int index = 0; index< keys.size(); index++) {
        TString key = keys[index];

        if (Defaults.ContainsKey(key) == true) {
            try {
                TString value = Overrides[key];
                Defaults[key] = value;
            }
            catch (std::out_of_range &) {
            }
        }
        else {
            result.push_back(key);
        }
    }

    return result;
}

std::list<TString> Package::GetArgs() {
    assert(FBootFields != NULL);
    return FBootFields->FArgs;
}

TString Package::GetPackageRootDirectory() {
    assert(FBootFields != NULL);
    return FBootFields->FPackageRootDirectory;
}

TString Package::GetPackageAppDirectory() {
    assert(FBootFields != NULL);
    return FBootFields->FPackageAppDirectory;
}

TString Package::GetPackageLauncherDirectory() {
    assert(FBootFields != NULL);
    return FBootFields->FPackageLauncherDirectory;
}

TString Package::GetAppDataDirectory() {
    assert(FBootFields != NULL);
    return FBootFields->FAppDataDirectory;
}

TString Package::GetAppCDSCacheDirectory() {
    if (FAppCDSCacheDirectory.empty()) {
        Platform& platform = Platform::GetInstance();
        FAppCDSCacheDirectory = FilePath::IncludeTrailingSeparator(
                platform.GetAppDataDirectory())
                + FilePath::IncludeTrailingSeparator(
                GetPackageAppDataDirectory()) + _T("cache");

        Macros& macros = Macros::GetInstance();
        FAppCDSCacheDirectory = macros.ExpandMacros(FAppCDSCacheDirectory);
        FAppCDSCacheDirectory =
                FilePath::FixPathForPlatform(FAppCDSCacheDirectory);
    }

    return FAppCDSCacheDirectory;
}

TString Package::GetAppCDSCacheFileName() {
    assert(FBootFields != NULL);

    if (FBootFields->FAppCDSCacheFileName.empty() == false) {
        Macros& macros = Macros::GetInstance();
        FBootFields->FAppCDSCacheFileName =
                macros.ExpandMacros(FBootFields->FAppCDSCacheFileName);
        FBootFields->FAppCDSCacheFileName =
                FilePath::FixPathForPlatform(FBootFields->FAppCDSCacheFileName);
    }

    return FBootFields->FAppCDSCacheFileName;
}

TString Package::GetPackageAppDataDirectory() {
    assert(FBootFields != NULL);
    return FBootFields->FPackageAppDataDirectory;
}

TString Package::GetClassPath() {
    assert(FBootFields != NULL);
    return FBootFields->FClassPath;
}

TString Package::GetModulePath() {
    assert(FBootFields != NULL);
    return FBootFields->FModulePath;
}

TString Package::GetMainJar() {
    assert(FBootFields != NULL);
    return FBootFields->FMainJar;
}

TString Package::GetMainModule() {
    assert(FBootFields != NULL);
    return FBootFields->FMainModule;
}

TString Package::GetMainClassName() {
    assert(FBootFields != NULL);
    return FBootFields->FMainClassName;
}

TString Package::GetJavaLibraryFileName() {
    assert(FBootFields != NULL);

    if (FBootFields->FJavaLibraryFileName.empty() == true) {
        Platform& platform = Platform::GetInstance();
        Macros& macros = Macros::GetInstance();
        TString jvmRuntimePath = macros.ExpandMacros(GetJavaRuntimeDirectory());
        FBootFields->FJavaLibraryFileName =
                platform.GetBundledJavaLibraryFileName(jvmRuntimePath);
    }

    return FBootFields->FJavaLibraryFileName;
}

TString Package::GetJavaRuntimeDirectory() {
    assert(FBootFields != NULL);
    return FBootFields->FJavaRuntimeDirectory;
}

TString Package::GetSplashScreenFileName() {
    assert(FBootFields != NULL);
    return FBootFields->FSplashScreenFileName;
}

bool Package::HasSplashScreen() {
    assert(FBootFields != NULL);
    return FilePath::FileExists(FBootFields->FSplashScreenFileName);
}

TString Package::GetCommandName() {
    assert(FBootFields != NULL);
    return FBootFields->FCommandName;
}

TPlatformNumber Package::GetMemorySize() {
    assert(FBootFields != NULL);
    return FBootFields->FMemorySize;
}

PackageBootFields::MemoryState Package::GetMemoryState() {
    assert(FBootFields != NULL);
    return FBootFields->FMemoryState;
}

DebugState Package::Debugging() {
    return FDebugging;
}
