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

#include "FileAttributes.h"

#include <algorithm>
#include <list>
#include <sys/stat.h>

FileAttributes::FileAttributes(const TString FileName, bool FollowLink) {
    FFileName = FileName;
    FFollowLink = FollowLink;
    ReadAttributes();
}

bool FileAttributes::WriteAttributes() {
    bool result = false;

    mode_t attributes = 0;

    for (std::vector<FileAttribute>::const_iterator iterator =
            FAttributes.begin();
            iterator != FAttributes.end(); iterator++) {
        switch (*iterator) {
            case faBlockSpecial:
            {
                attributes |= S_IFBLK;
                break;
            }
            case faCharacterSpecial:
            {
                attributes |= S_IFCHR;
                break;
            }
            case faFIFOSpecial:
            {
                attributes |= S_IFIFO;
                break;
            }
            case faNormal:
            {
                attributes |= S_IFREG;
                break;
            }
            case faDirectory:
            {
                attributes |= S_IFDIR;
                break;
            }
            case faSymbolicLink:
            {
                attributes |= S_IFLNK;
                break;
            }
            case faSocket:
            {
                attributes |= S_IFSOCK;
                break;
            }

                // Owner
            case faReadOnly:
            {
                attributes |= S_IRUSR;
                break;
            }
            case faWriteOnly:
            {
                attributes |= S_IWUSR;
                break;
            }
            case faReadWrite:
            {
                attributes |= S_IRUSR;
                attributes |= S_IWUSR;
                break;
            }
            case faExecute:
            {
                attributes |= S_IXUSR;
                break;
            }

                // Group
            case faGroupReadOnly:
            {
                attributes |= S_IRGRP;
                break;
            }
            case faGroupWriteOnly:
            {
                attributes |= S_IWGRP;
                break;
            }
            case faGroupReadWrite:
            {
                attributes |= S_IRGRP;
                attributes |= S_IWGRP;
                break;
            }
            case faGroupExecute:
            {
                attributes |= S_IXGRP;
                break;
            }

                // Others
            case faOthersReadOnly:
            {
                attributes |= S_IROTH;
                break;
            }
            case faOthersWriteOnly:
            {
                attributes |= S_IWOTH;
                break;
            }
            case faOthersReadWrite:
            {
                attributes |= S_IROTH;
                attributes |= S_IWOTH;
                break;
            }
            case faOthersExecute:
            {
                attributes |= S_IXOTH;
                break;
            }
            default:
                break;
        }
    }

    if (chmod(FFileName.data(), attributes) == 0) {
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

    struct stat status;

    if (stat(StringToFileSystemString(FFileName), &status) == 0) {
        result = true;

        if (S_ISBLK(status.st_mode) != 0) {
            FAttributes.push_back(faBlockSpecial);
        }
        if (S_ISCHR(status.st_mode) != 0) {
            FAttributes.push_back(faCharacterSpecial);
        }
        if (S_ISFIFO(status.st_mode) != 0) {
            FAttributes.push_back(faFIFOSpecial);
        }
        if (S_ISREG(status.st_mode) != 0) {
            FAttributes.push_back(faNormal);
        }
        if (S_ISDIR(status.st_mode) != 0) {
            FAttributes.push_back(faDirectory);
        }
        if (S_ISLNK(status.st_mode) != 0) {
            FAttributes.push_back(faSymbolicLink);
        }
        if (S_ISSOCK(status.st_mode) != 0) {
            FAttributes.push_back(faSocket);
        }

        // Owner
        if (S_ISRUSR(status.st_mode) != 0) {
            if (S_ISWUSR(status.st_mode) != 0) {
                FAttributes.push_back(faReadWrite);
            } else {
                FAttributes.push_back(faReadOnly);
            }
        } else if (S_ISWUSR(status.st_mode) != 0) {
            FAttributes.push_back(faWriteOnly);
        }

        if (S_ISXUSR(status.st_mode) != 0) {
            FAttributes.push_back(faExecute);
        }

        // Group
        if (S_ISRGRP(status.st_mode) != 0) {
            if (S_ISWGRP(status.st_mode) != 0) {
                FAttributes.push_back(faGroupReadWrite);
            } else {
                FAttributes.push_back(faGroupReadOnly);
            }
        } else if (S_ISWGRP(status.st_mode) != 0) {
            FAttributes.push_back(faGroupWriteOnly);
        }

        if (S_ISXGRP(status.st_mode) != 0) {
            FAttributes.push_back(faGroupExecute);
        }


        // Others
        if (S_ISROTH(status.st_mode) != 0) {
            if (S_ISWOTH(status.st_mode) != 0) {
                FAttributes.push_back(faOthersReadWrite);
            } else {
                FAttributes.push_back(faOthersReadOnly);
            }
        } else if (S_ISWOTH(status.st_mode) != 0) {
            FAttributes.push_back(faOthersWriteOnly);
        }

        if (S_ISXOTH(status.st_mode) != 0) {
            FAttributes.push_back(faOthersExecute);
        }

        if (FFileName.size() > 0 && FFileName[0] == '.') {
            FAttributes.push_back(faHidden);
        }
    }

    return result;
}

bool FileAttributes::Valid(const FileAttribute Value) {
    bool result = false;

    switch (Value) {
        case faReadWrite:
        case faWriteOnly:
        case faExecute:

        case faGroupReadWrite:
        case faGroupWriteOnly:
        case faGroupReadOnly:
        case faGroupExecute:

        case faOthersReadWrite:
        case faOthersWriteOnly:
        case faOthersReadOnly:
        case faOthersExecute:

        case faReadOnly:
            result = true;
            break;

        default:
            break;
    }

    return result;
}

void FileAttributes::Append(FileAttribute Value) {
    if (Valid(Value) == true) {
        if ((Value == faReadOnly && Contains(faWriteOnly) == true) ||
                (Value == faWriteOnly && Contains(faReadOnly) == true)) {
            Value = faReadWrite;
        }

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
        if (Value == faReadOnly && Contains(faReadWrite) == true) {
            Append(faWriteOnly);
            Remove(faReadWrite);
        } else if (Value == faWriteOnly && Contains(faReadWrite) == true) {
            Append(faReadOnly);
            Remove(faReadWrite);
        }

        std::vector<FileAttribute>::iterator iterator =
                std::find(FAttributes.begin(), FAttributes.end(), Value);

        if (iterator != FAttributes.end()) {
            FAttributes.erase(iterator);
            WriteAttributes();
        }
    }
}
