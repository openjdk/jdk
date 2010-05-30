/*
 * Copyright (c) 2006, Oracle and/or its affiliates. All rights reserved.
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
 * @bug     6410653 6401277
 * @summary REGRESSION: javac crashes if -d or -s argument is a file
 * @author  Peter von der Ah\u00e9
 */

import java.lang.reflect.Method;
import java.io.File;
import java.io.ByteArrayOutputStream;
import javax.tools.*;

public class T6410653 {
    public static void main(String... args) throws Exception {
        File testSrc = new File(System.getProperty("test.src"));
        String source = new File(testSrc, "T6410653.java").getPath();
        ClassLoader cl = ToolProvider.getSystemToolClassLoader();
        Tool compiler = ToolProvider.getSystemJavaCompiler();
        Class<?> main = Class.forName("com.sun.tools.javac.main.Main", true, cl);
        Method useRawMessages = main.getMethod("useRawMessages", boolean.class);
        useRawMessages.invoke(null, true);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        compiler.run(null, null, out, "-d", source, source);
        useRawMessages.invoke(null, false);
        if (!out.toString().equals(String.format("%s%n%s%n",
                                                 "javac: javac.err.file.not.directory",
                                                 "javac.msg.usage"))) {
            throw new AssertionError(out);
        }
        System.out.println("Test PASSED.  Running javac again to see localized output:");
        compiler.run(null, null, System.out, "-d", source, source);
    }
}
