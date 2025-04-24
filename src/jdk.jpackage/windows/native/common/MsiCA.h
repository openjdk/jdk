/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef MsiCA_h
#define MsiCA_h

#include <windows.h>
#include <Msi.h>
#include <msidefs.h>
#include <msiquery.h>
#include <map>

#include "Log.h"
#include "Guid.h"


/**
 * Helpers to implement custom actions (CA).
 */
namespace msi {

/**
 * Return values from CA functions.
 */
struct CAStatus {
    // https://msdn.microsoft.com/en-us/library/windows/desktop/aa368072(v=vs.85).aspx
    enum Values {
        Success = ERROR_SUCCESS,

        // Abort installation session.
        UserExit = ERROR_INSTALL_USEREXIT,

        // Unexpected error interrupted installation session.
        FatalError = ERROR_INSTALL_FAILURE,

        // Complete installation session without running further actions.
        ExitNoError = ERROR_NO_MORE_ITEMS
    };
};


/**
 * Wrapper around MSIHANDLE passed in CA function by MSI service.
 * Provides basic functionality to read/write property into the current MSI
 * session.
 */
class CAImpl {
public:
    explicit CAImpl(MSIHANDLE h): handle(h) {
    }

    /**
     * Returns value of a property with the given name.
     * Returns empty string if property with the given name doesn't exist.
     * Throws exception if error occurs.
     */
    tstring getProperty(const tstring& name) const;

    /**
     * Sets property value.
     * Throws exception if error occurs.
     * Throws exception if value is empty string.
     */
    void setProperty(const tstring& name, const tstring& v);

    /**
     * Removes property.
     * Throws exception if error occurs.
     */
    void removeProperty(const tstring& name);

    MSIHANDLE getHandle() const {
        return handle;
    }

private:
    CAImpl(const CAImpl&);
    CAImpl& operator=(const CAImpl&);
private:
    MSIHANDLE handle;
};


/**
 * Provides common functionality for deferred and immediate CAs.
 */
class CAFacade: public CAStatus {
public:
    explicit CAFacade(MSIHANDLE h, UINT* status=NULL): impl(h), status(status) {
    }

    Guid getProductCode() const;

    bool isInMode(MSIRUNMODE v) const;

    // Debug
    tstring getModes() const;

    void exitStatus(CAStatus::Values v) {
        if (status) {
            *status = v;
        }
    }

    void doAction(const tstring& name) const;

    // Replaces all forward slashes with back slashes and ensures
    // the last character is a backslash.
    // Terminating directory paths with backslash is standard for MSI.
    // Do nothing if 'path' is empty string.
    static tstring normalizeDirectoryPath(tstring path);

protected:
    CAImpl impl;
    UINT* status;
};


/**
 * Immediate CA.
 */
class CA: public CAFacade {
public:
    CA(MSIHANDLE h, const tstring& /* name */,
                                    UINT* status=NULL): CAFacade(h, status) {
    }

    tstring getProperty(const tstring& name) const {
        return impl.getProperty(name);
    }

    CA& setProperty(const tstring& name, const tstring& v) {
        impl.setProperty(name, v);
        return *this;
    }

    CA& removeProperty(const tstring& name) {
        impl.removeProperty(name);
        return *this;
    }

    /**
     * Like setProperty(), but do nothing if property with the given name
     * exists and its value is not empty.
     */
    CA& setPropertyIfEmpty(const tstring& name, const tstring& v);

    MSIHANDLE getHandle() const {
        return impl.getHandle();
    }
};


/**
 * Deferred CA.
 */
class DeferredCA: public CAFacade {
public:
    DeferredCA(MSIHANDLE h, const tstring& name,
            UINT* status=NULL): CAFacade(h, status), caArgPropertyName(name) {
    }

    typedef std::map<tstring, tstring> ArgsCtnr;

    DeferredCA& parseArgs() {
        parseArgs(theParsedArgs, getArg());
        return *this;
    }

    tstring getArg() const;

    const ArgsCtnr& parsedArgs() const {
        return theParsedArgs;
    }

    tstring getParsedArg(const tstring& name) const;

    static void parseArgs(ArgsCtnr& dst, const tstring& src);

private:
    ArgsCtnr theParsedArgs;
    tstring caArgPropertyName;
};


/**
 * Write log messages into MSI log.
 */
class MsiLogAppender: public LogAppender {
public:
    explicit MsiLogAppender(MSIHANDLE h);

    virtual void append(const LogEvent& v);
private:
    MSIHANDLE handle;
    long ctorThread;
};


/**
 * Configures logging for the current CA.
 * Log messages that we send with LOG_INFO, LOG_ERROR, etc., go to both
 * the existing log appender and temporary MSI log file managed by
 * MSI service for the running MSI session (if any).
 */
class MsiLogTrigger {
public:
    explicit MsiLogTrigger(MSIHANDLE h);
    ~MsiLogTrigger();
private:
    MsiLogAppender msiLogAppender;
    LogAppender& oldLogAppender;
    TeeLogAppender teeLogAppender;
};

} // namespace msi


//
// Helpers to define CA functions.
//
// Sample usage:
//  Define immediate CA foo:
//      JP_CA(foo) {
//          // `ca` is a local variable of type msi::CA.
//          LOG_TRACE(ca.getProperty("Some property"));
//      }
//
//  Define deferred CA bar:
//      JP_DEFERRED_CA(bar) {
//          // `ca` is a local variable of type msi::DeferredCA.
//          LOG_TRACE(ca.getArg());
//      }
//
// JP_DEFERRED_CA/JP_CA macros take care of everything related to setup CA
// handler:
//  - define CA function with the right calling convention and arguments
//    expected by MSI;
//  - construct local instance of either DeferredCA or CA type to access data
//    in the running MSI session;
//  - setup logging, so that log messages issues with LOG_INFO, LOG_ERROR, etc.
//    macros go to MSI log file;
//  - registers CA function with linker, so there is no need to manage
//    separate .def file with the list of CA functions explicitly.
//
#define JP_CA_BASE(name, ca_type) \
    static void name ## Body(ca_type&); \
    extern "C" UINT name(MSIHANDLE hInstall) { \
        __pragma(comment(linker, "/EXPORT:" __FUNCTION__ "=" __FUNCDNAME__)); \
        const msi::MsiLogTrigger logTrigger(hInstall); \
        JP_DEBUG_BREAK(JP_CA_DEBUG_BREAK, name); \
        LOG_TRACE_FUNCTION(); \
        JP_TRY; \
        UINT status = ca_type::Success; \
        ca_type ca(hInstall, _T(#name), &status); \
        LOG_TRACE(tstrings::any() << "CA modes=[" << ca.getModes() << "]"); \
        name ## Body(ca); \
        return status; \
        JP_CATCH_ALL; \
        return ca_type::FatalError; \
    } \
    static void name ## Body(ca_type& ca)
#define JP_CA(name) JP_CA_BASE(name, msi::CA)
#define JP_DEFERRED_CA(name) JP_CA_BASE(name, msi::DeferredCA)

#define JP_CA_DECLARE(name) \
    extern "C" UINT name(MSIHANDLE); \
    __pragma(comment(linker, "/INCLUDE:" JP_CA_MANGLED_NAME(name)))

#define JP_CA_MANGLED_NAME(name) #name

#endif // #ifndef MsiCA_h
