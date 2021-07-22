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

#include "kludge_c++11.h"

#include <fstream>
#include <algorithm>
#include "ReleaseFile.h"
#include "Toolbox.h"
#include "FileUtils.h"
#include "ErrorHandling.h"
#include "Log.h"


namespace {

} // namespace

tstring& ReleaseFile::getVersion() {
    return version;
}

tstring_array& ReleaseFile::getModules() {
    return modules;
}

bool versionMatch(const tstring& versionSpec, tstring& version) {
    bool greaterThan = tstrings::endsWith(versionSpec, _T("+"));
    tstring reqVer = tstrings::trim(versionSpec, _T("+*"));

    if (tstrings::endsWith(versionSpec, _T("*"))) {
        if (!tstrings::startsWith(version, reqVer)) {
            return false;
        }
    } else {

        tstring_array rvers = tstrings::split(reqVer, _T("."));
        tstring_array overs = tstrings::split(version, _T("."));

        tstring_array::const_iterator rit = rvers.begin();
        const tstring_array::const_iterator rend = rvers.end();

        tstring_array::const_iterator oit = overs.begin();
        const tstring_array::const_iterator oend = overs.end();

        for (; rit != rend; ++rit) {
            int rnum = stoi(*rit);
            const tstring oitstr = (oit != oend) ? *oit++ : _T("0");
            int onum = stoi(oitstr);
            if (greaterThan) {
                if (rnum > onum) {
                    return false;
                }
            } else {
                if (rnum != onum) {
                    return false;
                }
            }
        }
    }
    return true;
}

bool ReleaseFile::greaterThan(const tstring& v1, const tstring& v2) {
    tstring_array vers1 = tstrings::split(v1, _T("."));
    tstring_array vers2 = tstrings::split(v2, _T("."));

    tstring_array::const_iterator it1 = vers1.begin();
    const tstring_array::const_iterator end1 = vers1.end();

    tstring_array::const_iterator it2 = vers2.begin();
    const tstring_array::const_iterator end2 = vers2.end();

    for (; it1 != end1; ++it1) {
        int num1 = stoi(*it1);
        const tstring num2str = (it2 != end2) ? *it2++ : _T("0");
        int num2 = stoi(num2str);
        if (num1 > num2) {
            return true;
        }
        if (num1 < num2) {
            return false;
        }
    }
    return false;
}

bool ReleaseFile::satisfies(ReleaseFile required, const tstring& versionSpec) {
    // We need to insure version satisfies the versionSpec
    if (versionMatch(versionSpec, getVersion())) {
        // now we need to make sure all required modules are there.
        tstring_array reqmods = required.modules;
        tstring_array canmods = modules;

        for (int i=0; i<(int)reqmods.size(); i++) {
            int j = 0;
            for (; j<(int)canmods.size(); j++) {
                if (tstrings::equals(reqmods[i], canmods[j])) {
                    break;
                }
            }
            if (j == (int)canmods.size()) {
                LOG_TRACE(tstrings::any() << " missing mod: " << reqmods[i]
                        << "in version: " << getVersion());
                return false;
            }
        }
        LOG_TRACE(tstrings::any() << " all modules satisfied with: "
                                  << getVersion());
        return true;
    }
    return false;
}

ReleaseFile ReleaseFile::load(const tstring& path) {
    std::ifstream input(path.c_str());

    ReleaseFile releaseFile;

    if (input.good()) {
        std::string utf8line;

        int lineno = 0;
        // we should find JAVA_VERSION and MODULES in the first few lines
        while ((std::getline(input, utf8line)) && lineno++ < 10) {
            const tstring line = tstrings::any(utf8line).tstr();
            tstring::size_type spos = 0, epos = 0;
            if (tstrings::startsWith(line, _T("JAVA_VERSION=\""))) {
                spos = line.find(_T("\""), 0) + 1;
                epos = line.find(_T("\""), spos);
                if (spos > 1 && epos > spos) {
                    releaseFile.version = line.substr(spos, epos-spos);
                }
            } else if (tstrings::startsWith(line, _T("MODULES=\""))) {
                spos = line.find(_T("\""), 0) + 1;
                epos = line.find(_T("\""), spos);
                if (spos > 1 && epos > spos) {
                    tstring mods = line.substr(spos, epos - spos);
                    releaseFile.modules = tstrings::split(mods, _T(" "));

                    tstring_array::const_iterator it;
                    for (it = releaseFile.modules.begin();
                         it != releaseFile.modules.end(); ++it) {
                    }
                }
            }
        }
    }

    return releaseFile;
}

