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
 * @summary verify the output of -Xlog:class+path when using AOT cache
 * @bug 8356308
 * @requires vm.cds.supports.aot.class.linking
 * @requires vm.flagless
 * @library /test/lib
 * @build ClassPathLogging
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar ClassPathLoggingApp
 * @run driver ClassPathLogging
 */

import java.io.File;
import jdk.test.lib.cds.SimpleCDSAppTester;
import jdk.test.lib.process.OutputAnalyzer;

public class ClassPathLogging {
    public static void main(String... args) throws Exception {
        String sep = File.pathSeparator;
        SimpleCDSAppTester.of("ClassPathLogging")
            .addVmArgs("-Xlog:class+path=debug")
            .classpath(sep + "foo.jar" + sep + sep + sep + "app.jar" + sep) // all empty paths should be skipped.
            .appCommandLine("ClassPathLoggingApp")
            .setProductionChecker((OutputAnalyzer out) -> {
                    out.shouldContain("HelloWorld")
                       .shouldContain("Reading classpath(s) from ClassPathLogging.aot (size = 3)")
                       .shouldMatch("boot.*0.*=.*modules")
                       .shouldContain("(app   ) [1] = foo.jar")
                       .shouldContain("(app   ) [2] = app.jar");
                })
            .runAOTWorkflow();
    }
}

class ClassPathLoggingApp {
    public static void main(String[] args) {
        System.out.println("HelloWorld");
    }
}
