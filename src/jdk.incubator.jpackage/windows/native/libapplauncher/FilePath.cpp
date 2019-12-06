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

#include "FilePath.h"

#include <algorithm>
#include <list>
#include <ShellAPI.h>

bool FilePath::FileExists(const TString FileName) {
    bool result = false;
    WIN32_FIND_DATA FindFileData;
    TString fileName = FixPathForPlatform(FileName);
    HANDLE handle = FindFirstFile(fileName.data(), &FindFileData);

    if (handle != INVALID_HANDLE_VALUE) {
        if (FILE_ATTRIBUTE_DIRECTORY & FindFileData.dwFileAttributes) {
            result = true;
        }
        else {
            result = true;
        }

        FindClose(handle);
    }
    return result;
}

bool FilePath::DirectoryExists(const TString DirectoryName) {
    bool result = false;
    WIN32_FIND_DATA FindFileData;
    TString directoryName = FixPathForPlatform(DirectoryName);
    HANDLE handle = FindFirstFile(directoryName.data(), &FindFileData);

    if (handle != INVALID_HANDLE_VALUE) {
        if (FILE_ATTRIBUTE_DIRECTORY & FindFileData.dwFileAttributes) {
            result = true;
        }

        FindClose(handle);
    }
    return result;
}

std::string GetLastErrorAsString() {
    // Get the error message, if any.
    DWORD errorMessageID = ::GetLastError();

    if (errorMessageID == 0) {
        return "No error message has been recorded";
    }

    LPSTR messageBuffer = NULL;
    size_t size = FormatMessageA(FORMAT_MESSAGE_ALLOCATE_BUFFER
            | FORMAT_MESSAGE_FROM_SYSTEM | FORMAT_MESSAGE_IGNORE_INSERTS,
            NULL, errorMessageID, MAKELANGID(LANG_NEUTRAL,
            SUBLANG_DEFAULT), (LPSTR)&messageBuffer, 0, NULL);

    std::string message(messageBuffer, size);

    // Free the buffer.
    LocalFree(messageBuffer);

    return message;
}

bool FilePath::DeleteFile(const TString FileName) {
    bool result = false;

    if (FileExists(FileName) == true) {
        TString lFileName = FixPathForPlatform(FileName);
        FileAttributes attributes(lFileName);

        if (attributes.Contains(faReadOnly) == true) {
            attributes.Remove(faReadOnly);
        }

        result = ::DeleteFile(lFileName.data()) == TRUE;
    }

    return result;
}

bool FilePath::DeleteDirectory(const TString DirectoryName) {
    bool result = false;

    if (DirectoryExists(DirectoryName) == true) {
        SHFILEOPSTRUCTW fos = {0};
        TString directoryName = FixPathForPlatform(DirectoryName);
        DynamicBuffer<TCHAR> lDirectoryName(directoryName.size() + 2);
        if (lDirectoryName.GetData() == NULL) {
            return false;
        }
        memcpy(lDirectoryName.GetData(), directoryName.data(),
                (directoryName.size() + 2) * sizeof(TCHAR));
        lDirectoryName[directoryName.size() + 1] = NULL;
        // Double null terminate for SHFileOperation.

        // Delete the folder and everything inside.
        fos.wFunc = FO_DELETE;
        fos.pFrom = lDirectoryName.GetData();
        fos.fFlags = FOF_NO_UI;
        result = SHFileOperation(&fos) == 0;
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
    TString result;
    size_t slash = Path.find_last_of(TRAILING_PATHSEPARATOR);
    if (slash != TString::npos)
        result = Path.substr(0, slash);
    return result;
}

TString FilePath::ExtractFileExt(TString Path) {
    TString result;
    size_t dot = Path.find_last_of('.');

    if (dot != TString::npos) {
        result  = Path.substr(dot, Path.size() - dot);
    }

    return result;
}

TString FilePath::ExtractFileName(TString Path) {
    TString result;

    size_t slash = Path.find_last_of(TRAILING_PATHSEPARATOR);
    if (slash != TString::npos)
        result = Path.substr(slash + 1, Path.size() - slash - 1);

    return result;
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
    // The maximum path that does not require long path prefix. On Windows the
    // maximum path is 260 minus 1 (NUL) but for directories it is 260 minus
    // 12 minus 1 (to allow for the creation of a 8.3 file in the directory).
    const int maxPath = 247;
    if (result.length() > maxPath &&
        result.find(_T("\\\\?\\")) == TString::npos &&
        result.find(_T("\\\\?\\UNC")) == TString::npos) {
        const TString prefix(_T("\\\\"));
        if (!result.compare(0, prefix.size(), prefix)) {
            // UNC path, converting to UNC path in long notation
            result = _T("\\\\?\\UNC") + result.substr(1, result.length());
        } else {
            // converting to non-UNC path in long notation
            result = _T("\\\\?\\") + result;
        }
    }
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

        if (_wmkdir(lpath.data()) == 0) {
            result = true;
        } else {
            result = false;
            break;
        }
    }

    return result;
}

void FilePath::ChangePermissions(TString FileName, bool ownerOnly) {
}

#include <algorithm>

FileAttributes::FileAttributes(const TString FileName, bool FollowLink) {
    FFileName = FileName;
    FFollowLink = FollowLink;
    ReadAttributes();
}

bool FileAttributes::WriteAttributes() {
    bool result = false;

    DWORD attributes = 0;

    for (std::vector<FileAttribute>::const_iterator iterator =
            FAttributes.begin();
        iterator != FAttributes.end(); iterator++) {
        switch (*iterator) {
            case faArchive: {
                attributes = attributes & FILE_ATTRIBUTE_ARCHIVE;
                break;
            }
            case faCompressed: {
                attributes = attributes & FILE_ATTRIBUTE_COMPRESSED;
                break;
            }
            case faDevice: {
                attributes = attributes & FILE_ATTRIBUTE_DEVICE;
                break;
            }
            case faDirectory: {
                attributes = attributes & FILE_ATTRIBUTE_DIRECTORY;
                break;
            }
            case faEncrypted: {
                attributes = attributes & FILE_ATTRIBUTE_ENCRYPTED;
                break;
            }
            case faHidden: {
                attributes = attributes & FILE_ATTRIBUTE_HIDDEN;
                break;
            }
            case faNormal: {
                attributes = attributes & FILE_ATTRIBUTE_NORMAL;
                break;
            }
            case faNotContentIndexed: {
                attributes = attributes & FILE_ATTRIBUTE_NOT_CONTENT_INDEXED;
                break;
            }
            case faOffline: {
                attributes = attributes & FILE_ATTRIBUTE_OFFLINE;
                break;
            }
            case faSystem: {
                attributes = attributes & FILE_ATTRIBUTE_SYSTEM;
                break;
            }
            case faSymbolicLink: {
                attributes = attributes & FILE_ATTRIBUTE_REPARSE_POINT;
                break;
            }
            case faSparceFile: {
                attributes = attributes & FILE_ATTRIBUTE_SPARSE_FILE;
                break;
            }
            case faReadOnly: {
                attributes = attributes & FILE_ATTRIBUTE_READONLY;
                break;
            }
            case faTemporary: {
                attributes = attributes & FILE_ATTRIBUTE_TEMPORARY;
                break;
            }
            case faVirtual: {
                attributes = attributes & FILE_ATTRIBUTE_VIRTUAL;
                break;
            }
        }
    }

    if (::SetFileAttributes(FFileName.data(), attributes) != 0) {
        result = true;
    }

    return result;
}

#define S_ISRUSR(m)    (((m) & S_IRWXU) == S_IRUSR)
#define S_ISWUSR(m)    (((m) & S_IRWXU) == S_IWUSR)
#define S_ISXUSR(m)    (((m) & S_IRWXU) == S_IXUSR)

#define S_ISRGRP(m)    (((m) & S_IRWXG) == S_IRGRP)
#define S_ISWGRP(m)    (((m) & S_IRWXG) == S_IWGRP)
#define S_ISXGRP(m)    (((m) & S_IRWXG) == S_IXGRP)

#define S_ISROTH(m)    (((m) & S_IRWXO) == S_IROTH)
#define S_ISWOTH(m)    (((m) & S_IRWXO) == S_IWOTH)
#define S_ISXOTH(m)    (((m) & S_IRWXO) == S_IXOTH)

bool FileAttributes::ReadAttributes() {
    bool result = false;

    DWORD attributes = ::GetFileAttributes(FFileName.data());

    if (attributes != INVALID_FILE_ATTRIBUTES) {
        result = true;

        if (attributes | FILE_ATTRIBUTE_ARCHIVE) {
            FAttributes.push_back(faArchive);
        }
        if (attributes | FILE_ATTRIBUTE_COMPRESSED) {
            FAttributes.push_back(faCompressed);
        }
        if (attributes | FILE_ATTRIBUTE_DEVICE) {
            FAttributes.push_back(faDevice);
        }
        if (attributes | FILE_ATTRIBUTE_DIRECTORY) {
            FAttributes.push_back(faDirectory);
        }
        if (attributes | FILE_ATTRIBUTE_ENCRYPTED) {
            FAttributes.push_back(faEncrypted);
        }
        if (attributes | FILE_ATTRIBUTE_HIDDEN) {
            FAttributes.push_back(faHidden);
        }
        if (attributes | FILE_ATTRIBUTE_NORMAL) {
            FAttributes.push_back(faNormal);
        }
        if (attributes | FILE_ATTRIBUTE_NOT_CONTENT_INDEXED) {
            FAttributes.push_back(faNotContentIndexed);
        }
        if (attributes | FILE_ATTRIBUTE_SYSTEM) {
            FAttributes.push_back(faSystem);
        }
        if (attributes | FILE_ATTRIBUTE_OFFLINE) {
            FAttributes.push_back(faOffline);
        }
        if (attributes | FILE_ATTRIBUTE_REPARSE_POINT) {
            FAttributes.push_back(faSymbolicLink);
        }
        if (attributes | FILE_ATTRIBUTE_SPARSE_FILE) {
            FAttributes.push_back(faSparceFile);
        }
        if (attributes | FILE_ATTRIBUTE_READONLY ) {
            FAttributes.push_back(faReadOnly);
        }
        if (attributes | FILE_ATTRIBUTE_TEMPORARY) {
            FAttributes.push_back(faTemporary);
        }
        if (attributes | FILE_ATTRIBUTE_VIRTUAL) {
            FAttributes.push_back(faVirtual);
        }
    }

    return result;
}

bool FileAttributes::Valid(const FileAttribute Value) {
    bool result = false;

    switch (Value) {
        case faHidden:
        case faReadOnly: {
            result = true;
            break;
        }
        default:
            break;
    }

    return result;
}

void FileAttributes::Append(FileAttribute Value) {
    if (Valid(Value) == true) {
        FAttributes.push_back(Value);
        WriteAttributes();
    }
}

bool FileAttributes::Contains(FileAttribute Value) {
    bool result = false;

    std::vector<FileAttribute>::const_iterator iterator =
            std::find(FAttributes.begin(), FAttributes.end(), Value);

    if (iterator != FAttributes.end()) {
        result = true;
    }

    return result;
}

void FileAttributes::Remove(FileAttribute Value) {
    if (Valid(Value) == true) {
        std::vector<FileAttribute>::iterator iterator =
            std::find(FAttributes.begin(), FAttributes.end(), Value);

        if (iterator != FAttributes.end()) {
            FAttributes.erase(iterator);
            WriteAttributes();
        }
    }
}
