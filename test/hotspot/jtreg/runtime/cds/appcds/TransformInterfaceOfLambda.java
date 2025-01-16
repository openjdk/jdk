/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8323950
 * @summary Transforming an interface of an archived lambda proxy class should not
 *          crash the VM. The lambda proxy class should be regenerated during runtime.
 * @requires vm.cds
 * @requires vm.jvmti
 * @requires vm.flagless
 * @library /test/lib /test/hotspot/jtreg/runtime/cds/appcds/test-classes
 * @compile test-classes/SimpleTest.java
 * @compile test-classes/TransformBootClass.java
 * @run driver TransformInterfaceOfLambda
 */

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.helpers.ClassFileInstaller;

public class TransformInterfaceOfLambda {

    public static String agentClasses[] = {
        TransformBootClass.class.getName(),
    };

    public static void main(String[] args) throws Exception {
        String mainClass = SimpleTest.class.getName();
        String namePrefix = "transform-interface-of-lambda";
        JarBuilder.build(namePrefix, mainClass);

        String appJar = TestCommon.getTestJar(namePrefix + ".jar");

        String agentJar =
            ClassFileInstaller.writeJar("TransformBootClass.jar",
                                        ClassFileInstaller.Manifest.fromSourceFile("test-classes/TransformBootClass.mf"),
                                        agentClasses);
        String useJavaAgent = "-javaagent:" + agentJar + "=java/util/function/IntFunction";

        ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(
            "-cp", appJar, "-Xlog:class+load,cds=debug",
            "-XX:+UnlockDiagnosticVMOptions",
            "-XX:+AllowArchivingWithJavaAgent",
            useJavaAgent,
            mainClass);
        OutputAnalyzer out = new OutputAnalyzer(pb.start());
        System.out.println(out.getStdout());
        out.shouldHaveExitValue(0)
           // the class loaded by the SimpleTest should be from the archive
           .shouldContain("[class,load] java.text.SimpleDateFormat source: shared objects file")
           // the IntFunction is the interface which is being transformed. The
           // interface is a super type of the following lambda proxy class.
           .shouldContain("Transforming class java/util/function/IntFunction")
           // the lambda proxy class should be regenerated
           .shouldMatch(".class.load.*sun.util.locale.provider.LocaleProviderAdapter[$][$]Lambda/0x.*source:.*sun.util.locale.provider.LocaleProviderAdapter");
    }
}
