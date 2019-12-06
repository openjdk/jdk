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

#ifndef FILEPATH_H
#define FILEPATH_H

#include "Platform.h"
#include "PlatformString.h"
#include "FileAttribute.h"

#include <vector>

class FileAttributes {
private:
    TString FFileName;
    bool FFollowLink;
    std::vector<FileAttribute> FAttributes;

    bool WriteAttributes();
    bool ReadAttributes();
    bool Valid(const FileAttribute Value);

public:
    FileAttributes(const TString FileName, bool FollowLink = true);

    void Append(const FileAttribute Value);
    bool Contains(const FileAttribute Value);
    void Remove(const FileAttribute Value);
};

class FilePath {
private:
    FilePath(void) {}
    ~FilePath(void) {}

public:
    static bool FileExists(const TString FileName);
    static bool DirectoryExists(const TString DirectoryName);

    static bool DeleteFile(const TString FileName);
    static bool DeleteDirectory(const TString DirectoryName);

    static TString ExtractFilePath(TString Path);
    static TString ExtractFileExt(TString Path);
    static TString ExtractFileName(TString Path);
    static TString ChangeFileExt(TString Path, TString Extension);

    static TString IncludeTrailingSeparator(const TString value);
    static TString IncludeTrailingSeparator(const char* value);
    static TString IncludeTrailingSeparator(const wchar_t* value);
    static TString FixPathForPlatform(TString Path);
    static TString FixPathSeparatorForPlatform(TString Path);
    static TString PathSeparator();

    static bool CreateDirectory(TString Path, bool ownerOnly);
    static void ChangePermissions(TString FileName, bool ownerOnly);
};

#endif //FILEPATH_H
