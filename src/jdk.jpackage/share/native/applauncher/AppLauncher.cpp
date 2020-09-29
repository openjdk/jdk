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

#include <algorithm>
#include "AppLauncher.h"
#include "JvmLauncher.h"
#include "CfgFile.h"
#include "Log.h"
#include "Dll.h"
#include "Toolbox.h"
#include "SysInfo.h"
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

tstring findJvmLib(const CfgFile& cfgFile, const tstring& defaultRuntimePath,
        const tstring_array& jvmLibNames) {
    const CfgFile::Properties& appOptions = cfgFile.getProperties(
            SectionName::Application);

    const CfgFile::Properties::const_iterator runtimePathProp = appOptions.find(
            PropertyName::runtime);
    tstring runtimePath;
    if (runtimePathProp != appOptions.end()) {
        runtimePath = CfgFile::asString(*runtimePathProp);
    } else {
        runtimePath = defaultRuntimePath;
        LOG_TRACE(tstrings::any()
                << "Property \"" << PropertyName::runtime.name()
                << "\" not found in \"" << SectionName::Application.name()
                << "\" section of launcher config file."
                << " Using Java runtime from \""
                << runtimePath << "\" directory");
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
        << appDirPath
        << FileUtils::basename(FileUtils::replaceSuffix(
                launcherPath, _T(".cfg")));

    LOG_TRACE(tstrings::any() << "Launcher config file path: \""
            << cfgFilePath << "\"");

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


namespace {
const std::string* theLastErrorMsg = 0;

NopLogAppender nopLogAppender;

class StandardLogAppender : public LogAppender {
public:
    virtual void append(const LogEvent& v) {
        std::cerr << "[" << v.logLevel << "] "
            << v.fileName
            << ":" << v.lineNum
            << ": " << v.message
            << std::endl;
    }
} standardLogAppender;

class LastErrorLogAppender : public LogAppender {
public:
    virtual void append(const LogEvent& v) {
        std::cerr << AppLauncher::lastErrorMsg() << std::endl;
    }
} lastErrorLogAppender;
} // namespace

LogAppender& AppLauncher::defaultLastErrorLogAppender() {
    return lastErrorLogAppender;
}


std::string AppLauncher::lastErrorMsg() {
    if (theLastErrorMsg) {
        return *theLastErrorMsg;
    }
    return "";
}


bool AppLauncher::isWithLogging() {
    // If JPACKAGE_DEBUG environment variable is set to "true"
    // logging is enabled.
    return SysInfo::getEnvVariable(
            std::nothrow, _T("JPACKAGE_DEBUG")) == _T("true");
}


namespace {

class ResetLastErrorMsgAtEndOfScope {
public:
    ~ResetLastErrorMsgAtEndOfScope() {
        JP_NO_THROW(theLastErrorMsg = 0);
    }
};

class SetLoggerAtEndOfScope {
public:
    SetLoggerAtEndOfScope(
            std::unique_ptr<WithExtraLogAppender>& withLogAppender,
            LogAppender* lastErrorLogAppender):
                withLogAppender(withLogAppender),
                lastErrorLogAppender(lastErrorLogAppender) {
    }

    ~SetLoggerAtEndOfScope() {
        JP_TRY;
        std::unique_ptr<WithExtraLogAppender> other(
                new WithExtraLogAppender(*lastErrorLogAppender));
        withLogAppender.swap(other);
        JP_CATCH_ALL;
    }

private:
    std::unique_ptr<WithExtraLogAppender>& withLogAppender;
    LogAppender* lastErrorLogAppender;
};

} // namespace

int AppLauncher::launch(const std::nothrow_t&,
        LauncherFunc func, LogAppender* lastErrorLogAppender) {
    if (isWithLogging()) {
        Logger::defaultLogger().setAppender(standardLogAppender);
    } else {
        Logger::defaultLogger().setAppender(nopLogAppender);
    }

    LOG_TRACE_FUNCTION();

    if (!lastErrorLogAppender) {
        lastErrorLogAppender = &defaultLastErrorLogAppender();
    }
    std::unique_ptr<WithExtraLogAppender> withLogAppender;
    std::string errorMsg;
    const ResetLastErrorMsgAtEndOfScope resetLastErrorMsg;

    JP_TRY;

    // This will temporary change log appenders of the default logger
    // to save log messages in the default and additional log appenders.
    // Log appenders config of the default logger will be restored to
    // the original state at function exit automatically.
    const SetLoggerAtEndOfScope setLogger(withLogAppender, lastErrorLogAppender);
    func();
    return 0;

    // The point of all these redefines is to save the last raw error message in
    // 'AppLauncher::theLastErrorMsg' variable.
    // By default error messages are saved in exception instances with the details
    // of error origin (source file, function name, line number).
    // We don't want these details in user error messages. However we still want to
    // save full information about the last error in the default log appender.
#undef JP_HANDLE_ERROR
#undef JP_HANDLE_UNKNOWN_ERROR
#undef JP_CATCH_EXCEPTIONS
#define JP_HANDLE_ERROR(e) \
    do { \
        errorMsg = (tstrings::any() << e.what()).str(); \
        theLastErrorMsg = &errorMsg; \
        reportError(JP_SOURCE_CODE_POS, e); \
    } while(0)
#define JP_HANDLE_UNKNOWN_ERROR \
    do { \
        errorMsg = "Unknown error"; \
        theLastErrorMsg = &errorMsg; \
        reportUnknownError(JP_SOURCE_CODE_POS); \
    } while(0)
#define JP_CATCH_EXCEPTIONS \
    catch (const JpErrorBase& e) { \
        errorMsg = (tstrings::any() << e.rawMessage()).str(); \
        theLastErrorMsg = &errorMsg; \
        try { \
            throw; \
        } catch (const std::runtime_error& e) { \
            reportError(JP_SOURCE_CODE_POS, e); \
        } \
    } catch (const std::runtime_error& e) { \
        errorMsg = lastCRTError(); \
        theLastErrorMsg = &errorMsg; \
        reportError(JP_SOURCE_CODE_POS, e); \
    } \
    JP_CATCH_UNKNOWN_EXCEPTION

    JP_CATCH_ALL;

#undef JP_HANDLE_ERROR
#undef JP_HANDLE_UNKNOWN_ERROR
#undef JP_CATCH_EXCEPTIONS
#define JP_HANDLE_ERROR(e)      JP_REPORT_ERROR(e)
#define JP_HANDLE_UNKNOWN_ERROR JP_REPORT_UNKNOWN_ERROR
#define JP_CATCH_EXCEPTIONS     JP_DEFAULT_CATCH_EXCEPTIONS

    return 1;
}
