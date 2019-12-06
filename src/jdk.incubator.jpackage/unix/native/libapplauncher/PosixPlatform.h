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

#ifndef POSIXPLATFORM_H
#define POSIXPLATFORM_H

#include "Platform.h"
#include <signal.h>

class PosixPlatform : virtual public Platform {
protected:

    TString fixName(const TString& name);

    virtual TString getTmpDirString() = 0;

public:
    PosixPlatform(void);
    virtual ~PosixPlatform(void);

public:
    virtual MessageResponse ShowResponseMessage(TString title,
            TString description);

    virtual Module LoadLibrary(TString FileName);
    virtual void FreeLibrary(Module AModule);
    virtual Procedure GetProcAddress(Module AModule, std::string MethodName);

    virtual Process* CreateProcess();
    virtual TString GetTempDirectory();
    void InitStreamLocale(wios *stream);
    void addPlatformDependencies(JavaLibrary *pJavaLibrary);
};

class PosixProcess : public Process {
private:
    pid_t FChildPID;
    sigset_t saveblock;
    int FOutputHandle;
    int FInputHandle;
    struct sigaction savintr, savequit;
    bool FRunning;

    void Cleanup();
    bool ReadOutput();

public:
    PosixProcess();
    virtual ~PosixProcess();

    virtual bool IsRunning();
    virtual bool Terminate();
    virtual bool Execute(const TString Application,
            const std::vector<TString> Arguments, bool AWait = false);
    virtual bool Wait();
    virtual TProcessID GetProcessID();
    virtual void SetInput(TString Value);
    virtual std::list<TString> GetOutput();
};

#endif // POSIXPLATFORM_H
