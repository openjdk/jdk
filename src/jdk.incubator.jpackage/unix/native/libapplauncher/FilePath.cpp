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

#include "PlatformDefs.h"
#include "FilePath.h"

#include <algorithm>
#include <list>
#include <sys/stat.h>

bool FilePath::FileExists(const TString FileName) {
    bool result = false;
    struct stat buf;

    if ((stat(StringToFileSystemString(FileName), &buf) == 0) &&
            (S_ISREG(buf.st_mode) != 0)) {
        result = true;
    }

    return result;
}

bool FilePath::DirectoryExists(const TString DirectoryName) {
    bool result = false;

    struct stat buf;

    if ((stat(StringToFileSystemString(DirectoryName), &buf) == 0) &&
            (S_ISDIR(buf.st_mode) != 0)) {
        result = true;
    }

    return result;
}

bool FilePath::DeleteFile(const TString FileName) {
    bool result = false;

    if (FileExists(FileName) == true) {
        if (unlink(StringToFileSystemString(FileName)) == 0) {
            result = true;
        }
    }

    return result;
}

bool FilePath::DeleteDirectory(const TString DirectoryName) {
    bool result = false;

    if (DirectoryExists(DirectoryName) == true) {
        if (unlink(StringToFileSystemString(DirectoryName)) == 0) {
            result = true;
        }
    }

    return result;
}

TString FilePath::IncludeTrailingSeparator(const TString value) {
    TString result = value;

    if (value.size() > 0) {
        TString::iterator i = result.end();
        i--;

        if (*i != TRAILING_PATHSEPARATOR) {
            result += TRAILING_PATHSEPARATOR;
        }
    }

    return result;
}

TString FilePath::IncludeTrailingSeparator(const char* value) {
    TString lvalue = PlatformString(value).toString();
    return IncludeTrailingSeparator(lvalue);
}

TString FilePath::IncludeTrailingSeparator(const wchar_t* value) {
    TString lvalue = PlatformString(value).toString();
    return IncludeTrailingSeparator(lvalue);
}

TString FilePath::ExtractFilePath(TString Path) {
    return dirname(StringToFileSystemString(Path));
}

TString FilePath::ExtractFileExt(TString Path) {
    TString result;
    size_t dot = Path.find_last_of('.');

    if (dot != TString::npos) {
        result = Path.substr(dot, Path.size() - dot);
    }

    return result;
}

TString FilePath::ExtractFileName(TString Path) {
    return basename(StringToFileSystemString(Path));
}

TString FilePath::ChangeFileExt(TString Path, TString Extension) {
    TString result;
    size_t dot = Path.find_last_of('.');

    if (dot != TString::npos) {
        result = Path.substr(0, dot) + Extension;
    }

    if (result.empty() == true) {
        result = Path;
    }

    return result;
}

TString FilePath::FixPathForPlatform(TString Path) {
    TString result = Path;
    std::replace(result.begin(), result.end(),
            BAD_TRAILING_PATHSEPARATOR, TRAILING_PATHSEPARATOR);
    return result;
}

TString FilePath::FixPathSeparatorForPlatform(TString Path) {
    TString result = Path;
    std::replace(result.begin(), result.end(),
            BAD_PATH_SEPARATOR, PATH_SEPARATOR);
    return result;
}

TString FilePath::PathSeparator() {
    TString result;
    result = PATH_SEPARATOR;
    return result;
}

bool FilePath::CreateDirectory(TString Path, bool ownerOnly) {
    bool result = false;

    std::list<TString> paths;
    TString lpath = Path;

    while (lpath.empty() == false && DirectoryExists(lpath) == false) {
        paths.push_front(lpath);
        lpath = ExtractFilePath(lpath);
    }

    for (std::list<TString>::iterator iterator = paths.begin();
            iterator != paths.end(); iterator++) {
        lpath = *iterator;

        mode_t mode = S_IRWXU;
        if (!ownerOnly) {
            mode |= S_IRWXG | S_IROTH | S_IXOTH;
        }
        if (mkdir(StringToFileSystemString(lpath), mode) == 0) {
            result = true;
        } else {
            result = false;
            break;
        }
    }

    return result;
}

void FilePath::ChangePermissions(TString FileName, bool ownerOnly) {
    mode_t mode = S_IRWXU;
    if (!ownerOnly) {
        mode |= S_IRWXG | S_IROTH | S_IXOTH;
    }
    chmod(FileName.data(), mode);
}
