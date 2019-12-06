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

#include "Platform.h"
#include "Messages.h"
#include "PlatformString.h"
#include "FilePath.h"

#include <fstream>
#include <locale>

#ifdef WINDOWS
#include "WindowsPlatform.h"
#endif // WINDOWS
#ifdef LINUX
#include "LinuxPlatform.h"
#endif // LINUX
#ifdef MAC
#include "MacPlatform.h"
#endif // MAC

Platform& Platform::GetInstance() {
#ifdef WINDOWS
    static WindowsPlatform instance;
#endif // WINDOWS

#ifdef LINUX
    static LinuxPlatform instance;
#endif // LINUX

#ifdef MAC
    static MacPlatform instance;
#endif // MAC

    return instance;
}

TString Platform::GetConfigFileName() {
    TString result;
    TString basedir = GetPackageAppDirectory();

    if (basedir.empty() == false) {
        basedir = FilePath::IncludeTrailingSeparator(basedir);
        TString appConfig = basedir + GetAppName() + _T(".cfg");

        if (FilePath::FileExists(appConfig) == true) {
            result = appConfig;
        }
        else {
            result = basedir + _T("package.cfg");

            if (FilePath::FileExists(result) == false) {
                result = _T("");
            }
        }
    }

    return result;
}

std::list<TString> Platform::LoadFromFile(TString FileName) {
    std::list<TString> result;

    if (FilePath::FileExists(FileName) == true) {
        std::wifstream stream(FileName.data());
        InitStreamLocale(&stream);

        if (stream.is_open() == true) {
            while (stream.eof() == false) {
                std::wstring line;
                std::getline(stream, line);

                // # at the first character will comment out the line.
                if (line.empty() == false && line[0] != '#') {
                    result.push_back(PlatformString(line).toString());
                }
            }
        }
    }

    return result;
}

void Platform::SaveToFile(TString FileName, std::list<TString> Contents, bool ownerOnly) {
    TString path = FilePath::ExtractFilePath(FileName);

    if (FilePath::DirectoryExists(path) == false) {
        FilePath::CreateDirectory(path, ownerOnly);
    }

    std::wofstream stream(FileName.data());
    InitStreamLocale(&stream);

    FilePath::ChangePermissions(FileName.data(), ownerOnly);

    if (stream.is_open() == true) {
        for (std::list<TString>::const_iterator iterator =
                Contents.begin(); iterator != Contents.end(); iterator++) {
            TString line = *iterator;
            stream << PlatformString(line).toUnicodeString() << std::endl;
        }
    }
}

std::map<TString, TString> Platform::GetKeys() {
    std::map<TString, TString> keys;
    keys.insert(std::map<TString, TString>::value_type(CONFIG_VERSION,
            _T("app.version")));
    keys.insert(std::map<TString, TString>::value_type(CONFIG_MAINJAR_KEY,
            _T("app.mainjar")));
    keys.insert(std::map<TString, TString>::value_type(CONFIG_MAINMODULE_KEY,
            _T("app.mainmodule")));
    keys.insert(std::map<TString, TString>::value_type(CONFIG_MAINCLASSNAME_KEY,
            _T("app.mainclass")));
    keys.insert(std::map<TString, TString>::value_type(CONFIG_CLASSPATH_KEY,
            _T("app.classpath")));
    keys.insert(std::map<TString, TString>::value_type(CONFIG_MODULEPATH_KEY,
            _T("app.modulepath")));
    keys.insert(std::map<TString, TString>::value_type(APP_NAME_KEY,
            _T("app.name")));
    keys.insert(std::map<TString, TString>::value_type(JAVA_RUNTIME_KEY,
            _T("app.runtime")));
    keys.insert(std::map<TString, TString>::value_type(JPACKAGE_APP_DATA_DIR,
            _T("app.identifier")));
    keys.insert(std::map<TString, TString>::value_type(CONFIG_SPLASH_KEY,
            _T("app.splash")));
    keys.insert(std::map<TString, TString>::value_type(CONFIG_APP_MEMORY,
            _T("app.memory")));
    keys.insert(std::map<TString, TString>::value_type(CONFIG_APP_DEBUG,
            _T("app.debug")));
    keys.insert(std::map<TString,
            TString>::value_type(CONFIG_APPLICATION_INSTANCE,
            _T("app.application.instance")));
    keys.insert(std::map<TString,
            TString>::value_type(CONFIG_SECTION_APPLICATION,
            _T("Application")));
    keys.insert(std::map<TString,
            TString>::value_type(CONFIG_SECTION_JAVAOPTIONS,
            _T("JavaOptions")));
    keys.insert(std::map<TString,
            TString>::value_type(CONFIG_SECTION_APPCDSJAVAOPTIONS,
            _T("AppCDSJavaOptions")));
    keys.insert(std::map<TString,
            TString>::value_type(CONFIG_SECTION_APPCDSGENERATECACHEJAVAOPTIONS,
            _T("AppCDSGenerateCacheJavaOptions")));
    keys.insert(std::map<TString,
            TString>::value_type(CONFIG_SECTION_ARGOPTIONS,
            _T("ArgOptions")));

    return keys;
}
