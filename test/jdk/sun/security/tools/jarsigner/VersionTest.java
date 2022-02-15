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
 * @summary jarsigner -version test
 * @library /test/lib
 */

import jdk.test.lib.SecurityTools;
import jdk.test.lib.util.JarUtils;
import java.nio.file.Path;

public class VersionTest {

    public static void main(String[] args) throws Exception {
        SecurityTools.jarsigner("-version")
                .shouldContain("jarsigner")
                .shouldHaveExitValue(0);

        SecurityTools.jarsigner("-version -erropt")
                .shouldContain("Illegal option: -erropt")
                .shouldContain("Please type jarsigner --help for usage")
                .shouldHaveExitValue(1);

        SecurityTools.jarsigner("-verify -erropt")
                .shouldContain("Illegal option: -erropt")
                .shouldContain("Please type jarsigner --help for usage")
                .shouldHaveExitValue(1);

        SecurityTools.jarsigner("-version --help")
                .shouldContain("Usage: jarsigner [options] jar-file alias")
                .shouldContain("[-verify]                   verify a signed JAR file")
                .shouldContain("[-version]                  print the program version")
                .shouldHaveExitValue(0);

        SecurityTools.jarsigner("--help -version")
                .shouldContain("Usage: jarsigner [options] jar-file alias")
                .shouldContain("[-verify]                   verify a signed JAR file")
                .shouldContain("[-version]                  print the program version")
                .shouldHaveExitValue(0);

        SecurityTools.jarsigner("-verify --help")
                .shouldContain("Usage: jarsigner [options] jar-file alias")
                .shouldContain("[-verify]                   verify a signed JAR file")
                .shouldContain("[-version]                  print the program version")
                .shouldHaveExitValue(0);

        SecurityTools.jarsigner("--help")
                .shouldContain("Usage: jarsigner [options] jar-file alias")
                .shouldContain("[-verify]                   verify a signed JAR file")
                .shouldContain("[-version]                  print the program version")
                .shouldHaveExitValue(0);

        SecurityTools.jarsigner()
                .shouldContain("Usage: jarsigner [options] jar-file alias")
                .shouldContain("[-verify]                   verify a signed JAR file")
                .shouldContain("[-version]                  print the program version")
                .shouldHaveExitValue(0);

        SecurityTools.keytool("-genkeypair -keystore ks -storepass changeit" +
                " -keyalg rsa -dname CN=ee -alias ee")
                .shouldHaveExitValue(0);

        JarUtils.createJarFile(Path.of("a.jar"), Path.of("."), Path.of("."));

        /*
         * -version is specified but -help is not specified, jarsigner
         * will only print the program version and ignore other options.
         */
        SecurityTools.jarsigner("-keystore ks -storepass changeit" +
                " -signedjar signeda.jar a.jar ee -version")
                .shouldNotContain("jar signed.")
                .shouldContain("jarsigner ")
                .shouldHaveExitValue(0);

        /*
         * -version is specified but -help is not specified, jarsigner
         * will only print the program version and ignore other options.
         */
        SecurityTools.jarsigner("-version -verify a.jar")
                .shouldNotContain("jar is unsigned.")
                .shouldContain("jarsigner ")
                .shouldHaveExitValue(0);
    }
}
