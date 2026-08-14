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
 * @test id=inline
 * @bug 8384756
 * @summary A class should be excluded if the type of one of its inlined fields is excluded.
 * @requires vm.cds
 * @library /test/lib
 * @modules java.base/jdk.internal.vm.annotation
 * @enablePreview
 * @compile test-classes/InlineFieldExclusionApp.java test-classes/excluded/ExcludedValueClass.java
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar InlineFieldExclusionApp
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar excluded.jar excluded/ExcludedValueClass
 * @run main/othervm InlineFieldExclusionTest InlineFieldExclusionApp
 */

/* @test id=static
 * @bug 8384756
 * @summary A class should be excluded if the type of one of its inlined fields is excluded.
 * @requires vm.cds
 * @library /test/lib
 * @modules java.base/jdk.internal.vm.annotation
 * @enablePreview
 * @compile test-classes/StaticNullRestrictedExclusionApp.java test-classes/excluded/ExcludedValueClass.java
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar StaticNullRestrictedExclusionApp
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar excluded.jar excluded/ExcludedValueClass
 * @run main/othervm InlineFieldExclusionTest StaticNullRestrictedExclusionApp
 */

import jdk.test.lib.helpers.ClassFileInstaller;
import jdk.test.lib.process.OutputAnalyzer;
import java.io.File;

public class InlineFieldExclusionTest {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            throw new RuntimeException("Missing main class name");
        }

        String mainClass = args[0];
        String appJar = ClassFileInstaller.getJarPath("app.jar");
        String unsignedJar = ClassFileInstaller.getJarPath("excluded.jar");

        JarBuilder.signJar("excluded");
        String signedJar = TestCommon.getTestJar("signed_excluded.jar");

        String classpath = appJar + System.getProperty("path.separator") + signedJar;
        OutputAnalyzer output;

        String[] suffix = TestCommon.list("--enable-preview",
                                          "-Xlog:cds,aot,class+load");

        String skipMsg1 = "Skipping excluded/ExcludedValueClass: Signed JAR";
        String skipMsg2 = "Skipping " + mainClass;
        String loadFromJar = ".class,load\s*. " + mainClass + " source: file:.*app.jar";

        // ExcludedValueClass should be excluded from the archive because it is found inside
        // a signed JAR. InlineFieldExclusionApp has a flat field of type ExcludedValueClass and
        // relies on this class to calculate the layout of the field, so it too must be excluded
        // from the archive.
        output = TestCommon.dump(classpath, TestCommon.list(mainClass, "excluded/ExcludedValueClass"), suffix);
        TestCommon.checkDump(output, skipMsg1, skipMsg2);

        // At runtime, both InlineFieldExclusionApp and ExcludedValueClass should be loaded from
        // the jar file instead of from the shared archive.
        output = TestCommon.exec(classpath, TestCommon.concat(suffix, mainClass));
        try {
            output.shouldMatch(loadFromJar);
        } catch (Exception e) {
            TestCommon.checkCommonExecExceptions(output, e);
        }
    }
}
