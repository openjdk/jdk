/*
 * Copyright (c) 2014, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @summary classpath mismatch between dump time and execution time
 * @requires vm.cds
 * @library /test/lib
 * @compile test-classes/Hello.java
 * @compile test-classes/C2.java
 * @run driver WrongClasspath
 */

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.helpers.ClassFileInstaller;

public class WrongClasspath {

  public static void main(String[] args) throws Exception {
    String appJar = JarBuilder.getOrCreateHelloJar();
    String unableToUseMsg = "Unable to use shared archive";
    String mismatchMsg = "shared class paths mismatch";
    String hintMsg = "(hint: enable -Xlog:class+path=info to diagnose the failure)";

    // Dump CDS archive with hello.jar
    // Run with a jar file that differs from the original jar file by the first character only: -cp mello.jar
    // Shared class paths mismatch should be detected.
    String hellojar = "hello.jar";
    String mellojar = "mello.jar";
    Files.copy(Paths.get(appJar), Paths.get(hellojar), StandardCopyOption.COPY_ATTRIBUTES);
    Files.copy(Paths.get(appJar), Paths.get(mellojar), StandardCopyOption.COPY_ATTRIBUTES);
    TestCommon.testDump(hellojar, TestCommon.list("Hello"));
    TestCommon.run("-cp", mellojar,
        "-Xlog:cds",
        "Hello")
        .assertAbnormalExit(unableToUseMsg, mismatchMsg, hintMsg);

    // Dump an archive with a specified JAR file in -classpath
    TestCommon.testDump(appJar, TestCommon.list("Hello"));

    // Then try to execute the archive without -classpath -- it should fail
    // To run without classpath, set the property test.noclasspath to true
    // so that ProcessTools won't append the classpath of the jtreg process to the test process
    System.setProperty("test.noclasspath", "true");
    TestCommon.run(
        /* "-cp", appJar, */ // <- uncomment this and the execution should succeed
        "-Xlog:cds",
        "Hello")
        .assertAbnormalExit(unableToUseMsg, mismatchMsg, hintMsg);

    // Run with -Xshare:auto and without CDS logging enabled, the mismatch message
    // should still be there.
    OutputAnalyzer output = TestCommon.execAuto("Hello");
    output.shouldContain(mismatchMsg)
          .shouldContain(hintMsg);

    // Run with -Xshare:on and -Xlog:class+path=info, the mismatchMsg should
    // be there, the hintMsg should NOT be there.
    TestCommon.run(
        "-Xlog:class+path=info",
        "Hello")
        .assertAbnormalExit( out -> {
            out.shouldContain(unableToUseMsg)
               .shouldContain(mismatchMsg)
               .shouldNotContain(hintMsg);
        });
    System.clearProperty("test.noclasspath");

    // Dump CDS archive with 2 jars: -cp hello.jar:jar2.jar
    // Run with 2 jars but the second jar doesn't exist: -cp hello.jarjar2.jarx
    // Shared class paths mismatch should be detected.
    String jar2 = ClassFileInstaller.writeJar("jar2.jar", "pkg/C2");
    String jars = appJar + File.pathSeparator + jar2;
    TestCommon.testDump(jars, TestCommon.list("Hello", "pkg/C2"));
    TestCommon.run(
        "-cp", jars + "x", "Hello")
        .assertAbnormalExit(unableToUseMsg, mismatchMsg, hintMsg);

    // modify the timestamp of the jar2
    (new File(jar2.toString())).setLastModified(System.currentTimeMillis() + 2000);

    // Run with -Xshare:auto and without CDS logging enabled, the "timestamp has changed"
    // message should be there.
    output = TestCommon.execAuto(
        "-cp", jars, "Hello");
    output.shouldMatch("This file is not the one used while building the shared archive file:.*jar2.jar")
          .shouldMatch(".warning..cds.*jar2.jar timestamp has changed.");
  }
}
