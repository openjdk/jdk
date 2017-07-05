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

/** Defines what must be specified for each platform. This class must
    have a no-arg constructor. */

import java.io.*;

public abstract class Platform {
    /** file name templates capture naming conventions */
    protected FileName dummyFileTemplate =
        new FileName(this, "", "", "", "", "", "");

    // The next three must be instantiated in subclasses' constructors

    /** An incl file is produced per .c file and contains all the
        includes it needs */
    protected FileName inclFileTemplate;

    /** A GI (grand-include) file has any file used more than N times
        for precompiled headers */
    protected FileName giFileTemplate;

    /** A GD (grand-dependencies) file that tells Unix make all the
        .o's needed for linking and the include dependencies */
    protected FileName gdFileTemplate;

    // Accessors
    public FileName getInclFileTemplate() {
        return inclFileTemplate;
    }

    public FileName getGIFileTemplate() {
        return giFileTemplate;
    }

    public FileName getGDFileTemplate() {
        return gdFileTemplate;
    }

    // an incl file is the file included by each.c file that includes
    // all needed header files

    public abstract void setupFileTemplates();
    public abstract String[] outerSuffixes();

    /** empty file name -> no grand include file */
    public boolean haveGrandInclude() {
        return (giFileTemplate.nameOfList().length() > 0);
    }

    public boolean writeDeps() {
        return (gdFileTemplate.nameOfList().length() > 0);
    }

    /** <p> A gi file is the grand-include file. It includes in one
        file any file that is included more than a certain number of
        times. </p>

        <p> It is used for precompiled header files. </p>

        <p> It has a source name, that is the file that this program
        generates, and a compiled name; that is the file that is
        included by other files. </p>

        <p> Some platforms have this program actually explictly
        include the preprocessed gi file-- see includeGIInEachIncl().
        </p>

        <p> Also, some platforms need a pragma in the GI file. </p> */
    public boolean includeGIInEachIncl() {
        return false;
    }

    /** For some platforms, e.g. Solaris, include the grand-include
        dependencies in the makefile. For others, e.g. Windows, do
        not. */
    public boolean includeGIDependencies() {
        return false;
    }

    /** Should C/C++ source file be dependent on a file included
        into the grand-include file. */
    public boolean writeDependenciesOnHFilesFromGI() {
        return false;
    }

    /** Default implementation does nothing */
    public void writeGIPragma(PrintWriter out) {
    }

    /** A line with a filename and the noGrandInclude string means
        that this file cannot use the precompiled header. */
    public String noGrandInclude() {
        return "no_precompiled_headers";
    }

    /** A line with a filename and the
        generatePlatformDependentInclude means that an include file
        for the header file must be generated. This file generated include
        file is directly included by the non-platform dependent include file
        (e.g os.hpp includes _os_pd.hpp.incl. So while we notice files that
        are directly dependent on non-platform dependent files from the database
        we must infer the dependence on platform specific files to generate correct
        dependences on the platform specific files. */
    public String generatePlatformDependentInclude() {
        return "generate_platform_dependent_include";
    }

    /** Prefix and suffix strings for emitting Makefile rules */
    public abstract String objFileSuffix();
    public abstract String asmFileSuffix();
    public abstract String dependentPrefix();

    // Exit routines:

    /** Abort means an internal error */
    public void abort() {
        throw new RuntimeException("Internal error");
    }

    /** fatalError is used by clients to stop the system */
    public void fatalError(String msg) {
        System.err.println(msg);
        System.exit(1);
    }

    /** Default implementation performs case-sensitive comparison */
    public boolean fileNameStringEquality(String s1, String s2) {
        return s1.equals(s2);
    }

    public void fileNamePortabilityCheck(String name) {
        if (Character.isUpperCase(name.charAt(0))) {
            fatalError("Error: for the sake of portability we have chosen\n" +
                       "to avoid files starting with an uppercase letter.\n" +
                       "Please rename " + name + ".");
        }
    }

    public void fileNamePortabilityCheck(String name, String matchingName) {
        if (!name.equals(matchingName)) {
            fatalError("Error: file " + name + " also appears as " +
                       matchingName + ".  Case must be consistent for " +
                       "portability.");
        }
    }

    /** max is 31 on mac, so warn */
    public int fileNameLengthLimit() {
        return 45;
    }

    public int defaultGrandIncludeThreshold() {
        return 30;
    }

    /** Not very general, but this is a way to get platform-specific
        files to be written. Default implementation does nothing. */
    public void writePlatformSpecificFiles(Database previousDB,
                                           Database currentDB, String[] args)
        throws IllegalArgumentException, IOException {
    }
}
