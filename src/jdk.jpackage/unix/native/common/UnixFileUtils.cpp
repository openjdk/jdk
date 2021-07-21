/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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


#include <limits.h>
#include <sys/stat.h>
#include <unistd.h>
#include <filesystem>
#include <dirent.h>
#include <cstring>
#include "FileUtils.h"
#include "ErrorHandling.h"
#include "Log.h"


namespace FileUtils {

bool isFileExists(const tstring &filePath) {
    struct stat statBuffer;
    return (stat(filePath.c_str(), &statBuffer) != -1);
}

tstring toAbsolutePath(const tstring& path) {
    if (path.empty()) {
        char buffer[PATH_MAX] = { 0 };
        char* buf = getcwd(buffer, sizeof(buffer));
        if (buf) {
            tstring result(buf);
            if (result.empty()) {
                JP_THROW(tstrings::any() << "getcwd() returned empty string");
            }
            return result;
        }

        JP_THROW(tstrings::any() << "getcwd() failed. Error: "
                << lastCRTError());
    }

    if (isDirSeparator(path[0])) {
        return path;
    }

    return mkpath() << toAbsolutePath("") << path;
}

// The "release" file in a JDK or other Java runtime is in a directory with
// several sub-dirs, but not a lot (or any) other files. 
// We use width to limit search.
#define WIDTH 8
#define TYPE_FILE 0x08
#define TYPE_DIR 0x04

/*
 * We recursivly search thru "base" (with open DIR "dir" for file named
 * "filename".  We only look at the first "width" files in each dir.
 * When a match is found we add it to "reply" and look no further in that dir.
 */
tstring_array searchDir(const char *base, DIR *dir,
        const tstring& filename, int width, tstring_array reply) {
    struct dirent *dirEntry;
    int count = 0;
    while (((dirEntry = readdir(dir)) != NULL) && (count < width)) {
        if (strchr(dirEntry->d_name, '.') != dirEntry->d_name) {
            if (dirEntry->d_type == TYPE_DIR) {
                tstring newbase = (tstrings::any() << base << "/"
                        << dirEntry->d_name).tstr();
                DIR *subdir = opendir(newbase.c_str());
                if (subdir != NULL) {
                    reply = searchDir(newbase.c_str(), subdir, filename,
                                      width, reply);
                    closedir(subdir);
                }
            } else if (dirEntry->d_type == TYPE_FILE) {
                count++;
                if (strcmp(filename.c_str(), dirEntry->d_name) == 0) {
                    reply.push_back((tstrings::any() << base << "/"
                            << dirEntry->d_name).tstr());
                    LOG_TRACE(tstrings::any() << "found: " << base);
                    break;
                }
            }
        }
    }
    return reply;
}

tstring_array listContents(const tstring& basedir, const tstring& filename) {
    tstring_array reply;
    DIR *directory;
    directory = opendir(basedir.c_str());
    if (directory != NULL) {
        reply = searchDir(basedir.c_str(), directory, filename, WIDTH, reply);
        closedir(directory);
    }
    return reply;
}

tstring stripExeSuffix(const tstring& path) {
    // for unix - there is no suffix to remove
    return path;
}

} //  namespace FileUtils
