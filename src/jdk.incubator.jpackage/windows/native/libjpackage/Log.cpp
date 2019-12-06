/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

#include "Log.h"
#include "SysInfo.h"
#include "FileUtils.h"


namespace {
    //
    // IMPORTANT: Static objects with non-trivial constructors are NOT allowed
    // in logger module. Allocate buffers only and do lazy initialization of
    // globals in Logger::getDefault().
    //
    // Logging subsystem is used almost in every module, and logging API can be
    // called from constructors of static objects in various modules. As
    // ordering of static objects initialization between modules is undefined,
    // this means some module may call logging api before logging static
    // variables are initialized if any. This will result in AV. To avoid such
    // use cases keep logging module free from static variables that require
    // initialization with functions called by CRT.
    //

    // by default log everything
    const Logger::LogLevel defaultLogLevel = Logger::LOG_TRACE;

    char defaultLogAppenderMemory[sizeof(StderrLogAppender)] = {};

    char defaultLoggerMemory[sizeof(Logger)] = {};

    NopLogAppender nopLogApender;

    LPCTSTR getLogLevelStr(Logger::LogLevel level) {
        switch (level) {
        case Logger::LOG_TRACE:
            return _T("TRACE");
        case Logger::LOG_INFO:
            return _T("INFO");
        case Logger::LOG_WARNING:
            return _T("WARNING");
        case Logger::LOG_ERROR:
            return _T("ERROR");
        }
        return _T("UNKNOWN");
    }

    tstring retrieveModuleName() {
        try {
            return FileUtils::basename(SysInfo::getCurrentModulePath());
        } catch (const std::exception&) {
            return _T("Unknown");
        }
    }

    TCHAR moduleName[MAX_PATH] = { 'U', 'n', 'k', 'o', 'w', 'n', TCHAR(0) };

    const LPCTSTR format = _T("[%04u/%02u/%02u %02u:%02u:%02u.%03u, %s (PID: %u, TID: %u), %s:%u (%s)]\n\t%s: %s\n");

    enum State { NotInitialized, Initializing, Initialized };
    State state = NotInitialized;
}


LogEvent::LogEvent() {
    memset(this, 0, sizeof(*this));
    moduleName = tstring();
    logLevel = tstring();
    fileName = tstring();
    funcName = tstring();
    message = tstring();
}


StderrLogAppender::StderrLogAppender() {
}


/*static*/
Logger& Logger::defaultLogger() {
    Logger* reply = reinterpret_cast<Logger*>(defaultLoggerMemory);

    if (!reply->appender) {
        // Memory leak by design. Not an issue at all as this is global
        // object. OS will do resources clean up anyways when application
        // terminates and the default log appender should live as long as
        // application lives.
        reply->appender = new (defaultLogAppenderMemory) StderrLogAppender();
    }

    if (Initializing == state) {
        // Recursive call to Logger::defaultLogger.
        moduleName[0] = TCHAR(0);
    } else if (NotInitialized == state) {
        state = Initializing;

        tstring mname = retrieveModuleName();
        mname.resize(_countof(moduleName) - 1);
        std::memcpy(moduleName, mname.c_str(), mname.size());
        moduleName[mname.size()] = TCHAR(0);

        // if JPACKAGE_DEBUG environment variable is NOT set to "true" disable
        // logging.
        if (SysInfo::getEnvVariable(std::nothrow,
                L"JPACKAGE_DEBUG") != L"true") {
            reply->appender = &nopLogApender;
        }

        state = Initialized;
    }

    return *reply;
}

Logger::Logger(LogAppender& appender, LogLevel logLevel)
        : level(logLevel), appender(&appender) {
}

void Logger::setLogLevel(LogLevel logLevel) {
    level = logLevel;
}

Logger::~Logger() {
}


bool Logger::isLoggable(LogLevel logLevel) const {
    return logLevel >= level;
}

void Logger::log(LogLevel logLevel, LPCTSTR fileName, int lineNum,
        LPCTSTR funcName, const tstring& message) const {
    LogEvent logEvent;

    // [YYYY/MM/DD HH:MM:SS.ms, <module> (PID: processID, TID: threadID),
    // fileName:lineNum (funcName)] <tab>LEVEL: message
    GetLocalTime(&logEvent.ts);

    logEvent.pid = GetCurrentProcessId();
    logEvent.tid = GetCurrentThreadId();
    logEvent.moduleName = moduleName;
    logEvent.fileName = FileUtils::basename(fileName);
    logEvent.funcName = funcName;
    logEvent.logLevel = getLogLevelStr(logLevel);
    logEvent.lineNum = lineNum;
    logEvent.message = message;

    appender->append(logEvent);
}


void StderrLogAppender::append(const LogEvent& v)
{
    const tstring out = tstrings::unsafe_format(format,
        unsigned(v.ts.wYear), unsigned(v.ts.wMonth), unsigned(v.ts.wDay),
        unsigned(v.ts.wHour), unsigned(v.ts.wMinute), unsigned(v.ts.wSecond),
                unsigned(v.ts.wMilliseconds),
        v.moduleName.c_str(), v.pid, v.tid,
        v.fileName.c_str(), v.lineNum, v.funcName.c_str(),
        v.logLevel.c_str(),
        v.message.c_str());

    std::cerr << tstrings::toUtf8(out);
}


// Logger::ScopeTracer
Logger::ScopeTracer::ScopeTracer(Logger &logger, LogLevel logLevel,
        LPCTSTR fileName, int lineNum, LPCTSTR funcName,
        const tstring& scopeName) : log(logger), level(logLevel),
        file(fileName), line(lineNum),
        func(funcName), scope(scopeName), needLog(logger.isLoggable(logLevel)) {
    if (needLog) {
        log.log(level, file.c_str(), line, func.c_str(),
                tstrings::any() << "Entering " << scope);
    }
}

Logger::ScopeTracer::~ScopeTracer() {
    if (needLog) {
        // we don't know what line is end of scope at, so specify line 0
        // and add note about line when the scope begins
        log.log(level, file.c_str(), 0, func.c_str(),
                tstrings::any() << "Exiting " << scope << " (entered at "
                << FileUtils::basename(file) << ":" << line << ")");
    }
}
