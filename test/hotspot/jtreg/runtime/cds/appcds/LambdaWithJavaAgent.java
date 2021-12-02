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
 * @bug 8276126
 * @summary Test static dumping with java agent transforming a class loaded
 *          by the boot class loader.
 * @requires vm.cds.write.archived.java.heap
 * @requires vm.jvmti
 * @library /test/lib /test/hotspot/jtreg/runtime/cds/appcds/test-classes
 * @compile test-classes/Hello.java
 * @compile test-classes/TransformBootClass.java
 * @run driver LambdaWithJavaAgent
 */

import jdk.test.lib.cds.CDSOptions;
import jdk.test.lib.cds.CDSTestUtils;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.helpers.ClassFileInstaller;

public class LambdaWithJavaAgent {

    public static String agentClasses[] = {
        TransformBootClass.class.getName(),
    };

    public static void main(String[] args) throws Exception {
        String mainClass = Hello.class.getName();
        String namePrefix = "lambda-with-java-agent";
        JarBuilder.build(namePrefix, mainClass);

        String appJar = TestCommon.getTestJar(namePrefix + ".jar");
        String classList = namePrefix + ".list";
        String archiveName = namePrefix + ".jsa";

        String agentJar =
            ClassFileInstaller.writeJar("TransformBootClass.jar",
                                        ClassFileInstaller.Manifest.fromSourceFile("test-classes/TransformBootClass.mf"),
                                        agentClasses);
        String useJavaAgent = "-javaagent:" + agentJar + "=jdk/internal/math/FDBigInteger";

        // dump class list
        CDSTestUtils.dumpClassList(classList, "-cp", appJar, mainClass);

        // create archive with the class list
        CDSOptions opts = (new CDSOptions())
            .addPrefix("-XX:ExtraSharedClassListFile=" + classList,
                       "-cp", appJar,
                       "-XX:+AllowArchivingWithJavaAgent",
                       useJavaAgent,
                       "-Xlog:class+load,cds+class=debug,cds")
            .setArchiveName(archiveName);
        OutputAnalyzer output = CDSTestUtils.createArchiveAndCheck(opts);
        output.shouldContain("CDS heap objects cannot be written because class jdk.internal.math.FDBigInteger maybe modified by ClassFileLoadHook")
              .shouldContain("Skipping jdk/internal/math/FDBigInteger: Unsupported location")
              .shouldMatch(".class.load.*jdk.internal.math.FDBigInteger.*source.*modules");

        // run with archive
        CDSOptions runOpts = (new CDSOptions())
            .addPrefix("-cp", appJar, "-Xlog:class+load,cds=debug",
                       "-XX:+AllowArchivingWithJavaAgent",
                       useJavaAgent)
            .setArchiveName(archiveName)
            .setUseVersion(false)
            .addSuffix(mainClass);
        output = CDSTestUtils.runWithArchive(runOpts);
        TestCommon.checkExecReturn(output, 0, true,
            "Hello source: shared objects file");
    }
}
