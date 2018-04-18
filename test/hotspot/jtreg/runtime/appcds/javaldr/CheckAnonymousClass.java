/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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
 * @summary ensure no anonymous class is being dumped into the CDS archive
 * @requires vm.cds
 * @library /test/lib /test/hotspot/jtreg/runtime/appcds
 * @modules jdk.jartool/sun.tools.jar
 * @compile ../test-classes/Hello.java
 * @run main CheckAnonymousClass
 */

import jdk.test.lib.process.OutputAnalyzer;

public class CheckAnonymousClass {

  public static void main(String[] args) throws Exception {
    JarBuilder.build("hello", "Hello");

    String appJar = TestCommon.getTestJar("hello.jar");

    TestCommon.dump(appJar, TestCommon.list("Hello", "org/omg/CORBA/ORB"),
        "--add-modules", "java.corba", "-Xlog:class+load=info");

    String prefix = ".class.load. ";
    // class name pattern like the following:
    // jdk.internal.loader.BuiltinClassLoader$$Lambda$1/1816757085
    // java.lang.invoke.LambdaForm$MH/1585787493
    String class_pattern = ".*Lambda([a-z0-9$]+)/([0-9]+).*";
    String suffix = ".*source: shared objects file.*";
    String pattern = prefix + class_pattern + suffix;
    // during run time, anonymous classes shouldn't be loaded from the archive
    TestCommon.run("-XX:+UnlockDiagnosticVMOptions",
        "-cp", appJar, "-Xlog:class+load=info", "--add-modules", "java.corba", "Hello")
      .assertNormalExit(output -> output.shouldNotMatch(pattern));

    // inspect the archive and make sure no anonymous class is in there
    TestCommon.run("-XX:+UnlockDiagnosticVMOptions",
        "-cp", appJar, "-Xlog:class+load=info", "-XX:+PrintSharedArchiveAndExit",
        "-XX:+PrintSharedDictionary", "--add-modules", "java.corba", "Hello")
      .assertNormalExit(output -> output.shouldNotMatch(class_pattern));
  }
}
