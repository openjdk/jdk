/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 8072480
 * @summary Verify option clash between -release and -source is reported correctly.
 * @modules jdk.compiler/com.sun.tools.javac.util
 */

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.tools.Tool;
import javax.tools.ToolProvider;

public class ReleaseOptionClashes {
    public static void main(String... args) throws Exception {
        new ReleaseOptionClashes().run();
    }

    void run() throws Exception {
        doRunTest("-bootclasspath", "any");
        doRunTest("-Xbootclasspath:any");
        doRunTest("-Xbootclasspath/a:any");
        doRunTest("-Xbootclasspath/p:any");
        doRunTest("-endorseddirs", "any");
        doRunTest("-extdirs", "any");
        doRunTest("-source", "8");
        doRunTest("-target", "8");
    }

    void doRunTest(String... args) throws Exception {
        System.out.println("Testing clashes for arguments: " + Arrays.asList(args));
        Class<?> log = Class.forName("com.sun.tools.javac.util.Log", true, cl);
        Field useRawMessages = log.getDeclaredField("useRawMessages");
        useRawMessages.setAccessible(true);
        useRawMessages.setBoolean(null, true);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        List<String> options = new ArrayList<>();
        options.addAll(Arrays.asList("-release", "7"));
        options.addAll(Arrays.asList(args));
        options.add(System.getProperty("test.src") + File.separator + "ReleaseOptionClashes.java");
        compiler.run(null, null, out, options.toArray(new String[0]));
        useRawMessages.setBoolean(null, false);
        if (!out.toString().equals(String.format("%s%n%s%n",
                                                 "javac: javac.err.release.bootclasspath.conflict",
                                                 "javac.msg.usage")) &&
            //-Xbootclasspath:any produces two warnings: one for -bootclasspath and one for -Xbootclasspath:
            !out.toString().equals(String.format("%s%n%s%n%s%n%s%n",
                                                 "javac: javac.err.release.bootclasspath.conflict",
                                                 "javac.msg.usage",
                                                 "javac: javac.err.release.bootclasspath.conflict",
                                                 "javac.msg.usage"))) {
            throw new AssertionError(out);
        }
        System.out.println("Test PASSED.  Running javac again to see localized output:");
        compiler.run(null, null, System.out, options.toArray(new String[0]));
    }

    ClassLoader cl = ToolProvider.getSystemToolClassLoader();
    Tool compiler = ToolProvider.getSystemJavaCompiler();
}
