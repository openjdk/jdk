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


#ifndef ReleaseFile_h
#define ReleaseFile_h

#include <map>
#include "tstrings.h"


class ReleaseFile {
public:
    template <class Tag> class Id {
    public:
        Id(const tstring::const_pointer str) : str(str) {
        }

        bool operator == (const Id& other) const {
            return tstring(str) == tstring(other.str);
        }

        bool operator != (const Id& other) const {
            return !operator == (other);
        }

        bool operator < (const Id& other) const {
            return tstring(str) < tstring(other.str);
        }

        tstring name() const {
            return tstring(str);
        }

    private:
        tstring::const_pointer str;
    };

    tstring& getVersion();

    tstring_array& getModules();

    bool satisfies(ReleaseFile other, const tstring& versionSpec);

    static ReleaseFile load(const tstring& path);

private:
    tstring version;
    tstring_array modules;
};


#endif // ReleaseFile_h
