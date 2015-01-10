/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @summary Verify deletion of a source file results in dropping of all .class files including inner classes
 * @bug 8054689
 * @author Fredrik O
 * @author sogoel (rewrite)
 * @library /tools/lib
 * @build Wrapper ToolBox
 * @run main Wrapper IncCompileDropClasses
 */

import java.util.*;
import java.nio.file.*;

public class IncCompileDropClasses extends SJavacTester {
    public static void main(String... args) throws Exception {
        IncCompileDropClasses dc = new IncCompileDropClasses();
        dc.test();
    }

    // Remember the previous bin and headers state here.
    Map<String,Long> previous_bin_state;
    Map<String,Long> previous_headers_state;
    ToolBox tb = new ToolBox();

    void test() throws Exception {
        Files.createDirectory(GENSRC);
        Files.createDirectory(BIN);
        Files.createDirectory(HEADERS);

        initialCompile();
        incrementalCompileDroppingClasses();

        clean(GENSRC, BIN, HEADERS);
    }

    // Testing that deleting AA.java deletes all generated inner class including AA.class
    void incrementalCompileDroppingClasses() throws Exception {
        previous_bin_state = collectState(BIN);
        previous_headers_state = collectState(HEADERS);
        System.out.println("\nIn incrementalCompileDroppingClasses() ");
        System.out.println("Testing that deleting AA.java deletes all generated inner class including AA.class");
        removeFrom(GENSRC, "alfa/omega/AA.java");
        compile("gensrc", "-d", "bin", "-h", "headers", "-j", "1",
                SERVER_ARG, "--log=debug");
        Map<String,Long> new_bin_state = collectState(BIN);
        verifyThatFilesHaveBeenRemoved(previous_bin_state, new_bin_state,
                                       "bin/alfa/omega/AA$1.class",
                                       "bin/alfa/omega/AA$AAAA.class",
                                       "bin/alfa/omega/AA$AAA.class",
                                       "bin/alfa/omega/AAAAA.class",
                                       "bin/alfa/omega/AA.class");

        previous_bin_state = new_bin_state;
        Map<String,Long> new_headers_state = collectState(HEADERS);
        verifyEqual(previous_headers_state, new_headers_state);
    }
}
