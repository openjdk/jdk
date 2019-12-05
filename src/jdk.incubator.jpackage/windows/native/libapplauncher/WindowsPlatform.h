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

#ifndef WINDOWSPLATFORM_H
#define WINDOWSPLATFORM_H

#include <Windows.h>
#include "Platform.h"

class WindowsPlatform : virtual public Platform {
private:
    DWORD FMainThread;

public:
    WindowsPlatform(void);
    virtual ~WindowsPlatform(void);

    virtual TCHAR* ConvertStringToFileSystemString(TCHAR* Source,
            bool &release);
    virtual TCHAR* ConvertFileSystemStringToString(TCHAR* Source,
            bool &release);

    virtual void ShowMessage(TString title, TString description);
    virtual void ShowMessage(TString description);
    virtual MessageResponse ShowResponseMessage(TString title,
            TString description);

    virtual TString GetPackageRootDirectory();
    virtual TString GetAppDataDirectory();
    virtual TString GetAppName();
    virtual TString GetBundledJavaLibraryFileName(TString RuntimePath);
    TString GetPackageAppDirectory();
    TString GetPackageLauncherDirectory();
    TString GetPackageRuntimeBinDirectory();

    virtual ISectionalPropertyContainer* GetConfigFile(TString FileName);

    virtual TString GetModuleFileName();
    virtual Module LoadLibrary(TString FileName);
    virtual void FreeLibrary(Module AModule);
    virtual Procedure GetProcAddress(Module AModule, std::string MethodName);

    virtual Process* CreateProcess();

    virtual bool IsMainThread();
    virtual TPlatformNumber GetMemorySize();

    virtual TString GetTempDirectory();
    void InitStreamLocale(wios *stream);
    void addPlatformDependencies(JavaLibrary *pJavaLibrary);
};

class FileHandle {
private:
    HANDLE FHandle;

public:
    FileHandle(std::wstring FileName);
    ~FileHandle();

    bool IsValid();
    HANDLE GetHandle();
};


class FileMappingHandle {
private:
    HANDLE FHandle;

public:
    FileMappingHandle(HANDLE FileHandle);
    ~FileMappingHandle();

    bool IsValid();
    HANDLE GetHandle();
};


class FileData {
private:
    LPVOID FBaseAddress;

public:
    FileData(HANDLE Handle);
    ~FileData();

    bool IsValid();
    LPVOID GetBaseAddress();
};


class WindowsLibrary {
private:
    TString FFileName;

    // Given an RVA, look up the section header that encloses it and return a
    // pointer to its IMAGE_SECTION_HEADER
    static PIMAGE_SECTION_HEADER GetEnclosingSectionHeader(DWORD rva,
            PIMAGE_NT_HEADERS pNTHeader);
    static LPVOID GetPtrFromRVA(DWORD rva, PIMAGE_NT_HEADERS pNTHeader,
            DWORD imageBase);
    static std::vector<TString> GetImportsSection(DWORD base,
            PIMAGE_NT_HEADERS pNTHeader);
    static std::vector<TString> DumpPEFile(PIMAGE_DOS_HEADER dosHeader);

public:
    WindowsLibrary(const TString FileName);

    std::vector<TString> GetImports();
};


class WindowsJob {
private:
    HANDLE FHandle;

public:
    WindowsJob();
    ~WindowsJob();

    HANDLE GetHandle();
};


class WindowsProcess : public Process {
private:
    bool FRunning;

    PROCESS_INFORMATION FProcessInfo;
    static WindowsJob FJob;

    void Cleanup();
    bool ReadOutput();

public:
    WindowsProcess();
    virtual ~WindowsProcess();

    virtual bool IsRunning();
    virtual bool Terminate();
    virtual bool Execute(const TString Application,
            const std::vector<TString> Arguments, bool AWait = false);
    virtual bool Wait();
    virtual TProcessID GetProcessID();
    virtual void SetInput(TString Value);
    virtual std::list<TString> GetOutput();
};

#endif // WINDOWSPLATFORM_H
