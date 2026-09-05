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
 * @summary Early init of classes in the AOT cache
 * @bug 8378731
 * @requires vm.cds.supports.aot.class.linking
 * @library /test/lib
 * @build EarlyClassInit
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar EarlyClassInitApp
 * @run driver EarlyClassInit
 */

import jdk.test.lib.cds.SimpleCDSAppTester;
import jdk.test.lib.process.OutputAnalyzer;

public class EarlyClassInit {
    public static void main(String... args) throws Exception {
        SimpleCDSAppTester.of("EarlyClassInit")
            .addVmArgs("-Xlog:aot+init=debug")
            .classpath("app.jar")
            .appCommandLine("EarlyClassInitApp")
            .setProductionChecker((OutputAnalyzer out) -> {
                    out.shouldContain("java.lang.Object (aot-inited, early)")
                       .shouldContain("No early init java.lang.ClassLoader: needs runtimeSetup()")
                       .shouldContain("No early init java.security.SecureClassLoader: super type java.lang.ClassLoader not initialized")
                       .shouldContain("Calling java.lang.ClassLoader::runtimeSetup()")
                       .shouldContain("java.security.SecureClassLoader (aot-inited)");
                    out.shouldContain("HelloWorld");
                })
            .runAOTWorkflow();
    }
}

class EarlyClassInitApp {
    public static void main(String[] args) {
        System.out.println("HelloWorld");
    }
}
