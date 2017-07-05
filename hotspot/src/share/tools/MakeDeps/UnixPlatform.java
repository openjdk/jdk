/*
 * Copyright (c) 1999, 2005, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
 *
 */

public class UnixPlatform extends Platform {
    public void setupFileTemplates() {
        inclFileTemplate = new FileName(this,
            "incls/", "_", "",             ".incl", "", ""
        );
        giFileTemplate = new FileName(this,
            "incls/", "",  "_precompiled", ".incl", "", ""
        );
        gdFileTemplate = new FileName(this,
            "",       "",  "Dependencies", "",      "", ""
        );
    }

    private static String[] suffixes = { ".cpp", ".c", ".s" };

    public String[] outerSuffixes() {
        return suffixes;
    }

    public String objFileSuffix() {
        return ".o";
    }

    public String asmFileSuffix() {
        return ".i";
    }

    public String dependentPrefix() {
        return "";
    }

    /** Do not change this; unless you fix things so precompiled
        header files get translated into make dependencies. - Ungar */
    public int defaultGrandIncludeThreshold() {
       if (System.getProperty("USE_PRECOMPILED_HEADER") != null)
          return 30;
       else
          return 1 << 30;
    }

    /** For Unix make, include the dependencies for precompiled header
        files. */
    public boolean includeGIDependencies() {
        return false;
    }

    /** Should C/C++ source file be dependent on a file included
        into the grand-include file.
        On Unix with precompiled headers we don't want each file to be
        dependent on grand-include file. Instead each C/C++ source file
        is depended on each own set of files, and recompiled only when
        files from this set are changed. */
    public boolean writeDependenciesOnHFilesFromGI() {
        return System.getProperty("USE_PRECOMPILED_HEADER") != null;
    }
}
