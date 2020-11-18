/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8256487
 * @summary Run the LambdaEagerInitTest.java test in static CDS archive mode.
 *          Create a base archive with the -Djdk.internal.lambda.disableEagerInitialization=true property.
 *          Run with the archive with and without specifying the property.
 * @requires vm.cds
 * @library /test/lib /test/hotspot/jtreg/runtime/cds/appcds
 * @compile ../../../../../jdk/java/lang/invoke/lambda/LambdaEagerInitTest.java
 * @run main/othervm LambdaEagerInit
 */

import java.io.File;

import jdk.test.lib.cds.CDSOptions;
import jdk.test.lib.cds.CDSTestUtils;
import jdk.test.lib.process.OutputAnalyzer;

public class LambdaEagerInit {
    public static void main(String[] args) throws Exception {
        testImpl();
    }

    private static final String classDir = System.getProperty("test.classes");
    private static final String mainClass = "LambdaEagerInitTest";
    private static final String testProperty = "-Djdk.internal.lambda.disableEagerInitialization=true";

    static void testImpl() throws Exception {
        String appJar = JarBuilder.build("lambda_eager", new File(classDir), null);
        String archiveName = mainClass + ".jsa";

        // create base archive with the -Djdk.internal.lambda.disableEagerInitialization=true property
        CDSOptions opts = (new CDSOptions())
            .addPrefix(testProperty,
                       "-Xlog:class+load,cds")
            .setArchiveName(archiveName);
        CDSTestUtils.createArchiveAndCheck(opts);

        // run with archive with the -Djdk.internal.lambda.disableEagerInitialization=true property
        CDSOptions runOpts = (new CDSOptions())
            .addPrefix("-cp", appJar, testProperty,  "-Xlog:class+load,cds=debug")
            .setArchiveName(archiveName)
            .setUseVersion(false)
            .addSuffix(mainClass);
        OutputAnalyzer output = CDSTestUtils.runWithArchive(runOpts);
        output.shouldMatch(".class.load. java.util.stream.Collectors[$][$]Lambda[$].*/0x.*source:.*shared.*objects.*file")
              .shouldHaveExitValue(0);

        // run with archive without the -Djdk.internal.lambda.disableEagerInitialization=true property
        runOpts = (new CDSOptions())
            .addPrefix("-cp", appJar, "-Xlog:class+load,cds=debug")
            .setArchiveName(archiveName)
            .setUseVersion(false)
            .addSuffix(mainClass);
        output = CDSTestUtils.runWithArchive(runOpts);
        output.shouldMatch(".class.load. java.util.stream.Collectors[$][$]Lambda[$].*/0x.*source:.*java.*util.*stream.*Collectors")
              .shouldNotMatch(".cds.*Loaded.*lambda.*proxy")
              .shouldHaveExitValue(0);
    }
}
