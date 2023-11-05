/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8272163
 * @summary keytool -version test
 * @library /test/lib
 */

import jdk.test.lib.SecurityTools;

public class VersionTest {

    public static void main(String[] args) throws Exception {
        SecurityTools.keytool("-version")
                .shouldContain("keytool")
                .shouldHaveExitValue(0);

        SecurityTools.keytool("-version -erropt")
                .shouldContain("Illegal option:  -erropt")
                .shouldContain("Prints the program version")
                .shouldContain("Use \"keytool -?, -h, or --help\" for this help message")
                .shouldHaveExitValue(1);

        SecurityTools.keytool("-genkeypair -erropt")
                .shouldContain("Illegal option:  -erropt")
                .shouldContain("Generates a key pair")
                .shouldContain("Use \"keytool -?, -h, or --help\" for this help message")
                .shouldHaveExitValue(1);

        SecurityTools.keytool("-version --help")
                .shouldContain("Prints the program version")
                .shouldContain("Use \"keytool -?, -h, or --help\" for this help message")
                .shouldHaveExitValue(0);

        SecurityTools.keytool("--help -version")
                .shouldContain("Prints the program version")
                .shouldContain("Use \"keytool -?, -h, or --help\" for this help message")
                .shouldHaveExitValue(0);

        SecurityTools.keytool("-genkeypair --help")
                .shouldContain("Generates a key pair")
                .shouldContain("Use \"keytool -?, -h, or --help\" for this help message")
                .shouldHaveExitValue(0);

        SecurityTools.keytool("--help")
                .shouldContain("-genkeypair         Generates a key pair")
                .shouldContain("-version            Prints the program version")
                .shouldHaveExitValue(0);
    }
}
