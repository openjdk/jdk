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
 */

/**
 * @test
 * @requires vm.cds
 * @requires vm.flagless
 * @requires vm.bits == 64
 * @bug 8376822
 * @summary Allocation gaps in the RW region caused by -XX:+UseCompactObjectHeaders should be reused
 * @library /test/lib
 * @build MetaspaceAllocGaps
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar hello.jar Hello
 * @run driver MetaspaceAllocGaps
 */

import jdk.test.lib.cds.SimpleCDSAppTester;
import jdk.test.lib.helpers.ClassFileInstaller;
import jdk.test.lib.process.OutputAnalyzer;

public class MetaspaceAllocGaps {
    public static void main(String[] args) throws Exception {
        String appJar = ClassFileInstaller.getJarPath("hello.jar");
        for (int i = 0; i < 2; i++) {
            String compressedOops = "-XX:" + (i == 0 ? "-" : "+") + "UseCompressedOops";
            SimpleCDSAppTester.of("MetaspaceAllocGaps" + i)
                .addVmArgs("-Xlog:aot=debug,aot+alloc=trace",
                           "-XX:+UseCompactObjectHeaders")
                .classpath(appJar)
                .appCommandLine("Hello")
                .setTrainingChecker((OutputAnalyzer out) -> {
                    // Typically all gaps should be filled. If not, we probably have a regression in C++ class ArchiveUtils.
                    //
                    // [0.422s][debug][aot ] Detailed metadata info (excluding heap region):
                    // [...]
                    // [0.422s][debug][aot ] Gap : 0 0 0.0 | 0 0 0.0 | 0 0 0.0 <<< look for this pattern
                    out.shouldMatch("Allocated [1-9][0-9]+ objects of [1-9][0-9]+ bytes in gaps .remain = 0 bytes")
                       .shouldMatch("debug.* Gap .*0[.]0.*0[.]0.*0[.]0")
                       .shouldNotMatch("Unexpected .* gaps .* for Klass alignment");
                })
                .setProductionChecker((OutputAnalyzer out) -> {
                    out.shouldContain("HelloWorld");
                })
                .runAOTWorkflow();
        }
    }
}

class Hello {
    public static void main(String[] args) {
        System.out.println("HelloWorld");
    }
}
