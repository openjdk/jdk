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
 * @summary Run the LambdaEagerInitTest.java test in dynamic CDS archive mode.
 *          Tests various combinations of base and dynamic archives dumping and
 *          run with those archives with the -Djdk.internal.lambda.disableEagerInitialization
 *          property set to {true, false}.
 * @requires vm.cds
 * @library /test/lib /test/hotspot/jtreg/runtime/cds/appcds
 * @compile ../../../../../../jdk/java/lang/invoke/lambda/LambdaEagerInitTest.java
 * @build sun.hotspot.WhiteBox
 * @run driver ClassFileInstaller sun.hotspot.WhiteBox
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:. LambdaEagerInit
 */

import java.io.File;

import jdk.test.lib.cds.CDSOptions;
import jdk.test.lib.cds.CDSTestUtils;
import jdk.test.lib.process.OutputAnalyzer;

public class LambdaEagerInit extends DynamicArchiveTestBase {
    public static void main(String[] args) throws Exception {
        createBaseArchive();
        runTest(LambdaEagerInit::createDynamicArchives);
        runTest(LambdaEagerInit::doTests);
    }

    private static final String classDir = System.getProperty("test.classes");
    private static final String mainClass = "LambdaEagerInitTest";
    private static final String testProperty = "-Djdk.internal.lambda.disableEagerInitialization";
    private static final String baseDisableEagerInit = getNewArchiveName("base-disableEagerInit");
    private static final String topDisableEagerInit = getNewArchiveName("top-disableEagerInit");
    private static final String topRegular = getNewArchiveName("top-regular");
    private static final String failedChecksum = "Dynamic archive cannot be used: static archive header checksum verification failed.";
    private static final String lambdaClassLoad = ".class.load. LambdaEagerInitTest[$][$]Lambda[$].*/0x.*source:";
    private static final String lambdaInArchive = lambdaClassLoad + ".*shared.*objects.*file.*(top)";
    private static final String lambdaNotInArchive = lambdaClassLoad + ".*LambdaEagerInitTest";
    static String appJar = null;

    static void createBaseArchive() throws Exception {
        // Note: the default base CDS archive (classes.jsa) was created during
        // JDK build time without setting the -Djdk.internal.lambda.disableEagerInitialization property.

        // create base archive with the -Djdk.internal.lambda.disableEagerInitialization=true property
        CDSOptions opts = (new CDSOptions())
            .addPrefix(testProperty + "=true",
                       "-Xlog:class+load,cds")
            .setArchiveName(baseDisableEagerInit);
        CDSTestUtils.createArchiveAndCheck(opts);
    }

    static void createDynamicArchives() throws Exception {
        appJar = JarBuilder.build("lambda_eager", new File(classDir), null);
        // create dynamic archive with the -Djdk.internal.lambda.disableEagerInitialization=true property
        // based on the base archive created with the same property setting.
        dump2(baseDisableEagerInit, topDisableEagerInit,
             testProperty + "=true",
             "-Xlog:class+load,cds,cds+dynamic=debug",
             "-cp", appJar, mainClass)
            .assertNormalExit(output -> {
                    output.shouldContain("Buffer-space to target-space delta")
                           .shouldContain("Written dynamic archive 0x");
                });

        // create dynamic archive without the property setting using the default
        // CDS archive as the base.
        dump2(null, topRegular,
             "-Xlog:class+load,cds,cds+dynamic=debug",
             "-cp", appJar, mainClass)
            .assertNormalExit(output -> {
                    output.shouldContain("Buffer-space to target-space delta")
                           .shouldContain("Written dynamic archive 0x");
                });
    }

    static void doTests() throws Exception {
        // doTest(baseArchiveName, dynamicArchiveName, set disableEagerInit during runtime, string to match)
        doTest(null, topRegular, true, lambdaNotInArchive);
        doTest(null, topRegular, false, lambdaInArchive);
        doTest(null, topDisableEagerInit, true, lambdaInArchive);
        doTest(null, topDisableEagerInit, false, lambdaNotInArchive);
        doTest(baseDisableEagerInit, topRegular, true, failedChecksum);
        doTest(baseDisableEagerInit, topRegular, false, failedChecksum);
        doTest(baseDisableEagerInit, topDisableEagerInit, true, lambdaInArchive);
        doTest(baseDisableEagerInit, topDisableEagerInit, false, lambdaNotInArchive);
    }


    private static void doTest(String baseArchiveName,
                               String topArchiveName,
                               boolean propertySetting,
                               String match) throws Exception {

        String value = propertySetting ? "=true" : "=false";

        // run with base and dynamic archives
        run2(baseArchiveName, topArchiveName,
            testProperty + value,
            "-Xlog:class+load,cds+dynamic=debug,cds=debug",
            "-cp", appJar, mainClass)
            .ifNormalExit(output -> { output.shouldMatch(match); })
            .ifAbnormalExit(output -> { output.shouldMatch(match); });
    }
}
