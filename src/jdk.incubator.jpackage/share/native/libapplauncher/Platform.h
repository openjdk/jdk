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

#ifndef PLATFORM_H
#define PLATFORM_H

#include "PlatformDefs.h"
#include "Properties.h"
#include "OrderedMap.h"
#include "Library.h"

#include <stdio.h>
#include <stdlib.h>
#include <memory.h>
#include <string>
#include <map>
#include <list>
#include <vector>
#include <fstream>

using namespace std;

// Config file sections
#define CONFIG_SECTION_APPLICATION        _T("CONFIG_SECTION_APPLICATION")
#define CONFIG_SECTION_JAVAOPTIONS        _T("CONFIG_SECTION_JAVAOPTIONS")
#define CONFIG_SECTION_APPCDSJAVAOPTIONS  _T("CONFIG_SECTION_APPCDSJAVAOPTIONS")
#define CONFIG_SECTION_ARGOPTIONS         _T("CONFIG_SECTION_ARGOPTIONS")
#define CONFIG_SECTION_APPCDSGENERATECACHEJAVAOPTIONS \
        _T("CONFIG_SECTION_APPCDSGENERATECACHEJAVAOPTIONS")

// Config file keys.
#define CONFIG_VERSION            _T("CONFIG_VERSION")
#define CONFIG_MAINJAR_KEY        _T("CONFIG_MAINJAR_KEY")
#define CONFIG_MAINMODULE_KEY     _T("CONFIG_MAINMODULE_KEY")
#define CONFIG_MAINCLASSNAME_KEY  _T("CONFIG_MAINCLASSNAME_KEY")
#define CONFIG_CLASSPATH_KEY      _T("CONFIG_CLASSPATH_KEY")
#define CONFIG_MODULEPATH_KEY     _T("CONFIG_MODULEPATH_KEY")
#define APP_NAME_KEY              _T("APP_NAME_KEY")
#define CONFIG_SPLASH_KEY         _T("CONFIG_SPLASH_KEY")
#define CONFIG_APP_MEMORY         _T("CONFIG_APP_MEMORY")
#define CONFIG_APP_DEBUG          _T("CONFIG_APP_DEBUG")
#define CONFIG_APPLICATION_INSTANCE _T("CONFIG_APPLICATION_INSTANCE")

#define JAVA_RUNTIME_KEY           _T("JAVA_RUNTIME_KEY")
#define JPACKAGE_APP_DATA_DIR     _T("CONFIG_APP_IDENTIFIER")

struct WideString {
    size_t length;
    wchar_t* data;

    WideString() { length = 0; data = NULL; }
};

struct MultibyteString {
    size_t length;
    char* data;

    MultibyteString() { length = 0; data = NULL; }
};

class Process {
protected:
    std::list<TString> FOutput;

public:
    Process() {
        Output.SetInstance(this);
        Input.SetInstance(this);
    }

    virtual ~Process() {}

    virtual bool IsRunning() = 0;
    virtual bool Terminate() = 0;
    virtual bool Execute(const TString Application,
        const std::vector<TString> Arguments, bool AWait = false) = 0;
    virtual bool Wait() = 0;
    virtual TProcessID GetProcessID() = 0;

    virtual std::list<TString> GetOutput() { return FOutput; }
    virtual void SetInput(TString Value) = 0;

    ReadProperty<Process, std::list<TString>, &Process::GetOutput> Output;
    WriteProperty<Process, TString, &Process::SetInput> Input;
};


template <typename T>
class AutoFreePtr {
private:
    T* FObject;

public:
    AutoFreePtr() {
        FObject = NULL;
    }

    AutoFreePtr(T* Value) {
        FObject = Value;
    }

    ~AutoFreePtr() {
        if (FObject != NULL) {
            delete FObject;
        }
    }

    operator T* () const {
        return FObject;
    }

    T& operator* () const {
        return *FObject;
    }

    T* operator->() const {
        return FObject;
    }

    T** operator&() {
        return &FObject;
    }

    T* operator=(const T * rhs) {
        FObject = rhs;
        return FObject;
    }
};

enum DebugState {dsNone, dsNative, dsJava};
enum MessageResponse {mrOK, mrCancel};
enum AppCDSState {cdsUninitialized, cdsDisabled,
        cdsEnabled, cdsAuto, cdsGenCache};

class Platform {
private:
    AppCDSState FAppCDSState;

protected:
    Platform(void): FAppCDSState(cdsUninitialized) {
    }

public:
    AppCDSState GetAppCDSState() { return FAppCDSState; }
    void SetAppCDSState(AppCDSState Value) { FAppCDSState = Value; }

    static Platform& GetInstance();

    virtual ~Platform(void) {}

public:
    virtual void ShowMessage(TString title, TString description) = 0;
    virtual void ShowMessage(TString description) = 0;
    virtual MessageResponse ShowResponseMessage(TString title,
           TString description) = 0;

    // Caller must free result using delete[].
    virtual TCHAR* ConvertStringToFileSystemString(TCHAR* Source,
            bool &release) = 0;

    // Caller must free result using delete[].
    virtual TCHAR* ConvertFileSystemStringToString(TCHAR* Source,
            bool &release) = 0;

    // Returns:
    // Windows=C:\Users\<username>\AppData\Local
    // Linux=~/.local
    // Mac=~/Library/Application Support
    virtual TString GetAppDataDirectory() = 0;

    virtual TString GetPackageAppDirectory() = 0;
    virtual TString GetPackageLauncherDirectory() = 0;
    virtual TString GetPackageRuntimeBinDirectory() = 0;
    virtual TString GetAppName() = 0;

    virtual TString GetConfigFileName();

    virtual TString GetBundledJavaLibraryFileName(TString RuntimePath) = 0;

    // Caller must free result.
    virtual ISectionalPropertyContainer* GetConfigFile(TString FileName) = 0;

    virtual TString GetModuleFileName() = 0;
    virtual TString GetPackageRootDirectory() = 0;

    virtual Module LoadLibrary(TString FileName) = 0;
    virtual void FreeLibrary(Module Module) = 0;
    virtual Procedure GetProcAddress(Module Module, std::string MethodName) = 0;

    // Caller must free result.
    virtual Process* CreateProcess() = 0;

    virtual bool IsMainThread() = 0;

    // Returns megabytes.
    virtual TPlatformNumber GetMemorySize() = 0;

    virtual std::map<TString, TString> GetKeys();

    virtual void InitStreamLocale(wios *stream) = 0;
    virtual std::list<TString> LoadFromFile(TString FileName);
    virtual void SaveToFile(TString FileName,
             std::list<TString> Contents, bool ownerOnly);

    virtual TString GetTempDirectory() = 0;

    virtual void addPlatformDependencies(JavaLibrary *pJavaLibrary) = 0;

public:
    // String helpers
    // Caller must free result using delete[].
    static void CopyString(char *Destination,
            size_t NumberOfElements, const char *Source);

    // Caller must free result using delete[].
    static void CopyString(wchar_t *Destination,
            size_t NumberOfElements, const wchar_t *Source);

    static WideString MultibyteStringToWideString(const char* value);
    static MultibyteString WideStringToMultibyteString(const wchar_t* value);
};

class Exception: public std::exception {
private:
    TString FMessage;

protected:
    void SetMessage(const TString Message) {
        FMessage = Message;
    }

public:
    explicit Exception() : exception() {}
    explicit Exception(const TString Message) : exception() {
        SetMessage(Message);
    }
    virtual ~Exception() throw() {}

    TString GetMessage() { return FMessage; }
};

#endif // PLATFORM_H
