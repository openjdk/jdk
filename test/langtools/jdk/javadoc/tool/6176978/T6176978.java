/*
 * Copyright (c) 2008, 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6176978
 * @summary current Javadoc's invocation and extension (Doclet) mechanisms are problematic
 * @modules jdk.javadoc
 * @ignore no longer applicable, should delete
 * @build T6176978
 * @run main T6176978
 */

import java.io.*;
import java.net.*;

public class T6176978
{
    public static void main(String[] args) throws Exception {
        // create and use a temp dir that will not be on jtreg's
        // default class path
        File tmpDir = new File("tmp");
        tmpDir.mkdirs();

        File testSrc = new File(System.getProperty("test.src", "."));
        String[] javac_args = {
            "-d",
            "tmp",
            new File(testSrc, "X.java").getPath()
        };

        int rc = com.sun.tools.javac.Main.compile(javac_args);
        if (rc != 0)
            throw new Error("javac exit code: " + rc);

        String[] jdoc_args = {
            "-doclet",
            "X",
            new File(testSrc, "T6176978.java").getPath()
        };

        rc = jdk.javadoc.internal.tool.Main.execute(jdoc_args);
        if (rc == 0)
            throw new Error("javadoc unexpectedly succeeded");



        Thread currThread = Thread.currentThread();
        ClassLoader saveClassLoader = currThread.getContextClassLoader();
        URLClassLoader urlCL = new URLClassLoader(new URL[] { tmpDir.toURL() });
        currThread.setContextClassLoader(urlCL);

        try {
            rc = jdk.javadoc.internal.tool.Main.execute(jdoc_args);
            if (rc != 0)
                throw new Error("javadoc exit: " + rc);
        } finally {
            currThread.setContextClassLoader(saveClassLoader);
        }
    }
}
