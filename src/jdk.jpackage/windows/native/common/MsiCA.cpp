/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

#include "MsiCA.h"
#include "MsiDb.h"
#include "MsiUtils.h"
#include "FileUtils.h"
#include "ErrorHandling.h"
#include "Toolbox.h"


#pragma comment(lib, "msi.lib")


namespace msi {

tstring CAImpl::getProperty(const tstring& name) const {
    return getPropertyFromCustomAction(handle, name);
}


void CAImpl::setProperty(const tstring& name, const tstring& value) {
    if (value.empty()) {
        JP_THROW(tstrings::any() << "Attempt to assign empty value to '"
                                          << name << "' MSI property");
    }

    LOG_TRACE(tstrings::any() << "Setting MSI property '" << name <<
                                                    "' to '" << value << "'");
    const UINT status = MsiSetProperty(handle, name.c_str(), value.c_str());
    if (status != ERROR_SUCCESS) {
        JP_THROW(msi::Error(tstrings::any() << "MsiSetProperty(" << name
                                    << ", " << value << ") failed", status));
    }
}


void CAImpl::removeProperty(const tstring& name) {
    LOG_TRACE(tstrings::any() << "Removing MSI property '" << name << "'");
    const UINT status = MsiSetProperty(handle, name.c_str(), NULL);
    if (status != ERROR_SUCCESS) {
        JP_THROW(msi::Error(tstrings::any() << "MsiSetProperty(" << name
                                    << ", NULL) failed", status));
    }
}


Guid CAFacade::getProductCode() const {
    return impl.getProperty(_T("ProductCode"));
}


bool CAFacade::isInMode(MSIRUNMODE v) const {
    return MsiGetMode(impl.getHandle(), v) != FALSE;
}


tstring CAFacade::getModes() const {
    tstring modes;
    // Iterate all modes in the range [MSIRUNMODE_ADMIN, MSIRUNMODE_COMMIT]
    for (int mode = MSIRUNMODE_ADMIN; mode != MSIRUNMODE_COMMIT + 1; ++mode) {
        modes.insert(modes.end(), isInMode(MSIRUNMODE(mode)) ?
                                                        _T('1') : _T('0'));
    }
    return modes;
}


void CAFacade::doAction(const tstring& name) const {
    const UINT status = MsiDoAction(impl.getHandle(), name.c_str());
    if (status != ERROR_SUCCESS) {
        JP_THROW(msi::Error(tstrings::any() << "MsiDoAction(" << name
                                                    << ") failed", status));
    }
}


tstring CAFacade::normalizeDirectoryPath(tstring v) {
    if (v.empty()) {
        return v;
    }
    std::replace(v.begin(), v.end(), '/', '\\');
    return FileUtils::removeTrailingSlash(v) + _T("\\");
}


CA& CA::setPropertyIfEmpty(const tstring& name, const tstring& v) {
    if (getProperty(name).empty()) {
        setProperty(name, v);
    }
    return *this;
}


tstring DeferredCA::getArg() const {
    if (isInMode(MSIRUNMODE_SCHEDULED) || caArgPropertyName.empty()) {
        // Details on accessing MSI properties from deferred custom actions:
        //  http://blogs.technet.com/b/alexshev/archive/2008/03/25/property-does-not-exist-or-empty-when-accessed-from-deferred-custom-action.aspx
        //  http://stackoverflow.com/questions/17988392/unable-to-fetch-the-install-location-property-in-a-deferred-custom-action
        //  http://stackoverflow.com/questions/11233267/how-to-pass-customactiondata-to-a-customaction-using-wix
        return impl.getProperty(_T("CustomActionData"));
    }

    return impl.getProperty(caArgPropertyName);
}


tstring DeferredCA::getParsedArg(const tstring& name) const {
    const auto entry = theParsedArgs.find(name);
    if (entry == theParsedArgs.end()) {
        JP_THROW(tstrings::any() << "Argument << '" << name
                                                        << "' not found.");
    }
    return entry->second;
}


namespace {
std::pair<tstring, tstring> parseArg(const tstring& v) {
    const auto pos = v.find(_T('='));
    if (pos == tstring::npos) {
        JP_THROW(tstrings::any() << "Missing expected '=' character in ["
                                                        << v << "] string.");
    }
    return std::pair<tstring, tstring>(v.substr(0, pos), v.substr(pos + 1));
}

void parseArgsImpl(DeferredCA::ArgsCtnr& dst, const tstring& src) {
    const tstring_array pairs = tstrings::split(src, _T("*"));
    for(auto it = pairs.begin(), end = pairs.end(); it != end; ++it) {
        const auto pair = parseArg(*it);
        dst[pair.first] = pair.second;
    }
}
} //  namespace
void DeferredCA::parseArgs(ArgsCtnr& dst, const tstring& src) {
    DeferredCA::ArgsCtnr tmp;

    const auto end = src.find(_T("**"));
    if (end != tstring::npos) {
        parseArgsImpl(tmp, src.substr(0, end));
        tmp[tstring()] = src.substr(end + 2);
    } else {
        parseArgsImpl(tmp, src);
    }

    tmp.insert(dst.begin(), dst.end());
    tmp.swap(dst);
}


MsiLogAppender::MsiLogAppender(MSIHANDLE h): handle(h),
        ctorThread(GetCurrentThreadId()) {

}

void MsiLogAppender::append(const LogEvent& v) {
    const LPCTSTR format = _T("[%02u:%02u:%02u.%03u%s%s:%u (%s)] %s: %s");

    tstring ctxInfo = _T(" ");
    if (v.tid != ctorThread) {
        ctxInfo = (tstrings::any() << " (TID: " << v.tid << ") ").tstr();
    }

    const tstring buf = tstrings::unsafe_format(format,
        unsigned(v.ts.wHour), unsigned(v.ts.wMinute), unsigned(v.ts.wSecond), unsigned(v.ts.wMilliseconds), // time
        ctxInfo.c_str(),
        v.fileName.c_str(), v.lineNum, v.funcName.c_str(),
        v.logLevel.c_str(),
        v.message.c_str());

    DatabaseRecord r(1);
    r.setString(0, _T("Java [1]"));
    r.setString(1, buf);

    MsiProcessMessage(handle, INSTALLMESSAGE_INFO, r.getHandle());
}


MsiLogTrigger::MsiLogTrigger(MSIHANDLE h):
        msiLogAppender(h),
        oldLogAppender(Logger::defaultLogger().getAppender()),
        teeLogAppender(&msiLogAppender, &oldLogAppender) {
    Logger::defaultLogger().setAppender(teeLogAppender);
}


MsiLogTrigger::~MsiLogTrigger() {
    Logger::defaultLogger().setAppender(oldLogAppender);
}




namespace {
MSIHANDLE openDatabase(const CA& ca) {
    MSIHANDLE h = MsiGetActiveDatabase(ca.getHandle());
    if (h == NULL) {
        JP_THROW(Error(std::string("MsiGetActiveDatabase() failed"),
                                                    ERROR_FUNCTION_FAILED));
    }
    return h;
}

} // namespace

Database::Database(const CA& ca): msiPath(_T("*CA*")),
                                            dbHandle(openDatabase(ca)) {
}

} // namespace msi
