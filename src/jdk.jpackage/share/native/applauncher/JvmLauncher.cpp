/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
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

#include <cstddef>
#include <cstring>
#include "tstrings.h"
#include "JvmLauncher.h"
#include "Log.h"
#include "Dll.h"
#include "CfgFile.h"
#include "FileUtils.h"
#include "Toolbox.h"
#include "ErrorHandling.h"

Jvm::Jvm() {
    LOG_TRACE(tstrings::any() << "Jvm(" << this << ")::Jvm()");
}


Jvm::~Jvm() {
    LOG_TRACE(tstrings::any() << "Jvm(" << this << ")::~Jvm()");
}


Jvm& Jvm::initFromConfigFile(const CfgFile& cfgFile) {
    const CfgFile::Properties& appOptions = cfgFile.getProperties(
            SectionName::Application);

    {
        const CfgFile::Properties::const_iterator modulepath = appOptions.find(
                PropertyName::modulepath);
        if (modulepath != appOptions.end()) {
            tstring_array::const_iterator it = modulepath->second.begin();
            const tstring_array::const_iterator end = modulepath->second.end();
            for (; it != end; ++it) {
                addArgument(_T("--module-path"));
                addArgument(*it);
            };
        }
    }

    {
        const CfgFile::Properties::const_iterator classpath = appOptions.find(
                PropertyName::classpath);
        if (classpath != appOptions.end()) {
            addArgument(_T("-classpath"));
            addArgument(CfgFile::asPathList(*classpath));
        }
    }

    {
        const CfgFile::Properties::const_iterator splash = appOptions.find(
                PropertyName::splash);
        if (splash != appOptions.end()) {
            const tstring splashPath = CfgFile::asString(*splash);
            if (FileUtils::isFileExists(splashPath)) {
                addArgument(_T("-splash:") + splashPath);
            } else {
                LOG_WARNING(tstrings::any()
                        << "Splash property ignored. File \""
                        << splashPath << "\" not found");
            }
        }
    }

    {
        const CfgFile::Properties& section = cfgFile.getProperties(
                SectionName::JavaOptions);
        const CfgFile::Properties::const_iterator javaOptions = section.find(
                PropertyName::javaOptions);
        if (javaOptions != section.end()) {
            tstring_array::const_iterator it = javaOptions->second.begin();
            const tstring_array::const_iterator end = javaOptions->second.end();
            for (; it != end; ++it) {
                addArgument(*it);
            };
        }
    }

    {
        addArgument(_T("-Djpackage.app-path=")
                + SysInfo::getProcessModulePath());
    }

    // No validation of data in config file related to how Java app should be
    // launched intentionally.
    // Just read what is in config file and put on jvm's command line as is.

    { // Run modular app
        const CfgFile::Properties::const_iterator mainmodule = appOptions.find(
                PropertyName::mainmodule);
        if (mainmodule != appOptions.end()) {
            addArgument(_T("-m"));
            addArgument(CfgFile::asString(*mainmodule));
        }
    }

    { // Run main class
        const CfgFile::Properties::const_iterator mainclass = appOptions.find(
                PropertyName::mainclass);
        if (mainclass != appOptions.end()) {
            addArgument(CfgFile::asString(*mainclass));
        }
    }

    { // Run jar
        const CfgFile::Properties::const_iterator mainjar = appOptions.find(
                PropertyName::mainjar);
        if (mainjar != appOptions.end()) {
            addArgument(_T("-jar"));
            addArgument(CfgFile::asString(*mainjar));
        }
    }

    {
        const CfgFile::Properties& section = cfgFile.getProperties(
                SectionName::ArgOptions);
        const CfgFile::Properties::const_iterator arguments = section.find(
                PropertyName::arguments);
        if (arguments != section.end()) {
            tstring_array::const_iterator it = arguments->second.begin();
            const tstring_array::const_iterator end = arguments->second.end();
            for (; it != end; ++it) {
                addArgument(*it);
            };
        }
    }

    return *this;
}


bool Jvm::isWithSplash() const {
    tstring_array::const_iterator it = args.begin();
    const tstring_array::const_iterator end = args.end();
    for (; it != end; ++it) {
        if (tstrings::startsWith(*it, _T("-splash:"))) {
            return true;
        }
    }
    return false;
}


namespace {

struct JvmlLauncherHandleCloser {
    typedef JvmlLauncherHandle pointer;

    void operator()(JvmlLauncherHandle h) {
        jvmLauncherCloseHandle(jvmLauncherGetAPI(), h);
    }
};

struct JvmlLauncherDataDeleter {
    typedef JvmlLauncherData* pointer;

    void operator()(JvmlLauncherData* ptr) {
        free(ptr);
    }
};

} // namespace

void Jvm::launch() {
    typedef std::unique_ptr<
        JvmlLauncherHandle, JvmlLauncherHandleCloser> AutoJvmlLauncherHandle;

    typedef std::unique_ptr<
        JvmlLauncherData, JvmlLauncherDataDeleter> AutoJvmlLauncherData;

    AutoJvmlLauncherHandle jlh(exportLauncher());

    JvmlLauncherAPI* api = jvmLauncherGetAPI();

    AutoJvmlLauncherData jld(jvmLauncherCreateJvmlLauncherData(api,
                                                        jlh.release(), 0));

    LOG_TRACE(tstrings::any() << "JVM library: \"" << jvmPath << "\"");

    DllFunction<void*> func(Dll(jvmPath), "JLI_Launch");

    int exitStatus = jvmLauncherStartJvm(jld.get(), func.operator void*());

    if (exitStatus != 0) {
        JP_THROW("Failed to launch JVM");
    }
}


void Jvm::setEnvVariables() {
    for (size_t i = 0; i != envVarNames.size(); i++) {
        SysInfo::setEnvVariable(envVarNames.at(i), envVarValues.at(i));
    }
}


namespace {

struct JliLaunchData {
    std::string jliLibPath;
    std::vector<std::string> args;
    tstring_array envVarNames;
    tstring_array envVarValues;

    int initJvmlLauncherData(JvmlLauncherData* ptr, int bufferSize) const {
        int minimalBufferSize = initJvmlLauncherData(0);
        if (minimalBufferSize <= bufferSize) {
            initJvmlLauncherData(ptr);
        }
        return minimalBufferSize;
    }

private:
    template <class T>
    static char* copyStrings(const std::vector<T>& src,
                JvmlLauncherData* ptr, const size_t offset, char* curPtr) {
        char** strArray = 0;
        if (ptr) {
            strArray = *reinterpret_cast<char***>(
                                    reinterpret_cast<char*>(ptr) + offset);
        }

        for (size_t i = 0; i != src.size(); i++) {
            const size_t count = (src[i].size() + 1 /* trailing zero */)
                                            * sizeof(typename T::value_type);
            if (ptr) {
                std::memcpy(curPtr, src[i].c_str(), count);
                strArray[i] = curPtr;
            }

            curPtr += count;
        };

        return curPtr;
    }

    int initJvmlLauncherData(JvmlLauncherData* ptr) const {
        // Store path to JLI library just behind JvmlLauncherData header.
        char* curPtr = reinterpret_cast<char*>(ptr + 1);
        {
            const size_t count = sizeof(char)
                    * (jliLibPath.size() + 1 /* trailing zero */);
            if (ptr) {
                std::memcpy(curPtr, jliLibPath.c_str(), count);
                ptr->jliLibPath = curPtr;
            }
            curPtr += count;
        }

        // Write array of char* pointing to JLI lib arg strings.
        if (ptr) {
            ptr->jliLaunchArgv = reinterpret_cast<char**>(curPtr);
            ptr->jliLaunchArgc = (int)args.size();
            // Add terminal '0' arg.
            ptr->jliLaunchArgv[ptr->jliLaunchArgc] = 0;
        }
        // Skip memory occupied by JvmlLauncherData::jliLaunchArgv array.
        curPtr += sizeof(char*) * (args.size() + 1 /* terminal '0' arg */);
        // Store JLI lib arg strings.
        curPtr = copyStrings(args, ptr,
                            offsetof(JvmlLauncherData, jliLaunchArgv), curPtr);

        // Write array of char* pointing to env variable name strings.
        if (ptr) {
            ptr->envVarNames = reinterpret_cast<TCHAR**>(curPtr);
            ptr->envVarCount = (int)envVarNames.size();
        }
        // Skip memory occupied by JvmlLauncherData::envVarNames array.
        curPtr += sizeof(TCHAR*) * envVarNames.size();
        // Store env variable names.
        curPtr = copyStrings(envVarNames, ptr,
                            offsetof(JvmlLauncherData, envVarNames), curPtr);

        // Write array of char* pointing to env variable value strings.
        if (ptr) {
            ptr->envVarValues = reinterpret_cast<TCHAR**>(curPtr);
        }
        // Skip memory occupied by JvmlLauncherData::envVarValues array.
        curPtr += sizeof(TCHAR*) * envVarValues.size();
        // Store env variable values.
        curPtr = copyStrings(envVarValues, ptr,
                            offsetof(JvmlLauncherData, envVarValues), curPtr);

        const size_t bufferSize = curPtr - reinterpret_cast<char*>(ptr);
        if (ptr) {
            LOG_TRACE(tstrings::any() << "Initialized " << bufferSize
                                        << " bytes at " << ptr << " address");
        } else {
            LOG_TRACE(tstrings::any() << "Need " << bufferSize
                                    << " bytes for JvmlLauncherData buffer");
        }
        return static_cast<int>(bufferSize);
    }
};

void copyStringArray(const tstring_array& src, std::vector<std::string>& dst) {
#ifdef TSTRINGS_WITH_WCHAR
    {
        tstring_array::const_iterator it = src.begin();
        const tstring_array::const_iterator end = src.end();
        for (; it != end; ++it) {
            dst.push_back(tstrings::toACP(*it));
        }
    }
#else
    dst = src;
#endif
}

} // namespace

JvmlLauncherHandle Jvm::exportLauncher() const {
    std::unique_ptr<JliLaunchData> result(new JliLaunchData());

    result->jliLibPath = tstrings::toUtf8(jvmPath);

    copyStringArray(args, result->args);
    result->envVarNames = envVarNames;
    result->envVarValues = envVarValues;

    return result.release();
}


namespace {

void closeHandle(JvmlLauncherHandle h) {
    JP_TRY;

    JliLaunchData* data = static_cast<JliLaunchData*>(h);
    const std::unique_ptr<JliLaunchData> deleter(data);

    JP_CATCH_ALL;
}


int getJvmlLauncherDataSize(JvmlLauncherHandle h) {
    JP_TRY;

    const JliLaunchData* data = static_cast<const JliLaunchData*>(h);
    return data->initJvmlLauncherData(0, 0);

    JP_CATCH_ALL;

    return -1;
}


JvmlLauncherData* initJvmlLauncherData(JvmlLauncherHandle h,
                                                void* ptr, int bufferSize) {
    JP_TRY;

    const JliLaunchData* data = static_cast<const JliLaunchData*>(h);
    const int usedBufferSize = data->initJvmlLauncherData(
                            static_cast<JvmlLauncherData*>(ptr), bufferSize);
    if (bufferSize <= usedBufferSize) {
        return static_cast<JvmlLauncherData*>(ptr);
    }

    JP_CATCH_ALL;

    return 0;
}

class Impl : public JvmlLauncherAPI {
public:
    Impl() {
        this->closeHandle = ::closeHandle;
        this->getJvmlLauncherDataSize = ::getJvmlLauncherDataSize;
        this->initJvmlLauncherData = ::initJvmlLauncherData;
    }
} api;

} // namespace


extern "C" {

JNIEXPORT JvmlLauncherAPI* jvmLauncherGetAPI(void) {
    return &api;
}

} // extern "C"
