/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Handling of non-final static string that has an initial value
 * @bug 8356125
 * @requires vm.cds.supports.aot.class.linking
 * @library /test/lib
 * @build NonFinalStaticWithInitVal_Helper
 * @build NonFinalStaticWithInitVal
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar MyTestApp NonFinalStaticWithInitVal_Helper
 * @run driver NonFinalStaticWithInitVal AOT
 */

import jdk.test.lib.cds.SimpleCDSAppTester;
import jdk.test.lib.helpers.ClassFileInstaller;
import jdk.test.lib.process.OutputAnalyzer;

public class NonFinalStaticWithInitVal {
    static final String appJar = ClassFileInstaller.getJarPath("app.jar");
    static final String mainClass = "MyTestApp";

    public static void main(String[] args) throws Exception {
        for (int i = 0; i < 2; i++) {
            SimpleCDSAppTester.of("NonFinalStaticWithInitVal")
                .addVmArgs("-XX:" + (i == 0 ? "-" : "+") + "AOTClassLinking",
                           "-Xlog:cds")
            .classpath("app.jar")
            .appCommandLine("MyTestApp")
            .setProductionChecker((OutputAnalyzer out) -> {
                    out.shouldContain("field = Dummy 12345678");
                })
            .runStaticWorkflow()
            .runAOTWorkflow();
        }
    }
}

class MyTestApp {
    volatile static int x = 0;

    public static void main(String args[]) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("Dummy ");
        sb.append("1234567");
        sb.append(8 + x);
        String myValue = sb.toString().intern();
        String theirValue = NonFinalStaticWithInitVal_Helper.foo;
        System.out.println("field = " + theirValue);
        if (myValue != theirValue) {
            // String literals from different class files must be interned.
            throw new RuntimeException("Interned strings do not match");
        }
    }
}
