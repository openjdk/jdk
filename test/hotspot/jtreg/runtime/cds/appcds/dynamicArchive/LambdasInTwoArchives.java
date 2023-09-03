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
 * @bug 8307468
 * @summary Test archiving of lambda proxy classes with the same LambdaProxyClassKey
 *          (see cds/lambdaProxyClassDictionary.hpp). If some lambda proxy classes
 *          are already in the static archive, during dynamic dump with the static archive,
 *          the ones in the static archive should not be generated and archived
 *          in the dynamic archive.
 * @requires vm.cds
 * @library /test/lib /test/hotspot/jtreg/runtime/cds/appcds
 *          /test/hotspot/jtreg/runtime/cds/appcds/dynamicArchive/test-classes
 * @build LambdasWithSameKey jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar lambdas_same_key.jar LambdasWithSameKey
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar WhiteBox.jar jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:. LambdasInTwoArchives
 */

import java.io.File;
import java.util.List;
import java.util.regex.Pattern;

import jdk.test.lib.cds.CDSTestUtils;
import jdk.test.lib.helpers.ClassFileInstaller;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class LambdasInTwoArchives extends DynamicArchiveTestBase {
    static final String lambdaPattern =
        ".*cds.class.*klasses.*LambdasWithSameKey[$][$]Lambda.*/0x.*hidden";
    static final String loadFromStatic =
        ".*class.load.*LambdasWithSameKey[$][$]Lambda/0x.*source:.*shared.*objects.*file";
    static final String loadFromTop = loadFromStatic + ".*(top).*";
    static final String usedAllStatic =
        "Used all static archived lambda proxy classes for: LambdasWithSameKey";

    public static void main(String[] args) throws Exception {
        runTest(LambdasInTwoArchives::test);
    }

    static void checkLambdas(OutputAnalyzer output, String matchPattern, int numLambdas) throws Exception {
        List<String> lines = output.asLines();
        Pattern pattern = Pattern.compile(matchPattern);
        int count = 0;
        for (int i = 0; i < lines.size(); i++) {
            if (pattern.matcher(lines.get(i)).matches()) {
                count++;
            }
        }
        if (count != numLambdas) {
            throw new RuntimeException("Expecting " + numLambdas + " lambda proxy classes, but got " + count);
        }
    }

    static void test() throws Exception {
        String classListFileName = "lambda-classes.list";
        File fileList = new File(classListFileName);
        if (fileList.exists()) {
            fileList.delete();
        }
        String appJar = ClassFileInstaller.getJarPath("lambdas_same_key.jar");
        String mainClass = "LambdasWithSameKey";
        // Generate a class list for static dump.
        // Note that the class list contains one less lambda proxy class comparing
        // with running the LambdasWithSameKey app with the "run" argument.
        String[] launchArgs = {
                "-Xshare:off",
                "-XX:DumpLoadedClassList=" + classListFileName,
                "-Xlog:cds",
                "-Xlog:cds+lambda",
                "-cp", appJar, mainClass};
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(launchArgs);
        OutputAnalyzer oa = TestCommon.executeAndLog(pb, "lambda-classes");
        oa.shouldHaveExitValue(0);

        String logOptions = "-Xlog:cds=debug,class+load,cds+class=debug";
        String baseArchiveName = CDSTestUtils.getOutputFileName("lambda-base.jsa");
        // Static dump based on the class list.
        dumpBaseArchive(baseArchiveName,
                        "-XX:SharedClassListFile=" + classListFileName,
                        logOptions,
                        "-cp", appJar, mainClass)
            // Expects 2 lambda proxy classes with LambdasWithSameKey as the
            // caller class in the static dump log.
            .assertNormalExit(output -> checkLambdas(output, lambdaPattern, 2));

        String topArchiveName = getNewArchiveName("lambda-classes-top");

        // Dynamic dump with the static archive.
        dump2(baseArchiveName, topArchiveName,
                 logOptions,
                 "-cp", appJar, mainClass, "run")
            // Expects only 1 lambda proxy class with LambdasWithSameKey as the
            // caller class in the dynamic dump log.
            .assertNormalExit(output -> checkLambdas(output, lambdaPattern, 1))
            .assertNormalExit(output -> {
                output.shouldContain(usedAllStatic);
            });

        // Run with both static and dynamic archives.
        run2(baseArchiveName, topArchiveName,
            logOptions,
            "-cp", appJar, mainClass, "run")
            // Two lambda proxy classes should be loaded from the static archive.
            .assertNormalExit(output -> checkLambdas(output, loadFromStatic, 2))
            .assertNormalExit(output -> { output.shouldContain(usedAllStatic); })
            // One lambda proxy class should be loaded from the dynamic archive.
            .assertNormalExit(output -> checkLambdas(output, loadFromTop, 1));
    }
}
