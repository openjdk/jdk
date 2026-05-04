/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8351232
 * @summary NPE when type annotation missing on classpath
 * @modules jdk.compiler/com.sun.tools.javac.api
 */

import java.io.File;
import com.sun.tools.javac.api.*;

public class TypeAnnotationSymNullTest {
    public static void main(String[] args) {
        File testSrc = new File(System.getProperty("test.src", "."));

        File ANNO = new File("ANNO");
        ANNO.mkdirs();

        // first, compile Anno to the ANNO directory
        compile(0, "-d", ANNO.getPath(), new File(testSrc, "Anno.java").getPath());

        File CLP = new File("CLP");
        CLP.mkdirs();

        // second, compile Cls, Intf1 and Intf2 to the CLP directory with ANNO on classpath
        compile(0, "-cp", ANNO.getPath(), "-d", CLP.getPath(), new File(testSrc, "Cls.java").getPath(),
                                                            new File(testSrc, "Intf1.java").getPath(),
                                                            new File(testSrc, "Intf2.java").getPath());

        // now compile TestClass with CLP on classpath (but Anno.class missing)
        // compilation fails (exit code 1) but should not get NPE from compiler (exit code 4)
        compile(1, "-cp", CLP.getPath(), "-d", ".", new File(testSrc, "TestClass.java").getPath());
    }

    private static void compile(int expectedExit, String... args) {
        int exitCode = JavacTool.create().run(null, null, null, args);
        if (exitCode != expectedExit) {
            throw new AssertionError("test compilation failed with exit code: " + exitCode);
        }
    }
}
