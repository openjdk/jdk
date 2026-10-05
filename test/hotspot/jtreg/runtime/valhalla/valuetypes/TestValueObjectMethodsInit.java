/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test ValueObjectMethods class initialization
 * @bug 8391022
 * @requires vm.cds.supports.aot.class.linking
 * @comment DeoptimizeALot flag requires debug VM
 * @requires vm.debug
 * @library /test/lib
 * @enablePreview
 * @modules java.base/jdk.internal.value
 * @build TestValueObjectMethodsInit
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar
 *                 ValueObjectMethodsClassApp
 *                 ValueObjectMethodsClassApp$Value
 * @run driver TestValueObjectMethodsInit
 */

import jdk.test.lib.cds.CDSTestUtils;
import jdk.test.lib.helpers.ClassFileInstaller;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class TestValueObjectMethodsInit {
    public static void main(String[] args) throws Exception {
        final String appJar = ClassFileInstaller.getJarPath("app.jar");
        final String aotConfigFile = "app.aotconfig";
        final String aotCacheFile = "app.aot";
        final String appClass = "ValueObjectMethodsClassApp";

        ProcessBuilder pb;
        OutputAnalyzer out;

        // first make sure we have a valid aotConfigFile
        pb = ProcessTools.createLimitedTestJavaProcessBuilder(
            "--enable-preview",
            "-XX:CompileThresholdScaling=0.01",
            "-Xlog:aot",
            "-XX:AOTMode=record",
            "-XX:AOTConfiguration=" + aotConfigFile,
            "-cp", appJar, appClass);

        out = CDSTestUtils.executeAndLog(pb, "train");
        out.shouldHaveExitValue(0);

        pb = ProcessTools.createLimitedTestJavaProcessBuilder(
            "--enable-preview",
            "-Xlog:aot",
            "-XX:AOTMode=create",
            "-XX:AOTConfiguration=" + aotConfigFile,
            "-XX:AOTCache=" + aotCacheFile,
            "-cp", appJar);

        out = CDSTestUtils.executeAndLog(pb, "assemble");
        out.shouldHaveExitValue(0);

        pb = ProcessTools.createLimitedTestJavaProcessBuilder(
            "--enable-preview",
            "-XX:CompileThresholdScaling=0.01",
            "-XX:+DeoptimizeALot",
            "-Xlog:aot",
            "-XX:AOTCache=" + aotCacheFile,
            "-cp", appJar, appClass);

        out = CDSTestUtils.executeAndLog(pb, "production");
        out.shouldHaveExitValue(0);
    }
}

class ValueObjectMethodsClassApp {
    private static int hash;

    static value class Value {
        private final int val;
        Value(int i) {
            val = i;
        }
    }

    static int test(int i) {
        return System.identityHashCode(new Value(i));
    }

    public static void main(String[] args) {
        // 1000 iterations are enough since we use CompileThresholdScaling=0.01
        for (int i = 0; i < 1000; i++) {
            hash = test(i);
        }
        System.out.println("Hash: " + hash);
    }
}
