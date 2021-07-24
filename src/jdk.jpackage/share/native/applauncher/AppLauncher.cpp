/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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

#include <algorithm>
#include "AppLauncher.h"
#include "JvmLauncher.h"
#include "CfgFile.h"
#include "Log.h"
#include "Dll.h"
#include "Toolbox.h"
#include "SysInfo.h"
#include "ReleaseFile.h"
#include "FileUtils.h"


AppLauncher::AppLauncher() {
    setInitJvmFromCmdlineOnly(false);
    launcherPath = SysInfo::getProcessModulePath();
    args = SysInfo::getCommandArgs();
}


namespace {

struct find_jvmlib {
    find_jvmlib(const tstring& v): runtimePath(v) {
    }

    bool operator () (const tstring& jvmLibName) const {
        const tstring path = FileUtils::mkpath() << runtimePath << jvmLibName;
        return FileUtils::isFileExists(path);
    }

private:
    const tstring& runtimePath;
};

/**
 * Returns path to a java runtime conforming to the giver release file.
 */
tstring findRuntime(const tstring& releasePath,
        const tstring& versionSpec, const tstring_array places) {
    tstring bestPath;
    tstring bestVersion;

    ReleaseFile required;
    required = ReleaseFile::load(releasePath);

    tstring_array::const_iterator it_places = places.begin();
    for(; it_places != places.end(); ++it_places) {
        tstring place = *it_places;
LOG_TRACE(tstrings::any() << "looking in " << place);
        tstring_array candidates = FileUtils::listContents(place, _T("release"));
        tstring_array::const_iterator it_candidate = candidates.begin();
        for(; it_candidate != candidates.end(); ++it_candidate) {
            tstring path = *it_candidate;
            ReleaseFile candidate = ReleaseFile::load(path);
            if (candidate.satisfies(required, versionSpec)) {
                tstring version = candidate.getVersion();
LOG_TRACE(tstrings::any() << "satisfied with: " << path <<
        " , version: " << version);
                if (candidate.greaterThan(version, bestVersion)) {
                    bestPath = FileUtils::dirname(path);
                    bestVersion = version;
LOG_TRACE(tstrings::any() << "best: " << version << " at: " << bestPath);
                }
            }
        }
    }
    return bestPath;
}

tstring findJvmLib(const CfgFile& cfgFile,
        const tstring& defaultRuntimePath, const tstring_array& jvmLibNames) {
    const CfgFile::Properties& appOptions = cfgFile.getProperties(
            SectionName::Application);

    const CfgFile::Properties::const_iterator runtimePathProp = appOptions.find(
            PropertyName::runtime);
    tstring runtimePath ((runtimePathProp == appOptions.end()) ?
            _T("") : CfgFile::asString(*runtimePathProp));
LOG_TRACE(tstrings::any() << "runtimePath: " << runtimePath);

    const CfgFile::Properties::const_iterator releasePathProp = appOptions.find(
            PropertyName::runtimeRelease);
    tstring releasePath ((releasePathProp == appOptions.end()) ?
            _T("") : CfgFile::asString(*releasePathProp));
LOG_TRACE(tstrings::any() << "releasePath: " << releasePath);

    const CfgFile::Properties::const_iterator versionProp = appOptions.find(
            PropertyName::runtimeVersion);
    tstring version ((versionProp == appOptions.end()) ?
            _T("") : CfgFile::asString(*versionProp));
LOG_TRACE(tstrings::any() << "version: " << version);

    const CfgFile::Properties::const_iterator searchpathProp = appOptions.find(
                PropertyName::runtimeSearchPath);

    tstring searchpath = (searchpathProp == appOptions.end()) ?
            _T("") : CfgFile::asString(*searchpathProp);
LOG_TRACE(tstrings::any() << "search path: " << searchpath);

    if ((runtimePathProp != appOptions.end()) && (FileUtils::isFileExists(
            FileUtils::mkpath() << runtimePath << _T("/lib")))) {
        LOG_TRACE(tstrings::any()
                << "Property \"" << PropertyName::runtime.name()
                << "\" found in \"" << SectionName::Application.name()
                << "\" section of launcher config file."
                << " Using Java runtime from \""
                << runtimePath << "\" directory");
    } else if (FileUtils::isFileExists(
            FileUtils::mkpath() << defaultRuntimePath << _T("/lib"))) {
        // if there is a lib subdir in the defaultRuntimePath then use default
        runtimePath = defaultRuntimePath;
        LOG_TRACE(tstrings::any()
                << " Using built-in Java runtime from \""
                << runtimePath << "\" directory");
    } else if (searchpathProp != appOptions.end()) {
        tstring releaseFile = FileUtils::mkpath() << releasePath;
        if (FileUtils::isFileExists(releaseFile)) {
            tstring_array places = tstrings::split(searchpath, _T(","));
            runtimePath = findRuntime(releaseFile, version, places);
            if (FileUtils::isFileExists(runtimePath)) {
                LOG_TRACE(tstrings::any()
                        << "Searching for runtime image matching: "
                        << releaseFile << " found matching runtime in: "
                        << runtimePath);
            } else {
                JP_THROW(tstrings::any() << "No runtime in app image. "
                        << "Cannot find runtime matching " << releaseFile);
            }
        } else {
            JP_THROW(tstrings::any() << "No runtime in application image, "
                    << "and no release file at: " << releaseFile);
        }
    } else {
        JP_THROW(tstrings::any() << "No runtime in app image and "
                        << "no runtime serch path given in cfg file.");
    }

    const tstring_array::const_iterator jvmLibNameEntry = std::find_if(
            jvmLibNames.begin(),
            jvmLibNames.end(),
            find_jvmlib(runtimePath));

    if (jvmLibNameEntry == jvmLibNames.end()) {
        JP_THROW(tstrings::any() << "Failed to find JVM in \""
            << runtimePath
            << "\" directory.");
    }

    return FileUtils::mkpath() << runtimePath << *jvmLibNameEntry;
}
} // namespace

Jvm* AppLauncher::createJvmLauncher() const {
    const tstring cfgFilePath = FileUtils::mkpath()
        << appDirPath << FileUtils::stripExeSuffix(
            FileUtils::basename(launcherPath)) + _T(".cfg");

    CfgFile::Macros macros;
    macros[_T("$APPDIR")] = appDirPath;
    macros[_T("$BINDIR")] = FileUtils::dirname(launcherPath);
    macros[_T("$ROOTDIR")] = imageRoot;

    CfgFile cfgFile = CfgFile::load(cfgFilePath).expandMacros(macros);

    if (!args.empty()) {
        // Override default launcher arguments.
        cfgFile.setPropertyValue(SectionName::ArgOptions,
            PropertyName::arguments, args);
    }

    SysInfo::setEnvVariable(libEnvVarName, SysInfo::getEnvVariable(
            std::nothrow, libEnvVarName) + _T(";") + appDirPath);

    std::unique_ptr<Jvm> jvm(new Jvm());

    (*jvm)
        .setPath(findJvmLib(cfgFile, defaultRuntimePath, jvmLibNames))
        .addArgument(launcherPath);

    if (initJvmFromCmdlineOnly) {
        tstring_array::const_iterator argIt = args.begin();
        const tstring_array::const_iterator argEnd = args.end();
        for (; argIt != argEnd; ++argIt) {
            (*jvm).addArgument(*argIt);
        }
    } else {
        (*jvm).initFromConfigFile(cfgFile);
    }

    return jvm.release();
}


void AppLauncher::launch() const {
    std::unique_ptr<Jvm>(createJvmLauncher())->launch();
}
