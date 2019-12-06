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

#ifndef MACPLATFORM_H
#define MACPLATFORM_H

#include "Platform.h"
#include "PosixPlatform.h"

class MacPlatform : virtual public Platform, PosixPlatform {
private:
    bool UsePListForConfigFile();

protected:
    virtual TString getTmpDirString();

public:
    MacPlatform(void);
    virtual ~MacPlatform(void);

public:
    virtual void ShowMessage(TString title, TString description);
    virtual void ShowMessage(TString description);

    virtual TCHAR* ConvertStringToFileSystemString(
            TCHAR* Source, bool &release);
    virtual TCHAR* ConvertFileSystemStringToString(
            TCHAR* Source, bool &release);

    virtual TString GetPackageRootDirectory();
    virtual TString GetAppDataDirectory();
    virtual TString GetBundledJavaLibraryFileName(TString RuntimePath);
    virtual TString GetAppName();

    TString GetPackageAppDirectory();
    TString GetPackageLauncherDirectory();
    TString GetPackageRuntimeBinDirectory();

    virtual ISectionalPropertyContainer* GetConfigFile(TString FileName);
    virtual TString GetModuleFileName();

    virtual bool IsMainThread();
    virtual TPlatformNumber GetMemorySize();

    virtual std::map<TString, TString> GetKeys();
};


#endif // MACPLATFORM_H
