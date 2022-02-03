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
 *
 */

/*
 * @test
 * @bug 8274944
 * @summary VM should not crash during CDS dump when a lambda proxy class
 *          contains an old version of interface.
 * @requires vm.cds
 * @library /test/lib
 * @compile test-classes/OldProvider.jasm
 * @compile test-classes/LambdaContainsOldInfApp.java
 * @run driver LambdaContainsOldInf
 */

import jdk.test.lib.cds.CDSOptions;
import jdk.test.lib.cds.CDSTestUtils;
import jdk.test.lib.process.OutputAnalyzer;

public class LambdaContainsOldInf {

    public static void main(String[] args) throws Exception {
        String mainClass = "LambdaContainsOldInfApp";
        String namePrefix = "lambdacontainsoldinf";
        JarBuilder.build(namePrefix, mainClass, "OldProvider");

        String appJar = TestCommon.getTestJar(namePrefix + ".jar");
        String classList = namePrefix + ".list";
        String archiveName = namePrefix + ".jsa";

        // dump class list
        CDSTestUtils.dumpClassList(classList, "-cp", appJar, mainClass);

        // create archive with the class list
        CDSOptions opts = (new CDSOptions())
            .addPrefix("-XX:ExtraSharedClassListFile=" + classList,
                       "-cp", appJar,
                       "-Xlog:class+load,cds")
            .setArchiveName(archiveName);
        OutputAnalyzer output = CDSTestUtils.createArchiveAndCheck(opts);
        TestCommon.checkExecReturn(output, 0, true,
                                   "Skipping OldProvider: Old class has been linked");
        output.shouldMatch("Skipping.LambdaContainsOldInfApp[$][$]Lambda[$].*0x.*:.*Old.class.has.been.linked");

        // run with archive
        CDSOptions runOpts = (new CDSOptions())
            .addPrefix("-cp", appJar, "-Xlog:class+load,cds=debug")
            .setArchiveName(archiveName)
            .setUseVersion(false)
            .addSuffix(mainClass);
        output = CDSTestUtils.runWithArchive(runOpts);
        TestCommon.checkExecReturn(output, 0, true,
            "[class,load] LambdaContainsOldInfApp source: shared objects file");
        output.shouldMatch(".class.load. OldProvider.source:.*lambdacontainsoldinf.jar")
              .shouldMatch(".class.load. LambdaContainsOldInfApp[$][$]Lambda[$].*/0x.*source:.*LambdaContainsOldInf");
    }
}
