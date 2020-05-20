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


#ifndef JvmLauncher_h
#define JvmLauncher_h

#include "tstrings.h"

class CfgFile;


class Jvm {
public:
    Jvm& initFromConfigFile(const CfgFile& cfgFile);

    Jvm& addArgument(const tstring& value) {
        args.push_back(tstrings::any(value).str());
        return *this;
    }

    Jvm& setPath(const tstring& v) {
        jvmPath = v;
        return *this;
    }

    tstring getPath() const {
        return jvmPath;
    }

    void launch();

private:
    tstring jvmPath;
    std::vector<std::string> args;
};

#endif // JvmLauncher_h
