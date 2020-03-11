/*
 * Copyright (c) 2014, 2020, Oracle and/or its affiliates. All rights reserved.
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

#include "JavaVirtualMachine.h"
#include "WindowsPlatform.h"
#include "Package.h"
#include "Helpers.h"
#include "PlatformString.h"
#include "Macros.h"

#include <map>
#include <vector>
#include <regex>
#include <fstream>
#include <locale>
#include <codecvt>

using namespace std;

#define WINDOWS_JPACKAGE_TMP_DIR \
        L"\\AppData\\Local\\Java\\JPackage\\tmp"

class Registry {
private:
    HKEY FKey;
    HKEY FOpenKey;
    bool FOpen;

public:

    Registry(HKEY Key) {
        FOpen = false;
        FKey = Key;
    }

    ~Registry() {
        Close();
    }

    void Close() {
        if (FOpen == true) {
            RegCloseKey(FOpenKey);
        }
    }

    bool Open(TString SubKey) {
        bool result = false;
        Close();

        if (RegOpenKeyEx(FKey, SubKey.data(), 0, KEY_READ, &FOpenKey) ==
                ERROR_SUCCESS) {
            result = true;
        }

        return result;
    }

    std::list<TString> GetKeys() {
        std::list<TString> result;
        DWORD count;

        if (RegQueryInfoKey(FOpenKey, NULL, NULL, NULL, NULL, NULL, NULL,
                &count, NULL, NULL, NULL, NULL) == ERROR_SUCCESS) {

            DWORD length = 255;
            DynamicBuffer<TCHAR> buffer(length);
            if (buffer.GetData() == NULL) {
                return result;
            }

            for (unsigned int index = 0; index < count; index++) {
                buffer.Zero();
                DWORD status = RegEnumValue(FOpenKey, index, buffer.GetData(),
                        &length, NULL, NULL, NULL, NULL);

                while (status == ERROR_MORE_DATA) {
                    length = length * 2;
                    if (!buffer.Resize(length)) {
                        return result;
                    }
                    status = RegEnumValue(FOpenKey, index, buffer.GetData(),
                            &length, NULL, NULL, NULL, NULL);
                }

                if (status == ERROR_SUCCESS) {
                    TString value = buffer.GetData();
                    result.push_back(value);
                }
            }
        }

        return result;
    }

    TString ReadString(TString Name) {
        TString result;
        DWORD length;
        DWORD dwRet;
        DynamicBuffer<wchar_t> buffer(0);
        length = 0;

        dwRet = RegQueryValueEx(FOpenKey, Name.data(), NULL, NULL, NULL,
                &length);
        if (dwRet == ERROR_MORE_DATA || dwRet == 0) {
            if (!buffer.Resize(length + 1)) {
                return result;
            }
            dwRet = RegQueryValueEx(FOpenKey, Name.data(), NULL, NULL,
                    (LPBYTE) buffer.GetData(), &length);
            result = buffer.GetData();
        }

        return result;
    }
};

WindowsPlatform::WindowsPlatform(void) : Platform() {
    FMainThread = ::GetCurrentThreadId();
}

WindowsPlatform::~WindowsPlatform(void) {
}

TString WindowsPlatform::GetPackageAppDirectory() {
    return FilePath::IncludeTrailingSeparator(
            GetPackageRootDirectory()) + _T("app");
}

TString WindowsPlatform::GetPackageLauncherDirectory() {
    return  GetPackageRootDirectory();
}

TString WindowsPlatform::GetPackageRuntimeBinDirectory() {
    return FilePath::IncludeTrailingSeparator(GetPackageRootDirectory()) + _T("runtime\\bin");
}

TCHAR* WindowsPlatform::ConvertStringToFileSystemString(TCHAR* Source,
        bool &release) {
    // Not Implemented.
    return NULL;
}

TCHAR* WindowsPlatform::ConvertFileSystemStringToString(TCHAR* Source,
        bool &release) {
    // Not Implemented.
    return NULL;
}

TString WindowsPlatform::GetPackageRootDirectory() {
    TString result;
    TString filename = GetModuleFileName();
    return FilePath::ExtractFilePath(filename);
}

TString WindowsPlatform::GetAppDataDirectory() {
    TString result;
    TCHAR path[MAX_PATH];

    if (SHGetFolderPath(NULL, CSIDL_APPDATA, NULL, 0, path) == S_OK) {
        result = path;
    }

    return result;
}

TString WindowsPlatform::GetAppName() {
    TString result = GetModuleFileName();
    result = FilePath::ExtractFileName(result);
    result = FilePath::ChangeFileExt(result, _T(""));
    return result;
}

void WindowsPlatform::ShowMessage(TString title, TString description) {
    MessageBox(NULL, description.data(),
            !title.empty() ? title.data() : description.data(),
            MB_ICONERROR | MB_OK);
}

void WindowsPlatform::ShowMessage(TString description) {
    TString appname = GetModuleFileName();
    appname = FilePath::ExtractFileName(appname);
    MessageBox(NULL, description.data(), appname.data(), MB_ICONERROR | MB_OK);
}

MessageResponse WindowsPlatform::ShowResponseMessage(TString title,
        TString description) {
    MessageResponse result = mrCancel;

    if (::MessageBox(NULL, description.data(), title.data(), MB_OKCANCEL) ==
            IDOK) {
        result = mrOK;
    }

    return result;
}

TString WindowsPlatform::GetBundledJavaLibraryFileName(TString RuntimePath) {
    TString result = FilePath::IncludeTrailingSeparator(RuntimePath) +
            _T("jre\\bin\\jli.dll");

    if (FilePath::FileExists(result) == false) {
        result = FilePath::IncludeTrailingSeparator(RuntimePath) +
                _T("bin\\jli.dll");
    }

    return result;
}

ISectionalPropertyContainer* WindowsPlatform::GetConfigFile(TString FileName) {
    IniFile *result = new IniFile();
    if (result == NULL) {
        return NULL;
    }

    result->LoadFromFile(FileName);

    return result;
}

TString WindowsPlatform::GetModuleFileName() {
    TString result;
    DynamicBuffer<wchar_t> buffer(MAX_PATH);
    if (buffer.GetData() == NULL) {
        return result;
    }

    ::GetModuleFileName(NULL, buffer.GetData(),
            static_cast<DWORD> (buffer.GetSize()));

    while (ERROR_INSUFFICIENT_BUFFER == GetLastError()) {
        if (!buffer.Resize(buffer.GetSize() * 2)) {
            return result;
        }
        ::GetModuleFileName(NULL, buffer.GetData(),
                static_cast<DWORD> (buffer.GetSize()));
    }

    result = buffer.GetData();
    return result;
}

Module WindowsPlatform::LoadLibrary(TString FileName) {
    return ::LoadLibrary(FileName.data());
}

void WindowsPlatform::FreeLibrary(Module AModule) {
    ::FreeLibrary((HMODULE) AModule);
}

Procedure WindowsPlatform::GetProcAddress(Module AModule,
        std::string MethodName) {
    return ::GetProcAddress((HMODULE) AModule, MethodName.c_str());
}

bool WindowsPlatform::IsMainThread() {
    bool result = (FMainThread == ::GetCurrentThreadId());
    return result;
}

TString WindowsPlatform::GetTempDirectory() {
    TString result;
    PWSTR userDir = 0;

    if (SUCCEEDED(SHGetKnownFolderPath(
            FOLDERID_Profile,
            0,
            NULL,
            &userDir))) {
        result = userDir;
        result += WINDOWS_JPACKAGE_TMP_DIR;
        CoTaskMemFree(userDir);
    }

    return result;
}

static BOOL CALLBACK enumWindows(HWND winHandle, LPARAM lParam) {
    DWORD pid = (DWORD) lParam, wPid = 0;
    GetWindowThreadProcessId(winHandle, &wPid);
    if (pid == wPid) {
        SetForegroundWindow(winHandle);
        return FALSE;
    }
    return TRUE;
}

TPlatformNumber WindowsPlatform::GetMemorySize() {
    SYSTEM_INFO si;
    GetSystemInfo(&si);
    size_t result = (size_t) si.lpMaximumApplicationAddress;
    result = result / 1048576; // Convert from bytes to megabytes.
    return result;
}

std::vector<TString> FilterList(std::vector<TString> &Items,
        std::wregex Pattern) {
    std::vector<TString> result;

    for (std::vector<TString>::iterator it = Items.begin();
            it != Items.end(); ++it) {
        TString item = *it;
        std::wsmatch match;

        if (std::regex_search(item, match, Pattern)) {
            result.push_back(item);
        }
    }
    return result;
}

Process* WindowsPlatform::CreateProcess() {
    return new WindowsProcess();
}

void WindowsPlatform::InitStreamLocale(wios *stream) {
    const std::locale empty_locale = std::locale::empty();
    const std::locale utf8_locale =
                std::locale(empty_locale, new std::codecvt_utf8<wchar_t>());
    stream->imbue(utf8_locale);
}

void WindowsPlatform::addPlatformDependencies(JavaLibrary *pJavaLibrary) {
    if (pJavaLibrary == NULL) {
        return;
    }

    if (FilePath::FileExists(_T("msvcr100.dll")) == true) {
        pJavaLibrary->AddDependency(_T("msvcr100.dll"));
    }

    TString runtimeBin = GetPackageRuntimeBinDirectory();
    SetDllDirectory(runtimeBin.c_str());
}

void Platform::CopyString(char *Destination,
        size_t NumberOfElements, const char *Source) {
    strcpy_s(Destination, NumberOfElements, Source);

    if (NumberOfElements > 0) {
        Destination[NumberOfElements - 1] = '\0';
    }
}

void Platform::CopyString(wchar_t *Destination,
        size_t NumberOfElements, const wchar_t *Source) {
    wcscpy_s(Destination, NumberOfElements, Source);

    if (NumberOfElements > 0) {
        Destination[NumberOfElements - 1] = '\0';
    }
}

// Owner must free the return value.
MultibyteString Platform::WideStringToMultibyteString(
        const wchar_t* value) {
    MultibyteString result;
    size_t count = 0;

    if (value == NULL) {
        return result;
    }

    count = WideCharToMultiByte(CP_UTF8, 0, value, -1, NULL, 0, NULL, NULL);

    if (count > 0) {
        result.data = new char[count + 1];
        result.length = WideCharToMultiByte(CP_UTF8, 0, value, -1,
                result.data, (int)count, NULL, NULL);
    }

    return result;
}

// Owner must free the return value.
WideString Platform::MultibyteStringToWideString(const char* value) {
    WideString result;
    size_t count = 0;

    if (value == NULL) {
        return result;
    }

    count = MultiByteToWideChar(CP_THREAD_ACP, MB_ERR_INVALID_CHARS,
                                value, -1, NULL, 0);

    if (count > 0) {
        result.data = new wchar_t[count];
        result.length = MultiByteToWideChar(CP_THREAD_ACP, MB_ERR_INVALID_CHARS,
                                            value, -1, result.data, (int)count);
        if (result.length == 0) {
            delete[] result.data;
            result.data = NULL;
        }
    }

    return result;
}

FileHandle::FileHandle(std::wstring FileName) {
    FHandle = ::CreateFile(FileName.data(), GENERIC_READ, FILE_SHARE_READ,
            NULL, OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL, 0);
}

FileHandle::~FileHandle() {
    if (IsValid() == true) {
        ::CloseHandle(FHandle);
    }
}

bool FileHandle::IsValid() {
    return FHandle != INVALID_HANDLE_VALUE;
}

HANDLE FileHandle::GetHandle() {
    return FHandle;
}

FileMappingHandle::FileMappingHandle(HANDLE FileHandle) {
    FHandle = ::CreateFileMapping(FileHandle, NULL, PAGE_READONLY, 0, 0, NULL);
}

bool FileMappingHandle::IsValid() {
    return FHandle != NULL;
}

FileMappingHandle::~FileMappingHandle() {
    if (IsValid() == true) {
        ::CloseHandle(FHandle);
    }
}

HANDLE FileMappingHandle::GetHandle() {
    return FHandle;
}

FileData::FileData(HANDLE Handle) {
    FBaseAddress = ::MapViewOfFile(Handle, FILE_MAP_READ, 0, 0, 0);
}

FileData::~FileData() {
    if (IsValid() == true) {
        ::UnmapViewOfFile(FBaseAddress);
    }
}

bool FileData::IsValid() {
    return FBaseAddress != NULL;
}

LPVOID FileData::GetBaseAddress() {
    return FBaseAddress;
}

WindowsLibrary::WindowsLibrary(std::wstring FileName) {
    FFileName = FileName;
}

std::vector<TString> WindowsLibrary::GetImports() {
    std::vector<TString> result;
    FileHandle library(FFileName);

    if (library.IsValid() == true) {
        FileMappingHandle mapping(library.GetHandle());

        if (mapping.IsValid() == true) {
            FileData fileData(mapping.GetHandle());

            if (fileData.IsValid() == true) {
                PIMAGE_DOS_HEADER dosHeader =
                        (PIMAGE_DOS_HEADER) fileData.GetBaseAddress();
                PIMAGE_FILE_HEADER pImgFileHdr =
                        (PIMAGE_FILE_HEADER) fileData.GetBaseAddress();
                if (dosHeader->e_magic == IMAGE_DOS_SIGNATURE) {
                    result = DumpPEFile(dosHeader);
                }
            }
        }
    }

    return result;
}

// Given an RVA, look up the section header that encloses it and return a
// pointer to its IMAGE_SECTION_HEADER

PIMAGE_SECTION_HEADER WindowsLibrary::GetEnclosingSectionHeader(DWORD rva,
        PIMAGE_NT_HEADERS pNTHeader) {
    PIMAGE_SECTION_HEADER result = 0;
    PIMAGE_SECTION_HEADER section = IMAGE_FIRST_SECTION(pNTHeader);

    for (unsigned index = 0; index < pNTHeader->FileHeader.NumberOfSections;
            index++, section++) {
        // Is the RVA is within this section?
        if ((rva >= section->VirtualAddress) &&
                (rva < (section->VirtualAddress + section->Misc.VirtualSize))) {
            result = section;
        }
    }

    return result;
}

LPVOID WindowsLibrary::GetPtrFromRVA(DWORD rva, PIMAGE_NT_HEADERS pNTHeader,
        DWORD imageBase) {
    LPVOID result = 0;
    PIMAGE_SECTION_HEADER pSectionHdr = GetEnclosingSectionHeader(rva,
            pNTHeader);

    if (pSectionHdr != NULL) {
        INT delta = (INT) (
                pSectionHdr->VirtualAddress - pSectionHdr->PointerToRawData);
        DWORD_PTR dwp = (DWORD_PTR) (imageBase + rva - delta);
        result = reinterpret_cast<LPVOID> (dwp); // VS2017 - FIXME
    }

    return result;
}

std::vector<TString> WindowsLibrary::GetImportsSection(DWORD base,
        PIMAGE_NT_HEADERS pNTHeader) {
    std::vector<TString> result;

    // Look up where the imports section is located. Normally in
    // the .idata section,
    // but not necessarily so. Therefore, grab the RVA from the data dir.
    DWORD importsStartRVA = pNTHeader->OptionalHeader.DataDirectory[
            IMAGE_DIRECTORY_ENTRY_IMPORT].VirtualAddress;

    if (importsStartRVA != NULL) {
        // Get the IMAGE_SECTION_HEADER that contains the imports. This is
        // usually the .idata section, but doesn't have to be.
        PIMAGE_SECTION_HEADER pSection =
                GetEnclosingSectionHeader(importsStartRVA, pNTHeader);

        if (pSection != NULL) {
            PIMAGE_IMPORT_DESCRIPTOR importDesc =
                    (PIMAGE_IMPORT_DESCRIPTOR) GetPtrFromRVA(
                    importsStartRVA, pNTHeader, base);

            if (importDesc != NULL) {
                while (true) {
                    // See if we've reached an empty IMAGE_IMPORT_DESCRIPTOR
                    if ((importDesc->TimeDateStamp == 0) &&
                            (importDesc->Name == 0)) {
                        break;
                    }

                    std::string filename = (char*) GetPtrFromRVA(
                            importDesc->Name, pNTHeader, base);
                    result.push_back(PlatformString(filename));
                    importDesc++; // advance to next IMAGE_IMPORT_DESCRIPTOR
                }
            }
        }
    }

    return result;
}

std::vector<TString> WindowsLibrary::DumpPEFile(PIMAGE_DOS_HEADER dosHeader) {
    std::vector<TString> result;
    // all of this is VS2017 - FIXME
    DWORD_PTR dwDosHeaders = reinterpret_cast<DWORD_PTR> (dosHeader);
    DWORD_PTR dwPIHeaders = dwDosHeaders + (DWORD) (dosHeader->e_lfanew);

    PIMAGE_NT_HEADERS pNTHeader =
            reinterpret_cast<PIMAGE_NT_HEADERS> (dwPIHeaders);

    // Verify that the e_lfanew field gave us a reasonable
    // pointer and the PE signature.
    // TODO: To really fix JDK-8131321 this condition needs to be changed.
    // There is a matching change
    // in JavaVirtualMachine.cpp that also needs to be changed.
    if (pNTHeader->Signature == IMAGE_NT_SIGNATURE) {
        DWORD base = (DWORD) (dwDosHeaders);
        result = GetImportsSection(base, pNTHeader);
    }

    return result;
}

#include <TlHelp32.h>

WindowsJob::WindowsJob() {
    FHandle = NULL;
}

WindowsJob::~WindowsJob() {
    if (FHandle != NULL) {
        CloseHandle(FHandle);
    }
}

HANDLE WindowsJob::GetHandle() {
    if (FHandle == NULL) {
        FHandle = CreateJobObject(NULL, NULL); // GLOBAL

        if (FHandle == NULL) {
            ::MessageBox(0, _T("Could not create job object"),
                    _T("TEST"), MB_OK);
        } else {
            JOBOBJECT_EXTENDED_LIMIT_INFORMATION jeli = {0};

            // Configure all child processes associated with
            // the job to terminate when the
            jeli.BasicLimitInformation.LimitFlags =
                    JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE;
            if (0 == SetInformationJobObject(FHandle,
                    JobObjectExtendedLimitInformation, &jeli, sizeof (jeli))) {
                ::MessageBox(0, _T("Could not SetInformationJobObject"),
                        _T("TEST"), MB_OK);
            }
        }
    }

    return FHandle;
}

// Initialize static member of WindowsProcess
WindowsJob WindowsProcess::FJob;

WindowsProcess::WindowsProcess() : Process() {
    FRunning = false;
}

WindowsProcess::~WindowsProcess() {
    Terminate();
}

void WindowsProcess::Cleanup() {
    CloseHandle(FProcessInfo.hProcess);
    CloseHandle(FProcessInfo.hThread);
}

bool WindowsProcess::IsRunning() {
    bool result = false;

    HANDLE handle = ::CreateToolhelp32Snapshot(TH32CS_SNAPALL, 0);
    if (handle == INVALID_HANDLE_VALUE) {
        return false;
    }

    PROCESSENTRY32 process = {0};
    process.dwSize = sizeof (process);

    if (::Process32First(handle, &process)) {
        do {
            if (process.th32ProcessID == FProcessInfo.dwProcessId) {
                result = true;
                break;
            }
        } while (::Process32Next(handle, &process));
    }

    CloseHandle(handle);

    return result;
}

bool WindowsProcess::Terminate() {
    bool result = false;

    if (IsRunning() == true && FRunning == true) {
        FRunning = false;
    }

    return result;
}

bool WindowsProcess::Execute(const TString Application,
        const std::vector<TString> Arguments, bool AWait) {
    bool result = false;

    if (FRunning == false) {
        FRunning = true;

        STARTUPINFO startupInfo;
        ZeroMemory(&startupInfo, sizeof (startupInfo));
        startupInfo.cb = sizeof (startupInfo);
        ZeroMemory(&FProcessInfo, sizeof (FProcessInfo));

        TString command = Application;

        for (std::vector<TString>::const_iterator iterator = Arguments.begin();
                iterator != Arguments.end(); iterator++) {
            command += TString(_T(" ")) + *iterator;
        }

        if (::CreateProcess(Application.data(), (wchar_t*)command.data(), NULL,
                NULL, FALSE, 0, NULL, NULL, &startupInfo, &FProcessInfo)
                == FALSE) {
            TString message = PlatformString::Format(
                    _T("Error: Unable to create process %s"),
                    Application.data());
            throw Exception(message);
        } else {
            if (FJob.GetHandle() != NULL) {
                if (::AssignProcessToJobObject(FJob.GetHandle(),
                        FProcessInfo.hProcess) == 0) {
                    // Failed to assign process to job. It doesn't prevent
                    // anything from continuing so continue.
                }
            }

            // Wait until child process exits.
            if (AWait == true) {
                Wait();
                // Close process and thread handles.
                Cleanup();
            }
        }
    }

    return result;
}

bool WindowsProcess::Wait() {
    bool result = false;

    WaitForSingleObject(FProcessInfo.hProcess, INFINITE);
    return result;
}

TProcessID WindowsProcess::GetProcessID() {
    return FProcessInfo.dwProcessId;
}

bool WindowsProcess::ReadOutput() {
    bool result = false;
    // TODO implement
    return result;
}

void WindowsProcess::SetInput(TString Value) {
    // TODO implement
}

std::list<TString> WindowsProcess::GetOutput() {
    ReadOutput();
    return Process::GetOutput();
}
