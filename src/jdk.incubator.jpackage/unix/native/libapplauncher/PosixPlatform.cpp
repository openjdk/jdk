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

#include "PosixPlatform.h"

#include "PlatformString.h"
#include "FilePath.h"
#include "Helpers.h"

#include <assert.h>
#include <stdbool.h>
#include <sys/types.h>
#include <unistd.h>
#include <sys/file.h>
#include <sys/stat.h>
#include <sys/wait.h>
#include <errno.h>
#include <limits.h>
#include <pwd.h>
#include <iostream>
#include <algorithm>
#include <dlfcn.h>
#include <signal.h>

using namespace std;

PosixPlatform::PosixPlatform(void) {
}

PosixPlatform::~PosixPlatform(void) {
}

TString PosixPlatform::GetTempDirectory() {
    struct passwd* pw = getpwuid(getuid());
    TString homedir(pw->pw_dir);
    homedir += getTmpDirString();
    if (!FilePath::DirectoryExists(homedir)) {
        if (!FilePath::CreateDirectory(homedir, false)) {
            homedir.clear();
        }
    }

    return homedir;
}

TString PosixPlatform::fixName(const TString& name) {
    TString fixedName(name);
    const TString chars("?:*<>/\\");
    for (TString::const_iterator it = chars.begin(); it != chars.end(); it++) {
        fixedName.erase(std::remove(fixedName.begin(),
                fixedName.end(), *it), fixedName.end());
    }
    return fixedName;
}

MessageResponse PosixPlatform::ShowResponseMessage(TString title,
        TString description) {
    MessageResponse result = mrCancel;

    printf("%s %s (Y/N)\n", PlatformString(title).toPlatformString(),
            PlatformString(description).toPlatformString());
    fflush(stdout);

    std::string input;
    std::cin >> input;

    if (input == "Y") {
        result = mrOK;
    }

    return result;
}

Module PosixPlatform::LoadLibrary(TString FileName) {
    return dlopen(StringToFileSystemString(FileName), RTLD_LAZY);
}

void PosixPlatform::FreeLibrary(Module AModule) {
    dlclose(AModule);
}

Procedure PosixPlatform::GetProcAddress(Module AModule,
        std::string MethodName) {
    return dlsym(AModule, PlatformString(MethodName));
}

Process* PosixPlatform::CreateProcess() {
    return new PosixProcess();
}

void PosixPlatform::addPlatformDependencies(JavaLibrary *pJavaLibrary) {
}

void Platform::CopyString(char *Destination,
        size_t NumberOfElements, const char *Source) {
    strncpy(Destination, Source, NumberOfElements);

    if (NumberOfElements > 0) {
        Destination[NumberOfElements - 1] = '\0';
    }
}

void Platform::CopyString(wchar_t *Destination,
        size_t NumberOfElements, const wchar_t *Source) {
    wcsncpy(Destination, Source, NumberOfElements);

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

    count = wcstombs(NULL, value, 0);
    if (count > 0) {
        result.data = new char[count + 1];
        result.data[count] = '\0';
        result.length = count;
        wcstombs(result.data, value, count);
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

    count = mbstowcs(NULL, value, 0);
    if (count > 0) {
        result.data = new wchar_t[count + 1];
        result.data[count] = '\0';
        result.length = count;
        mbstowcs(result.data, value, count);
    }

    return result;
}

void PosixPlatform::InitStreamLocale(wios *stream) {
    // Nothing to do for POSIX platforms.
}

PosixProcess::PosixProcess() : Process() {
    FChildPID = 0;
    FRunning = false;
    FOutputHandle = 0;
    FInputHandle = 0;
}

PosixProcess::~PosixProcess() {
    Terminate();
}

bool PosixProcess::ReadOutput() {
    bool result = false;

    if (FOutputHandle != 0 && IsRunning() == true) {
        char buffer[4096] = {0};

        ssize_t count = read(FOutputHandle, buffer, sizeof (buffer));

        if (count == -1) {
            if (errno == EINTR) {
                // continue;
            } else {
                perror("read");
                exit(1);
            }
        } else if (count == 0) {
            // break;
        } else {
            std::list<TString> output = Helpers::StringToArray(buffer);
            FOutput.splice(FOutput.end(), output, output.begin(), output.end());
            result = true;
        }
    }

    return false;
}

bool PosixProcess::IsRunning() {
    bool result = false;

    if (kill(FChildPID, 0) == 0) {
        result = true;
    }

    return result;
}

bool PosixProcess::Terminate() {
    bool result = false;

    if (IsRunning() == true && FRunning == true) {
        FRunning = false;
        Cleanup();
        int status = kill(FChildPID, SIGTERM);

        if (status == 0) {
            result = true;
        } else {
#ifdef DEBUG
            if (errno == EINVAL) {
                printf("Kill error: The value of the sig argument is an invalid or unsupported signal number.");
            } else if (errno == EPERM) {
                printf("Kill error: The process does not have permission to send the signal to any receiving process.");
            } else if (errno == ESRCH) {
                printf("Kill error: No process or process group can be found corresponding to that specified by pid.");
            }
#endif // DEBUG
            if (IsRunning() == true) {
                status = kill(FChildPID, SIGKILL);

                if (status == 0) {
                    result = true;
                }
            }
        }
    }

    return result;
}

bool PosixProcess::Wait() {
    bool result = false;

    int status = 0;
    pid_t wpid = 0;

    wpid = wait(&status);
    if (!WIFEXITED(status) || WEXITSTATUS(status) != 0) {
        if (errno != EINTR) {
            status = -1;
        }
    }

#ifdef DEBUG
    if (WIFEXITED(status)) {
        printf("child exited, status=%d\n", WEXITSTATUS(status));
    } else if (WIFSIGNALED(status)) {
        printf("child killed (signal %d)\n", WTERMSIG(status));
    } else if (WIFSTOPPED(status)) {
        printf("child stopped (signal %d)\n", WSTOPSIG(status));
#ifdef WIFCONTINUED // Not all implementations support this
    } else if (WIFCONTINUED(status)) {
        printf("child continued\n");
#endif // WIFCONTINUED
    } else { // Non-standard case -- may never happen
        printf("Unexpected status (0x%x)\n", status);
    }
#endif // DEBUG

    if (wpid != -1) {
        result = true;
    }

    return result;
}

TProcessID PosixProcess::GetProcessID() {
    return FChildPID;
}

void PosixProcess::SetInput(TString Value) {
    if (FInputHandle != 0) {
        if (write(FInputHandle, Value.data(), Value.size()) < 0) {
            throw Exception(_T("Internal Error - write failed"));
        }
    }
}

std::list<TString> PosixProcess::GetOutput() {
    ReadOutput();
    return Process::GetOutput();
}
