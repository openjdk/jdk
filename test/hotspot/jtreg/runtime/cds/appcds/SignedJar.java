/*
 * Copyright (c) 2014, 2023, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

/*
 * @test
 * @bug 8276184
 * @summary AppCDS handling of signed JAR.
 * @requires vm.cds
 * @library /test/lib
 * @compile test-classes/Hello.java
 * @run driver SignedJar
 */

import jdk.test.lib.process.OutputAnalyzer;
import java.io.File;

public class SignedJar {
    public static void main(String[] args) throws Exception {
        String unsignedJar = JarBuilder.getOrCreateHelloJar();
        JarBuilder.signJar("hello");

        // Test class exists in signed JAR
        String signedJar = TestCommon.getTestJar("signed_hello.jar");
        OutputAnalyzer output;

        // "testlambda" is for testing JDK-8276184
        String[] mainArgs = { "dummy", "testlambda" };
        String mainClass = "Hello";

        String skipMsg = "Skipping Hello: Signed JAR";
        String lambdaInArchive = "klasses.*=.*app.*Hello[$][$]Lambda.*hidden";
        String loadFromJar = ".class,load. Hello source: file:.*signed_hello.jar";
        String lambdaLoadFromHello = ".class.load. Hello[$][$]Lambda.*/0x.*source.*Hello";

        for (String mainArg : mainArgs) {
            output = TestCommon.dump(signedJar, TestCommon.list(mainClass),
                                     "-Xlog:cds+class=debug", mainClass, mainArg);
            TestCommon.checkDump(output, skipMsg);
            output.shouldNotContain(lambdaInArchive);

            // At runtime, the Hello class should be loaded from the jar file
            // instead of from the shared archive since a class from a signed
            // jar shouldn't be dumped into the archive.
            output = TestCommon.exec(signedJar, "-verbose:class", mainClass, mainArg);

            try {
                output.shouldMatch(loadFromJar);
                if (mainArg.equals("testlambda")) {
                    output.shouldMatch(lambdaLoadFromHello);
                }
            } catch (Exception e) {
                TestCommon.checkCommonExecExceptions(output, e);
            }

            // Test class exists in both signed JAR and unsigned JAR
            String jars = signedJar + System.getProperty("path.separator") + unsignedJar;
            output = TestCommon.dump(jars, TestCommon.list(mainClass),
                                     "-Xlog:cds+class=debug", mainClass, mainArg);
            TestCommon.checkDump(output, skipMsg);
            output.shouldNotContain(lambdaInArchive);
        }
    }
}
