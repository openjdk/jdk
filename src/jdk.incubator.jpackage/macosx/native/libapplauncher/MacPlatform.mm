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

#include "Platform.h"

#include "MacPlatform.h"
#include "Helpers.h"
#include "Package.h"
#include "PropertyFile.h"
#include "IniFile.h"

#include <sys/sysctl.h>
#include <pthread.h>
#include <vector>
#include <signal.h>
#include <mach-o/dyld.h>

#import <Foundation/Foundation.h>
#import <AppKit/NSRunningApplication.h>

#include <CoreFoundation/CoreFoundation.h>
#include <CoreFoundation/CFString.h>

#ifdef __OBJC__
#import <Cocoa/Cocoa.h>
#endif //__OBJC__

#define MAC_JPACKAGE_TMP_DIR \
        "/Library/Application Support/Java/JPackage/tmp"

NSString* StringToNSString(TString Value) {
    NSString* result = [NSString stringWithCString : Value.c_str()
            encoding : [NSString defaultCStringEncoding]];
    return result;
}

FileSystemStringToString::FileSystemStringToString(const TCHAR* value) {
    bool release = false;
    PlatformString lvalue = PlatformString(value);
    Platform& platform = Platform::GetInstance();
    TCHAR* buffer = platform.ConvertFileSystemStringToString(lvalue, release);
    FData = buffer;

    if (buffer != NULL && release == true) {
        delete[] buffer;
    }
}

FileSystemStringToString::operator TString() {
    return FData;
}

StringToFileSystemString::StringToFileSystemString(const TString &value) {
    FRelease = false;
    PlatformString lvalue = PlatformString(value);
    Platform& platform = Platform::GetInstance();
    FData = platform.ConvertStringToFileSystemString(lvalue, FRelease);
}

StringToFileSystemString::~StringToFileSystemString() {
    if (FRelease == true) {
        delete[] FData;
    }
}

StringToFileSystemString::operator TCHAR* () {
    return FData;
}

MacPlatform::MacPlatform(void) : Platform(), PosixPlatform() {
}

MacPlatform::~MacPlatform(void) {
}

TString MacPlatform::GetPackageAppDirectory() {
    return FilePath::IncludeTrailingSeparator(
            GetPackageRootDirectory()) + _T("app");
}

TString MacPlatform::GetPackageLauncherDirectory() {
    return FilePath::IncludeTrailingSeparator(
            GetPackageRootDirectory()) + _T("MacOS");
}

TString MacPlatform::GetPackageRuntimeBinDirectory() {
    return FilePath::IncludeTrailingSeparator(GetPackageRootDirectory()) +
            _T("runtime/Contents/Home/bin");
}

bool MacPlatform::UsePListForConfigFile() {
    return FilePath::FileExists(GetConfigFileName()) == false;
}

void MacPlatform::ShowMessage(TString Title, TString Description) {
    NSString *ltitle = StringToNSString(Title);
    NSString *ldescription = StringToNSString(Description);

    NSLog(@"%@:%@", ltitle, ldescription);
}

void MacPlatform::ShowMessage(TString Description) {
    TString appname = GetModuleFileName();
    appname = FilePath::ExtractFileName(appname);
    ShowMessage(appname, Description);
}

TString MacPlatform::getTmpDirString() {
    return TString(MAC_JPACKAGE_TMP_DIR);
}

TCHAR* MacPlatform::ConvertStringToFileSystemString(TCHAR* Source,
        bool &release) {
    TCHAR* result = NULL;
    release = false;
    CFStringRef StringRef = CFStringCreateWithCString(kCFAllocatorDefault,
            Source, kCFStringEncodingUTF8);

    if (StringRef != NULL) {
        @ try {
            CFIndex length =
                    CFStringGetMaximumSizeOfFileSystemRepresentation(StringRef);
            result = new char[length + 1];
            if (result != NULL) {
                if (CFStringGetFileSystemRepresentation(StringRef,
                        result, length)) {
                    release = true;
                } else {
                    delete[] result;
                    result = NULL;
                }
            }
        }
        @finally
        {
            CFRelease(StringRef);
        }
    }

    return result;
}

TCHAR* MacPlatform::ConvertFileSystemStringToString(TCHAR* Source,
        bool &release) {
    TCHAR* result = NULL;
    release = false;
    CFStringRef StringRef = CFStringCreateWithFileSystemRepresentation(
            kCFAllocatorDefault, Source);

    if (StringRef != NULL) {
        @ try {
            CFIndex length = CFStringGetLength(StringRef);

            if (length > 0) {
                CFIndex maxSize = CFStringGetMaximumSizeForEncoding(
                        length, kCFStringEncodingUTF8);

                result = new char[maxSize + 1];
                if (result != NULL) {
                    if (CFStringGetCString(StringRef, result, maxSize,
                            kCFStringEncodingUTF8) == true) {
                        release = true;
                    } else {
                        delete[] result;
                        result = NULL;
                    }
                }
            }
        }
        @finally
        {
            CFRelease(StringRef);
        }
    }

    return result;
}

TString MacPlatform::GetPackageRootDirectory() {
    NSBundle *mainBundle = [NSBundle mainBundle];
    NSString *mainBundlePath = [mainBundle bundlePath];
    NSString *contentsPath =
            [mainBundlePath stringByAppendingString : @"/Contents"];
    TString result = [contentsPath UTF8String];
    return result;
}

TString MacPlatform::GetAppDataDirectory() {
    TString result;
    NSArray *paths = NSSearchPathForDirectoriesInDomains(
            NSApplicationSupportDirectory, NSUserDomainMask, YES);
    NSString *applicationSupportDirectory = [paths firstObject];
    result = [applicationSupportDirectory UTF8String];
    return result;
}

TString MacPlatform::GetBundledJavaLibraryFileName(TString RuntimePath) {
    TString result;

    // first try lib/, then lib/jli
    result = FilePath::IncludeTrailingSeparator(RuntimePath) +
            _T("Contents/Home/lib/libjli.dylib");

    if (FilePath::FileExists(result) == false) {
        result = FilePath::IncludeTrailingSeparator(RuntimePath) +
                _T("Contents/Home/lib/jli/libjli.dylib");

        if (FilePath::FileExists(result) == false) {
            // cannot find
            NSLog(@"Cannot find libjli.dysym!");
            result = _T("");
        }
    }

    return result;
}

TString MacPlatform::GetAppName() {
    NSString *appName = [[NSProcessInfo processInfo] processName];
    TString result = [appName UTF8String];
    return result;
}

void PosixProcess::Cleanup() {
    if (FOutputHandle != 0) {
        close(FOutputHandle);
        FOutputHandle = 0;
    }

    if (FInputHandle != 0) {
        close(FInputHandle);
        FInputHandle = 0;
    }

    sigaction(SIGINT, &savintr, (struct sigaction *) 0);
    sigaction(SIGQUIT, &savequit, (struct sigaction *) 0);
    sigprocmask(SIG_SETMASK, &saveblock, (sigset_t *) 0);
}

#define PIPE_READ 0
#define PIPE_WRITE 1

bool PosixProcess::Execute(const TString Application,
        const std::vector<TString> Arguments, bool AWait) {
    bool result = false;

    if (FRunning == false) {
        FRunning = true;

        int handles[2];

        if (pipe(handles) == -1) {
            return false;
        }

        struct sigaction sa;
        sa.sa_handler = SIG_IGN;
        sigemptyset(&sa.sa_mask);
        sa.sa_flags = 0;
        sigemptyset(&savintr.sa_mask);
        sigemptyset(&savequit.sa_mask);
        sigaction(SIGINT, &sa, &savintr);
        sigaction(SIGQUIT, &sa, &savequit);
        sigaddset(&sa.sa_mask, SIGCHLD);
        sigprocmask(SIG_BLOCK, &sa.sa_mask, &saveblock);

        FChildPID = fork();

        // PID returned by vfork is 0 for the child process and the
        // PID of the child process for the parent.
        if (FChildPID == -1) {
            // Error
            TString message = PlatformString::Format(
                    _T("Error: Unable to create process %s"),
                    Application.data());
            throw Exception(message);
        } else if (FChildPID == 0) {
            Cleanup();
            TString command = Application;

            for (std::vector<TString>::const_iterator iterator =
                    Arguments.begin(); iterator != Arguments.end();
                    iterator++) {
                command += TString(_T(" ")) + *iterator;
            }
#ifdef DEBUG
            printf("%s\n", command.data());
#endif // DEBUG

            dup2(handles[PIPE_READ], STDIN_FILENO);
            dup2(handles[PIPE_WRITE], STDOUT_FILENO);

            close(handles[PIPE_READ]);
            close(handles[PIPE_WRITE]);

            execl("/bin/sh", "sh", "-c", command.data(), (char *) 0);

            _exit(127);
        } else {
            FOutputHandle = handles[PIPE_READ];
            FInputHandle = handles[PIPE_WRITE];

            if (AWait == true) {
                ReadOutput();
                Wait();
                Cleanup();
                FRunning = false;
                result = true;
            } else {
                result = true;
            }
        }
    }

    return result;
}

void AppendPListArrayToIniFile(NSDictionary *infoDictionary,
        IniFile *result, TString Section) {
    NSString *sectionKey =
            [NSString stringWithUTF8String : PlatformString(Section).toMultibyte()];
    NSDictionary *array = [infoDictionary objectForKey : sectionKey];

    for (id option in array) {
        if ([option isKindOfClass : [NSString class]]) {
            TString arg = [option UTF8String];

            TString name;
            TString value;

            if (Helpers::SplitOptionIntoNameValue(arg, name, value) == true) {
                result->Append(Section, name, value);
            }
        }
    }
}

void AppendPListDictionaryToIniFile(NSDictionary *infoDictionary,
        IniFile *result, TString Section, bool FollowSection = true) {
    NSDictionary *dictionary = NULL;

    if (FollowSection == true) {
        NSString *sectionKey = [NSString stringWithUTF8String : PlatformString(
                Section).toMultibyte()];
        dictionary = [infoDictionary objectForKey : sectionKey];
    } else {
        dictionary = infoDictionary;
    }

    for (id key in dictionary) {
        id option = [dictionary valueForKey : key];

        if ([key isKindOfClass : [NSString class]] &&
                [option isKindOfClass : [NSString class]]) {
            TString name = [key UTF8String];
            TString value = [option UTF8String];
            result->Append(Section, name, value);
        }
    }
}

// Convert parts of the info.plist to the INI format the rest of the jpackage
// uses unless a jpackage config file exists.
ISectionalPropertyContainer* MacPlatform::GetConfigFile(TString FileName) {
    IniFile* result = new IniFile();
    if (result == NULL) {
        return NULL;
    }

    if (UsePListForConfigFile() == false) {
        result->LoadFromFile(FileName);
    } else {
        NSBundle *mainBundle = [NSBundle mainBundle];
        NSDictionary *infoDictionary = [mainBundle infoDictionary];
        std::map<TString, TString> keys = GetKeys();

        // JPackage options.
        AppendPListDictionaryToIniFile(infoDictionary, result,
                keys[CONFIG_SECTION_APPLICATION], false);

        // jvmargs
        AppendPListArrayToIniFile(infoDictionary, result,
                keys[CONFIG_SECTION_JAVAOPTIONS]);

        // Generate AppCDS Cache
        AppendPListDictionaryToIniFile(infoDictionary, result,
                keys[CONFIG_SECTION_APPCDSJAVAOPTIONS]);
        AppendPListDictionaryToIniFile(infoDictionary, result,
                keys[CONFIG_SECTION_APPCDSGENERATECACHEJAVAOPTIONS]);

        // args
        AppendPListArrayToIniFile(infoDictionary, result,
                keys[CONFIG_SECTION_ARGOPTIONS]);
    }

    return result;
}

TString GetModuleFileNameOSX() {
    Dl_info module_info;
    if (dladdr(reinterpret_cast<void*> (GetModuleFileNameOSX),
            &module_info) == 0) {
        // Failed to find the symbol we asked for.
        return std::string();
    }
    return TString(module_info.dli_fname);
}

TString MacPlatform::GetModuleFileName() {
    TString result;
    DynamicBuffer<TCHAR> buffer(MAX_PATH);
    uint32_t size = buffer.GetSize();

    if (_NSGetExecutablePath(buffer.GetData(), &size) == 0) {
        result = FileSystemStringToString(buffer.GetData());
    }

    return result;
}

bool MacPlatform::IsMainThread() {
    bool result = (pthread_main_np() == 1);
    return result;
}

TPlatformNumber MacPlatform::GetMemorySize() {
    unsigned long long memory = [[NSProcessInfo processInfo] physicalMemory];

    // Convert from bytes to megabytes.
    TPlatformNumber result = memory / 1048576;

    return result;
}

std::map<TString, TString> MacPlatform::GetKeys() {
    std::map<TString, TString> keys;

    if (UsePListForConfigFile() == false) {
        return Platform::GetKeys();
    } else {
        keys.insert(std::map<TString, TString>::value_type(CONFIG_VERSION,
                _T("app.version")));
        keys.insert(std::map<TString, TString>::value_type(CONFIG_MAINJAR_KEY,
                _T("JavaMainJarName")));
        keys.insert(std::map<TString,
                TString>::value_type(CONFIG_MAINMODULE_KEY,
                _T("JavaMainModuleName")));
        keys.insert(std::map<TString, TString>::value_type(
                CONFIG_MAINCLASSNAME_KEY, _T("JavaMainClassName")));
        keys.insert(std::map<TString, TString>::value_type(
                CONFIG_CLASSPATH_KEY, _T("JavaAppClasspath")));
        keys.insert(std::map<TString, TString>::value_type(APP_NAME_KEY,
                _T("CFBundleName")));
        keys.insert(std::map<TString, TString>::value_type(JAVA_RUNTIME_KEY,
                _T("JavaRuntime")));
        keys.insert(std::map<TString,
                TString>::value_type(JPACKAGE_APP_DATA_DIR,
                _T("CFBundleIdentifier")));

        keys.insert(std::map<TString, TString>::value_type(CONFIG_SPLASH_KEY,
                _T("app.splash")));
        keys.insert(std::map<TString, TString>::value_type(CONFIG_APP_MEMORY,
                _T("app.memory")));
        keys.insert(std::map<TString, TString>::value_type(CONFIG_APP_DEBUG,
                _T("app.debug")));
        keys.insert(std::map<TString, TString>::value_type(
                CONFIG_APPLICATION_INSTANCE, _T("app.application.instance")));

        keys.insert(std::map<TString, TString>::value_type(
                CONFIG_SECTION_APPLICATION, _T("Application")));
        keys.insert(std::map<TString, TString>::value_type(
                CONFIG_SECTION_JAVAOPTIONS, _T("JavaOptions")));
        keys.insert(std::map<TString, TString>::value_type(
                CONFIG_SECTION_APPCDSJAVAOPTIONS, _T("AppCDSJavaOptions")));
        keys.insert(std::map<TString, TString>::value_type(
                CONFIG_SECTION_APPCDSGENERATECACHEJAVAOPTIONS,
                _T("AppCDSGenerateCacheJavaOptions")));
        keys.insert(std::map<TString, TString>::value_type(
                CONFIG_SECTION_ARGOPTIONS, _T("ArgOptions")));
    }

    return keys;
}
