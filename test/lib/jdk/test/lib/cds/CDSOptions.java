/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates. All rights reserved.
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
 */
package jdk.test.lib.cds;

import java.util.ArrayList;

// This class represents options used
// during creation of CDS archive and/or running JVM with a CDS archive
public class CDSOptions {
    public String xShareMode = "on";
    public String archiveName;
    public ArrayList<String> prefix = new ArrayList<String>();
    public ArrayList<String> suffix = new ArrayList<String>();
    public boolean useSystemArchive = false;

    // classes to be archived
    public String[] classList;

    // Indicate whether to append "-version" when using CDS Archive.
    // Most of tests will use '-version'
    public boolean useVersion = true;


    public CDSOptions() {
    }


    public CDSOptions addPrefix(String... prefix) {
        for (String s : prefix) this.prefix.add(s);
        return this;
    }

    public CDSOptions addPrefix(String prefix[], String... extra) {
        for (String s : prefix) this.prefix.add(s);
        for (String s : extra) this.prefix.add(s);
        return this;
    }

    public CDSOptions addSuffix(String... suffix) {
        for (String s : suffix) this.suffix.add(s);
        return this;
    }

    public CDSOptions addSuffix(String suffix[], String... extra) {
        for (String s : suffix) this.suffix.add(s);
        for (String s : extra) this.suffix.add(s);
        return this;
    }

    public CDSOptions setXShareMode(String mode) {
        this.xShareMode = mode;
        return this;
    }


    public CDSOptions setArchiveName(String name) {
        this.archiveName = name;
        return this;
    }


    public CDSOptions setUseVersion(boolean use) {
        this.useVersion = use;
        return this;
    }

    public CDSOptions setUseSystemArchive(boolean use) {
        this.useSystemArchive = use;
        return this;
    }

    public CDSOptions setClassList(String[] list) {
        this.classList = list;
        return this;
    }
    public CDSOptions setClassList(ArrayList<String> list) {
        String array[] = new String[list.size()];
        list.toArray(array);
        this.classList = array;
        return this;
    }

    // Call by CDSTestUtils.runWithArchive() and TestCommon.runWithArchive().
    //
    // Example:
    //  - The dumping will be done with the default G1GC so we can generate
    //    the archived heap.
    //  - The runtime execution will be done with the EpsilonGC, to test its
    //    ability to load the the archived heap.
    //
    // jtreg -vmoptions:-Dtest.cds.runtime.options=-XX:+UnlockExperimentalVMOptions,-XX:+UseEpsilonGC \
    //       test/hotspot/jtreg/runtime/cds
    public ArrayList<String> getRuntimePrefix() {
        ArrayList<String> cmdline = new ArrayList<>();

        String jtropts = System.getProperty("test.cds.runtime.options");
        if (jtropts != null) {
            for (String s : jtropts.split(",")) {
                if (!disabledRuntimePrefixes.contains(s)) {
                    cmdline.add(s);
                }
            }
        }

        for (String p : prefix) {
            cmdline.add(p);
        }

        return cmdline;
    }

    static ArrayList<String> disabledRuntimePrefixes = new ArrayList<>();

    // Do not use the command-line option s, even if it's specified in -Dtest.cds.runtime.options
    private static void disableRuntimePrefix(String s) {
        disabledRuntimePrefixes.add(s);
    }

    // Do not use the command-line option "-XX:+UseEpsilonGC", even if it's specified in -Dtest.cds.runtime.options
    public static void disableRuntimePrefixForEpsilonGC() {
        disableRuntimePrefix("-XX:+UseEpsilonGC");
    }
}
