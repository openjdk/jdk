/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @summary Test white listing of external artifacts inside the destination dir
 * @bug 8054689
 * @author Fredrik O
 * @author sogoel (rewrite)
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.file
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.compiler/com.sun.tools.sjavac
 * @build Wrapper ToolBox
 * @run main Wrapper PermittedArtifact
 */

import java.lang.reflect.Method;
import java.util.*;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.nio.charset.*;

public class PermittedArtifact extends SJavacTester {
    public static void main(String... args) throws Exception {
        PermittedArtifact pa = new PermittedArtifact();
        pa.test();
    }

    //Verify that --permit-artifact=bin works
    void test() throws Exception {
        Files.createDirectory(BIN);
        clean(GENSRC, BIN);

        Map<String,Long> previous_bin_state = collectState(BIN);

        new ToolBox().writeFile(GENSRC+"/alfa/omega/A.java",
                "package alfa.omega; public class A { }");

        new ToolBox().writeFile(BIN+"/alfa/omega/AA.class",
                 "Ugh, a messy build system (tobefixed) wrote this class file, "
                         + "sjavac must not delete it.");

        compile("--log=debug", "--permit-artifact=bin/alfa/omega/AA.class",
                "-src", "gensrc", "-d", "bin", SERVER_ARG);

        Map<String,Long> new_bin_state = collectState(BIN);
        verifyThatFilesHaveBeenAdded(previous_bin_state, new_bin_state,
                                     "bin/alfa/omega/A.class",
                                     "bin/alfa/omega/AA.class",
                                     "bin/javac_state");
        clean(GENSRC, BIN);
    }
}
