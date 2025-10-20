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
 * @summary AOT cache handling for package-info class loaded by jdk/internal/loader/ClassLoaders$BootClassLoader
 * @bug 8354558
 * @requires vm.cds.supports.aot.class.linking
 * @library /test/lib /test/jdk/java/lang/Package/bootclasspath/boot
 * @build PackageInfoClass foo.Foo foo.MyAnnotation foo.package-info
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar boot.jar foo.Foo foo.package-info foo.MyAnnotation
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar PackageInfoClassApp
 * @run driver PackageInfoClass AOT
 */

import java.lang.annotation.Annotation;
import java.util.Arrays;
import jdk.test.lib.cds.CDSAppTester.RunMode;
import jdk.test.lib.cds.SimpleCDSAppTester;
import jdk.test.lib.process.OutputAnalyzer;

public class PackageInfoClass {
    public static void main(String... args) throws Exception {
        SimpleCDSAppTester.of("PackageInfoClass")
            .classpath("app.jar")
            .addVmArgs("-Xbootclasspath/a:boot.jar")
            .appCommandLine("PackageInfoClassApp")
            .setAssemblyChecker((OutputAnalyzer out, RunMode runMode) -> {
                    if (runMode == RunMode.TRAINING) {
                        out.shouldContain("Skipping foo/package-info: Unsupported location");
                    }
                })
            .runAOTWorkflow();
    }
}

class PackageInfoClassApp {
    public static void main(String[] args) throws Exception {
        // This code is taken from test/jdk/java/lang/Package/bootclasspath/GetPackageFromBootClassPath.java
        Class<?> c = Class.forName("foo.Foo", false, null);
        Package p = c.getPackage();
        Annotation[] annotations = p.getAnnotations();
        Class<?> annType = Class.forName("foo.MyAnnotation", false, null);
        if (annotations.length != 1 ||
            annotations[0].annotationType() != annType) {
            throw new RuntimeException("Expected foo.MyAnnotation but got " +
                Arrays.toString(annotations));
        }
    }
}
