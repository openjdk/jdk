/*
 * Copyright (c) 1999, 2001, Oracle and/or its affiliates. All rights reserved.
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

// This program reads an include file database.
// The database should cover each self .c and .h file,
//   but not files in /usr/include
// The database consists of pairs of nonblank words, where the first word is
//   the filename that needs to include the file named by the second word.
// For each .c file, this program generates a fooIncludes.h file that
//  the .c file may include to include all the needed files in the right order.
// It also generates a foo.dep file to include in the makefile.
// Finally it detects cycles, and can work with two files, an old and a new one.
// To incrementally write out only needed files after a small change.
//
// Based on a suggestion by Roland Conybeare, algorithm suggested by Craig
//  Chambers, written by David Ungar, 3/1/89.
//  Added PREFIX, {DEP/INC}_DIR, smaller dep output  10/92  -Urs

// Add something for precompiled headers

// To handle different platforms, I am introducing a platform file.
// The platform file contains lines like:
// os = svr4
//
// Then, when processing the includeDB file, a token such as <os>
// gets replaced by svr4. -- dmu 3/25/97

// Modified to centralize Dependencies to speed up make -- dmu 5/97

public class MakeDeps {

    public static void usage() {
        System.out.println("usage:");
        System.out.println("\tmakeDeps platform-name     platform-file     database-file [MakeDeps args] [platform args]");
        System.out.println("\tmakeDeps diffs platform-name old-platform-file old-database-file new-platform-file new-database-file [MakeDeps args] [platform args]");
        System.out.println("where platform-name is the name of a platform MakeDeps supports");
        System.out.println("(currently \"WinGammaPlatform\" or \"UnixPlatform\")");
        System.out.println("MakeDeps options:");
        System.out.println("  -firstFile [filename]: Specify the first file in link order (i.e.,");
        System.out.println("   to have a well-known function at the start of the output file)");
        System.out.println("  -lastFile [filename]: Specify the last file in link order (i.e.,");
        System.out.println("   to have a well-known function at the end of the output file)");
        System.err.println("WinGammaPlatform platform-specific options:");
        System.err.println("  -sourceBase <path to directory (workspace) " +
                           "containing source files; no trailing slash>");
        System.err.println("  -dspFileName <full pathname to which .dsp file " +
                           "will be written; all parent directories must " +
                           "already exist>");
        System.err.println("  -envVar <environment variable to be inserted " +
                           "into .dsp file, substituting for path given in " +
                           "-sourceBase. Example: HotSpotWorkSpace>");
        System.err.println("  -dllLoc <path to directory in which to put " +
                           "jvm.dll and jvm_g.dll; no trailing slash>");
        System.err.println("  If any of the above are specified, "+
                           "they must all be.");
        System.err.println("  Additional, optional arguments, which can be " +
                           "specified multiple times:");
        System.err.println("    -absoluteInclude <string containing absolute " +
                           "path to include directory>");
        System.err.println("    -relativeInclude <string containing include " +
                           "directory relative to -envVar>");
        System.err.println("    -define <preprocessor flag to be #defined " +
                           "(note: doesn't yet support " +
                           "#define (flag) (value))>");
        System.err.println("    -perFileLine <file> <line>");
        System.err.println("    -conditionalPerFileLine <file> <line for " +
                           "release build> <line for debug build>");
        System.err.println("  (NOTE: To work around a bug in nmake, where " +
                           "you can't have a '#' character in a quoted " +
                           "string, all of the lines outputted have \"#\"" +
                           "prepended)");
        System.err.println("    -startAt <subdir of sourceBase>");
        System.err.println("    -ignoreFile <file which won't be able to be " +
                           "found in the sourceBase because it's generated " +
                           "later>");
        System.err.println("    -additionalFile <file not in database but " +
                           "which should show up in .dsp file, like " +
                           "includeDB_core>");
        System.err.println("    -additionalGeneratedFile <environment variable of " +
                           "generated file's location> <relative path to " +
                           "directory containing file; no trailing slash> " +
                           "<name of file generated later in the build process>");
        System.err.println("    -prelink <build> <desc> <cmds>:");
        System.err.println(" Generate a set of prelink commands for the given BUILD");
        System.err.println(" (\"Debug\" or \"Release\"). The prelink description and commands");
        System.err.println(" are both quoted strings.");
        System.err.println("    Default includes: \".\"");
        System.err.println("    Default defines: WIN32, _WINDOWS, \"HOTSPOT_BUILD_USER=$(USERNAME)\"");
    }

    public static void main(String[] args) {
        try {
            if (args.length < 3) {
                usage();
                System.exit(1);
            }

            int argc = 0;
            boolean diffMode = false;
            if (args[argc].equals("diffs")) {
                diffMode = true;
                ++argc;
            }

            String platformName = args[argc++];
            Class platformClass = Class.forName(platformName);

            String plat1 = null;
            String db1 = null;
            String plat2 = null;
            String db2 = null;

            String firstFile = null;
            String lastFile = null;

            int numOptionalArgs =
                (diffMode ? (args.length - 6) : (args.length - 3));
            if (numOptionalArgs < 0) {
                usage();
                System.exit(1);
            }

            plat1 = args[argc++];
            db1   = args[argc++];

            if (diffMode) {
              plat2 = args[argc++];
              db2   = args[argc++];
            }

            // argc now points at start of optional arguments, if any

            try {
              boolean gotOne = true;
              while (gotOne && (argc < args.length - 1)) {
                gotOne = false;
                String arg = args[argc];
                if (arg.equals("-firstFile")) {
                  firstFile = args[argc + 1];
                  argc += 2;
                  gotOne = true;
                } else if (arg.equals("-lastFile")) {
                  lastFile = args[argc + 1];
                  argc += 2;
                  gotOne = true;
                }
              }
            }
            catch (Exception e) {
              e.printStackTrace();
              usage();
              System.exit(1);
            }

            Platform platform = (Platform) platformClass.newInstance();
            platform.setupFileTemplates();
            long t = platform.defaultGrandIncludeThreshold();

            String[] platformArgs = null;
            int numPlatformArgs = args.length - argc;
            if (numPlatformArgs > 0) {
                platformArgs = new String[numPlatformArgs];
                int offset = argc;
                while (argc < args.length) {
                  platformArgs[argc - offset] = args[argc];
                  ++argc;
                }
            }

            // If you want to change the threshold, change the default
            // "grand include" threshold in Platform.java, or override
            // it in the platform-specific file like UnixPlatform.java

            Database previous = new Database(platform, t);
            Database current = new Database(platform, t);

            previous.canBeMissing();

            if (firstFile != null) {
              previous.setFirstFile(firstFile);
              current.setFirstFile(firstFile);
            }
            if (lastFile != null) {
              previous.setLastFile(lastFile);
              current.setLastFile(lastFile);
            }

            if (diffMode) {
                System.out.println("Old database:");
                previous.get(plat1, db1);
                previous.compute();
                System.out.println("New database:");
                current.get(plat2, db2);
                current.compute();
                System.out.println("Deltas:");
                current.putDiffs(previous);
            } else {
                System.out.println("New database:");
                current.get(plat1, db1);
                current.compute();
                current.put();
            }

            if (platformArgs != null) {
                // Allow the platform to write platform-specific files
                platform.writePlatformSpecificFiles(previous, current,
                                                    platformArgs);
            }
        }
        catch (Exception e) {
            e.printStackTrace();
              System.exit(1);
        }
    }
}
