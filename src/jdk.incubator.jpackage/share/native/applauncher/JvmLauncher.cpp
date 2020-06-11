/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

#include <cstring>
#include <jni.h>
#include "JvmLauncher.h"
#include "Log.h"
#include "Dll.h"
#include "CfgFile.h"
#include "FileUtils.h"
#include "Toolbox.h"
#include "ErrorHandling.h"


Jvm& Jvm::initFromConfigFile(const CfgFile& cfgFile) {
    const CfgFile::Properties& appOptions = cfgFile.getProperties(
            SectionName::Application);

    do {
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
    } while (0);

    do {
        const CfgFile::Properties::const_iterator classpath = appOptions.find(
                PropertyName::classpath);
        if (classpath != appOptions.end()) {
            addArgument(_T("-classpath"));
            addArgument(CfgFile::asPathList(*classpath));
        }
    } while (0);

    do {
        const CfgFile::Properties::const_iterator splash = appOptions.find(
                PropertyName::splash);
        if (splash != appOptions.end()) {
            const tstring splashPath = CfgFile::asString(*splash);
            if (FileUtils::isFileExists(splashPath)) {
                addArgument(_T("-splash"));
                addArgument(splashPath);
            } else {
                LOG_WARNING(tstrings::any()
                        << "Splash property ignored. File \""
                        << splashPath << "\" not found");
            }
        }
    } while (0);

    do {
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
    } while (0);

    // No validation of data in config file related to how Java app should be
    // launched intentionally.
    // Just read what is in config file and put on jvm's command line as is.

    do { // Run modular app
        const CfgFile::Properties::const_iterator mainmodule = appOptions.find(
                PropertyName::mainmodule);
        if (mainmodule != appOptions.end()) {
            addArgument(_T("-m"));
            addArgument(CfgFile::asString(*mainmodule));
        }
    } while (0);

    do { // Run main class
        const CfgFile::Properties::const_iterator mainclass = appOptions.find(
                PropertyName::mainclass);
        if (mainclass != appOptions.end()) {
            addArgument(CfgFile::asString(*mainclass));
        }
    } while (0);

    do { // Run jar
        const CfgFile::Properties::const_iterator mainjar = appOptions.find(
                PropertyName::mainjar);
        if (mainjar != appOptions.end()) {
            addArgument(_T("-jar"));
            addArgument(CfgFile::asString(*mainjar));
        }
    } while (0);

    do {
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
    } while (0);

    return *this;
}


namespace {
void convertArgs(const std::vector<std::string>& args, std::vector<char*>& argv) {
    argv.reserve(args.size() + 1);
    argv.resize(0);

    std::vector<std::string>::const_iterator it = args.begin();
    const std::vector<std::string>::const_iterator end = args.end();

    for (; it != end; ++it) {
        argv.push_back(const_cast<char*>(it->c_str()));
    };

    // Add treminal '0'.
    argv.push_back(0);
}
} // namespace

void Jvm::launch() {
    typedef int (JNICALL *LaunchFuncType)(int argc, char ** argv,
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

    std::vector<char*> argv;
#ifdef TSTRINGS_WITH_WCHAR
    std::vector<std::string> mbcs_args;
    do {
        tstring_array::const_iterator it = args.begin();
        const tstring_array::const_iterator end = args.end();
        for (; it != end; ++it) {
            mbcs_args.push_back(tstrings::toACP(*it));
        }
    } while (0);
    convertArgs(mbcs_args, argv);
#else
    convertArgs(args, argv);
#endif

    // Don't count terminal '0'.
    const int argc = (int)argv.size() - 1;

    LOG_TRACE(tstrings::any() << "JVM library: \"" << jvmPath << "\"");

    DllFunction<LaunchFuncType> func(Dll(jvmPath), "JLI_Launch");
    int exitStatus = func(argc, argv.data(),
        0, 0,
        0, 0,
        "",
        "",
        "java",
        "java",
        JNI_FALSE,
        JNI_FALSE,
        JNI_FALSE,
        0);

    if (exitStatus != 0) {
        JP_THROW("Failed to launch JVM");
    }
}
