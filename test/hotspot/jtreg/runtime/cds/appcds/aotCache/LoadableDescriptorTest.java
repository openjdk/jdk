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
 * @summary Handling of missing classes referred to by loadable descriptors.
 * @bug 8390613
 * @requires vm.cds.supports.aot.class.linking
 * @library /test/lib
 * @enablePreview
 * @build LoadableDescriptorTest
 * @comment Omit the Line class when creating app.jar
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar LoadableDescriptorApp Point
 * @run driver LoadableDescriptorTest -XX:+AOTClassLinking
 * @run driver LoadableDescriptorTest -XX:-AOTClassLinking
 */

import jdk.test.lib.cds.SimpleCDSAppTester;
import jdk.test.lib.helpers.ClassFileInstaller;
import jdk.test.lib.process.OutputAnalyzer;

public class LoadableDescriptorTest {
    public static void main(String[] args) throws Exception {
        final String mainClass = LoadableDescriptorApp.class.getName();
        final String appJar = ClassFileInstaller.getJarPath("app.jar");

        SimpleCDSAppTester tester = SimpleCDSAppTester.of("LoadableDescriptorTest");
        tester.addVmArgs("-Xlog:aot+class=debug,class+preload", "--enable-preview", args[0])
              .appCommandLine(mainClass)
              .classpath(appJar)
              .setProductionChecker((OutputAnalyzer out) -> {
                  out.shouldContain("LoadableDescriptorApp: success")
                     .shouldContain("Preloading of class Point during linking of class LoadableDescriptorApp (cause: LoadableDescriptors attribute) succeeded")
                     .shouldContain("Preloading of class Line during linking of class LoadableDescriptorApp (cause: LoadableDescriptors attribute) failed");
              });
        tester.runAOTWorkflow();
    }
}

class LoadableDescriptorApp {
    public static void main(String[] args) {
        LoadableDescriptorApp app = new LoadableDescriptorApp();
        app.foo(null, null);
        app.bar(null, null);
        System.out.println("LoadableDescriptorApp: success");
    }

    // "Point" and "Line" are (symbolically) declared in the loadable descriptors of the
    // LoadableDescriptorApp class. However, Line is not in app.jar so it cannot be resolved
    // when LoadableDescriptorApp is linked.

    void foo(Point p1, Point p2) {}
    void bar(Line a, Line b) {}
}

value record Point(byte x, byte y) { }
value record Line(Point a, Point b) { }

