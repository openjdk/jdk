/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import jdk.javadoc.internal.tool.Main;

/**
 * @test
 * @bug 8086737
 * @summary Test -release option in javadoc
 * @run main ReleaseOption
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 */
public class ReleaseOption {
    public static void main(String... args) {
        new ReleaseOption().run();
    }

    void run() {
        doRunTest(0, out -> out.contains("compiler.err.doesnt.exist: java.util.stream"), "-release", "7");
        doRunTest(0, out -> !out.contains("compiler.err.doesnt.exist: java.util.stream"), "-release", "8");
        doRunTest(1, out -> true, "-release", "7", "-source", "7");
        doRunTest(1, out -> true, "-release", "7", "-bootclasspath", "any");
    }

    void doRunTest(int expectedResult, Predicate<String> validate, String... args) {
        System.err.println("running with args: " + Arrays.asList(args));
        List<String> options = new ArrayList<>();
        options.addAll(Arrays.asList(args));
        options.add("-XDrawDiagnostics");
        options.add(System.getProperty("test.src", ".") + java.io.File.separatorChar + "ReleaseOptionSource.java");
        StringWriter out = new StringWriter();
        PrintWriter pw = new PrintWriter(out);
        int actualResult = Main.execute(options.toArray(new String[0]), pw);
        System.err.println("actual result=" + actualResult);
        System.err.println("actual output=" + out.toString());
        if (actualResult != expectedResult)
            throw new Error();
        if (!validate.test(out.toString())) {
            throw new Error("Not an expected error output: " + out.toString());
        }
    }
}
