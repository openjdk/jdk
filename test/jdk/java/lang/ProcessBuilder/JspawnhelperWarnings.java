/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8325567
 * @requires (os.family == "linux") | (os.family == "aix")
 * @library /test/lib
 * @run main/othervm/timeout=300 JspawnhelperWarnings
 */

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import jdk.test.lib.process.ProcessTools;

public class JspawnhelperWarnings {

    private static final int TIMEOUT = 60;

    private static void jspawnhelperWithNArgs(int args) throws Exception {
        System.out.println("Running jspawnhelper with "+args+" args");
        String[] argArray = new String[args +1];
        Arrays.fill(argArray, "1");
        argArray[0] = Paths.get(System.getProperty("java.home"), "lib", "jspawnhelper").toString();
        for (int i = 0; i < argArray.length; ++i)
            System.out.println(argArray[i]);
        Process p = Runtime.getRuntime().exec(argArray);

        if (!p.waitFor(TIMEOUT, TimeUnit.SECONDS)) {
            throw new Exception("Child process timed out after " + TIMEOUT + " seconds");
        }

        try (BufferedReader br = p.inputReader()) {
            String line = br.readLine();
            System.out.println(line);
            while (line != null && !line.startsWith("This command is not for general use")) {
                System.out.println(line);
                line = br.readLine();
            }
            if (line == null) {
                throw new Exception("Wrong output from parent process");
            }
            System.out.println(line);
        }

        if (p.exitValue() != 1)
            throw new Exception("Unexpected exit value from jspawnhelper "+ p.exitValue());
    }

     public static void main(String[] args) throws Exception {
        for (int nArgs = 0; nArgs < 10; ++nArgs)
            jspawnhelperWithNArgs(nArgs);
    }
}