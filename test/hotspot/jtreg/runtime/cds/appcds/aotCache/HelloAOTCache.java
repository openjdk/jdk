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
 * @summary Sanity test for AOTCache
 * @requires vm.cds.supports.aot.class.linking
 * @library /test/lib
 * @build HelloAOTCache
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar HelloAOTCacheApp
 * @run driver HelloAOTCache
 */

import jdk.test.lib.cds.SimpleCDSAppTester;
import jdk.test.lib.process.OutputAnalyzer;

public class HelloAOTCache {
    public static void main(String... args) throws Exception {
        SimpleCDSAppTester.of("HelloAOTCache")
            .addVmArgs("-Xlog:class+load")
            .classpath("app.jar")
            .appCommandLine("HelloAOTCacheApp")
            .setProductionChecker((OutputAnalyzer out) -> {
                    out.shouldMatch("class,load.*HelloAOTCacheApp.*shared objects");
                    out.shouldContain("HelloWorld");
                })
            .runAOTWorkflow();
    }
}

class HelloAOTCacheApp {
    public static void main(String[] args) {
        System.out.println("HelloWorld");
    }
}
