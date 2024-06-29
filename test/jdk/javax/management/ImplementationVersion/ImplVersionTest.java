/*
 * Copyright (c) 2003, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4842196
 * @summary Test that there is no difference between the JMX version and the
 * JDK version when the application is run with a security manager and the
 * test codebase has the java permission to read the "java.runtime.version"
 * system property.
 * @author Luis-Miguel Alventosa
 *
 * @library /test/lib
 * @run build ImplVersionTest ImplVersionCommand ImplVersionReader
 * @run main ImplVersionTest
 */

import jdk.test.lib.process.ProcessTools;

import java.io.File;

public class ImplVersionTest {

    public static void main(String[] args) throws Exception {
        // Get test src
        //
        String testSrc = System.getProperty("test.src");
        System.out.println("testSrc = " + testSrc);
        // Get test classes
        //
        String testClasses = System.getProperty("test.classes");
        System.out.println("testClasses = " + testClasses);
        // Get boot class path
        //
        String[] command = new String[] {
            "-Djava.security.manager",
            "-Djava.security.policy==" + testSrc + File.separator + "policy",
            "-Dtest.classes=" + testClasses,
            "ImplVersionCommand",
            System.getProperty("java.runtime.version")
        };

        ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(command);
        Process proc = pb.start();
        new ImplVersionReader(proc, proc.getInputStream()).start();
        new ImplVersionReader(proc, proc.getErrorStream()).start();
        int exitValue = proc.waitFor();
        System.out.println("ImplVersionCommand Exit Value = " + exitValue);
        if (exitValue != 0) {
            throw new RuntimeException("TEST FAILED: Incorrect exit value " +
                                       "from ImplVersionCommand " + exitValue);
        }
        // Test OK!
        //
        System.out.println("Bye! Bye!");
    }
}
