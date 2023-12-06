/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8312434
 * @summary A jar file containing classes in the same package. Sign the jar file with
 *          a disabled algorithm. The jar will be treated as unsigned.
 *          Dump only one class into the CDS archive. During runtime, load the class
 *          stored in the archive and then load another class not from the archive
 *          but from the same pacakge. Loading of the second class should not result
 *          in sealing violation.
 *
 * @requires vm.cds
 * @library /test/lib
 * @compile test-classes/GenericTestApp.java test-classes/pkg/ClassInPackage.java test-classes/C2.java
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar WhiteBox.jar jdk.test.whitebox.WhiteBox
 * @run driver SealingViolation
 */

import jdk.test.lib.helpers.ClassFileInstaller;
import jdk.test.lib.process.OutputAnalyzer;

public class SealingViolation {
    public static void main(String[] args) throws Exception {
        String[] classList = {"pkg/ClassInPackage"};
        String appJar = ClassFileInstaller.writeJar("pkg-classes-sealed.jar",
            ClassFileInstaller.Manifest.fromSourceFile("test-classes/pkg/package_seal.mf"),
            "GenericTestApp", "pkg/ClassInPackage", "pkg/C2");

        JarBuilder.signJarWithDisabledAlgorithm("pkg-classes-sealed");
        String signedJar = TestCommon.getTestJar("pkg-classes-sealed.jar");

        // GenericTestApp requires WhiteBox
        String wbJar = ClassFileInstaller.getJarPath("WhiteBox.jar");
        String bootclasspath = "-Xbootclasspath/a:" + wbJar;

        OutputAnalyzer output = TestCommon.dump(signedJar, classList, bootclasspath,
                                     "-Xlog:cds+class=debug");
        output.shouldMatch("cds.class.*klasses.* pkg.ClassInPackage")
              .shouldHaveExitValue(0);

        output = TestCommon.exec(signedJar, "-Xlog:cds=debug,class+load",
                                 bootclasspath,
                                 "-XX:+UnlockDiagnosticVMOptions",
                                 "-XX:+WhiteBoxAPI",
                                 "GenericTestApp",
                                 "assertShared:pkg.ClassInPackage",
                                 "assertNotShared:pkg.C2");
        output.shouldHaveExitValue(0);
    }
}
