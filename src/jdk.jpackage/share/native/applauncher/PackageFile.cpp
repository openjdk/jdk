/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

#include <fstream>
#include "PackageFile.h"
#include "Log.h"
#include "FileUtils.h"
#include "ErrorHandling.h"


PackageFile::PackageFile(const tstring& v): packageName(v) {
}


PackageFile PackageFile::loadFromAppDir(const tstring& appDirPath) {
    tstring packageName;
    const tstring packageFilePath =
            FileUtils::mkpath() << appDirPath << _T(".package");
    if (FileUtils::isFileExists(packageFilePath)) {
        LOG_TRACE(tstrings::any() << "Read \"" << packageFilePath
                                  << "\" package file");
        std::ifstream input(packageFilePath);
        if (!input.good()) {
            JP_THROW(tstrings::any() << "Error opening \"" << packageFilePath
                    << "\" file: " << lastCRTError());
        }

        std::string utf8line;
        if (std::getline(input, utf8line)) {
            LOG_TRACE(tstrings::any()
                    << "Package name is [" << utf8line << "]");
            packageName = tstrings::any(utf8line).tstr();
        }
    }

    return PackageFile(packageName);
}
