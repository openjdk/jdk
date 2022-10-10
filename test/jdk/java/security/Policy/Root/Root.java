/*
 * Copyright (c) 2002, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4619757
 * @summary User Policy Setting is not recognized on Netscape 6
 *          when invoked as root.
 * @library /test/lib
 * @run driver Root
 */

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.*;

public class Root {
    private static final String SRC = System.getProperty("test.src");
    private static final String ROOT = System.getProperty("user.home");
    private static final Path SOURCE = Paths.get(SRC, "Root.policy");
    private static final Path TARGET = Paths.get(ROOT, ".java.policy");
    public static void main(String[] args) throws Exception {
        Files.copy(SOURCE, TARGET, StandardCopyOption.REPLACE_EXISTING);
        try {
            test();
        } finally {
            Files.delete(TARGET);
        }
    }

    private static void test() throws Exception{
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
                RootTest.class.getName()
        );

        Process proc = pb.start();
        OutputAnalyzer output = new OutputAnalyzer(proc);
        output.stdoutShouldNotBeEmpty();
        output.shouldContain("Test succeeded");
        System.out.println("Test passed.");
    }

    class RootTest {
        public static void main(String[] args) {
            Policy p = Policy.getPolicy();
            if (p.implies(Root.class.getProtectionDomain(), new AllPermission())) {
                System.out.println("Test succeeded");
            } else {
                throw new SecurityException("Test failed");
            }
        }
    }
}
